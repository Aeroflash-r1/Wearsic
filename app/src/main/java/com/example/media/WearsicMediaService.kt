package com.example.media

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
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

        // Media3's default media notification uses a system-managed playback
        // channel; no manual channel needed (and a manual one would leave
        // dead config behind once the custom provider is gone).

        // 1. Audio attributes for battery-conscious music playback
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // 2. Build MediaSourceFactory with Media3 Caching
        val cacheDataSourceFactory = WearsicPlaybackCacheManager.buildCacheDataSourceFactory(this)
        val mediaSourceFactory = DefaultMediaSourceFactory(cacheDataSourceFactory)

        // 3. Buffer policy: keep only a play-ahead window (~1 min) in the
        //    stream cache. The "guaranteed offline" layer is the Auto-Cache
        //    downloader (each played song is saved as a real file), so pulling
        //    the WHOLE song through the cache too made every song exist twice
        //    on disk and cost 2x network on first play.
        // bufferForPlaybackMs 1500: start audio after ~1.5s of buffer instead
        // of 2.5s — noticeably faster tap-to-sound over the tunnel. Rebuffer
        // stays conservative (5s) so a mid-song stall still waits properly.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30000,
                /* maxBufferMs = */ 60000,
                /* bufferForPlaybackMs = */ 1500,
                /* bufferForPlaybackAfterRebufferMs = */ 5000
            )
            .build()

        // 4. Initialize ExoPlayer
        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        this.player = exoPlayer

        // 5. Session Activity Intent + MediaSession. Wear OS creates the
        //    media ongoing activity (status ring) automatically from media
        //    notifications, and Media3 publishes the notification itself — so
        //    there is deliberately NO manual OngoingActivity here. Applying
        //    one manually on top of Media3's notification made the watch show
        //    TWO notifications (the media card + an orphaned ongoing-activity
        //    card), because every metadata update re-posts the notification
        //    without the manual ongoing-activity extras.
        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivityIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    /**
     * Swiping the app away from recents ends playback and tears the service
     * down cleanly: pause, release the player and session (removes the media
     * notification and foreground state), then stop.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val session = mediaSession
        if (session == null) {
            stopSelf()
            super.onTaskRemoved(rootIntent)
            return
        }

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