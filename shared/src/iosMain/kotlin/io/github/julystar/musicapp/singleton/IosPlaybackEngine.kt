package io.github.julystar.musicapp.singleton

import io.github.julystar.musicapp.core.audio.tap.AUDIO_PROCESSING_TAP_ATTACHED
import io.github.julystar.musicapp.core.audio.tap.AUDIO_PROCESSING_TAP_CREATION_FAILED
import io.github.julystar.musicapp.core.audio.tap.AUDIO_PROCESSING_TAP_NO_AUDIO_TRACK
import io.github.julystar.musicapp.core.audio.tap.AUDIO_PROCESSING_TAP_PROTECTED_OR_UNAVAILABLE
import io.github.julystar.musicapp.core.audio.tap.AUDIO_PROCESSING_TAP_UNSUPPORTED_FORMAT
import io.github.julystar.musicapp.core.audio.tap.AudioProcessingTapAttach
import io.github.julystar.musicapp.core.audio.tap.AudioProcessingTapDetach
import io.github.julystar.musicapp.core.audio.tap.AudioProcessingTapReset
import io.github.julystar.musicapp.core.audio.toDomainAudioDspRuntimeSnapshot
import io.github.julystar.musicapp.core.audio.toNativeDspConfiguration
import io.github.julystar.musicapp.core.domain.model.AudioDspBypassReason
import io.github.julystar.musicapp.core.domain.model.AudioDspRuntimeSnapshot
import io.github.julystar.musicapp.core.domain.model.AudioDspRuntimeState
import io.github.julystar.musicapp.core.domain.model.AudioDspRuntimeStatus
import io.github.julystar.musicapp.core.domain.model.DiagnosticLogCategory
import io.github.julystar.musicapp.diagnostics.AppLogger
import io.github.julystar.musicapp.core.domain.model.AudioEffectSettings
import io.github.julystar.musicapp.platform.currentTimeMillis
import io.github.julystar.musicapp.service.playback.domain.PlaybackEngine
import io.github.julystar.musicapp.service.playback.domain.PlaybackEngineFailureReason
import io.github.julystar.musicapp.service.playback.domain.PlaybackEngineLoadRequest
import io.github.julystar.musicapp.service.playback.domain.PlaybackEngineLoadResult
import io.github.julystar.musicapp.service.playback.domain.PlaybackEngineUnsupportedReason
import io.github.julystar.musicapp.service.playback.domain.PlaybackPosition
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import platform.AVFoundation.*
import platform.CoreMedia.*
import platform.Foundation.*
import uniffi.app_backend.NativeAudioDsp
import uniffi.app_backend.ctCreateAudioDspProcessor

internal interface IosPlaybackEngine : PlaybackEngine {
    val playbackCompleted: Flow<Unit>
    fun seekTo(positionMs: Long, completionHandler: (Boolean) -> Unit)
    fun updateAudioDsp(settings: AudioEffectSettings, inputGainDb: Float) = Unit
    fun audioDspRuntimeSnapshot(): AudioDspRuntimeSnapshot? = null
}

@OptIn(ExperimentalForeignApi::class)
internal class AvPlayerIosPlaybackEngine : IosPlaybackEngine {
    private val player = AVPlayer()
    private val nativeDsp: NativeAudioDsp = ctCreateAudioDspProcessor()
    private val nativeDspHandle = nativeDsp.nativeHandle()
    private var attachFailureStatus: AudioDspRuntimeStatus? = null
    private val _playbackCompleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val playbackCompleted = _playbackCompleted.asSharedFlow()
    private val playbackCompletedObserver = NSNotificationCenter.defaultCenter.addObserverForName(
        name = AVPlayerItemDidPlayToEndTimeNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue,
    ) { notification ->
        if (notification?.`object` == player.currentItem) {
            _playbackCompleted.tryEmit(Unit)
        }
    }

    override fun load(request: PlaybackEngineLoadRequest): PlaybackEngineLoadResult {
        val resource = request.resource
        if (resource.isExpired(nowEpochMs = currentTimeMillis())) {
            return PlaybackEngineLoadResult.Failure(
                PlaybackEngineFailureReason.ExpiredResource
            )
        }
        val url = NSURL.URLWithString(resource.uri)
            ?: return PlaybackEngineLoadResult.Unsupported(
                PlaybackEngineUnsupportedReason.UnsupportedResource
            )
        val item = AVPlayerItem.playerItemWithURL(url)
        player.currentItem?.let(::AudioProcessingTapDetach)
        AudioProcessingTapReset(nativeDspHandle)
        val attachResult = AudioProcessingTapAttach(item, nativeDspHandle)
        attachFailureStatus = attachResult.toRuntimeStatusOrNull()
        if (attachFailureStatus == null) {
            AppLogger.info(
                DiagnosticLogCategory.Dsp,
                "AvPlayerIosPlaybackEngine",
                "Audio processing tap attached",
            )
        } else {
            AppLogger.warn(
                DiagnosticLogCategory.Dsp,
                "AvPlayerIosPlaybackEngine",
                "Audio processing tap unavailable; playback will continue without DSP",
                fields = mapOf("attachResult" to attachResult.toString()),
            )
        }
        player.replaceCurrentItemWithPlayerItem(item)
        return PlaybackEngineLoadResult.Ready
    }

    override fun updateAudioDsp(
        settings: AudioEffectSettings,
        inputGainDb: Float,
    ) {
        nativeDsp.updateConfig(settings.toNativeDspConfiguration(inputGainDb))
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun audioDspRuntimeSnapshot(): AudioDspRuntimeSnapshot {
        val failure = attachFailureStatus
        return if (failure == null) {
            nativeDsp.runtimeSnapshot().toDomainAudioDspRuntimeSnapshot()
        } else {
            AudioDspRuntimeSnapshot(status = failure)
        }
    }

    override fun stop() {
        player.pause()
        player.currentItem?.let(::AudioProcessingTapDetach)
        player.replaceCurrentItemWithPlayerItem(null)
        AudioProcessingTapReset(nativeDspHandle)
        attachFailureStatus = null
    }

    override fun seekTo(positionMs: Long) {
        seekTo(positionMs) { }
    }

    override fun seekTo(positionMs: Long, completionHandler: (Boolean) -> Unit) {
        AudioProcessingTapReset(nativeDspHandle)
        player.seekToTime(
            time = CMTimeMake(value = positionMs.coerceAtLeast(0), timescale = 1_000),
            toleranceBefore = CMTimeMake(value = 0, timescale = 1),
            toleranceAfter = CMTimeMake(value = 0, timescale = 1),
            completionHandler = completionHandler,
        )
    }

    override fun readPosition(): PlaybackPosition {
        val currentPositionMs = secondsToMillis(CMTimeGetSeconds(player.currentTime()))
        val seconds = player.currentItem?.let { CMTimeGetSeconds(it.duration) }
            ?: return PlaybackPosition.Zero
        return PlaybackPosition(
            positionMs = currentPositionMs,
            bufferedMs = currentPositionMs,
            durationMs = secondsToMillis(seconds),
        )
    }

    override fun release() {
        stop()
        NSNotificationCenter.defaultCenter.removeObserver(playbackCompletedObserver)
        nativeDsp.close()
    }

    private fun secondsToMillis(seconds: Double): Long {
        return if (seconds.isFinite() && seconds >= 0.0) {
            (seconds * 1_000.0).toLong()
        } else {
            0L
        }
    }

    private fun Int.toRuntimeStatusOrNull(): AudioDspRuntimeStatus? {
        val reason = when (this) {
            AUDIO_PROCESSING_TAP_ATTACHED -> return null
            AUDIO_PROCESSING_TAP_NO_AUDIO_TRACK ->
                AudioDspBypassReason.PlatformProcessingUnavailable
            AUDIO_PROCESSING_TAP_CREATION_FAILED -> AudioDspBypassReason.AudioTapUnavailable
            AUDIO_PROCESSING_TAP_PROTECTED_OR_UNAVAILABLE -> AudioDspBypassReason.ProtectedContent
            AUDIO_PROCESSING_TAP_UNSUPPORTED_FORMAT ->
                AudioDspBypassReason.UnsupportedSampleFormat
            else -> AudioDspBypassReason.AudioTapUnavailable
        }
        return AudioDspRuntimeStatus(
            state = AudioDspRuntimeState.Bypassed,
            bypassReason = reason,
        )
    }
}
