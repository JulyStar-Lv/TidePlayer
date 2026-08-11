package io.github.julystar.musicapp.feature.artist.presentation

import io.github.julystar.musicapp.core.domain.repository.UiMessage

import androidx.compose.runtime.Immutable
import io.github.julystar.musicapp.core.domain.model.Artwork
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class ArtistState(
    val isLoading: Boolean = true,
    val artistId: Long = 0,
    val name: String = "",
    val artwork: Artwork? = null,
    val albums: ImmutableList<ArtistAlbumItem> = persistentListOf(),
    val tracks: ImmutableList<ArtistTrackItem> = persistentListOf(),
    val error: UiMessage? = null,
)

@Immutable
data class ArtistAlbumItem(
    val id: Long,
    val name: String,
    val year: Int?,
    val artwork: Artwork?,
)

@Immutable
data class ArtistTrackItem(
    val id: Long,
    val title: String,
    val albumName: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val durationMs: Long?,
    val mediaId: io.github.julystar.musicapp.core.domain.model.MediaId?,
    val canDownload: Boolean,
    val albumId: Long?,
)
