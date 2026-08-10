package io.github.julystar.musicapp.source.api

import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import kotlin.test.Test
import kotlin.test.assertEquals

class RemoteServerMediaIdTest {
    @Test
    fun mediaSourceIdRoundTripsForEmbyPlayback() {
        val encoded = RemoteServerTrack(
            accountId = SourceAccountId("storage:7"),
            remoteId = "item-id",
            title = "Song",
            streamUrl = "https://example.test/stream",
            sourceMediaId = "media-source-id",
        ).encodedPlaybackId()

        val target = encoded.decodeRemoteServerPlaybackTarget()

        assertEquals(SourceAccountId("storage:7"), target?.accountId)
        assertEquals("item-id", target?.remoteId)
        assertEquals("media-source-id", target?.sourceMediaId)
    }

    @Test
    fun oldTwoPartIdsRemainCompatible() {
        val target = "storage:7|item-id".decodeRemoteServerPlaybackTarget()

        assertEquals("item-id", target?.remoteId)
        assertEquals(null, target?.sourceMediaId)
    }
}
