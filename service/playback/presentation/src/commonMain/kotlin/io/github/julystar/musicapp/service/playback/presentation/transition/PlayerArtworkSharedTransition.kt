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
        val fraction = if (expandedSizePx <= compactSizePx) {
            1f
        } else {
            ((currentSizePx - compactSizePx) / (expandedSizePx - compactSizePx)).coerceIn(0f, 1f)
        }
        val radiusPx = compactRadiusPx + (expandedRadiusPx - compactRadiusPx) * fraction
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
