use crate::config::{db_to_linear, HeadroomConfig, HeadroomMode, MAX_CHANNELS};

#[derive(Debug)]
pub(crate) struct HeadroomManager {
    gain_current: f32,
    gain_target: f32,
    applied_db: f32,
}

impl Default for HeadroomManager {
    fn default() -> Self {
        Self {
            gain_current: 1.0,
            gain_target: 1.0,
            applied_db: 0.0,
        }
    }
}

impl HeadroomManager {
    pub(crate) fn configure(
        &mut self,
        config: HeadroomConfig,
        estimated_positive_response_db: f32,
        smooth: bool,
    ) {
        self.applied_db = match config.mode {
            HeadroomMode::Off => 0.0,
            HeadroomMode::Automatic => -estimated_positive_response_db.clamp(0.0, 24.0),
            HeadroomMode::Manual => config.manual_db.clamp(-24.0, 0.0),
        };
        self.gain_target = db_to_linear(self.applied_db);
        if !smooth {
            self.gain_current = self.gain_target;
        }
    }

    pub(crate) fn process_frame(&mut self, frame: &mut [f32; MAX_CHANNELS], channels: usize) {
        self.gain_current += (self.gain_target - self.gain_current) * 0.005;
        for sample in frame.iter_mut().take(channels) {
            *sample *= self.gain_current;
        }
    }

    pub(crate) fn reset(&mut self) {
        self.gain_current = self.gain_target;
    }

    pub(crate) fn applied_db(&self) -> f32 {
        self.applied_db
    }
}
