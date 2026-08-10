# DSP runtime status and diagnostics

The settings page reports what the playback pipeline is actually doing; the saved effect switch alone is not treated as proof that DSP is running.

## Status model

| State | Meaning |
| --- | --- |
| `Inactive` | No configured audio buffer is currently being processed. This is normal before playback and after stop/close. |
| `Active` | A supported PCM format is reaching the shared processor and at least one input-gain, headroom, effect, or limiter stage is active. |
| `Bypassed` | Playback continues safely, but DSP is transparent because effects are disabled or a platform/resource/format limitation applies. |
| `Unavailable` | The platform has no usable processing facility for the current pipeline. |
| `Error` | Native processing rejected a buffer or format; the snapshot includes the stable error code. |

Each snapshot also carries sample rate, channel count, PCM format, bypass reason, last error code, and added latency. Bypass reasons distinguish disabled effects, unsupported sample format/channel count/sample rate, unavailable platform processing, protected content, audio-tap failure, unsupported output route, and native processing errors.

## Meter and signal safety

The native processor maintains input peak, output peak, compressor gain reduction, limiter gain reduction, applied headroom, clipped-sample count, and non-finite recovery count. Peaks use a fast attack and visible decay. Clipping is counted before the final safety clamp, so a zero output peak does not hide upstream overload.

Audio callbacks write only atomics that contain numeric snapshots. They do not allocate, log, serialize, or acquire a mutex. Android, iOS, and Desktop controllers publish snapshots every 150 ms (about 6.7 Hz) to `AudioDspRuntimeRepository`. Compose observes those low-frequency `StateFlow` values. Meters are shown only while the state is `Active`.

## Lifecycle behavior

- Android configures status from Media3 format negotiation, records PCM16 or Float32 for every processed buffer, reports unsupported formats as bypass, resets on flush/seek, and becomes inactive when the processor closes.
- iOS returns an explicit attach result before playback. The tap validates interleaved/planar Float32 and interleaved PCM16, reports unsupported layouts from the callback, resets on discontinuity/unprepare/item replacement, and becomes inactive when stopped.
- Desktop shares one atomic telemetry object across the current rodio source, resets state on seek/source replacement/stop, and marks unavailable output or processing failures as bypass reasons without changing the resource loader.

Status changes are written to the existing diagnostics logger from the controller/UI side only. Per-buffer logs are prohibited. The diagnostic fields are state, format, sample rate, channel count, bypass reason, and error code; signal values stay in the meter snapshot.

## Native boundary

The primary C ABI uses neutral `audio_dsp_*` symbols. Temporary `tide_audio_dsp_*` wrappers remain exported for binary compatibility with older app builds. Android JNI, iOS `AudioProcessingTap`, UniFFI records, and Desktop Rust code use the neutral API.
