package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "clips",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"])]
)
data class ClipEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val clipIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val speakerName: String = "Speaker 1",
    val scriptText: String = "",
    val activeTakeId: String? = null,
    val isMuted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
