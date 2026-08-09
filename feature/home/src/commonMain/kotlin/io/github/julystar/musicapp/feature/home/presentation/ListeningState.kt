package io.github.julystar.musicapp.feature.home.presentation

import androidx.compose.runtime.Immutable
import io.github.julystar.musicapp.core.domain.model.LibraryTrackItem
import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.feature.home.domain.ListeningDistributionBucket
import io.github.julystar.musicapp.feature.home.domain.ListeningHistoryEntry
import io.github.julystar.musicapp.feature.home.domain.ListeningStatisticsSnapshot
import io.github.julystar.musicapp.feature.home.domain.ListeningTrackStatistics
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

enum class ListeningTab {
    Overview,
    Calendar,
    Rankings,
}

enum class ListeningTimePeriod {
    LateNight,
    Morning,
    Afternoon,
    Evening,
}

@Immutable
data class ListeningState(
    val isLoading: Boolean = true,
    val selectedTab: ListeningTab = ListeningTab.Overview,
    val monthLabel: String = "",
    val monthPlayCount: Int = 0,
    val monthUniqueTrackCount: Int = 0,
    val monthListenedMs: Long = 0L,
    val activeDays: Int = 0,
    val elapsedDaysInMonth: Int = 0,
    val longestStreakDays: Int = 0,
    val averagePerActiveDayMs: Long = 0L,
    val peakTimePeriod: ListeningTimePeriod? = null,
    val favoriteTrack: ListeningInsight? = null,
    val favoriteArtist: ListeningInsight? = null,
    val favoriteAlbum: ListeningInsight? = null,
    val calendarDays: List<ListeningDay> = emptyList(),
    val recentHistory: List<ListeningHistoryItem> = emptyList(),
    val durationRanking: List<ListeningRankedTrack> = emptyList(),
    val playCountRanking: List<ListeningRankedTrack> = emptyList(),
    val formatDistribution: List<ListeningDistributionBucket> = emptyList(),
    val qualityDistribution: List<ListeningDistributionBucket> = emptyList(),
)

@Immutable
data class ListeningInsight(
    val title: String,
    val subtitle: String?,
    val playCount: Int,
)

@Immutable
data class ListeningDay(
    val date: LocalDate,
    val listenedMs: Long,
    val playCount: Int,
)

@Immutable
data class ListeningHistoryItem(
    val id: Long,
    val trackId: Long,
    val mediaId: MediaId?,
    val title: String,
    val artist: String?,
    val listenedMs: Long,
    val playedAtEpochMs: Long,
)

@Immutable
data class ListeningRankedTrack(
    val trackId: Long,
    val mediaId: MediaId?,
    val title: String,
    val artist: String?,
    val album: String?,
    val playCount: Int,
    val listenedMs: Long,
)

sealed interface ListeningAction {
    data object NavigateBack : ListeningAction
    data class SelectTab(val tab: ListeningTab) : ListeningAction
    data class PlayTrack(val trackId: Long) : ListeningAction
}

internal fun buildListeningState(
    snapshot: ListeningStatisticsSnapshot,
    libraryTracks: List<LibraryTrackItem>,
    selectedTab: ListeningTab,
    isLoading: Boolean,
    nowEpochMs: Long = Clock.System.now().toEpochMilliseconds(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): ListeningState {
    val now = Instant.fromEpochMilliseconds(nowEpochMs).toLocalDateTime(timeZone)
    val today = now.date
    val libraryById = libraryTracks.associateBy(LibraryTrackItem::id)
    val monthlyHistory = snapshot.history.filter { entry ->
        val date = entry.playedAtEpochMs.toLocalDate(timeZone)
        date.year == today.year && date.month == today.month
    }
    val dailyHistory = snapshot.history.groupBy { entry ->
        entry.playedAtEpochMs.toLocalDate(timeZone)
    }
    val dailyListenMs = dailyHistory.mapValues { (_, entries) ->
        entries.sumOf(ListeningHistoryEntry::listenedMs)
    }
    val activeMonthDates = dailyListenMs
        .filter { (date, listenedMs) ->
            date.year == today.year && date.month == today.month && listenedMs > 0L
        }
        .keys
    val firstCalendarDay = today.minus(83, DateTimeUnit.DAY)

    return ListeningState(
        isLoading = isLoading,
        selectedTab = selectedTab,
        monthLabel = "${today.year}-${(today.month.ordinal + 1).toString().padStart(2, '0')}",
        monthPlayCount = monthlyHistory.size,
        monthUniqueTrackCount = monthlyHistory.distinctBy(ListeningHistoryEntry::trackId).size,
        monthListenedMs = monthlyHistory.sumOf(ListeningHistoryEntry::listenedMs),
        activeDays = activeMonthDates.size,
        elapsedDaysInMonth = today.day,
        longestStreakDays = longestStreak(activeMonthDates),
        averagePerActiveDayMs = monthlyHistory
            .sumOf(ListeningHistoryEntry::listenedMs)
            .let { total -> if (activeMonthDates.isEmpty()) 0L else total / activeMonthDates.size },
        peakTimePeriod = monthlyHistory.peakTimePeriod(timeZone),
        favoriteTrack = monthlyHistory.favoriteTrack(),
        favoriteArtist = monthlyHistory.favoriteArtist(),
        favoriteAlbum = monthlyHistory.favoriteAlbum(),
        calendarDays = List(84) { offset ->
            val date = firstCalendarDay.plus(offset, DateTimeUnit.DAY)
            ListeningDay(
                date = date,
                listenedMs = dailyListenMs[date] ?: 0L,
                playCount = dailyHistory[date]?.size ?: 0,
            )
        },
        recentHistory = snapshot.history.take(50).map { entry ->
            ListeningHistoryItem(
                id = entry.id,
                trackId = entry.trackId,
                mediaId = libraryById[entry.trackId]?.mediaId,
                title = entry.title,
                artist = entry.artist,
                listenedMs = entry.listenedMs,
                playedAtEpochMs = entry.playedAtEpochMs,
            )
        },
        durationRanking = snapshot.tracks
            .filter { it.listenedMs > 0L }
            .sortedByDescending(ListeningTrackStatistics::listenedMs)
            .take(10)
            .map { it.toRankedTrack(libraryById[it.trackId]?.mediaId) },
        playCountRanking = snapshot.tracks
            .filter { it.playCount > 0 }
            .sortedWith(
                compareByDescending<ListeningTrackStatistics>(ListeningTrackStatistics::playCount)
                    .thenByDescending(ListeningTrackStatistics::listenedMs),
            )
            .take(10)
            .map { it.toRankedTrack(libraryById[it.trackId]?.mediaId) },
        formatDistribution = snapshot.libraryAnalysis.formatDistribution,
        qualityDistribution = snapshot.libraryAnalysis.qualityDistribution,
    )
}

private fun Long.toLocalDate(timeZone: TimeZone): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(timeZone).date

private fun longestStreak(activeDates: Set<LocalDate>): Int {
    if (activeDates.isEmpty()) return 0
    val sorted = activeDates.sorted()
    var longest = 0
    var current = 0
    var previous: LocalDate? = null
    sorted.forEach { date ->
        current = if (previous != null && date.minus(1, DateTimeUnit.DAY) == previous) {
            current + 1
        } else {
            1
        }
        longest = maxOf(longest, current)
        previous = date
    }
    return longest
}

private fun List<ListeningHistoryEntry>.peakTimePeriod(
    timeZone: TimeZone,
): ListeningTimePeriod? {
    if (isEmpty()) return null
    val buckets = IntArray(ListeningTimePeriod.entries.size)
    forEach { entry ->
        val hour = Instant.fromEpochMilliseconds(entry.playedAtEpochMs)
            .toLocalDateTime(timeZone)
            .hour
        val index = when (hour) {
            in 0..5 -> ListeningTimePeriod.LateNight.ordinal
            in 6..11 -> ListeningTimePeriod.Morning.ordinal
            in 12..17 -> ListeningTimePeriod.Afternoon.ordinal
            else -> ListeningTimePeriod.Evening.ordinal
        }
        buckets[index] += 1
    }
    return ListeningTimePeriod.entries[buckets.indices.maxBy { buckets[it] }]
}

private fun List<ListeningHistoryEntry>.favoriteTrack(): ListeningInsight? =
    groupBy(ListeningHistoryEntry::trackId)
        .values
        .maxByOrNull(List<ListeningHistoryEntry>::size)
        ?.let { entries ->
            ListeningInsight(
                title = entries.first().title,
                subtitle = entries.first().artist,
                playCount = entries.size,
            )
        }

private fun List<ListeningHistoryEntry>.favoriteArtist(): ListeningInsight? =
    favoriteTextInsight(
        value = { entry -> entry.artist },
        subtitle = { entry -> entry.album },
    )

private fun List<ListeningHistoryEntry>.favoriteAlbum(): ListeningInsight? =
    favoriteTextInsight(
        value = { entry -> entry.album },
        subtitle = { entry -> entry.artist },
    )

private fun List<ListeningHistoryEntry>.favoriteTextInsight(
    value: (ListeningHistoryEntry) -> String?,
    subtitle: (ListeningHistoryEntry) -> String?,
): ListeningInsight? = mapNotNull { entry ->
    value(entry)?.trim()?.takeIf(String::isNotBlank)?.let { text -> text to entry }
}
    .groupBy { (text, _) -> text.lowercase() }
    .values
    .maxByOrNull(List<Pair<String, ListeningHistoryEntry>>::size)
    ?.let { entries ->
        ListeningInsight(
            title = entries.first().first,
            subtitle = subtitle(entries.first().second),
            playCount = entries.size,
        )
    }

private fun ListeningTrackStatistics.toRankedTrack(mediaId: MediaId?) =
    ListeningRankedTrack(
        trackId = trackId,
        mediaId = mediaId,
        title = title,
        artist = artist,
        album = album,
        playCount = playCount,
        listenedMs = listenedMs,
    )
