const LEVEL_ATTACK_SECONDS: f32 = 0.045;
const LEVEL_RELEASE_SECONDS: f32 = 0.350;
const FAST_ATTACK_SECONDS: f32 = 0.035;
const FAST_RELEASE_SECONDS: f32 = 0.350;
const SLOW_ATTACK_SECONDS: f32 = 0.350;
const SLOW_RELEASE_SECONDS: f32 = 0.350;
const NOISE_FLOOR_SECONDS: f32 = 0.800;
const MIN_BEAT_GAP_SECONDS: f32 = 0.120;
const BEAT_DECAY_SECONDS: f32 = 0.220;
const TRANSIENT_MULTIPLIER: f32 = 2.5;
const TRANSIENT_EPSILON: f32 = 0.02;

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct AudioReactiveSnapshot {
    pub level: f32,
    pub beat: f32,
}

impl Default for AudioReactiveSnapshot {
    fn default() -> Self {
        Self {
            level: 0.0,
            beat: 0.0,
        }
    }
}

#[derive(Debug, Clone, Copy)]
pub(crate) struct AudioReactiveAnalyzer {
    snapshot: AudioReactiveSnapshot,
    fast_envelope: f32,
    slow_envelope: f32,
    noise_floor: f32,
    time_since_beat: f32,
    previous_delta: f32,
    beat_armed: bool,
}

impl Default for AudioReactiveAnalyzer {
    fn default() -> Self {
        Self {
            snapshot: AudioReactiveSnapshot::default(),
            fast_envelope: 0.0,
            slow_envelope: 0.0,
            noise_floor: 0.0,
            time_since_beat: MIN_BEAT_GAP_SECONDS,
            previous_delta: 0.0,
            beat_armed: true,
        }
    }
}

impl AudioReactiveAnalyzer {
    pub(crate) fn process_block(
        &mut self,
        sum_squares: f32,
        frames: usize,
        channels: usize,
        sample_rate: u32,
    ) {
        if frames == 0 || channels == 0 || sample_rate == 0 {
            return;
        }
        let duration = frames as f32 / sample_rate as f32;
        if !duration.is_finite() || duration <= 0.0 {
            return;
        }
        let sample_count = frames.saturating_mul(channels).max(1) as f32;
        let mean_square = if sum_squares.is_finite() {
            (sum_squares.max(0.0) / sample_count).min(1.0)
        } else {
            0.0
        };
        let rms = finite_clamped(mean_square.sqrt());

        self.fast_envelope = follow(
            self.fast_envelope,
            rms,
            FAST_ATTACK_SECONDS,
            FAST_RELEASE_SECONDS,
            duration,
        );
        self.slow_envelope = follow(
            self.slow_envelope,
            rms,
            SLOW_ATTACK_SECONDS,
            SLOW_RELEASE_SECONDS,
            duration,
        );
        let delta = (self.fast_envelope - self.slow_envelope).max(0.0);
        self.noise_floor = follow(
            self.noise_floor,
            delta,
            NOISE_FLOOR_SECONDS,
            NOISE_FLOOR_SECONDS,
            duration,
        );

        let level_target = finite_clamped(self.fast_envelope.sqrt());
        self.snapshot.level = finite_clamped(follow(
            self.snapshot.level,
            level_target,
            LEVEL_ATTACK_SECONDS,
            LEVEL_RELEASE_SECONDS,
            duration,
        ));

        self.time_since_beat = (self.time_since_beat + duration).min(10.0);
        self.snapshot.beat =
            finite_clamped(self.snapshot.beat * decay_factor(duration, BEAT_DECAY_SECONDS));
        if delta < self.previous_delta {
            self.beat_armed = true;
        }
        let threshold = TRANSIENT_MULTIPLIER * (self.noise_floor + TRANSIENT_EPSILON);
        let transient = finite_clamped(delta / threshold);
        let rising = delta > self.previous_delta;
        if self.beat_armed
            && rising
            && transient >= 1.0
            && self.time_since_beat >= MIN_BEAT_GAP_SECONDS
        {
            self.snapshot.beat = transient;
            self.time_since_beat = 0.0;
            self.beat_armed = false;
        }
        self.previous_delta = delta;
    }

    pub(crate) fn reset(&mut self) {
        *self = Self::default();
    }

    pub(crate) fn snapshot(&self) -> AudioReactiveSnapshot {
        self.snapshot
    }
}

fn follow(current: f32, target: f32, attack: f32, release: f32, duration: f32) -> f32 {
    let current = finite_clamped(current);
    let target = finite_clamped(target);
    let time_constant = if target >= current { attack } else { release };
    let coefficient = 1.0 - decay_factor(duration, time_constant);
    finite_clamped(current + (target - current) * coefficient)
}

fn decay_factor(duration: f32, time_constant: f32) -> f32 {
    if !duration.is_finite() || !time_constant.is_finite() || time_constant <= 0.0 {
        return 0.0;
    }
    (-duration / time_constant).exp().clamp(0.0, 1.0)
}

fn finite_clamped(value: f32) -> f32 {
    if value.is_finite() {
        value.clamp(0.0, 1.0)
    } else {
        0.0
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const SAMPLE_RATE: u32 = 48_000;
    const BLOCK_FRAMES: usize = 480;

    fn block(analyzer: &mut AudioReactiveAnalyzer, rms: f32) {
        analyzer.process_block(
            rms * rms * BLOCK_FRAMES as f32 * 2.0,
            BLOCK_FRAMES,
            2,
            SAMPLE_RATE,
        );
    }

    fn settle(analyzer: &mut AudioReactiveAnalyzer, rms: f32, blocks: usize) {
        for _ in 0..blocks {
            block(analyzer, rms);
        }
    }

    #[test]
    fn silence_stays_zero() {
        let mut analyzer = AudioReactiveAnalyzer::default();
        settle(&mut analyzer, 0.0, 16);
        assert_eq!(analyzer.snapshot(), AudioReactiveSnapshot::default());
    }

    #[test]
    fn steady_tone_has_finite_nonzero_level() {
        let mut analyzer = AudioReactiveAnalyzer::default();
        settle(&mut analyzer, 0.25, 120);
        let snapshot = analyzer.snapshot();
        assert!(snapshot.level.is_finite());
        assert!(snapshot.beat.is_finite());
        assert!(snapshot.level > 0.0);
    }

    #[test]
    fn level_rises_and_releases() {
        let mut analyzer = AudioReactiveAnalyzer::default();
        settle(&mut analyzer, 0.0, 8);
        block(&mut analyzer, 0.8);
        let risen = analyzer.snapshot().level;
        settle(&mut analyzer, 0.0, 300);
        assert!(risen > 0.0);
        assert!(analyzer.snapshot().level < risen);
    }

    #[test]
    fn transient_triggers_beat_and_gap_blocks_retrigger() {
        let mut analyzer = AudioReactiveAnalyzer::default();
        settle(&mut analyzer, 0.0, 8);
        block(&mut analyzer, 0.25);
        let first_beat = analyzer.snapshot().beat;
        assert!(first_beat > 0.0);

        block(&mut analyzer, 0.0);
        assert!(analyzer.beat_armed);
        settle(&mut analyzer, 0.0, 3);
        block(&mut analyzer, 0.25);
        let beat_inside_gap = analyzer.snapshot().beat;
        assert!(beat_inside_gap < first_beat);
        settle(&mut analyzer, 0.0, 8);
        let beat_after_rearm = analyzer.snapshot().beat;
        block(&mut analyzer, 0.25);
        assert!(analyzer.snapshot().beat > beat_after_rearm);
    }

    #[test]
    fn repeated_transients_can_trigger_again_after_about_two_hundred_ms() {
        let mut analyzer = AudioReactiveAnalyzer::default();
        block(&mut analyzer, 0.25);
        let first_beat = analyzer.snapshot().beat;
        assert!(first_beat > 0.0);
        block(&mut analyzer, 0.25);
        settle(&mut analyzer, 0.0, 18);
        let beat_before_second = analyzer.snapshot().beat;
        block(&mut analyzer, 0.25);
        assert!(analyzer.snapshot().beat > beat_before_second);
    }

    #[test]
    fn steady_tone_onset_does_not_retrigger_periodically() {
        let mut analyzer = AudioReactiveAnalyzer::default();
        settle(&mut analyzer, 0.0, 8);
        block(&mut analyzer, 0.5);
        let onset_beat = analyzer.snapshot().beat;
        assert!(onset_beat > 0.0);
        settle(&mut analyzer, 0.5, 120);
        assert!(analyzer.snapshot().beat < onset_beat);
    }

    #[test]
    fn beat_decays_without_sleeping() {
        let mut analyzer = AudioReactiveAnalyzer::default();
        settle(&mut analyzer, 0.0, 8);
        block(&mut analyzer, 1.0);
        assert!(analyzer.snapshot().beat > 0.0);
        settle(&mut analyzer, 0.0, 120);
        assert!(analyzer.snapshot().beat < 0.01);
    }

    #[test]
    fn reset_clears_level_beat_and_history() {
        let mut analyzer = AudioReactiveAnalyzer::default();
        settle(&mut analyzer, 1.0, 8);
        assert!(analyzer.snapshot().level > 0.0);
        analyzer.reset();
        assert_eq!(analyzer.snapshot(), AudioReactiveSnapshot::default());
        block(&mut analyzer, 0.0);
        assert_eq!(analyzer.snapshot(), AudioReactiveSnapshot::default());
    }

    #[test]
    fn mono_and_stereo_rms_match() {
        let mut mono = AudioReactiveAnalyzer::default();
        let mut stereo = AudioReactiveAnalyzer::default();
        for _ in 0..120 {
            mono.process_block(
                0.25 * 0.25 * BLOCK_FRAMES as f32,
                BLOCK_FRAMES,
                1,
                SAMPLE_RATE,
            );
            stereo.process_block(
                0.25 * 0.25 * BLOCK_FRAMES as f32 * 2.0,
                BLOCK_FRAMES,
                2,
                SAMPLE_RATE,
            );
        }
        assert!((mono.snapshot().level - stereo.snapshot().level).abs() < 1.0e-6);
    }
}
