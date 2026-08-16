package io.github.julystar.musicapp.plugin

import io.github.julystar.musicapp.plugin.runtime.InstalledPlugin
import io.github.julystar.musicapp.plugin.runtime.LyricoJsMetaSource
import io.github.julystar.musicapp.plugin.runtime.PluginCandidateContextStore
import io.github.julystar.musicapp.plugin.runtime.PluginConfigProvider
import io.github.julystar.musicapp.plugin.runtime.PluginLookupDeniedException
import io.github.julystar.musicapp.plugin.runtime.PluginLookupMode
import io.github.julystar.musicapp.plugin.runtime.PluginResultParser
import io.github.julystar.musicapp.plugin.runtime.PluginRuntimeCacheKey
import io.github.julystar.musicapp.plugin.runtime.PluginRuntimeDescriptor
import io.github.julystar.musicapp.plugin.runtime.PluginRuntimeFactory
import io.github.julystar.musicapp.plugin.runtime.PluginRuntimeManager
import io.github.julystar.musicapp.plugin.runtime.PluginRuntimeSettings
import io.github.julystar.musicapp.plugin.runtime.PluginScriptBundleBuilder
import io.github.julystar.musicapp.source.api.MetaSongQuery
import io.github.julystar.musicapp.source.api.MetaSourceRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive

class PluginPipelineTest {
    @Test
    fun runtimeCacheKeyEquality() {
        val first = PluginRuntimeCacheKey("a", 1, 100, "abc")
        val same = PluginRuntimeCacheKey("a", 1, 100, "abc")
        val changed = PluginRuntimeCacheKey("a", 1, 100, "def")
        assertEquals(first, same)
        assertNotEquals(first, changed)
    }

    @Test
    fun songResultsSupportAllWrappersAliasesAndInvalidEntryIsolation() = runTest {
        val parser = PluginResultParser()
        val wrappers = listOf("items", "results", "songs", "data")
        wrappers.forEach { wrapper ->
            val raw = """
                {
                  "$wrapper": [
                    {
                      "trackId": 42,
                      "songName": "Song",
                      "artists": ["Artist A", "Artist B"],
                      "albumName": "Album",
                      "duration_ms": "123000",
                      "release_date": "2026-01-02",
                      "track_number": 3,
                      "cover_url": "https://example.test/cover.jpg",
                      "fields": {"text":"value","number":7,"flag":true,"nested":{"x":1}},
                      "internal": {"lyric_id":"secret"},
                      "unknown": "ignored"
                    },
                    {"id":"missing-title"},
                    "invalid"
                  ]
                }
            """.trimIndent()
            val songs = parser.songs("com.example.plugin", raw, " & ")
            assertEquals(1, songs.size)
            val song = songs.single()
            assertEquals("42", song.id)
            assertEquals("Song", song.title)
            assertEquals("Artist A & Artist B", song.artist)
            assertEquals("Album", song.album)
            assertEquals(123000L, song.durationMs)
            assertEquals("2026-01-02", song.date)
            assertEquals("3", song.trackNumber)
            assertEquals("https://example.test/cover.jpg", song.pictureUrl)
            assertEquals("value", song.fields["text"])
            assertEquals("7", song.fields["number"])
            assertEquals("true", song.fields["flag"])
            assertFalse("nested" in song.fields)
            assertNotNull(song.contextToken)
            assertEquals(
                "secret",
                parser.internal("com.example.plugin", song.contextToken)
                    ?.let { it.toString() }
                    ?.substringAfter("lyric_id\":\"")
                    ?.substringBefore('"'),
            )
            assertNull(parser.internal("com.other.plugin", song.contextToken))
        }
    }

    @Test
    fun songResultsAcceptTopLevelArrayAndEncodedJsonString() = runTest {
        val parser = PluginResultParser()
        val array = parser.songs(
            "com.example.plugin",
            """[{"songId":"id-1","name":"One","singer":"Singer"}]""",
        )
        assertEquals("id-1", array.single().id)

        val encoded = parser.songs(
            "com.example.plugin",
            """"[{\"id\":\"id-2\",\"title\":\"Two\"}]"""",
        )
        assertEquals("id-2", encoded.single().id)
    }

    @Test
    fun coverResultsSupportStringsObjectsSongObjectsAndWrappers() {
        val parser = PluginResultParser()
        val covers = parser.covers(
            "com.example.plugin",
            """
                {
                  "covers": [
                    "https://example.test/a.jpg",
                    {"url":"https://example.test/b.jpg","width":800,"height":600},
                    {"id":"song-1","picUrl":"https://example.test/c.jpg"},
                    {"cover_url":""}
                  ]
                }
            """.trimIndent(),
        )
        assertEquals(3, covers.size)
        assertEquals("com.example.plugin", covers[0].sourceId)
        assertEquals(800, covers[1].width)
        assertEquals(600, covers[1].height)
        assertEquals("song-1", covers[2].id)
        assertEquals("com.example.plugin", covers[2].sourceId)
    }

    @Test
    fun api4CoverCandidatesRequireJudgementMetadataAndKeepSourceIdentity() {
        val covers = PluginResultParser().covers(
            pluginId = "com.example.cover",
            apiVersion = 4,
            raw = """
                {"results":[
                  {"title":"Song","artist":"Artist","album":"Album","date":"2026","coverUrl":"https://example.test/a.jpg"},
                  {"id":"remote-id","title":"Song","artist":"Artist","album":"Album","date":"2025","url":"https://example.test/b.jpg","width":1200,"height":1200},
                  {"title":"Missing date","artist":"Artist","album":"Album","url":"https://example.test/invalid.jpg"},
                  "https://example.test/legacy.jpg"
                ]}
            """.trimIndent(),
        )

        assertEquals(2, covers.size)
        assertNull(covers[0].id)
        assertEquals("remote-id", covers[1].id)
        assertEquals("2025", covers[1].date)
        assertTrue(covers.all { it.sourceId == "com.example.cover" })
    }

    @Test
    fun api4LyricsCandidatesPreserveAllResultsAndRequireCanonicalTags() {
        val parser = PluginResultParser()
        val candidates = parser.lyricsCandidates(
            pluginId = "com.example.lyrics",
            apiVersion = 4,
            raw = """
                {"candidates":[
                  {"id":"one","tags":{"ti":"Song","ar":"Artist","al":"Album","date":"2026"},"type":"rawPlainLrc","raw_plain_lrc":"[00:00]One"},
                  {"tags":{"ti":"Song (Live)","ar":"Artist","al":"Live","date":"2025"},"type":"rawTtml","raw_ttml":"<tt/>"},
                  {"tags":{"ti":"Missing date","ar":"Artist","al":"Album"},"type":"rawPlainLrc","lrc":"invalid"},
                  {"tags":{"ar":"Artist","al":"Album","date":"2024"},"type":"rawPlainLrc","lrc":"missing ti"},
                  {"tags":{"ti":"Song","al":"Album","date":"2024"},"type":"rawPlainLrc","lrc":"missing ar"},
                  {"tags":{"ti":"Song","ar":"Artist","date":"2024"},"type":"rawPlainLrc","lrc":"missing al"},
                  {"tags":{"ti":"Song","ar":"Artist","al":"Album","date":"2024"},"notFound":true}
                ]}
            """.trimIndent(),
            fallbackSong = MetaSongQuery("unused").let {
                io.github.julystar.musicapp.source.api.MetaSongCandidate("unused", it.title)
            },
        )

        assertEquals(2, candidates.size)
        assertEquals("one", candidates[0].id)
        assertEquals("[00:00]One", candidates[0].lyrics.rawPlainLrc)
        assertEquals("Song (Live)", candidates[1].title)
        assertEquals("<tt/>", candidates[1].lyrics.rawTtml)
        assertEquals("com.example.lyrics", candidates[1].sourceId)
    }

    @Test
    fun api3LyricsRemainSingleResultCompatible() {
        val fallback = io.github.julystar.musicapp.source.api.MetaSongCandidate(
            id = "legacy-id",
            title = "Legacy Song",
            artist = "Legacy Artist",
        )
        val candidates = PluginResultParser().lyricsCandidates(
            pluginId = "com.example.legacy",
            apiVersion = 3,
            raw = """{"type":"rawPlainLrc","original_lrc":"[00:00]Legacy"}""",
            fallbackSong = fallback,
        )

        assertEquals(1, candidates.size)
        assertEquals("legacy-id", candidates.single().id)
        assertEquals("Legacy Song", candidates.single().title)
        assertEquals("[00:00]Legacy", candidates.single().lyrics.rawPlainLrc)
    }

    @Test
    fun structuredLyricsSupportLineAndWordTimingWithTranslationMatching() {
        val parser = PluginResultParser()
        val lyrics = parser.lyrics(
            """
                {
                  "type":"structured",
                  "tags":{"title":"ignored"},
                  "original":[
                    [0,2000,"First line"],
                    [2000,4000,[[2000,3000,"Second"],[3000,4000," line"]]],
                    [5000,6000,"Third"]
                  ],
                  "translated":[[0,2000,"翻译一"],[2100,4000,"翻译二"],[5000,6000,"翻译三"]],
                  "romanization":[[0,2000,"roman one"],[2000,4000,"roman two"]]
                }
            """.trimIndent(),
        )
        assertNotNull(lyrics)
        assertEquals(3, lyrics.lines.size)
        assertEquals("First line", lyrics.lines[0].text)
        assertEquals("翻译一", lyrics.lines[0].translation)
        assertEquals("Second line", lyrics.lines[1].text)
        assertEquals(2, lyrics.lines[1].words.size)
        assertEquals("翻译二", lyrics.lines[1].translation)
        assertEquals("roman two", lyrics.lines[1].romanization)
        assertEquals("翻译三", lyrics.lines[2].translation)
    }

    @Test
    fun missingTypeDefaultsToStructuredAndNotFoundReturnsNull() {
        val parser = PluginResultParser()
        val lyrics = parser.lyrics("""{"original":[[0,1000,"Line"]]}""")
        assertEquals("Line", lyrics?.lines?.single()?.text)
        assertNull(parser.lyrics("""{"notFound":true}"""))
    }

    @Test
    fun allRawLyricTypesArePreserved() {
        val parser = PluginResultParser()
        assertEquals(
            "plain",
            parser.lyrics("""{"type":"rawPlainLrc","rawPlainLrc":"plain"}""")?.rawPlainLrc,
        )
        assertEquals(
            "verbatim",
            parser.lyrics("""{"type":"rawVerbatimLrc","content":"verbatim"}""")?.rawVerbatimLrc,
        )
        assertEquals(
            "enhanced",
            parser.lyrics("""{"type":"rawEnhancedLrc","lyrics":"enhanced"}""")?.rawEnhancedLrc,
        )
        assertEquals(
            "ttml",
            parser.lyrics("""{"type":"rawTtml","rawTtml":"ttml"}""")?.rawTtml,
        )
        assertEquals(
            "multi",
            parser.lyrics(
                """{"type":"rawMultiPersonEnhancedLrc","rawMultiPersonEnhancedLrc":"multi"}""",
            )?.rawMultiPersonEnhancedLrc,
        )
    }

    @Test
    fun legacyLyricsRemainCompatible() {
        val parser = PluginResultParser()
        val lyrics = parser.lyrics(
            """
                {
                  "lines":[{
                    "text":"Hello",
                    "startMs":100,
                    "endMs":200,
                    "words":[{"text":"Hello","startMs":100,"endMs":200}],
                    "translation":"你好",
                    "romanization":"ni hao"
                  }],
                  "rawPlainLrc":"[00:00.10]Hello"
                }
            """.trimIndent(),
        )
        assertEquals("Hello", lyrics?.lines?.single()?.text)
        assertEquals("你好", lyrics?.lines?.single()?.translation)
        assertEquals("[00:00.10]Hello", lyrics?.rawPlainLrc)
    }

    @Test
    fun privateContextStoreIsConcurrentBoundedAndPluginIsolated() = runTest {
        val store = PluginCandidateContextStore(ttlMs = 60_000, maxEntries = 8)
        val tokens = (0 until 32).map { index ->
            async { store.put("plugin-${index % 2}", JsonPrimitive(index)) }
        }.awaitAll()
        assertEquals(tokens.size, tokens.toSet().size)
        assertNull(store.get("plugin-0", tokens.first()))

        val token = store.put("plugin-a", JsonPrimitive("secret"))
        assertEquals("secret", store.get("plugin-a", token)?.toString()?.trim('"'))
        assertNull(store.get("plugin-b", token))
        store.clearPlugin("plugin-a")
        assertNull(store.get("plugin-a", token))
    }

    @Test
    fun permissionCombinationsRejectDisabledOrDisallowedModes() = runTest {
        val disabled = source(enabled = false, manual = true, automatic = true, batch = true)
        assertIs<PluginLookupDeniedException>(
            runCatching {
                disabled.searchSongs(MetaSongQuery("Song"), PluginLookupMode.MANUAL)
            }.exceptionOrNull(),
        )

        val plugin = source(enabled = true, manual = true, automatic = false, batch = false)
        assertIs<PluginLookupDeniedException>(
            runCatching {
                plugin.searchSongs(MetaSongQuery("Song"), PluginLookupMode.AUTOMATIC)
            }.exceptionOrNull(),
        )
        assertIs<PluginLookupDeniedException>(
            runCatching {
                plugin.searchSongs(MetaSongQuery("Song"), PluginLookupMode.BATCH)
            }.exceptionOrNull(),
        )
    }

    @Test
    fun registryCanBeReplacedObservably() {
        val registry = MetaSourceRegistry()
        assertTrue(registry.sources.isEmpty())
        val source = source(enabled = true, manual = true, automatic = false, batch = false)
        registry.replace(listOf(source))
        assertEquals(source.id, registry.sources.single().id)
        assertEquals(source, registry.sourceOrNull(source.id))
    }

    private fun source(
        enabled: Boolean,
        manual: Boolean,
        automatic: Boolean,
        batch: Boolean,
    ): LyricoJsMetaSource {
        val settings = PluginRuntimeSettings(
            appVersionName = "test",
            cacheDirectory = "/tmp/musicapp-plugin-test",
        )
        return LyricoJsMetaSource(
            plugin = InstalledPlugin(
                descriptor = PluginRuntimeDescriptor(
                    pluginId = "com.example.test",
                    pluginName = "Test",
                    pluginVersionCode = 1,
                    pluginUpdatedAt = 1,
                    entryFile = "source.js",
                    includeDirs = emptyList(),
                    directory = "/tmp/missing-plugin",
                ),
                capabilities = setOf("searchSongs", "getLyrics", "searchCovers"),
                enabled = enabled,
                allowManualLookup = manual,
                allowAutomaticLookup = automatic,
                allowBatchLookup = batch,
            ),
            runtimeManager = PluginRuntimeManager(
                factory = PluginRuntimeFactory(settings),
                bundleBuilder = PluginScriptBundleBuilder(),
            ),
            configProvider = PluginConfigProvider { emptyMap() },
            resultParser = PluginResultParser(),
        )
    }
}
