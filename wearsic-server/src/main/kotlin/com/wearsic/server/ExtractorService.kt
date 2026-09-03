package com.wearsic.server

// NOTE ON API SURFACE: written against NewPipeExtractor v0.26.4's documented
// public API. A couple of getter/property names (e.g. on AudioStream,
// StreamInfoItem, PlaylistInfoItem) have shifted across NewPipeExtractor
// versions historically. This couldn't be compiled/verified in the sandbox
// this was written in — if a specific line fails to compile, check that
// method's exact name in the installed NewPipeExtractor jar/sources first;
// the surrounding logic (caching, parallel search, client fallback order,
// bitrate selection) is the part that actually matters and shouldn't need
// to change.

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

data class StreamTarget(val url: String, val mimeType: String)

private data class CachedStreamTarget(val target: StreamTarget, val expiresAtMillis: Long)

class ExtractorService {

    private val logger = LoggerFactory.getLogger(ExtractorService::class.java)

    companion object {
        private const val MAX_RESULTS = 10
        private const val MAX_SUGGESTIONS = 5
        private const val MAX_RELATED_MINUTES = 10
        // Audio profile tuned for the Galaxy Watch client:
        //  - AAC-LC is preferred (hardware-decoded on the watch's SoC, so it
        //    uses far less battery than software-decoded Opus/Vorbis).
        //  - ~128 kbps is YouTube's standard AAC-LC tier (itag 140): good
        //    quality for earbuds/speakers while staying small enough for the
        //    watch's storage and bandwidth.
        // Opus is only a fallback when YouTube offers no AAC stream.
        private const val TARGET_AUDIO_BITRATE_KBPS = 128
        // Long enough that replaying a song within the hour is instant (no
        // re-extraction), short enough that expired CDN URLs never linger.
        private const val STREAM_CACHE_TTL_MILLIS = 60 * 60 * 1000L // 1 hour
    }

    init {
        NewPipe.init(NewPipeDownloader(NewPipeDownloader.buildClient()))
    }

    private val searchCache = BoundedCache<String, List<TrackDto>>(maxSize = 128)
    private val streamCache = BoundedCache<String, CachedStreamTarget>(maxSize = 64)

    // Per-key coroutine mutex so two simultaneous requests for the same
    // query/videoId don't both pay full extraction cost — the second one
    // waits for the first's result and then reads the now-populated cache.
    private val keyLocks = ConcurrentHashMap<String, Mutex>()
    private suspend fun <T> withKeyLock(key: String, block: suspend () -> T): T =
        keyLocks.getOrPut(key) { Mutex() }.withLock { block() }

    // ---------------- Search ----------------

    suspend fun search(query: String): List<TrackDto> {
        val normalized = query.trim()
        if (normalized.length < 2) return emptyList()

        val cacheKey = "search:$normalized"
        searchCache.get(cacheKey)?.let { return it }

        return withKeyLock(cacheKey) {
            searchCache.get(cacheKey)?.let { return@withKeyLock it }
            val results = searchMusic(normalized)
            searchCache.put(cacheKey, results)
            results
        }
    }

    /**
     * Runs the YT-Music-filtered search and the unfiltered fallback search
     * CONCURRENTLY instead of sequentially. The original implementation ran
     * these one after another, so any query where the music filter came
     * back empty (common) paid for two full sequential network round trips.
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
            searchPage(query, emptyList())
        }
        val filtered = filteredDeferred.await()
        if (filtered.isNotEmpty()) filtered else unfilteredDeferred.await()
    }

    private fun searchPage(query: String, contentFilters: List<String>): List<TrackDto> {
        val extractor = ServiceList.YouTube.getSearchExtractor(query, contentFilters, "")
        extractor.fetchPage()
        return extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toTrackDtoOrNull() }
            // YouTube often returns the same video twice in one result page;
            // without this the watch showed duplicate songs in search results
            // and duplicated them in the play queue.
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

    suspend fun suggestions(prefix: String): List<String> = withContext(Dispatchers.IO) {
        if (prefix.trim().length < 2) return@withContext emptyList()
        runCatching {
            ServiceList.YouTube.suggestionExtractor
                .suggestionList(prefix.trim())
                .take(MAX_SUGGESTIONS)
        }.getOrDefault(emptyList())
    }

    // ---------------- Related / radio ----------------

    suspend fun related(videoId: String): List<TrackDto> = withContext(Dispatchers.IO) {
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

    suspend fun searchAlbums(query: String): List<AlbumDto> = withContext(Dispatchers.IO) {
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

    suspend fun playlistByUrl(url: String): PlaylistTracksResponse? = withContext(Dispatchers.IO) {
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

    suspend fun streamTarget(videoId: String): StreamTarget? {
        val now = System.currentTimeMillis()
        streamCache.get(videoId)?.let { cached ->
            if (cached.expiresAtMillis > now) return cached.target
        }

        return withKeyLock("stream:$videoId") {
            streamCache.get(videoId)?.let { cached ->
                if (cached.expiresAtMillis > now) return@withKeyLock cached.target
            }

            val audio = withContext(Dispatchers.IO) {
                // Try the iOS-spoofed client FIRST. In practice YouTube's
                // default web client is the one that tends to fail (bot
                // detection / cipher issues), so leading with the client
                // that actually works avoids paying for two sequential
                // extractions on almost every play.
                runCatching { resolveAudioStream(videoId, useIosClient = true) }
                    .getOrElse { e ->
                        logger.info("iOS client failed for video {}, retrying with default client: {}", videoId, e.message)
                        resolveAudioStream(videoId, useIosClient = false)
                    }
            } ?: return@withKeyLock null

            val target = StreamTarget(
                url = audio.content,
                mimeType = audio.format?.mimeType ?: "audio/webm",
            )
            streamCache.put(videoId, CachedStreamTarget(target, now + STREAM_CACHE_TTL_MILLIS))
            target
        }
    }

    private fun resolveAudioStream(videoId: String, useIosClient: Boolean): AudioStream? {
        val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
        if (extractor is YoutubeStreamExtractor) {
            // setFetchIosClient is a STATIC global toggle on the extractor, so
            // set it (when requested) for this fetch only and always reset it
            // afterwards — otherwise it would leak into unrelated extractions.
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
            // storage/bandwidth on unnecessarily huge files.
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

/** Extracts an 11-char YouTube video id from a full watch/short URL. */
private fun extractVideoId(url: String?): String? {
    if (url.isNullOrBlank()) return null
    Regex("[?&]v=([a-zA-Z0-9_-]{11})").find(url)?.let { return it.groupValues[1] }
    Regex("youtu\\.be/([a-zA-Z0-9_-]{11})").find(url)?.let { return it.groupValues[1] }
    Regex("/shorts/([a-zA-Z0-9_-]{11})").find(url)?.let { return it.groupValues[1] }
    return null
}

/** Picks a reasonably sized thumbnail from NewPipeExtractor's Image list. */
private fun bestThumbnailUrl(thumbnails: List<org.schabi.newpipe.extractor.Image>?): String? =
    thumbnails?.minByOrNull { it.height.takeIf { h -> h > 0 } ?: Int.MAX_VALUE }?.url
        ?: thumbnails?.firstOrNull()?.url
