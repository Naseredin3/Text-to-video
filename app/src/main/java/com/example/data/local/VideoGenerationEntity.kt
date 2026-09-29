package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "video_generations")
data class VideoGenerationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val prompt: String,
    val model: String = "veo-3.1-generate-preview",
    val aspectRatio: String = "16:9",
    val resolution: String = "1080p",
    val downloadPath: String = "dialogue_example.mp4",
    val localFilePath: String? = null,
    val remoteVideoUri: String? = null,
    val operationName: String? = null,
    val status: String = STATUS_POLLING,
    val pollCount: Int = 0,
    val elapsedSeconds: Int = 0,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_QUEUED = "QUEUED"
        const val STATUS_POLLING = "POLLING"
        const val STATUS_DOWNLOADING = "DOWNLOADING"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_CANCELLED = "CANCELLED"
    }
}
