package io.github.julystar.musicapp.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uniffi.app_backend.PlayMode

class AppPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val favoriteTrackIds: Flow<Set<Long>> = dataStore.data.map { preferences ->
        preferences[FAVORITE_TRACK_IDS_KEY]
            .orEmpty()
            .mapNotNullTo(mutableSetOf(), String::toLongOrNull)
    }

    val playMode: Flow<PlayMode> = dataStore.data.map { preferences ->
        preferences[PLAY_MODE_KEY]
            ?.let { value -> runCatching { PlayMode.valueOf(value) }.getOrNull() }
            ?: PlayMode.SINGLE
    }

    val playbackSession: Flow<PersistedPlaybackSession?> = dataStore.data.map { preferences ->
        val trackId = preferences[LAST_TRACK_ID_KEY] ?: return@map null
        val playlistId = preferences[LAST_PLAYLIST_ID_KEY] ?: return@map null
        PersistedPlaybackSession(
            trackId = trackId,
            playlistId = playlistId,
            positionMs = preferences[LAST_POSITION_MS_KEY] ?: 0L,
            wasPlaying = preferences[LAST_WAS_PLAYING_KEY] ?: false,
            queueTrackIds = preferences[LAST_QUEUE_TRACK_IDS_KEY]?.toTrackIds(),
        )
    }

    suspend fun setPlayMode(playMode: PlayMode) {
        dataStore.edit { preferences ->
            preferences[PLAY_MODE_KEY] = playMode.name
        }
    }

    suspend fun savePlaybackSession(session: PersistedPlaybackSession) {
        dataStore.edit { preferences ->
            preferences[LAST_TRACK_ID_KEY] = session.trackId
            preferences[LAST_PLAYLIST_ID_KEY] = session.playlistId
            preferences[LAST_POSITION_MS_KEY] = session.positionMs.coerceAtLeast(0L)
            preferences[LAST_WAS_PLAYING_KEY] = session.wasPlaying
            session.queueTrackIds?.let { trackIds ->
                preferences[LAST_QUEUE_TRACK_IDS_KEY] = trackIds.joinToString(separator = ",")
            } ?: preferences.remove(LAST_QUEUE_TRACK_IDS_KEY)
        }
    }

    suspend fun clearPlaybackSession() {
        dataStore.edit { preferences ->
            preferences.remove(LAST_TRACK_ID_KEY)
            preferences.remove(LAST_PLAYLIST_ID_KEY)
            preferences.remove(LAST_POSITION_MS_KEY)
            preferences.remove(LAST_WAS_PLAYING_KEY)
            preferences.remove(LAST_QUEUE_TRACK_IDS_KEY)
        }
    }

    suspend fun toggleFavoriteTrack(trackId: Long): Boolean {
        var isFavorite = false
        dataStore.edit { preferences ->
            val trackIds = preferences[FAVORITE_TRACK_IDS_KEY].orEmpty().toMutableSet()
            val value = trackId.toString()
            isFavorite = if (value in trackIds) {
                trackIds.remove(value)
                false
            } else {
                trackIds.add(value)
                true
            }
            preferences[FAVORITE_TRACK_IDS_KEY] = trackIds
        }
        return isFavorite
    }

    suspend fun remapTrackIds(replacements: Map<Long, Long>) {
        if (replacements.isEmpty()) return
        dataStore.edit { preferences ->
            val favoriteIds = preferences[FAVORITE_TRACK_IDS_KEY].orEmpty()
                .mapNotNull(String::toLongOrNull)
                .mapTo(mutableSetOf()) { trackId -> TrackIdRemapper.resolve(trackId, replacements) }
            preferences[FAVORITE_TRACK_IDS_KEY] = favoriteIds.mapTo(mutableSetOf(), Long::toString)

            preferences[LAST_TRACK_ID_KEY]?.let { trackId ->
                TrackIdRemapper.resolveOrNull(trackId, replacements)?.let { targetTrackId ->
                    preferences[LAST_TRACK_ID_KEY] = targetTrackId
                }
            }
            preferences[LAST_QUEUE_TRACK_IDS_KEY]?.let { encodedTrackIds ->
                preferences[LAST_QUEUE_TRACK_IDS_KEY] = encodedTrackIds
                    .toTrackIds()
                    .map { trackId -> TrackIdRemapper.resolve(trackId, replacements) }
                    .joinToString(separator = ",")
            }
        }
    }
}

/** Resolves replacement chains while preserving queue occurrences (queue is a sequence, not a set). */
object TrackIdRemapper {
    fun resolve(id: Long, replacements: Map<Long, Long>): Long =
        resolveOrNull(id, replacements) ?: id

    fun resolveOrNull(id: Long, replacements: Map<Long, Long>): Long? {
        var current = id
        val visited = mutableSetOf<Long>()
        while (true) {
            if (!visited.add(current)) return null
            val next = replacements[current] ?: return if (current == id && id !in replacements) null else current
            if (next == current) return current
            current = next
        }
    }
}

data class PersistedPlaybackSession(
    val trackId: Long,
    val playlistId: Long,
    val positionMs: Long,
    val wasPlaying: Boolean,
    val queueTrackIds: List<Long>? = null,
)

private fun String.toTrackIds(): List<Long> = split(',').mapNotNull(String::toLongOrNull)

private val PLAY_MODE_KEY = stringPreferencesKey("playMode")
private val LAST_TRACK_ID_KEY = longPreferencesKey("playback.lastTrackId")
private val LAST_PLAYLIST_ID_KEY = longPreferencesKey("playback.lastPlaylistId")
private val LAST_POSITION_MS_KEY = longPreferencesKey("playback.lastPositionMs")
private val LAST_WAS_PLAYING_KEY = booleanPreferencesKey("playback.lastWasPlaying")
private val LAST_QUEUE_TRACK_IDS_KEY = stringPreferencesKey("playback.queueTrackIds")
private val FAVORITE_TRACK_IDS_KEY = stringSetPreferencesKey("library.favoriteTrackIds")
