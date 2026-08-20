package io.github.julystar.musicapp.core.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.julystar.musicapp.core.data.security.CredentialStore
import io.github.julystar.musicapp.core.domain.model.SourceEditorDraft
import io.github.julystar.musicapp.core.domain.model.SourceEditorType
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.StoredCredential
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.AppDatabaseConstructor
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.SourceAccountDao
import io.github.julystar.musicapp.database.SourceAccountEntity
import io.github.julystar.musicapp.singleton.Bridge
import io.github.julystar.musicapp.source.api.OpenListAuthenticator
import io.github.julystar.musicapp.source.api.BuiltInSourceIds
import io.github.julystar.musicapp.source.api.LegacyStorageKind
import io.github.julystar.musicapp.source.api.LegacyStoragePlaybackResolver
import io.github.julystar.musicapp.source.api.MusicSource
import io.github.julystar.musicapp.source.api.MusicSourceDescriptor
import io.github.julystar.musicapp.source.api.MusicSourceRegistry
import io.github.julystar.musicapp.source.api.PlaybackResource
import io.github.julystar.musicapp.source.api.SourceAuthResult
import io.github.julystar.musicapp.source.api.SourceCapability
import io.github.julystar.musicapp.source.api.SourceConfiguration
import io.github.julystar.musicapp.source.api.SourceListFailureReason
import io.github.julystar.musicapp.source.api.SourceListResult
import io.github.julystar.musicapp.source.api.SourcePlaybackFailureReason
import io.github.julystar.musicapp.source.api.SourcePlaybackResult
import io.github.julystar.musicapp.source.storage.LegacyStorageLookup
import io.github.julystar.musicapp.service.playback.data.PlaybackResourceResolver
import io.github.julystar.musicapp.service.playback.data.SourceItemPropertyReader
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import uniffi.app_backend.ArgUpsertStorage
import uniffi.app_backend.EmbyLoginIdentity
import uniffi.app_backend.Music
import uniffi.app_backend.MusicId
import uniffi.app_backend.MusicMeta
import uniffi.app_backend.Storage
import uniffi.app_backend.StorageEntryLoc
import uniffi.app_backend.StorageId
import uniffi.app_backend.StorageType

class StorageCredentialSecurityIntegrationTest {
    @Test
    fun rustStorageLookupMapsOnlyFileProvidersWithoutChangingCredentials() = runBlocking {
        val fixture = CredentialSecurityFixture()
        try {
            val repository = fixture.repository()
            val fileProviders = listOf(
                Triple(201L, ProviderTypes.Local, StorageType.LOCAL),
                Triple(202L, ProviderTypes.WebDav, StorageType.WEBDAV),
                Triple(203L, ProviderTypes.OneDrive, StorageType.ONE_DRIVE),
                Triple(204L, ProviderTypes.Smb, StorageType.SMB),
                Triple(205L, ProviderTypes.OpenList, StorageType.OPEN_LIST),
            )
            val rejectedProviders = listOf(
                206L to ProviderTypes.Navidrome,
                207L to ProviderTypes.OpenSubsonic,
                208L to ProviderTypes.Emby,
                209L to "corrupt-provider",
            )
            (fileProviders.map { it.first to it.second } + rejectedProviders).forEach { (id, provider) ->
                seedAccount(fixture.database.sourceAccountDao(), id, provider)
                fixture.credentials.save(id, StoredCredential("user-$id", "secret-$id", false))
            }

            fileProviders.forEach { (id, _, expectedType) ->
                val storage = assertNotNull(repository.storageForRust(StorageId(id)))
                assertEquals(id, storage.id.value)
                assertEquals(expectedType, storage.typ)
            }
            rejectedProviders.forEach { (id, _) ->
                assertNull(repository.storageForRust(StorageId(id)))
            }
            (201L..209L).forEach { id ->
                assertTrue(
                    fixture.credentials.load(id)?.secret == "secret-$id",
                    "credential changed for storage $id",
                )
            }
        } finally {
            fixture.close()
        }
    }

    @Test
    fun allRemoteProviderAccountsPersistOnlyNonSensitiveFieldsInRoom() = runBlocking {
        val fixture = CredentialSecurityFixture()
        try {
            val repository = fixture.repository()
            repository.upsertStorage(storageArgument(101, StorageType.WEBDAV, PERSISTED_SECRET))
            repository.upsertStorage(storageArgument(102, StorageType.ONE_DRIVE, PERSISTED_TOKEN))
            repository.upsertSource(smbDraft(103, PERSISTED_AUTHORIZATION))
            repository.upsertOpenListSource(
                serverDraft(104, SourceEditorType.OpenList, PERSISTED_SECRET),
                PERSISTED_OTP,
            )
            repository.upsertSource(serverDraft(105, SourceEditorType.Navidrome, PERSISTED_SECRET))
            repository.upsertSource(serverDraft(106, SourceEditorType.OpenSubsonic, PERSISTED_SECRET))
            repository.upsertSource(serverDraft(107, SourceEditorType.Emby, PERSISTED_SECRET))

            val persistedAccounts = fixture.database.sourceAccountDao().listAll()
                .filter { it.id in 101L..107L }
            assertEquals(
                setOf(
                    ProviderTypes.WebDav,
                    ProviderTypes.OneDrive,
                    ProviderTypes.Smb,
                    ProviderTypes.OpenList,
                    ProviderTypes.Navidrome,
                    ProviderTypes.OpenSubsonic,
                    ProviderTypes.Emby,
                ),
                persistedAccounts.map { it.providerType }.toSet(),
            )
            assertTrue(persistedAccounts.all { it.credentialRef == "storage-${it.id}" })

            val queryableRoomValues = persistedAccounts.joinToString("\n") { account ->
                listOf(
                    account.displayName,
                    account.endpoint,
                    account.externalAccountId,
                    account.credentialRef,
                    account.rootPath,
                    account.providerConfig,
                ).joinToString("|")
            }
            FORBIDDEN_PERSISTED_VALUES.forEach { forbidden ->
                assertFalse(forbidden in queryableRoomValues)
            }
            assertTrue((101L..107L).all { fixture.credentials.load(it) != null })
        } finally {
            fixture.close()
        }
    }

    @Test
    fun resolvingPlaybackResourceDoesNotPersistItsMemoryOnlyUrl() = runBlocking {
        val fixture = CredentialSecurityFixture()
        try {
            val repository = fixture.repository()
            repository.upsertStorage(storageArgument(141, StorageType.WEBDAV, PERSISTED_SECRET))
            val source = object : MusicSource {
                override val descriptor = MusicSourceDescriptor(BuiltInSourceIds.WebDav, "WebDAV")
                override val capabilities = setOf(SourceCapability.Stream)
                override suspend fun authenticate(configuration: SourceConfiguration) =
                    SourceAuthResult.Success
                override suspend fun list(accountId: SourceAccountId, directoryId: String?) =
                    SourceListResult.Failure(SourceListFailureReason.Unavailable)
                override suspend fun resolvePlayback(mediaId: io.github.julystar.musicapp.core.domain.model.MediaId) =
                    SourcePlaybackResult.Success(PlaybackResource(PLAYBACK_URL))
            }
            val resolver = PlaybackResourceResolver(
                storageLookup = LegacyStorageLookup {
                    Storage(
                        id = StorageId(141),
                        addr = "https://storage-141.example",
                        alias = "Storage 141",
                        username = "",
                        password = "",
                        isAnonymous = true,
                        typ = StorageType.WEBDAV,
                        musicCount = 0u,
                    )
                },
                trackSourceRefDao = fixture.database.trackSourceRefDao(),
                sourceRegistry = MusicSourceRegistry(listOf(source)),
                legacyStoragePlaybackResolver = object : LegacyStoragePlaybackResolver {
                    override suspend fun resolve(
                        accountId: SourceAccountId,
                        path: String,
                        expectedStorageKind: LegacyStorageKind,
                    ) = SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unavailable)
                    override suspend fun release(uri: String) = Unit
                    override suspend fun releaseAll() = Unit
                },
                sourceItemPropertyReader = SourceItemPropertyReader.Empty,
            )

            val result = resolver.resolve(
                Music(
                    meta = MusicMeta(MusicId(1), "Track", null, emptyList()),
                    loc = StorageEntryLoc(StorageId(141), "/track.flac"),
                    cover = null,
                    lyric = null,
                )
            )

            assertTrue(result is SourcePlaybackResult.Success)
            assertTrue(result.resource.uri == PLAYBACK_URL)
            assertFalse(
                PLAYBACK_URL in fixture.database.sourceAccountDao().listAll().joinToString("\n")
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun replacingAndRemovingCredentialDropsThePreviousSecret() = runBlocking {
        val fixture = CredentialSecurityFixture()
        try {
            val repository = fixture.repository()
            repository.upsertStorage(storageArgument(111, StorageType.WEBDAV, OLD_SECRET))
            repository.upsertStorage(storageArgument(111, StorageType.WEBDAV, NEW_SECRET))

            assertTrue(NEW_SECRET == fixture.credentials.load(111)?.secret)
            assertFalse(OLD_SECRET == fixture.credentials.load(111)?.secret)

            repository.remove(StorageId(111))
            assertNull(fixture.credentials.load(111))
            assertNull(fixture.database.sourceAccountDao().get(111))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun daoFailureRestoresOrDeletesCredentialsForEverySavePath() = runBlocking {
        val fixture = CredentialSecurityFixture()
        try {
            val realDao = fixture.database.sourceAccountDao()
            val failingDao = FailingUpsertSourceAccountDao(
                realDao,
                setOf(121, 122, 123, 124, 125, 126),
            )
            val repository = fixture.repository(sourceAccountDao = failingDao)

            seedAccount(realDao, 121, ProviderTypes.WebDav)
            seedAccount(realDao, 123, ProviderTypes.Smb)
            seedAccount(realDao, 124, ProviderTypes.Navidrome)
            fixture.credentials.save(121, StoredCredential("old", OLD_SECRET, false))
            fixture.credentials.save(123, StoredCredential("old", OLD_SECRET, false))
            fixture.credentials.save(124, StoredCredential("old", OLD_SECRET, false))

            assertFailsWith<TestPersistenceFailure> {
                repository.upsertStorage(storageArgument(121, StorageType.WEBDAV, NEW_SECRET))
            }
            assertTrue(OLD_SECRET == fixture.credentials.load(121)?.secret)

            assertFailsWith<TestPersistenceFailure> {
                repository.upsertStorage(storageArgument(122, StorageType.WEBDAV, NEW_SECRET))
            }
            assertNull(fixture.credentials.load(122))

            assertFailsWith<TestPersistenceFailure> {
                repository.upsertSource(smbDraft(123, NEW_SECRET))
            }
            assertTrue(OLD_SECRET == fixture.credentials.load(123)?.secret)

            assertFailsWith<TestPersistenceFailure> {
                repository.upsertSource(serverDraft(124, SourceEditorType.Navidrome, NEW_SECRET))
            }
            assertTrue(OLD_SECRET == fixture.credentials.load(124)?.secret)

            assertFailsWith<TestPersistenceFailure> {
                repository.upsertSource(smbDraft(125, NEW_SECRET))
            }
            assertNull(fixture.credentials.load(125))

            assertFailsWith<TestPersistenceFailure> {
                repository.upsertSource(serverDraft(126, SourceEditorType.Navidrome, NEW_SECRET))
            }
            assertNull(fixture.credentials.load(126))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun rollbackFailureNeverMasksTheOriginalDaoFailure() = runBlocking {
        val fixture = CredentialSecurityFixture()
        try {
            val realDao = fixture.database.sourceAccountDao()
            seedAccount(realDao, 131, ProviderTypes.Navidrome)
            fixture.credentials.save(131, StoredCredential("old", OLD_SECRET, false))
            fixture.credentials.rejectedSecret = OLD_SECRET
            val repository = fixture.repository(
                sourceAccountDao = FailingUpsertSourceAccountDao(realDao, setOf(131)),
            )

            val failure = assertFailsWith<TestPersistenceFailure> {
                repository.upsertSource(serverDraft(131, SourceEditorType.Navidrome, NEW_SECRET))
            }

            assertEquals("source account persistence failed", failure.message)
        } finally {
            fixture.close()
        }
    }

    private fun storageArgument(id: Long, type: StorageType, secret: String) = ArgUpsertStorage(
        id = StorageId(id),
        addr = if (type == StorageType.ONE_DRIVE) "drive-$id" else "https://storage-$id.example",
        alias = "Storage $id",
        username = "listener",
        password = secret,
        isAnonymous = false,
        typ = type,
    )

    private fun smbDraft(id: Long, secret: String) = SourceEditorDraft(
        id = id,
        alias = "SMB $id",
        username = "listener",
        secret = secret,
        storageType = SourceEditorType.Smb,
        smbHost = "nas-$id.example",
        smbShare = "Music",
    )

    private fun serverDraft(id: Long, type: SourceEditorType, secret: String) = SourceEditorDraft(
        id = id,
        address = "https://server-$id.example",
        alias = "Server $id",
        username = "listener",
        secret = secret,
        storageType = type,
    )

    private suspend fun seedAccount(dao: SourceAccountDao, id: Long, providerType: String) {
        dao.upsert(
            SourceAccountEntity(
                id = id,
                providerType = providerType,
                displayName = "Existing $id",
                endpoint = "https://existing-$id.example",
                externalAccountId = null,
                credentialRef = "storage-$id",
                priority = 0,
                enabled = true,
                createdAt = 1,
                updatedAt = 1,
            )
        )
    }

    private companion object {
        const val PERSISTED_SECRET = "room-fixture-password-sensitive"
        const val PERSISTED_TOKEN = "room-fixture-token-sensitive"
        const val PERSISTED_OTP = "room-fixture-otp-sensitive"
        const val PERSISTED_AUTHORIZATION = "Bearer room-fixture-authorization-sensitive"
        const val PLAYBACK_URL =
            "http://127.0.0.1:45678/media/room-fixture-capability-sensitive/stream.flac"
        const val OLD_SECRET = "credential-old-sensitive"
        const val NEW_SECRET = "credential-new-sensitive"
        val FORBIDDEN_PERSISTED_VALUES = listOf(
            PERSISTED_SECRET,
            PERSISTED_TOKEN,
            PERSISTED_OTP,
            PERSISTED_AUTHORIZATION,
        )
    }
}

private class CredentialSecurityFixture {
    val database = Room.inMemoryDatabaseBuilder<AppDatabase> {
        AppDatabaseConstructor.initialize()
    }.setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.Default).build()
    val credentials = CredentialSecurityStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tempDir = Files.createTempDirectory("musicapp-credential-security").toFile()
    private val bridge = Bridge(
        appDocumentDir = tempDir.absolutePath,
        appCacheDir = tempDir.absolutePath,
        toastRepository = ToastRepositoryImpl(scope),
    )

    fun repository(
        sourceAccountDao: SourceAccountDao = database.sourceAccountDao(),
    ) = StorageRepositoryImpl(
        bridge = bridge,
        scope = scope,
        sourceAccountDao = sourceAccountDao,
        credentialStore = credentials,
        embyLogin = { _, username, _ ->
            EmbyLoginIdentity(
                accessToken = "room-fixture-token-sensitive",
                userId = username,
                serverId = "server-id",
                serverName = "Server",
            )
        },
        subsonicRequest = { _, _, _, _, _ ->
            """{"subsonic-response":{"status":"ok"}}"""
        },
        openListAuthenticator = OpenListAuthenticator { SourceAuthResult.Success },
    )

    fun close() {
        scope.cancel()
        database.close()
        tempDir.deleteRecursively()
    }
}

private class CredentialSecurityStore : CredentialStore {
    private val values = mutableMapOf<Long, StoredCredential>()
    var rejectedSecret: String? = null

    override suspend fun load(storageId: Long): StoredCredential? = values[storageId]

    override suspend fun save(storageId: Long, credential: StoredCredential) {
        if (credential.secret == rejectedSecret) error("credential rollback failed")
        values[storageId] = credential
    }

    override suspend fun delete(storageId: Long) {
        values.remove(storageId)
    }

    override suspend fun clear() {
        values.clear()
    }
}

private class FailingUpsertSourceAccountDao(
    private val delegate: SourceAccountDao,
    private val failingIds: Set<Long>,
) : SourceAccountDao by delegate {
    override suspend fun upsert(account: SourceAccountEntity): Long {
        if (account.id in failingIds) throw TestPersistenceFailure()
        return delegate.upsert(account)
    }
}

private class TestPersistenceFailure : RuntimeException("source account persistence failed")
