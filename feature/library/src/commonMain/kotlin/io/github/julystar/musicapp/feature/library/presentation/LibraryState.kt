package io.github.julystar.musicapp.feature.library.presentation

import androidx.compose.runtime.Immutable
import io.github.julystar.musicapp.core.domain.model.DomainTrackBrowserItem
import io.github.julystar.musicapp.core.domain.model.FilterCriteria
import io.github.julystar.musicapp.core.domain.model.LibraryAlbumItem
import io.github.julystar.musicapp.core.domain.model.LibraryArtistItem
import io.github.julystar.musicapp.core.domain.model.LibrarySortField
import io.github.julystar.musicapp.core.domain.model.LibraryTrackItem
import io.github.julystar.musicapp.core.domain.model.PlaylistSummary
import io.github.julystar.musicapp.core.domain.model.RepositoryState
import io.github.julystar.musicapp.core.domain.model.SortCriteria
import io.github.julystar.musicapp.core.domain.model.SortDirection
import io.github.julystar.musicapp.core.domain.repository.LibraryFolderItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import io.github.julystar.musicapp.core.domain.repository.UiMessage

/**
 * Top-level library presentation state.
 *
 * Each category section tracks its own [RepositoryState], exposing
 * Loading / Error / Empty / Loaded semantics uniformly.
 * Sorting and filtering parameters are also stored per-category so the UI
 * can drive them without round-tripping through the domain layer.
 */
@Immutable
data class LibraryState(
    // Flat library (tracks / albums / artists / playlists)
    val tracks: ImmutableList<LibraryTrackItem> = persistentListOf(),
    val albums: ImmutableList<LibraryAlbumItem> = persistentListOf(),
    val artists: ImmutableList<LibraryArtistItem> = persistentListOf(),
    val playlists: ImmutableList<PlaylistSummary> = persistentListOf(),

    // Per-category repository state
    val genreNames: RepositoryState<List<String>> = RepositoryState.Loading,
    val genreTracks: Map<String, RepositoryState<List<DomainTrackBrowserItem>>> = emptyMap(),
    val folders: RepositoryState<List<LibraryFolderItem>> = RepositoryState.Loading,
    val favorites: RepositoryState<List<LibraryTrackItem>> = RepositoryState.Loading,
    val history: RepositoryState<List<LibraryTrackItem>> = RepositoryState.Loading,
    val lossless: RepositoryState<List<LibraryTrackItem>> = RepositoryState.Loading,
    val hiRes: RepositoryState<List<LibraryTrackItem>> = RepositoryState.Loading,
    val downloads: RepositoryState<List<LibraryTrackItem>> = RepositoryState.Loading,

    // Per-category sort / filter
    val genreSort: SortCriteria = SortCriteria.Default,
    val genreFilter: FilterCriteria.GenreFilter = FilterCriteria.GenreFilter(),
    val folderSort: SortCriteria = SortCriteria.Default,
    val folderFilter: FilterCriteria.FolderFilter = FilterCriteria.FolderFilter(),
    val favoritesSort: SortCriteria = SortCriteria.Default,
    val favoritesFilter: FilterCriteria.FavoritesFilter = FilterCriteria.FavoritesFilter(),
    val historySort: SortCriteria = SortCriteria(field = LibrarySortField.LastPlayed, direction = SortDirection.Descending),
    val historyFilter: FilterCriteria.HistoryFilter = FilterCriteria.HistoryFilter(),
    val losslessSort: SortCriteria = SortCriteria.Default,
    val losslessFilter: FilterCriteria.LosslessFilter = FilterCriteria.LosslessFilter(),
    val hiResSort: SortCriteria = SortCriteria.Default,
    val hiResFilter: FilterCriteria.LosslessFilter = FilterCriteria.LosslessFilter(),
    val downloadsSort: SortCriteria = SortCriteria.Default,
    val downloadsFilter: FilterCriteria.DownloadsFilter = FilterCriteria.DownloadsFilter(),
)

internal val LibraryState.hasIndexedTracks: Boolean
    get() = tracks.isNotEmpty()

// ── Actions ──

sealed interface LibraryAction {
    data object Refresh : LibraryAction
    data class PlayTrack(val trackId: Long) : LibraryAction
    data class DownloadTrack(val track: LibraryTrackItem) : LibraryAction

    // Category navigation
    data class SelectGenre(val genreName: String) : LibraryAction
    data class BrowseFolder(val path: String) : LibraryAction

    // Sort / filter updates
    data class UpdateGenreSort(val sort: SortCriteria) : LibraryAction
    data class UpdateGenreFilter(val filter: FilterCriteria.GenreFilter) : LibraryAction
    data class UpdateFolderSort(val sort: SortCriteria) : LibraryAction
    data class UpdateFolderFilter(val filter: FilterCriteria.FolderFilter) : LibraryAction
    data class UpdateFavoritesSort(val sort: SortCriteria) : LibraryAction
    data class UpdateFavoritesFilter(val filter: FilterCriteria.FavoritesFilter) : LibraryAction
    data class UpdateHistorySort(val sort: SortCriteria) : LibraryAction
    data class UpdateHistoryFilter(val filter: FilterCriteria.HistoryFilter) : LibraryAction
    data class UpdateLosslessSort(val sort: SortCriteria) : LibraryAction
    data class UpdateLosslessFilter(val filter: FilterCriteria.LosslessFilter) : LibraryAction
    data class UpdateHiResSort(val sort: SortCriteria) : LibraryAction
    data class UpdateHiResFilter(val filter: FilterCriteria.LosslessFilter) : LibraryAction
    data class UpdateDownloadsSort(val sort: SortCriteria) : LibraryAction
    data class UpdateDownloadsFilter(val filter: FilterCriteria.DownloadsFilter) : LibraryAction

    // Favorites toggle
    data class ToggleFavorite(val trackId: Long) : LibraryAction

    // History clear
    data object ClearHistory : LibraryAction

    // Downloads management
    data class RemoveDownload(val trackId: Long) : LibraryAction
}

sealed interface LibraryEvent {
    data class ShowMessage(val message: UiMessage) : LibraryEvent
}
