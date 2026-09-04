package com.wearsic.server

/**
 * Scores candidate YouTube search results against known-correct metadata
 * (artist/title/duration from iTunes) to find the actual matching upload —
 * duration is the strongest signal since official audio uploads match the
 * real track length almost exactly, while covers/remixes/extended versions
 * rarely do.
 *
 * Accuracy notes (v1.4.3):
 *  - When no candidate clears MIN_CONFIDENCE, the fallback is the candidate
 *    with the most title-word overlap (not blindly the raw top search hit —
 *    YouTube's first result for "Artist - Title" is often a vlog/mix that
 *    merely shares a word).
 *  - Artist-name-in-uploader beats artist-name-in-title-only: covers often
 *    repeat the artist inside the title ("Song by X cover"), while genuine
 *    uploads carry the artist in the channel name.
 */
class TrackMatcher(private val youtube: YoutubeMetadataClient) {

    private class Scored(val candidate: TrackDto, val score: Double, val titleOverlap: Double)

    companion object {
        private const val MIN_CONFIDENCE = 20.0

        /** Below this much title-word overlap a candidate counts as unrelated. */
        private const val PARTIAL_TITLE_OVERLAP = 0.5

        private val PENALTY_WORDS = listOf(
            "cover", "remix", "reaction", "8d audio", "sped up",
            "slowed", "reverb", "karaoke", "type beat", "tutorial",
        )
    }

    suspend fun match(artist: String, title: String, durationMs: Long): String? {
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
        if (best.score >= MIN_CONFIDENCE) return best.candidate.videoId

        // Weak pool: prefer the candidate that actually resembles the song
        // (≥ half the title words present), else keep the legacy behavior of
        // trusting the raw top search result so playback never blocks.
        val fallback = scored
            .filter { it.titleOverlap >= PARTIAL_TITLE_OVERLAP }
            .maxByOrNull { it.score }
            ?: scored.first()
        return fallback.candidate.videoId
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
            // Partial word-level credit so "Weather With You (Live)" still
            // outranks a totally unrelated video when durations are unknown.
            val overlap = titleOverlapFraction(title, candidate.title)
            if (overlap >= PARTIAL_TITLE_OVERLAP) score += 12.0 * overlap
        }

        for (word in PENALTY_WORDS) {
            val inCandidate = candidateTitleNorm.contains(word)
            val inRealTitle = titleNorm.contains(word) // don't penalize if the real track IS a remix/cover
            if (inCandidate && !inRealTitle) score -= 20.0
        }

        return score
    }

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
