use std::{
    collections::BTreeMap,
    fs::File,
    io::{self, BufReader, Read, Seek, SeekFrom},
    path::PathBuf,
    str::FromStr,
    sync::{
        atomic::{AtomicBool, AtomicU64, Ordering},
        Arc, Mutex,
    },
    thread,
    time::Duration,
};

use bytes::Bytes;
use reqwest::{
    blocking::{Client, RequestBuilder},
    header::{HeaderMap, HeaderName, HeaderValue, CONTENT_LENGTH, CONTENT_RANGE, RANGE},
    StatusCode,
};
use rodio::{
    cpal::{
        self,
        traits::{DeviceTrait, HostTrait},
    },
    Decoder, DeviceSinkBuilder, MixerDeviceSink, Player, Source,
};

use super::{
    audio_dsp_bridge::{
        DspConfiguration, DspRuntimeBypassReason, DspRuntimeTelemetry, NativeDspRuntimeSnapshot,
    },
    desktop_dsp::{DesktopDspConfigInput, DesktopDspSource},
};
use audio_dsp::AudioDspConfig;

const HTTP_BLOCK_SIZE: u64 = 256 * 1024;
const HTTP_PREFETCH_BYTES: u64 = 8 * 1024 * 1024;
const HTTP_CACHE_MAX_BLOCKS: usize = 48;

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum DesktopRodioLoadResult {
    Ready,
    Unsupported,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct DesktopAudioOutputDevice {
    pub id: String,
    pub name: String,
    pub is_default: bool,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum DesktopAudioOutputSwitchResult {
    Ready,
    DeviceNotFound,
    OpenFailed,
    RestoreFailed,
}

#[derive(uniffi::Object)]
pub struct DesktopRodioPlayer {
    state: Mutex<DesktopRodioState>,
    completion: Arc<PlaybackCompletionState>,
    telemetry: Arc<DspRuntimeTelemetry>,
    output_backend: Arc<dyn AudioOutputBackend>,
}

#[derive(Default)]
struct DesktopRodioState {
    output: Option<RodioOutput>,
    active_device_id: Option<String>,
    loaded: bool,
    loaded_resource: Option<LoadedResource>,
    duration_ms: i64,
    dsp_config: AudioDspConfig,
    crossfade_duration_ms: u64,
    dsp_input: Option<DesktopDspConfigInput>,
}

#[derive(Clone)]
struct LoadedResource {
    uri: String,
    http_header_fields: String,
}

#[derive(Default)]
struct PlaybackCompletionState {
    generation: AtomicU64,
    completed_generation: AtomicU64,
}

struct RodioOutput {
    sink: Option<MixerDeviceSink>,
    player: Arc<Player>,
    telemetry: Arc<DspRuntimeTelemetry>,
}

trait AudioOutputBackend: Send + Sync {
    fn list_devices(&self) -> Result<Vec<DesktopAudioOutputDevice>, String>;

    fn open_output(
        &self,
        device_id: Option<&str>,
        telemetry: Arc<DspRuntimeTelemetry>,
    ) -> Result<(RodioOutput, String), AudioOutputOpenError>;
}

struct CpalAudioOutputBackend;

#[uniffi::export]
impl DesktopRodioPlayer {
    pub fn load(&self, uri: String, http_header_fields: String) -> DesktopRodioLoadResult {
        if uri.trim().is_empty() {
            return DesktopRodioLoadResult::Unsupported;
        }

        let mut state = self.state.lock().unwrap();
        let dsp_config = state.dsp_config;
        let crossfade_duration_ms = state.crossfade_duration_ms;
        let was_loaded = state.loaded;
        let output = match state.ensure_output(self.output_backend.as_ref(), self.telemetry.clone())
        {
            Ok(output) => output,
            Err(message) => {
                self.telemetry
                    .mark_bypassed(DspRuntimeBypassReason::OutputRouteUnavailable, 0);
                tracing::warn!(message, "desktop rodio output unavailable");
                return DesktopRodioLoadResult::Unsupported;
            }
        };

        let old_player = output.replace_player();
        let (duration_ms, dsp_input) =
            match output.load_resource(&uri, &http_header_fields, dsp_config) {
                Ok(result) => result,
                Err(message) => {
                    self.telemetry
                        .mark_bypassed(DspRuntimeBypassReason::PlatformProcessingUnavailable, 0);
                    output.player.stop();
                    output.player = old_player;
                    tracing::warn!(message, "desktop rodio failed to decode resource");
                    return DesktopRodioLoadResult::Unsupported;
                }
            };
        let target_gain = 1.0;
        let should_crossfade = was_loaded && !old_player.is_paused() && crossfade_duration_ms > 0;
        if should_crossfade {
            output.player.set_volume(0.0);
            output.player.play();
            fade_between_players(
                old_player,
                output.player.clone(),
                target_gain,
                crossfade_duration_ms,
            );
        } else {
            old_player.stop();
            output.player.set_volume(target_gain);
            output.player.pause();
        }
        let player = output.player.clone();
        state.loaded = true;
        state.loaded_resource = Some(LoadedResource {
            uri,
            http_header_fields,
        });
        state.duration_ms = duration_ms;
        state.dsp_input = Some(dsp_input);
        let generation = self.completion.generation.fetch_add(1, Ordering::AcqRel) + 1;
        self.completion
            .completed_generation
            .store(0, Ordering::Release);
        observe_playback_completion(player, self.completion.clone(), generation);
        DesktopRodioLoadResult::Ready
    }

    pub fn play(&self) {
        self.with_loaded_player(|player| player.play());
    }

    pub fn pause(&self) {
        self.with_loaded_player(|player| player.pause());
    }

    pub fn stop(&self) {
        self.completion.generation.fetch_add(1, Ordering::AcqRel);
        self.completion
            .completed_generation
            .store(0, Ordering::Release);
        let mut state = self.state.lock().unwrap();
        if let Some(output) = state.output.as_ref() {
            output.player.stop();
        }
        state.loaded = false;
        state.loaded_resource = None;
        state.duration_ms = 0;
        state.dsp_input = None;
        self.telemetry.reset();
    }

    pub fn seek(&self, ms: u64) {
        self.with_loaded_player(|player| {
            let _ = player.try_seek(Duration::from_millis(ms));
        });
    }

    pub fn current_position_ms(&self) -> i64 {
        let state = self.state.lock().unwrap();
        if !state.loaded {
            return 0;
        }
        state
            .output
            .as_ref()
            .map(|output| duration_to_ms(output.player.get_pos()))
            .unwrap_or(0)
    }

    pub fn buffered_position_ms(&self) -> i64 {
        self.current_position_ms()
    }

    pub fn duration_ms(&self) -> i64 {
        let state = self.state.lock().unwrap();
        if state.loaded {
            state.duration_ms
        } else {
            0
        }
    }

    pub fn take_playback_completed(&self) -> bool {
        let generation = self.completion.generation.load(Ordering::Acquire);
        generation != 0
            && self
                .completion
                .completed_generation
                .swap(0, Ordering::AcqRel)
                == generation
    }

    pub fn list_audio_output_devices(&self) -> Vec<DesktopAudioOutputDevice> {
        match self.output_backend.list_devices() {
            Ok(devices) => devices,
            Err(message) => {
                tracing::warn!(message, "desktop audio output enumeration failed");
                Vec::new()
            }
        }
    }

    pub fn current_audio_output_device(&self) -> Option<DesktopAudioOutputDevice> {
        let active_device_id = self.state.lock().unwrap().active_device_id.clone();
        let devices = self.list_audio_output_devices();
        active_device_id
            .as_ref()
            .and_then(|id| devices.iter().find(|device| &device.id == id).cloned())
            .or_else(|| devices.into_iter().find(|device| device.is_default))
    }

    pub fn refresh_audio_output_devices(&self) -> Vec<DesktopAudioOutputDevice> {
        let devices = self.list_audio_output_devices();
        let active_device_id = self.state.lock().unwrap().active_device_id.clone();
        if active_device_id
            .as_ref()
            .is_some_and(|id| devices.iter().all(|device| &device.id != id))
        {
            let _ = self.set_audio_output_device(None);
            return self.list_audio_output_devices();
        }
        devices
    }

    pub fn set_audio_output_device(
        &self,
        device_id: Option<String>,
    ) -> DesktopAudioOutputSwitchResult {
        let requested_device_id = device_id.filter(|id| !id.trim().is_empty());
        let (next_output, active_device_id) = match self
            .output_backend
            .open_output(requested_device_id.as_deref(), self.telemetry.clone())
        {
            Ok(output) => output,
            Err(AudioOutputOpenError::DeviceNotFound(message)) => {
                tracing::warn!(message, "desktop audio output device was not found");
                return DesktopAudioOutputSwitchResult::DeviceNotFound;
            }
            Err(AudioOutputOpenError::OpenFailed(message)) => {
                tracing::warn!(message, "desktop audio output device could not be opened");
                return DesktopAudioOutputSwitchResult::OpenFailed;
            }
        };

        let mut state = self.state.lock().unwrap();
        let restored = if state.loaded {
            let Some(resource) = state.loaded_resource.clone() else {
                return DesktopAudioOutputSwitchResult::RestoreFailed;
            };
            let Some(current_output) = state.output.as_ref() else {
                return DesktopAudioOutputSwitchResult::RestoreFailed;
            };
            let restore_snapshot = PlaybackRestoreSnapshot::capture(&current_output.player);
            let (duration_ms, dsp_input) = match next_output.load_resource(
                &resource.uri,
                &resource.http_header_fields,
                state.dsp_config,
            ) {
                Ok(result) => result,
                Err(message) => {
                    tracing::warn!(message, "desktop audio output playback restore failed");
                    return DesktopAudioOutputSwitchResult::RestoreFailed;
                }
            };
            if let Err(error) = next_output.player.try_seek(restore_snapshot.position) {
                tracing::warn!(%error, "desktop audio output seek restore failed");
                return DesktopAudioOutputSwitchResult::RestoreFailed;
            }
            restore_snapshot.apply_controls(&next_output.player);
            Some((duration_ms, dsp_input, next_output.player.clone()))
        } else {
            None
        };

        if let Some(previous_output) = state.output.replace(next_output) {
            previous_output.player.stop();
        }
        state.active_device_id = Some(active_device_id);
        if let Some((duration_ms, dsp_input, player)) = restored {
            state.duration_ms = duration_ms;
            state.dsp_input = Some(dsp_input);
            let generation = self.completion.generation.fetch_add(1, Ordering::AcqRel) + 1;
            self.completion
                .completed_generation
                .store(0, Ordering::Release);
            observe_playback_completion(player, self.completion.clone(), generation);
        }
        DesktopAudioOutputSwitchResult::Ready
    }

    pub fn configure_dsp(&self, config: DspConfiguration, crossfade_duration_ms: u64) {
        let mut state = self.state.lock().unwrap();
        let dsp_config = config.into_core();
        if let Some(input) = state.dsp_input.as_ref() {
            input.publish(dsp_config);
        }
        state.dsp_config = dsp_config;
        state.crossfade_duration_ms = crossfade_duration_ms.min(30_000);
    }

    pub fn runtime_snapshot(&self) -> NativeDspRuntimeSnapshot {
        self.telemetry.snapshot()
    }
}

impl DesktopRodioPlayer {
    fn new() -> Self {
        Self::new_with_output_backend(Arc::new(CpalAudioOutputBackend))
    }

    fn new_with_output_backend(output_backend: Arc<dyn AudioOutputBackend>) -> Self {
        let telemetry = Arc::new(DspRuntimeTelemetry::default());
        Self {
            state: Mutex::new(DesktopRodioState::default()),
            completion: Arc::new(PlaybackCompletionState::default()),
            telemetry,
            output_backend,
        }
    }

    fn with_loaded_player(&self, block: impl FnOnce(&Player)) {
        let state = self.state.lock().unwrap();
        if !state.loaded {
            return;
        }
        if let Some(output) = state.output.as_ref() {
            block(&output.player);
        }
    }
}

fn observe_playback_completion(
    player: Arc<Player>,
    completion: Arc<PlaybackCompletionState>,
    generation: u64,
) {
    thread::spawn(move || {
        player.sleep_until_end();
        if completion.generation.load(Ordering::Acquire) == generation {
            completion
                .completed_generation
                .store(generation, Ordering::Release);
        }
    });
}

impl DesktopRodioState {
    fn ensure_output(
        &mut self,
        output_backend: &dyn AudioOutputBackend,
        telemetry: Arc<DspRuntimeTelemetry>,
    ) -> Result<&mut RodioOutput, String> {
        if self.output.is_none() {
            let (output, active_device_id) = output_backend
                .open_output(None, telemetry)
                .map_err(AudioOutputOpenError::into_message)?;
            self.output = Some(output);
            self.active_device_id = Some(active_device_id);
        }
        Ok(self.output.as_mut().unwrap())
    }
}

impl RodioOutput {
    fn new_for_device(
        device_id: Option<&str>,
        telemetry: Arc<DspRuntimeTelemetry>,
    ) -> Result<(Self, String), AudioOutputOpenError> {
        let device = resolve_audio_output_device(device_id)?;
        let active_device_id = device.id().map_err(|error| {
            AudioOutputOpenError::OpenFailed(format!("read audio device id failed: {error}"))
        })?;
        let mut sink = DeviceSinkBuilder::from_device(device)
            .and_then(|builder| builder.open_sink_or_fallback())
            .map_err(|error| {
                AudioOutputOpenError::OpenFailed(format!("open audio sink failed: {error}"))
            })?;
        sink.log_on_drop(false);
        let player = Arc::new(Player::connect_new(sink.mixer()));
        Ok((
            Self {
                sink: Some(sink),
                player,
                telemetry,
            },
            active_device_id.to_string(),
        ))
    }

    fn load_resource(
        &self,
        uri: &str,
        http_header_fields: &str,
        dsp_config: AudioDspConfig,
    ) -> Result<(i64, DesktopDspConfigInput), String> {
        if is_http_uri(uri) {
            self.load_http_resource(uri, http_header_fields, dsp_config)
        } else {
            self.load_file_resource(uri, dsp_config)
        }
    }

    fn replace_player(&mut self) -> Arc<Player> {
        let next = self
            .sink
            .as_ref()
            .map(|sink| Arc::new(Player::connect_new(sink.mixer())))
            .unwrap_or_else(|| Arc::new(Player::new().0));
        std::mem::replace(&mut self.player, next)
    }

    #[cfg(test)]
    fn new_for_test(telemetry: Arc<DspRuntimeTelemetry>) -> Self {
        Self {
            sink: None,
            player: Arc::new(Player::new().0),
            telemetry,
        }
    }

    fn load_file_resource(
        &self,
        uri: &str,
        dsp_config: AudioDspConfig,
    ) -> Result<(i64, DesktopDspConfigInput), String> {
        let path = uri_to_path(uri);
        let file = File::open(&path).map_err(|error| format!("open file failed: {error}"))?;
        let byte_len = file
            .metadata()
            .map_err(|error| format!("read file metadata failed: {error}"))?
            .len();
        let reader = BufReader::new(file);
        let mut builder = Decoder::builder()
            .with_data(reader)
            .with_byte_len(byte_len)
            .with_seekable(true);
        if let Some(hint) = extension_hint(uri) {
            builder = builder.with_hint(hint);
        }
        let source = builder.build().map_err(|error| error.to_string())?;
        let duration_ms = source
            .total_duration()
            .map(duration_to_ms)
            .unwrap_or_default();
        let input = self.append_source(source, dsp_config);
        Ok((duration_ms, input))
    }

    fn load_http_resource(
        &self,
        uri: &str,
        http_header_fields: &str,
        dsp_config: AudioDspConfig,
    ) -> Result<(i64, DesktopDspConfigInput), String> {
        let reader = BufReader::new(HttpRangeReader::open(uri, http_header_fields)?);
        let byte_len = reader.get_ref().len();
        let mut builder = Decoder::builder()
            .with_data(reader)
            .with_byte_len(byte_len)
            .with_seekable(true);
        if let Some(hint) = extension_hint(uri) {
            builder = builder.with_hint(hint);
        }
        let source = builder.build().map_err(|error| error.to_string())?;
        let duration_ms = source
            .total_duration()
            .map(duration_to_ms)
            .unwrap_or_default();
        let input = self.append_source(source, dsp_config);
        Ok((duration_ms, input))
    }

    fn append_source<S>(&self, source: S, dsp_config: AudioDspConfig) -> DesktopDspConfigInput
    where
        S: Source + Send + 'static,
    {
        let (source, input) =
            DesktopDspSource::new_with_telemetry(source, dsp_config, self.telemetry.clone());
        self.player.append(source);
        input
    }
}

enum AudioOutputOpenError {
    DeviceNotFound(String),
    OpenFailed(String),
}

#[derive(Debug, Clone, Copy, PartialEq)]
struct PlaybackRestoreSnapshot {
    position: Duration,
    was_paused: bool,
    volume: f32,
}

impl PlaybackRestoreSnapshot {
    fn capture(player: &Player) -> Self {
        Self {
            position: player.get_pos(),
            was_paused: player.is_paused(),
            volume: player.volume(),
        }
    }

    fn apply_controls(self, player: &Player) {
        player.set_volume(self.volume);
        if self.was_paused {
            player.pause();
        } else {
            player.play();
        }
    }
}

impl AudioOutputOpenError {
    fn into_message(self) -> String {
        match self {
            Self::DeviceNotFound(message) | Self::OpenFailed(message) => message,
        }
    }
}

fn resolve_audio_output_device(
    device_id: Option<&str>,
) -> Result<cpal::Device, AudioOutputOpenError> {
    let host = cpal::default_host();
    let Some(device_id) = device_id else {
        return host.default_output_device().ok_or_else(|| {
            AudioOutputOpenError::DeviceNotFound(
                "system default audio output device is unavailable".to_string(),
            )
        });
    };
    let parsed_id = cpal::DeviceId::from_str(device_id).map_err(|error| {
        AudioOutputOpenError::DeviceNotFound(format!(
            "invalid audio output device id '{device_id}': {error}"
        ))
    })?;
    host.device_by_id(&parsed_id).ok_or_else(|| {
        AudioOutputOpenError::DeviceNotFound(format!(
            "audio output device '{device_id}' is unavailable"
        ))
    })
}

fn system_audio_output_devices() -> Result<Vec<DesktopAudioOutputDevice>, String> {
    let host = cpal::default_host();
    let default_device_id = host
        .default_output_device()
        .and_then(|device| device.id().ok())
        .map(|id| id.to_string());
    let devices = host
        .output_devices()
        .map_err(|error| format!("list audio output devices failed: {error}"))?;
    Ok(devices
        .filter_map(|device| {
            let id = device.id().ok()?.to_string();
            let name = device
                .description()
                .ok()
                .map(|description| description.name().trim().to_string())
                .filter(|name| !name.is_empty())
                .unwrap_or_else(|| id.clone());
            Some(describe_audio_output_device(
                id,
                name,
                default_device_id.as_deref(),
            ))
        })
        .collect())
}

impl AudioOutputBackend for CpalAudioOutputBackend {
    fn list_devices(&self) -> Result<Vec<DesktopAudioOutputDevice>, String> {
        system_audio_output_devices()
    }

    fn open_output(
        &self,
        device_id: Option<&str>,
        telemetry: Arc<DspRuntimeTelemetry>,
    ) -> Result<(RodioOutput, String), AudioOutputOpenError> {
        RodioOutput::new_for_device(device_id, telemetry)
    }
}

fn describe_audio_output_device(
    id: String,
    name: String,
    default_device_id: Option<&str>,
) -> DesktopAudioOutputDevice {
    DesktopAudioOutputDevice {
        is_default: default_device_id == Some(id.as_str()),
        id,
        name,
    }
}

#[uniffi::export]
pub fn ct_create_desktop_rodio_player() -> Arc<DesktopRodioPlayer> {
    Arc::new(DesktopRodioPlayer::new())
}

fn fade_between_players(
    previous: Arc<Player>,
    next: Arc<Player>,
    target_gain: f32,
    duration_ms: u64,
) {
    thread::spawn(move || {
        let previous_gain = previous.volume();
        let steps = (duration_ms / 20).max(1);
        let sleep = Duration::from_millis((duration_ms / steps).max(1));
        for step in 1..=steps {
            let progress = step as f32 / steps as f32;
            previous.set_volume(previous_gain * (1.0 - progress));
            next.set_volume(target_gain * progress);
            thread::sleep(sleep);
        }
        previous.stop();
        next.set_volume(target_gain);
    });
}

struct HttpRangeReader {
    shared: Arc<HttpRangeShared>,
    position: u64,
}

struct HttpRangeShared {
    client: Client,
    uri: String,
    headers: HeaderMap,
    len: u64,
    read_position: AtomicU64,
    stop_prefetch: AtomicBool,
    cache: Mutex<BTreeMap<u64, Arc<Bytes>>>,
}

struct HttpRangeBlock {
    start: u64,
    bytes: Bytes,
}

impl Drop for HttpRangeReader {
    fn drop(&mut self) {
        self.shared.stop_prefetch.store(true, Ordering::Relaxed);
    }
}

impl HttpRangeReader {
    fn open(uri: &str, http_header_fields: &str) -> Result<Self, String> {
        let client = Client::builder()
            .no_proxy()
            .build()
            .map_err(|error| format!("failed to create http client: {error}"))?;
        let headers = header_map_from_fields(http_header_fields)?;
        let len = probe_http_len(&client, uri, &headers)?;
        if len == 0 {
            return Err("http resource is empty".to_string());
        }

        let shared = Arc::new(HttpRangeShared {
            client,
            uri: uri.to_string(),
            headers,
            len,
            read_position: AtomicU64::new(0),
            stop_prefetch: AtomicBool::new(false),
            cache: Mutex::new(BTreeMap::new()),
        });
        shared
            .fetch_block(0)
            .map(|block| shared.insert_block(block))
            .map_err(|error| error.to_string())?;
        thread::spawn({
            let shared = shared.clone();
            move || shared.prefetch()
        });

        Ok(Self {
            shared,
            position: 0,
        })
    }

    fn len(&self) -> u64 {
        self.shared.len
    }
}

impl HttpRangeShared {
    fn read_block(&self, position: u64) -> io::Result<(u64, Arc<Bytes>)> {
        if let Some(block) = self.cached_block(position) {
            return Ok(block);
        }

        tracing::warn!(position, "desktop rodio http reader synchronous cache miss");
        let block = self.fetch_block(block_start(position))?;
        let start = block.start;
        let bytes = Arc::new(block.bytes);
        self.cache.lock().unwrap().insert(start, bytes.clone());
        self.prune_cache(position);
        Ok((start, bytes))
    }

    fn cached_block(&self, position: u64) -> Option<(u64, Arc<Bytes>)> {
        let cache = self.cache.lock().unwrap();
        let (start, bytes) = cache.range(..=position).next_back()?;
        let end = start.checked_add(bytes.len() as u64)?;
        if position < end {
            Some((*start, bytes.clone()))
        } else {
            None
        }
    }

    fn has_cached_position(&self, position: u64) -> bool {
        self.cached_block(position).is_some()
    }

    fn insert_block(&self, block: HttpRangeBlock) {
        self.cache
            .lock()
            .unwrap()
            .insert(block.start, Arc::new(block.bytes));
    }

    fn fetch_block(&self, start: u64) -> io::Result<HttpRangeBlock> {
        let end = start
            .saturating_add(HTTP_BLOCK_SIZE - 1)
            .min(self.len.saturating_sub(1));
        if start > end {
            return Ok(HttpRangeBlock {
                start,
                bytes: Bytes::new(),
            });
        }

        let mut request = self
            .client
            .get(&self.uri)
            .header(RANGE, format!("bytes={start}-{end}"));
        request = apply_headers(request, &self.headers);
        let response = request
            .send()
            .and_then(|response| response.error_for_status())
            .map_err(to_io_error)?;
        let response_start = if response.status() == StatusCode::PARTIAL_CONTENT {
            response
                .headers()
                .get(CONTENT_RANGE)
                .and_then(|value| value.to_str().ok())
                .and_then(parse_content_range_start)
                .unwrap_or(start)
        } else {
            0
        };
        let bytes = response.bytes().map_err(to_io_error)?;
        if bytes.is_empty() && start < self.len {
            return Err(io::Error::new(
                io::ErrorKind::UnexpectedEof,
                "http range response was empty",
            ));
        }
        Ok(HttpRangeBlock {
            start: response_start,
            bytes,
        })
    }

    fn prefetch(self: Arc<Self>) {
        while !self.stop_prefetch.load(Ordering::Relaxed) {
            let position = self.read_position.load(Ordering::Relaxed).min(self.len);
            let mut start = block_start(position);
            let end = position.saturating_add(HTTP_PREFETCH_BYTES).min(self.len);

            while start < end && !self.stop_prefetch.load(Ordering::Relaxed) {
                if !self.has_cached_position(start) {
                    match self.fetch_block(start) {
                        Ok(block) => self.insert_block(block),
                        Err(error) => {
                            tracing::warn!(%error, start, "desktop rodio http prefetch failed");
                            break;
                        }
                    }
                }
                start = start.saturating_add(HTTP_BLOCK_SIZE);
            }

            self.prune_cache(position);
            thread::sleep(Duration::from_millis(50));
        }
    }

    fn prune_cache(&self, position: u64) {
        let min_start = block_start(position.saturating_sub(HTTP_PREFETCH_BYTES / 2));
        let max_start = position.saturating_add(HTTP_PREFETCH_BYTES);
        let mut cache = self.cache.lock().unwrap();
        cache.retain(|start, bytes| {
            let block_end = start.saturating_add(bytes.len() as u64);
            block_end >= min_start && *start <= max_start
        });

        while cache.len() > HTTP_CACHE_MAX_BLOCKS {
            let Some(first_key) = cache.keys().next().copied() else {
                break;
            };
            cache.remove(&first_key);
        }
    }
}

impl Read for HttpRangeReader {
    fn read(&mut self, buf: &mut [u8]) -> io::Result<usize> {
        if buf.is_empty() || self.position >= self.shared.len {
            return Ok(0);
        }
        let (cache_start, bytes) = self.shared.read_block(self.position)?;
        let offset = (self.position - cache_start) as usize;
        let slice = &bytes[offset..];
        let remaining_len = (self.shared.len - self.position).min(usize::MAX as u64) as usize;
        let count = slice.len().min(buf.len()).min(remaining_len);
        buf[..count].copy_from_slice(&slice[..count]);
        self.position += count as u64;
        self.shared
            .read_position
            .store(self.position, Ordering::Relaxed);
        Ok(count)
    }
}

impl Seek for HttpRangeReader {
    fn seek(&mut self, pos: SeekFrom) -> io::Result<u64> {
        let target = match pos {
            SeekFrom::Start(offset) => offset as i128,
            SeekFrom::Current(offset) => self.position as i128 + offset as i128,
            SeekFrom::End(offset) => self.shared.len as i128 + offset as i128,
        };
        if target < 0 {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "cannot seek before start",
            ));
        }
        self.position = target as u64;
        self.shared
            .read_position
            .store(self.position, Ordering::Relaxed);
        Ok(self.position)
    }
}

fn block_start(position: u64) -> u64 {
    position / HTTP_BLOCK_SIZE * HTTP_BLOCK_SIZE
}

fn uri_to_path(uri: &str) -> PathBuf {
    uri.strip_prefix("file://")
        .map(path_url_to_path)
        .unwrap_or_else(|| PathBuf::from(uri))
}

fn path_url_to_path(path: &str) -> PathBuf {
    PathBuf::from(
        urlencoding::decode(path)
            .unwrap_or_else(|_| path.into())
            .into_owned(),
    )
}

fn is_http_uri(uri: &str) -> bool {
    uri.starts_with("http://") || uri.starts_with("https://")
}

fn extension_hint(uri: &str) -> Option<&str> {
    let path = uri.split_once('?').map(|(path, _)| path).unwrap_or(uri);
    let file_name = path.rsplit('/').next().unwrap_or(path);
    file_name.rsplit_once('.').map(|(_, extension)| extension)
}

fn parse_header_fields(http_header_fields: &str) -> impl Iterator<Item = (&str, &str)> {
    http_header_fields.lines().filter_map(|line| {
        let (name, value) = line.split_once(':')?;
        let name = name.trim();
        let value = value.trim();
        if name.is_empty() || value.is_empty() {
            None
        } else {
            Some((name, value))
        }
    })
}

fn header_map_from_fields(http_header_fields: &str) -> Result<HeaderMap, String> {
    let mut headers = HeaderMap::new();
    for (name, value) in parse_header_fields(http_header_fields) {
        let name = HeaderName::from_bytes(name.as_bytes())
            .map_err(|error| format!("invalid http header name '{name}': {error}"))?;
        let value = HeaderValue::from_str(value)
            .map_err(|error| format!("invalid http header value for '{name}': {error}"))?;
        headers.insert(name, value);
    }
    Ok(headers)
}

fn probe_http_len(client: &Client, uri: &str, headers: &HeaderMap) -> Result<u64, String> {
    let head_result = apply_headers(client.head(uri), headers)
        .send()
        .and_then(|response| response.error_for_status());
    if let Ok(response) = head_result {
        if let Some(len) = content_length(response.headers()) {
            return Ok(len);
        }
    }

    let response = apply_headers(client.get(uri).header(RANGE, "bytes=0-0"), headers)
        .send()
        .and_then(|response| response.error_for_status())
        .map_err(|error| format!("request failed: {error}"))?;
    content_range_total(response.headers())
        .or_else(|| content_length(response.headers()))
        .ok_or_else(|| "http resource length unavailable".to_string())
}

fn apply_headers(mut request: RequestBuilder, headers: &HeaderMap) -> RequestBuilder {
    for (name, value) in headers {
        request = request.header(name, value);
    }
    request
}

fn content_length(headers: &HeaderMap) -> Option<u64> {
    headers
        .get(CONTENT_LENGTH)?
        .to_str()
        .ok()?
        .parse::<u64>()
        .ok()
}

fn content_range_total(headers: &HeaderMap) -> Option<u64> {
    headers
        .get(CONTENT_RANGE)?
        .to_str()
        .ok()
        .and_then(parse_content_range_total)
}

fn parse_content_range_total(value: &str) -> Option<u64> {
    let (_, total) = value.rsplit_once('/')?;
    total.parse::<u64>().ok()
}

fn parse_content_range_start(value: &str) -> Option<u64> {
    let range = value.strip_prefix("bytes ")?;
    let (start, _) = range.split_once('-')?;
    start.parse::<u64>().ok()
}

fn duration_to_ms(duration: Duration) -> i64 {
    duration.as_millis().min(i64::MAX as u128) as i64
}

fn to_io_error(error: reqwest::Error) -> io::Error {
    io::Error::other(error)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::{io::Write, net::TcpListener, thread};

    struct FakeAudioOutputBackend {
        devices: Vec<DesktopAudioOutputDevice>,
        open_requests: Mutex<Vec<Option<String>>>,
    }

    impl FakeAudioOutputBackend {
        fn new() -> Self {
            Self {
                devices: vec![
                    DesktopAudioOutputDevice {
                        id: "fake:built-in".to_string(),
                        name: "Speakers".to_string(),
                        is_default: true,
                    },
                    DesktopAudioOutputDevice {
                        id: "fake:usb".to_string(),
                        name: "USB DAC".to_string(),
                        is_default: false,
                    },
                ],
                open_requests: Mutex::new(Vec::new()),
            }
        }
    }

    impl AudioOutputBackend for FakeAudioOutputBackend {
        fn list_devices(&self) -> Result<Vec<DesktopAudioOutputDevice>, String> {
            Ok(self.devices.clone())
        }

        fn open_output(
            &self,
            device_id: Option<&str>,
            telemetry: Arc<DspRuntimeTelemetry>,
        ) -> Result<(RodioOutput, String), AudioOutputOpenError> {
            self.open_requests
                .lock()
                .unwrap()
                .push(device_id.map(str::to_string));
            let resolved_id = device_id.unwrap_or("fake:built-in");
            if resolved_id == "fake:missing" {
                return Err(AudioOutputOpenError::DeviceNotFound(
                    "fake device missing".to_string(),
                ));
            }
            if resolved_id == "fake:busy" {
                return Err(AudioOutputOpenError::OpenFailed(
                    "fake device busy".to_string(),
                ));
            }
            Ok((
                RodioOutput::new_for_test(telemetry),
                resolved_id.to_string(),
            ))
        }
    }

    #[test]
    fn empty_uri_is_unsupported_without_opening_output() {
        let player = DesktopRodioPlayer::new();

        assert_eq!(
            DesktopRodioLoadResult::Unsupported,
            player.load("".to_string(), "".to_string())
        );
        player.play();
        player.pause();
        player.seek(1_000);
        player.stop();
        assert_eq!(0, player.current_position_ms());
        assert_eq!(0, player.buffered_position_ms());
        assert_eq!(0, player.duration_ms());
    }

    #[test]
    fn playback_completion_event_is_consumed_once() {
        let player = DesktopRodioPlayer::new();
        player.completion.generation.store(1, Ordering::Release);
        player
            .completion
            .completed_generation
            .store(1, Ordering::Release);

        assert!(player.take_playback_completed());
        assert!(!player.take_playback_completed());
    }

    #[test]
    fn device_descriptors_keep_cpal_ids_and_real_default() {
        let first = describe_audio_output_device(
            "coreaudio:first".to_string(),
            "Speakers".to_string(),
            Some("coreaudio:second"),
        );
        let second = describe_audio_output_device(
            "coreaudio:second".to_string(),
            "Speakers".to_string(),
            Some("coreaudio:second"),
        );

        assert_ne!(first.id, second.id);
        assert!(!first.is_default);
        assert!(second.is_default);
    }

    #[test]
    fn fake_backend_enumerates_default_and_switches_without_hardware() {
        let backend = Arc::new(FakeAudioOutputBackend::new());
        let player = DesktopRodioPlayer::new_with_output_backend(backend.clone());

        assert_eq!(backend.devices, player.list_audio_output_devices());
        assert_eq!(
            Some("fake:built-in"),
            player
                .current_audio_output_device()
                .as_ref()
                .map(|device| device.id.as_str())
        );
        assert_eq!(
            DesktopAudioOutputSwitchResult::Ready,
            player.set_audio_output_device(Some("fake:usb".to_string()))
        );
        assert_eq!(
            Some("fake:usb"),
            player
                .current_audio_output_device()
                .as_ref()
                .map(|device| device.id.as_str())
        );
        assert_eq!(
            vec![Some("fake:usb".to_string())],
            *backend.open_requests.lock().unwrap()
        );
    }

    #[test]
    fn switch_failure_does_not_change_player_state() {
        let backend = Arc::new(FakeAudioOutputBackend::new());
        let player = DesktopRodioPlayer::new_with_output_backend(backend);

        assert_eq!(
            DesktopAudioOutputSwitchResult::Ready,
            player.set_audio_output_device(Some("fake:usb".to_string()))
        );

        assert_eq!(
            DesktopAudioOutputSwitchResult::DeviceNotFound,
            player.set_audio_output_device(Some("fake:missing".to_string()))
        );
        let state = player.state.lock().unwrap();
        assert!(state.output.is_some());
        assert_eq!(Some("fake:usb"), state.active_device_id.as_deref());
        assert!(!state.loaded);
    }

    #[test]
    fn playback_restore_snapshot_preserves_pause_and_volume_controls() {
        let telemetry = Arc::new(DspRuntimeTelemetry::default());
        let previous = RodioOutput::new_for_test(telemetry.clone());
        previous.player.set_volume(0.37);
        previous.player.pause();
        let snapshot = PlaybackRestoreSnapshot::capture(&previous.player);
        let next = RodioOutput::new_for_test(telemetry);

        snapshot.apply_controls(&next.player);

        assert_eq!(Duration::ZERO, snapshot.position);
        assert!(next.player.is_paused());
        assert!((next.player.volume() - 0.37).abs() < f32::EPSILON);
    }

    #[test]
    fn parses_http_header_fields() {
        let headers = parse_header_fields("Authorization: Bearer token\n\nUser-Agent: TidePlayer")
            .collect::<Vec<_>>();

        assert_eq!(
            vec![
                ("Authorization", "Bearer token"),
                ("User-Agent", "TidePlayer")
            ],
            headers
        );
    }

    #[test]
    fn parses_content_range_headers() {
        assert_eq!(Some(123_456), parse_content_range_total("bytes 0-0/123456"));
        assert_eq!(Some(42), parse_content_range_start("bytes 42-99/123456"));
    }

    #[test]
    fn http_range_reader_reads_seekable_ranges() {
        let body = b"abcdefghijklmnopqrstuvwxyz".to_vec();
        let listener = TcpListener::bind(("127.0.0.1", 0)).unwrap();
        let uri = format!("http://{}/track.flac", listener.local_addr().unwrap());
        let server_body = body.clone();
        let server = thread::spawn(move || {
            for _ in 0..2 {
                let (mut stream, _) = listener.accept().unwrap();
                serve_range_request(&mut stream, &server_body);
            }
        });

        let mut reader =
            HttpRangeReader::open(&uri, "Authorization: Bearer token").expect("open range reader");
        assert_eq!(body.len() as u64, reader.len());

        let mut head = [0u8; 5];
        reader.read_exact(&mut head).unwrap();
        assert_eq!(b"abcde", &head);

        reader.seek(SeekFrom::Start(20)).unwrap();
        let mut tail = [0u8; 3];
        reader.read_exact(&mut tail).unwrap();
        assert_eq!(b"uvw", &tail);

        server.join().unwrap();
    }

    #[test]
    fn http_range_reader_feeds_rodio_decoder() {
        let body = test_wav_bytes();
        let listener = TcpListener::bind(("127.0.0.1", 0)).unwrap();
        let uri = format!("http://{}/track.wav", listener.local_addr().unwrap());
        let server_body = body.clone();
        let server = thread::spawn(move || {
            for _ in 0..2 {
                let (mut stream, _) = listener.accept().unwrap();
                serve_range_request(&mut stream, &server_body);
            }
        });

        let reader =
            BufReader::new(HttpRangeReader::open(&uri, "Authorization: Bearer token").unwrap());
        let mut source = Decoder::builder()
            .with_data(reader)
            .with_byte_len(body.len() as u64)
            .with_hint("wav")
            .build()
            .unwrap();

        assert!(source.next().is_some());
        server.join().unwrap();
    }

    fn serve_range_request(stream: &mut std::net::TcpStream, body: &[u8]) {
        let mut buffer = [0u8; 4096];
        let read = stream.read(&mut buffer).unwrap();
        let request = String::from_utf8_lossy(&buffer[..read]);
        assert!(request
            .to_ascii_lowercase()
            .contains("authorization: bearer token"));

        if request.starts_with("HEAD ") {
            write!(
                stream,
                "HTTP/1.1 200 OK\r\nAccept-Ranges: bytes\r\nContent-Length: {}\r\n\r\n",
                body.len()
            )
            .unwrap();
            return;
        }

        let (start, end) = request
            .lines()
            .find_map(|line| {
                line.to_ascii_lowercase()
                    .strip_prefix("range: bytes=")
                    .map(ToOwned::to_owned)
            })
            .as_deref()
            .and_then(parse_test_range)
            .unwrap();
        let end = end.min(body.len() - 1);
        let slice = &body[start..=end];
        write!(
            stream,
            "HTTP/1.1 206 Partial Content\r\nAccept-Ranges: bytes\r\nContent-Length: {}\r\nContent-Range: bytes {}-{}/{}\r\n\r\n",
            slice.len(),
            start,
            end,
            body.len()
        )
        .unwrap();
        stream.write_all(slice).unwrap();
    }

    fn parse_test_range(value: &str) -> Option<(usize, usize)> {
        let (start, end) = value.split_once('-')?;
        Some((start.parse().ok()?, end.parse().ok()?))
    }

    fn test_wav_bytes() -> Vec<u8> {
        let samples = [0i16, 1024, -1024, 0];
        let data_len = samples.len() as u32 * 2;
        let mut bytes = Vec::new();
        bytes.extend_from_slice(b"RIFF");
        bytes.extend_from_slice(&(36 + data_len).to_le_bytes());
        bytes.extend_from_slice(b"WAVEfmt ");
        bytes.extend_from_slice(&16u32.to_le_bytes());
        bytes.extend_from_slice(&1u16.to_le_bytes());
        bytes.extend_from_slice(&1u16.to_le_bytes());
        bytes.extend_from_slice(&44_100u32.to_le_bytes());
        bytes.extend_from_slice(&(44_100u32 * 2).to_le_bytes());
        bytes.extend_from_slice(&2u16.to_le_bytes());
        bytes.extend_from_slice(&16u16.to_le_bytes());
        bytes.extend_from_slice(b"data");
        bytes.extend_from_slice(&data_len.to_le_bytes());
        for sample in samples {
            bytes.extend_from_slice(&sample.to_le_bytes());
        }
        bytes
    }
}
