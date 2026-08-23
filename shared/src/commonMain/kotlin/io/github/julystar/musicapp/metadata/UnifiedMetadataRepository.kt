package io.github.julystar.musicapp.metadata

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import io.github.julystar.musicapp.database.AlbumEntity
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.MetadataDao
import io.github.julystar.musicapp.database.TrackDao
import io.github.julystar.musicapp.database.TrackEntity
import io.github.julystar.musicapp.database.TrackMetadataSources
import io.github.julystar.musicapp.platform.currentTimeMillis
import io.github.julystar.musicapp.source.api.MetaSongCandidate
import uniffi.app_backend.RemoteMetadata

enum class MetadataMergePolicy {
    ReplaceFilenameMetadata,
    FillMissingFileMetadata,
    FillMissingServerMetadata,
    RefreshPluginMetadata,
    ManualOverride,
}

data class ResolvedTrackMetadata(
    val title: String? = null,
    val artist: String? = null,
    val artists: List<String> = emptyList(),
    val album: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val composer: String? = null,
    val lyricist: String? = null,
    val conductor: String? = null,
    val comment: String? = null,
    val grouping: String? = null,
    val copyright: String? = null,
    val publisher: String? = null,
    val originalReleaseDate: String? = null,
    val trackNumber: Int? = null,
    val trackTotal: Int? = null,
    val discNumber: Int? = null,
    val discTotal: Int? = null,
    val date: String? = null,
    val bpm: Double? = null,
    val musicalKey: String? = null,
    val durationMs: Long? = null,
    val sampleRate: Int? = null,
    val bitRate: Int? = null,
    val bitsPerSample: Int? = null,
    val channels: Int? = null,
    val channelLayout: String? = null,
    val codec: String? = null,
    val container: String? = null,
    val lossless: Boolean? = null,
    val isrc: String? = null,
    val musicBrainzRecordingId: String? = null,
    val musicBrainzTrackId: String? = null,
    val musicBrainzReleaseId: String? = null,
    val musicBrainzReleaseGroupId: String? = null,
    val musicBrainzArtistId: String? = null,
    val musicBrainzReleaseArtistId: String? = null,
    val musicBrainzWorkId: String? = null,
    val replayGainTrackGain: Double? = null,
    val replayGainTrackPeak: Double? = null,
    val replayGainAlbumGain: Double? = null,
    val replayGainAlbumPeak: Double? = null,
    val sourceId: String? = null,
    val externalId: String? = null,
    val fields: Map<String, String> = emptyMap(),
) {
    companion object
}

data class MetadataApplyResult(
    val requestedTrackId: Long,
    val track: TrackEntity?,
    val changedFields: Set<String> = emptySet(),
    val changedIdentityFields: Set<String> = emptySet(),
    val identityChanged: Boolean = changedIdentityFields.isNotEmpty(),
    val effectiveTrackId: Long? = track?.id,
)

/** Single persistence boundary for file, server, plugin and manual metadata. */
class UnifiedMetadataRepository(
    private val database: AppDatabase,
    private val trackDao: TrackDao,
    private val metadataDao: MetadataDao,
) {
    private val graphWriter = MetadataGraphWriter(metadataDao)
    suspend fun apply(
        trackId: Long,
        metadata: ResolvedTrackMetadata,
        policy: MetadataMergePolicy,
    ): MetadataApplyResult {
        val now = currentTimeMillis()
        return database.useWriterConnection { connection -> connection.immediateTransaction {
            val current = trackDao.get(trackId) ?: return@immediateTransaction MetadataApplyResult(trackId, null)
            if (current.metadataLocked && policy != MetadataMergePolicy.ManualOverride) {
                return@immediateTransaction MetadataApplyResult(trackId, current)
            }
            val allowMissing = policy == MetadataMergePolicy.FillMissingFileMetadata ||
                policy == MetadataMergePolicy.FillMissingServerMetadata ||
                policy == MetadataMergePolicy.RefreshPluginMetadata &&
                    current.metadataSource != TrackMetadataSources.Filename
            if (policy == MetadataMergePolicy.RefreshPluginMetadata &&
                current.metadataSource == TrackMetadataSources.Plugin &&
                current.metadataExternalId == metadata.externalId &&
                current.metadataSourceId == metadata.sourceId
            ) return@immediateTransaction MetadataApplyResult(trackId, current)
            val albumId = metadata.album?.takeIf(String::isNotBlank)?.let {
                if (allowMissing && current.albumId != null) current.albumId else resolveAlbum(it, metadata.date)
            } ?: current.albumId
            val resultingSource = when (policy) {
                MetadataMergePolicy.ReplaceFilenameMetadata -> TrackMetadataSources.Filename
                MetadataMergePolicy.FillMissingFileMetadata ->
                    if (current.metadataSource == TrackMetadataSources.Plugin) current.metadataSource
                    else TrackMetadataSources.File
                MetadataMergePolicy.FillMissingServerMetadata ->
                    if (current.metadataSource == TrackMetadataSources.Plugin) current.metadataSource
                    else TrackMetadataSources.Server
                MetadataMergePolicy.RefreshPluginMetadata ->
                    if (current.metadataSource == TrackMetadataSources.Filename) TrackMetadataSources.Plugin
                    else current.metadataSource
                MetadataMergePolicy.ManualOverride -> TrackMetadataSources.Plugin
            }
            fun String?.semantic(existing: String?): String? = if (allowMissing) {
                existing?.takeIf(String::isNotBlank) ?: this?.takeIf(String::isNotBlank)
            } else {
                this?.takeIf(String::isNotBlank) ?: existing
            }
            fun <T> T?.semantic(existing: T?): T? = if (allowMissing) existing ?: this else this ?: existing
            val next = current.copy(
                title = metadata.title.semantic(current.title) ?: current.title,
                artist = metadata.artist.semantic(current.artist),
                albumId = albumId,
                albumArtist = metadata.albumArtist.semantic(current.albumArtist),
                composer = metadata.composer.semantic(current.composer),
                lyricist = metadata.lyricist.semantic(current.lyricist),
                conductor = metadata.conductor.semantic(current.conductor),
                comment = metadata.comment.semantic(current.comment),
                grouping = metadata.grouping.semantic(current.grouping),
                copyright = metadata.copyright.semantic(current.copyright),
                publisher = metadata.publisher.semantic(current.publisher),
                originalReleaseDate = metadata.originalReleaseDate.semantic(current.originalReleaseDate),
                trackNumber = metadata.trackNumber.semantic(current.trackNumber),
                trackTotal = metadata.trackTotal.semantic(current.trackTotal),
                discNumber = metadata.discNumber.semantic(current.discNumber),
                discTotal = metadata.discTotal.semantic(current.discTotal),
                date = metadata.date.semantic(current.date),
                bpm = metadata.bpm.semantic(current.bpm),
                musicalKey = metadata.musicalKey.semantic(current.musicalKey),
                isrc = metadata.isrc.semantic(current.isrc),
                musicBrainzRecordingId = metadata.musicBrainzRecordingId.semantic(current.musicBrainzRecordingId),
                musicBrainzTrackId = metadata.musicBrainzTrackId.semantic(current.musicBrainzTrackId),
                musicBrainzReleaseId = metadata.musicBrainzReleaseId.semantic(current.musicBrainzReleaseId),
                musicBrainzReleaseGroupId = metadata.musicBrainzReleaseGroupId.semantic(current.musicBrainzReleaseGroupId),
                musicBrainzArtistId = metadata.musicBrainzArtistId.semantic(current.musicBrainzArtistId),
                musicBrainzReleaseArtistId = metadata.musicBrainzReleaseArtistId.semantic(current.musicBrainzReleaseArtistId),
                musicBrainzWorkId = metadata.musicBrainzWorkId.semantic(current.musicBrainzWorkId),
                durationMs = if (allowMissing) current.durationMs ?: metadata.durationMs else current.durationMs,
                sampleRate = if (allowMissing) current.sampleRate ?: metadata.sampleRate else current.sampleRate,
                bitRate = if (allowMissing) current.bitRate ?: metadata.bitRate else current.bitRate,
                bitsPerSample = if (allowMissing) current.bitsPerSample ?: metadata.bitsPerSample else current.bitsPerSample,
                channels = if (allowMissing) current.channels ?: metadata.channels else current.channels,
                channelLayout = if (allowMissing) current.channelLayout ?: metadata.channelLayout else current.channelLayout,
                codec = if (allowMissing) current.codec ?: metadata.codec else current.codec,
                container = if (allowMissing) current.container ?: metadata.container else current.container,
                lossless = if (allowMissing) current.lossless ?: metadata.lossless else current.lossless,
                replayGainTrackGain = if (allowMissing) current.replayGainTrackGain ?: metadata.replayGainTrackGain else current.replayGainTrackGain,
                replayGainTrackPeak = if (allowMissing) current.replayGainTrackPeak ?: metadata.replayGainTrackPeak else current.replayGainTrackPeak,
                replayGainAlbumGain = if (allowMissing) current.replayGainAlbumGain ?: metadata.replayGainAlbumGain else current.replayGainAlbumGain,
                replayGainAlbumPeak = if (allowMissing) current.replayGainAlbumPeak ?: metadata.replayGainAlbumPeak else current.replayGainAlbumPeak,
                year = metadata.date.semantic(current.date)?.take(4)?.toIntOrNull() ?: current.year,
                metadataSource = resultingSource,
                metadataSourceId = when {
                    resultingSource != TrackMetadataSources.Plugin -> null
                    policy == MetadataMergePolicy.RefreshPluginMetadata || policy == MetadataMergePolicy.ManualOverride ->
                        metadata.sourceId ?: current.metadataSourceId
                    else -> current.metadataSourceId
                },
                metadataExternalId = when {
                    resultingSource != TrackMetadataSources.Plugin -> null
                    policy == MetadataMergePolicy.RefreshPluginMetadata || policy == MetadataMergePolicy.ManualOverride ->
                        metadata.externalId ?: current.metadataExternalId
                    else -> current.metadataExternalId
                },
                metadataAppliedAt = now,
                metadataLocked = policy == MetadataMergePolicy.ManualOverride || current.metadataLocked,
                updatedAt = now,
            )
            val changed = changedFields(current, next)
            trackDao.upsertAll(listOf(next))
            val incomingArtists = metadata.artists.ifEmpty {
                metadata.artist?.split('/', ';', ',').orEmpty()
            }
            if (incomingArtists.isNotEmpty() && (!allowMissing || current.artist.isNullOrBlank())) {
                replaceTrackArtists(trackId, incomingArtists)
            }
            if (
                !metadata.genre.isNullOrBlank() &&
                (!allowMissing || metadataDao.genreNamesForTrack(trackId).isEmpty())
            ) replaceTrackGenre(trackId, metadata.genre)
            if (
                !metadata.albumArtist.isNullOrBlank() && albumId != null &&
                (!allowMissing || current.albumArtist.isNullOrBlank())
            ) replaceAlbumArtists(albumId, metadata.albumArtist)
            MetadataApplyResult(
                requestedTrackId = trackId,
                track = next,
                changedFields = changed,
                changedIdentityFields = changed.intersect(IDENTITY_FIELDS),
            )
        } }
    }

    suspend fun replaceFilenameMetadata(trackId: Long, filename: String): TrackEntity? {
        val parsed = FilenameMetadataParser.parse(filename) ?: return null
        return apply(
            trackId,
            ResolvedTrackMetadata(
                title = parsed.title,
                artist = parsed.artist,
                trackNumber = parsed.trackNumber,
                discNumber = parsed.discNumber,
            ),
            MetadataMergePolicy.ReplaceFilenameMetadata,
        ).track
    }

    suspend fun fillMissingFileMetadata(trackId: Long, metadata: RemoteMetadata): TrackEntity? =
        apply(trackId, metadata.toResolved(), MetadataMergePolicy.FillMissingFileMetadata).track

    suspend fun fillMissingServerMetadata(trackId: Long, metadata: RemoteMetadata): TrackEntity? =
        apply(trackId, metadata.toResolved(), MetadataMergePolicy.FillMissingServerMetadata).track

    suspend fun refreshPluginMetadata(trackId: Long, candidate: MetaSongCandidate): TrackEntity? {
        return apply(trackId, ResolvedTrackMetadata.from(candidate), MetadataMergePolicy.RefreshPluginMetadata).track
    }

    suspend fun refreshPluginMetadata(trackId: Long, match: TrackCandidateMatch): TrackEntity? {
        if (match.confidence != TrackCandidateMatchConfidence.HIGH) return trackDao.get(trackId)
        return refreshPluginMetadata(trackId, match.candidate)
    }

    /** Automatic plugin entry point; low-confidence matches are guaranteed no-ops. */
    suspend fun applyAutomaticPluginMatch(trackId: Long, match: TrackCandidateMatch): MetadataApplyResult =
        if (match.confidence != TrackCandidateMatchConfidence.HIGH) {
            MetadataApplyResult(trackId, trackDao.get(trackId))
        } else {
            apply(trackId, ResolvedTrackMetadata.from(match.candidate), MetadataMergePolicy.RefreshPluginMetadata)
        }

    /** Applies an explicit user choice and protects it from future automatic refreshes. */
    suspend fun manualOverride(trackId: Long, candidate: MetaSongCandidate): TrackEntity? {
        return apply(trackId, ResolvedTrackMetadata.from(candidate), MetadataMergePolicy.ManualOverride).track
    }

    private suspend fun resolveAlbum(name: String, date: String?): Long {
        val normalized = normalize(name)
        metadataDao.insertAlbums(listOf(AlbumEntity(
            name = name,
            normalizedName = normalized,
            sortName = null,
            year = date?.take(4)?.toIntOrNull(),
            artworkId = null,
        )))
        return metadataDao.findAlbumsByNormalizedNames(listOf(normalized)).single().id
    }

    private suspend fun replaceTrackArtists(trackId: Long, raw: String?) =
        graphWriter.replaceTrackArtists(trackId, raw?.split('/', ';', ',').orEmpty())

    private suspend fun replaceTrackArtists(trackId: Long, names: List<String>) {
        graphWriter.replaceTrackArtists(trackId, names)
    }

    private suspend fun replaceTrackGenre(trackId: Long, raw: String?) {
        raw?.let { graphWriter.replaceTrackGenre(trackId, it) }
    }

    private suspend fun replaceAlbumArtists(albumId: Long, raw: String?) {
        raw?.let { graphWriter.replaceAlbumArtists(albumId, it) }
    }

    private fun normalize(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun changedFields(before: TrackEntity, after: TrackEntity): Set<String> = buildSet {
        if (before.title != after.title) add("title")
        if (before.artist != after.artist) add("artist")
        if (before.albumId != after.albumId) add("albumId")
        if (before.albumArtist != after.albumArtist) add("albumArtist")
        if (before.trackNumber != after.trackNumber) add("trackNumber")
        if (before.discNumber != after.discNumber) add("discNumber")
        if (before.date != after.date) add("date")
        if (before.durationMs != after.durationMs) add("durationMs")
        if (before.isrc != after.isrc) add("isrc")
        if (before.musicBrainzRecordingId != after.musicBrainzRecordingId) add("musicBrainzRecordingId")
        if (before.metadataExternalId != after.metadataExternalId) add("metadataExternalId")
        if (before.metadataSource != after.metadataSource) add("metadataSource")
        if (before.metadataLocked != after.metadataLocked) add("metadataLocked")
    }

    private companion object {
        val IDENTITY_FIELDS = setOf(
            "title", "artist", "albumId", "trackNumber", "discNumber", "date",
            "isrc", "musicBrainzRecordingId", "metadataExternalId", "durationMs",
        )
    }
}

private fun ResolvedTrackMetadata.Companion.from(candidate: MetaSongCandidate): ResolvedTrackMetadata =
    ResolvedTrackMetadata(
        title = candidate.title,
        artist = candidate.artist,
        album = candidate.album,
        date = candidate.date,
        trackNumber = candidate.trackNumber?.substringBefore('/')?.toIntOrNull(),
        trackTotal = candidate.trackNumber?.substringAfter('/', "")?.toIntOrNull()
            ?: candidate.fields.intValue("trackTotal", "track_total"),
        discNumber = candidate.fields.intValue("discNumber", "disc_number"),
        discTotal = candidate.fields.intValue("discTotal", "disc_total"),
        sourceId = candidate.sourceId,
        externalId = candidate.id,
        isrc = candidate.fields.firstValue("isrc", "ISRC"),
        musicBrainzRecordingId = candidate.fields.firstValue("musicbrainzRecordingId", "musicBrainzRecordingId", "mbid"),
        albumArtist = candidate.fields.firstValue("albumArtist", "album_artist"),
        genre = candidate.fields.firstValue("genre"),
        composer = candidate.fields.firstValue("composer"),
        lyricist = candidate.fields.firstValue("lyricist"),
        conductor = candidate.fields.firstValue("conductor"),
        copyright = candidate.fields.firstValue("copyright"),
        publisher = candidate.fields.firstValue("publisher"),
        originalReleaseDate = candidate.fields.firstValue("originalReleaseDate", "original_release_date"),
        bpm = candidate.fields.firstValue("bpm")?.toDoubleOrNull(),
        musicalKey = candidate.fields.firstValue("musicalKey", "musical_key", "key"),
        musicBrainzTrackId = candidate.fields.firstValue("musicBrainzTrackId", "musicbrainzTrackId"),
        musicBrainzReleaseId = candidate.fields.firstValue("musicBrainzReleaseId", "musicbrainzReleaseId"),
        musicBrainzReleaseGroupId = candidate.fields.firstValue(
            "musicBrainzReleaseGroupId",
            "musicbrainzReleaseGroupId",
        ),
        musicBrainzArtistId = candidate.fields.firstValue("musicBrainzArtistId", "musicbrainzArtistId"),
        musicBrainzReleaseArtistId = candidate.fields.firstValue(
            "musicBrainzReleaseArtistId",
            "musicbrainzReleaseArtistId",
        ),
        musicBrainzWorkId = candidate.fields.firstValue("musicBrainzWorkId", "musicbrainzWorkId"),
        fields = candidate.fields,
    )

private fun RemoteMetadata.toResolved(): ResolvedTrackMetadata = ResolvedTrackMetadata(
    title = title,
    artist = artist,
    artists = artists,
    album = album,
    albumArtist = albumArtist,
    genre = genre,
    composer = composer,
    lyricist = lyricist,
    conductor = conductor,
    comment = comment,
    grouping = grouping,
    copyright = copyright,
    publisher = publisher,
    originalReleaseDate = originalReleaseDate,
    trackNumber = trackNumber?.toInt(),
    trackTotal = trackTotal?.toInt(),
    discNumber = discNumber?.toInt(),
    discTotal = discTotal?.toInt(),
    date = date,
    bpm = bpm,
    musicalKey = musicalKey,
    durationMs = durationMs.takeIf { it in 1uL..Long.MAX_VALUE.toULong() }?.toLong(),
    sampleRate = sampleRate?.toInt(),
    bitRate = (audioBitrate ?: overallBitrate)?.toInt(),
    bitsPerSample = bitDepth?.toInt(),
    channels = channels?.toInt(),
    channelLayout = channelLayout,
    codec = codec,
    container = container,
    lossless = lossless,
    isrc = isrc,
    musicBrainzRecordingId = musicbrainzRecordingId,
    musicBrainzTrackId = musicbrainzTrackId,
    musicBrainzReleaseId = musicbrainzReleaseId,
    musicBrainzReleaseGroupId = musicbrainzReleaseGroupId,
    musicBrainzArtistId = musicbrainzArtistId,
    musicBrainzReleaseArtistId = musicbrainzReleaseArtistId,
    musicBrainzWorkId = musicbrainzWorkId,
    replayGainTrackGain = replayGainTrackGain,
    replayGainTrackPeak = replayGainTrackPeak,
    replayGainAlbumGain = replayGainAlbumGain,
    replayGainAlbumPeak = replayGainAlbumPeak,
)

private fun Map<String, String>.firstValue(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key -> entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value }

private fun Map<String, String>.intValue(vararg keys: String): Int? =
    firstValue(*keys)?.toIntOrNull()
