package io.github.julystar.musicapp.source.api

interface PlaylistImportTarget {
    suspend fun createPlaylistFromSelections(
        title: String,
        cover: SourceNodeSelection?,
        entries: List<SourceNodeSelection>,
        coverImage: ByteArray? = null,
    )

    suspend fun addMusicSelectionsToPlaylist(
        playlistId: Long,
        selections: List<SourceNodeSelection>,
    ): List<Long>
}
