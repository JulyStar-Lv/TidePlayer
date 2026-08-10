package io.github.julystar.musicapp.feature.downloads.presentation

import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.MediaType
import io.github.julystar.musicapp.core.domain.model.SourceId
import io.github.julystar.musicapp.service.download.domain.DownloadController
import io.github.julystar.musicapp.service.download.domain.DownloadStatus
import io.github.julystar.musicapp.service.download.domain.DownloadTask
import io.github.julystar.musicapp.service.download.domain.DownloadTaskId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DownloadsViewModelTest {
    @Test
    fun mapsDownloadTasksIntoPresentationState() = runDownloadsViewModelTest {
        val controller = FakeDownloadController(
            initialTasks = listOf(
                task(
                    id = DownloadTaskId("task-1"),
                    status = DownloadStatus.Downloading,
                    downloadedBytes = 512,
                    totalBytes = 1024,
                ),
                task(
                    id = DownloadTaskId("task-2"),
                    status = DownloadStatus.Failed,
                    errorMessage = "network",
                ),
            )
        )
        val viewModel = downloadsViewModel(controller, coroutineScope = this)
        awaitUntil { viewModel.state.value.tasks.size == 2 }

        val downloading = viewModel.state.value.tasks[0]
        assertEquals("Track task-1", downloading.title)
        assertEquals("Artist - Album", downloading.subtitle)
        assertEquals(DownloadStatus.Downloading, downloading.status)
        assertEquals(50, downloading.progressPercent)
        assertEquals(512, downloading.downloadedBytes)
        assertEquals(1024, downloading.totalBytes)
        assertEquals(0.5f, downloading.progressFraction)
        assertTrue(downloading.canPause)
        assertFalse(downloading.canResume)
        assertFalse(downloading.canRetry)
        assertTrue(downloading.canCancel)

        val failed = viewModel.state.value.tasks[1]
        assertEquals("network", failed.errorMessage)
        assertFalse(failed.canPause)
        assertFalse(failed.canResume)
        assertTrue(failed.canRetry)
        assertTrue(failed.canCancel)
    }

    @Test
    fun actionsDelegateToDownloadController() = runDownloadsViewModelTest {
        val controller = FakeDownloadController()
        val viewModel = downloadsViewModel(controller, coroutineScope = this)
        val id = DownloadTaskId("task-1")

        viewModel.onAction(DownloadsAction.Pause(id))
        viewModel.onAction(DownloadsAction.Resume(id))
        viewModel.onAction(DownloadsAction.Retry(id))
        viewModel.onAction(DownloadsAction.Cancel(id))
        awaitUntil {
            controller.paused == listOf(id) &&
                controller.resumed == listOf(id) &&
                controller.retried == listOf(id) &&
                controller.cancelled == listOf(id)
        }

        assertEquals(listOf(id), controller.paused)
        assertEquals(listOf(id), controller.resumed)
        assertEquals(listOf(id), controller.retried)
        assertEquals(listOf(id), controller.cancelled)
    }

    @Test
    fun `row keys stay unique when task ids repeat`() {
        val task = task(id = DownloadTaskId("task-1"), status = DownloadStatus.Queued)
            .toDownloadTaskUi()

        assertNotEquals(task.lazyListKey(0), task.copy(title = "Track duplicate").lazyListKey(1))
    }

    private fun task(
        id: DownloadTaskId,
        status: DownloadStatus,
        downloadedBytes: Long = 0,
        totalBytes: Long? = null,
        errorMessage: String? = null,
    ): DownloadTask {
        return DownloadTask(
            id = id,
            mediaId = MediaId(
                sourceId = SourceId("webdav"),
                mediaType = MediaType.Track,
                remoteId = id.value,
            ),
            title = "Track ${id.value}",
            artist = "Artist",
            album = "Album",
            status = status,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            errorMessage = errorMessage,
            createdAtEpochMs = 1,
            updatedAtEpochMs = 1,
        )
    }

    private fun downloadsViewModel(
        controller: DownloadController,
        coroutineScope: CoroutineScope,
    ): DownloadsViewModel {
        return DownloadsViewModel(
            downloadController = controller,
            coroutineScopeOverride = coroutineScope,
        )
    }
}

private fun runDownloadsViewModelTest(block: suspend CoroutineScope.() -> Unit) = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    try {
        scope.block()
    } finally {
        scope.cancel()
    }
}

private suspend fun awaitUntil(condition: () -> Boolean) {
    withTimeout(5_000) {
        while (!condition()) {
            delay(10)
        }
    }
}

private class FakeDownloadController(
    initialTasks: List<DownloadTask> = emptyList(),
) : DownloadController {
    private val currentTasks = MutableStateFlow(initialTasks)
    override val tasks: Flow<List<DownloadTask>> = currentTasks.asStateFlow()

    val paused = mutableListOf<DownloadTaskId>()
    val resumed = mutableListOf<DownloadTaskId>()
    val retried = mutableListOf<DownloadTaskId>()
    val cancelled = mutableListOf<DownloadTaskId>()

    override suspend fun enqueue(task: DownloadTask) {
        currentTasks.value = currentTasks.value.filterNot { it.id == task.id } + task
    }

    override suspend fun pause(id: DownloadTaskId) {
        paused += id
    }

    override suspend fun resume(id: DownloadTaskId) {
        resumed += id
    }

    override suspend fun cancel(id: DownloadTaskId) {
        cancelled += id
    }

    override suspend fun cancelAll() = Unit

    override suspend fun retry(id: DownloadTaskId) {
        retried += id
    }
}
