package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import androidx.compose.ui.tooling.preview.Preview
import com.example.model.PlaybackUiState
import com.example.model.Track
import com.example.ui.components.AmbientBlurTransformation
import com.example.ui.components.WearsicCircularIconButton
import com.example.ui.theme.WearsicBlack
import com.example.ui.theme.WearsicGlassBorder
import com.example.ui.theme.WearsicGlassFill
import com.example.ui.theme.WearsicLavenderSubtle
import com.example.ui.theme.WearsicTextMuted
import com.example.ui.theme.WearsicTextPrimary
import com.example.ui.theme.WearsicTheme
import com.example.ui.theme.WearsicVibrantLavender

import com.example.ui.util.wearsicRotaryScroll
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Player screen sized for 44mm round displays (e.g. Galaxy Watch 7).
 *
 * Signature element: the BLOB PROGRESS POD — an organic scalloped play/pause
 * button whose wavy perimeter is stroked by the playback progress. Centre tap
 * = play/pause; rim taps seek ∓10 s.
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
        // Ambient artwork glow behind the glass panels — pre-blurred ONCE at
        // load time (no runtime GPU blur cost).
        if (!playbackState.currentTrack.artworkUrl.isNullOrBlank()) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(playbackState.currentTrack.artworkUrl)
                    .size(256)
                    .transformations(AmbientBlurTransformation())
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.22f,
                modifier = Modifier.matchParentSize()
            )
        }

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

            item { TransportRow(
                positionMs = playbackState.currentPositionMs,
                durationMs = if (playbackState.currentTrack.id.isNotBlank()) playbackState.durationMs else 0L,
                isPlaying = playbackState.isPlaying,
                isBuffering = playbackState.isBuffering,
                onSkipPrevious = onSkipPrevious,
                onTogglePlayPause = onTogglePlayPause,
                onSkipNext = onSkipNext,
                onSeekForward = onSeekForward,
                onSeekBack = onSeekBack
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

/** Artwork-led track header. Only recomposes when the track actually changes. */
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
                    .size(512)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(WearsicGlassFill)
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Headphones,
                    contentDescription = "Music",
                    tint = WearsicVibrantLavender,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            color = WearsicTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp
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

/** Prev / blob pod / next transport controls. */
@Composable
private fun TransportRow(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onSkipPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekBack: () -> Unit,
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
            testTag = "player_previous_button"
        )

        Spacer(modifier = Modifier.width(5.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BlobProgressPod(
                positionMs = positionMs,
                durationMs = durationMs,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                onTogglePlayPause = onTogglePlayPause,
                onSeekBack = onSeekBack,
                onSeekForward = onSeekForward
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatMillis(positionMs),
                    color = WearsicTextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "  /  ",
                    color = WearsicTextMuted.copy(alpha = 0.5f),
                    fontSize = 10.sp
                )
                Text(
                    text = formatMillis(durationMs),
                    color = WearsicTextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.width(5.dp))

        WearsicCircularIconButton(
            icon = Icons.Rounded.SkipNext,
            contentDescription = "Next Track",
            onClick = onSkipNext,
            size = 40.dp,
            iconSize = 19.dp,
            testTag = "player_next_button"
        )
    }
}

/**
 * Generates the organic scalloped-blob outline:
 *     r(θ) = R · (1 + A · sin(k·θ + seed))
 * Sampled densely so lineTo segments render as a smooth wavy stamp shape.
 *
 * When [progressFraction] < 1 the returned path covers only that fraction of
 * the perimeter (starting at θ = -90°, i.e. top), which we stroke as progress.
 */
private fun blobPath(
    centerX: Float,
    centerY: Float,
    baseRadius: Float,
    lobeCount: Int,
    amplitudeRatio: Float,
    progressFraction: Float = 1f,
    samples: Int = 180
): Path {
    val path = Path()
    val endT = progressFraction.coerceIn(0.001f, 1f)
    val drawnSamples = (samples * endT).toInt().coerceAtLeast(2)
    val startAngle = -Math.PI.toFloat() / 2f   // start from the top

    for (i in 0..drawnSamples) {
        val t = i.toFloat() / samples.toFloat()
        val theta = startAngle + t * 2f * Math.PI.toFloat()
        val r = baseRadius * (1f + amplitudeRatio * sin(theta * lobeCount))
        val x = centerX + r * cos(theta)
        val y = centerY + r * sin(theta)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
}

private const val BLOB_LOBES = 7
private const val BLOB_AMPLITUDE = 0.085f

/**
 * The signature BLOB PROGRESS POD.
 *
 * An organic scalloped stamp-shaped button. Its wavy perimeter doubles as the
 * seek bar: a gradient stroke traces the outline according to playback
 * position. Centre tap = play/pause; rim taps seek ∓10 s. While buffering the
 * whole blob slowly rotates to signal activity (battery-friendly: no idle
 * animation while simply playing).
 */
@Composable
private fun BlobProgressPod(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val targetProgress = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val smoothProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 850),
        label = "blobFill"
    )

    // Slow rotation only while buffering (activity cue, zero cost when idle).
    val bufferingSpin = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isBuffering) {
        if (!isBuffering) return@LaunchedEffect
        var last = System.nanoTime()
        while (true) {
            delay(50)
            val now = System.nanoTime()
            bufferingSpin.floatValue += ((now - last) / 1_000_000_000f) * 60f
            last = now
        }
    }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Box(
        modifier = modifier
            .size(84.dp)
            .graphicsLayer {
                scaleX = if (pressed) 0.93f else 1f
                scaleY = if (pressed) 0.93f else 1f
            }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val distFromCenter = kotlin.math.hypot(pos.x - cx, pos.y - cy)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (distFromCenter <= size.width * 0.30f) {
                        onTogglePlayPause()
                    } else if (pos.x < cx) {
                        onSeekBack()
                    } else {
                        onSeekForward()
                    }
                }
            }
            .testTag("player_play_pause_button"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(78.dp)) {
            val rotation = if (isBuffering) bufferingSpin.floatValue else 0f
            rotate(degrees = rotation) {
                val baseRadius = this.size.minDimension / 2f - 6.dp.toPx()

                // Full wavy outline as the muted track
                drawBlobStroke(
                    centerX = this.center.x,
                    centerY = this.center.y,
                    baseRadius = baseRadius,
                    width = 4.dp.toPx(),
                    color = WearsicGlassBorder,
                    progressFraction = 1f
                )

                // Progress stroke tracing the same wavy edge (gradient fill)
                drawBlobStrokeWithGradient(
                    centerX = this.center.x,
                    centerY = this.center.y,
                    baseRadius = baseRadius,
                    width = 5.dp.toPx(),
                    progressFraction = smoothProgress,
                    colors = listOf(Color(0xFFE8D9FF), WearsicVibrantLavender, Color(0xFF8A5CF6))
                )

                // Frosted glass interior of the blob
                drawBlobFill(
                    centerX = this.center.x,
                    centerY = this.center.y,
                    baseRadius = baseRadius,
                    color = WearsicGlassFill
                )
            }
        }

        // Center control icon with a soft scrim so it reads over anything
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when {
                    isBuffering -> Icons.Rounded.HourglassEmpty
                    isPlaying -> Icons.Rounded.Pause
                    else -> Icons.Rounded.PlayArrow
                },
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun DrawScope.drawBlobStroke(
    centerX: Float,
    centerY: Float,
    baseRadius: Float,
    width: Float,
    color: Color,
    progressFraction: Float
) {
    drawPath(
        path = blobPath(centerX, centerY, baseRadius, BLOB_LOBES, BLOB_AMPLITUDE, progressFraction),
        color = color,
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawBlobStrokeWithGradient(
    centerX: Float,
    centerY: Float,
    baseRadius: Float,
    width: Float,
    progressFraction: Float,
    colors: List<Color>
) {
    drawPath(
        path = blobPath(centerX, centerY, baseRadius, BLOB_LOBES, BLOB_AMPLITUDE, progressFraction),
        brush = Brush.linearGradient(colors),
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawBlobFill(
    centerX: Float,
    centerY: Float,
    baseRadius: Float,
    color: Color
) {
    drawPath(
        path = blobPath(centerX, centerY, baseRadius, BLOB_LOBES, BLOB_AMPLITUDE, 1f),
        color = color
    )
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
        // Audio Output / Volume
        ActionCircle(
            icon = if (isBluetoothConnected) Icons.Rounded.Headphones else Icons.AutoMirrored.Rounded.VolumeUp,
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
            borderColor = if (isDownloaded) WearsicVibrantLavender else WearsicGlassBorder,
            testTag = "player_download_button"
        )

        Spacer(modifier = Modifier.width(12.dp))

        ActionCircle(
            icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = if (isFavorite) "Favorited" else "Favorite",
            onClick = onToggleFavorite,
            iconTint = if (isFavorite) WearsicVibrantLavender else WearsicTextMuted,
            backgroundTint = if (isFavorite) WearsicLavenderSubtle else Color.Unspecified,
            borderColor = if (isFavorite) WearsicVibrantLavender else WearsicGlassBorder,
            testTag = "player_favorite_button"
        )

        Spacer(modifier = Modifier.width(12.dp))

        ActionCircle(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
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
    borderColor: Color = WearsicGlassBorder,
    testTag: String = "player_action"
) {
    val haptic = LocalHapticFeedback.current

    // 48dp guaranteed touch area; visual circle stays 36dp, with press scale.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = if (pressed) 0.9f else 1f
                scaleY = if (pressed) 0.9f else 1f
            }
            .clickable(interactionSource = interaction, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (backgroundTint == Color.Unspecified) WearsicGlassFill else backgroundTint)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.10f), Color.Transparent)
                    )
                )
                .border(1.dp, borderColor, CircleShape),
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