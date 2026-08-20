package io.github.julystar.musicapp.source.server

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.toStorageRouteIdOrNull
import io.github.julystar.musicapp.database.AlbumEntity
import io.github.julystar.musicapp.database.AlbumArtistCrossRef
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.ArtistEntity
import io.github.julystar.musicapp.database.GenreEntity
import io.github.julystar.musicapp.database.MetadataDao
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.SourceAccountDao
import io.github.julystar.musicapp.database.SourceItemDao
import io.github.julystar.musicapp.database.SourceItemEntity
import io.github.julystar.musicapp.database.SourceItemPropertyEntity
import io.github.julystar.musicapp.database.SourceItemTypes
import io.github.julystar.musicapp.database.TrackArtistCrossRef
import io.github.julystar.musicapp.database.TrackDao
import io.github.julystar.musicapp.database.TrackEntity
import io.github.julystar.musicapp.database.TrackGenreCrossRef
import io.github.julystar.musicapp.database.TrackMetadataSources
import io.github.julystar.musicapp.database.TrackSourceRefDao
import io.github.julystar.musicapp.database.TrackSourceRefEntity
import io.github.julystar.musicapp.domain.importing.TrackMatchMethods
import io.github.julystar.musicapp.domain.importing.TrackSourceRoles
import io.github.julystar.musicapp.domain.importing.DURATION_MATCH_TOLERANCE_MS
import io.github.julystar.musicapp.domain.importing.MATCH_CONFIDENCE_EXACT
import io.github.julystar.musicapp.domain.importing.MATCH_CONFIDENCE_STRICT_METADATA
import io.github.julystar.musicapp.domain.importing.normalizeMetadataName
import io.github.julystar.musicapp.domain.importing.normalizedTrackMatchKey
import io.github.julystar.musicapp.domain.importing.stableTrackId
import io.github.julystar.musicapp.platform.currentTimeMillis
import io.github.julystar.musicapp.source.api.RemoteServerGateway
import io.github.julystar.musicapp.source.api.RemoteServerKind
import io.github.julystar.musicapp.source.api.RemoteServerTrack
import kotlinx.coroutines.flow.collect

data class RemoteServerLibrarySyncResult(
    val scanId: String,
    val scanned: Long,
    val added: Long,
    val modified: Long,
    val unchanged: Long,
    val deleted: Long,
)

typealias NavidromeLibrarySyncResult = RemoteServerLibrarySyncResult

internal class RemoteServerLibrarySyncEngine(
    private val database: AppDatabase,
    private val gateway: RemoteServerGateway,
    private val sourceAccountDao: SourceAccountDao,
    private val sourceItemDao: SourceItemDao,
    private val trackSourceRefDao: TrackSourceRefDao,
    private val trackDao: TrackDao,
    private val metadataDao: MetadataDao,
    private val providerKind: RemoteServerKind,
) {
    suspend fun sync(
        accountId: SourceAccountId,
        scanId: String = "navidrome-${currentTimeMillis()}",
        pageSize: Int = 500,
    ): RemoteServerLibrarySyncResult {
        require(scanId.isNotBlank()) { "scanId must not be blank" }
        val sourceAccountId = accountId.toStorageRouteIdOrNull()
            ?: error("$providerKind account must use a storage route")
        check(sourceAccountDao.get(sourceAccountId)?.providerType == providerKind.providerTypeName) {
            "$providerKind sync requires a matching remote server source account"
        }

        val seenProviderIds = mutableSetOf<String>()
        var scanned = 0L
        var added = 0L
        var modified = 0L
        var unchanged = 0L
        gateway.trackPages(
            kind = providerKind,
            accountId = accountId,
            pageSize = pageSize.coerceIn(1, 500),
        ).collect { result ->
            val page = result.getOrThrow()
            check(page.tracks.all { it.accountId == accountId }) {
                "$providerKind gateway returned a mismatched source account"
            }
            val tracks = page.tracks.filter { seenProviderIds.add(it.remoteId) }
            if (tracks.isEmpty()) return@collect
            val counts = applyPage(sourceAccountId, scanId, tracks)
            scanned += counts.scanned
            added += counts.added
            modified += counts.modified
            unchanged += counts.unchanged
        }

        val now = currentTimeMillis()
        var deleted = 0L
        while (true) {
            val missing = sourceItemDao.findMissingTracksForSourceAccount(
                sourceAccountId = sourceAccountId,
                scanId = scanId,
                limit = 500,
            )
            if (missing.isEmpty()) break
            database.useWriterConnection { connection ->
                connection.immediateTransaction {
                    sourceItemDao.upsertAll(
                        missing.map { it.copy(isDeleted = true, lastSyncedAt = now) }
                    )
                    trackSourceRefDao.markUnavailableBySourceItemIds(
                        sourceItemIds = missing.map(SourceItemEntity::id),
                        now = now,
                    )
                }
            }
            deleted += missing.size
        }
        return RemoteServerLibrarySyncResult(
            scanId = scanId,
            scanned = scanned,
            added = added,
            modified = modified,
            unchanged = unchanged,
            deleted = deleted,
        )
    }

    private suspend fun applyPage(
        sourceAccountId: Long,
        scanId: String,
        tracks: List<RemoteServerTrack>,
    ): SyncBatchCounts {
        val now = currentTimeMillis()
        var counts = SyncBatchCounts()
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                val providerIds = tracks.map(RemoteServerTrack::remoteId)
                val existingItems = sourceItemDao
                    .findByProviderItemIds(sourceAccountId, providerIds)
                    .associateBy { it.providerItemId }
                val sourceItems = tracks.map { track ->
                    val existing = existingItems[track.remoteId]
                    SourceItemEntity(
                        id = existing?.id ?: 0,
                        sourceAccountId = sourceAccountId,
                        libraryRootId = existing?.libraryRootId,
                        itemType = SourceItemTypes.Track,
                        providerItemId = track.remoteId,
                        parentProviderItemId = existing?.parentProviderItemId,
                        canonicalPath = existing?.canonicalPath,
                        displayPath = existing?.displayPath,
                        displayName = track.title.ifBlank { existing?.displayName ?: track.remoteId },
                        mimeType = track.mimeType ?: existing?.mimeType,
                        sizeBytes = existing?.sizeBytes,
                        etag = existing?.etag,
                        revision = existing?.revision,
                        createdAtRemote = existing?.createdAtRemote,
                        modifiedAtRemote = existing?.modifiedAtRemote,
                        contentHash = existing?.contentHash,
                        audioFingerprint = existing?.audioFingerprint,
                        isDeleted = false,
                        firstSyncedAt = existing?.firstSyncedAt ?: now,
                        lastSyncedAt = now,
                        lastSeenScanId = scanId,
                    )
                }
                sourceItemDao.upsertAll(sourceItems)
                val persistedItems = sourceItemDao
                    .findByProviderItemIds(sourceAccountId, providerIds)
                    .associateBy { it.providerItemId }
                val tracksByProviderId = tracks.associateBy(RemoteServerTrack::remoteId)
                val itemIds = persistedItems.values.map(SourceItemEntity::id)
                val existingPropertiesByItem = sourceItemDao.propertiesForItems(itemIds)
                    .groupBy(SourceItemPropertyEntity::sourceItemId)
                val providerProperties = persistedItems.values.flatMap { item ->
                    val track = tracksByProviderId[item.providerItemId] ?: return@flatMap emptyList()
                    buildList {
                        val coverId = (if (providerKind == RemoteServerKind.Emby) {
                            track.imageTag
                        } else {
                            track.coverArtId
                        })?.trim()
                            ?.takeIf(String::isNotEmpty)
                        coverId?.let {
                            add(item.stringProperty(
                                if (providerKind == RemoteServerKind.Emby) IMAGE_TAG_PROPERTY else COVER_ART_PROPERTY,
                                it,
                            ))
                        }
                        if (providerKind == RemoteServerKind.Emby) {
                            track.sourceMediaId?.trim()?.takeIf(String::isNotEmpty)
                                ?.let { add(item.stringProperty(SOURCE_MEDIA_ID_PROPERTY, it)) }
                            track.albumId?.trim()?.takeIf(String::isNotEmpty)
                                ?.let { add(item.stringProperty(ALBUM_ID_PROPERTY, it)) }
                            track.userData?.isFavorite?.let {
                                add(item.booleanProperty(EMBY_FAVORITE_PROPERTY, it))
                            }
                            track.userData?.playCount?.let {
                                add(item.longProperty(EMBY_PLAY_COUNT_PROPERTY, it.toLong()))
                            }
                            track.userData?.lastPlayedDate?.trim()?.takeIf(String::isNotEmpty)
                                ?.let { add(item.stringProperty(EMBY_LAST_PLAYED_PROPERTY, it)) }
                            track.userData?.played?.let {
                                add(item.booleanProperty(EMBY_PLAYED_PROPERTY, it))
                            }
                        }
                    }
                }
                sourceItemDao.deletePropertyForItems(itemIds, COVER_ART_PROPERTY)
                if (providerKind == RemoteServerKind.Emby) {
                    listOf(
                        IMAGE_TAG_PROPERTY,
                        SOURCE_MEDIA_ID_PROPERTY,
                        ALBUM_ID_PROPERTY,
                        EMBY_FAVORITE_PROPERTY,
                        EMBY_PLAY_COUNT_PROPERTY,
                        EMBY_LAST_PLAYED_PROPERTY,
                        EMBY_PLAYED_PROPERTY,
                    ).forEach { key -> sourceItemDao.deletePropertyForItems(itemIds, key) }
                }
                if (providerProperties.isNotEmpty()) sourceItemDao.upsertProperties(providerProperties)
                val existingRefs = trackSourceRefDao.findBySourceItemIds(itemIds)
                    .associateBy { it.sourceItemId }
                val existingTracks = trackDao.findByIds(existingRefs.values.map { it.trackId })
                    .associateBy { it.id }

                val albumsByName = ensureAlbums(tracks)
                val artistsByName = ensureArtists(tracks)
                val genresByName = ensureGenres(tracks)
                val newTracks = mutableListOf<TrackEntity>()
                val refs = mutableListOf<TrackSourceRefEntity>()
                val trackArtists = mutableListOf<TrackArtistCrossRef>()
                val trackGenres = mutableListOf<TrackGenreCrossRef>()
                val albumArtists = mutableListOf<AlbumArtistCrossRef>()
                val trackArtistRefreshIds = mutableSetOf<Long>()
                val trackGenreRefreshIds = mutableSetOf<Long>()
                val albumArtistRefreshIds = mutableSetOf<Long>()

                tracks.forEach { remote ->
                    val item = requireNotNull(persistedItems[remote.remoteId])
                    val existingRef = existingRefs[item.id]
                    val existingTrack = existingRef?.let { existingTracks[it.trackId] }
                    val canonicalTrack = existingTrack ?: findCanonicalTrack(remote)
                    val preserveCanonicalMetadata = existingTrack == null && canonicalTrack != null
                    val album = remote.album?.trim()?.takeIf(String::isNotEmpty)
                        ?.let { albumsByName[normalizeMetadataName(it)] }
                    val track = buildTrack(
                        remote = remote,
                        sourceItem = item,
                        existing = canonicalTrack,
                        preserveExistingMetadata = preserveCanonicalMetadata,
                        albumId = album?.id,
                        now = now,
                    )
                    val sourceChanged = existingItems[remote.remoteId]?.let { old ->
                        old.isDeleted || old.displayName != item.displayName || old.mimeType != item.mimeType
                    } ?: true
                    val propertyChanged = providerKind == RemoteServerKind.Emby &&
                        !remote.embyPropertiesMatch(existingPropertiesByItem[item.id].orEmpty())
                    val trackChanged = existingTrack == null ||
                        track.copy(updatedAt = existingTrack.updatedAt) != existingTrack
                    val ref = buildRef(
                        remote = remote,
                        track = track,
                        item = item,
                        existing = existingRef,
                        now = now,
                        role = existingRef?.role ?: if (preserveCanonicalMetadata) {
                            TrackSourceRoles.Alternate
                        } else {
                            TrackSourceRoles.Primary
                        },
                        matchMethod = existingRef?.matchMethod ?: if (preserveCanonicalMetadata) {
                            TrackMatchMethods.StrictMetadata
                        } else {
                            TrackMatchMethods.SourceIdentity
                        },
                        matchConfidence = existingRef?.matchConfidence ?: if (preserveCanonicalMetadata) {
                            MATCH_CONFIDENCE_STRICT_METADATA
                        } else {
                            MATCH_CONFIDENCE_EXACT
                        },
                    )
                    val refChanged = existingRef == null ||
                        ref.copy(updatedAt = existingRef.updatedAt) != existingRef
                    counts = counts.plus(
                        added = if (existingRef == null) 1 else 0,
                        modified = if (existingRef != null && (sourceChanged || propertyChanged || trackChanged || refChanged)) 1 else 0,
                        unchanged = if (existingRef != null && !sourceChanged && !propertyChanged && !trackChanged && !refChanged) 1 else 0,
                    )
                    newTracks += track
                    refs += ref
                    if (track.metadataLocked || preserveCanonicalMetadata) return@forEach
                    val remoteArtists = remote.artists.ifEmpty { listOfNotNull(remote.artist) }
                    remoteArtists.map(String::trim).filter(String::isNotEmpty).forEachIndexed { index, artistName ->
                        trackArtistRefreshIds += track.id
                        artistsByName[normalizeMetadataName(artistName)]
                            ?.let { artist -> trackArtists += TrackArtistCrossRef(track.id, artist.id, index) }
                    }
                    val remoteGenres = remote.genres.ifEmpty { listOfNotNull(remote.genre) }
                    remoteGenres.map(String::trim).filter(String::isNotEmpty).forEach { genreName ->
                        trackGenreRefreshIds += track.id
                        genresByName[normalizeMetadataName(genreName)]
                            ?.let { genre -> trackGenres += TrackGenreCrossRef(track.id, genre.id) }
                    }
                    remote.albumArtist?.trim()?.takeIf(String::isNotEmpty)?.let { artistName ->
                        album?.let { albumEntity ->
                            albumArtistRefreshIds += albumEntity.id
                            artistsByName[normalizeMetadataName(artistName)]
                                ?.let { artist -> albumArtists += AlbumArtistCrossRef(albumEntity.id, artist.id, 0) }
                        }
                    }
                }
                trackDao.upsertAll(newTracks)
                trackSourceRefDao.upsertAll(refs)
                if (trackArtistRefreshIds.isNotEmpty()) {
                    metadataDao.deleteTrackArtistsForTracks(trackArtistRefreshIds.toList())
                }
                if (trackGenreRefreshIds.isNotEmpty()) {
                    metadataDao.deleteTrackGenresForTracks(trackGenreRefreshIds.toList())
                }
                if (trackArtists.isNotEmpty()) metadataDao.upsertTrackArtists(trackArtists)
                if (trackGenres.isNotEmpty()) metadataDao.upsertTrackGenres(trackGenres)
                if (albumArtistRefreshIds.isNotEmpty()) {
                    metadataDao.deleteAlbumArtistsForAlbums(albumArtistRefreshIds.toList())
                    if (albumArtists.isNotEmpty()) {
                        metadataDao.upsertAlbumArtists(albumArtists)
                    }
                }
            }
        }
        counts = counts.copy(scanned = tracks.size.toLong())
        return counts
    }

    private suspend fun ensureAlbums(tracks: List<RemoteServerTrack>): Map<String, AlbumEntity> {
        val names = tracks.mapNotNull { it.album?.trim()?.takeIf(String::isNotEmpty) }
            .distinctBy(::normalizeMetadataName)
        if (names.isEmpty()) return emptyMap()
        metadataDao.insertAlbums(names.map { AlbumEntity(name = it, normalizedName = normalizeMetadataName(it), sortName = null, year = null, artworkId = null) })
        return metadataDao.findAlbumsByNormalizedNames(names.map(::normalizeMetadataName)).associateBy { it.normalizedName }
    }

    private suspend fun ensureArtists(tracks: List<RemoteServerTrack>): Map<String, ArtistEntity> {
        val names = tracks.flatMap { it.artists + listOfNotNull(it.artist, it.albumArtist) }
            .map(String::trim).filter(String::isNotEmpty).distinctBy(::normalizeMetadataName)
        if (names.isEmpty()) return emptyMap()
        metadataDao.insertArtists(names.map { ArtistEntity(name = it, normalizedName = normalizeMetadataName(it), sortName = null) })
        return metadataDao.findArtistsByNormalizedNames(names.map(::normalizeMetadataName)).associateBy { it.normalizedName }
    }

    private suspend fun ensureGenres(tracks: List<RemoteServerTrack>): Map<String, GenreEntity> {
        val names = tracks.flatMap { it.genres + listOfNotNull(it.genre) }
            .distinctBy(::normalizeMetadataName)
        if (names.isEmpty()) return emptyMap()
        metadataDao.insertGenres(names.map { GenreEntity(name = it, normalizedName = normalizeMetadataName(it)) })
        return metadataDao.findGenresByNormalizedNames(names.map(::normalizeMetadataName)).associateBy { it.normalizedName }
    }

    private suspend fun findCanonicalTrack(remote: RemoteServerTrack): TrackEntity? {
        val duration = remote.durationMs ?: return null
        val title = remote.title.normalizedTrackMatchKey()
        val artist = remote.artist.normalizedTrackMatchKey()
        val album = remote.album.normalizedTrackMatchKey()
        if (title.isBlank() || artist.isBlank() || album.isBlank()) return null
        return trackDao.findByStrictMetadata(
            titleKey = title,
            artistKey = artist,
            albumKey = album,
            minDurationMs = (duration - DURATION_MATCH_TOLERANCE_MS).coerceAtLeast(0),
            maxDurationMs = duration + DURATION_MATCH_TOLERANCE_MS,
        ).singleOrNull()
    }

    private fun buildTrack(
        remote: RemoteServerTrack,
        sourceItem: SourceItemEntity,
        existing: TrackEntity?,
        albumId: Long?,
        now: Long,
        preserveExistingMetadata: Boolean,
    ): TrackEntity {
        if (preserveExistingMetadata || existing?.metadataLocked == true) {
            return existing?.copy(updatedAt = now)
                ?: error("existing track required when preserving metadata")
        }
        val properties = remote.audioProperties
        return TrackEntity(
            id = existing?.id ?: stableTrackId(sourceItem.sourceAccountId, "${providerKind.name.lowercase()}:${remote.remoteId}"),
            title = remote.title.ifBlank { existing?.title ?: remote.remoteId },
            sortTitle = existing?.sortTitle,
            albumId = albumId ?: existing?.albumId,
            albumArtist = remote.albumArtist ?: existing?.albumArtist,
            composer = existing?.composer,
            comment = existing?.comment,
            grouping = existing?.grouping,
            durationMs = remote.durationMs ?: existing?.durationMs,
            discNumber = remote.discNumber ?: existing?.discNumber,
            discTotal = existing?.discTotal,
            trackNumber = remote.track ?: existing?.trackNumber,
            trackTotal = existing?.trackTotal,
            year = remote.year ?: existing?.year,
            date = existing?.date,
            sampleRate = remote.sampleRate ?: properties?.sampleRateHz ?: existing?.sampleRate,
            bitRate = remote.bitRate ?: properties?.bitrateKbps ?: existing?.bitRate,
            bitsPerSample = remote.bitDepth ?: properties?.bitDepth ?: existing?.bitsPerSample,
            channels = remote.channelCount ?: properties?.channels ?: existing?.channels,
            channelLayout = properties?.channelLayout ?: existing?.channelLayout,
            codec = properties?.codec ?: existing?.codec,
            container = properties?.container ?: remote.suffix ?: existing?.container,
            lossless = properties?.lossless ?: existing?.lossless,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            lastPlayedAt = existing?.lastPlayedAt,
            artist = remote.artist ?: existing?.artist,
            metadataSource = TrackMetadataSources.Server,
            metadataLocked = existing?.metadataLocked ?: false,
            metadataSourceId = existing?.metadataSourceId,
            metadataExternalId = existing?.metadataExternalId,
            metadataAppliedAt = existing?.metadataAppliedAt,
        )
    }

    private fun buildRef(
        remote: RemoteServerTrack,
        track: TrackEntity,
        item: SourceItemEntity,
        existing: TrackSourceRefEntity?,
        now: Long,
        role: String,
        matchMethod: String,
        matchConfidence: Int,
    ) = TrackSourceRefEntity(
        trackId = track.id,
        sourceItemId = item.id,
        role = role,
        matchMethod = matchMethod,
        matchConfidence = matchConfidence,
        isPreferred = existing?.isPreferred ?: (role != TrackSourceRoles.Alternate),
        isAvailable = true,
        isDownloaded = existing?.isDownloaded ?: false,
        playable = true,
        downloadable = true,
        codec = remote.audioProperties?.codec ?: track.codec,
        container = remote.audioProperties?.container ?: remote.suffix ?: track.container,
        bitRate = remote.bitRate ?: remote.audioProperties?.bitrateKbps ?: track.bitRate,
        sampleRate = remote.sampleRate ?: remote.audioProperties?.sampleRateHz ?: track.sampleRate,
        bitsPerSample = remote.bitDepth ?: remote.audioProperties?.bitDepth ?: track.bitsPerSample,
        channels = remote.channelCount ?: remote.audioProperties?.channels ?: track.channels,
        channelLayout = remote.audioProperties?.channelLayout ?: track.channelLayout,
        lossless = remote.audioProperties?.lossless ?: track.lossless,
        createdAt = existing?.createdAt ?: now,
        updatedAt = now,
    )
}

class NavidromeLibrarySyncCoordinator(
    database: AppDatabase,
    gateway: RemoteServerGateway,
    sourceAccountDao: SourceAccountDao,
    sourceItemDao: SourceItemDao,
    trackSourceRefDao: TrackSourceRefDao,
    trackDao: TrackDao,
    metadataDao: MetadataDao,
) {
    private val engine = RemoteServerLibrarySyncEngine(
        database = database,
        gateway = gateway,
        sourceAccountDao = sourceAccountDao,
        sourceItemDao = sourceItemDao,
        trackSourceRefDao = trackSourceRefDao,
        trackDao = trackDao,
        metadataDao = metadataDao,
        providerKind = RemoteServerKind.Navidrome,
    )

    suspend fun sync(
        accountId: SourceAccountId,
        scanId: String = "navidrome-${currentTimeMillis()}",
        pageSize: Int = 500,
    ): NavidromeLibrarySyncResult = engine.sync(accountId, scanId, pageSize)
}

private const val COVER_ART_PROPERTY = "coverArtId"
private const val IMAGE_TAG_PROPERTY = "imageTag"
private const val SOURCE_MEDIA_ID_PROPERTY = "sourceMediaId"
private const val ALBUM_ID_PROPERTY = "albumId"
private const val EMBY_FAVORITE_PROPERTY = "embyIsFavorite"
private const val EMBY_PLAY_COUNT_PROPERTY = "embyPlayCount"
private const val EMBY_LAST_PLAYED_PROPERTY = "embyLastPlayedDate"
private const val EMBY_PLAYED_PROPERTY = "embyPlayed"

private val RemoteServerKind.providerTypeName: String
    get() = when (this) {
        RemoteServerKind.Navidrome -> ProviderTypes.Navidrome
        RemoteServerKind.OpenSubsonic -> ProviderTypes.OpenSubsonic
        RemoteServerKind.Emby -> ProviderTypes.Emby
    }

private fun SourceItemEntity.stringProperty(key: String, value: String) = SourceItemPropertyEntity(
    sourceItemId = id,
    propertyKey = key,
    stringValue = value,
    longValue = null,
    doubleValue = null,
    booleanValue = null,
)

private fun SourceItemEntity.longProperty(key: String, value: Long) = SourceItemPropertyEntity(
    sourceItemId = id,
    propertyKey = key,
    stringValue = null,
    longValue = value,
    doubleValue = null,
    booleanValue = null,
)

private fun SourceItemEntity.booleanProperty(key: String, value: Boolean) = SourceItemPropertyEntity(
    sourceItemId = id,
    propertyKey = key,
    stringValue = null,
    longValue = null,
    doubleValue = null,
    booleanValue = value,
)

private fun RemoteServerTrack.embyPropertiesMatch(
    properties: List<SourceItemPropertyEntity>,
): Boolean {
    val expected = buildMap {
        fun add(key: String, value: String?) {
            value?.trim()?.takeIf(String::isNotEmpty)?.let { put(key, it) }
        }
        add(IMAGE_TAG_PROPERTY, imageTag)
        add(SOURCE_MEDIA_ID_PROPERTY, sourceMediaId)
        add(ALBUM_ID_PROPERTY, albumId)
        add(EMBY_FAVORITE_PROPERTY, userData?.isFavorite?.toString())
        add(EMBY_PLAY_COUNT_PROPERTY, userData?.playCount?.toString())
        add(EMBY_LAST_PLAYED_PROPERTY, userData?.lastPlayedDate)
        add(EMBY_PLAYED_PROPERTY, userData?.played?.toString())
    }
    val managedKeys = setOf(
        IMAGE_TAG_PROPERTY,
        SOURCE_MEDIA_ID_PROPERTY,
        ALBUM_ID_PROPERTY,
        EMBY_FAVORITE_PROPERTY,
        EMBY_PLAY_COUNT_PROPERTY,
        EMBY_LAST_PLAYED_PROPERTY,
        EMBY_PLAYED_PROPERTY,
    )
    val actual = properties.mapNotNull { property ->
        if (property.propertyKey !in managedKeys) return@mapNotNull null
        val value = property.stringValue ?: property.longValue?.toString() ?: property.booleanValue?.toString()
        value?.let { property.propertyKey to it }
    }.toMap()
    return expected == actual
}

private data class SyncBatchCounts(
    val scanned: Long = 0,
    val added: Long = 0,
    val modified: Long = 0,
    val unchanged: Long = 0,
) {
    fun plus(added: Long, modified: Long, unchanged: Long) = copy(
        added = this.added + added,
        modified = this.modified + modified,
        unchanged = this.unchanged + unchanged,
    )
}
