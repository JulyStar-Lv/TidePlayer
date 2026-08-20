package io.github.julystar.musicapp.source.server

import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.source.api.RemoteServerMediaSource
import io.github.julystar.musicapp.source.api.RemoteServerMediaStream
import io.github.julystar.musicapp.source.api.RemoteServerTrack
import io.github.julystar.musicapp.source.api.RemoteServerTrackPage
import io.github.julystar.musicapp.source.api.RemoteServerUserData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

internal class EmbyTrackPager(
    private val accountId: SourceAccountId,
    private val userId: String,
    private val pageSize: Int,
    private val request: (path: String, params: Map<String, String>) -> String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun pages(query: String?): Flow<Result<RemoteServerTrackPage>> = flow {
        val limit = pageSize.coerceIn(1, 500)
        val seenIds = mutableSetOf<String>()
        val seenFullPageFingerprints = mutableSetOf<String>()
        var offset = 0
        try {
            while (true) {
                val params = buildMap {
                    put("Recursive", "true")
                    put("IncludeItemTypes", "Audio")
                    put("StartIndex", offset.toString())
                    put("Limit", limit.toString())
                    put("SortBy", "SortName")
                    put("SortOrder", "Ascending")
                    put(
                        "Fields",
                        "Genres,MediaSources,MediaStreams,AlbumArtist,UserData",
                    )
                    query?.takeIf(String::isNotBlank)?.let { put("SearchTerm", it) }
                }
                val root = json.parseToJsonElement(request("Users/$userId/Items", params)) as? JsonObject
                    ?: error("Emby response root is not an object")
                val itemsElement = root["Items"] ?: error("Emby response is missing Items")
                val items = itemsElement as? JsonArray
                    ?: error("Emby response Items is not an array")
                if (items.isEmpty()) break

                val fingerprint = items.toString()
                if (items.size >= limit && !seenFullPageFingerprints.add(fingerprint)) {
                    emit(Result.failure(IllegalStateException("Emby server repeated a page")))
                    return@flow
                }

                val tracks = items.mapNotNull { element ->
                    val item = element as? JsonObject ?: return@mapNotNull null
                    val id = item.string("Id") ?: return@mapNotNull null
                    if (!seenIds.add(id)) return@mapNotNull null
                    item.toRemoteTrack(accountId)
                }
                if (tracks.isNotEmpty()) {
                    emit(Result.success(RemoteServerTrackPage(tracks, offset)))
                }
                if (items.size < limit) break
                offset += items.size
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            emit(Result.failure(error))
        }
    }

    private fun JsonObject.toRemoteTrack(accountId: SourceAccountId): RemoteServerTrack {
        val mediaMapping = EmbyAudioPropertiesMapper.map(this)
        val artists = array("Artists")?.strings().orEmpty()
        val genres = array("Genres")?.strings().orEmpty()
        val albumArtist = string("AlbumArtist")
        val ticks = long("RunTimeTicks")
        return RemoteServerTrack(
            accountId = accountId,
            remoteId = string("Id") ?: error("Emby item ID is missing"),
            title = string("Name") ?: string("Id").orEmpty(),
            artist = artists.firstOrNull() ?: albumArtist,
            album = string("Album"),
            albumArtist = albumArtist,
            year = int("ProductionYear"),
            track = int("IndexNumber"),
            discNumber = int("ParentIndexNumber"),
            durationMs = ticks?.div(10_000L),
            mimeType = mediaMapping.mimeType ?: "audio/*",
            audioProperties = mediaMapping.properties,
            sourceMediaId = mediaMapping.sourceMediaId,
            artists = artists,
            genres = genres,
            albumId = string("AlbumId"),
            productionYear = int("ProductionYear"),
            indexNumber = int("IndexNumber"),
            parentIndexNumber = int("ParentIndexNumber"),
            runTimeTicks = ticks,
            mediaSources = array("MediaSources")?.objects().orEmpty().map { it.toMediaSource() },
            mediaStreams = array("MediaStreams")?.objects().orEmpty().map { it.toMediaStream() },
            userData = jsonObjectAt("UserData")?.toUserData(),
            imageTag = jsonObjectAt("ImageTags")?.string("Primary"),
        )
    }
}

private fun JsonObject.toMediaSource() = RemoteServerMediaSource(
    id = string("Id"),
    isDefault = boolean("IsDefault") ?: boolean("Default"),
    container = string("Container"),
    mimeType = string("MimeType"),
    bitrate = int("Bitrate") ?: int("BitRate"),
    size = long("Size"),
    defaultAudioStreamIndex = int("DefaultAudioStreamIndex"),
    mediaStreams = array("MediaStreams")?.objects().orEmpty().map { it.toMediaStream() },
    supportsDirectPlay = boolean("SupportsDirectPlay"),
    supportsDirectStream = boolean("SupportsDirectStream"),
    requiredHttpHeaders = jsonObjectAt("RequiredHttpHeaders")?.entries
        ?.mapNotNull { (key, value) -> (value as? JsonPrimitive)?.contentOrNull?.let { key to it } }
        ?.toMap()
        .orEmpty(),
)

private fun JsonObject.toMediaStream() = RemoteServerMediaStream(
    index = int("Index"),
    type = string("Type"),
    codec = string("Codec"),
    bitrate = int("BitRate"),
    sampleRate = int("SampleRate"),
    bitDepth = int("BitDepth"),
    channels = int("Channels"),
    channelLayout = string("ChannelLayout"),
    isDefault = boolean("IsDefault"),
)

private fun JsonObject.toUserData() = RemoteServerUserData(
    isFavorite = boolean("IsFavorite"),
    playCount = int("PlayCount"),
    lastPlayedDate = string("LastPlayedDate"),
    played = boolean("Played"),
)

private fun JsonObject.array(name: String): JsonArray? = get(name) as? JsonArray
private fun JsonObject.jsonObjectAt(name: String): JsonObject? = get(name) as? JsonObject
private fun JsonObject.primitive(name: String): JsonPrimitive? = get(name) as? JsonPrimitive
private fun JsonObject.string(name: String): String? = primitive(name)?.contentOrNull
    ?.trim()?.takeIf(String::isNotBlank)
private fun JsonObject.int(name: String): Int? = primitive(name)?.intOrNull
private fun JsonObject.long(name: String): Long? = primitive(name)?.longOrNull
private fun JsonObject.boolean(name: String): Boolean? = primitive(name)?.booleanOrNull
private fun JsonArray.objects(): List<JsonObject> = mapNotNull { it as? JsonObject }
private fun JsonArray.strings(): List<String> = mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    .map(String::trim).filter(String::isNotBlank).distinct()
