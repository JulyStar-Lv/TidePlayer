package io.github.julystar.musicapp.service.playback.presentation.miniplayer

import io.github.julystar.musicapp.core.domain.model.AudioTechnicalInfo
import io.github.julystar.musicapp.core.domain.model.PlaybackAudioInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopAudioBadgeTest {
    @Test
    fun showsLosslessBadgeForLosslessPlayback() {
        val info = PlaybackAudioInfo(effective = AudioTechnicalInfo(codec = "FLAC", lossless = true))

        assertEquals(DesktopAudioBadge.Lossless, info.desktopAudioBadge())
    }

    @Test
    fun givesDolbyBadgePriorityForDolbyPlayback() {
        val info = PlaybackAudioInfo(
            effective = AudioTechnicalInfo(codec = "E-AC-3 JOC", lossless = true),
        )

        assertEquals(DesktopAudioBadge.Dolby, info.desktopAudioBadge())
    }

    @Test
    fun omitsBadgeForLossyPlayback() {
        val info = PlaybackAudioInfo(effective = AudioTechnicalInfo(codec = "AAC", lossless = false))

        assertNull(info.desktopAudioBadge())
    }
}
