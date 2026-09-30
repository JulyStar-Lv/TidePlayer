@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package io.github.julystar.musicapp.feature.playlist.presentation

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.presentation.components.DesktopCollectionToolbar
import io.github.julystar.musicapp.core.presentation.components.LiquidGlassOverlayScene
import io.github.julystar.musicapp.core.presentation.media.ArtworkImage
import io.github.julystar.musicapp.core.presentation.platform.rememberPlatformWindowFocused
import io.github.julystar.musicapp.core.presentation.theme.LocalDesignIsDarkTheme
import io.github.julystar.musicapp.core.presentation.theme.DesignPalette
import musicapp.core.presentation.generated.resources.Res as CoreRes
import musicapp.core.presentation.generated.resources.icon_check
import musicapp.core.presentation.generated.resources.icon_chevron_right
import musicapp.core.presentation.generated.resources.icon_play
import musicapp.core.presentation.generated.resources.icon_star
import musicapp.feature.playlist.generated.resources.Res
import musicapp.feature.playlist.generated.resources.icon_playlist_favorites
import musicapp.feature.playlist.generated.resources.icon_playlist_placeholder
import musicapp.feature.playlist.generated.resources.icon_playlist_sort_order
import musicapp.feature.playlist.generated.resources.playlist_create
import musicapp.feature.playlist.generated.resources.playlist_desktop_all
import musicapp.feature.playlist.generated.resources.playlist_desktop_ascending
import musicapp.feature.playlist.generated.resources.playlist_desktop_descending
import musicapp.feature.playlist.generated.resources.playlist_desktop_favorites
import musicapp.feature.playlist.generated.resources.playlist_desktop_favorites_only
import musicapp.feature.playlist.generated.resources.playlist_desktop_no_results
import musicapp.feature.playlist.generated.resources.playlist_desktop_not_favorited
import musicapp.feature.playlist.generated.resources.playlist_desktop_open
import musicapp.feature.playlist.generated.resources.playlist_desktop_play
import musicapp.feature.playlist.generated.resources.playlist_desktop_recently_added
import musicapp.feature.playlist.generated.resources.playlist_desktop_recently_played
import musicapp.feature.playlist.generated.resources.playlist_desktop_search
import musicapp.feature.playlist.generated.resources.playlist_desktop_sort
import musicapp.feature.playlist.generated.resources.playlist_desktop_title
import musicapp.feature.playlist.generated.resources.playlist_desktop_type
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class DesktopPlaylistSort { Title, RecentlyAdded, PlaylistType }

internal fun shownDesktopPlaylists(
    playlists: List<PlaylistListItem>,
    query: String,
    sort: DesktopPlaylistSort,
    descending: Boolean,
): List<PlaylistListItem> {
    val comparator = when (sort) {
        DesktopPlaylistSort.RecentlyAdded -> compareBy<PlaylistListItem> { it.createdAt ?: 0L }
        else -> compareBy { it.title.lowercase() }
    }.thenBy { it.id }
    return playlists.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
        .sortedWith(if (descending) comparator.reversed() else comparator)
}

@Composable
internal fun DesktopPlaylistsScreen(
    state: PlaylistsListState,
    favoriteCount: Int,
    onOpenPlaylist: (Long) -> Unit,
    onOpenFavorites: (() -> Unit)?,
    onCreatePlaylist: () -> Unit,
    onPlayPlaylist: (Long) -> Unit,
    onPlayFavorites: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(DesktopPlaylistSort.Title) }
    var descending by remember { mutableStateOf(false) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var selectedKey by remember { mutableStateOf<String?>(null) }
    var createMenuOpen by remember { mutableStateOf(false) }
    var createMenuPosition by remember { mutableStateOf(IntOffset.Zero) }
    val favoritesTitle = stringResource(Res.string.playlist_desktop_favorites)
    val shown = remember(state.playlists, query, sort, descending, favoritesOnly) {
        if (favoritesOnly) emptyList() else shownDesktopPlaylists(state.playlists, query, sort, descending)
    }
    val showFavorites = onOpenFavorites != null && !favoritesOnly &&
        (query.isBlank() || favoritesTitle.contains(query.trim(), ignoreCase = true))

    LiquidGlassOverlayScene(
        modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background),
        backdropContent = {
            BoxWithConstraints(Modifier.fillMaxSize().onPointerEvent(PointerEventType.Press) { event ->
                if (event.buttons.isSecondaryPressed && event.changes.none { it.isConsumed }) {
                    val position = event.changes.first().position
                    createMenuPosition = IntOffset(position.x.toInt(), position.y.toInt())
                    createMenuOpen = true
                }
            }) {
                val columns = when {
                    maxWidth < 570.dp -> 3
                    maxWidth < 1000.dp -> 4
                    else -> 5
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 62.dp, bottom = 86.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(33.dp),
                ) {
                    if (showFavorites) {
                        item(key = "favorites") {
                            DesktopPlaylistCard(
                                title = favoritesTitle,
                                artwork = null,
                                isFavorites = true,
                                isSelected = selectedKey == "favorites",
                                canPlay = favoriteCount > 0,
                                onSelect = { selectedKey = "favorites" },
                                onOpen = onOpenFavorites,
                                onPlay = onPlayFavorites,
                            )
                        }
                    }
                    itemsIndexed(shown, key = { index, playlist -> playlist.lazyListKey(index) }) { index, playlist ->
                        val key = playlist.lazyListKey(index)
                        DesktopPlaylistCard(
                            title = playlist.title,
                            artwork = playlist.cover,
                            isFavorites = false,
                            isSelected = selectedKey == key,
                            canPlay = playlist.musicCount.toLongOrNull()?.let { it > 0 } == true,
                            onSelect = { selectedKey = key },
                            onOpen = { onOpenPlaylist(playlist.id) },
                            onPlay = { onPlayPlaylist(playlist.id) },
                        )
                    }
                }
                if (!showFavorites && shown.isEmpty()) {
                    Text(
                        stringResource(Res.string.playlist_desktop_no_results),
                        color = desktopForeground().copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                DesktopPlaylistMenu(createMenuOpen, { createMenuOpen = false },
                    alignment = Alignment.TopStart, offset = createMenuPosition) {
                    PlaylistMenuRow(stringResource(Res.string.playlist_create)) {
                        createMenuOpen = false
                        onCreatePlaylist()
                    }
                }
            }
        },
        overlayContent = {
            DesktopPlaylistsToolbar(
                query = query,
                onQueryChange = { query = it },
                sort = sort,
                descending = descending,
                favoritesOnly = favoritesOnly,
                onFavoritesOnly = { favoritesOnly = it },
                onSort = { sort = it },
                onDescending = { descending = it },
            )
        },
    )
}

@Composable
private fun DesktopPlaylistsToolbar(
    query: String,
    onQueryChange: (String) -> Unit,
    sort: DesktopPlaylistSort,
    descending: Boolean,
    favoritesOnly: Boolean,
    onFavoritesOnly: (Boolean) -> Unit,
    onSort: (DesktopPlaylistSort) -> Unit,
    onDescending: (Boolean) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var submenuOpen by remember { mutableStateOf(false) }
    DesktopCollectionToolbar(
        title = stringResource(Res.string.playlist_desktop_all),
        query = query,
        searchHint = stringResource(Res.string.playlist_desktop_search),
        onQueryChange = onQueryChange,
        sortDescription = stringResource(Res.string.playlist_desktop_sort),
        onSortClick = {
            menuOpen = !menuOpen
            submenuOpen = false
        },
        sortMenu = {
            DesktopPlaylistMenu(menuOpen, { menuOpen = false; submenuOpen = false }) {
                PlaylistMenuRow(stringResource(Res.string.playlist_desktop_all), checked = !favoritesOnly) {
                    onFavoritesOnly(false); menuOpen = false
                }
                PlaylistMenuRow(stringResource(Res.string.playlist_desktop_favorites_only), checked = favoritesOnly) {
                    onFavoritesOnly(true); menuOpen = false
                }
                PlaylistMenuSeparator()
                Box {
                    PlaylistMenuRow(
                        stringResource(Res.string.playlist_desktop_sort),
                        icon = Res.drawable.icon_playlist_sort_order,
                        submenu = true,
                        onHover = { submenuOpen = true },
                    ) { submenuOpen = true }
                    DesktopPlaylistMenu(
                        submenuOpen,
                        { submenuOpen = false },
                        alignment = Alignment.TopStart,
                        offset = IntOffset(336, 0),
                    ) {
                        DesktopPlaylistSort.entries.forEach { option ->
                            if (option == DesktopPlaylistSort.PlaylistType) {
                                PlaylistMenuRow(stringResource(Res.string.playlist_desktop_recently_played), enabled = false) {}
                            }
                            val label = when (option) {
                                DesktopPlaylistSort.Title -> stringResource(Res.string.playlist_desktop_title)
                                DesktopPlaylistSort.RecentlyAdded -> stringResource(Res.string.playlist_desktop_recently_added)
                                DesktopPlaylistSort.PlaylistType -> stringResource(Res.string.playlist_desktop_type)
                            }
                            PlaylistMenuRow(label, checked = sort == option) {
                                onSort(option); submenuOpen = false; menuOpen = false
                            }
                        }
                        PlaylistMenuSeparator()
                        PlaylistMenuRow(stringResource(Res.string.playlist_desktop_ascending), checked = !descending) {
                            onDescending(false); submenuOpen = false; menuOpen = false
                        }
                        PlaylistMenuRow(stringResource(Res.string.playlist_desktop_descending), checked = descending) {
                            onDescending(true); submenuOpen = false; menuOpen = false
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun DesktopPlaylistCard(
    title: String,
    artwork: Artwork?,
    isFavorites: Boolean,
    isSelected: Boolean,
    canPlay: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    val focused = rememberPlatformWindowFocused()
    val isDark = LocalDesignIsDarkTheme.current
    var hovered by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var menuPosition by remember { mutableStateOf(IntOffset.Zero) }
    LaunchedEffect(focused) { if (!focused) hovered = false }
    val shape = RoundedCornerShape(7.dp)
    val selection = if (focused) MiuixTheme.colorScheme.primary else desktopForeground().copy(alpha = 0.22f)
    Column(verticalArrangement = Arrangement.spacedBy(7.5.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(shape)
                .background(if (isFavorites) Color.White else if (isDark) Color(0xFF28282A) else Color(0xFFEDEEF0))
                .then(if (isFavorites) Modifier.testTag("desktop-favorites-cover") else Modifier)
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false }
                .onPointerEvent(PointerEventType.Press) { event ->
                    if (event.buttons.isSecondaryPressed) {
                        val position = event.changes.first().position
                        menuPosition = IntOffset(position.x.toInt(), position.y.toInt())
                        onSelect()
                        menuOpen = true
                        event.changes.forEach { it.consume() }
                    }
                }
                .semantics { contentDescription = title; selected = isSelected }
                .combinedClickable(indication = null, interactionSource = remember { MutableInteractionSource() },
                    onClick = onSelect, onDoubleClick = onOpen)
                .border(if (isSelected) 5.dp else 0.5.dp,
                    if (isSelected) selection else desktopForeground().copy(alpha = 0.08f), shape),
        ) {
            if (isFavorites) {
                PlaylistPlaceholder(isFavorites = true)
            } else {
                ArtworkImage(Modifier.fillMaxSize().padding(if (isSelected) 5.dp else 0.dp), artwork,
                    contentScale = ContentScale.Crop, fallback = { PlaylistPlaceholder(isFavorites = false) })
            }
            if (hovered && focused && canPlay) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(12.dp).size(29.dp).clip(CircleShape)
                        .background(Color(0xFF767680).copy(alpha = 0.46f)).clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(CoreRes.drawable.icon_play), stringResource(Res.string.playlist_desktop_play),
                        tint = Color.White, modifier = Modifier.size(12.dp).offset(y = 1.dp))
                }
            }
            DesktopPlaylistMenu(menuOpen, { menuOpen = false },
                alignment = Alignment.TopStart, offset = menuPosition) {
                PlaylistMenuRow(stringResource(Res.string.playlist_desktop_play), icon = CoreRes.drawable.icon_play, enabled = canPlay) {
                    menuOpen = false; onPlay()
                }
                PlaylistMenuSeparator()
                PlaylistMenuRow(stringResource(Res.string.playlist_desktop_open)) { menuOpen = false; onOpen() }
            }
        }
        Row(Modifier.fillMaxWidth().height(20.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 12.sp, lineHeight = 16.sp, color = desktopForeground(),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (!isFavorites) {
                Spacer(Modifier.width(10.dp))
                Icon(painterResource(CoreRes.drawable.icon_star), stringResource(Res.string.playlist_desktop_not_favorited),
                    tint = MiuixTheme.colorScheme.primary.copy(alpha = 0.6f), modifier = Modifier.size(11.dp).semantics { disabled() })
            }
        }
    }
}

@Composable
private fun PlaylistPlaceholder(isFavorites: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            painterResource(if (isFavorites) Res.drawable.icon_playlist_favorites else Res.drawable.icon_playlist_placeholder),
            contentDescription = null,
            tint = if (isFavorites) DesignPalette.FavoriteRed else desktopForeground().copy(alpha = 0.3f),
            modifier = Modifier.size(maxWidth * if (isFavorites) 0.63f else 0.54f)
                .offset(y = if (isFavorites) (-1).dp else 0.dp),
        )
    }
}

@Composable
private fun desktopForeground(): Color = if (LocalDesignIsDarkTheme.current) Color.White else Color.Black

@Composable
private fun DesktopPlaylistMenu(
    show: Boolean,
    onDismiss: () -> Unit,
    alignment: Alignment = Alignment.TopStart,
    offset: IntOffset = IntOffset(-8, 80),
    content: @Composable () -> Unit,
) {
    if (!show) return
    val isDark = LocalDesignIsDarkTheme.current
    val shape = RoundedCornerShape(10.dp)
    Popup(alignment, offset, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(
            Modifier.width(170.dp).shadow(14.dp, shape).clip(shape)
                .background(if (isDark) Color(0xF22C2C2E) else Color(0xF2F8F8F8))
                .border(0.5.dp, desktopForeground().copy(alpha = 0.12f), shape).padding(vertical = 3.dp),
        ) { content() }
    }
}

@Composable
private fun PlaylistMenuRow(
    label: String,
    checked: Boolean = false,
    icon: DrawableResource? = null,
    enabled: Boolean = true,
    submenu: Boolean = false,
    onHover: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val ink = if (!enabled) desktopForeground().copy(alpha = 0.3f) else if (hovered) Color.White else desktopForeground()
    Row(
        Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 4.dp).clip(RoundedCornerShape(5.dp))
            .background(if (hovered && enabled) MiuixTheme.colorScheme.primary else Color.Transparent)
            .hoverable(interaction).onPointerEvent(PointerEventType.Enter) { onHover?.invoke() }
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
            val leading = if (checked) CoreRes.drawable.icon_check else icon
            if (leading != null) Icon(painterResource(leading), null, tint = ink,
                modifier = Modifier.size(if (submenu) 14.dp else 10.dp))
        }
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, color = ink, maxLines = 1, modifier = Modifier.weight(1f))
        if (submenu) Icon(painterResource(CoreRes.drawable.icon_chevron_right), null, tint = ink, modifier = Modifier.size(10.dp))
    }
}

@Composable
private fun PlaylistMenuSeparator() {
    Box(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 5.dp).height(0.5.dp)
        .background(desktopForeground().copy(alpha = 0.13f)))
}
