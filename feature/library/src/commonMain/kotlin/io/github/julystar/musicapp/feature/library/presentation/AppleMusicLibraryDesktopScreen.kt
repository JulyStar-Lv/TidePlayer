@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package io.github.julystar.musicapp.feature.library.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.model.LibraryAlbumItem
import io.github.julystar.musicapp.core.domain.model.LibraryArtistItem
import io.github.julystar.musicapp.core.domain.model.LibraryTrackItem
import io.github.julystar.musicapp.core.presentation.media.ArtworkImage
import io.github.julystar.musicapp.core.presentation.platform.rememberPlatformWindowFocused
import io.github.julystar.musicapp.core.presentation.theme.LocalDesignIsDarkTheme
import musicapp.core.presentation.generated.resources.Res as CoreRes
import musicapp.core.presentation.generated.resources.icon_check
import musicapp.core.presentation.generated.resources.icon_chevron_down
import musicapp.core.presentation.generated.resources.icon_chevron_up
import musicapp.core.presentation.generated.resources.icon_download
import musicapp.core.presentation.generated.resources.icon_artist_microphone
import musicapp.core.presentation.generated.resources.icon_more_horizontal
import musicapp.core.presentation.generated.resources.icon_pause
import musicapp.core.presentation.generated.resources.icon_play
import musicapp.core.presentation.generated.resources.icon_search
import musicapp.core.presentation.generated.resources.icon_shuffle
import musicapp.core.presentation.generated.resources.icon_speaker
import musicapp.core.presentation.generated.resources.icon_sort
import musicapp.core.presentation.generated.resources.icon_star
import musicapp.core.presentation.generated.resources.icon_star_filled
import musicapp.feature.library.generated.resources.Res
import musicapp.feature.library.generated.resources.library_add_favorite
import musicapp.feature.library.generated.resources.library_category_albums
import musicapp.feature.library.generated.resources.library_category_artists
import musicapp.feature.library.generated.resources.library_category_songs
import musicapp.feature.library.generated.resources.library_desktop_album
import musicapp.feature.library.generated.resources.library_desktop_all_artists
import musicapp.feature.library.generated.resources.library_desktop_artist
import musicapp.feature.library.generated.resources.library_desktop_artists_title
import musicapp.feature.library.generated.resources.library_desktop_more
import musicapp.feature.library.generated.resources.library_desktop_play
import musicapp.feature.library.generated.resources.library_desktop_search_albums
import musicapp.feature.library.generated.resources.library_desktop_search_artists
import musicapp.feature.library.generated.resources.library_desktop_search_songs
import musicapp.feature.library.generated.resources.library_desktop_sort
import musicapp.feature.library.generated.resources.library_desktop_time
import musicapp.feature.library.generated.resources.library_desktop_title
import musicapp.feature.library.generated.resources.library_desktop_year
import musicapp.feature.library.generated.resources.library_download
import musicapp.feature.library.generated.resources.library_remove_favorite
import musicapp.feature.library.generated.resources.library_track_count
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val ApplePlaybackIndicatorLight = Color(0xFF3A7CED)
private val AppleContentPadding = 20.dp
private val AppleToolbarHeight = 52.dp
private val AppleSongRowHeight = 22.dp
private val AppleSongStatusWidth = 16.dp
private val AppleSongTitleWidth = 196.dp
private val AppleSongMoreWidth = 28.dp
private val AppleSongArtistWidth = 87.dp
private val AppleSongAlbumWidth = 162.dp
private val AppleSongDurationWidth = 39.dp
private val AppleSongYearWidth = 48.dp
private val AppleSongFavoriteWidth = 28.dp

private fun Modifier.appleToolbarShadow(shape: Shape, isDark: Boolean): Modifier =
    if (isDark) {
        this
    } else {
        dropShadow(
            shape = shape,
            shadow = Shadow(
                radius = 36.dp,
                offset = DpOffset(0.dp, 6.dp),
                color = Color.Black.copy(alpha = 0.04f),
            ),
        ).dropShadow(
            shape = shape,
            shadow = Shadow(
                radius = 16.dp,
                offset = DpOffset(0.dp, 5.dp),
                color = Color.Black.copy(alpha = 0.04f),
            ),
        )
    }

private data class CompactMenuEntry(
    val text: String,
    val icon: org.jetbrains.compose.resources.DrawableResource?,
    val separatorBefore: Boolean = false,
    val onClick: () -> Unit,
)

private enum class DesktopSort { Title, Artist, Album, Duration, Year }

@Composable
internal fun AppleMusicLibraryDesktopScreen(
    state: LibraryState,
    section: LibraryDesktopSection,
    currentPlayingTrackId: Long?,
    onNavigateToAlbum: (Long) -> Unit,
    onAction: (LibraryAction) -> Unit,
) {
    var query by remember(section) { mutableStateOf("") }
    var sort by remember(section) {
        mutableStateOf(if (section == LibraryDesktopSection.Artists) DesktopSort.Artist else DesktopSort.Title)
    }
    var sortDescending by remember(section) { mutableStateOf(false) }
    val title = when (section) {
        LibraryDesktopSection.Songs -> stringResource(Res.string.library_category_songs)
        LibraryDesktopSection.Albums -> stringResource(Res.string.library_category_albums)
        LibraryDesktopSection.Artists -> stringResource(Res.string.library_desktop_artists_title)
    }
    val searchHint = when (section) {
        LibraryDesktopSection.Songs -> stringResource(Res.string.library_desktop_search_songs)
        LibraryDesktopSection.Albums -> stringResource(Res.string.library_desktop_search_albums)
        LibraryDesktopSection.Artists -> stringResource(Res.string.library_desktop_search_artists)
    }
    val favoriteIds = state.favorites.dataOrNull.orEmpty().mapTo(mutableSetOf()) { it.id }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background),
    ) {
        DesktopLibraryToolbar(
            title = title,
            section = section,
            sort = sort,
            query = query,
            searchHint = searchHint,
            onQueryChange = { query = it },
            onSort = { selected ->
                if (sort == selected) sortDescending = !sortDescending
                else {
                    sort = selected
                    sortDescending = false
                }
            },
        )
        when (section) {
            LibraryDesktopSection.Songs -> DesktopSongs(
                tracks = state.tracks,
                albums = state.albums,
                query = query,
                sort = sort,
                sortDescending = sortDescending,
                currentPlayingTrackId = currentPlayingTrackId,
                favoriteIds = favoriteIds,
                onSort = { selected ->
                    if (sort == selected) sortDescending = !sortDescending
                    else {
                        sort = selected
                        sortDescending = false
                    }
                },
                onAction = onAction,
            )
            LibraryDesktopSection.Albums -> DesktopAlbums(
                albums = state.albums,
                tracks = state.tracks,
                query = query,
                sort = sort,
                sortDescending = sortDescending,
                onNavigateToAlbum = onNavigateToAlbum,
                onAction = onAction,
            )
            LibraryDesktopSection.Artists -> DesktopArtists(
                artists = state.artists,
                albums = state.albums,
                tracks = state.tracks,
                query = query,
                sortDescending = sortDescending,
                currentPlayingTrackId = currentPlayingTrackId,
                favoriteIds = favoriteIds,
                onNavigateToAlbum = onNavigateToAlbum,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun DesktopLibraryToolbar(
    title: String,
    section: LibraryDesktopSection,
    sort: DesktopSort,
    query: String,
    searchHint: String,
    onQueryChange: (String) -> Unit,
    onSort: (DesktopSort) -> Unit,
) {
    var sortMenuOpen by remember(section) { mutableStateOf(false) }
    val sortOptions = when (section) {
        LibraryDesktopSection.Songs -> DesktopSort.entries
        LibraryDesktopSection.Albums -> listOf(DesktopSort.Title, DesktopSort.Artist, DesktopSort.Year)
        LibraryDesktopSection.Artists -> listOf(DesktopSort.Artist)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AppleToolbarHeight)
            .background(
                MiuixTheme.colorScheme.background.copy(alpha = 0.42f)
            )
            .padding(start = AppleContentPadding, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = if (LocalDesignIsDarkTheme.current) {
                MiuixTheme.colorScheme.onBackground
            } else {
                Color.Black.copy(alpha = 0.69f)
            },
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        Box {
            DesktopRoundButton(
                description = stringResource(Res.string.library_desktop_sort),
                onClick = { sortMenuOpen = true },
            ) {
                Icon(
                    painter = painterResource(CoreRes.drawable.icon_sort),
                    contentDescription = null,
                    tint = appleDesktopForeground(),
                    modifier = Modifier.size(17.dp),
                )
            }
            CompactContextMenu(
                show = sortMenuOpen,
                onDismiss = { sortMenuOpen = false },
                entries = sortOptions.map { option ->
                    CompactMenuEntry(
                        text = when (option) {
                            DesktopSort.Title -> stringResource(Res.string.library_desktop_title)
                            DesktopSort.Artist -> stringResource(Res.string.library_desktop_artist)
                            DesktopSort.Album -> stringResource(Res.string.library_desktop_album)
                            DesktopSort.Duration -> stringResource(Res.string.library_desktop_time)
                            DesktopSort.Year -> stringResource(Res.string.library_desktop_year)
                        },
                        icon = if (option == sort) CoreRes.drawable.icon_check else null,
                        onClick = {
                            sortMenuOpen = false
                            onSort(option)
                        },
                    )
                },
            )
        }
        Spacer(Modifier.width(8.dp))
        DesktopSearchField(
            value = query,
            hint = searchHint,
            onValueChange = onQueryChange,
        )
    }
}

@Composable
private fun DesktopSearchField(
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
) {
    val isDark = LocalDesignIsDarkTheme.current
    val foreground = if (isDark) Color.White else Color.Black
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .width(196.dp)
            .height(36.dp)
            .appleToolbarShadow(shape, isDark)
            .clip(shape)
            .background(if (isDark) Color.White.copy(alpha = 0.075f) else Color.White.copy(alpha = 0.55f))
            .border(
                0.5.dp,
                if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.86f),
                shape,
            )
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(CoreRes.drawable.icon_search),
            contentDescription = null,
            tint = foreground.copy(alpha = 0.48f),
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MiuixTheme.textStyles.body2.copy(color = foreground.copy(alpha = 0.86f), fontSize = 13.sp),
            cursorBrush = SolidColor(appleAccent()),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = hint,
                            color = foreground.copy(alpha = 0.45f),
                            fontSize = 13.sp,
                            maxLines = 1,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun DesktopRoundButton(
    description: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val isDark = LocalDesignIsDarkTheme.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val background = if (isDark) {
        Color.White.copy(alpha = when { pressed -> 0.14f; hovered -> 0.10f; else -> 0.075f })
    } else {
        Color.White.copy(alpha = when { pressed -> 0.70f; hovered -> 0.60f; else -> 0.55f })
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .appleToolbarShadow(CircleShape, isDark)
            .clip(CircleShape)
            .background(background)
            .border(0.5.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.86f), CircleShape)
            .hoverable(interaction)
            .semantics { contentDescription = description }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DesktopSongs(
    tracks: List<LibraryTrackItem>,
    albums: List<LibraryAlbumItem>,
    query: String,
    sort: DesktopSort,
    sortDescending: Boolean,
    currentPlayingTrackId: Long?,
    favoriteIds: Set<Long>,
    onSort: (DesktopSort) -> Unit,
    onAction: (LibraryAction) -> Unit,
) {
    val years = remember(albums) { albums.associate { it.id to it.year } }
    val filtered = remember(tracks, query, sort, sortDescending) {
        val comparator = when (sort) {
            DesktopSort.Title -> compareBy<LibraryTrackItem> { it.title.lowercase() }
            DesktopSort.Artist -> compareBy { it.artist.orEmpty().lowercase() }
            DesktopSort.Album -> compareBy { it.albumName.orEmpty().lowercase() }
            DesktopSort.Duration -> compareBy { it.durationMs ?: 0L }
            DesktopSort.Year -> compareBy { years[it.albumId] ?: 0 }
        }.let { if (sortDescending) it.reversed() else it }
        tracks.filter { track ->
            query.isBlank() || listOf(track.title, track.artist.orEmpty(), track.albumName.orEmpty())
                .any { it.contains(query, ignoreCase = true) }
        }.sortedWith(comparator)
    }
    val filteredIds = remember(filtered) { filtered.map { it.id } }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var lastSelectedIndex by remember { mutableIntStateOf(-1) }

    Column(Modifier.fillMaxSize().padding(top = 2.dp)) {
        SongTableHeader(sort = sort, sortDescending = sortDescending, onSort = onSort)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 78.dp),
        ) {
            itemsIndexed(filtered, key = { index, item -> item.lazyListKey(index) }) { index, track ->
                DesktopSongRow(
                    track = track,
                    year = years[track.albumId],
                    index = index,
                    selected = track.id in selectedIds,
                    playing = track.id == currentPlayingTrackId,
                    favorite = track.id in favoriteIds,
                    onSelect = { shift, additive ->
                        selectedIds = updateDesktopSongSelection(
                            current = selectedIds,
                            orderedTrackIds = filteredIds,
                            clickedIndex = index,
                            lastSelectedIndex = lastSelectedIndex,
                            shift = shift,
                            additive = additive,
                        )
                        lastSelectedIndex = index
                    },
                    onPlay = { onAction(LibraryAction.PlayTrack(track.id)) },
                    onToggleFavorite = { onAction(LibraryAction.ToggleFavorite(track.id)) },
                    onDownload = track.mediaId?.let { { onAction(LibraryAction.DownloadTrack(track)) } },
                )
            }
        }
    }
}

@Composable
private fun SongTableHeader(sort: DesktopSort, sortDescending: Boolean, onSort: (DesktopSort) -> Unit) {
    val muted = appleDesktopForeground().copy(alpha = 0.90f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(21.dp)
            .padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(AppleSongStatusWidth))
        HeaderCell(
            stringResource(Res.string.library_desktop_title),
            AppleSongTitleWidth,
            muted,
            active = sort == DesktopSort.Title,
            showChevron = false,
        ) { onSort(DesktopSort.Title) }
        HeaderGap(AppleSongMoreWidth, active = sort == DesktopSort.Title, descending = sortDescending, color = muted)
        HeaderCell(stringResource(Res.string.library_desktop_artist), AppleSongArtistWidth, muted, active = sort == DesktopSort.Artist, descending = sortDescending) { onSort(DesktopSort.Artist) }
        HeaderCell(stringResource(Res.string.library_desktop_album), AppleSongAlbumWidth, muted, active = sort == DesktopSort.Album, descending = sortDescending) { onSort(DesktopSort.Album) }
        HeaderCell(stringResource(Res.string.library_desktop_time), AppleSongDurationWidth, muted, active = sort == DesktopSort.Duration, descending = sortDescending) { onSort(DesktopSort.Duration) }
        HeaderCell(stringResource(Res.string.library_desktop_year), AppleSongYearWidth, muted, active = sort == DesktopSort.Year, descending = sortDescending) { onSort(DesktopSort.Year) }
        HeaderGap(AppleSongFavoriteWidth, active = false, color = muted)
    }
}

@Composable
private fun HeaderGap(width: androidx.compose.ui.unit.Dp, active: Boolean, descending: Boolean = false, color: Color) {
    val activeColor = appleDesktopForeground().copy(alpha = 0.90f)
    Box(Modifier.width(width).fillMaxHeight(), contentAlignment = Alignment.Center) {
        if (active) {
            Icon(
                painterResource(if (descending) CoreRes.drawable.icon_chevron_down else CoreRes.drawable.icon_chevron_up),
                null,
                tint = activeColor,
                modifier = Modifier.size(11.dp),
            )
        }
        Box(Modifier.align(Alignment.CenterEnd).width(0.5.dp).height(18.dp).background(color.copy(alpha = 0.32f)))
    }
}

@Composable
private fun RowScope.HeaderCell(
    text: String,
    width: androidx.compose.ui.unit.Dp?,
    color: Color,
    active: Boolean = false,
    descending: Boolean = false,
    showChevron: Boolean = true,
    onClick: () -> Unit,
) {
    val textColor = if (active) appleDesktopForeground().copy(alpha = 0.90f) else color
    Box(Modifier.width(width ?: 0.dp).fillMaxHeight().clickable(onClick = onClick)) {
        Text(
            text = text,
            modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 4.dp),
            color = textColor,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
        if (active && showChevron) {
            Icon(
                painterResource(if (descending) CoreRes.drawable.icon_chevron_down else CoreRes.drawable.icon_chevron_up),
                null,
                tint = textColor,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 3.dp).size(11.dp),
            )
        }
        Box(Modifier.align(Alignment.CenterEnd).width(0.5.dp).height(18.dp).background(color.copy(alpha = 0.32f)))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DesktopSongRow(
    track: LibraryTrackItem,
    year: Int?,
    index: Int,
    selected: Boolean,
    playing: Boolean,
    favorite: Boolean,
    onSelect: (shift: Boolean, additive: Boolean) -> Unit,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: (() -> Unit)?,
) {
    val focused = rememberPlatformWindowFocused()
    val isDark = LocalDesignIsDarkTheme.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var shiftDown by remember { mutableStateOf(false) }
    var additiveDown by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(IntOffset.Zero) }
    val density = LocalDensity.current
    val selectionActive = focused || menuOpen
    val selectionColor = when {
        !selectionActive && selected -> if (isDark) Color.White.copy(alpha = 0.13f) else Color.Black.copy(alpha = 0.13f)
        selected -> appleAccent()
        hovered -> if (isDark) Color.White.copy(alpha = 0.075f) else Color.Black.copy(alpha = 0.065f)
        index % 2 == 1 -> if (isDark) Color.White.copy(alpha = 0.035f) else Color.Black.copy(alpha = 0.038f)
        else -> Color.Transparent
    }
    val textColor = if (selected && selectionActive) MiuixTheme.colorScheme.onPrimary else appleDesktopForeground()

    Box(Modifier.fillMaxWidth().height(AppleSongRowHeight)) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(selectionColor)
                .hoverable(interaction)
                .onPointerEvent(PointerEventType.Press) { event ->
                    shiftDown = event.keyboardModifiers.isShiftPressed
                    additiveDown = event.keyboardModifiers.isMetaPressed || event.keyboardModifiers.isCtrlPressed
                    if (event.buttons.isSecondaryPressed) {
                        event.changes.firstOrNull()?.position?.let { position ->
                            menuOffset = IntOffset(position.x.toInt(), position.y.toInt())
                        }
                        if (!selected) onSelect(false, false)
                        menuOpen = true
                    }
                }
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { onSelect(shiftDown, additiveDown) },
                    onDoubleClick = onPlay,
                )
                .padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(AppleSongStatusWidth), contentAlignment = Alignment.CenterStart) {
                if (playing) {
                    Icon(
                        painter = painterResource(CoreRes.drawable.icon_speaker),
                        contentDescription = null,
                        tint = when {
                            selected && selectionActive -> textColor
                            isDark -> appleAccent()
                            else -> ApplePlaybackIndicatorLight
                        },
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            SongCell(track.title, Modifier.width(AppleSongTitleWidth), textColor)
            Box(Modifier.width(AppleSongMoreWidth), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(CoreRes.drawable.icon_more_horizontal),
                    contentDescription = stringResource(Res.string.library_desktop_more),
                    tint = if (selected && selectionActive) textColor else appleAccent(),
                    modifier = Modifier.size(15.dp).clickable {
                        menuOffset = IntOffset(with(density) { 236.dp.roundToPx() }, 0)
                        menuOpen = true
                    },
                )
            }
            SongCell(track.artist.orEmpty(), Modifier.width(AppleSongArtistWidth), textColor)
            SongCell(track.albumName.orEmpty(), Modifier.width(AppleSongAlbumWidth), textColor)
            SongCell(formatDuration(track.durationMs), Modifier.width(AppleSongDurationWidth), textColor, align = TextAlign.Right)
            SongCell(year?.toString().orEmpty(), Modifier.width(AppleSongYearWidth), textColor)
            Box(Modifier.width(AppleSongFavoriteWidth), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(if (favorite) CoreRes.drawable.icon_star_filled else CoreRes.drawable.icon_star),
                    contentDescription = stringResource(
                        if (favorite) Res.string.library_remove_favorite else Res.string.library_add_favorite,
                    ),
                    tint = if (selected && selectionActive) textColor else if (favorite) appleAccent() else textColor.copy(alpha = 0.43f),
                    modifier = Modifier.size(if (favorite) 12.dp else 11.dp).clickable(onClick = onToggleFavorite),
                )
            }
        }
        DesktopTrackMenu(
            show = menuOpen,
            track = track,
            favorite = favorite,
            onDismiss = { menuOpen = false },
            onPlay = onPlay,
            onToggleFavorite = onToggleFavorite,
            onDownload = onDownload,
            offset = menuOffset,
        )
    }
}

@Composable
private fun SongCell(
    text: String,
    modifier: Modifier,
    color: Color,
    weight: FontWeight = FontWeight.Normal,
    align: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 4.dp),
        color = color,
        fontSize = 12.sp,
        lineHeight = 14.sp,
        fontWeight = weight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
    )
}

@Composable
private fun DesktopTrackMenu(
    show: Boolean,
    track: LibraryTrackItem,
    favorite: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: (() -> Unit)?,
    offset: IntOffset,
    alignment: Alignment = Alignment.TopStart,
    width: Dp = 176.dp,
) {
    CompactContextMenu(
        show = show,
        onDismiss = onDismiss,
        alignment = alignment,
        offset = offset,
        width = width,
        entries = buildList {
            if (onDownload != null) add(
                CompactMenuEntry(stringResource(Res.string.library_download), CoreRes.drawable.icon_download) {
                    onDismiss(); onDownload()
                },
            )
            add(
                CompactMenuEntry(
                    text = stringResource(Res.string.library_desktop_play),
                    icon = CoreRes.drawable.icon_play,
                    separatorBefore = onDownload != null,
                ) { onDismiss(); onPlay() },
            )
            add(
                CompactMenuEntry(
                    text = stringResource(if (favorite) Res.string.library_remove_favorite else Res.string.library_add_favorite),
                    icon = if (favorite) CoreRes.drawable.icon_star_filled else CoreRes.drawable.icon_star,
                    separatorBefore = true,
                ) { onDismiss(); onToggleFavorite() },
            )
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DesktopAlbums(
    albums: List<LibraryAlbumItem>,
    tracks: List<LibraryTrackItem>,
    query: String,
    sort: DesktopSort,
    sortDescending: Boolean,
    onNavigateToAlbum: (Long) -> Unit,
    onAction: (LibraryAction) -> Unit,
) {
    val shown = remember(albums, query, sort, sortDescending) {
        val comparator = when (sort) {
            DesktopSort.Artist -> compareBy<LibraryAlbumItem> { it.artist.orEmpty().lowercase() }
            DesktopSort.Year -> compareBy { it.year ?: 0 }
            else -> compareBy { it.name.lowercase() }
        }.let { if (sortDescending) it.reversed() else it }
        albums.filter { album ->
            query.isBlank() || album.name.contains(query, true) || album.artist.orEmpty().contains(query, true)
        }.sortedWith(comparator)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = when {
            maxWidth < 570.dp -> 3
            maxWidth < 1000.dp -> 4
            else -> 5
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, top = 16.dp, end = 42.dp, bottom = 86.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(33.dp),
        ) {
            gridItems(shown, key = { it.id }) { album ->
                val albumTracks = tracks.filter { it.albumId == album.id }
                DesktopAlbumCard(
                    album = album,
                    tracks = albumTracks,
                    onOpen = { onNavigateToAlbum(album.id) },
                    onPlay = albumTracks.takeIf { it.isNotEmpty() }?.let { playable ->
                        { onAction(LibraryAction.PlayTracks(playable.map { it.id })) }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DesktopAlbumCard(
    album: LibraryAlbumItem,
    tracks: List<LibraryTrackItem>,
    onOpen: () -> Unit,
    onPlay: (() -> Unit)?,
) {
    val interaction = remember { MutableInteractionSource() }
    val windowFocused = rememberPlatformWindowFocused()
    var hovered by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(windowFocused) {
        if (!windowFocused) hovered = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(2.dp, RoundedCornerShape(7.dp), clip = false)
                .clip(RoundedCornerShape(7.dp))
                .background(MiuixTheme.colorScheme.surfaceVariant)
                .hoverable(interaction)
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false }
                .onPointerEvent(PointerEventType.Press) { event ->
                    if (event.buttons.isSecondaryPressed) menuOpen = true
                }
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = {
                        hovered = false
                        onOpen()
                    },
                    onDoubleClick = { onPlay?.invoke() },
                ),
        ) {
            ArtworkImage(
                artwork = Artwork.LibraryAlbum(album.id),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (hovered && windowFocused) {
                onPlay?.let { play ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .size(29.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF767680).copy(alpha = 0.46f))
                            .clickable(onClick = play),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(CoreRes.drawable.icon_play),
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp).offset(y = 1.dp),
                        )
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomEnd)) {
                CompactContextMenu(
                    show = menuOpen,
                    onDismiss = { menuOpen = false },
                    entries = listOf(
                        CompactMenuEntry(stringResource(Res.string.library_desktop_play), CoreRes.drawable.icon_play) {
                            menuOpen = false; onPlay?.invoke()
                        },
                    ),
                )
            }
        }
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = album.name,
                color = appleDesktopForeground(),
                fontSize = 13.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = album.artist.orEmpty(),
                color = appleDesktopForeground().copy(alpha = 0.54f),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DesktopArtists(
    artists: List<LibraryArtistItem>,
    albums: List<LibraryAlbumItem>,
    tracks: List<LibraryTrackItem>,
    query: String,
    sortDescending: Boolean,
    currentPlayingTrackId: Long?,
    favoriteIds: Set<Long>,
    onNavigateToAlbum: (Long) -> Unit,
    onAction: (LibraryAction) -> Unit,
) {
    val shown = remember(artists, query, sortDescending) {
        artists.filter { query.isBlank() || it.name.contains(query, true) }
            .sortedBy { it.name.lowercase() }
            .let { if (sortDescending) it.reversed() else it }
    }
    var selectedArtistId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(shown) {
        if (selectedArtistId != null && shown.none { it.id == selectedArtistId }) selectedArtistId = null
    }
    Row(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.width(300.dp).fillMaxHeight(),
            contentPadding = PaddingValues(bottom = 78.dp),
        ) {
            item("all") {
                DesktopArtistRow(
                    name = stringResource(Res.string.library_desktop_all_artists),
                    artwork = null,
                    showAllIcon = true,
                    selected = selectedArtistId == null,
                    onClick = { selectedArtistId = null },
                )
            }
            items(shown, key = { it.id }) { artist ->
                val artworkTrack = tracks.firstOrNull { trackArtistMatches(it.artist, artist.name) }
                DesktopArtistRow(
                    name = artist.name,
                    artwork = artworkTrack?.let { Artwork.LibraryTrack(it.id) },
                    showAllIcon = false,
                    selected = selectedArtistId == artist.id,
                    onClick = { selectedArtistId = artist.id },
                )
            }
        }
        Box(
            Modifier.width(1.dp).fillMaxHeight()
                .background(appleDesktopForeground().copy(alpha = 0.09f)),
        )
        ArtistContent(
            modifier = Modifier.weight(1f),
            selectedArtist = shown.firstOrNull { it.id == selectedArtistId },
            artists = shown,
            albums = albums,
            tracks = tracks,
            currentPlayingTrackId = currentPlayingTrackId,
            favoriteIds = favoriteIds,
            onNavigateToAlbum = onNavigateToAlbum,
            onAction = onAction,
        )
    }
}

@Composable
private fun DesktopArtistRow(
    name: String,
    artwork: Artwork?,
    showAllIcon: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val focused = rememberPlatformWindowFocused()
    val isDark = LocalDesignIsDarkTheme.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val selectedFill = if (focused) appleAccent() else if (isDark) Color.White.copy(alpha = 0.13f) else Color.Black.copy(alpha = 0.13f)
    val textColor = if (selected && focused) MiuixTheme.colorScheme.onPrimary else appleDesktopForeground()
    Box(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 10.dp)) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(5.dp))
                .background(
                    when {
                        selected -> selectedFill
                        hovered -> appleDesktopForeground().copy(alpha = 0.055f)
                        else -> Color.Transparent
                    },
                )
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showAllIcon) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(CoreRes.drawable.icon_artist_microphone),
                        null,
                        tint = textColor.copy(alpha = 0.58f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else if (artwork == null) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .background(appleDesktopForeground().copy(alpha = 0.08f)),
                )
            } else {
                ArtworkImage(
                    artwork = artwork,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(36.dp).clip(CircleShape),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = name,
                color = textColor,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier.align(Alignment.BottomEnd).width(226.dp).height(0.5.dp)
                .background(appleDesktopForeground().copy(alpha = 0.12f)),
        )
    }
}

@Composable
private fun ArtistContent(
    modifier: Modifier,
    selectedArtist: LibraryArtistItem?,
    artists: List<LibraryArtistItem>,
    albums: List<LibraryAlbumItem>,
    tracks: List<LibraryTrackItem>,
    currentPlayingTrackId: Long?,
    favoriteIds: Set<Long>,
    onNavigateToAlbum: (Long) -> Unit,
    onAction: (LibraryAction) -> Unit,
) {
    val visibleArtists = selectedArtist?.let(::listOf) ?: artists
    LazyColumn(
        modifier = modifier.fillMaxHeight(),
        contentPadding = PaddingValues(start = 10.dp, top = 14.dp, end = 30.dp, bottom = 86.dp),
    ) {
        visibleArtists.forEach { artist ->
            val artistTracks = tracks.filter { trackArtistMatches(it.artist, artist.name) }
            val artistAlbums = albums.filter { trackArtistMatches(it.artist, artist.name) }
            item("artist-heading-${artist.id}") {
                ArtistHeading(
                    artist = artist,
                    trackCount = artistTracks.size,
                    showTrackCount = selectedArtist != null,
                    onPlay = artistTracks.takeIf { it.isNotEmpty() }?.let { playable ->
                        { onAction(LibraryAction.PlayTracks(playable.map { it.id })) }
                    },
                    onShuffle = artistTracks.takeIf { it.isNotEmpty() }?.let { playable ->
                        { onAction(LibraryAction.PlayTracks(playable.shuffled().map { it.id })) }
                    },
                )
            }
            artistAlbums.forEach { album ->
                val albumTracks = artistTracks.filter { it.albumId == album.id }
                item("artist-album-${artist.id}-${album.id}") {
                    ArtistAlbumGroup(
                        album = album,
                        tracks = albumTracks,
                        currentPlayingTrackId = currentPlayingTrackId,
                        favoriteIds = favoriteIds,
                        onOpenAlbum = { onNavigateToAlbum(album.id) },
                        onAction = onAction,
                    )
                }
            }
            if (artistAlbums.isEmpty() && artistTracks.isNotEmpty()) {
                item("artist-tracks-${artist.id}") {
                    ArtistLooseTracks(
                        tracks = artistTracks,
                        currentPlayingTrackId = currentPlayingTrackId,
                        favoriteIds = favoriteIds,
                        onAction = onAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistHeading(
    artist: LibraryArtistItem,
    trackCount: Int,
    showTrackCount: Boolean,
    onPlay: (() -> Unit)?,
    onShuffle: (() -> Unit)?,
) {
    var menuOpen by remember(artist.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(start = 20.dp)) {
        Row(
            Modifier.fillMaxWidth().height(47.dp).padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = artist.name,
                modifier = Modifier.weight(1f),
                color = appleDesktopForeground(),
                fontSize = 20.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            onPlay?.let { play ->
                ArtistRoundButton(stringResource(Res.string.library_desktop_play), play) {
                    Icon(painterResource(CoreRes.drawable.icon_play), null, tint = appleAccent(), modifier = Modifier.size(15.dp))
                }
            }
            Spacer(Modifier.width(8.dp))
            onShuffle?.let { shuffle ->
                ArtistRoundButton(stringResource(Res.string.library_desktop_sort), shuffle) {
                    Icon(painterResource(CoreRes.drawable.icon_shuffle), null, tint = appleAccent(), modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(8.dp))
            }
            ArtistRoundButton(stringResource(Res.string.library_add_favorite), {}, enabled = false) {
                Icon(
                    painterResource(CoreRes.drawable.icon_star),
                    null,
                    tint = appleAccent().copy(alpha = 0.32f),
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Box {
                ArtistRoundButton(stringResource(Res.string.library_desktop_more), { menuOpen = true }) {
                    Icon(painterResource(CoreRes.drawable.icon_more_horizontal), null, tint = appleAccent(), modifier = Modifier.size(15.dp))
                }
                CompactContextMenu(
                    show = menuOpen,
                    onDismiss = { menuOpen = false },
                    entries = buildList {
                        onPlay?.let { play ->
                            add(CompactMenuEntry(stringResource(Res.string.library_desktop_play), CoreRes.drawable.icon_play) {
                                menuOpen = false; play()
                            })
                        }
                        onShuffle?.let { shuffle ->
                            add(CompactMenuEntry(stringResource(Res.string.library_desktop_sort), CoreRes.drawable.icon_shuffle) {
                                menuOpen = false; shuffle()
                            })
                        }
                    },
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(appleDesktopForeground().copy(alpha = 0.13f)))
        if (showTrackCount) {
            Text(
                text = stringResource(Res.string.library_track_count, trackCount),
                modifier = Modifier.fillMaxWidth().height(35.dp).padding(start = 10.dp, top = 8.dp),
                color = appleDesktopForeground().copy(alpha = 0.52f),
                fontSize = 13.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun ArtistRoundButton(
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val backgroundAlpha = when {
        !enabled -> 0.035f
        pressed -> 0.13f
        hovered -> 0.085f
        else -> 0.055f
    }
    Box(
        Modifier.size(26.dp).clip(CircleShape)
            .background(appleDesktopForeground().copy(alpha = backgroundAlpha))
            .hoverable(interaction)
            .semantics { contentDescription = description }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ArtistAlbumGroup(
    album: LibraryAlbumItem,
    tracks: List<LibraryTrackItem>,
    currentPlayingTrackId: Long?,
    favoriteIds: Set<Long>,
    onOpenAlbum: () -> Unit,
    onAction: (LibraryAction) -> Unit,
) {
    var menuOpen by remember(album.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 30.dp, bottom = 26.dp)) {
        ArtworkImage(
            artwork = Artwork.LibraryAlbum(album.id),
            contentScale = ContentScale.Crop,
            modifier = Modifier.padding(start = 20.dp).size(116.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onOpenAlbum),
        )
        Spacer(Modifier.height(38.dp))
        Row(Modifier.fillMaxWidth().padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = album.name,
                modifier = Modifier.weight(1f).clickable(onClick = onOpenAlbum),
                color = appleDesktopForeground(),
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val downloadable = tracks.filter { it.mediaId != null }
            if (downloadable.isNotEmpty()) {
                ArtistRoundButton(stringResource(Res.string.library_download), {
                    downloadable.forEach { onAction(LibraryAction.DownloadTrack(it)) }
                }) {
                    Icon(painterResource(CoreRes.drawable.icon_download), null, tint = appleAccent(), modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(8.dp))
            }
            Box {
                ArtistRoundButton(stringResource(Res.string.library_desktop_more), { menuOpen = true }) {
                    Icon(painterResource(CoreRes.drawable.icon_more_horizontal), null, tint = appleAccent(), modifier = Modifier.size(15.dp))
                }
                CompactContextMenu(
                    show = menuOpen,
                    onDismiss = { menuOpen = false },
                    entries = listOf(
                        CompactMenuEntry(stringResource(Res.string.library_desktop_play), CoreRes.drawable.icon_play) {
                            menuOpen = false
                            if (tracks.isNotEmpty()) onAction(LibraryAction.PlayTracks(tracks.map { it.id }))
                        },
                    ),
                )
            }
        }
        album.year?.let { year ->
            Text(
                year.toString(),
                color = appleDesktopForeground().copy(alpha = 0.52f),
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 20.dp, top = 10.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier.fillMaxWidth().padding(start = 20.dp).height(0.5.dp)
                .background(appleDesktopForeground().copy(alpha = 0.13f)),
        )
        tracks.forEachIndexed { index, track ->
            ArtistTrackLine(
                track = track,
                index = index,
                playing = track.id == currentPlayingTrackId,
                favorite = track.id in favoriteIds,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun ArtistLooseTracks(
    tracks: List<LibraryTrackItem>,
    currentPlayingTrackId: Long?,
    favoriteIds: Set<Long>,
    onAction: (LibraryAction) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        tracks.forEachIndexed { index, track ->
            ArtistTrackLine(track, index, track.id == currentPlayingTrackId, track.id in favoriteIds, onAction)
        }
    }
}

@Composable
private fun ArtistTrackLine(
    track: LibraryTrackItem,
    index: Int,
    playing: Boolean,
    favorite: Boolean,
    onAction: (LibraryAction) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(IntOffset.Zero) }
    var menuAlignment by remember { mutableStateOf<Alignment>(Alignment.TopStart) }
    val density = LocalDensity.current
    val rowColor = when {
        hovered -> appleDesktopForeground().copy(alpha = 0.065f)
        index % 2 == 1 -> appleDesktopForeground().copy(alpha = 0.035f)
        else -> Color.Transparent
    }

    Box(Modifier.fillMaxWidth().height(46.dp)) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(4.dp))
                .background(rowColor)
                .hoverable(interaction)
                .onPointerEvent(PointerEventType.Press) { event ->
                    if (event.buttons.isSecondaryPressed) {
                        event.changes.firstOrNull()?.position?.let { position ->
                            menuOffset = IntOffset(position.x.toInt(), position.y.toInt())
                        }
                        menuAlignment = Alignment.TopStart
                        menuOpen = true
                    }
                }
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { onAction(LibraryAction.PlayTrack(track.id)) },
                    onDoubleClick = { onAction(LibraryAction.PlayTrack(track.id)) },
                )
                .padding(top = 8.dp, end = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(22.dp).padding(start = 1.dp), contentAlignment = Alignment.CenterStart) {
                Icon(
                    painter = painterResource(if (favorite) CoreRes.drawable.icon_star_filled else CoreRes.drawable.icon_star),
                    contentDescription = stringResource(
                        if (favorite) Res.string.library_remove_favorite else Res.string.library_add_favorite,
                    ),
                    tint = if (favorite) appleAccent() else appleDesktopForeground().copy(alpha = 0.38f),
                    modifier = Modifier.size(11.dp).clickable { onAction(LibraryAction.ToggleFavorite(track.id)) },
                )
            }
            Box(Modifier.width(34.dp), contentAlignment = Alignment.CenterStart) {
                if (playing) {
                    Icon(painterResource(CoreRes.drawable.icon_speaker), null, tint = appleAccent(), modifier = Modifier.size(12.dp))
                } else {
                    Text((index + 1).toString(), color = appleDesktopForeground().copy(alpha = 0.45f), fontSize = 11.sp)
                }
            }
            Text(
                text = track.title,
                modifier = Modifier.weight(1f),
                color = appleDesktopForeground(),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(103.dp))
            Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                if (track.mediaId != null) {
                    Icon(
                        painter = painterResource(CoreRes.drawable.icon_download),
                        contentDescription = stringResource(Res.string.library_download),
                        tint = appleAccent(),
                        modifier = Modifier.size(13.dp).clickable { onAction(LibraryAction.DownloadTrack(track)) },
                    )
                }
            }
            Box(Modifier.width(47.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = formatDuration(track.durationMs),
                    modifier = Modifier.offset(x = 3.dp),
                    color = appleDesktopForeground().copy(alpha = 0.48f),
                    fontSize = 11.sp,
                )
            }
            Box(Modifier.width(35.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(
                    painter = painterResource(CoreRes.drawable.icon_more_horizontal),
                    contentDescription = stringResource(Res.string.library_desktop_more),
                    tint = appleAccent(),
                    modifier = Modifier.size(13.dp).clickable {
                        menuAlignment = Alignment.TopEnd
                        menuOffset = IntOffset(0, with(density) { 18.dp.roundToPx() })
                        menuOpen = true
                    },
                )
            }
        }
        DesktopTrackMenu(
            show = menuOpen,
            track = track,
            favorite = favorite,
            onDismiss = { menuOpen = false },
            onPlay = { onAction(LibraryAction.PlayTrack(track.id)) },
            onToggleFavorite = { onAction(LibraryAction.ToggleFavorite(track.id)) },
            onDownload = track.mediaId?.let { { onAction(LibraryAction.DownloadTrack(track)) } },
            offset = menuOffset,
            alignment = menuAlignment,
            width = 212.dp,
        )
    }
}

@Composable
private fun appleAccent(): Color = MiuixTheme.colorScheme.primary

@Composable
private fun appleDesktopForeground(): Color =
    if (LocalDesignIsDarkTheme.current) MiuixTheme.colorScheme.onBackground else Color.Black

private fun formatDuration(durationMs: Long?): String {
    val seconds = ((durationMs ?: 0L) / 1000L).coerceAtLeast(0L)
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

internal fun updateDesktopSongSelection(
    current: Set<Long>,
    orderedTrackIds: List<Long>,
    clickedIndex: Int,
    lastSelectedIndex: Int,
    shift: Boolean,
    additive: Boolean,
): Set<Long> {
    val clickedId = orderedTrackIds.getOrNull(clickedIndex) ?: return current
    return when {
        shift && lastSelectedIndex in orderedTrackIds.indices -> {
            val range = minOf(lastSelectedIndex, clickedIndex)..maxOf(lastSelectedIndex, clickedIndex)
            range.mapTo(mutableSetOf()) { orderedTrackIds[it] }
        }
        additive && clickedId in current -> current - clickedId
        additive -> current + clickedId
        else -> setOf(clickedId)
    }
}

internal fun LibraryTrackItem.lazyListKey(index: Int): String = "library-track-$index-$id"

private fun trackArtistMatches(trackArtist: String?, artistName: String): Boolean =
    trackArtist.orEmpty()
        .split('、', '/', ';', ',', '&')
        .any { it.trim().equals(artistName.trim(), ignoreCase = true) }

@Composable
private fun CompactContextMenu(
    show: Boolean,
    onDismiss: () -> Unit,
    entries: List<CompactMenuEntry>,
    alignment: Alignment = Alignment.TopEnd,
    offset: IntOffset = IntOffset(8, 18),
    width: Dp = 176.dp,
) {
    if (!show) return
    val isDark = LocalDesignIsDarkTheme.current
    Popup(
        alignment = alignment,
        offset = offset,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier.width(width)
                .shadow(14.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDark) Color(0xF22C2C2E) else Color(0xF2F8F8F8))
                .border(
                    0.5.dp,
                    if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.12f),
                    RoundedCornerShape(10.dp),
                )
                .padding(vertical = 3.dp),
        ) {
            entries.forEach { entry ->
                if (entry.separatorBefore) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                            .height(0.5.dp)
                            .background(appleDesktopForeground().copy(alpha = 0.13f)),
                    )
                }
                val interaction = remember(entry.text) { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                Row(
                    Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 5.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (hovered) appleAccent() else Color.Transparent)
                        .hoverable(interaction)
                        .clickable(interactionSource = interaction, indication = null, onClick = entry.onClick)
                        .padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                        entry.icon?.let { icon ->
                            Icon(
                                painterResource(icon),
                                null,
                                tint = if (hovered) MiuixTheme.colorScheme.onPrimary else appleDesktopForeground(),
                                modifier = Modifier.size(10.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(9.dp))
                    Text(
                        entry.text,
                        color = if (hovered) MiuixTheme.colorScheme.onPrimary else appleDesktopForeground(),
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
