package io.github.julystar.musicapp.core.presentation.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp

@Composable
internal actual fun rememberPlayerBackgroundBitmap(bitmap: ImageBitmap): ImageBitmap = bitmap

internal actual fun Modifier.platformPlayerBackgroundBlur(radius: Dp): Modifier = blur(radius)
