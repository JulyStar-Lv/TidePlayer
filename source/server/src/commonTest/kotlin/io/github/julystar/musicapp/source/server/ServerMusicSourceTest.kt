package io.github.julystar.musicapp.source.server

import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.source.api.RemoteServerGateway
import io.github.julystar.musicapp.source.api.RemoteServerKind
import io.github.julystar.musicapp.source.api.RemoteServerSourceConfiguration
import io.github.julystar.musicapp.source.api.RemoteServerTrack
import io.github.julystar.musicapp.source.api.SourceAudioProperties
import io.github.julystar.musicapp.source.api.SourceAuthResult
import io.github.julystar.musicapp.source.api.SourceListResult
import io.github.julystar.musicapp.source.api.SourcePlaybackResult
import io.github.julystar.musicapp.source.api.SourceSearchResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ServerMusicSourceTest {
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
        assertEquals("storage:7|item-id|media-source-id", list.nodes.single().remoteId)
        assertEquals(properties, search.items.single().audioProperties)
    }
}

private class FakeGateway(
    private val track: RemoteServerTrack,
) : RemoteServerGateway {
    override suspend fun authenticate(
        configuration: RemoteServerSourceConfiguration,
    ): SourceAuthResult = SourceAuthResult.Success

    override suspend fun tracks(
        kind: RemoteServerKind,
        accountId: SourceAccountId,
        query: String?,
        limit: Int,
    ): Result<List<RemoteServerTrack>> = Result.success(listOf(track))

    override suspend fun playback(
        kind: RemoteServerKind,
        encodedRemoteId: String,
    ): SourcePlaybackResult = error("Not used")
}
