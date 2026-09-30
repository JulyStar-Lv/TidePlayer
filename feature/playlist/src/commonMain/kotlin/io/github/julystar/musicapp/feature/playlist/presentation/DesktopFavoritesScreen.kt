package io.github.julystar.musicapp.feature.playlist.presentation

import androidx.compose.runtime.Composable

@Composable
internal fun DesktopFavoritesScreen(
    state: PlaylistState,
    currentPlayingTrackId: Long?,
    favoriteTrackIds: Set<Long>,
    onToggleFavorite: (Long) -> Unit,
    onAction: (PlaylistAction) -> Unit,
    onShuffle: () -> Unit,
    onBack: (() -> Unit)? = null,
    isPlaying: Boolean = false,
) {
    DesktopPlaylistScreen(
        state = state,
        currentPlayingTrackId = currentPlayingTrackId,
        isPlaying = isPlaying,
        favoriteTrackIds = favoriteTrackIds,
        onToggleFavorite = onToggleFavorite,
        onAction = onAction,
        onShuffle = onShuffle,
        onBack = onBack,
        editable = false,
    )
}
