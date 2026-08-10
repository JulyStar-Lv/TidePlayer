// Adapted from RawS Music:
// https://github.com/QFDY-GZC/RawS-Music
//
// Original project license: Apache-2.0.
// This implementation has been rewritten for the shared cross-platform Rust DSP pipeline.

use thiserror::Error;

use crate::{
    biquad::FrequencyResponse,
    compressor::Compressor,
    config::{db_to_linear, AudioDspConfig, SpatialMode, MAX_CHANNELS},
    dynamic_eq::DynamicEqualizer,
    equalizer::Equalizer,
    headroom::HeadroomManager,
    limiter::PeakLimiter,
    loudness::LoudnessBalance,
    mono_bass::MonoBass,
    moog::MoogLadder,
    reverb::Reverb,
    spatial::SpatialProcessor,
    speaker::SpeakerOutput,
    stereo::StereoProcessor,
    tone::ToneControl,
};

pub const DSP_PIPELINE_ORDER: &[&str] = &[
    "input_safety",
    "replay_gain_and_preamp",
    "headroom",
    "equalizer",
    "bass_treble_shelves",
    "equal_loudness_and_balance",
    "mono_bass",
    "dynamic_eq_and_de_esser",
    "moog_ladder",
    "linked_compressor",
    "reverb",
    "exclusive_spatial_stage",
    "speaker_output",
    "linked_sample_or_true_peak_limiter",
    "final_safety_clamp",
];

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct AudioDspMeterSnapshot {
    pub input_peak: f32,
    pub output_peak: f32,
    pub compressor_gain_reduction_db: f32,
    pub limiter_gain_reduction_db: f32,
    pub clipped_samples: u64,
    pub non_finite_recovery_count: u64,
    pub applied_headroom_db: f32,
}

impl Default for AudioDspMeterSnapshot {
    fn default() -> Self {
        Self {
            input_peak: 0.0,
            output_peak: 0.0,
            compressor_gain_reduction_db: 0.0,
            limiter_gain_reduction_db: 0.0,
            clipped_samples: 0,
            non_finite_recovery_count: 0,
            applied_headroom_db: 0.0,
        }
    }
}

#[derive(Debug, Error, Clone, Copy, PartialEq, Eq)]
pub enum DspError {
    #[error("sample rate {0} Hz is unsupported")]
    UnsupportedSampleRate(u32),
    #[error("channel count {0} is unsupported; only mono and stereo are supported")]
    UnsupportedChannelCount(usize),
    #[error("audio format is not configured")]
    FormatNotConfigured,
    #[error("interleaved sample count {samples} is not divisible by channel count {channels}")]
    MisalignedInterleavedBuffer { samples: usize, channels: usize },
    #[error("planar channel lengths do not match")]
    MismatchedPlanarBufferLengths,
    #[error("frequency and response output lengths do not match")]
    MismatchedResponseBufferLengths,
}

#[derive(Debug)]
pub struct AudioDspProcessor {
    config: AudioDspConfig,
    sample_rate: u32,
    channels: usize,
    format_configured: bool,
    active: bool,
    input_gain_current: f32,
    input_gain_target: f32,
    headroom: HeadroomManager,
    equalizer: Equalizer,
    tone: ToneControl,
    loudness: LoudnessBalance,
    mono_bass: MonoBass,
    dynamic_eq: DynamicEqualizer,
    moog: MoogLadder,
    compressor: Compressor,
    reverb: Reverb,
    stereo: StereoProcessor,
    spatial: SpatialProcessor,
    speaker_output: SpeakerOutput,
    limiter: PeakLimiter,
    meter: AudioDspMeterSnapshot,
    meter_release: f32,
}

impl AudioDspProcessor {
    pub fn new(config: AudioDspConfig) -> Result<Self, DspError> {
        let sample_rate = 48_000;
        let mut processor = Self {
            config: AudioDspConfig::default(),
            sample_rate,
            channels: 2,
            format_configured: false,
            active: false,
            input_gain_current: 1.0,
            input_gain_target: 1.0,
            headroom: HeadroomManager::default(),
            equalizer: Equalizer::default(),
            tone: ToneControl::default(),
            loudness: LoudnessBalance::default(),
            mono_bass: MonoBass::default(),
            dynamic_eq: DynamicEqualizer::default(),
            moog: MoogLadder::default(),
            compressor: Compressor::default(),
            reverb: Reverb::new(sample_rate),
            stereo: StereoProcessor::default(),
            spatial: SpatialProcessor::new(sample_rate),
            speaker_output: SpeakerOutput::default(),
            limiter: PeakLimiter::default(),
            meter: AudioDspMeterSnapshot::default(),
            meter_release: 0.9999,
        };
        processor.apply_config(config, false);
        Ok(processor)
    }

    pub fn configure_format(&mut self, sample_rate: u32, channels: usize) -> Result<(), DspError> {
        if !(8_000..=384_000).contains(&sample_rate) {
            return Err(DspError::UnsupportedSampleRate(sample_rate));
        }
        if !(1..=MAX_CHANNELS).contains(&channels) {
            return Err(DspError::UnsupportedChannelCount(channels));
        }
        let format_changed =
            !self.format_configured || sample_rate != self.sample_rate || channels != self.channels;
        self.sample_rate = sample_rate;
        self.channels = channels;
        self.format_configured = true;
        self.meter_release = (-1.0 / (sample_rate as f32 * 0.3)).exp();
        if format_changed {
            self.reverb.configure_format(sample_rate);
            self.spatial.configure_format(sample_rate);
            self.apply_config(self.config, false);
            self.reset();
        }
        Ok(())
    }

    pub fn update_config(&mut self, config: AudioDspConfig) -> Result<(), DspError> {
        let previous = self.config;
        self.apply_config(config, self.format_configured);
        self.reset_transitioned_state(previous);
        Ok(())
    }

    fn reset_transitioned_state(&mut self, previous: AudioDspConfig) {
        if previous.enabled != self.config.enabled {
            self.reset();
            return;
        }

        let previous_eq_enabled = match previous.eq_mode {
            crate::config::EqMode::Graphic => previous.graphic_equalizer.enabled,
            crate::config::EqMode::Parametric => previous.parametric_equalizer.enabled,
        };
        let eq_enabled = match self.config.eq_mode {
            crate::config::EqMode::Graphic => self.config.graphic_equalizer.enabled,
            crate::config::EqMode::Parametric => self.config.parametric_equalizer.enabled,
        };
        if previous.eq_mode != self.config.eq_mode || previous_eq_enabled != eq_enabled {
            self.equalizer.reset();
        }
        if previous.tone.enabled != self.config.tone.enabled {
            self.tone.reset();
        }
        if previous.loudness.enabled != self.config.loudness.enabled {
            self.loudness.reset();
        }
        if previous.mono_bass.enabled != self.config.mono_bass.enabled {
            self.mono_bass.reset();
        }
        if previous.dynamic_eq.enabled != self.config.dynamic_eq.enabled {
            self.dynamic_eq.reset();
        }
        if previous.moog.enabled != self.config.moog.enabled
            || previous.moog.mode != self.config.moog.mode
        {
            self.moog.reset();
        }
        if previous.compressor.enabled != self.config.compressor.enabled {
            self.compressor.reset();
        }
        if previous.reverb.preset != self.config.reverb.preset {
            self.reverb.reset_for_transition();
        }
        if previous.stereo_width.enabled != self.config.stereo_width.enabled
            || previous.crossfeed.enabled != self.config.crossfeed.enabled
        {
            self.stereo.reset();
        }
        if previous.spatial.mode != self.config.spatial.mode {
            self.spatial.reset();
        }
        if previous.speaker_output.enabled != self.config.speaker_output.enabled
            || previous.speaker_output.mode != self.config.speaker_output.mode
        {
            self.speaker_output.reset();
        }
        if previous.limiter != self.config.limiter {
            self.limiter.reset();
        }
    }

    fn apply_config(&mut self, config: AudioDspConfig, smooth: bool) {
        let config = config.sanitized(self.sample_rate);
        self.active = config.has_active_effects();
        self.input_gain_target = db_to_linear(config.input_gain_db);
        if !smooth {
            self.input_gain_current = self.input_gain_target;
        }
        self.equalizer.configure(
            self.sample_rate,
            config.eq_mode,
            config.graphic_equalizer,
            config.parametric_equalizer,
            smooth,
        );
        self.tone.configure(self.sample_rate, config.tone, smooth);
        let estimated_positive_response_db = self.estimate_eq_tone_boost_db();
        self.headroom
            .configure(config.headroom, estimated_positive_response_db, smooth);
        self.loudness
            .configure(self.sample_rate, config.loudness, smooth);
        self.mono_bass.configure(self.sample_rate, config.mono_bass);
        self.dynamic_eq
            .configure(self.sample_rate, config.dynamic_eq);
        self.moog.configure(self.sample_rate, config.moog, smooth);
        self.compressor
            .configure(self.sample_rate, config.compressor);
        self.reverb.configure(config.reverb, smooth);
        self.stereo.configure(
            self.sample_rate,
            config.stereo_width,
            config.crossfeed,
            smooth,
        );
        self.spatial.configure(config.spatial, smooth);
        self.speaker_output
            .configure(self.sample_rate, config.speaker_output);
        self.limiter.configure(self.sample_rate, config.limiter);
        self.config = config;
    }

    pub fn process_interleaved_f32(&mut self, samples: &mut [f32]) -> Result<(), DspError> {
        self.ensure_format()?;
        if !samples.len().is_multiple_of(self.channels) {
            return Err(DspError::MisalignedInterleavedBuffer {
                samples: samples.len(),
                channels: self.channels,
            });
        }
        for source_frame in samples.chunks_exact_mut(self.channels) {
            let mut frame = [0.0; MAX_CHANNELS];
            frame[..self.channels].copy_from_slice(source_frame);
            self.process_frame(&mut frame);
            source_frame.copy_from_slice(&frame[..self.channels]);
        }
        Ok(())
    }

    pub fn process_planar_f32(&mut self, channels: &mut [&mut [f32]]) -> Result<(), DspError> {
        self.ensure_format()?;
        if channels.len() != self.channels {
            return Err(DspError::UnsupportedChannelCount(channels.len()));
        }
        let frame_count = channels.first().map_or(0, |channel| channel.len());
        if channels.iter().any(|channel| channel.len() != frame_count) {
            return Err(DspError::MismatchedPlanarBufferLengths);
        }
        let mut frame_index = 0;
        while frame_index < frame_count {
            let mut frame = [0.0; MAX_CHANNELS];
            for channel in 0..self.channels {
                frame[channel] = channels[channel][frame_index];
            }
            self.process_frame(&mut frame);
            for channel in 0..self.channels {
                channels[channel][frame_index] = frame[channel];
            }
            frame_index += 1;
        }
        Ok(())
    }

    fn process_frame(&mut self, frame: &mut [f32; MAX_CHANNELS]) {
        let mut input_peak = 0.0_f32;
        for sample in frame.iter_mut().take(self.channels) {
            if sample.is_finite() {
                input_peak = input_peak.max(sample.abs());
            } else {
                self.meter.non_finite_recovery_count =
                    self.meter.non_finite_recovery_count.saturating_add(1);
                *sample = 0.0;
            }
        }
        self.meter.input_peak = (self.meter.input_peak * self.meter_release).max(input_peak);
        if !self.active {
            self.update_output_peak(frame);
            return;
        }

        self.input_gain_current += (self.input_gain_target - self.input_gain_current) * 0.005;
        for sample in frame.iter_mut().take(self.channels) {
            *sample *= self.input_gain_current;
        }
        self.headroom.process_frame(frame, self.channels);
        self.equalizer.process_frame(frame, self.channels);
        self.tone.process_frame(frame, self.channels);
        self.loudness.process_frame(frame, self.channels);
        self.mono_bass.process_frame(frame, self.channels);
        self.dynamic_eq.process_frame(frame, self.channels);
        self.moog.process_frame(frame, self.channels);
        self.compressor.process_frame(frame, self.channels);
        self.reverb.process_frame(frame, self.channels);

        let crossfeed_and_width_allowed =
            self.config.spatial.mode == SpatialMode::CrossfeedAndWidth;
        self.spatial.process_frame(frame, self.channels);
        self.stereo
            .process_frame(frame, self.channels, crossfeed_and_width_allowed);
        self.speaker_output.process_frame(frame, self.channels);
        self.limiter.process_frame(frame, self.channels);
        for sample in frame.iter_mut().take(self.channels) {
            *sample = if sample.is_finite() {
                if sample.abs() > 1.0 {
                    self.meter.clipped_samples = self.meter.clipped_samples.saturating_add(1);
                }
                sample.clamp(-1.0, 1.0)
            } else {
                self.meter.non_finite_recovery_count =
                    self.meter.non_finite_recovery_count.saturating_add(1);
                0.0
            };
        }
        self.update_output_peak(frame);
    }

    fn update_output_peak(&mut self, frame: &[f32; MAX_CHANNELS]) {
        let peak = frame[..self.channels]
            .iter()
            .fold(0.0_f32, |maximum, sample| maximum.max(sample.abs()));
        self.meter.output_peak = (self.meter.output_peak * self.meter_release).max(peak);
    }

    fn estimate_eq_tone_boost_db(&self) -> f32 {
        const RESPONSE_SAMPLES: usize = 256;
        let minimum_hz = 20.0_f32;
        let maximum_hz = (self.sample_rate as f32 * 0.45)
            .min(20_000.0)
            .max(minimum_hz);
        let ratio = maximum_hz / minimum_hz;
        let mut maximum_db = 0.0_f32;
        for index in 0..RESPONSE_SAMPLES {
            let position = index as f32 / (RESPONSE_SAMPLES - 1) as f32;
            let frequency = minimum_hz * ratio.powf(position);
            let response = self.equalizer.frequency_response_db(frequency)
                + self.tone.frequency_response_db(frequency);
            maximum_db = maximum_db.max(response);
        }
        maximum_db
    }

    pub fn calculate_frequency_response(
        &self,
        frequencies_hz: &[f32],
        response_db: &mut [f32],
    ) -> Result<(), DspError> {
        if frequencies_hz.len() != response_db.len() {
            return Err(DspError::MismatchedResponseBufferLengths);
        }
        for (frequency, response) in frequencies_hz.iter().copied().zip(response_db.iter_mut()) {
            *response = self.config.input_gain_db
                + self.headroom.applied_db()
                + self.equalizer.frequency_response_db(frequency)
                + self.tone.frequency_response_db(frequency);
        }
        Ok(())
    }

    pub fn reset(&mut self) {
        self.equalizer.reset();
        self.tone.reset();
        self.headroom.reset();
        self.loudness.reset();
        self.mono_bass.reset();
        self.dynamic_eq.reset();
        self.moog.reset();
        self.compressor.reset();
        self.reverb.reset();
        self.stereo.reset();
        self.spatial.reset();
        self.speaker_output.reset();
        self.limiter.reset();
        self.input_gain_current = self.input_gain_target;
        self.meter = AudioDspMeterSnapshot {
            applied_headroom_db: self.headroom.applied_db(),
            ..AudioDspMeterSnapshot::default()
        };
    }

    pub fn latency_frames(&self) -> usize {
        self.limiter.latency_frames()
    }

    pub fn limiter_gain_reduction_db(&self) -> f32 {
        self.limiter.gain_reduction_db()
    }

    pub fn compressor_gain_reduction_db(&self) -> f32 {
        self.compressor.gain_reduction_db()
    }

    pub fn meter_snapshot(&self) -> AudioDspMeterSnapshot {
        AudioDspMeterSnapshot {
            compressor_gain_reduction_db: self.compressor.gain_reduction_db(),
            limiter_gain_reduction_db: self.limiter.gain_reduction_db(),
            applied_headroom_db: self.headroom.applied_db(),
            ..self.meter
        }
    }

    pub fn has_active_effects(&self) -> bool {
        self.active
    }

    pub fn format(&self) -> Option<(u32, usize)> {
        self.format_configured
            .then_some((self.sample_rate, self.channels))
    }

    fn ensure_format(&self) -> Result<(), DspError> {
        if self.format_configured {
            Ok(())
        } else {
            Err(DspError::FormatNotConfigured)
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::config::{
        BiquadFilterType, EqMode, GraphicEqualizerConfig, HeadroomConfig, HeadroomMode,
        LimiterConfig, ParametricEqBand, ParametricEqualizerConfig,
    };

    fn configured(config: AudioDspConfig, sample_rate: u32, channels: usize) -> AudioDspProcessor {
        let mut processor = AudioDspProcessor::new(config).unwrap();
        processor.configure_format(sample_rate, channels).unwrap();
        processor
    }

    #[test]
    fn bypass_is_transparent_except_non_finite_safety() {
        let mut processor = configured(AudioDspConfig::default(), 48_000, 2);
        let mut samples = [0.25, -0.5, f32::NAN, f32::INFINITY];
        processor.process_interleaved_f32(&mut samples).unwrap();
        assert_eq!(samples, [0.25, -0.5, 0.0, 0.0]);
    }

    #[test]
    fn supports_required_sample_rates_and_mono_stereo() {
        for sample_rate in [44_100, 48_000, 88_200, 96_000, 176_400, 192_000] {
            for channels in [1, 2] {
                let mut processor = AudioDspProcessor::new(AudioDspConfig::default()).unwrap();
                processor.configure_format(sample_rate, channels).unwrap();
                let mut samples = [0.0; 16];
                processor
                    .process_interleaved_f32(&mut samples[..16 / channels * channels])
                    .unwrap();
            }
        }
    }

    #[test]
    fn invalid_formats_and_buffer_layouts_return_explicit_errors() {
        let mut processor = AudioDspProcessor::new(AudioDspConfig::default()).unwrap();
        assert_eq!(
            processor.configure_format(7_999, 2),
            Err(DspError::UnsupportedSampleRate(7_999))
        );
        assert_eq!(
            processor.configure_format(48_000, 3),
            Err(DspError::UnsupportedChannelCount(3))
        );
        processor.configure_format(48_000, 2).unwrap();

        assert_eq!(
            processor.process_interleaved_f32(&mut [0.0; 3]),
            Err(DspError::MisalignedInterleavedBuffer {
                samples: 3,
                channels: 2,
            })
        );
        let mut mono = [0.0; 4];
        assert_eq!(
            processor.process_planar_f32(&mut [&mut mono]),
            Err(DspError::UnsupportedChannelCount(1))
        );
        let mut left = [0.0; 4];
        let mut right = [0.0; 3];
        assert_eq!(
            processor.process_planar_f32(&mut [&mut left, &mut right]),
            Err(DspError::MismatchedPlanarBufferLengths)
        );
    }

    #[test]
    fn planar_and_interleaved_match() {
        let config = AudioDspConfig {
            enabled: true,
            tone: crate::config::ToneControlConfig {
                enabled: true,
                bass_gain_db: 4.0,
                treble_gain_db: -2.0,
                ..Default::default()
            },
            ..Default::default()
        };
        let mut interleaved_processor = configured(config, 48_000, 2);
        let mut planar_processor = configured(config, 48_000, 2);
        let mut interleaved = [0.0; 128];
        let mut left = [0.0; 64];
        let mut right = [0.0; 64];
        for frame in 0..64 {
            let sample = (frame as f32 * 0.1).sin() * 0.25;
            interleaved[frame * 2] = sample;
            interleaved[frame * 2 + 1] = -sample;
            left[frame] = sample;
            right[frame] = -sample;
        }
        interleaved_processor
            .process_interleaved_f32(&mut interleaved)
            .unwrap();
        planar_processor
            .process_planar_f32(&mut [&mut left, &mut right])
            .unwrap();
        for frame in 0..64 {
            assert!((interleaved[frame * 2] - left[frame]).abs() < 1.0e-6);
            assert!((interleaved[frame * 2 + 1] - right[frame]).abs() < 1.0e-6);
        }
    }

    #[test]
    fn parametric_response_matches_center_gain() {
        let mut parametric = ParametricEqualizerConfig {
            enabled: true,
            band_count: 1,
            ..Default::default()
        };
        parametric.bands[0] = ParametricEqBand {
            enabled: true,
            filter_type: BiquadFilterType::Peak,
            frequency_hz: 1_000.0,
            gain_db: 6.0,
            q: 1.0,
        };
        let processor = configured(
            AudioDspConfig {
                enabled: true,
                eq_mode: EqMode::Parametric,
                parametric_equalizer: parametric,
                ..Default::default()
            },
            48_000,
            2,
        );
        let mut response = [0.0];
        processor
            .calculate_frequency_response(&[1_000.0], &mut response)
            .unwrap();
        assert!((response[0] - 6.0).abs() < 0.1);
    }

    #[test]
    fn reset_clears_stateful_tail() {
        let config = AudioDspConfig {
            enabled: true,
            reverb: crate::config::ReverbConfig {
                preset: crate::config::ReverbPreset::Hall,
                wet: 0.3,
            },
            ..Default::default()
        };
        let mut processor = configured(config, 48_000, 2);
        let mut impulse = [0.0; 8_192];
        impulse[0] = 1.0;
        impulse[1] = 1.0;
        processor.process_interleaved_f32(&mut impulse).unwrap();
        processor.reset();
        let mut silence = [0.0; 8_192];
        processor.process_interleaved_f32(&mut silence).unwrap();
        assert!(silence.iter().all(|sample| sample.abs() < 1.0e-8));
    }

    #[test]
    fn live_effect_toggles_do_not_restore_stale_dynamics_state() {
        let active = AudioDspConfig {
            enabled: true,
            compressor: crate::config::CompressorConfig {
                enabled: true,
                threshold_db: -36.0,
                ratio: 20.0,
                attack_ms: 0.01,
                release_ms: 1_000.0,
                ..Default::default()
            },
            limiter: crate::config::LimiterConfig {
                enabled: true,
                ceiling_db: -12.0,
                attack_ms: 0.01,
                release_ms: 1_000.0,
                ..Default::default()
            },
            ..Default::default()
        };
        let mut processor = configured(active, 48_000, 2);
        let mut loud = [1.0; 4_096];
        processor.process_interleaved_f32(&mut loud).unwrap();
        assert!(processor.compressor_gain_reduction_db() > 6.0);
        assert!(processor.limiter_gain_reduction_db() > 3.0);

        processor
            .update_config(AudioDspConfig {
                compressor: crate::config::CompressorConfig {
                    enabled: false,
                    ..active.compressor
                },
                limiter: crate::config::LimiterConfig {
                    enabled: false,
                    ..active.limiter
                },
                ..active
            })
            .unwrap();
        processor.update_config(active).unwrap();

        let mut quiet = [0.01, 0.01];
        processor.process_interleaved_f32(&mut quiet).unwrap();
        assert!(quiet[0] > 0.009);
        assert!(quiet[1] > 0.009);
    }

    #[test]
    fn pipeline_order_is_centralized_and_limiter_is_last_effect() {
        assert_eq!(
            DSP_PIPELINE_ORDER[DSP_PIPELINE_ORDER.len() - 2],
            "linked_sample_or_true_peak_limiter"
        );
        assert_eq!(
            DSP_PIPELINE_ORDER[DSP_PIPELINE_ORDER.len() - 1],
            "final_safety_clamp"
        );
    }

    #[test]
    fn automatic_headroom_prevents_positive_eq_response_from_hard_clipping() {
        let mut gains = [0.0; crate::config::GRAPHIC_EQ_BAND_COUNT];
        gains[5] = 12.0;
        let config = AudioDspConfig {
            enabled: true,
            headroom: HeadroomConfig {
                mode: HeadroomMode::Automatic,
                manual_db: 0.0,
            },
            graphic_equalizer: GraphicEqualizerConfig {
                enabled: true,
                gains_db: gains,
                ..GraphicEqualizerConfig::default()
            },
            limiter: LimiterConfig {
                enabled: false,
                ..LimiterConfig::default()
            },
            ..AudioDspConfig::default()
        };
        let mut processor = configured(config, 48_000, 2);
        let mut samples = [0.0; 8_192];
        for frame in 0..samples.len() / 2 {
            let sample =
                (2.0 * std::f32::consts::PI * 1_000.0 * frame as f32 / 48_000.0).sin() * 0.75;
            samples[frame * 2] = sample;
            samples[frame * 2 + 1] = sample;
        }
        processor.process_interleaved_f32(&mut samples).unwrap();
        assert!(samples.iter().all(|sample| sample.abs() < 1.0));
        let meter = processor.meter_snapshot();
        assert_eq!(meter.clipped_samples, 0);
        assert!(meter.applied_headroom_db < -11.0);
    }

    #[test]
    fn clip_meter_counts_samples_before_final_safety_clamp() {
        let mut processor = configured(
            AudioDspConfig {
                enabled: true,
                input_gain_db: 12.0,
                limiter: LimiterConfig {
                    enabled: false,
                    ..LimiterConfig::default()
                },
                ..AudioDspConfig::default()
            },
            48_000,
            2,
        );
        let mut samples = [0.5, -0.5];
        processor.process_interleaved_f32(&mut samples).unwrap();
        assert_eq!(samples, [1.0, -1.0]);
        assert_eq!(processor.meter_snapshot().clipped_samples, 2);
    }

    #[test]
    fn true_peak_latency_tracks_sample_rate_and_reset_clears_delayed_audio() {
        let config = AudioDspConfig {
            enabled: true,
            limiter: LimiterConfig {
                enabled: true,
                true_peak_enabled: true,
                oversampling: 4,
                lookahead_ms: 3.0,
                ..LimiterConfig::default()
            },
            ..AudioDspConfig::default()
        };
        let mut processor = configured(config, 48_000, 1);
        assert_eq!(processor.latency_frames(), 144);
        let mut impulse = [0.0; 200];
        impulse[0] = 0.5;
        processor.process_interleaved_f32(&mut impulse).unwrap();
        assert!(impulse[..144].iter().all(|sample| sample.abs() < 1.0e-9));

        processor.reset();
        let mut silence = [0.0; 320];
        processor.process_interleaved_f32(&mut silence).unwrap();
        assert!(silence.iter().all(|sample| sample.abs() < 1.0e-9));

        processor.configure_format(96_000, 2).unwrap();
        assert_eq!(processor.latency_frames(), 288);
    }

    #[test]
    fn reset_clears_meter_transient_and_counter_state() {
        let mut processor = configured(
            AudioDspConfig {
                enabled: true,
                input_gain_db: 12.0,
                limiter: LimiterConfig {
                    enabled: false,
                    ..LimiterConfig::default()
                },
                ..AudioDspConfig::default()
            },
            48_000,
            1,
        );
        processor.process_interleaved_f32(&mut [0.75]).unwrap();
        assert!(processor.meter_snapshot().clipped_samples > 0);
        processor.reset();
        let meter = processor.meter_snapshot();
        assert_eq!(meter.input_peak, 0.0);
        assert_eq!(meter.output_peak, 0.0);
        assert_eq!(meter.clipped_samples, 0);
    }
}
