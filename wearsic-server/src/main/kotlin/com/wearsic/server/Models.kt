package com.wearsic.server

import kotlinx.serialization.Serializable

/**
 * Matches API_CONTRACT.md exactly. The client parses these defensively
 * (optString/optLong), so new fields can be added freely later, but
 * `videoId` must never be renamed or removed within this client generation.
 */
@Serializable
data class TrackDto(
    val videoId: String,
    val title: String,
    val uploader: String,
    val durationMs: Long = 0L,
    val thumbnailUrl: String? = null,
)

@Serializable
data class SearchResponse(val results: List<TrackDto>)

@Serializable
data class SuggestionsResponse(val suggestions: List<String>)

@Serializable
data class RelatedResponse(val results: List<TrackDto>)

@Serializable
data class AlbumDto(
    val id: String, // full playlist URL, not a bare id — per contract
    val name: String,
    val uploader: String,
    val trackCount: Int,
    val thumbnailUrl: String? = null,
)

@Serializable
data class PlaylistTracksResponse(
    val id: String,
    val name: String,
    val tracks: List<TrackDto>,
)

@Serializable
data class PlaylistSummaryDto(
    val id: String,
    val name: String,
    val trackCount: Int,
    val thumbnailUrl: String? = null,
)

@Serializable
data class CreatePlaylistRequest(val name: String)

@Serializable
data class HealthResponse(
    val status: String = "ok",
    val version: String = "1.2.0",
    val serverName: String = "Wearsic Engine",
)

@Serializable
data class ErrorResponse(val error: String)

@Serializable
data class YoutubeCookieRequest(val cookie: String)

@Serializable
data class YoutubeCookieStatus(val hasCookie: Boolean)
