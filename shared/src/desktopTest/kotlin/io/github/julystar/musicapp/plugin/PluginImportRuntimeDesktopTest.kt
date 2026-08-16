package io.github.julystar.musicapp.plugin

import io.github.julystar.musicapp.plugin.install.FakePluginDao
import io.github.julystar.musicapp.plugin.install.PluginInstaller
import io.github.julystar.musicapp.plugin.management.PluginRepository
import io.github.julystar.musicapp.plugin.management.isPluginConfigFieldVisible
import io.github.julystar.musicapp.plugin.runtime.InstalledPlugin
import io.github.julystar.musicapp.plugin.runtime.LyricoJsMetaSource
import io.github.julystar.musicapp.plugin.runtime.PluginCandidateContextStore
import io.github.julystar.musicapp.plugin.runtime.PluginConfigProvider
import io.github.julystar.musicapp.plugin.runtime.PluginLookupMode
import io.github.julystar.musicapp.plugin.runtime.PluginResultParser
import io.github.julystar.musicapp.plugin.runtime.PluginRuntimeDescriptor
import io.github.julystar.musicapp.plugin.runtime.PluginRuntimeFactory
import io.github.julystar.musicapp.plugin.runtime.PluginRuntimeManager
import io.github.julystar.musicapp.plugin.runtime.PluginRuntimeSettings
import io.github.julystar.musicapp.plugin.runtime.PluginScriptBundleBuilder
import io.github.julystar.musicapp.source.api.MetaSongCandidate
import io.github.julystar.musicapp.source.api.MetaSongQuery
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.outputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath

class PluginImportRuntimeDesktopTest {
    @Test
    fun importsStrictLyricoV3ZipAndRunsCompleteMetadataFlow() = runTest {
        val temp = Files.createTempDirectory("musicapp-plugin-import-test")
        val zip = temp.resolve("plugin.zip")
        writeZip(
            zip,
            mapOf(
                "metadata/manifest.json" to
                    """
                    {
                      "id": "com.musicapp.test.imported",
                      "name": "Imported Metadata",
                      "versionCode": 1,
                      "versionName": "1.0.0",
                      "author": "TidePlayer Test",
                      "description": "Lyrico v3 import and runtime contract test",
                      "apiVersion": 3,
                      "minHostApiVersion": 3,
                      "entry": "source.js",
                      "includeDirs": ["lib"],
                      "capabilities": ["searchSongs", "getLyrics", "searchCovers"],
                      "configFields": [
                        {"key":"region","title":"Region","type":"text","defaultValue":"us"}
                      ]
                    }
                    """.trimIndent(),
                "metadata/lib/01_helper.js" to
                    """
                    function importedHelper(value) {
                      return "helper:" + value;
                    }
                    """.trimIndent(),
                "metadata/source.js" to
                    """
                    function searchSongs(request) {
                      if (!request.keyword || request.page !== 1 || request.pageSize !== 20) {
                        throw new Error("invalid Lyrico search request");
                      }
                      if (request.separator !== "/" || request.config.region !== "us") {
                        throw new Error("missing separator or merged config");
                      }
                      var info = Platform.runtime.getInfo();
                      return JSON.stringify([{
                        id: "song-1",
                        title: importedHelper(request.keyword),
                        artist: ["Artist A", "Artist B"],
                        album: "Album",
                        duration_ms: 123000,
                        cover_url: "https://example.test/cover.jpg",
                        fields: {
                          album: "Album",
                          md5: Platform.crypto.md5("abc"),
                          hostApiVersion: String(info.hostApiVersion)
                        },
                        internal: { lyric_id: "lyric-1" }
                      }]);
                    }

                    function getLyrics(request) {
                      if (!request.song || request.song.pluginId !== "com.musicapp.test.imported") {
                        throw new Error("missing nested song request");
                      }
                      if (request.song.internal.lyric_id !== "lyric-1") {
                        throw new Error("private context was not returned");
                      }
                      return JSON.stringify({
                        type: "structured",
                        original: [
                          [0, 2000, request.song.fields.md5 + ":" + request.song.internal.lyric_id],
                          [2000, 4000, [[2000, 3000, "Second"], [3000, 4000, " line"]]]
                        ],
                        translated: [[0, 2000, "translated"]],
                        romanization: [[0, 2000, "romanized"]]
                      });
                    }

                    function searchCovers(request) {
                      if (!request.keyword || request.pageSize !== 5) {
                        throw new Error("invalid Lyrico cover request");
                      }
                      return JSON.stringify([{
                        id: "song-1",
                        picUrl: "https://example.test/cover.jpg"
                      }]);
                    }
                    """.trimIndent(),
            ),
        )

        val dao = FakePluginDao()
        val pluginsDir = temp.resolve("plugins").toString().toPath()
        val installer = PluginInstaller(dao, pluginsDir)
        val installResult = installer.installAllFromZip(zip.toString().toPath())

        assertTrue(installResult.failed.isEmpty(), installResult.failed.toString())
        assertEquals(1, installResult.installed.size)
        var entity = dao.findByPluginId("com.musicapp.test.imported")
        assertNotNull(entity)
        assertFalse(entity.enabled)
        assertTrue(entity.allowManualLookup)
        assertFalse(entity.allowAutomaticLookup)
        assertFalse(entity.allowBatchLookup)
        assertEquals("us", dao.configValue(entity.pluginId, "region"))

        dao.setEnabled(entity.pluginId, true)
        entity = dao.findByPluginId(entity.pluginId)
        assertNotNull(entity)

        val descriptor = PluginRuntimeDescriptor(
            pluginId = entity.pluginId,
            pluginName = entity.name,
            pluginVersionCode = entity.versionCode,
            pluginUpdatedAt = entity.updatedAt,
            entryFile = entity.entryFile,
            includeDirs = listOf("lib"),
            directory = (pluginsDir / entity.pluginId).toString(),
            apiVersion = entity.apiVersion,
            minHostApiVersion = entity.minHostApiVersion,
        )
        val settings = PluginRuntimeSettings(
            appVersionName = "test",
            cacheDirectory = temp.resolve("cache").toString(),
        )
        val manager = PluginRuntimeManager(
            factory = PluginRuntimeFactory(settings),
            bundleBuilder = PluginScriptBundleBuilder(),
        )
        val parser = PluginResultParser(PluginCandidateContextStore())
        val source = LyricoJsMetaSource(
            plugin = InstalledPlugin(
                descriptor = descriptor,
                capabilities = setOf("searchSongs", "getLyrics", "searchCovers"),
                enabled = true,
            ),
            runtimeManager = manager,
            configProvider = PluginConfigProvider { pluginId ->
                dao.configsFor(pluginId).associate { it.configKey to it.configValue }
            },
            resultParser = parser,
        )

        val songs = source.searchSongs(
            MetaSongQuery(title = "Title", artist = "Artist"),
            PluginLookupMode.MANUAL,
        )
        assertEquals(1, songs.size)
        assertEquals("helper:Title Artist", songs[0].title)
        assertEquals("Artist A/Artist B", songs[0].artist)
        assertEquals("900150983cd24fb0d6963f7d28e17f72", songs[0].fields["md5"])
        assertEquals("3", songs[0].fields["hostApiVersion"])
        assertNotNull(songs[0].contextToken)

        val lyrics = source.getLyrics(
            candidate = songs[0],
            config = emptyMap(),
            mode = PluginLookupMode.MANUAL,
        )
        assertNotNull(lyrics)
        assertEquals(
            "900150983cd24fb0d6963f7d28e17f72:lyric-1",
            lyrics.lines[0].text,
        )
        assertEquals("translated", lyrics.lines[0].translation)
        assertEquals("romanized", lyrics.lines[0].romanization)
        assertEquals("Second line", lyrics.lines[1].text)
        assertEquals(2, lyrics.lines[1].words.size)

        val covers = source.searchCovers(
            MetaSongQuery(title = "Title"),
            PluginLookupMode.MANUAL,
        )
        assertEquals("https://example.test/cover.jpg", covers.single().url)
        assertEquals("song-1", covers.single().id)
        assertEquals("com.musicapp.test.imported", covers.single().sourceId)

        source.clearPrivateContexts()
        manager.closeAll()
        assertTrue(manager.cachedPluginIds().isEmpty())
    }

    @Test
    fun importsLyricoV4IndependentProvidersAndPreservesDirectReturnCandidates() = runTest {
        val temp = Files.createTempDirectory("musicapp-plugin-v4-test")
        val zip = temp.resolve("plugins.zip")
        writeZip(
            zip,
            mapOf(
                "lyrics/manifest.json" to
                    """
                    {
                      "id":"com.musicapp.test.lyrics-v4",
                      "name":"Lyrics v4",
                      "versionCode":1,
                      "versionName":"1.0.0",
                      "apiVersion":4,
                      "minHostApiVersion":3,
                      "capabilities":["getLyrics"]
                    }
                    """.trimIndent(),
                "lyrics/source.js" to
                    """
                    function getLyrics(request) {
                      if (request.song.id !== "local-song" || request.page !== 1 || request.pageSize !== 20) {
                        throw new Error("invalid API v4 local-song lyrics request");
                      }
                      if (Object.keys(request.song.internal).length !== 0) {
                        throw new Error("local-song leaked private internal state");
                      }
                      var info = Platform.runtime.getInfo();
                      if (info.pluginApiVersion !== 4 || info.hostApiVersion !== 3 || info.engine !== "quickjs") {
                        throw new Error("incomplete runtime.info");
                      }
                      if (["cache.get", "xml.findElements", "http.get"].some(function(name) { return info.supportedHostApis.indexOf(name) < 0; })) {
                        throw new Error("missing supportedHostApis");
                      }
                      return [
                        {id:"lyrics-1",tags:{ti:"Song",ar:"Artist",al:"Album",date:"2026"},type:"rawPlainLrc",raw_plain_lrc:"[00:00]One"},
                        {tags:{ti:"Song Live",ar:"Artist",al:"Live",date:"2025"},type:"rawTtml",raw_ttml:"<tt/>"}
                      ];
                    }
                    """.trimIndent(),
                "covers/manifest.json" to
                    """
                    {
                      "id":"com.musicapp.test.covers-v4",
                      "name":"Covers v4",
                      "versionCode":1,
                      "versionName":"1.0.0",
                      "apiVersion":4,
                      "minHostApiVersion":1,
                      "capabilities":["searchCovers"]
                    }
                    """.trimIndent(),
                "covers/source.js" to
                    """
                    function searchCovers(request) {
                      if (request.page !== 2 || request.pageSize !== 7 || request.song.id !== "local-song") {
                        throw new Error("invalid API v4 cover request");
                      }
                      return [
                        {title:"Song",artist:"Artist",album:"Album",date:"2026",picUrl:"https://example.test/a.jpg"},
                        {id:"cover-2",title:"Song",artist:"Artist",album:"Album",date:"2025",picUrl:"https://example.test/b.jpg"}
                      ];
                    }
                    """.trimIndent(),
                "internal/manifest.json" to
                    """
                    {
                      "id":"com.musicapp.test.internal-v4",
                      "name":"Internal v4",
                      "versionCode":1,
                      "versionName":"1.0.0",
                      "apiVersion":4,
                      "minHostApiVersion":1,
                      "capabilities":["searchSongs","getLyrics"]
                    }
                    """.trimIndent(),
                "internal/source.js" to
                    """
                    function searchSongs() {
                      return [{id:"remote-song",title:"Song",artist:"Artist",album:"Album",date:"2026",internal:{lyric_id:"private-lyric"}}];
                    }
                    function getLyrics(request) {
                      if (request.song.internal.lyric_id !== "private-lyric") {
                        throw new Error("same-plugin private context was not returned");
                      }
                      return [{tags:{ti:"Song",ar:"Artist",al:"Album",date:"2026"},type:"rawPlainLrc",lrc:"[00:00]Private"}];
                    }
                    """.trimIndent(),
            ),
        )

        val dao = FakePluginDao()
        val pluginsDir = temp.resolve("plugins").toString().toPath()
        val result = PluginInstaller(dao, pluginsDir).installAllFromZip(zip.toString().toPath())
        assertTrue(result.failed.isEmpty(), result.failed.toString())
        assertEquals(3, result.installed.size)
        result.installed.forEach { dao.setEnabled(it.id, true) }

        val manager = PluginRuntimeManager(
            factory = PluginRuntimeFactory(
                PluginRuntimeSettings(
                    appVersionName = "test",
                    cacheDirectory = temp.resolve("cache").toString(),
                ),
            ),
            bundleBuilder = PluginScriptBundleBuilder(),
        )
        val parser = PluginResultParser(PluginCandidateContextStore())
        suspend fun source(pluginId: String): LyricoJsMetaSource {
            val entity = assertNotNull(dao.findByPluginId(pluginId))
            return LyricoJsMetaSource(
                plugin = InstalledPlugin(
                    descriptor = PluginRuntimeDescriptor(
                        pluginId = entity.pluginId,
                        pluginName = entity.name,
                        pluginVersionCode = entity.versionCode,
                        pluginUpdatedAt = entity.updatedAt,
                        entryFile = entity.entryFile,
                        includeDirs = emptyList(),
                        directory = (pluginsDir / entity.pluginId).toString(),
                        apiVersion = entity.apiVersion,
                        minHostApiVersion = entity.minHostApiVersion,
                    ),
                    capabilities = result.installed.single { it.id == pluginId }.capabilities.toSet(),
                    enabled = true,
                ),
                runtimeManager = manager,
                configProvider = PluginConfigProvider { emptyMap() },
                resultParser = parser,
            )
        }

        val lyricsSource = source("com.musicapp.test.lyrics-v4")
        val lyrics = lyricsSource.getLyricsCandidates(
            candidate = MetaSongCandidate(
                id = "local-song",
                title = "Song",
                artist = "Artist",
                sourceId = "com.musicapp.test.lyrics-v4",
            ),
            mode = PluginLookupMode.MANUAL,
        )
        assertEquals(2, lyrics.size)
        assertEquals("lyrics-1", lyrics[0].id)
        assertEquals("<tt/>", lyrics[1].lyrics.rawTtml)

        val internalSource = source("com.musicapp.test.internal-v4")
        val songs = internalSource.searchSongs(
            MetaSongQuery(title = "Song"),
            PluginLookupMode.MANUAL,
        )
        assertNotNull(songs.single().contextToken)
        val privateLyrics = internalSource.getLyricsCandidates(
            candidate = songs.single(),
            mode = PluginLookupMode.MANUAL,
        )
        assertEquals("[00:00]Private", privateLyrics.single().lyrics.rawPlainLrc)
        val isolatedLyrics = lyricsSource.getLyricsCandidates(
            candidate = MetaSongCandidate(
                id = "local-song",
                title = "Song",
                artist = "Artist",
                contextToken = songs.single().contextToken,
                sourceId = "com.musicapp.test.internal-v4",
            ),
            mode = PluginLookupMode.MANUAL,
        )
        assertEquals(2, isolatedLyrics.size)

        val covers = source("com.musicapp.test.covers-v4").searchCovers(
            query = MetaSongQuery(
                title = "Song",
                artist = "Artist",
                page = 2,
                pageSize = 7,
                song = MetaSongCandidate(
                    id = "local-song",
                    title = "Song",
                    artist = "Artist",
                    sourceId = "com.musicapp.test.covers-v4",
                ),
            ),
            mode = PluginLookupMode.MANUAL,
        )
        assertEquals(2, covers.size)
        assertNull(covers[0].id)
        assertEquals("cover-2", covers[1].id)
        assertTrue(covers.all { it.sourceId == "com.musicapp.test.covers-v4" })
        manager.closeAll()
    }

    @Test
    fun manifestAcceptsApi4IndependentCapabilitiesAndRejectsFutureProtocolOrHost() = runTest {
        val temp = Files.createTempDirectory("musicapp-plugin-manifest-boundaries")
        val zip = temp.resolve("plugins.zip")
        fun manifest(id: String, api: Int, host: Int, capability: String) =
            """{"id":"$id","name":"$id","versionCode":1,"versionName":"1","apiVersion":$api,"minHostApiVersion":$host,"capabilities":["$capability"]}"""
        writeZip(
            zip,
            mapOf(
                "lyrics/manifest.json" to manifest("com.test.lyrics", 4, 3, "getLyrics"),
                "lyrics/source.js" to "function getLyrics(){return [];}",
                "covers/manifest.json" to manifest("com.test.covers", 4, 1, "searchCovers"),
                "covers/source.js" to "function searchCovers(){return [];}",
                "api1/manifest.json" to manifest("com.test.api1", 1, 1, "searchSongs"),
                "api1/source.js" to "function searchSongs(){return [];}",
                "api2/manifest.json" to manifest("com.test.api2", 2, 2, "searchSongs"),
                "api2/source.js" to "function searchSongs(){return [];}",
                "api3/manifest.json" to manifest("com.test.api3", 3, 3, "searchSongs"),
                "api3/source.js" to "function searchSongs(){return [];}",
                "future-plugin/manifest.json" to manifest("com.test.future.plugin", 5, 3, "getLyrics"),
                "future-plugin/source.js" to "function getLyrics(){return [];}",
                "past-plugin/manifest.json" to manifest("com.test.past.plugin", 0, 1, "getLyrics"),
                "past-plugin/source.js" to "function getLyrics(){return [];}",
                "future-host/manifest.json" to manifest("com.test.future.host", 4, 4, "getLyrics"),
                "future-host/source.js" to "function getLyrics(){return [];}",
            ),
        )

        val result = PluginInstaller(
            FakePluginDao(),
            temp.resolve("plugins").toString().toPath(),
        ).installAllFromZip(zip.toString().toPath())

        assertEquals(
            setOf(
                "com.test.lyrics",
                "com.test.covers",
                "com.test.api1",
                "com.test.api2",
                "com.test.api3",
            ),
            result.installed.map { it.id }.toSet(),
        )
        assertEquals(3, result.failed.size)
        assertTrue(result.failed.any { it.reason.contains("unsupported plugin protocol 5") })
        assertTrue(result.failed.any { it.reason.contains("unsupported plugin protocol 0") })
        assertTrue(result.failed.any { it.reason.contains("unsupported host API 4") })
    }

    @Test
    fun importsBundlesAndLoadsCurrentOfficialPluginsWhenFixtureIsAvailable() = runTest {
        val fixtureRoot = System.getenv("LYRICO_PLUGINS_DIR")
            ?.takeIf(String::isNotBlank)
            ?.let { java.nio.file.Path.of(it) }
            ?: return@runTest
        val pluginDirectories = listOf("apple", "qq", "netease", "kugou", "soda")
        val temp = Files.createTempDirectory("musicapp-official-plugin-smoke")
        val zip = temp.resolve("official-plugins.zip")
        writePluginDirectoriesZip(zip, fixtureRoot, pluginDirectories)

        val dao = FakePluginDao()
        val pluginsDir = temp.resolve("plugins").toString().toPath()
        val result = PluginInstaller(dao, pluginsDir).installAllFromZip(zip.toString().toPath())
        assertTrue(result.failed.isEmpty(), result.failed.toString())
        assertEquals(
            setOf(
                "com.applemusic.source",
                "com.qqmusic.source",
                "com.neteasecloudmusic.source",
                "com.kugou.source",
                "com.sodamusic.source",
            ),
            result.installed.map { it.id }.toSet(),
        )

        val repository = PluginRepository(dao, pluginsDir)
        val summaries = repository.allSnapshot()
        assertEquals(5, summaries.size)
        assertTrue(summaries.all { it.apiVersion == 4 })
        assertTrue(summaries.all { it.capabilities.containsAll(listOf("searchSongs", "getLyrics", "searchCovers")) })
        val manager = PluginRuntimeManager(
            factory = PluginRuntimeFactory(
                PluginRuntimeSettings(
                    appVersionName = "test",
                    cacheDirectory = temp.resolve("cache").toString(),
                ),
            ),
            bundleBuilder = PluginScriptBundleBuilder(),
        )
        summaries.forEach { summary ->
            val installed = with(repository) { summary.toInstalledPlugin() }
            manager.runtime(installed.descriptor)
        }
        assertEquals(summaries.map { it.id }.toSet(), manager.cachedPluginIds())
        manager.closeAll()
    }

    @Test
    fun importsOfficialLyricoV3ConfigFieldsAndDefaultsEmptyCapabilitiesToSongSearch() = runTest {
        val temp = Files.createTempDirectory("musicapp-plugin-config-test")
        val zip = temp.resolve("plugin.zip")
        writeZip(
            zip,
            mapOf(
                "manifest.json" to
                    """
                    {
                      "id": "com.musicapp.test.config",
                      "name": "Config Metadata",
                      "versionCode": 1,
                      "versionName": "1.0.0",
                      "apiVersion": 3,
                      "configFields": [
                        {"key":"intro","title":"Introduction","type":"markdown","defaultValue":"Plugin help"},
                        {"key":"token","title":"Token","type":"password"},
                        {"key":"limit","title":"Limit","type":"number","defaultValue":"20"},
                        {"key":"proxy","title":"Proxy","type":"switch","defaultValue":"false"},
                        {
                          "key":"region",
                          "title":"Region",
                          "type":"dropdown",
                          "defaultValue":"us",
                          "options":[
                            {"value":"us","label":"United States","summary":"US catalog"},
                            {"value":"jp","label":"Japan"}
                          ]
                        },
                        {"key":"headers","title":"Headers","type":"textarea","defaultValue":"Accept: application/json"},
                        {
                          "key":"proxy_url",
                          "title":"Proxy URL",
                          "type":"text",
                          "dependency":{"match":{"key":"proxy","value":"true"}}
                        },
                        {
                          "key":"and_field",
                          "title":"AND dependency",
                          "type":"text",
                          "dependency":{"and":{"conditions":[
                            {"match":{"key":"proxy","value":"true"}},
                            {"match":{"key":"region","value":"us"}}
                          ]}}
                        },
                        {
                          "key":"or_field",
                          "title":"OR dependency",
                          "type":"text",
                          "dependency":{"or":{"conditions":[
                            {"match":{"key":"proxy","value":"true"}},
                            {"match":{"key":"region","value":"jp"}}
                          ]}}
                        },
                        {
                          "key":"not_field",
                          "title":"NOT dependency",
                          "type":"text",
                          "dependency":{"not":{"condition":{"match":{"key":"proxy","value":"true"}}}}
                        }
                      ]
                    }
                    """.trimIndent(),
                "source.js" to
                    """
                    function searchSongs(request) {
                      return JSON.stringify([]);
                    }
                    """.trimIndent(),
            ),
        )

        val dao = FakePluginDao()
        val pluginsDir = temp.resolve("plugins").toString().toPath()
        val result = PluginInstaller(dao, pluginsDir).installAllFromZip(zip.toString().toPath())

        assertTrue(result.failed.isEmpty(), result.failed.toString())
        val manifest = result.installed.single()
        assertEquals("", manifest.author)
        assertEquals("", manifest.description)
        assertTrue(manifest.capabilities.isEmpty())
        assertEquals(
            listOf("markdown", "password", "number", "switch", "dropdown", "textarea", "text"),
            manifest.configFields.take(7).map { it.type },
        )
        assertEquals("United States", manifest.configFields[4].options[0].label)
        assertEquals("US catalog", manifest.configFields[4].options[0].summary)
        assertNull(dao.configValue(manifest.id, "intro"))
        assertEquals("false", dao.configValue(manifest.id, "proxy"))
        assertEquals("us", dao.configValue(manifest.id, "region"))

        val repository = PluginRepository(dao, pluginsDir)
        val summary = assertNotNull(repository.getPlugin(manifest.id))
        assertEquals("United States", summary.configFields[4].options[0].label)
        assertEquals(setOf("searchSongs"), with(repository) { summary.toInstalledPlugin() }.capabilities)
        assertTrue("intro" !in repository.config(manifest.id))
        assertFalse(isPluginConfigFieldVisible(summary.configFields[6], mapOf("proxy" to "false")))
        assertTrue(isPluginConfigFieldVisible(summary.configFields[6], mapOf("proxy" to "true")))
        assertFalse(
            isPluginConfigFieldVisible(summary.configFields[7], mapOf("proxy" to "false", "region" to "us")),
        )
        assertTrue(
            isPluginConfigFieldVisible(summary.configFields[7], mapOf("proxy" to "true", "region" to "us")),
        )
        assertFalse(
            isPluginConfigFieldVisible(summary.configFields[8], mapOf("proxy" to "false", "region" to "us")),
        )
        assertTrue(
            isPluginConfigFieldVisible(summary.configFields[8], mapOf("proxy" to "false", "region" to "jp")),
        )
        assertTrue(isPluginConfigFieldVisible(summary.configFields[9], mapOf("proxy" to "false")))
        assertFalse(isPluginConfigFieldVisible(summary.configFields[9], mapOf("proxy" to "true")))
    }

    private fun writeZip(
        path: java.nio.file.Path,
        entries: Map<String, String>,
    ) {
        ZipOutputStream(path.outputStream()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    private fun writePluginDirectoriesZip(
        path: java.nio.file.Path,
        root: java.nio.file.Path,
        directories: List<String>,
    ) {
        ZipOutputStream(path.outputStream()).use { zip ->
            directories.forEach { name ->
                val directory = root.resolve(name)
                require(Files.isDirectory(directory)) { "Missing official plugin fixture: $directory" }
                Files.walk(directory).use { files ->
                    files.filter(Files::isRegularFile).forEach { file ->
                        val relative = directory.relativize(file).toString().replace('\\', '/')
                        zip.putNextEntry(ZipEntry("$name/$relative"))
                        zip.write(Files.readAllBytes(file))
                        zip.closeEntry()
                    }
                }
            }
        }
    }
}
