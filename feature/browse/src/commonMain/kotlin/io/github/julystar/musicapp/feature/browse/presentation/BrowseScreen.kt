package io.github.julystar.musicapp.feature.browse.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.components.TagChip
import io.github.julystar.musicapp.core.presentation.components.StatusMessageCard
import io.github.julystar.musicapp.core.presentation.components.LocalDesignBottomContentInset
import io.github.julystar.musicapp.core.presentation.media.ArtworkImage
import io.github.julystar.musicapp.core.presentation.theme.DesignPalette
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.julystar.musicapp.core.presentation.transition.albumArtworkSharedElement
import musicapp.feature.browse.generated.resources.Res
import musicapp.feature.browse.generated.resources.browse_album_meta
import musicapp.feature.browse.generated.resources.browse_albums
import musicapp.feature.browse.generated.resources.browse_artists
import musicapp.feature.browse.generated.resources.browse_genres
import musicapp.feature.browse.generated.resources.browse_import_first
import musicapp.feature.browse.generated.resources.browse_loading
import musicapp.feature.browse.generated.resources.browse_loading_message
import musicapp.feature.browse.generated.resources.browse_no_content
import musicapp.feature.browse.generated.resources.browse_retry
import musicapp.feature.browse.generated.resources.browse_summary
import musicapp.feature.browse.generated.resources.browse_title
import musicapp.feature.browse.generated.resources.browse_track_count
import musicapp.feature.browse.generated.resources.browse_unavailable
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BrowseScreen(
    state: BrowseState,
    onAction: (BrowseAction) -> Unit,
) {
    val spacing = DesignTokens.spacing
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val horizontalPadding = if (maxWidth < 600.dp) spacing.pageCompact else spacing.pageExpanded

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
                .padding(horizontal = horizontalPadding, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TopAppBar(
                title = stringResource(Res.string.browse_title),
                subtitle = stringResource(
                    Res.string.browse_summary,
                    state.albums.size,
                    state.artists.size,
                    state.genres.size,
                ),
            )
            when {
                state.isLoading -> StatusMessageCard(
                    title = stringResource(Res.string.browse_loading),
                    message = stringResource(Res.string.browse_loading_message),
                    loading = true,
                    modifier = Modifier.weight(1f),
                )
                state.error != null -> StatusMessageCard(
                    title = stringResource(Res.string.browse_unavailable),
                    message = stringResource(Res.string.browse_loading_message),
                    actionText = stringResource(Res.string.browse_retry),
                    onAction = { onAction(BrowseAction.Retry) },
                    modifier = Modifier.weight(1f),
                )
                else -> BrowseContent(
                    state = state,
                    onAction = onAction,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

internal fun BrowseAlbumItem.lazyListKey(index: Int): String = "browse-album-$index-$id"
internal fun BrowseArtistItem.lazyListKey(index: Int): String = "browse-artist-$index-$id"

@Composable
private fun BrowseContent(
    state: BrowseState,
    onAction: (BrowseAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bottomContentInset = LocalDesignBottomContentInset.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(bottom = 24.dp + bottomContentInset),
    ) {
        if (state.albums.isNotEmpty()) {
            item {
                BrowseSectionTitle(
                    title = stringResource(Res.string.browse_albums),
                    count = state.albums.size,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(
                        state.albums,
                        key = { index, album -> album.lazyListKey(index) },
                    ) { _, album ->
                        BrowseAlbumCard(
                            album = album,
                            onClick = { onAction(BrowseAction.NavigateToAlbum(album.id)) },
                        )
                    }
                }
            }
        }
        if (state.artists.isNotEmpty()) {
            item {
                BrowseSectionTitle(
                    title = stringResource(Res.string.browse_artists),
                    count = state.artists.size,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(
                        state.artists,
                        key = { index, artist -> artist.lazyListKey(index) },
                    ) { _, artist ->
                        BrowseArtistCard(
                            artist = artist,
                            onClick = { onAction(BrowseAction.NavigateToArtist(artist.id)) },
                        )
                    }
                }
            }
        }
        if (state.genres.isNotEmpty()) {
            item {
                BrowseGenreTags(
                    genres = state.genres,
                    onGenreClick = { genre -> onAction(BrowseAction.NavigateToGenre(genre)) },
                )
            }
        }
        if (state.albums.isEmpty() && state.artists.isEmpty() && state.genres.isEmpty()) {
            item {
                StatusMessageCard(
                    title = stringResource(Res.string.browse_no_content),
                    message = stringResource(Res.string.browse_import_first),
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun BrowseGenreTags(
    genres: List<String>,
    onGenreClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            SmallTitle(
                text = stringResource(Res.string.browse_genres),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = genres.size.toString(),
                color = MiuixTheme.colorScheme.primary,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            genres.forEach { genre ->
                TagChip(label = genre, onClick = { onGenreClick(genre) })
            }
        }
    }
}

@Composable
private fun BrowseSectionTitle(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallTitle(text = title)
        Text(
            text = count.toString(),
            color = MiuixTheme.colorScheme.primary,
            style = MiuixTheme.textStyles.footnote1,
        )
    }
}

@Composable
private fun BrowseAlbumCard(album: BrowseAlbumItem, onClick: () -> Unit) {
    val shapes = DesignTokens.shapes
    Card(
        modifier = Modifier.width(156.dp),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ArtworkImage(
                artwork = album.artwork,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .albumArtworkSharedElement(album.id)
                    .fillMaxWidth()
                    .height(136.dp)
                    .clip(RoundedCornerShape(shapes.md)),
            )
            Text(
                text = album.name,
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (album.year == null) {
                    stringResource(Res.string.browse_track_count, album.trackCount)
                } else {
                    stringResource(Res.string.browse_album_meta, album.year, album.trackCount)
                },
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BrowseArtistCard(artist: BrowseArtistItem, onClick: () -> Unit) {
    val shapes = DesignTokens.shapes
    Card(
        modifier = Modifier.width(140.dp),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(RoundedCornerShape(shapes.full))
                    .background(DesignPalette.Secondary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = artist.name.take(1).uppercase(),
                    style = MiuixTheme.textStyles.title2,
                    color = MiuixTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Text(
                text = artist.name,
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(Res.string.browse_track_count, artist.trackCount),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                maxLines = 1,
            )
        }
    }
}
