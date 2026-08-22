package com.example.network.model

import com.example.model.Track
import com.example.model.Playlist

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
            artworkUrl = artworkUrl,
            durationMs = durationMs,
            mediaUri = streamUrl,
            isFavorite = false
        )
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