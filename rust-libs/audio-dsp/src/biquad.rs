// Adapted from RawS Music:
// https://github.com/QFDY-GZC/RawS-Music
//
// Original project license: Apache-2.0.
// This implementation has been rewritten and modified for TidePlayer's
// cross-platform Rust DSP pipeline.

use std::f64::consts::PI;

use crate::config::{BiquadFilterType, ParametricEqBand};

const COEFFICIENT_SMOOTHING_SAMPLES: u32 = 128;

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct BiquadCoefficients {
    pub b0: f32,
    pub b1: f32,
    pub b2: f32,
    pub a1: f32,
    pub a2: f32,
}

impl Default for BiquadCoefficients {
    fn default() -> Self {
        Self::IDENTITY
    }
}

impl BiquadCoefficients {
    pub const IDENTITY: Self = Self {
        b0: 1.0,
        b1: 0.0,
        b2: 0.0,
        a1: 0.0,
        a2: 0.0,
    };

    pub fn for_band(sample_rate: u32, band: ParametricEqBand) -> Self {
        let band = band.sanitized(sample_rate);
        let sample_rate = sample_rate.max(1) as f64;
        let frequency = band.frequency_hz as f64;
        let q = band.q as f64;
        let gain_db = band.gain_db as f64;
        let omega = (2.0 * PI * frequency / sample_rate).clamp(1.0e-8, 3.0013);
        let sin = omega.sin();
        let cos = omega.cos();
        let alpha = sin / (2.0 * q.max(1.0e-8));
        let amplitude = 10.0_f64.powf(gain_db / 40.0);

        let (b0, b1, b2, a0, a1, a2) = match band.filter_type {
            BiquadFilterType::Peak => (
                1.0 + alpha * amplitude,
                -2.0 * cos,
                1.0 - alpha * amplitude,
                1.0 + alpha / amplitude,
                -2.0 * cos,
                1.0 - alpha / amplitude,
            ),
            BiquadFilterType::LowShelf => {
                let sqrt_a = amplitude.sqrt();
                (
                    amplitude
                        * ((amplitude + 1.0) - (amplitude - 1.0) * cos + 2.0 * sqrt_a * alpha),
                    2.0 * amplitude * ((amplitude - 1.0) - (amplitude + 1.0) * cos),
                    amplitude
                        * ((amplitude + 1.0) - (amplitude - 1.0) * cos - 2.0 * sqrt_a * alpha),
                    (amplitude + 1.0) + (amplitude - 1.0) * cos + 2.0 * sqrt_a * alpha,
                    -2.0 * ((amplitude - 1.0) + (amplitude + 1.0) * cos),
                    (amplitude + 1.0) + (amplitude - 1.0) * cos - 2.0 * sqrt_a * alpha,
                )
            }
            BiquadFilterType::HighShelf => {
                let sqrt_a = amplitude.sqrt();
                (
                    amplitude
                        * ((amplitude + 1.0) + (amplitude - 1.0) * cos + 2.0 * sqrt_a * alpha),
                    -2.0 * amplitude * ((amplitude - 1.0) + (amplitude + 1.0) * cos),
                    amplitude
                        * ((amplitude + 1.0) + (amplitude - 1.0) * cos - 2.0 * sqrt_a * alpha),
                    (amplitude + 1.0) - (amplitude - 1.0) * cos + 2.0 * sqrt_a * alpha,
                    2.0 * ((amplitude - 1.0) - (amplitude + 1.0) * cos),
                    (amplitude + 1.0) - (amplitude - 1.0) * cos - 2.0 * sqrt_a * alpha,
                )
            }
            BiquadFilterType::LowPass => (
                (1.0 - cos) * 0.5,
                1.0 - cos,
                (1.0 - cos) * 0.5,
                1.0 + alpha,
                -2.0 * cos,
                1.0 - alpha,
            ),
            BiquadFilterType::HighPass => (
                (1.0 + cos) * 0.5,
                -(1.0 + cos),
                (1.0 + cos) * 0.5,
                1.0 + alpha,
                -2.0 * cos,
                1.0 - alpha,
            ),
            BiquadFilterType::BandPass => {
                let output_gain = 10.0_f64.powf(gain_db / 20.0);
                (
                    alpha * output_gain,
                    0.0,
                    -alpha * output_gain,
                    1.0 + alpha,
                    -2.0 * cos,
                    1.0 - alpha,
                )
            }
            BiquadFilterType::Notch => (1.0, -2.0 * cos, 1.0, 1.0 + alpha, -2.0 * cos, 1.0 - alpha),
        };
        Self::normalized(b0, b1, b2, a0, a1, a2)
    }

    fn normalized(b0: f64, b1: f64, b2: f64, a0: f64, a1: f64, a2: f64) -> Self {
        if !a0.is_finite() || a0.abs() < 1.0e-15 {
            return Self::IDENTITY;
        }
        let result = Self {
            b0: (b0 / a0) as f32,
            b1: (b1 / a0) as f32,
            b2: (b2 / a0) as f32,
            a1: (a1 / a0) as f32,
            a2: (a2 / a0) as f32,
        };
        if result.is_finite() {
            result
        } else {
            Self::IDENTITY
        }
    }

    pub fn magnitude_db(self, frequency_hz: f32, sample_rate: u32) -> f32 {
        let omega = 2.0 * PI * frequency_hz.max(0.0) as f64 / sample_rate.max(1) as f64;
        let cos_1 = omega.cos();
        let sin_1 = omega.sin();
        let cos_2 = (2.0 * omega).cos();
        let sin_2 = (2.0 * omega).sin();
        let numerator_real = self.b0 as f64 + self.b1 as f64 * cos_1 + self.b2 as f64 * cos_2;
        let numerator_imag = -(self.b1 as f64 * sin_1 + self.b2 as f64 * sin_2);
        let denominator_real = 1.0 + self.a1 as f64 * cos_1 + self.a2 as f64 * cos_2;
        let denominator_imag = -(self.a1 as f64 * sin_1 + self.a2 as f64 * sin_2);
        let numerator = numerator_real * numerator_real + numerator_imag * numerator_imag;
        let denominator = (denominator_real * denominator_real
            + denominator_imag * denominator_imag)
            .max(1.0e-30);
        (10.0 * (numerator / denominator).max(1.0e-30).log10()) as f32
    }

    fn is_finite(self) -> bool {
        self.b0.is_finite()
            && self.b1.is_finite()
            && self.b2.is_finite()
            && self.a1.is_finite()
            && self.a2.is_finite()
    }

    fn step_towards(&mut self, target: Self, remaining: u32) {
        if remaining <= 1 {
            *self = target;
            return;
        }
        let reciprocal = 1.0 / remaining as f32;
        self.b0 += (target.b0 - self.b0) * reciprocal;
        self.b1 += (target.b1 - self.b1) * reciprocal;
        self.b2 += (target.b2 - self.b2) * reciprocal;
        self.a1 += (target.a1 - self.a1) * reciprocal;
        self.a2 += (target.a2 - self.a2) * reciprocal;
    }
}

#[derive(Debug, Clone, Copy)]
pub(crate) struct Biquad {
    coefficients: BiquadCoefficients,
    target: BiquadCoefficients,
    smoothing_remaining: u32,
    x1: f32,
    x2: f32,
    y1: f32,
    y2: f32,
}

impl Default for Biquad {
    fn default() -> Self {
        Self {
            coefficients: BiquadCoefficients::IDENTITY,
            target: BiquadCoefficients::IDENTITY,
            smoothing_remaining: 0,
            x1: 0.0,
            x2: 0.0,
            y1: 0.0,
            y2: 0.0,
        }
    }
}

impl Biquad {
    pub(crate) fn set_coefficients(&mut self, coefficients: BiquadCoefficients, smooth: bool) {
        self.target = coefficients;
        if smooth {
            self.smoothing_remaining = COEFFICIENT_SMOOTHING_SAMPLES;
        } else {
            self.coefficients = coefficients;
            self.smoothing_remaining = 0;
        }
    }

    pub(crate) fn process(&mut self, input: f32) -> f32 {
        if self.smoothing_remaining > 0 {
            self.coefficients
                .step_towards(self.target, self.smoothing_remaining);
            self.smoothing_remaining -= 1;
        }
        let c = self.coefficients;
        let output =
            c.b0 * input + c.b1 * self.x1 + c.b2 * self.x2 - c.a1 * self.y1 - c.a2 * self.y2;
        if !output.is_finite() {
            self.reset();
            return 0.0;
        }
        self.x2 = self.x1;
        self.x1 = input;
        self.y2 = self.y1;
        self.y1 = output;
        output
    }

    pub(crate) fn reset(&mut self) {
        self.x1 = 0.0;
        self.x2 = 0.0;
        self.y1 = 0.0;
        self.y2 = 0.0;
    }
}

pub trait FrequencyResponse {
    fn frequency_response_db(&self, frequency_hz: f32) -> f32;
}

#[cfg(test)]
mod tests {
    use super::*;

    fn band(filter_type: BiquadFilterType, frequency_hz: f32, gain_db: f32) -> ParametricEqBand {
        ParametricEqBand {
            enabled: true,
            filter_type,
            frequency_hz,
            gain_db,
            q: 0.707,
        }
    }

    #[test]
    fn peak_has_requested_center_gain() {
        let coefficients =
            BiquadCoefficients::for_band(48_000, band(BiquadFilterType::Peak, 1_000.0, 6.0));
        assert!((coefficients.magnitude_db(1_000.0, 48_000) - 6.0).abs() < 0.1);
    }

    #[test]
    fn shelf_and_cut_filters_have_expected_trend() {
        let low_shelf =
            BiquadCoefficients::for_band(48_000, band(BiquadFilterType::LowShelf, 120.0, 6.0));
        assert!(
            low_shelf.magnitude_db(40.0, 48_000) > low_shelf.magnitude_db(4_000.0, 48_000) + 4.0
        );
        let high_pass =
            BiquadCoefficients::for_band(48_000, band(BiquadFilterType::HighPass, 500.0, 0.0));
        assert!(
            high_pass.magnitude_db(2_000.0, 48_000) > high_pass.magnitude_db(100.0, 48_000) + 15.0
        );

        let high_shelf =
            BiquadCoefficients::for_band(48_000, band(BiquadFilterType::HighShelf, 4_000.0, 6.0));
        assert!(
            high_shelf.magnitude_db(10_000.0, 48_000)
                > high_shelf.magnitude_db(200.0, 48_000) + 4.0
        );
        let low_pass =
            BiquadCoefficients::for_band(48_000, band(BiquadFilterType::LowPass, 1_000.0, 0.0));
        assert!(
            low_pass.magnitude_db(100.0, 48_000) > low_pass.magnitude_db(10_000.0, 48_000) + 15.0
        );
    }

    #[test]
    fn band_pass_and_notch_have_opposite_center_response() {
        let band_pass =
            BiquadCoefficients::for_band(48_000, band(BiquadFilterType::BandPass, 1_000.0, 0.0));
        assert!(
            band_pass.magnitude_db(1_000.0, 48_000) > band_pass.magnitude_db(100.0, 48_000) + 10.0
        );
        let notch =
            BiquadCoefficients::for_band(48_000, band(BiquadFilterType::Notch, 1_000.0, 0.0));
        assert!(notch.magnitude_db(100.0, 48_000) > notch.magnitude_db(1_000.0, 48_000) + 20.0);
    }
}
