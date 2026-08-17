package io.github.julystar.musicapp.di

import io.github.julystar.musicapp.core.data.datastore.AppPreferencesRepository
import io.github.julystar.musicapp.core.data.datastore.createAppDataStore
import io.github.julystar.musicapp.core.data.security.createCredentialStore
import io.github.julystar.musicapp.core.data.settings.AutoScanCoordinator
import io.github.julystar.musicapp.core.data.settings.AutomaticTrackMerger
import io.github.julystar.musicapp.core.data.settings.DataStoreSettingsRepository
import io.github.julystar.musicapp.core.data.settings.FileDiagnosticsService
import io.github.julystar.musicapp.core.data.settings.FileStorageUsageRepository
import io.github.julystar.musicapp.core.data.settings.RoomLibraryMaintenanceService
import io.github.julystar.musicapp.core.data.settings.RoomAppDataClearService
import io.github.julystar.musicapp.core.data.settings.RoomSettingsMigration
import io.github.julystar.musicapp.core.data.settings.RoomSourceSettingsRepository
import io.github.julystar.musicapp.core.data.settings.TrackDuplicateMerger
import io.github.julystar.musicapp.core.data.settings.JsonSettingsBackupService
import io.github.julystar.musicapp.core.data.settings.RustAudioDspAnalysisRepository
import io.github.julystar.musicapp.core.domain.model.SettingsCapabilities
import io.github.julystar.musicapp.core.domain.repository.AudioDspAnalysisRepository
import io.github.julystar.musicapp.core.domain.repository.AudioDspRuntimeRepository
import io.github.julystar.musicapp.core.domain.repository.AudioMonitoringRepository
import io.github.julystar.musicapp.core.domain.repository.AudioReactiveRepository
import io.github.julystar.musicapp.core.domain.repository.DiagnosticsService
import io.github.julystar.musicapp.core.domain.repository.DiagnosticsRepository
import io.github.julystar.musicapp.core.domain.repository.AppDataClearService
import io.github.julystar.musicapp.core.domain.repository.LibraryMaintenanceService
import io.github.julystar.musicapp.core.domain.repository.SettingsMigration
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.core.domain.repository.SettingsBackupService
import io.github.julystar.musicapp.core.domain.repository.SourceSettingsRepository
import io.github.julystar.musicapp.core.domain.repository.StorageUsageRepository
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.buildDatabase
import io.github.julystar.musicapp.platform.getAppCacheDir
import io.github.julystar.musicapp.platform.getAppDataDirectory
import io.github.julystar.musicapp.platform.getAppVersion
import io.github.julystar.musicapp.platform.platformSettingsCapabilities
import io.github.julystar.musicapp.plugin.install.PluginInstaller
import io.github.julystar.musicapp.plugin.management.MetadataLookupUseCase
import io.github.julystar.musicapp.plugin.management.PluginManager
import io.github.julystar.musicapp.plugin.management.PluginMetaSourceRegistry
import io.github.julystar.musicapp.plugin.management.PluginRepository
import io.github.julystar.musicapp.plugin.runtime.PluginCandidateContextStore
import io.github.julystar.musicapp.plugin.runtime.PluginResultParser
import io.github.julystar.musicapp.plugin.runtime.PluginRuntimeFactory
import io.github.julystar.musicapp.plugin.runtime.PluginRuntimeManager
import io.github.julystar.musicapp.plugin.runtime.PluginRuntimeSettings
import io.github.julystar.musicapp.plugin.runtime.PluginScriptBundleBuilder
import io.github.julystar.musicapp.singleton.Bridge
import io.github.julystar.musicapp.singleton.RoomLibraryStore
import io.github.julystar.musicapp.feature.home.data.RoomHomeHistoryRepository
import io.github.julystar.musicapp.feature.home.data.RoomHomeStatisticsRepository
import io.github.julystar.musicapp.feature.home.domain.HomeHistoryRepository
import io.github.julystar.musicapp.feature.home.domain.HomeStatisticsRepository
import io.github.julystar.musicapp.diagnostics.RustDiagnosticsRepository
import io.github.julystar.musicapp.core.audio.AudioDspRuntimeMonitor
import io.github.julystar.musicapp.core.audio.AudioReactiveMonitor

import io.github.julystar.musicapp.source.api.MetaSourceRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import org.koin.dsl.onClose
import org.koin.dsl.module

val coreDataModule = module {
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { buildDatabase() }
    single { get<AppDatabase>().sourceAccountDao() }
    single { get<AppDatabase>().libraryRootDao() }
    single { get<AppDatabase>().sourceItemDao() }
    single { get<AppDatabase>().trackSourceRefDao() }
    single { get<AppDatabase>().trackDao() }
    single { get<AppDatabase>().trackFtsDao() }
    single { get<AppDatabase>().playlistDao() }
    single { get<AppDatabase>().metadataDao() }
    single { get<AppDatabase>().syncDao() }
    single { get<AppDatabase>().sourceErrorDao() }
    single { get<AppDatabase>().downloadTaskDao() }
    single { get<AppDatabase>().pluginDao() }
    single { get<AppDatabase>().listeningStatisticsDao() }
    single { createAppDataStore() }
    single { AppPreferencesRepository(get()) }
    single<SettingsRepository> { DataStoreSettingsRepository(get()) }
    single<AudioDspAnalysisRepository> { RustAudioDspAnalysisRepository }
    single<AudioDspRuntimeRepository> { AudioDspRuntimeMonitor }
    single<AudioMonitoringRepository> { get<AudioDspRuntimeRepository>() }
    single<AudioReactiveRepository> { AudioReactiveMonitor }
    single<SettingsBackupService>(createdAtStart = true) {
        JsonSettingsBackupService(get(), getAppDataDirectory(), get(), get())
    }
    single<SettingsMigration> { RoomSettingsMigration(get(), get()) }
    single<SourceSettingsRepository> { RoomSourceSettingsRepository(get(), get()) }
    single<DiagnosticsRepository> { RustDiagnosticsRepository }
    single<StorageUsageRepository> { FileStorageUsageRepository(get()) }
    single<DiagnosticsService> {
        FileDiagnosticsService(get(), get(), get(), get(), get(), get(), get())
    }
    single { io.github.julystar.musicapp.platform.diagnosticExportPresenter() }
    single<LibraryMaintenanceService> {
        RoomLibraryMaintenanceService(get(), get(), get(), get(), get())
    }
    single<AutomaticTrackMerger> { TrackDuplicateMerger(get(), get()) }
    single<AppDataClearService> {
        RoomAppDataClearService(get(), get(), get(), get(), get(), get(), get(), get(), get())
    }
    single { AutoScanCoordinator(get(), get(), get(), get(), get()) }
    single<SettingsCapabilities> { platformSettingsCapabilities() }
    single { createCredentialStore() }
    single { Bridge(getAppDataDirectory(), getAppCacheDir(), get()) }
    single { RoomLibraryStore(get(), get(), get(), get(), get(), get(), get()) }
    single<HomeHistoryRepository> { RoomHomeHistoryRepository(get(), get(), get()) }
    single<HomeStatisticsRepository> { RoomHomeStatisticsRepository(get(), get(), get(), get()) }


    single {
        PluginRuntimeSettings(
            appVersionName = getAppVersion(),
            cacheDirectory = getAppCacheDir(),
        )
    }
    single { PluginScriptBundleBuilder() }
    single { PluginRuntimeFactory(get()) }
    single { PluginRuntimeManager(get(), get()) }
    single { PluginCandidateContextStore() }
    single { PluginResultParser(get()) }
    single {
        PluginRepository(
            pluginDao = get(),
            pluginsDir = getAppDataDirectory().toPath() / "plugins",
        )
    }
    single {
        PluginInstaller(
            pluginDao = get(),
            pluginsDir = getAppDataDirectory().toPath() / "plugins",
        )
    }
    single { MetaSourceRegistry() }
    single {
        PluginMetaSourceRegistry(
            scope = get(),
            repository = get(),
            runtimeManager = get(),
            resultParser = get(),
            registry = get(),
        )
    } onClose { registry ->
        registry?.let { runBlocking { it.shutdown() } }
    }
    single {
        MetadataLookupUseCase(
            registry = get<PluginMetaSourceRegistry>().registry,
            pluginRepository = get(),
            manualOperationTimeoutMs = get<PluginRuntimeSettings>().manualOperationTimeoutMs,
        )
    }
    single {
        PluginManager(
            repository = get(),
            installer = get(),
            runtimeManager = get(),
            resultParser = get(),
            runtimeSettings = get(),
        )
    }
}
