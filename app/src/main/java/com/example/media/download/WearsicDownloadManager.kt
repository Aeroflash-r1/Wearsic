package com.example.media.download

import android.content.Context
import android.net.Uri
import android.os.StatFs
import com.example.data.WearsicDownloadRepository
import com.example.media.cache.WearsicPlaybackCacheManager
import com.example.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlin.coroutines.coroutineContext

class WearsicDownloadManager(
    private val context: Context,
    private val repository: WearsicDownloadRepository = WearsicDownloadRepository(context),
    // Derived from the shared pool; only the longer read timeout differs.
    private val okHttpClient: OkHttpClient = com.example.network.WearsicHttp.client.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    companion object {
        private const val DOWNLOAD_DIR_NAME = "wearsic_downloads"
        private const val MIN_REQUIRED_STORAGE_BYTES = 15L * 1024L * 1024L // 15 MB
    }

    /** How many auto-cached songs to keep; evicted oldest-first. Configurable
     *  from Settings ("Offline Audio"), synced by the ViewModel. */
    @Volatile
    var maxAutoCachedTracks: Int = 15

    val allDownloadsFlow = repository.allDownloadsFlow
    val completedTracksFlow = repository.completedTracksFlow

    fun getDownloadDir(): File {
        return File(context.filesDir, DOWNLOAD_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    private fun getTargetFile(trackId: String): File {
        val sanitizedId = trackId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        return File(getDownloadDir(), "$sanitizedId.m4a")
    }

    fun isDownloading(trackId: String): Boolean {
        return activeJobs.containsKey(trackId)
    }

    fun startDownload(track: Track, autoCached: Boolean = false) {
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
                repository.recordQueued(track, targetFile.absolutePath, autoCached)

                // 3. Download (HTTP with resume, or local Android Resource)
                val uri = Uri.parse(track.mediaUri)
                val scheme = uri.scheme?.lowercase()

                if (scheme == "http" || scheme == "https") {
                    downloadWithResume(track, partFile)
                } else {
                    // Local resource/test stream
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: throw Exception("Cannot open stream from $uri")
                    if (partFile.exists()) partFile.delete()
                    FileOutputStream(partFile).use { outputStream ->
                        inputStream.use { it.copyTo(outputStream, 8192) }
                    }
                }

                // 4. Atomic file completion
                if (targetFile.exists()) targetFile.delete()
                if (!partFile.renameTo(targetFile)) {
                    // Fallback copy if rename fails
                    partFile.copyTo(targetFile, overwrite = true)
                    partFile.delete()
                }

                repository.markCompleted(track.id, targetFile.length())

                // De-duplicate storage: the song is now a real file, so purge
                // its streamed copy from the playback cache.
                if (track.mediaUri.startsWith("http")) {
                    WearsicPlaybackCacheManager.removeCachedResource(track.mediaUri)
                }

                // Keep the auto-cache footprint small: evict the oldest
                // auto-cached songs beyond the configured cap.
                if (autoCached) {
                    evictOldAutoCachedTracks()
                }
            } catch (e: Exception) {
                if (partFile.exists()) partFile.delete()
                repository.markFailed(track.id, e.message ?: "Download failed")
            } finally {
                activeJobs.remove(track.id)
            }
        }

        activeJobs[track.id] = job
    }

    /**
     * Streams the remote track into [partFile], resuming from any bytes already
     * written via open-ended Range requests. Tunnel connections drop frequently,
     * so each failure restarts the request from the last written offset instead
     * of restarting the whole download.
     */
    private suspend fun downloadWithResume(track: Track, partFile: File) {
        var downloaded = if (partFile.exists()) partFile.length() else 0L
        var totalLength = (track.durationMs * 16).coerceAtLeast(1L)
        var consecutiveFailures = 0

        while (coroutineContext.isActive) {
            var response: Response? = null
            try {
                val request = Request.Builder()
                    .url(track.mediaUri)
                    .header("Range", "bytes=$downloaded-")
                    .get()
                    .build()

                response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    throw Exception("HTTP error ${response.code}")
                }

                // Total length from Content-Range: "bytes a-b/total"
                response.header("Content-Range")?.let { contentRange ->
                    contentRange.substringAfter('/').trim().toLongOrNull()?.let { total ->
                        if (total > 0) totalLength = total
                    }
                }

                val body = response.body ?: throw Exception("Empty response body")

                FileOutputStream(partFile, true).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var lastReportedProgress = 0
                    var lastReportTime = System.currentTimeMillis()

                    body.byteStream().use { inputStream ->
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            if (!coroutineContext.isActive) {
                                withContext(NonCancellable) {
                                    inputStream.close()
                                    partFile.delete()
                                    repository.markCancelled(track.id)
                                }
                                return
                            }

                            outputStream.write(buffer, 0, bytesRead)
                            downloaded += bytesRead

                            val now = System.currentTimeMillis()
                            val currentProgress = if (totalLength > 0) {
                                ((downloaded * 100) / totalLength).toInt().coerceIn(0, 99)
                            } else {
                                50
                            }

                            // Battery & performance friendly UI update throttle (every 10% or 500ms)
                            if (currentProgress - lastReportedProgress >= 10 || (now - lastReportTime) >= 500) {
                                repository.updateProgress(track.id, currentProgress, downloaded)
                                lastReportedProgress = currentProgress
                                lastReportTime = now
                            }
                        }
                    }
                    outputStream.flush()
                }

                // Full file written
                return
            } catch (e: Exception) {
                response?.close()
                if (!coroutineContext.isActive) return
                if (consecutiveFailures >= 3) throw e
                consecutiveFailures++
                delay(1000L * consecutiveFailures)
                // Resume from the offset already written on the next attempt
            }
        }
    }

    private suspend fun evictOldAutoCachedTracks() {
        try {
            repository.getAutoCachedCompleted()
                .drop(maxAutoCachedTracks)
                .forEach { entity ->
                    repository.deleteDownload(entity.trackId)
                }
        } catch (_: Exception) {}
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

    fun release() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        scope.cancel()
    }
}
