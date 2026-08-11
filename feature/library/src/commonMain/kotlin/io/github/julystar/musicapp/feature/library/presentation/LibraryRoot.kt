package io.github.julystar.musicapp.feature.library.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import io.github.julystar.musicapp.core.domain.model.LIBRARY_PLAYBACK_PLAYLIST_ID
import io.github.julystar.musicapp.core.domain.model.LibraryTrackItem
import io.github.julystar.musicapp.core.domain.repository.ToastRepository
import io.github.julystar.musicapp.service.playback.domain.PlayableItem
import io.github.julystar.musicapp.service.playback.domain.PlaybackController
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LibraryRoot(
    onNavigateToLibraryFolderImport: () -> Unit = {},
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (Long) -> Unit = {},
    onNavigateToPlaylist: (Long) -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToPlaylists: () -> Unit = {},
    viewModel: LibraryVM = koinViewModel(),
) {
    val playbackController = koinInject<PlaybackController>()
    val toastRepository = koinInject<ToastRepository>()
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()
    val playerState by playbackController.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LibraryEvent.ShowMessage -> toastRepository.emit(event.message)
            }
        }
    }

    LibraryDesignScreen(
        state = state,
        currentPlayingTrackId = playerState.currentItem?.libraryTrackId,
        onNavigateToLibraryFolderImport = onNavigateToLibraryFolderImport,
        onNavigateToAlbum = onNavigateToAlbum,
        onNavigateToArtist = onNavigateToArtist,
        onNavigateToPlaylist = onNavigateToPlaylist,
        onNavigateToFavorites = onNavigateToFavorites,
        onNavigateToPlaylists = onNavigateToPlaylists,
        onAction = { action ->
            when (action) {
                is LibraryAction.PlayTrack -> {
                    val items = state.tracks.map { track -> track.toPlayableItem() }
                    val startIndex = state.tracks.indexOfFirst { track -> track.id == action.trackId }
                    if (items.isNotEmpty() && startIndex >= 0) {
                        coroutineScope.launch {
                            playbackController.play(
                                items = items,
                                startIndex = startIndex,
                            )
                        }
                    }
                }
                else -> viewModel.onAction(action)
            }
        },
    )
}

private fun LibraryTrackItem.toPlayableItem(): PlayableItem {
    return PlayableItem(
        mediaId = mediaId,
        title = title,
        artist = artist,
        durationMs = durationMs,
        libraryTrackId = id,
        libraryPlaylistId = LIBRARY_PLAYBACK_PLAYLIST_ID,
    )
}
