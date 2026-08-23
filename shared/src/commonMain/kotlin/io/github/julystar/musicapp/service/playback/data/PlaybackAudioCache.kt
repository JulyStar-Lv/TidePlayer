package io.github.julystar.musicapp.service.playback.data

import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.source.api.PlaybackResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path.Companion.toPath
import uniffi.app_backend.PlaybackCacheOptions
import uniffi.app_backend.PlaybackSession
import uniffi.app_backend.ctCreateHttpPlaybackCacheSession
import uniffi.app_backend.ctOpenCompletedPlaybackCache

data class PlaybackCacheIdentity(
    val storageId: Long,
    val path: String,
    val version: String? = null,
) {
    init {
        require(path.isNotBlank()) { "Playback cache path cannot be blank" }
    }

    internal val key: String
        get() = "$storageId\n$path\n${version.orEmpty()}"
}

enum class PlaybackAudioPreloadResult {
    Completed,
    Deduplicated,
    HandedOff,
    Failed,
}

interface PlaybackAudioCache {
    suspend fun resolveCompleted(
        identity: PlaybackCacheIdentity,
        mimeType: String?,
    ): PlaybackResource?

    suspend fun wrapRemote(
        identity: PlaybackCacheIdentity,
        resource: PlaybackResource,
    ): PlaybackResource

    suspend fun resolvePreloaded(
        identity: PlaybackCacheIdentity,
        mimeType: String?,
    ): PlaybackResource? = null

    suspend fun preloadRemote(
        identity: PlaybackCacheIdentity,
        resource: PlaybackResource,
        maxBytes: Long,
    ): PlaybackAudioPreloadResult = PlaybackAudioPreloadResult.Failed

    suspend fun release(resource: PlaybackResource): PlaybackResource?

    suspend fun releaseAll()

    data object Disabled : PlaybackAudioCache {
        override suspend fun resolveCompleted(
            identity: PlaybackCacheIdentity,
            mimeType: String?,
        ): PlaybackResource? = null

        override suspend fun wrapRemote(
            identity: PlaybackCacheIdentity,
            resource: PlaybackResource,
        ): PlaybackResource = resource

        override suspend fun release(resource: PlaybackResource): PlaybackResource = resource

        override suspend fun releaseAll() = Unit
    }
}

class PersistentPlaybackAudioCache internal constructor(
    private val settingsRepository: SettingsRepository,
    cacheDirectory: String,
    private val completedMediaPromoter: CompletedMediaPromoter = CompletedMediaPromoter.Disabled,
    private val sessionFactory: suspend (String, Map<String, String>, PlaybackCacheOptions) -> PlaybackCacheSessionHandle,
) : PlaybackAudioCache {
    constructor(
        settingsRepository: SettingsRepository,
        cacheDirectory: String,
        completedMediaPromoter: CompletedMediaPromoter = CompletedMediaPromoter.Disabled,
    ) : this(
        settingsRepository,
        cacheDirectory,
        completedMediaPromoter,
        ::createNativePlaybackCacheSession,
    )
    private val directory = (cacheDirectory.toPath() / CACHE_DIRECTORY_NAME).toString()
    private val mutex = Mutex()
    private val sessions = mutableMapOf<String, RetainedCacheSession>()
    private val preloads = mutableMapOf<String, PreloadCacheSession>()

    override suspend fun resolveCompleted(
        identity: PlaybackCacheIdentity,
        mimeType: String?,
    ): PlaybackResource? {
        val session = runCatching {
            ctOpenCompletedPlaybackCache(
                cacheOptions(
                    identity = identity,
                    mimeType = mimeType,
                    writeEnabled = false,
                    maxBytes = 0L,
                )
            )
        }.getOrNull() ?: return null
        val resource = PlaybackResource(
            uri = session.url(),
            mimeType = mimeType,
            isLocal = true,
        )
        retain(
            resource.uri,
            NativePlaybackCacheSessionHandle(session),
            original = null,
            identity = identity,
            mimeType = mimeType,
        )
        return resource
    }

    override suspend fun wrapRemote(
        identity: PlaybackCacheIdentity,
        resource: PlaybackResource,
    ): PlaybackResource {
        if (resource.isLocal || !resource.uri.isHttpUri()) {
            if (resource.headers.isNotEmpty()) throw PlaybackProxyUnavailableException()
            return resource
        }
        val settings = settingsRepository.settings.first()
        val requiresHeaderProxy = resource.headers.isNotEmpty()
        val cacheEnabled = settings.listenAndCacheEnabled && settings.audioCacheLimitBytes > 0L
        if (!requiresHeaderProxy && !cacheEnabled) {
            return resource
        }
        val session = try {
            sessionFactory(
                resource.uri,
                resource.headers,
                cacheOptions(
                    identity = identity,
                    mimeType = resource.mimeType,
                    writeEnabled = cacheEnabled,
                    maxBytes = if (cacheEnabled) settings.audioCacheLimitBytes else 0L,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            if (requiresHeaderProxy) throw PlaybackProxyUnavailableException()
            return resource
        }
        val wrapped = resource.copy(
            uri = session.url(),
            headers = emptyMap(),
            expiresAtEpochMs = null,
            isLocal = false,
        )
        retain(
            uri = wrapped.uri,
            session = session,
            original = resource,
            identity = identity,
            mimeType = resource.mimeType,
        )
        return wrapped
    }

    override suspend fun resolvePreloaded(
        identity: PlaybackCacheIdentity,
        mimeType: String?,
    ): PlaybackResource? {
        val preload = takePreloadSession(identity.key) ?: return null
        val wrapped = preload.original.copy(
            uri = preload.session.url(),
            headers = emptyMap(),
            expiresAtEpochMs = null,
            isLocal = false,
        )
        retain(
            uri = wrapped.uri,
            session = preload.session,
            original = preload.original,
            identity = identity,
            mimeType = mimeType ?: preload.mimeType,
        )
        return wrapped
    }

    override suspend fun preloadRemote(
        identity: PlaybackCacheIdentity,
        resource: PlaybackResource,
        maxBytes: Long,
    ): PlaybackAudioPreloadResult {
        if (maxBytes <= 0L || resource.isLocal || !resource.uri.isHttpUri()) {
            return PlaybackAudioPreloadResult.Failed
        }
        val preload = PreloadCacheSession(
            original = resource,
            mimeType = resource.mimeType,
            caller = currentCoroutineContext()[Job],
        )
        val existing = mutex.withLock {
            preloads[identity.key].also { current ->
                if (current == null) preloads[identity.key] = preload
            }
        }
        if (existing != null) {
            return when (existing.result.await()) {
                PlaybackAudioPreloadResult.Failed -> PlaybackAudioPreloadResult.Failed
                else -> PlaybackAudioPreloadResult.Deduplicated
            }
        }

        var session: PlaybackCacheSessionHandle? = null
        try {
            val settings = settingsRepository.settings.first()
            if (!settings.listenAndCacheEnabled || settings.audioCacheLimitBytes <= 0L) {
                return PlaybackAudioPreloadResult.Failed
            }
            val createdSession = sessionFactory(
                resource.uri,
                resource.headers,
                cacheOptions(
                    identity = identity,
                    mimeType = resource.mimeType,
                    writeEnabled = true,
                    maxBytes = settings.audioCacheLimitBytes,
                ),
            )
            session = createdSession
            val retained = mutex.withLock {
                if (preloads[identity.key] !== preload) return@withLock false
                preload.session = createdSession
                true
            }
            if (!retained) {
                createdSession.shutdown()
                return PlaybackAudioPreloadResult.Failed
            }
            val prefetchResult = coroutineScope {
                val task = async(start = CoroutineStart.LAZY) {
                    createdSession.prefetchPrefix(maxBytes)
                }
                val shouldStart = mutex.withLock {
                    if (preloads[identity.key] !== preload) return@withLock false
                    preload.task = task
                    true
                }
                if (!shouldStart) {
                    task.cancel()
                    return@coroutineScope PlaybackAudioPreloadResult.HandedOff
                }
                task.start()
                try {
                    task.await()
                    mutex.withLock {
                        if (preloads[identity.key] === preload) {
                            preloads.remove(identity.key)
                            PlaybackAudioPreloadResult.Completed
                        } else {
                            PlaybackAudioPreloadResult.HandedOff
                        }
                    }
                } catch (error: CancellationException) {
                    if (preload.sessionClaimed) {
                        PlaybackAudioPreloadResult.HandedOff
                    } else {
                        throw error
                    }
                }
            }
            preload.result.complete(prefetchResult)
            return prefetchResult
        } catch (error: CancellationException) {
            preload.result.complete(PlaybackAudioPreloadResult.Failed)
            throw error
        } catch (_: Throwable) {
            preload.result.complete(PlaybackAudioPreloadResult.Failed)
            return PlaybackAudioPreloadResult.Failed
        } finally {
            val shouldShutdown = mutex.withLock {
                if (preloads[identity.key] === preload) {
                    preloads.remove(identity.key)
                }
                !preload.sessionClaimed
            }
            if (shouldShutdown) session?.shutdown()
            preload.result.complete(PlaybackAudioPreloadResult.Failed)
        }
    }

    override suspend fun release(resource: PlaybackResource): PlaybackResource? {
        val retained = mutex.withLock {
            sessions.remove(resource.uri)
        } ?: return resource
        retained.session.shutdown()
        completedMediaPromoter.promote(retained.toCompletedPlaybackCache(directory))
        return retained.original
    }

    override suspend fun releaseAll() {
        val (retained, preloading) = mutex.withLock {
            val retained = sessions.values.toList().also { sessions.clear() }
            val preloading = preloads.values.toList().also {
                preloads.clear()
                it.forEach { preload -> preload.sessionClaimed = true }
            }
            retained to preloading
        }
        preloading.forEach { preload ->
            preload.result.complete(PlaybackAudioPreloadResult.Failed)
            preload.task?.cancel() ?: preload.caller?.cancel()
            preload.session?.shutdown()
        }
        retained.forEach { retainedSession ->
            retainedSession.session.shutdown()
            completedMediaPromoter.promote(retainedSession.toCompletedPlaybackCache(directory))
        }
    }

    private suspend fun takePreloadSession(key: String): PreloadSessionHandoff? {
        val currentJob = currentCoroutineContext()[Job]
        val preload = mutex.withLock {
            preloads.remove(key)?.also { entry ->
                if (entry.session != null) entry.sessionClaimed = true
            }
        } ?: return null
        val session = preload.session
        if (session == null) {
            preload.result.complete(PlaybackAudioPreloadResult.Failed)
            if (preload.caller !== currentJob) preload.caller?.cancel()
            return null
        }
        preload.result.complete(PlaybackAudioPreloadResult.HandedOff)
        preload.task?.cancel()
        return PreloadSessionHandoff(session, preload.original, preload.mimeType)
    }

    private suspend fun retain(
        uri: String,
        session: PlaybackCacheSessionHandle,
        original: PlaybackResource?,
        identity: PlaybackCacheIdentity,
        mimeType: String?,
    ) {
        val previous = mutex.withLock {
            sessions.put(uri, RetainedCacheSession(session, original, identity, mimeType))
        }
        previous?.let { retained ->
            retained.session.shutdown()
            completedMediaPromoter.promote(retained.toCompletedPlaybackCache(directory))
        }
    }

    private fun cacheOptions(
        identity: PlaybackCacheIdentity,
        mimeType: String?,
        writeEnabled: Boolean,
        maxBytes: Long,
    ) = PlaybackCacheOptions(
        directory = directory,
        key = identity.key,
        extension = cacheExtension(identity.path, mimeType),
        writeEnabled = writeEnabled,
        maxBytes = maxBytes.coerceAtLeast(0L).toULong(),
    )
}

internal class PlaybackProxyUnavailableException : Exception()

internal interface PlaybackCacheSessionHandle {
    fun url(): String
    suspend fun prefetchPrefix(maxBytes: Long): Long
    fun shutdown()
}

private class NativePlaybackCacheSessionHandle(
    private val session: PlaybackSession,
) : PlaybackCacheSessionHandle {
    override fun url(): String = session.url()

    override suspend fun prefetchPrefix(maxBytes: Long): Long {
        return session.prefetchPrefix(maxBytes.coerceAtLeast(0L).toULong()).toLong()
    }

    override fun shutdown() = session.shutdown()
}

private suspend fun createNativePlaybackCacheSession(
    uri: String,
    headers: Map<String, String>,
    options: PlaybackCacheOptions,
): PlaybackCacheSessionHandle = NativePlaybackCacheSessionHandle(
    ctCreateHttpPlaybackCacheSession(
        uri = uri,
        headers = headers,
        cacheOptions = options,
    )
)

private data class RetainedCacheSession(
    val session: PlaybackCacheSessionHandle,
    val original: PlaybackResource?,
    val identity: PlaybackCacheIdentity,
    val mimeType: String?,
)

private class PreloadCacheSession(
    val original: PlaybackResource,
    val mimeType: String?,
    val caller: Job?,
    val result: CompletableDeferred<PlaybackAudioPreloadResult> = CompletableDeferred(),
    var session: PlaybackCacheSessionHandle? = null,
    var task: Job? = null,
    var sessionClaimed: Boolean = false,
)

private data class PreloadSessionHandoff(
    val session: PlaybackCacheSessionHandle,
    val original: PlaybackResource,
    val mimeType: String?,
)

private fun RetainedCacheSession.toCompletedPlaybackCache(
    directory: String,
) = CompletedPlaybackCache(
    identity = identity,
    mimeType = mimeType,
    cacheDirectory = directory,
    extension = cacheExtension(identity.path, mimeType),
)

private fun cacheExtension(path: String, mimeType: String?): String {
    val pathExtension = path
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
        .takeIf { extension ->
            extension.isNotEmpty() &&
                extension.length <= MAX_EXTENSION_LENGTH &&
                extension.all(Char::isLetterOrDigit)
        }
    return pathExtension ?: when (mimeType?.substringBefore(';')?.lowercase()) {
        "audio/flac" -> "flac"
        "audio/mpeg" -> "mp3"
        "audio/mp4", "audio/x-m4a" -> "m4a"
        "audio/ogg" -> "ogg"
        "audio/opus" -> "opus"
        "audio/wav", "audio/x-wav" -> "wav"
        else -> "bin"
    }
}

private fun String.isHttpUri(): Boolean {
    return startsWith("http://", ignoreCase = true) ||
        startsWith("https://", ignoreCase = true)
}

private const val CACHE_DIRECTORY_NAME = "playback-cache"
private const val MAX_EXTENSION_LENGTH = 10
