package io.github.julystar.musicapp.core.presentation.media

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

private const val PlayerBackgroundMaxBitmapSize = 32

@Composable
internal actual fun rememberPlayerBackgroundBitmap(bitmap: ImageBitmap): ImageBitmap =
    remember(bitmap) {
        val source = bitmap.asAndroidBitmap()
        val longestEdge = maxOf(source.width, source.height)
        if (longestEdge <= PlayerBackgroundMaxBitmapSize) {
            bitmap
        } else {
            val scale = PlayerBackgroundMaxBitmapSize.toFloat() / longestEdge
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).roundToInt().coerceAtLeast(1),
                (source.height * scale).roundToInt().coerceAtLeast(1),
                true,
            ).asImageBitmap()
        }
    }

internal actual fun Modifier.platformPlayerBackgroundBlur(radius: Dp): Modifier = this
