package io.github.julystar.musicapp.plugin.runtime

import io.github.julystar.musicapp.source.api.MetaCoverCandidate
import io.github.julystar.musicapp.source.api.MetaLyrics
import io.github.julystar.musicapp.source.api.MetaLyricsCandidate
import io.github.julystar.musicapp.source.api.MetaSongCandidate
import io.github.julystar.musicapp.source.api.MetaSongQuery
import io.github.julystar.musicapp.source.api.MetaSource
import io.github.julystar.musicapp.source.api.MetaSourceCapability
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class LyricoJsMetaSource(
    private val plugin: InstalledPlugin,
    private val runtimeManager: PluginRuntimeManager,
    private val configProvider: PluginConfigProvider,
    private val resultParser: PluginResultParser,
) : MetaSource {
    override val id: String = plugin.descriptor.pluginId
    override val displayName: String = plugin.descriptor.pluginName
    val apiVersion: Int = plugin.descriptor.apiVersion
    override val capabilities: Set<MetaSourceCapability> = plugin.capabilities.mapNotNullTo(
        mutableSetOf(),
    ) { capability ->
        when (capability) {
            "searchSongs" -> MetaSourceCapability.SEARCH_SONGS
            "getLyrics" -> MetaSourceCapability.GET_LYRICS
            "searchCovers" -> MetaSourceCapability.SEARCH_COVERS
            else -> null
        }
    }

    override suspend fun searchSongs(query: MetaSongQuery): List<MetaSongCandidate> =
        searchSongs(query, PluginLookupMode.MANUAL)

    suspend fun searchSongs(
        query: MetaSongQuery,
        mode: PluginLookupMode,
    ): List<MetaSongCandidate> {
        requireUsable("searchSongs", mode)
        val separator = query.separator.ifEmpty { "/" }
        val request = buildJsonObject {
            put("keyword", query.keyword ?: query.defaultKeyword())
            put("page", query.page.coerceAtLeast(1))
            put("pageSize", query.pageSize.coerceAtLeast(1))
            put("separator", separator)
            put("config", configJson(query.config))
        }
        return resultParser.songs(
            pluginId = id,
            raw = call("searchSongs", request),
            separator = separator,
        )
    }

    override suspend fun getLyrics(
        candidate: MetaSongCandidate,
        config: Map<String, String>,
    ): MetaLyrics? = getLyricsCandidates(
        candidate = candidate,
        config = config,
        mode = PluginLookupMode.MANUAL,
    ).firstOrNull()?.lyrics

    suspend fun getLyrics(
        candidate: MetaSongCandidate,
        config: Map<String, String>,
        mode: PluginLookupMode,
    ): MetaLyrics? = getLyricsCandidates(
        candidate = candidate,
        config = config,
        mode = mode,
    ).firstOrNull()?.lyrics

    override suspend fun getLyricsCandidates(
        candidate: MetaSongCandidate,
        page: Int,
        pageSize: Int,
        config: Map<String, String>,
    ): List<MetaLyricsCandidate> = getLyricsCandidates(
        candidate = candidate,
        page = page,
        pageSize = pageSize,
        config = config,
        mode = PluginLookupMode.MANUAL,
    )

    suspend fun getLyricsCandidates(
        candidate: MetaSongCandidate,
        page: Int = 1,
        pageSize: Int = 20,
        config: Map<String, String> = emptyMap(),
        mode: PluginLookupMode,
    ): List<MetaLyricsCandidate> {
        requireUsable("getLyrics", mode)
        val requestSong = songJson(candidate)
        val request = buildJsonObject {
            put("song", requestSong)
            if (plugin.descriptor.apiVersion >= 4) {
                put("page", page.coerceAtLeast(1))
                put("pageSize", pageSize.coerceAtLeast(1))
            }
            put("config", configJson(config))
        }
        return resultParser.lyricsCandidates(
            pluginId = id,
            apiVersion = plugin.descriptor.apiVersion,
            raw = call("getLyrics", request),
            fallbackSong = candidate,
        )
    }

    override suspend fun searchCovers(query: MetaSongQuery): List<MetaCoverCandidate> =
        searchCovers(query, PluginLookupMode.MANUAL)

    suspend fun searchCovers(
        query: MetaSongQuery,
        mode: PluginLookupMode,
    ): List<MetaCoverCandidate> {
        requireUsable("searchCovers", mode)
        val requestSong = if (plugin.descriptor.apiVersion >= 4) {
            query.song?.let { song ->
                val sourceSong = if (song.sourceId == null || song.sourceId == id) {
                    song
                } else {
                    song.copy(id = LOCAL_SONG_ID, contextToken = null, sourceId = id)
                }
                songJson(sourceSong)
            }
        } else {
            null
        }
        val request = buildJsonObject {
            put("keyword", query.keyword ?: query.defaultKeyword())
            put("pageSize", if (query.pageSize == 20) 5 else query.pageSize.coerceAtLeast(1))
            if (plugin.descriptor.apiVersion >= 4) {
                put("page", query.page.coerceAtLeast(1))
                requestSong?.let { put("song", it) }
            }
            put("config", configJson(query.config))
        }
        return resultParser.covers(
            pluginId = id,
            apiVersion = plugin.descriptor.apiVersion,
            raw = call("searchCovers", request),
        )
    }

    suspend fun clearPrivateContexts() {
        resultParser.clearPlugin(id)
    }

    private suspend fun call(
        name: String,
        request: JsonObject,
    ): String = try {
        runtimeManager.call(
            plugin = plugin.descriptor,
            functionName = name,
            requestJson = request.toString(),
        )
    } catch (error: PluginRuntimeError) {
        if (error.requiresRuntimeRebuild()) {
            resultParser.clearPlugin(id)
        }
        throw error
    }

    private suspend fun configJson(overrides: Map<String, String>): JsonObject = buildJsonObject {
        (configProvider.config(id) + overrides).forEach { (key, value) ->
            put(key, value)
        }
    }

    private suspend fun songJson(candidate: MetaSongCandidate): JsonObject {
        val internal = if (candidate.sourceId == null || candidate.sourceId == id) {
            resultParser.internal(id, candidate.contextToken)
        } else {
            null
        } ?: JsonObject(emptyMap())
        return buildJsonObject {
            put("id", candidate.id)
            put("title", candidate.title)
            candidate.artist?.let { put("artist", it) }
            candidate.album?.let { put("album", it) }
            candidate.date?.let { put("date", it) }
            candidate.durationMs?.let { put("duration", it) }
            put("sourceId", id)
            put("pluginId", id)
            put("fields", buildJsonObject {
                candidate.fields.forEach { (key, value) -> put(key, value) }
            })
            put("internal", internal)
        }
    }

    private fun requireUsable(
        capability: String,
        mode: PluginLookupMode,
    ) {
        if (!plugin.enabled) {
            throw PluginLookupDeniedException(id, mode, "Plugin $id is disabled")
        }
        val allowed = when (mode) {
            PluginLookupMode.MANUAL -> plugin.allowManualLookup
            PluginLookupMode.AUTOMATIC -> plugin.allowAutomaticLookup
            PluginLookupMode.BATCH -> plugin.allowBatchLookup
        }
        if (!allowed) {
            throw PluginLookupDeniedException(id, mode, "Plugin $id does not allow $mode lookup")
        }
        if (capability !in plugin.capabilities) {
            throw PluginLookupDeniedException(
                id,
                mode,
                "Plugin $id does not declare $capability",
            )
        }
    }

    private fun MetaSongQuery.defaultKeyword(): String = listOfNotNull(
        title.takeIf(String::isNotBlank),
        artist?.takeIf(String::isNotBlank),
        album?.takeIf(String::isNotBlank),
    ).joinToString(" ")

    private companion object {
        const val LOCAL_SONG_ID = "local-song"
    }
}
