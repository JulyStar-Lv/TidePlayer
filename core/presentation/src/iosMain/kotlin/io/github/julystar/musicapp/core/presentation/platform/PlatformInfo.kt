package io.github.julystar.musicapp.core.presentation.platform

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

actual fun isDesktopPlatform(): Boolean = false

@Composable
actual fun isSystemDarkTheme(): Boolean = isSystemInDarkTheme()
