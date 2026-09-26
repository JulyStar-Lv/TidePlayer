package io.github.julystar.musicapp.core.lyrics.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.julystar.musicapp.core.lyrics.ui.reference.KaraokeLineText
import io.github.julystar.musicapp.core.lyrics.ui.reference.LyricsMotionSpec
import io.github.julystar.musicapp.core.lyrics.ui.reference.LyricsViewSpec
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val PlaybackResyncThresholdMs = 220.0
private const val PlaybackJitterToleranceMs = 24.0
private const val PlaybackCorrectionFraction = 0.25
private const val LyricHeaderPlaceholder = "•••"
private const val PlaceholderDotCount = 3
private const val PlaceholderDotSizeEm = 0.62f
private const val PlaceholderDotSpacingEm = 0.48f
private const val PlaceholderBreathingCycleDurationMs = 1_800
private const val PlaceholderBreathingScaleMidpoint = 0.91f
private const val PlaceholderBreathingScaleAmplitude = 0.09f

/**
 * A desktop-friendly lyrics surface adapted from accompanist-lyrics-ui.
 *
 * The active line follows playback automatically, karaoke syllables fill according to their own
 * time ranges, nearby lines retain context, and distant lines recede through alpha and blur.
 */
@Composable
fun LyricsView(
    lyrics: SyncedLyrics,
    currentPositionMs: Int,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onLineClick: (ISyncedLine) -> Unit = {},
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.34f),
    edgeColor: Color = Color.Transparent,
    activeTextStyle: TextStyle = TextStyle(
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.Bold,
    ),
    activeSecondaryColor: Color = activeColor.copy(alpha = 0.72f),
    inactiveSecondaryColor: Color = activeColor.copy(alpha = 0.48f),
    inactiveTextStyle: TextStyle = TextStyle(
        fontSize = 27.sp,
        lineHeight = 35.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    secondaryTextStyle: TextStyle = TextStyle(
        fontSize = 19.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    textAlign: TextAlign = TextAlign.Start,
    lineSpacing: Dp = 18.dp,
    showTranslation: Boolean = true,
    wordLiftEnabled: Boolean = true,
    useBlurEffect: Boolean = true,
    perspectiveEffectEnabled: Boolean = false,
    perspectiveAngleDegrees: Float = 25f,
    tapToSeekEnabled: Boolean = true,
    verticalContentPaddingFraction: Float = 0.34f,
    lineHorizontalPadding: Dp = 20.dp,
    lineVerticalPadding: Dp = 6.dp,
    contextLinesBeforeActive: Int = 1,
    focusLineCenterY: Dp? = null,
    focusLineContentAnchorFraction: Float = 0.5f,
    focusPrimaryText: Boolean = false,
    previousLineVisualOffset: Dp = 0.dp,
    blurAdjacentLines: Boolean = false,
    balancedLineWrap: Boolean = true,
    karaokeInactiveAlpha: Float = 0.2f,
    lineBlendMode: BlendMode = BlendMode.SrcOver,
    topEdgeFadeHeight: Dp = 0.dp,
    motionSpec: LyricsMotionSpec? = null,
) {
    val listState = rememberLazyListState()
    val primaryHeights = remember(lyrics.lines) { mutableStateMapOf<Int, Int>() }
    val verticalPaddingPx = with(LocalDensity.current) { lineVerticalPadding.toPx() }
    // Opt in on desktop; callers without a motion spec retain the existing scroll behavior.
    val scrollMotion = remember(lyrics.lines, motionSpec != null) {
        if (motionSpec != null) LyricsScrollMotionState() else null
    }
    val manualScrollConnection = remember(scrollMotion) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    scrollMotion?.onUserScroll()
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.SideEffect && consumed.y != 0f &&
                    scrollMotion?.isUserBrowsing == true
                ) {
                    scrollMotion.onUserScroll()
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(
        scrollMotion,
        scrollMotion?.isUserBrowsing,
        scrollMotion?.userScrollGeneration,
        motionSpec?.manualScrollResumeDelayMs,
    ) {
        if (scrollMotion == null || motionSpec == null || !scrollMotion.isUserBrowsing) {
            return@LaunchedEffect
        }
        // Start the idle interval at the last actual wheel/drag or inertial movement.
        // A desktop wheel channel can remain open after its viewport has stopped moving.
        val idleStart = withFrameNanos { it }
        do {
            val elapsed = withFrameNanos { it } - idleStart
        } while (elapsed < motionSpec.manualScrollResumeDelayMs * 1_000_000L)
        scrollMotion.resumeFollowing()
    }
    val renderPositionProvider = rememberInterpolatedPlaybackPositionProvider(
        currentPositionMs = currentPositionMs,
        isPlaying = isPlaying,
    )
    val currentIndex = remember(lyrics.lines, currentPositionMs) {
        lyrics.lines.indexOfLast { line -> currentPositionMs >= line.start }
            .coerceAtLeast(0)
            .coerceAtMost((lyrics.lines.size - 1).coerceAtLeast(0))
    }
    val scrollTargetIndex = remember(currentIndex, contextLinesBeforeActive) {
        lyricsScrollTargetIndex(currentIndex, contextLinesBeforeActive)
    }
    var displayedIndex by remember(lyrics.lines) { mutableIntStateOf(-1) }
    val perspectiveCameraDistance = with(LocalDensity.current) { 18.dp.toPx() }
    val focusLineCenterPx = with(LocalDensity.current) { focusLineCenterY?.toPx() }

    LaunchedEffect(
        currentIndex,
        scrollTargetIndex,
        lyrics.lines.size,
        focusLineCenterPx,
        focusLineContentAnchorFraction,
        focusPrimaryText,
        primaryHeights[currentIndex],
        motionSpec,
    ) {
        if (motionSpec == null && lyrics.lines.isNotEmpty()) {
            if (shouldSnapLyricsScroll(displayedIndex, currentIndex)) {
                listState.scrollToItem(scrollTargetIndex)
            } else {
                listState.animateScrollToItem(scrollTargetIndex)
            }
            if (focusLineCenterPx != null) {
                val activeLine = snapshotFlow {
                    listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIndex }
                }.filterNotNull().first()
                val anchor = if (focusPrimaryText && primaryHeights[currentIndex] != null) {
                    verticalPaddingPx + primaryHeights.getValue(currentIndex) * focusLineContentAnchorFraction.coerceIn(0f, 1f)
                } else activeLine.size * focusLineContentAnchorFraction.coerceIn(0f, 2f)
                val delta = activeLine.offset + anchor - focusLineCenterPx
                listState.scrollBy(delta)
            }
            displayedIndex = currentIndex
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .fillMaxSize(),
    ) {
        val verticalPadding = maxHeight * verticalContentPaddingFraction.coerceIn(0f, 0.5f)
        val perspectiveRotation = when (textAlign) {
            TextAlign.Right, TextAlign.End -> perspectiveAngleDegrees
            else -> -perspectiveAngleDegrees
        }
        val perspectiveOrigin = when (textAlign) {
            TextAlign.Center -> 0.5f
            TextAlign.Right, TextAlign.End -> 1f
            else -> 0f
        }

        LaunchedEffect(
            lyrics.lines,
            currentIndex,
            scrollTargetIndex,
            focusLineCenterPx,
            focusLineContentAnchorFraction,
            focusPrimaryText,
            primaryHeights[currentIndex],
            verticalPaddingPx,
            maxWidth,
            maxHeight,
            verticalPadding,
            activeTextStyle,
            inactiveTextStyle,
            secondaryTextStyle,
            showTranslation,
            lineSpacing,
            scrollMotion?.isUserBrowsing,
            motionSpec,
        ) {
            if (motionSpec == null || scrollMotion == null ||
                scrollMotion.isUserBrowsing || lyrics.lines.isEmpty()
            ) {
                return@LaunchedEffect
            }
            // Returning to playback or replacing lyrics takes ownership from any pending wheel
            // animation before measuring. Fresh user input cancels this following effect.
            listState.stopScroll(MutatePriority.PreventUserInput)
            // Let the changed focus/text metrics reach layout before measuring the target row.
            withFrameNanos { }
            val targetIndex = if (focusLineCenterPx != null) currentIndex else scrollTargetIndex
            val wasVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }
            val animateLines = displayedIndex >= 0 && wasVisible
            if (!animateLines) scrollMotion.resetAnimation()
            if (!wasVisible) listState.scrollToItem(targetIndex)

            for (pass in 0 until motionSpec.focusedLineAlignmentCorrectionPasses) {
                val layout = listState.layoutInfo
                val target = layout.visibleItemsInfo.firstOrNull { it.index == targetIndex }
                    ?: break
                val anchor = if (focusLineCenterPx == null) 0f else if (focusPrimaryText && primaryHeights[targetIndex] != null) {
                    verticalPaddingPx + primaryHeights.getValue(targetIndex) * focusLineContentAnchorFraction.coerceIn(0f, 1f)
                } else {
                    target.size * focusLineContentAnchorFraction.coerceIn(0f, 2f)
                }
                val focusY = focusLineCenterPx ?: layout.beforeContentPadding.toFloat()
                val delta = target.offset - layout.viewportStartOffset + anchor - focusY
                if (abs(delta) <= motionSpec.focusedLineAlignmentTolerancePx) break

                // Move the viewport once and compensate in each row's spring. Animating the
                // viewport as well would apply the same movement twice and produce a rebound.
                val consumed = listState.scrollBy(delta)
                if (animateLines) scrollMotion.recordScroll(consumed)
                if (abs(consumed) <= motionSpec.focusedLineAlignmentTolerancePx) break
                withFrameNanos { }
            }
            displayedIndex = currentIndex
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(if (topEdgeFadeHeight > 0.dp) {
                    Modifier.graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        // Keep additive lyrics interacting with the artwork after masking.
                        blendMode = lineBlendMode
                    }.drawWithCache {
                        val fade = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.5f to Color.White.copy(alpha = 0.5f),
                            1f to Color.White,
                            endY = topEdgeFadeHeight.toPx().coerceAtMost(size.height),
                        )
                        onDrawWithContent {
                            drawContent()
                            drawRect(fade, blendMode = BlendMode.DstIn)
                        }
                    }
                } else Modifier)
                .then(if (scrollMotion != null) Modifier.nestedScroll(manualScrollConnection) else Modifier)
                .graphicsLayer {
                    if (perspectiveEffectEnabled) {
                        rotationY = perspectiveRotation
                        transformOrigin = TransformOrigin(perspectiveOrigin, 0.5f)
                        cameraDistance = perspectiveCameraDistance
                    }
                },
            contentPadding = PaddingValues(
                top = verticalPadding,
                bottom = maxHeight,
            ),
            verticalArrangement = Arrangement.spacedBy(lineSpacing),
        ) {
            itemsIndexed(
                items = lyrics.lines,
                key = { index, line -> "${line.start}:${line.end}:$index" },
            ) { index, line ->
                val distance = abs(index - currentIndex)
                val isCurrent = index == currentIndex
                LyricLineItem(
                    line = line,
                    renderPositionProvider = renderPositionProvider,
                    isCurrent = isCurrent,
                    distanceFromCurrent = distance,
                    activeColor = activeColor,
                    inactiveColor = inactiveColor,
                    activeSecondaryColor = activeSecondaryColor,
                    inactiveSecondaryColor = inactiveSecondaryColor,
                    activeTextStyle = activeTextStyle,
                    inactiveTextStyle = inactiveTextStyle,
                    secondaryTextStyle = secondaryTextStyle,
                    textAlign = textAlign,
                    showTranslation = showTranslation,
                    wordLiftEnabled = wordLiftEnabled,
                    useBlurEffect = useBlurEffect,
                    tapToSeekEnabled = tapToSeekEnabled,
                    horizontalPadding = lineHorizontalPadding,
                    verticalPadding = lineVerticalPadding,
                    topInset = if (
                        contextLinesBeforeActive > 0 &&
                        index == scrollTargetIndex &&
                        lineHorizontalPadding == 0.dp
                    ) {
                        12.dp
                    } else {
                        0.dp
                    },
                    visualOffset = if (index < currentIndex) previousLineVisualOffset else 0.dp,
                    blurAdjacentLines = blurAdjacentLines,
                    balancedLineWrap = balancedLineWrap,
                    karaokeInactiveAlpha = karaokeInactiveAlpha,
                    lineBlendMode = lineBlendMode,
                    motionSpec = motionSpec,
                    scrollMotion = scrollMotion,
                    onPrimaryHeightChanged = { height ->
                        if (focusPrimaryText) primaryHeights[index] = height
                    },
                    onClick = {
                        scrollMotion?.resumeFollowing()
                        onLineClick(line)
                    },
                )
            }
        }

        if (edgeColor != Color.Transparent) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(112.dp)
                    .background(Brush.verticalGradient(listOf(edgeColor, Color.Transparent))),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(112.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, edgeColor))),
            )
        }
    }
}

@Composable
private fun LyricLineItem(
    line: ISyncedLine,
    renderPositionProvider: (() -> Int)?,
    isCurrent: Boolean,
    distanceFromCurrent: Int,
    activeColor: Color,
    inactiveColor: Color,
    activeSecondaryColor: Color,
    inactiveSecondaryColor: Color,
    activeTextStyle: TextStyle,
    inactiveTextStyle: TextStyle,
    secondaryTextStyle: TextStyle,
    textAlign: TextAlign,
    showTranslation: Boolean,
    wordLiftEnabled: Boolean,
    useBlurEffect: Boolean,
    tapToSeekEnabled: Boolean,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    topInset: Dp,
    visualOffset: Dp,
    blurAdjacentLines: Boolean,
    balancedLineWrap: Boolean,
    karaokeInactiveAlpha: Float,
    lineBlendMode: BlendMode,
    motionSpec: LyricsMotionSpec?,
    scrollMotion: LyricsScrollMotionState?,
    onPrimaryHeightChanged: (Int) -> Unit,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (!wordLiftEnabled || isCurrent) 1f else 0.94f,
        animationSpec = spring(stiffness = 420f),
        label = "lyricLineScale",
    )
    val alpha by animateFloatAsState(
        targetValue = when {
            isCurrent -> 1f
            isHovered -> 0.90f
            distanceFromCurrent == 1 -> 0.62f
            distanceFromCurrent == 2 -> 0.42f
            else -> 0.26f
        },
        label = "lyricLineAlpha",
    )
    val targetBlurRadius = if (!isHovered && useBlurEffect && (distanceFromCurrent > 1 || blurAdjacentLines && distanceFromCurrent == 1)) {
        if (distanceFromCurrent == 1) {
            if (blurAdjacentLines) 3.5f else 2.8f
        } else {
            (distanceFromCurrent.coerceAtMost(4) - 1) * 1.75f
        }
    } else {
        0f
    }
    val blurRadius = if (motionSpec != null) {
        animateFloatAsState(
            targetValue = targetBlurRadius,
            animationSpec = tween(motionSpec.blurAnimationDurationMs),
            label = "lyricLineBlur",
        )
    } else {
        rememberUpdatedState(targetBlurRadius)
    }
    val animatedScrollOffset = if (motionSpec != null && scrollMotion != null) {
        key(scrollMotion, scrollMotion.animationEpoch) {
            animateFloatAsState(
                targetValue = scrollMotion.scrollOffset,
                animationSpec = spring(
                    dampingRatio = motionSpec.placementSpringDampingRatio,
                    stiffness = motionSpec.placementStiffness(distanceFromCurrent),
                ),
                label = "lyricLineFollow",
            )
        }
    } else {
        null
    }
    val animatedVisualOffset = if (motionSpec != null) {
        animateFloatAsState(
            targetValue = with(LocalDensity.current) { visualOffset.toPx() },
            animationSpec = spring(
                dampingRatio = motionSpec.placementSpringDampingRatio,
                stiffness = motionSpec.placementStiffness(distanceFromCurrent),
            ),
            label = "lyricLineOffset",
        )
    } else {
        null
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = if (motionSpec == null) visualOffset else 0.dp)
            .graphicsLayer {
                translationY = (animatedVisualOffset?.value ?: 0f) +
                    if (animatedScrollOffset != null && scrollMotion != null) {
                        scrollMotion.scrollOffset - animatedScrollOffset.value
                    } else {
                        0f
                    }
                scaleX = scale
                scaleY = scale
                transformOrigin = when (textAlign) {
                    TextAlign.End, TextAlign.Right -> TransformOrigin(1f, 0.5f)
                    TextAlign.Center -> TransformOrigin.Center
                    else -> TransformOrigin(0f, 0.5f)
                }
                this.alpha = alpha
                blendMode = lineBlendMode
                val radius = blurRadius.value
                renderEffect = if (radius > 0f) {
                    BlurEffect(radius, radius, TileMode.Decal)
                } else {
                    null
                }
            }
            .hoverable(interactionSource)
            .then(
                if (tapToSeekEnabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = horizontalPadding)
            .padding(top = verticalPadding + topInset, bottom = verticalPadding),
    ) {
        Box(Modifier.fillMaxWidth().onSizeChanged { onPrimaryHeightChanged(it.height) }) {
            KaraokeText(
                line = line,
                renderPositionProvider = renderPositionProvider,
                isCurrent = isCurrent,
                activeColor = if (!isCurrent && lineBlendMode == BlendMode.Plus) {
                    inactiveColor
                } else activeColor,
                inactiveColor = if (isHovered && !isCurrent) {
                    inactiveColor.copy(alpha = maxOf(inactiveColor.alpha, 0.70f))
                } else {
                    inactiveColor
                },
                textStyle = if (isCurrent) activeTextStyle else inactiveTextStyle,
                textAlign = textAlign,
                wordLiftEnabled = wordLiftEnabled,
                balancedLineWrap = balancedLineWrap,
                karaokeInactiveAlpha = if (!isCurrent && lineBlendMode == BlendMode.Plus) {
                    1f
                } else karaokeInactiveAlpha,
            )
        }

        val translation = line.translationOrNull()
        if (showTranslation && !translation.isNullOrBlank()) {
            BasicText(
                text = translation,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp),
                style = secondaryTextStyle.copy(
                    color = if (isCurrent) {
                        activeSecondaryColor
                    } else if (isHovered) {
                        inactiveSecondaryColor.copy(alpha = maxOf(inactiveSecondaryColor.alpha, 0.70f))
                    } else {
                        inactiveSecondaryColor
                    },
                    textAlign = textAlign,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun KaraokeText(
    line: ISyncedLine,
    renderPositionProvider: (() -> Int)?,
    isCurrent: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    textStyle: TextStyle,
    textAlign: TextAlign,
    wordLiftEnabled: Boolean,
    balancedLineWrap: Boolean,
    karaokeInactiveAlpha: Float,
) {
    if (line is SyncedLine && line.content == LyricHeaderPlaceholder) {
        TimelinePlaceholder(
            line = line,
            positionMs = renderPositionProvider?.invoke() ?: line.start,
            activeColor = activeColor,
            inactiveColor = inactiveColor,
            textStyle = textStyle,
            textAlign = textAlign,
        )
        return
    }

    if (line !is KaraokeLine) {
        BasicText(
            text = (line as? SyncedLine)?.content.orEmpty(),
            modifier = Modifier.fillMaxWidth(),
            style = textStyle.copy(
                color = if (isCurrent) activeColor else inactiveColor,
                textAlign = textAlign,
            ),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }

    val baseSpec = remember(textStyle, activeColor, balancedLineWrap, karaokeInactiveAlpha) {
        LyricsViewSpec.default(
            normalLineTextStyle = textStyle,
            accompanimentLineTextStyle = textStyle,
            phoneticTextStyle = textStyle,
            textColor = activeColor,
            blendMode = BlendMode.Plus,
            showTranslation = false,
            showPhonetic = false,
        ).let { spec ->
            spec.copy(
                line = spec.line.copy(
                    balancedLineWrap = balancedLineWrap,
                    contentVerticalPadding = 0.dp,
                    mainHorizontalPadding = 0.dp,
                    accompanimentHorizontalPadding = 0.dp,
                    contentSpacing = 0.dp,
                ),
                progress = spec.progress.copy(inactiveAlpha = karaokeInactiveAlpha),
            )
        }
    }
    val renderSpec = remember(baseSpec, wordLiftEnabled) {
        if (wordLiftEnabled) {
            baseSpec
        } else {
            baseSpec.copy(
                textAnimation = baseSpec.textAnimation.copy(
                    simpleLiftPx = 0f,
                    advancedLiftPx = 0f,
                    advancedShadowBlurPx = 0f,
                    maxDip = 0.0,
                    maxSwell = 0.0,
                ),
            )
        }
    }
    val fallbackPositionProvider = remember(line, isCurrent) {
        { if (isCurrent) line.start else Int.MIN_VALUE }
    }

    KaraokeLineText(
        line = line,
        currentTimeProvider = renderPositionProvider ?: fallbackPositionProvider,
        renderTimeProvider = renderPositionProvider ?: fallbackPositionProvider,
        forcedTextAlign = textAlign,
        modifier = Modifier.fillMaxWidth(),
        normalLineTextStyle = textStyle,
        accompanimentLineTextStyle = textStyle,
        phoneticTextStyle = textStyle,
        activeColor = activeColor,
        blendMode = BlendMode.Plus,
        showTranslation = false,
        showPhonetic = false,
        spec = renderSpec,
    )
}

@Composable
private fun TimelinePlaceholder(
    line: ISyncedLine,
    positionMs: Int,
    activeColor: Color,
    inactiveColor: Color,
    textStyle: TextStyle,
    textAlign: TextAlign,
) {
    val density = LocalDensity.current
    val metrics = density.lyricPlaceholderMetrics(textStyle)
    val breathingScale = lyricPlaceholderBreathingScale(
        positionMs = positionMs,
        startMs = line.start,
        endMs = line.end,
    )
    val horizontalAlignment = when (textAlign) {
        TextAlign.Center -> Alignment.CenterHorizontally
        TextAlign.End, TextAlign.Right -> Alignment.End
        else -> Alignment.Start
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.lineHeight),
        horizontalArrangement = Arrangement.spacedBy(
            space = metrics.dotSpacing,
            alignment = horizontalAlignment,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(PlaceholderDotCount) { index ->
            val progress = lyricPlaceholderDotProgress(
                positionMs = positionMs,
                startMs = line.start,
                endMs = line.end,
                dotIndex = index,
            )
            Box(
                modifier = Modifier
                    .size(metrics.dotSize)
                    .graphicsLayer {
                        scaleX = breathingScale
                        scaleY = breathingScale
                    }
                    .background(
                        color = lerp(inactiveColor, activeColor, progress),
                        shape = CircleShape,
                    ),
            )
        }
    }
}

internal data class LyricPlaceholderMetrics(
    val dotSize: Dp,
    val dotSpacing: Dp,
    val lineHeight: Dp,
)

internal fun Density.lyricPlaceholderMetrics(textStyle: TextStyle): LyricPlaceholderMetrics {
    val fontSize = if (textStyle.fontSize.isSp) textStyle.fontSize.toDp() else 32.sp.toDp()
    val lineHeight = when {
        textStyle.lineHeight.isSp -> textStyle.lineHeight.toDp()
        textStyle.lineHeight.isEm -> fontSize * textStyle.lineHeight.value
        else -> fontSize * 1.25f
    }
    return LyricPlaceholderMetrics(
        dotSize = fontSize * PlaceholderDotSizeEm,
        dotSpacing = fontSize * PlaceholderDotSpacingEm,
        lineHeight = lineHeight,
    )
}

internal fun lyricPlaceholderDotProgress(
    positionMs: Int,
    startMs: Int,
    endMs: Int,
    dotIndex: Int,
): Float {
    require(dotIndex in 0 until PlaceholderDotCount)
    if (endMs <= startMs) return if (positionMs >= endMs) 1f else 0f

    val timelineProgress = (positionMs - startMs).toFloat() / (endMs - startMs)
    return (timelineProgress * PlaceholderDotCount - dotIndex).coerceIn(0f, 1f)
}

internal fun lyricPlaceholderBreathingScale(
    positionMs: Int,
    startMs: Int,
    endMs: Int,
): Float {
    if (endMs <= startMs || positionMs < startMs || positionMs >= endMs) return 1f
    val durationMs = (endMs - startMs).toFloat()
    val desiredHalfCycles = durationMs / (PlaceholderBreathingCycleDurationMs / 2f)
    val roundedHalfCycles = desiredHalfCycles.roundToInt().coerceAtLeast(1)
    val alignedHalfCycles = when {
        roundedHalfCycles % 2 == 1 -> roundedHalfCycles
        desiredHalfCycles - (roundedHalfCycles - 1) <= (roundedHalfCycles + 1) - desiredHalfCycles ->
            (roundedHalfCycles - 1).coerceAtLeast(1)
        else -> roundedHalfCycles + 1
    }
    val progress = (positionMs - startMs) / durationMs
    val angle = progress * alignedHalfCycles * PI.toFloat()
    return PlaceholderBreathingScaleMidpoint - PlaceholderBreathingScaleAmplitude * cos(angle)
}

private fun ISyncedLine.translationOrNull(): String? = when (this) {
    is KaraokeLine -> translation
    is SyncedLine -> translation
    else -> null
}

@Composable
private fun rememberInterpolatedPlaybackPositionProvider(
    currentPositionMs: Int,
    isPlaying: Boolean,
): () -> Int {
    var renderedPositionMs by remember { mutableLongStateOf(currentPositionMs.toLong()) }
    val externalPosition = rememberUpdatedState(currentPositionMs.toLong())

    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            snapshotFlow { externalPosition.value }.collect { positionMs ->
                renderedPositionMs = positionMs
            }
            return@LaunchedEffect
        }

        var preciseRenderedPositionMs = renderedPositionMs.toDouble()
        var observedExternalPositionMs = externalPosition.value
        var previousFrameNanos: Long? = null

        while (isActive) {
            val frameNanos = withFrameNanos { it }
            previousFrameNanos?.let { previousNanos ->
                preciseRenderedPositionMs +=
                    (frameNanos - previousNanos).coerceAtLeast(0L) / 1_000_000.0
            }

            val latestExternalPositionMs = externalPosition.value
            if (latestExternalPositionMs != observedExternalPositionMs) {
                preciseRenderedPositionMs = correctInterpolatedPlaybackPosition(
                    externalPositionMs = latestExternalPositionMs.toDouble(),
                    renderedPositionMs = preciseRenderedPositionMs,
                )
                observedExternalPositionMs = latestExternalPositionMs
            }

            val nextRenderedPositionMs = preciseRenderedPositionMs
                .roundToLong()
                .coerceIn(0L, Int.MAX_VALUE.toLong())
            if (nextRenderedPositionMs != renderedPositionMs) {
                renderedPositionMs = nextRenderedPositionMs
            }
            previousFrameNanos = frameNanos
        }
    }

    return remember {
        { renderedPositionMs.toInt() }
    }
}

internal fun correctInterpolatedPlaybackPosition(
    externalPositionMs: Double,
    renderedPositionMs: Double,
    resyncThresholdMs: Double = PlaybackResyncThresholdMs,
    jitterToleranceMs: Double = PlaybackJitterToleranceMs,
    correctionFraction: Double = PlaybackCorrectionFraction,
): Double {
    val errorMs = externalPositionMs - renderedPositionMs
    return when {
        abs(errorMs) >= resyncThresholdMs -> externalPositionMs
        abs(errorMs) <= jitterToleranceMs -> renderedPositionMs
        else -> renderedPositionMs + errorMs * correctionFraction
    }
}

internal fun shouldSnapLyricsScroll(
    previousIndex: Int,
    currentIndex: Int,
): Boolean {
    return previousIndex < 0 || abs(currentIndex - previousIndex) > 1
}

internal fun lyricsScrollTargetIndex(
    currentIndex: Int,
    contextLinesBeforeActive: Int,
): Int = (currentIndex - contextLinesBeforeActive.coerceAtLeast(0)).coerceAtLeast(0)
