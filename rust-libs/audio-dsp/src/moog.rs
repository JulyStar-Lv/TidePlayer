// Adapted from RawS Music:
// https://github.com/QFDY-GZC/RawS-Music
//
// Original project license: Apache-2.0.
// This implementation has been rewritten and modified for TidePlayer's
// cross-platform Rust DSP pipeline.

use std::f32::consts::PI;

use crate::config::{db_to_linear, MoogFilterConfig, MoogFilterMode, MAX_CHANNELS};

#[derive(Debug)]
pub(crate) struct MoogLadder {
    enabled: bool,
    mode: MoogFilterMode,
    cutoff_target: f32,
    cutoff_current: f32,
    resonance_target: f32,
    resonance_current: f32,
    drive: f32,
    mix_target: f32,
    mix_current: f32,
    sample_rate: f32,
    state: [[f32; 4]; MAX_CHANNELS],
    previous_input: [f32; MAX_CHANNELS],
}

impl Default for MoogLadder {
    fn default() -> Self {
        Self {
            enabled: false,
            mode: MoogFilterMode::LowPass24,
            cutoff_target: 8_000.0,
            cutoff_current: 8_000.0,
            resonance_target: 0.0,
            resonance_current: 0.0,
            drive: 1.0,
            mix_target: 1.0,
            mix_current: 1.0,
            sample_rate: 48_000.0,
            state: [[0.0; 4]; MAX_CHANNELS],
            previous_input: [0.0; MAX_CHANNELS],
        }
    }
}

impl MoogLadder {
    pub(crate) fn configure(&mut self, sample_rate: u32, config: MoogFilterConfig, smooth: bool) {
        self.sample_rate = sample_rate.max(1) as f32;
        self.enabled = config.enabled;
        self.mode = config.mode;
        self.cutoff_target = config.cutoff_hz;
        self.resonance_target = config.resonance;
        self.drive = db_to_linear(config.drive_db);
        self.mix_target = config.mix;
        if !smooth {
            self.cutoff_current = self.cutoff_target;
            self.resonance_current = self.resonance_target;
            self.mix_current = self.mix_target;
        }
    }

    pub(crate) fn process_frame(&mut self, frame: &mut [f32; MAX_CHANNELS], channels: usize) {
        if !self.enabled {
            return;
        }
        self.cutoff_current += (self.cutoff_target - self.cutoff_current) * 0.002;
        self.resonance_current += (self.resonance_target - self.resonance_current) * 0.002;
        self.mix_current += (self.mix_target - self.mix_current) * 0.002;

        // Two-times oversampling uses a linear midpoint followed by the real
        // sample. This reduces the most obvious high-drive alias components
        // without adding latency or allocating in the callback.
        for (channel, sample) in frame.iter_mut().enumerate().take(channels) {
            let dry = *sample;
            let midpoint = 0.5 * (self.previous_input[channel] + dry);
            let _ = self.process_oversampled(channel, midpoint);
            let wet = self.process_oversampled(channel, dry);
            self.previous_input[channel] = dry;
            *sample = dry + (wet - dry) * self.mix_current;
            if !sample.is_finite() {
                self.state[channel] = [0.0; 4];
                *sample = 0.0;
            }
        }
    }

    fn process_oversampled(&mut self, channel: usize, input: f32) -> f32 {
        let oversampled_rate = self.sample_rate * 2.0;
        let normalized_cutoff =
            (2.0 * PI * self.cutoff_current / oversampled_rate).clamp(0.0001, 1.2);
        let coefficient = normalized_cutoff / (1.0 + normalized_cutoff);
        let resonance = self.resonance_current * 3.8;
        let feedback_input = (input * self.drive - resonance * self.state[channel][3]).tanh();
        let mut stage_input = feedback_input;
        for stage in 0..4 {
            let nonlinear_input = stage_input.tanh();
            self.state[channel][stage] +=
                coefficient * (nonlinear_input - self.state[channel][stage].tanh());
            stage_input = self.state[channel][stage];
        }
        let low_12 = self.state[channel][1];
        let low_24 = self.state[channel][3];
        let high_24 = input - 4.0 * self.state[channel][0] + 6.0 * self.state[channel][1]
            - 4.0 * self.state[channel][2]
            + self.state[channel][3];
        let band_12 = self.state[channel][1] - self.state[channel][3];
        match self.mode {
            MoogFilterMode::LowPass24 => low_24,
            MoogFilterMode::LowPass12 => low_12,
            MoogFilterMode::HighPass24 => high_24,
            MoogFilterMode::BandPass12 => band_12,
            MoogFilterMode::Notch => low_24 + high_24,
        }
    }

    pub(crate) fn reset(&mut self) {
        self.state = [[0.0; 4]; MAX_CHANNELS];
        self.previous_input = [0.0; MAX_CHANNELS];
        self.cutoff_current = self.cutoff_target;
        self.resonance_current = self.resonance_target;
        self.mix_current = self.mix_target;
    }
}
