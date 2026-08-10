package io.github.julystar.musicapp.service.playback.data

import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.TrackSourcePlaybackCandidate
import io.github.julystar.musicapp.database.TrackSourceRefDao
import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.MediaType
import io.github.julystar.musicapp.source.api.BuiltInSourceIds
import io.github.julystar.musicapp.source.api.LegacyStoragePlaybackResolver
import io.github.julystar.musicapp.source.api.MusicSourceRegistry
import io.github.julystar.musicapp.source.api.PlaybackResource
import io.github.julystar.musicapp.source.api.SourcePlaybackFailureReason
import io.github.julystar.musicapp.source.api.SourcePlaybackResult
import io.github.julystar.musicapp.source.api.legacyStorageTrackMediaId
import io.github.julystar.musicapp.source.storage.LegacyStorageLookup
import io.github.julystar.musicapp.source.storage.toLegacyStorageSourceAccountId
import uniffi.app_backend.Music
import uniffi.app_backend.StorageId
import uniffi.app_backend.StorageType

class PlaybackResourceResolver(
    private val storageLookup: LegacyStorageLookup,
    private val trackSourceRefDao: TrackSourceRefDao,
    private val sourceRegistry: MusicSourceRegistry,
    private val legacyStoragePlaybackResolver: LegacyStoragePlaybackResolver,
    private val playbackAudioCache: PlaybackAudioCache = PlaybackAudioCache.Disabled,
) {
    suspend fun resolve(music: Music): SourcePlaybackResult {
        val candidates = trackSourceRefDao.playbackCandidates(music.meta.id.value)
        val explicitlyPreferred = candidates
            .filter { candidate -> candidate.ref.isPreferred }
            .singleOrNull()
        explicitlyPreferred?.let { candidate ->
            resolvePlaybackCandidate(candidate)?.let { result ->
                if (result is SourcePlaybackResult.Success) return result
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
            val identity = candidate.cacheIdentity() ?: continue
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
            val identity = candidate.cacheIdentity() ?: continue
            when (val result = resolveCandidate(candidate)) {
                is SourcePlaybackResult.Success -> {
                    return SourcePlaybackResult.Success(
                        playbackAudioCache.wrapRemote(identity, result.resource)
                    )
                }
                is SourcePlaybackResult.Failure,
                null -> Unit
            }
        }

        storage ?: return SourcePlaybackResult.Failure(
            SourcePlaybackFailureReason.UnsupportedAccount
        )
        val result = resolveLegacyLocation(storage, music.loc.path)
            ?: return SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unavailable)
        if (result !is SourcePlaybackResult.Success || storage.typ == StorageType.LOCAL) {
            return result
        }
        return SourcePlaybackResult.Success(
            playbackAudioCache.wrapRemote(
                identity = PlaybackCacheIdentity(storage.id.value, music.loc.path),
                resource = result.resource,
            )
        )
    }

    private suspend fun resolvePlaybackCandidate(
        candidate: TrackSourcePlaybackCandidate,
    ): SourcePlaybackResult? {
        if (candidate.account.providerType == ProviderTypes.Local) {
            return resolveCandidate(candidate)
        }
        val identity = candidate.cacheIdentity() ?: return null
        playbackAudioCache.resolveCompleted(identity, candidate.item.mimeType)?.let { resource ->
            return SourcePlaybackResult.Success(resource)
        }
        return when (val result = resolveCandidate(candidate)) {
            is SourcePlaybackResult.Success -> SourcePlaybackResult.Success(
                playbackAudioCache.wrapRemote(identity, result.resource)
            )
            is SourcePlaybackResult.Failure,
            null -> result
        }
    }

    private suspend fun resolveCandidate(
        candidate: TrackSourcePlaybackCandidate,
    ): SourcePlaybackResult? {
        val path = candidate.item.canonicalPath ?: return null
        val sourceId = candidate.account.providerType.toBuiltInSourceId()
        val source = sourceRegistry.sourceOrNull(sourceId) ?: return null
        val mediaId = if (candidate.account.providerType.isRemoteServerProvider()) {
            val remoteId = candidate.item.providerItemId ?: return null
            MediaId(sourceId = sourceId, mediaType = MediaType.Track, remoteId = remoteId)
        } else {
            legacyStorageTrackMediaId(
                sourceId = sourceId,
                accountId = StorageId(candidate.item.sourceAccountId)
                    .toLegacyStorageSourceAccountId(),
                path = path,
            )
        }
        return source.resolvePlayback(mediaId)
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

private fun TrackSourcePlaybackCandidate.cacheIdentity(): PlaybackCacheIdentity? {
    val path = item.canonicalPath ?: return null
    val version = item.contentHash
        ?: item.etag
        ?: item.revision
        ?: item.modifiedAtRemote?.let { modifiedAt ->
            "$modifiedAt:${item.sizeBytes?.toString().orEmpty()}"
        }
    return PlaybackCacheIdentity(item.sourceAccountId, path, version)
}

private fun StorageType.toBuiltInSourceId() = when (this) {
    StorageType.LOCAL -> BuiltInSourceIds.Local
    StorageType.WEBDAV -> BuiltInSourceIds.WebDav
    StorageType.ONE_DRIVE -> BuiltInSourceIds.OneDrive
    StorageType.SMB -> BuiltInSourceIds.Smb
}

private fun String.toBuiltInSourceId() = when (this) {
    ProviderTypes.Local -> BuiltInSourceIds.Local
    ProviderTypes.WebDav -> BuiltInSourceIds.WebDav
    ProviderTypes.OneDrive -> BuiltInSourceIds.OneDrive
    ProviderTypes.Smb -> BuiltInSourceIds.Smb
    ProviderTypes.Navidrome -> BuiltInSourceIds.Navidrome
    ProviderTypes.OpenSubsonic -> BuiltInSourceIds.OpenSubsonic
    ProviderTypes.Emby -> BuiltInSourceIds.Emby
    else -> BuiltInSourceIds.WebDav
}

private fun String.isRemoteServerProvider(): Boolean = this == ProviderTypes.Navidrome ||
    this == ProviderTypes.OpenSubsonic ||
    this == ProviderTypes.Emby
