package com.example.network

import com.example.network.model.SearchResponseDto
import com.example.network.model.ServerHealthDto
import com.example.network.model.TrackDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class WearsicHttpApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()
) : WearsicApiClient {

    override suspend fun checkHealth(baseUrl: String): Result<ServerHealthDto> = withContext(Dispatchers.IO) {
        val trimmed = baseUrl.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return@withContext Result.failure(IOException("Invalid URL scheme. Must use https:// or http://"))
        }
        val sanitizedUrl = trimmed.trimEnd('/')
        val healthUrl = "$sanitizedUrl/api/v1/health"

        try {
            val request = Request.Builder()
                .url(healthUrl)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Server returned HTTP ${response.code}")
                    )
                }

                val bodyString = response.body?.string() ?: ""
                val json = try {
                    JSONObject(bodyString)
                } catch (_: Exception) {
                    JSONObject()
                }

                val status = json.optString("status", if (response.isSuccessful) "ok" else "error")
                val version = json.optString("version", "v1.0")
                val serverName = json.optString("serverName", "Wearsic Ktor Engine")

                Result.success(ServerHealthDto(status = status, version = version, serverName = serverName))
            }
        } catch (e: UnknownHostException) {
            Result.failure(IOException("Host not found. Check URL or internet."))
        } catch (e: SocketTimeoutException) {
            Result.failure(IOException("Connection timed out (5s)."))
        } catch (e: Exception) {
            Result.failure(IOException(e.message ?: "Could not connect to server"))
        }
    }

    override suspend fun searchTracks(baseUrl: String, query: String): Result<SearchResponseDto> = withContext(Dispatchers.IO) {
        val trimmed = baseUrl.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return@withContext Result.failure(IOException("Invalid URL scheme. Must use https:// or http://"))
        }
        val sanitizedUrl = trimmed.trimEnd('/')
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "$sanitizedUrl/api/v1/search?q=$encodedQuery"

        try {
            val request = Request.Builder()
                .url(searchUrl)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Server error: HTTP ${response.code}")
                    )
                }

                val bodyString = response.body?.string() ?: ""
                val json = JSONObject(bodyString)
                val queryResp = json.optString("query", query)
                val tracksJson = json.optJSONArray("tracks") ?: org.json.JSONArray()

                val trackList = mutableListOf<TrackDto>()
                for (i in 0 until tracksJson.length()) {
                    val item = tracksJson.getJSONObject(i)
                    trackList.add(
                        TrackDto(
                            id = item.optString("id", "track_$i"),
                            title = item.optString("title", "Unknown Track"),
                            artist = item.optString("artist", "Unknown Artist"),
                            album = item.optString("album", "Unknown Album"),
                            artworkUrl = item.optString("artworkUrl").takeIf { it.isNotBlank() },
                            durationMs = item.optLong("durationMs", 0L),
                            streamUrl = item.optString("streamUrl", "")
                        )
                    )
                }

                Result.success(SearchResponseDto(query = queryResp, tracks = trackList))
            }
        } catch (e: UnknownHostException) {
            Result.failure(IOException("Server host not resolved"))
        } catch (e: SocketTimeoutException) {
            Result.failure(IOException("Search request timed out"))
        } catch (e: Exception) {
            Result.failure(IOException(e.message ?: "Search failed"))
        }
    }
}
