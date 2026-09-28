package io.github.julystar.musicapp.service.playback.presentation.navigation

import io.github.julystar.musicapp.core.presentation.navigation.MusicGraph
import io.github.julystar.musicapp.service.playback.presentation.nowplaying.NowPlayingRoot
import io.github.julystar.musicapp.service.playback.presentation.nowplaying.NowPlayingTrackItem
import io.github.julystar.musicapp.service.playback.presentation.sleep.TimeToPauseModal
import io.github.julystar.musicapp.service.playback.presentation.transition.LocalPlayerArtworkAnimatedVisibilityScope

import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

fun NavGraphBuilder.playerGraph(
    onNavigateBack: () -> Unit,
    onNavigateToLyrics: (Long) -> Unit,
    onOpenQueue: () -> Unit,
    onNavigateToLyricImport: () -> Unit,
    onSearchMetadata: (NowPlayingTrackItem) -> Unit,
    onOpenMetadataSources: () -> Unit,
) {
    composable<MusicGraph.NowPlaying> {
        val animatedVisibilityScope = this
        CompositionLocalProvider(
            LocalPlayerArtworkAnimatedVisibilityScope provides animatedVisibilityScope,
        ) {
            NowPlayingRoot(
                onNavigateBack = onNavigateBack,
                onNavigateToLyrics = onNavigateToLyrics,
                onOpenQueue = onOpenQueue,
                onNavigateToLyricImport = onNavigateToLyricImport,
                onSearchMetadata = onSearchMetadata,
                onOpenMetadataSources = onOpenMetadataSources,
            )
            TimeToPauseModal()
        }
    }
}
