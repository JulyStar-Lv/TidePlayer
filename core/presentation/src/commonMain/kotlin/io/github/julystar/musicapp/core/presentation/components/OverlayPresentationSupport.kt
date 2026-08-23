package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

object OverlayPresentationDefaults {
    val scrimColor = Color.Black.copy(alpha = 0.56f)
    const val maxHeightFraction = 0.86f
    val maxHeight = 640.dp

    fun isCompactWindow(viewportWidth: Dp): Boolean =
        viewportWidth.isSpecified && viewportWidth < 600.dp

    fun scrimEnterTransition(): EnterTransition = fadeIn(
        animationSpec = tween(
            durationMillis = 220,
            easing = LinearOutSlowInEasing,
        ),
    )

    fun scrimExitTransition(): ExitTransition = fadeOut(
        animationSpec = tween(
            durationMillis = 200,
            easing = FastOutLinearInEasing,
        ),
    )

    fun surfaceEnterTransition(): EnterTransition =
        fadeIn(
            animationSpec = tween(
                durationMillis = 220,
                easing = LinearOutSlowInEasing,
            ),
        ) + slideInVertically(
            animationSpec = tween(
                durationMillis = 260,
                easing = FastOutSlowInEasing,
            ),
            initialOffsetY = { height ->
                (height * 0.02f).roundToInt().coerceIn(8, 24)
            },
        )

    fun surfaceExitTransition(): ExitTransition =
        fadeOut(
            animationSpec = tween(
                durationMillis = 160,
                easing = FastOutLinearInEasing,
            ),
        ) + slideOutVertically(
            animationSpec = tween(
                durationMillis = 180,
                easing = FastOutLinearInEasing,
            ),
            targetOffsetY = { height ->
                (height * 0.012f).roundToInt().coerceIn(6, 16)
            },
        )
}

object OverlayBottomSheetDefaults {
    val handleWidth = 40.dp
    val handleHeight = 6.dp
    val handleAreaHeight = 30.dp
    val dismissDistance = 72.dp
    val dismissVelocity = 900.dp
    const val enterDurationMillis = 260
    const val exitDurationMillis = 180

    fun surfaceEnterTransition(): EnterTransition =
        slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(
                durationMillis = enterDurationMillis,
                easing = FastOutSlowInEasing,
            ),
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = 220,
                easing = LinearOutSlowInEasing,
            ),
        )

    fun surfaceExitTransition(): ExitTransition =
        slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(
                durationMillis = exitDurationMillis,
                easing = FastOutLinearInEasing,
            ),
        ) + fadeOut(
            animationSpec = tween(
                durationMillis = 160,
                easing = FastOutLinearInEasing,
            ),
        )
}

fun shouldDismissOverlayBottomSheet(
    dragOffsetPx: Float,
    velocityPxPerSecond: Float,
    distanceThresholdPx: Float,
    velocityThresholdPxPerSecond: Float,
): Boolean =
    dragOffsetPx >= distanceThresholdPx ||
        velocityPxPerSecond >= velocityThresholdPxPerSecond

@Composable
fun OverlayBottomSheetHandle(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(OverlayBottomSheetDefaults.handleAreaHeight),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .widthIn(
                    min = OverlayBottomSheetDefaults.handleWidth,
                    max = OverlayBottomSheetDefaults.handleWidth,
                )
                .height(OverlayBottomSheetDefaults.handleHeight)
                .clip(RoundedCornerShape(DesignTokens.shapes.full))
                .background(
                    MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.35f),
                ),
        )
    }
}

fun resolveOverlayMaxHeight(requestedMaxHeight: Dp?, viewportHeight: Dp): Dp {
    val maxHeight = minOf(
        requestedMaxHeight ?: OverlayPresentationDefaults.maxHeight,
        OverlayPresentationDefaults.maxHeight,
    )
    return if (viewportHeight.isSpecified) {
        minOf(maxHeight, viewportHeight * OverlayPresentationDefaults.maxHeightFraction)
    } else {
        maxHeight
    }
}
