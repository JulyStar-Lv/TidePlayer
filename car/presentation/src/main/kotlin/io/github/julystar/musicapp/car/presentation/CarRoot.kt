package io.github.julystar.musicapp.car.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.julystar.musicapp.car.presentation.layout.CarLayoutMetrics
import io.github.julystar.musicapp.car.presentation.navigation.CarNavigationRoot
import io.github.julystar.musicapp.car.presentation.theme.CarTheme
import io.github.julystar.musicapp.car.presentation.theme.LocalCarColors
import io.github.julystar.musicapp.core.domain.model.AppSettings
import io.github.julystar.musicapp.core.domain.model.AppThemeMode
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import org.koin.compose.koinInject

@Composable
fun CarRoot(
    metrics: CarLayoutMetrics? = null,
    onExit: () -> Unit = {},
    onEnterFullscreen: () -> Unit = {},
    onExitPlayback: () -> Unit = {},
    onExitFullscreen: () -> Unit = {},
    exitPlaybackRequest: Long = 0L,
    modifier: Modifier = Modifier,
) {
    val settingsRepository = koinInject<SettingsRepository>()
    val settings by settingsRepository.settings.collectAsState(AppSettings())
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (settings.themeMode) {
        AppThemeMode.Dark -> true
        AppThemeMode.Light -> false
        AppThemeMode.System -> systemDark
    }
    CarTheme(darkTheme = darkTheme, layoutMetrics = metrics) {
        Box(modifier.fillMaxSize().background(LocalCarColors.current.backgroundBase)) {
            CarNavigationRoot(
                metrics = metrics,
                lyricDisplaySettings = settings.lyrics,
                onExit = onExit,
                onEnterFullscreen = onEnterFullscreen,
                onExitPlayback = onExitPlayback,
                onExitFullscreen = onExitFullscreen,
                exitPlaybackRequest = exitPlaybackRequest,
            )
        }
    }
}
