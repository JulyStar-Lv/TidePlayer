package io.github.julystar.musicapp.source.server

import io.github.julystar.musicapp.core.domain.model.*
import io.github.julystar.musicapp.core.domain.repository.StorageRepository
import io.github.julystar.musicapp.database.*
import io.github.julystar.musicapp.source.api.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import uniffi.app_backend.RemoteMusicException
import uniffi.app_backend.EmbyLoginIdentity

class RemoteServerGatewayImplTest {
    @Test
    fun embyTrackPagesRejectsMissingExternalUserBeforeRequest() = runTest {
        var requests = 0
        val gateway = RemoteServerGatewayImpl(
            FakeAccountDao({ navidromeAccount(null).copy(providerType = ProviderTypes.Emby) }),
            FakeStorageRepository(),
            embyRequest = { _, _, _, _ -> requests++; error("must not request") },
        )
        val result = gateway.trackPages(
            RemoteServerKind.Emby,
            SourceAccountId("storage:7"),
            pageSize = 10,
        ).toList()
        assertEquals(0, requests)
        assertTrue(result.single().isFailure)
    }

    @Test
    fun embyTrackPagesWiresScopedUserAndRequiredPagingParameters() = runTest {
        val requests = mutableListOf<Pair<String, Map<String, String>>>()
        val account = navidromeAccount(null).copy(
            providerType = ProviderTypes.Emby,
            externalAccountId = "emby-user",
        )
        val gateway = RemoteServerGatewayImpl(
            FakeAccountDao({ account }),
            FakeStorageRepository(),
            embyRequest = { _, token, path, params ->
                assertEquals("secret", token)
                requests += path to params
                """{"TotalRecordCount":1,"Items":[{"Id":"opaque/id","Name":"Track","ImageTags":{"Primary":"tag"}}]}"""
            },
        )
        val pages = gateway.trackPages(
            RemoteServerKind.Emby,
            SourceAccountId("storage:7"),
            query = "opaque query",
            pageSize = 900,
        ).toList().map { it.getOrThrow() }
        assertEquals("opaque/id", pages.single().tracks.single().remoteId)
        assertEquals("Users/emby-user/Items", requests.first().first)
        assertEquals(
            mapOf(
                "Recursive" to "true",
                "IncludeItemTypes" to "Audio",
                "StartIndex" to "0",
                "Limit" to "500",
                "SortBy" to "SortName",
                "SortOrder" to "Ascending",
                "Fields" to "Genres,MediaSources,MediaStreams,AlbumArtist,UserData",
                "SearchTerm" to "opaque query",
            ),
            requests.first().second,
        )
    }

    @Test
    fun embyAuthenticateUsesTypedLoginAndMapsUnauthorized() = runTest {
        var unauthorized = false
        val gateway = RemoteServerGatewayImpl(
            FakeAccountDao({ navidromeAccount(null) }),
            FakeStorageRepository(),
            embyLogin = { _, _, _ ->
                if (unauthorized) throw RemoteMusicException.Unauthorized()
                EmbyLoginIdentity("token", "user-1", "server-1", "Server")
            },
        )
        val configuration = RemoteServerSourceConfiguration(
            alias = "Emby",
            kind = RemoteServerKind.Emby,
            address = "https://emby.example",
            username = "alice",
            password = "password",
        )
        assertTrue(gateway.authenticate(configuration) is SourceAuthResult.Success)
        unauthorized = true
        val failure = gateway.authenticate(configuration) as SourceAuthResult.Failure
        assertEquals(SourceAuthFailureReason.Unauthorized, failure.reason)
    }

    @Test
    fun playlistReadsParseEntriesAndStarredFallsBackOnlyWhenPayloadIsUnsupported() = runTest {
        val requests = mutableListOf<String>()
        val gateway = RemoteServerGatewayImpl(
            FakeAccountDao({ navidromeAccount(null) }),
            FakeStorageRepository(),
            subsonicRequest = { _, _, _, endpoint, _ ->
                requests += endpoint
                when (endpoint) {
                    "getPlaylists" -> """{"subsonic-response":{"status":"ok","playlists":{"playlist":[{"id":"p|1","name":"Mix","songCount":1}]}}}"""
                    "getPlaylist" -> """{"subsonic-response":{"status":"ok","playlist":{"id":"p|1","name":"Mix","entry":[{"id":"song|1","title":"Track"}]}}}"""
                    "getStarred2" -> """{"subsonic-response":{"status":"ok"}}"""
                    else -> """{"subsonic-response":{"status":"ok","starred":{"song":[{"id":"star|1","title":"Starred"}]}}}"""
                }
            },
            subsonicResourceUrl = { _, _, _, endpoint, _ -> "https://server/$endpoint" },
        )
        val summaries = gateway.playlists(RemoteServerKind.Navidrome, SourceAccountId("storage:7")).getOrThrow()
        assertEquals("p|1", summaries.single().identity.remotePlaylistId)
        val detail = gateway.playlist(RemoteServerKind.Navidrome, SourceAccountId("storage:7"), "p|1").getOrThrow()
        assertEquals("song|1", detail.tracks.single().remoteId)
        val starred = gateway.starred(RemoteServerKind.Navidrome, SourceAccountId("storage:7")).getOrThrow()
        assertEquals("star|1", starred.single().remoteId)
        assertEquals(listOf("getPlaylists", "getPlaylist", "getStarred2", "getStarred"), requests)
    }

    @Test
    fun writesUseOrderedDuplicatePairsAndGateBeforeCredentialLookup() = runTest {
        val disabledStorage = FakeStorageRepository()
        val disabled = RemoteServerGatewayImpl(
            FakeAccountDao({ navidromeAccount(null) }), disabledStorage,
            subsonicRequestPairs = { _, _, _, _, _ -> error("write must be gated") },
        )
        val disabledResult = disabled.createPlaylist(
            RemoteServerKind.Navidrome, SourceAccountId("storage:7"), "Mix", listOf("a"),
        )
        assertTrue(disabledResult.exceptionOrNull() is RemoteServerWriteDisabledException)
        assertEquals(0, disabledStorage.credentialLookups)

        val calls = mutableListOf<Pair<String, List<Pair<String, String>>>>()
        val enabledStorage = FakeStorageRepository()
        val enabledAccount = navidromeAccount(NavidromeProviderConfigurationCodec.encode(
            NavidromeProviderConfiguration(remoteWriteEnabled = true),
        ))
        val enabled = RemoteServerGatewayImpl(
            FakeAccountDao({ enabledAccount }), enabledStorage,
            subsonicRequestPairs = { _, _, _, endpoint, values ->
                calls += endpoint to values.map { it.key to it.value }
                """{"subsonic-response":{"status":"ok","playlist":{"id":"created|1"}}}"""
            },
        )
        assertEquals("created|1", enabled.createPlaylist(
            RemoteServerKind.Navidrome, SourceAccountId("storage:7"), "Mix", listOf("song|1", "song|1"),
        ).getOrThrow())
        assertEquals(listOf(
            "name" to "Mix",
            "songId" to "song|1",
            "songId" to "song|1",
        ), calls.single().second)
        enabled.updatePlaylist(
            RemoteServerKind.Navidrome,
            SourceAccountId("storage:7"),
            "created|1",
            name = "Renamed",
            comment = "Comment",
            isPublic = true,
            songIdsToAdd = listOf("add|1", "add|1"),
            songIndexesToRemove = listOf(0, 0),
        ).getOrThrow()
        enabled.deletePlaylist(RemoteServerKind.Navidrome, SourceAccountId("storage:7"), "created|1").getOrThrow()
        enabled.star(
            RemoteServerKind.Navidrome,
            SourceAccountId("storage:7"),
            remoteIds = listOf("song|1", "song|1"),
            albumIds = listOf("album|1"),
            artistIds = listOf("artist|1", "artist|1"),
        ).getOrThrow()
        enabled.unstar(
            RemoteServerKind.Navidrome,
            SourceAccountId("storage:7"),
            remoteIds = listOf("song|1"),
        ).getOrThrow()
        enabled.scrobble(
            RemoteServerKind.Navidrome,
            SourceAccountId("storage:7"),
            submission = false,
            events = listOf(RemoteServerScrobble("now-playing|1")),
        ).getOrThrow()
        enabled.scrobble(
            RemoteServerKind.Navidrome,
            SourceAccountId("storage:7"),
            submission = true,
            events = listOf(
                RemoteServerScrobble("song|1", 1000),
                RemoteServerScrobble("song|2", 2000),
            ),
        ).getOrThrow()
        assertEquals(7, calls.size)
        assertEquals(listOf("playlistId" to "created|1", "name" to "Renamed", "comment" to "Comment", "public" to "true", "songIdToAdd" to "add|1", "songIdToAdd" to "add|1", "songIndexToRemove" to "0", "songIndexToRemove" to "0"), calls[1].second)
        assertEquals(listOf("id" to "created|1"), calls[2].second)
        assertEquals(listOf("id" to "song|1", "id" to "song|1", "albumId" to "album|1", "artistId" to "artist|1", "artistId" to "artist|1"), calls[3].second)
        assertEquals(listOf("id" to "song|1"), calls[4].second)
        assertEquals(listOf("submission" to "false", "id" to "now-playing|1"), calls[5].second)
        assertEquals(listOf("submission" to "true", "id" to "song|1", "time" to "1000", "id" to "song|2", "time" to "2000"), calls[6].second)
        assertEquals(7, enabledStorage.credentialLookups)
    }

    @Test
    fun everyWriteIsDisabledBeforeCredentialLookupForDefaultAndMalformedConfig() = runTest {
        suspend fun assertAllWritesDisabled(config: String?) {
            val storage = FakeStorageRepository()
            var requests = 0
            val gateway = RemoteServerGatewayImpl(
                FakeAccountDao({ navidromeAccount(config) }), storage,
                subsonicRequestPairs = { _, _, _, _, _ -> requests++; error("disabled write must not request") },
            )
            suspend fun assertDisabled(result: Result<*>) {
                assertTrue(result.exceptionOrNull() is RemoteServerWriteDisabledException)
            }
            assertDisabled(gateway.createPlaylist(RemoteServerKind.Navidrome, SourceAccountId("storage:7"), "Mix", listOf("song")))
            assertDisabled(gateway.updatePlaylist(RemoteServerKind.Navidrome, SourceAccountId("storage:7"), "playlist"))
            assertDisabled(gateway.deletePlaylist(RemoteServerKind.Navidrome, SourceAccountId("storage:7"), "playlist"))
            assertDisabled(gateway.star(RemoteServerKind.Navidrome, SourceAccountId("storage:7"), listOf("song"), emptyList(), emptyList()))
            assertDisabled(gateway.unstar(RemoteServerKind.Navidrome, SourceAccountId("storage:7"), listOf("song"), emptyList(), emptyList()))
            assertDisabled(gateway.scrobble(RemoteServerKind.Navidrome, SourceAccountId("storage:7"), false, listOf(RemoteServerScrobble("song"))))
            assertDisabled(gateway.scrobble(RemoteServerKind.Navidrome, SourceAccountId("storage:7"), true, listOf(RemoteServerScrobble("song", 1234))))
            assertEquals(0, storage.credentialLookups)
            assertEquals(0, requests)
        }

        assertAllWritesDisabled(null)
        assertAllWritesDisabled("{malformed provider config")
    }

    @Test
    fun updatePlaylistRejectsNegativeIndexesBeforeCredentialLookup() = runTest {
        val storage = FakeStorageRepository()
        val gateway = RemoteServerGatewayImpl(
            FakeAccountDao({
                navidromeAccount(NavidromeProviderConfigurationCodec.encode(
                    NavidromeProviderConfiguration(remoteWriteEnabled = true),
                ))
            }),
            storage,
            subsonicRequestPairs = { _, _, _, _, _ -> error("invalid index must not request") },
        )
        val result = gateway.updatePlaylist(
            RemoteServerKind.Navidrome,
            SourceAccountId("storage:7"),
            "playlist",
            songIndexesToRemove = listOf(-1),
        )
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, storage.credentialLookups)
    }

    @Test
    fun starredMalformedAuthAndPermissionResponsesNeverFallback() = runTest {
        val malformedEndpoints = mutableListOf<String>()
        val malformedGateway = RemoteServerGatewayImpl(
            FakeAccountDao({ navidromeAccount(null) }),
            FakeStorageRepository(),
            subsonicRequest = { _, _, _, endpoint, _ ->
                malformedEndpoints += endpoint
                "not-json"
            },
        )
        assertTrue(malformedGateway.starred(RemoteServerKind.Navidrome, SourceAccountId("storage:7")).isFailure)
        assertEquals(listOf("getStarred2"), malformedEndpoints)

        for (error in listOf<Throwable>(
            RemoteMusicException.Unauthorized(),
            RemoteMusicException.PermissionDenied(),
        )) {
            val endpoints = mutableListOf<String>()
            val gateway = RemoteServerGatewayImpl(
                FakeAccountDao({ navidromeAccount(null) }),
                FakeStorageRepository(),
                subsonicRequest = { _, _, _, endpoint, _ ->
                    endpoints += endpoint
                    throw error
                },
            )
            val result = gateway.starred(RemoteServerKind.Navidrome, SourceAccountId("storage:7"))
            assertTrue(result.isFailure)
            assertEquals(listOf("getStarred2"), endpoints)
        }
    }

    @Test
    fun openSubsonicRefreshPreservesRemoteWriteGate() = runTest {
        var account = navidromeAccount(OpenSubsonicProviderConfigurationCodec.encode(
            OpenSubsonicProviderConfiguration(
                streamMaxBitRate = 192,
                downloadMaxBitRate = 320,
                coverArtSize = 1024,
                remoteWriteEnabled = true,
                secondaryBaseUrl = "https://secondary.example",
                openSubsonicCapabilities = OpenSubsonicCapabilitySnapshot(emptyList(), 1),
            ),
        )).copy(providerType = ProviderTypes.OpenSubsonic)
        val gateway = RemoteServerGatewayImpl(
            FakeAccountDao({ account }) { account = it }, FakeStorageRepository(),
            subsonicRequest = { _, _, _, _, _ ->
                """{"subsonic-response":{"status":"ok","openSubsonicExtensions":[]}}"""
            },
        )
        gateway.refreshCapabilities(RemoteServerKind.OpenSubsonic, SourceAccountId("storage:7")).getOrThrow()
        val refreshed = OpenSubsonicProviderConfigurationCodec.decode(account.providerConfig)
        assertTrue(refreshed.remoteWriteEnabled)
        assertEquals(192, refreshed.streamMaxBitRate)
        assertEquals(320, refreshed.downloadMaxBitRate)
        assertEquals(1024, refreshed.coverArtSize)
        assertEquals("https://secondary.example", refreshed.secondaryBaseUrl)
        assertTrue(refreshed.openSubsonicCapabilities != null)
    }

    @Test
    fun emptyCapabilitiesArePersistedAndFirstUseDetectsOnlyOnce() = runTest {
        var account = navidromeAccount(null).copy(providerType = ProviderTypes.OpenSubsonic)
        var detectionCalls = 0
        val gateway = RemoteServerGatewayImpl(
            sourceAccountDao = FakeAccountDao({ account }) { account = it },
            storageRepository = FakeStorageRepository(),
            subsonicRequest = { _, _, _, endpoint, _ ->
                if (endpoint == "getOpenSubsonicExtensions") {
                    detectionCalls++
                    """{"subsonic-response":{"status":"ok","openSubsonicExtensions":[]}}"""
                } else """{"subsonic-response":{"status":"ok","searchResult3":{"song":[]}}}"""
            },
        )
        val first = gateway.trackPages(RemoteServerKind.OpenSubsonic, SourceAccountId("storage:7"), pageSize = 50).toList()
        val second = gateway.trackPages(RemoteServerKind.OpenSubsonic, SourceAccountId("storage:7"), pageSize = 50).toList()
        assertTrue(first.isEmpty() && second.isEmpty())
        assertEquals(1, detectionCalls)
        assertTrue(account.providerConfig.orEmpty().contains("openSubsonicCapabilities"))
        assertTrue("user" !in account.providerConfig.orEmpty())
        assertTrue("secret" !in account.providerConfig.orEmpty())
        assertTrue("token=secret" !in account.providerConfig.orEmpty())
        assertTrue("https://server.test" !in account.providerConfig.orEmpty())
    }

    @Test
    fun unsupportedAndEmptyStructuredResponsesAreTypedUnsupported() = runTest {
        val emptyConfigAccount = navidromeAccount(OpenSubsonicCapabilityCodec.encode(
            OpenSubsonicCapabilitySnapshot(emptyList(), 1)
        )).copy(providerType = ProviderTypes.OpenSubsonic)
        val gateway = RemoteServerGatewayImpl(
            FakeAccountDao({ emptyConfigAccount }), FakeStorageRepository(),
            subsonicRequest = { _, _, _, _, _ -> """{"subsonic-response":{"status":"ok","lyricsList":{"structuredLyrics":[]}}}""" },
        )
        val error = gateway.openSubsonicLyrics(RemoteServerKind.OpenSubsonic, SourceAccountId("storage:7"), "id").exceptionOrNull()
        assertTrue(error is OpenSubsonicLyricsUnsupportedException)
    }

    @Test
    fun songLyricsV1OmitsEnhancedAndExplicitRefreshReplacesSnapshot() = runTest {
        var account = navidromeAccount(OpenSubsonicCapabilityCodec.encode(
            OpenSubsonicCapabilitySnapshot(
                extensions = listOf(OpenSubsonicExtension("songLyrics", listOf(1))),
                checkedAtEpochMs = 1,
            )
        )).copy(providerType = ProviderTypes.OpenSubsonic)
        var capabilities = "songLyrics"
        val requests = mutableListOf<Pair<String, Map<String, String>>>()
        val gateway = RemoteServerGatewayImpl(
            FakeAccountDao({ account }) { account = it }, FakeStorageRepository(),
            subsonicRequest = { _, _, _, endpoint, params ->
                requests += endpoint to params
                if (endpoint == "getOpenSubsonicExtensions") {
                    """{"subsonic-response":{"status":"ok","openSubsonicExtensions":[{"name":"$capabilities","versions":[1]}]}}"""
                } else """{"subsonic-response":{"status":"ok","lyricsList":{"structuredLyrics":[{"line":[{"value":"line"}]}]}}}"""
            },
        )
        gateway.openSubsonicLyrics(RemoteServerKind.OpenSubsonic, SourceAccountId("storage:7"), "id")
        assertTrue("enhanced" !in requests.last().second)
        capabilities = "z"
        gateway.refreshCapabilities(RemoteServerKind.OpenSubsonic, SourceAccountId("storage:7"))
        assertTrue(account.providerConfig.orEmpty().contains("\"name\":\"z\""))
    }

    @Test
    fun refreshesAndPersistsOpenSubsonicCapabilitiesAndUsesV2EnhancedLyrics() = runTest {
        var account = navidromeAccount(null).copy(providerType = ProviderTypes.OpenSubsonic)
        val requests = mutableListOf<Pair<String, Map<String, String>>>()
        val gateway = RemoteServerGatewayImpl(
            sourceAccountDao = FakeAccountDao({ account }) { account = it },
            storageRepository = FakeStorageRepository(),
            subsonicRequest = { _, _, _, endpoint, params ->
                requests += endpoint to params
                when (endpoint) {
                    "getOpenSubsonicExtensions" -> """{"subsonic-response":{"status":"ok","openSubsonicExtensions":[{"name":"songLyrics","versions":[2,1,2]},{"name":"z","versions":[1]}]}}"""
                    else -> """{"subsonic-response":{"status":"ok","lyricsList":{"structuredLyrics":[{"displayArtist":"歌手","lang":"zh","synced":true,"line":[{"start":100,"value":"你好"}],"agents":[{"id":"a"}],"cueLine":[{"index":0,"start":100,"end":900,"value":"你好","agentId":"a","cue":[{"start":100,"end":400,"value":"你","byteStart":0,"byteEnd":2}]}]}]}}}"""
                }
            },
        )
        val snapshot = gateway.refreshCapabilities(RemoteServerKind.OpenSubsonic, SourceAccountId("storage:7")).getOrThrow()
        assertEquals(listOf(1, 2), snapshot.extensions.first { it.name == "songLyrics" }.versions)
        val lyrics = gateway.openSubsonicLyrics(RemoteServerKind.OpenSubsonic, SourceAccountId("storage:7"), "opaque").getOrThrow()
        assertEquals("true", requests.last().second["enhanced"])
        assertEquals("a", lyrics.tracks.single().agents.single().id)
        assertEquals(2, lyrics.tracks.single().cueLines.single().cues.single().byteEnd)
        assertTrue(account.providerConfig.orEmpty().contains("songLyrics"))
        assertTrue(account.providerConfig.orEmpty().contains("checkedAtEpochMs"))
        assertTrue(account.providerConfig.orEmpty().none { it == '\u0000' })
    }

    @Test
    fun playbackAndDownloadReloadConfigAndKeepBitratesIndependent() = runTest {
        var account = navidromeAccount(NavidromeProviderConfigurationCodec.encode(
            NavidromeProviderConfiguration(streamMaxBitRate = 192, downloadMaxBitRate = 320, coverArtSize = 768)
        ))
        val requestUrls = mutableListOf<Pair<String, Map<String, String>>>()
        val gateway = RemoteServerGatewayImpl(
            sourceAccountDao = FakeAccountDao({ account }),
            storageRepository = FakeStorageRepository(),
            subsonicResourceUrl = { _, _, _, endpoint, params ->
                requestUrls += endpoint to params
                "https://server.test/$endpoint?${params.entries.joinToString("&") { "${it.key}=${it.value}" }}"
            },
        )
        val id = RemoteServerPlaybackTarget(SourceAccountId("storage:7"), "opaque/id").encodedPlaybackId()
        gateway.playback(RemoteServerKind.Navidrome, id)
        gateway.download(RemoteServerKind.Navidrome, id)
        assertEquals("stream", requestUrls[0].first)
        assertEquals("192", requestUrls[0].second["maxBitRate"])
        assertEquals("stream", requestUrls[1].first)
        assertEquals("320", requestUrls[1].second["maxBitRate"])

        account = account.copy(providerConfig = NavidromeProviderConfigurationCodec.encode(
            NavidromeProviderConfiguration(streamMaxBitRate = 256, downloadMaxBitRate = 0, coverArtSize = 1024)
        ))
        gateway.playback(RemoteServerKind.Navidrome, id)
        gateway.download(RemoteServerKind.Navidrome, id)
        assertEquals("256", requestUrls[2].second["maxBitRate"])
        assertEquals("download", requestUrls[3].first)
        assertFalse("maxBitRate" in requestUrls[3].second)
        assertTrue(requestUrls.none { it.second.values.any { value -> value == "password" || value == "token" } })
    }

    @Test
    fun openSubsonicPlaybackReadsItsOwnBitrateConfiguration() = runTest {
        val account = navidromeAccount(OpenSubsonicProviderConfigurationCodec.encode(
            OpenSubsonicProviderConfiguration(streamMaxBitRate = 256),
        )).copy(providerType = ProviderTypes.OpenSubsonic)
        var params = emptyMap<String, String>()
        val gateway = RemoteServerGatewayImpl(
            FakeAccountDao({ account }),
            FakeStorageRepository(),
            subsonicResourceUrl = { _, _, _, _, value -> params = value; "https://stream.example/audio" },
        )
        val id = RemoteServerPlaybackTarget(SourceAccountId("storage:7"), "song").encodedPlaybackId()
        assertTrue(gateway.playback(RemoteServerKind.OpenSubsonic, id) is SourcePlaybackResult.Success)
        assertEquals("256", params["maxBitRate"])
    }

    @Test
    fun eachSubsonicPageFallsBackIndependentlyAndUsesThatEndpointForResourceUrls() = runTest {
        val account = navidromeAccount(NavidromeProviderConfigurationCodec.encode(
            NavidromeProviderConfiguration(
                streamMaxBitRate = 192,
                coverArtSize = 768,
                secondaryBaseUrl = "https://secondary.example",
            ),
        ))
        val requests = mutableListOf<Pair<String, String>>()
        val gateway = RemoteServerGatewayImpl(
            FakeAccountDao({ account }),
            FakeStorageRepository(),
            subsonicRequest = { baseUrl, _, _, endpoint, requestParams ->
                val offset = requestParams["songOffset"].orEmpty()
                requests += baseUrl to "$endpoint:$offset"
                if (baseUrl.contains("server.test")) throw RemoteMusicException.Timeout()
                val songs = when (offset) {
                    "0" -> """{"id":"song-0","title":"First","coverArt":"cover-0"}"""
                    "1" -> """{"id":"song-1","title":"Second","coverArt":"cover-1"}"""
                    else -> ""
                }
                """{"subsonic-response":{"status":"ok","searchResult3":{"song":[$songs]}}}"""
            },
            subsonicResourceUrl = { baseUrl, _, _, endpoint, params ->
                "$baseUrl/$endpoint?${params.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }}"
            },
        )

        val tracks = gateway.trackPages(
            RemoteServerKind.Navidrome,
            SourceAccountId("storage:7"),
            query = "all",
            pageSize = 1,
        ).toList().flatMap { it.getOrThrow().tracks }

        assertEquals(
            listOf(
                "https://server.test" to "search3:0",
                "https://secondary.example" to "search3:0",
                "https://server.test" to "search3:1",
                "https://secondary.example" to "search3:1",
                "https://server.test" to "search3:2",
                "https://secondary.example" to "search3:2",
            ),
            requests,
        )
        assertTrue(tracks.all { it.streamUrl.orEmpty().startsWith("https://secondary.example/") })
        assertTrue(tracks.all { it.streamUrl.orEmpty().contains("maxBitRate=192") })
        assertTrue(tracks.all { it.coverUrl.orEmpty().startsWith("https://secondary.example/") })
        assertTrue(tracks.all { it.coverUrl.orEmpty().contains("size=768") })
    }

    @Test
    fun accountScopedSecondaryEndpointsNeverCrossAccounts() = runTest {
        val accounts = mapOf(
            7L to navidromeAccount(NavidromeProviderConfigurationCodec.encode(
                NavidromeProviderConfiguration(secondaryBaseUrl = "https://secondary-a.example"),
            )),
            8L to navidromeAccount(NavidromeProviderConfigurationCodec.encode(
                NavidromeProviderConfiguration(secondaryBaseUrl = "https://secondary-b.example"),
            )).copy(id = 8, credentialRef = "credential-8"),
        )
        val endpoints = mutableListOf<String>()
        val gateway = RemoteServerGatewayImpl(
            MapAccountDao(accounts),
            FakeStorageRepository(),
            subsonicRequest = { baseUrl, _, _, _, _ ->
                endpoints += baseUrl
                if (baseUrl == "https://server.test") throw RemoteMusicException.Connectivity()
                """{"subsonic-response":{"status":"ok","lyrics":{"value":"line"}}}"""
            },
        )
        gateway.lyrics(RemoteServerKind.Navidrome, SourceAccountId("storage:7"), "a", "t").getOrThrow()
        gateway.lyrics(RemoteServerKind.Navidrome, SourceAccountId("storage:8"), "a", "t").getOrThrow()
        assertEquals(
            listOf(
                "https://server.test",
                "https://secondary-a.example",
                "https://server.test",
                "https://secondary-b.example",
            ),
            endpoints,
        )
    }

    @Test
    fun coverArtAndLyricsUseCurrentConfigAndParseSafeResponse() = runTest {
        var response = "{\"subsonic-response\":{\"status\":\"ok\",\"lyrics\":{\"value\":\"[00:01.00]line\"}}}"
        val urls = mutableListOf<Pair<String, Map<String, String>>>()
        val gateway = RemoteServerGatewayImpl(
            FakeAccountDao({ navidromeAccount(NavidromeProviderConfigurationCodec.encode(NavidromeProviderConfiguration(coverArtSize = 1024))) }),
            FakeStorageRepository(),
            subsonicRequest = { _, _, _, _, _ -> response },
            subsonicResourceUrl = { _, _, _, endpoint, params -> urls += endpoint to params; "https://server/$endpoint" },
        )
        gateway.coverArt(RemoteServerKind.Navidrome, SourceAccountId("storage:7"), "cover", 9999)
        gateway.lyrics(RemoteServerKind.Navidrome, SourceAccountId("storage:7"), "artist", "title")
        assertEquals("1024", urls.single().second["size"])
        val lyrics = gateway.lyrics(RemoteServerKind.Navidrome, SourceAccountId("storage:7"), "artist", "title").getOrThrow()
        assertEquals("lrc", lyrics.format)
        assertTrue(lyrics.synchronized)
        response = "not-json"
        assertTrue(gateway.lyrics(RemoteServerKind.Navidrome, SourceAccountId("storage:7"), "a", "t").isFailure)
    }

    @Test
    fun embyPlaybackNegotiatesDirectSourceWithHeadersAndExpiry() = runTest {
        val account = navidromeAccount(null).copy(
            providerType = ProviderTypes.Emby,
            externalAccountId = "user/?:#%",
        )
        var request = ""
        var callbackCalls = 0
        val gateway = RemoteServerGatewayImpl(
            FakeAccountDao({ account }),
            FakeStorageRepository(),
            nowEpochMs = { 1_000L },
            embyPlaybackUrl = { base, item, user, source ->
                "https://emby.example/base/Audio/$item/stream?UserId=$user&MediaSourceId=$source&static=true"
            },
            embyRequest = { _, token, path, params ->
                callbackCalls++
                request = "$token|$path|${params["UserId"]}"
                """{"MediaSources":[{"Id":"source-1","SupportsDirectPlay":true,"SupportsDirectStream":true,"MimeType":"audio/flac","RequiredHttpHeaders":{"X-Trace":"opaque"}}]}"""
            },
        )
        val id = RemoteServerPlaybackTarget(
            SourceAccountId("storage:7"), "opaque/id?#%",
        ).encodedPlaybackId()
        val result = gateway.playback(RemoteServerKind.Emby, id) as SourcePlaybackResult.Success
        assertEquals(1, callbackCalls)
        assertEquals("secret|Items/opaque/id?#%/PlaybackInfo|user/?:#%", request)
        assertEquals("https://emby.example/base/Audio/opaque/id?#%/stream?UserId=user/?:#%&MediaSourceId=source-1&static=true", result.resource.uri)
        assertEquals("secret", result.resource.headers["X-Emby-Token"])
        assertEquals("opaque", result.resource.headers["X-Trace"])
        assertEquals("audio/flac", result.resource.mimeType)
        assertEquals(301_000L, result.resource.expiresAtEpochMs)
        assertTrue("secret" !in result.resource.uri)
        assertTrue("api_key" !in result.resource.uri)
        assertTrue("token" !in result.resource.uri)
    }

    @Test
    fun embyPlaybackInfoFallbackUsesTheSuccessfulEndpointForPlaybackUrl() = runTest {
        val account = navidromeAccount(EmbyProviderConfigurationCodec.encode(
            EmbyProviderConfiguration(secondaryBaseUrl = "https://emby-secondary.example"),
        )).copy(providerType = ProviderTypes.Emby, externalAccountId = "user")
        val requestEndpoints = mutableListOf<String>()
        var playbackEndpoint = ""
        val gateway = RemoteServerGatewayImpl(
            FakeAccountDao({ account }),
            FakeStorageRepository(),
            embyRequest = { endpoint, _, _, _ ->
                requestEndpoints += endpoint
                if (endpoint == "https://server.test") throw RemoteMusicException.Timeout()
                """{"MediaSources":[{"Id":"source","SupportsDirectPlay":true}]}"""
            },
            embyPlaybackUrl = { endpoint, _, _, _ ->
                playbackEndpoint = endpoint
                "$endpoint/Audio/track/stream"
            },
        )
        val id = RemoteServerPlaybackTarget(SourceAccountId("storage:7"), "track").encodedPlaybackId()
        assertTrue(gateway.playback(RemoteServerKind.Emby, id) is SourcePlaybackResult.Success)
        assertEquals(listOf("https://server.test", "https://emby-secondary.example"), requestEndpoints)
        assertEquals("https://emby-secondary.example", playbackEndpoint)
    }

    @Test
    fun embyPlaybackSelectsDirectStreamAndRejectsExactIncompatibleSource() = runTest {
        val account = navidromeAccount(null).copy(providerType = ProviderTypes.Emby, externalAccountId = "user")
        val response = """{"MediaSources":[{"Id":"stream","SupportsDirectStream":true,"Container":"mp3"},{"Id":"play","SupportsDirectPlay":true},{"Id":"bad","SupportsDirectPlay":false,"SupportsDirectStream":false}]}"""
        val gateway = RemoteServerGatewayImpl(
            FakeAccountDao({ account }), FakeStorageRepository(),
            embyRequest = { _, _, _, _ -> response },
            embyPlaybackUrl = { _, _, _, source -> "https://emby/Audio/track/stream?MediaSourceId=$source" },
        )
        val id = RemoteServerPlaybackTarget(SourceAccountId("storage:7"), "track").encodedPlaybackId()
        val directPlay = gateway.playback(RemoteServerKind.Emby, id) as SourcePlaybackResult.Success
        assertTrue(directPlay.resource.uri.contains("MediaSourceId=play"))
        val exact = RemoteServerPlaybackTarget(SourceAccountId("storage:7"), "track", "bad").encodedPlaybackId()
        val rejected = gateway.playback(RemoteServerKind.Emby, exact)
        assertEquals(SourcePlaybackResult.Failure(SourcePlaybackFailureReason.UnsupportedMediaType), rejected)
    }

    @Test
    fun embyPlaybackMapsUnauthorizedAndMalformedMediaSources() = runTest {
        val account = navidromeAccount(null).copy(providerType = ProviderTypes.Emby, externalAccountId = "user")
        var response = "not-json"
        val gateway = RemoteServerGatewayImpl(
            FakeAccountDao({ account }), FakeStorageRepository(),
            embyRequest = { _, _, _, _ ->
                if (response == "unauthorized") throw RemoteMusicException.Unauthorized()
                response
            },
            embyPlaybackUrl = { _, _, _, _ -> "https://emby/stream" },
        )
        val id = RemoteServerPlaybackTarget(SourceAccountId("storage:7"), "track").encodedPlaybackId()
        assertEquals(
            SourcePlaybackResult.Failure(SourcePlaybackFailureReason.UnsupportedMediaType),
            gateway.playback(RemoteServerKind.Emby, id),
        )
        response = """{"MediaSources":{}}"""
        assertEquals(
            SourcePlaybackResult.Failure(SourcePlaybackFailureReason.UnsupportedMediaType),
            gateway.playback(RemoteServerKind.Emby, id),
        )
        response = "unauthorized"
        assertEquals(
            SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unauthorized),
            gateway.playback(RemoteServerKind.Emby, id),
        )
    }

    @Test
    fun embyPlaybackRejectsUnsafeRequiredHeadersAndMissingSources() = runTest {
        val account = navidromeAccount(null).copy(providerType = ProviderTypes.Emby, externalAccountId = "user")
        val id = RemoteServerPlaybackTarget(SourceAccountId("storage:7"), "track").encodedPlaybackId()
        val responses = listOf(
            "{\"MediaSources\":[{\"Id\":\"s\",\"SupportsDirectPlay\":true,\"RequiredHttpHeaders\":{\"X-Test\":1}}]}",
            "{\"MediaSources\":[{\"Id\":\"s\",\"SupportsDirectPlay\":true,\"RequiredHttpHeaders\":{\"X-Test\":true}}]}",
            "{\"MediaSources\":[{\"Id\":\"s\",\"SupportsDirectPlay\":true,\"RequiredHttpHeaders\":{\"X-Ü\":\"ok\"}}]}",
            "{\"MediaSources\":[{\"Id\":\"s\",\"SupportsDirectPlay\":true,\"RequiredHttpHeaders\":{\"X-Test\":\"bad\\r\\nvalue\"}}]}",
            "{\"MediaSources\":[{\"Id\":\"s\",\"SupportsDirectPlay\":true,\"RequiredHttpHeaders\":{\"Range\":\"bytes=0-1\"}}]}",
            "{\"MediaSources\":[{\"Id\":\"s\",\"SupportsDirectPlay\":true,\"RequiredHttpHeaders\":{\"x-emby-token\":\"other\"}}]}",
            "{\"MediaSources\":[]}",
            "{\"MediaSources\":{}}",
            "{}",
        )
        responses.forEach { response ->
            val gateway = RemoteServerGatewayImpl(
                FakeAccountDao({ account }),
                FakeStorageRepository(),
                embyRequest = { _, _, _, _ -> response },
                embyPlaybackUrl = { _, _, _, _ -> "https://emby/stream" },
            )
            assertEquals(
                SourcePlaybackResult.Failure(SourcePlaybackFailureReason.UnsupportedMediaType),
                gateway.playback(RemoteServerKind.Emby, id),
                response,
            )
        }
    }
}

private fun navidromeAccount(config: String?) = SourceAccountEntity(
    id = 7, providerType = ProviderTypes.Navidrome, displayName = "Navidrome",
    endpoint = "https://server.test", externalAccountId = null, credentialRef = "credential-7",
    priority = 0, enabled = true, createdAt = 1, updatedAt = 1, providerConfig = config,
)

private class FakeAccountDao(
    private val value: () -> SourceAccountEntity,
    private val onUpsert: (SourceAccountEntity) -> Unit = {},
) : SourceAccountDao {
    override fun observeAll(): Flow<List<SourceAccountEntity>> = emptyFlow()
    override fun observeSummaries(): Flow<List<SourceAccountSummaryRow>> = emptyFlow()
    override suspend fun get(id: Long) = value().takeIf { it.id == id }
    override suspend fun maxId() = 7L
    override suspend fun listAll() = listOf(value())
    override suspend fun upsert(account: SourceAccountEntity): Long {
        onUpsert(account)
        return account.id
    }
    override suspend fun setEnabledByProviderType(providerType: String, enabled: Boolean, updatedAt: Long) = Unit
    override suspend fun setEnabled(id: Long, enabled: Boolean, updatedAt: Long) = Unit
    override suspend fun disableRemoteSources(updatedAt: Long) = Unit
    override suspend fun setRootPath(id: Long, rootPath: String, updatedAt: Long) = Unit
    override suspend fun delete(id: Long) = Unit
}

private class MapAccountDao(
    private val accounts: Map<Long, SourceAccountEntity>,
) : SourceAccountDao {
    override fun observeAll(): Flow<List<SourceAccountEntity>> = emptyFlow()
    override fun observeSummaries(): Flow<List<SourceAccountSummaryRow>> = emptyFlow()
    override suspend fun get(id: Long) = accounts[id]
    override suspend fun maxId() = accounts.keys.maxOrNull()
    override suspend fun listAll() = accounts.values.toList()
    override suspend fun upsert(account: SourceAccountEntity) = error("upsert is not expected")
    override suspend fun setEnabledByProviderType(providerType: String, enabled: Boolean, updatedAt: Long) = Unit
    override suspend fun setEnabled(id: Long, enabled: Boolean, updatedAt: Long) = Unit
    override suspend fun disableRemoteSources(updatedAt: Long) = Unit
    override suspend fun setRootPath(id: Long, rootPath: String, updatedAt: Long) = Unit
    override suspend fun delete(id: Long) = Unit
}

private class FakeStorageRepository : StorageRepository {
    var credentialLookups: Int = 0
    override val storageAccounts = MutableStateFlow<List<StorageAccountInfo>>(emptyList())
    override val onRemoveStorageEvent = MutableSharedFlow<Unit>()
    override val oauthRefreshToken = MutableStateFlow("")
    override suspend fun reload() = Unit
    override suspend fun startOneDriveOAuth() = ""
    override suspend fun upsertSource(draft: SourceEditorDraft) = SourceAccountId("storage:7")
    override suspend fun loadEditorState(id: Long): SourceEditorStorageState? = null
    override suspend fun testSource(draft: SourceEditorDraft) = SourceConnectionTestStatus.Unavailable
    override suspend fun listOneDriveDriveInfos(refreshToken: String) = OneDriveDriveListResult(emptyList(), "")
    override suspend fun updateOneDriveRefreshTokenByAccountId(accountId: SourceAccountId, refreshToken: String) = Unit
    override fun findStorageAccountByAccountId(accountId: SourceAccountId): StorageAccountInfo? = null
    override suspend fun loadCredentialByAccountId(accountId: SourceAccountId): StoredCredential? {
        credentialLookups++
        return StoredCredential("user", "secret", false)
    }
    override suspend fun setAccountRootPath(accountId: SourceAccountId, rootPath: String) = Unit
    override suspend fun removeByAccountId(accountId: SourceAccountId) = Unit
}
