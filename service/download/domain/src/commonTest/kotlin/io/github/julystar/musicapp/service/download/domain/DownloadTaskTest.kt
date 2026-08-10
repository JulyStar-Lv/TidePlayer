package io.github.julystar.musicapp.service.download.domain

import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.MediaType
import io.github.julystar.musicapp.core.domain.model.SourceId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DownloadTaskTest {
    @Test
    fun statusTransitionsAllowPauseResumeRetryAndCancel() {
        assertTrue(DownloadStatus.Queued.canTransitionTo(DownloadStatus.Resolving))
        assertTrue(DownloadStatus.Resolving.canTransitionTo(DownloadStatus.Downloading))
        assertTrue(DownloadStatus.Downloading.canTransitionTo(DownloadStatus.Paused))
        assertTrue(DownloadStatus.Downloading.canTransitionTo(DownloadStatus.Finalizing))
        assertTrue(DownloadStatus.Finalizing.canTransitionTo(DownloadStatus.Completed))
        assertFalse(DownloadStatus.Downloading.canTransitionTo(DownloadStatus.Completed))
        assertTrue(DownloadStatus.Paused.canTransitionTo(DownloadStatus.Queued))
        assertTrue(DownloadStatus.Failed.canTransitionTo(DownloadStatus.Queued))
        assertTrue(DownloadStatus.Downloading.canTransitionTo(DownloadStatus.Cancelled))
    }

    @Test
    fun completedAndCancelledTasksAreTerminal() {
        assertFalse(DownloadStatus.Completed.canTransitionTo(DownloadStatus.Queued))
        assertFalse(DownloadStatus.Completed.canTransitionTo(DownloadStatus.Failed))
        assertFalse(DownloadStatus.Cancelled.canTransitionTo(DownloadStatus.Queued))
        assertFalse(DownloadStatus.Cancelled.canTransitionTo(DownloadStatus.Failed))
    }

    @Test
    fun taskRejectsInvalidProgress() {
        assertFailsWith<IllegalArgumentException> {
            task(downloadedBytes = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            task(downloadedBytes = 11, totalBytes = 10)
        }
    }

    private fun task(
        downloadedBytes: Long = 0,
        totalBytes: Long? = null,
    ): DownloadTask {
        return DownloadTask(
            id = DownloadTaskId("task-1"),
            mediaId = MediaId(
                sourceId = SourceId("webdav"),
                mediaType = MediaType.Track,
                remoteId = "track-1",
            ),
            title = "Track",
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            createdAtEpochMs = 1,
            updatedAtEpochMs = 1,
        )
    }
}
