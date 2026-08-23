package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.HsvHueSlider
import top.yukonga.miuix.kmp.basic.HsvSaturationSlider
import top.yukonga.miuix.kmp.basic.HsvValueSlider
import top.yukonga.miuix.kmp.color.api.toHsv
import top.yukonga.miuix.kmp.color.space.Hsv

@Composable
fun AppHsvColorPicker(
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hsv = color.toHsv()
    val saturation = hsv.s / 100f
    val value = hsv.v / 100f

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HsvHueSlider(
            currentHue = hsv.h,
            onHueChanged = { hue ->
                onColorChange(Hsv(hue * 360f, hsv.s, hsv.v).toColor())
            },
        )
        HsvSaturationSlider(
            currentHue = hsv.h,
            currentSaturation = saturation,
            onSaturationChanged = { newSaturation ->
                onColorChange(Hsv(hsv.h, newSaturation * 100f, hsv.v).toColor())
            },
        )
        HsvValueSlider(
            currentHue = hsv.h,
            currentSaturation = saturation,
            currentValue = value,
            onValueChanged = { newValue ->
                onColorChange(Hsv(hsv.h, hsv.s, newValue * 100f).toColor())
            },
        )
    }
}
