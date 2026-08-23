package io.github.julystar.musicapp.core.data.datastore

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.Path.Companion.toPath
import uniffi.app_backend.PlayMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class AppPreferencesRepositoryTest {
    @Test
    fun persistsPlayModeInDataStore() = runBlocking {
        val file = File.createTempFile("musicapp-preferences-", ".preferences_pb").apply {
            delete()
        }

        try {
            val repository = AppPreferencesRepository(
                createAppDataStore { file.absolutePath.toPath() }
            )

            assertEquals(PlayMode.SINGLE, withTimeout(5_000) { repository.playMode.first() })

            repository.setPlayMode(PlayMode.LIST_LOOP)

            assertEquals(PlayMode.LIST_LOOP, withTimeout(5_000) { repository.playMode.first() })
        } finally {
            file.delete()
        }
    }

    @Test
    fun persistsAndClearsPlaybackSession() = runBlocking {
        val file = File.createTempFile("musicapp-preferences-", ".preferences_pb").apply {
            delete()
        }

        try {
            val repository = AppPreferencesRepository(
                createAppDataStore { file.absolutePath.toPath() }
            )
            val session = PersistedPlaybackSession(
                trackId = 2,
                playlistId = 7,
                positionMs = 45_000,
                wasPlaying = true,
                queueTrackIds = listOf(2L, 3L, 1L),
            )

            repository.savePlaybackSession(session)
            assertEquals(session, withTimeout(5_000) { repository.playbackSession.first() })

            repository.clearPlaybackSession()
            assertEquals(null, withTimeout(5_000) { repository.playbackSession.first() })
        } finally {
            file.delete()
        }
    }

    @Test
    fun remapsFavoritesAndPersistedPlaybackSession() = runBlocking {
        val file = File.createTempFile("musicapp-preferences-", ".preferences_pb").apply {
            delete()
        }

        try {
            val repository = AppPreferencesRepository(
                createAppDataStore { file.absolutePath.toPath() }
            )
            repository.toggleFavoriteTrack(1)
            repository.toggleFavoriteTrack(2)
            repository.savePlaybackSession(
                PersistedPlaybackSession(
                    trackId = 2,
                    playlistId = 7,
                    positionMs = 1_000,
                    wasPlaying = true,
                    queueTrackIds = listOf(2L, 2L, 1L),
                )
            )

            repository.remapTrackIds(mapOf(2L to 3L))

            assertEquals(setOf(1L, 3L), withTimeout(5_000) { repository.favoriteTrackIds.first() })
            val session = withTimeout(5_000) { repository.playbackSession.first() }
            assertEquals(3L, session?.trackId)
            assertEquals(listOf(3L, 3L, 1L), session?.queueTrackIds)
        } finally {
            file.delete()
        }
    }
}
