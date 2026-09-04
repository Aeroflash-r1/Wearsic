package com.wearsic.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * /api/search now returns iTunes metadata directly — fast (~150-300ms),
 * clean data, no YouTube round trip in the request path at all. Each
 * result's videoId is a surrogate ("it:12345") until it's actually matched
 * to a YouTube video.
 *
 * Immediately after responding, the top few results are matched + their
 * streams pre-resolved in the background, so by the time someone actually
 * taps play, both steps are usually already done and cached.
 */
class MetadataSearchOrchestrator(
    private val iTunes: ITunesService,
    private val youtube: ExtractorService,
    private val matcher: TrackMatcher,
) {
    companion object {
        private const val PREFETCH_COUNT = 6
    }

    // Deliberately not tied to any single request's lifecycle — a prefetch
    // job should keep running even after the search response is already
    // sent back to the client.
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val trackInfoCache = BoundedCache<String, ITunesTrack>(maxSize = 256)
    private val matchCache = BoundedCache<String, String>(maxSize = 256) // surrogateId -> real YouTube videoId

    suspend fun search(query: String): List<TrackDto> {
        val tracks = iTunes.searchSongs(query)
        if (tracks.isNotEmpty()) {
            tracks.forEach { trackInfoCache.put(it.surrogateId, it) }

            prefetch(tracks.take(PREFETCH_COUNT))

            return tracks.map { iTunes.toTrackDto(it) }
        }
        // iTunes has no entry for this query (obscure or unreleased-on-iTunes
        // track) — fall back to the direct YouTube search so the watch still
        // gets results instead of an empty page.
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
     * finished, or match synchronously right now as a fallback so
     * playback always works even if the user tapped faster than the
     * background prefetch could keep up.
     */
    suspend fun resolveStreamVideoId(requestedId: String): String? {
        if (!requestedId.startsWith(ITunesTrack.SURROGATE_PREFIX)) return requestedId

        matchCache.get(requestedId)?.let { return it }

        // Cache miss — most commonly a server restart since the search (or a
        // favorite/playlist saved from an earlier session). Recover the track
        // metadata from iTunes by id instead of giving up, so saved songs
        // keep playing forever.
        val track = trackInfoCache.get(requestedId)
            ?: iTunes.lookupTrack(requestedId.removePrefix(ITunesTrack.SURROGATE_PREFIX).toLongOrNull() ?: return null)
            ?: return null
        trackInfoCache.put(track.surrogateId, track)
        return resolveAndCacheMatch(track)
    }

    private suspend fun resolveAndCacheMatch(track: ITunesTrack): String? {
        matchCache.get(track.surrogateId)?.let { return it }
        val videoId = matcher.match(
            artist = track.artistName ?: return null,
            title = track.trackName ?: return null,
            durationMs = track.trackTimeMillis ?: 0L,
        ) ?: return null
        matchCache.put(track.surrogateId, videoId)
        return videoId
    }
}
