package io.github.julystar.musicapp.service.playback.data

import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.MediaType
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.SourceId
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.MetadataRefreshCandidate
import io.github.julystar.musicapp.database.SourceAccountEntity
import io.github.julystar.musicapp.database.SourceItemEntity
import io.github.julystar.musicapp.database.SourceItemPropertyEntity
import io.github.julystar.musicapp.database.SourceItemTypes
import io.github.julystar.musicapp.database.TrackSourcePlaybackCandidate
import io.github.julystar.musicapp.database.TrackSourceRefDao
import io.github.julystar.musicapp.database.TrackSourceRefEntity
import io.github.julystar.musicapp.source.api.BuiltInSourceIds
import io.github.julystar.musicapp.source.api.MusicSource
import io.github.julystar.musicapp.source.api.MusicSourceDescriptor
import io.github.julystar.musicapp.source.api.MusicSourceRegistry
import io.github.julystar.musicapp.source.api.PlaybackResource
import io.github.julystar.musicapp.source.api.RemoteServerPlaybackTarget
import io.github.julystar.musicapp.source.api.encodedPlaybackId
import io.github.julystar.musicapp.source.api.decodeRemoteServerPlaybackTarget
import io.github.julystar.musicapp.source.api.SourceAuthResult
import io.github.julystar.musicapp.source.api.SourceCapability
import io.github.julystar.musicapp.source.api.SourceConfiguration
import io.github.julystar.musicapp.source.api.SourceListResult
import io.github.julystar.musicapp.source.api.SourcePlaybackFailureReason
import io.github.julystar.musicapp.source.api.SourcePlaybackResult
import io.github.julystar.musicapp.source.api.LegacyStorageKind
import io.github.julystar.musicapp.source.api.LegacyStoragePlaybackResolver
import io.github.julystar.musicapp.source.api.legacyStorageTrackMediaId
import io.github.julystar.musicapp.source.api.toLegacyStoragePlaybackTarget
import io.github.julystar.musicapp.source.storage.LegacyStorageLookup
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import uniffi.app_backend.Music
import uniffi.app_backend.MusicId
import uniffi.app_backend.MusicMeta
import uniffi.app_backend.Storage
import uniffi.app_backend.StorageEntryLoc
import uniffi.app_backend.StorageId
import uniffi.app_backend.StorageType

class PlaybackResourceResolverTest {
    @Test
    fun unknownPersistedProviderFailsClosedWithoutCallingWebDav() = runBlocking {
        var webDavCalls = 0
        val resolver = PlaybackResourceResolver(
            storageLookup = LegacyStorageLookup { null },
            trackSourceRefDao = fakeTrackSourceRefDao(
                candidate(
                    path = "/must-not-route.flac",
                    providerType = "corrupt-provider",
                )
            ),
            sourceRegistry = MusicSourceRegistry(
                listOf(
                    fakeMusicSource(BuiltInSourceIds.WebDav) {
                        webDavCalls += 1
                        SourcePlaybackResult.Success(PlaybackResource(uri = "http://127.0.0.1/wrong"))
                    }
                )
            ),
            legacyStoragePlaybackResolver = unusedPlaybackResolver(),
            sourceItemPropertyReader = SourceItemPropertyReader.Empty,
        )

        assertEquals(
            SourcePlaybackResult.Failure(SourcePlaybackFailureReason.UnsupportedAccount),
            resolver.resolve(music(storageId = 42, path = "/legacy/ignored.flac")),
        )
        assertEquals(0, webDavCalls)
    }

    @Test
    fun sevenRemoteProvidersRoutePersistedCandidatesThroughTheRegisteredSource() = runBlocking {
        val cases = listOf(
            ProviderPlaybackCase(ProviderTypes.WebDav, BuiltInSourceIds.WebDav, false),
            ProviderPlaybackCase(ProviderTypes.OneDrive, BuiltInSourceIds.OneDrive, false),
            ProviderPlaybackCase(ProviderTypes.Smb, BuiltInSourceIds.Smb, false),
            ProviderPlaybackCase(ProviderTypes.OpenList, BuiltInSourceIds.OpenList, false),
            ProviderPlaybackCase(ProviderTypes.Navidrome, BuiltInSourceIds.Navidrome, true),
            ProviderPlaybackCase(ProviderTypes.OpenSubsonic, BuiltInSourceIds.OpenSubsonic, true),
            ProviderPlaybackCase(ProviderTypes.Emby, BuiltInSourceIds.Emby, true),
        )

        cases.forEachIndexed { index, playbackCase ->
            val accountId = 700L + index
            val opaqueIdentity = "opaque/${playbackCase.providerType}:?#% $index"
            val expectedResource = PlaybackResource(uri = "http://127.0.0.1/resolved-$index.flac")
            val calls = mutableListOf<SourceId>()
            var capturedMediaId: MediaId? = null
            val sources = cases.map { registeredCase ->
                fakeMusicSource(registeredCase.sourceId) { mediaId ->
                    calls += registeredCase.sourceId
                    capturedMediaId = mediaId
                    SourcePlaybackResult.Success(expectedResource)
                }
            }
            val resolver = PlaybackResourceResolver(
                storageLookup = LegacyStorageLookup { null },
                trackSourceRefDao = fakeTrackSourceRefDao(
                    candidate(
                        path = opaqueIdentity.takeUnless { playbackCase.isServer },
                        providerType = playbackCase.providerType,
                        sourceAccountId = accountId,
                        sourceItemId = 800L + index,
                        providerItemId = opaqueIdentity,
                    )
                ),
                sourceRegistry = MusicSourceRegistry(sources),
                legacyStoragePlaybackResolver = unusedPlaybackResolver(),
                sourceItemPropertyReader = SourceItemPropertyReader.Empty,
            )

            assertEquals(
                SourcePlaybackResult.Success(expectedResource),
                resolver.resolve(music(storageId = accountId, path = "/legacy/ignored.flac")),
            )
            assertEquals(listOf(playbackCase.sourceId), calls)
            val mediaId = capturedMediaId ?: error("registered source was not called")
            assertEquals(playbackCase.sourceId, mediaId.sourceId)
            if (playbackCase.isServer) {
                assertTrue(mediaId.remoteId.startsWith("v2:"))
                assertEquals(
                    RemoteServerPlaybackTarget(
                        accountId = SourceAccountId("storage:$accountId"),
                        remoteId = opaqueIdentity,
                    ),
                    mediaId.remoteId.decodeRemoteServerPlaybackTarget(),
                )
            } else {
                assertEquals(
                    SourceAccountId("storage:$accountId"),
                    mediaId.toLegacyStoragePlaybackTarget()?.accountId,
                )
                assertEquals(opaqueIdentity, mediaId.toLegacyStoragePlaybackTarget()?.path)
            }
        }
    }

    @Test
    fun embySourceMediaIdIsReadOnceAndSharedByTargetAndCacheIdentity() = runBlocking {
        var propertyReads = 0
        var capturedMediaId: MediaId? = null
        val source = fakeMusicSource(BuiltInSourceIds.Emby) { mediaId ->
            capturedMediaId = mediaId
            SourcePlaybackResult.Success(PlaybackResource(uri = "https://emby.invalid/audio"))
        }
        val resolver = PlaybackResourceResolver(
            storageLookup = LegacyStorageLookup { null },
            trackSourceRefDao = fakeTrackSourceRefDao(
                candidate(
                    path = null,
                    providerType = ProviderTypes.Emby,
                    sourceItemId = 321,
                )
            ),
            sourceRegistry = MusicSourceRegistry(listOf(source)),
            legacyStoragePlaybackResolver = unusedPlaybackResolver(),
            sourceItemPropertyReader = SourceItemPropertyReader { itemId ->
                propertyReads += 1
                assertEquals(321L, itemId)
                listOf(
                    SourceItemPropertyEntity(
                        sourceItemId = itemId,
                        propertyKey = "sourceMediaId",
                        stringValue = " media:/?#% ",
                        longValue = null,
                        doubleValue = null,
                        booleanValue = null,
                    )
                )
            },
            playbackAudioCache = fakePlaybackAudioCache(
                onWrapRemote = { identity, resource ->
                    assertEquals("media:media:/?#%", identity.version)
                    resource
                }
            ),
        )

        assertTrue(resolver.resolve(music(42, "/ignored.flac")) is SourcePlaybackResult.Success)
        assertEquals(1, propertyReads)
        val encoded = (capturedMediaId ?: error("target not captured")).remoteId
        val decoded = encoded.decodeRemoteServerPlaybackTarget()
        assertEquals("media:/?#%", decoded?.sourceMediaId)
    }

    @Test
    fun localCandidateSuccessDoesNotReadRemoteProperties() = runBlocking {
        var propertyReads = 0
        val local = fakeMusicSource(BuiltInSourceIds.Local) {
            SourcePlaybackResult.Success(PlaybackResource(uri = "file:///local.flac", isLocal = true))
        }
        val resolver = PlaybackResourceResolver(
            storageLookup = LegacyStorageLookup { null },
            trackSourceRefDao = fakeTrackSourceRefDao(
                candidate(path = "/local.flac", providerType = ProviderTypes.Local, sourceAccountId = 7),
                candidate(path = null, providerType = ProviderTypes.Emby, sourceItemId = 321, isPreferred = false),
            ),
            sourceRegistry = MusicSourceRegistry(listOf(local)),
            legacyStoragePlaybackResolver = unusedPlaybackResolver(),
            sourceItemPropertyReader = SourceItemPropertyReader { propertyReads += 1; emptyList() },
        )

        assertTrue(resolver.resolve(music(42, "/ignored.flac")) is SourcePlaybackResult.Success)
        assertEquals(0, propertyReads)
    }

    @Test
    fun embyUnauthorizedIsReturnedWhenNoFallbackExists() = runBlocking {
        val resolver = PlaybackResourceResolver(
            storageLookup = LegacyStorageLookup { null },
            trackSourceRefDao = fakeTrackSourceRefDao(
                candidate(path = null, providerType = ProviderTypes.Emby)
            ),
            sourceRegistry = MusicSourceRegistry(
                listOf(fakeMusicSource(BuiltInSourceIds.Emby) {
                    SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unauthorized)
                })
            ),
            legacyStoragePlaybackResolver = unusedPlaybackResolver(),
            sourceItemPropertyReader = SourceItemPropertyReader.Empty,
        )

        assertEquals(
            SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unauthorized),
            resolver.resolve(music(42, "/ignored.flac")),
        )
    }

    @Test
    fun unsupportedRemoteCandidateFallsThroughToNextCandidate() = runBlocking {
        val calls = mutableListOf<String>()
        val resolver = PlaybackResourceResolver(
            storageLookup = LegacyStorageLookup { null },
            trackSourceRefDao = fakeTrackSourceRefDao(
                candidate(path = null, providerType = ProviderTypes.Emby, sourceAccountId = 42, sourceItemId = 100),
                candidate(path = null, providerType = ProviderTypes.Emby, sourceAccountId = 43, sourceItemId = 101),
            ),
            sourceRegistry = MusicSourceRegistry(
                listOf(fakeMusicSource(BuiltInSourceIds.Emby) { mediaId ->
                    val target = mediaId.remoteId.decodeRemoteServerPlaybackTarget()
                    calls += target?.accountId?.value.orEmpty()
                    if (target?.accountId?.value == "storage:42") {
                        SourcePlaybackResult.Failure(SourcePlaybackFailureReason.UnsupportedMediaType)
                    } else {
                        SourcePlaybackResult.Success(PlaybackResource(uri = "https://server.invalid/ok"))
                    }
                })
            ),
            legacyStoragePlaybackResolver = unusedPlaybackResolver(),
            sourceItemPropertyReader = SourceItemPropertyReader.Empty,
        )

        assertTrue(resolver.resolve(music(42, "/ignored.flac")) is SourcePlaybackResult.Success)
        assertEquals(listOf("storage:42", "storage:43"), calls)
    }

    @Test
    fun proxyFailureFallsThroughToNextRemoteCandidate() = runBlocking {
        var wrapCalls = 0
        val resolver = PlaybackResourceResolver(
            storageLookup = LegacyStorageLookup { null },
            trackSourceRefDao = fakeTrackSourceRefDao(
                candidate(path = null, providerType = ProviderTypes.Emby, sourceAccountId = 42, sourceItemId = 100),
                candidate(path = null, providerType = ProviderTypes.Emby, sourceAccountId = 43, sourceItemId = 101),
            ),
            sourceRegistry = MusicSourceRegistry(
                listOf(fakeMusicSource(BuiltInSourceIds.Emby) {
                    SourcePlaybackResult.Success(PlaybackResource(uri = "https://server.invalid/remote"))
                })
            ),
            legacyStoragePlaybackResolver = unusedPlaybackResolver(),
            sourceItemPropertyReader = SourceItemPropertyReader.Empty,
            playbackAudioCache = fakePlaybackAudioCache(
                onWrapRemote = { identity, _ ->
                    wrapCalls += 1
                    if (identity.storageId == 42L) throw PlaybackProxyUnavailableException()
                    PlaybackResource(uri = "http://127.0.0.1/proxy")
                }
            ),
        )

        assertEquals(
            SourcePlaybackResult.Success(PlaybackResource(uri = "http://127.0.0.1/proxy")),
            resolver.resolve(music(42, "/ignored.flac")),
        )
        assertEquals(2, wrapCalls)
    }

    @Test
    fun allProxyFailuresReturnUnavailable() = runBlocking {
        var wrapCalls = 0
        val resolver = PlaybackResourceResolver(
            storageLookup = LegacyStorageLookup { null },
            trackSourceRefDao = fakeTrackSourceRefDao(
                candidate(path = null, providerType = ProviderTypes.Emby, sourceAccountId = 42, sourceItemId = 100),
                candidate(path = null, providerType = ProviderTypes.Emby, sourceAccountId = 43, sourceItemId = 101),
            ),
            sourceRegistry = MusicSourceRegistry(
                listOf(fakeMusicSource(BuiltInSourceIds.Emby) {
                    SourcePlaybackResult.Success(PlaybackResource(uri = "https://server.invalid/remote"))
                })
            ),
            legacyStoragePlaybackResolver = unusedPlaybackResolver(),
            sourceItemPropertyReader = SourceItemPropertyReader.Empty,
            playbackAudioCache = fakePlaybackAudioCache(
                onWrapRemote = { _, _ ->
                    wrapCalls += 1
                    throw PlaybackProxyUnavailableException()
                }
            ),
        )

        assertEquals(
            SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unavailable),
            resolver.resolve(music(42, "/ignored.flac")),
        )
        assertEquals(2, wrapCalls)
    }

    @Test
    fun resolvesLegacyMusicLocationThroughMatchingSource() = runBlocking {
        var capturedMediaId: MediaId? = null
        val source = fakeMusicSource(BuiltInSourceIds.WebDav) { mediaId ->
            capturedMediaId = mediaId
            SourcePlaybackResult.Success(PlaybackResource(uri = "http://127.0.0.1/track.flac"))
        }
        val resolver = PlaybackResourceResolver(
            storageLookup = LegacyStorageLookup {
                storage(id = 42, typ = StorageType.WEBDAV)
            },
            trackSourceRefDao = fakeTrackSourceRefDao(),
            sourceRegistry = MusicSourceRegistry(listOf(source)),
            legacyStoragePlaybackResolver = unusedPlaybackResolver(),
            sourceItemPropertyReader = SourceItemPropertyReader.Empty,
        )

        val result = resolver.resolve(music(storageId = 42, path = "/Music/Track.flac"))

        assertEquals(
            SourcePlaybackResult.Success(PlaybackResource(uri = "http://127.0.0.1/track.flac")),
            result,
        )
        assertEquals(
            legacyStorageTrackMediaId(
                sourceId = BuiltInSourceIds.WebDav,
                accountId = SourceAccountId("storage:42"),
                path = "/Music/Track.flac",
            ),
            capturedMediaId,
        )
    }

    @Test
    fun resolvesRoomSourceCandidateBeforeLegacyLocation() = runBlocking {
        var capturedMediaId: MediaId? = null
        val source = fakeMusicSource(BuiltInSourceIds.WebDav) { mediaId ->
            capturedMediaId = mediaId
            SourcePlaybackResult.Success(PlaybackResource(uri = "http://127.0.0.1/candidate.flac"))
        }
        val resolver = PlaybackResourceResolver(
            storageLookup = LegacyStorageLookup { null },
            trackSourceRefDao = fakeTrackSourceRefDao(
                candidate(path = "/Music/Candidate.flac"),
            ),
            sourceRegistry = MusicSourceRegistry(listOf(source)),
            legacyStoragePlaybackResolver = unusedPlaybackResolver(),
            sourceItemPropertyReader = SourceItemPropertyReader.Empty,
        )

        val result = resolver.resolve(music(storageId = 1, path = "/Legacy/Track.flac"))

        assertEquals(
            SourcePlaybackResult.Success(PlaybackResource(uri = "http://127.0.0.1/candidate.flac")),
            result,
        )
        assertEquals(
            legacyStorageTrackMediaId(
                sourceId = BuiltInSourceIds.WebDav,
                accountId = SourceAccountId("storage:42"),
                path = "/Music/Candidate.flac",
            ),
            capturedMediaId,
        )
    }

    @Test
    fun remoteServerCandidateUsesProviderPlaybackIdWithoutLegacyPathEncoding() = runBlocking {
        var capturedMediaId: MediaId? = null
        val source = fakeMusicSource(BuiltInSourceIds.Navidrome) { mediaId ->
            capturedMediaId = mediaId
            SourcePlaybackResult.Success(PlaybackResource(uri = "http://127.0.0.1/navidrome-track.flac"))
        }
        val resolver = PlaybackResourceResolver(
            storageLookup = LegacyStorageLookup { null },
            trackSourceRefDao = fakeTrackSourceRefDao(
                candidate(
                    path = null,
                    providerType = ProviderTypes.Navidrome,
                    sourceItemId = 321,
                ),
            ),
            sourceRegistry = MusicSourceRegistry(listOf(source)),
            legacyStoragePlaybackResolver = unusedPlaybackResolver(),
            sourceItemPropertyReader = SourceItemPropertyReader.Empty,
            playbackAudioCache = fakePlaybackAudioCache(
                onWrapRemote = { identity, resource ->
                    assertEquals(PlaybackCacheIdentity(42, "remote:42:item-321"), identity)
                    assertTrue("secret" !in identity.path)
                    resource
                }
            ),
        )

        resolver.resolve(music(storageId = 42, path = "/Legacy/Track.flac"))

        assertEquals(
            MediaId(
                sourceId = BuiltInSourceIds.Navidrome,
                mediaType = MediaType.Track,
                remoteId = RemoteServerPlaybackTarget(
                    accountId = SourceAccountId("storage:42"),
                    remoteId = "item-321",
                ).encodedPlaybackId(),
            ),
            capturedMediaId,
        )
    }

    @Test
    fun resolvesLocalCandidateBeforeCacheAndRemoteCandidate() = runBlocking {
        var remoteCalls = 0
        val remoteSource = fakeMusicSource(BuiltInSourceIds.WebDav) {
            remoteCalls += 1
            SourcePlaybackResult.Success(PlaybackResource(uri = "http://127.0.0.1/remote.flac"))
        }
        val localResource = PlaybackResource(
            uri = "file:///Music/Track.flac",
            mimeType = "audio/flac",
            isLocal = true,
        )
        val localSource = fakeMusicSource(BuiltInSourceIds.Local) {
            SourcePlaybackResult.Success(localResource)
        }
        var cacheCalls = 0
        val cache = fakePlaybackAudioCache(
            onResolveCompleted = { _, _ ->
                cacheCalls += 1
                PlaybackResource(uri = "file:///cache/track.flac", isLocal = true)
            }
        )
        val resolver = PlaybackResourceResolver(
            storageLookup = LegacyStorageLookup { null },
            trackSourceRefDao = fakeTrackSourceRefDao(
                candidate(path = "/Remote/Track.flac"),
                candidate(
                    path = "/Music/Track.flac",
                    providerType = ProviderTypes.Local,
                    sourceAccountId = 7,
                ),
            ),
            sourceRegistry = MusicSourceRegistry(listOf(remoteSource, localSource)),
            legacyStoragePlaybackResolver = unusedPlaybackResolver(),
            sourceItemPropertyReader = SourceItemPropertyReader.Empty,
            playbackAudioCache = cache,
        )

        assertEquals(
            SourcePlaybackResult.Success(localResource),
            resolver.resolve(music(storageId = 42, path = "/Remote/Track.flac")),
        )
        assertEquals(0, cacheCalls)
        assertEquals(0, remoteCalls)
    }

    @Test
    fun explicitlyPreferredRemoteSourceIsTriedBeforeLocalFallback() = runBlocking {
        val calls = mutableListOf<String>()
        val remoteResource = PlaybackResource(uri = "http://127.0.0.1/preferred.flac")
        val resolver = PlaybackResourceResolver(
            storageLookup = LegacyStorageLookup { null },
            trackSourceRefDao = fakeTrackSourceRefDao(
                candidate(
                    path = "/Remote/Track.flac",
                    sourceItemId = 101,
                    isPreferred = true,
                ),
                candidate(
                    path = "/Local/Track.flac",
                    providerType = ProviderTypes.Local,
                    sourceAccountId = 7,
                    sourceItemId = 102,
                    isPreferred = false,
                ),
            ),
            sourceRegistry = MusicSourceRegistry(
                listOf(
                    fakeMusicSource(BuiltInSourceIds.WebDav) {
                        calls += "remote"
                        SourcePlaybackResult.Success(remoteResource)
                    },
                    fakeMusicSource(BuiltInSourceIds.Local) {
                        calls += "local"
                        SourcePlaybackResult.Success(PlaybackResource(uri = "file:///Local/Track.flac"))
                    },
                )
            ),
            legacyStoragePlaybackResolver = unusedPlaybackResolver(),
            sourceItemPropertyReader = SourceItemPropertyReader.Empty,
        )

        assertEquals(SourcePlaybackResult.Success(remoteResource), resolver.resolve(music(42, "/Legacy.flac")))
        assertEquals(listOf("remote"), calls)
    }

    @Test
    fun resolvesCompletedCacheBeforeRemoteCandidate() = runBlocking {
        var remoteCalls = 0
        val source = fakeMusicSource(BuiltInSourceIds.WebDav) {
            remoteCalls += 1
            SourcePlaybackResult.Success(PlaybackResource(uri = "http://127.0.0.1/remote.flac"))
        }
        val cachedResource = PlaybackResource(
            uri = "http://127.0.0.1/cached.flac",
            mimeType = "audio/flac",
            isLocal = true,
        )
        val resolver = PlaybackResourceResolver(
            storageLookup = LegacyStorageLookup { null },
            trackSourceRefDao = fakeTrackSourceRefDao(candidate(path = "/Music/Track.flac")),
            sourceRegistry = MusicSourceRegistry(listOf(source)),
            legacyStoragePlaybackResolver = unusedPlaybackResolver(),
            sourceItemPropertyReader = SourceItemPropertyReader.Empty,
            playbackAudioCache = fakePlaybackAudioCache(
                onResolveCompleted = { identity, _ ->
                    assertEquals(PlaybackCacheIdentity(42, "/Music/Track.flac"), identity)
                    cachedResource
                }
            ),
        )

        assertEquals(
            SourcePlaybackResult.Success(cachedResource),
            resolver.resolve(music(storageId = 42, path = "/Music/Track.flac")),
        )
        assertEquals(0, remoteCalls)
    }

    @Test
    fun wrapsRemoteCandidateForListenAndCache() = runBlocking {
        val remoteResource = PlaybackResource(uri = "http://127.0.0.1/remote.flac")
        val cachedProxy = PlaybackResource(uri = "http://127.0.0.1/cache-proxy.flac")
        val source = fakeMusicSource(BuiltInSourceIds.WebDav) {
            SourcePlaybackResult.Success(remoteResource)
        }
        val resolver = PlaybackResourceResolver(
            storageLookup = LegacyStorageLookup { null },
            trackSourceRefDao = fakeTrackSourceRefDao(
                candidate(path = "/Music/Track.flac", etag = "etag-v1"),
            ),
            sourceRegistry = MusicSourceRegistry(listOf(source)),
            legacyStoragePlaybackResolver = unusedPlaybackResolver(),
            sourceItemPropertyReader = SourceItemPropertyReader.Empty,
            playbackAudioCache = fakePlaybackAudioCache(
                onWrapRemote = { identity, resource ->
                    assertEquals(
                        PlaybackCacheIdentity(42, "/Music/Track.flac", "etag-v1"),
                        identity,
                    )
                    assertEquals(remoteResource, resource)
                    cachedProxy
                }
            ),
        )

        assertEquals(
            SourcePlaybackResult.Success(cachedProxy),
            resolver.resolve(music(storageId = 42, path = "/Music/Track.flac")),
        )
    }

    @Test
    fun preloadsResolvedRemoteCandidateWithItsPhysicalCacheIdentity() = runBlocking {
        val remoteResource = PlaybackResource(uri = "https://server.invalid/audio")
        val source = fakeMusicSource(BuiltInSourceIds.WebDav) {
            SourcePlaybackResult.Success(remoteResource)
        }
        var preloadCalls = 0
        val released = mutableListOf<String>()
        val resolver = PlaybackResourceResolver(
            storageLookup = LegacyStorageLookup { null },
            trackSourceRefDao = fakeTrackSourceRefDao(
                candidate(path = "/Music/Track.flac", etag = "etag-v1"),
            ),
            sourceRegistry = MusicSourceRegistry(listOf(source)),
            legacyStoragePlaybackResolver = unusedPlaybackResolver(released::add),
            sourceItemPropertyReader = SourceItemPropertyReader.Empty,
            playbackAudioCache = fakePlaybackAudioCache(
                onPreloadRemote = { identity, resource, maxBytes ->
                    preloadCalls += 1
                    assertEquals(
                        PlaybackCacheIdentity(42, "/Music/Track.flac", "etag-v1"),
                        identity,
                    )
                    assertEquals(remoteResource, resource)
                    assertEquals(2L * 1024 * 1024, maxBytes)
                    PlaybackAudioPreloadResult.Completed
                }
            ),
        )

        assertTrue(resolver.preload(music(42, "/Legacy.flac"), 2L * 1024 * 1024))
        assertEquals(1, preloadCalls)
        assertEquals(listOf(remoteResource.uri), released)
    }

    @Test
    fun actualPlaybackTakesPreloadedSessionBeforeResolvingSourceAgain() = runBlocking {
        var sourceCalls = 0
        val wrapped = PlaybackResource(uri = "http://127.0.0.1/preloaded.flac")
        val resolver = PlaybackResourceResolver(
            storageLookup = LegacyStorageLookup { null },
            trackSourceRefDao = fakeTrackSourceRefDao(
                candidate(path = "/Music/Track.flac", etag = "etag-v1"),
            ),
            sourceRegistry = MusicSourceRegistry(
                listOf(
                    fakeMusicSource(BuiltInSourceIds.WebDav) {
                        sourceCalls += 1
                        SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unavailable)
                    }
                )
            ),
            legacyStoragePlaybackResolver = unusedPlaybackResolver(),
            sourceItemPropertyReader = SourceItemPropertyReader.Empty,
            playbackAudioCache = fakePlaybackAudioCache(
                onResolvePreloaded = { identity, mimeType ->
                    assertEquals(
                        PlaybackCacheIdentity(42, "/Music/Track.flac", "etag-v1"),
                        identity,
                    )
                    assertEquals("audio/flac", mimeType)
                    wrapped
                }
            ),
        )

        assertEquals(
            SourcePlaybackResult.Success(wrapped),
            resolver.resolve(music(42, "/Legacy.flac")),
        )
        assertEquals(0, sourceCalls)
    }

    @Test
    fun preloadReleasesCompletedCacheProbeWithoutResolvingRemoteSource() = runBlocking {
        val cached = PlaybackResource(uri = "http://127.0.0.1/completed.flac", isLocal = true)
        val released = mutableListOf<PlaybackResource>()
        var sourceCalls = 0
        val resolver = PlaybackResourceResolver(
            storageLookup = LegacyStorageLookup { null },
            trackSourceRefDao = fakeTrackSourceRefDao(candidate(path = "/Music/Track.flac")),
            sourceRegistry = MusicSourceRegistry(
                listOf(
                    fakeMusicSource(BuiltInSourceIds.WebDav) {
                        sourceCalls += 1
                        SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unavailable)
                    }
                )
            ),
            legacyStoragePlaybackResolver = unusedPlaybackResolver(),
            sourceItemPropertyReader = SourceItemPropertyReader.Empty,
            playbackAudioCache = fakePlaybackAudioCache(
                onResolveCompleted = { _, _ -> cached },
                onRelease = { resource -> released += resource; null },
            ),
        )

        assertTrue(resolver.preload(music(42, "/Legacy.flac"), 2L * 1024 * 1024))
        assertEquals(listOf(cached), released)
        assertEquals(0, sourceCalls)
    }

    @Test
    fun preloadReleasesLocalResourceUsedOnlyToSkipRemotePreload() = runBlocking {
        val local = PlaybackResource(uri = "file:///Music/Track.flac", isLocal = true)
        val released = mutableListOf<String>()
        val resolver = PlaybackResourceResolver(
            storageLookup = LegacyStorageLookup { null },
            trackSourceRefDao = fakeTrackSourceRefDao(
                candidate(
                    path = "/Music/Track.flac",
                    providerType = ProviderTypes.Local,
                    sourceAccountId = 7,
                )
            ),
            sourceRegistry = MusicSourceRegistry(
                listOf(
                    fakeMusicSource(BuiltInSourceIds.Local) {
                        SourcePlaybackResult.Success(local)
                    }
                )
            ),
            legacyStoragePlaybackResolver = unusedPlaybackResolver(released::add),
            sourceItemPropertyReader = SourceItemPropertyReader.Empty,
        )

        assertEquals(false, resolver.preload(music(42, "/Legacy.flac"), 2L * 1024 * 1024))
        assertEquals(listOf(local.uri), released)
    }

    @Test
    fun missingStorageFailsBeforeCallingSource() = runBlocking {
        var sourceCalls = 0
        val source = fakeMusicSource(BuiltInSourceIds.Local) {
            sourceCalls += 1
            SourcePlaybackResult.Success(PlaybackResource(uri = "http://127.0.0.1/local.wav"))
        }
        val resolver = PlaybackResourceResolver(
            storageLookup = LegacyStorageLookup { null },
            trackSourceRefDao = fakeTrackSourceRefDao(),
            sourceRegistry = MusicSourceRegistry(listOf(source)),
            legacyStoragePlaybackResolver = unusedPlaybackResolver(),
            sourceItemPropertyReader = SourceItemPropertyReader.Empty,
        )

        assertEquals(
            SourcePlaybackResult.Failure(SourcePlaybackFailureReason.UnsupportedAccount),
            resolver.resolve(music(storageId = 1, path = "/Missing.wav")),
        )
        assertEquals(0, sourceCalls)
    }

    @Test
    fun releaseDelegatesToRetainedLegacyResolver() = runBlocking {
        val released = mutableListOf<String>()
        val resolver = PlaybackResourceResolver(
            storageLookup = LegacyStorageLookup { null },
            trackSourceRefDao = fakeTrackSourceRefDao(),
            sourceRegistry = MusicSourceRegistry(emptyList()),
            legacyStoragePlaybackResolver = object : LegacyStoragePlaybackResolver {
                override suspend fun resolve(
                    accountId: SourceAccountId,
                    path: String,
                    expectedStorageKind: LegacyStorageKind,
                ): SourcePlaybackResult {
                    return SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unavailable)
                }

                override suspend fun release(uri: String) {
                    released += uri
                }

                override suspend fun releaseAll() = Unit
            },
            sourceItemPropertyReader = SourceItemPropertyReader.Empty,
        )

        resolver.release(PlaybackResource(uri = "http://127.0.0.1/release.mp3"))

        assertEquals(listOf("http://127.0.0.1/release.mp3"), released)
    }

    private fun fakeMusicSource(
        sourceId: SourceId,
        resolve: suspend (MediaId) -> SourcePlaybackResult,
    ) = object : MusicSource {
        override val descriptor = MusicSourceDescriptor(sourceId, sourceId.value)
        override val capabilities = setOf(SourceCapability.Stream)

        override suspend fun authenticate(configuration: SourceConfiguration): SourceAuthResult {
            return SourceAuthResult.Success
        }

        override suspend fun list(
            accountId: SourceAccountId,
            directoryId: String?,
        ): SourceListResult {
            return SourceListResult.Success(emptyList())
        }

        override suspend fun resolvePlayback(mediaId: MediaId): SourcePlaybackResult {
            return resolve(mediaId)
        }
    }

    private fun unusedPlaybackResolver(
        onRelease: (String) -> Unit = {},
    ) = object : LegacyStoragePlaybackResolver {
        override suspend fun resolve(
            accountId: SourceAccountId,
            path: String,
            expectedStorageKind: LegacyStorageKind,
        ): SourcePlaybackResult {
            return SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unavailable)
        }

        override suspend fun release(uri: String) = onRelease(uri)

        override suspend fun releaseAll() = Unit
    }

    private fun fakePlaybackAudioCache(
        onResolveCompleted: suspend (PlaybackCacheIdentity, String?) -> PlaybackResource? = { _, _ ->
            null
        },
        onWrapRemote: suspend (PlaybackCacheIdentity, PlaybackResource) -> PlaybackResource =
            { _, resource -> resource },
        onResolvePreloaded: suspend (PlaybackCacheIdentity, String?) -> PlaybackResource? =
            { _, _ -> null },
        onPreloadRemote: suspend (
            PlaybackCacheIdentity,
            PlaybackResource,
            Long,
        ) -> PlaybackAudioPreloadResult = { _, _, _ -> PlaybackAudioPreloadResult.Failed },
        onRelease: suspend (PlaybackResource) -> PlaybackResource? = { it },
    ) = object : PlaybackAudioCache {
        override suspend fun resolveCompleted(
            identity: PlaybackCacheIdentity,
            mimeType: String?,
        ): PlaybackResource? = onResolveCompleted(identity, mimeType)

        override suspend fun wrapRemote(
            identity: PlaybackCacheIdentity,
            resource: PlaybackResource,
        ): PlaybackResource = onWrapRemote(identity, resource)

        override suspend fun resolvePreloaded(
            identity: PlaybackCacheIdentity,
            mimeType: String?,
        ): PlaybackResource? = onResolvePreloaded(identity, mimeType)

        override suspend fun preloadRemote(
            identity: PlaybackCacheIdentity,
            resource: PlaybackResource,
            maxBytes: Long,
        ): PlaybackAudioPreloadResult = onPreloadRemote(identity, resource, maxBytes)

        override suspend fun release(resource: PlaybackResource): PlaybackResource? = onRelease(resource)

        override suspend fun releaseAll() = Unit
    }

    private fun music(
        storageId: Long,
        path: String,
    ) = Music(
        meta = MusicMeta(
            id = MusicId(7),
            title = "Track",
            duration = null,
            order = emptyList(),
        ),
        loc = StorageEntryLoc(
            storageId = StorageId(storageId),
            path = path,
        ),
        cover = null,
        lyric = null,
    )

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

    private fun fakeTrackSourceRefDao(
        vararg candidates: TrackSourcePlaybackCandidate,
    ) = object : TrackSourceRefDao {
        override suspend fun findByTrackId(trackId: Long): List<TrackSourceRefEntity> {
            return emptyList()
        }

        override suspend fun contains(trackId: Long, sourceItemId: Long) = false

        override suspend fun updatePreferredSource(trackId: Long, sourceItemId: Long, now: Long) = Unit

        override suspend fun findBySourceItemIds(sourceItemIds: List<Long>): List<TrackSourceRefEntity> {
            return emptyList()
        }

        override suspend fun webDavMetadataCandidatesForTrack(trackId: Long) = emptyList<MetadataRefreshCandidate>()

        override suspend fun metadataResetCandidateForTrack(trackId: Long): MetadataRefreshCandidate? = null

        override suspend fun webDavMetadataCandidatesForAlbum(albumId: Long) = emptyList<MetadataRefreshCandidate>()

        override suspend fun missingWebDavMetadataCandidates(target: String) = emptyList<MetadataRefreshCandidate>()

        override suspend fun countForTrack(trackId: Long): Int {
            return 0
        }

        override suspend fun hasSourceAccount(trackId: Long, sourceAccountId: Long) = false

        override suspend fun upsertAll(refs: List<TrackSourceRefEntity>) = Unit

        override suspend fun updateEmbeddedMetadataPresence(
            sourceItemId: Long,
            hasEmbeddedArtwork: Boolean,
            embeddedLyricsKind: String,
            now: Long,
        ) = Unit

        override suspend fun markAvailableBySourceItemIds(sourceItemIds: List<Long>, now: Long) = Unit

        override suspend fun markUnavailableBySourceItemIds(sourceItemIds: List<Long>, now: Long) = Unit

        override suspend fun markUnavailableForDeletedSourceItems(libraryRootId: Long, now: Long) = Unit

        override suspend fun playbackCandidates(trackId: Long): List<TrackSourcePlaybackCandidate> {
            return candidates.toList()
        }

        override suspend fun playbackCandidatesForTracks(trackIds: List<Long>): List<TrackSourcePlaybackCandidate> {
            return candidates.filter { candidate -> candidate.ref.trackId in trackIds }
        }
    }

    private fun candidate(
        path: String?,
        providerType: String = ProviderTypes.WebDav,
        sourceAccountId: Long = 42,
        etag: String? = null,
        sourceItemId: Long = 100,
        providerItemId: String = "item-$sourceItemId",
        isPreferred: Boolean = true,
    ) = TrackSourcePlaybackCandidate(
        ref = TrackSourceRefEntity(
            trackId = 7,
            sourceItemId = sourceItemId,
            role = "primary",
            matchMethod = "source_identity",
            matchConfidence = 100,
            isPreferred = isPreferred,
            isAvailable = true,
            isDownloaded = false,
            playable = true,
            downloadable = true,
            codec = null,
            container = null,
            bitRate = null,
            sampleRate = null,
            bitsPerSample = null,
            channels = null,
            lossless = null,
            createdAt = 1,
            updatedAt = 2,
        ),
        item = SourceItemEntity(
            id = sourceItemId,
            sourceAccountId = sourceAccountId,
            libraryRootId = 2,
            itemType = SourceItemTypes.Track,
            providerItemId = providerItemId,
            parentProviderItemId = null,
            canonicalPath = path,
            displayPath = path,
            displayName = path?.substringAfterLast('/') ?: "Remote track",
            mimeType = "audio/flac",
            sizeBytes = 100,
            etag = etag,
            revision = null,
            createdAtRemote = null,
            modifiedAtRemote = null,
            contentHash = null,
            audioFingerprint = null,
            isDeleted = false,
            firstSyncedAt = 1,
            lastSyncedAt = 2,
            lastSeenScanId = "scan-1",
        ),
        account = SourceAccountEntity(
            id = sourceAccountId,
            providerType = providerType,
            displayName = "NAS",
            endpoint = null,
            externalAccountId = null,
            credentialRef = null,
            priority = 0,
            enabled = true,
            createdAt = 1,
            updatedAt = 2,
        ),
    )

    private data class ProviderPlaybackCase(
        val providerType: String,
        val sourceId: SourceId,
        val isServer: Boolean,
    )
}
