package io.github.julystar.musicapp.domain.importing

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import io.github.julystar.musicapp.core.domain.model.toOptions
import io.github.julystar.musicapp.database.MetadataRefreshCandidate
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.platform.currentTimeMillis
import io.github.julystar.musicapp.metadata.UnifiedMetadataRepository
import io.github.julystar.musicapp.service.librarysync.domain.MetadataRefreshController
import io.github.julystar.musicapp.service.librarysync.domain.MetadataRefreshRequest
import io.github.julystar.musicapp.service.librarysync.domain.MetadataRefreshResult
import io.github.julystar.musicapp.service.librarysync.domain.MetadataRefreshScope
import io.github.julystar.musicapp.source.storage.RemoteMetadataReader
import uniffi.app_backend.RemoteMetadata
import uniffi.app_backend.StorageEntry
import uniffi.app_backend.StorageId

class RemoteMetadataRefreshController(
    private val database: AppDatabase,
    private val metadataRepository: RemoteMetadataReader,
    private val unifiedMetadataRepository: UnifiedMetadataRepository? = null,
) : MetadataRefreshController {
    override suspend fun refresh(request: MetadataRefreshRequest): MetadataRefreshResult {
        val selectedCandidates = candidates(request)
            .distinctBy(MetadataRefreshCandidate::trackId)
        val candidates = if (request.allowNetwork) {
            selectedCandidates
        } else {
            selectedCandidates.filter { candidate ->
                database.sourceAccountDao().get(candidate.storageId)?.providerType == ProviderTypes.Local
            }
        }
        if (candidates.isEmpty()) return EMPTY_METADATA_REFRESH_RESULT

        val refreshed = mutableListOf<RefreshedMetadata>()
        var failedCount = 0L
        candidates.groupBy(MetadataRefreshCandidate::storageId).values.forEach { storageCandidates ->
            val entries = storageCandidates.map(MetadataRefreshCandidate::toStorageEntry)
            val results = metadataRepository.readBatch(
                entries = entries,
                concurrency = request.metadataConcurrency,
                options = request.target.toOptions(),
            )
            val resultsByPath = results.associateBy { it.entry.path }
            storageCandidates.forEach { candidate ->
                val metadata = resultsByPath[candidate.path]?.metadata
                if (metadata == null) {
                    failedCount++
                } else {
                    refreshed += RefreshedMetadata(candidate, metadata)
                }
            }
        }

        persist(request, refreshed)
        val metadata = refreshed.map(RefreshedMetadata::metadata)
        return MetadataRefreshResult(
            requestedCount = candidates.size.toLong(),
            refreshedCount = refreshed.size.toLong(),
            failedCount = failedCount,
            metadataRequestCount = metadata.sumMetric(RemoteMetadata::metadataRequestCount),
            metadataFetchedBytes = metadata.sumMetric(RemoteMetadata::metadataFetchedBytes),
            metadataElapsedMs = metadata.sumMetric(RemoteMetadata::metadataElapsedMs),
            artworkCachedBytes = metadata.sumMetric(RemoteMetadata::artworkCachedBytes),
        )
    }

    private suspend fun candidates(request: MetadataRefreshRequest): List<MetadataRefreshCandidate> {
        return when (val scope = request.scope) {
            is MetadataRefreshScope.Track -> listOfNotNull(
                database.trackSourceRefDao().metadataResetCandidateForTrack(scope.trackId)
            )
            is MetadataRefreshScope.Album -> database.trackSourceRefDao()
                .webDavMetadataCandidatesForAlbum(scope.albumId)
            MetadataRefreshScope.MissingWebDavTracks -> database.trackSourceRefDao()
                .missingWebDavMetadataCandidates(request.target.name)
        }
    }

    private suspend fun persist(
        request: MetadataRefreshRequest,
        refreshed: List<RefreshedMetadata>,
    ) {
        if (refreshed.isEmpty()) return
        val options = request.target.toOptions()
        val now = currentTimeMillis()
        val resolvedAlbumIds = mutableMapOf<Long, Long?>()
        if (request.target == io.github.julystar.musicapp.core.domain.model.MetadataRefreshTarget.All) {
            refreshed.forEach { value ->
                val providerType = database.sourceAccountDao()
                    .get(value.candidate.storageId)
                    ?.providerType
                when {
                    unifiedMetadataRepository == null -> Unit
                    providerType == ProviderTypes.Local -> {
                        unifiedMetadataRepository.fillMissingFileMetadata(value.candidate.trackId, value.metadata)
                        resolvedAlbumIds[value.candidate.trackId] = database.trackDao().get(value.candidate.trackId)?.albumId
                    }
                    providerType != null -> {
                        unifiedMetadataRepository.fillMissingServerMetadata(value.candidate.trackId, value.metadata)
                        resolvedAlbumIds[value.candidate.trackId] = database.trackDao().get(value.candidate.trackId)?.albumId
                    }
                }
            }
        }
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                database.metadataDao().updateOptionalMetadata(
                    updates = refreshed.map { value ->
                        OptionalMetadataUpdate(
                            trackId = value.candidate.trackId,
                            albumId = resolvedAlbumIds[value.candidate.trackId] ?: value.candidate.albumId,
                            metadata = value.metadata,
                        )
                    },
                    options = options,
                    now = now,
                )
                refreshed.forEach { value ->
                    database.trackSourceRefDao().updateEmbeddedMetadataPresence(
                        sourceItemId = value.candidate.sourceItemId,
                        hasEmbeddedArtwork = value.metadata.hasEmbeddedArtwork,
                        embeddedLyricsKind = value.metadata.embeddedLyricsKind,
                        now = now,
                    )
                }
            }
        }
    }
}

private data class RefreshedMetadata(
    val candidate: MetadataRefreshCandidate,
    val metadata: RemoteMetadata,
)

internal fun MetadataRefreshCandidate.toStorageEntry(): StorageEntry {
    return StorageEntry(
        storageId = StorageId(storageId),
        name = name,
        path = path,
        size = sizeBytes.takeIf { it > 0 }?.toULong(),
        isDir = false,
        remoteId = remoteId,
        parentRemoteId = parentRemoteId,
        mimeType = mimeType,
        etag = etag,
        ctag = null,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
    )
}

private inline fun Iterable<RemoteMetadata>.sumMetric(metric: (RemoteMetadata) -> ULong): Long {
    return fold(0L) { total, metadata ->
        val value = metric(metadata).takeIf { it <= Long.MAX_VALUE.toULong() }?.toLong()
            ?: Long.MAX_VALUE
        if (value > 0 && total > Long.MAX_VALUE - value) Long.MAX_VALUE else total + value
    }
}

private val EMPTY_METADATA_REFRESH_RESULT = MetadataRefreshResult(
    requestedCount = 0,
    refreshedCount = 0,
    failedCount = 0,
    metadataRequestCount = 0,
    metadataFetchedBytes = 0,
    metadataElapsedMs = 0,
    artworkCachedBytes = 0,
)
