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
import io.github.julystar.musicapp.source.api.RemoteServerTrackPage
import io.github.julystar.musicapp.source.api.RemoteServerLyrics
import io.github.julystar.musicapp.source.api.NavidromeProviderConfigurationCodec
import io.github.julystar.musicapp.source.api.EmbyProviderConfigurationCodec
import io.github.julystar.musicapp.source.api.OpenSubsonicProviderConfigurationCodec
import io.github.julystar.musicapp.source.api.OpenSubsonicCapabilitySnapshot
import io.github.julystar.musicapp.source.api.OpenSubsonicExtension
import io.github.julystar.musicapp.source.api.OpenSubsonicStructuredLyricsDocument
import io.github.julystar.musicapp.source.api.OpenSubsonicLyricsUnsupportedException
import io.github.julystar.musicapp.source.api.RemoteServerPlaylist
import io.github.julystar.musicapp.source.api.RemoteServerPlaylistIdentity
import io.github.julystar.musicapp.source.api.RemoteServerPlaylistSummary
import io.github.julystar.musicapp.source.api.RemoteServerScrobble
import io.github.julystar.musicapp.source.api.RemoteServerWriteDisabledException
import io.github.julystar.musicapp.source.api.SourceAuthFailureReason
import io.github.julystar.musicapp.source.api.SourceAuthResult
import io.github.julystar.musicapp.source.api.SourcePlaybackFailureReason
import io.github.julystar.musicapp.source.api.SourcePlaybackResult
import io.github.julystar.musicapp.source.api.decodeRemoteServerPlaybackTarget
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import io.github.julystar.musicapp.platform.currentTimeMillis
import uniffi.app_backend.ctEmbyLogin
import uniffi.app_backend.ctEmbyRequest
import uniffi.app_backend.ctEmbyResourceUrl
import uniffi.app_backend.ctEmbyPlaybackUrl
import uniffi.app_backend.ctEmbyPlaybackInfoRequest
import uniffi.app_backend.EmbyLoginIdentity
import uniffi.app_backend.ctSubsonicRequest
import uniffi.app_backend.ctSubsonicRequestPairs
import uniffi.app_backend.ctSubsonicResourceUrl
import uniffi.app_backend.RemoteMusicException
import uniffi.app_backend.SubsonicQueryParameter

private const val EMBY_PLAYBACK_TTL_MS = 5 * 60 * 1000L
private class EmbyUnsupportedMediaTypeException : Exception()

class RemoteServerGatewayImpl(
    private val sourceAccountDao: SourceAccountDao,
    private val storageRepository: StorageRepository,
    private val subsonicRequest: (String, String, String, String, Map<String, String>) -> String =
        { baseUrl, username, password, endpoint, params ->
            ctSubsonicRequest(baseUrl, username, password, endpoint, params)
        },
    private val subsonicResourceUrl: (String, String, String, String, Map<String, String>) -> String =
        { baseUrl, username, password, endpoint, params ->
            ctSubsonicResourceUrl(baseUrl, username, password, endpoint, params)
        },
    private val subsonicRequestPairs: (String, String, String, String, List<SubsonicQueryParameter>) -> String =
        { baseUrl, username, password, endpoint, params ->
            ctSubsonicRequestPairs(baseUrl, username, password, endpoint, params)
        },
    private val embyLogin: (String, String, String) -> EmbyLoginIdentity =
        { baseUrl, username, password -> ctEmbyLogin(baseUrl, username, password) },
    private val embyRequest: (String, String, String, Map<String, String>) -> String =
        { baseUrl, token, path, params ->
            if (path.startsWith("Items/") && path.endsWith("/PlaybackInfo")) {
                val itemId = path.removePrefix("Items/").removeSuffix("/PlaybackInfo")
                ctEmbyPlaybackInfoRequest(baseUrl, token, itemId, params["UserId"].orEmpty())
            } else {
                ctEmbyRequest(baseUrl, token, path, params)
            }
        },
    private val embyPlaybackUrl: (String, String, String, String) -> String =
        { baseUrl, itemId, userId, sourceMediaId ->
            ctEmbyPlaybackUrl(baseUrl, itemId, userId, sourceMediaId)
        },
    private val nowEpochMs: () -> Long = { currentTimeMillis() },
) : RemoteServerGateway {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun authenticate(
        configuration: RemoteServerSourceConfiguration,
    ): SourceAuthResult = try {
        when (configuration.kind) {
            RemoteServerKind.Navidrome,
            RemoteServerKind.OpenSubsonic -> subsonicRequest(
                configuration.address, configuration.username, configuration.password, "ping", emptyMap(),
            )
            RemoteServerKind.Emby -> embyLogin(
                configuration.address,
                configuration.username,
                configuration.password,
            )
        }
        SourceAuthResult.Success
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        SourceAuthResult.Failure(error.toRemoteAuthFailureReason())
    }

    override fun trackPages(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        query: String?,
        pageSize: Int,
    ): Flow<Result<RemoteServerTrackPage>> = flow {
        try {
            val account = loadAccount(accountId, kind)
            if (kind == RemoteServerKind.Emby) {
                require(!account.externalAccountId.isNullOrBlank()) {
                    "Emby account user id is unavailable"
                }
            }
            val credential = storageRepository.loadCredentialByAccountId(accountId)
                ?: error("Remote server credential is unavailable")
            if (kind == RemoteServerKind.OpenSubsonic && OpenSubsonicCapabilityCodec.decode(account.providerConfig) == null) {
                refreshCapabilities(kind, accountId).getOrThrow()
            }
            when (kind) {
                RemoteServerKind.Navidrome,
                RemoteServerKind.OpenSubsonic -> {
                    val configuration = account.subsonicProviderConfiguration(kind)
                    SubsonicTrackPager(
                        accountId = accountId,
                        pageSize = pageSize,
                        streamMaxBitRate = configuration.streamMaxBitRate,
                        coverArtSize = configuration.coverArtSize,
                        request = { endpoint, params ->
                            executeSubsonicRequest(account, kind, credential, endpoint, params)
                        },
                        resourceUrl = { baseUrl, endpoint, params ->
                            subsonicResourceUrl(
                                baseUrl, credential.username, credential.secret, endpoint, params,
                            )
                        },
                    ).pages(query).collect { emit(it) }
                }
                RemoteServerKind.Emby -> EmbyTrackPager(
                    accountId = accountId,
                    userId = account.externalAccountId.orEmpty(),
                    pageSize = pageSize,
                    request = { path, params ->
                        executeEmbyRequest(account, credential.secret, path, params).value
                    },
                ).pages(query).collect { emit(it) }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            emit(Result.failure(error))
        }
    }

    override suspend fun playback(
        kind: RemoteServerKind,
        encodedRemoteId: String,
    ): SourcePlaybackResult = try {
        val target = encodedRemoteId.decodeRemoteServerPlaybackTarget()
            ?: error("Remote server playback target is invalid")
        val accountId = target.accountId
        val account = loadAccount(accountId, kind)
        if (kind == RemoteServerKind.Emby && account.externalAccountId.isNullOrBlank()) {
            return SourcePlaybackResult.Failure(SourcePlaybackFailureReason.UnsupportedAccount)
        }
        val credential = storageRepository.loadCredentialByAccountId(accountId)
            ?: error("Remote server credential is unavailable")
        if (kind == RemoteServerKind.Emby) {
            return SourcePlaybackResult.Success(negotiateEmbyPlayback(account, credential, target))
        }
        val providerConfig = account.subsonicProviderConfiguration(kind)
        val uri = when (kind) {
            RemoteServerKind.Navidrome,
            RemoteServerKind.OpenSubsonic -> subsonicResourceUrl(
                account.endpoint.orEmpty(), credential.username, credential.secret, "stream", buildMap {
                    put("id", target.remoteId)
                    if (providerConfig.streamMaxBitRate > 0) {
                        put("maxBitRate", providerConfig.streamMaxBitRate.toString())
                    }
                }
            )
            RemoteServerKind.Emby -> error("Emby playback handled above")
        }
        SourcePlaybackResult.Success(PlaybackResource(uri = uri))
    } catch (error: CancellationException) {
        throw error
    } catch (_: RemoteMusicException.Unauthorized) {
        SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unauthorized)
    } catch (_: EmbyUnsupportedMediaTypeException) {
        SourcePlaybackResult.Failure(SourcePlaybackFailureReason.UnsupportedMediaType)
    } catch (_: UnsupportedOperationException) {
        SourcePlaybackResult.Failure(SourcePlaybackFailureReason.UnsupportedMediaType)
    } catch (_: Throwable) {
        SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unavailable)
    }

    override suspend fun download(
        kind: RemoteServerKind,
        encodedRemoteId: String,
    ): SourcePlaybackResult = try {
        require(kind == RemoteServerKind.Navidrome)
        val target = encodedRemoteId.decodeRemoteServerPlaybackTarget()
            ?: error("Remote server download target is invalid")
        val account = loadAccount(target.accountId, kind)
        val credential = storageRepository.loadCredentialByAccountId(target.accountId)
            ?: error("Remote server credential is unavailable")
        val config = NavidromeProviderConfigurationCodec.decode(account.providerConfig)
        val params = buildMap {
            put("id", target.remoteId)
            if (config.downloadMaxBitRate > 0) put("maxBitRate", config.downloadMaxBitRate.toString())
        }
        val endpoint = if (config.downloadMaxBitRate > 0) "stream" else "download"
        val uri = subsonicResourceUrl(
            account.endpoint.orEmpty(), credential.username, credential.secret, endpoint, params,
        )
        SourcePlaybackResult.Success(PlaybackResource(uri = uri))
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unavailable)
    }

    override suspend fun coverArt(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        coverArtId: String,
        size: Int,
        imageTag: String?,
    ): SourcePlaybackResult = try {
        val account = loadAccount(accountId, kind)
        val credential = storageRepository.loadCredentialByAccountId(accountId)
            ?: error("Remote server credential is unavailable")
        when (kind) {
            RemoteServerKind.Navidrome -> {
                val config = NavidromeProviderConfigurationCodec.decode(account.providerConfig)
                val requestedSize = size.takeIf {
                    it in io.github.julystar.musicapp.source.api.NavidromeProviderConfiguration.ALLOWED_COVER_ART_SIZES
                } ?: config.coverArtSize
                SourcePlaybackResult.Success(
                    PlaybackResource(
                        uri = subsonicResourceUrl(
                            account.endpoint.orEmpty(), credential.username, credential.secret,
                            "getCoverArt", mapOf("id" to coverArtId, "size" to requestedSize.toString()),
                        ),
                        mimeType = "image/*",
                    )
                )
            }
            RemoteServerKind.Emby -> SourcePlaybackResult.Success(
                PlaybackResource(
                    uri = ctEmbyResourceUrl(
                        account.endpoint.orEmpty(), credential.secret,
                        "Items/$coverArtId/Images/Primary",
                        buildMap {
                            put("maxWidth", size.coerceIn(1, 4096).toString())
                            put("quality", "90")
                            imageTag?.takeIf(String::isNotBlank)?.let { put("tag", it) }
                        },
                    ),
                    mimeType = "image/*",
                )
            )
            RemoteServerKind.OpenSubsonic -> error("OpenSubsonic artwork is unavailable")
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unavailable)
    }

    override suspend fun lyrics(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        artist: String,
        title: String,
    ): Result<RemoteServerLyrics> = try {
        require(kind == RemoteServerKind.Navidrome || kind == RemoteServerKind.OpenSubsonic)
        val account = loadAccount(accountId, kind)
        val credential = storageRepository.loadCredentialByAccountId(accountId)
            ?: error("Remote server credential is unavailable")
        val raw = executeSubsonicRequest(
            account, kind, credential, "getLyrics", mapOf("artist" to artist, "title" to title),
        ).value
        val root = json.parseToJsonElement(raw).jsonObject
        val lyrics = root.objectAt("subsonic-response", "lyrics")
            ?: error("lyrics response unavailable")
        val content = buildString {
            lyrics.string("value")?.let(::append)
            if (isEmpty()) {
                lyrics.string("lyrics")?.let(::append)
            }
        }.trim()
        require(content.isNotEmpty())
        Result.success(RemoteServerLyrics(
            content = content,
            format = if (content.lineSequence().any { it.trimStart().startsWith("[") }) "lrc" else "plain",
            synchronized = content.lineSequence().any { it.trimStart().matches(Regex("\\[\\d{1,2}:\\d{2}.*")) },
        ))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure<RemoteServerLyrics>(error)
    }

    override suspend fun refreshCapabilities(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
    ): Result<OpenSubsonicCapabilitySnapshot> = try {
        require(kind == RemoteServerKind.OpenSubsonic)
        val account = loadAccount(accountId, kind)
        val credential = storageRepository.loadCredentialByAccountId(accountId)
            ?: error("Remote server credential is unavailable")
        val response = subsonicResponse(executeSubsonicRequest(
            account, kind, credential, "getOpenSubsonicExtensions", emptyMap(),
        ).value)
        val extensions = (response["openSubsonicExtensions"] as? JsonArray)
            ?.mapNotNull { element ->
                val item = element as? JsonObject ?: return@mapNotNull null
                val name = item.string("name")?.trim()?.takeIf(String::isNotEmpty)
                    ?: return@mapNotNull null
                val versions = (item["versions"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
                    ?.filter { it > 0 }
                    ?.distinct()
                    ?.sorted()
                    .orEmpty()
                OpenSubsonicExtension(name, versions)
            }
            .orEmpty()
            .groupBy { it.name.lowercase() }
            .values
            .map { entries ->
                OpenSubsonicExtension(
                    name = entries.first().name,
                    versions = entries.flatMap { it.versions }.distinct().sorted(),
                )
            }
            .sortedBy { it.name.lowercase() }
        val snapshot = OpenSubsonicCapabilitySnapshot(extensions, currentTimeMillis())
        sourceAccountDao.upsert(account.copy(
            providerConfig = OpenSubsonicProviderConfigurationCodec.encode(
                OpenSubsonicProviderConfigurationCodec.decode(account.providerConfig).copy(
                    openSubsonicCapabilities = snapshot,
                ),
            ),
            updatedAt = currentTimeMillis(),
        ))
        Result.success(snapshot)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    override suspend fun openSubsonicLyrics(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        remoteId: String,
    ): Result<OpenSubsonicStructuredLyricsDocument> = try {
        require(kind == RemoteServerKind.OpenSubsonic)
        var account = loadAccount(accountId, kind)
        val credential = storageRepository.loadCredentialByAccountId(accountId)
            ?: error("Remote server credential is unavailable")
        var snapshot = OpenSubsonicCapabilityCodec.decode(account.providerConfig)
        if (snapshot == null) {
            refreshCapabilities(kind, accountId).getOrThrow()
            account = loadAccount(accountId, kind)
            snapshot = OpenSubsonicCapabilityCodec.decode(account.providerConfig)
        }
        snapshot ?: throw OpenSubsonicLyricsUnsupportedException()
        val extension = snapshot.extensions.firstOrNull { it.name.equals("songLyrics", ignoreCase = true) }
            ?: throw OpenSubsonicLyricsUnsupportedException()
        val params = buildMap {
            put("id", remoteId)
            if (extension.versions.any { it >= 2 }) put("enhanced", "true")
        }
        val response = subsonicResponse(executeSubsonicRequest(
            account, kind, credential, "getLyricsBySongId", params,
        ).value)
        val lyricsList = response["lyricsList"] as? JsonObject
        val structured = (lyricsList?.get("structuredLyrics") ?: response["structuredLyrics"])
            ?.let { element ->
                when (element) {
                    is JsonArray -> OpenSubsonicLyricsCodec.decode(buildJsonObject {
                        put("tracks", element)
                    })
                    is JsonObject -> OpenSubsonicLyricsCodec.decode(element)
                    else -> null
                }
            }
            ?.takeIf { it.tracks.isNotEmpty() }
            ?: throw OpenSubsonicLyricsUnsupportedException()
        Result.success(structured)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    override suspend fun playlists(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
    ): Result<List<RemoteServerPlaylistSummary>> = try {
        requireSubsonicPlaylistKind(kind)
        val account = loadAccount(accountId, kind)
        val credential = loadCredential(accountId)
        val response = subsonicResponse(executeSubsonicRequest(
            account, kind, credential, "getPlaylists", emptyMap(),
        ).value)
        val summaries = response.objectAt("playlists")?.array("playlist").orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.toPlaylistSummary(kind, accountId)
        }
        Result.success(summaries)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    override suspend fun playlist(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        remotePlaylistId: String,
    ): Result<RemoteServerPlaylist> = try {
        requireSubsonicPlaylistKind(kind)
        val account = loadAccount(accountId, kind)
        val credential = loadCredential(accountId)
        val responseResult = executeSubsonicRequest(
            account, kind, credential, "getPlaylist", mapOf("id" to remotePlaylistId),
        )
        val response = subsonicResponse(responseResult.value)
        val playlist = response.objectAt("playlist") ?: error("playlist response is missing")
        val summary = playlist.toPlaylistSummary(kind, accountId)
            ?: error("playlist identity is missing")
        val tracks = playlist.array("entry").orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.toRemoteServerTrack(accountId) { endpoint, params ->
                subsonicResourceUrl(
                    responseResult.endpoint,
                    credential.username,
                    credential.secret,
                    endpoint,
                    account.configuredSubsonicResourceParams(kind, endpoint, params),
                )
            }
        }
        Result.success(RemoteServerPlaylist(summary, tracks))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    override suspend fun createPlaylist(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        name: String,
        songIds: List<String>,
        playlistId: String?,
    ): Result<String> = try {
        require(name.isNotBlank() || !playlistId.isNullOrBlank()) { "playlist name or id is required" }
        require(songIds.all(String::isNotBlank)) { "playlist song ids must not be blank" }
        val response = writeResponse(kind, accountId, "createPlaylist", buildList {
            if (playlistId.isNullOrBlank()) addPair("name", name)
            else addPair("playlistId", playlistId)
            songIds.forEach { addPair("songId", it) }
        })
        val id = response.objectAt("playlist")?.string("id")
            ?: error("created playlist id is missing")
        Result.success(id)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    override suspend fun updatePlaylist(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        remotePlaylistId: String,
        name: String?,
        comment: String?,
        isPublic: Boolean?,
        songIdsToAdd: List<String>,
        songIndexesToRemove: List<Int>,
    ): Result<Unit> = try {
        require(remotePlaylistId.isNotBlank()) { "playlist id is required" }
        require(songIdsToAdd.all(String::isNotBlank)) { "playlist song ids must not be blank" }
        require(songIndexesToRemove.all { it >= 0 }) { "playlist song indexes must not be negative" }
        writeResponse(kind, accountId, "updatePlaylist", buildList {
            addPair("playlistId", remotePlaylistId)
            name?.let { addPair("name", it) }
            comment?.let { addPair("comment", it) }
            isPublic?.let { addPair("public", it.toString()) }
            songIdsToAdd.forEach { addPair("songIdToAdd", it) }
            songIndexesToRemove.forEach { addPair("songIndexToRemove", it.toString()) }
        })
        Result.success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    override suspend fun deletePlaylist(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        remotePlaylistId: String,
    ): Result<Unit> = try {
        require(remotePlaylistId.isNotBlank()) { "playlist id is required" }
        writeResponse(kind, accountId, "deletePlaylist", listOf(pair("id", remotePlaylistId)))
        Result.success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    override suspend fun starred(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
    ): Result<List<RemoteServerTrack>> = try {
        requireSubsonicPlaylistKind(kind)
        val account = loadAccount(accountId, kind)
        val credential = loadCredential(accountId)
        val response = try {
            executeSubsonicRequest(account, kind, credential, "getStarred2", emptyMap())
                .let { result ->
                    RemoteServerEndpointResult(subsonicResponse(result.value), result.endpoint)
                }
                .takeIf { it.value["starred2"] is JsonObject }
        } catch (error: RemoteMusicException) {
            if (error !is RemoteMusicException.NotFound &&
                error !is RemoteMusicException.ProtocolFailure
            ) throw error
            null
        }
        val payload = response ?: executeSubsonicRequest(account, kind, credential, "getStarred", emptyMap())
            .let { result ->
                RemoteServerEndpointResult(subsonicResponse(result.value), result.endpoint)
            }
        val starred = (payload.value["starred2"] as? JsonObject ?: payload.value["starred"] as? JsonObject)
        val tracks = starred?.array("song").orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.toRemoteServerTrack(accountId) { endpoint, params ->
                subsonicResourceUrl(
                    payload.endpoint,
                    credential.username,
                    credential.secret,
                    endpoint,
                    account.configuredSubsonicResourceParams(kind, endpoint, params),
                )
            }
        }
        Result.success(tracks)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    override suspend fun star(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        remoteIds: List<String>,
        albumIds: List<String>,
        artistIds: List<String>,
    ): Result<Unit> = try {
        require(remoteIds.isNotEmpty() || albumIds.isNotEmpty() || artistIds.isNotEmpty()) {
            "star requires at least one id"
        }
        require((remoteIds + albumIds + artistIds).all(String::isNotBlank)) { "star ids must not be blank" }
        writeResponse(kind, accountId, "star", buildList {
            remoteIds.forEach { addPair("id", it) }
            albumIds.forEach { addPair("albumId", it) }
            artistIds.forEach { addPair("artistId", it) }
        })
        Result.success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    override suspend fun unstar(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        remoteIds: List<String>,
        albumIds: List<String>,
        artistIds: List<String>,
    ): Result<Unit> = try {
        require(remoteIds.isNotEmpty() || albumIds.isNotEmpty() || artistIds.isNotEmpty()) {
            "unstar requires at least one id"
        }
        require((remoteIds + albumIds + artistIds).all(String::isNotBlank)) { "unstar ids must not be blank" }
        writeResponse(kind, accountId, "unstar", buildList {
            remoteIds.forEach { addPair("id", it) }
            albumIds.forEach { addPair("albumId", it) }
            artistIds.forEach { addPair("artistId", it) }
        })
        Result.success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    override suspend fun scrobble(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        submission: Boolean,
        events: List<RemoteServerScrobble>,
    ): Result<Unit> = try {
        require(events.isNotEmpty()) { "scrobble requires at least one event" }
        require(events.all { it.remoteId.isNotBlank() }) { "scrobble ids must not be blank" }
        require(events.map { it.timeMs != null }.distinct().size <= 1) {
            "scrobble events must either all include time or all omit time"
        }
        writeResponse(kind, accountId, "scrobble", buildList {
            addPair("submission", submission.toString())
            events.forEach { event ->
                addPair("id", event.remoteId)
                event.timeMs?.let { addPair("time", it.toString()) }
            }
        })
        Result.success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private suspend fun writeResponse(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        endpoint: String,
        params: List<Pair<String, String>>,
    ): JsonObject {
        requireSubsonicPlaylistKind(kind)
        val account = loadAccount(accountId, kind)
        ensureRemoteWriteEnabled(account, kind, accountId)
        val credential = loadCredential(accountId)
        return subsonicResponse(executeSubsonicRequestPairs(
            account,
            kind,
            credential,
            endpoint,
            params.map { (key, value) -> SubsonicQueryParameter(key, value) },
        ).value)
    }

    private suspend fun loadCredential(accountId: SourceAccountId) =
        storageRepository.loadCredentialByAccountId(accountId)
            ?: error("Remote server credential is unavailable")

    private fun executeSubsonicRequest(
        account: io.github.julystar.musicapp.database.SourceAccountEntity,
        kind: RemoteServerKind,
        credential: io.github.julystar.musicapp.core.domain.model.StoredCredential,
        endpoint: String,
        params: Map<String, String>,
    ): RemoteServerEndpointResult<String> = RemoteServerEndpointPolicy.execute(
        account.endpoint.orEmpty(),
        account.secondaryBaseUrl(kind),
    ) { baseUrl ->
        subsonicRequest(baseUrl, credential.username, credential.secret, endpoint, params)
    }

    private fun executeSubsonicRequestPairs(
        account: io.github.julystar.musicapp.database.SourceAccountEntity,
        kind: RemoteServerKind,
        credential: io.github.julystar.musicapp.core.domain.model.StoredCredential,
        endpoint: String,
        params: List<SubsonicQueryParameter>,
    ): RemoteServerEndpointResult<String> = RemoteServerEndpointPolicy.execute(
        account.endpoint.orEmpty(),
        account.secondaryBaseUrl(kind),
    ) { baseUrl ->
        subsonicRequestPairs(baseUrl, credential.username, credential.secret, endpoint, params)
    }

    private fun executeEmbyRequest(
        account: io.github.julystar.musicapp.database.SourceAccountEntity,
        token: String,
        path: String,
        params: Map<String, String>,
    ): RemoteServerEndpointResult<String> = RemoteServerEndpointPolicy.execute(
        account.endpoint.orEmpty(),
        account.secondaryBaseUrl(RemoteServerKind.Emby),
    ) { baseUrl ->
        embyRequest(baseUrl, token, path, params)
    }

    private fun negotiateEmbyPlayback(
        account: io.github.julystar.musicapp.database.SourceAccountEntity,
        credential: io.github.julystar.musicapp.core.domain.model.StoredCredential,
        target: io.github.julystar.musicapp.source.api.RemoteServerPlaybackTarget,
    ): PlaybackResource {
        val userId = account.externalAccountId?.trim().orEmpty()
        if (userId.isEmpty()) throw UnsupportedOperationException("Emby user identity is unavailable")
        val playbackInfo = executeEmbyRequest(
            account,
            credential.secret,
            "Items/${target.remoteId}/PlaybackInfo",
            mapOf("UserId" to userId),
        )
        val raw = playbackInfo.value
        val root = json.parseToJsonElement(raw) as? JsonObject
            ?: throw EmbyUnsupportedMediaTypeException()
        val sources = (root["MediaSources"] as? JsonArray)
            ?.map { it as? JsonObject ?: throw EmbyUnsupportedMediaTypeException() }
            ?: throw EmbyUnsupportedMediaTypeException()
        if (sources.isEmpty()) throw EmbyUnsupportedMediaTypeException()
        val requestedId = target.sourceMediaId?.trim()?.takeIf(String::isNotEmpty)
        val selected = if (requestedId != null) {
            sources.firstOrNull { it.string("Id") == requestedId }
                ?: throw EmbyUnsupportedMediaTypeException()
        } else {
            sources.firstOrNull { it.bool("SupportsDirectPlay") == true }
                ?: sources.firstOrNull { it.bool("SupportsDirectStream") == true }
                ?: throw EmbyUnsupportedMediaTypeException()
        }
        val directPlay = selected.bool("SupportsDirectPlay") == true
        val directStream = selected.bool("SupportsDirectStream") == true
        if (!directPlay && !directStream) throw EmbyUnsupportedMediaTypeException()
        val mediaId = selected.string("Id")?.takeIf(String::isNotBlank)
            ?: throw EmbyUnsupportedMediaTypeException()

        val headers = linkedMapOf("X-Emby-Token" to credential.secret)
        val requiredHeaders = selected["RequiredHttpHeaders"]?.let { element ->
            element as? JsonObject ?: throw EmbyUnsupportedMediaTypeException()
        }.orEmpty()
        requiredHeaders.forEach { (name, value) ->
            val primitive = value as? JsonPrimitive
            val headerValue = primitive
                ?.takeIf { it.isString }
                ?.contentOrNull
                ?: throw EmbyUnsupportedMediaTypeException()
            validatePlaybackHeader(name, headerValue, headers.keys)
            headers[name] = headerValue
        }
        return PlaybackResource(
            uri = embyPlaybackUrl(
                playbackInfo.endpoint, target.remoteId, userId, mediaId,
            ),
            headers = headers,
            mimeType = selected.string("MimeType") ?: selected.string("Container")?.toEmbyMimeType(),
            expiresAtEpochMs = nowEpochMs() + EMBY_PLAYBACK_TTL_MS,
        )
    }

    private fun validatePlaybackHeader(
        name: String,
        value: String,
        existingNames: Set<String>,
    ) {
        val forbidden = setOf("host", "content-length", "range", "connection", "transfer-encoding")
        if (!(
            name.isNotBlank() &&
                name.trim() == name &&
                name.all { it.code in 33..126 && it in "!#$%&'*+-.^_`|~0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz" } &&
                name.lowercase() !in forbidden
        )) throw EmbyUnsupportedMediaTypeException()
        if (value.any { it == '\r' || it == '\n' }) throw EmbyUnsupportedMediaTypeException()
        if (existingNames.any { it.equals(name, ignoreCase = true) }) {
            throw EmbyUnsupportedMediaTypeException()
        }
    }

    private fun ensureRemoteWriteEnabled(
        account: io.github.julystar.musicapp.database.SourceAccountEntity,
        kind: RemoteServerKind,
        accountId: SourceAccountId,
    ) {
        val enabled = when (kind) {
            RemoteServerKind.Navidrome -> NavidromeProviderConfigurationCodec
                .decode(account.providerConfig).remoteWriteEnabled
            RemoteServerKind.OpenSubsonic -> OpenSubsonicCapabilityCodec
                .remoteWriteEnabled(account.providerConfig)
            RemoteServerKind.Emby -> false
        }
        if (!enabled) throw RemoteServerWriteDisabledException(kind, accountId)
    }

    private fun requireSubsonicPlaylistKind(kind: RemoteServerKind) {
        require(kind == RemoteServerKind.Navidrome || kind == RemoteServerKind.OpenSubsonic) {
            "playlist operations require a Subsonic server"
        }
    }

    private fun subsonicResponse(raw: String): JsonObject {
        val response = json.parseToJsonElement(raw).jsonObject["subsonic-response"]?.jsonObject
            ?: error("Subsonic response envelope is missing")
        require(response.string("status")?.equals("ok", ignoreCase = true) == true) {
            "Subsonic response status is not successful"
        }
        return response
    }

    private suspend fun loadAccount(accountId: SourceAccountId, kind: RemoteServerKind) =
        accountId.toStorageRouteIdOrNull()
            ?.let { sourceAccountDao.get(it) }
            ?.takeIf { account -> account.providerType == kind.providerType }
            ?: error("Remote server account is unavailable")

}

internal class SubsonicTrackPager(
    private val accountId: SourceAccountId,
    private val pageSize: Int,
    private val streamMaxBitRate: Int = 0,
    private val coverArtSize: Int = 512,
    private val request: (endpoint: String, params: Map<String, String>) -> RemoteServerEndpointResult<String>,
    private val resourceUrl: (baseUrl: String, endpoint: String, params: Map<String, String>) -> String,
) {
    private enum class SearchTermination {
        EmptyWithoutResults,
        Repeated,
        Complete,
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun pages(query: String?): Flow<Result<RemoteServerTrackPage>> = flow {
        val effectivePageSize = pageSize.coerceIn(1, 500)
        val seenSongIds = mutableSetOf<String>()
        try {
            val termination = emitSearchPages(this, query.orEmpty(), effectivePageSize, seenSongIds)
            if (
                query.isNullOrBlank() &&
                termination != SearchTermination.Complete
            ) {
                emitAlbumFallback(this, effectivePageSize, seenSongIds)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            emit(Result.failure(error))
        }
    }

    private suspend fun emitSearchPages(
        collector: FlowCollector<Result<RemoteServerTrackPage>>,
        query: String,
        pageSize: Int,
        seenSongIds: MutableSet<String>,
    ): SearchTermination {
        var offset = 0
        while (true) {
            val response = request(
                "search3",
                mapOf(
                    "query" to query,
                    "songCount" to pageSize.toString(),
                    "songOffset" to offset.toString(),
                    "albumCount" to "0",
                    "artistCount" to "0",
                ),
            )
            val songs = json.parseToJsonElement(response.value).jsonObject
                .objectAt("subsonic-response", "searchResult3")
                ?.array("song")
                .orEmpty()
            if (songs.isEmpty()) {
                return if (seenSongIds.isEmpty()) {
                    SearchTermination.EmptyWithoutResults
                } else {
                    SearchTermination.Complete
                }
            }

            val tracks = songs.mapNotNull { element ->
                val song = element as? JsonObject ?: return@mapNotNull null
                val id = song.string("id") ?: return@mapNotNull null
                if (!seenSongIds.add(id)) return@mapNotNull null
                song.toRemoteServerTrack(accountId) { endpoint, params ->
                    resourceUrl(response.endpoint, endpoint, configuredResourceParams(endpoint, params))
                }
            }
            if (tracks.isEmpty()) return SearchTermination.Repeated
            collector.emit(Result.success(RemoteServerTrackPage(tracks, offset)))
            if (songs.size < pageSize) return SearchTermination.Complete
            offset += songs.size
        }
    }

    private suspend fun emitAlbumFallback(
        collector: FlowCollector<Result<RemoteServerTrackPage>>,
        pageSize: Int,
        seenSongIds: MutableSet<String>,
    ) {
        val seenAlbumIds = mutableSetOf<String>()
        val bufferedTracks = mutableListOf<RemoteServerTrack>()
        var offset = 0
        while (true) {
            val response = request(
                "getAlbumList2",
                mapOf(
                    "type" to "alphabeticalByName",
                    "size" to pageSize.toString(),
                    "offset" to offset.toString(),
                ),
            )
            val albums = json.parseToJsonElement(response.value).jsonObject
                .objectAt("subsonic-response", "albumList2")
                ?.array("album")
                .orEmpty()
            if (albums.isEmpty()) {
                if (bufferedTracks.isNotEmpty()) {
                    collector.emit(Result.success(RemoteServerTrackPage(bufferedTracks.toList(), offset)))
                }
                return
            }

            val newAlbums = albums.mapNotNull { element ->
                val album = element as? JsonObject ?: return@mapNotNull null
                val id = album.string("id") ?: return@mapNotNull null
                if (seenAlbumIds.add(id)) id else null
            }
            if (newAlbums.isEmpty()) {
                if (bufferedTracks.isNotEmpty()) {
                    collector.emit(Result.success(RemoteServerTrackPage(bufferedTracks.toList(), offset)))
                }
                return
            }

            for (albumId in newAlbums) {
                val albumResponse = request("getAlbum", mapOf("id" to albumId))
                val songs = json.parseToJsonElement(albumResponse.value).jsonObject
                    .objectAt("subsonic-response", "album")
                    ?.array("song")
                    .orEmpty()
                for (element in songs) {
                    val song = element as? JsonObject ?: continue
                    val id = song.string("id") ?: continue
                    if (!seenSongIds.add(id)) continue
                    bufferedTracks += song.toRemoteServerTrack(accountId) { endpoint, params ->
                        resourceUrl(
                            albumResponse.endpoint,
                            endpoint,
                            configuredResourceParams(endpoint, params),
                        )
                    }
                    while (bufferedTracks.size >= pageSize) {
                        collector.emit(
                            Result.success(
                                RemoteServerTrackPage(
                                    tracks = bufferedTracks.take(pageSize),
                                    offset = offset,
                                )
                            )
                        )
                        repeat(pageSize) { bufferedTracks.removeAt(0) }
                    }
                }
            }

            if (albums.size < pageSize) {
                if (bufferedTracks.isNotEmpty()) {
                    collector.emit(Result.success(RemoteServerTrackPage(bufferedTracks.toList(), offset)))
                }
                return
            }
            offset += albums.size
        }
    }

    private fun configuredResourceParams(
        endpoint: String,
        params: Map<String, String>,
    ): Map<String, String> = buildMap {
        putAll(params)
        if (endpoint == "stream" && streamMaxBitRate > 0) {
            put("maxBitRate", streamMaxBitRate.toString())
        }
        if (endpoint == "getCoverArt") {
            put("size", coverArtSize.toString())
        }
    }
}

private fun JsonObject.toRemoteServerTrack(
    accountId: SourceAccountId,
    resourceUrl: (endpoint: String, params: Map<String, String>) -> String,
): RemoteServerTrack {
    val id = string("id") ?: error("Subsonic song ID is missing")
    return RemoteServerTrack(
        accountId = accountId,
        remoteId = id,
        title = string("title") ?: id,
        artist = string("artist"),
        album = string("album"),
        albumArtist = string("albumArtist"),
        genre = string("genre"),
        year = int("year"),
        track = int("track"),
        discNumber = int("discNumber"),
        suffix = string("suffix"),
        durationMs = long("duration")?.times(1_000L),
        streamUrl = resourceUrl("stream", mapOf("id" to id)),
        coverUrl = string("coverArt")?.let { coverId ->
            resourceUrl("getCoverArt", mapOf("id" to coverId, "size" to "512"))
        },
        coverArtId = string("coverArt"),
        mimeType = string("contentType"),
        bitRate = int("bitRate"),
        sampleRate = int("samplingRate"),
        bitDepth = int("bitDepth"),
        channelCount = int("channelCount"),
        audioProperties = SubsonicAudioPropertiesMapper.map(this),
    )
}

private fun JsonObject.toPlaylistSummary(
    kind: RemoteServerKind,
    accountId: SourceAccountId,
): RemoteServerPlaylistSummary? {
    val id = string("id")?.takeIf(String::isNotBlank) ?: return null
    return RemoteServerPlaylistSummary(
        identity = RemoteServerPlaylistIdentity(kind, accountId, id),
        name = string("name") ?: id,
        comment = string("comment"),
        isPublic = (get("public") as? JsonPrimitive)?.booleanOrNull,
        songCount = int("songCount"),
        durationMs = long("duration")?.times(1_000L),
    )
}

private fun pair(key: String, value: String): Pair<String, String> = key to value

private fun MutableList<Pair<String, String>>.addPair(key: String, value: String) {
    add(pair(key, value))
}

internal fun Throwable.toRemoteAuthFailureReason(): SourceAuthFailureReason = when (this) {
    is RemoteMusicException -> when (this) {
        is RemoteMusicException.InvalidAddress -> SourceAuthFailureReason.InvalidAddress
        is RemoteMusicException.Timeout -> SourceAuthFailureReason.Timeout
        is RemoteMusicException.Connectivity -> SourceAuthFailureReason.Unavailable
        is RemoteMusicException.Unauthorized -> SourceAuthFailureReason.Unauthorized
        is RemoteMusicException.PermissionDenied -> SourceAuthFailureReason.PermissionDenied
        is RemoteMusicException.NotFound -> SourceAuthFailureReason.NotFound
        is RemoteMusicException.HttpFailure,
        is RemoteMusicException.InvalidResponse,
        is RemoteMusicException.ProtocolFailure,
        is RemoteMusicException.Unavailable -> SourceAuthFailureReason.Unavailable
    }
    else -> SourceAuthFailureReason.Unavailable
}

private val RemoteServerKind.providerType: String
    get() = when (this) {
        RemoteServerKind.Navidrome -> ProviderTypes.Navidrome
        RemoteServerKind.OpenSubsonic -> ProviderTypes.OpenSubsonic
        RemoteServerKind.Emby -> ProviderTypes.Emby
    }

private data class SubsonicProviderConfiguration(
    val streamMaxBitRate: Int,
    val downloadMaxBitRate: Int,
    val coverArtSize: Int,
)

private fun io.github.julystar.musicapp.database.SourceAccountEntity.subsonicProviderConfiguration(
    kind: RemoteServerKind,
): SubsonicProviderConfiguration = when (kind) {
    RemoteServerKind.Navidrome -> NavidromeProviderConfigurationCodec.decode(providerConfig).let { config ->
        SubsonicProviderConfiguration(
            config.streamMaxBitRate,
            config.downloadMaxBitRate,
            config.coverArtSize,
        )
    }
    RemoteServerKind.OpenSubsonic -> OpenSubsonicProviderConfigurationCodec.decode(providerConfig).let { config ->
        SubsonicProviderConfiguration(
            config.streamMaxBitRate,
            config.downloadMaxBitRate,
            config.coverArtSize,
        )
    }
    RemoteServerKind.Emby -> error("Emby does not use Subsonic configuration")
}

private fun io.github.julystar.musicapp.database.SourceAccountEntity.secondaryBaseUrl(
    kind: RemoteServerKind,
): String? = when (kind) {
    RemoteServerKind.Navidrome -> NavidromeProviderConfigurationCodec.decode(providerConfig).secondaryBaseUrl
    RemoteServerKind.OpenSubsonic -> OpenSubsonicProviderConfigurationCodec.decode(providerConfig).secondaryBaseUrl
    RemoteServerKind.Emby -> EmbyProviderConfigurationCodec.decode(providerConfig).secondaryBaseUrl
}

private fun io.github.julystar.musicapp.database.SourceAccountEntity.configuredSubsonicResourceParams(
    kind: RemoteServerKind,
    endpoint: String,
    params: Map<String, String>,
): Map<String, String> {
    val configuration = subsonicProviderConfiguration(kind)
    return buildMap {
        putAll(params)
        if (endpoint == "stream" && configuration.streamMaxBitRate > 0) {
            put("maxBitRate", configuration.streamMaxBitRate.toString())
        }
        if (endpoint == "getCoverArt") {
            put("size", configuration.coverArtSize.toString())
        }
    }
}

private fun JsonObject.objectAt(vararg names: String): JsonObject? =
    names.fold(this as JsonObject?) { current, name -> current?.get(name) as? JsonObject }

private fun JsonObject.array(name: String): JsonArray? = get(name) as? JsonArray
private fun JsonObject.string(name: String): String? =
    (get(name) as? JsonPrimitive)?.contentOrNull

private fun JsonObject.long(name: String): Long? = (get(name) as? JsonPrimitive)?.longOrNull

private fun JsonObject.int(name: String): Int? = (get(name) as? JsonPrimitive)?.intOrNull

private fun JsonObject.bool(name: String): Boolean? =
    (get(name) as? JsonPrimitive)?.booleanOrNull

private fun String.toEmbyMimeType(): String? = when (trim().lowercase()) {
    "mp3" -> "audio/mpeg"
    "flac" -> "audio/flac"
    "m4a", "mp4", "aac" -> "audio/mp4"
    "ogg", "oga", "opus" -> "audio/ogg"
    "wav" -> "audio/wav"
    else -> null
}

private val JsonPrimitive.booleanOrNull: Boolean?
    get() = contentOrNull?.toBooleanStrictOrNull()
