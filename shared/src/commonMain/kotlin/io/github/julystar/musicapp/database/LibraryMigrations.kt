package io.github.julystar.musicapp.database

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        trackV2Columns.forEach { definition ->
            connection.prepare("ALTER TABLE track ADD COLUMN $definition").use { statement ->
                statement.step()
            }
        }
    }
}
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        listOf(
            "ALTER TABLE track ADD COLUMN sourceStorageId INTEGER",
            "ALTER TABLE track ADD COLUMN sourcePath TEXT",
            "ALTER TABLE playlist ADD COLUMN coverStorageId INTEGER",
            "ALTER TABLE playlist ADD COLUMN coverPath TEXT",
        ).forEach { sql ->
            connection.prepare(sql).use { statement ->
                statement.step()
            }
        }
    }
}
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        listOf(
            """
            CREATE TABLE IF NOT EXISTS download_task (
                id TEXT NOT NULL PRIMARY KEY,
                sourceId TEXT NOT NULL,
                mediaType TEXT NOT NULL,
                remoteId TEXT NOT NULL,
                title TEXT NOT NULL,
                artist TEXT,
                album TEXT,
                durationMs INTEGER,
                status TEXT NOT NULL,
                downloadedBytes INTEGER NOT NULL,
                totalBytes INTEGER,
                localPath TEXT,
                mimeType TEXT,
                errorMessage TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
            "CREATE INDEX IF NOT EXISTS index_download_task_status ON download_task(status)",
            "CREATE INDEX IF NOT EXISTS index_download_task_updatedAt ON download_task(updatedAt)",
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_download_task_sourceId_mediaType_remoteId
            ON download_task(sourceId, mediaType, remoteId)
            """.trimIndent(),
        ).forEach { sql ->
            connection.prepare(sql).use { statement ->
                statement.step()
            }
        }
    }
}

private val trackV2Columns = listOf(
    "artist TEXT",
    "lyricist TEXT",
    "conductor TEXT",
    "copyright TEXT",
    "publisher TEXT",
    "originalReleaseDate TEXT",
    "bpm REAL",
    "musicalKey TEXT",
    "isrc TEXT",
    "musicBrainzRecordingId TEXT",
    "musicBrainzTrackId TEXT",
    "musicBrainzReleaseId TEXT",
    "musicBrainzReleaseGroupId TEXT",
    "musicBrainzArtistId TEXT",
    "musicBrainzReleaseArtistId TEXT",
    "musicBrainzWorkId TEXT",
    "replayGainTrackGain REAL",
    "replayGainTrackPeak REAL",
    "replayGainAlbumGain REAL",
    "replayGainAlbumPeak REAL",
)

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        listOf(
            "ALTER TABLE track ADD COLUMN lastPlayedAt INTEGER",
        ).forEach { sql ->
            connection.prepare(sql).use { statement ->
                statement.step()
            }
        }
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.prepare(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS track_fts USING fts4(
                title,
                artist,
                albumArtist,
                composer,
                content=`track`
            )
            """.trimIndent()
        ).use { statement ->
            statement.step()
        }

        connection.prepare(
            """
            INSERT INTO track_fts(track_fts) VALUES('rebuild')
            """.trimIndent()
        ).use { statement ->
            statement.step()
        }
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        sourceSchemaV7Statements.forEach { sql ->
            connection.prepare(sql).use { statement ->
                statement.step()
            }
        }
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        listOf(
            "ALTER TABLE source_error ADD COLUMN importJobId TEXT",
            "CREATE INDEX IF NOT EXISTS index_source_error_importJobId ON source_error(importJobId)",
        ).forEach { sql ->
            connection.prepare(sql).use { statement ->
                statement.step()
            }
        }
    }
}

private val sourceSchemaV7Statements = listOf(
    "PRAGMA foreign_keys=OFF",
    """
    CREATE TABLE IF NOT EXISTS source_account (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        providerType TEXT NOT NULL,
        displayName TEXT NOT NULL,
        endpoint TEXT,
        externalAccountId TEXT,
        credentialRef TEXT,
        priority INTEGER NOT NULL,
        enabled INTEGER NOT NULL,
        createdAt INTEGER NOT NULL,
        updatedAt INTEGER NOT NULL
    )
    """.trimIndent(),
    "CREATE INDEX IF NOT EXISTS index_source_account_providerType ON source_account(providerType)",
    "CREATE UNIQUE INDEX IF NOT EXISTS index_source_account_credentialRef ON source_account(credentialRef)",
    """
    INSERT OR IGNORE INTO source_account(
        id, providerType, displayName, endpoint, externalAccountId, credentialRef,
        priority, enabled, createdAt, updatedAt
    )
    SELECT id,
           CASE type WHEN 'LOCAL' THEN 'local' WHEN 'WEBDAV' THEN 'webdav'
                     WHEN 'ONE_DRIVE' THEN 'onedrive' ELSE lower(type) END,
           displayName,
           CASE WHEN type = 'ONE_DRIVE' THEN NULLIF(driveId, '') ELSE NULLIF(baseUrl, '') END,
           NULLIF(driveId, ''),
           credentialRef,
           0,
           1,
           createdAt,
           updatedAt
    FROM storage
    """.trimIndent(),
    """
    CREATE TABLE IF NOT EXISTS library_root (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        sourceAccountId INTEGER NOT NULL,
        providerRootId TEXT,
        canonicalPath TEXT,
        displayName TEXT NOT NULL,
        syncStatus TEXT NOT NULL,
        syncCursor TEXT,
        lastSyncAt INTEGER,
        createdAt INTEGER NOT NULL,
        updatedAt INTEGER NOT NULL,
        FOREIGN KEY(sourceAccountId) REFERENCES source_account(id) ON UPDATE NO ACTION ON DELETE CASCADE
    )
    """.trimIndent(),
    "CREATE INDEX IF NOT EXISTS index_library_root_sourceAccountId ON library_root(sourceAccountId)",
    "CREATE UNIQUE INDEX IF NOT EXISTS index_library_root_sourceAccountId_providerRootId ON library_root(sourceAccountId, providerRootId)",
    "CREATE UNIQUE INDEX IF NOT EXISTS index_library_root_sourceAccountId_canonicalPath ON library_root(sourceAccountId, canonicalPath)",
    """
    INSERT OR IGNORE INTO library_root(
        id, sourceAccountId, providerRootId, canonicalPath, displayName, syncStatus,
        syncCursor, lastSyncAt, createdAt, updatedAt
    )
    SELECT id, storageId, remoteId, canonicalPath,
           COALESCE(NULLIF(displayPath, ''), canonicalPath),
           syncStatus, deltaLink, lastSyncAt,
           COALESCE(lastSyncAt, 0), COALESCE(lastSyncAt, 0)
    FROM selected_folder
    """.trimIndent(),
    """
    CREATE TABLE IF NOT EXISTS source_item (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        sourceAccountId INTEGER NOT NULL,
        libraryRootId INTEGER,
        itemType TEXT NOT NULL,
        providerItemId TEXT,
        parentProviderItemId TEXT,
        canonicalPath TEXT,
        displayPath TEXT,
        displayName TEXT NOT NULL,
        mimeType TEXT,
        sizeBytes INTEGER,
        etag TEXT,
        revision TEXT,
        createdAtRemote INTEGER,
        modifiedAtRemote INTEGER,
        contentHash TEXT,
        audioFingerprint TEXT,
        isDeleted INTEGER NOT NULL,
        firstSyncedAt INTEGER NOT NULL,
        lastSyncedAt INTEGER NOT NULL,
        lastSeenScanId TEXT,
        FOREIGN KEY(sourceAccountId) REFERENCES source_account(id) ON UPDATE NO ACTION ON DELETE CASCADE,
        FOREIGN KEY(libraryRootId) REFERENCES library_root(id) ON UPDATE NO ACTION ON DELETE CASCADE
    )
    """.trimIndent(),
    "CREATE INDEX IF NOT EXISTS index_source_item_sourceAccountId ON source_item(sourceAccountId)",
    "CREATE INDEX IF NOT EXISTS index_source_item_libraryRootId ON source_item(libraryRootId)",
    "CREATE UNIQUE INDEX IF NOT EXISTS index_source_item_sourceAccountId_providerItemId ON source_item(sourceAccountId, providerItemId)",
    "CREATE UNIQUE INDEX IF NOT EXISTS index_source_item_sourceAccountId_canonicalPath ON source_item(sourceAccountId, canonicalPath)",
    "CREATE INDEX IF NOT EXISTS index_source_item_parentProviderItemId ON source_item(parentProviderItemId)",
    "CREATE INDEX IF NOT EXISTS index_source_item_itemType ON source_item(itemType)",
    "CREATE INDEX IF NOT EXISTS index_source_item_isDeleted ON source_item(isDeleted)",
    "CREATE INDEX IF NOT EXISTS index_source_item_lastSeenScanId ON source_item(lastSeenScanId)",
    "CREATE INDEX IF NOT EXISTS index_source_item_contentHash ON source_item(contentHash)",
    "CREATE INDEX IF NOT EXISTS index_source_item_audioFingerprint ON source_item(audioFingerprint)",
    """
    INSERT OR IGNORE INTO source_item(
        id, sourceAccountId, libraryRootId, itemType, providerItemId, parentProviderItemId,
        canonicalPath, displayPath, displayName, mimeType, sizeBytes, etag, revision,
        createdAtRemote, modifiedAtRemote, contentHash, audioFingerprint, isDeleted,
        firstSyncedAt, lastSyncedAt, lastSeenScanId
    )
    SELECT id, storageId, selectedFolderId, 'track', remoteId, parentRemoteId,
           canonicalPath, displayPath, fileName, mimeType, size, etag, ctag,
           createdAt, modifiedAt, contentHash, NULL, isDeleted,
           COALESCE(modifiedAt, createdAt, 0), COALESCE(modifiedAt, createdAt, 0),
           lastSeenScanId
    FROM remote_file
    """.trimIndent(),
    """
    INSERT OR IGNORE INTO source_item(
        id, sourceAccountId, libraryRootId, itemType, providerItemId, parentProviderItemId,
        canonicalPath, displayPath, displayName, mimeType, sizeBytes, etag, revision,
        createdAtRemote, modifiedAtRemote, contentHash, audioFingerprint, isDeleted,
        firstSyncedAt, lastSyncedAt, lastSeenScanId
    )
    SELECT CASE WHEN t.id = 0 THEN -9223372036854775807 ELSE -ABS(t.id) END,
           t.sourceStorageId, NULL, 'track', NULL, NULL, t.sourcePath, t.sourcePath,
           COALESCE(NULLIF(substr(t.sourcePath, length(rtrim(t.sourcePath, replace(t.sourcePath, '/', ''))) + 1), ''), t.title),
           NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0,
           t.createdAt, t.updatedAt, NULL
    FROM track t
    WHERE t.remoteFileId IS NULL
      AND t.sourceStorageId IS NOT NULL
      AND TRIM(COALESCE(t.sourcePath, '')) != ''
    """.trimIndent(),
    """
    CREATE TABLE IF NOT EXISTS source_item_property (
        sourceItemId INTEGER NOT NULL,
        propertyKey TEXT NOT NULL,
        stringValue TEXT,
        longValue INTEGER,
        doubleValue REAL,
        booleanValue INTEGER,
        PRIMARY KEY(sourceItemId, propertyKey),
        FOREIGN KEY(sourceItemId) REFERENCES source_item(id) ON UPDATE NO ACTION ON DELETE CASCADE
    )
    """.trimIndent(),
    "CREATE INDEX IF NOT EXISTS index_source_item_property_sourceItemId ON source_item_property(sourceItemId)",
    """
    CREATE TABLE IF NOT EXISTS track_source_ref (
        trackId INTEGER NOT NULL,
        sourceItemId INTEGER NOT NULL,
        role TEXT NOT NULL,
        matchMethod TEXT NOT NULL,
        matchConfidence INTEGER NOT NULL,
        isPreferred INTEGER NOT NULL,
        isAvailable INTEGER NOT NULL,
        isDownloaded INTEGER NOT NULL,
        playable INTEGER NOT NULL,
        downloadable INTEGER NOT NULL,
        codec TEXT,
        container TEXT,
        bitRate INTEGER,
        sampleRate INTEGER,
        bitsPerSample INTEGER,
        channels INTEGER,
        lossless INTEGER,
        createdAt INTEGER NOT NULL,
        updatedAt INTEGER NOT NULL,
        PRIMARY KEY(trackId, sourceItemId),
        FOREIGN KEY(trackId) REFERENCES track(id) ON UPDATE NO ACTION ON DELETE CASCADE,
        FOREIGN KEY(sourceItemId) REFERENCES source_item(id) ON UPDATE NO ACTION ON DELETE CASCADE
    )
    """.trimIndent(),
    "CREATE INDEX IF NOT EXISTS index_track_source_ref_trackId ON track_source_ref(trackId)",
    "CREATE UNIQUE INDEX IF NOT EXISTS index_track_source_ref_sourceItemId ON track_source_ref(sourceItemId)",
    "CREATE INDEX IF NOT EXISTS index_track_source_ref_isPreferred ON track_source_ref(isPreferred)",
    "CREATE INDEX IF NOT EXISTS index_track_source_ref_isAvailable ON track_source_ref(isAvailable)",
    "CREATE INDEX IF NOT EXISTS index_track_source_ref_isDownloaded ON track_source_ref(isDownloaded)",
    """
    INSERT OR IGNORE INTO track_source_ref(
        trackId, sourceItemId, role, matchMethod, matchConfidence, isPreferred,
        isAvailable, isDownloaded, playable, downloadable, codec, container,
        bitRate, sampleRate, bitsPerSample, channels, lossless, createdAt, updatedAt
    )
    SELECT t.id, rf.id, 'primary', 'legacy_remote_file', 100, 1,
           CASE WHEN rf.isDeleted = 0 THEN 1 ELSE 0 END,
           0, 1, 1, t.codec, t.container, t.bitRate, t.sampleRate,
           t.bitsPerSample, t.channels, t.lossless, t.createdAt, t.updatedAt
    FROM track t
    JOIN remote_file rf ON rf.id = t.remoteFileId
    """.trimIndent(),
    """
    INSERT OR IGNORE INTO track_source_ref(
        trackId, sourceItemId, role, matchMethod, matchConfidence, isPreferred,
        isAvailable, isDownloaded, playable, downloadable, codec, container,
        bitRate, sampleRate, bitsPerSample, channels, lossless, createdAt, updatedAt
    )
    SELECT t.id, item.id, 'primary', 'legacy_source_path', 100, 1, 1, 0, 1, 1,
           t.codec, t.container, t.bitRate, t.sampleRate, t.bitsPerSample,
           t.channels, t.lossless, t.createdAt, t.updatedAt
    FROM track t
    JOIN source_item item
      ON item.sourceAccountId = t.sourceStorageId
     AND item.canonicalPath = t.sourcePath
    WHERE t.remoteFileId IS NULL
      AND t.sourceStorageId IS NOT NULL
      AND TRIM(COALESCE(t.sourcePath, '')) != ''
    """.trimIndent(),
    """
    CREATE TABLE IF NOT EXISTS source_sync_cursor (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        sourceAccountId INTEGER NOT NULL,
        libraryRootId INTEGER,
        cursorType TEXT NOT NULL,
        cursorValue TEXT,
        lastScanId TEXT,
        lastSyncAt INTEGER,
        FOREIGN KEY(sourceAccountId) REFERENCES source_account(id) ON UPDATE NO ACTION ON DELETE CASCADE,
        FOREIGN KEY(libraryRootId) REFERENCES library_root(id) ON UPDATE NO ACTION ON DELETE CASCADE
    )
    """.trimIndent(),
    "CREATE INDEX IF NOT EXISTS index_source_sync_cursor_sourceAccountId ON source_sync_cursor(sourceAccountId)",
    "CREATE INDEX IF NOT EXISTS index_source_sync_cursor_libraryRootId ON source_sync_cursor(libraryRootId)",
    "CREATE UNIQUE INDEX IF NOT EXISTS index_source_sync_cursor_sourceAccountId_libraryRootId_cursorType ON source_sync_cursor(sourceAccountId, libraryRootId, cursorType)",
    """
    INSERT OR IGNORE INTO source_sync_cursor(
        id, sourceAccountId, libraryRootId, cursorType, cursorValue, lastScanId, lastSyncAt
    )
    SELECT c.id, sf.storageId, c.selectedFolderId, 'delta',
           COALESCE(c.deltaLink, c.continuationToken), c.lastScanId, c.lastSyncAt
    FROM sync_cursor c
    JOIN selected_folder sf ON sf.id = c.selectedFolderId
    """.trimIndent(),
    """
    CREATE TABLE IF NOT EXISTS source_error (
        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
        sourceAccountId INTEGER NOT NULL,
        libraryRootId INTEGER,
        sourceItemId INTEGER,
        errorType TEXT NOT NULL,
        message TEXT NOT NULL,
        createdAt INTEGER NOT NULL,
        resolvedAt INTEGER,
        FOREIGN KEY(sourceAccountId) REFERENCES source_account(id) ON UPDATE NO ACTION ON DELETE CASCADE,
        FOREIGN KEY(libraryRootId) REFERENCES library_root(id) ON UPDATE NO ACTION ON DELETE SET NULL,
        FOREIGN KEY(sourceItemId) REFERENCES source_item(id) ON UPDATE NO ACTION ON DELETE SET NULL
    )
    """.trimIndent(),
    "CREATE INDEX IF NOT EXISTS index_source_error_sourceAccountId ON source_error(sourceAccountId)",
    "CREATE INDEX IF NOT EXISTS index_source_error_libraryRootId ON source_error(libraryRootId)",
    "CREATE INDEX IF NOT EXISTS index_source_error_sourceItemId ON source_error(sourceItemId)",
    "CREATE INDEX IF NOT EXISTS index_source_error_createdAt ON source_error(createdAt)",
    """
    CREATE TABLE track_new (
        id INTEGER NOT NULL PRIMARY KEY,
        title TEXT NOT NULL,
        sortTitle TEXT,
        albumId INTEGER,
        albumArtist TEXT,
        composer TEXT,
        comment TEXT,
        grouping TEXT,
        durationMs INTEGER,
        discNumber INTEGER,
        discTotal INTEGER,
        trackNumber INTEGER,
        trackTotal INTEGER,
        year INTEGER,
        date TEXT,
        sampleRate INTEGER,
        bitRate INTEGER,
        bitsPerSample INTEGER,
        channels INTEGER,
        channelLayout TEXT,
        codec TEXT,
        container TEXT,
        lossless INTEGER,
        createdAt INTEGER NOT NULL,
        updatedAt INTEGER NOT NULL,
        lastPlayedAt INTEGER,
        artist TEXT,
        lyricist TEXT,
        conductor TEXT,
        copyright TEXT,
        publisher TEXT,
        originalReleaseDate TEXT,
        bpm REAL,
        musicalKey TEXT,
        isrc TEXT,
        musicBrainzRecordingId TEXT,
        musicBrainzTrackId TEXT,
        musicBrainzReleaseId TEXT,
        musicBrainzReleaseGroupId TEXT,
        musicBrainzArtistId TEXT,
        musicBrainzReleaseArtistId TEXT,
        musicBrainzWorkId TEXT,
        replayGainTrackGain REAL,
        replayGainTrackPeak REAL,
        replayGainAlbumGain REAL,
        replayGainAlbumPeak REAL
    )
    """.trimIndent(),
    """
    INSERT INTO track_new(
        id, title, sortTitle, albumId, albumArtist, composer, comment, grouping,
        durationMs, discNumber, discTotal, trackNumber, trackTotal, year, date,
        sampleRate, bitRate, bitsPerSample, channels, channelLayout, codec,
        container, lossless, createdAt, updatedAt, lastPlayedAt, artist,
        lyricist, conductor, copyright, publisher, originalReleaseDate, bpm,
        musicalKey, isrc, musicBrainzRecordingId, musicBrainzTrackId,
        musicBrainzReleaseId, musicBrainzReleaseGroupId, musicBrainzArtistId,
        musicBrainzReleaseArtistId, musicBrainzWorkId, replayGainTrackGain,
        replayGainTrackPeak, replayGainAlbumGain, replayGainAlbumPeak
    )
    SELECT id, title, sortTitle, albumId, albumArtist, composer, comment, grouping,
           durationMs, discNumber, discTotal, trackNumber, trackTotal, year, date,
           sampleRate, bitRate, bitsPerSample, channels, channelLayout, codec,
           container, lossless, createdAt, updatedAt, lastPlayedAt, artist,
           lyricist, conductor, copyright, publisher, originalReleaseDate, bpm,
           musicalKey, isrc, musicBrainzRecordingId, musicBrainzTrackId,
           musicBrainzReleaseId, musicBrainzReleaseGroupId, musicBrainzArtistId,
           musicBrainzReleaseArtistId, musicBrainzWorkId, replayGainTrackGain,
           replayGainTrackPeak, replayGainAlbumGain, replayGainAlbumPeak
    FROM track
    """.trimIndent(),
    "DROP TABLE IF EXISTS track_fts",
    "DROP TABLE track",
    "ALTER TABLE track_new RENAME TO track",
    "CREATE INDEX IF NOT EXISTS index_track_albumId ON track(albumId)",
    "CREATE INDEX IF NOT EXISTS index_track_title ON track(title)",
    "CREATE INDEX IF NOT EXISTS index_track_musicBrainzRecordingId ON track(musicBrainzRecordingId)",
    "CREATE INDEX IF NOT EXISTS index_track_isrc ON track(isrc)",
    """
    CREATE TABLE import_job_new (
        id TEXT NOT NULL PRIMARY KEY,
        libraryRootId INTEGER NOT NULL,
        status TEXT NOT NULL,
        scannedCount INTEGER NOT NULL,
        importedCount INTEGER NOT NULL,
        skippedCount INTEGER NOT NULL,
        failedCount INTEGER NOT NULL,
        checkpoint TEXT,
        errorMessage TEXT,
        createdAt INTEGER NOT NULL,
        updatedAt INTEGER NOT NULL
    )
    """.trimIndent(),
    """
    INSERT INTO import_job_new(
        id, libraryRootId, status, scannedCount, importedCount, skippedCount,
        failedCount, checkpoint, errorMessage, createdAt, updatedAt
    )
    SELECT id, selectedFolderId, status, scannedCount, importedCount, skippedCount,
           failedCount, checkpoint, errorMessage, createdAt, updatedAt
    FROM import_job
    """.trimIndent(),
    "DROP TABLE import_job",
    "ALTER TABLE import_job_new RENAME TO import_job",
    "CREATE INDEX IF NOT EXISTS index_import_job_libraryRootId ON import_job(libraryRootId)",
    "CREATE INDEX IF NOT EXISTS index_import_job_status ON import_job(status)",
    """
    CREATE VIRTUAL TABLE IF NOT EXISTS track_fts USING fts4(
        title,
        artist,
        albumArtist,
        composer,
        content=`track`
    )
    """.trimIndent(),
    "INSERT INTO track_fts(track_fts) VALUES('rebuild')",
    "DROP TABLE IF EXISTS sync_cursor",
    "DROP TABLE IF EXISTS remote_file",
    "DROP TABLE IF EXISTS selected_folder",
    "DROP TABLE IF EXISTS storage",
    "PRAGMA foreign_keys=ON",
)



val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(connection: SQLiteConnection) {
        listOf(
           """
           CREATE TABLE IF NOT EXISTS plugin (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
               pluginId TEXT NOT NULL,
                name TEXT NOT NULL,
                versionCode INTEGER NOT NULL,
                versionName TEXT NOT NULL,
                author TEXT NOT NULL,
                description TEXT NOT NULL,
                apiVersion INTEGER NOT NULL,
                minHostApiVersion INTEGER NOT NULL,
                entryFile TEXT NOT NULL,
                includeDirsJson TEXT NOT NULL,
                iconPath TEXT,
                capabilitiesJson TEXT NOT NULL,
                manifestRawJson TEXT NOT NULL,
                installedAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                enabled INTEGER NOT NULL
            )
            """.trimIndent(),
            "CREATE UNIQUE INDEX IF NOT EXISTS index_plugin_pluginId ON plugin(pluginId)",
            """
            CREATE TABLE IF NOT EXISTS plugin_config (
                pluginId TEXT NOT NULL,
                configKey TEXT NOT NULL,
                configValue TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(pluginId, configKey)
            )
            """.trimIndent(),
       ).forEach { sql ->
           connection.prepare(sql).use { statement -> statement.step() }
       }
   }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(connection: SQLiteConnection) {
        listOf(
            """
            CREATE TABLE IF NOT EXISTS plugin_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                pluginId TEXT NOT NULL,
                name TEXT NOT NULL,
                versionCode INTEGER NOT NULL,
                versionName TEXT NOT NULL,
                author TEXT NOT NULL,
                description TEXT NOT NULL,
                apiVersion INTEGER NOT NULL,
                minHostApiVersion INTEGER NOT NULL,
                entryFile TEXT NOT NULL,
                includeDirsJson TEXT NOT NULL,
                iconPath TEXT,
                capabilitiesJson TEXT NOT NULL,
                manifestRawJson TEXT NOT NULL,
                installedAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                enabled INTEGER NOT NULL
            )
            """.trimIndent(),
            "INSERT OR IGNORE INTO plugin_new SELECT * FROM plugin",
            "DROP TABLE plugin",
            "ALTER TABLE plugin_new RENAME TO plugin",
            "CREATE UNIQUE INDEX IF NOT EXISTS index_plugin_pluginId ON plugin(pluginId)",
        ).forEach { sql ->
            connection.prepare(sql).use { statement -> statement.step() }
        }
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(connection: SQLiteConnection) {
        listOf(
            "ALTER TABLE source_account ADD COLUMN rootPath TEXT",
            "UPDATE source_account SET rootPath = '/' WHERE providerType = 'webdav'",
        ).forEach { sql ->
            connection.prepare(sql).use { statement -> statement.step() }
        }
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(connection: SQLiteConnection) {
        listOf(
            "ALTER TABLE import_job ADD COLUMN metadataScanMode TEXT NOT NULL DEFAULT 'Full'",
            "ALTER TABLE import_job ADD COLUMN metadataConcurrency INTEGER NOT NULL DEFAULT 8",
            "ALTER TABLE import_job ADD COLUMN importBatchSize INTEGER NOT NULL DEFAULT 200",
            "ALTER TABLE import_job ADD COLUMN scanSubdirectories INTEGER NOT NULL DEFAULT 1",
            "ALTER TABLE import_job ADD COLUMN ignoreShortAudio INTEGER NOT NULL DEFAULT 1",
            "ALTER TABLE import_job ADD COLUMN minDurationMs INTEGER NOT NULL DEFAULT 30000",
            "ALTER TABLE import_job ADD COLUMN ignoreHiddenFiles INTEGER NOT NULL DEFAULT 1",
            "ALTER TABLE import_job ADD COLUMN ignoredDirectoryNames TEXT NOT NULL DEFAULT '.cache|.trash|@eaDir|__MACOSX'",
            "ALTER TABLE import_job ADD COLUMN missingFilePolicy TEXT NOT NULL DEFAULT 'MarkUnavailable'",
            "ALTER TABLE import_job ADD COLUMN duplicateTrackPolicy TEXT NOT NULL DEFAULT 'SeparateBySource'",
            "ALTER TABLE import_job ADD COLUMN metadataRequestCount INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE import_job ADD COLUMN metadataFetchedBytes INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE import_job ADD COLUMN metadataElapsedMs INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE import_job ADD COLUMN artworkCachedBytes INTEGER NOT NULL DEFAULT 0",
        ).forEach { sql ->
            connection.prepare(sql).use { statement -> statement.step() }
        }
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(connection: SQLiteConnection) {
        listOf(
            "ALTER TABLE import_job ADD COLUMN syncMode TEXT NOT NULL DEFAULT 'LEGACY_FULL_SCAN_FALLBACK'",
            "ALTER TABLE import_job ADD COLUMN directoryConcurrency INTEGER NOT NULL DEFAULT 4",
            "ALTER TABLE import_job ADD COLUMN capabilityDetectionElapsedMs INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE import_job ADD COLUMN directoryScanElapsedMs INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE import_job ADD COLUMN directoryRequestCount INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE import_job ADD COLUMN listedDirectoryCount INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE import_job ADD COLUMN visitedEntryCount INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE import_job ADD COLUMN discoveredMusicCount INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE import_job ADD COLUMN unchangedCount INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE import_job ADD COLUMN addedCount INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE import_job ADD COLUMN modifiedCount INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE import_job ADD COLUMN renamedCount INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE import_job ADD COLUMN deletedCount INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE import_job ADD COLUMN databaseReadElapsedMs INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE import_job ADD COLUMN databaseWriteElapsedMs INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE import_job ADD COLUMN totalElapsedMs INTEGER NOT NULL DEFAULT 0",
        ).forEach { sql ->
            connection.prepare(sql).use { statement -> statement.step() }
        }
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(connection: SQLiteConnection) {
        listOf(
            "ALTER TABLE track ADD COLUMN metadataSource TEXT NOT NULL DEFAULT 'FILE'",
            "ALTER TABLE track ADD COLUMN metadataLocked INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE track ADD COLUMN metadataSourceId TEXT",
            "ALTER TABLE track ADD COLUMN metadataExternalId TEXT",
            "ALTER TABLE track ADD COLUMN metadataAppliedAt INTEGER",
        ).forEach { sql ->
            connection.prepare(sql).use { statement -> statement.step() }
        }
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(connection: SQLiteConnection) {
        listOf(
            """
            CREATE TABLE IF NOT EXISTS lyrics_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                trackId INTEGER NOT NULL,
                format TEXT NOT NULL,
                language TEXT,
                synchronized INTEGER NOT NULL,
                content TEXT NOT NULL,
                sourcePath TEXT,
                sourceKind TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(trackId) REFERENCES track(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
            """
            INSERT INTO lyrics_new (
                id, trackId, format, language, synchronized, content, sourcePath, sourceKind, updatedAt
            )
            SELECT id, trackId, format, language, synchronized, content, sourcePath,
                   CASE
                       WHEN sourcePath IS NOT NULL AND TRIM(sourcePath) != '' THEN
                           CASE WHEN UPPER(format) = 'TTML' THEN 'ExternalTtml' ELSE 'ExternalPlain' END
                       ELSE
                           CASE WHEN UPPER(format) = 'TTML' THEN 'EmbeddedTtml' ELSE 'EmbeddedPlain' END
                   END,
                   updatedAt
            FROM lyrics
            """.trimIndent(),
            "DROP TABLE lyrics",
            "ALTER TABLE lyrics_new RENAME TO lyrics",
            "CREATE UNIQUE INDEX IF NOT EXISTS index_lyrics_trackId_sourceKind ON lyrics(trackId, sourceKind)",
        ).forEach { sql ->
            connection.prepare(sql).use { statement -> statement.step() }
        }
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(connection: SQLiteConnection) {
        connection.prepare(
            "ALTER TABLE source_account ADD COLUMN providerConfig TEXT"
        ).use { statement -> statement.step() }
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(connection: SQLiteConnection) {
        listOf(
            """
            CREATE TABLE IF NOT EXISTS listening_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                trackId INTEGER NOT NULL,
                title TEXT NOT NULL,
                artist TEXT,
                album TEXT,
                durationMs INTEGER,
                listenedMs INTEGER NOT NULL,
                playedAtEpochMs INTEGER NOT NULL
            )
            """.trimIndent(),
            "CREATE INDEX IF NOT EXISTS index_listening_history_trackId ON listening_history(trackId)",
            "CREATE INDEX IF NOT EXISTS index_listening_history_playedAtEpochMs ON listening_history(playedAtEpochMs)",
        ).forEach { sql ->
            connection.prepare(sql).use { statement -> statement.step() }
        }
    }
}

val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(connection: SQLiteConnection) {
        connection.prepare(
            "ALTER TABLE track_source_ref ADD COLUMN hasEmbeddedArtwork INTEGER"
        ).use { statement -> statement.step() }
    }
}

val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(connection: SQLiteConnection) {
        connection.prepare(
            "ALTER TABLE track_source_ref ADD COLUMN embeddedLyricsKind TEXT"
        ).use { statement -> statement.step() }
    }
}

val MIGRATION_20_21 = object : Migration(20, 21) {
    override fun migrate(connection: SQLiteConnection) {
        connection.prepare(
            "ALTER TABLE download_task ADD COLUMN finalizationWarning TEXT"
        ).use { statement -> statement.step() }
    }
}

val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(connection: SQLiteConnection) {
        connection.prepare(
            "ALTER TABLE track_source_ref ADD COLUMN channelLayout TEXT"
        ).use { statement -> statement.step() }
    }
}

val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(connection: SQLiteConnection) {
        connection.prepare("ALTER TABLE lyrics ADD COLUMN structuredContent TEXT").use { statement ->
            statement.step()
        }
    }
}

val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(connection: SQLiteConnection) {
        listOf(
            "ALTER TABLE playlist ADD COLUMN providerType TEXT",
            "ALTER TABLE playlist ADD COLUMN sourceAccountId INTEGER",
            "ALTER TABLE playlist ADD COLUMN remotePlaylistId TEXT",
            "CREATE UNIQUE INDEX IF NOT EXISTS index_playlist_providerType_sourceAccountId_remotePlaylistId " +
                "ON playlist(providerType, sourceAccountId, remotePlaylistId)",
        ).forEach { sql ->
            connection.prepare(sql).use { statement -> statement.step() }
        }
    }
}
