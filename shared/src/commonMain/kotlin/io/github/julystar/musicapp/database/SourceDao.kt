package io.github.julystar.musicapp.database

import androidx.room.Embedded
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceAccountDao {
    @Query("SELECT * FROM source_account ORDER BY displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<SourceAccountEntity>>

    @Query(
        """
        SELECT account.*,
               COUNT(DISTINCT CASE
                   WHEN item.itemType = 'track'
                    AND item.isDeleted = 0
                    AND ref.isAvailable = 1
                   THEN ref.trackId
               END) AS trackCount,
               MAX(root.lastSyncAt) AS lastScanAt,
               (
                   SELECT latest.syncStatus
                   FROM library_root latest
                   WHERE latest.sourceAccountId = account.id
                   ORDER BY latest.lastSyncAt DESC, latest.updatedAt DESC
                   LIMIT 1
               ) AS lastScanStatus
        FROM source_account account
        LEFT JOIN library_root root ON root.sourceAccountId = account.id
        LEFT JOIN source_item item ON item.sourceAccountId = account.id
        LEFT JOIN track_source_ref ref ON ref.sourceItemId = item.id
        GROUP BY account.id
        ORDER BY account.displayName COLLATE NOCASE
        """
    )
    fun observeSummaries(): Flow<List<SourceAccountSummaryRow>>

    @Query("SELECT * FROM source_account WHERE id = :id")
    suspend fun get(id: Long): SourceAccountEntity?

    @Query("SELECT MAX(id) FROM source_account")
    suspend fun maxId(): Long?

    @Query("SELECT * FROM source_account ORDER BY priority DESC, displayName COLLATE NOCASE")
    suspend fun listAll(): List<SourceAccountEntity>

    @Upsert
    suspend fun upsert(account: SourceAccountEntity): Long

    @Query(
        """
        UPDATE source_account
        SET enabled = :enabled,
            updatedAt = :updatedAt
        WHERE providerType = :providerType
        """
    )
    suspend fun setEnabledByProviderType(
        providerType: String,
        enabled: Boolean,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE source_account
        SET enabled = :enabled,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun setEnabled(id: Long, enabled: Boolean, updatedAt: Long)

    @Query(
        """
        UPDATE source_account
        SET enabled = 0,
            updatedAt = :updatedAt
        WHERE providerType != 'local'
        """
    )
    suspend fun disableRemoteSources(updatedAt: Long)

    @Query(
        """
        UPDATE source_account
        SET rootPath = :rootPath,
            updatedAt = :updatedAt
        WHERE id = :id AND providerType = 'webdav'
        """
    )
    suspend fun setRootPath(id: Long, rootPath: String, updatedAt: Long)

    @Query("DELETE FROM source_account WHERE id = :id")
    suspend fun delete(id: Long)
}

data class SourceAccountSummaryRow(
    @Embedded val account: SourceAccountEntity,
    val trackCount: Long,
    val lastScanAt: Long?,
    val lastScanStatus: String?,
)

@Dao
interface LibraryRootDao {
    @Query(
        """
        UPDATE library_root
        SET syncStatus = 'PENDING', syncCursor = NULL, lastSyncAt = NULL
        """
    )
    suspend fun resetSyncState()

    @Query("SELECT * FROM library_root WHERE id = :id")
    suspend fun get(id: Long): LibraryRootEntity?

    @Query("SELECT * FROM library_root ORDER BY sourceAccountId, id")
    suspend fun listAll(): List<LibraryRootEntity>

    @Query(
        """
        SELECT * FROM library_root
        WHERE sourceAccountId = :sourceAccountId
          AND canonicalPath = :canonicalPath
        LIMIT 1
        """
    )
    suspend fun findByPath(sourceAccountId: Long, canonicalPath: String): LibraryRootEntity?

    @Query(
        """
        SELECT * FROM library_root
        WHERE sourceAccountId = :sourceAccountId
          AND providerRootId = :providerRootId
        LIMIT 1
        """
    )
    suspend fun findByProviderRootId(sourceAccountId: Long, providerRootId: String): LibraryRootEntity?

    @Query(
        """
        SELECT canonicalPath FROM library_root
        WHERE sourceAccountId = :sourceAccountId
        ORDER BY id
        """
    )
    suspend fun listCanonicalPaths(sourceAccountId: Long): List<String>

    @Query(
        """
        SELECT * FROM library_root
        WHERE sourceAccountId = :sourceAccountId
        ORDER BY displayName COLLATE NOCASE
        """
    )
    fun observeBySourceAccount(sourceAccountId: Long): Flow<List<LibraryRootEntity>>

    @Upsert
    suspend fun upsert(root: LibraryRootEntity): Long

    @Query("DELETE FROM library_root WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SourceItemDao {
    @Query("DELETE FROM source_item")
    suspend fun deleteAll()

    @Query("SELECT * FROM source_item WHERE id = :id")
    suspend fun get(id: Long): SourceItemEntity?

    @Query("SELECT COUNT(*) FROM source_item WHERE libraryRootId = :libraryRootId")
    suspend fun countForLibraryRoot(libraryRootId: Long): Long

    @Query(
        """
        SELECT id,
               providerItemId,
               canonicalPath,
               sizeBytes,
               etag,
               revision,
               modifiedAtRemote,
               isDeleted
        FROM source_item
        WHERE libraryRootId = :libraryRootId
          AND itemType = 'track'
          AND isDeleted = 0
        """
    )
    suspend fun signaturesForLibraryRoot(libraryRootId: Long): List<SourceItemSignature>

    @Query(
        """
        SELECT * FROM source_item
        WHERE sourceAccountId = :sourceAccountId
          AND canonicalPath = :canonicalPath
        LIMIT 1
        """
    )
    suspend fun findByPath(sourceAccountId: Long, canonicalPath: String): SourceItemEntity?

    @Query(
        """
        SELECT * FROM source_item
        WHERE sourceAccountId = :sourceAccountId
          AND canonicalPath IN (:canonicalPaths)
        """
    )
    suspend fun findByPaths(
        sourceAccountId: Long,
        canonicalPaths: List<String>,
    ): List<SourceItemEntity>

    @Query(
        """
        SELECT * FROM source_item
        WHERE libraryRootId = :libraryRootId
          AND isDeleted = 0
          AND (
              canonicalPath = :canonicalPath
              OR canonicalPath LIKE :descendantPattern ESCAPE '\'
          )
        """
    )
    suspend fun findLiveAtOrBelowPath(
        libraryRootId: Long,
        canonicalPath: String,
        descendantPattern: String,
    ): List<SourceItemEntity>

    @Query(
        """
        SELECT * FROM source_item
        WHERE sourceAccountId = :sourceAccountId
          AND providerItemId IN (:providerItemIds)
        """
    )
    suspend fun findByProviderItemIds(
        sourceAccountId: Long,
        providerItemIds: List<String>,
    ): List<SourceItemEntity>

    @Query(
        """
        SELECT * FROM source_item
        WHERE sourceAccountId = :sourceAccountId
          AND itemType = 'track'
          AND isDeleted = 0
          AND (lastSeenScanId IS NULL OR lastSeenScanId != :scanId)
        LIMIT :limit
        """
    )
    suspend fun findMissingTracksForSourceAccount(
        sourceAccountId: Long,
        scanId: String,
        limit: Int,
    ): List<SourceItemEntity>

    @Query(
        """
        SELECT COUNT(*) FROM source_item
        WHERE sourceAccountId = :sourceAccountId
          AND itemType = 'track'
          AND isDeleted = 0
        """
    )
    suspend fun countLiveTracksForSourceAccount(sourceAccountId: Long): Long

    @Query("SELECT * FROM source_item WHERE contentHash = :contentHash AND contentHash IS NOT NULL")
    suspend fun findByContentHash(contentHash: String): List<SourceItemEntity>

    @Query(
        """
        SELECT * FROM source_item
        WHERE audioFingerprint = :audioFingerprint
          AND audioFingerprint IS NOT NULL
        """
    )
    suspend fun findByAudioFingerprint(audioFingerprint: String): List<SourceItemEntity>

    @Upsert
    suspend fun upsertAll(items: List<SourceItemEntity>): List<Long>

    @Query("SELECT * FROM source_item_property WHERE sourceItemId IN (:sourceItemIds)")
    suspend fun propertiesForItems(sourceItemIds: List<Long>): List<SourceItemPropertyEntity>

    @Upsert
    suspend fun upsertProperties(properties: List<SourceItemPropertyEntity>)

    @Query("DELETE FROM source_item_property WHERE sourceItemId IN (:sourceItemIds) AND propertyKey = :propertyKey")
    suspend fun deletePropertyForItems(sourceItemIds: List<Long>, propertyKey: String)

    @Query(
        """
        UPDATE source_item
        SET lastSeenScanId = :scanId,
            lastSyncedAt = :now,
            isDeleted = 0
        WHERE id IN (:ids)
        """
    )
    suspend fun markSeen(
        ids: List<Long>,
        scanId: String,
        now: Long,
    )

    @Query(
        """
        UPDATE source_item
        SET isDeleted = 1,
            lastSyncedAt = :now
        WHERE libraryRootId = :libraryRootId
          AND lastSeenScanId != :scanId
        """
    )
    suspend fun markMissingDeleted(
        libraryRootId: Long,
        scanId: String,
        now: Long,
    )

    @Query(
        """
        DELETE FROM source_item
        WHERE libraryRootId = :libraryRootId
          AND lastSeenScanId != :scanId
        """
    )
    suspend fun deleteMissingForLibraryRoot(libraryRootId: Long, scanId: String)

    @Query(
        """
        UPDATE source_item
        SET isDeleted = 1,
            lastSyncedAt = :now
        WHERE sourceAccountId = :sourceAccountId
          AND providerItemId IN (:providerItemIds)
        """
    )
    suspend fun markDeletedByProviderItemIds(
        sourceAccountId: Long,
        providerItemIds: List<String>,
        now: Long,
    ): Int

    @Query(
        """
        UPDATE source_item
        SET isDeleted = 1,
            lastSyncedAt = :now
        WHERE id IN (:ids)
          AND isDeleted = 0
        """
    )
    suspend fun markDeletedByIds(ids: List<Long>, now: Long): Int

    @Query("DELETE FROM source_item WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>): Int

    @Transaction
    suspend fun applyScanBatch(
        changedItems: List<SourceItemEntity>,
    ) {
        if (changedItems.isNotEmpty()) {
            upsertAll(changedItems)
        }
    }
}

data class SourceItemSignature(
    val id: Long,
    val providerItemId: String?,
    val canonicalPath: String?,
    val sizeBytes: Long?,
    val etag: String?,
    val revision: String?,
    val modifiedAtRemote: Long?,
    val isDeleted: Boolean,
)

@Dao
interface TrackSourceRefDao {
    @Query("SELECT * FROM track_source_ref WHERE trackId = :trackId")
    suspend fun findByTrackId(trackId: Long): List<TrackSourceRefEntity>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM track_source_ref
            WHERE trackId = :trackId AND sourceItemId = :sourceItemId
        )
        """
    )
    suspend fun contains(trackId: Long, sourceItemId: Long): Boolean

    @Query(
        """
        UPDATE track_source_ref
        SET isPreferred = CASE WHEN sourceItemId = :sourceItemId THEN 1 ELSE 0 END,
            updatedAt = :now
        WHERE trackId = :trackId
        """
    )
    suspend fun updatePreferredSource(trackId: Long, sourceItemId: Long, now: Long)

    @Transaction
    suspend fun selectPreferredSource(trackId: Long, sourceItemId: Long, now: Long): Boolean {
        if (!contains(trackId, sourceItemId)) return false
        updatePreferredSource(trackId, sourceItemId, now)
        return true
    }

    @Query("SELECT * FROM track_source_ref WHERE sourceItemId IN (:sourceItemIds)")
    suspend fun findBySourceItemIds(sourceItemIds: List<Long>): List<TrackSourceRefEntity>

    @Query(
        """
        SELECT item.id AS sourceItemId,
               ref.trackId AS trackId,
               track.albumId AS albumId,
               item.sourceAccountId AS storageId,
               item.displayName AS name,
               item.canonicalPath AS path,
               item.sizeBytes AS sizeBytes,
               item.providerItemId AS remoteId,
               item.parentProviderItemId AS parentRemoteId,
               item.mimeType AS mimeType,
               item.etag AS etag,
               item.createdAtRemote AS createdAt,
               item.modifiedAtRemote AS modifiedAt
        FROM track_source_ref ref
        JOIN track ON track.id = ref.trackId
        JOIN source_item item ON item.id = ref.sourceItemId
        JOIN source_account account ON account.id = item.sourceAccountId
        WHERE ref.trackId = :trackId
          AND account.providerType = 'webdav'
          AND account.enabled = 1
          AND ref.isAvailable = 1
          AND item.isDeleted = 0
          AND item.canonicalPath IS NOT NULL
          AND item.sizeBytes > 0
        ORDER BY ref.isPreferred DESC, ref.updatedAt DESC
        """
    )
    suspend fun webDavMetadataCandidatesForTrack(trackId: Long): List<MetadataRefreshCandidate>

    @Query(
        """
        SELECT item.id AS sourceItemId,
               ref.trackId AS trackId,
               track.albumId AS albumId,
               item.sourceAccountId AS storageId,
               item.displayName AS name,
               item.canonicalPath AS path,
               item.sizeBytes AS sizeBytes,
               item.providerItemId AS remoteId,
               item.parentProviderItemId AS parentRemoteId,
               item.mimeType AS mimeType,
               item.etag AS etag,
               item.createdAtRemote AS createdAt,
               item.modifiedAtRemote AS modifiedAt
        FROM track_source_ref ref
        JOIN track ON track.id = ref.trackId
        JOIN source_item item ON item.id = ref.sourceItemId
        JOIN source_account account ON account.id = item.sourceAccountId
        WHERE ref.trackId = :trackId
          AND account.enabled = 1
          AND ref.isAvailable = 1
          AND item.isDeleted = 0
          AND item.canonicalPath IS NOT NULL
          AND item.sizeBytes > 0
        ORDER BY ref.isPreferred DESC,
                 CASE WHEN account.providerType = 'local' THEN 1 ELSE 0 END DESC,
                 account.priority DESC,
                 ref.updatedAt DESC
        LIMIT 1
        """
    )
    suspend fun metadataResetCandidateForTrack(trackId: Long): MetadataRefreshCandidate?

    @Query(
        """
        SELECT item.id AS sourceItemId,
               ref.trackId AS trackId,
               track.albumId AS albumId,
               item.sourceAccountId AS storageId,
               item.displayName AS name,
               item.canonicalPath AS path,
               item.sizeBytes AS sizeBytes,
               item.providerItemId AS remoteId,
               item.parentProviderItemId AS parentRemoteId,
               item.mimeType AS mimeType,
               item.etag AS etag,
               item.createdAtRemote AS createdAt,
               item.modifiedAtRemote AS modifiedAt
        FROM track_source_ref ref
        JOIN track ON track.id = ref.trackId
        JOIN source_item item ON item.id = ref.sourceItemId
        JOIN source_account account ON account.id = item.sourceAccountId
        WHERE track.albumId = :albumId
          AND account.providerType = 'webdav'
          AND account.enabled = 1
          AND ref.isAvailable = 1
          AND item.isDeleted = 0
          AND item.canonicalPath IS NOT NULL
          AND item.sizeBytes > 0
        ORDER BY ref.trackId, ref.isPreferred DESC, ref.updatedAt DESC
        """
    )
    suspend fun webDavMetadataCandidatesForAlbum(albumId: Long): List<MetadataRefreshCandidate>

    @Query(
        """
        SELECT item.id AS sourceItemId,
               ref.trackId AS trackId,
               track.albumId AS albumId,
               item.sourceAccountId AS storageId,
               item.displayName AS name,
               item.canonicalPath AS path,
               item.sizeBytes AS sizeBytes,
               item.providerItemId AS remoteId,
               item.parentProviderItemId AS parentRemoteId,
               item.mimeType AS mimeType,
               item.etag AS etag,
               item.createdAtRemote AS createdAt,
               item.modifiedAtRemote AS modifiedAt
        FROM track_source_ref ref
        JOIN track ON track.id = ref.trackId
        JOIN source_item item ON item.id = ref.sourceItemId
        JOIN source_account account ON account.id = item.sourceAccountId
        WHERE account.providerType = 'webdav'
          AND account.enabled = 1
          AND ref.isAvailable = 1
          AND item.isDeleted = 0
          AND item.canonicalPath IS NOT NULL
          AND item.sizeBytes > 0
          AND (
              (:target = 'Artwork' AND NOT EXISTS (
                  SELECT 1 FROM artwork
                  WHERE artwork.trackId = track.id
                     OR (track.albumId IS NOT NULL AND artwork.albumId = track.albumId)
                     OR artwork.id = (
                         SELECT album.artworkId FROM album WHERE album.id = track.albumId
                     )
              ))
              OR (:target = 'Lyrics' AND NOT EXISTS (
                  SELECT 1 FROM lyrics WHERE lyrics.trackId = track.id
              ))
              OR (:target = 'ArtworkAndLyrics' AND (
                  NOT EXISTS (
                      SELECT 1 FROM artwork
                      WHERE artwork.trackId = track.id
                         OR (track.albumId IS NOT NULL AND artwork.albumId = track.albumId)
                         OR artwork.id = (
                             SELECT album.artworkId FROM album WHERE album.id = track.albumId
                         )
                  )
                  OR NOT EXISTS (SELECT 1 FROM lyrics WHERE lyrics.trackId = track.id)
              ))
              OR (:target = 'RawMetadata' AND NOT EXISTS (
                  SELECT 1 FROM raw_metadata WHERE raw_metadata.trackId = track.id
              ))
              OR (:target = 'All' AND (
                  NOT EXISTS (
                      SELECT 1 FROM artwork
                      WHERE artwork.trackId = track.id
                         OR (track.albumId IS NOT NULL AND artwork.albumId = track.albumId)
                         OR artwork.id = (
                             SELECT album.artworkId FROM album WHERE album.id = track.albumId
                         )
                  )
                  OR NOT EXISTS (SELECT 1 FROM lyrics WHERE lyrics.trackId = track.id)
                  OR NOT EXISTS (SELECT 1 FROM raw_metadata WHERE raw_metadata.trackId = track.id)
              ))
          )
        ORDER BY ref.trackId, ref.isPreferred DESC, ref.updatedAt DESC
        """
    )
    suspend fun missingWebDavMetadataCandidates(target: String): List<MetadataRefreshCandidate>

    @Query("SELECT COUNT(*) FROM track_source_ref WHERE trackId = :trackId")
    suspend fun countForTrack(trackId: Long): Int


    @Query(
        """
        SELECT EXISTS(
            SELECT 1
            FROM track_source_ref ref
            JOIN source_item item ON item.id = ref.sourceItemId
            WHERE ref.trackId = :trackId
              AND item.sourceAccountId = :sourceAccountId
        )
        """
    )
    suspend fun hasSourceAccount(trackId: Long, sourceAccountId: Long): Boolean

    @Upsert
    suspend fun upsertAll(refs: List<TrackSourceRefEntity>)

    @Query(
        """
        UPDATE track_source_ref
        SET hasEmbeddedArtwork = :hasEmbeddedArtwork,
            embeddedLyricsKind = :embeddedLyricsKind,
            updatedAt = :now
        WHERE sourceItemId = :sourceItemId
        """
    )
    suspend fun updateEmbeddedMetadataPresence(
        sourceItemId: Long,
        hasEmbeddedArtwork: Boolean,
        embeddedLyricsKind: String,
        now: Long,
    )

    @Query(
        """
        UPDATE track_source_ref
        SET isAvailable = 1,
            updatedAt = :now
        WHERE sourceItemId IN (:sourceItemIds)
        """
    )
    suspend fun markAvailableBySourceItemIds(sourceItemIds: List<Long>, now: Long)

    @Query(
        """
        UPDATE track_source_ref
        SET isAvailable = 0,
            updatedAt = :now
        WHERE sourceItemId IN (:sourceItemIds)
        """
    )
    suspend fun markUnavailableBySourceItemIds(sourceItemIds: List<Long>, now: Long)

    @Query(
        """
        UPDATE track_source_ref
        SET isAvailable = 0,
            updatedAt = :now
        WHERE sourceItemId IN (
            SELECT id FROM source_item
            WHERE libraryRootId = :libraryRootId
              AND isDeleted = 1
        )
        """
    )
    suspend fun markUnavailableForDeletedSourceItems(libraryRootId: Long, now: Long)

    @Query(
        """
        SELECT
            ref.trackId AS ref_trackId,
            ref.sourceItemId AS ref_sourceItemId,
            ref.role AS ref_role,
            ref.matchMethod AS ref_matchMethod,
            ref.matchConfidence AS ref_matchConfidence,
            ref.isPreferred AS ref_isPreferred,
            ref.isAvailable AS ref_isAvailable,
            ref.isDownloaded AS ref_isDownloaded,
            ref.playable AS ref_playable,
            ref.downloadable AS ref_downloadable,
            ref.codec AS ref_codec,
            ref.container AS ref_container,
            ref.bitRate AS ref_bitRate,
            ref.sampleRate AS ref_sampleRate,
            ref.bitsPerSample AS ref_bitsPerSample,
            ref.channels AS ref_channels,
            ref.channelLayout AS ref_channelLayout,
            ref.lossless AS ref_lossless,
            ref.createdAt AS ref_createdAt,
            ref.updatedAt AS ref_updatedAt,
            ref.hasEmbeddedArtwork AS ref_hasEmbeddedArtwork,
            ref.embeddedLyricsKind AS ref_embeddedLyricsKind,
            item.id AS item_id,
            item.sourceAccountId AS item_sourceAccountId,
            item.libraryRootId AS item_libraryRootId,
            item.itemType AS item_itemType,
            item.providerItemId AS item_providerItemId,
            item.parentProviderItemId AS item_parentProviderItemId,
            item.canonicalPath AS item_canonicalPath,
            item.displayPath AS item_displayPath,
            item.displayName AS item_displayName,
            item.mimeType AS item_mimeType,
            item.sizeBytes AS item_sizeBytes,
            item.etag AS item_etag,
            item.revision AS item_revision,
            item.createdAtRemote AS item_createdAtRemote,
            item.modifiedAtRemote AS item_modifiedAtRemote,
            item.contentHash AS item_contentHash,
            item.audioFingerprint AS item_audioFingerprint,
            item.isDeleted AS item_isDeleted,
            item.firstSyncedAt AS item_firstSyncedAt,
            item.lastSyncedAt AS item_lastSyncedAt,
            item.lastSeenScanId AS item_lastSeenScanId,
            account.id AS account_id,
            account.providerType AS account_providerType,
            account.displayName AS account_displayName,
            account.endpoint AS account_endpoint,
            account.externalAccountId AS account_externalAccountId,
            account.credentialRef AS account_credentialRef,
            account.priority AS account_priority,
            account.enabled AS account_enabled,
            account.createdAt AS account_createdAt,
            account.updatedAt AS account_updatedAt,
            account.rootPath AS account_rootPath,
            account.providerConfig AS account_providerConfig
        FROM track_source_ref ref
        JOIN source_item item ON item.id = ref.sourceItemId
        JOIN source_account account ON account.id = item.sourceAccountId
        WHERE ref.trackId = :trackId
          AND ref.playable = 1
          AND ref.isAvailable = 1
          AND item.isDeleted = 0
          AND account.enabled = 1
        ORDER BY
          ref.isDownloaded DESC,
          CASE WHEN account.providerType = 'local' THEN 1 ELSE 0 END DESC,
          ref.isPreferred DESC,
          COALESCE(ref.lossless, 0) DESC,
          COALESCE(ref.bitsPerSample, 0) DESC,
          COALESCE(ref.sampleRate, 0) DESC,
          COALESCE(ref.bitRate, 0) DESC,
          account.priority DESC,
          ref.updatedAt DESC
        """
    )
    suspend fun playbackCandidates(trackId: Long): List<TrackSourcePlaybackCandidate>

    @Query(
        """
        SELECT
            ref.trackId AS ref_trackId,
            ref.sourceItemId AS ref_sourceItemId,
            ref.role AS ref_role,
            ref.matchMethod AS ref_matchMethod,
            ref.matchConfidence AS ref_matchConfidence,
            ref.isPreferred AS ref_isPreferred,
            ref.isAvailable AS ref_isAvailable,
            ref.isDownloaded AS ref_isDownloaded,
            ref.playable AS ref_playable,
            ref.downloadable AS ref_downloadable,
            ref.codec AS ref_codec,
            ref.container AS ref_container,
            ref.bitRate AS ref_bitRate,
            ref.sampleRate AS ref_sampleRate,
            ref.bitsPerSample AS ref_bitsPerSample,
            ref.channels AS ref_channels,
            ref.channelLayout AS ref_channelLayout,
            ref.lossless AS ref_lossless,
            ref.createdAt AS ref_createdAt,
            ref.updatedAt AS ref_updatedAt,
            ref.hasEmbeddedArtwork AS ref_hasEmbeddedArtwork,
            ref.embeddedLyricsKind AS ref_embeddedLyricsKind,
            item.id AS item_id,
            item.sourceAccountId AS item_sourceAccountId,
            item.libraryRootId AS item_libraryRootId,
            item.itemType AS item_itemType,
            item.providerItemId AS item_providerItemId,
            item.parentProviderItemId AS item_parentProviderItemId,
            item.canonicalPath AS item_canonicalPath,
            item.displayPath AS item_displayPath,
            item.displayName AS item_displayName,
            item.mimeType AS item_mimeType,
            item.sizeBytes AS item_sizeBytes,
            item.etag AS item_etag,
            item.revision AS item_revision,
            item.createdAtRemote AS item_createdAtRemote,
            item.modifiedAtRemote AS item_modifiedAtRemote,
            item.contentHash AS item_contentHash,
            item.audioFingerprint AS item_audioFingerprint,
            item.isDeleted AS item_isDeleted,
            item.firstSyncedAt AS item_firstSyncedAt,
            item.lastSyncedAt AS item_lastSyncedAt,
            item.lastSeenScanId AS item_lastSeenScanId,
            account.id AS account_id,
            account.providerType AS account_providerType,
            account.displayName AS account_displayName,
            account.endpoint AS account_endpoint,
            account.externalAccountId AS account_externalAccountId,
            account.credentialRef AS account_credentialRef,
            account.priority AS account_priority,
            account.enabled AS account_enabled,
            account.createdAt AS account_createdAt,
            account.updatedAt AS account_updatedAt,
            account.rootPath AS account_rootPath,
            account.providerConfig AS account_providerConfig
        FROM track_source_ref ref
        JOIN source_item item ON item.id = ref.sourceItemId
        JOIN source_account account ON account.id = item.sourceAccountId
        WHERE ref.trackId IN (:trackIds)
          AND ref.playable = 1
          AND ref.isAvailable = 1
          AND item.isDeleted = 0
          AND account.enabled = 1
        ORDER BY
          ref.trackId ASC,
          ref.isDownloaded DESC,
          CASE WHEN account.providerType = 'local' THEN 1 ELSE 0 END DESC,
          ref.isPreferred DESC,
          COALESCE(ref.lossless, 0) DESC,
          COALESCE(ref.bitsPerSample, 0) DESC,
          COALESCE(ref.sampleRate, 0) DESC,
          COALESCE(ref.bitRate, 0) DESC,
          account.priority DESC,
          ref.updatedAt DESC
        """
    )
    suspend fun playbackCandidatesForTracks(trackIds: List<Long>): List<TrackSourcePlaybackCandidate>
}

data class MetadataRefreshCandidate(
    val sourceItemId: Long,
    val trackId: Long,
    val albumId: Long?,
    val storageId: Long,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val remoteId: String?,
    val parentRemoteId: String?,
    val mimeType: String?,
    val etag: String?,
    val createdAt: Long?,
    val modifiedAt: Long?,
)

data class TrackSourcePlaybackCandidate(
    @Embedded(prefix = "ref_") val ref: TrackSourceRefEntity,
    @Embedded(prefix = "item_") val item: SourceItemEntity,
    @Embedded(prefix = "account_") val account: SourceAccountEntity,
)

@Dao
interface SourceSyncCursorDao {
    @Upsert
    suspend fun upsert(cursor: SourceSyncCursorEntity)

    @Query("DELETE FROM source_sync_cursor")
    suspend fun deleteAll()
}

@Dao
interface SourceErrorDao {
    @Upsert
    suspend fun upsert(error: SourceErrorEntity)

    @Insert
    suspend fun insertAll(errors: List<SourceErrorEntity>)

    @Query("SELECT * FROM source_error ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun listRecent(limit: Int): List<SourceErrorEntity>

    @Query("SELECT * FROM source_error WHERE importJobId = :importJobId ORDER BY createdAt DESC, id DESC")
    fun observeByImportJob(importJobId: String): Flow<List<SourceErrorEntity>>

    @Query("DELETE FROM source_error WHERE importJobId = :importJobId")
    suspend fun deleteByImportJob(importJobId: String)

    @Query("DELETE FROM source_error")
    suspend fun deleteAll()
}
