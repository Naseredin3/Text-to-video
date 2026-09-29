package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoGenerationDao {
    @Query("SELECT * FROM video_generations ORDER BY createdAt DESC")
    fun getAllGenerations(): Flow<List<VideoGenerationEntity>>

    @Query("SELECT * FROM video_generations WHERE id = :id LIMIT 1")
    suspend fun getGenerationById(id: Long): VideoGenerationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGeneration(generation: VideoGenerationEntity): Long

    @Update
    suspend fun updateGeneration(generation: VideoGenerationEntity)

    @Query("DELETE FROM video_generations WHERE id = :id")
    suspend fun deleteGenerationById(id: Long)
}
