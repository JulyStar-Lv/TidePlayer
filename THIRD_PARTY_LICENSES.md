# Third-party licenses and notices

This file records third-party work directly relevant to TidePlayer's shared audio DSP implementation. The repository's own license remains GNU GPL v3 as described by [`LICENSE.md`](./LICENSE.md).

## RawS Music

- Project: **RawS Music / RawSMusic**
- Source: <https://github.com/QFDY-GZC/RawS-Music>
- Audited revision: `6c5cb436b3ed4372b32df9145bba756015d082be` (2026-07-21)
- Copyright: 2024–2026 RawSMusic Contributors
- License: Apache License 2.0; a copy is available at [`license/LICENSE-APACHE`](./license/LICENSE-APACHE)

TidePlayer studied and rewrote software DSP ideas from the Apache-2.0 main repository, principally its DSP engine, automatic peak limiter, loudness/balance, mono bass, dynamic EQ, Moog ladder, stereo width, speaker output, stereo scene, ambisonic/spatial, analytic HRTF, compact BRIR, Android binaural spatial, and FFT-convolver source files and their Kotlin/JNI control layer. The resulting implementation is modified for a platform-independent Rust realtime pipeline and is not a verbatim copy of the RawS application.

Attribution headers are present on the adapted/re-written files in `rust-libs/audio-dsp/src`. The bridge and platform adapters are TidePlayer integration code.

The following were explicitly **not imported**:

- RawS application UI, database, media library, scanner, lyrics, or Android playback framework;
- USB exclusive playback, libusb, UAC2, DSD USB output, or the planned separate GPLv3 USB-exclusive native core;
- HRTF, BRIR, impulse-response, music, or other external audio datasets.

The advanced HRTF/BRIR/ambisonics/FFT-convolution phase remains deferred until implementation and dataset licenses can be audited independently.

RawS Music's upstream notice states:

> RawSMusic  
> Copyright 2024-2026 RawSMusic Contributors  
> This repository is distributed under the Apache License, Version 2.0.

## triple_buffer

- Crate: `triple_buffer` 9.0.0
- Source: <https://crates.io/crates/triple_buffer>
- License: Mozilla Public License 2.0

`triple_buffer` transports complete immutable DSP configuration snapshots from control threads to realtime consumers. TidePlayer does not modify the crate. Its source and license are resolved through Cargo; the upstream MPL-2.0 text is also available at <https://www.mozilla.org/MPL/2.0/>.
