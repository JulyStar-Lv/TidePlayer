package io.github.julystar.musicapp.singleton

import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_CHANGE_MEDIA_ITEMS
import androidx.media3.common.Player.COMMAND_PLAY_PAUSE
import androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_STOP
import androidx.media3.session.MediaController
import io.github.julystar.musicapp.core.buildAndroidMediaQueueWindow
import io.github.julystar.musicapp.core.playUtil
import io.github.julystar.musicapp.core.domain.model.DiagnosticLogCategory
import io.github.julystar.musicapp.diagnostics.AppLogger
import io.github.julystar.musicapp.service.playback.domain.PlaybackEngine
import io.github.julystar.musicapp.service.playback.domain.PlaybackEngineFailureReason
import io.github.julystar.musicapp.service.playback.domain.PlaybackEngineLoadRequest
import io.github.julystar.musicapp.service.playback.domain.PlaybackEngineLoadResult
import io.github.julystar.musicapp.service.playback.domain.PlaybackEngineUnsupportedReason
import io.github.julystar.musicapp.service.playback.domain.PlaybackPosition
import kotlinx.coroutines.CoroutineScope
import uniffi.app_backend.Playlist
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

internal data class AndroidPlaybackQueueLoadRequest(
    val playlist: Playlist,
    val currentTrackId: Long,
    val startPositionMs: Long = 0L,
)

internal interface AndroidPlaybackEngine : PlaybackEngine {
    fun hasLoadedTrack(trackId: Long): Boolean = false

    fun loadQueue(request: AndroidPlaybackQueueLoadRequest): PlaybackEngineLoadResult {
        return PlaybackEngineLoadResult.Unsupported(
            PlaybackEngineUnsupportedReason.MissingPlatformEngine
        )
    }
}

internal fun androidMediaQueueMatches(
    existingMediaIds: List<String>,
    requestedMediaIds: List<String>,
): Boolean = existingMediaIds == requestedMediaIds

internal class MediaControllerAndroidPlaybackEngine(
    private val mediaController: MediaController,
    private val bridge: Bridge,
    private val scope: CoroutineScope,
) : AndroidPlaybackEngine {
    private val applicationHandler = Handler(mediaController.applicationLooper)

    override fun load(request: PlaybackEngineLoadRequest): PlaybackEngineLoadResult {
        return runOnApplicationThread {
            val resource = request.resource
            if (resource.isExpired(nowEpochMs = System.currentTimeMillis())) {
                return@runOnApplicationThread PlaybackEngineLoadResult.Failure(
                    PlaybackEngineFailureReason.ExpiredResource
                )
            }
            playUtil(
                item = request.item,
                player = mediaController,
                playbackUri = resource.uri,
            )
            PlaybackEngineLoadResult.Ready
        }
    }

    override fun loadQueue(request: AndroidPlaybackQueueLoadRequest): PlaybackEngineLoadResult {
        return runOnApplicationThread {
            val window = buildAndroidMediaQueueWindow(
                playlist = request.playlist,
                currentTrackId = request.currentTrackId,
            ) ?: return@runOnApplicationThread PlaybackEngineLoadResult.Failure(
                PlaybackEngineFailureReason.EngineError
            )
            val existingMediaIds = (0 until mediaController.mediaItemCount).map { index ->
                mediaController.getMediaItemAt(index).mediaId
            }
            val requestedMediaIds = window.mediaItems.map { item -> item.mediaId }
            if (
                androidMediaQueueMatches(existingMediaIds, requestedMediaIds) &&
                mediaController.isCommandAvailable(COMMAND_SEEK_TO_MEDIA_ITEM)
            ) {
                mediaController.seekTo(window.currentIndex, request.startPositionMs.coerceAtLeast(0L))
                mediaController.play()
                return@runOnApplicationThread PlaybackEngineLoadResult.Ready
            }

            if (!mediaController.isCommandAvailable(COMMAND_CHANGE_MEDIA_ITEMS)) {
                return@runOnApplicationThread PlaybackEngineLoadResult.Unsupported(
                    PlaybackEngineUnsupportedReason.MissingPlatformEngine
                )
            }

            mediaController.setMediaItems(
                window.mediaItems,
                window.currentIndex,
                request.startPositionMs.coerceAtLeast(0L),
            )
            mediaController.prepare()
            mediaController.play()
            PlaybackEngineLoadResult.Ready
        }
    }

    override fun hasLoadedTrack(trackId: Long): Boolean {
        return runOnApplicationThread {
            mediaController.currentMediaItem?.mediaId == trackId.toString()
        }
    }

    override fun play() {
        runOnApplicationThread {
            if (mediaController.isCommandAvailable(COMMAND_PLAY_PAUSE)) {
                mediaController.play()
            } else {
                AppLogger.warn(
                    DiagnosticLogCategory.Playback,
                    "AndroidPlaybackEngine",
                    "Resume command is unavailable",
                )
            }
        }
    }

    override fun pause() {
        runOnApplicationThread {
            if (mediaController.isCommandAvailable(COMMAND_PLAY_PAUSE)) {
                mediaController.pause()
            } else {
                AppLogger.warn(
                    DiagnosticLogCategory.Playback,
                    "AndroidPlaybackEngine",
                    "Pause command is unavailable",
                )
            }
        }
    }

    override fun stop() {
        runOnApplicationThread {
            if (mediaController.isCommandAvailable(COMMAND_STOP)) {
                // The session wrapper intentionally maps controller STOP to a resumable pause.
                // Internal app stop is destructive, so clear the timeline after issuing STOP.
                mediaController.stop()
                if (mediaController.isCommandAvailable(COMMAND_CHANGE_MEDIA_ITEMS)) {
                    mediaController.clearMediaItems()
                }
            } else {
                AppLogger.warn(
                    DiagnosticLogCategory.Playback,
                    "AndroidPlaybackEngine",
                    "Stop command is unavailable",
                )
            }
        }
    }

    override fun seekTo(positionMs: Long) {
        runOnApplicationThread {
            if (mediaController.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
                mediaController.seekTo(positionMs.coerceAtLeast(0))
            } else {
                AppLogger.warn(
                    DiagnosticLogCategory.Playback,
                    "AndroidPlaybackEngine",
                    "Seek command is unavailable",
                )
            }
        }
    }

    override fun readPosition(): PlaybackPosition {
        return runOnApplicationThread {
            PlaybackPosition(
                positionMs = mediaController.currentPosition.coerceAtLeast(0),
                bufferedMs = mediaController.bufferedPosition.coerceAtLeast(0),
                durationMs = mediaController.duration.coerceAtLeast(0),
            )
        }
    }

    override fun release() {
        runOnApplicationThread {
            mediaController.release()
        }
    }

    private fun <T : Any> runOnApplicationThread(block: () -> T): T {
        if (Looper.myLooper() == mediaController.applicationLooper) {
            return block()
        }

        val value = AtomicReference<T?>()
        val throwable = AtomicReference<Throwable?>()
        val latch = CountDownLatch(1)
        check(applicationHandler.post {
            try {
                value.set(block())
            } catch (error: Throwable) {
                throwable.set(error)
            } finally {
                latch.countDown()
            }
        }) {
            "MediaController application thread is unavailable"
        }
        latch.await()
        throwable.get()?.let { throw it }
        return checkNotNull(value.get()) {
            "MediaController application thread completed without a result"
        }
    }
}
