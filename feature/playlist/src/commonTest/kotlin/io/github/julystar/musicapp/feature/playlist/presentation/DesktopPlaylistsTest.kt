package io.github.julystar.musicapp.feature.playlist.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopPlaylistsTest {
    private val playlists = listOf(
        PlaylistListItem(1, "Morning", "2", "5:00", null, createdAt = 300),
        PlaylistListItem(2, "夜晚", "3", "8:00", null, createdAt = 100),
        PlaylistListItem(3, "morning radio", "1", "3:00", null, createdAt = 200),
    )

    @Test
    fun searchIgnoresCaseAndSurroundingWhitespace() {
        val shown = shownDesktopPlaylists(playlists, " MORNING ", DesktopPlaylistSort.Title, false)
        assertEquals(listOf(1L, 3L), shown.map { it.id })
        assertEquals(listOf(2L), shownDesktopPlaylists(playlists, "夜", DesktopPlaylistSort.Title, false).map { it.id })
    }

    @Test
    fun recentlyAddedUsesTheCreationDateInsteadOfThePlaylistIdOrManualOrder() {
        val shown = shownDesktopPlaylists(playlists, "", DesktopPlaylistSort.RecentlyAdded, true)
        assertEquals(listOf(1L, 3L, 2L), shown.map { it.id })
        assertEquals(listOf(2L, 3L, 1L), shownDesktopPlaylists(playlists, "", DesktopPlaylistSort.RecentlyAdded, false).map { it.id })
    }

    @Test
    fun filteringDoesNotChangeTheRepositoryOrder() {
        assertEquals(emptyList(), shownDesktopPlaylists(playlists, "missing", DesktopPlaylistSort.Title, false))
        assertEquals(listOf(1L, 2L, 3L), playlists.map { it.id })
    }
}
