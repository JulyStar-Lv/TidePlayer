package io.github.julystar.musicapp.core.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.julystar.musicapp.core.data.security.CredentialStore
import io.github.julystar.musicapp.core.domain.model.NeedsReauthenticationException
import io.github.julystar.musicapp.core.domain.model.SourceEditorDraft
import io.github.julystar.musicapp.core.domain.model.SourceEditorType
import io.github.julystar.musicapp.core.domain.model.SourceConnectionTestStatus
import io.github.julystar.musicapp.core.domain.model.StoredCredential
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.AppDatabaseConstructor
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.SourceAccountEntity
import io.github.julystar.musicapp.singleton.Bridge
import io.github.julystar.musicapp.source.api.EmbyProviderConfiguration
import io.github.julystar.musicapp.source.api.EmbyProviderConfigurationCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import uniffi.app_backend.EmbyLoginIdentity
import uniffi.app_backend.RemoteMusicException

class EmbyStorageRepositoryIntegrationTest {
    @Test
    fun newLoginPersistsTypedIdentityOnlyAndEditorRedactsSecret() = runBlocking {
        val fixture = EmbyFixture()
        try {
            val endpoints = mutableListOf<String>()
            val repository = fixture.repository(
                login = { endpoint, _, _ ->
                    endpoints += endpoint
                    if (endpoint == "https://emby.example") throw RemoteMusicException.Connectivity()
                    EmbyLoginIdentity("access-token", "user-1", "server-1", "Server")
                },
            )
            val accountId = repository.upsertSource(SourceEditorDraft(
                id = 42,
                address = "https://emby.example",
                alias = "Emby",
                username = "alice",
                secret = "password",
                storageType = SourceEditorType.Emby,
                secondaryBaseUrl = "https://emby-secondary.example",
            )).value.substringAfter(':').toLong()
            val entity = assertNotNull(fixture.database.sourceAccountDao().get(accountId))
            val credential = assertNotNull(fixture.credentials.load(accountId))
            assertEquals("user-1", entity.externalAccountId)
            assertEquals("access-token", credential.secret)
            assertEquals("server-1", EmbyProviderConfigurationCodec.decode(entity.providerConfig).serverId)
            assertEquals("Server", EmbyProviderConfigurationCodec.decode(entity.providerConfig).serverName)
            assertEquals(
                "https://emby-secondary.example",
                EmbyProviderConfigurationCodec.decode(entity.providerConfig).secondaryBaseUrl,
            )
            assertFalse(entity.providerConfig.orEmpty().contains("password"))
            assertFalse(entity.providerConfig.orEmpty().contains("access-token"))
            val editor = assertNotNull(repository.loadEditorState(accountId)).draft
            assertEquals("", editor.secret)
            assertEquals("https://emby-secondary.example", editor.secondaryBaseUrl)
            assertEquals(
                listOf("https://emby.example", "https://emby-secondary.example"),
                endpoints,
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun emptyPasswordVerifiesPersistedIdentityWithoutLoginAndPreservesStateOnFailure() = runBlocking {
        val fixture = EmbyFixture()
        try {
            fixture.seedExisting()
            var loginCalls = 0
            val requests = mutableListOf<Triple<String, String, String>>()
            val repository = fixture.repository(
                login = { _, _, _ -> loginCalls++; error("empty-password edit must not login") },
                request = { address, token, path, _ ->
                    requests += Triple(address, token, path)
                    """{"ServerId":null,"ServerName":"Updated Server","User":{"Id":"user-1","Name":"Verified Name","ServerId":"server-1","ServerName":""}}"""
                },
            )
            val id = repository.upsertSource(SourceEditorDraft(
                id = 42,
                address = "https://emby.example",
                alias = "Renamed",
                username = "edited-untrusted-name",
                externalAccountId = "wrong-user",
                secret = "",
                storageType = SourceEditorType.Emby,
                secondaryBaseUrl = "https://edited-secondary.example",
            )).value.substringAfter(':').toLong()
            assertEquals(42L, id)
            assertEquals(0, loginCalls)
            assertEquals(listOf(Triple("https://emby.example", "access-token", "Users/user-1")), requests)
            assertEquals("user-1", fixture.database.sourceAccountDao().get(42)?.externalAccountId)
            assertEquals("Verified Name", fixture.credentials.load(42)?.username)
            assertEquals("Updated Server", EmbyProviderConfigurationCodec.decode(
                fixture.database.sourceAccountDao().get(42)?.providerConfig,
            ).serverName)
            val updatedConfig = EmbyProviderConfigurationCodec.decode(
                fixture.database.sourceAccountDao().get(42)?.providerConfig,
            )
            assertEquals("server-1", updatedConfig.serverId)
            assertEquals("https://edited-secondary.example", updatedConfig.secondaryBaseUrl)
            assertEquals("", assertNotNull(repository.loadEditorState(42)).draft.secret)

            val before = fixture.database.sourceAccountDao().get(42)
            val mismatchRepository = fixture.repository(
                request = { _, _, _, _ -> """{"Id":"other-user","ServerId":"server-1"}""" },
            )
            assertFailsWith<IllegalArgumentException> {
                mismatchRepository.upsertSource(SourceEditorDraft(
                    id = 42,
                    address = "https://emby.example",
                    alias = "Should Not Save",
                    username = "edited",
                    secret = "",
                    storageType = SourceEditorType.Emby,
                ))
            }
            assertEquals(before, fixture.database.sourceAccountDao().get(42))
            assertEquals(StoredCredential("Verified Name", "access-token", false), fixture.credentials.load(42))

            val serverMismatchRepository = fixture.repository(
                request = { _, _, _, _ -> """{"Id":"user-1","ServerId":"other-server"}""" },
            )
            assertFailsWith<IllegalArgumentException> {
                serverMismatchRepository.upsertSource(SourceEditorDraft(
                    id = 42,
                    address = "https://emby.example",
                    alias = "Should Not Save",
                    username = "edited",
                    secret = "",
                    storageType = SourceEditorType.Emby,
                ))
            }
            assertEquals(before, fixture.database.sourceAccountDao().get(42))
            assertEquals(StoredCredential("Verified Name", "access-token", false), fixture.credentials.load(42))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun unauthorizedEmptyPasswordNeedsReauthenticationWithoutMutation() = runBlocking {
        val fixture = EmbyFixture()
        try {
            fixture.seedExisting()
            val before = fixture.database.sourceAccountDao().get(42)
            val repository = fixture.repository(
                request = { _, _, _, _ -> throw RemoteMusicException.Unauthorized() },
            )
            assertFailsWith<NeedsReauthenticationException> {
                repository.upsertSource(SourceEditorDraft(
                    id = 42,
                    address = "https://emby.example",
                    alias = "Should Not Save",
                    username = "edited",
                    secret = "",
                    storageType = SourceEditorType.Emby,
                ))
            }
            assertEquals(before, fixture.database.sourceAccountDao().get(42))
            assertEquals("access-token", fixture.credentials.load(42)?.secret)
            assertEquals(SourceConnectionTestStatus.Unauthorized, repository.testSource(SourceEditorDraft(
                id = 42,
                address = "https://emby.example",
                username = "edited",
                secret = "",
                storageType = SourceEditorType.Emby,
            )))

            val newLoginRepository = fixture.repository(
                login = { _, _, _ -> throw RemoteMusicException.Unauthorized() },
            )
            assertFailsWith<NeedsReauthenticationException> {
                newLoginRepository.upsertSource(SourceEditorDraft(
                    id = 43,
                    address = "https://emby.example",
                    alias = "New Emby",
                    username = "alice",
                    secret = "new-password",
                    storageType = SourceEditorType.Emby,
                ))
            }
            assertEquals(null, fixture.database.sourceAccountDao().get(43))
            assertEquals(null, fixture.credentials.load(43))
            assertEquals(SourceConnectionTestStatus.Unauthorized, newLoginRepository.testSource(SourceEditorDraft(
                id = 43,
                address = "https://emby.example",
                username = "alice",
                secret = "new-password",
                storageType = SourceEditorType.Emby,
            )))
        } finally {
            fixture.close()
        }
    }

    @Test
    fun emptyPasswordCannotUseCredentialFromAnotherProvider() = runBlocking {
        val fixture = EmbyFixture()
        try {
            fixture.database.sourceAccountDao().upsert(SourceAccountEntity(
                id = 42,
                providerType = ProviderTypes.Navidrome,
                displayName = "Navidrome",
                endpoint = "https://navidrome.example",
                externalAccountId = "nav-user",
                credentialRef = "storage-42",
                priority = 0,
                enabled = true,
                createdAt = 1,
                updatedAt = 1,
            ))
            fixture.credentials.save(42, StoredCredential("nav-user", "nav-token", false))
            val before = fixture.database.sourceAccountDao().get(42)
            var requestCalls = 0
            val repository = fixture.repository(
                request = { _, _, _, _ -> requestCalls++; error("must not request") },
            )

            assertFailsWith<NeedsReauthenticationException> {
                repository.upsertSource(SourceEditorDraft(
                    id = 42,
                    address = "https://emby.example",
                    alias = "Must Not Rebind",
                    username = "alice",
                    secret = "",
                    storageType = SourceEditorType.Emby,
                ))
            }
            assertEquals(0, requestCalls)
            assertEquals(before, fixture.database.sourceAccountDao().get(42))
            assertEquals(StoredCredential("nav-user", "nav-token", false), fixture.credentials.load(42))
            assertEquals(SourceConnectionTestStatus.Unauthorized, repository.testSource(SourceEditorDraft(
                id = 42,
                address = "https://emby.example",
                username = "alice",
                secret = "",
                storageType = SourceEditorType.Emby,
            )))
            assertEquals(0, requestCalls)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun testSourcePropagatesCancellation() = runBlocking {
        val fixture = EmbyFixture()
        try {
            fixture.seedExisting()
            val repository = fixture.repository(
                request = { _, _, _, _ -> throw CancellationException("cancelled") },
            )
            assertFailsWith<CancellationException> {
                repository.testSource(SourceEditorDraft(
                    id = 42,
                    address = "https://emby.example",
                    username = "alice",
                    secret = "",
                    storageType = SourceEditorType.Emby,
                ))
            }
            Unit
        } finally {
            fixture.close()
        }
    }
}

private class EmbyFixture {
    val database = Room.inMemoryDatabaseBuilder<AppDatabase> {
        AppDatabaseConstructor.initialize()
    }.setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.Default).build()
    val credentials = TestCredentialStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tempDir = Files.createTempDirectory("musicapp-emby").toFile()
    private val bridge = Bridge(
        appDocumentDir = tempDir.absolutePath,
        appCacheDir = tempDir.absolutePath,
        toastRepository = ToastRepositoryImpl(scope),
    )

    fun repository(
        login: (String, String, String) -> EmbyLoginIdentity = { _, _, _ ->
            EmbyLoginIdentity("access-token", "user-1", "server-1", "Server")
        },
        request: (String, String, String, Map<String, String>) -> String = { _, _, _, _ ->
            """{"Id":"user-1","ServerId":"server-1"}"""
        },
    ) = StorageRepositoryImpl(
        bridge = bridge,
        scope = scope,
        sourceAccountDao = database.sourceAccountDao(),
        credentialStore = credentials,
        embyLogin = login,
        embyRequest = request,
    )

    suspend fun seedExisting() {
        database.sourceAccountDao().upsert(SourceAccountEntity(
            id = 42,
            providerType = ProviderTypes.Emby,
            displayName = "Existing",
            endpoint = "https://emby.example",
            externalAccountId = "user-1",
            credentialRef = "storage-42",
            priority = 0,
            enabled = true,
            createdAt = 1,
            updatedAt = 1,
            providerConfig = EmbyProviderConfigurationCodec.encode(
                EmbyProviderConfiguration(
                    serverId = "server-1",
                    serverName = "Old Server",
                    secondaryBaseUrl = "https://old-secondary.example",
                ),
            ),
        ))
        credentials.save(42, StoredCredential("stored-user", "access-token", false))
    }

    fun close() {
        scope.cancel()
        database.close()
        tempDir.deleteRecursively()
    }
}

private class TestCredentialStore : CredentialStore {
    private val values = mutableMapOf<Long, StoredCredential>()
    override suspend fun load(storageId: Long): StoredCredential? = values[storageId]
    override suspend fun save(storageId: Long, credential: StoredCredential) { values[storageId] = credential }
    override suspend fun delete(storageId: Long) { values.remove(storageId) }
    override suspend fun clear() { values.clear() }
}
