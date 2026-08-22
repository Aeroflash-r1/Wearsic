package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.WearsicDownloadRepository
import com.example.data.WearsicMusicRepository
import com.example.data.WearsicPreferencesRepository
import com.example.data.WearsicRecentRepository
import com.example.data.db.WearsicDownloadEntity
import com.example.media.WearsicPlaybackController
import com.example.media.cache.WearsicPlaybackCacheManager
import com.example.media.download.WearsicDownloadManager
import com.example.model.PlaybackUiState
import com.example.model.Playlist
import com.example.model.Track
import com.example.network.model.ConnectionTestState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class SearchUiState(
    val query: String = "",
    val results: List<Track> = emptyList(),
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
    val hasSearched: Boolean = false
)

data class FavoritesUiState(
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class PlaylistsUiState(
    val favorites: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class PlaylistDetailUiState(
    val playlistId: String = "",
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class WearsicPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesRepository = WearsicPreferencesRepository(application.applicationContext)
    private val musicRepository = WearsicMusicRepository(application.applicationContext, preferencesRepository)
    private val downloadRepository = WearsicDownloadRepository(application.applicationContext)
    private val downloadManager = WearsicDownloadManager(application.applicationContext, downloadRepository)
    private val recentRepository = WearsicRecentRepository(application.applicationContext)
    private val playbackController = WearsicPlaybackController(application.applicationContext)

    val uiState: StateFlow<PlaybackUiState> = playbackController.uiState

    val downloads: StateFlow<List<WearsicDownloadEntity>> = downloadManager.allDownloadsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Eagerly: read via .value in playTrack() before any UI subscribes to it,
    // so completed downloads must be loaded even when DownloadsScreen is closed.
    val completedTracks: StateFlow<List<Track>> = downloadManager.completedTracksFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recentTracks: StateFlow<List<Track>> = recentRepository.recentTracksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val serverUrl: StateFlow<String> = preferencesRepository.serverUrlFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WearsicPreferencesRepository.DEFAULT_SERVER_URL)

    val cacheLimitMb: StateFlow<Int> = preferencesRepository.cacheLimitFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WearsicPreferencesRepository.DEFAULT_CACHE_LIMIT)

    private val _connectionTestState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val connectionTestState: StateFlow<ConnectionTestState> = _connectionTestState.asStateFlow()

    private val _searchState = MutableStateFlow(SearchUiState())
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _favoritesState = MutableStateFlow(FavoritesUiState())
    val favoritesState: StateFlow<FavoritesUiState> = _favoritesState.asStateFlow()

    private val _playlistsState = MutableStateFlow(PlaylistsUiState())
    val playlistsState: StateFlow<PlaylistsUiState> = _playlistsState.asStateFlow()

    private val _playlistDetailState = MutableStateFlow(PlaylistDetailUiState())
    val playlistDetailState: StateFlow<PlaylistDetailUiState> = _playlistDetailState.asStateFlow()

    private var searchJob: Job? = null
    private var detailJob: Job? = null

    // Lightweight client used only to pre-warm the server's stream cache for
    // the NEXT track in the queue, so it starts playing instantly.
    private val warmUpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    @Volatile
    private var lastWarmUpTrackId: String? = null

    init {
        // No cache setup at startup: the cache limit defaults to 128MB and the
        // playback cache is built lazily by the media session. Applying the
        // limit here used to release/rebuild the SimpleCache while the player
        // was active, which caused IO errors on slow watches.

        // Record recently played tracks whenever the current track changes,
        // including auto-advance to the next song. Tracks that are played
        // substantially (>= 60%) are auto-cached as real downloads so they
        // keep playing offline even though the stream cache only holds the
        // bytes that actually streamed. The NEXT queued track is pre-warmed
        // on the server so it starts playing without the extraction delay.
        viewModelScope.launch {
            val autoCachedTrackIds = mutableSetOf<String>()
            var lastTrack: Track? = null
            var lastPositionMs = 0L
            var lastDurationMs = 0L

            playbackController.uiState.collect { state ->
                val track = state.currentTrack
                if (track.id.isNotBlank()) {
                    if (track.id != lastTrack?.id) {
                        // Track changed: the previous one finished or was skipped.
                        lastTrack?.let { previous ->
                            maybeAutoCacheTrack(previous, lastPositionMs, lastDurationMs, autoCachedTrackIds)
                        }
                        lastTrack = track
                        recentRepository.recordPlayed(track)
                    } else if (!state.isPlaying &&
                        lastDurationMs > 0 &&
                        lastPositionMs >= lastDurationMs * 0.6 &&
                        track.id !in autoCachedTrackIds
                    ) {
                        // The last track in the queue ended: auto-cache it too.
                        maybeAutoCacheTrack(track, lastPositionMs, lastDurationMs, autoCachedTrackIds)
                    }

                    // Pre-warm the server for the upcoming song (1-byte request:
                    // the server resolves + caches the real stream URL so the
                    // next play() skips the multi-second extraction).
                    val nextTrack = state.playlist.getOrNull(state.currentTrackIndex + 1)
                    if (nextTrack != null && nextTrack.id != lastWarmUpTrackId) {
                        warmUpStream(nextTrack)
                    }
                }
                lastPositionMs = state.currentPositionMs
                lastDurationMs = state.durationMs
            }
        }
    }

    private fun warmUpStream(nextTrack: Track) {
        if (!nextTrack.mediaUri.startsWith("http")) return
        if (!isNetworkAvailable()) {
            lastWarmUpTrackId = nextTrack.id
            return
        }
        lastWarmUpTrackId = nextTrack.id
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(nextTrack.mediaUri)
                    .header("Range", "bytes=0-0")
                    .build()
                warmUpClient.newCall(request).execute().use { response ->
                    response.body?.close()
                }
            } catch (_: Exception) {
                // Warm-up is best-effort; playback retries normally anyway.
            }
        }
    }

    private fun maybeAutoCacheTrack(
        track: Track,
        positionMs: Long,
        durationMs: Long,
        evaluatedTrackIds: MutableSet<String>
    ) {
        if (track.id in evaluatedTrackIds) return
        // Only streamed tracks can be auto-cached; local files are already offline.
        if (!track.mediaUri.startsWith("http")) return
        if (!isNetworkAvailable()) return

        val duration = if (durationMs > 0) durationMs else track.durationMs
        val playedRatio = if (duration > 0) positionMs.toDouble() / duration else 0.0
        if (playedRatio < 0.6) return

        if (downloadManager.isDownloading(track.id)) return
        if (downloads.value.any { it.trackId == track.id && it.isCompleted() }) return

        evaluatedTrackIds.add(track.id)
        downloadManager.startDownload(track, autoCached = true)
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = getApplication<Application>()
                .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.activeNetworkInfo?.isConnected == true
        } catch (_: Exception) {
            true
        }
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
            WearsicPlaybackCacheManager.setCacheLimit(limitMb.toLong() * 1024L * 1024L)
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

            // Build a contextual queue so the next song auto-plays:
            // 1. If the track belongs to the completed downloads list, queue all
            //    downloaded tracks from this one onwards.
            // 2. Otherwise, if it belongs to the latest search results, queue the
            //    whole result set from this track onwards.
            val completed = completedTracks.value
            val completedIndex = completed.indexOfFirst { it.id == trackToPlay.id }
            if (completedIndex >= 0) {
                playbackController.playTracks(completed, completedIndex)
                return@launch
            }

            val results = _searchState.value.results
            val resultIndex = results.indexOfFirst { it.id == trackToPlay.id }
            if (resultIndex >= 0) {
                playbackController.playTracks(results, resultIndex)
                return@launch
            }

            playbackController.playTrack(trackToPlay)
        }
    }

    fun addToQueue(track: Track) {
        playbackController.addToQueue(listOf(track))
    }

    fun removeFromQueue(index: Int) {
        playbackController.removeFromQueue(index)
    }

    fun seekToQueueItem(index: Int) {
        playbackController.seekToQueueItem(index)
    }

    fun clearQueue() {
        playbackController.clearQueue()
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

    fun cleanPlaybackCache(onResult: (Long) -> Unit) {
        viewModelScope.launch {
            val freedBytes = withContext(Dispatchers.IO) {
                WearsicPlaybackCacheManager.cleanCache(getApplication<Application>().applicationContext)
            }
            onResult(freedBytes)
        }
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
        val track = playbackController.uiState.value.currentTrack
        if (track.id.isBlank()) return

        val targetFavorite = !track.isFavorite
        playbackController.setCurrentTrackFavorite(targetFavorite)

        viewModelScope.launch {
            val result = if (targetFavorite) {
                musicRepository.addFavorite(track)
            } else {
                musicRepository.removeFavorite(track.id)
            }

            result.onSuccess {
                val updatedFavorites = if (targetFavorite) {
                    if (_favoritesState.value.tracks.none { it.id == track.id }) {
                        _favoritesState.value.tracks + track.copy(isFavorite = true)
                    } else {
                        _favoritesState.value.tracks
                    }
                } else {
                    _favoritesState.value.tracks.filterNot { it.id == track.id }
                }
                _favoritesState.update { it.copy(tracks = updatedFavorites, errorMessage = null) }
                _playlistsState.update { it.copy(favorites = updatedFavorites) }
            }.onFailure { err ->
                // Revert the optimistic flip when the server call fails.
                playbackController.setCurrentTrackFavorite(!targetFavorite)
                _favoritesState.update {
                    it.copy(errorMessage = err.message ?: "Could not update favorite")
                }
            }
        }
    }

    fun removeFavorite(trackId: String) {
        viewModelScope.launch {
            val result = musicRepository.removeFavorite(trackId)
            result.onSuccess {
                val updatedFavorites = _favoritesState.value.tracks.filterNot { it.id == trackId }
                _favoritesState.update { it.copy(tracks = updatedFavorites, errorMessage = null) }
                _playlistsState.update { it.copy(favorites = updatedFavorites) }
                if (playbackController.uiState.value.currentTrack.id == trackId) {
                    playbackController.setCurrentTrackFavorite(false)
                }
            }.onFailure { err ->
                _favoritesState.update {
                    it.copy(errorMessage = err.message ?: "Could not remove favorite")
                }
            }
        }
    }

    fun refreshFavorites() {
        if (_favoritesState.value.isLoading) return
        _favoritesState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = musicRepository.getFavorites()
            result.onSuccess { tracks ->
                _favoritesState.update { it.copy(tracks = tracks, isLoading = false, errorMessage = null) }
                _playlistsState.update { it.copy(favorites = tracks) }
                // Keep the player heart in sync when the current track is favorited.
                val current = playbackController.uiState.value.currentTrack
                if (current.id.isNotBlank()) {
                    playbackController.setCurrentTrackFavorite(tracks.any { it.id == current.id })
                }
            }.onFailure { err ->
                _favoritesState.update {
                    it.copy(isLoading = false, errorMessage = err.message ?: "Could not load favorites")
                }
            }
        }
    }

    fun refreshLibrary() {
        if (_playlistsState.value.isLoading) return
        _playlistsState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val favoritesResult = musicRepository.getFavorites()
            val playlistsResult = musicRepository.getPlaylists()

            var errorMessage: String? = null
            val favorites = favoritesResult.getOrNull()
            if (favorites != null) {
                _favoritesState.update { it.copy(tracks = favorites, errorMessage = null) }
            } else {
                errorMessage = favoritesResult.exceptionOrNull()?.message ?: "Could not load favorites"
            }
            val playlists = playlistsResult.getOrNull()
            if (playlists != null) {
                // no-op, applied below
            } else {
                errorMessage = playlistsResult.exceptionOrNull()?.message ?: errorMessage ?: "Could not load playlists"
            }

            _playlistsState.update {
                it.copy(
                    favorites = favorites ?: _favoritesState.value.tracks,
                    playlists = playlists ?: it.playlists,
                    isLoading = false,
                    errorMessage = errorMessage
                )
            }

            val current = playbackController.uiState.value.currentTrack
            if (current.id.isNotBlank()) {
                playbackController.setCurrentTrackFavorite((favorites ?: _favoritesState.value.tracks).any { t -> t.id == current.id })
            }
        }
    }

    fun loadPlaylistTracks(playlistId: String) {
        val current = _playlistDetailState.value
        // Skip only when this exact playlist is already loading or loaded clean.
        if (current.playlistId == playlistId && (current.isLoading || current.errorMessage == null)) return

        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            _playlistDetailState.update {
                it.copy(playlistId = playlistId, isLoading = true, errorMessage = null, tracks = emptyList())
            }
            val result = musicRepository.getPlaylistTracks(playlistId)
            result.onSuccess { tracks ->
                _playlistDetailState.update {
                    it.copy(playlistId = playlistId, tracks = tracks, isLoading = false, errorMessage = null)
                }
            }.onFailure { err ->
                // Ignore stale results after switching playlists quickly.
                if (_playlistDetailState.value.playlistId != playlistId) return@launch
                _playlistDetailState.update {
                    it.copy(playlistId = playlistId, isLoading = false, errorMessage = err.message ?: "Could not load playlist")
                }
            }
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            val result = musicRepository.removeTrackFromPlaylist(playlistId, trackId)
            result.onSuccess {
                _playlistDetailState.update { current ->
                    current.copy(
                        tracks = current.tracks.filterNot { it.id == trackId },
                        errorMessage = null
                    )
                }
                refreshLibrary()
            }.onFailure { err ->
                _playlistDetailState.update {
                    it.copy(errorMessage = err.message ?: "Could not remove track")
                }
            }
        }
    }

    fun playTracksFromList(tracks: List<Track>, startIndex: Int = 0) {
        playbackController.playTracks(tracks, startIndex)
    }

    fun refreshOutputDevice() {
        playbackController.refreshOutputDevice()
    }

    override fun onCleared() {
        downloadManager.release()
        playbackController.release()
        super.onCleared()
    }
}
