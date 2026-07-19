package com.wearsic.app.data.api

import com.wearsic.app.data.model.SearchResponse
import com.wearsic.app.data.model.StreamInfoDto
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface WearsicApi {

    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20,
    ): SearchResponse

    @GET("stream")
    suspend fun getStream(
        @Query("id") videoId: String,
    ): StreamInfoDto

    companion object {
        fun create(baseUrl: String): WearsicApi {
            val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            val json = Json { ignoreUnknownKeys = true }

            return Retrofit.Builder()
                .baseUrl(normalizedUrl)
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(WearsicApi::class.java)
        }
    }
}
