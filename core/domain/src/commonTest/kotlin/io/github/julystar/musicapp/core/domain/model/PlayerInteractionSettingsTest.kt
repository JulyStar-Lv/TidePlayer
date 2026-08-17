package io.github.julystar.musicapp.core.domain.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerInteractionSettingsTest {
    @Test
    fun audioReactiveBackgroundDefaultsToFalse() {
        assertFalse(PlayerInteractionSettings.Default.audioReactiveBackgroundEnabled)
    }

    @Test
    fun backupJsonRoundTripsAudioReactiveBackgroundAndOldJsonDefaultsToFalse() {
        val json = Json { encodeDefaults = true }
        val settings = PlayerInteractionSettings.Default.copy(audioReactiveBackgroundEnabled = true)

        val restored = json.decodeFromString<PlayerInteractionSettings>(json.encodeToString(settings))
        val legacyWithoutAudioReactiveBackground =
            json.decodeFromString<PlayerInteractionSettings>("{\"immersiveAlbumCoverEnabled\":true}")

        assertTrue(restored.audioReactiveBackgroundEnabled)
        assertFalse(legacyWithoutAudioReactiveBackground.audioReactiveBackgroundEnabled)
    }
}
