package io.github.julystar.musicapp.source.api

import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

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

    @Test
    fun versionedIdsRoundTripReservedAndUnicodeCharacters() {
        val track = RemoteServerTrack(
            accountId = SourceAccountId("account|:%%/?# 空间音乐😀"),
            remoteId = "item|:%%/?# 文件😀",
            title = "Song",
            streamUrl = "https://example.test/stream",
            sourceMediaId = "media|:%%/?# 来源😀",
        )

        val encoded = track.encodedPlaybackId()
        val target = encoded.decodeRemoteServerPlaybackTarget()

        assertTrue(encoded.startsWith("v2:"))
        assertTrue('|' !in encoded)
        assertEquals(track.accountId, target?.accountId)
        assertEquals(track.remoteId, target?.remoteId)
        assertEquals(track.sourceMediaId, target?.sourceMediaId)
    }

    @Test
    fun versionedIdsSupportOmittedSourceMediaId() {
        val encoded = RemoteServerTrack(
            accountId = SourceAccountId("account"),
            remoteId = "item",
            title = "Song",
            streamUrl = "https://example.test/stream",
        ).encodedPlaybackId()

        assertEquals(
            RemoteServerPlaybackTarget(SourceAccountId("account"), "item"),
            encoded.decodeRemoteServerPlaybackTarget(),
        )
    }

    @Test
    fun oldThreePartIdsRemainCompatible() {
        val target = "storage:7|item-id|media-source-id".decodeRemoteServerPlaybackTarget()

        assertEquals(SourceAccountId("storage:7"), target?.accountId)
        assertEquals("item-id", target?.remoteId)
        assertEquals("media-source-id", target?.sourceMediaId)
    }

    @Test
    fun oldThreePartIdsWithVersionLookingAccountRemainCompatible() {
        val target = "v2|remote|media".decodeRemoteServerPlaybackTarget()

        assertEquals(SourceAccountId("v2"), target?.accountId)
        assertEquals("remote", target?.remoteId)
        assertEquals("media", target?.sourceMediaId)
    }

    @Test
    fun malformedVersionedIdsAreRejected() {
        assertNull("v2:account:remote%".decodeRemoteServerPlaybackTarget())
        assertNull("v2:account".decodeRemoteServerPlaybackTarget())
        assertNull("v2:account:remote:media:extra".decodeRemoteServerPlaybackTarget())
        assertNull("v2:account:rem?te".decodeRemoteServerPlaybackTarget())
        assertNull("account|remote|media|extra".decodeRemoteServerPlaybackTarget())
    }
}
