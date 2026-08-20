package io.github.julystar.musicapp.source.storage

import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.MediaType
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.source.api.BuiltInSourceIds
import io.github.julystar.musicapp.source.api.LegacyStorageConnectionRequest
import io.github.julystar.musicapp.source.api.LegacyStorageConnectionTester
import io.github.julystar.musicapp.source.api.LegacyStorageDirectoryLister
import io.github.julystar.musicapp.source.api.LegacyStorageKind
import io.github.julystar.musicapp.source.api.LegacyStoragePlaybackResolver
import io.github.julystar.musicapp.source.api.LegacyStorageSearchProvider
import io.github.julystar.musicapp.source.api.LocalSourceConfiguration
import io.github.julystar.musicapp.source.api.MusicSource
import io.github.julystar.musicapp.source.api.OneDriveSourceConfiguration
import io.github.julystar.musicapp.source.api.PlaybackResource
import io.github.julystar.musicapp.source.api.SourceAuthFailureReason
import io.github.julystar.musicapp.source.api.SourceAuthResult
import io.github.julystar.musicapp.source.api.SourceCapability
import io.github.julystar.musicapp.source.api.SourceListFailureReason
import io.github.julystar.musicapp.source.api.SourceListResult
import io.github.julystar.musicapp.source.api.SourceNodeType
import io.github.julystar.musicapp.source.api.SourcePlaybackFailureReason
import io.github.julystar.musicapp.source.api.SourcePlaybackResult
import io.github.julystar.musicapp.source.api.SourceSearchResult
import io.github.julystar.musicapp.source.api.WebDavSourceConfiguration
import io.github.julystar.musicapp.source.api.legacyStorageTrackMediaId
import io.github.julystar.musicapp.source.local.LocalMusicSource
import io.github.julystar.musicapp.source.onedrive.OneDriveMusicSource
import io.github.julystar.musicapp.source.smb.SmbMusicSource
import io.github.julystar.musicapp.source.webdav.WebDavMusicSource
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import uniffi.app_backend.ListStorageEntryChildrenResp
import uniffi.app_backend.Storage
import uniffi.app_backend.StorageEntry
import uniffi.app_backend.StorageId
import uniffi.app_backend.StorageType

class LegacyStorageMusicSourceTest {
    @Test
    fun webDavAuthenticationUsesLegacyStorageConnectionTest() = runBlocking {
        var captured: LegacyStorageConnectionRequest? = null
        val source = WebDavMusicSource(
            connectionTester = LegacyStorageConnectionTester { request ->
                captured = request
                SourceAuthResult.Success
            },
            directoryLister = unusedDirectoryLister(),
            playbackResolver = unusedPlaybackResolver(),
        )

        val result = source.authenticate(
            WebDavSourceConfiguration(
                accountId = SourceAccountId("account-webdav"),
                alias = "NAS",
                address = "https://dav.example.com/music",
                username = "alice",
                password = "secret-password",
                isAnonymous = false,
            )
        )

        assertEquals(SourceAuthResult.Success, result)
        val request = assertNotNull(captured)
        assertEquals("https://dav.example.com/music", request.address)
        assertEquals("NAS", request.alias)
        assertEquals("alice", request.username)
        assertEquals("secret-password", request.password)
        assertFalse(request.isAnonymous)
        assertEquals(LegacyStorageKind.WebDav, request.kind)
    }

    @Test
    fun oneDriveAuthenticationMapsLegacyFailure() = runBlocking {
        var captured: LegacyStorageConnectionRequest? = null
        val source = OneDriveMusicSource(
            connectionTester = LegacyStorageConnectionTester { request ->
                captured = request
                SourceAuthResult.Failure(SourceAuthFailureReason.Unauthorized)
            },
            directoryLister = unusedDirectoryLister(),
            playbackResolver = unusedPlaybackResolver(),
        )

        val result = source.authenticate(
            OneDriveSourceConfiguration(
                accountId = SourceAccountId("account-onedrive"),
                alias = "OneDrive",
                driveId = "drive-123",
                refreshToken = "refresh-token",
            )
        )

        assertEquals(
            SourceAuthResult.Failure(SourceAuthFailureReason.Unauthorized),
            result,
        )
        val request = assertNotNull(captured)
        assertEquals("drive-123", request.address)
        assertEquals("OneDrive", request.alias)
        assertEquals("", request.username)
        assertEquals("refresh-token", request.password)
        assertFalse(request.isAnonymous)
        assertEquals(LegacyStorageKind.OneDrive, request.kind)
    }

    @Test
    fun localAuthenticationAcceptsOnlyLocalConfiguration() = runBlocking {
        val source = LocalMusicSource(unusedDirectoryLister(), unusedPlaybackResolver())

        assertEquals(
            SourceAuthResult.Success,
            source.authenticate(LocalSourceConfiguration(alias = "Device")),
        )
        assertEquals(
            SourceAuthResult.Failure(SourceAuthFailureReason.UnsupportedConfiguration),
            source.authenticate(
                WebDavSourceConfiguration(
                    alias = "NAS",
                    address = "https://dav.example.com/music",
                    username = "alice",
                    password = "secret-password",
                    isAnonymous = false,
                )
            ),
        )
    }

    @Test
    fun sourceConfigurationStringDoesNotExposeSecrets() {
        val webDavConfiguration = WebDavSourceConfiguration(
            alias = "NAS",
            address = "https://dav.example.com/music",
            username = "alice",
            password = "secret-password",
            isAnonymous = false,
        )
        val oneDriveConfiguration = OneDriveSourceConfiguration(
            alias = "OneDrive",
            driveId = "drive-123",
            refreshToken = "refresh-token",
        )

        assertFalse(webDavConfiguration.toString().contains("secret-password"))
        assertFalse(oneDriveConfiguration.toString().contains("refresh-token"))
    }

    @Test
    fun musicSourcesListWithExpectedLegacyStorageTypes() = runBlocking {
        val accountId = SourceAccountId("storage:42")
        val captured = mutableListOf<LegacyStorageKind>()
        val directoryLister = LegacyStorageDirectoryLister { _, _, expectedKind ->
            captured += expectedKind
            SourceListResult.Success(emptyList())
        }

        LocalMusicSource(directoryLister, unusedPlaybackResolver()).list(accountId, "/")
        WebDavMusicSource(
            unusedConnectionTester(),
            directoryLister,
            unusedPlaybackResolver(),
        ).list(accountId, "/Music")
        OneDriveMusicSource(
            unusedConnectionTester(),
            directoryLister,
            unusedPlaybackResolver(),
        ).list(accountId, "Albums")
        SmbMusicSource(
            unusedConnectionTester(),
            directoryLister,
            unusedPlaybackResolver(),
        ).list(accountId, "/Share")

        assertEquals(
            listOf(
                LegacyStorageKind.Local,
                LegacyStorageKind.WebDav,
                LegacyStorageKind.OneDrive,
                LegacyStorageKind.Smb,
            ),
            captured,
        )
    }

    @Test
    fun builtInStorageSourcesAdvertiseSearchAndDelegateToLegacySearchProvider() = runBlocking {
        val accountId = SourceAccountId("storage:42")
        val captured = mutableListOf<Pair<LegacyStorageKind, String>>()
        val searchProvider = LegacyStorageSearchProvider { _, query, limit, expectedKind, sourceId ->
            assertEquals("moon", query)
            assertEquals(5, limit)
            captured += expectedKind to sourceId.value
            SourceSearchResult.Success(emptyList())
        }
        val sources: List<MusicSource> = listOf(
            LocalMusicSource(
                unusedDirectoryLister(),
                unusedPlaybackResolver(),
                searchProvider,
            ),
            WebDavMusicSource(
                unusedConnectionTester(),
                unusedDirectoryLister(),
                unusedPlaybackResolver(),
                searchProvider,
            ),
            OneDriveMusicSource(
                unusedConnectionTester(),
                unusedDirectoryLister(),
                unusedPlaybackResolver(),
                searchProvider,
            ),
            SmbMusicSource(
                unusedConnectionTester(),
                unusedDirectoryLister(),
                unusedPlaybackResolver(),
                searchProvider,
            ),
        )

        sources.forEach { source ->
            assertTrue(SourceCapability.Search in source.capabilities)
            assertEquals(
                SourceSearchResult.Success(emptyList()),
                source.search(accountId, query = "moon", limit = 5),
            )
        }
        assertEquals(
            listOf(
                LegacyStorageKind.Local to BuiltInSourceIds.Local.value,
                LegacyStorageKind.WebDav to BuiltInSourceIds.WebDav.value,
                LegacyStorageKind.OneDrive to BuiltInSourceIds.OneDrive.value,
                LegacyStorageKind.Smb to BuiltInSourceIds.Smb.value,
            ),
            captured,
        )
    }

    @Test
    fun musicSourcesResolvePlaybackWithExpectedLegacyStorageTypes() = runBlocking {
        val accountId = SourceAccountId("storage:42")
        val captured = mutableListOf<Triple<SourceAccountId, String, LegacyStorageKind>>()
        val playbackResolver = object : LegacyStoragePlaybackResolver {
            override suspend fun resolve(
                accountId: SourceAccountId,
                path: String,
                expectedStorageKind: LegacyStorageKind,
            ): SourcePlaybackResult {
                captured += Triple(accountId, path, expectedStorageKind)
                return SourcePlaybackResult.Success(
                    PlaybackResource(
                        uri = "http://127.0.0.1/${expectedStorageKind.name}",
                        isLocal = expectedStorageKind == LegacyStorageKind.Local,
                    )
                )
            }

            override suspend fun release(uri: String) = Unit

            override suspend fun releaseAll() = Unit
        }

        val localResult = LocalMusicSource(
            unusedDirectoryLister(),
            playbackResolver,
        ).resolvePlayback(
            legacyStorageTrackMediaId(BuiltInSourceIds.Local, accountId, "/Local.flac")
        )
        WebDavMusicSource(
            unusedConnectionTester(),
            unusedDirectoryLister(),
            playbackResolver,
        ).resolvePlayback(
            legacyStorageTrackMediaId(BuiltInSourceIds.WebDav, accountId, "/Music/WebDAV.mp3")
        )
        OneDriveMusicSource(
            unusedConnectionTester(),
            unusedDirectoryLister(),
            playbackResolver,
        ).resolvePlayback(
            legacyStorageTrackMediaId(BuiltInSourceIds.OneDrive, accountId, "/Cloud/OneDrive.m4a")
        )
        SmbMusicSource(
            unusedConnectionTester(),
            unusedDirectoryLister(),
            playbackResolver,
        ).resolvePlayback(
            legacyStorageTrackMediaId(BuiltInSourceIds.Smb, accountId, "/NAS/SMB.flac")
        )

        assertEquals(
            listOf(
                Triple(accountId, "/Local.flac", LegacyStorageKind.Local),
                Triple(accountId, "/Music/WebDAV.mp3", LegacyStorageKind.WebDav),
                Triple(accountId, "/Cloud/OneDrive.m4a", LegacyStorageKind.OneDrive),
                Triple(accountId, "/NAS/SMB.flac", LegacyStorageKind.Smb),
            ),
            captured,
        )
        assertTrue((localResult as SourcePlaybackResult.Success).resource.isLocal)
    }

    @Test
    fun musicSourcesRejectUnsupportedPlaybackMediaIdsWithoutCallingResolver() = runBlocking {
        var resolveCalls = 0
        val playbackResolver = object : LegacyStoragePlaybackResolver {
            override suspend fun resolve(
                accountId: SourceAccountId,
                path: String,
                expectedStorageKind: LegacyStorageKind,
            ): SourcePlaybackResult {
                resolveCalls += 1
                return SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unavailable)
            }

            override suspend fun release(uri: String) = Unit

            override suspend fun releaseAll() = Unit
        }
        val source = WebDavMusicSource(
            unusedConnectionTester(),
            unusedDirectoryLister(),
            playbackResolver,
        )

        assertEquals(
            SourcePlaybackResult.Failure(SourcePlaybackFailureReason.UnsupportedMediaId),
            source.resolvePlayback(
                MediaId(
                    sourceId = BuiltInSourceIds.OneDrive,
                    mediaType = MediaType.Track,
                    remoteId = "legacy-storage-track:storage%3A42:%2FTrack.mp3",
                )
            ),
        )
        assertEquals(
            SourcePlaybackResult.Failure(SourcePlaybackFailureReason.UnsupportedMediaType),
            source.resolvePlayback(
                MediaId(
                    sourceId = BuiltInSourceIds.WebDav,
                    mediaType = MediaType.Album,
                    remoteId = "legacy-storage-track:storage%3A42:%2FTrack.mp3",
                )
            ),
        )
        assertEquals(0, resolveCalls)
    }

    @Test
    fun retainedLegacyPlaybackResolverKeepsSessionUntilReleased() = runBlocking {
        val session = FakeLegacyPlaybackSession("http://127.0.0.1:1234/stream.flac")
        val resolver = RetainedLegacyStoragePlaybackResolver(
            storageLookup = LegacyStorageLookup { storageId ->
                if (storageId == StorageId(42)) {
                    storage(id = 42, typ = StorageType.WEBDAV)
                } else {
                    null
                }
            },
            sessionFactory = LegacyPlaybackSessionFactory { storage, path ->
                assertEquals(StorageId(42), storage.id)
                assertEquals("/Music/Track.flac", path)
                session
            },
        )

        val result = resolver.resolve(
            accountId = SourceAccountId("storage:42"),
            path = "/Music/Track.flac",
            expectedStorageKind = LegacyStorageKind.WebDav,
        )
        val resource = (result as SourcePlaybackResult.Success).resource

        assertEquals("http://127.0.0.1:1234/stream.flac", resource.uri)
        assertEquals("audio/flac", resource.mimeType)
        assertFalse(resource.isLocal)
        assertEquals(0, session.shutdownCalls)

        resolver.release(resource.uri)

        assertEquals(1, session.shutdownCalls)
    }

    @Test
    fun openListPlaybackUsesSpecializedLoopbackNativeMimeAndOwnsSessionLifetime() = runBlocking {
        var storageLookups = 0
        var genericCreates = 0
        val created = mutableListOf<FakeLegacyPlaybackSession>()
        val accountId = SourceAccountId("openlist:41")
        val rawPath = "/音乐/type3 %25 #? \\"
        val resolver = RetainedLegacyStoragePlaybackResolver(
            storageLookup = LegacyStorageLookup {
                storageLookups += 1
                null
            },
            sessionFactory = LegacyPlaybackSessionFactory { _, _ ->
                genericCreates += 1
                null
            },
            openListPlaybackSessionFactory = OpenListPlaybackSessionFactory { receivedAccount, path ->
                assertEquals(accountId, receivedAccount)
                assertEquals(rawPath, path)
                FakeLegacyPlaybackSession(
                    url = "http://127.0.0.1:${1234 + created.size}/stream.bin",
                    mimeType = "audio/flac",
                ).also(created::add)
            },
        )

        val first = resolver.resolve(accountId, rawPath, LegacyStorageKind.OpenList)
        val firstResource = (first as SourcePlaybackResult.Success).resource
        assertEquals("http://127.0.0.1:1234/stream.bin", firstResource.uri)
        assertEquals("audio/flac", firstResource.mimeType)
        assertFalse(firstResource.isLocal)
        assertFalse(firstResource.uri.contains("openlist.example"))
        assertEquals(0, storageLookups)
        assertEquals(0, genericCreates)
        assertEquals(0, created.single().shutdownCalls)

        resolver.release(firstResource.uri)
        assertEquals(1, created.single().shutdownCalls)

        val second = resolver.resolve(accountId, rawPath, LegacyStorageKind.OpenList)
        val secondResource = (second as SourcePlaybackResult.Success).resource
        assertEquals("audio/flac", secondResource.mimeType)
        resolver.releaseAll()
        assertEquals(1, created[1].shutdownCalls)
        resolver.release(secondResource.uri)
        assertEquals(1, created[1].shutdownCalls)
    }

    @Test
    fun openListPlaybackMapsReauthenticationWithoutAffectingLegacyFactory() = runBlocking {
        var genericCreates = 0
        val resolver = RetainedLegacyStoragePlaybackResolver(
            storageLookup = LegacyStorageLookup { storage(id = 42, typ = StorageType.WEBDAV) },
            sessionFactory = LegacyPlaybackSessionFactory { _, _ ->
                genericCreates += 1
                FakeLegacyPlaybackSession("http://127.0.0.1:1234/legacy.mp3")
            },
            openListPlaybackSessionFactory = OpenListPlaybackSessionFactory { _, _ ->
                throw io.github.julystar.musicapp.core.domain.model.NeedsReauthenticationException()
            },
        )

        assertEquals(
            SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unauthorized),
            resolver.resolve(
                SourceAccountId("openlist:41"),
                "/track",
                LegacyStorageKind.OpenList,
            ),
        )
        assertEquals(0, genericCreates)
        val legacy = resolver.resolve(
            SourceAccountId("storage:42"),
            "/legacy.mp3",
            LegacyStorageKind.WebDav,
        )
        assertTrue(legacy is SourcePlaybackResult.Success)
        assertEquals(1, genericCreates)
        resolver.releaseAll()
    }

    @Test
    fun openListPlaybackMapsTypedTransportFailures() = runBlocking {
        for ((reason, expected) in listOf(
            io.github.julystar.musicapp.core.data.OpenListAuthTransportFailureReason.PermissionDenied to
                SourcePlaybackFailureReason.Unauthorized,
            io.github.julystar.musicapp.core.data.OpenListAuthTransportFailureReason.RateLimited to
                SourcePlaybackFailureReason.Unavailable,
        )) {
            val resolver = RetainedLegacyStoragePlaybackResolver(
                storageLookup = LegacyStorageLookup { null },
                sessionFactory = LegacyPlaybackSessionFactory { _, _ -> null },
                openListPlaybackSessionFactory = OpenListPlaybackSessionFactory { _, _ ->
                    throw io.github.julystar.musicapp.core.data.OpenListAuthTransportException(reason)
                },
            )
            assertEquals(
                SourcePlaybackResult.Failure(expected),
                resolver.resolve(
                    SourceAccountId("openlist:41"),
                    "/track",
                    LegacyStorageKind.OpenList,
                ),
            )
        }
    }

    @Test
    fun retainedLegacyPlaybackResolverRejectsWrongStorageTypeBeforeCreatingSession() = runBlocking {
        var createCalls = 0
        val resolver = RetainedLegacyStoragePlaybackResolver(
            storageLookup = LegacyStorageLookup {
                storage(id = 42, typ = StorageType.LOCAL)
            },
            sessionFactory = LegacyPlaybackSessionFactory { _, _ ->
                createCalls += 1
                FakeLegacyPlaybackSession("http://127.0.0.1:1234/stream.mp3")
            },
        )

        assertEquals(
            SourcePlaybackResult.Failure(SourcePlaybackFailureReason.UnsupportedAccount),
            resolver.resolve(
                accountId = SourceAccountId("storage:42"),
                path = "/Music/Track.mp3",
                expectedStorageKind = LegacyStorageKind.WebDav,
            ),
        )
        assertEquals(0, createCalls)
    }

    @Test
    fun legacyDirectoryResponseMapsToSourceNodes() {
        val accountId = SourceAccountId("storage:42")

        val result = ListStorageEntryChildrenResp.Ok(
            listOf(
                storageEntry(
                    name = "Albums",
                    path = "/Albums",
                    isDir = true,
                    remoteId = "folder-1",
                    parentRemoteId = "root",
                    ctag = "folder-ctag",
                ),
                storageEntry(
                    name = "Song.FLAC",
                    path = "/Albums/Song.FLAC",
                    isDir = false,
                    size = 123u,
                    remoteId = "track-1",
                    parentRemoteId = "folder-1",
                    mimeType = "audio/flac",
                    etag = "track-etag",
                    modifiedAt = 1_000,
                ),
                storageEntry(
                    name = "WebDAV.mp3",
                    path = "/Albums/WebDAV.mp3",
                    isDir = false,
                ),
            )
        ).toSourceListResult(accountId)

        val nodes = (result as SourceListResult.Success).nodes
        assertEquals(3, nodes.size)
        assertEquals(SourceNodeType.Folder, nodes[0].type)
        assertEquals("folder-1", nodes[0].nodeId)
        assertEquals("folder-1", nodes[0].remoteId)
        assertEquals("root", nodes[0].parentNodeId)
        assertEquals("folder-ctag", nodes[0].ctag)
        assertEquals(SourceNodeType.Track, nodes[1].type)
        assertEquals("track-1", nodes[1].nodeId)
        assertEquals("track-1", nodes[1].remoteId)
        assertEquals("audio/flac", nodes[1].mimeType)
        assertEquals("track-etag", nodes[1].etag)
        assertEquals(1_000, nodes[1].modifiedAtEpochMs)
        assertEquals("/Albums/WebDAV.mp3", nodes[2].nodeId)
        assertNull(nodes[2].remoteId)
    }

    @Test
    fun legacyDirectoryFailuresMapToSourceListFailures() {
        assertEquals(
            SourceListResult.Failure(SourceListFailureReason.Unauthorized),
            ListStorageEntryChildrenResp.AuthenticationFailed.toSourceListResult(
                SourceAccountId("storage:1")
            ),
        )
        assertEquals(
            SourceListResult.Failure(SourceListFailureReason.Timeout),
            ListStorageEntryChildrenResp.Timeout.toSourceListResult(SourceAccountId("storage:1")),
        )
        assertEquals(
            SourceListResult.Failure(SourceListFailureReason.Unknown),
            ListStorageEntryChildrenResp.Unknown.toSourceListResult(SourceAccountId("storage:1")),
        )
    }

    private fun unusedConnectionTester() = LegacyStorageConnectionTester {
        SourceAuthResult.Failure(SourceAuthFailureReason.Unknown)
    }

    private fun unusedDirectoryLister() = LegacyStorageDirectoryLister { _, _, _ ->
        SourceListResult.Failure(SourceListFailureReason.Unavailable)
    }

    private fun unusedPlaybackResolver() = object : LegacyStoragePlaybackResolver {
        override suspend fun resolve(
            accountId: SourceAccountId,
            path: String,
            expectedStorageKind: LegacyStorageKind,
        ): SourcePlaybackResult {
            return SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unavailable)
        }

        override suspend fun release(uri: String) = Unit

        override suspend fun releaseAll() = Unit
    }

    private fun storage(
        id: Long,
        typ: StorageType,
    ) = Storage(
        id = StorageId(id),
        addr = "",
        alias = "Storage",
        username = "",
        password = "",
        isAnonymous = true,
        typ = typ,
        musicCount = 0u,
    )

    private class FakeLegacyPlaybackSession(
        override val url: String,
        override val mimeType: String? = null,
    ) : LegacyPlaybackSession {
        var shutdownCalls = 0
            private set

        override fun shutdown() {
            shutdownCalls += 1
        }
    }

    private fun storageEntry(
        name: String,
        path: String,
        isDir: Boolean,
        size: ULong? = null,
        remoteId: String? = null,
        parentRemoteId: String? = null,
        mimeType: String? = null,
        etag: String? = null,
        ctag: String? = null,
        createdAt: Long? = null,
        modifiedAt: Long? = null,
    ) = StorageEntry(
        storageId = uniffi.app_backend.StorageId(42),
        name = name,
        path = path,
        size = size,
        isDir = isDir,
        remoteId = remoteId,
        parentRemoteId = parentRemoteId,
        mimeType = mimeType,
        etag = etag,
        ctag = ctag,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
    )
}
