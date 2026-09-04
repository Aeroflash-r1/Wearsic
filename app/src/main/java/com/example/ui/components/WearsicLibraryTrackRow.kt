package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.model.Track
import com.example.ui.theme.WearsicTextMuted
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
    WearsicSongRow(
        title = track.title,
        artist = track.artist,
        artworkUrl = track.artworkUrl,
        onClick = onPlay,
        modifier = modifier
            // combinedClickable wires the declared onLongPress (action sheet)
            // so callers relying on long-press actually get it.
            .combinedClickable(onClick = onPlay, onLongClick = onLongPress),
        testTag = "${testTagPrefix}_${track.id}",
        trailing = {
            Row {
                WearsicSongRowActionButton(
                    icon = Icons.Rounded.MoreHoriz,
                    contentDescription = "More actions",
                    onClick = onMore,
                    testTag = "${testTagPrefix}_more_${track.id}",
                    tint = WearsicTextMuted
                )
                if (onRemove != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    WearsicSongRowActionButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = removeDescription,
                        onClick = onRemove,
                        testTag = "${testTagPrefix}_remove_${track.id}",
                        tint = WearsicTextMuted
                    )
                }
                if (onDownload != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    WearsicSongRowActionButton(
                        icon = Icons.Rounded.Download,
                        contentDescription = "Download",
                        onClick = onDownload,
                        testTag = "${testTagPrefix}_download_${track.id}",
                        tint = WearsicVibrantLavender
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                WearsicSongRowPlayButton(onClick = onPlay)
            }
        }
    )
}