use std::f32::consts::PI;

use crate::config::MAX_CHANNELS;

pub(crate) const TRUE_PEAK_OVERSAMPLING: usize = 4;
const INTERPOLATOR_TAPS: usize = 16;
const INTERPOLATOR_DELAY_FRAMES: usize = INTERPOLATOR_TAPS / 2;
const MAX_SAMPLE_RATE: usize = 384_000;
const MAX_LOOKAHEAD_MS: usize = 10;
const MAX_LOOKAHEAD_FRAMES: usize = MAX_SAMPLE_RATE * MAX_LOOKAHEAD_MS / 1_000;
const MAX_DELAY_SAMPLES: usize = MAX_LOOKAHEAD_FRAMES * MAX_CHANNELS;

#[derive(Debug)]
pub(crate) struct TruePeakDetector {
    coefficients: [[f32; INTERPOLATOR_TAPS]; TRUE_PEAK_OVERSAMPLING],
    history: [[f32; INTERPOLATOR_TAPS]; MAX_CHANNELS],
    cursor: usize,
}

impl Default for TruePeakDetector {
    fn default() -> Self {
        let mut detector = Self {
            coefficients: [[0.0; INTERPOLATOR_TAPS]; TRUE_PEAK_OVERSAMPLING],
            history: [[0.0; INTERPOLATOR_TAPS]; MAX_CHANNELS],
            cursor: 0,
        };
        detector.prepare_coefficients();
        detector
    }
}

impl TruePeakDetector {
    fn prepare_coefficients(&mut self) {
        let radius = INTERPOLATOR_TAPS as f32 / 2.0;
        for phase in 0..TRUE_PEAK_OVERSAMPLING {
            let fractional = phase as f32 / TRUE_PEAK_OVERSAMPLING as f32;
            let mut sum = 0.0;
            for tap in 0..INTERPOLATOR_TAPS {
                let distance = tap as f32 - INTERPOLATOR_DELAY_FRAMES as f32 + fractional;
                let sinc = if distance.abs() < 1.0e-6 {
                    1.0
                } else {
                    (PI * distance).sin() / (PI * distance)
                };
                let window = if distance.abs() < radius {
                    0.5 + 0.5 * (PI * distance / radius).cos()
                } else {
                    0.0
                };
                let coefficient = sinc * window;
                self.coefficients[phase][tap] = coefficient;
                sum += coefficient;
            }
            if sum.abs() > 1.0e-9 {
                for coefficient in &mut self.coefficients[phase] {
                    *coefficient /= sum;
                }
            }
        }
    }

    pub(crate) fn observe_frame(&mut self, frame: &[f32; MAX_CHANNELS], channels: usize) -> f32 {
        for (channel, sample) in frame.iter().take(channels).enumerate() {
            self.history[channel][self.cursor] = *sample;
        }

        let mut peak = 0.0_f32;
        for phase in 0..TRUE_PEAK_OVERSAMPLING {
            for channel in 0..channels {
                let mut value = 0.0;
                for tap in 0..INTERPOLATOR_TAPS {
                    let index = (self.cursor + INTERPOLATOR_TAPS - tap) % INTERPOLATOR_TAPS;
                    value += self.history[channel][index] * self.coefficients[phase][tap];
                }
                peak = peak.max(value.abs());
            }
        }
        self.cursor = (self.cursor + 1) % INTERPOLATOR_TAPS;
        peak
    }

    pub(crate) fn delay_frames(&self) -> usize {
        INTERPOLATOR_DELAY_FRAMES
    }

    pub(crate) fn reset(&mut self) {
        self.history = [[0.0; INTERPOLATOR_TAPS]; MAX_CHANNELS];
        self.cursor = 0;
    }
}

#[derive(Debug)]
pub(crate) struct LookaheadDelay {
    samples: [f32; MAX_DELAY_SAMPLES],
    frames: usize,
    cursor: usize,
}

impl Default for LookaheadDelay {
    fn default() -> Self {
        Self {
            samples: [0.0; MAX_DELAY_SAMPLES],
            frames: 1,
            cursor: 0,
        }
    }
}

impl LookaheadDelay {
    pub(crate) fn configure(&mut self, sample_rate: u32, lookahead_ms: f32) {
        let frames = ((sample_rate as f32 * lookahead_ms * 0.001).round() as usize)
            .clamp(1, MAX_LOOKAHEAD_FRAMES);
        if frames != self.frames {
            self.frames = frames;
            self.reset();
        }
    }

    pub(crate) fn push(
        &mut self,
        frame: &[f32; MAX_CHANNELS],
        channels: usize,
    ) -> [f32; MAX_CHANNELS] {
        let mut delayed = [0.0; MAX_CHANNELS];
        let base = self.cursor * MAX_CHANNELS;
        delayed[..channels].copy_from_slice(&self.samples[base..base + channels]);
        self.samples[base..base + channels].copy_from_slice(&frame[..channels]);
        self.cursor = (self.cursor + 1) % self.frames;
        delayed
    }

    pub(crate) fn frames(&self) -> usize {
        self.frames
    }

    pub(crate) fn reset(&mut self) {
        self.samples.fill(0.0);
        self.cursor = 0;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn four_times_detector_finds_inter_sample_overshoot() {
        let mut detector = TruePeakDetector::default();
        let signal = [0.0, 0.9, 0.9, -0.9, -0.9, 0.9, 0.9, -0.9, -0.9, 0.0];
        let mut detected = 0.0_f32;
        for _ in 0..3 {
            for sample in signal {
                detected = detected.max(detector.observe_frame(&[sample, sample], 2));
            }
        }
        assert!(detected > 0.9, "detected peak was {detected}");
    }

    #[test]
    fn lookahead_delay_has_exact_frame_latency_and_reset_clears_it() {
        let mut delay = LookaheadDelay::default();
        delay.configure(48_000, 3.0);
        assert_eq!(delay.frames(), 144);
        assert_eq!(delay.push(&[1.0, -1.0], 2), [0.0, 0.0]);
        for _ in 1..144 {
            delay.push(&[0.0, 0.0], 2);
        }
        assert_eq!(delay.push(&[0.0, 0.0], 2), [1.0, -1.0]);
        delay.reset();
        assert_eq!(delay.push(&[0.0, 0.0], 2), [0.0, 0.0]);
    }
}
