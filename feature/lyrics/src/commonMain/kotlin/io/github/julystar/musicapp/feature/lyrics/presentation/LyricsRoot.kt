package io.github.julystar.musicapp.feature.lyrics.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import io.github.julystar.musicapp.core.domain.model.AppSettings
import io.github.julystar.musicapp.core.domain.repository.FavoritesRepository
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.core.presentation.platform.PlatformBackHandler
import io.github.julystar.musicapp.core.presentation.platform.StatusBarIconsEffect
import io.github.julystar.musicapp.service.playback.presentation.PlayerVM
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LyricsRoot(
    onNavigateBack: () -> Unit,
    drawBackground: Boolean = true,
    viewModel: LyricsViewModel = koinViewModel(),
    playerViewModel: PlayerVM = koinViewModel(),
    settingsRepository: SettingsRepository = koinInject(),
    favoritesRepository: FavoritesRepository = koinInject(),
) {
    PlatformBackHandler(onBack = onNavigateBack)
    StatusBarIconsEffect(useLightIcons = true)

    val state by viewModel.state.collectAsState()
    val nowPlayingState by playerViewModel.nowPlayingState.collectAsState()
    val currentDuration by playerViewModel.currentDuration.collectAsState()
    val settings by settingsRepository.settings.collectAsState(AppSettings.Default)
    val favoriteTrackIds by favoritesRepository.favoriteTrackIds.collectAsState(emptySet())
    val coroutineScope = rememberCoroutineScope()
    val nowPlayingTrack = nowPlayingState.currentTrack
        ?.takeIf { track -> track.id == state.trackId }

    LyricsScreen(
        state = state,
        nowPlayingTrack = nowPlayingTrack,
        currentPositionMs = currentDuration.inWholeMilliseconds,
        isPlaying = nowPlayingState.controls.isPlaying,
        lyricDisplaySettings = settings.lyrics,
        isFavorite = state.trackId?.let(favoriteTrackIds::contains) == true,
        onToggleFavorite = {
            state.trackId?.let { trackId ->
                coroutineScope.launch { favoritesRepository.toggleFavorite(trackId) }
            }
        },
        onAction = { action ->
            when (action) {
                LyricsAction.NavigateBack -> onNavigateBack()
                LyricsAction.Retry -> viewModel.onAction(action)
            }
        },
        onPlayerAction = playerViewModel::onNowPlayingAction,
        drawBackground = drawBackground,
    )
}

@Composable
fun NowPlayingLyricsRoot(
    onNavigateBack: () -> Unit,
    drawBackground: Boolean = true,
    playerViewModel: PlayerVM = koinViewModel(),
    settingsRepository: SettingsRepository = koinInject(),
    favoritesRepository: FavoritesRepository = koinInject(),
) {
    PlatformBackHandler(onBack = onNavigateBack)
    StatusBarIconsEffect(useLightIcons = true)

    val nowPlayingState by playerViewModel.nowPlayingState.collectAsState()
    val currentDuration by playerViewModel.currentDuration.collectAsState()
    val settings by settingsRepository.settings.collectAsState(AppSettings.Default)
    val favoriteTrackIds by favoritesRepository.favoriteTrackIds.collectAsState(emptySet())
    val coroutineScope = rememberCoroutineScope()
    val track = nowPlayingState.currentTrack
    val trackId = track?.id

    LyricsScreen(
        state = LyricsState(
            trackId = trackId,
            isLoading = false,
            trackTitle = track?.title.orEmpty(),
            trackArtist = track?.artist,
        ),
        nowPlayingTrack = track,
        currentPositionMs = currentDuration.inWholeMilliseconds,
        isPlaying = nowPlayingState.controls.isPlaying,
        lyricDisplaySettings = settings.lyrics,
        isFavorite = trackId?.let(favoriteTrackIds::contains) == true,
        onToggleFavorite = {
            trackId?.let { id ->
                coroutineScope.launch { favoritesRepository.toggleFavorite(id) }
            }
        },
        onAction = { action ->
            if (action == LyricsAction.NavigateBack) onNavigateBack()
        },
        onPlayerAction = playerViewModel::onNowPlayingAction,
        drawBackground = drawBackground,
    )
}
