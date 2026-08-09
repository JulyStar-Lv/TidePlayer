package io.github.julystar.musicapp.di

import io.github.julystar.musicapp.database.PluginEntity
import io.github.julystar.musicapp.plugin.install.FakePluginDao
import io.github.julystar.musicapp.plugin.management.PluginMetaSourceRegistry
import io.github.julystar.musicapp.plugin.management.PluginRepository
import io.github.julystar.musicapp.plugin.runtime.PluginCandidateContextStore
import io.github.julystar.musicapp.plugin.runtime.PluginResultParser
import io.github.julystar.musicapp.plugin.runtime.PluginRuntimeFactory
import io.github.julystar.musicapp.plugin.runtime.PluginRuntimeManager
import io.github.julystar.musicapp.plugin.runtime.PluginRuntimeSettings
import io.github.julystar.musicapp.plugin.runtime.PluginScriptBundleBuilder
import io.github.julystar.musicapp.source.api.MetaSourceRegistry
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertNotNull

class AppInitializerTest {
    @Test
    fun pluginSourcesAreRegisteredBeforeInitializationReturns() = runTest {
        val dao = FakePluginDao()
        dao.upsert(pluginEntity())
        val sourceRegistry = MetaSourceRegistry()
        val runtimeManager = PluginRuntimeManager(
            factory = PluginRuntimeFactory(
                PluginRuntimeSettings(
                    appVersionName = "test",
                    cacheDirectory = "/tmp/musicapp-app-initializer-test",
                ),
            ),
            bundleBuilder = PluginScriptBundleBuilder(),
        )
        val pluginRegistry = PluginMetaSourceRegistry(
            scope = this,
            repository = PluginRepository(dao, "/plugins".toPath()),
            runtimeManager = runtimeManager,
            resultParser = PluginResultParser(PluginCandidateContextStore()),
            registry = sourceRegistry,
        )
        val koinApplication = koinApplication {
            modules(module { single { pluginRegistry } })
        }

        try {
            AppInitializer.initializePluginSources(koinApplication.koin, emptySet())

            assertNotNull(sourceRegistry.sourceOrNull(PLUGIN_ID))
        } finally {
            pluginRegistry.shutdown()
            koinApplication.close()
        }
    }

    private fun pluginEntity(): PluginEntity = PluginEntity(
        id = 1,
        pluginId = PLUGIN_ID,
        name = "Example Metadata",
        versionCode = 1,
        versionName = "1.0.0",
        author = "Test",
        description = "Test plugin",
        apiVersion = 1,
        minHostApiVersion = 1,
        entryFile = "source.js",
        includeDirsJson = "[]",
        iconPath = null,
        capabilitiesJson = "[\"searchSongs\",\"getLyrics\"]",
        manifestRawJson = "{}",
        installedAt = 1,
        updatedAt = 1,
        enabled = true,
        allowManualLookup = true,
        allowAutomaticLookup = true,
        allowBatchLookup = false,
        lastError = null,
        lastErrorAt = null,
    )

    private companion object {
        const val PLUGIN_ID = "com.example.metadata"
    }
}
