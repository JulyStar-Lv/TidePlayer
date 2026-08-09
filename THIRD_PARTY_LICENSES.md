# Third-party licenses and notices

This file records third-party work and separately licensed repository components that require explicit attribution or license tracking in TidePlayer.

Except where a file or module states otherwise, TidePlayer's repository-wide default license is the GNU General Public License v3.0; see [`LICENSE.md`](./LICENSE.md). Project-origin and upstream-baseline information is documented separately in [`NOTICE.md`](./NOTICE.md).

This document is an attribution and audit index rather than an exhaustive inventory of every transitive dependency. Ordinary unmodified dependencies remain governed by the license metadata and notices distributed by their upstream packages and package registries.

## Repository modules with separate terms

### order-key

- Module: [`rust-libs/order-key`](./rust-libs/order-key)
- License declaration: `MIT OR Apache-2.0`
- License texts: [`license/LICENSE-MIT`](./license/LICENSE-MIT) and [`license/LICENSE-APACHE`](./license/LICENSE-APACHE)

The crate is part of this repository but intentionally retains its explicit dual-license declaration rather than inheriting the repository-wide GPL default for standalone use under the terms stated by the crate.

## Adapted or studied third-party implementations

### RawS Music

- Project: **RawS Music / RawSMusic**
- Source: <https://github.com/QFDY-GZC/RawS-Music>
- Audited revision: `6c5cb436b3ed4372b32df9145bba756015d082be` (2026-07-21)
- Copyright: 2024–2026 RawSMusic Contributors
- License: Apache License 2.0
- Local license text: [`license/LICENSE-APACHE`](./license/LICENSE-APACHE)

TidePlayer studied and rewrote software DSP ideas from the Apache-2.0 main repository, principally its DSP engine, automatic peak limiter, loudness/balance, mono bass, dynamic EQ, Moog ladder, stereo width, speaker output, stereo scene, ambisonic/spatial, analytic HRTF, compact BRIR, Android binaural spatial, and FFT-convolver source files and their Kotlin/JNI control layer. The resulting implementation is modified for a platform-independent Rust realtime pipeline and is not a verbatim copy of the RawS application.

Attribution headers are present on the adapted or rewritten files in `rust-libs/audio-dsp/src`. The bridge and platform adapters are TidePlayer integration code.

The following were explicitly **not imported**:

- RawS application UI, database, media library, scanner, lyrics, or Android playback framework;
- USB exclusive playback, libusb, UAC2, DSD USB output, or the planned separate GPLv3 USB-exclusive native core;
- HRTF, BRIR, impulse-response, music, or other external audio datasets.

The advanced HRTF/BRIR/ambisonics/FFT-convolution phase remains deferred until implementation and dataset licenses can be audited independently.

RawS Music's upstream notice identifies RawSMusic Contributors as copyright holders and states that the repository is distributed under Apache License 2.0.

## Unmodified third-party libraries requiring explicit notice

### triple_buffer

- Crate: `triple_buffer` 9.0.0
- Source: <https://crates.io/crates/triple_buffer>
- License: Mozilla Public License 2.0

`triple_buffer` transports complete immutable DSP configuration snapshots from control threads to realtime consumers. TidePlayer does not modify the crate. Its source and license are resolved through Cargo and remain governed by the upstream MPL-2.0 terms.

## License-file organization

The repository's `license/` directory currently contains reusable license texts needed by separately licensed repository modules or explicitly audited third-party work:

- `LICENSE-APACHE` — Apache License 2.0
- `LICENSE-MIT` — MIT License

The repository-wide GNU GPL v3.0 text is intentionally kept only at [`LICENSE.md`](./LICENSE.md) to avoid duplicate copies drifting out of sync.
