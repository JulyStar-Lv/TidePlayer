package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.julystar.musicapp.core.domain.repository.AudioDspFrequencyResponse
import musicapp.feature.settings.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.ln

@Composable
internal fun EqualizerFrequencyResponse(response: AudioDspFrequencyResponse) {
    val points = response.frequenciesHz.zip(response.gainsDb).filter { (frequency, gain) ->
        frequency.isFinite() && frequency > 0f && gain.isFinite()
    }
    if (points.size < 2) {
        Text(
            text = stringResource(Res.string.settings_eq_frequency_response_unavailable),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
            modifier = Modifier.padding(16.dp),
        )
        return
    }

    val curveColor = MiuixTheme.colorScheme.primary
    val guideColor = MiuixTheme.colorScheme.dividerLine
    val zeroColor = MiuixTheme.colorScheme.onSurface
    val labelColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = labelColor, fontSize = 9.sp)
    val yTicks = listOf(24f, 12f, 0f, -12f, -24f)
    val xTicks = listOf(31f, 62f, 125f, 250f, 500f, 1_000f, 2_000f, 4_000f, 8_000f, 16_000f)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (maxWidth >= 600.dp) 220.dp else 180.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            val plotLeft = 34.dp.toPx()
            val plotRight = size.width - 4.dp.toPx()
            val plotTop = 4.dp.toPx()
            val plotBottom = size.height - 22.dp.toPx()
            val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
            val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)
            val minimumFrequency = 20f
            val maximumFrequency = maxOf(20_000f, points.last().first)
            val logMin = ln(minimumFrequency)
            val logRange = ln(maximumFrequency) - logMin

            fun xFor(frequency: Float): Float = plotLeft +
                ((ln(frequency.coerceIn(minimumFrequency, maximumFrequency)) - logMin) / logRange) *
                plotWidth
            fun yFor(gain: Float): Float = plotTop + (24f - gain.coerceIn(-24f, 24f)) / 48f * plotHeight

            yTicks.forEach { gain ->
                val y = yFor(gain)
                drawLine(
                    color = if (gain == 0f) zeroColor.copy(alpha = 0.65f) else guideColor.copy(alpha = 0.5f),
                    start = Offset(plotLeft, y),
                    end = Offset(plotRight, y),
                    strokeWidth = if (gain == 0f) 1.5.dp.toPx() else 1.dp.toPx(),
                )
                val label = if (gain > 0) "+${gain.toInt()}" else gain.toInt().toString()
                val measured = textMeasurer.measure(label, labelStyle)
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    style = labelStyle,
                    topLeft = Offset(plotLeft - measured.size.width - 5.dp.toPx(), y - measured.size.height / 2f),
                )
            }

            xTicks.forEach { frequency ->
                val x = xFor(frequency)
                drawLine(
                    color = guideColor.copy(alpha = 0.22f),
                    start = Offset(x, plotTop),
                    end = Offset(x, plotBottom),
                    strokeWidth = 1.dp.toPx(),
                )
                val label = compactFrequencyLabel(frequency.toInt())
                val measured = textMeasurer.measure(label, labelStyle)
                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    style = labelStyle,
                    topLeft = Offset(
                        (x - measured.size.width / 2f).coerceIn(0f, size.width - measured.size.width),
                        plotBottom + 4.dp.toPx(),
                    ),
                )
            }

            val mapped = points.map { (frequency, gain) -> Offset(xFor(frequency), yFor(gain)) }
            val path = Path().apply {
                moveTo(mapped.first().x, mapped.first().y)
                for (index in 1..mapped.lastIndex) {
                    val previous = mapped[index - 1]
                    val current = mapped[index]
                    val midpoint = (previous.x + current.x) / 2f
                    cubicTo(midpoint, previous.y, midpoint, current.y, current.x, current.y)
                }
            }
            drawPath(path = path, color = curveColor, style = Stroke(width = 2.5.dp.toPx()))
        }
    }
}

internal fun compactFrequencyLabel(frequencyHz: Int): String = when {
    frequencyHz >= 1_000 -> "${frequencyHz / 1_000}k"
    else -> frequencyHz.toString()
}
