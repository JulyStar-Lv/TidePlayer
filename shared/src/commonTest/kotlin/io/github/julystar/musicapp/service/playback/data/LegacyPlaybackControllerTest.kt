package io.github.julystar.musicapp.service.playback.data

import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.service.playback.domain.PlaybackPosition
import io.github.julystar.musicapp.service.playback.domain.PlaybackQueue
import io.github.julystar.musicapp.service.playback.domain.PlaybackStatus
import io.github.julystar.musicapp.service.playback.domain.PlayableItem
import io.github.julystar.musicapp.service.playback.domain.RepeatMode
import io.github.julystar.musicapp.singleton.PlaybackItemMetadata
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import uniffi.app_backend.Music
import uniffi.app_backend.MusicAbstract
import uniffi.app_backend.MusicId
import uniffi.app_backend.MusicMeta
import uniffi.app_backend.PlayMode
import uniffi.app_backend.Playlist
import uniffi.app_backend.PlaylistAbstract
import uniffi.app_backend.PlaylistId
import uniffi.app_backend.PlaylistMeta
import uniffi.app_backend.StorageEntryLoc
import uniffi.app_backend.StorageId
import kotlin.time.Duration.Companion.milliseconds

class LegacyPlaybackControllerTest {
    @Test
    fun pendingSeekPublishesTargetAndMarksPositionAsSeeking() {
        assertEquals(
            PlaybackPosition(
                positionMs = 45_000L,
                bufferedMs = 32_000L,
                durationMs = 180_000L,
                isSeeking = true,
            ),
            legacyPlaybackPosition(
                currentPositionMs = 12_000L,
                bufferedPositionMs = 32_000L,
                durationMs = 180_000L,
                pendingSeekPositionMs = 45_000L,
            ),
        )
    }

    @Test
    fun completedSeekUsesActualPlayerPosition() {
        assertEquals(
            PlaybackPosition(
                positionMs = 45_120L,
                bufferedMs = 61_000L,
                durationMs = 180_000L,
            ),
            legacyPlaybackPosition(
                currentPositionMs = 45_120L,
                bufferedPositionMs = 61_000L,
                durationMs = 180_000L,
                pendingSeekPositionMs = null,
            ),
        )
    }

    @Test
    fun restoredPreviewPublishesSavedProgressWhilePlayerIsNotLoaded() {
        assertEquals(
            PlaybackPosition(
                positionMs = 45_000L,
                durationMs = 180_000L,
            ),
            PlaybackPosition.Zero.withRestoredPlaybackPreview(
                positionMs = 45_000L,
                durationMs = 180_000L,
            ),
        )
    }

    @Test
    fun restoredPreviewDoesNotOverrideLivePlayerProgress() {
        val live = PlaybackPosition(
            positionMs = 46_000L,
            bufferedMs = 70_000L,
            durationMs = 180_000L,
        )

        assertEquals(
            live,
            live.withRestoredPlaybackPreview(
                positionMs = 45_000L,
                durationMs = 180_000L,
            ),
        )
    }

    @Test
    fun restoredPreviewClampsStaleProgressToTrackDuration() {
        assertEquals(180_000L, restoredPlaybackPosition(240_000L, 180_000L))
        assertEquals(0L, restoredPlaybackPosition(-1L, 180_000L))
    }

    @Test
    fun mapsLegacyStateToSeparatedPlayerState() {
        val state = legacyPlayerState(
            music = music(id = 7, title = "Moon"),
            playing = true,
            loading = false,
            playMode = PlayMode.LIST_LOOP,
            playlistId = 3,
        )

        assertEquals(PlaybackStatus.Playing, state.status)
        assertEquals(RepeatMode.All, state.repeatMode)
        assertEquals(false, state.shuffleEnabled)
        assertEquals(7L, state.currentItem?.libraryTrackId)
        assertEquals(3L, state.currentItem?.libraryPlaylistId)
        assertEquals("Moon", state.currentItem?.title)
        assertEquals(180_000L, state.currentItem?.durationMs)
    }

    @Test
    fun loadingStateWinsOverPlayingFlag() {
        val state = legacyPlayerState(
            music = music(id = 7, title = "Moon"),
            playing = true,
            loading = true,
            playMode = PlayMode.SINGLE_LOOP,
            playlistId = 3,
        )

        assertEquals(PlaybackStatus.Loading, state.status)
        assertEquals(RepeatMode.One, state.repeatMode)
    }

    @Test
    fun mapsLegacyPlaylistToPlaybackQueue() {
        val queue = legacyPlaybackQueue(
            playlist = playlist(
                id = 3,
                musics = listOf(
                    musicAbstract(id = 1, title = "One"),
                    musicAbstract(id = 2, title = "Two"),
                    musicAbstract(id = 3, title = "Three"),
                ),
            ),
            currentMusic = music(id = 2, title = "Two"),
        )

        assertEquals(listOf(1L, 2L, 3L), queue.items.map { it.libraryTrackId })
        assertEquals(listOf(3L, 3L, 3L), queue.items.map { it.libraryPlaylistId })
        assertEquals(1, queue.currentIndex)
        assertEquals("Two", queue.currentItem?.title)
    }

    @Test
    fun buildsPlaybackPlaylistFromRequestedItemsInOrder() {
        val playlist = playlist(
            id = 3,
            musics = listOf(
                musicAbstract(id = 1, title = "One"),
                musicAbstract(id = 2, title = "Two"),
                musicAbstract(id = 3, title = "Three"),
            ),
        )

        val queue = playlist.forPlaybackItems(
            listOf(
                PlayableItem(title = "Three", libraryTrackId = 3),
                PlayableItem(title = "One", libraryTrackId = 1),
            ),
        )

        assertEquals(listOf(3L, 1L), queue?.musics?.map { it.meta.id.value })
        assertEquals(2uL, queue?.abstr?.musicCount)
        assertEquals(360_000.milliseconds, queue?.abstr?.duration)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun replacementQueuePublishesBeforeStaleMetadataLookupCompletes() = runTest {
        val oldQueue = playbackQueue(size = 75, firstTrackId = 1L)
        val replacementQueue = playbackQueue(size = 50, firstTrackId = 101L)
        val source = MutableStateFlow(oldQueue)
        val firstLookupStarted = CompletableDeferred<Unit>()
        val neverCompletes = CompletableDeferred<Unit>()
        val emissions = mutableListOf<PlaybackQueue>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            source.withPlaybackItemMetadata { trackId ->
                if (trackId == 1L) {
                    firstLookupStarted.complete(Unit)
                    neverCompletes.await()
                }
                PlaybackItemMetadata(artist = "Artist $trackId", album = null)
            }.collect(emissions::add)
        }

        firstLookupStarted.await()
        source.value = replacementQueue
        runCurrent()

        assertEquals(50, emissions.last().items.size)
        assertEquals("Artist 101", emissions.last().items.first().artist)
        collector.cancelAndJoin()
    }

    @Test
    fun multiTrackQueuePromotesDefaultSingleModeToList() {
        assertEquals(PlayMode.LIST, playbackModeForQueue(PlayMode.SINGLE, queueSize = 2))
        assertEquals(PlayMode.SINGLE, playbackModeForQueue(PlayMode.SINGLE, queueSize = 1))
        assertEquals(PlayMode.SINGLE_LOOP, playbackModeForQueue(PlayMode.SINGLE_LOOP, queueSize = 2))
        assertEquals(PlayMode.LIST_LOOP, playbackModeForQueue(PlayMode.LIST_LOOP, queueSize = 2))
    }

    @Test
    fun fallsBackToLibraryTrackArtworkWhenCurrentMusicCoverIsMissing() {
        val artwork = music(id = 7, title = "Moon").toPlaybackArtwork()

        assertEquals(Artwork.LibraryTrack(trackId = 7), artwork)
    }

    @Test
    fun fallsBackToLibraryTrackArtworkWhenQueueMusicCoverIsMissing() {
        val artwork = musicAbstract(id = 2, title = "Two").toPlaybackArtwork()

        assertEquals(Artwork.LibraryTrack(trackId = 2), artwork)
    }

    private fun music(
        id: Long,
        title: String,
    ): Music {
        return Music(
            meta = musicMeta(id = id, title = title),
            loc = StorageEntryLoc(
                storageId = StorageId(1),
                path = "/Music/$title.flac",
            ),
            cover = null,
            lyric = null,
        )
    }

    private fun musicAbstract(
        id: Long,
        title: String,
    ): MusicAbstract {
        return MusicAbstract(
            meta = musicMeta(id = id, title = title),
            cover = null,
        )
    }

    private fun musicMeta(
        id: Long,
        title: String,
    ): MusicMeta {
        return MusicMeta(
            id = MusicId(id),
            title = title,
            duration = 180_000.milliseconds,
            order = listOf(id.toUInt()),
        )
    }

    private fun playlist(
        id: Long,
        musics: List<MusicAbstract>,
    ): Playlist {
        return Playlist(
            abstr = PlaylistAbstract(
                meta = PlaylistMeta(
                    id = PlaylistId(id),
                    title = "Playlist",
                    cover = null,
                    showCover = null,
                    createdTime = 0.milliseconds,
                    order = listOf(id.toUInt()),
                ),
                musicCount = musics.size.toULong(),
                duration = null,
            ),
            musics = musics,
        )
    }

    private fun playbackQueue(size: Int, firstTrackId: Long): PlaybackQueue = PlaybackQueue(
        items = List(size) { index ->
            val trackId = firstTrackId + index
            PlayableItem(
                title = "Track $trackId",
                libraryTrackId = trackId,
                libraryPlaylistId = -1L,
            )
        },
        currentIndex = 0,
    )
}
