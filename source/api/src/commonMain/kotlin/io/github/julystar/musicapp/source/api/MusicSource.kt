package io.github.julystar.musicapp.source.api

import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.SourceId

interface MusicSource {
    val descriptor: MusicSourceDescriptor
    val capabilities: Set<SourceCapability>
    val rootDirectoryRemoteId: String?
        get() = null

    suspend fun authenticate(configuration: SourceConfiguration): SourceAuthResult

    suspend fun list(
        accountId: SourceAccountId,
        directoryId: String? = null,
    ): SourceListResult

    suspend fun listPathConfiguration(
        accountId: SourceAccountId,
        directoryId: String? = null,
    ): SourceListResult = list(accountId, directoryId)

    suspend fun search(
        accountId: SourceAccountId,
        query: String,
        limit: Int = DEFAULT_SOURCE_SEARCH_LIMIT,
    ): SourceSearchResult {
        return SourceSearchResult.Failure(SourceSearchFailureReason.Unsupported)
    }

    suspend fun resolvePlayback(mediaId: MediaId): SourcePlaybackResult
}

data class MusicSourceDescriptor(
    val id: SourceId,
    val displayName: String,
)

enum class SourceCapability {
    Browse,
    Search,
    Stream,
    Download,
    Lyrics,
    PlaylistRead,
    PlaylistWrite,
    IncrementalSync,
}

sealed interface SourceConfiguration {
    val accountId: SourceAccountId?
    val alias: String
}

class WebDavSourceConfiguration(
    override val accountId: SourceAccountId? = null,
    override val alias: String,
    val address: String,
    val username: String,
    val password: String,
    val isAnonymous: Boolean,
) : SourceConfiguration

class OneDriveSourceConfiguration(
    override val accountId: SourceAccountId? = null,
    override val alias: String,
    val driveId: String,
    val refreshToken: String,
) : SourceConfiguration

data class SmbSourceConfiguration(
    override val accountId: SourceAccountId? = null,
    override val alias: String,
    val host: String,
    val port: Int = 445,
    val share: String,
    val rootPath: String = "",
    val domain: String? = null,
    val username: String = "",
    val password: String = "",
    val isGuest: Boolean = false,
    val requireSigning: Boolean = false,
    val requireEncryption: Boolean = false,
) : SourceConfiguration {
    override fun toString(): String {
        return "SmbSourceConfiguration(" +
            "accountId=$accountId, alias=$alias, host=$host, port=$port, share=$share, " +
            "rootPath=$rootPath, domain=$domain, username=$username, password=<redacted>, " +
            "isGuest=$isGuest, requireSigning=$requireSigning, " +
            "requireEncryption=$requireEncryption)"
    }
}

data class LocalSourceConfiguration(
    override val accountId: SourceAccountId? = null,
    override val alias: String = "Local",
) : SourceConfiguration

enum class RemoteServerKind {
    Navidrome,
    OpenSubsonic,
    Emby,
}

data class RemoteServerSourceConfiguration(
    override val accountId: SourceAccountId? = null,
    override val alias: String,
    val kind: RemoteServerKind,
    val address: String,
    val username: String,
    val password: String,
) : SourceConfiguration

data class RemoteServerTrack(
    val accountId: SourceAccountId,
    val remoteId: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val streamUrl: String,
    val coverUrl: String? = null,
    val mimeType: String? = null,
    val audioProperties: SourceAudioProperties? = null,
    val sourceMediaId: String? = null,
)

interface RemoteServerGateway {
    suspend fun authenticate(configuration: RemoteServerSourceConfiguration): SourceAuthResult
    suspend fun tracks(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        query: String? = null,
        limit: Int = DEFAULT_SOURCE_SEARCH_LIMIT,
    ): Result<List<RemoteServerTrack>>

    suspend fun playback(
        kind: RemoteServerKind,
        encodedRemoteId: String,
    ): SourcePlaybackResult
}

sealed interface SourceAuthResult {
    data object Success : SourceAuthResult
    data class Failure(val reason: SourceAuthFailureReason) : SourceAuthResult
}

enum class SourceAuthFailureReason {
    Timeout,
    Unauthorized,
    PermissionDenied,
    NotFound,
    InvalidAddress,
    UnsupportedSecurityPolicy,
    UnsupportedConfiguration,
    Unavailable,
    Unknown,
}

data class PlaybackResource(
    val uri: String,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
    val expiresAtEpochMs: Long? = null,
    val isLocal: Boolean = false,
)

sealed interface SourcePlaybackResult {
    data class Success(val resource: PlaybackResource) : SourcePlaybackResult
    data class Failure(val reason: SourcePlaybackFailureReason) : SourcePlaybackResult
}

enum class SourcePlaybackFailureReason {
    UnsupportedMediaId,
    UnsupportedMediaType,
    UnsupportedAccount,
    Unavailable,
    Unknown,
}

data class SourceNode(
    val accountId: SourceAccountId,
    val nodeId: String,
    val remoteId: String? = null,
    val parentNodeId: String? = null,
    val name: String,
    val path: String,
    val type: SourceNodeType,
    val sizeBytes: ULong? = null,
    val mimeType: String? = null,
    val etag: String? = null,
    val ctag: String? = null,
    val createdAtEpochMs: Long? = null,
    val modifiedAtEpochMs: Long? = null,
    val audioProperties: SourceAudioProperties? = null,
    val sourceMediaId: String? = null,
) {
    init {
        require(nodeId.isNotBlank()) { "SourceNode nodeId cannot be blank" }
        require(path.isNotBlank()) { "SourceNode path cannot be blank" }
    }
}

enum class SourceNodeType {
    Folder,
    Track,
    Image,
    Lyric,
    Other,
}

data class SourceNodeSelection(
    val sourceId: SourceId,
    val accountId: SourceAccountId,
    val node: SourceNode,
)

data class SourceDirectorySelection(
    val sourceId: SourceId,
    val accountId: SourceAccountId,
    val path: String,
    val remoteId: String?,
)

data class SourceMediaItem(
    val mediaId: MediaId,
    val accountId: SourceAccountId,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val path: String? = null,
    val audioProperties: SourceAudioProperties? = null,
) {
    init {
        require(title.isNotBlank()) { "SourceMediaItem title cannot be blank" }
    }
}

sealed interface SourceListResult {
    data class Success(val nodes: List<SourceNode>) : SourceListResult
    data class Failure(val reason: SourceListFailureReason) : SourceListResult
}

enum class SourceListFailureReason {
    Unauthorized,
    Timeout,
    PermissionDenied,
    NotFound,
    InvalidAddress,
    UnsupportedSecurityPolicy,
    UnsupportedAccount,
    Unavailable,
    Unknown,
}

sealed interface SourceSearchResult {
    data class Success(val items: List<SourceMediaItem>) : SourceSearchResult
    data class Failure(val reason: SourceSearchFailureReason) : SourceSearchResult
}

enum class SourceSearchFailureReason {
    Unsupported,
    Unauthorized,
    Timeout,
    UnsupportedAccount,
    Unavailable,
    Unknown,
}

object BuiltInSourceIds {
    val Local = SourceId("local")
    val WebDav = SourceId("webdav")
    val OneDrive = SourceId("onedrive")
    val Smb = SourceId("smb")
    val Navidrome = SourceId("navidrome")
    val OpenSubsonic = SourceId("open_subsonic")
    val Emby = SourceId("emby")
}

const val DEFAULT_SOURCE_SEARCH_LIMIT = 50
