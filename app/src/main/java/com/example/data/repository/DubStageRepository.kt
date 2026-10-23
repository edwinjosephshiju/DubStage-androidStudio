package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.dao.DubStageDao
import com.example.data.model.ClipEntity
import com.example.data.model.ProjectEntity
import com.example.data.model.TakeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class DubStageRepository(
    private val context: Context,
    private val dao: DubStageDao
) {
    val allProjects: Flow<List<ProjectEntity>> = dao.getAllProjects()

    fun getProject(id: String): Flow<ProjectEntity?> = dao.getProject(id)

    suspend fun getProjectDirect(id: String): ProjectEntity? = dao.getProjectDirect(id)

    fun getClipsForProject(projectId: String): Flow<List<ClipEntity>> = dao.getClipsForProject(projectId)

    suspend fun getClipsForProjectDirect(projectId: String): List<ClipEntity> = dao.getClipsForProjectDirect(projectId)

    fun getTakesForClip(clipId: String): Flow<List<TakeEntity>> = dao.getTakesForClip(clipId)

    suspend fun getTakesForClipDirect(clipId: String): List<TakeEntity> = dao.getTakesForClipDirect(clipId)

    fun getAllTakesForProject(projectId: String): Flow<List<TakeEntity>> = dao.getAllTakesForProject(projectId)

    suspend fun getAllTakesForProjectDirect(projectId: String): List<TakeEntity> = dao.getAllTakesForProjectDirect(projectId)

    suspend fun createProject(
        title: String,
        videoUri: String,
        videoDurationMs: Long,
        width: Int,
        height: Int
    ): ProjectEntity {
        val id = UUID.randomUUID().toString()
        val projectDir = getProjectDir(id)
        if (!projectDir.exists()) {
            projectDir.mkdirs()
        }
        val project = ProjectEntity(
            id = id,
            title = title,
            videoUri = videoUri,
            videoDurationMs = videoDurationMs,
            videoWidth = width,
            videoHeight = height,
            originalPcmPath = File(projectDir, "original.pcm").absolutePath,
            vocalsWavPath = File(projectDir, "vocals.wav").absolutePath,
            backingWavPath = File(projectDir, "backing.wav").absolutePath,
            status = ProjectEntity.STATUS_CREATED
        )
        dao.insertProject(project)
        return project
    }

    suspend fun updateProject(project: ProjectEntity) {
        dao.updateProject(project.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun persistProjectVideo(project: ProjectEntity): ProjectEntity = withContext(Dispatchers.IO) {
        val projectDir = getProjectDir(project.id)
        val localVideoFile = File(projectDir, "video.mp4")

        val currentUri = Uri.parse(project.videoUri)
        // If it's already pointing to localVideoFile and file exists with content, no copy needed
        if (currentUri.scheme == "file" && currentUri.path == localVideoFile.absolutePath && localVideoFile.exists() && localVideoFile.length() > 0) {
            return@withContext project
        }

        try {
            val inputStream = context.contentResolver.openInputStream(currentUri)
                ?: if (currentUri.scheme == "file") File(currentUri.path ?: "").inputStream() else null

            if (inputStream != null) {
                inputStream.use { input ->
                    localVideoFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                val updated = project.copy(
                    videoUri = Uri.fromFile(localVideoFile).toString(),
                    updatedAt = System.currentTimeMillis()
                )
                dao.updateProject(updated)
                return@withContext updated
            } else {
                if (localVideoFile.exists() && localVideoFile.length() > 0) {
                    val updated = project.copy(
                        videoUri = Uri.fromFile(localVideoFile).toString(),
                        updatedAt = System.currentTimeMillis()
                    )
                    dao.updateProject(updated)
                    return@withContext updated
                }
                return@withContext project
            }
        } catch (e: Exception) {
            Log.w("DubStageRepo", "Non-fatal: could not copy video stream into internal storage: ${e.message}")
            if (localVideoFile.exists() && localVideoFile.length() > 0) {
                val updated = project.copy(
                    videoUri = Uri.fromFile(localVideoFile).toString(),
                    updatedAt = System.currentTimeMillis()
                )
                dao.updateProject(updated)
                return@withContext updated
            }
            return@withContext project
        }
    }

    suspend fun deleteProject(project: ProjectEntity) {
        dao.deleteProject(project)
        // Clean up project disk directory
        val projectDir = getProjectDir(project.id)
        if (projectDir.exists()) {
            projectDir.deleteRecursively()
        }
    }

    suspend fun insertClips(clips: List<ClipEntity>) {
        dao.insertClips(clips)
    }

    suspend fun replaceClipsForProject(projectId: String, clips: List<ClipEntity>) {
        dao.clearAndReplaceClipsForProject(projectId, clips)
    }

    suspend fun updateClip(clip: ClipEntity) {
        dao.updateClip(clip)
    }

    suspend fun deleteClip(clip: ClipEntity) {
        dao.deleteClip(clip)
    }

    suspend fun insertTake(take: TakeEntity) {
        dao.insertTake(take)
    }

    suspend fun updateTake(take: TakeEntity) {
        dao.updateTake(take)
    }

    suspend fun deleteTake(take: TakeEntity) {
        dao.deleteTake(take)
        val file = File(take.pcmFilePath)
        if (file.exists()) {
            file.delete()
        }
    }

    fun getProjectDir(projectId: String): File {
        val dir = File(context.filesDir, "projects/$projectId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getTakesDir(projectId: String): File {
        val dir = File(getProjectDir(projectId), "takes")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getExportsDir(projectId: String): File {
        val dir = File(getProjectDir(projectId), "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getModelsDir(): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
