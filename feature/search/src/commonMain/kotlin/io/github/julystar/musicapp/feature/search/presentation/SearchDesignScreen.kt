package io.github.julystar.musicapp.feature.search.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.presentation.components.QualityBadge
import io.github.julystar.musicapp.core.presentation.components.QualityBadgeType
import io.github.julystar.musicapp.core.presentation.components.LiquidGlassScene
import io.github.julystar.musicapp.core.presentation.components.LocalDesignBottomContentInset
import io.github.julystar.musicapp.core.presentation.components.LiquidGlassActionBar
import io.github.julystar.musicapp.core.presentation.media.ArtworkImage
import io.github.julystar.musicapp.core.presentation.theme.DesignPalette
import io.github.julystar.musicapp.core.presentation.theme.DesignFontFamilies
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.julystar.musicapp.core.presentation.transition.albumArtworkSharedElement
import io.github.julystar.musicapp.feature.search.domain.SearchAlbumItem
import io.github.julystar.musicapp.feature.search.domain.SearchArtistItem
import io.github.julystar.musicapp.feature.search.domain.SearchTrackItem
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import musicapp.feature.search.generated.resources.Res
import musicapp.feature.search.generated.resources.icon_music_note
import musicapp.feature.search.generated.resources.icon_search
import musicapp.feature.search.generated.resources.search_clear
import musicapp.feature.search.generated.resources.search_cover_1
import musicapp.feature.search.generated.resources.search_cover_2
import musicapp.feature.search.generated.resources.search_cover_3
import musicapp.feature.search.generated.resources.search_cover_4
import musicapp.feature.search.generated.resources.search_cover_5
import musicapp.feature.search.generated.resources.search_cover_6
import musicapp.feature.search.generated.resources.search_hint
import musicapp.feature.search.generated.resources.search_connection_retry
import musicapp.feature.search.generated.resources.search_no_matches_yet
import musicapp.feature.search.generated.resources.search_recent_searches
import musicapp.feature.search.generated.resources.search_sources_unavailable
import musicapp.feature.search.generated.resources.search_suggestions
import musicapp.feature.search.generated.resources.search_title
import musicapp.feature.search.generated.resources.search_retry
import musicapp.feature.search.generated.resources.search_results_title
import musicapp.feature.search.generated.resources.search_result_type_album
import musicapp.feature.search.generated.resources.search_result_type_artist
import musicapp.feature.search.generated.resources.search_result_summary
import musicapp.feature.search.generated.resources.search_empty
import musicapp.feature.search.generated.resources.search_trending_library
import musicapp.feature.search.generated.resources.search_trending_subtitle
import musicapp.feature.search.generated.resources.search_try_query
import musicapp.feature.search.generated.resources.searching_library
import musicapp.core.presentation.generated.resources.Res as CoreRes
import musicapp.core.presentation.generated.resources.icon_timelapse
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SearchDesignScreen(
    state: SearchState,
    showSearchContent: Boolean = true,
    onAction: (SearchAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bottomContentInset = LocalDesignBottomContentInset.current
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val actionBarProgress = topAppBarScrollBehavior.state.collapsedFraction
    val pageTitle = stringResource(Res.string.search_title)
    var showDefaultRecentSearches by remember { mutableStateOf(true) }
    val clearRecentSearches = {
        showDefaultRecentSearches = false
        onAction(SearchAction.ClearHistory)
    }

    LiquidGlassScene(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        ) {
        val compact = maxWidth < DesignTokens.adaptive.largeMinWidth
        val pagePadding = if (compact) 24.dp else DesignTokens.spacing.pageExpanded
        val listState = rememberLazyListState()

        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = pageTitle,
                largeTitle = pageTitle,
                color = Color.Transparent,
                titleColor = Color.Transparent,
                scrollBehavior = topAppBarScrollBehavior,
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = DesignTokens.adaptive.contentMaxWidth),
                contentPadding = PaddingValues(
                    start = pagePadding,
                    top = if (compact) 20.dp else 28.dp,
                    end = pagePadding,
                    bottom = 28.dp + bottomContentInset,
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    InputField(
                        query = state.query,
                        onQueryChange = { query ->
                            onAction(if (query.isEmpty()) SearchAction.ClearQuery else SearchAction.QueryChanged(query))
                        },
                        onSearch = { onAction(SearchAction.SubmitSearch) },
                        label = stringResource(Res.string.search_hint),
                        expanded = false,
                        onExpandedChange = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                if (showSearchContent) when (state.loadState) {
                    SearchLoadState.Searching -> {
                        item {
                            SearchStatus(
                                title = stringResource(Res.string.searching_library),
                                message = state.query,
                                loading = true,
                            )
                        }
                    }
                    SearchLoadState.Error -> {
                        item {
                            SearchStatus(
                                title = stringResource(Res.string.search_sources_unavailable),
                                message = stringResource(Res.string.search_connection_retry),
                                actionLabel = stringResource(Res.string.search_retry),
                                onAction = { onAction(SearchAction.Retry) },
                            )
                        }
                    }
                    SearchLoadState.Empty -> {
                        item {
                            SearchStatus(
                                title = stringResource(Res.string.search_no_matches_yet),
                                message = stringResource(Res.string.search_try_query),
                                actionLabel = "Clear search",
                                onAction = { onAction(SearchAction.ClearQuery) },
                            )
                        }
                    }
                    SearchLoadState.Results -> {
                        item {
                            SearchResultsSummary(trackCount = state.tracks.size, albumCount = state.albums.size, artistCount = state.artists.size)
                        }
                        item {
                            SearchResultSectionHeader("Songs", state.tracks.size)
                        }
                        itemsIndexed(
                            items = state.tracks,
                            key = { index, track -> track.lazyListKey(index) },
                        ) { index, track ->
                            SearchResultRow(
                                rank = index + 1,
                                track = track,
                                onOpen = { onAction(SearchAction.OpenTrack(track)) },
                            )
                        }
                        if (state.albums.isNotEmpty()) {
                            item {
                                SearchResultSectionHeader("Albums", state.albums.size)
                            }
                            items(
                                items = state.albums,
                                key = { album -> "search-album-${album.id}" },
                            ) { album ->
                                SearchAlbumResultRow(
                                    album = album,
                                    onClick = { onAction(SearchAction.OpenAlbum(album)) },
                                )
                            }
                        }
                        if (state.artists.isNotEmpty()) {
                            item {
                                SearchResultSectionHeader("Artists", state.artists.size)
                            }
                            items(
                                items = state.artists,
                                key = { artist -> "search-artist-${artist.id}" },
                            ) { artist ->
                                SearchArtistResultRow(
                                    artist = artist,
                                    onClick = { onAction(SearchAction.OpenArtist(artist)) },
                                )
                            }
                        }
                    }
                    SearchLoadState.Idle,
                    SearchLoadState.Typing -> {
                        item {
                            SearchDiscovery(
                                state = state,
                                onAction = onAction,
                                showDefaultRecentSearches = showDefaultRecentSearches,
                                onClearRecentSearches = clearRecentSearches,
                            )
                        }
                    }
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

@Composable
private fun SearchDiscovery(
    state: SearchState,
    onAction: (SearchAction) -> Unit,
    showDefaultRecentSearches: Boolean,
    onClearRecentSearches: () -> Unit,
) {
    if (state.query.isNotBlank()) {
        SearchQueryChips(
            title = stringResource(Res.string.search_suggestions),
            searches = state.suggestions.take(8),
            onSelect = { onAction(SearchAction.SelectSuggestion(it)) },
        )
        return
    }

    val recentSearches = state.history
        .take(8)
        .ifEmpty { if (showDefaultRecentSearches) defaultRecentSearches else emptyList() }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (recentSearches.isNotEmpty()) {
            SearchQueryChips(
                title = stringResource(Res.string.search_recent_searches),
                searches = recentSearches,
                onSelect = { onAction(SearchAction.SelectSuggestion(it)) },
                onClear = onClearRecentSearches,
            )
        }
        SearchTrendingSection(
            onSelect = { onAction(SearchAction.SelectSuggestion(it)) },
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SearchQueryChips(
    title: String,
    searches: List<String>,
    onSelect: (String) -> Unit,
    onClear: (() -> Unit)? = null,
) {
    if (searches.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onBackground,
                style = MiuixTheme.textStyles.title3.copy(fontSize = 20.sp, lineHeight = 26.sp),
                fontWeight = FontWeight.SemiBold,
            )
            if (onClear != null) {
                Text(
                    text = stringResource(Res.string.search_clear),
                    color = MiuixTheme.colorScheme.primary,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onClear)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            searches.forEach { label ->
                SearchChip(label = label, onClick = { onSelect(label) })
            }
        }
    }
}

@Composable
private fun SearchChip(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(DesignTokens.shapes.full))
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            painter = painterResource(CoreRes.drawable.icon_timelapse),
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body1,
        )
    }
}

@Composable
private fun SearchTrendingSection(
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(
            modifier = Modifier.padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(Res.string.search_trending_library),
                color = MiuixTheme.colorScheme.onBackground,
                style = MiuixTheme.textStyles.title3.copy(fontSize = 20.sp, lineHeight = 26.sp),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(Res.string.search_trending_subtitle),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
        designTrendingTracks.forEachIndexed { index, track ->
            SearchTrendingRow(
                rank = index + 1,
                track = track,
                onClick = { onSelect(track.title) },
            )
        }
    }
}

@Composable
private fun SearchTrendingRow(
    rank: Int,
    track: SearchTrendingTrack,
    onClick: () -> Unit,
) {
    Column {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = rank.toString(),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(20.dp),
        )
        Image(
            painter = painterResource(track.artwork),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(11.dp)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = track.title,
                color = MiuixTheme.colorScheme.onBackground,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${track.artist} · ${track.album}",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    HorizontalDivider()
    }
}

@Composable
private fun SearchResultsSummary(
    trackCount: Int,
    albumCount: Int = 0,
    artistCount: Int = 0,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = stringResource(Res.string.search_results_title),
            color = MiuixTheme.colorScheme.onBackground,
            style = MiuixTheme.textStyles.title2,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (trackCount + albumCount + artistCount == 0) {
                stringResource(Res.string.search_empty)
            } else {
                stringResource(
                    Res.string.search_result_summary,
                    trackCount,
                    albumCount,
                    artistCount,
                )
            },
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            style = MiuixTheme.textStyles.footnote1,
        )
    }
}

@Composable
private fun SearchResultSectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onBackground,
            style = MiuixTheme.textStyles.subtitle,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "$count",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
        )
    }
}

@Composable
private fun SearchResultRow(
    rank: Int,
    track: SearchTrackItem,
    onOpen: () -> Unit,
) {
    Column {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = rank.toString(),
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(18.dp),
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            searchTrackGradients[(rank - 1) % searchTrackGradients.size].first,
                            searchTrackGradients[(rank - 1) % searchTrackGradients.size].second,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.icon_music_note),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.82f),
                modifier = Modifier.size(16.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                QualityBadge(type = track.qualityBadgeType())
            }
            Text(
                text = listOfNotNull(track.artist, track.sourceLabel.takeIf { it.isNotBlank() }).joinToString(" · "),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = track.durationMs.durationText(),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1.copy(fontFamily = DesignFontFamilies.Mono),
        )
    }
    HorizontalDivider()
    }
}

@Composable

private fun SearchAlbumResultRow(
    album: SearchAlbumItem,
    onClick: () -> Unit,
) {
    Column {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ArtworkImage(
            artwork = Artwork.LibraryAlbum(album.id),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .albumArtworkSharedElement(album.id)
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp)),
            fallback = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(DesignPalette.SupportBlue, DesignPalette.SupportGreen),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.icon_music_note),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.82f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            },
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = album.name,
                color = MiuixTheme.colorScheme.onBackground,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (album.artist != null) {
                Text(
                    text = album.artist,
                    color = MiuixTheme.colorScheme.onBackgroundVariant,
                    style = MiuixTheme.textStyles.footnote1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = stringResource(Res.string.search_result_type_album),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
        )
    }
    HorizontalDivider()
    }
}

@Composable
private fun SearchArtistResultRow(
    artist: SearchArtistItem,
    onClick: () -> Unit,
) {
    Column {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(DesignPalette.SupportOrange, DesignPalette.BrandPink),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = artist.name.take(1).uppercase(),
                color = Color.White,
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
        }
        Text(
            text = stringResource(Res.string.search_result_type_artist),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
        )
    }
    HorizontalDivider()
    }
}

@Composable
private fun SearchStatus(
    title: String,
    message: String,
    loading: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MiuixTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(size = 22.dp)
                } else {
                    Icon(
                        painter = painterResource(Res.drawable.icon_search),
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = title,
                    color = MiuixTheme.colorScheme.onSurface,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = message,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (actionLabel != null && onAction != null) {
                Text(
                    text = actionLabel,
                    color = MiuixTheme.colorScheme.primary,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .heightIn(min = DesignTokens.adaptive.minimumTouchTarget)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onAction)
                        .padding(8.dp),
                )
            }
        }
    }
}

private fun Long?.durationText(): String {
    val duration = this?.milliseconds ?: return "--:--"
    val minutes = duration.inWholeMinutes
    val seconds = duration.inWholeSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun SearchTrackItem.qualityBadgeType(): QualityBadgeType = when {
    sourceLabel.contains("Hi-Res", ignoreCase = true) -> QualityBadgeType.HiRes
    sourceLabel.contains("Lossless", ignoreCase = true) -> QualityBadgeType.Flac
    sourceLabel.contains("Dolby", ignoreCase = true) -> QualityBadgeType.DolbyAtmos
    else -> QualityBadgeType.Flac
}

private val searchTrackGradients = listOf(
    DesignPalette.BrandPink to DesignPalette.Secondary,
    DesignPalette.Secondary to DesignPalette.SupportBlue,
    DesignPalette.SupportOrange to DesignPalette.BrandPink,
    DesignPalette.SupportGreen to DesignPalette.SupportBlue,
)

private data class SearchTrendingTrack(
    val title: String,
    val artist: String,
    val album: String,
    val artwork: DrawableResource,
)

private val defaultRecentSearches = listOf(
    "Luna Waves",
    "Synthwave",
    "Midnight Cascade",
    "Hi-Res",
    "Ambient",
)

private val designTrendingTracks = listOf(
    SearchTrendingTrack("Midnight Cascade", "Luna Waves", "Tidal Drift", Res.drawable.search_cover_1),
    SearchTrendingTrack("Neon Undertow", "Prism Circuit", "Voltage Dreams", Res.drawable.search_cover_2),
    SearchTrendingTrack("Silver Tide", "Coastal Drift", "Open Water", Res.drawable.search_cover_3),
    SearchTrendingTrack("Aurora Sequence", "Polar Echo", "Northern Lights", Res.drawable.search_cover_4),
    SearchTrendingTrack("Depth Protocol", "Ocean Syntax", "Subsonic", Res.drawable.search_cover_5),
    SearchTrendingTrack("Glass Architecture", "Fractal Mind", "Prism", Res.drawable.search_cover_6),
)
