package io.github.julystar.musicapp.feature.settings.di

import io.github.julystar.musicapp.feature.settings.presentation.SettingsVM
import io.github.julystar.musicapp.feature.settings.presentation.ComposeSettingsTextProvider
import io.github.julystar.musicapp.feature.settings.presentation.SettingsTextProvider
import io.github.julystar.musicapp.feature.settings.presentation.DiagnosticsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val settingsFeatureDiModule = module {
    single<SettingsTextProvider> { ComposeSettingsTextProvider() }
    viewModel {
        SettingsVM(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()
        )
    }
    viewModel { DiagnosticsViewModel(get(), get(), get()) }
}
