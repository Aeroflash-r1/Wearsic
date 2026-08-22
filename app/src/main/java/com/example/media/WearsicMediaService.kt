package com.example.media

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.media.cache.WearsicPlaybackCacheManager

class WearsicMediaService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // 1. Audio attributes for battery-conscious music playback
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // 2. Build MediaSourceFactory with Media3 Caching
        val cacheDataSourceFactory = WearsicPlaybackCacheManager.buildCacheDataSourceFactory(this)
        val mediaSourceFactory = DefaultMediaSourceFactory(cacheDataSourceFactory)

        // 3. Initialize ExoPlayer with Cache MediaSourceFactory
        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        this.player = exoPlayer

        // 5. Create Session Activity Intent for Now Playing / System media controls
        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 6. Initialize MediaSession
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivityIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val session = mediaSession
        if (session == null) {
            stopSelf()
            super.onTaskRemoved(rootIntent)
            return
        }

        // Swiping the app away from recents ends playback and tears the service
        // down cleanly: pause, release the player and session (which also
        // removes the media notification and the foreground state), then stop.
        val activePlayer = session.player
        if (activePlayer.isPlaying) {
            activePlayer.pause()
        }
        activePlayer.stop()
        activePlayer.release()
        session.release()
        mediaSession = null
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }
}
