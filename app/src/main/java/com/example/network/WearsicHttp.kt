package com.example.network

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * Process-wide shared OkHttpClient. Every consumer derives from this base via
 * [OkHttpClient.newBuilder], so connection pools, dispatchers and sockets are
 * reused across the API client, downloader, stream cache and warm-up requests
 * instead of each holding a private pool (wasteful on a watch).
 */
object WearsicHttp {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
}
