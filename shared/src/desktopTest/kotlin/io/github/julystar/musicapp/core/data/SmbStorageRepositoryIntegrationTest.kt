package io.github.julystar.musicapp.core.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.julystar.musicapp.core.data.security.CredentialStore
import io.github.julystar.musicapp.core.domain.model.SourceEditorDraft
import io.github.julystar.musicapp.core.domain.model.SourceEditorType
import io.github.julystar.musicapp.core.domain.model.StoredCredential
import io.github.julystar.musicapp.core.domain.model.toStorageRouteIdOrNull
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.AppDatabaseConstructor
import io.github.julystar.musicapp.database.SourceAccountEntity
import io.github.julystar.musicapp.domain.importing.RemoteLibraryImportCoordinator
import io.github.julystar.musicapp.domain.importing.RemoteLibraryImportRequest
import io.github.julystar.musicapp.source.storage.MetadataRepository
import io.github.julystar.musicapp.source.storage.RemoteScannerRepository
import io.github.julystar.musicapp.singleton.Bridge
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import uniffi.app_backend.StorageType

class SmbStorageRepositoryIntegrationTest {
    @Test
    fun smbAccountPersistsStructuredConfigurationAndSecureCredential() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder<AppDatabase> {
            AppDatabaseConstructor.initialize()
        }
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val credentialStore = InMemoryCredentialStore()
        val tempDir = Files.createTempDirectory("musicapp-smb-account").toFile()
        try {
            val bridge = Bridge(
                appDocumentDir = tempDir.absolutePath,
                appCacheDir = tempDir.absolutePath,
                toastRepository = ToastRepositoryImpl(scope),
            )
            val repository = StorageRepositoryImpl(
                bridge = bridge,
                scope = scope,
                sourceAccountDao = database.sourceAccountDao(),
                credentialStore = credentialStore,
            )
            withTimeout(5_000) {
                while (database.sourceAccountDao().get(1) == null) delay(10)
            }
            val draft = SourceEditorDraft(
                alias = "Studio NAS",
                username = "alice",
                secret = "secret-password",
                storageType = SourceEditorType.Smb,
                smbHost = "nas.local",
                smbPort = 1445,
                smbShare = "Music Share",
                smbRootPath = "Library",
                smbDomain = "STUDIO",
                smbRequireSigning = true,
                smbRequireEncryption = true,
            )

            val accountId = repository.upsertSource(draft)
            val storageId = assertNotNull(accountId.toStorageRouteIdOrNull())
            val entity = assertNotNull(database.sourceAccountDao().get(storageId))
            val credential = assertNotNull(credentialStore.load(storageId))
            val rustStorage = assertNotNull(
                repository.storageForRust(uniffi.app_backend.StorageId(storageId))
            )
            val editor = assertNotNull(repository.loadEditorState(storageId))

            assertEquals(ProviderTypes.Smb, entity.providerType)
            assertEquals("nas.local", entity.endpoint)
            assertTrue(entity.providerConfig.orEmpty().contains("\"share\":\"Music Share\""))
            assertFalse(entity.providerConfig.orEmpty().contains("secret-password"))
            assertEquals("alice", credential.username)
            assertEquals("secret-password", credential.secret)
            assertEquals(StorageType.SMB, rustStorage.typ)
            assertEquals(
                "smb://nas.local:1445/Music%20Share/Library" +
                    "?domain=STUDIO&signing=true&encryption=true",
                rustStorage.addr,
            )
            assertFalse(rustStorage.addr.contains("secret-password"))
            assertEquals("", editor.draft.secret)
            assertEquals("Music Share", editor.draft.smbShare)

            repository.upsertSource(
                draft.copy(
                    id = storageId,
                    secret = "",
                    smbPort = 445,
                    smbShare = "",
                    smbRootPath = "",
                    smbDomain = "",
                    smbRequireSigning = false,
                    smbRequireEncryption = false,
                )
            )
            assertEquals(
                "smb://nas.local",
                repository.storageForRust(uniffi.app_backend.StorageId(storageId))?.addr,
            )
            assertEquals(
                "",
                repository.loadEditorState(storageId)?.draft?.smbShare,
            )
            assertNull(database.sourceAccountDao().get(storageId)?.rootPath)

            repository.setAccountRootPath(accountId, "/Music Share")

            assertEquals(
                "smb://nas.local/Music%20Share",
                repository.storageForRust(uniffi.app_backend.StorageId(storageId))?.addr,
            )
            assertEquals(
                "Music Share",
                repository.loadEditorState(storageId)?.draft?.smbShare,
            )
            assertEquals("/Music Share", database.sourceAccountDao().get(storageId)?.rootPath)

            RemoteLibraryImportCoordinator(
                database = database,
                trackDao = database.trackDao(),
                metadataDao = database.metadataDao(),
                syncDao = database.syncDao(),
                metadataRepository = MetadataRepository(bridge, repository),
                remoteScannerRepository = RemoteScannerRepository(bridge, repository),
                storageRepository = repository,
            ).importCompleteSnapshot(
                RemoteLibraryImportRequest(
                    storageId = storageId,
                    selectedFolderRemoteId = null,
                    selectedFolderCanonicalPath = "/",
                    entries = emptyList(),
                    scanId = "smb-configuration-preservation",
                )
            )
            val accountAfterScanStarted = assertNotNull(database.sourceAccountDao().get(storageId))
            assertEquals("/Music Share", accountAfterScanStarted.rootPath)
            assertTrue(
                accountAfterScanStarted.providerConfig.orEmpty()
                    .contains("\"share\":\"Music Share\"")
            )

            repository.upsertSource(
                draft.copy(
                    id = storageId,
                    alias = "Renamed NAS",
                    secret = "",
                )
            )
            assertEquals("secret-password", credentialStore.load(storageId)?.secret)

            repository.removeByAccountId(accountId)
            assertNull(database.sourceAccountDao().get(storageId))
            assertNull(credentialStore.load(storageId))

            val legacyStorageId = 42L
            database.sourceAccountDao().upsert(
                SourceAccountEntity(
                    id = legacyStorageId,
                    providerType = ProviderTypes.Smb,
                    displayName = "Legacy NAS",
                    endpoint = "nas.local",
                    externalAccountId = null,
                    credentialRef = "storage-$legacyStorageId",
                    priority = 0,
                    enabled = true,
                    createdAt = 1L,
                    updatedAt = 1L,
                    rootPath = null,
                    providerConfig = null,
                )
            )

            val legacyEditor = assertNotNull(repository.loadEditorState(legacyStorageId))
            assertEquals(SourceEditorType.Smb, legacyEditor.draft.storageType)
            assertEquals("nas.local", legacyEditor.draft.smbHost)
            assertEquals(445, legacyEditor.draft.smbPort)
            assertEquals("", legacyEditor.draft.smbShare)
            assertEquals("", legacyEditor.draft.smbRootPath)
            assertEquals("", legacyEditor.draft.smbDomain)
            assertFalse(legacyEditor.draft.smbRequireSigning)
            assertFalse(legacyEditor.draft.smbRequireEncryption)
        } finally {
            scope.cancel()
            database.close()
            tempDir.deleteRecursively()
        }
    }
}

private class InMemoryCredentialStore : CredentialStore {
    private val values = mutableMapOf<Long, StoredCredential>()

    override suspend fun load(storageId: Long): StoredCredential? = values[storageId]

    override suspend fun save(
        storageId: Long,
        credential: StoredCredential,
    ) {
        values[storageId] = credential
    }

    override suspend fun delete(storageId: Long) {
        values.remove(storageId)
    }

    override suspend fun clear() {
        values.clear()
    }
}
