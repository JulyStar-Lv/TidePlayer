package io.github.julystar.musicapp.service.librarysync.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.julystar.musicapp.core.domain.model.MetadataScanMode
import io.github.julystar.musicapp.core.domain.model.storageSourceAccountId
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.AppDatabaseConstructor
import io.github.julystar.musicapp.database.LibraryRootEntity
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.SourceAccountEntity
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncController
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncFailure
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncRequest
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncResult
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncTask
import io.github.julystar.musicapp.source.server.RemoteServerLibrarySyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountScopedLibrarySyncControllerTest {
    @Test
    fun productionDispatcherUsesExactServerAccountAndAllPersistedOpenListRoots() = runTest {
        val database = Room.inMemoryDatabaseBuilder<AppDatabase> {
            AppDatabaseConstructor.initialize()
        }.setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.Default).build()
        try {
            val serverId = 101L
            val openListId = 102L
            database.sourceAccountDao().upsert(account(serverId, ProviderTypes.OpenSubsonic))
            database.sourceAccountDao().upsert(account(openListId, ProviderTypes.OpenList))
            database.libraryRootDao().upsert(root(openListId, "raw::first", "/First folder"))
            database.libraryRootDao().upsert(root(openListId, "raw::second", "/Second folder"))

            val remoteRequests = mutableListOf<String>()
            val fileSync = RecordingLibrarySyncController()
            val metadataProviders = mutableListOf<String>()
            val controller = AccountScopedLibrarySyncController.forTesting(
                sourceAccountDao = database.sourceAccountDao(),
                libraryRootDao = database.libraryRootDao(),
                librarySyncController = fileSync,
                remoteServerSync = { accountId ->
                    remoteRequests += accountId.value
                    RemoteServerLibrarySyncResult(
                        scanId = "server-scan",
                        scanned = 9,
                        added = 2,
                        modified = 3,
                        unchanged = 4,
                        deleted = 0,
                    )
                },
                metadataScanMode = { providerType ->
                    metadataProviders += providerType
                    MetadataScanMode.Standard
                },
            )

            assertEquals(
                expected = io.github.julystar.musicapp.service.librarysync.domain.SourceAccountLibrarySyncResult(
                    importedCount = 5,
                    skippedCount = 4,
                    failedCount = 0,
                ),
                actual = controller.sync(storageSourceAccountId(serverId)),
            )
            assertEquals(listOf("storage:$serverId"), remoteRequests)
            assertEquals(emptyList(), fileSync.requests)

            assertEquals(
                expected = io.github.julystar.musicapp.service.librarysync.domain.SourceAccountLibrarySyncResult(
                    importedCount = 2,
                    skippedCount = 4,
                    failedCount = 6,
                ),
                actual = controller.sync(storageSourceAccountId(openListId)),
            )
            assertEquals(
                listOf(
                    Triple("raw::first", "/First folder", "/First folder"),
                    Triple("raw::second", "/Second folder", "/Second folder"),
                ),
                fileSync.requests.map { request ->
                    Triple(
                        request.selectedFolderRemoteId,
                        request.selectedFolderCanonicalPath,
                        request.selectedFolderDisplayPath,
                    )
                },
            )
            assertEquals(listOf(ProviderTypes.OpenList, ProviderTypes.OpenList), metadataProviders)
        } finally {
            database.close()
        }
    }
}

private fun account(id: Long, providerType: String) = SourceAccountEntity(
    id = id,
    providerType = providerType,
    displayName = providerType,
    endpoint = "https://example.test",
    externalAccountId = null,
    credentialRef = "credential-$id",
    priority = 0,
    enabled = true,
    createdAt = 1,
    updatedAt = 1,
)

private fun root(accountId: Long, remoteId: String, path: String) = LibraryRootEntity(
    sourceAccountId = accountId,
    providerRootId = remoteId,
    canonicalPath = path,
    displayName = path,
    syncStatus = "PENDING",
    syncCursor = null,
    lastSyncAt = null,
    createdAt = 1,
    updatedAt = 1,
)

private class RecordingLibrarySyncController : LibrarySyncController {
    val requests = mutableListOf<LibrarySyncRequest>()
    override val recentTasks: Flow<List<LibrarySyncTask>> = emptyFlow()
    override fun observeFailures(taskId: String): Flow<List<LibrarySyncFailure>> = emptyFlow()
    override suspend fun syncFolder(request: LibrarySyncRequest): LibrarySyncResult {
        requests += request
        return LibrarySyncResult(
            scanId = "file-scan",
            selectedFolderId = requests.size.toLong(),
            scannedCount = 1,
            changedCount = 1,
            skippedCount = 2,
            importedCount = 1,
            failedCount = 3,
        )
    }
    override suspend fun pause(scanId: String) = false
    override suspend fun cancel(scanId: String) = false
    override suspend fun cancelAll() = Unit
    override suspend fun recoverInterruptedTasks() = 0
    override suspend fun resume(scanId: String): LibrarySyncResult? = null
    override suspend fun retry(scanId: String): LibrarySyncResult? = null
}
