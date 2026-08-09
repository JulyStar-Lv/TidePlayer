package io.github.julystar.musicapp.feature.artist.presentation.navigation

import io.github.julystar.musicapp.core.presentation.navigation.MusicGraph
import io.github.julystar.musicapp.core.presentation.transition.LocalDetailArtworkAnimatedVisibilityScope

import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.julystar.musicapp.feature.artist.presentation.ArtistRoot

fun NavGraphBuilder.artistGraph(
    onNavigateBack: () -> Unit,
    onNavigateToAlbum: (albumId: Long) -> Unit,
) {
    composable<MusicGraph.Artist> {
        val animatedVisibilityScope = this
        CompositionLocalProvider(
            LocalDetailArtworkAnimatedVisibilityScope provides animatedVisibilityScope,
        ) {
            ArtistRoot(
                onNavigateBack = onNavigateBack,
                onNavigateToAlbum = onNavigateToAlbum,
            )
        }
    }
}
