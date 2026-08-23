use std::{
    collections::{HashMap, HashSet},
    fs::{self, File, OpenOptions},
    io::{self, Read, Seek, SeekFrom, Write},
    net::{Ipv4Addr, SocketAddrV4, TcpListener},
    num::NonZeroUsize,
    path::{Path as FsPath, PathBuf},
    sync::{
        atomic::{AtomicBool, AtomicU64, Ordering},
        Arc, Mutex,
    },
};

use axum::{
    body::{boxed, Body},
    extract::{Path, State},
    http::{
        header::{ACCEPT_RANGES, CONTENT_LENGTH, CONTENT_RANGE, CONTENT_TYPE, RANGE},
        HeaderMap, HeaderValue, StatusCode,
    },
    response::{IntoResponse, Response},
    routing::get,
    Router,
};
use bytes::Bytes;
use futures_util::future::BoxFuture;
use lru::LruCache;
use rand::{rngs::OsRng, RngCore};
use sha2::{Digest, Sha256};
use storage_backend::{
    ByteRange, Entry, LocalBackend, RangeResponse, StorageBackend, StorageBackendError,
    StorageBackendResult, StreamFile,
};
use tokio::sync::oneshot;

use crate::error::{BError, BResult};

const BLOCK_SIZE: u64 = 256 * 1024;
const CACHE_BLOCKS: usize = 32;

#[derive(Debug, Clone, Copy, Default, uniffi::Record)]
pub struct PlaybackRangeStats {
    pub remote_requests: u64,
    pub remote_bytes: u64,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct PlaybackCacheOptions {
    pub directory: String,
    pub key: String,
    pub extension: String,
    pub write_enabled: bool,
    pub max_bytes: u64,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum PlaybackCachePromotionStatus {
    Partial,
    Promoted,
    AlreadyPromoted,
}

#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct PlaybackCachePromotionResult {
    pub status: PlaybackCachePromotionStatus,
    pub path: Option<String>,
    pub bytes: u64,
}

#[derive(uniffi::Object)]
pub struct PlaybackSession {
    url: String,
    source: Arc<PlaybackSource>,
    shutdown: Mutex<Option<oneshot::Sender<()>>>,
}

#[uniffi::export]
impl PlaybackSession {
    pub fn url(&self) -> String {
        self.url.clone()
    }

    pub fn content_type(&self) -> String {
        self.source.content_type.clone()
    }

    pub fn stats(&self) -> PlaybackRangeStats {
        PlaybackRangeStats {
            remote_requests: self.source.remote_requests.load(Ordering::Relaxed),
            remote_bytes: self.source.remote_bytes.load(Ordering::Relaxed),
        }
    }

    pub async fn prefetch_prefix(&self, max_bytes: u64) -> BResult<u64> {
        let target = max_bytes.min(self.source.total_size);
        let mut block_start = 0;
        while block_start < target {
            if !self.source.active.load(Ordering::Acquire) {
                return Err(BError::CustomError {
                    message: "playback session is inactive".to_string(),
                });
            }
            self.source
                .block(block_start)
                .await
                .map_err(|message| BError::CustomError { message })?;
            if !self.source.active.load(Ordering::Acquire) {
                return Err(BError::CustomError {
                    message: "playback session stopped during prefix prefetch".to_string(),
                });
            }
            block_start = block_start.saturating_add(BLOCK_SIZE);
        }
        Ok(target)
    }

    pub fn shutdown(&self) {
        self.close_internal();
    }
}

impl Drop for PlaybackSession {
    fn drop(&mut self) {
        self.close_internal();
    }
}

impl PlaybackSession {
    fn close_internal(&self) {
        if let Some(shutdown) = self.shutdown.lock().unwrap().take() {
            self.source.active.store(false, Ordering::Release);
            let _ = shutdown.send(());
            let source = self.source.clone();
            std::mem::drop(
                std::thread::Builder::new()
                    .name("playback-cleanup".into())
                    .spawn(move || {
                        let rt = tokio::runtime::Builder::new_current_thread()
                            .enable_io()
                            .build()
                            .expect("playback cleanup runtime");
                        rt.block_on(async move {
                            source.prune_persistent_cache();
                            if source.backend.release(source.path.clone()).await.is_err() {
                                tracing::debug!("failed to release playback source reader");
                            }
                        });
                    }),
            );
        }
    }
}

struct PlaybackSource {
    backend: Arc<dyn StorageBackend + Send + Sync>,
    path: String,
    total_size: u64,
    content_type: String,
    token: String,
    active: AtomicBool,
    cache: Mutex<LruCache<u64, Bytes>>,
    persistent_cache: Option<PersistentPlaybackCache>,
    remote_requests: AtomicU64,
    remote_bytes: AtomicU64,
}

impl PlaybackSource {
    async fn block(&self, block_start: u64) -> Result<Bytes, String> {
        if let Some(bytes) = self.cache.lock().unwrap().get(&block_start).cloned() {
            return Ok(bytes);
        }
        if let Some(bytes) = self
            .persistent_cache
            .as_ref()
            .and_then(|cache| cache.read_block(block_start))
        {
            self.cache.lock().unwrap().put(block_start, bytes.clone());
            return Ok(bytes);
        }

        let block_end = block_start
            .saturating_add(BLOCK_SIZE - 1)
            .min(self.total_size - 1);
        let response = self
            .backend
            .get_range_response(
                self.path.clone(),
                ByteRange::new(block_start, block_end).map_err(|error| error.to_string())?,
            )
            .await
            .map_err(|error| error.to_string())?;
        if response.total_size != self.total_size {
            return Err(format!(
                "remote size changed from {} to {}",
                self.total_size, response.total_size
            ));
        }
        self.remote_requests.fetch_add(1, Ordering::Relaxed);
        self.remote_bytes
            .fetch_add(response.bytes.len() as u64, Ordering::Relaxed);
        self.cache
            .lock()
            .unwrap()
            .put(block_start, response.bytes.clone());
        if let Some(cache) = &self.persistent_cache {
            cache.store_block(block_start, &response.bytes);
        }
        Ok(response.bytes)
    }

    fn prune_persistent_cache(&self) {
        if let Some(cache) = &self.persistent_cache {
            cache.prune();
        }
    }
}

struct PersistentPlaybackCache {
    complete_path: PathBuf,
    partial_path: PathBuf,
    index_path: PathBuf,
    total_size: u64,
    block_count: u64,
    max_bytes: u64,
    write_enabled: bool,
    cached_blocks: Mutex<HashSet<u64>>,
}

impl PersistentPlaybackCache {
    fn open(options: PlaybackCacheOptions, total_size: u64) -> Option<Self> {
        let (complete_path, partial_path, index_path) = cache_paths(&options)?;
        let write_enabled =
            options.write_enabled && options.max_bytes > 0 && total_size <= options.max_bytes;
        if write_enabled {
            if let Err(error) = fs::create_dir_all(&options.directory) {
                tracing::warn!(%error, "failed to create playback cache directory");
                return None;
            }
        } else if !complete_path.exists() && !partial_path.exists() {
            return None;
        }

        if complete_path
            .metadata()
            .ok()
            .is_some_and(|metadata| metadata.len() != total_size)
            && write_enabled
        {
            let _ = fs::remove_file(&complete_path);
        }

        let cached_blocks = load_cached_blocks(&index_path, total_size).unwrap_or_default();
        let cache = Self {
            complete_path,
            partial_path,
            index_path,
            total_size,
            block_count: total_size.div_ceil(BLOCK_SIZE),
            max_bytes: options.max_bytes,
            write_enabled,
            cached_blocks: Mutex::new(cached_blocks),
        };
        cache.prune();
        Some(cache)
    }

    fn read_block(&self, block_start: u64) -> Option<Bytes> {
        let expected_len = self.block_len(block_start)?;
        if self
            .complete_path
            .metadata()
            .ok()
            .is_some_and(|metadata| metadata.len() == self.total_size)
        {
            return read_file_range(&self.complete_path, block_start, expected_len);
        }
        if !self.cached_blocks.lock().unwrap().contains(&block_start) {
            return None;
        }
        read_file_range(&self.partial_path, block_start, expected_len)
    }

    fn store_block(&self, block_start: u64, bytes: &Bytes) {
        if !self.write_enabled {
            return;
        }
        let Some(expected_len) = self.block_len(block_start) else {
            return;
        };
        if bytes.len() != expected_len {
            tracing::debug!(
                block_start,
                expected = expected_len,
                actual = bytes.len(),
                "skipping incomplete playback cache block"
            );
            return;
        }

        let mut blocks = self.cached_blocks.lock().unwrap();
        if blocks.contains(&block_start) {
            if read_file_range(&self.partial_path, block_start, expected_len).is_some() {
                return;
            }
            blocks.remove(&block_start);
        }
        if let Err(error) = write_file_range(&self.partial_path, block_start, bytes) {
            tracing::warn!(%error, block_start, "failed to write playback cache block");
            return;
        }
        blocks.insert(block_start);
        if let Err(error) =
            persist_cached_blocks(&self.index_path, self.total_size, blocks.iter().copied())
        {
            tracing::warn!(%error, "failed to update playback cache index");
            return;
        }
        if blocks.len() as u64 != self.block_count {
            return;
        }

        if let Ok(file) = OpenOptions::new().write(true).open(&self.partial_path) {
            let _ = file.set_len(self.total_size);
        }
        if self.complete_path.exists() {
            let _ = fs::remove_file(&self.complete_path);
        }
        match fs::rename(&self.partial_path, &self.complete_path) {
            Ok(()) => {
                let _ = fs::remove_file(&self.index_path);
                tracing::info!(
                    path = %self.complete_path.display(),
                    bytes = self.total_size,
                    "playback cache completed"
                );
            }
            Err(error) => {
                tracing::warn!(%error, "failed to finalize playback cache");
            }
        }
    }

    fn block_len(&self, block_start: u64) -> Option<usize> {
        if block_start >= self.total_size || block_start % BLOCK_SIZE != 0 {
            return None;
        }
        Some((self.total_size - block_start).min(BLOCK_SIZE) as usize)
    }

    fn prune(&self) {
        prune_cache_directory(
            self.complete_path.parent().map(PathBuf::from),
            self.max_bytes,
            [&self.complete_path, &self.partial_path, &self.index_path],
        );
    }
}

fn cache_paths(options: &PlaybackCacheOptions) -> Option<(PathBuf, PathBuf, PathBuf)> {
    if options.directory.trim().is_empty() || options.key.trim().is_empty() {
        return None;
    }
    let hash = playback_cache_hash(&options.key);
    let extension = sanitized_extension(&options.extension);
    let complete_path = PathBuf::from(&options.directory).join(format!("{hash}.{extension}"));
    let partial_path = PathBuf::from(&options.directory).join(format!("{hash}.{extension}.part"));
    let index_path = PathBuf::from(&options.directory).join(format!("{hash}.{extension}.blocks"));
    Some((complete_path, partial_path, index_path))
}

fn playback_cache_hash(key: &str) -> String {
    Sha256::digest(key.as_bytes())
        .iter()
        .map(|byte| format!("{byte:02x}"))
        .collect()
}

pub fn promote_completed_playback_cache(
    options: PlaybackCacheOptions,
    destination_directory: String,
) -> io::Result<PlaybackCachePromotionResult> {
    let Some((complete_path, partial_path, index_path)) = cache_paths(&options) else {
        return Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            "playback cache identity is incomplete",
        ));
    };
    if destination_directory.trim().is_empty() {
        return Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            "playback cache promotion directory is empty",
        ));
    }
    let extension = sanitized_extension(&options.extension);
    let hash = playback_cache_hash(&options.key);
    let destination_directory = PathBuf::from(destination_directory);
    let destination = destination_directory.join(format!("{hash}.{extension}"));
    let marker = complete_path.with_extension(format!("{extension}.promoted"));

    if destination.is_file() {
        write_promotion_marker(&marker, &destination)?;
        let bytes = destination.metadata()?.len();
        return Ok(PlaybackCachePromotionResult {
            status: PlaybackCachePromotionStatus::AlreadyPromoted,
            path: Some(destination.to_string_lossy().into_owned()),
            bytes,
        });
    }
    if marker.exists() {
        let _ = fs::remove_file(&marker);
    }
    let bytes = match complete_path.metadata() {
        Ok(metadata) if metadata.is_file() && metadata.len() > 0 => metadata.len(),
        _ => {
            return Ok(PlaybackCachePromotionResult {
                status: PlaybackCachePromotionStatus::Partial,
                path: None,
                bytes: 0,
            });
        }
    };

    fs::create_dir_all(&destination_directory)?;
    let temporary = destination.with_extension(format!("{extension}.promote.tmp"));
    if temporary.exists() {
        fs::remove_file(&temporary)?;
    }
    let moved = fs::rename(&complete_path, &temporary).is_ok();
    if !moved {
        fs::copy(&complete_path, &temporary)?;
    }
    let promotion_result = (|| {
        let temporary_file = OpenOptions::new().read(true).write(true).open(&temporary)?;
        temporary_file.sync_all()?;
        if temporary_file.metadata()?.len() != bytes {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                "promoted playback cache size changed",
            ));
        }
        fs::rename(&temporary, &destination)?;
        if let Ok(directory) = File::open(&destination_directory) {
            let _ = directory.sync_all();
        }
        if !moved {
            fs::remove_file(&complete_path)?;
        }
        let _ = fs::remove_file(&partial_path);
        let _ = fs::remove_file(&index_path);
        write_promotion_marker(&marker, &destination)?;
        Ok(())
    })();
    if promotion_result.is_err() && moved && temporary.exists() && !complete_path.exists() {
        let _ = fs::rename(&temporary, &complete_path);
    }
    promotion_result?;
    Ok(PlaybackCachePromotionResult {
        status: PlaybackCachePromotionStatus::Promoted,
        path: Some(destination.to_string_lossy().into_owned()),
        bytes,
    })
}

fn write_promotion_marker(marker: &FsPath, destination: &FsPath) -> io::Result<()> {
    let temporary = marker.with_extension("promoted.tmp");
    fs::write(&temporary, destination.to_string_lossy().as_bytes())?;
    File::open(&temporary)?.sync_all()?;
    if marker.exists() {
        fs::remove_file(marker)?;
    }
    fs::rename(temporary, marker)
}

fn sanitized_extension(extension: &str) -> String {
    let extension = extension
        .trim()
        .trim_start_matches('.')
        .to_ascii_lowercase();
    if !extension.is_empty()
        && extension.len() <= 10
        && extension.bytes().all(|byte| byte.is_ascii_alphanumeric())
    {
        extension
    } else {
        "bin".to_string()
    }
}

fn load_cached_blocks(path: &PathBuf, total_size: u64) -> Option<HashSet<u64>> {
    let contents = fs::read_to_string(path).ok()?;
    let mut lines = contents.lines();
    if lines.next()?.parse::<u64>().ok()? != total_size {
        return None;
    }
    Some(
        lines
            .filter_map(|line| line.parse::<u64>().ok())
            .filter(|start| *start < total_size && *start % BLOCK_SIZE == 0)
            .collect(),
    )
}

fn persist_cached_blocks(
    path: &PathBuf,
    total_size: u64,
    blocks: impl Iterator<Item = u64>,
) -> io::Result<()> {
    let mut blocks = blocks.collect::<Vec<_>>();
    blocks.sort_unstable();
    let mut contents = format!("{total_size}\n");
    for block in blocks {
        contents.push_str(&format!("{block}\n"));
    }
    fs::write(path, contents)
}

fn read_file_range(path: &PathBuf, start: u64, len: usize) -> Option<Bytes> {
    let mut file = File::open(path).ok()?;
    file.seek(SeekFrom::Start(start)).ok()?;
    let mut bytes = vec![0_u8; len];
    file.read_exact(&mut bytes).ok()?;
    Some(Bytes::from(bytes))
}

fn write_file_range(path: &PathBuf, start: u64, bytes: &Bytes) -> io::Result<()> {
    let mut file = OpenOptions::new()
        .create(true)
        .truncate(false)
        .write(true)
        .open(path)?;
    file.seek(SeekFrom::Start(start))?;
    file.write_all(bytes)?;
    file.flush()
}

fn prune_cache_directory<'a>(
    directory: Option<PathBuf>,
    max_bytes: u64,
    keep: impl IntoIterator<Item = &'a PathBuf>,
) {
    let Some(directory) = directory else {
        return;
    };
    let keep = keep.into_iter().cloned().collect::<HashSet<_>>();
    let Ok(entries) = fs::read_dir(&directory) else {
        return;
    };
    let mut files = entries
        .filter_map(Result::ok)
        .filter_map(|entry| {
            let path = entry.path();
            let metadata = entry.metadata().ok()?;
            metadata
                .is_file()
                .then_some((path, metadata.len(), metadata.modified().ok()))
        })
        .collect::<Vec<_>>();
    let mut total = files.iter().map(|(_, size, _)| *size).sum::<u64>();
    if total <= max_bytes {
        return;
    }
    files.sort_by_key(|(_, _, modified)| *modified);
    for (path, size, _) in files {
        if total <= max_bytes {
            break;
        }
        if keep.contains(&path) {
            continue;
        }
        if fs::remove_file(&path).is_ok() {
            total = total.saturating_sub(size);
        }
    }
}

pub async fn start_playback_gateway(
    backend: Arc<dyn StorageBackend + Send + Sync>,
    path: String,
) -> BResult<Arc<PlaybackSession>> {
    start_playback_gateway_internal(backend, path, None).await
}

pub async fn start_cached_playback_gateway(
    backend: Arc<dyn StorageBackend + Send + Sync>,
    path: String,
    cache_options: PlaybackCacheOptions,
) -> BResult<Arc<PlaybackSession>> {
    start_playback_gateway_internal(backend, path, Some(cache_options)).await
}

async fn start_playback_gateway_internal(
    backend: Arc<dyn StorageBackend + Send + Sync>,
    path: String,
    cache_options: Option<PlaybackCacheOptions>,
) -> BResult<Arc<PlaybackSession>> {
    let probe = backend
        .get_range_response(path.clone(), ByteRange::new(0, 0)?)
        .await?;
    if probe.total_size == 0 {
        return Err(BError::AssetLoadFail(
            "playback source is empty".to_string(),
        ));
    }

    let token = random_token();
    let inferred_content_type = content_type_for_path(&path);
    let content_type = probe
        .content_type
        .filter(|value| !is_generic_content_type(value))
        .unwrap_or_else(|| inferred_content_type.to_string());
    let source = Arc::new(PlaybackSource {
        backend,
        path: path.clone(),
        total_size: probe.total_size,
        content_type,
        token: token.clone(),
        active: AtomicBool::new(true),
        cache: Mutex::new(LruCache::new(NonZeroUsize::new(CACHE_BLOCKS).unwrap())),
        persistent_cache: cache_options
            .and_then(|options| PersistentPlaybackCache::open(options, probe.total_size)),
        remote_requests: AtomicU64::new(1),
        remote_bytes: AtomicU64::new(probe.bytes.len() as u64),
    });

    let server_source = source.clone();
    let (ready_tx, ready_rx) = oneshot::channel();
    let (shutdown_tx, shutdown_rx) = oneshot::channel();
    std::thread::Builder::new()
        .name("playback-gateway".into())
        .spawn(move || {
            let rt = tokio::runtime::Builder::new_current_thread()
                .enable_all()
                .build()
                .expect("playback gateway runtime");
            rt.block_on(async move {
                let listener = match TcpListener::bind(SocketAddrV4::new(Ipv4Addr::LOCALHOST, 0))
                    .and_then(|listener| {
                        listener.set_nonblocking(true)?;
                        Ok(listener)
                    }) {
                    Ok(listener) => listener,
                    Err(error) => {
                        let _ = ready_tx.send(Err(error.to_string()));
                        return;
                    }
                };
                let address = match listener.local_addr() {
                    Ok(address) => address,
                    Err(error) => {
                        let _ = ready_tx.send(Err(error.to_string()));
                        return;
                    }
                };
                let router = Router::new()
                    .route("/media/:token/:file_name", get(get_media).head(head_media))
                    .with_state(server_source);
                let server = match axum::Server::from_tcp(listener) {
                    Ok(server) => server,
                    Err(error) => {
                        let _ = ready_tx.send(Err(error.to_string()));
                        return;
                    }
                }
                .serve(router.into_make_service())
                .with_graceful_shutdown(async move {
                    let _ = shutdown_rx.await;
                });
                if ready_tx.send(Ok(address.port())).is_err() {
                    return;
                }
                if let Err(error) = server.await {
                    tracing::error!("playback gateway failed: {error}");
                }
            });
        })
        .expect("playback gateway thread");
    let port = ready_rx
        .await
        .map_err(|error| BError::CustomError {
            message: format!("playback gateway stopped during startup: {error}"),
        })?
        .map_err(|message| BError::CustomError {
            message: format!("failed to create playback gateway: {message}"),
        })?;

    tracing::info!(
        total_size = source.total_size,
        content_type = %source.content_type,
        "playback gateway ready"
    );
    Ok(Arc::new(PlaybackSession {
        url: format!(
            "http://127.0.0.1:{port}/media/{token}/stream.{}",
            media_extension_for_path(&path)
        ),
        source,
        shutdown: Mutex::new(Some(shutdown_tx)),
    }))
}

pub async fn start_http_playback_cache_gateway(
    uri: String,
    headers: HashMap<String, String>,
    cache_options: PlaybackCacheOptions,
) -> BResult<Arc<PlaybackSession>> {
    let backend = Arc::new(DirectHttpPlaybackBackend::new(&uri, headers)?);
    start_cached_playback_gateway(backend, uri, cache_options).await
}

pub async fn start_completed_playback_cache(
    cache_options: PlaybackCacheOptions,
) -> BResult<Option<Arc<PlaybackSession>>> {
    let Some((complete_path, _, _)) = cache_paths(&cache_options) else {
        return Ok(None);
    };
    let Some(metadata) = complete_path.metadata().ok() else {
        return Ok(None);
    };
    if !metadata.is_file() || metadata.len() == 0 {
        return Ok(None);
    }
    start_playback_gateway(
        Arc::new(LocalBackend::new()),
        complete_path.to_string_lossy().into_owned(),
    )
    .await
    .map(Some)
}

struct DirectHttpPlaybackBackend {
    client: reqwest::Client,
    headers: reqwest::header::HeaderMap,
}

impl DirectHttpPlaybackBackend {
    fn new(uri: &str, headers: HashMap<String, String>) -> BResult<Self> {
        let mut header_map = reqwest::header::HeaderMap::new();
        for (name, value) in headers {
            let name =
                reqwest::header::HeaderName::from_bytes(name.as_bytes()).map_err(|error| {
                    BError::CustomError {
                        message: format!("invalid playback header name: {error}"),
                    }
                })?;
            let value = reqwest::header::HeaderValue::from_str(&value).map_err(|error| {
                BError::CustomError {
                    message: format!("invalid playback header value: {error}"),
                }
            })?;
            header_map.insert(name, value);
        }
        let origin = reqwest::Url::parse(uri).map_err(|_| BError::CustomError {
            message: "HTTP playback URL is invalid".to_string(),
        })?;
        let restrict_cross_origin = !header_map.is_empty();
        let client_builder = disable_proxy_for_loopback(reqwest::Client::builder(), &origin);
        let client = client_builder
            .redirect(reqwest::redirect::Policy::custom(move |attempt| {
                if attempt.previous().len() >= 10 {
                    attempt.error("too many playback redirects")
                } else if !restrict_cross_origin || same_origin(&origin, attempt.url()) {
                    attempt.follow()
                } else {
                    attempt.error("cross-origin playback redirect rejected")
                }
            }))
            .build()
            .map_err(StorageBackendError::from)?;
        Ok(Self {
            client,
            headers: header_map,
        })
    }
}

fn disable_proxy_for_loopback(
    builder: reqwest::ClientBuilder,
    origin: &reqwest::Url,
) -> reqwest::ClientBuilder {
    if is_numeric_loopback_origin(origin) {
        builder.no_proxy()
    } else {
        builder
    }
}

fn is_numeric_loopback_origin(origin: &reqwest::Url) -> bool {
    origin
        .host_str()
        .map(|host| host.trim_start_matches('[').trim_end_matches(']'))
        .and_then(|host| host.parse::<std::net::IpAddr>().ok())
        .is_some_and(|address| address.is_loopback())
}

impl StorageBackend for DirectHttpPlaybackBackend {
    fn list(&self, _dir: String) -> BoxFuture<'_, StorageBackendResult<Vec<Entry>>> {
        Box::pin(async {
            Err(StorageBackendError::UnsupportedFeature(
                "HTTP playback cache does not support directory listing".to_string(),
            ))
        })
    }

    fn get(
        &self,
        _path: String,
        _byte_offset: u64,
    ) -> BoxFuture<'_, StorageBackendResult<StreamFile>> {
        Box::pin(async {
            Err(StorageBackendError::UnsupportedFeature(
                "HTTP playback cache requires range requests".to_string(),
            ))
        })
    }

    fn get_range_response(
        &self,
        uri: String,
        range: ByteRange,
    ) -> BoxFuture<'_, StorageBackendResult<RangeResponse>> {
        let client = self.client.clone();
        let headers = self.headers.clone();
        Box::pin(async move {
            async_runtime::tokio_runtime()
                .spawn(async move {
                    let response = client
                        .get(uri)
                        .headers(headers)
                        .header(
                            reqwest::header::RANGE,
                            format!("bytes={}-{}", range.start, range.end_inclusive),
                        )
                        .send()
                        .await?;
                    if response.status() != reqwest::StatusCode::PARTIAL_CONTENT {
                        if response.status().is_success() {
                            return Err(StorageBackendError::RangeNotSupported {
                                status: response.status().as_u16(),
                            });
                        }
                        return Err(response.error_for_status().unwrap_err().into());
                    }
                    let content_range = response
                        .headers()
                        .get(reqwest::header::CONTENT_RANGE)
                        .and_then(|value| value.to_str().ok())
                        .map(str::to_owned)
                        .ok_or_else(|| {
                            StorageBackendError::InvalidContentRange("missing".to_string())
                        })?;
                    let (start, end_inclusive, total_size) =
                        parse_origin_content_range(&content_range)?;
                    if start != range.start || end_inclusive != range.end_inclusive {
                        return Err(StorageBackendError::InvalidContentRange(content_range));
                    }
                    let content_type = response
                        .headers()
                        .get(reqwest::header::CONTENT_TYPE)
                        .and_then(|value| value.to_str().ok())
                        .map(str::to_owned);
                    let bytes = response.bytes().await?;
                    if bytes.len() as u64 != range.len() {
                        return Err(StorageBackendError::InvalidContentRange(format!(
                            "{content_range}; body length {}",
                            bytes.len()
                        )));
                    }
                    Ok(RangeResponse {
                        bytes,
                        total_size,
                        content_type,
                    })
                })
                .await?
        })
    }
}

fn same_origin(left: &reqwest::Url, right: &reqwest::Url) -> bool {
    left.scheme() == right.scheme()
        && left.host_str() == right.host_str()
        && left.port_or_known_default() == right.port_or_known_default()
}

fn parse_origin_content_range(value: &str) -> StorageBackendResult<(u64, u64, u64)> {
    let (range, total_size) = value
        .strip_prefix("bytes ")
        .and_then(|value| value.split_once('/'))
        .ok_or_else(|| StorageBackendError::InvalidContentRange(value.to_string()))?;
    let (start, end_inclusive) = range
        .split_once('-')
        .ok_or_else(|| StorageBackendError::InvalidContentRange(value.to_string()))?;
    let start = start
        .parse::<u64>()
        .map_err(|_| StorageBackendError::InvalidContentRange(value.to_string()))?;
    let end_inclusive = end_inclusive
        .parse::<u64>()
        .map_err(|_| StorageBackendError::InvalidContentRange(value.to_string()))?;
    let total_size = total_size
        .parse::<u64>()
        .map_err(|_| StorageBackendError::InvalidContentRange(value.to_string()))?;
    if end_inclusive < start || total_size == 0 || end_inclusive >= total_size {
        return Err(StorageBackendError::InvalidContentRange(value.to_string()));
    }
    Ok((start, end_inclusive, total_size))
}

async fn head_media(
    Path((token, _file_name)): Path<(String, String)>,
    State(source): State<Arc<PlaybackSource>>,
) -> Response {
    if token != source.token || !source.active.load(Ordering::Acquire) {
        return StatusCode::NOT_FOUND.into_response();
    }
    response_with_headers(
        StatusCode::OK,
        source.total_size,
        None,
        &source.content_type,
        Body::empty(),
    )
}

async fn get_media(
    Path((token, _file_name)): Path<(String, String)>,
    State(source): State<Arc<PlaybackSource>>,
    headers: HeaderMap,
) -> Response {
    if token != source.token || !source.active.load(Ordering::Acquire) {
        return StatusCode::NOT_FOUND.into_response();
    }
    let resolved = resolve_range(
        headers.get(RANGE).and_then(|value| value.to_str().ok()),
        source.total_size,
    );
    let (start, end_inclusive, partial) = match resolved {
        Ok(value) => value,
        Err(()) => {
            let mut response = StatusCode::RANGE_NOT_SATISFIABLE.into_response();
            response.headers_mut().insert(
                CONTENT_RANGE,
                HeaderValue::from_str(&format!("bytes */{}", source.total_size)).unwrap(),
            );
            return response;
        }
    };
    let stream_source = source.clone();
    let stream = async_stream::stream! {
        let mut current = start;
        while current <= end_inclusive {
            let block_start = current / BLOCK_SIZE * BLOCK_SIZE;
            match stream_source.block(block_start).await {
                Ok(block) => {
                    let offset = (current - block_start) as usize;
                    if offset >= block.len() {
                        yield Err(io::Error::new(io::ErrorKind::UnexpectedEof, "range block too short"));
                        break;
                    }
                    let remaining = (end_inclusive - current + 1) as usize;
                    let count = remaining.min(block.len() - offset);
                    yield Ok::<Bytes, io::Error>(block.slice(offset..offset + count));
                    current += count as u64;
                }
                Err(error) => {
                    yield Err(io::Error::other(error));
                    break;
                }
            }
        }
    };
    let status = if partial {
        StatusCode::PARTIAL_CONTENT
    } else {
        StatusCode::OK
    };
    response_with_headers(
        status,
        end_inclusive - start + 1,
        partial.then_some((start, end_inclusive, source.total_size)),
        &source.content_type,
        Body::wrap_stream(stream),
    )
}

fn response_with_headers(
    status: StatusCode,
    content_length: u64,
    content_range: Option<(u64, u64, u64)>,
    content_type: &str,
    body: Body,
) -> Response {
    let mut response = Response::new(boxed(body));
    *response.status_mut() = status;
    let headers = response.headers_mut();
    headers.insert(ACCEPT_RANGES, HeaderValue::from_static("bytes"));
    headers.insert(
        CONTENT_LENGTH,
        HeaderValue::from_str(&content_length.to_string()).unwrap(),
    );
    headers.insert(
        CONTENT_TYPE,
        HeaderValue::from_str(content_type)
            .unwrap_or_else(|_| HeaderValue::from_static("application/octet-stream")),
    );
    if let Some((start, end_inclusive, total_size)) = content_range {
        headers.insert(
            CONTENT_RANGE,
            HeaderValue::from_str(&format!("bytes {start}-{end_inclusive}/{total_size}")).unwrap(),
        );
    }
    response
}

fn resolve_range(value: Option<&str>, total_size: u64) -> Result<(u64, u64, bool), ()> {
    let Some(value) = value else {
        return Ok((0, total_size - 1, false));
    };
    let value = value.strip_prefix("bytes=").ok_or(())?;
    if value.contains(',') {
        return Err(());
    }
    let (start, end) = value.split_once('-').ok_or(())?;
    if start.is_empty() {
        let suffix_len: u64 = end.parse().map_err(|_| ())?;
        if suffix_len == 0 {
            return Err(());
        }
        let start = total_size.saturating_sub(suffix_len);
        return Ok((start, total_size - 1, true));
    }
    let start: u64 = start.parse().map_err(|_| ())?;
    if start >= total_size {
        return Err(());
    }
    let end_inclusive = if end.is_empty() {
        total_size - 1
    } else {
        end.parse::<u64>().map_err(|_| ())?.min(total_size - 1)
    };
    if end_inclusive < start {
        return Err(());
    }
    Ok((start, end_inclusive, true))
}

fn random_token() -> String {
    let mut bytes = [0_u8; 16];
    OsRng.fill_bytes(&mut bytes);
    bytes.iter().map(|byte| format!("{byte:02x}")).collect()
}

fn content_type_for_path(path: &str) -> &'static str {
    match media_extension_for_path(path) {
        "flac" => "audio/flac",
        "mp3" => "audio/mpeg",
        "m4a" | "mp4" => "audio/mp4",
        "ogg" | "oga" => "audio/ogg",
        "opus" => "audio/opus",
        "wav" => "audio/wav",
        _ => "application/octet-stream",
    }
}

fn media_extension_for_path(path: &str) -> &'static str {
    match path
        .rsplit('.')
        .next()
        .map(str::to_ascii_lowercase)
        .as_deref()
    {
        Some("flac") => "flac",
        Some("mp3") => "mp3",
        Some("m4a") => "m4a",
        Some("mp4") => "mp4",
        Some("ogg") => "ogg",
        Some("oga") => "oga",
        Some("opus") => "opus",
        Some("wav") => "wav",
        _ => "bin",
    }
}

fn is_generic_content_type(value: &str) -> bool {
    matches!(
        value
            .split(';')
            .next()
            .unwrap_or_default()
            .trim()
            .to_ascii_lowercase()
            .as_str(),
        "application/octet-stream" | "binary/octet-stream"
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use futures_util::future::BoxFuture;
    use std::sync::mpsc;
    use std::{
        future::Future,
        io::{Read, Write},
        sync::Arc,
        task::{Context, Poll, Wake, Waker},
        thread,
        time::Duration,
    };
    use storage_backend::{
        Entry, LocalBackend, RangeResponse, StorageBackendError, StorageBackendResult, StreamFile,
    };

    struct ThreadWaker(thread::Thread);

    impl Wake for ThreadWaker {
        fn wake(self: Arc<Self>) {
            self.0.unpark();
        }

        fn wake_by_ref(self: &Arc<Self>) {
            self.0.unpark();
        }
    }

    fn block_on_without_tokio_runtime<F: Future>(future: F) -> F::Output {
        let mut future = std::pin::pin!(future);
        let waker = Waker::from(Arc::new(ThreadWaker(thread::current())));
        let mut context = Context::from_waker(&waker);
        loop {
            match future.as_mut().poll(&mut context) {
                Poll::Ready(output) => return output,
                Poll::Pending => thread::park_timeout(Duration::from_secs(1)),
            }
        }
    }

    #[derive(Default)]
    struct SizeChangingBackend {
        range_calls: AtomicU64,
        released: AtomicBool,
    }

    #[derive(Default)]
    struct ReleaseCountingBackend {
        released: AtomicU64,
    }

    struct PrefixBackend {
        total_size: u64,
        ranges: Mutex<Vec<(u64, u64)>>,
    }

    impl PrefixBackend {
        fn new(total_size: u64) -> Self {
            Self {
                total_size,
                ranges: Mutex::new(Vec::new()),
            }
        }
    }

    impl StorageBackend for SizeChangingBackend {
        fn list(&self, _dir: String) -> BoxFuture<'_, StorageBackendResult<Vec<Entry>>> {
            Box::pin(async { Ok(Vec::new()) })
        }

        fn get(
            &self,
            _path: String,
            _byte_offset: u64,
        ) -> BoxFuture<'_, StorageBackendResult<StreamFile>> {
            Box::pin(async {
                Err(StorageBackendError::UnsupportedFeature(
                    "streaming is not used by this test".to_string(),
                ))
            })
        }

        fn get_range_response(
            &self,
            _path: String,
            range: ByteRange,
        ) -> BoxFuture<'_, StorageBackendResult<RangeResponse>> {
            let total_size = if self.range_calls.fetch_add(1, Ordering::SeqCst) == 0 {
                10
            } else {
                11
            };
            Box::pin(async move {
                Ok(RangeResponse {
                    bytes: Bytes::from(vec![b'x'; range.len() as usize]),
                    total_size,
                    content_type: Some("audio/flac".to_string()),
                })
            })
        }

        fn release(&self, _path: String) -> BoxFuture<'_, StorageBackendResult<()>> {
            self.released.store(true, Ordering::SeqCst);
            Box::pin(async { Ok(()) })
        }
    }

    impl StorageBackend for ReleaseCountingBackend {
        fn list(&self, _dir: String) -> BoxFuture<'_, StorageBackendResult<Vec<Entry>>> {
            Box::pin(async { Ok(Vec::new()) })
        }

        fn get(
            &self,
            _path: String,
            _byte_offset: u64,
        ) -> BoxFuture<'_, StorageBackendResult<StreamFile>> {
            Box::pin(async {
                Err(StorageBackendError::UnsupportedFeature(
                    "streaming is not used by this test".to_string(),
                ))
            })
        }

        fn get_range_response(
            &self,
            _path: String,
            range: ByteRange,
        ) -> BoxFuture<'_, StorageBackendResult<RangeResponse>> {
            Box::pin(async move {
                Ok(RangeResponse {
                    bytes: Bytes::from(vec![b'x'; range.len() as usize]),
                    total_size: 10,
                    content_type: Some("audio/flac".to_string()),
                })
            })
        }

        fn release(&self, _path: String) -> BoxFuture<'_, StorageBackendResult<()>> {
            self.released.fetch_add(1, Ordering::SeqCst);
            Box::pin(async { Ok(()) })
        }
    }

    impl StorageBackend for PrefixBackend {
        fn list(&self, _dir: String) -> BoxFuture<'_, StorageBackendResult<Vec<Entry>>> {
            Box::pin(async { Ok(Vec::new()) })
        }

        fn get(
            &self,
            _path: String,
            _byte_offset: u64,
        ) -> BoxFuture<'_, StorageBackendResult<StreamFile>> {
            Box::pin(async {
                Err(StorageBackendError::UnsupportedFeature(
                    "streaming is not used by this test".to_string(),
                ))
            })
        }

        fn get_range_response(
            &self,
            _path: String,
            range: ByteRange,
        ) -> BoxFuture<'_, StorageBackendResult<RangeResponse>> {
            self.ranges
                .lock()
                .unwrap()
                .push((range.start, range.end_inclusive));
            let total_size = self.total_size;
            Box::pin(async move {
                Ok(RangeResponse {
                    bytes: Bytes::from(vec![b'x'; range.len() as usize]),
                    total_size,
                    content_type: Some("audio/flac".to_string()),
                })
            })
        }
    }

    #[test]
    fn parses_http_ranges() {
        assert_eq!(resolve_range(None, 100), Ok((0, 99, false)));
        assert_eq!(resolve_range(Some("bytes=10-19"), 100), Ok((10, 19, true)));
        assert_eq!(resolve_range(Some("bytes=90-"), 100), Ok((90, 99, true)));
        assert_eq!(resolve_range(Some("bytes=-10"), 100), Ok((90, 99, true)));
        assert!(resolve_range(Some("bytes=100-"), 100).is_err());
        assert!(resolve_range(Some("bytes=1-2,4-5"), 100).is_err());
    }

    #[test]
    fn creates_http_cache_gateway_without_caller_tokio_reactor() {
        let root = std::env::temp_dir().join(format!("musicapp-no-reactor-{}", random_token()));
        let source_path = root.join("source.flac");
        fs::create_dir_all(&root).unwrap();
        fs::write(&source_path, b"0123456789").unwrap();
        let origin = block_on_without_tokio_runtime(start_playback_gateway(
            Arc::new(LocalBackend::new()),
            source_path.to_string_lossy().into_owned(),
        ))
        .unwrap();
        let options = PlaybackCacheOptions {
            directory: root.join("cache").to_string_lossy().into_owned(),
            key: "no-reactor".to_string(),
            extension: "flac".to_string(),
            write_enabled: true,
            max_bytes: 1_024,
        };

        let cached = block_on_without_tokio_runtime(start_http_playback_cache_gateway(
            origin.url(),
            HashMap::new(),
            options,
        ))
        .unwrap();

        assert!(cached.url().ends_with("/stream.flac"));
        cached.shutdown();
        origin.shutdown();
        fs::remove_dir_all(root).unwrap();
    }

    #[tokio::test]
    async fn prefetches_two_four_and_non_aligned_mib_prefixes_by_block() {
        let cases = [
            (2 * 1024 * 1024, 8_u64),
            (4 * 1024 * 1024, 16_u64),
            (2 * 1024 * 1024 + 1, 9_u64),
        ];
        for (index, (max_bytes, expected_blocks)) in cases.into_iter().enumerate() {
            let root = std::env::temp_dir()
                .join(format!("musicapp-prefix-size-{index}-{}", random_token()));
            let total_size = 5 * 1024 * 1024 + 17;
            let options = PlaybackCacheOptions {
                directory: root.to_string_lossy().into_owned(),
                key: format!("prefix-size-{index}"),
                extension: "flac".to_string(),
                write_enabled: true,
                max_bytes: 16 * 1024 * 1024,
            };
            let backend = Arc::new(PrefixBackend::new(total_size));
            let session =
                start_cached_playback_gateway(backend, "/prefix.flac".to_string(), options)
                    .await
                    .unwrap();

            assert_eq!(session.prefetch_prefix(max_bytes).await.unwrap(), max_bytes);
            assert_eq!(session.stats().remote_requests, expected_blocks + 1);
            session.shutdown();
            let _ = fs::remove_dir_all(root);
        }
    }

    #[tokio::test]
    async fn prefix_prefetch_reuses_partial_blocks_and_promotes_full_cache() {
        let root = std::env::temp_dir().join(format!("musicapp-prefix-resume-{}", random_token()));
        let total_size = 4 * BLOCK_SIZE - 13;
        let options = PlaybackCacheOptions {
            directory: root.to_string_lossy().into_owned(),
            key: "same-physical-resource".to_string(),
            extension: "flac".to_string(),
            write_enabled: true,
            max_bytes: 16 * 1024 * 1024,
        };
        let backend = Arc::new(PrefixBackend::new(total_size));
        let partial = start_cached_playback_gateway(
            backend.clone(),
            "/resume.flac".to_string(),
            options.clone(),
        )
        .await
        .unwrap();
        assert_eq!(
            partial.prefetch_prefix(2 * BLOCK_SIZE).await.unwrap(),
            2 * BLOCK_SIZE
        );
        assert_eq!(partial.stats().remote_requests, 3);
        partial.shutdown();

        let resumed =
            start_cached_playback_gateway(backend, "/resume.flac".to_string(), options.clone())
                .await
                .unwrap();
        assert_eq!(
            resumed.prefetch_prefix(total_size).await.unwrap(),
            total_size
        );
        assert_eq!(
            resumed.stats().remote_requests,
            3,
            "probe plus only the two missing blocks should hit the remote source"
        );
        assert_eq!(
            resumed.prefetch_prefix(total_size).await.unwrap(),
            total_size
        );
        assert_eq!(
            resumed.stats().remote_requests,
            3,
            "already cached blocks must not issue more remote requests"
        );
        let (complete, partial_path, index_path) = cache_paths(&options).unwrap();
        assert_eq!(complete.metadata().unwrap().len(), total_size);
        assert!(!partial_path.exists());
        assert!(!index_path.exists());
        resumed.shutdown();
        let _ = fs::remove_dir_all(root);
    }

    #[tokio::test]
    async fn prefix_prefetch_stops_when_session_is_inactive() {
        let backend = Arc::new(PrefixBackend::new(2 * BLOCK_SIZE));
        let session = start_playback_gateway(backend, "/inactive.flac".to_string())
            .await
            .unwrap();
        session.shutdown();

        assert!(session.prefetch_prefix(BLOCK_SIZE).await.is_err());
        assert_eq!(session.stats().remote_requests, 1);
    }

    #[tokio::test]
    async fn streams_bounded_ranges_over_loopback() {
        let path = std::env::temp_dir().join(format!("musicapp-range-{}.flac", random_token()));
        std::fs::write(&path, b"0123456789").unwrap();
        let session = start_playback_gateway(
            Arc::new(LocalBackend::new()),
            path.to_string_lossy().to_string(),
        )
        .await
        .unwrap();

        assert!(session.url().ends_with("/stream.flac"));
        let client = reqwest::Client::builder().no_proxy().build().unwrap();
        let head = client.head(session.url()).send().await.unwrap();
        assert_eq!(head.status(), StatusCode::OK);
        assert_eq!(head.headers()[CONTENT_LENGTH], "10");
        assert_eq!(head.headers()[CONTENT_TYPE], "audio/flac");
        assert_eq!(head.headers()[ACCEPT_RANGES], "bytes");

        let full = client.get(session.url()).send().await.unwrap();
        assert_eq!(full.status(), StatusCode::OK);
        assert_eq!(full.bytes().await.unwrap().as_ref(), b"0123456789");

        let response = client
            .get(session.url())
            .header(RANGE, "bytes=2-5")
            .send()
            .await
            .unwrap();
        assert_eq!(response.status(), StatusCode::PARTIAL_CONTENT);
        assert_eq!(response.headers()[CONTENT_RANGE], "bytes 2-5/10");
        assert_eq!(response.bytes().await.unwrap().as_ref(), b"2345");

        let suffix = client
            .get(session.url())
            .header(RANGE, "bytes=-3")
            .send()
            .await
            .unwrap();
        assert_eq!(suffix.status(), StatusCode::PARTIAL_CONTENT);
        assert_eq!(suffix.bytes().await.unwrap().as_ref(), b"789");

        let forward_seek = client
            .get(session.url())
            .header(RANGE, "bytes=7-8")
            .send()
            .await
            .unwrap();
        assert_eq!(forward_seek.bytes().await.unwrap().as_ref(), b"78");
        let backward_seek = client
            .get(session.url())
            .header(RANGE, "bytes=1-2")
            .send()
            .await
            .unwrap();
        assert_eq!(backward_seek.bytes().await.unwrap().as_ref(), b"12");

        let invalid = client
            .get(session.url())
            .header(RANGE, "bytes=10-")
            .send()
            .await
            .unwrap();
        assert_eq!(invalid.status(), StatusCode::RANGE_NOT_SATISFIABLE);
        assert_eq!(invalid.headers()[CONTENT_RANGE], "bytes */10");
        assert_eq!(session.stats().remote_requests, 2);

        let url = session.url();
        session.shutdown();
        assert!(!session.source.active.load(Ordering::Acquire));
        let direct = get_media(
            Path((session.source.token.clone(), "stream.flac".to_string())),
            State(session.source.clone()),
            HeaderMap::new(),
        )
        .await;
        assert_eq!(direct.status(), StatusCode::NOT_FOUND);
        let stopped = match tokio::time::timeout(
            std::time::Duration::from_millis(500),
            client.get(&url).send(),
        )
        .await
        {
            Err(_) | Ok(Err(_)) => true,
            Ok(Ok(response)) => !response.status().is_success(),
        };
        assert!(stopped, "playback URL remained available after shutdown");
        std::fs::remove_file(path).unwrap();
    }

    #[tokio::test]
    async fn persists_remote_playback_and_reopens_completed_cache() {
        let root = std::env::temp_dir().join(format!("musicapp-playback-cache-{}", random_token()));
        let source_path = root.join("source.flac");
        let cache_path = root.join("cache");
        fs::create_dir_all(&root).unwrap();
        fs::write(&source_path, b"0123456789").unwrap();
        let origin = start_playback_gateway(
            Arc::new(LocalBackend::new()),
            source_path.to_string_lossy().into_owned(),
        )
        .await
        .unwrap();
        let options = PlaybackCacheOptions {
            directory: cache_path.to_string_lossy().into_owned(),
            key: "storage:42\n/Music/Track.flac".to_string(),
            extension: "flac".to_string(),
            write_enabled: true,
            max_bytes: 1_024,
        };
        let cached =
            start_http_playback_cache_gateway(origin.url(), HashMap::new(), options.clone())
                .await
                .unwrap();
        let client = reqwest::Client::builder().no_proxy().build().unwrap();

        let response = client.get(cached.url()).send().await.unwrap();
        assert_eq!(response.bytes().await.unwrap().as_ref(), b"0123456789");
        cached.shutdown();
        origin.shutdown();

        let completed = start_completed_playback_cache(options)
            .await
            .unwrap()
            .expect("completed playback cache");
        let response = client.get(completed.url()).send().await.unwrap();
        assert_eq!(response.bytes().await.unwrap().as_ref(), b"0123456789");
        completed.shutdown();
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn partial_playback_cache_cannot_be_promoted() {
        let root = std::env::temp_dir().join(format!(
            "musicapp-playback-promotion-partial-{}",
            random_token()
        ));
        let options = PlaybackCacheOptions {
            directory: root.join("cache").to_string_lossy().into_owned(),
            key: "partial-cache".to_string(),
            extension: "flac".to_string(),
            write_enabled: true,
            max_bytes: 1_024,
        };
        let (_, partial, index) = cache_paths(&options).unwrap();
        fs::create_dir_all(partial.parent().unwrap()).unwrap();
        fs::write(&partial, b"incomplete").unwrap();
        fs::write(&index, b"10\n0\n").unwrap();

        let result = promote_completed_playback_cache(
            options,
            root.join("downloads").to_string_lossy().into_owned(),
        )
        .unwrap();

        assert_eq!(result.status, PlaybackCachePromotionStatus::Partial);
        assert!(result.path.is_none());
        assert!(partial.exists());
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn promotes_completed_cache_once_and_preserves_bytes_and_extension() {
        let root = std::env::temp_dir().join(format!(
            "musicapp-playback-promotion-complete-{}",
            random_token()
        ));
        let options = PlaybackCacheOptions {
            directory: root.join("cache").to_string_lossy().into_owned(),
            key: "storage:42\n/Music/Track.flac\nversion-1".to_string(),
            extension: "flac".to_string(),
            write_enabled: true,
            max_bytes: 1_024,
        };
        let (complete, _, _) = cache_paths(&options).unwrap();
        fs::create_dir_all(complete.parent().unwrap()).unwrap();
        let original = b"complete audio bytes";
        fs::write(&complete, original).unwrap();
        let destination_directory = root.join("downloads");

        let promoted = promote_completed_playback_cache(
            options.clone(),
            destination_directory.to_string_lossy().into_owned(),
        )
        .unwrap();
        let path = PathBuf::from(promoted.path.as_ref().unwrap());
        assert_eq!(promoted.status, PlaybackCachePromotionStatus::Promoted);
        assert_eq!(promoted.bytes, original.len() as u64);
        assert_eq!(
            path.extension().and_then(|value| value.to_str()),
            Some("flac")
        );
        assert_eq!(fs::read(&path).unwrap(), original);
        assert!(!complete.exists());

        let repeated = promote_completed_playback_cache(
            options,
            destination_directory.to_string_lossy().into_owned(),
        )
        .unwrap();
        assert_eq!(
            repeated.status,
            PlaybackCachePromotionStatus::AlreadyPromoted
        );
        assert_eq!(fs::read(&path).unwrap(), original);
        fs::remove_dir_all(root).unwrap();
    }

    #[tokio::test]
    async fn reports_remote_size_changes_and_releases_the_source() {
        let backend = Arc::new(SizeChangingBackend::default());
        let session = start_playback_gateway(backend.clone(), "/changed.flac".to_string())
            .await
            .unwrap();

        let error = session.source.block(0).await.unwrap_err();
        assert!(error.contains("remote size changed from 10 to 11"));

        session.shutdown();
        tokio::time::timeout(std::time::Duration::from_secs(2), async {
            while !backend.released.load(Ordering::SeqCst) {
                tokio::task::yield_now().await;
            }
        })
        .await
        .expect("playback source reader was not released");
    }

    #[tokio::test]
    async fn rapid_session_switching_releases_every_source_reader() {
        const SESSION_COUNT: u64 = 12;
        let backend = Arc::new(ReleaseCountingBackend::default());

        for index in 0..SESSION_COUNT {
            let session = start_playback_gateway(backend.clone(), format!("/track-{index}.flac"))
                .await
                .unwrap();
            session.shutdown();
        }

        tokio::time::timeout(std::time::Duration::from_secs(2), async {
            while backend.released.load(Ordering::SeqCst) != SESSION_COUNT {
                tokio::task::yield_now().await;
            }
        })
        .await
        .expect("rapid playback switching left source readers unreleased");
    }

    #[test]
    fn playback_redirect_policy_compares_effective_origin() {
        let origin = reqwest::Url::parse("https://media.example:443/base/track").unwrap();
        assert!(same_origin(
            &origin,
            &reqwest::Url::parse("https://media.example/base/other").unwrap()
        ));
        assert!(!same_origin(
            &origin,
            &reqwest::Url::parse("https://evil.example/base/other").unwrap()
        ));
        assert!(!same_origin(
            &origin,
            &reqwest::Url::parse("http://media.example/base/other").unwrap()
        ));
    }

    struct RawRedirectServer {
        url: String,
        records: Arc<Mutex<Vec<String>>>,
        stop: Option<mpsc::Sender<()>>,
        thread: Option<thread::JoinHandle<()>>,
    }

    impl RawRedirectServer {
        fn start(mode: RawRedirectMode) -> Self {
            let listener = TcpListener::bind(SocketAddrV4::new(Ipv4Addr::LOCALHOST, 0)).unwrap();
            listener.set_nonblocking(true).unwrap();
            let address = listener.local_addr().unwrap();
            let records = Arc::new(Mutex::new(Vec::new()));
            let records_for_thread = records.clone();
            let (stop, stop_rx) = mpsc::channel();
            let url = format!("http://127.0.0.1:{}/start", address.port());
            let thread = thread::spawn(move || {
                let deadline = std::time::Instant::now() + Duration::from_secs(5);
                loop {
                    if stop_rx.try_recv().is_ok() || std::time::Instant::now() >= deadline {
                        break;
                    }
                    let Ok((mut stream, _)) = listener.accept() else {
                        thread::sleep(Duration::from_millis(2));
                        continue;
                    };
                    stream
                        .set_read_timeout(Some(Duration::from_millis(500)))
                        .unwrap();
                    let request = read_raw_http_request(&mut stream);
                    records_for_thread.lock().unwrap().push(request.clone());
                    let path = request
                        .lines()
                        .next()
                        .and_then(|line| line.split_whitespace().nth(1))
                        .unwrap_or("/");
                    let response = mode.response(address.port(), path);
                    let _ = stream.write_all(response.as_bytes());
                    let _ = stream.shutdown(std::net::Shutdown::Both);
                }
            });
            Self {
                url,
                records,
                stop: Some(stop),
                thread: Some(thread),
            }
        }

        fn requests(&self) -> Vec<String> {
            self.records.lock().unwrap().clone()
        }
    }

    fn read_raw_http_request(stream: &mut std::net::TcpStream) -> String {
        let mut request = Vec::new();
        let mut chunk = [0_u8; 1024];
        let deadline = std::time::Instant::now() + Duration::from_secs(2);
        while !request.windows(4).any(|window| window == b"\r\n\r\n") {
            match stream.read(&mut chunk) {
                Ok(0) => break,
                Ok(size) => request.extend_from_slice(&chunk[..size]),
                Err(error)
                    if matches!(
                        error.kind(),
                        io::ErrorKind::WouldBlock | io::ErrorKind::TimedOut
                    ) && std::time::Instant::now() < deadline => {}
                Err(error) => panic!("raw HTTP request: {error}"),
            }
            assert!(request.len() <= 64 * 1024, "raw HTTP request was too large");
        }
        String::from_utf8_lossy(&request).into_owned()
    }

    impl Drop for RawRedirectServer {
        fn drop(&mut self) {
            if let Some(stop) = self.stop.take() {
                let _ = stop.send(());
            }
            if let Some(thread) = self.thread.take() {
                thread.join().unwrap();
            }
        }
    }

    enum RawRedirectMode {
        Same,
        Loop,
        Cross(String),
        Target,
    }

    impl RawRedirectMode {
        fn response(&self, port: u16, path: &str) -> String {
            match self {
                Self::Same if path == "/start" => format!(
                    "HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:{port}/final\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                ),
                Self::Same => "HTTP/1.1 206 Partial Content\r\nContent-Range: bytes 0-0/1\r\nContent-Length: 1\r\nContent-Type: audio/flac\r\nConnection: close\r\n\r\nx".to_string(),
                Self::Loop => format!(
                    "HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:{port}/loop\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                ),
                Self::Cross(target) if path == "/start" => format!(
                    "HTTP/1.1 302 Found\r\nLocation: {target}\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                ),
                Self::Cross(_) => "HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".to_string(),
                Self::Target => "HTTP/1.1 206 Partial Content\r\nContent-Range: bytes 0-0/1\r\nContent-Length: 1\r\nContent-Type: audio/flac\r\nConnection: close\r\n\r\nx".to_string(),
            }
        }
    }

    fn test_cache_options(name: &str) -> PlaybackCacheOptions {
        PlaybackCacheOptions {
            directory: std::env::temp_dir()
                .join(format!("musicapp-redirect-{name}"))
                .to_string_lossy()
                .into_owned(),
            key: name.to_string(),
            extension: "flac".to_string(),
            write_enabled: false,
            max_bytes: 0,
        }
    }

    #[test]
    fn numeric_loopback_origin_detection_does_not_trust_hostnames() {
        for url in ["http://127.0.0.1/track", "http://[::1]/track"] {
            assert!(is_numeric_loopback_origin(
                &reqwest::Url::parse(url).unwrap()
            ));
        }
        for url in [
            "http://localhost/track",
            "http://192.0.2.1/track",
            "https://media.example/track",
        ] {
            assert!(!is_numeric_loopback_origin(
                &reqwest::Url::parse(url).unwrap()
            ));
        }
    }

    #[tokio::test]
    async fn loopback_origin_bypasses_configured_proxy_but_remote_origin_keeps_it() {
        let target = RawRedirectServer::start(RawRedirectMode::Target);
        let bypassed_proxy = RawRedirectServer::start(RawRedirectMode::Target);
        let bypassed_proxy_port = reqwest::Url::parse(&bypassed_proxy.url)
            .unwrap()
            .port()
            .unwrap();
        let loopback_origin = reqwest::Url::parse(&target.url).unwrap();
        let loopback_client = disable_proxy_for_loopback(
            reqwest::Client::builder().proxy(
                reqwest::Proxy::all(format!("http://127.0.0.1:{bypassed_proxy_port}")).unwrap(),
            ),
            &loopback_origin,
        )
        .build()
        .unwrap();

        let response = loopback_client
            .get(loopback_origin)
            .header(reqwest::header::RANGE, "bytes=0-0")
            .send()
            .await
            .unwrap();
        assert_eq!(response.status(), reqwest::StatusCode::PARTIAL_CONTENT);
        assert!(!target.requests().is_empty());
        assert!(bypassed_proxy.requests().is_empty());

        let retained_proxy = RawRedirectServer::start(RawRedirectMode::Target);
        let retained_proxy_port = reqwest::Url::parse(&retained_proxy.url)
            .unwrap()
            .port()
            .unwrap();
        let remote_origin = reqwest::Url::parse("http://192.0.2.1/track").unwrap();
        let remote_client = disable_proxy_for_loopback(
            reqwest::Client::builder().proxy(
                reqwest::Proxy::all(format!("http://127.0.0.1:{retained_proxy_port}")).unwrap(),
            ),
            &remote_origin,
        )
        .build()
        .unwrap();

        let response = remote_client
            .get(remote_origin)
            .header(reqwest::header::RANGE, "bytes=0-0")
            .send()
            .await
            .unwrap();
        assert_eq!(response.status(), reqwest::StatusCode::PARTIAL_CONTENT);
        assert!(retained_proxy.requests().iter().any(|request| request
            .to_ascii_lowercase()
            .contains("get http://192.0.2.1/track")));
    }

    #[tokio::test]
    async fn redirect_policy_follows_same_origin_and_forwards_headers() {
        let server = RawRedirectServer::start(RawRedirectMode::Same);
        let session = start_http_playback_cache_gateway(
            server.url.clone(),
            HashMap::from([(String::from("X-Test"), String::from("required"))]),
            test_cache_options("same-origin"),
        )
        .await
        .unwrap();
        assert!(server.requests().iter().any(|request| {
            let request = request.to_ascii_lowercase();
            request.contains("get /final") && request.contains("x-test: required")
        }));
        session.shutdown();
    }

    #[tokio::test]
    async fn redirect_policy_rejects_cross_origin_before_sending_token() {
        let target = RawRedirectServer::start(RawRedirectMode::Target);
        let origin = RawRedirectServer::start(RawRedirectMode::Cross(target.url.clone()));
        let result = tokio::time::timeout(
            Duration::from_secs(2),
            start_http_playback_cache_gateway(
                origin.url.clone(),
                HashMap::from([(String::from("X-Emby-Token"), String::from("secret"))]),
                test_cache_options("cross-origin"),
            ),
        )
        .await
        .unwrap();
        assert!(result.is_err());
        assert!(
            target.requests().is_empty(),
            "cross-origin target received a request"
        );
    }

    #[tokio::test]
    async fn empty_header_redirect_can_follow_cross_origin_without_forwarding_custom_headers() {
        let target = RawRedirectServer::start(RawRedirectMode::Target);
        let origin = RawRedirectServer::start(RawRedirectMode::Cross(target.url.clone()));
        let session = start_http_playback_cache_gateway(
            origin.url.clone(),
            HashMap::new(),
            test_cache_options("cross-origin-no-headers"),
        )
        .await
        .unwrap();
        let target_requests = target.requests();
        assert!(target_requests.iter().any(|request| {
            let request = request.to_ascii_lowercase();
            request.contains("get /start")
                && !request.contains("x-emby-token:")
                && !request.contains("x-test:")
        }));
        session.shutdown();
    }

    #[tokio::test]
    async fn redirect_policy_bounds_same_origin_loops() {
        let server = RawRedirectServer::start(RawRedirectMode::Loop);
        let result = tokio::time::timeout(
            Duration::from_secs(2),
            start_http_playback_cache_gateway(
                server.url.clone(),
                HashMap::new(),
                test_cache_options("redirect-loop"),
            ),
        )
        .await
        .unwrap();
        assert!(result.is_err());
        assert!(server.requests().len() <= 11);
    }
}
