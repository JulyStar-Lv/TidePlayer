package io.github.julystar.musicapp.core.presentation.platform

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Space reserved for native window controls when desktop content extends into the title bar.
 */
val LocalDesktopTitleBarInset = staticCompositionLocalOf { 0.dp }

/** Account label shown by desktop navigation surfaces that mirror native macOS apps. */
val LocalDesktopAccountName = staticCompositionLocalOf { "" }
