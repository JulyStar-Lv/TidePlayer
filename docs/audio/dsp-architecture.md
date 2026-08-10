# Shared Rust DSP architecture

The application uses one platform-independent Rust signal-processing core on Android, iOS, and Desktop. Platform code only delivers decoded PCM, publishes immutable configuration snapshots, exposes runtime telemetry, and manages playback lifecycle.

```text
Compose settings UI
  → AudioEffectSettings (schema version 2)
  → DspConfiguration (shared Kotlin mapper)
  → lock-free Rust configuration snapshot
  → AudioDspProcessor
  → Android Media3 AudioProcessor / iOS MTAudioProcessingTap / Desktop rodio Source
  → atomic runtime status, meter, and timing snapshots
  → AudioDspRuntimeRepository (about 6.7 Hz UI polling)
```

The pure `rust-libs/audio-dsp` crate has no UI, UniFFI, Media3, AVFoundation, or rodio dependency. `rust-libs/app-backend` owns the UniFFI records, low-overhead C/JNI entry points, and Desktop adapter. This keeps algorithm defaults, parameter clamping, processing order, and state behavior identical across platforms.

## Realtime contract

`AudioDspProcessor` accepts interleaved or planar `f32` PCM, mono or stereo, at 8–384 kHz. The tested required rates are 44.1, 48, 88.2, 96, 176.4, and 192 kHz.

The `process_interleaved_f32` and `process_planar_f32` methods:

- allocate no heap memory;
- acquire no locks;
- emit no logs;
- perform no serialization, strings, file, or network work;
- replace non-finite input/state with safe finite output;
- use a low-cost bypass when the configuration has no active processing.

Android PCM buffers cross JNI as direct `ByteBuffer` memory. iOS passes `AudioBufferList` pointers through a thin Objective-C shim. UniFFI is used only for processor ownership, immutable configuration updates, capability queries, and frequency-response analysis; PCM never travels in a UniFFI `List<Float>`.

Format configuration may resize the fixed-lifetime reverb and spatial delay lines. It occurs on prepare/format change, not in the per-buffer processing path. Seek, source replacement, stop, flush, and format changes reset all stateful processors.

## Live configuration

Settings are normalized in the domain layer, converted once to explicit floating-point units, and published through a `triple_buffer` snapshot. The control side may use a mutex to serialize publishers. The audio side reads the newest complete snapshot at a buffer boundary without locking or allocating. Coefficients and targets are prepared during the boundary update; stateful gain/filter transitions use smoothing.

The settings master switch gates every saved effect. ReplayGain remains a separate input gain. If ReplayGain is active while the effect master switch is off, only input gain and the configured sample-peak safety limiter may run.

Headroom is a global playback-safety setting rather than a sound-effect preset. Automatic mode samples the active EQ and tone response at 256 logarithmically spaced frequencies, reserves the largest positive boost, and smooths attenuation at buffer boundaries. Manual mode accepts 0 to −24 dB. Off preserves the Phase 1 behavior.

## Processing order

The order is centralized in `DSP_PIPELINE_ORDER` and tested:

1. input finite-value safety;
2. ReplayGain/input gain and EQ preamp;
3. automatic or manual headroom;
4. graphic or parametric equalizer;
5. bass low shelf and treble high shelf;
6. equal-loudness compensation and attenuation-only balance;
7. mono bass;
8. dynamic EQ and de-esser;
9. two-times-oversampled Moog ladder;
10. stereo-linked compressor;
11. multi-delay damped reverb;
12. one exclusive spatial stage: Panoramic 360, Surround 360, or Crossfeed + Stereo Width;
13. speaker output enhancement;
14. stereo-linked sample-peak or 4× true-peak limiter;
15. final finite-value check and safety clamp.

Crossfade stays at the player level and is not part of the single-stream DSP pipeline.

## Ownership and lifecycle

- Android: `PlaybackService` owns one `RustDspAudioProcessor`; Media3 serializes its audio callbacks. The neutral `Media3AudioRenderersFactory` installs it, and `close()` releases the UniFFI owner.
- iOS: `IosPlaybackEngine` owns `NativeAudioDsp`. `AudioProcessingTap` retains the Rust `Arc` for its callback lifetime and releases it from the tap finalizer. Replacing an `AVPlayerItem` first detaches the old mix.
- Desktop: `DesktopRodioPlayer` owns the current configuration publisher and shared telemetry. Each decoded source owns its processor and fixed 2,048-sample block buffer.

Unsupported sample formats or channel counts fail configuration and use platform-safe fallback/bypass behavior rather than processing an invalid pointer layout.

## Settings and migration

`AudioEffectSettings.schemaVersion` is currently `2`. A structured `AudioEffectProfile` contains effect-specific data classes; legacy ten-band fields remain serialized as mirrors. Version-0 data and old individual DataStore keys migrate into the profile. Version-1 and later profiles are authoritative and regenerate the legacy mirrors without data loss. Headroom is serialized outside presets, while true-peak fields live in the limiter profile. The complete JSON profile, global headroom, and user presets are persisted and included in settings backup/restore.

The UI reads `AudioDspCapabilities`; it does not infer support from the operating system name. Convolution remains false on every platform.

## Source and license boundary

The software algorithms were rewritten with reference to the Apache-2.0 RawS Music main repository. No RawS application UI, database, library scanner, Android player framework, USB playback, libusb, UAC2, DSD USB output, HRTF/BRIR dataset, or planned GPLv3 USB-exclusive native core was imported. See [`THIRD_PARTY_LICENSES.md`](../../THIRD_PARTY_LICENSES.md).
