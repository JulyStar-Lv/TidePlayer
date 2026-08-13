package io.github.julystar.musicapp.core.presentation.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp

@Composable
internal expect fun rememberPlayerBackgroundBitmap(bitmap: ImageBitmap): ImageBitmap

internal expect fun Modifier.platformPlayerBackgroundBlur(radius: Dp): Modifier
