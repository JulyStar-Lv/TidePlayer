package io.github.julystar.musicapp.core.presentation.overlay

import androidx.compose.runtime.Composable
import io.github.julystar.musicapp.core.domain.repository.UiMessage
import io.github.julystar.musicapp.core.domain.repository.UiMessageKey
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import musicapp.core.presentation.generated.resources.Res
import musicapp.core.presentation.generated.resources.*

@Composable
fun UiMessage.resolve(): String = when (this) {
    is UiMessage.Text -> value
    is UiMessage.Resource -> stringResource(key.resource(), *args.toTypedArray())
}

private fun UiMessageKey.resource(): StringResource = when (this) {
    UiMessageKey.AddedToDownloads -> Res.string.ui_message_added_to_downloads
    UiMessageKey.DownloadFailed -> Res.string.ui_message_download_failed
    UiMessageKey.DownloadActionFailed -> Res.string.ui_message_download_action_failed
    UiMessageKey.DownloadRemoved -> Res.string.ui_message_download_removed
    UiMessageKey.TrackCannotBeDownloaded -> Res.string.ui_message_track_cannot_download
    UiMessageKey.SourceUnavailable -> Res.string.ui_message_source_unavailable
    UiMessageKey.UnableToOpenAudioStream -> Res.string.ui_message_audio_stream_failed
    UiMessageKey.PreferredSourceUpdated -> Res.string.ui_message_preferred_source_updated
    UiMessageKey.PlayHistoryCleared -> Res.string.ui_message_play_history_cleared
    UiMessageKey.PlaylistOperationFailed -> Res.string.ui_message_playlist_operation_failed
    UiMessageKey.FavoriteOperationFailed -> Res.string.ui_message_favorite_operation_failed
    UiMessageKey.FavoriteAdded -> Res.string.ui_message_favorite_added
    UiMessageKey.FavoriteRemoved -> Res.string.ui_message_favorite_removed
    UiMessageKey.MetadataActionCompleted -> Res.string.ui_message_metadata_completed
    UiMessageKey.MetadataActionFailed -> Res.string.ui_message_metadata_failed
    UiMessageKey.AudioOutputSwitchFailed -> Res.string.ui_message_audio_output_switch_failed
    UiMessageKey.UnknownAlbum -> Res.string.ui_message_unknown_album
    UiMessageKey.UnknownArtist -> Res.string.ui_message_unknown_artist
    UiMessageKey.UnknownTrack -> Res.string.ui_message_unknown_track
    UiMessageKey.AlbumLoadFailed -> Res.string.ui_message_album_load_failed
    UiMessageKey.ArtistLoadFailed -> Res.string.ui_message_artist_load_failed
    UiMessageKey.BrowseLoadFailed -> Res.string.ui_message_browse_load_failed
    UiMessageKey.GenreTracksLoadFailed -> Res.string.ui_message_genre_tracks_load_failed
    UiMessageKey.RadioGenerationFailed -> Res.string.ui_message_radio_generation_failed
    UiMessageKey.RecentlyAddedLoadFailed -> Res.string.ui_message_recently_added_load_failed
    UiMessageKey.RecentlyPlayedLoadFailed -> Res.string.ui_message_recently_played_load_failed
    UiMessageKey.LyricsLoadFailed -> Res.string.ui_message_lyrics_load_failed
    UiMessageKey.OneDriveSignInFailed -> Res.string.ui_message_onedrive_sign_in_failed
    UiMessageKey.OneDriveDriveListFailed -> Res.string.ui_message_onedrive_drive_list_failed
    UiMessageKey.LibraryImportStarted -> Res.string.ui_message_library_import_started
    UiMessageKey.LibraryImportCompleted -> Res.string.ui_message_library_import_completed
    UiMessageKey.LibraryImportCancelled -> Res.string.ui_message_library_import_cancelled
    UiMessageKey.LibraryImportFailed -> Res.string.ui_message_library_import_failed
    UiMessageKey.SourceResultMustBeImported -> Res.string.ui_message_source_result_import_required
    UiMessageKey.PlayHistoryClearFailed -> Res.string.ui_message_play_history_clear_failed
    UiMessageKey.DownloadRemoveFailed -> Res.string.ui_message_download_remove_failed
    UiMessageKey.PreviousAbnormalExitDetected ->
        Res.string.ui_message_previous_abnormal_exit_detected
}
