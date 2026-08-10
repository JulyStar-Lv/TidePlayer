// Adapted from RawS Music:
// https://github.com/QFDY-GZC/RawS-Music
//
// Original project license: Apache-2.0.
// This implementation has been rewritten for the shared cross-platform Rust DSP pipeline.

//! Platform-independent, realtime-safe audio DSP.
//!
//! Configuration and format changes are control-boundary operations. The
//! `process_*` methods allocate no memory, acquire no locks, and emit no logs.

mod biquad;
mod compressor;
mod config;
mod dynamic_eq;
mod equalizer;
mod headroom;
mod limiter;
mod loudness;
mod mono_bass;
mod moog;
mod processor;
mod reverb;
mod spatial;
mod speaker;
mod stereo;
mod tone;
mod true_peak;

pub use biquad::{BiquadCoefficients, FrequencyResponse};
pub use config::*;
pub use processor::{AudioDspMeterSnapshot, AudioDspProcessor, DspError, DSP_PIPELINE_ORDER};
