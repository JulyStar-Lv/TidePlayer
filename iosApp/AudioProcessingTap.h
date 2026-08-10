#import <AVFoundation/AVFoundation.h>
#import <stdbool.h>
#import <stdint.h>

NS_ASSUME_NONNULL_BEGIN

#define AUDIO_PROCESSING_TAP_ATTACHED 0
#define AUDIO_PROCESSING_TAP_NO_AUDIO_TRACK 1
#define AUDIO_PROCESSING_TAP_CREATION_FAILED 2
#define AUDIO_PROCESSING_TAP_PROTECTED_OR_UNAVAILABLE 3
#define AUDIO_PROCESSING_TAP_UNSUPPORTED_FORMAT 4

/// Attaches an in-place post-effects processing tap to the item's first audio track.
int32_t AudioProcessingTapAttach(AVPlayerItem *item, uint64_t dspHandle);

/// Detaches the audio mix and releases the tap after in-flight callbacks finish.
void AudioProcessingTapDetach(AVPlayerItem *item);

/// Clears history after seeks and explicit discontinuities.
void AudioProcessingTapReset(uint64_t dspHandle);

NS_ASSUME_NONNULL_END
