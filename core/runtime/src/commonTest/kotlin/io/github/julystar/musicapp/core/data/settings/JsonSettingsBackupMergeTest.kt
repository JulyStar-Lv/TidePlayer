package io.github.julystar.musicapp.core.data.settings

import io.github.julystar.musicapp.core.domain.model.AppSettings
import io.github.julystar.musicapp.core.domain.model.PlayerInteractionSettings
import io.github.julystar.musicapp.core.domain.model.SettingsBackupSelection
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonSettingsBackupMergeTest {
    @Test
    fun appearanceSelectionRestoresBothThemeColors() {
        val merged = AppSettings.Default.mergeBackup(
            backup = AppSettings.Default.copy(
                manualThemeSeedArgb = 0xFF3D9AFFL,
                darkManualThemeSeedArgb = 0xFF3DCA8AL,
            ),
            selection = SettingsBackupSelection(
                appearance = true,
                playback = false,
                lyrics = false,
                libraryAndMetadata = false,
                networkAndCache = false,
            ),
        )

        assertEquals(0xFF3D9AFFL, merged.manualThemeSeedArgb)
        assertEquals(0xFF3DCA8AL, merged.darkManualThemeSeedArgb)
    }

    @Test
    fun playbackSelectionReplacesWholePlayerInteractionSettings() {
        val currentInteraction = PlayerInteractionSettings(
            immersiveAlbumCoverEnabled = false,
            audioReactiveBackgroundEnabled = false,
            coverSwipeEnabled = false,
            showTotalDuration = true,
            showAudioTechnicalInfo = false,
            desktopShortcutsEnabled = true,
        )
        val backupInteraction = PlayerInteractionSettings(
            immersiveAlbumCoverEnabled = true,
            audioReactiveBackgroundEnabled = true,
            coverSwipeEnabled = true,
            showTotalDuration = false,
            showAudioTechnicalInfo = true,
            desktopShortcutsEnabled = false,
        )

        val merged = AppSettings.Default.copy(
            playerInteraction = currentInteraction,
        ).mergeBackup(
            backup = AppSettings.Default.copy(playerInteraction = backupInteraction),
            selection = SettingsBackupSelection(
                appearance = false,
                playback = true,
                lyrics = false,
                libraryAndMetadata = false,
                networkAndCache = false,
            ),
        )

        assertEquals(backupInteraction, merged.playerInteraction)
    }

    @Test
    fun unselectedPlaybackKeepsCurrentPlayerInteractionSettings() {
        val currentInteraction = PlayerInteractionSettings(
            audioReactiveBackgroundEnabled = true,
            immersiveAlbumCoverEnabled = true,
            coverSwipeEnabled = false,
        )
        val backupInteraction = PlayerInteractionSettings.Default

        val merged = AppSettings.Default.copy(
            playerInteraction = currentInteraction,
        ).mergeBackup(
            backup = AppSettings.Default.copy(playerInteraction = backupInteraction),
            selection = SettingsBackupSelection(
                appearance = false,
                playback = false,
                lyrics = false,
                libraryAndMetadata = false,
                networkAndCache = false,
            ),
        )

        assertEquals(currentInteraction, merged.playerInteraction)
    }
}
