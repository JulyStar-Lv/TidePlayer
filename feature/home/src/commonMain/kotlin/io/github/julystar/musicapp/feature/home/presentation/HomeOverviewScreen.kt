package io.github.julystar.musicapp.feature.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.components.QualityBadge
import io.github.julystar.musicapp.core.presentation.layout.WindowSizeClass
import io.github.julystar.musicapp.core.presentation.layout.rememberWindowSizeClass
import io.github.julystar.musicapp.core.presentation.theme.DesignGradients
import io.github.julystar.musicapp.core.presentation.theme.DesignPalette
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import musicapp.feature.home.generated.resources.Res
import musicapp.feature.home.generated.resources.home_continue_playing
import musicapp.feature.home.generated.resources.home_duration_hours
import musicapp.feature.home.generated.resources.home_duration_minutes
import musicapp.feature.home.generated.resources.home_featured_default_description
import musicapp.feature.home.generated.resources.home_featured_default_title
import musicapp.feature.home.generated.resources.home_featured_playlist
import musicapp.feature.home.generated.resources.home_good_evening
import musicapp.feature.home.generated.resources.home_liked
import musicapp.feature.home.generated.resources.home_pinned_playlists
import musicapp.feature.home.generated.resources.home_play
import musicapp.feature.home.generated.resources.home_playlist_track_count
import musicapp.feature.home.generated.resources.home_recently_added
import musicapp.feature.home.generated.resources.home_recently_played
import musicapp.feature.home.generated.resources.home_recommended_artists
import musicapp.feature.home.generated.resources.home_save
import musicapp.feature.home.generated.resources.home_see_all
import musicapp.feature.home.generated.resources.icon_heart
import musicapp.feature.home.generated.resources.icon_music_note
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeOverviewScreen(
    scaffoldPadding: PaddingValues,
    state: HomeState,
    onAction: (HomeAction) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val windowSizeClass = rememberWindowSizeClass(DpSize(maxWidth, maxHeight))
        val layout = homeOverviewLayout(windowSizeClass)
        val showMobileHeader = windowSizeClass != WindowSizeClass.Large &&
            windowSizeClass != WindowSizeClass.XL
        val seeAll = stringResource(Res.string.home_see_all)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background),
            contentPadding = PaddingValues(
                start = layout.horizontalPadding,
                top = layout.topPadding,
                end = layout.horizontalPadding,
                bottom = scaffoldPadding.calculateBottomPadding() + layout.bottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (showMobileHeader) {
                item {
                    MobilePageHeader(title = stringResource(Res.string.home_good_evening))
                }
            }
            item {
                FeaturedHero(
                    playlist = state.pinnedPlaylists.firstOrNull(),
                    height = layout.heroHeight,
                    onPlay = { onAction(HomeAction.NavigateToLibrary) },
                )
            }
            item {
                HomeMediaSection(
                    title = stringResource(Res.string.home_continue_playing),
                    action = seeAll,
                ) {
                    MediaCardRow(
                        albums = state.featuredAlbums,
                        cardSize = layout.albumCardSize,
                        onClick = { album -> onAction(HomeAction.NavigateToAlbum(album.id)) },
                    )
                }
            }
            item {
                HomeMediaSection(
                    title = stringResource(Res.string.home_recently_added),
                    action = seeAll,
                ) {
                    MediaCardRow(
                        albums = state.recentlyAddedAlbums,
                        cardSize = layout.smallAlbumCardSize,
                        onClick = { album -> onAction(HomeAction.NavigateToAlbum(album.id)) },
                    )
                }
            }
            item {
                HomeMediaSection(
                    title = stringResource(Res.string.home_recommended_artists),
                    action = seeAll,
                ) {
                    ArtistRow(
                        artists = state.artists,
                        onClick = { artist ->
                            onAction(HomeAction.NavigateToArtist(artist.id))
                        },
                    )
                }
            }
            item {
                HomeMediaSection(
                    title = stringResource(Res.string.home_pinned_playlists),
                    action = seeAll,
                ) {
                    PlaylistRow(
                        playlists = state.pinnedPlaylists,
                        onClick = { playlist ->
                            onAction(HomeAction.NavigateToPlaylist(playlist.id))
                        },
                    )
                }
            }
            item {
                HomeMediaSection(title = stringResource(Res.string.home_recently_played)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.recentTracks.forEach { track ->
                            RecentTrackRow(
                                track = track,
                                onClick = { onAction(HomeAction.NavigateToLibrary) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobilePageHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onBackground,
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeaderAction(label = "◐")
            HeaderAction(label = "•")
        }
    }
}

@Composable
private fun HeaderAction(label: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun FeaturedHero(
    playlist: HomePlaylist?,
    height: Dp,
    onPlay: () -> Unit,
) {
    val colors = playlist?.colors ?: DesignGradients.Brand.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.linearGradient(colors)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.70f)),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .size(width = if (index == 0) 20.dp else 6.dp, height = 6.dp)
                        .clip(RoundedCornerShape(DesignTokens.shapes.full))
                        .background(
                            if (index == 0) Color.White else Color.White.copy(alpha = 0.40f),
                        ),
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(Res.string.home_featured_playlist).uppercase(),
                color = Color.White.copy(alpha = 0.72f),
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = playlist?.title ?: stringResource(Res.string.home_featured_default_title),
                color = Color.White,
                style = MiuixTheme.textStyles.title1,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = playlist?.let {
                    stringResource(Res.string.home_playlist_track_count, it.trackCount)
                } ?: stringResource(Res.string.home_featured_default_description),
                color = Color.White.copy(alpha = 0.72f),
                style = MiuixTheme.textStyles.body1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroButton(
                    text = "▶  ${stringResource(Res.string.home_play)}",
                    background = Color.White,
                    foreground = Color(0xFF0D0B18),
                    onClick = onPlay,
                )
                HeroButton(
                    text = "◇  ${stringResource(Res.string.home_save)}",
                    background = Color.White.copy(alpha = 0.20f),
                    foreground = Color.White,
                )
            }
        }
    }
}

@Composable
private fun HeroButton(
    text: String,
    background: Color,
    foreground: Color,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(DesignTokens.shapes.full))
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = foreground,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun HomeMediaSection(
    title: String,
    action: String? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onBackground,
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Bold,
            )
            if (action != null) {
                Text(
                    text = "$action  ›",
                    color = MiuixTheme.colorScheme.primary,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        content()
    }
}

@Composable
private fun MediaCardRow(
    albums: List<HomeFeaturedAlbum>,
    cardSize: Dp,
    onClick: (HomeFeaturedAlbum) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        albums.forEach { album ->
            AlbumCard(album = album, size = cardSize, onClick = { onClick(album) })
        }
    }
}

@Composable
private fun AlbumCard(
    album: HomeFeaturedAlbum,
    size: Dp,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(size)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        val artworkShape = RoundedCornerShape(24.dp)
        Box(
            modifier = Modifier
                .size(size)
                .shadow(8.dp, artworkShape, clip = false)
                .clip(artworkShape)
                .background(Brush.linearGradient(album.colors)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.icon_music_note),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.24f),
                modifier = Modifier.size(size * 0.36f),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = album.title,
            color = MiuixTheme.colorScheme.onBackground,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (album.subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = album.subtitle,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                style = MiuixTheme.textStyles.footnote1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ArtistRow(
    artists: List<HomeArtist>,
    onClick: (HomeArtist) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        artists.forEach { artist ->
            Column(
                modifier = Modifier
                    .width(128.dp)
                    .clickable { onClick(artist) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(128.dp)
                        .shadow(8.dp, CircleShape, clip = false)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(artist.colors)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = artist.initials,
                        color = Color.White.copy(alpha = 0.90f),
                        style = MiuixTheme.textStyles.title1,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = artist.name,
                    color = MiuixTheme.colorScheme.onBackground,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (artist.followers.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
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
private fun PlaylistRow(
    playlists: List<HomePlaylist>,
    onClick: (HomePlaylist) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        playlists.forEach { playlist ->
            Column(
                modifier = Modifier
                    .width(160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onClick(playlist) },
            ) {
                val artworkShape = RoundedCornerShape(24.dp)
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .shadow(8.dp, artworkShape, clip = false)
                        .clip(artworkShape)
                        .background(Brush.linearGradient(playlist.colors)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.icon_music_note),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.30f),
                        modifier = Modifier.size(56.dp),
                    )
                    Text(
                        text = homeDurationLabel(playlist.durationMs),
                        color = Color.White.copy(alpha = 0.82f),
                        style = MiuixTheme.textStyles.footnote1,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = playlist.title,
                    color = MiuixTheme.colorScheme.onBackground,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(Res.string.home_playlist_track_count, playlist.trackCount),
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    style = MiuixTheme.textStyles.footnote1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RecentTrackRow(
    track: HomeRecentTrack,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(track.color, DesignPalette.Secondary))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.icon_music_note),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.82f),
                modifier = Modifier.size(16.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = track.title,
                    color = MiuixTheme.colorScheme.onBackground,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                track.qualityBadge?.let { QualityBadge(type = it) }
            }
            Text(
                text = track.subtitle,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                style = MiuixTheme.textStyles.footnote1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            track.durationMs?.let { durationMs ->
                Text(
                    text = durationClockLabel(durationMs),
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
            if (track.liked) {
                Icon(
                    painter = painterResource(Res.drawable.icon_heart),
                    tint = MiuixTheme.colorScheme.primary,
                    contentDescription = stringResource(Res.string.home_liked),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun homeDurationLabel(durationMs: Long): String {
    val totalMinutes = (durationMs / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) {
        stringResource(Res.string.home_duration_hours, hours, minutes)
    } else {
        stringResource(Res.string.home_duration_minutes, minutes)
    }
}

private fun durationClockLabel(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
private fun homeOverviewLayout(windowSizeClass: WindowSizeClass): HomeOverviewLayout {
    val spacing = DesignTokens.spacing
    return HomeOverviewLayout(
        horizontalPadding = spacing.pageCompact,
        topPadding = 8.dp,
        bottomPadding = 16.dp,
        heroHeight = 208.dp,
        albumCardSize = 160.dp,
        smallAlbumCardSize = 120.dp,
    )
}

private data class HomeOverviewLayout(
    val horizontalPadding: Dp,
    val topPadding: Dp,
    val bottomPadding: Dp,
    val heroHeight: Dp,
    val albumCardSize: Dp,
    val smallAlbumCardSize: Dp,
)
