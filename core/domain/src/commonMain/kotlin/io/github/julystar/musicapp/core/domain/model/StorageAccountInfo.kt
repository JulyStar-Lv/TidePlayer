package io.github.julystar.musicapp.core.domain.model

data class StorageAccountInfo(
    val accountId: SourceAccountId,
    val sourceId: SourceId,
    val isLocal: Boolean,
    val isOneDrive: Boolean,
    val title: String,
    val subtitle: String,
    val musicCount: Long,
    val rootPath: String? = null,
    val enabled: Boolean = true,
    val lastScanAtEpochMs: Long? = null,
    val lastScanStatus: String? = null,
)

data class OneDriveDriveInfo(
    val id: String,
    val name: String,
)

data class SourceAccountRootSelection(
    val remoteId: String?,
    val path: String,
)

fun storageSourceAccountId(storageId: Long): SourceAccountId {
    return SourceAccountId("$STORAGE_ACCOUNT_PREFIX$storageId")
}

fun SourceAccountId.toStorageRouteIdOrNull(): Long? {
    return value
        .takeIf { it.startsWith(STORAGE_ACCOUNT_PREFIX) }
        ?.removePrefix(STORAGE_ACCOUNT_PREFIX)
        ?.toLongOrNull()
}

const val STORAGE_ACCOUNT_PREFIX = "storage:"
