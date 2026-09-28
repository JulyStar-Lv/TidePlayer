package io.github.julystar.musicapp.feature.playlist.presentation

import androidx.compose.runtime.Immutable
import io.github.julystar.musicapp.core.domain.model.Artwork
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class PlaylistsListState(
    val playlists: ImmutableList<PlaylistListItem> = persistentListOf(),
    val mode: PlaylistsListMode = PlaylistsListMode.Normal,
    val isEmpty: Boolean = true,
)

@Immutable
data class PlaylistListItem(
    val id: Long,
    val title: String,
    val musicCount: String,
    val durationLabel: String,
    val cover: Artwork?,
    val createdAt: Long? = null,
)

enum class PlaylistsListMode {
    Normal,
    Adjust,
}

sealed interface PlaylistsListAction {
    data object ToggleMode : PlaylistsListAction
    data object SetModeNormal : PlaylistsListAction
    data object CreatePlaylist : PlaylistsListAction
    data class NavigateToPlaylist(val id: Long) : PlaylistsListAction
    data class MovePlaylist(val fromIndex: Int, val toIndex: Int) : PlaylistsListAction
}

sealed interface PlaylistsListEvent {
    // no one-shot events for now; navigation is handled by Root callbacks
}
