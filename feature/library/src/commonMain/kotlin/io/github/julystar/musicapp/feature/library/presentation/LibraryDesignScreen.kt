package io.github.julystar.musicapp.feature.library.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.model.LIBRARY_PLAYBACK_PLAYLIST_ID
import io.github.julystar.musicapp.core.domain.model.LibraryAlbumItem
import io.github.julystar.musicapp.core.domain.model.LibraryArtistItem
import io.github.julystar.musicapp.core.domain.model.LibraryTrackItem
import io.github.julystar.musicapp.core.domain.model.PlaylistSummary
import io.github.julystar.musicapp.core.presentation.components.LiquidGlassScene
import io.github.julystar.musicapp.core.presentation.components.LocalDesignBottomContentInset
import io.github.julystar.musicapp.core.presentation.components.LiquidGlassActionBar
import io.github.julystar.musicapp.core.presentation.media.ArtworkImage
import io.github.julystar.musicapp.core.presentation.media.FavoritesPlaylistArtwork
import io.github.julystar.musicapp.core.presentation.theme.DesignPalette
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.julystar.musicapp.core.presentation.transition.albumArtworkSharedElement
import io.github.julystar.musicapp.core.presentation.transition.playlistArtworkSharedElement
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.DrawableResource
import musicapp.core.presentation.generated.resources.Res as CoreRes
import musicapp.core.presentation.generated.resources.icon_album
import musicapp.core.presentation.generated.resources.icon_cloud
import musicapp.core.presentation.generated.resources.icon_download
import musicapp.core.presentation.generated.resources.icon_filter
import musicapp.core.presentation.generated.resources.icon_folder
import musicapp.core.presentation.generated.resources.icon_heart
import musicapp.core.presentation.generated.resources.icon_heart_filled
import musicapp.core.presentation.generated.resources.icon_dashboard
import musicapp.core.presentation.generated.resources.icon_log
import musicapp.core.presentation.generated.resources.icon_music_note
import musicapp.core.presentation.generated.resources.icon_play
import musicapp.core.presentation.generated.resources.icon_plus
import musicapp.core.presentation.generated.resources.icon_chevron_right
import musicapp.core.presentation.generated.resources.icon_pin
import musicapp.core.presentation.generated.resources.icon_pin_filled
import musicapp.core.presentation.generated.resources.icon_search
import musicapp.core.presentation.generated.resources.icon_vertialcal_more
import musicapp.feature.library.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LibraryDesignScreen(
    state: LibraryState,
    currentPlayingTrackId: Long? = null,
    onNavigateToLibraryFolderImport: () -> Unit = {},
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (Long) -> Unit = {},
    onNavigateToPlaylist: (Long) -> Unit = {},
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToPlaylists: () -> Unit = {},
    onAction: (LibraryAction) -> Unit,
) {
    var selectedCategory by remember { mutableStateOf(LibraryDesignCategory.Playlists) }
    var songQuery by remember { mutableStateOf("") }
    var artistQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf(LibrarySortBy.Title) }
    val bottomContentInset = LocalDesignBottomContentInset.current

    LiquidGlassScene(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background),
        ) {
        val compact = maxWidth < DesignTokens.adaptive.largeMinWidth
        val pagePadding = if (compact) 24.dp else DesignTokens.spacing.pageExpanded
        val isDesktop = maxWidth >= DesignTokens.adaptive.largeMinWidth

        if (isDesktop) {
            Row(modifier = Modifier.fillMaxSize()) {
                LibrarySidebar(
                    selected = selectedCategory,
                    onSelect = { selectedCategory = it },
                )
                Box(modifier = Modifier.weight(1f)) {
                    LibraryContent(
                        state = state,
                        selectedCategory = selectedCategory,
                        currentPlayingTrackId = currentPlayingTrackId,
                        onNavigateToLibraryFolderImport = onNavigateToLibraryFolderImport,
                        onNavigateToAlbum = onNavigateToAlbum,
                        onNavigateToArtist = onNavigateToArtist,
                        onNavigateToPlaylist = onNavigateToPlaylist,
                        onNavigateToFavorites = onNavigateToFavorites,
                        onNavigateToPlaylists = onNavigateToPlaylists,
                        onAction = onAction,
                        onSelectCategory = { selectedCategory = it },
                        songQuery = songQuery,
                        onSongQueryChange = { songQuery = it },
                        artistQuery = artistQuery,
                        onArtistQueryChange = { artistQuery = it },
                        sortBy = sortBy,
                        onSortByChange = { sortBy = it },
                        compact = false,
                        pagePadding = pagePadding,
                        bottomContentInset = bottomContentInset,
                    )
                }
            }
        } else {
            val topAppBarScrollBehavior = MiuixScrollBehavior()
            val actionBarProgress = topAppBarScrollBehavior.state.collapsedFraction
            val pageTitle = localizedLibraryText("Library")
            val pagerState = rememberPagerState(
                initialPage = primaryLibraryCategories.indexOf(selectedCategory).coerceAtLeast(0),
                pageCount = { primaryLibraryCategories.size },
            )
            val pagerScope = rememberCoroutineScope()
            val mobileListStates = listOf(
                rememberLazyListState(),
                rememberLazyListState(),
                rememberLazyListState(),
                rememberLazyListState(),
                rememberLazyListState(),
            )
            val activePage = pagerState.currentPage.coerceIn(primaryLibraryCategories.indices)
            val activeCategory = primaryLibraryCategories[activePage]

            LaunchedEffect(pagerState.settledPage) {
                selectedCategory = primaryLibraryCategories[pagerState.settledPage]
            }

            fun animateToCategory(category: LibraryDesignCategory) {
                val page = primaryLibraryCategories.indexOf(category)
                if (page >= 0) {
                    pagerScope.launch { pagerState.animateScrollToPage(page) }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = DesignTokens.adaptive.contentMaxWidth)
                    .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
            ) {
                TopAppBar(
                    title = pageTitle,
                    largeTitle = pageTitle,
                    color = Color.Transparent,
                    titleColor = Color.Transparent,
                    scrollBehavior = topAppBarScrollBehavior,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.padding(horizontal = pagePadding)) {
                    LibraryMobileTabs(
                        selected = activeCategory,
                        onSelect = ::animateToCategory,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.weight(1f)) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        key = { primaryLibraryCategories[it] },
                    ) { page ->
                        val pageCategory = primaryLibraryCategories[page]
                        LazyColumn(
                            state = mobileListStates[page],
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = pagePadding,
                                top = 0.dp,
                                end = pagePadding,
                                bottom = 28.dp + bottomContentInset,
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            LibraryCategoryItems(
                                state = state,
                                selectedCategory = pageCategory,
                                currentPlayingTrackId = currentPlayingTrackId,
                                onNavigateToLibraryFolderImport = onNavigateToLibraryFolderImport,
                                onNavigateToAlbum = onNavigateToAlbum,
                                onNavigateToArtist = onNavigateToArtist,
                                onNavigateToPlaylist = onNavigateToPlaylist,
                                onNavigateToFavorites = onNavigateToFavorites,
                                onNavigateToPlaylists = onNavigateToPlaylists,
                                onAction = onAction,
                                onSelectCategory = ::animateToCategory,
                                songQuery = songQuery,
                                onSongQueryChange = { songQuery = it },
                                artistQuery = artistQuery,
                                onArtistQueryChange = { artistQuery = it },
                                sortBy = sortBy,
                                onSortByChange = { sortBy = it },
                                showPlaylistMetadata = false,
                                artistContentEndPadding = 24.dp,
                            )
                        }
                    }
                    val artistGroups = remember(state.artists, state.tracks, artistQuery) {
                        libraryArtistGroups(state.artists, state.tracks, artistQuery)
                    }
                    if (activeCategory == LibraryDesignCategory.Artists && state.hasIndexedTracks) {
                        LibraryArtistIndexOverlay(
                            groups = artistGroups,
                            listState = mobileListStates[
                                primaryLibraryCategories.indexOf(LibraryDesignCategory.Artists)
                            ],
                            firstGroupItemIndex = MOBILE_ARTIST_FIRST_GROUP_ITEM_INDEX,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp),
                        )
                    }
                }
            }
            LiquidGlassActionBar(
                title = pageTitle,
                collapseFraction = actionBarProgress,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
        }
    }
}

@Composable
private fun LibrarySidebar(
    selected: LibraryDesignCategory,
    onSelect: (LibraryDesignCategory) -> Unit,
) {
    val dividerColor = MiuixTheme.colorScheme.outline
    Column(
        modifier = Modifier
            .width(196.dp)
            .fillMaxHeight()
            .background(MiuixTheme.colorScheme.surface)
            .drawBehind {
                val dividerWidth = 1.dp.toPx()
                val x = size.width - dividerWidth / 2f
                drawLine(
                    color = dividerColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = dividerWidth,
                )
            }
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        librarySidebarGroups.forEach { group ->
            Text(
                text = localizedLibraryText(group.label),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f),
                style = MiuixTheme.textStyles.footnote2,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            group.categories.forEach { category ->
                val isSelected = selected == category
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = DesignTokens.adaptive.minimumTouchTarget)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) MiuixTheme.colorScheme.tertiaryContainer
                            else Color.Transparent,
                        )
                        .clickable { onSelect(category) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        painter = painterResource(category.icon),
                        contentDescription = null,
                        tint = if (isSelected) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = localizedLibraryText(category.label),
                        color = if (isSelected) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private data class SidebarGroup(
    val label: String,
    val categories: List<LibraryDesignCategory>,
)

private val librarySidebarGroups = listOf(
    SidebarGroup("Collection", listOf(
        LibraryDesignCategory.Playlists,
        LibraryDesignCategory.Songs,
        LibraryDesignCategory.Albums,
        LibraryDesignCategory.Artists,
        LibraryDesignCategory.Genres,
    )),
    SidebarGroup("Storage", listOf(
        LibraryDesignCategory.Folders,
    )),
    SidebarGroup("More", listOf(
        LibraryDesignCategory.Favorites,
        LibraryDesignCategory.Downloads,
        LibraryDesignCategory.History,
        LibraryDesignCategory.RecentlyAdded,
        LibraryDesignCategory.RecentlyPlayed,
        LibraryDesignCategory.Lossless,
        LibraryDesignCategory.HiRes,
        LibraryDesignCategory.Sources,
    )),
)

@Composable
private fun LibraryMobileTabs(
    selected: LibraryDesignCategory,
    onSelect: (LibraryDesignCategory) -> Unit,
) {
    TabRow(
        tabs = primaryLibraryCategories.map { category -> localizedLibraryText(category.label) },
        selectedTabIndex = primaryLibraryCategories.indexOf(selected).coerceAtLeast(0),
        onTabSelected = { index -> onSelect(primaryLibraryCategories[index]) },
    )
}

@Composable
private fun LibraryContent(
    state: LibraryState,
    selectedCategory: LibraryDesignCategory,
    currentPlayingTrackId: Long?,
    onNavigateToLibraryFolderImport: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateToArtist: (Long) -> Unit,
    onNavigateToPlaylist: (Long) -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onAction: (LibraryAction) -> Unit,
    onSelectCategory: (LibraryDesignCategory) -> Unit,
    songQuery: String,
    onSongQueryChange: (String) -> Unit,
    artistQuery: String,
    onArtistQueryChange: (String) -> Unit,
    sortBy: LibrarySortBy,
    onSortByChange: (LibrarySortBy) -> Unit,
    compact: Boolean,
    pagePadding: Dp,
    bottomContentInset: Dp,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val listState = rememberLazyListState()
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

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 800.dp)
                .padding(horizontal = pagePadding),
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = 28.dp + bottomContentInset,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!compact) {
                item {
                    TopAppBar(
                        title = localizedLibraryText("Library"),
                        subtitle = "",
                        modifier = Modifier.alpha(pageTitleAlpha),
                    )
                }
            }

            LibraryCategoryItems(
                state = state,
                selectedCategory = selectedCategory,
                currentPlayingTrackId = currentPlayingTrackId,
                onNavigateToLibraryFolderImport = onNavigateToLibraryFolderImport,
                onNavigateToAlbum = onNavigateToAlbum,
                onNavigateToArtist = onNavigateToArtist,
                onNavigateToPlaylist = onNavigateToPlaylist,
                onNavigateToFavorites = onNavigateToFavorites,
                onNavigateToPlaylists = onNavigateToPlaylists,
                onAction = onAction,
                onSelectCategory = onSelectCategory,
                songQuery = songQuery,
                onSongQueryChange = onSongQueryChange,
                artistQuery = artistQuery,
                onArtistQueryChange = onArtistQueryChange,
                sortBy = sortBy,
                onSortByChange = onSortByChange,
                showPlaylistMetadata = true,
                artistContentEndPadding = 24.dp,
            )
        }
        val artistGroups = remember(state.artists, state.tracks, artistQuery) {
            libraryArtistGroups(state.artists, state.tracks, artistQuery)
        }
        if (selectedCategory == LibraryDesignCategory.Artists && state.hasIndexedTracks) {
            LibraryArtistIndexOverlay(
                groups = artistGroups,
                listState = listState,
                firstGroupItemIndex = if (compact) {
                    COMPACT_ARTIST_FIRST_GROUP_ITEM_INDEX
                } else {
                    DESKTOP_ARTIST_FIRST_GROUP_ITEM_INDEX
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp, top = 44.dp),
            )
        }
        LiquidGlassActionBar(
            title = localizedLibraryText("Library"),
            collapseFraction = actionBarProgress,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

private fun LazyListScope.LibraryCategoryItems(
    state: LibraryState,
    selectedCategory: LibraryDesignCategory,
    currentPlayingTrackId: Long?,
    onNavigateToLibraryFolderImport: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateToArtist: (Long) -> Unit,
    onNavigateToPlaylist: (Long) -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onAction: (LibraryAction) -> Unit,
    onSelectCategory: (LibraryDesignCategory) -> Unit,
    songQuery: String,
    onSongQueryChange: (String) -> Unit,
    artistQuery: String,
    onArtistQueryChange: (String) -> Unit,
    sortBy: LibrarySortBy,
    onSortByChange: (LibrarySortBy) -> Unit,
    showPlaylistMetadata: Boolean,
    artistContentEndPadding: Dp,
) = with(state) {
    val libraryHasTracks = hasIndexedTracks
    val favoriteTracks = favorites.dataOrNull.orEmpty()
    val tracksForCategory = getTracksForCategory(selectedCategory, tracks, favoriteTracks)
    val filteredTracks = tracksForCategory
        .filter { track ->
            songQuery.isBlank() || listOfNotNull(track.title, track.artist)
                .any { it.contains(songQuery, ignoreCase = true) }
        }
        .sortedWith(sortBy.comparator)
    val favoriteTrackIds = favoriteTracks.mapTo(mutableSetOf()) { it.id }
    val albumCards = albums
        .take(24)
        .map { album ->
            LibraryAlbumCardItem(
                id = album.id,
                title = album.name,
                year = album.year,
            )
        }
    val artistRows = libraryArtistRows(artists, tracks)
    val filteredArtistRows = artistRows.filter { artist ->
        artistQuery.isBlank() || artist.name.contains(artistQuery.trim(), ignoreCase = true)
    }
    val artistGroups = groupLibraryArtists(filteredArtistRows)
    val genreCards = genreNames.dataOrNull
        .orEmpty()
        .map { genre -> LibraryGenreCardItem(name = genre) }

    val isSongTab = selectedCategory in songLibraryCategories

    // Category header with actions
    item {
        CategorySectionHeader(
            title = selectedCategory.label,
            metadata = libraryMetadata(
                category = selectedCategory,
                state = state,
                albumCount = if (libraryHasTracks) albumCards.size else 0,
                artistCount = if (libraryHasTracks) artistRows.size else 0,
                genreCount = if (libraryHasTracks) genreCards.size else 0,
            ),
            showShuffle = isSongTab && filteredTracks.isNotEmpty() && showPlaylistMetadata,
            showPlayAll = isSongTab && filteredTracks.isNotEmpty(),
            showNewPlaylist = selectedCategory == LibraryDesignCategory.Playlists,
            onShuffle = {
                if (filteredTracks.isNotEmpty()) {
                    onAction(LibraryAction.PlayTrack(filteredTracks.first().id))
                }
            },
            onPlayAll = {
                if (filteredTracks.isNotEmpty()) {
                    onAction(LibraryAction.PlayTrack(filteredTracks.first().id))
                }
            },
            onNewPlaylist = onNavigateToPlaylists,
        )
    }

    // Search + Sort for song tabs
    if (isSongTab && tracksForCategory.isNotEmpty()) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InputField(
                    query = songQuery,
                    onQueryChange = onSongQueryChange,
                    onSearch = {},
                    expanded = false,
                    onExpandedChange = {},
                    label = if (selectedCategory == LibraryDesignCategory.Songs) {
                        "Search songs, artists, or albums"
                    } else {
                        "Search ${selectedCategory.label.lowercase()}"
                    },
                    modifier = Modifier.weight(1f),
                )
                SongFilterButton(
                    current = sortBy,
                    onChange = onSortByChange,
                )
            }
        }
    }

    if (
        selectedCategory == LibraryDesignCategory.Artists &&
        libraryHasTracks &&
        artistRows.isNotEmpty()
    ) {
        item {
            InputField(
                query = artistQuery,
                onQueryChange = onArtistQueryChange,
                onSearch = {},
                expanded = false,
                onExpandedChange = {},
                label = stringResource(Res.string.library_search_category_hint, stringResource(Res.string.library_category_artists)),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    when (selectedCategory) {
        LibraryDesignCategory.Songs,
        LibraryDesignCategory.Favorites,
        LibraryDesignCategory.History,
        LibraryDesignCategory.RecentlyPlayed,
        LibraryDesignCategory.RecentlyAdded,
        LibraryDesignCategory.Lossless,
        LibraryDesignCategory.HiRes -> {
            if (filteredTracks.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        LibraryEmptyContent(
                            title = if (songQuery.isNotBlank()) "No matches"
                            else "No tracks",
                            message = if (songQuery.isNotBlank()) "Try a different search."
                            else selectedCategory.emptyMessage,
                            action = if (songQuery.isNotBlank()) "Clear search" to { onSongQueryChange("") }
                            else null,
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = filteredTracks,
                    key = { _, track -> track.id.takeIf { it > 0L } ?: track.hashCode() },
                ) { index, track ->
                    LibrarySongRow(
                        track = track,
                        rank = if (selectedCategory == LibraryDesignCategory.Songs) index + 1 else null,
                        playing = track.id == currentPlayingTrackId,
                        isFavorite = track.id in favoriteTrackIds,
                        onPlay = { onAction(LibraryAction.PlayTrack(track.id)) },
                        onToggleFavorite = { onAction(LibraryAction.ToggleFavorite(track.id)) },
                        onMore = {},
                    )
                }
            }
        }

        LibraryDesignCategory.Albums -> {
            item {
                if (!libraryHasTracks || albumCards.isEmpty()) {
                    LibraryCategoryEmptyCard(LibraryDesignCategory.Albums)
                } else {
                    LibraryAlbumGrid(
                        albums = albumCards,
                        onOpenAlbum = { onNavigateToAlbum(it.id) },
                    )
                }
            }
        }

        LibraryDesignCategory.Artists -> {
            when {
                !libraryHasTracks || artistRows.isEmpty() -> item {
                    LibraryCategoryEmptyCard(LibraryDesignCategory.Artists)
                }
                filteredArtistRows.isEmpty() -> item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        LibraryEmptyContent(
                            title = stringResource(Res.string.library_no_matches),
                            message = stringResource(Res.string.library_try_different_search),
                            action = stringResource(Res.string.library_action_clear_search) to
                                { onArtistQueryChange("") },
                        )
                    }
                }
                else -> artistGroups.forEach { group ->
                    item(key = "artist-section-${group.letter}") {
                        Column {
                            LibraryArtistSectionHeader(group.letter)
                            group.artists.forEach { artist ->
                                LibraryArtistRow(
                                    artist = artist,
                                    onOpenArtist = { onNavigateToArtist(artist.id) },
                                    modifier = Modifier.padding(end = artistContentEndPadding),
                                )
                            }
                        }
                    }
                }
            }
        }

        LibraryDesignCategory.Genres -> item {
            if (!libraryHasTracks || genreCards.isEmpty()) {
                LibraryCategoryEmptyCard(LibraryDesignCategory.Genres)
            } else {
                LibraryGenreGrid(
                    genres = genreCards,
                    onOpenGenre = { genre -> onAction(LibraryAction.SelectGenre(genre.name)) },
                )
            }
        }

        LibraryDesignCategory.Folders -> item {
            Card(modifier = Modifier.fillMaxWidth()) {
                LibraryEmptyContent(
                    title = stringResource(Res.string.library_no_folders_added),
                    message = stringResource(Res.string.library_import_folder_message),
                    action = stringResource(Res.string.library_action_import_folder) to
                        onNavigateToLibraryFolderImport,
                    painter = painterResource(CoreRes.drawable.icon_folder),
                )
            }
        }

        LibraryDesignCategory.Playlists -> item {
            PlaylistListView(
                playlists = playlists.toLibraryPlaylistRows(
                    favoriteTracks = favoriteTracks,
                    favoritesTitle = stringResource(Res.string.library_my_favorites),
                    favoritesDescription = stringResource(Res.string.library_liked_songs),
                ),
                onOpenPlaylist = { playlist ->
                    if (playlist.key == FavoritesPlaylistKey) {
                        onNavigateToFavorites()
                    } else {
                        playlist.summary?.let { onNavigateToPlaylist(it.id) }
                            ?: onNavigateToPlaylists()
                    }
                },
                onManagePlaylists = onNavigateToPlaylists,
                showMetadata = showPlaylistMetadata,
            )
        }

        LibraryDesignCategory.Downloads -> item {
            Card(modifier = Modifier.fillMaxWidth()) {
                LibraryEmptyContent(
                    title = stringResource(Res.string.library_no_downloads_yet),
                    message = stringResource(Res.string.library_offline_message),
                    action = stringResource(Res.string.library_action_browse_songs) to
                        { onSelectCategory(LibraryDesignCategory.Songs) },
                    painter = painterResource(CoreRes.drawable.icon_download),
                )
            }
        }

        LibraryDesignCategory.Sources -> item {
            Card(modifier = Modifier.fillMaxWidth()) {
                LibraryEmptyContent(
                    title = stringResource(Res.string.library_sources_title),
                    message = stringResource(Res.string.library_sources_message),
                    painter = painterResource(CoreRes.drawable.icon_cloud),
                )
            }
        }
    }
}

// ── Category Section Header ──

@Composable
private fun CategorySectionHeader(
    title: String,
    metadata: String,
    showShuffle: Boolean,
    showPlayAll: Boolean,
    showNewPlaylist: Boolean,
    onShuffle: () -> Unit,
    onPlayAll: () -> Unit,
    onNewPlaylist: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = localizedLibraryText(title),
                color = MiuixTheme.colorScheme.onBackground,
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = localizedLibraryText(metadata),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showShuffle) {
                ShuffleButton(onClick = onShuffle)
            }
            if (showPlayAll) {
                PlayAllButton(onClick = onPlayAll)
            }
            if (showNewPlaylist) {
                NewPlaylistButton(onClick = onNewPlaylist)
            }
        }
    }
}

@Composable
private fun ShuffleButton(onClick: () -> Unit) {
    Button(onClick = onClick) {
        Icon(
            painter = painterResource(CoreRes.drawable.icon_dashboard),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = localizedLibraryText("Shuffle"),
            style = MiuixTheme.textStyles.footnote1,
        )
    }
}

@Composable
private fun PlayAllButton(onClick: () -> Unit) {
    Button(onClick = onClick) {
        Icon(
            painter = painterResource(CoreRes.drawable.icon_play),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = localizedLibraryText("Play all"),
            style = MiuixTheme.textStyles.footnote1,
        )
    }
}

@Composable
private fun NewPlaylistButton(onClick: () -> Unit) {
    Button(onClick = onClick) {
        Icon(
            painter = painterResource(CoreRes.drawable.icon_plus),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = localizedLibraryText("New"),
            style = MiuixTheme.textStyles.footnote1,
        )
    }
}

// ── Song Search + Filter ──

@Composable
private fun SongFilterButton(
    current: LibrarySortBy,
    onChange: (LibrarySortBy) -> Unit,
) {
    IconButton(
        onClick = {
            val nextIndex = (LibrarySortBy.entries.indexOf(current) + 1) % LibrarySortBy.entries.size
            onChange(LibrarySortBy.entries[nextIndex])
        },
    ) {
        Icon(
            painter = painterResource(CoreRes.drawable.icon_filter),
            contentDescription = localizedLibraryText("Filter songs, sorted by ${current.label}"),
        )
    }
}

// ── Song Row ──

@Composable
private fun LibrarySongRow(
    track: LibraryTrackItem,
    rank: Int?,
    playing: Boolean,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMore: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(
                    if (playing) MiuixTheme.colorScheme.primary.copy(alpha = 0.10f)
                    else Color.Transparent,
                )
                .padding(end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .clickable(onClick = onPlay),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.width(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = rank?.toString() ?: "",
                    color = if (playing) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                    style = MiuixTheme.textStyles.footnote2.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    ),
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
                        MiuixTheme.colorScheme.onBackground
                    },
                    style = MiuixTheme.textStyles.body1.copy(
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                    ),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist ?: localizedLibraryText("Unknown Artist"),
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    style = MiuixTheme.textStyles.footnote1.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier.width(64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleFavorite),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(
                        if (isFavorite) CoreRes.drawable.icon_heart_filled else CoreRes.drawable.icon_heart,
                    ),
                    contentDescription = localizedLibraryText(if (isFavorite) "Remove from favorites" else "Add to favorites"),
                    tint = if (isFavorite) {
                        DesignPalette.FavoriteRed
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                    modifier = Modifier.size(16.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMore),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(CoreRes.drawable.icon_vertialcal_more),
                    contentDescription = localizedLibraryText("More actions for ${track.title}"),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        }
        HorizontalDivider()
    }
}

// ── Album Grid ──

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun LibraryAlbumGrid(
    albums: List<LibraryAlbumCardItem>,
    onOpenAlbum: (LibraryAlbumCardItem) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth >= 800.dp -> 4
            maxWidth >= 500.dp -> 3
            else -> 2
        }
        val itemPadding = DesignTokens.spacing.xxs
        val gap = 16.dp - itemPadding * 2
        val width = (maxWidth - gap * (columns - 1)) / columns
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            maxItemsInEachRow = columns,
        ) {
            albums.forEachIndexed { index, album ->
                AlbumCard(album, index, width, itemPadding, onOpenAlbum)
            }
        }
    }
}

@Composable
private fun AlbumCard(
    album: LibraryAlbumCardItem,
    index: Int,
    width: Dp,
    contentPadding: Dp,
    onOpenAlbum: (LibraryAlbumCardItem) -> Unit,
) {
    val artworkShape = RoundedCornerShape(14.dp)
    val artworkSize = width - contentPadding * 2
    val metadata = listOfNotNull(
        album.artist?.takeIf(String::isNotBlank),
        album.year?.toString(),
    ).joinToString(" · ")
    Column(
        modifier = Modifier
            .width(width)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onOpenAlbum(album) }
            .padding(contentPadding),
    ) {
        Box(
            modifier = Modifier
                .albumArtworkSharedElement(album.id)
                .size(artworkSize)
                .shadow(DesignTokens.elevation.card, artworkShape, clip = false)
                .clip(artworkShape)
                .background(libraryArtworkBrush(index)),
            contentAlignment = Alignment.Center,
        ) {
            ArtworkImage(
                artwork = Artwork.LibraryAlbum(album.id),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                fallback = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(libraryArtworkBrush(index)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = album.title.take(2).uppercase(),
                            color = Color.White.copy(alpha = 0.5f),
                            style = MiuixTheme.textStyles.title1,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
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
        if (metadata.isNotBlank()) {
            Text(
                text = metadata,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                style = MiuixTheme.textStyles.footnote1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Artists ──

@Composable
private fun LibraryArtistSectionHeader(letter: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = letter,
            color = MiuixTheme.colorScheme.primary,
            style = MiuixTheme.textStyles.footnote2,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun LibraryArtistRow(
    artist: LibraryArtistRowItem,
    onOpenArtist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onOpenArtist)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(libraryArtworkBrush(artist.name.hashCode())),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = artistInitials(artist.name),
                color = Color.White.copy(alpha = 0.94f),
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = artist.name,
                color = MiuixTheme.colorScheme.onBackground,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = localizedLibraryText("${artist.trackCount} tracks"),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                style = MiuixTheme.textStyles.footnote1,
                maxLines = 1,
            )
        }
        Icon(
            painter = painterResource(CoreRes.drawable.icon_chevron_right),
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp),
        )
        }
        HorizontalDivider()
    }
}

@Composable
private fun LibraryArtistIndexOverlay(
    groups: List<LibraryArtistGroup>,
    listState: LazyListState,
    firstGroupItemIndex: Int,
    modifier: Modifier = Modifier,
) {
    val targets = artistIndexTargets(groups, firstGroupItemIndex)
    if (targets.size <= 1) return

    val coroutineScope = rememberCoroutineScope()
    ArtistAlphabetIndex(
        availableLetters = targets.map { it.letter }.toSet(),
        selectedLetter = visibleArtistLetter(targets, listState.firstVisibleItemIndex),
        onLetterSelected = { letter ->
            targets.firstOrNull { it.letter == letter }?.let { target ->
                coroutineScope.launch {
                    listState.animateScrollToItem(target.itemIndex)
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun ArtistAlphabetIndex(
    availableLetters: Set<String>,
    selectedLetter: String?,
    onLetterSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(DesignTokens.shapes.full)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f))
            .border(1.dp, MiuixTheme.colorScheme.outline.copy(alpha = 0.55f), shape)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        artistIndexLetters.forEach { letter ->
            val available = letter in availableLetters
            val isSelected = letter == selectedLetter
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MiuixTheme.colorScheme.primary else Color.Transparent,
                    )
                    .then(
                        if (available) Modifier.clickable { onLetterSelected(letter) } else Modifier,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter,
                    color = when {
                        isSelected -> MiuixTheme.colorScheme.onPrimary
                        available -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.22f)
                    },
                    style = MiuixTheme.textStyles.footnote2.copy(
                        fontSize = if (letter == "#") 8.sp else 9.sp,
                        lineHeight = 11.sp,
                    ),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

// ── Genre Grid ──

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun LibraryGenreGrid(
    genres: List<LibraryGenreCardItem>,
    onOpenGenre: (LibraryGenreCardItem) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 640.dp) 3 else 2
        val gap = 12.dp
        val width = (maxWidth - gap * (columns - 1)) / columns
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
            maxItemsInEachRow = columns,
        ) {
            genres.forEachIndexed { index, genre ->
                Box(
                    modifier = Modifier
                        .width(width)
                        .height(96.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(libraryArtworkBrush(index))
                        .clickable { onOpenGenre(genre) }
                        .padding(16.dp),
                ) {
                    Text(
                        text = genre.name,
                        color = Color.White,
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomStart),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    genre.albumCount?.let { albumCount ->
                        Text(
                            text = localizedLibraryText("$albumCount albums"),
                            color = Color.White,
                            style = MiuixTheme.textStyles.footnote2,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.20f))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── Playlist List ──

@Composable
private fun PlaylistListView(
    playlists: List<LibraryPlaylistRowItem>,
    onOpenPlaylist: (LibraryPlaylistRowItem) -> Unit,
    onManagePlaylists: () -> Unit,
    showMetadata: Boolean,
) {
    var pinnedPlaylistKeys by remember(playlists) {
        mutableStateOf<Set<String>>(playlists.filter { it.isInitiallyPinned }.mapTo(mutableSetOf()) { it.key })
    }

    Column {
        playlists.forEachIndexed { index, playlist ->
            val isPinned = playlist.key in pinnedPlaylistKeys
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .combinedClickable(
                            onClick = { onOpenPlaylist(playlist) },
                            onLongClick = onManagePlaylists,
                        )
                        .padding(start = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (playlist.isFavorites) {
                        FavoritesPlaylistArtwork(
                            size = 56.dp,
                            cornerRadius = 12.dp,
                            modifier = Modifier.playlistArtworkSharedElement(
                                LIBRARY_PLAYBACK_PLAYLIST_ID,
                            ),
                        )
                    } else {
                        val summary = playlist.summary
                        val artworkModifier = if (summary == null) {
                            Modifier
                        } else {
                            Modifier.playlistArtworkSharedElement(summary.id)
                        }
                        ArtworkImage(
                            modifier = artworkModifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            artwork = summary?.coverArtwork,
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = localizedLibraryText(playlist.title),
                            color = MiuixTheme.colorScheme.onBackground,
                            style = MiuixTheme.textStyles.body1.copy(
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                            ),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = localizedLibraryText(playlist.description),
                            color = MiuixTheme.colorScheme.onBackgroundVariant,
                            style = MiuixTheme.textStyles.footnote1.copy(
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (showMetadata) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = localizedLibraryText("${playlist.musicCount} tracks"),
                                color = MiuixTheme.colorScheme.onSurface,
                                style = MiuixTheme.textStyles.footnote2,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = localizedLibraryText(playlist.durationLabel),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                style = MiuixTheme.textStyles.footnote2,
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable {
                            pinnedPlaylistKeys = if (isPinned) {
                                pinnedPlaylistKeys - playlist.key
                            } else {
                                pinnedPlaylistKeys + playlist.key
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(
                            if (isPinned) CoreRes.drawable.icon_pin_filled else CoreRes.drawable.icon_pin,
                        ),
                        contentDescription = localizedLibraryText(if (isPinned) "Unpin playlist" else "Pin playlist"),
                        tint = if (isPinned) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            if (index < playlists.lastIndex) {
                HorizontalDivider()
            }
        }
    }
}

// ── Empty Content ──

@Composable
private fun LibraryEmptyContent(
    title: String,
    message: String,
    action: Pair<String, () -> Unit>? = null,
    painter: Painter? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (painter != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MiuixTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painter,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Text(
            text = localizedLibraryText(title),
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = localizedLibraryText(message),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Text(
                text = localizedLibraryText(action.first),
                color = Color.White,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .heightIn(min = DesignTokens.adaptive.minimumTouchTarget)
                    .clip(RoundedCornerShape(DesignTokens.shapes.full))
                    .background(
                        Brush.linearGradient(
                            listOf(MiuixTheme.colorScheme.primary, MiuixTheme.colorScheme.secondary),
                        ),
                    )
                    .clickable(onClick = action.second)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun LibraryCategoryEmptyCard(category: LibraryDesignCategory) {
    Card(modifier = Modifier.fillMaxWidth()) {
        LibraryEmptyContent(
            title = "No ${category.label.lowercase()}",
            message = category.emptyMessage,
        )
    }
}

// ── Metadata ──

private fun libraryMetadata(
    category: LibraryDesignCategory,
    state: LibraryState,
    albumCount: Int,
    artistCount: Int,
    genreCount: Int,
): String {
    val tracks = state.tracks
    return when (category) {
        LibraryDesignCategory.Songs -> {
            "${tracks.size} songs · ${formatLibraryDuration(tracks)}"
        }
        LibraryDesignCategory.Albums -> "$albumCount albums"
        LibraryDesignCategory.Artists -> "$artistCount artists"
        LibraryDesignCategory.Genres -> "$genreCount genres"
        LibraryDesignCategory.Folders -> "Import a music folder"
        LibraryDesignCategory.Playlists -> {
            val playlistCount = state.playlists.size + 1
            "$playlistCount playlists · Long press to edit"
        }
        LibraryDesignCategory.Downloads -> "Available offline"
        LibraryDesignCategory.Favorites -> {
            val favoriteTracks = state.favorites.dataOrNull.orEmpty()
            "${favoriteTracks.size} songs · ${formatLibraryDuration(favoriteTracks)}"
        }
        else -> "No collection data available"
    }
}

private fun getTracksForCategory(
    category: LibraryDesignCategory,
    tracks: List<LibraryTrackItem>,
    favoriteTracks: List<LibraryTrackItem>,
): List<LibraryTrackItem> {
    return when (category) {
        LibraryDesignCategory.Songs -> tracks
        LibraryDesignCategory.Favorites -> favoriteTracks
        else -> emptyList()
    }
}

// ── Utilities ──

private fun libraryArtworkBrush(index: Int): Brush {
    val safeIdx = index.mod(libraryArtworkColors.size)
    val first = libraryArtworkColors[safeIdx]
    val second = libraryArtworkColors[(safeIdx + 1).mod(libraryArtworkColors.size)]
    return Brush.linearGradient(listOf(first, second))
}

private fun formatLibraryDuration(tracks: List<LibraryTrackItem>): String {
    val totalMs = tracks.sumOf { (it.durationMs ?: 0L).coerceAtLeast(0L) }
    val totalMinutes = (totalMs + 59_999L) / 60_000L
    return if (totalMinutes >= 60L) {
        "${totalMinutes / 60L}h ${totalMinutes % 60L}m"
    } else {
        "$totalMinutes min"
    }
}

private fun PlaylistSummary.compactMetadata(): String {
    val trackLabel = if (musicCount == 1L) "track" else "tracks"
    return "$musicCount $trackLabel · ${formatPlaylistDuration(durationMs)}"
}

private fun List<PlaylistSummary>.toLibraryPlaylistRows(
    favoriteTracks: List<LibraryTrackItem>,
    favoritesTitle: String,
    favoritesDescription: String,
): List<LibraryPlaylistRowItem> {
    val favoritesRow = LibraryPlaylistRowItem(
        key = FavoritesPlaylistKey,
        title = favoritesTitle,
        description = favoritesDescription,
        musicCount = favoriteTracks.size.toLong(),
        durationLabel = formatPlaylistDuration(favoriteTracks.sumOf { it.durationMs ?: 0L }),
        isFavorites = true,
        isInitiallyPinned = true,
    )
    val playlistRows = map { playlist ->
        LibraryPlaylistRowItem(
            key = "playlist-${playlist.id}",
            title = playlist.title,
            description = playlist.compactMetadata(),
            musicCount = playlist.musicCount,
            durationLabel = formatPlaylistDuration(playlist.durationMs),
            summary = playlist,
        )
    }

    return listOf(favoritesRow) + playlistRows
}

private fun formatPlaylistDuration(durationMs: Long): String {
    val totalMinutes = (durationMs.coerceAtLeast(0L) + 59_999L) / 60_000L
    return if (totalMinutes >= 60L) {
        "${totalMinutes / 60L}h ${totalMinutes % 60L}m"
    } else {
        "$totalMinutes min"
    }
}

private fun artistInitials(name: String): String = name
    .split(' ')
    .filter { it.isNotBlank() }
    .take(2)
    .joinToString("") { it.take(1).uppercase() }
    .ifBlank { "?" }

private fun libraryArtistRows(
    artists: List<LibraryArtistItem>,
    tracks: List<LibraryTrackItem>,
): List<LibraryArtistRowItem> {
    val trackCounts = tracks
        .mapNotNull { track -> track.artist?.normalizedArtistName()?.takeIf(String::isNotEmpty) }
        .groupingBy { it }
        .eachCount()
    return artists
        .filter { artist -> artist.name.isNotBlank() }
        .map { artist ->
            LibraryArtistRowItem(
                id = artist.id,
                name = artist.name.trim(),
                trackCount = trackCounts[artist.name.normalizedArtistName()] ?: 0,
            )
        }
        .sortedBy { artist -> artist.name.lowercase() }
}

private fun libraryArtistGroups(
    artists: List<LibraryArtistItem>,
    tracks: List<LibraryTrackItem>,
    query: String,
): List<LibraryArtistGroup> {
    val normalizedQuery = query.trim()
    return groupLibraryArtists(
        libraryArtistRows(artists, tracks).filter { artist ->
            normalizedQuery.isEmpty() || artist.name.contains(normalizedQuery, ignoreCase = true)
        },
    )
}

private fun groupLibraryArtists(artists: List<LibraryArtistRowItem>): List<LibraryArtistGroup> {
    return artists
        .groupBy { artist -> artistSectionLabel(artist.name) }
        .entries
        .sortedWith(compareBy({ it.key == "#" }, { it.key }))
        .map { (letter, rows) -> LibraryArtistGroup(letter = letter, artists = rows) }
}

private fun artistSectionLabel(name: String): String {
    val initial = name.trim().firstOrNull()?.uppercaseChar() ?: return "#"
    return if (initial in 'A'..'Z') initial.toString() else "#"
}

private fun String.normalizedArtistName(): String = trim().lowercase()

private fun artistIndexTargets(
    groups: List<LibraryArtistGroup>,
    firstGroupItemIndex: Int,
): List<ArtistIndexTarget> {
    var itemIndex = firstGroupItemIndex
    return groups.map { group ->
        ArtistIndexTarget(letter = group.letter, itemIndex = itemIndex).also {
            itemIndex += 1
        }
    }
}

private fun visibleArtistLetter(
    targets: List<ArtistIndexTarget>,
    firstVisibleItemIndex: Int,
): String? {
    return targets.lastOrNull { target -> firstVisibleItemIndex >= target.itemIndex }?.letter
        ?: targets.firstOrNull()?.letter
}

// ── Sort By ──

private enum class LibrarySortBy(val label: String) {
    Title("Title"),
    Artist("Artist"),
    Album("Album");

    val comparator: Comparator<LibraryTrackItem>
        get() = when (this) {
            Title -> compareBy { it.title }
            Artist -> compareBy { it.artist ?: "" }
            Album -> compareBy { it.title }
        }
}

// ── Constants ──

private data class LibraryAlbumCardItem(
    val id: Long,
    val title: String,
    val artist: String? = null,
    val year: Int? = null,
)

private data class LibraryArtistRowItem(
    val id: Long,
    val name: String,
    val trackCount: Int,
)

private data class LibraryArtistGroup(
    val letter: String,
    val artists: List<LibraryArtistRowItem>,
)

private data class ArtistIndexTarget(
    val letter: String,
    val itemIndex: Int,
)

private data class LibraryGenreCardItem(
    val name: String,
    val albumCount: Int? = null,
)

private data class LibraryPlaylistRowItem(
    val key: String,
    val title: String,
    val description: String,
    val musicCount: Long,
    val durationLabel: String,
    val summary: PlaylistSummary? = null,
    val isFavorites: Boolean = false,
    val isInitiallyPinned: Boolean = false,
)

private const val FavoritesPlaylistKey = "favorites"

private enum class LibraryDesignCategory(
    val label: String,
    val icon: DrawableResource,
    val emptyMessage: String = "No music is available in this collection yet.",
) {
    Playlists("Playlists", CoreRes.drawable.icon_music_note),
    Songs("Songs", CoreRes.drawable.icon_music_note),
    Albums("Albums", CoreRes.drawable.icon_album),
    Artists("Artists", CoreRes.drawable.icon_music_note),
    Genres("Genres", CoreRes.drawable.icon_album),
    Folders("Folders", CoreRes.drawable.icon_folder),
    Favorites("Favorites", CoreRes.drawable.icon_music_note, "Favorite songs will appear here."),
    Downloads("Downloads", CoreRes.drawable.icon_download),
    History("History", CoreRes.drawable.icon_log),
    RecentlyAdded("Recently Added", CoreRes.drawable.icon_album),
    RecentlyPlayed("Recently Played", CoreRes.drawable.icon_log),
    Lossless("Lossless", CoreRes.drawable.icon_music_note, "Lossless tracks appear after scan."),
    HiRes("Hi-Res", CoreRes.drawable.icon_music_note, "Hi-Res tracks appear after scan."),
    Sources("Sources", CoreRes.drawable.icon_cloud),
}

private val primaryLibraryCategories = listOf(
    LibraryDesignCategory.Playlists,
    LibraryDesignCategory.Songs,
    LibraryDesignCategory.Albums,
    LibraryDesignCategory.Artists,
    LibraryDesignCategory.Genres,
)

private val songLibraryCategories = setOf(
    LibraryDesignCategory.Songs,
    LibraryDesignCategory.Favorites,
    LibraryDesignCategory.History,
    LibraryDesignCategory.RecentlyAdded,
    LibraryDesignCategory.RecentlyPlayed,
    LibraryDesignCategory.Lossless,
    LibraryDesignCategory.HiRes,
)

private val libraryArtworkColors = listOf(
    DesignPalette.BrandPink,
    DesignPalette.Secondary,
    DesignPalette.SupportBlue,
    DesignPalette.SupportOrange,
    DesignPalette.SupportGreen,
    DesignPalette.SupportYellow,
)

private val artistIndexLetters = ('A'..'Z').map(Char::toString) + "#"
private const val MOBILE_ARTIST_FIRST_GROUP_ITEM_INDEX = 2
private const val COMPACT_ARTIST_FIRST_GROUP_ITEM_INDEX = 2
private const val DESKTOP_ARTIST_FIRST_GROUP_ITEM_INDEX = 3
