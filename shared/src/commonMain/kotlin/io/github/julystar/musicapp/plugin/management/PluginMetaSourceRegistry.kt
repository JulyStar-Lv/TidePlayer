package io.github.julystar.musicapp.plugin.management

import io.github.julystar.musicapp.plugin.runtime.LyricoJsMetaSource
import io.github.julystar.musicapp.plugin.runtime.PluginResultParser
import io.github.julystar.musicapp.plugin.runtime.PluginRuntimeManager
import io.github.julystar.musicapp.source.api.MetaSource
import io.github.julystar.musicapp.source.api.MetaSourceRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Observes installed plugins and keeps [MetaSourceRegistry] synchronized without creating
 * QuickJS runtimes. Runtimes remain lazy and are created only when a source is invoked.
 */
class PluginMetaSourceRegistry(
    scope: CoroutineScope,
    private val repository: PluginRepository,
    private val runtimeManager: PluginRuntimeManager,
    private val resultParser: PluginResultParser,
    val registry: MetaSourceRegistry,
    private val builtInSources: List<MetaSource> = emptyList(),
) {
    private val synchronizeMutex = Mutex()
    private var previousPlugins: Map<String, PluginSummary> = emptyMap()

    private val observerJob: Job = scope.launch {
        repository.allPlugins().collect { plugins ->
            synchronize(plugins)
        }
    }

    suspend fun refresh() {
        synchronize(repository.allSnapshot())
    }

    suspend fun shutdown() {
        observerJob.cancelAndJoin()
        synchronizeMutex.withLock {
            previousPlugins.keys.forEach { pluginId ->
                resultParser.clearPlugin(pluginId)
            }
            runtimeManager.closeAll()
            registry.replace(builtInSources)
            previousPlugins = emptyMap()
        }
    }

    private suspend fun synchronize(plugins: List<PluginSummary>) = synchronizeMutex.withLock {
        val current = plugins.associateBy(PluginSummary::id)
        val invalidatedPluginIds = previousPlugins.mapNotNull { (pluginId, previous) ->
            val updated = current[pluginId]
            when {
                updated == null -> pluginId
                previous.enabled && !updated.enabled -> pluginId
                previous.versionCode != updated.versionCode -> pluginId
                previous.updatedAt != updated.updatedAt -> pluginId
                previous.entryFile != updated.entryFile -> pluginId
                previous.includeDirs != updated.includeDirs -> pluginId
                previous.apiVersion != updated.apiVersion -> pluginId
                previous.minHostApiVersion != updated.minHostApiVersion -> pluginId
                previous.capabilities != updated.capabilities -> pluginId
                else -> null
            }
        }.toSet()

        invalidatedPluginIds.forEach { pluginId ->
            runtimeManager.invalidate(pluginId)
            resultParser.clearPlugin(pluginId)
        }

        val pluginSources = plugins
            .asSequence()
            .filter(PluginSummary::enabled)
            .map { plugin ->
                LyricoJsMetaSource(
                    plugin = with(repository) { plugin.toInstalledPlugin() },
                    runtimeManager = runtimeManager,
                    configProvider = repository,
                    resultParser = resultParser,
                )
            }
            .toList()

        registry.replace(builtInSources + pluginSources)
        previousPlugins = current
    }
}
