# DSP effects and parameters

All values below are normalized in Kotlin and sanitized again in Rust. The settings UI displays user-facing integer units; the Rust configuration uses `f32` Hz, dB, milliseconds, ratios, and normalized 0–1 amounts.

## Equalization and tone

| Effect | Parameters and range | Notes |
| --- | --- | --- |
| Graphic EQ | 10 fixed bands, −12…+12 dB; Q 0.10…10; preamp −24…+12 dB | Centers: 31, 62, 125, 250, 500, 1k, 2k, 4k, 8k, 16k Hz. Legacy compatible. |
| Parametric EQ | Up to 40 bands; 10 Hz…Nyquist safety limit; −24…+24 dB; Q 0.05…24; preamp −96…+12 dB | Peak, Low Shelf, High Shelf, Low Pass, High Pass, Band Pass, and Notch. |
| Tone | Bass −24…+24 dB at 50–500 Hz; treble −24…+24 dB at 2–16 kHz | Bass is a real low shelf; treble is a real high shelf. |

Filter coefficients are calculated in double precision and stored/processed as finite `f32`. Active-filter indices are prepared at configuration time. The Rust frequency-response API reports input gain + applied headroom + selected EQ + tone response; the settings page renders 96 log-spaced points from that API and labels this limited scope explicitly.

## Gain staging

| Mode | Range | Behavior |
| --- | --- | --- |
| Off | 0 dB | Preserves the prior pipeline without added attenuation. |
| Automatic | computed, never positive | Samples 256 logarithmically spaced frequencies from 20 Hz to the Nyquist safety limit, reserves the maximum positive EQ/tone response, and smooths changes at buffer boundaries. |
| Manual | −24…0 dB | Applies an explicit global attenuation after ReplayGain/input gain and before EQ. |

Headroom is not copied into sound-effect presets. The meter reports the applied value, and the signal-safety counters distinguish pre-clamp clipping from non-finite-value recovery.

## Dynamics

| Effect | Parameters and range | Behavior |
| --- | --- | --- |
| Compressor | threshold −60…0 dB; ratio 1:1…30:1; attack 0.05–500 ms; release 5–5,000 ms; makeup −12…+24 dB; knee 0–24 dB | One linked detector and gain for both channels. |
| Equal loudness | amount 0–100%; balance −100…100% | Low/high compensation reserves headroom. Balance attenuates one side; it never boosts the other. This is not ReplayGain. |
| Dynamic EQ/de-esser | amount 0–100%; de-esser 0–100%; 4–10 kHz | Shared program and sibilance detectors prevent image drift. |
| Sample-peak limiter | ceiling −12…0 dB; attack 0.01–20 ms; release 5–2,000 ms | Zero-latency linked-channel sample-peak protection. |
| True-peak limiter | ceiling −12…0 dBTP; 4× detection; lookahead 1–10 ms (default 3 ms); release 5–2,000 ms | Windowed-sinc intersample detector, shared stereo gain, fixed-capacity delay, and explicit latency reporting. Enabling it from the default sample-peak settings changes the default ceiling from −0.5 to −1.0 dBTP. |

`latencyFrames` is zero in sample-peak mode. True-peak mode reports `sampleRate × lookaheadMs / 1000`, so the default is 144 frames at 48 kHz and 288 frames at 96 kHz. Reset clears both detector history and delayed audio.

## Bass, stereo, and spatial processing

- **Mono Bass:** crossover 60–300 Hz and amount 0–100%. A low-passed component moves toward mid; higher frequencies remain stereo.
- **Stereo Width:** 0–200% Mid/Side width with conservative headroom (`100%` is unchanged and `0%` is mono).
- **Crossfeed:** 50–1,000 Hz low cut, 500–8,000 Hz high cut, and 0–15 dB cross-channel attenuation.
- **Surround 360:** Woodworth spherical-head ITD, ILD, head-shadow low pass, rear all-pass decorrelation, azimuth, intensity, and automatic rotation.
- **Panoramic 360:** Surround 360 plus elevation-dependent pinna shaping and bounded early reflections/room contribution.

The spatial modes are exclusive. Panoramic 360, Surround 360, and Crossfeed + Stereo Width are never stacked. These effects are not Dolby Atmos, Apple Spatial Audio, or a compatible implementation of either brand.

## Filter, ambience, and output

- **Moog Ladder:** LowPass24, LowPass12, HighPass24, BandPass12, and Notch; cutoff 20 Hz to the Nyquist safety limit, resonance 0–100%, drive 0–18 dB, mix 0–100%. It uses two-times oversampling, nonlinear stages, parameter smoothing, and state recovery.
- **Reverb:** Small Room, Medium Room, Large Room, Hall, and Plate presets with 0–50% wet level. The implementation is a four-delay-per-channel damped feedback network, not the former single feedback delay.
- **Speaker Output:** Elasticity, Powerful, and Wide modes with 0–100% conservative strength and program-dependent headroom.

## Defaults and presets

The effect master switch defaults off. Graphic EQ is the default EQ mode, but it cannot process until the master switch is enabled. Spatial, Moog, dynamic EQ, compressor, reverb, headroom, and true-peak mode default off. The sample-peak limiter defaults enabled inside an active DSP configuration.

Built-in Flat, Bass Boost, Vocal Clarity, and Night Listening profiles are immutable UI presets. User presets receive separate IDs, can be loaded or deleted, and are serialized in `AudioEffectSettings.userPresets`; saving a user preset never replaces a built-in preset.

## Deferred advanced spatial phase

Second-order ambisonics, analytic HRTF, compact BRIR, binaural rendering, FFT convolution, and external convolution IR loading are not implemented. They are intentionally deferred because this repository does not bundle clearly licensed HRTF, BRIR, or IR assets. A later implementation must use independent feature flags, remain disabled without data, audit every dataset license, and keep the ordinary EQ build independent.
