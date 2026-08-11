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
                      "apiVersion": 1,
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
        assertEquals("song-1", covers.single().sourceId)

        source.clearPrivateContexts()
        manager.closeAll()
        assertTrue(manager.cachedPluginIds().isEmpty())
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
}
