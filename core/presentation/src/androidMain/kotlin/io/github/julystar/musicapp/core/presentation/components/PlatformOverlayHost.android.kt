package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
actual fun PlatformOverlayHost(
    onDismissRequest: () -> Unit,
    dismissOnClickOutside: Boolean,
    navigationBarStyle: PlatformOverlayNavigationBarStyle,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnClickOutside = dismissOnClickOutside,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        PlatformOverlaySystemBarsEffect(navigationBarStyle)
        content()
    }
}

@Composable
@Suppress("DEPRECATION")
internal actual fun PlatformOverlaySystemBarsEffect(
    navigationBarStyle: PlatformOverlayNavigationBarStyle,
) {
    val view = LocalView.current
    if (view.isInEditMode) return

    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    val surfaceColor = MiuixTheme.colorScheme.surfaceContainer

    DisposableEffect(window, navigationBarStyle, surfaceColor) {
        val insetsController = WindowCompat.getInsetsController(window, view)
        val previousStatusBarColor = window.statusBarColor
        val previousNavigationBarColor = window.navigationBarColor
        val previousLightStatusBars = insetsController.isAppearanceLightStatusBars
        val previousLightNavigationBars = insetsController.isAppearanceLightNavigationBars
        val previousStatusBarContrast = window.isStatusBarContrastEnforced
        val previousNavigationBarContrast = window.isNavigationBarContrastEnforced

        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = Color.Transparent.toArgb()
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars =
            navigationBarStyle == PlatformOverlayNavigationBarStyle.Surface &&
                surfaceColor.luminance() > 0.5f

        onDispose {
            window.statusBarColor = previousStatusBarColor
            window.navigationBarColor = previousNavigationBarColor
            window.isStatusBarContrastEnforced = previousStatusBarContrast
            window.isNavigationBarContrastEnforced = previousNavigationBarContrast
            insetsController.isAppearanceLightStatusBars = previousLightStatusBars
            insetsController.isAppearanceLightNavigationBars = previousLightNavigationBars
        }
    }
}
