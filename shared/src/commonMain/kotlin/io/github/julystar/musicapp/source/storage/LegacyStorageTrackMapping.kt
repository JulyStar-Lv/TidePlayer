package io.github.julystar.musicapp.source.storage

import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.SourceId
import io.github.julystar.musicapp.source.api.BuiltInSourceIds
import io.github.julystar.musicapp.source.api.legacyStorageTrackMediaId
import uniffi.app_backend.StorageId
import uniffi.app_backend.StorageType

suspend fun legacyStorageTrackMediaIdOrNull(
    storageLookup: LegacyStorageLookup,
    sourceStorageId: Long?,
    sourcePath: String?,
): MediaId? {
    val storageId = sourceStorageId ?: return null
    val path = sourcePath?.takeIf { it.isNotBlank() } ?: return null
    val storage = storageLookup.storageForPlayback(StorageId(storageId)) ?: return null
    return legacyStorageTrackMediaId(
        sourceId = storage.typ.toBuiltInSourceId(),
        accountId = storage.id.toLegacyStorageSourceAccountId(),
        path = path,
    )
}

fun StorageType.toBuiltInSourceId(): SourceId {
    return when (this) {
        StorageType.LOCAL -> BuiltInSourceIds.Local
        StorageType.WEBDAV -> BuiltInSourceIds.WebDav
        StorageType.ONE_DRIVE -> BuiltInSourceIds.OneDrive
        StorageType.SMB -> BuiltInSourceIds.Smb
        StorageType.OPEN_LIST -> BuiltInSourceIds.OpenList
    }
}
