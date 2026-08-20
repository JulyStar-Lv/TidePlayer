package io.github.julystar.musicapp.service.playback.data

import io.github.julystar.musicapp.core.data.datastore.createAppDataStore
import io.github.julystar.musicapp.core.data.settings.DataStoreSettingsRepository
import io.github.julystar.musicapp.source.api.PlaybackResource
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import uniffi.app_backend.PlaybackCacheOptions
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlaybackAudioCacheTest {
    @Test
    fun headerResourceIsProxiedWhenCacheDisabledAndProxyOptionsDisableWrites() = runBlocking {
        val root = createTempDirectory("playback-cache-test-").toFile()
        try {
            val dataStore = createAppDataStore { File(root, "settings.preferences_pb").absolutePath.toPath() }
            val settings = DataStoreSettingsRepository(dataStore, applyLanguageMode = {})
            settings.setListenAndCacheEnabled(false)
            val sessions = mutableListOf<FakeSession>()
            var capturedOptions: PlaybackCacheOptions? = null
            val cache = PersistentPlaybackAudioCache(
                settingsRepository = settings,
                cacheDirectory = root.absolutePath,
                sessionFactory = { _, _, options ->
                    capturedOptions = options
                    FakeSession("http://127.0.0.1/proxy")
                        .also(sessions::add)
                },
            )

            val wrapped = cache.wrapRemote(
                PlaybackCacheIdentity(1, "/track.flac"),
                PlaybackResource(
                    uri = "https://server.invalid/audio",
                    headers = mapOf("X-Emby-Token" to "secret"),
                    expiresAtEpochMs = 123L,
                ),
            )

            assertEquals("http://127.0.0.1/proxy", wrapped.uri)
            assertEquals(emptyMap(), wrapped.headers)
            assertEquals(null, wrapped.expiresAtEpochMs)
            assertEquals(false, capturedOptions?.writeEnabled)
            assertEquals(0uL, capturedOptions?.maxBytes)
            cache.release(wrapped)
            assertEquals(1, sessions.single().shutdownCount)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun headerProxyFailureFailsClosedButCacheDisabledWithoutHeadersReturnsOriginal() = runBlocking {
        val root = createTempDirectory("playback-cache-test-").toFile()
        try {
            val dataStore = createAppDataStore { File(root, "settings.preferences_pb").absolutePath.toPath() }
            val settings = DataStoreSettingsRepository(dataStore, applyLanguageMode = {})
            settings.setListenAndCacheEnabled(false)
            val cache = PersistentPlaybackAudioCache(
                settingsRepository = settings,
                cacheDirectory = root.absolutePath,
                sessionFactory = { _, _, _ -> error("proxy unavailable") },
            )
            val headerResource = PlaybackResource(
                uri = "https://server.invalid/audio",
                headers = mapOf("X-Emby-Token" to "secret"),
            )
            assertFailsWith<PlaybackProxyUnavailableException> {
                cache.wrapRemote(PlaybackCacheIdentity(1, "/track.flac"), headerResource)
            }
            val plain = PlaybackResource(uri = "https://server.invalid/audio")
            assertEquals(plain, cache.wrapRemote(PlaybackCacheIdentity(1, "/track.flac"), plain))
        } finally {
            root.deleteRecursively()
        }
    }

    private class FakeSession(
        private val sessionUrl: String,
    ) : PlaybackCacheSessionHandle {
        var shutdownCount = 0

        override fun url(): String = sessionUrl

        override fun shutdown() {
            shutdownCount += 1
        }
    }
}
