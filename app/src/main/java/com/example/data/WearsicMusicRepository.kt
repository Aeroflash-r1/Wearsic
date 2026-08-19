package com.example.data

import android.content.Context
import com.example.model.Track
import com.example.network.WearsicApiClient
import com.example.network.WearsicHttpApiClient
import com.example.network.model.ConnectionTestState
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
}
