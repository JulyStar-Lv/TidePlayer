package io.github.julystar.musicapp.service.download.data

import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.MediaType
import io.github.julystar.musicapp.core.domain.model.SourceId
import io.github.julystar.musicapp.database.DownloadTaskDao
import io.github.julystar.musicapp.database.DownloadTaskEntity
import io.github.julystar.musicapp.service.download.domain.DownloadStatus
import io.github.julystar.musicapp.service.download.domain.DownloadTask
import io.github.julystar.musicapp.service.download.domain.DownloadTaskId
import io.github.julystar.musicapp.service.download.domain.DownloadTaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomDownloadTaskRepository(
    private val dao: DownloadTaskDao,
) : DownloadTaskRepository {
    override fun observeTasks(): Flow<List<DownloadTask>> {
        return dao.observeAll().map { tasks -> tasks.map { it.toDomain() } }
    }

    override fun observeActiveTasks(): Flow<List<DownloadTask>> {
        return dao.observeActive().map { tasks -> tasks.map { it.toDomain() } }
    }

    override fun observeTask(id: DownloadTaskId): Flow<DownloadTask?> {
        return dao.observe(id.value).map { it?.toDomain() }
    }

    override suspend fun getTask(id: DownloadTaskId): DownloadTask? {
        return dao.get(id.value)?.toDomain()
    }

    override suspend fun upsertTask(task: DownloadTask) {
        dao.upsert(task.toEntity())
    }

    override suspend fun updateTask(task: DownloadTask) {
        dao.upsert(task.toEntity())
    }

    override suspend fun deleteTask(id: DownloadTaskId) {
        dao.delete(id.value)
    }
}

internal fun DownloadTask.toEntity(): DownloadTaskEntity {
    return DownloadTaskEntity(
        id = id.value,
        sourceId = mediaId.sourceId.value,
        mediaType = mediaId.mediaType.name,
        remoteId = mediaId.remoteId,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        status = status.name,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        localPath = localPath,
        mimeType = mimeType,
        errorMessage = errorMessage,
        finalizationWarning = finalizationWarning,
        createdAt = createdAtEpochMs,
        updatedAt = updatedAtEpochMs,
    )
}

internal fun DownloadTaskEntity.toDomain(): DownloadTask {
    return DownloadTask(
        id = DownloadTaskId(id),
        mediaId = MediaId(
            sourceId = SourceId(sourceId),
            mediaType = MediaType.valueOf(mediaType),
            remoteId = remoteId,
        ),
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        status = DownloadStatus.valueOf(status),
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        localPath = localPath,
        mimeType = mimeType,
        errorMessage = errorMessage,
        finalizationWarning = finalizationWarning,
        createdAtEpochMs = createdAt,
        updatedAtEpochMs = updatedAt,
    )
}
