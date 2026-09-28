package io.github.julystar.musicapp.plugin

import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.parser.TTMLParser
import io.github.julystar.musicapp.plugin.management.toEntity
import io.github.julystar.musicapp.plugin.runtime.PluginResultParser
import io.github.julystar.musicapp.source.api.MetaSongCandidate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginApi5Test {
    private fun parse(bodyDur: String = "3s") = PluginResultParser().lyricsCandidates(
        pluginId = "com.test.api5",
        apiVersion = 5,
        fallbackSong = MetaSongCandidate("local-song", "unused"),
        raw = """
            [{"type":"structured","tags":{"ti":"Song & title","ar":"Artist","al":"Album","date":"2026"},
              "original":[[1000,3000,[[1000,2000,"詮",[[null,null,"せ"],[null,null,"ん"]]],[2000,3000,"明"]],
                {"ttm:agent":"v1","itunes:songPart":"Verse","divBegin":"1000","divEnd":"3000","bad:attribute":"ignored"}]],
              "translated":[[1000,3000,"Translation & text"]],
              "romanization":[[1000,3000,[[1000,2000,"sen"],[2000,3000,"mei"]]]],
              "agents":[{"id":"v1","type":"person","name":"Artist <A>"},{"type":"person"}],
              "metadata":[{"name":"songwriters","children":[{"name":"songwriter","text":"Writer"}]},
                {"name":"amll:meta","namespace":"https://example.test/amll","attributes":{"key":"value"},"text":"A & B"},
                {"name":"translations"},{"name":"bad:meta"},{"name":"songwriters"}],
              "timing":"Word","language":"ja","translatedLang":"en","romanizationLang":"ja-Latn","body_dur":"$bodyDur"}]
        """.trimIndent(),
    ).single().lyrics

    @Test
    fun parsesAndPersistsExtendedLyricsWithoutLosingTimingOrMetadata() {
        val lyrics = parse()
        val line = lyrics.lines.single()
        assertEquals("詮明", line.text)
        assertEquals("senmei", line.romanization)
        assertEquals(listOf(1000L, 2000L), line.romanizationWords.map { it.startMs })
        assertEquals("v1", line.person)
        assertFalse("bad:attribute" in line.extensions)
        assertNull(line.words.first().ruby.first().startMs)
        assertEquals(1, lyrics.agents.size)
        assertEquals(2, lyrics.metadata.size)
        val entity = assertNotNull(lyrics.toEntity(42, 100))
        assertEquals("TTML", entity.format)
        assertEquals("ja", entity.language)
        assertEquals("ExternalTtml", entity.sourceKind)
        val ttml = entity.content
        listOf("itunes:song-part=\"Verse\"", "xml:lang=\"ja-Latn\"", "<body dur=\"3s\">",
            "tts:ruby=\"container\"", "begin=\"00:00:01.500\"", "<songwriter>Writer</songwriter>",
            "<amll:meta", "xmlns:amll=\"https://example.test/amll\"", "Song &amp; title").forEach {
            assertTrue(ttml.contains(it), "Missing $it in $ttml")
        }
        val playback = TTMLParser().parse(ttml).lines.single() as KaraokeLine
        assertEquals(1000, playback.start)
        assertEquals(3000, playback.end)
        assertEquals("詮明", playback.syllables.joinToString("") { it.content })
        assertEquals("sen", playback.syllables.first().phonetic)
        assertEquals("Translation & text", playback.translation)
    }

    @Test
    fun dropsInvalidDurationButKeepsValidTtmlTimeExpressions() {
        assertNull(parse("not-a-time").bodyDur)
        assertFalse(assertNotNull(parse("not-a-time").toEntity(1, 0)).content.contains("<body dur="))
        listOf("00:02:03.456", "123ms", "2.5s", "1h", "20f").forEach { assertEquals(it, parse(it).bodyDur) }
    }

    @Test
    fun api5KeepsCandidateJudgementRulesAndLegacyLineRomanization() {
        val result = PluginResultParser().lyricsCandidates(
            "com.test.api5", 5,
            """[
                {"tags":{"ti":"Song","ar":"Artist","al":"Album","date":"2026"},"original":[[0,1000,"Line"]],"romanization":[[0,1000,"Roma"]]},
                {"tags":{"ti":"Song","ar":"Artist","al":"Album"},"original":[[0,1000,"Invalid"]]}
            ]""", MetaSongCandidate("local-song", "unused"),
        )
        assertEquals(1, result.size)
        assertEquals("Roma", result.single().lyrics.lines.single().romanization)
        assertTrue(result.single().lyrics.lines.single().romanizationWords.isEmpty())
    }
}
