package com.wearsic.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.slf4j.LoggerFactory

/**
 * NewPipeExtractor is initialized with a global [Downloader] and has no
 * per-instance client configuration. The only way to influence which YouTube
 * inner client performs an extraction is the STATIC toggle
 * `YoutubeStreamExtractor.setFetchIosClient(boolean)` — a process-wide setting.
 *
 * That makes concurrent extractions a correctness hazard: request A sets
 * iOS-mode, request B sets default-mode before A's fetchPage() reads it, and A
 * silently extracts with B's configuration.
 *
 * [YoutubeGateway] is the single choke point that owns that global toggle:
 * every extraction is serialized through [extractionMutex], so the
 * set -> fetch -> reset sequence is atomic with respect to every other
 * extraction. The serialization cost is acceptable for a personal low-QPS
 * server, and [streamTarget]'s result cache means repeat plays never re-enter
 * the lock at all.
 */
class YoutubeGateway(
    /**
     * Injectable for tests: a fake Downloader can serve canned responses
     * without any network access.
     */
    downloader: Downloader = NewPipeDownloader(NewPipeDownloader.buildClient()),
) : YoutubeMetadataClient {

    private val logger = LoggerFactory.getLogger(YoutubeGateway::class.java)

    init {
        // Idempotent: NewPipe.init just replaces the global downloader.
        NewPipe.init(downloader)
    }

    /**
     * Serializes ALL stream extractions because YoutubeStreamExtractor's
     * client selection is global static state (see class docs).
     */
    private val extractionMutex = Mutex()

    companion object {
        private const val MAX_RESULTS = 10
        private const val MAX_SUGGESTIONS = 5
        private const val MAX_RELATED_MINUTES = 10

        // Audio profile tuned for the Galaxy Watch client:
        //  - AAC-LC is preferred (hardware-decoded on the watch's SoC, so it
        //    uses far less battery than software-decoded Opus/Vorbis).
        //  - ~128 kbps is YouTube's standard AAC-LC tier (itag 140).
        private const val TARGET_AUDIO_BITRATE_KBPS = 128

        // Long enough that replaying a song within the hour is instant (no
        // re-extraction), short enough that expired CDN URLs never linger.
        const val STREAM_CACHE_TTL_MILLIS: Long = 60 * 60 * 1000L // 1 hour

        /** Client order for stream extraction: iOS first, default fallback. */
        internal val CLIENT_ORDER: List<Boolean> = listOf(true, false)

        /** Sentinel marking "extraction ran but nothing playable was found". */
        internal const val NOT_PLAYABLE = "wearsic:not-playable"
    }

    // ---------------- Caches ----------------

    private val searchCache = BoundedCache<String, List<TrackDto>>(maxSize = 128)
    private val streamCache = BoundedCache<String, CachedStreamTarget>(maxSize = 64)

    // Replaces the old per-key Mutex map (one entry per key EVER seen — an
    // unbounded leak). SingleFlight removes each entry when the operation
    // completes, so memory stays flat over weeks of uptime.
    private val searchSingleFlight = SingleFlight<String, List<TrackDto>>()
    private val streamSingleFlight = SingleFlight<String, StreamTarget>()

    // ---------------- Search ----------------

    override suspend fun search(query: String): List<TrackDto> {
        val normalized = query.trim()
        if (normalized.length < 2) return emptyList()

        val cacheKey = "search:$normalized"
        searchCache.get(cacheKey)?.let { return it }

        return searchSingleFlight.run(cacheKey) {
            // Double-check the cache after joining: the winner just populated it.
            searchCache.get(cacheKey)?.let { return@run it }
            val results = searchMusic(normalized)
            searchCache.put(cacheKey, results)
            results
        }
    }

    /**
     * Runs the YT-Music-filtered search and the unfiltered fallback search
     * CONCURRENTLY, and cancels the loser when the other wins — the original
     * implementation awaited both, so a successful music-filtered search still
     * paid for the full unfiltered round trip.
     */
    private suspend fun searchMusic(query: String): List<TrackDto> = coroutineScope {
        val filteredDeferred = async(Dispatchers.IO) {
            runCatching { searchPage(query, listOf("music_songs")) }
                .onFailure { e ->
                    logger.info("Music-filtered search failed for '{}': {}", query, e.message)
                }
                .getOrDefault(emptyList())
        }
        val unfilteredDeferred = async(Dispatchers.IO) {
            runCatching { searchPage(query, emptyList()) }
                .onFailure { e ->
                    logger.info("Unfiltered search failed for '{}': {}", query, e.message)
                }
                .getOrDefault(emptyList())
        }

        val filtered = filteredDeferred.await()
        if (filtered.isNotEmpty()) {
            unfilteredDeferred.cancel()
            filtered
        } else {
            unfilteredDeferred.await()
        }
    }

    private fun searchPage(query: String, contentFilters: List<String>): List<TrackDto> {
        val extractor = ServiceList.YouTube.getSearchExtractor(query, contentFilters, "")
        extractor.fetchPage()
        return extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toTrackDtoOrNull() }
            // YouTube often returns the same video twice in one result page.
            .distinctBy { it.videoId }
            .take(MAX_RESULTS)
    }

    private fun StreamInfoItem.toTrackDtoOrNull(): TrackDto? {
        val videoId = extractVideoId(url) ?: return null
        return TrackDto(
            videoId = videoId,
            title = name ?: return null,
            uploader = uploaderName ?: "Unknown",
            durationMs = duration.coerceAtLeast(0) * 1000,
            thumbnailUrl = bestThumbnailUrl(thumbnails),
        )
    }

    // ---------------- Suggestions ----------------

    override suspend fun suggestions(prefix: String): List<String> = withContext(Dispatchers.IO) {
        if (prefix.trim().length < 2) return@withContext emptyList()
        runCatching {
            ServiceList.YouTube.suggestionExtractor
                .suggestionList(prefix.trim())
                .take(MAX_SUGGESTIONS)
        }.getOrDefault(emptyList())
    }

    // ---------------- Related / radio ----------------

    override suspend fun related(videoId: String): List<TrackDto> = withContext(Dispatchers.IO) {
        runCatching {
            val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            extractor.fetchPage()
            extractor.relatedItems?.items
                .orEmpty()
                .filterIsInstance<StreamInfoItem>()
                .filter { it.duration in 1..(MAX_RELATED_MINUTES * 60) }
                .mapNotNull { it.toTrackDtoOrNull() }
                .distinctBy { it.videoId }
                .take(MAX_RESULTS)
        }.getOrDefault(emptyList())
    }

    // ---------------- Albums ----------------

    override suspend fun searchAlbums(query: String): List<AlbumDto> = withContext(Dispatchers.IO) {
        runCatching {
            val extractor = ServiceList.YouTube.getSearchExtractor(query, listOf("music_albums"), "")
            extractor.fetchPage()
            extractor.initialPage.items
                .filterIsInstance<PlaylistInfoItem>()
                .mapNotNull { item ->
                    AlbumDto(
                        id = item.url ?: return@mapNotNull null,
                        name = item.name ?: return@mapNotNull null,
                        uploader = item.uploaderName ?: "Unknown",
                        trackCount = item.streamCount.coerceAtLeast(0).toInt(),
                        thumbnailUrl = bestThumbnailUrl(item.thumbnails),
                    )
                }
                .take(MAX_RESULTS)
        }.getOrDefault(emptyList())
    }

    // ---------------- Playlist by URL (also used for albums) ----------------

    override suspend fun playlistByUrl(url: String): PlaylistTracksResponse? = withContext(Dispatchers.IO) {
        runCatching {
            val extractor = ServiceList.YouTube.getPlaylistExtractor(url)
            extractor.fetchPage()
            val tracks = extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { it.toTrackDtoOrNull() }
                .distinctBy { it.videoId }
                .take(MAX_RESULTS)
            PlaylistTracksResponse(id = url, name = extractor.name ?: "Playlist", tracks = tracks)
        }.getOrNull()
    }

    // ---------------- Stream resolution ----------------

    override suspend fun streamTarget(videoId: String): StreamTarget? {
        val now = System.currentTimeMillis()
        streamCache.get(videoId)?.let { cached ->
            if (cached.expiresAtMillis > now) return cached.target
        }

        return streamSingleFlight.run(videoId) {
            // Re-check the cache after joining: the winner populated it.
            streamCache.get(videoId)?.let { cached ->
                if (cached.expiresAtMillis > now) return@run cached.target
            }

            val audio = extractionMutex.withLock {
                withContext(Dispatchers.IO) {
                    resolveAudioStreamWithFallback(videoId)
                }
            }

            val target = if (audio != null) {
                StreamTarget(
                    url = audio.content,
                    mimeType = audio.format?.mimeType ?: "audio/webm",
                )
            } else {
                // Nothing playable found for this id (e.g. audio-only video
                // removed). Cache a NOT_PLAYABLE marker briefly so repeat
                // misses don't hammer extraction.
                StreamTarget(url = NOT_PLAYABLE, mimeType = NOT_PLAYABLE)
            }
            streamCache.put(videoId, CachedStreamTarget(target, System.currentTimeMillis() + STREAM_CACHE_TTL_MILLIS))
            target
        }.takeIf { it.url != NOT_PLAYABLE }
    }

    /**
     * iOS-spoofed client FIRST (it actually works against the bot wall), then
     * the default client as fallback. Both attempts happen INSIDE the
     * extraction mutex because the client choice is process-global state.
     */
    private fun resolveAudioStreamWithFallback(videoId: String): AudioStream? {
        for (useIos in CLIENT_ORDER) {
            try {
                return resolveAudioStream(videoId, useIosClient = useIos)
            } catch (e: Exception) {
                logger.info(
                    "{} client failed for video {}: {}",
                    if (useIos) "iOS" else "Default", videoId, e.message,
                )
            }
        }
        return null
    }

    private fun resolveAudioStream(videoId: String, useIosClient: Boolean): AudioStream? {
        val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
        if (extractor is YoutubeStreamExtractor) {
            // setFetchIosClient is a STATIC global toggle: it must be set
            // immediately before fetchPage() and always reset afterwards.
            // All such sequences are serialized by extractionMutex.
            YoutubeStreamExtractor.setFetchIosClient(useIosClient)
        }
        try {
            extractor.fetchPage()

            val candidates = extractor.audioStreams.orEmpty().filter { it.isUrl }
            if (candidates.isEmpty()) return null

            // Prefer AAC-LC (m4a/mp4 container): hardware-decoded on the watch
            // for low battery drain. Only fall back to Opus/Vorbis (WebM) when
            // YouTube offers no AAC audio at all.
            val aacCandidates = candidates.filter { stream ->
                stream.format?.mimeType?.contains("mp4", ignoreCase = true) == true
            }
            val pool = if (aacCandidates.isNotEmpty()) aacCandidates else candidates

            // Prefer streams with a known bitrate; among those, pick the one
            // closest to the target — good quality without wasting the watch's
            // storage/bandwidth.
            val withKnownBitrate = pool.filter { it.averageBitrate > 0 }
            val selectable = if (withKnownBitrate.isNotEmpty()) withKnownBitrate else pool
            return selectable.minByOrNull { stream ->
                kotlin.math.abs(stream.averageBitrate - TARGET_AUDIO_BITRATE_KBPS)
            }
        } finally {
            if (extractor is YoutubeStreamExtractor) {
                YoutubeStreamExtractor.setFetchIosClient(false)
            }
        }
    }
}

/** A resolved, playable audio target: CDN URL plus MIME type. */
data class StreamTarget(val url: String, val mimeType: String)

/** Cache entry: the resolved CDN URL plus its expiry (URLs expire upstream). */
data class CachedStreamTarget(val target: StreamTarget, val expiresAtMillis: Long)

/** Extracts an 11-char YouTube video id from a full watch/short URL. */
internal fun extractVideoId(url: String?): String? {
    if (url.isNullOrBlank()) return null
    Regex("[?&]v=([a-zA-Z0-9_-]{11})").find(url)?.let { return it.groupValues[1] }
    Regex("youtu\\.be/([a-zA-Z0-9_-]{11})").find(url)?.let { return it.groupValues[1] }
    Regex("/shorts/([a-zA-Z0-9_-]{11})").find(url)?.let { return it.groupValues[1] }
    return null
}

/** Picks a reasonably sized thumbnail from NewPipeExtractor's Image list. */
internal fun bestThumbnailUrl(thumbnails: List<Image>?): String? =
    thumbnails?.minByOrNull { it.height.takeIf { h -> h > 0 } ?: Int.MAX_VALUE }?.url
        ?: thumbnails?.firstOrNull()?.url
