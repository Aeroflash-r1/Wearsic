package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.Track

enum class DownloadState {
    NOT_DOWNLOADED,
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Entity(tableName = "downloads")
data class WearsicDownloadEntity(
    @PrimaryKey val trackId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    val durationMs: Long,
    val localFilePath: String,
    val originalStreamUrl: String,
    val downloadState: String, // from DownloadState enum
    val progress: Int = 0, // 0..100
    val fileSizeBytes: Long = 0L,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomainTrack(): Track {
        return Track(
            id = trackId,
            title = title,
            artist = artist,
            album = album ?: "Unknown Album",
            durationMs = durationMs,
            mediaUri = localFilePath,
            artworkUrl = artworkUrl
        )
    }

    fun isCompleted(): Boolean = downloadState == DownloadState.COMPLETED.name
    fun isDownloading(): Boolean = downloadState == DownloadState.DOWNLOADING.name || downloadState == DownloadState.QUEUED.name
}
