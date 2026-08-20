package io.github.julystar.musicapp.source.server

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.toStorageRouteIdOrNull
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.PlaylistEntity
import io.github.julystar.musicapp.database.PlaylistTrackCrossRef
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.SourceItemTypes
import io.github.julystar.musicapp.source.api.RemoteServerGateway
import io.github.julystar.musicapp.source.api.RemoteServerKind
import io.github.julystar.musicapp.source.api.RemoteServerPlaylist
import io.github.julystar.musicapp.platform.currentTimeMillis

data class SubsonicRemotePlaylistSyncResult(
    val accountId: SourceAccountId,
    val synced: Int,
    val deleted: Int,
    val unresolvedEntries: Int,
)

class SubsonicRemotePlaylistCoordinator(
    private val database: AppDatabase,
    private val gateway: RemoteServerGateway,
) {
    suspend fun sync(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
    ): SubsonicRemotePlaylistSyncResult {
        require(kind == RemoteServerKind.Navidrome || kind == RemoteServerKind.OpenSubsonic) {
            "remote playlists require a Subsonic server"
        }
        val sourceAccountId = accountId.toStorageRouteIdOrNull()
            ?: error("remote playlist account must use a storage route")
        val providerType = when (kind) {
            RemoteServerKind.Navidrome -> ProviderTypes.Navidrome
            RemoteServerKind.OpenSubsonic -> ProviderTypes.OpenSubsonic
            RemoteServerKind.Emby -> error("Emby playlists are unsupported")
        }
        val summaries = gateway.playlists(kind, accountId).getOrThrow()
        val details = summaries.map { summary ->
            validateIdentity(summary, kind, accountId)
            gateway.playlist(kind, accountId, summary.identity.remotePlaylistId).getOrThrow()
                .also { validateIdentity(it.summary, kind, accountId) }
        }
        val remoteIds = details.map { it.summary.identity.remotePlaylistId }.toSet()
        var unresolved = 0
        var deleted = 0
        val now = currentTimeMillis()
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                val playlistDao = database.playlistDao()
                var nextId = (playlistDao.maxId() ?: 0L) + 1L
                details.forEachIndexed { index, remotePlaylist ->
                    val identity = remotePlaylist.summary.identity
                    val existing = playlistDao.findRemote(
                        providerType, sourceAccountId, identity.remotePlaylistId,
                    )
                    val playlistId = existing?.id ?: nextId++
                    val (trackIds, unresolvedCount) = resolveTrackIds(
                        sourceAccountId = sourceAccountId,
                        remotePlaylist = remotePlaylist,
                    )
                    unresolved += unresolvedCount
                    playlistDao.upsert(
                        PlaylistEntity(
                            id = playlistId,
                            title = remotePlaylist.summary.name,
                            artworkId = null,
                            createdAt = existing?.createdAt ?: now,
                            updatedAt = now,
                            sortOrder = index.toLong(),
                            providerType = providerType,
                            sourceAccountId = sourceAccountId,
                            remotePlaylistId = identity.remotePlaylistId,
                        )
                    )
                    playlistDao.replaceTracks(
                        playlistId,
                        trackIds.mapIndexed { order, trackId ->
                            PlaylistTrackCrossRef(
                                playlistId = playlistId,
                                trackId = trackId,
                                sortOrder = order.toLong(),
                                addedAt = now,
                            )
                        },
                    )
                }
                val stale = playlistDao.listRemoteForAccount(providerType, sourceAccountId)
                    .filter { it.remotePlaylistId !in remoteIds }
                deleted = stale.size
                stale.forEach { playlist ->
                    playlistDao.deleteTracks(playlist.id)
                    playlistDao.delete(playlist.id)
                }
            }
        }
        return SubsonicRemotePlaylistSyncResult(
            accountId = accountId,
            synced = details.size,
            deleted = deleted,
            unresolvedEntries = unresolved,
        )
    }

    private fun validateIdentity(
        summary: io.github.julystar.musicapp.source.api.RemoteServerPlaylistSummary,
        kind: RemoteServerKind,
        accountId: SourceAccountId,
    ) {
        require(summary.identity.kind == kind) { "playlist kind does not match request" }
        require(summary.identity.accountId == accountId) { "playlist account does not match request" }
        require(summary.identity.remotePlaylistId.isNotBlank()) { "playlist id must not be blank" }
    }

    private suspend fun resolveTrackIds(
        sourceAccountId: Long,
        remotePlaylist: RemoteServerPlaylist,
    ): Pair<List<Long>, Int> {
        val remoteIds = remotePlaylist.tracks.map { it.remoteId }.distinct()
        if (remoteIds.isEmpty()) return emptyList<Long>() to 0
        val items = database.sourceItemDao().findByProviderItemIds(sourceAccountId, remoteIds)
            .filter { it.itemType == SourceItemTypes.Track && !it.isDeleted }
            .associateBy { it.providerItemId }
        val refs = database.trackSourceRefDao().findBySourceItemIds(items.values.map { it.id })
            .filter { it.isAvailable && it.playable }
            .associateBy { it.sourceItemId }
        val resolved = remotePlaylist.tracks.mapNotNull { track ->
            val item = items[track.remoteId] ?: return@mapNotNull null
            refs[item.id]?.trackId
        }.distinct()
        val unresolved = remoteIds.count { remoteId ->
            val item = items[remoteId]
            item == null || refs[item.id] == null
        }
        return resolved to unresolved
    }
}
