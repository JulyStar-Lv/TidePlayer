package io.github.julystar.musicapp.source.api

import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SensitiveModelToStringTest {
    @Test
    fun configurationAndLegacyRequestRedactPasswords() {
        val models = listOf(
            RemoteServerSourceConfiguration(
                alias = "Server",
                kind = RemoteServerKind.Navidrome,
                address = "https://server.example",
                username = "listener",
                password = TEST_SECRET,
            ),
            LegacyStorageConnectionRequest(
                alias = "Storage",
                address = "https://storage.example",
                username = "listener",
                password = TEST_SECRET,
                kind = LegacyStorageKind.WebDav,
            ),
        )

        models.forEach { model ->
            assertFalse(TEST_SECRET in model.toString())
            assertTrue("<redacted>" in model.toString())
        }
    }

    @Test
    fun playbackAndRemoteTrackNestingRedactsUrlsAndHeaders() {
        val mediaSource = RemoteServerMediaSource(
            id = "media",
            requiredHttpHeaders = mapOf("Authorization" to TEST_SECRET),
        )
        val track = RemoteServerTrack(
            accountId = SourceAccountId("storage:7"),
            remoteId = "song",
            title = "Song",
            streamUrl = "$TEST_URL/stream",
            coverUrl = "$TEST_URL/cover",
            mediaSources = listOf(mediaSource),
        )
        val playback = SourcePlaybackResult.Success(
            PlaybackResource(
                uri = "$TEST_URL/playback",
                headers = mapOf("Authorization" to TEST_SECRET),
            ),
        )

        listOf(mediaSource, track, playback).forEach { model ->
            val rendered = model.toString()
            assertFalse(TEST_SECRET in rendered)
            assertFalse(TEST_URL in rendered)
            assertTrue("<redacted>" in rendered)
        }
    }

    @Test
    fun pluginMetadataModelsRedactConfigContextAndUrls() {
        val candidate = MetaSongCandidate(
            id = "song",
            title = "Song",
            pictureUrl = "$TEST_URL/cover",
            fields = mapOf("apiKey" to TEST_SECRET),
            contextToken = TEST_SECRET,
        )
        val query = MetaSongQuery(
            title = "Song",
            config = mapOf("apiKey" to TEST_SECRET),
            song = candidate,
        )
        val cover = MetaCoverCandidate(url = "$TEST_URL/plugin-cover")

        listOf(candidate, query, cover).forEach { model ->
            val rendered = model.toString()
            assertFalse(TEST_SECRET in rendered)
            assertFalse(TEST_URL in rendered)
            assertTrue("<redacted>" in rendered)
        }
    }

    private companion object {
        const val TEST_SECRET = "source-api-fixture-sensitive-value"
        const val TEST_URL = "https://media.example/private"
    }
}
