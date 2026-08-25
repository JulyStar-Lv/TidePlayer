package io.github.julystar.musicapp.plugin.management

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.core.model.synced.UncheckedSyncedLine
import com.mocharealm.accompanist.lyrics.core.parser.AutoParser
import io.github.julystar.musicapp.core.domain.model.MetadataScanMode
import io.github.julystar.musicapp.core.domain.model.toOptions
import io.github.julystar.musicapp.core.data.media.PluginArtworkResolver
import io.github.julystar.musicapp.database.AlbumEntity
import io.github.julystar.musicapp.database.LyricsEntity
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
import io.github.julystar.musicapp.source.api.MetaLyricLine
import io.github.julystar.musicapp.source.api.MetaLyricWord
import io.github.julystar.musicapp.source.api.MetaLyricsCandidate
import io.github.julystar.musicapp.source.api.MetaCoverCandidate
import io.github.julystar.musicapp.source.api.MetaSongCandidate
import io.github.julystar.musicapp.source.api.MetaSongQuery
import io.github.julystar.musicapp.source.storage.RemoteMetadataReader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import uniffi.app_backend.RemoteMetadata
import kotlin.math.abs

data class ManualMetadataResult(
    val song: MetaSongCandidate,
    val lyrics: MetaLyricsCandidate,
)

class ManualMetadataService(
    private val lookup: MetadataLookupUseCase,
    private val database: AppDatabase,
    private val trackDao: TrackDao,
    private val metadataDao: MetadataDao,
    private val playerRepository: PlayerRepository,
    private val metadataReader: RemoteMetadataReader,
    private val artworkResolver: PluginArtworkResolver,
) {
    private val metadataGraphWriter = MetadataGraphWriter(metadataDao)
    suspend fun search(
        track: NowPlayingTrackItem,
        keyword: String,
    ): MetadataLookupCollection<ManualMetadataResult> {
        val normalizedKeyword = keyword.trim()
        val songResult = lookup.searchSongs(
            query = MetaSongQuery(
                title = track.title,
                artist = track.artist,
                durationMs = track.durationMs,
                keyword = normalizedKeyword.takeIf(String::isNotEmpty),
                pageSize = MANUAL_METADATA_RESULTS_PER_SOURCE,
            ),
            mode = PluginLookupMode.MANUAL,
        )
        val candidates = rankManualMetadataCandidates(
            candidates = songResult.items,
            track = track,
            keyword = normalizedKeyword,
        ).filter { candidate -> candidate.toManualCoverCandidate() != null }
        val lyricResults = coroutineScope {
            candidates.map { candidate ->
                async {
                    candidate to lookup.getLyricsCandidates(
                        candidate = candidate,
                        mode = PluginLookupMode.MANUAL,
                        pageSize = 1,
                    )
                }
            }.awaitAll()
        }
        return MetadataLookupCollection(
            items = lyricResults.mapNotNull { (candidate, lyricsResult) ->
                candidate.toManualMetadataResult(lyricsResult.value.orEmpty())
            },
            failures = (songResult.failures + lyricResults.flatMap { (_, result) -> result.failures })
                .distinctBy { failure -> failure.sourceId to failure.operation },
            queriedSourceCount = songResult.queriedSourceCount,
        )
    }

    suspend fun loadCoverPreview(candidate: MetaSongCandidate): ByteArray? =
        candidate.toManualCoverCandidate()?.let { artworkResolver.loadPreview(it) }

    suspend fun apply(
        trackId: Long,
        result: ManualMetadataResult,
    ) {
        val candidate = result.song
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
                result.lyrics.lyrics.toEntity(trackId, now)?.let { lyrics ->
                    metadataDao.upsertLyrics(listOf(lyrics))
                }
            }
        }
        val resultCover = candidate.toManualCoverCandidate()
        if (resultCover != null && !artworkResolver.applyManual(trackId, resultCover)) {
            error("Failed to apply result cover")
        }
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
                replaceTrackArtists(trackId, metadata.trackArtistNames())
                replaceTrackGenre(trackId, metadata.genre)
                albumId?.let { replaceAlbumArtist(it, metadata.albumArtist) }
            }
        }
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

private const val MANUAL_METADATA_RESULTS_PER_SOURCE = 3

internal fun MetaSongCandidate.toManualMetadataResult(
    lyricsCandidates: List<MetaLyricsCandidate>,
): ManualMetadataResult? = if (toManualCoverCandidate() == null) {
    null
} else {
    lyricsCandidates.firstOrNull()?.let { lyrics -> ManualMetadataResult(this, lyrics) }
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
    .sortedByDescending { candidate ->
        candidate.manualMetadataMatchScore(track, keyword)
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

internal suspend fun MetadataDao.resolveManualMetadataAlbum(
    name: String,
    date: String?,
    currentAlbumId: Long?,
): Long {
    val normalized = normalizeName(name)
    val preservedArtworkId = currentAlbumId
        ?.let { albumId -> getArtworkForAlbum(albumId)?.id }
    insertAlbums(
        listOf(
            AlbumEntity(
                name = name,
                normalizedName = normalized,
                sortName = null,
                year = date?.take(4)?.toIntOrNull(),
                artworkId = preservedArtworkId,
            ),
        ),
    )
    val album = findAlbumsByNormalizedNames(listOf(normalized)).single()
    if (
        preservedArtworkId != null &&
        album.artworkId == null &&
        getArtworkForAlbum(album.id) == null
    ) {
        upsertAlbums(listOf(album.copy(artworkId = preservedArtworkId)))
    }
    return album.id
}

internal fun MetaLyrics.toEntity(trackId: Long, updatedAt: Long): LyricsEntity? {
    val persistableLines = lines.attachTranslatedTrack(translated)
    val structuredWordTimed = persistableLines.toEnhancedLrcOrNull()?.let { content ->
        PersistedLyricPayload("LRC", content, wordTimed = true)
    }
    val rawWordTimed = listOfNotNull(
        rawTtml.toPayload("TTML", wordTimed = true),
        rawMultiPersonEnhancedLrc.toPayload("LRC", wordTimed = true),
        rawEnhancedLrc.toPayload("LRC", wordTimed = true),
        rawVerbatimLrc.toPayload("LRC", wordTimed = true),
    ).firstOrNull()
    val hasStructuredTranslation = persistableLines.any { !it.translation.isNullOrBlank() }
    val wordTimed = if (hasStructuredTranslation) {
        structuredWordTimed ?: rawWordTimed
    } else {
        rawWordTimed ?: structuredWordTimed
    }
    val plain = rawPlainLrc.toPayload("LRC")
    val generated = persistableLines.takeIf { it.isNotEmpty() }?.joinToString("\n") { line ->
        val text = line.text.trim()
        val timeTag = line.startMs?.toLrcTimestamp().orEmpty()
        buildString {
            append(timeTag)
            append(text)
            line.translation?.trim()?.takeIf(String::isNotEmpty)?.let { translation ->
                append('\n')
                append(timeTag)
                append(translation)
            }
        }
    }
    val generatedPayload = generated?.let { content ->
        PersistedLyricPayload(
            format = if (persistableLines.any { it.startMs != null }) "LRC" else "TEXT",
            content = content,
            wordTimed = false,
        )
    }
    val fallbackPayload = if (hasStructuredTranslation) {
        generatedPayload ?: plain
    } else {
        plain ?: generatedPayload
    }
    val payload = wordTimed ?: fallbackPayload ?: return null
    val synchronized = payload.format != "TEXT"
    return LyricsEntity(
        trackId = trackId,
        format = payload.format,
        language = null,
        synchronized = synchronized,
        content = payload.content,
        sourcePath = "external:plugin",
        updatedAt = updatedAt,
        sourceKind = when {
            payload.format == "TTML" -> "ExternalTtml"
            payload.wordTimed -> "ExternalWordTimed"
            else -> "ExternalPlain"
        },
    )
}

private fun List<MetaLyricLine>.attachTranslatedTrack(translated: String?): List<MetaLyricLine> {
    val candidates = translated?.toTranslationCandidates().orEmpty()
    if (candidates.isEmpty()) return this

    return mapIndexed { index, line ->
        if (!line.translation.isNullOrBlank()) return@mapIndexed line
        val timestampMatch = line.startMs?.let { startMs ->
            candidates
                .mapNotNull { candidate ->
                    candidate.startMs?.let { candidateStartMs ->
                        candidate to abs(candidateStartMs - startMs)
                    }
                }
                .minByOrNull { (_, distanceMs) -> distanceMs }
                ?.takeIf { (_, distanceMs) -> distanceMs <= 150L }
                ?.first
        }
        line.copy(translation = (timestampMatch ?: candidates.getOrNull(index))?.text)
    }
}

private data class TranslationCandidate(
    val text: String,
    val startMs: Long?,
)

private fun String.toTranslationCandidates(): List<TranslationCandidate> {
    val parsed = runCatching { AutoParser().parse(this).lines }.getOrDefault(emptyList())
    if (parsed.isNotEmpty()) {
        return parsed.mapNotNull { line ->
            val text = when (line) {
                is KaraokeLine -> line.syllables.joinToString(separator = "") { it.content }
                is SyncedLine -> line.content
                is UncheckedSyncedLine -> line.content
                else -> null
            }?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            TranslationCandidate(text = text, startMs = line.start.toLong())
        }
    }
    return lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map { text -> TranslationCandidate(text = text, startMs = null) }
        .toList()
}

private data class PersistedLyricPayload(
    val format: String,
    val content: String,
    val wordTimed: Boolean,
)

private fun String?.toPayload(
    format: String,
    wordTimed: Boolean = false,
): PersistedLyricPayload? = this
    ?.takeIf(String::isNotBlank)
    ?.let { content -> PersistedLyricPayload(format, content, wordTimed) }

private fun List<MetaLyricLine>.toEnhancedLrcOrNull(): String? {
    if (none { line -> line.words.any { word -> word.startMs != null } }) return null

    return mapIndexedNotNull { index, line ->
        val lineStart = line.startMs ?: return@mapIndexedNotNull null
        val timedWords = line.words.mapNotNull { word -> word.toAbsoluteTiming(lineStart) }
        val timeTag = lineStart.toLrcTimestamp()
        val primaryLine = if (timedWords.size != line.words.size || timedWords.isEmpty()) {
            "$timeTag${line.text.trim()}"
        } else {
            val lineEnd = line.endMs
                ?: timedWords.last().endMs
                ?: getOrNull(index + 1)?.startMs
                ?: (timedWords.last().startMs + 1)
            buildString {
                append(timeTag)
                timedWords.forEach { word ->
                    append('<')
                    append(word.startMs.toEnhancedLrcTimestamp())
                    append('>')
                    append(word.text)
                }
                append('<')
                append(lineEnd.coerceAtLeast(timedWords.last().startMs + 1).toEnhancedLrcTimestamp())
                append('>')
            }
        }
        line.translation?.trim()?.takeIf(String::isNotEmpty)?.let { translation ->
            "$primaryLine\n$timeTag$translation"
        } ?: primaryLine
    }.joinToString("\n").takeIf(String::isNotBlank)
}

private data class TimedWord(
    val text: String,
    val startMs: Long,
    val endMs: Long?,
)

private fun MetaLyricWord.toAbsoluteTiming(lineStartMs: Long): TimedWord? {
    val start = startMs ?: return null
    return TimedWord(
        text = text,
        startMs = start.toAbsoluteTime(lineStartMs),
        endMs = endMs?.toAbsoluteTime(lineStartMs),
    )
}

private fun Long.toAbsoluteTime(lineStartMs: Long): Long =
    if (this < lineStartMs) lineStartMs + this else this

private fun Long.toLrcTimestamp(): String {
    val safe = coerceAtLeast(0)
    val minutes = safe / 60_000
    val seconds = (safe % 60_000) / 1_000
    val hundredths = (safe % 1_000) / 10
    return "[${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}." +
        "${hundredths.toString().padStart(2, '0')}]"
}

private fun Long.toEnhancedLrcTimestamp(): String {
    val safe = coerceAtLeast(0)
    val minutes = safe / 60_000
    val seconds = (safe % 60_000) / 1_000
    val milliseconds = safe % 1_000
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}." +
        milliseconds.toString().padStart(3, '0')
}

private fun normalizeName(value: String): String = value.trim().lowercase()

private fun RemoteMetadata.trackArtistNames(): List<String> = artists
    .ifEmpty { listOfNotNull(artist) }
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinctBy(::normalizeName)
