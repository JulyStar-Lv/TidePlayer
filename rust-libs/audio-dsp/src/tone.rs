// Adapted from RawS Music:
// https://github.com/QFDY-GZC/RawS-Music
//
// Original project license: Apache-2.0.
// This implementation has been rewritten and modified for TidePlayer's
// cross-platform Rust DSP pipeline.

use crate::{
    biquad::{Biquad, BiquadCoefficients, FrequencyResponse},
    config::{BiquadFilterType, ParametricEqBand, ToneControlConfig, MAX_CHANNELS},
};

#[derive(Debug)]
pub(crate) struct ToneControl {
    sample_rate: u32,
    enabled: bool,
    bass: [Biquad; MAX_CHANNELS],
    treble: [Biquad; MAX_CHANNELS],
    bass_coefficients: BiquadCoefficients,
    treble_coefficients: BiquadCoefficients,
}

impl Default for ToneControl {
    fn default() -> Self {
        Self {
            sample_rate: 48_000,
            enabled: false,
            bass: [Biquad::default(); MAX_CHANNELS],
            treble: [Biquad::default(); MAX_CHANNELS],
            bass_coefficients: BiquadCoefficients::IDENTITY,
            treble_coefficients: BiquadCoefficients::IDENTITY,
        }
    }
}

impl ToneControl {
    pub(crate) fn configure(&mut self, sample_rate: u32, config: ToneControlConfig, smooth: bool) {
        self.sample_rate = sample_rate;
        self.enabled = config.enabled;
        self.bass_coefficients = BiquadCoefficients::for_band(
            sample_rate,
            ParametricEqBand {
                enabled: true,
                filter_type: BiquadFilterType::LowShelf,
                frequency_hz: config.bass_frequency_hz,
                gain_db: config.bass_gain_db,
                q: 0.707,
            },
        );
        self.treble_coefficients = BiquadCoefficients::for_band(
            sample_rate,
            ParametricEqBand {
                enabled: true,
                filter_type: BiquadFilterType::HighShelf,
                frequency_hz: config.treble_frequency_hz,
                gain_db: config.treble_gain_db,
                q: 0.707,
            },
        );
        for channel in 0..MAX_CHANNELS {
            self.bass[channel].set_coefficients(self.bass_coefficients, smooth);
            self.treble[channel].set_coefficients(self.treble_coefficients, smooth);
        }
    }

    pub(crate) fn process_frame(&mut self, frame: &mut [f32; MAX_CHANNELS], channels: usize) {
        if !self.enabled {
            return;
        }
        for (channel, sample) in frame.iter_mut().enumerate().take(channels) {
            *sample = self.treble[channel].process(self.bass[channel].process(*sample));
        }
    }

    pub(crate) fn reset(&mut self) {
        for filter in &mut self.bass {
            filter.reset();
        }
        for filter in &mut self.treble {
            filter.reset();
        }
    }
}

impl FrequencyResponse for ToneControl {
    fn frequency_response_db(&self, frequency_hz: f32) -> f32 {
        if !self.enabled {
            0.0
        } else {
            self.bass_coefficients
                .magnitude_db(frequency_hz, self.sample_rate)
                + self
                    .treble_coefficients
                    .magnitude_db(frequency_hz, self.sample_rate)
        }
    }
}
