package io.github.julystar.musicapp.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import io.github.julystar.musicapp.core.isRouteHome
import io.github.julystar.musicapp.core.isRouteLyrics
import io.github.julystar.musicapp.core.isRouteNowPlaying
import io.github.julystar.musicapp.core.presentation.components.DesignGlassOverlayScene
import io.github.julystar.musicapp.core.presentation.components.DesignStickyGlassActionBar
import io.github.julystar.musicapp.core.presentation.components.DesignStickyHeaderState
import io.github.julystar.musicapp.core.presentation.components.LocalDesignStickyHeaderStateSink
import io.github.julystar.musicapp.core.presentation.components.getBottomBarSpace
import io.github.julystar.musicapp.core.presentation.layout.WindowSizeClass
import io.github.julystar.musicapp.core.presentation.layout.rememberWindowSizeClass
import io.github.julystar.musicapp.core.presentation.navigation.MusicGraph
import io.github.julystar.musicapp.core.presentation.platform.LocalDesktopTitleBarInset
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.julystar.musicapp.core.presentation.transition.LocalDetailArtworkAnimatedVisibilityScope
import io.github.julystar.musicapp.core.presentation.transition.LocalDetailArtworkSharedTransitionScope
import io.github.julystar.musicapp.feature.album.presentation.navigation.albumGraph
import io.github.julystar.musicapp.feature.artist.presentation.navigation.artistGraph
import io.github.julystar.musicapp.feature.browse.presentation.navigation.browseGraph
import io.github.julystar.musicapp.feature.downloads.presentation.navigation.downloadsGraph
import io.github.julystar.musicapp.feature.importing.presentation.navigation.RouteImportType
import io.github.julystar.musicapp.feature.importing.presentation.navigation.importGraph
import io.github.julystar.musicapp.feature.home.presentation.ListeningRoot
import io.github.julystar.musicapp.feature.lyrics.presentation.navigation.lyricsGraph
import io.github.julystar.musicapp.feature.playlist.presentation.CreatePlaylistRoot
import io.github.julystar.musicapp.feature.playlist.presentation.CreatePlaylistVM
import io.github.julystar.musicapp.feature.playlist.presentation.EditPlaylistRoot
import io.github.julystar.musicapp.feature.playlist.presentation.FavoritesPlaylistRoot
import io.github.julystar.musicapp.feature.playlist.presentation.PlaylistRoot
import io.github.julystar.musicapp.feature.playlist.presentation.PlaylistsListRoot
import io.github.julystar.musicapp.feature.queue.presentation.QueueRoot
import io.github.julystar.musicapp.feature.radio.presentation.navigation.radioGraph
import io.github.julystar.musicapp.feature.recentlyadded.presentation.navigation.recentlyAddedGraph
import io.github.julystar.musicapp.feature.recentlyplayed.presentation.navigation.recentlyPlayedGraph
import io.github.julystar.musicapp.feature.search.presentation.navigation.searchGraph
import io.github.julystar.musicapp.feature.sources.presentation.navigation.sourcesGraph
import io.github.julystar.musicapp.plugin.management.PluginSettingsRoot
import io.github.julystar.musicapp.plugin.management.ManualMetadataSearchDialog
import io.github.julystar.musicapp.service.playback.presentation.nowplaying.NowPlayingTrackItem
import io.github.julystar.musicapp.service.playback.presentation.navigation.playerGraph
import io.github.julystar.musicapp.service.playback.presentation.shell.PlaybackMiniPlayerHost
import io.github.julystar.musicapp.service.playback.presentation.shell.rememberHasPlaybackItem
import io.github.julystar.musicapp.service.playback.presentation.transition.LocalPlayerArtworkAnimatedVisibilityScope
import io.github.julystar.musicapp.service.playback.presentation.transition.LocalPlayerArtworkSharedTransitionScope
import io.github.julystar.musicapp.widgets.appbar.BottomBar
import io.github.julystar.musicapp.widgets.appbar.NavigationRailBar
import io.github.julystar.musicapp.widgets.appbar.SidebarBar
import io.github.julystar.musicapp.widgets.appbar.getNavigationRailWidth
import io.github.julystar.musicapp.widgets.appbar.getSidebarWidth
import org.koin.compose.viewmodel.koinViewModel
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun RootNavHost(
    navController: NavHostController,
    scaffoldPadding: PaddingValues,
) {
    var metadataTrack by remember { mutableStateOf<NowPlayingTrackItem?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    var selectedRootTabName by rememberSaveable { mutableStateOf(HomeTab.HOME.name) }
    val selectedRootTab = HomeTab.entries.firstOrNull { it.name == selectedRootTabName } ?: HomeTab.HOME
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val showRootNavigationChrome = !isImmersivePlayerRoute(currentRoute)
    val onRootTabSelected: (HomeTab) -> Unit = { selectedRootTabName = it.name }
    val playerTransitionDurationMillis = DesignTokens.motion.playerExpandMillis

    // Keep the NavHost identity stable while persistent chrome animates independently.
    val movableNavigationContent = remember(navController, playerTransitionDurationMillis) {
        movableContentOf<RootNavigationContentArgs> { args ->
        NavHost(
            modifier = args.modifier,
            navController = navController,
            startDestination = MusicGraph.Home,
            enterTransition = {
                if (isArtworkDetailTransition(
                        initialRoute = initialState.destination.route,
                        targetRoute = targetState.destination.route,
                    )
                ) {
                    immediateEnterTransition(playerTransitionDurationMillis)
                } else if (isImmersivePlayerRoute(targetState.destination.route)) {
                    immediateEnterTransition(playerTransitionDurationMillis)
                } else {
                    slideIn(
                        animationSpec = tween(300),
                        initialOffset = { fullSize -> IntOffset(fullSize.width, 0) },
                    )
                }
            },
            exitTransition = {
                if (isArtworkDetailTransition(
                        initialRoute = initialState.destination.route,
                        targetRoute = targetState.destination.route,
                    )
                ) {
                    immediateExitTransition(playerTransitionDurationMillis)
                } else if (isImmersivePlayerRoute(targetState.destination.route)) {
                    immediateExitTransition(playerTransitionDurationMillis)
                } else {
                    slideOut(
                        animationSpec = tween(300),
                        targetOffset = { fullSize -> IntOffset(-fullSize.width, 0) },
                    )
                }
            },
            popEnterTransition = {
                if (isArtworkDetailTransition(
                        initialRoute = initialState.destination.route,
                        targetRoute = targetState.destination.route,
                    )
                ) {
                    immediateEnterTransition(playerTransitionDurationMillis)
                } else if (isImmersivePlayerRoute(initialState.destination.route)) {
                    immediateEnterTransition(playerTransitionDurationMillis)
                } else {
                    slideIn(
                        animationSpec = tween(300),
                        initialOffset = { fullSize -> IntOffset(fullSize.width, 0) },
                    )
                }
            },
            popExitTransition = {
                if (isArtworkDetailTransition(
                        initialRoute = initialState.destination.route,
                        targetRoute = targetState.destination.route,
                    )
                ) {
                    immediateExitTransition(playerTransitionDurationMillis)
                } else if (isImmersivePlayerRoute(initialState.destination.route)) {
                    immediateExitTransition(playerTransitionDurationMillis)
                } else {
                    slideOut(
                        animationSpec = tween(300),
                        targetOffset = { fullSize -> IntOffset(-fullSize.width, 0) },
                    )
                }
            },
        ) {
        homeGraph(
            scaffoldPadding = args.scaffoldPadding,
            currentTab = args.selectedRootTab,
            onTabSelected = args.onRootTabSelected,
            onOpenQueue = args.onOpenQueue,
        )
        albumGraph(
            onNavigateBack = { navController.popBackStack() },
        )
        artistGraph(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToAlbum = { albumId ->
                navController.navigate(MusicGraph.Album(id = albumId))
            },
        )
        composable<MusicGraph.Playlists> {
            val animatedVisibilityScope = this
            CompositionLocalProvider(
                LocalDetailArtworkAnimatedVisibilityScope provides animatedVisibilityScope,
            ) {
                val createPlaylistVM: CreatePlaylistVM = koinViewModel()
                PlaylistsListRoot(
                    onNavigateToPlaylist = { id ->
                        navController.navigate(MusicGraph.Playlist(id))
                    },
                    onCreatePlaylist = createPlaylistVM::openModal,
                )
                CreatePlaylistRoot(
                    createPlaylistVM = createPlaylistVM,
                    onNavigateToImport = {
                        navController.navigate(MusicGraph.Import(RouteImportType.EditPlaylist))
                    },
                    onNavigateToCoverImport = {
                        navController.navigate(MusicGraph.Import(RouteImportType.EditPlaylistCover))
                    },
                )
            }
        }
        composable<MusicGraph.Playlist> {
            val animatedVisibilityScope = this
            CompositionLocalProvider(
                LocalDetailArtworkAnimatedVisibilityScope provides animatedVisibilityScope,
            ) {
                PlaylistRoot(
                    scaffoldPadding = args.scaffoldPadding,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToImport = {
                        navController.navigate(MusicGraph.Import(RouteImportType.Music))
                    },
                )
                EditPlaylistRoot(
                    onNavigateToCoverImport = {
                        navController.navigate(MusicGraph.Import(RouteImportType.EditPlaylistCover))
                    },
                )
            }
        }
        composable<MusicGraph.Favorites> {
            val animatedVisibilityScope = this
            CompositionLocalProvider(
                LocalDetailArtworkAnimatedVisibilityScope provides animatedVisibilityScope,
            ) {
                FavoritesPlaylistRoot(
                    scaffoldPadding = args.scaffoldPadding,
                    onNavigateBack = { navController.popBackStack() },
                )
            }
        }
        browseGraph(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToAlbum = { albumId ->
                navController.navigate(MusicGraph.Album(id = albumId))
            },
            onNavigateToArtist = { artistId ->
                navController.navigate(MusicGraph.Artist(id = artistId))
            },
            onNavigateToGenre = { genre ->
                navController.navigate(MusicGraph.BrowseGenre(genre = genre))
            },
        )
        radioGraph(navController)
        recentlyAddedGraph(navController)
        recentlyPlayedGraph(navController)
        composable<MusicGraph.Listening> {
            ListeningRoot(onNavigateBack = { navController.popBackStack() })
        }
        lyricsGraph(navController)
        sourcesGraph(
            onNavigateBack = { navController.navigateUp() },
            onNavigateToLibraryFolderImport = {
                navController.navigate(MusicGraph.Import(RouteImportType.LibraryFolder))
            },
        )
        importGraph(
            onNavigateBack = { navController.popBackStack() },
        )
        composable<MusicGraph.PluginSettings> {
            PluginSettingsRoot(onBack = { navController.popBackStack() })
        }
        searchGraph(navController)
        downloadsGraph()
        playerGraph(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToLyrics = { trackId -> navController.navigate(MusicGraph.Lyrics(trackId)) },
            onOpenQueue = args.onOpenQueue,
            onNavigateToLyricImport = {
                navController.navigate(MusicGraph.Import(RouteImportType.Lyric))
            },
            onSearchMetadata = args.onSearchMetadata,
        )
        }
        }
    }
    val navigationContent: @Composable (Modifier) -> Unit = { modifier ->
        movableNavigationContent(
            RootNavigationContentArgs(
                modifier = modifier,
                scaffoldPadding = scaffoldPadding,
                selectedRootTab = selectedRootTab,
                onRootTabSelected = onRootTabSelected,
                onOpenQueue = { showQueue = true },
                onSearchMetadata = { track -> metadataTrack = track },
            ),
        )
    }
    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        val sharedTransitionScope = this
        CompositionLocalProvider(
            LocalPlayerArtworkSharedTransitionScope provides sharedTransitionScope,
            LocalDetailArtworkSharedTransitionScope provides sharedTransitionScope,
        ) {
            SecondaryRootNavigationLayout(
                currentTab = selectedRootTab,
                onTabSelected = { tab ->
                    onRootTabSelected(tab)
                    if (!navController.popBackStack<MusicGraph.Home>(inclusive = false)) {
                        navController.navigate(MusicGraph.Home)
                    }
                },
                scaffoldPadding = scaffoldPadding,
                onOpenNowPlaying = { navController.navigate(MusicGraph.NowPlaying) },
                onOpenQueue = { showQueue = true },
                captureStickyHeader = shouldCaptureSecondaryStickyHeader(currentRoute),
                showChrome = showRootNavigationChrome,
                transitionDurationMillis = playerTransitionDurationMillis,
                content = navigationContent,
            )
            ManualMetadataSearchDialog(
                track = metadataTrack,
                onDismiss = { metadataTrack = null },
            )
            QueueRoot(
                show = showQueue,
                coverNowPlayingLyrics = isRouteNowPlaying(currentRoute),
                onDismiss = { showQueue = false },
            )
        }
    }
}

private data class RootNavigationContentArgs(
    val modifier: Modifier,
    val scaffoldPadding: PaddingValues,
    val selectedRootTab: HomeTab,
    val onRootTabSelected: (HomeTab) -> Unit,
    val onOpenQueue: () -> Unit,
    val onSearchMetadata: (NowPlayingTrackItem) -> Unit,
)

private fun immediateEnterTransition(durationMillis: Int) = fadeIn(
    initialAlpha = 0f,
    animationSpec = keyframes {
        this.durationMillis = durationMillis
        1f at 1
    },
)

private fun immediateExitTransition(durationMillis: Int) = fadeOut(
    targetAlpha = 0f,
    animationSpec = keyframes {
        this.durationMillis = durationMillis
        0f at 1
    },
)

internal fun shouldShowPersistentMiniPlayer(route: String?): Boolean =
    !isRouteHome(route) && !isImmersivePlayerRoute(route)

internal fun isImmersivePlayerRoute(route: String?): Boolean =
    isRouteNowPlaying(route) || isRouteLyrics(route)

internal fun isArtworkDetailRoute(route: String?): Boolean {
    val routeName = route?.substringBefore('/') ?: return false
    return routeName == "Album" || routeName.endsWith(".Album") ||
        routeName == "Playlist" || routeName.endsWith(".Playlist") ||
        routeName == "Favorites" || routeName.endsWith(".Favorites")
}

private fun isArtworkDetailTransition(initialRoute: String?, targetRoute: String?): Boolean =
    isArtworkDetailRoute(initialRoute) || isArtworkDetailRoute(targetRoute)

internal fun shouldCaptureSecondaryStickyHeader(route: String?): Boolean {
    if (isRouteHome(route)) return true
    val routeName = route?.substringBefore('/') ?: return false
    return routeName == "Album" || routeName.endsWith(".Album") ||
        routeName == "Playlist" || routeName.endsWith(".Playlist") ||
        routeName == "Favorites" || routeName.endsWith(".Favorites") ||
        routeName == "Listening" || routeName.endsWith(".Listening") ||
        routeName == "PluginSettings" || routeName.endsWith(".PluginSettings")
}

internal fun shouldHoistSecondaryStickyHeader(
    captureStickyHeader: Boolean,
    windowSizeClass: WindowSizeClass,
): Boolean = captureStickyHeader && windowSizeClass == WindowSizeClass.Compact

@Composable
private fun SecondaryRootNavigationLayout(
    currentTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    scaffoldPadding: PaddingValues,
    onOpenNowPlaying: () -> Unit,
    onOpenQueue: () -> Unit,
    captureStickyHeader: Boolean,
    showChrome: Boolean,
    transitionDurationMillis: Int,
    content: @Composable (Modifier) -> Unit,
) {
    val titleBarInset = LocalDesktopTitleBarInset.current
    val hasPlaybackItem = rememberHasPlaybackItem()
    val miniPlayerContent: @Composable () -> Unit = {
        PlaybackMiniPlayerHost(
            onOpenNowPlaying = onOpenNowPlaying,
            onOpenQueue = onOpenQueue,
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val windowSizeClass = rememberWindowSizeClass(
            containerSize = androidx.compose.ui.unit.DpSize(maxWidth, maxHeight),
        )
        val statusBarInset = WindowInsets.statusBars
            .asPaddingValues()
            .calculateTopPadding() + titleBarInset
        val hoistStickyHeader = shouldHoistSecondaryStickyHeader(
            captureStickyHeader = captureStickyHeader,
            windowSizeClass = windowSizeClass,
        )
        var stickyHeaderState by remember(hoistStickyHeader) {
            mutableStateOf<DesignStickyHeaderState?>(null)
        }
        val stickyHeaderStateSink = remember(hoistStickyHeader) {
            OwnedDesignStickyHeaderStateSink { state -> stickyHeaderState = state }
        }
        val sideNavigationWidth = when (windowSizeClass) {
            WindowSizeClass.Compact -> 0.dp
            WindowSizeClass.Medium -> getNavigationRailWidth(windowSizeClass)
            WindowSizeClass.Expanded,
            WindowSizeClass.Large,
            WindowSizeClass.XL -> getSidebarWidth(windowSizeClass)
        }
        val contentModifier = if (showChrome) {
            Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
                .statusBarsPadding()
                .padding(
                    start = sideNavigationWidth,
                    top = titleBarInset,
                )
        } else {
            Modifier.fillMaxSize()
        }

        DesignGlassOverlayScene(
            modifier = Modifier.fillMaxSize(),
            captureBackdrop = showChrome,
            contentBottomInset = when {
                !showChrome -> 0.dp
                windowSizeClass == WindowSizeClass.Compact ->
                    getBottomBarSpace(hasPlaybackItem, scaffoldPadding)
                hasPlaybackItem ->
                    DesignTokens.player.miniBarHeight + DesignTokens.spacing.xs
                else -> 0.dp
            },
            backdropContent = {
                Box(modifier = contentModifier) {
                    CompositionLocalProvider(
                        LocalDesignStickyHeaderStateSink provides
                            stickyHeaderStateSink.takeIf { hoistStickyHeader },
                    ) {
                        content(Modifier.fillMaxSize())
                    }
                }
            },
            overlayContent = {
                AnimatedVisibility(
                    visible = showChrome,
                    modifier = Modifier.fillMaxSize(),
                    enter = immediateEnterTransition(transitionDurationMillis),
                    exit = immediateExitTransition(transitionDurationMillis),
                ) {
                    val chromeVisibilityScope = this
                    CompositionLocalProvider(
                        LocalPlayerArtworkAnimatedVisibilityScope provides chromeVisibilityScope,
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (windowSizeClass) {
                                WindowSizeClass.Compact -> {
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
                                        showMiniPlayer = hasPlaybackItem,
                                        showChrome = true,
                                        scaffoldPadding = scaffoldPadding,
                                    )
                                }

                                WindowSizeClass.Medium -> {
                                    NavigationRailBar(
                                        currentTab = currentTab,
                                        onTabSelected = onTabSelected,
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .fillMaxHeight()
                                            .statusBarsPadding(),
                                        windowSizeClass = windowSizeClass,
                                    )
                                    if (hasPlaybackItem) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .padding(
                                                    start = sideNavigationWidth + 12.dp,
                                                    top = 8.dp,
                                                    end = 12.dp,
                                                    bottom = 8.dp,
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            miniPlayerContent()
                                        }
                                    }
                                }

                                WindowSizeClass.Expanded,
                                WindowSizeClass.Large,
                                WindowSizeClass.XL -> {
                                    SidebarBar(
                                        currentTab = currentTab,
                                        onTabSelected = onTabSelected,
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .fillMaxHeight()
                                            .statusBarsPadding(),
                                        windowSizeClass = windowSizeClass,
                                    )
                                    if (hasPlaybackItem) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .padding(
                                                    start = sideNavigationWidth + 12.dp,
                                                    top = 8.dp,
                                                    end = 12.dp,
                                                    bottom = 8.dp,
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            miniPlayerContent()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
        )
    }
}
