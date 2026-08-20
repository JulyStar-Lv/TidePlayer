package io.github.julystar.musicapp.core.data.media

import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.model.ArtworkCacheKey
import io.github.julystar.musicapp.core.domain.repository.ArtworkRepository
import io.github.julystar.musicapp.database.ArtworkEntity
import io.github.julystar.musicapp.database.MetadataDao
import io.github.julystar.musicapp.database.TrackDao
import io.github.julystar.musicapp.database.TrackEntity
import io.github.julystar.musicapp.singleton.Bridge
import io.github.julystar.musicapp.singleton.RoomLibraryStore
import io.github.julystar.musicapp.core.data.StorageRepositoryImpl
import io.github.julystar.musicapp.source.api.toLegacyStorageArtworkTarget
import io.github.julystar.musicapp.source.storage.toLegacyStorageIdOrNull
import okio.FileSystem
import okio.Path.Companion.toPath
import uniffi.app_backend.MusicId
import uniffi.app_backend.StorageEntryLoc
import uniffi.app_backend.StorageId
import uniffi.app_backend.ctGetAsset

internal interface RemoteArtworkCacheAware {
    suspend fun isRemoteArtwork(artwork: Artwork): Boolean
}

class LegacyArtworkRepository(
    private val bridge: Bridge,
    private val storageRepository: StorageRepositoryImpl,
    private val roomLibraryStore: RoomLibraryStore,
    private val trackDao: TrackDao,
    private val metadataDao: MetadataDao,
    private val pluginArtworkResolver: PluginArtworkResolver? = null,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val navidromeArtworkResolver: NavidromeArtworkResolver? = null,
) : ArtworkRepository, RemoteArtworkCacheAware {
    private val cache = HashMap<Artwork, ByteArray>()
    private val remoteArtwork = HashSet<Artwork>()

    override fun cached(artwork: Artwork): ByteArray? {
        if (artwork in remoteArtwork) return null
        return cache[artwork]
    }

    override suspend fun isRemoteArtwork(artwork: Artwork): Boolean =
        navidromeArtworkResolver?.isRemoteArtwork(artwork) == true

    override suspend fun cacheKey(artwork: Artwork): ArtworkCacheKey? {
        return artwork.resolveRoomArtworkCacheKey(
            findTrack = trackDao::get,
            findTrackArtwork = metadataDao::getArtworkForTrack,
            findAlbumArtwork = metadataDao::getArtworkForAlbum,
        )
    }

    override suspend fun load(artwork: Artwork): ByteArray? {
        val isRemote = isRemoteArtwork(artwork)
        if (isRemote) remoteArtwork += artwork
        if (!isRemote) cache[artwork]?.let { return it }

        if (!isRemote) {
            cacheKey(artwork)?.readLocalArtworkBytes(fileSystem)?.let { bytes ->
                cache[artwork] = bytes
                return bytes
            }
        }

        navidromeArtworkResolver?.load(artwork)?.let { bytes ->
            cache[artwork] = bytes
            return bytes
        }

        if (
            (artwork is Artwork.LibraryTrack && artwork.allowPluginLookup) ||
            artwork is Artwork.LibraryAlbum
        ) {
            pluginArtworkResolver?.load(artwork)?.let { bytes ->
                cache[artwork] = bytes
                return bytes
            }
            return null
        }
        if (artwork is Artwork.LibraryTrack || artwork is Artwork.LibraryCover) return null

        val loc = artwork.resolveLegacyStorageEntryLoc { trackId ->
            roomLibraryStore.resolveTrackLoc(MusicId(trackId))
        } ?: return null
        val storage = storageRepository.storageForRust(loc.storageId) ?: return null
        val bytes = bridge.run { backend ->
            ctGetAsset(backend, storage, loc)
        } ?: return null
        cache[artwork] = bytes
        return bytes
    }

}

internal fun ArtworkCacheKey.readLocalArtworkBytes(fileSystem: FileSystem): ByteArray? {
    return readRegularFile(fileSystem, localPath)
        ?: thumbnailPath?.let { readRegularFile(fileSystem, it) }
}

private fun readRegularFile(fileSystem: FileSystem, path: String): ByteArray? {
    val okioPath = path.toPath()
    val metadata = fileSystem.metadataOrNull(okioPath) ?: return null
    if (!metadata.isRegularFile) return null
    return try {
        fileSystem.read(okioPath) {
            readByteArray()
        }
    } catch (_: Exception) {
        null
    }
}

internal suspend fun Artwork.resolveRoomArtworkCacheKey(
    findTrack: suspend (trackId: Long) -> TrackEntity?,
    findTrackArtwork: suspend (trackId: Long) -> ArtworkEntity?,
    findAlbumArtwork: suspend (albumId: Long) -> ArtworkEntity?,
): ArtworkCacheKey? {
    val entity = when (this) {
        is Artwork.LibraryTrack -> findTrackArtwork(trackId)
            ?: findTrack(trackId)?.albumId?.let { albumId -> findAlbumArtwork(albumId) }
        is Artwork.LibraryAlbum -> findAlbumArtwork(albumId)
        is Artwork.LibraryCover -> findTrackArtwork(trackId)
            ?: findTrack(trackId)?.albumId?.let { albumId -> findAlbumArtwork(albumId) }
        is Artwork.SourceMedia -> null
        is Artwork.LegacyStorageEntry -> null
    }
    return entity?.toArtworkCacheKey()
}

internal fun ArtworkEntity.toArtworkCacheKey(): ArtworkCacheKey {
    return ArtworkCacheKey(
        contentHash = contentHash,
        localPath = localPath,
        thumbnailPath = thumbnailPath,
        width = width,
        height = height,
        mimeType = mimeType,
        pictureType = pictureType,
    )
}

internal suspend fun Artwork.resolveLegacyStorageEntryLoc(
    resolveTrackLoc: suspend (trackId: Long) -> StorageEntryLoc?,
): StorageEntryLoc? {
    return when (this) {
        is Artwork.LibraryTrack -> resolveTrackLoc(trackId)
        is Artwork.LibraryAlbum -> null
        is Artwork.LibraryCover -> null
        is Artwork.SourceMedia -> {
            val target = mediaId.toLegacyStorageArtworkTarget() ?: return null
            val storageId = target.accountId.toLegacyStorageIdOrNull() ?: return null
            StorageEntryLoc(
                storageId = storageId,
                path = target.path,
            )
        }
        is Artwork.LegacyStorageEntry -> StorageEntryLoc(
            storageId = StorageId(storageId),
            path = path,
        )
    }
}
