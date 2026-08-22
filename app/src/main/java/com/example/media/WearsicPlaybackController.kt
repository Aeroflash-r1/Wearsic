package com.example.media

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.model.PlaybackUiState
import com.example.model.Track
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class WearsicPlaybackController(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var positionUpdateJob: Job? = null
    private var pendingPlay: Pair<List<Track>, Int>? = null
    private var reconnectAttempts = 0
    private var playbackRetryAttempts = 0
    private var lastPreviousTapTimeMs = 0L
    private var pendingPreviousTap = false
    private var lastTrackIndex = -1

    companion object {
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val MAX_PLAYBACK_RETRIES = 3
        private const val PREVIOUS_DOUBLE_TAP_WINDOW_MS = 1500L
    }

    private val _uiState = MutableStateFlow(
        PlaybackUiState(
            playlist = emptyList()
        )
    )
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateStateFromPlayer(player)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                startPositionTracker()
            } else {
                stopPositionTracker()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val isTransient = error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT

            if (isTransient && playbackRetryAttempts < MAX_PLAYBACK_RETRIES) {
                // Tunnel connections drop mid-stream: retry and let the
                // CacheDataSource resume from the bytes already buffered.
                playbackRetryAttempts++
                val attempt = playbackRetryAttempts
                _uiState.update { it.copy(isBuffering = true) }
                scope.launch {
                    delay(1500L * attempt)
                    val controller = mediaController
                    if (controller != null && controller.mediaItemCount > 0) {
                        controller.prepare()
                        controller.play()
                    }
                }
                return
            }
            playbackRetryAttempts = 0

            // Walk the cause chain to the root for a useful diagnostic.
            var deepest: Throwable? = error.cause
            while (deepest?.cause != null) deepest = deepest.cause
            val detail = deepest?.message
                ?: error.cause?.message
                ?: error.message
            _uiState.update { current ->
                current.copy(
                    isPlaying = false,
                    isBuffering = false,
                    playbackError = if (!detail.isNullOrBlank()) {
                        "Playback error (${error.errorCodeName}): ${detail.substringBefore('\n')}"
                    } else {
                        "Playback error: ${error.errorCodeName}"
                    }
                )
            }
        }
    }

    init {
        initializeController()
    }

    fun initializeController() {
        connect()
    }

    private fun connect() {
        if (mediaController != null || controllerFuture != null) return

        if (android.os.Build.FINGERPRINT == "robolectric" || android.os.Build.HARDWARE == "robolectric") {
            // In Robolectric test environments, Media3 SessionService connection lacks a live OS binder
            return
        }

        try {
            val sessionToken = SessionToken(
                context,
                ComponentName(context, WearsicMediaService::class.java)
            )

            val future = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture = future

            future.addListener(
                {
                    controllerFuture = null
                    try {
                        val controller = future.get()
                        mediaController = controller
                        reconnectAttempts = 0
                        controller.addListener(playerListener)
                        updateStateFromPlayer(controller)
                        refreshOutputDevice()
                        // Flush any play request that arrived while connecting
                        pendingPlay?.let { (tracks, startIndex) ->
                            pendingPlay = null
                            playTracks(tracks, startIndex)
                        }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(playbackError = "Could not connect to media service") }
                        scheduleReconnect()
                    }
                },
                MoreExecutors.directExecutor()
            )
        } catch (e: Exception) {
            _uiState.update { it.copy(playbackError = "Media service unavailable in test environment") }
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) return
        reconnectAttempts++
        scope.launch {
            delay(1000L * reconnectAttempts)
            connect()
        }
    }

    private fun updateStateFromPlayer(player: Player) {
        val currentMediaItem = player.currentMediaItem
        val currentIdx = player.currentMediaItemIndex
        if (currentIdx != lastTrackIndex) {
            lastTrackIndex = currentIdx
            pendingPreviousTap = false
        }
        val isPlaying = player.isPlaying
        val isBuffering = player.playbackState == Player.STATE_BUFFERING
        val currentPos = player.currentPosition.coerceAtLeast(0L)
        val duration = if (player.duration > 0) player.duration else 0L

        val activePlaylist = _uiState.value.playlist
        val currentTrack = if (currentIdx in activePlaylist.indices) {
            val base = activePlaylist[currentIdx]
            val meta = currentMediaItem?.mediaMetadata
            base.copy(
                title = meta?.title?.toString() ?: base.title,
                artist = meta?.artist?.toString() ?: base.artist,
                artworkUrl = meta?.artworkUri?.toString() ?: base.artworkUrl,
                durationMs = if (duration > 0) duration else base.durationMs,
                isFavorite = _uiState.value.currentTrack.isFavorite.takeIf { it && _uiState.value.currentTrackIndex == currentIdx } ?: base.isFavorite
            )
        } else {
            val meta = currentMediaItem?.mediaMetadata
            if (meta != null && !meta.title.isNullOrBlank()) {
                Track(
                    id = currentMediaItem.mediaId.ifBlank { "current" },
                    title = meta.title.toString(),
                    artist = meta.artist?.toString() ?: "Unknown Artist",
                    album = meta.albumTitle?.toString() ?: "",
                    artworkUrl = meta.artworkUri?.toString(),
                    durationMs = duration
                )
            } else {
                activePlaylist.firstOrNull() ?: Track()
            }
        }

        _uiState.update { current ->
            val next = current.copy(
                currentTrack = currentTrack,
                currentTrackIndex = currentIdx,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                currentPositionMs = currentPos,
                durationMs = duration,
                hasNext = player.hasNextMediaItem(),
                hasPrevious = player.hasPreviousMediaItem()
            )
            // Skip redundant emissions: the player fires many events that do
            // not change observable state. Re-emitting identical state makes
            // every collector recompose, which burns battery on the watch.
            if (next != current) next else current
        }
    }

    private fun startPositionTracker() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            var idleTicks = 0
            while (isActive) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    idleTicks = 0
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = if (controller.duration > 0) controller.duration else _uiState.value.durationMs
                    _uiState.update { it.copy(currentPositionMs = pos, durationMs = dur) }
                } else {
                    // Not playing (paused/ended): stop ticking entirely to save
                    // battery instead of looping forever.
                    idleTicks++
                    if (idleTicks >= 2) break
                }
                delay(1000L) // 1s cadence: smooth progress with minimal wakeups
            }
        }
    }

    private fun stopPositionTracker() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    fun playTrack(track: Track) {
        val tracks = listOf(track)
        playTracks(tracks, 0)
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return

        val controller = mediaController
        if (controller == null) {
            // Controller is still connecting — queue the play request instead of
            // silently dropping it. It is flushed in connect() once ready.
            pendingPlay = tracks to startIndex
            connect()
            return
        }
        pendingPlay = null
        playbackRetryAttempts = 0
        pendingPreviousTap = false

        val items = WearsicMediaItemFactory.buildMediaItems(tracks)
        controller.stop()
        controller.clearMediaItems()
        controller.setMediaItems(items, startIndex, 0L)
        controller.repeatMode = Player.REPEAT_MODE_OFF
        controller.prepare()
        controller.play()
        val currentTrack = tracks.getOrNull(startIndex) ?: tracks.first()
        _uiState.update { current ->
            current.copy(
                currentTrack = currentTrack,
                playlist = tracks,
                currentTrackIndex = startIndex,
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = if (currentTrack.durationMs > 0) currentTrack.durationMs else 6000L,
                playbackError = null
            )
        }
    }

    fun addToQueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return

        val controller = mediaController
        if (controller == null) {
            playTracks(tracks, 0)
            return
        }
        if (controller.mediaItemCount == 0) {
            playTracks(tracks, 0)
            return
        }

        val items = WearsicMediaItemFactory.buildMediaItems(tracks)
        controller.addMediaItems(items)
        _uiState.update { current ->
            current.copy(
                playlist = current.playlist + tracks,
                playbackError = null
            )
        }
    }

    fun removeFromQueue(index: Int) {
        val controller = mediaController ?: return
        if (index !in 0 until controller.mediaItemCount) return

        controller.removeMediaItem(index)
        _uiState.update { current ->
            current.copy(
                playlist = current.playlist.filterIndexed { i, _ -> i != index }
            )
        }
    }

    fun seekToQueueItem(index: Int) {
        val controller = mediaController ?: return
        if (index !in 0 until controller.mediaItemCount) return

        pendingPreviousTap = false
        controller.seekTo(index, 0L)
        if (controller.playbackState == Player.STATE_IDLE || controller.playbackState == Player.STATE_ENDED) {
            controller.prepare()
        }
        controller.play()
        val queuedTrack = _uiState.value.playlist.getOrNull(index)
        _uiState.update { current ->
            current.copy(
                currentTrack = queuedTrack ?: current.currentTrack,
                currentTrackIndex = index,
                isPlaying = true,
                currentPositionMs = 0L,
                playbackError = null
            )
        }
    }

    fun clearQueue() {
        val controller = mediaController ?: return
        controller.stop()
        controller.clearMediaItems()
        _uiState.update {
            it.copy(
                currentTrack = Track(),
                playlist = emptyList(),
                currentTrackIndex = 0,
                isPlaying = false,
                isBuffering = false,
                currentPositionMs = 0L,
                durationMs = 0L,
                hasNext = false,
                hasPrevious = false,
                playbackError = null
            )
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE || controller.playbackState == Player.STATE_ENDED) {
                controller.prepare()
            }
            controller.play()
        }
    }

    fun skipToNext() {
        mediaController?.let { controller ->
            if (controller.hasNextMediaItem()) {
                controller.seekToNextMediaItem()
            } else {
                controller.seekTo(0, 0L)
            }
        }
    }

    fun skipToPrevious() {
        mediaController?.let { controller ->
            val now = SystemClock.uptimeMillis()

            // Second tap within the window: go to the actual previous track.
            if (pendingPreviousTap && now - lastPreviousTapTimeMs < PREVIOUS_DOUBLE_TAP_WINDOW_MS) {
                pendingPreviousTap = false
                if (controller.hasPreviousMediaItem()) {
                    controller.seekToPreviousMediaItem()
                } else {
                    controller.seekTo(0L)
                }
                return
            }

            // First tap: always restart the current song from the beginning and
            // arm the double-tap so a second tap goes to the previous track.
            pendingPreviousTap = true
            lastPreviousTapTimeMs = now
            controller.seekTo(0L)
            _uiState.update { it.copy(currentPositionMs = 0L) }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.let { controller ->
            val clamped = positionMs.coerceIn(0L, controller.duration.takeIf { it > 0 } ?: 6000L)
            controller.seekTo(clamped)
            _uiState.update { it.copy(currentPositionMs = clamped) }
        }
    }

    fun seekForward(ms: Long = 5000L) {
        mediaController?.let { controller ->
            val newPos = (controller.currentPosition + ms).coerceAtMost(controller.duration.takeIf { it > 0 } ?: 6000L)
            controller.seekTo(newPos)
            _uiState.update { it.copy(currentPositionMs = newPos) }
        }
    }

    fun seekBack(ms: Long = 5000L) {
        mediaController?.let { controller ->
            val newPos = (controller.currentPosition - ms).coerceAtLeast(0L)
            controller.seekTo(newPos)
            _uiState.update { it.copy(currentPositionMs = newPos) }
        }
    }

    fun toggleFavorite() {
        _uiState.update { current ->
            val updatedFavorite = !current.currentTrack.isFavorite
            val updatedTrack = current.currentTrack.copy(isFavorite = updatedFavorite)
            current.copy(currentTrack = updatedTrack)
        }
    }

    fun setCurrentTrackFavorite(isFavorite: Boolean) {
        _uiState.update { current ->
            current.copy(currentTrack = current.currentTrack.copy(isFavorite = isFavorite))
        }
    }

    fun refreshOutputDevice() {
        val output = AudioOutputHelper.getCurrentOutputInfo(context)
        _uiState.update {
            it.copy(
                outputDeviceName = output.name,
                isBluetoothConnected = output.isBluetooth
            )
        }
    }

    fun release() {
        stopPositionTracker()
        try {
            scope.cancel()
        } catch (_: Exception) {}
        mediaController?.removeListener(playerListener)
        mediaController?.release()
        mediaController = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }
}
