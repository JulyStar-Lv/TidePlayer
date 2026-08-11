package io.github.julystar.musicapp.feature.browse.presentation

import io.github.julystar.musicapp.core.domain.repository.UiMessage

import androidx.compose.runtime.Immutable
import io.github.julystar.musicapp.core.domain.model.MediaId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class GenreTracksState(
    val isLoading: Boolean = true,
    val genre: String = "",
    val tracks: ImmutableList<GenreTrackItem> = persistentListOf(),
    val error: UiMessage? = null,
)

@Immutable
data class GenreTrackItem(
    val id: Long,
    val title: String,
    val artist: String?,
    val albumName: String?,
    val durationMs: Long?,
    val mediaId: MediaId?,
    val canDownload: Boolean,
)
