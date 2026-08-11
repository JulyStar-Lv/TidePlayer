package io.github.julystar.musicapp.feature.settings.presentation

import kotlin.math.abs
import kotlin.math.round

internal fun formatDb(value: Int): String = "${if (value > 0) "+" else ""}$value dB"

internal fun formatTenthsDb(value: Int): String {
    val sign = when {
        value > 0 -> "+"
        value < 0 -> "-"
        else -> ""
    }
    val magnitude = abs(value)
    return "$sign${magnitude / 10}.${magnitude % 10} dB"
}

internal fun formatHundredths(value: Int): String =
    "${value / 100}.${abs(value % 100).toString().padStart(2, '0')}"

internal fun formatHundredthsMs(value: Int): String = "${formatHundredths(value)} ms"

internal fun formatMilliseconds(value: Int): String = "$value ms"

internal fun formatPercent(value: Int): String = "$value%"

internal fun formatHz(value: Int): String = when {
    value >= 1_000 -> {
        val tenths = value / 100
        if (tenths % 10 == 0) "${tenths / 10} kHz" else "${tenths / 10}.${tenths % 10} kHz"
    }
    else -> "$value Hz"
}

internal fun formatDegrees(value: Int): String = "$value°"

internal fun formatMeter(value: Float): String = "${round(value * 10f) / 10f} dB"
