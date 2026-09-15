package io.github.julystar.musicapp.car.presentation.nowplaying

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.julystar.musicapp.car.presentation.component.CarArtwork
import io.github.julystar.musicapp.car.presentation.component.carInteractiveSurface
import io.github.julystar.musicapp.car.presentation.icon.CarIcon
import io.github.julystar.musicapp.car.presentation.icon.CarIcon as IconView
import io.github.julystar.musicapp.car.presentation.layout.CarLayoutMetrics
import io.github.julystar.musicapp.car.presentation.theme.LocalCarColors
import io.github.julystar.musicapp.car.presentation.theme.LocalCarDimensions
import io.github.julystar.musicapp.car.presentation.theme.LocalCarShapes
import io.github.julystar.musicapp.car.presentation.theme.LocalCarSpacing
import io.github.julystar.musicapp.car.presentation.theme.LocalCarTouchTargets
import io.github.julystar.musicapp.car.presentation.theme.LocalCarTypography
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.model.CurrentTrackInfo
import io.github.julystar.musicapp.core.domain.model.LyricDisplaySettings
import io.github.julystar.musicapp.core.domain.model.LyricTextAlignment
import io.github.julystar.musicapp.core.domain.repository.ArtworkRepository
import io.github.julystar.musicapp.core.lyrics.ui.LyricsView
import io.github.julystar.musicapp.core.lyrics.ui.toSyncedLyrics
import io.github.julystar.musicapp.service.playback.domain.PlayableItem
import io.github.julystar.musicapp.service.playback.domain.PlaybackPosition
import io.github.julystar.musicapp.service.playback.domain.PlaybackQueue
import io.github.julystar.musicapp.service.playback.domain.PlaybackStatus
import io.github.julystar.musicapp.service.playback.domain.PlayerState
import io.github.julystar.musicapp.service.playback.domain.RepeatMode
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import io.github.julystar.musicapp.car.presentation.focus.CarFocusCoordinator
import io.github.julystar.musicapp.car.presentation.focus.CarFocusId
import io.github.julystar.musicapp.car.presentation.focus.CarFocusIds
import io.github.julystar.musicapp.car.presentation.focus.carFocusTarget

@Composable
fun CarNowPlayingScreen(
    metrics: CarLayoutMetrics,
    lyricDisplaySettings: LyricDisplaySettings = LyricDisplaySettings.Default,
    focusCoordinator: CarFocusCoordinator,
    onCollapse: () -> Unit,
    onEnterFullscreen: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel = koinViewModel<CarNowPlayingViewModel>()
    val artworkRepository = koinInject<ArtworkRepository>()
    val uiState by viewModel.state.collectAsState()
    val state = uiState.player
    val position = uiState.position
    val queue = uiState.queue
    val trackInfo = uiState.trackInfo
    var queueVisible by remember { mutableStateOf(false) }
    val focusScope = rememberCoroutineScope()
    fun closeQueue() {
        queueVisible = false
        focusScope.launch {
            withFrameNanos { }
            focusCoordinator.requestFocus(CarFocusIds.NowPlayingQueue)
        }
    }
    BackHandler(enabled = queueVisible, onBack = ::closeQueue)
    LaunchedEffect(queueVisible, queue.items.size) {
        if (queueVisible && queue.items.isNotEmpty()) {
            withFrameNanos { }
            val index = queue.currentIndex.takeIf { it in queue.items.indices } ?: 0
            focusCoordinator.requestFocus(queue.items[index].carQueueFocusId(index))
        }
    }

    val backdropArtwork = trackInfo?.artwork
        ?: state.currentItem?.libraryTrackId?.let { Artwork.LibraryTrack(it, true) }
    val colors = LocalCarColors.current
    val darkBackground = colors.backgroundBase == Color.Black
    val headerControlBackground = if (darkBackground) Color.Black.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.42f)
    val headerControlBorder = if (darkBackground) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f)
    val headerControlShape = RoundedCornerShape(20.dp)
    Box(modifier.fillMaxSize().background(colors.backgroundBase)) {
        CarArtwork(
            artwork = backdropArtwork,
            repository = artworkRepository,
            size = metrics.contentSize.height,
            shape = RectangleShape,
            fillBounds = true,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = 1.15f
                scaleY = 1.15f
            }.blur(60.dp),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        colors.backgroundBase.copy(alpha = 0.72f),
                        colors.backgroundBase.copy(alpha = 0.62f),
                        colors.accentSubtle.copy(alpha = 0.48f),
                    ),
                ),
            ),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(metrics.nowPlayingPaneGap),
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = metrics.nowPlayingHorizontalMargin + metrics.nowPlayingContentStartOffset,
                    top = metrics.nowPlayingVerticalMargin,
                    end = metrics.nowPlayingHorizontalMargin,
                    bottom = metrics.nowPlayingVerticalMargin,
                ),
        ) {
            PlayerPane(
                metrics,
                state,
                position,
                trackInfo,
                artworkRepository,
                onAction = viewModel::onAction,
                isFavorite = uiState.isFavorite,
                queueVisible,
                onToggleQueue = { if (queueVisible) closeQueue() else queueVisible = true },
                modifier = Modifier.width(metrics.nowPlayingPlayerPaneWidth).fillMaxHeight(),
            )
            if (queueVisible) {
                QueuePane(metrics, queue, viewModel::onAction, artworkRepository, Modifier.weight(1f).fillMaxHeight())
            } else {
                LyricsPane(
                    metrics = metrics,
                    state = state,
                    position = position,
                    trackInfo = trackInfo,
                    lyricDisplaySettings = lyricDisplaySettings,
                    onAction = viewModel::onAction,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        CarPlayerControl(
            icon = CarIcon.Collapse,
            description = "退出播放界面",
            size = metrics.headerHeight,
            iconSize = metrics.iconSize,
            selected = false,
            focusId = CarFocusIds.NowPlayingCollapse,
            right = CarFocusIds.NowPlayingShuffle,
            onClick = onCollapse,
            modifier = Modifier.padding(
                start = metrics.nowPlayingHorizontalMargin,
                top = metrics.nowPlayingVerticalMargin,
            ),
            containerColor = headerControlBackground,
            borderColor = headerControlBorder,
            shape = headerControlShape,
        )
        CarPlayerControl(
            icon = CarIcon.Fullscreen,
            description = "全屏播放",
            size = metrics.headerHeight,
            iconSize = metrics.iconSize,
            selected = false,
            focusId = CarFocusIds.NowPlayingFullscreen,
            left = CarFocusIds.NowPlayingCollapse,
            right = CarFocusIds.NowPlayingShuffle,
            onClick = onEnterFullscreen,
            modifier = Modifier.padding(
                start = metrics.nowPlayingHorizontalMargin,
                top = metrics.nowPlayingVerticalMargin +
                    metrics.headerHeight +
                    metrics.nowPlayingVerticalMargin / 3f,
            ),
            containerColor = headerControlBackground,
            borderColor = headerControlBorder,
            shape = headerControlShape,
        )
    }
}

@Composable
private fun PlayerPane(
    metrics: CarLayoutMetrics,
    state: PlayerState,
    position: PlaybackPosition,
    trackInfo: CurrentTrackInfo?,
    artworkRepository: ArtworkRepository,
    onAction: (CarNowPlayingAction) -> Unit,
    isFavorite: Boolean,
    queueVisible: Boolean,
    onToggleQueue: () -> Unit,
    modifier: Modifier,
) {
    val colors = LocalCarColors.current
    val spacing = LocalCarSpacing.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        CarArtwork(
            artwork = trackInfo?.artwork ?: state.currentItem?.libraryTrackId?.let { Artwork.LibraryTrack(it, true) },
            repository = artworkRepository,
            size = metrics.nowPlayingArtworkSize,
            shape = LocalCarShapes.current.panel,
            modifier = Modifier.padding(top = spacing.pane),
        )
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.width(metrics.nowPlayingArtworkSize),
        ) {
            CarPlayerControl(
                if (isFavorite) CarIcon.HeartFilled else CarIcon.Heart,
                if (isFavorite) "取消收藏" else "收藏",
                metrics.headerHeight,
                metrics.iconSize,
                isFavorite,
                focusId = CarFocusIds.NowPlayingShuffle,
                right = CarFocusIds.NowPlayingMore,
                down = CarFocusIds.NowPlayingRepeat,
                enabled = state.currentItem?.libraryTrackId != null,
                onClick = { onAction(CarNowPlayingAction.ToggleFavorite) },
                modifier = Modifier,
                showStateBackground = false,
            )
            BasicText(
                if (state.status == PlaybackStatus.Loading) "正在缓冲…" else "LOSSLESS",
                style = LocalCarTypography.current.label.copy(
                    color = if (state.status == PlaybackStatus.Loading) colors.accentPrimary else colors.textSecondary,
                ),
            )
            CarPlayerControl(
                CarIcon.More, "更多（暂不支持）", metrics.headerHeight, metrics.iconSize, false,
                focusId = CarFocusIds.NowPlayingMore,
                enabled = false,
                onClick = {},
                modifier = Modifier,
                showStateBackground = false,
            )
        }
        Spacer(Modifier.height(spacing.content))
        PlaybackProgress(
            metrics,
            position,
            { onAction(CarNowPlayingAction.Seek(it)) },
            Modifier.width(metrics.nowPlayingArtworkSize),
        )
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.width(metrics.nowPlayingArtworkSize)) {
            BasicText(position.positionMs.asTime(), style = LocalCarTypography.current.body.copy(color = colors.textSecondary))
            BasicText(position.durationMs.asTime(), style = LocalCarTypography.current.body.copy(color = colors.textSecondary))
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.width(metrics.nowPlayingArtworkSize),
        ) {
            val playbackModeIcon = when {
                state.shuffleEnabled -> CarIcon.Shuffle
                state.repeatMode == RepeatMode.One -> CarIcon.RepeatOne
                else -> CarIcon.RepeatAll
            }
            val playbackModeDescription = when {
                state.shuffleEnabled -> "随机播放"
                state.repeatMode == RepeatMode.One -> "单曲循环"
                else -> "列表循环"
            }
            CarPlayerControl(
                playbackModeIcon,
                playbackModeDescription,
                LocalCarTouchTargets.current.playerControl,
                metrics.headerHeight,
                state.shuffleEnabled || state.repeatMode == RepeatMode.One,
                focusId = CarFocusIds.NowPlayingRepeat,
                up = CarFocusIds.NowPlayingShuffle,
                right = CarFocusIds.NowPlayingPrevious,
                onClick = { onAction(CarNowPlayingAction.CyclePlaybackMode) },
                modifier = Modifier,
                showStateBackground = false,
            )
            CarPlayerControl(
                CarIcon.PreviousLarge, "上一首", LocalCarTouchTargets.current.playerControl, metrics.headerHeight, false,
                focusId = CarFocusIds.NowPlayingPrevious,
                left = CarFocusIds.NowPlayingRepeat,
                right = CarFocusIds.NowPlayingToggle,
                onClick = { onAction(CarNowPlayingAction.Previous) },
                modifier = Modifier,
            )
            CarPlayerControl(
                if (state.status == PlaybackStatus.Playing) CarIcon.PauseLarge else CarIcon.Play,
                if (state.status == PlaybackStatus.Playing) "暂停" else "播放",
                LocalCarTouchTargets.current.primaryControl,
                metrics.headerHeight,
                false,
                focusId = CarFocusIds.NowPlayingToggle,
                left = CarFocusIds.NowPlayingPrevious,
                right = CarFocusIds.NowPlayingNext,
                onClick = { onAction(CarNowPlayingAction.PlayPause) },
                modifier = Modifier,
            )
            CarPlayerControl(
                CarIcon.NextLarge, "下一首", LocalCarTouchTargets.current.playerControl, metrics.headerHeight, false,
                focusId = CarFocusIds.NowPlayingNext,
                left = CarFocusIds.NowPlayingToggle,
                right = CarFocusIds.NowPlayingQueue,
                onClick = { onAction(CarNowPlayingAction.Next) },
                modifier = Modifier,
            )
            CarPlayerControl(
                CarIcon.Queue, "播放队列", LocalCarTouchTargets.current.playerControl, metrics.headerHeight, queueVisible,
                focusId = CarFocusIds.NowPlayingQueue,
                up = CarFocusIds.NowPlayingShuffle,
                left = CarFocusIds.NowPlayingNext,
                onClick = onToggleQueue,
                modifier = Modifier,
                showStateBackground = false,
            )
        }
    }
}

@Composable
private fun PlaybackProgress(
    metrics: CarLayoutMetrics,
    position: PlaybackPosition,
    onSeek: (Long) -> Unit,
    modifier: Modifier,
) {
    val progress = position.toCarProgress()
    val fraction = progress.playedFraction
    val bufferedFraction = progress.bufferedFraction
    val colors = LocalCarColors.current
    Box(
        modifier = modifier
            .height(metrics.progressTrackHeight)
            .clip(LocalCarShapes.current.control)
            .background(colors.controlTrack)
            .semantics {
                contentDescription = "播放进度"
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            }
            .pointerInput(position.durationMs) {
                detectTapGestures { offset ->
                    if (position.durationMs > 0L) onSeek((position.durationMs * (offset.x / size.width)).toLong())
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(bufferedFraction)
                .background(colors.controlTrackDisabled),
        )
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .background(colors.accentPrimary),
        )
    }
}

@Composable
private fun LyricsPane(
    metrics: CarLayoutMetrics,
    state: PlayerState,
    position: PlaybackPosition,
    trackInfo: CurrentTrackInfo?,
    lyricDisplaySettings: LyricDisplaySettings,
    onAction: (CarNowPlayingAction) -> Unit,
    modifier: Modifier,
) {
    val colors = LocalCarColors.current
    val spacing = LocalCarSpacing.current
    val lines = trackInfo?.lyrics?.lines.orEmpty()
    val syncedLyrics = remember(lines, trackInfo?.title, trackInfo?.durationMs, lyricDisplaySettings) {
        lines.toSyncedLyrics(
            trackTitle = trackInfo?.title.orEmpty(),
            trackDurationMs = trackInfo?.durationMs ?: state.currentItem?.durationMs,
            settings = lyricDisplaySettings,
        )
    }
    Column(modifier.padding(metrics.nowPlayingInnerPadding)) {
        BasicText(
            text = state.currentItem?.title ?: "尚未播放",
            style = LocalCarTypography.current.headline.copy(color = colors.textPrimary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val artistAndAlbum = listOfNotNull(
            trackInfo?.artist?.takeIf(String::isNotBlank) ?: state.currentItem?.artist?.takeIf(String::isNotBlank),
            state.currentItem?.album?.takeIf(String::isNotBlank),
        ).joinToString(" · ")
        BasicText(
            text = artistAndAlbum,
            style = LocalCarTypography.current.title.copy(color = colors.textSecondary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(spacing.section))
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.38f)))
        if (syncedLyrics.lines.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                BasicText("暂无歌词", style = LocalCarTypography.current.title.copy(color = colors.textSummary))
            }
        } else {
            val textAlign = when (lyricDisplaySettings.textAlignment) {
                LyricTextAlignment.Left -> TextAlign.Start
                LyricTextAlignment.Center -> TextAlign.Center
                LyricTextAlignment.Right -> TextAlign.End
            }
            LyricsView(
                lyrics = syncedLyrics,
                currentPositionMs = position.positionMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                isPlaying = state.status == PlaybackStatus.Playing,
                onLineClick = { line -> onAction(CarNowPlayingAction.Seek(line.start.toLong())) },
                activeColor = colors.accentPrimary,
                inactiveColor = colors.textSecondary,
                activeTextStyle = LocalCarTypography.current.headline.copy(fontWeight = FontWeight.Bold),
                inactiveTextStyle = LocalCarTypography.current.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                secondaryTextStyle = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium),
                textAlign = textAlign,
                lineSpacing = spacing.wide,
                showTranslation = lyricDisplaySettings.showTranslation,
                wordLiftEnabled = lyricDisplaySettings.wordLiftEnabled,
                useBlurEffect = lyricDisplaySettings.blurEffectEnabled,
                perspectiveEffectEnabled = lyricDisplaySettings.perspectiveEffectEnabled,
                perspectiveAngleDegrees = lyricDisplaySettings.perspectiveAngleDegrees.toFloat(),
                tapToSeekEnabled = lyricDisplaySettings.tapToSeekEnabled,
                verticalContentPaddingFraction = 0.04f,
                lineHorizontalPadding = 0.dp,
                contextLinesBeforeActive = 2,
                modifier = Modifier.fillMaxSize().padding(top = spacing.wide),
            )
        }
    }
}

@Composable
private fun QueuePane(
    metrics: CarLayoutMetrics,
    queue: PlaybackQueue,
    onAction: (CarNowPlayingAction) -> Unit,
    artworkRepository: ArtworkRepository,
    modifier: Modifier,
) {
    val colors = LocalCarColors.current
    val spacing = LocalCarSpacing.current
    val listState = rememberLazyListState()
    LaunchedEffect(queue.currentIndex, queue.items.size) {
        if (queue.currentIndex in queue.items.indices) listState.animateScrollToItem(queue.currentIndex)
    }
    Column(modifier.padding(metrics.nowPlayingInnerPadding)) {
        BasicText("正在播放", style = LocalCarTypography.current.pageTitle.copy(color = colors.textPrimary))
        BasicText("${queue.items.size} 首", style = LocalCarTypography.current.body.copy(color = colors.textSecondary))
        Spacer(Modifier.height(spacing.section))
        Box(Modifier.fillMaxWidth().height(LocalCarDimensions.current.dividerWidth).background(colors.borderDefault))
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(spacing.small),
            modifier = Modifier.fillMaxSize().padding(top = spacing.small),
        ) {
            itemsIndexed(queue.items, key = { index, item -> item.stableCarQueueKey(index) }) { index, item ->
                QueueRow(
                    item = item,
                    playing = index == queue.currentIndex,
                    artworkRepository = artworkRepository,
                    height = metrics.compactCardHeight * (104f / 112f),
                    artworkSize = metrics.iconSize,
                    focusId = item.carQueueFocusId(index),
                    onClick = { onAction(CarNowPlayingAction.PlayQueueItem(index)) },
                )
            }
        }
    }
}

@Composable
private fun QueueRow(
    item: PlayableItem,
    playing: Boolean,
    artworkRepository: ArtworkRepository,
    height: Dp,
    artworkSize: Dp,
    focusId: CarFocusId,
    onClick: () -> Unit,
) {
    val colors = LocalCarColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .carFocusTarget(focusId, left = CarFocusIds.NowPlayingQueue)
            .fillMaxWidth()
            .height(height)
            .carInteractiveSurface(LocalCarShapes.current.navigationItem, playing = playing, onClick = onClick)
            .padding(horizontal = LocalCarSpacing.current.compact),
    ) {
        BasicText("⋮⋮", style = LocalCarTypography.current.body.copy(color = colors.textSummary))
        Spacer(Modifier.width(LocalCarSpacing.current.small))
        CarArtwork(
            artwork = item.libraryTrackId?.let { Artwork.LibraryTrack(it, true) },
            repository = artworkRepository,
            size = artworkSize,
            shape = LocalCarShapes.current.artwork,
        )
        Spacer(Modifier.width(LocalCarSpacing.current.section))
        Column(Modifier.weight(1f)) {
            BasicText(
                item.title,
                style = LocalCarTypography.current.bodyLarge.copy(color = if (playing) colors.accentPrimary else colors.textPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(item.artist.orEmpty(), style = LocalCarTypography.current.body.copy(color = colors.textSecondary))
        }
        BasicText(item.durationMs.asTime(), style = LocalCarTypography.current.body.copy(color = colors.textSecondary))
        Spacer(Modifier.width(LocalCarSpacing.current.section))
        IconView(CarIcon.More, "更多", colors.textSecondary, Modifier.size(32.dp))
    }
}

@Composable
private fun CarPlayerControl(
    icon: CarIcon,
    description: String,
    size: Dp,
    iconSize: Dp,
    selected: Boolean,
    focusId: CarFocusId,
    enabled: Boolean = true,
    up: CarFocusId? = null,
    down: CarFocusId? = null,
    left: CarFocusId? = null,
    right: CarFocusId? = null,
    onClick: () -> Unit,
    modifier: Modifier,
    containerColor: Color = Color.Transparent,
    borderColor: Color = Color.Transparent,
    shape: Shape? = null,
    showStateBackground: Boolean = true,
) {
    val colors = LocalCarColors.current
    val controlShape = shape ?: LocalCarShapes.current.control
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .carFocusTarget(focusId, up = up, down = down, left = left, right = right)
            .size(size)
            .semantics { contentDescription = description }
            .then(if (borderColor != Color.Transparent) Modifier.border(1.dp, borderColor, controlShape) else Modifier)
            .carInteractiveSurface(
                controlShape,
                selected = selected,
                enabled = enabled,
                defaultColor = containerColor,
                showStateBackground = showStateBackground,
                onClick = onClick,
            ),
    ) {
        IconView(
            icon,
            null,
            when {
                !enabled -> colors.textDisabled
                selected -> colors.accentPrimary
                else -> colors.textPrimary
            },
            Modifier.size(iconSize),
        )
    }
}

internal data class CarPlaybackModeSelection(
    val repeatMode: RepeatMode,
    val shuffleEnabled: Boolean,
)

internal fun PlayerState.nextCarPlaybackMode(): CarPlaybackModeSelection = when {
    shuffleEnabled -> CarPlaybackModeSelection(RepeatMode.One, shuffleEnabled = false)
    repeatMode == RepeatMode.One -> CarPlaybackModeSelection(RepeatMode.All, shuffleEnabled = false)
    repeatMode == RepeatMode.All -> CarPlaybackModeSelection(RepeatMode.All, shuffleEnabled = true)
    else -> CarPlaybackModeSelection(RepeatMode.All, shuffleEnabled = false)
}

private fun Long?.asTime(): String {
    val totalSeconds = (this ?: 0L).coerceAtLeast(0L) / 1000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

internal data class CarPlaybackProgress(
    val playedFraction: Float,
    val bufferedFraction: Float,
)

internal fun PlaybackPosition.toCarProgress(): CarPlaybackProgress {
    if (durationMs <= 0L) return CarPlaybackProgress(0f, 0f)
    val played = (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    val buffered = (bufferedMs.toFloat() / durationMs).coerceIn(played, 1f)
    return CarPlaybackProgress(played, buffered)
}

internal fun PlayableItem.stableCarQueueKey(index: Int): String =
    mediaId?.toString() ?: libraryTrackId?.let { "library:$it" } ?: "queue:$index:$title"

internal fun PlayableItem.carQueueFocusId(index: Int): CarFocusId = CarFocusIds.queue(stableCarQueueKey(index))
