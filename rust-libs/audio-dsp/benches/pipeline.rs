use audio_dsp::{
    AudioDspConfig, AudioDspProcessor, CompressorConfig, DynamicEqConfig, EqMode,
    GraphicEqualizerConfig, LimiterConfig, LoudnessConfig, MonoBassConfig, MoogFilterConfig,
    ParametricEqBand, ParametricEqualizerConfig, ReverbConfig, ReverbPreset, SpatialAudioConfig,
    SpatialMode, SpeakerOutputConfig, StereoWidthConfig, ToneControlConfig,
    MAX_PARAMETRIC_EQ_BANDS,
};
use criterion::{black_box, criterion_group, criterion_main, BenchmarkId, Criterion};

fn benchmark_pipeline(c: &mut Criterion) {
    let mut group = c.benchmark_group("audio_dsp");
    let frames = 1_024;
    for sample_rate in [44_100, 48_000, 96_000, 192_000] {
        group.bench_with_input(
            BenchmarkId::new("bypass_stereo", sample_rate),
            &sample_rate,
            |bench, sample_rate| {
                let mut processor = AudioDspProcessor::new(AudioDspConfig::default()).unwrap();
                processor.configure_format(*sample_rate, 2).unwrap();
                let mut samples = vec![0.1; frames * 2];
                bench.iter(|| {
                    processor
                        .process_interleaved_f32(black_box(&mut samples))
                        .unwrap()
                });
            },
        );
    }

    let graphic = AudioDspConfig {
        enabled: true,
        graphic_equalizer: GraphicEqualizerConfig {
            enabled: true,
            gains_db: [3.0; 10],
            ..Default::default()
        },
        ..Default::default()
    };
    group.bench_function("graphic_eq_10_band_48k_stereo", |bench| {
        let mut processor = AudioDspProcessor::new(graphic).unwrap();
        processor.configure_format(48_000, 2).unwrap();
        let mut samples = vec![0.1; frames * 2];
        bench.iter(|| {
            processor
                .process_interleaved_f32(black_box(&mut samples))
                .unwrap()
        });
    });

    for sample_rate in [48_000, 96_000] {
        group.bench_with_input(
            BenchmarkId::new("true_peak_4x_stereo", sample_rate),
            &sample_rate,
            |bench, sample_rate| {
                let mut processor = AudioDspProcessor::new(AudioDspConfig {
                    enabled: true,
                    limiter: LimiterConfig {
                        enabled: true,
                        ceiling_db: -1.0,
                        true_peak_enabled: true,
                        oversampling: 4,
                        lookahead_ms: 3.0,
                        ..Default::default()
                    },
                    ..Default::default()
                })
                .unwrap();
                processor.configure_format(*sample_rate, 2).unwrap();
                let mut samples = vec![0.9; frames * 2];
                bench.iter(|| {
                    processor
                        .process_interleaved_f32(black_box(&mut samples))
                        .unwrap()
                });
            },
        );
    }

    let mut parametric = ParametricEqualizerConfig {
        enabled: true,
        band_count: MAX_PARAMETRIC_EQ_BANDS,
        ..Default::default()
    };
    for (index, band) in parametric.bands.iter_mut().enumerate() {
        *band = ParametricEqBand {
            enabled: true,
            frequency_hz: 30.0 * (1.17_f32).powi(index as i32),
            gain_db: if index % 2 == 0 { 1.0 } else { -1.0 },
            ..Default::default()
        };
    }
    group.bench_function("parametric_eq_40_band_48k_stereo", |bench| {
        let mut processor = AudioDspProcessor::new(AudioDspConfig {
            enabled: true,
            eq_mode: EqMode::Parametric,
            parametric_equalizer: parametric,
            ..Default::default()
        })
        .unwrap();
        processor.configure_format(48_000, 2).unwrap();
        let mut samples = vec![0.1; frames * 2];
        bench.iter(|| {
            processor
                .process_interleaved_f32(black_box(&mut samples))
                .unwrap()
        });
    });

    let full_effects = AudioDspConfig {
        enabled: true,
        input_gain_db: -6.0,
        graphic_equalizer: GraphicEqualizerConfig {
            enabled: true,
            gains_db: [2.0, 1.0, 0.0, -1.0, 1.0, 2.0, -1.0, 1.0, 0.0, -2.0],
            ..Default::default()
        },
        tone: ToneControlConfig {
            enabled: true,
            bass_gain_db: 2.0,
            treble_gain_db: 1.0,
            ..Default::default()
        },
        loudness: LoudnessConfig {
            enabled: true,
            amount: 0.4,
            ..Default::default()
        },
        mono_bass: MonoBassConfig {
            enabled: true,
            ..Default::default()
        },
        dynamic_eq: DynamicEqConfig {
            enabled: true,
            amount: 0.4,
            de_esser_amount: 0.4,
            ..Default::default()
        },
        moog: MoogFilterConfig {
            enabled: true,
            mix: 0.25,
            ..Default::default()
        },
        compressor: CompressorConfig {
            enabled: true,
            ..Default::default()
        },
        reverb: ReverbConfig {
            preset: ReverbPreset::SmallRoom,
            wet: 0.1,
        },
        stereo_width: StereoWidthConfig {
            enabled: true,
            width: 1.25,
        },
        spatial: SpatialAudioConfig {
            mode: SpatialMode::Panoramic360,
            intensity: 0.3,
            ..Default::default()
        },
        speaker_output: SpeakerOutputConfig {
            enabled: true,
            strength: 0.25,
            ..Default::default()
        },
        limiter: LimiterConfig {
            enabled: true,
            ..Default::default()
        },
        ..Default::default()
    };
    group.bench_function("full_effects_48k_stereo", |bench| {
        let mut processor = AudioDspProcessor::new(full_effects).unwrap();
        processor.configure_format(48_000, 2).unwrap();
        let mut samples = vec![0.1; frames * 2];
        bench.iter(|| {
            processor
                .process_interleaved_f32(black_box(&mut samples))
                .unwrap()
        });
    });
    group.finish();
}

criterion_group!(benches, benchmark_pipeline);
criterion_main!(benches);
