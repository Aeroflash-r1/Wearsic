package com.example.network

import com.example.network.model.SearchResponseDto
import com.example.network.model.ServerHealthDto

interface WearsicApiClient {
    suspend fun checkHealth(baseUrl: String): Result<ServerHealthDto>
    suspend fun searchTracks(baseUrl: String, query: String): Result<SearchResponseDto>
}
