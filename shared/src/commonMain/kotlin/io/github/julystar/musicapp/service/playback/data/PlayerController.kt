package io.github.julystar.musicapp.service.playback.data

import io.github.julystar.musicapp.service.playback.domain.SleepController
import io.github.julystar.musicapp.service.playback.domain.SleepModeState
import kotlinx.coroutines.flow.StateFlow
import uniffi.app_backend.Playlist
import uniffi.app_backend.MusicId
import uniffi.app_backend.PlaylistId

interface PlayerController : SleepController {
    override val sleepState: StateFlow<SleepModeState>
    fun getCurrentPosition(): Long
    fun getBufferedPosition(): Long
    fun getDuration(): Long
    fun getPendingSeekPosition(): Long? = null
    fun play(
        id: MusicId,
        playlistId: PlaylistId,
        startPositionMs: Long = 0L,
    )
    fun resume()
    fun pause()
    fun stop()
    fun playNext()
    fun playPrevious()
    fun seek(ms: ULong)
    override fun scheduleSleep(newExpiredMs: Long)
    fun refreshPlaylistIfMatch(playlist: Playlist)
    override fun cancelSleep()
}
