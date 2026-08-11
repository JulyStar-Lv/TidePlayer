// Adapted from RawS Music:
// https://github.com/QFDY-GZC/RawS-Music
//
// Original project license: Apache-2.0.
// This implementation has been rewritten and modified for TidePlayer's
// cross-platform Rust DSP pipeline.

use crate::config::{db_to_linear, smoothing_coefficient, CompressorConfig, MAX_CHANNELS};

#[derive(Debug)]
pub(crate) struct Compressor {
    enabled: bool,
    threshold_db: f32,
    ratio: f32,
    knee_db: f32,
    makeup_gain: f32,
    attack_coefficient: f32,
    release_coefficient: f32,
    envelope: f32,
    gain: f32,
    gain_reduction_db: f32,
}

impl Default for Compressor {
    fn default() -> Self {
        Self {
            enabled: false,
            threshold_db: -18.0,
            ratio: 4.0,
            knee_db: 6.0,
            makeup_gain: 1.0,
            attack_coefficient: 1.0,
            release_coefficient: 1.0,
            envelope: 0.0,
            gain: 1.0,
            gain_reduction_db: 0.0,
        }
    }
}

impl Compressor {
    pub(crate) fn configure(&mut self, sample_rate: u32, config: CompressorConfig) {
        self.enabled = config.enabled;
        self.threshold_db = config.threshold_db;
        self.ratio = config.ratio;
        self.knee_db = config.knee_db;
        self.makeup_gain = db_to_linear(config.makeup_gain_db);
        self.attack_coefficient = smoothing_coefficient(config.attack_ms, sample_rate);
        self.release_coefficient = smoothing_coefficient(config.release_ms, sample_rate);
        if !self.enabled {
            self.gain_reduction_db = 0.0;
        }
    }

    pub(crate) fn process_frame(&mut self, frame: &mut [f32; MAX_CHANNELS], channels: usize) {
        if !self.enabled {
            return;
        }
        let detector = frame[..channels]
            .iter()
            .fold(0.0_f32, |peak, sample| peak.max(sample.abs()));
        let envelope_coefficient = if detector > self.envelope {
            self.attack_coefficient
        } else {
            self.release_coefficient
        };
        self.envelope += (detector - self.envelope) * envelope_coefficient;

        let input_db = 20.0 * self.envelope.max(1.0e-12).log10();
        let over_db = input_db - self.threshold_db;
        let compressed_over_db = if self.knee_db <= 1.0e-4 {
            if over_db > 0.0 {
                over_db * (1.0 / self.ratio - 1.0)
            } else {
                0.0
            }
        } else {
            let half_knee = self.knee_db * 0.5;
            if over_db <= -half_knee {
                0.0
            } else if over_db >= half_knee {
                over_db * (1.0 / self.ratio - 1.0)
            } else {
                let knee_position = over_db + half_knee;
                (1.0 / self.ratio - 1.0) * knee_position * knee_position / (2.0 * self.knee_db)
            }
        };
        let target_gain = db_to_linear(compressed_over_db) * self.makeup_gain;
        let gain_coefficient = if target_gain < self.gain {
            self.attack_coefficient
        } else {
            self.release_coefficient
        };
        self.gain += (target_gain - self.gain) * gain_coefficient;
        if !self.gain.is_finite() {
            self.reset();
            return;
        }
        self.gain_reduction_db = (-20.0 * self.gain.clamp(1.0e-12, 1.0).log10()).max(0.0);
        for sample in frame.iter_mut().take(channels) {
            *sample *= self.gain;
        }
    }

    pub(crate) fn gain_reduction_db(&self) -> f32 {
        self.gain_reduction_db
    }

    pub(crate) fn reset(&mut self) {
        self.envelope = 0.0;
        self.gain = 1.0;
        self.gain_reduction_db = 0.0;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn detector_is_linked_across_stereo_channels() {
        let mut compressor = Compressor::default();
        compressor.configure(
            48_000,
            CompressorConfig {
                enabled: true,
                threshold_db: -24.0,
                ratio: 10.0,
                attack_ms: 0.05,
                release_ms: 100.0,
                ..CompressorConfig::default()
            },
        );
        let mut frame = [1.0, 0.25];
        for _ in 0..256 {
            compressor.process_frame(&mut frame, 2);
            frame = [1.0, 0.25];
        }
        let left_gain = frame[0];
        let right_gain = frame[1] / 0.25;
        assert!((left_gain - right_gain).abs() < 1.0e-5);
        assert!(compressor.gain_reduction_db() > 3.0);
    }

    #[test]
    fn below_threshold_is_not_reduced() {
        let mut compressor = Compressor::default();
        compressor.configure(
            48_000,
            CompressorConfig {
                enabled: true,
                threshold_db: -12.0,
                makeup_gain_db: 0.0,
                ..CompressorConfig::default()
            },
        );
        let mut frame = [0.05, 0.05];
        for _ in 0..512 {
            compressor.process_frame(&mut frame, 2);
            assert!((frame[0] - 0.05).abs() < 1.0e-4);
            frame = [0.05, 0.05];
        }
    }

    #[test]
    fn attack_and_release_time_constants_change_gain_motion() {
        let config = CompressorConfig {
            enabled: true,
            threshold_db: -24.0,
            ratio: 10.0,
            attack_ms: 0.05,
            release_ms: 5.0,
            knee_db: 0.0,
            ..CompressorConfig::default()
        };
        let mut fast = Compressor::default();
        fast.configure(48_000, config);
        let mut slow = Compressor::default();
        slow.configure(
            48_000,
            CompressorConfig {
                attack_ms: 100.0,
                release_ms: 1_000.0,
                ..config
            },
        );

        let mut fast_frame = [1.0, 1.0];
        let mut slow_frame = [1.0, 1.0];
        for _ in 0..64 {
            fast_frame = [1.0, 1.0];
            slow_frame = [1.0, 1.0];
            fast.process_frame(&mut fast_frame, 2);
            slow.process_frame(&mut slow_frame, 2);
        }
        assert!(fast_frame[0] < slow_frame[0] * 0.8);

        let mut fast_release = Compressor::default();
        fast_release.configure(48_000, config);
        let mut slow_release = Compressor::default();
        slow_release.configure(
            48_000,
            CompressorConfig {
                release_ms: 1_000.0,
                ..config
            },
        );
        for _ in 0..1_024 {
            fast_frame = [1.0, 1.0];
            slow_frame = [1.0, 1.0];
            fast_release.process_frame(&mut fast_frame, 2);
            slow_release.process_frame(&mut slow_frame, 2);
        }
        for _ in 0..1_024 {
            fast_frame = [0.01, 0.01];
            slow_frame = [0.01, 0.01];
            fast_release.process_frame(&mut fast_frame, 2);
            slow_release.process_frame(&mut slow_frame, 2);
        }
        assert!(fast_frame[0] > slow_frame[0] * 1.1);
    }

    #[test]
    fn makeup_gain_is_applied_below_threshold() {
        let mut compressor = Compressor::default();
        compressor.configure(
            48_000,
            CompressorConfig {
                enabled: true,
                threshold_db: -6.0,
                makeup_gain_db: 6.0,
                release_ms: 5.0,
                ..CompressorConfig::default()
            },
        );
        let mut frame = [0.05, 0.05];
        for _ in 0..4_096 {
            frame = [0.05, 0.05];
            compressor.process_frame(&mut frame, 2);
        }
        assert!(frame[0] > 0.09);
        assert!((frame[0] - frame[1]).abs() < 1.0e-6);
    }
}
