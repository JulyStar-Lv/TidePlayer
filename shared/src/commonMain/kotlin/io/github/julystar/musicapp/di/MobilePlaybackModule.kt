package io.github.julystar.musicapp.di

import io.github.julystar.musicapp.plugin.management.ManualMetadataService
import io.github.julystar.musicapp.service.playback.presentation.di.playbackPresentationModule
import org.koin.dsl.module

val mobilePlaybackModule = module {
    includes(playbackPresentationModule)
    single { ManualMetadataService(get(), get(), get(), get(), get(), get(), get(), get()) }
}

val playbackModule = module {
    includes(mobilePlaybackModule)
}
