#import "AudioProcessingTap.h"

#import <AudioToolbox/AudioToolbox.h>
#import <MediaToolbox/MediaToolbox.h>
#import <stdlib.h>

// Implemented by the Rust app_backend static library embedded in SharedKit.
extern int32_t audio_dsp_retain(uint64_t handle);
extern void audio_dsp_release(uint64_t handle);
extern int32_t audio_dsp_configure_format(
    uint64_t handle,
    uint32_t sampleRate,
    uint32_t channels);
extern void audio_dsp_reset(uint64_t handle);
extern int32_t audio_dsp_process_interleaved_f32(
    uint64_t handle,
    float *samples,
    uint32_t frames,
    uint32_t channels);
extern int32_t audio_dsp_process_planar_f32(
    uint64_t handle,
    float **channelBuffers,
    uint32_t frames,
    uint32_t channels);
extern int32_t audio_dsp_process_interleaved_i16(
    uint64_t handle,
    int16_t *samples,
    uint32_t sampleCount);
extern void audio_dsp_set_runtime_bypass(uint64_t handle, int32_t reasonCode);

typedef struct {
    uint64_t dspHandle;
    AudioStreamBasicDescription format;
    bool configured;
} AudioProcessingTapContext;

static void AudioProcessingTapInit(
    MTAudioProcessingTapRef tap,
    void *clientInfo,
    void **tapStorageOut) {
    (void)tap;
    *tapStorageOut = clientInfo;
}

static void AudioProcessingTapFinalize(MTAudioProcessingTapRef tap) {
    AudioProcessingTapContext *context = MTAudioProcessingTapGetStorage(tap);
    if (context == NULL) {
        return;
    }
    audio_dsp_release(context->dspHandle);
    free(context);
}

static void AudioProcessingTapPrepare(
    MTAudioProcessingTapRef tap,
    CMItemCount maxFrames,
    const AudioStreamBasicDescription *processingFormat) {
    (void)maxFrames;
    AudioProcessingTapContext *context = MTAudioProcessingTapGetStorage(tap);
    if (context == NULL || processingFormat == NULL) {
        return;
    }
    context->format = *processingFormat;
    context->configured =
        audio_dsp_configure_format(
            context->dspHandle,
            (uint32_t)processingFormat->mSampleRate,
            processingFormat->mChannelsPerFrame) == 0;
    if (context->configured) {
        audio_dsp_reset(context->dspHandle);
    }
}

static void AudioProcessingTapUnprepare(MTAudioProcessingTapRef tap) {
    AudioProcessingTapContext *context = MTAudioProcessingTapGetStorage(tap);
    if (context != NULL && context->configured) {
        audio_dsp_reset(context->dspHandle);
    }
}

static void AudioProcessingTapProcess(
    MTAudioProcessingTapRef tap,
    CMItemCount requestedFrames,
    MTAudioProcessingTapFlags flags,
    AudioBufferList *bufferListInOut,
    CMItemCount *numberFramesOut,
    MTAudioProcessingTapFlags *flagsOut) {
    (void)flags;
    CMItemCount sourceFrames = 0;
    MTAudioProcessingTapFlags sourceFlags = 0;
    OSStatus status = MTAudioProcessingTapGetSourceAudio(
        tap,
        requestedFrames,
        bufferListInOut,
        &sourceFlags,
        NULL,
        &sourceFrames);
    *numberFramesOut = status == noErr ? sourceFrames : 0;
    *flagsOut = sourceFlags;
    if (status != noErr || sourceFrames <= 0) {
        return;
    }

    AudioProcessingTapContext *context = MTAudioProcessingTapGetStorage(tap);
    if (context == NULL || !context->configured) {
        return;
    }
    if ((sourceFlags & kMTAudioProcessingTapFlag_StartOfStream) != 0) {
        audio_dsp_reset(context->dspHandle);
    }

    const AudioStreamBasicDescription format = context->format;
    const uint32_t channels = format.mChannelsPerFrame;
    const bool isLinearPcm = format.mFormatID == kAudioFormatLinearPCM;
    const bool isFloat = (format.mFormatFlags & kAudioFormatFlagIsFloat) != 0;
    const bool isNonInterleaved =
        (format.mFormatFlags & kAudioFormatFlagIsNonInterleaved) != 0;
    if (!isLinearPcm) {
        audio_dsp_set_runtime_bypass(context->dspHandle, 2);
        return;
    }
    if (channels == 0 || channels > 2) {
        audio_dsp_set_runtime_bypass(context->dspHandle, 3);
        return;
    }

    if (isFloat && format.mBitsPerChannel == 32) {
        if (isNonInterleaved && bufferListInOut->mNumberBuffers >= channels) {
            float *channelBuffers[2] = {NULL, NULL};
            for (uint32_t channel = 0; channel < channels; channel++) {
                channelBuffers[channel] =
                    (float *)bufferListInOut->mBuffers[channel].mData;
                if (channelBuffers[channel] == NULL) {
                    return;
                }
            }
            audio_dsp_process_planar_f32(
                context->dspHandle,
                channelBuffers,
                (uint32_t)sourceFrames,
                channels);
        } else if (
            !isNonInterleaved &&
            bufferListInOut->mNumberBuffers == 1 &&
            bufferListInOut->mBuffers[0].mData != NULL) {
            audio_dsp_process_interleaved_f32(
                context->dspHandle,
                (float *)bufferListInOut->mBuffers[0].mData,
                (uint32_t)sourceFrames,
                channels);
        }
    } else if (
        !isFloat &&
        !isNonInterleaved &&
        format.mBitsPerChannel == 16 &&
        bufferListInOut->mNumberBuffers == 1 &&
        bufferListInOut->mBuffers[0].mData != NULL) {
        audio_dsp_process_interleaved_i16(
            context->dspHandle,
            (int16_t *)bufferListInOut->mBuffers[0].mData,
            (uint32_t)sourceFrames * channels);
    } else {
        audio_dsp_set_runtime_bypass(context->dspHandle, 2);
    }
}

int32_t AudioProcessingTapAttach(AVPlayerItem *item, uint64_t dspHandle) {
    if (item == nil || dspHandle == 0) {
        return AUDIO_PROCESSING_TAP_PROTECTED_OR_UNAVAILABLE;
    }
    if (item.asset.hasProtectedContent) {
        audio_dsp_set_runtime_bypass(dspHandle, 6);
        return AUDIO_PROCESSING_TAP_PROTECTED_OR_UNAVAILABLE;
    }
    AVAssetTrack *audioTrack =
        [[item.asset tracksWithMediaType:AVMediaTypeAudio] firstObject];
    if (audioTrack == nil) {
        return AUDIO_PROCESSING_TAP_NO_AUDIO_TRACK;
    }

    AudioProcessingTapContext *context = calloc(1, sizeof(AudioProcessingTapContext));
    if (context == NULL || audio_dsp_retain(dspHandle) != 0) {
        free(context);
        return AUDIO_PROCESSING_TAP_CREATION_FAILED;
    }
    context->dspHandle = dspHandle;

    MTAudioProcessingTapCallbacks callbacks;
    callbacks.version = kMTAudioProcessingTapCallbacksVersion_0;
    callbacks.clientInfo = context;
    callbacks.init = AudioProcessingTapInit;
    callbacks.finalize = AudioProcessingTapFinalize;
    callbacks.prepare = AudioProcessingTapPrepare;
    callbacks.unprepare = AudioProcessingTapUnprepare;
    callbacks.process = AudioProcessingTapProcess;

    MTAudioProcessingTapRef tap = NULL;
    OSStatus status = MTAudioProcessingTapCreate(
        kCFAllocatorDefault,
        &callbacks,
        kMTAudioProcessingTapCreationFlag_PostEffects,
        &tap);
    if (status != noErr || tap == NULL) {
        audio_dsp_release(dspHandle);
        free(context);
        audio_dsp_set_runtime_bypass(dspHandle, 7);
        return AUDIO_PROCESSING_TAP_CREATION_FAILED;
    }

    AVMutableAudioMixInputParameters *inputParameters =
        [AVMutableAudioMixInputParameters
            audioMixInputParametersWithTrack:audioTrack];
    inputParameters.audioTapProcessor = tap;
    AVMutableAudioMix *audioMix = [AVMutableAudioMix audioMix];
    audioMix.inputParameters = @[inputParameters];
    item.audioMix = audioMix;
    CFRelease(tap);
    return AUDIO_PROCESSING_TAP_ATTACHED;
}

void AudioProcessingTapDetach(AVPlayerItem *item) {
    item.audioMix = nil;
}

void AudioProcessingTapReset(uint64_t dspHandle) {
    if (dspHandle != 0) {
        audio_dsp_reset(dspHandle);
    }
}
