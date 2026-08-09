package io.github.julystar.musicapp.feature.search.presentation.navigation

import io.github.julystar.musicapp.core.presentation.navigation.MusicGraph
import io.github.julystar.musicapp.core.presentation.transition.LocalDetailArtworkAnimatedVisibilityScope

import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import io.github.julystar.musicapp.feature.search.presentation.SearchRoot

fun NavGraphBuilder.searchGraph(
    navController: NavHostController? = null,
) {
    composable<MusicGraph.Search> {
        val animatedVisibilityScope = this
        CompositionLocalProvider(
            LocalDetailArtworkAnimatedVisibilityScope provides animatedVisibilityScope,
        ) {
            SearchRoot(
                onNavigateToAlbum = { albumId ->
                    navController?.navigate(MusicGraph.Album(id = albumId))
                },
                onNavigateToArtist = { artistId ->
                    navController?.navigate(MusicGraph.Artist(id = artistId))
                },
            )
        }
    }
}
