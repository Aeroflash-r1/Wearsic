package com.example.data

import android.content.Context
import com.example.data.db.WearsicDatabase
import com.example.data.db.WearsicRecentTrackEntity
import com.example.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WearsicRecentRepository(context: Context) {

    private val recentTrackDao = WearsicDatabase.getInstance(context).recentTrackDao()

    val recentTracksFlow: Flow<List<Track>> = recentTrackDao.getRecentTracksFlow()
        .map { entities -> entities.map { it.toDomainTrack() } }

    suspend fun recordPlayed(track: Track) {
        if (track.id.isBlank()) return
        recentTrackDao.upsert(
            WearsicRecentTrackEntity(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                artworkUrl = track.artworkUrl,
                durationMs = track.durationMs,
                mediaUri = track.mediaUri,
                playedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearRecent() {
        recentTrackDao.deleteAll()
    }
}