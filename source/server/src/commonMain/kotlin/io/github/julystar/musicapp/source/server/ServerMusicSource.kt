package io.github.julystar.musicapp.source.server

import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.MediaType
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.source.api.BuiltInSourceIds
import io.github.julystar.musicapp.source.api.MusicSource
import io.github.julystar.musicapp.source.api.MusicSourceDescriptor
import io.github.julystar.musicapp.source.api.RemoteServerGateway
import io.github.julystar.musicapp.source.api.RemoteServerKind
import io.github.julystar.musicapp.source.api.RemoteServerSourceConfiguration
import io.github.julystar.musicapp.source.api.SourceAuthFailureReason
import io.github.julystar.musicapp.source.api.SourceAuthResult
import io.github.julystar.musicapp.source.api.SourceCapability
import io.github.julystar.musicapp.source.api.SourceConfiguration
import io.github.julystar.musicapp.source.api.SourceListFailureReason
import io.github.julystar.musicapp.source.api.SourceListResult
import io.github.julystar.musicapp.source.api.SourceMediaItem
import io.github.julystar.musicapp.source.api.SourceNode
import io.github.julystar.musicapp.source.api.SourceNodeType
import io.github.julystar.musicapp.source.api.SourcePlaybackFailureReason
import io.github.julystar.musicapp.source.api.SourcePlaybackResult
import io.github.julystar.musicapp.source.api.SourceSearchFailureReason
import io.github.julystar.musicapp.source.api.SourceSearchResult
import io.github.julystar.musicapp.source.api.encodedPlaybackId

class ServerMusicSource(
    private val kind: RemoteServerKind,
    private val gateway: RemoteServerGateway,
) : MusicSource {
    override val descriptor = MusicSourceDescriptor(
        id = when (kind) {
            RemoteServerKind.Navidrome -> BuiltInSourceIds.Navidrome
            RemoteServerKind.OpenSubsonic -> BuiltInSourceIds.OpenSubsonic
            RemoteServerKind.Emby -> BuiltInSourceIds.Emby
        },
        displayName = when (kind) {
            RemoteServerKind.Navidrome -> "Navidrome"
            RemoteServerKind.OpenSubsonic -> "OpenSubsonic"
            RemoteServerKind.Emby -> "Emby"
        },
    )

    override val capabilities = setOf(
        SourceCapability.Browse,
        SourceCapability.Search,
        SourceCapability.Stream,
        SourceCapability.Download,
        SourceCapability.Lyrics,
    )

    override suspend fun authenticate(configuration: SourceConfiguration): SourceAuthResult {
        val server = configuration as? RemoteServerSourceConfiguration
            ?: return SourceAuthResult.Failure(SourceAuthFailureReason.UnsupportedConfiguration)
        if (server.kind != kind) {
            return SourceAuthResult.Failure(SourceAuthFailureReason.UnsupportedConfiguration)
        }
        return gateway.authenticate(server)
    }

    override suspend fun list(
        accountId: SourceAccountId,
        directoryId: String?,
    ): SourceListResult = gateway.tracks(
        kind = kind,
        accountId = accountId,
        limit = SERVER_BROWSE_LIMIT,
    ).fold(
        onSuccess = { tracks ->
            SourceListResult.Success(
                tracks.map { track ->
                    SourceNode(
                        accountId = accountId,
                        nodeId = track.encodedId(),
                        remoteId = track.encodedId(),
                        name = buildString {
                            append(track.title)
                            track.artist?.let { append(" — ").append(it) }
                        },
                        path = "/${track.remoteId}",
                        type = SourceNodeType.Track,
                        mimeType = track.mimeType,
                        audioProperties = track.audioProperties,
                        sourceMediaId = track.sourceMediaId,
                    )
                }
            )
        },
        onFailure = { SourceListResult.Failure(SourceListFailureReason.Unavailable) },
    )

    override suspend fun search(
        accountId: SourceAccountId,
        query: String,
        limit: Int,
    ): SourceSearchResult = gateway.tracks(kind, accountId, query, limit).fold(
        onSuccess = { tracks ->
            SourceSearchResult.Success(
                tracks.map { track ->
                    SourceMediaItem(
                        mediaId = MediaId(descriptor.id, MediaType.Track, track.encodedId()),
                        accountId = accountId,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        durationMs = track.durationMs,
                        path = track.streamUrl,
                        audioProperties = track.audioProperties,
                    )
                }
            )
        },
        onFailure = { SourceSearchResult.Failure(SourceSearchFailureReason.Unavailable) },
    )

    override suspend fun resolvePlayback(mediaId: MediaId): SourcePlaybackResult {
        if (mediaId.sourceId != descriptor.id) {
            return SourcePlaybackResult.Failure(SourcePlaybackFailureReason.UnsupportedMediaId)
        }
        if (mediaId.mediaType != MediaType.Track) {
            return SourcePlaybackResult.Failure(SourcePlaybackFailureReason.UnsupportedMediaType)
        }
        return gateway.playback(kind, mediaId.remoteId)
    }
}

private fun io.github.julystar.musicapp.source.api.RemoteServerTrack.encodedId(): String =
    encodedPlaybackId()

private const val SERVER_BROWSE_LIMIT = 10_000
