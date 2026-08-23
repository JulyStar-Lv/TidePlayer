package io.github.julystar.musicapp.service.playback.data

import io.github.julystar.musicapp.core.data.datastore.createAppDataStore
import io.github.julystar.musicapp.core.data.settings.DataStoreSettingsRepository
import io.github.julystar.musicapp.source.api.PlaybackResource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
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

    @Test
    fun concurrentPreloadsForSamePhysicalIdentityShareOneSession() = runBlocking {
        val root = createTempDirectory("playback-cache-test-").toFile()
        try {
            val dataStore = createAppDataStore { File(root, "settings.preferences_pb").absolutePath.toPath() }
            val settings = DataStoreSettingsRepository(dataStore, applyLanguageMode = {})
            settings.setListenAndCacheEnabled(true)
            val started = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val session = FakeSession("http://127.0.0.1/preload") { maxBytes ->
                started.complete(Unit)
                finish.await()
                maxBytes
            }
            var sessionCreates = 0
            val cache = PersistentPlaybackAudioCache(
                settingsRepository = settings,
                cacheDirectory = root.absolutePath,
                sessionFactory = { _, _, _ ->
                    sessionCreates += 1
                    session
                },
            )
            val identity = PlaybackCacheIdentity(42, "/Music/Track.flac", "etag-v1")
            val resource = PlaybackResource(uri = "https://server.invalid/audio")

            val first = async { cache.preloadRemote(identity, resource, 2L * 1024 * 1024) }
            started.await()
            val second = async { cache.preloadRemote(identity, resource, 2L * 1024 * 1024) }
            yield()

            assertEquals(1, sessionCreates)
            finish.complete(Unit)
            assertEquals(PlaybackAudioPreloadResult.Completed, first.await())
            assertEquals(PlaybackAudioPreloadResult.Deduplicated, second.await())
            assertEquals(listOf(2L * 1024 * 1024), session.prefetchRequests)
            assertEquals(1, session.shutdownCount)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun actualPlaybackCancelsAndImmediatelyTakesOverSameKeyPreloadSession() = runBlocking {
        val root = createTempDirectory("playback-cache-test-").toFile()
        try {
            val dataStore = createAppDataStore { File(root, "settings.preferences_pb").absolutePath.toPath() }
            val settings = DataStoreSettingsRepository(dataStore, applyLanguageMode = {})
            settings.setListenAndCacheEnabled(true)
            val started = CompletableDeferred<Unit>()
            val neverFinish = CompletableDeferred<Unit>()
            val session = FakeSession("http://127.0.0.1/handoff") { maxBytes ->
                started.complete(Unit)
                neverFinish.await()
                maxBytes
            }
            var sessionCreates = 0
            val cache = PersistentPlaybackAudioCache(
                settingsRepository = settings,
                cacheDirectory = root.absolutePath,
                sessionFactory = { _, _, _ ->
                    sessionCreates += 1
                    session
                },
            )
            val identity = PlaybackCacheIdentity(42, "/Music/Track.flac", "etag-v1")
            val preloadedResource = PlaybackResource(uri = "https://server.invalid/preloaded")

            val preload = async {
                cache.preloadRemote(identity, preloadedResource, 4L * 1024 * 1024)
            }
            started.await()
            val wrapped = withTimeout(500) {
                cache.resolvePreloaded(identity, mimeType = "audio/flac")
            } ?: error("preload session was not handed off")

            assertEquals("http://127.0.0.1/handoff", wrapped.uri)
            assertEquals(1, sessionCreates)
            assertEquals(PlaybackAudioPreloadResult.HandedOff, preload.await())
            assertEquals(0, session.shutdownCount)
            assertEquals(preloadedResource, cache.release(wrapped))
            assertEquals(1, session.shutdownCount)
        } finally {
            root.deleteRecursively()
        }
    }

    private class FakeSession(
        private val sessionUrl: String,
        private val prefetch: suspend (Long) -> Long = { it },
    ) : PlaybackCacheSessionHandle {
        var shutdownCount = 0
        val prefetchRequests = mutableListOf<Long>()

        override fun url(): String = sessionUrl

        override suspend fun prefetchPrefix(maxBytes: Long): Long {
            prefetchRequests += maxBytes
            return prefetch(maxBytes)
        }

        override fun shutdown() {
            shutdownCount += 1
        }
    }
}
