package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.runtime.Composable

enum class PlatformOverlayNavigationBarStyle {
    Dimmed,
    Surface,
}

@Composable
expect fun PlatformOverlayHost(
    onDismissRequest: () -> Unit,
    dismissOnClickOutside: Boolean = true,
    navigationBarStyle: PlatformOverlayNavigationBarStyle = PlatformOverlayNavigationBarStyle.Dimmed,
    content: @Composable () -> Unit,
)

@Composable
internal expect fun PlatformOverlaySystemBarsEffect(
    navigationBarStyle: PlatformOverlayNavigationBarStyle,
)
