package io.github.julystar.musicapp.metadata

import io.github.julystar.musicapp.database.AlbumArtistCrossRef
import io.github.julystar.musicapp.database.ArtistEntity
import io.github.julystar.musicapp.database.GenreEntity
import io.github.julystar.musicapp.database.MetadataDao
import io.github.julystar.musicapp.database.TrackArtistCrossRef
import io.github.julystar.musicapp.database.TrackGenreCrossRef

/** Low-level relation-table writer shared by automatic and manual metadata transactions. */
class MetadataGraphWriter(private val dao: MetadataDao) {
    suspend fun replaceTrackArtists(trackId: Long, names: List<String>) {
        val clean = names.map(String::trim).filter(String::isNotBlank).distinctBy(::normalize)
        dao.deleteTrackArtistsForTracks(listOf(trackId))
        if (clean.isEmpty()) return
        dao.insertArtists(clean.map { ArtistEntity(name = it, normalizedName = normalize(it), sortName = null) })
        val artists = dao.findArtistsByNormalizedNames(clean.map(::normalize)).associateBy(ArtistEntity::normalizedName)
        dao.upsertTrackArtists(clean.mapIndexedNotNull { index, name ->
            artists[normalize(name)]?.let { TrackArtistCrossRef(trackId, it.id, index) }
        })
    }

    suspend fun replaceTrackGenre(trackId: Long, name: String?) {
        replaceTrackGenres(
            trackId = trackId,
            names = name?.split(';', ',', '/') ?: emptyList(),
        )
    }

    suspend fun replaceTrackGenres(trackId: Long, names: List<String>) {
        dao.deleteTrackGenresForTracks(listOf(trackId))
        val clean = names.map(String::trim).filter(String::isNotBlank).distinctBy(::normalize)
        if (clean.isEmpty()) return
        dao.insertGenres(clean.map { GenreEntity(name = it, normalizedName = normalize(it)) })
        val genres = dao.findGenresByNormalizedNames(clean.map(::normalize))
            .associateBy(GenreEntity::normalizedName)
        dao.upsertTrackGenres(clean.mapNotNull { name ->
            genres[normalize(name)]?.let { TrackGenreCrossRef(trackId, it.id) }
        })
    }

    suspend fun replaceAlbumArtists(albumId: Long, name: String?) {
        dao.deleteAlbumArtistsForAlbums(listOf(albumId))
        val clean = name?.trim()?.takeIf(String::isNotBlank) ?: return
        val normalized = normalize(clean)
        dao.insertArtists(listOf(ArtistEntity(name = clean, normalizedName = normalized, sortName = null)))
        dao.findArtistsByNormalizedNames(listOf(normalized)).singleOrNull()?.let {
            dao.upsertAlbumArtists(listOf(AlbumArtistCrossRef(albumId, it.id, 0)))
        }
    }

    private fun normalize(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")
}
