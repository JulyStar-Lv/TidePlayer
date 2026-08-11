package io.github.julystar.musicapp.service.playback.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class PlaybackUiRequest(
    val items: List<PlayableItem>,
    val startIndex: Int,
)

fun playbackUiRequest(
    items: List<PlayableItem>,
    selectedTrackId: Long? = null,
): PlaybackUiRequest? {
    if (items.isEmpty()) return null
    val startIndex = selectedTrackId?.let { trackId ->
        items.indexOfFirst { it.libraryTrackId == trackId }.takeIf { it >= 0 }
            ?: return null
    } ?: 0
    return PlaybackUiRequest(items = items, startIndex = startIndex)
}

fun CoroutineScope.launchPlaybackUiAction(
    onFailure: (Throwable) -> Unit,
    action: suspend () -> Unit,
): Job = launch {
    try {
        action()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        onFailure(error)
    }
}
