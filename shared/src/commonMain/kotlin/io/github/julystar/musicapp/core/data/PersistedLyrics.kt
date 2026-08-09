package io.github.julystar.musicapp.core.data

import io.github.julystar.musicapp.core.domain.model.LyricLine
import io.github.julystar.musicapp.core.domain.model.LyricDisplaySettings
import io.github.julystar.musicapp.core.domain.model.LyricSourceKind
import io.github.julystar.musicapp.core.domain.model.LyricSourceMode
import io.github.julystar.musicapp.core.domain.model.LyricWord
import io.github.julystar.musicapp.core.domain.model.Lyrics
import io.github.julystar.musicapp.core.domain.model.LyricsLoadState
import io.github.julystar.musicapp.core.domain.model.isLyricHeaderTag
import io.github.julystar.musicapp.database.LyricsEntity
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.core.model.synced.UncheckedSyncedLine
import com.mocharealm.accompanist.lyrics.core.parser.AutoParser
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlin.time.Duration.Companion.milliseconds

internal fun LyricsEntity.toPlaybackLyrics(): Lyrics {
    if (!synchronized && !format.equals("TTML", ignoreCase = true) && !content.hasWordTiming()) {
        return Lyrics(
            lines = persistentListOf(LyricLine(0.milliseconds, content)),
            loadState = LyricsLoadState.Loaded,
        )
    }

    val parsedLines = runCatching { AutoParser().parse(content).lines }
        .getOrDefault(emptyList())
        .mapNotNull { line ->
            when (line) {
                is KaraokeLine -> line.toDomainLine()
                is SyncedLine -> LyricLine(
                    line.start.milliseconds,
                    line.content.withTranslation(line.translation),
                )
                is UncheckedSyncedLine -> LyricLine(
                    line.start.milliseconds,
                    line.content.withTranslation(line.translation),
                )
                else -> null
            }
        }
        .ifEmpty {
            content.lineSequence().mapNotNull(::parseBasicLrcLine).toList()
        }

    val headerLines = content.leadingLyricHeaderTags().map { header ->
        LyricLine(0.milliseconds, header)
    }
    return Lyrics(
        lines = (headerLines + parsedLines).toPersistentList(),
        loadState = LyricsLoadState.Loaded,
    )
}

internal fun List<LyricsEntity>.selectLyrics(settings: LyricDisplaySettings): LyricsEntity? {
    val eligible = filter { entity ->
        when (settings.sourceMode) {
            LyricSourceMode.Auto -> true
            LyricSourceMode.Embedded -> entity.resolvedSourceKind().isEmbedded
            LyricSourceMode.External -> !entity.resolvedSourceKind().isEmbedded
        }
    }
    val priority = settings.sourcePriority.withIndex().associate { (index, kind) -> kind to index }
    return eligible.minWithOrNull(
        compareBy<LyricsEntity> { entity -> priority[entity.resolvedSourceKind()] ?: Int.MAX_VALUE }
            .thenByDescending(LyricsEntity::updatedAt),
    )
}

internal fun LyricsEntity.resolvedSourceKind(): LyricSourceKind =
    run {
        val embedded = sourcePath.isNullOrBlank() || sourcePath.startsWith("embedded", ignoreCase = true)
        val ttml = format.equals("TTML", ignoreCase = true)
        val stored = runCatching { LyricSourceKind.valueOf(sourceKind) }.getOrNull()
        when {
            embedded && ttml -> LyricSourceKind.EmbeddedTtml
            ttml -> LyricSourceKind.ExternalTtml
            embedded && (stored == LyricSourceKind.EmbeddedWordTimed || content.hasWordTiming()) ->
                LyricSourceKind.EmbeddedWordTimed
            !embedded && (stored == LyricSourceKind.ExternalWordTimed || content.hasWordTiming()) ->
                LyricSourceKind.ExternalWordTimed
            embedded -> LyricSourceKind.EmbeddedPlain
            else -> LyricSourceKind.ExternalPlain
        }
    }

private val LyricSourceKind.isEmbedded: Boolean
    get() = this == LyricSourceKind.EmbeddedTtml ||
        this == LyricSourceKind.EmbeddedWordTimed ||
        this == LyricSourceKind.EmbeddedPlain

internal fun List<LyricsEntity>.shouldLookupPreferredExternalLyrics(
    settings: LyricDisplaySettings,
): Boolean {
    if (settings.sourceMode == LyricSourceMode.Embedded) return false

    val priority = settings.sourcePriority.withIndex().associate { (index, kind) -> kind to index }
    val qualityRank = listOf(
        LyricSourceKind.ExternalTtml,
        LyricSourceKind.ExternalWordTimed,
    ).minOf { kind -> priority[kind] ?: Int.MAX_VALUE }
    val fallbackKinds = when (settings.sourceMode) {
        LyricSourceMode.Auto -> listOf(
            LyricSourceKind.EmbeddedPlain,
            LyricSourceKind.ExternalPlain,
        )
        LyricSourceMode.External -> listOf(LyricSourceKind.ExternalPlain)
        LyricSourceMode.Embedded -> emptyList()
    }
    val fallbackRank = fallbackKinds.minOfOrNull { kind ->
        priority[kind] ?: Int.MAX_VALUE
    } ?: Int.MAX_VALUE
    if (qualityRank >= fallbackRank) return false

    val selected = selectLyrics(settings) ?: return true
    val selectedKind = selected.resolvedSourceKind()
    if (
        selectedKind == LyricSourceKind.ExternalTtml ||
        selectedKind == LyricSourceKind.ExternalWordTimed
    ) {
        return false
    }
    return (priority[selectedKind] ?: Int.MAX_VALUE) > qualityRank
}

private fun String.hasWordTiming(): Boolean =
    runCatching {
        AutoParser().parse(this).lines.any { line ->
            line is KaraokeLine && line.syllables.isNotEmpty()
        }
    }.getOrDefault(false)

private fun String.leadingLyricHeaderTags(): List<String> =
    lineSequence()
        .map { line -> line.trim().removePrefix("\uFEFF").trimStart() }
        .dropWhile(String::isBlank)
        .takeWhile { line -> line.isBlank() || isLyricHeaderTag(line) }
        .filter(::isLyricHeaderTag)
        .toList()

private fun parseBasicLrcLine(rawLine: String): LyricLine? {
    val line = rawLine.trim()
    if (!line.startsWith("[")) return null
    val close = line.indexOf(']')
    if (close <= 1) return null
    val timestamp = line.substring(1, close).split(':')
    if (timestamp.size != 2) return null
    val minutes = timestamp[0].toLongOrNull() ?: return null
    val seconds = timestamp[1].toDoubleOrNull() ?: return null
    val text = line.substring(close + 1).trim()
    if (text.isEmpty()) return null
    return LyricLine(
        duration = (minutes * 60_000L + (seconds * 1_000.0).toLong()).milliseconds,
        text = text,
    )
}

private fun KaraokeLine.toDomainLine(): LyricLine {
    val lineStart = start.coerceAtLeast(0)
    return LyricLine(
        duration = lineStart.milliseconds,
        text = syllables
            .joinToString(separator = "") { syllable -> syllable.content }
            .withTranslation(translation),
        words = syllables.map { syllable ->
            LyricWord(
                text = syllable.content,
                startOffset = (syllable.start - lineStart).coerceAtLeast(0).milliseconds,
                duration = (syllable.end - syllable.start).coerceAtLeast(1).milliseconds,
            )
        }.toPersistentList(),
    )
}

private fun String.withTranslation(translation: String?): String {
    val normalizedTranslation = translation?.trim()?.takeIf(String::isNotEmpty) ?: return this
    return "$this\n$normalizedTranslation"
}
