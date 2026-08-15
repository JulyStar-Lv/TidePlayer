package io.github.julystar.musicapp.core.domain.repository

import kotlinx.coroutines.flow.Flow

sealed interface UiMessage {
    data class Text(val value: String) : UiMessage

    data class Resource(
        val key: UiMessageKey,
        val args: List<String> = emptyList(),
    ) : UiMessage
}

enum class UiMessageKey {
    AddedToDownloads,
    DownloadFailed,
    DownloadActionFailed,
    DownloadRemoved,
    TrackCannotBeDownloaded,
    SourceUnavailable,
    UnableToOpenAudioStream,
    PreferredSourceUpdated,
    PlayHistoryCleared,
    PlaylistOperationFailed,
    FavoriteOperationFailed,
    FavoriteAdded,
    FavoriteRemoved,
    MetadataActionCompleted,
    MetadataActionFailed,
    AudioOutputSwitchFailed,
    UnknownAlbum,
    UnknownArtist,
    UnknownTrack,
    AlbumLoadFailed,
    ArtistLoadFailed,
    BrowseLoadFailed,
    GenreTracksLoadFailed,
    RadioGenerationFailed,
    RecentlyAddedLoadFailed,
    RecentlyPlayedLoadFailed,
    LyricsLoadFailed,
    OneDriveSignInFailed,
    OneDriveDriveListFailed,
    LibraryImportStarted,
    LibraryImportCompleted,
    LibraryImportCancelled,
    LibraryImportFailed,
    SourceResultMustBeImported,
    PlayHistoryClearFailed,
    DownloadRemoveFailed,
    PreviousAbnormalExitDetected,
}

interface ToastRepository {
    val messages: Flow<UiMessage>

    fun emit(message: UiMessage)
}

fun ToastRepository.emit(key: UiMessageKey, vararg args: String) {
    emit(UiMessage.Resource(key = key, args = args.toList()))
}

fun ToastRepository.emitText(value: String) {
    emit(UiMessage.Text(value))
}
