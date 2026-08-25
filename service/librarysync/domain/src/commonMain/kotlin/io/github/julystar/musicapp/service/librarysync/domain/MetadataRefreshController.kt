package io.github.julystar.musicapp.service.librarysync.domain

import io.github.julystar.musicapp.core.domain.model.MetadataRefreshTarget

interface MetadataRefreshController {
    suspend fun refresh(request: MetadataRefreshRequest): MetadataRefreshResult
}

data class MetadataRefreshRequest(
    val scope: MetadataRefreshScope,
    val target: MetadataRefreshTarget,
    val metadataConcurrency: UInt = DEFAULT_LIBRARY_SYNC_METADATA_CONCURRENCY,
    val allowNetwork: Boolean = true,
) {
    init {
        require(metadataConcurrency in 1u..MAX_LIBRARY_SYNC_METADATA_CONCURRENCY) {
            "metadata concurrency must be between 1 and $MAX_LIBRARY_SYNC_METADATA_CONCURRENCY"
        }
    }
}

sealed interface MetadataRefreshScope {
    data class Track(val trackId: Long) : MetadataRefreshScope
    data class Album(val albumId: Long) : MetadataRefreshScope
    data object MissingWebDavTracks : MetadataRefreshScope
}

data class MetadataRefreshResult(
    val requestedCount: Long,
    val refreshedCount: Long,
    val failedCount: Long,
    val metadataRequestCount: Long,
    val metadataFetchedBytes: Long,
    val metadataElapsedMs: Long,
    val artworkCachedBytes: Long,
)
