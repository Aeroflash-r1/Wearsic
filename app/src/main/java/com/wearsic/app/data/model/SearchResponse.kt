package com.wearsic.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrackDto(
    val id: String,
    val title: String,
    val duration: Long,
    val channel: String,
)

@Serializable
data class SearchResponse(
    val results: List<TrackDto>,
)

@Serializable
data class StreamInfoDto(
    val id: String,
    val title: String,
    val duration: Long,
    @SerialName("stream_url")
    val streamUrl: String,
    val thumbnail: String,
)

fun TrackDto.toDomain() = Track(id, title, duration, channel)
fun StreamInfoDto.toDomain() = StreamInfo(id, title, duration, streamUrl, thumbnail)
