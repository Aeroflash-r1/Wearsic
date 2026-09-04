package com.wearsic.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ITunesTrack(
    val trackId: Long,
    val trackName: String? = null,
    val artistName: String? = null,
    val trackTimeMillis: Long? = null,
    val artworkUrl100: String? = null,
) {
    /** Prefixed so the stream endpoint can tell "needs YouTube matching" apart from a real videoId. */
    val surrogateId: String get() = "$SURROGATE_PREFIX$trackId"

    fun artworkUrl(size: Int = 300): String? =
        artworkUrl100?.replace("100x100bb", "${size}x${size}bb")

    companion object {
        const val SURROGATE_PREFIX = "it:"
    }
}

@Serializable
private data class ITunesSearchResponse(
    val resultCount: Int = 0,
    val results: List<ITunesTrack> = emptyList(),
)

/** No API key/registration needed — this is iTunes' public, unauthenticated search endpoint. */
class ITunesService : MetadataSource {

    // iTunes answers with Content-Type "text/javascript" (not
    // application/json), so the client deliberately skips ContentNegotiation
    // and decodes the raw body with kotlinx.serialization directly — this
    // works regardless of what Content-Type iTunes happens to send.
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO)

    override suspend fun searchSongs(query: String, limit: Int): List<ITunesTrack> {
        if (query.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<ITunesSearchResponse>(
                client.get("https://itunes.apple.com/search") {
                    parameter("term", query)
                    parameter("media", "music")
                    parameter("entity", "song")
                    parameter("limit", limit)
                }.bodyAsText()
            ).results.filter { it.trackName != null && it.artistName != null }
        }.onFailure { e -> System.err.println("ITUNES search failed for '$query': $e") }.getOrDefault(emptyList())
    }

    /**
     * Fetches one track's metadata by its iTunes id (the /lookup endpoint).
     * Used when a surrogate id ("it:12345") arrives at /api/stream but the
     * in-memory search cache is gone — e.g. a favorite/playlist saved from a
     * previous server run, or a server restart since the search. Without this
     * those ids could never be matched to a YouTube video again.
     */
    override suspend fun lookupTrack(trackId: Long): ITunesTrack? =
        runCatching {
            json.decodeFromString<ITunesSearchResponse>(
                client.get("https://itunes.apple.com/lookup") {
                    parameter("id", trackId)
                    parameter("entity", "song")
                }.bodyAsText()
            ).results.firstOrNull { it.trackName != null && it.artistName != null }
        }.onFailure { e -> System.err.println("ITUNES lookup failed for $trackId: $e") }.getOrNull()

    override fun toTrackDto(track: ITunesTrack): TrackDto = TrackDto(
        videoId = track.surrogateId,
        title = track.trackName ?: "Unknown",
        uploader = track.artistName ?: "Unknown",
        durationMs = track.trackTimeMillis ?: 0L,
        // Served at a modest size deliberately — iTunes' mzstatic.com URLs
        // don't match the client's existing ytimg/googleusercontent
        // upscale regex, so unlike YouTube thumbnails these won't get
        // upscaled client-side. 300px is a reasonable fixed size for a
        // watch screen without over-fetching.
        thumbnailUrl = track.artworkUrl(300),
    )
}
