package com.example.data

import android.content.Context
import com.example.model.Playlist
import com.example.model.Track
import com.example.network.WearsicApiClient
import com.example.network.WearsicHttpApiClient
import com.example.network.model.ConnectionTestState
import com.example.network.model.TrackDto
import kotlinx.coroutines.flow.first

class WearsicMusicRepository(
    private val context: Context,
    private val preferencesRepository: WearsicPreferencesRepository = WearsicPreferencesRepository(context),
    private val httpApiClient: WearsicApiClient = WearsicHttpApiClient()
) {

    val serverUrlFlow = preferencesRepository.serverUrlFlow

    suspend fun getServerUrl(): String {
        return preferencesRepository.serverUrlFlow.first()
    }

    suspend fun saveServerUrl(url: String) {
        preferencesRepository.saveServerUrl(url)
    }

    suspend fun testServerConnection(targetUrl: String): ConnectionTestState {
        val url = targetUrl.trim()
        if (!preferencesRepository.isValidServerUrl(url)) {
            return ConnectionTestState.Error("Invalid URL. Must start with http:// or https://")
        }

        val httpResult = httpApiClient.checkHealth(url)
        if (httpResult.isSuccess) {
            val health = httpResult.getOrThrow()
            return ConnectionTestState.Success(version = health.version, serverName = health.serverName)
        }

        val errorMsg = httpResult.exceptionOrNull()?.message ?: "Connection failed"
        return ConnectionTestState.Error(errorMsg)
    }

    suspend fun searchMusic(query: String): Result<List<Track>> {
        val currentUrl = getServerUrl()
        
        val httpResult = httpApiClient.searchTracks(currentUrl, query)
        if (httpResult.isSuccess) {
            val dtoList = httpResult.getOrThrow().tracks
            val domainTracks = dtoList.map { it.toDomainTrack() }
            if (domainTracks.isNotEmpty()) {
                return Result.success(domainTracks)
            }
        }

        val exception = httpResult.exceptionOrNull() ?: Exception("No tracks found on server")
        return Result.failure(exception)
    }

    suspend fun getFavorites(): Result<List<Track>> {
        val currentUrl = getServerUrl()
        val httpResult = httpApiClient.getFavorites(currentUrl)
        return httpResult.map { dtoList -> dtoList.map { it.toDomainTrack() } }
    }

    suspend fun addFavorite(track: Track): Result<Unit> {
        val currentUrl = getServerUrl()
        val dto = TrackDto(
            id = track.id,
            title = track.title,
            artist = track.artist,
            album = null,
            artworkUrl = track.artworkUrl,
            durationMs = track.durationMs,
            streamUrl = track.mediaUri
        )
        return httpApiClient.addFavorite(currentUrl, dto)
    }

    suspend fun removeFavorite(trackId: String): Result<Unit> {
        val currentUrl = getServerUrl()
        return httpApiClient.removeFavorite(currentUrl, trackId)
    }

    suspend fun getPlaylists(): Result<List<Playlist>> {
        val currentUrl = getServerUrl()
        return httpApiClient.getPlaylists(currentUrl).map { dtoList -> dtoList.map { it.toDomainPlaylist() } }
    }

    suspend fun getPlaylistTracks(playlistId: String): Result<List<Track>> {
        val currentUrl = getServerUrl()
        return httpApiClient.getPlaylistTracks(currentUrl, playlistId)
            .map { dto -> dto.tracks.map { it.toDomainTrack() } }
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String): Result<Unit> {
        val currentUrl = getServerUrl()
        return httpApiClient.removeTrackFromPlaylist(currentUrl, playlistId, trackId)
    }
}
