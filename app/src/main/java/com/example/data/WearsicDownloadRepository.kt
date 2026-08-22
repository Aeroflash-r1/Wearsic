package com.example.data

import android.content.Context
import com.example.data.db.DownloadDao
import com.example.data.db.DownloadState
import com.example.data.db.WearsicDatabase
import com.example.data.db.WearsicDownloadEntity
import com.example.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class WearsicDownloadRepository(
    private val context: Context,
    private val downloadDao: DownloadDao = WearsicDatabase.getInstance(context).downloadDao()
) {

    val allDownloadsFlow: Flow<List<WearsicDownloadEntity>> = downloadDao.getAllDownloadsFlow()

    val completedTracksFlow: Flow<List<Track>> = downloadDao.getCompletedDownloadsFlow().map { entities ->
        entities.filter { entity ->
            val file = File(entity.localFilePath)
            file.exists() && file.length() > 0
        }.map { it.toDomainTrack() }
    }

    fun getDownloadFlow(trackId: String): Flow<WearsicDownloadEntity?> {
        return downloadDao.getDownloadFlowById(trackId)
    }

    suspend fun getDownloadedTrack(trackId: String): Track? {
        val entity = downloadDao.getDownloadById(trackId) ?: return null
        if (entity.downloadState == DownloadState.COMPLETED.name) {
            val file = File(entity.localFilePath)
            if (file.exists() && file.length() > 0) {
                return entity.toDomainTrack()
            }
        }
        return null
    }

    suspend fun isTrackDownloaded(trackId: String): Boolean {
        return getDownloadedTrack(trackId) != null
    }

    suspend fun recordQueued(track: Track, localFilePath: String, autoCached: Boolean = false) {
        val entity = WearsicDownloadEntity(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            album = track.album,
            artworkUrl = track.artworkUrl,
            durationMs = track.durationMs,
            localFilePath = localFilePath,
            originalStreamUrl = track.mediaUri,
            downloadState = DownloadState.QUEUED.name,
            progress = 0,
            fileSizeBytes = 0L,
            errorMessage = null,
            autoCached = autoCached
        )
        downloadDao.insertOrUpdate(entity)
    }

    suspend fun getAutoCachedCompleted(): List<WearsicDownloadEntity> {
        return downloadDao.getAutoCachedCompleted()
    }

    suspend fun updateProgress(trackId: String, progress: Int, sizeBytes: Long) {
        downloadDao.updateProgress(
            trackId = trackId,
            progress = progress.coerceIn(0, 100),
            state = DownloadState.DOWNLOADING.name,
            fileSizeBytes = sizeBytes
        )
    }

    suspend fun markCompleted(trackId: String, sizeBytes: Long) {
        downloadDao.updateProgress(
            trackId = trackId,
            progress = 100,
            state = DownloadState.COMPLETED.name,
            fileSizeBytes = sizeBytes
        )
    }

    suspend fun markFailed(trackId: String, error: String) {
        downloadDao.updateState(
            trackId = trackId,
            state = DownloadState.FAILED.name,
            errorMessage = error
        )
    }

    suspend fun markCancelled(trackId: String) {
        downloadDao.updateState(
            trackId = trackId,
            state = DownloadState.CANCELLED.name,
            errorMessage = "Download cancelled"
        )
    }

    suspend fun deleteDownload(trackId: String) {
        val entity = downloadDao.getDownloadById(trackId)
        if (entity != null) {
            try {
                val file = File(entity.localFilePath)
                if (file.exists()) file.delete()
                val partFile = File("${entity.localFilePath}.part")
                if (partFile.exists()) partFile.delete()
            } catch (_: Exception) {}
            downloadDao.deleteById(trackId)
        }
    }

    suspend fun clearAllDownloads() {
        val all = downloadDao.getAllDownloads()
        for (entity in all) {
            try {
                val file = File(entity.localFilePath)
                if (file.exists()) file.delete()
                val partFile = File("${entity.localFilePath}.part")
                if (partFile.exists()) partFile.delete()
            } catch (_: Exception) {}
        }
        downloadDao.deleteAll()
    }
}
