package com.wearsic.server

/**
 * Scores candidate YouTube search results against known-correct metadata
 * (artist/title/duration from iTunes) to find the actual matching upload —
 * duration is the strongest signal since official audio uploads match the
 * real track length almost exactly, while covers/remixes/extended versions
 * rarely do.
 */
class TrackMatcher(private val youtube: ExtractorService) {

    companion object {
        private const val MIN_CONFIDENCE = 20.0
        private val PENALTY_WORDS = listOf(
            "cover", "remix", "reaction", "8d audio", "sped up",
            "slowed", "reverb", "karaoke", "type beat", "tutorial",
        )
    }

    suspend fun match(artist: String, title: String, durationMs: Long): String? {
        val query = "$artist - $title"
        val candidates = youtube.search(query)
        if (candidates.isEmpty()) return null

        val best = candidates.map { it to score(it, artist, title, durationMs) }.maxByOrNull { it.second }

        // Fall back to the raw top search result rather than blocking
        // playback entirely if nothing clears the confidence bar.
        return if (best != null && best.second >= MIN_CONFIDENCE) best.first.videoId else candidates.first().videoId
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
        if (uploaderNorm.contains(artistNorm) || normalize(candidate.title).contains(artistNorm)) {
            score += 15.0
        }

        val titleNorm = normalize(title)
        val candidateTitleNorm = normalize(candidate.title)
        if (candidateTitleNorm.contains(titleNorm) || titleNorm.contains(candidateTitleNorm)) {
            score += 15.0
        }

        for (word in PENALTY_WORDS) {
            val inCandidate = candidateTitleNorm.contains(word)
            val inRealTitle = titleNorm.contains(word) // don't penalize if the real track IS a remix/cover
            if (inCandidate && !inRealTitle) score -= 20.0
        }

        return score
    }

    private fun normalize(s: String): String = s
        .lowercase()
        .replace(Regex("\\(.*?\\)"), "")
        .replace(Regex("\\[.*?\\]"), "")
        .replace(Regex("official\\s*(video|audio|music video)?"), "")
        .replace(Regex("[^a-z0-9 ]"), "")
        .trim()
}
