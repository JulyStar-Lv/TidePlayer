use std::{
    sync::{Arc, Mutex},
    time::{Duration, Instant},
};

use audio_dsp::{AudioDspConfig, AudioDspProcessor};
use rodio::{source::SeekError, ChannelCount, SampleRate, Source};
use triple_buffer::{triple_buffer, Input, Output};

use super::audio_dsp_bridge::{DspRuntimeTelemetry, DspSampleFormat};

const BLOCK_SAMPLE_CAPACITY: usize = 2_048;

#[derive(Clone, Debug)]
pub struct DesktopDspConfigInput {
    inner: Arc<Mutex<Input<AudioDspConfig>>>,
}

impl DesktopDspConfigInput {
    pub fn publish(&self, config: AudioDspConfig) {
        if let Ok(mut input) = self.inner.lock() {
            input.write(config);
        }
    }
}

pub struct DesktopDspSource<S> {
    inner: S,
    processor: Option<AudioDspProcessor>,
    config_output: Output<AudioDspConfig>,
    samples: [f32; BLOCK_SAMPLE_CAPACITY],
    sample_count: usize,
    read_cursor: usize,
    telemetry: Arc<DspRuntimeTelemetry>,
}

impl<S: Source> DesktopDspSource<S> {
    #[cfg(test)]
    pub fn new(inner: S, config: AudioDspConfig) -> (Self, DesktopDspConfigInput) {
        Self::new_with_telemetry(inner, config, Arc::new(DspRuntimeTelemetry::default()))
    }

    pub(crate) fn new_with_telemetry(
        inner: S,
        config: AudioDspConfig,
        telemetry: Arc<DspRuntimeTelemetry>,
    ) -> (Self, DesktopDspConfigInput) {
        let channels = inner.channels().get() as usize;
        let sample_rate = inner.sample_rate().get();
        let processor =
            AudioDspProcessor::new(config)
                .ok()
                .and_then(|mut processor| {
                    match processor.configure_format(sample_rate, channels) {
                        Ok(()) => {
                            telemetry.configured(&processor);
                            Some(processor)
                        }
                        Err(error) => {
                            telemetry.configure_error(error);
                            None
                        }
                    }
                });
        let (input, output) = triple_buffer(&config);
        (
            Self {
                inner,
                processor,
                config_output: output,
                samples: [0.0; BLOCK_SAMPLE_CAPACITY],
                sample_count: 0,
                read_cursor: 0,
                telemetry,
            },
            DesktopDspConfigInput {
                inner: Arc::new(Mutex::new(input)),
            },
        )
    }

    fn fill_block(&mut self) -> bool {
        let channels = self.inner.channels().get() as usize;
        let capacity = BLOCK_SAMPLE_CAPACITY - BLOCK_SAMPLE_CAPACITY % channels.max(1);
        let mut count = 0;
        while count < capacity {
            let Some(sample) = self.inner.next() else {
                break;
            };
            self.samples[count] = sample;
            count += 1;
        }
        count -= count % channels.max(1);
        if count == 0 {
            return false;
        }
        if self.config_output.update() {
            if let Some(processor) = self.processor.as_mut() {
                let config = *self.config_output.output_buffer();
                let _ = processor.update_config(config);
            }
        }
        if let Some(processor) = self.processor.as_mut() {
            let started = Instant::now();
            let result = processor.process_interleaved_f32(&mut self.samples[..count]);
            self.telemetry.record_process(
                processor,
                DspSampleFormat::Float32,
                count / channels,
                started.elapsed().as_nanos().min(u64::MAX as u128) as u64,
                result,
            );
        }
        self.sample_count = count;
        self.read_cursor = 0;
        true
    }

    fn reset_processing_state(&mut self) {
        self.sample_count = 0;
        self.read_cursor = 0;
        if let Some(processor) = self.processor.as_mut() {
            processor.reset();
        }
        self.telemetry.reset();
    }
}

impl<S: Source> Iterator for DesktopDspSource<S> {
    type Item = f32;

    fn next(&mut self) -> Option<Self::Item> {
        if self.read_cursor == self.sample_count && !self.fill_block() {
            return None;
        }
        let sample = self.samples[self.read_cursor];
        self.read_cursor += 1;
        Some(sample)
    }
}

impl<S: Source> Source for DesktopDspSource<S> {
    fn current_span_len(&self) -> Option<usize> {
        self.inner.current_span_len()
    }

    fn channels(&self) -> ChannelCount {
        self.inner.channels()
    }

    fn sample_rate(&self) -> SampleRate {
        self.inner.sample_rate()
    }

    fn total_duration(&self) -> Option<Duration> {
        self.inner.total_duration()
    }

    fn try_seek(&mut self, pos: Duration) -> Result<(), SeekError> {
        self.inner.try_seek(pos)?;
        self.reset_processing_state();
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use rodio::buffer::SamplesBuffer;
    use std::num::NonZero;

    #[test]
    fn source_processes_in_blocks_and_preserves_length() {
        let source = SamplesBuffer::new(
            NonZero::new(2).unwrap(),
            NonZero::new(48_000).unwrap(),
            vec![0.25; 4_096],
        );
        let config = AudioDspConfig {
            enabled: true,
            tone: audio_dsp::ToneControlConfig {
                enabled: true,
                bass_gain_db: 6.0,
                ..Default::default()
            },
            ..Default::default()
        };
        let (source, _) = DesktopDspSource::new(source, config);
        let output = source.collect::<Vec<_>>();
        assert_eq!(output.len(), 4_096);
        assert!(output.iter().all(|sample| sample.is_finite()));
    }

    #[test]
    fn live_config_update_is_consumed_at_next_block_boundary() {
        let source = SamplesBuffer::new(
            NonZero::new(1).unwrap(),
            NonZero::new(48_000).unwrap(),
            vec![0.25; 8_192],
        );
        let (mut source, input) = DesktopDspSource::new(source, AudioDspConfig::default());
        let first = source.next().unwrap();
        input.publish(AudioDspConfig {
            enabled: true,
            input_gain_db: -12.0,
            ..Default::default()
        });
        for _ in 1..BLOCK_SAMPLE_CAPACITY {
            source.next();
        }
        let after_update = source.next().unwrap();
        assert_eq!(first, 0.25);
        assert!(after_update < first);
    }
}
