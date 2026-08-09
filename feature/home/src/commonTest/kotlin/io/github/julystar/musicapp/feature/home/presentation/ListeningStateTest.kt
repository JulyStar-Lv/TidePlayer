package io.github.julystar.musicapp.feature.home.presentation

import io.github.julystar.musicapp.core.domain.model.LibraryTrackItem
import io.github.julystar.musicapp.feature.home.domain.ListeningHistoryEntry
import io.github.julystar.musicapp.feature.home.domain.ListeningStatisticsSnapshot
import io.github.julystar.musicapp.feature.home.domain.ListeningTrackStatistics
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ListeningStateTest {
    @Test
    fun buildsMonthlyReportCalendarAndRankingsFromHistory() {
        val firstPlay = Instant.parse("2026-07-25T08:00:00Z").toEpochMilliseconds()
        val secondPlay = Instant.parse("2026-07-26T09:00:00Z").toEpochMilliseconds()
        val snapshot = ListeningStatisticsSnapshot(
            history = listOf(
                history(1L, 7L, "Moon", "Ella", 30_000L, firstPlay),
                history(2L, 7L, "Moon", "Ella", 40_000L, secondPlay),
            ),
            tracks = listOf(
                ListeningTrackStatistics(
                    trackId = 7L,
                    title = "Moon",
                    artist = "Ella",
                    album = "Night",
                    durationMs = 180_000L,
                    playCount = 2,
                    listenedMs = 70_000L,
                    lastPlayedAtEpochMs = secondPlay,
                ),
            ),
        )

        val state = buildListeningState(
            snapshot = snapshot,
            libraryTracks = listOf(
                LibraryTrackItem(7L, "Moon", "Ella", 180_000L),
            ),
            selectedTab = ListeningTab.Overview,
            isLoading = false,
            nowEpochMs = Instant.parse("2026-07-27T12:00:00Z").toEpochMilliseconds(),
            timeZone = TimeZone.UTC,
        )

        assertEquals("2026-07", state.monthLabel)
        assertEquals(2, state.monthPlayCount)
        assertEquals(1, state.monthUniqueTrackCount)
        assertEquals(70_000L, state.monthListenedMs)
        assertEquals(2, state.activeDays)
        assertEquals(27, state.elapsedDaysInMonth)
        assertEquals(2, state.longestStreakDays)
        assertEquals(84, state.calendarDays.size)
        assertEquals("Moon", state.durationRanking.single().title)
        assertEquals("Night", state.durationRanking.single().album)
        assertEquals(2, state.playCountRanking.single().playCount)
        assertEquals(ListeningTimePeriod.Morning, state.peakTimePeriod)
    }
}

private fun history(
    id: Long,
    trackId: Long,
    title: String,
    artist: String,
    listenedMs: Long,
    playedAtEpochMs: Long,
) = ListeningHistoryEntry(
    id = id,
    trackId = trackId,
    title = title,
    artist = artist,
    album = "Night",
    durationMs = 180_000L,
    listenedMs = listenedMs,
    playedAtEpochMs = playedAtEpochMs,
)
