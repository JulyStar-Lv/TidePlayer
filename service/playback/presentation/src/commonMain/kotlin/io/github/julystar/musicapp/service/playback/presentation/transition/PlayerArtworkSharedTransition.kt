@file:OptIn(ExperimentalSharedTransitionApi::class)

package io.github.julystar.musicapp.service.playback.presentation.transition

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens

val LocalPlayerArtworkSharedTransitionScope =
    staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalPlayerArtworkAnimatedVisibilityScope =
    staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

internal data class PlayerArtworkTransitionShape(
    val compactSize: Dp,
    val expandedSize: Dp,
    val compactCornerRadius: Dp,
    val expandedCornerRadius: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val compactSizePx = with(density) { compactSize.toPx() }
        val expandedSizePx = with(density) { expandedSize.toPx() }
        val compactRadiusPx = with(density) { compactCornerRadius.toPx() }
        val expandedRadiusPx = with(density) { expandedCornerRadius.toPx() }
        val currentSizePx = minOf(size.width, size.height)
        val radiusPx = playerArtworkCornerRadius(
            currentSize = currentSizePx,
            compactSize = compactSizePx,
            expandedSize = expandedSizePx,
            compactRadius = compactRadiusPx,
            expandedRadius = expandedRadiusPx,
        )
        return Outline.Rounded(
            RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                cornerRadius = CornerRadius(radiusPx),
            ),
        )
    }
}

fun playerArtworkTransitionShape(
    expandedSize: Dp = 356.dp,
    expandedCornerRadius: Dp = 28.dp,
): Shape = PlayerArtworkTransitionShape(
    compactSize = 52.dp,
    expandedSize = expandedSize,
    compactCornerRadius = 13.dp,
    expandedCornerRadius = expandedCornerRadius,
)

internal fun playerArtworkCornerRadius(
    currentSize: Float,
    compactSize: Float,
    expandedSize: Float,
    compactRadius: Float,
    expandedRadius: Float,
): Float {
    val fraction = if (expandedSize <= compactSize) {
        1f
    } else {
        ((currentSize - compactSize) / (expandedSize - compactSize)).coerceIn(0f, 1f)
    }
    return compactRadius + (expandedRadius - compactRadius) * fraction
}

@Composable
fun Modifier.playerArtworkSharedElement(): Modifier {
    val sharedTransitionScope = LocalPlayerArtworkSharedTransitionScope.current ?: return this
    val animatedVisibilityScope = LocalPlayerArtworkAnimatedVisibilityScope.current ?: return this
    val durationMillis = DesignTokens.motion.playerExpandMillis

    return with(sharedTransitionScope) {
        sharedElement(
            sharedContentState = rememberSharedContentState(PlayerArtworkSharedElementKey),
            animatedVisibilityScope = animatedVisibilityScope,
            boundsTransform = BoundsTransform { _, _ ->
                tween(
                    durationMillis = durationMillis,
                    easing = CubicBezierEasing(0.32f, 0f, 0.15f, 1f),
                )
            },
        )
    }
}

private const val PlayerArtworkSharedElementKey = "player-artwork"
