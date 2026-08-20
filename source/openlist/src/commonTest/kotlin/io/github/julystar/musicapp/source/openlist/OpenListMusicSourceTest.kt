package io.github.julystar.musicapp.source.openlist

import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.MediaType
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.SourceId
import io.github.julystar.musicapp.source.api.OpenListAuthenticator
import io.github.julystar.musicapp.source.api.OpenListBrowseClient
import io.github.julystar.musicapp.source.api.OpenListBrowseEntry
import io.github.julystar.musicapp.source.api.OpenListBrowsePage
import io.github.julystar.musicapp.source.api.OpenListBrowsePageResult
import io.github.julystar.musicapp.source.api.LegacyStorageKind
import io.github.julystar.musicapp.source.api.LegacyStoragePlaybackResolver
import io.github.julystar.musicapp.source.api.LegacyStorageSearchProvider
import io.github.julystar.musicapp.source.api.BuiltInSourceIds
import io.github.julystar.musicapp.source.api.LocalSourceConfiguration
import io.github.julystar.musicapp.source.api.OpenListSourceConfiguration
import io.github.julystar.musicapp.source.api.PlaybackResource
import io.github.julystar.musicapp.source.api.SourceAuthResult
import io.github.julystar.musicapp.source.api.SourceListResult
import io.github.julystar.musicapp.source.api.SourceNodeType
import io.github.julystar.musicapp.source.api.SourcePlaybackResult
import io.github.julystar.musicapp.source.api.SourceSearchResult
import io.github.julystar.musicapp.source.api.legacyStorageTrackMediaId
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.toList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OpenListMusicSourceTest {
    @Test
    fun descriptorAdvertisesBrowseAndStreamOnlyAndUsesRoot() {
        val source = source()

        assertEquals(BuiltInSourceIds.OpenList, source.descriptor.id)
        assertEquals("OpenList", source.descriptor.displayName)
        assertEquals(
            setOf(
                io.github.julystar.musicapp.source.api.SourceCapability.Browse,
                io.github.julystar.musicapp.source.api.SourceCapability.Stream,
            ),
            source.capabilities,
        )
        assertEquals("/", source.rootDirectoryRemoteId)
    }

    @Test
    fun pagedBrowsePreservesOpaquePathsDeduplicatesAndClassifies() = runTest {
        val calls = mutableListOf<Pair<Int, String>>()
        val pages = mapOf(
            1 to OpenListBrowsePage(
                entries = listOf(
                    OpenListBrowseEntry("a b", 1, false, 3),
                    OpenListBrowseEntry("音乐🎵.lrc", 2, false, 4),
                ),
                total = 4,
            ),
            2 to OpenListBrowsePage(
                entries = listOf(
                    OpenListBrowseEntry("a b", 1, false, 3),
                    OpenListBrowseEntry("cover#?.jpg", 3, false, 5),
                ),
                total = 4,
            ),
        )
        val source = source(
            browseClient = OpenListBrowseClient { _, path, page, _ ->
                calls += page to path
                OpenListBrowsePageResult.Success(pages.getValue(page))
            },
        )

        val results = source.listPages(SourceAccountId("openlist:1"), "/目录", 2).toList()
        val nodes = results.filterIsInstance<SourceListResult.Success>().flatMap { it.nodes }

        assertEquals(listOf(1 to "/目录", 2 to "/目录"), calls)
        assertEquals(listOf("/目录/a b", "/目录/音乐🎵.lrc", "/目录/cover#?.jpg"), nodes.map { it.path })
        assertEquals(nodes.map { it.path }, nodes.map { it.nodeId })
        assertEquals(nodes.map { it.path }, nodes.map { it.remoteId })
        assertEquals(listOf("/目录", "/目录", "/目录"), nodes.map { it.parentNodeId })
        assertEquals(SourceNodeType.Track, nodes[0].type)
        assertEquals(SourceNodeType.Lyric, nodes[1].type)
        assertEquals(SourceNodeType.Image, nodes[2].type)
        assertEquals(null, nodes[0].mimeType)
        assertEquals("text/plain", nodes[1].mimeType)
    }

    @Test
    fun rawDirectoryWithPercentUnicodeAndBackslashRoundTripsThroughBrowse() = runTest {
        val rawDirectory = "/音乐/%25 #? 😀\\folder"
        val rawChild = "子\\song.flac"
        val calls = mutableListOf<Pair<Int, String>>()
        val source = source(
            browseClient = OpenListBrowseClient { _, path, page, pageSize ->
                calls += page to path
                assertEquals(10, pageSize)
                OpenListBrowsePageResult.Success(
                    OpenListBrowsePage(
                        entries = listOf(OpenListBrowseEntry(rawChild, 7, false, 3)),
                        total = 1,
                    ),
                )
            },
        )

        val result = source.listPages(SourceAccountId("openlist:1"), rawDirectory, 10).toList()
        val node = (result.single() as SourceListResult.Success).nodes.single()

        assertEquals(listOf(1 to rawDirectory), calls)
        assertEquals("$rawDirectory/$rawChild", node.path)
        assertEquals(node.path, node.nodeId)
        assertEquals(node.path, node.remoteId)
        assertEquals(rawDirectory, node.parentNodeId)
        assertEquals(rawChild, node.name)
    }

    @Test
    fun allNodeKindsNegativeSizeAndUnicodeNamesRemainExact() = runTest {
        val names = listOf("日本語", "한국어", "é", "e\u0301")
        val source = source(
            browseClient = OpenListBrowseClient { _, path, page, _ ->
                assertEquals("/nested/日本語 # % ? 😀", path)
                assertEquals(1, page)
                OpenListBrowsePageResult.Success(
                    OpenListBrowsePage(
                        entries = listOf(
                            OpenListBrowseEntry("folder", -1, true, 1),
                            OpenListBrowseEntry("song", -1, false, 3),
                            OpenListBrowseEntry("image", 1, false, 5),
                            OpenListBrowseEntry("words.lrc", 2, false, 4),
                            OpenListBrowseEntry("unknown.bin", 3, false, 0),
                        ) + names.map { OpenListBrowseEntry(it, 1, false, 0) },
                        total = 9,
                    ),
                )
            },
        )

        val result = source.listPages(
            SourceAccountId("openlist:1"),
            "/nested/日本語 # % ? 😀",
            9,
        ).toList().single() as SourceListResult.Success

        assertEquals(
            listOf(
                SourceNodeType.Folder,
                SourceNodeType.Track,
                SourceNodeType.Image,
                SourceNodeType.Lyric,
                SourceNodeType.Other,
                SourceNodeType.Other,
                SourceNodeType.Other,
                SourceNodeType.Other,
                SourceNodeType.Other,
            ),
            result.nodes.map { it.type },
        )
        assertEquals(null, result.nodes[0].sizeBytes)
        assertEquals(names.map { "/nested/日本語 # % ? 😀/$it" }, result.nodes.drop(5).map { it.path })
        assertEquals(9, result.nodes.map { it.path }.toSet().size)
    }

    @Test
    fun pagedBrowseRejectsTraversalAndStopsOnRepeatedRawPage() = runTest {
        var requests = 0
        val repeated = OpenListBrowsePage(
            entries = listOf(OpenListBrowseEntry("../escape", 1, false, 3)),
            total = 100,
        )
        val source = source(
            browseClient = OpenListBrowseClient { _, _, _, _ ->
                requests++
                OpenListBrowsePageResult.Success(repeated)
            },
        )

        val results = source.listPages(SourceAccountId("openlist:1"), "/", 1).toList()

        assertEquals(2, requests)
        assertEquals(listOf(emptyList()), results.filterIsInstance<SourceListResult.Success>().map { it.nodes })
    }

    @Test
    fun emptyAndShortPagesTerminateEvenWhenTotalIsInconsistent() = runTest {
        var emptyCalls = 0
        val emptySource = source(
            browseClient = OpenListBrowseClient { _, _, _, _ ->
                emptyCalls++
                OpenListBrowsePageResult.Success(OpenListBrowsePage(emptyList(), 99))
            },
        )
        val emptyResult = emptySource.listPages(SourceAccountId("openlist:1"), "/", 2).toList()
        assertEquals(1, emptyCalls)
        assertEquals(listOf(emptyList()), emptyResult.filterIsInstance<SourceListResult.Success>().map { it.nodes })

        var shortCalls = 0
        val shortSource = source(
            browseClient = OpenListBrowseClient { _, _, _, _ ->
                shortCalls++
                OpenListBrowsePageResult.Success(
                    OpenListBrowsePage(listOf(OpenListBrowseEntry("one", 1, false, 3)), 99),
                )
            },
        )
        val shortResult = shortSource.listPages(SourceAccountId("openlist:1"), "/", 2).toList()
        assertEquals(1, shortCalls)
        assertEquals(1, shortResult.filterIsInstance<SourceListResult.Success>().single().nodes.size)
    }

    @Test
    fun invalidParentIsReportedWithoutCallingBrowseClient() = runTest {
        var calls = 0
        val source = source(
            browseClient = OpenListBrowseClient { _, _, _, _ ->
                calls++
                error("invalid parent must not reach network")
            },
        )

        for (path in listOf("relative", "/trailing/", "/empty//segment", "/dot/../x", "/nul\u0000")) {
            val result = source.list(SourceAccountId("openlist:1"), path)
            assertEquals(
                SourceListResult.Failure(io.github.julystar.musicapp.source.api.SourceListFailureReason.InvalidAddress),
                result,
            )
        }
        assertEquals(0, calls)
    }

    @Test
    fun listAggregatesPagesButReturnsTheFirstFailure() = runTest {
        var page = 0
        val source = source(
            browseClient = OpenListBrowseClient { _, _, _, _ ->
                page++
                if (page == 1) {
                    OpenListBrowsePageResult.Success(
                        OpenListBrowsePage(
                            entries = List(100) { index ->
                                OpenListBrowseEntry("first-$index.mp3", 4, false, 3)
                            },
                            total = 101,
                        ),
                    )
                } else {
                    OpenListBrowsePageResult.Failure(
                        io.github.julystar.musicapp.source.api.SourceListFailureReason.Unavailable,
                    )
                }
            },
        )

        val result = source.list(SourceAccountId("openlist:1"), "/")

        assertEquals(
            SourceListResult.Failure(io.github.julystar.musicapp.source.api.SourceListFailureReason.Unavailable),
            result,
        )
        assertEquals(2, page)
    }

    @Test
    fun configurationRedactsPasswordAndRejectsOtherConfiguration() = runTest {
        val configuration = OpenListSourceConfiguration(
            alias = "OpenList",
            address = "https://openlist.example",
            username = "alice",
            password = "secret",
            isGuest = false,
        )
        val text = configuration.toString()
        assertFalse("secret" in text)
        assertTrue("<redacted>" in text)
        assertIs<SourceAuthResult.Success>(source().authenticate(configuration))
        assertEquals(
            SourceAuthResult.Failure(
                io.github.julystar.musicapp.source.api.SourceAuthFailureReason.UnsupportedConfiguration
            ),
            source().authenticate(LocalSourceConfiguration(alias = "wrong")),
        )
    }

    @Test
    fun delegatesUseOpenListKindAndSourceIdGuards() = runTest {
        var authenticated: OpenListSourceConfiguration? = null
        var searchedKind: LegacyStorageKind? = null
        var searchedSource: SourceId? = null
        var resolvedKind: LegacyStorageKind? = null
        var resolvedPath: String? = null
        val source = source(
            authenticator = OpenListAuthenticator {
                authenticated = it
                SourceAuthResult.Success
            },
            searchProvider = LegacyStorageSearchProvider { _, _, _, kind, sourceId ->
                searchedKind = kind
                searchedSource = sourceId
                SourceSearchResult.Success(emptyList())
            },
            playbackResolver = object : LegacyStoragePlaybackResolver {
                override suspend fun resolve(
                    accountId: SourceAccountId,
                    path: String,
                    expectedStorageKind: LegacyStorageKind,
                ): SourcePlaybackResult {
                    resolvedKind = expectedStorageKind
                    resolvedPath = path
                    return SourcePlaybackResult.Success(PlaybackResource("file:///track"))
                }

                override suspend fun release(uri: String) = Unit
                override suspend fun releaseAll() = Unit
            },
        )
        val accountId = SourceAccountId("storage:42")
        source.authenticate(
            OpenListSourceConfiguration(
                alias = "OpenList",
                address = "address",
                username = "user",
                password = "password",
                isGuest = true,
            )
        )
        source.search(accountId, "song", 10)
        val rawPath = "/音乐/type3 %25 #? \\"
        source.resolvePlayback(
            legacyStorageTrackMediaId(BuiltInSourceIds.OpenList, accountId, rawPath)
        )

        assertEquals(true, authenticated?.isGuest)
        assertEquals("user", authenticated?.username)
        assertEquals(LegacyStorageKind.OpenList, searchedKind)
        assertEquals(BuiltInSourceIds.OpenList, searchedSource)
        assertEquals(LegacyStorageKind.OpenList, resolvedKind)
        assertEquals(rawPath, resolvedPath)
        assertEquals(
            SourcePlaybackResult.Failure(
                io.github.julystar.musicapp.source.api.SourcePlaybackFailureReason.UnsupportedMediaId
            ),
            source.resolvePlayback(MediaId(SourceId("other"), MediaType.Track, "legacy-storage-track:x:y")),
        )
    }

    private fun source(
        authenticator: OpenListAuthenticator = OpenListAuthenticator {
            SourceAuthResult.Success
        },
        playbackResolver: LegacyStoragePlaybackResolver = object : LegacyStoragePlaybackResolver {
            override suspend fun resolve(
                accountId: SourceAccountId,
                path: String,
                expectedStorageKind: LegacyStorageKind,
            ) = SourcePlaybackResult.Success(PlaybackResource("file:///track"))

            override suspend fun release(uri: String) = Unit
            override suspend fun releaseAll() = Unit
        },
        searchProvider: LegacyStorageSearchProvider = LegacyStorageSearchProvider { _, _, _, _, _ ->
            SourceSearchResult.Success(emptyList())
        },
        browseClient: OpenListBrowseClient = OpenListBrowseClient { _, _, _, _ ->
            OpenListBrowsePageResult.Success(OpenListBrowsePage(emptyList(), 0))
        },
    ) = OpenListMusicSource(authenticator, playbackResolver, searchProvider, browseClient)
}
