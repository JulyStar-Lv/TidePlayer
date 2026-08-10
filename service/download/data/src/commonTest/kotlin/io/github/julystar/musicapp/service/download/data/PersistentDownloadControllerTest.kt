package io.github.julystar.musicapp.service.download.data

import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.MediaType
import io.github.julystar.musicapp.core.domain.model.SourceId
import io.github.julystar.musicapp.service.download.domain.DownloadStatus
import io.github.julystar.musicapp.service.download.domain.DownloadTask
import io.github.julystar.musicapp.service.download.domain.DownloadTaskId
import io.github.julystar.musicapp.service.download.domain.DownloadTaskRepository
import io.github.julystar.musicapp.service.download.domain.DownloadTaskScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersistentDownloadControllerTest {
    @Test
    fun enqueueStoresQueuedTaskAndSchedulesIt() = runBlocking {
        val repository = InMemoryDownloadTaskRepository()
        val scheduler = RecordingDownloadTaskScheduler()
        val controller = PersistentDownloadController(
            repository = repository,
            scheduler = scheduler,
            nowEpochMs = { 99 },
        )
        val task = task(
            status = DownloadStatus.Downloading,
            errorMessage = "previous failure",
        )

        controller.enqueue(task)

        val stored = repository.requireTask(task.id)
        assertEquals(DownloadStatus.Queued, stored.status)
        assertEquals(null, stored.errorMessage)
        assertEquals(99, stored.updatedAtEpochMs)
        assertEquals(listOf(task.id), scheduler.scheduled.map { it.id })
    }

    @Test
    fun pauseUpdatesDownloadingTaskAndPausesScheduler() = runBlocking {
        val repository = InMemoryDownloadTaskRepository(
            task(status = DownloadStatus.Downloading),
        )
        val scheduler = RecordingDownloadTaskScheduler()
        val controller = PersistentDownloadController(
            repository = repository,
            scheduler = scheduler,
            nowEpochMs = { 100 },
        )

        controller.pause(DownloadTaskId("task-1"))

        val stored = repository.requireTask(DownloadTaskId("task-1"))
        assertEquals(DownloadStatus.Paused, stored.status)
        assertEquals(100, stored.updatedAtEpochMs)
        assertEquals(listOf(DownloadTaskId("task-1")), scheduler.paused)
    }

    @Test
    fun resumeFailedTaskClearsErrorAndSchedulesIt() = runBlocking {
        val repository = InMemoryDownloadTaskRepository(
            task(status = DownloadStatus.Failed, errorMessage = "network"),
        )
        val scheduler = RecordingDownloadTaskScheduler()
        val controller = PersistentDownloadController(
            repository = repository,
            scheduler = scheduler,
            nowEpochMs = { 101 },
        )

        controller.resume(DownloadTaskId("task-1"))

        val stored = repository.requireTask(DownloadTaskId("task-1"))
        assertEquals(DownloadStatus.Queued, stored.status)
        assertEquals(null, stored.errorMessage)
        assertEquals(101, stored.updatedAtEpochMs)
        assertEquals(listOf(DownloadTaskId("task-1")), scheduler.scheduled.map { it.id })
    }

    @Test
    fun cancelCompletedTaskIsIgnored() = runBlocking {
        val repository = InMemoryDownloadTaskRepository(
            task(status = DownloadStatus.Completed),
        )
        val scheduler = RecordingDownloadTaskScheduler()
        val controller = PersistentDownloadController(
            repository = repository,
            scheduler = scheduler,
            nowEpochMs = { 102 },
        )

        controller.cancel(DownloadTaskId("task-1"))

        val stored = repository.requireTask(DownloadTaskId("task-1"))
        assertEquals(DownloadStatus.Completed, stored.status)
        assertTrue(scheduler.cancelled.isEmpty())
    }

    @Test
    fun cancelAllCancelsEveryActiveTask() = runBlocking {
        val repository = InMemoryDownloadTaskRepository(
            task(id = "queued", status = DownloadStatus.Queued),
            task(id = "downloading", status = DownloadStatus.Downloading),
            task(id = "completed", status = DownloadStatus.Completed),
        )
        val scheduler = RecordingDownloadTaskScheduler()
        val controller = PersistentDownloadController(repository, scheduler)

        controller.cancelAll()

        assertEquals(DownloadStatus.Cancelled, repository.requireTask(DownloadTaskId("queued")).status)
        assertEquals(DownloadStatus.Cancelled, repository.requireTask(DownloadTaskId("downloading")).status)
        assertEquals(DownloadStatus.Completed, repository.requireTask(DownloadTaskId("completed")).status)
        assertEquals(
            setOf(DownloadTaskId("queued"), DownloadTaskId("downloading")),
            scheduler.cancelled.toSet(),
        )
    }

    @Test
    fun recoveryOnlyReschedulesPersistedFinalizationWithAStableFile() = runBlocking {
        val repository = InMemoryDownloadTaskRepository(
            task(id = "ready", status = DownloadStatus.Finalizing, localPath = "/downloads/ready.flac"),
            task(id = "missing", status = DownloadStatus.Finalizing),
            task(id = "active", status = DownloadStatus.Downloading),
        )
        val scheduler = RecordingDownloadTaskScheduler()
        val controller = PersistentDownloadController(repository, scheduler)

        assertEquals(1, controller.recoverInterruptedTasks())
        assertEquals(listOf(DownloadTaskId("ready")), scheduler.scheduled.map { it.id })
    }
}

private class InMemoryDownloadTaskRepository(
    vararg initialTasks: DownloadTask,
) : DownloadTaskRepository {
    private val state = MutableStateFlow(initialTasks.associateBy { it.id })

    override fun observeTasks(): Flow<List<DownloadTask>> {
        return state.map { tasks -> tasks.values.toList() }
    }

    override fun observeActiveTasks(): Flow<List<DownloadTask>> {
        return state.map { tasks ->
            tasks.values.filter { task ->
                task.status !in setOf(
                    DownloadStatus.Completed,
                    DownloadStatus.Cancelled,
                )
            }
        }
    }

    override fun observeTask(id: DownloadTaskId): Flow<DownloadTask?> {
        return state.map { tasks -> tasks[id] }
    }

    override suspend fun getTask(id: DownloadTaskId): DownloadTask? {
        return state.value[id]
    }

    override suspend fun upsertTask(task: DownloadTask) {
        state.value = state.value + (task.id to task)
    }

    override suspend fun updateTask(task: DownloadTask) {
        upsertTask(task)
    }

    override suspend fun deleteTask(id: DownloadTaskId) {
        state.value = state.value - id
    }

    fun requireTask(id: DownloadTaskId): DownloadTask {
        return requireNotNull(state.value[id])
    }
}

private class RecordingDownloadTaskScheduler : DownloadTaskScheduler {
    val scheduled = mutableListOf<DownloadTask>()
    val paused = mutableListOf<DownloadTaskId>()
    val cancelled = mutableListOf<DownloadTaskId>()

    override suspend fun schedule(task: DownloadTask) {
        scheduled += task
    }

    override suspend fun pause(id: DownloadTaskId) {
        paused += id
    }

    override suspend fun cancel(id: DownloadTaskId) {
        cancelled += id
    }
}

private fun task(
    id: String = "task-1",
    status: DownloadStatus,
    errorMessage: String? = null,
    localPath: String? = null,
): DownloadTask {
    return DownloadTask(
        id = DownloadTaskId(id),
        mediaId = MediaId(
            sourceId = SourceId("webdav"),
            mediaType = MediaType.Track,
            remoteId = "track-1",
        ),
        title = "Track",
        status = status,
        errorMessage = errorMessage,
        localPath = localPath,
        createdAtEpochMs = 1,
        updatedAtEpochMs = 1,
    )
}
