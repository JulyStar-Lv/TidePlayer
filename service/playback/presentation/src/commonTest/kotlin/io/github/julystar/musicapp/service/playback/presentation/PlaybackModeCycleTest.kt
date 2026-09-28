package io.github.julystar.musicapp.service.playback.presentation

import io.github.julystar.musicapp.service.playback.domain.PlayerState
import io.github.julystar.musicapp.service.playback.domain.RepeatMode
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackModeCycleTest {
    @Test
    fun repeatButtonCyclesOffAllOneWithoutChangingShuffle() {
        for (shuffleEnabled in listOf(false, true)) {
            var state = PlayerState(repeatMode = RepeatMode.Off, shuffleEnabled = shuffleEnabled)
            for (expected in listOf(RepeatMode.All, RepeatMode.One, RepeatMode.Off)) {
                state = state.copy(repeatMode = state.repeatMode.nextRepeatMode())
                assertEquals(expected, state.repeatMode)
                assertEquals(shuffleEnabled, state.shuffleEnabled)
            }
        }
    }

    @Test
    fun cyclesListRepeatShuffleAndSingleRepeat() {
        val shuffle = PlayerState(
            repeatMode = RepeatMode.All,
            shuffleEnabled = false,
        ).nextPlaybackMode()
        assertEquals(
            PlaybackModeSelection(RepeatMode.All, shuffleEnabled = true),
            shuffle,
        )

        val singleRepeat = PlayerState(
            repeatMode = shuffle.repeatMode,
            shuffleEnabled = shuffle.shuffleEnabled,
        ).nextPlaybackMode()
        assertEquals(
            PlaybackModeSelection(RepeatMode.One, shuffleEnabled = false),
            singleRepeat,
        )

        val listRepeat = PlayerState(
            repeatMode = singleRepeat.repeatMode,
            shuffleEnabled = singleRepeat.shuffleEnabled,
        ).nextPlaybackMode()
        assertEquals(
            PlaybackModeSelection(RepeatMode.All, shuffleEnabled = false),
            listRepeat,
        )
    }

    @Test
    fun normalizesLegacyOffModeToListRepeat() {
        assertEquals(
            PlaybackModeSelection(RepeatMode.All, shuffleEnabled = false),
            PlayerState(repeatMode = RepeatMode.Off).nextPlaybackMode(),
        )
    }
}
