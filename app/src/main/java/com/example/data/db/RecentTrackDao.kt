package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentTrackDao {

    @Query("SELECT * FROM recent_tracks ORDER BY playedAt DESC LIMIT 10")
    fun getRecentTracksFlow(): Flow<List<WearsicRecentTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WearsicRecentTrackEntity)

    @Query("DELETE FROM recent_tracks WHERE trackId = :trackId")
    suspend fun deleteByTrackId(trackId: String)

    @Query("DELETE FROM recent_tracks")
    suspend fun deleteAll()
}