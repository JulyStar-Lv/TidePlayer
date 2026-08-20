package io.github.julystar.musicapp.service.playback.data

import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.TrackSourcePlaybackCandidate
import io.github.julystar.musicapp.database.TrackSourceRefDao
import io.github.julystar.musicapp.database.SourceItemPropertyEntity
import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.MediaType
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.source.api.BuiltInSourceIds
import io.github.julystar.musicapp.source.api.LegacyStoragePlaybackResolver
import io.github.julystar.musicapp.source.api.MusicSourceRegistry
import io.github.julystar.musicapp.source.api.PlaybackResource
import io.github.julystar.musicapp.source.api.RemoteServerPlaybackTarget
import io.github.julystar.musicapp.source.api.encodedPlaybackId
import io.github.julystar.musicapp.source.api.SourcePlaybackFailureReason
import io.github.julystar.musicapp.source.api.SourcePlaybackResult
import io.github.julystar.musicapp.source.api.legacyStorageTrackMediaId
import io.github.julystar.musicapp.source.storage.LegacyStorageLookup
import io.github.julystar.musicapp.source.storage.toLegacyStorageSourceAccountId
import uniffi.app_backend.Music
import uniffi.app_backend.StorageId
import uniffi.app_backend.StorageType

fun interface SourceItemPropertyReader {
    suspend fun propertiesForItem(sourceItemId: Long): List<SourceItemPropertyEntity>

    companion object {
        val Empty = SourceItemPropertyReader { emptyList() }
    }
}

class PlaybackResourceResolver(
    private val storageLookup: LegacyStorageLookup,
    private val trackSourceRefDao: TrackSourceRefDao,
    private val sourceRegistry: MusicSourceRegistry,
    private val legacyStoragePlaybackResolver: LegacyStoragePlaybackResolver,
    private val playbackAudioCache: PlaybackAudioCache = PlaybackAudioCache.Disabled,
    private val sourceItemPropertyReader: SourceItemPropertyReader,
) {
    suspend fun resolve(music: Music): SourcePlaybackResult {
        val candidates = trackSourceRefDao.playbackCandidates(music.meta.id.value)
        val sourceMediaIds = mutableMapOf<Long, String?>()
        var lastRemoteFailure: SourcePlaybackResult.Failure? = null
        suspend fun sourceMediaIdFor(candidate: TrackSourcePlaybackCandidate): String? {
            if (candidate.account.providerType != ProviderTypes.Emby) return null
            if (candidate.item.id !in sourceMediaIds) {
                sourceMediaIds[candidate.item.id] = sourceItemPropertyReader
                    .propertiesForItem(candidate.item.id)
                    .firstOrNull { it.propertyKey == "sourceMediaId" }
                    ?.stringValue
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
            }
            return sourceMediaIds[candidate.item.id]
        }
        val explicitlyPreferred = candidates
            .filter { candidate -> candidate.ref.isPreferred }
            .singleOrNull()
        explicitlyPreferred?.let { candidate ->
            resolvePlaybackCandidate(candidate, sourceMediaIdFor(candidate))?.let { result ->
                if (result is SourcePlaybackResult.Success) return result
                if (result is SourcePlaybackResult.Failure) lastRemoteFailure = result
            }
        }
        val fallbackCandidates = candidates.filterNot { candidate ->
            candidate.item.id == explicitlyPreferred?.item?.id
        }
        val localCandidates = fallbackCandidates.filter { candidate ->
            candidate.account.providerType == ProviderTypes.Local
        }
        for (candidate in localCandidates) {
            resolveCandidate(candidate)?.let { result ->
                if (result is SourcePlaybackResult.Success) return result
            }
        }

        val storage = storageLookup.storageForPlayback(music.loc.storageId)
        if (storage?.typ == StorageType.LOCAL) {
            resolveLegacyLocation(storage, music.loc.path)?.let { result ->
                if (result is SourcePlaybackResult.Success) return result
            }
        }

        val remoteCandidates = fallbackCandidates.filterNot { candidate ->
            candidate.account.providerType == ProviderTypes.Local
        }
        for (candidate in remoteCandidates) {
            val identity = candidate.cacheIdentity(sourceMediaIdFor(candidate)) ?: continue
            playbackAudioCache.resolveCompleted(identity, candidate.item.mimeType)?.let { resource ->
                return SourcePlaybackResult.Success(resource)
            }
        }
        if (storage != null && storage.typ != StorageType.LOCAL) {
            val identity = PlaybackCacheIdentity(storage.id.value, music.loc.path)
            playbackAudioCache.resolveCompleted(identity, mimeType = null)?.let { resource ->
                return SourcePlaybackResult.Success(resource)
            }
        }

        for (candidate in remoteCandidates) {
            val sourceMediaId = sourceMediaIdFor(candidate)
            val identity = candidate.cacheIdentity(sourceMediaId) ?: continue
            when (val result = resolveCandidate(candidate, sourceMediaId)) {
                is SourcePlaybackResult.Success -> {
                    try {
                        val wrapped = playbackAudioCache.wrapRemote(identity, result.resource)
                        return SourcePlaybackResult.Success(wrapped)
                    } catch (_: PlaybackProxyUnavailableException) {
                        lastRemoteFailure = SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unavailable)
                        continue
                    }
                }
                is SourcePlaybackResult.Failure -> lastRemoteFailure = result
                null -> Unit
            }
        }

        storage ?: return lastRemoteFailure ?: SourcePlaybackResult.Failure(
            SourcePlaybackFailureReason.UnsupportedAccount
        )
        val result = resolveLegacyLocation(storage, music.loc.path)
            ?: return SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unavailable)
        if (result !is SourcePlaybackResult.Success || storage.typ == StorageType.LOCAL) {
            return result
        }
        return try {
            SourcePlaybackResult.Success(
                playbackAudioCache.wrapRemote(
                    identity = PlaybackCacheIdentity(storage.id.value, music.loc.path),
                    resource = result.resource,
                )
            )
        } catch (_: PlaybackProxyUnavailableException) {
            SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unavailable)
        }
    }

    private suspend fun resolvePlaybackCandidate(
        candidate: TrackSourcePlaybackCandidate,
        sourceMediaId: String?,
    ): SourcePlaybackResult? {
        if (candidate.account.providerType == ProviderTypes.Local) {
            return resolveCandidate(candidate)
        }
        val identity = candidate.cacheIdentity(sourceMediaId) ?: return null
        playbackAudioCache.resolveCompleted(identity, candidate.item.mimeType)?.let { resource ->
            return SourcePlaybackResult.Success(resource)
        }
        return when (val result = resolveCandidate(candidate, sourceMediaId)) {
            is SourcePlaybackResult.Success -> try {
                SourcePlaybackResult.Success(
                    playbackAudioCache.wrapRemote(identity, result.resource)
                )
            } catch (_: PlaybackProxyUnavailableException) {
                SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unavailable)
            }
            is SourcePlaybackResult.Failure,
            null -> result
        }
    }

    private suspend fun resolveCandidate(
        candidate: TrackSourcePlaybackCandidate,
        sourceMediaId: String? = null,
    ): SourcePlaybackResult? {
        val sourceId = candidate.account.providerType.toBuiltInSourceId() ?: return null
        val source = sourceRegistry.sourceOrNull(sourceId) ?: return null
        if (candidate.account.providerType.isRemoteServerProvider()) {
            val remoteId = candidate.item.providerItemId ?: return null
            val target = RemoteServerPlaybackTarget(
                accountId = SourceAccountId("storage:${candidate.item.sourceAccountId}"),
                remoteId = remoteId,
                sourceMediaId = sourceMediaId,
            )
            return source.resolvePlayback(
                MediaId(sourceId = sourceId, mediaType = MediaType.Track, remoteId = target.encodedPlaybackId())
            )
        }
        val path = candidate.item.canonicalPath ?: return null
        return source.resolvePlayback(
            legacyStorageTrackMediaId(
                sourceId = sourceId,
                accountId = StorageId(candidate.item.sourceAccountId)
                    .toLegacyStorageSourceAccountId(),
                path = path,
            )
        )
    }

    private suspend fun resolveLegacyLocation(
        storage: uniffi.app_backend.Storage,
        path: String,
    ): SourcePlaybackResult? {
        val sourceId = storage.typ.toBuiltInSourceId()
        val source = sourceRegistry.sourceOrNull(sourceId) ?: return null
        return source.resolvePlayback(
            legacyStorageTrackMediaId(
                sourceId = sourceId,
                accountId = storage.id.toLegacyStorageSourceAccountId(),
                path = path,
            )
        )
    }

    suspend fun release(resource: PlaybackResource) {
        val original = playbackAudioCache.release(resource) ?: return
        legacyStoragePlaybackResolver.release(original.uri)
    }

    suspend fun release(uri: String) {
        legacyStoragePlaybackResolver.release(uri)
    }

    suspend fun releaseAll() {
        playbackAudioCache.releaseAll()
        legacyStoragePlaybackResolver.releaseAll()
    }
}

private fun TrackSourcePlaybackCandidate.cacheIdentity(sourceMediaId: String?): PlaybackCacheIdentity? {
    val path = if (account.providerType.isRemoteServerProvider()) {
        "remote:${item.sourceAccountId}:${item.providerItemId ?: return null}"
    } else {
        item.canonicalPath ?: return null
    }
    val baseVersion = item.contentHash
        ?: item.etag
        ?: item.revision
        ?: item.modifiedAtRemote?.let { modifiedAt ->
            "$modifiedAt:${item.sizeBytes?.toString().orEmpty()}"
        }
    val version = baseVersion?.let { base ->
        sourceMediaId?.let { "$base|media:$it" } ?: base
    } ?: sourceMediaId?.let { "media:$it" }
    return PlaybackCacheIdentity(item.sourceAccountId, path, version)
}

private fun StorageType.toBuiltInSourceId() = when (this) {
    StorageType.LOCAL -> BuiltInSourceIds.Local
    StorageType.WEBDAV -> BuiltInSourceIds.WebDav
    StorageType.ONE_DRIVE -> BuiltInSourceIds.OneDrive
    StorageType.SMB -> BuiltInSourceIds.Smb
    StorageType.OPEN_LIST -> BuiltInSourceIds.OpenList
}

private fun String.toBuiltInSourceId() = when (this) {
    ProviderTypes.Local -> BuiltInSourceIds.Local
    ProviderTypes.WebDav -> BuiltInSourceIds.WebDav
    ProviderTypes.OneDrive -> BuiltInSourceIds.OneDrive
    ProviderTypes.Smb -> BuiltInSourceIds.Smb
    ProviderTypes.OpenList -> BuiltInSourceIds.OpenList
    ProviderTypes.Navidrome -> BuiltInSourceIds.Navidrome
    ProviderTypes.OpenSubsonic -> BuiltInSourceIds.OpenSubsonic
    ProviderTypes.Emby -> BuiltInSourceIds.Emby
    else -> null
}

private fun String.isRemoteServerProvider(): Boolean = this == ProviderTypes.Navidrome ||
    this == ProviderTypes.OpenSubsonic ||
    this == ProviderTypes.Emby
