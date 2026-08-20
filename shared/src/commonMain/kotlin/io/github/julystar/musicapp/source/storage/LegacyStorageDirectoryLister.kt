package io.github.julystar.musicapp.source.storage

import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.storageSourceAccountId
import io.github.julystar.musicapp.core.domain.model.toStorageRouteIdOrNull
import io.github.julystar.musicapp.source.api.LegacyStorageKind
import io.github.julystar.musicapp.source.api.SourceListFailureReason
import io.github.julystar.musicapp.source.api.SourceListResult
import io.github.julystar.musicapp.source.api.SourceNode
import io.github.julystar.musicapp.source.api.SourceNodeType
import uniffi.app_backend.ListStorageEntryChildrenResp
import uniffi.app_backend.StorageEntry
import uniffi.app_backend.StorageId
import uniffi.app_backend.StorageType

internal fun SourceAccountId.toLegacyStorageIdOrNull(): StorageId? {
    return toStorageRouteIdOrNull()?.let(::StorageId)
}

internal fun StorageId.toLegacyStorageSourceAccountId(): SourceAccountId {
    return storageSourceAccountId(value)
}

internal fun LegacyStorageKind.toStorageType(): StorageType {
    return when (this) {
        LegacyStorageKind.Local -> StorageType.LOCAL
        LegacyStorageKind.WebDav -> StorageType.WEBDAV
        LegacyStorageKind.OneDrive -> StorageType.ONE_DRIVE
        LegacyStorageKind.Smb -> StorageType.SMB
        LegacyStorageKind.OpenList -> StorageType.OPEN_LIST
    }
}

internal fun ListStorageEntryChildrenResp.toSourceListResult(
    accountId: SourceAccountId,
): SourceListResult {
    return when (this) {
        is ListStorageEntryChildrenResp.Ok -> {
            SourceListResult.Success(
                nodes = v1.map { entry ->
                    entry.toSourceNode(accountId)
                }
            )
        }
        ListStorageEntryChildrenResp.AuthenticationFailed -> {
            SourceListResult.Failure(SourceListFailureReason.Unauthorized)
        }
        ListStorageEntryChildrenResp.Timeout -> {
            SourceListResult.Failure(SourceListFailureReason.Timeout)
        }
        ListStorageEntryChildrenResp.PermissionDenied -> {
            SourceListResult.Failure(SourceListFailureReason.PermissionDenied)
        }
        ListStorageEntryChildrenResp.NotFound -> {
            SourceListResult.Failure(SourceListFailureReason.NotFound)
        }
        ListStorageEntryChildrenResp.InvalidAddress -> {
            SourceListResult.Failure(SourceListFailureReason.InvalidAddress)
        }
        ListStorageEntryChildrenResp.Unavailable -> {
            SourceListResult.Failure(SourceListFailureReason.Unavailable)
        }
        ListStorageEntryChildrenResp.Unsupported -> {
            SourceListResult.Failure(SourceListFailureReason.UnsupportedSecurityPolicy)
        }
        ListStorageEntryChildrenResp.Unknown -> {
            SourceListResult.Failure(SourceListFailureReason.Unknown)
        }
    }
}

private fun StorageEntry.toSourceNode(accountId: SourceAccountId): SourceNode {
    return SourceNode(
        accountId = accountId,
        nodeId = remoteId ?: path,
        remoteId = remoteId,
        parentNodeId = parentRemoteId,
        name = name,
        path = path,
        type = sourceNodeType(),
        sizeBytes = size,
        mimeType = mimeType,
        etag = etag,
        ctag = ctag,
        createdAtEpochMs = createdAt,
        modifiedAtEpochMs = modifiedAt,
    )
}

private fun StorageEntry.sourceNodeType(): SourceNodeType {
    if (isDir) return SourceNodeType.Folder

    val lowerPath = path.lowercase()
    return when {
        mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            ?.startsWith("video/") == true -> SourceNodeType.Other
        MUSIC_EXTENSIONS.any { extension -> lowerPath.endsWith(extension) } -> SourceNodeType.Track
        IMAGE_EXTENSIONS.any { extension -> lowerPath.endsWith(extension) } -> SourceNodeType.Image
        LYRIC_EXTENSIONS.any { extension -> lowerPath.endsWith(extension) } -> SourceNodeType.Lyric
        else -> SourceNodeType.Other
    }
}

private val MUSIC_EXTENSIONS = arrayOf(
    ".wav",
    ".mp3",
    ".aac",
    ".flac",
    ".ogg",
    ".oga",
    ".opus",
    ".m4a",
    ".mp4",
    ".ape",
    ".wv",
    ".aif",
    ".aiff",
)
private val IMAGE_EXTENSIONS = arrayOf(".jpg", ".jpeg", ".png")
private val LYRIC_EXTENSIONS = arrayOf(".lrc")
