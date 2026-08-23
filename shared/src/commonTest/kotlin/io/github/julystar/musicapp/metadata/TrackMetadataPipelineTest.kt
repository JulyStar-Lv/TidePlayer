package io.github.julystar.musicapp.metadata

import io.github.julystar.musicapp.database.TrackEntity
import io.github.julystar.musicapp.database.TrackMetadataSources
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackMetadataPipelineTest {
    @Test
    fun parsesArtistTitleAndExtension() {
        val parsed = assertNotNull(FilenameMetadataParser.parse("周杰伦 - 晴天.flac"))
        assertEquals("周杰伦", parsed.artist)
        assertEquals("晴天", parsed.title)
    }

    @Test
    fun parsesDiscAndTrackPrefix() {
        val parsed = assertNotNull(FilenameMetadataParser.parse("01-02 - Intro.mp3"))
        assertEquals(1, parsed.discNumber)
        assertEquals(2, parsed.trackNumber)
        assertEquals("Intro", parsed.title)
    }

    @Test
    fun keepsRawContextAndRemovesTechnicalTags() {
        val parsed = assertNotNull(
            FilenameMetadataParser.parse(
                "周杰伦 - 夜曲 (Live Remix).mp3",
                parent = "专辑",
                grandparent = "歌手",
                durationMs = 240_000,
            ),
        )
        assertEquals("夜曲 (Live Remix)", parsed.title)
        assertEquals("专辑", parsed.parent)
        assertEquals("歌手", parsed.grandparent)
        assertEquals(240_000, parsed.durationMs)
        assertEquals(setOf("Live Remix"), parsed.hints)
        assertEquals("周杰伦 - 夜曲 (Live Remix).mp3", parsed.raw)
    }

    @Test
    fun parsesArtistAlbumTrackTitleAndFeature() {
        val parsed = assertNotNull(FilenameMetadataParser.parse("Artist - Album - 03 - Title feat. Guest.m4a"))
        assertEquals("Artist", parsed.artist)
        assertEquals("Album", parsed.album)
        assertEquals(3, parsed.trackNumber)
        assertEquals("Title feat. Guest", parsed.title)
    }

    @Test
    fun parsesRequiredFilenameShapesWithoutRemovingVersionIdentity() {
        val expectations = listOf(
            "夜曲.flac" to Triple("夜曲", null, null),
            "周杰伦 - 夜曲.flac" to Triple("夜曲", "周杰伦", null),
            "01 - 夜曲.flac" to Triple("夜曲", null, 1),
            "04. 周杰伦 - 夜曲.flac" to Triple("夜曲", "周杰伦", 4),
            "周杰伦 - 十一月的萧邦 - 04 - 夜曲.flac" to Triple("夜曲", "周杰伦", 4),
            "夜曲 (Live).flac" to Triple("夜曲 (Live)", null, null),
            "夜曲 Remix.flac" to Triple("夜曲 Remix", null, null),
            "track001.flac" to Triple("track001", null, null),
            "audio_123.flac" to Triple("audio_123", null, null),
            "宇多田ヒカル—First Love.flac" to Triple("First Love", "宇多田ヒカル", null),
            "아이유－좋은 날.flac" to Triple("좋은 날", "아이유", null),
        )

        expectations.forEach { (fileName, expected) ->
            val parsed = assertNotNull(FilenameMetadataParser.parse(fileName), fileName)
            assertEquals(expected.first, parsed.title, fileName)
            assertEquals(expected.second, parsed.artist, fileName)
            assertEquals(expected.third, parsed.trackNumber, fileName)
        }
    }

    @Test
    fun removesOnlyPureTechnicalBracketTags() {
        assertEquals(
            "夜曲",
            FilenameMetadataParser.parse("01 - 夜曲 [24bit 96kHz].flac")?.title,
        )
        assertEquals(
            "Song (2011 Remastered)",
            FilenameMetadataParser.parse("Song (2011 Remastered) [FLAC].flac")?.title,
        )
        assertEquals(
            "Title feat. Guest",
            FilenameMetadataParser.parse("Title feat. Guest [Official Audio].mp3")?.title,
        )
    }

    @Test
    fun matcherUsesDurationAndReturnsConfidence() {
        val track = TrackEntity(
            id = 1, title = "Song", sortTitle = null, albumId = null, albumArtist = null,
            composer = null, comment = null, grouping = null, durationMs = 100_000,
            discNumber = null, discTotal = null, trackNumber = 2, trackTotal = null,
            year = null, date = null, sampleRate = null, bitRate = null, bitsPerSample = null,
            channels = null, channelLayout = null, codec = null, container = null, lossless = null,
            createdAt = 1, updatedAt = 1, artist = "Artist",
        )
        val match = TrackCandidateMatcher.match(track, listOf(
            io.github.julystar.musicapp.source.api.MetaSongCandidate(
                id = "remote", title = "Song", artist = "Artist", durationMs = 100_500,
            ),
        ))
        assertEquals(TrackCandidateMatchConfidence.HIGH, assertNotNull(match).confidence)
    }

    @Test
    fun matcherRejectsVersionAndUnacceptableDuration() {
        val track = testTrack(title = "Song", artist = "Artist", duration = 100_000)
        val version = TrackCandidateMatcher.match(
            track,
            listOf(io.github.julystar.musicapp.source.api.MetaSongCandidate("v", "Song Live", "Artist", durationMs = 100_000)),
        )
        val duration = TrackCandidateMatcher.match(
            track,
            listOf(io.github.julystar.musicapp.source.api.MetaSongCandidate("d", "Song", "Artist", durationMs = 110_000)),
        )
        assertEquals(null, version)
        assertEquals(null, duration)
    }

    @Test
    fun differentArtistCannotBeHigh() {
        val track = testTrack(title = "Song", artist = "Artist", duration = 100_000)
        val match = TrackCandidateMatcher.match(
            track,
            listOf(io.github.julystar.musicapp.source.api.MetaSongCandidate("x", "Song", "Other", durationMs = 100_000)),
        )
        assertEquals(TrackCandidateMatchConfidence.MEDIUM, assertNotNull(match).confidence)
    }

    @Test
    fun matcherBoostsExactStrongAndPersistedPluginIdentity() {
        val track = testTrack("Song", "Artist", 100_000).copy(
            musicBrainzRecordingId = "recording",
            isrc = "ISRC",
            metadataSourceId = "plugin",
            metadataExternalId = "external",
        )
        val base = io.github.julystar.musicapp.source.api.MetaSongCandidate(
            id = "other", title = "Song", artist = "Artist", durationMs = 100_000, sourceId = "plugin",
        )
        val exact = base.copy(
            id = "external",
            fields = mapOf("musicBrainzRecordingId" to "recording", "isrc" to "ISRC"),
        )
        val baseScore = assertNotNull(TrackCandidateMatcher.match(track, listOf(base))).score
        val exactScore = assertNotNull(TrackCandidateMatcher.match(track, listOf(exact))).score
        assertTrue(exactScore > baseScore)
    }

    @Test
    fun matcherVetoesDifferentStrongIdsButNotOrdinaryPluginIdMismatch() {
        val track = testTrack("Song", "Artist", 100_000).copy(
            musicBrainzRecordingId = "recording-a",
            metadataSourceId = "plugin",
            metadataExternalId = "old-id",
        )
        assertNull(TrackCandidateMatcher.match(track, listOf(
            io.github.julystar.musicapp.source.api.MetaSongCandidate(
                id = "new-id", title = "Song", artist = "Artist", durationMs = 100_000,
                sourceId = "plugin", fields = mapOf("musicBrainzRecordingId" to "recording-b"),
            ),
        )))
        assertNotNull(TrackCandidateMatcher.match(track, listOf(
            io.github.julystar.musicapp.source.api.MetaSongCandidate(
                id = "new-id", title = "Song", artist = "Artist", durationMs = 100_000,
                sourceId = "plugin",
            ),
        )))
    }

    @Test
    fun pluginResultRetainsMatchedCandidate() {
        val candidate = io.github.julystar.musicapp.source.api.MetaSongCandidate("source-id", "Song")
        val match = TrackCandidateMatch(candidate, TrackCandidateMatchConfidence.LOW, 20)
        assertEquals(candidate, PluginSemanticMetadataResult(match, rounds = 4).match.candidate)
    }

    @Test
    fun qualityUsesFilenameProvenanceAndCompleteSemanticFields() {
        assertEquals(
            TrackMetadataQuality.EMPTY,
            TrackMetadataQualityEvaluator.evaluate(TrackMetadataQualityInput()),
        )
        assertEquals(
            TrackMetadataQuality.FILENAME_ONLY,
            TrackMetadataQualityEvaluator.evaluate(
                TrackMetadataQualityInput(title = "Song"),
                TrackMetadataSources.Filename,
            ),
        )
        assertEquals(
            TrackMetadataQuality.PARTIAL,
            TrackMetadataQualityEvaluator.evaluate(
                TrackMetadataQualityInput(title = "Song", artist = "Artist"),
                TrackMetadataSources.File,
            ),
        )
        assertEquals(
            TrackMetadataQuality.COMPLETE,
            TrackMetadataQualityEvaluator.evaluate(
                TrackMetadataQualityInput(
                    title = "Song",
                    artist = "Artist",
                    album = "Album",
                    durationMs = 180_000,
                ),
                TrackMetadataSources.File,
            ),
        )
    }

    @Test
    fun confidenceBoundariesAreDeterministic() {
        val track = testTrack(title = "Song", artist = "Artist", duration = 100_000)
        fun candidate(id: String, duration: Long) =
            io.github.julystar.musicapp.source.api.MetaSongCandidate(
                id = id,
                title = "Song",
                artist = "Artist",
                durationMs = duration,
            )

        assertEquals(
            TrackCandidateMatchConfidence.HIGH,
            TrackCandidateMatcher.match(track, listOf(candidate("one", 101_000)))?.confidence,
        )
        assertEquals(
            TrackCandidateMatchConfidence.HIGH,
            TrackCandidateMatcher.match(track, listOf(candidate("three", 103_000)))?.confidence,
        )
        assertNull(TrackCandidateMatcher.match(track, listOf(candidate("bad", 103_001))))
        assertTrue(
            PluginSemanticMetadataResult(
                TrackCandidateMatch(candidate("high", 100_000), TrackCandidateMatchConfidence.HIGH, 90),
                rounds = 1,
            ).canApplyAutomatically,
        )
    }

    private fun testTrack(title: String, artist: String, duration: Long) = TrackEntity(
        id = 1, title = title, sortTitle = null, albumId = null, albumArtist = null,
        composer = null, comment = null, grouping = null, durationMs = duration,
        discNumber = null, discTotal = null, trackNumber = null, trackTotal = null,
        year = null, date = null, sampleRate = null, bitRate = null, bitsPerSample = null,
        channels = null, channelLayout = null, codec = null, container = null, lossless = null,
        createdAt = 1, updatedAt = 1, artist = artist,
    )
}
