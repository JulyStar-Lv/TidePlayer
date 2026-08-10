# DSP platform support and validation

## Support matrix

| Capability | Android | iOS | Desktop |
| --- | :---: | :---: | :---: |
| Shared Rust algorithms/configuration | Yes | Yes | Yes |
| Mono/stereo PCM | Yes | Yes | Yes |
| 10-band and 40-band parametric EQ | Yes | Yes | Yes |
| Live configuration snapshots | Yes | Yes | Yes |
| Seek/source reset | Yes | Yes | Yes |
| ReplayGain before DSP | Yes | Yes | Yes |
| Frequency-response analysis | Yes | Yes | Yes |
| Convolution/HRTF/BRIR | No | No | No |
| True-peak limiting (4×, lookahead) | Yes | Yes | Yes |
| Runtime status and meter | Yes | Yes | Yes |
| High-resolution DSP output | No (PCM16 fallback) | Yes | Yes |

All Phase 1–5 software effects listed in [`dsp-effects.md`](./dsp-effects.md) are implemented in the same Rust pipeline. iOS advertises resource-dependent support because a tap may not be available for every AVFoundation resource or output route.

## Android Media3

`PlaybackService` constructs its production ExoPlayer with `Media3AudioRenderersFactory` and `RustDspAudioProcessor`. The sink advertises baseline decoded PCM only, so encoded passthrough cannot silently skip DSP; Media3 offload is not enabled. Media3 1.10.1 omits user processors from its high-resolution float-output pipeline, so this adapter uses the stable PCM16 output path and exposes that limitation through `AudioPipelineCapabilities`. The processor also accepts float PCM if Media3 supplies it in a supported path.

Buffers stay direct and are processed in place through JNI. PCM16 conversion uses a fixed 2,048-sample Rust scratch buffer. Flush/seek resets the DSP and format negotiation configures sample rate and channels.

When the effect master is off and ReplayGain is zero, the Rust processor takes its transparent bypass and Media3 skips inactive audio-processor work. The current fixed Media3 sink still decodes to PCM and does not dynamically switch an in-progress item back to hardware offload/passthrough; this is the deliberate tradeoff that guarantees effects can be enabled live without reconstructing ExoPlayer or reloading the item.

MediaSession, background service, notification, lock-screen/headset commands, MediaSource resolution, WebDAV, and SMB loopback resources remain in the original production player.

## iOS AVPlayer

`IosPlaybackEngine` attaches `AudioProcessingTap` through `AVMutableAudioMix` to the first audio track of the current `AVPlayerItem`. The Objective-C shim handles:

- interleaved Float32;
- non-interleaved/planar Float32;
- interleaved signed PCM16;
- format and channel validation;
- callback-thread pointer forwarding;
- Rust owner retain/release;
- discontinuity, unprepare, seek, stop, and item replacement reset.

The attach API distinguishes attached, missing track, creation failure, protected content, and unsupported resource. Unsupported formats, channel counts, missing tracks, tap creation failures, protected content, or AVFoundation routes that do not expose tap PCM are safe, visible bypass conditions. They do not replace or fail the AVPlayer item. Local/HTTP resources still use the existing AVPlayer path, and system now-playing/background controls remain unchanged.

`MTAudioProcessingTap` behavior is controlled by AVFoundation. AirPlay and Bluetooth routes are expected to bypass safely if the tap cannot receive a supported PCM layout; physical-device listening tests are still required for release qualification.

## Desktop rodio

rodio remains responsible for file/HTTP Range decoding and output. Every decoded source is wrapped by `DesktopDspSource`, which:

- batches into a fixed 2,048-sample array;
- runs the shared `AudioDspProcessor`;
- consumes lock-free configuration updates at block boundaries;
- resets on rodio seek.

The Desktop controller passes the complete shared `DspConfiguration`, including headroom, true-peak limiting, parametric EQ, and every advanced effect. ReplayGain is input gain in that configuration. Crossfade remains a two-player volume transition and is not embedded in the DSP.

Local files, HTTP Range, WebDAV URLs, and the SMB localhost gateway continue through the same resource loaders. Output device selection is limited by the existing rodio default-sink implementation.

## Performance

Criterion was run on 2026-08-10 on an Apple M1 Pro, arm64 macOS 26.4, Rust 1.96.0. Each iteration processes 1,024 stereo frames in an optimized benchmark build.

| Benchmark | Time estimate | Buffer duration / realtime margin |
| --- | ---: | ---: |
| Bypass, 44.1 kHz | 7.232 µs | 23.22 ms / ~3,211× |
| Bypass, 48 kHz | 7.298 µs | 21.33 ms / ~2,923× |
| Bypass, 96 kHz | 7.267 µs | 10.67 ms / ~1,468× |
| Bypass, 192 kHz | 7.245 µs | 5.33 ms / ~736× |
| 10-band EQ, 48 kHz | 91.602 µs | 21.33 ms / ~233× |
| 4× true peak, 48 kHz | 63.096 µs | 21.33 ms / ~338× |
| 4× true peak, 96 kHz | 63.422 µs | 10.67 ms / ~168× |
| 40-band PEQ, 48 kHz | 358.67 µs | 21.33 ms / ~59.5× |
| Full effects, 48 kHz | 614.12 µs | 21.33 ms / ~34.7× |

These numbers prove comfortable Desktop headroom on the measured machine, not universal mobile timing. Phase 2 adds per-buffer average/max time, buffer duration, and deadline-utilization telemetry so regressions are observable during playback. Android arm64 and iOS arm64 compile/link checks prove target compatibility; physical-device profiling remains a release test. The allocation-counting test now covers graphic, full-effect, and true-peak processing and reports zero allocations inside the realtime methods.

## Automated checks

Primary commands:

```bash
cd rust-libs
cargo test --workspace
cargo clippy --workspace --all-targets -- -D warnings
cargo fmt --all -- --check
cargo bench -p audio-dsp --bench pipeline -- --noplot

cd ..
./gradlew :androidApp:assembleDebug
./gradlew :desktopApp:compileKotlinDesktop
./gradlew :core:domain:desktopTest
./gradlew :feature:settings:desktopTest
./gradlew :shared:desktopTest
./gradlew :shared:compileDebugKotlinAndroid
./gradlew :shared:compileKotlinIosSimulatorArm64

xcodebuild \
  -project iosApp/App.xcodeproj \
  -scheme App \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO \
  SWIFT_VERSION=5 \
  build
```

The DSP framework, processing tap, and neutral C ABI compile and link in that simulator build. The app target's default Swift 6 build is currently blocked by pre-existing strict-concurrency diagnostics in `AppMain.swift`; the compatibility build above keeps DSP validation separate from that unrelated migration work. It is not a substitute for physical-device validation.

The Rust regression suite generates impulse, logarithmic sweeps, sine/multi-tone, bass, transient, sibilance-like high-frequency bursts, phase-varied stereo, deterministic noise, and silence in code. It checks RMS, peak, DC, direction, finite output, channel safety, reset, required sample rates, every effect stage, extreme Moog modes, and zero allocation for both interleaved and planar processing rather than comparing platform-sensitive WAV bytes.
