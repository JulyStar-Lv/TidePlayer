package io.github.julystar.musicapp.plugin.management

import io.github.julystar.musicapp.plugin.runtime.LyricoJsMetaSource
import io.github.julystar.musicapp.plugin.runtime.PluginLookupMode
import io.github.julystar.musicapp.source.api.MetaCoverCandidate
import io.github.julystar.musicapp.source.api.MetaLyrics
import io.github.julystar.musicapp.source.api.MetaLyricsCandidate
import io.github.julystar.musicapp.source.api.MetaSongCandidate
import io.github.julystar.musicapp.source.api.MetaSongQuery
import io.github.julystar.musicapp.source.api.MetaSource
import io.github.julystar.musicapp.source.api.MetaSourceCapability
import io.github.julystar.musicapp.source.api.MetaSourceRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout

enum class MetadataLookupOperation {
    SEARCH_SONGS,
    GET_LYRICS,
    SEARCH_COVERS,
}

data class MetadataLookupFailure(
    val sourceId: String,
    val operation: MetadataLookupOperation,
    val message: String,
    val errorType: String,
)

data class MetadataLookupCollection<T>(
    val items: List<T>,
    val failures: List<MetadataLookupFailure> = emptyList(),
    val queriedSourceCount: Int = 0,
)

data class MetadataLookupValue<T>(
    val value: T?,
    val failures: List<MetadataLookupFailure> = emptyList(),
)

/**
 * Production metadata lookup entry point. Each source failure is isolated so automatic and
 * batch scans can continue with remaining sources and preserve existing local metadata.
 */
class MetadataLookupUseCase(
    private val registry: MetaSourceRegistry,
    private val pluginRepository: PluginRepository,
    private val manualOperationTimeoutMs: Long = 30_000,
) {
    suspend fun searchSongs(
        query: MetaSongQuery,
        mode: PluginLookupMode,
        sourceIds: Set<String>? = null,
    ): MetadataLookupCollection<MetaSongCandidate> = withinModeTimeout(mode) {
        val sources = selectedSources(sourceIds, MetaSourceCapability.SEARCH_SONGS)
        val candidates = mutableListOf<MetaSongCandidate>()
        val failures = mutableListOf<MetadataLookupFailure>()
        sources.forEach { source ->
            try {
                val sourceCandidates = when (source) {
                    is LyricoJsMetaSource -> source.searchSongs(query, mode)
                    else -> source.searchSongs(query)
                }
                candidates += sourceCandidates.take(query.pageSize.coerceAtLeast(1)).map { candidate ->
                    candidate.copy(sourceId = source.id)
                }
                clearPluginError(source)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                recordPluginError(source, error)
                failures += error.toFailure(source.id, MetadataLookupOperation.SEARCH_SONGS)
            }
        }
        MetadataLookupCollection(
            items = candidates,
            failures = failures,
            queriedSourceCount = sources.size,
        )
    }

    suspend fun getLyrics(
        candidate: MetaSongCandidate,
        mode: PluginLookupMode,
        config: Map<String, String> = emptyMap(),
    ): MetadataLookupValue<MetaLyrics> {
        val result = getLyricsCandidates(
            candidate = candidate,
            mode = mode,
            config = config,
        )
        return MetadataLookupValue(
            value = result.value?.firstOrNull()?.lyrics,
            failures = result.failures,
        )
    }

    suspend fun getLyricsCandidates(
        candidate: MetaSongCandidate,
        mode: PluginLookupMode,
        page: Int = 1,
        pageSize: Int = 20,
        config: Map<String, String> = emptyMap(),
    ): MetadataLookupValue<List<MetaLyricsCandidate>> = withinModeTimeout(mode) {
        val sourceId = candidate.sourceId
            ?: return@withinModeTimeout MetadataLookupValue(
                value = null,
                failures = listOf(
                    MetadataLookupFailure(
                        sourceId = "unknown",
                        operation = MetadataLookupOperation.GET_LYRICS,
                        message = "Metadata candidate does not identify its source",
                        errorType = "MissingSourceId",
                    ),
                ),
            )
        val source = registry.sourceOrNull(sourceId)
            ?: return@withinModeTimeout MetadataLookupValue(
                value = null,
                failures = listOf(
                    MetadataLookupFailure(
                        sourceId = sourceId,
                        operation = MetadataLookupOperation.GET_LYRICS,
                        message = "Metadata source is no longer available",
                        errorType = "SourceUnavailable",
                    ),
                ),
            )

        if (MetaSourceCapability.GET_LYRICS !in source.capabilities) {
            return@withinModeTimeout MetadataLookupValue(value = emptyList())
        }

        try {
            val candidates = when (source) {
                is LyricoJsMetaSource -> source.getLyricsCandidates(
                    candidate = candidate,
                    page = page,
                    pageSize = pageSize,
                    config = config,
                    mode = mode,
                )
                else -> source.getLyricsCandidates(candidate, page, pageSize, config)
            }
            clearPluginError(source)
            MetadataLookupValue(candidates)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            recordPluginError(source, error)
            MetadataLookupValue(
                value = null,
                failures = listOf(error.toFailure(source.id, MetadataLookupOperation.GET_LYRICS)),
            )
        }
    }

    suspend fun searchIndependentLyricsCandidates(
        query: MetaSongQuery,
        mode: PluginLookupMode,
        sourceIds: Set<String>? = null,
    ): MetadataLookupCollection<MetaLyricsCandidate> = withinModeTimeout(mode) {
        val sources = selectedSources(sourceIds, MetaSourceCapability.GET_LYRICS)
            .filter { source ->
                MetaSourceCapability.SEARCH_SONGS !in source.capabilities &&
                    (source !is LyricoJsMetaSource || source.apiVersion >= 4)
            }
        val candidates = mutableListOf<MetaLyricsCandidate>()
        val failures = mutableListOf<MetadataLookupFailure>()
        sources.forEach { source ->
            val localSong = MetaSongCandidate(
                id = LOCAL_SONG_ID,
                title = query.title,
                artist = query.artist,
                album = query.album,
                date = query.date,
                durationMs = query.durationMs,
                fields = emptyMap(),
                contextToken = null,
                sourceId = source.id,
            )
            try {
                val sourceCandidates = when (source) {
                    is LyricoJsMetaSource -> source.getLyricsCandidates(
                        candidate = localSong,
                        page = query.page,
                        pageSize = query.pageSize,
                        config = query.config,
                        mode = mode,
                    )
                    else -> source.getLyricsCandidates(
                        candidate = localSong,
                        page = query.page,
                        pageSize = query.pageSize,
                        config = query.config,
                    )
                }
                candidates += sourceCandidates.map { it.copy(sourceId = source.id) }
                clearPluginError(source)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                recordPluginError(source, error)
                failures += error.toFailure(source.id, MetadataLookupOperation.GET_LYRICS)
            }
        }
        MetadataLookupCollection(
            items = candidates,
            failures = failures,
            queriedSourceCount = sources.size,
        )
    }

    suspend fun searchCovers(
        query: MetaSongQuery,
        mode: PluginLookupMode,
        sourceIds: Set<String>? = null,
    ): MetadataLookupCollection<MetaCoverCandidate> = withinModeTimeout(mode) {
        val sources = selectedSources(sourceIds, MetaSourceCapability.SEARCH_COVERS)
        val covers = mutableListOf<MetaCoverCandidate>()
        val failures = mutableListOf<MetadataLookupFailure>()
        sources.forEach { source ->
            try {
                val sourceCovers = when (source) {
                    is LyricoJsMetaSource -> source.searchCovers(query, mode)
                    else -> source.searchCovers(query)
                }
                covers += sourceCovers.map { cover -> cover.copy(sourceId = source.id) }
                clearPluginError(source)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                recordPluginError(source, error)
                failures += error.toFailure(source.id, MetadataLookupOperation.SEARCH_COVERS)
            }
        }
        MetadataLookupCollection(
            items = covers,
            failures = failures,
            queriedSourceCount = sources.size,
        )
    }

    private fun selectedSources(
        sourceIds: Set<String>?,
        capability: MetaSourceCapability,
    ): List<MetaSource> =
        registry.sources.filter { candidate ->
            capability in candidate.capabilities &&
                (sourceIds == null || candidate.id in sourceIds)
        }

    private suspend fun recordPluginError(
        source: MetaSource,
        error: Throwable,
    ) {
        if (source is LyricoJsMetaSource) {
            pluginRepository.recordError(source.id, error)
        }
    }

    private suspend fun clearPluginError(source: MetaSource) {
        if (source is LyricoJsMetaSource) {
            pluginRepository.clearError(source.id)
        }
    }

    private suspend fun <T> withinModeTimeout(
        mode: PluginLookupMode,
        block: suspend () -> T,
    ): T = if (mode == PluginLookupMode.MANUAL) {
        withTimeout(manualOperationTimeoutMs.coerceAtLeast(1)) { block() }
    } else {
        block()
    }

    private fun Throwable.toFailure(
        sourceId: String,
        operation: MetadataLookupOperation,
    ): MetadataLookupFailure = MetadataLookupFailure(
        sourceId = sourceId,
        operation = operation,
        message = message?.take(2_000).orEmpty().ifBlank { "Plugin metadata lookup failed" },
        errorType = this::class.simpleName ?: "UnknownError",
    )

    private companion object {
        const val LOCAL_SONG_ID = "local-song"
    }
}
