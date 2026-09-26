package io.github.julystar.musicapp.core.presentation.platform

import androidx.compose.runtime.Composable

expect fun isDesktopPlatform(): Boolean

@Composable
expect fun isSystemDarkTheme(): Boolean
