package io.github.julystar.musicapp.core.data

import io.github.julystar.musicapp.core.domain.model.DomainLyrics
import io.github.julystar.musicapp.core.domain.model.AppSettings
import io.github.julystar.musicapp.core.domain.model.filterLyricTextBlock
import io.github.julystar.musicapp.core.domain.repository.LyricsRepository
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.database.MetadataDao
import io.github.julystar.musicapp.database.TrackDao
import io.github.julystar.musicapp.domain.importing.TrackMetadataPrefetcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class LyricsRepositoryImpl(
    private val metadataDao: MetadataDao,
    private val trackDao: TrackDao,
    private val metadataPrefetcher: TrackMetadataPrefetcher,
    private val settingsRepository: SettingsRepository,
) : LyricsRepository {

    override suspend fun loadLyrics(trackId: Long): DomainLyrics {
        val track = trackDao.findByIds(listOf(trackId)).firstOrNull()
        var candidates = metadataDao.getLyricsCandidates(trackId)
        if (candidates.isEmpty()) {
            try {
                metadataPrefetcher.prefetch(trackId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Metadata prefetch is best effort and should not block the lyrics screen.
            }
            candidates = metadataDao.getLyricsCandidates(trackId)
        }
        val settings = settingsRepository.settings.first()
        val lyrics = candidates.selectLyrics(settings.lyrics)
        val artistNames = metadataDao.artistNamesForTrack(trackId)

        val lines = lyrics?.content
            ?.let(settings.lyrics::filterLyricTextBlock)
            ?: emptyList()

        return DomainLyrics(
            trackTitle = track?.title.orEmpty(),
            trackArtist = artistNames.joinToString(", ").ifBlank { track?.artist },
            lines = lines,
            format = lyrics?.format,
            synchronized = lyrics?.synchronized ?: false,
        )
    }
}
