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
        // The same song can reach the watch under different ids — surrogate
        // `it:12345` from search vs the real YouTube id from radio/playlists —
        // so the trackId PK alone cannot dedupe. Drop other rows that are the
        // same song by title+artist, keeping the freshest (just-upserted) row.
        recentTrackDao.deleteDuplicatesOf(track.id, track.title, track.artist)
    }

    suspend fun clearRecent() {
        recentTrackDao.deleteAll()
    }
}