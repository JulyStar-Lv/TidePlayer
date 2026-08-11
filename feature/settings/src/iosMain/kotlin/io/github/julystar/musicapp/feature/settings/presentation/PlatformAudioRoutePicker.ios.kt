package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVKit.AVRoutePickerView

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformAudioRoutePicker(modifier: Modifier) {
    UIKitView(
        factory = { AVRoutePickerView() },
        modifier = modifier,
    )
}
