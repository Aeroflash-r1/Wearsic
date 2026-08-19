package com.example.media.cache

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

object WearsicPlaybackCacheManager {

    @Volatile
    private var simpleCache: SimpleCache? = null
    @Volatile
    private var databaseProvider: StandaloneDatabaseProvider? = null

    private const val CACHE_DIR_NAME = "wearsic_playback_cache"
    private const val DEFAULT_CACHE_BYTES = 128L * 1024L * 1024L // 128 MB

    fun getCacheDir(context: Context): File {
        return File(context.cacheDir, CACHE_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    @Synchronized
    fun getCache(context: Context, maxCacheSizeBytes: Long = DEFAULT_CACHE_BYTES): SimpleCache {
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

    fun buildCacheDataSourceFactory(
        context: Context,
        maxCacheSizeBytes: Long = DEFAULT_CACHE_BYTES
    ): DataSource.Factory {
        val cache = getCache(context, maxCacheSizeBytes)
        val upstreamFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(8000)
            .setAllowCrossProtocolRedirects(true)

        val defaultUpstreamFactory = DefaultDataSource.Factory(context, upstreamFactory)

        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(defaultUpstreamFactory)
            .setCacheReadDataSourceFactory(FileDataSource.Factory())
            .setCacheWriteDataSinkFactory(
                CacheDataSink.Factory()
                    .setCache(cache)
                    .setFragmentSize(CacheDataSink.DEFAULT_FRAGMENT_SIZE)
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
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
