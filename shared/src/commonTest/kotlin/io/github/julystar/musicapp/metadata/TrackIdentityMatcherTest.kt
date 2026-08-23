package io.github.julystar.musicapp.metadata

import io.github.julystar.musicapp.database.LyricsEntity
import io.github.julystar.musicapp.database.TrackEntity
import io.github.julystar.musicapp.core.data.datastore.TrackIdRemapper
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrackIdentityMatcherTest {
    @Test
    fun sameAlbumStrongEvidenceMergesButDifferentReleaseDoesNot() {
        val first = snapshot(1, albumId = 10, recording = "rec", album = "Album")
        val sameAlbum = snapshot(2, albumId = 10, recording = "rec", album = "Album")
        val otherAlbum = snapshot(3, albumId = 11, recording = "rec", album = "Other")
        assertEquals(TrackIdentityRelation.SameLibraryTrack, TrackIdentityMatcher.match(first, sameAlbum).relation)
        assertEquals(TrackIdentityRelation.SameRecordingDifferentRelease, TrackIdentityMatcher.match(first, otherAlbum).relation)
    }

    @Test
    fun releaseConflictWithoutRecordingEvidenceIsOnlyUncertain() {
        val first = snapshot(1, albumId = 10, album = "Release A")
        val second = snapshot(2, albumId = 11, album = "Release B")
        assertEquals(TrackIdentityRelation.Uncertain, TrackIdentityMatcher.match(first, second).relation)
    }

    @Test
    fun versionConflictAndBridgeAreVetoedPairwise() {
        val plain = snapshot(1, hash = "h", title = "Song")
        val remaster = snapshot(2, hash = "h", title = "Song (Remaster)")
        assertEquals(TrackIdentityRelation.DifferentVersion, TrackIdentityMatcher.match(plain, remaster).relation)
        assertFalse(TrackIdentityMatcher.pairwiseCompatible(listOf(plain, remaster)))
    }

    @Test
    fun everyProtectedVersionTokenVetoesStrongEvidence() {
        listOf(
            "Live", "Remix", "Acoustic", "Instrumental", "Demo", "Radio Edit",
            "Extended Mix", "Karaoke", "Cover", "Re-recorded", "Alternate Take",
            "Mono", "Stereo", "2011 Remaster",
        ).forEach { version ->
            val plain = snapshot(1, hash = "same", title = "Song")
            val variant = snapshot(2, hash = "same", title = "Song ($version)")
            assertEquals(
                TrackIdentityRelation.DifferentVersion,
                TrackIdentityMatcher.match(plain, variant).relation,
                version,
            )
        }
    }

    @Test
    fun strongIdConflictsAndReleaseFieldsVetoMerge() {
        val first = snapshot(
            1, hash = "same", recording = "recording-a", isrc = "isrc-a",
            releaseId = "release-a", releaseGroupId = "group-a", mbTrackId = "track-a",
            albumArtist = "Album Artist", discNumber = 1, trackNumber = 1, date = "2019",
        )
        assertFalse(TrackIdentityMatcher.match(first, snapshot(2, hash = "same", recording = "recording-b")).canMerge)
        assertFalse(TrackIdentityMatcher.match(first, snapshot(2, hash = "same", isrc = "isrc-b")).canMerge)
        assertFalse(TrackIdentityMatcher.match(first, snapshot(2, hash = "same", releaseId = "release-b")).canMerge)
        assertFalse(TrackIdentityMatcher.match(first, snapshot(2, hash = "same", releaseGroupId = "group-b")).canMerge)
        assertFalse(TrackIdentityMatcher.match(first, snapshot(2, hash = "same", mbTrackId = "track-b")).canMerge)
        assertFalse(TrackIdentityMatcher.match(first, snapshot(2, hash = "same", albumArtist = "Other")).canMerge)
        assertFalse(TrackIdentityMatcher.match(first, snapshot(2, hash = "same", discNumber = 2)).canMerge)
        assertFalse(TrackIdentityMatcher.match(first, snapshot(2, hash = "same", trackNumber = 2)).canMerge)
        assertFalse(TrackIdentityMatcher.match(first, snapshot(2, hash = "same", date = "2020")).canMerge)
    }

    @Test
    fun fingerprintIsrcAndStrictMetadataRequireBothDurations() {
        val fingerprint = snapshot(1, fingerprint = "fp", duration = null)
        assertFalse(TrackIdentityMatcher.match(fingerprint, snapshot(2, fingerprint = "fp")).canMerge)
        val isrc = snapshot(1, isrc = "X", duration = null)
        assertFalse(TrackIdentityMatcher.match(isrc, snapshot(2, isrc = "X")).canMerge)
        val strict = snapshot(1, sourceAccountId = 10, duration = null)
        assertFalse(TrackIdentityMatcher.match(strict, snapshot(2, sourceAccountId = 20)).canMerge)
    }

    @Test
    fun sameSourceHashAndFingerprintCanMergeButMetadataOnlyCannot() {
        assertTrue(TrackIdentityMatcher.match(
            snapshot(1, hash = "hash", sourceAccountId = 10),
            snapshot(2, hash = "hash", sourceAccountId = 10),
        ).canMerge)
        assertTrue(TrackIdentityMatcher.match(
            snapshot(1, fingerprint = "fp", sourceAccountId = 10),
            snapshot(2, fingerprint = "fp", sourceAccountId = 10),
        ).canMerge)
        assertFalse(TrackIdentityMatcher.match(
            snapshot(1, sourceAccountId = 10),
            snapshot(2, sourceAccountId = 10),
        ).canMerge)
    }

    @Test
    fun transitiveBridgeRequiresPairwiseCompatibility() {
        val a = snapshot(1, recording = "recording-a", hashes = listOf("ab"))
        val b = snapshot(2, hashes = listOf("ab", "bc"))
        val c = snapshot(3, recording = "recording-c", hashes = listOf("bc"))
        assertTrue(TrackIdentityMatcher.match(a, b).canMerge)
        assertTrue(TrackIdentityMatcher.match(b, c).canMerge)
        assertFalse(TrackIdentityMatcher.match(a, c).canMerge)
        assertFalse(TrackIdentityMatcher.pairwiseCompatible(listOf(a, b, c)))
    }

    @Test
    fun currentPlayingWinsFavoriteUnlessLockedConflictRequiresSkip() {
        val favorite = snapshot(1)
        val current = snapshot(2)
        assertEquals(2, selectCanonicalTrackId(listOf(favorite, current), setOf(1), 2))
        val lockedFavorite = favorite.copy(track = favorite.track.copy(metadataLocked = true))
        assertEquals(null, selectCanonicalTrackId(listOf(lockedFavorite, current), setOf(1), 2))
    }

    @Test
    fun incrementalProviderUsesOnlyFiniteEvidenceQueriesIncludingStrictMetadata() = runTest {
        val changed = snapshot(
            1,
            hash = "hash",
            fingerprint = "fingerprint",
            recording = "recording",
            isrc = "isrc",
            sourceAccountId = 10,
        ).copy(track = snapshot(1).track.copy(
            musicBrainzRecordingId = "recording",
            isrc = "isrc",
            metadataSourceId = "plugin",
            metadataExternalId = "external",
        ))
        val returned = (2L..5L).map { id -> snapshot(id).track }
        val calls = mutableListOf<String>()
        val queries = object : FiniteTrackIdentityCandidateQueries {
            override suspend fun findBySourceContentHash(contentHash: String) = returned.also { calls += "hash" }
            override suspend fun findByAudioFingerprintWithinDuration(
                audioFingerprint: String,
                minDurationMs: Long,
                maxDurationMs: Long,
            ) = returned.also { calls += "fingerprint" }
            override suspend fun findByMusicBrainzRecordingId(recordingId: String) = returned.also { calls += "recording" }
            override suspend fun findByIsrcWithinDuration(isrc: String, minDurationMs: Long, maxDurationMs: Long) =
                returned.also { calls += "isrc" }
            override suspend fun findByPluginExternalIdentity(sourceId: String, externalId: String) =
                returned.also { calls += "plugin" }
            override suspend fun findByStrictMetadata(
                titleKey: String,
                artistKey: String,
                albumKey: String,
                minDurationMs: Long,
                maxDurationMs: Long,
            ) = returned.also { calls += "strict" }
        }

        val candidates = TrackDaoIdentityCandidateProvider(queries) { track ->
            if (track.id == changed.track.id) changed else TrackIdentitySnapshot(track, albumName = "Album")
        }.candidates(changed.track)

        assertEquals(listOf(2L, 3L, 4L, 5L), candidates.map { it.track.id })
        assertEquals(setOf("hash", "fingerprint", "recording", "isrc", "plugin", "strict"), calls.toSet())
    }

    @Test
    fun precommitCrashLeavesRetryableDuplicateAndConcurrentMergeExecutesOnce() = runTest {
        val remaps = mutableListOf<Map<Long, Long>>()
        var roomAvailable = false
        var sourceExists = true
        var effectiveMerges = 0
        val executor = SafeTrackIdentityMergeExecutor(
            precommitRemap = { remaps += it },
            roomMerge = { _, _ ->
                if (!roomAvailable) error("simulated crash before Room commit")
                if (sourceExists) {
                    sourceExists = false
                    effectiveMerges++
                    true
                } else {
                    false
                }
            },
        )
        val request = TrackIdentityMergeRequest(1, listOf(2), "hash", 100, null)
        runCatching { executor.execute(request) }
        assertTrue(sourceExists)
        assertEquals(mapOf(2L to 1L), remaps.single())

        roomAvailable = true
        coroutineScope { listOf(async { executor.execute(request) }, async { executor.execute(request) }).awaitAll() }
        assertEquals(1, effectiveMerges)
        assertFalse(sourceExists)
    }

    @Test
    fun replacementChainPreservesQueueOccurrences() {
        val replacements = mapOf(3L to 2L, 2L to 1L)
        assertEquals(1L, TrackIdRemapper.resolve(3, replacements))
        assertEquals(listOf(1L, 1L, 4L), listOf(3L, 2L, 4L).map { TrackIdRemapper.resolve(it, replacements) })
    }

    @Test
    fun lyricsQualitySelectsTtmlOverPlainForSameSourceKind() {
        val plain = lyrics(1, "plain", "plain", 10)
        val ttml = lyrics(2, "ttml", "<tt>timed</tt>", 1)
        assertEquals(ttml.id, LyricsQualitySelector.select(plain, ttml)?.id)
    }

    @Test
    fun lyricsQualityUsesTranslationRomanizationAndKeepsDifferentKinds() {
        val sparse = lyrics(1, "ttml", "<tt>lyrics</tt>", 20).copy(structuredContent = "{\"lines\":[]}")
        val complete = lyrics(2, "ttml", "<tt>lyrics</tt>", 10).copy(
            structuredContent = "{\"translation\":\"translated\",\"romanization\":\"romanized\"}",
        )
        assertEquals(complete.id, LyricsQualitySelector.select(sparse, complete)?.id)
        val embedded = lyrics(3, "plain", "embedded", 1).copy(sourceKind = "EmbeddedPlain")
        assertEquals(2, LyricsQualitySelector.selectBySourceKind(listOf(complete, embedded)).size)
    }

    private fun snapshot(
        id: Long,
        albumId: Long? = null,
        recording: String? = null,
        album: String? = "Album",
        hash: String? = null,
        hashes: List<String> = listOfNotNull(hash),
        fingerprint: String? = null,
        title: String = "Song",
        duration: Long? = 180_000,
        isrc: String? = null,
        releaseId: String? = null,
        releaseGroupId: String? = null,
        mbTrackId: String? = null,
        albumArtist: String? = null,
        discNumber: Int? = null,
        trackNumber: Int? = null,
        date: String? = null,
        sourceAccountId: Long? = null,
    ) = TrackIdentitySnapshot(
        track = TrackEntity(
            id = id, title = title, sortTitle = null, albumId = albumId, albumArtist = albumArtist,
            composer = null, comment = null, grouping = null, durationMs = duration,
            discNumber = discNumber, discTotal = null, trackNumber = trackNumber, trackTotal = null,
            year = null, date = date, sampleRate = null, bitRate = null, bitsPerSample = null,
            channels = null, channelLayout = null, codec = null, container = null, lossless = null,
            createdAt = id, updatedAt = id, artist = "Artist", isrc = isrc,
            musicBrainzRecordingId = recording, musicBrainzTrackId = mbTrackId,
            musicBrainzReleaseId = releaseId, musicBrainzReleaseGroupId = releaseGroupId,
        ),
        albumName = album,
        sources = hashes.map { value ->
            TrackIdentitySource(sourceAccountId = sourceAccountId, contentHash = value, audioFingerprint = fingerprint)
        }.ifEmpty {
            listOf(TrackIdentitySource(sourceAccountId = sourceAccountId, audioFingerprint = fingerprint))
        },
    )

    private fun lyrics(id: Long, kind: String, content: String, updated: Long) = LyricsEntity(
        id = id, trackId = 1, format = kind, language = null, synchronized = kind == "ttml",
        content = content, sourcePath = null, updatedAt = updated, sourceKind = "External",
    )
}
