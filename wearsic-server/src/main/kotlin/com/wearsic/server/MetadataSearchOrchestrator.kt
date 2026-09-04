package com.wearsic.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * /api/search returns iTunes metadata directly — fast (~150-300ms), clean
 * data, and NO YouTube round trip in the request path when iTunes has usable
 * results. Each result's videoId is a surrogate ("it:12345") until it is
 * matched to a real YouTube video.
 *
 * Fallback contract (see OrchestratorFallbackTest):
 *   iTunes usable results  -> return them; YouTube is only touched in the
 *                             background (match + stream prefetch)
 *   iTunes empty/unusable  -> direct YouTube search so the watch still gets
 *                             results instead of an empty page
 *
 * Immediately after responding, the top few results are matched + their
 * streams pre-resolved in the background, so by the time someone taps play,
 * both steps are usually already done and cached.
 */
class MetadataSearchOrchestrator(
    private val metadata: MetadataSource,
    private val youtube: YoutubeMetadataClient,
    private val matcher: TrackMatcher,
    /**
     * Optional persistent match store. When supplied, surrogate -> videoId
     * matches survive a server restart, so tapping a saved favorite/playlist
     * song replays instantly instead of re-running the multi-second YouTube
     * match + extraction path. Nullable so tests can run fully in-memory.
     */
    private val persistentMatches: MatchPersistence? = null,
) {
    companion object {
        private const val PREFETCH_COUNT = 6
    }

    /** Read/write view over persisted surrogate matches (e.g. SQLite). */
    interface MatchPersistence {
        fun getMatchedVideoId(surrogateId: String): String?
        fun putMatchedVideoId(surrogateId: String, videoId: String)
    }

    // Deliberately not tied to any single request's lifecycle — a prefetch
    // job should keep running even after the search response is already sent
    // back to the client.
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val trackInfoCache = BoundedCache<String, ITunesTrack>(maxSize = 256)
    private val matchCache = BoundedCache<String, String>(maxSize = 256) // surrogateId -> real YouTube videoId

    /**
     * Counts fallback invocations; exposed for tests to prove that a usable
     * iTunes response never triggers the synchronous YouTube fallback.
     */
    var youtubeFallbackCount: Int = 0
        private set

    suspend fun search(query: String): List<TrackDto> {
        val tracks = metadata.searchSongs(query)
        if (tracks.isNotEmpty()) {
            // USABLE iTunes results: return them immediately. The YouTube
            // fallback below is NOT reached — a previous version awaited the
            // fallback search even when this branch produced results, paying a
            // pointless multi-second YouTube round trip per search.
            tracks.forEach { trackInfoCache.put(it.surrogateId, it) }

            prefetch(tracks.take(PREFETCH_COUNT))

            return tracks.map { metadata.toTrackDto(it) }
        }

        // No usable iTunes metadata (obscure or unreleased-on-iTunes track) —
        // fall back to the direct YouTube search so the watch still gets
        // results instead of an empty page.
        youtubeFallbackCount++
        return youtube.search(query)
    }

    private fun prefetch(tracks: List<ITunesTrack>) {
        tracks.forEach { track ->
            backgroundScope.launch {
                val videoId = resolveAndCacheMatch(track) ?: return@launch
                youtube.streamTarget(videoId) // warms the stream-resolution cache too
            }
        }
    }

    /**
     * Given whatever the client sent to /api/stream/{id}: if it's a real
     * YouTube id already (related/playlist results, which stay
     * NewPipeExtractor-only), pass it through unchanged. If it's a
     * surrogate iTunes id, use the cached match if the prefetch already
     * finished, or match synchronously right now as a fallback so playback
     * always works even if the user tapped faster than the background
     * prefetch could keep up.
     */
    suspend fun resolveStreamVideoId(requestedId: String): String? {
        if (!requestedId.startsWith(ITunesTrack.SURROGATE_PREFIX)) return requestedId

        matchCache.get(requestedId)?.let { return it }

        // Cache miss — most commonly a server restart since the search (or a
        // favorite/playlist saved from an earlier session). Recover the track
        // metadata from iTunes by id instead of giving up, so saved songs
        // keep playing forever.
        val track = trackInfoCache.get(requestedId)
            ?: metadata.lookupTrack(requestedId.removePrefix(ITunesTrack.SURROGATE_PREFIX).toLongOrNull() ?: return null)
            ?: return null
        trackInfoCache.put(track.surrogateId, track)
        return resolveAndCacheMatch(track)
    }

    private suspend fun resolveAndCacheMatch(track: ITunesTrack): String? {
        matchCache.get(track.surrogateId)?.let { return it }
        // Check the persistent store before paying for a YouTube match. Any
        // persistence failure must never break playback, hence runCatching.
        persistentMatches?.let { store ->
            runCatching { store.getMatchedVideoId(track.surrogateId) }.getOrNull()?.let { cached ->
                matchCache.put(track.surrogateId, cached)
                return cached
            }
        }
        val videoId = matcher.match(
            artist = track.artistName ?: return null,
            title = track.trackName ?: return null,
            durationMs = track.trackTimeMillis ?: 0L,
        ) ?: return null
        matchCache.put(track.surrogateId, videoId)
        runCatching { persistentMatches?.putMatchedVideoId(track.surrogateId, videoId) }
        return videoId
    }
}
