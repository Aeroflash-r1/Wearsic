package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloadsFlow(): Flow<List<WearsicDownloadEntity>>

    @Query("SELECT * FROM downloads WHERE downloadState = 'COMPLETED' ORDER BY createdAt DESC")
    fun getCompletedDownloadsFlow(): Flow<List<WearsicDownloadEntity>>

    @Query("SELECT * FROM downloads WHERE trackId = :trackId LIMIT 1")
    fun getDownloadFlowById(trackId: String): Flow<WearsicDownloadEntity?>

    @Query("SELECT * FROM downloads WHERE trackId = :trackId LIMIT 1")
    suspend fun getDownloadById(trackId: String): WearsicDownloadEntity?

    @Query("SELECT * FROM downloads")
    suspend fun getAllDownloads(): List<WearsicDownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: WearsicDownloadEntity)

    @Query("UPDATE downloads SET progress = :progress, downloadState = :state, fileSizeBytes = :fileSizeBytes WHERE trackId = :trackId")
    suspend fun updateProgress(trackId: String, progress: Int, state: String, fileSizeBytes: Long)

    @Query("UPDATE downloads SET downloadState = :state, errorMessage = :errorMessage WHERE trackId = :trackId")
    suspend fun updateState(trackId: String, state: String, errorMessage: String? = null)

    @Query("DELETE FROM downloads WHERE trackId = :trackId")
    suspend fun deleteById(trackId: String)

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()
}
