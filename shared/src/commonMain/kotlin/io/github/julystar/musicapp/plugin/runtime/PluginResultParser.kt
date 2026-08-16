package io.github.julystar.musicapp.plugin.runtime

import io.github.julystar.musicapp.source.api.MetaCoverCandidate
import io.github.julystar.musicapp.source.api.MetaLyricLine
import io.github.julystar.musicapp.source.api.MetaLyricWord
import io.github.julystar.musicapp.source.api.MetaLyrics
import io.github.julystar.musicapp.source.api.MetaLyricsCandidate
import io.github.julystar.musicapp.source.api.MetaSongCandidate
import kotlin.math.abs
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

class PluginResultParser(
    private val contexts: PluginCandidateContextStore = PluginCandidateContextStore(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun songs(
        pluginId: String,
        raw: String,
        separator: String = "/",
    ): List<MetaSongCandidate> {
        val actualSeparator = separator.ifEmpty { "/" }
        return resultArray(
            root = parseRoot(raw),
            resultName = "song search",
            wrappers = arrayOf("items", "results", "songs", "data"),
        ).mapNotNull { value ->
            val obj = value as? JsonObject ?: return@mapNotNull null
            val id = obj.firstString("id", "songId", "trackId")?.trim().orEmpty()
            val title = obj.firstString("title", "name", "songName")?.trim().orEmpty()
            if (id.isEmpty() || title.isEmpty()) return@mapNotNull null
            val artist = when (val artistValue = obj.first("artist", "artists", "singer")) {
                is JsonArray -> artistValue
                    .mapNotNull { element -> element.primitiveString() }
                    .filter(String::isNotBlank)
                    .joinToString(actualSeparator)
                    .ifBlank { null }
                else -> artistValue?.primitiveString()?.takeIf(String::isNotBlank)
            }
            MetaSongCandidate(
                id = id,
                title = title,
                artist = artist,
                album = obj.firstString("album", "albumName"),
                durationMs = obj.firstLong("duration", "durationMs", "duration_ms"),
                date = obj.firstString("date", "year", "releaseDate", "release_date"),
                trackNumber = obj.firstString("trackNumber", "trackerNumber", "track_number"),
                pictureUrl = obj.firstString(
                    "picUrl",
                    "coverUrl",
                    "cover_url",
                    "artworkUrl",
                    "pictureUrl",
                ),
                fields = obj.fieldsMap(),
                contextToken = obj["internal"]
                    ?.takeUnless { it is JsonNull }
                    ?.let { contexts.put(pluginId, it) },
                sourceId = pluginId,
            )
        }
    }

    fun covers(pluginId: String, raw: String): List<MetaCoverCandidate> =
        covers(pluginId, apiVersion = 1, raw = raw)

    fun covers(
        pluginId: String,
        apiVersion: Int,
        raw: String,
    ): List<MetaCoverCandidate> = resultArray(
        root = parseRoot(raw),
        resultName = "cover search",
        wrappers = arrayOf("items", "results", "songs", "data", "covers"),
    ).mapIndexedNotNull { _, value ->
        when (value) {
            is JsonPrimitive -> if (apiVersion >= 4) {
                null
            } else {
                value.contentOrNull
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?.let { url -> MetaCoverCandidate(url = url, sourceId = pluginId) }
            }
            is JsonObject -> {
                val url = value.firstString(
                    "url",
                    "picUrl",
                    "coverUrl",
                    "cover_url",
                    "artworkUrl",
                    "pictureUrl",
                )?.trim()?.takeIf(String::isNotEmpty) ?: return@mapIndexedNotNull null
                val title = value.firstString("title", "name", "songName")?.trim()
                val artist = value.firstString("artist", "artists", "singer")?.trim()
                val album = value.firstString("album", "albumName")?.trim()
                val date = value.firstString("date", "year", "releaseDate", "release_date")?.trim()
                if (apiVersion >= 4 && listOf(title, artist, album, date).any { it.isNullOrBlank() }) {
                    return@mapIndexedNotNull null
                }
                MetaCoverCandidate(
                    url = url,
                    id = value.firstString("id", "songId", "trackId")
                        ?.trim()
                        ?.takeIf(String::isNotEmpty),
                    title = title,
                    artist = artist,
                    album = album,
                    date = date,
                    width = value.firstInt("width"),
                    height = value.firstInt("height"),
                    sourceId = pluginId,
                )
            }
            else -> null
        }
    }

    fun lyrics(raw: String): MetaLyrics? = parseLyricsV1ToV3(raw)

    fun lyricsCandidates(
        pluginId: String,
        apiVersion: Int,
        raw: String,
        fallbackSong: MetaSongCandidate,
    ): List<MetaLyricsCandidate> = if (apiVersion >= 4) {
        parseLyricsCandidatesV4(pluginId, raw)
    } else {
        parseLyricsV1ToV3(raw)?.let { lyrics ->
            listOf(
                MetaLyricsCandidate(
                    id = fallbackSong.id,
                    title = fallbackSong.title,
                    artist = fallbackSong.artist,
                    album = fallbackSong.album,
                    date = fallbackSong.date,
                    lyrics = lyrics,
                    sourceId = pluginId,
                ),
            )
        }.orEmpty()
    }

    fun parseLyricsV1ToV3(raw: String): MetaLyrics? = parseLyricsElement(parseRoot(raw))

    fun parseLyricsCandidatesV4(
        pluginId: String,
        raw: String,
    ): List<MetaLyricsCandidate> {
        val root = parseRoot(raw)
        if (root is JsonNull) return emptyList()
        val values = when (root) {
            is JsonArray -> root.toList()
            is JsonObject -> root.firstArray("items", "results", "candidates")?.toList()
                ?: listOf(root)
            else -> listOf(root)
        }
        return values.mapIndexedNotNull { index, value ->
            val obj = value as? JsonObject ?: return@mapIndexedNotNull null
            val tags = obj["tags"] as? JsonObject ?: return@mapIndexedNotNull null
            val title = tags.firstString("ti")?.trim()?.takeIf(String::isNotEmpty)
                ?: return@mapIndexedNotNull null
            val artist = tags.firstString("ar")?.trim()?.takeIf(String::isNotEmpty)
                ?: return@mapIndexedNotNull null
            val album = tags.firstString("al")?.trim()?.takeIf(String::isNotEmpty)
                ?: return@mapIndexedNotNull null
            val date = tags.firstString("date")?.trim()?.takeIf(String::isNotEmpty)
                ?: return@mapIndexedNotNull null
            val lyrics = parseLyricsElement(value) ?: return@mapIndexedNotNull null
            MetaLyricsCandidate(
                id = obj.firstString("id")?.trim()?.takeIf(String::isNotEmpty)
                    ?: "$pluginId:lyrics:$index",
                title = title,
                artist = artist,
                album = album,
                date = date,
                lyrics = lyrics,
                sourceId = pluginId,
            )
        }
    }

    suspend fun internal(pluginId: String, token: String?): JsonElement? =
        contexts.get(pluginId, token)

    suspend fun clearPlugin(pluginId: String) {
        contexts.clearPlugin(pluginId)
    }

    private fun parseLyricsElement(element: JsonElement): MetaLyrics? {
        if (element is JsonNull) return null
        if (element is JsonPrimitive) {
            return element.contentOrNull
                ?.takeIf(String::isNotBlank)
                ?.let { MetaLyrics(rawPlainLrc = it) }
        }
        val root = element as? JsonObject ?: return null
        if (root.firstBoolean("notFound") == true) return null
        val legacyLines = root["lines"] as? JsonArray
        if (legacyLines?.any { it is JsonObject } == true) {
            return parseLegacyLyrics(root, legacyLines)
        }
        return when (normalizeLyricsType(root.firstString("type") ?: "structured")) {
            "structured" -> parseStructuredLyrics(root)
            "rawPlainLrc" -> root.firstString(
                "rawPlainLrc",
                "raw_plain_lrc",
                "plainLrc",
                "plain_lrc",
                "lrc",
                "originalLrc",
                "original_lrc",
                "original",
                "content",
                "lyrics",
            )?.takeIf(String::isNotBlank)?.let { MetaLyrics(rawPlainLrc = it) }
            "rawVerbatimLrc" -> root.firstString(
                "rawVerbatimLrc",
                "raw_verbatim_lrc",
                "content",
                "lyrics",
            )?.takeIf(String::isNotBlank)?.let { MetaLyrics(rawVerbatimLrc = it) }
            "rawEnhancedLrc" -> root.firstString(
                "rawEnhancedLrc",
                "raw_enhanced_lrc",
                "content",
                "lyrics",
            )?.takeIf(String::isNotBlank)?.let { MetaLyrics(rawEnhancedLrc = it) }
            "rawTtml" -> root.firstString(
                "rawTtml",
                "raw_ttml",
                "ttml",
                "content",
                "lyrics",
            )?.takeIf(String::isNotBlank)?.let { MetaLyrics(rawTtml = it) }
            "rawMultiPersonEnhancedLrc" -> root.firstString(
                "rawMultiPersonEnhancedLrc",
                "raw_multi_person_enhanced_lrc",
                "content",
                "lyrics",
            )?.takeIf(String::isNotBlank)?.let { MetaLyrics(rawMultiPersonEnhancedLrc = it) }
            else -> parseUnknownRawLyrics(root)
        }
    }

    private fun parseStructuredLyrics(root: JsonObject): MetaLyrics? {
        val translated = timedTexts(root.firstArray("translated", "translation", "translations"))
        val romanized = timedTexts(root.firstArray("romanization", "romanized", "roma"))
        val original = root.firstArray("original", "lines") ?: JsonArray(emptyList())
        val lines = original.mapIndexedNotNull { index, element ->
            val tuple = element as? JsonArray ?: return@mapIndexedNotNull null
            val start = tuple.getOrNull(0)?.primitiveLong()
            val end = tuple.getOrNull(1)?.primitiveLong()
            val payload = tuple.getOrNull(2) ?: return@mapIndexedNotNull null
            val words = (payload as? JsonArray).orEmpty().mapNotNull { wordValue ->
                val word = wordValue as? JsonArray ?: return@mapNotNull null
                val text = word.getOrNull(2)?.primitiveString() ?: return@mapNotNull null
                MetaLyricWord(
                    text = text,
                    startMs = word.getOrNull(0)?.primitiveLong() ?: start,
                    endMs = word.getOrNull(1)?.primitiveLong() ?: end,
                )
            }
            val text = if (payload is JsonArray) {
                words.joinToString(separator = "", transform = MetaLyricWord::text)
            } else {
                payload.primitiveString().orEmpty()
            }
            if (text.isEmpty() && words.isEmpty()) return@mapIndexedNotNull null
            MetaLyricLine(
                text = text,
                startMs = start,
                endMs = end,
                words = words,
                translation = matchTimedText(translated, start, index),
                romanization = matchTimedText(romanized, start, index),
            )
        }
        if (lines.isEmpty()) return null
        return MetaLyrics(
            lines = lines,
            translated = (root["translated"] as? JsonPrimitive)?.contentOrNull,
            romanization = (root["romanization"] as? JsonPrimitive)?.contentOrNull,
        )
    }

    private fun parseLegacyLyrics(root: JsonObject, lineValues: JsonArray): MetaLyrics = MetaLyrics(
        lines = lineValues.mapNotNull { it as? JsonObject }.mapNotNull { line ->
            val text = line.firstString("text") ?: return@mapNotNull null
            MetaLyricLine(
                text = text,
                startMs = line.firstLong("startMs", "start"),
                endMs = line.firstLong("endMs", "end"),
                words = (line["words"] as? JsonArray).orEmpty()
                    .mapNotNull { it as? JsonObject }
                    .mapNotNull { word ->
                        word.firstString("text")?.let { wordText ->
                            MetaLyricWord(
                                text = wordText,
                                startMs = word.firstLong("startMs", "start"),
                                endMs = word.firstLong("endMs", "end"),
                            )
                        }
                    },
                translation = line.firstString("translation"),
                romanization = line.firstString("romanization"),
                person = line.firstString("person"),
            )
        },
        rawPlainLrc = root.firstString("rawPlainLrc", "raw_plain_lrc", "plainLrc", "plain_lrc", "lrc"),
        rawVerbatimLrc = root.firstString("rawVerbatimLrc", "raw_verbatim_lrc"),
        rawEnhancedLrc = root.firstString("rawEnhancedLrc", "raw_enhanced_lrc"),
        rawTtml = root.firstString("rawTtml", "raw_ttml", "ttml"),
        rawMultiPersonEnhancedLrc = root.firstString(
            "rawMultiPersonEnhancedLrc",
            "raw_multi_person_enhanced_lrc",
        ),
        translated = (root["translated"] as? JsonPrimitive)?.contentOrNull,
        romanization = (root["romanization"] as? JsonPrimitive)?.contentOrNull,
    )

    private fun parseUnknownRawLyrics(root: JsonObject): MetaLyrics? {
        val lyrics = MetaLyrics(
            rawPlainLrc = root.firstString(
                "rawPlainLrc",
                "raw_plain_lrc",
                "plainLrc",
                "plain_lrc",
                "lrc",
            ),
            rawVerbatimLrc = root.firstString("rawVerbatimLrc", "raw_verbatim_lrc"),
            rawEnhancedLrc = root.firstString("rawEnhancedLrc", "raw_enhanced_lrc"),
            rawTtml = root.firstString("rawTtml", "raw_ttml", "ttml"),
            rawMultiPersonEnhancedLrc = root.firstString(
                "rawMultiPersonEnhancedLrc",
                "raw_multi_person_enhanced_lrc",
            ),
        )
        return lyrics.takeIf {
            it.rawPlainLrc != null ||
                it.rawVerbatimLrc != null ||
                it.rawEnhancedLrc != null ||
                it.rawTtml != null ||
                it.rawMultiPersonEnhancedLrc != null
        }
    }

    private fun timedTexts(array: JsonArray?): List<TimedText> = array.orEmpty().mapNotNull { value ->
        val tuple = value as? JsonArray ?: return@mapNotNull null
        val text = tuple.getOrNull(2)?.primitiveString() ?: return@mapNotNull null
        TimedText(tuple.getOrNull(0)?.primitiveLong(), text)
    }

    private fun matchTimedText(
        values: List<TimedText>,
        startMs: Long?,
        index: Int,
    ): String? = values.firstOrNull { value ->
        value.startMs != null && value.startMs == startMs
    }?.text
        ?: values.getOrNull(index)?.text
        ?: startMs?.let { start ->
            values.filter { it.startMs != null }
                .minByOrNull { value -> abs(value.startMs!! - start) }
                ?.text
        }

    private fun parseRoot(raw: String): JsonElement {
        val normalized = raw.ifBlank { "null" }
        val first = try {
            json.parseToJsonElement(normalized)
        } catch (error: SerializationException) {
            if (normalized.trimStart().startsWith('{') || normalized.trimStart().startsWith('[')) {
                throw PluginResultParseException("Plugin returned invalid JSON", error)
            }
            JsonPrimitive(normalized)
        }
        if (first is JsonPrimitive && first.isString) {
            return try {
                json.parseToJsonElement(first.content)
            } catch (_: SerializationException) {
                first
            }
        }
        return first
    }

    private fun resultArray(
        root: JsonElement,
        resultName: String,
        wrappers: Array<out String>,
    ): JsonArray = when (root) {
        is JsonArray -> root
        is JsonObject -> wrappers.firstNotNullOfOrNull { key -> root[key] as? JsonArray }
            ?: throw PluginResultParseException(
                "$resultName result object must contain one of: ${wrappers.joinToString()}",
            )
        is JsonNull -> JsonArray(emptyList())
        else -> throw PluginResultParseException(
            "$resultName result must be a JSON array, null, or a supported wrapper object",
        )
    }

    private fun JsonObject.fieldsMap(): Map<String, String> =
        (this["fields"] as? JsonObject)
            ?.mapNotNull { (key, value) -> value.primitiveString()?.let { key to it } }
            ?.toMap()
            .orEmpty()

    private fun JsonObject.first(vararg keys: String): JsonElement? =
        keys.firstNotNullOfOrNull { key -> this[key] }

    private fun JsonObject.firstString(vararg keys: String): String? =
        first(*keys)?.primitiveString()

    private fun JsonObject.firstLong(vararg keys: String): Long? =
        first(*keys)?.primitiveLong()

    private fun JsonObject.firstInt(vararg keys: String): Int? =
        first(*keys)?.let { value -> (value as? JsonPrimitive)?.intOrNull }

    private fun JsonObject.firstBoolean(vararg keys: String): Boolean? =
        first(*keys)?.primitiveString()?.toBooleanStrictOrNull()

    private fun JsonObject.firstArray(vararg keys: String): JsonArray? =
        keys.firstNotNullOfOrNull { key -> this[key] as? JsonArray }

    private fun JsonElement.primitiveString(): String? = when (this) {
        is JsonPrimitive -> contentOrNull
        is JsonObject -> firstString("name", "title", "value")
        else -> null
    }

    private fun JsonElement.primitiveLong(): Long? =
        (this as? JsonPrimitive)?.longOrNull

    private fun normalizeLyricsType(value: String): String? = when (value.trim()) {
        "structured", "STRUCTURED" -> "structured"
        "rawPlainLrc", "raw_plain_lrc", "RAW_PLAIN_LRC", "plainLrc", "plain_lrc", "lrc" ->
            "rawPlainLrc"
        "rawVerbatimLrc", "raw_verbatim_lrc", "RAW_VERBATIM_LRC" -> "rawVerbatimLrc"
        "rawEnhancedLrc", "raw_enhanced_lrc", "RAW_ENHANCED_LRC" -> "rawEnhancedLrc"
        "rawTtml", "raw_ttml", "RAW_TTML", "ttml" -> "rawTtml"
        "rawMultiPersonEnhancedLrc", "raw_multi_person_enhanced_lrc", "RAW_MULTI_PERSON_ENHANCED_LRC" ->
            "rawMultiPersonEnhancedLrc"
        else -> null
    }

    private data class TimedText(val startMs: Long?, val text: String)
}
