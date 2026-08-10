package io.github.julystar.musicapp.singleton

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import io.github.julystar.musicapp.database.MetadataDao
import io.github.julystar.musicapp.database.LyricsEntity
import io.github.julystar.musicapp.database.PlaylistDao
import io.github.julystar.musicapp.database.PlaylistEntity
import io.github.julystar.musicapp.database.PlaylistSummaryRow
import io.github.julystar.musicapp.database.PlaylistTrackCrossRef
import io.github.julystar.musicapp.database.PlaylistTrackRow
import io.github.julystar.musicapp.database.SourceItemDao
import io.github.julystar.musicapp.database.SourceItemEntity
import io.github.julystar.musicapp.database.SourceItemTypes
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.TrackDao
import io.github.julystar.musicapp.database.TrackEntity
import io.github.julystar.musicapp.database.TrackSourceRefDao
import io.github.julystar.musicapp.database.TrackSourceRefEntity
import io.github.julystar.musicapp.core.data.CreatePlaylistRequest
import io.github.julystar.musicapp.core.data.UpdatePlaylistRequest
import io.github.julystar.musicapp.core.data.toLegacyStorageEntry
import io.github.julystar.musicapp.core.data.toLegacyStorageEntryLoc
import io.github.julystar.musicapp.core.data.toPlaybackLyrics
import io.github.julystar.musicapp.core.data.selectLyrics
import io.github.julystar.musicapp.core.domain.model.AppSettings
import io.github.julystar.musicapp.core.domain.model.PlaybackAudioInfo
import io.github.julystar.musicapp.core.domain.model.Lyrics as DomainLyrics
import io.github.julystar.musicapp.core.domain.model.LyricsLoadState
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.core.domain.model.LIBRARY_PLAYBACK_PLAYLIST_ID
import io.github.julystar.musicapp.domain.importing.normalizeRemotePath
import io.github.julystar.musicapp.domain.importing.stableTrackId
import io.github.julystar.musicapp.platform.currentTimeMillis
import io.github.julystar.musicapp.service.playback.data.toPlaybackAudioInfo
import io.github.julystar.musicapp.source.api.SourceNodeSelection
import io.github.julystar.musicapp.source.api.SourceNodeType
import io.github.julystar.musicapp.source.api.SourceAudioProperties
import kotlinx.coroutines.flow.first
import okio.FileSystem
import okio.Path.Companion.toPath
import uniffi.app_backend.ArgCreatePlaylist
import uniffi.app_backend.ArgUpdatePlaylist
import uniffi.app_backend.DataSourceKey
import uniffi.app_backend.LrcMetadata
import uniffi.app_backend.LyricLine
import uniffi.app_backend.LyricLoadState
import uniffi.app_backend.Lyrics
import uniffi.app_backend.Music
import uniffi.app_backend.MusicAbstract
import uniffi.app_backend.MusicId
import uniffi.app_backend.MusicLyric
import uniffi.app_backend.MusicMeta
import uniffi.app_backend.Playlist
import uniffi.app_backend.PlaylistAbstract
import uniffi.app_backend.PlaylistId
import uniffi.app_backend.PlaylistMeta
import uniffi.app_backend.StorageEntry
import uniffi.app_backend.StorageEntryLoc
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class RoomLibraryStore(
    private val database: AppDatabase,
    private val trackDao: TrackDao,
    private val sourceItemDao: SourceItemDao,
    private val trackSourceRefDao: TrackSourceRefDao,
    private val playlistDao: PlaylistDao,
    private val metadataDao: MetadataDao,
    private val settingsRepository: SettingsRepository? = null,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) {
    suspend fun getMusic(id: MusicId): Music? {
        val track = trackDao.get(id.value) ?: return null
        val loc = resolveTrackLoc(track) ?: return null
        val lyric = buildLyric(track.id, loc)
        return Music(
            meta = track.toMusicMeta(),
            loc = loc,
            cover = null,
            lyric = lyric,
        )
    }

    suspend fun getTrackPrimaryArtist(trackId: Long): String? {
        return metadataDao.artistNamesForTrack(trackId).firstOrNull()
    }

    suspend fun getPlaybackItemMetadata(trackId: Long): PlaybackItemMetadata {
        val track = trackDao.get(trackId)
        return PlaybackItemMetadata(
            artist = metadataDao.artistNamesForTrack(trackId)
                .firstOrNull()
                ?.takeIf(String::isNotBlank)
                ?: track?.artist?.takeIf(String::isNotBlank),
            album = track?.albumId
                ?.let { albumId -> metadataDao.getAlbum(albumId)?.name }
                ?.takeIf(String::isNotBlank),
        )
    }

    suspend fun getTrackAnnotation(trackId: Long): String? {
        return trackDao.get(trackId)?.comment?.takeIf(String::isNotBlank)
    }

    suspend fun getPlaybackAudioInfo(trackId: Long): PlaybackAudioInfo? {
        val track = trackDao.get(trackId) ?: return null
        val candidates = trackSourceRefDao.playbackCandidates(trackId)
        val selected = candidates
            .filter { candidate -> candidate.ref.isPreferred }
            .singleOrNull()
            ?: candidates.firstOrNull()
        return selected?.toPlaybackAudioInfo(track) ?: track.toPlaybackAudioInfo()
    }

    suspend fun getTrackReplayGain(trackId: Long): TrackReplayGain? {
        val track = trackDao.get(trackId) ?: return null
        return TrackReplayGain(
            trackGainDb = track.replayGainTrackGain,
            trackPeak = track.replayGainTrackPeak,
            albumGainDb = track.replayGainAlbumGain,
            albumPeak = track.replayGainAlbumPeak,
        )
    }

    suspend fun getPlaybackLyrics(trackId: Long): DomainLyrics {
        val settings = settingsRepository?.settings?.first() ?: AppSettings.Default
        val candidates = metadataDao.getLyricsCandidates(trackId) + externalSidecarLyrics(trackId)
        return candidates.selectLyrics(settings.lyrics)?.toPlaybackLyrics()
            ?: DomainLyrics(loadState = LyricsLoadState.Missing)
    }

    suspend fun hasCachedArtwork(trackId: Long): Boolean {
        val track = trackDao.get(trackId) ?: return false
        val artwork = metadataDao.getArtworkForTrack(trackId)
            ?: track.albumId?.let { albumId -> metadataDao.getArtworkForAlbum(albumId) }
            ?: return false
        return listOfNotNull(artwork.localPath, artwork.thumbnailPath).any { path ->
            fileSystem.metadataOrNull(path.toPath())?.isRegularFile == true
        }
    }

    suspend fun getPlaylist(id: PlaylistId): Playlist? {
        if (id.value == LIBRARY_PLAYBACK_PLAYLIST_ID) {
            return getLibraryPlaybackPlaylist()
        }

        val entity = playlistDao.get(id.value) ?: return null
        val rows = playlistDaoRows(id.value)
        val musics = rows.map { row ->
            MusicAbstract(
                meta = MusicMeta(
                    id = MusicId(row.trackId),
                    title = row.title,
                    duration = row.durationMs?.milliseconds,
                    order = listOf(row.sortOrder.coerceAtLeast(0).toUInt()),
                ),
                cover = null,
            )
        }
        return Playlist(
            abstr = entity.toPlaylistAbstract(musics.size.toLong(), musics.totalDuration()),
            musics = musics,
        )
    }

    // Long overload for callers that do not import UniFFI types
    suspend fun getPlaylistById(id: Long): Playlist? = getPlaylist(PlaylistId(id))

    private suspend fun getLibraryPlaybackPlaylist(): Playlist {
        val tracks = trackDao.observeAll().first()
        val musics = tracks.mapIndexed { index, track ->
            MusicAbstract(
                meta = MusicMeta(
                    id = MusicId(track.id),
                    title = track.title,
                    duration = track.durationMs?.milliseconds,
                    order = listOf(index.toUInt()),
                ),
                cover = null,
            )
        }
        return Playlist(
            abstr = PlaylistAbstract(
                meta = PlaylistMeta(
                    id = PlaylistId(LIBRARY_PLAYBACK_PLAYLIST_ID),
                    title = "Library",
                    cover = null,
                    showCover = null,
                    createdTime = Duration.ZERO,
                    order = listOf(0u),
                ),
                musicCount = musics.size.toULong(),
                duration = musics.totalDuration(),
            ),
            musics = musics,
        )
    }

    suspend fun createPlaylist(arg: ArgCreatePlaylist): Playlist? {
        return createPlaylist(
            title = arg.title,
            cover = arg.cover,
            entries = arg.entries.map { input ->
                TrackEntryInput(entry = input.entry, title = input.name)
            },
        )
    }

    suspend fun createPlaylist(request: CreatePlaylistRequest): Playlist? {
        return createPlaylist(
            title = request.title,
            cover = request.cover
                ?.takeIf { selection -> selection.node.type == SourceNodeType.Image }
                ?.toLegacyStorageEntryLoc(),
            entries = request.entries
                .filter { selection -> selection.node.type == SourceNodeType.Track }
                .mapNotNull(SourceNodeSelection::toTrackEntryInputOrNull),
        )
    }

    private suspend fun createPlaylist(
        title: String,
        cover: StorageEntryLoc?,
        entries: List<TrackEntryInput>,
    ): Playlist? {
        val now = currentTimeMillis()
        val playlistId = (playlistDao.maxId() ?: 0L) + 1L
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                playlistDao.upsert(
                    PlaylistEntity(
                        id = playlistId,
                        title = title,
                        artworkId = null,
                        coverStorageId = cover?.storageId?.value,
                        coverPath = cover?.path,
                        createdAt = now,
                        updatedAt = now,
                        sortOrder = (playlistDao.maxSortOrder() ?: -1L) + 1L,
                    )
                )
                playlistDao.upsertTracks(
                    ensureTracksForEntries(entries, now)
                        .mapIndexed { index, track ->
                            PlaylistTrackCrossRef(
                                playlistId = playlistId,
                                trackId = track.id,
                                sortOrder = index.toLong(),
                                addedAt = now,
                            )
                        }
                )
            }
        }
        return getPlaylist(PlaylistId(playlistId))
    }

    suspend fun updatePlaylist(arg: ArgUpdatePlaylist) {
        val current = playlistDao.get(arg.id.value) ?: return
        playlistDao.upsert(
            current.copy(
                title = arg.title,
                coverStorageId = arg.cover?.storageId?.value,
                coverPath = arg.cover?.path,
                updatedAt = currentTimeMillis(),
            )
        )
    }

    suspend fun updatePlaylist(request: UpdatePlaylistRequest) {
        updatePlaylist(
            ArgUpdatePlaylist(
                id = PlaylistId(request.id),
                title = request.title,
                cover = request.cover
                    ?.takeIf { selection -> selection.node.type == SourceNodeType.Image }
                    ?.toLegacyStorageEntryLoc(),
            )
        )
    }

    suspend fun addMusicEntries(playlistId: PlaylistId, entries: List<StorageEntry>): List<MusicId> {
        return addMusicInputs(
            playlistId = playlistId,
            entries = entries.map { entry -> TrackEntryInput(entry = entry) },
        )
    }

    private suspend fun addMusicInputs(
        playlistId: PlaylistId,
        entries: List<TrackEntryInput>,
    ): List<MusicId> {
        if (entries.isEmpty()) return emptyList()
        val now = currentTimeMillis()
        val currentRows = playlistDaoRows(playlistId.value)
        val existingTrackIds = currentRows.map { it.trackId }.toSet()
        val startOrder = (currentRows.maxOfOrNull { it.sortOrder } ?: -1L) + 1L
        val addedIds = mutableListOf<MusicId>()
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                val tracks = ensureTracksForEntries(entries, now)
                    .filter { it.id !in existingTrackIds }
                playlistDao.upsertTracks(
                    tracks.mapIndexed { index, track ->
                        addedIds += MusicId(track.id)
                        PlaylistTrackCrossRef(
                            playlistId = playlistId.value,
                            trackId = track.id,
                            sortOrder = startOrder + index,
                            addedAt = now,
                        )
                    }
                )
            }
        }
        return addedIds
    }


    // Long overloads for callers that do not import UniFFI types
    suspend fun addMusicSelectionsById(
        playlistId: Long,
        selections: List<SourceNodeSelection>,
    ): List<Long> {
        val ids = addMusicSelections(PlaylistId(playlistId), selections)
        return ids.map { it.value }
    }

    suspend fun addMusicSelections(
        playlistId: PlaylistId,
        selections: List<SourceNodeSelection>,
    ): List<MusicId> {
        return addMusicInputs(
            playlistId = playlistId,
            entries = selections.mapNotNull(SourceNodeSelection::toTrackEntryInputOrNull),
        )
    }

    suspend fun removeMusic(playlistId: PlaylistId, musicId: MusicId) {
        playlistDao.deleteTrack(playlistId.value, musicId.value)
    }


    suspend fun replaceMusicOrderById(
        playlistId: Long,
        orderedTrackIds: List<Long>,
    ) {
        replaceMusicOrder(PlaylistId(playlistId), orderedTrackIds.map { MusicId(it) })
    }

    suspend fun replaceMusicOrder(playlistId: PlaylistId, orderedIds: List<MusicId>) {
        val now = currentTimeMillis()
        playlistDao.replaceTracks(
            playlistId = playlistId.value,
            tracks = orderedIds.mapIndexed { index, id ->
                PlaylistTrackCrossRef(
                    playlistId = playlistId.value,
                    trackId = id.value,
                    sortOrder = index.toLong(),
                    addedAt = now,
                )
            },
        )
    }

    suspend fun replacePlaylistOrder(orderedIds: List<PlaylistId>) {
        orderedIds.forEachIndexed { index, id ->
            playlistDao.get(id.value)?.let { playlist ->
                playlistDao.upsert(
                    playlist.copy(
                        sortOrder = index.toLong(),
                        updatedAt = currentTimeMillis(),
                    )
                )
            }
        }
    }

    suspend fun removeLyric(trackId: MusicId) {
        metadataDao.deleteLyricsForTracks(listOf(trackId.value))
    }

    suspend fun updateDuration(trackId: MusicId, durationMs: Long) {
        trackDao.updateDuration(trackId.value, max(durationMs, 0L), currentTimeMillis())
    }

    suspend fun resolveTrackLoc(id: MusicId): StorageEntryLoc? {
        return trackDao.get(id.value)?.let { resolveTrackLoc(it) }
    }

    private suspend fun playlistDaoRows(playlistId: Long): List<PlaylistTrackRow> {
        return playlistDao.observeTracks(playlistId).first()
    }

    private suspend fun ensureTracksForEntries(
        entries: List<TrackEntryInput>,
        now: Long,
    ): List<TrackEntity> {
        val normalizedEntries = entries.map { input ->
            Triple(input.entry, input, normalizeRemotePath(input.entry.path))
        }
        val existingItems = normalizedEntries
            .groupBy { it.first.storageId.value }
            .flatMap { (sourceAccountId, values) ->
                sourceItemDao.findByPaths(
                    sourceAccountId = sourceAccountId,
                    canonicalPaths = values.map { it.third },
                )
            }
            .associateBy { it.sourceAccountId to it.canonicalPath }
        val sourceItems = normalizedEntries.map { (entry, input, path) ->
            val existing = existingItems[entry.storageId.value to path]
            val title = input.title
                ?.takeIf { it.isNotBlank() }
                ?: entry.name.ifBlank { path.substringAfterLast('/').substringBeforeLast('.') }
            SourceItemEntity(
                id = existing?.id ?: 0,
                sourceAccountId = entry.storageId.value,
                libraryRootId = null,
                itemType = SourceItemTypes.Track,
                providerItemId = entry.remoteId,
                parentProviderItemId = entry.parentRemoteId,
                canonicalPath = path,
                displayPath = path,
                displayName = entry.name.ifBlank { title },
                mimeType = entry.mimeType,
                sizeBytes = entry.size?.toLong(),
                etag = entry.etag,
                revision = entry.ctag,
                createdAtRemote = entry.createdAt,
                modifiedAtRemote = entry.modifiedAt,
                contentHash = null,
                audioFingerprint = null,
                isDeleted = false,
                firstSyncedAt = existing?.firstSyncedAt ?: now,
                lastSyncedAt = now,
                lastSeenScanId = null,
            )
        }
        if (sourceItems.isNotEmpty()) {
            sourceItemDao.upsertAll(sourceItems)
        }
        val persistedItems = normalizedEntries
            .groupBy { it.first.storageId.value }
            .flatMap { (sourceAccountId, values) ->
                sourceItemDao.findByPaths(
                    sourceAccountId = sourceAccountId,
                    canonicalPaths = values.map { it.third },
                )
            }
            .associateBy { it.sourceAccountId to it.canonicalPath }
        val tracks = entries.map { input ->
            val entry = input.entry
            val path = normalizeRemotePath(entry.path)
            val title = input.title
                ?.takeIf { it.isNotBlank() }
                ?: entry.name.ifBlank { path.substringAfterLast('/').substringBeforeLast('.') }
            val audio = input.audioProperties
            TrackEntity(
                id = stableTrackId(entry.storageId.value, path),
                title = title.substringBeforeLast('.', title),
                sortTitle = null,
                albumId = null,
                albumArtist = null,
                composer = null,
                comment = null,
                grouping = null,
                durationMs = null,
                discNumber = null,
                discTotal = null,
                trackNumber = null,
                trackTotal = null,
                year = null,
                date = null,
                sampleRate = audio?.sampleRateHz?.takeIf { it > 0 },
                bitRate = audio?.bitrateKbps?.takeIf { it > 0 },
                bitsPerSample = audio?.bitDepth?.takeIf { it > 0 },
                channels = audio?.channels?.takeIf { it > 0 },
                channelLayout = audio?.channelLayout?.takeIf(String::isNotBlank),
                codec = audio?.codec?.takeIf(String::isNotBlank),
                container = audio?.container?.takeIf(String::isNotBlank),
                lossless = audio?.lossless,
                createdAt = now,
                updatedAt = now,
            )
        }
        if (tracks.isNotEmpty()) {
            trackDao.upsertAll(tracks)
            trackSourceRefDao.upsertAll(
                tracks.zip(normalizedEntries).mapNotNull { (track, normalizedEntry) ->
                    val entry = normalizedEntry.first
                    val path = normalizedEntry.third
                    val item = persistedItems[entry.storageId.value to path] ?: return@mapNotNull null
                    TrackSourceRefEntity(
                        trackId = track.id,
                        sourceItemId = item.id,
                        role = "primary",
                        matchMethod = "playlist_import",
                        matchConfidence = 100,
                        isPreferred = true,
                        isAvailable = true,
                        isDownloaded = false,
                        playable = true,
                        downloadable = true,
                        codec = track.codec,
                        container = track.container,
                        bitRate = track.bitRate,
                        sampleRate = track.sampleRate,
                        bitsPerSample = track.bitsPerSample,
                        channels = track.channels,
                        channelLayout = track.channelLayout,
                        lossless = track.lossless,
                        createdAt = now,
                        updatedAt = now,
                    )
                }
            )
        }
        return tracks
    }

    private suspend fun resolveTrackLoc(track: TrackEntity): StorageEntryLoc? {
        val item = trackSourceRefDao.playbackCandidates(track.id).firstOrNull()?.item
            ?: return null
        val path = item.canonicalPath ?: return null
        return StorageEntryLoc(
            storageId = uniffi.app_backend.StorageId(item.sourceAccountId),
            path = path,
        )
    }

    private suspend fun buildLyric(trackId: Long, loc: StorageEntryLoc): MusicLyric {
        val settings = settingsRepository?.settings?.first() ?: AppSettings.Default
        val candidates = metadataDao.getLyricsCandidates(trackId) + externalSidecarLyrics(trackId)
        val entity = candidates.selectLyrics(settings.lyrics)
        val parsed = entity?.toPlaybackLyrics()?.let { lyrics ->
            Lyrics(
                metdata = LrcMetadata("", "", "", "", "", "", ""),
                lines = lyrics.lines.map { line -> LyricLine(line.duration, line.text) },
            )
        }
        return MusicLyric(
            loc = loc,
            data = parsed ?: emptyLyrics(),
            loadedState = if (parsed == null) LyricLoadState.MISSING else LyricLoadState.LOADED,
        )
    }

    private suspend fun externalSidecarLyrics(trackId: Long): List<LyricsEntity> {
        val item = trackSourceRefDao.playbackCandidates(trackId).firstOrNull()?.item ?: return emptyList()
        val audioPath = item.canonicalPath ?: return emptyList()
        val fileNameStart = audioPath.lastIndexOf('/').coerceAtLeast(audioPath.lastIndexOf('\\')) + 1
        val extensionStart = audioPath.lastIndexOf('.').takeIf { it >= fileNameStart } ?: audioPath.length
        val basePath = audioPath.substring(0, extensionStart)
        return listOf("ttml", "lrc", "txt").mapNotNull { extension ->
            val sidecarPath = "$basePath.$extension".toPath()
            val metadata = fileSystem.metadataOrNull(sidecarPath) ?: return@mapNotNull null
            if (!metadata.isRegularFile) return@mapNotNull null
            val content = runCatching { fileSystem.read(sidecarPath) { readUtf8() } }.getOrNull()
                ?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            LyricsEntity(
                trackId = trackId,
                format = extension.uppercase(),
                language = null,
                synchronized = extension != "txt",
                content = content,
                sourcePath = sidecarPath.toString(),
                updatedAt = metadata.lastModifiedAtMillis ?: 0L,
                sourceKind = if (extension == "ttml") "ExternalTtml" else "ExternalPlain",
            )
        }
    }

    private fun TrackEntity.toMusicMeta(): MusicMeta {
        return MusicMeta(
            id = MusicId(id),
            title = title,
            duration = durationMs?.milliseconds,
            order = listOf(0u),
        )
    }

    private fun PlaylistEntity.toPlaylistAbstract(
        trackCount: Long,
        duration: Duration?,
    ): PlaylistAbstract {
        val coverLoc = coverPath?.let { path ->
            val storageId = coverStorageId ?: return@let null
            StorageEntryLoc(uniffi.app_backend.StorageId(storageId), path)
        }
        return PlaylistAbstract(
            meta = PlaylistMeta(
                id = PlaylistId(id),
                title = title,
                cover = coverLoc,
                showCover = coverLoc?.let { DataSourceKey.AnyEntry(it) },
                createdTime = createdAt.milliseconds,
                order = listOf(sortOrder.coerceAtLeast(0).toUInt()),
            ),
            musicCount = trackCount.coerceAtLeast(0).toULong(),
            duration = duration,
        )
    }

    private fun PlaylistSummaryRow.toPlaylistAbstract(): PlaylistAbstract {
        val coverLoc = coverPath?.let { path ->
            val storageId = coverStorageId ?: return@let null
            StorageEntryLoc(uniffi.app_backend.StorageId(storageId), path)
        }
        return PlaylistAbstract(
            meta = PlaylistMeta(
                id = PlaylistId(id),
                title = title,
                cover = coverLoc,
                showCover = coverLoc?.let { DataSourceKey.AnyEntry(it) },
                createdTime = createdAt.milliseconds,
                order = listOf(sortOrder.coerceAtLeast(0).toUInt()),
            ),
            musicCount = musicCount.coerceAtLeast(0).toULong(),
            duration = durationMs?.milliseconds,
        )
    }

    fun mapPlaylistSummary(row: PlaylistSummaryRow): PlaylistAbstract = row.toPlaylistAbstract()
}

data class TrackReplayGain(
    val trackGainDb: Double?,
    val trackPeak: Double?,
    val albumGainDb: Double?,
    val albumPeak: Double?,
)

data class PlaybackItemMetadata(
    val artist: String?,
    val album: String?,
)

private data class TrackEntryInput(
    val entry: StorageEntry,
    val title: String? = null,
    val audioProperties: SourceAudioProperties? = null,
)

private fun SourceNodeSelection.toTrackEntryInputOrNull(): TrackEntryInput? {
    val entry = toLegacyStorageEntry() ?: return null
    return TrackEntryInput(
        entry = entry,
        title = entry.name,
        audioProperties = node.audioProperties,
    )
}

private fun List<MusicAbstract>.totalDuration(): Duration? {
    var total = Duration.ZERO
    for (music in this) {
        val duration = music.meta.duration ?: return null
        total += duration
    }
    return total
}

private fun emptyLyrics(): Lyrics {
    return Lyrics(
        metdata = LrcMetadata("", "", "", "", "", "", ""),
        lines = emptyList(),
    )
}
