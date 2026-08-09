package io.github.julystar.musicapp.navigation

import io.github.julystar.musicapp.core.presentation.navigation.MusicGraph
import io.github.julystar.musicapp.core.presentation.transition.LocalDetailArtworkAnimatedVisibilityScope
import io.github.julystar.musicapp.service.playback.presentation.transition.LocalPlayerArtworkAnimatedVisibilityScope

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

fun NavGraphBuilder.homeGraph(
    scaffoldPadding: PaddingValues,
    currentTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    onOpenQueue: () -> Unit,
) {
    composable<MusicGraph.Home> {
        val animatedVisibilityScope = this
        CompositionLocalProvider(
            LocalPlayerArtworkAnimatedVisibilityScope provides animatedVisibilityScope,
            LocalDetailArtworkAnimatedVisibilityScope provides animatedVisibilityScope,
        ) {
            HomePage(
                scaffoldPadding = scaffoldPadding,
                currentTab = currentTab,
                onTabSelected = onTabSelected,
                onOpenQueue = onOpenQueue,
            )
        }
    }
}
