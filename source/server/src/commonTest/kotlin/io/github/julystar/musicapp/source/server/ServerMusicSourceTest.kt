package io.github.julystar.musicapp.source.server

import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.source.api.RemoteServerGateway
import io.github.julystar.musicapp.source.api.RemoteServerKind
import io.github.julystar.musicapp.source.api.RemoteServerSourceConfiguration
import io.github.julystar.musicapp.source.api.RemoteServerTrack
import io.github.julystar.musicapp.source.api.RemoteServerTrackPage
import io.github.julystar.musicapp.source.api.SourceAudioProperties
import io.github.julystar.musicapp.source.api.SourceAuthResult
import io.github.julystar.musicapp.source.api.SourceCapability
import io.github.julystar.musicapp.source.api.SourceListResult
import io.github.julystar.musicapp.source.api.SourcePlaybackResult
import io.github.julystar.musicapp.source.api.SourceSearchResult
import io.github.julystar.musicapp.source.api.decodeRemoteServerPlaybackTarget
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ServerMusicSourceTest {
    @Test
    fun onlyNavidromeAdvertisesNavidromeSpecificCapabilities() {
        val navidrome = ServerMusicSource(RemoteServerKind.Navidrome, PagingGateway(emptyList()))
        val emby = ServerMusicSource(RemoteServerKind.Emby, PagingGateway(emptyList()))
        assertTrue(SourceCapability.Download in navidrome.capabilities)
        assertTrue(SourceCapability.Lyrics in navidrome.capabilities)
        assertFalse(SourceCapability.Download in emby.capabilities)
        assertFalse(SourceCapability.Lyrics in emby.capabilities)
    }
    @Test
    fun listUsesOnlyTheFirstPageAndListPagesPreservesOrder() = runTest {
        val first = track("first")
        val second = track("second")
        val source = ServerMusicSource(
            kind = RemoteServerKind.Emby,
            gateway = PagingGateway(listOf(first, second)),
        )

        val list = assertIs<SourceListResult.Success>(source.list(SourceAccountId("storage:7")))
        assertEquals("first", list.nodes.single().path.removePrefix("/"))
        val pages = source.listPages(SourceAccountId("storage:7"), pageSize = 1).toList()
        assertEquals(2, pages.size)
        assertEquals(
            listOf("first", "second"),
            pages.map { assertIs<SourceListResult.Success>(it).nodes.single().path.removePrefix("/") },
        )
    }

    @Test
    fun searchStopsAfterRequestedLimitAcrossPages() = runTest {
        val source = ServerMusicSource(
            kind = RemoteServerKind.Emby,
            gateway = PagingGateway(listOf(track("first"), track("second"), track("third"))),
        )

        val search = assertIs<SourceSearchResult.Success>(
            source.search(SourceAccountId("storage:7"), "Song", limit = 2)
        )
        assertEquals(listOf("first", "second"), search.items.map { it.title })
    }

    @Test
    fun browseAndSearchPreserveUnifiedAudioProperties() = runTest {
        val properties = SourceAudioProperties(
            codec = "FLAC",
            bitrateKbps = 2_784,
            sampleRateHz = 96_000,
            bitDepth = 24,
            channels = 2,
            lossless = true,
        )
        val source = ServerMusicSource(
            kind = RemoteServerKind.Emby,
            gateway = FakeGateway(
                RemoteServerTrack(
                    accountId = SourceAccountId("storage:7"),
                    remoteId = "item-id",
                    title = "Song",
                    streamUrl = "https://example.test/stream",
                    audioProperties = properties,
                    sourceMediaId = "media-source-id",
                )
            ),
        )

        val list = assertIs<SourceListResult.Success>(source.list(SourceAccountId("storage:7")))
        val search = assertIs<SourceSearchResult.Success>(
            source.search(SourceAccountId("storage:7"), "Song")
        )

        assertEquals(properties, list.nodes.single().audioProperties)
        assertEquals("media-source-id", list.nodes.single().sourceMediaId)
        val target = requireNotNull(list.nodes.single().remoteId)
            .decodeRemoteServerPlaybackTarget()
        assertEquals(SourceAccountId("storage:7"), target?.accountId)
        assertEquals("item-id", target?.remoteId)
        assertEquals("media-source-id", target?.sourceMediaId)
        assertEquals(properties, search.items.single().audioProperties)
    }
}

private fun track(id: String) = RemoteServerTrack(
    accountId = SourceAccountId("storage:7"),
    remoteId = id,
    title = id,
    streamUrl = "https://example.test/$id",
)

private class PagingGateway(
    private val tracks: List<RemoteServerTrack>,
) : RemoteServerGateway {
    override suspend fun authenticate(
        configuration: RemoteServerSourceConfiguration,
    ): SourceAuthResult = SourceAuthResult.Success

    override fun trackPages(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        query: String?,
        pageSize: Int,
    ) = flow {
        tracks.chunked(1).forEachIndexed { offset, page ->
            emit(Result.success(RemoteServerTrackPage(page, offset)))
        }
    }

    override suspend fun playback(
        kind: RemoteServerKind,
        encodedRemoteId: String,
    ): SourcePlaybackResult = error("Not used")
}

private class FakeGateway(
    private val track: RemoteServerTrack,
) : RemoteServerGateway {
    override suspend fun authenticate(
        configuration: RemoteServerSourceConfiguration,
    ): SourceAuthResult = SourceAuthResult.Success

    override fun trackPages(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        query: String?,
        pageSize: Int,
    ) = flowOf(Result.success(RemoteServerTrackPage(listOf(track), 0)))

    override suspend fun playback(
        kind: RemoteServerKind,
        encodedRemoteId: String,
    ): SourcePlaybackResult = error("Not used")
}
