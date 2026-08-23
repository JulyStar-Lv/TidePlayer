package io.github.julystar.musicapp.feature.queue.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.julystar.musicapp.core.presentation.components.PlatformOverlayHost
import io.github.julystar.musicapp.core.presentation.components.PlatformOverlayNavigationBarStyle
import io.github.julystar.musicapp.core.presentation.components.OverlayPresentationDefaults
import io.github.julystar.musicapp.core.presentation.components.OverlayBottomSheetDefaults
import io.github.julystar.musicapp.core.presentation.components.OverlayBottomSheetHandle
import io.github.julystar.musicapp.core.presentation.components.shouldDismissOverlayBottomSheet
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import musicapp.core.presentation.generated.resources.Res as CoreRes
import musicapp.core.presentation.generated.resources.icon_deleteseep
import musicapp.core.presentation.generated.resources.icon_mode_list
import musicapp.feature.queue.generated.resources.Res as QueueRes
import musicapp.feature.queue.generated.resources.icon_locate
import musicapp.feature.queue.generated.resources.icon_queue_trash
import musicapp.feature.queue.generated.resources.queue_clear
import musicapp.feature.queue.generated.resources.queue_empty
import musicapp.feature.queue.generated.resources.queue_locate_current
import musicapp.feature.queue.generated.resources.queue_move_down
import musicapp.feature.queue.generated.resources.queue_move_up
import musicapp.feature.queue.generated.resources.queue_reorder_item
import musicapp.feature.queue.generated.resources.queue_remove_item
import musicapp.feature.queue.generated.resources.queue_title
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private val QueueSideDialogWidth = 480.dp
private val QueueBottomDialogMaxWidth = 680.dp
private val QueueBottomDialogMaxHeight = 720.dp
private val QueueWideMinWidth = 860.dp
private val QueueWideMinHeight = 520.dp
private val QueueLandscapeMinWidth = 640.dp
private val NowPlayingContentStartPadding = 34.dp
private val NowPlayingContentEndPadding = 28.dp
private val NowPlayingColumnsGap = 34.dp
private const val NowPlayingLyricsWeight = 0.54f
private const val QueueSideDialogEnterDurationMillis = 240
private const val QueueSideDialogExitDurationMillis = 180
private const val QueueDragAutoScrollIntervalMillis = 16L

/** Matches the maintained Design player breakpoints for the queue surface. */
internal fun isQueueSideDialog(maxWidth: androidx.compose.ui.unit.Dp, maxHeight: androidx.compose.ui.unit.Dp): Boolean =
    (maxWidth >= QueueWideMinWidth && maxHeight >= QueueWideMinHeight) ||
        (
            maxWidth >= QueueLandscapeMinWidth &&
                maxWidth > maxHeight &&
                maxHeight < QueueWideMinHeight
            )

/** Matches the desktop [LyricsSurface] width so the queue fully replaces that column. */
internal fun nowPlayingLyricsPanelWidth(maxWidth: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp =
    (maxWidth - NowPlayingContentStartPadding - NowPlayingContentEndPadding - NowPlayingColumnsGap) *
        NowPlayingLyricsWeight + NowPlayingContentEndPadding

@Composable
fun QueueDialog(
    state: QueueState,
    coverNowPlayingLyrics: Boolean,
    onDismiss: () -> Unit,
    onAction: (QueueAction) -> Unit,
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val autoScrollEdgePx = with(density) { 48.dp.toPx() }
    val autoScrollStepPx = with(density) { 8.dp.toPx() }
    val dismissDistancePx = with(density) { OverlayBottomSheetDefaults.dismissDistance.toPx() }
    val dismissVelocityPxPerSecond = with(density) { OverlayBottomSheetDefaults.dismissVelocity.toPx() }
    var displayItems: List<QueueItemUi> by remember { mutableStateOf(state.items) }
    var dragState by remember { mutableStateOf<QueueDragState?>(null) }
    val hasCurrentItem = state.currentIndex in state.items.indices
    var contentVisible by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }
    var sheetDragOffsetPx by remember { mutableFloatStateOf(0f) }
    var sheetDragAnimationJob by remember { mutableStateOf<Job?>(null) }
    val sheetDraggableState = rememberDraggableState { deltaPx ->
        sheetDragOffsetPx = (sheetDragOffsetPx + deltaPx).coerceAtLeast(0f)
    }

    fun cancelDrag() {
        dragState = null
        displayItems = state.items
    }

    fun requestDismiss() {
        if (dismissing) return
        cancelDrag()
        sheetDragAnimationJob?.cancel()
        dismissing = true
        contentVisible = false
        coroutineScope.launch {
            delay(OverlayBottomSheetDefaults.exitDurationMillis.toLong())
            onDismiss()
        }
    }

    LaunchedEffect(Unit) {
        contentVisible = true
    }

    LaunchedEffect(state.items) {
        if (dragState != null) dragState = null
        displayItems = state.items
    }

    LaunchedEffect(dragState?.originalIndex) {
        while (dragState != null) {
            val scrollDelta = dragState?.autoScrollDelta ?: 0f
            if (scrollDelta != 0f) listState.scrollBy(scrollDelta)
            delay(QueueDragAutoScrollIntervalMillis)
        }
    }

    PlatformOverlayHost(
        onDismissRequest = ::requestDismiss,
        dismissOnClickOutside = false,
        navigationBarStyle = PlatformOverlayNavigationBarStyle.Surface,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(OverlayBottomSheetDefaults.enterDurationMillis)),
                exit = fadeOut(tween(OverlayBottomSheetDefaults.exitDurationMillis)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(OverlayPresentationDefaults.scrimColor)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { requestDismiss() })
                        },
                )
            }

            val sideDialog = isQueueSideDialog(maxWidth, maxHeight)
            val coversDesktopLyrics = coverNowPlayingLyrics &&
                maxWidth >= QueueWideMinWidth &&
                maxHeight >= QueueWideMinHeight
            val surfaceShape: Shape = if (sideDialog) {
                RectangleShape
            } else {
                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            }
            val surfaceModifier = if (sideDialog) {
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(
                        if (coversDesktopLyrics) {
                            nowPlayingLyricsPanelWidth(maxWidth)
                        } else {
                            QueueSideDialogWidth
                        },
                    )
                    .fillMaxHeight()
            } else {
                Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = QueueBottomDialogMaxWidth)
                    .fillMaxWidth()
                    .height(minOf(maxHeight * 0.76f, QueueBottomDialogMaxHeight))
            }

            val sheetHandleDragModifier = if (sideDialog) {
                Modifier
            } else {
                Modifier.draggable(
                    state = sheetDraggableState,
                    orientation = Orientation.Vertical,
                    enabled = !dismissing,
                    onDragStarted = {
                        sheetDragAnimationJob?.cancel()
                    },
                    onDragStopped = { velocity ->
                        if (
                            shouldDismissOverlayBottomSheet(
                                dragOffsetPx = sheetDragOffsetPx,
                                velocityPxPerSecond = velocity,
                                distanceThresholdPx = dismissDistancePx,
                                velocityThresholdPxPerSecond = dismissVelocityPxPerSecond,
                            )
                        ) {
                            requestDismiss()
                        } else {
                            sheetDragAnimationJob?.cancel()
                            sheetDragAnimationJob = coroutineScope.launch {
                                animate(
                                    initialValue = sheetDragOffsetPx,
                                    targetValue = 0f,
                                    animationSpec = spring(),
                                ) { value, _ ->
                                    sheetDragOffsetPx = value
                                }
                            }
                        }
                    },
                )
            }

            AnimatedVisibility(
                visible = contentVisible,
                modifier = surfaceModifier,
                enter = if (sideDialog) {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(
                            QueueSideDialogEnterDurationMillis,
                            easing = FastOutSlowInEasing,
                        ),
                    ) + fadeIn(tween(QueueSideDialogEnterDurationMillis))
                } else {
                    OverlayBottomSheetDefaults.surfaceEnterTransition()
                },
                exit = if (sideDialog) {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = tween(
                            QueueSideDialogExitDurationMillis,
                            easing = FastOutSlowInEasing,
                        ),
                    ) + fadeOut(tween(QueueSideDialogExitDurationMillis))
                } else {
                    OverlayBottomSheetDefaults.surfaceExitTransition()
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset {
                            IntOffset(
                                x = 0,
                                y = if (sideDialog) 0 else sheetDragOffsetPx.roundToInt(),
                            )
                        }
                        .then(
                            if (sideDialog) {
                                Modifier.shadow(
                                    elevation = DesignTokens.elevation.overlay,
                                    shape = RectangleShape,
                                )
                            } else {
                                Modifier
                            },
                        )
                        .clip(surfaceShape)
                        .background(MiuixTheme.colorScheme.surfaceContainer)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) awaitPointerEvent()
                            }
                        }
                        .navigationBarsPadding(),
                ) {
                    if (!sideDialog) {
                        OverlayBottomSheetHandle(sheetHandleDragModifier)
                    }

                    QueueHeader(
                        itemCount = state.items.size,
                        canLocateCurrent = hasCurrentItem,
                        onLocateCurrent = {
                            coroutineScope.launch {
                                listState.animateScrollToItem(state.currentIndex)
                            }
                        },
                        onClear = {
                            cancelDrag()
                            onAction(QueueAction.ClearQueue)
                        },
                    )

                    if (state.items.isEmpty()) {
                        QueueEmptyState(modifier = Modifier.weight(1f))
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            itemsIndexed(
                                items = displayItems,
                                key = { _, item -> item.lazyListKey() },
                            ) { visualIndex, item ->
                                val activeDrag = dragState
                                QueueTrackRow(
                                    item = item,
                                    position = visualIndex + 1,
                                    active = item.index == state.currentIndex && state.isPlaying,
                                    interactionsEnabled = activeDrag == null,
                                    isDragged = activeDrag?.originalIndex == item.index,
                                    dragOffsetY = activeDrag?.takeIf {
                                        it.originalIndex == item.index
                                    }?.offsetY ?: 0f,
                                    onClick = { onAction(QueueAction.PlayItem(item.index)) },
                                    onRemove = {
                                        cancelDrag()
                                        onAction(QueueAction.RemoveItem(item.index))
                                    },
                                    onMoveUp = if (visualIndex > 0) {
                                        { onAction(QueueAction.MoveItem(item.index, visualIndex - 1)) }
                                    } else {
                                        null
                                    },
                                    onMoveDown = if (visualIndex < displayItems.lastIndex) {
                                        { onAction(QueueAction.MoveItem(item.index, visualIndex + 1)) }
                                    } else {
                                        null
                                    },
                                    onDragStart = {
                                        if (dragState == null) {
                                            dragState = QueueDragState(
                                                originalIndex = item.index,
                                                currentIndex = visualIndex,
                                            )
                                        }
                                    },
                                    onDrag = onDrag@{ dragAmount ->
                                        val drag = dragState ?: return@onDrag
                                        val visibleItems = listState.layoutInfo.visibleItemsInfo
                                        val draggedItem = visibleItems.firstOrNull {
                                            it.index == drag.currentIndex
                                        } ?: return@onDrag
                                        val nextOffset = drag.offsetY + dragAmount
                                        val draggedCenter =
                                            draggedItem.offset + nextOffset + draggedItem.size / 2f
                                        val target = visibleItems.firstOrNull { visibleItem ->
                                            visibleItem.index != drag.currentIndex &&
                                                draggedCenter >= visibleItem.offset &&
                                                draggedCenter <= visibleItem.offset + visibleItem.size
                                        }
                                        val viewport = listState.layoutInfo
                                        val autoScrollDelta = when {
                                            draggedCenter < viewport.viewportStartOffset + autoScrollEdgePx -> -autoScrollStepPx
                                            draggedCenter > viewport.viewportEndOffset - autoScrollEdgePx -> autoScrollStepPx
                                            else -> 0f
                                        }
                                        if (target == null) {
                                            dragState = drag.copy(
                                                offsetY = nextOffset,
                                                autoScrollDelta = autoScrollDelta,
                                            )
                                        } else {
                                            displayItems = displayItems.move(
                                                fromIndex = drag.currentIndex,
                                                toIndex = target.index,
                                            )
                                            dragState = drag.copy(
                                                currentIndex = target.index,
                                                offsetY = nextOffset + draggedItem.offset - target.offset,
                                                autoScrollDelta = autoScrollDelta,
                                            )
                                        }
                                    },
                                    onDragEnd = {
                                        val finishedDrag = dragState ?: return@QueueTrackRow
                                        dragState = null
                                        displayItems = state.items
                                        if (finishedDrag.originalIndex != finishedDrag.currentIndex) {
                                            onAction(
                                                QueueAction.MoveItem(
                                                    fromIndex = finishedDrag.originalIndex,
                                                    toIndex = finishedDrag.currentIndex,
                                                ),
                                            )
                                        }
                                    },
                                    onDragCancel = ::cancelDrag,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueHeader(
    itemCount: Int,
    canLocateCurrent: Boolean,
    onLocateCurrent: () -> Unit,
    onClear: () -> Unit,
) {
    val dividerColor = MiuixTheme.colorScheme.outline.copy(alpha = 0.08f)
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .drawBehind {
                    drawLine(
                        color = dividerColor,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(QueueRes.string.queue_title, itemCount),
                modifier = Modifier.weight(1f),
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.title3.copy(fontSize = 20.sp, lineHeight = 24.sp),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(
                backgroundColor = MiuixTheme.colorScheme.surfaceContainerHigh,
                enabled = canLocateCurrent,
                onClick = onLocateCurrent,
            ) {
                Icon(
                    painterResource(QueueRes.drawable.icon_locate),
                    stringResource(QueueRes.string.queue_locate_current),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            IconButton(
                backgroundColor = MiuixTheme.colorScheme.surfaceContainerHigh,
                onClick = onClear,
            ) {
                Icon(
                    painterResource(QueueRes.drawable.icon_queue_trash),
                    stringResource(QueueRes.string.queue_clear),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun QueueTrackRow(
    item: QueueItemUi,
    position: Int,
    active: Boolean,
    interactionsEnabled: Boolean,
    isDragged: Boolean,
    dragOffsetY: Float,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val contentColor = if (active) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
    val secondaryColor = if (active) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
    val rowShape = RoundedCornerShape(12.dp)
    val rowBackground = if (isDragged) {
        MiuixTheme.colorScheme.surfaceContainerHigh
    } else {
        MiuixTheme.colorScheme.surfaceContainer
    }
    val subtitle = item.subtitle()

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
                .height(56.dp)
                .background(color = rowBackground, shape = rowShape)
                .padding(end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .clickable(enabled = interactionsEnabled, onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.width(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (active) {
                    QueuePlayingIndicator()
                } else {
                    Text(
                        text = position.toString(),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote2.copy(
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        ),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.title,
                    color = contentColor,
                    style = MiuixTheme.textStyles.body1.copy(
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                    ),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let { secondaryText ->
                    Text(
                        text = secondaryText,
                        color = secondaryColor,
                        style = MiuixTheme.textStyles.footnote1.copy(
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.width(72.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(enabled = interactionsEnabled, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(CoreRes.drawable.icon_deleteseep),
                    contentDescription = stringResource(QueueRes.string.queue_remove_item),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(16.dp),
                )
            }
            QueueDragHandle(
                title = item.title,
                canMoveUp = onMoveUp != null,
                canMoveDown = onMoveDown != null,
                enabled = interactionsEnabled || isDragged,
                isDragged = isDragged,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
            )
        }
        }
        HorizontalDivider()
    }
}

@Composable
private fun QueueDragHandle(
    title: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    enabled: Boolean,
    isDragged: Boolean,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val reorderLabel = stringResource(QueueRes.string.queue_reorder_item, title)
    val moveUpLabel = stringResource(QueueRes.string.queue_move_up)
    val moveDownLabel = stringResource(QueueRes.string.queue_move_down)
    val tint = if (isDragged) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    val accessibilityActions = buildList {
        if (canMoveUp) {
            add(
                CustomAccessibilityAction(moveUpLabel) {
                    onMoveUp?.invoke()
                    true
                },
            )
        }
        if (canMoveDown) {
            add(
                CustomAccessibilityAction(moveDownLabel) {
                    onMoveDown?.invoke()
                    true
                },
            )
        }
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
                        Key.DirectionUp -> onMoveUp?.let { action -> action(); true } ?: false
                        Key.DirectionDown -> onMoveDown?.let { action -> action(); true } ?: false
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
                // Capture in the initial pass so LazyColumn cannot turn a handle drag into scroll.
                detectDragGestures(
                    orientationLock = Orientation.Vertical,
                    shouldAwaitTouchSlop = { false },
                    onDragStart = { _, _, _ -> onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = onDragCancel,
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
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun QueuePlayingIndicator() {
    val transition = rememberInfiniteTransition(label = "queue-playing")
    val heights = listOf(0.35f, 0.70f, 0.50f, 0.80f)

    Row(
        modifier = Modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        heights.forEachIndexed { index, initialHeight ->
            val height by transition.animateFloat(
                initialValue = initialHeight,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 800,
                        delayMillis = index * 100,
                        easing = FastOutSlowInEasing,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "queue-playing-bar-$index",
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(16.dp * height)
                    .clip(RoundedCornerShape(DesignTokens.shapes.full))
                    .background(MiuixTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun QueueEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 208.dp)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(CoreRes.drawable.icon_mode_list),
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.45f),
            modifier = Modifier.size(36.dp),
        )
        Text(
            text = stringResource(QueueRes.string.queue_empty),
            modifier = Modifier.padding(top = 12.dp),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Medium,
        )
    }
}

private data class QueueDragState(
    val originalIndex: Int,
    val currentIndex: Int,
    val offsetY: Float = 0f,
    val autoScrollDelta: Float = 0f,
)

private fun <T> List<T>.move(fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex !in indices || toIndex !in indices || fromIndex == toIndex) return this
    return toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
}

internal fun QueueItemUi.lazyListKey(): String = "queue-item-$index"
