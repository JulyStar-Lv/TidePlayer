package io.github.julystar.musicapp.core.domain.repository

import io.github.julystar.musicapp.core.domain.model.DomainPlaylistTrack
import io.github.julystar.musicapp.core.domain.model.PlaylistSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface PlaylistRepository {
    val playlistSummaries: StateFlow<List<PlaylistSummary>>
    val playlistRefreshEvents: Flow<Unit>
    fun playlistMoveTo(fromIndex: Int, toIndex: Int)
    fun scheduleReload()
    fun observePlaylistTracks(playlistId: Long): Flow<List<DomainPlaylistTrack>>
    fun removePlaylist(id: Long)
    fun requestTotalDurationById(addedTrackIds: List<Long>)
    suspend fun addMusic(playlistId: Long, musicId: Long): Boolean
    suspend fun createPlaylistWithMusic(title: String, musicId: Long, coverImage: ByteArray? = null): Boolean
    suspend fun removeMusic(playlistId: Long, musicId: Long)
    suspend fun replaceMusicOrderById(
        playlistId: Long,
        orderedTrackIds: List<Long>,
    )
}
