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
import io.github.julystar.musicapp.source.api.RemoteServerTrack
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile

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
    ) + when (kind) {
        RemoteServerKind.Navidrome -> setOf(
            SourceCapability.Download,
            SourceCapability.Lyrics,
            SourceCapability.PlaylistRead,
            SourceCapability.PlaylistWrite,
        )
        RemoteServerKind.OpenSubsonic -> setOf(
            SourceCapability.Lyrics,
            SourceCapability.PlaylistRead,
            SourceCapability.PlaylistWrite,
        )
        RemoteServerKind.Emby -> emptySet()
    }

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
    ): SourceListResult = listPages(accountId, directoryId).firstOrNull()
        ?: SourceListResult.Failure(SourceListFailureReason.Unavailable)

    override fun listPages(
        accountId: SourceAccountId,
        directoryId: String?,
        pageSize: Int,
    ): Flow<SourceListResult> = gateway.trackPages(
        kind = kind,
        accountId = accountId,
        pageSize = pageSize,
    ).map { result ->
        result.fold(
            onSuccess = { page ->
                SourceListResult.Success(page.tracks.map { it.toSourceNode(accountId) })
            },
            onFailure = { SourceListResult.Failure(SourceListFailureReason.Unavailable) },
        )
    }

    override suspend fun search(
        accountId: SourceAccountId,
        query: String,
        limit: Int,
    ): SourceSearchResult {
        val target = limit.coerceAtLeast(0)
        if (target == 0) return SourceSearchResult.Success(emptyList())
        val items = mutableListOf<SourceMediaItem>()
        var failed = false
        gateway.trackPages(kind, accountId, query, target.coerceAtMost(500))
            .takeWhile { result ->
                result.fold(
                    onSuccess = { page ->
                        items += page.tracks.take(target - items.size)
                            .map { it.toSourceMediaItem(descriptor.id) }
                        items.size < target
                    },
                    onFailure = {
                        failed = true
                        false
                    },
                )
            }
            .collect()
        return if (failed) {
            SourceSearchResult.Failure(SourceSearchFailureReason.Unavailable)
        } else {
            SourceSearchResult.Success(items)
        }
    }

    override suspend fun resolvePlayback(mediaId: MediaId): SourcePlaybackResult {
        if (mediaId.sourceId != descriptor.id) {
            return SourcePlaybackResult.Failure(SourcePlaybackFailureReason.UnsupportedMediaId)
        }
        if (mediaId.mediaType != MediaType.Track) {
            return SourcePlaybackResult.Failure(SourcePlaybackFailureReason.UnsupportedMediaType)
        }
        return gateway.playback(kind, mediaId.remoteId)
    }

    override suspend fun resolveDownload(mediaId: MediaId): SourcePlaybackResult {
        if (mediaId.sourceId != descriptor.id) {
            return SourcePlaybackResult.Failure(SourcePlaybackFailureReason.UnsupportedMediaId)
        }
        if (mediaId.mediaType != MediaType.Track) {
            return SourcePlaybackResult.Failure(SourcePlaybackFailureReason.UnsupportedMediaType)
        }
        if (kind != RemoteServerKind.Navidrome) {
            return SourcePlaybackResult.Failure(SourcePlaybackFailureReason.UnsupportedMediaType)
        }
        return gateway.download(kind, mediaId.remoteId)
    }
}

private fun RemoteServerTrack.encodedId(): String =
    encodedPlaybackId()

private fun RemoteServerTrack.toSourceNode(accountId: SourceAccountId): SourceNode = SourceNode(
    accountId = accountId,
    nodeId = encodedId(),
    remoteId = encodedId(),
    name = buildString {
        append(title)
        artist?.let { append(" — ").append(it) }
    },
    path = "/$remoteId",
    type = SourceNodeType.Track,
    mimeType = mimeType,
    audioProperties = audioProperties,
    sourceMediaId = sourceMediaId,
)

private fun RemoteServerTrack.toSourceMediaItem(
    sourceId: io.github.julystar.musicapp.core.domain.model.SourceId,
): SourceMediaItem = SourceMediaItem(
    mediaId = MediaId(sourceId, MediaType.Track, encodedId()),
    accountId = accountId,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    path = streamUrl.orEmpty(),
    audioProperties = audioProperties,
)
