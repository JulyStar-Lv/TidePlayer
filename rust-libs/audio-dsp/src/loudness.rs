// Adapted from RawS Music:
// https://github.com/QFDY-GZC/RawS-Music
//
// Original project license: Apache-2.0.
// This implementation has been rewritten and modified for TidePlayer's
// cross-platform Rust DSP pipeline.

use crate::{
    biquad::{Biquad, BiquadCoefficients},
    config::{db_to_linear, BiquadFilterType, LoudnessConfig, ParametricEqBand, MAX_CHANNELS},
};

#[derive(Debug, Default)]
pub(crate) struct LoudnessBalance {
    enabled: bool,
    low: [Biquad; MAX_CHANNELS],
    high: [Biquad; MAX_CHANNELS],
    headroom_gain: f32,
    left_gain: f32,
    right_gain: f32,
}

impl LoudnessBalance {
    pub(crate) fn configure(&mut self, sample_rate: u32, config: LoudnessConfig, smooth: bool) {
        self.enabled = config.enabled;
        let low_gain_db = 9.0 * config.amount;
        let high_gain_db = 4.0 * config.amount;
        let low_coefficients = BiquadCoefficients::for_band(
            sample_rate,
            ParametricEqBand {
                enabled: true,
                filter_type: BiquadFilterType::LowShelf,
                frequency_hz: 160.0,
                gain_db: low_gain_db,
                q: 0.707,
            },
        );
        let high_coefficients = BiquadCoefficients::for_band(
            sample_rate,
            ParametricEqBand {
                enabled: true,
                filter_type: BiquadFilterType::HighShelf,
                frequency_hz: 5_000.0,
                gain_db: high_gain_db,
                q: 0.707,
            },
        );
        for channel in 0..MAX_CHANNELS {
            self.low[channel].set_coefficients(low_coefficients, smooth);
            self.high[channel].set_coefficients(high_coefficients, smooth);
        }
        self.headroom_gain = db_to_linear(-low_gain_db.max(high_gain_db));
        self.left_gain = if config.balance > 0.0 {
            db_to_linear(-24.0 * config.balance)
        } else {
            1.0
        };
        self.right_gain = if config.balance < 0.0 {
            db_to_linear(24.0 * config.balance)
        } else {
            1.0
        };
    }

    pub(crate) fn process_frame(&mut self, frame: &mut [f32; MAX_CHANNELS], channels: usize) {
        if !self.enabled {
            return;
        }
        for (channel, sample) in frame.iter_mut().enumerate().take(channels) {
            let filtered = self.high[channel].process(self.low[channel].process(*sample));
            *sample = filtered
                * self.headroom_gain
                * if channel == 0 {
                    self.left_gain
                } else {
                    self.right_gain
                };
        }
    }

    pub(crate) fn reset(&mut self) {
        for filter in &mut self.low {
            filter.reset();
        }
        for filter in &mut self.high {
            filter.reset();
        }
    }
}
