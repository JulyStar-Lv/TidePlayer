package io.github.julystar.musicapp.feature.album.presentation

sealed interface AlbumAction {
    data object NavigateBack : AlbumAction
    data object Retry : AlbumAction
    data object PlayAll : AlbumAction
    data object Shuffle : AlbumAction
    data class PlayTrack(val trackId: Long) : AlbumAction
    data class DownloadTrack(val track: AlbumTrackItem) : AlbumAction
}
