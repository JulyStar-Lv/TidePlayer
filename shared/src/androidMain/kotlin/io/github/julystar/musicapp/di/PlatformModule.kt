package io.github.julystar.musicapp.di

import io.github.julystar.musicapp.core.domain.repository.PermissionChecker
import io.github.julystar.musicapp.singleton.PermissionRepository
import io.github.julystar.musicapp.service.playback.data.PlayerController
import io.github.julystar.musicapp.singleton.PlayerControllerRepository
import io.github.julystar.musicapp.service.download.data.scheduler.AndroidWorkManagerDownloadScheduler
import io.github.julystar.musicapp.service.download.domain.DownloadTaskScheduler
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import io.github.julystar.musicapp.core.data.settings.AndroidNetworkStatusProvider
import io.github.julystar.musicapp.core.domain.repository.NetworkStatusProvider

actual val platformModule: Module = module {
    single {
        PlayerControllerRepository(
            playerRepository = get(),
            toastRepository = get(),
            playlistRepository = get(),
            storageRepository = get(),
            bridge = get(),
            roomLibraryStore = get(),
            playbackResourceResolver = get(),
            _scope = get(),
            settingsRepository = get(),
            networkStatusProvider = get(),
        )
    } bind PlayerController::class
    single { PermissionRepository(get()) } bind PermissionChecker::class
    single<DownloadTaskScheduler> { AndroidWorkManagerDownloadScheduler() }
    single<NetworkStatusProvider> { AndroidNetworkStatusProvider() }
}
