package io.github.julystar.musicapp.service.librarysync.data

import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.MetadataScanMode
import io.github.julystar.musicapp.core.domain.model.metadataScanModeFor
import io.github.julystar.musicapp.core.domain.model.toStorageRouteIdOrNull
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.database.LibraryRootDao
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.SourceAccountDao
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncController
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncRequest
import io.github.julystar.musicapp.service.librarysync.domain.SourceAccountLibrarySyncController
import io.github.julystar.musicapp.service.librarysync.domain.SourceAccountLibrarySyncResult
import io.github.julystar.musicapp.source.server.RemoteServerLibrarySyncCoordinator
import io.github.julystar.musicapp.source.server.RemoteServerLibrarySyncResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException

internal class AccountScopedLibrarySyncController private constructor(
    private val sourceAccountDao: SourceAccountDao,
    private val libraryRootDao: LibraryRootDao,
    private val librarySyncController: LibrarySyncController,
    private val remoteServerSync: suspend (SourceAccountId) -> RemoteServerLibrarySyncResult,
    private val metadataScanMode: suspend (String) -> MetadataScanMode,
) : SourceAccountLibrarySyncController {
    constructor(
        sourceAccountDao: SourceAccountDao,
        libraryRootDao: LibraryRootDao,
        librarySyncController: LibrarySyncController,
        remoteServerSyncCoordinator: RemoteServerLibrarySyncCoordinator,
        settingsRepository: SettingsRepository,
    ) : this(
        sourceAccountDao = sourceAccountDao,
        libraryRootDao = libraryRootDao,
        librarySyncController = librarySyncController,
        remoteServerSync = { accountId -> remoteServerSyncCoordinator.sync(accountId) },
        metadataScanMode = { providerType ->
            settingsRepository.settings.first().metadataScanModeFor(
                isWebDav = providerType == ProviderTypes.WebDav || providerType == ProviderTypes.Smb,
            )
        },
    )

    companion object {
        internal fun forTesting(
            sourceAccountDao: SourceAccountDao,
            libraryRootDao: LibraryRootDao,
            librarySyncController: LibrarySyncController,
            remoteServerSync: suspend (SourceAccountId) -> RemoteServerLibrarySyncResult,
            metadataScanMode: suspend (String) -> MetadataScanMode,
        ) = AccountScopedLibrarySyncController(
            sourceAccountDao,
            libraryRootDao,
            librarySyncController,
            remoteServerSync,
            metadataScanMode,
        )
    }

    override suspend fun sync(accountId: SourceAccountId): SourceAccountLibrarySyncResult {
        val routeId = accountId.toStorageRouteIdOrNull()
            ?: error("source account must use a storage route")
        val account = sourceAccountDao.get(routeId)
            ?: error("source account does not exist")
        check(account.enabled) { "source account is disabled" }

        return when (account.providerType) {
            ProviderTypes.Navidrome,
            ProviderTypes.OpenSubsonic,
            ProviderTypes.Emby -> remoteServerSync(accountId).let { result ->
                SourceAccountLibrarySyncResult(
                    importedCount = result.added + result.modified,
                    skippedCount = result.unchanged,
                    failedCount = 0,
                )
            }
            ProviderTypes.Local,
            ProviderTypes.WebDav,
            ProviderTypes.OneDrive,
            ProviderTypes.Smb,
            ProviderTypes.OpenList -> syncFileAccount(accountId, routeId, account.providerType, account.rootPath)
            else -> error("source account provider cannot be synchronized")
        }
    }

    private suspend fun syncFileAccount(
        accountId: SourceAccountId,
        routeId: Long,
        providerType: String,
        configuredRootPath: String?,
    ): SourceAccountLibrarySyncResult {
        val persistedRoots = libraryRootDao.observeBySourceAccount(routeId).first()
        val roots = if (persistedRoots.isNotEmpty()) {
            persistedRoots.map { root ->
                SyncRoot(
                    remoteId = root.providerRootId,
                    canonicalPath = root.canonicalPath ?: root.providerRootId.orEmpty(),
                    displayPath = root.displayName,
                )
            }
        } else {
            configuredRootPath?.takeIf(String::isNotBlank)?.let { configured ->
                val canonicalPath = if (providerType == ProviderTypes.Smb) "/" else configured
                listOf(
                    SyncRoot(
                        remoteId = configured.takeIf { providerType == ProviderTypes.OpenList },
                        canonicalPath = canonicalPath,
                        displayPath = canonicalPath,
                    )
                )
            }.orEmpty()
        }
        check(roots.isNotEmpty() && roots.all { it.canonicalPath.isNotBlank() }) {
            "source account has no selected library root"
        }

        var imported = 0L
        var skipped = 0L
        var failed = 0L
        roots.forEach { root ->
            try {
                val result = librarySyncController.syncFolder(
                    LibrarySyncRequest(
                        accountId = accountId,
                        selectedFolderRemoteId = root.remoteId,
                        selectedFolderCanonicalPath = root.canonicalPath,
                        selectedFolderDisplayPath = root.displayPath,
                        metadataScanMode = metadataScanMode(providerType),
                    )
                )
                imported += result.importedCount
                skipped += result.skippedCount
                failed += result.failedCount
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                // A failed root must not prevent the remaining independent roots from scanning.
                failed += 1
            }
        }
        return SourceAccountLibrarySyncResult(
            importedCount = imported,
            skippedCount = skipped,
            failedCount = failed,
        )
    }
}

private data class SyncRoot(
    val remoteId: String?,
    val canonicalPath: String,
    val displayPath: String,
)
