package io.github.julystar.musicapp.core.data

import io.github.julystar.musicapp.feature.playlist.domain.EditPlaylistGateway
import io.github.julystar.musicapp.feature.playlist.domain.PlaylistMetaToEdit
import io.github.julystar.musicapp.source.api.SourceNodeSelection

class LegacyEditPlaylistGateway(
    private val playlistRepository: PlaylistRepositoryImpl,
    private val storageRepository: StorageRepositoryImpl,
) : EditPlaylistGateway {
    override fun getPlaylistMetaToEdit(id: Long): PlaylistMetaToEdit? {
        return playlistRepository.playlists.value
            .find { item -> item.meta.id.value == id }
            ?.let { item ->
                PlaylistMetaToEdit(
                    title = item.meta.title,
                    coverSelection = item.meta.cover?.toSourceNodeSelection(
                        storageRepository.storages.value,
                    ),
                    coverArtwork = playlistRepository.playlistSummaries.value.find { it.id == id }?.coverArtwork,
                )
            }
    }

    override fun updatePlaylist(
        id: Long,
        title: String,
        cover: SourceNodeSelection?,
        coverImage: ByteArray?,
    ) {
        playlistRepository.editPlaylist(
            UpdatePlaylistRequest(
                id = id,
                title = title,
                cover = cover,
                coverImage = coverImage,
            ),
        )
    }
}
