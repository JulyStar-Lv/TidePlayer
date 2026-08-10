package io.github.julystar.musicapp.service.download.data.finalization

import io.github.julystar.musicapp.core.data.resolvedSourceKind
import io.github.julystar.musicapp.core.data.toPlaybackLyrics
import io.github.julystar.musicapp.core.domain.model.LyricSourceKind
import io.github.julystar.musicapp.core.domain.model.isLyricHeaderTag
import io.github.julystar.musicapp.database.LyricsEntity
import io.github.julystar.musicapp.service.download.domain.LyricsSnapshot
import io.github.julystar.musicapp.service.download.domain.LyricsSnapshotFormat
import io.github.julystar.musicapp.service.download.domain.MetadataSource

internal fun LyricsEntity.toLyricsSnapshot(): LyricsSnapshot? {
    val normalized = content.trim().takeIf(String::isNotEmpty) ?: return null
    val kind = resolvedSourceKind()
    val source = if (kind.isEmbedded) MetadataSource.Embedded else MetadataSource.Plugin
    val isTtml = format.equals("TTML", ignoreCase = true)
    val isPlain = !synchronized && format.equals("TEXT", ignoreCase = true)
    val lrc = when {
        isPlain -> null
        isTtml -> toPlaybackLyrics().toCompatibleLrc()
        else -> normalized
    }
    val embedded = when {
        isPlain -> normalized
        else -> lrc
    }
    return LyricsSnapshot(
        embedded = embedded,
        lrc = lrc,
        ttml = normalized.takeIf { isTtml },
        format = when {
            isTtml -> LyricsSnapshotFormat.Ttml
            kind == LyricSourceKind.EmbeddedWordTimed ||
                kind == LyricSourceKind.ExternalWordTimed -> LyricsSnapshotFormat.WordTimed
            isPlain -> LyricsSnapshotFormat.Plain
            else -> LyricsSnapshotFormat.Lrc
        },
        source = source,
    )
}

private val LyricSourceKind.isEmbedded: Boolean
    get() = this == LyricSourceKind.EmbeddedTtml ||
        this == LyricSourceKind.EmbeddedWordTimed ||
        this == LyricSourceKind.EmbeddedPlain

private fun io.github.julystar.musicapp.core.domain.model.Lyrics.toCompatibleLrc(): String? {
    return lines.asSequence()
        .mapNotNull { line ->
            val primary = line.text.substringBefore('\n').trim()
            if (primary.isEmpty() || isLyricHeaderTag(primary)) return@mapNotNull null
            "${line.duration.inWholeMilliseconds.toLrcTimestamp()}$primary"
        }
        .joinToString("\n")
        .takeIf(String::isNotBlank)
}

private fun Long.toLrcTimestamp(): String {
    val safe = coerceAtLeast(0L)
    val minutes = safe / 60_000L
    val seconds = (safe % 60_000L) / 1_000L
    val millis = safe % 1_000L
    return buildString(12) {
        append('[')
        append(minutes.toString().padStart(2, '0'))
        append(':')
        append(seconds.toString().padStart(2, '0'))
        append('.')
        append(millis.toString().padStart(3, '0'))
        append(']')
    }
}
