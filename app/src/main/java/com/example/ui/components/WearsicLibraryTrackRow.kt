package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.Track
import com.example.ui.theme.WearsicGlassBorder
import com.example.ui.theme.WearsicGlassFill
import com.example.ui.theme.WearsicLavenderContainer
import com.example.ui.theme.WearsicSurface
import com.example.ui.theme.WearsicSurfaceBorder
import com.example.ui.theme.WearsicSurfaceBorderSubtle
import com.example.ui.theme.WearsicTextMuted
import com.example.ui.theme.WearsicTextPrimary
import com.example.ui.theme.WearsicTextPrimaryDark
import com.example.ui.theme.WearsicTextSecondary
import com.example.ui.theme.WearsicVibrantLavender

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun WearsicLibraryTrackRow(
    track: Track,
    onPlay: () -> Unit,
    onLongPress: () -> Unit = {},
    onMore: () -> Unit = {},
    onDownload: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    removeDescription: String = "Remove",
    testTagPrefix: String = "library_track",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(WearsicGlassFill)
            .border(1.dp, WearsicGlassBorder, CircleShape)
            // combinedClickable wires the declared onLongPress (action sheet)
            // so callers relying on long-press actually get it.
            .combinedClickable(onClick = onPlay, onLongClick = onLongPress)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("${testTagPrefix}_${track.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (!track.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(track.artworkUrl)
                            .size(120)
                            .build(),
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(WearsicLavenderContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = WearsicVibrantLavender,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = track.title,
                        color = WearsicTextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = track.artist,
                        color = WearsicTextSecondary,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(WearsicGlassFill)
                        .border(1.dp, WearsicGlassBorder, CircleShape)
                        .clickable(onClick = onMore)
                        .testTag("${testTagPrefix}_more_${track.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Rounded.MoreHoriz,
                        contentDescription = "More actions",
                        tint = com.example.ui.theme.WearsicTextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))

                if (onRemove != null) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(WearsicSurface)
                            .border(1.dp, WearsicSurfaceBorder, CircleShape)
                            .clickable(onClick = onRemove)
                            .testTag("${testTagPrefix}_remove_${track.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = removeDescription,
                            tint = WearsicTextMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                if (onDownload != null) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(WearsicSurface)
                            .border(1.dp, WearsicSurfaceBorderSubtle, CircleShape)
                            .clickable(onClick = onDownload)
                            .testTag("${testTagPrefix}_download_${track.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Download",
                            tint = WearsicVibrantLavender,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(WearsicVibrantLavender, androidx.compose.ui.graphics.Color(0xFF8A5CF6))
                            )
                        )
                        .clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        tint = WearsicTextPrimaryDark,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}