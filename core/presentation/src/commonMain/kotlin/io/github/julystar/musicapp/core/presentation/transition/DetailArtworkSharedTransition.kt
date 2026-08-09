@file:OptIn(ExperimentalSharedTransitionApi::class)

package io.github.julystar.musicapp.core.presentation.transition

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens

val LocalDetailArtworkSharedTransitionScope =
    staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalDetailArtworkAnimatedVisibilityScope =
    staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

@Composable
fun Modifier.albumArtworkSharedElement(albumId: Long): Modifier =
    detailArtworkSharedElement(key = "album-artwork-$albumId")

@Composable
fun Modifier.playlistArtworkSharedElement(playlistId: Long): Modifier =
    detailArtworkSharedElement(key = "playlist-artwork-$playlistId")

@Composable
private fun Modifier.detailArtworkSharedElement(key: String): Modifier {
    val sharedTransitionScope = LocalDetailArtworkSharedTransitionScope.current ?: return this
    val animatedVisibilityScope = LocalDetailArtworkAnimatedVisibilityScope.current ?: return this
    val durationMillis = DesignTokens.motion.emphasizedMillis

    return with(sharedTransitionScope) {
        sharedElement(
            sharedContentState = rememberSharedContentState(key),
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
