use std::{
    collections::{HashSet, VecDeque},
    pin::Pin,
    sync::{
        atomic::{AtomicBool, AtomicU64, Ordering},
        Arc,
    },
};

use async_runtime::tokio_runtime;
use futures_util::{stream::FuturesUnordered, StreamExt};
use storage_backend::{Entry, StorageBackend, StorageBackendError, StorageBackendResult};
use tokio::sync::{mpsc, Mutex, Notify};

use crate::{
    error::{BError, BResult},
    objects::{RemoteMusicScanBatch, StorageEntry},
    schema::StorageId,
};

const MAX_SCAN_BATCH_FILES: u32 = 1_000;
const MAX_SCAN_ENTRIES: u64 = 100_000;
const DEFAULT_DIRECTORY_CONCURRENCY: usize = 4;
const MIN_DIRECTORY_CONCURRENCY: usize = 1;
const MAX_DIRECTORY_CONCURRENCY: usize = 8;
const SCAN_CHANNEL_CAPACITY: usize = 400;
const MAX_DIRECTORY_RETRIES: u8 = 2;
const DIRECTORY_RETRY_DELAYS: [std::time::Duration; MAX_DIRECTORY_RETRIES as usize] = [
    std::time::Duration::from_millis(250),
    std::time::Duration::from_millis(500),
];

#[derive(Default)]
struct RemoteMusicScanStats {
    directory_request_count: AtomicU64,
    listed_directory_count: AtomicU64,
    visited_entry_count: AtomicU64,
}

enum ScanMessage {
    Entry(Entry),
    Failed(ScanFailure),
    Done,
}

enum ScanFailure {
    Backend(StorageBackendError),
    Message(String),
}

#[derive(Clone)]
struct DirectoryTask {
    path: String,
    retry_count: u8,
}

struct DirectoryScanArgs {
    backend: Arc<dyn StorageBackend + Send + Sync>,
    root: String,
    directory_concurrency: usize,
    sender: mpsc::Sender<ScanMessage>,
    stats: Arc<RemoteMusicScanStats>,
    cancelled: Arc<AtomicBool>,
    cancel_notify: Arc<Notify>,
    preserve_raw_paths: bool,
}

type DirectoryFuture = Pin<
    Box<dyn std::future::Future<Output = (DirectoryTask, StorageBackendResult<Vec<Entry>>)> + Send>,
>;

#[derive(uniffi::Object)]
pub struct RemoteMusicScanSession {
    storage_id: StorageId,
    receiver: Mutex<mpsc::Receiver<ScanMessage>>,
    pending_failure: Mutex<Option<ScanFailure>>,
    stats: Arc<RemoteMusicScanStats>,
    cancelled: Arc<AtomicBool>,
    done: AtomicBool,
    cancel_notify: Arc<Notify>,
    directory_concurrency: u32,
}

#[uniffi::export]
impl RemoteMusicScanSession {
    pub async fn next_batch(&self, max_files: u32) -> BResult<RemoteMusicScanBatch> {
        if !(1..=MAX_SCAN_BATCH_FILES).contains(&max_files) {
            return Err(BError::CustomError {
                message: format!(
                    "remote scan batch size must be between 1 and {MAX_SCAN_BATCH_FILES}"
                ),
            });
        }

        if let Some(failure) = self.pending_failure.lock().await.take() {
            self.done.store(true, Ordering::Release);
            return Err(scan_failure_to_error(failure));
        }
        if self.done.load(Ordering::Acquire) || self.cancelled.load(Ordering::Acquire) {
            self.done.store(true, Ordering::Release);
            return Ok(self.batch(Vec::new()));
        }

        let mut receiver = self.receiver.lock().await;
        let mut files = Vec::with_capacity(max_files as usize);
        while files.len() < max_files as usize {
            let message = tokio::select! {
                message = receiver.recv() => message,
                _ = self.cancel_notify.notified() => None,
            };
            if self.cancelled.load(Ordering::Acquire) {
                self.done.store(true, Ordering::Release);
                files.clear();
                break;
            }
            match message {
                Some(ScanMessage::Entry(entry)) => {
                    files.push(storage_entry(self.storage_id, entry));
                }
                Some(ScanMessage::Failed(failure)) if files.is_empty() => {
                    self.done.store(true, Ordering::Release);
                    return Err(scan_failure_to_error(failure));
                }
                Some(ScanMessage::Failed(failure)) => {
                    *self.pending_failure.lock().await = Some(failure);
                    break;
                }
                Some(ScanMessage::Done) | None => {
                    self.done.store(true, Ordering::Release);
                    break;
                }
            }
        }

        Ok(self.batch(files))
    }

    pub fn cancel(&self) {
        self.cancelled.store(true, Ordering::Release);
        self.cancel_notify.notify_waiters();
    }

    pub fn is_cancelled(&self) -> bool {
        self.cancelled.load(Ordering::Acquire)
    }
}

impl Drop for RemoteMusicScanSession {
    fn drop(&mut self) {
        self.cancelled.store(true, Ordering::Release);
        self.cancel_notify.notify_waiters();
    }
}

impl RemoteMusicScanSession {
    pub fn new(
        storage_id: StorageId,
        backend: Arc<dyn StorageBackend + Send + Sync>,
        root: String,
    ) -> Arc<Self> {
        Self::new_with_concurrency(storage_id, backend, root, DEFAULT_DIRECTORY_CONCURRENCY)
    }

    pub fn new_openlist(
        storage_id: StorageId,
        backend: Arc<dyn StorageBackend + Send + Sync>,
        root: String,
    ) -> Arc<Self> {
        Self::new_with_concurrency_and_path_mode(
            storage_id,
            backend,
            root,
            DEFAULT_DIRECTORY_CONCURRENCY,
            true,
        )
    }

    pub(crate) fn new_with_concurrency(
        storage_id: StorageId,
        backend: Arc<dyn StorageBackend + Send + Sync>,
        root: String,
        directory_concurrency: usize,
    ) -> Arc<Self> {
        Self::new_with_concurrency_and_path_mode(
            storage_id,
            backend,
            root,
            directory_concurrency,
            false,
        )
    }

    fn new_with_concurrency_and_path_mode(
        storage_id: StorageId,
        backend: Arc<dyn StorageBackend + Send + Sync>,
        root: String,
        directory_concurrency: usize,
        preserve_raw_paths: bool,
    ) -> Arc<Self> {
        assert!(
            (MIN_DIRECTORY_CONCURRENCY..=MAX_DIRECTORY_CONCURRENCY)
                .contains(&directory_concurrency),
            "directory concurrency must be between {MIN_DIRECTORY_CONCURRENCY} and {MAX_DIRECTORY_CONCURRENCY}"
        );
        let (sender, receiver) = mpsc::channel(SCAN_CHANNEL_CAPACITY);
        let stats = Arc::new(RemoteMusicScanStats::default());
        let cancelled = Arc::new(AtomicBool::new(false));
        let cancel_notify = Arc::new(Notify::new());
        std::mem::drop(tokio_runtime().spawn(run_directory_scan(DirectoryScanArgs {
            backend,
            root,
            directory_concurrency,
            sender,
            stats: Arc::clone(&stats),
            cancelled: Arc::clone(&cancelled),
            cancel_notify: Arc::clone(&cancel_notify),
            preserve_raw_paths,
        })));
        Arc::new(Self {
            storage_id,
            receiver: Mutex::new(receiver),
            pending_failure: Mutex::new(None),
            stats,
            cancelled,
            done: AtomicBool::new(false),
            cancel_notify,
            directory_concurrency: directory_concurrency as u32,
        })
    }

    fn batch(&self, entries: Vec<StorageEntry>) -> RemoteMusicScanBatch {
        RemoteMusicScanBatch {
            entries,
            done: self.done.load(Ordering::Acquire),
            cancelled: self.cancelled.load(Ordering::Acquire),
            directory_request_count: self.stats.directory_request_count.load(Ordering::Acquire),
            listed_directory_count: self.stats.listed_directory_count.load(Ordering::Acquire),
            visited_entry_count: self.stats.visited_entry_count.load(Ordering::Acquire),
            directory_concurrency: self.directory_concurrency,
        }
    }
}

async fn run_directory_scan(args: DirectoryScanArgs) {
    let DirectoryScanArgs {
        backend,
        root,
        directory_concurrency,
        sender,
        stats,
        cancelled,
        cancel_notify,
        preserve_raw_paths,
    } = args;
    // One coordinator owns queue/dedup state. Directory futures own only their
    // path and backend handle, so no scan-state lock is held across network I/O.
    let root = if preserve_raw_paths {
        root
    } else {
        canonical_directory_path(&root)
    };
    let mut directories = VecDeque::from([DirectoryTask {
        path: root.clone(),
        retry_count: 0,
    }]);
    let mut scheduled_directories = HashSet::from([root]);
    let mut active: FuturesUnordered<DirectoryFuture> = FuturesUnordered::new();

    loop {
        if cancelled.load(Ordering::Acquire) {
            return;
        }
        while active.len() < directory_concurrency {
            let Some(task) = directories.pop_front() else {
                break;
            };
            let backend = Arc::clone(&backend);
            let stats = Arc::clone(&stats);
            active.push(Box::pin(async move {
                if task.retry_count > 0 {
                    tokio::time::sleep(DIRECTORY_RETRY_DELAYS[(task.retry_count - 1) as usize])
                        .await;
                }
                stats.directory_request_count.fetch_add(1, Ordering::AcqRel);
                let result = backend.list(task.path.clone()).await;
                (task, result)
            }));
        }

        if active.is_empty() {
            let _ = sender.send(ScanMessage::Done).await;
            return;
        }

        let completed = tokio::select! {
            completed = active.next() => completed,
            _ = cancel_notify.notified() => None,
        };
        if cancelled.load(Ordering::Acquire) {
            return;
        }
        let Some((task, result)) = completed else {
            return;
        };
        match result {
            Ok(entries) => {
                stats.listed_directory_count.fetch_add(1, Ordering::AcqRel);
                for entry in entries {
                    let visited = stats.visited_entry_count.fetch_add(1, Ordering::AcqRel) + 1;
                    if visited > MAX_SCAN_ENTRIES {
                        let _ = sender
                            .send(ScanMessage::Failed(ScanFailure::Message(format!(
                                "remote scan exceeded the {MAX_SCAN_ENTRIES} entry safety limit"
                            ))))
                            .await;
                        return;
                    }
                    if entry.is_dir {
                        let path = if preserve_raw_paths {
                            entry.path.clone()
                        } else {
                            canonical_directory_path(&entry.path)
                        };
                        if scheduled_directories.insert(path.clone()) {
                            directories.push_back(DirectoryTask {
                                path,
                                retry_count: 0,
                            });
                        }
                    } else if is_supported_music_entry(&entry, preserve_raw_paths) {
                        let sent = tokio::select! {
                            sent = sender.send(ScanMessage::Entry(entry)) => sent.is_ok(),
                            _ = cancel_notify.notified() => false,
                        };
                        if !sent {
                            return;
                        }
                    }
                }
            }
            Err(error) if error.is_retryable() && task.retry_count < MAX_DIRECTORY_RETRIES => {
                directories.push_back(DirectoryTask {
                    path: task.path,
                    retry_count: task.retry_count + 1,
                });
            }
            Err(error) => {
                let _ = sender
                    .send(ScanMessage::Failed(ScanFailure::Backend(error)))
                    .await;
                return;
            }
        }
    }
}

fn canonical_directory_path(path: &str) -> String {
    let normalized = path.replace('\\', "/");
    let decoded = urlencoding::decode(&normalized)
        .map(|value| value.into_owned())
        .unwrap_or(normalized);
    let with_root = if decoded.starts_with('/') {
        decoded
    } else {
        format!("/{decoded}")
    };
    if with_root == "/" {
        with_root
    } else {
        with_root.trim_end_matches('/').to_string()
    }
}

fn scan_failure_to_error(failure: ScanFailure) -> BError {
    match failure {
        ScanFailure::Backend(error) => error.into(),
        ScanFailure::Message(message) => BError::CustomError { message },
    }
}

pub(crate) fn storage_entry(storage_id: StorageId, entry: Entry) -> StorageEntry {
    StorageEntry {
        storage_id,
        name: entry.name,
        path: entry.path,
        size: entry.size.map(|size| size as u64),
        is_dir: entry.is_dir,
        remote_id: entry.remote_id,
        parent_remote_id: entry.parent_remote_id,
        mime_type: entry.mime_type,
        etag: entry.etag,
        ctag: entry.ctag,
        created_at: entry.created_at,
        modified_at: entry.modified_at,
    }
}

pub(crate) fn is_supported_music_path(path: &str) -> bool {
    let lower_path = path.to_ascii_lowercase();
    [
        ".mp3", ".flac", ".m4a", ".mp4", ".aac", ".ogg", ".oga", ".opus", ".wav", ".ape", ".wv",
        ".aif", ".aiff",
    ]
    .iter()
    .any(|suffix| lower_path.ends_with(suffix))
}

fn is_supported_music_entry(entry: &Entry, openlist_mode: bool) -> bool {
    let mime = entry
        .mime_type
        .as_deref()
        .unwrap_or_default()
        .trim()
        .to_ascii_lowercase();
    !mime.starts_with("video/")
        && (is_supported_music_path(&entry.path) || (openlist_mode && mime.starts_with("audio/")))
}

#[cfg(test)]
mod tests {
    use std::{
        collections::HashMap,
        sync::{
            atomic::{AtomicUsize, Ordering as AtomicOrdering},
            Mutex as StdMutex,
        },
        time::{Duration, Instant},
    };

    use axum::{
        body::Body,
        extract::State,
        http::{Request, Response, StatusCode},
        routing::any,
        Router,
    };
    use bytes::Bytes;
    use futures_util::future::BoxFuture;
    use storage_backend::{
        BuildWebdavArg, ByteRange, RangeResponse, StorageBackendError, StorageBackendResult,
        StreamFile, Webdav,
    };

    use super::*;

    macro_rules! storage_read_stubs {
        () => {
            fn get(
                &self,
                _path: String,
                _byte_offset: u64,
            ) -> BoxFuture<'_, StorageBackendResult<StreamFile>> {
                Box::pin(async {
                    Err(StorageBackendError::UrlParseError(
                        "not implemented for scan test".to_string(),
                    ))
                })
            }

            fn get_range_response(
                &self,
                _path: String,
                _range: ByteRange,
            ) -> BoxFuture<'_, StorageBackendResult<RangeResponse>> {
                Box::pin(async {
                    Ok(RangeResponse {
                        bytes: Bytes::new(),
                        total_size: 1,
                        content_type: None,
                    })
                })
            }
        };
    }

    struct MemoryStorage {
        directories: HashMap<String, Vec<Entry>>,
    }

    impl StorageBackend for MemoryStorage {
        fn list(&self, dir: String) -> BoxFuture<'_, StorageBackendResult<Vec<Entry>>> {
            Box::pin(async move {
                self.directories
                    .get(&dir)
                    .cloned()
                    .ok_or_else(|| StorageBackendError::UrlParseError(dir))
            })
        }

        fn get(
            &self,
            _path: String,
            _byte_offset: u64,
        ) -> BoxFuture<'_, StorageBackendResult<StreamFile>> {
            Box::pin(async {
                Err(StorageBackendError::UrlParseError(
                    "not implemented for scan test".to_string(),
                ))
            })
        }

        fn get_range_response(
            &self,
            _path: String,
            _range: ByteRange,
        ) -> BoxFuture<'_, StorageBackendResult<RangeResponse>> {
            Box::pin(async {
                Ok(RangeResponse {
                    bytes: Bytes::new(),
                    total_size: 1,
                    content_type: None,
                })
            })
        }
    }

    #[tokio::test]
    async fn scans_music_in_bounded_batches_and_can_cancel() {
        let storage = Arc::new(MemoryStorage {
            directories: HashMap::from([
                (
                    "/".to_string(),
                    vec![
                        entry("/Album", true),
                        entry("/root.mp3", false),
                        entry("/readme.txt", false),
                    ],
                ),
                (
                    "/Album".to_string(),
                    vec![
                        entry("/Album/song.flac", false),
                        entry("/Album/cover.jpg", false),
                    ],
                ),
            ]),
        });
        let session = RemoteMusicScanSession::new(StorageId::wrap(7), storage, "/".to_string());

        let first = session.next_batch(1).await.unwrap();
        assert_eq!(first.entries.len(), 1);
        assert_eq!(first.entries[0].path, "/root.mp3");
        assert!(!first.done);

        let second = session.next_batch(1).await.unwrap();
        assert_eq!(second.entries.len(), 1);
        assert_eq!(second.entries[0].path, "/Album/song.flac");
        assert!(!second.done);

        session.cancel();
        let cancelled = session.next_batch(1).await.unwrap();
        assert!(cancelled.entries.is_empty());
        assert!(cancelled.done);
        assert!(cancelled.cancelled);
    }

    #[tokio::test]
    async fn cancellation_interrupts_an_in_flight_directory_request() {
        let session =
            RemoteMusicScanSession::new(StorageId::wrap(7), Arc::new(SlowStorage), "/".to_string());
        let scanning = {
            let session = session.clone();
            tokio::spawn(async move { session.next_batch(10).await.unwrap() })
        };
        tokio::task::yield_now().await;
        session.cancel();

        let cancelled = tokio::time::timeout(std::time::Duration::from_secs(1), scanning)
            .await
            .expect("scan cancellation should not wait for the remote timeout")
            .unwrap();
        assert!(cancelled.done);
        assert!(cancelled.cancelled);
        assert!(cancelled.entries.is_empty());
    }

    #[tokio::test]
    async fn scans_directories_concurrently_without_exceeding_limit() {
        let directories = HashMap::from_iter(
            std::iter::once((
                "/".to_string(),
                (0..8)
                    .map(|index| entry(&format!("/Album-{index}"), true))
                    .collect(),
            ))
            .chain((0..8).map(|index| {
                (
                    format!("/Album-{index}"),
                    vec![entry(&format!("/Album-{index}/song-{index}.flac"), false)],
                )
            })),
        );
        let storage = Arc::new(ConcurrentStorage {
            directories,
            active: AtomicUsize::new(0),
            max_active: AtomicUsize::new(0),
        });
        let session = RemoteMusicScanSession::new_with_concurrency(
            StorageId::wrap(7),
            storage.clone(),
            "/".to_string(),
            4,
        );

        let batch = session.next_batch(100).await.unwrap();

        assert!(batch.done);
        assert_eq!(batch.entries.len(), 8);
        assert!(storage.max_active.load(AtomicOrdering::Acquire) > 1);
        assert!(storage.max_active.load(AtomicOrdering::Acquire) <= 4);
        assert_eq!(batch.directory_concurrency, 4);
        assert_eq!(batch.listed_directory_count, 9);
        assert_eq!(batch.directory_request_count, 9);
    }

    #[tokio::test]
    async fn parallel_scan_is_at_least_forty_percent_faster_for_large_library() {
        let directories = benchmark_library(100, 10);

        let serial_started = Instant::now();
        let serial = drain_scan(RemoteMusicScanSession::new_with_concurrency(
            StorageId::wrap(7),
            Arc::new(LatencyStorage {
                directories: directories.clone(),
                latency: Duration::from_millis(8),
            }),
            "/".to_string(),
            1,
        ))
        .await;
        let serial_elapsed = serial_started.elapsed();

        let parallel_started = Instant::now();
        let parallel = drain_scan(RemoteMusicScanSession::new_with_concurrency(
            StorageId::wrap(7),
            Arc::new(LatencyStorage {
                directories,
                latency: Duration::from_millis(8),
            }),
            "/".to_string(),
            4,
        ))
        .await;
        let parallel_elapsed = parallel_started.elapsed();

        println!(
            "webdav scan baseline: serial_ms={}, parallel_ms={}, files={}",
            serial_elapsed.as_millis(),
            parallel_elapsed.as_millis(),
            parallel.entries.len()
        );
        assert_eq!(serial.entries.len(), 1_000);
        assert_eq!(parallel.entries.len(), 1_000);
        assert_eq!(serial.directory_request_count, 101);
        assert_eq!(parallel.directory_request_count, 101);
        assert!(
            parallel_elapsed.mul_f32(1.0 / 0.6) <= serial_elapsed,
            "parallel scan should take no more than 60% of serial time: serial={serial_elapsed:?}, parallel={parallel_elapsed:?}"
        );
    }

    #[tokio::test]
    async fn local_webdav_1000_track_scan_is_bounded_and_faster_in_parallel() {
        let (address, fixture, server) = spawn_webdav_fixture(Duration::from_millis(8));

        let serial_started = Instant::now();
        let serial = drain_scan(RemoteMusicScanSession::new_with_concurrency(
            StorageId::wrap(7),
            Arc::new(webdav_backend(address.clone())),
            "/".to_string(),
            1,
        ))
        .await;
        let serial_elapsed = serial_started.elapsed();
        assert_eq!(fixture.max_active.load(AtomicOrdering::Acquire), 1);

        fixture.max_active.store(0, AtomicOrdering::Release);
        fixture.request_count.store(0, AtomicOrdering::Release);
        let parallel_started = Instant::now();
        let parallel = drain_scan(RemoteMusicScanSession::new_with_concurrency(
            StorageId::wrap(7),
            Arc::new(webdav_backend(address)),
            "/".to_string(),
            4,
        ))
        .await;
        let parallel_elapsed = parallel_started.elapsed();
        let max_active = fixture.max_active.load(AtomicOrdering::Acquire);
        server.abort();

        println!(
            "local webdav baseline: serial_ms={}, parallel_ms={}, requests={}, files={}",
            serial_elapsed.as_millis(),
            parallel_elapsed.as_millis(),
            fixture.request_count.load(AtomicOrdering::Acquire),
            parallel.entries.len()
        );
        assert_eq!(serial.entries.len(), 1_000);
        assert_eq!(parallel.entries.len(), 1_000);
        assert_eq!(parallel.directory_request_count, 101);
        assert_eq!(fixture.request_count.load(AtomicOrdering::Acquire), 101);
        assert!(max_active > 1);
        assert!(max_active <= 4);
        assert!(
            parallel_elapsed.mul_f32(1.0 / 0.6) <= serial_elapsed,
            "parallel WebDAV scan should take no more than 60% of serial time: serial={serial_elapsed:?}, parallel={parallel_elapsed:?}"
        );
    }

    #[tokio::test]
    async fn first_batch_arrives_before_the_complete_tree_finishes() {
        let session = RemoteMusicScanSession::new_with_concurrency(
            StorageId::wrap(7),
            Arc::new(OutOfOrderStorage),
            "/".to_string(),
            4,
        );
        let started = Instant::now();

        let first = session.next_batch(1).await.unwrap();

        assert_eq!(first.entries.len(), 1);
        assert_eq!(first.entries[0].path, "/fast/song.flac");
        assert!(!first.done);
        assert!(started.elapsed() < Duration::from_millis(40));
    }

    #[tokio::test]
    async fn duplicate_encoded_or_trailed_directories_are_scanned_once() {
        let storage = Arc::new(CountingStorage {
            calls: StdMutex::new(HashMap::new()),
        });
        let session = RemoteMusicScanSession::new_with_concurrency(
            StorageId::wrap(7),
            storage.clone(),
            "/".to_string(),
            4,
        );

        let batch = session.next_batch(100).await.unwrap();

        assert!(batch.done);
        assert_eq!(batch.entries.len(), 1);
        let calls = storage.calls.lock().unwrap();
        assert_eq!(calls.get("/").copied(), Some(1));
        assert_eq!(calls.get("/Album One").copied(), Some(1));
    }

    #[tokio::test]
    async fn openlist_recursive_scan_preserves_raw_paths_and_generic_mode_does_not() {
        let raw_dir = "/音乐/%25 #? 😀\\folder".to_string();
        let raw_track = format!("{raw_dir}/无扩展曲目");
        let calls = Arc::new(StdMutex::new(Vec::new()));
        let storage = Arc::new(RawMemoryStorage {
            directories: HashMap::from([
                ("/".to_string(), vec![entry(&raw_dir, true)]),
                (raw_dir.clone(), vec![audio_entry(&raw_track)]),
            ]),
            calls: Arc::clone(&calls),
        });
        let session =
            RemoteMusicScanSession::new_openlist(StorageId::wrap(7), storage, "/".to_string());
        let batch = drain_scan(session).await;
        assert_eq!(batch.entries.len(), 1);
        assert_eq!(batch.entries[0].path, raw_track);
        assert_eq!(
            calls.lock().unwrap().as_slice(),
            ["/".to_string(), raw_dir.clone()].as_slice()
        );

        let generic_storage = Arc::new(RawMemoryStorage {
            directories: HashMap::from([
                ("/".to_string(), vec![entry(&raw_dir, true)]),
                (raw_dir, vec![audio_entry(&raw_track)]),
            ]),
            calls: Arc::new(StdMutex::new(Vec::new())),
        });
        let generic = RemoteMusicScanSession::new(StorageId::wrap(7), generic_storage, "/".into());
        assert!(generic.next_batch(10).await.is_err());
    }

    #[tokio::test]
    async fn transient_directory_failure_retries_then_succeeds() {
        let storage = Arc::new(RetryStorage {
            attempts: AtomicUsize::new(0),
            failures_before_success: 2,
        });
        let session = RemoteMusicScanSession::new_with_concurrency(
            StorageId::wrap(7),
            storage.clone(),
            "/".to_string(),
            1,
        );

        let batch = session.next_batch(10).await.unwrap();

        assert!(batch.done);
        assert_eq!(batch.entries.len(), 1);
        assert_eq!(storage.attempts.load(AtomicOrdering::Acquire), 3);
        assert_eq!(batch.directory_request_count, 3);
        assert_eq!(batch.listed_directory_count, 1);
    }

    #[tokio::test]
    async fn transient_directory_failure_stops_after_retry_limit() {
        let storage = Arc::new(RetryStorage {
            attempts: AtomicUsize::new(0),
            failures_before_success: usize::MAX,
        });
        let session = RemoteMusicScanSession::new_with_concurrency(
            StorageId::wrap(7),
            storage.clone(),
            "/".to_string(),
            1,
        );

        let result = session.next_batch(10).await;

        assert!(result.is_err());
        assert_eq!(storage.attempts.load(AtomicOrdering::Acquire), 3);
    }

    #[tokio::test]
    async fn directory_completion_order_does_not_omit_files() {
        let storage = Arc::new(OutOfOrderStorage);
        let session = RemoteMusicScanSession::new_with_concurrency(
            StorageId::wrap(7),
            storage,
            "/".to_string(),
            4,
        );

        let batch = session.next_batch(10).await.unwrap();
        assert!(batch.done);
        let mut paths = batch
            .entries
            .into_iter()
            .map(|entry| entry.path)
            .collect::<Vec<_>>();
        paths.sort();

        assert_eq!(paths, vec!["/fast/song.flac", "/slow/song.flac"]);
    }

    #[tokio::test]
    async fn entry_limit_fails_instead_of_silently_truncating() {
        let entries = (0..=MAX_SCAN_ENTRIES)
            .map(|index| entry(&format!("/ignored-{index}.txt"), false))
            .collect();
        let storage = Arc::new(MemoryStorage {
            directories: HashMap::from([("/".to_string(), entries)]),
        });
        let session = RemoteMusicScanSession::new(StorageId::wrap(7), storage, "/".to_string());

        let error = session.next_batch(10).await.unwrap_err();

        assert!(error.to_string().contains("100000 entry safety limit"));
    }

    struct ConcurrentStorage {
        directories: HashMap<String, Vec<Entry>>,
        active: AtomicUsize,
        max_active: AtomicUsize,
    }

    struct LatencyStorage {
        directories: HashMap<String, Vec<Entry>>,
        latency: Duration,
    }

    impl StorageBackend for LatencyStorage {
        fn list(&self, dir: String) -> BoxFuture<'_, StorageBackendResult<Vec<Entry>>> {
            Box::pin(async move {
                tokio::time::sleep(self.latency).await;
                self.directories
                    .get(&dir)
                    .cloned()
                    .ok_or_else(|| StorageBackendError::UrlParseError(dir))
            })
        }

        storage_read_stubs!();
    }

    impl StorageBackend for ConcurrentStorage {
        fn list(&self, dir: String) -> BoxFuture<'_, StorageBackendResult<Vec<Entry>>> {
            Box::pin(async move {
                let active = self.active.fetch_add(1, AtomicOrdering::AcqRel) + 1;
                self.max_active.fetch_max(active, AtomicOrdering::AcqRel);
                tokio::time::sleep(Duration::from_millis(25)).await;
                self.active.fetch_sub(1, AtomicOrdering::AcqRel);
                self.directories
                    .get(&dir)
                    .cloned()
                    .ok_or_else(|| StorageBackendError::UrlParseError(dir))
            })
        }

        storage_read_stubs!();
    }

    struct CountingStorage {
        calls: StdMutex<HashMap<String, usize>>,
    }

    struct RawMemoryStorage {
        directories: HashMap<String, Vec<Entry>>,
        calls: Arc<StdMutex<Vec<String>>>,
    }

    impl StorageBackend for RawMemoryStorage {
        fn list(&self, dir: String) -> BoxFuture<'_, StorageBackendResult<Vec<Entry>>> {
            Box::pin(async move {
                self.calls.lock().unwrap().push(dir.clone());
                self.directories
                    .get(&dir)
                    .cloned()
                    .ok_or_else(|| StorageBackendError::UrlParseError("missing raw path".into()))
            })
        }

        storage_read_stubs!();
    }

    impl StorageBackend for CountingStorage {
        fn list(&self, dir: String) -> BoxFuture<'_, StorageBackendResult<Vec<Entry>>> {
            Box::pin(async move {
                *self.calls.lock().unwrap().entry(dir.clone()).or_default() += 1;
                match dir.as_str() {
                    "/" => Ok(vec![
                        entry("/Album%20One", true),
                        entry("/Album One/", true),
                    ]),
                    "/Album One" => Ok(vec![entry("/Album One/song.flac", false)]),
                    _ => Err(StorageBackendError::UrlParseError(dir)),
                }
            })
        }

        storage_read_stubs!();
    }

    struct RetryStorage {
        attempts: AtomicUsize,
        failures_before_success: usize,
    }

    impl StorageBackend for RetryStorage {
        fn list(&self, _dir: String) -> BoxFuture<'_, StorageBackendResult<Vec<Entry>>> {
            Box::pin(async move {
                let attempt = self.attempts.fetch_add(1, AtomicOrdering::AcqRel);
                if attempt < self.failures_before_success {
                    Err(StorageBackendError::TokioIO(std::io::Error::new(
                        std::io::ErrorKind::TimedOut,
                        "simulated timeout",
                    )))
                } else {
                    Ok(vec![entry("/song.flac", false)])
                }
            })
        }

        storage_read_stubs!();
    }

    struct OutOfOrderStorage;

    impl StorageBackend for OutOfOrderStorage {
        fn list(&self, dir: String) -> BoxFuture<'_, StorageBackendResult<Vec<Entry>>> {
            Box::pin(async move {
                match dir.as_str() {
                    "/" => Ok(vec![entry("/slow", true), entry("/fast", true)]),
                    "/slow" => {
                        tokio::time::sleep(Duration::from_millis(50)).await;
                        Ok(vec![entry("/slow/song.flac", false)])
                    }
                    "/fast" => {
                        tokio::time::sleep(Duration::from_millis(5)).await;
                        Ok(vec![entry("/fast/song.flac", false)])
                    }
                    _ => Err(StorageBackendError::UrlParseError(dir)),
                }
            })
        }

        storage_read_stubs!();
    }

    struct SlowStorage;

    impl StorageBackend for SlowStorage {
        fn list(&self, _dir: String) -> BoxFuture<'_, StorageBackendResult<Vec<Entry>>> {
            Box::pin(async {
                std::future::pending::<()>().await;
                Ok(Vec::new())
            })
        }

        fn get(
            &self,
            _path: String,
            _byte_offset: u64,
        ) -> BoxFuture<'_, StorageBackendResult<StreamFile>> {
            Box::pin(async {
                Err(StorageBackendError::UrlParseError(
                    "not implemented for scan test".to_string(),
                ))
            })
        }

        fn get_range_response(
            &self,
            _path: String,
            _range: ByteRange,
        ) -> BoxFuture<'_, StorageBackendResult<RangeResponse>> {
            Box::pin(async {
                Ok(RangeResponse {
                    bytes: Bytes::new(),
                    total_size: 1,
                    content_type: None,
                })
            })
        }
    }

    struct WebDavFixture {
        latency: Duration,
        active: AtomicUsize,
        max_active: AtomicUsize,
        request_count: AtomicUsize,
    }

    fn spawn_webdav_fixture(
        latency: Duration,
    ) -> (String, Arc<WebDavFixture>, tokio::task::JoinHandle<()>) {
        let fixture = Arc::new(WebDavFixture {
            latency,
            active: AtomicUsize::new(0),
            max_active: AtomicUsize::new(0),
            request_count: AtomicUsize::new(0),
        });
        let app = Router::new()
            .fallback(any(webdav_fixture_response))
            .with_state(Arc::clone(&fixture));
        let listener = std::net::TcpListener::bind((std::net::Ipv4Addr::LOCALHOST, 0)).unwrap();
        listener.set_nonblocking(true).unwrap();
        let address = format!("http://{}", listener.local_addr().unwrap());
        let server = tokio::spawn(async move {
            axum::Server::from_tcp(listener)
                .unwrap()
                .serve(app.into_make_service())
                .await
                .unwrap();
        });
        (address, fixture, server)
    }

    async fn webdav_fixture_response(
        State(fixture): State<Arc<WebDavFixture>>,
        request: Request<Body>,
    ) -> Response<Body> {
        if request.method().as_str() != "PROPFIND" {
            return Response::builder()
                .status(StatusCode::METHOD_NOT_ALLOWED)
                .body(Body::empty())
                .unwrap();
        }
        fixture.request_count.fetch_add(1, AtomicOrdering::AcqRel);
        let active = fixture.active.fetch_add(1, AtomicOrdering::AcqRel) + 1;
        fixture.max_active.fetch_max(active, AtomicOrdering::AcqRel);
        tokio::time::sleep(fixture.latency).await;
        fixture.active.fetch_sub(1, AtomicOrdering::AcqRel);

        let path = request.uri().path().trim_end_matches('/');
        let path = if path.is_empty() { "/" } else { path };
        let responses = if path == "/" {
            std::iter::once(webdav_xml_item("/", "Music", true))
                .chain((0..100).map(|album| {
                    webdav_xml_item(&format!("/Album-{album}/"), &format!("Album-{album}"), true)
                }))
                .collect::<String>()
        } else if path.starts_with("/Album-") {
            let album = path.trim_start_matches("/Album-");
            std::iter::once(webdav_xml_item(
                &format!("{path}/"),
                &format!("Album-{album}"),
                true,
            ))
            .chain((0..10).map(|track| {
                webdav_xml_item(
                    &format!("{path}/Track-{track}.flac"),
                    &format!("Track-{track}.flac"),
                    false,
                )
            }))
            .collect::<String>()
        } else {
            String::new()
        };
        Response::builder()
            .status(StatusCode::from_u16(207).unwrap())
            .header("content-type", "application/xml")
            .body(Body::from(format!(
                "<D:multistatus xmlns:D=\"DAV:\">{responses}</D:multistatus>"
            )))
            .unwrap()
    }

    fn webdav_xml_item(href: &str, display_name: &str, is_dir: bool) -> String {
        let resource_type = if is_dir { "<D:collection/>" } else { "" };
        let content_length = if is_dir {
            String::new()
        } else {
            "<D:getcontentlength>1024</D:getcontentlength>".to_string()
        };
        format!(
            "<D:response><D:href>{href}</D:href><D:propstat><D:prop><D:displayname>{display_name}</D:displayname><D:resourcetype>{resource_type}</D:resourcetype>{content_length}<D:getcontenttype>audio/flac</D:getcontenttype><D:getetag>W/&quot;stable&quot;</D:getetag><D:getlastmodified>Wed, 15 Jul 2026 12:00:00 GMT</D:getlastmodified></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>"
        )
    }

    fn webdav_backend(address: String) -> Webdav {
        Webdav::new(BuildWebdavArg {
            addr: address,
            username: String::new(),
            password: String::new(),
            is_anonymous: true,
            connect_timeout: Duration::from_secs(2),
        })
    }

    fn benchmark_library(
        album_count: usize,
        tracks_per_album: usize,
    ) -> HashMap<String, Vec<Entry>> {
        HashMap::from_iter(
            std::iter::once((
                "/".to_string(),
                (0..album_count)
                    .map(|album| entry(&format!("/Album-{album}"), true))
                    .collect(),
            ))
            .chain((0..album_count).map(|album| {
                let album_path = format!("/Album-{album}");
                let tracks = (0..tracks_per_album)
                    .map(|track| entry(&format!("{album_path}/Track-{track}.flac"), false))
                    .collect();
                (album_path, tracks)
            })),
        )
    }

    async fn drain_scan(session: Arc<RemoteMusicScanSession>) -> RemoteMusicScanBatch {
        let mut entries = Vec::new();
        loop {
            let mut batch = session.next_batch(MAX_SCAN_BATCH_FILES).await.unwrap();
            entries.append(&mut batch.entries);
            if batch.done {
                batch.entries = entries;
                return batch;
            }
        }
    }

    fn entry(path: &str, is_dir: bool) -> Entry {
        Entry {
            name: path.rsplit('/').next().unwrap_or_default().to_string(),
            path: path.to_string(),
            size: (!is_dir).then_some(100),
            is_dir,
            remote_id: None,
            parent_remote_id: None,
            mime_type: None,
            etag: None,
            ctag: None,
            created_at: None,
            modified_at: None,
        }
    }

    fn audio_entry(path: &str) -> Entry {
        let mut value = entry(path, false);
        value.mime_type = Some("audio/flac".to_string());
        value
    }

    #[test]
    fn scan_excludes_video_mp4_and_keeps_audio_mp4() {
        let mut video = entry("/Photos/clip.mp4", false);
        video.mime_type = Some("video/mp4".to_string());
        assert!(!is_supported_music_entry(&video, false));

        let mut audio = entry("/Music/track.mp4", false);
        audio.mime_type = Some("audio/mp4".to_string());
        assert!(is_supported_music_entry(&audio, false));

        let unknown = entry("/Music/legacy.mp4", false);
        assert!(is_supported_music_entry(&unknown, false));
    }

    #[test]
    fn openlist_mode_allows_audio_mime_without_changing_generic_filter() {
        let mut opaque = entry("/音乐/%25 #? 😀\\track", false);
        opaque.mime_type = Some("audio/flac".to_string());
        assert!(!is_supported_music_entry(&opaque, false));
        assert!(is_supported_music_entry(&opaque, true));
        assert!(opaque.path.contains("%25 #? 😀\\track"));
    }
}
