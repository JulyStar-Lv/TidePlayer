package io.github.julystar.musicapp.feature.downloads.presentation

import androidx.compose.runtime.Immutable
import io.github.julystar.musicapp.service.download.domain.DownloadStatus
import io.github.julystar.musicapp.service.download.domain.DownloadTask
import io.github.julystar.musicapp.service.download.domain.DownloadTaskId
import io.github.julystar.musicapp.service.download.domain.canTransitionTo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.math.roundToInt

@Immutable
data class DownloadsState(
    val tasks: ImmutableList<DownloadTaskUi> = persistentListOf(),
) {
    val activeCount: Int
        get() = tasks.count { task -> task.isActive }
}

@Immutable
data class DownloadTaskUi(
    val id: DownloadTaskId,
    val title: String,
    val subtitle: String,
    val status: DownloadStatus,
    val progressPercent: Int?,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val progressFraction: Float?,
    val errorMessage: String?,
    val warningMessage: String?,
    val canPause: Boolean,
    val canResume: Boolean,
    val canRetry: Boolean,
    val canCancel: Boolean,
    val isActive: Boolean,
)

sealed interface DownloadsAction {
    data class Pause(val id: DownloadTaskId) : DownloadsAction
    data class Resume(val id: DownloadTaskId) : DownloadsAction
    data class Retry(val id: DownloadTaskId) : DownloadsAction
    data class Cancel(val id: DownloadTaskId) : DownloadsAction
}

sealed interface DownloadsEvent {
    data class ShowMessage(val message: String) : DownloadsEvent
}

internal fun DownloadTask.toDownloadTaskUi(): DownloadTaskUi {
    val progressPercent = progressPercent()
    return DownloadTaskUi(
        id = id,
        title = title,
        subtitle = listOfNotNull(artist, album).joinToString(" - ").ifBlank { mediaId.sourceId.value },
        status = status,
        progressPercent = progressPercent,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        progressFraction = when {
            status == DownloadStatus.Completed -> 1f
            else -> totalBytes?.let { total ->
                if (total > 0L) {
                    (downloadedBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                } else null
            }
        },
        errorMessage = errorMessage?.takeIf { it.isNotBlank() },
        warningMessage = finalizationWarning?.takeIf { it.isNotBlank() },
        canPause = status.canTransitionTo(DownloadStatus.Paused),
        canResume = status == DownloadStatus.Paused && status.canTransitionTo(DownloadStatus.Queued),
        canRetry = status == DownloadStatus.Failed && status.canTransitionTo(DownloadStatus.Queued),
        canCancel = status.canTransitionTo(DownloadStatus.Cancelled),
        isActive = status in setOf(
            DownloadStatus.Queued,
            DownloadStatus.Resolving,
            DownloadStatus.Downloading,
            DownloadStatus.Finalizing,
            DownloadStatus.Paused,
        ),
    )
}

private fun DownloadTask.progressPercent(): Int? {
    val total = totalBytes ?: return null
    if (total <= 0L) return null
    return ((downloadedBytes.toDouble() / total.toDouble()) * 100)
        .roundToInt()
        .coerceIn(0, 100)
}

internal fun formatByteCount(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    val rounded = (value * 10).roundToInt() / 10.0
    val text = if (rounded % 1.0 == 0.0) {
        rounded.toInt().toString()
    } else {
        rounded.toString()
    }
    return "$text ${units[unitIndex]}"
}
