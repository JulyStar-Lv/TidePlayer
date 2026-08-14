package io.github.julystar.musicapp.core.presentation.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import java.util.WeakHashMap

@Composable
actual fun SystemBarsEffect(isDarkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return

    DisposableEffect(view, isDarkTheme) {
        val window = view.context.findActivity()?.window
        if (window == null) {
            onDispose { }
        } else {
            val insetsController = WindowCompat.getInsetsController(window, view)
            val previousLightStatusBars = insetsController.isAppearanceLightStatusBars
            val previousLightNavigationBars = insetsController.isAppearanceLightNavigationBars
            val previousNavigationBarContrast = window.isNavigationBarContrastEnforced

            window.isNavigationBarContrastEnforced = false
            StatusBarIconRequests.updateBase(window, view, useLightIcons = isDarkTheme)
            insetsController.isAppearanceLightNavigationBars = !isDarkTheme

            onDispose {
                window.isNavigationBarContrastEnforced = previousNavigationBarContrast
                StatusBarIconRequests.updateBase(
                    window = window,
                    view = view,
                    useLightIcons = !previousLightStatusBars,
                )
                insetsController.isAppearanceLightNavigationBars = previousLightNavigationBars
            }
        }
    }
}

@Composable
actual fun StatusBarIconsEffect(useLightIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val window = view.context.findActivity()?.window ?: return
    val request = remember { Any() }

    SideEffect {
        StatusBarIconRequests.update(window, view, request, useLightIcons)
    }
    DisposableEffect(window, view, request) {
        onDispose {
            StatusBarIconRequests.remove(window, view, request)
        }
    }
}

private object StatusBarIconRequests {
    private data class WindowState(
        var baseUseLightIcons: Boolean,
        val requests: LinkedHashMap<Any, Boolean> = linkedMapOf(),
    )

    private val states = WeakHashMap<android.view.Window, WindowState>()

    fun updateBase(
        window: android.view.Window,
        view: android.view.View,
        useLightIcons: Boolean,
    ) {
        val controller = WindowCompat.getInsetsController(window, view)
        val state = states.getOrPut(window) {
            WindowState(baseUseLightIcons = useLightIcons)
        }
        state.baseUseLightIcons = useLightIcons
        apply(controller, state)
    }

    fun update(
        window: android.view.Window,
        view: android.view.View,
        request: Any,
        useLightIcons: Boolean,
    ) {
        val controller = WindowCompat.getInsetsController(window, view)
        val state = states.getOrPut(window) {
            WindowState(baseUseLightIcons = !controller.isAppearanceLightStatusBars)
        }
        state.requests[request] = useLightIcons
        apply(controller, state)
    }

    fun remove(
        window: android.view.Window,
        view: android.view.View,
        request: Any,
    ) {
        val state = states[window] ?: return
        state.requests.remove(request)
        apply(WindowCompat.getInsetsController(window, view), state)
    }

    private fun apply(
        controller: androidx.core.view.WindowInsetsControllerCompat,
        state: WindowState,
    ) {
        val useLightIcons = when {
            state.requests.values.any { it } -> true
            state.requests.isNotEmpty() -> false
            else -> state.baseUseLightIcons
        }
        controller.isAppearanceLightStatusBars = !useLightIcons
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
