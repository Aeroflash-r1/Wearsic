package com.wearsic.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.AmbientMode
import androidx.wear.compose.foundation.LocalAmbientModeManager
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.LinearProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.wearsic.app.WearsicApplication
import com.wearsic.app.data.model.Track
import com.wearsic.app.playback.PlaybackState

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
) {
    val playbackManager =
        (LocalContext.current.applicationContext as WearsicApplication).playbackManager

    val playbackState by playbackManager.playbackState.collectAsStateWithLifecycle()
    val currentPosition by playbackManager.currentPosition.collectAsStateWithLifecycle()
    val duration by playbackManager.duration.collectAsStateWithLifecycle()
    val currentTrack by playbackManager.currentTrack.collectAsStateWithLifecycle()

    val ambientManager = LocalAmbientModeManager.current
    val isAmbient = ambientManager?.currentAmbientMode is AmbientMode.Ambient

    if (isAmbient) {
        AmbientPlayerContent(track = currentTrack)
    } else {
        ActivePlayerContent(
            playbackState = playbackState,
            currentPosition = currentPosition,
            duration = duration,
            currentTrack = currentTrack,
            onPlayPause = {
                if (playbackState == PlaybackState.Playing) {
                    playbackManager.pause()
                } else {
                    playbackManager.play()
                }
            },
            onSkipNext = { playbackManager.skipToNext() },
        )
    }
}

@Composable
private fun AmbientPlayerContent(track: Track?) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = track?.title ?: "No track",
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun ActivePlayerContent(
    playbackState: PlaybackState,
    currentPosition: Long,
    duration: Long,
    currentTrack: Track?,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = currentTrack?.title ?: "No track",
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        if (currentTrack != null) {
            Text(
                text = currentTrack.channel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (duration > 0L) {
            LinearProgressIndicator(
                progress = { (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onPlayPause) {
                Text(
                    when (playbackState) {
                        is PlaybackState.Playing -> "Pause"
                        else -> "Play"
                    },
                )
            }
            Button(onClick = onSkipNext) {
                Text("Next")
            }
        }
    }
}
