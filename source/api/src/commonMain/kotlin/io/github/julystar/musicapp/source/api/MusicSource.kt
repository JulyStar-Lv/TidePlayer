package io.github.julystar.musicapp.source.api

import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.SourceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

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

    fun listPages(
        accountId: SourceAccountId,
        directoryId: String? = null,
        pageSize: Int = DEFAULT_SOURCE_SEARCH_LIMIT,
    ): Flow<SourceListResult> = flow {
        emit(list(accountId, directoryId))
    }

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

    /** Resolves a download resource. Sources without a distinct download endpoint use playback. */
    suspend fun resolveDownload(mediaId: MediaId): SourcePlaybackResult = resolvePlayback(mediaId)
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

data class OpenListSourceConfiguration(
    override val accountId: SourceAccountId? = null,
    override val alias: String,
    val address: String,
    val username: String,
    val password: String,
    val isGuest: Boolean,
    val otpCode: String = "",
) : SourceConfiguration {
    override fun toString(): String {
        return "OpenListSourceConfiguration(" +
            "accountId=$accountId, alias=$alias, address=$address, username=$username, " +
            "password=<redacted>, isGuest=$isGuest, otpCode=<redacted>)"
    }
}

data class OpenListBrowseEntry(
    val name: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    val type: Int,
)

data class OpenListBrowsePage(
    val entries: List<OpenListBrowseEntry>,
    val total: Long,
)

sealed interface OpenListBrowsePageResult {
    data class Success(val page: OpenListBrowsePage) : OpenListBrowsePageResult
    data class Failure(val reason: SourceListFailureReason) : OpenListBrowsePageResult
}

fun interface OpenListBrowseClient {
    suspend fun listPage(
        accountId: SourceAccountId,
        path: String,
        page: Int,
        pageSize: Int,
    ): OpenListBrowsePageResult
}

fun interface OpenListAuthenticator {
    suspend fun authenticate(configuration: OpenListSourceConfiguration): SourceAuthResult

    /** Runs a connection check without binding a runtime session to the account. */
    suspend fun probe(configuration: OpenListSourceConfiguration): SourceAuthResult = authenticate(configuration)
}

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
) : SourceConfiguration {
    override fun toString(): String =
        "RemoteServerSourceConfiguration(" +
            "accountId=$accountId, alias=$alias, kind=$kind, address=$address, " +
            "username=$username, password=<redacted>)"
}

data class RemoteServerTrack(
    val accountId: SourceAccountId,
    val remoteId: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val year: Int? = null,
    val track: Int? = null,
    val discNumber: Int? = null,
    val suffix: String? = null,
    val durationMs: Long? = null,
    val streamUrl: String? = null,
    val coverUrl: String? = null,
    val coverArtId: String? = null,
    val mimeType: String? = null,
    val bitRate: Int? = null,
    val sampleRate: Int? = null,
    val bitDepth: Int? = null,
    val channelCount: Int? = null,
    val audioProperties: SourceAudioProperties? = null,
    val sourceMediaId: String? = null,
    val artists: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val albumId: String? = null,
    val productionYear: Int? = null,
    val indexNumber: Int? = null,
    val parentIndexNumber: Int? = null,
    val runTimeTicks: Long? = null,
    val mediaSources: List<RemoteServerMediaSource> = emptyList(),
    val mediaStreams: List<RemoteServerMediaStream> = emptyList(),
    val userData: RemoteServerUserData? = null,
    val imageTag: String? = null,
) {
    override fun toString(): String =
        "RemoteServerTrack(" +
            "accountId=$accountId, remoteId=$remoteId, title=$title, artist=$artist, " +
            "album=$album, streamUrl=<redacted>, coverUrl=<redacted>, " +
            "mediaSources=$mediaSources)"
}

data class RemoteServerMediaSource(
    val id: String? = null,
    val isDefault: Boolean? = null,
    val container: String? = null,
    val mimeType: String? = null,
    val bitrate: Int? = null,
    val size: Long? = null,
    val defaultAudioStreamIndex: Int? = null,
    val mediaStreams: List<RemoteServerMediaStream> = emptyList(),
    val supportsDirectPlay: Boolean? = null,
    val supportsDirectStream: Boolean? = null,
    val requiredHttpHeaders: Map<String, String> = emptyMap(),
) {
    override fun toString(): String =
        "RemoteServerMediaSource(" +
            "id=$id, isDefault=$isDefault, container=$container, mimeType=$mimeType, " +
            "bitrate=$bitrate, size=$size, defaultAudioStreamIndex=$defaultAudioStreamIndex, " +
            "mediaStreams=$mediaStreams, supportsDirectPlay=$supportsDirectPlay, " +
            "supportsDirectStream=$supportsDirectStream, requiredHttpHeaders=<redacted>)"
}

data class RemoteServerMediaStream(
    val index: Int? = null,
    val type: String? = null,
    val codec: String? = null,
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val bitDepth: Int? = null,
    val channels: Int? = null,
    val channelLayout: String? = null,
    val isDefault: Boolean? = null,
)

data class RemoteServerUserData(
    val isFavorite: Boolean? = null,
    val playCount: Int? = null,
    val lastPlayedDate: String? = null,
    val played: Boolean? = null,
)

data class RemoteServerTrackPage(
    val tracks: List<RemoteServerTrack>,
    val offset: Int = 0,
)

interface RemoteServerGateway {
    suspend fun authenticate(configuration: RemoteServerSourceConfiguration): SourceAuthResult
    fun trackPages(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        query: String? = null,
        pageSize: Int = DEFAULT_SOURCE_SEARCH_LIMIT,
    ): Flow<Result<RemoteServerTrackPage>>

    suspend fun playback(
        kind: RemoteServerKind,
        encodedRemoteId: String,
    ): SourcePlaybackResult

    suspend fun download(
        kind: RemoteServerKind,
        encodedRemoteId: String,
    ): SourcePlaybackResult = playback(kind, encodedRemoteId)

    suspend fun coverArt(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        coverArtId: String,
        size: Int,
        imageTag: String? = null,
    ): SourcePlaybackResult = SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unavailable)

    suspend fun lyrics(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        artist: String,
        title: String,
    ): Result<RemoteServerLyrics> = Result.failure(UnsupportedOperationException("lyrics unavailable"))

    suspend fun refreshCapabilities(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
    ): Result<OpenSubsonicCapabilitySnapshot> =
        Result.failure(UnsupportedOperationException("capability discovery unavailable"))

    suspend fun openSubsonicLyrics(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        remoteId: String,
    ): Result<OpenSubsonicStructuredLyricsDocument> =
        Result.failure(UnsupportedOperationException("structured lyrics unavailable"))

    suspend fun playlists(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
    ): Result<List<RemoteServerPlaylistSummary>> =
        Result.failure(UnsupportedOperationException("playlists unavailable"))

    suspend fun playlist(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        remotePlaylistId: String,
    ): Result<RemoteServerPlaylist> =
        Result.failure(UnsupportedOperationException("playlist unavailable"))

    suspend fun createPlaylist(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        name: String,
        songIds: List<String> = emptyList(),
        playlistId: String? = null,
    ): Result<String> = Result.failure(UnsupportedOperationException("playlist writes unavailable"))

    suspend fun updatePlaylist(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        remotePlaylistId: String,
        name: String? = null,
        comment: String? = null,
        isPublic: Boolean? = null,
        songIdsToAdd: List<String> = emptyList(),
        songIndexesToRemove: List<Int> = emptyList(),
    ): Result<Unit> = Result.failure(UnsupportedOperationException("playlist writes unavailable"))

    suspend fun deletePlaylist(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        remotePlaylistId: String,
    ): Result<Unit> = Result.failure(UnsupportedOperationException("playlist writes unavailable"))

    suspend fun starred(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
    ): Result<List<RemoteServerTrack>> =
        Result.failure(UnsupportedOperationException("starred unavailable"))

    suspend fun star(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        remoteIds: List<String> = emptyList(),
        albumIds: List<String> = emptyList(),
        artistIds: List<String> = emptyList(),
    ): Result<Unit> = Result.failure(UnsupportedOperationException("star writes unavailable"))

    suspend fun unstar(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        remoteIds: List<String> = emptyList(),
        albumIds: List<String> = emptyList(),
        artistIds: List<String> = emptyList(),
    ): Result<Unit> = Result.failure(UnsupportedOperationException("star writes unavailable"))

    suspend fun scrobble(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        submission: Boolean,
        events: List<RemoteServerScrobble> = emptyList(),
    ): Result<Unit> = Result.failure(UnsupportedOperationException("scrobble unavailable"))
}

data class RemoteServerPlaylistIdentity(
    val kind: RemoteServerKind,
    val accountId: SourceAccountId,
    val remotePlaylistId: String,
)

data class RemoteServerPlaylistSummary(
    val identity: RemoteServerPlaylistIdentity,
    val name: String,
    val comment: String? = null,
    val isPublic: Boolean? = null,
    val songCount: Int? = null,
    val durationMs: Long? = null,
)

data class RemoteServerPlaylist(
    val summary: RemoteServerPlaylistSummary,
    val tracks: List<RemoteServerTrack>,
)

data class RemoteServerScrobble(
    val remoteId: String,
    val timeMs: Long? = null,
)

class RemoteServerWriteDisabledException(
    val kind: RemoteServerKind,
    val accountId: SourceAccountId,
) : Exception("remote server writes are disabled for $kind account $accountId")

class OpenSubsonicLyricsUnsupportedException : Exception("OpenSubsonic structured lyrics unavailable")

data class RemoteServerLyrics(
    val content: String,
    val format: String? = null,
    val synchronized: Boolean = false,
    val structuredDocument: OpenSubsonicStructuredLyricsDocument? = null,
)

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
    OtpRequired,
    Unavailable,
    Unknown,
}

data class PlaybackResource(
    val uri: String,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
    val expiresAtEpochMs: Long? = null,
    val isLocal: Boolean = false,
) {
    override fun toString(): String =
        "PlaybackResource(uri=<redacted>, headers=<redacted>, mimeType=$mimeType, " +
            "expiresAtEpochMs=$expiresAtEpochMs, isLocal=$isLocal)"
}

sealed interface SourcePlaybackResult {
    data class Success(val resource: PlaybackResource) : SourcePlaybackResult
    data class Failure(val reason: SourcePlaybackFailureReason) : SourcePlaybackResult
}

enum class SourcePlaybackFailureReason {
    UnsupportedMediaId,
    UnsupportedMediaType,
    UnsupportedAccount,
    Unauthorized,
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
    val OpenList = SourceId("openlist")
}

const val DEFAULT_SOURCE_SEARCH_LIMIT = 50
