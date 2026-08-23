package io.github.julystar.musicapp.service.playback.presentation.sleep

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import musicapp.service.playback.presentation.generated.resources.Res
import musicapp.service.playback.presentation.generated.resources.playlists_dialog_button_cancel
import musicapp.service.playback.presentation.generated.resources.playlists_dialog_button_ok
import musicapp.service.playback.presentation.generated.resources.time_to_pause_delete
import musicapp.service.playback.presentation.generated.resources.time_to_pause_hour
import musicapp.service.playback.presentation.generated.resources.time_to_pause_minute
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

@Composable
private fun Block(
    stringRes: StringResource,
    l: Int,
    r: Int,
    current: Int,
    onChange: (value: Int) -> Unit
) {
    val BOX_WIDTH = 56.dp
    val BOX_HEIGHT = 150.dp
    val STRIDE = 50;
    var dragOffsetInDp by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val rng = r - l + 1;

    fun next(x: Int): Int {
        return ((current + x - l) % rng + rng) % rng + l;
    }

    val consumeDragOffsetInDp = {
        val gapFloor = floor(dragOffsetInDp / STRIDE).toInt()
        val gapCeil = ceil(dragOffsetInDp / STRIDE).toInt()

        var gap = 0
        var isFloor = false
        if (dragOffsetInDp - gapFloor * STRIDE < gapCeil * STRIDE - dragOffsetInDp) {
            gap = gapFloor
            isFloor = true
        } else {
            gap = gapCeil
            isFloor = false
        }

        if (gap != 0) {
            val next = next(-gap);

            if (isFloor) {
                dragOffsetInDp -= gapFloor * STRIDE;
            } else {
                dragOffsetInDp -= gapCeil * STRIDE;
            }
            onChange(next)
        }
    }

    val draggableState = rememberDraggableState { deltaPx ->
        dragOffsetInDp += with(density) { deltaPx.toDp().value }

        consumeDragOffsetInDp()
    }

    // Coroutine scope to manage the animation
    val animationScope = rememberCoroutineScope()
    var animationJob by remember { mutableStateOf<Job?>(null) }

    val startAnimateDragOffsetToZero = {
        // Cancel any ongoing animation
        animationJob?.cancel()

        consumeDragOffsetInDp()

        // Start a new animation
        animationJob = animationScope.launch {
            animate(dragOffsetInDp, 0f, animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)) { value, _ ->
                dragOffsetInDp = value
            }
        }
    }
    val abortAnimateDragOffset = {
        // Cancel any ongoing animation when dragging starts
        animationJob?.cancel()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(stringRes),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote2,
        )
        Box(
            modifier = Modifier
                .width(BOX_WIDTH)
                .height(BOX_HEIGHT)
                .clipToBounds()
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Vertical,
                    onDragStarted = {
                        abortAnimateDragOffset()
                    },
                    onDragStopped = {
                        startAnimateDragOffsetToZero()
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            for (i in -2..2) {
                val dis = (i * STRIDE) + dragOffsetInDp
                val offsetY = dis.dp + 10.dp
                val color = MiuixTheme.colorScheme.onSurface.copy(
                    alpha = 1 - 0.5f * min(dis.absoluteValue / STRIDE, 1f)
                )
                val textScale = 1.28f - 0.21f * min(dis.absoluteValue / STRIDE, 1f)

                Text(
                    modifier = Modifier
                        .padding(vertical = 16.dp)
                        .offset(0.dp, offsetY)
                        .graphicsLayer {
                            scaleX = textScale
                            scaleY = textScale
                        },
                    style = MiuixTheme.textStyles.title1,
                    color = color,
                    text = next(i).toString().padStart(2, '0'),
                )
            }
        }
    }
}

@Composable
private fun TimeToPauseModalCore(
    isOpen: Boolean,
    initHours: Int,
    initMinutes: Int,
    deleteEnabled: Boolean,
    onCancel: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = DesignTokens.spacing
    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(0) }

    LaunchedEffect(isOpen) {
        hours = initHours
        minutes = initMinutes
    }

    OverlayDialog(show = isOpen, onDismissRequest = onCancel) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
            ) {
                Block(
                    stringRes = Res.string.time_to_pause_hour,
                    l = 0,
                    r = 99,
                    current = hours,
                    onChange = { value -> hours = value }
                )
                Box(
                    modifier = Modifier
                        .width(spacing.md)
                )
                Block(
                    stringRes = Res.string.time_to_pause_minute,
                    l = 0,
                    r = 59,
                    current = minutes,
                    onChange = { value -> minutes = value }
                )
            }
            Box(
                modifier = Modifier
                    .height(spacing.md)
            )
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Row {
                    TextButton(
                        text = stringResource(Res.string.time_to_pause_delete),
                        enabled = deleteEnabled,
                        onClick = {
                            onDelete()
                        }
                    )
                }
                Row {
                    TextButton(
                        text = stringResource(Res.string.playlists_dialog_button_cancel),
                        onClick = {
                            onCancel()
                        }
                    )
                    TextButton(
                        text = stringResource(Res.string.playlists_dialog_button_ok),
                        enabled = !(minutes == 0 && hours == 0),
                        onClick = {
                            onConfirm(hours, minutes)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TimeToPauseModal(sleepModeVM: SleepModeVM = koinViewModel()) {
    val state by sleepModeVM.state.collectAsState()
    val modalOpen by sleepModeVM.modalOpen.collectAsState()
    val editLeftTime by sleepModeVM.editLeftTime.collectAsState()

    val onClose = {
        sleepModeVM.closeModal()
    }

    TimeToPauseModalCore(
        isOpen = modalOpen,
        initHours = editLeftTime.hour,
        initMinutes = editLeftTime.minute,
        deleteEnabled = state.enabled,
        onCancel = onClose,
        onConfirm = { hour, minute ->
            sleepModeVM.set(hour, minute)
            onClose()
        },
        onDelete = {
            sleepModeVM.remove()
            onClose()
        }
    )
}
