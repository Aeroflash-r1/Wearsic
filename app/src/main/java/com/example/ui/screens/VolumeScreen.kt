package com.example.ui.screens

import android.content.Context
import android.media.AudioManager
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import androidx.compose.ui.tooling.preview.Preview
import com.example.media.AudioOutputHelper
import com.example.ui.components.WearsicCircularIconButton
import com.example.ui.components.WearsicScreenHeader
import com.example.ui.theme.WearsicBlack
import com.example.ui.theme.WearsicGlassBorder
import com.example.ui.theme.WearsicSurface
import com.example.ui.theme.WearsicSurfaceActive
import com.example.ui.theme.WearsicSurfaceBorderSubtle
import com.example.ui.theme.WearsicTextMuted
import com.example.ui.theme.WearsicTextPrimary
import com.example.ui.theme.WearsicTheme
import com.example.ui.theme.WearsicVibrantLavender

import com.example.ui.util.wearsicRotaryScroll

/**
 * VOLUME & OUTPUT — output picker first, then volume + sleep timer.
 *
 * The device picker matches the reference media-headphones design: on the
 * black backdrop, unselected outputs are deep-violet icon pills, while the
 * active output is a light-lavender pill carrying its dark icon and label
 * ("Headphones" / "Watch Speaker").
 */
@Composable
fun VolumeScreen(
    currentOutputDevice: String = "Watch Speaker",
    sleepRemainingMs: Long = 0L,
    onSleepTimerSet: (Int) -> Unit = {},
    onOutputDeviceChanged: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }

    // Read initial system media volume
    val maxVol = remember { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15 }
    val initialVolPercent = remember {
        val cur = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 10
        ((cur.toFloat() / maxVol.toFloat()) * 100).toInt()
    }

    var volumeLevel by remember { mutableIntStateOf(initialVolPercent) }
    var selectedOutput by remember { mutableStateOf(currentOutputDevice) }
    val listState = rememberScalingLazyListState()

    // "Bluetooth Audio", "Bluetooth: Buds 2", … all count as the headphones
    // output; anything that isn't the built-in speaker is headphones.
    val isHeadphonesActive =
        currentOutputDevice.isNotBlank() && !currentOutputDevice.equals("Watch Speaker", ignoreCase = true)
    val activeDeviceLabel = if (isHeadphonesActive) "Headphones" else "Watch Speaker"

    ScreenScaffold(
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
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            item {
                WearsicScreenHeader(
                    title = activeDeviceLabel,
                    subtitle = "Volume & Output"
                )
            }

            // ── Output picker (reference style pills) ─────────────────────
            item {
                SectionLabel("Audio Output")
            }

            // Output: Watch Speaker
            item {
                OutputDevicePill(
                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                    label = "Watch Speaker",
                    isSelected = !isHeadphonesActive,
                    onClick = {
                        selectedOutput = "Watch Speaker"
                        onOutputDeviceChanged("Watch Speaker")
                    },
                    testTag = "output_watch_speaker"
                )
            }

            // Output: Bluetooth Headphones (active device in reference)
            item {
                OutputDevicePill(
                    icon = Icons.Rounded.Headphones,
                    label = "Headphones",
                    isSelected = isHeadphonesActive,
                    onClick = {
                        selectedOutput = "Bluetooth Audio"
                        onOutputDeviceChanged("Bluetooth Audio")
                        try {
                            context.startActivity(AudioOutputHelper.createBluetoothSettingsIntent())
                        } catch (_: Exception) {
                            // Intent fallback
                        }
                    },
                    testTag = "output_bluetooth_audio"
                )
            }

            // ── Volume ────────────────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(6.dp))
            }
            item {
                SectionLabel("Volume")
            }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(WearsicSurface)
                        .border(1.dp, WearsicSurfaceBorderSubtle, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("volume_controls_container")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Decrease Volume
                        WearsicCircularIconButton(
                            icon = Icons.Rounded.Remove,
                            contentDescription = "Decrease Volume",
                            onClick = {
                                if (volumeLevel > 0) {
                                    volumeLevel = (volumeLevel - 10).coerceAtLeast(0)
                                    val streamVal = ((volumeLevel / 100f) * maxVol).toInt()
                                    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, streamVal, 0)
                                }
                            },
                            size = 36.dp,
                            iconSize = 18.dp,
                            backgroundColor = WearsicSurfaceActive,
                            testTag = "volume_decrease_button"
                        )

                        // Center Volume Percentage Indicator — gradient hero number
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$volumeLevel%",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                style = TextStyle(
                                    brush = Brush.verticalGradient(
                                        listOf(WearsicVibrantLavender, Color(0xFFE8D9FF))
                                    )
                                )
                            )
                            Text(
                                text = if (volumeLevel == 0) "Muted" else "Level",
                                color = WearsicTextMuted,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Increase Volume
                        WearsicCircularIconButton(
                            icon = Icons.Rounded.Add,
                            contentDescription = "Increase Volume",
                            onClick = {
                                if (volumeLevel < 100) {
                                    volumeLevel = (volumeLevel + 10).coerceAtMost(100)
                                    val streamVal = ((volumeLevel / 100f) * maxVol).toInt()
                                    audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, streamVal, 0)
                                }
                            },
                            size = 36.dp,
                            iconSize = 18.dp,
                            backgroundColor = WearsicSurfaceActive,
                            testTag = "volume_increase_button"
                        )
                    }
                }
            }

            // ── Sleep Timer ───────────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(6.dp))
            }
            item {
                SectionLabel("Sleep Timer")
            }
            item {
                val options = listOf(15, 30, 45, 60)
                val activeMinutes = if (sleepRemainingMs > 0) (sleepRemainingMs / 60000).toInt() else 0
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        options.forEach { mins ->
                            val isActive = sleepRemainingMs > 0 && activeMinutes in (mins - 14)..mins
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (isActive) WearsicVibrantLavender.copy(alpha = 0.25f)
                                        else WearsicSurface
                                    )
                                    .border(
                                        1.dp,
                                        if (isActive) WearsicVibrantLavender else WearsicVibrantLavender.copy(alpha = 0.4f),
                                        CircleShape
                                    )
                                    .clickable { onSleepTimerSet(mins) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${mins}m",
                                    color = if (isActive) WearsicVibrantLavender else WearsicTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (sleepRemainingMs == 0L) WearsicSurfaceActive
                                    else WearsicSurface
                                )
                                .border(
                                    1.dp,
                                    if (sleepRemainingMs == 0L) WearsicVibrantLavender else WearsicGlassBorder,
                                    CircleShape
                                )
                                .clickable { onSleepTimerSet(0) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "Off",
                                color = if (sleepRemainingMs == 0L) WearsicVibrantLavender else WearsicTextMuted,
                                fontSize = 11.sp,
                                fontWeight = if (sleepRemainingMs == 0L) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                    if (sleepRemainingMs > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "🌙 Sleep in ${sleepRemainingMs / 60000}m ${(sleepRemainingMs % 60000) / 1000}s",
                            color = WearsicVibrantLavender,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Bottom Spacing
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/** Small muted section heading above a control group. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = WearsicTextMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.2.sp,
        textAlign = TextAlign.Center
    )
}

/**
 * Reference-style output pill.
 *
 *  · selected  — light lavender fill (#ECE7F5), dark icon + label ("Headphones")
 *  · unselected — deep violet fill (#5E4998), white icon only
 */
@Composable
private fun OutputDevicePill(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val fill = if (isSelected) Color(0xFFECE7F5) else Color(0xFF5E4998)
    val contentTint = if (isSelected) Color(0xFF453678) else Color.White
    val pillShape = RoundedCornerShape(28.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(pillShape)
            .background(fill)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (isSelected) "$label, selected" else label
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentTint,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(9.dp))
                Text(
                    text = label,
                    color = contentTint,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentTint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
fun VolumeScreenPreview() {
    WearsicTheme {
        VolumeScreen()
    }
}
