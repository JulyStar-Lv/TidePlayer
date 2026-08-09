package io.github.julystar.musicapp.service.playback.presentation.nowplaying

import io.github.julystar.musicapp.core.domain.model.LyricLine
import io.github.julystar.musicapp.core.domain.model.LyricDisplaySettings
import io.github.julystar.musicapp.core.domain.model.filterLyricLinesForDisplay
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine

/** Converts MelodyTrove timestamped lyric lines into the accompanist lyrics-core timeline model. */
fun List<LyricLine>.toSyncedLyrics(
    trackTitle: String,
    trackDurationMs: Long?,
    settings: LyricDisplaySettings = LyricDisplaySettings.Default,
): SyncedLyrics {
    val visibleLines = filterVisibleLyrics(settings)
    val timeline = visibleLines.mapIndexed { index, line ->
        val startMs = line.duration.inWholeMilliseconds.toSafeInt().coerceAtLeast(0)
        val nextStartMs = visibleLines.getOrNull(index + 1)
            ?.duration
            ?.inWholeMilliseconds
            ?.toSafeInt()
        val fallbackEndMs = if (index == visibleLines.lastIndex) {
            trackDurationMs?.toSafeInt()
        } else {
            nextStartMs
        }
        val endMs = fallbackEndMs
            ?.coerceAtLeast(startMs + 1)
            ?: (startMs + 5_000)

        line.toSyncedLine(startMs = startMs, endMs = endMs)
    }

    return SyncedLyrics(
        lines = timeline,
        title = trackTitle,
    )
}

internal fun List<LyricLine>.filterVisibleLyrics(settings: LyricDisplaySettings): List<LyricLine> {
    val containsUnsynchronisedBlock = size == 1 &&
        first().words.isEmpty() &&
        first().duration.inWholeMilliseconds == 0L
    return flatMap { line ->
        // Unsynchronised lyrics are persisted as one block. Split that block here so
        // header and blacklist rules behave exactly like they do for timed lyrics.
        if (containsUnsynchronisedBlock && ('\n' in line.text || '\r' in line.text)) {
            line.text.lineSequence().map { text -> line.copy(text = text) }.toList()
        } else {
            listOf(line)
        }
    }.filterLyricLinesForDisplay(settings)
}

private fun LyricLine.toSyncedLine(startMs: Int, endMs: Int): ISyncedLine {
    val textParts = text.lyricTextParts()
    if (words.isEmpty()) {
        return SyncedLine(
            content = textParts.primary,
            translation = textParts.secondary,
            start = startMs,
            end = endMs,
        )
    }

    val syllables = words.mapIndexed { index, word ->
        val wordStart = (startMs.toLong() + word.startOffset.inWholeMilliseconds)
            .toSafeInt()
            .coerceIn(startMs, endMs)
        val wordEnd = (wordStart.toLong() + word.duration.inWholeMilliseconds)
            .toSafeInt()
            .coerceIn(wordStart, endMs)
        KaraokeSyllable(
            content = word.text + wordSeparatorAfter(index),
            start = wordStart,
            end = wordEnd,
        )
    }

    return KaraokeLine.MainKaraokeLine(
        syllables = syllables,
        translation = textParts.secondary,
        alignment = KaraokeAlignment.Start,
        start = startMs,
        end = endMs,
    )
}

private data class LyricTextParts(
    val primary: String,
    val secondary: String?,
)

private fun String.lyricTextParts(): LyricTextParts {
    val parts = lineSequence().map(String::trim).filter(String::isNotBlank).toList()
    return LyricTextParts(
        primary = parts.firstOrNull().orEmpty(),
        secondary = parts.drop(1).joinToString("\n").takeIf(String::isNotBlank),
    )
}

private fun LyricLine.wordSeparatorAfter(index: Int): String {
    if (index >= words.lastIndex || text.none(Char::isWhitespace)) return ""
    val currentEndsWithSpace = words[index].text.lastOrNull()?.isWhitespace() == true
    val nextStartsWithSpace = words[index + 1].text.firstOrNull()?.isWhitespace() == true
    return if (currentEndsWithSpace || nextStartsWithSpace) "" else " "
}

private fun Long.toSafeInt(): Int = coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
