package io.github.julystar.musicapp.feature.playlist.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import io.github.julystar.musicapp.core.domain.model.LIBRARY_PLAYBACK_PLAYLIST_ID
import io.github.julystar.musicapp.core.domain.model.RepositoryState
import io.github.julystar.musicapp.core.domain.repository.FavoritesRepository
import io.github.julystar.musicapp.core.domain.repository.PlaylistRepository
import io.github.julystar.musicapp.service.playback.domain.PlayableItem
import io.github.julystar.musicapp.service.playback.domain.PlaybackController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
internal fun DesktopPlaylistsRoot(
    state: PlaylistsListState,
    onNavigateToPlaylist: (Long) -> Unit,
    onNavigateToFavorites: (() -> Unit)?,
    onCreatePlaylist: () -> Unit,
) {
    val playlists = koinInject<PlaylistRepository>()
    val favorites = koinInject<FavoritesRepository>()
    val player = koinInject<PlaybackController>()
    val scope = rememberCoroutineScope()
    val favoriteCount by favorites.favoriteCount.collectAsState(0)

    DesktopPlaylistsScreen(
        state = state,
        favoriteCount = favoriteCount,
        onOpenPlaylist = onNavigateToPlaylist,
        onOpenFavorites = onNavigateToFavorites,
        onCreatePlaylist = onCreatePlaylist,
        onPlayPlaylist = { id ->
            scope.launch {
                val items = playlists.observePlaylistTracks(id).first().map { track ->
                    PlayableItem(
                        mediaId = track.mediaId,
                        title = track.title,
                        artist = track.artist,
                        album = track.albumName,
                        durationMs = track.durationMs,
                        libraryTrackId = track.trackId,
                        libraryPlaylistId = id,
                    )
                }
                if (items.isNotEmpty()) player.play(items)
            }
        },
        onPlayFavorites = {
            scope.launch {
                val tracks = favorites.favoriteTracks().first { it !is RepositoryState.Loading }
                val items = tracks.dataOrNull.orEmpty().map { track ->
                    PlayableItem(
                        mediaId = track.mediaId,
                        title = track.title,
                        artist = track.artist,
                        album = track.albumName,
                        durationMs = track.durationMs,
                        libraryTrackId = track.id,
                        libraryPlaylistId = LIBRARY_PLAYBACK_PLAYLIST_ID,
                    )
                }
                if (items.isNotEmpty()) player.play(items)
            }
        },
    )
}
