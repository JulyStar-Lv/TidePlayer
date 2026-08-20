package io.github.julystar.musicapp.core.data

import io.github.julystar.musicapp.core.domain.model.SourceEditorDraft
import io.github.julystar.musicapp.core.domain.model.SourceEditorType
import io.github.julystar.musicapp.core.domain.model.storageSourceAccountId
import io.github.julystar.musicapp.source.api.SmbSourceConfiguration
import io.github.julystar.musicapp.source.smb.toSmbAddress
import uniffi.app_backend.ArgUpsertStorage
import uniffi.app_backend.Storage
import uniffi.app_backend.StorageId
import uniffi.app_backend.StorageType

internal fun SourceEditorDraft.toArgUpsertStorage(): ArgUpsertStorage {
    return ArgUpsertStorage(
        id = id?.let { StorageId(it) },
        addr = when (storageType) {
            SourceEditorType.Smb -> toSmbSourceConfiguration().toSmbAddress()
            else -> address
        },
        alias = alias,
        username = username,
        password = secret,
        isAnonymous = isAnonymous,
        typ = storageType.toStorageType(),
    )
}

internal fun Storage.toSourceEditorDraft(): SourceEditorDraft {
    return SourceEditorDraft(
        id = id.value,
        address = addr,
        alias = alias,
        username = username,
        secret = password,
        isAnonymous = isAnonymous,
        storageType = typ.toSourceEditorType(),
    )
}

internal fun SourceEditorType.toStorageType(): StorageType {
    return when (this) {
        SourceEditorType.WebDav -> StorageType.WEBDAV
        SourceEditorType.OneDrive -> StorageType.ONE_DRIVE
        SourceEditorType.Smb -> StorageType.SMB
        SourceEditorType.OpenList -> StorageType.OPEN_LIST
        SourceEditorType.Navidrome,
        SourceEditorType.OpenSubsonic,
        SourceEditorType.Emby -> error(
            "$this cannot be represented by legacy StorageType; use the remote server path"
        )
    }
}

internal fun StorageType.toSourceEditorType(): SourceEditorType {
    return when (this) {
        StorageType.ONE_DRIVE -> SourceEditorType.OneDrive
        StorageType.SMB -> SourceEditorType.Smb
        StorageType.OPEN_LIST -> SourceEditorType.OpenList
        else -> SourceEditorType.WebDav
    }
}

internal fun SourceEditorDraft.toSmbSourceConfiguration(): SmbSourceConfiguration {
    return SmbSourceConfiguration(
        accountId = id?.let(::storageSourceAccountId),
        alias = alias,
        host = smbHost,
        port = smbPort,
        share = smbShare,
        rootPath = smbRootPath,
        domain = smbDomain.ifBlank { null },
        username = username,
        password = secret,
        isGuest = isAnonymous,
        requireSigning = smbRequireSigning,
        requireEncryption = smbRequireEncryption,
    )
}
