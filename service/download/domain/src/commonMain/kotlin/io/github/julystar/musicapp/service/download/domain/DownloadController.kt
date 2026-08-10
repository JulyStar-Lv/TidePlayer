package io.github.julystar.musicapp.service.download.domain

import kotlinx.coroutines.flow.Flow

interface DownloadController {
    val tasks: Flow<List<DownloadTask>>

    suspend fun enqueue(task: DownloadTask)
    suspend fun pause(id: DownloadTaskId)
    suspend fun resume(id: DownloadTaskId)
    suspend fun cancel(id: DownloadTaskId)
    suspend fun cancelAll()
    suspend fun recoverInterruptedTasks(): Int = 0
    suspend fun retry(id: DownloadTaskId)
}

interface DownloadTaskRepository {
    fun observeTasks(): Flow<List<DownloadTask>>
    fun observeActiveTasks(): Flow<List<DownloadTask>>
    fun observeTask(id: DownloadTaskId): Flow<DownloadTask?>
    suspend fun getTask(id: DownloadTaskId): DownloadTask?
    suspend fun upsertTask(task: DownloadTask)
    suspend fun updateTask(task: DownloadTask)
    suspend fun deleteTask(id: DownloadTaskId)
}
