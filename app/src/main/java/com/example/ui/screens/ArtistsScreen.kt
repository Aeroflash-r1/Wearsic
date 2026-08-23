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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import androidx.compose.ui.tooling.preview.Preview
import com.example.ui.viewmodel.ArtistGroup
import com.example.ui.components.WearsicScreenHeader
import com.example.ui.theme.WearsicBlack
import com.example.ui.theme.WearsicGlassBorder
import com.example.ui.theme.WearsicGlassFill
import com.example.ui.theme.WearsicTextMuted
import com.example.ui.theme.WearsicTextPrimary
import com.example.ui.theme.WearsicTextSecondary
import com.example.ui.theme.WearsicTheme
import com.example.ui.viewmodel.ArtistsUiState

import com.example.ui.util.wearsicRotaryScroll

@Composable
fun ArtistsScreen(
    artistsState: ArtistsUiState,
    onRefresh: () -> Unit,
    onPlayArtistSongs: (ArtistGroup, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberScalingLazyListState()
    var selected by remember { mutableStateOf<com.example.ui.viewmodel.ArtistGroup?>(null) }

    LaunchedEffect(Unit) { onRefresh() }

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
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                if (selected == null) {
                    WearsicScreenHeader(title = "Artists", subtitle = "From your saved songs")
                } else {
                    WearsicScreenHeader(title = selected!!.name, subtitle = "${selected!!.songs.size} songs • tap Clear to go back")
                    // Clear button — exits the artist back to the full list
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(WearsicGlassFill)
                            .border(1.dp, WearsicGlassBorder, CircleShape)
                            .clickable { selected = null }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear artist",
                            tint = WearsicTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Clear", color = WearsicTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            if (artistsState.isLoading && artistsState.artists.isEmpty()) {
                item {
                    Text("Loading…", color = WearsicTextSecondary, fontSize = 11.sp)
                }
            }

            val group = selected
            if (group != null) {
                items(group.songs.size) { index ->
                    val song = group.songs[index]
                    com.example.ui.components.WearsicLibraryTrackRow(
                        track = song,
                        onPlay = { onPlayArtistSongs(group, index) }
                    )
                }
            } else if (artistsState.artists.isEmpty() && !artistsState.isLoading) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = WearsicVibrantLavenderFallback,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "No artists yet",
                            color = WearsicTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Save favorites or download songs to see artists here.",
                            color = WearsicTextMuted,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }
                }
            } else {
                items(artistsState.artists, key = { it.name }) { artist ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(WearsicGlassFill)
                            .border(1.dp, WearsicGlassBorder, CircleShape)
                            .clickable { selected = artist }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                            .testTag("artist_${artist.name}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(WearsicVibrantLavenderFallback.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = null,
                                    tint = WearsicVibrantLavenderFallback,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.size(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = artist.name,
                                    color = WearsicTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${artist.songs.size} saved songs",
                                    color = WearsicTextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

private val WearsicVibrantLavenderFallback = androidx.compose.ui.graphics.Color(0xFFD0BCFF)

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
fun ArtistsScreenPreview() {
    WearsicTheme {
        ArtistsScreen(artistsState = ArtistsUiState(), onRefresh = {}, onPlayArtistSongs = { _, _ -> })
    }
}