package io.github.julystar.musicapp.feature.album.presentation

import io.github.julystar.musicapp.core.domain.repository.UiMessage

import androidx.compose.runtime.Immutable
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.model.MediaId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class AlbumState(
    val isLoading: Boolean = true,
    val albumId: Long = 0,
    val title: String = "",
    val artist: String = "",
    val year: Int? = null,
    val genre: String? = null,
    val artwork: Artwork? = null,
    val tracks: ImmutableList<AlbumTrackItem> = persistentListOf(),
    val error: UiMessage? = null,
)

@Immutable
data class AlbumTrackItem(
    val id: Long,
    val title: String,
    val artist: String? = null,
    val albumTitle: String = "",
    val trackNumber: Int?,
    val discNumber: Int?,
    val durationMs: Long?,
    val mediaId: MediaId?,
    val canDownload: Boolean,
)
