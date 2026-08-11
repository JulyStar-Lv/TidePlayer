package io.github.julystar.musicapp.core.data

import io.github.julystar.musicapp.core.domain.model.DomainAlbumDetail
import io.github.julystar.musicapp.core.domain.model.DomainTrackBrowserItem
import io.github.julystar.musicapp.core.domain.repository.AlbumDetailRepository
import io.github.julystar.musicapp.database.MetadataDao
import io.github.julystar.musicapp.database.TrackDao

class AlbumDetailRepositoryImpl(
    private val metadataDao: MetadataDao,
    private val trackDao: TrackDao,
) : AlbumDetailRepository {

    override suspend fun loadAlbumDetail(albumId: Long): DomainAlbumDetail {
        val album = metadataDao.getAlbum(albumId)
        val tracks = trackDao.findByAlbumId(albumId)
        val artist = metadataDao.artistNamesForAlbum(albumId).joinToString(", ")
        val genre = tracks.firstOrNull()
            ?.let { track -> metadataDao.genreNamesForTrack(track.id).firstOrNull() }

        return DomainAlbumDetail(
            albumTitle = album?.name.orEmpty(),
            albumArtist = artist.ifBlank {
                tracks.firstOrNull { track -> !track.artist.isNullOrBlank() }?.artist
            },
            year = album?.year,
            genre = genre,
            tracks = tracks.map { track ->
                DomainTrackBrowserItem(
                    id = track.id,
                    title = track.title,
                    artist = track.artist,
                    albumName = album?.name,
                    durationMs = track.durationMs,
                    trackNumber = track.trackNumber,
                    discNumber = track.discNumber,
                    mediaId = null,
                    albumId = track.albumId,
                    canDownload = false,
                )
            },
        )
    }
}
