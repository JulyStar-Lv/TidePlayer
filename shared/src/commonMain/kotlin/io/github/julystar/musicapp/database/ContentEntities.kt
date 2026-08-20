package io.github.julystar.musicapp.database

import androidx.room.Entity
import androidx.room.Embedded
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "artwork",
    indices = [
        Index(value = ["contentHash"], unique = true),
        Index(value = ["trackId"]),
        Index(value = ["albumId"]),
    ],
)
data class ArtworkEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long?,
    val albumId: Long?,
    val contentHash: String,
    val localPath: String,
    val thumbnailPath: String?,
    val width: Int?,
    val height: Int?,
    val mimeType: String?,
    val pictureType: String?,
)

@Entity(
    tableName = "lyrics",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["trackId", "sourceKind"], unique = true)],
)
data class LyricsEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val format: String,
    val language: String?,
    val synchronized: Boolean,
    val content: String,
    val sourcePath: String?,
    val updatedAt: Long,
    val sourceKind: String = "EmbeddedPlain",
    val structuredContent: String? = null,
)

@Entity(
    tableName = "raw_metadata",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["trackId"]),
        Index(value = ["trackId", "tagKey"]),
    ],
)
data class RawMetadataEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val tagKey: String,
    val value: String,
    val locale: String?,
    val description: String?,
)

@Entity(
    tableName = "import_job",
    indices = [
        Index(value = ["libraryRootId"]),
        Index(value = ["status"]),
    ],
)
data class ImportJobEntity(
    @androidx.room.PrimaryKey val id: String,
    val libraryRootId: Long,
    val status: String,
    val scannedCount: Long,
    val importedCount: Long,
    val skippedCount: Long,
    val failedCount: Long,
    val checkpoint: String?,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long,
    @androidx.room.ColumnInfo(defaultValue = "'Full'")
    val metadataScanMode: String = "Full",
    @androidx.room.ColumnInfo(defaultValue = "8")
    val metadataConcurrency: Long = 8,
    @androidx.room.ColumnInfo(defaultValue = "200")
    val importBatchSize: Int = 200,
    @androidx.room.ColumnInfo(defaultValue = "1")
    val scanSubdirectories: Boolean = true,
    @androidx.room.ColumnInfo(defaultValue = "1")
    val ignoreShortAudio: Boolean = true,
    @androidx.room.ColumnInfo(defaultValue = "30000")
    val minDurationMs: Long = 30_000,
    @androidx.room.ColumnInfo(defaultValue = "1")
    val ignoreHiddenFiles: Boolean = true,
    @androidx.room.ColumnInfo(defaultValue = "'.cache|.trash|@eaDir|__MACOSX'")
    val ignoredDirectoryNames: String = ".cache|.trash|@eaDir|__MACOSX",
    @androidx.room.ColumnInfo(defaultValue = "'MarkUnavailable'")
    val missingFilePolicy: String = "MarkUnavailable",
    @androidx.room.ColumnInfo(defaultValue = "'SeparateBySource'")
    val duplicateTrackPolicy: String = "SeparateBySource",
    @androidx.room.ColumnInfo(defaultValue = "0")
    val metadataRequestCount: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val metadataFetchedBytes: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val metadataElapsedMs: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val artworkCachedBytes: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "'LEGACY_FULL_SCAN_FALLBACK'")
    val syncMode: String = "LEGACY_FULL_SCAN_FALLBACK",
    @androidx.room.ColumnInfo(defaultValue = "4")
    val directoryConcurrency: Int = 4,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val capabilityDetectionElapsedMs: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val directoryScanElapsedMs: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val directoryRequestCount: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val listedDirectoryCount: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val visitedEntryCount: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val discoveredMusicCount: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val unchangedCount: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val addedCount: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val modifiedCount: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val renamedCount: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val deletedCount: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val databaseReadElapsedMs: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val databaseWriteElapsedMs: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val totalElapsedMs: Long = 0,
)

data class ImportJobWithFolder(
    @Embedded val job: ImportJobEntity,
    val sourceAccountId: Long,
    val providerRootId: String?,
    val canonicalPath: String?,
    val displayName: String,
)

@Entity(
    tableName = "download_task",
    indices = [
        Index(value = ["status"]),
        Index(value = ["updatedAt"]),
        Index(value = ["sourceId", "mediaType", "remoteId"], unique = true),
    ],
)
data class DownloadTaskEntity(
    @androidx.room.PrimaryKey val id: String,
    val sourceId: String,
    val mediaType: String,
    val remoteId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val status: String,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val localPath: String?,
    val mimeType: String?,
    val errorMessage: String?,
    val finalizationWarning: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "playlist",
    indices = [
        Index(value = ["sortOrder"]),
        Index(value = ["providerType", "sourceAccountId", "remotePlaylistId"], unique = true),
    ],
)
data class PlaylistEntity(
    @androidx.room.PrimaryKey val id: Long,
    val title: String,
    val artworkId: Long?,
    val coverStorageId: Long? = null,
    val coverPath: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val sortOrder: Long,
    val providerType: String? = null,
    val sourceAccountId: Long? = null,
    val remotePlaylistId: String? = null,
) {
    init {
        val identityFields = listOf(providerType, sourceAccountId?.toString(), remotePlaylistId)
        require(identityFields.all { it == null } || identityFields.all { !it.isNullOrBlank() }) {
            "Playlist remote identity must be fully specified or fully local"
        }
    }
}

@Entity(
    tableName = "playlist_track",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["trackId"]),
        Index(value = ["playlistId", "sortOrder"]),
    ],
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: Long,
    val sortOrder: Long,
    val addedAt: Long,
)
