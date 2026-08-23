package io.github.julystar.musicapp.feature.library.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.domain.model.LibraryAlbumItem
import io.github.julystar.musicapp.core.domain.model.LibraryArtistItem
import io.github.julystar.musicapp.core.domain.model.LibraryTrackItem
import io.github.julystar.musicapp.core.domain.model.PlaylistSummary
import io.github.julystar.musicapp.core.domain.model.RepositoryState
import io.github.julystar.musicapp.core.domain.repository.LibraryFolderItem
import io.github.julystar.musicapp.core.presentation.components.EmptyState
import io.github.julystar.musicapp.core.presentation.components.StatusMessageCard
import io.github.julystar.musicapp.core.presentation.theme.DesignPalette
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import musicapp.core.presentation.generated.resources.Res as CorePresentationRes
import musicapp.core.presentation.generated.resources.icon_album
import musicapp.core.presentation.generated.resources.icon_chevron_right
import musicapp.core.presentation.generated.resources.icon_download
import musicapp.core.presentation.generated.resources.icon_folder
import musicapp.core.presentation.generated.resources.icon_mode_list
import musicapp.core.presentation.generated.resources.icon_music_note
import musicapp.core.presentation.generated.resources.icon_pause
import musicapp.feature.library.generated.resources.Res
import musicapp.feature.library.generated.resources.library_add_folder
import musicapp.feature.library.generated.resources.library_category_albums
import musicapp.feature.library.generated.resources.library_category_artists
import musicapp.feature.library.generated.resources.library_category_downloads
import musicapp.feature.library.generated.resources.library_category_favorites
import musicapp.feature.library.generated.resources.library_category_folders
import musicapp.feature.library.generated.resources.library_category_genres
import musicapp.feature.library.generated.resources.library_category_hires
import musicapp.feature.library.generated.resources.library_category_history
import musicapp.feature.library.generated.resources.library_category_lossless
import musicapp.feature.library.generated.resources.library_category_playlists
import musicapp.feature.library.generated.resources.library_category_recently_added
import musicapp.feature.library.generated.resources.library_category_recently_played
import musicapp.feature.library.generated.resources.library_category_songs
import musicapp.feature.library.generated.resources.library_category_sources
import musicapp.feature.library.generated.resources.library_download
import musicapp.feature.library.generated.resources.library_duration_hours
import musicapp.feature.library.generated.resources.library_duration_minutes
import musicapp.feature.library.generated.resources.library_empty_albums
import musicapp.feature.library.generated.resources.library_empty_artists
import musicapp.feature.library.generated.resources.library_empty_downloads
import musicapp.feature.library.generated.resources.library_empty_favorites
import musicapp.feature.library.generated.resources.library_empty_folders
import musicapp.feature.library.generated.resources.library_empty_genres
import musicapp.feature.library.generated.resources.library_empty_hires
import musicapp.feature.library.generated.resources.library_empty_history
import musicapp.feature.library.generated.resources.library_empty_lossless
import musicapp.feature.library.generated.resources.library_empty_message
import musicapp.feature.library.generated.resources.library_empty_playlists
import musicapp.feature.library.generated.resources.library_empty_recently_added
import musicapp.feature.library.generated.resources.library_empty_recently_played
import musicapp.feature.library.generated.resources.library_empty_sources
import musicapp.feature.library.generated.resources.library_empty_title
import musicapp.feature.library.generated.resources.library_folder_track_count
import musicapp.feature.library.generated.resources.library_loading
import musicapp.feature.library.generated.resources.library_playlist_track_count
import musicapp.feature.library.generated.resources.library_retry
import musicapp.feature.library.generated.resources.library_song_summary
import musicapp.feature.library.generated.resources.library_title
import musicapp.feature.library.generated.resources.library_unavailable
import musicapp.feature.library.generated.resources.library_unknown_artist
import musicapp.feature.library.generated.resources.library_unknown_year
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LibraryScreen(
    state: LibraryState,
    currentPlayingTrackId: Long? = null,
    onNavigateToLibraryFolderImport: () -> Unit = {},
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (Long) -> Unit = {},
    onNavigateToPlaylist: (Long) -> Unit = {},
    onNavigateToPlaylists: () -> Unit = {},
    onAction: (LibraryAction) -> Unit,
) {
    var category by remember { mutableStateOf(LibraryCategory.Songs) }
    val categories = LibraryCategory.entries
    val labels = categories.map { stringResource(it.labelRes) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = DesignTokens.spacing.pageCompact,
            top = 8.dp,
            end = DesignTokens.spacing.pageCompact,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            TopAppBar(
                title = stringResource(Res.string.library_title),
                subtitle = "",
            )
        }
        item {
            TabRow(
                tabs = labels,
                selectedTabIndex = categories.indexOf(category),
                onTabSelected = { category = categories[it] },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        when (category) {
            LibraryCategory.Songs -> {
                item { SongSummary(state.tracks) }
                addTrackItems(
                    tracks = state.tracks,
                    category = category,
                    currentPlayingTrackId = currentPlayingTrackId,
                    onAction = onAction,
                    rootEmpty = true,
                )
            }

            LibraryCategory.Albums -> {
                if (state.albums.isEmpty()) {
                    item { CategoryEmpty(category) }
                } else {
                    items(state.albums, key = { it.id }) { album ->
                        AlbumRow(album = album, onClick = { onNavigateToAlbum(album.id) })
                    }
                }
            }

            LibraryCategory.Artists -> {
                if (state.artists.isEmpty()) {
                    item { CategoryEmpty(category) }
                } else {
                    items(state.artists, key = { it.id }) { artist ->
                        ArtistRow(artist = artist, onClick = { onNavigateToArtist(artist.id) })
                    }
                }
            }

            LibraryCategory.Genres -> when (val value = state.genreNames) {
                RepositoryState.Loading -> item { LoadingState() }
                is RepositoryState.Error -> item { ErrorState { onAction(LibraryAction.Refresh) } }
                is RepositoryState.Empty -> item { CategoryEmpty(category) }
                is RepositoryState.Loaded -> {
                    if (value.data.isEmpty()) {
                        item { CategoryEmpty(category) }
                    } else {
                        items(value.data, key = { it }) { genre ->
                            NavigationRow(
                                title = genre,
                                painter = painterResource(CorePresentationRes.drawable.icon_music_note),
                                onClick = { onAction(LibraryAction.SelectGenre(genre)) },
                            )
                        }
                    }
                }
            }

            LibraryCategory.Folders -> {
                when (val value = state.folders) {
                    RepositoryState.Loading -> item { LoadingState() }
                    is RepositoryState.Error -> item { ErrorState { onAction(LibraryAction.Refresh) } }
                    is RepositoryState.Empty -> item { CategoryEmpty(category) }
                    is RepositoryState.Loaded -> {
                        if (value.data.isEmpty()) {
                            item { CategoryEmpty(category) }
                        } else {
                            items(value.data, key = { it.path }) { folder ->
                                FolderRow(folder) {
                                    onAction(LibraryAction.BrowseFolder(folder.path))
                                }
                            }
                        }
                    }
                }
                item {
                    TextButton(
                        text = stringResource(Res.string.library_add_folder),
                        onClick = onNavigateToLibraryFolderImport,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            LibraryCategory.Playlists -> {
                if (state.playlists.isEmpty()) {
                    item {
                        CategoryEmpty(
                            category = category,
                            onClick = onNavigateToPlaylists,
                        )
                    }
                } else {
                    items(state.playlists, key = { it.id }) { playlist ->
                        PlaylistRow(
                            playlist = playlist,
                            onClick = { onNavigateToPlaylist(playlist.id) },
                        )
                    }
                }
            }

            LibraryCategory.RecentlyAdded -> addTrackItems(
                tracks = state.tracks.take(50),
                category = category,
                currentPlayingTrackId = currentPlayingTrackId,
                onAction = onAction,
            )

            LibraryCategory.Sources -> item { CategoryEmpty(category) }

            else -> addRepositoryTrackItems(
                repositoryState = category.repositoryState(state),
                category = category,
                currentPlayingTrackId = currentPlayingTrackId,
                onAction = onAction,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.addRepositoryTrackItems(
    repositoryState: RepositoryState<List<LibraryTrackItem>>?,
    category: LibraryCategory,
    currentPlayingTrackId: Long?,
    onAction: (LibraryAction) -> Unit,
) {
    when (repositoryState) {
        null -> item { CategoryEmpty(category) }
        RepositoryState.Loading -> item { LoadingState() }
        is RepositoryState.Error -> item { ErrorState { onAction(LibraryAction.Refresh) } }
        is RepositoryState.Empty -> item { CategoryEmpty(category) }
        is RepositoryState.Loaded -> addTrackItems(
            tracks = repositoryState.data,
            category = category,
            currentPlayingTrackId = currentPlayingTrackId,
            onAction = onAction,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.addTrackItems(
    tracks: List<LibraryTrackItem>,
    category: LibraryCategory,
    currentPlayingTrackId: Long?,
    onAction: (LibraryAction) -> Unit,
    rootEmpty: Boolean = false,
) {
    if (tracks.isEmpty()) {
        item {
            if (rootEmpty) RootEmpty() else CategoryEmpty(category)
        }
        return
    }
    itemsIndexed(
        items = tracks,
        key = { index, track -> "${track.id}-${track.mediaId}-$index" },
    ) { index, track ->
        TrackRow(
            track = track,
            index = index,
            playing = track.id == currentPlayingTrackId,
            onPlay = { onAction(LibraryAction.PlayTrack(track.id)) },
            onDownload = { onAction(LibraryAction.DownloadTrack(track)) },
        )
    }
}

@Composable
private fun SongSummary(tracks: List<LibraryTrackItem>) {
    val totalMinutes = tracks.mapNotNull { it.durationMs }.sum() / 60_000L
    Text(
        text = stringResource(Res.string.library_song_summary, tracks.size, totalMinutes),
        color = MiuixTheme.colorScheme.onBackgroundVariant,
        style = MiuixTheme.textStyles.body2,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun TrackRow(
    track: LibraryTrackItem,
    index: Int,
    playing: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
) {
    val start = accentColors[index % accentColors.size]
    val end = accentColors[(index + 1) % accentColors.size]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (playing) MiuixTheme.colorScheme.tertiaryContainer.copy(alpha = 0.62f)
                else Color.Transparent,
            )
            .clickable(onClick = onPlay)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(start, end))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(
                    if (playing) CorePresentationRes.drawable.icon_pause
                    else CorePresentationRes.drawable.icon_music_note,
                ),
                tint = Color.White.copy(alpha = 0.86f),
                contentDescription = null,
                modifier = Modifier.size(17.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (playing) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onBackground,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist ?: stringResource(Res.string.library_unknown_artist),
                color = MiuixTheme.colorScheme.onBackgroundVariant,
                style = MiuixTheme.textStyles.footnote1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = clockLabel(track.durationMs),
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            style = MiuixTheme.textStyles.footnote1,
        )
        if (track.mediaId != null) {
            Icon(
                painter = painterResource(CorePresentationRes.drawable.icon_download),
                tint = MiuixTheme.colorScheme.primary,
                contentDescription = stringResource(Res.string.library_download),
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
private fun AlbumRow(album: LibraryAlbumItem, onClick: () -> Unit) {
    NavigationRow(
        title = album.name,
        subtitle = album.year?.toString() ?: stringResource(Res.string.library_unknown_year),
        painter = painterResource(CorePresentationRes.drawable.icon_album),
        onClick = onClick,
    )
}

@Composable
private fun ArtistRow(artist: LibraryArtistItem, onClick: () -> Unit) {
    NavigationRow(
        title = artist.name,
        painter = painterResource(CorePresentationRes.drawable.icon_music_note),
        onClick = onClick,
    )
}

@Composable
private fun FolderRow(folder: LibraryFolderItem, onClick: () -> Unit) {
    NavigationRow(
        title = folder.displayName.ifBlank { folder.path.substringAfterLast('/') },
        subtitle = stringResource(Res.string.library_folder_track_count, folder.trackCount),
        painter = painterResource(CorePresentationRes.drawable.icon_folder),
        onClick = onClick,
    )
}

@Composable
private fun PlaylistRow(playlist: PlaylistSummary, onClick: () -> Unit) {
    NavigationRow(
        title = playlist.title,
        subtitle = buildString {
            append(stringResource(Res.string.library_playlist_track_count, playlist.musicCount))
            append(" · ")
            append(durationLabel(playlist.durationMs))
        },
        painter = painterResource(CorePresentationRes.drawable.icon_mode_list),
        onClick = onClick,
    )
}

@Composable
private fun NavigationRow(
    title: String,
    painter: Painter,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(DesignPalette.BrandPink, DesignPalette.Secondary))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painter,
                    tint = Color.White.copy(alpha = 0.84f),
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MiuixTheme.colorScheme.onBackground,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        style = MiuixTheme.textStyles.footnote1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                painter = painterResource(CorePresentationRes.drawable.icon_chevron_right),
                tint = MiuixTheme.colorScheme.onBackgroundVariant,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun RootEmpty() {
    EmptyState(
        title = stringResource(Res.string.library_empty_title),
        message = stringResource(Res.string.library_empty_message),
        marker = "M",
    )
}

@Composable
private fun CategoryEmpty(category: LibraryCategory, onClick: (() -> Unit)? = null) {
    val title = stringResource(category.labelRes)
    EmptyState(
        title = title,
        message = stringResource(category.emptyMessageRes),
        marker = title.take(1),
        action = onClick?.let { click ->
            {
                TextButton(
                    text = title,
                    onClick = click,
                )
            }
        },
    )
}

@Composable
private fun LoadingState() {
    StatusMessageCard(
        title = stringResource(Res.string.library_loading),
        message = stringResource(Res.string.library_title),
        loading = true,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
    )
}

@Composable
private fun ErrorState(onRetry: () -> Unit) {
    StatusMessageCard(
        title = stringResource(Res.string.library_unavailable),
        message = stringResource(Res.string.library_empty_message),
        actionText = stringResource(Res.string.library_retry),
        onAction = onRetry,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
    )
}

private fun LibraryCategory.repositoryState(
    state: LibraryState,
): RepositoryState<List<LibraryTrackItem>>? = when (this) {
    LibraryCategory.Favorites -> state.favorites
    LibraryCategory.Downloads -> state.downloads
    LibraryCategory.History,
    LibraryCategory.RecentlyPlayed -> state.history
    LibraryCategory.Lossless -> state.lossless
    LibraryCategory.HiRes -> state.hiRes
    else -> null
}

private fun clockLabel(durationMs: Long?): String {
    if (durationMs == null) return "--:--"
    val seconds = durationMs.coerceAtLeast(0L) / 1_000L
    return "${seconds / 60L}:${(seconds % 60L).toString().padStart(2, '0')}"
}

@Composable
private fun durationLabel(durationMs: Long): String {
    val totalMinutes = (durationMs / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) {
        stringResource(Res.string.library_duration_hours, hours, minutes)
    } else {
        stringResource(Res.string.library_duration_minutes, minutes)
    }
}

private enum class LibraryCategory(
    val labelRes: StringResource,
    val emptyMessageRes: StringResource,
) {
    Songs(Res.string.library_category_songs, Res.string.library_empty_message),
    Albums(Res.string.library_category_albums, Res.string.library_empty_albums),
    Artists(Res.string.library_category_artists, Res.string.library_empty_artists),
    Genres(Res.string.library_category_genres, Res.string.library_empty_genres),
    Folders(Res.string.library_category_folders, Res.string.library_empty_folders),
    Playlists(Res.string.library_category_playlists, Res.string.library_empty_playlists),
    Favorites(Res.string.library_category_favorites, Res.string.library_empty_favorites),
    Downloads(Res.string.library_category_downloads, Res.string.library_empty_downloads),
    History(Res.string.library_category_history, Res.string.library_empty_history),
    RecentlyAdded(Res.string.library_category_recently_added, Res.string.library_empty_recently_added),
    RecentlyPlayed(Res.string.library_category_recently_played, Res.string.library_empty_recently_played),
    Lossless(Res.string.library_category_lossless, Res.string.library_empty_lossless),
    HiRes(Res.string.library_category_hires, Res.string.library_empty_hires),
    Sources(Res.string.library_category_sources, Res.string.library_empty_sources),
}

private val accentColors = listOf(
    DesignPalette.BrandPink,
    DesignPalette.Secondary,
    DesignPalette.SupportBlue,
    DesignPalette.SupportOrange,
    DesignPalette.SupportGreen,
    DesignPalette.SupportYellow,
)
