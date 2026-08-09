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
import androidx.compose.ui.draw.blur as softBlur
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
import musicapp.feature.home.generated.resources.home_listening_today
import musicapp.feature.home.generated.resources.home_listening_total_time
import musicapp.feature.home.generated.resources.home_listening_tracks_played
import musicapp.feature.home.generated.resources.home_no_track
import musicapp.feature.home.generated.resources.home_now_playing_label
import musicapp.feature.home.generated.resources.home_add_source
import musicapp.feature.home.generated.resources.home_recommended_artists
import musicapp.feature.home.generated.resources.home_recently_played
import musicapp.feature.home.generated.resources.home_subtitle
import musicapp.feature.home.generated.resources.home_suggested_albums
import musicapp.feature.home.generated.resources.home_title
import musicapp.feature.home.generated.resources.home_your_listening
import musicapp.feature.home.generated.resources.listening_title
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
                    state.statistics?.let { statistics ->
                        item {
                            HomeSection(
                                title = stringResource(Res.string.home_your_listening),
                                icon = Res.drawable.icon_activity,
                                onClick = { onAction(HomeAction.NavigateToListening) },
                            ) {
                                HomeStatisticsCard(statistics = statistics)
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
                                    onOpen = { onAction(HomeAction.NavigateToLibrary) },
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
                style = MiuixTheme.textStyles.title3,
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
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium,
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
    val blobOneX by transition.animateFloat(
        initialValue = -38f,
        targetValue = 46f,
        animationSpec = dailyPicksAnimation(durationMillis = 14_000),
        label = "daily-picks-blob-one-x",
    )
    val blobOneY by transition.animateFloat(
        initialValue = -92f,
        targetValue = -42f,
        animationSpec = dailyPicksAnimation(durationMillis = 14_000),
        label = "daily-picks-blob-one-y",
    )
    val blobTwoX by transition.animateFloat(
        initialValue = 20f,
        targetValue = -42f,
        animationSpec = dailyPicksAnimation(durationMillis = 17_000),
        label = "daily-picks-blob-two-x",
    )
    val blobTwoY by transition.animateFloat(
        initialValue = -82f,
        targetValue = -22f,
        animationSpec = dailyPicksAnimation(durationMillis = 17_000),
        label = "daily-picks-blob-two-y",
    )
    val blobThreeX by transition.animateFloat(
        initialValue = 74f,
        targetValue = -28f,
        animationSpec = dailyPicksAnimation(durationMillis = 20_000),
        label = "daily-picks-blob-three-x",
    )
    val blobThreeY by transition.animateFloat(
        initialValue = 70f,
        targetValue = 18f,
        animationSpec = dailyPicksAnimation(durationMillis = 20_000),
        label = "daily-picks-blob-three-y",
    )
    val blobFourX by transition.animateFloat(
        initialValue = -30f,
        targetValue = 64f,
        animationSpec = dailyPicksAnimation(durationMillis = 23_000),
        label = "daily-picks-blob-four-x",
    )
    val blobFourY by transition.animateFloat(
        initialValue = 52f,
        targetValue = -18f,
        animationSpec = dailyPicksAnimation(durationMillis = 23_000),
        label = "daily-picks-blob-four-y",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (dark) Color(0xFF0F1026) else Color(0xFFE9DEF6)),
    ) {
        DailyPicksBlob(
            color = if (dark) Color(0xFF1226C9).copy(alpha = 0.88f) else Color(0xFF94BDFF).copy(alpha = 0.95f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = blobOneX.dp, y = blobOneY.dp)
                .size(190.dp),
        )
        DailyPicksBlob(
            color = if (dark) Color(0xFF9E35AA).copy(alpha = 0.78f) else Color(0xFFFFD6E8).copy(alpha = 0.96f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = blobTwoX.dp, y = blobTwoY.dp)
                .size(180.dp),
        )
        DailyPicksBlob(
            color = if (dark) Color(0xFF0F3FD6).copy(alpha = 0.82f) else Color(0xFFBDC2FF).copy(alpha = 0.92f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = blobThreeX.dp, y = blobThreeY.dp)
                .size(210.dp),
        )
        DailyPicksBlob(
            color = if (dark) Color(0xFF4129A6).copy(alpha = 0.72f) else Color(0xFFF7C4D6).copy(alpha = 0.90f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = blobFourX.dp, y = blobFourY.dp)
                .size(170.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = if (dark) 0.10f else 0.62f),
                            Color.Transparent,
                            if (dark) Color(0xFF565BFF).copy(alpha = 0.12f)
                            else Color(0xFFB4DCFF).copy(alpha = 0.22f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun DailyPicksBlob(color: Color, modifier: Modifier) {
    Box(
        modifier = modifier
            .softBlur(radius = 36.dp)
            .clip(CircleShape)
            .background(color),
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
    onOpen: () -> Unit,
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
                    .clickable(onClick = onOpen),
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
private fun HomeStatisticsCard(
    statistics: io.github.julystar.musicapp.feature.home.domain.HomeStatistics,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.icon_headphones),
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = stringResource(Res.string.listening_title),
                    color = MiuixTheme.colorScheme.onBackground,
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatItem(
                    label = stringResource(Res.string.home_listening_tracks_played),
                    value = statistics.totalTracksEverPlayed.toString(),
                )
                StatItem(
                    label = stringResource(Res.string.home_listening_today),
                    value = statistics.tracksPlayedToday.toString(),
                )
                StatItem(
                    label = stringResource(Res.string.home_listening_total_time),
                    value = formatListeningDuration(statistics.totalListeningDurationMs),
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = MiuixTheme.colorScheme.onBackground,
            style = MiuixTheme.textStyles.title2,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            color = MiuixTheme.colorScheme.primary,
            style = MiuixTheme.textStyles.footnote1,
        )
    }
}

private fun formatListeningDuration(totalMs: Long): String {
    val totalMinutes = (totalMs / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) hours.toString() + "h " + minutes.toString() + "m" else minutes.toString() + "m"
}
