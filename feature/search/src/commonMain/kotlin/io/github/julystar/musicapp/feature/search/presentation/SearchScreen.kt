package io.github.julystar.musicapp.feature.search.presentation

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.components.TagChip
import io.github.julystar.musicapp.core.presentation.components.StatusBadge
import io.github.julystar.musicapp.core.presentation.components.StatusMessageCard
import io.github.julystar.musicapp.core.presentation.components.StatusTone
import io.github.julystar.musicapp.core.presentation.components.LocalDesignBottomContentInset
import io.github.julystar.musicapp.core.presentation.theme.DesignPalette
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.julystar.musicapp.feature.search.domain.LOCAL_LIBRARY_SOURCE_LABEL
import io.github.julystar.musicapp.feature.search.domain.SearchAlbumItem
import io.github.julystar.musicapp.feature.search.domain.SearchArtistItem
import io.github.julystar.musicapp.feature.search.domain.SearchTrackItem
import musicapp.feature.search.generated.resources.Res
import musicapp.feature.search.generated.resources.icon_download
import musicapp.feature.search.generated.resources.icon_music_note
import musicapp.feature.search.generated.resources.icon_search
import musicapp.feature.search.generated.resources.search_albums
import musicapp.feature.search.generated.resources.search_artists
import musicapp.feature.search.generated.resources.search_clear
import musicapp.feature.search.generated.resources.search_clear_search
import musicapp.feature.search.generated.resources.search_connection_retry
import musicapp.feature.search.generated.resources.search_download
import musicapp.feature.search.generated.resources.search_empty
import musicapp.feature.search.generated.resources.search_hint
import musicapp.feature.search.generated.resources.search_local_library
import musicapp.feature.search.generated.resources.search_no_history
import musicapp.feature.search.generated.resources.search_no_matches_yet
import musicapp.feature.search.generated.resources.search_recent_searches
import musicapp.feature.search.generated.resources.search_result_count
import musicapp.feature.search.generated.resources.search_result_summary
import musicapp.feature.search.generated.resources.search_retry
import musicapp.feature.search.generated.resources.search_songs
import musicapp.feature.search.generated.resources.search_source_failures
import musicapp.feature.search.generated.resources.search_sources_unavailable
import musicapp.feature.search.generated.resources.search_subtitle
import musicapp.feature.search.generated.resources.search_suggestions
import musicapp.feature.search.generated.resources.search_title
import musicapp.feature.search.generated.resources.search_try_query
import musicapp.feature.search.generated.resources.search_unknown_artist
import musicapp.feature.search.generated.resources.searching_library
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SearchScreen(
    state: SearchState,
    onAction: (SearchAction) -> Unit,
    modifier: Modifier = Modifier,
    showSearchContent: Boolean = true,
) {
    val spacing = DesignTokens.spacing
    val bottomInset = LocalDesignBottomContentInset.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val horizontalPadding = if (maxWidth < 600.dp) spacing.pageCompact else spacing.pageExpanded
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
                .padding(horizontal = horizontalPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            TopAppBar(
                title = stringResource(Res.string.search_title),
                subtitle = stringResource(Res.string.search_subtitle),
            )
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
            if (state.failedSourceCount > 0) {
                StatusBadge(
                    label = stringResource(Res.string.search_source_failures, state.failedSourceCount),
                    tone = StatusTone.Error,
                )
            }
            if (showSearchContent) {
                SearchBody(
                    state = state,
                    onAction = onAction,
                    modifier = Modifier.weight(1f),
                    bottomInset = bottomInset,
                )
            }
        }
    }
}

@Composable
private fun SearchBody(
    state: SearchState,
    onAction: (SearchAction) -> Unit,
    modifier: Modifier,
    bottomInset: androidx.compose.ui.unit.Dp,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = DesignTokens.spacing.lg + bottomInset),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (state.loadState) {
            SearchLoadState.Searching -> item {
                StatusMessageCard(
                    title = stringResource(Res.string.searching_library),
                    message = state.query.ifBlank { stringResource(Res.string.search_hint) },
                    loading = true,
                )
            }

            SearchLoadState.Error -> {
                item {
                    StatusMessageCard(
                        title = stringResource(Res.string.search_sources_unavailable),
                        message = stringResource(Res.string.search_connection_retry),
                        actionText = stringResource(Res.string.search_retry),
                        onAction = { onAction(SearchAction.Retry) },
                    )
                }
                addDiscovery(state, onAction)
            }

            SearchLoadState.Empty -> {
                item {
                    StatusMessageCard(
                        title = stringResource(Res.string.search_no_matches_yet),
                        message = stringResource(Res.string.search_try_query),
                        actionText = stringResource(Res.string.search_clear_search),
                        onAction = { onAction(SearchAction.ClearQuery) },
                    )
                }
                addDiscovery(state, onAction)
            }

            SearchLoadState.Results -> {
                item {
                    Text(
                        text = stringResource(
                            Res.string.search_result_summary,
                            state.tracks.size,
                            state.albums.size,
                            state.artists.size,
                        ),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.body2,
                    )
                }
                if (state.tracks.isNotEmpty()) {
                    item { ResultHeader(Res.string.search_songs, state.tracks.size) }
                    itemsIndexed(
                        items = state.tracks,
                        key = { index, track -> track.stableKey(index) },
                    ) { index, track ->
                        TrackResult(
                            rank = index + 1,
                            track = track,
                            onOpen = { onAction(SearchAction.OpenTrack(track)) },
                            onDownload = { onAction(SearchAction.DownloadTrack(track)) },
                        )
                    }
                }
                if (state.albums.isNotEmpty()) {
                    item { ResultHeader(Res.string.search_albums, state.albums.size) }
                    items(state.albums, key = { it.id }) { album ->
                        AlbumResult(album) { onAction(SearchAction.OpenAlbum(album)) }
                    }
                }
                if (state.artists.isNotEmpty()) {
                    item { ResultHeader(Res.string.search_artists, state.artists.size) }
                    items(state.artists, key = { it.id }) { artist ->
                        ArtistResult(artist) { onAction(SearchAction.OpenArtist(artist)) }
                    }
                }
                if (state.tracks.isEmpty() && state.albums.isEmpty() && state.artists.isEmpty()) {
                    item {
                        StatusMessageCard(
                            title = stringResource(Res.string.search_empty),
                            message = stringResource(Res.string.search_try_query),
                        )
                    }
                }
            }

            SearchLoadState.Idle,
            SearchLoadState.Typing -> addDiscovery(state, onAction)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.addDiscovery(
    state: SearchState,
    onAction: (SearchAction) -> Unit,
) {
    val recent = state.history.take(8)
    val suggestions = state.suggestions
        .filterNot { it in recent }
        .take(10)

    if (recent.isNotEmpty()) {
        item {
            SearchHistoryTags(
                labels = recent,
                onSelect = { onAction(SearchAction.SelectSuggestion(it)) },
                onClear = { onAction(SearchAction.ClearHistory) },
            )
        }
    }
    if (suggestions.isNotEmpty()) {
        item {
            SearchSuggestionTags(
                labels = suggestions,
                onSelect = { onAction(SearchAction.SelectSuggestion(it)) },
            )
        }
    }
    if (recent.isEmpty() && suggestions.isEmpty()) {
        item {
            StatusMessageCard(
                title = stringResource(Res.string.search_no_history),
                message = stringResource(Res.string.search_try_query),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SearchHistoryTags(
    labels: List<String>,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            SmallTitle(
                text = stringResource(Res.string.search_recent_searches),
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(Res.string.search_clear),
                onClick = onClear,
            )
        }
        SearchTagFlow(labels = labels, onSelect = onSelect)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SearchSuggestionTags(
    labels: List<String>,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SmallTitle(text = stringResource(Res.string.search_suggestions))
        SearchTagFlow(labels = labels, onSelect = onSelect)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SearchTagFlow(labels: List<String>, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEach { label ->
            TagChip(
                label = label,
                leading = {
                    Icon(
                        painter = painterResource(Res.drawable.icon_search),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                },
                onClick = { onSelect(label) },
            )
        }
    }
}

@Composable
private fun ResultHeader(titleRes: org.jetbrains.compose.resources.StringResource, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(titleRes),
            color = MiuixTheme.colorScheme.onBackground,
            style = MiuixTheme.textStyles.subtitle,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(Res.string.search_result_count, count),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
        )
    }
}

@Composable
private fun TrackResult(
    rank: Int,
    track: SearchTrackItem,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(DesignPalette.BrandPink, DesignPalette.Secondary))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.icon_music_note),
                tint = Color.White.copy(alpha = 0.86f),
                contentDescription = null,
                modifier = Modifier.size(17.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist ?: stringResource(Res.string.search_unknown_artist),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (track.sourceLabel == LOCAL_LIBRARY_SOURCE_LABEL) {
                    stringResource(Res.string.search_local_library)
                } else {
                    track.sourceLabel
                },
                color = MiuixTheme.colorScheme.primary,
                style = MiuixTheme.textStyles.footnote2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        track.durationMs?.let {
            Text(
                text = durationLabel(it),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
        if (track.mediaId != null) {
            Icon(
                painter = painterResource(Res.drawable.icon_download),
                tint = MiuixTheme.colorScheme.primary,
                contentDescription = stringResource(Res.string.search_download),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onDownload)
                    .padding(6.dp)
                    .size(18.dp),
            )
        }
    }
}

@Composable
private fun AlbumResult(album: SearchAlbumItem, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ResultMarker(album.name.take(1).uppercase())
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.name,
                    color = MiuixTheme.colorScheme.onSurface,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                album.artist?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistResult(artist: SearchArtistItem, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ResultMarker(artist.name.take(1).uppercase())
            Text(
                text = artist.name,
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ResultMarker(label: String) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.tertiaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.primary,
            style = MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun SearchTrackItem.stableKey(index: Int): String {
    val mediaKey = mediaId?.let { "${it.sourceId.value}:${it.remoteId}" }
        ?: "local:${id ?: index}"
    return "search-track-$index-$mediaKey"
}

private fun durationLabel(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1_000L
    return "${totalSeconds / 60L}:${(totalSeconds % 60L).toString().padStart(2, '0')}"
}
