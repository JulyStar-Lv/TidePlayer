// Adapted from RawS Music:
// https://github.com/QFDY-GZC/RawS-Music
//
// Original project license: Apache-2.0.
// This implementation has been rewritten and modified for TidePlayer's
// cross-platform Rust DSP pipeline.

use std::f32::consts::PI;

use crate::config::{smoothing_coefficient, DynamicEqConfig, MAX_CHANNELS};

#[derive(Debug)]
pub(crate) struct DynamicEqualizer {
    enabled: bool,
    amount: f32,
    de_esser_amount: f32,
    bass_coefficient: f32,
    presence_low_coefficient: f32,
    presence_high_coefficient: f32,
    de_esser_coefficient: f32,
    envelope_attack: f32,
    envelope_release: f32,
    bass_state: [f32; MAX_CHANNELS],
    presence_low_state: [f32; MAX_CHANNELS],
    presence_high_state: [f32; MAX_CHANNELS],
    de_esser_low_state: [f32; MAX_CHANNELS],
    program_envelope: f32,
    sibilance_envelope: f32,
}

impl Default for DynamicEqualizer {
    fn default() -> Self {
        Self {
            enabled: false,
            amount: 0.0,
            de_esser_amount: 0.0,
            bass_coefficient: 0.0,
            presence_low_coefficient: 0.0,
            presence_high_coefficient: 0.0,
            de_esser_coefficient: 0.0,
            envelope_attack: 1.0,
            envelope_release: 1.0,
            bass_state: [0.0; MAX_CHANNELS],
            presence_low_state: [0.0; MAX_CHANNELS],
            presence_high_state: [0.0; MAX_CHANNELS],
            de_esser_low_state: [0.0; MAX_CHANNELS],
            program_envelope: 0.0,
            sibilance_envelope: 0.0,
        }
    }
}

impl DynamicEqualizer {
    pub(crate) fn configure(&mut self, sample_rate: u32, config: DynamicEqConfig) {
        self.enabled = config.enabled;
        self.amount = config.amount;
        self.de_esser_amount = config.de_esser_amount;
        self.bass_coefficient = one_pole_coefficient(160.0, sample_rate);
        self.presence_low_coefficient = one_pole_coefficient(1_200.0, sample_rate);
        self.presence_high_coefficient = one_pole_coefficient(4_500.0, sample_rate);
        self.de_esser_coefficient = one_pole_coefficient(config.de_esser_frequency_hz, sample_rate);
        self.envelope_attack = smoothing_coefficient(3.0, sample_rate);
        self.envelope_release = smoothing_coefficient(140.0, sample_rate);
    }

    pub(crate) fn process_frame(&mut self, frame: &mut [f32; MAX_CHANNELS], channels: usize) {
        if !self.enabled {
            return;
        }
        let program_peak = frame[..channels]
            .iter()
            .fold(0.0_f32, |peak, sample| peak.max(sample.abs()));
        let program_coefficient = if program_peak > self.program_envelope {
            self.envelope_attack
        } else {
            self.envelope_release
        };
        self.program_envelope += (program_peak - self.program_envelope) * program_coefficient;

        let mut high_bands = [0.0; MAX_CHANNELS];
        let mut sibilance_peak = 0.0_f32;
        for channel in 0..channels {
            self.de_esser_low_state[channel] +=
                self.de_esser_coefficient * (frame[channel] - self.de_esser_low_state[channel]);
            high_bands[channel] = frame[channel] - self.de_esser_low_state[channel];
            sibilance_peak = sibilance_peak.max(high_bands[channel].abs());
        }
        let sibilance_coefficient = if sibilance_peak > self.sibilance_envelope {
            self.envelope_attack
        } else {
            self.envelope_release
        };
        self.sibilance_envelope +=
            (sibilance_peak - self.sibilance_envelope) * sibilance_coefficient;

        let quietness = (1.0 - self.program_envelope * 2.5).clamp(0.0, 1.0);
        let bass_gain = 0.35 * self.amount * quietness;
        let presence_gain = 0.18 * self.amount * (0.25 + quietness * 0.75);
        let de_esser_gain =
            self.de_esser_amount * ((self.sibilance_envelope - 0.08) / 0.35).clamp(0.0, 1.0) * 0.75;

        for channel in 0..channels {
            let input = frame[channel];
            self.bass_state[channel] += self.bass_coefficient * (input - self.bass_state[channel]);
            self.presence_low_state[channel] +=
                self.presence_low_coefficient * (input - self.presence_low_state[channel]);
            self.presence_high_state[channel] +=
                self.presence_high_coefficient * (input - self.presence_high_state[channel]);
            let presence = self.presence_high_state[channel] - self.presence_low_state[channel];
            frame[channel] =
                input + self.bass_state[channel] * bass_gain + presence * presence_gain
                    - high_bands[channel] * de_esser_gain;
        }
    }

    pub(crate) fn reset(&mut self) {
        self.bass_state = [0.0; MAX_CHANNELS];
        self.presence_low_state = [0.0; MAX_CHANNELS];
        self.presence_high_state = [0.0; MAX_CHANNELS];
        self.de_esser_low_state = [0.0; MAX_CHANNELS];
        self.program_envelope = 0.0;
        self.sibilance_envelope = 0.0;
    }
}

fn one_pole_coefficient(frequency_hz: f32, sample_rate: u32) -> f32 {
    1.0 - (-2.0 * PI * frequency_hz / sample_rate.max(1) as f32).exp()
}
