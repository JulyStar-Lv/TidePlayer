package io.github.julystar.musicapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import io.github.julystar.musicapp.core.domain.model.AppSettings
import io.github.julystar.musicapp.core.domain.model.AppThemeMode
import io.github.julystar.musicapp.core.domain.model.resolvedDarkManualThemeSeedArgb
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.core.domain.repository.ToastRepository
import io.github.julystar.musicapp.core.domain.repository.UiMessageKey
import io.github.julystar.musicapp.core.domain.repository.emit
import io.github.julystar.musicapp.core.presentation.theme.AppTheme
import io.github.julystar.musicapp.core.presentation.theme.ThemeSeedState
import io.github.julystar.musicapp.feature.home.presentation.HomeViewModel
import io.github.julystar.musicapp.feature.home.presentation.LocalPreloadedHomeViewModel
import io.github.julystar.musicapp.core.LocalNavController
import io.github.julystar.musicapp.core.RoutesProvider
import io.github.julystar.musicapp.navigation.AppNavigation
import io.github.julystar.musicapp.navigation.isImmersivePlayerRoute
import io.github.julystar.musicapp.platform.AppLocaleEnvironment
import io.github.julystar.musicapp.diagnostics.DiagnosticsBootstrapState
import io.github.julystar.musicapp.diagnostics.RustDiagnosticsRepository
import io.github.julystar.musicapp.diagnostics.SafeModeScreen
import io.github.julystar.musicapp.core.domain.model.DiagnosticStartupStage
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun Root(
    diagnosticsState: DiagnosticsBootstrapState? = null,
    onReady: () -> Unit = {},
    onStartupStable: () -> Unit = {},
    onTryNormalStartup: (Set<String>) -> Unit = {},
) {
    if (diagnosticsState?.safeMode == true) {
        SafeModeScreen(
            state = diagnosticsState,
            onTryNormalStartup = onTryNormalStartup,
        )
        StartupLifecycleEffect(onReady, onStartupStable)
        return
    }
    RoutesProvider {
        val controller = LocalNavController.current
        val currentBackStackEntry by controller.currentBackStackEntryAsState()
        val useDarkSystemBars = isImmersivePlayerRoute(currentBackStackEntry?.destination?.route)
        val settingsRepository = koinInject<SettingsRepository>()
        val toastRepository = koinInject<ToastRepository>()
        val settings by settingsRepository.settings.collectAsState<AppSettings, AppSettings?>(null)
        val homeViewModel = koinViewModel<HomeViewModel>()
        val homeState by homeViewModel.state.collectAsState()
        val loadedSettings = settings
        if (loadedSettings == null || homeState.isLoading) {
            AppTheme(
                themeMode = loadedSettings?.themeMode ?: AppThemeMode.System,
                themeSeedState = loadedSettings?.themeSeedState() ?: ThemeSeedState.Default,
            ) {
                AppStartupScreen()
            }
            return@RoutesProvider
        }

        AppLocaleEnvironment(loadedSettings.languageMode) {
            AppTheme(
                themeMode = loadedSettings.themeMode,
                themeSeedState = loadedSettings.themeSeedState(),
                forceDarkSystemBars = useDarkSystemBars,
            ) {
                CompositionLocalProvider(LocalPreloadedHomeViewModel provides homeViewModel) {
                    AppNavigation(navController = controller)
                }
            }
        }
        LaunchedEffect(diagnosticsState?.snapshot?.startupAttempt?.attemptId) {
            if (diagnosticsState?.consumeRecoveryAttention() != null) {
                toastRepository.emit(UiMessageKey.PreviousAbnormalExitDetected)
            }
        }
        StartupLifecycleEffect(onReady, onStartupStable)
    }
}

@Composable
private fun StartupLifecycleEffect(
    onReady: () -> Unit,
    onStartupStable: () -> Unit,
) {
    LaunchedEffect(Unit) {
        runCatching {
            RustDiagnosticsRepository.updateStartupStage(DiagnosticStartupStage.UiCompositionStarted)
        }
        withFrameNanos { }
        runCatching {
            RustDiagnosticsRepository.updateStartupStage(DiagnosticStartupStage.FirstFrameRendered)
        }
        onReady()
        delay(10_000)
        runCatching {
            RustDiagnosticsRepository.updateStartupStage(DiagnosticStartupStage.StartupStable)
        }
        onStartupStable()
    }
}

@Composable
private fun AppStartupScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background),
    )
}

private fun AppSettings.themeSeedState() = ThemeSeedState(
    manualSeedArgb = manualThemeSeedArgb,
    darkManualSeedArgb = resolvedDarkManualThemeSeedArgb(),
)
