package io.github.julystar.musicapp.source.webdav

import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.source.api.LegacyStorageConnectionTester
import io.github.julystar.musicapp.source.api.LegacyStorageDirectoryLister
import io.github.julystar.musicapp.source.api.LegacyStorageKind
import io.github.julystar.musicapp.source.api.LegacyStoragePlaybackResolver
import io.github.julystar.musicapp.source.api.SourceAuthResult
import io.github.julystar.musicapp.source.api.SourceCapability
import io.github.julystar.musicapp.source.api.SourceListResult
import io.github.julystar.musicapp.source.api.SourcePlaybackResult
import kotlin.test.Test
import kotlin.test.assertTrue

class WebDavMusicSourceTest {
    @Test
    fun `advertises the RFC 6578 incremental sync implemented by the sync controller`() {
        val source = WebDavMusicSource(
            connectionTester = LegacyStorageConnectionTester { SourceAuthResult.Success },
            directoryLister = LegacyStorageDirectoryLister { _, _, _ ->
                SourceListResult.Success(emptyList())
            },
            playbackResolver = NoopPlaybackResolver,
        )

        assertTrue(SourceCapability.IncrementalSync in source.capabilities)
    }
}

private object NoopPlaybackResolver : LegacyStoragePlaybackResolver {
    override suspend fun resolve(
        accountId: SourceAccountId,
        path: String,
        expectedStorageKind: LegacyStorageKind,
    ): SourcePlaybackResult = error("not used")

    override suspend fun release(uri: String) = Unit

    override suspend fun releaseAll() = Unit
}
