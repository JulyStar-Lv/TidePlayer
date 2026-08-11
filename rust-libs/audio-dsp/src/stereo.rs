// Adapted from RawS Music:
// https://github.com/QFDY-GZC/RawS-Music
//
// Original project license: Apache-2.0.
// This implementation has been rewritten and modified for TidePlayer's
// cross-platform Rust DSP pipeline.

use crate::{
    biquad::{Biquad, BiquadCoefficients},
    config::{
        db_to_linear, BiquadFilterType, CrossfeedConfig, ParametricEqBand, StereoWidthConfig,
        MAX_CHANNELS,
    },
};

#[derive(Debug, Default)]
pub(crate) struct StereoProcessor {
    width_enabled: bool,
    width_current: f32,
    width_target: f32,
    crossfeed_enabled: bool,
    crossfeed_gain: f32,
    high_pass: [Biquad; MAX_CHANNELS],
    low_pass: [Biquad; MAX_CHANNELS],
}

impl StereoProcessor {
    pub(crate) fn configure(
        &mut self,
        sample_rate: u32,
        width: StereoWidthConfig,
        crossfeed: CrossfeedConfig,
        smooth: bool,
    ) {
        self.width_enabled = width.enabled;
        self.width_target = width.width;
        if !smooth {
            self.width_current = width.width;
        }
        self.crossfeed_enabled = crossfeed.enabled;
        self.crossfeed_gain = db_to_linear(-crossfeed.attenuation_db);
        let high_pass = BiquadCoefficients::for_band(
            sample_rate,
            ParametricEqBand {
                enabled: true,
                filter_type: BiquadFilterType::HighPass,
                frequency_hz: crossfeed.low_cut_hz,
                gain_db: 0.0,
                q: 0.707,
            },
        );
        let low_pass = BiquadCoefficients::for_band(
            sample_rate,
            ParametricEqBand {
                enabled: true,
                filter_type: BiquadFilterType::LowPass,
                frequency_hz: crossfeed.high_cut_hz,
                gain_db: 0.0,
                q: 0.707,
            },
        );
        for channel in 0..MAX_CHANNELS {
            self.high_pass[channel].set_coefficients(high_pass, smooth);
            self.low_pass[channel].set_coefficients(low_pass, smooth);
        }
    }

    pub(crate) fn process_frame(
        &mut self,
        frame: &mut [f32; MAX_CHANNELS],
        channels: usize,
        crossfeed_and_width_allowed: bool,
    ) {
        if channels != 2 || !crossfeed_and_width_allowed {
            return;
        }
        if self.crossfeed_enabled {
            let left_filtered = self.low_pass[0].process(self.high_pass[0].process(frame[0]));
            let right_filtered = self.low_pass[1].process(self.high_pass[1].process(frame[1]));
            let dry_gain = 1.0 / (1.0 + self.crossfeed_gain);
            frame[0] = (frame[0] + right_filtered * self.crossfeed_gain) * dry_gain;
            frame[1] = (frame[1] + left_filtered * self.crossfeed_gain) * dry_gain;
        }
        if self.width_enabled {
            self.width_current += (self.width_target - self.width_current) * 0.005;
            let mid = 0.5 * (frame[0] + frame[1]);
            let side = 0.5 * (frame[0] - frame[1]) * self.width_current;
            let headroom = 1.0 / self.width_current.max(1.0).sqrt();
            frame[0] = (mid + side) * headroom;
            frame[1] = (mid - side) * headroom;
        }
    }

    pub(crate) fn reset(&mut self) {
        for filter in &mut self.high_pass {
            filter.reset();
        }
        for filter in &mut self.low_pass {
            filter.reset();
        }
        self.width_current = self.width_target;
    }
}
