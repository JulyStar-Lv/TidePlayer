package io.github.julystar.musicapp.service.playback.presentation.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.julystar.musicapp.service.playback.domain.SleepModeLeftTime
import io.github.julystar.musicapp.service.playback.presentation.PlayerVM
import io.github.julystar.musicapp.service.playback.presentation.miniplayer.MiniPlayer
import io.github.julystar.musicapp.service.playback.presentation.sleep.SleepModeVM
import io.github.julystar.musicapp.service.playback.presentation.sleep.TimeToPauseModal
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun rememberIsPlaybackPlaying(
    playerVM: PlayerVM = koinViewModel(),
): Boolean {
    val isPlaying by playerVM.playing.collectAsState()
    return isPlaying
}

@Composable
fun rememberHasPlaybackItem(
    playerVM: PlayerVM = koinViewModel(),
): Boolean {
    val playbackState by playerVM.playbackState.collectAsState()
    return playbackState.currentItem != null
}

@Composable
fun PlaybackMiniPlayerHost(
    onOpenNowPlaying: () -> Unit,
    onOpenLyrics: (Long) -> Unit,
    onOpenQueue: () -> Unit,
    lyricsSelected: Boolean = false,
    queueSelected: Boolean = false,
    playerVM: PlayerVM = koinViewModel(),
) {
    val playbackState by playerVM.playbackState.collectAsState()
    if (playbackState.currentItem != null) {
        MiniPlayer(
            onOpenNowPlaying = onOpenNowPlaying,
            onOpenLyrics = onOpenLyrics,
            onOpenQueue = onOpenQueue,
            lyricsSelected = lyricsSelected,
            queueSelected = queueSelected,
            playerVM = playerVM,
        )
    }
}

@Composable
fun rememberOpenSleepTimer(
    sleepModeVM: SleepModeVM = koinViewModel(),
): (SleepModeLeftTime) -> Unit = sleepModeVM::openModal

@Composable
fun PlaybackSleepTimerHost(
    sleepModeVM: SleepModeVM = koinViewModel(),
) {
    TimeToPauseModal(sleepModeVM = sleepModeVM)
}
