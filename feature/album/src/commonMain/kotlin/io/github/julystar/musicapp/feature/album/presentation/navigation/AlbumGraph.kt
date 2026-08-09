package io.github.julystar.musicapp.feature.album.presentation.navigation

import io.github.julystar.musicapp.core.presentation.navigation.MusicGraph
import io.github.julystar.musicapp.core.presentation.transition.LocalDetailArtworkAnimatedVisibilityScope

import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.julystar.musicapp.feature.album.presentation.AlbumRoot

fun NavGraphBuilder.albumGraph(
    onNavigateBack: () -> Unit,
) {
    composable<MusicGraph.Album> {
        val animatedVisibilityScope = this
        CompositionLocalProvider(
            LocalDetailArtworkAnimatedVisibilityScope provides animatedVisibilityScope,
        ) {
            AlbumRoot(
                onNavigateBack = onNavigateBack,
            )
        }
    }
}
