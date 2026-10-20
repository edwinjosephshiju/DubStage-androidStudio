package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val videoUri: String,
    val videoDurationMs: Long,
    val videoWidth: Int = 1920,
    val videoHeight: Int = 1080,
    val sampleRate: Int = 44100,
    val channels: Int = 2,
    val originalPcmPath: String? = null,
    val vocalsWavPath: String? = null,
    val backingWavPath: String? = null,
    val modelChecksum: String? = null,
    val status: String = STATUS_CREATED, // CREATED, AUDIO_EXTRACTED, SEPARATING, SEPARATED, READY
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_CREATED = "CREATED"
        const val STATUS_AUDIO_EXTRACTING = "AUDIO_EXTRACTING"
        const val STATUS_AUDIO_EXTRACTED = "AUDIO_EXTRACTED"
        const val STATUS_SEPARATING = "SEPARATING"
        const val STATUS_SEPARATED = "SEPARATED"
        const val STATUS_READY = "READY"
    }
}
