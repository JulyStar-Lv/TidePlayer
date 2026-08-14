package io.github.julystar.musicapp.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.julystar.musicapp.core.domain.model.AppSettings
import io.github.julystar.musicapp.core.domain.model.AudioDspMeterSnapshot
import io.github.julystar.musicapp.core.domain.model.AudioDspPerformanceSnapshot
import io.github.julystar.musicapp.core.domain.model.AudioDspRuntimeStatus
import io.github.julystar.musicapp.core.domain.model.DiagnosticsExportResult
import io.github.julystar.musicapp.core.domain.model.LocalMusicDirectory
import io.github.julystar.musicapp.core.domain.model.MAX_AUDIO_CACHE_LIMIT_BYTES
import io.github.julystar.musicapp.core.domain.model.MAX_IMAGE_CACHE_LIMIT_BYTES
import io.github.julystar.musicapp.core.domain.model.MetadataScanMode
import io.github.julystar.musicapp.core.domain.model.metadataScanModeFor
import io.github.julystar.musicapp.core.domain.model.MetadataRefreshTarget
import io.github.julystar.musicapp.core.domain.model.SettingsCapabilities
import io.github.julystar.musicapp.core.domain.model.SettingsBackupResult
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.SourceConnectionTestStatus
import io.github.julystar.musicapp.core.domain.model.SourceEditorDraft
import io.github.julystar.musicapp.core.domain.model.SourceEditorType
import io.github.julystar.musicapp.core.domain.model.StorageAccountInfo
import io.github.julystar.musicapp.core.domain.model.StorageUsage
import io.github.julystar.musicapp.core.domain.model.normalizeAudioCacheLimitBytes
import io.github.julystar.musicapp.core.domain.model.normalizeImageCacheLimitBytes
import io.github.julystar.musicapp.core.domain.model.storageSourceAccountId
import io.github.julystar.musicapp.core.domain.model.toStorageRouteIdOrNull
import io.github.julystar.musicapp.core.domain.repository.DiagnosticsService
import io.github.julystar.musicapp.core.domain.repository.AppDataClearService
import io.github.julystar.musicapp.core.domain.repository.AudioDspAnalysisRepository
import io.github.julystar.musicapp.core.domain.repository.AudioDspRuntimeRepository
import io.github.julystar.musicapp.core.domain.repository.AudioDspFrequencyResponse
import io.github.julystar.musicapp.core.domain.repository.LibraryMaintenanceService
import io.github.julystar.musicapp.core.domain.repository.PermissionChecker
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.core.domain.repository.SettingsBackupService
import io.github.julystar.musicapp.core.domain.repository.SourceSettingsRepository
import io.github.julystar.musicapp.core.domain.repository.StorageRepository
import io.github.julystar.musicapp.core.domain.repository.StorageUsageRepository
import io.github.julystar.musicapp.core.domain.repository.ToastRepository
import io.github.julystar.musicapp.core.domain.repository.emitText
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncAlreadyActiveException
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncController
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncFailure
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncRequest
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncScanRules
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncTask
import io.github.julystar.musicapp.service.librarysync.domain.MetadataRefreshController
import io.github.julystar.musicapp.service.librarysync.domain.MetadataRefreshRequest
import io.github.julystar.musicapp.service.librarysync.domain.MetadataRefreshScope
import io.github.julystar.musicapp.service.playback.domain.PlaybackController
import io.github.julystar.musicapp.source.api.BuiltInSourceIds
import io.github.julystar.musicapp.source.api.ImportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import musicapp.feature.settings.generated.resources.*

class SettingsVM(
    private val settingsRepository: SettingsRepository,
    private val sourceSettingsRepository: SourceSettingsRepository,
    private val storageRepository: StorageRepository,
    private val storageUsageRepository: StorageUsageRepository,
    private val diagnosticsService: DiagnosticsService,
    private val libraryMaintenanceService: LibraryMaintenanceService,
    private val appDataClearService: AppDataClearService,
    private val toastRepository: ToastRepository,
    private val permissionChecker: PermissionChecker,
    private val librarySyncController: LibrarySyncController,
    private val playbackController: PlaybackController,
    private val metadataRefreshController: MetadataRefreshController,
    private val audioDspAnalysisRepository: AudioDspAnalysisRepository,
    private val importRepository: ImportRepository,
    private val capabilities: SettingsCapabilities,
    private val textProvider: SettingsTextProvider,
    private val backupService: SettingsBackupService? = null,
    audioDspRuntimeRepository: AudioDspRuntimeRepository? = null,
) : ViewModel() {
    private val storageUsage = MutableStateFlow(StorageUsage.Unknown)
    private val storageRefreshing = MutableStateFlow(false)
    private val pendingConfirmation = MutableStateFlow<SettingsConfirmation?>(null)
    private val customCacheLimitDialog = MutableStateFlow<CacheLimitType?>(null)
    private val customCacheLimitInputMb = MutableStateFlow("")
    private val sourceOperationInProgress = MutableStateFlow(false)
    private val maintenanceOperationInProgress = MutableStateFlow(false)
    private val webDavDialog = MutableStateFlow<WebDavAccountDialogState?>(null)
    private val webDavConnectionTestStatus = MutableStateFlow(SourceConnectionTestStatus.None)
    private val webDavConnectionTestMessage = MutableStateFlow<String?>(null)
    private val smbDialog = MutableStateFlow<SmbAccountDialogState?>(null)
    private val smbConnectionTestStatus = MutableStateFlow(SourceConnectionTestStatus.None)
    private val smbConnectionTestMessage = MutableStateFlow<String?>(null)
    private val failureDialogTaskId = MutableStateFlow<String?>(null)
    private val pendingLocalDirectoryPath = MutableStateFlow<String?>(null)
    private val events = Channel<SettingsEvent>(Channel.BUFFERED)
    private val audioDspRuntimeStatus =
        audioDspRuntimeRepository?.status ?: MutableStateFlow(AudioDspRuntimeStatus())
    private val audioDspMeter =
        audioDspRuntimeRepository?.meter ?: MutableStateFlow(AudioDspMeterSnapshot())
    private val audioDspPerformance =
        audioDspRuntimeRepository?.performance ?: MutableStateFlow(AudioDspPerformanceSnapshot())

    private val audioDspFrequencyResponse = settingsRepository.settings
        .map { settings ->
            if (!capabilities.audioDsp.anySoftwareDsp) {
                AudioDspFrequencyResponse.Empty
            } else {
                runCatching {
                    audioDspAnalysisRepository.calculateFrequencyResponse(settings.audioEffects)
                }.getOrDefault(AudioDspFrequencyResponse.Empty)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AudioDspFrequencyResponse.Empty,
        )

    val eventFlow = events.receiveAsFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val failureDetails = failureDialogTaskId
        .flatMapLatest { taskId ->
            if (taskId == null) flowOf(emptyList())
            else librarySyncController.observeFailures(taskId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    @Suppress("UNCHECKED_CAST")
    val state: StateFlow<SettingsUiState> = combine(
        settingsRepository.settings,
        storageUsage,
        storageRefreshing,
        pendingConfirmation,
        customCacheLimitDialog,
        customCacheLimitInputMb,
        sourceSettingsRepository.localDirectories,
        storageRepository.storageAccounts,
        librarySyncController.recentTasks,
        sourceOperationInProgress,
        maintenanceOperationInProgress,
        libraryMaintenanceService.rebuildState,
        webDavDialog,
        webDavConnectionTestStatus,
        webDavConnectionTestMessage,
        smbDialog,
        smbConnectionTestStatus,
        smbConnectionTestMessage,
        failureDialogTaskId,
        failureDetails,
        audioDspFrequencyResponse,
        audioDspRuntimeStatus,
        audioDspMeter,
        audioDspPerformance,
    ) { values ->
        val settings = values[0] as AppSettings
        val localDirectories = values[6] as List<LocalMusicDirectory>
        val storageAccounts = values[7] as List<StorageAccountInfo>
        val sourceAccounts = storageAccounts
            .filter { account ->
                (account.sourceId == BuiltInSourceIds.Local && localDirectories.isNotEmpty()) ||
                    account.sourceId == BuiltInSourceIds.WebDav ||
                    account.sourceId == BuiltInSourceIds.Smb
            }
            .map(StorageAccountInfo::toSettingsItem)
        val scanTasks = values[8] as List<LibrarySyncTask>

        SettingsUiState(
            settings = settings,
            capabilities = capabilities,
            storageUsage = values[1] as StorageUsage,
            storageRefreshing = values[2] as Boolean,
            pendingConfirmation = values[3] as SettingsConfirmation?,
            customCacheLimitDialog = values[4] as CacheLimitType?,
            customCacheLimitInputMb = values[5] as String,
            localDirectories = localDirectories,
            sourceAccounts = sourceAccounts,
            scanTasks = scanTasks.filterRelevantToSettings(localDirectories, sourceAccounts),
            sourceOperationInProgress = values[9] as Boolean,
            maintenanceOperationInProgress = values[10] as Boolean,
            rebuildState = values[11] as io.github.julystar.musicapp.core.domain.model.LibraryRebuildState,
            webDavDialog = values[12] as WebDavAccountDialogState?,
            webDavConnectionTestStatus = values[13] as SourceConnectionTestStatus,
            webDavConnectionTestMessage = values[14] as String?,
            smbDialog = values[15] as SmbAccountDialogState?,
            smbConnectionTestStatus = values[16] as SourceConnectionTestStatus,
            smbConnectionTestMessage = values[17] as String?,
            failureDialogTaskId = values[18] as String?,
            failureDetails = values[19] as List<LibrarySyncFailure>,
            audioDspFrequencyResponse = values[20] as AudioDspFrequencyResponse,
            audioDspRuntimeStatus = values[21] as AudioDspRuntimeStatus,
            audioDspMeter = values[22] as AudioDspMeterSnapshot,
            audioDspPerformance = values[23] as AudioDspPerformanceSnapshot,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(capabilities = capabilities),
    )

    init {
        refreshStorageUsage()
        viewModelScope.launch { storageRepository.reload() }
        viewModelScope.launch {
            permissionChecker.havePermission.collect { granted ->
                val path = pendingLocalDirectoryPath.value ?: return@collect
                if (granted) {
                    pendingLocalDirectoryPath.value = null
                    syncSelectedDirectory(path)
                }
            }
        }
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.SetThemeMode -> updateSetting { settingsRepository.setThemeMode(action.mode) }
            is SettingsAction.SetArtworkThemeEnabled -> updateSetting {
                settingsRepository.setArtworkThemeEnabled(action.enabled)
            }
            is SettingsAction.SetManualThemeSeedArgb -> updateSetting {
                settingsRepository.setManualThemeSeedArgb(action.argb)
            }
            is SettingsAction.SetCustomThemeSeedArgbValues -> updateSetting {
                settingsRepository.setCustomThemeSeedArgbValues(action.argbValues)
            }
            is SettingsAction.SetLanguageMode -> updateSetting {
                settingsRepository.setLanguageMode(action.mode)
                emitFeedback(Res.string.settings_feedback_language_restart)
            }
            is SettingsAction.SetAudioFocusMode -> updateSetting {
                if (capabilities.audioFocusSupported) {
                    settingsRepository.setAudioFocusMode(action.mode)
                }
            }
            is SettingsAction.SetPauseOnDisconnect -> updateSetting {
                if (capabilities.deviceDisconnectSupported || !action.enabled) {
                    settingsRepository.setPauseOnDisconnect(action.enabled)
                }
            }
            is SettingsAction.SetGaplessPlaybackEnabled -> updateSetting {
                if (capabilities.gaplessPlaybackSupported || !action.enabled) {
                    settingsRepository.setGaplessPlaybackEnabled(action.enabled)
                }
            }
            is SettingsAction.SetRetryPlaybackOnFailure -> updateSetting {
                settingsRepository.setRetryPlaybackOnFailure(action.enabled)
            }
            is SettingsAction.SetResumePlaybackAfterNetworkRecovery -> updateSetting {
                if (capabilities.networkStatusSupported || !action.enabled) {
                    settingsRepository.setResumePlaybackAfterNetworkRecovery(action.enabled)
                }
            }
            is SettingsAction.SetKeepScreenOnInPlayer -> updateSetting {
                settingsRepository.setKeepScreenOnInPlayer(action.enabled)
            }
            is SettingsAction.SetLyricTextAlignment -> updateSetting {
                settingsRepository.setLyricTextAlignment(action.alignment)
            }
            is SettingsAction.SetLyricPrimaryFontScalePercent -> updateSetting {
                settingsRepository.setLyricPrimaryFontScalePercent(action.value)
            }
            is SettingsAction.SetLyricPrimaryFontSizeSp -> updateSetting {
                settingsRepository.setLyricPrimaryFontSizeSp(action.value)
            }
            is SettingsAction.SetLyricSecondaryFontScalePercent -> updateSetting {
                settingsRepository.setLyricSecondaryFontScalePercent(action.value)
            }
            is SettingsAction.SetLyricSecondaryFontSizeSp -> updateSetting {
                settingsRepository.setLyricSecondaryFontSizeSp(action.value)
            }
            is SettingsAction.SetLyricTranslationVisible -> updateSetting {
                settingsRepository.setLyricTranslationVisible(action.visible)
            }
            is SettingsAction.SetLyricWordLiftEnabled -> updateSetting {
                settingsRepository.setLyricWordLiftEnabled(action.enabled)
            }
            is SettingsAction.SetLyricBlurEffectEnabled -> updateSetting {
                settingsRepository.setLyricBlurEffectEnabled(action.enabled)
            }
            is SettingsAction.SetLyricPerspectiveEffectEnabled -> updateSetting {
                settingsRepository.setLyricPerspectiveEffectEnabled(action.enabled)
            }
            is SettingsAction.SetLyricPerspectiveAngleDegrees -> updateSetting {
                settingsRepository.setLyricPerspectiveAngleDegrees(action.value)
            }
            is SettingsAction.SetLyricTapToSeekEnabled -> updateSetting {
                settingsRepository.setLyricTapToSeekEnabled(action.enabled)
            }
            is SettingsAction.SetLyricSourceMode -> updateSetting {
                settingsRepository.setLyricSourceMode(action.mode)
            }
            is SettingsAction.SetLyricSourcePriority -> updateSetting {
                settingsRepository.setLyricSourcePriority(action.priority)
            }
            is SettingsAction.SetIgnoreLyricHeaderTags -> updateSetting {
                settingsRepository.setIgnoreLyricHeaderTags(action.enabled)
            }
            is SettingsAction.SetLyricFontSettings -> updateSetting {
                settingsRepository.setLyricFontSettings(action.settings)
            }
            is SettingsAction.SetPlaybackAdvancedSettings -> updateSetting {
                settingsRepository.setPlaybackAdvancedSettings(action.settings)
            }
            is SettingsAction.SetPlayerInteractionSettings -> updateSetting {
                settingsRepository.setPlayerInteractionSettings(action.settings)
            }
            is SettingsAction.SetMetadataParsingSettings -> updateSetting {
                settingsRepository.setMetadataParsingSettings(action.settings)
            }
            is SettingsAction.SetDownloadFinalizationSettings -> updateSetting {
                settingsRepository.setDownloadFinalizationSettings(action.settings)
            }
            is SettingsAction.SetAudioEffectSettings -> updateSetting {
                settingsRepository.setAudioEffectSettings(action.settings)
            }
            is SettingsAction.SetLyricOutputSettings -> updateSetting {
                settingsRepository.setLyricOutputSettings(action.settings)
            }
            is SettingsAction.SetBackupSettings -> updateSetting {
                settingsRepository.setBackupSettings(action.settings)
            }
            SettingsAction.CreateSettingsBackup -> runSettingsBackup(restore = false)
            SettingsAction.RestoreLatestSettingsBackup -> runSettingsBackup(restore = true)
            is SettingsAction.SetAutoScanMode -> updateSetting {
                settingsRepository.setAutoScanMode(action.mode)
            }
            is SettingsAction.SetScanSubdirectories -> updateSetting {
                settingsRepository.setScanSubdirectories(action.enabled)
            }
            is SettingsAction.SetWebDavMetadataScanMode -> updateSetting {
                settingsRepository.setWebDavMetadataScanMode(action.mode)
            }
            is SettingsAction.SetMinimumAudioDurationMs -> updateSetting {
                settingsRepository.setMinimumAudioDurationMs(action.value)
            }
            is SettingsAction.SetMissingFilePolicy -> updateSetting {
                settingsRepository.setMissingFilePolicy(action.policy)
            }
            is SettingsAction.SetAllowMeteredNetworkUsage -> updateSetting {
                if (
                    capabilities.networkStatusSupported ||
                    capabilities.backgroundScanSupported ||
                    !action.enabled
                ) {
                    settingsRepository.setAllowMeteredNetworkUsage(action.enabled)
                }
            }
            is SettingsAction.SetNetworkRetryCount -> updateSetting {
                settingsRepository.setNetworkRetryCount(action.value)
            }
            is SettingsAction.SetConnectionTimeoutSeconds -> updateSetting {
                settingsRepository.setConnectionTimeoutSeconds(action.value)
            }
            is SettingsAction.SetAudioPreloadBytes -> updateSetting {
                if (capabilities.audioPreloadSupported || action.bytes == 0L) {
                    settingsRepository.setAudioPreloadBytes(action.bytes)
                }
            }
            is SettingsAction.SetListenAndCacheEnabled -> updateSetting {
                settingsRepository.setListenAndCacheEnabled(action.enabled)
            }
            is SettingsAction.SetAccountEnabled -> setAccountEnabled(action.accountId, action.enabled)
            SettingsAction.RequestAddLocalDirectory -> requestAddLocalDirectory()
            is SettingsAction.AddLocalDirectory -> addLocalDirectory(action.path)
            SettingsAction.ReportUnsupportedLocalDirectory -> reportUnsupportedLocalDirectory()
            is SettingsAction.RequestRemoveLocalDirectory -> {
                pendingConfirmation.value = SettingsConfirmation.RemoveLocalDirectory(
                    id = action.id,
                    title = action.title,
                )
            }
            SettingsAction.ScanAllSources -> scanAllSources()
            SettingsAction.CancelActiveScans -> cancelActiveScans()
            SettingsAction.RefreshMissingArtwork -> refreshMissingMetadata(MetadataRefreshTarget.Artwork)
            SettingsAction.RefreshMissingLyrics -> refreshMissingMetadata(MetadataRefreshTarget.Lyrics)
            SettingsAction.ScanLocalMusic -> scanLocalMusic()
            SettingsAction.OpenAddWebDavDialog -> openAddWebDavDialog()
            is SettingsAction.OpenEditWebDavDialog -> openEditWebDavDialog(action.accountId)
            SettingsAction.DismissWebDavDialog -> dismissWebDavDialog()
            is SettingsAction.TestWebDavConnection -> {
                testWebDavConnection(action.password, action.draft)
            }
            is SettingsAction.SaveWebDavAccount -> saveWebDavAccount(action.password, action.draft)
            is SettingsAction.RequestDeleteWebDavAccount -> {
                pendingConfirmation.value = SettingsConfirmation.DeleteWebDavAccount(
                    accountId = action.accountId,
                    title = action.title,
                )
            }
            SettingsAction.OpenAddSmbDialog -> openAddSmbDialog()
            is SettingsAction.OpenEditSmbDialog -> openEditSmbDialog(action.accountId)
            SettingsAction.DismissSmbDialog -> dismissSmbDialog()
            is SettingsAction.TestSmbConnection -> testSmbConnection(action.password, action.draft)
            is SettingsAction.SaveSmbAccount -> saveSmbAccount(action.password, action.draft)
            is SettingsAction.RequestDeleteSmbAccount -> {
                pendingConfirmation.value = SettingsConfirmation.DeleteSmbAccount(
                    accountId = action.accountId,
                    title = action.title,
                )
            }
            is SettingsAction.ConfigureSourcePath -> configureSourcePath(action.accountId)
            is SettingsAction.ScanSourceAccount -> scanSourceAccount(action.accountId)
            is SettingsAction.CancelScan -> cancelScan(action.scanId)
            is SettingsAction.OpenScanFailures -> failureDialogTaskId.value = action.scanId
            SettingsAction.DismissScanFailures -> failureDialogTaskId.value = null
            is SettingsAction.SetAudioCacheLimitBytes -> setCacheLimit(CacheLimitType.Audio, action.bytes)
            is SettingsAction.SetImageCacheLimitBytes -> setCacheLimit(CacheLimitType.Image, action.bytes)
            is SettingsAction.SetCustomCacheLimitInput -> {
                customCacheLimitInputMb.value = action.value.filter(Char::isDigit)
            }
            is SettingsAction.OpenCustomCacheLimitDialog -> {
                customCacheLimitInputMb.value = currentCacheLimitMbInput(action.type)
                customCacheLimitDialog.value = action.type
            }
            SettingsAction.DismissCustomCacheLimitDialog -> customCacheLimitDialog.value = null
            SettingsAction.ApplyCustomCacheLimit -> applyCustomCacheLimit()
            SettingsAction.RefreshStorageUsage -> refreshStorageUsage()
            SettingsAction.RequestClearAudio -> pendingConfirmation.value = SettingsConfirmation.ClearAudio
            SettingsAction.RequestClearImage -> pendingConfirmation.value = SettingsConfirmation.ClearImage
            SettingsAction.RequestClearAllCaches -> {
                pendingConfirmation.value = SettingsConfirmation.ClearAllCaches
            }
            SettingsAction.RequestClearAllData -> {
                pendingConfirmation.value = SettingsConfirmation.ClearAllData
            }
            SettingsAction.RequestResetDefaults -> {
                pendingConfirmation.value = SettingsConfirmation.ResetDefaults
            }
            SettingsAction.RequestRebuildLibrary -> {
                pendingConfirmation.value = SettingsConfirmation.RebuildLibrary
            }
            SettingsAction.ExportDiagnostics -> exportDiagnostics()
            SettingsAction.DismissConfirmation -> pendingConfirmation.value = null
            SettingsAction.ConfirmPendingAction -> confirmPendingAction()
        }
    }

    private fun updateSetting(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { error ->
                emitFeedback(Res.string.settings_feedback_save_failed, error.userMessage())
            }
        }
    }

    private fun runSettingsBackup(restore: Boolean) {
        val service = backupService ?: return
        viewModelScope.launch {
            val result = if (restore) service.restoreLatestBackup() else service.createBackup()
            when (result) {
                is SettingsBackupResult.Success -> emitFeedback(
                    if (restore) {
                        Res.string.settings_feedback_backup_restored
                    } else {
                        Res.string.settings_feedback_backup_created
                    },
                    result.path,
                )
                is SettingsBackupResult.Failure -> emitFeedback(
                    Res.string.settings_feedback_backup_failed,
                    result.message,
                )
            }
        }
    }

    private fun setAccountEnabled(accountId: SourceAccountId, enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                sourceSettingsRepository.setAccountEnabled(accountId, enabled)
                storageRepository.reload()
            }.onFailure { error ->
                emitFeedback(Res.string.settings_feedback_source_update_failed, error.userMessage())
            }
        }
    }

    private fun requestAddLocalDirectory() {
        viewModelScope.launch { events.send(SettingsEvent.OpenLibraryFolderPicker) }
    }

    private fun configureSourcePath(accountId: SourceAccountId) {
        val account = state.value.sourceAccounts.firstOrNull { it.accountId == accountId }
            ?: return
        if (!account.isWebDav && !account.isSmb) return

        importRepository.prepareCurrentDirectory(accountId) { selection ->
            if (selection.accountId != accountId) return@prepareCurrentDirectory
            viewModelScope.launch {
                runCatching {
                    storageRepository.setAccountRootPath(accountId, selection.path)
                    storageRepository.reload()
                }.onSuccess {
                    emitFeedback(Res.string.settings_feedback_source_path_saved, selection.path)
                }.onFailure { error ->
                    emitFeedback(
                        Res.string.settings_feedback_source_path_save_failed,
                        error.userMessage(),
                    )
                }
            }
        }
        viewModelScope.launch { events.send(SettingsEvent.OpenSourcePathPicker) }
    }

    private fun addLocalDirectory(path: String) {
        if (permissionChecker.havePermission.value) {
            syncSelectedDirectory(path)
        } else {
            pendingLocalDirectoryPath.value = path
            permissionChecker.requestStoragePermission()
        }
    }

    private fun syncSelectedDirectory(path: String) {
        val accountId = storageSourceAccountId(LOCAL_STORAGE_ID)
        viewModelScope.launch {
            syncFolder(
                request = LibrarySyncRequest(
                    accountId = accountId,
                    selectedFolderRemoteId = null,
                    selectedFolderCanonicalPath = path,
                    selectedFolderDisplayPath = path,
                    scanRules = state.value.settings.scanRules(),
                    metadataScanMode = metadataScanModeFor(accountId),
                ),
                startMessage = textProvider.get(
                    Res.string.settings_feedback_scan_start,
                    path,
                ),
            )
        }
    }

    private fun reportUnsupportedLocalDirectory() {
        viewModelScope.launch {
            emitFeedback(Res.string.settings_feedback_directory_unsupported)
        }
    }

    private fun scanAllSources() {
        scanLocalMusic(showEmptyMessage = false)
        state.value.sourceAccounts
            .filter { item -> item.isWebDav && item.enabled }
            .forEach { item -> scanWebDavAccount(item.accountId) }
        state.value.sourceAccounts
            .filter { item -> item.isSmb && item.enabled && !item.rootPath.isNullOrBlank() }
            .forEach { item -> scanSmbAccount(item.accountId) }
    }

    private fun cancelActiveScans() {
        if (state.value.scanTasks.none(LibrarySyncTask::isActive)) return

        viewModelScope.launch {
            runCatching { librarySyncController.cancelAll() }
                .onSuccess { emitFeedback(Res.string.settings_feedback_scan_cancelled) }
                .onFailure { emitFeedback(Res.string.settings_feedback_scan_not_cancelled) }
        }
    }

    private fun refreshMissingMetadata(target: MetadataRefreshTarget) {
        viewModelScope.launch {
            maintenanceOperationInProgress.value = true
            runCatching {
                metadataRefreshController.refresh(
                    MetadataRefreshRequest(
                        scope = MetadataRefreshScope.MissingWebDavTracks,
                        target = target,
                    )
                )
            }.onSuccess { result ->
                emitFeedback(
                    Res.string.settings_feedback_metadata_complete,
                    result.refreshedCount.toString(),
                    result.failedCount.toString(),
                    formatBytes(result.metadataFetchedBytes),
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                emitFeedback(Res.string.settings_feedback_metadata_failed, error.userMessage())
            }
            maintenanceOperationInProgress.value = false
        }
    }

    private fun scanLocalMusic(showEmptyMessage: Boolean = true) {
        val localAccount = state.value.sourceAccounts.firstOrNull(SourceAccountSettingsItem::isLocal)
        val directories = state.value.localDirectories
        if (localAccount?.enabled != true || directories.isEmpty()) {
            if (showEmptyMessage) {
                viewModelScope.launch {
                    emitFeedback(Res.string.settings_feedback_local_source_required)
                }
            }
            return
        }
        directories.forEach { directory ->
            viewModelScope.launch {
                syncFolder(
                    request = LibrarySyncRequest(
                        accountId = directory.accountId,
                        selectedFolderRemoteId = null,
                        selectedFolderCanonicalPath = directory.path,
                        selectedFolderDisplayPath = directory.path,
                        scanRules = state.value.settings.scanRules(),
                    ),
                    startMessage = textProvider.get(
                        Res.string.settings_feedback_scan_start,
                        directory.displayName,
                    ),
                )
            }
        }
    }

    private fun scanSourceAccount(accountId: SourceAccountId) {
        val account = state.value.sourceAccounts.firstOrNull { it.accountId == accountId } ?: return
        when {
            account.isLocal -> scanLocalMusic()
            account.isWebDav -> scanWebDavAccount(accountId)
            account.isSmb -> scanSmbAccount(accountId)
        }
    }

    private fun metadataScanModeFor(accountId: SourceAccountId): MetadataScanMode {
        val isWebDav = state.value.sourceAccounts
            .any { account -> account.accountId == accountId && account.isWebDav }
        return state.value.settings.metadataScanModeFor(isWebDav)
    }

    private fun openAddWebDavDialog() {
        webDavDialog.value = WebDavAccountDialogState()
        resetWebDavTest()
    }

    private fun openEditWebDavDialog(accountId: SourceAccountId) {
        viewModelScope.launch {
            val routeId = accountId.toStorageRouteIdOrNull() ?: return@launch
            val editorState = storageRepository.loadEditorState(routeId) ?: return@launch
            val credential = storageRepository.loadCredentialByAccountId(accountId)
            val account = state.value.sourceAccounts.firstOrNull { it.accountId == accountId }
            webDavDialog.value = WebDavAccountDialogState(
                accountId = accountId,
                name = editorState.draft.alias,
                serverUrl = editorState.draft.address,
                username = credential?.username.orEmpty(),
                rootPath = account?.rootPath ?: "/",
            )
            resetWebDavTest()
        }
    }

    private fun dismissWebDavDialog() {
        webDavDialog.value = null
        resetWebDavTest()
    }

    private fun testWebDavConnection(
        password: String,
        submittedDraft: WebDavAccountDialogState? = null,
    ) {
        val dialog = submittedDraft ?: webDavDialog.value ?: return
        resetWebDavTest()
        viewModelScope.launch {
            val draft = dialog.toWebDavDraftOrNull(password) ?: return@launch
            webDavConnectionTestStatus.value = SourceConnectionTestStatus.Testing
            webDavConnectionTestMessage.value = textProvider.get(
                Res.string.settings_feedback_connection_testing
            )
            runCatching { storageRepository.testSource(draft) }
                .onSuccess { status ->
                    webDavConnectionTestStatus.value = status
                    webDavConnectionTestMessage.value = when (status) {
                        SourceConnectionTestStatus.Success -> textProvider.get(
                            Res.string.settings_feedback_connection_success
                        )
                        SourceConnectionTestStatus.Error -> textProvider.get(
                            Res.string.settings_feedback_connection_check
                        )
                        SourceConnectionTestStatus.Unauthorized,
                        SourceConnectionTestStatus.Timeout,
                        SourceConnectionTestStatus.PermissionDenied,
                        SourceConnectionTestStatus.NotFound,
                        SourceConnectionTestStatus.InvalidAddress,
                        SourceConnectionTestStatus.Unavailable,
                        SourceConnectionTestStatus.UnsupportedSecurityPolicy -> textProvider.get(
                            Res.string.settings_feedback_connection_check
                        )
                        SourceConnectionTestStatus.Testing -> textProvider.get(
                            Res.string.settings_feedback_connection_testing
                        )
                        SourceConnectionTestStatus.None -> null
                    }
                }
                .onFailure { error ->
                    webDavConnectionTestStatus.value = SourceConnectionTestStatus.Error
                    webDavConnectionTestMessage.value = textProvider.get(
                        Res.string.settings_feedback_connection_failed,
                        error.userMessage(),
                    )
                }
        }
    }

    private fun saveWebDavAccount(
        password: String,
        submittedDraft: WebDavAccountDialogState? = null,
    ) {
        val dialog = submittedDraft ?: webDavDialog.value ?: return
        viewModelScope.launch {
            val draft = dialog.toWebDavDraftOrNull(password) ?: return@launch
            sourceOperationInProgress.value = true
            runCatching {
                val accountId = storageRepository.upsertSource(draft)
                storageRepository.setAccountRootPath(accountId, dialog.rootPath)
                storageRepository.reload()
            }.onSuccess {
                webDavDialog.value = null
                resetWebDavTest()
                emitFeedback(Res.string.settings_feedback_webdav_saved)
            }.onFailure { error ->
                emitFeedback(Res.string.settings_feedback_webdav_save_failed, error.userMessage())
            }
            sourceOperationInProgress.value = false
        }
    }

    private fun scanWebDavAccount(accountId: SourceAccountId) {
        val account = state.value.sourceAccounts.firstOrNull { it.accountId == accountId }
        if (account?.enabled != true) {
            viewModelScope.launch {
                emitFeedback(Res.string.settings_feedback_webdav_enable_required)
            }
            return
        }
        val rootPath = account.rootPath.normalizedRootPath()
        viewModelScope.launch {
            syncFolder(
                request = LibrarySyncRequest(
                    accountId = accountId,
                    selectedFolderRemoteId = null,
                    selectedFolderCanonicalPath = rootPath,
                    selectedFolderDisplayPath = rootPath,
                    scanRules = state.value.settings.scanRules(),
                    metadataScanMode = state.value.settings.webDavMetadataScanMode,
                ),
                startMessage = textProvider.get(
                    Res.string.settings_feedback_scan_start,
                    account.title,
                ),
            )
        }
    }

    private fun openAddSmbDialog() {
        smbDialog.value = SmbAccountDialogState()
        resetSmbTest()
    }

    private fun openEditSmbDialog(accountId: SourceAccountId) {
        viewModelScope.launch {
            val routeId = accountId.toStorageRouteIdOrNull() ?: return@launch
            val editorState = storageRepository.loadEditorState(routeId) ?: return@launch
            val draft = editorState.draft
            if (draft.storageType != SourceEditorType.Smb) return@launch
            val credential = storageRepository.loadCredentialByAccountId(accountId)
            smbDialog.value = SmbAccountDialogState(
                accountId = accountId,
                name = draft.alias,
                host = draft.smbHost,
                port = draft.smbPort.toString(),
                share = draft.smbShare,
                rootPath = draft.smbRootPath,
                domain = draft.smbDomain,
                username = credential?.username.orEmpty(),
                guestAccess = draft.isAnonymous,
                requireSigning = draft.smbRequireSigning,
                requireEncryption = draft.smbRequireEncryption,
            )
            resetSmbTest()
        }
    }

    private fun dismissSmbDialog() {
        smbDialog.value = null
        resetSmbTest()
    }

    private fun testSmbConnection(
        password: String,
        submittedDraft: SmbAccountDialogState? = null,
    ) {
        val dialog = submittedDraft ?: smbDialog.value ?: return
        resetSmbTest()
        viewModelScope.launch {
            val draft = dialog.toSmbDraftOrNull(password) ?: return@launch
            smbConnectionTestStatus.value = SourceConnectionTestStatus.Testing
            smbConnectionTestMessage.value = textProvider.get(
                Res.string.settings_feedback_connection_testing,
            )
            runCatching { storageRepository.testSource(draft) }
                .onSuccess { status ->
                    smbConnectionTestStatus.value = status
                    smbConnectionTestMessage.value = connectionTestMessage(status)
                }
                .onFailure { error ->
                    smbConnectionTestStatus.value = SourceConnectionTestStatus.Error
                    smbConnectionTestMessage.value = textProvider.get(
                        Res.string.settings_feedback_connection_failed,
                        error.userMessage(),
                    )
                }
        }
    }

    private fun saveSmbAccount(
        password: String,
        submittedDraft: SmbAccountDialogState? = null,
    ) {
        val dialog = submittedDraft ?: smbDialog.value ?: return
        viewModelScope.launch {
            val draft = dialog.toSmbDraftOrNull(password) ?: return@launch
            sourceOperationInProgress.value = true
            runCatching {
                storageRepository.upsertSource(draft)
                storageRepository.reload()
            }.onSuccess {
                smbDialog.value = null
                resetSmbTest()
                emitFeedback(Res.string.settings_feedback_smb_saved)
            }.onFailure { error ->
                emitFeedback(Res.string.settings_feedback_smb_save_failed, error.userMessage())
            }
            sourceOperationInProgress.value = false
        }
    }

    private fun scanSmbAccount(accountId: SourceAccountId) {
        val account = state.value.sourceAccounts.firstOrNull { it.accountId == accountId }
        if (account?.enabled != true) {
            viewModelScope.launch {
                emitFeedback(Res.string.settings_feedback_smb_enable_required)
            }
            return
        }
        if (account.rootPath.isNullOrBlank()) {
            viewModelScope.launch {
                emitFeedback(Res.string.settings_feedback_smb_path_required)
            }
            return
        }
        viewModelScope.launch {
            val rootPath = "/"
            syncFolder(
                request = LibrarySyncRequest(
                    accountId = accountId,
                    selectedFolderRemoteId = null,
                    selectedFolderCanonicalPath = rootPath,
                    selectedFolderDisplayPath = rootPath,
                    scanRules = state.value.settings.scanRules(),
                ),
                startMessage = textProvider.get(
                    Res.string.settings_feedback_scan_start,
                    account.title,
                ),
            )
        }
    }

    private suspend fun SmbAccountDialogState.toSmbDraftOrNull(
        password: String,
    ): SourceEditorDraft? {
        val hostValue = host.trim()
        if (hostValue.isBlank()) {
            emitFeedback(Res.string.settings_feedback_smb_host_required)
            return null
        }
        val portValue = port.toIntOrNull()
        if (portValue == null || portValue !in 1..65_535) {
            emitFeedback(Res.string.settings_feedback_smb_port_invalid)
            return null
        }
        val shareValue = share.trim().trim('/')
        val usernameValue = username.trim()
        if (!guestAccess && usernameValue.isBlank()) {
            emitFeedback(Res.string.settings_feedback_smb_username_required)
            return null
        }
        val previousCredential = accountId?.let { storageRepository.loadCredentialByAccountId(it) }
        val secretValue = if (guestAccess) {
            ""
        } else {
            password.ifBlank { previousCredential?.secret.orEmpty() }
        }
        if (!guestAccess && secretValue.isBlank()) {
            emitFeedback(Res.string.settings_feedback_smb_password_required)
            return null
        }
        return SourceEditorDraft(
            id = accountId?.toStorageRouteIdOrNull(),
            alias = name.trim(),
            username = if (guestAccess) "" else usernameValue,
            secret = secretValue,
            isAnonymous = guestAccess,
            storageType = SourceEditorType.Smb,
            smbHost = hostValue,
            smbPort = portValue,
            smbShare = shareValue,
            smbRootPath = rootPath.trim().trim('/'),
            smbDomain = domain.trim(),
            smbRequireSigning = requireSigning,
            smbRequireEncryption = requireEncryption,
        )
    }

    private fun cancelScan(scanId: String) {
        viewModelScope.launch {
            val cancelled = librarySyncController.cancel(scanId)
            emitFeedback(
                if (cancelled) Res.string.settings_feedback_scan_cancelled
                else Res.string.settings_feedback_scan_not_cancelled
            )
        }
    }

    private suspend fun WebDavAccountDialogState.toWebDavDraftOrNull(
        password: String,
    ): SourceEditorDraft? {
        val address = serverUrl.trim()
        if (address.isBlank()) {
            emitFeedback(Res.string.settings_feedback_url_required)
            return null
        }
        val usernameValue = username.trim()
        val previousCredential = accountId?.let { storageRepository.loadCredentialByAccountId(it) }
        val anonymous = usernameValue.isBlank() && password.isBlank()
        val secretValue = if (anonymous) "" else password.ifBlank { previousCredential?.secret.orEmpty() }
        if (usernameValue.isNotBlank() && secretValue.isBlank()) {
            emitFeedback(Res.string.settings_feedback_password_required)
            return null
        }
        return SourceEditorDraft(
            id = accountId?.toStorageRouteIdOrNull(),
            address = address,
            alias = name.trim(),
            username = if (anonymous) "" else usernameValue,
            secret = secretValue,
            isAnonymous = anonymous,
            storageType = SourceEditorType.WebDav,
        )
    }

    private suspend fun syncFolder(request: LibrarySyncRequest, startMessage: String) {
        toastRepository.emitText(startMessage)
        runCatching { librarySyncController.syncFolder(request) }
            .onSuccess { value ->
                emitFeedback(
                    Res.string.settings_feedback_scan_complete,
                    value.syncMode,
                    value.scannedCount.toString(),
                    value.addedCount.toString(),
                    (value.modifiedCount + value.renamedCount).toString(),
                    value.deletedCount.toString(),
                    value.skippedCount.toString(),
                    value.metadataRequestCount.toString(),
                    formatBytes(value.metadataFetchedBytes),
                    value.totalElapsedMs.toString(),
                )
                storageRepository.reload()
            }
            .onFailure { error ->
                if (error is CancellationException) throw error
                if (error is LibrarySyncAlreadyActiveException) {
                    emitFeedback(Res.string.settings_feedback_scan_already_active)
                } else {
                    emitFeedback(Res.string.settings_feedback_scan_failed, error.userMessage())
                }
            }
    }

    private fun setCacheLimit(type: CacheLimitType, bytes: Long) {
        viewModelScope.launch {
            runCatching {
                when (type) {
                    CacheLimitType.Audio -> settingsRepository.setAudioCacheLimitBytes(bytes)
                    CacheLimitType.Image -> settingsRepository.setImageCacheLimitBytes(bytes)
                }
                val settings = settingsRepository.settings.first()
                storageUsageRepository.enforceCacheLimits(
                    audioLimitBytes = settings.audioCacheLimitBytes,
                    imageLimitBytes = settings.imageCacheLimitBytes,
                )
                refreshStorageUsage()
            }.onFailure { error ->
                emitFeedback(Res.string.settings_feedback_cache_limit_failed, error.userMessage())
            }
        }
    }

    private fun applyCustomCacheLimit() {
        val type = customCacheLimitDialog.value ?: return
        val maxBytes = when (type) {
            CacheLimitType.Audio -> MAX_AUDIO_CACHE_LIMIT_BYTES
            CacheLimitType.Image -> MAX_IMAGE_CACHE_LIMIT_BYTES
        }
        val maxMb = maxBytes / BYTES_PER_MB
        val normalizedMb = (customCacheLimitInputMb.value.toLongOrNull() ?: 0L).coerceIn(0L, maxMb)
        customCacheLimitInputMb.value = normalizedMb.toString()
        customCacheLimitDialog.value = null
        val bytes = normalizedMb * BYTES_PER_MB
        setCacheLimit(
            type = type,
            bytes = when (type) {
                CacheLimitType.Audio -> normalizeAudioCacheLimitBytes(bytes)
                CacheLimitType.Image -> normalizeImageCacheLimitBytes(bytes)
            },
        )
    }

    private fun exportDiagnostics() {
        viewModelScope.launch {
            maintenanceOperationInProgress.value = true
            when (val result = diagnosticsService.exportDiagnostics()) {
                is DiagnosticsExportResult.Success -> emitFeedback(
                    Res.string.settings_feedback_diagnostics_exported,
                    result.path,
                )
                is DiagnosticsExportResult.Failure -> emitFeedback(
                    Res.string.settings_feedback_diagnostics_failed,
                    result.message,
                )
            }
            maintenanceOperationInProgress.value = false
        }
    }

    private fun confirmPendingAction() {
        val action = pendingConfirmation.value ?: return
        pendingConfirmation.value = null
        viewModelScope.launch {
            maintenanceOperationInProgress.value = true
            runCatching {
                when (action) {
                    SettingsConfirmation.ClearAudio -> storageUsageRepository.clearAudioCache()
                    SettingsConfirmation.ClearImage -> storageUsageRepository.clearImageCache()
                    SettingsConfirmation.ClearAllCaches -> storageUsageRepository.clearAllCaches()
                    SettingsConfirmation.ClearAllData -> appDataClearService.clearAllData()
                    SettingsConfirmation.ResetDefaults -> {
                        settingsRepository.resetToDefaults()
                        storageUsageRepository.enforceCacheLimits(
                            AppSettings.Default.audioCacheLimitBytes,
                            AppSettings.Default.imageCacheLimitBytes,
                        )
                    }
                    SettingsConfirmation.RebuildLibrary -> libraryMaintenanceService.rebuildLibrary()
                    is SettingsConfirmation.RemoveLocalDirectory -> {
                        sourceSettingsRepository.removeLocalDirectory(action.id)
                        playbackController.clearQueue()
                    }
                    is SettingsConfirmation.DeleteWebDavAccount -> {
                        storageRepository.removeByAccountId(action.accountId)
                        storageRepository.reload()
                        webDavDialog.value = null
                        resetWebDavTest()
                    }
                    is SettingsConfirmation.DeleteSmbAccount -> {
                        storageRepository.removeByAccountId(action.accountId)
                        storageRepository.reload()
                        smbDialog.value = null
                        resetSmbTest()
                    }
                }
            }.onSuccess {
                emitFeedback(action.successMessageResource())
                refreshStorageUsage()
            }.onFailure { error ->
                emitFeedback(Res.string.settings_feedback_operation_failed, error.userMessage())
            }
            maintenanceOperationInProgress.value = false
        }
    }

    private fun refreshStorageUsage() {
        viewModelScope.launch {
            storageRefreshing.value = true
            storageUsage.value = runCatching { storageUsageRepository.loadUsage() }
                .getOrElse { StorageUsage.Unknown }
            storageRefreshing.value = false
        }
    }

    private fun resetWebDavTest() {
        webDavConnectionTestStatus.value = SourceConnectionTestStatus.None
        webDavConnectionTestMessage.value = null
    }

    private fun resetSmbTest() {
        smbConnectionTestStatus.value = SourceConnectionTestStatus.None
        smbConnectionTestMessage.value = null
    }

    private suspend fun connectionTestMessage(status: SourceConnectionTestStatus): String? = when (status) {
        SourceConnectionTestStatus.Success -> textProvider.get(
            Res.string.settings_feedback_connection_success,
        )
        SourceConnectionTestStatus.Error,
        SourceConnectionTestStatus.Unauthorized,
        SourceConnectionTestStatus.Timeout,
        SourceConnectionTestStatus.PermissionDenied,
        SourceConnectionTestStatus.NotFound,
        SourceConnectionTestStatus.InvalidAddress,
        SourceConnectionTestStatus.Unavailable,
        SourceConnectionTestStatus.UnsupportedSecurityPolicy -> textProvider.get(
            Res.string.settings_feedback_connection_check,
        )
        SourceConnectionTestStatus.Testing -> textProvider.get(
            Res.string.settings_feedback_connection_testing,
        )
        SourceConnectionTestStatus.None -> null
    }

    private fun currentCacheLimitMbInput(type: CacheLimitType): String {
        val bytes = when (type) {
            CacheLimitType.Audio -> state.value.settings.audioCacheLimitBytes
            CacheLimitType.Image -> state.value.settings.imageCacheLimitBytes
        }
        return (bytes / BYTES_PER_MB).toString()
    }

    private suspend fun emitFeedback(resource: StringResource, vararg formatArgs: Any) {
        toastRepository.emitText(textProvider.get(resource, *formatArgs))
    }

    private suspend fun Throwable.userMessage(): String =
        message?.takeIf(String::isNotBlank)
            ?: textProvider.get(Res.string.settings_feedback_unknown_error)
}

fun interface SettingsTextProvider {
    suspend fun get(resource: StringResource, vararg formatArgs: Any): String
}

internal class ComposeSettingsTextProvider : SettingsTextProvider {
    override suspend fun get(resource: StringResource, vararg formatArgs: Any): String {
        return getString(resource, *formatArgs)
    }
}

private fun StorageAccountInfo.toSettingsItem(): SourceAccountSettingsItem {
    return SourceAccountSettingsItem(
        accountId = accountId,
        title = title,
        subtitle = subtitle,
        rootPath = rootPath,
        enabled = enabled,
        trackCount = musicCount,
        lastScanAtEpochMs = lastScanAtEpochMs,
        lastScanStatus = lastScanStatus,
        isLocal = sourceId == BuiltInSourceIds.Local,
        isWebDav = sourceId == BuiltInSourceIds.WebDav,
        isSmb = sourceId == BuiltInSourceIds.Smb,
        isRemoteServer = sourceId == BuiltInSourceIds.Navidrome ||
            sourceId == BuiltInSourceIds.OpenSubsonic ||
            sourceId == BuiltInSourceIds.Emby,
        sourceLabel = when (sourceId) {
            BuiltInSourceIds.Local -> "Local"
            BuiltInSourceIds.WebDav -> "WebDAV"
            BuiltInSourceIds.OneDrive -> "OneDrive"
            BuiltInSourceIds.Smb -> "SMB"
            BuiltInSourceIds.Navidrome -> "Navidrome"
            BuiltInSourceIds.OpenSubsonic -> "OpenSubsonic"
            BuiltInSourceIds.Emby -> "Emby"
            else -> sourceId.value
        },
    )
}

private fun List<LibrarySyncTask>.filterRelevantToSettings(
    localDirectories: List<LocalMusicDirectory>,
    sourceAccounts: List<SourceAccountSettingsItem>,
): List<LibrarySyncTask> {
    val localAccountIds = if (localDirectories.isEmpty()) {
        emptySet()
    } else {
        localDirectories.map(LocalMusicDirectory::accountId).toSet() +
            storageSourceAccountId(LOCAL_STORAGE_ID)
    }
    val accountIds = sourceAccounts.map(SourceAccountSettingsItem::accountId).toSet()
    return filter { task -> task.accountId in localAccountIds || task.accountId in accountIds }
}

private fun String?.normalizedRootPath(): String {
    val trimmed = this?.trim().orEmpty().ifBlank { "/" }
    return if (trimmed.startsWith('/')) trimmed else "/$trimmed"
}

internal fun AppSettings.scanRules(): LibrarySyncScanRules {
    return LibrarySyncScanRules(
        scanSubdirectories = scanSubdirectories,
        minDurationMs = minimumAudioDurationMs,
        missingFilePolicy = missingFilePolicy,
    )
}

private fun SettingsConfirmation.successMessageResource(): StringResource = when (this) {
    SettingsConfirmation.ClearAudio -> Res.string.settings_feedback_audio_cleared
    SettingsConfirmation.ClearImage -> Res.string.settings_feedback_image_cleared
    SettingsConfirmation.ClearAllCaches -> Res.string.settings_feedback_all_cleared
    SettingsConfirmation.ClearAllData -> Res.string.settings_feedback_all_data_cleared
    SettingsConfirmation.ResetDefaults -> Res.string.settings_feedback_defaults_restored
    SettingsConfirmation.RebuildLibrary -> Res.string.settings_feedback_library_rebuilt
    is SettingsConfirmation.RemoveLocalDirectory -> Res.string.settings_feedback_directory_removed
    is SettingsConfirmation.DeleteWebDavAccount -> Res.string.settings_feedback_webdav_deleted
    is SettingsConfirmation.DeleteSmbAccount -> Res.string.settings_feedback_smb_deleted
}

private const val BYTES_PER_MB = 1_048_576L
private const val LOCAL_STORAGE_ID = 1L
