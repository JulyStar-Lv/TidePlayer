package io.github.julystar.musicapp.feature.artist.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.presentation.components.DesignIconButton
import io.github.julystar.musicapp.core.presentation.components.DesignIconButtonSize
import io.github.julystar.musicapp.core.presentation.components.DesignIconButtonVariant
import io.github.julystar.musicapp.core.presentation.components.DesignListDivider
import io.github.julystar.musicapp.core.presentation.components.DesignStatusCard
import io.github.julystar.musicapp.core.presentation.components.DesignStickyGlassActionBar
import io.github.julystar.musicapp.core.presentation.components.DesignTabItem
import io.github.julystar.musicapp.core.presentation.components.DesignTabs
import io.github.julystar.musicapp.core.presentation.components.DesignTabsVariant
import io.github.julystar.musicapp.core.presentation.components.LocalDesignBottomContentInset
import io.github.julystar.musicapp.core.presentation.media.ArtworkImage
import io.github.julystar.musicapp.core.presentation.overlay.resolve
import io.github.julystar.musicapp.core.presentation.theme.DesignFontFamilies
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.julystar.musicapp.core.presentation.transition.albumArtworkSharedElement
import kotlinx.coroutines.launch
import musicapp.core.presentation.generated.resources.Res as CoreRes
import musicapp.core.presentation.generated.resources.icon_download
import musicapp.core.presentation.generated.resources.icon_play
import musicapp.feature.artist.generated.resources.Res
import musicapp.feature.artist.generated.resources.artist_albums
import musicapp.feature.artist.generated.resources.artist_all_songs
import musicapp.feature.artist.generated.resources.artist_back
import musicapp.feature.artist.generated.resources.artist_default_title
import musicapp.feature.artist.generated.resources.artist_download_track
import musicapp.feature.artist.generated.resources.artist_loading
import musicapp.feature.artist.generated.resources.artist_no_albums
import musicapp.feature.artist.generated.resources.artist_no_albums_message
import musicapp.feature.artist.generated.resources.artist_no_songs
import musicapp.feature.artist.generated.resources.artist_no_songs_message
import musicapp.feature.artist.generated.resources.artist_play_all
import musicapp.feature.artist.generated.resources.artist_retry
import musicapp.feature.artist.generated.resources.artist_summary
import musicapp.feature.artist.generated.resources.artist_unavailable
import musicapp.feature.artist.generated.resources.artist_unknown_year
import musicapp.feature.artist.generated.resources.artist_unknown_album
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistScreen(
    state: ArtistState,
    currentPlayingTrackId: Long? = null,
    onAction: (ArtistAction) -> Unit,
) {
    val spacing = DesignTokens.spacing
    val bottomContentInset = LocalDesignBottomContentInset.current
    val defaultTitle = stringResource(Res.string.artist_default_title)
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val collapseDistance = with(LocalDensity.current) { 88.dp.roundToPx() }
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
    val heroTitleAlpha = (1f - actionBarProgress / 0.70f).coerceIn(0f, 1f)
    var selectedSection by remember(state.artistId) {
        mutableStateOf(
            if (state.albums.isNotEmpty() || state.tracks.isEmpty()) {
                ArtistSection.Albums
            } else {
                ArtistSection.Songs
            },
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background),
    ) {
        val compact = maxWidth < 600.dp
        val horizontalPadding = if (compact) 20.dp else 32.dp

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 960.dp)
                .fillMaxWidth()
                .fillMaxHeight(),
        ) {
            when {
                state.isLoading -> DesignStatusCard(
                    title = stringResource(Res.string.artist_loading),
                    message = state.name.ifBlank { defaultTitle },
                    loading = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = DesignTokens.adaptive.compactHeaderHeight)
                        .padding(horizontal = horizontalPadding, vertical = spacing.md),
                )

                state.error != null -> DesignStatusCard(
                    title = stringResource(Res.string.artist_unavailable),
                    message = state.error.resolve(),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = DesignTokens.adaptive.compactHeaderHeight)
                        .padding(horizontal = horizontalPadding, vertical = spacing.md),
                    actionText = stringResource(Res.string.artist_retry),
                    onAction = { onAction(ArtistAction.Retry) },
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = horizontalPadding,
                        top = DesignTokens.adaptive.compactHeaderHeight,
                        end = horizontalPadding,
                        bottom = spacing.lg + bottomContentInset,
                    ),
                ) {
                    item(key = "artist-hero") {
                        ArtistHero(
                            name = state.name.ifBlank { defaultTitle },
                            artwork = state.artwork,
                            albumCount = state.albums.size,
                            trackCount = state.tracks.size,
                            compact = compact,
                            titleAlpha = heroTitleAlpha,
                        )
                    }
                    stickyHeader(key = "artist-sections") {
                        ArtistSectionTabs(
                            selectedSection = selectedSection,
                            albumCount = state.albums.size,
                            trackCount = state.tracks.size,
                            onSectionSelected = { section ->
                                if (selectedSection != section) {
                                    selectedSection = section
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(ARTIST_SECTION_TABS_INDEX)
                                    }
                                }
                            },
                        )
                    }
                    when (selectedSection) {
                        ArtistSection.Albums -> {
                            if (state.albums.isEmpty()) {
                                item(key = "artist-albums-empty") {
                                    ArtistEmptyState(
                                        title = stringResource(Res.string.artist_no_albums),
                                        message = stringResource(Res.string.artist_no_albums_message),
                                    )
                                }
                            } else {
                                item(key = "artist-album-grid") {
                                    ArtistAlbumGrid(
                                        albums = state.albums,
                                        onAlbumClick = { album ->
                                            onAction(ArtistAction.NavigateToAlbum(album.id))
                                        },
                                    )
                                }
                            }
                        }

                        ArtistSection.Songs -> {
                            item(key = "artist-song-actions") {
                                ArtistSongsActionBar(
                                    canPlay = state.tracks.isNotEmpty(),
                                    onPlayAll = { onAction(ArtistAction.PlayAll) },
                                )
                            }
                            if (state.tracks.isEmpty()) {
                                item(key = "artist-songs-empty") {
                                    ArtistEmptyState(
                                        title = stringResource(Res.string.artist_no_songs),
                                        message = stringResource(Res.string.artist_no_songs_message),
                                    )
                                }
                            } else {
                                itemsIndexed(
                                    items = state.tracks,
                                    key = { index, track -> track.lazyListKey(index) },
                                ) { index, track ->
                                    ArtistTrackRow(
                                        track = track,
                                        fallbackNumber = index + 1,
                                        playing = track.id == currentPlayingTrackId,
                                        onPlay = { onAction(ArtistAction.PlayTrack(track.id)) },
                                        onAlbumClick = track.albumId?.let { albumId ->
                                            { onAction(ArtistAction.NavigateToAlbum(albumId)) }
                                        },
                                        onDownload = {
                                            if (track.canDownload) {
                                                onAction(ArtistAction.DownloadTrack(track))
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            DesignStickyGlassActionBar(
                title = state.name.ifBlank { defaultTitle },
                collapseFraction = if (state.isLoading || state.error != null) {
                    1f
                } else {
                    actionBarProgress
                },
                onNavigateBack = { onAction(ArtistAction.NavigateBack) },
                backContentDescription = stringResource(Res.string.artist_back),
                centerTitle = true,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

private const val ARTIST_SECTION_TABS_INDEX = 1

private enum class ArtistSection {
    Albums,
    Songs,
}

@Composable
private fun ArtistHero(
    name: String,
    artwork: Artwork?,
    albumCount: Int,
    trackCount: Int,
    compact: Boolean,
    titleAlpha: Float,
) {
    val artworkSize = if (compact) 112.dp else 220.dp
    val titleSize = if (compact) 26.sp else 38.sp
    val titleLineHeight = if (compact) 31.sp else 44.sp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (compact) 14.dp else 20.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 28.dp),
        verticalAlignment = if (compact) Alignment.CenterVertically else Alignment.Bottom,
    ) {
        Box(
            modifier = Modifier
                .size(artworkSize)
                .shadow(if (compact) 8.dp else 14.dp, CircleShape)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        ) {
            ArtworkImage(artwork = artwork, modifier = Modifier.fillMaxSize())
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (compact) 0.dp else 4.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp),
        ) {
            Text(
                text = stringResource(Res.string.artist_default_title),
                color = MiuixTheme.colorScheme.primary,
                style = MiuixTheme.textStyles.footnote2.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                ),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = name,
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.title2.copy(
                    fontSize = titleSize,
                    lineHeight = titleLineHeight,
                ),
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(titleAlpha),
            )
            Text(
                text = stringResource(Res.string.artist_summary, albumCount, trackCount),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1.copy(
                    fontSize = if (compact) 12.sp else 14.sp,
                    lineHeight = if (compact) 16.sp else 20.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ArtistSectionTabs(
    selectedSection: ArtistSection,
    albumCount: Int,
    trackCount: Int,
    onSectionSelected: (ArtistSection) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.background.copy(alpha = 0.96f))
            .padding(vertical = 10.dp),
    ) {
        DesignTabs(
            items = listOf(
                DesignTabItem(
                    label = stringResource(Res.string.artist_albums),
                    badge = albumCount.toString(),
                ),
                DesignTabItem(
                    label = stringResource(Res.string.artist_all_songs),
                    badge = trackCount.toString(),
                ),
            ),
            selectedIndex = selectedSection.ordinal,
            onSelectedIndexChange = { index ->
                onSectionSelected(ArtistSection.entries[index])
            },
            variant = DesignTabsVariant.Segmented,
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ArtistAlbumGrid(
    albums: List<ArtistAlbumItem>,
    onAlbumClick: (ArtistAlbumItem) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        val columns = when {
            maxWidth >= 820.dp -> 4
            maxWidth >= 520.dp -> 3
            else -> 2
        }
        val gap = 16.dp
        val cardWidth = (maxWidth - gap * (columns - 1)) / columns

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            maxItemsInEachRow = columns,
        ) {
            albums.forEach { album ->
                ArtistAlbumCard(
                    album = album,
                    width = cardWidth,
                    onClick = { onAlbumClick(album) },
                )
            }
        }
    }
}

@Composable
private fun ArtistAlbumCard(
    album: ArtistAlbumItem,
    width: Dp,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .albumArtworkSharedElement(album.id)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)),
        ) {
            ArtworkImage(artwork = album.artwork, modifier = Modifier.fillMaxSize())
        }
        Text(
            text = album.name.ifBlank { stringResource(Res.string.artist_unknown_album) },
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.body2.copy(fontSize = 14.sp, lineHeight = 18.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = album.year?.toString() ?: stringResource(Res.string.artist_unknown_year),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.sp, lineHeight = 16.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun ArtistSongsActionBar(
    canPlay: Boolean,
    onPlayAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(enabled = canPlay, onClick = onPlayAll),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(CoreRes.drawable.icon_play),
            contentDescription = null,
            tint = MiuixTheme.colorScheme.primary.copy(alpha = if (canPlay) 1f else 0.35f),
            modifier = Modifier.size(17.dp),
        )
        Text(
            text = stringResource(Res.string.artist_play_all),
            color = MiuixTheme.colorScheme.primary.copy(alpha = if (canPlay) 1f else 0.35f),
            style = MiuixTheme.textStyles.body2.copy(fontSize = 14.sp, lineHeight = 18.sp),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ArtistTrackRow(
    track: ArtistTrackItem,
    fallbackNumber: Int,
    playing: Boolean,
    onPlay: () -> Unit,
    onAlbumClick: (() -> Unit)?,
    onDownload: () -> Unit,
) {
    val albumName = track.albumName?.takeIf(String::isNotBlank)
    val metadata = listOfNotNull(
        albumName,
        track.durationMs?.let(::durationLabel),
    ).joinToString(" · ")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .background(
                if (playing) {
                    MiuixTheme.colorScheme.primary.copy(alpha = 0.09f)
                } else {
                    MiuixTheme.colorScheme.background
                },
            )
            .clickable(onClick = onPlay),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.width(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = track.trackLabel(fallbackNumber),
                    color = if (playing) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                    style = MiuixTheme.textStyles.footnote1.copy(
                        fontFamily = DesignFontFamilies.Mono,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    ),
                    fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = track.title,
                    color = if (playing) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurface
                    },
                    style = MiuixTheme.textStyles.body1.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (metadata.isNotEmpty()) {
                    Text(
                        text = metadata,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (albumName != null && onAlbumClick != null) {
                            Modifier.clickable(onClick = onAlbumClick)
                        } else {
                            Modifier
                        },
                    )
                }
            }
            if (track.canDownload) {
                DesignIconButton(
                    size = DesignIconButtonSize.Medium,
                    variant = DesignIconButtonVariant.Default,
                    painter = painterResource(CoreRes.drawable.icon_download),
                    contentDescription = stringResource(Res.string.artist_download_track, track.title),
                    onClick = onDownload,
                )
            }
        }
        DesignListDivider(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ArtistEmptyState(
    title: String,
    message: String,
) {
    DesignStatusCard(
        title = title,
        message = message,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp)
            .padding(top = 8.dp),
    )
}

internal fun ArtistAlbumItem.lazyListKey(index: Int): String = "artist-album-$index-$id"
internal fun ArtistTrackItem.lazyListKey(index: Int): String = "artist-track-$index-$id"

private fun ArtistTrackItem.trackLabel(fallbackNumber: Int): String = buildString {
    if (discNumber != null && discNumber > 1) {
        append("$discNumber.")
    }
    append(trackNumber ?: fallbackNumber)
}

private fun durationLabel(durationMs: Long): String {
    val totalSeconds = (durationMs / 1_000).coerceAtLeast(0)
    val hours = totalSeconds / 3_600
    val minutes = totalSeconds / 60 % 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
