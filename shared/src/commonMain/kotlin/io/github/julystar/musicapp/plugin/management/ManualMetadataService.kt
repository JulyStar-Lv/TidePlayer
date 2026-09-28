package io.github.julystar.musicapp.plugin.management

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import io.github.julystar.musicapp.core.domain.model.MetadataScanMode
import io.github.julystar.musicapp.core.domain.model.toOptions
import io.github.julystar.musicapp.core.data.media.PluginArtworkResolver
import io.github.julystar.musicapp.database.MetadataDao
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.TrackDao
import io.github.julystar.musicapp.database.TrackMetadataSources
import io.github.julystar.musicapp.domain.importing.buildTrackEntity
import io.github.julystar.musicapp.domain.importing.toStorageEntry
import io.github.julystar.musicapp.metadata.MetadataGraphWriter
import io.github.julystar.musicapp.platform.currentTimeMillis
import io.github.julystar.musicapp.plugin.runtime.PluginLookupMode
import io.github.julystar.musicapp.service.playback.data.PlayerRepository
import io.github.julystar.musicapp.service.playback.presentation.nowplaying.NowPlayingTrackItem
import io.github.julystar.musicapp.source.api.MetaLyrics
import io.github.julystar.musicapp.source.api.MetaLyricsCandidate
import io.github.julystar.musicapp.source.api.MetaCoverCandidate
import io.github.julystar.musicapp.source.api.MetaSongCandidate
import io.github.julystar.musicapp.source.api.MetaSongQuery
import io.github.julystar.musicapp.source.storage.RemoteMetadataReader
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uniffi.app_backend.RemoteMetadata
import kotlin.math.abs

data class ManualMetadataResult(
    val song: MetaSongCandidate,
    val lyrics: MetaLyricsCandidate? = null,
)

class ManualMetadataService(
    private val lookup: MetadataLookupUseCase,
    private val database: AppDatabase,
    private val trackDao: TrackDao,
    private val metadataDao: MetadataDao,
    private val playerRepository: PlayerRepository,
    private val metadataReader: RemoteMetadataReader,
    private val artworkResolver: PluginArtworkResolver,
    private val artworkRepository: io.github.julystar.musicapp.core.domain.repository.ArtworkRepository,
) {
    private val metadataGraphWriter = MetadataGraphWriter(metadataDao)
    suspend fun search(
        track: NowPlayingTrackItem,
        keyword: String,
        onResults: (List<ManualMetadataResult>) -> Unit = {},
    ): MetadataLookupCollection<ManualMetadataResult> {
        val normalizedKeyword = keyword.trim()
        val received = mutableListOf<MetaSongCandidate>()
        val resultsMutex = Mutex()
        fun results(candidates: List<MetaSongCandidate>): List<ManualMetadataResult> =
            rankManualMetadataCandidates(candidates, track, normalizedKeyword)
                .mapNotNull { it.toManualMetadataResult() }
        val songResult = lookup.searchSongs(
            query = MetaSongQuery(
                title = track.title,
                artist = track.artist,
                durationMs = track.durationMs,
                keyword = normalizedKeyword.takeIf(String::isNotEmpty),
                pageSize = MANUAL_METADATA_RESULTS_PER_SOURCE,
            ),
            mode = PluginLookupMode.MANUAL,
            onSourceResult = { sourceResult ->
                resultsMutex.withLock {
                    received += sourceResult.items
                    onResults(results(received))
                }
            },
        )
        return MetadataLookupCollection(
            items = results(songResult.items),
            failures = songResult.failures,
            queriedSourceCount = songResult.queriedSourceCount,
        )
    }

    suspend fun loadCoverPreview(candidate: MetaSongCandidate): ByteArray? =
        candidate.toManualCoverCandidate()?.let { artworkResolver.loadPreview(it) }

    suspend fun loadLyricsPreview(candidate: MetaSongCandidate): MetaLyricsCandidate? =
        lookup.getLyricsCandidates(
            candidate = candidate,
            mode = PluginLookupMode.MANUAL,
            pageSize = 1,
        ).let { response ->
            check(response.failures.isEmpty()) { "Failed to load selected result lyrics" }
            response.value?.firstOrNull { it.lyrics.previewType() != null }
        }

    suspend fun apply(
        trackId: Long,
        result: ManualMetadataResult,
        lyricsChecked: Boolean = false,
    ) {
        val candidate = result.song
        val lyrics = if (lyricsChecked || result.lyrics != null) result.lyrics
            else loadLyricsPreview(candidate)
        val now = currentTimeMillis()
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                val current = trackDao.get(trackId) ?: error("Current track is no longer in the library")
                val albumId = candidate.album
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.let { name ->
                        metadataDao.resolveManualMetadataAlbum(
                            name = name,
                            date = candidate.date,
                            currentAlbumId = current.albumId,
                        )
                    }
                    ?: current.albumId
                trackDao.upsertAll(
                    listOf(
                        current.copy(
                            title = candidate.title.trim().ifEmpty { current.title },
                            albumId = albumId,
                            artist = candidate.artist?.trim()?.takeIf(String::isNotEmpty) ?: current.artist,
                            trackNumber = candidate.trackNumber?.substringBefore('/')?.trim()?.toIntOrNull()
                                ?: current.trackNumber,
                            date = candidate.date?.trim()?.takeIf(String::isNotEmpty) ?: current.date,
                            year = candidate.date?.take(4)?.toIntOrNull() ?: current.year,
                            updatedAt = now,
                            metadataSource = TrackMetadataSources.Plugin,
                            metadataLocked = true,
                            metadataSourceId = candidate.sourceId,
                            metadataExternalId = candidate.id,
                            metadataAppliedAt = now,
                        ),
                    ),
                )
                candidate.artist?.let { replaceArtists(trackId, it) }
                lyrics?.lyrics?.toEntity(trackId, now)?.let { lyrics ->
                    metadataDao.upsertLyrics(listOf(lyrics))
                }
            }
        }
        val resultCover = candidate.toManualCoverCandidate()
        if (resultCover != null && !artworkResolver.applyManual(trackId, resultCover)) {
            error("Failed to apply result cover")
        }
        artworkRepository.invalidate()
        playerRepository.refreshCurrentMetadata()
    }

    suspend fun resetFromFile(trackId: Long) {
        val candidate = database.trackSourceRefDao().metadataResetCandidateForTrack(trackId)
            ?: error("No available music file can be used to reset metadata")
        val sourceItem = database.sourceItemDao().get(candidate.sourceItemId)
            ?: error("The selected music file is no longer in the library")
        val entry = candidate.toStorageEntry()
        val metadata = metadataReader.read(entry, MetadataScanMode.Fast.toOptions())
            ?: error("Failed to read metadata from the music file")
        val now = currentTimeMillis()

        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                val current = trackDao.get(trackId)
                    ?: error("Current track is no longer in the library")
                val albumId = metadata.album
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.let { name ->
                        metadataDao.resolveManualMetadataAlbum(
                            name = name,
                            date = metadata.date,
                            currentAlbumId = current.albumId,
                        )
                    }
                trackDao.upsertAll(
                    listOf(
                        buildTrackEntity(
                            entry = entry,
                            metadata = metadata,
                            sourceItem = sourceItem,
                            now = now,
                            existingTrack = current,
                            albumId = albumId,
                            respectMetadataLock = false,
                        ),
                    ),
                )
                metadataDao.deleteManualArtworkForTrack(trackId)
                replaceTrackArtists(trackId, metadata.trackArtistNames())
                replaceTrackGenre(trackId, metadata.genre)
                albumId?.let { replaceAlbumArtist(it, metadata.albumArtist) }
            }
        }
        artworkRepository.invalidate()
        playerRepository.refreshCurrentMetadata()
    }

    private suspend fun replaceArtists(trackId: Long, rawArtists: String) {
        replaceTrackArtists(
            trackId = trackId,
            names = rawArtists.split('/')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinctBy(::normalizeName),
        )
    }

    private suspend fun replaceTrackArtists(trackId: Long, names: List<String>) {
        metadataGraphWriter.replaceTrackArtists(trackId, names)
    }

    private suspend fun replaceTrackGenre(trackId: Long, rawGenre: String?) {
        metadataGraphWriter.replaceTrackGenre(trackId, rawGenre)
    }

    private suspend fun replaceAlbumArtist(albumId: Long, rawArtist: String?) {
        metadataGraphWriter.replaceAlbumArtists(albumId, rawArtist)
    }
}

internal const val MANUAL_METADATA_RESULTS_PER_SOURCE = 20

internal fun MetaSongCandidate.toManualMetadataResult(
    lyricsCandidates: List<MetaLyricsCandidate> = emptyList(),
): ManualMetadataResult? = if (toManualCoverCandidate() == null) {
    null
} else {
    ManualMetadataResult(this, lyricsCandidates.firstOrNull())
}

internal fun MetaSongCandidate.toManualCoverCandidate(): MetaCoverCandidate? =
    pictureUrl?.trim()?.takeIf(String::isNotEmpty)?.let { url ->
        MetaCoverCandidate(
            url = url,
            id = id,
            title = title,
            artist = artist,
            album = album,
            date = date,
            sourceId = sourceId,
        )
    }

internal fun rankManualMetadataCandidates(
    candidates: List<MetaSongCandidate>,
    track: NowPlayingTrackItem,
    keyword: String,
): List<MetaSongCandidate> = candidates
    .distinctBy { candidate -> candidate.sourceId to candidate.id }
    .distinctBy { candidate -> candidate.manualMetadataDuplicateKey() }
    .filter { candidate -> candidate.matchesManualMetadataSearch(track, keyword) }
    .sortedByDescending { candidate ->
        candidate.manualMetadataMatchScore(track, keyword)
    }

private fun MetaSongCandidate.manualMetadataDuplicateKey(): List<String> {
    if (artist.isNullOrBlank() || album.isNullOrBlank()) return listOf(sourceId.orEmpty(), id)
    return listOf(
        sourceId.orEmpty(),
        title.manualMetadataMatchKey(),
        artist.orEmpty().manualMetadataMatchKey(),
        album.orEmpty().manualMetadataMatchKey(),
        date?.trim()?.take(4).orEmpty(),
        durationMs?.div(1000)?.toString().orEmpty(),
    )
}

private fun MetaSongCandidate.matchesManualMetadataSearch(
    track: NowPlayingTrackItem,
    keyword: String,
): Boolean {
    val defaultKeyword = listOfNotNull(track.title, track.artist).joinToString(" ")
    if (keyword.manualMetadataMatchKey() == defaultKeyword.manualMetadataMatchKey()) {
        val expectedTitle = track.title.manualMetadataTitleKey()
        return expectedTitle.isNotEmpty() && title.manualMetadataTitleKey() == expectedTitle
    }
    // A corrected query must be allowed to replace inaccurate file metadata.
    val searchable = listOfNotNull(title, artist).joinToString(" ").manualMetadataMatchKey()
    val tokens = keyword.split(Regex("\\s+"))
        .map(String::manualMetadataMatchKey)
        .filter(String::isNotEmpty)
    return tokens.isNotEmpty() && tokens.all { it in searchable }
}

private val manualMetadataVersionLabel = Regex(
    "^(?:[0-9]{4}\\s+)?(?:live\\b|现场|cover\\b|翻唱|remix\\b|混音|伴奏|纯音乐|" +
        "instrumental\\b|acoustic\\b|不插电|remaster|重制|radio\\s+edit\\b|feat[.\\s]|ft[.\\s])",
    RegexOption.IGNORE_CASE,
)
private val manualMetadataTitleAnnotation = Regex("[（(\\[]([^）)\\]]*)[）)\\]]")

private fun String.manualMetadataTitleKey(): String {
    val withoutVersions = manualMetadataTitleAnnotation.replace(this) { match ->
        if (manualMetadataVersionLabel.containsMatchIn(match.groupValues[1].trim())) "" else match.value
    }
    val suffix = Regex("\\s+[-–—]\\s+").find(withoutVersions)
    val title = if (suffix != null && manualMetadataVersionLabel.containsMatchIn(
            withoutVersions.substring(suffix.range.last + 1).trim(),
        )
    ) {
        withoutVersions.substring(0, suffix.range.first)
    } else {
        withoutVersions
    }
    return title.manualMetadataMatchKey()
}

private fun MetaSongCandidate.manualMetadataMatchScore(
    track: NowPlayingTrackItem,
    keyword: String,
): Int {
    val titleKey = title.manualMetadataMatchKey()
    val artistKey = artist?.manualMetadataMatchKey().orEmpty()
    val albumKey = album?.manualMetadataMatchKey().orEmpty()
    val combinedKey = titleKey + artistKey + albumKey
    val keywordKey = keyword.manualMetadataMatchKey()
    val trackTitleKey = track.title.manualMetadataMatchKey()
    val trackArtistKey = track.artist?.manualMetadataMatchKey().orEmpty()
    val trackAlbumKey = track.album?.manualMetadataMatchKey().orEmpty()

    var score = 0
    if (keywordKey.isNotEmpty() && keywordKey in combinedKey) score += 80
    score += keyword
        .split(Regex("\\s+"))
        .map(String::manualMetadataMatchKey)
        .filter(String::isNotEmpty)
        .count { token -> token in combinedKey } * 8

    if (trackTitleKey.isNotEmpty()) {
        score += when {
            titleKey == trackTitleKey -> 60
            titleKey in trackTitleKey || trackTitleKey in titleKey -> 25
            else -> 0
        }
    }
    if (trackArtistKey.isNotEmpty() && artistKey.isNotEmpty()) {
        score += when {
            artistKey == trackArtistKey -> 30
            artistKey in trackArtistKey || trackArtistKey in artistKey -> 15
            else -> 0
        }
    }

    if (trackAlbumKey.isNotEmpty() && albumKey.isNotEmpty()) {
        score += when {
            albumKey == trackAlbumKey -> 40
            minOf(trackAlbumKey.length, albumKey.length) >= 3 &&
                (trackAlbumKey in albumKey || albumKey in trackAlbumKey) -> 25
            else -> 0
        }
    }

    val expectedDuration = track.durationMs
    val resultDuration = durationMs
    if (expectedDuration != null && resultDuration != null) {
        score += when (abs(expectedDuration - resultDuration)) {
            in 0L..2_000L -> 30
            in 2_001L..5_000L -> 20
            in 5_001L..10_000L -> 10
            else -> 0
        }
    }
    return score
}

private fun String.manualMetadataMatchKey(): String =
    lowercase().filter(Char::isLetterOrDigit)

private fun normalizeName(value: String): String = value.trim().lowercase()

private fun RemoteMetadata.trackArtistNames(): List<String> = artists
    .ifEmpty { listOfNotNull(artist) }
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinctBy(::normalizeName)
