package io.github.julystar.musicapp.core.data

import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.LibraryAlbumItem
import io.github.julystar.musicapp.core.domain.model.LibraryArtistItem
import io.github.julystar.musicapp.core.domain.model.LibraryTrackItem
import io.github.julystar.musicapp.core.domain.repository.LibraryRepository
import io.github.julystar.musicapp.database.MetadataDao
import io.github.julystar.musicapp.database.TrackDao
import io.github.julystar.musicapp.database.TrackEntity
import io.github.julystar.musicapp.database.TrackSourceRefDao
import io.github.julystar.musicapp.source.storage.toSourceTrackMediaIdOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LibraryRepositoryImpl(
    private val scope: CoroutineScope,
    private val trackDao: TrackDao,
    private val metadataDao: MetadataDao,
    private val trackSourceRefDao: TrackSourceRefDao,
) : LibraryRepository {
    private val _tracks = MutableStateFlow<List<LibraryTrackItem>>(emptyList())
    private val _albums = MutableStateFlow<List<LibraryAlbumItem>>(emptyList())
    private val _artists = MutableStateFlow<List<LibraryArtistItem>>(emptyList())
    private val _initialLoadComplete = MutableStateFlow(false)

    override val initialLoadComplete = _initialLoadComplete.asStateFlow()
    override val tracks = _tracks.asStateFlow()
    override val albums = _albums.asStateFlow()
    override val artists = _artists.asStateFlow()

    init {
        scope.launch {
            trackDao.observeAll().collect { entities ->
                val mediaIds = if (entities.isEmpty()) {
                    emptyMap()
                } else {
                    trackSourceRefDao
                        .playbackCandidatesForTracks(entities.map(TrackEntity::id))
                        .groupBy { candidate -> candidate.ref.trackId }
                        .mapValues { (_, candidates) ->
                            candidates.firstNotNullOfOrNull { candidate -> candidate.toSourceTrackMediaIdOrNull() }
                        }
                }
                _tracks.value = entities.map { track ->
                    track.toLibraryTrackItem(
                        mediaId = mediaIds[track.id],
                    )
                }
                _initialLoadComplete.value = true
            }
        }
        scope.launch {
            metadataDao.observeAlbumsWithTracks().collect { rows ->
                _albums.value = rows.map { row ->
                    LibraryAlbumItem(
                        id = row.album.id,
                        name = row.album.name,
                        year = row.album.year,
                        artist = row.artistName,
                    )
                }
            }
        }
        scope.launch {
            metadataDao.observeArtistsWithTracks().collect { entities ->
                _artists.value = entities.map { LibraryArtistItem(it.id, it.name) }
            }
        }
    }
}

internal fun TrackEntity.toLibraryTrackItem(
    mediaId: MediaId? = null,
): LibraryTrackItem {
    return LibraryTrackItem(
        id = id,
        title = title,
        artist = artist?.takeIf { it.isNotBlank() }
            ?: albumArtist?.takeIf { it.isNotBlank() }
            ?: composer?.takeIf { it.isNotBlank() },
        durationMs = durationMs,
        mediaId = mediaId,
    )
}
