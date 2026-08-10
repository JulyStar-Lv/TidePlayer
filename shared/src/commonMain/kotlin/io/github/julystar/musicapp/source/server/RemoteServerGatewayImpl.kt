package io.github.julystar.musicapp.source.server

import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.toStorageRouteIdOrNull
import io.github.julystar.musicapp.core.domain.repository.StorageRepository
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.SourceAccountDao
import io.github.julystar.musicapp.source.api.PlaybackResource
import io.github.julystar.musicapp.source.api.RemoteServerGateway
import io.github.julystar.musicapp.source.api.RemoteServerKind
import io.github.julystar.musicapp.source.api.RemoteServerSourceConfiguration
import io.github.julystar.musicapp.source.api.RemoteServerTrack
import io.github.julystar.musicapp.source.api.SourceAuthFailureReason
import io.github.julystar.musicapp.source.api.SourceAuthResult
import io.github.julystar.musicapp.source.api.SourcePlaybackFailureReason
import io.github.julystar.musicapp.source.api.SourcePlaybackResult
import io.github.julystar.musicapp.source.api.decodeRemoteServerPlaybackTarget
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import uniffi.app_backend.ctEmbyLogin
import uniffi.app_backend.ctEmbyRequest
import uniffi.app_backend.ctEmbyResourceUrl
import uniffi.app_backend.ctSubsonicRequest
import uniffi.app_backend.ctSubsonicResourceUrl

class RemoteServerGatewayImpl(
    private val sourceAccountDao: SourceAccountDao,
    private val storageRepository: StorageRepository,
) : RemoteServerGateway {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun authenticate(
        configuration: RemoteServerSourceConfiguration,
    ): SourceAuthResult = runCatching {
        when (configuration.kind) {
            RemoteServerKind.Navidrome,
            RemoteServerKind.OpenSubsonic -> ctSubsonicRequest(
                baseUrl = configuration.address,
                username = configuration.username,
                password = configuration.password,
                endpoint = "ping",
                params = emptyMap(),
            )
            RemoteServerKind.Emby -> ctEmbyLogin(
                baseUrl = configuration.address,
                username = configuration.username,
                password = configuration.password,
            )
        }
    }.fold(
        onSuccess = { SourceAuthResult.Success },
        onFailure = { error ->
            SourceAuthResult.Failure(
                if (error.message.orEmpty().contains("401")) {
                    SourceAuthFailureReason.Unauthorized
                } else {
                    SourceAuthFailureReason.Unavailable
                }
            )
        },
    )

    override suspend fun tracks(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        query: String?,
        limit: Int,
    ): Result<List<RemoteServerTrack>> = runCatching {
        val account = loadAccount(accountId, kind)
        val credential = storageRepository.loadCredentialByAccountId(accountId)
            ?: error("Remote server credential is unavailable")
        when (kind) {
            RemoteServerKind.Navidrome,
            RemoteServerKind.OpenSubsonic -> loadSubsonicTracks(
                accountId = accountId,
                baseUrl = account.endpoint.orEmpty(),
                username = credential.username,
                password = credential.secret,
                query = query,
                limit = limit,
            )
            RemoteServerKind.Emby -> loadEmbyTracks(
                accountId = accountId,
                baseUrl = account.endpoint.orEmpty(),
                token = credential.secret,
                userId = account.externalAccountId.orEmpty(),
                query = query,
                limit = limit,
            )
        }
    }

    override suspend fun playback(
        kind: RemoteServerKind,
        encodedRemoteId: String,
    ): SourcePlaybackResult = runCatching {
        val target = encodedRemoteId.decodeRemoteServerPlaybackTarget()
            ?: error("Remote server playback target is invalid")
        val accountId = target.accountId
        val account = loadAccount(accountId, kind)
        val credential = storageRepository.loadCredentialByAccountId(accountId)
            ?: error("Remote server credential is unavailable")
        val uri = when (kind) {
            RemoteServerKind.Navidrome,
            RemoteServerKind.OpenSubsonic -> ctSubsonicResourceUrl(
                baseUrl = account.endpoint.orEmpty(),
                username = credential.username,
                password = credential.secret,
                endpoint = "stream",
                params = mapOf("id" to target.remoteId),
            )
            RemoteServerKind.Emby -> ctEmbyResourceUrl(
                baseUrl = account.endpoint.orEmpty(),
                token = credential.secret,
                path = "Audio/${target.remoteId}/stream",
                params = buildMap {
                    put("UserId", account.externalAccountId.orEmpty())
                    put("static", "true")
                    target.sourceMediaId?.let { put("MediaSourceId", it) }
                },
            )
        }
        SourcePlaybackResult.Success(PlaybackResource(uri = uri))
    }.getOrElse {
        SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unavailable)
    }

    private suspend fun loadAccount(accountId: SourceAccountId, kind: RemoteServerKind) =
        accountId.toStorageRouteIdOrNull()
            ?.let { sourceAccountDao.get(it) }
            ?.takeIf { account -> account.providerType == kind.providerType }
            ?: error("Remote server account is unavailable")

    private fun loadSubsonicTracks(
        accountId: SourceAccountId,
        baseUrl: String,
        username: String,
        password: String,
        query: String?,
        limit: Int,
    ): List<RemoteServerTrack> {
        val result = mutableListOf<RemoteServerTrack>()
        var offset = 0
        val target = limit.coerceIn(1, 10_000)
        while (result.size < target) {
            val pageSize = minOf(500, target - result.size)
            val raw = ctSubsonicRequest(
                baseUrl = baseUrl,
                username = username,
                password = password,
                endpoint = "search3",
                params = mapOf(
                    "query" to query.orEmpty(),
                    "songCount" to pageSize.toString(),
                    "songOffset" to offset.toString(),
                    "albumCount" to "0",
                    "artistCount" to "0",
                ),
            )
            val songs = json.parseToJsonElement(raw).jsonObject
                .objectAt("subsonic-response", "searchResult3")
                ?.array("song")
                .orEmpty()
            result += songs.mapNotNull { element ->
                val song = element as? JsonObject ?: return@mapNotNull null
                val id = song.string("id") ?: return@mapNotNull null
                val stream = ctSubsonicResourceUrl(
                    baseUrl, username, password, "stream", mapOf("id" to id)
                )
                RemoteServerTrack(
                    accountId = accountId,
                    remoteId = id,
                    title = song.string("title") ?: id,
                    artist = song.string("artist"),
                    album = song.string("album"),
                    durationMs = song.long("duration")?.times(1_000L),
                    streamUrl = stream,
                    coverUrl = song.string("coverArt")?.let { coverId ->
                        ctSubsonicResourceUrl(
                            baseUrl, username, password, "getCoverArt",
                            mapOf("id" to coverId, "size" to "512")
                        )
                    },
                    mimeType = song.string("contentType"),
                    audioProperties = SubsonicAudioPropertiesMapper.map(song),
                )
            }
            if (songs.size < pageSize) break
            offset += songs.size
        }
        return result
    }

    private fun loadEmbyTracks(
        accountId: SourceAccountId,
        baseUrl: String,
        token: String,
        userId: String,
        query: String?,
        limit: Int,
    ): List<RemoteServerTrack> {
        val target = limit.coerceIn(1, 10_000)
        val result = mutableListOf<RemoteServerTrack>()
        var offset = 0
        while (result.size < target) {
            val pageSize = minOf(500, target - result.size)
            val params = buildMap {
                put("Recursive", "true")
                put("IncludeItemTypes", "Audio")
                put("Fields", "Genres,MediaSources,AlbumArtist")
                put("SortBy", "SortName")
                put("SortOrder", "Ascending")
                put("StartIndex", offset.toString())
                put("Limit", pageSize.toString())
                query?.takeIf(String::isNotBlank)?.let { put("SearchTerm", it) }
            }
            val raw = ctEmbyRequest(baseUrl, token, "Users/$userId/Items", params)
            val items = json.parseToJsonElement(raw).jsonObject.array("Items").orEmpty()
            result += items.mapNotNull { element ->
                val item = element as? JsonObject ?: return@mapNotNull null
                val id = item.string("Id") ?: return@mapNotNull null
                val audio = EmbyAudioPropertiesMapper.map(item)
                RemoteServerTrack(
                    accountId = accountId,
                    remoteId = id,
                    title = item.string("Name") ?: id,
                    artist = (item.array("Artists")?.firstOrNull() as? JsonPrimitive)?.contentOrNull
                        ?: item.string("AlbumArtist"),
                    album = item.string("Album"),
                    durationMs = item.long("RunTimeTicks")?.div(10_000L),
                    streamUrl = ctEmbyResourceUrl(
                        baseUrl, token, "Audio/$id/stream",
                        buildMap {
                            put("UserId", userId)
                            put("static", "true")
                            audio.sourceMediaId?.let { put("MediaSourceId", it) }
                        }
                    ),
                    coverUrl = ctEmbyResourceUrl(
                        baseUrl, token, "Items/$id/Images/Primary",
                        mapOf("maxWidth" to "512", "quality" to "90")
                    ),
                    mimeType = audio.mimeType ?: "audio/*",
                    audioProperties = audio.properties,
                    sourceMediaId = audio.sourceMediaId,
                )
            }
            if (items.size < pageSize) break
            offset += items.size
        }
        return result
    }
}

private val RemoteServerKind.providerType: String
    get() = when (this) {
        RemoteServerKind.Navidrome -> ProviderTypes.Navidrome
        RemoteServerKind.OpenSubsonic -> ProviderTypes.OpenSubsonic
        RemoteServerKind.Emby -> ProviderTypes.Emby
    }

private fun JsonObject.objectAt(vararg names: String): JsonObject? =
    names.fold(this as JsonObject?) { current, name -> current?.get(name) as? JsonObject }

private fun JsonObject.array(name: String): JsonArray? = get(name) as? JsonArray
private fun JsonObject.string(name: String): String? =
    (get(name) as? JsonPrimitive)?.contentOrNull

private fun JsonObject.long(name: String): Long? = (get(name) as? JsonPrimitive)?.longOrNull
