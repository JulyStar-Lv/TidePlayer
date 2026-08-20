package io.github.julystar.musicapp.source.server

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.AppDatabaseConstructor
import io.github.julystar.musicapp.database.PlaylistEntity
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.SourceAccountEntity
import io.github.julystar.musicapp.database.SourceItemEntity
import io.github.julystar.musicapp.database.SourceItemTypes
import io.github.julystar.musicapp.database.TrackEntity
import io.github.julystar.musicapp.database.TrackSourceRefEntity
import io.github.julystar.musicapp.source.api.PlaybackResource
import io.github.julystar.musicapp.source.api.RemoteServerGateway
import io.github.julystar.musicapp.source.api.RemoteServerKind
import io.github.julystar.musicapp.source.api.RemoteServerLyrics
import io.github.julystar.musicapp.source.api.RemoteServerPlaylist
import io.github.julystar.musicapp.source.api.RemoteServerPlaylistIdentity
import io.github.julystar.musicapp.source.api.RemoteServerPlaylistSummary
import io.github.julystar.musicapp.source.api.RemoteServerScrobble
import io.github.julystar.musicapp.source.api.RemoteServerSourceConfiguration
import io.github.julystar.musicapp.source.api.RemoteServerTrack
import io.github.julystar.musicapp.source.api.RemoteServerTrackPage
import io.github.julystar.musicapp.source.api.SourceAuthResult
import io.github.julystar.musicapp.source.api.SourcePlaybackFailureReason
import io.github.julystar.musicapp.source.api.SourcePlaybackResult
import io.github.julystar.musicapp.source.api.OpenSubsonicCapabilitySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SubsonicRemotePlaylistCoordinatorIntegrationTest {
    @Test
    fun accountsWithSameRemoteIdentityRemainIsolatedAndMissingMirrorIsDeleted() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder<AppDatabase> {
            AppDatabaseConstructor.initialize()
        }.setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.Default).build()
        try {
            seed(database)
            val gateway = PlaylistGateway()
            val coordinator = SubsonicRemotePlaylistCoordinator(database, gateway)
            coordinator.sync(RemoteServerKind.Navidrome, SourceAccountId("storage:1"))
            coordinator.sync(RemoteServerKind.Navidrome, SourceAccountId("storage:2"))

            assertEquals(1, database.playlistDao().listRemoteForAccount(ProviderTypes.Navidrome, 1).size)
            assertEquals(1, database.playlistDao().listRemoteForAccount(ProviderTypes.Navidrome, 2).size)
            val accountOnePlaylist = database.playlistDao().listRemoteForAccount(ProviderTypes.Navidrome, 1).single()
            val accountTwoPlaylist = database.playlistDao().listRemoteForAccount(ProviderTypes.Navidrome, 2).single()
            assertEquals(listOf(101L, 102L), database.playlistDao().observeTracks(
                accountOnePlaylist.id,
            ).first().map { it.trackId })
            assertEquals(listOf(201L, 202L), database.playlistDao().observeTracks(
                accountTwoPlaylist.id,
            ).first().map { it.trackId })
            assertEquals(listOf(1L, 1L), database.playlistDao().observeTracks(
                accountOnePlaylist.id,
            ).first().map { it.sourceAccountId })
            assertEquals(listOf(2L, 2L), database.playlistDao().observeTracks(
                accountTwoPlaylist.id,
            ).first().map { it.sourceAccountId })
            assertEquals(listOf(99L), database.playlistDao().observeSummaries().first().map { it.id })
            assertEquals(2, database.playlistDao().observeTracks(
                accountOnePlaylist.id,
            ).first().size)
            assertEquals(1, database.playlistDao().listAll().count { it.providerType == null })

            val snapshotPlaylist = database.playlistDao().get(accountOnePlaylist.id)
            val snapshotTracks = database.playlistDao().observeTracks(accountOnePlaylist.id).first()
            gateway.identityOverride = RemoteServerPlaylistIdentity(
                RemoteServerKind.Navidrome, SourceAccountId("storage:2"), "playlist|1",
            )
            assertFailsWith<IllegalArgumentException> {
                coordinator.sync(RemoteServerKind.Navidrome, SourceAccountId("storage:1"))
            }
            assertEquals(snapshotPlaylist, database.playlistDao().get(accountOnePlaylist.id))
            assertEquals(snapshotTracks, database.playlistDao().observeTracks(accountOnePlaylist.id).first())

            gateway.summariesByAccount[1] = emptyList()
            val deleted = coordinator.sync(RemoteServerKind.Navidrome, SourceAccountId("storage:1"))
            assertEquals(1, deleted.deleted)
            assertTrue(database.playlistDao().listRemoteForAccount(ProviderTypes.Navidrome, 1).isEmpty())
            assertEquals(1, database.playlistDao().listRemoteForAccount(ProviderTypes.Navidrome, 2).size)
        } finally {
            database.close()
        }
    }

    private suspend fun seed(database: AppDatabase) {
        database.sourceAccountDao().upsert(SourceAccountEntity(1, ProviderTypes.Navidrome, "A", "https://a", null, "cred-a", 0, true, 1, 1))
        database.sourceAccountDao().upsert(SourceAccountEntity(2, ProviderTypes.Navidrome, "B", "https://b", null, "cred-b", 0, true, 1, 1))
        database.trackDao().upsertAll(listOf(track(101), track(102), track(201), track(202)))
        database.sourceItemDao().upsertAll(listOf(
            item(11, 1, "shared-song-1"), item(12, 1, "shared-song-2"),
            item(21, 2, "shared-song-1"), item(22, 2, "shared-song-2"),
        ))
        database.trackSourceRefDao().upsertAll(listOf(ref(101, 11), ref(102, 12), ref(201, 21), ref(202, 22)))
        database.playlistDao().upsert(PlaylistEntity(99, "Local", null, createdAt = 1, updatedAt = 1, sortOrder = 0))
    }

    private fun track(id: Long) = TrackEntity(
        id = id, title = "Track $id", sortTitle = null, albumId = null, albumArtist = null,
        composer = null, comment = null, grouping = null, durationMs = 1_000, discNumber = null,
        discTotal = null, trackNumber = null, trackTotal = null, year = null, date = null,
        sampleRate = null, bitRate = null, bitsPerSample = null, channels = null, channelLayout = null,
        codec = null, container = null, lossless = null, createdAt = 1, updatedAt = 1,
    )

    private fun item(id: Long, accountId: Long, providerItemId: String) = SourceItemEntity(
        id = id, sourceAccountId = accountId, libraryRootId = null, itemType = SourceItemTypes.Track,
        providerItemId = providerItemId, parentProviderItemId = null, canonicalPath = null,
        displayPath = null, displayName = "Song", mimeType = "audio/flac", sizeBytes = null,
        etag = null, revision = null, createdAtRemote = null, modifiedAtRemote = null,
        contentHash = null, audioFingerprint = null, isDeleted = false, firstSyncedAt = 1,
        lastSyncedAt = 1, lastSeenScanId = "scan",
    )

    private fun ref(trackId: Long, itemId: Long) = TrackSourceRefEntity(
        trackId = trackId, sourceItemId = itemId, role = "primary", matchMethod = "source",
        matchConfidence = 100, isPreferred = true, isAvailable = true, isDownloaded = false,
        playable = true, downloadable = true, codec = null, container = null, bitRate = null,
        sampleRate = null, bitsPerSample = null, channels = null, lossless = null,
        createdAt = 1, updatedAt = 1,
    )
}

private class PlaylistGateway : RemoteServerGateway {
    val summariesByAccount = mutableMapOf<Int, List<RemoteServerPlaylistSummary>>()
    var identityOverride: RemoteServerPlaylistIdentity? = null

    fun summary(account: Int) = RemoteServerPlaylistSummary(
        RemoteServerPlaylistIdentity(RemoteServerKind.Navidrome, SourceAccountId("storage:$account"), "playlist|1"),
        "Shared",
    )

    override suspend fun authenticate(configuration: RemoteServerSourceConfiguration) = SourceAuthResult.Success

    override fun trackPages(kind: RemoteServerKind, accountId: SourceAccountId, query: String?, pageSize: Int): Flow<Result<RemoteServerTrackPage>> = emptyFlow()

    override suspend fun playback(kind: RemoteServerKind, encodedRemoteId: String) = SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unavailable)

    override suspend fun playlists(kind: RemoteServerKind, accountId: SourceAccountId): Result<List<RemoteServerPlaylistSummary>> {
        val account = accountId.value.substringAfter(':').toInt()
        return Result.success(summariesByAccount.getOrPut(account) { listOf(summary(account)) })
    }

    override suspend fun playlist(kind: RemoteServerKind, accountId: SourceAccountId, remotePlaylistId: String): Result<RemoteServerPlaylist> {
        val identity = identityOverride ?: RemoteServerPlaylistIdentity(kind, accountId, remotePlaylistId)
        identityOverride = null
        val account = accountId.value.substringAfter(':').toInt()
        return Result.success(
            RemoteServerPlaylist(
                RemoteServerPlaylistSummary(identity, "Shared"),
                listOf(
                    RemoteServerTrack(accountId, "shared-song-1", "Song 1", streamUrl = "https://server/$account/1"),
                    RemoteServerTrack(accountId, "shared-song-2", "Song 2", streamUrl = "https://server/$account/2"),
                ),
            )
        )
    }
}
