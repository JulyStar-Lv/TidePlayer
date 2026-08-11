// Adapted from RawS Music:
// https://github.com/QFDY-GZC/RawS-Music
//
// Original project license: Apache-2.0.
// This implementation has been rewritten and modified for TidePlayer's
// cross-platform Rust DSP pipeline.

use crate::config::{SpatialAudioConfig, SpatialMode, MAX_CHANNELS};

const SPEED_OF_SOUND_METERS_PER_SECOND: f32 = 343.0;
const DEFAULT_HEAD_RADIUS_METERS: f32 = 0.0875;

#[derive(Debug)]
pub(crate) struct SpatialProcessor {
    sample_rate: u32,
    mode: SpatialMode,
    intensity_current: f32,
    intensity_target: f32,
    azimuth_degrees: f32,
    elevation_degrees: f32,
    auto_rotate_degrees_per_second: f32,
    room_amount: f32,
    delay: [Vec<f32>; MAX_CHANNELS],
    delay_cursor: usize,
    shadow_state: [f32; MAX_CHANNELS],
    allpass_state: [f32; MAX_CHANNELS],
    reflection: [Vec<f32>; MAX_CHANNELS],
    reflection_cursor: usize,
}

impl SpatialProcessor {
    pub(crate) fn new(sample_rate: u32) -> Self {
        let mut result = Self {
            sample_rate,
            mode: SpatialMode::None,
            intensity_current: 0.0,
            intensity_target: 0.0,
            azimuth_degrees: 0.0,
            elevation_degrees: 0.0,
            auto_rotate_degrees_per_second: 0.0,
            room_amount: 0.15,
            delay: [Vec::new(), Vec::new()],
            delay_cursor: 0,
            shadow_state: [0.0; MAX_CHANNELS],
            allpass_state: [0.0; MAX_CHANNELS],
            reflection: [Vec::new(), Vec::new()],
            reflection_cursor: 0,
        };
        result.configure_format(sample_rate);
        result
    }

    pub(crate) fn configure_format(&mut self, sample_rate: u32) {
        self.sample_rate = sample_rate;
        let maximum_itd_frames = ((sample_rate as f32 * 0.0012).ceil() as usize + 2).max(2);
        self.delay = [vec![0.0; maximum_itd_frames], vec![0.0; maximum_itd_frames]];
        let reflection_frames = ((sample_rate as f32 * 0.023).ceil() as usize).max(2);
        self.reflection = [
            vec![0.0; reflection_frames],
            vec![0.0; reflection_frames + 17],
        ];
        self.reset();
    }

    pub(crate) fn configure(&mut self, config: SpatialAudioConfig, smooth: bool) {
        self.mode = config.mode;
        self.intensity_target = if config.mode == SpatialMode::None {
            0.0
        } else {
            config.intensity
        };
        if !smooth {
            self.intensity_current = self.intensity_target;
        }
        self.azimuth_degrees = config.azimuth_degrees;
        self.elevation_degrees = config.elevation_degrees;
        self.auto_rotate_degrees_per_second = config.auto_rotate_degrees_per_second;
        self.room_amount = config.room_amount;
    }

    pub(crate) fn process_frame(&mut self, frame: &mut [f32; MAX_CHANNELS], channels: usize) {
        if channels != 2
            || (self.mode != SpatialMode::Surround360 && self.mode != SpatialMode::Panoramic360)
        {
            return;
        }
        self.intensity_current += (self.intensity_target - self.intensity_current) * 0.003;
        if self.intensity_current <= 1.0e-6 {
            return;
        }
        self.azimuth_degrees = (self.azimuth_degrees
            + self.auto_rotate_degrees_per_second / self.sample_rate.max(1) as f32)
            .rem_euclid(360.0);

        let azimuth = self.azimuth_degrees.to_radians();
        let sin_azimuth = azimuth.sin();
        let source = 0.5 * (frame[0] + frame[1]);
        let dry_side = 0.5 * (frame[0] - frame[1]);

        // Woodworth spherical-head approximation, clamped to the allocated
        // sub-millisecond delay line.
        let absolute_angle = sin_azimuth.abs().asin();
        let itd_seconds = DEFAULT_HEAD_RADIUS_METERS / SPEED_OF_SOUND_METERS_PER_SECOND
            * (absolute_angle + absolute_angle.sin());
        let delay_frames = (itd_seconds * self.sample_rate as f32).round() as usize;
        self.delay[0][self.delay_cursor] = source;
        self.delay[1][self.delay_cursor] = source;
        let left_delay = if sin_azimuth > 0.0 { delay_frames } else { 0 };
        let right_delay = if sin_azimuth < 0.0 { delay_frames } else { 0 };
        let left = read_delay(&self.delay[0], self.delay_cursor, left_delay);
        let right = read_delay(&self.delay[1], self.delay_cursor, right_delay);
        self.delay_cursor += 1;
        if self.delay_cursor == self.delay[0].len() {
            self.delay_cursor = 0;
        }

        let ild = 0.35 * sin_azimuth * self.intensity_current;
        let shadow_coefficient = 0.12;
        self.shadow_state[0] += shadow_coefficient * (left - self.shadow_state[0]);
        self.shadow_state[1] += shadow_coefficient * (right - self.shadow_state[1]);
        let left_shadow = if ild > 0.0 {
            left + (self.shadow_state[0] - left) * ild
        } else {
            left
        };
        let right_shadow = if ild < 0.0 {
            right + (self.shadow_state[1] - right) * -ild
        } else {
            right
        };

        let rear_amount = ((-azimuth.cos()).max(0.0) * self.intensity_current).clamp(0.0, 1.0);
        let allpass_coefficient = 0.55;
        let decorrelated_left = -allpass_coefficient * left_shadow + self.allpass_state[0];
        self.allpass_state[0] = left_shadow + allpass_coefficient * decorrelated_left;
        let decorrelated_right = -allpass_coefficient * right_shadow + self.allpass_state[1];
        self.allpass_state[1] = right_shadow + allpass_coefficient * decorrelated_right;
        let spatial_left = left_shadow + (decorrelated_left - left_shadow) * rear_amount;
        let spatial_right = right_shadow + (decorrelated_right - right_shadow) * rear_amount;

        let mut output_left = spatial_left * (1.0 - ild).clamp(0.5, 1.25);
        let mut output_right = spatial_right * (1.0 + ild).clamp(0.5, 1.25);
        if self.mode == SpatialMode::Panoramic360 {
            let elevation = self.elevation_degrees.to_radians().sin();
            let pinna = elevation * 0.15 * self.intensity_current;
            output_left += (spatial_left - self.shadow_state[0]) * pinna;
            output_right += (spatial_right - self.shadow_state[1]) * pinna;

            let reflection_left = self.reflection[0][self.reflection_cursor];
            let right_reflection_index = self.reflection_cursor % self.reflection[1].len();
            let reflection_right = self.reflection[1][right_reflection_index];
            self.reflection[0][self.reflection_cursor] = output_right * 0.35;
            self.reflection[1][right_reflection_index] = output_left * 0.35;
            self.reflection_cursor += 1;
            if self.reflection_cursor == self.reflection[0].len() {
                self.reflection_cursor = 0;
            }
            output_left += reflection_left * self.room_amount * 0.3;
            output_right += reflection_right * self.room_amount * 0.3;
        }

        let intensity = self.intensity_current;
        let headroom = 1.0 / (1.0 + intensity * 0.25);
        frame[0] =
            (frame[0] * (1.0 - intensity) + (output_left + dry_side * 0.2) * intensity) * headroom;
        frame[1] =
            (frame[1] * (1.0 - intensity) + (output_right - dry_side * 0.2) * intensity) * headroom;
    }

    pub(crate) fn reset(&mut self) {
        for line in &mut self.delay {
            line.fill(0.0);
        }
        for line in &mut self.reflection {
            line.fill(0.0);
        }
        self.delay_cursor = 0;
        self.reflection_cursor = 0;
        self.shadow_state = [0.0; MAX_CHANNELS];
        self.allpass_state = [0.0; MAX_CHANNELS];
        self.intensity_current = self.intensity_target;
    }
}

fn read_delay(line: &[f32], cursor: usize, delay_frames: usize) -> f32 {
    let delay = delay_frames.min(line.len() - 1);
    let index = (cursor + line.len() - delay) % line.len();
    line[index]
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn all_supported_rates_allocate_safe_delay_lines() {
        for rate in [44_100, 48_000, 88_200, 96_000, 176_400, 192_000] {
            let mut processor = SpatialProcessor::new(rate);
            processor.configure(
                SpatialAudioConfig {
                    mode: SpatialMode::Surround360,
                    intensity: 1.0,
                    auto_rotate_degrees_per_second: 180.0,
                    ..SpatialAudioConfig::default()
                },
                false,
            );
            for _ in 0..rate / 10 {
                let mut frame = [0.5, -0.25];
                processor.process_frame(&mut frame, 2);
                assert!(frame.iter().all(|sample| sample.is_finite()));
            }
        }
    }
}
