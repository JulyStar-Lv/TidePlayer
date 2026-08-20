package io.github.julystar.musicapp.source.server

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.AppDatabaseConstructor
import io.github.julystar.musicapp.database.AlbumEntity
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.SourceAccountEntity
import io.github.julystar.musicapp.database.SourceItemEntity
import io.github.julystar.musicapp.database.SourceItemTypes
import io.github.julystar.musicapp.database.TrackEntity
import io.github.julystar.musicapp.database.TrackSourceRefEntity
import io.github.julystar.musicapp.domain.importing.MATCH_CONFIDENCE_EXACT
import io.github.julystar.musicapp.domain.importing.MATCH_CONFIDENCE_STRICT_METADATA
import io.github.julystar.musicapp.domain.importing.TrackMatchMethods
import io.github.julystar.musicapp.domain.importing.TrackSourceRoles
import io.github.julystar.musicapp.source.api.RemoteServerGateway
import io.github.julystar.musicapp.source.api.RemoteServerKind
import io.github.julystar.musicapp.source.api.RemoteServerSourceConfiguration
import io.github.julystar.musicapp.source.api.RemoteServerTrack
import io.github.julystar.musicapp.source.api.RemoteServerTrackPage
import io.github.julystar.musicapp.source.api.SourceAudioProperties
import io.github.julystar.musicapp.source.api.SourceAuthResult
import io.github.julystar.musicapp.source.api.SourcePlaybackFailureReason
import io.github.julystar.musicapp.source.api.SourcePlaybackResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NavidromeLibrarySyncCoordinatorTest {
    @Test
    fun syncIsMetadataRichIdempotentAndFailureSafe() = runBlocking {
        val database = buildDatabase()
        try {
            insertAccount(database, 7)
            val track = remoteTrack("song-1", title = "First title")
            val gateway = FakeGateway(listOf(Result.success(RemoteServerTrackPage(listOf(track)))))
            val coordinator = coordinator(database, gateway)
            assertFailsWith<IllegalArgumentException> {
                coordinator.sync(SourceAccountId("storage:7"), scanId = " ", pageSize = 1)
            }

            val first = coordinator.sync(SourceAccountId("storage:7"), scanId = "scan-1", pageSize = 1)
            assertEquals(1, first.added)
            assertEquals(1, first.scanned)
            val item = assertNotNull(
                database.sourceItemDao().findByProviderItemIds(7, listOf("song-1")).singleOrNull()
            )
            val ref = database.trackSourceRefDao().findBySourceItemIds(listOf(item.id)).single()
            val stored = assertNotNull(database.trackDao().get(ref.trackId))
            assertEquals("First title", stored.title)
            assertEquals(2024, stored.year)
            assertEquals(3, stored.trackNumber)
            assertEquals(2, stored.discNumber)
            assertEquals(245_000L, stored.durationMs)
            assertEquals(900, stored.bitRate)
            assertEquals(96_000, stored.sampleRate)
            assertEquals(24, stored.bitsPerSample)
            assertEquals(2, stored.channels)
            assertEquals("flac", stored.container?.lowercase())
            assertEquals("SERVER", stored.metadataSource)
            assertEquals("flac", ref.container?.lowercase())
            assertEquals(900, ref.bitRate)
            assertEquals(96_000, ref.sampleRate)
            assertEquals(24, ref.bitsPerSample)
            assertEquals(2, ref.channels)
            assertEquals("Album", database.metadataDao().findAlbumsByNormalizedNames(listOf("album")).single().name)
            assertEquals("Artist", database.metadataDao().findArtistsByNormalizedNames(listOf("artist")).single().name)
            assertEquals("Genre", database.metadataDao().findGenresByNormalizedNames(listOf("genre")).single().name)
            assertEquals(listOf("Artist"), database.metadataDao().artistNamesForTrack(stored.id))
            assertEquals(listOf("Genre"), database.metadataDao().genreNamesForTrack(stored.id))
            val albumId = assertNotNull(stored.albumId)
            assertEquals(listOf("Album Artist"), database.metadataDao().artistNamesForAlbum(albumId))
            val coverProperty = database.sourceItemDao().propertiesForItems(listOf(item.id))
                .single { it.propertyKey == "coverArtId" }
            assertEquals("cover-art-song-1", coverProperty.stringValue)
            val persistedAccount = requireNotNull(database.sourceAccountDao().get(7))
            val persistedProperties = database.sourceItemDao().propertiesForItems(listOf(item.id))
            assertTrue("token=secret" !in item.toString() && "password=secret" !in item.toString())
            assertTrue(persistedProperties.none { "secret" in it.toString() })
            assertTrue("password" !in persistedAccount.providerConfig.orEmpty())

            val itemId = item.id
            val trackId = stored.id
            val refKey = ref.sourceItemId to ref.trackId
            val second = coordinator.sync(SourceAccountId("storage:7"), scanId = "scan-2", pageSize = 1)
            assertEquals(1, second.unchanged)
            assertEquals(0, second.added)
            val itemAfter = database.sourceItemDao().findByProviderItemIds(7, listOf("song-1")).single()
            val refAfter = database.trackSourceRefDao().findBySourceItemIds(listOf(itemAfter.id)).single()
            assertEquals(itemId, itemAfter.id)
            assertEquals(trackId, refAfter.trackId)
            assertEquals(refKey, refAfter.sourceItemId to refAfter.trackId)

            gateway.results = listOf(
                Result.success(
                    RemoteServerTrackPage(listOf(track.copy(title = "Modified title")))
                )
            )
            val modified = coordinator.sync(SourceAccountId("storage:7"), scanId = "scan-3", pageSize = 1)
            assertEquals(1, modified.modified)
            assertEquals("Modified title", database.trackDao().get(trackId)?.title)

            gateway.results = listOf(
                Result.success(
                    RemoteServerTrackPage(listOf(track.copy(title = "Modified title", coverArtId = null)))
                )
            )
            coordinator.sync(SourceAccountId("storage:7"), scanId = "scan-cover-clear", pageSize = 1)
            assertTrue(database.sourceItemDao().propertiesForItems(listOf(itemId)).none { it.propertyKey == "coverArtId" })

            database.trackDao().upsertAll(listOf(requireNotNull(database.trackDao().get(trackId)).copy(metadataLocked = true)))
            gateway.results = listOf(
                Result.success(
                    RemoteServerTrackPage(listOf(track.copy(title = "Locked remote title")))
                )
            )
            coordinator.sync(SourceAccountId("storage:7"), scanId = "scan-locked", pageSize = 1)
            assertEquals("Modified title", database.trackDao().get(trackId)?.title)

            gateway.results = listOf(Result.failure(IllegalStateException("page failed")))
            assertFailsWith<IllegalStateException> {
                coordinator.sync(SourceAccountId("storage:7"), scanId = "scan-failed", pageSize = 1)
            }
            val afterFailure = database.sourceItemDao().findByProviderItemIds(7, listOf("song-1")).single()
            assertTrue(!afterFailure.isDeleted)
            assertTrue(database.trackSourceRefDao().findBySourceItemIds(listOf(itemId)).single().isAvailable)
        } finally {
            database.close()
        }
    }

    @Test
    fun missingRemoteMetadataPreservesExistingScalarAndNormalizedValues() = runBlocking {
        val database = buildDatabase()
        try {
            insertAccount(database, 7)
            val initial = remoteTrack("fallback-song", "Keep this title")
            val gateway = FakeGateway(listOf(Result.success(RemoteServerTrackPage(listOf(initial)))))
            val coordinator = coordinator(database, gateway)
            coordinator.sync(SourceAccountId("storage:7"), scanId = "fallback-1", pageSize = 1)

            gateway.results = listOf(
                Result.success(
                    RemoteServerTrackPage(
                        listOf(initial.copy(title = "", artist = null, genre = null, albumArtist = null, mimeType = null))
                    )
                )
            )
            coordinator.sync(SourceAccountId("storage:7"), scanId = "fallback-2", pageSize = 1)
            val item = database.sourceItemDao().findByProviderItemIds(7, listOf("fallback-song")).single()
            val ref = database.trackSourceRefDao().findBySourceItemIds(listOf(item.id)).single()
            val track = assertNotNull(database.trackDao().get(ref.trackId))
            assertEquals("Keep this title", track.title)
            assertEquals("Artist", track.artist)
            assertEquals("audio/flac", item.mimeType)
            assertEquals("Keep this title", item.displayName)
            assertEquals(listOf("Artist"), database.metadataDao().artistNamesForTrack(track.id))
            assertEquals(listOf("Genre"), database.metadataDao().genreNamesForTrack(track.id))
            assertEquals(listOf("Album Artist"), database.metadataDao().artistNamesForAlbum(requireNotNull(track.albumId)))
        } finally {
            database.close()
        }
    }

    @Test
    fun duplicateIdsAcrossPagesAreAppliedOnceAndLaterFailureDoesNotDelete() = runBlocking {
        val database = buildDatabase()
        try {
            insertAccount(database, 7)
            val existing = remoteTrack("existing", "Existing")
            val coordinator = coordinator(
                database,
                FakeGateway(listOf(Result.success(RemoteServerTrackPage(listOf(existing)))))
            )
            coordinator.sync(SourceAccountId("storage:7"), scanId = "before-duplicates", pageSize = 2)

            val duplicateGateway = FakeGateway(
                listOf(
                    Result.success(RemoteServerTrackPage(listOf(existing, remoteTrack("new-1", "New 1")))),
                    Result.success(RemoteServerTrackPage(listOf(remoteTrack("new-1", "New 1"), remoteTrack("new-2", "New 2")))),
                    Result.failure(IllegalStateException("later page failed")),
                )
            )
            assertFailsWith<IllegalStateException> {
                coordinator(database, duplicateGateway).sync(
                    SourceAccountId("storage:7"),
                    scanId = "duplicate-failure",
                    pageSize = 2,
                )
            }
            val existingItem = database.sourceItemDao().findByProviderItemIds(7, listOf("existing")).single()
            assertTrue(!existingItem.isDeleted)
            assertEquals("duplicate-failure", existingItem.lastSeenScanId)
            assertTrue(database.trackSourceRefDao().findBySourceItemIds(listOf(existingItem.id)).single().isAvailable)
            assertEquals(3, database.sourceItemDao().countLiveTracksForSourceAccount(7))
        } finally {
            database.close()
        }
    }

    @Test
    fun generatedTwentyFiveThousandPagesAreConsumedBoundedByCoordinator() = runBlocking {
        val database = buildDatabase()
        try {
            insertAccount(database, 7)
            val gateway = GeneratedGateway()
            val result = coordinator(database, gateway).sync(
                SourceAccountId("storage:7"),
                scanId = "generated-25k",
                pageSize = 500,
            )
            assertEquals(25_000, result.scanned)
            assertEquals(25_000, result.added)
            assertEquals(50, gateway.pagesEmitted)
            assertEquals(500, gateway.maxPageSize)
            assertEquals(25_000, database.sourceItemDao().countLiveTracksForSourceAccount(7))
            assertEquals(25_000, database.trackDao().page(limit = 25_001, offset = 0).size)
        } finally {
            database.close()
        }
    }

    @Test
    fun ambiguousStrictCandidatesDoNotMergeIntoAnArbitraryTrack() = runBlocking {
        val database = buildDatabase()
        try {
            insertAccount(database, 7)
            insertAccount(database, 8)
            val first = remoteTrack("ambiguous-1", "Same metadata")
            val second = remoteTrack("ambiguous-2", "Same metadata")
            coordinator(
                database,
                FakeGateway(listOf(Result.success(RemoteServerTrackPage(listOf(
                    first.copy(accountId = SourceAccountId("storage:8")),
                    second.copy(accountId = SourceAccountId("storage:8")),
                )))))
            ).sync(SourceAccountId("storage:8"), scanId = "ambiguous-canonical", pageSize = 2)
            val canonicalIds = database.trackDao().page(limit = 10, offset = 0).map { it.id }.toSet()

            val result = coordinator(
                database,
                FakeGateway(listOf(Result.success(RemoteServerTrackPage(listOf(first.copy(remoteId = "ambiguous-new"))))))
            ).sync(SourceAccountId("storage:7"), scanId = "ambiguous-new", pageSize = 1)
            assertEquals(1, result.added)
            val newItem = database.sourceItemDao()
                .findByProviderItemIds(7, listOf("ambiguous-new")).single()
            val newRef = database.trackSourceRefDao().findBySourceItemIds(listOf(newItem.id)).single()
            assertTrue(newRef.trackId !in canonicalIds)
        } finally {
            database.close()
        }
    }

    @Test
    fun snapshotDeletionRestoresAvailabilityAndAlternatePreservesCanonicalTrack() = runBlocking {
        val database = buildDatabase()
        try {
            insertAccount(database, 7)
            insertAccount(database, 8)
            val canonicalRemote = remoteTrack("canonical-song", title = "Canonical title").copy(
                accountId = SourceAccountId("storage:8"),
            )
            val canonicalCoordinator = coordinator(
                database,
                FakeGateway(listOf(Result.success(RemoteServerTrackPage(listOf(canonicalRemote)))))
            )
            canonicalCoordinator.sync(SourceAccountId("storage:8"), scanId = "canonical", pageSize = 1)
            val canonicalItem = database.sourceItemDao()
                .findByProviderItemIds(8, listOf("canonical-song")).single()
            val canonicalRef = database.trackSourceRefDao().findBySourceItemIds(listOf(canonicalItem.id)).single()
            val canonicalId = canonicalRef.trackId
            assertTrue(canonicalRef.isPreferred)

            val alternate = canonicalRemote.copy(
                accountId = SourceAccountId("storage:7"),
                albumArtist = "Remote alternate artist",
            )
            val alternateGateway = FakeGateway(
                listOf(Result.success(RemoteServerTrackPage(listOf(alternate))))
            )
            val alternateCoordinator = coordinator(database, alternateGateway)
            val alternateResult = alternateCoordinator.sync(
                SourceAccountId("storage:7"),
                scanId = "alternate-1",
                pageSize = 1,
            )
            assertEquals(1, alternateResult.added)
            val alternateItem = database.sourceItemDao()
                .findByProviderItemIds(7, listOf("canonical-song")).single()
            val alternateRef = database.trackSourceRefDao().findBySourceItemIds(listOf(alternateItem.id)).single()
            assertEquals(canonicalId, alternateRef.trackId)
            assertEquals("alternate", alternateRef.role)
            assertTrue(!alternateRef.isPreferred)
            assertEquals("Canonical title", assertNotNull(database.trackDao().get(canonicalId)).title)
            assertEquals(2, database.trackSourceRefDao().findByTrackId(canonicalId).size)
            assertEquals(
                listOf("Album Artist"),
                database.metadataDao().artistNamesForAlbum(
                    requireNotNull(database.trackDao().get(canonicalId)?.albumId)
                )
            )

            alternateGateway.results = emptyList()
            val deleted = alternateCoordinator.sync(
                SourceAccountId("storage:7"),
                scanId = "alternate-2",
                pageSize = 1,
            )
            assertEquals(1, deleted.deleted)
            assertTrue(database.sourceItemDao().get(alternateItem.id)!!.isDeleted)
            assertTrue(!database.trackSourceRefDao().findBySourceItemIds(listOf(alternateItem.id)).single().isAvailable)
            assertNotNull(database.trackDao().get(canonicalId))

            alternateGateway.results = listOf(Result.success(RemoteServerTrackPage(listOf(alternate))))
            alternateCoordinator.sync(
                SourceAccountId("storage:7"),
                scanId = "alternate-3",
                pageSize = 1,
            )
            val restoredItem = database.sourceItemDao().get(alternateItem.id)!!
            assertTrue(!restoredItem.isDeleted)
            assertTrue(database.trackSourceRefDao().findBySourceItemIds(listOf(alternateItem.id)).single().isAvailable)
        } finally {
            database.close()
        }
    }

    @Test
    fun openSubsonicCoordinatorUsesDistinctDefaultScanPrefixAndExactRequestIdentity() = runBlocking {
        val database = buildDatabase()
        try {
            val accountId = SourceAccountId("storage:17")
            insertAccount(database, 17, RemoteServerKind.OpenSubsonic)
            val gateway = RecordingGateway { kind, requestedAccount ->
                check(kind == RemoteServerKind.OpenSubsonic)
                listOf(
                    Result.success(
                        RemoteServerTrackPage(
                            listOf(remoteTrack("open-subsonic-song", "OpenSubsonic title").copy(
                                accountId = requestedAccount,
                            )),
                        ),
                    ),
                )
            }
            val coordinator = OpenSubsonicLibrarySyncCoordinator(
                database,
                gateway,
                database.sourceAccountDao(),
                database.sourceItemDao(),
                database.trackSourceRefDao(),
                database.trackDao(),
                database.metadataDao(),
            )

            val result = coordinator.sync(accountId, pageSize = 1)

            assertTrue(result.scanId.startsWith("open-subsonic-"))
            assertEquals(
                listOf(GatewayRequest(RemoteServerKind.OpenSubsonic, accountId, 1)),
                gateway.requests,
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun providerNeutralCoordinatorIsolatesTwoAccountsPerServerKindAndDeduplicatesPages() = runBlocking {
        val database = buildDatabase()
        try {
            val accounts = listOf(
                RemoteServerKind.Navidrome to 11L,
                RemoteServerKind.Navidrome to 12L,
                RemoteServerKind.OpenSubsonic to 21L,
                RemoteServerKind.OpenSubsonic to 22L,
                RemoteServerKind.Emby to 31L,
                RemoteServerKind.Emby to 32L,
            )
            accounts.forEach { (kind, id) -> insertAccount(database, id, kind) }
            val gateway = RecordingGateway { kind, accountId ->
                val track = remoteTrack(
                    id = "shared-${kind.name.lowercase()}",
                    title = "${kind.name} shared song",
                ).copy(accountId = accountId)
                listOf(
                    Result.success(RemoteServerTrackPage(listOf(track, track))),
                    Result.success(RemoteServerTrackPage(listOf(track))),
                )
            }
            val coordinator = remoteServerCoordinator(database, gateway)
            val firstItems = mutableMapOf<Pair<RemoteServerKind, Long>, Pair<Long, Long>>()

            accounts.forEach { (kind, id) ->
                val accountId = SourceAccountId("storage:$id")
                val first = coordinator.sync(accountId, "matrix-$id-first", pageSize = 2)
                assertEquals(1, first.scanned)
                assertEquals(1, first.added)
                val item = database.sourceItemDao()
                    .findByProviderItemIds(id, listOf("shared-${kind.name.lowercase()}"))
                    .single()
                val ref = database.trackSourceRefDao().findBySourceItemIds(listOf(item.id)).single()
                firstItems[kind to id] = item.id to ref.trackId

                val repeated = coordinator.sync(accountId, "matrix-$id-second", pageSize = 2)
                assertEquals(1, repeated.scanned)
                assertEquals(0, repeated.added)
                assertEquals(1, repeated.unchanged)
                val repeatedItem = database.sourceItemDao()
                    .findByProviderItemIds(id, listOf("shared-${kind.name.lowercase()}"))
                    .single()
                val repeatedRef = database.trackSourceRefDao()
                    .findBySourceItemIds(listOf(repeatedItem.id)).single()
                assertEquals(firstItems.getValue(kind to id), repeatedItem.id to repeatedRef.trackId)
            }

            for ((kind, firstId, secondId) in listOf(
                Triple(RemoteServerKind.Navidrome, 11L, 12L),
                Triple(RemoteServerKind.OpenSubsonic, 21L, 22L),
                Triple(RemoteServerKind.Emby, 31L, 32L),
            )) {
                val first = firstItems.getValue(kind to firstId)
                val second = firstItems.getValue(kind to secondId)
                assertTrue(first.first != second.first)
                assertEquals(first.second, second.second)
            }
            assertEquals(
                accounts.flatMap { (kind, id) ->
                    List(2) { GatewayRequest(kind, SourceAccountId("storage:$id"), 2) }
                },
                gateway.requests,
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun providerNeutralCoordinatorFailsClosedForAccountMismatchAndNonServerAccounts() = runBlocking {
        val database = buildDatabase()
        try {
            insertAccount(database, 90, RemoteServerKind.Navidrome)
            insertAccount(database, 91, RemoteServerKind.OpenSubsonic)
            insertRawAccount(database, 92, ProviderTypes.WebDav)
            val gateway = RecordingGateway { kind, accountId ->
                val responseAccount = if (accountId == SourceAccountId("storage:91")) {
                    SourceAccountId("storage:999")
                } else {
                    accountId
                }
                listOf(Result.success(RemoteServerTrackPage(listOf(
                    remoteTrack("account-${accountId.value}", "Account scoped").copy(
                        accountId = responseAccount,
                    ),
                ))))
            }
            val coordinator = remoteServerCoordinator(database, gateway)

            coordinator.sync(SourceAccountId("storage:90"), "valid-account", 1)
            val validItem = database.sourceItemDao()
                .findByProviderItemIds(90, listOf("account-storage:90")).single()
            for (invalid in listOf(
                SourceAccountId("server:90"),
                SourceAccountId("storage:999"),
                SourceAccountId("storage:92"),
            )) {
                assertFailsWith<IllegalStateException> {
                    coordinator.sync(invalid, "must-not-request", 1)
                }
            }
            assertEquals(1, gateway.requests.size)

            assertFailsWith<IllegalStateException> {
                coordinator.sync(SourceAccountId("storage:91"), "mismatched-response", 1)
            }
            assertEquals(2, gateway.requests.size)
            assertEquals(1, database.sourceItemDao().countLiveTracksForSourceAccount(90))
            assertEquals(validItem.id, database.sourceItemDao().get(validItem.id)?.id)
            assertEquals(0, database.sourceItemDao().countLiveTracksForSourceAccount(91))
        } finally {
            database.close()
        }
    }

    @Test
    fun sevenRemoteSourcesConvergeAndOneServerDeletionPreservesPlaybackAndIdentity() = runBlocking {
        val database = buildDatabase()
        try {
            val canonicalTrackId = seedCanonicalStorageSources(database)
            val serverAccounts = listOf(
                RemoteServerKind.Navidrome to 201L,
                RemoteServerKind.OpenSubsonic to 202L,
                RemoteServerKind.Emby to 203L,
            )
            serverAccounts.forEach { (kind, id) -> insertAccount(database, id, kind) }
            val availableAccounts = serverAccounts.mapTo(mutableSetOf()) {
                SourceAccountId("storage:${it.second}")
            }
            val gateway = RecordingGateway { kind, accountId ->
                if (accountId !in availableAccounts) {
                    emptyList()
                } else {
                    listOf(Result.success(RemoteServerTrackPage(listOf(
                        remoteTrack("seven-${kind.name.lowercase()}", "Seven source song").copy(
                            accountId = accountId,
                            artist = "Seven Artist",
                            album = "Seven Album",
                            albumArtist = "Seven Album Artist",
                            durationMs = 210_000,
                        ),
                    ))))
                }
            }
            val coordinator = remoteServerCoordinator(database, gateway)
            serverAccounts.forEach { (_, id) ->
                coordinator.sync(SourceAccountId("storage:$id"), "seven-$id", 1)
            }

            assertEquals(listOf(canonicalTrackId), database.trackDao().page(10, 0).map(TrackEntity::id))
            val allRefs = database.trackSourceRefDao().findByTrackId(canonicalTrackId)
            assertEquals(7, allRefs.size)
            assertEquals(7, allRefs.map(TrackSourceRefEntity::sourceItemId).toSet().size)
            val expectedProviders = setOf(
                ProviderTypes.WebDav,
                ProviderTypes.Smb,
                ProviderTypes.OneDrive,
                ProviderTypes.OpenList,
                ProviderTypes.Navidrome,
                ProviderTypes.OpenSubsonic,
                ProviderTypes.Emby,
            )
            assertEquals(
                expectedProviders,
                database.trackSourceRefDao().playbackCandidates(canonicalTrackId)
                    .map { it.account.providerType }.toSet(),
            )
            assertEquals(6, allRefs.count { it.matchMethod == TrackMatchMethods.StrictMetadata })

            val openSubsonicAccount = SourceAccountId("storage:202")
            val openSubsonicItem = database.sourceItemDao()
                .findByProviderItemIds(202, listOf("seven-opensubsonic")).single()
            val openSubsonicRef = database.trackSourceRefDao()
                .findBySourceItemIds(listOf(openSubsonicItem.id)).single()
            availableAccounts.remove(openSubsonicAccount)
            val deleted = coordinator.sync(openSubsonicAccount, "seven-delete", 1)
            assertEquals(1, deleted.deleted)
            assertTrue(database.sourceItemDao().get(openSubsonicItem.id)!!.isDeleted)
            assertTrue(!database.trackSourceRefDao()
                .findBySourceItemIds(listOf(openSubsonicItem.id)).single().isAvailable)
            assertEquals(listOf(canonicalTrackId), database.trackDao().page(10, 0).map(TrackEntity::id))
            assertEquals(6, database.trackSourceRefDao().playbackCandidates(canonicalTrackId).size)

            availableAccounts.add(openSubsonicAccount)
            coordinator.sync(openSubsonicAccount, "seven-restore", 1)
            val restoredItem = database.sourceItemDao()
                .findByProviderItemIds(202, listOf("seven-opensubsonic")).single()
            val restoredRef = database.trackSourceRefDao()
                .findBySourceItemIds(listOf(restoredItem.id)).single()
            assertEquals(openSubsonicItem.id, restoredItem.id)
            assertEquals(openSubsonicRef.trackId, restoredRef.trackId)
            assertEquals(canonicalTrackId, restoredRef.trackId)
            assertTrue(!restoredItem.isDeleted && restoredRef.isAvailable)
            assertEquals(7, database.trackSourceRefDao().playbackCandidates(canonicalTrackId).size)
        } finally {
            database.close()
        }
    }

    @Test
    fun embyCoordinatorUsesProviderScopedMetadataAndSnapshotLifecycle() = runBlocking {
        val database = buildDatabase()
        try {
            insertAccount(database, 7, RemoteServerKind.Emby)
            val initial = remoteTrack("emby-song", "Emby title").copy(
                accountId = SourceAccountId("storage:7"),
                imageTag = "image-tag-1",
                sourceMediaId = "media-1",
                albumId = "album-1",
                userData = io.github.julystar.musicapp.source.api.RemoteServerUserData(
                    isFavorite = true,
                    playCount = 3,
                    lastPlayedDate = "2024-02-01",
                    played = true,
                ),
                artists = listOf("Artist", "Featured"),
                genres = listOf("Genre", "Ambient"),
                audioProperties = SourceAudioProperties(
                    codec = "FLAC", container = "FLAC", bitrateKbps = 900,
                    sampleRateHz = 96_000, bitDepth = 24, channels = 2,
                    channelLayout = "stereo", lossless = true,
                ),
            )
            val gateway = FakeGateway(
                listOf(Result.success(RemoteServerTrackPage(listOf(initial)))),
                supportedKind = RemoteServerKind.Emby,
            )
            val coordinator = EmbyLibrarySyncCoordinator(
                database = database,
                gateway = gateway,
                sourceAccountDao = database.sourceAccountDao(),
                sourceItemDao = database.sourceItemDao(),
                trackSourceRefDao = database.trackSourceRefDao(),
                trackDao = database.trackDao(),
                metadataDao = database.metadataDao(),
            )
            val first = coordinator.sync(SourceAccountId("storage:7"), "emby-1", 1)
            assertEquals(1, first.added)
            val item = database.sourceItemDao().findByProviderItemIds(7, listOf("emby-song")).single()
            val properties = database.sourceItemDao().propertiesForItems(listOf(item.id))
            assertEquals("image-tag-1", properties.single { it.propertyKey == "imageTag" }.stringValue)
            assertEquals("media-1", properties.single { it.propertyKey == "sourceMediaId" }.stringValue)
            assertEquals("album-1", properties.single { it.propertyKey == "albumId" }.stringValue)
            assertEquals(true, properties.single { it.propertyKey == "embyIsFavorite" }.booleanValue)
            assertEquals(3L, properties.single { it.propertyKey == "embyPlayCount" }.longValue)
            assertEquals("2024-02-01", properties.single { it.propertyKey == "embyLastPlayedDate" }.stringValue)
            assertEquals(true, properties.single { it.propertyKey == "embyPlayed" }.booleanValue)
            val firstRef = database.trackSourceRefDao().findBySourceItemIds(listOf(item.id)).single()
            assertEquals("FLAC", firstRef.codec)
            assertEquals("FLAC", firstRef.container)
            assertEquals(900, firstRef.bitRate)
            assertEquals(96_000, firstRef.sampleRate)
            assertEquals(24, firstRef.bitsPerSample)
            assertEquals(2, firstRef.channels)
            assertEquals("stereo", firstRef.channelLayout)
            assertEquals(true, firstRef.lossless)
            assertEquals(listOf("Artist", "Featured"), database.metadataDao().artistNamesForTrack(
                firstRef.trackId,
            ))

            assertEquals(1, coordinator.sync(SourceAccountId("storage:7"), "emby-unchanged", 1).unchanged)
            gateway.results = listOf(Result.success(RemoteServerTrackPage(listOf(initial.copy(
                imageTag = "image-tag-2",
                sourceMediaId = "media-2",
                albumId = "album-2",
                userData = initial.userData?.copy(isFavorite = false, playCount = 4),
            )))))
            assertEquals(1, coordinator.sync(SourceAccountId("storage:7"), "emby-properties-modified", 1).modified)
            val modifiedProperties = database.sourceItemDao().propertiesForItems(listOf(item.id))
            assertEquals("image-tag-2", modifiedProperties.single { it.propertyKey == "imageTag" }.stringValue)
            assertEquals("media-2", modifiedProperties.single { it.propertyKey == "sourceMediaId" }.stringValue)
            assertEquals(false, modifiedProperties.single { it.propertyKey == "embyIsFavorite" }.booleanValue)

            gateway.results = listOf(Result.success(RemoteServerTrackPage(listOf(initial.copy(title = "Changed")))))
            assertEquals(1, coordinator.sync(SourceAccountId("storage:7"), "emby-2", 1).modified)
            gateway.results = emptyList()
            assertEquals(1, coordinator.sync(SourceAccountId("storage:7"), "emby-3", 1).deleted)
            assertTrue(database.sourceItemDao().get(item.id)!!.isDeleted)
            gateway.results = listOf(Result.success(RemoteServerTrackPage(listOf(initial))))
            coordinator.sync(SourceAccountId("storage:7"), "emby-4", 1)
            assertTrue(!database.sourceItemDao().get(item.id)!!.isDeleted)
        } finally {
            database.close()
        }
    }

    @Test
    fun embyOptionalPropertiesAreIdempotentAndClearWhenRemoteValuesDisappear() = runBlocking {
        val database = buildDatabase()
        try {
            insertAccount(database, 7, RemoteServerKind.Emby)
            val bare = remoteTrack("emby-bare", "Bare").copy(
                imageTag = null,
                sourceMediaId = null,
                albumId = null,
                userData = null,
                audioProperties = null,
            )
            val gateway = FakeGateway(
                listOf(Result.success(RemoteServerTrackPage(listOf(bare)))),
                supportedKind = RemoteServerKind.Emby,
            )
            val coordinator = EmbyLibrarySyncCoordinator(
                database, gateway, database.sourceAccountDao(), database.sourceItemDao(),
                database.trackSourceRefDao(), database.trackDao(), database.metadataDao(),
            )
            coordinator.sync(SourceAccountId("storage:7"), "bare-1", 1)
            assertEquals(1, coordinator.sync(SourceAccountId("storage:7"), "bare-2", 1).unchanged)

            gateway.results = listOf(Result.success(RemoteServerTrackPage(listOf(
                bare.copy(imageTag = "tag", sourceMediaId = "media", albumId = "album", userData =
                    io.github.julystar.musicapp.source.api.RemoteServerUserData(isFavorite = true)),
            ))))
            assertEquals(1, coordinator.sync(SourceAccountId("storage:7"), "bare-3", 1).modified)
            gateway.results = listOf(Result.success(RemoteServerTrackPage(listOf(bare))))
            assertEquals(1, coordinator.sync(SourceAccountId("storage:7"), "bare-4", 1).modified)
            val item = database.sourceItemDao().findByProviderItemIds(7, listOf("emby-bare")).single()
            assertTrue(database.sourceItemDao().propertiesForItems(listOf(item.id)).none {
                it.propertyKey in setOf("imageTag", "sourceMediaId", "albumId", "embyIsFavorite")
            })
        } finally {
            database.close()
        }
    }

    @Test
    fun embyAccountsIsolateSameRemoteIdAndDeletionKeepsOtherRef() = runBlocking {
        val database = buildDatabase()
        try {
            insertAccount(database, 7, RemoteServerKind.Emby)
            insertAccount(database, 8, RemoteServerKind.Emby)
            val shared = remoteTrack("same-remote-id", "Shared")
            val gateway = FakeGateway(
                listOf(Result.success(RemoteServerTrackPage(listOf(shared)))),
                supportedKind = RemoteServerKind.Emby,
            )
            fun emby() = EmbyLibrarySyncCoordinator(
                database, gateway, database.sourceAccountDao(), database.sourceItemDao(),
                database.trackSourceRefDao(), database.trackDao(), database.metadataDao(),
            )
            val first = emby()
            first.sync(SourceAccountId("storage:7"), "isolation-7", 1)
            gateway.results = listOf(Result.success(RemoteServerTrackPage(listOf(
                shared.copy(accountId = SourceAccountId("storage:8")),
            ))))
            emby().sync(SourceAccountId("storage:8"), "isolation-8", 1)
            val item7 = database.sourceItemDao().findByProviderItemIds(7, listOf("same-remote-id")).single()
            val item8 = database.sourceItemDao().findByProviderItemIds(8, listOf("same-remote-id")).single()
            assertTrue(item7.id != item8.id)
            val trackId = database.trackSourceRefDao().findBySourceItemIds(listOf(item7.id)).single().trackId
            assertEquals(trackId, database.trackSourceRefDao().findBySourceItemIds(listOf(item8.id)).single().trackId)

            gateway.results = emptyList()
            assertEquals(1, first.sync(SourceAccountId("storage:7"), "isolation-delete", 1).deleted)
            assertTrue(database.sourceItemDao().get(item7.id)!!.isDeleted)
            assertTrue(database.trackSourceRefDao().findBySourceItemIds(listOf(item7.id)).single().isAvailable.not())
            assertTrue(database.trackSourceRefDao().findBySourceItemIds(listOf(item8.id)).single().isAvailable)
            assertNotNull(database.trackDao().get(trackId))

            insertAccount(database, 9, RemoteServerKind.Navidrome)
            assertFailsWith<IllegalStateException> {
                emby().sync(SourceAccountId("storage:9"), "provider-guard", 1)
            }
            Unit
        } finally {
            database.close()
        }
    }

    private fun coordinator(database: AppDatabase, gateway: RemoteServerGateway) =
        NavidromeLibrarySyncCoordinator(
            database = database,
            gateway = gateway,
            sourceAccountDao = database.sourceAccountDao(),
            sourceItemDao = database.sourceItemDao(),
            trackSourceRefDao = database.trackSourceRefDao(),
            trackDao = database.trackDao(),
            metadataDao = database.metadataDao(),
        )

    private fun remoteServerCoordinator(
        database: AppDatabase,
        gateway: RemoteServerGateway,
    ) = RemoteServerLibrarySyncCoordinator(
        sourceAccountDao = database.sourceAccountDao(),
        navidrome = coordinator(database, gateway),
        openSubsonic = OpenSubsonicLibrarySyncCoordinator(
            database,
            gateway,
            database.sourceAccountDao(),
            database.sourceItemDao(),
            database.trackSourceRefDao(),
            database.trackDao(),
            database.metadataDao(),
        ),
        emby = EmbyLibrarySyncCoordinator(
            database,
            gateway,
            database.sourceAccountDao(),
            database.sourceItemDao(),
            database.trackSourceRefDao(),
            database.trackDao(),
            database.metadataDao(),
        ),
    )

    private suspend fun insertAccount(
        database: AppDatabase,
        id: Long,
        kind: RemoteServerKind = RemoteServerKind.Navidrome,
    ) {
        database.sourceAccountDao().upsert(
            SourceAccountEntity(
                id = id,
                providerType = when (kind) {
                    RemoteServerKind.Navidrome -> ProviderTypes.Navidrome
                    RemoteServerKind.OpenSubsonic -> ProviderTypes.OpenSubsonic
                    RemoteServerKind.Emby -> ProviderTypes.Emby
                },
                displayName = kind.name,
                endpoint = "https://${kind.name.lowercase()}.example",
                externalAccountId = if (kind == RemoteServerKind.Emby) "emby-user-$id" else null,
                credentialRef = "${kind.name.lowercase()}-$id",
                priority = 0,
                enabled = true,
                createdAt = 1,
                updatedAt = 1,
            ),
        )
    }

    private suspend fun insertRawAccount(
        database: AppDatabase,
        id: Long,
        providerType: String,
    ) {
        database.sourceAccountDao().upsert(
            SourceAccountEntity(
                id = id,
                providerType = providerType,
                displayName = providerType,
                endpoint = "https://$providerType.example",
                externalAccountId = null,
                credentialRef = "$providerType-$id",
                priority = 0,
                enabled = true,
                createdAt = 1,
                updatedAt = 1,
            ),
        )
    }

    private suspend fun seedCanonicalStorageSources(database: AppDatabase): Long {
        val storageAccounts = listOf(
            101L to ProviderTypes.WebDav,
            102L to ProviderTypes.Smb,
            103L to ProviderTypes.OneDrive,
            104L to ProviderTypes.OpenList,
        )
        storageAccounts.forEach { (id, providerType) ->
            insertRawAccount(database, id, providerType)
        }
        val albumId = database.metadataDao().insertAlbums(
            listOf(AlbumEntity(
                name = "Seven Album",
                normalizedName = "seven album",
                sortName = null,
                year = null,
                artworkId = null,
            )),
        ).single()
        val trackId = 7_000L
        database.trackDao().upsertAll(listOf(
            TrackEntity(
                id = trackId,
                title = "Seven source song",
                sortTitle = null,
                albumId = albumId,
                albumArtist = "Seven Album Artist",
                composer = null,
                comment = null,
                grouping = null,
                durationMs = 210_000,
                discNumber = null,
                discTotal = null,
                trackNumber = null,
                trackTotal = null,
                year = null,
                date = null,
                sampleRate = 96_000,
                bitRate = 900,
                bitsPerSample = 24,
                channels = 2,
                channelLayout = "stereo",
                codec = "flac",
                container = "flac",
                lossless = true,
                createdAt = 1,
                updatedAt = 1,
                artist = "Seven Artist",
            ),
        ))
        val refs = storageAccounts.mapIndexed { index, (accountId, providerType) ->
            val sourceItemId = database.sourceItemDao().upsertAll(listOf(
                SourceItemEntity(
                    sourceAccountId = accountId,
                    libraryRootId = null,
                    itemType = SourceItemTypes.Track,
                    providerItemId = "seven-$providerType",
                    parentProviderItemId = null,
                    canonicalPath = "/seven-$providerType.flac",
                    displayPath = "/seven-$providerType.flac",
                    displayName = "Seven source song.flac",
                    mimeType = "audio/flac",
                    sizeBytes = 1_000,
                    etag = "etag-$providerType",
                    revision = null,
                    createdAtRemote = 1,
                    modifiedAtRemote = 1,
                    contentHash = null,
                    audioFingerprint = null,
                    isDeleted = false,
                    firstSyncedAt = 1,
                    lastSyncedAt = 1,
                    lastSeenScanId = "storage-seed",
                ),
            )).single()
            TrackSourceRefEntity(
                trackId = trackId,
                sourceItemId = sourceItemId,
                role = if (index == 0) TrackSourceRoles.Primary else TrackSourceRoles.Alternate,
                matchMethod = if (index == 0) {
                    TrackMatchMethods.SourceIdentity
                } else {
                    TrackMatchMethods.StrictMetadata
                },
                matchConfidence = if (index == 0) {
                    MATCH_CONFIDENCE_EXACT
                } else {
                    MATCH_CONFIDENCE_STRICT_METADATA
                },
                isPreferred = index == 0,
                isAvailable = true,
                isDownloaded = false,
                playable = true,
                downloadable = true,
                codec = "flac",
                container = "flac",
                bitRate = 900,
                sampleRate = 96_000,
                bitsPerSample = 24,
                channels = 2,
                channelLayout = "stereo",
                lossless = true,
                createdAt = 1,
                updatedAt = 1,
            )
        }
        database.trackSourceRefDao().upsertAll(refs)
        return trackId
    }

    private fun remoteTrack(id: String, title: String) = RemoteServerTrack(
        accountId = SourceAccountId("storage:7"),
        remoteId = id,
        title = title,
        artist = "Artist",
        album = "Album",
        albumArtist = "Album Artist",
        genre = "Genre",
        year = 2024,
        track = 3,
        discNumber = 2,
        suffix = "flac",
        durationMs = 245_000,
        streamUrl = "https://navidrome.example/stream?id=$id&token=secret",
        coverUrl = "https://navidrome.example/cover?id=$id&password=secret",
        mimeType = "audio/flac",
        bitRate = 900,
        sampleRate = 96_000,
        bitDepth = 24,
        channelCount = 2,
        coverArtId = "cover-art-$id",
    )

    private fun buildDatabase(): AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase> {
        AppDatabaseConstructor.initialize()
    }
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
}

private data class GatewayRequest(
    val kind: RemoteServerKind,
    val accountId: SourceAccountId,
    val pageSize: Int,
)

private class RecordingGateway(
    private val responses: (
        RemoteServerKind,
        SourceAccountId,
    ) -> List<Result<RemoteServerTrackPage>>,
) : RemoteServerGateway {
    val requests = mutableListOf<GatewayRequest>()

    override suspend fun authenticate(configuration: RemoteServerSourceConfiguration): SourceAuthResult =
        SourceAuthResult.Success

    override fun trackPages(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        query: String?,
        pageSize: Int,
    ): Flow<Result<RemoteServerTrackPage>> {
        requests += GatewayRequest(kind, accountId, pageSize)
        return responses(kind, accountId).asFlow()
    }

    override suspend fun playback(
        kind: RemoteServerKind,
        encodedRemoteId: String,
    ): SourcePlaybackResult = SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unavailable)
}

private class FakeGateway(
    var results: List<Result<RemoteServerTrackPage>>,
    private val supportedKind: RemoteServerKind = RemoteServerKind.Navidrome,
) : RemoteServerGateway {
    override suspend fun authenticate(configuration: RemoteServerSourceConfiguration): SourceAuthResult =
        SourceAuthResult.Failure(io.github.julystar.musicapp.source.api.SourceAuthFailureReason.Unavailable)

    override fun trackPages(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        query: String?,
        pageSize: Int,
    ): Flow<Result<RemoteServerTrackPage>> {
        check(kind == supportedKind)
        return results.asFlow()
    }

    override suspend fun playback(
        kind: RemoteServerKind,
        encodedRemoteId: String,
    ): SourcePlaybackResult = SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unavailable)
}

private class GeneratedGateway : RemoteServerGateway {
    var pagesEmitted = 0
    var maxPageSize = 0

    override suspend fun authenticate(configuration: RemoteServerSourceConfiguration): SourceAuthResult =
        SourceAuthResult.Success

    override fun trackPages(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        query: String?,
        pageSize: Int,
    ): Flow<Result<RemoteServerTrackPage>> = flow {
        check(kind == RemoteServerKind.Navidrome)
        for (page in 0 until 50) {
            val tracks = (page * 500 until (page + 1) * 500).map { index ->
                remoteTrackForGeneratedId(index)
            }
            pagesEmitted++
            maxPageSize = maxOf(maxPageSize, tracks.size)
            emit(Result.success(RemoteServerTrackPage(tracks, page * 500)))
        }
    }

    override suspend fun playback(
        kind: RemoteServerKind,
        encodedRemoteId: String,
    ): SourcePlaybackResult = SourcePlaybackResult.Failure(SourcePlaybackFailureReason.Unavailable)
}

private fun remoteTrackForGeneratedId(index: Int): RemoteServerTrack = RemoteServerTrack(
    accountId = SourceAccountId("storage:7"),
    remoteId = "generated-$index-音乐😀",
    title = "Generated $index",
    artist = "Generated Artist",
    album = "Generated Album",
    albumArtist = "Generated Album Artist",
    genre = "Generated Genre",
    durationMs = 180_000L + index,
    streamUrl = "https://navidrome.example/stream?id=generated-$index",
    mimeType = "audio/flac",
    suffix = "flac",
    bitRate = 800,
    sampleRate = 44_100,
    bitDepth = 16,
    channelCount = 2,
)
