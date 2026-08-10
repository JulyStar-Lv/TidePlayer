use std::{
    cell::UnsafeCell,
    slice,
    sync::{
        atomic::{AtomicI32, AtomicU32, AtomicU64, AtomicU8, Ordering},
        Arc, Mutex,
    },
    time::Instant,
};

use audio_dsp::{
    AudioDspConfig, AudioDspProcessor, BiquadFilterType, CompressorConfig, CrossfeedConfig,
    DspError, DynamicEqConfig, EqMode, GraphicEqualizerConfig, HeadroomConfig, HeadroomMode,
    LimiterConfig, LoudnessConfig, MonoBassConfig, MoogFilterConfig, MoogFilterMode,
    ParametricEqBand, ParametricEqualizerConfig, ReverbConfig, ReverbPreset, SpatialAudioConfig,
    SpatialMode, SpeakerOutputConfig, SpeakerOutputMode, StereoWidthConfig, ToneControlConfig,
    GRAPHIC_EQ_BAND_COUNT, MAX_PARAMETRIC_EQ_BANDS,
};
use triple_buffer::{triple_buffer, Input, Output};

const I16_SCRATCH_SAMPLES: usize = 2_048;

#[derive(Debug, Clone, Copy, uniffi::Enum)]
pub enum DspEqMode {
    Graphic,
    Parametric,
}

#[derive(Debug, Clone, Copy, uniffi::Enum)]
pub enum DspHeadroomMode {
    Off,
    Automatic,
    Manual,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct DspHeadroom {
    pub mode: DspHeadroomMode,
    pub manual_db: f32,
}

#[derive(Debug, Clone, Copy, uniffi::Enum)]
pub enum DspFilterType {
    Peak,
    LowShelf,
    HighShelf,
    LowPass,
    HighPass,
    BandPass,
    Notch,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct DspParametricEqBand {
    pub enabled: bool,
    pub filter_type: DspFilterType,
    pub frequency_hz: f32,
    pub gain_db: f32,
    pub q: f32,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct DspGraphicEqualizer {
    pub enabled: bool,
    pub preamp_db: f32,
    pub q: f32,
    pub gains_db: Vec<f32>,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct DspParametricEqualizer {
    pub enabled: bool,
    pub preamp_db: f32,
    pub bands: Vec<DspParametricEqBand>,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct DspToneControl {
    pub enabled: bool,
    pub bass_gain_db: f32,
    pub bass_frequency_hz: f32,
    pub treble_gain_db: f32,
    pub treble_frequency_hz: f32,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct DspCompressor {
    pub enabled: bool,
    pub threshold_db: f32,
    pub ratio: f32,
    pub attack_ms: f32,
    pub release_ms: f32,
    pub makeup_gain_db: f32,
    pub knee_db: f32,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct DspLoudness {
    pub enabled: bool,
    pub amount: f32,
    pub balance: f32,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct DspDynamicEqualizer {
    pub enabled: bool,
    pub amount: f32,
    pub de_esser_amount: f32,
    pub de_esser_frequency_hz: f32,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct DspMonoBass {
    pub enabled: bool,
    pub crossover_hz: f32,
    pub amount: f32,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct DspStereoWidth {
    pub enabled: bool,
    pub width: f32,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct DspCrossfeed {
    pub enabled: bool,
    pub low_cut_hz: f32,
    pub high_cut_hz: f32,
    pub attenuation_db: f32,
}

#[derive(Debug, Clone, Copy, uniffi::Enum)]
pub enum DspSpatialMode {
    None,
    CrossfeedAndWidth,
    Surround360,
    Panoramic360,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct DspSpatialAudio {
    pub mode: DspSpatialMode,
    pub intensity: f32,
    pub azimuth_degrees: f32,
    pub elevation_degrees: f32,
    pub auto_rotate_degrees_per_second: f32,
    pub room_amount: f32,
}

#[derive(Debug, Clone, Copy, uniffi::Enum)]
pub enum DspMoogMode {
    LowPass24,
    LowPass12,
    HighPass24,
    BandPass12,
    Notch,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct DspMoogFilter {
    pub enabled: bool,
    pub mode: DspMoogMode,
    pub cutoff_hz: f32,
    pub resonance: f32,
    pub drive_db: f32,
    pub mix: f32,
}

#[derive(Debug, Clone, Copy, uniffi::Enum)]
pub enum DspSpeakerMode {
    Elasticity,
    Powerful,
    Wide,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct DspSpeakerOutput {
    pub enabled: bool,
    pub mode: DspSpeakerMode,
    pub strength: f32,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct DspLimiter {
    pub enabled: bool,
    pub ceiling_db: f32,
    pub attack_ms: f32,
    pub release_ms: f32,
    pub true_peak_enabled: bool,
    pub oversampling: u8,
    pub lookahead_ms: f32,
}

#[derive(Debug, Clone, Copy, uniffi::Enum)]
pub enum DspReverbPreset {
    None,
    SmallRoom,
    MediumRoom,
    LargeRoom,
    Hall,
    Plate,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct DspReverb {
    pub preset: DspReverbPreset,
    pub wet: f32,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct DspConfiguration {
    pub enabled: bool,
    pub input_gain_db: f32,
    pub headroom: DspHeadroom,
    pub equalizer_mode: DspEqMode,
    pub graphic_equalizer: DspGraphicEqualizer,
    pub parametric_equalizer: DspParametricEqualizer,
    pub tone: DspToneControl,
    pub compressor: DspCompressor,
    pub loudness: DspLoudness,
    pub dynamic_equalizer: DspDynamicEqualizer,
    pub mono_bass: DspMonoBass,
    pub stereo_width: DspStereoWidth,
    pub crossfeed: DspCrossfeed,
    pub spatial_audio: DspSpatialAudio,
    pub moog_filter: DspMoogFilter,
    pub speaker_output: DspSpeakerOutput,
    pub limiter: DspLimiter,
    pub reverb: DspReverb,
}

impl Default for DspConfiguration {
    fn default() -> Self {
        let config = AudioDspConfig::default();
        Self {
            enabled: config.enabled,
            input_gain_db: config.input_gain_db,
            headroom: DspHeadroom {
                mode: DspHeadroomMode::Off,
                manual_db: 0.0,
            },
            equalizer_mode: DspEqMode::Graphic,
            graphic_equalizer: DspGraphicEqualizer {
                enabled: config.graphic_equalizer.enabled,
                preamp_db: config.graphic_equalizer.preamp_db,
                q: config.graphic_equalizer.q,
                gains_db: config.graphic_equalizer.gains_db.to_vec(),
            },
            parametric_equalizer: DspParametricEqualizer {
                enabled: false,
                preamp_db: 0.0,
                bands: Vec::new(),
            },
            tone: DspToneControl {
                enabled: config.tone.enabled,
                bass_gain_db: config.tone.bass_gain_db,
                bass_frequency_hz: config.tone.bass_frequency_hz,
                treble_gain_db: config.tone.treble_gain_db,
                treble_frequency_hz: config.tone.treble_frequency_hz,
            },
            compressor: DspCompressor {
                enabled: config.compressor.enabled,
                threshold_db: config.compressor.threshold_db,
                ratio: config.compressor.ratio,
                attack_ms: config.compressor.attack_ms,
                release_ms: config.compressor.release_ms,
                makeup_gain_db: config.compressor.makeup_gain_db,
                knee_db: config.compressor.knee_db,
            },
            loudness: DspLoudness {
                enabled: false,
                amount: 0.0,
                balance: 0.0,
            },
            dynamic_equalizer: DspDynamicEqualizer {
                enabled: false,
                amount: 0.0,
                de_esser_amount: 0.0,
                de_esser_frequency_hz: 6_500.0,
            },
            mono_bass: DspMonoBass {
                enabled: false,
                crossover_hz: 120.0,
                amount: 1.0,
            },
            stereo_width: DspStereoWidth {
                enabled: false,
                width: 1.0,
            },
            crossfeed: DspCrossfeed {
                enabled: false,
                low_cut_hz: 120.0,
                high_cut_hz: 700.0,
                attenuation_db: 6.0,
            },
            spatial_audio: DspSpatialAudio {
                mode: DspSpatialMode::None,
                intensity: 0.0,
                azimuth_degrees: 0.0,
                elevation_degrees: 0.0,
                auto_rotate_degrees_per_second: 0.0,
                room_amount: 0.15,
            },
            moog_filter: DspMoogFilter {
                enabled: false,
                mode: DspMoogMode::LowPass24,
                cutoff_hz: 8_000.0,
                resonance: 0.0,
                drive_db: 0.0,
                mix: 1.0,
            },
            speaker_output: DspSpeakerOutput {
                enabled: false,
                mode: DspSpeakerMode::Elasticity,
                strength: 0.5,
            },
            limiter: DspLimiter {
                enabled: config.limiter.enabled,
                ceiling_db: config.limiter.ceiling_db,
                attack_ms: config.limiter.attack_ms,
                release_ms: config.limiter.release_ms,
                true_peak_enabled: config.limiter.true_peak_enabled,
                oversampling: config.limiter.oversampling,
                lookahead_ms: config.limiter.lookahead_ms,
            },
            reverb: DspReverb {
                preset: DspReverbPreset::None,
                wet: 0.15,
            },
        }
    }
}

impl DspConfiguration {
    pub(crate) fn into_core(self) -> AudioDspConfig {
        let mut graphic_gains = [0.0; GRAPHIC_EQ_BAND_COUNT];
        for (target, source) in graphic_gains
            .iter_mut()
            .zip(self.graphic_equalizer.gains_db)
        {
            *target = source;
        }
        let mut parametric_bands = [ParametricEqBand::default(); MAX_PARAMETRIC_EQ_BANDS];
        let parametric_count = self
            .parametric_equalizer
            .bands
            .len()
            .min(MAX_PARAMETRIC_EQ_BANDS);
        for (target, source) in parametric_bands
            .iter_mut()
            .zip(self.parametric_equalizer.bands)
        {
            *target = ParametricEqBand {
                enabled: source.enabled,
                filter_type: source.filter_type.into(),
                frequency_hz: source.frequency_hz,
                gain_db: source.gain_db,
                q: source.q,
            };
        }
        AudioDspConfig {
            enabled: self.enabled,
            input_gain_db: self.input_gain_db,
            headroom: HeadroomConfig {
                mode: self.headroom.mode.into(),
                manual_db: self.headroom.manual_db,
            },
            eq_mode: self.equalizer_mode.into(),
            graphic_equalizer: GraphicEqualizerConfig {
                enabled: self.graphic_equalizer.enabled,
                preamp_db: self.graphic_equalizer.preamp_db,
                q: self.graphic_equalizer.q,
                gains_db: graphic_gains,
            },
            parametric_equalizer: ParametricEqualizerConfig {
                enabled: self.parametric_equalizer.enabled,
                preamp_db: self.parametric_equalizer.preamp_db,
                band_count: parametric_count,
                bands: parametric_bands,
            },
            tone: ToneControlConfig {
                enabled: self.tone.enabled,
                bass_gain_db: self.tone.bass_gain_db,
                bass_frequency_hz: self.tone.bass_frequency_hz,
                treble_gain_db: self.tone.treble_gain_db,
                treble_frequency_hz: self.tone.treble_frequency_hz,
            },
            compressor: CompressorConfig {
                enabled: self.compressor.enabled,
                threshold_db: self.compressor.threshold_db,
                ratio: self.compressor.ratio,
                attack_ms: self.compressor.attack_ms,
                release_ms: self.compressor.release_ms,
                makeup_gain_db: self.compressor.makeup_gain_db,
                knee_db: self.compressor.knee_db,
            },
            loudness: LoudnessConfig {
                enabled: self.loudness.enabled,
                amount: self.loudness.amount,
                balance: self.loudness.balance,
            },
            dynamic_eq: DynamicEqConfig {
                enabled: self.dynamic_equalizer.enabled,
                amount: self.dynamic_equalizer.amount,
                de_esser_amount: self.dynamic_equalizer.de_esser_amount,
                de_esser_frequency_hz: self.dynamic_equalizer.de_esser_frequency_hz,
            },
            mono_bass: MonoBassConfig {
                enabled: self.mono_bass.enabled,
                crossover_hz: self.mono_bass.crossover_hz,
                amount: self.mono_bass.amount,
            },
            stereo_width: StereoWidthConfig {
                enabled: self.stereo_width.enabled,
                width: self.stereo_width.width,
            },
            crossfeed: CrossfeedConfig {
                enabled: self.crossfeed.enabled,
                low_cut_hz: self.crossfeed.low_cut_hz,
                high_cut_hz: self.crossfeed.high_cut_hz,
                attenuation_db: self.crossfeed.attenuation_db,
            },
            spatial: SpatialAudioConfig {
                mode: self.spatial_audio.mode.into(),
                intensity: self.spatial_audio.intensity,
                azimuth_degrees: self.spatial_audio.azimuth_degrees,
                elevation_degrees: self.spatial_audio.elevation_degrees,
                auto_rotate_degrees_per_second: self.spatial_audio.auto_rotate_degrees_per_second,
                room_amount: self.spatial_audio.room_amount,
            },
            moog: MoogFilterConfig {
                enabled: self.moog_filter.enabled,
                mode: self.moog_filter.mode.into(),
                cutoff_hz: self.moog_filter.cutoff_hz,
                resonance: self.moog_filter.resonance,
                drive_db: self.moog_filter.drive_db,
                mix: self.moog_filter.mix,
            },
            speaker_output: SpeakerOutputConfig {
                enabled: self.speaker_output.enabled,
                mode: self.speaker_output.mode.into(),
                strength: self.speaker_output.strength,
            },
            limiter: LimiterConfig {
                enabled: self.limiter.enabled,
                ceiling_db: self.limiter.ceiling_db,
                attack_ms: self.limiter.attack_ms,
                release_ms: self.limiter.release_ms,
                true_peak_enabled: self.limiter.true_peak_enabled,
                oversampling: self.limiter.oversampling,
                lookahead_ms: self.limiter.lookahead_ms,
            },
            reverb: ReverbConfig {
                preset: self.reverb.preset.into(),
                wet: self.reverb.wet,
            },
            ..Default::default()
        }
    }
}

macro_rules! enum_conversion {
    ($from:ty => $to:ty { $($variant:ident),+ $(,)? }) => {
        impl From<$from> for $to {
            fn from(value: $from) -> Self {
                match value {
                    $(<$from>::$variant => <$to>::$variant,)+
                }
            }
        }
    };
}

enum_conversion!(DspEqMode => EqMode { Graphic, Parametric });
enum_conversion!(DspHeadroomMode => HeadroomMode { Off, Automatic, Manual });
enum_conversion!(DspFilterType => BiquadFilterType {
    Peak, LowShelf, HighShelf, LowPass, HighPass, BandPass, Notch
});
enum_conversion!(DspSpatialMode => SpatialMode {
    None, CrossfeedAndWidth, Surround360, Panoramic360
});
enum_conversion!(DspMoogMode => MoogFilterMode {
    LowPass24, LowPass12, HighPass24, BandPass12, Notch
});
enum_conversion!(DspSpeakerMode => SpeakerOutputMode {
    Elasticity, Powerful, Wide
});
enum_conversion!(DspReverbPreset => ReverbPreset {
    None, SmallRoom, MediumRoom, LargeRoom, Hall, Plate
});

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum DspRuntimeState {
    Inactive,
    Active,
    Bypassed,
    Unavailable,
    Error,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum DspRuntimeBypassReason {
    None,
    EffectsDisabled,
    UnsupportedSampleFormat,
    UnsupportedChannelCount,
    UnsupportedSampleRate,
    PlatformProcessingUnavailable,
    ProtectedContent,
    AudioTapUnavailable,
    OutputRouteUnavailable,
    NativeProcessingError,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum DspSampleFormat {
    Unknown,
    Pcm16,
    Float32,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct NativeDspRuntimeSnapshot {
    pub state: DspRuntimeState,
    pub sample_rate: u32,
    pub channel_count: u32,
    pub sample_format: DspSampleFormat,
    pub bypass_reason: DspRuntimeBypassReason,
    pub last_error_code: i32,
    pub latency_frames: u32,
    pub input_peak_db: f32,
    pub output_peak_db: f32,
    pub compressor_gain_reduction_db: f32,
    pub limiter_gain_reduction_db: f32,
    pub clipped_samples: u64,
    pub non_finite_recovery_count: u64,
    pub applied_headroom_db: f32,
    pub process_count: u64,
    pub average_processing_time_us: f32,
    pub max_processing_time_us: f32,
    pub buffer_duration_us: f32,
    pub deadline_utilization: f32,
}

#[derive(Debug)]
pub(crate) struct DspRuntimeTelemetry {
    state: AtomicU8,
    sample_rate: AtomicU32,
    channel_count: AtomicU32,
    sample_format: AtomicU8,
    bypass_reason: AtomicU8,
    last_error_code: AtomicI32,
    latency_frames: AtomicU32,
    input_peak_bits: AtomicU32,
    output_peak_bits: AtomicU32,
    compressor_reduction_bits: AtomicU32,
    limiter_reduction_bits: AtomicU32,
    clipped_samples: AtomicU64,
    non_finite_recovery_count: AtomicU64,
    applied_headroom_bits: AtomicU32,
    process_count: AtomicU64,
    total_processing_nanoseconds: AtomicU64,
    max_processing_nanoseconds: AtomicU64,
    buffer_duration_nanoseconds: AtomicU64,
}

impl Default for DspRuntimeTelemetry {
    fn default() -> Self {
        Self {
            state: AtomicU8::new(DspRuntimeState::Inactive as u8),
            sample_rate: AtomicU32::new(0),
            channel_count: AtomicU32::new(0),
            sample_format: AtomicU8::new(DspSampleFormat::Unknown as u8),
            bypass_reason: AtomicU8::new(DspRuntimeBypassReason::None as u8),
            last_error_code: AtomicI32::new(0),
            latency_frames: AtomicU32::new(0),
            input_peak_bits: AtomicU32::new(f32::NEG_INFINITY.to_bits()),
            output_peak_bits: AtomicU32::new(f32::NEG_INFINITY.to_bits()),
            compressor_reduction_bits: AtomicU32::new(0.0_f32.to_bits()),
            limiter_reduction_bits: AtomicU32::new(0.0_f32.to_bits()),
            clipped_samples: AtomicU64::new(0),
            non_finite_recovery_count: AtomicU64::new(0),
            applied_headroom_bits: AtomicU32::new(0.0_f32.to_bits()),
            process_count: AtomicU64::new(0),
            total_processing_nanoseconds: AtomicU64::new(0),
            max_processing_nanoseconds: AtomicU64::new(0),
            buffer_duration_nanoseconds: AtomicU64::new(0),
        }
    }
}

impl DspRuntimeTelemetry {
    pub(crate) fn configured(&self, processor: &AudioDspProcessor) {
        if let Some((sample_rate, channels)) = processor.format() {
            self.sample_rate.store(sample_rate, Ordering::Relaxed);
            self.channel_count.store(channels as u32, Ordering::Relaxed);
            self.latency_frames
                .store(processor.latency_frames() as u32, Ordering::Relaxed);
        }
        self.state
            .store(DspRuntimeState::Inactive as u8, Ordering::Release);
        self.bypass_reason
            .store(DspRuntimeBypassReason::None as u8, Ordering::Relaxed);
        self.last_error_code.store(0, Ordering::Relaxed);
    }

    pub(crate) fn configure_error(&self, error: DspError) {
        let reason = match error {
            DspError::UnsupportedSampleRate(_) => DspRuntimeBypassReason::UnsupportedSampleRate,
            DspError::UnsupportedChannelCount(_) => DspRuntimeBypassReason::UnsupportedChannelCount,
            _ => DspRuntimeBypassReason::NativeProcessingError,
        };
        self.mark_bypassed(reason, error_code(error));
    }

    pub(crate) fn record_process(
        &self,
        processor: &AudioDspProcessor,
        sample_format: DspSampleFormat,
        frames: usize,
        elapsed_nanoseconds: u64,
        result: Result<(), DspError>,
    ) {
        self.sample_format
            .store(sample_format as u8, Ordering::Relaxed);
        let sample_rate = processor.format().map_or(0, |format| format.0);
        let buffer_nanoseconds = if sample_rate == 0 {
            0
        } else {
            (frames as u64).saturating_mul(1_000_000_000) / sample_rate as u64
        };
        self.buffer_duration_nanoseconds
            .store(buffer_nanoseconds, Ordering::Relaxed);
        self.process_count.fetch_add(1, Ordering::Relaxed);
        self.total_processing_nanoseconds
            .fetch_add(elapsed_nanoseconds, Ordering::Relaxed);
        self.max_processing_nanoseconds
            .fetch_max(elapsed_nanoseconds, Ordering::Relaxed);

        match result {
            Ok(()) => {
                let meter = processor.meter_snapshot();
                self.input_peak_bits
                    .store(linear_to_db(meter.input_peak).to_bits(), Ordering::Relaxed);
                self.output_peak_bits
                    .store(linear_to_db(meter.output_peak).to_bits(), Ordering::Relaxed);
                self.compressor_reduction_bits.store(
                    meter.compressor_gain_reduction_db.to_bits(),
                    Ordering::Relaxed,
                );
                self.limiter_reduction_bits
                    .store(meter.limiter_gain_reduction_db.to_bits(), Ordering::Relaxed);
                self.clipped_samples
                    .store(meter.clipped_samples, Ordering::Relaxed);
                self.non_finite_recovery_count
                    .store(meter.non_finite_recovery_count, Ordering::Relaxed);
                self.applied_headroom_bits
                    .store(meter.applied_headroom_db.to_bits(), Ordering::Relaxed);
                self.latency_frames
                    .store(processor.latency_frames() as u32, Ordering::Relaxed);
                if processor.has_active_effects() {
                    self.state
                        .store(DspRuntimeState::Active as u8, Ordering::Release);
                    self.bypass_reason
                        .store(DspRuntimeBypassReason::None as u8, Ordering::Relaxed);
                } else {
                    self.state
                        .store(DspRuntimeState::Bypassed as u8, Ordering::Release);
                    self.bypass_reason.store(
                        DspRuntimeBypassReason::EffectsDisabled as u8,
                        Ordering::Relaxed,
                    );
                }
                self.last_error_code.store(0, Ordering::Relaxed);
            }
            Err(error) => {
                self.state
                    .store(DspRuntimeState::Error as u8, Ordering::Release);
                self.bypass_reason.store(
                    DspRuntimeBypassReason::NativeProcessingError as u8,
                    Ordering::Relaxed,
                );
                self.last_error_code
                    .store(error_code(error), Ordering::Relaxed);
            }
        }
    }

    pub(crate) fn mark_bypassed(&self, reason: DspRuntimeBypassReason, error: i32) {
        self.bypass_reason.store(reason as u8, Ordering::Relaxed);
        self.last_error_code.store(error, Ordering::Relaxed);
        self.state
            .store(DspRuntimeState::Bypassed as u8, Ordering::Release);
    }

    pub(crate) fn reset(&self) {
        self.state
            .store(DspRuntimeState::Inactive as u8, Ordering::Release);
        self.bypass_reason
            .store(DspRuntimeBypassReason::None as u8, Ordering::Relaxed);
        self.last_error_code.store(0, Ordering::Relaxed);
        self.input_peak_bits
            .store(f32::NEG_INFINITY.to_bits(), Ordering::Relaxed);
        self.output_peak_bits
            .store(f32::NEG_INFINITY.to_bits(), Ordering::Relaxed);
        self.compressor_reduction_bits
            .store(0.0_f32.to_bits(), Ordering::Relaxed);
        self.limiter_reduction_bits
            .store(0.0_f32.to_bits(), Ordering::Relaxed);
        self.clipped_samples.store(0, Ordering::Relaxed);
        self.non_finite_recovery_count.store(0, Ordering::Relaxed);
        self.process_count.store(0, Ordering::Relaxed);
        self.total_processing_nanoseconds
            .store(0, Ordering::Relaxed);
        self.max_processing_nanoseconds.store(0, Ordering::Relaxed);
        self.buffer_duration_nanoseconds.store(0, Ordering::Relaxed);
    }

    pub(crate) fn snapshot(&self) -> NativeDspRuntimeSnapshot {
        let process_count = self.process_count.load(Ordering::Relaxed);
        let total_nanoseconds = self.total_processing_nanoseconds.load(Ordering::Relaxed);
        let max_nanoseconds = self.max_processing_nanoseconds.load(Ordering::Relaxed);
        let buffer_nanoseconds = self.buffer_duration_nanoseconds.load(Ordering::Relaxed);
        let average_nanoseconds = if process_count == 0 {
            0.0
        } else {
            total_nanoseconds as f32 / process_count as f32
        };
        NativeDspRuntimeSnapshot {
            state: runtime_state(self.state.load(Ordering::Acquire)),
            sample_rate: self.sample_rate.load(Ordering::Relaxed),
            channel_count: self.channel_count.load(Ordering::Relaxed),
            sample_format: sample_format(self.sample_format.load(Ordering::Relaxed)),
            bypass_reason: bypass_reason(self.bypass_reason.load(Ordering::Relaxed)),
            last_error_code: self.last_error_code.load(Ordering::Relaxed),
            latency_frames: self.latency_frames.load(Ordering::Relaxed),
            input_peak_db: f32::from_bits(self.input_peak_bits.load(Ordering::Relaxed)),
            output_peak_db: f32::from_bits(self.output_peak_bits.load(Ordering::Relaxed)),
            compressor_gain_reduction_db: f32::from_bits(
                self.compressor_reduction_bits.load(Ordering::Relaxed),
            ),
            limiter_gain_reduction_db: f32::from_bits(
                self.limiter_reduction_bits.load(Ordering::Relaxed),
            ),
            clipped_samples: self.clipped_samples.load(Ordering::Relaxed),
            non_finite_recovery_count: self.non_finite_recovery_count.load(Ordering::Relaxed),
            applied_headroom_db: f32::from_bits(self.applied_headroom_bits.load(Ordering::Relaxed)),
            process_count,
            average_processing_time_us: average_nanoseconds / 1_000.0,
            max_processing_time_us: max_nanoseconds as f32 / 1_000.0,
            buffer_duration_us: buffer_nanoseconds as f32 / 1_000.0,
            deadline_utilization: if buffer_nanoseconds == 0 {
                0.0
            } else {
                average_nanoseconds / buffer_nanoseconds as f32
            },
        }
    }
}

fn linear_to_db(value: f32) -> f32 {
    if value > 0.0 && value.is_finite() {
        (20.0 * value.log10()).max(-120.0)
    } else {
        -120.0
    }
}

fn runtime_state(value: u8) -> DspRuntimeState {
    match value {
        value if value == DspRuntimeState::Active as u8 => DspRuntimeState::Active,
        value if value == DspRuntimeState::Bypassed as u8 => DspRuntimeState::Bypassed,
        value if value == DspRuntimeState::Unavailable as u8 => DspRuntimeState::Unavailable,
        value if value == DspRuntimeState::Error as u8 => DspRuntimeState::Error,
        _ => DspRuntimeState::Inactive,
    }
}

fn sample_format(value: u8) -> DspSampleFormat {
    match value {
        value if value == DspSampleFormat::Pcm16 as u8 => DspSampleFormat::Pcm16,
        value if value == DspSampleFormat::Float32 as u8 => DspSampleFormat::Float32,
        _ => DspSampleFormat::Unknown,
    }
}

fn bypass_reason(value: u8) -> DspRuntimeBypassReason {
    match value {
        value if value == DspRuntimeBypassReason::EffectsDisabled as u8 => {
            DspRuntimeBypassReason::EffectsDisabled
        }
        value if value == DspRuntimeBypassReason::UnsupportedSampleFormat as u8 => {
            DspRuntimeBypassReason::UnsupportedSampleFormat
        }
        value if value == DspRuntimeBypassReason::UnsupportedChannelCount as u8 => {
            DspRuntimeBypassReason::UnsupportedChannelCount
        }
        value if value == DspRuntimeBypassReason::UnsupportedSampleRate as u8 => {
            DspRuntimeBypassReason::UnsupportedSampleRate
        }
        value if value == DspRuntimeBypassReason::PlatformProcessingUnavailable as u8 => {
            DspRuntimeBypassReason::PlatformProcessingUnavailable
        }
        value if value == DspRuntimeBypassReason::ProtectedContent as u8 => {
            DspRuntimeBypassReason::ProtectedContent
        }
        value if value == DspRuntimeBypassReason::AudioTapUnavailable as u8 => {
            DspRuntimeBypassReason::AudioTapUnavailable
        }
        value if value == DspRuntimeBypassReason::OutputRouteUnavailable as u8 => {
            DspRuntimeBypassReason::OutputRouteUnavailable
        }
        value if value == DspRuntimeBypassReason::NativeProcessingError as u8 => {
            DspRuntimeBypassReason::NativeProcessingError
        }
        _ => DspRuntimeBypassReason::None,
    }
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct NativeDspCapabilities {
    pub max_parametric_bands: u32,
    pub supports_mono: bool,
    pub supports_stereo: bool,
    pub true_peak_limiter: bool,
    pub convolution: bool,
}

#[derive(Debug)]
struct NativeDspAudioState {
    processor: AudioDspProcessor,
    config_output: Output<AudioDspConfig>,
    i16_scratch: [f32; I16_SCRATCH_SAMPLES],
    telemetry: Arc<DspRuntimeTelemetry>,
}

impl NativeDspAudioState {
    fn apply_pending_config(&mut self) {
        if self.config_output.update() {
            let config = *self.config_output.output_buffer();
            let _ = self.processor.update_config(config);
        }
    }
}

#[derive(Debug, uniffi::Object)]
pub struct NativeAudioDsp {
    config_input: Mutex<Input<AudioDspConfig>>,
    audio: UnsafeCell<NativeDspAudioState>,
    telemetry: Arc<DspRuntimeTelemetry>,
}

// The control side only touches `config_input`. Platform adapters guarantee
// that configure/reset/process calls are serialized on one audio callback
// thread, while configuration publication may happen from a control thread.
unsafe impl Send for NativeAudioDsp {}
unsafe impl Sync for NativeAudioDsp {}

#[uniffi::export]
impl NativeAudioDsp {
    pub fn update_config(&self, config: DspConfiguration) {
        if let Ok(mut input) = self.config_input.lock() {
            input.write(config.into_core());
        }
    }

    pub fn native_handle(&self) -> u64 {
        self as *const Self as usize as u64
    }

    pub fn capabilities(&self) -> NativeDspCapabilities {
        NativeDspCapabilities {
            max_parametric_bands: MAX_PARAMETRIC_EQ_BANDS as u32,
            supports_mono: true,
            supports_stereo: true,
            true_peak_limiter: true,
            convolution: false,
        }
    }

    pub fn runtime_snapshot(&self) -> NativeDspRuntimeSnapshot {
        self.telemetry.snapshot()
    }

    pub fn mark_bypassed(&self, reason: DspRuntimeBypassReason) {
        self.telemetry.mark_bypassed(reason, 0);
    }

    pub fn mark_inactive(&self) {
        self.telemetry.reset();
    }
}

#[uniffi::export]
pub fn ct_create_audio_dsp_processor() -> std::sync::Arc<NativeAudioDsp> {
    let config = AudioDspConfig::default();
    let (input, output) = triple_buffer(&config);
    let processor =
        AudioDspProcessor::new(config).expect("the built-in DSP configuration must be valid");
    let telemetry = Arc::new(DspRuntimeTelemetry::default());
    std::sync::Arc::new(NativeAudioDsp {
        config_input: Mutex::new(input),
        audio: UnsafeCell::new(NativeDspAudioState {
            processor,
            config_output: output,
            i16_scratch: [0.0; I16_SCRATCH_SAMPLES],
            telemetry: telemetry.clone(),
        }),
        telemetry,
    })
}

#[uniffi::export]
pub fn ct_calculate_dsp_frequency_response(
    config: DspConfiguration,
    sample_rate: u32,
    frequencies_hz: Vec<f32>,
) -> Vec<f32> {
    let mut processor = match AudioDspProcessor::new(config.into_core()) {
        Ok(processor) => processor,
        Err(_) => return vec![0.0; frequencies_hz.len()],
    };
    if processor.configure_format(sample_rate, 2).is_err() {
        return vec![0.0; frequencies_hz.len()];
    }
    let mut response = vec![0.0; frequencies_hz.len()];
    let _ = processor.calculate_frequency_response(&frequencies_hz, &mut response);
    response
}

unsafe fn audio_state<'a>(handle: u64) -> Option<&'a mut NativeDspAudioState> {
    let owner = (handle as usize as *const NativeAudioDsp).as_ref()?;
    Some(&mut *owner.audio.get())
}

#[no_mangle]
pub unsafe extern "C" fn audio_dsp_retain(handle: u64) -> i32 {
    let owner = handle as usize as *const NativeAudioDsp;
    if owner.is_null() {
        return -1;
    }
    std::sync::Arc::increment_strong_count(owner);
    0
}

#[no_mangle]
pub unsafe extern "C" fn audio_dsp_release(handle: u64) {
    let owner = handle as usize as *const NativeAudioDsp;
    if !owner.is_null() {
        std::sync::Arc::decrement_strong_count(owner);
    }
}

fn error_code(error: DspError) -> i32 {
    match error {
        DspError::UnsupportedSampleRate(_) => -2,
        DspError::UnsupportedChannelCount(_) => -3,
        DspError::FormatNotConfigured => -4,
        DspError::MisalignedInterleavedBuffer { .. } => -5,
        DspError::MismatchedPlanarBufferLengths => -6,
        DspError::MismatchedResponseBufferLengths => -7,
    }
}

#[no_mangle]
pub unsafe extern "C" fn audio_dsp_configure_format(
    handle: u64,
    sample_rate: u32,
    channels: u32,
) -> i32 {
    let Some(state) = audio_state(handle) else {
        return -1;
    };
    let result = state
        .processor
        .configure_format(sample_rate, channels as usize);
    match result {
        Ok(()) => state.telemetry.configured(&state.processor),
        Err(error) => state.telemetry.configure_error(error),
    }
    result.map(|()| 0).unwrap_or_else(error_code)
}

#[no_mangle]
pub unsafe extern "C" fn audio_dsp_reset(handle: u64) {
    if let Some(state) = audio_state(handle) {
        state.processor.reset();
        state.telemetry.reset();
    }
}

#[no_mangle]
pub unsafe extern "C" fn audio_dsp_process_interleaved_f32(
    handle: u64,
    samples: *mut f32,
    frames: u32,
    channels: u32,
) -> i32 {
    if samples.is_null() {
        return -1;
    }
    let Some(state) = audio_state(handle) else {
        return -1;
    };
    let Some((_, configured_channels)) = state.processor.format() else {
        return -4;
    };
    if channels as usize != configured_channels {
        return -3;
    }
    let Some(sample_count) = (frames as usize).checked_mul(channels as usize) else {
        return -1;
    };
    state.apply_pending_config();
    let started = Instant::now();
    let result = state
        .processor
        .process_interleaved_f32(slice::from_raw_parts_mut(samples, sample_count));
    state.telemetry.record_process(
        &state.processor,
        DspSampleFormat::Float32,
        frames as usize,
        started.elapsed().as_nanos().min(u64::MAX as u128) as u64,
        result,
    );
    result.map(|()| 0).unwrap_or_else(error_code)
}

#[no_mangle]
pub unsafe extern "C" fn audio_dsp_process_planar_f32(
    handle: u64,
    channel_buffers: *mut *mut f32,
    frames: u32,
    channels: u32,
) -> i32 {
    if channel_buffers.is_null() {
        return -1;
    }
    let Some(state) = audio_state(handle) else {
        return -1;
    };
    state.apply_pending_config();
    let started = Instant::now();
    let result = match channels {
        1 => {
            let first = *channel_buffers;
            if first.is_null() {
                return -1;
            }
            let mut first = slice::from_raw_parts_mut(first, frames as usize);
            state.processor.process_planar_f32(&mut [&mut first])
        }
        2 => {
            let first = *channel_buffers;
            let second = *channel_buffers.add(1);
            if first.is_null() || second.is_null() {
                return -1;
            }
            let mut first = slice::from_raw_parts_mut(first, frames as usize);
            let mut second = slice::from_raw_parts_mut(second, frames as usize);
            state
                .processor
                .process_planar_f32(&mut [&mut first, &mut second])
        }
        _ => Err(DspError::UnsupportedChannelCount(channels as usize)),
    };
    state.telemetry.record_process(
        &state.processor,
        DspSampleFormat::Float32,
        frames as usize,
        started.elapsed().as_nanos().min(u64::MAX as u128) as u64,
        result,
    );
    result.map(|()| 0).unwrap_or_else(error_code)
}

#[no_mangle]
pub unsafe extern "C" fn audio_dsp_process_interleaved_i16(
    handle: u64,
    samples: *mut i16,
    sample_count: u32,
) -> i32 {
    if samples.is_null() {
        return -1;
    }
    let Some(state) = audio_state(handle) else {
        return -1;
    };
    let Some((_, channels)) = state.processor.format() else {
        return -4;
    };
    if !(sample_count as usize).is_multiple_of(channels) {
        return -5;
    }
    state.apply_pending_config();
    let samples = slice::from_raw_parts_mut(samples, sample_count as usize);
    let chunk_capacity = I16_SCRATCH_SAMPLES - I16_SCRATCH_SAMPLES % channels;
    let started = Instant::now();
    let mut process_result = Ok(());
    for chunk in samples.chunks_mut(chunk_capacity) {
        let chunk_length = chunk.len();
        for (target, source) in state.i16_scratch[..chunk_length].iter_mut().zip(&*chunk) {
            *target = *source as f32 / 32_768.0;
        }
        if let Err(error) = state
            .processor
            .process_interleaved_f32(&mut state.i16_scratch[..chunk_length])
        {
            process_result = Err(error);
            break;
        }
        for (target, source) in chunk.iter_mut().zip(&state.i16_scratch[..chunk_length]) {
            let scaled = if *source < 0.0 {
                *source * 32_768.0
            } else {
                *source * 32_767.0
            };
            *target = scaled.clamp(i16::MIN as f32, i16::MAX as f32) as i16;
        }
    }
    state.telemetry.record_process(
        &state.processor,
        DspSampleFormat::Pcm16,
        sample_count as usize / channels,
        started.elapsed().as_nanos().min(u64::MAX as u128) as u64,
        process_result,
    );
    process_result.map(|()| 0).unwrap_or_else(error_code)
}

#[no_mangle]
pub unsafe extern "C" fn audio_dsp_set_runtime_bypass(handle: u64, reason_code: i32) {
    let owner = (handle as usize as *const NativeAudioDsp).as_ref();
    let Some(owner) = owner else {
        return;
    };
    let reason = match reason_code {
        1 => DspRuntimeBypassReason::EffectsDisabled,
        2 => DspRuntimeBypassReason::UnsupportedSampleFormat,
        3 => DspRuntimeBypassReason::UnsupportedChannelCount,
        4 => DspRuntimeBypassReason::UnsupportedSampleRate,
        5 => DspRuntimeBypassReason::PlatformProcessingUnavailable,
        6 => DspRuntimeBypassReason::ProtectedContent,
        7 => DspRuntimeBypassReason::AudioTapUnavailable,
        8 => DspRuntimeBypassReason::OutputRouteUnavailable,
        _ => DspRuntimeBypassReason::NativeProcessingError,
    };
    owner.telemetry.mark_bypassed(reason, 0);
}

// Compatibility wrappers for the original internal symbols. Platform code
// now uses the neutral names, while older binaries remain link-compatible.
#[no_mangle]
pub unsafe extern "C" fn tide_audio_dsp_retain(handle: u64) -> i32 {
    audio_dsp_retain(handle)
}

#[no_mangle]
pub unsafe extern "C" fn tide_audio_dsp_release(handle: u64) {
    audio_dsp_release(handle);
}

#[no_mangle]
pub unsafe extern "C" fn tide_audio_dsp_configure_format(
    handle: u64,
    sample_rate: u32,
    channels: u32,
) -> i32 {
    audio_dsp_configure_format(handle, sample_rate, channels)
}

#[no_mangle]
pub unsafe extern "C" fn tide_audio_dsp_reset(handle: u64) {
    audio_dsp_reset(handle);
}

#[no_mangle]
pub unsafe extern "C" fn tide_audio_dsp_process_interleaved_f32(
    handle: u64,
    samples: *mut f32,
    frames: u32,
    channels: u32,
) -> i32 {
    audio_dsp_process_interleaved_f32(handle, samples, frames, channels)
}

#[no_mangle]
pub unsafe extern "C" fn tide_audio_dsp_process_planar_f32(
    handle: u64,
    channel_buffers: *mut *mut f32,
    frames: u32,
    channels: u32,
) -> i32 {
    audio_dsp_process_planar_f32(handle, channel_buffers, frames, channels)
}

#[no_mangle]
pub unsafe extern "C" fn tide_audio_dsp_process_interleaved_i16(
    handle: u64,
    samples: *mut i16,
    sample_count: u32,
) -> i32 {
    audio_dsp_process_interleaved_i16(handle, samples, sample_count)
}

#[cfg(target_os = "android")]
mod android_jni {
    use super::{
        audio_dsp_configure_format, audio_dsp_process_interleaved_f32,
        audio_dsp_process_interleaved_i16, audio_dsp_reset,
    };
    use jni::{
        objects::{JByteBuffer, JClass},
        sys::{jint, jlong},
        JNIEnv,
    };

    #[no_mangle]
    pub unsafe extern "system" fn Java_io_github_julystar_musicapp_core_audio_RustDspNative_nativeConfigureFormat(
        _env: JNIEnv,
        _class: JClass,
        handle: jlong,
        sample_rate: jint,
        channels: jint,
    ) -> jint {
        if sample_rate <= 0 || channels <= 0 {
            return -1;
        }
        audio_dsp_configure_format(handle as u64, sample_rate as u32, channels as u32)
    }

    #[no_mangle]
    pub unsafe extern "system" fn Java_io_github_julystar_musicapp_core_audio_RustDspNative_nativeReset(
        _env: JNIEnv,
        _class: JClass,
        handle: jlong,
    ) {
        audio_dsp_reset(handle as u64);
    }

    #[no_mangle]
    pub unsafe extern "system" fn Java_io_github_julystar_musicapp_core_audio_RustDspNative_nativeProcessFloat(
        env: JNIEnv,
        _class: JClass,
        handle: jlong,
        buffer: JByteBuffer,
        frames: jint,
        channels: jint,
    ) -> jint {
        if frames < 0 || channels <= 0 {
            return -1;
        }
        let Ok(address) = env.get_direct_buffer_address(&buffer) else {
            return -1;
        };
        audio_dsp_process_interleaved_f32(
            handle as u64,
            address.cast(),
            frames as u32,
            channels as u32,
        )
    }

    #[no_mangle]
    pub unsafe extern "system" fn Java_io_github_julystar_musicapp_core_audio_RustDspNative_nativeProcessI16(
        env: JNIEnv,
        _class: JClass,
        handle: jlong,
        buffer: JByteBuffer,
        sample_count: jint,
    ) -> jint {
        if sample_count < 0 {
            return -1;
        }
        let Ok(address) = env.get_direct_buffer_address(&buffer) else {
            return -1;
        };
        audio_dsp_process_interleaved_i16(handle as u64, address.cast(), sample_count as u32)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn c_abi_processes_float_and_i16_without_copying_through_uniffi() {
        let processor = ct_create_audio_dsp_processor();
        let handle = processor.native_handle();
        assert_eq!(unsafe { audio_dsp_configure_format(handle, 48_000, 2) }, 0);
        let mut float_samples = [0.25_f32; 64];
        assert_eq!(
            unsafe { audio_dsp_process_interleaved_f32(handle, float_samples.as_mut_ptr(), 32, 2) },
            0
        );
        let mut integer_samples = [8_192_i16; 64];
        assert_eq!(
            unsafe {
                audio_dsp_process_interleaved_i16(
                    handle,
                    integer_samples.as_mut_ptr(),
                    integer_samples.len() as u32,
                )
            },
            0
        );
        assert_eq!(
            unsafe { audio_dsp_process_interleaved_f32(handle, float_samples.as_mut_ptr(), 32, 1) },
            -3
        );
        assert_eq!(
            unsafe {
                audio_dsp_process_interleaved_i16(
                    handle,
                    integer_samples.as_mut_ptr(),
                    (integer_samples.len() - 1) as u32,
                )
            },
            -5
        );
        let mut channel_buffers = [float_samples.as_mut_ptr(); 3];
        assert_eq!(
            unsafe { audio_dsp_process_planar_f32(handle, channel_buffers.as_mut_ptr(), 16, 3) },
            -3
        );

        let snapshot = processor.runtime_snapshot();
        assert_eq!(snapshot.state, DspRuntimeState::Error);
        assert_eq!(
            snapshot.bypass_reason,
            DspRuntimeBypassReason::NativeProcessingError
        );
        assert!(snapshot.process_count >= 3);
        assert!(snapshot.buffer_duration_us > 0.0);

        unsafe {
            audio_dsp_set_runtime_bypass(
                handle,
                DspRuntimeBypassReason::UnsupportedSampleFormat as i32,
            )
        };
        let bypassed = processor.runtime_snapshot();
        assert_eq!(bypassed.state, DspRuntimeState::Bypassed);
        assert_eq!(
            bypassed.bypass_reason,
            DspRuntimeBypassReason::UnsupportedSampleFormat
        );

        // The legacy wrappers remain ABI-compatible during the neutral-name migration.
        assert_eq!(
            unsafe { tide_audio_dsp_configure_format(handle, 48_000, 2) },
            0
        );
        unsafe { audio_dsp_reset(handle) };
        assert_eq!(
            processor.runtime_snapshot().state,
            DspRuntimeState::Inactive
        );
    }
}
