package com.example.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.wear.ongoing.OngoingActivity
import com.example.MainActivity
import com.example.R
import com.example.media.cache.WearsicPlaybackCacheManager

class WearsicMediaService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    private val CHANNEL_ID = "wearsic_playback"
    private val NOTIFICATION_ID = 1001

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // Notification channel for playback + Ongoing Activity ring.
        getSystemService(NOTIFICATION_SERVICE).let {
            (it as NotificationManager).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW)
            )
        }

        // 1. Audio attributes for battery-conscious music playback
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // 2. Build MediaSourceFactory with Media3 Caching
        val cacheDataSourceFactory = WearsicPlaybackCacheManager.buildCacheDataSourceFactory(this)
        val mediaSourceFactory = DefaultMediaSourceFactory(cacheDataSourceFactory)

        // 3. Buffer policy: pull the WHOLE song through the cache while playing
        //    (~5 MB RAM at 70 kbps) so replaying any fully-played song works offline.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30000,
                /* maxBufferMs = */ 600000,
                /* bufferForPlaybackMs = */ 2500,
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

        ensureNotificationChannel()
        setMediaNotificationProvider(ongoingNotificationProvider())

        // 5. Session Activity Intent + MediaSession
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

    /** Wear OS Ongoing Activity: status ring around the app icon while playing. */
    private fun ongoingNotificationProvider(): MediaNotification.Provider {
        return object : MediaNotification.Provider {
            override fun createNotification(
                mediaSession: MediaSession,
                commandButtons: com.google.common.collect.ImmutableList<androidx.media3.session.CommandButton>,
                actionFactory: MediaNotification.ActionFactory,
                callback: MediaNotification.Provider.Callback
            ): androidx.media3.session.MediaNotification {
                ensureNotificationChannel()
                val player = mediaSession?.player
                val title = player?.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty().ifBlank { "Wearsic" }
                val text = player?.currentMediaItem?.mediaMetadata?.artist?.toString().orEmpty()

                val builder = NotificationCompat.Builder(this@WearsicMediaService, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setContentIntent(sessionActivityIntent())

                try {
                    OngoingActivity.Builder(this@WearsicMediaService, NOTIFICATION_ID, builder)
                        .setStaticIcon(R.drawable.ic_launcher_foreground)
                        .setTouchIntent(sessionActivityIntent())
                        .build()
                        .apply(this@WearsicMediaService)
                } catch (_: Exception) {
                    // Best-effort; base notification still works without the ring.
                }
                return androidx.media3.session.MediaNotification(NOTIFICATION_ID, builder.build())
            }

            override fun handleCustomCommand(
                mediaSession: MediaSession,
                action: String,
                extras: Bundle
            ): Boolean = false
        }
    }

    private fun sessionActivityIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW)
        )
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