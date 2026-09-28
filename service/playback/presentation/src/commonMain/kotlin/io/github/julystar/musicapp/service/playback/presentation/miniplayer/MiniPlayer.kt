package io.github.julystar.musicapp.service.playback.presentation.miniplayer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.julystar.musicapp.service.playback.presentation.nowplaying.AppleMusicDesktopTransportButton
import io.github.julystar.musicapp.service.playback.presentation.nowplaying.AppleMusicDesktopTransportMotion
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.model.PlaybackAudioInfo
import io.github.julystar.musicapp.core.domain.repository.FavoritesRepository
import io.github.julystar.musicapp.core.presentation.components.MusicCover
import io.github.julystar.musicapp.core.presentation.components.CompactMiniPlayerBar
import io.github.julystar.musicapp.core.presentation.components.ExpandedMiniPlayerBar
import io.github.julystar.musicapp.core.presentation.components.MiniPlayerBar
import io.github.julystar.musicapp.core.presentation.components.PlaybackSlider
import io.github.julystar.musicapp.core.presentation.components.PlaybackControlButton
import io.github.julystar.musicapp.core.presentation.components.PlaybackControlSize
import io.github.julystar.musicapp.core.presentation.components.PlaybackControlVariant
import io.github.julystar.musicapp.core.presentation.components.liquidGlassSurface
import io.github.julystar.musicapp.core.presentation.platform.isDesktopPlatform
import io.github.julystar.musicapp.core.presentation.theme.DesignPalette
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.julystar.musicapp.core.presentation.theme.LocalDesignIsDarkTheme
import io.github.julystar.musicapp.service.playback.domain.PlaybackStatus
import io.github.julystar.musicapp.service.playback.domain.RepeatMode
import io.github.julystar.musicapp.service.playback.presentation.PlayerVM
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import musicapp.service.playback.presentation.generated.resources.Res
import musicapp.service.playback.presentation.generated.resources.icon_heart_compact
import musicapp.service.playback.presentation.generated.resources.icon_heart_compact_filled
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_artwork_overlay
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_artwork_overlay_icon
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_dolby
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_favorite
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_favorite_outline
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_lyrics
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_more
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_next
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_pause
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_play
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_previous
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_queue
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_repeat
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_repeat_one
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_shuffle
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_lossless
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_volume_high
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_volume_low
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_volume_medium
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_volume_mute
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
import musicapp.service.playback.presentation.generated.resources.player_lyrics
import musicapp.service.playback.presentation.generated.resources.player_lossless_audio
import musicapp.service.playback.presentation.generated.resources.player_dolby_audio
import musicapp.service.playback.presentation.generated.resources.player_more_options
import musicapp.service.playback.presentation.generated.resources.player_mute
import musicapp.service.playback.presentation.generated.resources.player_next_track
import musicapp.service.playback.presentation.generated.resources.player_pause
import musicapp.service.playback.presentation.generated.resources.player_play
import musicapp.service.playback.presentation.generated.resources.player_previous_track
import musicapp.service.playback.presentation.generated.resources.player_queue
import musicapp.service.playback.presentation.generated.resources.player_remove_favorite
import musicapp.service.playback.presentation.generated.resources.player_repeat
import musicapp.service.playback.presentation.generated.resources.player_shuffle
import musicapp.service.playback.presentation.generated.resources.player_shuffle_on
import musicapp.service.playback.presentation.generated.resources.player_shuffle_off
import musicapp.service.playback.presentation.generated.resources.player_repeat_off
import musicapp.service.playback.presentation.generated.resources.player_single_repeat
import musicapp.service.playback.presentation.generated.resources.player_unknown_artist
import musicapp.service.playback.presentation.generated.resources.player_unmute
import musicapp.service.playback.presentation.generated.resources.player_volume
import musicapp.service.playback.presentation.generated.resources.now_playing_title
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup

@Composable
private fun MiniPlayerCore(
    isPlaying: Boolean,
    title: String,
    subtitle: String,
    cover: Artwork?,
    audioBadge: DesktopAudioBadge?,
    currentDurationMS: ULong,
    totalDurationMS: ULong,
    loading: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    repeatMode: RepeatMode,
    shuffleEnabled: Boolean,
    isFavorite: Boolean,
    lyricsSelected: Boolean,
    queueSelected: Boolean,
    volume: Float,
    playbackAvailable: Boolean = true,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onSeek: (ULong) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val progress = playbackProgress(
        currentDurationMS = currentDurationMS,
        totalDurationMS = totalDurationMS,
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        if (isDesktopPlatform()) {
            DesktopMiniPlayerBar(
                isPlaying = isPlaying,
                title = title,
                subtitle = subtitle,
                cover = cover,
                audioBadge = audioBadge,
                currentDurationMS = currentDurationMS,
                totalDurationMS = totalDurationMS,
                progress = progress,
                loading = loading,
                canPrevious = canPrevious,
                canNext = canNext,
                repeatMode = repeatMode,
                shuffleEnabled = shuffleEnabled,
                isFavorite = isFavorite,
                lyricsSelected = lyricsSelected,
                queueSelected = queueSelected,
                volume = volume,
                playbackAvailable = playbackAvailable,
                onClick = onClick,
                onPlay = onPlay,
                onPause = onPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onToggleFavorite = onToggleFavorite,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeatMode = onCycleRepeatMode,
                onSeek = onSeek,
                onVolumeChange = onVolumeChange,
                onOpenLyrics = onOpenLyrics,
                onOpenQueue = onOpenQueue,
            )
        } else if (maxWidth >= 840.dp) {
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

    MiniPlayerBar(
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
                    tint = if (isFavorite) DesignPalette.FavoriteRed else actionTint,
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

private val DesktopPlayerWidth = 700.dp
private val DesktopPlayerHeight = 54.dp
private val DesktopPlayerShape = RoundedCornerShape(27.dp)
private val DesktopArtworkLeadingInset = 2.dp
private const val DesktopProgressHoverDelayMillis = 100L
private const val DesktopTransportPressInDurationMillis = 70
private const val DesktopTransportReleaseDurationMillis = 160

internal enum class DesktopVolumeIconLevel {
    Mute,
    Low,
    Medium,
    High,
}

internal fun desktopVolumeIconLevel(volume: Float): DesktopVolumeIconLevel = when {
    volume <= 0.001f -> DesktopVolumeIconLevel.Mute
    volume <= 0.33f -> DesktopVolumeIconLevel.Low
    volume <= 0.66f -> DesktopVolumeIconLevel.Medium
    else -> DesktopVolumeIconLevel.High
}

internal enum class DesktopAudioBadge {
    Lossless,
    Dolby,
}

internal fun PlaybackAudioInfo?.desktopAudioBadge(): DesktopAudioBadge? {
    val audioInfo = this?.preferred ?: return null
    val descriptors = listOfNotNull(
        audioInfo.codec,
        audioInfo.container,
        audioInfo.channelLayout?.value,
    ).joinToString(" ").lowercase()
    val isDolby = listOf("dolby", "atmos", "eac3", "ec-3", "ac3", "ac-3", "truehd", "joc")
        .any(descriptors::contains)
    val isLosslessCodec = listOf("flac", "alac", "wav", "wave", "aiff", "ape", "wavpack")
        .any(descriptors::contains)

    return when {
        isDolby -> DesktopAudioBadge.Dolby
        audioInfo.lossless == true || isLosslessCodec -> DesktopAudioBadge.Lossless
        else -> null
    }
}

@Composable
private fun DesktopMiniPlayerBar(
    isPlaying: Boolean,
    title: String,
    subtitle: String,
    cover: Artwork?,
    audioBadge: DesktopAudioBadge?,
    currentDurationMS: ULong,
    totalDurationMS: ULong,
    progress: Float,
    loading: Boolean,
    canPrevious: Boolean,
    canNext: Boolean,
    repeatMode: RepeatMode,
    shuffleEnabled: Boolean,
    isFavorite: Boolean,
    lyricsSelected: Boolean,
    queueSelected: Boolean,
    volume: Float,
    playbackAvailable: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onSeek: (ULong) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val isDark = LocalDesignIsDarkTheme.current
    val foreground = if (isDark) Color.White.copy(alpha = 0.95f) else Color.Black
    val transportForeground = if (isDark) foreground else Color(0xFF242424)
    val secondaryForeground = if (isDark) Color.White.copy(alpha = 0.68f) else Color.Black
    var progressDragging by remember { mutableStateOf(false) }
    val progressInteractionSource = remember { MutableInteractionSource() }
    val progressHovered by progressInteractionSource.collectIsHoveredAsState()
    var artworkHovered by remember { mutableStateOf(false) }
    var progressHoverArmed by remember { mutableStateOf(false) }
    val progressExpanded = progressDragging || progressHoverArmed
    val trackHeight by animateDpAsState(
        targetValue = if (progressExpanded) 8.dp else 2.dp,
        animationSpec = tween(120),
        label = "desktop-player-progress-height",
    )
    var volumeExpanded by remember { mutableStateOf(false) }
    val initialVolume = remember { volume.coerceIn(0f, 1f) }
    var volumeLevel by remember { mutableStateOf(initialVolume) }
    var volumeBeforeMute by remember { mutableStateOf(initialVolume.coerceAtLeast(0.2f)) }
    val volumeControlWidth by animateDpAsState(
        targetValue = if (volumeExpanded) 112.dp else 36.dp,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "desktop-player-volume-width",
    )
    val volumeReveal by animateFloatAsState(
        targetValue = if (volumeExpanded) 1f else 0f,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "desktop-player-volume-reveal",
    )
    val volumeSurfaceColor by animateColorAsState(
        targetValue = when {
            !volumeExpanded -> Color.Transparent
            isDark -> Color(0xFF2C2C2E)
            else -> Color(0xFFF0F0F0)
        },
        animationSpec = tween(durationMillis = 140),
        label = "desktop-player-volume-surface",
    )
    val volumeInteractionSource = remember { MutableInteractionSource() }
    val volumeHovered by volumeInteractionSource.collectIsHoveredAsState()
    var volumeWasHovered by remember { mutableStateOf(false) }
    var moreMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(progressHovered, artworkHovered) {
        if (progressHovered && !artworkHovered) {
            delay(DesktopProgressHoverDelayMillis)
            progressHoverArmed = true
        } else {
            progressHoverArmed = false
        }
    }

    LaunchedEffect(volumeHovered) {
        if (volumeWasHovered && !volumeHovered && volumeExpanded) volumeExpanded = false
        volumeWasHovered = volumeHovered
    }

    Box(
        modifier = Modifier
            .width(DesktopPlayerWidth)
            .height(DesktopPlayerHeight)
            .dropShadow(
                shape = DesktopPlayerShape,
                shadow = Shadow(
                    radius = 14.dp,
                    offset = DpOffset(0.dp, 7.dp),
                    color = Color.Black.copy(alpha = 0.10f),
                ),
            )
            .clip(DesktopPlayerShape)
            .liquidGlassSurface(
                shape = DesktopPlayerShape,
                surfaceColor = if (isDark) Color.Black else Color.White,
                surfaceAlphaScale = if (isDark) 1f else 0.50f,
                vibrant = true,
                showHighlight = true,
            ),
    ) {
        Box(
            Modifier.fillMaxSize().background(
                if (isDark) Color(0xFF5D5B59).copy(alpha = 0.51f) else Color.White.copy(alpha = 0.50f),
            ),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.width(148.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DesktopPlayerIconButton(
                    painter = painterResource(Res.drawable.icon_apple_music_shuffle),
                    contentDescription = stringResource(
                        if (shuffleEnabled) Res.string.player_shuffle_off else Res.string.player_shuffle_on,
                    ),
                    tint = if (shuffleEnabled) transportForeground else transportForeground.copy(alpha = 0.315f),
                    selected = shuffleEnabled,
                    enabled = playbackAvailable,
                    buttonSize = 28.dp,
                    iconSize = 27.5.dp,
                    iconHeight = 23.dp,
                    iconOffsetX = (-0.75).dp,
                    iconOffsetY = 1.25.dp,
                    iconScaleY = 1.1125f,
                    onClick = onToggleShuffle,
                )
                AppleMusicDesktopTransportButton(
                    painter = Res.drawable.icon_apple_music_previous,
                    motion = AppleMusicDesktopTransportMotion.Seek,
                    seekDirection = -1,
                    contentDescription = stringResource(Res.string.player_previous_track),
                    tint = transportForeground,
                    enabled = canPrevious,
                    buttonWidth = 28.dp,
                    modifier = Modifier.height(28.dp),
                    iconSize = 31.75.dp,
                    iconHeight = 30.dp,
                    iconOffsetX = 0.75.dp,
                    iconOffsetY = 0.75.dp,
                    iconScaleY = 0.92f,
                    onClick = onPrevious,
                )
                AppleMusicDesktopTransportButton(
                    painter = if (isPlaying) Res.drawable.icon_apple_music_pause else Res.drawable.icon_apple_music_play,
                    motion = AppleMusicDesktopTransportMotion.PlayPause,
                    contentDescription = stringResource(
                        if (isPlaying) Res.string.player_pause else Res.string.player_play,
                    ),
                    tint = transportForeground,
                    enabled = !loading,
                    buttonWidth = 36.dp,
                    modifier = Modifier.height(36.dp),
                    iconSize = 33.5.dp,
                    iconHeight = 34.5.dp,
                    iconOffsetX = (-1.5).dp,
                    iconOffsetY = 0.75.dp,
                    iconScaleY = 1.0125f,
                    onClick = if (isPlaying) onPause else onPlay,
                )
                AppleMusicDesktopTransportButton(
                    painter = Res.drawable.icon_apple_music_next,
                    motion = AppleMusicDesktopTransportMotion.Seek,
                    seekDirection = 1,
                    contentDescription = stringResource(Res.string.player_next_track),
                    tint = transportForeground,
                    enabled = canNext,
                    buttonWidth = 28.dp,
                    modifier = Modifier.height(28.dp),
                    iconSize = 31.75.dp,
                    iconHeight = 30.dp,
                    iconOffsetX = (-0.75).dp,
                    iconOffsetY = 0.75.dp,
                    iconScaleY = 0.92f,
                    onClick = onNext,
                )
                DesktopPlayerIconButton(
                    painter = painterResource(
                        if (repeatMode == RepeatMode.One) {
                            Res.drawable.icon_apple_music_repeat_one
                        } else {
                            Res.drawable.icon_apple_music_repeat
                        },
                    ),
                    contentDescription = stringResource(
                        when (repeatMode) {
                            RepeatMode.Off -> Res.string.player_repeat_off
                            RepeatMode.All -> Res.string.player_list_repeat
                            RepeatMode.One -> Res.string.player_single_repeat
                        },
                    ),
                    tint = if (repeatMode == RepeatMode.Off) {
                        transportForeground.copy(alpha = 0.315f)
                    } else {
                        transportForeground
                    },
                    selected = repeatMode != RepeatMode.Off,
                    enabled = playbackAvailable,
                    buttonSize = 28.dp,
                    iconSize = 26.dp,
                    iconHeight = 23.dp,
                    iconOffsetY = 1.25.dp,
                    iconScaleY = 1.10f,
                    onClick = onCycleRepeatMode,
                )
            }

            Spacer(Modifier.width(9.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                DesktopPlayerMetadata(
                    title = title,
                    subtitle = subtitle,
                    cover = cover,
                    audioBadge = audioBadge,
                    isFavorite = isFavorite,
                    foreground = foreground,
                    secondaryForeground = secondaryForeground,
                    progressExpanded = progressExpanded,
                    artworkHovered = artworkHovered,
                    moreMenuExpanded = moreMenuExpanded,
                    onArtworkHoverChange = { artworkHovered = it },
                    onToggleFavorite = onToggleFavorite,
                    onMoreClick = { moreMenuExpanded = true },
                    onClick = onClick,
                )
                DesktopPlayerProgress(
                    progress = progress,
                    currentDurationMS = currentDurationMS,
                    totalDurationMS = totalDurationMS,
                    expanded = progressExpanded,
                    dragging = progressDragging,
                    trackHeight = trackHeight,
                    foreground = foreground,
                    onDragStateChange = { progressDragging = it },
                    onSeek = onSeek,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .hoverable(progressInteractionSource),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = 31.dp),
                ) {
                    WindowListPopup(
                        show = moreMenuExpanded,
                        alignment = PopupPositionProvider.Align.End,
                        enableWindowDim = false,
                        onDismissRequest = { moreMenuExpanded = false },
                        onDismissFinished = {},
                        maxHeight = null,
                    ) {
                        ListPopupColumn {
                            DesktopPlayerMenuItem(
                                text = stringResource(Res.string.player_lyrics),
                                onClick = {
                                    moreMenuExpanded = false
                                    onOpenLyrics()
                                },
                            )
                            DesktopPlayerMenuItem(
                                text = stringResource(Res.string.player_queue),
                                onClick = {
                                    moreMenuExpanded = false
                                    onOpenQueue()
                                },
                            )
                            DesktopPlayerMenuItem(
                                text = stringResource(
                                    if (isFavorite) {
                                        Res.string.player_remove_favorite
                                    } else {
                                        Res.string.player_add_favorite
                                    },
                                ),
                                onClick = {
                                    moreMenuExpanded = false
                                    onToggleFavorite()
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(9.dp))
            Box(
                modifier = Modifier
                    .width(112.dp)
                    .height(42.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (!volumeExpanded || volumeReveal < 0.99f) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .graphicsLayer {
                                alpha = 1f - volumeReveal
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        DesktopPlayerIconButton(
                            painter = painterResource(Res.drawable.icon_apple_music_lyrics),
                            contentDescription = stringResource(Res.string.player_lyrics),
                            tint = foreground,
                            selected = lyricsSelected,
                            enabled = playbackAvailable && !volumeExpanded,
                            buttonSize = 36.dp,
                            iconSize = 36.dp,
                            appleMusicActionStyle = true,
                            onClick = onOpenLyrics,
                        )
                        DesktopPlayerIconButton(
                            painter = painterResource(Res.drawable.icon_apple_music_queue),
                            contentDescription = stringResource(Res.string.player_queue),
                            tint = foreground,
                            selected = queueSelected,
                            enabled = playbackAvailable && !volumeExpanded,
                            buttonSize = 36.dp,
                            iconSize = 36.dp,
                            appleMusicActionStyle = true,
                            onClick = onOpenQueue,
                        )
                    }
                }

                val volumeShape = RoundedCornerShape(20.dp)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .offset(x = 2.5.dp, y = (-0.5).dp)
                        .width(volumeControlWidth)
                        .height(40.dp)
                        .dropShadow(
                            shape = volumeShape,
                            shadow = Shadow(
                                radius = 9.dp,
                                offset = DpOffset(0.dp, 1.dp),
                                color = Color.Black.copy(alpha = 0.035f * volumeReveal),
                            ),
                        )
                        .clip(volumeShape)
                        .background(volumeSurfaceColor)
                        .hoverable(volumeInteractionSource),
                ) {
                    if (volumeExpanded) {
                        PlaybackSlider(
                            value = volumeLevel,
                            onValueChange = { value ->
                                volumeLevel = value
                                if (value > 0.001f) volumeBeforeMute = value
                                onVolumeChange(value)
                            },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = 16.dp)
                                .width(54.dp)
                                .graphicsLayer {
                                    alpha = volumeReveal
                                    scaleX = 0.88f + (0.12f * volumeReveal)
                                    transformOrigin = TransformOrigin(1f, 0.5f)
                                },
                            height = 16.dp,
                            trackHeight = 8.dp,
                            thumbSize = 0.dp,
                            activeThumbSize = 0.dp,
                            trackColorOverride = if (isDark) {
                                Color.White.copy(alpha = 0.24f)
                            } else {
                                Color(0xFFB8B8B8)
                            },
                            activeTrackColorOverride = foreground,
                            thumbColorOverride = Color.Transparent,
                        )
                    }
                    DesktopPlayerIconButton(
                        painter = painterResource(
                            when (desktopVolumeIconLevel(volumeLevel)) {
                                DesktopVolumeIconLevel.Mute -> Res.drawable.icon_apple_music_volume_mute
                                DesktopVolumeIconLevel.Low -> Res.drawable.icon_apple_music_volume_low
                                DesktopVolumeIconLevel.Medium -> Res.drawable.icon_apple_music_volume_medium
                                DesktopVolumeIconLevel.High -> Res.drawable.icon_apple_music_volume_high
                            },
                        ),
                        contentDescription = stringResource(
                            when {
                                !volumeExpanded -> Res.string.player_volume
                                volumeLevel <= 0.001f -> Res.string.player_unmute
                                else -> Res.string.player_mute
                            },
                        ),
                        tint = foreground,
                        buttonSize = 18.dp,
                        iconSize = 24.dp,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .offset(x = (-11.5).dp),
                        onClick = {
                            if (!volumeExpanded) {
                                volumeExpanded = true
                            } else {
                                val target = if (volumeLevel <= 0.001f) {
                                    volumeBeforeMute.coerceAtLeast(0.2f)
                                } else {
                                    volumeBeforeMute = volumeLevel
                                    0f
                                }
                                volumeLevel = target
                                onVolumeChange(target)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopPlayerMenuItem(
    text: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val overlay = if (LocalDesignIsDarkTheme.current) Color.White else Color.Black
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (hovered) overlay.copy(alpha = 0.06f) else Color.Transparent)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun DesktopPlayerMetadata(
    title: String,
    subtitle: String,
    cover: Artwork?,
    audioBadge: DesktopAudioBadge?,
    isFavorite: Boolean,
    foreground: Color,
    secondaryForeground: Color,
    progressExpanded: Boolean,
    artworkHovered: Boolean,
    moreMenuExpanded: Boolean,
    onArtworkHoverChange: (Boolean) -> Unit,
    onToggleFavorite: () -> Unit,
    onMoreClick: () -> Unit,
    onClick: () -> Unit,
) {
    val artworkInteractionSource = remember { MutableInteractionSource() }
    val metadataInteractionSource = remember { MutableInteractionSource() }
    val artworkIsHovered by artworkInteractionSource.collectIsHoveredAsState()
    val artworkHoverProgress by animateFloatAsState(
        targetValue = if (artworkHovered) 1f else 0f,
        animationSpec = tween(if (artworkHovered) 150 else 180),
        label = "desktop-player-artwork-hover",
    )
    val metadataBlur by animateDpAsState(
        targetValue = if (progressExpanded) 7.dp else 0.dp,
        animationSpec = tween(120),
        label = "desktop-player-metadata-blur",
    )
    val metadataAlpha by animateFloatAsState(
        targetValue = if (progressExpanded) 0.30f else 1f,
        animationSpec = tween(120),
        label = "desktop-player-metadata-alpha",
    )

    LaunchedEffect(artworkIsHovered) {
        onArtworkHoverChange(artworkIsHovered)
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .blur(metadataBlur)
            .graphicsLayer { alpha = metadataAlpha },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .offset(y = 1.dp)
                .clickable(
                    enabled = !progressExpanded,
                    interactionSource = metadataInteractionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(45.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = DesktopArtworkLeadingInset)
                        .size(34.dp)
                        .scale(1f + 0.06f * artworkHoverProgress)
                        .then(
                            if (artworkHovered) {
                                Modifier.dropShadow(
                                    shape = RoundedCornerShape(6.dp),
                                    shadow = Shadow(
                                        radius = 8.dp,
                                        offset = DpOffset(0.dp, 4.dp),
                                        color = Color.Black.copy(alpha = 0.26f),
                                    ),
                                )
                            } else {
                                Modifier
                            },
                        )
                        .clip(RoundedCornerShape(6.dp))
                        .hoverable(artworkInteractionSource)
                        .zIndex(2f),
                    contentAlignment = Alignment.Center,
                ) {
                    MusicCover(
                        modifier = Modifier.fillMaxSize(),
                        artwork = cover,
                    )
                    Image(
                        painter = painterResource(Res.drawable.icon_apple_music_artwork_overlay),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = if (progressExpanded) 0f else artworkHoverProgress
                            },
                    )
                    Icon(
                        painter = painterResource(Res.drawable.icon_apple_music_artwork_overlay_icon),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .requiredSize(56.dp)
                            .graphicsLayer {
                                alpha = if (progressExpanded) 0f else artworkHoverProgress
                                rotationZ = 90f
                                scaleX = 0.8f + 0.2f * artworkHoverProgress
                                scaleY = scaleX
                            },
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = title.ifBlank { "Tide Player" },
                    color = foreground,
                    fontSize = 12.5.sp,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .appleMusicMarqueeFade()
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            repeatDelayMillis = 1_800,
                            initialDelayMillis = 1_800,
                            spacing = MarqueeSpacing(24.dp),
                            velocity = 30.dp,
                        ),
                )
                Text(
                    text = subtitle,
                    color = secondaryForeground,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .appleMusicMarqueeFade()
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            repeatDelayMillis = 1_800,
                            initialDelayMillis = 1_800,
                            spacing = MarqueeSpacing(24.dp),
                            velocity = 30.dp,
                        ),
                )
            }
        }
        DesktopPlayerIconButton(
            painter = painterResource(
                if (isFavorite) {
                    Res.drawable.icon_apple_music_favorite
                } else {
                    Res.drawable.icon_apple_music_favorite_outline
                },
            ),
            contentDescription = stringResource(
                if (isFavorite) Res.string.player_remove_favorite else Res.string.player_add_favorite,
            ),
            tint = if (isFavorite) DesignPalette.FavoriteRed else foreground.copy(alpha = 0.30f),
            enabled = !progressExpanded,
            buttonSize = 16.dp,
            iconSize = 12.dp,
            showsInteractionBackground = false,
            pressedIconAlpha = 0.58f,
            onClick = onToggleFavorite,
        )
        audioBadge?.let { badge ->
            Spacer(Modifier.width(8.dp))
            DesktopPlayerAudioBadge(
                badge = badge,
                foreground = foreground,
            )
        }
        DesktopPlayerIconButton(
            painter = painterResource(Res.drawable.icon_apple_music_more),
            contentDescription = stringResource(Res.string.player_more_options),
            tint = foreground,
            enabled = !progressExpanded,
            buttonSize = 36.dp,
            iconSize = 36.dp,
            appleMusicActionStyle = true,
            onClick = onMoreClick,
        )
    }
}

private fun Modifier.appleMusicMarqueeFade(fadeWidth: Dp = 22.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startX = (size.width - fadeWidth.toPx()).coerceAtLeast(0f),
                endX = size.width,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
private fun DesktopPlayerAudioBadge(
    badge: DesktopAudioBadge,
    foreground: Color,
) {
    val painter = painterResource(
        when (badge) {
            DesktopAudioBadge.Lossless -> Res.drawable.icon_apple_music_lossless
            DesktopAudioBadge.Dolby -> Res.drawable.icon_apple_music_dolby
        },
    )
    val description = stringResource(
        when (badge) {
            DesktopAudioBadge.Lossless -> Res.string.player_lossless_audio
            DesktopAudioBadge.Dolby -> Res.string.player_dolby_audio
        },
    )
    Box(
        modifier = Modifier
            .width(34.dp)
            .height(26.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = foreground.copy(alpha = 0.30f),
            modifier = when (badge) {
                DesktopAudioBadge.Lossless -> Modifier.width(25.dp).height(18.dp)
                DesktopAudioBadge.Dolby -> Modifier.width(15.dp).height(14.dp)
            },
        )
    }
}

@Composable
private fun DesktopPlayerProgress(
    progress: Float,
    currentDurationMS: ULong,
    totalDurationMS: ULong,
    expanded: Boolean,
    dragging: Boolean,
    trackHeight: androidx.compose.ui.unit.Dp,
    foreground: Color,
    onDragStateChange: (Boolean) -> Unit,
    onSeek: (ULong) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = DesktopArtworkLeadingInset, bottom = 1.dp)
            .graphicsLayer {
                scaleX = if (dragging) 1.022f else 1f
                scaleY = if (dragging) 1.06f else 1f
                transformOrigin = if (dragging) {
                    TransformOrigin(0f, 1f)
                } else {
                    TransformOrigin(0.5f, 1f)
                }
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(15.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatPlayerTime(currentDurationMS),
                    color = foreground,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Default,
                )
                Text(
                    text = "−${formatPlayerTime(totalDurationMS.saturatingMinus(currentDurationMS))}",
                    color = foreground,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Default,
                )
            }
        }
        PlaybackSlider(
            value = progress,
            onValueChange = { fraction ->
                onSeek((totalDurationMS.toDouble() * fraction.toDouble()).toULong())
            },
            enabled = totalDurationMS > 0uL,
            modifier = Modifier
                .offset(y = if (expanded) (-4).dp else 3.dp)
                .graphicsLayer {
                    scaleY = if (dragging) 1.14f else 1f
                },
            height = 14.dp,
            trackHeight = trackHeight,
            thumbSize = 0.dp,
            activeThumbSize = 0.dp,
            trackColorOverride = foreground.copy(alpha = if (expanded) 0.20f else 0.24f),
            activeTrackColorOverride = foreground.copy(alpha = if (expanded) 1f else 0.70f),
            thumbColorOverride = Color.Transparent,
            onValueChangeStarted = { onDragStateChange(true) },
            onValueChangeFinished = { onDragStateChange(false) },
        )
    }
}

@Composable
private fun DesktopPlayerIconButton(
    painter: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String,
    tint: Color,
    buttonSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    iconHeight: androidx.compose.ui.unit.Dp = iconSize,
    iconOffsetX: androidx.compose.ui.unit.Dp = 0.dp,
    iconOffsetY: androidx.compose.ui.unit.Dp = 0.dp,
    iconScaleY: Float = 1f,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
    showsInteractionBackground: Boolean = true,
    pressedIconAlpha: Float = 1f,
    appleMusicActionStyle: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    var actionReleaseFlash by remember { mutableStateOf(false) }
    val isDark = LocalDesignIsDarkTheme.current
    LaunchedEffect(actionReleaseFlash) {
        if (actionReleaseFlash) {
            delay(100)
            actionReleaseFlash = false
        }
    }
    val actionFeedbackVisible = appleMusicActionStyle && (pressed || actionReleaseFlash)
    val standardOverlayColor by animateColorAsState(
        targetValue = when {
            pressed && showsInteractionBackground -> tint.copy(alpha = 0.12f)
            selected -> tint.copy(alpha = 0.09f)
            hovered && showsInteractionBackground -> tint.copy(alpha = 0.06f)
            else -> Color.Transparent
        },
        animationSpec = tween(90),
        label = "desktop-player-control-hover",
    )
    val overlayColor = if (appleMusicActionStyle) {
        if (actionFeedbackVisible) {
            Color.Black.copy(alpha = if (isDark) 0.52f else 0.12f)
        } else {
            Color.Transparent
        }
    } else {
        standardOverlayColor
    }
    val iconTint = if (appleMusicActionStyle && selected && !actionFeedbackVisible) {
        DesignPalette.FavoriteRed
    } else {
        tint
    }
    val iconAlpha by animateFloatAsState(
        targetValue = if (pressed) pressedIconAlpha else 1f,
        animationSpec = tween(
            durationMillis = if (pressed) {
                DesktopTransportPressInDurationMillis
            } else {
                DesktopTransportReleaseDurationMillis
            },
            easing = FastOutSlowInEasing,
        ),
        label = "desktop-player-control-press-alpha",
    )
    Box(
        modifier = modifier
            .size(buttonSize)
            .hoverable(interactionSource, enabled)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = {
                    if (appleMusicActionStyle) actionReleaseFlash = true
                    onClick()
                },
            )
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .requiredSize(if (appleMusicActionStyle) 40.dp else buttonSize)
                .background(overlayColor, CircleShape),
        )
        Icon(
            painter = painter,
            contentDescription = null,
            tint = if (enabled) iconTint else iconTint.copy(alpha = 0.28f),
            modifier = Modifier
                .offset(x = iconOffsetX, y = iconOffsetY)
                .requiredSize(width = iconSize, height = iconHeight)
                .graphicsLayer {
                    alpha = iconAlpha
                    scaleY = iconScaleY
                },
        )
    }
}

private fun formatPlayerTime(milliseconds: ULong): String {
    val seconds = milliseconds / 1_000uL
    return "${seconds / 60uL}:${(seconds % 60uL).toString().padStart(2, '0')}"
}

private fun ULong.saturatingMinus(other: ULong): ULong = if (this > other) this - other else 0uL

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

    ExpandedMiniPlayerBar(
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
                tint = if (isFavorite) DesignPalette.FavoriteRed else actionTint,
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
    IconButton(
        modifier = Modifier.width(if (emphasized) 48.dp else 44.dp),
        onClick = onClick,
        enabled = enabled,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(if (emphasized) 24.dp else 16.dp),
        )
    }
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

    CompactMiniPlayerBar(
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
            PlaybackControlButton(
                painter = painterResource(if (isPlaying) Res.drawable.icon_pause else Res.drawable.icon_play),
                enabled = !loading,
                size = PlaybackControlSize.Mini,
                variant = PlaybackControlVariant.Primary,
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
    onOpenLyrics: (Long) -> Unit,
    onOpenQueue: () -> Unit,
    lyricsSelected: Boolean = false,
    queueSelected: Boolean = false,
    playerVM: PlayerVM = koinViewModel(),
    favoritesRepository: FavoritesRepository = koinInject(),
) {
    val playbackState by playerVM.playbackState.collectAsStateWithLifecycle()
    val playbackPosition by playerVM.playbackPosition.collectAsStateWithLifecycle()
    val nowPlayingState by playerVM.nowPlayingState.collectAsStateWithLifecycle()
    val currentTrack = nowPlayingState.currentTrack
    val favoriteTrackIds by favoritesRepository.favoriteTrackIds.collectAsState(emptySet())
    val coroutineScope = rememberCoroutineScope()
    val isFavorite = currentTrack?.id?.let(favoriteTrackIds::contains) == true
    val durationMs = playbackPosition.durationMs.takeIf { it > 0 }
        ?: currentTrack?.durationMs
        ?: 0

    MiniPlayerCore(
        isPlaying = nowPlayingState.controls.isPlaying,
        title = currentTrack?.title ?: playbackState.currentItem?.title ?: "",
        subtitle = listOfNotNull(
            currentTrack?.artist?.takeIf { it.isNotBlank() }
                ?: playbackState.currentItem?.artist?.takeIf { it.isNotBlank() },
            currentTrack?.album?.takeIf { it.isNotBlank() }
                ?: playbackState.currentItem?.album?.takeIf { it.isNotBlank() },
        ).joinToString(" — ").ifBlank { stringResource(Res.string.player_unknown_artist) },
        cover = currentTrack?.artwork,
        audioBadge = currentTrack?.playbackAudioInfo.desktopAudioBadge(),
        currentDurationMS = playbackPosition.positionMs.coerceAtLeast(0).toULong(),
        totalDurationMS = durationMs.coerceAtLeast(0).toULong(),
        canPrevious = nowPlayingState.queue.canPlayPrevious,
        canNext = nowPlayingState.queue.canPlayNext,
        repeatMode = nowPlayingState.controls.repeatMode,
        shuffleEnabled = nowPlayingState.controls.shuffleEnabled,
        isFavorite = isFavorite,
        lyricsSelected = lyricsSelected,
        queueSelected = queueSelected,
        volume = playerVM.volume(),
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
        onToggleShuffle = { playerVM.toggleShuffle() },
        onCycleRepeatMode = { playerVM.cycleRepeatMode() },
        onSeek = { playerVM.seek(it) },
        onVolumeChange = { playerVM.setVolume(it) },
        onOpenLyrics = {
            currentTrack?.id?.let(onOpenLyrics) ?: onOpenNowPlaying()
        },
        onOpenQueue = onOpenQueue,
    )
}
