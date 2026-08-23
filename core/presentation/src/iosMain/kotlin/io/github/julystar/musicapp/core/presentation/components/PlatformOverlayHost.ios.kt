package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

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
        ),
        content = content,
    )
}

@Composable
internal actual fun PlatformOverlaySystemBarsEffect(
    navigationBarStyle: PlatformOverlayNavigationBarStyle,
) = Unit
