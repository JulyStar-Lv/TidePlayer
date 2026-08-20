package io.github.julystar.musicapp.source.openlist

import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.source.api.BuiltInSourceIds
import io.github.julystar.musicapp.source.api.OpenListAuthenticator
import io.github.julystar.musicapp.source.api.OpenListBrowseClient
import io.github.julystar.musicapp.source.api.OpenListBrowseEntry
import io.github.julystar.musicapp.source.api.OpenListBrowsePageResult
import io.github.julystar.musicapp.source.api.LegacyStorageKind
import io.github.julystar.musicapp.source.api.LegacyStoragePlaybackResolver
import io.github.julystar.musicapp.source.api.LegacyStorageSearchProvider
import io.github.julystar.musicapp.source.api.MusicSource
import io.github.julystar.musicapp.source.api.MusicSourceDescriptor
import io.github.julystar.musicapp.source.api.OpenListSourceConfiguration
import io.github.julystar.musicapp.source.api.SourceAuthFailureReason
import io.github.julystar.musicapp.source.api.SourceAuthResult
import io.github.julystar.musicapp.source.api.SourceCapability
import io.github.julystar.musicapp.source.api.SourceConfiguration
import io.github.julystar.musicapp.source.api.SourceListResult
import io.github.julystar.musicapp.source.api.SourceNode
import io.github.julystar.musicapp.source.api.SourceNodeType
import io.github.julystar.musicapp.source.api.SourcePlaybackResult
import io.github.julystar.musicapp.source.api.SourceSearchResult
import io.github.julystar.musicapp.source.api.UnsupportedLegacyStorageSearchProvider
import io.github.julystar.musicapp.source.api.resolveLegacyStoragePlayback
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect

class OpenListMusicSource(
    private val authenticator: OpenListAuthenticator,
    private val playbackResolver: LegacyStoragePlaybackResolver,
    private val searchProvider: LegacyStorageSearchProvider = UnsupportedLegacyStorageSearchProvider,
    private val browseClient: OpenListBrowseClient,
) : MusicSource {
    override val descriptor = MusicSourceDescriptor(
        id = BuiltInSourceIds.OpenList,
        displayName = "OpenList",
    )

    override val capabilities = setOf(SourceCapability.Browse, SourceCapability.Stream)

    override val rootDirectoryRemoteId: String = "/"

    override suspend fun authenticate(configuration: SourceConfiguration): SourceAuthResult {
        if (configuration !is OpenListSourceConfiguration) {
            return SourceAuthResult.Failure(SourceAuthFailureReason.UnsupportedConfiguration)
        }
        return authenticator.authenticate(configuration)
    }

    override suspend fun list(
        accountId: SourceAccountId,
        directoryId: String?,
    ): SourceListResult {
        val nodes = mutableListOf<io.github.julystar.musicapp.source.api.SourceNode>()
        var failure: SourceListResult.Failure? = null
        browsePages(accountId, directoryId, DEFAULT_PAGE_SIZE).collect { result ->
            when (result) {
                is SourceListResult.Success -> nodes += result.nodes
                is SourceListResult.Failure -> failure = result
            }
        }
        return failure ?: SourceListResult.Success(nodes)
    }

    override fun listPages(
        accountId: SourceAccountId,
        directoryId: String?,
        pageSize: Int,
    ): Flow<SourceListResult> {
        return browsePages(accountId, directoryId, pageSize.coerceIn(1, MAX_PAGE_SIZE))
    }

    private fun browsePages(
        accountId: SourceAccountId,
        directoryId: String?,
        pageSize: Int,
    ): Flow<SourceListResult> = flow {
        val parentPath = canonicalPath(directoryId)
        if (parentPath == null) {
            emit(SourceListResult.Failure(io.github.julystar.musicapp.source.api.SourceListFailureReason.InvalidAddress))
            return@flow
        }
        val seenPaths = mutableSetOf<String>()
        val seenPages = mutableSetOf<List<OpenListBrowseEntry>>()
        var pageNumber = 1
        while (true) {
            if (pageNumber > MAX_PAGE_COUNT) {
                emit(SourceListResult.Failure(io.github.julystar.musicapp.source.api.SourceListFailureReason.Unavailable))
                return@flow
            }
            when (val result = browseClient.listPage(accountId, parentPath, pageNumber, pageSize)) {
                is OpenListBrowsePageResult.Failure -> {
                    emit(SourceListResult.Failure(result.reason))
                    return@flow
                }
                is OpenListBrowsePageResult.Success -> {
                    val page = result.page
                    if (!seenPages.add(page.entries)) return@flow
                    val nodes = page.entries.mapNotNull { entry ->
                        val childPath = safeChildPath(parentPath, entry.name) ?: return@mapNotNull null
                        if (!seenPaths.add(childPath)) return@mapNotNull null
                        SourceNode(
                            accountId = accountId,
                            nodeId = childPath,
                            remoteId = childPath,
                            parentNodeId = parentPath,
                            name = entry.name,
                            path = childPath,
                            type = entry.toNodeType(),
                            sizeBytes = entry.sizeBytes.takeIf { it >= 0 }?.toULong(),
                            mimeType = entry.mimeType(),
                        )
                    }
                    emit(SourceListResult.Success(nodes))
                    val rawCount = page.entries.size
                    if (rawCount == 0 || rawCount < pageSize ||
                        (page.total >= 0 && pageNumber.toLong() * pageSize >= page.total)
                    ) return@flow
                    pageNumber++
                }
            }
        }
    }

    override suspend fun search(
        accountId: SourceAccountId,
        query: String,
        limit: Int,
    ): SourceSearchResult = searchProvider.search(
        accountId = accountId,
        query = query,
        limit = limit,
        expectedStorageKind = LegacyStorageKind.OpenList,
        sourceId = descriptor.id,
    )

    override suspend fun resolvePlayback(mediaId: MediaId): SourcePlaybackResult {
        return mediaId.resolveLegacyStoragePlayback(
            expectedSourceId = descriptor.id,
            expectedStorageKind = LegacyStorageKind.OpenList,
            playbackResolver = playbackResolver,
        )
    }
}

private const val MAX_PAGE_SIZE = 500
private const val DEFAULT_PAGE_SIZE = 100
private const val MAX_PAGE_COUNT = 10_000

private fun canonicalPath(path: String?): String? {
    if (path == null || path == "/") return "/"
    if (path.isEmpty() || !path.startsWith('/') || path.endsWith('/') ||
        path.contains('\u0000')
    ) return null
    val segments = path.removePrefix("/").split('/')
    if (segments.any { it.isEmpty() || it == "." || it == ".." }) return null
    return path
}

private fun safeChildPath(parent: String, name: String): String? {
    if (name.isBlank() || name == "." || name == ".." ||
        name.contains('/') || name.contains('\u0000')
    ) return null
    return if (parent == "/") "/$name" else "$parent/$name"
}

private fun OpenListBrowseEntry.toNodeType(): SourceNodeType = when {
    isDirectory || type == 1 -> SourceNodeType.Folder
    name.endsWith(".lrc", ignoreCase = true) -> SourceNodeType.Lyric
    type == 3 -> SourceNodeType.Track
    type == 5 -> SourceNodeType.Image
    else -> SourceNodeType.Other
}

private fun OpenListBrowseEntry.mimeType(): String? = when {
    name.endsWith(".lrc", ignoreCase = true) -> "text/plain"
    else -> null
}
