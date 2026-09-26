package io.github.julystar.musicapp.service.playback.presentation.nowplaying

import androidx.compose.animation.core.animate
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode as AnimationRepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import io.github.julystar.musicapp.core.lyrics.ui.reference.LyricsMotionSpec
import io.github.julystar.musicapp.core.presentation.components.PlaybackControlButton
import io.github.julystar.musicapp.core.presentation.components.PlaybackControlSize
import io.github.julystar.musicapp.core.presentation.components.PlaybackControlVariant
import io.github.julystar.musicapp.core.presentation.components.PlaybackSlider
import io.github.julystar.musicapp.core.presentation.components.LiquidGlassOverlayScene
import io.github.julystar.musicapp.core.presentation.components.liquidGlassSurface
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
import io.github.julystar.musicapp.service.playback.presentation.miniplayer.DesktopAudioBadge
import io.github.julystar.musicapp.service.playback.presentation.miniplayer.desktopAudioBadge
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
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_dolby
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_favorite
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_favorite_outline
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_lossless
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
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_volume_high
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_volume_low
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_volume_medium
import musicapp.service.playback.presentation.generated.resources.icon_apple_music_volume_mute
import musicapp.service.playback.presentation.generated.resources.icon_back
import musicapp.service.playback.presentation.generated.resources.icon_now_playing_translation
import musicapp.service.playback.presentation.generated.resources.icon_now_playing_lyrics
import musicapp.service.playback.presentation.generated.resources.icon_now_playing_close
import musicapp.service.playback.presentation.generated.resources.icon_now_playing_mini
import musicapp.service.playback.presentation.generated.resources.icon_desktop_queue
import musicapp.service.playback.presentation.generated.resources.icon_heart_compact
import musicapp.service.playback.presentation.generated.resources.icon_heart_compact_filled
import musicapp.service.playback.presentation.generated.resources.icon_lyrics
import musicapp.service.playback.presentation.generated.resources.icon_more_compact
import musicapp.service.playback.presentation.generated.resources.icon_melox_autoplay
import musicapp.service.playback.presentation.generated.resources.icon_melox_favorite
import musicapp.service.playback.presentation.generated.resources.icon_melox_favorite_filled
import musicapp.service.playback.presentation.generated.resources.icon_melox_next
import musicapp.service.playback.presentation.generated.resources.icon_melox_pause
import musicapp.service.playback.presentation.generated.resources.icon_melox_play
import musicapp.service.playback.presentation.generated.resources.icon_melox_previous
import musicapp.service.playback.presentation.generated.resources.icon_melox_repeat
import musicapp.service.playback.presentation.generated.resources.icon_melox_repeat_one
import musicapp.service.playback.presentation.generated.resources.icon_melox_shuffle
import musicapp.service.playback.presentation.generated.resources.icon_melox_volume_high
import musicapp.service.playback.presentation.generated.resources.icon_melox_volume_low
import musicapp.service.playback.presentation.generated.resources.icon_melox_volume_medium
import musicapp.service.playback.presentation.generated.resources.icon_melox_volume_mute
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
import musicapp.service.playback.presentation.generated.resources.player_exit_now_playing
import musicapp.service.playback.presentation.generated.resources.player_dolby_audio
import musicapp.service.playback.presentation.generated.resources.player_loading_lyrics
import musicapp.service.playback.presentation.generated.resources.player_lossless_audio
import musicapp.service.playback.presentation.generated.resources.player_lyrics
import musicapp.service.playback.presentation.generated.resources.player_translation
import musicapp.service.playback.presentation.generated.resources.player_lyrics_unavailable
import musicapp.service.playback.presentation.generated.resources.player_more_options
import musicapp.service.playback.presentation.generated.resources.player_mute
import musicapp.service.playback.presentation.generated.resources.player_open_mini_player
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
import musicapp.service.playback.presentation.generated.resources.player_queue_automix
import musicapp.service.playback.presentation.generated.resources.player_queue_autoplay
import musicapp.service.playback.presentation.generated.resources.player_queue_clear
import musicapp.service.playback.presentation.generated.resources.player_queue_continue
import musicapp.service.playback.presentation.generated.resources.player_queue_history
import musicapp.service.playback.presentation.generated.resources.player_remove_favorite
import musicapp.service.playback.presentation.generated.resources.player_list_repeat
import musicapp.service.playback.presentation.generated.resources.player_shuffle
import musicapp.service.playback.presentation.generated.resources.player_single_repeat
import musicapp.service.playback.presentation.generated.resources.player_unknown_artist
import musicapp.service.playback.presentation.generated.resources.player_unmute
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val DesktopPlayerBreakpoint = 860.dp
private val MeloXDesktopChromeHeight = 48.dp
private val MeloXDesktopLyricsTopPadding = 40.dp
private val MeloXDesktopPanelTrailingInset = 32.dp
private val MeloXDesktopPlayerBaseWidth = 320.dp
private val MeloXDesktopPlayerExpandedWidth = 422.dp
private val MeloXDesktopArtworkBaseSize = 214.dp
private val MeloXDesktopArtworkExpandedSize = 308.dp
private val MeloXDesktopArtworkPausedScale = 0.74f
private val MeloXDesktopPlayerBaseArtworkTopInset = 56.dp
private val MeloXDesktopPlayerExpandedArtworkTopInset = 116.dp
private val AppleMusicDesktopLyricsMotion = LyricsMotionSpec()
private val NowPlayingDismissDistanceThreshold = 240.dp
private val NowPlayingDismissVelocityThreshold = 1_250.dp
private const val NowPlayingDismissSettleDurationMillis = 260
private const val LandscapeControlsAutoHideDelayMs = 5_000L
private val ZeroAudioReactiveSnapshot = MutableStateFlow(AudioReactiveSnapshot())

private enum class MeloXDesktopNowPlayingPage {
    Artwork,
    Lyrics,
    Queue,
}

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
        IconButton(
            onClick = { onAction(NowPlayingAction.NavigateBack) },
        ) { Icon(painterResource(Res.drawable.icon_back), contentDescription = null) }
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
    compactButtonSize: Dp = 44.dp,
    compactIconSize: Dp = 24.dp,
    compactBackgroundAlpha: Float = 0f,
    modifier: Modifier = Modifier,
) {
    var moreMenuExpanded by remember { mutableStateOf(false) }
    var sourceDialogOpen by remember { mutableStateOf(false) }
    val menuContentColor = MiuixTheme.colorScheme.onSurfaceContainer

    Box(modifier = modifier) {
        if (compact) {
            Box(
                modifier = Modifier
                    .size(compactButtonSize)
                    .clip(CircleShape)
                    .appleMusicDesktopControlBackground(compactBackgroundAlpha)
                    .clickable { moreMenuExpanded = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.icon_apple_music_more),
                    contentDescription = stringResource(Res.string.player_more_options),
                    tint = Color.White,
                    modifier = Modifier.size(compactIconSize),
                )
            }
        } else {
            IconButton(
                onClick = { moreMenuExpanded = true },
            ) {
                Icon(
                    painterResource(Res.drawable.icon_vertialcal_more),
                    stringResource(Res.string.player_more_options),
                )
            }
        }
        Box(
            contentAlignment = Alignment.TopEnd,
            modifier = Modifier.offset(20.dp, 20.dp),
        ) {
            OverlayDropdownPopup(
                DropdownEntry(
                    items = listOfNotNull(
                        DropdownItem(
                            text = stringResource(Res.string.music_player_search_metadata),
                            icon = { modifier ->
                                Icon(
                                    painter = painterResource(CoreRes.drawable.icon_search),
                                    contentDescription = null,
                                    modifier = modifier,
                                    tint = menuContentColor,
                                )
                            },
                            onClick = {
                                moreMenuExpanded = false
                                onAction(NowPlayingAction.SearchMetadata)
                            },
                        ),
                        if (hasLyric) {
                            DropdownItem(
                                text = stringResource(Res.string.music_lyric_remove),
                                icon = { modifier ->
                                    Icon(
                                        painter = painterResource(CoreRes.drawable.icon_deleteseep),
                                        contentDescription = null,
                                        modifier = modifier,
                                        tint = menuContentColor,
                                    )
                                },
                                onClick = {
                                    moreMenuExpanded = false
                                    onAction(NowPlayingAction.RemoveLyric)
                                },
                            )
                        } else {
                            DropdownItem(
                                text = stringResource(Res.string.music_lyric_add),
                                icon = { modifier ->
                                    Icon(
                                        painter = painterResource(Res.drawable.icon_lyrics),
                                        contentDescription = null,
                                        modifier = modifier,
                                        tint = menuContentColor,
                                    )
                                },
                                onClick = {
                                    moreMenuExpanded = false
                                    onAction(NowPlayingAction.AddLyric)
                                },
                            )
                        },
                        if (nowPlayingState.currentTrack?.canDownload == true) {
                            DropdownItem(
                                text = stringResource(Res.string.downloads_title),
                                icon = { modifier ->
                                    Icon(
                                        painter = painterResource(CoreRes.drawable.icon_download),
                                        contentDescription = null,
                                        modifier = modifier,
                                        tint = menuContentColor,
                                    )
                                },
                                onClick = {
                                    moreMenuExpanded = false
                                    onAction(NowPlayingAction.DownloadCurrentTrack)
                                },
                            )
                        } else null,
                        if (nowPlayingState.playbackSources.size > 1) {
                            DropdownItem(
                                text = stringResource(Res.string.player_playback_source),
                                icon = { modifier ->
                                    Icon(
                                        painter = painterResource(CoreRes.drawable.icon_settings_sliders),
                                        contentDescription = null,
                                        modifier = modifier,
                                        tint = menuContentColor,
                                    )
                                },
                                onClick = {
                                    moreMenuExpanded = false
                                    sourceDialogOpen = true
                                },
                            )
                        } else null,
                        DropdownItem(
                            text = stringResource(Res.string.music_player_context_menu_remove),
                            icon = { modifier ->
                                Icon(
                                    painter = painterResource(CoreRes.drawable.icon_deleteseep),
                                    contentDescription = null,
                                    modifier = modifier,
                                    tint = menuContentColor,
                                )
                            },
                            onClick = {
                                moreMenuExpanded = false
                                onAction(NowPlayingAction.RemoveCurrentTrack)
                            },
                        ),
                    ),
                ),
                show = moreMenuExpanded,
                onDismiss = { moreMenuExpanded = false },
                onDismissFinished = {},
                maxHeight = if (compact) 360.dp else null,
                dropdownColors = DropdownDefaults.dropdownColors(),
                renderInRootScaffold = true,
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
    OverlayDialog(show = show, onDismissRequest = onDismiss) {
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

                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(DesignTokens.spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                text = stringResource(Res.string.player_playback_source_cancel),
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
        PlaybackSlider(
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "appleMusicTransportPressScale",
    )
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
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
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
    desktopVolume: Float,
    onDesktopVolumeChange: (Float) -> Unit,
    liked: Boolean,
    onLikedChange: (Boolean) -> Unit,
    onAction: (NowPlayingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.currentTrack
    var page by remember { mutableStateOf(MeloXDesktopNowPlayingPage.Lyrics) }
    var translationVisible by remember { mutableStateOf(lyricDisplaySettings.showTranslation) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val heightProgress = ((maxHeight.value - 600f) / 168f).coerceIn(0f, 1f)
        val heightCompression = (600f - maxHeight.value).coerceIn(0f, 50f)
        val elementScale = minOf(
            (maxHeight.value / 768f).coerceAtLeast(1f),
            (maxWidth.value / 1200f).coerceAtLeast(1f),
        )
        val playerWidth = (MeloXDesktopPlayerBaseWidth.value +
            (MeloXDesktopPlayerExpandedWidth.value - MeloXDesktopPlayerBaseWidth.value) * heightProgress).dp * elementScale
        val artworkSize = (MeloXDesktopArtworkBaseSize.value +
            (MeloXDesktopArtworkExpandedSize.value - MeloXDesktopArtworkBaseSize.value) * heightProgress).dp * elementScale
        val artworkTopInset = (MeloXDesktopPlayerBaseArtworkTopInset.value - heightCompression +
            (MeloXDesktopPlayerExpandedArtworkTopInset.value - MeloXDesktopPlayerBaseArtworkTopInset.value) * heightProgress).dp * elementScale
        val metadataTopInset = (68f + 10f * heightProgress).dp * elementScale
        val panelLeading = maxWidth / 2f
        val panelWidth = (maxWidth - panelLeading - MeloXDesktopPanelTrailingInset).coerceAtLeast(1.dp)
        val lyricsPanelWidth = minOf(panelWidth, 400.dp * elementScale)
        val lyricsPanelHeight = (maxHeight - MeloXDesktopChromeHeight - MeloXDesktopLyricsTopPadding).coerceAtLeast(1.dp)
        val playerX = if (page == MeloXDesktopNowPlayingPage.Artwork) {
            ((maxWidth - playerWidth) / 2f).coerceAtLeast(0.dp)
        } else {
            ((panelLeading - playerWidth) / 2f).coerceAtLeast(0.dp)
        }

        Box(modifier = Modifier.fillMaxSize()) {
            MeloXDesktopPlayerColumn(
                state = state,
                lyricDisplaySettings = lyricDisplaySettings,
                playerInteractionSettings = playerInteractionSettings,
                currentPositionMs = currentPositionMs,
                liked = liked,
                onLikedChange = onLikedChange,
                onAction = onAction,
                artworkSize = artworkSize,
                artworkTopInset = artworkTopInset,
                metadataTopInset = metadataTopInset,
                elementScale = elementScale,
                modifier = Modifier
                    .offset(x = playerX, y = MeloXDesktopChromeHeight)
                    .width(playerWidth)
                    .fillMaxHeight(),
            )

            if (page == MeloXDesktopNowPlayingPage.Lyrics) {
                LyricsSurface(
                    track = track,
                    lyricDisplaySettings = lyricDisplaySettings,
                    currentPositionMs = currentPositionMs,
                    isPlaying = state.controls.isPlaying && !isSeeking,
                    showTranslation = translationVisible,
                    // Apple Music keeps the active lyric near the upper part of the lyrics
                    // viewport. The translation follows below it instead of moving the
                    // primary line to the visual center of the whole bilingual row.
                    focusLineCenterY = 85.dp * elementScale,
                    onAction = onAction,
                    modifier = Modifier
                        .offset(x = panelLeading, y = MeloXDesktopChromeHeight + MeloXDesktopLyricsTopPadding)
                        .width(lyricsPanelWidth)
                        .height(lyricsPanelHeight),
                )
            }

            if (page == MeloXDesktopNowPlayingPage.Queue) {
                MeloXDesktopQueuePanel(
                    queue = state.queue,
                    isPlaying = state.controls.isPlaying,
                    onAction = onAction,
                    modifier = Modifier
                        .offset(x = panelLeading, y = MeloXDesktopChromeHeight)
                        .width(panelWidth)
                        .fillMaxHeight(),
                )
            }

            MeloXDesktopPageSwitcher(
                page = page,
                translationVisible = translationVisible,
                hasTranslation = track?.lyrics?.lines?.toSyncedLyrics(
                    trackTitle = track.title,
                    trackDurationMs = track.durationMs,
                    settings = lyricDisplaySettings,
                )?.hasTranslation() == true,
                onTranslationClick = { translationVisible = !translationVisible },
                onLyricsClick = {
                    page = if (page == MeloXDesktopNowPlayingPage.Lyrics) {
                        MeloXDesktopNowPlayingPage.Artwork
                    } else {
                        MeloXDesktopNowPlayingPage.Lyrics
                    }
                },
                onQueueClick = {
                    page = if (page == MeloXDesktopNowPlayingPage.Queue) {
                        MeloXDesktopNowPlayingPage.Artwork
                    } else {
                        MeloXDesktopNowPlayingPage.Queue
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 9.dp, bottom = 11.dp),
            )
            MeloXDesktopTitlebar(
                volume = desktopVolume,
                onVolumeChange = onDesktopVolumeChange,
                onExit = { onAction(NowPlayingAction.NavigateBack) },
                onOpenMiniPlayer = { onAction(NowPlayingAction.NavigateBack) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MeloXDesktopTitlebar(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onExit: () -> Unit,
    onOpenMiniPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var volumeBeforeMute by remember { mutableFloatStateOf(volume.coerceAtLeast(0.2f)) }
    val volumeIcon = when {
        volume <= 0.001f -> Res.drawable.icon_apple_music_volume_mute
        volume < 0.35f -> Res.drawable.icon_apple_music_volume_low
        volume < 0.7f -> Res.drawable.icon_apple_music_volume_medium
        else -> Res.drawable.icon_apple_music_volume_high
    }

    Box(modifier = modifier.height(44.dp)) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 101.dp, y = 8.dp)
                .size(width = 75.dp, height = 36.dp)
                .appleMusicDesktopCapsule(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.width(7.dp))
            MeloXDesktopTitlebarButton(
                painter = Res.drawable.icon_now_playing_close,
                contentDescription = stringResource(Res.string.player_exit_now_playing),
                iconSize = 18.dp,
                onClick = onExit,
            )
            Spacer(modifier = Modifier.width(9.dp))
            MeloXDesktopTitlebarButton(
                painter = Res.drawable.icon_now_playing_mini,
                contentDescription = stringResource(Res.string.player_open_mini_player),
                iconSize = 26.dp,
                onClick = onOpenMiniPlayer,
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 8.dp, top = 8.dp)
                .size(width = 180.dp, height = 36.dp)
                .appleMusicDesktopCapsule()
                .padding(start = 15.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaybackSlider(
                value = volume.coerceIn(0f, 1f),
                onValueChange = { value ->
                    if (value > 0.001f) volumeBeforeMute = value
                    onVolumeChange(value)
                },
                modifier = Modifier.width(114.dp),
                height = 22.dp,
                trackHeight = 6.dp,
                thumbSize = 13.dp,
                thumbWidth = 24.dp,
                thumbTravelInset = 12.dp,
                activeThumbSize = 13.dp,
                trackColorOverride = Color.White.copy(alpha = 0.28f),
                activeTrackColorOverride = Color.White,
                thumbColorOverride = Color.White,
            )
            Spacer(modifier = Modifier.width(9.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable {
                        if (volume <= 0.001f) {
                            onVolumeChange(volumeBeforeMute.coerceAtLeast(0.2f))
                        } else {
                            volumeBeforeMute = volume
                            onVolumeChange(0f)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(volumeIcon),
                    contentDescription = stringResource(
                        if (volume <= 0.001f) Res.string.player_unmute else Res.string.player_mute,
                    ),
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

@Composable
private fun Modifier.appleMusicDesktopCapsule(): Modifier {
    val focused = LocalWindowInfo.current.isWindowFocused
    val surfaceAlpha by animateFloatAsState(
        targetValue = if (focused) 0.10f else 0.035f,
        animationSpec = tween(180),
        label = "now-playing-capsule-focus",
    )
    val rimAlpha by animateFloatAsState(
        targetValue = if (focused) 0.16f else 0f,
        animationSpec = tween(180),
        label = "now-playing-capsule-rim",
    )
    val shape = RoundedCornerShape(18.dp)
    // All three capsules share the local backdrop tint and the focused window's thin rim.
    return clip(shape)
        .background(Color.White.copy(alpha = surfaceAlpha))
        .border(
            width = 0.75.dp,
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = rimAlpha),
                0.5f to Color.White.copy(alpha = rimAlpha * 0.35f),
                1f to Color.White.copy(alpha = rimAlpha),
            ),
            shape = shape,
        )
}

@Composable
private fun MeloXDesktopTitlebarButton(
    painter: DrawableResource,
    contentDescription: String,
    iconSize: Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(painter),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.requiredSize(iconSize),
        )
    }
}

@Composable
private fun MeloXDesktopPlayerColumn(
    state: NowPlayingState,
    lyricDisplaySettings: LyricDisplaySettings,
    playerInteractionSettings: PlayerInteractionSettings,
    currentPositionMs: Long,
    liked: Boolean,
    onLikedChange: (Boolean) -> Unit,
    onAction: (NowPlayingAction) -> Unit,
    artworkSize: Dp,
    artworkTopInset: Dp,
    metadataTopInset: Dp,
    elementScale: Float,
    modifier: Modifier = Modifier,
) {
    val track = state.currentTrack
    val artworkScale by animateFloatAsState(
        targetValue = if (state.controls.isPlaying) 1f / MeloXDesktopArtworkPausedScale else 1f,
        animationSpec = tween(durationMillis = 480),
        label = "melox-desktop-now-playing-artwork-scale",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoverImage(
            artwork = track?.artwork,
            maxArtworkSize = artworkSize,
            cornerRadius = 11.dp,
            shadowOffsetY = if (state.controls.isPlaying) 15.dp else 8.dp,
            shadowBlurRadius = if (state.controls.isPlaying) 26.dp else 14.dp,
            swipeEnabled = playerInteractionSettings.coverSwipeEnabled,
            onSwipePrevious = {
                if (state.queue.canPlayPrevious) onAction(NowPlayingAction.PlayPrevious)
            },
            onSwipeNext = {
                if (state.queue.canPlayNext) onAction(NowPlayingAction.PlayNext)
            },
            modifier = Modifier
                .padding(top = artworkTopInset)
                .size(artworkSize)
                .graphicsLayer {
                    scaleX = artworkScale
                    scaleY = artworkScale
                },
        )
        MeloXDesktopMetadataRow(
            state = state,
            lyricDisplaySettings = lyricDisplaySettings,
            elementScale = elementScale,
            liked = liked,
            onLikedChange = onLikedChange,
            onAction = onAction,
            modifier = Modifier.padding(top = metadataTopInset),
        )
        MeloXDesktopProgress(
            track = track,
            currentPositionMs = currentPositionMs,
            durationMs = track?.durationMs ?: 0L,
            elementScale = elementScale,
            onAction = onAction,
            modifier = Modifier.padding(top = 30.dp * elementScale),
        )
        MeloXDesktopTransportControls(
            state = state,
            elementScale = elementScale,
            onAction = onAction,
            modifier = Modifier.padding(top = 5.dp * elementScale),
        )
    }
}

@Composable
internal fun MeloXDesktopMetadataRow(
    state: NowPlayingState,
    lyricDisplaySettings: LyricDisplaySettings,
    elementScale: Float,
    liked: Boolean,
    onLikedChange: (Boolean) -> Unit,
    onAction: (NowPlayingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.currentTrack
    val artistText = track?.artist?.takeIf(String::isNotBlank)
        ?: stringResource(Res.string.player_unknown_artist)
    val subtitle = listOfNotNull(artistText, track?.album?.takeIf(String::isNotBlank))
        .joinToString(separator = " — ")
        .replace('\'', '’')
    val displayTitle = track?.title.orEmpty().replace('\'', '’')
    val appleMusicPrimaryText = Color(0xFFF5F5F7)
    val appleMusicSecondaryText = Color(0xFFB3B3B3)

    Row(
        modifier = modifier.fillMaxWidth().height(41.dp * elementScale),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).height(41.dp * elementScale)) {
            Text(
                text = displayTitle,
                color = appleMusicPrimaryText,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                style = TextStyle(
                    fontFamily = DesignFontFamilies.Sans,
                    fontSize = 16.sp * elementScale,
                    lineHeight = 20.sp * elementScale,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp * elementScale)
                    .paddingFromBaseline(top = 16.dp * elementScale)
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
                color = appleMusicSecondaryText,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                style = TextStyle(
                    fontFamily = DesignFontFamilies.Sans,
                    fontSize = 14.sp * elementScale,
                    lineHeight = 18.sp * elementScale,
                    fontWeight = FontWeight.Medium,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp * elementScale)
                    .height(18.dp * elementScale)
                    .paddingFromBaseline(top = 14.dp * elementScale)
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
        MeloXDesktopGlassAction(
            painter = if (liked) Res.drawable.icon_apple_music_favorite else Res.drawable.icon_apple_music_favorite_outline,
            contentDescription = stringResource(
                if (liked) Res.string.player_remove_favorite else Res.string.player_add_favorite,
            ),
            tint = if (liked) DesignPalette.FavoriteRed else Color.White,
            elementScale = elementScale,
            onClick = { onLikedChange(!liked) },
        )
        Spacer(modifier = Modifier.width(6.dp * elementScale))
        NowPlayingMoreButton(
            hasLyric = track?.hasLyric == true,
            nowPlayingState = state,
            onAction = onAction,
            compact = true,
            compactButtonSize = 28.dp * elementScale,
            compactIconSize = 25.dp * elementScale,
            compactBackgroundAlpha = AppleMusicDesktopControlFillAlpha,
            modifier = Modifier.offset(x = 1.dp * elementScale),
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
private fun MeloXDesktopGlassAction(
    painter: DrawableResource,
    contentDescription: String,
    tint: Color,
    elementScale: Float,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(28.dp * elementScale)
            .clip(CircleShape)
            .appleMusicDesktopControlBackground()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(painter),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(16.dp * elementScale),
        )
    }
}

@Composable
internal fun MeloXDesktopProgress(
    track: NowPlayingTrackItem?,
    currentPositionMs: Long,
    durationMs: Long,
    elementScale: Float,
    onAction: (NowPlayingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubbedPositionMs by remember { mutableStateOf(currentPositionMs) }
    val displayedPositionMs = if (scrubbing) scrubbedPositionMs else currentPositionMs
    val progressTrackHeight by animateDpAsState(
        targetValue = (if (scrubbing) 18.dp else 8.dp) * elementScale,
        animationSpec = tween(durationMillis = if (scrubbing) 320 else 400),
        label = "melox-desktop-now-playing-progress-height",
    )
    val totalDurationMs = durationMs.coerceAtLeast(1L)

    Column(modifier = modifier.fillMaxWidth()) {
        PlaybackSlider(
            value = displayedPositionMs.coerceIn(0L, totalDurationMs).toFloat(),
            onValueChange = { value ->
                scrubbedPositionMs = value.toLong().coerceIn(0L, totalDurationMs)
            },
            valueRange = 0f..totalDurationMs.toFloat(),
            modifier = Modifier.fillMaxWidth(),
            height = 18.dp * elementScale,
            trackHeight = progressTrackHeight,
            thumbSize = 0.dp,
            activeThumbSize = 0.dp,
            trackColorOverride = Color.White.copy(alpha = 0.22f),
            activeTrackColorOverride = Color.White,
            thumbColorOverride = Color.Transparent,
            onValueChangeStarted = {
                scrubbing = true
                scrubbedPositionMs = currentPositionMs.coerceIn(0L, totalDurationMs)
            },
            onValueChangeFinished = {
                onAction(NowPlayingAction.SeekTo(scrubbedPositionMs.toULong()))
                scrubbing = false
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 3.dp * elementScale)
                .height(18.dp * elementScale),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatPlayerDuration(displayedPositionMs.milliseconds),
                color = Color.White.copy(alpha = 0.82f),
                style = TextStyle(fontFamily = DesignFontFamilies.Mono, fontSize = 10.sp * elementScale),
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                track?.playbackAudioInfo?.desktopAudioBadge()?.let { badge ->
                    MeloXDesktopAudioBadge(badge = badge)
                }
            }
            Text(
                text = "-${formatPlayerDuration((totalDurationMs - displayedPositionMs).coerceAtLeast(0L).milliseconds)}",
                color = Color.White.copy(alpha = 0.82f),
                style = TextStyle(fontFamily = DesignFontFamilies.Mono, fontSize = 10.sp * elementScale),
            )
        }
    }
}

@Composable
private fun MeloXDesktopAudioBadge(badge: DesktopAudioBadge) {
    val painter = painterResource(
        when (badge) {
            DesktopAudioBadge.Lossless -> Res.drawable.icon_apple_music_lossless
            DesktopAudioBadge.Dolby -> Res.drawable.icon_apple_music_dolby
        },
    )
    Icon(
        painter = painter,
        contentDescription = stringResource(
            when (badge) {
                DesktopAudioBadge.Lossless -> Res.string.player_lossless_audio
                DesktopAudioBadge.Dolby -> Res.string.player_dolby_audio
            },
        ),
        tint = Color.White.copy(alpha = 0.70f),
        modifier = when (badge) {
            DesktopAudioBadge.Lossless -> Modifier.width(25.dp).height(18.dp)
            DesktopAudioBadge.Dolby -> Modifier.width(15.dp).height(14.dp)
        },
    )
}

@Composable
private fun MeloXDesktopTransportControls(
    state: NowPlayingState,
    elementScale: Float,
    onAction: (NowPlayingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val controls = state.controls
    val queue = state.queue
    val repeatPainter = if (controls.repeatMode == RepeatMode.One) {
        Res.drawable.icon_apple_music_repeat_one
    } else {
        Res.drawable.icon_apple_music_repeat
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp * elementScale),
    ) {
        AppleMusicDesktopTransportButton(
            painter = Res.drawable.icon_apple_music_shuffle,
            motion = AppleMusicDesktopTransportMotion.Shuffle,
            contentDescription = stringResource(Res.string.player_shuffle),
            tint = Color.White.copy(alpha = if (controls.shuffleEnabled) 0.90f else 0.58f),
            selected = controls.shuffleEnabled,
            iconSize = 40.dp * elementScale,
            buttonWidth = 28.dp * elementScale,
            onClick = { onAction(NowPlayingAction.ToggleShuffle) },
            modifier = Modifier.fillMaxHeight().offset(x = 1.dp * elementScale),
        )
        AppleMusicDesktopTransportButton(
            painter = Res.drawable.icon_apple_music_previous,
            motion = AppleMusicDesktopTransportMotion.Seek,
            seekDirection = -1,
            contentDescription = stringResource(Res.string.player_previous_track),
            tint = Color.White,
            iconSize = 52.dp * elementScale,
            buttonWidth = 36.dp * elementScale,
            enabled = queue.canPlayPrevious,
            onClick = { onAction(NowPlayingAction.PlayPrevious) },
            modifier = Modifier.fillMaxHeight().offset(x = 58.dp * elementScale),
        )
        AppleMusicDesktopTransportButton(
            painter = if (controls.isPlaying) Res.drawable.icon_apple_music_pause else Res.drawable.icon_apple_music_play,
            motion = AppleMusicDesktopTransportMotion.PlayPause,
            contentDescription = stringResource(
                if (controls.isPlaying) Res.string.player_pause else Res.string.player_play,
            ),
            tint = Color.White,
            iconSize = 43.dp * elementScale,
            buttonWidth = 48.dp * elementScale,
            enabled = controls.isPlaying || !controls.isLoading,
            onClick = {
                onAction(if (controls.isPlaying) NowPlayingAction.Pause else NowPlayingAction.Resume)
            },
            modifier = Modifier.fillMaxHeight().offset(x = 134.5.dp * elementScale),
        )
        AppleMusicDesktopTransportButton(
            painter = Res.drawable.icon_apple_music_next,
            motion = AppleMusicDesktopTransportMotion.Seek,
            seekDirection = 1,
            contentDescription = stringResource(Res.string.player_next_track),
            tint = Color.White,
            iconSize = 52.dp * elementScale,
            buttonWidth = 36.dp * elementScale,
            enabled = queue.canPlayNext,
            onClick = { onAction(NowPlayingAction.PlayNext) },
            modifier = Modifier.fillMaxHeight().offset(x = 227.dp * elementScale),
        )
        AppleMusicDesktopTransportButton(
            painter = repeatPainter,
            motion = AppleMusicDesktopTransportMotion.Repeat,
            contentDescription = stringResource(
                if (controls.repeatMode == RepeatMode.One) Res.string.player_single_repeat else Res.string.player_list_repeat,
            ),
            tint = Color.White.copy(alpha = if (controls.repeatMode == RepeatMode.Off) 0.58f else 0.90f),
            selected = controls.repeatMode != RepeatMode.Off,
            iconSize = 40.dp * elementScale,
            buttonWidth = 28.dp * elementScale,
            onClick = { onAction(NowPlayingAction.CycleRepeatMode) },
            modifier = Modifier.fillMaxHeight().offset(x = 290.dp * elementScale),
        )
    }
}

@Composable
private fun MeloXDesktopQueuePanel(
    queue: NowPlayingQueueState,
    isPlaying: Boolean,
    onAction: (NowPlayingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var autoplayEnabled by remember { mutableStateOf(false) }
    var autoMixEnabled by remember { mutableStateOf(false) }
    val history = queue.items.take(queue.currentIndex.coerceAtLeast(0))
    val upcomingStart = (queue.currentIndex + 1).coerceAtLeast(0)
    val upcoming = queue.items.drop(upcomingStart)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 0.dp)
                .padding(top = 38.dp)
                .height(38.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            MeloXDesktopQueueModeButton(
                selected = autoplayEnabled,
                contentDescription = stringResource(Res.string.player_queue_autoplay),
                onClick = { autoplayEnabled = !autoplayEnabled },
                modifier = Modifier.weight(1f),
            )
            MeloXDesktopQueueModeButton(
                selected = autoMixEnabled,
                contentDescription = stringResource(Res.string.player_queue_automix),
                onClick = { autoMixEnabled = !autoMixEnabled },
                modifier = Modifier.weight(1f),
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 64.dp),
        ) {
            if (history.isNotEmpty()) {
                item(key = "history-header") {
                    MeloXDesktopQueueHeader(stringResource(Res.string.player_queue_history))
                }
                items(history, key = { item -> "history-${item.index}" }) { item ->
                    MeloXDesktopQueueRow(
                        item = item,
                        isPlaying = isPlaying && item.index == queue.currentIndex,
                        onAction = onAction,
                    )
                }
            }

            item(key = "continue-header") {
                MeloXDesktopQueueHeader(stringResource(Res.string.player_queue_continue))
            }
            items(upcoming, key = { item -> "upcoming-${item.index}" }) { item ->
                MeloXDesktopQueueRow(
                    item = item,
                    isPlaying = isPlaying && item.index == queue.currentIndex,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun MeloXDesktopQueueModeButton(
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(19.dp))
            .background(
                if (selected) DesignPalette.FavoriteRed else Color.White.copy(alpha = 0.10f),
            )
            .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(19.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(Res.drawable.icon_melox_autoplay),
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = if (selected) 1f else 0.88f),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun MeloXDesktopQueueHeader(
    title: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Color.White,
            style = TextStyle(
                fontFamily = DesignFontFamilies.Sans,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(Res.string.player_queue_clear),
            color = DesignPalette.FavoriteRed,
            style = TextStyle(
                fontFamily = DesignFontFamilies.Sans,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun MeloXDesktopQueueRow(
    item: NowPlayingQueueItem,
    isPlaying: Boolean,
    onAction: (NowPlayingAction) -> Unit,
) {
    val titleColor = if (isPlaying) DesignPalette.FavoriteRed else Color.White
    val subtitle = listOfNotNull(item.artist, item.album).distinct().joinToString(" · ")

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAction(NowPlayingAction.PlayQueueItem(item.index)) }
                .padding(start = 15.dp, top = 7.dp, end = 9.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                item.artwork?.let { artwork ->
                    ArtworkImage(
                        artwork = artwork,
                        contentScale = ContentScale.Crop,
                        smoothTransition = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 11.dp),
            ) {
                Text(
                    text = item.title,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        fontFamily = DesignFontFamilies.Sans,
                        fontSize = 14.sp,
                        lineHeight = 17.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.60f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            fontFamily = DesignFontFamilies.Sans,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                        ),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Icon(
                painter = painterResource(Res.drawable.icon_more_compact),
                contentDescription = stringResource(Res.string.player_more_options),
                tint = Color.White.copy(alpha = 0.68f),
                modifier = Modifier.size(28.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 15.dp)
                .height(0.5.dp)
                .background(Color.White.copy(alpha = 0.13f)),
        )
    }
}

@Composable
private fun MeloXDesktopPageSwitcher(
    page: MeloXDesktopNowPlayingPage,
    translationVisible: Boolean,
    hasTranslation: Boolean,
    onTranslationClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(width = 117.dp, height = 36.dp),
    ) {
        if (hasTranslation) {
            MeloXDesktopPageSwitchButton(
                painter = Res.drawable.icon_now_playing_translation,
                contentDescription = stringResource(Res.string.player_translation),
                selected = translationVisible,
                selectedSize = 36.dp,
                selectedCircleSize = 36.dp,
                iconSize = 28.dp,
                onClick = onTranslationClick,
                modifier = Modifier.align(Alignment.CenterStart).appleMusicDesktopCapsule(),
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(width = 72.dp, height = 36.dp)
                .appleMusicDesktopCapsule(),
        ) {
            MeloXDesktopPageSwitchButton(
                painter = Res.drawable.icon_now_playing_lyrics,
                contentDescription = stringResource(Res.string.player_lyrics),
                selected = page == MeloXDesktopNowPlayingPage.Lyrics,
                selectedSize = 36.dp,
                selectedCircleSize = 30.dp,
                iconSize = 25.dp,
                onClick = onLyricsClick,
            )
            MeloXDesktopPageSwitchButton(
                painter = Res.drawable.icon_apple_music_queue,
                contentDescription = stringResource(Res.string.player_queue),
                selected = page == MeloXDesktopNowPlayingPage.Queue,
                selectedSize = 36.dp,
                selectedCircleSize = 30.dp,
                iconSize = 31.dp,
                onClick = onQueueClick,
            )
        }
    }
}

@Composable
private fun MeloXDesktopPageSwitchButton(
    painter: DrawableResource,
    contentDescription: String,
    selected: Boolean,
    selectedSize: Dp,
    selectedCircleSize: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "appleMusicPageSwitchPressScale",
    )
    Box(
        modifier = modifier
            .size(selectedSize)
            .clip(CircleShape)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                scaleX = scale
                scaleY = scale
            }
            .semantics { this.selected = selected }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(selectedCircleSize)
                    .background(Color.White.copy(alpha = 0.83f), CircleShape),
            )
        }
        Icon(
            painter = painterResource(painter),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer {
                    // Selected glyphs reveal the live material instead of a fixed black tint.
                    blendMode = if (selected) BlendMode.DstOut else BlendMode.SrcOver
                },
        )
    }
}

@Composable
private fun LyricsSurface(
    track: NowPlayingTrackItem?,
    lyricDisplaySettings: LyricDisplaySettings,
    currentPositionMs: Long,
    isPlaying: Boolean,
    showTranslation: Boolean,
    focusLineCenterY: Dp? = null,
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
    val primaryFontSize = lyricDisplaySettings.primaryFontSizeSp * primaryScale * 0.85f
    val secondaryScale = lyricDisplaySettings.secondaryFontScalePercent / 100f
    val secondaryFontSize = lyricDisplaySettings.secondaryFontSizeSp * secondaryScale * 0.89f
    val lyricTextAlign = when (lyricDisplaySettings.textAlignment) {
        LyricTextAlignment.Left -> TextAlign.Start
        LyricTextAlignment.Center -> TextAlign.Center
        LyricTextAlignment.Right -> TextAlign.End
    }
    val lyricFontFamily = if (lyricDisplaySettings.font.applyToLyricsPage) {
        val containsCjk = lyricLines.any { line -> line.text.any(Char::isCjkCharacter) }
        val choice = if (containsCjk) lyricDisplaySettings.font.cjkFont else lyricDisplaySettings.font.westernFont
        if (choice == LyricFontChoice.AppSans) DesignFontFamilies.Sans else choice.toFontFamily()
    } else {
        DesignFontFamilies.Sans
    }
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
                        TextButton(
                            text = stringResource(Res.string.music_lyric_try_add_desc),
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
                    inactiveColor = Color.White.copy(alpha = 0.65f),
                    activeSecondaryColor = Color.White.copy(alpha = 0.50f),
                    inactiveSecondaryColor = Color.White.copy(alpha = 0.65f),
                    activeTextStyle = TextStyle(
                        fontFamily = lyricFontFamily,
                        fontSize = primaryFontSize.sp,
                        lineHeight = (primaryFontSize * 1.25f).sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    inactiveTextStyle = TextStyle(
                        fontFamily = lyricFontFamily,
                        fontSize = (primaryFontSize * 0.96f).sp,
                        lineHeight = (primaryFontSize * 1.08f).sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    secondaryTextStyle = TextStyle(
                        fontFamily = lyricFontFamily,
                        fontSize = secondaryFontSize.sp,
                        lineHeight = (secondaryFontSize * 1.28f).sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    textAlign = lyricTextAlign,
                    lineSpacing = 30.dp,
                    showTranslation = showTranslation,
                    wordLiftEnabled = lyricDisplaySettings.wordLiftEnabled,
                    useBlurEffect = true,
                    perspectiveEffectEnabled = lyricDisplaySettings.perspectiveEffectEnabled,
                    perspectiveAngleDegrees = lyricDisplaySettings.perspectiveAngleDegrees.toFloat(),
                    tapToSeekEnabled = lyricDisplaySettings.tapToSeekEnabled,
                    verticalContentPaddingFraction = 0.5f,
                    focusLineCenterY = focusLineCenterY,
                    focusLineContentAnchorFraction = 0f,
                    focusPrimaryText = true,
                    contextLinesBeforeActive = 0,
                    motionSpec = AppleMusicDesktopLyricsMotion,
                    blurAdjacentLines = true,
                    balancedLineWrap = false,
                    karaokeInactiveAlpha = 0.50f,
                    lineBlendMode = BlendMode.Plus,
                    topEdgeFadeHeight = 64.dp,
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
    accentColor: Color,
    audioReactiveBackgroundEnabled: Boolean,
    audioReactiveSnapshot: StateFlow<AudioReactiveSnapshot>,
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
            if (audioReactiveBackgroundEnabled) {
                ReactiveImmersiveOverlay(
                    accentColor = accentColor,
                    audioReactiveSnapshot = audioReactiveSnapshot,
                )
            }
        }
    }
}

internal fun compactArtworkTargetSize(isPlaying: Boolean): Dp =
    CompactArtworkExpandedSize * if (isPlaying) 1f else CompactArtworkPausedScale

private val CompactArtworkExpandedSize = 356.dp
private const val CompactArtworkPausedScale = 0.96f
private const val CompactPlayerContentWidthFraction = 0.88f
private val CompactPlayerLyricsLineHorizontalPadding = 8.dp
private val CompactPlayerTopInset = 90.dp
private val CompactPlayerControlsBottomInset = 44.dp

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
private fun CompactPlayerContentLayout(
    artworkContent: @Composable () -> Unit,
    playerContent: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = CompactPlayerTopInset,
                bottom = CompactPlayerControlsBottomInset,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(CompactPlayerContentWidthFraction)
                .widthIn(max = CompactArtworkExpandedSize),
        ) {
            artworkContent()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                playerContent()
            }
        }
    }
}

@Composable
private fun CompactPlayerContent(
    state: NowPlayingState,
    lyricDisplaySettings: LyricDisplaySettings,
    showAudioTechnicalInfo: Boolean,
    currentPositionMs: Long,
    isSeeking: Boolean,
    liked: Boolean,
    onLikedChange: (Boolean) -> Unit,
    progressContent: @Composable (Long?) -> Unit,
    onAction: (NowPlayingAction) -> Unit,
    isPortrait: Boolean,
    modifier: Modifier = Modifier,
) {
    val track = state.currentTrack

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(modifier),
    ) {
        TrackRow(
            state = state,
            lyricDisplaySettings = lyricDisplaySettings,
            showAudioTechnicalInfo = showAudioTechnicalInfo,
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

    CompactPlayerContentLayout(
        artworkContent = {
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
        },
        playerContent = {
            if (composeChrome) {
                CompactPlayerContent(
                    state = state,
                    lyricDisplaySettings = lyricDisplaySettings,
                    showAudioTechnicalInfo = playerInteractionSettings.showAudioTechnicalInfo,
                    currentPositionMs = currentPositionMs,
                    isSeeking = isSeeking,
                    liked = liked,
                    onLikedChange = onLikedChange,
                    progressContent = progressContent,
                    onAction = onAction,
                    isPortrait = isPortrait,
                    modifier = Modifier.graphicsLayer { alpha = chromeAlpha },
                )
            }
        },
    )
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
    audioReactiveSnapshot: StateFlow<AudioReactiveSnapshot>,
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
        Column(modifier = Modifier.fillMaxSize()) {
            ImmersiveArtworkArea(
                artwork = track?.artwork,
                surfaceColor = surfaceColor,
                accentColor = palette.vibrant,
                audioReactiveBackgroundEnabled = playerInteractionSettings.audioReactiveBackgroundEnabled,
                audioReactiveSnapshot = audioReactiveSnapshot,
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to surfaceColor,
                                    0.16f to surfaceColor.copy(alpha = 0.94f),
                                    1f to surfaceColor.copy(alpha = 0.90f),
                                ),
                            ),
                        ),
                )
                if (playerInteractionSettings.audioReactiveBackgroundEnabled) {
                    ReactiveImmersiveOverlay(
                        accentColor = palette.vibrant,
                        audioReactiveSnapshot = audioReactiveSnapshot,
                    )
                }
            }
        }
        CompactPlayerContentLayout(
            artworkContent = {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
            },
            playerContent = {
                CompactPlayerContent(
                    state = state,
                    lyricDisplaySettings = lyricDisplaySettings,
                    showAudioTechnicalInfo = playerInteractionSettings.showAudioTechnicalInfo,
                    currentPositionMs = currentPositionMs,
                    isSeeking = isSeeking,
                    liked = liked,
                    onLikedChange = onLikedChange,
                    progressContent = progressContent,
                    onAction = onAction,
                    isPortrait = isPortrait,
                )
            },
        )
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
    audioReactiveSnapshot: StateFlow<AudioReactiveSnapshot>,
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
                audioReactiveSnapshot = audioReactiveSnapshot,
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
    desktopVolume: Float = 1f,
    onDesktopVolumeChange: (Float) -> Unit = {},
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
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onAction(NowPlayingAction.NavigateBack)
                    true
                } else {
                    false
                }
            }
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

        LiquidGlassOverlayScene(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(x = 0, y = dragOffsetPx.roundToInt()) }
                .clipToBounds()
                .then(dismissGestureModifier),
            captureBackdrop = drawBackground && !usesCompactLayout,
            backdropContent = {
                if (drawBackground) {
                    ImmersivePlayerBackground(
                        artwork = currentTrack?.artwork,
                        enabled = playerInteractionSettings.audioReactiveBackgroundEnabled,
                        audioReactiveSnapshot = audioReactiveSnapshot,
                        meloxDesktopStyle = !usesCompactLayout,
                    )
                }
            },
            overlayContent = {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                if (maxWidth >= DesktopPlayerBreakpoint && maxHeight >= 520.dp) {
                    DesktopNowPlayingLayout(
                        state = state,
                        lyricDisplaySettings = lyricDisplaySettings,
                        playerInteractionSettings = playerInteractionSettings,
                        currentPositionMs = currentPositionMs,
                        isSeeking = isSeeking,
                        desktopVolume = desktopVolume,
                        onDesktopVolumeChange = onDesktopVolumeChange,
                        liked = isFavorite,
                        onLikedChange = { onToggleFavorite() },
                        onAction = onAction,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().padding(top = titleBarInset)) {
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
                            audioReactiveSnapshot = audioReactiveSnapshot,
                        )
                    }
                }
            }
            },
        )
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
    meloxDesktopStyle: Boolean = false,
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
    if (meloxDesktopStyle) {
        AppleMusicDesktopBackground(palette = palette)
    } else if (enabled) {
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
    val reactiveValues = audioReactiveValues(audioReactiveSnapshot)
    val level = reactiveValues.level
    val beat = reactiveValues.beat
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
        ReactiveBackgroundGlow(
            accentColor = accentColor,
            level = level,
            beat = beat,
        )
    }
}

private data class AudioReactiveValues(
    val level: Float,
    val beat: Float,
)

@Composable
private fun audioReactiveValues(
    audioReactiveSnapshot: StateFlow<AudioReactiveSnapshot>,
): AudioReactiveValues {
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
    return AudioReactiveValues(
        level = levelAnimation.value,
        beat = beatAnimation.value,
    )
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
private fun ReactiveBackgroundGlow(
    accentColor: Color,
    level: Float,
    beat: Float,
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            color = accentColor.copy(alpha = resolveReactiveGlowAlpha(level, beat)),
        )
        drawRect(
            color = Color.White.copy(alpha = resolveReactiveWhiteGlowAlpha(level, beat)),
        )
    }
}

@Composable
private fun ReactiveImmersiveOverlay(
    accentColor: Color,
    audioReactiveSnapshot: StateFlow<AudioReactiveSnapshot>,
) {
    val reactiveValues = audioReactiveValues(audioReactiveSnapshot)
    ReactiveBackgroundGlow(
        accentColor = accentColor,
        level = reactiveValues.level,
        beat = reactiveValues.beat,
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
                        accentColor.copy(alpha = 0.52f),
                        topColor.copy(alpha = 0.16f),
                        accentColor.copy(alpha = 0.24f),
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
                        0f to Color.Black.copy(alpha = 0.02f),
                        0.5f to Color.Black.copy(alpha = 0.06f),
                        1f to Color.Black.copy(alpha = 0.36f),
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

internal fun resolveReactiveGlowAlpha(level: Float, beat: Float): Float =
    (
        sanitizeReactiveValue(level) * ReactiveLevelGlowAlphaGain +
            sanitizeReactiveValue(beat) * ReactiveBeatGlowAlphaGain
        ).coerceIn(0f, ReactiveMaxGlowAlpha)

internal fun resolveReactiveWhiteGlowAlpha(level: Float, beat: Float): Float =
    (
        sanitizeReactiveValue(level) * ReactiveLevelWhiteGlowAlphaGain +
            sanitizeReactiveValue(beat) * ReactiveBeatWhiteGlowAlphaGain
        ).coerceIn(0f, ReactiveMaxWhiteGlowAlpha)

private const val PlayerBackgroundBaseScale = 2.90f
private val PlayerBackgroundBlurRadius = 48.dp
private const val PlayerBackgroundArtworkAlpha = 0.78f
private const val ReactiveLevelScaleGain = 0.020f
private const val ReactiveBeatScaleGain = 0.035f
private const val ReactiveMaxScaleMultiplier = 1.06f
private const val ReactiveMaxBaseScale = 100f
private const val ReactiveLevelBlobExpansion = 0.16f
private const val ReactiveBeatBlobExpansion = 0.24f
private const val ReactiveMaxBlobExpansion = 1.38f
private const val ReactiveVibrantBlobAlpha = 0.24f
private const val ReactiveMutedBlobAlpha = 0.19f
private const val ReactiveDarkMutedBlobAlpha = 0.15f
private const val ReactiveLevelGlowAlphaGain = 0.04f
private const val ReactiveBeatGlowAlphaGain = 0.08f
private const val ReactiveMaxGlowAlpha = 0.12f
private const val ReactiveLevelWhiteGlowAlphaGain = 0.012f
private const val ReactiveBeatWhiteGlowAlphaGain = 0.028f
private const val ReactiveMaxWhiteGlowAlpha = 0.04f
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
