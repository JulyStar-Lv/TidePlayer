package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.julystar.musicapp.core.presentation.media.ArtworkPalette
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

internal actual fun encodePlaylistCover(index: Int, palette: ArtworkPalette): ByteArray {
    val bitmap = ImageBitmap(512, 512)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(512f, 512f)) {
        drawPlaylistCoverPattern(index, playlistCoverColors(palette))
    }
    return ByteArrayOutputStream().apply { bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, this) }.toByteArray()
}
