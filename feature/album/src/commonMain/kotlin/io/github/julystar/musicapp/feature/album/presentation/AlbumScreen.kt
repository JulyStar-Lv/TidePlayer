package io.github.julystar.musicapp.feature.album.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.presentation.components.DesignContextMenu
import io.github.julystar.musicapp.core.presentation.components.DesignContextMenuItem
import io.github.julystar.musicapp.core.presentation.components.DesignIconButton
import io.github.julystar.musicapp.core.presentation.components.DesignIconButtonColors
import io.github.julystar.musicapp.core.presentation.components.DesignIconButtonSize
import io.github.julystar.musicapp.core.presentation.components.DesignIconButtonVariant
import io.github.julystar.musicapp.core.presentation.components.DesignListDivider
import io.github.julystar.musicapp.core.presentation.components.DesignStatusCard
import io.github.julystar.musicapp.core.presentation.components.DesignStickyGlassActionBar
import io.github.julystar.musicapp.core.presentation.components.LocalDesignBottomContentInset
import io.github.julystar.musicapp.core.presentation.media.ArtworkImage
import io.github.julystar.musicapp.core.presentation.overlay.resolve
import io.github.julystar.musicapp.core.presentation.theme.DesignFontFamilies
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.julystar.musicapp.core.presentation.transition.albumArtworkSharedElement
import kotlinx.coroutines.launch
import musicapp.core.presentation.generated.resources.Res as CoreRes
import musicapp.core.presentation.generated.resources.icon_download
import musicapp.core.presentation.generated.resources.icon_heart
import musicapp.core.presentation.generated.resources.icon_heart_filled
import musicapp.core.presentation.generated.resources.icon_locate_fixed
import musicapp.core.presentation.generated.resources.icon_play
import musicapp.core.presentation.generated.resources.icon_vertialcal_more
import musicapp.feature.album.generated.resources.Res
import musicapp.feature.album.generated.resources.album_add_favorite
import musicapp.feature.album.generated.resources.album_back
import musicapp.feature.album.generated.resources.album_default_title
import musicapp.feature.album.generated.resources.album_detail_summary
import musicapp.feature.album.generated.resources.album_download
import musicapp.feature.album.generated.resources.album_duration_hours
import musicapp.feature.album.generated.resources.album_duration_hours_minutes
import musicapp.feature.album.generated.resources.album_duration_minutes
import musicapp.feature.album.generated.resources.album_duration_minutes_seconds
import musicapp.feature.album.generated.resources.album_duration_seconds
import musicapp.feature.album.generated.resources.album_locate_current
import musicapp.feature.album.generated.resources.album_no_tracks
import musicapp.feature.album.generated.resources.album_play
import musicapp.feature.album.generated.resources.album_play_all
import musicapp.feature.album.generated.resources.album_remove_favorite
import musicapp.feature.album.generated.resources.album_retry
import musicapp.feature.album.generated.resources.album_track_more_actions
import musicapp.feature.album.generated.resources.album_unavailable
import musicapp.feature.album.generated.resources.album_unknown_artist
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumScreen(
    state: AlbumState,
    currentPlayingTrackId: Long?,
    favoriteTrackIds: Set<Long>,
    onToggleFavorite: (Long) -> Unit,
    onAction: (AlbumAction) -> Unit,
) {
    val spacing = DesignTokens.spacing
    val bottomContentInset = LocalDesignBottomContentInset.current
    val defaultTitle = stringResource(Res.string.album_default_title)
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
    val pageTitleAlpha = (1f - actionBarProgress / 0.70f).coerceIn(0f, 1f)
    val currentTrackIndex = state.tracks.indexOfFirst { track -> track.id == currentPlayingTrackId }

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
                state.error != null -> DesignStatusCard(
                    title = stringResource(Res.string.album_unavailable),
                    message = state.error.resolve(),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = DesignTokens.adaptive.compactHeaderHeight)
                        .padding(horizontal = horizontalPadding, vertical = spacing.md),
                    actionText = stringResource(Res.string.album_retry),
                    onAction = { onAction(AlbumAction.Retry) },
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
                    item(key = "album-hero") {
                        AlbumHero(
                            state = state,
                            compact = compact,
                            titleAlpha = pageTitleAlpha,
                            showDetails = !state.isLoading,
                            defaultTitle = defaultTitle,
                        )
                    }
                    if (!state.isLoading) {
                        stickyHeader(key = "album-actions") {
                            AlbumActionBar(
                                canPlay = state.tracks.isNotEmpty(),
                                canLocate = currentTrackIndex >= 0,
                                onPlayAll = { onAction(AlbumAction.PlayAll) },
                                onLocateCurrent = {
                                    if (currentTrackIndex >= 0) {
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(
                                                currentTrackIndex + AlbumTrackListStartIndex,
                                            )
                                        }
                                    }
                                }
                            )
                        }
                        if (state.tracks.isEmpty()) {
                            item(key = "album-empty") {
                                DesignStatusCard(
                                    title = stringResource(Res.string.album_no_tracks),
                                    message = state.title.ifBlank { defaultTitle },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 260.dp)
                                        .padding(top = spacing.sm),
                                )
                            }
                        } else {
                            itemsIndexed(
                                items = state.tracks,
                                key = { index, track -> "album-track-${track.id}-$index" },
                            ) { index, track ->
                                AlbumTrackRow(
                                    track = track,
                                    trackNumber = index + 1,
                                    favorite = track.id in favoriteTrackIds,
                                    onPlay = { onAction(AlbumAction.PlayTrack(track.id)) },
                                    onToggleFavorite = { onToggleFavorite(track.id) },
                                    onDownload = { onAction(AlbumAction.DownloadTrack(track)) },
                                )
                            }
                        }
                    }
                }
            }
            DesignStickyGlassActionBar(
                title = state.title.ifBlank { defaultTitle },
                collapseFraction = if (state.error != null) {
                    1f
                } else {
                    actionBarProgress
                },
                onNavigateBack = { onAction(AlbumAction.NavigateBack) },
                backContentDescription = stringResource(Res.string.album_back),
                centerTitle = true,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

private const val AlbumTrackListStartIndex = 2

@Composable
private fun AlbumHero(
    state: AlbumState,
    compact: Boolean,
    titleAlpha: Float,
    showDetails: Boolean,
    defaultTitle: String,
) {
    val artworkSize = if (compact) 168.dp else 280.dp
    val artworkRadius = if (compact) 18.dp else 24.dp
    val titleSize = if (compact) 24.sp else 36.sp
    val titleLineHeight = if (compact) 29.sp else 42.sp
    val metadata = listOfNotNull(
        state.artist.takeIf { it.isNotBlank() },
        state.year?.toString(),
        state.genre?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    val totalDurationMs = state.tracks.sumOf { track -> track.durationMs ?: 0L }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (compact) 14.dp else 20.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 28.dp),
        verticalAlignment = if (compact) Alignment.CenterVertically else Alignment.Bottom,
    ) {
        Box(
            modifier = Modifier
                .albumArtworkSharedElement(state.albumId)
                .size(artworkSize)
                .clip(RoundedCornerShape(artworkRadius))
                .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        ) {
            ArtworkImage(artwork = state.artwork, modifier = Modifier.fillMaxSize())
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (compact) 0.dp else 4.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp),
        ) {
            if (showDetails) {
                Text(
                    text = state.title.ifBlank { defaultTitle },
                    style = MiuixTheme.textStyles.title2.copy(
                        fontSize = titleSize,
                        lineHeight = titleLineHeight,
                    ),
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(titleAlpha),
                )
                if (metadata.isNotBlank()) {
                    Text(
                        text = metadata,
                        style = MiuixTheme.textStyles.footnote1.copy(
                            fontSize = if (compact) 12.sp else 14.sp,
                            lineHeight = if (compact) 16.sp else 20.sp,
                        ),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = stringResource(
                        Res.string.album_detail_summary,
                        state.tracks.size,
                        albumDurationLabel(totalDurationMs),
                    ),
                    style = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AlbumActionBar(
    canPlay: Boolean,
    canLocate: Boolean,
    onPlayAll: () -> Unit,
    onLocateCurrent: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(MiuixTheme.colorScheme.background.copy(alpha = 0.96f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .height(44.dp)
                .clip(CircleShape)
                .clickable(enabled = canPlay, onClick = onPlayAll)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(CoreRes.drawable.icon_play),
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary.copy(alpha = if (canPlay) 1f else 0.35f),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(Res.string.album_play_all),
                color = MiuixTheme.colorScheme.primary.copy(alpha = if (canPlay) 1f else 0.35f),
                style = MiuixTheme.textStyles.body2.copy(fontSize = 14.sp, lineHeight = 18.sp),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Box(modifier = Modifier.weight(1f))
        DesignIconButton(
            size = DesignIconButtonSize.Medium,
            variant = DesignIconButtonVariant.Default,
            painter = painterResource(CoreRes.drawable.icon_locate_fixed),
            contentDescription = stringResource(Res.string.album_locate_current),
            colors = DesignIconButtonColors(
                buttonBg = MiuixTheme.colorScheme.surfaceContainerHigh,
                iconTint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            ),
            enabled = canLocate,
            onClick = onLocateCurrent,
        )
    }
}

@Composable
private fun AlbumTrackRow(
    track: AlbumTrackItem,
    trackNumber: Int,
    favorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
) {
    var moreMenuExpanded by remember(track.id) { mutableStateOf(false) }
    val artist = track.artist?.takeIf { it.isNotBlank() }
        ?: stringResource(Res.string.album_unknown_artist)
    val subtitle = listOfNotNull(
        artist,
        track.albumTitle.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(MiuixTheme.colorScheme.background)
            .clickable(onClick = onPlay),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 0.dp, end = 0.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.width(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = track.trackLabel(trackNumber),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1.copy(
                        fontFamily = DesignFontFamilies.Mono,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    ),
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
                    color = MiuixTheme.colorScheme.onSurface,
                    style = MiuixTheme.textStyles.body1.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleFavorite),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(
                        if (favorite) CoreRes.drawable.icon_heart_filled else CoreRes.drawable.icon_heart,
                    ),
                    contentDescription = stringResource(
                        if (favorite) Res.string.album_remove_favorite else Res.string.album_add_favorite,
                        track.title,
                    ),
                    tint = if (favorite) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(17.dp),
                )
            }
            Box {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { moreMenuExpanded = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(CoreRes.drawable.icon_vertialcal_more),
                        contentDescription = stringResource(Res.string.album_track_more_actions, track.title),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 12.dp, y = 28.dp),
                ) {
                    DesignContextMenu(
                        expanded = moreMenuExpanded,
                        onDismissRequest = { moreMenuExpanded = false },
                        compact = true,
                        items = buildList {
                            add(
                                DesignContextMenuItem(
                                    label = Res.string.album_play,
                                    icon = CoreRes.drawable.icon_play,
                                    onClick = onPlay,
                                ),
                            )
                            if (track.canDownload) {
                                add(
                                    DesignContextMenuItem(
                                        label = Res.string.album_download,
                                        icon = CoreRes.drawable.icon_download,
                                        onClick = onDownload,
                                    ),
                                )
                            }
                        },
                    )
                }
            }
        }
        DesignListDivider(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

private fun AlbumTrackItem.trackLabel(fallbackNumber: Int): String = buildString {
    if (discNumber != null && discNumber > 1) {
        append("$discNumber.")
    }
    append(trackNumber ?: fallbackNumber)
}

@Composable
private fun albumDurationLabel(durationMs: Long): String {
    val totalSeconds = (durationMs / 1_000).coerceAtLeast(0)
    val hours = totalSeconds / 3_600
    val minutes = totalSeconds / 60 % 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 && minutes > 0 -> stringResource(
            Res.string.album_duration_hours_minutes,
            hours,
            minutes,
        )
        hours > 0 -> stringResource(Res.string.album_duration_hours, hours)
        minutes > 0 && seconds > 0 -> stringResource(
            Res.string.album_duration_minutes_seconds,
            minutes,
            seconds,
        )
        minutes > 0 -> stringResource(Res.string.album_duration_minutes, minutes)
        else -> stringResource(Res.string.album_duration_seconds, seconds)
    }
}
