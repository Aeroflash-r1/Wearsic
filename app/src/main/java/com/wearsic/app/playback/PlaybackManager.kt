package com.wearsic.app.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.wearsic.app.data.model.Track
import com.wearsic.app.data.repository.WearsicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlaybackManager(
    private val appContext: Context,
    repository: WearsicRepository,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val player: ExoPlayer = ExoPlayer.Builder(appContext)
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .build()

    var repository: WearsicRepository = repository
        private set

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private var currentIndex = -1
    private val preResolvedUrls = mutableMapOf<String, String>()
    private var positionPollJob: Job? = null

    init {
        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_IDLE -> _playbackState.value = PlaybackState.Idle
                        Player.STATE_BUFFERING -> _playbackState.value = PlaybackState.Buffering
                        Player.STATE_READY -> {
                            _playbackState.value = if (player.playWhenReady) PlaybackState.Playing else PlaybackState.Paused
                        }
                        Player.STATE_ENDED -> {
                            autoAdvanceNext()
                        }
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (player.playbackState == Player.STATE_READY) {
                        _playbackState.value = if (isPlaying) PlaybackState.Playing else PlaybackState.Paused
                    }
                    if (isPlaying) startPositionPolling() else stopPositionPolling()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updateCurrentTrackFromPlayer()
                }
            },
        )
    }

    fun updateRepository(repository: WearsicRepository) {
        this.repository = repository
    }

    fun setQueue(tracks: List<Track>, startIndex: Int) {
        _queue.value = tracks.toList()
        currentIndex = startIndex
        preResolvedUrls.clear()
        if (tracks.isNotEmpty() && startIndex in tracks.indices) {
            _currentTrack.value = tracks[startIndex]
        }
    }

    fun play(trackId: String? = null) {
        if (trackId != null) {
            val index = _queue.value.indexOfFirst { it.id == trackId }
            if (index >= 0) {
                currentIndex = index
            }
        }
        val track = _currentTrack.value ?: return
        playTrack(track)
    }

    fun pause() {
        player.pause()
    }

    fun skipToNext() {
        val list = _queue.value
        if (currentIndex + 1 < list.size) {
            val nextIndex = currentIndex + 1
            currentIndex = nextIndex
            val track = list[nextIndex]
            _currentTrack.value = track
            playTrack(track)
        } else {
            player.stop()
            _playbackState.value = PlaybackState.Idle
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun release() {
        stopPositionPolling()
        player.release()
        scope.cancel()
    }

    private fun playTrack(track: Track) {
        scope.launch {
            _playbackState.value = PlaybackState.Buffering
            val streamUrl = preResolvedUrls.remove(track.id) ?: run {
                val result = repository.getStream(track.id)
                result.getOrNull()?.streamUrl ?: run {
                    _playbackState.value = PlaybackState.Error("Could not resolve stream URL")
                    return@launch
                }
            }
            val mediaItem = MediaItem.fromUri(streamUrl)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        }
    }

    private fun autoAdvanceNext() {
        val list = _queue.value
        if (currentIndex + 1 < list.size) {
            val nextIndex = currentIndex + 1
            currentIndex = nextIndex
            val track = list[nextIndex]
            _currentTrack.value = track
            playTrack(track)
        } else {
            _playbackState.value = PlaybackState.Idle
        }
    }

    private fun startPositionPolling() {
        positionPollJob?.cancel()
        positionPollJob = scope.launch {
            while (isActive) {
                _currentPosition.value = player.currentPosition
                _duration.value = player.duration
                checkPreFetch()
                delay(250)
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollJob?.cancel()
        positionPollJob = null
    }

    private fun checkPreFetch() {
        val list = _queue.value
        if (currentIndex < 0 || currentIndex >= list.size - 1) return
        val dur = _duration.value
        if (dur <= 0) return
        val remaining = dur - _currentPosition.value
        if (remaining in 0..PRE_FETCH_THRESHOLD_MS) {
            val nextTrack = list[currentIndex + 1]
            if (!preResolvedUrls.containsKey(nextTrack.id)) {
                scope.launch {
                    repository.getStream(nextTrack.id).onSuccess { info ->
                        preResolvedUrls[nextTrack.id] = info.streamUrl
                    }
                }
            }
        }
    }

    private fun updateCurrentTrackFromPlayer() {
        if (currentIndex in _queue.value.indices) {
            _currentTrack.value = _queue.value[currentIndex]
        }
    }

    companion object {
        private const val PRE_FETCH_THRESHOLD_MS = 20_000L
    }
}
