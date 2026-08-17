package io.github.julystar.musicapp.service.playback.presentation.nowplaying

import androidx.compose.animation.core.animate
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode as AnimationRepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.model.AudioReactiveSnapshot
import io.github.julystar.musicapp.core.domain.model.LyricDisplaySettings
import io.github.julystar.musicapp.core.domain.model.LyricFontChoice
import io.github.julystar.musicapp.core.domain.model.LyricTextAlignment
import io.github.julystar.musicapp.core.domain.model.LyricsLoadState
import io.github.julystar.musicapp.core.domain.model.PlayerInteractionSettings
import io.github.julystar.musicapp.core.lyrics.ui.LyricsView
import io.github.julystar.musicapp.core.presentation.components.DesignContextMenu
import io.github.julystar.musicapp.core.presentation.components.DesignContextMenuItem
import io.github.julystar.musicapp.core.presentation.components.DesignDialog
import io.github.julystar.musicapp.core.presentation.components.DesignIconButton
import io.github.julystar.musicapp.core.presentation.components.DesignIconButtonSize
import io.github.julystar.musicapp.core.presentation.components.DesignIconButtonVariant
import io.github.julystar.musicapp.core.presentation.components.DesignPlayerControlButton
import io.github.julystar.musicapp.core.presentation.components.DesignPlayerControlSize
import io.github.julystar.musicapp.core.presentation.components.DesignPlayerControlVariant
import io.github.julystar.musicapp.core.presentation.components.DesignSlider
import io.github.julystar.musicapp.core.presentation.components.DesignTextButton
import io.github.julystar.musicapp.core.presentation.components.DesignTextButtonSize
import io.github.julystar.musicapp.core.presentation.components.DesignTextButtonVariant
import io.github.julystar.musicapp.core.presentation.components.dropShadow
import io.github.julystar.musicapp.core.presentation.media.ArtworkImage
import io.github.julystar.musicapp.core.presentation.media.PlayerBackgroundArtworkImage
import io.github.julystar.musicapp.core.presentation.media.rememberArtworkPalette
import io.github.julystar.musicapp.core.presentation.platform.LocalDesktopTitleBarInset
import io.github.julystar.musicapp.core.presentation.theme.DesignFontFamilies
import io.github.julystar.musicapp.core.presentation.theme.DesignPalette
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.julystar.musicapp.core.utils.toMusicDurationMs
import io.github.julystar.musicapp.service.playback.domain.RepeatMode
import io.github.julystar.musicapp.service.playback.presentation.transition.playerArtworkSharedElement
import io.github.julystar.musicapp.service.playback.presentation.transition.playerArtworkTransitionShape
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import musicapp.core.presentation.generated.resources.Res as CoreRes
import musicapp.core.presentation.generated.resources.icon_deleteseep
import musicapp.core.presentation.generated.resources.icon_download
import musicapp.core.presentation.generated.resources.icon_search
import musicapp.core.presentation.generated.resources.icon_settings_sliders
import musicapp.service.playback.presentation.generated.resources.Res
import musicapp.service.playback.presentation.generated.resources.downloads_title
import musicapp.service.playback.presentation.generated.resources.icon_back
import musicapp.service.playback.presentation.generated.resources.icon_heart_compact
import musicapp.service.playback.presentation.generated.resources.icon_heart_compact_filled
import musicapp.service.playback.presentation.generated.resources.icon_lyrics
import musicapp.service.playback.presentation.generated.resources.icon_more_compact
import musicapp.service.playback.presentation.generated.resources.icon_transport_next
import musicapp.service.playback.presentation.generated.resources.icon_transport_pause
import musicapp.service.playback.presentation.generated.resources.icon_transport_play
import musicapp.service.playback.presentation.generated.resources.icon_transport_previous
import musicapp.service.playback.presentation.generated.resources.icon_transport_queue
import musicapp.service.playback.presentation.generated.resources.icon_transport_repeat
import musicapp.service.playback.presentation.generated.resources.icon_transport_repeat_one
import musicapp.service.playback.presentation.generated.resources.icon_transport_shuffle
import musicapp.service.playback.presentation.generated.resources.icon_vertialcal_more
import musicapp.service.playback.presentation.generated.resources.music_lyric_add
import musicapp.service.playback.presentation.generated.resources.music_lyric_fail
import musicapp.service.playback.presentation.generated.resources.music_lyric_no_desc
import musicapp.service.playback.presentation.generated.resources.music_lyric_remove
import musicapp.service.playback.presentation.generated.resources.music_lyric_try_add_desc
import musicapp.service.playback.presentation.generated.resources.music_player_context_menu_remove
import musicapp.service.playback.presentation.generated.resources.music_player_search_metadata
import musicapp.service.playback.presentation.generated.resources.now_playing_title
import musicapp.service.playback.presentation.generated.resources.player_add_favorite
import musicapp.service.playback.presentation.generated.resources.player_loading_lyrics
import musicapp.service.playback.presentation.generated.resources.player_lyrics_unavailable
import musicapp.service.playback.presentation.generated.resources.player_more_options
import musicapp.service.playback.presentation.generated.resources.player_playback_source
import musicapp.service.playback.presentation.generated.resources.player_playback_source_cancel
import musicapp.service.playback.presentation.generated.resources.player_playback_source_current
import musicapp.service.playback.presentation.generated.resources.player_playback_source_description
import musicapp.service.playback.presentation.generated.resources.player_playback_source_title
import musicapp.service.playback.presentation.generated.resources.player_next_track
import musicapp.service.playback.presentation.generated.resources.player_pause
import musicapp.service.playback.presentation.generated.resources.player_play
import musicapp.service.playback.presentation.generated.resources.player_previous_track
import musicapp.service.playback.presentation.generated.resources.player_queue
import musicapp.service.playback.presentation.generated.resources.player_remove_favorite
import musicapp.service.playback.presentation.generated.resources.player_list_repeat
import musicapp.service.playback.presentation.generated.resources.player_shuffle
import musicapp.service.playback.presentation.generated.resources.player_single_repeat
import musicapp.service.playback.presentation.generated.resources.player_unknown_artist
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val DesktopPlayerBreakpoint = 860.dp
private val NowPlayingDismissDistanceThreshold = 240.dp
private val NowPlayingDismissVelocityThreshold = 1_250.dp
private const val NowPlayingDismissSettleDurationMillis = 260
private const val LandscapeControlsAutoHideDelayMs = 5_000L
private val ZeroAudioReactiveSnapshot = MutableStateFlow(AudioReactiveSnapshot())

internal fun doesPlayerCoverStatusBar(
    playerTopInWindowPx: Float,
    dragOffsetPx: Float,
    statusBarBottomInWindowPx: Float,
): Boolean = playerTopInWindowPx + dragOffsetPx < statusBarBottomInWindowPx

@Composable
private fun MusicPlayerHeader(
    onAction: (NowPlayingAction) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .fillMaxWidth(),
    ) {
        DesignIconButton(
            size = DesignIconButtonSize.Medium,
            variant = DesignIconButtonVariant.Default,
            painter = painterResource(Res.drawable.icon_back),
            onClick = { onAction(NowPlayingAction.NavigateBack) },
        )
        Text(
            text = stringResource(Res.string.now_playing_title),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.size(36.dp))
    }
}

@Composable
private fun NowPlayingMoreButton(
    hasLyric: Boolean,
    nowPlayingState: NowPlayingState,
    onAction: (NowPlayingAction) -> Unit,
    compact: Boolean = false,
) {
    var moreMenuExpanded by remember { mutableStateOf(false) }
    var sourceDialogOpen by remember { mutableStateOf(false) }

    Box {
        if (compact) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable { moreMenuExpanded = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.icon_more_compact),
                    contentDescription = stringResource(Res.string.player_more_options),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        } else {
            DesignIconButton(
                size = DesignIconButtonSize.Medium,
                variant = DesignIconButtonVariant.Default,
                painter = painterResource(Res.drawable.icon_vertialcal_more),
                contentDescription = stringResource(Res.string.player_more_options),
                onClick = { moreMenuExpanded = true },
            )
        }
        Box(
            contentAlignment = Alignment.TopEnd,
            modifier = Modifier.offset(20.dp, 20.dp),
        ) {
            DesignContextMenu(
                expanded = moreMenuExpanded,
                onDismissRequest = { moreMenuExpanded = false },
                compact = compact,
                items = listOfNotNull(
                    DesignContextMenuItem(
                        label = Res.string.music_player_search_metadata,
                        icon = CoreRes.drawable.icon_search,
                        onClick = {
                            moreMenuExpanded = false
                            onAction(NowPlayingAction.SearchMetadata)
                        },
                    ),
                    if (hasLyric) {
                        DesignContextMenuItem(
                            label = Res.string.music_lyric_remove,
                            icon = CoreRes.drawable.icon_deleteseep,
                            onClick = {
                                moreMenuExpanded = false
                                onAction(NowPlayingAction.RemoveLyric)
                            },
                        )
                    } else {
                        DesignContextMenuItem(
                            label = Res.string.music_lyric_add,
                            icon = Res.drawable.icon_lyrics,
                            onClick = {
                                moreMenuExpanded = false
                                onAction(NowPlayingAction.AddLyric)
                            },
                        )
                    },
                    if (nowPlayingState.currentTrack?.canDownload == true) {
                        DesignContextMenuItem(
                            label = Res.string.downloads_title,
                            icon = CoreRes.drawable.icon_download,
                            onClick = {
                                moreMenuExpanded = false
                                onAction(NowPlayingAction.DownloadCurrentTrack)
                            },
                        )
                    } else null,
                    if (nowPlayingState.playbackSources.size > 1) {
                        DesignContextMenuItem(
                            label = Res.string.player_playback_source,
                            icon = CoreRes.drawable.icon_settings_sliders,
                            onClick = {
                                moreMenuExpanded = false
                                sourceDialogOpen = true
                            },
                        )
                    } else null,
                    DesignContextMenuItem(
                        label = Res.string.music_player_context_menu_remove,
                        icon = CoreRes.drawable.icon_deleteseep,
                        isError = true,
                        onClick = {
                            moreMenuExpanded = false
                            onAction(NowPlayingAction.RemoveCurrentTrack)
                        },
                    ),
                ),
            )
        }
    }
    PlaybackSourceDialog(
        show = sourceDialogOpen,
        sources = nowPlayingState.playbackSources,
        onSelect = { sourceItemId ->
            sourceDialogOpen = false
            onAction(NowPlayingAction.SelectPlaybackSource(sourceItemId))
        },
        onDismiss = { sourceDialogOpen = false },
    )
}

@Composable
private fun PlaybackSourceDialog(
    show: Boolean,
    sources: List<NowPlayingSourceItem>,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    DesignDialog(show = show, onDismiss = onDismiss) {
        Text(
            text = stringResource(Res.string.player_playback_source_title),
            style = MiuixTheme.textStyles.title3,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(DesignTokens.spacing.xs))
        Text(
            text = stringResource(Res.string.player_playback_source_description),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.height(DesignTokens.spacing.sm))
        sources.forEach { source ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(DesignTokens.shapes.md))
                    .clickable { onSelect(source.sourceItemId) }
                    .padding(horizontal = DesignTokens.spacing.sm, vertical = DesignTokens.spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = source.accountName,
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(source.displayName, source.quality)
                            .joinToString(" · "),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (source.isSelected) {
                    Text(
                        text = stringResource(Res.string.player_playback_source_current),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(DesignTokens.spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            DesignTextButton(
                text = stringResource(Res.string.player_playback_source_cancel),
                variant = DesignTextButtonVariant.Default,
                size = DesignTextButtonSize.Medium,
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun MusicSlider(
    currentDuration: String,
    currentDurationMs: ULong,
    bufferDurationMs: ULong,
    totalDuration: String,
    totalDurationMs: ULong,
    tapToSeekEnabled: Boolean,
    showTotalDuration: Boolean,
    onChangeMusicPosition: (ms: ULong) -> Unit,
    lightTheme: Boolean = false,
    compact: Boolean = false,
    immersive: Boolean = false,
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubbingDurationMs by remember { mutableStateOf(currentDurationMs) }
    val displayedDurationMs = if (isScrubbing) scrubbingDurationMs else currentDurationMs
    val sliderRange = 0f..totalDurationMs.toFloat().coerceAtLeast(1f)
    val labelColor = if (immersive) {
        Color.White.copy(alpha = 0.52f)
    } else if (lightTheme) {
        Color.White.copy(alpha = 0.40f)
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        DesignSlider(
            value = displayedDurationMs.toFloat(),
            onValueChange = { value ->
                scrubbingDurationMs = value.toLong()
                    .coerceIn(0L, totalDurationMs.toLong())
                    .toULong()
            },
            modifier = Modifier.fillMaxWidth(),
            valueRange = sliderRange,
            bufferedValue = bufferDurationMs.toFloat(),
            tapToSeekEnabled = tapToSeekEnabled,
            height = if (immersive) {
                if (compact) 16.dp else 24.dp
            } else {
                16.dp
            },
            trackHeight = if (immersive) 3.dp else 4.dp,
            thumbSize = if (immersive) 8.dp else 12.dp,
            activeThumbSize = if (immersive) {
                if (compact) 10.dp else 12.dp
            } else {
                16.dp
            },
            trackColorOverride = Color.White.copy(alpha = if (immersive) 0.20f else 0.28f),
            bufferColorOverride = Color.White.copy(alpha = if (immersive) 0.20f else 0.44f),
            activeTrackColorOverride = Color.White.copy(alpha = if (immersive) 0.85f else 1f),
            thumbColorOverride = Color.White,
            onValueChangeStarted = {
                isScrubbing = true
                scrubbingDurationMs = currentDurationMs.coerceAtMost(totalDurationMs)
            },
            onValueChangeFinished = {
                val nextDurationMs = scrubbingDurationMs.coerceAtMost(totalDurationMs)
                isScrubbing = false
                onChangeMusicPosition(nextDurationMs)
            },
        )
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (immersive) if (compact) 2.dp else 4.dp else 0.dp),
        ) {
            val durationStyle = if (immersive) {
                TextStyle(
                    fontFamily = DesignFontFamilies.Mono,
                    fontSize = if (compact) 12.sp else 15.sp,
                    lineHeight = if (compact) 17.sp else 21.sp,
                )
            } else {
                MiuixTheme.textStyles.footnote2.copy(fontFamily = DesignFontFamilies.Mono)
            }
            Text(
                text = currentDuration,
                color = labelColor,
                style = durationStyle,
            )
            Text(
                text = if (showTotalDuration) {
                    totalDuration
                } else {
                    val remainingMs = totalDurationMs.toLong() - displayedDurationMs.toLong()
                    "-${formatPlayerDuration(remainingMs.coerceAtLeast(0).milliseconds)}"
                },
                color = labelColor,
                style = durationStyle,
            )
        }
    }
}

@Composable
private fun CoverImage(
    artwork: Artwork?,
    modifier: Modifier = Modifier,
    maxArtworkSize: Dp = 400.dp,
    cornerRadius: Dp = DesignTokens.shapes.xl,
    shadowOffsetY: Dp = 18.dp,
    shadowBlurRadius: Dp = 38.dp,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Transparent,
    swipeEnabled: Boolean = false,
    onSwipePrevious: () -> Unit = {},
    onSwipeNext: () -> Unit = {},
) {
    val artworkShape = remember(maxArtworkSize, cornerRadius) {
        playerArtworkTransitionShape(
            expandedSize = maxArtworkSize,
            expandedCornerRadius = cornerRadius,
        )
    }
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        val artworkSize = minOf(maxWidth, maxHeight, maxArtworkSize)
        Box(
            modifier = Modifier
                .playerArtworkSharedElement()
                .size(artworkSize)
                .playerCoverSwipe(
                    enabled = swipeEnabled,
                    onSwipePrevious = onSwipePrevious,
                    onSwipeNext = onSwipeNext,
                )
                .dropShadow(
                    color = Color.Black.copy(alpha = 0.32f),
                    offsetX = 0.dp,
                    offsetY = shadowOffsetY,
                    blurRadius = shadowBlurRadius,
                )
                .clip(artworkShape)
                .border(
                    width = borderWidth,
                    color = borderColor,
                    shape = artworkShape,
                )
                .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.24f)),
        ) {
            ArtworkImage(
                modifier = Modifier.fillMaxSize(),
                artwork = artwork,
                contentScale = ContentScale.Crop,
                smoothTransition = true,
            )
        }
    }
}

private fun Modifier.playerCoverSwipe(
    enabled: Boolean,
    onSwipePrevious: () -> Unit,
    onSwipeNext: () -> Unit,
): Modifier = pointerInput(enabled) {
    if (enabled) {
        var accumulatedDrag = 0f
        detectHorizontalDragGestures(
            onDragStart = { accumulatedDrag = 0f },
            onHorizontalDrag = { _, dragAmount -> accumulatedDrag += dragAmount },
            onDragEnd = {
                if (abs(accumulatedDrag) >= 72f) {
                    if (accumulatedDrag > 0f) onSwipePrevious() else onSwipeNext()
                }
            },
        )
    }
}

@Composable
private fun TrackInformation(
    track: NowPlayingTrackItem?,
    lyricDisplaySettings: LyricDisplaySettings,
    modifier: Modifier = Modifier,
    lightTheme: Boolean = false,
    compact: Boolean = false,
) {
    val customFontWeight = FontWeight(lyricDisplaySettings.font.weight.coerceIn(100, 900))
    val titleFontFamily = lyricDisplaySettings.pageFontFamilyFor(track?.title.orEmpty())
    val artistText = track?.artist?.takeIf { it.isNotBlank() }
        ?: stringResource(Res.string.player_unknown_artist)
    val artistFontFamily = lyricDisplaySettings.pageFontFamilyFor(artistText)
    val textColor = if (lightTheme) Color.White else MiuixTheme.colorScheme.onSurface
    val mutedColor = if (lightTheme) Color.White.copy(alpha = 0.55f) else MiuixTheme.colorScheme.onSurfaceVariantSummary

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = track?.title.orEmpty(),
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            color = textColor,
            style = TextStyle(
                fontFamily = titleFontFamily ?: DesignFontFamilies.Sans,
                fontSize = if (compact) 20.sp else 24.sp,
                fontWeight = if (titleFontFamily == null) FontWeight.Bold else customFontWeight,
                lineHeight = if (compact) 28.sp else 30.sp,
            ),
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = artistText,
            color = mutedColor,
            style = if (compact) {
                TextStyle(
                    fontFamily = artistFontFamily ?: DesignFontFamilies.Sans,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = if (artistFontFamily == null) FontWeight.Medium else customFontWeight,
                )
            } else {
                MiuixTheme.textStyles.body1.let { style ->
                    if (artistFontFamily == null) style else style.copy(
                        fontFamily = artistFontFamily,
                        fontWeight = customFontWeight,
                    )
                }
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 3.dp),
        )
    }
}

@Composable
private fun CompactTransportPanel(
    nowPlayingState: NowPlayingState,
    onAction: (NowPlayingAction) -> Unit,
    dense: Boolean,
) {
    val controls = nowPlayingState.controls
    val queue = nowPlayingState.queue
    val playbackModeDrawable: DrawableResource = when {
        controls.shuffleEnabled -> Res.drawable.icon_transport_shuffle
        controls.repeatMode == RepeatMode.One -> Res.drawable.icon_transport_repeat_one
        else -> Res.drawable.icon_transport_repeat
    }
    val playbackModeDescription = stringResource(
        when {
            controls.shuffleEnabled -> Res.string.player_shuffle
            controls.repeatMode == RepeatMode.One -> Res.string.player_single_repeat
            else -> Res.string.player_list_repeat
        },
    )
    val playbackModeTint = if (controls.shuffleEnabled || controls.repeatMode == RepeatMode.One) {
        MiuixTheme.colorScheme.primary
    } else {
        Color.White.copy(alpha = 0.82f)
    }
    val secondaryButtonSize = if (dense) 44.dp else 56.dp
    val primaryButtonSize = if (dense) 58.dp else 72.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (dense) 62.dp else 84.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactTransportButton(
            painter = playbackModeDrawable,
            contentDescription = playbackModeDescription,
            tint = playbackModeTint,
            buttonSize = secondaryButtonSize,
            iconSize = if (dense) 21.dp else 24.dp,
            onClick = { onAction(NowPlayingAction.CycleRepeatMode) },
            modifier = Modifier.weight(1f),
        )
        CompactTransportButton(
            painter = Res.drawable.icon_transport_previous,
            contentDescription = stringResource(Res.string.player_previous_track),
            tint = Color.White,
            buttonSize = secondaryButtonSize,
            iconSize = if (dense) 28.dp else 30.dp,
            enabled = queue.canPlayPrevious,
            onClick = { onAction(NowPlayingAction.PlayPrevious) },
            modifier = Modifier.weight(1f),
        )
        CompactTransportButton(
            painter = if (controls.isPlaying) {
                Res.drawable.icon_transport_pause
            } else {
                Res.drawable.icon_transport_play
            },
            contentDescription = stringResource(
                if (controls.isPlaying) Res.string.player_pause else Res.string.player_play,
            ),
            tint = Color.White,
            background = Color.White.copy(alpha = 0.16f),
            buttonSize = primaryButtonSize,
            iconSize = when {
                controls.isPlaying && dense -> 28.dp
                controls.isPlaying -> 32.dp
                dense -> 32.dp
                else -> 36.dp
            },
            enabled = controls.isPlaying || !controls.isLoading,
            onClick = {
                onAction(if (controls.isPlaying) NowPlayingAction.Pause else NowPlayingAction.Resume)
            },
            iconOffsetX = if (controls.isPlaying) 0.dp else 3.dp,
            showShadow = !dense,
            modifier = Modifier.weight(1f),
        )
        CompactTransportButton(
            painter = Res.drawable.icon_transport_next,
            contentDescription = stringResource(Res.string.player_next_track),
            tint = Color.White,
            buttonSize = secondaryButtonSize,
            iconSize = if (dense) 28.dp else 30.dp,
            enabled = queue.canPlayNext,
            onClick = { onAction(NowPlayingAction.PlayNext) },
            modifier = Modifier.weight(1f),
        )
        CompactTransportButton(
            painter = Res.drawable.icon_transport_queue,
            contentDescription = stringResource(Res.string.player_queue),
            tint = Color.White.copy(alpha = 0.72f),
            buttonSize = secondaryButtonSize,
            iconSize = if (dense) 22.dp else 25.dp,
            onClick = { onAction(NowPlayingAction.OpenQueue) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CompactTransportButton(
    painter: DrawableResource,
    contentDescription: String,
    tint: Color,
    buttonSize: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Color.Transparent,
    enabled: Boolean = true,
    iconOffsetX: Dp = 0.dp,
    showShadow: Boolean = true,
) {
    Box(
        modifier = modifier.fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .sizeIn(maxWidth = buttonSize, maxHeight = buttonSize)
                .aspectRatio(1f)
                .then(
                    if (showShadow && background.alpha > 0f) {
                        Modifier.shadow(
                            elevation = 12.dp,
                            shape = CircleShape,
                            clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.18f),
                            spotColor = Color.Black.copy(alpha = 0.18f),
                        )
                    } else {
                        Modifier
                    },
                )
                .clip(CircleShape)
                .background(background, CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(painter),
                contentDescription = contentDescription,
                tint = tint.copy(alpha = if (enabled) tint.alpha else tint.alpha * 0.32f),
                modifier = Modifier
                    .size(iconSize)
                    .offset(x = iconOffsetX),
            )
        }
    }
}

@Composable
private fun DesktopNowPlayingLayout(
    state: NowPlayingState,
    lyricDisplaySettings: LyricDisplaySettings,
    playerInteractionSettings: PlayerInteractionSettings,
    currentPositionMs: Long,
    isSeeking: Boolean,
    progressContent: @Composable (Long?) -> Unit,
    liked: Boolean,
    onLikedChange: (Boolean) -> Unit,
    onAction: (NowPlayingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.currentTrack

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 34.dp, end = 28.dp, bottom = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(34.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(0.46f)
                .fillMaxHeight()
                .widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CoverImage(
                artwork = track?.artwork,
                swipeEnabled = playerInteractionSettings.coverSwipeEnabled,
                onSwipePrevious = {
                    if (state.queue.canPlayPrevious) onAction(NowPlayingAction.PlayPrevious)
                },
                onSwipeNext = {
                    if (state.queue.canPlayNext) onAction(NowPlayingAction.PlayNext)
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
            )
            TrackRow(
                state = state,
                lyricDisplaySettings = lyricDisplaySettings,
                showAudioTechnicalInfo = playerInteractionSettings.showAudioTechnicalInfo,
                liked = liked,
                onLikedChange = onLikedChange,
                onAction = onAction,
                modifier = Modifier.padding(top = 10.dp),
                compact = false,
            )
            Spacer(modifier = Modifier.height(14.dp))
            progressContent(track?.durationMs)
            Spacer(modifier = Modifier.height(20.dp))
            CompactTransportPanel(
                nowPlayingState = state,
                onAction = onAction,
                dense = false,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        LyricsSurface(
            track = track,
            lyricDisplaySettings = lyricDisplaySettings,
            currentPositionMs = currentPositionMs,
            isPlaying = state.controls.isPlaying && !isSeeking,
            onAction = onAction,
            modifier = Modifier
                .weight(0.54f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun LyricsSurface(
    track: NowPlayingTrackItem?,
    lyricDisplaySettings: LyricDisplaySettings,
    currentPositionMs: Long,
    isPlaying: Boolean,
    onAction: (NowPlayingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val loadState = track?.lyrics?.loadState ?: LyricsLoadState.Loading
    val lyricLines = track?.lyrics?.lines.orEmpty()
    val syncedLyrics = remember(lyricLines, track?.title, track?.durationMs, lyricDisplaySettings) {
        lyricLines.toSyncedLyrics(
            trackTitle = track?.title.orEmpty(),
            trackDurationMs = track?.durationMs,
            settings = lyricDisplaySettings,
        )
    }
    val primaryScale = lyricDisplaySettings.primaryFontScalePercent / 100f
    val primaryFontSize = lyricDisplaySettings.primaryFontSizeSp * primaryScale
    val secondaryScale = lyricDisplaySettings.secondaryFontScalePercent / 100f
    val secondaryFontSize = lyricDisplaySettings.secondaryFontSizeSp * secondaryScale
    val lyricTextAlign = when (lyricDisplaySettings.textAlignment) {
        LyricTextAlignment.Left -> TextAlign.Start
        LyricTextAlignment.Center -> TextAlign.Center
        LyricTextAlignment.Right -> TextAlign.End
    }
    val lyricFontFamily = if (lyricDisplaySettings.font.applyToLyricsPage) {
        val containsCjk = lyricLines.any { line -> line.text.any(Char::isCjkCharacter) }
        val choice = if (containsCjk) lyricDisplaySettings.font.cjkFont else lyricDisplaySettings.font.westernFont
        choice.toFontFamily()
    } else {
        FontFamily.Default
    }
    val lyricFontWeight = FontWeight(lyricDisplaySettings.font.weight.coerceIn(100, 900))

    Box(
        modifier = modifier.clip(RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loadState == LyricsLoadState.Loading -> {
                Text(
                    text = stringResource(Res.string.player_loading_lyrics),
                    color = Color.White.copy(alpha = 0.55f),
                    style = MiuixTheme.textStyles.body1,
                )
            }
            loadState == LyricsLoadState.Missing || loadState == LyricsLoadState.Failed -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        modifier = Modifier.size(58.dp),
                        painter = painterResource(Res.drawable.icon_lyrics),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.30f),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            if (loadState == LyricsLoadState.Missing) {
                                Res.string.music_lyric_no_desc
                            } else {
                                Res.string.music_lyric_fail
                            },
                        ),
                        color = Color.White.copy(alpha = 0.55f),
                        style = MiuixTheme.textStyles.body1,
                    )
                    if (loadState == LyricsLoadState.Missing) {
                        Spacer(modifier = Modifier.height(8.dp))
                        DesignTextButton(
                            text = stringResource(Res.string.music_lyric_try_add_desc),
                            variant = DesignTextButtonVariant.Primary,
                            size = DesignTextButtonSize.Medium,
                            onClick = { onAction(NowPlayingAction.AddLyric) },
                        )
                    }
                }
            }
            syncedLyrics.lines.isEmpty() -> {
                Text(
                    text = stringResource(Res.string.music_lyric_no_desc),
                    color = Color.White.copy(alpha = 0.55f),
                    style = MiuixTheme.textStyles.body1,
                )
            }
            else -> {
                LyricsView(
                    lyrics = syncedLyrics,
                    currentPositionMs = currentPositionMs.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
                    isPlaying = isPlaying,
                    onLineClick = { line ->
                        onAction(NowPlayingAction.SeekTo(line.start.coerceAtLeast(0).toULong()))
                    },
                    activeColor = Color.White,
                    inactiveColor = Color.White.copy(alpha = 0.42f),
                    activeTextStyle = TextStyle(
                        fontFamily = lyricFontFamily,
                        fontSize = primaryFontSize.sp,
                        lineHeight = (primaryFontSize * 1.25f).sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    inactiveTextStyle = TextStyle(
                        fontFamily = lyricFontFamily,
                        fontSize = (primaryFontSize * 0.84f).sp,
                        lineHeight = (primaryFontSize * 1.08f).sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    secondaryTextStyle = TextStyle(
                        fontFamily = lyricFontFamily,
                        fontSize = secondaryFontSize.sp,
                        lineHeight = (secondaryFontSize * 1.28f).sp,
                        fontWeight = lyricFontWeight,
                    ),
                    textAlign = lyricTextAlign,
                    showTranslation = lyricDisplaySettings.showTranslation,
                    wordLiftEnabled = lyricDisplaySettings.wordLiftEnabled,
                    useBlurEffect = lyricDisplaySettings.blurEffectEnabled,
                    perspectiveEffectEnabled = lyricDisplaySettings.perspectiveEffectEnabled,
                    perspectiveAngleDegrees = lyricDisplaySettings.perspectiveAngleDegrees.toFloat(),
                    tapToSeekEnabled = lyricDisplaySettings.tapToSeekEnabled,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun LyricFontChoice.toFontFamily(): FontFamily = when (this) {
    LyricFontChoice.System -> FontFamily.Default
    LyricFontChoice.AppSans -> DesignFontFamilies.JakartaSans
    LyricFontChoice.AppCjk -> DesignFontFamilies.Sans
    LyricFontChoice.Monospace -> DesignFontFamilies.Mono
}

@Composable
private fun LyricDisplaySettings.pageFontFamilyFor(text: String): FontFamily? {
    if (!font.applyToLyricsPage) return null
    val choice = if (text.any(Char::isCjkCharacter)) font.cjkFont else font.westernFont
    return choice.toFontFamily()
}

private fun Char.isCjkCharacter(): Boolean = code in 0x2E80..0x9FFF ||
    code in 0xAC00..0xD7AF ||
    code in 0xF900..0xFAFF

@Composable
private fun CompactArtworkArea(
    artwork: Artwork?,
    isPlaying: Boolean,
    coverSwipeEnabled: Boolean,
    onSwipePrevious: () -> Unit,
    onSwipeNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetArtworkSize = compactArtworkTargetSize(isPlaying)
    val artworkSize by animateDpAsState(
        targetValue = targetArtworkSize,
        animationSpec = spring(stiffness = 180f),
        label = "compactArtworkSize",
    )
    Box(
        modifier = modifier.aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        CoverImage(
            artwork = artwork,
            modifier = Modifier.fillMaxSize(),
            maxArtworkSize = artworkSize,
            cornerRadius = 28.dp,
            shadowOffsetY = 20.dp,
            shadowBlurRadius = 44.dp,
            borderWidth = 1.dp,
            borderColor = Color.White.copy(alpha = 0.10f),
            swipeEnabled = coverSwipeEnabled,
            onSwipePrevious = onSwipePrevious,
            onSwipeNext = onSwipeNext,
        )
    }
}

@Composable
private fun ImmersiveArtworkArea(
    artwork: Artwork?,
    surfaceColor: Color,
    coverSwipeEnabled: Boolean,
    onSwipePrevious: () -> Unit,
    onSwipeNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val artworkExpandedSize = minOf(maxWidth, maxHeight)
        val artworkShape = remember(artworkExpandedSize) {
            playerArtworkTransitionShape(
                expandedSize = artworkExpandedSize,
                expandedCornerRadius = 0.dp,
            )
        }
        Box(
            modifier = Modifier
                .playerArtworkSharedElement()
                .fillMaxSize()
                .playerCoverSwipe(
                    enabled = coverSwipeEnabled,
                    onSwipePrevious = onSwipePrevious,
                    onSwipeNext = onSwipeNext,
                )
                .clip(artworkShape)
                .background(Color.Black),
        ) {
            ArtworkImage(
                modifier = Modifier.fillMaxSize(),
                artwork = artwork,
                contentScale = ContentScale.Fit,
                smoothTransition = true,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.42f),
                                Color.Black.copy(alpha = 0.30f),
                                Color.Black.copy(alpha = 0.54f),
                            ),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.40f to surfaceColor.copy(alpha = 0.30f),
                                0.72f to surfaceColor.copy(alpha = 0.82f),
                                1f to surfaceColor,
                            ),
                        ),
                    ),
            )
        }
    }
}

internal fun compactArtworkTargetSize(isPlaying: Boolean): Dp =
    CompactArtworkExpandedSize * if (isPlaying) 1f else CompactArtworkPausedScale

private val CompactArtworkExpandedSize = 356.dp
private const val CompactArtworkPausedScale = 0.96f
private const val CompactPlayerContentWidthFraction = 0.88f
private val CompactPlayerLyricsLineHorizontalPadding = 8.dp
private val CompactImmersiveContentHorizontalPadding = 28.dp
private val CompactPlayerControlsBottomInset = 44.dp

internal fun immersiveLyricsLineHorizontalPadding(viewportWidth: Dp): Dp =
    (
        viewportWidth * ((1f - CompactPlayerContentWidthFraction) / 2f) +
            CompactPlayerLyricsLineHorizontalPadding -
            CompactImmersiveContentHorizontalPadding
        ).coerceAtLeast(0.dp)

@Composable
private fun TrackRow(
    state: NowPlayingState,
    lyricDisplaySettings: LyricDisplaySettings,
    showAudioTechnicalInfo: Boolean,
    liked: Boolean,
    onLikedChange: (Boolean) -> Unit,
    onAction: (NowPlayingAction) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
    dense: Boolean = false,
) {
    val track = state.currentTrack
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackInformation(
                track = track,
                lyricDisplaySettings = lyricDisplaySettings,
                modifier = Modifier.weight(1f),
                lightTheme = true,
                compact = compact,
            )
            Box(
                modifier = Modifier
                    .size(if (dense) 40.dp else 44.dp)
                    .clip(CircleShape)
                    .clickable { onLikedChange(!liked) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(
                        if (liked) Res.drawable.icon_heart_compact_filled else Res.drawable.icon_heart_compact,
                    ),
                    contentDescription = stringResource(
                        if (liked) Res.string.player_remove_favorite else Res.string.player_add_favorite,
                    ),
                    tint = if (liked) DesignPalette.FavoriteRed else Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.size(if (dense) 20.dp else 24.dp),
                )
            }
            NowPlayingMoreButton(
                hasLyric = track?.hasLyric == true,
                nowPlayingState = state,
                onAction = onAction,
                compact = true,
            )
        }
        if (showAudioTechnicalInfo) {
            track?.audioQuality?.takeIf(String::isNotBlank)?.let { quality ->
                Text(
                    text = quality,
                    color = Color.White.copy(alpha = 0.55f),
                    style = TextStyle(
                        fontFamily = DesignFontFamilies.Mono,
                        fontSize = when {
                            dense -> 11.sp
                            compact -> 12.sp
                            else -> 13.sp
                        },
                        lineHeight = when {
                            dense -> 15.sp
                            compact -> 17.sp
                            else -> 18.sp
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun CompactLyricsSurface(
    track: NowPlayingTrackItem?,
    lyricDisplaySettings: LyricDisplaySettings,
    currentPositionMs: Long,
    isPlaying: Boolean,
    onLineClick: () -> Unit,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
    lineHorizontalPadding: Dp = if (dense) 0.dp else CompactPlayerLyricsLineHorizontalPadding,
    onSurfaceClick: (() -> Unit)? = null,
    isPortrait: Boolean = false,
) {
    val loadState = track?.lyrics?.loadState ?: LyricsLoadState.Loading
    val lyricLines = track?.lyrics?.lines.orEmpty()
    val syncedLyrics = remember(lyricLines, track?.title, track?.durationMs, lyricDisplaySettings) {
        lyricLines.toSyncedLyrics(
            trackTitle = track?.title.orEmpty(),
            trackDurationMs = track?.durationMs,
            settings = lyricDisplaySettings,
        )
    }
    val contextLinesBeforeActive = nowPlayingLyricsContextLinesBeforeActive(
        isPortrait = isPortrait,
        showTranslation = lyricDisplaySettings.showTranslation,
        hasTranslation = syncedLyrics.hasTranslation(),
    )
    val lyricTextAlign = when (lyricDisplaySettings.textAlignment) {
        LyricTextAlignment.Left -> TextAlign.Start
        LyricTextAlignment.Center -> TextAlign.Center
        LyricTextAlignment.Right -> TextAlign.End
    }
    val lyricFontFamily = if (lyricDisplaySettings.font.applyToLyricsPage) {
        val containsCjk = lyricLines.any { line -> line.text.any(Char::isCjkCharacter) }
        val choice = if (containsCjk) lyricDisplaySettings.font.cjkFont else lyricDisplaySettings.font.westernFont
        choice.toFontFamily()
    } else {
        FontFamily.Default
    }
    val lyricFontWeight = FontWeight(lyricDisplaySettings.font.weight.coerceIn(100, 900))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onSurfaceClick != null) Modifier.clickable(onClick = onSurfaceClick) else Modifier,
            ),
        contentAlignment = Alignment.TopStart,
    ) {
        when {
            loadState == LyricsLoadState.Loading -> {
                CompactLyricsStatus(
                    text = stringResource(Res.string.player_loading_lyrics),
                    dense = dense,
                    horizontalPadding = lineHorizontalPadding,
                )
            }
            loadState == LyricsLoadState.Missing ||
                loadState == LyricsLoadState.Failed ||
                syncedLyrics.lines.isEmpty() -> {
                CompactLyricsStatus(
                    text = stringResource(Res.string.player_lyrics_unavailable),
                    dense = dense,
                    horizontalPadding = lineHorizontalPadding,
                )
            }
            else -> {
                val activeFontSize = if (dense) 16f else 17f
                val inactiveFontSize = if (dense) 14f else 15f
                val secondaryFontSize = if (dense) 11f else 12f
                LyricsView(
                    lyrics = syncedLyrics,
                    currentPositionMs = currentPositionMs.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
                    isPlaying = isPlaying,
                    onLineClick = { onLineClick() },
                    activeColor = Color.White,
                    inactiveColor = Color.White.copy(alpha = 0.42f),
                    activeTextStyle = TextStyle(
                        fontFamily = lyricFontFamily,
                        fontSize = activeFontSize.sp,
                        lineHeight = (activeFontSize * 1.4f).sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    inactiveTextStyle = TextStyle(
                        fontFamily = lyricFontFamily,
                        fontSize = inactiveFontSize.sp,
                        lineHeight = (inactiveFontSize * 1.4f).sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    secondaryTextStyle = TextStyle(
                        fontFamily = lyricFontFamily,
                        fontSize = secondaryFontSize.sp,
                        lineHeight = (secondaryFontSize * 1.34f).sp,
                        fontWeight = lyricFontWeight,
                    ),
                    textAlign = lyricTextAlign,
                    lineSpacing = if (dense) 0.dp else 2.dp,
                    showTranslation = lyricDisplaySettings.showTranslation,
                    wordLiftEnabled = lyricDisplaySettings.wordLiftEnabled,
                    useBlurEffect = lyricDisplaySettings.blurEffectEnabled,
                    tapToSeekEnabled = true,
                    verticalContentPaddingFraction = 0.04f,
                    lineHorizontalPadding = lineHorizontalPadding,
                    lineVerticalPadding = if (dense) 1.dp else 2.dp,
                    contextLinesBeforeActive = contextLinesBeforeActive,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

internal fun nowPlayingLyricsContextLinesBeforeActive(
    isPortrait: Boolean,
    showTranslation: Boolean,
    hasTranslation: Boolean,
): Int = if (isPortrait && showTranslation && hasTranslation) 0 else 1

private fun SyncedLyrics.hasTranslation(): Boolean = lines.any { line ->
    when (line) {
        is KaraokeLine -> !line.translation.isNullOrBlank()
        is SyncedLine -> !line.translation.isNullOrBlank()
        else -> false
    }
}

@Composable
private fun CompactLyricsStatus(
    text: String,
    dense: Boolean,
    horizontalPadding: Dp,
) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.52f),
        style = MiuixTheme.textStyles.title3.copy(
            fontSize = if (dense) 16.sp else 17.sp,
            lineHeight = if (dense) 22.sp else 24.sp,
        ),
        modifier = Modifier.padding(
            horizontal = horizontalPadding,
            vertical = if (dense) 4.dp else 12.dp,
        ),
    )
}

@Composable
private fun CompactClassicNowPlayingLayout(
    state: NowPlayingState,
    lyricDisplaySettings: LyricDisplaySettings,
    playerInteractionSettings: PlayerInteractionSettings,
    currentPositionMs: Long,
    isSeeking: Boolean,
    liked: Boolean,
    onLikedChange: (Boolean) -> Unit,
    composeChrome: Boolean,
    chromeAlpha: Float,
    progressContent: @Composable (Long?) -> Unit,
    onAction: (NowPlayingAction) -> Unit,
    isPortrait: Boolean,
) {
    val track = state.currentTrack

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 90.dp, bottom = CompactPlayerControlsBottomInset),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(CompactPlayerContentWidthFraction)
                .widthIn(max = 356.dp),
        ) {
            CompactArtworkArea(
                artwork = track?.artwork,
                isPlaying = state.controls.isPlaying,
                coverSwipeEnabled = playerInteractionSettings.coverSwipeEnabled,
                onSwipePrevious = {
                    if (state.queue.canPlayPrevious) onAction(NowPlayingAction.PlayPrevious)
                },
                onSwipeNext = {
                    if (state.queue.canPlayNext) onAction(NowPlayingAction.PlayNext)
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (composeChrome) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = chromeAlpha },
                    ) {
                        TrackRow(
                            state = state,
                            lyricDisplaySettings = lyricDisplaySettings,
                            showAudioTechnicalInfo = playerInteractionSettings.showAudioTechnicalInfo,
                            liked = liked,
                            onLikedChange = onLikedChange,
                            onAction = onAction,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, top = 20.dp, end = 8.dp),
                        )

                        CompactLyricsSurface(
                            track = track,
                            lyricDisplaySettings = lyricDisplaySettings,
                            currentPositionMs = currentPositionMs,
                            isPlaying = state.controls.isPlaying && !isSeeking,
                            onLineClick = { onAction(NowPlayingAction.OpenLyrics) },
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 12.dp, bottom = 16.dp),
                            isPortrait = isPortrait,
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                        ) {
                            Box(modifier = Modifier.offset(y = (-8).dp)) {
                                progressContent(track?.durationMs)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            CompactTransportPanel(
                                nowPlayingState = state,
                                onAction = onAction,
                                dense = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactImmersiveNowPlayingLayout(
    state: NowPlayingState,
    lyricDisplaySettings: LyricDisplaySettings,
    playerInteractionSettings: PlayerInteractionSettings,
    currentPositionMs: Long,
    isSeeking: Boolean,
    liked: Boolean,
    onLikedChange: (Boolean) -> Unit,
    progressContent: @Composable (Long?) -> Unit,
    onAction: (NowPlayingAction) -> Unit,
    isPortrait: Boolean,
) {
    val track = state.currentTrack
    val palette = rememberArtworkPalette(track?.artwork)
    val surfaceColor by animateColorAsState(
        targetValue = lerp(palette.muted, Color.Black, 0.66f).copy(alpha = 1f),
        animationSpec = tween(durationMillis = 700),
        label = "immersiveSurfaceColor",
    )
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val coverHeight = minOf(
            maxWidth,
            maxHeight * 0.47f,
        )
        val lyricsLineHorizontalPadding = immersiveLyricsLineHorizontalPadding(maxWidth)
        Column(modifier = Modifier.fillMaxSize()) {
            ImmersiveArtworkArea(
                artwork = track?.artwork,
                surfaceColor = surfaceColor,
                coverSwipeEnabled = playerInteractionSettings.coverSwipeEnabled,
                onSwipePrevious = {
                    if (state.queue.canPlayPrevious) onAction(NowPlayingAction.PlayPrevious)
                },
                onSwipeNext = {
                    if (state.queue.canPlayNext) onAction(NowPlayingAction.PlayNext)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(coverHeight),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to surfaceColor,
                                0.16f to surfaceColor.copy(alpha = 0.94f),
                                1f to surfaceColor.copy(alpha = 0.90f),
                            ),
                        ),
                    )
                    .padding(
                        start = CompactImmersiveContentHorizontalPadding,
                        end = CompactImmersiveContentHorizontalPadding,
                        bottom = CompactPlayerControlsBottomInset,
                    ),
            ) {
                TrackRow(
                    state = state,
                    lyricDisplaySettings = lyricDisplaySettings,
                    showAudioTechnicalInfo = playerInteractionSettings.showAudioTechnicalInfo,
                    liked = liked,
                    onLikedChange = onLikedChange,
                    onAction = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = lyricsLineHorizontalPadding,
                            top = 4.dp,
                        ),
                )
                CompactLyricsSurface(
                    track = track,
                    lyricDisplaySettings = lyricDisplaySettings,
                    currentPositionMs = currentPositionMs,
                    isPlaying = state.controls.isPlaying && !isSeeking,
                    onLineClick = { onAction(NowPlayingAction.OpenLyrics) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 6.dp, bottom = 10.dp),
                    lineHorizontalPadding = lyricsLineHorizontalPadding,
                    isPortrait = isPortrait,
                )
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.offset(y = (-8).dp)) {
                        progressContent(track?.durationMs)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    CompactTransportPanel(
                        nowPlayingState = state,
                        onAction = onAction,
                        dense = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactLandscapeNowPlayingLayout(
    state: NowPlayingState,
    lyricDisplaySettings: LyricDisplaySettings,
    playerInteractionSettings: PlayerInteractionSettings,
    currentPositionMs: Long,
    isSeeking: Boolean,
    liked: Boolean,
    onLikedChange: (Boolean) -> Unit,
    composeChrome: Boolean,
    chromeAlpha: Float,
    progressContent: @Composable (Long?) -> Unit,
    onAction: (NowPlayingAction) -> Unit,
) {
    val track = state.currentTrack
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsHeightPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.controls.isPlaying, controlsVisible) {
        if (state.controls.isPlaying && controlsVisible) {
            delay(LandscapeControlsAutoHideDelayMs)
            controlsVisible = false
        }
    }

    val toggleControls = {
        controlsVisible = !controlsVisible
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val artworkColumnWidth = maxOf(maxWidth * 0.47f, 413.dp).coerceAtMost(maxWidth * 0.56f)
        val stageHeight = minOf(
            (maxHeight * 0.82f).coerceAtLeast(300.dp),
            maxHeight - 34.dp,
            340.dp,
        )

        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(artworkColumnWidth)
                    .height(stageHeight)
                    .align(Alignment.CenterVertically)
                    .padding(start = 40.dp, end = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                CoverImage(
                    artwork = track?.artwork,
                    modifier = Modifier.size(stageHeight),
                    maxArtworkSize = 340.dp,
                    cornerRadius = 18.dp,
                    shadowOffsetY = 16.dp,
                    shadowBlurRadius = 42.dp,
                    borderWidth = 1.dp,
                    borderColor = Color.White.copy(alpha = 0.10f),
                    swipeEnabled = playerInteractionSettings.coverSwipeEnabled,
                    onSwipePrevious = {
                        if (state.queue.canPlayPrevious) onAction(NowPlayingAction.PlayPrevious)
                    },
                    onSwipeNext = {
                        if (state.queue.canPlayNext) onAction(NowPlayingAction.PlayNext)
                    },
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(stageHeight)
                    .align(Alignment.CenterVertically)
                    .padding(start = 8.dp, end = 40.dp),
            ) {
                if (composeChrome) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = chromeAlpha },
                    ) {
                        TrackRow(
                            state = state,
                            lyricDisplaySettings = lyricDisplaySettings,
                            showAudioTechnicalInfo = playerInteractionSettings.showAudioTechnicalInfo,
                            liked = liked,
                            onLikedChange = onLikedChange,
                            onAction = onAction,
                            dense = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) {
                            CompactLyricsSurface(
                                track = track,
                                lyricDisplaySettings = lyricDisplaySettings,
                                currentPositionMs = currentPositionMs,
                                isPlaying = state.controls.isPlaying && !isSeeking,
                                onLineClick = toggleControls,
                                onSurfaceClick = toggleControls,
                                dense = true,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 8.dp, bottom = 12.dp)
                                    .drawWithContent {
                                        val drawLyrics = { drawContent() }
                                        if (controlsVisible && controlsHeightPx > 0) {
                                            val controlsTop = (
                                                size.height - controlsHeightPx.toFloat() - 8.dp.toPx()
                                            ).coerceAtLeast(0f)
                                            clipRect(bottom = controlsTop) {
                                                drawLyrics()
                                            }
                                        } else {
                                            drawLyrics()
                                        }
                                    },
                            )
                            if (controlsVisible) {
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .onSizeChanged { controlsHeightPx = it.height },
                                ) {
                                    Box(modifier = Modifier.offset(y = (-8).dp)) {
                                        progressContent(track?.durationMs)
                                    }
                                    CompactTransportPanel(
                                        nowPlayingState = state,
                                        onAction = onAction,
                                        dense = true,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactNowPlayingLayout(
    state: NowPlayingState,
    lyricDisplaySettings: LyricDisplaySettings,
    playerInteractionSettings: PlayerInteractionSettings,
    currentPositionMs: Long,
    isSeeking: Boolean,
    progressContent: @Composable (Long?) -> Unit,
    compactProgressContent: @Composable (Long?) -> Unit,
    liked: Boolean,
    onLikedChange: (Boolean) -> Unit,
    onAction: (NowPlayingAction) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isShortLandscape = maxWidth >= 640.dp && maxWidth > maxHeight && maxHeight < 520.dp
        val isPortrait = maxHeight >= maxWidth
        if (isShortLandscape) {
            CompactLandscapeNowPlayingLayout(
                state = state,
                lyricDisplaySettings = lyricDisplaySettings,
                playerInteractionSettings = playerInteractionSettings,
                currentPositionMs = currentPositionMs,
                isSeeking = isSeeking,
                liked = liked,
                onLikedChange = onLikedChange,
                composeChrome = true,
                chromeAlpha = 1f,
                progressContent = compactProgressContent,
                onAction = onAction,
            )
        } else if (playerInteractionSettings.immersiveAlbumCoverEnabled) {
            CompactImmersiveNowPlayingLayout(
                state = state,
                lyricDisplaySettings = lyricDisplaySettings,
                playerInteractionSettings = playerInteractionSettings,
                currentPositionMs = currentPositionMs,
                isSeeking = isSeeking,
                liked = liked,
                onLikedChange = onLikedChange,
                progressContent = progressContent,
                onAction = onAction,
                isPortrait = isPortrait,
            )
        } else {
            CompactClassicNowPlayingLayout(
                state = state,
                lyricDisplaySettings = lyricDisplaySettings,
                playerInteractionSettings = playerInteractionSettings,
                currentPositionMs = currentPositionMs,
                isSeeking = isSeeking,
                liked = liked,
                onLikedChange = onLikedChange,
                composeChrome = true,
                chromeAlpha = 1f,
                progressContent = progressContent,
                onAction = onAction,
                isPortrait = isPortrait,
            )
        }
    }
}

@Composable
fun NowPlayingScreen(
    state: NowPlayingState,
    lyricDisplaySettings: LyricDisplaySettings = LyricDisplaySettings.Default,
    playerInteractionSettings: PlayerInteractionSettings = PlayerInteractionSettings.Default,
    currentPositionMs: Long,
    isSeeking: Boolean = false,
    progressContent: @Composable (Long?) -> Unit,
    compactProgressContent: @Composable (Long?) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    drawBackground: Boolean = true,
    audioReactiveSnapshot: StateFlow<AudioReactiveSnapshot> = ZeroAudioReactiveSnapshot,
    onAction: (NowPlayingAction) -> Unit,
    onStatusBarCoverageChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val currentTrack = state.currentTrack
    val titleBarInset = LocalDesktopTitleBarInset.current
    val density = LocalDensity.current
    val dragAnimationScope = rememberCoroutineScope()
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var dragAnimationJob by remember { mutableStateOf<Job?>(null) }
    var playerTopInWindowPx by remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }
    val statusBarBottomInWindowPx = WindowInsets.statusBars.getTop(density).toFloat()
    val coversStatusBar = doesPlayerCoverStatusBar(
        playerTopInWindowPx = playerTopInWindowPx,
        dragOffsetPx = dragOffsetPx,
        statusBarBottomInWindowPx = statusBarBottomInWindowPx,
    )

    LaunchedEffect(coversStatusBar) {
        onStatusBarCoverageChanged(coversStatusBar)
    }

    BoxWithConstraints(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                playerTopInWindowPx = coordinates.positionInWindow().y
            }
            .clipToBounds()
            .fillMaxSize(),
    ) {
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val dismissDistanceThresholdPx = with(density) {
            NowPlayingDismissDistanceThreshold.toPx()
        }
        val dismissVelocityPxPerSecond = with(density) { NowPlayingDismissVelocityThreshold.toPx() }
        val usesCompactLayout = maxWidth < DesktopPlayerBreakpoint || maxHeight < 520.dp
        val dismissGestureModifier = if (usesCompactLayout) {
            Modifier.pointerInput(
                viewportHeightPx,
                dismissDistanceThresholdPx,
                dismissVelocityPxPerSecond,
            ) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    val velocityTracker = VelocityTracker().apply {
                        addPosition(down.uptimeMillis, down.position)
                    }
                    var accumulatedX = 0f
                    var accumulatedY = 0f
                    var dismissDragStarted = false
                    var pointerPressed = true

                    while (pointerPressed) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val delta = change.positionChange()
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        pointerPressed = change.pressed

                        if (!dismissDragStarted) {
                            accumulatedX += delta.x
                            accumulatedY += delta.y
                            if (
                                accumulatedY > viewConfiguration.touchSlop &&
                                accumulatedY > kotlin.math.abs(accumulatedX)
                            ) {
                                dismissDragStarted = true
                                dragAnimationJob?.cancel()
                                dragOffsetPx = (dragOffsetPx + accumulatedY)
                                    .coerceIn(0f, viewportHeightPx)
                                change.consume()
                            }
                        } else {
                            val dragDeltaY = if (delta.y > 0f) delta.y else delta.y * 0.36f
                            dragOffsetPx = (dragOffsetPx + dragDeltaY)
                                .coerceIn(0f, viewportHeightPx)
                            change.consume()
                        }
                    }

                    if (dismissDragStarted) {
                        val velocityPxPerSecond = velocityTracker.calculateVelocity().y
                        val shouldDismiss = shouldDismissNowPlayingScreen(
                            dragOffsetPx = dragOffsetPx,
                            dismissThresholdPx = dismissDistanceThresholdPx,
                            velocityPxPerSecond = velocityPxPerSecond,
                            velocityThresholdPxPerSecond = dismissVelocityPxPerSecond,
                        )
                        dragAnimationJob?.cancel()
                        dragAnimationJob = dragAnimationScope.launch {
                            animate(
                                initialValue = dragOffsetPx,
                                targetValue = if (shouldDismiss) viewportHeightPx else 0f,
                                animationSpec = if (shouldDismiss) {
                                    tween(
                                        durationMillis = NowPlayingDismissSettleDurationMillis,
                                        easing = LinearOutSlowInEasing,
                                    )
                                } else {
                                    spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    )
                                },
                            ) { value, _ ->
                                dragOffsetPx = value
                            }
                            if (shouldDismiss) onAction(NowPlayingAction.NavigateBack)
                        }
                    }
                }
            }
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(x = 0, y = dragOffsetPx.roundToInt()) }
                .clipToBounds()
                .then(dismissGestureModifier),
        ) {
            if (drawBackground) {
                ImmersivePlayerBackground(
                    artwork = currentTrack?.artwork,
                    enabled = playerInteractionSettings.audioReactiveBackgroundEnabled,
                    audioReactiveSnapshot = audioReactiveSnapshot,
                )
            }
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = titleBarInset),
            ) {
                if (maxWidth >= DesktopPlayerBreakpoint && maxHeight >= 520.dp) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                    ) {
                        MusicPlayerHeader(onAction = onAction)
                        DesktopNowPlayingLayout(
                            state = state,
                            lyricDisplaySettings = lyricDisplaySettings,
                            playerInteractionSettings = playerInteractionSettings,
                            currentPositionMs = currentPositionMs,
                            isSeeking = isSeeking,
                            progressContent = progressContent,
                            liked = isFavorite,
                            onLikedChange = { onToggleFavorite() },
                            onAction = onAction,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    CompactNowPlayingLayout(
                        state = state,
                        lyricDisplaySettings = lyricDisplaySettings,
                        playerInteractionSettings = playerInteractionSettings,
                        currentPositionMs = currentPositionMs,
                        isSeeking = isSeeking,
                        progressContent = progressContent,
                        compactProgressContent = compactProgressContent,
                        liked = isFavorite,
                        onLikedChange = { onToggleFavorite() },
                        onAction = onAction,
                    )
                }
            }
        }
    }
}

internal fun shouldDismissNowPlayingScreen(
    dragOffsetPx: Float,
    dismissThresholdPx: Float,
    velocityPxPerSecond: Float,
    velocityThresholdPxPerSecond: Float,
): Boolean =
    dismissThresholdPx > 0f &&
        (
            dragOffsetPx >= dismissThresholdPx ||
                velocityPxPerSecond >= velocityThresholdPxPerSecond
        )

@Composable
fun ImmersivePlayerBackground(
    artwork: Artwork?,
    enabled: Boolean = false,
    audioReactiveSnapshot: StateFlow<AudioReactiveSnapshot> = ZeroAudioReactiveSnapshot,
) {
    val palette = rememberArtworkPalette(artwork)
    val topColor by animateColorAsState(
        targetValue = palette.darkMuted.copy(alpha = 1f),
        animationSpec = tween(durationMillis = 700),
        label = "playerBackgroundTopColor",
    )
    val middleColor by animateColorAsState(
        targetValue = palette.muted.copy(alpha = 1f),
        animationSpec = tween(durationMillis = 700),
        label = "playerBackgroundMiddleColor",
    )
    val accentColor by animateColorAsState(
        targetValue = palette.vibrant.copy(alpha = 1f),
        animationSpec = tween(durationMillis = 700),
        label = "playerBackgroundAccentColor",
    )
    if (enabled) {
        AudioReactiveBackground(
            artwork = artwork,
            audioReactiveSnapshot = audioReactiveSnapshot,
            topColor = topColor,
            middleColor = middleColor,
            accentColor = accentColor,
        )
    } else {
        StaticAudioReactiveBackground(
            artwork = artwork,
            topColor = topColor,
            middleColor = middleColor,
            accentColor = accentColor,
        )
    }
}

@Composable
private fun StaticAudioReactiveBackground(
    artwork: Artwork?,
    topColor: Color,
    middleColor: Color,
    accentColor: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(middleColor),
    ) {
        PlayerBackgroundArtworkImage(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = PlayerBackgroundBaseScale
                    scaleY = PlayerBackgroundBaseScale
                    alpha = PlayerBackgroundArtworkAlpha
                },
            artwork = artwork,
            blurRadius = PlayerBackgroundBlurRadius,
            contentScale = ContentScale.Crop,
            smoothTransition = true,
            fallback = {},
        )
        PlayerBackgroundOverlays(
            topColor = topColor,
            accentColor = accentColor,
        )
    }
}

@Composable
private fun AudioReactiveBackground(
    artwork: Artwork?,
    audioReactiveSnapshot: StateFlow<AudioReactiveSnapshot>,
    topColor: Color,
    middleColor: Color,
    accentColor: Color,
) {
    val snapshot by audioReactiveSnapshot.collectAsState()
    val levelTarget = sanitizeReactiveValue(snapshot.level)
    val beatTarget = sanitizeReactiveValue(snapshot.beat)
    val levelAnimation = remember { Animatable(0f) }
    val beatAnimation = remember { Animatable(0f) }
    LaunchedEffect(levelTarget) {
        levelAnimation.animateTo(
            targetValue = levelTarget,
            animationSpec = tween(
                durationMillis = resolveReactiveSmoothingDurationMillis(
                    currentValue = levelAnimation.value,
                    targetValue = levelTarget,
                    attackDurationMillis = ReactiveLevelAttackDurationMillis,
                    releaseDurationMillis = ReactiveLevelReleaseDurationMillis,
                ),
            ),
        )
    }
    LaunchedEffect(beatTarget) {
        beatAnimation.animateTo(
            targetValue = beatTarget,
            animationSpec = tween(
                durationMillis = resolveReactiveSmoothingDurationMillis(
                    currentValue = beatAnimation.value,
                    targetValue = beatTarget,
                    attackDurationMillis = ReactiveBeatAttackDurationMillis,
                    releaseDurationMillis = ReactiveBeatReleaseDurationMillis,
                ),
            ),
        )
    }
    val level = levelAnimation.value
    val beat = beatAnimation.value
    val phaseTransition = rememberInfiniteTransition(label = "audioReactiveBackgroundPhase")
    val phase by phaseTransition.animateFloat(
        initialValue = 0f,
        targetValue = ReactivePhaseRadians,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = ReactivePhaseCycleDurationMillis),
            repeatMode = AnimationRepeatMode.Restart,
        ),
        label = "audioReactiveBackgroundPhase",
    )
    val scale = resolveReactiveScale(
        baseScale = PlayerBackgroundBaseScale,
        level = level,
        beat = beat,
        enabled = true,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(middleColor),
    ) {
        PlayerBackgroundArtworkImage(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = PlayerBackgroundArtworkAlpha
                },
            artwork = artwork,
            blurRadius = PlayerBackgroundBlurRadius,
            contentScale = ContentScale.Crop,
            smoothTransition = true,
            fallback = {},
        )
        ReactiveBackgroundBlobs(
            phase = phase,
            level = level,
            beat = beat,
            vibrantColor = accentColor,
            mutedColor = middleColor,
            darkMutedColor = topColor,
        )
        PlayerBackgroundOverlays(
            topColor = topColor,
            accentColor = accentColor,
        )
    }
}

@Composable
private fun ReactiveBackgroundBlobs(
    phase: Float,
    level: Float,
    beat: Float,
    vibrantColor: Color,
    mutedColor: Color,
    darkMutedColor: Color,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val movement = phase
        val expansion = resolveReactiveBlobExpansion(level, beat)
        val width = size.width
        val height = size.height
        val maxDimension = maxOf(width, height)
        drawReactiveBlob(
            color = vibrantColor,
            center = Offset(
                x = width * (0.22f + 0.04f * sin(movement)),
                y = height * (0.28f + 0.03f * cos(movement)),
            ),
            radius = maxDimension * 0.62f * expansion,
            alpha = ReactiveVibrantBlobAlpha,
        )
        drawReactiveBlob(
            color = mutedColor,
            center = Offset(
                x = width * (0.78f + 0.04f * cos(movement + 2f)),
                y = height * (0.46f + 0.04f * sin(movement + 2f)),
            ),
            radius = maxDimension * 0.70f * expansion,
            alpha = ReactiveMutedBlobAlpha,
        )
        drawReactiveBlob(
            color = darkMutedColor,
            center = Offset(
                x = width * (0.46f + 0.03f * sin(movement + 4f)),
                y = height * (0.82f + 0.03f * cos(movement + 4f)),
            ),
            radius = maxDimension * 0.76f * expansion,
            alpha = ReactiveDarkMutedBlobAlpha,
        )
    }
}

private fun DrawScope.drawReactiveBlob(
    color: Color,
    center: Offset,
    radius: Float,
    alpha: Float,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = alpha),
                color.copy(alpha = 0f),
            ),
            center = center,
            radius = radius,
        ),
        center = center,
        radius = radius,
    )
}

@Composable
private fun PlayerBackgroundOverlays(
    topColor: Color,
    accentColor: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.28f),
                        topColor.copy(alpha = 0.42f),
                        Color.Black.copy(alpha = 0.34f),
                    ),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                ),
            ),
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Black.copy(alpha = 0.42f),
                        0.5f to Color.Black.copy(alpha = 0.18f),
                        1f to Color.Black.copy(alpha = 0.42f),
                    ),
                ),
            ),
    )
}

internal fun sanitizeReactiveValue(value: Float): Float =
    if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

internal fun resolveReactiveSmoothingDurationMillis(
    currentValue: Float,
    targetValue: Float,
    attackDurationMillis: Int,
    releaseDurationMillis: Int,
): Int = if (sanitizeReactiveValue(targetValue) > sanitizeReactiveValue(currentValue)) {
    attackDurationMillis
} else {
    releaseDurationMillis
}

internal fun resolveReactiveScale(
    baseScale: Float,
    level: Float,
    beat: Float,
    enabled: Boolean,
): Float {
    val safeBaseScale = if (baseScale.isFinite()) {
        baseScale.coerceIn(0f, ReactiveMaxBaseScale)
    } else {
        0f
    }
    if (!enabled) return safeBaseScale
    val multiplier = 1f +
        sanitizeReactiveValue(level) * ReactiveLevelScaleGain +
        sanitizeReactiveValue(beat) * ReactiveBeatScaleGain
    return (safeBaseScale * multiplier).coerceIn(
        0f,
        safeBaseScale * ReactiveMaxScaleMultiplier,
    )
}

internal fun resolveReactiveBlobExpansion(level: Float, beat: Float): Float =
    (
        1f +
            sanitizeReactiveValue(level) * ReactiveLevelBlobExpansion +
            sanitizeReactiveValue(beat) * ReactiveBeatBlobExpansion
        ).coerceIn(1f, ReactiveMaxBlobExpansion)

private const val PlayerBackgroundBaseScale = 2.90f
private val PlayerBackgroundBlurRadius = 48.dp
private const val PlayerBackgroundArtworkAlpha = 0.78f
private const val ReactiveLevelScaleGain = 0.014f
private const val ReactiveBeatScaleGain = 0.024f
private const val ReactiveMaxScaleMultiplier = 1.05f
private const val ReactiveMaxBaseScale = 100f
private const val ReactiveLevelBlobExpansion = 0.10f
private const val ReactiveBeatBlobExpansion = 0.14f
private const val ReactiveMaxBlobExpansion = 1.24f
private const val ReactiveVibrantBlobAlpha = 0.18f
private const val ReactiveMutedBlobAlpha = 0.14f
private const val ReactiveDarkMutedBlobAlpha = 0.12f
private const val ReactiveLevelAttackDurationMillis = 80
private const val ReactiveLevelReleaseDurationMillis = 420
private const val ReactiveBeatAttackDurationMillis = 60
private const val ReactiveBeatReleaseDurationMillis = 240
private const val ReactivePhaseCycleDurationMillis = 15_000
private const val ReactivePhaseRadians = (2f * kotlin.math.PI).toFloat()

@Composable
fun NowPlayingProgressPanel(
    progressState: NowPlayingProgressState,
    trackDurationMs: Long?,
    playerInteractionSettings: PlayerInteractionSettings = PlayerInteractionSettings.Default,
    onAction: (NowPlayingAction) -> Unit,
    lightTheme: Boolean = false,
    compact: Boolean = false,
    immersive: Boolean = false,
) {
    val totalDurationMs = trackDurationMs ?: progressState.playerDuration.inWholeMilliseconds

    MusicSlider(
        currentDuration = formatPlayerDuration(progressState.currentDuration),
        currentDurationMs = toMusicDurationMs(progressState.currentDuration),
        bufferDurationMs = progressState.bufferDuration.inWholeMilliseconds.coerceAtLeast(0).toULong(),
        totalDuration = formatPlayerDuration(totalDurationMs.milliseconds),
        totalDurationMs = totalDurationMs.coerceAtLeast(0).toULong(),
        tapToSeekEnabled = true,
        showTotalDuration = playerInteractionSettings.showTotalDuration,
        onChangeMusicPosition = { nextMs -> onAction(NowPlayingAction.SeekTo(nextMs)) },
        lightTheme = lightTheme,
        compact = compact,
        immersive = immersive,
    )
}

private fun formatPlayerDuration(duration: kotlin.time.Duration): String {
    val totalSeconds = duration.inWholeSeconds.coerceAtLeast(0)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
