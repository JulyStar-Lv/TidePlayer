package io.github.julystar.musicapp.car.presentation.nowplaying

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.julystar.musicapp.car.presentation.component.CarArtwork
import io.github.julystar.musicapp.car.presentation.component.carInteractiveSurface
import io.github.julystar.musicapp.car.presentation.focus.CarFocusHost
import io.github.julystar.musicapp.car.presentation.focus.CarFocusId
import io.github.julystar.musicapp.car.presentation.focus.CarFocusIds
import io.github.julystar.musicapp.car.presentation.focus.carFocusTarget
import io.github.julystar.musicapp.car.presentation.focus.carInputRouter
import io.github.julystar.musicapp.car.presentation.focus.rememberCarFocusCoordinator
import io.github.julystar.musicapp.car.presentation.icon.CarIcon
import io.github.julystar.musicapp.car.presentation.icon.CarIcon as IconView
import io.github.julystar.musicapp.car.presentation.layout.CarLayoutMetrics
import io.github.julystar.musicapp.car.presentation.layout.CarFullscreenMetrics
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.model.LyricDisplaySettings
import io.github.julystar.musicapp.core.domain.model.LyricLine
import io.github.julystar.musicapp.core.domain.model.LyricTextAlignment
import io.github.julystar.musicapp.core.domain.repository.ArtworkRepository
import io.github.julystar.musicapp.core.lyrics.ui.LyricsView
import io.github.julystar.musicapp.core.lyrics.ui.toSyncedLyrics
import io.github.julystar.musicapp.service.playback.domain.PlayableItem
import io.github.julystar.musicapp.service.playback.domain.PlaybackStatus
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.absoluteValue
import kotlin.math.floor
import kotlin.math.roundToInt

/** Figma 1480:1130 (minimal player) and 1524:1130 (fullscreen Cover Flow). */
@Composable
fun CarFullscreenNowPlayingScreen(
    metrics: CarLayoutMetrics,
    lyricDisplaySettings: LyricDisplaySettings = LyricDisplaySettings.Default,
    onExitPlayback: () -> Unit,
    onExitFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = koinViewModel<CarNowPlayingViewModel>()
    val artworkRepository = koinInject<ArtworkRepository>()
    val uiState by viewModel.state.collectAsState()
    val state = uiState.player
    val position = uiState.position
    val queue = uiState.queue
    val trackInfo = uiState.trackInfo
    val fullscreen = metrics.fullscreen
    var coverFlow by rememberSaveable { mutableStateOf(false) }
    val focusCoordinator = rememberCarFocusCoordinator()
    val focusManager = LocalFocusManager.current
    val currentIndex = queue.currentIndex.takeIf { it in queue.items.indices } ?: 0
    val focusRoute = if (coverFlow) "fullscreen.cover_flow" else "fullscreen.now_playing"
    val initialFocus = if (coverFlow && queue.items.isNotEmpty()) {
        CarFocusIds.item("fullscreen_cover", currentIndex)
    } else {
        CarFocusIds.FullscreenCoverFlow
    }
    val currentArtwork = trackInfo?.artwork
        ?: state.currentItem?.libraryTrackId?.let { Artwork.LibraryTrack(it, true) }
    val lyricLines = trackInfo?.lyrics?.lines.orEmpty()

    BackHandler {
        if (coverFlow) coverFlow = false else onExitFullscreen()
    }

    CarFocusHost(focusCoordinator, focusRoute, initialFocus, metrics.profile) {
        Box(
            modifier
                .fillMaxSize()
                .background(Color.Black)
                .carInputRouter(
                    focusManager = focusManager,
                    onPlayPause = { viewModel.onAction(CarNowPlayingAction.PlayPause) },
                    onNext = { viewModel.onAction(CarNowPlayingAction.Next) },
                    onPrevious = { viewModel.onAction(CarNowPlayingAction.Previous) },
                    onStop = { viewModel.onAction(CarNowPlayingAction.Pause) },
                ),
        ) {
            FullscreenArtworkBackground(currentArtwork, artworkRepository, fullscreen)
            FullscreenExitPlaybackButton(
                fullscreen,
                onExitPlayback,
                Modifier.offset(fullscreen.exitPlaybackOffset.x, fullscreen.exitPlaybackOffset.y),
            )
            FullscreenExitButton(
                fullscreen,
                onExitFullscreen,
                Modifier.offset(fullscreen.exitFullscreenOffset.x, fullscreen.exitFullscreenOffset.y),
            )
            FullscreenTrackMeta(
                fullscreen = fullscreen,
                title = state.currentItem?.title ?: "尚未播放",
                artist = trackInfo?.artist?.takeIf(String::isNotBlank) ?: state.currentItem?.artist.orEmpty(),
                modifier = Modifier.offset(fullscreen.metadataOffset.x, fullscreen.metadataOffset.y),
            )

            if (coverFlow) {
                if (queue.items.isEmpty()) {
                    BasicText(
                        "播放队列为空",
                        style = TextStyle(fontSize = 64.sp, color = Color.White.copy(alpha = 0.56f)),
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    FullscreenCoverFlow(
                        items = queue.items,
                        currentIndex = currentIndex,
                        fullscreen = fullscreen,
                        artworkRepository = artworkRepository,
                        onPlay = { queueIndex ->
                            viewModel.onAction(CarNowPlayingAction.PlayQueueItem(queueIndex))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(fullscreen.coverFlowHeight)
                            .offset(y = fullscreen.coverFlowTop),
                    )
                    Box(
                        Modifier
                            .offset(fullscreen.indicatorOffset.x, fullscreen.indicatorOffset.y)
                            .size(fullscreen.indicatorSize)
                            .background(Color.White.copy(alpha = 0.78f), RoundedCornerShape(4.dp)),
                    )
                }
            } else {
                FullscreenMinimalContent(
                    fullscreen = fullscreen,
                    artwork = currentArtwork,
                    artworkRepository = artworkRepository,
                    lyricLines = lyricLines,
                    trackTitle = state.currentItem?.title.orEmpty(),
                    trackDurationMs = trackInfo?.durationMs ?: state.currentItem?.durationMs,
                    positionMs = position.positionMs,
                    isPlaying = state.status == PlaybackStatus.Playing,
                    lyricDisplaySettings = lyricDisplaySettings,
                    onSeek = { viewModel.onAction(CarNowPlayingAction.Seek(it)) },
                    onOpenCoverFlow = { coverFlow = true },
                    modifier = Modifier.offset(fullscreen.minimalOffset.x, fullscreen.minimalOffset.y),
                )
            }
        }
    }
}

@Composable
private fun FullscreenArtworkBackground(
    artwork: Artwork?,
    artworkRepository: ArtworkRepository,
    fullscreen: CarFullscreenMetrics,
) {
    CarArtwork(
        artwork = artwork,
        repository = artworkRepository,
        size = 1.dp,
        shape = RectangleShape,
        fillBounds = true,
        modifier = Modifier.fillMaxSize().graphicsLayer {
            scaleX = fullscreen.backgroundScaleX
            scaleY = fullscreen.backgroundScaleY
        }.blur(120.dp),
    )
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.64f)))
}

@Composable
private fun FullscreenExitPlaybackButton(
    fullscreen: CarFullscreenMetrics,
    onExitPlayback: () -> Unit,
    modifier: Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .carFocusTarget(CarFocusIds.NowPlayingCollapse, right = CarFocusIds.FullscreenExit)
            .size(fullscreen.controlSize)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(fullscreen.controlCornerRadius))
            .carInteractiveSurface(
                shape = RoundedCornerShape(fullscreen.controlCornerRadius),
                defaultColor = Color.Black.copy(alpha = 0.22f),
                onClick = onExitPlayback,
            ),
    ) {
        IconView(CarIcon.Collapse, "退出播放界面", Color.White, Modifier.size(fullscreen.controlIconSize))
    }
}

@Composable
private fun FullscreenExitButton(
    fullscreen: CarFullscreenMetrics,
    onExitFullscreen: () -> Unit,
    modifier: Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .carFocusTarget(
                CarFocusIds.FullscreenExit,
                left = CarFocusIds.NowPlayingCollapse,
                right = CarFocusIds.FullscreenCoverFlow,
            )
            .size(fullscreen.controlSize)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(fullscreen.controlCornerRadius))
            .carInteractiveSurface(
                shape = RoundedCornerShape(fullscreen.controlCornerRadius),
                defaultColor = Color.Black.copy(alpha = 0.22f),
                onClick = onExitFullscreen,
            ),
    ) {
        IconView(CarIcon.ExitFullscreen, "退出全屏", Color.White, Modifier.size(fullscreen.controlIconSize))
    }
}

@Composable
private fun FullscreenTrackMeta(
    fullscreen: CarFullscreenMetrics,
    title: String,
    artist: String,
    modifier: Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier.width(fullscreen.metadataWidth)) {
        BasicText(
            title,
            style = TextStyle(
                color = Color(0xFFF7F7F7),
                fontSize = 48.sp,
                lineHeight = 56.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        BasicText(
            artist,
            style = TextStyle(color = Color(0xFFC7C7C7), fontSize = 28.sp, lineHeight = 36.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FullscreenMinimalContent(
    fullscreen: CarFullscreenMetrics,
    artwork: Artwork?,
    artworkRepository: ArtworkRepository,
    lyricLines: List<LyricLine>,
    trackTitle: String,
    trackDurationMs: Long?,
    positionMs: Long,
    isPlaying: Boolean,
    lyricDisplaySettings: LyricDisplaySettings,
    onSeek: (Long) -> Unit,
    onOpenCoverFlow: () -> Unit,
    modifier: Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(fullscreen.minimalGap),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.size(fullscreen.minimalSize),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .carFocusTarget(CarFocusIds.FullscreenCoverFlow, left = CarFocusIds.FullscreenExit)
                .size(fullscreen.artworkSize)
                .carInteractiveSurface(
                    RoundedCornerShape(fullscreen.artworkCornerRadius),
                    defaultColor = Color.Transparent,
                    onClick = onOpenCoverFlow,
                ),
        ) {
            CarArtwork(
                artwork = artwork,
                repository = artworkRepository,
                size = fullscreen.artworkSize,
                shape = RoundedCornerShape(fullscreen.artworkCornerRadius),
                modifier = Modifier,
            )
        }
        FullscreenLyrics(
            lyricLines = lyricLines,
            trackTitle = trackTitle,
            trackDurationMs = trackDurationMs,
            positionMs = positionMs,
            isPlaying = isPlaying,
            lyricDisplaySettings = lyricDisplaySettings,
            onSeek = onSeek,
            modifier = Modifier.size(fullscreen.lyricsSize),
        )
    }
}

@Composable
private fun FullscreenLyrics(
    lyricLines: List<LyricLine>,
    trackTitle: String,
    trackDurationMs: Long?,
    positionMs: Long,
    isPlaying: Boolean,
    lyricDisplaySettings: LyricDisplaySettings,
    onSeek: (Long) -> Unit,
    modifier: Modifier,
) {
    val syncedLyrics = remember(lyricLines, trackTitle, trackDurationMs, lyricDisplaySettings) {
        lyricLines.toSyncedLyrics(trackTitle, trackDurationMs, lyricDisplaySettings)
    }
    if (syncedLyrics.lines.isEmpty()) {
        BasicText(
            "暂无歌词",
            style = TextStyle(
                color = Color(0xFFF7F7F7),
                fontSize = 156.sp,
                lineHeight = 190.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            modifier = modifier,
        )
        return
    }
    val textAlign = when (lyricDisplaySettings.textAlignment) {
        LyricTextAlignment.Left -> TextAlign.Start
        LyricTextAlignment.Center -> TextAlign.Center
        LyricTextAlignment.Right -> TextAlign.End
    }
    LyricsView(
        lyrics = syncedLyrics,
        currentPositionMs = positionMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
        isPlaying = isPlaying,
        onLineClick = { line -> onSeek(line.start.toLong()) },
        activeColor = Color(0xFFF7F7F7),
        inactiveColor = Color(0xFFF7F7F7).copy(alpha = 0.56f),
        activeTextStyle = TextStyle(
            fontSize = 156.sp,
            lineHeight = 190.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        inactiveTextStyle = TextStyle(fontSize = 116.sp, lineHeight = 150.sp),
        secondaryTextStyle = TextStyle(fontSize = 72.sp, lineHeight = 96.sp),
        textAlign = textAlign,
        lineSpacing = 28.dp,
        showTranslation = lyricDisplaySettings.showTranslation,
        wordLiftEnabled = lyricDisplaySettings.wordLiftEnabled,
        useBlurEffect = lyricDisplaySettings.blurEffectEnabled,
        perspectiveEffectEnabled = lyricDisplaySettings.perspectiveEffectEnabled,
        perspectiveAngleDegrees = lyricDisplaySettings.perspectiveAngleDegrees.toFloat(),
        tapToSeekEnabled = lyricDisplaySettings.tapToSeekEnabled,
        verticalContentPaddingFraction = 0f,
        lineHorizontalPadding = 0.dp,
        lineVerticalPadding = 0.dp,
        contextLinesBeforeActive = 0,
        modifier = modifier,
    )
}

@Composable
private fun CoverFlowItem(
    item: PlayableItem,
    distanceFromCenter: Float,
    fullscreen: CarFullscreenMetrics,
    artworkRepository: ArtworkRepository,
    focusId: CarFocusId,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val absoluteDistance = distanceFromCenter.absoluteValue
    val artworkWidth = coverFlowArtworkWidth(absoluteDistance, fullscreen)
    val artworkHeight = coverFlowArtworkHeight(absoluteDistance, fullscreen)
    val selected = absoluteDistance < 0.5f
    val artworkShape = RoundedCornerShape(fullscreen.coverFlowArtworkCornerRadius)
    val infoTop = coverFlowInfoTop(absoluteDistance, fullscreen)
    val infoWidth = maxOf(
        artworkWidth - fullscreen.coverFlowInfoWidthInset,
        fullscreen.coverFlowInfoMinimumWidth,
    )
    Box(modifier = modifier.size(fullscreen.coverFlowItemSize)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(fullscreen.artworkSize)) {
            CarArtwork(
                artwork = item.libraryTrackId?.let { Artwork.LibraryTrack(it, true) },
                repository = artworkRepository,
                size = fullscreen.artworkSize,
                shape = artworkShape,
                modifier = Modifier
                    .requiredSize(fullscreen.artworkSize)
                    .graphicsLayer {
                        scaleX = artworkWidth / fullscreen.artworkSize
                        scaleY = artworkHeight / fullscreen.artworkSize
                        rotationY = if (absoluteDistance < 0.01f) 0f else {
                            -distanceFromCenter.coerceIn(-1f, 1f) * 13f
                        }
                        cameraDistance = fullscreen.coverFlowCameraDistance * density
                        alpha = coverFlowArtworkAlpha(absoluteDistance)
                        shape = artworkShape
                        clip = true
                    }
                    .carFocusTarget(focusId)
                    .carInteractiveSurface(
                        artworkShape,
                        selected = selected,
                        defaultColor = Color.Transparent,
                        onClick = onClick,
                ),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = infoTop).width(infoWidth),
        ) {
            BasicText(
                item.title,
                style = TextStyle(
                    color = Color(0xFFF7F7F7),
                    fontSize = 36.sp,
                    lineHeight = 46.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().height(46.dp),
            )
            Spacer(Modifier.height(4.dp))
            BasicText(
                item.artist.orEmpty(),
                style = TextStyle(
                    color = Color(0xFFF7F7F7).copy(alpha = 0.56f),
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().height(32.dp),
            )
        }
    }
}

@Composable
private fun FullscreenCoverFlow(
    items: List<PlayableItem>,
    currentIndex: Int,
    fullscreen: CarFullscreenMetrics,
    artworkRepository: ArtworkRepository,
    onPlay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val anchor = items.size * COVER_FLOW_ANCHOR_REPEAT + currentIndex
    var position by remember(items.size) { mutableFloatStateOf(anchor.toFloat()) }
    val dragIntervalPx = with(density) { fullscreen.coverFlowDragInterval.toPx() }
    val draggableState = rememberDraggableState { deltaPx ->
        position -= deltaPx / dragIntervalPx
    }

    LaunchedEffect(currentIndex, items.size) {
        val nearestCycle = ((position - currentIndex) / items.size).roundToInt()
        val target = nearestCycle * items.size + currentIndex
        animate(
            initialValue = position,
            targetValue = target.toFloat(),
            animationSpec = tween(COVER_FLOW_SETTLE_MILLIS, easing = FastOutSlowInEasing),
        ) { value, _ -> position = value }
    }

    Box(
        modifier = modifier.draggable(
            state = draggableState,
            orientation = Orientation.Horizontal,
            onDragStopped = { velocityPxPerSecond ->
                val projected = position - velocityPxPerSecond / dragIntervalPx * COVER_FLOW_FLING_SECONDS
                val target = projected.roundToInt()
                animate(
                    initialValue = position,
                    targetValue = target.toFloat(),
                    animationSpec = tween(COVER_FLOW_SETTLE_MILLIS, easing = FastOutSlowInEasing),
                ) { value, _ -> position = value }
            },
        ),
    ) {
        val firstVisible = floor(position).toInt() - COVER_FLOW_SIDE_ITEMS
        val lastVisible = floor(position).toInt() + COVER_FLOW_SIDE_ITEMS + 1
        for (virtualIndex in firstVisible..lastVisible) {
            val distance = virtualIndex - position
            if (distance.absoluteValue > COVER_FLOW_SIDE_ITEMS + 0.75f) continue
            val queueIndex = virtualIndex.floorMod(items.size)
            val centerOffset = coverFlowCenterOffset(distance, fullscreen)
            key(virtualIndex) {
                CoverFlowItem(
                    item = items[queueIndex],
                    distanceFromCenter = distance,
                    fullscreen = fullscreen,
                    artworkRepository = artworkRepository,
                    focusId = CarFocusIds.item("fullscreen_cover", virtualIndex),
                    onClick = {
                        onPlay(queueIndex)
                        position = virtualIndex.toFloat()
                    },
                    modifier = Modifier
                        .offset(x = fullscreen.coverFlowCenterX + centerOffset)
                        .zIndex(COVER_FLOW_SIDE_ITEMS + 1f - distance.absoluteValue),
                )
            }
        }
    }
}

private fun coverFlowCenterOffset(distance: Float, fullscreen: CarFullscreenMetrics): Dp {
    val absolute = distance.absoluteValue.coerceAtMost(COVER_FLOW_SIDE_ITEMS.toFloat())
    val lower = floor(absolute).toInt()
    val upper = (lower + 1).coerceAtMost(COVER_FLOW_SIDE_ITEMS)
    val fraction = absolute - lower
    val offset = fullscreen.coverFlowCenterOffsets[lower] +
        (fullscreen.coverFlowCenterOffsets[upper] - fullscreen.coverFlowCenterOffsets[lower]) * fraction
    return if (distance < 0f) -offset else offset
}

private fun coverFlowArtworkWidth(distance: Float, fullscreen: CarFullscreenMetrics): Dp =
    coverFlowInterpolated(distance, fullscreen.coverFlowArtworkWidths)

private fun coverFlowArtworkHeight(distance: Float, fullscreen: CarFullscreenMetrics): Dp =
    coverFlowInterpolated(distance, fullscreen.coverFlowArtworkHeights)

private fun coverFlowArtworkAlpha(distance: Float): Float =
    coverFlowInterpolated(distance, COVER_FLOW_ARTWORK_ALPHAS)

private fun coverFlowInfoTop(distance: Float, fullscreen: CarFullscreenMetrics): Dp =
    fullscreen.coverFlowInfoTopCentered +
        (fullscreen.coverFlowInfoTopNear - fullscreen.coverFlowInfoTopCentered) * distance.coerceIn(0f, 1f)

private fun coverFlowInterpolated(distance: Float, values: List<Dp>): Dp {
    val absolute = distance.coerceIn(0f, COVER_FLOW_SIDE_ITEMS.toFloat())
    val lower = floor(absolute).toInt()
    val upper = (lower + 1).coerceAtMost(COVER_FLOW_SIDE_ITEMS)
    val fraction = absolute - lower
    return values[lower] + (values[upper] - values[lower]) * fraction
}

private fun coverFlowInterpolated(distance: Float, values: FloatArray): Float {
    val absolute = distance.coerceIn(0f, COVER_FLOW_SIDE_ITEMS.toFloat())
    val lower = floor(absolute).toInt()
    val upper = (lower + 1).coerceAtMost(COVER_FLOW_SIDE_ITEMS)
    val fraction = absolute - lower
    return values[lower] + (values[upper] - values[lower]) * fraction
}

private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor

private const val COVER_FLOW_SIDE_ITEMS = 5
private const val COVER_FLOW_ANCHOR_REPEAT = 100
private const val COVER_FLOW_FLING_SECONDS = 0.12f
private const val COVER_FLOW_SETTLE_MILLIS = 220
private val COVER_FLOW_ARTWORK_ALPHAS = floatArrayOf(1f, 1f, 0.96f, 0.92f, 0.88f, 0.84f)
