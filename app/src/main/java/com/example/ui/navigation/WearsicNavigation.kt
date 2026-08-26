package com.example.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.example.MainActivity
import com.example.data.db.DownloadState
import com.example.model.Track
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.AlbumsScreen
import com.example.ui.screens.StorageStatsContent
import com.example.ui.screens.ArtistsScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlaceholderScreen
import com.example.ui.screens.PlaylistDetailScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.QueueScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.VolumeScreen
import com.example.ui.viewmodel.ArtistGroup
import com.example.ui.viewmodel.WearsicPlayerViewModel

@Composable
fun WearsicApp(
    modifier: Modifier = Modifier,
    playerViewModel: WearsicPlayerViewModel = viewModel(),
    timeText: @Composable () -> Unit = {
        // Slightly smaller system clock than the default Wear size.
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(base.density, fontScale = 0.85f)
        ) {
            TimeText()
        }
    }
) {
    val navController = rememberSwipeDismissableNavController()
    val playbackState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val searchState by playerViewModel.searchState.collectAsStateWithLifecycle()
    val serverUrl by playerViewModel.serverUrl.collectAsStateWithLifecycle()
    val cacheLimitMb by playerViewModel.cacheLimitMb.collectAsStateWithLifecycle()
    val connectionTestState by playerViewModel.connectionTestState.collectAsStateWithLifecycle()
    val downloads by playerViewModel.downloads.collectAsStateWithLifecycle()
    val autoCacheEnabled by playerViewModel.autoCacheEnabled.collectAsStateWithLifecycle()
    val radioState by playerViewModel.radioState.collectAsStateWithLifecycle()
    val apiKey by playerViewModel.apiKey.collectAsStateWithLifecycle()
    val storageStats by playerViewModel.storageStats.collectAsStateWithLifecycle()
    val sleepRemainingMs by playerViewModel.sleepRemainingMs.collectAsStateWithLifecycle()
    val albumsState by playerViewModel.albumsState.collectAsStateWithLifecycle()
    val artistsState by playerViewModel.artistsState.collectAsStateWithLifecycle()
    val recentTracks by playerViewModel.recentTracks.collectAsStateWithLifecycle()
    val favoritesState by playerViewModel.favoritesState.collectAsStateWithLifecycle()
    val playlistsState by playerViewModel.playlistsState.collectAsStateWithLifecycle()
    val playlistDetailState by playerViewModel.playlistDetailState.collectAsStateWithLifecycle()

    val currentTrackDownload = downloads.find { it.trackId == playbackState.currentTrack.id }
    val isCurrentTrackDownloaded = currentTrackDownload?.isCompleted() == true
    val isCurrentTrackDownloading = currentTrackDownload?.downloadState == DownloadState.DOWNLOADING.name || currentTrackDownload?.downloadState == DownloadState.QUEUED.name
    val currentTrackDownloadProgress = currentTrackDownload?.progress ?: 0

    // Stable snapshots: the 1Hz position ticks must not recompose screens
    // that do not render progress. remember() returns the same instance until
    // one of the displayed fields actually changes.
    val libraryPlaybackState = remember(
        playbackState.currentTrack.id,
        playbackState.currentTrack.title,
        playbackState.currentTrack.artist,
        playbackState.isPlaying
    ) { playbackState }

    val queuePlaybackState = remember(
        playbackState.currentTrack.id,
        playbackState.currentTrackIndex,
        playbackState.playlist.size,
        playbackState.isPlaying
    ) { playbackState }

    // Execute actions requested from the system Tile (opens app briefly).
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1500)
        MainActivity.pendingTileAction?.let { action ->
            MainActivity.pendingTileAction = null
            playerViewModel.handleTileAction(action)
        }
    }

    AppScaffold(
        timeText = timeText,
        modifier = modifier
    ) {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = Screen.Library.route
        ) {
            // 1. Library Screen (Start destination)
            composable(Screen.Library.route) {
                LibraryScreen(
                    playbackState = libraryPlaybackState,
                    recentTracks = recentTracks,
                    onPlayRecentTrack = { tracks, index ->
                        playerViewModel.playTracksFromList(tracks, index)
                        navController.navigate(Screen.Player.route)
                    },
                    onNavigateToSearch = {
                        navController.navigate(Screen.Search.route)
                    },
                    onNavigateToDownloads = {
                        navController.navigate(Screen.Downloads.route)
                    },
                    onNavigateToPlaylists = {
                        navController.navigate(Screen.Playlists.route)
                    },
                    onNavigateToAlbums = {
                        navController.navigate(Screen.Albums.route)
                    },
                    onNavigateToArtists = {
                        navController.navigate(Screen.Artists.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    },
                    onNavigateToPlayer = {
                        navController.navigate(Screen.Player.route)
                    }
                )
            }

            // 2. Search Screen (API-connected music discovery)
            composable(Screen.Search.route) {
                SearchScreen(
                    searchState = searchState,
                    onQuerySelected = { query ->
                        playerViewModel.search(query)
                    },
                    onSearchTextChanged = { text ->
                        playerViewModel.onSearchTextChanged(text)
                    },
                    onTrackSelected = { track ->
                        playerViewModel.playTrack(track)
                        navController.navigate(Screen.Player.route)
                    },
                    onDownloadTrack = { track ->
                        playerViewModel.startDownload(track)
                    },
                    onAddToQueue = { track ->
                        playerViewModel.addToQueue(track)
                    },
                    playlists = playlistsState.playlists,
                    onCreatePlaylistAndAdd = { name, track ->
                        playerViewModel.createPlaylistAndAdd(name, track)
                    },
                    onAddToPlaylist = { playlistId, track ->
                        playerViewModel.addToPlaylist(playlistId, track)
                    }
                )
            }

            // 3. Player Screen (Connected to real Media3 Playback + Offline awareness)
            composable(Screen.Player.route) {
                PlayerScreen(
                    playbackState = playbackState,
                    onTogglePlayPause = {
                        playerViewModel.togglePlayPause()
                    },
                    onSkipNext = {
                        playerViewModel.skipToNext()
                    },
                    onSkipPrevious = {
                        playerViewModel.skipToPrevious()
                    },
                    onSeekForward = {
                        playerViewModel.seekForward()
                    },
                    onSeekBack = {
                        playerViewModel.seekBack()
                    },
                    onToggleFavorite = {
                        playerViewModel.toggleFavorite()
                    },
                    onNavigateToVolume = {
                        navController.navigate(Screen.Volume.route)
                    },
                    onNavigateToQueue = {
                        navController.navigate(Screen.Queue.route)
                    },
                    onDownloadTrack = { track ->
                        playerViewModel.startDownload(track)
                    },
                    isDownloaded = isCurrentTrackDownloaded,
                    isDownloading = isCurrentTrackDownloading,
                    downloadProgress = currentTrackDownloadProgress
                )
            }

            // 4. Volume & Output Screen
            composable(Screen.Volume.route) {
                VolumeScreen(
                    currentOutputDevice = playbackState.outputDeviceName,
                    sleepRemainingMs = sleepRemainingMs,
                    onSleepTimerSet = { minutes ->
                        playerViewModel.setSleepTimer(minutes)
                    },
                    onOutputDeviceChanged = {
                        playerViewModel.refreshOutputDevice()
                    }
                )
            }

            // 5. Settings Screen (Persistent Server URL, Cache & Downloads Management)
            composable(Screen.Settings.route) {
                SettingsScreen(
                    serverUrl = serverUrl,
                    connectionTestState = connectionTestState,
                    onServerUrlChanged = { newUrl ->
                        playerViewModel.saveServerUrl(newUrl)
                    },
                    onTestConnection = { urlToTest ->
                        playerViewModel.testConnection(urlToTest)
                    },
                    cacheLimitMb = cacheLimitMb,
                    onCacheLimitChanged = { newLimit ->
                        playerViewModel.saveCacheLimit(newLimit)
                    },
                    apiKey = apiKey,
                    onApiKeyChanged = { key ->
                        playerViewModel.setApiKey(key)
                    },
                    onOpenStorage = {
                        playerViewModel.refreshStorageStats()
                        navController.navigate(Screen.Storage.route)
                    },
                    autoCacheEnabled = autoCacheEnabled,
                    onAutoCacheToggled = { enabled ->
                        playerViewModel.setAutoCacheEnabled(enabled)
                    },
                    onCleanCache = { onResult ->
                        playerViewModel.cleanPlaybackCache(onResult)
                    },
                    onClearDownloads = {
                        playerViewModel.clearAllDownloads()
                    }
                )
            }

            // 6. Queue Screen (Up-next list with jump-to & remove)
            composable(Screen.Queue.route) {
                QueueScreen(
                    playbackState = queuePlaybackState,
                    onPlayItem = { index ->
                        playerViewModel.seekToQueueItem(index)
                    },
                    onRemoveItem = { index ->
                        playerViewModel.removeFromQueue(index)
                    },
                    onClearQueue = {
                        playerViewModel.clearQueue()
                    },
                    shuffleEnabled = playbackState.shuffleEnabled,
                    repeatMode = playbackState.repeatMode,
                    onToggleShuffle = { playerViewModel.toggleShuffle() },
                    onCycleRepeat = { playerViewModel.cycleRepeatMode() },
                    radioState = radioState,
                    onStartRadio = { playerViewModel.startRadio() }
                )
            }

            // 7. Downloads Screen (Full offline playback and download queue management)
            composable(Screen.Downloads.route) {
                DownloadsScreen(
                    downloads = downloads,
                    onPlayTrack = { track ->
                        playerViewModel.playTrack(track)
                        navController.navigate(Screen.Player.route)
                    },
                    onDeleteDownload = { trackId ->
                        playerViewModel.deleteDownload(trackId)
                    },
                    onCancelDownload = { trackId ->
                        playerViewModel.cancelDownload(trackId)
                    },
                    onClearAllDownloads = {
                        playerViewModel.clearAllDownloads()
                    }
                )
            }

            // Storage stats
            composable(Screen.Storage.route) {
                StorageStatsContent(
                    autoCount = storageStats.autoCount,
                    autoMb = storageStats.autoMb,
                    manualCount = storageStats.manualCount,
                    manualMb = storageStats.manualMb,
                    streamCacheMb = storageStats.streamCacheMb,
                    onPurgeStreamCache = { playerViewModel.purgeStreamCache() },
                    onClearAutoCached = { playerViewModel.clearAutoCachedDownloads() }
                )
            }

            // Playlists & Favorites hub
            composable(Screen.Playlists.route) {
                PlaylistsScreen(
                    playlistsState = playlistsState,
                    onRefresh = {
                        playerViewModel.refreshLibrary()
                    },
                    onNavigateToFavorites = {
                        navController.navigate(Screen.Favorites.route)
                    },
                    onOpenPlaylist = { playlist ->
                        navController.navigate(Screen.PlaylistDetail.createRoute(playlist.id, playlist.name))
                    },
                    onRemovePlaylist = { id ->
                        playerViewModel.removePlaylist(id)
                    }
                )
            }

            // Favorites track list
            composable(Screen.Favorites.route) {
                FavoritesScreen(
                    favoritesState = favoritesState,
                    onRefresh = {
                        playerViewModel.refreshFavorites()
                    },
                    onPlayTrack = { tracks, index ->
                        playerViewModel.playTracksFromList(tracks, index)
                        navController.navigate(Screen.Player.route)
                    },
                    onDownloadTrack = { track ->
                        playerViewModel.startDownload(track)
                    },
                    onRemoveFavorite = { trackId ->
                        playerViewModel.removeFavorite(trackId)
                    },
                    playlists = playlistsState.playlists,
                    onCreatePlaylistAndAdd = { name, track ->
                        playerViewModel.createPlaylistAndAdd(name, track)
                    },
                    onAddToPlaylist = { playlistId, track ->
                        playerViewModel.addToPlaylist(playlistId, track)
                    }
                )
            }

            // Playlist detail track list
            composable(Screen.PlaylistDetail.route) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getString("id") ?: ""
                val playlistName = backStackEntry.arguments?.getString("name") ?: "Playlist"
                PlaylistDetailScreen(
                    playlistId = playlistId,
                    playlistName = playlistName,
                    detailState = playlistDetailState,
                    onLoadTracks = { id ->
                        playerViewModel.loadPlaylistTracks(id)
                    },
                    onPlayTrack = { tracks, index ->
                        playerViewModel.playTracksFromList(tracks, index)
                        navController.navigate(Screen.Player.route)
                    },
                    onDownloadTrack = { track ->
                        playerViewModel.startDownload(track)
                    },
                    onRemoveTrack = { id, trackId ->
                        playerViewModel.removeTrackFromPlaylist(id, trackId)
                    },
                    playlists = playlistsState.playlists,
                    onCreatePlaylistAndAdd = { name, track ->
                        playerViewModel.createPlaylistAndAdd(name, track)
                    },
                    onAddToPlaylist = { playlistId, track ->
                        playerViewModel.addToPlaylist(playlistId, track)
                    }
                )
            }

            // Albums (search + open as queue)
            composable(Screen.Albums.route) {
                AlbumsScreen(
                    albumsState = albumsState,
                    onQueryChanged = { query ->
                        playerViewModel.searchAlbums(query)
                    },
                    onOpenAlbum = { album ->
                        navController.navigate(Screen.PlaylistDetail.createRoute(album.id, album.name))
                    }
                )
            }

            // Artists (local grouping of saved songs)
            composable(Screen.Artists.route) {
                ArtistsScreen(
                    artistsState = artistsState,
                    onRefresh = { playerViewModel.refreshArtists() },
                    onPlayArtistSongs = { group, index ->
                        playerViewModel.playTracksFromList(group.songs, index)
                        navController.navigate(Screen.Player.route)
                    }
                )
            }
        }
    }
}
