package io.github.julystar.musicapp.singleton

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidPlaybackEngineTest {
    @Test
    fun staleQueueContainingRequestedTrackCannotBeReused() {
        assertFalse(
            androidMediaQueueMatches(
                existingMediaIds = listOf("previous", "requested", "next"),
                requestedMediaIds = listOf("requested"),
            )
        )
    }

    @Test
    fun identicalQueueCanBeReused() {
        assertTrue(
            androidMediaQueueMatches(
                existingMediaIds = listOf("previous", "requested", "next"),
                requestedMediaIds = listOf("previous", "requested", "next"),
            )
        )
    }
}
