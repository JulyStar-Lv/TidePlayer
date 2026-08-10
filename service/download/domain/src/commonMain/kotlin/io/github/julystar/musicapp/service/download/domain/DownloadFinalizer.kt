package io.github.julystar.musicapp.service.download.domain

import io.github.julystar.musicapp.core.domain.model.MediaId

enum class MetadataSource {
    User,
    Embedded,
    Database,
    Plugin,
    Fallback,
}

data class MetadataValue<T>(
    val value: T,
    val source: MetadataSource,
)

data class ArtworkSnapshot(
    val localPath: String,
    val mimeType: String?,
    val source: MetadataSource,
)

enum class LyricsSnapshotFormat {
    Plain,
    Lrc,
    Ttml,
    WordTimed,
}

data class LyricsSnapshot(
    val embedded: String?,
    val lrc: String?,
    val ttml: String?,
    val format: LyricsSnapshotFormat,
    val source: MetadataSource,
)

data class MetadataSnapshot(
    val title: MetadataValue<String>?,
    val artists: List<MetadataValue<String>>,
    val albumArtist: MetadataValue<String>?,
    val album: MetadataValue<String>?,
    val composer: MetadataValue<String>?,
    val lyricist: MetadataValue<String>?,
    val conductor: MetadataValue<String>?,
    val genre: MetadataValue<String>?,
    val grouping: MetadataValue<String>?,
    val comment: MetadataValue<String>?,
    val copyright: MetadataValue<String>?,
    val publisher: MetadataValue<String>?,
    val date: MetadataValue<String>?,
    val originalReleaseDate: MetadataValue<String>?,
    val trackNumber: MetadataValue<Int>?,
    val trackTotal: MetadataValue<Int>?,
    val discNumber: MetadataValue<Int>?,
    val discTotal: MetadataValue<Int>?,
    val bpm: MetadataValue<Double>?,
    val musicalKey: MetadataValue<String>?,
    val isrc: MetadataValue<String>?,
    val musicBrainzRecordingId: MetadataValue<String>?,
    val musicBrainzTrackId: MetadataValue<String>?,
    val musicBrainzReleaseId: MetadataValue<String>?,
    val musicBrainzReleaseGroupId: MetadataValue<String>?,
    val musicBrainzArtistId: MetadataValue<String>?,
    val musicBrainzReleaseArtistId: MetadataValue<String>?,
    val musicBrainzWorkId: MetadataValue<String>?,
    val replayGainTrackGain: MetadataValue<Double>?,
    val replayGainTrackPeak: MetadataValue<Double>?,
    val replayGainAlbumGain: MetadataValue<Double>?,
    val replayGainAlbumPeak: MetadataValue<Double>?,
    val artwork: ArtworkSnapshot?,
    val lyrics: LyricsSnapshot?,
)

data class DownloadFinalizationRequest(
    val mediaId: MediaId,
    val localPath: String,
    val mimeType: String?,
    val fallbackTitle: String,
    val fallbackArtist: String? = null,
    val fallbackAlbum: String? = null,
    val expectedDurationMs: Long? = null,
    val expectedBytes: Long? = null,
)

sealed interface DownloadFinalizationResult {
    data class Success(
        val localPath: String,
        val warnings: List<String> = emptyList(),
        val libraryTrackId: Long? = null,
    ) : DownloadFinalizationResult

    data class Failure(
        val error: DownloadFinalizationError,
        val message: String,
    ) : DownloadFinalizationResult
}

enum class DownloadFinalizationError {
    MissingFile,
    UnsafeFinalFile,
    DatabaseUpdateFailed,
}

fun interface DownloadFinalizer {
    suspend fun finalize(request: DownloadFinalizationRequest): DownloadFinalizationResult

    data object Disabled : DownloadFinalizer {
        override suspend fun finalize(
            request: DownloadFinalizationRequest,
        ): DownloadFinalizationResult = DownloadFinalizationResult.Success(request.localPath)
    }
}
