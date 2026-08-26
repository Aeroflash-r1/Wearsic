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
import androidx.core.net.toUri
import com.example.media.download.WearsicDownloadManager
import com.example.model.PlaybackUiState
import com.example.model.Playlist
import com.example.model.Track
import com.example.network.model.ConnectionTestState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class SearchUiState(
    val query: String = "",
    val results: List<Track> = emptyList(),
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
    val hasSearched: Boolean = false,
    val suggestions: List<String> = emptyList()
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

data class AlbumsUiState(
    val query: String = "",
    val albums: List<com.example.model.Album> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class StorageStats(
    val autoCount: Int = 0,
    val autoMb: Double = 0.0,
    val manualCount: Int = 0,
    val manualMb: Double = 0.0,
    val streamCacheMb: Double = 0.0
)

data class ArtistGroup(
    val name: String,
    val songs: List<Track>
)

data class ArtistsUiState(
    val artists: List<ArtistGroup> = emptyList(),
    val isLoading: Boolean = false
)

sealed interface RadioState {
    data object Idle : RadioState
    data object Loading : RadioState
    data class Error(val message: String) : RadioState
}
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

    val apiKey: StateFlow<String> = preferencesRepository.apiKeyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /**
     * SLEEP TIMER: pauses playback after [minutes], fading volume to zero over
     * the final 10 seconds. minutes <= 0 cancels an active timer.
     */
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        playbackController.setVolumeScale(1f)
        if (minutes <= 0) {
            _sleepRemainingMs.value = 0L
            return
        }
        val durationMs = minutes * 60_000L
        val endsAt = System.currentTimeMillis() + durationMs
        sleepJob = viewModelScope.launch {
            var lastTick = System.nanoTime()
            while (true) {
                delay(250)
                val now = System.currentTimeMillis()
                val remaining = endsAt - now
                _sleepRemainingMs.value = remaining.coerceAtLeast(0L)
                if (remaining <= 10_000L) {
                    // Fade over the final 10 seconds.
                    val nowNano = System.nanoTime()
                    val dtSec = ((nowNano - lastTick) / 1_000_000_000f).coerceIn(0.05f, 0.5f)
                    lastTick = nowNano
                    val step = dtSec / 10f
                    val currentVol = 1f - ((10_000f - remaining.toFloat()) / 10_000f)
                    playbackController.setVolumeScale((currentVol - step).coerceIn(0f, 1f))
                }
                if (remaining <= 0L) break
            }
            playbackController.pause()
            playbackController.setVolumeScale(1f)
            _sleepRemainingMs.value = 0L
        }
    }

    fun setApiKey(key: String) {
        viewModelScope.launch {
            musicRepository.saveApiKey(key)
        }
    }

    val autoCacheEnabled: StateFlow<Boolean> = preferencesRepository.autoCacheEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** How many auto-cached (offline) songs to keep before oldest-first eviction. */
    val offlineLimit: StateFlow<Int> = preferencesRepository.offlineLimitFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WearsicPreferencesRepository.DEFAULT_OFFLINE_LIMIT)

    fun saveOfflineLimit(limitSongs: Int) {
        downloadManager.maxAutoCachedTracks = limitSongs.coerceIn(5, 200)
        viewModelScope.launch {
            preferencesRepository.saveOfflineLimit(limitSongs)
        }
    }

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
    private var radioJob: Job? = null
    private var suggestionsJob: Job? = null

    private val _radioState = MutableStateFlow<RadioState>(RadioState.Idle)
    val radioState: StateFlow<RadioState> = _radioState.asStateFlow()

    private val _sleepRemainingMs = MutableStateFlow(0L)
    val sleepRemainingMs: StateFlow<Long> = _sleepRemainingMs.asStateFlow()
    private var sleepJob: Job? = null

    private val _albumsState = MutableStateFlow(AlbumsUiState())
    val albumsState: StateFlow<AlbumsUiState> = _albumsState.asStateFlow()

    private val _artistsState = MutableStateFlow(ArtistsUiState())
    val artistsState: StateFlow<ArtistsUiState> = _artistsState.asStateFlow()

    private val _storageStats = MutableStateFlow(StorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    @Volatile
    private var hiddenPlaylistIds: Set<String> = emptySet()

    // Lightweight client used only to pre-warm the server's stream cache for
    // the NEXT track in the queue, so it starts playing instantly.
    private val warmUpClient = com.example.network.WearsicHttp.client.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    @Volatile
    private var lastWarmUpTrackId: String? = null

    init {
        // Sync the optional API key into the HTTP client for every request.
        viewModelScope.launch {
            preferencesRepository.apiKeyFlow.collect { key ->
                musicRepository.refreshApiKeyWith(key)
            }
        }
        viewModelScope.launch {
            preferencesRepository.hiddenPlaylistsFlow.collect { hidden ->
                hiddenPlaylistIds = hidden
                // Re-filter already-loaded playlists instantly.
                _playlistsState.update { it.copy(playlists = it.playlists.filter { pl -> pl.id !in hidden }) }
            }
        }
        // Apply the persisted offline-song limit to the download manager.
        viewModelScope.launch {
            preferencesRepository.offlineLimitFlow.collect { limit ->
                downloadManager.maxAutoCachedTracks = limit.coerceIn(5, 200)
            }
        }
        // No cache setup at startup: the cache limit defaults to 128MB and the
        // playback cache is built lazily by the media session. Applying the
        // limit here used to release/rebuild the SimpleCache while the player
        // was active, which caused IO errors on slow watches.

        // GUARANTEED offline layer: every streamed song that starts playing is
        // immediately saved as a real download (subject to the Auto-Cache
        // toggle, network availability and the 15-song auto-cache cap). This
        // replaces reliance on ExoPlayer's opaque stream cache — play a song
        // once online and it plays forever offline. The NEXT queued track is
        // also pre-warmed on the server to kill extraction delay.
        viewModelScope.launch {
            val autoCachedTrackIds = mutableSetOf<String>()
            var lastTrack: Track? = null

            playbackController.uiState.collect { state ->
                val track = state.currentTrack
                if (track.id.isNotBlank()) {
                    if (track.id != lastTrack?.id) {
                        // Track changed: save the previous one if mid-play.
                        lastTrack?.let { previous ->
                            maybeAutoCacheTrack(previous, autoCachedTrackIds)
                        }
                        lastTrack = track
                        recentRepository.recordPlayed(track)
                    }

                    // Save the CURRENT track too so even the first song of a
                    // session is protected.
                    maybeAutoCacheTrack(track, autoCachedTrackIds)

                    // Pre-warm the server for the upcoming song (1-byte request:
                    // the server resolves + caches the real stream URL so the
                    // next play() skips the multi-second extraction).
                    val nextTrack = state.playlist.getOrNull(state.currentTrackIndex + 1)
                    if (nextTrack != null && nextTrack.id != lastWarmUpTrackId) {
                        warmUpStream(nextTrack)
                    }
                }
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

    fun setAutoCacheEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoCacheEnabled(enabled)
        }
    }

    private fun maybeAutoCacheTrack(
        track: Track,
        evaluatedTrackIds: MutableSet<String>
    ) {
        if (track.id in evaluatedTrackIds) return
        if (!autoCacheEnabled.value) return
        // Only streamed tracks can be auto-cached; local files are already offline.
        if (!track.mediaUri.startsWith("http")) return
        if (!isNetworkAvailable()) return

        if (downloadManager.isDownloading(track.id)) return
        if (downloads.value.any { it.trackId == track.id && it.isCompleted() }) return

        evaluatedTrackIds.add(track.id)
        downloadManager.startDownload(track, autoCached = true)
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = getApplication<Application>()
                .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
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

    /**
     * Live search suggestions while typing (debounced 300 ms). The raw text is
     * mirrored into [SearchUiState.query] so the UI can bind to the ViewModel
     * as its single source of truth (no local remember-mirror that fights the
     * IME or drifts out of sync).
     */
    fun onSearchTextChanged(text: String) {
        _searchState.update { it.copy(query = text) }
        suggestionsJob?.cancel()
        if (text.isBlank()) {
            _searchState.update { it.copy(suggestions = emptyList()) }
            return
        }
        suggestionsJob = viewModelScope.launch {
            delay(300)
            val result = musicRepository.getSuggestions(text.trim())
            result.onSuccess { list ->
                _searchState.update { it.copy(suggestions = list.take(6)) }
            }.onFailure {
                _searchState.update { it.copy(suggestions = emptyList()) }
            }
        }
    }

    private var albumsJob: Job? = null

    fun searchAlbums(query: String) {
        if (query.isBlank()) {
            _albumsState.update { it.copy(query = "", albums = emptyList(), isLoading = false) }
            return
        }
        albumsJob?.cancel()
        _albumsState.update { it.copy(query = query, isLoading = true, errorMessage = null) }
        albumsJob = viewModelScope.launch {
            val result = musicRepository.searchAlbums(query.trim())
            result.onSuccess { albums ->
                _albumsState.update { it.copy(albums = albums, isLoading = false) }
            }.onFailure { err ->
                _albumsState.update {
                    it.copy(albums = emptyList(), isLoading = false, errorMessage = err.message ?: "Could not load albums")
                }
            }
        }
    }

    /** Local grouping of downloaded + favorite songs by artist name. */
    fun refreshArtists() {
        viewModelScope.launch {
            _artistsState.value = ArtistsUiState(isLoading = true)
            val downloaded = completedTracks.value
            val favorites = favoritesState.value.tracks
            val combined = (downloaded + favorites).distinctBy { it.id }
            val groups = combined
                .filter { it.artist.isNotBlank() }
                .groupBy { it.artist.trim().lowercase() }
                .map { (_, songs) -> ArtistGroup(name = songs.first().artist.trim(), songs = songs.sortedBy { it.title.lowercase() }) }
                .sortedBy { it.name.lowercase() }
            _artistsState.value = ArtistsUiState(artists = groups)
        }
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            val entities = downloadManager.allDownloadsFlow.first().filter { it.isCompleted() }
            val autoEntities = entities.filter { it.autoCached }
            val manualEntities = entities.filterNot { it.autoCached }
            val cacheBytes = withContext(Dispatchers.IO) {
                WearsicPlaybackCacheManager.getUsedCacheSizeBytes(getApplication<Application>())
            }
            _storageStats.value = StorageStats(
                autoCount = autoEntities.size,
                autoMb = autoEntities.sumOf { it.fileSizeBytes } / (1024.0 * 1024.0),
                manualCount = manualEntities.size,
                manualMb = manualEntities.sumOf { it.fileSizeBytes } / (1024.0 * 1024.0),
                streamCacheMb = cacheBytes / (1024.0 * 1024.0)
            )
        }
    }

    fun purgeStreamCache(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                WearsicPlaybackCacheManager.cleanCache(getApplication<Application>())
            }
            refreshStorageStats()
            onDone()
        }
    }

    fun clearAutoCachedDownloads() {
        viewModelScope.launch {
            val ids = downloadManager.allDownloadsFlow.first()
                .filter { it.isCompleted() && it.autoCached }
                .map { it.trackId }
            ids.forEach { deleteDownload(it) }
        }
    }

    fun toggleHiddenPlaylist(id: String) {
        viewModelScope.launch {
            preferencesRepository.toggleHiddenPlaylist(id)
        }
    }

    /**
     * DELETE a whole playlist server-side. The server's delete-track endpoint
     * treats videoId "*" as "remove the entire playlist" (FK cascades tracks).
     */
    fun removePlaylist(id: String) {
        viewModelScope.launch {
            musicRepository.removeTrackFromPlaylist(id, "*")
            refreshLibrary()
        }
    }

    fun addToPlaylist(playlistId: String, track: Track) {
        viewModelScope.launch {
            musicRepository.addTrackToPlaylist(playlistId, track)
        }
    }

    fun createPlaylistAndAdd(name: String, track: Track?) {
        viewModelScope.launch {
            val created = musicRepository.createPlaylist(name.trim())
            created.onSuccess { playlist ->
                if (track != null && track.id.isNotBlank()) {
                    musicRepository.addTrackToPlaylist(playlist.id, track)
                }
                refreshLibrary()
            }
        }
    }

    /**
     * RADIO: fetches songs related to the current track from the server and
     * appends them to the queue (skipping duplicates already queued).
     */
    fun startRadio() {
        val current = playbackController.uiState.value.currentTrack
        if (current.id.isBlank()) return
        radioJob?.cancel()
        radioJob = viewModelScope.launch {
            _radioState.value = RadioState.Loading
            val result = musicRepository.getRelated(current.id)
            result.onSuccess { related ->
                val existingIds = playbackController.uiState.value.playlist.map { it.id }.toSet()
                // Exclude 10min+ videos — those are usually full-album "mixes",
                // not individual songs.
                val fresh = related.filter { rel ->
                    rel.id !in existingIds && rel.durationMs in 1..600_000
                }
                if (fresh.isEmpty()) {
                    _radioState.value = RadioState.Idle
                    return@launch
                }
                playbackController.addToQueue(fresh)
                _radioState.value = RadioState.Idle
            }.onFailure { err ->
                _radioState.value = RadioState.Error(err.message ?: "Radio unavailable")
                // Auto-clear transient error after a moment.
                delay(2500)
                _radioState.value = RadioState.Idle
            }
        }
    }

    fun removeFromQueue(index: Int) {
        playbackController.removeFromQueue(index)
    }

    fun seekToQueueItem(index: Int) {
        playbackController.seekToQueueItem(index)
    }

    fun handleTileAction(action: String) {
        when (action) {
            "prev" -> skipToPrevious()
            "next" -> skipToNext()
            "toggle" -> togglePlayPause()
        }
    }

    fun toggleShuffle() {
        playbackController.toggleShuffle()
    }

    fun cycleRepeatMode() {
        playbackController.cycleRepeatMode()
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
            val playlists = playlistsResult.getOrNull()?.filter { it.id !in hiddenPlaylistIds }
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
            // Albums pass a full playlist URL; server playlists pass an id.
            var result = musicRepository.getPlaylistTracksFlexible(playlistId)
            if (result.isFailure) {
                // Heavy extractions (large albums) occasionally time out on
                // first hit — retry once silently.
                delay(1500)
                result = musicRepository.getPlaylistTracksFlexible(playlistId)
            }
            result.onSuccess { tracks ->
                if (_playlistDetailState.value.playlistId != playlistId) return@onSuccess
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
        viewModelScope.launch {
            // Offline-smart: swap every track for its downloaded file when one
            // exists, so saved songs NEVER re-stream from the server.
            val resolved = tracks.map { t ->
                downloadRepository.getDownloadedTrack(t.id) ?: t
            }
            playbackController.playTracks(resolved, startIndex.coerceIn(0, resolved.lastIndex))
        }
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
