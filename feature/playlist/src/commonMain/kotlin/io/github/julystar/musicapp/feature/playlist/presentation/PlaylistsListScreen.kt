package io.github.julystar.musicapp.feature.playlist.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.components.DesignEmptyState
import io.github.julystar.musicapp.core.presentation.components.LocalDesignBottomContentInset
import io.github.julystar.musicapp.core.presentation.media.ArtworkImage
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.julystar.musicapp.core.presentation.transition.playlistArtworkSharedElement
import musicapp.feature.playlist.generated.resources.Res
import musicapp.feature.playlist.generated.resources.cover_default_image
import musicapp.feature.playlist.generated.resources.icon_adjust
import musicapp.feature.playlist.generated.resources.icon_drag
import musicapp.feature.playlist.generated.resources.icon_plus
import musicapp.feature.playlist.generated.resources.icon_yes
import musicapp.feature.playlist.generated.resources.playlist_add
import musicapp.feature.playlist.generated.resources.playlist_adjust
import musicapp.feature.playlist.generated.resources.playlist_create
import musicapp.feature.playlist.generated.resources.playlist_done
import musicapp.feature.playlist.generated.resources.playlist_empty
import musicapp.feature.playlist.generated.resources.playlist_empty_message
import musicapp.feature.playlist.generated.resources.playlist_list_count
import musicapp.feature.playlist.generated.resources.playlist_list_title
import musicapp.feature.playlist.generated.resources.playlist_summary
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ScrollMoveMode
import sh.calvin.reorderable.rememberReorderableLazyGridState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PlaylistsListScreen(
    state: PlaylistsListState,
    onAction: (PlaylistsListAction) -> Unit,
) {
    val spacing = DesignTokens.spacing
    val shapes = DesignTokens.shapes

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val horizontalPadding = if (maxWidth < 600.dp) spacing.pageCompact else spacing.pageExpanded

        if (state.isEmpty) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                DesignEmptyState(
                    title = stringResource(Res.string.playlist_empty),
                    message = stringResource(Res.string.playlist_empty_message),
                    marker = "P",
                    action = {
                        Box(
                            modifier = Modifier
                                .heightIn(min = DesignTokens.adaptive.minimumTouchTarget)
                                .clip(RoundedCornerShape(shapes.full))
                                .clickable { onAction(PlaylistsListAction.CreatePlaylist) }
                                .background(MiuixTheme.colorScheme.primary)
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = stringResource(Res.string.playlist_create),
                                color = MiuixTheme.colorScheme.onPrimary,
                                style = MiuixTheme.textStyles.button,
                            )
                        }
                    },
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.background),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPadding, vertical = 18.dp),
                ) {
                    TopAppBar(
                        title = stringResource(Res.string.playlist_list_title),
                        subtitle = stringResource(Res.string.playlist_list_count, state.playlists.size),
                        actions = {
                            IconButton(
                                enabled = state.mode != PlaylistsListMode.Adjust,
                                onClick = { onAction(PlaylistsListAction.ToggleMode) },
                            ) {
                                Icon(
                                    painterResource(Res.drawable.icon_adjust),
                                    stringResource(Res.string.playlist_adjust),
                                )
                            }
                            IconButton(
                                enabled = state.mode != PlaylistsListMode.Adjust,
                                onClick = { onAction(PlaylistsListAction.CreatePlaylist) },
                            ) {
                                Icon(
                                    painterResource(Res.drawable.icon_plus),
                                    stringResource(Res.string.playlist_add),
                                )
                            }
                        },
                    )
                    GridPlaylists(
                        playlists = state.playlists,
                        mode = state.mode,
                        onAction = onAction,
                    )
                }
                if (state.mode == PlaylistsListMode.Adjust) {
                    FloatingActionButton(
                        onClick = { onAction(PlaylistsListAction.SetModeNormal) },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(spacing.xl),
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.icon_yes),
                            tint = Color.White,
                            contentDescription = stringResource(Res.string.playlist_done),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GridPlaylists(
    playlists: List<PlaylistListItem>,
    mode: PlaylistsListMode,
    onAction: (PlaylistsListAction) -> Unit,
) {
    val bottomContentInset = LocalDesignBottomContentInset.current
    val lazyGridState = rememberLazyGridState()
    val reorderableLazyListState = rememberReorderableLazyGridState(
        lazyGridState = lazyGridState,
        scrollMoveMode = ScrollMoveMode.INSERT,
    ) { from, to ->
        onAction(PlaylistsListAction.MovePlaylist(from.index, to.index))
    }

    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        columns = GridCells.FixedSize(172.dp),
        horizontalArrangement = Arrangement.Center,
        state = lazyGridState,
        contentPadding = PaddingValues(bottom = 12.dp + bottomContentInset),
    ) {
        itemsIndexed(
            playlists,
            key = { index, playlist -> playlist.lazyListKey(index) },
        ) { index, playlist ->
            ReorderableItem(
                reorderableLazyListState,
                key = playlist.lazyListKey(index),
            ) {
                PlaylistItem(playlist = playlist, mode = mode, onAction = onAction)
            }
        }
    }
}

internal fun PlaylistListItem.lazyListKey(index: Int): String = "playlist-list-$index-$id"

@Composable
private fun ReorderableCollectionItemScope.PlaylistItem(
    playlist: PlaylistListItem,
    mode: PlaylistsListMode,
    onAction: (PlaylistsListAction) -> Unit,
) {
    val shapes = DesignTokens.shapes

    Box(
        Modifier.then(
            if (mode == PlaylistsListMode.Adjust) {
                Modifier.draggableHandle()
            } else {
                Modifier.clickable { onAction(PlaylistsListAction.NavigateToPlaylist(playlist.id)) }
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp, 8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Box(
                modifier = Modifier
                    .playlistArtworkSharedElement(playlist.id)
                    .clip(RoundedCornerShape(shapes.md))
                    .background(MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    .size(136.dp),
            ) {
                if (playlist.cover == null) {
                    Image(
                        modifier = Modifier.fillMaxSize(),
                        painter = painterResource(Res.drawable.cover_default_image),
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                    )
                } else {
                    ArtworkImage(
                        modifier = Modifier.fillMaxSize(),
                        artwork = playlist.cover,
                    )
                }
            }
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = playlist.title,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(
                    Res.string.playlist_summary,
                    playlist.musicCount,
                    playlist.durationLabel,
                ),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
            )
        }
        if (mode == PlaylistsListMode.Adjust) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(24.dp)
                    .clip(RoundedCornerShape(shapes.xxs))
                    .background(MiuixTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    modifier = Modifier.size(12.dp),
                    painter = painterResource(Res.drawable.icon_drag),
                    tint = Color.White,
                    contentDescription = stringResource(Res.string.playlist_adjust),
                )
            }
        }
    }
}
