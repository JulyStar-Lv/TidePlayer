package io.github.julystar.musicapp.source.server

import io.github.julystar.musicapp.source.api.OpenSubsonicCue
import io.github.julystar.musicapp.source.api.OpenSubsonicCueLine
import io.github.julystar.musicapp.source.api.OpenSubsonicLyricsAgent
import io.github.julystar.musicapp.source.api.OpenSubsonicLyricsLine
import io.github.julystar.musicapp.source.api.OpenSubsonicLyricsTrack
import io.github.julystar.musicapp.source.api.OpenSubsonicLyricsTrackKind
import io.github.julystar.musicapp.source.api.OpenSubsonicStructuredLyricsDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenSubsonicLyricsCodecTest {
    @Test
    fun preservesTrackHierarchyAndUtf8CueByteOffsets() {
        val document = OpenSubsonicStructuredLyricsDocument(
            tracks = listOf(
                OpenSubsonicLyricsTrack(
                    kind = OpenSubsonicLyricsTrackKind.Main,
                    displayArtist = "歌手",
                    displayTitle = "曲名",
                    language = "zh",
                    offsetMs = 12,
                    synced = true,
                    lines = listOf(OpenSubsonicLyricsLine(100, "你好")),
                    agents = listOf(OpenSubsonicLyricsAgent(id = "a1", name = "main", role = "main")),
                    cueLines = listOf(
                        OpenSubsonicCueLine(
                            index = 0,
                            startMs = 100,
                            endMs = 900,
                            value = "你好",
                            agentId = "a1",
                            cues = listOf(OpenSubsonicCue(100, 400, "你", byteStart = 0, byteEnd = 2)),
                        )
                    ),
                ),
                OpenSubsonicLyricsTrack(kind = OpenSubsonicLyricsTrackKind.Translation, language = "en"),
                OpenSubsonicLyricsTrack(kind = OpenSubsonicLyricsTrackKind.Pronunciation, language = "pinyin"),
            ),
        )
        val encoded = OpenSubsonicLyricsCodec.encode(document)
        val decoded = OpenSubsonicLyricsCodec.decode(encoded)
        assertEquals(document, decoded)
        assertTrue("\"tracks\"" in encoded)
        assertTrue("\"cueLine\"" in encoded)
        assertTrue("\"cue\"" in encoded)
        assertTrue("displayArtist" !in encoded.substringBefore("tracks"))
    }

    @Test
    fun missingTrackKindUsesV1MainDefault() {
        val decoded = OpenSubsonicLyricsCodec.decode("{\"tracks\":[{\"line\":[{\"value\":\"line\"}]}]}")
        assertEquals(OpenSubsonicLyricsTrackKind.Main, decoded?.tracks?.single()?.kind)
    }
}
