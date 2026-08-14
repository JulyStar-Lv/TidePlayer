package io.github.julystar.musicapp.feature.lyrics.presentation.navigation

import io.github.julystar.musicapp.core.presentation.navigation.MusicGraph
import io.github.julystar.musicapp.service.playback.presentation.transition.LocalPlayerArtworkAnimatedVisibilityScope

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.compose.runtime.CompositionLocalProvider
import io.github.julystar.musicapp.feature.lyrics.presentation.LyricsRoot

fun NavGraphBuilder.lyricsGraph(
    navController: NavHostController,
    onNavigateBack: () -> Unit = { navController.popBackStack() },
) {
    composable<MusicGraph.Lyrics> {
        val animatedVisibilityScope = this
        CompositionLocalProvider(
            LocalPlayerArtworkAnimatedVisibilityScope provides animatedVisibilityScope,
        ) {
            LyricsRoot(
                onNavigateBack = onNavigateBack,
            )
        }
    }
}
