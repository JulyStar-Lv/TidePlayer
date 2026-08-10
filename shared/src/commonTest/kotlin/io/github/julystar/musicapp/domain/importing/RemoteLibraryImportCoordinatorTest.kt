package io.github.julystar.musicapp.domain.importing

import io.github.julystar.musicapp.database.ArtworkEntity
import io.github.julystar.musicapp.database.SourceItemEntity
import io.github.julystar.musicapp.database.SourceItemSignature
import io.github.julystar.musicapp.database.SourceItemTypes
import io.github.julystar.musicapp.database.TrackMetadataSources
import io.github.julystar.musicapp.core.domain.model.MetadataScanMode
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncScanRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import uniffi.app_backend.RemoteEmbeddedLyrics
import uniffi.app_backend.RemoteArtwork
import uniffi.app_backend.RemoteMetadata
import uniffi.app_backend.RemoteMetadataResult
import uniffi.app_backend.RemoteRawMetadataEntry
import uniffi.app_backend.OneDriveDeltaItem
import uniffi.app_backend.StorageEntry
import uniffi.app_backend.StorageEntryLoc
import uniffi.app_backend.StorageId

class RemoteLibraryImportCoordinatorTest {
    @Test
    fun pluginArtworkLookupRunsOnlyWhenEmbeddedArtworkIsExplicitlyMissing() {
        assertTrue(shouldLookupPluginArtwork(false))
        assertFalse(shouldLookupPluginArtwork(true))
        assertFalse(shouldLookupPluginArtwork(null))
    }

    @Test
    fun activeImportOperationCancelRequestsStopWithoutPauseFlag() = runBlocking {
        val operation = ActiveImportOperation()

        operation.cancel()

        assertFailsWith<CancellationException> {
            operation.throwIfStopRequested()
        }
        assertFalse(operation.isPauseRequested())
    }

    @Test
    fun activeImportOperationPauseRequestsStopWithPauseFlag() = runBlocking {
        val operation = ActiveImportOperation()

        operation.pause()

        assertFailsWith<CancellationException> {
            operation.throwIfStopRequested()
        }
        assertTrue(operation.isPauseRequested())
    }

    @Test
    fun activeImportOperationKeepsFirstStopReason() = runBlocking {
        val cancelled = ActiveImportOperation()
        cancelled.cancel()
        cancelled.pause()

        val paused = ActiveImportOperation()
        paused.pause()
        paused.cancel()

        assertFalse(cancelled.isPauseRequested())
        assertTrue(paused.isPauseRequested())
    }

    @Test
    fun oneDriveDeltaMapsStableIdentityAndParent() {
        val item = deltaItem(
            remoteId = "file-id",
            parentRemoteId = "folder-id",
            path = "/Music/Renamed.flac",
        )

        val entry = item.toStorageEntry(storageId = 9)

        assertEquals(StorageId(9), entry.storageId)
        assertEquals("file-id", entry.remoteId)
        assertEquals("folder-id", entry.parentRemoteId)
        assertEquals("/Music/Renamed.flac", entry.path)
        assertTrue(item.isSupportedMusicFile())
    }

    @Test
    fun legacyAndroidLocalRootPathMapsOnlyLogicalPrimaryPaths() {
        assertEquals(
            "/storage/emulated/0/Music/Albums",
            "/Music/Albums".toLegacyAndroidPrimaryStoragePath(),
        )
        assertEquals(
            "/storage/emulated/0",
            "/".toLegacyAndroidPrimaryStoragePath(),
        )
        assertEquals(
            null,
            "/storage/emulated/0/Music".toLegacyAndroidPrimaryStoragePath(),
        )
    }

    @Test
    fun oneDriveDeltaOnlyResyncsForLiveFileWithoutPath() {
        assertFalse(
            requiresOneDriveResync(
                listOf(deltaItem(remoteId = "folder", isDir = true))
            )
        )
        assertFalse(
            requiresOneDriveResync(
                listOf(deltaItem(remoteId = "unknown", deleted = true, path = null))
            )
        )
        assertTrue(
            requiresOneDriveResync(
                listOf(deltaItem(remoteId = "live", deleted = false, path = null))
            )
        )
    }

    @Test
    fun preparesLargeSnapshotsInStablePathOrder() {
        val entries = buildList {
            add(entry(path = "/Music/z.flac", name = "z.flac"))
            add(entry(path = "/Music/readme.txt", name = "readme.txt"))
            add(entry(path = "/Music/a.flac", name = "a.flac"))
            add(entry(path = "Music/a.flac", name = "duplicate.flac"))
            repeat(1_005) { index ->
                add(
                    entry(
                        path = "/Music/Album/track-${index.toString().padStart(4, '0')}.mp3",
                        name = "track-$index.mp3",
                    )
                )
            }
        }

        val prepared = prepareMusicEntries(storageId = 1, entries = entries)
        val batches = prepared.chunked(DEFAULT_IMPORT_BATCH_SIZE)

        assertEquals(1_007, prepared.size)
        assertEquals("/Music/Album/track-0000.mp3", prepared.first().path)
        assertEquals("/Music/z.flac", prepared.last().path)
        assertEquals(6, batches.size)
        assertTrue(batches.all { it.size <= DEFAULT_IMPORT_BATCH_SIZE })
    }

    @Test
    fun musicFiltersExcludeVideoMp4AndKeepAudioMp4() {
        val video = entry(
            path = "/Photos/clip.mp4",
            name = "clip.mp4",
            mimeType = "video/mp4",
        )
        val audio = entry(
            path = "/Music/track.mp4",
            name = "track.mp4",
            mimeType = "audio/mp4",
        )
        val unknown = entry(
            path = "/Music/legacy.mp4",
            name = "legacy.mp4",
            mimeType = null,
        )

        assertFalse(isSupportedMusicEntry(video))
        assertTrue(isSupportedMusicEntry(audio))
        assertTrue(isSupportedMusicEntry(unknown))

        assertFalse(
            deltaItem(
                remoteId = "video-id",
                path = "/Photos/clip.mp4",
                mimeType = "video/mp4; codecs=avc1",
            ).isSupportedMusicFile()
        )
        assertTrue(
            deltaItem(
                remoteId = "audio-id",
                path = "/Music/track.mp4",
                mimeType = "audio/mp4",
            ).isSupportedMusicFile()
        )
        assertTrue(isSupportedMusicEntry(entry(path = "/Music/album.ape", name = "album.ape")))
        assertTrue(isSupportedMusicEntry(entry(path = "/Music/album.wv", name = "album.wv")))
    }

    @Test
    fun discoveredMusicEntriesKeepLargeRecursiveWebDavCounts() {
        val entries = buildList {
            repeat(805) { index ->
                add(
                    entry(
                        path = "/Music/Album-${index / 25}/track-${index.toString().padStart(4, '0')}.flac",
                        name = "track-$index.flac",
                    )
                )
            }
            add(entry(path = "/Music/.hidden/secret.flac", name = "secret.flac"))
            add(entry(path = "/Music/__MACOSX/sidecar.flac", name = "sidecar.flac"))
        }

        val discovered = prepareDiscoveredMusicEntries(
            storageId = 1,
            rootPath = "/Music",
            rules = LibrarySyncScanRules(),
            seenPaths = mutableSetOf(),
            entries = entries,
        )

        assertEquals(805, discovered.size)
        assertEquals("/Music/Album-0/track-0000.flac", discovered.first().path)
    }

    @Test
    fun discoveredMusicEntriesCanDisableRecursiveScan() {
        val discovered = prepareDiscoveredMusicEntries(
            storageId = 1,
            rootPath = "/Music",
            rules = LibrarySyncScanRules(scanSubdirectories = false),
            seenPaths = mutableSetOf(),
            entries = listOf(
                entry(path = "/Music/root.flac", name = "root.flac"),
                entry(path = "/Music/Album/nested.flac", name = "nested.flac"),
            ),
        )

        assertEquals(listOf("/Music/root.flac"), discovered.map { it.path })
    }

    @Test
    fun completeSnapshotSkipsUnchangedWithoutSchedulingDatabaseItemUpdate() {
        val signature = signature(
            id = 11,
            path = "/Music/unchanged.flac",
            etag = "W/\"same\"",
        )

        val plan = planCompleteSnapshotBatch(
            entries = listOf(
                entry(
                    path = "/Music/unchanged.flac",
                    name = "unchanged.flac",
                    etag = "W/\"same\"",
                )
            ),
            existingByPath = mapOf(requireNotNull(signature.canonicalPath) to signature),
        )

        assertEquals(emptyList(), plan.entriesToImport)
        assertEquals(setOf(11L), plan.matchedExistingIds)
        assertEquals(1, plan.unchangedCount)
    }

    @Test
    fun completeSnapshotTreatsChangedOrInsufficientSignaturesAsChanges() {
        val base = signature(id = 11, path = "/Music/song.flac", etag = "\"old\"")
        val changedEtag = planCompleteSnapshotBatch(
            entries = listOf(entry("/Music/song.flac", "song.flac", etag = "\"new\"")),
            existingByPath = mapOf("/Music/song.flac" to base),
        )
        val changedSize = planCompleteSnapshotBatch(
            entries = listOf(
                entry("/Music/song.flac", "song.flac", etag = "\"old\"", size = 101uL)
            ),
            existingByPath = mapOf("/Music/song.flac" to base),
        )
        val missingRevision = planCompleteSnapshotBatch(
            entries = listOf(
                entry("/Music/song.flac", "song.flac", etag = null, modifiedAt = null)
            ),
            existingByPath = mapOf(
                "/Music/song.flac" to base.copy(etag = null, modifiedAtRemote = null)
            ),
        )

        assertEquals(1, changedEtag.entriesToImport.size)
        assertEquals(1, changedSize.entriesToImport.size)
        assertEquals(1, missingRevision.entriesToImport.size)
    }

    @Test
    fun completeSnapshotUsesStableRemoteIdBeforePathAndDetectsIdentityReplacement() {
        val moved = signature(
            id = 11,
            path = "/Music/Old/song.flac",
            etag = "\"same\"",
            remoteId = "stable-id",
        )
        val pathOccupant = signature(
            id = 12,
            path = "/Music/New/song.flac",
            etag = "\"same\"",
            remoteId = "other-id",
        )
        val plan = planCompleteSnapshotBatch(
            entries = listOf(
                entry(
                    path = "/Music/New/song.flac",
                    name = "song.flac",
                    etag = "\"same\"",
                    remoteId = "stable-id",
                )
            ),
            existingByPath = mapOf("/Music/New/song.flac" to pathOccupant),
            existingByRemoteId = mapOf("stable-id" to moved),
        )
        val replacement = planCompleteSnapshotBatch(
            entries = listOf(
                entry(
                    path = "/Music/New/song.flac",
                    name = "song.flac",
                    etag = "\"same\"",
                    remoteId = "replacement-id",
                )
            ),
            existingByPath = mapOf("/Music/New/song.flac" to pathOccupant),
            existingByRemoteId = emptyMap(),
        )

        assertEquals(setOf(11L), plan.matchedExistingIds)
        assertEquals(1, plan.entriesToImport.size)
        assertEquals(setOf(12L), replacement.matchedExistingIds)
        assertEquals(1, replacement.entriesToImport.size)
    }

    @Test
    fun webDavMoveMatchingRequiresUniqueStableEtagAndFreeDestination() {
        val previous = sourceItem(
            id = 11,
            canonicalPath = "/Music/Old/song.flac",
            etag = "W/\"same\"",
        )
        val movedEntry = entry(
            path = "/Music/New/song.flac",
            name = "song.flac",
            etag = "W/\"same\"",
        )

        assertEquals(
            mapOf("/Music/New/song.flac" to previous),
            matchWebDavMoves(listOf(movedEntry), listOf(previous)),
        )
        assertEquals(
            emptyMap(),
            matchWebDavMoves(
                listOf(movedEntry),
                listOf(previous),
                occupiedLivePaths = setOf("/Music/New/song.flac"),
            ),
        )
        assertEquals(
            emptyMap(),
            matchWebDavMoves(
                listOf(movedEntry),
                listOf(previous, previous.copy(id = 12, canonicalPath = "/Music/Other/song.flac")),
            ),
        )
    }

    @Test
    fun webDavDailyRescanUsesFastUnlessFullWasExplicitlyRequested() {
        assertEquals(
            MetadataScanMode.Standard,
            webDavMetadataModeFor(false, MetadataScanMode.Standard),
        )
        assertEquals(
            MetadataScanMode.Fast,
            webDavMetadataModeFor(true, MetadataScanMode.Standard),
        )
        assertEquals(
            MetadataScanMode.Full,
            webDavMetadataModeFor(true, MetadataScanMode.Full),
        )
    }

    @Test
    fun planSkipsUnchangedFilesAndKeepsChangedMusicOnly() {
        val unchanged = sourceItem(
            id = 11,
            canonicalPath = "/Music/unchanged.flac",
            etag = "\"same\"",
        )
        val plan = planRemoteLibraryImport(
            storageId = 1,
            libraryRootId = 7,
            scanId = "scan-1",
            now = 100,
            entries = listOf(
                entry(path = "/Music/unchanged.flac", name = "unchanged.flac", etag = "\"same\""),
                entry(path = "/Music/changed.mp3", name = "changed.mp3", etag = "\"new\""),
                entry(path = "/Music/readme.txt", name = "readme.txt", etag = "\"new\""),
                entry(path = "/Music/Sub", name = "Sub", isDir = true),
            ).filter(::isSupportedMusicEntry),
            existing = mapOf(unchanged.canonicalPath to unchanged),
        )

        assertEquals(listOf(11L), plan.unchangedFileIds)
        assertEquals(listOf("/Music/changed.mp3"), plan.changedEntries.map { it.path })
        assertEquals(listOf("/Music/changed.mp3"), plan.changedItems.map { it.canonicalPath })
        assertEquals(listOf("/Music/changed.mp3"), plan.metadataEntries.map { it.path })
        assertEquals(1, plan.changedCount)
        assertEquals(1, plan.metadataSkippedCount)
        assertEquals(0, plan.unreadableChangedCount)
    }

    @Test
    fun planRestoresDeletedUnchangedFileWithoutReadingMetadata() {
        val deleted = sourceItem(
            id = 11,
            canonicalPath = "/Music/restored.flac",
            etag = "\"same\"",
            remoteId = "remote-track-1",
        ).copy(isDeleted = true)
        val plan = planRemoteLibraryImport(
            storageId = 1,
            libraryRootId = 7,
            scanId = "scan-restore",
            now = 100,
            entries = listOf(
                entry(
                    path = "/Music/restored.flac",
                    name = "restored.flac",
                    etag = "\"same\"",
                    remoteId = "remote-track-1",
                )
            ),
            existing = mapOf(deleted.canonicalPath to deleted),
        )

        assertTrue(plan.changedEntries.isEmpty())
        assertTrue(plan.metadataEntries.isEmpty())
        assertTrue(plan.unchangedFileIds.isEmpty())
        assertEquals(1, plan.changedCount)
        assertEquals(0, plan.unchangedCount)
        assertEquals(1, plan.modifiedCount)
        assertEquals(1, plan.metadataSkippedCount)
        val restored = plan.changedItems.single()
        assertEquals(11, restored.id)
        assertFalse(restored.isDeleted)
        assertEquals("scan-restore", restored.lastSeenScanId)
    }

    @Test
    fun failureDetailsKeepMetadataReadErrorsForFailedFiles() {
        val broken = entry(path = "/Music/Broken.flac", name = "Broken.flac")
        val missing = entry(path = "/Music/Missing.flac", name = "Missing.flac")
        val plan = planRemoteLibraryImport(
            storageId = 1,
            libraryRootId = 7,
            scanId = "scan-failed",
            now = 100,
            entries = listOf(broken, missing),
            existing = emptyMap(),
        )

        val details = buildImportFailureDetails(
            plan = plan,
            metadataResults = listOf(
                RemoteMetadataResult(
                    requestIndex = 0u,
                    entry = StorageEntryLoc(StorageId(1), broken.path),
                    metadata = null,
                    error = "unsupported container",
                )
            ),
        )

        assertEquals(2, details.size)
        assertTrue(details.any { it.path == broken.path && it.message.contains("unsupported container") })
        assertTrue(details.any { it.path == missing.path && it.message.contains("元数据读取无返回结果") })
    }

    @Test
    fun moveByStableRemoteIdUpdatesPathWithoutMetadataRead() {
        val previous = sourceItem(
            id = 11,
            canonicalPath = "/Music/Old/song.flac",
            etag = "\"same\"",
            remoteId = "drive-item-1",
        )
        val moved = entry(
            path = "/Music/New/song.flac",
            name = "song.flac",
            etag = "\"same\"",
            remoteId = "drive-item-1",
        )

        val plan = planRemoteLibraryImport(
            storageId = 1,
            libraryRootId = 7,
            scanId = "scan-move",
            now = 200,
            entries = listOf(moved),
            existing = emptyMap(),
            existingByRemoteId = mapOf("drive-item-1" to previous),
        )

        assertTrue(plan.changedEntries.isEmpty())
        assertTrue(plan.metadataEntries.isEmpty())
        assertEquals(1, plan.changedCount)
        assertEquals(1, plan.metadataSkippedCount)
        assertEquals(11, plan.changedItems.single().id)
        assertEquals("/Music/New/song.flac", plan.changedItems.single().canonicalPath)
        assertEquals("scan-move", plan.changedItems.single().lastSeenScanId)
    }

    @Test
    fun trackIdIsStableAndPositive() {
        val lhs = stableTrackId(1, "/Music/track.flac")
        val rhs = stableTrackId(1, "Music/track.flac")

        assertEquals(lhs, rhs)
        assertTrue(lhs > 0)
        assertFalse(lhs == stableTrackId(2, "/Music/track.flac"))
    }

    @Test
    fun mapsRemoteMetadataToTrackEntity() {
        val entry = entry(path = "/Music/Song.flac", name = "Song.flac")
        val sourceItem = sourceItem(id = 42, canonicalPath = "/Music/Song.flac")
        val metadata = metadata(
            title = "Metadata Title",
            artist = "Artist",
            albumArtist = "Album Artist",
            date = "2025-01-02",
            trackNumber = 3u,
            trackTotal = 9u,
            discNumber = 1u,
            discTotal = 2u,
            durationMs = 181_000uL,
            sampleRate = 48_000u,
            bitDepth = 24u.toUByte(),
            channels = 2u.toUByte(),
            overallBitrate = 950u,
            audioBitrate = 900u,
        )
        val track = buildTrackEntity(
            entry = entry,
            metadata = metadata,
            sourceItem = sourceItem,
            now = 1000,
        )
        val ref = buildTrackSourceRefEntity(
            track = track,
            sourceItem = sourceItem,
            metadata = metadata,
            now = 1000,
        )

        assertEquals(track.id, ref.trackId)
        assertEquals(42, ref.sourceItemId)
        assertEquals("source_identity", ref.matchMethod)
        assertEquals(true, ref.isAvailable)
        assertEquals(false, ref.hasEmbeddedArtwork)
        assertEquals("Metadata Title", track.title)
        assertEquals("Album Artist", track.albumArtist)
        assertEquals(181_000, track.durationMs)
        assertEquals(3, track.trackNumber)
        assertEquals(2025, track.year)
        assertEquals(48_000, track.sampleRate)
        assertEquals(900, track.bitRate)
        assertEquals(24, track.bitsPerSample)
        assertEquals(2, track.channels)
        assertEquals("Artist", track.artist)
        assertEquals("Composer", track.composer)
        assertEquals("Lyricist", track.lyricist)
        assertEquals("Conductor", track.conductor)
        assertEquals("Copyright", track.copyright)
        assertEquals("Publisher", track.publisher)
        assertEquals("1999-01-01", track.originalReleaseDate)
        assertEquals(128.5, track.bpm)
        assertEquals("8A", track.musicalKey)
        assertEquals("US-AAA-26-00001", track.isrc)
        assertEquals("recording-id", track.musicBrainzRecordingId)
        assertEquals(-7.25, track.replayGainTrackGain)
        assertEquals("FLAC", track.codec)
        assertEquals(true, track.lossless)
    }

    @Test
    fun metadataRefreshPreservesTrackIdentityAfterMove() {
        val previousTrack = buildTrackEntity(
            entry = entry(path = "/Music/Old.flac", name = "Old.flac"),
            metadata = metadata(title = "Old"),
            sourceItem = sourceItem(id = 42, canonicalPath = "/Music/Old.flac"),
            now = 100,
        )
        val refreshed = buildTrackEntity(
            entry = entry(path = "/Music/New.flac", name = "New.flac"),
            metadata = metadata(title = "New"),
            sourceItem = sourceItem(id = 42, canonicalPath = "/Music/New.flac"),
            now = 200,
            existingTrack = previousTrack,
        )

        assertEquals(previousTrack.id, refreshed.id)
        assertEquals(previousTrack.createdAt, refreshed.createdAt)
        assertEquals(200, refreshed.updatedAt)
        assertEquals("New", refreshed.title)
    }

    @Test
    fun crossSourceMatchPreservesCanonicalMetadataAndRecordsEvidence() {
        val canonicalEntry = entry(path = "/Music/Canonical.flac", name = "Canonical.flac")
        val canonical = buildTrackEntity(
            entry = canonicalEntry,
            metadata = metadata(title = "Canonical title", artist = "Canonical artist", sampleRate = 44_100u),
            sourceItem = sourceItem(id = 41, canonicalPath = canonicalEntry.path),
            now = 100,
        )
        val sourceItem = sourceItem(id = 42, canonicalPath = "/Mirror/Other.flac")
        val sourceMetadata = metadata(title = "Other title", artist = "Other artist", sampleRate = 96_000u)
        val preserved = buildTrackEntity(
            entry = entry(path = "/Mirror/Other.flac", name = "Other.flac"),
            metadata = sourceMetadata,
            sourceItem = sourceItem,
            now = 200,
            existingTrack = canonical,
            preserveExistingMetadata = true,
        )
        val ref = buildTrackSourceRefEntity(
            track = preserved,
            sourceItem = sourceItem,
            metadata = sourceMetadata,
            now = 200,
            role = TrackSourceRoles.Alternate,
            matchMethod = TrackMatchMethods.IsrcDuration,
            matchConfidence = MATCH_CONFIDENCE_ISRC,
        )

        assertEquals(canonical.id, preserved.id)
        assertEquals("Canonical title", preserved.title)
        assertEquals("Canonical artist", preserved.artist)
        assertEquals(44_100, preserved.sampleRate)
        assertEquals(96_000, ref.sampleRate)
        assertEquals("alternate", ref.role)
        assertEquals("isrc_duration", ref.matchMethod)
        assertEquals(95, ref.matchConfidence)
    }

    @Test
    fun metadataRefreshProtectsPluginFieldsUntilExplicitFileReset() {
        val entry = entry(path = "/Music/Song.flac", name = "Song.flac")
        val sourceItem = sourceItem(id = 42, canonicalPath = entry.path)
        val pluginTrack = buildTrackEntity(
            entry = entry,
            metadata = metadata(title = "File title", artist = "File artist", sampleRate = 44_100u),
            sourceItem = sourceItem,
            now = 100,
            albumId = 7,
        ).copy(
            title = "Plugin title",
            artist = "Plugin artist",
            albumId = 9,
            lastPlayedAt = 90,
            metadataSource = TrackMetadataSources.Plugin,
            metadataLocked = true,
            metadataSourceId = "example.plugin",
            metadataExternalId = "song-1",
            metadataAppliedAt = 110,
        )

        val backgroundRefresh = buildTrackEntity(
            entry = entry,
            metadata = metadata(title = "Changed file title", artist = "Changed file artist", sampleRate = 48_000u),
            sourceItem = sourceItem,
            now = 200,
            existingTrack = pluginTrack,
            albumId = 8,
        )

        assertEquals("Plugin title", backgroundRefresh.title)
        assertEquals("Plugin artist", backgroundRefresh.artist)
        assertEquals(9, backgroundRefresh.albumId)
        assertEquals(48_000, backgroundRefresh.sampleRate)
        assertEquals(90, backgroundRefresh.lastPlayedAt)
        assertTrue(backgroundRefresh.metadataLocked)
        assertEquals("example.plugin", backgroundRefresh.metadataSourceId)

        val reset = buildTrackEntity(
            entry = entry,
            metadata = metadata(title = "Changed file title", artist = "Changed file artist", sampleRate = 48_000u),
            sourceItem = sourceItem,
            now = 300,
            existingTrack = backgroundRefresh,
            albumId = 8,
            respectMetadataLock = false,
        )

        assertEquals("Changed file title", reset.title)
        assertEquals("Changed file artist", reset.artist)
        assertEquals(8, reset.albumId)
        assertEquals(90, reset.lastPlayedAt)
        assertEquals(TrackMetadataSources.File, reset.metadataSource)
        assertFalse(reset.metadataLocked)
        assertEquals(null, reset.metadataSourceId)
        assertEquals(null, reset.metadataExternalId)
        assertEquals(null, reset.metadataAppliedAt)
    }

    @Test
    fun mapsEmbeddedLyricsAndRawTagsForRoom() {
        val metadata = metadata(title = "Song")

        val lyrics = buildLyricsEntity(trackId = 9, metadata = metadata, now = 500)
        val raw = buildRawMetadataEntities(trackId = 9, metadata = metadata)

        assertEquals(9, lyrics?.trackId)
        assertEquals("LRC", lyrics?.format)
        assertEquals("eng", lyrics?.language)
        assertEquals("[00:01.00]Line", lyrics?.content)
        assertEquals(500, lyrics?.updatedAt)
        assertEquals(1, raw.size)
        assertEquals("Composer", raw.single().tagKey)
        assertEquals("Composer", raw.single().value)
    }

    @Test
    fun mapsRemoteArtworkForRoomCacheMetadata() {
        val trackArtwork = buildArtworkEntity(
            trackId = 9,
            albumId = null,
            artwork = artwork(localPath = "/cache/artwork/track.jpg"),
        )
        val albumArtwork = buildArtworkEntity(
            trackId = 9,
            albumId = 90,
            artwork = artwork(localPath = "/cache/artwork/album.png", mimeType = "image/png"),
        )

        assertEquals(9, trackArtwork.trackId)
        assertEquals(null, trackArtwork.albumId)
        assertEquals("/cache/artwork/track.jpg", trackArtwork.localPath)
        assertEquals("image/jpeg", trackArtwork.mimeType)
        assertEquals("CoverFront", trackArtwork.pictureType)
        assertEquals(null, albumArtwork.trackId)
        assertEquals(90, albumArtwork.albumId)
        assertEquals("/cache/artwork/album.png", albumArtwork.localPath)
        assertEquals("image/png", albumArtwork.mimeType)
    }

    @Test
    fun refreshesExistingArtworkCachePathWithoutChangingItsLibraryAssociation() {
        val existing = ArtworkEntity(
            id = 7,
            trackId = null,
            albumId = 90,
            contentHash = "same-content",
            localPath = "/old-container/Library/Caches/artwork/same-content.jpg",
            thumbnailPath = null,
            width = 512,
            height = 512,
            mimeType = "image/jpeg",
            pictureType = "CoverFront",
        )
        val refreshed = existing.withRefreshedCacheMetadata(
            existing.copy(
                trackId = 9,
                albumId = null,
                localPath = "/current-container/Library/Caches/artwork/same-content.jpg",
                thumbnailPath = "/current-container/Library/Caches/artwork/same-content-thumb.jpg",
            ),
        )

        assertEquals(7, refreshed.id)
        assertEquals(null, refreshed.trackId)
        assertEquals(90, refreshed.albumId)
        assertEquals("/current-container/Library/Caches/artwork/same-content.jpg", refreshed.localPath)
        assertEquals(
            "/current-container/Library/Caches/artwork/same-content-thumb.jpg",
            refreshed.thumbnailPath,
        )
    }

    private fun sourceItem(
        id: Long,
        canonicalPath: String,
        etag: String? = "\"same\"",
        remoteId: String? = null,
    ) = SourceItemEntity(
        id = id,
        sourceAccountId = 1,
        libraryRootId = 7,
        itemType = SourceItemTypes.Track,
        providerItemId = remoteId,
        parentProviderItemId = null,
        canonicalPath = canonicalPath,
        displayPath = canonicalPath,
        displayName = canonicalPath.substringAfterLast('/'),
        mimeType = "audio/flac",
        sizeBytes = 100,
        etag = etag,
        revision = null,
        createdAtRemote = 10,
        modifiedAtRemote = 20,
        contentHash = null,
        audioFingerprint = null,
        isDeleted = false,
        firstSyncedAt = 1,
        lastSyncedAt = 2,
        lastSeenScanId = "previous",
    )

    private fun signature(
        id: Long,
        path: String,
        etag: String?,
        remoteId: String? = null,
    ) = SourceItemSignature(
        id = id,
        providerItemId = remoteId,
        canonicalPath = path,
        sizeBytes = 100,
        etag = etag,
        revision = null,
        modifiedAtRemote = 20,
        isDeleted = false,
    )

    private fun entry(
        path: String,
        name: String,
        etag: String? = "\"same\"",
        isDir: Boolean = false,
        remoteId: String? = null,
        size: ULong? = if (isDir) null else 100uL,
        modifiedAt: Long? = 20,
        mimeType: String? = if (isDir) null else "audio/flac",
    ) = StorageEntry(
        storageId = StorageId(1),
        name = name,
        path = path,
        size = size,
        isDir = isDir,
        remoteId = remoteId,
        parentRemoteId = null,
        mimeType = mimeType,
        etag = etag,
        ctag = null,
        createdAt = 10,
        modifiedAt = modifiedAt,
    )

    private fun metadata(
        title: String,
        artist: String? = null,
        albumArtist: String? = null,
        date: String? = null,
        trackNumber: UInt? = null,
        trackTotal: UInt? = null,
        discNumber: UInt? = null,
        discTotal: UInt? = null,
        durationMs: ULong = 1uL,
        sampleRate: UInt? = null,
        bitDepth: UByte? = null,
        channels: UByte? = null,
        overallBitrate: UInt? = null,
        audioBitrate: UInt? = null,
        artwork: RemoteArtwork? = null,
    ) = RemoteMetadata(
        title = title,
        artist = artist,
        artists = listOfNotNull(artist),
        albumArtist = albumArtist,
        album = "Album",
        composer = "Composer",
        lyricist = "Lyricist",
        conductor = "Conductor",
        genre = "Jazz",
        grouping = "Suite",
        comment = "Comment",
        copyright = "Copyright",
        publisher = "Publisher",
        date = date,
        originalReleaseDate = "1999-01-01",
        trackNumber = trackNumber,
        trackTotal = trackTotal,
        discNumber = discNumber,
        discTotal = discTotal,
        bpm = 128.5,
        musicalKey = "8A",
        isrc = "US-AAA-26-00001",
        musicbrainzRecordingId = "recording-id",
        musicbrainzTrackId = "track-id",
        musicbrainzReleaseId = "release-id",
        musicbrainzReleaseGroupId = "release-group-id",
        musicbrainzArtistId = "artist-id",
        musicbrainzReleaseArtistId = "release-artist-id",
        musicbrainzWorkId = "work-id",
        replayGainTrackGain = -7.25,
        replayGainTrackPeak = 0.98,
        replayGainAlbumGain = -6.0,
        replayGainAlbumPeak = 0.99,
        lyrics = RemoteEmbeddedLyrics(
            content = "[00:01.00]Line",
            synchronized = true,
            language = "eng",
            description = "main",
        ),
        embeddedLyricsKind = "LineTimed",
        artwork = artwork,
        hasEmbeddedArtwork = artwork != null,
        rawMetadata = listOf(
            RemoteRawMetadataEntry(
                key = "Composer",
                value = "Composer",
                locale = null,
                description = null,
            )
        ),
        durationMs = durationMs,
        sampleRate = sampleRate,
        bitDepth = bitDepth,
        channels = channels,
        channelLayout = "FRONT_LEFT|FRONT_RIGHT",
        overallBitrate = overallBitrate,
        audioBitrate = audioBitrate,
        codec = "FLAC",
        container = "FLAC",
        lossless = true,
        metadataRequestCount = 2u,
        metadataFetchedBytes = 512u,
        metadataElapsedMs = 10u,
        artworkCachedBytes = if (artwork == null) 0u else 128u,
    )

    private fun artwork(
        localPath: String,
        mimeType: String = "image/jpeg",
    ) = RemoteArtwork(
        contentHash = "hash-${localPath.substringAfterLast('/')}",
        localPath = localPath,
        thumbnailPath = null,
        width = 512u,
        height = 512u,
        mimeType = mimeType,
        pictureType = "CoverFront",
    )

    private fun deltaItem(
        remoteId: String,
        parentRemoteId: String? = "parent-id",
        path: String? = "/Music/Song.flac",
        isDir: Boolean = false,
        deleted: Boolean = false,
        mimeType: String? = if (isDir || deleted) null else "audio/flac",
    ) = OneDriveDeltaItem(
        remoteId = remoteId,
        parentRemoteId = parentRemoteId,
        name = path?.substringAfterLast('/'),
        path = path,
        size = if (isDir || deleted) null else 100uL,
        isDir = isDir,
        deleted = deleted,
        mimeType = mimeType,
        etag = "\"etag\"",
        ctag = null,
        createdAt = 10,
        modifiedAt = 20,
    )
}
