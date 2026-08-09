package io.github.julystar.musicapp.core

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import uniffi.app_backend.MusicAbstract
import uniffi.app_backend.Playlist

internal const val ANDROID_MEDIA_QUEUE_SCHEME = "musicapp"
private val LEGACY_ANDROID_MEDIA_QUEUE_SCHEMES = setOf("melodytrove", "tidetunes")
internal const val ANDROID_MEDIA_QUEUE_AUTHORITY = "track"
internal const val ANDROID_MEDIA_QUEUE_MAX_ITEMS = 101

internal const val MEDIA_EXTRA_GLOBAL_QUEUE_INDEX =
    "io.github.julystar.musicapp.extra.GLOBAL_QUEUE_INDEX"
internal const val MEDIA_EXTRA_GLOBAL_QUEUE_COUNT =
    "io.github.julystar.musicapp.extra.GLOBAL_QUEUE_COUNT"
internal const val MEDIA_EXTRA_PLAYLIST_ID =
    "io.github.julystar.musicapp.extra.PLAYLIST_ID"

/**
 * A bounded projection of the application queue that is safe to send through Media3/Binder.
 *
 * The application can hold very large local or remote libraries. Publishing every item at once
 * would risk Binder transaction limits and would encourage eager remote-resource resolution. The
 * window keeps the current item and nearby tracks in the native Media3 timeline while the full
 * queue remains available through [Playlist] for rebuilding when playback reaches an edge.
 */
internal data class AndroidMediaQueueWindow(
    val mediaItems: List<MediaItem>,
    val currentIndex: Int,
    val globalStartIndex: Int,
    val globalCurrentIndex: Int,
    val globalCount: Int,
)

@OptIn(UnstableApi::class)
internal fun buildAndroidMediaQueueWindow(
    playlist: Playlist,
    currentTrackId: Long,
    maxItems: Int = ANDROID_MEDIA_QUEUE_MAX_ITEMS,
): AndroidMediaQueueWindow? {
    val musics = playlist.musics
    if (musics.isEmpty()) return null

    val globalCurrentIndex = musics.indexOfFirst { music ->
        music.meta.id.value == currentTrackId
    }
    if (globalCurrentIndex < 0) return null

    val safeMaxItems = maxItems.coerceAtLeast(1)
    val windowSize = minOf(safeMaxItems, musics.size)
    val preferredStart = globalCurrentIndex - windowSize / 2
    val globalStartIndex = preferredStart.coerceIn(0, musics.size - windowSize)
    val windowMusics = musics.subList(globalStartIndex, globalStartIndex + windowSize)
    val playlistId = playlist.abstr.meta.id.value

    return AndroidMediaQueueWindow(
        mediaItems = windowMusics.mapIndexed { localIndex, music ->
            music.toAndroidQueueMediaItem(
                playlistId = playlistId,
                globalIndex = globalStartIndex + localIndex,
                globalCount = musics.size,
            )
        },
        currentIndex = globalCurrentIndex - globalStartIndex,
        globalStartIndex = globalStartIndex,
        globalCurrentIndex = globalCurrentIndex,
        globalCount = musics.size,
    )
}

@OptIn(UnstableApi::class)
private fun MusicAbstract.toAndroidQueueMediaItem(
    playlistId: Long,
    globalIndex: Int,
    globalCount: Int,
): MediaItem {
    val trackId = meta.id.value
    val extras = Bundle().apply {
        putInt(MEDIA_EXTRA_GLOBAL_QUEUE_INDEX, globalIndex)
        putInt(MEDIA_EXTRA_GLOBAL_QUEUE_COUNT, globalCount)
        putLong(MEDIA_EXTRA_PLAYLIST_ID, playlistId)
    }
    val metadata = MediaMetadata.Builder()
        .setTitle(meta.title)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .setExtras(extras)
        .apply {
            meta.duration?.inWholeMilliseconds?.let(::setDurationMs)
        }
        .build()

    return MediaItem.Builder()
        .setMediaId(trackId.toString())
        .setUri(androidQueueTrackUri(trackId, playlistId))
        .setMediaMetadata(metadata)
        .build()
}

internal fun androidQueueTrackUri(trackId: Long, playlistId: Long? = null): Uri {
    return Uri.Builder()
        .scheme(ANDROID_MEDIA_QUEUE_SCHEME)
        .authority(ANDROID_MEDIA_QUEUE_AUTHORITY)
        .appendPath(trackId.toString())
        .apply {
            playlistId?.let { appendQueryParameter("playlist", it.toString()) }
        }
        .build()
}

/** Supports current brand-neutral queue URIs and legacy MelodyTrove/TideTunes queue URIs. */
internal fun Uri.androidPlaybackTrackIdOrNull(): Long? {
    val normalizedScheme = scheme?.lowercase() ?: return null
    if (
        normalizedScheme != ANDROID_MEDIA_QUEUE_SCHEME &&
        normalizedScheme !in LEGACY_ANDROID_MEDIA_QUEUE_SCHEMES
    ) {
        return null
    }
    return when (authority?.lowercase()) {
        ANDROID_MEDIA_QUEUE_AUTHORITY -> pathSegments.singleOrNull()?.toLongOrNull()
        "data" -> getQueryParameter("music")?.toLongOrNull()
        else -> null
    }
}
