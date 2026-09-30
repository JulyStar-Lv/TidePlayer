@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package io.github.julystar.musicapp.feature.playlist.presentation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.presentation.components.DesktopCollectionToolbar
import io.github.julystar.musicapp.core.presentation.components.DesktopBackButton
import io.github.julystar.musicapp.core.presentation.components.DesktopPlaybackActionPill
import io.github.julystar.musicapp.core.presentation.components.LiquidGlassOverlayScene
import io.github.julystar.musicapp.core.presentation.components.desktopToolbarCapsule
import io.github.julystar.musicapp.core.presentation.media.ArtworkImage
import io.github.julystar.musicapp.core.presentation.platform.rememberPlatformWindowFocused
import io.github.julystar.musicapp.core.presentation.theme.DesignPalette
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.julystar.musicapp.core.presentation.theme.LocalDesignIsDarkTheme
import musicapp.core.presentation.generated.resources.Res as CoreRes
import musicapp.core.presentation.generated.resources.icon_pencil
import musicapp.core.presentation.generated.resources.icon_star
import musicapp.core.presentation.generated.resources.icon_player_download
import musicapp.core.presentation.generated.resources.icon_player_more
import musicapp.core.presentation.generated.resources.icon_play
import musicapp.core.presentation.generated.resources.icon_shuffle
import musicapp.core.presentation.generated.resources.icon_star_filled
import musicapp.feature.playlist.generated.resources.Res
import musicapp.feature.playlist.generated.resources.icon_playlist_favorites
import musicapp.feature.playlist.generated.resources.playlist_add_favorite
import musicapp.feature.playlist.generated.resources.playlist_back
import musicapp.feature.playlist.generated.resources.playlist_desktop_artist
import musicapp.feature.playlist.generated.resources.playlist_desktop_duration
import musicapp.feature.playlist.generated.resources.playlist_desktop_no_results
import musicapp.feature.playlist.generated.resources.playlist_desktop_order
import musicapp.feature.playlist.generated.resources.playlist_desktop_play
import musicapp.feature.playlist.generated.resources.playlist_desktop_search_favorites
import musicapp.feature.playlist.generated.resources.playlist_desktop_shuffle
import musicapp.feature.playlist.generated.resources.playlist_desktop_song
import musicapp.feature.playlist.generated.resources.playlist_desktop_sort
import musicapp.feature.playlist.generated.resources.playlist_detail_summary
import musicapp.feature.playlist.generated.resources.playlist_context_menu_edit
import musicapp.feature.playlist.generated.resources.playlist_context_menu_import
import musicapp.feature.playlist.generated.resources.playlist_context_menu_remove
import musicapp.feature.playlist.generated.resources.playlist_desktop_album
import musicapp.feature.playlist.generated.resources.playlist_download
import musicapp.feature.playlist.generated.resources.playlist_empty_list
import musicapp.feature.playlist.generated.resources.playlist_remove_favorite
import musicapp.feature.playlist.generated.resources.playlist_track_more_actions
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class PlaylistSort { PlaylistOrder, Title, Artist, Album, Duration }

@Composable
internal fun DesktopPlaylistScreen(
    state: PlaylistState,
    currentPlayingTrackId: Long?,
    favoriteTrackIds: Set<Long>,
    onToggleFavorite: (Long) -> Unit,
    onAction: (PlaylistAction) -> Unit,
    onShuffle: () -> Unit,
    onBack: (() -> Unit)? = null,
    editable: Boolean = true,
    isPlaying: Boolean = false,
) {
    var query by remember(state.playlistId) { mutableStateOf("") }
    var sort by remember(state.playlistId) { mutableStateOf(PlaylistSort.PlaylistOrder) }
    var selectedTrackId by remember(state.playlistId) { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = from.index - 2
        val toIndex = to.index - 2
        if (fromIndex in state.tracks.indices && toIndex in state.tracks.indices) {
            onAction(PlaylistAction.MoveTrack(fromIndex, toIndex))
        }
    }
    val tracks = remember(state.tracks, query, sort) {
        val filtered = state.tracks.filter { track ->
            query.isBlank() || listOf(track.title, track.artist.orEmpty(), track.albumName.orEmpty())
                .any { it.contains(query.trim(), ignoreCase = true) }
        }
        when (sort) {
            PlaylistSort.PlaylistOrder -> filtered
            PlaylistSort.Title -> filtered.sortedBy { it.title.lowercase() }
            PlaylistSort.Artist -> filtered.sortedBy { it.artist.orEmpty().lowercase() }
            PlaylistSort.Album -> filtered.sortedBy { it.albumName.orEmpty().lowercase() }
            PlaylistSort.Duration -> filtered.sortedBy { it.durationMs ?: 0L }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val trackSpace = (maxWidth - 246.dp).coerceAtLeast(180.dp)
        val artistWidth = if (state.isFavorites) 210.dp else trackSpace * 0.17f
        val albumWidth = if (state.isFavorites) 0.dp else trackSpace * 0.50f
        LiquidGlassOverlayScene(
            modifier = Modifier.fillMaxSize(),
            captureBackdrop = false,
            backdropContent = {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(top = DesignTokens.adaptive.compactHeaderHeight, bottom = 86.dp),
                ) {
                    item("playlist-hero") {
                        PlaylistHero(state, onPlay = { onAction(PlaylistAction.PlayAll) }, onShuffle = onShuffle)
                    }
                    item("playlist-header") { PlaylistTableHeader(artistWidth, albumWidth, onSort = { sort = it }) }
                    if (tracks.isEmpty()) {
                        item("playlist-empty") {
                            Text(
                                stringResource(if (query.isBlank()) Res.string.playlist_empty_list else Res.string.playlist_desktop_no_results),
                                color = playlistForeground().copy(alpha = 0.52f),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(start = 50.dp, top = 24.dp),
                            )
                        }
                    }
                    items(tracks, key = { it.id }) { track ->
                        val row: @Composable (Modifier) -> Unit = { dragModifier ->
                            PlaylistTrackRow(
                                track = track,
                                dragModifier = dragModifier,
                                selected = selectedTrackId == track.id,
                                playing = currentPlayingTrackId == track.id,
                                isPlaying = isPlaying,
                                favorite = track.id in favoriteTrackIds,
                                onSelect = { selectedTrackId = track.id },
                                onPlay = { onAction(PlaylistAction.PlayTrack(track.id)) },
                                onToggleFavorite = { onToggleFavorite(track.id) },
                                onDownload = track.mediaId?.let { { onAction(PlaylistAction.DownloadTrack(track)) } },
                                onRemove = if (editable) { { onAction(PlaylistAction.RemoveTrack(track.id)) } } else null,
                                artistWidth = artistWidth,
                                albumWidth = albumWidth,
                            )
                        }
                        if (editable && query.isBlank() && sort == PlaylistSort.PlaylistOrder) {
                            ReorderableItem(reorderableState, key = track.id) {
                                row(Modifier.draggableHandle())
                            }
                        } else {
                            row(Modifier)
                        }
                    }
                    item("playlist-summary") {
                        Text(
                            stringResource(Res.string.playlist_detail_summary, state.tracks.size, playlistDurationLabel(state.durationMs)),
                            color = playlistForeground().copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 40.dp, top = 24.dp),
                        )
                    }
                }
            },
            overlayContent = {
                PlaylistToolbar(
                    title = state.title,
                    query = query,
                    onQueryChange = { query = it },
                    sort = sort,
                    onSort = { sort = it },
                    canDownload = state.tracks.any { it.mediaId != null },
                    onDownloadAll = { state.tracks.filter { it.mediaId != null }.forEach { onAction(PlaylistAction.DownloadTrack(it)) } },
                    onPlay = { onAction(PlaylistAction.PlayAll) },
                    onBack = onBack,
                    isFavorites = state.isFavorites,
                    editable = editable,
                    onAction = onAction,
                )
            },
        )
    }
}

@Composable
private fun PlaylistToolbar(
    title: String,
    query: String,
    onQueryChange: (String) -> Unit,
    sort: PlaylistSort,
    onSort: (PlaylistSort) -> Unit,
    canDownload: Boolean,
    onDownloadAll: () -> Unit,
    onPlay: () -> Unit,
    onBack: (() -> Unit)?,
    isFavorites: Boolean,
    editable: Boolean,
    onAction: (PlaylistAction) -> Unit,
) {
    var moreOpen by remember(title) { mutableStateOf(false) }
    var sortOpen by remember(title) { mutableStateOf(false) }
    DesktopCollectionToolbar(
        title = title,
        showTitle = false,
        navigationIcon = onBack?.let { back ->
            { DesktopBackButton(stringResource(Res.string.playlist_back), back) }
        },
        query = query,
        searchHint = stringResource(Res.string.playlist_desktop_search_favorites),
        onQueryChange = onQueryChange,
        sortDescription = stringResource(Res.string.playlist_desktop_sort),
        onSortClick = { sortOpen = true },
        sortMenu = {
            PlaylistMenu(sortOpen, { sortOpen = false }, PlaylistSort.entries.filter { !isFavorites || it != PlaylistSort.Album }.map { value ->
                val label = when (value) {
                    PlaylistSort.PlaylistOrder -> stringResource(Res.string.playlist_desktop_order)
                    PlaylistSort.Title -> stringResource(Res.string.playlist_desktop_song)
                    PlaylistSort.Artist -> stringResource(Res.string.playlist_desktop_artist)
                    PlaylistSort.Album -> stringResource(Res.string.playlist_desktop_album)
                    PlaylistSort.Duration -> stringResource(Res.string.playlist_desktop_duration)
                }
                (if (sort == value) "✓  $label" else label) to { sortOpen = false; onSort(value) }
            })
        },
        toolbarActions = {
            if (editable) {
                Box(Modifier.size(36.dp).desktopToolbarCapsule(CircleShape)) {
                    PlaylistToolbarButton(stringResource(Res.string.playlist_context_menu_edit), CoreRes.drawable.icon_pencil) {
                        onAction(PlaylistAction.EditPlaylist)
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
            Row(
                Modifier.height(36.dp).desktopToolbarCapsule(RoundedCornerShape(18.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaylistToolbarButton(stringResource(Res.string.playlist_download), CoreRes.drawable.icon_player_download, canDownload, onDownloadAll)
                Box {
                    PlaylistToolbarButton(stringResource(Res.string.playlist_track_more_actions, title), CoreRes.drawable.icon_player_more) { moreOpen = true }
                    PlaylistMenu(moreOpen, { moreOpen = false }, buildList {
                        add(stringResource(Res.string.playlist_desktop_play) to { moreOpen = false; onPlay() })
                        if (canDownload) add(stringResource(Res.string.playlist_download) to { moreOpen = false; onDownloadAll() })
                        if (editable) {
                            add(stringResource(Res.string.playlist_context_menu_import) to {
                                moreOpen = false; onAction(PlaylistAction.ImportTracks)
                            })
                            add(stringResource(Res.string.playlist_context_menu_edit) to {
                                moreOpen = false; onAction(PlaylistAction.EditPlaylist)
                            })
                            add(stringResource(Res.string.playlist_context_menu_remove) to {
                                moreOpen = false; onAction(PlaylistAction.OpenRemoveDialog)
                            })
                        }
                    })
                }
            }
            Spacer(Modifier.width(10.dp))
        },
    )
}

@Composable
private fun PlaylistToolbarButton(description: String, icon: DrawableResource, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), null, tint = playlistForeground().copy(alpha = if (enabled) 0.75f else 0.28f),
            modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PlaylistHero(state: PlaylistState, onPlay: () -> Unit, onShuffle: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(315.dp).padding(start = 40.dp, end = 40.dp)) {
        val artworkSize = if (maxWidth < 650.dp) 230.dp else 270.dp
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Box(
                Modifier.size(artworkSize).shadow(7.dp, RoundedCornerShape(7.dp))
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (state.isFavorites) Color.White else playlistForeground().copy(alpha = 0.06f))
                    .testTag(if (state.isFavorites) "desktop-favorites-hero-cover" else "desktop-playlist-hero-cover"),
                contentAlignment = Alignment.Center,
            ) {
                if (state.isFavorites) {
                    Icon(painterResource(Res.drawable.icon_playlist_favorites), null,
                        tint = DesignPalette.FavoriteRed, modifier = Modifier.size(artworkSize * 0.63f))
                } else {
                    ArtworkImage(Modifier.fillMaxSize(), state.cover, contentScale = ContentScale.Crop)
                }
            }
            Column(Modifier.weight(1f).height(artworkSize).padding(top = if (state.isFavorites) 104.dp else 82.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.title, color = playlistForeground(), fontSize = 27.sp, lineHeight = 32.sp,
                        fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (state.isFavorites) {
                        Spacer(Modifier.width(8.dp))
                        Icon(painterResource(CoreRes.drawable.icon_star_filled), null,
                            tint = DesignPalette.FavoriteRed, modifier = Modifier.size(15.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DesktopPlaybackActionPill(stringResource(Res.string.playlist_desktop_play), CoreRes.drawable.icon_play,
                        state.tracks.isNotEmpty(), onPlay)
                    DesktopPlaybackActionPill(stringResource(Res.string.playlist_desktop_shuffle), CoreRes.drawable.icon_shuffle,
                        state.tracks.isNotEmpty(), onShuffle)
                }
            }
        }
    }
}

@Composable
private fun PlaylistTableHeader(artistWidth: Dp, albumWidth: Dp, onSort: (PlaylistSort) -> Unit) {
    val headerColor = playlistForeground().copy(alpha = 0.55f)
    val dividerColor = playlistForeground().copy(alpha = 0.14f)
    Row(Modifier.fillMaxWidth().height(24.dp).padding(start = 52.dp, end = 37.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.playlist_desktop_song), Modifier.weight(1f)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSort(PlaylistSort.Title) },
            color = headerColor, fontSize = 12.sp)
        Box(Modifier.width(0.5.dp).height(16.dp).background(dividerColor))
        Text(stringResource(Res.string.playlist_desktop_artist), Modifier.width(artistWidth + 12.dp).padding(start = 12.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSort(PlaylistSort.Artist) },
            color = headerColor, fontSize = 12.sp)
        if (albumWidth > 0.dp) {
            Box(Modifier.width(0.5.dp).height(16.dp).background(dividerColor))
            Text(stringResource(Res.string.playlist_desktop_album), Modifier.width(albumWidth).padding(start = 12.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSort(PlaylistSort.Album) },
                color = headerColor, fontSize = 12.sp)
        }
        Box(Modifier.width(0.5.dp).height(16.dp).background(dividerColor))
        Text(stringResource(Res.string.playlist_desktop_duration), Modifier.width(105.dp).padding(start = 30.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSort(PlaylistSort.Duration) },
            color = headerColor, fontSize = 12.sp)
    }
}

@Composable
private fun PlaylistTrackRow(
    track: PlaylistTrackItem,
    selected: Boolean,
    playing: Boolean,
    isPlaying: Boolean,
    favorite: Boolean,
    onSelect: () -> Unit,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    artistWidth: Dp,
    albumWidth: Dp,
    dragModifier: Modifier,
) {
    var menuOpen by remember(track.id) { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused = rememberPlatformWindowFocused()
    val selectedAndFocused = selected && focused
    val ink = if (selectedAndFocused) Color.White else playlistForeground()
    Column(Modifier.testTag("desktop-playlist-track-${track.id}")) {
        Row(Modifier.fillMaxWidth().height(55.dp).padding(start = 20.dp, end = 30.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(24.dp).clickable(onClick = onToggleFavorite), contentAlignment = Alignment.CenterStart) {
                if (albumWidth == 0.dp || favorite || hovered || selected) Icon(
                    painterResource(if (favorite) CoreRes.drawable.icon_star_filled else CoreRes.drawable.icon_star),
                    stringResource(if (favorite) Res.string.playlist_remove_favorite else Res.string.playlist_add_favorite, track.title),
                    tint = if (selectedAndFocused) ink else if (favorite) DesignPalette.FavoriteRed else ink.copy(alpha = 0.35f),
                    modifier = Modifier.size(13.dp))
            }
            Spacer(Modifier.width(1.dp))
            Row(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp))
                .background(when {
                    selectedAndFocused -> MiuixTheme.colorScheme.primary
                    selected -> playlistForeground().copy(alpha = 0.13f)
                    hovered -> playlistForeground().copy(alpha = 0.10f)
                    playing -> playlistForeground().copy(alpha = 0.06f)
                    else -> Color.Transparent
                })
                .hoverable(interaction)
                .combinedClickable(interactionSource = interaction, indication = null,
                    onClick = onSelect, onDoubleClick = onPlay)
                .padding(horizontal = 7.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)).then(dragModifier).clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center) {
                    ArtworkImage(
                        artwork = track.albumId?.let(Artwork::LibraryAlbum) ?: Artwork.LibraryTrack(track.id),
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    )
                    if (playing && isPlaying) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                        PlaylistPlayingIndicator(Modifier.testTag("desktop-playlist-playing-${track.id}"))
                    } else if (hovered) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.16f)))
                        Icon(painterResource(CoreRes.drawable.icon_play), null, tint = Color.White,
                            modifier = Modifier.size(15.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = ink, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (albumWidth == 0.dp) track.albumName?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = ink.copy(alpha = 0.52f), fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(track.artist.orEmpty(), Modifier.width(artistWidth), color = ink.copy(alpha = 0.65f),
                    fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (albumWidth > 0.dp) {
                    Text(track.albumName.orEmpty(), Modifier.width(albumWidth).padding(start = 12.dp),
                        color = ink.copy(alpha = 0.65f), fontSize = 13.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
                    if (hovered && onDownload != null) {
                        Icon(painterResource(CoreRes.drawable.icon_player_download),
                            stringResource(Res.string.playlist_download), tint = DesignPalette.FavoriteRed,
                            modifier = Modifier.size(16.dp).clickable(onClick = onDownload))
                    }
                }
                Text(playlistTrackDuration(track.durationMs), Modifier.width(45.dp), color = ink.copy(alpha = 0.65f),
                    fontSize = 12.sp, textAlign = TextAlign.Start)
                Box(Modifier.width(30.dp), contentAlignment = Alignment.CenterEnd) {
                    Icon(painterResource(CoreRes.drawable.icon_player_more),
                        stringResource(Res.string.playlist_track_more_actions, track.title),
                        tint = if (hovered) DesignPalette.FavoriteRed else ink.copy(alpha = 0.55f),
                        modifier = Modifier.size(16.dp).clickable { menuOpen = true })
                    PlaylistMenu(menuOpen, { menuOpen = false }, buildList {
                        add(stringResource(Res.string.playlist_desktop_play) to { menuOpen = false; onPlay() })
                        if (onDownload != null) add(stringResource(Res.string.playlist_download) to { menuOpen = false; onDownload() })
                        add(stringResource(if (favorite) Res.string.playlist_remove_favorite else Res.string.playlist_add_favorite, track.title)
                            to { menuOpen = false; onToggleFavorite() })
                        if (onRemove != null) add(stringResource(Res.string.playlist_context_menu_remove)
                            to { menuOpen = false; onRemove() })
                    })
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(start = 88.dp, end = 37.dp).height(0.5.dp)
            .background(ink.copy(alpha = 0.10f)))
    }
}

@Composable
private fun PlaylistPlayingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "playlist-playing")
    // Four 3 pt capsules with 1 pt gaps, sharing a fixed baseline, as in Music.app.
    val patterns = remember {
        listOf(
            listOf(5f, 10f, 8f, 11f, 6f, 10f, 5f),
            listOf(12f, 15f, 10f, 14f, 9f, 12f, 12f),
            listOf(15f, 8f, 12f, 6f, 14f, 8f, 15f),
            listOf(9f, 12f, 7f, 10f, 12f, 9f, 9f),
        )
    }
    Row(modifier.height(16.dp), horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.Bottom) {
        patterns.forEachIndexed { index, heights ->
            val duration = 1_200 + index * 130
            val height by transition.animateFloat(
                initialValue = heights.first(),
                targetValue = heights.last(),
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = duration
                        heights.forEachIndexed { frame, value ->
                            value at duration * frame / heights.lastIndex using FastOutSlowInEasing
                        }
                    },
                    repeatMode = RepeatMode.Restart,
                ),
                label = "playlist-playing-bar-$index",
            )
            Box(Modifier.width(3.dp).height(height.dp)
                .clip(RoundedCornerShape(2.dp)).background(Color.White))
        }
    }
}

@Composable
private fun PlaylistMenu(show: Boolean, onDismiss: () -> Unit, entries: List<Pair<String, () -> Unit>>) {
    if (!show) return
    Popup(alignment = Alignment.TopEnd, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(
            Modifier.width(188.dp).shadow(14.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(if (LocalDesignIsDarkTheme.current) Color(0xFF353537) else Color(0xFFF8F8F8))
                .border(0.5.dp, playlistForeground().copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                .padding(5.dp),
        ) {
            entries.forEach { (label, action) ->
                Text(label, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp))
                    .clickable(onClick = action).padding(horizontal = 9.dp, vertical = 5.dp),
                    color = playlistForeground(), fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

private fun playlistTrackDuration(durationMs: Long?): String {
    val seconds = ((durationMs ?: 0L) / 1_000L).coerceAtLeast(0L)
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

@Composable
private fun playlistForeground(): Color = if (LocalDesignIsDarkTheme.current) Color.White else Color.Black
