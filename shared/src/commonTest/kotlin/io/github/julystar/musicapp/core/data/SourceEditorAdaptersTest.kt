package io.github.julystar.musicapp.core.data

import io.github.julystar.musicapp.core.domain.model.SourceEditorDraft
import io.github.julystar.musicapp.core.domain.model.SourceEditorType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import uniffi.app_backend.StorageType

class SourceEditorAdaptersTest {
    @Test
    fun remoteServerEditorsAreRejectedByLegacyStorageAdapter() {
        listOf(
            SourceEditorType.Navidrome,
            SourceEditorType.OpenSubsonic,
            SourceEditorType.Emby,
        ).forEach { type ->
            assertFailsWith<IllegalStateException> {
                SourceEditorDraft(storageType = type).toArgUpsertStorage()
            }
        }
    }

    @Test
    fun fileEditorsKeepLegacyStorageTypeMapping() {
        assertEquals(StorageType.WEBDAV, SourceEditorType.WebDav.toStorageType())
        assertEquals(StorageType.ONE_DRIVE, SourceEditorType.OneDrive.toStorageType())
        assertEquals(StorageType.SMB, SourceEditorType.Smb.toStorageType())
        assertEquals(StorageType.OPEN_LIST, SourceEditorType.OpenList.toStorageType())
    }
}
