package io.github.julystar.musicapp.service.playback.presentation.nowplaying

import androidx.compose.runtime.Immutable
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.model.Lyrics
import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.AudioTechnicalInfoFormatter
import io.github.julystar.musicapp.core.domain.model.PlaybackAudioInfo
import io.github.julystar.musicapp.core.domain.repository.UiMessage
import io.github.julystar.musicapp.service.playback.domain.RepeatMode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Immutable
data class NowPlayingState(
    val currentTrack: NowPlayingTrackItem? = null,
    val queue: NowPlayingQueueState = NowPlayingQueueState(),
    val controls: NowPlayingControlsState = NowPlayingControlsState(),
    val playbackSources: List<NowPlayingSourceItem> = emptyList(),
)

@Immutable
data class NowPlayingSourceItem(
    val sourceItemId: Long,
    val accountName: String,
    val displayName: String,
    val quality: String?,
    val isSelected: Boolean,
    val playbackAudioInfo: PlaybackAudioInfo? = null,
)

@Immutable
data class NowPlayingTrackItem(
    val id: Long,
    val title: String,
    val artist: String? = null,
    val durationMs: Long?,
    val artwork: Artwork?,
    val lyrics: Lyrics = Lyrics(),
    val mediaId: MediaId?,
    val annotation: String? = null,
    val playbackAudioInfo: PlaybackAudioInfo? = null,
) {
    val canDownload: Boolean
        get() = mediaId != null

    val hasLyric: Boolean
        get() = lyrics.hasLyric

    val audioQuality: String?
        get() = AudioTechnicalInfoFormatter.format(playbackAudioInfo)
}

@Immutable
data class NowPlayingQueueState(
    val currentIndex: Int = -1,
    val itemCount: Int = 0,
    val canPlayPrevious: Boolean = false,
    val canPlayNext: Boolean = false,
    val previousArtwork: Artwork? = null,
    val nextArtwork: Artwork? = null,
)

@Immutable
data class NowPlayingControlsState(
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val shuffleEnabled: Boolean = false,
)

@Immutable
data class NowPlayingProgressState(
    val currentDuration: Duration = 0.milliseconds,
    val bufferDuration: Duration = 0.milliseconds,
    val playerDuration: Duration = 0.milliseconds,
    val lyricIndex: Int = -1,
)

sealed interface NowPlayingAction {
    data object NavigateBack : NowPlayingAction
    data object AddLyric : NowPlayingAction
    data object SearchMetadata : NowPlayingAction
    data object RemoveLyric : NowPlayingAction
    data object RemoveCurrentTrack : NowPlayingAction
    data object DownloadCurrentTrack : NowPlayingAction
    data class SelectPlaybackSource(val sourceItemId: Long) : NowPlayingAction
    data object OpenSleepTimer : NowPlayingAction
    data object OpenLyrics : NowPlayingAction
    data object OpenQueue : NowPlayingAction
    data object PlayPrevious : NowPlayingAction
    data object PlayNext : NowPlayingAction
    data object Resume : NowPlayingAction
    data object Pause : NowPlayingAction
    data object CycleRepeatMode : NowPlayingAction
    data class SeekTo(val positionMs: ULong) : NowPlayingAction
}

sealed interface NowPlayingEvent {
    data class ShowMessage(val message: UiMessage) : NowPlayingEvent
}
