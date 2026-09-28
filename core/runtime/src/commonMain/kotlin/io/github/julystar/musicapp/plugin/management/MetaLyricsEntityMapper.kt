package io.github.julystar.musicapp.plugin.management

import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.core.model.synced.UncheckedSyncedLine
import com.mocharealm.accompanist.lyrics.core.parser.AutoParser
import io.github.julystar.musicapp.database.LyricsEntity
import io.github.julystar.musicapp.source.api.MetaLyricLine
import io.github.julystar.musicapp.source.api.MetaLyricWord
import io.github.julystar.musicapp.source.api.MetaLyrics
import kotlin.math.abs

fun MetaLyrics.toEntity(trackId: Long, updatedAt: Long): LyricsEntity? {
    val persistableLines = lines.attachTranslatedTrack(translated)
    val extendedTtml = toExtendedTtmlOrNull()?.let { PersistedLyricPayload("TTML", it, wordTimed = timing != "Line" && lines.any { line -> line.words.isNotEmpty() }) }
    val structuredWordTimed = persistableLines.toEnhancedLrcOrNull()?.let { content ->
        PersistedLyricPayload("LRC", content, wordTimed = true)
    }
    val rawWordTimed = listOfNotNull(
        rawTtml.toPayload("TTML", wordTimed = true),
        rawMultiPersonEnhancedLrc.toPayload("LRC", wordTimed = true),
        rawEnhancedLrc.toPayload("LRC", wordTimed = true),
        rawVerbatimLrc.toPayload("LRC", wordTimed = true),
    ).firstOrNull()
    val hasStructuredTranslation = persistableLines.any { !it.translation.isNullOrBlank() }
    val wordTimed = if (hasStructuredTranslation) {
        structuredWordTimed ?: rawWordTimed
    } else {
        rawWordTimed ?: structuredWordTimed
    }
    val plain = rawPlainLrc.toPayload("LRC")
    val generated = persistableLines.takeIf { it.isNotEmpty() }?.joinToString("\n") { line ->
        val text = line.text.trim()
        val timeTag = line.startMs?.toLrcTimestamp().orEmpty()
        buildString {
            append(timeTag)
            append(text)
            line.translation?.trim()?.takeIf(String::isNotEmpty)?.let { translation ->
                append('\n')
                append(timeTag)
                append(translation)
            }
        }
    }
    val generatedPayload = generated?.let { content ->
        PersistedLyricPayload(
            format = if (persistableLines.any { it.startMs != null }) "LRC" else "TEXT",
            content = content,
            wordTimed = false,
        )
    }
    val fallbackPayload = if (hasStructuredTranslation) {
        generatedPayload ?: plain
    } else {
        plain ?: generatedPayload
    }
    val payload = extendedTtml ?: wordTimed ?: fallbackPayload ?: return null
    val synchronized = payload.format != "TEXT"
    return LyricsEntity(
        trackId = trackId,
        format = payload.format,
        language = language,
        synchronized = synchronized,
        content = payload.content,
        sourcePath = "external:plugin",
        updatedAt = updatedAt,
        sourceKind = when {
            payload.format == "TTML" -> "ExternalTtml"
            payload.wordTimed -> "ExternalWordTimed"
            else -> "ExternalPlain"
        },
    )
}

private fun List<MetaLyricLine>.attachTranslatedTrack(translated: String?): List<MetaLyricLine> {
    val candidates = translated?.toTranslationCandidates().orEmpty()
    if (candidates.isEmpty()) return this

    return mapIndexed { index, line ->
        if (!line.translation.isNullOrBlank()) return@mapIndexed line
        val timestampMatch = line.startMs?.let { startMs ->
            candidates
                .mapNotNull { candidate ->
                    candidate.startMs?.let { candidateStartMs ->
                        candidate to abs(candidateStartMs - startMs)
                    }
                }
                .minByOrNull { (_, distanceMs) -> distanceMs }
                ?.takeIf { (_, distanceMs) -> distanceMs <= 150L }
                ?.first
        }
        line.copy(translation = (timestampMatch ?: candidates.getOrNull(index))?.text)
    }
}

private data class TranslationCandidate(
    val text: String,
    val startMs: Long?,
)

private fun String.toTranslationCandidates(): List<TranslationCandidate> {
    val parsed = runCatching { AutoParser().parse(this).lines }.getOrDefault(emptyList())
    if (parsed.isNotEmpty()) {
        return parsed.mapNotNull { line ->
            val text = when (line) {
                is KaraokeLine -> line.syllables.joinToString(separator = "") { it.content }
                is SyncedLine -> line.content
                is UncheckedSyncedLine -> line.content
                else -> null
            }?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            TranslationCandidate(text = text, startMs = line.start.toLong())
        }
    }
    return lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map { text -> TranslationCandidate(text = text, startMs = null) }
        .toList()
}

private data class PersistedLyricPayload(
    val format: String,
    val content: String,
    val wordTimed: Boolean,
)

private fun String?.toPayload(
    format: String,
    wordTimed: Boolean = false,
): PersistedLyricPayload? = this
    ?.takeIf(String::isNotBlank)
    ?.let { content -> PersistedLyricPayload(format, content, wordTimed) }

private fun List<MetaLyricLine>.toEnhancedLrcOrNull(): String? {
    if (none { line -> line.words.any { word -> word.startMs != null } }) return null

    return mapIndexedNotNull { index, line ->
        val lineStart = line.startMs ?: return@mapIndexedNotNull null
        val timedWords = line.words.mapNotNull { word -> word.toAbsoluteTiming(lineStart) }
        val timeTag = lineStart.toLrcTimestamp()
        val primaryLine = if (timedWords.size != line.words.size || timedWords.isEmpty()) {
            "$timeTag${line.text.trim()}"
        } else {
            val lineEnd = line.endMs
                ?: timedWords.last().endMs
                ?: getOrNull(index + 1)?.startMs
                ?: (timedWords.last().startMs + 1)
            buildString {
                append(timeTag)
                timedWords.forEach { word ->
                    append('<')
                    append(word.startMs.toEnhancedLrcTimestamp())
                    append('>')
                    append(word.text)
                }
                append('<')
                append(lineEnd.coerceAtLeast(timedWords.last().startMs + 1).toEnhancedLrcTimestamp())
                append('>')
            }
        }
        line.translation?.trim()?.takeIf(String::isNotEmpty)?.let { translation ->
            "$primaryLine\n$timeTag$translation"
        } ?: primaryLine
    }.joinToString("\n").takeIf(String::isNotBlank)
}

private data class TimedWord(
    val text: String,
    val startMs: Long,
    val endMs: Long?,
)

private fun MetaLyricWord.toAbsoluteTiming(lineStartMs: Long): TimedWord? {
    val start = startMs ?: return null
    return TimedWord(
        text = text,
        startMs = start.toAbsoluteTime(lineStartMs),
        endMs = endMs?.toAbsoluteTime(lineStartMs),
    )
}

private fun Long.toAbsoluteTime(lineStartMs: Long): Long =
    if (this < lineStartMs) lineStartMs + this else this

private fun Long.toLrcTimestamp(): String {
    val safe = coerceAtLeast(0)
    val minutes = safe / 60_000
    val seconds = (safe % 60_000) / 1_000
    val hundredths = (safe % 1_000) / 10
    return "[${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}." +
        "${hundredths.toString().padStart(2, '0')}]"
}

private fun Long.toEnhancedLrcTimestamp(): String {
    val safe = coerceAtLeast(0)
    val minutes = safe / 60_000
    val seconds = (safe % 60_000) / 1_000
    val milliseconds = safe % 1_000
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}." +
        milliseconds.toString().padStart(3, '0')
}
