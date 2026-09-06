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

class WearsicMediaService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // One-time migration from the old persistent stream cache: no playback
        // path writes to it anymore, so wipe any leftover directory (pure dead
        // storage duplicating completed downloads). Runs here — after the old
        // service instance (and its cache handles) has been released.
        WearsicStreamDataSource.deleteLegacyStreamCache(this)

        // Media3's default media notification uses a system-managed playback
        // channel; no manual channel needed (and a manual one would leave
        // dead config behind once the custom provider is gone).

        // 1-4. Build the player (audio attributes, memory-only buffering media
        // source, load policy) — shared with recreateSession().
        val exoPlayer = buildPlayer()

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
        // Self-healing: if the session was torn down by a previous lifecycle
        // event (or lost by a process/surface hiccup) while the service is
        // still alive, rebuild it instead of returning null forever. Returning
        // null wedges every future MediaController.connect() — the relaunch
        // then hangs on the splash screen with no way to recover.
        if (mediaSession == null) {
            runCatching { recreateSession() }
        }
        return mediaSession
    }

    /**
     * Swiping the app away from recents must NOT tear down playback: Wear OS
     * users expect music to continue with the app closed. The service stays
     * foregrounded via Media3's media notification while the player has
     * content; the system keeps the process alive and the session reachable,
     * so the next app launch connects instantly instead of inheriting a
     * half-released session (which previously wedged the relaunch). Media3
     * stops the service on its own once playback is idle/stopped and no
     * controller is bound.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        // Intentionally no teardown. Playback continues in the background;
        // Media3 stops the service automatically when it is no longer needed.
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

    /** Rebuilds player + session after a teardown, preserving nothing (the
     *  queue is gone with the old player; users re-select music). */
    private fun recreateSession() {
        player?.release()
        val exoPlayer = buildPlayer()
        this.player = exoPlayer
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

    @OptIn(UnstableApi::class)
    private fun buildPlayer(): ExoPlayer {
        // 1. Audio attributes for battery-conscious music playback
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // 2. Media source factory. NO persistent disk cache: playback buffers
        //    in memory only, and the sole permanent local copy of a song is
        //    the single Auto/Manual download file.
        val mediaSourceFactory = DefaultMediaSourceFactory(
            WearsicStreamDataSource.createDataSourceFactory(this)
        )

        // 3. Buffer policy: a modest in-memory window (ExoPlayer memory
        //    buffering only — nothing is written to disk).
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 30000,
                /* maxBufferMs = */ 60000,
                /* bufferForPlaybackMs = */ 1500,
                /* bufferForPlaybackAfterRebufferMs = */ 5000
            )
            .build()

        // 4. Player
        return ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }
}