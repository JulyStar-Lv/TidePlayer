package io.github.julystar.musicapp.core.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.time.Duration

sealed interface Artwork {
    data class LibraryTrack(
        val trackId: Long,
        val allowPluginLookup: Boolean = false,
    ) : Artwork
    data class LibraryAlbum(val albumId: Long) : Artwork
    data class LibraryCover(val trackId: Long) : Artwork
    data class SourceMedia(val mediaId: MediaId) : Artwork
    data class LegacyStorageEntry(
        val storageId: Long,
        val path: String,
    ) : Artwork
}

data class ArtworkCacheKey(
    val contentHash: String,
    val localPath: String,
    val thumbnailPath: String?,
    val width: Int?,
    val height: Int?,
    val mimeType: String?,
    val pictureType: String?,
)

data class Lyrics(
    val lines: ImmutableList<LyricLine> = persistentListOf(),
    val loadState: LyricsLoadState = LyricsLoadState.Loading,
) {
    val hasLyric: Boolean
        get() = loadState != LyricsLoadState.Missing
}

data class LyricLine(
    val duration: Duration,
    val text: String,
    val words: ImmutableList<LyricWord> = persistentListOf(),
)

data class LyricWord(
    val text: String,
    val startOffset: Duration,
    val duration: Duration,
)

enum class LyricsLoadState {
    Loading,
    Missing,
    Failed,
    Loaded,
}


data class PlaylistSummary(
    val id: Long,
    val title: String,
    val musicCount: Long,
    val durationMs: Long,
    val coverArtwork: Artwork?,
)


data class CurrentTrackInfo(
    val id: Long,
    val title: String,
    val durationMs: Long?,
    val artwork: Artwork?,
    val lyrics: Lyrics,
    val sourceStorageId: Long,
    val sourcePath: String,
    val coverArtwork: Artwork?,
    val artist: String? = null,
    val mediaId: MediaId? = null,
    val annotation: String? = null,
    val playbackAudioInfo: PlaybackAudioInfo? = null,
)
