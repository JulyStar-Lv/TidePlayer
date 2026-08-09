package io.github.julystar.musicapp.feature.playlist.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.julystar.musicapp.core.presentation.components.BottomBarSpacer
import io.github.julystar.musicapp.core.presentation.components.ConfirmDialog
import io.github.julystar.musicapp.core.presentation.components.DesignCardSurface
import io.github.julystar.musicapp.core.presentation.components.DesignCheckbox
import io.github.julystar.musicapp.core.presentation.components.DesignContextMenu
import io.github.julystar.musicapp.core.presentation.components.DesignContextMenuItem
import io.github.julystar.musicapp.core.presentation.components.DesignIconButton
import io.github.julystar.musicapp.core.presentation.components.DesignIconButtonColors
import io.github.julystar.musicapp.core.presentation.components.DesignIconButtonSize
import io.github.julystar.musicapp.core.presentation.components.DesignIconButtonVariant
import io.github.julystar.musicapp.core.presentation.components.DesignListDivider
import io.github.julystar.musicapp.core.presentation.components.DesignStickyGlassActionBar
import io.github.julystar.musicapp.core.presentation.media.ArtworkImage
import io.github.julystar.musicapp.core.presentation.media.FavoritesPlaylistArtwork
import io.github.julystar.musicapp.core.presentation.theme.DesignFontFamilies
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.julystar.musicapp.core.presentation.transition.playlistArtworkSharedElement
import kotlinx.coroutines.launch
import musicapp.core.presentation.generated.resources.Res as CoreRes
import musicapp.core.presentation.generated.resources.cover_default_playlist_image
import musicapp.core.presentation.generated.resources.icon_deleteseep
import musicapp.core.presentation.generated.resources.icon_download
import musicapp.core.presentation.generated.resources.icon_drag
import musicapp.core.presentation.generated.resources.icon_heart
import musicapp.core.presentation.generated.resources.icon_heart_filled
import musicapp.core.presentation.generated.resources.icon_locate_fixed
import musicapp.core.presentation.generated.resources.icon_more_horizontal
import musicapp.core.presentation.generated.resources.icon_ok
import musicapp.core.presentation.generated.resources.icon_pencil
import musicapp.core.presentation.generated.resources.icon_play
import musicapp.core.presentation.generated.resources.icon_setting
import musicapp.core.presentation.generated.resources.icon_vertialcal_more
import musicapp.feature.playlist.generated.resources.Res
import musicapp.feature.playlist.generated.resources.playlist_add_favorite
import musicapp.feature.playlist.generated.resources.playlist_back
import musicapp.feature.playlist.generated.resources.playlist_context_menu_edit
import musicapp.feature.playlist.generated.resources.playlist_context_menu_import
import musicapp.feature.playlist.generated.resources.playlist_context_menu_remove
import musicapp.feature.playlist.generated.resources.playlist_default_title
import musicapp.feature.playlist.generated.resources.playlist_deselect_all
import musicapp.feature.playlist.generated.resources.playlist_detail_summary
import musicapp.feature.playlist.generated.resources.playlist_download
import musicapp.feature.playlist.generated.resources.playlist_duration_hours
import musicapp.feature.playlist.generated.resources.playlist_duration_hours_minutes
import musicapp.feature.playlist.generated.resources.playlist_duration_minutes
import musicapp.feature.playlist.generated.resources.playlist_duration_minutes_seconds
import musicapp.feature.playlist.generated.resources.playlist_duration_seconds
import musicapp.feature.playlist.generated.resources.playlist_edit_tracks
import musicapp.feature.playlist.generated.resources.playlist_empty_list
import musicapp.feature.playlist.generated.resources.playlist_finish_editing
import musicapp.feature.playlist.generated.resources.playlist_locate_current
import musicapp.feature.playlist.generated.resources.playlist_play_all
import musicapp.feature.playlist.generated.resources.playlist_remove_dialog_text
import musicapp.feature.playlist.generated.resources.playlist_remove_favorite
import musicapp.feature.playlist.generated.resources.playlist_select_all
import musicapp.feature.playlist.generated.resources.playlist_track_more_actions
import musicapp.feature.playlist.generated.resources.playlist_unknown_artist
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val TrackListStartIndex = 2

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistScreen(
    state: PlaylistState,
    currentPlayingTrackId: Long?,
    favoriteTrackIds: Set<Long>,
    scaffoldPadding: PaddingValues,
    onToggleFavorite: (Long) -> Unit,
    onAction: (PlaylistAction) -> Unit,
    editable: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var editing by remember(state.playlistId) { mutableStateOf(false) }
    var selectedTrackIds by remember(state.playlistId) { mutableStateOf(emptySet<Long>()) }
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
    val allSelected = state.tracks.isNotEmpty() && state.tracks.all { track -> track.id in selectedTrackIds }
    val title = state.title.ifBlank { stringResource(Res.string.playlist_default_title) }
    val topBarActions: (@Composable () -> Unit)? = if (editable) {
        {
            PlaylistTopBarActions(
                title = title,
                onAction = onAction,
            )
        }
    } else {
        null
    }
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState = listState) { from, to ->
        val fromIndex = from.index - TrackListStartIndex
        val toIndex = to.index - TrackListStartIndex
        if (fromIndex in state.tracks.indices && toIndex in state.tracks.indices) {
            onAction(PlaylistAction.MoveTrack(fromIndex = fromIndex, toIndex = toIndex))
        }
    }

    BoxWithConstraints(
        modifier = modifier
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    start = horizontalPadding,
                    top = DesignTokens.adaptive.compactHeaderHeight,
                    end = horizontalPadding,
                ),
            ) {
                item(key = "playlist-hero") {
                    PlaylistHero(
                        state = state,
                        compact = compact,
                        titleAlpha = pageTitleAlpha,
                    )
                }
                stickyHeader(key = "playlist-actions") {
                    PlaylistActionBar(
                        editing = editing,
                        allSelected = allSelected,
                        selectedCount = selectedTrackIds.size,
                        canPlay = state.tracks.isNotEmpty(),
                        canLocate = currentTrackIndex >= 0,
                        editable = editable,
                        onPlayAll = { onAction(PlaylistAction.PlayAll) },
                        onLocateCurrent = {
                            if (currentTrackIndex >= 0) {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(currentTrackIndex + TrackListStartIndex)
                                }
                            }
                        },
                        onToggleEditing = {
                            editing = !editing
                            selectedTrackIds = emptySet()
                        },
                        onToggleSelectAll = {
                            selectedTrackIds = if (allSelected) {
                                emptySet()
                            } else {
                                state.tracks.mapTo(mutableSetOf()) { track -> track.id }
                            }
                        },
                    )
                }
                if (state.tracks.isEmpty()) {
                    item(key = "playlist-empty") {
                        EmptyPlaylist(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 280.dp)
                                .padding(top = 12.dp),
                        )
                    }
                } else {
                    items(
                        count = state.tracks.size,
                        key = { index -> state.tracks[index].lazyListKey(index) },
                    ) { index ->
                        val item = state.tracks[index]
                        if (editing) {
                            ReorderableItem(
                                reorderableLazyListState,
                                key = item.lazyListKey(index),
                            ) { _ ->
                                PlaylistEditingTrackRow(
                                    item = item,
                                    selected = item.id in selectedTrackIds,
                                    onSelectedChange = { selected ->
                                        selectedTrackIds = if (selected) {
                                            selectedTrackIds + item.id
                                        } else {
                                            selectedTrackIds - item.id
                                        }
                                    },
                                )
                            }
                        } else {
                            PlaylistTrackRow(
                                item = item,
                                trackNumber = index + 1,
                                favorite = item.id in favoriteTrackIds,
                                onPlay = { onAction(PlaylistAction.PlayTrack(item.id)) },
                                onToggleFavorite = { onToggleFavorite(item.id) },
                                onDownload = { onAction(PlaylistAction.DownloadTrack(item)) },
                                onRemove = { onAction(PlaylistAction.RemoveTrack(item.id)) },
                                removable = editable,
                            )
                        }
                    }
                }
                item(key = "playlist-bottom-space") {
                    BottomBarSpacer(showMiniPlayer = true, scaffoldPadding = scaffoldPadding)
                }
            }
            DesignStickyGlassActionBar(
                title = title,
                collapseFraction = actionBarProgress,
                onNavigateBack = { onAction(PlaylistAction.NavigateBack) },
                backContentDescription = stringResource(Res.string.playlist_back),
                centerTitle = true,
                actions = topBarActions,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
    if (editable) {
        RemovePlaylistDialog(state = state, onAction = onAction)
    }
}

@Composable
private fun PlaylistTopBarActions(
    title: String,
    onAction: (PlaylistAction) -> Unit,
) {
    var moreMenuExpanded by remember { mutableStateOf(false) }

    Box {
        DesignIconButton(
            size = DesignIconButtonSize.Touch,
            variant = DesignIconButtonVariant.Default,
            painter = painterResource(CoreRes.drawable.icon_more_horizontal),
            contentDescription = stringResource(Res.string.playlist_track_more_actions, title),
            onClick = { moreMenuExpanded = true },
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 14.dp, y = 36.dp),
        ) {
            DesignContextMenu(
                expanded = moreMenuExpanded,
                onDismissRequest = { moreMenuExpanded = false },
                compact = true,
                items = listOf(
                    DesignContextMenuItem(
                        label = Res.string.playlist_context_menu_import,
                        icon = CoreRes.drawable.icon_download,
                        onClick = { onAction(PlaylistAction.ImportTracks) },
                    ),
                    DesignContextMenuItem(
                        label = Res.string.playlist_context_menu_edit,
                        icon = CoreRes.drawable.icon_setting,
                        onClick = { onAction(PlaylistAction.EditPlaylist) },
                    ),
                    DesignContextMenuItem(
                        label = Res.string.playlist_context_menu_remove,
                        icon = CoreRes.drawable.icon_deleteseep,
                        isError = true,
                        onClick = { onAction(PlaylistAction.OpenRemoveDialog) },
                    ),
                ),
            )
        }
    }
}

@Composable
private fun PlaylistHero(
    state: PlaylistState,
    compact: Boolean,
    titleAlpha: Float,
) {
    val artworkSize = if (compact) 144.dp else 260.dp
    val artworkRadius = if (compact) 18.dp else 24.dp
    val titleSize = if (compact) 24.sp else 36.sp
    val titleLineHeight = if (compact) 29.sp else 42.sp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (compact) 14.dp else 20.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 28.dp),
        verticalAlignment = if (compact) Alignment.CenterVertically else Alignment.Bottom,
    ) {
        if (state.isFavorites) {
            FavoritesPlaylistArtwork(
                size = artworkSize,
                cornerRadius = artworkRadius,
                modifier = Modifier.playlistArtworkSharedElement(state.playlistId),
            )
        } else {
            Box(
                modifier = Modifier
                    .playlistArtworkSharedElement(state.playlistId)
                    .size(artworkSize)
                    .clip(RoundedCornerShape(artworkRadius))
                    .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
            ) {
                if (state.cover == null) {
                    Image(
                        modifier = Modifier.fillMaxSize(),
                        painter = painterResource(CoreRes.drawable.cover_default_playlist_image),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    ArtworkImage(modifier = Modifier.fillMaxSize(), artwork = state.cover)
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (compact) 0.dp else 4.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp),
        ) {
            Text(
                text = state.title.ifBlank { stringResource(Res.string.playlist_default_title) },
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
            Text(
                text = stringResource(
                    Res.string.playlist_detail_summary,
                    state.tracks.size,
                    playlistDurationLabel(state.durationMs),
                ),
                style = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PlaylistActionBar(
    editing: Boolean,
    allSelected: Boolean,
    selectedCount: Int,
    canPlay: Boolean,
    canLocate: Boolean,
    editable: Boolean,
    onPlayAll: () -> Unit,
    onLocateCurrent: () -> Unit,
    onToggleEditing: () -> Unit,
    onToggleSelectAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(MiuixTheme.colorScheme.background.copy(alpha = 0.96f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (editing) {
            DesignCheckbox(
                checked = allSelected,
                onCheckedChange = { onToggleSelectAll() },
            )
            Text(
                text = buildString {
                    append(
                        stringResource(
                            if (allSelected) Res.string.playlist_deselect_all else Res.string.playlist_select_all,
                        ),
                    )
                    if (selectedCount > 0) append(" ($selectedCount)")
                },
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.body2.copy(fontSize = 14.sp, lineHeight = 18.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        } else {
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
                    text = stringResource(Res.string.playlist_play_all),
                    color = MiuixTheme.colorScheme.primary.copy(alpha = if (canPlay) 1f else 0.35f),
                    style = MiuixTheme.textStyles.body2.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Box(modifier = Modifier.weight(1f))
        DesignIconButton(
            size = DesignIconButtonSize.Medium,
            variant = DesignIconButtonVariant.Default,
            painter = painterResource(CoreRes.drawable.icon_locate_fixed),
            contentDescription = stringResource(Res.string.playlist_locate_current),
            colors = DesignIconButtonColors(
                buttonBg = MiuixTheme.colorScheme.surfaceContainerHigh,
                iconTint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            ),
            enabled = canLocate,
            onClick = onLocateCurrent,
        )
        if (editable) {
            Box(modifier = Modifier.width(4.dp))
            DesignIconButton(
                size = DesignIconButtonSize.Touch,
                variant = if (editing) DesignIconButtonVariant.Primary else DesignIconButtonVariant.Surface,
                painter = painterResource(
                    if (editing) CoreRes.drawable.icon_ok else CoreRes.drawable.icon_pencil,
                ),
                contentDescription = stringResource(
                    if (editing) Res.string.playlist_finish_editing else Res.string.playlist_edit_tracks,
                ),
                colors = if (editing) null else DesignIconButtonColors(iconTint = MiuixTheme.colorScheme.primary),
                onClick = onToggleEditing,
            )
        }
    }
}

@Composable
private fun PlaylistTrackRow(
    item: PlaylistTrackItem,
    trackNumber: Int,
    favorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
    onRemove: () -> Unit,
    removable: Boolean,
) {
    var moreMenuExpanded by remember(item.id, item.sortOrder) { mutableStateOf(false) }
    val subtitle = item.trackSubtitle()
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
                .padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.width(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = trackNumber.toString(),
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
                    text = item.title,
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
                        if (favorite) Res.string.playlist_remove_favorite else Res.string.playlist_add_favorite,
                        item.title,
                    ),
                    tint = if (favorite) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(17.dp),
                )
            }
            if (item.mediaId != null || removable) {
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
                            contentDescription = stringResource(Res.string.playlist_track_more_actions, item.title),
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
                                if (item.mediaId != null) {
                                    add(
                                        DesignContextMenuItem(
                                            label = Res.string.playlist_download,
                                            icon = CoreRes.drawable.icon_download,
                                            onClick = onDownload,
                                        ),
                                    )
                                }
                                if (removable) {
                                    add(
                                        DesignContextMenuItem(
                                            label = Res.string.playlist_context_menu_remove,
                                            icon = CoreRes.drawable.icon_deleteseep,
                                            isError = true,
                                            onClick = onRemove,
                                        ),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
        DesignListDivider(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ReorderableCollectionItemScope.PlaylistEditingTrackRow(
    item: PlaylistTrackItem,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(MiuixTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DesignCheckbox(
                checked = selected,
                onCheckedChange = onSelectedChange,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.title,
                    color = MiuixTheme.colorScheme.onSurface,
                    style = MiuixTheme.textStyles.body1.copy(fontSize = 14.sp, lineHeight = 18.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.trackSubtitle(),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .draggableHandle(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(CoreRes.drawable.icon_drag),
                    contentDescription = stringResource(Res.string.playlist_edit_tracks),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        DesignListDivider(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun RemovePlaylistDialog(
    state: PlaylistState,
    onAction: (PlaylistAction) -> Unit,
) {
    ConfirmDialog(
        open = state.isRemoveDialogOpen,
        onConfirm = { onAction(PlaylistAction.ConfirmRemovePlaylist) },
        onCancel = { onAction(PlaylistAction.CloseRemoveDialog) },
    ) {
        Text(text = "${stringResource(Res.string.playlist_remove_dialog_text)} \"${state.title}\"")
    }
}

@Composable
private fun EmptyPlaylist(modifier: Modifier = Modifier) {
    DesignCardSurface(
        modifier = modifier,
        cornerRadius = DesignTokens.shapes.lg,
        contentPadding = PaddingValues(24.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(Res.string.playlist_empty_list),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PlaylistTrackItem.trackSubtitle(): String {
    val artistLabel = artist?.takeIf { it.isNotBlank() }
        ?: stringResource(Res.string.playlist_unknown_artist)
    return listOfNotNull(
        artistLabel,
        albumName?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
}

@Composable
private fun playlistDurationLabel(durationMs: Long): String {
    val totalSeconds = (durationMs / 1_000).coerceAtLeast(0)
    val hours = totalSeconds / 3_600
    val minutes = totalSeconds / 60 % 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 && minutes > 0 -> stringResource(
            Res.string.playlist_duration_hours_minutes,
            hours,
            minutes,
        )
        hours > 0 -> stringResource(Res.string.playlist_duration_hours, hours)
        minutes > 0 && seconds > 0 -> stringResource(
            Res.string.playlist_duration_minutes_seconds,
            minutes,
            seconds,
        )
        minutes > 0 -> stringResource(Res.string.playlist_duration_minutes, minutes)
        else -> stringResource(Res.string.playlist_duration_seconds, seconds)
    }
}

internal fun PlaylistTrackItem.lazyListKey(index: Int): String =
    "playlist-track-$sortOrder-$index-$id"
