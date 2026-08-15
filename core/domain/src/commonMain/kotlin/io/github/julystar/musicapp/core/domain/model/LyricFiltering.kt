package io.github.julystar.musicapp.core.domain.model

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList

const val LYRIC_HEADER_PLACEHOLDER = "•••"

/** Shared filtering used by the full lyrics page, player lyrics, and platform outputs. */
fun LyricDisplaySettings.isLyricLineVisible(rawText: String): Boolean {
    if (rawText.normalizedLyricText().isEmpty()) return false
    return !ignoreHeaderTags || !rawText.isStronglyHiddenLyricLine()
}

fun isLyricHeaderTag(rawText: String): Boolean =
    isKnownMetadataTag(rawText.lyricHeaderDetectionText())

fun LyricDisplaySettings.filterLyricTextBlock(content: String): List<String> {
    val visibleLines = content.lineSequence().filter { line ->
        line.normalizedLyricText().isNotEmpty()
    }.toList()
    if (!ignoreHeaderTags) return visibleLines

    val headerEndIndex = visibleLines.leadingLyricHeaderEndIndex { line -> line }
    val lyricLines = visibleLines.filterIndexed { index, line ->
        index > (headerEndIndex ?: -1) && !line.isStronglyHiddenLyricLine()
    }
    return when {
        lyricLines.isEmpty() -> emptyList()
        headerEndIndex != null -> listOf(LYRIC_HEADER_PLACEHOLDER) + lyricLines
        else -> lyricLines
    }
}

fun List<LyricLine>.filterLyricLinesForDisplay(settings: LyricDisplaySettings): List<LyricLine> {
    val visibleLines = filter { line ->
        line.primaryLyricText().normalizedLyricText().isNotEmpty()
    }
    if (!settings.ignoreHeaderTags) return visibleLines

    val headerEndIndex = visibleLines.leadingLyricHeaderEndIndex(LyricLine::primaryLyricText)
    val lyricLines = visibleLines.filterIndexed { index, line ->
        index > (headerEndIndex ?: -1) && !line.primaryLyricText().isStronglyHiddenLyricLine()
    }
    if (lyricLines.isEmpty()) return emptyList()
    if (headerEndIndex == null) return lyricLines

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

private enum class LyricMetadataMatch {
    Strong,
    HeaderOnly,
    None,
}

private fun LyricLine.primaryLyricText(): String =
    text.lineSequence().firstOrNull(String::isNotBlank).orEmpty()

private fun String.normalizedLyricText(): String = trim().removePrefix("\uFEFF").trimStart()

private fun String.lyricHeaderDetectionText(): String = KRC_WORD_TIMINGS.replace(
    LRC_WORD_TIMINGS.replace(
        LEADING_KRC_LINE_TIMING.replace(
            LEADING_LRC_TIMESTAMPS.replace(normalizedLyricText(), ""),
            "",
        ),
        "",
    ),
    "",
).trimStart()

private fun String.isStronglyHiddenLyricLine(): Boolean {
    val text = lyricHeaderDetectionText()
    return text.metadataMatch() == LyricMetadataMatch.Strong || isLyricHeaderSeparator(text)
}

private fun String.metadataMatch(): LyricMetadataMatch = when {
    isKnownMetadataTag(this) -> LyricMetadataMatch.Strong
    isStrongCreditLine(this) -> LyricMetadataMatch.Strong
    LYRIC_RIGHTS_NOTICE.matches(this) -> LyricMetadataMatch.Strong
    isHeaderOnlyCreditLine(this) -> LyricMetadataMatch.HeaderOnly
    else -> LyricMetadataMatch.None
}

private fun isKnownMetadataTag(text: String): Boolean =
    text.bracketedTagKey()?.lowercase() in KNOWN_METADATA_TAGS

private fun String.bracketedTagKey(): String? {
    if (length < 4 || first() != '[' || last() != ']') return null
    val separatorIndex = indexOfFirst { character -> character == ':' || character == '：' }
    if (separatorIndex <= 1) return null
    return substring(1, separatorIndex).trim().takeIf(String::isNotEmpty)
}

private fun isStrongCreditLine(text: String): Boolean {
    val field = text.creditFieldOrNull() ?: return false
    return field in STRONG_CREDIT_KEYS_ZH ||
        field.lowercase() in STRONG_CREDIT_KEYS_EN ||
        isCreditsRoleCombination(field)
}

private fun isHeaderOnlyCreditLine(text: String): Boolean {
    val field = text.creditFieldOrNull() ?: return false
    return field in HEADER_ONLY_CREDIT_KEYS_ZH || field.lowercase() in HEADER_ONLY_CREDIT_KEYS_EN
}

private fun String.creditFieldOrNull(): String? {
    val separatorIndex = indexOfFirst { character -> character == ':' || character == '：' }
    if (separatorIndex <= 0) return null
    return CREDIT_FIELD_WHITESPACE.replace(substring(0, separatorIndex).trim(), " ")
}

private fun isCreditsRoleCombination(field: String): Boolean {
    val compactField = field.filterNot(::isCreditRoleConnector)
    if (compactField.isEmpty()) return false

    var offset = 0
    var roleCount = 0
    while (offset < compactField.length) {
        val role = COMBINATION_ROLE_TOKENS.firstOrNull { token ->
            compactField.startsWith(token, startIndex = offset)
        } ?: return false
        offset += role.length
        roleCount += 1
    }
    return roleCount >= 2
}

private fun isCreditRoleConnector(character: Char): Boolean =
    character.isWhitespace() || character in CREDIT_ROLE_CONNECTORS

private fun isLyricHeaderSeparator(text: String): Boolean =
    text.isNotEmpty() && text.all { character -> character in HEADER_SEPARATOR_CHARACTERS }

private fun isHeaderContinuation(text: String): Boolean =
    text.metadataMatch() != LyricMetadataMatch.None ||
        isLyricHeaderSeparator(text) ||
        SHORT_CREDIT_LABEL.matches(text)

private inline fun <T> List<T>.leadingLyricHeaderEndIndex(textOf: (T) -> String): Int? {
    val scanEndExclusive = minOf(size, HEADER_SCAN_NON_BLANK_LIMIT)
    var unclassifiedLeadingLines = 0
    var firstMetadataIndex: Int? = null

    for (index in 0 until scanEndExclusive) {
        val text = textOf(this[index]).lyricHeaderDetectionText()
        if (isBackgroundLyricTag(text)) return null
        if (isLyricHeaderSeparator(text)) continue
        if (text.metadataMatch() != LyricMetadataMatch.None) {
            firstMetadataIndex = index
            break
        }
        unclassifiedLeadingLines += 1
        if (unclassifiedLeadingLines > HEADER_LEADING_CONTEXT_LINE_LIMIT) return null
    }

    val firstIndex = firstMetadataIndex ?: return null
    var headerEndIndex = firstIndex
    for (index in (firstIndex + 1) until scanEndExclusive) {
        val text = textOf(this[index]).lyricHeaderDetectionText()
        if (!isHeaderContinuation(text)) break
        headerEndIndex = index
    }
    return headerEndIndex
}

private const val HEADER_SCAN_NON_BLANK_LIMIT = 16
private const val HEADER_LEADING_CONTEXT_LINE_LIMIT = 1

private fun isBackgroundLyricTag(text: String): Boolean =
    text.bracketedTagKey()?.lowercase() in BACKGROUND_LYRIC_TAGS

private val LEADING_LRC_TIMESTAMPS = Regex(
    pattern = """^(?:\[\s*\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?\s*])+\s*""",
)

private val LEADING_KRC_LINE_TIMING = Regex(
    pattern = """^\[\s*-?\d+\s*,\s*-?\d+\s*]\s*""",
)

private val LRC_WORD_TIMINGS = Regex(
    pattern = """(?:<|\[)\s*\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?\s*(?:>|])""",
)

private val KRC_WORD_TIMINGS = Regex(
    pattern = """<\s*-?\d+\s*,\s*-?\d+\s*,\s*-?\d+\s*>""",
)

private val CREDIT_FIELD_WHITESPACE = Regex(pattern = """\s+""")

private val LYRIC_RIGHTS_NOTICE = Regex(
    pattern = """^[（(【\[].*(著作权|著作權|着作权|着作權|版权|版權|授权|授權|copyright|all rights reserved|licensed|permission).*[]）)】]$""",
    option = RegexOption.IGNORE_CASE,
)

private val SHORT_CREDIT_LABEL = Regex(pattern = """^.{1,24}[:：]\s*$""")

private val KNOWN_METADATA_TAGS = setOf(
    "al", "album", "ar", "artist", "au", "author", "by", "enc", "encoding", "hash", "id", "kana",
    "la", "language", "length", "offset", "provider", "qq", "re", "sign", "source", "src", "ti",
    "title", "tool", "total", "ve",
)

private val BACKGROUND_LYRIC_TAGS = setOf("bg", "x-bg")

private val STRONG_CREDIT_KEYS_ZH = setOf(
    "作词", "作詞", "填词", "填詞", "作曲", "编曲", "編曲", "词曲", "詞曲", "词", "詞", "曲",
    "演唱", "歌手", "原唱", "翻唱",
    "制作", "製作", "制作人", "製作人", "配唱制作人", "制作统筹", "制作統籌", "制作团队", "製作團隊",
    "制作助理", "製作助理", "监制", "監製", "音乐制作", "音樂製作", "音乐制作人", "音樂製作人",
    "音乐监制", "音樂監製", "音乐总监", "音樂總監",
    "录音", "錄音", "录音师", "錄音師", "录音工程师", "錄音工程師", "录音助理", "錄音助理",
    "录音棚", "錄音棚", "录音室", "錄音室",
    "混音", "混音师", "混音師", "混音工程师", "混音工程師", "混音助理",
    "母带", "母帶", "母带工程师", "母帶工程師", "母带处理", "母帶處理", "母带制作", "母帶製作",
    "母带工作室", "母帶工作室",
    "和声", "和聲", "和声编写", "和聲編寫", "和声演唱", "和聲演唱", "合声", "合聲", "合声编写",
    "合聲編寫", "伴唱",
    "出品", "出品人", "出品方", "出品公司", "发行", "發行", "发行方", "發行方", "发行公司", "發行公司",
    "联合出品", "聯合出品", "联合发行", "聯合發行",
    "弦乐编写", "弦樂編寫", "弦乐", "弦樂", "管弦乐", "管弦樂", "乐团", "樂團", "乐队", "樂隊",
    "吉他", "贝斯", "貝斯", "鼓", "键盘", "鍵盤", "钢琴", "鋼琴", "小提琴", "大提琴",
    "版权", "版權", "版权所有", "版權所有",
    "작사", "작곡", "편곡", "가수",
)

private val STRONG_CREDIT_KEYS_EN = setOf(
    "album", "arranged", "arranged by", "arrangement", "arranger", "artist", "backing vocal",
    "backing vocals", "background vocal", "background vocals", "bass", "chief producer", "co producer",
    "co-producer", "composed", "composed by", "composer", "composer by", "composition", "copyright",
    "distributed by", "distribution", "drum", "drums", "engineer", "executive producer", "guitar", "guitars",
    "keyboard", "keyboards", "lrc", "lyric source", "lyricist", "lyricist by", "lyric", "lyric by", "lyrics",
    "lyrics by", "mastered", "mastered at", "mastered by", "mastering", "mastering engineer", "mixed",
    "mixed at", "mixed by", "mixing", "mixing engineer", "music", "op", "orchestra", "performed", "piano",
    "presented by", "presenter", "produced", "produced by", "producer", "production company", "production team",
    "publisher", "recorded", "recorded at", "recorded by", "recording", "recording engineer", "recording studio",
    "release", "released by", "repertoire owner", "singer", "sp", "strings", "strings arrangement", "sung",
    "title", "vocal producer", "vocals", "vocals produced by", "vocals producer", "word", "words", "written",
    "written by",
)

private val HEADER_ONLY_CREDIT_KEYS_ZH = setOf(
    "策划", "策劃", "总策划", "總策劃", "企划", "企劃", "统筹", "統籌", "项目统筹", "項目統籌",
    "宣传", "宣傳", "宣推", "推广", "推廣", "推广策划", "推廣策劃", "营销推广", "營銷推廣",
    "文案", "艺术指导", "藝術指導", "音乐顾问", "音樂顧問", "鸣谢", "鳴謝", "特别鸣谢", "特別鳴謝",
    "视觉设计", "視覺設計", "封面设计", "封面設計", "插画", "插畫",
)

private val HEADER_ONLY_CREDIT_KEYS_EN = setOf("a&r")

private val COMBINATION_ROLE_TOKENS = setOf(
    "制作人", "製作人", "作词", "作詞", "填词", "填詞", "作曲", "编曲", "編曲",
    "管弦乐", "管弦樂", "小提琴", "大提琴", "弦乐", "弦樂", "吉他", "贝斯", "貝斯",
    "键盘", "鍵盤", "钢琴", "鋼琴", "录音", "錄音", "混音", "母带", "母帶",
    "和声", "和聲", "合声", "合聲", "伴唱", "鼓", "词", "詞", "曲",
).sortedByDescending(String::length)

private val CREDIT_ROLE_CONNECTORS = setOf('/', '／', '、', ',', '，', '&', '＆', '+', '＋')

private val HEADER_SEPARATOR_CHARACTERS = setOf(
    '/', '／', '\\', '|', '｜', '·', '•', '-', '—', '–', '_', '=', '*', '~', '～',
)
