package io.github.julystar.musicapp.source.storage

import io.github.julystar.musicapp.source.api.BuiltInSourceIds
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import uniffi.app_backend.Storage
import uniffi.app_backend.StorageId
import uniffi.app_backend.StorageType

class LegacyStorageTrackMappingTest {
    @Test
    fun mapsRawSourceFieldsToEachBuiltInLegacyStorageMediaId() = runBlocking {
        val cases = listOf(
            StorageType.LOCAL to BuiltInSourceIds.Local,
            StorageType.WEBDAV to BuiltInSourceIds.WebDav,
            StorageType.ONE_DRIVE to BuiltInSourceIds.OneDrive,
            StorageType.SMB to BuiltInSourceIds.Smb,
            StorageType.OPEN_LIST to BuiltInSourceIds.OpenList,
        )

        cases.forEach { (storageType, sourceId) ->
            val mediaId = legacyStorageTrackMediaIdOrNull(
                storageLookup = LegacyStorageLookup { storageId ->
                    storage(id = storageId.value, typ = storageType)
                },
                sourceStorageId = 5,
                sourcePath = "/Music/Song.flac",
            )

            assertEquals(sourceId, mediaId?.sourceId)
            assertEquals(
                "legacy-storage-track:storage%3A5:%2FMusic%2FSong.flac",
                mediaId?.remoteId,
            )
        }
    }

    @Test
    fun missingSourceFieldsDoNotCreateMediaId() = runBlocking {
        assertNull(
            legacyStorageTrackMediaIdOrNull(
                storageLookup = LegacyStorageLookup { null },
                sourceStorageId = 2,
                sourcePath = "/Music/Song.flac",
            )
        )
    }

    private fun storage(
        id: Long,
        typ: StorageType,
    ) = Storage(
        id = StorageId(id),
        addr = "https://example.com",
        alias = "NAS",
        username = "alice",
        password = "",
        isAnonymous = true,
        typ = typ,
            musicCount = 0u,
        )
}
