package io.github.julystar.musicapp.source.api

import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.SourceId

enum class LegacyStorageKind {
    Local,
    WebDav,
    OneDrive,
    Smb,
    OpenList,
}

data class LegacyStorageConnectionRequest(
    val alias: String,
    val address: String,
    val username: String = "",
    val password: String = "",
    val isAnonymous: Boolean = false,
    val kind: LegacyStorageKind,
) {
    override fun toString(): String =
        "LegacyStorageConnectionRequest(" +
            "alias=$alias, address=$address, username=$username, password=<redacted>, " +
            "isAnonymous=$isAnonymous, kind=$kind)"
}

fun interface LegacyStorageConnectionTester {
    suspend fun test(request: LegacyStorageConnectionRequest): SourceAuthResult
}

fun interface LegacyStorageDirectoryLister {
    suspend fun list(
        accountId: SourceAccountId,
        directoryId: String?,
        expectedStorageKind: LegacyStorageKind,
    ): SourceListResult
}

fun interface LegacySmbServerDirectoryLister {
    suspend fun list(
        accountId: SourceAccountId,
        directoryId: String?,
    ): SourceListResult
}

interface LegacyStoragePlaybackResolver {
    suspend fun resolve(
        accountId: SourceAccountId,
        path: String,
        expectedStorageKind: LegacyStorageKind,
    ): SourcePlaybackResult

    suspend fun release(uri: String)

    suspend fun releaseAll()
}

fun interface LegacyStorageSearchProvider {
    suspend fun search(
        accountId: SourceAccountId,
        query: String,
        limit: Int,
        expectedStorageKind: LegacyStorageKind,
        sourceId: SourceId,
    ): SourceSearchResult
}

object UnsupportedLegacyStorageSearchProvider : LegacyStorageSearchProvider {
    override suspend fun search(
        accountId: SourceAccountId,
        query: String,
        limit: Int,
        expectedStorageKind: LegacyStorageKind,
        sourceId: SourceId,
    ): SourceSearchResult {
        return SourceSearchResult.Failure(SourceSearchFailureReason.Unsupported)
    }
}
