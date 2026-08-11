// Adapted from RawS Music:
// https://github.com/QFDY-GZC/RawS-Music
//
// Original project license: Apache-2.0.
// This implementation has been rewritten and modified for TidePlayer's
// cross-platform Rust DSP pipeline.

use std::f32::consts::PI;

use crate::config::{MonoBassConfig, MAX_CHANNELS};

#[derive(Debug, Default)]
pub(crate) struct MonoBass {
    enabled: bool,
    amount: f32,
    coefficient: f32,
    low: [f32; MAX_CHANNELS],
}

impl MonoBass {
    pub(crate) fn configure(&mut self, sample_rate: u32, config: MonoBassConfig) {
        self.enabled = config.enabled;
        self.amount = config.amount;
        self.coefficient =
            1.0 - (-2.0 * PI * config.crossover_hz / sample_rate.max(1) as f32).exp();
    }

    pub(crate) fn process_frame(&mut self, frame: &mut [f32; MAX_CHANNELS], channels: usize) {
        if !self.enabled || channels != 2 {
            return;
        }
        self.low[0] += self.coefficient * (frame[0] - self.low[0]);
        self.low[1] += self.coefficient * (frame[1] - self.low[1]);
        let low_mid = 0.5 * (self.low[0] + self.low[1]);
        frame[0] += (low_mid - self.low[0]) * self.amount;
        frame[1] += (low_mid - self.low[1]) * self.amount;
    }

    pub(crate) fn reset(&mut self) {
        self.low = [0.0; MAX_CHANNELS];
    }
}
