package io.github.julystar.musicapp.core.audio

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

@OptIn(UnstableApi::class)
internal class Media3AudioRenderersFactory(
    context: Context,
    private val dspProcessor: RustDspAudioProcessor,
) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParams: Boolean,
    ): AudioSink {
        @Suppress("DEPRECATION")
        return DefaultAudioSink.Builder()
            .setAudioProcessors(arrayOf<AudioProcessor>(dspProcessor))
            // Media3 skips user AudioProcessors in its high-resolution float
            // output path. A context-free sink advertises only baseline PCM,
            // which prevents encoded passthrough; offload is disabled by
            // DefaultRenderersFactory unless explicitly enabled.
            .setEnableFloatOutput(false)
            .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
            .build()
    }
}
