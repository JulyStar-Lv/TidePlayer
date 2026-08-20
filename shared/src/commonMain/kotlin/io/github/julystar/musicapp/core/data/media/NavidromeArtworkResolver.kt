package io.github.julystar.musicapp.core.data.media

import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.database.ArtworkEntity
import io.github.julystar.musicapp.database.MetadataDao
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.SourceAccountDao
import io.github.julystar.musicapp.database.SourceItemDao
import io.github.julystar.musicapp.database.TrackDao
import io.github.julystar.musicapp.database.TrackSourceRefDao
import io.github.julystar.musicapp.platform.fetchRemoteImageBytes
import io.github.julystar.musicapp.platform.getAppCacheDir
import io.github.julystar.musicapp.source.api.NavidromeProviderConfigurationCodec
import io.github.julystar.musicapp.source.api.RemoteServerGateway
import io.github.julystar.musicapp.source.api.RemoteServerKind
import io.github.julystar.musicapp.source.api.SourcePlaybackResult
import kotlinx.coroutines.CancellationException
import okio.FileSystem
import okio.ByteString.Companion.encodeUtf8
import okio.Path.Companion.toPath

class NavidromeArtworkResolver(
    private val trackDao: TrackDao,
    private val metadataDao: MetadataDao,
    private val trackSourceRefDao: TrackSourceRefDao,
    private val sourceItemDao: SourceItemDao,
    private val sourceAccountDao: SourceAccountDao,
    private val remoteServerGateway: RemoteServerGateway,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val fetchRemoteBytes: suspend (String, Long) -> ByteArray? = ::fetchRemoteImageBytes,
) {
    suspend fun isRemoteArtwork(artwork: Artwork): Boolean {
        val track = when (artwork) {
            is Artwork.LibraryTrack -> trackDao.get(artwork.trackId)
            is Artwork.LibraryAlbum -> trackDao.findByAlbumId(artwork.albumId).firstOrNull()
            else -> null
        } ?: return false
        for (ref in trackSourceRefDao.findByTrackId(track.id)) {
            if (!ref.isAvailable) continue
            val item = sourceItemDao.get(ref.sourceItemId) ?: continue
            if (item.isDeleted) continue
            val account = sourceAccountDao.get(item.sourceAccountId) ?: continue
            if (!account.enabled) continue
            if (account.providerType != ProviderTypes.Navidrome && account.providerType != ProviderTypes.Emby) {
                continue
            }
            val properties = sourceItemDao.propertiesForItems(listOf(item.id))
            val providerItemId = item.providerItemId ?: continue
            val hasArtwork = when (account.providerType) {
                ProviderTypes.Navidrome -> properties.any {
                    it.propertyKey == COVER_ART_PROPERTY && !it.stringValue.isNullOrBlank()
                }
                ProviderTypes.Emby -> providerItemId.isNotBlank() && properties.any {
                    it.propertyKey == IMAGE_TAG_PROPERTY && !it.stringValue.isNullOrBlank()
                }
                else -> false
            }
            if (hasArtwork) return true
        }
        return false
    }

    suspend fun load(artwork: Artwork): ByteArray? {
        val track = when (artwork) {
            is Artwork.LibraryTrack -> trackDao.get(artwork.trackId)
            is Artwork.LibraryAlbum -> trackDao.findByAlbumId(artwork.albumId).firstOrNull()
            else -> null
        } ?: return null

        val persisted = when (artwork) {
            is Artwork.LibraryTrack -> metadataDao.getArtworkForTrack(artwork.trackId)
            is Artwork.LibraryAlbum -> metadataDao.getArtworkForAlbum(artwork.albumId)
            else -> null
        }

        for (ref in trackSourceRefDao.findByTrackId(track.id)) {
            if (!ref.isAvailable) continue
            val item = sourceItemDao.get(ref.sourceItemId) ?: continue
            if (item.isDeleted) continue
            val account = sourceAccountDao.get(item.sourceAccountId) ?: continue
            val kind = when (account.providerType) {
                ProviderTypes.Navidrome -> RemoteServerKind.Navidrome
                ProviderTypes.Emby -> RemoteServerKind.Emby
                else -> continue
            }
            if (!account.enabled) continue
            val properties = sourceItemDao.propertiesForItems(listOf(item.id))
            val providerItemId = item.providerItemId ?: continue
            val imageTag = properties.firstOrNull { it.propertyKey == IMAGE_TAG_PROPERTY }
                ?.stringValue?.takeIf(String::isNotBlank)
            val coverId = when (kind) {
                RemoteServerKind.Navidrome -> properties.firstOrNull { it.propertyKey == COVER_ART_PROPERTY }
                    ?.stringValue?.takeIf(String::isNotBlank)
                RemoteServerKind.Emby -> providerItemId.takeIf { imageTag != null }
                else -> null
            } ?: continue
            val size = if (kind == RemoteServerKind.Navidrome) {
                NavidromeProviderConfigurationCodec.decode(account.providerConfig).coverArtSize
            } else {
                512
            }
            val expectedKey = if (kind == RemoteServerKind.Navidrome) {
                navidromeArtworkCacheKey(item.sourceAccountId, coverId, size)
            } else {
                remoteArtworkCacheKey(
                    provider = account.providerType,
                    sourceAccountId = item.sourceAccountId,
                    providerItemId = providerItemId,
                    imageTag = imageTag ?: coverId,
                    size = size,
                )
            }
            persisted?.takeIf { it.contentHash == expectedKey }
                ?.toArtworkCacheKey()
                ?.readLocalArtworkBytes(fileSystem)
                ?.takeIf(ByteArray::isSupportedImage)
                ?.let { return it }
            val resource = try {
                remoteServerGateway.coverArt(
                    kind,
                    SourceAccountId("storage:${item.sourceAccountId}"),
                    coverId,
                    size,
                    imageTag,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                continue
            }
            val success = resource as? SourcePlaybackResult.Success ?: continue
            val bytes = try {
                fetchRemoteBytes(success.resource.uri, MAX_REMOTE_ARTWORK_BYTES)
                    ?.takeIf(ByteArray::isSupportedImage)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            } ?: continue
            val key = expectedKey
            val path = getAppCacheDir().toPath() / "artwork" / "$key.image"
            try {
                fileSystem.createDirectories(path.parent!!)
                fileSystem.write(path) { write(bytes) }
                metadataDao.upsertArtwork(
                    listOf(
                        ArtworkEntity(
                            trackId = (artwork as? Artwork.LibraryTrack)?.trackId,
                            albumId = (artwork as? Artwork.LibraryAlbum)?.albumId,
                            contentHash = key,
                            localPath = path.toString(),
                            thumbnailPath = null,
                            width = null,
                            height = null,
                            mimeType = bytes.detectImageMimeType(),
                            pictureType = "FrontCover",
                        )
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                continue
            }
            return bytes
        }
        return null
    }
}

internal fun navidromeArtworkCacheKey(sourceAccountId: Long, coverArtId: String, size: Int): String =
    "navidrome-${sourceAccountId}-${"$sourceAccountId:$coverArtId:$size".encodeUtf8().sha256().hex()}"

internal fun remoteArtworkCacheKey(
    provider: String,
    sourceAccountId: Long,
    providerItemId: String,
    imageTag: String,
    size: Int,
): String = "$provider-$sourceAccountId-${"$provider:$sourceAccountId:$providerItemId:$imageTag:$size".encodeUtf8().sha256().hex()}"

private const val COVER_ART_PROPERTY = "coverArtId"
private const val IMAGE_TAG_PROPERTY = "imageTag"
private const val MAX_REMOTE_ARTWORK_BYTES = 8L * 1024 * 1024
