// Adapted from RawS Music:
// https://github.com/QFDY-GZC/RawS-Music
//
// Original project license: Apache-2.0.
// This implementation has been rewritten and modified for TidePlayer's
// cross-platform Rust DSP pipeline.

pub const DSP_SCHEMA_VERSION: u32 = 2;
pub const MAX_CHANNELS: usize = 2;
pub const MAX_PARAMETRIC_EQ_BANDS: usize = 40;
pub const GRAPHIC_EQ_BAND_COUNT: usize = 10;
pub const GRAPHIC_EQ_FREQUENCIES_HZ: [f32; GRAPHIC_EQ_BAND_COUNT] = [
    31.0, 62.0, 125.0, 250.0, 500.0, 1_000.0, 2_000.0, 4_000.0, 8_000.0, 16_000.0,
];

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
#[repr(u8)]
pub enum HeadroomMode {
    #[default]
    Off = 0,
    Automatic = 1,
    Manual = 2,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct HeadroomConfig {
    pub mode: HeadroomMode,
    pub manual_db: f32,
}

impl Default for HeadroomConfig {
    fn default() -> Self {
        Self {
            mode: HeadroomMode::Off,
            manual_db: 0.0,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
#[repr(u8)]
pub enum EqMode {
    #[default]
    Graphic = 0,
    Parametric = 1,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
#[repr(u8)]
pub enum BiquadFilterType {
    #[default]
    Peak = 0,
    LowShelf = 1,
    HighShelf = 2,
    LowPass = 3,
    HighPass = 4,
    BandPass = 5,
    Notch = 6,
}

#[derive(Debug, Clone, Copy, PartialEq)]
#[repr(C)]
pub struct ParametricEqBand {
    pub enabled: bool,
    pub filter_type: BiquadFilterType,
    pub frequency_hz: f32,
    pub gain_db: f32,
    pub q: f32,
}

impl Default for ParametricEqBand {
    fn default() -> Self {
        Self {
            enabled: false,
            filter_type: BiquadFilterType::Peak,
            frequency_hz: 1_000.0,
            gain_db: 0.0,
            q: 1.0,
        }
    }
}

impl ParametricEqBand {
    pub(crate) fn sanitized(self, sample_rate: u32) -> Self {
        let nyquist_limit = sample_rate as f32 * 0.48;
        Self {
            enabled: self.enabled,
            filter_type: self.filter_type,
            frequency_hz: finite_or(self.frequency_hz, 1_000.0)
                .clamp(10.0, nyquist_limit.max(10.0)),
            gain_db: finite_or(self.gain_db, 0.0).clamp(-24.0, 24.0),
            q: finite_or(self.q, 1.0).clamp(0.05, 24.0),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct GraphicEqualizerConfig {
    pub enabled: bool,
    pub preamp_db: f32,
    pub q: f32,
    pub gains_db: [f32; GRAPHIC_EQ_BAND_COUNT],
}

impl Default for GraphicEqualizerConfig {
    fn default() -> Self {
        Self {
            enabled: false,
            preamp_db: 0.0,
            q: 1.0,
            gains_db: [0.0; GRAPHIC_EQ_BAND_COUNT],
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct ParametricEqualizerConfig {
    pub enabled: bool,
    pub preamp_db: f32,
    pub band_count: usize,
    pub bands: [ParametricEqBand; MAX_PARAMETRIC_EQ_BANDS],
}

impl Default for ParametricEqualizerConfig {
    fn default() -> Self {
        Self {
            enabled: false,
            preamp_db: 0.0,
            band_count: 0,
            bands: [ParametricEqBand::default(); MAX_PARAMETRIC_EQ_BANDS],
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct ToneControlConfig {
    pub enabled: bool,
    pub bass_gain_db: f32,
    pub bass_frequency_hz: f32,
    pub treble_gain_db: f32,
    pub treble_frequency_hz: f32,
}

impl Default for ToneControlConfig {
    fn default() -> Self {
        Self {
            enabled: false,
            bass_gain_db: 0.0,
            bass_frequency_hz: 120.0,
            treble_gain_db: 0.0,
            treble_frequency_hz: 8_000.0,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct CompressorConfig {
    pub enabled: bool,
    pub threshold_db: f32,
    pub ratio: f32,
    pub attack_ms: f32,
    pub release_ms: f32,
    pub makeup_gain_db: f32,
    pub knee_db: f32,
}

impl Default for CompressorConfig {
    fn default() -> Self {
        Self {
            enabled: false,
            threshold_db: -18.0,
            ratio: 4.0,
            attack_ms: 10.0,
            release_ms: 120.0,
            makeup_gain_db: 0.0,
            knee_db: 6.0,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct LimiterConfig {
    pub enabled: bool,
    pub ceiling_db: f32,
    pub attack_ms: f32,
    pub release_ms: f32,
    pub true_peak_enabled: bool,
    pub oversampling: u8,
    pub lookahead_ms: f32,
}

impl Default for LimiterConfig {
    fn default() -> Self {
        Self {
            enabled: true,
            ceiling_db: -0.5,
            attack_ms: 0.25,
            release_ms: 80.0,
            true_peak_enabled: false,
            oversampling: 1,
            lookahead_ms: 3.0,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct LoudnessConfig {
    pub enabled: bool,
    pub amount: f32,
    pub balance: f32,
}

impl Default for LoudnessConfig {
    fn default() -> Self {
        Self {
            enabled: false,
            amount: 0.0,
            balance: 0.0,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct DynamicEqConfig {
    pub enabled: bool,
    pub amount: f32,
    pub de_esser_amount: f32,
    pub de_esser_frequency_hz: f32,
}

impl Default for DynamicEqConfig {
    fn default() -> Self {
        Self {
            enabled: false,
            amount: 0.0,
            de_esser_amount: 0.0,
            de_esser_frequency_hz: 6_500.0,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct MonoBassConfig {
    pub enabled: bool,
    pub crossover_hz: f32,
    pub amount: f32,
}

impl Default for MonoBassConfig {
    fn default() -> Self {
        Self {
            enabled: false,
            crossover_hz: 120.0,
            amount: 1.0,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct StereoWidthConfig {
    pub enabled: bool,
    pub width: f32,
}

impl Default for StereoWidthConfig {
    fn default() -> Self {
        Self {
            enabled: false,
            width: 1.0,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct CrossfeedConfig {
    pub enabled: bool,
    pub low_cut_hz: f32,
    pub high_cut_hz: f32,
    pub attenuation_db: f32,
}

impl Default for CrossfeedConfig {
    fn default() -> Self {
        Self {
            enabled: false,
            low_cut_hz: 120.0,
            high_cut_hz: 700.0,
            attenuation_db: 6.0,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
#[repr(u8)]
pub enum SpatialMode {
    #[default]
    None = 0,
    CrossfeedAndWidth = 1,
    Surround360 = 2,
    Panoramic360 = 3,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct SpatialAudioConfig {
    pub mode: SpatialMode,
    pub intensity: f32,
    pub azimuth_degrees: f32,
    pub elevation_degrees: f32,
    pub auto_rotate_degrees_per_second: f32,
    pub room_amount: f32,
}

impl Default for SpatialAudioConfig {
    fn default() -> Self {
        Self {
            mode: SpatialMode::None,
            intensity: 0.0,
            azimuth_degrees: 0.0,
            elevation_degrees: 0.0,
            auto_rotate_degrees_per_second: 0.0,
            room_amount: 0.15,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
#[repr(u8)]
pub enum MoogFilterMode {
    #[default]
    LowPass24 = 0,
    LowPass12 = 1,
    HighPass24 = 2,
    BandPass12 = 3,
    Notch = 4,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct MoogFilterConfig {
    pub enabled: bool,
    pub mode: MoogFilterMode,
    pub cutoff_hz: f32,
    pub resonance: f32,
    pub drive_db: f32,
    pub mix: f32,
}

impl Default for MoogFilterConfig {
    fn default() -> Self {
        Self {
            enabled: false,
            mode: MoogFilterMode::LowPass24,
            cutoff_hz: 8_000.0,
            resonance: 0.0,
            drive_db: 0.0,
            mix: 1.0,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
#[repr(u8)]
pub enum SpeakerOutputMode {
    #[default]
    Elasticity = 0,
    Powerful = 1,
    Wide = 2,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct SpeakerOutputConfig {
    pub enabled: bool,
    pub mode: SpeakerOutputMode,
    pub strength: f32,
}

impl Default for SpeakerOutputConfig {
    fn default() -> Self {
        Self {
            enabled: false,
            mode: SpeakerOutputMode::Elasticity,
            strength: 0.5,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
#[repr(u8)]
pub enum ReverbPreset {
    #[default]
    None = 0,
    SmallRoom = 1,
    MediumRoom = 2,
    LargeRoom = 3,
    Hall = 4,
    Plate = 5,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct ReverbConfig {
    pub preset: ReverbPreset,
    pub wet: f32,
}

impl Default for ReverbConfig {
    fn default() -> Self {
        Self {
            preset: ReverbPreset::None,
            wet: 0.15,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct AudioDspConfig {
    pub schema_version: u32,
    pub enabled: bool,
    pub input_gain_db: f32,
    pub headroom: HeadroomConfig,
    pub eq_mode: EqMode,
    pub graphic_equalizer: GraphicEqualizerConfig,
    pub parametric_equalizer: ParametricEqualizerConfig,
    pub tone: ToneControlConfig,
    pub loudness: LoudnessConfig,
    pub mono_bass: MonoBassConfig,
    pub dynamic_eq: DynamicEqConfig,
    pub moog: MoogFilterConfig,
    pub compressor: CompressorConfig,
    pub reverb: ReverbConfig,
    pub stereo_width: StereoWidthConfig,
    pub crossfeed: CrossfeedConfig,
    pub spatial: SpatialAudioConfig,
    pub speaker_output: SpeakerOutputConfig,
    pub limiter: LimiterConfig,
}

impl Default for AudioDspConfig {
    fn default() -> Self {
        Self {
            schema_version: DSP_SCHEMA_VERSION,
            enabled: false,
            input_gain_db: 0.0,
            headroom: HeadroomConfig::default(),
            eq_mode: EqMode::Graphic,
            graphic_equalizer: GraphicEqualizerConfig::default(),
            parametric_equalizer: ParametricEqualizerConfig::default(),
            tone: ToneControlConfig::default(),
            loudness: LoudnessConfig::default(),
            mono_bass: MonoBassConfig::default(),
            dynamic_eq: DynamicEqConfig::default(),
            moog: MoogFilterConfig::default(),
            compressor: CompressorConfig::default(),
            reverb: ReverbConfig::default(),
            stereo_width: StereoWidthConfig::default(),
            crossfeed: CrossfeedConfig::default(),
            spatial: SpatialAudioConfig::default(),
            speaker_output: SpeakerOutputConfig::default(),
            limiter: LimiterConfig::default(),
        }
    }
}

impl AudioDspConfig {
    pub fn sanitized(mut self, sample_rate: u32) -> Self {
        self.schema_version = DSP_SCHEMA_VERSION;
        self.input_gain_db = finite_or(self.input_gain_db, 0.0).clamp(-96.0, 24.0);
        self.headroom.manual_db = finite_or(self.headroom.manual_db, 0.0).clamp(-24.0, 0.0);

        self.graphic_equalizer.preamp_db =
            finite_or(self.graphic_equalizer.preamp_db, 0.0).clamp(-24.0, 12.0);
        self.graphic_equalizer.q = finite_or(self.graphic_equalizer.q, 1.0).clamp(0.1, 10.0);
        for gain in &mut self.graphic_equalizer.gains_db {
            *gain = finite_or(*gain, 0.0).clamp(-24.0, 24.0);
        }

        self.parametric_equalizer.preamp_db =
            finite_or(self.parametric_equalizer.preamp_db, 0.0).clamp(-96.0, 12.0);
        self.parametric_equalizer.band_count = self
            .parametric_equalizer
            .band_count
            .min(MAX_PARAMETRIC_EQ_BANDS);
        for band in self
            .parametric_equalizer
            .bands
            .iter_mut()
            .take(self.parametric_equalizer.band_count)
        {
            *band = band.sanitized(sample_rate);
        }

        self.tone.bass_gain_db = finite_or(self.tone.bass_gain_db, 0.0).clamp(-24.0, 24.0);
        self.tone.bass_frequency_hz =
            finite_or(self.tone.bass_frequency_hz, 120.0).clamp(50.0, 500.0);
        self.tone.treble_gain_db = finite_or(self.tone.treble_gain_db, 0.0).clamp(-24.0, 24.0);
        self.tone.treble_frequency_hz = finite_or(self.tone.treble_frequency_hz, 8_000.0)
            .clamp(2_000.0, (sample_rate as f32 * 0.45).min(16_000.0));

        self.compressor.threshold_db =
            finite_or(self.compressor.threshold_db, -18.0).clamp(-60.0, 0.0);
        self.compressor.ratio = finite_or(self.compressor.ratio, 4.0).clamp(1.0, 30.0);
        self.compressor.attack_ms = finite_or(self.compressor.attack_ms, 10.0).clamp(0.05, 500.0);
        self.compressor.release_ms =
            finite_or(self.compressor.release_ms, 120.0).clamp(5.0, 5_000.0);
        self.compressor.makeup_gain_db =
            finite_or(self.compressor.makeup_gain_db, 0.0).clamp(-12.0, 24.0);
        self.compressor.knee_db = finite_or(self.compressor.knee_db, 6.0).clamp(0.0, 24.0);

        self.limiter.ceiling_db = finite_or(self.limiter.ceiling_db, -0.5).clamp(-12.0, 0.0);
        self.limiter.attack_ms = finite_or(self.limiter.attack_ms, 0.25).clamp(0.01, 20.0);
        self.limiter.release_ms = finite_or(self.limiter.release_ms, 80.0).clamp(5.0, 2_000.0);
        if self.limiter.true_peak_enabled {
            self.limiter.oversampling = 4;
        } else {
            self.limiter.oversampling = 1;
        }
        self.limiter.lookahead_ms = finite_or(self.limiter.lookahead_ms, 3.0).clamp(1.0, 10.0);

        self.loudness.amount = finite_or(self.loudness.amount, 0.0).clamp(0.0, 1.0);
        self.loudness.balance = finite_or(self.loudness.balance, 0.0).clamp(-1.0, 1.0);
        self.mono_bass.crossover_hz =
            finite_or(self.mono_bass.crossover_hz, 120.0).clamp(60.0, 300.0);
        self.mono_bass.amount = finite_or(self.mono_bass.amount, 1.0).clamp(0.0, 1.0);
        self.dynamic_eq.amount = finite_or(self.dynamic_eq.amount, 0.0).clamp(0.0, 1.0);
        self.dynamic_eq.de_esser_amount =
            finite_or(self.dynamic_eq.de_esser_amount, 0.0).clamp(0.0, 1.0);
        self.dynamic_eq.de_esser_frequency_hz =
            finite_or(self.dynamic_eq.de_esser_frequency_hz, 6_500.0)
                .clamp(4_000.0, (sample_rate as f32 * 0.45).min(10_000.0));

        self.stereo_width.width = finite_or(self.stereo_width.width, 1.0).clamp(0.0, 2.0);
        self.crossfeed.low_cut_hz =
            finite_or(self.crossfeed.low_cut_hz, 120.0).clamp(50.0, 1_000.0);
        self.crossfeed.high_cut_hz =
            finite_or(self.crossfeed.high_cut_hz, 700.0).clamp(500.0, 8_000.0);
        self.crossfeed.attenuation_db =
            finite_or(self.crossfeed.attenuation_db, 6.0).clamp(0.0, 15.0);

        self.spatial.intensity = finite_or(self.spatial.intensity, 0.0).clamp(0.0, 1.0);
        self.spatial.azimuth_degrees =
            finite_or(self.spatial.azimuth_degrees, 0.0).rem_euclid(360.0);
        self.spatial.elevation_degrees =
            finite_or(self.spatial.elevation_degrees, 0.0).clamp(-90.0, 90.0);
        self.spatial.auto_rotate_degrees_per_second =
            finite_or(self.spatial.auto_rotate_degrees_per_second, 0.0).clamp(-180.0, 180.0);
        self.spatial.room_amount = finite_or(self.spatial.room_amount, 0.15).clamp(0.0, 1.0);

        self.moog.cutoff_hz = finite_or(self.moog.cutoff_hz, 8_000.0)
            .clamp(20.0, (sample_rate as f32 * 0.45).min(20_000.0));
        self.moog.resonance = finite_or(self.moog.resonance, 0.0).clamp(0.0, 1.0);
        self.moog.drive_db = finite_or(self.moog.drive_db, 0.0).clamp(0.0, 18.0);
        self.moog.mix = finite_or(self.moog.mix, 1.0).clamp(0.0, 1.0);
        self.speaker_output.strength = finite_or(self.speaker_output.strength, 0.5).clamp(0.0, 1.0);
        self.reverb.wet = finite_or(self.reverb.wet, 0.15).clamp(0.0, 0.5);
        self
    }

    pub fn has_active_effects(&self) -> bool {
        self.enabled
            && (self.input_gain_db.abs() > 1.0e-4
                || matches!(self.headroom.mode, HeadroomMode::Manual)
                    && self.headroom.manual_db.abs() > 1.0e-4
                || match self.eq_mode {
                    EqMode::Graphic => self.graphic_equalizer.enabled,
                    EqMode::Parametric => self.parametric_equalizer.enabled,
                }
                || self.tone.enabled
                || self.loudness.enabled
                || self.mono_bass.enabled
                || self.dynamic_eq.enabled
                || self.moog.enabled
                || self.compressor.enabled
                || self.reverb.preset != ReverbPreset::None
                || self.spatial.mode != SpatialMode::None
                || self.speaker_output.enabled
                || self.limiter.enabled)
    }
}

pub(crate) fn finite_or(value: f32, fallback: f32) -> f32 {
    if value.is_finite() {
        value
    } else {
        fallback
    }
}

pub(crate) fn db_to_linear(db: f32) -> f32 {
    10.0_f32.powf(db / 20.0)
}

pub(crate) fn smoothing_coefficient(time_ms: f32, sample_rate: u32) -> f32 {
    let samples = (time_ms.max(0.001) * 0.001 * sample_rate.max(1) as f32).max(1.0);
    1.0 - (-1.0 / samples).exp()
}
