package io.github.julystar.musicapp.service.playback.presentation.miniplayer

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.repository.FavoritesRepository
import io.github.julystar.musicapp.core.presentation.components.MusicCover
import io.github.julystar.musicapp.core.presentation.components.DesignCompactMiniPlayerBar
import io.github.julystar.musicapp.core.presentation.components.DesignExpandedMiniPlayerBar
import io.github.julystar.musicapp.core.presentation.components.DesignGradientPlayButton
import io.github.julystar.musicapp.core.presentation.components.DesignIconButton
import io.github.julystar.musicapp.core.presentation.components.DesignIconButtonColors
import io.github.julystar.musicapp.core.presentation.components.DesignIconButtonSize
import io.github.julystar.musicapp.core.presentation.components.DesignIconButtonVariant
import io.github.julystar.musicapp.core.presentation.components.DesignMiniPlayerBar
import io.github.julystar.musicapp.core.presentation.components.DesignPlayerControlSize
import io.github.julystar.musicapp.core.presentation.platform.isDesktopPlatform
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.julystar.musicapp.service.playback.domain.PlaybackStatus
import io.github.julystar.musicapp.service.playback.domain.RepeatMode
import io.github.julystar.musicapp.service.playback.presentation.PlayerVM
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import musicapp.service.playback.presentation.generated.resources.Res
import musicapp.service.playback.presentation.generated.resources.icon_heart_compact
import musicapp.service.playback.presentation.generated.resources.icon_heart_compact_filled
import musicapp.service.playback.presentation.generated.resources.icon_pause
import musicapp.service.playback.presentation.generated.resources.icon_play
import musicapp.service.playback.presentation.generated.resources.icon_play_next
import musicapp.service.playback.presentation.generated.resources.icon_play_previous
import musicapp.service.playback.presentation.generated.resources.icon_transport_queue
import musicapp.service.playback.presentation.generated.resources.icon_transport_repeat
import musicapp.service.playback.presentation.generated.resources.icon_transport_repeat_one
import musicapp.service.playback.presentation.generated.resources.icon_transport_shuffle
import musicapp.service.playback.presentation.generated.resources.player_add_favorite
import musicapp.service.playback.presentation.generated.resources.player_list_repeat
import musicapp.service.playback.presentation.generated.resources.player_next_track
import musicapp.service.playback.presentation.generated.resources.player_pause
import musicapp.service.playback.presentation.generated.resources.player_play
import musicapp.service.playback.presentation.generated.resources.player_previous_track
import musicapp.service.playback.presentation.generated.resources.player_queue
import musicapp.service.playback.presentation.generated.resources.player_remove_favorite
import musicapp.service.playback.presentation.generated.resources.player_repeat
import musicapp.service.playback.presentation.generated.resources.player_shuffle
import musicapp.service.playback.presentation.generated.resources.player_single_repeat
import musicapp.service.playback.presentation.generated.resources.player_unknown_artist
import musicapp.service.playback.presentation.generated.resources.now_playing_title
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
private fun MiniPlayerCore(
    isPlaying: Boolean,
    title: String,
    subtitle: String,
    cover: Artwork?,
    currentDurationMS: ULong,
    totalDurationMS: ULong,
    loading: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    repeatMode: RepeatMode,
    shuffleEnabled: Boolean,
    isFavorite: Boolean,
    playbackAvailable: Boolean = true,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val progress = playbackProgress(
        currentDurationMS = currentDurationMS,
        totalDurationMS = totalDurationMS,
    )

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (isDesktopPlatform() || maxWidth >= 840.dp) {
            ExpandedMiniPlayerBar(
                isPlaying = isPlaying,
                title = title,
                subtitle = subtitle,
                cover = cover,
                progress = progress,
                loading = loading,
                canPrevious = canPrevious,
                canNext = canNext,
                repeatMode = repeatMode,
                shuffleEnabled = shuffleEnabled,
                isFavorite = isFavorite,
                playbackAvailable = playbackAvailable,
                onClick = onClick,
                onPlay = onPlay,
                onPause = onPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onToggleFavorite = onToggleFavorite,
                onCyclePlaybackMode = onCyclePlaybackMode,
                onOpenQueue = onOpenQueue,
            )
        } else if (maxWidth < 140.dp) {
            CompactMiniPlayer(
                isPlaying = isPlaying,
                cover = cover,
                progress = progress,
                loading = loading,
                onClick = onClick,
                onPlay = onPlay,
                onPause = onPause,
            )
        } else {
            MiniPlayerBar(
                isPlaying = isPlaying,
                title = title,
                subtitle = subtitle,
                cover = cover,
                progress = progress,
                loading = loading,
                canPrevious = canPrevious,
                canNext = canNext,
                showMobilePortraitActions = maxWidth < 600.dp,
                isFavorite = isFavorite,
                playbackAvailable = playbackAvailable,
                onClick = onClick,
                onPlay = onPlay,
                onPause = onPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onToggleFavorite = onToggleFavorite,
                onOpenQueue = onOpenQueue,
            )
        }
    }
}

@Composable
private fun MiniPlayerBar(
    isPlaying: Boolean,
    title: String,
    subtitle: String,
    cover: Artwork?,
    progress: Float,
    loading: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    showMobilePortraitActions: Boolean,
    isFavorite: Boolean,
    playbackAvailable: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val shapes = DesignTokens.shapes
    val actionTint = MiuixTheme.colorScheme.onSurface

    DesignMiniPlayerBar(
        title = title,
        subtitle = subtitle,
        progress = progress,
        onClick = onClick,
        artwork = {
            MusicCover(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(shapes.sm)),
                artwork = cover,
            )
        },
        controls = {
            if (showMobilePortraitActions) {
                MiniPlayerIconButton(
                    painter = painterResource(
                        if (isFavorite) {
                            Res.drawable.icon_heart_compact_filled
                        } else {
                            Res.drawable.icon_heart_compact
                        },
                    ),
                    contentDescription = stringResource(
                        if (isFavorite) Res.string.player_remove_favorite else Res.string.player_add_favorite,
                    ),
                    tint = if (isFavorite) MiuixTheme.colorScheme.primary else actionTint,
                    enabled = playbackAvailable,
                    onClick = onToggleFavorite,
                )
                MiniPlayerIconButton(
                    painter = painterResource(if (isPlaying) Res.drawable.icon_pause else Res.drawable.icon_play),
                    contentDescription = stringResource(
                        if (isPlaying) Res.string.player_pause else Res.string.player_play,
                    ),
                    tint = actionTint,
                    enabled = !loading,
                    emphasized = true,
                    onClick = if (isPlaying) onPause else onPlay,
                )
                MiniPlayerIconButton(
                    painter = painterResource(Res.drawable.icon_transport_queue),
                    contentDescription = stringResource(Res.string.player_queue),
                    tint = actionTint,
                    enabled = playbackAvailable,
                    onClick = onOpenQueue,
                )
            } else {
                MiniPlayerIconButton(
                    painter = painterResource(if (isPlaying) Res.drawable.icon_pause else Res.drawable.icon_play),
                    contentDescription = stringResource(
                        if (isPlaying) Res.string.player_pause else Res.string.player_play
                    ),
                    tint = actionTint,
                    enabled = !loading,
                    emphasized = true,
                    onClick = if (isPlaying) onPause else onPlay,
                )
                MiniPlayerIconButton(
                    painter = painterResource(Res.drawable.icon_play_next),
                    contentDescription = stringResource(Res.string.player_next_track),
                    tint = actionTint,
                    enabled = canNext,
                    onClick = onNext,
                )
            }
        },
    )
}

@Composable
private fun ExpandedMiniPlayerBar(
    isPlaying: Boolean,
    title: String,
    subtitle: String,
    cover: Artwork?,
    progress: Float,
    loading: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    repeatMode: RepeatMode,
    shuffleEnabled: Boolean,
    isFavorite: Boolean,
    playbackAvailable: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val shapes = DesignTokens.shapes
    val actionTint = MiuixTheme.colorScheme.onSurface
    val playbackModePainter = painterResource(
        when {
            shuffleEnabled -> Res.drawable.icon_transport_shuffle
            repeatMode == RepeatMode.One -> Res.drawable.icon_transport_repeat_one
            else -> Res.drawable.icon_transport_repeat
        },
    )
    val playbackModeDescription = stringResource(
        when {
            shuffleEnabled -> Res.string.player_shuffle
            repeatMode == RepeatMode.One -> Res.string.player_single_repeat
            repeatMode == RepeatMode.All -> Res.string.player_list_repeat
            else -> Res.string.player_repeat
        },
    )

    DesignExpandedMiniPlayerBar(
        title = title,
        subtitle = subtitle,
        progress = progress,
        onClick = onClick,
        artwork = {
            MusicCover(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(shapes.sm)),
                artwork = cover,
            )
        },
        actions = {
            MiniPlayerIconButton(
                painter = painterResource(
                    if (isFavorite) {
                        Res.drawable.icon_heart_compact_filled
                    } else {
                        Res.drawable.icon_heart_compact
                    },
                ),
                contentDescription = stringResource(
                    if (isFavorite) Res.string.player_remove_favorite else Res.string.player_add_favorite,
                ),
                tint = if (isFavorite) MiuixTheme.colorScheme.primary else actionTint,
                enabled = playbackAvailable,
                onClick = onToggleFavorite,
            )
            MiniPlayerIconButton(
                painter = playbackModePainter,
                contentDescription = playbackModeDescription,
                tint = actionTint,
                enabled = playbackAvailable,
                onClick = onCyclePlaybackMode,
            )
            MiniPlayerIconButton(
                painter = painterResource(Res.drawable.icon_play_previous),
                contentDescription = stringResource(Res.string.player_previous_track),
                tint = actionTint,
                enabled = canPrevious,
                onClick = onPrevious,
            )
            MiniPlayerIconButton(
                painter = painterResource(if (isPlaying) Res.drawable.icon_pause else Res.drawable.icon_play),
                contentDescription = stringResource(
                    if (isPlaying) Res.string.player_pause else Res.string.player_play,
                ),
                tint = actionTint,
                enabled = !loading,
                emphasized = true,
                onClick = if (isPlaying) onPause else onPlay,
            )
            MiniPlayerIconButton(
                painter = painterResource(Res.drawable.icon_play_next),
                contentDescription = stringResource(Res.string.player_next_track),
                tint = actionTint,
                enabled = canNext,
                onClick = onNext,
            )
            MiniPlayerIconButton(
                painter = painterResource(Res.drawable.icon_transport_queue),
                contentDescription = stringResource(Res.string.player_queue),
                tint = actionTint,
                enabled = playbackAvailable,
                onClick = onOpenQueue,
            )
        },
    )
}

@Composable
private fun MiniPlayerIconButton(
    painter: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    emphasized: Boolean = false,
) {
    DesignIconButton(
        modifier = Modifier.width(if (emphasized) 48.dp else 44.dp),
        size = if (emphasized) DesignIconButtonSize.Touch else DesignIconButtonSize.Medium,
        variant = DesignIconButtonVariant.Default,
        painter = painter,
        onClick = onClick,
        contentDescription = contentDescription,
        colors = DesignIconButtonColors(iconTint = tint),
        enabled = enabled,
        showClickIndication = false,
    )
}

@Composable
private fun CompactMiniPlayer(
    isPlaying: Boolean,
    cover: Artwork?,
    progress: Float,
    loading: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
) {
    val shapes = DesignTokens.shapes

    DesignCompactMiniPlayerBar(
        progress = progress,
        accessibilityLabel = stringResource(Res.string.now_playing_title),
        onClick = onClick,
        artwork = {
            MusicCover(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(shapes.md)),
                artwork = cover,
            )
        },
        overlayControls = {
            DesignGradientPlayButton(
                painter = painterResource(if (isPlaying) Res.drawable.icon_pause else Res.drawable.icon_play),
                enabled = !loading,
                size = DesignPlayerControlSize.Mini,
                contentDescription = stringResource(
                    if (isPlaying) Res.string.player_pause else Res.string.player_play
                ),
                showClickIndication = false,
                onClick = if (isPlaying) onPause else onPlay,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 4.dp),
            )
        },
    )
}

private fun playbackProgress(
    currentDurationMS: ULong,
    totalDurationMS: ULong,
): Float {
    if (totalDurationMS == 0uL) return 0f
    return (currentDurationMS.toFloat() / totalDurationMS.toFloat()).coerceIn(0f, 1f)
}

@Composable
fun MiniPlayer(
    onOpenNowPlaying: () -> Unit,
    onOpenQueue: () -> Unit,
    playerVM: PlayerVM = koinViewModel(),
    favoritesRepository: FavoritesRepository = koinInject(),
) {
    val playbackState by playerVM.playbackState.collectAsState()
    val playbackPosition by playerVM.playbackPosition.collectAsState()
    val nowPlayingState by playerVM.nowPlayingState.collectAsState()
    val currentTrack = nowPlayingState.currentTrack
    val favoriteTrackIds by favoritesRepository.favoriteTrackIds.collectAsState(emptySet())
    val coroutineScope = rememberCoroutineScope()
    val isFavorite = currentTrack?.id?.let(favoriteTrackIds::contains) == true
    val durationMs = playbackPosition.durationMs.takeIf { it > 0 }
        ?: currentTrack?.durationMs
        ?: 0

    MiniPlayerCore(
        isPlaying = playbackState.status == PlaybackStatus.Playing,
        title = currentTrack?.title ?: playbackState.currentItem?.title ?: "",
        subtitle = currentTrack?.artist
            ?.takeIf { it.isNotBlank() }
            ?: playbackState.currentItem?.artist?.takeIf { it.isNotBlank() }
            ?: stringResource(Res.string.player_unknown_artist),
        cover = currentTrack?.artwork,
        currentDurationMS = playbackPosition.positionMs.coerceAtLeast(0).toULong(),
        totalDurationMS = durationMs.coerceAtLeast(0).toULong(),
        canPrevious = nowPlayingState.queue.canPlayPrevious,
        canNext = nowPlayingState.queue.canPlayNext,
        repeatMode = nowPlayingState.controls.repeatMode,
        shuffleEnabled = nowPlayingState.controls.shuffleEnabled,
        isFavorite = isFavorite,
        loading = playbackState.status == PlaybackStatus.Loading,
        onClick = onOpenNowPlaying,
        onPlay = { playerVM.resume() },
        onPause = { playerVM.pause() },
        onPrevious = { playerVM.playPrevious() },
        onNext = { playerVM.playNext() },
        onToggleFavorite = {
            currentTrack?.id?.let { trackId ->
                coroutineScope.launch { favoritesRepository.toggleFavorite(trackId) }
            }
        },
        onCyclePlaybackMode = { playerVM.changePlayModeToNext() },
        onOpenQueue = onOpenQueue,
    )
}
