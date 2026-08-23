package com.example.tile

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.example.media.WearsicMediaService

/**
 * Executes playback commands coming from the system Tile without opening the
 * app: connects a lightweight MediaController to WearsicMediaService, runs the
 * command, then stops.
 */
class TileCommandService : android.app.Service() {

    companion object {
        const val ACTION_TOGGLE = "com.example.tile.TOGGLE"
        const val ACTION_PREV = "com.example.tile.PREV"
        const val ACTION_NEXT = "com.example.tile.NEXT"
    }

    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        connectAndRun { controller ->
            when (action) {
                ACTION_TOGGLE -> if (controller.isPlaying) controller.pause() else {
                    if (controller.playbackState == androidx.media3.common.Player.STATE_IDLE ||
                        controller.playbackState == androidx.media3.common.Player.STATE_ENDED
                    ) controller.prepare()
                    controller.play()
                }
                ACTION_PREV -> controller.seekToPreviousMediaItem()
                ACTION_NEXT -> controller.seekToNextMediaItem()
            }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun connectAndRun(block: (MediaController) -> Unit) {
        if (controllerFuture != null) return // already connecting; drop this tap
        val token = SessionToken(this, ComponentName(this, WearsicMediaService::class.java))
        val future = MediaController.Builder(applicationContext, token).buildAsync()
        controllerFuture = future
        future.addListener({
            controllerFuture = null
            try {
                val controller = future.get()
                block(controller)
                controller.release()
            } catch (_: Exception) {
                stopSelf()
            }
        }, MoreExecutors.directExecutor())
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onDestroy()
    }

    private fun pendingIntent(action: String): PendingIntent =
        PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, TileCommandService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun togglePendingIntent(): PendingIntent = pendingIntent(ACTION_TOGGLE)
    fun prevPendingIntent(): PendingIntent = pendingIntent(ACTION_PREV)
    fun nextPendingIntent(): PendingIntent = pendingIntent(ACTION_NEXT)
}