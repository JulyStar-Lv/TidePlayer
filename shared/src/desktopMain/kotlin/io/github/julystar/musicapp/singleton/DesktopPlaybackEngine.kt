package io.github.julystar.musicapp.singleton

import io.github.julystar.musicapp.core.audio.toNativeDspConfiguration
import io.github.julystar.musicapp.core.audio.toDomainAudioDspRuntimeSnapshot
import io.github.julystar.musicapp.core.domain.model.AudioEffectSettings
import io.github.julystar.musicapp.core.domain.model.AudioDspRuntimeSnapshot
import io.github.julystar.musicapp.core.domain.model.PlaybackAdvancedSettings
import io.github.julystar.musicapp.service.playback.domain.PlaybackEngine
import io.github.julystar.musicapp.service.playback.domain.PlaybackEngineFailureReason
import io.github.julystar.musicapp.service.playback.domain.PlaybackEngineLoadRequest
import io.github.julystar.musicapp.service.playback.domain.PlaybackEngineLoadResult
import io.github.julystar.musicapp.service.playback.domain.PlaybackEngineUnsupportedReason
import io.github.julystar.musicapp.service.playback.domain.PlaybackPosition
import uniffi.app_backend.DesktopRodioLoadResult
import uniffi.app_backend.DesktopRodioPlayer
import uniffi.app_backend.DspConfiguration
import uniffi.app_backend.NativeDspRuntimeSnapshot
import uniffi.app_backend.ctCreateDesktopRodioPlayer

interface DesktopPlaybackEngine : PlaybackEngine {
    fun takePlaybackCompleted(): Boolean = false

    fun audioDspRuntimeSnapshot(): AudioDspRuntimeSnapshot? = null

    fun configureAudioProcessing(
        effects: AudioEffectSettings,
        playback: PlaybackAdvancedSettings,
        replayGainDb: Float,
    ) = Unit
}

class NoopDesktopPlaybackEngine : DesktopPlaybackEngine {
    override fun load(request: PlaybackEngineLoadRequest): PlaybackEngineLoadResult {
        return PlaybackEngineLoadResult.Unsupported(
            PlaybackEngineUnsupportedReason.MissingPlatformEngine
        )
    }

    override fun play() = Unit

    override fun pause() = Unit

    override fun stop() = Unit

    override fun seekTo(positionMs: Long) = Unit

    override fun readPosition(): PlaybackPosition = PlaybackPosition.Zero

    override fun release() = Unit
}

class RodioDesktopPlaybackEngine internal constructor(
    private val runtime: DesktopRodioRuntime = UniffiDesktopRodioRuntime(),
) : DesktopPlaybackEngine {
    override fun audioDspRuntimeSnapshot(): AudioDspRuntimeSnapshot? {
        return runtime.runtimeSnapshot()?.toDomainAudioDspRuntimeSnapshot()
    }
    override fun configureAudioProcessing(
        effects: AudioEffectSettings,
        playback: PlaybackAdvancedSettings,
        replayGainDb: Float,
    ) {
        runtime.configureAudioProcessing(
            config = effects.toNativeDspConfiguration(inputGainDb = replayGainDb),
            crossfadeDurationMs = playback.crossfadeDurationMs.toULong(),
        )
    }

    override fun load(request: PlaybackEngineLoadRequest): PlaybackEngineLoadResult {
        val resource = request.resource
        if (resource.isExpired(nowEpochMs = System.currentTimeMillis())) {
            return PlaybackEngineLoadResult.Failure(
                PlaybackEngineFailureReason.ExpiredResource
            )
        }
        return if (runtime.load(resource.uri, resource.headers)) {
            PlaybackEngineLoadResult.Ready
        } else {
            PlaybackEngineLoadResult.Unsupported(
                PlaybackEngineUnsupportedReason.UnsupportedResource
            )
        }
    }

    override fun play() {
        runtime.play()
    }

    override fun pause() {
        runtime.pause()
    }

    override fun stop() {
        runtime.stop()
    }

    override fun seekTo(positionMs: Long) {
        runtime.seek(positionMs.coerceAtLeast(0).toULong())
    }

    override fun readPosition(): PlaybackPosition {
        return PlaybackPosition(
            positionMs = runtime.currentPositionMs().coerceAtLeast(0),
            bufferedMs = runtime.bufferedPositionMs().coerceAtLeast(0),
            durationMs = runtime.durationMs().coerceAtLeast(0),
        )
    }

    override fun takePlaybackCompleted(): Boolean = runtime.takePlaybackCompleted()

    override fun release() = stop()
}

internal interface DesktopRodioRuntime {
    fun load(uri: String, headers: Map<String, String>): Boolean
    fun play()
    fun pause()
    fun stop()
    fun seek(ms: ULong)
    fun currentPositionMs(): Long
    fun bufferedPositionMs(): Long
    fun durationMs(): Long
    fun takePlaybackCompleted(): Boolean
    fun runtimeSnapshot(): NativeDspRuntimeSnapshot? = null
    fun configureAudioProcessing(
        config: DspConfiguration,
        crossfadeDurationMs: ULong,
    ) = Unit
}

private class UniffiDesktopRodioRuntime(
    private val player: DesktopRodioPlayer = ctCreateDesktopRodioPlayer(),
) : DesktopRodioRuntime {
    override fun load(uri: String, headers: Map<String, String>): Boolean {
        return player.load(
            uri = uri,
            httpHeaderFields = headers.toHttpHeaderFields(),
        ) == DesktopRodioLoadResult.READY
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun stop() {
        player.stop()
    }

    override fun seek(ms: ULong) {
        player.seek(ms)
    }

    override fun currentPositionMs(): Long = player.currentPositionMs()

    override fun bufferedPositionMs(): Long = player.bufferedPositionMs()

    override fun durationMs(): Long = player.durationMs()

    override fun takePlaybackCompleted(): Boolean = player.takePlaybackCompleted()

    override fun runtimeSnapshot(): NativeDspRuntimeSnapshot = player.runtimeSnapshot()

    override fun configureAudioProcessing(
        config: DspConfiguration,
        crossfadeDurationMs: ULong,
    ) {
        player.configureDsp(
            config = config,
            crossfadeDurationMs = crossfadeDurationMs,
        )
    }
}

private fun Map<String, String>.toHttpHeaderFields(): String {
    return entries
        .filter { (name, value) -> name.isNotBlank() && value.isNotBlank() }
        .joinToString("\n") { (name, value) ->
            "${name.trim()}: ${value.trim()}"
        }
}
