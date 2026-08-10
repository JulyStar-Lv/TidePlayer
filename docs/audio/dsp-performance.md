# DSP performance and regression checks

## Realtime invariants

The realtime entry points are tested to allocate zero heap objects after format preparation. They use no callback-thread mutex, logger, string formatting, serialization, file access, or network access. True-peak mode uses a fixed-capacity delay line sized for stereo, 384 kHz, and 10 ms; changing the active lookahead only changes indices and latency, not capacity.

The allocation-counting regression test covers:

- transparent bypass;
- graphic EQ and the full Phase 1 effect chain;
- 4× true-peak detection and lookahead limiting;
- interleaved and planar Float32 processing.

## Runtime timing

Every native processing call records elapsed nanoseconds in atomics. The low-frequency snapshot exposes process count, average and maximum processing time, current buffer duration, and `averageProcessingTime / bufferDuration` deadline utilization. These values are diagnostic observations, not scheduling decisions, and they reset with the processor lifecycle.

A deadline utilization below `1.0` means the measured average completed within one buffer duration. Release qualification must also inspect maximum processing time, underruns, thermal throttling, route changes, and long-play stability; average utilization alone cannot prove glitch-free playback.

## Repeatable commands

```bash
cd rust-libs
cargo test --workspace
cargo clippy --workspace --all-targets -- -D warnings
cargo fmt --all -- --check
cargo bench -p audio-dsp --bench pipeline -- --noplot

cd ..
./gradlew :androidApp:assembleDebug
./gradlew :desktopApp:compileKotlinDesktop
./gradlew :core:domain:allTests :feature:settings:allTests :shared:allTests
./gradlew :shared:compileKotlinIosSimulatorArm64
```

The Criterion baseline in [`dsp-platform-support.md`](./dsp-platform-support.md) records machine, toolchain, frame count, sample rate, and the new 4× true-peak measurements. Future percentage comparisons must use an optimized build on the same machine.

## Release-device checklist

Automated builds validate native target compatibility, not speakers, route negotiation, or thermal behavior. Before release, run at least 10–15 minutes on one physical Android arm64 device and one physical iOS arm64 device with EQ boost, automatic headroom, and true-peak mode enabled. Record:

- average/max processing time and deadline utilization;
- underruns or audible discontinuities;
- seek, pause/resume, next-track, route-change, and background/foreground behavior;
- Bluetooth/AirPlay/headset behavior and any visible bypass reason;
- battery/thermal observations.

Physical-device testing is not performed by the repository build and must not be inferred from simulator or cross-compilation success.
