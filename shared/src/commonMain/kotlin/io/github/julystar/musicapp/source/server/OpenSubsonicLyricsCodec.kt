package io.github.julystar.musicapp.source.server

import io.github.julystar.musicapp.source.api.OpenSubsonicCue
import io.github.julystar.musicapp.source.api.OpenSubsonicCueLine
import io.github.julystar.musicapp.source.api.OpenSubsonicLyricsAgent
import io.github.julystar.musicapp.source.api.OpenSubsonicLyricsTrack
import io.github.julystar.musicapp.source.api.OpenSubsonicLyricsTrackKind
import io.github.julystar.musicapp.source.api.OpenSubsonicLyricsLine
import io.github.julystar.musicapp.source.api.OpenSubsonicStructuredLyricsDocument
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

private val LYRICS_JSON = Json { ignoreUnknownKeys = true }

internal object OpenSubsonicLyricsCodec {
    fun encode(document: OpenSubsonicStructuredLyricsDocument): String = document.toJson().toString()

    fun decode(raw: String): OpenSubsonicStructuredLyricsDocument? = runCatching {
        fromJson(LYRICS_JSON.parseToJsonElement(raw).jsonObject)
    }.getOrNull()

    fun decode(element: JsonElement): OpenSubsonicStructuredLyricsDocument? = runCatching {
        fromJson(element.jsonObject)
    }.getOrNull()

    private fun OpenSubsonicStructuredLyricsDocument.toJson() = buildJsonObject {
        put("tracks", buildJsonArray { tracks.forEach { add(it.toJson()) } })
    }

    private fun OpenSubsonicLyricsAgent.toJson() = buildJsonObject {
        put("id", id); put("name", name); put("role", role)
    }

    private fun OpenSubsonicLyricsTrack.toJson() = buildJsonObject {
        put("kind", kind.name.lowercase())
        put("displayArtist", displayArtist); put("displayTitle", displayTitle)
        put("lang", language); put("offset", offsetMs); put("synced", synced)
        put("line", buildJsonArray { lines.forEach { add(it.toJson()) } })
        put("agents", buildJsonArray { agents.forEach { add(it.toJson()) } })
        put("cueLine", buildJsonArray { cueLines.forEach { add(it.toJson()) } })
    }

    private fun OpenSubsonicLyricsLine.toJson() = buildJsonObject {
        put("start", startMs); put("value", value)
    }

    private fun OpenSubsonicCueLine.toJson() = buildJsonObject {
        put("index", index); put("start", startMs); put("end", endMs); put("value", value)
        put("agentId", agentId)
        put("cue", buildJsonArray { cues.forEach { add(it.toJson()) } })
    }

    private fun OpenSubsonicCue.toJson() = buildJsonObject {
        put("start", startMs); put("end", endMs); put("value", value)
        put("byteStart", byteStart); put("byteEnd", byteEnd)
    }

    private fun fromJson(root: JsonObject) = OpenSubsonicStructuredLyricsDocument(
        tracks = root.array("tracks").mapNotNull { track(it) },
    )

    private fun agent(element: JsonElement): OpenSubsonicLyricsAgent? {
        val value = element.jsonObject
        return value.string("id")?.let { OpenSubsonicLyricsAgent(it, value.string("name"), value.string("role")) }
    }

    private fun track(element: JsonElement): OpenSubsonicLyricsTrack? {
        val value = element.jsonObject
        val kind = when (value.string("kind")?.lowercase()) {
            "translation" -> OpenSubsonicLyricsTrackKind.Translation
            "pronunciation", "romanization" -> OpenSubsonicLyricsTrackKind.Pronunciation
            "main", "original", null -> OpenSubsonicLyricsTrackKind.Main
            else -> return null
        }
        return OpenSubsonicLyricsTrack(
            kind, value.string("displayArtist"), value.string("displayTitle"), value.string("lang", "language"),
            value.long("offset", "offsetMs"), value.boolean("synced"),
            value.array("line", "lines").mapNotNull { line(it) },
            value.array("agents").mapNotNull { agent(it) },
            value.array("cueLine", "cueLines").mapNotNull { cueLine(it) },
        )
    }

    private fun line(element: JsonElement): OpenSubsonicLyricsLine? {
        val value = element.jsonObject
        return OpenSubsonicLyricsLine(value.long("start", "startMs"), value.string("value") ?: return null)
    }

    private fun cueLine(element: JsonElement): OpenSubsonicCueLine? {
        val value = element.jsonObject
        return OpenSubsonicCueLine(
            index = value.int("index") ?: return null, startMs = value.long("start", "startMs"),
            endMs = value.long("end", "endMs"), value = value.string("value") ?: return null,
            agentId = value.string("agentId"),
            cues = value.array("cue", "cues").mapNotNull { cue(it) },
        )
    }

    private fun cue(element: JsonElement): OpenSubsonicCue? {
        val value = element.jsonObject
        return OpenSubsonicCue(
            value.long("start", "startMs"), value.long("end", "endMs"), value.string("value") ?: return null,
            value.int("byteStart"), value.int("byteEnd"),
        )
    }

    private fun JsonObject.array(vararg names: String): JsonArray =
        names.firstNotNullOfOrNull { this[it] as? JsonArray } ?: JsonArray(emptyList())
    private fun JsonObject.string(vararg names: String): String? = names.firstNotNullOfOrNull { this[it]?.jsonPrimitive?.contentOrNull }
    private fun JsonObject.long(vararg names: String): Long? = names.firstNotNullOfOrNull { this[it]?.jsonPrimitive?.longOrNull }
    private fun JsonObject.int(name: String): Int? = this[name]?.jsonPrimitive?.intOrNull
    private fun JsonObject.boolean(name: String): Boolean? = this[name]?.jsonPrimitive?.booleanOrNull
}
