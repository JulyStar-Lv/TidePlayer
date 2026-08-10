package io.github.julystar.musicapp.di

import io.github.julystar.musicapp.core.data.PlaylistRepositoryImpl
import io.github.julystar.musicapp.singleton.DesktopPermissionChecker
import io.github.julystar.musicapp.singleton.DesktopPlaybackEngine
import io.github.julystar.musicapp.singleton.DesktopPlayerController
import io.github.julystar.musicapp.singleton.RodioDesktopPlaybackEngine
import io.github.julystar.musicapp.core.domain.repository.PermissionChecker
import io.github.julystar.musicapp.service.playback.data.PlayerController
import io.github.julystar.musicapp.service.download.data.scheduler.DesktopCoroutineDownloadScheduler
import io.github.julystar.musicapp.service.download.domain.DownloadTaskScheduler
import io.github.julystar.musicapp.service.playback.data.DesktopAdvancedPlaybackController
import io.github.julystar.musicapp.service.playback.data.DesktopFloatingLyricsController
import io.github.julystar.musicapp.service.playback.domain.AdvancedPlaybackController
import org.koin.core.module.Module
import org.koin.dsl.module
import io.github.julystar.musicapp.core.data.settings.DesktopNetworkStatusProvider
import io.github.julystar.musicapp.core.domain.repository.NetworkStatusProvider

actual val platformModule: Module = module {
    single<DesktopPlaybackEngine> { RodioDesktopPlaybackEngine() }
    single<DownloadTaskScheduler> {
        DesktopCoroutineDownloadScheduler(
            repository = get(),
            sourceRegistry = get(),
            legacyStoragePlaybackResolver = get(),
            scope = get(),
            downloadFinalizer = get(),
        )
    }
    single<PlayerController> {
        DesktopPlayerController(
            playerRepository = get(),
            toastRepository = get(),
            playlistRepository = get(),
            storageRepository = get(),
            roomLibraryStore = get(),
            playbackResourceResolver = get(),
            playbackEngine = get(),
            scope = get(),
            settingsRepository = get(),
            networkStatusProvider = get(),
        )
    }
    single<PermissionChecker> { DesktopPermissionChecker() }
    single<AdvancedPlaybackController> { DesktopAdvancedPlaybackController() }
    single(createdAtStart = true) {
        DesktopFloatingLyricsController(get(), get(), get(), get(), get())
    }
    single<NetworkStatusProvider> { DesktopNetworkStatusProvider() }
}
