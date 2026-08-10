package io.github.julystar.musicapp.service.download.data.scheduler

import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.MediaType
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.SourceId
import io.github.julystar.musicapp.service.download.domain.DownloadStatus
import io.github.julystar.musicapp.service.download.domain.DownloadFinalizationResult
import io.github.julystar.musicapp.service.download.domain.DownloadFinalizer
import io.github.julystar.musicapp.service.download.domain.DownloadTask
import io.github.julystar.musicapp.service.download.domain.DownloadTaskId
import io.github.julystar.musicapp.service.download.domain.DownloadTaskRepository
import io.github.julystar.musicapp.source.api.LegacyStorageKind
import io.github.julystar.musicapp.source.api.LegacyStoragePlaybackResolver
import io.github.julystar.musicapp.source.api.MusicSource
import io.github.julystar.musicapp.source.api.MusicSourceDescriptor
import io.github.julystar.musicapp.source.api.MusicSourceRegistry
import io.github.julystar.musicapp.source.api.PlaybackResource
import io.github.julystar.musicapp.source.api.SourceAuthFailureReason
import io.github.julystar.musicapp.source.api.SourceAuthResult
import io.github.julystar.musicapp.source.api.SourceCapability
import io.github.julystar.musicapp.source.api.SourceConfiguration
import io.github.julystar.musicapp.source.api.SourceListFailureReason
import io.github.julystar.musicapp.source.api.SourceListResult
import io.github.julystar.musicapp.source.api.SourcePlaybackFailureReason
import io.github.julystar.musicapp.source.api.SourcePlaybackResult
import io.github.julystar.musicapp.source.api.SourceSearchFailureReason
import io.github.julystar.musicapp.source.api.SourceSearchResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopCoroutineDownloadSchedulerTest {
    @Test
    fun scheduleCopiesResolvedResourceAndCompletesTask() = runBlocking {
        val tempDir = Files.createTempDirectory("musicapp-download-test").toFile()
        val sourceFile = File(tempDir, "source.flac").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val repository = FakeDownloadTaskRepository()
        val task = task()
        repository.upsertTask(task)
        val playbackResourceResolver = RecordingLegacyStoragePlaybackResolver()
        val scheduler = DesktopCoroutineDownloadScheduler(
            repository = repository,
            sourceRegistry = MusicSourceRegistry(
                listOf(
                    FakeMusicSource(
                        result = SourcePlaybackResult.Success(
                            PlaybackResource(
                                uri = sourceFile.toURI().toString(),
                                mimeType = "audio/flac",
                                isLocal = true,
                            )
                        )
                    )
                )
            ),
            legacyStoragePlaybackResolver = playbackResourceResolver,
            scope = this,
            downloadDirectoryProvider = { File(tempDir, "downloads") },
            maxConcurrentTasks = 1,
            nowEpochMs = { 20 },
        )

        scheduler.schedule(task)

        val completed = awaitTask(repository, task.id, DownloadStatus.Completed)
        val localPath = completed.localPath!!
        assertEquals(sourceFile.readBytes().toList(), File(localPath).readBytes().toList())
        assertEquals(4, completed.downloadedBytes)
        assertEquals(4, completed.totalBytes)
        assertEquals("audio/flac", completed.mimeType)
        assertTrue(
            repository.statuses.containsAll(
                listOf(
                    DownloadStatus.Downloading,
                    DownloadStatus.Finalizing,
                    DownloadStatus.Completed,
                )
            )
        )
        awaitRelease(playbackResourceResolver, sourceFile.toURI().toString())
        assertEquals(listOf(sourceFile.toURI().toString()), playbackResourceResolver.releasedUris)
    }

    @Test
    fun finalizationWarningStillCompletesTask() = runBlocking {
        val tempDir = Files.createTempDirectory("musicapp-download-warning-test").toFile()
        val sourceFile = File(tempDir, "source.flac").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        val repository = FakeDownloadTaskRepository()
        val task = task()
        repository.upsertTask(task)
        val scheduler = DesktopCoroutineDownloadScheduler(
            repository = repository,
            sourceRegistry = MusicSourceRegistry(
                listOf(
                    FakeMusicSource(
                        SourcePlaybackResult.Success(
                            PlaybackResource(
                                uri = sourceFile.toURI().toString(),
                                mimeType = "audio/flac",
                                isLocal = true,
                            )
                        )
                    )
                )
            ),
            legacyStoragePlaybackResolver = RecordingLegacyStoragePlaybackResolver(),
            scope = this,
            downloadFinalizer = DownloadFinalizer { request ->
                DownloadFinalizationResult.Success(
                    localPath = request.localPath,
                    warnings = listOf("Artwork was too large"),
                )
            },
            downloadDirectoryProvider = { File(tempDir, "downloads") },
            maxConcurrentTasks = 1,
            nowEpochMs = { 25 },
        )

        scheduler.schedule(task)

        val completed = awaitTask(repository, task.id, DownloadStatus.Completed)
        assertEquals("Artwork was too large", completed.finalizationWarning)
        assertTrue(File(completed.localPath!!).isFile)
    }

    @Test
    fun failedResolveMarksTaskFailedWithoutCreatingFile() = runBlocking {
        val tempDir = Files.createTempDirectory("musicapp-download-failed-test").toFile()
        val repository = FakeDownloadTaskRepository()
        val task = task()
        repository.upsertTask(task)
        val scheduler = DesktopCoroutineDownloadScheduler(
            repository = repository,
            sourceRegistry = MusicSourceRegistry(
                listOf(
                    FakeMusicSource(
                        result = SourcePlaybackResult.Failure(
                            SourcePlaybackFailureReason.Unavailable
                        )
                    )
                )
            ),
            legacyStoragePlaybackResolver = RecordingLegacyStoragePlaybackResolver(),
            scope = this,
            downloadDirectoryProvider = { File(tempDir, "downloads") },
            maxConcurrentTasks = 1,
            nowEpochMs = { 30 },
        )

        scheduler.schedule(task)

        val failed = awaitTask(repository, task.id, DownloadStatus.Failed)
        assertTrue(failed.errorMessage!!.contains("Unavailable"))
        assertFalse(File(tempDir, "downloads").exists())
    }

    @Test
    fun resumeUsesPartFileOffsetWithoutRedownloadingPrefix() = runBlocking {
        val tempDir = Files.createTempDirectory("musicapp-download-resume-test").toFile()
        val downloadDir = File(tempDir, "downloads").apply { mkdirs() }
        File(downloadDir, "download-1.flac.part").writeBytes(byteArrayOf(1, 2))
        val repository = FakeDownloadTaskRepository()
        val task = task().copy(downloadedBytes = 2, totalBytes = 4)
        repository.upsertTask(task)
        var openedOffset: Long? = null
        val scheduler = DesktopCoroutineDownloadScheduler(
            repository = repository,
            sourceRegistry = MusicSourceRegistry(
                listOf(
                    FakeMusicSource(
                        result = SourcePlaybackResult.Success(
                            PlaybackResource(
                                uri = "http://127.0.0.1/download.flac",
                                mimeType = "audio/flac",
                            )
                        )
                    )
                )
            ),
            legacyStoragePlaybackResolver = RecordingLegacyStoragePlaybackResolver(),
            scope = this,
            downloadDirectoryProvider = { downloadDir },
            resourceOpener = object : DesktopDownloadResourceOpener {
                override fun open(
                    resource: PlaybackResource,
                    offset: Long,
                ): OpenedDesktopDownloadResource {
                    openedOffset = offset
                    return OpenedDesktopDownloadResource(
                        input = ByteArrayInputStream(byteArrayOf(3, 4)),
                        totalBytes = 4,
                    )
                }
            },
            maxConcurrentTasks = 1,
            nowEpochMs = { 40 },
        )

        scheduler.schedule(task)

        val completed = awaitTask(repository, task.id, DownloadStatus.Completed)
        assertEquals(2L, openedOffset)
        assertEquals(listOf<Byte>(1, 2, 3, 4), File(completed.localPath!!).readBytes().toList())
        assertEquals(4, completed.downloadedBytes)
    }

    @Test
    fun resumeRejectsChangedRemoteSizeAndRemovesStalePartFile() = runBlocking {
        val tempDir = Files.createTempDirectory("musicapp-download-size-change-test").toFile()
        val downloadDir = File(tempDir, "downloads").apply { mkdirs() }
        val partFile = File(downloadDir, "download-1.flac.part").apply {
            writeBytes(byteArrayOf(1, 2))
        }
        val repository = FakeDownloadTaskRepository()
        val task = task().copy(downloadedBytes = 2, totalBytes = 4)
        repository.upsertTask(task)
        val scheduler = DesktopCoroutineDownloadScheduler(
            repository = repository,
            sourceRegistry = MusicSourceRegistry(
                listOf(
                    FakeMusicSource(
                        result = SourcePlaybackResult.Success(
                            PlaybackResource(
                                uri = "http://127.0.0.1/download.flac",
                                mimeType = "audio/flac",
                            )
                        )
                    )
                )
            ),
            legacyStoragePlaybackResolver = RecordingLegacyStoragePlaybackResolver(),
            scope = this,
            downloadDirectoryProvider = { downloadDir },
            resourceOpener = object : DesktopDownloadResourceOpener {
                override fun open(
                    resource: PlaybackResource,
                    offset: Long,
                ): OpenedDesktopDownloadResource {
                    return OpenedDesktopDownloadResource(
                        input = ByteArrayInputStream(byteArrayOf(3, 4, 5)),
                        totalBytes = 5,
                    )
                }
            },
            maxConcurrentTasks = 1,
            nowEpochMs = { 50 },
        )

        scheduler.schedule(task)

        val failed = awaitTask(repository, task.id, DownloadStatus.Failed)
        assertTrue(failed.errorMessage!!.contains("size changed"))
        assertFalse(partFile.exists())
    }

    private suspend fun awaitTask(
        repository: FakeDownloadTaskRepository,
        id: DownloadTaskId,
        status: DownloadStatus,
    ): DownloadTask {
        return withTimeout(5_000) {
            while (true) {
                val task = repository.getTask(id)
                if (task?.status == status) return@withTimeout task
                delay(10)
            }
            error("unreachable")
        }
    }

    private suspend fun awaitRelease(
        resolver: RecordingLegacyStoragePlaybackResolver,
        uri: String,
    ) {
        withTimeout(5_000) {
            while (uri !in resolver.releasedUris) {
                delay(10)
            }
        }
    }

    private fun task(): DownloadTask {
        return DownloadTask(
            id = DownloadTaskId("download-1"),
            mediaId = MediaId(
                sourceId = SourceId("webdav"),
                mediaType = MediaType.Track,
                remoteId = "account-1:/Music/source.flac",
            ),
            title = "Source",
            createdAtEpochMs = 1,
            updatedAtEpochMs = 1,
        )
    }
}

private class FakeMusicSource(
    private val result: SourcePlaybackResult,
) : MusicSource {
    override val descriptor = MusicSourceDescriptor(
        id = SourceId("webdav"),
        displayName = "WebDAV",
    )
    override val capabilities = setOf(SourceCapability.Download)

    override suspend fun authenticate(configuration: SourceConfiguration): SourceAuthResult {
        return SourceAuthResult.Failure(SourceAuthFailureReason.UnsupportedConfiguration)
    }

    override suspend fun list(
        accountId: SourceAccountId,
        directoryId: String?,
    ): SourceListResult {
        return SourceListResult.Failure(SourceListFailureReason.UnsupportedAccount)
    }

    override suspend fun search(
        accountId: SourceAccountId,
        query: String,
        limit: Int,
    ): SourceSearchResult {
        return SourceSearchResult.Failure(SourceSearchFailureReason.Unsupported)
    }

    override suspend fun resolvePlayback(mediaId: MediaId): SourcePlaybackResult {
        return result
    }
}

private class RecordingLegacyStoragePlaybackResolver : LegacyStoragePlaybackResolver {
    val releasedUris = mutableListOf<String>()

    override suspend fun resolve(
        accountId: SourceAccountId,
        path: String,
        expectedStorageKind: LegacyStorageKind,
    ): SourcePlaybackResult {
        return SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unavailable)
    }

    override suspend fun release(uri: String) {
        releasedUris += uri
    }

    override suspend fun releaseAll() = Unit
}

private class FakeDownloadTaskRepository : DownloadTaskRepository {
    private val tasks = MutableStateFlow(emptyList<DownloadTask>())
    val statuses = mutableListOf<DownloadStatus>()

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
        statuses += task.status
    }

    override suspend fun updateTask(task: DownloadTask) {
        upsertTask(task)
    }

    override suspend fun deleteTask(id: DownloadTaskId) {
        tasks.value = tasks.value.filterNot { it.id == id }
    }
}
