package io.github.julystar.musicapp.core.data.settings

import io.github.julystar.musicapp.database.TrackDeduplicationCandidate
import io.github.julystar.musicapp.database.TrackDeduplicationSource
import io.github.julystar.musicapp.database.TrackEntity
import io.github.julystar.musicapp.domain.importing.TrackMatchMethods
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackDuplicateMergerTest {
    @Test
    fun strictMetadataMergesMatchingTracksAcrossSources() {
        val plans = buildTrackMergePlans(
            candidates = listOf(candidate(1), candidate(2)),
            sources = listOf(source(1, 10), source(2, 20)),
        )

        assertEquals(1, plans.size)
        assertEquals(1L, plans.single().targetTrackId)
        assertEquals(listOf(2L), plans.single().sourceTrackIds)
        assertEquals(TrackMatchMethods.StrictMetadata, plans.single().matchMethod)
    }

    @Test
    fun doesNotMergeWeakMatchesWithinOneSource() {
        val plans = buildTrackMergePlans(
            candidates = listOf(candidate(1), candidate(2)),
            sources = listOf(source(1, 10), source(2, 10)),
        )

        assertTrue(plans.isEmpty())
    }

    @Test
    fun versionTokensBlockStrictMetadataMerge() {
        val plans = buildTrackMergePlans(
            candidates = listOf(
                candidate(1, title = "Song (Live)"),
                candidate(2, title = "Song (Live)"),
            ),
            sources = listOf(source(1, 10), source(2, 20)),
        )

        assertTrue(plans.isEmpty())
    }

    @Test
    fun incompleteMetadataBlocksStrictMetadataMerge() {
        val plans = buildTrackMergePlans(
            candidates = listOf(
                candidate(1, artist = "", album = ""),
                candidate(2, artist = "", album = ""),
            ),
            sources = listOf(source(1, 10), source(2, 20)),
        )

        assertTrue(plans.isEmpty())
    }

    @Test
    fun strongRecordingIdCannotMergeRemasterVersion() {
        val plans = buildTrackMergePlans(
            candidates = listOf(
                candidate(1, title = "Song", recordingId = "recording-1", lastPlayedAt = 50),
                candidate(
                    id = 2,
                    title = "Song (Remaster)",
                    recordingId = "recording-1",
                    metadataLocked = true,
                ),
            ),
            sources = listOf(source(1, 10), source(2, 20)),
            favoriteTrackIds = setOf(1),
            currentTrackId = 1,
        )

        assertTrue(plans.isEmpty())
    }

    @Test
    fun currentPlayingWinsFavoriteWhenNoLockedMetadataConflicts() {
        val plans = buildTrackMergePlans(
            candidates = listOf(candidate(1), candidate(2)),
            sources = listOf(source(1, 10), source(2, 20)),
            favoriteTrackIds = setOf(1),
            currentTrackId = 2,
        )

        assertEquals(2L, plans.single().targetTrackId)
        assertEquals(listOf(1L), plans.single().sourceTrackIds)
    }

    private fun candidate(
        id: Long,
        title: String = "Song",
        artist: String = "Artist",
        album: String = "Album",
        recordingId: String? = null,
        metadataLocked: Boolean = false,
        lastPlayedAt: Long? = null,
    ) = TrackDeduplicationCandidate(
        track = TrackEntity(
            id = id,
            title = title,
            sortTitle = null,
            albumId = null,
            albumArtist = null,
            composer = null,
            comment = null,
            grouping = null,
            durationMs = 180_000,
            discNumber = null,
            discTotal = null,
            trackNumber = null,
            trackTotal = null,
            year = null,
            date = null,
            sampleRate = null,
            bitRate = null,
            bitsPerSample = null,
            channels = null,
            channelLayout = null,
            codec = null,
            container = null,
            lossless = null,
            createdAt = id,
            updatedAt = id,
            lastPlayedAt = lastPlayedAt,
            artist = artist,
            musicBrainzRecordingId = recordingId,
            metadataLocked = metadataLocked,
        ),
        albumName = album,
    )

    private fun source(
        trackId: Long,
        sourceAccountId: Long,
    ) = TrackDeduplicationSource(
        trackId = trackId,
        sourceAccountId = sourceAccountId,
        contentHash = null,
        audioFingerprint = null,
    )
}
