package io.github.julystar.musicapp.di

import io.github.julystar.musicapp.service.download.data.RoomDownloadTaskRepository
import io.github.julystar.musicapp.service.download.data.finalization.DownloadedMediaFinalizer
import io.github.julystar.musicapp.service.download.data.di.downloadDataModule
import io.github.julystar.musicapp.service.download.domain.DownloadFinalizer
import io.github.julystar.musicapp.service.download.domain.DownloadTaskRepository
import io.github.julystar.musicapp.service.download.domain.EnqueueDownloadUseCase
import io.github.julystar.musicapp.service.playback.data.PlayerRepository
import org.koin.dsl.module

val downloadModule = module {
    includes(downloadDataModule)
    single<DownloadTaskRepository> { RoomDownloadTaskRepository(get()) }
    single<DownloadFinalizer> {
        DownloadedMediaFinalizer(
            database = get(),
            settingsRepository = get(),
            playerRepository = lazy { get<PlayerRepository>() },
        )
    }
    single { EnqueueDownloadUseCase(get()) }
}
