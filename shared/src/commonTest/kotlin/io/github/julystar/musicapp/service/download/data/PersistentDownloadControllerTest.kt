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

class PersistentDownloadControllerTest {
    @Test
    fun pauseResumeAndRetryPersistStatusChanges() = runBlocking {
        var now = 10L
        val repository = FakeDownloadTaskRepository()
        val scheduler = RecordingDownloadTaskScheduler()
        val controller = PersistentDownloadController(repository, scheduler) { now }
        val id = DownloadTaskId("task-1")

        controller.enqueue(
            task(
                id = id,
                status = DownloadStatus.Downloading,
                errorMessage = "previous",
            )
        )
        assertEquals(DownloadStatus.Queued, repository.getTask(id)?.status)
        assertEquals(null, repository.getTask(id)?.errorMessage)
        assertEquals(listOf(DownloadStatus.Queued), scheduler.scheduled.map { it.status })

        repository.updateTask(repository.getTask(id)!!.copy(status = DownloadStatus.Downloading))
        now = 20
        controller.pause(id)
        assertEquals(DownloadStatus.Paused, repository.getTask(id)?.status)
        assertEquals(20, repository.getTask(id)?.updatedAtEpochMs)
        assertEquals(listOf(id), scheduler.paused)

        now = 30
        controller.resume(id)
        assertEquals(DownloadStatus.Queued, repository.getTask(id)?.status)
        assertEquals(30, repository.getTask(id)?.updatedAtEpochMs)
        assertEquals(
            listOf(DownloadStatus.Queued, DownloadStatus.Queued),
            scheduler.scheduled.map { it.status },
        )

        repository.updateTask(
            repository.getTask(id)!!.copy(
                status = DownloadStatus.Failed,
                errorMessage = "network",
            )
        )
        now = 40
        controller.retry(id)
        assertEquals(DownloadStatus.Queued, repository.getTask(id)?.status)
        assertEquals(null, repository.getTask(id)?.errorMessage)
        assertEquals(
            listOf(DownloadStatus.Queued, DownloadStatus.Queued, DownloadStatus.Queued),
            scheduler.scheduled.map { it.status },
        )

        now = 50
        controller.cancel(id)
        assertEquals(DownloadStatus.Cancelled, repository.getTask(id)?.status)
        assertEquals(listOf(id), scheduler.cancelled)
    }

    @Test
    fun terminalTasksIgnoreUpdates() = runBlocking {
        val repository = FakeDownloadTaskRepository()
        val scheduler = RecordingDownloadTaskScheduler()
        val controller = PersistentDownloadController(repository, scheduler) { 20 }
        val id = DownloadTaskId("task-1")
        repository.upsertTask(task(id = id, status = DownloadStatus.Completed))

        controller.retry(id)
        controller.cancel(id)

        assertEquals(DownloadStatus.Completed, repository.getTask(id)?.status)
        assertEquals(emptyList(), scheduler.scheduled)
        assertEquals(emptyList(), scheduler.cancelled)
    }

    @Test
    fun enqueuePersistsQueuedTaskBeforeScheduling() = runBlocking {
        val repository = FakeDownloadTaskRepository()
        val persistedAtSchedule = mutableListOf<DownloadTask?>()
        val scheduler = RecordingDownloadTaskScheduler(
            onSchedule = { task ->
                persistedAtSchedule += repository.getTask(task.id)
            },
        )
        val controller = PersistentDownloadController(repository, scheduler) { 20 }
        val id = DownloadTaskId("task-1")

        controller.enqueue(
            task(
                id = id,
                status = DownloadStatus.Downloading,
                errorMessage = "previous",
            )
        )

        assertEquals(DownloadStatus.Queued, persistedAtSchedule.single()?.status)
        assertEquals(null, persistedAtSchedule.single()?.errorMessage)
    }

    private fun task(
        id: DownloadTaskId,
        status: DownloadStatus,
        errorMessage: String? = null,
    ): DownloadTask {
        return DownloadTask(
            id = id,
            mediaId = MediaId(
                sourceId = SourceId("webdav"),
                mediaType = MediaType.Track,
                remoteId = "track-1",
            ),
            title = "Track",
            status = status,
            errorMessage = errorMessage,
            createdAtEpochMs = 1,
            updatedAtEpochMs = 1,
        )
    }
}

private class RecordingDownloadTaskScheduler(
    private val onSchedule: suspend (DownloadTask) -> Unit = {},
) : DownloadTaskScheduler {
    val scheduled = mutableListOf<DownloadTask>()
    val paused = mutableListOf<DownloadTaskId>()
    val cancelled = mutableListOf<DownloadTaskId>()

    override suspend fun schedule(task: DownloadTask) {
        onSchedule(task)
        scheduled += task
    }

    override suspend fun pause(id: DownloadTaskId) {
        paused += id
    }

    override suspend fun cancel(id: DownloadTaskId) {
        cancelled += id
    }
}

private class FakeDownloadTaskRepository : DownloadTaskRepository {
    private val tasks = MutableStateFlow(emptyList<DownloadTask>())

    override fun observeTasks(): Flow<List<DownloadTask>> {
        return tasks
    }

    override fun observeActiveTasks(): Flow<List<DownloadTask>> {
        return tasks.map { current ->
            current.filter { task ->
                task.status in setOf(
                    DownloadStatus.Queued,
                    DownloadStatus.Resolving,
                    DownloadStatus.Downloading,
                    DownloadStatus.Finalizing,
                    DownloadStatus.Paused,
                )
            }
        }
    }

    override fun observeTask(id: DownloadTaskId): Flow<DownloadTask?> {
        return tasks.map { current -> current.firstOrNull { it.id == id } }
    }

    override suspend fun getTask(id: DownloadTaskId): DownloadTask? {
        return tasks.value.firstOrNull { it.id == id }
    }

    override suspend fun upsertTask(task: DownloadTask) {
        tasks.value = tasks.value.filterNot { it.id == task.id } + task
    }

    override suspend fun updateTask(task: DownloadTask) {
        upsertTask(task)
    }

    override suspend fun deleteTask(id: DownloadTaskId) {
        tasks.value = tasks.value.filterNot { it.id == id }
    }
}
