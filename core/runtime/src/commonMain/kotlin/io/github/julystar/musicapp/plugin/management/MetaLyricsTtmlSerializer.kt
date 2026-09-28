package io.github.julystar.musicapp.plugin.management

import io.github.julystar.musicapp.source.api.MetaLyricMetadata
import io.github.julystar.musicapp.source.api.MetaLyricWord
import io.github.julystar.musicapp.source.api.MetaLyrics

/** Keeps API 5 information in the persisted TTML rather than flattening it to LRC. */
internal fun MetaLyrics.toExtendedTtmlOrNull(): String? {
    val extended = agents.isNotEmpty() || metadata.isNotEmpty() || timing != null || language != null ||
        bodyDur != null || translatedLang != null || romanizationLang != null ||
        lines.any { it.extensions.isNotEmpty() || it.romanizationWords.isNotEmpty() || it.words.any { word -> word.ruby.isNotEmpty() } }
    if (!extended || lines.isEmpty()) return null
    return buildString {
        append("<tt xmlns=\"http://www.w3.org/ns/ttml\" xmlns:ttm=\"http://www.w3.org/ns/ttml#metadata\" xmlns:tts=\"http://www.w3.org/ns/ttml#styling\" xmlns:itunes=\"http://music.apple.com/lyric-ttml-internal\"")
        attribute("itunes:timing", timing)
        attribute("xml:lang", language)
        append("><head><metadata>")
        tags.forEach { (key, value) ->
            if (key in setOf("ti", "ar", "al")) {
                val name = when (key) { "ti" -> "title"; "ar" -> "creator"; else -> "desc" }
                append("<ttm:$name>${value.xml()}</ttm:$name>")
            }
        }
        agents.forEach { agent ->
            append("<ttm:agent")
            attribute("xml:id", agent.id)
            attribute("type", agent.type)
            append('>')
            agent.name?.let { append("<ttm:name>${it.xml()}</ttm:name>") }
            append("</ttm:agent>")
        }
        metadata.filter { it.name != "songwriters" }.forEach { metadataNode(it) }
        append("</metadata>")
        val songwriters = metadata.filter { it.name == "songwriters" }
        val romanized = lines.withIndex().filter { !it.value.romanization.isNullOrBlank() }
        if (songwriters.isNotEmpty() || romanized.isNotEmpty()) {
            append("<iTunesMetadata xmlns=\"http://music.apple.com/lyric-ttml-internal\">")
            songwriters.forEach { metadataNode(it) }
            if (romanized.isNotEmpty()) {
                append("<transliterations><transliteration")
                attribute("xml:lang", romanizationLang)
                append('>')
                romanized.forEach { (index, line) ->
                    append("<text for=\"L${index + 1}\">")
                    if (line.romanizationWords.isEmpty()) append(line.romanization.orEmpty().xml())
                    else line.romanizationWords.forEachIndexed { wordIndex, word ->
                        if (wordIndex > 0) append(' ')
                        wordSpan(word)
                    }
                    append("</text>")
                }
                append("</transliteration></transliterations>")
            }
            append("</iTunesMetadata>")
        }
        append("</head><body")
        attribute("dur", bodyDur)
        append("><div>")
        var paragraph: List<String?>? = null
        lines.forEachIndexed { index, line ->
            val part = line.extensions["itunes:song-part"] ?: line.extensions["itunes:songPart"]
            val group = listOf(part, line.extensions["divBegin"], line.extensions["divEnd"])
            if (group.any { it != null } && group != paragraph) {
                if (paragraph != null) append("</div>")
                append("<div")
                attribute("itunes:song-part", part)
                attribute("begin", group[1]?.toLongOrNull()?.let { it.ttmlTime() })
                attribute("end", group[2]?.toLongOrNull()?.let { it.ttmlTime() })
                append('>')
                paragraph = group
            }
            append("<p")
            attribute("begin", (line.startMs ?: line.words.firstOrNull()?.startMs ?: 0).ttmlTime())
            attribute("end", (line.endMs ?: line.words.lastOrNull()?.endMs ?: lines.getOrNull(index + 1)?.startMs ?: line.startMs ?: 0).ttmlTime())
            attribute("itunes:key", "L${index + 1}")
            line.extensions.filterKeys {
                it !in setOf("divBegin", "divEnd", "itunes:song-part", "itunes:songPart", "itunes:key", "begin", "end") &&
                    XML_ATTRIBUTE.matches(it)
            }.forEach { (name, value) -> attribute(name, value) }
            append('>')
            if (line.words.isEmpty()) append(line.text.xml()) else line.words.forEach { word -> wordSpan(word) }
            line.translation?.let {
                append("<span ttm:role=\"x-translation\"")
                attribute("xml:lang", translatedLang)
                append(">${it.xml()}</span>")
            }
            append("</p>")
        }
        if (paragraph != null) append("</div>")
        append("</div></body></tt>")
    }
}

private val XML_ATTRIBUTE = Regex("(?:[A-Za-z_][A-Za-z0-9_.-]*|(?:ttm|itunes):[A-Za-z_][A-Za-z0-9_.-]*)")

private fun StringBuilder.wordSpan(word: MetaLyricWord) {
    append("<span")
    attribute("begin", word.startMs?.let { it.ttmlTime() })
    attribute("end", word.endMs?.let { it.ttmlTime() })
    if (word.ruby.isEmpty()) append(">${word.text.xml()}</span>")
    else {
        append(" tts:ruby=\"container\"><span tts:ruby=\"base\">${word.text.xml()}</span>")
        normalizedRuby(word).forEach { ruby ->
            append("<span tts:ruby=\"text\"")
            attribute("begin", ruby.startMs?.let { it.ttmlTime() })
            attribute("end", ruby.endMs?.let { it.ttmlTime() })
            append(">${ruby.text.xml()}</span>")
        }
        append("</span>")
    }
}

private fun normalizedRuby(word: MetaLyricWord): List<MetaLyricWord> {
    val ruby = word.ruby.toMutableList()
    var index = 0
    while (index < ruby.size) {
        if (ruby[index].startMs == null && ruby[index].endMs == null) {
            val first = index
            while (index < ruby.size && ruby[index].startMs == null && ruby[index].endMs == null) index++
            val start = ruby.getOrNull(first - 1)?.endMs ?: word.startMs
            val end = ruby.getOrNull(index)?.startMs ?: word.endMs
            if (start != null && end != null) {
                val count = index - first
                for (offset in 0 until count) {
                    ruby[first + offset] = ruby[first + offset].copy(
                        startMs = start + (end - start) * offset / count,
                        endMs = start + (end - start) * (offset + 1) / count,
                    )
                }
            }
        } else index++
    }
    return ruby.mapIndexed { i, syllable ->
        syllable.copy(
            startMs = syllable.startMs ?: ruby.getOrNull(i - 1)?.endMs ?: word.startMs,
            endMs = syllable.endMs ?: ruby.getOrNull(i + 1)?.startMs ?: word.endMs,
        )
    }
}

private fun StringBuilder.metadataNode(node: MetaLyricMetadata) {
    append('<').append(node.name)
    node.namespace?.let { namespace ->
        attribute(if (':' in node.name) "xmlns:${node.name.substringBefore(':')}" else "xmlns", namespace)
    }
    node.attributes.forEach { (name, value) -> attribute(name, value) }
    append('>').append(node.text.orEmpty().xml())
    node.children.forEach { metadataNode(it) }
    append("</").append(node.name).append('>')
}

private fun StringBuilder.attribute(name: String, value: String?) {
    if (value != null) append(' ').append(name).append("=\"").append(value.xml()).append('"')
}

private fun String.xml(): String = replace("&", "&amp;").replace("<", "&lt;")
    .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

private fun Long.ttmlTime(): String {
    val ms = coerceAtLeast(0)
    return "${(ms / 3_600_000).toString().padStart(2, '0')}:${(ms / 60_000 % 60).toString().padStart(2, '0')}:${(ms / 1_000 % 60).toString().padStart(2, '0')}.${(ms % 1_000).toString().padStart(3, '0')}"
}
