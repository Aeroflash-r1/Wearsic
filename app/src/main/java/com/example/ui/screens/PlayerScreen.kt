package com.example.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import androidx.compose.ui.tooling.preview.Preview
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.PlaybackUiState
import com.example.model.Track
import com.example.ui.theme.WearsicBlack
import com.example.ui.theme.WearsicLavenderContainer
import com.example.ui.theme.WearsicSurface
import com.example.ui.theme.WearsicSurfaceBorder
import com.example.ui.theme.WearsicTextMuted
import com.example.ui.theme.WearsicTheme
import com.example.ui.theme.WearsicVibrantLavender
import com.example.ui.theme.WearsicViolet
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Wear OS now-playing screen with blurred artwork backdrop and centered transport controls. */
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
    val track = playbackState.currentTrack
    val hasTrack = track.id.isNotBlank()
    val haptic = LocalHapticFeedback.current

    var showMoreSheet by remember { mutableStateOf(false) }

    ScreenScaffold(
        modifier = modifier
            .fillMaxSize()
            .background(WearsicBlack)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val safeHorizontal = (maxWidth * 0.12f).coerceIn(14.dp, 22.dp)
            val safeTop = (maxHeight * 0.16f).coerceIn(24.dp, 34.dp)
            val safeBottom = (maxHeight * 0.12f).coerceIn(16.dp, 26.dp)
            // ── Backdrop: real album artwork, blurred full-bleed ───────────
            Crossfade(
                targetState = track.artworkUrl,
                animationSpec = tween(durationMillis = 260),
                label = "playerBackdrop"
            ) { artworkUrl ->
                if (!artworkUrl.isNullOrBlank()) {
                    val context = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(artworkUrl)
                            .size(720)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(34.dp)
                            .scale(1.24f)
                    )
                } else {
                    // No art: deep charcoal backdrop with a soft violet wash.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        WearsicVibrantLavender.copy(alpha = 0.12f),
                                        Color(0xFF141216),
                                        Color(0xFF0C0B0E)
                                    )
                                )
                            )
                    )
                }
            }

            // Legibility scrim — open in the middle, darker behind text zones.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to WearsicBlack.copy(alpha = 0.72f),
                            0.25f to WearsicBlack.copy(alpha = 0.38f),
                            0.70f to WearsicBlack.copy(alpha = 0.44f),
                            1f to WearsicBlack.copy(alpha = 0.78f)
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to WearsicViolet.copy(alpha = 0.18f),
                            0.5f to WearsicVibrantLavender.copy(alpha = 0.08f),
                            1f to WearsicViolet.copy(alpha = 0.22f)
                        )
                    )
            )

            // ── Content ────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = safeHorizontal,
                        end = safeHorizontal,
                        top = safeTop,
                        bottom = safeBottom
                    )
                    .onRotaryScrollEvent { event ->
                        if (event.verticalScrollPixels == 0f) {
                            false
                        } else {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (event.verticalScrollPixels > 0f) onSeekForward() else onSeekBack()
                            true
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TopActionButton(
                        icon = Icons.Rounded.MoreVert,
                        contentDescription = "More actions",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showMoreSheet = true
                        },
                        testTag = "player_more_button"
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                // 2. Metadata + transport (centred middle block)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (hasTrack) track.title else "No Active Track",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.82f)
                    )
                    Text(
                        text = if (hasTrack) track.artist else "Play from Library to begin",
                        color = WearsicVibrantLavender.copy(alpha = 0.84f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.8f)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        SecondaryTransportButton(
                            icon = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous Track (tap twice)",
                            onClick = onSkipPrevious,
                            testTag = "player_previous_button"
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        WavyPlayBlob(
                            isPlaying = playbackState.isPlaying,
                            isBuffering = playbackState.isBuffering,
                            progress = if (playbackState.durationMs > 0L) {
                                (playbackState.currentPositionMs.toFloat() / playbackState.durationMs)
                                    .coerceIn(0f, 1f)
                            } else {
                                0f
                            },
                            onTogglePlayPause = onTogglePlayPause
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        SecondaryTransportButton(
                            icon = Icons.Rounded.SkipNext,
                            contentDescription = "Next Track",
                            onClick = onSkipNext,
                            testTag = "player_next_button"
                        )
                    }
                }

                // 3. Bottom output capsule.
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.8f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    val outputPressed = remember { MutableInteractionSource() }
                    val outputPressedState by outputPressed.collectIsPressedAsState()
                    val outputScale by animateFloatAsState(
                        targetValue = if (outputPressedState) 0.94f else 1f,
                        animationSpec = tween(durationMillis = 80),
                        label = "outputCapsule"
                    )
                    // Reference composite: headphones with a small
                    // speaker-with-wave badge overlapping it on the right.
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = outputScale
                                scaleY = outputScale
                            }
                            .clip(RoundedCornerShape(22.dp))
                            .background(WearsicLavenderContainer.copy(alpha = 0.78f))
                            .border(1.dp, WearsicVibrantLavender.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                            .clickable(interactionSource = outputPressed, indication = null) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigateToVolume()
                            }
                            .testTag("player_output_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Headphones,
                            contentDescription = "Audio Output",
                            tint = WearsicVibrantLavender,
                            modifier = Modifier.size(19.dp)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = null,
                            tint = WearsicVibrantLavender,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 8.dp, y = 7.dp)
                                .size(12.dp)
                        )
                    }
                }
            }

            // ── ⋮ More action sheet ────────────────────────────────────────
            if (showMoreSheet) {
                MoreSheet(
                    isFavorite = track.isFavorite,
                    isDownloaded = isDownloaded,
                    isDownloading = isDownloading,
                    downloadProgress = downloadProgress,
                    hasTrack = hasTrack,
                    onDismiss = { showMoreSheet = false },
                    onToggleFavorite = {
                        showMoreSheet = false
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleFavorite()
                    },
                    onDownload = {
                        showMoreSheet = false
                        if (!isDownloaded && !isDownloading) onDownloadTrack(track)
                    },
                    onQueue = {
                        showMoreSheet = false
                        onNavigateToQueue()
                    }
                )
            }
        }
    }
}

/** Primary play/pause control with integrated progress stroke. */
@Composable
private fun WavyPlayBlob(
    isPlaying: Boolean,
    isBuffering: Boolean,
    progress: Float,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 80),
        label = "blobPress"
    )

    Box(
        modifier = modifier
            .size(64.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(interactionSource = interaction, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onTogglePlayPause()
            }
            .testTag("player_play_pause_button"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Equal radii keep the wavy stamp round, not wide.
            val radius = min(size.width, size.height) * 0.46f
            val strokeW = 2.dp.toPx()
            val path = Path()

            // Wavy outline: 8 soft lobes around a rounded blob.
            val steps = 180
            val scallops = 8
            val wave = 0.07f
            for (i in 0..steps) {
                val theta = i.toFloat() / steps * 2f * kotlin.math.PI.toFloat()
                val ripple = 1f + wave * cos(scallops * theta)
                val x = size.width / 2f + radius * ripple * cos(theta)
                val y = size.height / 2f + radius * ripple * sin(theta)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()

            // Violet/lavender fill.
            drawPath(
                path = path,
                brush = Brush.radialGradient(
                    colors = listOf(WearsicVibrantLavender, WearsicViolet),
                    center = center,
                    radius = size.minDimension * 0.8f
                )
            )

            // Thin base outline + violet progress sweep around the lobes.
            val outline = Stroke(width = strokeW, cap = StrokeCap.Round)
            drawPath(
                path = path,
                color = Color.Black.copy(alpha = 0.16f),
                style = outline
            )
            if (progress > 0f) {
                val measure = PathMeasure()
                measure.setPath(path, false)
                val total = measure.length
                if (total > 0f) {
                    val trace = Path()
                    measure.getSegment(0f, total * progress, trace, true)
                    drawPath(
                        path = trace,
                        color = Color.White.copy(alpha = 0.92f),
                        style = Stroke(width = strokeW + 0.5f, cap = StrokeCap.Round)
                    )
                }
            }
        }

        Icon(
            imageVector = when {
                isBuffering -> Icons.Rounded.HourglassEmpty
                isPlaying -> Icons.Rounded.Pause
                else -> Icons.Rounded.PlayArrow
            },
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = WearsicBlack,
            modifier = Modifier.size(26.dp)
        )
    }
}

/** Secondary round transport button. */
@Composable
private fun SecondaryTransportButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "player_skip_button"
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = tween(durationMillis = 80),
        label = "skipPress"
    )
    Box(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(CircleShape)
            .background(WearsicLavenderContainer.copy(alpha = 0.78f))
            .border(1.dp, WearsicVibrantLavender.copy(alpha = 0.5f), CircleShape)
            .clickable(interactionSource = interaction, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = WearsicVibrantLavender,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun TopActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 80),
        label = "topActionPress"
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(CircleShape)
            .background(WearsicLavenderContainer.copy(alpha = 0.78f))
            .border(1.dp, WearsicVibrantLavender.copy(alpha = 0.5f), CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = WearsicVibrantLavender,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** ⋮ More bottom sheet — favourite / download / queue. */
@Composable
private fun MoreSheet(
    isFavorite: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    downloadProgress: Int,
    hasTrack: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
    onQueue: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WearsicBlack.copy(alpha = 0.55f))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(WearsicSurface)
                .border(1.dp, WearsicSurfaceBorder, RoundedCornerShape(24.dp))
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MoreSheetRow(
                icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = if (isFavorite) "Favorited" else "Favorite",
                tint = if (isFavorite) WearsicVibrantLavender else Color.White.copy(alpha = 0.92f),
                enabled = hasTrack,
                onClick = onToggleFavorite,
                testTag = "player_favorite_button"
            )
            MoreSheetRow(
                icon = when {
                    isDownloading -> Icons.Rounded.HourglassEmpty
                    isDownloaded -> Icons.Rounded.CheckCircle
                    else -> Icons.Rounded.Download
                },
                label = when {
                    isDownloading -> "Downloading… $downloadProgress%"
                    isDownloaded -> "Downloaded Offline"
                    else -> "Download"
                },
                tint = if (isDownloaded || isDownloading) WearsicVibrantLavender else Color.White.copy(alpha = 0.92f),
                enabled = hasTrack && !isDownloaded && !isDownloading,
                onClick = onDownload,
                testTag = "player_download_button"
            )
            MoreSheetRow(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                label = "Queue",
                tint = Color.White.copy(alpha = 0.92f),
                enabled = true,
                onClick = onQueue,
                testTag = "player_queue_button"
            )
        }
    }
}

@Composable
private fun MoreSheetRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) tint else WearsicTextMuted,
            modifier = Modifier.size(21.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            color = if (enabled) Color.White.copy(alpha = 0.94f) else WearsicTextMuted,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
fun PlayerScreenPreview() {
    WearsicTheme {
        PlayerScreen(
            playbackState = PlaybackUiState(
                currentTrack = Track(id = "1", title = "Weather with You", artist = "Crowded House"),
                isPlaying = true,
                durationMs = 240_000L,
                currentPositionMs = 95_000L,
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
