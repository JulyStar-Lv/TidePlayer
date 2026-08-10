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
import io.github.julystar.musicapp.source.api.PlaybackResource
import io.github.julystar.musicapp.source.api.MusicSourceRegistry
import io.github.julystar.musicapp.source.api.SourcePlaybackResult
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSError
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDownloadDelegateProtocol
import platform.Foundation.NSURLSessionDownloadTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskDelegateProtocol
import platform.Foundation.setValue
import platform.darwin.NSObject
import platform.posix.O_RDONLY
import platform.posix.close
import platform.posix.fsync
import platform.posix.open
import platform.posix.rename
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.time.Clock

@OptIn(ExperimentalForeignApi::class)
internal class IosUrlSessionDownloadScheduler(
    private val repository: DownloadTaskRepository,
    private val sourceRegistry: MusicSourceRegistry,
    private val legacyStoragePlaybackResolver: LegacyStoragePlaybackResolver,
    private val scope: CoroutineScope,
    private val downloadFinalizer: DownloadFinalizer = DownloadFinalizer.Disabled,
    private val downloadDirectoryProvider: () -> String = {
        "${getAppDataDirectory()}/downloads"
    },
    private val nowEpochMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : DownloadTaskScheduler {
    private val activeResources = mutableMapOf<String, PlaybackResource>()
    private val resumeDataByTaskId = mutableMapOf<String, NSData>()
    private val pausedTaskIds = mutableSetOf<String>()
    private val backgroundCompletionHandlers = mutableMapOf<String, () -> Unit>()
    private val delegate = IosDownloadSessionDelegate(this)
    private val sessionIdentifier = "io.github.julystar.musicapp.downloads"
    private val session: NSURLSession by lazy {
        val configuration =
            NSURLSessionConfiguration.backgroundSessionConfigurationWithIdentifier(sessionIdentifier)
        configuration.sessionSendsLaunchEvents = true
        configuration.allowsCellularAccess = true
        NSURLSession.sessionWithConfiguration(
            configuration = configuration,
            delegate = delegate,
            delegateQueue = NSOperationQueue.mainQueue,
        )
    }

    override suspend fun schedule(task: DownloadTask) {
        session.getTasksWithCompletionHandler { _, _, downloadTasks ->
            downloadTasks
                ?.filterIsInstance<NSURLSessionDownloadTask>()
                ?.filter { it.taskDescription == task.id.value }
                ?.forEach { it.cancel() }
        }
        scope.launch {
            runTask(task)
        }
    }

    override suspend fun pause(id: DownloadTaskId) {
        pausedTaskIds += id.value
        suspendCancellableCoroutine { continuation ->
            session.getTasksWithCompletionHandler { _, _, downloadTasks ->
                val matchingTasks = downloadTasks
                    ?.filterIsInstance<NSURLSessionDownloadTask>()
                    ?.filter { it.taskDescription == id.value }
                    .orEmpty()
                val task = matchingTasks.firstOrNull()
                matchingTasks.drop(1).forEach { it.cancel() }
                if (task == null) {
                    if (continuation.isActive) continuation.resume(Unit)
                } else {
                    task.cancelByProducingResumeData { resumeData ->
                        if (resumeData != null) {
                            resumeDataByTaskId[id.value] = resumeData
                        }
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
            }
        }
    }

    override suspend fun cancel(id: DownloadTaskId) {
        pausedTaskIds -= id.value
        resumeDataByTaskId.remove(id.value)
        cancelSessionTask(id)
    }

    fun setBackgroundCompletionHandler(
        identifier: String,
        completionHandler: () -> Unit,
    ) {
        backgroundCompletionHandlers[identifier] = completionHandler
    }

    private suspend fun runTask(task: DownloadTask) {
        if (task.status == DownloadStatus.Finalizing && task.localPath != null) {
            finalizeDownloadedFile(task)
            return
        }
        if (updateStatus(task.id, DownloadStatus.Resolving) == null) return

        pausedTaskIds -= task.id.value
        val resumeData = resumeDataByTaskId.remove(task.id.value)
        if (resumeData == null) {
            activeResources.remove(task.id.value)?.let { staleResource ->
                legacyStoragePlaybackResolver.release(staleResource.uri)
            }
        }
        val source = sourceRegistry.sourceOrNull(task.mediaId.sourceId)
        if (source == null) {
            markFailed(task.id, "Music source is unavailable")
            return
        }

        val resource = if (resumeData != null) {
            activeResources[task.id.value]
        } else {
            when (val result = source.resolvePlayback(task.mediaId)) {
                is SourcePlaybackResult.Success -> result.resource
                is SourcePlaybackResult.Failure -> {
                    markFailed(task.id, "Unable to resolve download resource: ${result.reason}")
                    return
                }
            }
        }
        if (resource == null) {
            markFailed(task.id, "Paused download session is no longer available")
            return
        }

        try {
            val url = NSURL.URLWithString(resource.uri)
            if (url == null) {
                markFailed(task.id, "Download resource URL is invalid")
                legacyStoragePlaybackResolver.release(resource.uri)
                return
            }

            updateStatus(task.id, DownloadStatus.Downloading) { current ->
                current.copy(mimeType = resource.mimeType ?: current.mimeType)
            } ?: return

            activeResources[task.id.value] = resource
            val downloadTask = if (resumeData != null) {
                session.downloadTaskWithResumeData(resumeData)
            } else {
                val request = NSMutableURLRequest.requestWithURL(url)
                resource.headers.forEach { (key, value) ->
                    request.setValue(value, forHTTPHeaderField = key)
                }
                session.downloadTaskWithRequest(request)
            }
            downloadTask.apply {
                taskDescription = task.id.value
                resume()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            activeResources.remove(task.id.value)?.let { resource ->
                legacyStoragePlaybackResolver.release(resource.uri)
            }
            markFailed(task.id, error.message ?: "Download failed")
        }
    }

    private fun cancelSessionTask(id: DownloadTaskId) {
        session.getTasksWithCompletionHandler { _, _, downloadTasks ->
            downloadTasks
                ?.filterIsInstance<NSURLSessionDownloadTask>()
                ?.filter { it.taskDescription == id.value }
                ?.forEach { it.cancel() }
        }
    }

    private fun handleProgress(
        id: DownloadTaskId,
        totalBytesWritten: Long,
        totalBytesExpectedToWrite: Long,
    ) {
        scope.launch {
            val current = repository.getTask(id) ?: return@launch
            if (current.status != DownloadStatus.Downloading) return@launch
            repository.updateTask(
                current.copy(
                    downloadedBytes = totalBytesWritten,
                    totalBytes = totalBytesExpectedToWrite
                        .takeIf { it >= 0 }
                        ?.let { max(it, totalBytesWritten) },
                    updatedAtEpochMs = nowEpochMs(),
                )
            )
        }
    }

    private fun handleFinished(
        id: DownloadTaskId,
        location: NSURL,
    ) {
        scope.launch {
            val current = repository.getTask(id) ?: return@launch
            if (current.status != DownloadStatus.Downloading) return@launch

            val resource = activeResources[id.value]
            val targetPath = targetPathFor(current, resource)
            val fileManager = NSFileManager.defaultManager
            fileManager.createDirectoryAtPath(
                path = targetPath.substringBeforeLast('/'),
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
            if (!commitDownloadedFile(fileManager, location, targetPath)) {
                markFailed(id, "Unable to commit downloaded file")
                return@launch
            }

            val totalBytes = current.totalBytes
                ?.let { max(it, current.downloadedBytes) }
                ?: current.downloadedBytes
            val finalizing = updateStatus(id, DownloadStatus.Finalizing) { task ->
                task.copy(
                    totalBytes = totalBytes,
                    localPath = targetPath,
                    mimeType = resource?.mimeType ?: task.mimeType,
                    errorMessage = null,
                    finalizationWarning = null,
                )
            } ?: return@launch
            finalizeDownloadedFile(finalizing)
        }
    }

    private fun commitDownloadedFile(
        fileManager: NSFileManager,
        location: NSURL,
        targetPath: String,
    ): Boolean {
        val sourcePath = location.path ?: return false
        val targetDirectory = targetPath.substringBeforeLast('/')
        if (rename(sourcePath, targetPath) == 0) {
            syncPath(targetPath)
            syncPath(targetDirectory)
            return true
        }

        val temporaryPath = "$targetPath.commit.tmp"
        val temporaryUrl = NSURL.fileURLWithPath(temporaryPath)
        fileManager.removeItemAtURL(temporaryUrl, error = null)
        if (!fileManager.copyItemAtURL(location, temporaryUrl, error = null)) return false
        if (!syncPath(temporaryPath)) return false
        if (rename(temporaryPath, targetPath) != 0) return false
        syncPath(targetDirectory)
        fileManager.removeItemAtURL(location, error = null)
        return fileManager.fileExistsAtPath(targetPath)
    }

    private fun syncPath(path: String): Boolean {
        val descriptor = open(path, O_RDONLY)
        if (descriptor < 0) return false
        val synced = fsync(descriptor) == 0
        close(descriptor)
        return synced
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

    private fun handleCompleted(
        id: DownloadTaskId,
        error: NSError?,
    ) {
        scope.launch {
            val current = repository.getTask(id)
            if (current?.status == DownloadStatus.Paused || id.value in pausedTaskIds) {
                return@launch
            }
            val resource = activeResources.remove(id.value)
            if (error != null) {
                if (
                    current?.status != DownloadStatus.Paused &&
                    current?.status != DownloadStatus.Cancelled
                ) {
                    markFailed(id, error.localizedDescription)
                }
            }
            if (resource != null) {
                legacyStoragePlaybackResolver.release(resource.uri)
            }
        }
    }

    private fun handleEventsFinished(session: NSURLSession) {
        val completionHandler = backgroundCompletionHandlers.remove(session.configuration.identifier)
        completionHandler?.invoke()
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

    private fun targetPathFor(
        task: DownloadTask,
        resource: PlaybackResource?,
    ): String {
        return "${downloadDirectoryProvider()}/" +
            "${safeFileName(task.id.value)}${extensionFor(task, resource)}"
    }

    private class IosDownloadSessionDelegate(
        private val scheduler: IosUrlSessionDownloadScheduler,
    ) : NSObject(),
        NSURLSessionDownloadDelegateProtocol,
        NSURLSessionTaskDelegateProtocol {
        override fun URLSession(
            session: NSURLSession,
            downloadTask: NSURLSessionDownloadTask,
            didWriteData: Long,
            totalBytesWritten: Long,
            totalBytesExpectedToWrite: Long,
        ) {
            val id = downloadTask.taskDescription?.let(::DownloadTaskId) ?: return
            scheduler.handleProgress(id, totalBytesWritten, totalBytesExpectedToWrite)
        }

        override fun URLSession(
            session: NSURLSession,
            downloadTask: NSURLSessionDownloadTask,
            didFinishDownloadingToURL: NSURL,
        ) {
            val id = downloadTask.taskDescription?.let(::DownloadTaskId) ?: return
            scheduler.handleFinished(id, didFinishDownloadingToURL)
        }

        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            didCompleteWithError: NSError?,
        ) {
            val id = task.taskDescription?.let(::DownloadTaskId) ?: return
            scheduler.handleCompleted(id, didCompleteWithError)
        }

        override fun URLSessionDidFinishEventsForBackgroundURLSession(
            session: NSURLSession,
        ) {
            scheduler.handleEventsFinished(session)
        }
    }
}

private fun List<String>.toWarningMessage(): String? =
    joinToString("; ").takeIf(String::isNotBlank)?.take(1_000)

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
    resource: PlaybackResource?,
): String {
    val fromPath = sequenceOf(
        task.mediaId.remoteId,
        task.title,
        resource?.uri?.substringBefore('?').orEmpty(),
    )
        .mapNotNull(::extensionFromPath)
        .firstOrNull()
    return fromPath ?: extensionFromMimeType(resource?.mimeType) ?: ".audio"
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
