package com.wearsic.app.data.repository

import com.wearsic.app.data.api.WearsicApi
import com.wearsic.app.data.model.StreamInfo
import com.wearsic.app.data.model.Track
import com.wearsic.app.data.model.toDomain

class WearsicRepository(private val api: WearsicApi) {

    suspend fun search(query: String): Result<List<Track>> {
        return try {
            val response = api.search(query)
            Result.success(response.results.map { it.toDomain() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStream(id: String): Result<StreamInfo> {
        return try {
            val response = api.getStream(id)
            Result.success(response.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
