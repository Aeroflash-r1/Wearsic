package com.example.network

import com.example.network.model.PlaylistDto
import com.example.network.model.AlbumDto
import com.example.network.model.PlaylistWithTracksDto
import com.example.network.model.SearchResponseDto
import com.example.network.model.ServerHealthDto
import com.example.network.model.TrackDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class WearsicHttpApiClient(
    // Derived from the shared pool; only the faster connect timeout differs.
    private val client: OkHttpClient = WearsicHttp.client.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .build()
) : WearsicApiClient {

    /** Updated by the repository whenever the user edits their API key. */
    @Volatile
    var currentApiKey: String = ""

    private fun Request.Builder.withApiKey(): Request.Builder =
        if (currentApiKey.isBlank()) this else header("X-Wearsic-Key", currentApiKey)

    override suspend fun checkHealth(baseUrl: String): Result<ServerHealthDto> = withContext(Dispatchers.IO) {
        val trimmed = baseUrl.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return@withContext Result.failure(IOException("Invalid URL scheme. Must use https:// or http://"))
        }
        val sanitizedUrl = trimmed.trimEnd('/')
        val healthUrl = "$sanitizedUrl/health"

        try {
            val request = Request.Builder()
                .url(healthUrl)
                .get()
                .withApiKey()
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
        val sanitizedUrl = sanitize(baseUrl) ?: return@withContext Result.failure(IOException("Invalid URL scheme. Must use https:// or http://"))
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "$sanitizedUrl/api/search?q=$encodedQuery"

        try {
            val request = Request.Builder()
                .url(searchUrl)
                .get()
                .withApiKey()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Server error: HTTP ${response.code}")
                    )
                }

                val bodyString = response.body?.string() ?: ""
                val json = JSONObject(bodyString)
                val resultsJson = json.optJSONArray("results") ?: org.json.JSONArray()

                Result.success(
                    SearchResponseDto(
                        query = query,
                        tracks = parseTrackArray(resultsJson, sanitizedUrl)
                    )
                )
            }
        } catch (e: UnknownHostException) {
            Result.failure(IOException("Server host not resolved"))
        } catch (e: SocketTimeoutException) {
            Result.failure(IOException("Search request timed out"))
        } catch (e: Exception) {
            Result.failure(IOException(e.message ?: "Search failed"))
        }
    }

    override suspend fun getFavorites(baseUrl: String): Result<List<TrackDto>> = withContext(Dispatchers.IO) {
        val sanitizedUrl = sanitize(baseUrl) ?: return@withContext Result.failure(IOException("Invalid URL scheme. Must use https:// or http://"))

        try {
            val request = Request.Builder()
                .url("$sanitizedUrl/api/favorites")
                .get()
                .withApiKey()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Server error: HTTP ${response.code}"))
                }
                val bodyString = response.body?.string() ?: ""
                Result.success(parseTrackArray(org.json.JSONArray(bodyString), sanitizedUrl))
            }
        } catch (e: Exception) {
            Result.failure(IOException(e.message ?: "Could not load favorites"))
        }
    }

    override suspend fun addFavorite(baseUrl: String, track: TrackDto): Result<Unit> = withContext(Dispatchers.IO) {
        val sanitizedUrl = sanitize(baseUrl) ?: return@withContext Result.failure(IOException("Invalid URL scheme. Must use https:// or http://"))

        try {
            val request = Request.Builder()
                .url("$sanitizedUrl/api/favorites")
                .post(track.toRequestBodyJson().toRequestBody(JSON_MEDIA_TYPE))
                .withApiKey()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Server error: HTTP ${response.code}"))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(IOException(e.message ?: "Could not add favorite"))
        }
    }

    override suspend fun removeFavorite(baseUrl: String, videoId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val sanitizedUrl = sanitize(baseUrl) ?: return@withContext Result.failure(IOException("Invalid URL scheme. Must use https:// or http://"))

        try {
            val request = Request.Builder()
                .url("$sanitizedUrl/api/favorites/${URLEncoder.encode(videoId, "UTF-8")}")
                .delete()
                .withApiKey()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Server error: HTTP ${response.code}"))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(IOException(e.message ?: "Could not remove favorite"))
        }
    }

    override suspend fun getPlaylists(baseUrl: String): Result<List<PlaylistDto>> = withContext(Dispatchers.IO) {
        val sanitizedUrl = sanitize(baseUrl) ?: return@withContext Result.failure(IOException("Invalid URL scheme. Must use https:// or http://"))

        try {
            val request = Request.Builder()
                .url("$sanitizedUrl/api/playlists")
                .get()
                .withApiKey()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Server error: HTTP ${response.code}"))
                }
                val bodyString = response.body?.string() ?: ""
                val json = JSONObject().put("playlists", org.json.JSONArray(bodyString))
                val playlistsJson = json.optJSONArray("playlists") ?: org.json.JSONArray()

                val playlists = mutableListOf<PlaylistDto>()
                for (i in 0 until playlistsJson.length()) {
                    val item = playlistsJson.getJSONObject(i)
                    playlists.add(
                        PlaylistDto(
                            id = item.optString("id", "playlist_$i"),
                            name = item.optString("name", "Unnamed Playlist"),
                            trackCount = item.optInt("trackCount", 0),
                            thumbnailUrl = item.optString("thumbnailUrl").takeIf { it.isNotBlank() }
                        )
                    )
                }
                Result.success(playlists)
            }
        } catch (e: Exception) {
            Result.failure(IOException(e.message ?: "Could not load playlists"))
        }
    }

    override suspend fun getPlaylistTracks(baseUrl: String, playlistId: String): Result<PlaylistWithTracksDto> = withContext(Dispatchers.IO) {
        val sanitizedUrl = sanitize(baseUrl) ?: return@withContext Result.failure(IOException("Invalid URL scheme. Must use https:// or http://"))

        try {
            val request = Request.Builder()
                .url("$sanitizedUrl/api/playlists/${URLEncoder.encode(playlistId, "UTF-8")}")
                .get()
                .withApiKey()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Server error: HTTP ${response.code}"))
                }
                val bodyString = response.body?.string() ?: ""
                val json = JSONObject(bodyString)
                Result.success(
                    PlaylistWithTracksDto(
                        id = json.optString("id", playlistId),
                        name = json.optString("name", "Playlist"),
                        tracks = parseTrackArray(json.optJSONArray("tracks") ?: org.json.JSONArray(), sanitizedUrl)
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(IOException(e.message ?: "Could not load playlist"))
        }
    }

    override suspend fun removeTrackFromPlaylist(baseUrl: String, playlistId: String, videoId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val sanitizedUrl = sanitize(baseUrl) ?: return@withContext Result.failure(IOException("Invalid URL scheme. Must use https:// or http://"))

        try {
            val request = Request.Builder()
                .url("$sanitizedUrl/api/playlists/${URLEncoder.encode(playlistId, "UTF-8")}/tracks/${URLEncoder.encode(videoId, "UTF-8")}")
                .delete()
                .withApiKey()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Server error: HTTP ${response.code}"))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(IOException(e.message ?: "Could not remove track"))
        }
    }


    override suspend fun getSuggestions(baseUrl: String, query: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            val sanitizedUrl = sanitize(baseUrl)
                ?: return@withContext Result.failure(IOException("Invalid URL scheme. Must use https:// or http://"))
            try {
                val request = Request.Builder()
                    .url("$sanitizedUrl/api/suggestions?q=${URLEncoder.encode(query, "UTF-8")}")
                    .get()
                    .withApiKey()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(IOException("Server error: HTTP ${response.code}"))
                    }
                    val json = JSONObject(response.body?.string() ?: "")
                    val arr = json.optJSONArray("suggestions") ?: org.json.JSONArray()
                    val list = mutableListOf<String>()
                    for (i in 0 until arr.length()) list.add(arr.optString(i))
                    Result.success(list)
                }
            } catch (e: Exception) {
                Result.failure(IOException(e.message ?: "Could not load suggestions"))
            }
        }

    override suspend fun getRelated(baseUrl: String, videoId: String): Result<List<TrackDto>> =
        withContext(Dispatchers.IO) {
            val sanitizedUrl = sanitize(baseUrl)
                ?: return@withContext Result.failure(IOException("Invalid URL scheme. Must use https:// or http://"))
            try {
                val request = Request.Builder()
                    .url("$sanitizedUrl/api/related/${URLEncoder.encode(videoId, "UTF-8")}")
                    .get()
                    .withApiKey()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(IOException("Server error: HTTP ${response.code}"))
                    }
                    val bodyString = response.body?.string() ?: ""
                    val resultsJson = JSONObject(bodyString).optJSONArray("results") ?: org.json.JSONArray()
                    Result.success(parseTrackArray(resultsJson, sanitizedUrl))
                }
            } catch (e: Exception) {
                Result.failure(IOException(e.message ?: "Could not load related songs"))
            }
        }

    override suspend fun searchAlbums(baseUrl: String, query: String): Result<List<AlbumDto>> =
        withContext(Dispatchers.IO) {
            val sanitizedUrl = sanitize(baseUrl)
                ?: return@withContext Result.failure(IOException("Invalid URL scheme. Must use https:// or http://"))
            try {
                val request = Request.Builder()
                    .url("$sanitizedUrl/api/search/albums?q=${URLEncoder.encode(query, "UTF-8")}")
                    .get()
                    .withApiKey()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(IOException("Server error: HTTP ${response.code}"))
                    }
                    val arr = org.json.JSONArray(response.body?.string() ?: "[]")
                    val albums = mutableListOf<AlbumDto>()
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        albums.add(
                            AlbumDto(
                                id = item.optString("id"),
                                name = item.optString("name", "Unknown Album"),
                                uploader = item.optString("uploader", ""),
                                trackCount = item.optInt("trackCount", 0),
                                thumbnailUrl = item.optString("thumbnailUrl").takeIf { it.isNotBlank() }
                            )
                        )
                    }
                    Result.success(albums)
                }
            } catch (e: Exception) {
                Result.failure(IOException(e.message ?: "Could not load albums"))
            }
        }

    override suspend fun getPlaylistByUrl(baseUrl: String, url: String): Result<PlaylistWithTracksDto> =
        withContext(Dispatchers.IO) {
            val sanitizedUrl = sanitize(baseUrl)
                ?: return@withContext Result.failure(IOException("Invalid URL scheme. Must use https:// or http://"))
            try {
                val request = Request.Builder()
                    .url("$sanitizedUrl/api/playlist?url=${URLEncoder.encode(url, "UTF-8")}")
                    .get()
                    .withApiKey()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(IOException("Server error: HTTP ${response.code}"))
                    }
                    val json = JSONObject(response.body?.string() ?: "{}")
                    Result.success(
                        PlaylistWithTracksDto(
                            id = json.optString("id", url),
                            name = json.optString("name", "Album"),
                            tracks = parseTrackArray(json.optJSONArray("tracks") ?: org.json.JSONArray(), sanitizedUrl)
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(IOException(e.message ?: "Could not load album"))
            }
        }

    override suspend fun createPlaylist(baseUrl: String, name: String): Result<PlaylistDto> =
        withContext(Dispatchers.IO) {
            val sanitizedUrl = sanitize(baseUrl)
                ?: return@withContext Result.failure(IOException("Invalid URL scheme. Must use https:// or http://"))
            try {
                val body = JSONObject().put("name", name).toString().toRequestBody(JSON_MEDIA_TYPE)
                val request = Request.Builder()
                    .url("$sanitizedUrl/api/playlists")
                    .post(body)
                    .withApiKey()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(IOException("Server error: HTTP ${response.code}"))
                    }
                    val json = JSONObject(response.body?.string() ?: "{}")
                    Result.success(
                        PlaylistDto(
                            id = json.optString("id"),
                            name = json.optString("name", name),
                            trackCount = json.optInt("trackCount", 0),
                            thumbnailUrl = null
                        )
                    )
                }
            } catch (e: Exception) {
                Result.failure(IOException(e.message ?: "Could not create playlist"))
            }
        }

    override suspend fun addTrackToPlaylist(baseUrl: String, playlistId: String, track: TrackDto): Result<Unit> =
        withContext(Dispatchers.IO) {
            val sanitizedUrl = sanitize(baseUrl)
                ?: return@withContext Result.failure(IOException("Invalid URL scheme. Must use https:// or http://"))
            try {
                val request = Request.Builder()
                    .url("$sanitizedUrl/api/playlists/${URLEncoder.encode(playlistId, "UTF-8")}/tracks")
                    .post(track.toRequestBodyJson().toRequestBody(JSON_MEDIA_TYPE))
                    .withApiKey()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(IOException("Server error: HTTP ${response.code}"))
                    }
                    Result.success(Unit)
                }
            } catch (e: Exception) {
                Result.failure(IOException(e.message ?: "Could not add to playlist"))
            }
        }

    private fun sanitize(baseUrl: String): String? {
        val trimmed = baseUrl.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return null
        return trimmed.trimEnd('/')
    }

    private fun parseTrackArray(jsonArray: org.json.JSONArray, sanitizedUrl: String): List<TrackDto> {
        val trackList = mutableListOf<TrackDto>()
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            val videoId = item.optString("videoId", "track_$i")
            trackList.add(
                TrackDto(
                    id = videoId,
                    title = item.optString("title", "Unknown Track"),
                    artist = item.optString("uploader", "Unknown Artist"),
                    album = null,
                    artworkUrl = item.optString("thumbnailUrl").takeIf { it.isNotBlank() },
                    durationMs = item.optLong("durationMs", 0L),
                    streamUrl = "$sanitizedUrl/api/stream/$videoId"
                )
            )
        }
        return trackList
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
