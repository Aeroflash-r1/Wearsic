package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.example.model.PlaybackUiState
import com.example.model.Track
import com.example.ui.components.WearsicCircularIconButton
import com.example.ui.theme.WearsicBlack
import com.example.ui.theme.WearsicLavenderSubtle
import com.example.ui.theme.WearsicSurface
import com.example.ui.theme.WearsicSurfaceBorder
import com.example.ui.theme.WearsicSurfaceBorderSubtle
import com.example.ui.theme.WearsicTextMuted
import com.example.ui.theme.WearsicTextPrimary
import com.example.ui.theme.WearsicTextPrimaryDark
import com.example.ui.theme.WearsicTheme
import com.example.ui.theme.WearsicVibrantLavender

import com.example.ui.util.wearsicRotaryScroll

/**
 * Player screen sized for 44mm round displays (e.g. Galaxy Watch 7).
 *
 * The content lives in a ScalingLazyColumn: the transport controls sit at the
 * center of the circle and the secondary action row is reachable with a short
 * scroll — nothing is clipped by the round bezel. Crown/bezel scrolls the
 * screen. Sub-composables take primitives so the 1Hz progress ticks only
 * recompose the progress cluster.
 */
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
    onNavigateToQueue: () -> Unit = {},
    onDownloadTrack: (Track) -> Unit = {},
    isDownloaded: Boolean = false,
    isDownloading: Boolean = false,
    downloadProgress: Int = 0,
    modifier: Modifier = Modifier
) {
    val listState = rememberScalingLazyListState()

    ScreenScaffold(
        timeText = {},
        scrollState = listState,
        modifier = modifier
            .fillMaxSize()
            .background(WearsicBlack)
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .wearsicRotaryScroll(listState),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { PlayerHeader(
                artworkUrl = playbackState.currentTrack.artworkUrl,
                title = playbackState.currentTrack.title.ifBlank { "No Active Track" },
                artist = playbackState.currentTrack.artist.ifBlank { "Select from Library" },
                upNextTitle = playbackState.playlist.getOrNull(playbackState.currentTrackIndex + 1)?.title
            ) }

            item { ProgressCluster(
                positionMs = playbackState.currentPositionMs,
                durationMs = if (playbackState.currentTrack.id.isNotBlank()) playbackState.durationMs else 0L,
                isBuffering = playbackState.isBuffering
            ) }

            item { TransportRow(
                isPlaying = playbackState.isPlaying,
                isBuffering = playbackState.isBuffering,
                onSkipPrevious = onSkipPrevious,
                onTogglePlayPause = onTogglePlayPause,
                onSkipNext = onSkipNext
            ) }

            item { SecondaryActionsRow(
                isBluetoothConnected = playbackState.isBluetoothConnected,
                isFavorite = playbackState.currentTrack.isFavorite,
                hasUpNext = playbackState.playlist.size > playbackState.currentTrackIndex + 1,
                isDownloaded = isDownloaded,
                isDownloading = isDownloading,
                onOpenVolume = onNavigateToVolume,
                onToggleFavorite = onToggleFavorite,
                onOpenQueue = onNavigateToQueue,
                onDownload = { onDownloadTrack(playbackState.currentTrack) }
            ) }
        }
    }
}

/** Artwork + track titles. Only recomposes when the track actually changes. */
@Composable
private fun PlayerHeader(
    artworkUrl: String?,
    title: String,
    artist: String,
    upNextTitle: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .size(120)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(1.dp, WearsicVibrantLavender.copy(alpha = 0.4f), CircleShape)
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Headphones,
                contentDescription = "Music",
                tint = WearsicVibrantLavender,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = title,
            color = WearsicTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = artist,
            color = WearsicVibrantLavender,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (!upNextTitle.isNullOrBlank()) {
            Text(
                text = "Up next: $upNextTitle",
                color = WearsicTextMuted,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Progress bar + time labels. This is the ONLY part that ticks each second. */
@Composable
private fun ProgressCluster(
    positionMs: Long,
    durationMs: Long,
    isBuffering: Boolean,
    modifier: Modifier = Modifier
) {
    val duration = if (durationMs > 0) durationMs else 0L
    val progress = if (duration > 0) (positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(WearsicSurfaceBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(WearsicVibrantLavender)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatMillis(positionMs),
                color = WearsicTextMuted,
                fontSize = 11.sp
            )
            if (isBuffering) {
                Text(
                    text = "Buffering...",
                    color = WearsicVibrantLavender,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = formatMillis(duration),
                    color = WearsicTextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/** Prev / play-pause / next transport controls. */
@Composable
private fun TransportRow(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onSkipPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        WearsicCircularIconButton(
            icon = Icons.Rounded.SkipPrevious,
            contentDescription = "Previous Track (tap twice)",
            onClick = onSkipPrevious,
            size = 40.dp,
            iconSize = 19.dp,
            backgroundColor = WearsicSurface,
            iconTint = WearsicTextPrimary,
            borderColor = WearsicSurfaceBorderSubtle,
            testTag = "player_previous_button"
        )

        Spacer(modifier = Modifier.width(10.dp))

        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(WearsicVibrantLavender)
                .clickable(onClick = onTogglePlayPause)
                .testTag("player_play_pause_button"),
            contentAlignment = Alignment.Center
        ) {
            if (isBuffering) {
                Icon(
                    imageVector = Icons.Rounded.HourglassEmpty,
                    contentDescription = "Buffering",
                    tint = WearsicTextPrimaryDark,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = WearsicTextPrimaryDark,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        WearsicCircularIconButton(
            icon = Icons.Rounded.SkipNext,
            contentDescription = "Next Track",
            onClick = onSkipNext,
            size = 40.dp,
            iconSize = 19.dp,
            backgroundColor = WearsicSurface,
            iconTint = WearsicTextPrimary,
            borderColor = WearsicSurfaceBorderSubtle,
            testTag = "player_next_button"
        )
    }
}

/**
 * Uniform circular action buttons in one short centered row.
 */
@Composable
private fun SecondaryActionsRow(
    isBluetoothConnected: Boolean,
    isFavorite: Boolean,
    hasUpNext: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onOpenVolume: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionCircle(
            icon = if (isBluetoothConnected) Icons.Rounded.Headphones else Icons.Rounded.VolumeUp,
            contentDescription = "Audio Output",
            onClick = onOpenVolume,
            iconTint = WearsicVibrantLavender,
            testTag = "player_output_button"
        )

        Spacer(modifier = Modifier.width(12.dp))

        ActionCircle(
            icon = when {
                isDownloading -> Icons.Rounded.HourglassEmpty
                isDownloaded -> Icons.Rounded.CheckCircle
                else -> Icons.Rounded.Download
            },
            contentDescription = when {
                isDownloading -> "Downloading"
                isDownloaded -> "Downloaded Offline"
                else -> "Download"
            },
            onClick = onDownload,
            iconTint = if (isDownloaded || isDownloading) WearsicVibrantLavender else WearsicTextMuted,
            backgroundTint = if (isDownloaded) WearsicLavenderSubtle else Color.Unspecified,
            borderColor = if (isDownloaded) WearsicVibrantLavender else WearsicSurfaceBorderSubtle,
            testTag = "player_download_button"
        )

        Spacer(modifier = Modifier.width(12.dp))

        ActionCircle(
            icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (isFavorite) "Favorited" else "Favorite",
            onClick = onToggleFavorite,
            iconTint = if (isFavorite) WearsicVibrantLavender else WearsicTextMuted,
            backgroundTint = if (isFavorite) WearsicLavenderSubtle else Color.Unspecified,
            borderColor = if (isFavorite) WearsicVibrantLavender else WearsicSurfaceBorderSubtle,
            testTag = "player_favorite_button"
        )

        Spacer(modifier = Modifier.width(12.dp))

        ActionCircle(
            icon = Icons.Rounded.QueueMusic,
            contentDescription = "Queue",
            onClick = onOpenQueue,
            iconTint = if (hasUpNext) WearsicVibrantLavender else WearsicTextMuted,
            testTag = "player_queue_button"
        )
    }
}

@Composable
private fun ActionCircle(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    iconTint: Color,
    modifier: Modifier = Modifier,
    backgroundTint: Color = Color.Unspecified,
    borderColor: Color = WearsicSurfaceBorderSubtle,
    testTag: String = "player_action"
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (backgroundTint == Color.Unspecified) WearsicSurface else backgroundTint)
            .border(1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.size(17.dp)
        )
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
            playbackState = PlaybackUiState(
                currentTrack = Track(id = "1", title = "Weather with You", artist = "Crowded House"),
                isPlaying = true,
                playlist = listOf(
                    Track(id = "1", title = "Weather with You", artist = "Crowded House"),
                    Track(id = "2", title = "Don't Dream It's Over", artist = "Crowded House")
                )
            ),
            onTogglePlayPause = {},
            onSkipNext = {},
            onSkipPrevious = {},
            onToggleFavorite = {},
            onNavigateToVolume = {}
        )
    }
}