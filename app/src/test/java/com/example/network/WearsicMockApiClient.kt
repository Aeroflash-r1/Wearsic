package com.example.network

import android.content.ContentResolver
import android.content.Context
import com.example.R
import com.example.network.model.SearchResponseDto
import com.example.network.model.ServerHealthDto
import com.example.network.model.TrackDto
import kotlinx.coroutines.delay

class WearsicMockApiClient(private val context: Context) : WearsicApiClient {

    override suspend fun checkHealth(baseUrl: String): Result<ServerHealthDto> {
        delay(400) // Simulate fast network roundtrip
        return if (baseUrl.contains("invalid") || baseUrl.contains("fail")) {
            Result.failure(Exception("Could not connect to $baseUrl (Connection refused)"))
        } else {
            Result.success(
                ServerHealthDto(
                    status = "ok",
                    version = "1.0.0-dev",
                    serverName = "Wearsic Ktor Server (Dev)"
                )
            )
        }
    }

    override suspend fun searchTracks(baseUrl: String, query: String): Result<SearchResponseDto> {
        delay(500) // Realistic search response delay
        val packageName = context.packageName
        val track1Uri = "${ContentResolver.SCHEME_ANDROID_RESOURCE}://$packageName/${R.raw.test_track_1}"
        val track2Uri = "${ContentResolver.SCHEME_ANDROID_RESOURCE}://$packageName/${R.raw.test_track_2}"

        val allMockTracks = listOf(
            TrackDto(
                id = "mock_track_1",
                title = "Weather with You",
                artist = "Crowded House",
                album = "Woodface",
                artworkUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=150&auto=format&fit=crop&q=60",
                durationMs = 6000L,
                streamUrl = track1Uri
            ),
            TrackDto(
                id = "mock_track_2",
                title = "Don't Dream It's Over",
                artist = "Crowded House",
                album = "Crowded House",
                artworkUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=150&auto=format&fit=crop&q=60",
                durationMs = 6000L,
                streamUrl = track2Uri
            ),
            TrackDto(
                id = "mock_track_3",
                title = "Four Seasons in One Day",
                artist = "Crowded House",
                album = "Woodface",
                artworkUrl = "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=150&auto=format&fit=crop&q=60",
                durationMs = 6000L,
                streamUrl = track1Uri
            ),
            TrackDto(
                id = "mock_track_4",
                title = "Fall at Your Feet",
                artist = "Crowded House",
                album = "Woodface",
                artworkUrl = "https://images.unsplash.com/photo-1487180144351-b8472da7d491?w=150&auto=format&fit=crop&q=60",
                durationMs = 6000L,
                streamUrl = track2Uri
            ),
            TrackDto(
                id = "mock_track_5",
                title = "Distant Sun",
                artist = "Crowded House",
                album = "Together Alone",
                artworkUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=150&auto=format&fit=crop&q=60",
                durationMs = 6000L,
                streamUrl = track1Uri
            )
        )

        val filtered = if (query.isBlank()) {
            allMockTracks
        } else {
            allMockTracks.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                (it.album?.contains(query, ignoreCase = true) == true)
            }.ifEmpty {
                // If query is custom like "Rock" or "Pop", return general list
                allMockTracks.take(3)
            }
        }

        return Result.success(SearchResponseDto(query = query, tracks = filtered))
    }
}
