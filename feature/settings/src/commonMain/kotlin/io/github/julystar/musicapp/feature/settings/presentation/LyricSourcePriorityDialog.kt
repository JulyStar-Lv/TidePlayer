package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.julystar.musicapp.core.domain.model.LyricSourceKind
import io.github.julystar.musicapp.core.presentation.components.OverlayPresentationDefaults
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import musicapp.core.presentation.generated.resources.Res as CoreRes
import musicapp.core.presentation.generated.resources.icon_mode_list
import musicapp.core.presentation.generated.resources.icon_chevron_right
import musicapp.feature.settings.generated.resources.Res
import musicapp.feature.settings.generated.resources.icon_close
import musicapp.feature.settings.generated.resources.icon_move_to_top
import musicapp.feature.settings.generated.resources.settings_close
import musicapp.feature.settings.generated.resources.settings_lyrics_priority
import musicapp.feature.settings.generated.resources.settings_lyrics_priority_description
import musicapp.feature.settings.generated.resources.settings_lyrics_priority_drag
import musicapp.feature.settings.generated.resources.settings_lyrics_priority_move_down
import musicapp.feature.settings.generated.resources.settings_lyrics_priority_move_to_top
import musicapp.feature.settings.generated.resources.settings_lyrics_priority_move_up
import musicapp.feature.settings.generated.resources.settings_lyrics_priority_summary
import musicapp.feature.settings.generated.resources.settings_lyrics_priority_title

private val PriorityRowHeight = 56.dp

private data class LyricPriorityDragState(
    val source: LyricSourceKind,
    val currentIndex: Int,
    val offsetY: Float = 0f,
)

@Composable
internal fun LyricSourcePrioritySettingsRow(
    priority: List<LyricSourceKind>,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = stringResource(Res.string.settings_lyrics_priority),
        summary = stringResource(
            Res.string.settings_lyrics_priority_summary,
            priority.size,
        ),
        onClick = onClick,
        endActions = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(priority.first().titleResource()),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 176.dp),
                )
                Icon(
                    painter = painterResource(CoreRes.drawable.icon_chevron_right),
                    contentDescription = null,
                )
            }
        },
    )
    HorizontalDivider()
}

@Composable
internal fun LyricSourcePriorityDialog(
    show: Boolean,
    priority: List<LyricSourceKind>,
    onPriorityChange: (List<LyricSourceKind>) -> Unit,
    onDismiss: () -> Unit,
) {
    var displayedPriority by remember(show) { mutableStateOf(priority) }
    var dragState by remember(show) { mutableStateOf<LyricPriorityDragState?>(null) }
    val rowHeightPx = with(LocalDensity.current) { PriorityRowHeight.toPx() }
    val compact = OverlayPresentationDefaults.isCompactWindow(
        LocalWindowInfo.current.containerDpSize.width,
    )

    fun updatePriority(updated: List<LyricSourceKind>) {
        displayedPriority = updated
        onPriorityChange(updated)
    }

    fun moveSource(fromIndex: Int, toIndex: Int) {
        val updated = displayedPriority.moveLyricSource(fromIndex, toIndex)
        if (updated !== displayedPriority) updatePriority(updated)
    }

    fun dragSource(deltaY: Float) {
        val active = dragState ?: return
        var currentIndex = active.currentIndex
        var offsetY = active.offsetY + deltaY
        var updated = displayedPriority

        while (offsetY > rowHeightPx / 2f && currentIndex < updated.lastIndex) {
            updated = updated.moveLyricSource(currentIndex, currentIndex + 1)
            currentIndex += 1
            offsetY -= rowHeightPx
        }
        while (offsetY < -rowHeightPx / 2f && currentIndex > 0) {
            updated = updated.moveLyricSource(currentIndex, currentIndex - 1)
            currentIndex -= 1
            offsetY += rowHeightPx
        }

        dragState = active.copy(currentIndex = currentIndex, offsetY = offsetY)
        if (updated !== displayedPriority) displayedPriority = updated
    }

    OverlayDialog(
        show = show,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.settings_lyrics_priority_title),
                    color = MiuixTheme.colorScheme.onSurface,
                    style = MiuixTheme.textStyles.title2.copy(
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                    ),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(Res.string.settings_lyrics_priority_description),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1.copy(
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    ),
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                )
            }
            if (!compact) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.icon_close),
                        contentDescription = stringResource(Res.string.settings_close),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            displayedPriority.forEachIndexed { index, source ->
                val isDragged = dragState?.source == source
                key(source) {
                    LyricSourcePriorityRow(
                        source = source,
                        index = index,
                        isDragged = isDragged,
                        dragOffsetY = dragState?.takeIf { it.source == source }?.offsetY ?: 0f,
                        interactionsEnabled = dragState == null || isDragged,
                        onMoveToTop = {
                            dragState = null
                            moveSource(index, 0)
                        },
                        onMoveUp = if (index > 0) {
                            { moveSource(index, index - 1) }
                        } else {
                            null
                        },
                        onMoveDown = if (index < displayedPriority.lastIndex) {
                            { moveSource(index, index + 1) }
                        } else {
                            null
                        },
                        onDragStart = {
                            dragState = LyricPriorityDragState(
                                source = source,
                                currentIndex = index,
                            )
                        },
                        onDrag = ::dragSource,
                        onDragEnd = {
                            onPriorityChange(displayedPriority)
                            dragState = null
                        },
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun LyricSourcePriorityRow(
    source: LyricSourceKind,
    index: Int,
    isDragged: Boolean,
    dragOffsetY: Float,
    interactionsEnabled: Boolean,
    onMoveToTop: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val sourceTitle = stringResource(source.titleResource())
    val rowShape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .zIndex(if (isDragged) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffsetY
                scaleX = if (isDragged) 1.01f else 1f
                scaleY = if (isDragged) 1.01f else 1f
                shadowElevation = if (isDragged) 8.dp.toPx() else 0f
                shape = rowShape
            }
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(PriorityRowHeight)
                .background(
                    color = if (isDragged) {
                        MiuixTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MiuixTheme.colorScheme.surfaceContainer
                    },
                    shape = rowShape,
                )
                .padding(end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Box(
            modifier = Modifier.width(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (index + 1).toString(),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote2.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            text = sourceTitle,
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.body1.copy(fontSize = 14.sp, lineHeight = 18.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (index == 0) {
            Spacer(modifier = Modifier.size(40.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(enabled = interactionsEnabled, onClick = onMoveToTop),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.icon_move_to_top),
                    contentDescription = stringResource(
                        Res.string.settings_lyrics_priority_move_to_top,
                        sourceTitle,
                    ),
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        LyricPriorityDragHandle(
            sourceTitle = sourceTitle,
            enabled = interactionsEnabled,
            isDragged = isDragged,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onDragStart = onDragStart,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
        )
        }
        HorizontalDivider()
    }
}

@Composable
private fun LyricPriorityDragHandle(
    sourceTitle: String,
    enabled: Boolean,
    isDragged: Boolean,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val moveUpLabel = stringResource(Res.string.settings_lyrics_priority_move_up)
    val moveDownLabel = stringResource(Res.string.settings_lyrics_priority_move_down)
    val reorderLabel = stringResource(Res.string.settings_lyrics_priority_drag, sourceTitle)
    val accessibilityActions = buildList {
        onMoveUp?.let { action -> add(CustomAccessibilityAction(moveUpLabel) { action(); true }) }
        onMoveDown?.let { action -> add(CustomAccessibilityAction(moveDownLabel) { action(); true }) }
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .focusable(enabled = enabled)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || !enabled) {
                    false
                } else {
                    when (event.key) {
                        Key.DirectionUp -> onMoveUp?.let { it(); true } ?: false
                        Key.DirectionDown -> onMoveDown?.let { it(); true } ?: false
                        else -> false
                    }
                }
            }
            .semantics {
                contentDescription = reorderLabel
                customActions = accessibilityActions
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    orientationLock = Orientation.Vertical,
                    shouldAwaitTouchSlop = { false },
                    onDragStart = { _, _, _ -> onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = onDragEnd,
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.y)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(CoreRes.drawable.icon_mode_list),
            contentDescription = null,
            tint = if (isDragged) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
            modifier = Modifier.size(18.dp),
        )
    }
}

internal fun List<LyricSourceKind>.moveLyricSource(
    fromIndex: Int,
    toIndex: Int,
): List<LyricSourceKind> {
    if (fromIndex == toIndex || fromIndex !in indices || toIndex !in indices) return this
    return toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}
