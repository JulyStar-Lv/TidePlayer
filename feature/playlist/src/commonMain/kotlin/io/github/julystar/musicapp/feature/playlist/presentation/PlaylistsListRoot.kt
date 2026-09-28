package io.github.julystar.musicapp.feature.playlist.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.julystar.musicapp.core.presentation.platform.isDesktopPlatform
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PlaylistsListRoot(
    onNavigateToPlaylist: (Long) -> Unit,
    onCreatePlaylist: () -> Unit = {},
    playlistsViewModel: PlaylistsVM = koinViewModel(),
    onNavigateToFavorites: (() -> Unit)? = null,
) {
    val state by playlistsViewModel.state.collectAsState()

    if (isDesktopPlatform()) {
        AppleMusicPlaylistsDesktopRoot(
            state = state,
            onNavigateToPlaylist = onNavigateToPlaylist,
            onNavigateToFavorites = onNavigateToFavorites,
            onCreatePlaylist = onCreatePlaylist,
        )
        return
    }

    PlaylistsListScreen(
        state = state,
        onAction = { action ->
            when (action) {
                is PlaylistsListAction.NavigateToPlaylist -> onNavigateToPlaylist(action.id)
                PlaylistsListAction.CreatePlaylist -> onCreatePlaylist()
                else -> playlistsViewModel.onAction(action)
            }
        },
    )
}
