package io.github.julystar.musicapp.core.data

import io.github.julystar.musicapp.core.domain.model.NeedsReauthenticationException
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.source.api.OpenListBrowseClient
import io.github.julystar.musicapp.source.api.OpenListBrowseEntry
import io.github.julystar.musicapp.source.api.OpenListBrowsePage
import io.github.julystar.musicapp.source.api.OpenListBrowsePageResult
import io.github.julystar.musicapp.source.api.SourceListFailureReason
import kotlinx.coroutines.CancellationException
import uniffi.app_backend.OpenListAuthException
import uniffi.app_backend.ctOpenlistListPage

interface OpenListBrowseTransport {
    suspend fun listPage(
        endpoint: String,
        token: String,
        path: String,
        page: Int,
        pageSize: Int,
    ): OpenListBrowsePage
}

class OpenListRustBrowseTransport : OpenListBrowseTransport {
    override suspend fun listPage(
        endpoint: String,
        token: String,
        path: String,
        page: Int,
        pageSize: Int,
    ): OpenListBrowsePage {
        return try {
            ctOpenlistListPage(endpoint, token, path, page.toUInt(), pageSize.toUInt()).let { result ->
                OpenListBrowsePage(
                    entries = result.entries.map { entry ->
                        OpenListBrowseEntry(
                            name = entry.name,
                            sizeBytes = entry.size,
                            isDirectory = entry.isDir,
                            type = entry.entryType,
                        )
                    },
                    total = result.total,
                )
            }
        } catch (error: OpenListAuthException) {
            throw OpenListAuthTransportException(error.toBrowseReason())
        }
    }
}

class OpenListSessionBrowseClient(
    private val materials: OpenListAccountMaterialReader,
    private val sessions: OpenListSessionManager,
    private val transport: OpenListBrowseTransport,
) : OpenListBrowseClient {
    override suspend fun listPage(
        accountId: SourceAccountId,
        path: String,
        page: Int,
        pageSize: Int,
    ): OpenListBrowsePageResult {
        return try {
            val pageResult = sessions.authorized(accountId) { token ->
                val material = materials.load(accountId)
                    ?: throw NeedsReauthenticationException()
                transport.listPage(material.endpoint, token, path, page, pageSize)
            }
            OpenListBrowsePageResult.Success(pageResult)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: NeedsReauthenticationException) {
            OpenListBrowsePageResult.Failure(SourceListFailureReason.Unauthorized)
        } catch (error: OpenListAuthTransportException) {
            OpenListBrowsePageResult.Failure(error.reason.toSourceListFailureReason())
        }
    }
}

private fun OpenListAuthException.toBrowseReason(): OpenListAuthTransportFailureReason = when (this) {
    is OpenListAuthException.InvalidAddress -> OpenListAuthTransportFailureReason.InvalidAddress
    is OpenListAuthException.Timeout -> OpenListAuthTransportFailureReason.Timeout
    is OpenListAuthException.Unauthorized -> OpenListAuthTransportFailureReason.Unauthorized
    is OpenListAuthException.PermissionDenied -> OpenListAuthTransportFailureReason.PermissionDenied
    is OpenListAuthException.OtpRequired -> OpenListAuthTransportFailureReason.OtpRequired
    is OpenListAuthException.RateLimited -> OpenListAuthTransportFailureReason.RateLimited
    is OpenListAuthException.InvalidResponse -> OpenListAuthTransportFailureReason.InvalidResponse
    is OpenListAuthException.ProtocolFailure -> OpenListAuthTransportFailureReason.ProtocolFailure
    is OpenListAuthException.Unavailable -> OpenListAuthTransportFailureReason.Unavailable
}

private fun OpenListAuthTransportFailureReason.toSourceListFailureReason(): SourceListFailureReason = when (this) {
    OpenListAuthTransportFailureReason.Unauthorized,
    OpenListAuthTransportFailureReason.OtpRequired -> SourceListFailureReason.Unauthorized
    OpenListAuthTransportFailureReason.Timeout -> SourceListFailureReason.Timeout
    OpenListAuthTransportFailureReason.PermissionDenied -> SourceListFailureReason.PermissionDenied
    OpenListAuthTransportFailureReason.InvalidAddress -> SourceListFailureReason.InvalidAddress
    OpenListAuthTransportFailureReason.Unavailable,
    OpenListAuthTransportFailureReason.RateLimited,
    OpenListAuthTransportFailureReason.InvalidResponse,
    OpenListAuthTransportFailureReason.ProtocolFailure -> SourceListFailureReason.Unavailable
}
