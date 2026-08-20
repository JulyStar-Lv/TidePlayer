package io.github.julystar.musicapp.service.playback.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PlaybackEngineModelsTest {
    @Test
    fun playbackResourceRejectsBlankUri() {
        assertFailsWith<IllegalArgumentException> {
            PlaybackEngineResource(uri = "")
        }
    }

    @Test
    fun playbackResourceRejectsBlankHeaderName() {
        assertFailsWith<IllegalArgumentException> {
            PlaybackEngineResource(
                uri = "https://example.test/track.flac",
                headers = mapOf("" to "value"),
            )
        }
    }

    @Test
    fun playbackResourceRejectsNegativeExpiration() {
        assertFailsWith<IllegalArgumentException> {
            PlaybackEngineResource(
                uri = "https://example.test/track.flac",
                expiresAtEpochMs = -1,
            )
        }
    }

    @Test
    fun playbackResourceReportsExpiration() {
        val resource = PlaybackEngineResource(
            uri = "https://example.test/track.flac",
            expiresAtEpochMs = 1_000,
        )

        assertFalse(resource.isExpired(nowEpochMs = 999))
        assertTrue(resource.isExpired(nowEpochMs = 1_000))
        assertTrue(resource.isExpired(nowEpochMs = 1_001))
    }

    @Test
    fun playbackResourceWithoutExpirationDoesNotExpire() {
        val resource = PlaybackEngineResource(uri = "file:///music/track.flac")

        assertFalse(resource.isExpired(nowEpochMs = Long.MAX_VALUE))
    }

    @Test
    fun loadRequestKeepsPlayableItemAndResourceSeparated() {
        val item = PlayableItem(title = "Track", libraryTrackId = 1)
        val resource = PlaybackEngineResource(uri = "file:///music/track.flac")

        val request = PlaybackEngineLoadRequest(item = item, resource = resource)

        assertSame(item, request.item)
        assertSame(resource, request.resource)
    }

    @Test
    fun playbackResourceAndNestedLoadRequestRedactUriAndHeaders() {
        val resource = PlaybackEngineResource(
            uri = "$TEST_URL/track.flac",
            headers = mapOf("Authorization" to TEST_SECRET),
        )
        val request = PlaybackEngineLoadRequest(
            item = PlayableItem(title = "Track", libraryTrackId = 1),
            resource = resource,
        )

        listOf(resource, request).forEach { model ->
            val rendered = model.toString()
            assertFalse(TEST_URL in rendered)
            assertFalse(TEST_SECRET in rendered)
            assertTrue("<redacted>" in rendered)
        }
    }

    private companion object {
        const val TEST_SECRET = "playback-fixture-sensitive-value"
        const val TEST_URL = "https://media.example/private"
    }
}
