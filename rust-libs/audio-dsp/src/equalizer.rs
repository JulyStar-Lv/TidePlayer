// Adapted from RawS Music:
// https://github.com/QFDY-GZC/RawS-Music
//
// Original project license: Apache-2.0.
// This implementation has been rewritten and modified for TidePlayer's
// cross-platform Rust DSP pipeline.

use crate::{
    biquad::{Biquad, BiquadCoefficients, FrequencyResponse},
    config::{
        db_to_linear, BiquadFilterType, EqMode, GraphicEqualizerConfig, ParametricEqBand,
        ParametricEqualizerConfig, GRAPHIC_EQ_BAND_COUNT, GRAPHIC_EQ_FREQUENCIES_HZ, MAX_CHANNELS,
        MAX_PARAMETRIC_EQ_BANDS,
    },
};

#[derive(Debug)]
pub(crate) struct Equalizer {
    sample_rate: u32,
    enabled: bool,
    active_count: usize,
    filters: [[Biquad; MAX_PARAMETRIC_EQ_BANDS]; MAX_CHANNELS],
    response_coefficients: [BiquadCoefficients; MAX_PARAMETRIC_EQ_BANDS],
    preamp_current: f32,
    preamp_target: f32,
    response_preamp_db: f32,
}

impl Default for Equalizer {
    fn default() -> Self {
        Self {
            sample_rate: 48_000,
            enabled: false,
            active_count: 0,
            filters: [[Biquad::default(); MAX_PARAMETRIC_EQ_BANDS]; MAX_CHANNELS],
            response_coefficients: [BiquadCoefficients::IDENTITY; MAX_PARAMETRIC_EQ_BANDS],
            preamp_current: 1.0,
            preamp_target: 1.0,
            response_preamp_db: 0.0,
        }
    }
}

impl Equalizer {
    pub(crate) fn configure(
        &mut self,
        sample_rate: u32,
        mode: EqMode,
        graphic: GraphicEqualizerConfig,
        parametric: ParametricEqualizerConfig,
        smooth: bool,
    ) {
        self.sample_rate = sample_rate;
        match mode {
            EqMode::Graphic => self.configure_graphic(graphic, smooth),
            EqMode::Parametric => self.configure_parametric(parametric, smooth),
        }
    }

    fn configure_graphic(&mut self, config: GraphicEqualizerConfig, smooth: bool) {
        let mut bands = [ParametricEqBand::default(); GRAPHIC_EQ_BAND_COUNT];
        for (index, band) in bands.iter_mut().enumerate() {
            *band = ParametricEqBand {
                enabled: config.gains_db[index].abs() > 1.0e-4,
                filter_type: BiquadFilterType::Peak,
                frequency_hz: GRAPHIC_EQ_FREQUENCIES_HZ[index],
                gain_db: config.gains_db[index],
                q: config.q,
            };
        }
        self.configure_bands(config.enabled, config.preamp_db, &bands, smooth);
    }

    fn configure_parametric(&mut self, config: ParametricEqualizerConfig, smooth: bool) {
        let count = config.band_count.min(MAX_PARAMETRIC_EQ_BANDS);
        self.configure_bands(
            config.enabled,
            config.preamp_db,
            &config.bands[..count],
            smooth,
        );
    }

    fn configure_bands(
        &mut self,
        enabled: bool,
        preamp_db: f32,
        bands: &[ParametricEqBand],
        smooth: bool,
    ) {
        self.enabled = enabled;
        self.response_preamp_db = preamp_db;
        self.preamp_target = db_to_linear(preamp_db);
        if !smooth {
            self.preamp_current = self.preamp_target;
        }

        let mut active_count = 0;
        for band in bands.iter().copied().filter(|band| band.enabled) {
            if active_count == MAX_PARAMETRIC_EQ_BANDS {
                break;
            }
            let coefficients = BiquadCoefficients::for_band(self.sample_rate, band);
            self.response_coefficients[active_count] = coefficients;
            for channel in &mut self.filters {
                channel[active_count].set_coefficients(coefficients, smooth);
            }
            active_count += 1;
        }
        if active_count < self.active_count {
            for channel in &mut self.filters {
                for filter in &mut channel[active_count..self.active_count] {
                    filter.reset();
                }
            }
        }
        self.active_count = active_count;
    }

    pub(crate) fn process_frame(&mut self, frame: &mut [f32; MAX_CHANNELS], channels: usize) {
        if !self.enabled {
            return;
        }
        self.preamp_current += (self.preamp_target - self.preamp_current) * 0.01;
        for (channel, sample) in frame.iter_mut().enumerate().take(channels) {
            let mut output = *sample * self.preamp_current;
            for filter in &mut self.filters[channel][..self.active_count] {
                output = filter.process(output);
            }
            *sample = output;
        }
    }

    pub(crate) fn reset(&mut self) {
        for channel in &mut self.filters {
            for filter in channel {
                filter.reset();
            }
        }
        self.preamp_current = self.preamp_target;
    }
}

impl FrequencyResponse for Equalizer {
    fn frequency_response_db(&self, frequency_hz: f32) -> f32 {
        if !self.enabled {
            return 0.0;
        }
        self.response_preamp_db
            + self.response_coefficients[..self.active_count]
                .iter()
                .map(|coefficients| coefficients.magnitude_db(frequency_hz, self.sample_rate))
                .sum::<f32>()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn graphic_eq_maps_all_ten_fixed_frequencies() {
        let mut equalizer = Equalizer::default();
        let mut config = GraphicEqualizerConfig {
            enabled: true,
            ..GraphicEqualizerConfig::default()
        };
        config.gains_db[5] = 6.0;
        equalizer.configure(48_000, EqMode::Graphic, config, Default::default(), false);
        assert_eq!(equalizer.active_count, 1);
        assert!((equalizer.frequency_response_db(1_000.0) - 6.0).abs() < 0.1);
    }
}
