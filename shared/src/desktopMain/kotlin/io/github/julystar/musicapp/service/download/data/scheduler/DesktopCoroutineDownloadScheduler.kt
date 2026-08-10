package io.github.julystar.musicapp.service.download.data.scheduler

import io.github.julystar.musicapp.platform.getAppDataDirectory
import io.github.julystar.musicapp.service.download.domain.DownloadFinalizationRequest
import io.github.julystar.musicapp.service.download.domain.DownloadFinalizationResult
import io.github.julystar.musicapp.service.download.domain.DownloadFinalizer
import io.github.julystar.musicapp.service.download.domain.DownloadStatus
import io.github.julystar.musicapp.service.download.domain.DownloadTask
import io.github.julystar.musicapp.service.download.domain.DownloadTaskId
import io.github.julystar.musicapp.service.download.domain.DownloadTaskRepository
import io.github.julystar.musicapp.service.download.domain.DownloadTaskScheduler
import io.github.julystar.musicapp.service.download.domain.canTransitionTo
import io.github.julystar.musicapp.source.api.LegacyStoragePlaybackResolver
import io.github.julystar.musicapp.source.api.MusicSourceRegistry
import io.github.julystar.musicapp.source.api.PlaybackResource
import io.github.julystar.musicapp.source.api.SourcePlaybackResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.time.Clock

internal class DesktopCoroutineDownloadScheduler(
    private val repository: DownloadTaskRepository,
    private val sourceRegistry: MusicSourceRegistry,
    private val legacyStoragePlaybackResolver: LegacyStoragePlaybackResolver,
    private val scope: CoroutineScope,
    private val downloadFinalizer: DownloadFinalizer = DownloadFinalizer.Disabled,
    private val downloadDirectoryProvider: () -> File = {
        File(getAppDataDirectory(), "downloads")
    },
    private val resourceOpener: DesktopDownloadResourceOpener = JvmDesktopDownloadResourceOpener,
    private val maxConcurrentTasks: Int = 2,
    private val nowEpochMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : DownloadTaskScheduler {
    private val activeJobs = ConcurrentHashMap<DownloadTaskId, Job>()
    private val semaphore = Semaphore(maxConcurrentTasks)

    init {
        require(maxConcurrentTasks > 0) {
            "Desktop download scheduler concurrency must be positive"
        }
    }

    override suspend fun schedule(task: DownloadTask) {
        activeJobs.remove(task.id)?.cancelAndJoin()
        val job = scope.launch {
            semaphore.withPermit {
                runTask(task)
            }
        }
        activeJobs[task.id] = job
        job.invokeOnCompletion {
            activeJobs.remove(task.id, job)
        }
    }

    override suspend fun pause(id: DownloadTaskId) {
        activeJobs.remove(id)?.cancel()
    }

    override suspend fun cancel(id: DownloadTaskId) {
        activeJobs.remove(id)?.cancel()
    }

    private suspend fun runTask(task: DownloadTask) {
        if (task.status == DownloadStatus.Finalizing && task.localPath != null) {
            finalizeDownloadedFile(task)
            return
        }
        if (updateStatus(task.id, DownloadStatus.Resolving) == null) return

        val source = sourceRegistry.sourceOrNull(task.mediaId.sourceId)
        if (source == null) {
            markFailed(task.id, "Music source is unavailable")
            return
        }

        val resource = when (val result = source.resolvePlayback(task.mediaId)) {
            is SourcePlaybackResult.Success -> result.resource
            is SourcePlaybackResult.Failure -> {
                markFailed(task.id, "Unable to resolve download resource: ${result.reason}")
                return
            }
        }

        try {
            downloadResource(task, resource)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            markFailed(task.id, e.message ?: "Download failed")
        } finally {
            legacyStoragePlaybackResolver.release(resource.uri)
        }
    }

    private suspend fun downloadResource(
        task: DownloadTask,
        resource: PlaybackResource,
    ) {
        withContext(Dispatchers.IO) {
            val targetFile = targetFileFor(task, resource)
            val partFile = File(targetFile.parentFile, "${targetFile.name}.part")
            targetFile.parentFile.mkdirs()
            if (task.downloadedBytes == 0L) {
                partFile.delete()
            }
            val resumeOffset = partFile.length()

            resourceOpener.open(resource, resumeOffset).use { opened ->
                val totalBytes = opened.totalBytes
                if (
                    resumeOffset > 0 &&
                    task.totalBytes != null &&
                    totalBytes != null &&
                    task.totalBytes != totalBytes
                ) {
                    partFile.delete()
                    throw IOException("Remote file size changed; retry the download")
                }
                updateStatus(task.id, DownloadStatus.Downloading) { current ->
                    current.copy(
                        downloadedBytes = resumeOffset,
                        totalBytes = normalizedTotalBytes(totalBytes, resumeOffset),
                        mimeType = resource.mimeType ?: current.mimeType,
                    )
                } ?: return@withContext

                var downloadedBytes = resumeOffset
                var lastPersistedBytes = resumeOffset
                try {
                    FileOutputStream(partFile, true).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = opened.input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read.toLong()
                            if (downloadedBytes - lastPersistedBytes >= PROGRESS_UPDATE_BYTES) {
                                updateDownloadingProgress(
                                    id = task.id,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                )
                                lastPersistedBytes = downloadedBytes
                            }
                        }
                        output.fd.sync()
                    }
                } catch (e: CancellationException) {
                    val current = repository.getTask(task.id)
                    if (current?.status == DownloadStatus.Cancelled) {
                        partFile.delete()
                    } else {
                        persistPartialProgress(
                            id = task.id,
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes,
                        )
                    }
                    throw e
                }

                updateDownloadingProgress(
                    id = task.id,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                )
                movePartFile(partFile, targetFile)
                val finalizing = updateStatus(task.id, DownloadStatus.Finalizing) { current ->
                    current.copy(
                        downloadedBytes = downloadedBytes,
                        totalBytes = normalizedTotalBytes(totalBytes, downloadedBytes),
                        localPath = targetFile.absolutePath,
                        mimeType = resource.mimeType ?: current.mimeType,
                        errorMessage = null,
                        finalizationWarning = null,
                    )
                } ?: return@withContext
                finalizeDownloadedFile(finalizing)
            }
        }
    }

    private suspend fun finalizeDownloadedFile(task: DownloadTask) {
        val localPath = task.localPath ?: return
        when (
            val result = downloadFinalizer.finalize(
                DownloadFinalizationRequest(
                    mediaId = task.mediaId,
                    localPath = localPath,
                    mimeType = task.mimeType,
                    fallbackTitle = task.title,
                    fallbackArtist = task.artist,
                    fallbackAlbum = task.album,
                    expectedDurationMs = task.durationMs,
                    expectedBytes = task.totalBytes ?: task.downloadedBytes,
                )
            )
        ) {
            is DownloadFinalizationResult.Success -> {
                updateStatus(task.id, DownloadStatus.Completed) { current ->
                    current.copy(
                        localPath = result.localPath,
                        errorMessage = null,
                        finalizationWarning = result.warnings.toWarningMessage(),
                    )
                }
            }
            is DownloadFinalizationResult.Failure -> markFailed(task.id, result.message)
        }
    }

    private suspend fun updateDownloadingProgress(
        id: DownloadTaskId,
        downloadedBytes: Long,
        totalBytes: Long?,
    ) {
        val current = repository.getTask(id) ?: return
        if (current.status != DownloadStatus.Downloading) return
        repository.updateTask(
            current.copy(
                downloadedBytes = downloadedBytes,
                totalBytes = normalizedTotalBytes(totalBytes, downloadedBytes),
                updatedAtEpochMs = nowEpochMs(),
            )
        )
    }

    private suspend fun persistPartialProgress(
        id: DownloadTaskId,
        downloadedBytes: Long,
        totalBytes: Long?,
    ) {
        val current = repository.getTask(id) ?: return
        if (current.status !in setOf(DownloadStatus.Downloading, DownloadStatus.Paused)) return
        repository.updateTask(
            current.copy(
                downloadedBytes = downloadedBytes,
                totalBytes = normalizedTotalBytes(totalBytes, downloadedBytes),
                updatedAtEpochMs = nowEpochMs(),
            )
        )
    }

    private suspend fun markFailed(id: DownloadTaskId, message: String) {
        updateStatus(id, DownloadStatus.Failed) { current ->
            current.copy(errorMessage = message)
        }
    }

    private suspend fun updateStatus(
        id: DownloadTaskId,
        status: DownloadStatus,
        transform: (DownloadTask) -> DownloadTask = { it },
    ): DownloadTask? {
        val current = repository.getTask(id) ?: return null
        if (!current.status.canTransitionTo(status)) return null
        val updated = transform(current).copy(
            status = status,
            updatedAtEpochMs = nowEpochMs(),
        )
        repository.updateTask(updated)
        return updated
    }

    private fun targetFileFor(
        task: DownloadTask,
        resource: PlaybackResource,
    ): File {
        val directory = downloadDirectoryProvider()
        return File(
            directory,
            "${safeFileName(task.id.value)}${extensionFor(task, resource)}",
        )
    }

    private fun movePartFile(partFile: File, targetFile: File) {
        if (!targetFile.exists() && partFile.renameTo(targetFile)) return
        val commitFile = File(targetFile.parentFile, "${targetFile.name}.commit.tmp")
        commitFile.delete()
        FileInputStream(partFile).use { input ->
            FileOutputStream(commitFile).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
        if (targetFile.exists() && !targetFile.delete()) {
            commitFile.delete()
            throw IOException("Unable to replace previous download")
        }
        if (!commitFile.renameTo(targetFile)) {
            commitFile.delete()
            throw IOException("Unable to commit downloaded file")
        }
        partFile.delete()
    }
}

private fun List<String>.toWarningMessage(): String? =
    joinToString("; ").takeIf(String::isNotBlank)?.take(1_000)

internal interface DesktopDownloadResourceOpener {
    fun open(
        resource: PlaybackResource,
        offset: Long = 0,
    ): OpenedDesktopDownloadResource
}

internal class OpenedDesktopDownloadResource(
    val input: InputStream,
    val totalBytes: Long?,
    private val closeAction: () -> Unit = {},
) : Closeable {
    override fun close() {
        try {
            input.close()
        } finally {
            closeAction()
        }
    }
}

internal object JvmDesktopDownloadResourceOpener : DesktopDownloadResourceOpener {
    override fun open(
        resource: PlaybackResource,
        offset: Long,
    ): OpenedDesktopDownloadResource {
        require(offset >= 0) { "Download offset cannot be negative" }
        val uri = parseUri(resource.uri)
        return when (uri?.scheme?.lowercase()) {
            "http",
            "https" -> openHttp(uri, resource, offset)
            "file" -> openFile(File(uri), offset)
            null,
            "" -> openFile(File(resource.uri), offset)
            else -> {
                if (resource.isLocal) {
                    openFile(File(resource.uri), offset)
                } else {
                    throw IOException("Unsupported download URI scheme: ${uri.scheme}")
                }
            }
        }
    }

    private fun openHttp(
        uri: URI,
        resource: PlaybackResource,
        offset: Long,
    ): OpenedDesktopDownloadResource {
        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        resource.headers.forEach { (key, value) ->
            connection.setRequestProperty(key, value)
        }
        if (offset > 0) {
            connection.setRequestProperty("Range", "bytes=$offset-")
        }
        connection.connect()
        if (
            connection.responseCode !in 200..299 ||
            (offset > 0 && connection.responseCode != HttpURLConnection.HTTP_PARTIAL)
        ) {
            val responseCode = connection.responseCode
            connection.disconnect()
            throw IOException("HTTP download failed with status $responseCode")
        }
        return OpenedDesktopDownloadResource(
            input = connection.inputStream,
            totalBytes = connection.totalResponseBytes(offset),
            closeAction = { connection.disconnect() },
        )
    }

    private fun openFile(
        file: File,
        offset: Long,
    ): OpenedDesktopDownloadResource {
        if (!file.isFile) {
            throw IOException("Download source file does not exist")
        }
        if (offset > file.length()) {
            throw IOException("Download offset exceeds source file size")
        }
        val input = FileInputStream(file)
        input.channel.position(offset)
        return OpenedDesktopDownloadResource(
            input = input,
            totalBytes = file.length(),
        )
    }

    private fun parseUri(value: String): URI? {
        return runCatching { URI(value) }.getOrNull()
    }
}

private fun HttpURLConnection.totalResponseBytes(offset: Long): Long? {
    val contentRangeTotal = getHeaderField("Content-Range")
        ?.substringAfterLast('/', missingDelimiterValue = "")
        ?.toLongOrNull()
    return contentRangeTotal
        ?: contentLengthLong.takeIf { it >= 0 }?.let { length -> length + offset }
}

private fun normalizedTotalBytes(totalBytes: Long?, downloadedBytes: Long): Long? {
    return totalBytes?.let { max(it, downloadedBytes) }
}

private fun safeFileName(value: String): String {
    return value
        .map { character ->
            if (character.isLetterOrDigit() || character == '-' || character == '_' || character == '.') {
                character
            } else {
                '_'
            }
        }
        .joinToString("")
        .take(96)
        .ifBlank { "download" }
}

private fun extensionFor(
    task: DownloadTask,
    resource: PlaybackResource,
): String {
    val fromPath = sequenceOf(
        task.mediaId.remoteId,
        task.title,
        resource.uri.substringBefore('?'),
    )
        .mapNotNull(::extensionFromPath)
        .firstOrNull()
    return fromPath ?: extensionFromMimeType(resource.mimeType) ?: ".audio"
}

private fun extensionFromPath(path: String): String? {
    val extension = path
        .substringAfterLast('/', path)
        .substringAfterLast('.', missingDelimiterValue = "")
        .takeIf { it.isNotBlank() && it.length <= 8 }
        ?.lowercase()
        ?: return null
    return ".$extension"
}

private fun extensionFromMimeType(mimeType: String?): String? {
    return when (mimeType) {
        "audio/flac" -> ".flac"
        "audio/mpeg" -> ".mp3"
        "audio/mp4" -> ".m4a"
        "audio/ogg" -> ".ogg"
        "audio/opus" -> ".opus"
        "audio/wav" -> ".wav"
        else -> null
    }
}

private const val PROGRESS_UPDATE_BYTES = 256 * 1024
