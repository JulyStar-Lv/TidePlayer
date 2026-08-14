package io.github.julystar.musicapp.core.presentation.platform

import androidx.compose.runtime.Composable

@Composable
expect fun SystemBarsEffect(isDarkTheme: Boolean)

@Composable
expect fun StatusBarIconsEffect(useLightIcons: Boolean)
