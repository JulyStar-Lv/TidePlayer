package io.github.julystar.musicapp.feature.settings.presentation

import io.github.julystar.musicapp.core.domain.model.AppLanguageMode
import io.github.julystar.musicapp.core.domain.model.AppThemeMode
import io.github.julystar.musicapp.core.domain.model.LyricTextAlignment
import io.github.julystar.musicapp.core.domain.model.MetadataScanMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsActionTest {

    @Test
    fun `theme action carries selected mode`() {
        val action = SettingsAction.SetThemeMode(AppThemeMode.Dark)
        assertEquals(AppThemeMode.Dark, action.mode)
    }

    @Test
    fun `theme color actions carry artwork manual and palette values`() {
        val artwork = SettingsAction.SetArtworkThemeEnabled(false)
        val manual = SettingsAction.SetManualThemeSeedArgb(0xFFFF5B8AL)
        val palette = SettingsAction.SetCustomThemeSeedArgbValues(
            listOf(0xFFFF5B8AL, 0xFF3D9AFFL),
        )

        assertEquals(false, artwork.enabled)
        assertEquals(0xFFFF5B8AL, manual.argb)
        assertEquals(listOf(0xFFFF5B8AL, 0xFF3D9AFFL), palette.argbValues)
    }

    @Test
    fun `language action carries selected mode`() {
        val action = SettingsAction.SetLanguageMode(AppLanguageMode.English)
        assertEquals(AppLanguageMode.English, action.mode)
    }

    @Test
    fun `metadata scan action carries selected mode`() {
        val action = SettingsAction.SetWebDavMetadataScanMode(MetadataScanMode.Fast)
        assertEquals(MetadataScanMode.Fast, action.mode)
    }

    @Test
    fun `lyric actions carry selected alignment and scale`() {
        val alignment = SettingsAction.SetLyricTextAlignment(LyricTextAlignment.Center)
        val scale = SettingsAction.SetLyricPrimaryFontScalePercent(125)

        assertEquals(LyricTextAlignment.Center, alignment.alignment)
        assertEquals(125, scale.value)
    }

    @Test
    fun `clear confirmation actions are singleton objects`() {
        assertEquals(SettingsAction.RequestClearAudio, SettingsAction.RequestClearAudio)
        assertEquals(SettingsAction.RequestClearImage, SettingsAction.RequestClearImage)
        assertEquals(SettingsAction.ConfirmPendingAction, SettingsAction.ConfirmPendingAction)
    }

    @Test
    fun `connection actions redact passwords`() {
        val actions = listOf(
            SettingsAction.TestWebDavConnection(TEST_SECRET),
            SettingsAction.SaveWebDavAccount(TEST_SECRET),
            SettingsAction.TestSmbConnection(TEST_SECRET),
            SettingsAction.SaveSmbAccount(TEST_SECRET),
        )

        actions.forEach { action ->
            assertFalse(TEST_SECRET in action.toString())
            assertTrue("<redacted>" in action.toString())
        }
    }

    private companion object {
        const val TEST_SECRET = "settings-action-fixture-sensitive-value"
    }
}
