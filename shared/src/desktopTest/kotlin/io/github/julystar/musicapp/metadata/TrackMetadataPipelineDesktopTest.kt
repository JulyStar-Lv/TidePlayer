package io.github.julystar.musicapp.metadata

import kotlin.test.Test
import kotlin.test.assertEquals

class TrackMetadataPipelineDesktopTest {
    @Test
    fun filenameParserIsPlatformIndependent() {
        assertEquals("Track", FilenameMetadataParser.parse("/music/Track.OGG")?.title)
    }

    @Test
    fun parsesUnicodeAndTechnicalFilenameHints() {
        val parsed = FilenameMetadataParser.parse("01_アーティスト_夜曲 [Official Audio].flac")
        assertEquals("夜曲", parsed?.title)
        assertEquals(setOf("Official Audio"), parsed?.hints)
    }
}
