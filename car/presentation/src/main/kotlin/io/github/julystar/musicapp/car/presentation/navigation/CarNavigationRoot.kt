package io.github.julystar.musicapp.car.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.car.presentation.component.CarAppWindowHeader
import io.github.julystar.musicapp.car.presentation.component.CarMiniPlayer
import io.github.julystar.musicapp.car.presentation.component.CarNavigationItem
import io.github.julystar.musicapp.car.presentation.component.carInteractiveSurface
import io.github.julystar.musicapp.car.presentation.focus.CarFocusHost
import io.github.julystar.musicapp.car.presentation.focus.CarFocusId
import io.github.julystar.musicapp.car.presentation.focus.CarFocusIds
import io.github.julystar.musicapp.car.presentation.focus.carFocusTarget
import io.github.julystar.musicapp.car.presentation.focus.carInputRouter
import io.github.julystar.musicapp.car.presentation.focus.rememberCarFocusCoordinator
import io.github.julystar.musicapp.car.presentation.layout.CarLayoutMetrics
import io.github.julystar.musicapp.car.presentation.layout.CarLayoutProfile
import io.github.julystar.musicapp.car.presentation.icon.CarIcon
import io.github.julystar.musicapp.car.presentation.nowplaying.CarNowPlayingScreen
import io.github.julystar.musicapp.car.presentation.nowplaying.CarFullscreenNowPlayingScreen
import io.github.julystar.musicapp.car.presentation.nowplaying.CarNowPlayingAction
import io.github.julystar.musicapp.car.presentation.nowplaying.CarNowPlayingViewModel
import io.github.julystar.musicapp.car.presentation.screen.CarAlbumsScreen
import io.github.julystar.musicapp.car.presentation.screen.CarAlbumDetailScreen
import io.github.julystar.musicapp.car.presentation.screen.CarArtistDetailScreen
import io.github.julystar.musicapp.car.presentation.screen.CarArtistsScreen
import io.github.julystar.musicapp.car.presentation.screen.CarHomeScreen
import io.github.julystar.musicapp.car.presentation.screen.CarLibraryViewModel
import io.github.julystar.musicapp.car.presentation.screen.CarPageState
import io.github.julystar.musicapp.car.presentation.screen.CarPlaylistsScreen
import io.github.julystar.musicapp.car.presentation.screen.CarPlaylistDetailScreen
import io.github.julystar.musicapp.car.presentation.screen.CarSearchScreen
import io.github.julystar.musicapp.car.presentation.screen.CarSettingsScreen
import io.github.julystar.musicapp.car.presentation.screen.CarSongsScreen
import io.github.julystar.musicapp.car.presentation.theme.LocalCarColors
import io.github.julystar.musicapp.car.presentation.theme.LocalCarShapes
import io.github.julystar.musicapp.car.presentation.theme.LocalCarTypography
import io.github.julystar.musicapp.core.domain.repository.ArtworkRepository
import io.github.julystar.musicapp.core.domain.model.LyricDisplaySettings
import io.github.julystar.musicapp.service.playback.domain.PlayerState
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CarNavigationRoot(
    metrics: CarLayoutMetrics?,
    lyricDisplaySettings: LyricDisplaySettings = LyricDisplaySettings.Default,
    onExit: () -> Unit,
    onEnterFullscreen: () -> Unit = {},
    onExitPlayback: () -> Unit = {},
    onExitFullscreen: () -> Unit = {},
    exitPlaybackRequest: Long = 0L,
    modifier: Modifier = Modifier,
) {
    val safeMetrics = metrics?.takeIf { it.metricsAvailable }
    if (safeMetrics == null) {
        CarPageState("无法读取当前窗口布局", modifier)
        return
    }
    var route by rememberSaveable { mutableStateOf(CarRoute.Home) }
    var previousRootRoute by rememberSaveable { mutableStateOf(CarRoute.Home) }
    val screenStateHolder = rememberSaveableStateHolder()
    if (safeMetrics.profile == CarLayoutProfile.FullscreenCockpit) {
        CarFullscreenNowPlayingScreen(
            metrics = safeMetrics,
            lyricDisplaySettings = lyricDisplaySettings,
            onExitPlayback = onExitPlayback,
            onExitFullscreen = onExitFullscreen,
            modifier = modifier,
        )
        return
    }
    val libraryViewModel = koinViewModel<CarLibraryViewModel>()
    val libraryState by libraryViewModel.state.collectAsState()
    val nowPlayingViewModel = koinViewModel<CarNowPlayingViewModel>()
    val nowPlayingState by nowPlayingViewModel.state.collectAsState()
    val artworkRepository = koinInject<ArtworkRepository>()
    var detailTarget by remember { mutableStateOf<CarDetailTarget?>(null) }
    var parentDetailTarget by remember { mutableStateOf<CarDetailTarget?>(null) }

    LaunchedEffect(exitPlaybackRequest) {
        if (exitPlaybackRequest > 0L && route == CarRoute.NowPlaying) route = previousRootRoute
    }
    val focusCoordinator = rememberCarFocusCoordinator()
    val focusManager = LocalFocusManager.current

    fun openDetail(target: CarDetailTarget) {
        parentDetailTarget = null
        detailTarget = target
    }

    fun closeDetail() {
        detailTarget = parentDetailTarget
        parentDetailTarget = null
    }

    BackHandler(enabled = detailTarget != null || route != CarRoute.Home) {
        if (detailTarget != null) closeDetail()
        else route = when (route) {
            CarRoute.NowPlaying, CarRoute.Search -> previousRootRoute
            else -> CarRoute.Home
        }
    }

    val focusRoute = detailTarget?.focusRoute ?: route.name
    val initialFocus = when {
        detailTarget != null -> CarFocusIds.DetailBack
        route == CarRoute.NowPlaying -> CarFocusIds.NowPlayingCollapse
        else -> route.navigationFocusId
    }
    CarFocusHost(focusCoordinator, focusRoute, initialFocus, safeMetrics.profile) {
        if (route == CarRoute.NowPlaying) {
            CarNowPlayingScreen(
                metrics = safeMetrics,
                lyricDisplaySettings = lyricDisplaySettings,
                focusCoordinator = focusCoordinator,
                onCollapse = { route = previousRootRoute },
                onEnterFullscreen = onEnterFullscreen,
                modifier = modifier.carInputRouter(
                    focusManager = focusManager,
                    onPlayPause = { nowPlayingViewModel.onAction(CarNowPlayingAction.PlayPause) },
                    onNext = { nowPlayingViewModel.onAction(CarNowPlayingAction.Next) },
                    onPrevious = { nowPlayingViewModel.onAction(CarNowPlayingAction.Previous) },
                    onStop = { nowPlayingViewModel.onAction(CarNowPlayingAction.Pause) },
                ),
            )
        } else Box(modifier = modifier.carInputRouter(
            focusManager = focusManager,
            onPlayPause = { nowPlayingViewModel.onAction(CarNowPlayingAction.PlayPause) },
            onNext = { nowPlayingViewModel.onAction(CarNowPlayingAction.Next) },
            onPrevious = { nowPlayingViewModel.onAction(CarNowPlayingAction.Previous) },
            onStop = { nowPlayingViewModel.onAction(CarNowPlayingAction.Pause) },
        )) {
        CarAppWindowHeader(
            onExit = onExit,
            modifier = Modifier
                .offset(safeMetrics.headerStart, safeMetrics.headerTop)
                .widthIn(min = safeMetrics.navigationRailWidth)
                .height(safeMetrics.headerHeight),
        )
        NavigationRail(
            metrics = safeMetrics,
            route = route,
            playerState = nowPlayingState.player,
            artist = nowPlayingState.miniPlayerArtist,
            artworkRepository = artworkRepository,
            onPrevious = { nowPlayingViewModel.onAction(CarNowPlayingAction.Previous) },
            onToggle = { nowPlayingViewModel.onAction(CarNowPlayingAction.PlayPause) },
            onNext = { nowPlayingViewModel.onAction(CarNowPlayingAction.Next) },
            contentFocusId = if (detailTarget != null) {
                CarFocusIds.DetailBack
            } else if (route == CarRoute.Settings) {
                CarFocusIds.item("settings_nav", "Playback")
            } else {
                CarFocusIds.content(route.name)
            },
            onRoute = {
                parentDetailTarget = null
                detailTarget = null
                route = it
            },
            onOpenNowPlaying = {
                previousRootRoute = route
                route = CarRoute.NowPlaying
            },
            modifier = Modifier
                .offset(safeMetrics.navigationRailStart, safeMetrics.navigationRailTop)
                .size(
                    safeMetrics.navigationRailWidth,
                    safeMetrics.contentSize.height - safeMetrics.navigationRailTop - safeMetrics.navigationRailBottom,
                ),
        )
        val contentModifier = Modifier
            .offset(x = safeMetrics.shellWidth)
            .width(safeMetrics.contentSize.width - safeMetrics.shellWidth)
            .fillMaxHeight()
        when (val detail = detailTarget) {
            is CarDetailTarget.Album -> CarAlbumDetailScreen(
                detail.id,
                safeMetrics,
                route.navigationFocusId,
                ::closeDetail,
                contentModifier,
            )
            is CarDetailTarget.Artist -> CarArtistDetailScreen(
                detail.id,
                safeMetrics,
                route.navigationFocusId,
                ::closeDetail,
                {
                    parentDetailTarget = detail
                    detailTarget = CarDetailTarget.Album(it)
                },
                contentModifier,
            )
            is CarDetailTarget.Playlist -> CarPlaylistDetailScreen(
                detail.id,
                detail.title,
                safeMetrics,
                route.navigationFocusId,
                ::closeDetail,
                contentModifier,
            )
            null -> screenStateHolder.SaveableStateProvider(route.name) {
                when (route) {
            CarRoute.Home -> CarHomeScreen(
                metrics = safeMetrics,
                loading = !libraryState.initialLoadComplete,
                error = libraryState.loadError,
                tracks = libraryState.tracks,
                albums = libraryState.albums,
                artists = libraryState.artists,
                playlists = libraryState.playlists,
                artworkRepository = artworkRepository,
                onPlay = libraryViewModel::play,
                onAlbumClick = { openDetail(CarDetailTarget.Album(it)) },
                onArtistClick = { openDetail(CarDetailTarget.Artist(it)) },
                onPlaylistClick = { openDetail(CarDetailTarget.Playlist(it.id, it.title)) },
                onOpenSearch = {
                    previousRootRoute = route
                    route = CarRoute.Search
                },
                modifier = contentModifier,
            )
            CarRoute.Songs -> CarSongsScreen(
                safeMetrics, !libraryState.initialLoadComplete, libraryState.loadError,
                libraryState.tracks, libraryState.currentTrackId,
                artworkRepository, libraryViewModel::play,
                onOpenAlbums = { route = CarRoute.Albums },
                onOpenArtists = { route = CarRoute.Artists },
                modifier = contentModifier,
            )
            CarRoute.Albums -> CarAlbumsScreen(
                safeMetrics, !libraryState.initialLoadComplete, libraryState.loadError,
                libraryState.albums, libraryState.tracks, artworkRepository,
                onOpenSongs = { route = CarRoute.Songs },
                onOpenArtists = { route = CarRoute.Artists },
                onAlbumClick = { openDetail(CarDetailTarget.Album(it)) },
                modifier = contentModifier,
            )
            CarRoute.Artists -> CarArtistsScreen(
                safeMetrics, !libraryState.initialLoadComplete, libraryState.loadError,
                libraryState.artists, libraryState.albums, libraryState.tracks, artworkRepository,
                onOpenSongs = { route = CarRoute.Songs },
                onOpenAlbums = { route = CarRoute.Albums },
                onArtistClick = { openDetail(CarDetailTarget.Artist(it)) },
                modifier = contentModifier,
            )
            CarRoute.Playlists -> CarPlaylistsScreen(
                safeMetrics, !libraryState.initialLoadComplete, libraryState.playlists,
                modifier = contentModifier,
            )
            CarRoute.Settings -> CarSettingsScreen(
                safeMetrics,
                contentModifier,
            )
            CarRoute.Search -> CarSearchScreen(
                safeMetrics,
                contentModifier,
            )
            CarRoute.NowPlaying -> Unit
                }
            }
        }
        }
    }
}

private sealed interface CarDetailTarget {
    data class Album(val id: Long) : CarDetailTarget
    data class Artist(val id: Long) : CarDetailTarget
    data class Playlist(val id: Long, val title: String) : CarDetailTarget
}

private val CarDetailTarget.focusRoute: String
    get() = when (this) {
        is CarDetailTarget.Album -> "detail.album.$id"
        is CarDetailTarget.Artist -> "detail.artist.$id"
        is CarDetailTarget.Playlist -> "detail.playlist.$id"
    }

private val CarRoute.navigationFocusId: CarFocusId
    get() = when (this) {
        CarRoute.Home -> CarFocusIds.Home
        CarRoute.Playlists -> CarFocusIds.Playlists
        CarRoute.Settings -> CarFocusIds.Settings
        CarRoute.Songs -> CarFocusIds.Songs
        CarRoute.Albums -> CarFocusIds.Albums
        CarRoute.Artists -> CarFocusIds.Artists
        CarRoute.Search -> CarFocusIds.SearchField
        CarRoute.NowPlaying -> CarFocusIds.NowPlayingCollapse
    }

@Composable
private fun NavigationRail(
    metrics: CarLayoutMetrics,
    route: CarRoute,
    playerState: PlayerState,
    artist: String?,
    artworkRepository: ArtworkRepository,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    contentFocusId: CarFocusId,
    onRoute: (CarRoute) -> Unit,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier,
) {
    val colors = LocalCarColors.current
    val railShape = RoundedCornerShape(26.dp)
    Box(
        modifier = modifier
            .background(colors.backgroundSubtle, railShape)
            .border(1.dp, colors.borderSubtle, railShape),
    ) {
        val primaryRoutes = listOf(CarRoute.Home, CarRoute.Songs, CarRoute.Playlists, CarRoute.Settings)
        primaryRoutes.forEachIndexed { index, item ->
            val down = primaryRoutes.getOrNull(index + 1)?.navigationFocusId
                ?: CarFocusIds.MiniPlayer
            CarNavigationItem(
                label = item.label,
                icon = item.icon,
                selected = route == item,
                enabled = true,
                iconSize = metrics.iconSize,
                onClick = { onRoute(item) },
                modifier = Modifier
                    .carFocusTarget(
                        id = item.navigationFocusId,
                        up = primaryRoutes.getOrNull(index - 1)?.navigationFocusId ?: CarFocusIds.Exit,
                        down = down,
                        right = if (route == item) contentFocusId else null,
                    )
                    .offset(
                        x = metrics.navigationRailInnerPadding,
                        y = metrics.navigationPrimaryTop + metrics.navigationItemInterval * index.toFloat(),
                    )
                    .width(metrics.navigationRailWidth - metrics.navigationRailInnerPadding * 2f)
                    .height(metrics.navigationItemHeight),
            )
        }
        val railHeight = metrics.contentSize.height - metrics.navigationRailTop - metrics.navigationRailBottom
        CarMiniPlayer(
            state = playerState,
            artworkRepository = artworkRepository,
            height = metrics.miniPlayerHeight,
            controlSize = metrics.iconSize,
            compact = metrics.profile == CarLayoutProfile.VehiclePanel,
            artist = artist,
            onOpen = onOpenNowPlaying,
            onPrevious = onPrevious,
            onToggle = onToggle,
            onNext = onNext,
            modifier = Modifier
                .carFocusTarget(
                    id = CarFocusIds.MiniPlayer,
                    up = CarFocusIds.Settings,
                    right = contentFocusId,
                )
                .offset(
                    x = metrics.navigationRailInnerPadding,
                    y = railHeight - metrics.miniPlayerBottom - metrics.miniPlayerHeight,
                )
                .width(metrics.navigationRailWidth - metrics.navigationRailInnerPadding * 2f),
        )
    }
}
