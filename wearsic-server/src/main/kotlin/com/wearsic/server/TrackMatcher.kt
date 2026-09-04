package com.wearsic.server

/**
 * Scores candidate YouTube search results against known-correct metadata
 * (artist/title/duration from iTunes) to find the actual matching upload —
 * duration is the strongest signal since official audio uploads match the
 * real track length almost exactly, while covers/remixes/extended versions
 * rarely do.
 *
 * Accuracy notes (v1.4.4 — fixes "tap X, hear Y" version confusion):
 *  - Version words (demo/live/acoustic/...) are checked on the RAW lowercase
 *    titles. [normalize] strips parenthesized groups — exactly where versions
 *    live — so scoring on normalized text made the matcher blind to them and
 *    it happily served the studio upload for a demo request.
 *  - Version agreement is enforced in BOTH directions: a "Home Demo" song
 *    must not match the studio upload, and a plain song must not match a
 *    "Live Version" upload.
 *  - Word-boundary matching so "Believe" is not read as containing "live".
 *  - [matchDetailed] reports whether the winner actually cleared the
 *    confidence bar, so callers can avoid persisting weak (fallback) matches.
 */
class TrackMatcher(private val youtube: YoutubeMetadataClient) {

    /** The matched video plus whether it cleared [MIN_CONFIDENCE]. */
    data class MatchResult(val videoId: String, val strong: Boolean)

    private class Scored(val candidate: TrackDto, val score: Double, val titleOverlap: Double)

    companion object {
        private const val MIN_CONFIDENCE = 20.0

        /** Below this much title-word overlap a candidate counts as unrelated. */
        private const val PARTIAL_TITLE_OVERLAP = 0.5

        private val PENALTY_WORDS = listOf(
            "cover", "reaction", "8d audio", "sped up",
            "slowed", "reverb", "karaoke", "type beat", "tutorial",
        )

        /**
         * Version qualifiers that must AGREE between the real track and the
         * candidate (checked with word boundaries on raw lowercase titles).
         * "remix" lives here and NOT in PENALTY_WORDS anymore: the symmetric
         * version check covers it in both directions, while the old one-way
         * penalty only ever caught remixes the real song didn't ask for.
         */
        private val VERSION_WORDS = listOf(
            "demo", "live", "acoustic", "remix", "instrumental",
            "extended", "mono", "stereo", "rehearsal", "session",
        )
    }

    suspend fun match(artist: String, title: String, durationMs: Long): String? =
        matchDetailed(artist, title, durationMs)?.videoId

    suspend fun matchDetailed(artist: String, title: String, durationMs: Long): MatchResult? {
        val query = "$artist - $title"
        val candidates = youtube.search(query)
        if (candidates.isEmpty()) return null

        val scored = candidates.map { candidate ->
            Scored(candidate, score(candidate, artist, title, durationMs), titleOverlapFraction(title, candidate.title))
        }
        // maxByOrNull keeps the earliest candidate on ties (search-rank order).
        val best = scored.maxByOrNull { it.score } ?: return null

        // Strong total score wins outright — duration alone is a powerful
        // signal even when the uploader titled things oddly.
        if (best.score >= MIN_CONFIDENCE) return MatchResult(best.candidate.videoId, strong = true)

        // Weak pool: prefer the candidate that actually resembles the song
        // (≥ half the title words present), else keep the legacy behavior of
        // trusting the raw top search result so playback never blocks. Either
        // way the match is flagged weak so callers don't persist it.
        val fallback = scored
            .filter { it.titleOverlap >= PARTIAL_TITLE_OVERLAP }
            .maxByOrNull { it.score }
            ?: scored.first()
        return MatchResult(fallback.candidate.videoId, strong = false)
    }

    private fun score(candidate: TrackDto, artist: String, title: String, durationMs: Long): Double {
        var score = 0.0

        if (durationMs > 0 && candidate.durationMs > 0) {
            val diffSeconds = kotlin.math.abs(candidate.durationMs - durationMs) / 1000.0
            score += when {
                diffSeconds <= 2 -> 50.0
                diffSeconds <= 5 -> 30.0
                diffSeconds <= 10 -> 10.0
                else -> -20.0
            }
        }

        // Version agreement — checked on RAW lowercase titles with word
        // boundaries. normalize() would strip the parenthesized "(Home Demo)"
        // and hide the mismatch entirely.
        val rawTitle = title.lowercase()
        val rawCandidateTitle = candidate.title.lowercase()
        val realVersions = VERSION_WORDS.filter { containsWord(rawTitle, it) }
        val candidateVersions = VERSION_WORDS.filter { containsWord(rawCandidateTitle, it) }
        if (realVersions.isNotEmpty() && realVersions.intersect(candidateVersions).isEmpty()) {
            // The real song IS a version (demo/live/...) and the candidate
            // isn't — serving the studio upload here is the classic
            // "tapped the demo, heard the studio track" bug.
            score -= 25.0
        }
        if (realVersions.isEmpty() && candidateVersions.isNotEmpty()) {
            // Candidate claims a version the real song doesn't have.
            score -= 12.0
        }

        // Content-farm penalties (covers, reactions, ...). Also on raw titles:
        // parenthesized "(Cover)" was previously invisible after normalize.
        for (word in PENALTY_WORDS) {
            if (containsWord(rawCandidateTitle, word) && !containsWord(rawTitle, word)) score -= 20.0
        }

        val uploaderNorm = normalize(candidate.uploader)
        if (uploaderNorm.endsWith("topic")) {
            score += 25.0 // YouTube's auto-generated "Artist - Topic" channels are as close to official as scraping gets
        }

        val artistNorm = normalize(artist)
        val candidateTitleNorm = normalize(candidate.title)
        if (uploaderNorm.contains(artistNorm)) {
            score += 15.0
        } else if (candidateTitleNorm.contains(artistNorm)) {
            // Weaker signal: covers/fan uploads often repeat the artist in the
            // video title while the channel itself is unrelated.
            score += 7.0
        }

        val titleNorm = normalize(title)
        if (candidateTitleNorm.contains(titleNorm)) {
            score += 15.0
        } else if (titleNorm.contains(candidateTitleNorm) && candidateTitleNorm.isNotBlank()) {
            // Candidate title is a short form / prefix of the real title.
            score += 10.0
        } else {
            // Partial word-level credit so "Weather With You" still outranks a
            // totally unrelated video when durations are unknown.
            val overlap = titleOverlapFraction(title, candidate.title)
            if (overlap >= PARTIAL_TITLE_OVERLAP) score += 12.0 * overlap
        }

        return score
    }

    /** Whole-word containment: "believe" must not match "live". */
    internal fun containsWord(text: String, word: String): Boolean =
        Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(text)

    /** Fraction of the real title's words present in the candidate title. */
    private fun titleOverlapFraction(realTitle: String, candidateTitle: String): Double {
        val words = normalize(realTitle).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return 0.0
        val candidateNorm = normalize(candidateTitle)
        val hits = words.count { candidateNorm.contains(it) }
        return hits.toDouble() / words.size
    }

    private fun normalize(s: String): String = s
        .lowercase()
        .replace(Regex("\\(.*?\\)"), "")
        .replace(Regex("\\[.*?\\]"), "")
        .replace(Regex("official\\s*(video|audio|music video)?"), "")
        .replace(Regex("[^a-z0-9 ]"), "")
        .trim()
}
