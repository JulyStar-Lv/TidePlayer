package io.github.julystar.musicapp.core.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.julystar.musicapp.core.data.security.CredentialStore
import io.github.julystar.musicapp.core.domain.model.SourceConnectionTestStatus
import io.github.julystar.musicapp.core.domain.model.SourceEditorDraft
import io.github.julystar.musicapp.core.domain.model.SourceEditorType
import io.github.julystar.musicapp.core.domain.model.StoredCredential
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.AppDatabaseConstructor
import io.github.julystar.musicapp.singleton.Bridge
import io.github.julystar.musicapp.source.api.NavidromeProviderConfigurationCodec
import io.github.julystar.musicapp.source.api.OpenSubsonicCapabilitySnapshot
import io.github.julystar.musicapp.source.api.OpenSubsonicExtension
import io.github.julystar.musicapp.source.api.OpenSubsonicProviderConfigurationCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import uniffi.app_backend.RemoteMusicException

class RemoteServerStorageRepositoryIntegrationTest {
    @Test
    fun navidromeAdvancedFieldsRoundTripAndTestUsesSecondaryOnce() = runBlocking {
        val fixture = RemoteServerEditorFixture()
        try {
            val endpoints = mutableListOf<String>()
            val repository = fixture.repository { endpoint, _, _, _, _ ->
                endpoints += endpoint
                if (endpoint == "https://primary.example") throw RemoteMusicException.Timeout()
                """{"subsonic-response":{"status":"ok"}}"""
            }
            val draft = SourceEditorDraft(
                id = 42,
                address = "https://primary.example/",
                alias = "Navidrome",
                username = "alice",
                secret = "password",
                storageType = SourceEditorType.Navidrome,
                streamMaxBitRate = 192,
                downloadMaxBitRate = 320,
                coverArtSize = 1024,
                remoteWriteEnabled = true,
                secondaryBaseUrl = "https://secondary.example/",
            )
            repository.upsertSource(draft)
            assertEquals(SourceConnectionTestStatus.Success, repository.testSource(draft))

            val editor = assertNotNull(repository.loadEditorState(42)).draft
            assertEquals("https://primary.example", editor.address)
            assertEquals(192, editor.streamMaxBitRate)
            assertEquals(320, editor.downloadMaxBitRate)
            assertEquals(1024, editor.coverArtSize)
            assertTrue(editor.remoteWriteEnabled)
            assertEquals("https://secondary.example", editor.secondaryBaseUrl)
            assertEquals("", editor.secret)
            val config = NavidromeProviderConfigurationCodec.decode(
                fixture.database.sourceAccountDao().get(42)?.providerConfig,
            )
            assertEquals("https://secondary.example", config.secondaryBaseUrl)
            assertEquals(
                listOf(
                    "https://primary.example", "https://secondary.example",
                    "https://primary.example", "https://secondary.example",
                ),
                endpoints,
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun openSubsonicEditorUpdatesAdvancedFieldsWithoutDroppingCapabilities() = runBlocking {
        val fixture = RemoteServerEditorFixture()
        try {
            val endpoints = mutableListOf<String>()
            val repository = fixture.repository { endpoint, _, _, _, _ ->
                endpoints += endpoint
                if (endpoint == "https://primary.example") throw RemoteMusicException.Connectivity()
                """{"subsonic-response":{"status":"ok"}}"""
            }
            repository.upsertSource(SourceEditorDraft(
                id = 43,
                address = "https://primary.example",
                alias = "OpenSubsonic",
                username = "alice",
                secret = "password",
                storageType = SourceEditorType.OpenSubsonic,
                streamMaxBitRate = 128,
                downloadMaxBitRate = 256,
                coverArtSize = 768,
                remoteWriteEnabled = true,
                secondaryBaseUrl = "https://secondary.example",
            ))
            val original = assertNotNull(fixture.database.sourceAccountDao().get(43))
            val capability = OpenSubsonicCapabilitySnapshot(
                listOf(OpenSubsonicExtension("songLyrics", listOf(2))),
                checkedAtEpochMs = 99,
            )
            fixture.database.sourceAccountDao().upsert(original.copy(
                providerConfig = OpenSubsonicProviderConfigurationCodec.encode(
                    OpenSubsonicProviderConfigurationCodec.decode(original.providerConfig).copy(
                        openSubsonicCapabilities = capability,
                    ),
                ),
            ))

            val loaded = assertNotNull(repository.loadEditorState(43)).draft
            repository.upsertSource(loaded.copy(
                streamMaxBitRate = 192,
                downloadMaxBitRate = 320,
                coverArtSize = 1024,
                secondaryBaseUrl = "https://edited-secondary.example",
            ))
            val saved = OpenSubsonicProviderConfigurationCodec.decode(
                fixture.database.sourceAccountDao().get(43)?.providerConfig,
            )
            assertEquals(192, saved.streamMaxBitRate)
            assertEquals(320, saved.downloadMaxBitRate)
            assertEquals(1024, saved.coverArtSize)
            assertTrue(saved.remoteWriteEnabled)
            assertEquals("https://edited-secondary.example", saved.secondaryBaseUrl)
            assertEquals(capability, saved.openSubsonicCapabilities)
            assertEquals("password", fixture.credentials.load(43)?.secret)
            assertFalse(fixture.database.sourceAccountDao().get(43)?.providerConfig.orEmpty().contains("password"))
            assertEquals(4, endpoints.size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun unsafePrimaryAndSecondaryUrlsFailClosedBeforeRequests() = runBlocking {
        val fixture = RemoteServerEditorFixture()
        try {
            var requests = 0
            val repository = fixture.repository { _, _, _, _, _ ->
                requests++
                error("unsafe URLs must not request")
            }
            val unsafeValues = listOf(
                "ftp://server.example",
                "https://user:password@server.example",
                "https://server.example/?token=secret",
                "https://server.example/#secret",
            )
            unsafeValues.forEach { value ->
                assertEquals(
                    SourceConnectionTestStatus.InvalidAddress,
                    repository.testSource(SourceEditorDraft(
                        address = value,
                        username = "alice",
                        secret = "password",
                        storageType = SourceEditorType.Navidrome,
                    )),
                )
                assertEquals(
                    SourceConnectionTestStatus.InvalidAddress,
                    repository.testSource(SourceEditorDraft(
                        address = "https://primary.example",
                        username = "alice",
                        secret = "password",
                        storageType = SourceEditorType.Navidrome,
                        secondaryBaseUrl = value,
                    )),
                )
            }
            assertEquals(0, requests)
        } finally {
            fixture.close()
        }
    }
}

private class RemoteServerEditorFixture {
    val database = Room.inMemoryDatabaseBuilder<AppDatabase> {
        AppDatabaseConstructor.initialize()
    }.setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.Default).build()
    val credentials = RemoteServerEditorCredentialStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tempDir = Files.createTempDirectory("musicapp-remote-editor").toFile()
    private val bridge = Bridge(
        appDocumentDir = tempDir.absolutePath,
        appCacheDir = tempDir.absolutePath,
        toastRepository = ToastRepositoryImpl(scope),
    )

    fun repository(
        request: (String, String, String, String, Map<String, String>) -> String,
    ) = StorageRepositoryImpl(
        bridge = bridge,
        scope = scope,
        sourceAccountDao = database.sourceAccountDao(),
        credentialStore = credentials,
        subsonicRequest = request,
    )

    fun close() {
        scope.cancel()
        database.close()
        tempDir.deleteRecursively()
    }
}

private class RemoteServerEditorCredentialStore : CredentialStore {
    private val values = mutableMapOf<Long, StoredCredential>()
    override suspend fun load(storageId: Long): StoredCredential? = values[storageId]
    override suspend fun save(storageId: Long, credential: StoredCredential) { values[storageId] = credential }
    override suspend fun delete(storageId: Long) { values.remove(storageId) }
    override suspend fun clear() { values.clear() }
}
