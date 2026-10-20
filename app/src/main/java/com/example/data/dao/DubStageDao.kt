package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ClipEntity
import com.example.data.model.ProjectEntity
import com.example.data.model.TakeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DubStageDao {

    // Projects
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    fun getProject(id: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectDirect(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    // Clips
    @Query("SELECT * FROM clips WHERE projectId = :projectId ORDER BY clipIndex ASC")
    fun getClipsForProject(projectId: String): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE projectId = :projectId ORDER BY clipIndex ASC")
    suspend fun getClipsForProjectDirect(projectId: String): List<ClipEntity>

    @Query("SELECT * FROM clips WHERE id = :id LIMIT 1")
    suspend fun getClipDirect(id: String): ClipEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClip(clip: ClipEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClips(clips: List<ClipEntity>)

    @Update
    suspend fun updateClip(clip: ClipEntity)

    @Delete
    suspend fun deleteClip(clip: ClipEntity)

    @Query("DELETE FROM clips WHERE projectId = :projectId")
    suspend fun deleteClipsForProject(projectId: String)

    @Query("DELETE FROM takes WHERE clipId IN (SELECT id FROM clips WHERE projectId = :projectId)")
    suspend fun deleteTakesForProject(projectId: String)

    @androidx.room.Transaction
    suspend fun clearAndReplaceClipsForProject(projectId: String, clips: List<ClipEntity>) {
        deleteTakesForProject(projectId)
        deleteClipsForProject(projectId)
        if (clips.isNotEmpty()) {
            insertClips(clips)
        }
    }
    // Takes
    @Query("SELECT * FROM takes WHERE clipId = :clipId ORDER BY takeNumber ASC")
    fun getTakesForClip(clipId: String): Flow<List<TakeEntity>>

    @Query("SELECT * FROM takes WHERE clipId = :clipId ORDER BY takeNumber ASC")
    suspend fun getTakesForClipDirect(clipId: String): List<TakeEntity>

    @Query("SELECT * FROM takes WHERE id = :id LIMIT 1")
    suspend fun getTakeDirect(id: String): TakeEntity?

    @Query("SELECT takes.* FROM takes INNER JOIN clips ON takes.clipId = clips.id WHERE clips.projectId = :projectId")
    fun getAllTakesForProject(projectId: String): Flow<List<TakeEntity>>

    @Query("SELECT takes.* FROM takes INNER JOIN clips ON takes.clipId = clips.id WHERE clips.projectId = :projectId")
    suspend fun getAllTakesForProjectDirect(projectId: String): List<TakeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTake(take: TakeEntity)

    @Update
    suspend fun updateTake(take: TakeEntity)

    @Delete
    suspend fun deleteTake(take: TakeEntity)

    @Query("DELETE FROM takes WHERE clipId = :clipId")
    suspend fun deleteTakesForClip(clipId: String)
}
