package io.github.julystar.musicapp.service.playback.domain

import io.github.julystar.musicapp.core.domain.model.PlaybackAudioInfo

data class PlaybackSourceOption(
    val sourceItemId: Long,
    val accountName: String,
    val displayName: String,
    val quality: String?,
    val isSelected: Boolean,
    val playbackAudioInfo: PlaybackAudioInfo? = null,
)

interface PlaybackSourceRepository {
    suspend fun sources(trackId: Long): List<PlaybackSourceOption>
    suspend fun select(trackId: Long, sourceItemId: Long): Boolean
}
