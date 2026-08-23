package com.example.network.model

import com.example.model.Track
import com.example.model.Playlist
import com.example.model.Album

data class ServerHealthDto(
    val status: String = "ok",
    val version: String = "1.0.0",
    val serverName: String = "Wearsic Engine"
)

data class TrackDto(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
    val streamUrl: String
) {
    fun toDomainTrack(): Track {
        return Track(
            id = id,
            title = title,
            artist = artist,
            album = album ?: "Single",
            artworkUrl = artworkUrl?.toHighResArtwork(),
            durationMs = durationMs,
            mediaUri = streamUrl,
            isFavorite = false
        )
    }

    /**
     * Server search results ship 60x60 thumbnails (`...=w60-h60-l90-rj`).
     * YouTube resizes on the fly — swapping the size segment gives us crisp
     * artwork everywhere for a few extra KB per image.
     */
    private fun String.toHighResArtwork(): String {
        return if (contains("ytimg") || contains("googleusercontent")) {
            replace(Regex("w\\d+-h\\d+"), "w544-h544")
        } else {
            this
        }
    }

    fun toRequestBodyJson(): String {
        return "{\"videoId\":\"${jsonEscape(id)}\"," +
            "\"title\":\"${jsonEscape(title)}\"," +
            "\"uploader\":\"${jsonEscape(artist)}\"," +
            "\"durationMs\":$durationMs," +
            "\"thumbnailUrl\":\"${jsonEscape(artworkUrl ?: "")}\"}"
    }

    private fun jsonEscape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}

data class SearchResponseDto(
    val query: String,
    val tracks: List<TrackDto> = emptyList()
)

data class AlbumDto(
    val id: String,
    val name: String,
    val uploader: String = "",
    val trackCount: Int = 0,
    val thumbnailUrl: String? = null
) {
    fun toDomainAlbum(): Album {
        return Album(
            id = id,
            name = name,
            uploader = uploader,
            trackCount = trackCount,
            thumbnailUrl = thumbnailUrl?.let { url ->
                if (url.contains("ytimg") || url.contains("googleusercontent")) {
                    url.replace(Regex("w\\d+-h\\d+"), "w544-h544")
                } else url
            }
        )
    }
}

data class PlaylistDto(
    val id: String,
    val name: String,
    val trackCount: Int = 0,
    val thumbnailUrl: String? = null
) {
    fun toDomainPlaylist(): Playlist {
        return Playlist(
            id = id,
            name = name,
            trackCount = trackCount,
            thumbnailUrl = thumbnailUrl
        )
    }
}

data class PlaylistWithTracksDto(
    val id: String,
    val name: String,
    val tracks: List<TrackDto> = emptyList()
)

sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState
    data object Testing : ConnectionTestState
    data class Success(val version: String, val serverName: String) : ConnectionTestState
    data class Error(val message: String) : ConnectionTestState
}