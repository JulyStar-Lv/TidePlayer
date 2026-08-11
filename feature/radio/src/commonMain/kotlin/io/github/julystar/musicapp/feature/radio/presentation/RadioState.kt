package io.github.julystar.musicapp.feature.radio.presentation

import io.github.julystar.musicapp.core.domain.repository.UiMessage

import androidx.compose.runtime.Immutable
import io.github.julystar.musicapp.core.domain.model.MediaId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class RadioState(
    val isLoading: Boolean = true,
    val tracks: ImmutableList<RadioTrackItem> = persistentListOf(),
    val error: UiMessage? = null,
)

@Immutable
data class RadioTrackItem(
    val id: Long,
    val title: String,
    val artist: String?,
    val albumName: String?,
    val durationMs: Long?,
    val mediaId: MediaId?,
    val canDownload: Boolean,
)
