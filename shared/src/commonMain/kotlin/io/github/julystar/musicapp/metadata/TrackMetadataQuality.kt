package io.github.julystar.musicapp.metadata

import io.github.julystar.musicapp.database.TrackMetadataSources

/** Describes how much semantic metadata is available for a track. */
enum class TrackMetadataQuality {
    EMPTY,
    FILENAME_ONLY,
    PARTIAL,
    COMPLETE,
}

data class TrackMetadataQualityInput(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val genre: String? = null,
    val durationMs: Long? = null,
    val trackNumber: Int? = null,
)

object TrackMetadataQualityEvaluator {
    fun evaluate(metadata: TrackMetadataQualityInput, metadataSource: String? = null): TrackMetadataQuality {
        val fields = listOf(metadata.title, metadata.artist, metadata.album, metadata.genre)
            .count { !it.isNullOrBlank() }
        return when {
            fields == 0 -> TrackMetadataQuality.EMPTY
            !metadata.title.isNullOrBlank() && !metadata.artist.isNullOrBlank() &&
                !metadata.album.isNullOrBlank() && metadata.durationMs != null -> TrackMetadataQuality.COMPLETE
            metadataSource == TrackMetadataSources.Filename && fields <= 1 ->
                TrackMetadataQuality.FILENAME_ONLY
            else -> TrackMetadataQuality.PARTIAL
        }
    }
}
