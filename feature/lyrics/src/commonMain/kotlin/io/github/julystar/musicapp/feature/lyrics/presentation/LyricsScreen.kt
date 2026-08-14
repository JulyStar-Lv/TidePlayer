package io.github.julystar.musicapp.feature.lyrics.presentation

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.julystar.musicapp.core.domain.model.LyricDisplaySettings
import io.github.julystar.musicapp.core.domain.model.LyricFontChoice
import io.github.julystar.musicapp.core.domain.model.LyricTextAlignment
import io.github.julystar.musicapp.core.domain.model.LyricsLoadState
import io.github.julystar.musicapp.core.lyrics.ui.LyricsView
import io.github.julystar.musicapp.core.presentation.components.DesignContextMenu
import io.github.julystar.musicapp.core.presentation.components.DesignContextMenuItem
import io.github.julystar.musicapp.core.presentation.components.DesignIconButton
import io.github.julystar.musicapp.core.presentation.components.DesignIconButtonColors
import io.github.julystar.musicapp.core.presentation.components.DesignIconButtonSize
import io.github.julystar.musicapp.core.presentation.components.DesignIconButtonVariant
import io.github.julystar.musicapp.core.presentation.components.LocalDesignBottomContentInset
import io.github.julystar.musicapp.core.presentation.components.DesignTextButton
import io.github.julystar.musicapp.core.presentation.components.DesignTextButtonSize
import io.github.julystar.musicapp.core.presentation.components.DesignTextButtonVariant
import io.github.julystar.musicapp.core.presentation.media.ArtworkImage
import io.github.julystar.musicapp.core.presentation.overlay.resolve
import io.github.julystar.musicapp.core.presentation.theme.DesignFontFamilies
import io.github.julystar.musicapp.service.playback.presentation.nowplaying.NowPlayingAction
import io.github.julystar.musicapp.service.playback.presentation.nowplaying.ImmersivePlayerBackground
import io.github.julystar.musicapp.service.playback.presentation.nowplaying.NowPlayingTrackItem
import io.github.julystar.musicapp.service.playback.presentation.nowplaying.toSyncedLyrics
import io.github.julystar.musicapp.service.playback.presentation.transition.playerArtworkSharedElement
import io.github.julystar.musicapp.service.playback.presentation.transition.playerArtworkTransitionShape
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import musicapp.core.presentation.generated.resources.Res as CoreRes
import musicapp.core.presentation.generated.resources.icon_deleteseep
import musicapp.service.playback.presentation.generated.resources.Res
import musicapp.service.playback.presentation.generated.resources.icon_heart_compact
import musicapp.service.playback.presentation.generated.resources.icon_heart_compact_filled
import musicapp.feature.lyrics.generated.resources.Res as LyricsRes
import musicapp.feature.lyrics.generated.resources.lyrics_loading
import musicapp.feature.lyrics.generated.resources.lyrics_not_available
import musicapp.feature.lyrics.generated.resources.lyrics_retry
import musicapp.service.playback.presentation.generated.resources.icon_vertialcal_more
import musicapp.service.playback.presentation.generated.resources.music_lyric_remove
import musicapp.service.playback.presentation.generated.resources.player_add_favorite
import musicapp.service.playback.presentation.generated.resources.player_loading_lyrics
import musicapp.service.playback.presentation.generated.resources.player_remove_favorite
import musicapp.service.playback.presentation.generated.resources.player_unknown_artist
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val LyricsDismissDistanceFraction = 0.5f
private val LyricsDismissVelocityThreshold = 900.dp

@Composable
fun LyricsScreen(
    state: LyricsState,
    nowPlayingTrack: NowPlayingTrackItem?,
    currentPositionMs: Long,
    isPlaying: Boolean,
    lyricDisplaySettings: LyricDisplaySettings,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onAction: (LyricsAction) -> Unit,
    onPlayerAction: (NowPlayingAction) -> Unit,
) {
    val trackTitle = nowPlayingTrack?.title ?: state.trackTitle
    val trackArtist = nowPlayingTrack?.artist ?: state.trackArtist
    val artwork = nowPlayingTrack?.artwork
    val bottomContentInset = LocalDesignBottomContentInset.current
    val density = LocalDensity.current
    val dragAnimationScope = rememberCoroutineScope()
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var dragAnimationJob by remember { mutableStateOf<Job?>(null) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val dismissVelocityPxPerSecond = with(density) { LyricsDismissVelocityThreshold.toPx() }
        val headerDraggableState = rememberDraggableState { deltaPx ->
            dragOffsetPx = (dragOffsetPx + deltaPx).coerceIn(0f, viewportHeightPx)
        }

        ImmersivePlayerBackground(artwork = artwork)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = dragOffsetPx
                },
        ) {
            LyricsTrackHeader(
                track = nowPlayingTrack,
                title = trackTitle,
                artist = trackArtist,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onPlayerAction = onPlayerAction,
                modifier = Modifier.draggable(
                    state = headerDraggableState,
                    orientation = Orientation.Vertical,
                    onDragStarted = {
                        dragAnimationJob?.cancel()
                    },
                    onDragStopped = { velocityPxPerSecond ->
                        if (
                            shouldDismissLyricsScreen(
                                dragOffsetPx = dragOffsetPx,
                                viewportHeightPx = viewportHeightPx,
                                velocityPxPerSecond = velocityPxPerSecond,
                                velocityThresholdPxPerSecond = dismissVelocityPxPerSecond,
                            )
                        ) {
                            onAction(LyricsAction.NavigateBack)
                        } else {
                            dragAnimationJob?.cancel()
                            dragAnimationJob = dragAnimationScope.launch {
                                animate(
                                    initialValue = dragOffsetPx,
                                    targetValue = 0f,
                                    animationSpec = spring(),
                                ) { value, _ ->
                                    dragOffsetPx = value
                                }
                            }
                        }
                    },
                ),
            )

            if (nowPlayingTrack != null) {
                NowPlayingLyricsContent(
                    track = nowPlayingTrack,
                    currentPositionMs = currentPositionMs,
                    isPlaying = isPlaying,
                    lyricDisplaySettings = lyricDisplaySettings,
                    onPlayerAction = onPlayerAction,
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = bottomContentInset),
                )
            } else {
                StoredLyricsContent(
                    state = state,
                    onAction = onAction,
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = bottomContentInset),
                )
            }
        }
    }
}

@Composable
private fun LyricsTrackHeader(
    track: NowPlayingTrackItem?,
    title: String,
    artist: String?,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlayerAction: (NowPlayingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var moreMenuExpanded by remember { mutableStateOf(false) }
    val artworkShape = remember { playerArtworkTransitionShape() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(84.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .playerArtworkSharedElement()
                .size(52.dp)
                .clip(artworkShape),
        ) {
            ArtworkImage(
                artwork = track?.artwork,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MiuixTheme.textStyles.title3.copy(fontSize = 19.sp, lineHeight = 23.sp),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist ?: stringResource(Res.string.player_unknown_artist),
                color = Color.White.copy(alpha = 0.62f),
                style = MiuixTheme.textStyles.body2.copy(fontSize = 14.sp, lineHeight = 19.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (track != null) {
            DesignIconButton(
                size = DesignIconButtonSize.Touch,
                variant = DesignIconButtonVariant.Default,
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
                colors = lyricsHeaderButtonColors(
                    iconTint = if (isFavorite) MiuixTheme.colorScheme.primary else Color.White,
                ),
                onClick = onToggleFavorite,
            )
            Box {
                DesignIconButton(
                    size = DesignIconButtonSize.Touch,
                    variant = DesignIconButtonVariant.Default,
                    painter = painterResource(Res.drawable.icon_vertialcal_more),
                    contentDescription = null,
                    colors = lyricsHeaderButtonColors(),
                    onClick = { moreMenuExpanded = true },
                )
                DesignContextMenu(
                    expanded = moreMenuExpanded,
                    onDismissRequest = { moreMenuExpanded = false },
                    items = listOf(
                        DesignContextMenuItem(
                            label = Res.string.music_lyric_remove,
                            icon = CoreRes.drawable.icon_deleteseep,
                            onClick = {
                                moreMenuExpanded = false
                                onPlayerAction(NowPlayingAction.RemoveLyric)
                            },
                        ),
                    ),
                )
            }
        }
    }
}

private fun lyricsHeaderButtonColors(iconTint: Color = Color.White): DesignIconButtonColors =
    DesignIconButtonColors(
        buttonBg = Color.White.copy(alpha = 0.10f),
        iconTint = iconTint,
    )

internal fun shouldDismissLyricsScreen(
    dragOffsetPx: Float,
    viewportHeightPx: Float,
    velocityPxPerSecond: Float,
    velocityThresholdPxPerSecond: Float,
): Boolean =
    viewportHeightPx > 0f &&
        (
            dragOffsetPx >= viewportHeightPx * LyricsDismissDistanceFraction ||
                velocityPxPerSecond >= velocityThresholdPxPerSecond
        )

@Composable
private fun NowPlayingLyricsContent(
    track: NowPlayingTrackItem,
    currentPositionMs: Long,
    isPlaying: Boolean,
    lyricDisplaySettings: LyricDisplaySettings,
    onPlayerAction: (NowPlayingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val loadState = track.lyrics.loadState
    val lyricLines = track.lyrics.lines
    val syncedLyrics = remember(lyricLines, track.title, track.durationMs, lyricDisplaySettings) {
        lyricLines.toSyncedLyrics(
            trackTitle = track.title,
            trackDurationMs = track.durationMs,
            settings = lyricDisplaySettings,
        )
    }

    when {
        loadState == LyricsLoadState.Loading -> LyricsStatus(
            message = stringResource(Res.string.player_loading_lyrics),
            modifier = modifier,
        )
        loadState == LyricsLoadState.Missing || loadState == LyricsLoadState.Failed || syncedLyrics.lines.isEmpty() ->
            LyricsStatus(
                message = stringResource(LyricsRes.string.lyrics_not_available),
                modifier = modifier,
            )
        else -> {
            val primarySize = lyricDisplaySettings.primaryFontSizeSp *
                (lyricDisplaySettings.primaryFontScalePercent / 100f) * 0.875f
            val secondarySize = lyricDisplaySettings.secondaryFontSizeSp *
                (lyricDisplaySettings.secondaryFontScalePercent / 100f) * (14f / 19f)
            val textAlign = when (lyricDisplaySettings.textAlignment) {
                LyricTextAlignment.Left -> TextAlign.Start
                LyricTextAlignment.Center -> TextAlign.Center
                LyricTextAlignment.Right -> TextAlign.End
            }
            val fontFamily = lyricDisplaySettings.lyricPageFontFamily(lyricLines.map { it.text })

            LyricsView(
                lyrics = syncedLyrics,
                currentPositionMs = currentPositionMs.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
                isPlaying = isPlaying,
                onLineClick = { line ->
                    onPlayerAction(NowPlayingAction.SeekTo(line.start.coerceAtLeast(0).toULong()))
                },
                activeColor = Color.White,
                inactiveColor = Color.White.copy(alpha = 0.62f),
                activeTextStyle = TextStyle(
                    fontFamily = fontFamily,
                    fontSize = primarySize.sp,
                    lineHeight = (primarySize * 1.25f).sp,
                    fontWeight = FontWeight.Bold,
                ),
                inactiveTextStyle = TextStyle(
                    fontFamily = fontFamily,
                    fontSize = (primarySize * (23f / 28f)).sp,
                    lineHeight = (primarySize * 1.30f * (23f / 28f)).sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                secondaryTextStyle = TextStyle(
                    fontFamily = fontFamily,
                    fontSize = secondarySize.sp,
                    lineHeight = (secondarySize * 1.42f).sp,
                    fontWeight = FontWeight.Medium,
                ),
                textAlign = textAlign,
                lineSpacing = 22.dp,
                showTranslation = lyricDisplaySettings.showTranslation,
                wordLiftEnabled = lyricDisplaySettings.wordLiftEnabled,
                useBlurEffect = lyricDisplaySettings.blurEffectEnabled,
                perspectiveEffectEnabled = lyricDisplaySettings.perspectiveEffectEnabled,
                perspectiveAngleDegrees = lyricDisplaySettings.perspectiveAngleDegrees.toFloat(),
                tapToSeekEnabled = lyricDisplaySettings.tapToSeekEnabled,
                modifier = modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StoredLyricsContent(
    state: LyricsState,
    onAction: (LyricsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> LyricsStatus(stringResource(LyricsRes.string.lyrics_loading), modifier)
        state.error != null -> LyricsStatus(
            state.error.resolve(),
            modifier,
            onRetry = { onAction(LyricsAction.Retry) },
        )
        state.lines.isEmpty() -> LyricsStatus(
            stringResource(LyricsRes.string.lyrics_not_available),
            modifier,
        )
        else -> LazyColumn(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Spacer(Modifier.height(120.dp)) }
            items(state.lines) { line ->
                Text(
                    text = line,
                    color = Color.White.copy(alpha = 0.72f),
                    style = MiuixTheme.textStyles.title3.copy(fontSize = 23.sp, lineHeight = 30.sp),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 11.dp),
                )
            }
            item { Spacer(Modifier.height(120.dp)) }
        }
    }
}

@Composable
private fun LyricsStatus(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.62f),
            style = MiuixTheme.textStyles.body1,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(12.dp))
            DesignTextButton(
                text = stringResource(LyricsRes.string.lyrics_retry),
                variant = DesignTextButtonVariant.Primary,
                size = DesignTextButtonSize.Medium,
                onClick = onRetry,
            )
        }
    }
}

@Composable
private fun LyricDisplaySettings.lyricPageFontFamily(lines: List<String>): FontFamily? {
    if (!font.applyToLyricsPage) return null
    val containsCjk = lines.any { line -> line.any(Char::isCjkCharacter) }
    return when (if (containsCjk) font.cjkFont else font.westernFont) {
        LyricFontChoice.System -> FontFamily.Default
        LyricFontChoice.AppSans -> DesignFontFamilies.JakartaSans
        LyricFontChoice.AppCjk -> DesignFontFamilies.Sans
        LyricFontChoice.Monospace -> DesignFontFamilies.Mono
    }
}

private fun Char.isCjkCharacter(): Boolean = code in 0x2E80..0x9FFF ||
    code in 0xAC00..0xD7AF ||
    code in 0xF900..0xFAFF
