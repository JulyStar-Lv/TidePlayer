package io.github.julystar.musicapp.feature.home.presentation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.presentation.components.DesignPageHeader
import io.github.julystar.musicapp.core.presentation.components.DesignGlassScene
import io.github.julystar.musicapp.core.presentation.components.DesignStickyGlassActionBar
import io.github.julystar.musicapp.core.presentation.components.LocalDesignBottomContentInset
import io.github.julystar.musicapp.core.presentation.components.designLiquidGlass
import io.github.julystar.musicapp.core.presentation.media.ArtworkImage
import io.github.julystar.musicapp.core.presentation.theme.DesignPalette
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.julystar.musicapp.core.presentation.transition.albumArtworkSharedElement
import io.github.julystar.musicapp.core.presentation.transition.playlistArtworkSharedElement
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import musicapp.core.presentation.generated.resources.Res as CoreRes
import musicapp.core.presentation.generated.resources.icon_play_outline
import musicapp.feature.home.generated.resources.Res
import musicapp.feature.home.generated.resources.icon_activity
import musicapp.feature.home.generated.resources.icon_bookmark
import musicapp.feature.home.generated.resources.icon_clock
import musicapp.feature.home.generated.resources.icon_disc
import musicapp.feature.home.generated.resources.icon_headphones
import musicapp.feature.home.generated.resources.icon_mic_vocal
import musicapp.feature.home.generated.resources.icon_section_chevron
import musicapp.feature.home.generated.resources.icon_sparkles
import musicapp.feature.home.generated.resources.home_cover_1
import musicapp.feature.home.generated.resources.home_cover_2
import musicapp.feature.home.generated.resources.home_cover_3
import musicapp.feature.home.generated.resources.home_cover_4
import musicapp.feature.home.generated.resources.home_cover_5
import musicapp.feature.home.generated.resources.home_cover_6
import musicapp.feature.home.generated.resources.home_cover_7
import musicapp.feature.home.generated.resources.home_cover_8
import musicapp.feature.home.generated.resources.home_good_evening
import musicapp.feature.home.generated.resources.home_continue_playing
import musicapp.feature.home.generated.resources.home_daily_picks
import musicapp.feature.home.generated.resources.home_new_songs
import musicapp.feature.home.generated.resources.home_pinned_playlists
import musicapp.feature.home.generated.resources.home_play
import musicapp.feature.home.generated.resources.home_empty_message
import musicapp.feature.home.generated.resources.home_empty_title
import musicapp.feature.home.generated.resources.home_no_track
import musicapp.feature.home.generated.resources.home_now_playing_label
import musicapp.feature.home.generated.resources.home_add_source
import musicapp.feature.home.generated.resources.home_recommended_artists
import musicapp.feature.home.generated.resources.home_recently_played
import musicapp.feature.home.generated.resources.home_subtitle
import musicapp.feature.home.generated.resources.home_suggested_albums
import musicapp.feature.home.generated.resources.home_this_month
import musicapp.feature.home.generated.resources.home_top_tracks
import musicapp.feature.home.generated.resources.home_top_tracks_by_plays
import musicapp.feature.home.generated.resources.home_title
import musicapp.feature.home.generated.resources.home_your_listening
import musicapp.feature.home.generated.resources.listening_plays
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Production Compose mapping of Design/src/app/App.tsx Home page.
 * The section order and compact/desktop behavior intentionally mirror the Design bundle.
 */
@Composable
fun HomeDesignScreen(
    scaffoldPadding: PaddingValues,
    state: HomeState,
    currentMiniPlayerTitle: String?,
    onAction: (HomeAction) -> Unit,
) {
    val fallbackTrack = remember(state.dailyPickTracks, state.recentTracks) {
        state.dailyPickTracks.ifEmpty { state.recentTracks }.randomOrNull()
    }
    val showEmptyStateOnly = state.shouldShowEmptyStateOnly
    val dailyPicksTrackTitle = currentMiniPlayerTitle
        ?: fallbackTrack?.title
        ?: stringResource(Res.string.home_no_track)
    val bottomContentInset = LocalDesignBottomContentInset.current
    DesignGlassScene(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background),
        ) {
            val compact = maxWidth < DesignTokens.adaptive.largeMinWidth
            val pagePadding = if (compact) 24.dp else DesignTokens.spacing.pageExpanded
            val playlistCardWidth = if (compact) 160.dp else 178.dp
            val newSongCardWidth = 120.dp
            val suggestedAlbumCardWidth = 160.dp
            val artistSize = 128.dp
            val listState = rememberLazyListState()
            val collapseDistance = with(LocalDensity.current) {
                DesignTokens.adaptive.compactHeaderCollapseDistance.roundToPx()
            }
            val actionBarProgress by remember(listState, collapseDistance) {
                derivedStateOf {
                    if (listState.firstVisibleItemIndex > 0) {
                        1f
                    } else {
                        (listState.firstVisibleItemScrollOffset / collapseDistance.toFloat())
                            .coerceIn(0f, 1f)
                    }
                }
            }
            val pageTitleAlpha = (1f - actionBarProgress / 0.70f).coerceIn(0f, 1f)

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxSize()
                    .widthIn(max = DesignTokens.adaptive.contentMaxWidth),
                contentPadding = PaddingValues(
                    start = pagePadding,
                    top = 0.dp,
                    end = pagePadding,
                    bottom = maxOf(
                        scaffoldPadding.calculateBottomPadding(),
                        bottomContentInset,
                    ) + 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                item {
                    if (compact) {
                        HomeMobileHeader(modifier = Modifier.alpha(pageTitleAlpha))
                    } else {
                        DesignPageHeader(
                            title = stringResource(Res.string.home_good_evening),
                            subtitle = stringResource(Res.string.home_subtitle),
                            modifier = Modifier.alpha(pageTitleAlpha),
                        )
                    }
                }
                if (state.shouldShowEmptyState) {
                    item {
                        HomeEmptyState(
                            onAddSource = { onAction(HomeAction.NavigateToSourceSettings) },
                        )
                    }
                }
                if (!showEmptyStateOnly) {
                    item {
                        DailyPicksHero(
                            compact = compact,
                            tracks = state.dailyPickTracks.take(3),
                            nowPlayingTitle = dailyPicksTrackTitle,
                            onPlay = state.dailyPickTracks.takeIf { it.isNotEmpty() }?.let {
                                { onAction(HomeAction.PlayDailyPicks) }
                            },
                        )
                    }
                    if (state.pinnedPlaylists.isNotEmpty()) {
                        item {
                            HomeSection(
                                title = stringResource(Res.string.home_pinned_playlists),
                                icon = Res.drawable.icon_bookmark,
                                onClick = { onAction(HomeAction.NavigateToLibrary) },
                            ) {
                                PlaylistRow(
                                    playlists = state.pinnedPlaylists,
                                    cardWidth = playlistCardWidth,
                                    showMeta = true,
                                    onClick = { playlist ->
                                        onAction(HomeAction.NavigateToPlaylist(playlist.id))
                                    },
                                )
                            }
                        }
                    }
                    state.listeningPreview?.let { preview ->
                        item {
                            HomeSection(
                                title = stringResource(Res.string.home_your_listening),
                                icon = Res.drawable.icon_activity,
                                onClick = { onAction(HomeAction.NavigateToListening) },
                            ) {
                                HomeListeningPreview(
                                    compact = compact,
                                    preview = preview,
                                    onPlay = { track, ranking ->
                                        onAction(
                                            HomeAction.PlayListeningTrack(
                                                trackId = track.track.id,
                                                ranking = ranking,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                    if (state.pinnedPlaylists.isNotEmpty()) {
                        item {
                            HomeSection(
                                title = stringResource(Res.string.home_continue_playing),
                                icon = Res.drawable.icon_headphones,
                                onClick = { onAction(HomeAction.NavigateToLibrary) },
                            ) {
                                PlaylistRow(
                                    playlists = state.pinnedPlaylists,
                                    cardWidth = playlistCardWidth,
                                    showMeta = false,
                                    onClick = { playlist ->
                                        onAction(HomeAction.NavigateToPlaylist(playlist.id))
                                    },
                                )
                            }
                        }
                    }
                    if (state.recentTracks.isNotEmpty()) {
                        item {
                            HomeSection(
                                title = stringResource(Res.string.home_recently_played),
                                icon = Res.drawable.icon_clock,
                                onClick = { onAction(HomeAction.NavigateToLibrary) },
                            ) {
                                RecentlyPlayedPager(
                                    tracks = state.recentTracks,
                                    onPlay = { track -> onAction(HomeAction.PlayTrack(track.id)) },
                                )
                            }
                        }
                    }
                    if (state.dailyPickTracks.isNotEmpty()) {
                        item {
                            HomeSection(
                                title = stringResource(Res.string.home_new_songs),
                                icon = Res.drawable.icon_sparkles,
                                onClick = { onAction(HomeAction.NavigateToLibrary) },
                            ) {
                                NewSongRow(
                                    tracks = state.dailyPickTracks,
                                    cardWidth = newSongCardWidth,
                                    onPlay = { track -> onAction(HomeAction.PlayLibraryTrack(track.id)) },
                                )
                            }
                        }
                    }
                    if (state.featuredAlbums.isNotEmpty()) {
                        item {
                            HomeSection(
                                title = stringResource(Res.string.home_suggested_albums),
                                icon = Res.drawable.icon_disc,
                                onClick = { onAction(HomeAction.NavigateToLibrary) },
                            ) {
                                AlbumRow(
                                    albums = state.featuredAlbums,
                                    cardWidth = suggestedAlbumCardWidth,
                                    onClick = { album ->
                                        onAction(HomeAction.NavigateToAlbum(album.id))
                                    },
                                )
                            }
                        }
                    }
                    if (state.artists.isNotEmpty()) {
                        item {
                            HomeSection(
                                title = stringResource(Res.string.home_recommended_artists),
                                icon = Res.drawable.icon_mic_vocal,
                                onClick = { onAction(HomeAction.NavigateToLibrary) },
                            ) {
                                ArtistRow(
                                    artists = state.artists,
                                    size = artistSize,
                                    onOpen = { artist ->
                                        onAction(HomeAction.NavigateToArtist(artist.id))
                                    },
                                )
                            }
                        }
                    }
                }
            }
            DesignStickyGlassActionBar(
                title = stringResource(if (compact) Res.string.home_title else Res.string.home_good_evening),
                collapseFraction = actionBarProgress,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun HomeMobileHeader(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = stringResource(Res.string.home_title),
            color = MiuixTheme.colorScheme.onBackground,
            style = MiuixTheme.textStyles.title1.copy(
                fontSize = 32.sp,
                lineHeight = 38.sp,
            ),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DailyPicksHero(
    compact: Boolean,
    tracks: List<HomeRecentTrack>,
    nowPlayingTitle: String,
    onPlay: (() -> Unit)?,
) {
    val dark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val shape = RoundedCornerShape(if (compact) 22.dp else 30.dp)
    val foreground = if (dark) Color.White else Color(0xFF15151A)
    val muted = foreground.copy(alpha = if (dark) 0.74f else 0.62f)
    val backgroundBackdrop = rememberLayerBackdrop()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 152.dp else 260.dp)
            .shadow(DesignTokens.elevation.card, shape, clip = false)
            .clip(shape)
            .background(if (dark) Color(0xFF0F1026) else Color(0xFFE9DEF6))
            .border(0.5.dp, MiuixTheme.colorScheme.onSurface.copy(alpha = 0.10f), shape),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .layerBackdrop(backgroundBackdrop),
        ) {
            DailyPicksBackground(dark = dark)
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .designLiquidGlass(
                    backdrop = backgroundBackdrop,
                    shape = shape,
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(if (compact) 190.dp else 280.dp)
                .padding(start = if (compact) 20.dp else 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(Res.string.home_daily_picks),
                color = foreground,
                style = MiuixTheme.textStyles.title3.copy(
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                ),
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            Text(
                text = stringResource(Res.string.home_now_playing_label).uppercase(),
                color = muted,
                style = MiuixTheme.textStyles.footnote2,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = nowPlayingTitle,
                color = muted,
                style = MiuixTheme.textStyles.body1.copy(
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                ),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(6.dp))
            DailyPicksPlayButton(onClick = onPlay)
        }
        DailyPicksArtwork(
            tracks = tracks,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = if (compact) 14.dp else 32.dp)
                .size(width = if (compact) 140.dp else 210.dp, height = if (compact) 112.dp else 174.dp),
        )
    }
}

@Composable
private fun DailyPicksBackground(dark: Boolean) {
    val transition = rememberInfiniteTransition(label = "daily-picks-background")
    val blobOneProgress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = dailyPicksAnimation(durationMillis = 18_000),
        label = "daily-picks-blob-one",
    )
    val blobTwoProgress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = dailyPicksAnimation(durationMillis = 22_000),
        label = "daily-picks-blob-two",
    )
    val blobThreeProgress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = dailyPicksAnimation(durationMillis = 26_000),
        label = "daily-picks-blob-three",
    )
    val blobFourProgress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = dailyPicksAnimation(durationMillis = 30_000),
        label = "daily-picks-blob-four",
    )

    val surface = if (dark) Color(0xFF0F1026) else Color(0xFFE9DEF6)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(surface),
    ) {
        DailyPicksBlob(
            color = if (dark) Color(0xFF3157FF).copy(alpha = 0.72f) else Color(0xFF78ADFF).copy(alpha = 0.68f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(260.dp)
                .graphicsLayer {
                    val progress = blobOneProgress.value
                    translationX = (-72 + 88 * progress).dp.toPx()
                    translationY = (-138 + 54 * progress).dp.toPx()
                    scaleX = 1f + 0.12f * progress
                    scaleY = 0.92f + 0.10f * progress
                },
        )
        DailyPicksBlob(
            color = if (dark) Color(0xFFBB4FC7).copy(alpha = 0.58f) else Color(0xFFFFBFD8).copy(alpha = 0.66f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(250.dp)
                .graphicsLayer {
                    val progress = blobTwoProgress.value
                    translationX = (42 - 74 * progress).dp.toPx()
                    translationY = (-126 + 78 * progress).dp.toPx()
                    scaleX = 1.08f - 0.14f * progress
                    scaleY = 0.94f + 0.18f * progress
                },
        )
        DailyPicksBlob(
            color = if (dark) Color(0xFF395FE0).copy(alpha = 0.64f) else Color(0xFF9DAAFF).copy(alpha = 0.62f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(286.dp)
                .graphicsLayer {
                    val progress = blobThreeProgress.value
                    translationX = (92 - 94 * progress).dp.toPx()
                    translationY = (122 - 62 * progress).dp.toPx()
                    scaleX = 0.94f + 0.14f * progress
                    scaleY = 1.06f - 0.12f * progress
                },
        )
        DailyPicksBlob(
            color = if (dark) Color(0xFF7650C9).copy(alpha = 0.52f) else Color(0xFFD8A8E8).copy(alpha = 0.56f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(238.dp)
                .graphicsLayer {
                    val progress = blobFourProgress.value
                    translationX = (-66 + 86 * progress).dp.toPx()
                    translationY = (102 - 72 * progress).dp.toPx()
                    scaleX = 1.04f - 0.10f * progress
                    scaleY = 0.92f + 0.14f * progress
                },
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = if (dark) 0.08f else 0.46f),
                            Color.Transparent,
                            if (dark) Color(0xFF6D66FF).copy(alpha = 0.14f)
                            else Color(0xFFB7D8FF).copy(alpha = 0.20f),
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to surface.copy(alpha = if (dark) 0.28f else 0.18f),
                        0.48f to Color.Transparent,
                        1f to Color.Transparent,
                    ),
                ),
        )
    }
}

@Composable
private fun DailyPicksBlob(color: Color, modifier: Modifier) {
    Box(
        modifier = modifier
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color,
                        color.copy(alpha = color.alpha * 0.48f),
                        color.copy(alpha = 0f),
                    ),
                ),
                shape = CircleShape,
            ),
    )
}

private fun dailyPicksAnimation(durationMillis: Int) = infiniteRepeatable<Float>(
    animation = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
    repeatMode = RepeatMode.Reverse,
)

@Composable
private fun DailyPicksArtwork(
    tracks: List<HomeRecentTrack>,
    modifier: Modifier,
) {
    Box(
        modifier = modifier,
    ) {
        tracks.getOrNull(0)?.let { track ->
            DailyPicksCover(
                artwork = track.artwork,
                demoIndex = track.artworkIndex + 3,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(72.dp)
                    .graphicsLayer(rotationZ = -6f),
                shape = CircleShape,
            )
        }
        tracks.getOrNull(1)?.let { track ->
            DailyPicksCover(
                artwork = track.artwork,
                demoIndex = track.artworkIndex + 5,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp)
                    .size(65.dp)
                    .graphicsLayer(rotationZ = 8f),
                shape = RoundedCornerShape(32.dp, 20.dp, 28.dp, 24.dp),
            )
        }
        tracks.getOrNull(2)?.let { track ->
            DailyPicksCover(
                artwork = track.artwork,
                demoIndex = track.artworkIndex + 7,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(top = 22.dp)
                    .size(60.dp)
                    .graphicsLayer(rotationZ = 14f),
                shape = RoundedCornerShape(50),
            )
        }
    }
}

@Composable
private fun DailyPicksCover(
    artwork: Artwork?,
    demoIndex: Int,
    modifier: Modifier,
    shape: Shape,
) {
    Box(
        modifier = modifier
            .border(2.5.dp, Color.White.copy(alpha = 0.72f), shape)
            .clip(shape),
    ) {
        HomeArtworkImage(
            artwork = artwork,
            demoIndex = demoIndex,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun DailyPicksPlayButton(onClick: (() -> Unit)?) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(DesignTokens.adaptive.minimumTouchTarget)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Icon(
            painter = painterResource(CoreRes.drawable.icon_play_outline),
            contentDescription = stringResource(Res.string.home_play),
            tint = MiuixTheme.colorScheme.primary.copy(alpha = if (onClick == null) 0.42f else 1f),
            modifier = Modifier
                .align(Alignment.Center)
                .size(32.dp),
        )
    }
}

@Composable
private fun HomeEmptyState(onAddSource: () -> Unit) {
    val shape = RoundedCornerShape(DesignTokens.shapes.card)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.home_empty_title),
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(Res.string.home_empty_message),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
        )
        Row(
            modifier = Modifier
                .height(DesignTokens.adaptive.minimumTouchTarget)
                .clip(RoundedCornerShape(DesignTokens.shapes.full))
                .background(MiuixTheme.colorScheme.primary)
                .clickable(onClick = onAddSource)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.home_add_source),
                color = MiuixTheme.colorScheme.onPrimary,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun HomeSection(
    title: String,
    icon: DrawableResource,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = DesignTokens.adaptive.minimumTouchTarget)
                .clip(RoundedCornerShape(14.dp))
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onBackground,
                style = MiuixTheme.textStyles.title3.copy(
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                ),
                fontWeight = FontWeight.SemiBold,
            )
            if (onClick != null) {
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    painter = painterResource(Res.drawable.icon_section_chevron),
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        content()
    }
}

@Composable
private fun PlaylistRow(
    playlists: List<HomePlaylist>,
    cardWidth: Dp,
    showMeta: Boolean,
    onClick: (HomePlaylist) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DesignTokens.adaptive.minimumTouchTarget),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(playlists) { playlist ->
            PlaylistCard(
                playlist = playlist,
                width = cardWidth,
                showMeta = showMeta,
                onClick = { onClick(playlist) },
            )
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: HomePlaylist,
    width: Dp,
    showMeta: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        val artworkShape = RoundedCornerShape(14.dp)
        Box(
            modifier = Modifier
                .playlistArtworkSharedElement(playlist.id)
                .size(width)
                .shadow(DesignTokens.elevation.card, artworkShape, clip = false)
                .clip(artworkShape)
                .background(Brush.linearGradient(playlist.colors)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(homeCover(playlist.artworkIndex)),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.08f)),
            )
            if (showMeta) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f)),
                            ),
                        )
                        .padding(12.dp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Text(
                        text = playlist.meta,
                        color = Color.White.copy(alpha = 0.82f),
                        style = MiuixTheme.textStyles.footnote2,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = playlist.title,
            color = MiuixTheme.colorScheme.onBackground,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = playlist.description,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            style = MiuixTheme.textStyles.footnote1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecentlyPlayedPager(
    tracks: List<HomeRecentTrack>,
    onPlay: (HomeRecentTrack) -> Unit,
) {
    val pages = tracks.chunked(3)
    val pagerState = rememberPagerState(pageCount = { pages.size })

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(204.dp),
        ) { pageIndex ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                pages[pageIndex].forEachIndexed { itemIndex, track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onPlay(track) }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = (pageIndex * 3 + itemIndex + 1).toString(),
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            style = MiuixTheme.textStyles.footnote1,
                            modifier = Modifier.width(18.dp),
                        )
                        ArtworkTile(track, 46.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = MiuixTheme.colorScheme.onBackground,
                                style = MiuixTheme.textStyles.body1,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (track.subtitle.isNotBlank()) {
                                Text(
                                    text = track.subtitle,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    style = MiuixTheme.textStyles.footnote1,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (pages.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                pages.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .size(
                                width = if (pagerState.currentPage == index) 20.dp else 6.dp,
                                height = 6.dp,
                            )
                            .clip(CircleShape)
                            .background(
                                if (pagerState.currentPage == index) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.3f),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun NewSongRow(
    tracks: List<HomeRecentTrack>,
    cardWidth: Dp,
    onPlay: (HomeRecentTrack) -> Unit,
) {
    val itemPadding = DesignTokens.spacing.xxs
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp - itemPadding * 2),
    ) {
        items(tracks) { track ->
            Column(
                modifier = Modifier
                    .width(cardWidth + itemPadding * 2)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onPlay(track) }
                    .padding(itemPadding),
            ) {
                val shape = RoundedCornerShape(14.dp)
                Box(
                    modifier = Modifier
                        .size(cardWidth)
                        .shadow(DesignTokens.elevation.card, shape, clip = false)
                        .clip(shape)
                        .background(
                            Brush.linearGradient(
                                listOf(track.color, DesignPalette.Secondary),
                            ),
                        ),
                ) {
                    HomeArtworkImage(
                        artwork = track.artwork,
                        demoIndex = track.artworkIndex,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = track.title,
                    color = MiuixTheme.colorScheme.onBackground,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (track.subtitle.isNotBlank()) {
                    Text(
                        text = track.subtitle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumRow(
    albums: List<HomeFeaturedAlbum>,
    cardWidth: Dp,
    onClick: (HomeFeaturedAlbum) -> Unit,
) {
    val itemPadding = DesignTokens.spacing.xxs
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp - itemPadding * 2),
    ) {
        items(albums) { album ->
            Column(
                modifier = Modifier
                    .width(cardWidth + itemPadding * 2)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onClick(album) }
                    .padding(itemPadding),
            ) {
                val shape = RoundedCornerShape(14.dp)
                Box(
                    modifier = Modifier
                        .albumArtworkSharedElement(album.id)
                        .size(cardWidth)
                        .shadow(DesignTokens.elevation.card, shape, clip = false)
                        .clip(shape)
                        .background(Brush.linearGradient(album.colors)),
                    contentAlignment = Alignment.Center,
                ) {
                    HomeArtworkImage(
                        artwork = album.artwork,
                        demoIndex = album.artworkIndex,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.08f)),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = album.title,
                    color = MiuixTheme.colorScheme.onBackground,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (album.subtitle.isNotBlank()) {
                    Text(
                        text = album.subtitle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistRow(
    artists: List<HomeArtist>,
    size: Dp,
    onOpen: (HomeArtist) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(artists) { artist ->
            Column(
                modifier = Modifier
                    .width(size)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onOpen(artist) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(size)
                        .shadow(DesignTokens.elevation.card, CircleShape, clip = false)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(artist.colors)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(homeCover(artist.artworkIndex)),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.08f)),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = artist.name,
                    color = MiuixTheme.colorScheme.onBackground,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (artist.followers.isNotBlank()) {
                    Text(
                        text = artist.followers,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        style = MiuixTheme.textStyles.footnote1,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtworkTile(track: HomeRecentTrack, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(13.dp))
            .background(
                Brush.linearGradient(
                    listOf(track.color, DesignPalette.Secondary),
                ),
            ),
    ) {
        HomeArtworkImage(
            artwork = track.artwork,
            demoIndex = track.artworkIndex,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun HomeArtworkImage(
    artwork: Artwork?,
    demoIndex: Int,
    modifier: Modifier,
) {
    ArtworkImage(
        artwork = artwork,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        fallback = {
            Image(
                painter = painterResource(homeCover(demoIndex)),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        },
    )
}

private fun homeCover(index: Int): DrawableResource = when ((index - 1).mod(8)) {
    0 -> Res.drawable.home_cover_1
    1 -> Res.drawable.home_cover_2
    2 -> Res.drawable.home_cover_3
    3 -> Res.drawable.home_cover_4
    4 -> Res.drawable.home_cover_5
    5 -> Res.drawable.home_cover_6
    6 -> Res.drawable.home_cover_7
    else -> Res.drawable.home_cover_8
}

@Composable
private fun HomeListeningPreview(
    compact: Boolean,
    preview: HomeListeningPreview,
    onPlay: (HomeListeningRankedTrack, HomeListeningRanking) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val durationTitle = stringResource(Res.string.home_top_tracks)
    val playCountTitle = stringResource(Res.string.home_top_tracks_by_plays)
    val thisMonth = stringResource(Res.string.home_this_month)

    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(222.dp),
            ) { page ->
                val ranking = if (page == 0) {
                    HomeListeningRanking.Duration
                } else {
                    HomeListeningRanking.PlayCount
                }
                HomeListeningRankingColumn(
                    title = if (page == 0) durationTitle else playCountTitle,
                    subtitle = thisMonth,
                    tracks = if (page == 0) preview.durationRanking else preview.playCountRanking,
                    ranking = ranking,
                    onPlay = onPlay,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                repeat(2) { index ->
                    Box(
                        modifier = Modifier
                            .size(
                                width = if (pagerState.currentPage == index) 20.dp else 6.dp,
                                height = 6.dp,
                            )
                            .clip(CircleShape)
                            .background(
                                if (pagerState.currentPage == index) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.3f),
                            ),
                    )
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            HomeListeningRankingColumn(
                title = durationTitle,
                subtitle = thisMonth,
                tracks = preview.durationRanking,
                ranking = HomeListeningRanking.Duration,
                onPlay = onPlay,
                modifier = Modifier.weight(1f),
            )
            HomeListeningRankingColumn(
                title = playCountTitle,
                subtitle = thisMonth,
                tracks = preview.playCountRanking,
                ranking = HomeListeningRanking.PlayCount,
                onPlay = onPlay,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HomeListeningRankingColumn(
    title: String,
    subtitle: String,
    tracks: List<HomeListeningRankedTrack>,
    ranking: HomeListeningRanking,
    onPlay: (HomeListeningRankedTrack, HomeListeningRanking) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onBackground,
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote2,
            )
        }
        Spacer(Modifier.height(4.dp))
        tracks.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onPlay(item, ranking) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = (index + 1).toString(),
                    color = if (index < 3) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantActions
                    },
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(18.dp),
                )
                ArtworkTile(item.track, 46.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.track.title,
                        color = MiuixTheme.colorScheme.onBackground,
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.track.subtitle.isNotBlank()) {
                        Text(
                            text = item.track.subtitle,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.footnote1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (ranking == HomeListeningRanking.Duration) {
                            formatListeningDuration(item.listenedMs)
                        } else {
                            stringResource(Res.string.listening_plays, item.playCount)
                        },
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = if (ranking == HomeListeningRanking.Duration) {
                            stringResource(Res.string.listening_plays, item.playCount)
                        } else {
                            formatListeningDuration(item.listenedMs)
                        },
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        style = MiuixTheme.textStyles.footnote2,
                    )
                }
            }
        }
    }
}

private fun formatListeningDuration(totalMs: Long): String {
    val totalMinutes = (totalMs / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) hours.toString() + "h " + minutes.toString() + "m" else minutes.toString() + "m"
}
