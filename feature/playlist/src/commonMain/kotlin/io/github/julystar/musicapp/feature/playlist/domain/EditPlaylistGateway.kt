package io.github.julystar.musicapp.feature.playlist.domain

import io.github.julystar.musicapp.source.api.SourceNodeSelection
import io.github.julystar.musicapp.core.domain.model.Artwork

data class PlaylistMetaToEdit(
    val title: String,
    val coverSelection: SourceNodeSelection?,
    val coverArtwork: Artwork? = null,
)

interface EditPlaylistGateway {
    fun getPlaylistMetaToEdit(id: Long): PlaylistMetaToEdit?

    fun updatePlaylist(
        id: Long,
        title: String,
        cover: SourceNodeSelection?,
        coverImage: ByteArray? = null,
    )
}
