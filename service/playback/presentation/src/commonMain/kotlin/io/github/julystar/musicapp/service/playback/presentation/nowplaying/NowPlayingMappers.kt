package io.github.julystar.musicapp.service.playback.presentation.nowplaying

import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.model.CurrentTrackInfo
import io.github.julystar.musicapp.service.playback.domain.PlaybackQueue
import io.github.julystar.musicapp.service.playback.domain.PlaybackStatus
import io.github.julystar.musicapp.service.playback.domain.PlayerState

internal fun CurrentTrackInfo?.toInitialNowPlayingState(
    externalEditorSupported: Boolean,
): NowPlayingState = NowPlayingState(
    currentTrack = this?.toNowPlayingTrackItem(),
    externalEditorSupported = externalEditorSupported,
)

public fun CurrentTrackInfo.toNowPlayingTrackItem(): NowPlayingTrackItem {
    return NowPlayingTrackItem(
        id = id,
        title = title,
        artist = this.artist?.takeIf { it.isNotBlank() },
        durationMs = durationMs,
        artwork = artwork,
        lyrics = lyrics,
        mediaId = mediaId,
        annotation = annotation?.takeIf(String::isNotBlank),
    )
}

public fun PlaybackQueue.toNowPlayingQueueState(
    previousArtwork: Artwork?,
    nextArtwork: Artwork?,
    canPlayPrevious: Boolean = previousArtwork != null,
    canPlayNext: Boolean = nextArtwork != null,
): NowPlayingQueueState {
    return NowPlayingQueueState(
        currentIndex = currentIndex,
        itemCount = items.size,
        canPlayPrevious = canPlayPrevious,
        canPlayNext = canPlayNext,
        previousArtwork = previousArtwork,
        nextArtwork = nextArtwork,
    )
}

public fun PlayerState.toNowPlayingControlsState(): NowPlayingControlsState {
    return NowPlayingControlsState(
        isPlaying = status == PlaybackStatus.Playing,
        isLoading = status == PlaybackStatus.Loading,
        repeatMode = repeatMode,
        shuffleEnabled = shuffleEnabled,
    )
}
