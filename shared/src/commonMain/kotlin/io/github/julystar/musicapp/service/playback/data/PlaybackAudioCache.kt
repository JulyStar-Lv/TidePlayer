package io.github.julystar.musicapp.service.playback.data

import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.source.api.PlaybackResource
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

interface PlaybackAudioCache {
    suspend fun resolveCompleted(
        identity: PlaybackCacheIdentity,
        mimeType: String?,
    ): PlaybackResource?

    suspend fun wrapRemote(
        identity: PlaybackCacheIdentity,
        resource: PlaybackResource,
    ): PlaybackResource

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

class PersistentPlaybackAudioCache(
    private val settingsRepository: SettingsRepository,
    cacheDirectory: String,
    private val completedMediaPromoter: CompletedMediaPromoter = CompletedMediaPromoter.Disabled,
) : PlaybackAudioCache {
    private val directory = (cacheDirectory.toPath() / CACHE_DIRECTORY_NAME).toString()
    private val mutex = Mutex()
    private val sessions = mutableMapOf<String, RetainedCacheSession>()

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
        retain(resource.uri, session, original = null, identity = identity, mimeType = mimeType)
        return resource
    }

    override suspend fun wrapRemote(
        identity: PlaybackCacheIdentity,
        resource: PlaybackResource,
    ): PlaybackResource {
        if (resource.isLocal || !resource.uri.isHttpUri()) return resource
        val settings = settingsRepository.settings.first()
        if (!settings.listenAndCacheEnabled || settings.audioCacheLimitBytes <= 0L) {
            return resource
        }
        val session = runCatching {
            ctCreateHttpPlaybackCacheSession(
                uri = resource.uri,
                headers = resource.headers,
                cacheOptions = cacheOptions(
                    identity = identity,
                    mimeType = resource.mimeType,
                    writeEnabled = true,
                    maxBytes = settings.audioCacheLimitBytes,
                ),
            )
        }.getOrNull() ?: return resource
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

    override suspend fun release(resource: PlaybackResource): PlaybackResource? {
        val retained = mutex.withLock {
            sessions.remove(resource.uri)
        } ?: return resource
        retained.session.shutdown()
        completedMediaPromoter.promote(retained.toCompletedPlaybackCache(directory))
        return retained.original
    }

    override suspend fun releaseAll() {
        val retained = mutex.withLock {
            sessions.values.toList().also { sessions.clear() }
        }
        retained.forEach { retainedSession ->
            retainedSession.session.shutdown()
            completedMediaPromoter.promote(retainedSession.toCompletedPlaybackCache(directory))
        }
    }

    private suspend fun retain(
        uri: String,
        session: PlaybackSession,
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

private data class RetainedCacheSession(
    val session: PlaybackSession,
    val original: PlaybackResource?,
    val identity: PlaybackCacheIdentity,
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
