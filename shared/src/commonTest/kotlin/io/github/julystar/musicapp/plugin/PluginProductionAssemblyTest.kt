package io.github.julystar.musicapp.plugin

import io.github.julystar.musicapp.database.PluginEntity
import io.github.julystar.musicapp.plugin.install.FakePluginDao
import io.github.julystar.musicapp.plugin.management.MetadataLookupUseCase
import io.github.julystar.musicapp.plugin.management.PluginMetaSourceRegistry
import io.github.julystar.musicapp.plugin.management.PluginRepository
import io.github.julystar.musicapp.plugin.runtime.LyricoJsMetaSource
import io.github.julystar.musicapp.plugin.runtime.PluginCandidateContextStore
import io.github.julystar.musicapp.plugin.runtime.PluginLookupDeniedException
import io.github.julystar.musicapp.plugin.runtime.PluginLookupMode
import io.github.julystar.musicapp.plugin.runtime.PluginResultParser
import io.github.julystar.musicapp.plugin.runtime.PluginRuntimeFactory
import io.github.julystar.musicapp.plugin.runtime.PluginRuntimeManager
import io.github.julystar.musicapp.plugin.runtime.PluginRuntimeSettings
import io.github.julystar.musicapp.plugin.runtime.PluginScriptBundleBuilder
import io.github.julystar.musicapp.source.api.MetaCoverCandidate
import io.github.julystar.musicapp.source.api.MetaLyrics
import io.github.julystar.musicapp.source.api.MetaSongCandidate
import io.github.julystar.musicapp.source.api.MetaSongQuery
import io.github.julystar.musicapp.source.api.MetaSource
import io.github.julystar.musicapp.source.api.MetaSourceRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath

@OptIn(ExperimentalCoroutinesApi::class)
class PluginProductionAssemblyTest {
    @Test
    fun databasePluginMapsToRegistryAndDisableRemovesIt() = runTest {
        val dao = FakePluginDao()
        dao.upsert(pluginEntity(enabled = true))
        val repository = PluginRepository(dao, "/plugins".toPath())
        val runtimeManager = runtimeManager()
        val parser = PluginResultParser(PluginCandidateContextStore())
        val metaRegistry = MetaSourceRegistry()
        val productionRegistry = PluginMetaSourceRegistry(
            scope = this,
            repository = repository,
            runtimeManager = runtimeManager,
            resultParser = parser,
            registry = metaRegistry,
        )

        productionRegistry.refresh()
        val source = metaRegistry.sourceOrNull("com.example.metadata")
        assertIs<LyricoJsMetaSource>(source)
        assertEquals("Example Metadata", source.displayName)
        assertIs<PluginLookupDeniedException>(
            runCatching {
                source.searchSongs(MetaSongQuery("Song"), PluginLookupMode.AUTOMATIC)
            }.exceptionOrNull(),
        )

        repository.setEnabled("com.example.metadata", false)
        advanceUntilIdle()
        assertTrue(metaRegistry.sources.isEmpty())

        productionRegistry.shutdown()
    }

    @Test
    fun oneSourceFailureDoesNotStopRemainingSources() = runTest {
        val failing = FakeMetaSource(
            id = "failing",
            searchFailure = IllegalStateException("network failed"),
        )
        val working = FakeMetaSource(
            id = "working",
            songs = listOf(MetaSongCandidate(id = "1", title = "Song")),
            covers = listOf(MetaCoverCandidate("https://example.test/cover.jpg")),
        )
        val registry = MetaSourceRegistry(listOf(failing, working))
        val useCase = MetadataLookupUseCase(
            registry = registry,
            pluginRepository = PluginRepository(FakePluginDao(), "/plugins".toPath()),
        )

        val songs = useCase.searchSongs(
            query = MetaSongQuery("Song"),
            mode = PluginLookupMode.BATCH,
        )
        assertEquals(1, songs.items.size)
        assertEquals(2, songs.queriedSourceCount)
        assertEquals("working", songs.items.single().sourceId)
        assertEquals(1, songs.failures.size)
        assertEquals("failing", songs.failures.single().sourceId)

        val covers = useCase.searchCovers(
            query = MetaSongQuery("Song"),
            mode = PluginLookupMode.AUTOMATIC,
        )
        assertEquals("working", covers.items.single().sourceId)
        assertEquals(1, covers.failures.size)
    }

    @Test
    fun songSearchLimitsResultsPerSource() = runTest {
        val first = FakeMetaSource(
            id = "first",
            songs = (1..4).map { MetaSongCandidate(id = "first-$it", title = "First $it") },
        )
        val second = FakeMetaSource(
            id = "second",
            songs = (1..4).map { MetaSongCandidate(id = "second-$it", title = "Second $it") },
        )
        val useCase = MetadataLookupUseCase(
            registry = MetaSourceRegistry(listOf(first, second)),
            pluginRepository = PluginRepository(FakePluginDao(), "/plugins".toPath()),
        )

        val result = useCase.searchSongs(
            query = MetaSongQuery(title = "Song", pageSize = 3),
            mode = PluginLookupMode.BATCH,
        )

        assertEquals(6, result.items.size)
        assertEquals(
            listOf("first", "first", "first", "second", "second", "second"),
            result.items.map { it.sourceId },
        )
    }

    @Test
    fun lyricsAreRequestedOnlyFromCandidateSource() = runTest {
        val first = FakeMetaSource(id = "first", lyrics = MetaLyrics(rawPlainLrc = "first"))
        val second = FakeMetaSource(id = "second", lyrics = MetaLyrics(rawPlainLrc = "second"))
        val useCase = MetadataLookupUseCase(
            registry = MetaSourceRegistry(listOf(first, second)),
            pluginRepository = PluginRepository(FakePluginDao(), "/plugins".toPath()),
        )

        val result = useCase.getLyrics(
            candidate = MetaSongCandidate(
                id = "song",
                title = "Song",
                contextToken = "private-token",
                sourceId = "second",
            ),
            mode = PluginLookupMode.MANUAL,
        )

        assertEquals("second", result.value?.rawPlainLrc)
        assertEquals(0, first.lyricsCalls)
        assertEquals(1, second.lyricsCalls)
        assertTrue(result.failures.isEmpty())
    }

    @Test
    fun missingCandidateSourceReturnsDiagnosticFailure() = runTest {
        val useCase = MetadataLookupUseCase(
            registry = MetaSourceRegistry(),
            pluginRepository = PluginRepository(FakePluginDao(), "/plugins".toPath()),
        )
        val result = useCase.getLyrics(
            candidate = MetaSongCandidate(id = "1", title = "Song"),
            mode = PluginLookupMode.MANUAL,
        )
        assertNotNull(result.failures.singleOrNull())
        assertEquals("MissingSourceId", result.failures.single().errorType)
    }

    private fun runtimeManager(): PluginRuntimeManager {
        val settings = PluginRuntimeSettings(
            appVersionName = "test",
            cacheDirectory = "/tmp/musicapp-plugin-production-test",
        )
        return PluginRuntimeManager(
            factory = PluginRuntimeFactory(settings),
            bundleBuilder = PluginScriptBundleBuilder(),
        )
    }

    private fun pluginEntity(enabled: Boolean): PluginEntity = PluginEntity(
        id = 1,
        pluginId = "com.example.metadata",
        name = "Example Metadata",
        versionCode = 1,
        versionName = "1.0.0",
        author = "Author",
        description = "Description",
        apiVersion = 3,
        minHostApiVersion = 3,
        entryFile = "source.js",
        includeDirsJson = "[]",
        iconPath = null,
        capabilitiesJson = "[\"searchSongs\",\"getLyrics\",\"searchCovers\"]",
        manifestRawJson = "{\"configFields\":[]}",
        installedAt = 1,
        updatedAt = 1,
        enabled = enabled,
        allowManualLookup = true,
        allowAutomaticLookup = false,
        allowBatchLookup = false,
    )

    private class FakeMetaSource(
        override val id: String,
        private val songs: List<MetaSongCandidate> = emptyList(),
        private val covers: List<MetaCoverCandidate> = emptyList(),
        private val lyrics: MetaLyrics? = null,
        private val searchFailure: Throwable? = null,
    ) : MetaSource {
        override val displayName: String = id
        var lyricsCalls: Int = 0
            private set

        override suspend fun searchSongs(query: MetaSongQuery): List<MetaSongCandidate> {
            searchFailure?.let { throw it }
            return songs
        }

        override suspend fun getLyrics(
            candidate: MetaSongCandidate,
            config: Map<String, String>,
        ): MetaLyrics? {
            lyricsCalls += 1
            return lyrics
        }

        override suspend fun searchCovers(query: MetaSongQuery): List<MetaCoverCandidate> {
            searchFailure?.let { throw it }
            return covers
        }
    }
}
