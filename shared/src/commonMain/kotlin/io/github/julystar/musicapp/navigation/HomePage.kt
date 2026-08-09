package io.github.julystar.musicapp.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.julystar.musicapp.core.LocalNavController
import io.github.julystar.musicapp.core.isRouteHome
import io.github.julystar.musicapp.core.isRouteNowPlaying
import io.github.julystar.musicapp.core.presentation.layout.WindowSizeClass
import io.github.julystar.musicapp.core.presentation.layout.rememberWindowSizeClass
import io.github.julystar.musicapp.core.presentation.navigation.MusicGraph
import io.github.julystar.musicapp.core.presentation.platform.LocalDesktopTitleBarInset
import io.github.julystar.musicapp.core.presentation.components.DesignGlassOverlayScene
import io.github.julystar.musicapp.core.presentation.components.DesignStickyGlassActionBar
import io.github.julystar.musicapp.core.presentation.components.DesignStickyHeaderState
import io.github.julystar.musicapp.core.presentation.components.DesignStickyHeaderStateSink
import io.github.julystar.musicapp.core.presentation.components.LocalDesignStickyHeaderStateSink
import io.github.julystar.musicapp.core.presentation.components.getBottomBarSpace
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.julystar.musicapp.feature.importing.presentation.navigation.RouteImportType
import io.github.julystar.musicapp.feature.settings.presentation.navigation.navigateToSourceSettings
import io.github.julystar.musicapp.service.playback.presentation.shell.PlaybackMiniPlayerHost
import io.github.julystar.musicapp.service.playback.presentation.shell.rememberHasPlaybackItem
import io.github.julystar.musicapp.widgets.appbar.BottomBar
import io.github.julystar.musicapp.widgets.appbar.NavigationRailBar
import io.github.julystar.musicapp.widgets.appbar.SidebarBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomePage(
    scaffoldPadding: PaddingValues,
    currentTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    onOpenQueue: () -> Unit,
    hostedByRootNavigationLayout: Boolean = false,
) {
    val titleBarInset = LocalDesktopTitleBarInset.current
    val globalNavController = LocalNavController.current
    val currentRootBackStackEntry by globalNavController.currentBackStackEntryAsState()
    val currentRootRoute = currentRootBackStackEntry?.destination?.route.orEmpty()
    val showHomeChrome = isRouteHome(currentRootRoute) || isRouteNowPlaying(currentRootRoute)
    val hasPlaybackItem = rememberHasPlaybackItem()
    val showMiniPlayer = showHomeChrome && hasPlaybackItem
    val onOpenNowPlaying = {
        globalNavController.navigate(MusicGraph.NowPlaying)
    }
    val onNavigateToDownloads = {
        globalNavController.navigate(MusicGraph.Downloads)
    }
    val onNavigateToLibraryFolderImport = {
        globalNavController.navigate(MusicGraph.Import(RouteImportType.LibraryFolder))
    }
    val onNavigateToAlbum = { id: Long ->
        globalNavController.navigate(MusicGraph.Album(id))
    }
    val onNavigateToArtist = { id: Long ->
        globalNavController.navigate(MusicGraph.Artist(id))
    }
    val onNavigateToPlaylist = { id: Long ->
        globalNavController.navigate(MusicGraph.Playlist(id))
    }
    val onNavigateToFavorites = {
        globalNavController.navigate(MusicGraph.Favorites)
    }
    val onNavigateToPlaylists = {
        globalNavController.navigate(MusicGraph.Playlists)
    }
    val miniPlayerContent: @Composable () -> Unit = {
        PlaybackMiniPlayerHost(
            onOpenNowPlaying = onOpenNowPlaying,
            onOpenQueue = onOpenQueue,
        )
    }

    val libraryNavController = rememberNavController()
    val searchNavController = rememberNavController()
    val settingsNavController = rememberNavController()
    var navigateToSourceSettings by remember { mutableStateOf(false) }

    LaunchedEffect(currentTab, navigateToSourceSettings) {
        if (currentTab == HomeTab.SETTINGS && navigateToSourceSettings) {
            settingsNavController.navigateToSourceSettings()
            navigateToSourceSettings = false
        }
    }

    if (hostedByRootNavigationLayout) {
        HomeTabContent(
            currentTab = currentTab,
            libraryNavController = libraryNavController,
            searchNavController = searchNavController,
            settingsNavController = settingsNavController,
            scaffoldPadding = scaffoldPadding,
            onNavigateToDownloads = onNavigateToDownloads,
            onNavigateToLibrary = { onTabSelected(HomeTab.LIBRARY) },
            onNavigateToSourceSettings = {
                navigateToSourceSettings = true
                onTabSelected(HomeTab.SETTINGS)
            },
            onNavigateToSearch = { onTabSelected(HomeTab.SEARCH) },
            onNavigateToLibraryFolderImport = onNavigateToLibraryFolderImport,
            onNavigateToAlbum = onNavigateToAlbum,
            onNavigateToArtist = onNavigateToArtist,
            onNavigateToPlaylist = onNavigateToPlaylist,
            onNavigateToFavorites = onNavigateToFavorites,
            onNavigateToPlaylists = onNavigateToPlaylists,
            stickyHeaderStateSink = LocalDesignStickyHeaderStateSink.current,
        )
        return
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        val windowSizeClass = rememberWindowSizeClass(
            containerSize = androidx.compose.ui.unit.DpSize(maxWidth, maxHeight),
        )
        val statusBarInset = WindowInsets.statusBars
            .asPaddingValues()
            .calculateTopPadding() + titleBarInset
        var stickyHeaderState by remember(currentTab) {
            mutableStateOf<DesignStickyHeaderState?>(null)
        }
        val stickyHeaderStateSink = remember(currentTab) {
            OwnedDesignStickyHeaderStateSink { state -> stickyHeaderState = state }
        }

        val tabContent: @Composable (
            HomeTab,
            PaddingValues,
            DesignStickyHeaderStateSink?,
        ) -> Unit = { tab, contentPadding, stickyHeaderSink ->
            HomeTabContent(
                currentTab = tab,
                libraryNavController = libraryNavController,
                searchNavController = searchNavController,
                settingsNavController = settingsNavController,
                scaffoldPadding = contentPadding,
                onNavigateToDownloads = onNavigateToDownloads,
                onNavigateToLibrary = { onTabSelected(HomeTab.LIBRARY) },
                onNavigateToSourceSettings = {
                    navigateToSourceSettings = true
                    onTabSelected(HomeTab.SETTINGS)
                },
                onNavigateToSearch = { onTabSelected(HomeTab.SEARCH) },
                onNavigateToLibraryFolderImport = onNavigateToLibraryFolderImport,
                onNavigateToAlbum = onNavigateToAlbum,
                onNavigateToArtist = onNavigateToArtist,
                onNavigateToPlaylist = onNavigateToPlaylist,
                onNavigateToFavorites = onNavigateToFavorites,
                onNavigateToPlaylists = onNavigateToPlaylists,
                stickyHeaderStateSink = stickyHeaderSink,
            )
        }

        when (windowSizeClass) {
            WindowSizeClass.Compact -> {
                DesignGlassOverlayScene(
                    modifier = Modifier.fillMaxSize(),
                    contentBottomInset = getBottomBarSpace(showMiniPlayer, scaffoldPadding),
                    backdropContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MiuixTheme.colorScheme.background)
                                .statusBarsPadding()
                                .padding(top = titleBarInset),
                        ) {
                            tabContent(
                                currentTab,
                                PaddingValues(
                                    bottom = getBottomBarSpace(showMiniPlayer, scaffoldPadding),
                                ),
                                stickyHeaderStateSink,
                            )
                        }
                    },
                    overlayContent = {
                        stickyHeaderState?.let { state ->
                            DesignStickyGlassActionBar(
                                title = state.title,
                                subtitle = state.subtitle,
                                collapseFraction = state.collapseFraction,
                                statusBarInset = statusBarInset,
                                onNavigateBack = state.onNavigateBack,
                                backContentDescription = state.backContentDescription,
                                actions = state.actions,
                                centerTitle = true,
                                compactTitle = state.compactTitle,
                                modifier = Modifier.align(Alignment.TopCenter),
                            )
                        }
                        BottomBar(
                            currentTab = currentTab,
                            onTabSelected = onTabSelected,
                            miniPlayerContent = miniPlayerContent,
                            showMiniPlayer = showMiniPlayer,
                            showChrome = showHomeChrome,
                            scaffoldPadding = scaffoldPadding,
                        )
                    },
                )
            }
            WindowSizeClass.Medium -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                ) {
                    NavigationRailBar(
                        currentTab = currentTab,
                        onTabSelected = onTabSelected,
                        modifier = Modifier.fillMaxHeight(),
                        windowSizeClass = windowSizeClass,
                    )
                    RootContentPane(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(top = titleBarInset),
                        showMiniPlayer = showMiniPlayer,
                        miniPlayerContent = miniPlayerContent,
                    ) {
                        tabContent(currentTab, scaffoldPadding, null)
                    }
                }
            }
            WindowSizeClass.Expanded,
            WindowSizeClass.Large,
            WindowSizeClass.XL -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                ) {
                    SidebarBar(
                        currentTab = currentTab,
                        onTabSelected = onTabSelected,
                        modifier = Modifier.fillMaxHeight(),
                        windowSizeClass = windowSizeClass,
                    )
                    RootContentPane(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(top = titleBarInset),
                        showMiniPlayer = showMiniPlayer,
                        miniPlayerContent = miniPlayerContent,
                    ) {
                        tabContent(currentTab, scaffoldPadding, null)
                    }
                }
            }
        }
    }
}

@Composable
internal fun RootContentPane(
    showMiniPlayer: Boolean,
    miniPlayerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!showMiniPlayer) {
        Box(modifier = modifier) { content() }
        return
    }

    DesignGlassOverlayScene(
        modifier = modifier,
        contentBottomInset = DesignTokens.player.miniBarHeight + DesignTokens.spacing.xs,
        backdropContent = { content() },
        overlayContent = {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                miniPlayerContent()
            }
        },
    )
}
