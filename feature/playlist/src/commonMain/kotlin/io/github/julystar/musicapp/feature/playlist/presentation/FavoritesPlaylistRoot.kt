package io.github.julystar.musicapp.feature.playlist.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.julystar.musicapp.core.domain.model.LIBRARY_PLAYBACK_PLAYLIST_ID
import io.github.julystar.musicapp.core.domain.model.LibraryTrackItem
import io.github.julystar.musicapp.core.domain.model.RepositoryState
import io.github.julystar.musicapp.core.domain.repository.FavoritesRepository
import io.github.julystar.musicapp.service.download.domain.DownloadRequest
import io.github.julystar.musicapp.service.download.domain.EnqueueDownloadUseCase
import io.github.julystar.musicapp.service.playback.domain.PlayableItem
import io.github.julystar.musicapp.service.playback.domain.PlaybackController
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import musicapp.feature.playlist.generated.resources.Res
import musicapp.feature.playlist.generated.resources.playlist_favorites_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun FavoritesPlaylistRoot(
    scaffoldPadding: PaddingValues,
    onNavigateBack: () -> Unit,
) {
    val favoritesRepository = koinInject<FavoritesRepository>()
    val playbackController = koinInject<PlaybackController>()
    val enqueueDownload = koinInject<EnqueueDownloadUseCase>()
    val coroutineScope = rememberCoroutineScope()
    val favoriteTracksState by favoritesRepository.favoriteTracks().collectAsState(RepositoryState.Loading)
    val favoriteTrackIds by favoritesRepository.favoriteTrackIds.collectAsState(emptySet())
    val playerState by playbackController.state.collectAsState()
    val title = stringResource(Res.string.playlist_favorites_title)
    val state = remember(title, favoriteTracksState) {
        favoriteTracksState.dataOrNull.orEmpty().toFavoritesPlaylistState(title)
    }

    PlaylistScreen(
        state = state,
        currentPlayingTrackId = playerState.currentItem?.libraryTrackId,
        favoriteTrackIds = favoriteTrackIds,
        scaffoldPadding = scaffoldPadding,
        onToggleFavorite = { trackId ->
            coroutineScope.launch { favoritesRepository.toggleFavorite(trackId) }
        },
        onAction = { action ->
            when (action) {
                PlaylistAction.NavigateBack -> onNavigateBack()
                PlaylistAction.PlayAll -> {
                    val items = state.tracks.map(PlaylistTrackItem::toFavoritesPlayableItem)
                    if (items.isNotEmpty()) {
                        coroutineScope.launch { playbackController.play(items = items) }
                    }
                }
                is PlaylistAction.PlayTrack -> {
                    val items = state.tracks.map(PlaylistTrackItem::toFavoritesPlayableItem)
                    val startIndex = state.tracks.indexOfFirst { track -> track.id == action.trackId }
                    if (items.isNotEmpty() && startIndex >= 0) {
                        coroutineScope.launch {
                            playbackController.play(items = items, startIndex = startIndex)
                        }
                    }
                }
                is PlaylistAction.DownloadTrack -> {
                    action.track.mediaId?.let { mediaId ->
                        coroutineScope.launch {
                            try {
                                enqueueDownload(
                                    DownloadRequest(
                                        mediaId = mediaId,
                                        title = action.track.title,
                                        artist = action.track.artist,
                                        durationMs = action.track.durationMs,
                                    ),
                                )
                            } catch (exception: CancellationException) {
                                throw exception
                            } catch (_: Throwable) {
                                return@launch
                            }
                        }
                    }
                }
                PlaylistAction.ImportTracks,
                PlaylistAction.EditPlaylist,
                PlaylistAction.OpenRemoveDialog,
                PlaylistAction.CloseRemoveDialog,
                PlaylistAction.ConfirmRemovePlaylist,
                is PlaylistAction.RemoveTrack,
                is PlaylistAction.MoveTrack -> Unit
            }
        },
        editable = false,
    )
}

internal fun List<LibraryTrackItem>.toFavoritesPlaylistState(title: String): PlaylistState {
    return PlaylistState(
        playlistId = LIBRARY_PLAYBACK_PLAYLIST_ID,
        title = title,
        isFavorites = true,
        durationMs = sumOf { track -> track.durationMs ?: 0L },
        tracks = mapIndexed { index, track ->
            PlaylistTrackItem(
                id = track.id,
                title = track.title,
                artist = track.artist,
                durationMs = track.durationMs,
                sortOrder = index.toLong(),
                mediaId = track.mediaId,
            )
        }.toPersistentList(),
    )
}

private fun PlaylistTrackItem.toFavoritesPlayableItem(): PlayableItem {
    return PlayableItem(
        mediaId = mediaId,
        title = title,
        artist = artist?.takeIf { it.isNotBlank() },
        durationMs = durationMs,
        libraryTrackId = id,
        libraryPlaylistId = LIBRARY_PLAYBACK_PLAYLIST_ID,
    )
}
