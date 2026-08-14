package io.github.julystar.musicapp.core.domain.model

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

const val LYRIC_HEADER_PLACEHOLDER = "•••"

/** Shared filtering used by the full lyrics page, player lyrics, and platform outputs. */
fun LyricDisplaySettings.isLyricLineVisible(rawText: String): Boolean {
    val text = rawText.normalizedLyricText()
    if (text.isEmpty()) return false
    return !ignoreHeaderTags || !isLyricHeaderLine(text)
}

fun isLyricHeaderTag(rawText: String): Boolean =
    LYRIC_HEADER_TAG.matches(rawText.normalizedLyricText())

fun LyricDisplaySettings.filterLyricTextBlock(content: String): List<String> {
    val visibleLines = content.lineSequence().filter { line ->
        line.normalizedLyricText().isNotEmpty()
    }.toList()
    if (!ignoreHeaderTags) return visibleLines

    val headerEndIndex = visibleLines.leadingLyricHeaderEndIndex { line -> line }
        ?: return visibleLines
    val lyricLines = visibleLines.drop(headerEndIndex + 1)
    return if (lyricLines.isEmpty()) emptyList() else listOf(LYRIC_HEADER_PLACEHOLDER) + lyricLines
}

fun List<LyricLine>.filterLyricLinesForDisplay(settings: LyricDisplaySettings): List<LyricLine> {
    val visibleLines = filter { line ->
        val primary = line.text.lineSequence().firstOrNull(String::isNotBlank).orEmpty()
        primary.normalizedLyricText().isNotEmpty()
    }
    if (!settings.ignoreHeaderTags) return visibleLines

    val headerEndIndex = visibleLines.leadingLyricHeaderEndIndex { line ->
        line.text.lineSequence().firstOrNull(String::isNotBlank).orEmpty()
    }
        ?: return visibleLines
    val lyricLines = visibleLines.drop(headerEndIndex + 1)
    if (lyricLines.isEmpty()) return emptyList()

    val placeholder = visibleLines.first().copy(
        text = LYRIC_HEADER_PLACEHOLDER,
        words = persistentListOf(),
    )
    return listOf(placeholder) + lyricLines
}

/** Keeps platform lyric outputs in sync with the filtering used by the in-app player. */
fun Lyrics.filteredForDisplay(settings: LyricDisplaySettings): Lyrics {
    val containsUnsynchronisedBlock = lines.size == 1 &&
        lines.first().words.isEmpty() &&
        lines.first().duration.inWholeMilliseconds == 0L
    val visibleLines = if (containsUnsynchronisedBlock) {
        val line = lines.first()
        settings.filterLyricTextBlock(line.text)
            .takeIf { visibleText -> visibleText.isNotEmpty() }
            ?.let { visibleText -> listOf(line.copy(text = visibleText.joinToString("\n"))) }
            .orEmpty()
    } else {
        lines.filterLyricLinesForDisplay(settings)
    }
    return copy(lines = visibleLines.toPersistentList())
}

private fun String.normalizedLyricText(): String = trim().removePrefix("\uFEFF").trimStart()

private fun String.lyricHeaderDetectionText(): String =
    KARAOKE_TIMESTAMPS.replace(
        LEADING_LRC_TIMESTAMPS.replace(normalizedLyricText(), ""),
        "",
    ).trimStart()

private fun isLyricHeaderLine(rawText: String): Boolean {
    val text = rawText.lyricHeaderDetectionText()
    return LYRIC_HEADER_TAG.matches(text) ||
        ENGLISH_LYRIC_CREDIT.matches(text) ||
        CJK_LYRIC_CREDIT.matches(text)
}

private fun isLyricHeaderSeparator(rawText: String): Boolean {
    val text = rawText.lyricHeaderDetectionText()
    return text.isNotEmpty() && text.all { character ->
        character in "/／\\|｜·•-—–_=*~～"
    }
}

private fun isLyricHeaderContinuation(rawText: String): Boolean {
    val text = rawText.lyricHeaderDetectionText()
    return isLyricHeaderLine(text) ||
        isLyricHeaderSeparator(text) ||
        SHORT_CREDIT_LABEL.matches(text)
}

private inline fun <T> List<T>.leadingLyricHeaderEndIndex(textOf: (T) -> String): Int? {
    val firstHeaderIndex = indices
        .take(MAX_LEADING_HEADER_SEARCH_LINES)
        .firstOrNull { index -> isLyricHeaderLine(textOf(this[index])) }
        ?: return null

    var headerEndIndex = firstHeaderIndex
    for (index in (firstHeaderIndex + 1)..lastIndex) {
        val text = textOf(this[index])
        if (!isLyricHeaderContinuation(text)) break
        headerEndIndex = index
    }
    return headerEndIndex
}

private const val MAX_LEADING_HEADER_SEARCH_LINES = 6

private val LEADING_LRC_TIMESTAMPS = Regex(
    pattern = """^(?:\[\s*\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?\s*])+\s*""",
)

private val KARAOKE_TIMESTAMPS = Regex(
    pattern = """(?:<|\[)\s*\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?\s*(?:>|])""",
)

private val ENGLISH_LYRIC_CREDIT = Regex(
    pattern = """^(lyrics?|words?|written|composed|composition|music|produced|producer|arranged|arrangement|performed|sung|vocals?|artist|album|title|lrc|lyric\s+source)(?:\s+by)?\s*[:：].*$""",
    option = RegexOption.IGNORE_CASE,
)

private val CJK_LYRIC_CREDIT = Regex(
    pattern = """^(作词|作詞|填词|填詞|作曲|编曲|編曲|演唱|歌手|原唱|翻唱|词曲|詞曲|制作人|製作人|制作|製作|监制|監製|混音|和声|和聲|录音|錄音|母带|母帶|发行|發行|出品|작사|작곡|편곡|가수|词|詞|曲)\s*[:：].*$""",
    option = RegexOption.IGNORE_CASE,
)

private val SHORT_CREDIT_LABEL = Regex(pattern = """^.{1,24}[:：]\s*$""")

private val LYRIC_HEADER_TAG = Regex(
    pattern = """^\[\s*(ar|artist|al|album|ti|title|au|author|by|offset|length|re|ve|tool|provider|id|language|la|encoding|enc|source|src|kana)\s*[:：].*]$""",
    option = RegexOption.IGNORE_CASE,
)
