package io.github.julystar.musicapp.core.data.settings

import androidx.datastore.preferences.core.edit
import io.github.julystar.musicapp.core.data.datastore.createAppDataStore
import io.github.julystar.musicapp.core.domain.model.AppLanguageMode
import io.github.julystar.musicapp.core.domain.model.AppSettings
import io.github.julystar.musicapp.core.domain.model.AppThemeMode
import io.github.julystar.musicapp.core.domain.model.AudioFocusMode
import io.github.julystar.musicapp.core.domain.model.AutoScanMode
import io.github.julystar.musicapp.core.domain.model.MAX_AUDIO_CACHE_LIMIT_BYTES
import io.github.julystar.musicapp.core.domain.model.MAX_IMAGE_CACHE_LIMIT_BYTES
import io.github.julystar.musicapp.core.domain.model.LyricTextAlignment
import io.github.julystar.musicapp.core.domain.model.MissingFilePolicy
import io.github.julystar.musicapp.core.domain.model.MetadataScanMode
import io.github.julystar.musicapp.core.domain.model.PlayerInteractionSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.Path.Companion.toPath
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataStoreSettingsRepositoryTest {
    @Test
    fun persistsAndReloadsSettings() = withRepository { dataStore, repository ->
        assertEquals(AppSettings.Default, repository.settingsValue())

        repository.setThemeMode(AppThemeMode.Light)
        repository.setArtworkThemeEnabled(false)
        repository.setManualThemeSeedArgb(0xFF3D9AFFL)
        repository.setCustomThemeSeedArgbValues(listOf(0xFF3D9AFFL, 0xFFFFD93DL))
        repository.setLanguageMode(AppLanguageMode.English)
        repository.setAudioFocusMode(AudioFocusMode.Duck)
        repository.setPauseOnDisconnect(false)
        repository.setGaplessPlaybackEnabled(true)
        repository.setRetryPlaybackOnFailure(false)
        repository.setResumePlaybackAfterNetworkRecovery(false)
        repository.setKeepScreenOnInPlayer(true)
        repository.setPlayerInteractionSettings(
            PlayerInteractionSettings.Default.copy(showAudioTechnicalInfo = true)
        )
        repository.setLyricTextAlignment(LyricTextAlignment.Right)
        repository.setLyricPrimaryFontScalePercent(125)
        repository.setLyricPrimaryFontSizeSp(42)
        repository.setLyricSecondaryFontScalePercent(135)
        repository.setLyricSecondaryFontSizeSp(24)
        repository.setLyricTranslationVisible(false)
        repository.setLyricWordLiftEnabled(false)
        repository.setLyricBlurEffectEnabled(false)
        repository.setLyricPerspectiveEffectEnabled(true)
        repository.setLyricPerspectiveAngleDegrees(35)
        repository.setLyricTapToSeekEnabled(false)
        repository.setAutoScanMode(AutoScanMode.OnStartup)
        repository.setScanSubdirectories(false)
        repository.setWebDavMetadataScanMode(MetadataScanMode.Full)
        repository.setMinimumAudioDurationMs(47_000L)
        repository.setMissingFilePolicy(MissingFilePolicy.RemoveOnScan)
        repository.setAllowMeteredNetworkUsage(true)
        repository.setNetworkRetryCount(4)
        repository.setConnectionTimeoutSeconds(45)
        repository.setAudioPreloadBytes(8L * 1024L * 1024L)
        repository.setListenAndCacheEnabled(false)
        repository.setAudioCacheLimitBytes(512L * 1024L * 1024L)
        repository.setImageCacheLimitBytes(128L * 1024L * 1024L)

        val settings = DataStoreSettingsRepository(dataStore).settingsValue()
        assertEquals(AppThemeMode.Light, settings.themeMode)
        assertFalse(settings.artworkThemeEnabled)
        assertEquals(0xFF3D9AFFL, settings.manualThemeSeedArgb)
        assertEquals(listOf(0xFF3D9AFFL, 0xFFFFD93DL), settings.customThemeSeedArgbValues)
        assertEquals(AppLanguageMode.English, settings.languageMode)
        assertEquals(AudioFocusMode.Duck, settings.audioFocusMode)
        assertFalse(settings.pauseOnDisconnect)
        assertTrue(settings.gaplessPlaybackEnabled)
        assertFalse(settings.retryPlaybackOnFailure)
        assertFalse(settings.resumePlaybackAfterNetworkRecovery)
        assertTrue(settings.keepScreenOnInPlayer)
        assertTrue(settings.playerInteraction.showAudioTechnicalInfo)
        assertEquals(LyricTextAlignment.Right, settings.lyrics.textAlignment)
        assertEquals(125, settings.lyrics.primaryFontScalePercent)
        assertEquals(42, settings.lyrics.primaryFontSizeSp)
        assertEquals(135, settings.lyrics.secondaryFontScalePercent)
        assertEquals(24, settings.lyrics.secondaryFontSizeSp)
        assertFalse(settings.lyrics.showTranslation)
        assertFalse(settings.lyrics.wordLiftEnabled)
        assertFalse(settings.lyrics.blurEffectEnabled)
        assertTrue(settings.lyrics.perspectiveEffectEnabled)
        assertEquals(35, settings.lyrics.perspectiveAngleDegrees)
        assertFalse(settings.lyrics.tapToSeekEnabled)
        assertEquals(AutoScanMode.OnStartup, settings.autoScanMode)
        assertFalse(settings.scanSubdirectories)
        assertEquals(MetadataScanMode.Full, settings.webDavMetadataScanMode)
        assertEquals(47_000L, settings.minimumAudioDurationMs)
        assertEquals(MissingFilePolicy.RemoveOnScan, settings.missingFilePolicy)
        assertTrue(settings.allowMeteredNetworkUsage)
        assertEquals(4, settings.networkRetryCount)
        assertEquals(45, settings.connectionTimeoutSeconds)
        assertEquals(8L * 1024L * 1024L, settings.audioPreloadBytes)
        assertFalse(settings.listenAndCacheEnabled)
        assertEquals(512L * 1024L * 1024L, settings.audioCacheLimitBytes)
        assertEquals(128L * 1024L * 1024L, settings.imageCacheLimitBytes)
    }

    @Test
    fun migratesLegacyPlaybackAndScanValues() = withRepository { dataStore, repository ->
        dataStore.edit { preferences ->
            preferences[ALLOW_MIXED_PLAYBACK_KEY] = true
            preferences[IGNORE_SHORT_AUDIO_KEY] = false
            preferences[LOCAL_SCAN_SUBDIRECTORIES_KEY] = false
        }

        val migrated = repository.settingsValue()
        assertEquals(AudioFocusMode.Mix, migrated.audioFocusMode)
        assertEquals(0L, migrated.minimumAudioDurationMs)
        assertFalse(migrated.scanSubdirectories)

        dataStore.edit { preferences ->
            preferences[ALLOW_MIXED_PLAYBACK_KEY] = false
            preferences[IGNORE_SHORT_AUDIO_KEY] = true
        }
        val second = repository.settingsValue()
        assertEquals(AudioFocusMode.Pause, second.audioFocusMode)
        assertEquals(30_000L, second.minimumAudioDurationMs)
    }

    @Test
    fun migratesLegacyDynamicColorAndNormalizesThemeSeeds() = withRepository { dataStore, repository ->
        dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR_ENABLED_KEY] = false
            preferences[MANUAL_THEME_SEED_ARGB_KEY] = 0x003D9AFFL
            preferences[CUSTOM_THEME_SEED_ARGB_VALUES_KEY] =
                "FF3D9AFF,003D9AFF,FFFFD93D,invalid"
        }

        val migrated = repository.settingsValue()

        assertFalse(migrated.artworkThemeEnabled)
        assertEquals(0xFF3D9AFFL, migrated.manualThemeSeedArgb)
        assertEquals(
            listOf(0xFF3D9AFFL, 0xFFFFD93DL),
            migrated.customThemeSeedArgbValues,
        )
    }

    @Test
    fun invalidValuesFallBackOrClampAndResetClearsSettings() = withRepository { dataStore, repository ->
        dataStore.edit { preferences ->
            preferences[AUDIO_FOCUS_MODE_KEY] = "invalid"
            preferences[AUTO_SCAN_MODE_KEY] = "invalid"
            preferences[WEB_DAV_METADATA_SCAN_MODE_KEY] = "invalid"
            preferences[NETWORK_RETRY_COUNT_KEY] = 99
            preferences[CONNECTION_TIMEOUT_SECONDS_KEY] = -1
            preferences[AUDIO_CACHE_LIMIT_BYTES_KEY] = Long.MAX_VALUE
            preferences[IMAGE_CACHE_LIMIT_BYTES_KEY] = Long.MAX_VALUE
            preferences[LYRIC_TEXT_ALIGNMENT_KEY] = "invalid"
            preferences[LYRIC_PRIMARY_FONT_SCALE_PERCENT_KEY] = Int.MAX_VALUE
            preferences[LYRIC_PRIMARY_FONT_SIZE_SP_KEY] = Int.MAX_VALUE
            preferences[LYRIC_SECONDARY_FONT_SCALE_PERCENT_KEY] = Int.MIN_VALUE
            preferences[LYRIC_SECONDARY_FONT_SIZE_SP_KEY] = Int.MIN_VALUE
            preferences[LYRIC_PERSPECTIVE_ANGLE_DEGREES_KEY] = Int.MAX_VALUE
        }

        val normalized = repository.settingsValue()
        assertEquals(AudioFocusMode.Pause, normalized.audioFocusMode)
        assertEquals(AutoScanMode.Off, normalized.autoScanMode)
        assertEquals(MetadataScanMode.Standard, normalized.webDavMetadataScanMode)
        assertEquals(5, normalized.networkRetryCount)
        assertEquals(5, normalized.connectionTimeoutSeconds)
        assertEquals(MAX_AUDIO_CACHE_LIMIT_BYTES, normalized.audioCacheLimitBytes)
        assertEquals(MAX_IMAGE_CACHE_LIMIT_BYTES, normalized.imageCacheLimitBytes)
        assertEquals(LyricTextAlignment.Left, normalized.lyrics.textAlignment)
        assertEquals(175, normalized.lyrics.primaryFontScalePercent)
        assertEquals(54, normalized.lyrics.primaryFontSizeSp)
        assertEquals(75, normalized.lyrics.secondaryFontScalePercent)
        assertEquals(12, normalized.lyrics.secondaryFontSizeSp)
        assertEquals(45, normalized.lyrics.perspectiveAngleDegrees)

        repository.setThemeMode(AppThemeMode.Light)
        repository.setPlayerInteractionSettings(
            PlayerInteractionSettings.Default.copy(showAudioTechnicalInfo = true)
        )
        repository.resetToDefaults()
        assertEquals(AppSettings.Default, repository.settingsValue())
    }

    private fun withRepository(
        block: suspend (
            androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
            DataStoreSettingsRepository,
        ) -> Unit,
    ) = runBlocking {
        val file = File.createTempFile("musicapp-settings-", ".preferences_pb").apply { delete() }
        try {
            val dataStore = createAppDataStore { file.absolutePath.toPath() }
            block(dataStore, DataStoreSettingsRepository(dataStore, applyLanguageMode = {}))
        } finally {
            file.delete()
        }
    }

    private suspend fun DataStoreSettingsRepository.settingsValue(): AppSettings {
        return withTimeout(5_000) { settings.first() }
    }
}
