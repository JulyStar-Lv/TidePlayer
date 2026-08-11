package io.github.julystar.musicapp.feature.search.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.julystar.musicapp.core.domain.model.LIBRARY_PLAYBACK_PLAYLIST_ID
import io.github.julystar.musicapp.core.domain.repository.LibraryRepository
import io.github.julystar.musicapp.core.domain.repository.ToastRepository
import io.github.julystar.musicapp.core.domain.repository.UiMessage
import io.github.julystar.musicapp.core.domain.repository.UiMessageKey
import io.github.julystar.musicapp.feature.search.domain.SearchTrackItem
import io.github.julystar.musicapp.service.playback.domain.PlayableItem
import io.github.julystar.musicapp.service.playback.domain.PlaybackController
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SearchRoot(
    viewModel: SearchViewModel = koinViewModel(),
    onNavigateToAlbum: (albumId: Long) -> Unit = {},
    onNavigateToArtist: (artistId: Long) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val playbackController = koinInject<PlaybackController>()
    val libraryRepository = koinInject<LibraryRepository>()
    val toastRepository = koinInject<ToastRepository>()
    val indexedTracks by libraryRepository.tracks.collectAsState()
    val initialLoadComplete by libraryRepository.initialLoadComplete.collectAsState()

    LaunchedEffect(viewModel, playbackController, onNavigateToAlbum, onNavigateToArtist) {
        viewModel.events.collect { event ->
            when (event) {
                is SearchEvent.OpenTrack -> try {
                    playbackController.play(items = listOf(event.track.toPlayableItem()))
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (_: Exception) {
                    toastRepository.emit(UiMessage.Resource(UiMessageKey.SourceUnavailable))
                }
                is SearchEvent.NavigateToAlbum -> onNavigateToAlbum(event.albumId)
                is SearchEvent.NavigateToArtist -> onNavigateToArtist(event.artistId)
                is SearchEvent.ShowMessage -> toastRepository.emit(event.message)
            }
        }
    }

    SearchDesignScreen(
        state = state,
        showSearchContent = shouldShowSearchContent(
            initialLoadComplete = initialLoadComplete,
            indexedTrackCount = indexedTracks.size,
        ),
        onAction = viewModel::onAction,
    )
}

internal fun shouldShowSearchContent(
    initialLoadComplete: Boolean,
    indexedTrackCount: Int,
): Boolean = !initialLoadComplete || indexedTrackCount > 0

internal fun SearchTrackItem.toPlayableItem(): PlayableItem = PlayableItem(
    mediaId = mediaId,
    title = title,
    artist = artist,
    durationMs = durationMs,
    libraryTrackId = id,
    libraryPlaylistId = id?.let { LIBRARY_PLAYBACK_PLAYLIST_ID },
)
