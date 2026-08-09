package io.github.julystar.musicapp.core

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import uniffi.app_backend.MusicAbstract
import uniffi.app_backend.Playlist

internal const val ANDROID_LIBRARY_ROOT_ID = "musicapp_root"
internal const val ANDROID_LIBRARY_CURRENT_QUEUE_ID = "musicapp_current_queue"

@OptIn(UnstableApi::class)
internal fun androidLibraryRoot(appName: String): MediaItem {
    return MediaItem.Builder()
        .setMediaId(ANDROID_LIBRARY_ROOT_ID)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(appName)
                .setDisplayTitle(appName)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                .setFolderType(MediaMetadata.FOLDER_TYPE_PLAYLISTS)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build()
        )
        .build()
}

@OptIn(UnstableApi::class)
internal fun androidCurrentQueueFolder(playlist: Playlist?): MediaItem {
    val title = playlist?.abstr?.meta?.title?.takeIf(String::isNotBlank) ?: "Current queue"
    return MediaItem.Builder()
        .setMediaId(ANDROID_LIBRARY_CURRENT_QUEUE_ID)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setDisplayTitle(title)
                .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                .setFolderType(MediaMetadata.FOLDER_TYPE_PLAYLISTS)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build()
        )
        .build()
}

@OptIn(UnstableApi::class)
internal fun androidCurrentQueueItems(
    playlist: Playlist?,
    page: Int,
    pageSize: Int,
): List<MediaItem> {
    playlist ?: return emptyList()
    if (page < 0 || pageSize <= 0) return emptyList()
    val fromIndex = page.toLong() * pageSize.toLong()
    if (fromIndex >= playlist.musics.size || fromIndex > Int.MAX_VALUE) return emptyList()
    val start = fromIndex.toInt()
    val end = minOf(start + pageSize, playlist.musics.size)
    val playlistId = playlist.abstr.meta.id.value
    return playlist.musics.subList(start, end).mapIndexed { localIndex, music ->
        music.toAndroidLibraryMediaItem(
            playlistId = playlistId,
            globalIndex = start + localIndex,
            globalCount = playlist.musics.size,
        )
    }
}

@OptIn(UnstableApi::class)
internal fun androidCurrentQueueItem(
    playlist: Playlist?,
    mediaId: String,
): MediaItem? {
    playlist ?: return null
    val trackId = mediaId.toLongOrNull() ?: return null
    val globalIndex = playlist.musics.indexOfFirst { music -> music.meta.id.value == trackId }
    if (globalIndex < 0) return null
    return playlist.musics[globalIndex].toAndroidLibraryMediaItem(
        playlistId = playlist.abstr.meta.id.value,
        globalIndex = globalIndex,
        globalCount = playlist.musics.size,
    )
}

@OptIn(UnstableApi::class)
private fun MusicAbstract.toAndroidLibraryMediaItem(
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
    return MediaItem.Builder()
        .setMediaId(trackId.toString())
        .setUri(androidQueueTrackUri(trackId, playlistId))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(meta.title)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setExtras(extras)
                .apply {
                    meta.duration?.inWholeMilliseconds?.let(::setDurationMs)
                }
                .build()
        )
        .build()
}
