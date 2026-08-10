package io.github.julystar.musicapp.service.download.data.finalization

import io.github.julystar.musicapp.core.data.selectLyrics
import io.github.julystar.musicapp.core.domain.model.DiagnosticLogCategory
import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.toStorageRouteIdOrNull
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.database.AlbumEntity
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.ArtistEntity
import io.github.julystar.musicapp.database.GenreEntity
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.SourceAccountEntity
import io.github.julystar.musicapp.database.SourceItemEntity
import io.github.julystar.musicapp.database.TrackArtistCrossRef
import io.github.julystar.musicapp.database.TrackEntity
import io.github.julystar.musicapp.database.TrackGenreCrossRef
import io.github.julystar.musicapp.database.TrackMetadataSources
import io.github.julystar.musicapp.database.TrackSourceRefEntity
import io.github.julystar.musicapp.diagnostics.AppLogger
import io.github.julystar.musicapp.platform.currentTimeMillis
import io.github.julystar.musicapp.service.download.domain.ArtworkSnapshot
import io.github.julystar.musicapp.service.download.domain.DownloadFinalizationError
import io.github.julystar.musicapp.service.download.domain.DownloadFinalizationRequest
import io.github.julystar.musicapp.service.download.domain.DownloadFinalizationResult
import io.github.julystar.musicapp.service.download.domain.DownloadFinalizer
import io.github.julystar.musicapp.service.download.domain.MetadataSnapshot
import io.github.julystar.musicapp.service.download.domain.MetadataSource
import io.github.julystar.musicapp.service.download.domain.MetadataValue
import io.github.julystar.musicapp.service.playback.data.PlayerRepository
import io.github.julystar.musicapp.source.api.toLegacyStoragePlaybackTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileSystem
import okio.Path.Companion.toPath
import uniffi.app_backend.AudioMetadataWriteRequest
import uniffi.app_backend.AudioMetadataWriteResult
import uniffi.app_backend.LocalMetadataSummary
import uniffi.app_backend.MetadataArtworkWriteRequest
import uniffi.app_backend.MetadataLyricsWriteRequest
import uniffi.app_backend.MetadataMergeMode
import uniffi.app_backend.MetadataWriteFields
import uniffi.app_backend.ctCleanupMetadataTemporaryFile
import uniffi.app_backend.ctWriteAudioMetadata

class DownloadedMediaFinalizer(
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val playerRepository: Lazy<PlayerRepository>? = null,
    private val nowEpochMs: () -> Long = ::currentTimeMillis,
) : DownloadFinalizer {
    private val librarySyncMutex = Mutex()

    override suspend fun finalize(
        request: DownloadFinalizationRequest,
    ): DownloadFinalizationResult {
        val correlationId = request.mediaId.finalizationCorrelationId()
        AppLogger.info(
            category = DiagnosticLogCategory.Metadata,
            target = LOG_TARGET,
            message = "Download finalization started",
            correlationId = correlationId,
            fields = mapOf("source" to request.mediaId.sourceId.value),
        )
        val localMetadata = runCatching {
            FileSystem.SYSTEM.metadataOrNull(request.localPath.toPath())
        }.getOrElse { error ->
            return failure(
                correlationId = correlationId,
                error = DownloadFinalizationError.UnsafeFinalFile,
                message = "Final media file could not be inspected: ${error.safeMessage()}",
            )
        } ?: return failure(
            correlationId = correlationId,
            error = DownloadFinalizationError.MissingFile,
            message = "Final media file is missing",
        )
        val actualBytes = localMetadata.size ?: 0L
        if (!localMetadata.isRegularFile || actualBytes <= 0L) {
            return failure(
                correlationId = correlationId,
                error = DownloadFinalizationError.UnsafeFinalFile,
                message = "Final media path is not a non-empty regular file",
            )
        }
        val expectedBytes = request.expectedBytes?.takeIf { expected -> expected > 0L }
        if (expectedBytes != null && expectedBytes != actualBytes) {
            return failure(
                correlationId = correlationId,
                error = DownloadFinalizationError.UnsafeFinalFile,
                message = "Final media byte count does not match the completed transfer",
            )
        }
        val settings = settingsRepository.settings.first()
        val existingTrackId = findTrackId(request.mediaId)
        val existingTrack = existingTrackId?.let { database.trackDao().get(it) }
        val snapshot = resolveSnapshot(request, existingTrack)
        AppLogger.debug(
            category = DiagnosticLogCategory.Metadata,
            target = LOG_TARGET,
            message = "Metadata snapshot resolved",
            correlationId = correlationId,
            fields = mapOf(
                "trackFound" to (existingTrack != null).toString(),
                "metadataEnabled" to settings.downloadFinalization.enrichMetadata.toString(),
            ),
        )

        val warnings = mutableListOf<String>()
        val writeResult = if (settings.downloadFinalization.enrichMetadata) {
            try {
                runCatching { ctCleanupMetadataTemporaryFile(request.localPath) }
                    .onFailure { error ->
                        warnings += "Unable to clean a stale metadata temporary file: ${error.safeMessage()}"
                    }
                ctWriteAudioMetadata(
                    snapshot.toWriteRequest(
                        path = request.localPath,
                        saveSidecars = settings.downloadFinalization.saveSidecarLyrics,
                    )
                ).also { result -> warnings += result.warnings }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                warnings += "Metadata enhancement failed; the original downloaded audio was preserved: " +
                    error.safeMessage()
                null
            }
        } else {
            null
        }

        val trackId = try {
            librarySyncMutex.withLock {
                synchronizeLibrary(
                    request = request,
                    snapshot = snapshot,
                    existingTrack = existingTrack,
                    verified = writeResult?.verified,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            warnings += "Downloaded audio was saved, but its library record could not be refreshed: " +
                error.safeMessage()
            existingTrackId
        }

        if (trackId != null) {
            runCatching { playerRepository?.value?.refreshCurrentMetadata() }
                .onFailure { error ->
                    warnings += "Current playback metadata could not be refreshed: ${error.safeMessage()}"
                }
        }
        if (warnings.isEmpty()) {
            AppLogger.info(
                DiagnosticLogCategory.Metadata,
                LOG_TARGET,
                "Download finalization completed",
                correlationId,
                fields = mapOf("verified" to (writeResult?.verified != null).toString()),
            )
        } else {
            AppLogger.warn(
                category = DiagnosticLogCategory.Metadata,
                target = LOG_TARGET,
                message = "Download finalization completed with warnings",
                correlationId = correlationId,
                fields = mapOf("warningCount" to warnings.size.toString()),
            )
        }
        return DownloadFinalizationResult.Success(
            localPath = request.localPath,
            warnings = warnings.distinct(),
            libraryTrackId = trackId,
        )
    }

    private fun failure(
        correlationId: String,
        error: DownloadFinalizationError,
        message: String,
    ): DownloadFinalizationResult.Failure {
        AppLogger.error(
            category = DiagnosticLogCategory.Metadata,
            target = LOG_TARGET,
            message = "Download finalization failed",
            correlationId = correlationId,
            fields = mapOf("reason" to error.name),
        )
        return DownloadFinalizationResult.Failure(error, message)
    }

    private suspend fun findTrackId(mediaId: MediaId): Long? {
        val target = mediaId.toLegacyStoragePlaybackTarget() ?: return null
        val storageId = target.accountId.toStorageRouteIdOrNull() ?: return null
        val sourceItem = database.sourceItemDao().findByPath(storageId, target.path) ?: return null
        return database.trackSourceRefDao().findBySourceItemIds(listOf(sourceItem.id))
            .firstOrNull()
            ?.trackId
    }

    private suspend fun resolveSnapshot(
        request: DownloadFinalizationRequest,
        track: TrackEntity?,
    ): MetadataSnapshot {
        if (track == null) return request.toFallbackSnapshot()
        val metadataDao = database.metadataDao()
        val source = when {
            track.metadataLocked -> MetadataSource.User
            track.metadataSource == TrackMetadataSources.Plugin -> MetadataSource.Plugin
            else -> MetadataSource.Database
        }
        val artists = metadataDao.artistNamesForTrack(track.id)
            .ifEmpty { track.artist?.split(',').orEmpty() }
            .mapNotNull { value -> value.metadataValueOrNull(source) }
        val album = track.albumId?.let { metadataDao.getAlbum(it) }
        val artwork = metadataDao.getArtworkForTrack(track.id)
            ?: track.albumId?.let { metadataDao.getArtworkForAlbum(it) }
        val lyricSettings = settingsRepository.settings.first().lyrics
        val lyrics = metadataDao.getLyricsCandidates(track.id).selectLyrics(lyricSettings)
        return MetadataSnapshot(
            title = track.title.metadataValueOrNull(source),
            artists = artists,
            albumArtist = track.albumArtist.metadataValueOrNull(source),
            album = album?.name.metadataValueOrNull(source),
            composer = track.composer.metadataValueOrNull(source),
            lyricist = track.lyricist.metadataValueOrNull(source),
            conductor = track.conductor.metadataValueOrNull(source),
            genre = metadataDao.genreNamesForTrack(track.id)
                .joinToString("; ")
                .metadataValueOrNull(source),
            grouping = track.grouping.metadataValueOrNull(source),
            comment = track.comment.metadataValueOrNull(source),
            copyright = track.copyright.metadataValueOrNull(source),
            publisher = track.publisher.metadataValueOrNull(source),
            date = (track.date ?: track.year?.toString()).metadataValueOrNull(source),
            originalReleaseDate = track.originalReleaseDate.metadataValueOrNull(source),
            trackNumber = track.trackNumber.metadataValueOrNull(source),
            trackTotal = track.trackTotal.metadataValueOrNull(source),
            discNumber = track.discNumber.metadataValueOrNull(source),
            discTotal = track.discTotal.metadataValueOrNull(source),
            bpm = track.bpm.metadataValueOrNull(source),
            musicalKey = track.musicalKey.metadataValueOrNull(source),
            isrc = track.isrc.metadataValueOrNull(source),
            musicBrainzRecordingId = track.musicBrainzRecordingId.metadataValueOrNull(source),
            musicBrainzTrackId = track.musicBrainzTrackId.metadataValueOrNull(source),
            musicBrainzReleaseId = track.musicBrainzReleaseId.metadataValueOrNull(source),
            musicBrainzReleaseGroupId = track.musicBrainzReleaseGroupId.metadataValueOrNull(source),
            musicBrainzArtistId = track.musicBrainzArtistId.metadataValueOrNull(source),
            musicBrainzReleaseArtistId = track.musicBrainzReleaseArtistId.metadataValueOrNull(source),
            musicBrainzWorkId = track.musicBrainzWorkId.metadataValueOrNull(source),
            replayGainTrackGain = track.replayGainTrackGain.metadataValueOrNull(source),
            replayGainTrackPeak = track.replayGainTrackPeak.metadataValueOrNull(source),
            replayGainAlbumGain = track.replayGainAlbumGain.metadataValueOrNull(source),
            replayGainAlbumPeak = track.replayGainAlbumPeak.metadataValueOrNull(source),
            artwork = artwork?.let {
                ArtworkSnapshot(
                    localPath = it.localPath,
                    mimeType = it.mimeType,
                    source = source,
                )
            },
            lyrics = lyrics?.toLyricsSnapshot(),
        )
    }

    private suspend fun synchronizeLibrary(
        request: DownloadFinalizationRequest,
        snapshot: MetadataSnapshot,
        existingTrack: TrackEntity?,
        verified: LocalMetadataSummary?,
    ): Long {
        val now = nowEpochMs()
        val track = existingTrack?.refreshedFrom(verified, now)
            ?: createTrack(request, snapshot, verified, now)
        database.trackDao().upsertAll(listOf(track))
        registerLocalSource(request, track.id, verified, now)
        return track.id
    }

    private suspend fun createTrack(
        request: DownloadFinalizationRequest,
        snapshot: MetadataSnapshot,
        verified: LocalMetadataSummary?,
        now: Long,
    ): TrackEntity {
        val albumName = verified?.album ?: snapshot.album?.value ?: request.fallbackAlbum
        val albumId = albumName?.takeIf(String::isNotBlank)?.let { ensureAlbum(it) }
        val trackId = (database.trackDao().maxId() ?: 0L) + 1L
        val artists = verified?.artists.orEmpty()
            .ifEmpty { snapshot.artists.map { value -> value.value } }
            .ifEmpty { listOfNotNull(request.fallbackArtist) }
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
        val genres = verified?.genre.orEmpty().split(';', ',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
        val entity = TrackEntity(
            id = trackId,
            title = verified?.title ?: snapshot.title?.value ?: request.fallbackTitle,
            sortTitle = null,
            albumId = albumId,
            albumArtist = verified?.albumArtist ?: snapshot.albumArtist?.value,
            composer = verified?.composer ?: snapshot.composer?.value,
            comment = verified?.comment ?: snapshot.comment?.value,
            grouping = verified?.grouping ?: snapshot.grouping?.value,
            durationMs = verified?.durationMs?.toLong() ?: request.expectedDurationMs,
            discNumber = verified?.discNumber?.toInt() ?: snapshot.discNumber?.value,
            discTotal = verified?.discTotal?.toInt() ?: snapshot.discTotal?.value,
            trackNumber = verified?.trackNumber?.toInt() ?: snapshot.trackNumber?.value,
            trackTotal = verified?.trackTotal?.toInt() ?: snapshot.trackTotal?.value,
            year = verified?.date?.take(4)?.toIntOrNull(),
            date = verified?.date ?: snapshot.date?.value,
            sampleRate = verified?.sampleRate?.toInt(),
            bitRate = verified?.overallBitrate?.toInt(),
            bitsPerSample = verified?.bitDepth?.toInt(),
            channels = verified?.channels?.toInt(),
            channelLayout = verified?.channelLayout,
            codec = verified?.codec,
            container = verified?.container,
            lossless = verified?.lossless,
            createdAt = now,
            updatedAt = now,
            artist = verified?.artist ?: artists.joinToString(", ").takeIf(String::isNotBlank),
            lyricist = verified?.lyricist ?: snapshot.lyricist?.value,
            conductor = verified?.conductor ?: snapshot.conductor?.value,
            copyright = verified?.copyright ?: snapshot.copyright?.value,
            publisher = verified?.publisher ?: snapshot.publisher?.value,
            originalReleaseDate = verified?.originalReleaseDate ?: snapshot.originalReleaseDate?.value,
            bpm = verified?.bpm ?: snapshot.bpm?.value,
            musicalKey = verified?.musicalKey ?: snapshot.musicalKey?.value,
            isrc = verified?.isrc ?: snapshot.isrc?.value,
            musicBrainzRecordingId = verified?.musicbrainzRecordingId ?: snapshot.musicBrainzRecordingId?.value,
            musicBrainzTrackId = verified?.musicbrainzTrackId ?: snapshot.musicBrainzTrackId?.value,
            musicBrainzReleaseId = verified?.musicbrainzReleaseId ?: snapshot.musicBrainzReleaseId?.value,
            musicBrainzReleaseGroupId = verified?.musicbrainzReleaseGroupId
                ?: snapshot.musicBrainzReleaseGroupId?.value,
            musicBrainzArtistId = verified?.musicbrainzArtistId ?: snapshot.musicBrainzArtistId?.value,
            musicBrainzReleaseArtistId = verified?.musicbrainzReleaseArtistId
                ?: snapshot.musicBrainzReleaseArtistId?.value,
            musicBrainzWorkId = verified?.musicbrainzWorkId ?: snapshot.musicBrainzWorkId?.value,
            replayGainTrackGain = verified?.replayGainTrackGain ?: snapshot.replayGainTrackGain?.value,
            replayGainTrackPeak = verified?.replayGainTrackPeak ?: snapshot.replayGainTrackPeak?.value,
            replayGainAlbumGain = verified?.replayGainAlbumGain ?: snapshot.replayGainAlbumGain?.value,
            replayGainAlbumPeak = verified?.replayGainAlbumPeak ?: snapshot.replayGainAlbumPeak?.value,
            metadataSource = TrackMetadataSources.File,
        )
        database.trackDao().upsertAll(listOf(entity))
        if (artists.isNotEmpty()) attachArtists(trackId, artists)
        if (genres.isNotEmpty()) attachGenres(trackId, genres)
        return entity
    }

    private suspend fun ensureAlbum(name: String): Long? {
        val normalized = name.normalizeMetadataName()
        val dao = database.metadataDao()
        dao.findAlbumsByNormalizedNames(listOf(normalized)).firstOrNull()?.let { return it.id }
        dao.insertAlbums(
            listOf(AlbumEntity(name = name.trim(), normalizedName = normalized, sortName = null, year = null, artworkId = null))
        )
        return dao.findAlbumsByNormalizedNames(listOf(normalized)).firstOrNull()?.id
    }

    private suspend fun attachArtists(trackId: Long, names: List<String>) {
        val dao = database.metadataDao()
        val normalizedNames = names.map(String::normalizeMetadataName)
        dao.insertArtists(
            names.zip(normalizedNames).map { (name, normalized) ->
                ArtistEntity(name = name, normalizedName = normalized, sortName = null)
            }
        )
        val byName = dao.findArtistsByNormalizedNames(normalizedNames).associateBy { it.normalizedName }
        dao.upsertTrackArtists(
            names.mapIndexedNotNull { index, name ->
                byName[name.normalizeMetadataName()]?.let { artist ->
                    TrackArtistCrossRef(trackId, artist.id, index)
                }
            }
        )
    }

    private suspend fun attachGenres(trackId: Long, names: List<String>) {
        val dao = database.metadataDao()
        val normalizedNames = names.map(String::normalizeMetadataName)
        dao.insertGenres(
            names.zip(normalizedNames).map { (name, normalized) ->
                GenreEntity(name = name, normalizedName = normalized)
            }
        )
        val byName = dao.findGenresByNormalizedNames(normalizedNames).associateBy { it.normalizedName }
        dao.upsertTrackGenres(
            names.mapNotNull { name ->
                byName[name.normalizeMetadataName()]?.let { genre ->
                    TrackGenreCrossRef(trackId, genre.id)
                }
            }
        )
    }

    private suspend fun registerLocalSource(
        request: DownloadFinalizationRequest,
        trackId: Long,
        verified: LocalMetadataSummary?,
        now: Long,
    ) {
        val sourceAccountDao = database.sourceAccountDao()
        val localSourceAccountId = sourceAccountDao.listAll()
            .firstOrNull { account -> account.providerType == ProviderTypes.Local }
            ?.id
            ?: run {
                val candidateId = if (sourceAccountDao.get(PREFERRED_LOCAL_SOURCE_ACCOUNT_ID) == null) {
                    PREFERRED_LOCAL_SOURCE_ACCOUNT_ID
                } else {
                    (sourceAccountDao.maxId() ?: 0L) + 1L
                }
                sourceAccountDao.upsert(
                    SourceAccountEntity(
                        id = candidateId,
                        providerType = ProviderTypes.Local,
                        displayName = "Local",
                        endpoint = null,
                        externalAccountId = null,
                        credentialRef = "storage-$candidateId",
                        priority = 0,
                        enabled = true,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
                candidateId
            }
        val itemDao = database.sourceItemDao()
        val previous = itemDao.findByPath(localSourceAccountId, request.localPath)
        itemDao.upsertAll(
            listOf(
                SourceItemEntity(
                    id = previous?.id ?: 0,
                    sourceAccountId = localSourceAccountId,
                    libraryRootId = null,
                    itemType = "track",
                    providerItemId = previous?.providerItemId,
                    parentProviderItemId = null,
                    canonicalPath = request.localPath,
                    displayPath = request.localPath,
                    displayName = request.localPath.substringAfterLast('/').substringAfterLast('\\'),
                    mimeType = request.mimeType,
                    sizeBytes = request.expectedBytes,
                    etag = null,
                    revision = null,
                    createdAtRemote = previous?.createdAtRemote ?: now,
                    modifiedAtRemote = now,
                    contentHash = null,
                    audioFingerprint = null,
                    isDeleted = false,
                    firstSyncedAt = previous?.firstSyncedAt ?: now,
                    lastSyncedAt = now,
                    lastSeenScanId = null,
                )
            )
        )
        val item = itemDao.findByPath(localSourceAccountId, request.localPath)
            ?: error("Downloaded local source item was not persisted")
        val sourceRefDao = database.trackSourceRefDao()
        sourceRefDao.upsertAll(
            listOf(
                TrackSourceRefEntity(
                    trackId = trackId,
                    sourceItemId = item.id,
                    role = "download",
                    matchMethod = "download_finalization",
                    matchConfidence = 100,
                    isPreferred = true,
                    isAvailable = true,
                    isDownloaded = true,
                    playable = true,
                    downloadable = false,
                    codec = verified?.codec,
                    container = verified?.container,
                    bitRate = verified?.overallBitrate?.toInt(),
                    sampleRate = verified?.sampleRate?.toInt(),
                    bitsPerSample = verified?.bitDepth?.toInt(),
                    channels = verified?.channels?.toInt(),
                    channelLayout = verified?.channelLayout,
                    lossless = verified?.lossless,
                    createdAt = now,
                    updatedAt = now,
                    hasEmbeddedArtwork = verified?.hasEmbeddedArtwork,
                    embeddedLyricsKind = verified?.embeddedLyricsKind?.takeIf(String::isNotBlank),
                )
            )
        )
        sourceRefDao.selectPreferredSource(trackId, item.id, now)
    }
}

private fun MetadataSnapshot.toWriteRequest(
    path: String,
    saveSidecars: Boolean,
): AudioMetadataWriteRequest {
    return AudioMetadataWriteRequest(
        path = path,
        metadata = MetadataWriteFields(
            title = title?.value,
            artist = artists.joinToString(", ") { value -> value.value }.takeIf(String::isNotBlank),
            artists = artists.map { value -> value.value },
            albumArtist = albumArtist?.value,
            album = album?.value,
            composer = composer?.value,
            lyricist = lyricist?.value,
            conductor = conductor?.value,
            genre = genre?.value,
            grouping = grouping?.value,
            comment = comment?.value,
            copyright = copyright?.value,
            publisher = publisher?.value,
            date = date?.value,
            originalReleaseDate = originalReleaseDate?.value,
            trackNumber = trackNumber?.value?.toUIntOrNull(),
            trackTotal = trackTotal?.value?.toUIntOrNull(),
            discNumber = discNumber?.value?.toUIntOrNull(),
            discTotal = discTotal?.value?.toUIntOrNull(),
            bpm = bpm?.value,
            musicalKey = musicalKey?.value,
            isrc = isrc?.value,
            musicbrainzRecordingId = musicBrainzRecordingId?.value,
            musicbrainzTrackId = musicBrainzTrackId?.value,
            musicbrainzReleaseId = musicBrainzReleaseId?.value,
            musicbrainzReleaseGroupId = musicBrainzReleaseGroupId?.value,
            musicbrainzArtistId = musicBrainzArtistId?.value,
            musicbrainzReleaseArtistId = musicBrainzReleaseArtistId?.value,
            musicbrainzWorkId = musicBrainzWorkId?.value,
            replayGainTrackGain = replayGainTrackGain?.value,
            replayGainTrackPeak = replayGainTrackPeak?.value,
            replayGainAlbumGain = replayGainAlbumGain?.value,
            replayGainAlbumPeak = replayGainAlbumPeak?.value,
        ),
        artwork = artwork?.let { value ->
            MetadataArtworkWriteRequest(localPath = value.localPath, mimeType = value.mimeType)
        },
        lyrics = lyrics?.let { value ->
            MetadataLyricsWriteRequest(
                embedded = value.embedded,
                lrc = value.lrc,
                ttml = value.ttml,
                saveSidecars = saveSidecars,
            )
        },
        mergeMode = if (containsUserMetadata()) {
            MetadataMergeMode.PREFER_SNAPSHOT
        } else {
            MetadataMergeMode.FILL_MISSING
        },
    )
}

private fun MetadataSnapshot.containsUserMetadata(): Boolean {
    return sequenceOf(
        title?.source,
        albumArtist?.source,
        album?.source,
        composer?.source,
        lyricist?.source,
        conductor?.source,
        genre?.source,
        grouping?.source,
        comment?.source,
        copyright?.source,
        publisher?.source,
        date?.source,
    ).any { source -> source == MetadataSource.User } ||
        artists.any { artist -> artist.source == MetadataSource.User }
}

private fun DownloadFinalizationRequest.toFallbackSnapshot(): MetadataSnapshot {
    val source = MetadataSource.Fallback
    return MetadataSnapshot(
        title = fallbackTitle.metadataValueOrNull(source),
        artists = listOfNotNull(fallbackArtist.metadataValueOrNull(source)),
        albumArtist = null,
        album = fallbackAlbum.metadataValueOrNull(source),
        composer = null,
        lyricist = null,
        conductor = null,
        genre = null,
        grouping = null,
        comment = null,
        copyright = null,
        publisher = null,
        date = null,
        originalReleaseDate = null,
        trackNumber = null,
        trackTotal = null,
        discNumber = null,
        discTotal = null,
        bpm = null,
        musicalKey = null,
        isrc = null,
        musicBrainzRecordingId = null,
        musicBrainzTrackId = null,
        musicBrainzReleaseId = null,
        musicBrainzReleaseGroupId = null,
        musicBrainzArtistId = null,
        musicBrainzReleaseArtistId = null,
        musicBrainzWorkId = null,
        replayGainTrackGain = null,
        replayGainTrackPeak = null,
        replayGainAlbumGain = null,
        replayGainAlbumPeak = null,
        artwork = null,
        lyrics = null,
    )
}

private fun TrackEntity.refreshedFrom(
    verified: LocalMetadataSummary?,
    now: Long,
): TrackEntity {
    verified ?: return copy(updatedAt = now)
    return copy(
        title = verified.title ?: title,
        albumArtist = verified.albumArtist ?: albumArtist,
        composer = verified.composer ?: composer,
        comment = verified.comment ?: comment,
        grouping = verified.grouping ?: grouping,
        durationMs = verified.durationMs.toLong().takeIf { it > 0 } ?: durationMs,
        discNumber = verified.discNumber?.toInt() ?: discNumber,
        discTotal = verified.discTotal?.toInt() ?: discTotal,
        trackNumber = verified.trackNumber?.toInt() ?: trackNumber,
        trackTotal = verified.trackTotal?.toInt() ?: trackTotal,
        year = verified.date?.take(4)?.toIntOrNull() ?: year,
        date = verified.date ?: date,
        sampleRate = verified.sampleRate?.toInt() ?: sampleRate,
        bitRate = verified.overallBitrate?.toInt() ?: bitRate,
        bitsPerSample = verified.bitDepth?.toInt() ?: bitsPerSample,
        channels = verified.channels?.toInt() ?: channels,
        channelLayout = verified.channelLayout ?: channelLayout,
        codec = verified.codec ?: codec,
        container = verified.container ?: container,
        lossless = verified.lossless ?: lossless,
        updatedAt = now,
        artist = verified.artist ?: artist,
        lyricist = verified.lyricist ?: lyricist,
        conductor = verified.conductor ?: conductor,
        copyright = verified.copyright ?: copyright,
        publisher = verified.publisher ?: publisher,
        originalReleaseDate = verified.originalReleaseDate ?: originalReleaseDate,
        bpm = verified.bpm ?: bpm,
        musicalKey = verified.musicalKey ?: musicalKey,
        isrc = verified.isrc ?: isrc,
        musicBrainzRecordingId = verified.musicbrainzRecordingId ?: musicBrainzRecordingId,
        musicBrainzTrackId = verified.musicbrainzTrackId ?: musicBrainzTrackId,
        musicBrainzReleaseId = verified.musicbrainzReleaseId ?: musicBrainzReleaseId,
        musicBrainzReleaseGroupId = verified.musicbrainzReleaseGroupId ?: musicBrainzReleaseGroupId,
        musicBrainzArtistId = verified.musicbrainzArtistId ?: musicBrainzArtistId,
        musicBrainzReleaseArtistId = verified.musicbrainzReleaseArtistId ?: musicBrainzReleaseArtistId,
        musicBrainzWorkId = verified.musicbrainzWorkId ?: musicBrainzWorkId,
        replayGainTrackGain = verified.replayGainTrackGain ?: replayGainTrackGain,
        replayGainTrackPeak = verified.replayGainTrackPeak ?: replayGainTrackPeak,
        replayGainAlbumGain = verified.replayGainAlbumGain ?: replayGainAlbumGain,
        replayGainAlbumPeak = verified.replayGainAlbumPeak ?: replayGainAlbumPeak,
    )
}

private fun Int.toUIntOrNull(): UInt? = takeIf { it >= 0 }?.toUInt()

private fun <T> T?.metadataValueOrNull(source: MetadataSource): MetadataValue<T>? =
    this?.let { value -> MetadataValue(value, source) }

private fun String?.metadataValueOrNull(source: MetadataSource): MetadataValue<String>? =
    this?.trim()?.takeIf(String::isNotEmpty)?.let { value -> MetadataValue(value, source) }

private fun String.normalizeMetadataName(): String = trim().lowercase()

private fun Throwable.safeMessage(): String =
    message?.lineSequence()?.firstOrNull()?.take(240)?.takeIf(String::isNotBlank)
        ?: this::class.simpleName.orEmpty().ifBlank { "unknown error" }

private fun MediaId.finalizationCorrelationId(): String =
    "finalize-${sourceId.value}-${remoteId.hashCode().toUInt().toString(16)}"

private const val PREFERRED_LOCAL_SOURCE_ACCOUNT_ID = 1L
private const val LOG_TARGET = "DownloadedMediaFinalizer"
