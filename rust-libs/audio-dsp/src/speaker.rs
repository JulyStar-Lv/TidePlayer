// Adapted from RawS Music:
// https://github.com/QFDY-GZC/RawS-Music
//
// Original project license: Apache-2.0.
// This implementation has been rewritten and modified for TidePlayer's
// cross-platform Rust DSP pipeline.

use std::f32::consts::PI;

use crate::config::{smoothing_coefficient, SpeakerOutputConfig, SpeakerOutputMode, MAX_CHANNELS};

#[derive(Debug)]
pub(crate) struct SpeakerOutput {
    enabled: bool,
    mode: SpeakerOutputMode,
    strength: f32,
    low_coefficient: f32,
    presence_coefficient: f32,
    envelope_attack: f32,
    envelope_release: f32,
    low: [f32; MAX_CHANNELS],
    presence_low: [f32; MAX_CHANNELS],
    envelope: f32,
    decorrelation: f32,
}

impl Default for SpeakerOutput {
    fn default() -> Self {
        Self {
            enabled: false,
            mode: SpeakerOutputMode::Elasticity,
            strength: 0.5,
            low_coefficient: 0.0,
            presence_coefficient: 0.0,
            envelope_attack: 1.0,
            envelope_release: 1.0,
            low: [0.0; MAX_CHANNELS],
            presence_low: [0.0; MAX_CHANNELS],
            envelope: 0.0,
            decorrelation: 0.0,
        }
    }
}

impl SpeakerOutput {
    pub(crate) fn configure(&mut self, sample_rate: u32, config: SpeakerOutputConfig) {
        self.enabled = config.enabled;
        self.mode = config.mode;
        self.strength = config.strength;
        self.low_coefficient = 1.0 - (-2.0 * PI * 180.0 / sample_rate.max(1) as f32).exp();
        self.presence_coefficient = 1.0 - (-2.0 * PI * 2_500.0 / sample_rate.max(1) as f32).exp();
        self.envelope_attack = smoothing_coefficient(4.0, sample_rate);
        self.envelope_release = smoothing_coefficient(90.0, sample_rate);
    }

    pub(crate) fn process_frame(&mut self, frame: &mut [f32; MAX_CHANNELS], channels: usize) {
        if !self.enabled {
            return;
        }
        let peak = frame[..channels]
            .iter()
            .fold(0.0_f32, |maximum, sample| maximum.max(sample.abs()));
        let coefficient = if peak > self.envelope {
            self.envelope_attack
        } else {
            self.envelope_release
        };
        self.envelope += (peak - self.envelope) * coefficient;
        let available_headroom = (1.0 - self.envelope).clamp(0.0, 1.0);

        match self.mode {
            SpeakerOutputMode::Elasticity => {
                let transient_gain = 1.0 + self.strength * available_headroom * 0.35;
                for sample in frame.iter_mut().take(channels) {
                    *sample *= transient_gain;
                }
            }
            SpeakerOutputMode::Powerful => {
                for (channel, sample) in frame.iter_mut().enumerate().take(channels) {
                    self.low[channel] += self.low_coefficient * (*sample - self.low[channel]);
                    self.presence_low[channel] +=
                        self.presence_coefficient * (*sample - self.presence_low[channel]);
                    let presence = *sample - self.presence_low[channel];
                    let harmonic = (self.low[channel] * 2.0).tanh() - self.low[channel];
                    *sample += (self.low[channel] * 0.20 + presence * 0.10 + harmonic * 0.30)
                        * self.strength
                        * available_headroom;
                }
            }
            SpeakerOutputMode::Wide => {
                if channels != 2 {
                    return;
                }
                self.low[0] += self.low_coefficient * (frame[0] - self.low[0]);
                self.low[1] += self.low_coefficient * (frame[1] - self.low[1]);
                let low_mid = 0.5 * (self.low[0] + self.low[1]);
                let high_left = frame[0] - self.low[0];
                let high_right = frame[1] - self.low[1];
                let side = 0.5 * (high_left - high_right);
                self.decorrelation = -0.55 * self.decorrelation + side;
                let width = self.strength * available_headroom * 0.35;
                frame[0] = low_mid + high_left + self.decorrelation * width;
                frame[1] = low_mid + high_right - self.decorrelation * width;
            }
        }
    }

    pub(crate) fn reset(&mut self) {
        self.low = [0.0; MAX_CHANNELS];
        self.presence_low = [0.0; MAX_CHANNELS];
        self.envelope = 0.0;
        self.decorrelation = 0.0;
    }
}
