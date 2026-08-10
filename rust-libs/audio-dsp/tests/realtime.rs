use std::{
    alloc::{GlobalAlloc, Layout, System},
    sync::atomic::{AtomicUsize, Ordering},
};

use audio_dsp::{
    AudioDspConfig, AudioDspProcessor, CompressorConfig, DynamicEqConfig, GraphicEqualizerConfig,
    LimiterConfig, LoudnessConfig, MonoBassConfig, MoogFilterConfig, ReverbConfig, ReverbPreset,
    SpatialAudioConfig, SpatialMode, SpeakerOutputConfig, ToneControlConfig,
};

struct CountingAllocator;
static ALLOCATIONS: AtomicUsize = AtomicUsize::new(0);

unsafe impl GlobalAlloc for CountingAllocator {
    unsafe fn alloc(&self, layout: Layout) -> *mut u8 {
        ALLOCATIONS.fetch_add(1, Ordering::Relaxed);
        unsafe { System.alloc(layout) }
    }

    unsafe fn dealloc(&self, pointer: *mut u8, layout: Layout) {
        unsafe { System.dealloc(pointer, layout) }
    }
}

#[global_allocator]
static GLOBAL_ALLOCATOR: CountingAllocator = CountingAllocator;

#[test]
fn realtime_interleaved_and_planar_processing_do_not_allocate() {
    let graphic = AudioDspConfig {
        enabled: true,
        graphic_equalizer: GraphicEqualizerConfig {
            enabled: true,
            gains_db: [3.0, 2.0, 1.0, 0.0, -1.0, -2.0, -3.0, 1.0, 2.0, 3.0],
            ..Default::default()
        },
        ..Default::default()
    };
    let full_effects = AudioDspConfig {
        enabled: true,
        graphic_equalizer: graphic.graphic_equalizer,
        tone: ToneControlConfig {
            enabled: true,
            bass_gain_db: 3.0,
            treble_gain_db: 2.0,
            ..Default::default()
        },
        loudness: LoudnessConfig {
            enabled: true,
            amount: 0.5,
            ..Default::default()
        },
        mono_bass: MonoBassConfig {
            enabled: true,
            ..Default::default()
        },
        dynamic_eq: DynamicEqConfig {
            enabled: true,
            amount: 0.5,
            de_esser_amount: 0.5,
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
            preset: ReverbPreset::MediumRoom,
            wet: 0.15,
        },
        spatial: SpatialAudioConfig {
            mode: SpatialMode::Panoramic360,
            intensity: 0.5,
            ..Default::default()
        },
        speaker_output: SpeakerOutputConfig {
            enabled: true,
            ..Default::default()
        },
        ..Default::default()
    };

    let true_peak = AudioDspConfig {
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

    for config in [graphic, full_effects, true_peak] {
        let mut processor = AudioDspProcessor::new(config).unwrap();
        processor.configure_format(48_000, 2).unwrap();
        let mut samples = [0.1; 2_048];
        processor.process_interleaved_f32(&mut samples).unwrap();

        ALLOCATIONS.store(0, Ordering::SeqCst);
        processor.process_interleaved_f32(&mut samples).unwrap();
        assert_eq!(ALLOCATIONS.load(Ordering::SeqCst), 0);

        let mut left = [0.1; 1_024];
        let mut right = [0.1; 1_024];
        processor
            .process_planar_f32(&mut [&mut left, &mut right])
            .unwrap();

        ALLOCATIONS.store(0, Ordering::SeqCst);
        processor
            .process_planar_f32(&mut [&mut left, &mut right])
            .unwrap();
        assert_eq!(ALLOCATIONS.load(Ordering::SeqCst), 0);
    }
}
