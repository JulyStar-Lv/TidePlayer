package io.github.julystar.musicapp.core

import java.util.Base64
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.core.text.isDigitsOnly
import androidx.media3.common.C.TIME_UNSET
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.metadata.flac.PictureFrame
import androidx.media3.extractor.metadata.id3.ApicFrame
import io.github.julystar.musicapp.singleton.Bridge
import io.github.julystar.musicapp.service.playback.data.DEFAULT_COVER_BASE64
import io.github.julystar.musicapp.service.playback.data.PlayerRepository
import io.github.julystar.musicapp.service.playback.domain.PlayableItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uniffi.app_backend.Music
import uniffi.app_backend.MusicAbstract
import uniffi.app_backend.MusicId

private val defaultArtworkData: ByteArray by lazy(LazyThreadSafetyMode.NONE) {
    Base64.getDecoder().decode(DEFAULT_COVER_BASE64.substringAfter("base64,"))
}

internal fun MediaMetadata.Builder.withDefaultArtwork(): MediaMetadata.Builder {
    return setArtworkData(defaultArtworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
}

@OptIn(UnstableApi::class)
private fun extractCurrentTracksCover(player: Player): ByteArray? {
    player.currentTracks.groups.forEach { trackGroup ->
        (0 until trackGroup.length).forEach { i ->
            val format = trackGroup.getTrackFormat(i)
            val metadata = format.metadata
            if (metadata != null) {
                (0 until metadata.length()).forEach { j ->
                    val entry = metadata.get(j)
                    if (entry is ApicFrame) {
                        // ID3
                        return entry.pictureData
                    } else if (entry is PictureFrame) {
                        // FLAC
                        return entry.pictureData
                    }
                }
            }
        }
    }
    return null
}



private sealed class MusicOrMusicAbstract {
    data class VMusic(
        val v1: Music
    ) : MusicOrMusicAbstract()
    data class VMusicAbstract (
        val v1: MusicAbstract
    ) : MusicOrMusicAbstract() {}
}

data class BuildMediaContext(
    val bridge: Bridge,
    val scope: CoroutineScope
)

@OptIn(UnstableApi::class)
private fun buildMediaItem(
    cx: BuildMediaContext,
    music: MusicOrMusicAbstract,
    playbackUri: String? = null,
): MediaItem {
    val meta = when(music) {
        is MusicOrMusicAbstract.VMusic -> music.v1.meta
        is MusicOrMusicAbstract.VMusicAbstract -> music.v1.meta
    }

    val mediaItem = MediaItem.Builder()
        .setMediaId(meta.id.value.toString())
        .setUri(playbackUri?.toUri() ?: "tideplayer://data?music=${meta.id.value}".toUri())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(meta.title)
                .withDefaultArtwork()
                .build()
        )
        .build()

    return mediaItem
}

fun buildMediaItem(cx: BuildMediaContext, music: Music): MediaItem {
    return buildMediaItem(cx, MusicOrMusicAbstract.VMusic(music))
}
fun buildMediaItem(cx: BuildMediaContext, music: MusicAbstract): MediaItem {
    return buildMediaItem(cx, MusicOrMusicAbstract.VMusicAbstract(music))
}

fun buildMediaItem(item: PlayableItem, playbackUri: String): MediaItem {
    val mediaId = item.libraryTrackId?.toString()
        ?: item.mediaId?.remoteId
        ?: item.title
    return MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(playbackUri.toUri())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(item.title)
                .withDefaultArtwork()
                .build()
        )
        .build()
}

@OptIn(UnstableApi::class)
private fun playUtil(
    cx: BuildMediaContext,
    music: MusicOrMusicAbstract,
    player: Player,
    playbackUri: String? = null,
) {
    val mediaItem = buildMediaItem(cx, music, playbackUri)
    player.stop()
    player.setMediaItem(mediaItem)
    player.prepare()
    player.play()
}
fun playUtil(
    cx: BuildMediaContext,
    music: Music,
    player: Player,
    playbackUri: String? = null,
) {
    playUtil(cx, MusicOrMusicAbstract.VMusic(music), player, playbackUri)
}
fun playUtil(
    cx: BuildMediaContext,
    music: MusicAbstract,
    player: Player,
    playbackUri: String? = null,
) {
    playUtil(cx, MusicOrMusicAbstract.VMusicAbstract(music), player, playbackUri)
}

fun playUtil(
    item: PlayableItem,
    player: Player,
    playbackUri: String,
) {
    val mediaItem = buildMediaItem(item, playbackUri)
    player.stop()
    player.setMediaItem(mediaItem)
    player.prepare()
    player.play()
}

fun syncMetadataUtil(scope: CoroutineScope, playerRepository: PlayerRepository, player: Player, onUpdated: () -> Unit = {}) {
    if (!player.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)) {
        return
    }
    if (player.duration == TIME_UNSET) {
        return
    }

    val mediaItem = player.currentMediaItem
    if (mediaItem != null && mediaItem.mediaId.isDigitsOnly()) {
        val id = MusicId(mediaItem.mediaId.toLong())
        val durationMS = player.duration

        scope.launch {
            playerRepository.updateDuration(id, durationMS)
            onUpdated()
        }
    }
}

@OptIn(UnstableApi::class)
internal fun MediaMetadata.withArtworkData(artworkData: ByteArray): MediaMetadata {
    return buildUpon()
        .setArtworkUri(null)
        .setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        .build()
}
