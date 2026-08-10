package io.github.julystar.musicapp.service.download.data.finalization

import io.github.julystar.musicapp.database.LyricsEntity
import io.github.julystar.musicapp.service.download.domain.LyricsSnapshotFormat
import io.github.julystar.musicapp.service.download.domain.MetadataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class LyricsSidecarSerializerTest {
    @Test
    fun plainLyricsRemainUtf8EmbeddedTextWithoutTimedSidecar() {
        val snapshot = lyrics(
            format = "TEXT",
            synchronized = false,
            content = "  夜空中最亮的星 ✨  ",
            sourceKind = "EmbeddedPlain",
        ).toLyricsSnapshot()

        assertEquals("夜空中最亮的星 ✨", snapshot?.embedded)
        assertNull(snapshot?.lrc)
        assertNull(snapshot?.ttml)
        assertEquals(LyricsSnapshotFormat.Plain, snapshot?.format)
        assertEquals(MetadataSource.Embedded, snapshot?.source)
    }

    @Test
    fun lineTimedLrcIsPreservedIncludingTranslationPair() {
        val raw = "[00:01.000]Hello\n[00:01.000]你好\n[00:03.000]World"
        val snapshot = lyrics(
            format = "LRC",
            synchronized = true,
            content = raw,
            sourceKind = "ExternalPlain",
        ).toLyricsSnapshot()

        assertEquals(raw, snapshot?.embedded)
        assertEquals(raw, snapshot?.lrc)
        assertEquals(LyricsSnapshotFormat.Lrc, snapshot?.format)
        assertEquals(MetadataSource.Plugin, snapshot?.source)
    }

    @Test
    fun ttmlKeepsLosslessSidecarAndDoesNotPromoteTranslationToPrimaryLrc() {
        val raw = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
                <body><div>
                    <p begin="00:01.000" end="00:02.000">
                        <span begin="00:01.000" end="00:02.000">Hello</span>
                        <span ttm:role="x-translation">你好</span>
                    </p>
                </div></body>
            </tt>
        """.trimIndent()
        val snapshot = lyrics(
            format = "TTML",
            synchronized = true,
            content = raw,
            sourceKind = "ExternalTtml",
        ).toLyricsSnapshot()

        assertEquals(raw, snapshot?.ttml)
        assertEquals("[00:01.000]Hello", snapshot?.lrc)
        assertFalse(snapshot?.lrc.orEmpty().contains("你好"))
        assertEquals(LyricsSnapshotFormat.Ttml, snapshot?.format)
    }

    @Test
    fun wordTimedLrcIsPreservedWithoutFlatteningWordTags() {
        val raw = "[00:02.000]<00:02.000>Hello<00:02.500> world<00:03.000>"
        val snapshot = lyrics(
            format = "LRC",
            synchronized = true,
            content = raw,
            sourceKind = "EmbeddedWordTimed",
        ).toLyricsSnapshot()

        assertEquals(raw, snapshot?.embedded)
        assertEquals(raw, snapshot?.lrc)
        assertEquals(LyricsSnapshotFormat.WordTimed, snapshot?.format)
    }

    @Test
    fun emptyLyricsAreSkipped() {
        assertNull(
            lyrics(
                format = "TEXT",
                synchronized = false,
                content = " \n\t ",
                sourceKind = "EmbeddedPlain",
            ).toLyricsSnapshot()
        )
    }

    private fun lyrics(
        format: String,
        synchronized: Boolean,
        content: String,
        sourceKind: String,
    ) = LyricsEntity(
        trackId = 1,
        format = format,
        language = null,
        synchronized = synchronized,
        content = content,
        sourcePath = "external:test".takeIf { sourceKind.startsWith("External") },
        updatedAt = 1,
        sourceKind = sourceKind,
    )
}
