package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
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
