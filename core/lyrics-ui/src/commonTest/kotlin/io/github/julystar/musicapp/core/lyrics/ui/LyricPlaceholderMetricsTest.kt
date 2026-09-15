package io.github.julystar.musicapp.core.lyrics.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals

class LyricPlaceholderMetricsTest {
    private val density = Density(density = 1f, fontScale = 1f)

    @Test
    fun unspecifiedLineHeightFallsBackToFontRelativeHeight() {
        val metrics = density.lyricPlaceholderMetrics(TextStyle(fontSize = 32.sp))

        assertEquals(40f, metrics.lineHeight.value)
        assertEquals(19.84f, metrics.dotSize.value)
        assertEquals(15.36f, metrics.dotSpacing.value)
    }

    @Test
    fun emLineHeightResolvesRelativeToFontSize() {
        val metrics = density.lyricPlaceholderMetrics(
            TextStyle(fontSize = 32.sp, lineHeight = 1.5.em),
        )

        assertEquals(48f, metrics.lineHeight.value)
    }
}
