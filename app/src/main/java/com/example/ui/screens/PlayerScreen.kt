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
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.example.ui.theme.WearsicSurface
import com.example.ui.theme.WearsicSurfaceBorder
import com.example.ui.theme.WearsicTextMuted
import com.example.ui.theme.WearsicTheme
import com.example.ui.theme.WearsicVibrantLavender
import com.example.ui.theme.WearsicViolet
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * NOW PLAYING — Wear OS 6 media controls, matched to the reference watch.
 *
 * Solid pale control pills with black glyphs over the blurred album artwork:
 *
 *     9:30 (live clock)
 *     ◉ logo  Song name
 *             Artist name
 *
 *     (◀)   ~ wavy progress blob ~   (▶)
 *
 *     [ 🎧⇉ output ]        [ ⋮ ]
 *
 * Wearsic's violet survives only where the reference lets colour speak: the
 * white logo chip glyph and the progress sweep around the wavy blob.
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
    val track = playbackState.currentTrack
    val hasTrack = track.id.isNotBlank()
    val haptic = LocalHapticFeedback.current

    var showMoreSheet by remember { mutableStateOf(false) }

    ScreenScaffold(
        modifier = modifier
            .fillMaxSize()
            .background(WearsicBlack)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                            .blur(26.dp)
                            .scale(1.18f)
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
                            0f to WearsicBlack.copy(alpha = 0.50f),
                            0.25f to WearsicBlack.copy(alpha = 0.05f),
                            0.70f to WearsicBlack.copy(alpha = 0.10f),
                            1f to WearsicBlack.copy(alpha = 0.62f)
                        )
                    )
            )

            // ── Content ────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)
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
                // 1. Clock
                LiveClock()

                // 2. Metadata + transport (centred middle block)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // White logo chip + song name / artist (reference layout).
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = WearsicViolet,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(9.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = if (hasTrack) track.title else "No Active Track",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (hasTrack) track.artist else "Play from Library to begin",
                                color = Color.White.copy(alpha = 0.68f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Transport: pale skip circles + pale wavy progress blob.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        PaleRoundButton(
                            icon = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous Track (tap twice)",
                            onClick = onSkipPrevious,
                            testTag = "player_previous_button"
                        )
                        Spacer(modifier = Modifier.width(9.dp))
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
                        Spacer(modifier = Modifier.width(9.dp))
                        PaleRoundButton(
                            icon = Icons.Rounded.SkipNext,
                            contentDescription = "Next Track",
                            onClick = onSkipNext,
                            testTag = "player_next_button"
                        )
                    }
                }

                // 3. Bottom capsules — output (left, wider) + ⋮ More (right).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally)
                ) {
                    // Pale pill with the two overlapping output glyphs.
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
                            .weight(1f)
                            .height(42.dp)
                            .graphicsLayer {
                                scaleX = outputScale
                                scaleY = outputScale
                            }
                            .clip(RoundedCornerShape(24.dp))
                            .background(PaleControl)
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
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 8.dp, y = 7.dp)
                                .size(11.dp)
                        )
                    }

                    // ⋮ More capsule.
                    val morePressed = remember { MutableInteractionSource() }
                    val morePressedState by morePressed.collectIsPressedAsState()
                    val moreScale by animateFloatAsState(
                        targetValue = if (morePressedState) 0.94f else 1f,
                        animationSpec = tween(durationMillis = 80),
                        label = "moreCapsule"
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 52.dp, height = 42.dp)
                            .graphicsLayer {
                                scaleX = moreScale
                                scaleY = moreScale
                            }
                            .clip(RoundedCornerShape(24.dp))
                            .background(PaleControl)
                            .clickable(interactionSource = morePressed, indication = null) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showMoreSheet = true
                            }
                            .testTag("player_more_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "More actions",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
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

/** Solid pale surface + black glyphs — the reference's control colour. */
private val PaleControl = Color(0xFFDEE4E0)

/** Live clock — the reference's 9:30, updated silently every minute. */
@Composable
private fun LiveClock() {
    var text by remember { mutableStateOf(formatClock()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            val fresh = formatClock()
            if (fresh != text) text = fresh
        }
    }
    Text(
        text = text,
        color = Color.White,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 2.dp)
    )
}

/**
 * The scalloped "wavy" play/pause blob — solid pale like the reference, with
 * a black glyph and a thin outline whose sweep doubles as the progress ring.
 */
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

            // Solid pale fill.
            drawPath(path = path, color = PaleControl)

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
                        color = WearsicVibrantLavender,
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
            tint = Color.Black,
            modifier = Modifier.size(26.dp)
        )
    }
}

/** Solid pale circle button with a black glyph (skip back / skip forward). */
@Composable
private fun PaleRoundButton(
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
            .size(50.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(CircleShape)
            .background(PaleControl)
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
            tint = Color.Black,
            modifier = Modifier.size(21.dp)
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

private fun formatClock(): String {
    val now = LocalTime.now()
    return String.format("%02d:%02d", now.hour, now.minute)
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
