package io.github.julystar.musicapp.car.presentation.nowplaying

import io.github.julystar.musicapp.core.domain.model.CurrentTrackInfo
import io.github.julystar.musicapp.core.domain.model.Lyrics
import io.github.julystar.musicapp.service.playback.domain.PlayableItem
import io.github.julystar.musicapp.service.playback.domain.PlaybackPosition
import io.github.julystar.musicapp.service.playback.domain.RepeatMode
import io.github.julystar.musicapp.service.playback.domain.PlayerState
import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.MediaType
import io.github.julystar.musicapp.core.domain.model.SourceId
import kotlin.test.Test
import kotlin.test.assertEquals

class CarNowPlayingStateTest {
    @Test
    fun playbackModeCyclesLikePhonePlayer() {
        assertEquals(
            CarPlaybackModeSelection(RepeatMode.All, shuffleEnabled = false),
            PlayerState(repeatMode = RepeatMode.Off).nextCarPlaybackMode(),
        )
        assertEquals(
            CarPlaybackModeSelection(RepeatMode.All, shuffleEnabled = true),
            PlayerState(repeatMode = RepeatMode.All).nextCarPlaybackMode(),
        )
        assertEquals(
            CarPlaybackModeSelection(RepeatMode.One, shuffleEnabled = false),
            PlayerState(repeatMode = RepeatMode.All, shuffleEnabled = true).nextCarPlaybackMode(),
        )
        assertEquals(
            CarPlaybackModeSelection(RepeatMode.All, shuffleEnabled = false),
            PlayerState(repeatMode = RepeatMode.One).nextCarPlaybackMode(),
        )
    }

    @Test
    fun progressClampsPlayedAndNeverShowsBufferBehindPlayback() {
        assertEquals(
            CarPlaybackProgress(1f, 1f),
            PlaybackPosition(positionMs = 120_000, durationMs = 60_000, bufferedMs = 30_000).toCarProgress(),
        )
        assertEquals(
            CarPlaybackProgress(0.5f, 0.5f),
            PlaybackPosition(positionMs = 30_000, durationMs = 60_000, bufferedMs = 10_000).toCarProgress(),
        )
        assertEquals(CarPlaybackProgress(0f, 0f), PlaybackPosition().toCarProgress())
    }

    @Test
    fun queueKeysPreferMediaThenLibraryThenStablePosition() {
        val remote = PlayableItem(
            mediaId = MediaId(SourceId("source"), MediaType.Track, "remote"),
            title = "Remote",
        )
        val library = PlayableItem(libraryTrackId = 42, title = "Library")

        assertEquals(remote.mediaId.toString(), remote.stableCarQueueKey(0))
        assertEquals("library:42", library.stableCarQueueKey(1))
        assertEquals("queue.library:42", library.carQueueFocusId(1).value)
    }

    @Test
    fun miniPlayerArtistOnlyUsesMetadataForCurrentTrack() {
        val player = PlayerState(currentItem = PlayableItem(libraryTrackId = 42, title = "Current"))

        assertEquals("Current Artist", CarNowPlayingUiState(
            player = player,
            trackInfo = trackInfo(42, "Current Artist"),
        ).miniPlayerArtist)
        assertEquals(null, CarNowPlayingUiState(
            player = player,
            trackInfo = trackInfo(41, "Previous Artist"),
        ).miniPlayerArtist)
    }

    private fun trackInfo(id: Long, artist: String) = CurrentTrackInfo(
        id = id,
        title = "Track $id",
        durationMs = null,
        artwork = null,
        lyrics = Lyrics(),
        sourceStorageId = 1,
        sourcePath = "/track/$id",
        coverArtwork = null,
        artist = artist,
    )
}
