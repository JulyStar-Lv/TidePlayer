package io.github.julystar.musicapp.core.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import io.github.julystar.musicapp.core.domain.model.AudioEffectSettings
import java.nio.ByteBuffer
import uniffi.app_backend.DspRuntimeBypassReason
import uniffi.app_backend.NativeAudioDsp
import uniffi.app_backend.NativeDspRuntimeSnapshot
import uniffi.app_backend.ctCreateAudioDspProcessor

/**
 * Media3 PCM adapter for the shared Rust DSP.
 *
 * Media3 calls this object from one audio-render thread. Configuration is
 * published independently through Rust's lock-free triple buffer.
 */
@OptIn(UnstableApi::class)
internal class RustDspAudioProcessor(
    private val nativeDsp: NativeAudioDsp = ctCreateAudioDspProcessor(),
) : BaseAudioProcessor(), AutoCloseable {
    private val nativeHandle = nativeDsp.nativeHandle().toLong()

    @Volatile
    var lastProcessError: Int = 0
        private set

    fun updateSettings(
        settings: AudioEffectSettings,
        inputGainDb: Float = 0f,
    ) {
        nativeDsp.updateConfig(settings.toNativeDspConfiguration(inputGainDb))
    }

    fun resetDspState() {
        RustDspNative.nativeReset(nativeHandle)
    }

    fun runtimeSnapshot(): NativeDspRuntimeSnapshot = nativeDsp.runtimeSnapshot()

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val supportedEncoding =
            inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
                inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        if (
            !supportedEncoding ||
            inputAudioFormat.channelCount !in 1..2 ||
            inputAudioFormat.sampleRate !in 8_000..384_000
        ) {
            nativeDsp.markBypassed(
                when {
                    !supportedEncoding -> DspRuntimeBypassReason.UNSUPPORTED_SAMPLE_FORMAT
                    inputAudioFormat.channelCount !in 1..2 ->
                        DspRuntimeBypassReason.UNSUPPORTED_CHANNEL_COUNT
                    else -> DspRuntimeBypassReason.UNSUPPORTED_SAMPLE_RATE
                }
            )
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onFlush() {
        val result = RustDspNative.nativeConfigureFormat(
            nativeHandle,
            inputAudioFormat.sampleRate,
            inputAudioFormat.channelCount,
        )
        lastProcessError = result
        if (result == 0) RustDspNative.nativeReset(nativeHandle)
    }

    override fun onReset() {
        resetDspState()
        lastProcessError = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val byteCount = inputBuffer.remaining()
        if (byteCount == 0) return

        val outputBuffer = replaceOutputBuffer(byteCount)
        outputBuffer.put(inputBuffer)
        lastProcessError = when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_FLOAT -> RustDspNative.nativeProcessFloat(
                nativeHandle,
                outputBuffer,
                byteCount / inputAudioFormat.bytesPerFrame,
                inputAudioFormat.channelCount,
            )

            C.ENCODING_PCM_16BIT -> RustDspNative.nativeProcessI16(
                nativeHandle,
                outputBuffer,
                byteCount / Short.SIZE_BYTES,
            )

            else -> -1
        }
        outputBuffer.flip()
    }

    override fun close() {
        nativeDsp.markInactive()
        nativeDsp.close()
    }
}
