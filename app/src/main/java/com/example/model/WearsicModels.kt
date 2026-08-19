package com.example.model

data class Track(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
    val mediaUri: String = "",
    val isFavorite: Boolean = false
)

data class PlaybackUiState(
    val currentTrack: Track = Track(),
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackError: String? = null,
    val outputDeviceName: String = "Watch Speaker",
    val isBluetoothConnected: Boolean = false,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val playlist: List<Track> = emptyList(),
    val currentTrackIndex: Int = 0
)

data class SettingsState(
    val serverUrl: String = "",
    val cacheLimitMb: Int = 128,
    val currentCacheUsedMb: Int = 0,
    val totalDownloadsCount: Int = 0,
    val isCacheCleaned: Boolean = false,
    val isDownloadsCleared: Boolean = false
)
