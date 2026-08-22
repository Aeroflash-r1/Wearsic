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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.Track
import com.example.ui.components.WearsicScreenHeader
import com.example.ui.theme.WearsicBlack
import com.example.ui.theme.WearsicLavenderContainer
import com.example.ui.theme.WearsicSurface
import com.example.ui.theme.WearsicSurfaceActive
import com.example.ui.theme.WearsicSurfaceBorderSubtle
import com.example.ui.theme.WearsicTextMuted
import com.example.ui.theme.WearsicTextPrimary
import com.example.ui.theme.WearsicTextPrimaryDark
import com.example.ui.theme.WearsicTextSecondary
import com.example.ui.theme.WearsicTheme
import com.example.ui.theme.WearsicVibrantLavender
import com.example.ui.viewmodel.SearchUiState

import com.example.ui.util.wearsicRotaryScroll

@Composable
fun SearchScreen(
    searchState: SearchUiState,
    onQuerySelected: (String) -> Unit,
    onTrackSelected: (Track) -> Unit,
    onDownloadTrack: (Track) -> Unit = {},
    onAddToQueue: (Track) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberScalingLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val quickQueries = listOf("Crowded House", "Rock", "Pop", "Acoustic")
    var typedQuery by remember(searchState.query) { mutableStateOf(searchState.query) }

    // Wear keyboards are inconsistent: the enter key may fire onSearch, onDone
    // or onGo depending on the active IME. Handle all of them and dismiss the
    // keyboard so the results become visible immediately.
    fun submitSearch() {
        val query = typedQuery.trim()
        if (query.isNotBlank()) {
            keyboardController?.hide()
            onQuerySelected(query)
        }
    }

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
            // Header
            item {
                WearsicScreenHeader(
                    title = "Search",
                    subtitle = "Stream Catalog"
                )
            }

            // Interactive Search Bar
            item {
                var isFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(CircleShape)
                        .background(if (isFocused) WearsicSurfaceActive else WearsicSurface)
                        .border(
                            1.dp,
                            if (isFocused) WearsicVibrantLavender else WearsicSurfaceBorderSubtle,
                            CircleShape
                        )
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search Icon",
                            tint = if (isFocused) WearsicVibrantLavender else WearsicTextMuted,
                            modifier = Modifier.size(16.dp)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (typedQuery.isEmpty()) {
                                Text(
                                    text = "Type artist/song...",
                                    color = WearsicTextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }

                            BasicTextField(
                                value = typedQuery,
                                onValueChange = { typedQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = WearsicTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                cursorBrush = SolidColor(WearsicVibrantLavender),
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Search
                                ),
                                keyboardActions = KeyboardActions(
                                    onSearch = { submitSearch() },
                                    onDone = { submitSearch() },
                                    onGo = { submitSearch() }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { focusState ->
                                        isFocused = focusState.isFocused
                                    }
                                    .testTag("search_text_input")
                            )
                        }

                        if (typedQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear Search",
                                tint = WearsicTextSecondary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        typedQuery = ""
                                        onQuerySelected("")
                                    }
                                    .testTag("search_clear_button")
                            )
                        }
                    }
                }
            }

            // Quick Category Chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    quickQueries.take(2).forEach { query ->
                        QuickChip(
                            label = query,
                            isSelected = searchState.query == query,
                            onClick = { onQuerySelected(query) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    quickQueries.drop(2).take(2).forEach { query ->
                        QuickChip(
                            label = query,
                            isSelected = searchState.query == query,
                            onClick = { onQuerySelected(query) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Loading State
            if (searchState.isSearching) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HourglassEmpty,
                            contentDescription = "Searching",
                            tint = WearsicVibrantLavender,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Searching server...",
                            color = WearsicTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Empty State
            if (searchState.hasSearched && searchState.results.isEmpty() && !searchState.isSearching) {
                item {
                    Text(
                        text = if (searchState.errorMessage != null) {
                            "No results found"
                        } else {
                            "No tracks found for \"${searchState.query}\""
                        },
                        color = WearsicTextMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            // Result Items
            items(searchState.results, key = { it.id }) { track ->
                SearchTrackItem(
                    track = track,
                    onClick = { onTrackSelected(track) },
                    onDownload = { onDownloadTrack(track) },
                    onAddToQueue = { onAddToQueue(track) }
                )
            }

            // Bottom Spacing
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun QuickChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (isSelected) WearsicVibrantLavender else WearsicSurface)
            .border(
                1.dp,
                if (isSelected) WearsicVibrantLavender else WearsicSurfaceBorderSubtle,
                CircleShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("search_chip_$label"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) WearsicTextPrimaryDark else WearsicTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchTrackItem(
    track: Track,
    onClick: () -> Unit,
    onDownload: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(WearsicSurface)
            .border(1.dp, WearsicSurfaceBorderSubtle, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("search_track_${track.id}")
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
                // Artwork thumbnail or placeholder
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
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        color = WearsicTextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Add to Queue button
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(WearsicSurface)
                        .border(1.dp, WearsicSurfaceBorderSubtle, CircleShape)
                        .clickable(onClick = onAddToQueue)
                        .testTag("search_add_to_queue_${track.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QueueMusic,
                        contentDescription = "Add to Queue",
                        tint = WearsicTextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Download button
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(WearsicSurface)
                        .border(1.dp, WearsicSurfaceBorderSubtle, CircleShape)
                        .clickable(onClick = onDownload)
                        .testTag("search_download_${track.id}"),
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

                // Play button
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(WearsicVibrantLavender)
                        .clickable(onClick = onClick),
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

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
fun SearchScreenPreview() {
    WearsicTheme {
        SearchScreen(
            searchState = SearchUiState(
                query = "Crowded House",
                results = listOf(
                    Track(id = "1", title = "Weather with You", artist = "Crowded House"),
                    Track(id = "2", title = "Don't Dream It's Over", artist = "Crowded House")
                )
            ),
            onQuerySelected = {},
            onTrackSelected = {}
        )
    }
}
