package io.github.julystar.musicapp.feature.playlist.presentation

import androidx.compose.runtime.Immutable
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.model.MediaId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Immutable
data class PlaylistState(
    val playlistId: Long = 0,
    val title: String = "",
    val cover: Artwork? = null,
    val isFavorites: Boolean = false,
    val durationMs: Long = 0,
    val durationLabel: String = formatDuration(null as Duration?),
    val isRemoveDialogOpen: Boolean = false,
    val tracks: ImmutableList<PlaylistTrackItem> = persistentListOf(),
)

@Immutable
data class PlaylistTrackItem(
    val id: Long,
    val title: String,
    val artist: String? = null,
    val albumName: String? = null,
    val durationMs: Long?,
    val sortOrder: Long,
    val mediaId: MediaId?,
) {
    val durationLabel: String
        get() = formatDuration(durationMs?.milliseconds)
}

sealed interface PlaylistAction {
    data object NavigateBack : PlaylistAction
    data object ImportTracks : PlaylistAction
    data object EditPlaylist : PlaylistAction
    data object OpenRemoveDialog : PlaylistAction
    data object CloseRemoveDialog : PlaylistAction
    data object ConfirmRemovePlaylist : PlaylistAction
    data object PlayAll : PlaylistAction
    data class PlayTrack(val trackId: Long) : PlaylistAction
    data class DownloadTrack(val track: PlaylistTrackItem) : PlaylistAction
    data class RemoveTrack(val trackId: Long) : PlaylistAction
    data class MoveTrack(val fromIndex: Int, val toIndex: Int) : PlaylistAction
}

sealed interface PlaylistEvent {
    data class ShowMessage(val message: String) : PlaylistEvent
}
