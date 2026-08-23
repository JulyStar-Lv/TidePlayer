package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

/** Defaults for TidePlayer's buffered, tappable playback progress control. */
object PlaybackSliderDefaults {
    val Height = 16.dp
    val TrackHeight = 4.dp
    val ThumbSize = 12.dp
    val ActiveThumbSize = 16.dp
}

@Composable
fun PlaybackSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tapToSeekEnabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    bufferedValue: Float? = null,
    onValueChangeStarted: (() -> Unit)? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    height: Dp = PlaybackSliderDefaults.Height,
    trackHeight: Dp = PlaybackSliderDefaults.TrackHeight,
    thumbSize: Dp = PlaybackSliderDefaults.ThumbSize,
    activeThumbSize: Dp = PlaybackSliderDefaults.ActiveThumbSize,
    trackColorOverride: Color? = null,
    bufferColorOverride: Color? = null,
    activeTrackColorOverride: Color? = null,
    thumbColorOverride: Color? = null,
) {
    require(steps >= 0) { "steps should be >= 0" }

    val motion = DesignTokens.motion
    val density = LocalDensity.current
    val isValueRangeValid = valueRange.start < valueRange.endInclusive
    val safeRange = if (isValueRangeValid) valueRange else 0f..1f
    val currentValue = if (isValueRangeValid) {
        value.coerceIn(valueRange.start, valueRange.endInclusive)
    } else {
        safeRange.start
    }
    val valueFraction = sliderFraction(currentValue, safeRange)
    val bufferFraction = bufferedValue?.let { sliderFraction(it, safeRange) }
    val canChange = enabled && isValueRangeValid

    var sliderWidthPx by remember { mutableIntStateOf(0) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var isChanging by remember { mutableStateOf(false) }
    val currentThumbSize by animateDpAsState(
        targetValue = if (isChanging) activeThumbSize else thumbSize,
        animationSpec = tween(durationMillis = motion.fastMillis),
        label = "playbackSliderThumbSize",
    )
    val sliderWidthDp = with(density) { sliderWidthPx.toDp() }

    fun updateValueFromOffset(offsetPx: Float) {
        if (!canChange || sliderWidthPx <= 0) return

        val fraction = (offsetPx / sliderWidthPx.toFloat()).coerceIn(0f, 1f)
        val nextValue = snappedSliderValue(
            value = safeRange.start + ((safeRange.endInclusive - safeRange.start) * fraction),
            valueRange = safeRange,
            steps = steps,
        )
        onValueChange(nextValue)
    }

    val draggableState = rememberDraggableState { deltaPx ->
        dragOffsetPx = (dragOffsetPx + deltaPx).coerceIn(0f, sliderWidthPx.toFloat())
        updateValueFromOffset(dragOffsetPx)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .onSizeChanged { sliderWidthPx = it.width }
            .then(
                if (canChange && tapToSeekEnabled) {
                    Modifier.pointerInput(safeRange, steps, sliderWidthPx) {
                        detectTapGestures { offset ->
                            isChanging = true
                            onValueChangeStarted?.invoke()
                            updateValueFromOffset(offset.x)
                            onValueChangeFinished?.invoke()
                            isChanging = false
                        }
                    }
                } else {
                    Modifier
                },
            )
            .draggable(
                enabled = canChange,
                state = draggableState,
                orientation = Orientation.Horizontal,
                onDragStarted = { offset ->
                    isChanging = true
                    onValueChangeStarted?.invoke()
                    dragOffsetPx = offset.x.coerceIn(0f, sliderWidthPx.toFloat())
                    updateValueFromOffset(dragOffsetPx)
                },
                onDragStopped = {
                    isChanging = false
                    onValueChangeFinished?.invoke()
                },
            )
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = currentValue,
                    range = safeRange.start..safeRange.endInclusive,
                    steps = if (steps > 0) steps else 0,
                )
                setProgress { target ->
                    if (!canChange) {
                        false
                    } else {
                        onValueChangeStarted?.invoke()
                        onValueChange(
                            snappedSliderValue(
                                value = target.coerceIn(safeRange.start, safeRange.endInclusive),
                                valueRange = safeRange,
                                steps = steps,
                            ),
                        )
                        onValueChangeFinished?.invoke()
                        true
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(DesignTokens.shapes.full))
                .background(trackColorOverride ?: trackColor(enabled)),
        ) {
            if (bufferFraction != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bufferFraction)
                        .fillMaxHeight()
                        .background(bufferColorOverride ?: bufferColor(enabled)),
                )
            }
            Box(
                modifier = Modifier
                        .fillMaxWidth(valueFraction)
                        .fillMaxHeight()
                        .then(
                            if (activeTrackColorOverride != null) {
                                Modifier.background(activeTrackColorOverride)
                            } else {
                                Modifier.background(fillBrush(enabled))
                            },
                        ),
            )
        }
        Box(
            modifier = Modifier
                .offset(
                    x = (sliderWidthDp * valueFraction) - (currentThumbSize / 2),
                    y = 0.dp,
                )
                .size(currentThumbSize)
                .clip(RoundedCornerShape(DesignTokens.shapes.full))
                .background(thumbColorOverride ?: thumbColor(enabled)),
        )
    }
}

private fun sliderFraction(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
): Float {
    val range = valueRange.endInclusive - valueRange.start
    if (range <= 0f) return 0f
    return ((value - valueRange.start) / range).coerceIn(0f, 1f)
}

private fun snappedSliderValue(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
): Float {
    if (steps == 0) return value.coerceIn(valueRange.start, valueRange.endInclusive)

    val intervalCount = steps + 1
    val fraction = sliderFraction(value, valueRange)
    val snappedFraction = (fraction * intervalCount).roundToInt() / intervalCount.toFloat()
    return valueRange.start + ((valueRange.endInclusive - valueRange.start) * snappedFraction)
}

@Composable
private fun trackColor(enabled: Boolean): Color = if (enabled) {
    MiuixTheme.colorScheme.sliderBackground
} else {
    MiuixTheme.colorScheme.disabledSecondary
}

@Composable
private fun bufferColor(enabled: Boolean): Color = if (enabled) {
    MiuixTheme.colorScheme.secondary
} else {
    MiuixTheme.colorScheme.disabledSecondaryVariant
}

@Composable
private fun thumbColor(enabled: Boolean): Color = if (enabled) {
    MiuixTheme.colorScheme.primary
} else {
    MiuixTheme.colorScheme.disabledOnPrimary
}

@Composable
private fun fillBrush(enabled: Boolean): Brush = if (enabled) {
    Brush.horizontalGradient(
        listOf(
            MiuixTheme.colorScheme.primary,
            MiuixTheme.colorScheme.secondary,
        ),
    )
} else {
    Brush.horizontalGradient(
        listOf(
            MiuixTheme.colorScheme.disabledPrimarySlider,
            MiuixTheme.colorScheme.disabledPrimarySlider,
        ),
    )
}
