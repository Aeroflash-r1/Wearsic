package com.example.media.cache

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSink
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import java.io.File

object WearsicPlaybackCacheManager {

    @Volatile
    private var simpleCache: SimpleCache? = null
    @Volatile
    private var databaseProvider: StandaloneDatabaseProvider? = null
    @Volatile
    private var configuredLimitBytes: Long = DEFAULT_CACHE_BYTES

    private const val CACHE_DIR_NAME = "wearsic_playback_cache"
    private const val DEFAULT_CACHE_BYTES = 32L * 1024L * 1024L // 32 MB

    fun getCacheDir(context: Context): File {
        return File(context.cacheDir, CACHE_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Applies a new cache size limit. If a cache is already built, it is released
     * so the next [getCache] call rebuilds with the new evictor.
     */
    @Synchronized
    fun setCacheLimit(maxCacheSizeBytes: Long) {
        if (maxCacheSizeBytes <= 0L) return
        configuredLimitBytes = maxCacheSizeBytes
        val current = simpleCache
        if (current != null) {
            try {
                current.release()
            } catch (_: Exception) {}
            simpleCache = null
        }
    }

    @Synchronized
    fun getCache(context: Context, maxCacheSizeBytes: Long = configuredLimitBytes): SimpleCache {
        val current = simpleCache
        if (current != null) return current

        val cacheFolder = getCacheDir(context)
        val dbProvider = databaseProvider ?: StandaloneDatabaseProvider(context.applicationContext).also {
            databaseProvider = it
        }
        val evictor = LeastRecentlyUsedCacheEvictor(maxCacheSizeBytes)

        return SimpleCache(cacheFolder, evictor, dbProvider).also {
            simpleCache = it
        }
    }

    @UnstableApi
    fun buildCacheDataSourceFactory(
        context: Context,
        maxCacheSizeBytes: Long = configuredLimitBytes
    ): DataSource.Factory {
        // The SimpleCache (SQLite index) is created lazily on the player thread,
        // never on the main thread during service creation. Building it eagerly
        // in onCreate blocks the MediaSession binder handshake on slow watches
        // and causes controller timeouts ("Unexpected IllegalStateException").
        val upstreamFactory = OkHttpDataSource.Factory(
            okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        )
            .setDefaultRequestProperties(mapOf("Range" to "bytes=0-"))

        val cacheUpstreamFactory = DefaultDataSource.Factory(context, upstreamFactory)
        val appContext = context.applicationContext

        return object : DataSource.Factory {
            override fun createDataSource(): DataSource {
                val cache = getCache(appContext, maxCacheSizeBytes)
                return CacheDataSource(
                    cache,
                    cacheUpstreamFactory.createDataSource(),
                    FileDataSource.Factory().createDataSource(),
                    CacheDataSink.Factory()
                        .setCache(cache)
                        .setFragmentSize(CacheDataSink.DEFAULT_FRAGMENT_SIZE)
                        .createDataSink(),
                    CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR,
                    null
                )
            }
        }
    }

    @Synchronized
    fun cleanCache(context: Context): Long {
        var freedBytes = 0L
        try {
            simpleCache?.let { cache ->
                freedBytes = cache.cacheSpace
                cache.release()
                simpleCache = null
            }
        } catch (_: Exception) {}

        val cacheFolder = getCacheDir(context)
        if (cacheFolder.exists()) {
            if (freedBytes == 0L) {
                freedBytes = calculateDirectorySize(cacheFolder)
            }
            cacheFolder.deleteRecursively()
            cacheFolder.mkdirs()
        }

        // Re-initialize cache with default or configured limit
        getCache(context)
        return freedBytes
    }

    fun getUsedCacheSizeBytes(context: Context): Long {
        return try {
            simpleCache?.cacheSpace ?: calculateDirectorySize(getCacheDir(context))
        } catch (_: Exception) {
            0L
        }
    }

    private fun calculateDirectorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        var bytes = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                bytes += file.length()
            }
        }
        return bytes
    }
}
