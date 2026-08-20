package io.github.julystar.musicapp.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "source_account",
    indices = [
        Index(value = ["providerType"]),
        Index(value = ["credentialRef"], unique = true),
    ],
)
data class SourceAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerType: String,
    val displayName: String,
    val endpoint: String?,
    val externalAccountId: String?,
    val credentialRef: String?,
    val priority: Int,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val rootPath: String? = null,
    val providerConfig: String? = null,
)

@Entity(
    tableName = "library_root",
    foreignKeys = [
        ForeignKey(
            entity = SourceAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceAccountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sourceAccountId"]),
        Index(value = ["sourceAccountId", "providerRootId"], unique = true),
        Index(value = ["sourceAccountId", "canonicalPath"], unique = true),
    ],
)
data class LibraryRootEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceAccountId: Long,
    val providerRootId: String?,
    val canonicalPath: String?,
    val displayName: String,
    val syncStatus: String,
    val syncCursor: String?,
    val lastSyncAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    init {
        require(!providerRootId.isNullOrBlank() || !canonicalPath.isNullOrBlank()) {
            "LibraryRoot requires providerRootId or canonicalPath"
        }
    }
}

@Entity(
    tableName = "source_item",
    foreignKeys = [
        ForeignKey(
            entity = SourceAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceAccountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LibraryRootEntity::class,
            parentColumns = ["id"],
            childColumns = ["libraryRootId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sourceAccountId"]),
        Index(value = ["libraryRootId"]),
        Index(value = ["sourceAccountId", "providerItemId"], unique = true),
        Index(value = ["sourceAccountId", "canonicalPath"], unique = true),
        Index(value = ["parentProviderItemId"]),
        Index(value = ["itemType"]),
        Index(value = ["isDeleted"]),
        Index(value = ["lastSeenScanId"]),
        Index(value = ["contentHash"]),
        Index(value = ["audioFingerprint"]),
    ],
)
data class SourceItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceAccountId: Long,
    val libraryRootId: Long?,
    val itemType: String,
    val providerItemId: String?,
    val parentProviderItemId: String?,
    val canonicalPath: String?,
    val displayPath: String?,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val etag: String?,
    val revision: String?,
    val createdAtRemote: Long?,
    val modifiedAtRemote: Long?,
    val contentHash: String?,
    val audioFingerprint: String?,
    val isDeleted: Boolean,
    val firstSyncedAt: Long,
    val lastSyncedAt: Long,
    val lastSeenScanId: String?,
) {
    init {
        require(!providerItemId.isNullOrBlank() || !canonicalPath.isNullOrBlank()) {
            "SourceItem requires providerItemId or canonicalPath"
        }
    }
}

@Entity(
    tableName = "source_item_property",
    primaryKeys = ["sourceItemId", "propertyKey"],
    foreignKeys = [
        ForeignKey(
            entity = SourceItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sourceItemId"])],
)
data class SourceItemPropertyEntity(
    val sourceItemId: Long,
    val propertyKey: String,
    val stringValue: String?,
    val longValue: Long?,
    val doubleValue: Double?,
    val booleanValue: Boolean?,
)

@Entity(
    tableName = "track_source_ref",
    primaryKeys = ["trackId", "sourceItemId"],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SourceItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceItemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["trackId"]),
        Index(value = ["sourceItemId"], unique = true),
        Index(value = ["isPreferred"]),
        Index(value = ["isAvailable"]),
        Index(value = ["isDownloaded"]),
    ],
)
data class TrackSourceRefEntity(
    val trackId: Long,
    val sourceItemId: Long,
    val role: String,
    val matchMethod: String,
    val matchConfidence: Int,
    val isPreferred: Boolean,
    val isAvailable: Boolean,
    val isDownloaded: Boolean,
    val playable: Boolean,
    val downloadable: Boolean,
    val codec: String?,
    val container: String?,
    val bitRate: Int?,
    val sampleRate: Int?,
    val bitsPerSample: Int?,
    val channels: Int?,
    val channelLayout: String? = null,
    val lossless: Boolean?,
    val createdAt: Long,
    val updatedAt: Long,
    val hasEmbeddedArtwork: Boolean? = null,
    val embeddedLyricsKind: String? = null,
)

@Entity(
    tableName = "source_sync_cursor",
    foreignKeys = [
        ForeignKey(
            entity = SourceAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceAccountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LibraryRootEntity::class,
            parentColumns = ["id"],
            childColumns = ["libraryRootId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sourceAccountId"]),
        Index(value = ["libraryRootId"]),
        Index(value = ["sourceAccountId", "libraryRootId", "cursorType"], unique = true),
    ],
)
data class SourceSyncCursorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceAccountId: Long,
    val libraryRootId: Long?,
    val cursorType: String,
    val cursorValue: String?,
    val lastScanId: String?,
    val lastSyncAt: Long?,
)

@Entity(
    tableName = "source_error",
    foreignKeys = [
        ForeignKey(
            entity = SourceAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceAccountId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LibraryRootEntity::class,
            parentColumns = ["id"],
            childColumns = ["libraryRootId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = SourceItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceItemId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["sourceAccountId"]),
        Index(value = ["libraryRootId"]),
        Index(value = ["sourceItemId"]),
        Index(value = ["importJobId"]),
        Index(value = ["createdAt"]),
    ],
)
data class SourceErrorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceAccountId: Long,
    val libraryRootId: Long?,
    val sourceItemId: Long?,
    val importJobId: String?,
    val errorType: String,
    val message: String,
    val createdAt: Long,
    val resolvedAt: Long?,
)

object ProviderTypes {
    const val Local = "local"
    const val WebDav = "webdav"
    const val Smb = "smb"
    const val OneDrive = "onedrive"
    const val OpenList = "openlist"
    const val GoogleDrive = "google_drive"
    const val Plex = "plex"
    const val Emby = "emby"
    const val Jellyfin = "jellyfin"
    const val Navidrome = "navidrome"
    const val OpenSubsonic = "open_subsonic"
    const val AudioStation = "audio_station"
}

object SourceItemTypes {
    const val Folder = "folder"
    const val File = "file"
    const val Track = "track"
    const val Album = "album"
    const val Artist = "artist"
    const val Playlist = "playlist"
    const val Image = "image"
    const val Lyric = "lyric"
    const val Other = "other"
}
