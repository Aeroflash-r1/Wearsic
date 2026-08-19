package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay5
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import androidx.compose.ui.tooling.preview.Preview
import com.example.model.PlaybackUiState
import com.example.model.Track
import com.example.ui.components.WearsicCircularIconButton
import com.example.ui.theme.WearsicBlack
import com.example.ui.theme.WearsicError
import com.example.ui.theme.WearsicLavenderContainer
import com.example.ui.theme.WearsicLavenderSubtle
import com.example.ui.theme.WearsicSurface
import com.example.ui.theme.WearsicSurfaceActive
import com.example.ui.theme.WearsicSurfaceBorder
import com.example.ui.theme.WearsicSurfaceBorderSubtle
import com.example.ui.theme.WearsicTextMuted
import com.example.ui.theme.WearsicTextPrimary
import com.example.ui.theme.WearsicTextPrimaryDark
import com.example.ui.theme.WearsicTextSecondary
import com.example.ui.theme.WearsicTheme
import com.example.ui.theme.WearsicVibrantLavender

import androidx.compose.foundation.focusable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent

@Composable
fun PlayerScreen(
    playbackState: PlaybackUiState,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekForward: () -> Unit = {},
    onSeekBack: () -> Unit = {},
    onToggleFavorite: () -> Unit,
    onNavigateToVolume: () -> Unit,
    onDownloadTrack: (Track) -> Unit = {},
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    downloadProgress: Int = 0,
    modifier: Modifier = Modifier
) {
    val duration = if (playbackState.currentTrack.id.isNotBlank() && playbackState.durationMs > 0) playbackState.durationMs else 0L
    val progress = if (duration > 0) (playbackState.currentPositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    ScreenScaffold(
        modifier = modifier
            .fillMaxSize()
            .background(WearsicBlack)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onRotaryScrollEvent { event ->
                    if (event.verticalScrollPixels > 0) {
                        onSeekForward()
                    } else if (event.verticalScrollPixels < 0) {
                        onSeekBack()
                    }
                    true
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 1. Grouped Track Info & Artwork (Top-Center)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                if (!playbackState.currentTrack.artworkUrl.isNullOrBlank()) {
                    val context = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(playbackState.currentTrack.artworkUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = playbackState.currentTrack.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .border(1.dp, WearsicVibrantLavender.copy(alpha = 0.4f), CircleShape)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Headphones,
                        contentDescription = "Music",
                        tint = WearsicVibrantLavender,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(bottom = 2.dp)
                    )
                }

                Text(
                    text = playbackState.currentTrack.title.ifBlank { "No Active Track" },
                    color = WearsicTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )

                Text(
                    text = playbackState.currentTrack.artist.ifBlank { "Select from Library" },
                    color = WearsicVibrantLavender,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }

            // 2. Playback Timeline / Progress Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Progress track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(WearsicSurfaceBorder)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = progress)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(WearsicVibrantLavender)
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Time labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatMillis(playbackState.currentPositionMs),
                        color = WearsicTextMuted,
                        fontSize = 9.sp
                    )
                    if (playbackState.isBuffering) {
                        Text(
                            text = "Buffering...",
                            color = WearsicVibrantLavender,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = formatMillis(duration),
                            color = WearsicTextMuted,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            // Error display if any
            if (playbackState.playbackError != null) {
                Text(
                    text = playbackState.playbackError,
                    color = WearsicError,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }

            // 3. Central Playback Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous Track Button
                WearsicCircularIconButton(
                    icon = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous Track",
                    onClick = onSkipPrevious,
                    size = 38.dp,
                    iconSize = 18.dp,
                    backgroundColor = WearsicSurface,
                    iconTint = WearsicTextPrimary,
                    borderColor = WearsicSurfaceBorderSubtle,
                    testTag = "player_previous_button"
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Large Central Play/Pause Button
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(WearsicVibrantLavender)
                        .clickable(onClick = onTogglePlayPause)
                        .testTag("player_play_pause_button"),
                    contentAlignment = Alignment.Center
                ) {
                    if (playbackState.isBuffering) {
                        Icon(
                            imageVector = Icons.Rounded.HourglassEmpty,
                            contentDescription = "Buffering",
                            tint = WearsicTextPrimaryDark,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            tint = WearsicTextPrimaryDark,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Next Track Button
                WearsicCircularIconButton(
                    icon = Icons.Rounded.SkipNext,
                    contentDescription = "Next Track",
                    onClick = onSkipNext,
                    size = 38.dp,
                    iconSize = 18.dp,
                    backgroundColor = WearsicSurface,
                    iconTint = WearsicTextPrimary,
                    borderColor = WearsicSurfaceBorderSubtle,
                    testTag = "player_next_button"
                )
            }

            // 4. Lower Controls: Output, Download & Favorite
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Output Destination Button
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(WearsicSurface)
                        .border(1.dp, WearsicSurfaceBorderSubtle, CircleShape)
                        .clickable(onClick = onNavigateToVolume)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("player_output_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (playbackState.isBluetoothConnected) Icons.Rounded.Headphones else Icons.Rounded.VolumeUp,
                            contentDescription = "Audio Output",
                            tint = WearsicVibrantLavender,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = playbackState.outputDeviceName,
                            color = WearsicTextPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Download Button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isDownloaded) WearsicLavenderSubtle else WearsicSurface)
                        .border(
                            1.dp,
                            if (isDownloaded) WearsicVibrantLavender else WearsicSurfaceBorderSubtle,
                            CircleShape
                        )
                        .clickable { onDownloadTrack(playbackState.currentTrack) }
                        .testTag("player_download_button"),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isDownloading -> {
                            Icon(
                                imageVector = Icons.Rounded.HourglassEmpty,
                                contentDescription = "Downloading",
                                tint = WearsicVibrantLavender,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        isDownloaded -> {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Downloaded Offline",
                                tint = WearsicVibrantLavender,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = "Download",
                                tint = WearsicTextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Favorite Heart Button
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (playbackState.currentTrack.isFavorite) WearsicLavenderSubtle else WearsicSurface)
                        .border(
                            1.dp,
                            if (playbackState.currentTrack.isFavorite) WearsicVibrantLavender else WearsicSurfaceBorderSubtle,
                            CircleShape
                        )
                        .clickable(onClick = onToggleFavorite)
                        .testTag("player_favorite_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playbackState.currentTrack.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (playbackState.currentTrack.isFavorite) "Favorited" else "Favorite",
                        tint = if (playbackState.currentTrack.isFavorite) WearsicVibrantLavender else WearsicTextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

private fun formatMillis(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
fun PlayerScreenPreview() {
    WearsicTheme {
        PlayerScreen(
            playbackState = PlaybackUiState(),
            onTogglePlayPause = {},
            onSkipNext = {},
            onSkipPrevious = {},
            onToggleFavorite = {},
            onNavigateToVolume = {}
        )
    }
}
