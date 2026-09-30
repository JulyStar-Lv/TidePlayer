package io.github.julystar.musicapp.core.data

import io.github.julystar.musicapp.singleton.RoomLibraryStore
import io.github.julystar.musicapp.source.api.PlaylistImportTarget
import io.github.julystar.musicapp.source.api.SourceNodeSelection

class PlaylistImportTargetImpl(
    private val roomLibraryStore: RoomLibraryStore,
) : PlaylistImportTarget {
    override suspend fun createPlaylistFromSelections(
        title: String,
        cover: SourceNodeSelection?,
        entries: List<SourceNodeSelection>,
        coverImage: ByteArray?,
    ) {
        roomLibraryStore.createPlaylist(
            CreatePlaylistRequest(
                title = title,
                cover = cover,
                entries = entries,
                coverImage = coverImage,
            )
        )
    }

    override suspend fun addMusicSelectionsToPlaylist(
        playlistId: Long,
        selections: List<SourceNodeSelection>,
    ): List<Long> {
        return roomLibraryStore.addMusicSelectionsById(playlistId, selections)
    }
}
