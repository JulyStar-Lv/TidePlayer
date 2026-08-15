package io.github.julystar.musicapp.core.lyrics.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.julystar.musicapp.core.lyrics.ui.reference.KaraokeLineText
import io.github.julystar.musicapp.core.lyrics.ui.reference.LyricsViewSpec
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import kotlinx.coroutines.isActive
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
) {
    val listState = rememberLazyListState()
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

    LaunchedEffect(currentIndex, scrollTargetIndex, lyrics.lines.size) {
        if (lyrics.lines.isNotEmpty()) {
            if (shouldSnapLyricsScroll(displayedIndex, currentIndex)) {
                listState.scrollToItem(scrollTargetIndex)
            } else {
                listState.animateScrollToItem(scrollTargetIndex)
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

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
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
                    onClick = { onLineClick(line) },
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
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (!wordLiftEnabled || isCurrent) 1f else 0.94f,
        animationSpec = spring(stiffness = 420f),
        label = "lyricLineScale",
    )
    val alpha by animateFloatAsState(
        targetValue = when {
            isCurrent -> 1f
            distanceFromCurrent == 1 -> 0.62f
            distanceFromCurrent == 2 -> 0.42f
            else -> 0.26f
        },
        label = "lyricLineAlpha",
    )
    val blurRadius = if (useBlurEffect && distanceFromCurrent > 1) {
        (distanceFromCurrent.coerceAtMost(4) - 1) * 1.15f
    } else {
        0f
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = when (textAlign) {
                    TextAlign.End, TextAlign.Right -> TransformOrigin(1f, 0.5f)
                    TextAlign.Center -> TransformOrigin.Center
                    else -> TransformOrigin(0f, 0.5f)
                }
                this.alpha = alpha
                renderEffect = if (blurRadius > 0f) {
                    BlurEffect(blurRadius, blurRadius, TileMode.Decal)
                } else {
                    null
                }
            }
            .then(if (tapToSeekEnabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = horizontalPadding)
            .padding(top = verticalPadding + topInset, bottom = verticalPadding),
    ) {
        KaraokeText(
            line = line,
            renderPositionProvider = renderPositionProvider,
            isCurrent = isCurrent,
            activeColor = activeColor,
            inactiveColor = inactiveColor,
            textStyle = if (isCurrent) activeTextStyle else inactiveTextStyle,
            textAlign = textAlign,
            wordLiftEnabled = wordLiftEnabled,
        )

        val translation = line.translationOrNull()
        if (showTranslation && !translation.isNullOrBlank()) {
            BasicText(
                text = translation,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp),
                style = secondaryTextStyle.copy(
                    color = activeColor.copy(alpha = if (isCurrent) 0.72f else 0.48f),
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

    val baseSpec = remember(textStyle, activeColor) {
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
                    contentVerticalPadding = 0.dp,
                    mainHorizontalPadding = 0.dp,
                    accompanimentHorizontalPadding = 0.dp,
                    contentSpacing = 0.dp,
                ),
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
    val fontSize = with(density) { textStyle.fontSize.toDp() }
    val lineHeight = with(density) { textStyle.lineHeight.toDp() }
    val dotSize = fontSize * PlaceholderDotSizeEm
    val dotSpacing = fontSize * PlaceholderDotSpacingEm
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
            .height(lineHeight),
        horizontalArrangement = Arrangement.spacedBy(
            space = dotSpacing,
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
                    .size(dotSize)
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
