package io.github.julystar.musicapp.service.playback.data

import io.github.julystar.musicapp.database.TrackSourcePlaybackCandidate
import io.github.julystar.musicapp.database.TrackDao
import io.github.julystar.musicapp.database.TrackSourceRefDao
import io.github.julystar.musicapp.platform.currentTimeMillis
import io.github.julystar.musicapp.service.playback.domain.PlaybackSourceOption
import io.github.julystar.musicapp.service.playback.domain.PlaybackSourceRepository
import io.github.julystar.musicapp.core.domain.model.AudioTechnicalInfoFormatter

class RoomPlaybackSourceRepository(
    private val trackSourceRefDao: TrackSourceRefDao,
    private val trackDao: TrackDao,
) : PlaybackSourceRepository {
    override suspend fun sources(trackId: Long): List<PlaybackSourceOption> {
        val candidates = trackSourceRefDao.playbackCandidates(trackId)
        val selectedSourceItemId = candidates
            .filter { candidate -> candidate.ref.isPreferred }
            .singleOrNull()
            ?.item
            ?.id
            ?: candidates.firstOrNull()?.item?.id
        val track = trackDao.get(trackId)
        return candidates.map { candidate ->
            candidate.toPlaybackSourceOption(
                isSelected = candidate.item.id == selectedSourceItemId,
                fallbackTrack = track,
            )
        }
    }

    override suspend fun select(trackId: Long, sourceItemId: Long): Boolean {
        return trackSourceRefDao.selectPreferredSource(
            trackId = trackId,
            sourceItemId = sourceItemId,
            now = currentTimeMillis(),
        )
    }
}

private fun TrackSourcePlaybackCandidate.toPlaybackSourceOption(
    isSelected: Boolean,
    fallbackTrack: io.github.julystar.musicapp.database.TrackEntity?,
): PlaybackSourceOption {
    val audioInfo = toPlaybackAudioInfo(fallbackTrack)
    return PlaybackSourceOption(
        sourceItemId = item.id,
        accountName = account.displayName,
        displayName = item.displayName,
        quality = AudioTechnicalInfoFormatter.format(audioInfo),
        isSelected = isSelected,
        playbackAudioInfo = audioInfo,
    )
}
