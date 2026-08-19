package com.example.network.model

import com.example.model.Track

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
}

data class SearchResponseDto(
    val query: String,
    val tracks: List<TrackDto> = emptyList(),
    val totalResults: Int = tracks.size
)

sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState
    data object Testing : ConnectionTestState
    data class Success(val version: String, val serverName: String) : ConnectionTestState
    data class Error(val message: String) : ConnectionTestState
}
