package io.github.julystar.musicapp.service.playback.presentation.nowplaying

import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.model.CurrentTrackInfo
import io.github.julystar.musicapp.service.playback.domain.PlaybackQueue
import io.github.julystar.musicapp.service.playback.domain.PlaybackStatus
import io.github.julystar.musicapp.service.playback.domain.PlayerState

internal fun CurrentTrackInfo?.toInitialNowPlayingState(): NowPlayingState = NowPlayingState(
    currentTrack = this?.toNowPlayingTrackItem(),
)

public fun CurrentTrackInfo.toNowPlayingTrackItem(): NowPlayingTrackItem {
    return NowPlayingTrackItem(
        id = id,
        title = title,
        artist = this.artist?.takeIf { it.isNotBlank() },
        album = album?.takeIf { it.isNotBlank() },
        durationMs = durationMs,
        artwork = artwork,
        lyrics = lyrics,
        mediaId = mediaId,
        annotation = annotation?.takeIf(String::isNotBlank),
        playbackAudioInfo = playbackAudioInfo,
    )
}

internal fun NowPlayingState.withPlaybackSources(
    sources: List<NowPlayingSourceItem>,
): NowPlayingState {
    val selectedAudioInfo = sources.firstOrNull(NowPlayingSourceItem::isSelected)
        ?.playbackAudioInfo
    return copy(
        currentTrack = currentTrack?.copy(playbackAudioInfo = selectedAudioInfo),
        playbackSources = sources,
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
        items = items.mapIndexed { index, item ->
            NowPlayingQueueItem(
                index = index,
                title = item.title,
                artist = item.artist?.takeIf(String::isNotBlank),
                album = item.album?.takeIf(String::isNotBlank),
                artwork = item.libraryTrackId?.let(Artwork::LibraryTrack),
            )
        },
        canPlayPrevious = canPlayPrevious,
        canPlayNext = canPlayNext,
        previousArtwork = previousArtwork,
        nextArtwork = nextArtwork,
    )
}

public fun PlayerState.toNowPlayingControlsState(
    previousIsPlaying: Boolean = false,
): NowPlayingControlsState {
    return NowPlayingControlsState(
        isPlaying = if (status == PlaybackStatus.Loading) previousIsPlaying else status == PlaybackStatus.Playing,
        isLoading = status == PlaybackStatus.Loading,
        repeatMode = repeatMode,
        shuffleEnabled = shuffleEnabled,
    )
}
