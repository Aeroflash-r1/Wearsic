package com.example.media.download

import android.content.Context
import android.net.Uri
import android.os.StatFs
import com.example.data.WearsicDownloadRepository
import com.example.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class WearsicDownloadManager(
    private val context: Context,
    private val repository: WearsicDownloadRepository = WearsicDownloadRepository(context),
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    companion object {
        private const val DOWNLOAD_DIR_NAME = "wearsic_downloads"
        private const val MIN_REQUIRED_STORAGE_BYTES = 15L * 1024L * 1024L // 15 MB
    }

    val allDownloadsFlow = repository.allDownloadsFlow
    val completedTracksFlow = repository.completedTracksFlow

    fun getDownloadDir(): File {
        return File(context.filesDir, DOWNLOAD_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    private fun getTargetFile(trackId: String): File {
        val sanitizedId = trackId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        return File(getDownloadDir(), "$sanitizedId.mp3")
    }

    fun isDownloading(trackId: String): Boolean {
        return activeJobs.containsKey(trackId)
    }

    fun startDownload(track: Track) {
        if (activeJobs.containsKey(track.id)) return

        val targetFile = getTargetFile(track.id)
        val partFile = File(getDownloadDir(), "${targetFile.name}.part")

        val job = scope.launch {
            try {
                // 1. Storage safety check
                val stat = StatFs(getDownloadDir().path)
                val availableBytes = stat.availableBytes
                if (availableBytes < MIN_REQUIRED_STORAGE_BYTES) {
                    repository.markFailed(track.id, "Storage full (<15MB free)")
                    return@launch
                }

                // 2. Mark queued in Room
                repository.recordQueued(track, targetFile.absolutePath)

                // 3. Open input stream (HTTP stream or Local Android Resource)
                val inputStream: InputStream
                val totalLength: Long

                val uri = Uri.parse(track.mediaUri)
                val scheme = uri.scheme?.lowercase()

                if (scheme == "http" || scheme == "https") {
                    val request = Request.Builder().url(track.mediaUri).get().build()
                    val response = okHttpClient.newCall(request).execute()
                    if (!response.isSuccessful) {
                        repository.markFailed(track.id, "HTTP error ${response.code}")
                        return@launch
                    }
                    val body = response.body ?: throw Exception("Empty response body")
                    inputStream = body.byteStream()
                    totalLength = body.contentLength().takeIf { it > 0 } ?: (track.durationMs * 16) // fallback estimate
                } else {
                    // Local resource/test stream
                    inputStream = context.contentResolver.openInputStream(uri)
                        ?: throw Exception("Cannot open stream from $uri")
                    totalLength = inputStream.available().toLong().takeIf { it > 0 } ?: 64000L
                }

                // 4. Stream to .part file
                if (partFile.exists()) partFile.delete()
                FileOutputStream(partFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    var lastReportedProgress = 0
                    var lastReportTime = System.currentTimeMillis()

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (!isActive) {
                            partFile.delete()
                            repository.markCancelled(track.id)
                            return@launch
                        }

                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        val now = System.currentTimeMillis()
                        val currentProgress = if (totalLength > 0) {
                            ((totalBytesRead * 100) / totalLength).toInt().coerceIn(0, 99)
                        } else {
                            50
                        }

                        // Battery & performance friendly UI update throttle (every 10% or 500ms)
                        if (currentProgress - lastReportedProgress >= 10 || (now - lastReportTime) >= 500) {
                            repository.updateProgress(track.id, currentProgress, totalBytesRead)
                            lastReportedProgress = currentProgress
                            lastReportTime = now
                        }
                    }
                    outputStream.flush()
                }
                inputStream.close()

                // 5. Atomic file completion
                if (targetFile.exists()) targetFile.delete()
                if (!partFile.renameTo(targetFile)) {
                    // Fallback copy if rename fails
                    partFile.copyTo(targetFile, overwrite = true)
                    partFile.delete()
                }

                repository.markCompleted(track.id, targetFile.length())
            } catch (e: Exception) {
                if (partFile.exists()) partFile.delete()
                repository.markFailed(track.id, e.message ?: "Download failed")
            } finally {
                activeJobs.remove(track.id)
            }
        }

        activeJobs[track.id] = job
    }

    fun cancelDownload(trackId: String) {
        val job = activeJobs.remove(trackId)
        job?.cancel()
        scope.launch {
            val targetFile = getTargetFile(trackId)
            val partFile = File(getDownloadDir(), "${targetFile.name}.part")
            if (partFile.exists()) partFile.delete()
            repository.markCancelled(trackId)
        }
    }

    fun deleteDownload(trackId: String) {
        cancelDownload(trackId)
        scope.launch {
            repository.deleteDownload(trackId)
        }
    }

    fun clearAllDownloads() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        scope.launch {
            repository.clearAllDownloads()
        }
    }
}
