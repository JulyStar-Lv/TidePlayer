package io.github.julystar.musicapp.database

import androidx.room.Dao
import kotlinx.coroutines.flow.Flow
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.github.julystar.musicapp.metadata.LyricsQualitySelector
import io.github.julystar.musicapp.metadata.TrackIdentityMatcher
import io.github.julystar.musicapp.metadata.TrackIdentitySnapshot
import io.github.julystar.musicapp.metadata.TrackIdentitySource

@Dao
interface TrackDao {
    @Query(
        """
        SELECT t.*
        FROM track t
        WHERE EXISTS (
            SELECT 1 FROM track_source_ref ref
            WHERE ref.trackId = t.id
              AND ref.isAvailable = 1
        )
        ORDER BY t.title COLLATE NOCASE
        """
    )
    fun observeAll(): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT t.*
        FROM track t
        WHERE EXISTS (
            SELECT 1 FROM track_source_ref ref
            WHERE ref.trackId = t.id
              AND ref.isAvailable = 1
        )
        ORDER BY t.title COLLATE NOCASE
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun page(limit: Int, offset: Int): List<TrackEntity>

    @Query("SELECT * FROM track WHERE id = :id")
    suspend fun get(id: Long): TrackEntity?

    @Query(
        """
        SELECT t.*
        FROM track t
        WHERE t.albumId = :albumId
          AND EXISTS (
              SELECT 1 FROM track_source_ref ref
              WHERE ref.trackId = t.id
                AND ref.isAvailable = 1
          )
        ORDER BY t.discNumber ASC, t.trackNumber ASC, t.title COLLATE NOCASE
        """
    )
    suspend fun findByAlbumId(albumId: Long): List<TrackEntity>

    @Query("SELECT * FROM track WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<Long>): List<TrackEntity>

    @Query(
        """
        SELECT t.*
        FROM track t
        WHERE EXISTS (
            SELECT 1 FROM track_source_ref ref
            WHERE ref.trackId = t.id
              AND ref.isAvailable = 1
        )
        ORDER BY t.createdAt DESC LIMIT :limit
        """
    )
    suspend fun findRecentlyAdded(limit: Int): List<TrackEntity>

    @Query(
        """
        SELECT t.*
        FROM track t
        WHERE t.lastPlayedAt IS NOT NULL
          AND EXISTS (
              SELECT 1 FROM track_source_ref ref
              WHERE ref.trackId = t.id
                AND ref.isAvailable = 1
          )
        ORDER BY t.lastPlayedAt DESC LIMIT :limit
        """
    )
    suspend fun findRecentlyPlayed(limit: Int): List<TrackEntity>

    @Query(
        """
        SELECT t.*
        FROM track t
        JOIN track_artist ta ON ta.trackId = t.id
        WHERE ta.artistId = :artistId
          AND EXISTS (
              SELECT 1 FROM track_source_ref ref
              WHERE ref.trackId = t.id
                AND ref.isAvailable = 1
          )
        ORDER BY t.year ASC, t.albumId ASC, t.discNumber ASC, t.trackNumber ASC, t.title COLLATE NOCASE
        """
    )
    suspend fun findTracksByArtistId(artistId: Long): List<TrackEntity>

    @Query(
        """
        SELECT COUNT(*) FROM track t
        WHERE t.albumId = :albumId
          AND EXISTS (
              SELECT 1 FROM track_source_ref ref
              WHERE ref.trackId = t.id
                AND ref.isAvailable = 1
          )
        """
    )
    suspend fun countTracksByAlbumId(albumId: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM track t
        JOIN track_artist ta ON ta.trackId = t.id
        WHERE ta.artistId = :artistId
          AND EXISTS (
              SELECT 1 FROM track_source_ref ref
              WHERE ref.trackId = t.id
                AND ref.isAvailable = 1
          )
        """
    )
    suspend fun countTracksByArtistId(artistId: Long): Int


    @Query(
        """
        SELECT t.*
        FROM track t
        JOIN track_genre tg ON tg.trackId = t.id
        JOIN genre g ON g.id = tg.genreId
        WHERE g.name = :genreName
          AND EXISTS (
              SELECT 1 FROM track_source_ref ref
              WHERE ref.trackId = t.id
                AND ref.isAvailable = 1
          )
        ORDER BY t.title COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun findTracksByGenre(genreName: String, limit: Int): List<TrackEntity>

    @Query(
        """
        SELECT t.*
        FROM track t
        JOIN track_source_ref ref ON ref.trackId = t.id
        WHERE ref.sourceItemId IN (:sourceItemIds)
          AND ref.isAvailable = 1
        """
    )
    suspend fun findBySourceItemIds(sourceItemIds: List<Long>): List<TrackEntity>

    @Query(
        """
        SELECT DISTINCT t.*
        FROM track t
        JOIN track_source_ref ref ON ref.trackId = t.id
        JOIN source_item item ON item.id = ref.sourceItemId
        WHERE item.contentHash = :contentHash
          AND item.contentHash IS NOT NULL
        ORDER BY t.id
        LIMIT 32
        """
    )
    suspend fun findBySourceContentHash(contentHash: String): List<TrackEntity>

    @Query(
        """
        SELECT DISTINCT t.*
        FROM track t
        JOIN track_source_ref ref ON ref.trackId = t.id
        JOIN source_item item ON item.id = ref.sourceItemId
        WHERE item.audioFingerprint = :audioFingerprint
          AND item.audioFingerprint IS NOT NULL
          AND t.durationMs BETWEEN :minDurationMs AND :maxDurationMs
        ORDER BY t.id
        LIMIT 32
        """
    )
    suspend fun findByAudioFingerprintWithinDuration(
        audioFingerprint: String,
        minDurationMs: Long,
        maxDurationMs: Long,
    ): List<TrackEntity>

    @Query(
        """
        SELECT * FROM track
        WHERE musicBrainzRecordingId = :recordingId
          AND musicBrainzRecordingId IS NOT NULL
        ORDER BY id
        LIMIT 32
        """
    )
    suspend fun findByMusicBrainzRecordingId(recordingId: String): List<TrackEntity>

    @Query(
        """
        SELECT * FROM track
        WHERE isrc = :isrc
          AND isrc IS NOT NULL
          AND durationMs BETWEEN :minDurationMs AND :maxDurationMs
        ORDER BY id
        LIMIT 32
        """
    )
    suspend fun findByIsrcWithinDuration(
        isrc: String,
        minDurationMs: Long,
        maxDurationMs: Long,
    ): List<TrackEntity>

    @Query(
        """
        SELECT t.*
        FROM track t
        LEFT JOIN album a ON a.id = t.albumId
        WHERE lower(trim(t.title)) = :titleKey
          AND lower(trim(COALESCE(t.artist, ''))) = :artistKey
          AND lower(trim(COALESCE(a.name, ''))) = :albumKey
          AND t.durationMs BETWEEN :minDurationMs AND :maxDurationMs
        ORDER BY t.id
        LIMIT 32
        """
    )
    suspend fun findByStrictMetadata(
        titleKey: String,
        artistKey: String,
        albumKey: String,
        minDurationMs: Long,
        maxDurationMs: Long,
    ): List<TrackEntity>

    @Query(
        """
        SELECT * FROM track
        WHERE metadataSourceId = :sourceId
          AND metadataExternalId = :externalId
          AND metadataSourceId IS NOT NULL
          AND metadataExternalId IS NOT NULL
        ORDER BY id
        LIMIT 32
        """
    )
    suspend fun findByPluginExternalIdentity(sourceId: String, externalId: String): List<TrackEntity>

    @Query(
        """
        SELECT t.*
        FROM track t
        WHERE EXISTS (
              SELECT 1 FROM track_source_ref ref
              WHERE ref.trackId = t.id
                AND ref.isAvailable = 1
          )
          AND (
              t.title COLLATE NOCASE LIKE :containsQuery ESCAPE '\'
              OR t.artist COLLATE NOCASE LIKE :containsQuery ESCAPE '\'
              OR t.albumArtist COLLATE NOCASE LIKE :containsQuery ESCAPE '\'
              OR t.composer COLLATE NOCASE LIKE :containsQuery ESCAPE '\'
          )
        ORDER BY
          CASE
              WHEN t.title COLLATE NOCASE = :query THEN 0
              WHEN t.title COLLATE NOCASE LIKE :prefixQuery ESCAPE '\' THEN 1
              ELSE 2
          END,
          t.title COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun search(
        query: String,
        prefixQuery: String,
        containsQuery: String,
        limit: Int,
    ): List<TrackEntity>

    @Query(
        """
        SELECT t.*,
               item.id AS sourceItemId,
               item.sourceAccountId AS sourceAccountId,
               COALESCE(item.displayPath, item.canonicalPath) AS resolvedSourcePath,
               a.name AS resolvedAlbum
        FROM track t
        JOIN track_source_ref ref ON ref.trackId = t.id
        JOIN source_item item ON item.id = ref.sourceItemId
        LEFT JOIN album a ON a.id = t.albumId
        WHERE item.sourceAccountId = :sourceAccountId
          AND ref.isAvailable = 1
          AND item.isDeleted = 0
          AND TRIM(COALESCE(item.displayPath, item.canonicalPath, '')) != ''
          AND (
              t.title COLLATE NOCASE LIKE :containsQuery ESCAPE '\'
              OR t.artist COLLATE NOCASE LIKE :containsQuery ESCAPE '\'
              OR t.albumArtist COLLATE NOCASE LIKE :containsQuery ESCAPE '\'
              OR t.composer COLLATE NOCASE LIKE :containsQuery ESCAPE '\'
          )
        ORDER BY
          CASE
              WHEN t.title COLLATE NOCASE = :query THEN 0
              WHEN t.title COLLATE NOCASE LIKE :prefixQuery ESCAPE '\' THEN 1
              ELSE 2
          END,
          t.title COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun searchBySourceStorage(
        sourceAccountId: Long,
        query: String,
        prefixQuery: String,
        containsQuery: String,
        limit: Int,
    ): List<SourceTrackSearchRow>

    @Query(
        """
        SELECT suggestion
        FROM (
            SELECT suggestion, MIN(score) AS bestScore
            FROM (
                SELECT t.title AS suggestion,
                       CASE
                           WHEN t.title COLLATE NOCASE = :query THEN 0
                           WHEN t.title COLLATE NOCASE LIKE :prefixQuery ESCAPE '\' THEN 10
                           ELSE 20
                       END AS score
                FROM track t
                WHERE EXISTS (
                        SELECT 1 FROM track_source_ref ref
                        WHERE ref.trackId = t.id
                          AND ref.isAvailable = 1
                    )
                  AND t.title COLLATE NOCASE LIKE :containsQuery ESCAPE '\'
                UNION ALL
                SELECT t.artist AS suggestion,
                       CASE
                           WHEN t.artist COLLATE NOCASE = :query THEN 1
                           WHEN t.artist COLLATE NOCASE LIKE :prefixQuery ESCAPE '\' THEN 11
                           ELSE 21
                       END AS score
                FROM track t
                WHERE EXISTS (
                        SELECT 1 FROM track_source_ref ref
                        WHERE ref.trackId = t.id
                          AND ref.isAvailable = 1
                    )
                  AND t.artist COLLATE NOCASE LIKE :containsQuery ESCAPE '\'
                UNION ALL
                SELECT t.albumArtist AS suggestion,
                       CASE
                           WHEN t.albumArtist COLLATE NOCASE = :query THEN 2
                           WHEN t.albumArtist COLLATE NOCASE LIKE :prefixQuery ESCAPE '\' THEN 12
                           ELSE 22
                       END AS score
                FROM track t
                WHERE EXISTS (
                        SELECT 1 FROM track_source_ref ref
                        WHERE ref.trackId = t.id
                          AND ref.isAvailable = 1
                    )
                  AND t.albumArtist COLLATE NOCASE LIKE :containsQuery ESCAPE '\'
                UNION ALL
                SELECT t.composer AS suggestion,
                       CASE
                           WHEN t.composer COLLATE NOCASE = :query THEN 3
                           WHEN t.composer COLLATE NOCASE LIKE :prefixQuery ESCAPE '\' THEN 13
                           ELSE 23
                       END AS score
                FROM track t
                WHERE EXISTS (
                        SELECT 1 FROM track_source_ref ref
                        WHERE ref.trackId = t.id
                          AND ref.isAvailable = 1
                    )
                  AND t.composer COLLATE NOCASE LIKE :containsQuery ESCAPE '\'
            ) AS rawSuggestions
            WHERE TRIM(suggestion) != ''
            GROUP BY suggestion COLLATE NOCASE
        ) AS rankedSuggestions
        ORDER BY bestScore, suggestion COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun searchSuggestions(
        query: String,
        prefixQuery: String,
        containsQuery: String,
        limit: Int,
    ): List<String>

    @Query("SELECT COUNT(*) FROM track")
    suspend fun count(): Long

    @Query("SELECT MAX(id) FROM track")
    suspend fun maxId(): Long?

    @Upsert
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Query("UPDATE track SET durationMs = :durationMs, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateDuration(id: Long, durationMs: Long, updatedAt: Long)

    @Query("UPDATE track SET lastPlayedAt = :playedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateLastPlayedAt(id: Long, playedAt: Long, updatedAt: Long)
}

data class SourceTrackSearchRow(
    @Embedded val track: TrackEntity,
    val sourceItemId: Long,
    val sourceAccountId: Long,
    val resolvedSourcePath: String,
    val resolvedAlbum: String?,
)

data class TrackDeduplicationCandidate(
    @Embedded val track: TrackEntity,
    val albumName: String?,
)

data class TrackDeduplicationSource(
    val trackId: Long,
    val sourceAccountId: Long,
    val contentHash: String?,
    val audioFingerprint: String?,
)

data class TrackMergeSourceChoice(
    val trackId: Long,
    val sourceItemId: Long,
    val isPreferred: Boolean,
    val isAvailable: Boolean,
    val isDownloaded: Boolean,
    val playable: Boolean,
    val codec: String?,
    val container: String?,
    val bitRate: Int?,
    val sampleRate: Int?,
    val bitsPerSample: Int?,
    val channels: Int?,
    val channelLayout: String?,
    val lossless: Boolean?,
    val updatedAt: Long,
    val providerType: String,
    val sourcePriority: Int,
    val itemDeleted: Boolean,
    val accountEnabled: Boolean,
)

@Dao
interface TrackMergeDao {
    @Query(
        """
        SELECT t.*, album.name AS albumName
        FROM track t
        LEFT JOIN album ON album.id = t.albumId
        WHERE EXISTS (
            SELECT 1
            FROM track_source_ref ref
            JOIN source_item item ON item.id = ref.sourceItemId
            WHERE ref.trackId = t.id
              AND ref.isAvailable = 1
              AND item.isDeleted = 0
        )
        ORDER BY t.id
        """
    )
    suspend fun listCandidates(): List<TrackDeduplicationCandidate>

    @Query(
        """
        SELECT ref.trackId,
               item.sourceAccountId,
               item.contentHash,
               item.audioFingerprint
        FROM track_source_ref ref
        JOIN source_item item ON item.id = ref.sourceItemId
        WHERE ref.isAvailable = 1
          AND item.isDeleted = 0
        ORDER BY ref.trackId, ref.sourceItemId
        """
    )
    suspend fun listSources(): List<TrackDeduplicationSource>

    @Query(
        """
        SELECT t.*, album.name AS albumName
        FROM track t
        LEFT JOIN album ON album.id = t.albumId
        WHERE t.id = :trackId
        LIMIT 1
        """
    )
    suspend fun getCandidate(trackId: Long): TrackDeduplicationCandidate?

    @Query(
        """
        SELECT ref.trackId,
               item.sourceAccountId,
               item.contentHash,
               item.audioFingerprint
        FROM track_source_ref ref
        JOIN source_item item ON item.id = ref.sourceItemId
        WHERE ref.trackId IN (:trackIds)
          AND ref.isAvailable = 1
          AND item.isDeleted = 0
        ORDER BY ref.trackId, ref.sourceItemId
        """
    )
    suspend fun listSourcesForTracks(trackIds: List<Long>): List<TrackDeduplicationSource>

    suspend fun identitySnapshot(trackId: Long): TrackIdentitySnapshot? {
        val candidate = getCandidate(trackId) ?: return null
        return TrackIdentitySnapshot(
            track = candidate.track,
            albumName = candidate.albumName,
            sources = listSourcesForTracks(listOf(trackId)).map { source ->
                TrackIdentitySource(source.sourceAccountId, source.contentHash, source.audioFingerprint)
            },
        )
    }

    @Query(
        """
        SELECT ref.trackId AS trackId,
               ref.sourceItemId AS sourceItemId,
               ref.isPreferred AS isPreferred,
               ref.isAvailable AS isAvailable,
               ref.isDownloaded AS isDownloaded,
               ref.playable AS playable,
               ref.codec AS codec,
               ref.container AS container,
               ref.bitRate AS bitRate,
               ref.sampleRate AS sampleRate,
               ref.bitsPerSample AS bitsPerSample,
               ref.channels AS channels,
               ref.channelLayout AS channelLayout,
               ref.lossless AS lossless,
               ref.updatedAt AS updatedAt,
               account.providerType AS providerType,
               account.priority AS sourcePriority,
               item.isDeleted AS itemDeleted,
               account.enabled AS accountEnabled
        FROM track_source_ref ref
        JOIN source_item item ON item.id = ref.sourceItemId
        JOIN source_account account ON account.id = item.sourceAccountId
        WHERE ref.trackId IN (:trackIds)
        ORDER BY ref.trackId, ref.sourceItemId
        """
    )
    suspend fun listMergeSourceChoices(trackIds: List<Long>): List<TrackMergeSourceChoice>

    @Upsert
    suspend fun upsertTrack(track: TrackEntity)

    @Query("DELETE FROM track_source_ref WHERE trackId = :trackId")
    suspend fun deleteSourceRefs(trackId: Long)

    @Upsert
    suspend fun upsertSourceRefs(values: List<TrackSourceRefEntity>)

    @Transaction
    suspend fun moveSourceRefs(
        sourceTrackId: Long,
        targetTrackId: Long,
        matchMethod: String,
        matchConfidence: Int,
        now: Long,
    ) {
        val moved = listSourceRefs(sourceTrackId).map { ref ->
            ref.copy(
                trackId = targetTrackId,
                role = "alternate",
                matchMethod = matchMethod,
                matchConfidence = maxOf(ref.matchConfidence, matchConfidence),
                updatedAt = now,
            )
        }
        deleteSourceRefs(sourceTrackId)
        if (moved.isNotEmpty()) upsertSourceRefs(moved)
    }

    @Query("SELECT * FROM playlist_track WHERE trackId = :trackId")
    suspend fun listPlaylistTracks(trackId: Long): List<PlaylistTrackCrossRef>

    @Upsert
    suspend fun upsertPlaylistTracks(tracks: List<PlaylistTrackCrossRef>)

    @Query("DELETE FROM playlist_track WHERE trackId = :sourceTrackId")
    suspend fun deleteRemainingPlaylistTracks(sourceTrackId: Long)

    @Query("UPDATE OR IGNORE track_artist SET trackId = :targetTrackId WHERE trackId = :sourceTrackId")
    suspend fun moveTrackArtists(sourceTrackId: Long, targetTrackId: Long)

    @Query("DELETE FROM track_artist WHERE trackId = :sourceTrackId")
    suspend fun deleteRemainingTrackArtists(sourceTrackId: Long)

    @Query("SELECT * FROM track_artist WHERE trackId = :trackId ORDER BY position, artistId")
    suspend fun listTrackArtists(trackId: Long): List<TrackArtistCrossRef>

    @Query("DELETE FROM track_artist WHERE trackId IN (:trackIds)")
    suspend fun deleteTrackArtists(trackIds: List<Long>)

    @Upsert
    suspend fun upsertTrackArtists(values: List<TrackArtistCrossRef>)

    @Query("UPDATE OR IGNORE track_genre SET trackId = :targetTrackId WHERE trackId = :sourceTrackId")
    suspend fun moveTrackGenres(sourceTrackId: Long, targetTrackId: Long)

    @Query("DELETE FROM track_genre WHERE trackId = :sourceTrackId")
    suspend fun deleteRemainingTrackGenres(sourceTrackId: Long)

    @Query("SELECT * FROM track_genre WHERE trackId = :trackId ORDER BY genreId")
    suspend fun listTrackGenres(trackId: Long): List<TrackGenreCrossRef>

    @Query("DELETE FROM track_genre WHERE trackId IN (:trackIds)")
    suspend fun deleteTrackGenres(trackIds: List<Long>)

    @Upsert
    suspend fun upsertTrackGenres(values: List<TrackGenreCrossRef>)

    @Query("UPDATE OR IGNORE lyrics SET trackId = :targetTrackId WHERE trackId = :sourceTrackId")
    suspend fun moveLyrics(sourceTrackId: Long, targetTrackId: Long)

    @Query("DELETE FROM lyrics WHERE trackId = :sourceTrackId")
    suspend fun deleteRemainingLyrics(sourceTrackId: Long)

    @Query("SELECT * FROM lyrics WHERE trackId = :trackId ORDER BY updatedAt DESC")
    suspend fun listLyrics(trackId: Long): List<LyricsEntity>

    @Query("SELECT * FROM track_source_ref WHERE trackId = :trackId")
    suspend fun listSourceRefs(trackId: Long): List<TrackSourceRefEntity>

    @Query("UPDATE track_source_ref SET isPreferred = 0 WHERE trackId = :trackId")
    suspend fun clearPreferred(trackId: Long)

    @Query("UPDATE track_source_ref SET isPreferred = 1 WHERE trackId = :trackId AND sourceItemId = :sourceItemId")
    suspend fun setPreferred(trackId: Long, sourceItemId: Long)

    @Query("DELETE FROM lyrics WHERE trackId = :trackId")
    suspend fun deleteAllLyrics(trackId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLyrics(values: List<LyricsEntity>)

    @Query("UPDATE raw_metadata SET trackId = :targetTrackId WHERE trackId = :sourceTrackId")
    suspend fun moveRawMetadata(sourceTrackId: Long, targetTrackId: Long)

    @Query("UPDATE artwork SET trackId = :targetTrackId WHERE trackId = :sourceTrackId")
    suspend fun moveArtwork(sourceTrackId: Long, targetTrackId: Long)

    @Query("SELECT * FROM artwork WHERE trackId = :trackId")
    suspend fun listArtwork(trackId: Long): List<ArtworkEntity>

    @Query("DELETE FROM artwork WHERE id = :id")
    suspend fun deleteArtwork(id: Long)

    @Query("UPDATE artwork SET trackId = :targetTrackId WHERE id = :id")
    suspend fun moveArtworkRow(id: Long, targetTrackId: Long)

    @Query("UPDATE listening_history SET trackId = :targetTrackId WHERE trackId = :sourceTrackId")
    suspend fun moveListeningHistory(sourceTrackId: Long, targetTrackId: Long)

    @Query("DELETE FROM track WHERE id = :trackId")
    suspend fun deleteTrack(trackId: Long)

    @Query(
        """
        UPDATE track
        SET lastPlayedAt = :lastPlayedAt,
            updatedAt = :now
        WHERE id = :trackId
        """
    )
    suspend fun updateLastPlayedAt(trackId: Long, lastPlayedAt: Long?, now: Long)

    @Query("INSERT INTO track_fts(track_fts) VALUES('rebuild')")
    suspend fun rebuildTrackFts()

    @Transaction
    suspend fun mergeTracks(
        targetTrackId: Long,
        sourceTrackIds: List<Long>,
        matchMethod: String,
        matchConfidence: Int,
        lastPlayedAt: Long?,
        now: Long,
    ): Boolean {
        val sourceIds = sourceTrackIds.distinct().filterNot { it == targetTrackId }
        if (sourceIds.isEmpty()) return false
        val allIds = listOf(targetTrackId) + sourceIds
        val candidates = allIds.mapNotNull { getCandidate(it) }
        if (candidates.size != allIds.size) return false
        val sources = listSourcesForTracks(allIds)
        val snapshots = candidates.map { candidate ->
            TrackIdentitySnapshot(
                track = candidate.track,
                albumName = candidate.albumName,
                sources = sources.filter { it.trackId == candidate.track.id }.map { source ->
                    TrackIdentitySource(source.sourceAccountId, source.contentHash, source.audioFingerprint)
                },
            )
        }
        if (!TrackIdentityMatcher.pairwiseCompatible(snapshots)) return false
        val target = candidates.single { it.track.id == targetTrackId }.track
        if (!target.metadataLocked && candidates.any { it.track.id != targetTrackId && it.track.metadataLocked }) {
            return false
        }

        val rankedTracks = candidates.map(TrackDeduplicationCandidate::track)
            .sortedWith(trackMetadataWinnerComparator(targetTrackId))
        val artistRefs = rankedTracks.firstNotNullOfOrNull { track ->
            listTrackArtists(track.id).takeIf(List<TrackArtistCrossRef>::isNotEmpty)
        }.orEmpty()
        val genreRefs = rankedTracks.firstNotNullOfOrNull { track ->
            listTrackGenres(track.id).takeIf(List<TrackGenreCrossRef>::isNotEmpty)
        }.orEmpty()
        val mergedLyrics = LyricsQualitySelector.selectBySourceKind(allIds.flatMap { listLyrics(it) })
            .map { lyric -> lyric.copy(id = 0, trackId = targetTrackId) }
        val sourceChoices = listMergeSourceChoices(allIds)
        val preferred = choosePreferredMergeSource(sourceChoices)
        upsertTrack(consolidateMergedTrack(
            rankedTracks = rankedTracks,
            targetTrackId = targetTrackId,
            preferred = preferred,
            requestedLastPlayedAt = lastPlayedAt,
            now = now,
        ))

        sourceIds.forEach { sourceTrackId ->
            moveSourceRefs(sourceTrackId, targetTrackId, matchMethod, matchConfidence, now)
            val targetPlaylistTracks = listPlaylistTracks(targetTrackId).associateBy { it.playlistId }
            val mergedPlaylistTracks = listPlaylistTracks(sourceTrackId).map { sourceTrack ->
                val targetTrack = targetPlaylistTracks[sourceTrack.playlistId]
                if (targetTrack == null) {
                    sourceTrack.copy(trackId = targetTrackId)
                } else {
                    targetTrack.copy(
                        sortOrder = minOf(targetTrack.sortOrder, sourceTrack.sortOrder),
                        addedAt = minOf(targetTrack.addedAt, sourceTrack.addedAt),
                    )
                }
            }
            deleteRemainingPlaylistTracks(sourceTrackId)
            upsertPlaylistTracks(mergedPlaylistTracks)
            moveRawMetadata(sourceTrackId, targetTrackId)
            val targetArtwork = listArtwork(targetTrackId)
            val targetHashes = targetArtwork.mapTo(mutableSetOf(), ArtworkEntity::contentHash)
            listArtwork(sourceTrackId).forEach { artwork ->
                if (artwork.contentHash in targetHashes) deleteArtwork(artwork.id)
                else {
                    moveArtworkRow(artwork.id, targetTrackId)
                    targetHashes += artwork.contentHash
                }
            }
            moveListeningHistory(sourceTrackId, targetTrackId)
            deleteTrack(sourceTrackId)
        }

        deleteTrackArtists(allIds)
        if (artistRefs.isNotEmpty()) {
            upsertTrackArtists(artistRefs.map { it.copy(trackId = targetTrackId) })
        }
        deleteTrackGenres(allIds)
        if (genreRefs.isNotEmpty()) {
            upsertTrackGenres(genreRefs.map { it.copy(trackId = targetTrackId) })
        }
        deleteAllLyrics(targetTrackId)
        if (mergedLyrics.isNotEmpty()) insertLyrics(mergedLyrics)
        clearPreferred(targetTrackId)
        preferred?.let { setPreferred(targetTrackId, it.sourceItemId) }
        rebuildTrackFts()
        return true
    }
}

private fun trackMetadataWinnerComparator(targetTrackId: Long): Comparator<TrackEntity> =
    compareByDescending<TrackEntity> { it.metadataLocked }
        .thenByDescending { metadataSourcePriority(it.metadataSource) }
        .thenByDescending { trackSemanticFieldCount(it) }
        .thenByDescending { it.id == targetTrackId }
        .thenByDescending { it.updatedAt }
        .thenBy { it.id }

private fun metadataSourcePriority(source: String): Int = when (source) {
    TrackMetadataSources.File, TrackMetadataSources.Server -> 3
    TrackMetadataSources.Plugin -> 2
    TrackMetadataSources.Filename -> 1
    else -> 0
}

private fun trackSemanticFieldCount(track: TrackEntity): Int = listOf(
    track.title,
    track.sortTitle,
    track.artist,
    track.albumArtist,
    track.composer,
    track.lyricist,
    track.conductor,
    track.date,
    track.isrc,
    track.musicBrainzRecordingId,
    track.musicBrainzTrackId,
    track.musicBrainzReleaseId,
).count { !it.isNullOrBlank() }

private fun choosePreferredMergeSource(values: List<TrackMergeSourceChoice>): TrackMergeSourceChoice? {
    val playable = values.filter { value ->
        value.isAvailable && value.playable && !value.itemDeleted && value.accountEnabled
    }
    val explicit = playable.filter(TrackMergeSourceChoice::isPreferred)
    if (explicit.size == 1) return explicit.single()
    return (explicit.ifEmpty { playable }).sortedWith(
        compareByDescending<TrackMergeSourceChoice> { it.isDownloaded }
            .thenByDescending { it.providerType == ProviderTypes.Local }
            .thenByDescending { it.lossless ?: false }
            .thenByDescending { it.bitsPerSample ?: 0 }
            .thenByDescending { it.sampleRate ?: 0 }
            .thenByDescending { it.bitRate ?: 0 }
            .thenByDescending { it.sourcePriority }
            .thenByDescending { it.updatedAt }
            .thenBy { it.sourceItemId },
    ).firstOrNull()
}

private fun consolidateMergedTrack(
    rankedTracks: List<TrackEntity>,
    targetTrackId: Long,
    preferred: TrackMergeSourceChoice?,
    requestedLastPlayedAt: Long?,
    now: Long,
): TrackEntity {
    val winner = rankedTracks.first()
    fun text(value: (TrackEntity) -> String?): String? = rankedTracks.firstNotNullOfOrNull { track ->
        value(track)?.trim()?.takeIf(String::isNotEmpty)
    }
    fun <T> value(value: (TrackEntity) -> T?): T? = rankedTracks.firstNotNullOfOrNull(value)
    val target = rankedTracks.single { it.id == targetTrackId }
    val physicalDuration = rankedTracks.firstNotNullOfOrNull { track ->
        track.durationMs.takeIf {
            track.metadataSource == TrackMetadataSources.File || track.metadataSource == TrackMetadataSources.Server
        }
    } ?: target.durationMs ?: value(TrackEntity::durationMs)
    return winner.copy(
        id = targetTrackId,
        title = text(TrackEntity::title) ?: target.title,
        sortTitle = text(TrackEntity::sortTitle),
        albumId = value(TrackEntity::albumId),
        albumArtist = text(TrackEntity::albumArtist),
        composer = text(TrackEntity::composer),
        comment = text(TrackEntity::comment),
        grouping = text(TrackEntity::grouping),
        durationMs = physicalDuration,
        discNumber = value(TrackEntity::discNumber),
        discTotal = value(TrackEntity::discTotal),
        trackNumber = value(TrackEntity::trackNumber),
        trackTotal = value(TrackEntity::trackTotal),
        year = value(TrackEntity::year),
        date = text(TrackEntity::date),
        sampleRate = preferred?.sampleRate ?: winner.sampleRate,
        bitRate = preferred?.bitRate ?: winner.bitRate,
        bitsPerSample = preferred?.bitsPerSample ?: winner.bitsPerSample,
        channels = preferred?.channels ?: winner.channels,
        channelLayout = preferred?.channelLayout ?: winner.channelLayout,
        codec = preferred?.codec ?: winner.codec,
        container = preferred?.container ?: winner.container,
        lossless = preferred?.lossless ?: winner.lossless,
        createdAt = rankedTracks.minOf(TrackEntity::createdAt),
        updatedAt = now,
        lastPlayedAt = (rankedTracks.mapNotNull(TrackEntity::lastPlayedAt) + listOfNotNull(requestedLastPlayedAt)).maxOrNull(),
        artist = text(TrackEntity::artist),
        lyricist = text(TrackEntity::lyricist),
        conductor = text(TrackEntity::conductor),
        copyright = text(TrackEntity::copyright),
        publisher = text(TrackEntity::publisher),
        originalReleaseDate = text(TrackEntity::originalReleaseDate),
        bpm = value(TrackEntity::bpm),
        musicalKey = text(TrackEntity::musicalKey),
        isrc = text(TrackEntity::isrc),
        musicBrainzRecordingId = text(TrackEntity::musicBrainzRecordingId),
        musicBrainzTrackId = text(TrackEntity::musicBrainzTrackId),
        musicBrainzReleaseId = text(TrackEntity::musicBrainzReleaseId),
        musicBrainzReleaseGroupId = text(TrackEntity::musicBrainzReleaseGroupId),
        musicBrainzArtistId = text(TrackEntity::musicBrainzArtistId),
        musicBrainzReleaseArtistId = text(TrackEntity::musicBrainzReleaseArtistId),
        musicBrainzWorkId = text(TrackEntity::musicBrainzWorkId),
        replayGainTrackGain = value(TrackEntity::replayGainTrackGain),
        replayGainTrackPeak = value(TrackEntity::replayGainTrackPeak),
        replayGainAlbumGain = value(TrackEntity::replayGainAlbumGain),
        replayGainAlbumPeak = value(TrackEntity::replayGainAlbumPeak),
        metadataSource = winner.metadataSource,
        metadataLocked = winner.metadataLocked,
        metadataSourceId = winner.metadataSourceId,
        metadataExternalId = winner.metadataExternalId,
        metadataAppliedAt = winner.metadataAppliedAt,
    )
}

data class PlaylistSummaryRow(
    val id: Long,
    val title: String,
    val artworkId: Long?,
    val coverStorageId: Long?,
    val coverPath: String?,
    val createdAt: Long,
    val sortOrder: Long,
    val musicCount: Long,
    val durationMs: Long?,
    val firstTrackId: Long?,
)


data class AlbumSearchRow(
    @Embedded val album: AlbumEntity,
    val trackCount: Long,
)

data class LibraryAlbumRow(
    @Embedded val album: AlbumEntity,
    val artistName: String?,
)

data class ArtistSearchRow(
    @Embedded val artist: ArtistEntity,
    val trackCount: Long,
)

data class PlaylistTrackRow(
    val playlistId: Long,
    val trackId: Long,
    val sortOrder: Long,
    val title: String,
    val artist: String?,
    val albumName: String?,
    val durationMs: Long?,
    val sourceItemId: Long?,
    val sourceAccountId: Long?,
    val sourcePath: String?,
)

@Dao
interface PlaylistDao {
    @Query(
        """
        SELECT p.id, p.title, p.artworkId, p.createdAt, p.sortOrder,
               p.coverStorageId, p.coverPath,
               COUNT(pt.trackId) AS musicCount,
               SUM(t.durationMs) AS durationMs,
               (
                   SELECT first_pt.trackId
                   FROM playlist_track first_pt
                   WHERE first_pt.playlistId = p.id
                   ORDER BY first_pt.sortOrder, first_pt.trackId
                   LIMIT 1
               ) AS firstTrackId
        FROM playlist p
        LEFT JOIN playlist_track pt ON pt.playlistId = p.id
        LEFT JOIN track t ON t.id = pt.trackId
        WHERE p.providerType IS NULL
        GROUP BY p.id
        ORDER BY p.sortOrder, p.id
        """
    )
    fun observeSummaries(): Flow<List<PlaylistSummaryRow>>

    @Query(
        """
        SELECT pt.playlistId, pt.trackId, pt.sortOrder, t.title, t.artist,
               a.name AS albumName, t.durationMs,
               item.id AS sourceItemId,
               item.sourceAccountId AS sourceAccountId,
               COALESCE(item.displayPath, item.canonicalPath) AS sourcePath
        FROM playlist_track pt
        JOIN track t ON t.id = pt.trackId
        LEFT JOIN album a ON a.id = t.albumId
        LEFT JOIN source_item item ON item.id = (
            SELECT candidate.sourceItemId
            FROM track_source_ref candidate
            JOIN source_item candidate_item ON candidate_item.id = candidate.sourceItemId
            JOIN source_account account ON account.id = candidate_item.sourceAccountId
            WHERE candidate.trackId = t.id
              AND candidate.playable = 1
              AND candidate.isAvailable = 1
              AND candidate_item.isDeleted = 0
              AND account.enabled = 1
            ORDER BY
              candidate.isDownloaded DESC,
              CASE WHEN account.providerType = 'local' THEN 1 ELSE 0 END DESC,
              candidate.isPreferred DESC,
              COALESCE(candidate.lossless, 0) DESC,
              COALESCE(candidate.bitsPerSample, 0) DESC,
              COALESCE(candidate.sampleRate, 0) DESC,
              COALESCE(candidate.bitRate, 0) DESC,
              account.priority DESC,
              candidate.updatedAt DESC
            LIMIT 1
        )
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.sortOrder, pt.trackId
        """
    )
    fun observeTracks(playlistId: Long): Flow<List<PlaylistTrackRow>>

    @Query("SELECT * FROM playlist WHERE id = :id")
    suspend fun get(id: Long): PlaylistEntity?

    @Query("SELECT MAX(id) FROM playlist")
    suspend fun maxId(): Long?

    @Query("SELECT * FROM playlist WHERE providerType IS NULL ORDER BY sortOrder, id")
    suspend fun listAll(): List<PlaylistEntity>

    @Query("SELECT MAX(sortOrder) FROM playlist WHERE providerType IS NULL")
    suspend fun maxSortOrder(): Long?

    @Query(
        """
        SELECT * FROM playlist
        WHERE providerType = :providerType
          AND sourceAccountId = :sourceAccountId
        ORDER BY sortOrder, id
        """
    )
    suspend fun listRemoteForAccount(providerType: String, sourceAccountId: Long): List<PlaylistEntity>

    @Query(
        """
        SELECT * FROM playlist
        WHERE providerType = :providerType
          AND sourceAccountId = :sourceAccountId
          AND remotePlaylistId = :remotePlaylistId
        LIMIT 1
        """
    )
    suspend fun findRemote(
        providerType: String,
        sourceAccountId: Long,
        remotePlaylistId: String,
    ): PlaylistEntity?

    @Upsert
    suspend fun upsert(playlist: PlaylistEntity)

    @Upsert
    suspend fun upsertTracks(tracks: List<PlaylistTrackCrossRef>)

    @Query("DELETE FROM playlist_track WHERE playlistId = :playlistId")
    suspend fun deleteTracks(playlistId: Long)

    @Query("DELETE FROM playlist_track WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun deleteTrack(playlistId: Long, trackId: Long)

    @Query("DELETE FROM playlist WHERE id = :id")
    suspend fun delete(id: Long)

    @Transaction
    suspend fun replaceTracks(playlistId: Long, tracks: List<PlaylistTrackCrossRef>) {
        deleteTracks(playlistId)
        upsertTracks(tracks)
    }
}

@Dao
interface MetadataDao {
    @Query(
        """
        SELECT DISTINCT a.*,
               COALESCE(
                   (
                       SELECT GROUP_CONCAT(ar.name, ', ')
                       FROM album_artist aa
                       JOIN artist ar ON ar.id = aa.artistId
                       WHERE aa.albumId = a.id
                   ),
                   (
                       SELECT COALESCE(
                           NULLIF(TRIM(t2.albumArtist), ''),
                           NULLIF(TRIM(t2.artist), ''),
                           NULLIF(TRIM(t2.composer), '')
                       )
                       FROM track t2
                       WHERE t2.albumId = a.id
                         AND COALESCE(
                             NULLIF(TRIM(t2.albumArtist), ''),
                             NULLIF(TRIM(t2.artist), ''),
                             NULLIF(TRIM(t2.composer), '')
                         ) IS NOT NULL
                       ORDER BY t2.discNumber, t2.trackNumber, t2.id
                       LIMIT 1
                   )
               ) AS artistName
        FROM album a
        JOIN track t ON t.albumId = a.id
        WHERE EXISTS (
            SELECT 1 FROM track_source_ref ref
            WHERE ref.trackId = t.id
              AND ref.isAvailable = 1
        )
        ORDER BY a.name COLLATE NOCASE
        """
    )
    fun observeAlbumsWithTracks(): Flow<List<LibraryAlbumRow>>

    @Query(
        """
        SELECT DISTINCT ar.*
        FROM artist ar
        WHERE EXISTS (
            SELECT 1
            FROM track_artist ta
            JOIN track_source_ref ref ON ref.trackId = ta.trackId
            WHERE ta.artistId = ar.id
              AND ref.isAvailable = 1
        ) OR EXISTS (
            SELECT 1
            FROM album_artist aa
            JOIN track t ON t.albumId = aa.albumId
            JOIN track_source_ref ref ON ref.trackId = t.id
            WHERE aa.artistId = ar.id
              AND ref.isAvailable = 1
        )
        ORDER BY ar.name COLLATE NOCASE
        """
    )
    fun observeArtistsWithTracks(): Flow<List<ArtistEntity>>

    @Upsert
    suspend fun upsertAlbums(albums: List<AlbumEntity>): List<Long>

    @Upsert
    suspend fun upsertArtists(artists: List<ArtistEntity>): List<Long>

    @Upsert
    suspend fun upsertGenres(genres: List<GenreEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlbums(albums: List<AlbumEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArtists(artists: List<ArtistEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGenres(genres: List<GenreEntity>): List<Long>

    @Query("SELECT * FROM album WHERE normalizedName IN (:normalizedNames)")
    suspend fun findAlbumsByNormalizedNames(normalizedNames: List<String>): List<AlbumEntity>

    @Query("SELECT * FROM album WHERE id = :id")
    suspend fun getAlbum(id: Long): AlbumEntity?

    @Query("SELECT * FROM artist WHERE normalizedName IN (:normalizedNames)")
    suspend fun findArtistsByNormalizedNames(normalizedNames: List<String>): List<ArtistEntity>

    @Query("SELECT * FROM artist WHERE id = :id")
    suspend fun getArtist(id: Long): ArtistEntity?

    @Query(
        """
        SELECT DISTINCT a.*
        FROM album a
        JOIN album_artist aa ON aa.albumId = a.id
        JOIN track t ON t.albumId = a.id
        WHERE aa.artistId = :artistId
          AND EXISTS (
              SELECT 1 FROM track_source_ref ref
              WHERE ref.trackId = t.id
                AND ref.isAvailable = 1
          )
        ORDER BY a.year ASC, a.name COLLATE NOCASE
        """
    )
    suspend fun albumsByArtistId(artistId: Long): List<AlbumEntity>

    @Query(
        """
        SELECT DISTINCT a.*
        FROM album a
        JOIN track t ON t.albumId = a.id
        WHERE EXISTS (
            SELECT 1 FROM track_source_ref ref
            WHERE ref.trackId = t.id
              AND ref.isAvailable = 1
        )
        ORDER BY a.name COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun listAlbumsWithTracks(limit: Int): List<AlbumEntity>

    @Query(
        """
        SELECT DISTINCT ar.*
        FROM artist ar
        JOIN track_artist ta ON ta.artistId = ar.id
        JOIN track t ON t.id = ta.trackId
        WHERE EXISTS (
            SELECT 1 FROM track_source_ref ref
            WHERE ref.trackId = t.id
              AND ref.isAvailable = 1
        )
        ORDER BY ar.name COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun listArtistsWithTracks(limit: Int): List<ArtistEntity>

    @Query(
        """
        SELECT DISTINCT a.*, COUNT(t.id) AS trackCount
        FROM album a
        JOIN track t ON t.albumId = a.id
        WHERE a.name LIKE :containsQuery ESCAPE '\'
          AND EXISTS (
              SELECT 1 FROM track_source_ref ref
              WHERE ref.trackId = t.id
                AND ref.isAvailable = 1
          )
        GROUP BY a.id
        ORDER BY
          CASE
              WHEN a.name = :query THEN 0
              WHEN a.name LIKE :prefixQuery ESCAPE '\' THEN 1
              ELSE 2
          END,
          a.name COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun searchAlbums(
        query: String,
        prefixQuery: String,
        containsQuery: String,
        limit: Int,
    ): List<AlbumSearchRow>

    @Query(
        """
        SELECT DISTINCT ar.*, COUNT(t.id) AS trackCount
        FROM artist ar
        JOIN track_artist ta ON ta.artistId = ar.id
        JOIN track t ON t.id = ta.trackId
        WHERE ar.name LIKE :containsQuery ESCAPE '\'
          AND EXISTS (
              SELECT 1 FROM track_source_ref ref
              WHERE ref.trackId = t.id
                AND ref.isAvailable = 1
          )
        GROUP BY ar.id
        ORDER BY
          CASE
              WHEN ar.name = :query THEN 0
              WHEN ar.name LIKE :prefixQuery ESCAPE '\' THEN 1
              ELSE 2
          END,
          ar.name COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun searchArtists(
        query: String,
        prefixQuery: String,
        containsQuery: String,
        limit: Int,
    ): List<ArtistSearchRow>


    @Query(
        """
        SELECT DISTINCT g.name
        FROM genre g
        JOIN track_genre tg ON tg.genreId = g.id
        JOIN track t ON t.id = tg.trackId
        WHERE EXISTS (
            SELECT 1 FROM track_source_ref ref
            WHERE ref.trackId = t.id
              AND ref.isAvailable = 1
        )
        ORDER BY g.name COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun listGenreNames(limit: Int): List<String>

    @Query("SELECT * FROM genre WHERE normalizedName IN (:normalizedNames)")
    suspend fun findGenresByNormalizedNames(normalizedNames: List<String>): List<GenreEntity>

    @Upsert
    suspend fun upsertTrackArtists(values: List<TrackArtistCrossRef>)

    @Query("DELETE FROM track_artist WHERE trackId IN (:trackIds)")
    suspend fun deleteTrackArtistsForTracks(trackIds: List<Long>)

    @Query(
        """
        SELECT a.name
        FROM artist a
        JOIN track_artist ta ON ta.artistId = a.id
        WHERE ta.trackId = :trackId
        ORDER BY ta.position
        """
    )
    suspend fun artistNamesForTrack(trackId: Long): List<String>

    @Upsert
    suspend fun upsertAlbumArtists(values: List<AlbumArtistCrossRef>)

    @Query("DELETE FROM album_artist WHERE albumId IN (:albumIds)")
    suspend fun deleteAlbumArtistsForAlbums(albumIds: List<Long>)

    @Query(
        """
        SELECT a.name
        FROM artist a
        JOIN album_artist aa ON aa.artistId = a.id
        WHERE aa.albumId = :albumId
        ORDER BY aa.position
        """
    )
    suspend fun artistNamesForAlbum(albumId: Long): List<String>

    @Upsert
    suspend fun upsertTrackGenres(values: List<TrackGenreCrossRef>)

    @Query("DELETE FROM track_genre WHERE trackId IN (:trackIds)")
    suspend fun deleteTrackGenresForTracks(trackIds: List<Long>)

    @Query(
        """
        SELECT g.name
        FROM genre g
        JOIN track_genre tg ON tg.genreId = g.id
        WHERE tg.trackId = :trackId
        ORDER BY g.name
        """
    )
    suspend fun genreNamesForTrack(trackId: Long): List<String>

    @Upsert
    suspend fun upsertArtwork(values: List<ArtworkEntity>): List<Long>

    @Query(
        """
        SELECT * FROM artwork
        WHERE trackId = :trackId
        ORDER BY
          CASE WHEN trim(localPath) <> '' THEN 1 ELSE 0 END DESC,
          COALESCE(width, 0) * COALESCE(height, 0) DESC,
          CASE WHEN lower(COALESCE(mimeType, '')) LIKE 'image/%' THEN 1 ELSE 0 END DESC,
          CASE WHEN lower(COALESCE(pictureType, '')) IN ('coverfront', 'front cover') THEN 1 ELSE 0 END DESC,
          contentHash ASC,
          id ASC
        LIMIT 1
        """
    )
    suspend fun getArtworkForTrack(trackId: Long): ArtworkEntity?

    @Query(
        """
        SELECT * FROM artwork
        WHERE albumId = :albumId
           OR id = (SELECT artworkId FROM album WHERE id = :albumId)
        ORDER BY CASE
            WHEN id = (SELECT artworkId FROM album WHERE id = :albumId) THEN 0
            ELSE 1
        END, id DESC
        LIMIT 1
        """
    )
    suspend fun getArtworkForAlbum(albumId: Long): ArtworkEntity?

    @Query("SELECT * FROM artwork WHERE contentHash = :contentHash LIMIT 1")
    suspend fun getArtworkByContentHash(contentHash: String): ArtworkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLyrics(values: List<LyricsEntity>)

    @Query("DELETE FROM lyrics WHERE trackId IN (:trackIds)")
    suspend fun deleteLyricsForTracks(trackIds: List<Long>)

    @Query("DELETE FROM lyrics WHERE trackId IN (:trackIds) AND sourceKind LIKE :sourcePrefix || '%'")
    suspend fun deleteLyricsForTracksBySource(trackIds: List<Long>, sourcePrefix: String)

    @Query("SELECT * FROM lyrics WHERE trackId = :trackId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLyrics(trackId: Long): LyricsEntity?

    @Query("SELECT * FROM lyrics WHERE trackId = :trackId ORDER BY updatedAt DESC")
    suspend fun getLyricsCandidates(trackId: Long): List<LyricsEntity>

    @Upsert
    suspend fun upsertRawMetadata(values: List<RawMetadataEntity>)

    @Query("DELETE FROM raw_metadata WHERE trackId IN (:trackIds)")
    suspend fun deleteRawMetadataForTracks(trackIds: List<Long>)

    @Query("SELECT * FROM raw_metadata WHERE trackId = :trackId ORDER BY id")
    suspend fun rawMetadataForTrack(trackId: Long): List<RawMetadataEntity>
}

@Dao
interface SyncDao {
    @Query("DELETE FROM import_job")
    suspend fun deleteAllJobs()

    @Query("SELECT * FROM import_job WHERE status IN ('QUEUED', 'RUNNING', 'PAUSED') ORDER BY createdAt")
    fun observeActiveJobs(): Flow<List<ImportJobEntity>>

    @Query("SELECT * FROM import_job ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecentJobs(limit: Int): Flow<List<ImportJobEntity>>

    @Query(
        """
        SELECT j.*, root.sourceAccountId AS sourceAccountId,
               root.providerRootId AS providerRootId,
               root.canonicalPath AS canonicalPath,
               root.displayName AS displayName
        FROM import_job j
        JOIN library_root root ON root.id = j.libraryRootId
        WHERE j.status IN ('QUEUED', 'RUNNING', 'PAUSED')
        ORDER BY j.createdAt
        """
    )
    fun observeActiveJobsWithFolder(): Flow<List<ImportJobWithFolder>>

    @Query(
        """
        SELECT j.*, root.sourceAccountId AS sourceAccountId,
               root.providerRootId AS providerRootId,
               root.canonicalPath AS canonicalPath,
               root.displayName AS displayName
        FROM import_job j
        JOIN library_root root ON root.id = j.libraryRootId
        ORDER BY j.updatedAt DESC LIMIT :limit
        """
    )
    fun observeRecentJobsWithFolder(limit: Int): Flow<List<ImportJobWithFolder>>

    @Query(
        """
        SELECT j.*, root.sourceAccountId AS sourceAccountId,
               root.providerRootId AS providerRootId,
               root.canonicalPath AS canonicalPath,
               root.displayName AS displayName
        FROM import_job j
        JOIN library_root root ON root.id = j.libraryRootId
        WHERE j.id = :jobId
        """
    )
    suspend fun getJobWithFolder(jobId: String): ImportJobWithFolder?

    @Query(
        """
        SELECT COUNT(*)
        FROM import_job j
        JOIN library_root root ON root.id = j.libraryRootId
        WHERE root.sourceAccountId = :sourceAccountId
          AND j.status IN ('QUEUED', 'RUNNING', 'PAUSED')
          AND (:excludedJobId = '' OR j.id != :excludedJobId)
        """
    )
    suspend fun activeJobCountForStorage(sourceAccountId: Long, excludedJobId: String): Int

    @Query("UPDATE import_job SET status = 'PAUSED', updatedAt = :now WHERE id = :jobId")
    suspend fun markJobPaused(jobId: String, now: Long): Int

    @Query(
        """
        UPDATE library_root
        SET syncStatus = 'PAUSED', lastSyncAt = :now
        WHERE id = (SELECT libraryRootId FROM import_job WHERE id = :jobId)
        """
    )
    suspend fun markSelectedFolderPausedForJob(jobId: String, now: Long): Int

    @Query("UPDATE import_job SET status = 'CANCELLED', updatedAt = :now WHERE id = :jobId")
    suspend fun markJobCancelled(jobId: String, now: Long): Int

    @Query(
        """
        UPDATE library_root
        SET syncStatus = 'IDLE', lastSyncAt = :now
        WHERE id = (SELECT libraryRootId FROM import_job WHERE id = :jobId)
        """
    )
    suspend fun markSelectedFolderCancelledForJob(jobId: String, now: Long): Int

    @Upsert
    suspend fun upsertJob(job: ImportJobEntity)

    @Upsert
    suspend fun upsertCursor(cursor: SourceSyncCursorEntity)

    @Query(
        """
        SELECT * FROM source_sync_cursor
        WHERE libraryRootId = :libraryRootId
          AND cursorType = :cursorType
        LIMIT 1
        """
    )
    suspend fun getCursor(
        libraryRootId: Long,
        cursorType: String = "delta",
    ): SourceSyncCursorEntity?

    @Query(
        """
        DELETE FROM source_sync_cursor
        WHERE libraryRootId = :libraryRootId
          AND cursorType = :cursorType
        """
    )
    suspend fun deleteCursor(libraryRootId: Long, cursorType: String)
}

@Dao
interface DownloadTaskDao {
    @Query("SELECT * FROM download_task ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_task WHERE status IN ('Queued', 'Resolving', 'Downloading', 'Finalizing', 'Paused') ORDER BY updatedAt")
    fun observeActive(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_task WHERE id = :id")
    fun observe(id: String): Flow<DownloadTaskEntity?>

    @Query("SELECT * FROM download_task WHERE id = :id")
    suspend fun get(id: String): DownloadTaskEntity?

    @Upsert
    suspend fun upsert(task: DownloadTaskEntity)

    @Query("DELETE FROM download_task WHERE id = :id")
    suspend fun delete(id: String)
}
