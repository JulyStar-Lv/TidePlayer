package io.github.julystar.musicapp.service.playback.presentation.nowplaying

import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.model.CurrentTrackInfo
import io.github.julystar.musicapp.core.domain.model.AudioDeliveryMode
import io.github.julystar.musicapp.core.domain.model.AudioTechnicalInfo
import io.github.julystar.musicapp.core.domain.model.PlaybackAudioInfo
import io.github.julystar.musicapp.core.domain.model.LyricLine
import io.github.julystar.musicapp.core.domain.model.Lyrics
import io.github.julystar.musicapp.core.domain.model.LyricsLoadState
import io.github.julystar.musicapp.service.playback.domain.PlayableItem
import io.github.julystar.musicapp.service.playback.domain.PlaybackQueue
import io.github.julystar.musicapp.service.playback.domain.PlaybackStatus
import io.github.julystar.musicapp.service.playback.domain.PlayerState
import io.github.julystar.musicapp.service.playback.domain.RepeatMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.collections.immutable.persistentListOf
import kotlin.time.Duration.Companion.seconds

class NowPlayingStateTest {
    @Test
    fun initialNowPlayingStateKeepsCurrentArtwork() {
        val artwork = Artwork.LibraryTrack(trackId = 7)
        val info = currentTrackInfo(artwork = artwork)

        val state = info.toInitialNowPlayingState()

        assertEquals(artwork, state.currentTrack?.artwork)
    }

    @Test
    fun mapsCurrentTrackInfoToNowPlayingTrackItem() {
        val info = CurrentTrackInfo(
            id = 7,
            title = "Now Playing",
            durationMs = 123_000,
            artwork = Artwork.LegacyStorageEntry(storageId = 9, path = "/Covers/Now.jpg"),
            lyrics = Lyrics(
                lines = persistentListOf(LyricLine(duration = 1.seconds, text = "First line")),
                loadState = LyricsLoadState.Loaded,
            ),
            sourceStorageId = 5,
            sourcePath = "/Music/Now.flac",
            artist = "The Artist",
            annotation = "Live version",
            coverArtwork = Artwork.LegacyStorageEntry(storageId = 9, path = "/Covers/Now.jpg"),
        )

        val item = info.toNowPlayingTrackItem()

        assertEquals(7, item.id)
        assertEquals("Now Playing", item.title)
        assertEquals("The Artist", item.artist)
        assertEquals("Live version", item.annotation)
        assertEquals(123_000, item.durationMs)
        assertEquals(Artwork.LegacyStorageEntry(storageId = 9, path = "/Covers/Now.jpg"), item.artwork)
        assertEquals(LyricsLoadState.Loaded, item.lyrics.loadState)
        assertEquals(true, item.hasLyric)
    }

    @Test
    fun mapsPlaybackQueueToNowPlayingQueuePresentationState() {
        val state = PlaybackQueue(
            items = listOf(
                playable(id = 1, title = "One"),
                playable(id = 2, title = "Two"),
            ),
            currentIndex = 1,
        ).toNowPlayingQueueState(
            previousArtwork = Artwork.LibraryTrack(trackId = 1),
            nextArtwork = null,
        )

        assertEquals(1, state.currentIndex)
        assertEquals(2, state.itemCount)
        assertEquals(true, state.canPlayPrevious)
        assertEquals(false, state.canPlayNext)
        assertEquals(Artwork.LibraryTrack(trackId = 1), state.previousArtwork)
        assertEquals(null, state.nextArtwork)
    }

    @Test
    fun mapsPlaybackStateToNowPlayingControlsPresentationState() {
        val state = PlayerState(
            currentItem = playable(id = 7, title = "Now Playing"),
            status = PlaybackStatus.Playing,
            repeatMode = RepeatMode.All,
            shuffleEnabled = true,
        ).toNowPlayingControlsState()

        assertEquals(true, state.isPlaying)
        assertEquals(false, state.isLoading)
        assertEquals(RepeatMode.All, state.repeatMode)
        assertEquals(true, state.shuffleEnabled)
    }

    @Test
    fun nowPlayingUsesEffectiveTranscodedAudioQuality() {
        val info = currentTrackInfo(Artwork.LibraryTrack(7)).copy(
            playbackAudioInfo = PlaybackAudioInfo(
                source = AudioTechnicalInfo(
                    codec = "FLAC",
                    bitrateKbps = 5_640,
                    sampleRateHz = 192_000,
                    bitDepth = 24,
                ),
                effective = AudioTechnicalInfo(
                    codec = "AAC",
                    bitrateKbps = 320,
                    sampleRateHz = 48_000,
                ),
                deliveryMode = AudioDeliveryMode.Transcode,
            )
        )

        assertEquals("AAC · 48 kHz · 320 kbps", info.toNowPlayingTrackItem().audioQuality)
    }

    @Test
    fun selectedPlaybackSourceImmediatelyUpdatesNowPlayingQuality() {
        val initial = currentTrackInfo(Artwork.LibraryTrack(7))
            .copy(
                playbackAudioInfo = PlaybackAudioInfo(
                    source = AudioTechnicalInfo(codec = "FLAC", sampleRateHz = 96_000, bitDepth = 24),
                    deliveryMode = AudioDeliveryMode.DirectPlay,
                )
            )
            .toInitialNowPlayingState()
        val switched = initial.withPlaybackSources(
            listOf(
                NowPlayingSourceItem(
                    sourceItemId = 2,
                    accountName = "Emby",
                    displayName = "Song",
                    quality = "AAC · 256 kbps",
                    isSelected = true,
                    playbackAudioInfo = PlaybackAudioInfo(
                        source = AudioTechnicalInfo(codec = "AAC", bitrateKbps = 256),
                        deliveryMode = AudioDeliveryMode.DirectPlay,
                    ),
                )
            )
        )

        assertEquals("AAC · 256 kbps", switched.currentTrack?.audioQuality)
    }

    @Test
    fun queueStateReportsNoPrevNextWhenArtworksAreNull() {
        val state = PlaybackQueue(
            items = listOf(playable(id = 1, title = "Only")),
            currentIndex = 0,
        ).toNowPlayingQueueState(
            previousArtwork = null,
            nextArtwork = null,
        )

        assertFalse(state.canPlayPrevious)
        assertFalse(state.canPlayNext)
        assertEquals(null, state.previousArtwork)
        assertEquals(null, state.nextArtwork)
    }

    @Test
    fun nowPlayingScreenDismissesAfterReferenceDistanceThreshold() {
        assertFalse(
            shouldDismissNowPlayingScreen(
                dragOffsetPx = 239f,
                dismissThresholdPx = 240f,
                velocityPxPerSecond = 1_249f,
                velocityThresholdPxPerSecond = 1_250f,
            ),
        )
        assertTrue(
            shouldDismissNowPlayingScreen(
                dragOffsetPx = 240f,
                dismissThresholdPx = 240f,
                velocityPxPerSecond = 0f,
                velocityThresholdPxPerSecond = 1_250f,
            ),
        )
    }

    @Test
    fun nowPlayingScreenDismissesOnlyForFastDownwardFling() {
        assertTrue(
            shouldDismissNowPlayingScreen(
                dragOffsetPx = 100f,
                dismissThresholdPx = 240f,
                velocityPxPerSecond = 1_250f,
                velocityThresholdPxPerSecond = 1_250f,
            ),
        )
        assertFalse(
            shouldDismissNowPlayingScreen(
                dragOffsetPx = 100f,
                dismissThresholdPx = 240f,
                velocityPxPerSecond = -1_500f,
                velocityThresholdPxPerSecond = 1_250f,
            ),
        )
    }

    private fun playable(
        id: Long,
        title: String,
    ) = PlayableItem(
        title = title,
        libraryTrackId = id,
    )

    private fun currentTrackInfo(artwork: Artwork) = CurrentTrackInfo(
        id = 7,
        title = "Now Playing",
        durationMs = 123_000,
        artwork = artwork,
        lyrics = Lyrics(loadState = LyricsLoadState.Missing),
        sourceStorageId = 5,
        sourcePath = "/Music/Now.flac",
        coverArtwork = artwork,
    )
}
