package io.github.julystar.musicapp.singleton

import io.github.julystar.musicapp.core.data.toLegacyStorageArtwork
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.SourceId
import io.github.julystar.musicapp.core.data.toLegacyStorageEntry
import io.github.julystar.musicapp.core.data.toLegacyStorageEntryLoc
import io.github.julystar.musicapp.core.data.toSourceNodeSelection
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.model.MediaType
import io.github.julystar.musicapp.feature.importing.data.ImportRepositoryImpl
import io.github.julystar.musicapp.core.domain.model.ImportSelectionMode
import io.github.julystar.musicapp.source.api.BuiltInSourceIds
import io.github.julystar.musicapp.source.api.SourceDirectorySelection
import io.github.julystar.musicapp.source.api.SourceNode
import io.github.julystar.musicapp.source.api.SourceNodeSelection
import io.github.julystar.musicapp.source.api.SourceNodeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import uniffi.app_backend.Storage
import uniffi.app_backend.StorageId
import uniffi.app_backend.StorageEntryLoc
import uniffi.app_backend.StorageType

class ImportRepositoryTest {
    @Test
    fun entryImportUsesSourceSelectionCallback() {
        val repository = ImportRepositoryImpl()
        var selected: List<SourceNodeSelection>? = null
        val selection = importSelection()

        repository.prepare(listOf(SourceNodeType.Track)) { entries ->
            selected = entries
        }

        assertEquals(ImportSelectionMode.Entries, repository.selectionMode.value)
        assertEquals(listOf(SourceNodeType.Track), repository.allowTypes.value)

        repository.onFinish(listOf(selection))

        assertEquals(listOf(selection), selected)
    }

    @Test
    fun sourceSelectionCanBeAdaptedToLegacyStorageEntryAtBoundary() {
        val entry = importSelection().toLegacyStorageEntry()

        assertEquals(StorageId(9), entry?.storageId)
        assertEquals("Track.flac", entry?.name)
        assertEquals("/Music/Track.flac", entry?.path)
        assertEquals("file-id", entry?.remoteId)
    }

    @Test
    fun legacyCoverLocCanBeAdaptedToSourceSelectionAtBoundary() {
        val loc = StorageEntryLoc(StorageId(9), "/Music/Cover.jpg")
        val selection = loc.toSourceNodeSelection(listOf(storage(id = 9, type = StorageType.WEBDAV)))

        assertEquals(BuiltInSourceIds.WebDav, selection?.sourceId)
        assertEquals(SourceAccountId("storage:9"), selection?.accountId)
        assertEquals(SourceNodeType.Image, selection?.node?.type)
        assertEquals("Cover.jpg", selection?.node?.name)
        assertEquals(loc, selection?.toLegacyStorageEntryLoc())

        val artwork = assertIs<Artwork.SourceMedia>(selection?.toLegacyStorageArtwork())
        assertEquals(BuiltInSourceIds.WebDav, artwork.mediaId.sourceId)
        assertEquals(MediaType.Image, artwork.mediaId.mediaType)
    }

    @Test
    fun legacyCoverLocWithoutStorageStillPreservesUpdateSelection() {
        val loc = StorageEntryLoc(StorageId(9), "/Music/Cover.jpg")
        val selection = loc.toSourceNodeSelection(emptyList())

        assertEquals(SourceAccountId("storage:9"), selection?.accountId)
        assertEquals(loc, selection?.toLegacyStorageEntryLoc())
        assertNull(selection?.toLegacyStorageArtwork())
    }

    @Test
    fun currentDirectoryImportUsesDirectoryModeAndCallback() {
        val repository = ImportRepositoryImpl()
        var selected: SourceDirectorySelection? = null
        val selection = SourceDirectorySelection(
            sourceId = SourceId("webdav"),
            accountId = SourceAccountId("storage:9"),
            path = "/Music",
            remoteId = "folder-id",
        )

        repository.prepareCurrentDirectory { directorySelection ->
            selected = directorySelection
        }

        assertEquals(ImportSelectionMode.CurrentDirectory, repository.selectionMode.value)
        assertEquals(emptyList(), repository.allowTypes.value)
        assertNull(repository.currentDirectoryAccountId.value)

        repository.onFinishCurrentDirectory(selection)

        assertEquals(selection, selected)
    }

    @Test
    fun currentDirectoryImportCanTargetOneSourceAccount() {
        val repository = ImportRepositoryImpl()
        val accountId = SourceAccountId("storage:9")

        repository.prepareCurrentDirectory(accountId) { }

        assertEquals(accountId, repository.currentDirectoryAccountId.value)
    }

    @Test
    fun multipleDirectoryImportReturnsEverySelectedRoot() {
        val repository = ImportRepositoryImpl()
        val accountId = SourceAccountId("storage:9")
        val selections = listOf(
            SourceDirectorySelection(SourceId("webdav"), accountId, "/Music", "music-id"),
            SourceDirectorySelection(SourceId("webdav"), accountId, "/OST", "ost-id"),
        )
        var received: List<SourceDirectorySelection>? = null

        repository.prepareDirectories(accountId) { received = it }
        repository.onFinishDirectories(selections)

        assertEquals(selections, received)
        assertEquals(accountId, repository.currentDirectoryAccountId.value)
    }

    @Test
    fun entryImportResetsDirectoryMode() {
        val repository = ImportRepositoryImpl()

        repository.prepareCurrentDirectory { }
        repository.prepare(listOf(SourceNodeType.Track)) {}

        assertEquals(ImportSelectionMode.Entries, repository.selectionMode.value)
        assertEquals(listOf(SourceNodeType.Track), repository.allowTypes.value)
    }

    private fun importSelection(): SourceNodeSelection {
        return SourceNodeSelection(
            sourceId = SourceId("webdav"),
            accountId = SourceAccountId("storage:9"),
            node = SourceNode(
                accountId = SourceAccountId("storage:9"),
                nodeId = "file-id",
                remoteId = "file-id",
                parentNodeId = "folder-id",
                name = "Track.flac",
                path = "/Music/Track.flac",
                type = SourceNodeType.Track,
                sizeBytes = 42u,
                mimeType = "audio/flac",
                etag = "etag",
                ctag = "ctag",
                createdAtEpochMs = 1,
                modifiedAtEpochMs = 2,
            ),
        )
    }

    private fun storage(id: Long, type: StorageType): Storage {
        return Storage(
            id = StorageId(id),
            addr = "",
            alias = "Storage $id",
            username = "",
            password = "",
            isAnonymous = true,
            typ = type,
            musicCount = 0u,
        )
    }
}
