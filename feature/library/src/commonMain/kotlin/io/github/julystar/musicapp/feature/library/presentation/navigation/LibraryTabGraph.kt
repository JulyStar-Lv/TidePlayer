package io.github.julystar.musicapp.feature.library.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import io.github.julystar.musicapp.feature.library.presentation.LibraryRoot
import io.github.julystar.musicapp.feature.library.presentation.LibraryDesktopSection

@Composable
fun LibraryTabGraph(
    navController: NavHostController,
    desktopSection: LibraryDesktopSection = LibraryDesktopSection.Songs,
    onNavigateToLibraryFolderImport: () -> Unit = {},
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (Long) -> Unit = {},
    onNavigateToPlaylist: (Long) -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToPlaylists: () -> Unit = {},
) {
    NavHost(
        navController = navController,
        startDestination = "library",
    ) {
        composable("library") {
            LibraryRoot(
                desktopSection = desktopSection,
                onNavigateToLibraryFolderImport = onNavigateToLibraryFolderImport,
                onNavigateToAlbum = onNavigateToAlbum,
                onNavigateToArtist = onNavigateToArtist,
                onNavigateToPlaylist = onNavigateToPlaylist,
                onNavigateToFavorites = onNavigateToFavorites,
                onNavigateToPlaylists = onNavigateToPlaylists,
            )
        }
    }
}
