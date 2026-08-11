package io.github.julystar.musicapp.feature.browse.presentation

import io.github.julystar.musicapp.core.domain.repository.UiMessage

import androidx.compose.runtime.Immutable
import io.github.julystar.musicapp.core.domain.model.Artwork
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class BrowseState(
    val isLoading: Boolean = true,
    val albums: ImmutableList<BrowseAlbumItem> = persistentListOf(),
    val artists: ImmutableList<BrowseArtistItem> = persistentListOf(),
    val genres: ImmutableList<String> = persistentListOf(),
    val error: UiMessage? = null,
)

@Immutable
data class BrowseAlbumItem(
    val id: Long,
    val name: String,
    val year: Int?,
    val artwork: Artwork?,
    val trackCount: Int,
)

@Immutable
data class BrowseArtistItem(
    val id: Long,
    val name: String,
    val trackCount: Int,
)
