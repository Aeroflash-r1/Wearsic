package com.example.network

import com.example.network.model.PlaylistDto
import com.example.network.model.PlaylistWithTracksDto
import com.example.network.model.SearchResponseDto
import com.example.network.model.ServerHealthDto
import com.example.network.model.TrackDto

interface WearsicApiClient {
    suspend fun checkHealth(baseUrl: String): Result<ServerHealthDto>
    suspend fun searchTracks(baseUrl: String, query: String): Result<SearchResponseDto>
    suspend fun getFavorites(baseUrl: String): Result<List<TrackDto>>
    suspend fun addFavorite(baseUrl: String, track: TrackDto): Result<Unit>
    suspend fun removeFavorite(baseUrl: String, videoId: String): Result<Unit>
    suspend fun getPlaylists(baseUrl: String): Result<List<PlaylistDto>>
    suspend fun getPlaylistTracks(baseUrl: String, playlistId: String): Result<PlaylistWithTracksDto>
    suspend fun removeTrackFromPlaylist(baseUrl: String, playlistId: String, videoId: String): Result<Unit>
}