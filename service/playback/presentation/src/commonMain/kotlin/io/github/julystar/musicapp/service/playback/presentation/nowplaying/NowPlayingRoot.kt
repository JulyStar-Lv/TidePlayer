package io.github.julystar.musicapp.service.playback.presentation.nowplaying

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.github.julystar.musicapp.core.domain.model.AppSettings
import io.github.julystar.musicapp.core.domain.model.AppThemeMode as DomainAppThemeMode
import io.github.julystar.musicapp.core.domain.model.PlayerInteractionSettings
import io.github.julystar.musicapp.core.domain.repository.FavoritesRepository
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.core.domain.repository.ToastRepository
import io.github.julystar.musicapp.service.playback.presentation.PlayerVM
import io.github.julystar.musicapp.service.playback.presentation.sleep.SleepModeVM
import io.github.julystar.musicapp.core.presentation.platform.KeepScreenOnEffect
import io.github.julystar.musicapp.core.presentation.platform.PlatformBackHandler
import io.github.julystar.musicapp.core.presentation.platform.StatusBarIconsEffect
import io.github.julystar.musicapp.core.presentation.theme.AppTheme
import io.github.julystar.musicapp.core.presentation.theme.AppThemeMode
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NowPlayingRoot(
    onNavigateBack: () -> Unit,
    onNavigateToLyrics: (Long) -> Unit,
    onOpenQueue: () -> Unit,
    onNavigateToLyricImport: () -> Unit,
    onSearchMetadata: (NowPlayingTrackItem) -> Unit,
    playerViewModel: PlayerVM = koinViewModel(),
    sleepModeViewModel: SleepModeVM = koinViewModel(),
    settingsRepository: SettingsRepository = koinInject(),
    favoritesRepository: FavoritesRepository = koinInject(),
    toastRepository: ToastRepository = koinInject(),
) {
    val state by playerViewModel.nowPlayingState.collectAsState()
    val settings by settingsRepository.settings.collectAsState(AppSettings.Default)
    val systemDarkTheme = isSystemInDarkTheme()
    val appUsesDarkTheme = when (settings.themeMode) {
        DomainAppThemeMode.System -> systemDarkTheme
        DomainAppThemeMode.Light -> false
        DomainAppThemeMode.Dark -> true
    }
    val favoriteTrackIds by favoritesRepository.favoriteTrackIds.collectAsState(emptySet())
    val coroutineScope = rememberCoroutineScope()
    var playerCoversStatusBar by remember { mutableStateOf(false) }
    val exitNowPlaying: () -> Unit = {
        playerCoversStatusBar = false
        onNavigateBack()
    }
    KeepScreenOnEffect(enabled = settings.keepScreenOnInPlayer)
    PlatformBackHandler(onBack = exitNowPlaying)
    StatusBarIconsEffect(
        useLightIcons = shouldUseLightStatusBarIcons(
            playerCoversStatusBar = playerCoversStatusBar,
            appUsesDarkTheme = appUsesDarkTheme,
        ),
    )

    val playbackPosition by playerViewModel.playbackPosition.collectAsState()

    LaunchedEffect(playerViewModel) {
        playerViewModel.nowPlayingEvents.collect { event ->
            when (event) {
                is NowPlayingEvent.ShowMessage -> toastRepository.emit(event.message)
            }
        }
    }

    fun onAction(action: NowPlayingAction) {
        when (action) {
            NowPlayingAction.NavigateBack -> exitNowPlaying()
            NowPlayingAction.AddLyric -> {
                if (state.currentTrack != null) {
                    onNavigateToLyricImport()
                }
            }
            NowPlayingAction.SearchMetadata -> state.currentTrack?.let(onSearchMetadata)
            NowPlayingAction.OpenSleepTimer -> sleepModeViewModel.openModal()
            NowPlayingAction.OpenLyrics -> state.currentTrack?.id?.let(onNavigateToLyrics)
            NowPlayingAction.OpenQueue -> onOpenQueue()
            else -> playerViewModel.onNowPlayingAction(action)
        }
    }

    AppTheme(
        darkTheme = true,
        themeMode = AppThemeMode.Dark,
        manageSystemBars = false,
    ) {
        NowPlayingScreen(
            state = state,
            lyricDisplaySettings = settings.lyrics,
            playerInteractionSettings = settings.playerInteraction,
            currentPositionMs = playbackPosition.positionMs,
            isSeeking = playbackPosition.isSeeking,
            isFavorite = state.currentTrack?.id?.let(favoriteTrackIds::contains) == true,
            onToggleFavorite = {
                state.currentTrack?.id?.let { trackId ->
                    coroutineScope.launch { favoritesRepository.toggleFavorite(trackId) }
                }
            },
            progressContent = { trackDurationMs ->
                NowPlayingProgressRoot(
                    trackDurationMs = trackDurationMs,
                    playerViewModel = playerViewModel,
                    playerInteractionSettings = settings.playerInteraction,
                    onAction = ::onAction,
                )
            },
            compactProgressContent = { trackDurationMs ->
                NowPlayingProgressRoot(
                    trackDurationMs = trackDurationMs,
                    playerViewModel = playerViewModel,
                    playerInteractionSettings = settings.playerInteraction,
                    compact = true,
                    onAction = ::onAction,
                )
            },
            onAction = ::onAction,
            onStatusBarCoverageChanged = { playerCoversStatusBar = it },
        )
    }
}

internal fun shouldUseLightStatusBarIcons(
    playerCoversStatusBar: Boolean,
    appUsesDarkTheme: Boolean,
): Boolean = playerCoversStatusBar || appUsesDarkTheme

@Composable
private fun NowPlayingProgressRoot(
    trackDurationMs: Long?,
    playerViewModel: PlayerVM,
    playerInteractionSettings: PlayerInteractionSettings,
    compact: Boolean = false,
    onAction: (NowPlayingAction) -> Unit,
) {
    val currentDuration by playerViewModel.currentDuration.collectAsState()
    val bufferDuration by playerViewModel.bufferDuration.collectAsState()
    val playerDuration by playerViewModel.playerDuration.collectAsState()

    NowPlayingProgressPanel(
        progressState = NowPlayingProgressState(
            currentDuration = currentDuration,
            bufferDuration = bufferDuration,
            playerDuration = playerDuration,
        ),
        trackDurationMs = trackDurationMs,
        playerInteractionSettings = playerInteractionSettings,
        onAction = onAction,
        compact = compact,
        immersive = true,
    )
}
