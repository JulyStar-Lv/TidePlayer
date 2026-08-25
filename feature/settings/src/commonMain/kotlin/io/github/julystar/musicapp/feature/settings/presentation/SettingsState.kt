package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.runtime.Immutable
import io.github.julystar.musicapp.core.domain.model.AppLanguageMode
import io.github.julystar.musicapp.core.domain.model.AppSettings
import io.github.julystar.musicapp.core.domain.model.AppThemeMode
import io.github.julystar.musicapp.core.domain.model.AudioEffectSettings
import io.github.julystar.musicapp.core.domain.model.AudioFocusMode
import io.github.julystar.musicapp.core.domain.model.AudioDspMeterSnapshot
import io.github.julystar.musicapp.core.domain.model.AudioDspPerformanceSnapshot
import io.github.julystar.musicapp.core.domain.model.AudioDspRuntimeStatus
import io.github.julystar.musicapp.core.domain.model.AutoScanMode
import io.github.julystar.musicapp.core.domain.model.DownloadFinalizationSettings
import io.github.julystar.musicapp.core.domain.model.LibraryRebuildState
import io.github.julystar.musicapp.core.domain.model.LocalMusicDirectory
import io.github.julystar.musicapp.core.domain.model.LyricFontSettings
import io.github.julystar.musicapp.core.domain.model.LyricOutputSettings
import io.github.julystar.musicapp.core.domain.model.LyricSourceKind
import io.github.julystar.musicapp.core.domain.model.LyricSourceMode
import io.github.julystar.musicapp.core.domain.model.LyricTextAlignment
import io.github.julystar.musicapp.core.domain.model.MetadataParsingSettings
import io.github.julystar.musicapp.core.domain.model.MissingFilePolicy
import io.github.julystar.musicapp.core.domain.model.MetadataScanMode
import io.github.julystar.musicapp.core.domain.model.SettingsCapabilities
import io.github.julystar.musicapp.core.domain.model.SettingsBackupSettings
import io.github.julystar.musicapp.core.domain.model.PlaybackAdvancedSettings
import io.github.julystar.musicapp.core.domain.model.PlayerInteractionSettings
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.SourceConnectionTestStatus
import io.github.julystar.musicapp.core.domain.model.StorageUsage
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncFailure
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncTask
import io.github.julystar.musicapp.core.domain.repository.AudioDspFrequencyResponse

@Immutable
data class SettingsUiState(
    val settings: AppSettings = AppSettings.Default,
    val capabilities: SettingsCapabilities = SettingsCapabilities(),
    val storageUsage: StorageUsage = StorageUsage.Unknown,
    val storageRefreshing: Boolean = false,
    val pendingConfirmation: SettingsConfirmation? = null,
    val customCacheLimitDialog: CacheLimitType? = null,
    val customCacheLimitInputMb: String = "",
    val localDirectories: List<LocalMusicDirectory> = emptyList(),
    val sourceAccounts: List<SourceAccountSettingsItem> = emptyList(),
    val scanTasks: List<LibrarySyncTask> = emptyList(),
    val sourceOperationInProgress: Boolean = false,
    val maintenanceOperationInProgress: Boolean = false,
    val rebuildState: LibraryRebuildState = LibraryRebuildState(),
    val webDavDialog: WebDavAccountDialogState? = null,
    val webDavConnectionTestStatus: SourceConnectionTestStatus = SourceConnectionTestStatus.None,
    val webDavConnectionTestMessage: String? = null,
    val smbDialog: SmbAccountDialogState? = null,
    val smbConnectionTestStatus: SourceConnectionTestStatus = SourceConnectionTestStatus.None,
    val smbConnectionTestMessage: String? = null,
    val failureDialogTaskId: String? = null,
    val failureDetails: List<LibrarySyncFailure> = emptyList(),
    val audioDspFrequencyResponse: AudioDspFrequencyResponse = AudioDspFrequencyResponse.Empty,
    val audioDspRuntimeStatus: AudioDspRuntimeStatus = AudioDspRuntimeStatus(),
    val audioDspMeter: AudioDspMeterSnapshot = AudioDspMeterSnapshot(),
    val audioDspPerformance: AudioDspPerformanceSnapshot = AudioDspPerformanceSnapshot(),
) {
    val enabledSourceCount: Int
        get() = sourceAccounts.count(SourceAccountSettingsItem::enabled)

    val trackCount: Long
        get() = sourceAccounts.sumOf(SourceAccountSettingsItem::trackCount)
}

enum class SettingsPage {
    Home,
    Appearance,
    Playback,
    Equalizer,
    AudioEffects,
    Lyrics,
    Source,
    Plugins,
    NetworkCache,
    Storage,
    Diagnostics,
    About,
    Licenses,
}

enum class CacheLimitType {
    Audio,
    Image,
}

sealed interface SettingsConfirmation {
    data object ClearAudio : SettingsConfirmation
    data object ClearImage : SettingsConfirmation
    data object ClearAllCaches : SettingsConfirmation
    data object ClearAllData : SettingsConfirmation
    data object ResetDefaults : SettingsConfirmation
    data object RebuildLibrary : SettingsConfirmation
    data class RemoveLocalDirectory(val id: String, val title: String) : SettingsConfirmation
    data class DeleteWebDavAccount(
        val accountId: SourceAccountId,
        val title: String,
    ) : SettingsConfirmation
    data class DeleteSmbAccount(
        val accountId: SourceAccountId,
        val title: String,
    ) : SettingsConfirmation
}

sealed interface SettingsAction {
    data class SetThemeMode(val mode: AppThemeMode) : SettingsAction
    data class SetArtworkThemeEnabled(val enabled: Boolean) : SettingsAction
    data class SetManualThemeSeedArgb(val argb: Long) : SettingsAction
    data class SetCustomThemeSeedArgbValues(val argbValues: List<Long>) : SettingsAction
    data class SetLanguageMode(val mode: AppLanguageMode) : SettingsAction
    data class SetAudioFocusMode(val mode: AudioFocusMode) : SettingsAction
    data class SetPauseOnDisconnect(val enabled: Boolean) : SettingsAction
    data class SetGaplessPlaybackEnabled(val enabled: Boolean) : SettingsAction
    data class SetRetryPlaybackOnFailure(val enabled: Boolean) : SettingsAction
    data class SetResumePlaybackAfterNetworkRecovery(val enabled: Boolean) : SettingsAction
    data class SetKeepScreenOnInPlayer(val enabled: Boolean) : SettingsAction
    data class SetLyricTextAlignment(val alignment: LyricTextAlignment) : SettingsAction
    data class SetLyricPrimaryFontScalePercent(val value: Int) : SettingsAction
    data class SetLyricPrimaryFontSizeSp(val value: Int) : SettingsAction
    data class SetLyricSecondaryFontScalePercent(val value: Int) : SettingsAction
    data class SetLyricSecondaryFontSizeSp(val value: Int) : SettingsAction
    data class SetLyricTranslationVisible(val visible: Boolean) : SettingsAction
    data class SetLyricWordLiftEnabled(val enabled: Boolean) : SettingsAction
    data class SetLyricBlurEffectEnabled(val enabled: Boolean) : SettingsAction
    data class SetLyricPerspectiveEffectEnabled(val enabled: Boolean) : SettingsAction
    data class SetLyricPerspectiveAngleDegrees(val value: Int) : SettingsAction
    data class SetLyricTapToSeekEnabled(val enabled: Boolean) : SettingsAction
    data class SetLyricSourceMode(val mode: LyricSourceMode) : SettingsAction
    data class SetLyricSourcePriority(val priority: List<LyricSourceKind>) : SettingsAction
    data class SetIgnoreLyricHeaderTags(val enabled: Boolean) : SettingsAction
    data class SetLyricFontSettings(val settings: LyricFontSettings) : SettingsAction
    data class SetPlaybackAdvancedSettings(val settings: PlaybackAdvancedSettings) : SettingsAction
    data class SetPlayerInteractionSettings(val settings: PlayerInteractionSettings) : SettingsAction
    data class SetMetadataParsingSettings(val settings: MetadataParsingSettings) : SettingsAction
    data class SetDownloadFinalizationSettings(
        val settings: DownloadFinalizationSettings,
    ) : SettingsAction
    data class SetAudioEffectSettings(val settings: AudioEffectSettings) : SettingsAction
    data class SetLyricOutputSettings(val settings: LyricOutputSettings) : SettingsAction
    data class SetBackupSettings(val settings: SettingsBackupSettings) : SettingsAction
    data object CreateSettingsBackup : SettingsAction
    data object RestoreLatestSettingsBackup : SettingsAction
    data class SetAutoScanMode(val mode: AutoScanMode) : SettingsAction
    data class SetScanSubdirectories(val enabled: Boolean) : SettingsAction
    data class SetWebDavMetadataScanMode(val mode: MetadataScanMode) : SettingsAction
    data class SetMinimumAudioDurationMs(val value: Long) : SettingsAction
    data class SetMissingFilePolicy(val policy: MissingFilePolicy) : SettingsAction
    data class SetAllowMeteredNetworkUsage(val enabled: Boolean) : SettingsAction
    data class SetNetworkRetryCount(val value: Int) : SettingsAction
    data class SetConnectionTimeoutSeconds(val value: Int) : SettingsAction
    data class SetAudioPreloadBytes(val bytes: Long) : SettingsAction
    data class SetListenAndCacheEnabled(val enabled: Boolean) : SettingsAction
    data class SetAccountEnabled(val accountId: SourceAccountId, val enabled: Boolean) : SettingsAction
    data object RequestAddLocalDirectory : SettingsAction
    data class AddLocalDirectory(val path: String) : SettingsAction
    data class HandleLocalDirectoryPickerResult(
        val result: LocalDirectoryPickerResult,
    ) : SettingsAction
    data object ReportUnsupportedLocalDirectory : SettingsAction
    data class RequestRemoveLocalDirectory(val id: String, val title: String) : SettingsAction
    data object ScanAllSources : SettingsAction
    data object CancelActiveScans : SettingsAction
    data object RefreshMissingArtwork : SettingsAction
    data object RefreshMissingLyrics : SettingsAction
    data object ScanLocalMusic : SettingsAction
    data object OpenAddWebDavDialog : SettingsAction
    data class OpenEditWebDavDialog(val accountId: SourceAccountId) : SettingsAction
    data object DismissWebDavDialog : SettingsAction
    data class TestWebDavConnection(
        val password: String,
        val draft: WebDavAccountDialogState? = null,
    ) : SettingsAction {
        override fun toString(): String =
            "TestWebDavConnection(password=<redacted>, draft=$draft)"
    }
    data class SaveWebDavAccount(
        val password: String,
        val draft: WebDavAccountDialogState? = null,
    ) : SettingsAction {
        override fun toString(): String =
            "SaveWebDavAccount(password=<redacted>, draft=$draft)"
    }
    data class RequestDeleteWebDavAccount(
        val accountId: SourceAccountId,
        val title: String,
    ) : SettingsAction
    data object OpenAddSmbDialog : SettingsAction
    data class OpenEditSmbDialog(val accountId: SourceAccountId) : SettingsAction
    data object DismissSmbDialog : SettingsAction
    data class TestSmbConnection(
        val password: String,
        val draft: SmbAccountDialogState? = null,
    ) : SettingsAction {
        override fun toString(): String =
            "TestSmbConnection(password=<redacted>, draft=$draft)"
    }
    data class SaveSmbAccount(
        val password: String,
        val draft: SmbAccountDialogState? = null,
    ) : SettingsAction {
        override fun toString(): String =
            "SaveSmbAccount(password=<redacted>, draft=$draft)"
    }
    data class RequestDeleteSmbAccount(
        val accountId: SourceAccountId,
        val title: String,
    ) : SettingsAction
    data class ConfigureSourcePath(val accountId: SourceAccountId) : SettingsAction
    data class ScanSourceAccount(val accountId: SourceAccountId) : SettingsAction
    data class CancelScan(val scanId: String) : SettingsAction
    data class OpenScanFailures(val scanId: String) : SettingsAction
    data object DismissScanFailures : SettingsAction
    data class SetAudioCacheLimitBytes(val bytes: Long) : SettingsAction
    data class SetImageCacheLimitBytes(val bytes: Long) : SettingsAction
    data class SetCustomCacheLimitInput(val value: String) : SettingsAction
    data class OpenCustomCacheLimitDialog(val type: CacheLimitType) : SettingsAction
    data object DismissCustomCacheLimitDialog : SettingsAction
    data object ApplyCustomCacheLimit : SettingsAction
    data object RefreshStorageUsage : SettingsAction
    data object RequestClearAudio : SettingsAction
    data object RequestClearImage : SettingsAction
    data object RequestClearAllCaches : SettingsAction
    data object RequestClearAllData : SettingsAction
    data object RequestResetDefaults : SettingsAction
    data object RequestRebuildLibrary : SettingsAction
    data object ExportDiagnostics : SettingsAction
    data object DismissConfirmation : SettingsAction
    data object ConfirmPendingAction : SettingsAction
}

sealed interface SettingsEvent {
    data object OpenLibraryFolderPicker : SettingsEvent
    data object OpenSourcePathPicker : SettingsEvent
}

@Immutable
data class SourceAccountSettingsItem(
    val accountId: SourceAccountId,
    val title: String,
    val subtitle: String,
    val rootPath: String?,
    val enabled: Boolean,
    val trackCount: Long,
    val lastScanAtEpochMs: Long?,
    val lastScanStatus: String?,
    val isLocal: Boolean,
    val isWebDav: Boolean,
    val isSmb: Boolean,
    val isOpenList: Boolean,
    val isRemoteServer: Boolean,
    val sourceLabel: String,
)

@Immutable
data class WebDavAccountDialogState(
    val accountId: SourceAccountId? = null,
    val name: String = "",
    val serverUrl: String = "",
    val username: String = "",
    val rootPath: String = "/",
) {
    val isEditing: Boolean
        get() = accountId != null
}

@Immutable
data class SmbAccountDialogState(
    val accountId: SourceAccountId? = null,
    val name: String = "",
    val host: String = "",
    val port: String = "445",
    val share: String = "",
    val rootPath: String = "",
    val domain: String = "",
    val username: String = "",
    val guestAccess: Boolean = false,
    val requireSigning: Boolean = false,
    val requireEncryption: Boolean = false,
) {
    val isEditing: Boolean
        get() = accountId != null
}
