package io.github.julystar.musicapp.di

import io.github.julystar.musicapp.singleton.IosPermissionChecker
import io.github.julystar.musicapp.singleton.IosPlayerController
import io.github.julystar.musicapp.core.domain.repository.PermissionChecker
import io.github.julystar.musicapp.service.playback.data.PlayerController
import io.github.julystar.musicapp.service.download.data.scheduler.IosUrlSessionDownloadScheduler
import io.github.julystar.musicapp.service.download.domain.DownloadTaskScheduler
import org.koin.core.module.Module
import org.koin.dsl.module
import io.github.julystar.musicapp.core.data.settings.IosNetworkStatusProvider
import io.github.julystar.musicapp.core.domain.repository.NetworkStatusProvider
import io.github.julystar.musicapp.service.playback.data.IosAdvancedPlaybackController
import io.github.julystar.musicapp.service.playback.domain.AdvancedPlaybackController

actual val platformModule: Module = module {
    single<PlayerController> {
        IosPlayerController(
            playerRepository = get(),
            toastRepository = get(),
            playlistRepository = get(),
            storageRepository = get(),
            roomLibraryStore = get(),
            playbackResourceResolver = get(),
            scope = get(),
            settingsRepository = get(),
            networkStatusProvider = get(),
        )
    }
    single<PermissionChecker> { IosPermissionChecker() }
    single<DownloadTaskScheduler> {
        IosUrlSessionDownloadScheduler(
            repository = get(),
            sourceRegistry = get(),
            legacyStoragePlaybackResolver = get(),
            scope = get(),
            downloadFinalizer = get(),
        )
    }
    single<NetworkStatusProvider> { IosNetworkStatusProvider() }
    single<AdvancedPlaybackController> { IosAdvancedPlaybackController() }
}
