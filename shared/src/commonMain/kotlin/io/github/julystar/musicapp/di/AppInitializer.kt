package io.github.julystar.musicapp.di

import io.github.julystar.musicapp.core.domain.model.DiagnosticIncidentDraft
import io.github.julystar.musicapp.core.domain.model.DiagnosticIncidentSeverity
import io.github.julystar.musicapp.core.domain.model.DiagnosticIncidentType
import io.github.julystar.musicapp.core.domain.model.DiagnosticStartupStage
import io.github.julystar.musicapp.core.domain.model.AudioEffectSettings
import io.github.julystar.musicapp.core.domain.model.AutoScanMode
import io.github.julystar.musicapp.singleton.Bridge
import io.github.julystar.musicapp.core.data.PlaylistRepositoryImpl
import io.github.julystar.musicapp.core.data.StorageRepositoryImpl
import io.github.julystar.musicapp.core.data.datastore.AppPreferencesRepository
import io.github.julystar.musicapp.service.playback.data.PlayerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.Koin
import io.github.julystar.musicapp.core.data.settings.AutoScanCoordinator
import io.github.julystar.musicapp.core.domain.repository.LibraryMaintenanceService
import io.github.julystar.musicapp.core.domain.repository.SettingsMigration
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.platform.currentTimeMillis
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncController
import kotlinx.coroutines.flow.first
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.PluginDao
import io.github.julystar.musicapp.database.SourceAccountDao
import io.github.julystar.musicapp.diagnostics.RustDiagnosticsRepository
import io.github.julystar.musicapp.plugin.management.PluginMetaSourceRegistry

/**
 * Shared app initialization called by every platform entry point
 * after [initKoin]. Initialize bridge synchronously, then reload
 * repositories asynchronously in the given scope.
 */
object AppInitializer {

    /**
     * Synchronous startup: initialize the Rust bridge.
     * Must be called before any Compose content renders.
     */
    fun initializeBridge(
        koin: Koin,
        disabledComponents: Set<String> = emptySet(),
    ) {
        runBlocking {
            RustDiagnosticsRepository.updateStartupStage(DiagnosticStartupStage.SettingsLoading)
            val settingsRepository = koin.get<SettingsRepository>()
            settingsRepository.settings.first()
            RustDiagnosticsRepository.updateStartupStage(DiagnosticStartupStage.SettingsReady)

            RustDiagnosticsRepository.updateStartupStage(DiagnosticStartupStage.DatabaseOpening)
            try {
                koin.get<AppDatabase>()
                val sourceAccounts = koin.get<SourceAccountDao>().listAll()
                RustDiagnosticsRepository.setMusicRoots(
                    sourceAccounts.mapNotNull { account -> account.rootPath?.takeIf(String::isNotBlank) },
                )
            } catch (error: Throwable) {
                recordStartupFailure(DiagnosticIncidentType.DatabaseOpenFailure, error)
                throw error
            }
            try {
                koin.get<SettingsMigration>().migrate()
            } catch (error: Throwable) {
                recordStartupFailure(DiagnosticIncidentType.DatabaseMigrationFailure, error)
                throw error
            }
            RustDiagnosticsRepository.updateStartupStage(DiagnosticStartupStage.DatabaseReady)

            applyRecoveryOptions(koin, disabledComponents)

            RustDiagnosticsRepository.updateStartupStage(DiagnosticStartupStage.BackendCreating)
            try {
                koin.get<Bridge>().initialize()
            } catch (error: Throwable) {
                recordStartupFailure(DiagnosticIncidentType.StartupFailure, error)
                throw error
            }
            RustDiagnosticsRepository.updateStartupStage(DiagnosticStartupStage.BackendReady)

            RustDiagnosticsRepository.updateStartupStage(DiagnosticStartupStage.PluginsLoading)
            try {
                initializePluginSources(koin, disabledComponents)
            } catch (error: Throwable) {
                recordStartupFailure(DiagnosticIncidentType.PluginBootFailure, error)
                throw error
            }
            RustDiagnosticsRepository.updateStartupStage(DiagnosticStartupStage.PluginsReady)
        }
    }

    internal suspend fun initializePluginSources(
        koin: Koin,
        disabledComponents: Set<String>,
    ) {
        if ("third_party_plugins" !in disabledComponents) {
            koin.get<PluginMetaSourceRegistry>().refresh()
        }
    }

    /**
     * Asynchronously reload player, storage, and playlist repositories.
     */
    suspend fun reloadRepositories(
        koin: Koin,
        disabledComponents: Set<String> = emptySet(),
    ) {
        try {
            RustDiagnosticsRepository.updateStartupStage(DiagnosticStartupStage.PlaybackRestoring)
            koin.get<LibrarySyncController>().recoverInterruptedTasks()
            if ("playback_restore" !in disabledComponents) {
                koin.get<PlayerRepository>().reload()
            }
            RustDiagnosticsRepository.updateStartupStage(DiagnosticStartupStage.PlaybackReady)
            koin.get<StorageRepositoryImpl>().reload()
            koin.get<PlaylistRepositoryImpl>().reload()
            RustDiagnosticsRepository.updateStartupStage(DiagnosticStartupStage.SourceTasksScheduling)
            if ("automatic_scan" !in disabledComponents) {
                val autoScanCoordinator = koin.get<AutoScanCoordinator>()
                koin.get<CoroutineScope>().launch {
                    autoScanCoordinator.runStartupScan()
                }
            }
            if ("rebuild_library_index" in disabledComponents) {
                koin.get<LibraryMaintenanceService>().rebuildLibrary()
            }
        } catch (error: Throwable) {
            val type = when (
                runCatching { RustDiagnosticsRepository.snapshot().startupAttempt.lastStage }
                    .getOrNull()
            ) {
                DiagnosticStartupStage.PlaybackRestoring ->
                    DiagnosticIncidentType.PlaybackBackendFailure
                else -> DiagnosticIncidentType.StartupFailure
            }
            recordStartupFailure(type, error)
            throw error
        }
    }

    private suspend fun applyRecoveryOptions(koin: Koin, options: Set<String>) {
        val settings = koin.get<SettingsRepository>()
        if ("settings_defaults" in options) settings.resetToDefaults()
        if ("dsp_defaults" in options) settings.setAudioEffectSettings(AudioEffectSettings.Default)
        if ("automatic_scan" in options) {
            settings.setAutoScanMode(AutoScanMode.Off)
        }
        if ("playback_restore" in options) {
            koin.get<AppPreferencesRepository>().clearPlaybackSession()
        }
        if ("third_party_plugins" in options) {
            koin.get<PluginDao>().disableAll()
        }
        if ("remote_sources" in options) {
            koin.get<SourceAccountDao>().disableRemoteSources(currentTimeMillis())
        }
    }

    private fun recordStartupFailure(type: DiagnosticIncidentType, error: Throwable) {
        runCatching {
            RustDiagnosticsRepository.recordFatalIncident(
                DiagnosticIncidentDraft(
                    type = type,
                    severity = DiagnosticIncidentSeverity.Fatal,
                    summary = error.message ?: type.name,
                    detail = error.stackTraceToString(),
                    fingerprintMaterial = error.stackTraceToString(),
                    requiresRecovery = true,
                )
            )
        }
    }
}
