package io.github.julystar.musicapp.domain.importing

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import io.github.julystar.musicapp.database.AlbumArtistCrossRef
import io.github.julystar.musicapp.database.AlbumEntity
import io.github.julystar.musicapp.database.ArtworkEntity
import io.github.julystar.musicapp.database.ArtistEntity
import io.github.julystar.musicapp.database.GenreEntity
import io.github.julystar.musicapp.database.ImportJobEntity
import io.github.julystar.musicapp.database.LibraryRootEntity
import io.github.julystar.musicapp.database.LyricsEntity
import io.github.julystar.musicapp.database.MetadataDao
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.RawMetadataEntity
import io.github.julystar.musicapp.database.SourceAccountEntity
import io.github.julystar.musicapp.database.SourceErrorEntity
import io.github.julystar.musicapp.database.SourceItemEntity
import io.github.julystar.musicapp.database.SourceItemSignature
import io.github.julystar.musicapp.database.SourceItemTypes
import io.github.julystar.musicapp.database.SourceSyncCursorEntity
import io.github.julystar.musicapp.database.SyncDao
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.TrackDao
import io.github.julystar.musicapp.database.TrackArtistCrossRef
import io.github.julystar.musicapp.database.TrackEntity
import io.github.julystar.musicapp.database.TrackGenreCrossRef
import io.github.julystar.musicapp.database.TrackMetadataSources
import io.github.julystar.musicapp.database.TrackSourceRefEntity
import io.github.julystar.musicapp.core.data.media.PluginArtworkResolver
import io.github.julystar.musicapp.platform.currentTimeMillis
import io.github.julystar.musicapp.core.domain.model.MetadataScanMode
import io.github.julystar.musicapp.core.domain.model.MetadataParsingSettings
import io.github.julystar.musicapp.core.domain.model.toOptions
import io.github.julystar.musicapp.core.domain.model.MissingFilePolicy
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncScanRules
import io.github.julystar.musicapp.source.storage.RemoteMetadataReader
import io.github.julystar.musicapp.source.storage.RemoteScannerRepository
import io.github.julystar.musicapp.core.data.StorageRepositoryImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uniffi.app_backend.RemoteArtwork
import uniffi.app_backend.RemoteMetadata
import uniffi.app_backend.RemoteMetadataResult
import uniffi.app_backend.RemoteMusicScanSession
import uniffi.app_backend.OneDriveDeltaItem
import uniffi.app_backend.OneDriveDeltaPageResult
import uniffi.app_backend.StorageEntry
import uniffi.app_backend.StorageId
import uniffi.app_backend.WebDavSyncItem
import uniffi.app_backend.WebDavSyncPage
import uniffi.app_backend.WebDavSyncPageResult

data class RemoteLibraryImportRequest(
    val storageId: Long,
    val selectedFolderRemoteId: String?,
    val selectedFolderCanonicalPath: String,
    val selectedFolderDisplayPath: String? = null,
    val entries: List<StorageEntry>,
    val scanRules: LibrarySyncScanRules = LibrarySyncScanRules(),
    val metadataScanMode: MetadataScanMode = MetadataScanMode.Full,
    val scanId: String? = null,
    val metadataConcurrency: UInt = DEFAULT_METADATA_CONCURRENCY,
    val importBatchSize: Int = DEFAULT_IMPORT_BATCH_SIZE,
    val syncMode: String = SYNC_MODE_LEGACY_FULL_SCAN_FALLBACK,
    val capabilityDetectionElapsedMs: Long = 0,
    val directoryScanElapsedMs: Long = 0,
)

data class RemoteLibraryImportResult(
    val scanId: String,
    val selectedFolderId: Long,
    val scannedCount: Long,
    val changedCount: Long,
    val skippedCount: Long,
    val importedCount: Long,
    val failedCount: Long,
    val metadataRequestCount: Long = 0,
    val metadataFetchedBytes: Long = 0,
    val metadataElapsedMs: Long = 0,
    val artworkCachedBytes: Long = 0,
    val syncMode: String = SYNC_MODE_LEGACY_FULL_SCAN_FALLBACK,
    val directoryConcurrency: Int = DEFAULT_DIRECTORY_CONCURRENCY,
    val capabilityDetectionElapsedMs: Long = 0,
    val directoryScanElapsedMs: Long = 0,
    val directoryRequestCount: Long = 0,
    val listedDirectoryCount: Long = 0,
    val visitedEntryCount: Long = 0,
    val discoveredMusicCount: Long = 0,
    val unchangedCount: Long = 0,
    val addedCount: Long = 0,
    val modifiedCount: Long = 0,
    val renamedCount: Long = 0,
    val deletedCount: Long = 0,
    val databaseReadElapsedMs: Long = 0,
    val databaseWriteElapsedMs: Long = 0,
    val totalElapsedMs: Long = 0,
)

class RemoteLibraryImportCoordinator(
    private val database: AppDatabase,
    private val trackDao: TrackDao,
    private val metadataDao: MetadataDao,
    private val syncDao: SyncDao,
    private val metadataRepository: RemoteMetadataReader,
    private val remoteScannerRepository: RemoteScannerRepository,
    private val storageRepository: StorageRepositoryImpl,
    private val settingsRepository: SettingsRepository? = null,
    private val pluginArtworkResolver: PluginArtworkResolver? = null,
) {
    private val activeOperationsMutex = Mutex()
    private val activeOperations = mutableMapOf<String, ActiveImportOperation>()

    suspend fun cancelImport(scanId: String): Boolean {
        val operation = activeOperationsMutex.withLock { activeOperations[scanId] }
        operation?.cancel()
        return operation != null
    }

    suspend fun pauseImport(scanId: String): Boolean {
        val operation = activeOperationsMutex.withLock { activeOperations[scanId] }
        operation?.pause()
        return operation != null
    }

    suspend fun scanAndInitializeOneDriveFolder(
        storageId: Long,
        selectedFolderRemoteId: String,
        selectedFolderCanonicalPath: String,
        selectedFolderDisplayPath: String? = null,
        scanId: String? = null,
        scanRules: LibrarySyncScanRules = LibrarySyncScanRules(),
        metadataScanMode: MetadataScanMode = MetadataScanMode.Full,
        metadataConcurrency: UInt = DEFAULT_METADATA_CONCURRENCY,
        importBatchSize: Int = DEFAULT_IMPORT_BATCH_SIZE,
    ): RemoteLibraryImportResult {
        validateImportSettings(metadataConcurrency, importBatchSize)
        val request = RemoteLibraryImportRequest(
            storageId = storageId,
            selectedFolderRemoteId = selectedFolderRemoteId,
            selectedFolderCanonicalPath = selectedFolderCanonicalPath,
            selectedFolderDisplayPath = selectedFolderDisplayPath,
            entries = emptyList(),
            scanRules = scanRules,
            metadataScanMode = metadataScanMode,
            scanId = scanId,
            metadataConcurrency = metadataConcurrency,
            importBatchSize = importBatchSize,
        )
        val (execution, operation) = startTrackedImport(request)
        var currentJob = execution.job

        return try {
            operation.throwIfStopRequested()
            val delta = readOneDriveDelta(
                storageId = storageId,
                rootRemoteId = selectedFolderRemoteId,
                cursor = null,
                latestOnly = false,
                operation = operation,
            )
            check(!delta.resyncRequired && delta.deltaLink != null) {
                "OneDrive did not return a complete initial delta snapshot"
            }
            check(!requiresOneDriveResync(delta.items)) {
                "OneDrive initial delta contained a file without a canonical path"
            }
            val snapshotRequest = request.copy(
                entries = delta.items
                    .asSequence()
                    .filter { !it.deleted && it.isSupportedMusicFile() }
                    .map { it.toStorageEntry(storageId) }
                    .filter { it.isAllowedByScanRules(request.selectedFolderCanonicalPath, request.scanRules) }
                    .toList(),
            )
            val result = runCompleteSnapshotImport(
                request = snapshotRequest,
                deltaLink = delta.deltaLink,
                execution = execution,
                operation = operation,
                currentJob = currentJob,
            )
            result.second.also { currentJob = it }
            result.first
        } catch (error: Throwable) {
            markImportStopOrFailure(
                error = error,
                operation = operation,
                root = execution.libraryRoot,
                job = currentJob,
            )
            throw error
        } finally {
            unregisterActiveOperation(execution, operation)
        }
    }

    suspend fun syncOneDriveFolder(
        storageId: Long,
        selectedFolderRemoteId: String,
        selectedFolderCanonicalPath: String,
        selectedFolderDisplayPath: String? = null,
        scanId: String? = null,
        scanRules: LibrarySyncScanRules = LibrarySyncScanRules(),
        metadataScanMode: MetadataScanMode = MetadataScanMode.Full,
        metadataConcurrency: UInt = DEFAULT_METADATA_CONCURRENCY,
        importBatchSize: Int = DEFAULT_IMPORT_BATCH_SIZE,
    ): RemoteLibraryImportResult {
        validateImportSettings(metadataConcurrency, importBatchSize)
        val canonicalPath = normalizeRemotePath(selectedFolderCanonicalPath)
        val root = database.libraryRootDao().findByPath(storageId, canonicalPath)
            ?: return scanAndInitializeOneDriveFolder(
                storageId = storageId,
                selectedFolderRemoteId = selectedFolderRemoteId,
                selectedFolderCanonicalPath = canonicalPath,
                selectedFolderDisplayPath = selectedFolderDisplayPath,
                scanId = scanId,
                scanRules = scanRules,
                metadataScanMode = metadataScanMode,
                metadataConcurrency = metadataConcurrency,
                importBatchSize = importBatchSize,
            )
        val cursor = syncDao.getCursor(root.id)?.cursorValue ?: root.syncCursor
            ?: return scanAndInitializeOneDriveFolder(
                storageId = storageId,
                selectedFolderRemoteId = selectedFolderRemoteId,
                selectedFolderCanonicalPath = canonicalPath,
                selectedFolderDisplayPath = selectedFolderDisplayPath,
                scanId = scanId,
                scanRules = scanRules,
                metadataScanMode = metadataScanMode,
                metadataConcurrency = metadataConcurrency,
                importBatchSize = importBatchSize,
            )
        val request = RemoteLibraryImportRequest(
            storageId = storageId,
            selectedFolderRemoteId = selectedFolderRemoteId,
            selectedFolderCanonicalPath = canonicalPath,
            selectedFolderDisplayPath = selectedFolderDisplayPath,
            entries = emptyList(),
            scanRules = scanRules,
            metadataScanMode = metadataScanMode,
            scanId = scanId,
            metadataConcurrency = metadataConcurrency,
            importBatchSize = importBatchSize,
        )
        val (execution, operation) = startTrackedImport(request)
        var currentJob = execution.job
        var changedCount = 0L
        return try {
            operation.throwIfStopRequested()
            val delta = readOneDriveDelta(
                storageId = storageId,
                rootRemoteId = selectedFolderRemoteId,
                cursor = cursor,
                latestOnly = false,
                operation = operation,
            )
            if (delta.resyncRequired || requiresOneDriveResync(delta.items)) {
                val snapshot = readOneDriveDelta(
                    storageId = storageId,
                    rootRemoteId = selectedFolderRemoteId,
                    cursor = null,
                    latestOnly = false,
                    operation = operation,
                )
                check(!snapshot.resyncRequired && snapshot.deltaLink != null) {
                    "OneDrive did not return a complete resync delta snapshot"
                }
                check(!requiresOneDriveResync(snapshot.items)) {
                    "OneDrive resync delta contained a file without a canonical path"
                }
                val snapshotRequest = request.copy(
                    entries = snapshot.items
                        .asSequence()
                        .filter { !it.deleted && it.isSupportedMusicFile() }
                        .map { it.toStorageEntry(storageId) }
                        .filter { it.isAllowedByScanRules(request.selectedFolderCanonicalPath, request.scanRules) }
                        .toList(),
                )
                val result = runCompleteSnapshotImport(
                    request = snapshotRequest,
                    deltaLink = snapshot.deltaLink,
                    execution = execution,
                    operation = operation,
                    currentJob = currentJob,
                )
                currentJob = result.second
                return result.first
            }
            val nextDeltaLink = requireNotNull(delta.deltaLink) {
                "OneDrive delta pagination completed without a deltaLink"
            }
            operation.throwIfStopRequested()
            val existingByRemoteId = delta.items
                .map { it.remoteId }
                .distinct()
                .chunked(MAX_REMOTE_ID_QUERY_SIZE)
                .flatMap { database.sourceItemDao().findByProviderItemIds(storageId, it) }
                .mapNotNull { item -> item.providerItemId?.let { it to item } }
                .toMap()
            operation.throwIfStopRequested()
            val deletedRemoteIds = delta.items.mapNotNull { item ->
                val existing = existingByRemoteId[item.remoteId]
                if (item.deleted || (existing != null && !item.isSupportedMusicFile())) {
                    item.remoteId
                } else {
                    null
                }
            }.distinct()
            if (deletedRemoteIds.isNotEmpty()) {
                val now = currentTimeMillis()
                val deletedCount = deletedRemoteIds
                    .chunked(MAX_REMOTE_ID_QUERY_SIZE)
                    .sumOf {
                        database.sourceItemDao().markDeletedByProviderItemIds(storageId, it, now)
                    }
                val deletedSourceItemIds = deletedRemoteIds
                    .chunked(MAX_REMOTE_ID_QUERY_SIZE)
                    .flatMap { ids ->
                        database.sourceItemDao()
                            .findByProviderItemIds(storageId, ids)
                    }
                    .map { it.id }
                if (deletedSourceItemIds.isNotEmpty()) {
                    database.trackSourceRefDao()
                        .markUnavailableBySourceItemIds(deletedSourceItemIds, now)
                }
                changedCount += deletedCount
                currentJob = currentJob.copy(
                    scannedCount = currentJob.scannedCount + deletedRemoteIds.size,
                    deletedCount = currentJob.deletedCount + deletedCount,
                    updatedAt = now,
                )
                syncDao.upsertJob(currentJob)
            }
            operation.throwIfStopRequested()

            val entries = delta.items
                .asSequence()
                .filter { !it.deleted && it.isSupportedMusicFile() }
                .map { it.toStorageEntry(storageId) }
                .filter { it.isAllowedByScanRules(request.selectedFolderCanonicalPath, request.scanRules) }
                .toList()
            currentJob = currentJob.copy(
                scannedCount = currentJob.scannedCount + entries.size,
                discoveredMusicCount = currentJob.discoveredMusicCount + entries.size,
                updatedAt = currentTimeMillis(),
            )
            syncDao.upsertJob(currentJob)

            entries.chunked(importBatchSize).forEach { batch ->
                operation.throwIfStopRequested()
                val batchResult = importBatch(
                    request = request,
                    execution = execution,
                    currentJob = currentJob,
                    entries = batch,
                )
                currentJob = batchResult.job
                changedCount += batchResult.changedCount
                operation.throwIfStopRequested()
            }
            operation.throwIfStopRequested()
            currentJob = completeDeltaImport(
                execution = execution,
                currentJob = currentJob,
                deltaLink = nextDeltaLink,
            )
            importResult(execution, currentJob, changedCount)
        } catch (error: Throwable) {
            markImportStopOrFailure(
                error = error,
                operation = operation,
                root = execution.libraryRoot,
                job = currentJob,
            )
            throw error
        } finally {
            unregisterActiveOperation(execution, operation)
        }
    }

    /**
     * Runs the WebDAV-only sync state machine. A cached token uses RFC 6578,
     * an initial empty-token REPORT creates a complete snapshot, and unsupported
     * or invalid server state falls back to the bounded parallel directory scan.
     * Tokens and capability markers are committed only by successful completion.
     */
    suspend fun syncWebDavFolder(
        storageId: Long,
        selectedFolderRemoteId: String?,
        selectedFolderCanonicalPath: String,
        selectedFolderDisplayPath: String? = null,
        scanId: String? = null,
        scanRules: LibrarySyncScanRules = LibrarySyncScanRules(),
        metadataScanMode: MetadataScanMode = MetadataScanMode.Standard,
        metadataConcurrency: UInt = DEFAULT_METADATA_CONCURRENCY,
        importBatchSize: Int = DEFAULT_IMPORT_BATCH_SIZE,
    ): RemoteLibraryImportResult {
        validateImportSettings(metadataConcurrency, importBatchSize)
        val canonicalPath = normalizeRemotePath(selectedFolderCanonicalPath)
        val existingRoot = database.libraryRootDao().findByPath(storageId, canonicalPath)
        val effectiveMetadataMode = webDavMetadataModeFor(
            hasExistingRoot = existingRoot != null,
            requestedMode = metadataScanMode,
        )
        val tokenCursor = existingRoot?.let {
            syncDao.getCursor(it.id, WEBDAV_SYNC_TOKEN_CURSOR_TYPE)
        }
        val capabilityCursor = existingRoot?.let {
            syncDao.getCursor(it.id, WEBDAV_CAPABILITY_CURSOR_TYPE)
        }
        val syncToken = tokenCursor?.cursorValue
        val request = RemoteLibraryImportRequest(
            storageId = storageId,
            selectedFolderRemoteId = selectedFolderRemoteId,
            selectedFolderCanonicalPath = canonicalPath,
            selectedFolderDisplayPath = selectedFolderDisplayPath,
            entries = emptyList(),
            scanRules = scanRules,
            metadataScanMode = effectiveMetadataMode,
            scanId = scanId,
            metadataConcurrency = metadataConcurrency,
            importBatchSize = importBatchSize,
        )

        if (capabilityCursor?.cursorValue == WEBDAV_CAPABILITY_UNSUPPORTED) {
            return runWebDavParallelSnapshot(
                request = request,
                capabilityValue = WEBDAV_CAPABILITY_UNSUPPORTED,
            )
        }

        if (syncToken != null) {
            val deltaStartedAt = currentTimeMillis()
            when (val delta = readWebDavSyncPage(storageId, canonicalPath, syncToken)) {
                is WebDavSyncPageResult.Page -> {
                    return runWebDavDeltaImport(
                        request.copy(
                            syncMode = SYNC_MODE_WEBDAV_SYNC_TOKEN,
                            directoryScanElapsedMs = (currentTimeMillis() - deltaStartedAt)
                                .coerceAtLeast(0),
                        ),
                        delta.v1,
                    )
                }
                WebDavSyncPageResult.Unsupported -> {
                    return runWebDavParallelSnapshot(
                        request = request,
                        capabilityValue = WEBDAV_CAPABILITY_UNSUPPORTED,
                    )
                }
                WebDavSyncPageResult.ResyncRequired,
                null -> Unit
            }
        }

        val capabilityStartedAt = currentTimeMillis()
        val snapshot = readWebDavSyncPage(storageId, canonicalPath, syncToken = null)
        val capabilityElapsedMs = (currentTimeMillis() - capabilityStartedAt).coerceAtLeast(0)
        val capabilityRequest = request.copy(
            syncMode = SYNC_MODE_WEBDAV_SYNC_TOKEN,
            capabilityDetectionElapsedMs = capabilityElapsedMs,
            directoryScanElapsedMs = capabilityElapsedMs,
        )
        return when (snapshot) {
            is WebDavSyncPageResult.Page -> runWebDavReportSnapshot(capabilityRequest, snapshot.v1)
            WebDavSyncPageResult.Unsupported -> runWebDavParallelSnapshot(
                request = capabilityRequest,
                capabilityValue = WEBDAV_CAPABILITY_UNSUPPORTED,
            )
            WebDavSyncPageResult.ResyncRequired,
            null -> runWebDavParallelSnapshot(
                request = capabilityRequest,
                capabilityValue = capabilityCursor?.cursorValue
                    ?.takeIf { it == WEBDAV_CAPABILITY_SUPPORTED },
            )
        }
    }

    private suspend fun readWebDavSyncPage(
        storageId: Long,
        rootPath: String,
        syncToken: String?,
    ): WebDavSyncPageResult? {
        return try {
            remoteScannerRepository.getWebDavSyncPage(
                storageId = StorageId(storageId),
                rootPath = rootPath,
                syncToken = syncToken,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun runWebDavReportSnapshot(
        request: RemoteLibraryImportRequest,
        page: WebDavSyncPage,
    ): RemoteLibraryImportResult {
        val snapshotRequest = request.copy(
            entries = page.items
                .asSequence()
                .filter { !it.deleted && !it.isDir }
                .map { it.toStorageEntry(request.storageId) }
                .filter(::isSupportedMusicEntry)
                .filter { it.isAllowedByScanRules(request.selectedFolderCanonicalPath, request.scanRules) }
                .toList(),
        )
        val (execution, operation) = startTrackedImport(snapshotRequest)
        var currentJob = execution.job.copy(
            directoryConcurrency = 1,
            directoryRequestCount = 1,
            visitedEntryCount = page.items.size.toLong(),
        )
        return try {
            val result = runCompleteSnapshotImport(
                request = snapshotRequest,
                deltaLink = page.syncToken,
                execution = execution,
                operation = operation,
                currentJob = currentJob,
                cursorType = WEBDAV_SYNC_TOKEN_CURSOR_TYPE,
                capabilityValue = WEBDAV_CAPABILITY_SUPPORTED,
            )
            currentJob = result.second
            result.first
        } catch (error: Throwable) {
            markImportStopOrFailure(error, operation, execution.libraryRoot, currentJob)
            throw error
        } finally {
            unregisterActiveOperation(execution, operation)
        }
    }

    private suspend fun runWebDavParallelSnapshot(
        request: RemoteLibraryImportRequest,
        capabilityValue: String?,
    ): RemoteLibraryImportResult {
        return scanAndImportFolder(
            storageId = request.storageId,
            selectedFolderRemoteId = request.selectedFolderRemoteId,
            selectedFolderCanonicalPath = request.selectedFolderCanonicalPath,
            selectedFolderDisplayPath = request.selectedFolderDisplayPath,
            scanId = request.scanId,
            scanRules = request.scanRules,
            metadataScanMode = request.metadataScanMode,
            metadataConcurrency = request.metadataConcurrency,
            importBatchSize = request.importBatchSize,
            cursorType = WEBDAV_SYNC_TOKEN_CURSOR_TYPE,
            persistCursor = false,
            clearRootCursor = true,
            capabilityValue = capabilityValue,
            syncMode = SYNC_MODE_PARALLEL_FULL_SCAN,
            capabilityDetectionElapsedMs = request.capabilityDetectionElapsedMs,
        )
    }

    private suspend fun runWebDavDeltaImport(
        request: RemoteLibraryImportRequest,
        page: WebDavSyncPage,
    ): RemoteLibraryImportResult {
        val (execution, operation) = startTrackedImport(request)
        var currentJob = execution.job.copy(
            directoryConcurrency = 1,
            directoryRequestCount = 1,
            visitedEntryCount = page.items.size.toLong(),
        )
        var changedCount = 0L
        return try {
            operation.throwIfStopRequested()
            val liveEntries = prepareMusicEntries(
                request.storageId,
                page.items
                    .asSequence()
                    .filter { !it.deleted && !it.isDir }
                    .map { it.toStorageEntry(request.storageId) }
                    .toList(),
            ).filter {
                it.isAllowedByScanRules(request.selectedFolderCanonicalPath, request.scanRules)
            }
            val deletedPaths = page.items
                .asSequence()
                .filter { it.deleted }
                .map { normalizeRemotePath(it.path) }
                .distinct()
                .toList()
            val deletedExisting = if (deletedPaths.isEmpty()) {
                emptyList()
            } else {
                deletedPaths.flatMap { path ->
                    database.sourceItemDao().findLiveAtOrBelowPath(
                        libraryRootId = execution.libraryRoot.id,
                        canonicalPath = path,
                        descendantPattern = "${escapeLikePattern(path)}/%",
                    )
                }.distinctBy { it.id }
            }
            val occupiedLivePaths = if (liveEntries.isEmpty()) {
                emptySet()
            } else {
                database.sourceItemDao()
                    .findByPaths(request.storageId, liveEntries.map { normalizeRemotePath(it.path) })
                    .mapNotNullTo(mutableSetOf()) { it.canonicalPath }
            }
            val movedExistingByPath = matchWebDavMoves(
                liveEntries = liveEntries,
                deletedExisting = deletedExisting,
                occupiedLivePaths = occupiedLivePaths,
            )
            val movedSourceIds = movedExistingByPath.values.mapTo(mutableSetOf()) { it.id }
            val deletedSourceIds = deletedExisting
                .asSequence()
                .map { it.id }
                .filterNot(movedSourceIds::contains)
                .distinct()
                .toList()
            currentJob = currentJob.copy(
                scannedCount = currentJob.scannedCount + liveEntries.size + deletedPaths.size,
                discoveredMusicCount = currentJob.discoveredMusicCount + liveEntries.size + deletedPaths.size,
                updatedAt = currentTimeMillis(),
            )
            syncDao.upsertJob(currentJob)

            currentJob = applyWebDavDeletions(
                execution = execution,
                currentJob = currentJob,
                deletedSourceIds = deletedSourceIds,
                deletionEventCount = deletedPaths.size,
                missingFilePolicy = request.scanRules.missingFilePolicy,
                checkpoint = deletedPaths.lastOrNull(),
            )
            changedCount += deletedSourceIds.size

            liveEntries.chunked(request.importBatchSize).forEach { batch ->
                operation.throwIfStopRequested()
                val batchResult = importBatch(
                    request = request,
                    execution = execution,
                    currentJob = currentJob,
                    entries = batch,
                    movedExistingByPath = movedExistingByPath.filterKeys { path ->
                        batch.any { normalizeRemotePath(it.path) == path }
                    },
                )
                currentJob = batchResult.job
                changedCount += batchResult.changedCount
            }
            operation.throwIfStopRequested()
            val capabilityCursor = syncDao.getCursor(
                execution.libraryRoot.id,
                WEBDAV_CAPABILITY_CURSOR_TYPE,
            )
            currentJob = completeDeltaImport(
                execution = execution,
                currentJob = currentJob,
                deltaLink = page.syncToken,
                cursorType = WEBDAV_SYNC_TOKEN_CURSOR_TYPE,
                capabilityCursor = capabilityCursor,
                capabilityValue = WEBDAV_CAPABILITY_SUPPORTED,
            )
            importResult(execution, currentJob, changedCount)
        } catch (error: Throwable) {
            markImportStopOrFailure(error, operation, execution.libraryRoot, currentJob)
            throw error
        } finally {
            unregisterActiveOperation(execution, operation)
        }
    }

    private suspend fun applyWebDavDeletions(
        execution: ImportExecution,
        currentJob: ImportJobEntity,
        deletedSourceIds: List<Long>,
        deletionEventCount: Int,
        missingFilePolicy: MissingFilePolicy,
        checkpoint: String?,
    ): ImportJobEntity {
        if (deletionEventCount == 0) return currentJob
        val now = currentTimeMillis()
        val updatedJob = currentJob.copy(
            scannedCount = currentJob.scannedCount,
            skippedCount = currentJob.skippedCount +
                (deletionEventCount - deletedSourceIds.size).coerceAtLeast(0),
            checkpoint = checkpoint ?: currentJob.checkpoint,
            deletedCount = currentJob.deletedCount + deletedSourceIds.size,
            updatedAt = now,
        )
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                deletedSourceIds.chunked(MAX_SOURCE_ITEM_ID_QUERY_SIZE).forEach { ids ->
                    when (missingFilePolicy) {
                        MissingFilePolicy.MarkUnavailable -> {
                            database.sourceItemDao().markDeletedByIds(ids, now)
                            database.trackSourceRefDao().markUnavailableBySourceItemIds(ids, now)
                        }
                        MissingFilePolicy.RemoveOnScan -> database.sourceItemDao().deleteByIds(ids)
                    }
                }
                syncDao.upsertJob(updatedJob)
            }
        }
        return updatedJob
    }

    suspend fun scanAndImportFolder(
        storageId: Long,
        selectedFolderRemoteId: String?,
        selectedFolderCanonicalPath: String,
        selectedFolderDisplayPath: String? = null,
        scanId: String? = null,
        scanRules: LibrarySyncScanRules = LibrarySyncScanRules(),
        metadataScanMode: MetadataScanMode = MetadataScanMode.Full,
        metadataConcurrency: UInt = DEFAULT_METADATA_CONCURRENCY,
        importBatchSize: Int = DEFAULT_IMPORT_BATCH_SIZE,
        deltaLink: String? = null,
        cursorType: String = GENERIC_DELTA_CURSOR_TYPE,
        persistCursor: Boolean = true,
        clearRootCursor: Boolean = false,
        capabilityValue: String? = null,
        syncMode: String = SYNC_MODE_LEGACY_FULL_SCAN_FALLBACK,
        capabilityDetectionElapsedMs: Long = 0,
    ): RemoteLibraryImportResult {
        validateImportSettings(metadataConcurrency, importBatchSize)
        val request = RemoteLibraryImportRequest(
            storageId = storageId,
            selectedFolderRemoteId = selectedFolderRemoteId,
            selectedFolderCanonicalPath = selectedFolderCanonicalPath,
            selectedFolderDisplayPath = selectedFolderDisplayPath,
            entries = emptyList(),
            scanRules = scanRules,
            metadataScanMode = metadataScanMode,
            scanId = scanId,
            metadataConcurrency = metadataConcurrency,
            importBatchSize = importBatchSize,
            syncMode = syncMode,
            capabilityDetectionElapsedMs = capabilityDetectionElapsedMs,
        )
        val (execution, operation) = startTrackedImport(request)
        var currentJob = execution.job
        var scanSession: RemoteMusicScanSession? = null

        return try {
            val previousCursor = syncDao.getCursor(execution.libraryRoot.id, cursorType)
            val capabilityCursor = capabilityValue?.let {
                syncDao.getCursor(execution.libraryRoot.id, WEBDAV_CAPABILITY_CURSOR_TYPE)
            }
            val seenPaths = mutableSetOf<String>()
            val signatures = database.sourceItemDao()
                .signaturesForLibraryRoot(execution.libraryRoot.id)
            val signaturesByPath = signatures
                .mapNotNull { signature -> signature.canonicalPath?.let { it to signature } }
                .toMap()
            val signaturesByRemoteId = signatures
                .mapNotNull { signature -> signature.providerItemId?.let { it to signature } }
                .toMap()
            // Removing matches is read-only for source_item. Only the IDs left
            // after a fully completed snapshot are marked missing.
            val missingCandidateIds = signatures.mapTo(mutableSetOf()) { it.id }
            var changedCount = 0L
            var directoryRequestCount = 0L
            var listedDirectoryCount = 0L
            var visitedEntryCount = 0L
            var directoryConcurrency = DEFAULT_DIRECTORY_CONCURRENCY
            operation.throwIfStopRequested()
            val directoryScanStartedAt = currentTimeMillis()
            val session = remoteScannerRepository.startMusicFolderScan(
                storageId = StorageId(storageId),
                path = selectedFolderCanonicalPath,
            )
            scanSession = session
            operation.attachScanSession(session)
            while (true) {
                operation.throwIfStopRequested()
                val scanBatch = session.nextBatch(importBatchSize.toUInt())
                if (scanBatch.cancelled) {
                    throw ImportCancelledException()
                }
                directoryRequestCount = scanBatch.directoryRequestCount.toLongOrNull() ?: Long.MAX_VALUE
                listedDirectoryCount = scanBatch.listedDirectoryCount.toLongOrNull() ?: Long.MAX_VALUE
                visitedEntryCount = scanBatch.visitedEntryCount.toLongOrNull() ?: Long.MAX_VALUE
                directoryConcurrency = scanBatch.directoryConcurrency.toInt()
                operation.throwIfStopRequested()
                val entries = prepareDiscoveredMusicEntries(
                    storageId = storageId,
                    rootPath = request.selectedFolderCanonicalPath,
                    rules = request.scanRules,
                    seenPaths = seenPaths,
                    entries = scanBatch.entries,
                )
                if (entries.isNotEmpty()) {
                    currentJob = currentJob.copy(
                        scannedCount = currentJob.scannedCount + entries.size,
                        discoveredMusicCount = currentJob.discoveredMusicCount + entries.size,
                        updatedAt = currentTimeMillis(),
                    )
                    syncDao.upsertJob(currentJob)

                    val snapshotPlan = planCompleteSnapshotBatch(
                        entries = entries,
                        existingByPath = signaturesByPath,
                        existingByRemoteId = signaturesByRemoteId,
                    )
                    missingCandidateIds.removeAll(snapshotPlan.matchedExistingIds)
                    val batchResult = importBatch(
                        request = request,
                        execution = execution,
                        currentJob = currentJob,
                        entries = snapshotPlan.entriesToImport,
                        additionalSkippedCount = snapshotPlan.unchangedCount,
                        progressCheckpoint = entries.last().path,
                    )
                    currentJob = batchResult.job
                    changedCount += batchResult.changedCount
                    operation.throwIfStopRequested()
                }
                if (scanBatch.done) break
            }
            if (session.isCancelled()) {
                throw ImportCancelledException()
            }
            operation.throwIfStopRequested()
            currentJob = currentJob.copy(
                directoryScanElapsedMs = (currentTimeMillis() - directoryScanStartedAt)
                    .coerceAtLeast(0),
                directoryRequestCount = directoryRequestCount,
                listedDirectoryCount = listedDirectoryCount,
                visitedEntryCount = visitedEntryCount,
                directoryConcurrency = directoryConcurrency,
                updatedAt = currentTimeMillis(),
            )
            syncDao.upsertJob(currentJob)

            currentJob = completeImport(
                execution = execution,
                previousCursor = previousCursor,
                currentJob = currentJob,
                deltaLink = if (clearRootCursor) null else deltaLink ?: execution.libraryRoot.syncCursor,
                missingFilePolicy = request.scanRules.missingFilePolicy,
                missingCandidateIds = missingCandidateIds,
                cursorType = cursorType,
                persistCursor = persistCursor,
                clearRootCursor = clearRootCursor,
                capabilityCursor = capabilityCursor,
                capabilityValue = capabilityValue,
            )
            importResult(execution, currentJob, changedCount)
        } catch (error: Throwable) {
            markImportStopOrFailure(
                error = error,
                operation = operation,
                root = execution.libraryRoot,
                job = currentJob,
            )
            throw error
        } finally {
            withContext(NonCancellable) {
                unregisterActiveOperation(execution, operation)
                scanSession?.cancel()
                scanSession?.close()
            }
        }
    }

    /**
     * Imports a complete snapshot for one selected library folder.
     *
     * The caller must pass every current music file under the selected folder. Files
     * already in Room but missing from this snapshot are marked deleted.
     */
    suspend fun importCompleteSnapshot(
        request: RemoteLibraryImportRequest,
        deltaLink: String? = null,
    ): RemoteLibraryImportResult {
        validateImportSettings(request.metadataConcurrency, request.importBatchSize)
        val (execution, operation) = startTrackedImport(request)
        var currentJob = execution.job

        return try {
            val result = runCompleteSnapshotImport(
                request = request,
                deltaLink = deltaLink,
                execution = execution,
                operation = operation,
                currentJob = currentJob,
            )
            currentJob = result.second
            result.first
        } catch (error: Throwable) {
            markImportStopOrFailure(
                error = error,
                operation = operation,
                root = execution.libraryRoot,
                job = currentJob,
            )
            throw error
        } finally {
            unregisterActiveOperation(execution, operation)
        }
    }

    private suspend fun runCompleteSnapshotImport(
        request: RemoteLibraryImportRequest,
        deltaLink: String?,
        execution: ImportExecution,
        operation: ActiveImportOperation,
        currentJob: ImportJobEntity,
        cursorType: String = GENERIC_DELTA_CURSOR_TYPE,
        capabilityValue: String? = null,
    ): Pair<RemoteLibraryImportResult, ImportJobEntity> {
        operation.throwIfStopRequested()
        val previousCursor = syncDao.getCursor(execution.libraryRoot.id, cursorType)
        val capabilityCursor = capabilityValue?.let {
            syncDao.getCursor(execution.libraryRoot.id, WEBDAV_CAPABILITY_CURSOR_TYPE)
        }
        operation.throwIfStopRequested()
        val musicEntries = prepareMusicEntries(request.storageId, request.entries)
            .filter { it.isAllowedByScanRules(request.selectedFolderCanonicalPath, request.scanRules) }
        val signatures = database.sourceItemDao()
            .signaturesForLibraryRoot(execution.libraryRoot.id)
        val signaturesByPath = signatures
            .mapNotNull { signature -> signature.canonicalPath?.let { it to signature } }
            .toMap()
        val signaturesByRemoteId = signatures
            .mapNotNull { signature -> signature.providerItemId?.let { it to signature } }
            .toMap()
        val missingCandidateIds = signatures.mapTo(mutableSetOf()) { it.id }
        var changedCount = 0L

        var job = currentJob
        job = job.copy(
            scannedCount = musicEntries.size.toLong(),
            discoveredMusicCount = musicEntries.size.toLong(),
            updatedAt = currentTimeMillis(),
        )
        syncDao.upsertJob(job)

        musicEntries.chunked(request.importBatchSize).forEach { batch ->
            operation.throwIfStopRequested()
            val snapshotPlan = planCompleteSnapshotBatch(
                entries = batch,
                existingByPath = signaturesByPath,
                existingByRemoteId = signaturesByRemoteId,
            )
            missingCandidateIds.removeAll(snapshotPlan.matchedExistingIds)
            val batchResult = importBatch(
                request = request,
                execution = execution,
                currentJob = job,
                entries = snapshotPlan.entriesToImport,
                additionalSkippedCount = snapshotPlan.unchangedCount,
                progressCheckpoint = batch.last().path,
            )
            job = batchResult.job
            changedCount += batchResult.changedCount
            operation.throwIfStopRequested()
        }

        operation.throwIfStopRequested()
        job = completeImport(
            execution = execution,
            previousCursor = previousCursor,
            currentJob = job,
            deltaLink = deltaLink ?: execution.libraryRoot.syncCursor,
            missingFilePolicy = request.scanRules.missingFilePolicy,
            missingCandidateIds = missingCandidateIds,
            cursorType = cursorType,
            capabilityCursor = capabilityCursor,
            capabilityValue = capabilityValue,
        )
        return importResult(execution, job, changedCount) to job
    }

    private suspend fun importBatch(
        request: RemoteLibraryImportRequest,
        execution: ImportExecution,
        currentJob: ImportJobEntity,
        entries: List<StorageEntry>,
        additionalSkippedCount: Int = 0,
        progressCheckpoint: String? = entries.lastOrNull()?.path,
        movedExistingByPath: Map<String, SourceItemEntity> = emptyMap(),
    ): ImportBatchResult {
        val now = currentTimeMillis()
        val databaseReadStartedAt = now
        val metadataOptions = request.metadataScanMode.toOptions()
        val batchPaths = entries.map { normalizeRemotePath(it.path) }
        val existing = if (batchPaths.isEmpty()) {
            emptyMap()
        } else {
            database.sourceItemDao()
                .findByPaths(request.storageId, batchPaths)
                .associateBy { it.canonicalPath }
        }
        val remoteIds = entries.mapNotNull { it.remoteId }.distinct()
        val existingByRemoteId = if (remoteIds.isEmpty()) {
            emptyMap()
        } else {
            database.sourceItemDao()
                .findByProviderItemIds(request.storageId, remoteIds)
                .mapNotNull { item -> item.providerItemId?.let { it to item } }
                .toMap()
        }
        val plan = planRemoteLibraryImport(
            storageId = request.storageId,
            libraryRootId = execution.libraryRoot.id,
            scanId = execution.scanId,
            now = now,
            entries = entries,
            existing = existing,
            existingByRemoteId = existingByRemoteId,
            movedExistingByPath = movedExistingByPath,
        )
        val databaseReadElapsedMs = (currentTimeMillis() - databaseReadStartedAt).coerceAtLeast(0)
        val metadataResults = if (plan.metadataEntries.isEmpty()) {
            emptyList()
        } else {
            metadataRepository.readBatch(
                entries = plan.metadataEntries,
                concurrency = request.metadataConcurrency,
                options = metadataOptions,
            )
        }
        val metadataByPath = metadataResults
            .mapNotNull { result ->
                val metadata = result.metadata ?: return@mapNotNull null
                normalizeRemotePath(result.entry.path) to metadata
            }
            .toMap()
        val batchMetadataRequestCount = metadataByPath.values.sumMetric {
            it.metadataRequestCount
        }
        val batchMetadataFetchedBytes = metadataByPath.values.sumMetric {
            it.metadataFetchedBytes
        }
        val batchMetadataElapsedMs = metadataByPath.values.sumMetric {
            it.metadataElapsedMs
        }
        val batchArtworkCachedBytes = metadataByPath.values.sumMetric {
            it.artworkCachedBytes
        }
        val shortAudioPaths = if (request.scanRules.minDurationMs > 0L) {
            metadataByPath
                .filterValues { metadata -> metadata.isShorterThan(request.scanRules.minDurationMs) }
                .keys
                .toSet()
        } else {
            emptySet()
        }
        val shortSkippedCount = plan.changedEntries.count { entry ->
            normalizeRemotePath(entry.path) in shortAudioPaths
        }
        val failureDetails = buildImportFailureDetails(
            plan = plan,
            metadataResults = metadataResults,
        )
        val batchFailedCount = failureDetails.size
        lateinit var updatedJob: ImportJobEntity
        var missingPluginArtworkTrackIds = emptyList<Long>()

        val databaseWriteStartedAt = currentTimeMillis()
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                database.sourceItemDao().applyScanBatch(
                    changedItems = plan.changedItems,
                )
                val changedExistingSourceItemIds = plan.changedItems
                    .map { it.id }
                    .filter { it != 0L }
                if (changedExistingSourceItemIds.isNotEmpty()) {
                    database.trackSourceRefDao()
                        .markAvailableBySourceItemIds(changedExistingSourceItemIds, now)
                }
                val sourceRows = if (plan.changedItems.isEmpty()) {
                    emptyMap()
                } else {
                    database.sourceItemDao()
                        .findByPaths(
                            request.storageId,
                            plan.changedItems.mapNotNull { it.canonicalPath },
                        )
                        .associateBy { it.canonicalPath }
                }
                val sourceErrors = failureDetails.map { detail ->
                    SourceErrorEntity(
                        sourceAccountId = request.storageId,
                        libraryRootId = execution.libraryRoot.id,
                        sourceItemId = sourceRows[detail.path]?.id,
                        importJobId = execution.job.id,
                        errorType = detail.errorType,
                        message = detail.message,
                        createdAt = now,
                        resolvedAt = null,
                    )
                }
                if (sourceErrors.isNotEmpty()) {
                    database.sourceErrorDao().insertAll(sourceErrors)
                }
                val trackMetadata = plan.changedEntries.mapNotNull { entry ->
                    val path = normalizeRemotePath(entry.path)
                    if (path in shortAudioPaths) return@mapNotNull null
                    val metadata = metadataByPath[path] ?: return@mapNotNull null
                    val sourceItem = sourceRows[path] ?: return@mapNotNull null
                    SourceImportRow(entry, metadata, sourceItem)
                }
                val metadataParsing = settingsRepository?.settings?.first()?.metadataParsing
                    ?: MetadataParsingSettings.Default
                val albumsByName = ensureAlbums(trackMetadata.map { it.metadata })
                val artistsByName = ensureArtists(trackMetadata.map { it.metadata }, metadataParsing)
                val genresByName = ensureGenres(trackMetadata.map { it.metadata }, metadataParsing)
                val existingRefsBySourceItemId = if (trackMetadata.isEmpty()) {
                    emptyMap()
                } else {
                    database.trackSourceRefDao()
                        .findBySourceItemIds(trackMetadata.map { it.sourceItem.id })
                        .associateBy { it.sourceItemId }
                }
                val existingTracksById = if (existingRefsBySourceItemId.isEmpty()) {
                    emptyMap()
                } else {
                    trackDao.findByIds(existingRefsBySourceItemId.values.map { it.trackId })
                        .associateBy { it.id }
                }
                val trackContexts = trackMetadata.map { row ->
                    val entry = row.entry
                    val metadata = row.metadata
                    val sourceItem = row.sourceItem
                    val existingRef = existingRefsBySourceItemId[sourceItem.id]
                    val canonicalMatch = existingRef
                        ?.let { ref ->
                            existingTracksById[ref.trackId]?.let { track ->
                                CanonicalTrackMatch(
                                    track = track,
                                    method = ref.matchMethod,
                                    confidence = ref.matchConfidence,
                                    preserveCanonicalMetadata = ref.role == TrackSourceRoles.Alternate,
                                )
                            }
                        }
                        ?: findCanonicalTrack(
                            metadata = metadata,
                            sourceItem = sourceItem,
                        )
                    val track = buildTrackEntity(
                        entry = entry,
                        metadata = metadata,
                        sourceItem = sourceItem,
                        now = now,
                        existingTrack = canonicalMatch?.track,
                        albumId = metadata.album
                            ?.let(::normalizeMetadataName)
                            ?.let(albumsByName::get)
                            ?.id,
                        preserveExistingMetadata = canonicalMatch?.preserveCanonicalMetadata == true,
                    )
                    TrackMetadataContext(
                        track = track,
                        metadata = metadata,
                        sourceItem = sourceItem,
                        updateCanonicalMetadata = canonicalMatch?.preserveCanonicalMetadata != true,
                        sourceRole = existingRef?.role ?: if (canonicalMatch?.preserveCanonicalMetadata == true) {
                            TrackSourceRoles.Alternate
                        } else {
                            TrackSourceRoles.Primary
                        },
                        matchMethod = canonicalMatch?.method ?: TrackMatchMethods.SourceIdentity,
                        matchConfidence = canonicalMatch?.confidence ?: MATCH_CONFIDENCE_EXACT,
                    )
                }
                val tracks = trackContexts.map { it.track }
                trackDao.upsertAll(tracks)
                val sourceRefs = trackContexts.map { context ->
                    buildTrackSourceRefEntity(
                        track = context.track,
                        sourceItem = context.sourceItem,
                        metadata = context.metadata,
                        now = now,
                        existingRef = existingRefsBySourceItemId[context.sourceItem.id],
                        role = context.sourceRole,
                        matchMethod = context.matchMethod,
                        matchConfidence = context.matchConfidence,
                    )
                }
                if (sourceRefs.isNotEmpty()) {
                    database.trackSourceRefDao().upsertAll(sourceRefs)
                }
                val unlockedTrackContexts = trackContexts.filter { context ->
                    context.updateCanonicalMetadata && !context.track.metadataLocked
                }
                val unlockedTrackIds = unlockedTrackContexts.map { it.track.id }
                if (unlockedTrackIds.isNotEmpty()) {
                    metadataDao.deleteTrackArtistsForTracks(unlockedTrackIds)
                    metadataDao.deleteTrackGenresForTracks(unlockedTrackIds)
                }
                val trackArtists = unlockedTrackContexts.flatMap { context ->
                    context.metadata.trackArtists(metadataParsing).mapIndexedNotNull { position, name ->
                        artistsByName[normalizeMetadataName(name)]?.let { artist ->
                            TrackArtistCrossRef(
                                trackId = context.track.id,
                                artistId = artist.id,
                                position = position,
                            )
                        }
                    }
                }
                if (trackArtists.isNotEmpty()) {
                    metadataDao.upsertTrackArtists(trackArtists)
                }
                val trackGenres = unlockedTrackContexts.flatMap { context ->
                    context.metadata.genres(metadataParsing).mapNotNull { genreName ->
                        genresByName[normalizeMetadataName(genreName)]?.let { genre ->
                            TrackGenreCrossRef(
                                trackId = context.track.id,
                                genreId = genre.id,
                            )
                        }
                    }
                }
                if (trackGenres.isNotEmpty()) {
                    metadataDao.upsertTrackGenres(trackGenres)
                }
                val albumIds = unlockedTrackContexts.mapNotNull { it.track.albumId }.distinct()
                if (albumIds.isNotEmpty()) {
                    metadataDao.deleteAlbumArtistsForAlbums(albumIds)
                }
                val albumArtists = unlockedTrackContexts.mapNotNull { context ->
                    val albumId = context.track.albumId ?: return@mapNotNull null
                    val albumArtist = context.metadata.albumArtists(metadataParsing).firstOrNull()
                        ?: return@mapNotNull null
                    artistsByName[normalizeMetadataName(albumArtist)]?.let { artist ->
                        AlbumArtistCrossRef(
                            albumId = albumId,
                            artistId = artist.id,
                            position = 0,
                        )
                    }
                }.distinctBy { it.albumId to it.artistId }
                if (albumArtists.isNotEmpty()) {
                    metadataDao.upsertAlbumArtists(albumArtists)
                }
                metadataDao.updateOptionalMetadata(
                    updates = trackContexts.filter(TrackMetadataContext::updateCanonicalMetadata).map { context ->
                        OptionalMetadataUpdate(
                            trackId = context.track.id,
                            albumId = context.track.albumId,
                            metadata = context.metadata,
                        )
                    },
                    options = metadataOptions,
                    now = now,
                )
                missingPluginArtworkTrackIds = trackContexts
                    .filter { context -> shouldLookupPluginArtwork(context.metadata.hasEmbeddedArtwork) }
                    .map { context -> context.track.id }
                updatedJob = currentJob.copy(
                    scannedCount = currentJob.scannedCount,
                    importedCount = currentJob.importedCount + tracks.size,
                    skippedCount = currentJob.skippedCount + plan.metadataSkippedCount +
                        shortSkippedCount + additionalSkippedCount,
                    failedCount = currentJob.failedCount + batchFailedCount,
                    metadataRequestCount = currentJob.metadataRequestCount
                        .saturatedAdd(batchMetadataRequestCount),
                    metadataFetchedBytes = currentJob.metadataFetchedBytes
                        .saturatedAdd(batchMetadataFetchedBytes),
                    metadataElapsedMs = currentJob.metadataElapsedMs
                        .saturatedAdd(batchMetadataElapsedMs),
                    artworkCachedBytes = currentJob.artworkCachedBytes
                        .saturatedAdd(batchArtworkCachedBytes),
                    discoveredMusicCount = currentJob.discoveredMusicCount,
                    unchangedCount = currentJob.unchangedCount + plan.unchangedCount +
                        additionalSkippedCount,
                    addedCount = currentJob.addedCount + plan.addedCount,
                    modifiedCount = currentJob.modifiedCount + plan.modifiedCount,
                    renamedCount = currentJob.renamedCount + plan.renamedCount,
                    databaseReadElapsedMs = currentJob.databaseReadElapsedMs
                        .saturatedAdd(databaseReadElapsedMs),
                    checkpoint = progressCheckpoint ?: currentJob.checkpoint,
                    errorMessage = failureDetails.summaryOrNull() ?: currentJob.errorMessage,
                    updatedAt = now,
                )
                syncDao.upsertJob(updatedJob)
            }
        }
        val databaseWriteElapsedMs = (currentTimeMillis() - databaseWriteStartedAt).coerceAtLeast(0)
        updatedJob = updatedJob.copy(
            databaseWriteElapsedMs = currentJob.databaseWriteElapsedMs
                .saturatedAdd(databaseWriteElapsedMs),
        )
        if (plan.unchangedFileIds.isNotEmpty()) {
            missingPluginArtworkTrackIds += database.trackSourceRefDao()
                .findBySourceItemIds(plan.unchangedFileIds)
                .filter { reference -> shouldLookupPluginArtwork(reference.hasEmbeddedArtwork) }
                .map { reference -> reference.trackId }
        }
        try {
            pluginArtworkResolver?.cacheMissingForBatch(missingPluginArtworkTrackIds.distinct())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Plugin cover lookup is best effort and must not fail a completed import batch.
        }

        return ImportBatchResult(
            job = updatedJob,
            changedCount = plan.changedCount.toLong(),
        )
    }

    private suspend fun findCanonicalTrack(
        metadata: RemoteMetadata,
        sourceItem: SourceItemEntity,
    ): CanonicalTrackMatch? {
        suspend fun match(
            candidates: List<TrackEntity>,
            method: String,
            confidence: Int,
        ): CanonicalTrackMatch? {
            val track = candidates.distinctBy(TrackEntity::id).singleOrNull() ?: return null
            val hasSameSource = database.trackSourceRefDao()
                .hasSourceAccount(track.id, sourceItem.sourceAccountId)
            return CanonicalTrackMatch(
                track = track,
                method = method,
                confidence = confidence,
                preserveCanonicalMetadata = !hasSameSource,
            )
        }

        sourceItem.contentHash?.takeIf(String::isNotBlank)?.let { contentHash ->
            match(
                candidates = trackDao.findBySourceContentHash(contentHash),
                method = TrackMatchMethods.ContentHash,
                confidence = MATCH_CONFIDENCE_EXACT,
            )?.let { return it }
        }

        val durationMs = metadata.durationMs.toLongOrNull()
        if (durationMs != null) {
            sourceItem.audioFingerprint?.takeIf(String::isNotBlank)?.let { fingerprint ->
                match(
                    candidates = trackDao.findByAudioFingerprintWithinDuration(
                        audioFingerprint = fingerprint,
                        minDurationMs = durationMs - DURATION_MATCH_TOLERANCE_MS,
                        maxDurationMs = durationMs + DURATION_MATCH_TOLERANCE_MS,
                    ),
                    method = TrackMatchMethods.AudioFingerprint,
                    confidence = MATCH_CONFIDENCE_FINGERPRINT,
                )?.let { return it }
            }
        }

        val recordingId = metadata.musicbrainzRecordingId?.takeIf { it.isNotBlank() }
        if (recordingId != null) {
            match(
                candidates = trackDao.findByMusicBrainzRecordingId(recordingId),
                method = TrackMatchMethods.MusicBrainzRecordingId,
                confidence = MATCH_CONFIDENCE_EXACT,
            )?.let { return it }
        }

        durationMs ?: return null
        val isrc = metadata.isrc?.takeIf { it.isNotBlank() }
        if (isrc != null) {
            match(
                candidates = trackDao.findByIsrcWithinDuration(
                    isrc = isrc,
                    minDurationMs = durationMs - DURATION_MATCH_TOLERANCE_MS,
                    maxDurationMs = durationMs + DURATION_MATCH_TOLERANCE_MS,
                ),
                method = TrackMatchMethods.IsrcDuration,
                confidence = MATCH_CONFIDENCE_ISRC,
            )?.let { return it }
        }

        val title = metadata.title?.takeIf { it.isNotBlank() }
            ?: sourceItem.displayName.substringBeforeLast('.')
        if (title.hasTrackVersionToken()) return null
        val titleKey = title.normalizedTrackMatchKey()
        val artistKey = metadata.artist.normalizedTrackMatchKey()
        val albumKey = metadata.album.normalizedTrackMatchKey()
        if (titleKey.isBlank() || artistKey.isBlank() || albumKey.isBlank()) return null

        return match(
            candidates = trackDao.findByStrictMetadata(
                titleKey = titleKey,
                artistKey = artistKey,
                albumKey = albumKey,
                minDurationMs = durationMs - DURATION_MATCH_TOLERANCE_MS,
                maxDurationMs = durationMs + DURATION_MATCH_TOLERANCE_MS,
            ),
            method = TrackMatchMethods.StrictMetadata,
            confidence = MATCH_CONFIDENCE_STRICT_METADATA,
        )
    }

    private suspend fun ensureAlbums(
        metadata: List<RemoteMetadata>,
    ): Map<String, AlbumEntity> {
        val values = metadata.mapNotNull { item ->
            val name = item.album?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            AlbumEntity(
                name = name,
                normalizedName = normalizeMetadataName(name),
                sortName = null,
                year = item.date?.yearPrefix(),
                artworkId = null,
            )
        }.distinctBy { it.normalizedName }
        if (values.isEmpty()) return emptyMap()
        metadataDao.insertAlbums(values)
        return metadataDao.findAlbumsByNormalizedNames(values.map { it.normalizedName })
            .associateBy { it.normalizedName }
    }

    private suspend fun ensureArtists(
        metadata: List<RemoteMetadata>,
        parsing: MetadataParsingSettings,
    ): Map<String, ArtistEntity> {
        val names = metadata
            .flatMap { it.trackArtists(parsing) + it.albumArtists(parsing) }
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy(::normalizeMetadataName)
        if (names.isEmpty()) return emptyMap()
        metadataDao.insertArtists(
            names.map { name ->
                ArtistEntity(
                    name = name,
                    normalizedName = normalizeMetadataName(name),
                    sortName = null,
                )
            }
        )
        return metadataDao.findArtistsByNormalizedNames(names.map(::normalizeMetadataName))
            .associateBy { it.normalizedName }
    }

    private suspend fun ensureGenres(
        metadata: List<RemoteMetadata>,
        parsing: MetadataParsingSettings,
    ): Map<String, GenreEntity> {
        val names = metadata
            .flatMap { it.genres(parsing) }
            .distinctBy(::normalizeMetadataName)
        if (names.isEmpty()) return emptyMap()
        metadataDao.insertGenres(
            names.map { name ->
                GenreEntity(
                    name = name,
                    normalizedName = normalizeMetadataName(name),
                )
            }
        )
        return metadataDao.findGenresByNormalizedNames(names.map(::normalizeMetadataName))
            .associateBy { it.normalizedName }
    }

    private suspend fun completeImport(
        execution: ImportExecution,
        previousCursor: SourceSyncCursorEntity?,
        currentJob: ImportJobEntity,
        deltaLink: String?,
        missingFilePolicy: MissingFilePolicy,
        missingCandidateIds: Set<Long>,
        cursorType: String = GENERIC_DELTA_CURSOR_TYPE,
        persistCursor: Boolean = true,
        clearRootCursor: Boolean = false,
        capabilityCursor: SourceSyncCursorEntity? = null,
        capabilityValue: String? = null,
    ): ImportJobEntity {
        val now = currentTimeMillis()
        val completedJob = currentJob.copy(
            status = if (currentJob.failedCount == 0L) {
                ImportJobStatus.COMPLETED
            } else {
                ImportJobStatus.COMPLETED_WITH_ERRORS
            },
            deletedCount = currentJob.deletedCount + missingCandidateIds.size,
            totalElapsedMs = (now - currentJob.createdAt).coerceAtLeast(0),
            updatedAt = now,
        )
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                when (missingFilePolicy) {
                    MissingFilePolicy.MarkUnavailable -> {
                        missingCandidateIds.chunked(MAX_SOURCE_ITEM_ID_QUERY_SIZE).forEach { ids ->
                            database.sourceItemDao().markDeletedByIds(ids, now)
                            database.trackSourceRefDao()
                                .markUnavailableBySourceItemIds(ids, now)
                        }
                    }
                    MissingFilePolicy.RemoveOnScan -> {
                        missingCandidateIds.chunked(MAX_SOURCE_ITEM_ID_QUERY_SIZE).forEach { ids ->
                            database.sourceItemDao().deleteByIds(ids)
                        }
                    }
                }
                if (persistCursor) {
                    syncDao.upsertCursor(
                        SourceSyncCursorEntity(
                            id = previousCursor?.id ?: 0,
                            sourceAccountId = execution.libraryRoot.sourceAccountId,
                            libraryRootId = execution.libraryRoot.id,
                            cursorType = cursorType,
                            cursorValue = deltaLink,
                            lastScanId = execution.scanId,
                            lastSyncAt = now,
                        )
                    )
                } else if (clearRootCursor) {
                    syncDao.deleteCursor(execution.libraryRoot.id, cursorType)
                }
                if (capabilityValue != null) {
                    syncDao.upsertCursor(
                        SourceSyncCursorEntity(
                            id = capabilityCursor?.id ?: 0,
                            sourceAccountId = execution.libraryRoot.sourceAccountId,
                            libraryRootId = execution.libraryRoot.id,
                            cursorType = WEBDAV_CAPABILITY_CURSOR_TYPE,
                            cursorValue = capabilityValue,
                            lastScanId = execution.scanId,
                            lastSyncAt = now,
                        )
                    )
                }
                syncDao.upsertJob(completedJob)
                database.libraryRootDao().upsert(
                    execution.libraryRoot.copy(
                        syncCursor = if (clearRootCursor) null else deltaLink,
                        syncStatus = if (completedJob.failedCount == 0L) {
                            LibraryRootSyncStatus.SYNCED
                        } else {
                            LibraryRootSyncStatus.SYNCED_WITH_ERRORS
                        },
                        lastSyncAt = now,
                        updatedAt = now,
                    )
                )
            }
        }
        return completedJob
    }

    private suspend fun completeDeltaImport(
        execution: ImportExecution,
        currentJob: ImportJobEntity,
        deltaLink: String,
        cursorType: String = GENERIC_DELTA_CURSOR_TYPE,
        capabilityCursor: SourceSyncCursorEntity? = null,
        capabilityValue: String? = null,
    ): ImportJobEntity {
        val now = currentTimeMillis()
        val previousCursor = syncDao.getCursor(execution.libraryRoot.id, cursorType)
        val completedJob = currentJob.copy(
            status = if (currentJob.failedCount == 0L) {
                ImportJobStatus.COMPLETED
            } else {
                ImportJobStatus.COMPLETED_WITH_ERRORS
            },
            totalElapsedMs = (now - currentJob.createdAt).coerceAtLeast(0),
            updatedAt = now,
        )
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                syncDao.upsertCursor(
                    SourceSyncCursorEntity(
                        id = previousCursor?.id ?: 0,
                        sourceAccountId = execution.libraryRoot.sourceAccountId,
                        libraryRootId = execution.libraryRoot.id,
                        cursorType = cursorType,
                        cursorValue = deltaLink,
                        lastScanId = execution.scanId,
                        lastSyncAt = now,
                    )
                )
                if (capabilityValue != null) {
                    syncDao.upsertCursor(
                        SourceSyncCursorEntity(
                            id = capabilityCursor?.id ?: 0,
                            sourceAccountId = execution.libraryRoot.sourceAccountId,
                            libraryRootId = execution.libraryRoot.id,
                            cursorType = WEBDAV_CAPABILITY_CURSOR_TYPE,
                            cursorValue = capabilityValue,
                            lastScanId = execution.scanId,
                            lastSyncAt = now,
                        )
                    )
                }
                syncDao.upsertJob(completedJob)
                database.libraryRootDao().upsert(
                    execution.libraryRoot.copy(
                        syncCursor = deltaLink,
                        syncStatus = if (completedJob.failedCount == 0L) {
                            LibraryRootSyncStatus.SYNCED
                        } else {
                            LibraryRootSyncStatus.SYNCED_WITH_ERRORS
                        },
                        lastSyncAt = now,
                        updatedAt = now,
                    )
                )
            }
        }
        return completedJob
    }

    private suspend fun readOneDriveDelta(
        storageId: Long,
        rootRemoteId: String,
        cursor: String?,
        latestOnly: Boolean,
        operation: ActiveImportOperation? = null,
    ): OneDriveDeltaSnapshot {
        val items = mutableListOf<OneDriveDeltaItem>()
        var nextCursor = cursor
        var pageCount = 0
        while (true) {
            operation?.throwIfStopRequested()
            pageCount += 1
            check(pageCount <= MAX_DELTA_PAGES) {
                "OneDrive delta exceeded the $MAX_DELTA_PAGES page safety limit"
            }
            when (
                val result = remoteScannerRepository.getOneDriveDeltaPage(
                    storageId = StorageId(storageId),
                    rootRemoteId = rootRemoteId,
                    cursor = nextCursor,
                    latestOnly = latestOnly && pageCount == 1,
                )
            ) {
                is OneDriveDeltaPageResult.Page -> {
                    operation?.throwIfStopRequested()
                    result.v1.refreshToken?.let { refreshToken ->
                        storageRepository.updateOneDriveRefreshToken(
                            StorageId(storageId),
                            refreshToken,
                        )
                    }
                    operation?.throwIfStopRequested()
                    items += result.v1.items
                    check(items.size <= MAX_DELTA_ITEMS) {
                        "OneDrive delta exceeded the $MAX_DELTA_ITEMS item safety limit"
                    }
                    val nextLink = result.v1.nextLink
                    if (nextLink != null) {
                        nextCursor = nextLink
                        continue
                    }
                    return OneDriveDeltaSnapshot(
                        items = items,
                        deltaLink = result.v1.deltaLink,
                        resyncRequired = false,
                    )
                }
                OneDriveDeltaPageResult.ResyncRequired -> {
                    operation?.throwIfStopRequested()
                    return OneDriveDeltaSnapshot(
                        items = emptyList(),
                        deltaLink = null,
                        resyncRequired = true,
                    )
                }
            }
        }
    }

    private fun importResult(
        execution: ImportExecution,
        job: ImportJobEntity,
        changedCount: Long,
    ): RemoteLibraryImportResult {
        return RemoteLibraryImportResult(
            scanId = execution.scanId,
            selectedFolderId = execution.libraryRoot.id,
            scannedCount = job.scannedCount,
            changedCount = changedCount,
            skippedCount = job.skippedCount,
            importedCount = job.importedCount,
            failedCount = job.failedCount,
            metadataRequestCount = job.metadataRequestCount,
            metadataFetchedBytes = job.metadataFetchedBytes,
            metadataElapsedMs = job.metadataElapsedMs,
            artworkCachedBytes = job.artworkCachedBytes,
            syncMode = job.syncMode,
            directoryConcurrency = job.directoryConcurrency,
            capabilityDetectionElapsedMs = job.capabilityDetectionElapsedMs,
            directoryScanElapsedMs = job.directoryScanElapsedMs,
            directoryRequestCount = job.directoryRequestCount,
            listedDirectoryCount = job.listedDirectoryCount,
            visitedEntryCount = job.visitedEntryCount,
            discoveredMusicCount = job.discoveredMusicCount,
            unchangedCount = job.unchangedCount,
            addedCount = job.addedCount,
            modifiedCount = job.modifiedCount,
            renamedCount = job.renamedCount,
            deletedCount = job.deletedCount,
            databaseReadElapsedMs = job.databaseReadElapsedMs,
            databaseWriteElapsedMs = job.databaseWriteElapsedMs,
            totalElapsedMs = job.totalElapsedMs,
        )
    }

    private suspend fun startImport(request: RemoteLibraryImportRequest): ImportExecution {
        val startedAt = currentTimeMillis()
        val sourceAccount = ensureSourceAccount(request.storageId, startedAt)
        val libraryRoot = ensureLibraryRoot(
            request = request,
            now = startedAt,
            sourceProviderType = sourceAccount.providerType,
        )
        val scanId = request.scanId ?: "scan-${libraryRoot.id}-$startedAt"
        val job = ImportJobEntity(
            id = scanId,
            libraryRootId = libraryRoot.id,
            status = ImportJobStatus.RUNNING,
            scannedCount = 0,
            importedCount = 0,
            skippedCount = 0,
            failedCount = 0,
            checkpoint = null,
            errorMessage = null,
            createdAt = startedAt,
            updatedAt = startedAt,
            metadataScanMode = request.metadataScanMode.name,
            metadataConcurrency = request.metadataConcurrency.toLong(),
            importBatchSize = request.importBatchSize,
            scanSubdirectories = request.scanRules.scanSubdirectories,
            ignoreShortAudio = request.scanRules.minDurationMs > 0,
            minDurationMs = request.scanRules.minDurationMs,
            ignoreHiddenFiles = request.scanRules.ignoreHiddenFiles,
            ignoredDirectoryNames = request.scanRules.ignoredDirectoryNames
                .sorted()
                .joinToString(SNAPSHOT_LIST_SEPARATOR),
            missingFilePolicy = request.scanRules.missingFilePolicy.name,
            duplicateTrackPolicy = AUTOMATIC_DUPLICATE_TRACK_POLICY,
            syncMode = request.syncMode,
            directoryConcurrency = DEFAULT_DIRECTORY_CONCURRENCY,
            capabilityDetectionElapsedMs = request.capabilityDetectionElapsedMs,
            directoryScanElapsedMs = request.directoryScanElapsedMs,
        )
        syncDao.upsertJob(job)
        database.sourceErrorDao().deleteByImportJob(scanId)
        return ImportExecution(libraryRoot, scanId, job)
    }

    private suspend fun startTrackedImport(
        request: RemoteLibraryImportRequest,
    ): Pair<ImportExecution, ActiveImportOperation> {
        val execution = startImport(request)
        return try {
            execution to registerActiveOperation(execution)
        } catch (error: Throwable) {
            markImportFailed(execution.libraryRoot, execution.job, error)
            throw error
        }
    }

    private suspend fun markImportFailed(
        root: LibraryRootEntity,
        job: ImportJobEntity,
        error: Throwable,
    ) {
        val now = currentTimeMillis()
        syncDao.upsertJob(
            job.copy(
                status = ImportJobStatus.FAILED,
                errorMessage = error.message?.take(512),
                totalElapsedMs = (now - job.createdAt).coerceAtLeast(0),
                updatedAt = now,
            )
        )
        database.libraryRootDao().upsert(
            root.copy(
                syncStatus = LibraryRootSyncStatus.FAILED,
                lastSyncAt = now,
                updatedAt = now,
            )
        )
    }

    private suspend fun markImportCancelled(
        root: LibraryRootEntity,
        job: ImportJobEntity,
    ) {
        val now = currentTimeMillis()
        syncDao.upsertJob(
            job.copy(
                status = ImportJobStatus.CANCELLED,
                errorMessage = null,
                totalElapsedMs = (now - job.createdAt).coerceAtLeast(0),
                updatedAt = now,
            )
        )
        database.libraryRootDao().upsert(
            root.copy(
                syncStatus = LibraryRootSyncStatus.CANCELLED,
                lastSyncAt = now,
                updatedAt = now,
            )
        )
    }

    private suspend fun markImportPaused(
        root: LibraryRootEntity,
        job: ImportJobEntity,
    ) {
        val now = currentTimeMillis()
        syncDao.upsertJob(
            job.copy(
                status = ImportJobStatus.PAUSED,
                errorMessage = null,
                totalElapsedMs = (now - job.createdAt).coerceAtLeast(0),
                updatedAt = now,
            )
        )
        database.libraryRootDao().upsert(
            root.copy(
                syncStatus = LibraryRootSyncStatus.PAUSED,
                lastSyncAt = now,
                updatedAt = now,
            )
        )
    }

    private suspend fun markImportStopOrFailure(
        error: Throwable,
        operation: ActiveImportOperation,
        root: LibraryRootEntity,
        job: ImportJobEntity,
    ) {
        withContext(NonCancellable) {
            if (error is CancellationException || error is ImportCancelledException) {
                if (operation.isPauseRequested()) {
                    markImportPaused(root, job)
                } else {
                    markImportCancelled(root, job)
                }
            } else {
                markImportFailed(root, job, error)
            }
        }
    }

    private suspend fun registerActiveOperation(
        execution: ImportExecution,
    ): ActiveImportOperation {
        return activeOperationsMutex.withLock {
            check(execution.scanId !in activeOperations) {
                "scan ${execution.scanId} is already active"
            }
            ActiveImportOperation().also { operation ->
                activeOperations[execution.scanId] = operation
            }
        }
    }

    private suspend fun unregisterActiveOperation(
        execution: ImportExecution,
        operation: ActiveImportOperation,
    ) {
        activeOperationsMutex.withLock {
            if (activeOperations[execution.scanId] === operation) {
                activeOperations.remove(execution.scanId)
            }
        }
    }

    private suspend fun ensureSourceAccount(storageId: Long, now: Long): SourceAccountEntity {
        val existing = database.sourceAccountDao().get(storageId)
        val account = existing?.copy(updatedAt = now) ?: SourceAccountEntity(
            id = storageId,
            providerType = ProviderTypes.WebDav,
            displayName = "Source $storageId",
            endpoint = null,
            externalAccountId = null,
            credentialRef = "storage-$storageId",
            priority = 0,
            enabled = true,
            createdAt = now,
            updatedAt = now,
        )
        database.sourceAccountDao().upsert(account)
        return database.sourceAccountDao().get(storageId)
            ?: error("source account was not persisted")
    }

    private suspend fun ensureLibraryRoot(
        request: RemoteLibraryImportRequest,
        now: Long,
        sourceProviderType: String,
    ): LibraryRootEntity {
        val canonicalPath = normalizeRemotePath(request.selectedFolderCanonicalPath)
        val libraryRootDao = database.libraryRootDao()
        val existing = libraryRootDao.findByPath(request.storageId, canonicalPath)
            ?: if (sourceProviderType == ProviderTypes.Local) {
                canonicalPath.toLegacyAndroidPrimaryStoragePath()?.let { legacyPath ->
                    libraryRootDao.findByPath(request.storageId, legacyPath)
                }
            } else {
                null
            }
            ?: request.selectedFolderRemoteId?.let { remoteId ->
                libraryRootDao.findByProviderRootId(request.storageId, remoteId)
            }
        val root = LibraryRootEntity(
            id = existing?.id ?: 0,
            sourceAccountId = request.storageId,
            providerRootId = request.selectedFolderRemoteId ?: existing?.providerRootId,
            canonicalPath = canonicalPath,
            displayName = request.selectedFolderDisplayPath ?: existing?.displayName ?: canonicalPath,
            syncStatus = LibraryRootSyncStatus.RUNNING,
            syncCursor = existing?.syncCursor,
            lastSyncAt = existing?.lastSyncAt,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        libraryRootDao.upsert(root)
        return libraryRootDao.findByPath(request.storageId, canonicalPath)
            ?: root.providerRootId?.let { libraryRootDao.findByProviderRootId(request.storageId, it) }
            ?: error("library root was not persisted")
    }
}

private data class ImportExecution(
    val libraryRoot: LibraryRootEntity,
    val scanId: String,
    val job: ImportJobEntity,
)

private data class ImportBatchResult(
    val job: ImportJobEntity,
    val changedCount: Long,
)

private data class TrackMetadataContext(
    val track: TrackEntity,
    val metadata: RemoteMetadata,
    val sourceItem: SourceItemEntity,
    val updateCanonicalMetadata: Boolean,
    val sourceRole: String,
    val matchMethod: String,
    val matchConfidence: Int,
)

private data class CanonicalTrackMatch(
    val track: TrackEntity,
    val method: String,
    val confidence: Int,
    val preserveCanonicalMetadata: Boolean,
)

private data class SourceImportRow(
    val entry: StorageEntry,
    val metadata: RemoteMetadata,
    val sourceItem: SourceItemEntity,
)

private data class OneDriveDeltaSnapshot(
    val items: List<OneDriveDeltaItem>,
    val deltaLink: String?,
    val resyncRequired: Boolean,
)

private class ImportCancelledException : CancellationException("remote scan cancelled")

internal class ActiveImportOperation {
    private val mutex = Mutex()
    private var stopRequest: ActiveImportStopRequest? = null
    private var scanSession: RemoteMusicScanSession? = null

    suspend fun cancel() {
        requestStop(ActiveImportStopRequest.Cancel)
    }

    suspend fun pause() {
        requestStop(ActiveImportStopRequest.Pause)
    }

    suspend fun attachScanSession(session: RemoteMusicScanSession) {
        val shouldCancel = mutex.withLock {
            scanSession = session
            stopRequest != null
        }
        if (shouldCancel) {
            session.cancel()
        }
    }

    suspend fun throwIfStopRequested() {
        if (mutex.withLock { stopRequest != null }) {
            throw ImportCancelledException()
        }
    }

    suspend fun isPauseRequested(): Boolean {
        return mutex.withLock { stopRequest == ActiveImportStopRequest.Pause }
    }

    private suspend fun requestStop(request: ActiveImportStopRequest) {
        val session = mutex.withLock {
            if (stopRequest == null) {
                stopRequest = request
            }
            scanSession
        }
        session?.cancel()
    }
}

internal enum class ActiveImportStopRequest {
    Cancel,
    Pause,
}

internal fun requiresOneDriveResync(items: List<OneDriveDeltaItem>): Boolean {
    return items.any { item ->
        !item.deleted && !item.isDir && item.path == null
    }
}

internal fun OneDriveDeltaItem.isSupportedMusicFile(): Boolean {
    if (isDir || deleted || mimeType.isVideoMimeType()) return false
    val fileName = name ?: path?.substringAfterLast('/') ?: return false
    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return extension in supportedMusicExtensions
}

internal fun OneDriveDeltaItem.toStorageEntry(storageId: Long): StorageEntry {
    val canonicalPath = requireNotNull(path) {
        "OneDrive delta item $remoteId has no path"
    }
    return StorageEntry(
        storageId = StorageId(storageId),
        name = name ?: canonicalPath.substringAfterLast('/'),
        path = canonicalPath,
        size = size,
        isDir = isDir,
        remoteId = remoteId,
        parentRemoteId = parentRemoteId,
        mimeType = mimeType,
        etag = etag,
        ctag = ctag,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
    )
}

internal fun WebDavSyncItem.toStorageEntry(storageId: Long): StorageEntry {
    return StorageEntry(
        storageId = StorageId(storageId),
        name = name ?: normalizeRemotePath(path).substringAfterLast('/'),
        path = normalizeRemotePath(path),
        size = size,
        isDir = isDir,
        remoteId = null,
        parentRemoteId = null,
        mimeType = mimeType,
        etag = etag,
        ctag = null,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
    )
}

internal fun webDavMetadataModeFor(
    hasExistingRoot: Boolean,
    requestedMode: MetadataScanMode,
): MetadataScanMode {
    return if (hasExistingRoot && requestedMode != MetadataScanMode.Full) {
        MetadataScanMode.Fast
    } else {
        requestedMode
    }
}

private data class WebDavRevisionKey(
    val etag: String,
    val sizeBytes: Long?,
)

internal fun matchWebDavMoves(
    liveEntries: List<StorageEntry>,
    deletedExisting: List<SourceItemEntity>,
    occupiedLivePaths: Set<String> = emptySet(),
): Map<String, SourceItemEntity> {
    val deletedByRevision = deletedExisting
        .mapNotNull { item ->
            val etag = item.etag?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            WebDavRevisionKey(etag, item.sizeBytes) to item
        }
        .groupBy({ it.first }, { it.second })
    val liveByRevision = liveEntries
        .mapNotNull { entry ->
            val etag = entry.etag?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val path = normalizeRemotePath(entry.path)
            WebDavRevisionKey(etag, entry.size?.toLongOrNull()) to path
        }
        .groupBy({ it.first }, { it.second })
    return deletedByRevision.mapNotNull { (revision, deleted) ->
        val livePaths = liveByRevision[revision].orEmpty()
        if (deleted.size != 1 || livePaths.size != 1) return@mapNotNull null
        val livePath = livePaths.single()
        val previous = deleted.single()
        if (livePath in occupiedLivePaths || previous.canonicalPath == livePath) {
            return@mapNotNull null
        }
        livePath to previous
    }.toMap()
}

internal fun prepareMusicEntries(
    storageId: Long,
    entries: List<StorageEntry>,
): List<StorageEntry> {
    return entries
        .asSequence()
        .filter { it.storageId.value == storageId }
        .filter(::isSupportedMusicEntry)
        .distinctBy { normalizeRemotePath(it.path) }
        .sortedBy { normalizeRemotePath(it.path) }
        .toList()
}

internal fun prepareDiscoveredMusicEntries(
    storageId: Long,
    rootPath: String,
    rules: LibrarySyncScanRules,
    seenPaths: MutableSet<String>,
    entries: List<StorageEntry>,
): List<StorageEntry> {
    return prepareMusicEntries(storageId, entries)
        .filter { it.isAllowedByScanRules(rootPath, rules) }
        .filter { seenPaths.add(normalizeRemotePath(it.path)) }
}

internal fun StorageEntry.isAllowedByScanRules(
    rootPath: String,
    rules: LibrarySyncScanRules,
): Boolean {
    val relativeSegments = relativePathSegments(rootPath, path)
    if (relativeSegments.isEmpty()) return false
    val directorySegments = relativeSegments.dropLast(1)
    if (!rules.scanSubdirectories && directorySegments.isNotEmpty()) return false
    if (rules.ignoreHiddenFiles && relativeSegments.any { segment -> segment.startsWith(".") }) {
        return false
    }
    if (directorySegments.any { segment -> segment in rules.ignoredDirectoryNames }) {
        return false
    }
    return true
}

private fun relativePathSegments(rootPath: String, path: String): List<String> {
    val normalizedRoot = normalizeRemotePath(rootPath).trimEnd('/')
    val normalizedPath = normalizeRemotePath(path)
    val relative = when {
        normalizedRoot.isBlank() || normalizedRoot == "/" -> normalizedPath.trimStart('/')
        normalizedPath == normalizedRoot -> ""
        normalizedPath.startsWith("$normalizedRoot/") -> normalizedPath.removePrefix("$normalizedRoot/")
        else -> normalizedPath.trimStart('/')
    }
    return relative
        .split('/')
        .map(String::trim)
        .filter(String::isNotEmpty)
}

private fun validateImportSettings(
    metadataConcurrency: UInt,
    importBatchSize: Int,
) {
    require(metadataConcurrency in 1u..16u) {
        "metadata concurrency must be between 1 and 16"
    }
    require(importBatchSize in 1..MAX_IMPORT_BATCH_SIZE) {
        "import batch size must be between 1 and $MAX_IMPORT_BATCH_SIZE"
    }
}

internal data class RemoteLibraryImportPlan(
    val changedEntries: List<StorageEntry>,
    val metadataEntries: List<StorageEntry>,
    val changedItems: List<SourceItemEntity>,
    val unchangedFileIds: List<Long>,
    val unreadableEntries: List<StorageEntry>,
    val changedCount: Int,
    val unchangedCount: Int,
    val addedCount: Int,
    val modifiedCount: Int,
    val renamedCount: Int,
    val metadataSkippedCount: Int,
    val unreadableChangedCount: Int,
)

internal fun shouldLookupPluginArtwork(hasEmbeddedArtwork: Boolean?): Boolean =
    hasEmbeddedArtwork == false

internal data class CompleteSnapshotBatchPlan(
    val entriesToImport: List<StorageEntry>,
    val matchedExistingIds: Set<Long>,
    val unchangedCount: Int,
)

internal fun planCompleteSnapshotBatch(
    entries: List<StorageEntry>,
    existingByPath: Map<String, SourceItemSignature>,
    existingByRemoteId: Map<String, SourceItemSignature> = emptyMap(),
): CompleteSnapshotBatchPlan {
    val entriesToImport = mutableListOf<StorageEntry>()
    val matchedExistingIds = mutableSetOf<Long>()
    var unchangedCount = 0
    entries.forEach { entry ->
        val canonicalPath = normalizeRemotePath(entry.path)
        val previous = entry.remoteId?.let(existingByRemoteId::get)
            ?: existingByPath[canonicalPath]
        if (previous != null) {
            matchedExistingIds += previous.id
        }
        val sameRemoteIdentity = previous?.providerItemId == null ||
            entry.remoteId == null ||
            previous.providerItemId == entry.remoteId
        if (
            previous != null &&
            sameRemoteIdentity &&
            previous.canonicalPath == canonicalPath &&
            previous.hasSameSourceContent(entry)
        ) {
            unchangedCount += 1
        } else {
            entriesToImport += entry
        }
    }
    return CompleteSnapshotBatchPlan(
        entriesToImport = entriesToImport,
        matchedExistingIds = matchedExistingIds,
        unchangedCount = unchangedCount,
    )
}

internal data class ImportFailureDetail(
    val path: String,
    val errorType: String,
    val message: String,
)

internal fun planRemoteLibraryImport(
    storageId: Long,
    libraryRootId: Long,
    scanId: String,
    now: Long,
    entries: List<StorageEntry>,
    existing: Map<String?, SourceItemEntity>,
    existingByRemoteId: Map<String, SourceItemEntity> = emptyMap(),
    movedExistingByPath: Map<String, SourceItemEntity> = emptyMap(),
): RemoteLibraryImportPlan {
    val changedEntries = mutableListOf<StorageEntry>()
    val metadataEntries = mutableListOf<StorageEntry>()
    val changedItems = mutableListOf<SourceItemEntity>()
    val unchangedFileIds = mutableListOf<Long>()
    val unreadableEntries = mutableListOf<StorageEntry>()
    var changedCount = 0
    var unchangedCount = 0
    var addedCount = 0
    var modifiedCount = 0
    var renamedCount = 0
    var metadataSkippedCount = 0
    var unreadableChangedCount = 0

    entries.forEach { entry ->
        val canonicalPath = normalizeRemotePath(entry.path)
        val movedPrevious = movedExistingByPath[canonicalPath]
        val previous = existing[canonicalPath]
            ?: entry.remoteId?.let(existingByRemoteId::get)
            ?: movedPrevious
        val sameRemoteIdentity = previous?.providerItemId == null ||
            entry.remoteId == null ||
            previous.providerItemId == entry.remoteId
        val sameCanonicalPath = previous?.canonicalPath == canonicalPath
        if (
            previous != null &&
            previous.isDeleted &&
            sameRemoteIdentity &&
            sameCanonicalPath &&
            previous.hasSameSourceContent(entry)
        ) {
            buildSourceItemEntity(
                entry = entry,
                libraryRootId = libraryRootId,
                scanId = scanId,
                now = now,
                existing = previous,
            )?.let(changedItems::add)
            changedCount += 1
            modifiedCount += 1
            metadataSkippedCount += 1
            return@forEach
        }
        if (previous != null && sameRemoteIdentity && sameCanonicalPath && previous.hasSameSourceContent(entry)) {
            unchangedFileIds.add(previous.id)
            unchangedCount += 1
            metadataSkippedCount += 1
            return@forEach
        }
        if (
            previous != null &&
            (
                movedPrevious === previous ||
                    previous.providerItemId != null && previous.providerItemId == entry.remoteId
            ) &&
            previous.hasSameSourceRevision(entry)
        ) {
            buildSourceItemEntity(
                entry = entry,
                libraryRootId = libraryRootId,
                scanId = scanId,
                now = now,
                existing = previous,
            )?.let(changedItems::add)
            changedCount += 1
            renamedCount += 1
            metadataSkippedCount += 1
            return@forEach
        }

        changedCount += 1
        if (previous == null) {
            addedCount += 1
        } else {
            modifiedCount += 1
        }
        changedEntries.add(entry)
        val sourceItem = buildSourceItemEntity(
            entry = entry,
            libraryRootId = libraryRootId,
            scanId = scanId,
            now = now,
            existing = previous,
        )
        if (sourceItem == null) {
            unreadableChangedCount += 1
            unreadableEntries.add(entry)
        } else {
            changedItems.add(sourceItem)
        }
        val size = entry.size
        if (sourceItem != null && (size == null || size == 0uL)) {
            unreadableChangedCount += 1
            unreadableEntries.add(entry)
        } else if (sourceItem != null) {
            metadataEntries.add(entry)
        }
    }

    return RemoteLibraryImportPlan(
        changedEntries = changedEntries,
        metadataEntries = metadataEntries,
        changedItems = changedItems,
        unchangedFileIds = unchangedFileIds,
        unreadableEntries = unreadableEntries,
        changedCount = changedCount,
        unchangedCount = unchangedCount,
        addedCount = addedCount,
        modifiedCount = modifiedCount,
        renamedCount = renamedCount,
        metadataSkippedCount = metadataSkippedCount,
        unreadableChangedCount = unreadableChangedCount,
    )
}

internal fun buildImportFailureDetails(
    plan: RemoteLibraryImportPlan,
    metadataResults: List<RemoteMetadataResult>,
): List<ImportFailureDetail> {
    val details = mutableListOf<ImportFailureDetail>()
    val metadataEntriesByPath = plan.metadataEntries.associateBy { normalizeRemotePath(it.path) }
    val returnedPaths = metadataResults.map { normalizeRemotePath(it.entry.path) }.toSet()

    plan.unreadableEntries.forEach { entry ->
        val path = normalizeRemotePath(entry.path)
        details += ImportFailureDetail(
            path = path,
            errorType = ImportFailureTypes.UnreadableEntry,
            message = "$path：文件不可读或大小无效",
        )
    }
    plan.metadataEntries
        .filter { entry -> normalizeRemotePath(entry.path) !in returnedPaths }
        .forEach { entry ->
            val path = normalizeRemotePath(entry.path)
            details += ImportFailureDetail(
                path = path,
                errorType = ImportFailureTypes.MetadataMissing,
                message = "$path：元数据读取无返回结果",
            )
        }
    metadataResults
        .filter { result -> result.metadata == null }
        .forEach { result ->
            val path = normalizeRemotePath(result.entry.path)
            val entry = metadataEntriesByPath[path]
            details += ImportFailureDetail(
                path = normalizeRemotePath(entry?.path ?: result.entry.path),
                errorType = ImportFailureTypes.MetadataReadFailed,
                message = "$path：${result.error?.takeIf(String::isNotBlank) ?: "元数据读取失败"}",
            )
        }

    return details.distinctBy { detail -> detail.errorType to detail.path }
}

private fun List<ImportFailureDetail>.summaryOrNull(): String? {
    if (isEmpty()) return null
    val head = take(MAX_FAILURE_SUMMARY_ITEMS)
        .joinToString("；") { detail -> detail.message }
    val suffix = if (size > MAX_FAILURE_SUMMARY_ITEMS) {
        "；另有 ${size - MAX_FAILURE_SUMMARY_ITEMS} 个失败"
    } else {
        ""
    }
    return (head + suffix).take(MAX_IMPORT_JOB_ERROR_MESSAGE_LENGTH)
}

internal fun buildTrackEntity(
    entry: StorageEntry,
    metadata: RemoteMetadata,
    sourceItem: SourceItemEntity,
    now: Long,
    existingTrack: TrackEntity? = null,
    albumId: Long? = null,
    respectMetadataLock: Boolean = true,
    preserveExistingMetadata: Boolean = false,
): TrackEntity {
    val scannedTrack = TrackEntity(
        id = existingTrack?.id
            ?: stableTrackId(entry.storageId.value, normalizeRemotePath(entry.path)),
        title = metadata.title?.takeIf { it.isNotBlank() }
            ?: sourceItem.displayName.substringBeforeLast('.'),
        sortTitle = null,
        albumId = albumId,
        albumArtist = metadata.albumArtist,
        composer = metadata.composer,
        comment = metadata.comment,
        grouping = metadata.grouping,
        durationMs = metadata.durationMs.toLongOrNull(),
        discNumber = metadata.discNumber?.toInt(),
        discTotal = metadata.discTotal?.toInt(),
        trackNumber = metadata.trackNumber?.toInt(),
        trackTotal = metadata.trackTotal?.toInt(),
        year = metadata.date?.yearPrefix(),
        date = metadata.date,
        sampleRate = metadata.sampleRate?.toInt(),
        bitRate = (metadata.audioBitrate ?: metadata.overallBitrate)?.toInt(),
        bitsPerSample = metadata.bitDepth?.toInt(),
        channels = metadata.channels?.toInt(),
        channelLayout = metadata.channelLayout,
        codec = metadata.codec,
        container = metadata.container,
        lossless = metadata.lossless,
        createdAt = existingTrack?.createdAt ?: now,
        updatedAt = now,
        lastPlayedAt = existingTrack?.lastPlayedAt,
        artist = metadata.artist,
        lyricist = metadata.lyricist,
        conductor = metadata.conductor,
        copyright = metadata.copyright,
        publisher = metadata.publisher,
        originalReleaseDate = metadata.originalReleaseDate,
        bpm = metadata.bpm,
        musicalKey = metadata.musicalKey,
        isrc = metadata.isrc,
        musicBrainzRecordingId = metadata.musicbrainzRecordingId,
        musicBrainzTrackId = metadata.musicbrainzTrackId,
        musicBrainzReleaseId = metadata.musicbrainzReleaseId,
        musicBrainzReleaseGroupId = metadata.musicbrainzReleaseGroupId,
        musicBrainzArtistId = metadata.musicbrainzArtistId,
        musicBrainzReleaseArtistId = metadata.musicbrainzReleaseArtistId,
        musicBrainzWorkId = metadata.musicbrainzWorkId,
        replayGainTrackGain = metadata.replayGainTrackGain,
        replayGainTrackPeak = metadata.replayGainTrackPeak,
        replayGainAlbumGain = metadata.replayGainAlbumGain,
        replayGainAlbumPeak = metadata.replayGainAlbumPeak,
        metadataSource = TrackMetadataSources.File,
        metadataLocked = false,
    )
    if (preserveExistingMetadata && existingTrack != null) {
        return existingTrack.copy(updatedAt = now)
    }
    if (!respectMetadataLock || existingTrack?.metadataLocked != true) return scannedTrack

    return scannedTrack.copy(
        title = existingTrack.title,
        sortTitle = existingTrack.sortTitle,
        albumId = existingTrack.albumId,
        albumArtist = existingTrack.albumArtist,
        composer = existingTrack.composer,
        comment = existingTrack.comment,
        grouping = existingTrack.grouping,
        discNumber = existingTrack.discNumber,
        discTotal = existingTrack.discTotal,
        trackNumber = existingTrack.trackNumber,
        trackTotal = existingTrack.trackTotal,
        year = existingTrack.year,
        date = existingTrack.date,
        artist = existingTrack.artist,
        lyricist = existingTrack.lyricist,
        conductor = existingTrack.conductor,
        copyright = existingTrack.copyright,
        publisher = existingTrack.publisher,
        originalReleaseDate = existingTrack.originalReleaseDate,
        bpm = existingTrack.bpm,
        musicalKey = existingTrack.musicalKey,
        isrc = existingTrack.isrc,
        musicBrainzRecordingId = existingTrack.musicBrainzRecordingId,
        musicBrainzTrackId = existingTrack.musicBrainzTrackId,
        musicBrainzReleaseId = existingTrack.musicBrainzReleaseId,
        musicBrainzReleaseGroupId = existingTrack.musicBrainzReleaseGroupId,
        musicBrainzArtistId = existingTrack.musicBrainzArtistId,
        musicBrainzReleaseArtistId = existingTrack.musicBrainzReleaseArtistId,
        musicBrainzWorkId = existingTrack.musicBrainzWorkId,
        metadataSource = existingTrack.metadataSource,
        metadataLocked = true,
        metadataSourceId = existingTrack.metadataSourceId,
        metadataExternalId = existingTrack.metadataExternalId,
        metadataAppliedAt = existingTrack.metadataAppliedAt,
    )
}

internal fun buildTrackSourceRefEntity(
    track: TrackEntity,
    sourceItem: SourceItemEntity,
    metadata: RemoteMetadata,
    now: Long,
    existingRef: TrackSourceRefEntity? = null,
    role: String = existingRef?.role ?: TrackSourceRoles.Primary,
    matchMethod: String = existingRef?.matchMethod ?: TrackMatchMethods.SourceIdentity,
    matchConfidence: Int = existingRef?.matchConfidence ?: MATCH_CONFIDENCE_EXACT,
): TrackSourceRefEntity {
    return TrackSourceRefEntity(
        trackId = track.id,
        sourceItemId = sourceItem.id,
        role = role,
        matchMethod = matchMethod,
        matchConfidence = matchConfidence,
        isPreferred = existingRef?.isPreferred ?: true,
        isAvailable = !sourceItem.isDeleted,
        isDownloaded = existingRef?.isDownloaded ?: false,
        playable = true,
        downloadable = true,
        codec = metadata.codec ?: track.codec,
        container = metadata.container ?: track.container,
        bitRate = (metadata.audioBitrate ?: metadata.overallBitrate)?.toInt() ?: track.bitRate,
        sampleRate = metadata.sampleRate?.toInt() ?: track.sampleRate,
        bitsPerSample = metadata.bitDepth?.toInt() ?: track.bitsPerSample,
        channels = metadata.channels?.toInt() ?: track.channels,
        lossless = metadata.lossless ?: track.lossless,
        createdAt = existingRef?.createdAt ?: now,
        updatedAt = now,
        hasEmbeddedArtwork = metadata.hasEmbeddedArtwork,
        embeddedLyricsKind = metadata.embeddedLyricsKind,
    )
}

internal fun buildArtworkEntity(
    trackId: Long,
    albumId: Long?,
    artwork: RemoteArtwork,
): ArtworkEntity {
    return ArtworkEntity(
        trackId = if (albumId == null) trackId else null,
        albumId = albumId,
        contentHash = artwork.contentHash,
        localPath = artwork.localPath,
        thumbnailPath = artwork.thumbnailPath,
        width = artwork.width?.toInt(),
        height = artwork.height?.toInt(),
        mimeType = artwork.mimeType,
        pictureType = artwork.pictureType,
    )
}

internal fun buildLyricsEntity(
    trackId: Long,
    metadata: RemoteMetadata,
    now: Long,
): LyricsEntity? {
    val embedded = metadata.lyrics ?: return null
    val isTtml = metadata.embeddedLyricsKind == "Ttml" ||
        embedded.content.contains("http://www.w3.org/ns/ttml")
    val isWordTimed = metadata.embeddedLyricsKind == "WordTimed"
    val synchronized = embedded.synchronized || isTtml || isWordTimed
    return LyricsEntity(
        trackId = trackId,
        format = when {
            isTtml -> "TTML"
            synchronized -> "LRC"
            else -> "TEXT"
        },
        language = embedded.language,
        synchronized = synchronized,
        content = embedded.content,
        sourcePath = "embedded",
        updatedAt = now,
        sourceKind = when {
            isTtml -> "EmbeddedTtml"
            isWordTimed -> "EmbeddedWordTimed"
            else -> "EmbeddedPlain"
        },
    )
}

internal fun buildRawMetadataEntities(
    trackId: Long,
    metadata: RemoteMetadata,
): List<RawMetadataEntity> {
    return metadata.rawMetadata.map { raw ->
        RawMetadataEntity(
            trackId = trackId,
            tagKey = raw.key,
            value = raw.value,
            locale = raw.locale,
            description = raw.description,
        )
    }
}

private fun buildSourceItemEntity(
    entry: StorageEntry,
    libraryRootId: Long,
    scanId: String,
    now: Long,
    existing: SourceItemEntity?,
): SourceItemEntity? {
    val size = entry.size
    if (size != null && size > Long.MAX_VALUE.toULong()) return null
    val canonicalPath = normalizeRemotePath(entry.path)
    val fileName = entry.name.ifBlank { canonicalPath.substringAfterLast('/').ifBlank { canonicalPath } }
    return SourceItemEntity(
        id = existing?.id ?: 0,
        sourceAccountId = entry.storageId.value,
        libraryRootId = libraryRootId,
        itemType = SourceItemTypes.Track,
        providerItemId = entry.remoteId,
        parentProviderItemId = entry.parentRemoteId,
        canonicalPath = canonicalPath,
        displayPath = canonicalPath,
        displayName = fileName,
        mimeType = entry.mimeType,
        sizeBytes = size?.toLong(),
        etag = entry.etag,
        revision = entry.ctag,
        createdAtRemote = entry.createdAt,
        modifiedAtRemote = entry.modifiedAt,
        contentHash = null,
        audioFingerprint = null,
        isDeleted = false,
        firstSyncedAt = existing?.firstSyncedAt ?: now,
        lastSyncedAt = now,
        lastSeenScanId = scanId,
    )
}

private fun SourceItemEntity.hasSameSourceContent(entry: StorageEntry): Boolean {
    val size = entry.size?.toLongOrNull()
    if (sizeBytes != size) return false
    val entryEtag = entry.etag
    return if (!entryEtag.isNullOrBlank() && !etag.isNullOrBlank()) {
        etag == entryEtag
    } else {
        modifiedAtRemote != null && entry.modifiedAt != null && modifiedAtRemote == entry.modifiedAt
    }
}

private fun SourceItemSignature.hasSameSourceContent(entry: StorageEntry): Boolean {
    val size = entry.size?.toLongOrNull()
    if (sizeBytes != size) return false
    val entryEtag = entry.etag
    return if (!entryEtag.isNullOrBlank() && !etag.isNullOrBlank()) {
        etag == entryEtag
    } else {
        modifiedAtRemote != null && entry.modifiedAt != null && modifiedAtRemote == entry.modifiedAt
    }
}

private fun SourceItemEntity.hasSameSourceRevision(entry: StorageEntry): Boolean {
    val entryEtag = entry.etag
    val entryRevision = entry.ctag
    return (!entryEtag.isNullOrBlank() && etag == entryEtag) ||
        (!entryRevision.isNullOrBlank() && revision == entryRevision)
}

internal fun isSupportedMusicEntry(entry: StorageEntry): Boolean {
    if (entry.isDir || entry.mimeType.isVideoMimeType()) return false
    return isSupportedMusicPath(entry.name.ifBlank { entry.path })
}

internal fun isSupportedMusicPath(path: String): Boolean {
    val extension = path
        .substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
    return extension in supportedMusicExtensions
}

private fun String?.isVideoMimeType(): Boolean {
    return this
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        ?.startsWith("video/") == true
}

private fun escapeLikePattern(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
}

internal fun normalizeRemotePath(path: String): String {
    if (path.isBlank()) return "/"
    val normalized = path.replace('\\', '/')
    return if (normalized.startsWith('/')) normalized else "/$normalized"
}

internal fun String.toLegacyAndroidPrimaryStoragePath(): String? {
    return when {
        this == "/" -> ANDROID_PRIMARY_STORAGE_PATH
        startsWith("$ANDROID_PRIMARY_STORAGE_PATH/") -> null
        else -> "$ANDROID_PRIMARY_STORAGE_PATH$this"
    }
}

private const val ANDROID_PRIMARY_STORAGE_PATH = "/storage/emulated/0"

internal fun stableTrackId(storageId: Long, canonicalPath: String): Long {
    var hash = -3_750_763_034_362_895_579L
    val value = "track:$storageId:${normalizeRemotePath(canonicalPath)}"
    value.forEach { ch ->
        hash = hash xor ch.code.toLong()
        hash *= 1_099_511_628_211L
    }
    val positive = hash and Long.MAX_VALUE
    return if (positive == 0L) 1L else positive
}

private fun String.yearPrefix(): Int? {
    if (length < 4) return null
    return take(4).toIntOrNull()
}

private fun RemoteMetadata.trackArtists(parsing: MetadataParsingSettings): List<String> {
    return artists
        .ifEmpty { listOfNotNull(artist) }
        .flatMap { value ->
            splitMetadataNames(
                value = value,
                separators = parsing.artistSeparators,
                protectedNames = parsing.artistProtectedNames,
                ignoreCase = parsing.ignoreTagCase,
            )
        }
        .filter(String::isNotEmpty)
        .distinctBy(::normalizeMetadataName)
}

private fun RemoteMetadata.albumArtists(parsing: MetadataParsingSettings): List<String> =
    splitMetadataNames(
        value = albumArtist.orEmpty(),
        separators = parsing.artistSeparators,
        protectedNames = parsing.artistProtectedNames,
        ignoreCase = parsing.ignoreTagCase,
    ).distinctBy(::normalizeMetadataName)

private fun RemoteMetadata.genres(parsing: MetadataParsingSettings): List<String> =
    splitMetadataNames(
        value = genre.orEmpty(),
        separators = parsing.genreSeparators,
        protectedNames = parsing.genreProtectedNames,
        ignoreCase = parsing.ignoreTagCase,
    ).distinctBy(::normalizeMetadataName)

internal fun splitMetadataNames(
    value: String,
    separators: String,
    protectedNames: String,
    ignoreCase: Boolean,
): List<String> {
    if (value.isBlank()) return emptyList()
    val protectedRanges = protectedNames.lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .flatMap { protected ->
            sequence {
                var start = 0
                while (start < value.length) {
                    val index = value.indexOf(protected, startIndex = start, ignoreCase = ignoreCase)
                    if (index < 0) break
                    yield(index until index + protected.length)
                    start = index + protected.length.coerceAtLeast(1)
                }
            }
        }
        .toList()
    val result = mutableListOf<String>()
    val current = StringBuilder()
    value.forEachIndexed { index, char ->
        if (char in separators && protectedRanges.none { range -> index in range }) {
            current.toString().trim().takeIf(String::isNotEmpty)?.let(result::add)
            current.clear()
        } else {
            current.append(char)
        }
    }
    current.toString().trim().takeIf(String::isNotEmpty)?.let(result::add)
    return result
}

private fun RemoteMetadata.isShorterThan(minDurationMs: Long): Boolean {
    val durationMs = this.durationMs.toLongOrNull() ?: return false
    return durationMs in 0 until minDurationMs
}

private fun normalizeMetadataName(value: String): String {
    return value.trim().lowercase()
}

internal fun String?.normalizedTrackMatchKey(): String {
    return this?.trim()?.lowercase()?.replace(Regex("\\s+"), " ") ?: ""
}

internal fun String.hasTrackVersionToken(): Boolean {
    val value = normalizedTrackMatchKey()
    return versionTokens.any { token -> value.contains(token) }
}

private fun ULong.toLongOrNull(): Long? {
    if (this > Long.MAX_VALUE.toULong()) return null
    return toLong()
}

private inline fun Iterable<RemoteMetadata>.sumMetric(
    metric: (RemoteMetadata) -> ULong,
): Long {
    return fold(0L) { total, metadata ->
        total.saturatedAdd(metric(metadata).toLongOrNull() ?: Long.MAX_VALUE)
    }
}

private fun Long.saturatedAdd(value: Long): Long {
    return if (value > 0 && this > Long.MAX_VALUE - value) Long.MAX_VALUE else this + value
}

private object ImportJobStatus {
    const val PAUSED = "PAUSED"
    const val RUNNING = "RUNNING"
    const val COMPLETED = "COMPLETED"
    const val COMPLETED_WITH_ERRORS = "COMPLETED_WITH_ERRORS"
    const val CANCELLED = "CANCELLED"
    const val FAILED = "FAILED"
}

private object LibraryRootSyncStatus {
    const val PAUSED = "PAUSED"
    const val RUNNING = "RUNNING"
    const val SYNCED = "SYNCED"
    const val SYNCED_WITH_ERRORS = "SYNCED_WITH_ERRORS"
    const val CANCELLED = "CANCELLED"
    const val FAILED = "FAILED"
}

private object ImportFailureTypes {
    const val UnreadableEntry = "UNREADABLE_ENTRY"
    const val MetadataMissing = "METADATA_MISSING"
    const val MetadataReadFailed = "METADATA_READ_FAILED"
}

private val supportedMusicExtensions = setOf(
    "mp3",
    "flac",
    "m4a",
    "mp4",
    "aac",
    "ogg",
    "oga",
    "opus",
    "wav",
    "ape",
    "wv",
    "aif",
    "aiff",
)

internal const val DEFAULT_IMPORT_BATCH_SIZE = 200
internal const val DEFAULT_METADATA_CONCURRENCY = 8u
internal const val SYNC_MODE_WEBDAV_SYNC_TOKEN = "WEBDAV_SYNC_TOKEN"
internal const val SYNC_MODE_PARALLEL_FULL_SCAN = "PARALLEL_FULL_SCAN"
internal const val SYNC_MODE_LEGACY_FULL_SCAN_FALLBACK = "LEGACY_FULL_SCAN_FALLBACK"
private const val DEFAULT_DIRECTORY_CONCURRENCY = 4
internal const val DURATION_MATCH_TOLERANCE_MS = 2_000L
internal const val MATCH_CONFIDENCE_EXACT = 100
internal const val MATCH_CONFIDENCE_FINGERPRINT = 98
internal const val MATCH_CONFIDENCE_ISRC = 95
internal const val MATCH_CONFIDENCE_STRICT_METADATA = 80
private const val MAX_IMPORT_BATCH_SIZE = 500
private const val MAX_REMOTE_ID_QUERY_SIZE = 500
private const val MAX_SOURCE_ITEM_ID_QUERY_SIZE = 500
private const val AUTOMATIC_DUPLICATE_TRACK_POLICY = "MergeAcrossSources"
private const val MAX_DELTA_PAGES = 1_000
private const val MAX_DELTA_ITEMS = 100_000
private const val MAX_FAILURE_SUMMARY_ITEMS = 7
private const val MAX_IMPORT_JOB_ERROR_MESSAGE_LENGTH = 512
private const val SNAPSHOT_LIST_SEPARATOR = "|"
private const val GENERIC_DELTA_CURSOR_TYPE = "delta"
private const val WEBDAV_SYNC_TOKEN_CURSOR_TYPE = "webdav_sync_token"
private const val WEBDAV_CAPABILITY_CURSOR_TYPE = "webdav_sync_capability"
private const val WEBDAV_CAPABILITY_SUPPORTED = "sync_collection"
private const val WEBDAV_CAPABILITY_UNSUPPORTED = "unsupported"

private val versionTokens = listOf(
    "live",
    "remaster",
    "remix",
    "acoustic",
    "instrumental",
    "karaoke",
    "demo",
    "radio edit",
    "extended mix",
    "cover",
)

internal object TrackMatchMethods {
    const val SourceIdentity = "source_identity"
    const val ContentHash = "content_hash"
    const val AudioFingerprint = "audio_fingerprint"
    const val MusicBrainzRecordingId = "musicbrainz_recording_id"
    const val IsrcDuration = "isrc_duration"
    const val StrictMetadata = "strict_metadata"
}

internal object TrackSourceRoles {
    const val Primary = "primary"
    const val Alternate = "alternate"
}
