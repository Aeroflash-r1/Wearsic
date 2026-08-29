package com.example.network

import com.example.network.model.AlbumDto
import com.example.network.model.FavoritesResponseDto
import com.example.network.model.PlaylistDto
import com.example.network.model.PlaylistWithTracksDto
import com.example.network.model.SearchResponseDto
import com.example.network.model.SearchResultsResponseDto
import com.example.network.model.ServerHealthDto
import com.example.network.model.SuggestionsResponseDto
import com.example.network.model.TrackDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class WearsicHttpApiClient(
    // Derived from the shared pool; only the faster connect timeout differs.
    // Auth (X-Wearsic-Key) is injected centrally by WearsicHttp.
    private val client: OkHttpClient = WearsicHttp.client.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .build()
) : WearsicApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    override suspend fun checkHealth(baseUrl: String): Result<ServerHealthDto> = withContext(Dispatchers.IO) {
        val sanitizedUrl = sanitize(baseUrl) ?: return@withContext Result.failure(IOException("Invalid URL scheme. Must use https:// or http://"))
        val healthUrl = "$sanitizedUrl/health"

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
                val dto = try {
                    json.decodeFromString<ServerHealthDto>(bodyString)
                } catch (_: Exception) {
                    ServerHealthDto()
                }

                Result.success(dto)
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
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Server error: HTTP ${response.code}")
                    )
                }

                val bodyString = response.body?.string() ?: ""
                val dto = try {
                    json.decodeFromString<SearchResultsResponseDto>(bodyString)
                } catch (_: Exception) {
                    SearchResultsResponseDto()
                }

                Result.success(
                    SearchResponseDto(
                        query = query,
                        tracks = dto.results
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
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Server error: HTTP ${response.code}"))
                }
                val bodyString = response.body?.string() ?: "[]"
                val dto = try {
                    json.decodeFromString<List<TrackDto>>(bodyString)
                } catch (_: Exception) {
                    try {
                        json.decodeFromString<FavoritesResponseDto>(bodyString).favorites
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
                Result.success(dto)
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
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Server error: HTTP ${response.code}"))
                }
                val bodyString = response.body?.string() ?: "[]"
                val playlists = try {
                    json.decodeFromString<List<PlaylistDto>>(bodyString)
                } catch (_: Exception) {
                    emptyList()
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
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Server error: HTTP ${response.code}"))
                }
                val bodyString = response.body?.string() ?: "{}"
                val dto = try {
                    json.decodeFromString<PlaylistWithTracksDto>(bodyString)
                } catch (_: Exception) {
                    PlaylistWithTracksDto(id = playlistId, name = "Playlist")
                }
                Result.success(dto)
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
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(IOException("Server error: HTTP ${response.code}"))
                    }
                    val bodyString = response.body?.string() ?: "{}"
                    val dto = try {
                        json.decodeFromString<SuggestionsResponseDto>(bodyString)
                    } catch (_: Exception) {
                        SuggestionsResponseDto()
                    }
                    Result.success(dto.suggestions)
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
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(IOException("Server error: HTTP ${response.code}"))
                    }
                    val bodyString = response.body?.string() ?: "{}"
                    val dto = try {
                        json.decodeFromString<com.example.network.model.RelatedResponseDto>(bodyString)
                    } catch (_: Exception) {
                        com.example.network.model.RelatedResponseDto()
                    }
                    Result.success(dto.results)
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
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(IOException("Server error: HTTP ${response.code}"))
                    }
                    val bodyString = response.body?.string() ?: "[]"
                    val albums = try {
                        json.decodeFromString<List<AlbumDto>>(bodyString)
                    } catch (_: Exception) {
                        emptyList()
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
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(IOException("Server error: HTTP ${response.code}"))
                    }
                    val bodyString = response.body?.string() ?: "{}"
                    val dto = try {
                        json.decodeFromString<PlaylistWithTracksDto>(bodyString)
                    } catch (_: Exception) {
                        PlaylistWithTracksDto(id = url, name = "Album")
                    }
                    Result.success(dto)
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
                val body = kotlinx.serialization.json.Json.encodeToString(
                    kotlinx.serialization.serializer<PlaylistDto>(),
                    PlaylistDto(id = "", name = name)
                ).toRequestBody(JSON_MEDIA_TYPE)
                val request = Request.Builder()
                    .url("$sanitizedUrl/api/playlists")
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.failure(IOException("Server error: HTTP ${response.code}"))
                    }
                    val bodyString = response.body?.string() ?: "{}"
                    val dto = try {
                        json.decodeFromString<PlaylistDto>(bodyString)
                    } catch (_: Exception) {
                        PlaylistDto(id = "", name = name)
                    }
                    Result.success(dto)
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

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
