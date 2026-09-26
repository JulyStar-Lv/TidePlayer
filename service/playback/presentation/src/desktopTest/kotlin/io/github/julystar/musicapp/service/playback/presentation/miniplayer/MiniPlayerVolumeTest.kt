package io.github.julystar.musicapp.service.playback.presentation.miniplayer

import kotlin.test.Test
import kotlin.test.assertEquals

class MiniPlayerVolumeTest {
    @Test
    fun iconLevelTracksPlayerVolume() {
        assertEquals(DesktopVolumeIconLevel.Mute, desktopVolumeIconLevel(0f))
        assertEquals(DesktopVolumeIconLevel.Low, desktopVolumeIconLevel(0.2f))
        assertEquals(DesktopVolumeIconLevel.Medium, desktopVolumeIconLevel(0.5f))
        assertEquals(DesktopVolumeIconLevel.High, desktopVolumeIconLevel(0.8f))
    }
}
