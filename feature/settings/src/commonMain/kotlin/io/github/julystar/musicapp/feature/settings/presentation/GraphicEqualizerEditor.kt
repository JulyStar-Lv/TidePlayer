package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.domain.model.GraphicEqualizerSettings
import io.github.julystar.musicapp.core.domain.model.MAX_EQ_BAND_GAIN_DB
import io.github.julystar.musicapp.core.domain.model.MIN_EQ_BAND_GAIN_DB
import musicapp.feature.settings.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private val GRAPHIC_EQ_FREQUENCIES_HZ =
    listOf(31, 62, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000)

@Composable
internal fun GraphicEqualizerEditor(
    settings: GraphicEqualizerSettings,
    enabled: Boolean,
    onUpdate: (GraphicEqualizerSettings) -> Unit,
) {
    var preciseBandIndex by remember { mutableStateOf<Int?>(null) }
    var preciseGain by remember { mutableIntStateOf(0) }
    val gains = List(GRAPHIC_EQ_FREQUENCIES_HZ.size) { index ->
        settings.bandGainsDb.getOrElse(index) { 0 }
    }

    fun updateBand(index: Int, gain: Int) {
        val clamped = gain.coerceIn(MIN_EQ_BAND_GAIN_DB, MAX_EQ_BAND_GAIN_DB)
        if (gains[index] == clamped) return
        onUpdate(
            settings.copy(
                bandGainsDb = gains.toMutableList().apply { this[index] = clamped },
            ),
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(
            modifier = Modifier
                .width(30.dp)
                .height(238.dp)
                .padding(top = 21.dp, bottom = 1.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = "+12",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = "0",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = "−12",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(268.dp),
        ) {
            val baselineColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(238.dp),
            ) {
                val top = 28.dp.toPx()
                val bottom = size.height - 8.dp.toPx()
                val zero = (top + bottom) / 2f
                drawLine(
                    color = baselineColor,
                    start = androidx.compose.ui.geometry.Offset(0f, zero),
                    end = androidx.compose.ui.geometry.Offset(size.width, zero),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                GRAPHIC_EQ_FREQUENCIES_HZ.forEachIndexed { index, frequency ->
                    val frequencyLabel = compactFrequencyLabel(frequency)
                    val fullFrequencyLabel = formatHz(frequency)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        VerticalEqualizerBand(
                            frequencyLabel = fullFrequencyLabel,
                            value = gains[index],
                            enabled = enabled,
                            onValueChange = { updateBand(index, it) },
                            onValueChangeFinished = { updateBand(index, it) },
                            onRequestPreciseAdjustment = { gain ->
                                preciseGain = gain
                                preciseBandIndex = index
                            },
                            onReset = { updateBand(index, 0) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(238.dp),
                        )
                        Text(
                            text = frequencyLabel,
                            style = MiuixTheme.textStyles.footnote2,
                            color = if (enabled) {
                                MiuixTheme.colorScheme.onSurfaceVariantSummary
                            } else {
                                MiuixTheme.colorScheme.disabledSecondary
                            },
                        )
                    }
                }
            }
        }
    }

    val selectedIndex = preciseBandIndex
    val decreaseGainDescription = stringResource(Res.string.settings_eq_decrease_gain)
    val increaseGainDescription = stringResource(Res.string.settings_eq_increase_gain)
    OverlayDialog(
        show = selectedIndex != null,
        onDismissRequest = { preciseBandIndex = null },
    ) {
        if (selectedIndex != null) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = formatHz(GRAPHIC_EQ_FREQUENCIES_HZ[selectedIndex]),
                    style = MiuixTheme.textStyles.title3,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.settings_eq_precise_adjustment),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        text = "−",
                        enabled = preciseGain > MIN_EQ_BAND_GAIN_DB,
                        modifier = Modifier.semantics {
                            contentDescription = decreaseGainDescription
                        },
                        onClick = {
                            preciseGain = (preciseGain - 1).coerceAtLeast(MIN_EQ_BAND_GAIN_DB)
                            updateBand(selectedIndex, preciseGain)
                        },
                    )
                    Text(
                        text = formatDb(preciseGain),
                        style = MiuixTheme.textStyles.title2,
                        color = MiuixTheme.colorScheme.primary,
                    )
                    TextButton(
                        text = "+",
                        enabled = preciseGain < MAX_EQ_BAND_GAIN_DB,
                        modifier = Modifier.semantics {
                            contentDescription = increaseGainDescription
                        },
                        onClick = {
                            preciseGain = (preciseGain + 1).coerceAtMost(MAX_EQ_BAND_GAIN_DB)
                            updateBand(selectedIndex, preciseGain)
                        },
                    )
                }
                TextButton(
                    text = stringResource(Res.string.settings_eq_reset_band),
                    modifier = Modifier.align(Alignment.End),
                    onClick = {
                        preciseGain = 0
                        updateBand(selectedIndex, 0)
                    },
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun VerticalEqualizerBand(
    frequencyLabel: String,
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: (Int) -> Unit,
    onRequestPreciseAdjustment: (Int) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var heightPx by remember { mutableIntStateOf(0) }
    var previewValue by remember { mutableIntStateOf(value) }
    var dragY by remember { mutableStateOf(0f) }
    var changing by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val trackTopPx = with(density) { 28.dp.toPx() }
    val trackBottomPaddingPx = with(density) { 8.dp.toPx() }
    val primary = MiuixTheme.colorScheme.primary
    val track = MiuixTheme.colorScheme.sliderBackground
    val inactive = MiuixTheme.colorScheme.disabledSecondary
    val gainLabel = formatDb(previewValue)
    val accessibilityLabel = stringResource(
        Res.string.settings_eq_gain_accessibility,
        frequencyLabel,
        gainLabel,
    )
    val preciseAction = stringResource(Res.string.settings_eq_precise_adjustment)

    LaunchedEffect(value, changing) {
        if (!changing) previewValue = value
    }

    fun yFor(current: Int): Float {
        val bottom = heightPx - trackBottomPaddingPx
        val height = (bottom - trackTopPx).coerceAtLeast(1f)
        val fraction = (MAX_EQ_BAND_GAIN_DB - current).toFloat() /
            (MAX_EQ_BAND_GAIN_DB - MIN_EQ_BAND_GAIN_DB)
        return trackTopPx + height * fraction
    }

    fun updateFromY(y: Float, finished: Boolean = false) {
        if (!enabled || heightPx <= 0) return
        val bottom = heightPx - trackBottomPaddingPx
        val trackHeight = (bottom - trackTopPx).coerceAtLeast(1f)
        val fraction = ((y - trackTopPx) / trackHeight).coerceIn(0f, 1f)
        val next = (MAX_EQ_BAND_GAIN_DB -
            fraction * (MAX_EQ_BAND_GAIN_DB - MIN_EQ_BAND_GAIN_DB)).roundToInt()
        if (previewValue != next) {
            previewValue = next
            onValueChange(next)
        }
        if (finished) onValueChangeFinished(previewValue)
    }

    val draggableState = rememberDraggableState { delta ->
        dragY = (dragY + delta).coerceIn(trackTopPx, heightPx - trackBottomPaddingPx)
        updateFromY(dragY)
    }

    Box(
        modifier = modifier
            .onSizeChanged { heightPx = it.height }
            .hoverable(interactionSource, enabled)
            .pointerInput(enabled) {
                if (enabled) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val scrollY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                if (scrollY != 0f) {
                                    val delta = if (scrollY < 0f) 1 else -1
                                    val next = (previewValue + delta).coerceIn(
                                        MIN_EQ_BAND_GAIN_DB,
                                        MAX_EQ_BAND_GAIN_DB,
                                    )
                                    previewValue = next
                                    onValueChangeFinished(next)
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
                }
            }
            .pointerInput(enabled, heightPx) {
                if (enabled) {
                    detectTapGestures(
                        onTap = { offset -> updateFromY(offset.y, finished = true) },
                        onDoubleTap = { onReset() },
                        onLongPress = { onRequestPreciseAdjustment(previewValue) },
                    )
                }
            }
            .draggable(
                enabled = enabled,
                state = draggableState,
                orientation = Orientation.Vertical,
                onDragStarted = {
                    changing = true
                    dragY = yFor(previewValue)
                },
                onDragStopped = {
                    changing = false
                    onValueChangeFinished(previewValue)
                },
            )
            .onKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> {
                        val next = (previewValue + 1).coerceAtMost(MAX_EQ_BAND_GAIN_DB)
                        previewValue = next
                        onValueChangeFinished(next)
                        true
                    }
                    Key.DirectionDown -> {
                        val next = (previewValue - 1).coerceAtLeast(MIN_EQ_BAND_GAIN_DB)
                        previewValue = next
                        onValueChangeFinished(next)
                        true
                    }
                    else -> false
                }
            }
            .focusable(enabled)
            .semantics {
                contentDescription = accessibilityLabel
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = previewValue.toFloat(),
                    range = MIN_EQ_BAND_GAIN_DB.toFloat()..MAX_EQ_BAND_GAIN_DB.toFloat(),
                    steps = MAX_EQ_BAND_GAIN_DB - MIN_EQ_BAND_GAIN_DB - 1,
                )
                setProgress { target ->
                    if (!enabled) return@setProgress false
                    val next = target.roundToInt().coerceIn(
                        MIN_EQ_BAND_GAIN_DB,
                        MAX_EQ_BAND_GAIN_DB,
                    )
                    previewValue = next
                    onValueChangeFinished(next)
                    true
                }
                onLongClick(label = preciseAction) {
                    onRequestPreciseAdjustment(previewValue)
                    true
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val x = size.width / 2f
            val bottom = size.height - trackBottomPaddingPx
            val thumbY = yFor(previewValue)
            val zeroY = yFor(0)
            drawLine(
                color = if (enabled) track else inactive,
                start = androidx.compose.ui.geometry.Offset(x, trackTopPx),
                end = androidx.compose.ui.geometry.Offset(x, bottom),
                strokeWidth = 3.dp.toPx(),
            )
            drawLine(
                color = if (enabled) primary else inactive,
                start = androidx.compose.ui.geometry.Offset(x, thumbY),
                end = androidx.compose.ui.geometry.Offset(x, zeroY),
                strokeWidth = 4.dp.toPx(),
            )
            drawCircle(
                color = if (enabled) primary else inactive,
                radius = if (changing || hovered) 8.dp.toPx() else 6.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(x, thumbY),
            )
        }
        if (changing) {
            val bubbleOffset = with(density) { (yFor(previewValue) - 25.dp.toPx()).toDp() }
            Box(
                modifier = Modifier
                    .offset(y = bubbleOffset)
                    .clip(RoundedCornerShape(10.dp))
                    .background(primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = gainLabel,
                    style = MiuixTheme.textStyles.footnote2,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}
