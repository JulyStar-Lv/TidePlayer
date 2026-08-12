package io.github.julystar.musicapp.feature.home.presentation

import androidx.compose.ui.graphics.Color
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.model.LibraryAlbumItem
import io.github.julystar.musicapp.core.domain.model.LibraryArtistItem
import io.github.julystar.musicapp.core.domain.model.LibraryTrackItem
import io.github.julystar.musicapp.feature.home.domain.ListeningHistoryEntry
import io.github.julystar.musicapp.feature.home.domain.ListeningStatisticsSnapshot
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class HomeStateTest {

    @Test
    fun `default state contains no demo content`() {
        val state = HomeState()

        assertTrue(state.isLoading)
        assertTrue(state.featuredAlbums.isEmpty())
        assertTrue(state.recentlyAddedAlbums.isEmpty())
        assertTrue(state.artists.isEmpty())
        assertTrue(state.pinnedPlaylists.isEmpty())
        assertTrue(state.dailyPickTracks.isEmpty())
        assertTrue(state.recentTracks.isEmpty())
        assertNull(state.statistics)
        assertNull(state.listeningPreview)
        assertFalse(state.shouldShowEmptyState)
        assertFalse(state.shouldShowEmptyStateOnly)
    }

    @Test
    fun `loaded empty state shows the empty library prompt`() {
        val state = HomeState(isLoading = false)

        assertTrue(state.shouldShowEmptyState)
        assertTrue(state.shouldShowEmptyStateOnly)
    }

    @Test
    fun `configured empty library only shows the empty library state`() {
        val state = HomeState(
            isLoading = false,
        )

        assertTrue(state.shouldShowEmptyState)
        assertTrue(state.shouldShowEmptyStateOnly)
    }

    @Test
    fun `empty indexed library hides persisted home sections`() {
        val state = HomeState(
            isLoading = false,
            pinnedPlaylists = persistentListOf(
                HomePlaylist(
                    id = 1L,
                    title = "Saved playlist",
                    description = "From a previous library",
                    meta = "0 tracks",
                    artworkIndex = 1,
                    colors = persistentListOf(Color.Black),
                ),
            ),
        )

        assertTrue(state.shouldShowEmptyState)
        assertTrue(state.shouldShowEmptyStateOnly)
    }

    @Test
    fun `play track action identifies the selected library track`() {
        val action = HomeAction.PlayTrack(trackId = 42L)

        assertEquals(42L, action.trackId)
    }

    @Test
    fun `collection navigation actions identify the selected item`() {
        val albumAction = HomeAction.NavigateToAlbum(albumId = 7L)
        val artistAction = HomeAction.NavigateToArtist(artistId = 8L)
        val playlistAction = HomeAction.NavigateToPlaylist(playlistId = 9L)

        assertEquals(7L, albumAction.albumId)
        assertEquals(8L, artistAction.artistId)
        assertEquals(9L, playlistAction.playlistId)
    }

    @Test
    fun `home artists retain their library id for detail navigation`() {
        val artist = LibraryArtistItem(id = 8L, name = "Artist").toHomeArtist()

        assertEquals(8L, artist.id)
        assertEquals("Artist", artist.name)
    }

    @Test
    fun `statistics can be attached to state`() {
        val stats = io.github.julystar.musicapp.feature.home.domain.HomeStatistics(
            totalTracksEverPlayed = 10,
            totalListeningDurationMs = 3600_000L,
            tracksPlayedToday = 3,
            mostPlayedTrackIds = listOf(1L, 2L, 3L),
        )
        val state = HomeState(isLoading = false, statistics = stats)

        assertNotNull(state.statistics)
        assertTrue(state.shouldShowEmptyState)
        assertTrue(state.shouldShowEmptyStateOnly)
        assertEquals(10, state.statistics.totalTracksEverPlayed)
        assertEquals(3600_000L, state.statistics.totalListeningDurationMs)
        assertEquals(3, state.statistics.tracksPlayedToday)
        assertEquals(listOf(1L, 2L, 3L), state.statistics.mostPlayedTrackIds)
    }

    @Test
    fun `home tracks request metadata and plugin artwork lookup`() {
        val item = LibraryTrackItem(
            id = 42L,
            title = "Real track",
            artist = "Artist",
            durationMs = 180_000L,
        ).toHomeTrack(liked = false)

        assertEquals(
            Artwork.LibraryTrack(trackId = 42L, allowPluginLookup = true),
            item.artwork,
        )
    }

    @Test
    fun `home albums resolve artwork by album id`() {
        val item = LibraryAlbumItem(
            id = 7L,
            name = "Real album",
            year = 2026,
            artist = "Album artist",
        ).toHomeAlbum()

        assertEquals(7L, item.id)
        assertEquals("Real album", item.title)
        assertEquals("Album artist", item.subtitle)
        assertEquals(Artwork.LibraryAlbum(albumId = 7L), item.artwork)
    }

    @Test
    fun `daily picks select fifty tracks and remain stable for the day`() {
        val tracks = (1L..75L).map { id ->
            LibraryTrackItem(
                id = id,
                title = "Track $id",
                artist = null,
                durationMs = null,
            )
        }
        val date = LocalDate(2026, 8, 9)

        val firstSelection = selectDailyPickTracks(tracks, date).map(LibraryTrackItem::id)
        val repeatedSelection = selectDailyPickTracks(tracks.reversed(), date).map(LibraryTrackItem::id)
        val nextDaySelection = selectDailyPickTracks(tracks, LocalDate(2026, 8, 10))
            .map(LibraryTrackItem::id)

        assertEquals(50, firstSelection.size)
        assertEquals(50, firstSelection.distinct().size)
        assertEquals(firstSelection, repeatedSelection)
        assertNotEquals(firstSelection, nextDaySelection)
    }

    @Test
    fun `daily picks keep every track when the library has fewer than fifty`() {
        val tracks = (1L..12L).map { id ->
            LibraryTrackItem(
                id = id,
                title = "Track $id",
                artist = null,
                durationMs = null,
            )
        }

        val selection = selectDailyPickTracks(tracks, LocalDate(2026, 8, 9))

        assertEquals(tracks.map(LibraryTrackItem::id).toSet(), selection.map(LibraryTrackItem::id).toSet())
    }

    @Test
    fun `home listening preview ranks current month by time and play count`() {
        val firstTrackId = 1L
        val secondTrackId = 2L
        val augustPlay = Instant.parse("2026-08-08T12:00:00Z").toEpochMilliseconds()
        val julyPlay = Instant.parse("2026-07-31T12:00:00Z").toEpochMilliseconds()
        val preview = buildHomeListeningPreview(
            snapshot = ListeningStatisticsSnapshot(
                history = listOf(
                    listeningHistory(1L, firstTrackId, 60_000L, augustPlay),
                    listeningHistory(2L, firstTrackId, 60_000L, augustPlay + 1_000L),
                    listeningHistory(3L, secondTrackId, 300_000L, augustPlay + 2_000L),
                    listeningHistory(4L, secondTrackId, 900_000L, julyPlay),
                ),
            ),
            libraryTracks = listOf(
                LibraryTrackItem(firstTrackId, "First", "Artist", 180_000L),
                LibraryTrackItem(secondTrackId, "Second", "Artist", 180_000L),
            ),
            favoriteTrackIds = emptySet(),
            nowEpochMs = Instant.parse("2026-08-09T12:00:00Z").toEpochMilliseconds(),
            timeZone = TimeZone.UTC,
        )

        assertNotNull(preview)
        assertEquals(listOf(secondTrackId, firstTrackId), preview.durationRanking.map { it.track.id })
        assertEquals(listOf(firstTrackId, secondTrackId), preview.playCountRanking.map { it.track.id })
        assertEquals(2, preview.playCountRanking.first().playCount)
    }
}

private fun listeningHistory(
    id: Long,
    trackId: Long,
    listenedMs: Long,
    playedAtEpochMs: Long,
) = ListeningHistoryEntry(
    id = id,
    trackId = trackId,
    title = "Track $trackId",
    artist = "Artist",
    album = "Album",
    durationMs = 180_000L,
    listenedMs = listenedMs,
    playedAtEpochMs = playedAtEpochMs,
)
