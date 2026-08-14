package io.github.julystar.musicapp.core.domain.repository

import io.github.julystar.musicapp.core.domain.model.AppLanguageMode
import io.github.julystar.musicapp.core.domain.model.AppSettings
import io.github.julystar.musicapp.core.domain.model.AppThemeMode
import io.github.julystar.musicapp.core.domain.model.AudioEffectSettings
import io.github.julystar.musicapp.core.domain.model.AudioFocusMode
import io.github.julystar.musicapp.core.domain.model.AutoScanMode
import io.github.julystar.musicapp.core.domain.model.DiagnosticsExportResult
import io.github.julystar.musicapp.core.domain.model.DiagnosticsReport
import io.github.julystar.musicapp.core.domain.model.DownloadFinalizationSettings
import io.github.julystar.musicapp.core.domain.model.LibraryRebuildState
import io.github.julystar.musicapp.core.domain.model.LyricFontSettings
import io.github.julystar.musicapp.core.domain.model.LyricOutputSettings
import io.github.julystar.musicapp.core.domain.model.LyricSourceKind
import io.github.julystar.musicapp.core.domain.model.LyricSourceMode
import io.github.julystar.musicapp.core.domain.model.LyricTextAlignment
import io.github.julystar.musicapp.core.domain.model.MetadataParsingSettings
import io.github.julystar.musicapp.core.domain.model.MissingFilePolicy
import io.github.julystar.musicapp.core.domain.model.MetadataScanMode
import io.github.julystar.musicapp.core.domain.model.NetworkStatus
import io.github.julystar.musicapp.core.domain.model.PlaybackAdvancedSettings
import io.github.julystar.musicapp.core.domain.model.PlayerInteractionSettings
import io.github.julystar.musicapp.core.domain.model.SettingsBackupSettings
import io.github.julystar.musicapp.core.domain.model.SettingsBackupResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(mode: AppThemeMode)
    suspend fun setArtworkThemeEnabled(enabled: Boolean)
    suspend fun setManualThemeSeedArgb(argb: Long)
    suspend fun setCustomThemeSeedArgbValues(argbValues: List<Long>)
    suspend fun setLanguageMode(mode: AppLanguageMode)
    suspend fun setAudioFocusMode(mode: AudioFocusMode)
    suspend fun setPauseOnDisconnect(enabled: Boolean)
    suspend fun setGaplessPlaybackEnabled(enabled: Boolean)
    suspend fun setRetryPlaybackOnFailure(enabled: Boolean)
    suspend fun setResumePlaybackAfterNetworkRecovery(enabled: Boolean)
    suspend fun setKeepScreenOnInPlayer(enabled: Boolean)
    suspend fun setLyricTextAlignment(alignment: LyricTextAlignment)
    suspend fun setLyricPrimaryFontScalePercent(value: Int)
    suspend fun setLyricPrimaryFontSizeSp(value: Int)
    suspend fun setLyricSecondaryFontScalePercent(value: Int)
    suspend fun setLyricSecondaryFontSizeSp(value: Int)
    suspend fun setLyricTranslationVisible(visible: Boolean)
    suspend fun setLyricWordLiftEnabled(enabled: Boolean)
    suspend fun setLyricBlurEffectEnabled(enabled: Boolean)
    suspend fun setLyricPerspectiveEffectEnabled(enabled: Boolean)
    suspend fun setLyricPerspectiveAngleDegrees(value: Int)
    suspend fun setLyricTapToSeekEnabled(enabled: Boolean)
    suspend fun setLyricSourceMode(mode: LyricSourceMode) = Unit
    suspend fun setLyricSourcePriority(priority: List<LyricSourceKind>) = Unit
    suspend fun setIgnoreLyricHeaderTags(enabled: Boolean) = Unit
    suspend fun setLyricFontSettings(settings: LyricFontSettings) = Unit
    suspend fun setPlaybackAdvancedSettings(settings: PlaybackAdvancedSettings) = Unit
    suspend fun setPlayerInteractionSettings(settings: PlayerInteractionSettings) = Unit
    suspend fun setMetadataParsingSettings(settings: MetadataParsingSettings) = Unit
    suspend fun setDownloadFinalizationSettings(settings: DownloadFinalizationSettings) = Unit
    suspend fun setAudioEffectSettings(settings: AudioEffectSettings) = Unit
    suspend fun setLyricOutputSettings(settings: LyricOutputSettings) = Unit
    suspend fun setBackupSettings(settings: SettingsBackupSettings) = Unit
    suspend fun replaceSettings(settings: AppSettings) = Unit
    suspend fun setAutoScanMode(mode: AutoScanMode)
    suspend fun setScanSubdirectories(enabled: Boolean)
    suspend fun setWebDavMetadataScanMode(mode: MetadataScanMode)
    suspend fun setMinimumAudioDurationMs(value: Long)
    suspend fun setMissingFilePolicy(policy: MissingFilePolicy)
    suspend fun setAllowMeteredNetworkUsage(enabled: Boolean)
    suspend fun setNetworkRetryCount(value: Int)
    suspend fun setConnectionTimeoutSeconds(value: Int)
    suspend fun setAudioPreloadBytes(bytes: Long)
    suspend fun setListenAndCacheEnabled(enabled: Boolean)
    suspend fun setAudioCacheLimitBytes(bytes: Long)
    suspend fun setImageCacheLimitBytes(bytes: Long)
    suspend fun resetToDefaults()
}

interface SettingsMigration {
    suspend fun migrate()
}

interface NetworkStatusProvider {
    val status: StateFlow<NetworkStatus>
}

interface DiagnosticsService {
    suspend fun collectDiagnostics(): DiagnosticsReport
    suspend fun exportDiagnostics(): DiagnosticsExportResult
}

interface LibraryMaintenanceService {
    val rebuildState: StateFlow<LibraryRebuildState>
    suspend fun rebuildLibrary()
}

interface AppDataClearService {
    suspend fun clearAllData()
}

interface SettingsBackupService {
    suspend fun createBackup(): SettingsBackupResult
    suspend fun restoreLatestBackup(): SettingsBackupResult
}
