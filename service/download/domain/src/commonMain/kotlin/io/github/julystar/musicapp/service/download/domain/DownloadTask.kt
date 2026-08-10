package io.github.julystar.musicapp.service.download.domain

import io.github.julystar.musicapp.core.domain.model.MediaId
import kotlin.jvm.JvmInline

@JvmInline
value class DownloadTaskId(val value: String) {
    init {
        require(value.isNotBlank()) { "DownloadTaskId cannot be blank" }
    }
}

enum class DownloadStatus {
    Queued,
    Resolving,
    Downloading,
    Finalizing,
    Paused,
    Completed,
    Failed,
    Cancelled,
}

data class DownloadTask(
    val id: DownloadTaskId,
    val mediaId: MediaId,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val status: DownloadStatus = DownloadStatus.Queued,
    val downloadedBytes: Long = 0,
    val totalBytes: Long? = null,
    val localPath: String? = null,
    val mimeType: String? = null,
    val errorMessage: String? = null,
    val finalizationWarning: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    init {
        require(title.isNotBlank()) { "DownloadTask title cannot be blank" }
        require(downloadedBytes >= 0) { "DownloadTask downloadedBytes cannot be negative" }
        require(totalBytes == null || totalBytes >= 0) { "DownloadTask totalBytes cannot be negative" }
        require(totalBytes == null || downloadedBytes <= totalBytes) {
            "DownloadTask downloadedBytes cannot exceed totalBytes"
        }
    }
}

fun DownloadStatus.canTransitionTo(next: DownloadStatus): Boolean {
    if (this == next) return true
    return when (this) {
        DownloadStatus.Queued -> next in setOf(
            DownloadStatus.Resolving,
            DownloadStatus.Cancelled,
        )
        DownloadStatus.Resolving -> next in setOf(
            DownloadStatus.Downloading,
            DownloadStatus.Paused,
            DownloadStatus.Failed,
            DownloadStatus.Cancelled,
        )
        DownloadStatus.Downloading -> next in setOf(
            DownloadStatus.Paused,
            DownloadStatus.Finalizing,
            DownloadStatus.Failed,
            DownloadStatus.Cancelled,
        )
        DownloadStatus.Finalizing -> next in setOf(
            DownloadStatus.Completed,
            DownloadStatus.Failed,
            DownloadStatus.Cancelled,
        )
        DownloadStatus.Paused -> next in setOf(
            DownloadStatus.Queued,
            DownloadStatus.Resolving,
            DownloadStatus.Cancelled,
        )
        DownloadStatus.Failed -> next in setOf(
            DownloadStatus.Queued,
            DownloadStatus.Cancelled,
        )
        DownloadStatus.Completed,
        DownloadStatus.Cancelled -> false
    }
}

data class DownloadPolicy(
    val allowMeteredNetwork: Boolean = true,
    val allowRoaming: Boolean = false,
    val maxConcurrentTasks: Int = 2,
) {
    init {
        require(maxConcurrentTasks > 0) { "DownloadPolicy maxConcurrentTasks must be positive" }
    }
}
