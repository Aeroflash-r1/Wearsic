package com.example.media

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Playback data source for the media session.
 *
 * The app deliberately has NO persistent stream cache: buffering is ExoPlayer's
 * normal in-memory window, and the only permanent local copy of a song is the
 * single full file written by [com.example.media.download.WearsicDownloadManager]
 * (AUTO or MANUAL — never both, never an extra partial on disk).
 *
 * `DefaultDataSource` wraps an OkHttp upstream so network URIs stream over the
 * app's tuned client while local playback (file:// for downloads, and
 * android.resource:// for bundled test tracks) still resolves through the
 * platform providers.
 */
object WearsicStreamDataSource {

    /**
     * Builds the DataSource.Factory used by the ExoPlayer media source.
     * No SimpleCache, no CacheDataSource: every byte the player reads is
     * buffered in memory only.
     */
    fun createDataSourceFactory(context: Context): DataSource.Factory {
        val upstreamFactory = OkHttpDataSource.Factory(
            com.example.network.WearsicHttp.client.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        )
        return DefaultDataSource.Factory(context.applicationContext, upstreamFactory)
    }

    /**
     * One-time migration: older builds persisted a 32–128MB SimpleCache under
     * filesDir/wearsic_playback_cache (and a legacy copy under cacheDir). With
     * the persistent stream cache gone, any leftover directory is pure dead
     * storage — every byte in it duplicated a completed Auto/Manual file (or
     * nothing at all). Called from the media service onCreate, i.e. only after
     * any previous player holding the cache open has been released.
     */
    fun deleteLegacyStreamCache(context: Context) {
        val dirs = listOf(
            File(context.filesDir, "wearsic_playback_cache"),
            File(context.cacheDir, "wearsic_playback_cache")
        )
        for (dir in dirs) {
            if (dir.exists()) {
                try {
                    dir.deleteRecursively()
                } catch (_: Exception) {
                }
            }
        }
    }
}
