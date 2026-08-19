package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.WearsicDownloadRepository
import com.example.data.WearsicMusicRepository
import com.example.data.WearsicPreferencesRepository
import com.example.data.db.WearsicDownloadEntity
import com.example.media.WearsicPlaybackController
import com.example.media.cache.WearsicPlaybackCacheManager
import com.example.media.download.WearsicDownloadManager
import com.example.model.PlaybackUiState
import com.example.model.Track
import com.example.network.model.ConnectionTestState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<Track> = emptyList(),
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
    val hasSearched: Boolean = false
)

class WearsicPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesRepository = WearsicPreferencesRepository(application.applicationContext)
    private val musicRepository = WearsicMusicRepository(application.applicationContext, preferencesRepository)
    private val downloadRepository = WearsicDownloadRepository(application.applicationContext)
    val downloadManager = WearsicDownloadManager(application.applicationContext, downloadRepository)
    val playbackController = WearsicPlaybackController(application.applicationContext)

    val uiState: StateFlow<PlaybackUiState> = playbackController.uiState

    val downloads: StateFlow<List<WearsicDownloadEntity>> = downloadManager.allDownloadsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedTracks: StateFlow<List<Track>> = downloadManager.completedTracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val serverUrl: StateFlow<String> = preferencesRepository.serverUrlFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WearsicPreferencesRepository.DEFAULT_SERVER_URL)

    val cacheLimitMb: StateFlow<Int> = preferencesRepository.cacheLimitFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WearsicPreferencesRepository.DEFAULT_CACHE_LIMIT)

    private val _connectionTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val connectionTestState: StateFlow<ConnectionTestState> = _connectionTestState.asStateFlow()

    private val _searchState = MutableStateFlow(SearchUiState())
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private var searchJob: Job? = null

    init {
        // Safe empty startup (no mock automatic loading)
    }

    fun testConnection(targetUrl: String) {
        if (_connectionTestState.value is ConnectionTestState.Testing) return
        viewModelScope.launch {
            _connectionTestState.value = ConnectionTestState.Testing
            val result = musicRepository.testServerConnection(targetUrl)
            _connectionTestState.value = result
        }
    }

    fun saveServerUrl(url: String) {
        viewModelScope.launch {
            preferencesRepository.saveServerUrl(url)
            _connectionTestState.value = ConnectionTestState.Idle
        }
    }

    fun saveCacheLimit(limitMb: Int) {
        viewModelScope.launch {
            preferencesRepository.saveCacheLimit(limitMb)
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _searchState.update { it.copy(query = query, isSearching = true, errorMessage = null) }
            val result = musicRepository.searchMusic(query)
            result.onSuccess { tracks ->
                _searchState.update {
                    it.copy(
                        results = tracks,
                        isSearching = false,
                        errorMessage = null,
                        hasSearched = true
                    )
                }
            }.onFailure { err ->
                _searchState.update {
                    it.copy(
                        results = emptyList(),
                        isSearching = false,
                        errorMessage = err.message ?: "Search failed",
                        hasSearched = true
                    )
                }
            }
        }
    }

    fun playTrack(track: Track) {
        viewModelScope.launch {
            // Check if track is downloaded offline locally
            val localTrack = downloadRepository.getDownloadedTrack(track.id)
            val trackToPlay = localTrack ?: track
            playbackController.playTrack(trackToPlay)
        }
    }

    fun startDownload(track: Track) {
        downloadManager.startDownload(track)
    }

    fun cancelDownload(trackId: String) {
        downloadManager.cancelDownload(trackId)
    }

    fun deleteDownload(trackId: String) {
        downloadManager.deleteDownload(trackId)
    }

    fun clearAllDownloads() {
        downloadManager.clearAllDownloads()
    }

    fun cleanPlaybackCache(): Long {
        return WearsicPlaybackCacheManager.cleanCache(getApplication<Application>().applicationContext)
    }

    fun togglePlayPause() {
        playbackController.togglePlayPause()
    }

    fun skipToNext() {
        playbackController.skipToNext()
    }

    fun skipToPrevious() {
        playbackController.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        playbackController.seekTo(positionMs)
    }

    fun seekForward() {
        playbackController.seekForward()
    }

    fun seekBack() {
        playbackController.seekBack()
    }

    fun toggleFavorite() {
        playbackController.toggleFavorite()
    }

    fun refreshOutputDevice() {
        playbackController.refreshOutputDevice()
    }

    override fun onCleared() {
        playbackController.release()
        super.onCleared()
    }
}
