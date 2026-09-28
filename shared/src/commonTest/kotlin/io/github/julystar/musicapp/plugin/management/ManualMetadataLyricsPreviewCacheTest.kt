package io.github.julystar.musicapp.plugin.management

import io.github.julystar.musicapp.source.api.MetaLyricLine
import io.github.julystar.musicapp.source.api.MetaLyricWord
import io.github.julystar.musicapp.source.api.MetaLyrics
import io.github.julystar.musicapp.source.api.MetaLyricsCandidate
import io.github.julystar.musicapp.source.api.MetaSongCandidate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ManualMetadataLyricsPreviewCacheTest {
    @Test
    fun identifiesTypesAndEmptyContent() {
        assertNull(MetaLyrics(rawPlainLrc = "  ").previewType())
        assertEquals(ManualMetadataLyricsType.TEXT, MetaLyrics(rawPlainLrc = "Hello").previewType())
        assertEquals(ManualMetadataLyricsType.LINE, MetaLyrics(rawPlainLrc = "[00:01.00]Hello").previewType())
        assertEquals(ManualMetadataLyricsType.WORD, MetaLyrics(rawEnhancedLrc = "[00:01]<00:01>Hello").previewType())
        assertEquals(ManualMetadataLyricsType.WORD, MetaLyrics(rawVerbatimLrc = "[1000,200](1000,200)Hello").previewType())
        assertEquals(ManualMetadataLyricsType.WORD, MetaLyrics(rawMultiPersonEnhancedLrc = "[00:01]<00:01>Hello").previewType())
        assertEquals(ManualMetadataLyricsType.TTML, MetaLyrics(rawTtml = "<tt>...</tt>").previewType())
        assertEquals(ManualMetadataLyricsType.LINE, MetaLyrics(lines = listOf(MetaLyricLine("Hello", startMs = 1000))).previewType())
        assertEquals(ManualMetadataLyricsType.WORD, MetaLyrics(lines = listOf(MetaLyricLine("Hello", words = listOf(MetaLyricWord("Hello", 1000))))).previewType())
    }

    @Test
    fun demotesMissingAndFailedLyricsWhilePreservingMatchOrder() {
        val results = (1..6).map { ManualMetadataResult(MetaSongCandidate("$it", "Song")) }
        val lyrics = MetaLyricsCandidate("lyrics", "Song", lyrics = MetaLyrics(rawPlainLrc = "Hello"), sourceId = "source")
        val statuses = mutableMapOf(
            "1" to ManualMetadataLyricsPreview(),
            "2" to ManualMetadataLyricsPreview(failed = true),
            "4" to ManualMetadataLyricsPreview(lyrics),
            "5" to ManualMetadataLyricsPreview(lyrics.copy(lyrics = MetaLyrics())),
            "6" to ManualMetadataLyricsPreview(lyrics),
        )
        assertEquals(
            listOf("3", "4", "6", "1", "2", "5"),
            rankManualMetadataResultsByLyrics(results) { statuses[it.id] }.map { it.song.id },
        )
        // A pending check keeps its match position until it confirms missing lyrics.
        statuses["3"] = ManualMetadataLyricsPreview()
        assertEquals(
            listOf("4", "6", "1", "2", "3", "5"),
            rankManualMetadataResultsByLyrics(results) { statuses[it.id] }.map { it.song.id },
        )
    }

    @Test
    fun recyclingKeepsChecksAndLimitsConcurrency() = runTest {
        var active = 0
        var maxActive = 0
        var requests = 0
        val cache = ManualMetadataLyricsPreviewCache(this) { song ->
            requests++
            active++
            maxActive = maxOf(maxActive, active)
            delay(100)
            active--
            MetaLyricsCandidate(song.id, song.title, lyrics = MetaLyrics(rawPlainLrc = "Hello"), sourceId = "source")
        }
        val song = MetaSongCandidate("1", "Song", sourceId = "source")
        val first = async { cache.load(song) }
        val recycled = async { cache.load(song) }
        val others = (2..5).map { id -> async { cache.load(song.copy(id = "$id")) } }
        runCurrent()
        first.cancelAndJoin()
        assertEquals("1", recycled.await().lyrics?.id)
        others.forEach { it.await() }
        assertEquals("1", cache.load(song).lyrics?.id)
        assertEquals(5, requests)
        assertEquals(2, maxActive)
    }

    @Test
    fun failuresAndTimeoutsAreNotReportedAsNoLyrics() = runTest {
        val song = MetaSongCandidate("1", "Song")
        val missing = ManualMetadataLyricsPreviewCache(this) { null }.load(song)
        assertNull(missing.lyrics)
        assertFalse(missing.failed)
        assertTrue(ManualMetadataLyricsPreviewCache(this) { error("Offline") }.load(song).failed)
        assertTrue(ManualMetadataLyricsPreviewCache(this) {
            withTimeout(10) { delay(100); null }
        }.load(song).failed)
    }
}
