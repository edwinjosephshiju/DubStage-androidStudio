package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "takes",
    foreignKeys = [
        ForeignKey(
            entity = ClipEntity::class,
            parentColumns = ["id"],
            childColumns = ["clipId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["clipId"])]
)
data class TakeEntity(
    @PrimaryKey val id: String,
    val clipId: String,
    val takeNumber: Int,
    val pcmFilePath: String,
    val durationMs: Long,
    val gainDb: Float = 0.0f,
    val timeOffsetMs: Long = 0L,
    val noiseGateThresholdDb: Float = -60.0f,
    val recordedAt: Long = System.currentTimeMillis()
)
