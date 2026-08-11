// Adapted from RawS Music:
// https://github.com/QFDY-GZC/RawS-Music
//
// Original project license: Apache-2.0.
// This implementation has been rewritten and modified for TidePlayer's
// cross-platform Rust DSP pipeline.

use crate::config::{ReverbConfig, ReverbPreset, MAX_CHANNELS};

const DELAY_COUNT: usize = 4;

#[derive(Debug)]
struct DelayLine {
    samples: Vec<f32>,
    cursor: usize,
}

impl DelayLine {
    fn new(length: usize) -> Self {
        Self {
            samples: vec![0.0; length.max(1)],
            cursor: 0,
        }
    }

    fn process(&mut self, input: f32, feedback: f32) -> f32 {
        let delayed = self.samples[self.cursor];
        self.samples[self.cursor] = input + delayed * feedback;
        self.cursor += 1;
        if self.cursor == self.samples.len() {
            self.cursor = 0;
        }
        delayed
    }

    fn reset(&mut self) {
        self.samples.fill(0.0);
        self.cursor = 0;
    }
}

#[derive(Debug)]
pub(crate) struct Reverb {
    enabled: bool,
    wet_current: f32,
    wet_target: f32,
    feedback: f32,
    damping: f32,
    damped: [[f32; DELAY_COUNT]; MAX_CHANNELS],
    delays: [[DelayLine; DELAY_COUNT]; MAX_CHANNELS],
}

impl Reverb {
    pub(crate) fn new(sample_rate: u32) -> Self {
        let delays = make_delays(sample_rate);
        Self {
            enabled: false,
            wet_current: 0.0,
            wet_target: 0.0,
            feedback: 0.35,
            damping: 0.25,
            damped: [[0.0; DELAY_COUNT]; MAX_CHANNELS],
            delays,
        }
    }

    pub(crate) fn configure_format(&mut self, sample_rate: u32) {
        self.delays = make_delays(sample_rate);
        self.damped = [[0.0; DELAY_COUNT]; MAX_CHANNELS];
    }

    pub(crate) fn configure(&mut self, config: ReverbConfig, smooth: bool) {
        self.enabled = config.preset != ReverbPreset::None;
        let (feedback, damping, preset_mix) = match config.preset {
            ReverbPreset::None => (0.0, 0.0, 0.0),
            ReverbPreset::SmallRoom => (0.28, 0.20, 0.65),
            ReverbPreset::MediumRoom => (0.38, 0.24, 0.80),
            ReverbPreset::LargeRoom => (0.48, 0.30, 0.95),
            ReverbPreset::Hall => (0.58, 0.38, 1.0),
            ReverbPreset::Plate => (0.50, 0.16, 0.9),
        };
        self.feedback = feedback;
        self.damping = damping;
        self.wet_target = if self.enabled {
            config.wet * preset_mix
        } else {
            0.0
        };
        if !smooth {
            self.wet_current = self.wet_target;
        }
    }

    pub(crate) fn process_frame(&mut self, frame: &mut [f32; MAX_CHANNELS], channels: usize) {
        if !self.enabled && self.wet_current <= 1.0e-5 {
            return;
        }
        self.wet_current += (self.wet_target - self.wet_current) * 0.002;
        for (channel, sample) in frame.iter_mut().enumerate().take(channels) {
            let dry = *sample;
            let mut wet = 0.0;
            for delay in 0..DELAY_COUNT {
                let delayed = self.delays[channel][delay]
                    .process(dry * 0.25 + self.damped[channel][delay], self.feedback);
                self.damped[channel][delay] +=
                    self.damping * (delayed - self.damped[channel][delay]);
                wet += delayed;
            }
            wet *= 0.25;
            *sample = dry * (1.0 - self.wet_current) + wet * self.wet_current;
        }
    }

    pub(crate) fn reset(&mut self) {
        self.clear_delay_state();
        self.wet_current = self.wet_target;
    }

    pub(crate) fn reset_for_transition(&mut self) {
        self.clear_delay_state();
    }

    fn clear_delay_state(&mut self) {
        for channel in &mut self.delays {
            for delay in channel {
                delay.reset();
            }
        }
        self.damped = [[0.0; DELAY_COUNT]; MAX_CHANNELS];
    }
}

fn make_delays(sample_rate: u32) -> [[DelayLine; DELAY_COUNT]; MAX_CHANNELS] {
    let rate = sample_rate.max(8_000) as f32;
    let lengths = [0.0297, 0.0371, 0.0411, 0.0437];
    std::array::from_fn(|channel| {
        std::array::from_fn(|index| {
            let stereo_offset = if channel == 0 { 0 } else { 23 + index * 7 };
            DelayLine::new((rate * lengths[index]) as usize + stereo_offset)
        })
    })
}
