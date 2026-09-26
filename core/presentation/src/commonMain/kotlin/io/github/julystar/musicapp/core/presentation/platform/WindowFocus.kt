package io.github.julystar.musicapp.core.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * Native desktop hosts provide the owning window's focus state here. A nullable default lets
 * previews and tests keep using Compose's own [LocalWindowInfo] value.
 */
val LocalDesktopWindowFocused = staticCompositionLocalOf<Boolean?> { null }

/**
 * Reports application-window focus while retaining Compose's window-info value as a fallback.
 *
 * Compose Desktop can leave [LocalWindowInfo.isWindowFocused] false after the native NSWindow has
 * become key, so the desktop application supplies the host platform's foreground/focus events.
 */
@Composable
fun rememberPlatformWindowFocused(): Boolean =
    LocalDesktopWindowFocused.current ?: LocalWindowInfo.current.isWindowFocused
