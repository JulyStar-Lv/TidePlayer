use std::{
    fmt,
    future::Future,
    io::ErrorKind as IoErrorKind,
    num::NonZeroUsize,
    sync::{
        atomic::{AtomicUsize, Ordering},
        Arc,
    },
    time::{Duration, SystemTime},
};

use async_runtime::tokio_runtime;
use bytes::Bytes;
use futures_util::future::BoxFuture;
use lru::LruCache;
use sha2::{Digest, Sha256};
use smb2::{
    client::{stream::FileReader, Cipher},
    ClientConfig, DirectoryEntry, ErrorKind as SmbErrorKind, SmbClient, Tree,
};
use tokio::sync::Mutex;

use crate::{
    ByteRange, Entry, RangeResponse, StorageBackend, StorageBackendError, StorageBackendResult,
    StreamFile,
};

const DEFAULT_SMB_PORT: u16 = 445;
const SESSION_POOL_SIZE: usize = 4;
const LIST_SESSION_START: usize = 1;
const READER_CACHE_CAPACITY: usize = 8;
const STREAM_CHUNK_SIZE: u64 = 512 * 1024;
const STREAM_CHANNEL_CAPACITY: usize = 2;
const MAX_OPERATION_RETRIES: usize = 2;
const RETRY_DELAYS: [Duration; MAX_OPERATION_RETRIES] =
    [Duration::from_millis(200), Duration::from_millis(400)];

struct AbortTaskOnDrop(Option<tokio::task::AbortHandle>);

impl Drop for AbortTaskOnDrop {
    fn drop(&mut self) {
        if let Some(handle) = self.0.take() {
            handle.abort();
        }
    }
}

#[derive(Clone, PartialEq, Eq)]
pub struct BuildSmbArg {
    pub host: String,
    pub port: u16,
    pub share: String,
    pub root_path: String,
    pub domain: Option<String>,
    pub username: String,
    pub password: String,
    pub is_guest: bool,
    pub require_signing: bool,
    pub require_encryption: bool,
    pub connect_timeout: Duration,
}

impl fmt::Debug for BuildSmbArg {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_struct("BuildSmbArg")
            .field("host", &self.host)
            .field("port", &self.port)
            .field("share", &self.share)
            .field("root_path", &self.root_path)
            .field("domain", &self.domain)
            .field("username", &self.username)
            .field("password", &"<redacted>")
            .field("is_guest", &self.is_guest)
            .field("require_signing", &self.require_signing)
            .field("require_encryption", &self.require_encryption)
            .field("connect_timeout", &self.connect_timeout)
            .finish()
    }
}

impl BuildSmbArg {
    pub fn from_url(
        address: &str,
        username: String,
        password: String,
        is_guest: bool,
        connect_timeout: Duration,
    ) -> StorageBackendResult<Self> {
        validate_raw_url_path(address)?;
        let url = reqwest::Url::parse(address)
            .map_err(|error| StorageBackendError::InvalidPath(error.to_string()))?;
        if url.scheme() != "smb" {
            return Err(StorageBackendError::InvalidPath(
                "SMB address must use smb://".to_string(),
            ));
        }
        if !url.username().is_empty() || url.password().is_some() {
            return Err(StorageBackendError::InvalidPath(
                "credentials must not be embedded in an SMB address".to_string(),
            ));
        }
        if url.fragment().is_some() {
            return Err(StorageBackendError::InvalidPath(
                "SMB addresses must not contain a fragment".to_string(),
            ));
        }

        let host = url
            .host_str()
            .filter(|host| !host.is_empty())
            .ok_or_else(|| StorageBackendError::InvalidPath("SMB host is required".to_string()))?
            .to_string();
        let port = url.port().unwrap_or(DEFAULT_SMB_PORT);
        if port == 0 {
            return Err(StorageBackendError::InvalidPath(
                "SMB port must be between 1 and 65535".to_string(),
            ));
        }

        let decoded_path = urlencoding::decode(url.path())
            .map_err(|error| StorageBackendError::InvalidPath(error.to_string()))?
            .into_owned();
        let normalized_path = normalize_relative_path(&decoded_path)?;
        let mut path_segments = normalized_path
            .split('/')
            .filter(|segment| !segment.is_empty());
        let share = path_segments.next().unwrap_or_default().to_string();
        let root_path = path_segments.collect::<Vec<_>>().join("/");

        let mut domain = None;
        let mut require_signing = false;
        let mut require_encryption = false;
        for (key, value) in url.query_pairs() {
            match key.as_ref() {
                "domain" | "workgroup" => {
                    if domain.as_deref().is_some_and(|current| current != value) {
                        return Err(StorageBackendError::InvalidPath(
                            "SMB domain and workgroup disagree".to_string(),
                        ));
                    }
                    domain = non_empty(value.into_owned());
                }
                "signing" => require_signing = parse_bool_query("signing", &value)?,
                "encryption" => require_encryption = parse_bool_query("encryption", &value)?,
                "username" | "password" => {
                    return Err(StorageBackendError::InvalidPath(
                        "credentials must not be embedded in an SMB address".to_string(),
                    ));
                }
                other => {
                    return Err(StorageBackendError::InvalidPath(format!(
                        "unsupported SMB address option: {other}"
                    )));
                }
            }
        }

        Self::validated(Self {
            host,
            port,
            share,
            root_path,
            domain,
            username,
            password,
            is_guest,
            require_signing,
            require_encryption,
            connect_timeout,
        })
    }

    pub fn validated(mut self) -> StorageBackendResult<Self> {
        self.host = validate_component("host", &self.host)?;
        self.share = if self.share.trim().is_empty() {
            String::new()
        } else {
            validate_path_component("share", &self.share)?
        };
        self.root_path = normalize_relative_path(&self.root_path)?;
        self.domain = self
            .domain
            .take()
            .map(|domain| validate_component("domain", &domain))
            .transpose()?
            .and_then(non_empty);
        if self.port == 0 {
            return Err(StorageBackendError::InvalidPath(
                "SMB port must be between 1 and 65535".to_string(),
            ));
        }
        if self.connect_timeout.is_zero() {
            return Err(StorageBackendError::InvalidPath(
                "SMB connection timeout must be greater than zero".to_string(),
            ));
        }
        if self.is_guest {
            self.username.clear();
            self.password.clear();
            self.domain = None;
        } else if self.username.trim().is_empty() {
            return Err(StorageBackendError::InvalidPath(
                "SMB username is required unless Guest is enabled".to_string(),
            ));
        }
        if contains_nul(&self.username) || contains_nul(&self.password) {
            return Err(StorageBackendError::InvalidPath(
                "SMB credentials contain an illegal NUL character".to_string(),
            ));
        }
        Ok(self)
    }

    fn server_address(&self) -> String {
        if self.host.contains(':') && !self.host.starts_with('[') {
            format!("[{}]:{}", self.host, self.port)
        } else {
            format!("{}:{}", self.host, self.port)
        }
    }
}

#[derive(Clone)]
pub struct SmbBackend {
    inner: Arc<SmbBackendInner>,
}

impl fmt::Debug for SmbBackend {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_struct("SmbBackend")
            .field("host", &self.inner.arg.host)
            .field("port", &self.inner.arg.port)
            .field("share", &self.inner.arg.share)
            .field("root_path", &self.inner.arg.root_path)
            .finish_non_exhaustive()
    }
}

struct SmbBackendInner {
    arg: BuildSmbArg,
    sessions: SessionPool,
    readers: Mutex<BoundedCache<Arc<CachedReader>>>,
}

struct ConnectedSession {
    client: SmbClient,
    tree: Tree,
}

struct SessionPool {
    arg: BuildSmbArg,
    slots: Vec<Mutex<Option<ConnectedSession>>>,
    list_cursor: AtomicUsize,
}

struct CachedReader {
    reader: Mutex<Option<FileReader>>,
    size: u64,
}

struct BoundedCache<T> {
    entries: LruCache<String, T>,
}

impl<T> BoundedCache<T> {
    fn new(capacity: usize) -> Self {
        Self {
            entries: LruCache::new(
                NonZeroUsize::new(capacity).expect("reader cache capacity must be non-zero"),
            ),
        }
    }

    fn insert(&mut self, key: String, value: T) -> Option<T> {
        self.entries.push(key, value).map(|(_, value)| value)
    }

    fn remove(&mut self, key: &str) -> Option<T> {
        self.entries.pop(key)
    }

    fn get_cloned(&mut self, key: &str) -> Option<T>
    where
        T: Clone,
    {
        self.entries.get(key).cloned()
    }

    fn pop_lru(&mut self) -> Option<T> {
        self.entries.pop_lru().map(|(_, value)| value)
    }
}

impl CachedReader {
    fn new(reader: FileReader) -> Self {
        let size = reader.size();
        Self {
            reader: Mutex::new(Some(reader)),
            size,
        }
    }

    async fn read_at(&self, offset: u64, length: u64) -> StorageBackendResult<Vec<u8>> {
        let guard = self.reader.lock().await;
        let reader = guard.as_ref().ok_or(StorageBackendError::ConnectionLost)?;
        reader.read_at(offset, length).await.map_err(map_smb_error)
    }

    async fn close(&self) {
        let reader = self.reader.lock().await.take();
        if let Some(reader) = reader {
            if let Err(error) = reader.close().await {
                tracing::debug!(
                    kind = ?error.kind(),
                    "failed to close an evicted SMB reader"
                );
            }
        }
    }
}

impl Drop for SmbBackendInner {
    fn drop(&mut self) {
        let readers = self.readers.get_mut();
        while let Some(reader) = readers.pop_lru() {
            schedule_reader_close(reader);
        }
    }
}

impl SessionPool {
    fn new(arg: BuildSmbArg) -> Self {
        Self {
            arg,
            slots: (0..SESSION_POOL_SIZE).map(|_| Mutex::new(None)).collect(),
            list_cursor: AtomicUsize::new(0),
        }
    }

    async fn list_directory(&self, path: &str) -> StorageBackendResult<Vec<DirectoryEntry>> {
        let list_slot_count = SESSION_POOL_SIZE - LIST_SESSION_START;
        let slot_index =
            LIST_SESSION_START + self.list_cursor.fetch_add(1, Ordering::Relaxed) % list_slot_count;
        let slot = &self.slots[slot_index];

        for attempt in 0..=MAX_OPERATION_RETRIES {
            let mut guard = slot.lock().await;
            if guard.is_none() {
                match connect_session(&self.arg).await {
                    Ok(session) => *guard = Some(session),
                    Err(error) if should_retry(attempt, &error) => {
                        drop(guard);
                        sleep_before_retry(attempt).await;
                        continue;
                    }
                    Err(error) => return Err(error),
                }
            }

            let session = guard.as_mut().expect("SMB session initialized");
            match session.client.list_directory(&mut session.tree, path).await {
                Ok(entries) => return Ok(entries),
                Err(error) => {
                    let mapped = map_smb_error(error);
                    if should_retry(attempt, &mapped) {
                        *guard = None;
                        drop(guard);
                        sleep_before_retry(attempt).await;
                        continue;
                    }
                    return Err(mapped);
                }
            }
        }
        Err(StorageBackendError::RetryExhausted(
            "SMB directory listing retries exhausted".to_string(),
        ))
    }

    async fn open_reader(&self, path: &str) -> StorageBackendResult<FileReader> {
        let slot = &self.slots[0];

        for attempt in 0..=MAX_OPERATION_RETRIES {
            let mut guard = slot.lock().await;
            if guard.is_none() {
                match connect_session(&self.arg).await {
                    Ok(session) => *guard = Some(session),
                    Err(error) if should_retry(attempt, &error) => {
                        drop(guard);
                        sleep_before_retry(attempt).await;
                        continue;
                    }
                    Err(error) => return Err(error),
                }
            }

            let session = guard.as_ref().expect("SMB session initialized");
            match session.client.open_file_reader(&session.tree, path).await {
                Ok(reader) => return Ok(reader),
                Err(error) => {
                    let mapped = map_smb_error(error);
                    if should_retry(attempt, &mapped) {
                        *guard = None;
                        drop(guard);
                        sleep_before_retry(attempt).await;
                        continue;
                    }
                    return Err(mapped);
                }
            }
        }
        Err(StorageBackendError::RetryExhausted(
            "SMB reader open retries exhausted".to_string(),
        ))
    }

    async fn file_size(&self, path: &str) -> StorageBackendResult<u64> {
        let slot = &self.slots[0];

        for attempt in 0..=MAX_OPERATION_RETRIES {
            let mut guard = slot.lock().await;
            if guard.is_none() {
                match connect_session(&self.arg).await {
                    Ok(session) => *guard = Some(session),
                    Err(error) if should_retry(attempt, &error) => {
                        drop(guard);
                        sleep_before_retry(attempt).await;
                        continue;
                    }
                    Err(error) => return Err(error),
                }
            }

            let session = guard.as_mut().expect("SMB session initialized");
            match session.client.stat(&mut session.tree, path).await {
                Ok(info) => return Ok(info.size),
                Err(error) => {
                    let mapped = map_smb_error(error);
                    if should_retry(attempt, &mapped) {
                        *guard = None;
                        drop(guard);
                        sleep_before_retry(attempt).await;
                        continue;
                    }
                    return Err(mapped);
                }
            }
        }
        Err(StorageBackendError::RetryExhausted(
            "SMB metadata retries exhausted".to_string(),
        ))
    }
}

impl SmbBackend {
    pub fn new(arg: BuildSmbArg) -> StorageBackendResult<Self> {
        let arg = BuildSmbArg::validated(arg)?;
        if arg.share.is_empty() {
            return Err(StorageBackendError::InvalidPath(
                "SMB share is required for file access".to_string(),
            ));
        }
        Ok(Self {
            inner: Arc::new(SmbBackendInner {
                sessions: SessionPool::new(arg.clone()),
                readers: Mutex::new(BoundedCache::new(READER_CACHE_CAPACITY)),
                arg,
            }),
        })
    }

    async fn list_impl(&self, dir: String) -> StorageBackendResult<Vec<Entry>> {
        let relative_dir = normalize_relative_path(&dir)?;
        let protocol_dir = self.protocol_path(&relative_dir);
        let entries = self.inner.sessions.list_directory(&protocol_dir).await?;
        let parent_id = remote_id(
            &self.inner.arg.share,
            &self.inner.arg.root_path,
            &public_path(&relative_dir),
        );
        let mut mapped = Vec::with_capacity(entries.len());

        for entry in entries {
            if matches!(entry.name.as_str(), "." | "..") {
                continue;
            }
            if contains_illegal_path_character(&entry.name) || entry.name.contains(['/', '\\']) {
                tracing::warn!("skipping invalid SMB directory entry name");
                continue;
            }

            let relative_path = join_relative(&relative_dir, &entry.name);
            let path = public_path(&relative_path);
            let created_at = system_time_millis(entry.created.to_system_time());
            let modified_at = system_time_millis(entry.modified.to_system_time());
            let size = (!entry.is_directory)
                .then(|| usize::try_from(entry.size).ok())
                .flatten();
            let mime_type =
                (!entry.is_directory).then(|| mime_type_for_path(&entry.name).to_string());
            mapped.push(Entry {
                name: entry.name,
                path: path.clone(),
                size,
                is_dir: entry.is_directory,
                remote_id: Some(remote_id(
                    &self.inner.arg.share,
                    &self.inner.arg.root_path,
                    &path,
                )),
                parent_remote_id: Some(parent_id.clone()),
                mime_type,
                etag: modified_at.map(|modified| format!("{:x}-{modified:x}", entry.size)),
                ctag: None,
                created_at,
                modified_at,
            });
        }
        mapped.sort_by(|left, right| {
            right
                .is_dir
                .cmp(&left.is_dir)
                .then_with(|| left.name.cmp(&right.name))
        });
        Ok(mapped)
    }

    async fn get_impl(&self, path: String, byte_offset: u64) -> StorageBackendResult<StreamFile> {
        let relative_path = normalize_file_path(&path)?;
        let reader = self.validated_reader_for(&relative_path).await?;
        let total_size = reader.size;
        if byte_offset > total_size {
            return Err(StorageBackendError::InvalidRange {
                start: byte_offset,
                end_inclusive: byte_offset,
            });
        }

        let (sender, receiver) =
            async_channel::bounded::<StorageBackendResult<Bytes>>(STREAM_CHANNEL_CAPACITY);
        let backend = self.clone();
        let stream_path = relative_path.clone();
        std::mem::drop(tokio_runtime().spawn(async move {
            let mut offset = byte_offset;
            while offset < total_size {
                let requested = STREAM_CHUNK_SIZE.min(total_size - offset);
                let result = backend
                    .read_at_with_retry(&stream_path, offset, requested)
                    .await;
                let (bytes, current_size) = match result {
                    Ok(result) => result,
                    Err(error) => {
                        let _ = sender.send(Err(error)).await;
                        break;
                    }
                };
                if current_size != total_size {
                    let _ = sender
                        .send(Err(StorageBackendError::ProtocolError(
                            "SMB file size changed while streaming".to_string(),
                        )))
                        .await;
                    backend.invalidate_reader(&stream_path).await;
                    break;
                }
                let length = bytes.len() as u64;
                if sender.send(Ok(bytes)).await.is_err() {
                    break;
                }
                offset += length;
            }
            sender.close();
        }));

        Ok(StreamFile::new_from_channel(
            receiver,
            usize::try_from(total_size).ok(),
            Some(mime_type_for_path(&relative_path).to_string()),
            file_name(&relative_path).to_string(),
            byte_offset,
        ))
    }

    async fn get_range_response_impl(
        &self,
        path: String,
        range: ByteRange,
    ) -> StorageBackendResult<RangeResponse> {
        let relative_path = normalize_file_path(&path)?;
        let total_size = self.validated_reader_for(&relative_path).await?.size;
        validate_range(total_size, range)?;
        let (bytes, total_size) = self
            .read_at_with_retry(&relative_path, range.start, range.len())
            .await?;
        Ok(RangeResponse {
            bytes,
            total_size,
            content_type: Some(mime_type_for_path(&relative_path).to_string()),
        })
    }

    async fn read_at_with_retry(
        &self,
        relative_path: &str,
        offset: u64,
        length: u64,
    ) -> StorageBackendResult<(Bytes, u64)> {
        for attempt in 0..=MAX_OPERATION_RETRIES {
            let reader = self.cached_reader_for(relative_path).await?;
            let total_size = reader.size;
            if offset >= total_size {
                return Err(StorageBackendError::InvalidRange {
                    start: offset,
                    end_inclusive: offset.saturating_add(length.saturating_sub(1)),
                });
            }
            let expected = length.min(total_size - offset);
            match reader.read_at(offset, expected).await {
                Ok(bytes) if bytes.len() as u64 == expected => {
                    return Ok((Bytes::from(bytes), total_size));
                }
                Ok(_) => {
                    self.invalidate_reader(relative_path).await;
                    return Err(StorageBackendError::ProtocolError(
                        "SMB file changed during a positioned read".to_string(),
                    ));
                }
                Err(error) if should_retry(attempt, &error) => {
                    self.invalidate_reader(relative_path).await;
                    sleep_before_retry(attempt).await;
                }
                Err(error) => {
                    self.invalidate_reader(relative_path).await;
                    return Err(error);
                }
            }
        }
        Err(StorageBackendError::RetryExhausted(
            "SMB positioned read retries exhausted".to_string(),
        ))
    }

    async fn validated_reader_for(
        &self,
        relative_path: &str,
    ) -> StorageBackendResult<Arc<CachedReader>> {
        let cached = self.inner.readers.lock().await.get_cloned(relative_path);
        let Some(cached) = cached else {
            return self.cached_reader_for(relative_path).await;
        };

        let protocol_path = self.protocol_path(relative_path);
        let current_size = match self.inner.sessions.file_size(&protocol_path).await {
            Ok(size) => size,
            Err(error) => {
                self.invalidate_reader(relative_path).await;
                return Err(error);
            }
        };
        if current_size == cached.size {
            return Ok(cached);
        }

        self.invalidate_reader(relative_path).await;
        self.cached_reader_for(relative_path).await
    }

    async fn cached_reader_for(
        &self,
        relative_path: &str,
    ) -> StorageBackendResult<Arc<CachedReader>> {
        if let Some(reader) = self.inner.readers.lock().await.get_cloned(relative_path) {
            return Ok(reader);
        }

        let protocol_path = self.protocol_path(relative_path);
        let opened = Arc::new(CachedReader::new(
            self.inner.sessions.open_reader(&protocol_path).await?,
        ));
        let mut cache = self.inner.readers.lock().await;
        if let Some(existing) = cache.get_cloned(relative_path) {
            schedule_reader_close(opened);
            return Ok(existing);
        }
        if let Some(evicted) = cache.insert(relative_path.to_string(), opened.clone()) {
            schedule_reader_close(evicted);
        }
        Ok(opened)
    }

    async fn invalidate_reader(&self, relative_path: &str) {
        if let Some(reader) = self.inner.readers.lock().await.remove(relative_path) {
            schedule_reader_close(reader);
        }
    }

    fn protocol_path(&self, relative_path: &str) -> String {
        join_relative(&self.inner.arg.root_path, relative_path)
    }
}

pub fn list_smb_server_path(
    mut arg: BuildSmbArg,
    path: String,
) -> BoxFuture<'static, StorageBackendResult<Vec<Entry>>> {
    Box::pin(run_on_tokio_runtime(async move {
        arg = BuildSmbArg::validated(arg)?;
        arg.share.clear();
        arg.root_path.clear();
        let relative_path = normalize_relative_path(&path)?;

        if relative_path.is_empty() {
            let mut client = connect_client(&arg).await?;
            let mut shares = client.list_shares().await.map_err(map_smb_error)?;
            shares.sort_by(|left, right| left.name.cmp(&right.name));
            let parent_remote_id = remote_id("", "", "/");
            return Ok(shares
                .into_iter()
                .map(|share| Entry {
                    path: public_path(&share.name),
                    remote_id: Some(remote_id(&share.name, "", "/")),
                    parent_remote_id: Some(parent_remote_id.clone()),
                    name: share.name,
                    size: None,
                    is_dir: true,
                    mime_type: None,
                    etag: None,
                    ctag: None,
                    created_at: None,
                    modified_at: None,
                })
                .collect());
        }

        let mut segments = relative_path.split('/');
        let share = segments.next().unwrap_or_default().to_string();
        let directory = segments.collect::<Vec<_>>().join("/");
        arg.share = share.clone();
        let backend = SmbBackend::new(arg)?;
        let mut entries = backend.list_impl(public_path(&directory)).await?;
        entries.iter_mut().for_each(|entry| {
            entry.path = public_path(&join_relative(&share, entry.path.trim_start_matches('/')));
        });
        Ok(entries)
    }))
}

fn validate_range(total_size: u64, range: ByteRange) -> StorageBackendResult<()> {
    if range.start >= total_size || range.end_inclusive >= total_size {
        return Err(StorageBackendError::InvalidRange {
            start: range.start,
            end_inclusive: range.end_inclusive,
        });
    }
    Ok(())
}

impl StorageBackend for SmbBackend {
    fn list(&self, dir: String) -> BoxFuture<'_, StorageBackendResult<Vec<Entry>>> {
        let backend = self.clone();
        Box::pin(run_on_tokio_runtime(
            async move { backend.list_impl(dir).await },
        ))
    }

    fn get(
        &self,
        path: String,
        byte_offset: u64,
    ) -> BoxFuture<'_, StorageBackendResult<StreamFile>> {
        let backend = self.clone();
        Box::pin(run_on_tokio_runtime(async move {
            backend.get_impl(path, byte_offset).await
        }))
    }

    fn get_range_response(
        &self,
        path: String,
        range: ByteRange,
    ) -> BoxFuture<'_, StorageBackendResult<RangeResponse>> {
        let backend = self.clone();
        Box::pin(run_on_tokio_runtime(async move {
            backend.get_range_response_impl(path, range).await
        }))
    }

    fn release(&self, path: String) -> BoxFuture<'_, StorageBackendResult<()>> {
        let backend = self.clone();
        Box::pin(run_on_tokio_runtime(async move {
            let relative_path = normalize_file_path(&path)?;
            backend.invalidate_reader(&relative_path).await;
            Ok(())
        }))
    }
}

async fn run_on_tokio_runtime<F, T>(future: F) -> StorageBackendResult<T>
where
    F: Future<Output = StorageBackendResult<T>> + Send + 'static,
    T: Send + 'static,
{
    let task = tokio_runtime().spawn(future);
    let mut abort_on_drop = AbortTaskOnDrop(Some(task.abort_handle()));
    let result = task.await?;
    abort_on_drop.0 = None;
    result
}

async fn connect_session(arg: &BuildSmbArg) -> StorageBackendResult<ConnectedSession> {
    let mut client = connect_client(arg).await?;
    let tree = client
        .connect_share(&arg.share)
        .await
        .map_err(map_smb_error)?;

    Ok(ConnectedSession { client, tree })
}

async fn connect_client(arg: &BuildSmbArg) -> StorageBackendResult<SmbClient> {
    let config = ClientConfig {
        addr: arg.server_address(),
        timeout: arg.connect_timeout,
        username: arg.username.clone(),
        password: arg.password.clone(),
        domain: arg.domain.clone().unwrap_or_default(),
        auto_reconnect: false,
        compression: true,
        dfs_enabled: true,
        dfs_target_overrides: Default::default(),
    };
    let mut client = SmbClient::connect(config).await.map_err(map_smb_error)?;

    if arg.require_signing && !client.session().should_sign {
        return Err(StorageBackendError::UnsupportedFeature(
            "the SMB session did not establish message signing".to_string(),
        ));
    }
    if arg.require_encryption && !client.session().should_encrypt {
        let encryption_key = client.session().encryption_key.clone();
        let decryption_key = client.session().decryption_key.clone();
        let cipher = client
            .params()
            .and_then(|params| params.cipher)
            .unwrap_or(Cipher::Aes128Ccm);
        match (encryption_key, decryption_key) {
            (Some(encryption_key), Some(decryption_key)) => {
                client
                    .connection_mut()
                    .activate_encryption(encryption_key, decryption_key, cipher);
            }
            _ => {
                return Err(StorageBackendError::UnsupportedFeature(
                    "SMB encryption requires SMB3 session keys".to_string(),
                ));
            }
        }
    }

    Ok(client)
}

fn map_smb_error(error: smb2::Error) -> StorageBackendError {
    match error.kind() {
        SmbErrorKind::AuthRequired => StorageBackendError::AuthenticationFailed,
        SmbErrorKind::SigningRequired => StorageBackendError::UnsupportedFeature(
            "the SMB server requires message signing".to_string(),
        ),
        SmbErrorKind::AccessDenied => StorageBackendError::PermissionDenied,
        SmbErrorKind::NotFound => StorageBackendError::NotFound,
        SmbErrorKind::ConnectionLost | SmbErrorKind::SessionExpired => {
            StorageBackendError::ConnectionLost
        }
        SmbErrorKind::TimedOut => StorageBackendError::Timeout,
        SmbErrorKind::Cancelled => {
            StorageBackendError::ProtocolError("SMB operation was cancelled".to_string())
        }
        SmbErrorKind::InvalidData => StorageBackendError::ProtocolError(error.to_string()),
        SmbErrorKind::Unsupported => StorageBackendError::UnsupportedFeature(error.to_string()),
        SmbErrorKind::Io => match &error {
            smb2::Error::Io(io_error) => match io_error.kind() {
                IoErrorKind::TimedOut => StorageBackendError::Timeout,
                IoErrorKind::ConnectionAborted
                | IoErrorKind::ConnectionRefused
                | IoErrorKind::ConnectionReset
                | IoErrorKind::BrokenPipe
                | IoErrorKind::UnexpectedEof => StorageBackendError::ConnectionLost,
                IoErrorKind::NotFound => StorageBackendError::NotFound,
                IoErrorKind::PermissionDenied => StorageBackendError::PermissionDenied,
                _ => StorageBackendError::ProtocolError(io_error.to_string()),
            },
            _ => StorageBackendError::ProtocolError(error.to_string()),
        },
        _ => StorageBackendError::ProtocolError(error.to_string()),
    }
}

fn should_retry(attempt: usize, error: &StorageBackendError) -> bool {
    attempt < MAX_OPERATION_RETRIES && error.is_retryable()
}

async fn sleep_before_retry(attempt: usize) {
    tokio::time::sleep(RETRY_DELAYS[attempt.min(RETRY_DELAYS.len() - 1)]).await;
}

fn schedule_reader_close(reader: Arc<CachedReader>) {
    std::mem::drop(tokio_runtime().spawn(async move {
        reader.close().await;
    }));
}

fn normalize_file_path(path: &str) -> StorageBackendResult<String> {
    let normalized = normalize_relative_path(path)?;
    if normalized.is_empty() {
        return Err(StorageBackendError::InvalidPath(
            "SMB file path is required".to_string(),
        ));
    }
    Ok(normalized)
}

fn validate_raw_url_path(address: &str) -> StorageBackendResult<()> {
    let remainder = address.strip_prefix("smb://").ok_or_else(|| {
        StorageBackendError::InvalidPath("SMB address must use smb://".to_string())
    })?;
    let Some(path_start) = remainder.find('/') else {
        return Ok(());
    };
    let raw_path = remainder[path_start..]
        .split(['?', '#'])
        .next()
        .unwrap_or_default();
    let decoded_path = urlencoding::decode(raw_path)
        .map_err(|error| StorageBackendError::InvalidPath(error.to_string()))?;
    normalize_relative_path(&decoded_path).map(|_| ())
}

fn normalize_relative_path(path: &str) -> StorageBackendResult<String> {
    if contains_nul(path) {
        return Err(StorageBackendError::InvalidPath(
            "SMB paths must not contain NUL".to_string(),
        ));
    }
    let normalized = path.replace('\\', "/");
    let mut segments = Vec::new();
    for segment in normalized.split('/') {
        match segment {
            "" | "." => {}
            ".." => {
                return Err(StorageBackendError::InvalidPath(
                    "SMB path traversal is not allowed".to_string(),
                ));
            }
            segment if contains_illegal_path_character(segment) => {
                return Err(StorageBackendError::InvalidPath(
                    "SMB paths contain a protocol-invalid character".to_string(),
                ));
            }
            segment => segments.push(segment),
        }
    }
    Ok(segments.join("/"))
}

fn validate_component(name: &str, value: &str) -> StorageBackendResult<String> {
    let value = value.trim();
    if value.is_empty()
        || contains_nul(value)
        || value.contains(['/', '\\'])
        || matches!(value, "." | "..")
    {
        return Err(StorageBackendError::InvalidPath(format!(
            "invalid SMB {name}"
        )));
    }
    Ok(value.to_string())
}

fn validate_path_component(name: &str, value: &str) -> StorageBackendResult<String> {
    let value = validate_component(name, value)?;
    if contains_illegal_path_character(&value) {
        return Err(StorageBackendError::InvalidPath(format!(
            "invalid SMB {name}"
        )));
    }
    Ok(value)
}

fn parse_bool_query(name: &str, value: &str) -> StorageBackendResult<bool> {
    match value {
        "1" | "true" | "yes" => Ok(true),
        "0" | "false" | "no" => Ok(false),
        _ => Err(StorageBackendError::InvalidPath(format!(
            "SMB {name} must be true or false"
        ))),
    }
}

fn contains_illegal_path_character(value: &str) -> bool {
    value
        .chars()
        .any(|character| character.is_control() || r#"<>:"|?*"#.contains(character))
}

fn contains_nul(value: &str) -> bool {
    value.contains('\0')
}

fn non_empty(value: String) -> Option<String> {
    (!value.trim().is_empty()).then_some(value)
}

fn join_relative(parent: &str, child: &str) -> String {
    match (parent.is_empty(), child.is_empty()) {
        (true, _) => child.to_string(),
        (_, true) => parent.to_string(),
        _ => format!("{parent}/{child}"),
    }
}

fn public_path(relative_path: &str) -> String {
    if relative_path.is_empty() {
        "/".to_string()
    } else {
        format!("/{relative_path}")
    }
}

fn file_name(path: &str) -> &str {
    path.rsplit('/').next().unwrap_or(path)
}

fn system_time_millis(time: Option<SystemTime>) -> Option<i64> {
    time.and_then(|time| time.duration_since(SystemTime::UNIX_EPOCH).ok())
        .and_then(|duration| i64::try_from(duration.as_millis()).ok())
}

fn remote_id(share: &str, root_path: &str, path: &str) -> String {
    let digest = Sha256::digest(format!("{share}\0{root_path}\0{path}").as_bytes());
    digest.iter().map(|byte| format!("{byte:02x}")).collect()
}

fn mime_type_for_path(path: &str) -> &'static str {
    match path
        .rsplit('.')
        .next()
        .map(str::to_ascii_lowercase)
        .as_deref()
    {
        Some("flac") => "audio/flac",
        Some("mp3") => "audio/mpeg",
        Some("m4a") | Some("mp4") => "audio/mp4",
        Some("ogg") | Some("oga") => "audio/ogg",
        Some("opus") => "audio/opus",
        Some("wav") => "audio/wav",
        Some("aac") => "audio/aac",
        Some("ape") => "audio/ape",
        Some("wv") => "audio/wavpack",
        Some("aif") | Some("aiff") => "audio/aiff",
        Some("lrc") => "text/plain",
        Some("ttml") => "application/ttml+xml",
        Some("jpg") | Some("jpeg") => "image/jpeg",
        Some("png") => "image/png",
        Some("webp") => "image/webp",
        _ => "application/octet-stream",
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::net::TcpListener;

    fn parse(address: &str) -> StorageBackendResult<BuildSmbArg> {
        BuildSmbArg::from_url(
            address,
            "alice".to_string(),
            "secret".to_string(),
            false,
            Duration::from_secs(5),
        )
    }

    #[test]
    fn parses_smb_uri_and_default_port() {
        let arg = parse("smb://nas.local/Music").unwrap();
        assert_eq!(arg.host, "nas.local");
        assert_eq!(arg.port, 445);
        assert_eq!(arg.share, "Music");
        assert_eq!(arg.root_path, "");
    }

    #[test]
    fn parses_server_root_without_a_share() {
        let arg = parse("smb://nas.local").unwrap();
        assert_eq!(arg.host, "nas.local");
        assert_eq!(arg.port, 445);
        assert_eq!(arg.share, "");
        assert_eq!(arg.root_path, "");
    }

    #[test]
    fn parses_share_root_domain_and_security_options() {
        let arg = parse(
            "smb://192.168.1.10:1445/media/Music/Hi%20Res?domain=STUDIO&signing=true&encryption=1",
        )
        .unwrap();
        assert_eq!(arg.port, 1445);
        assert_eq!(arg.share, "media");
        assert_eq!(arg.root_path, "Music/Hi Res");
        assert_eq!(arg.domain.as_deref(), Some("STUDIO"));
        assert!(arg.require_signing);
        assert!(arg.require_encryption);
    }

    #[test]
    fn preserves_unicode_paths() {
        let arg = parse("smb://nas.local/media/%E9%9F%B3%E4%B9%90/%F0%9F%8E%B5").unwrap();
        assert_eq!(arg.root_path, "音乐/🎵");
        assert_eq!(
            normalize_relative_path("/宇多田 ヒカル/初恋.flac").unwrap(),
            "宇多田 ヒカル/初恋.flac"
        );
    }

    #[test]
    fn rejects_path_traversal_nul_and_uri_credentials() {
        assert!(parse("smb://nas.local/media/../private").is_err());
        assert!(parse("smb://alice:secret@nas.local/media").is_err());
        assert!(normalize_relative_path("/Music/\0bad").is_err());
        assert!(normalize_relative_path("/Music/bad%name?.flac").is_err());
        assert!(parse("smb://nas.local/Mu%2Asic").is_err());
    }

    #[test]
    fn permits_uri_reserved_characters_in_credentials_without_logging_them() {
        let arg = BuildSmbArg::from_url(
            "smb://nas.local/Music",
            "alice@example".to_string(),
            "p:*?<>|".to_string(),
            false,
            Duration::from_secs(5),
        )
        .unwrap();
        assert_eq!(arg.username, "alice@example");
        assert_eq!(arg.password, "p:*?<>|");
        assert!(!format!("{arg:?}").contains("p:*?<>|"));
    }

    #[test]
    fn guest_configuration_clears_credentials_and_domain() {
        let arg = BuildSmbArg::from_url(
            "smb://nas.local/public?domain=WORKGROUP",
            "ignored".to_string(),
            "ignored".to_string(),
            true,
            Duration::from_secs(5),
        )
        .unwrap();
        assert!(arg.username.is_empty());
        assert!(arg.password.is_empty());
        assert_eq!(arg.domain, None);
    }

    #[test]
    fn debug_output_redacts_password() {
        let arg = parse("smb://nas.local/Music").unwrap();
        let debug = format!("{arg:?}");
        assert!(!debug.contains("secret"));
        assert!(debug.contains("<redacted>"));
    }

    #[test]
    fn reader_cache_evicts_least_recently_used_entry() {
        let mut cache = BoundedCache::new(2);
        assert_eq!(cache.insert("a".to_string(), 1), None);
        assert_eq!(cache.insert("b".to_string(), 2), None);
        assert_eq!(cache.get_cloned("a"), Some(1));
        assert_eq!(cache.insert("c".to_string(), 3), Some(2));
        assert_eq!(cache.get_cloned("b"), None);
    }

    #[test]
    fn remote_ids_are_stable_and_include_the_configured_root() {
        let first = remote_id("Music", "Library/音乐", "/Artist/Track.flac");
        assert_eq!(
            first,
            remote_id("Music", "Library/音乐", "/Artist/Track.flac")
        );
        assert_ne!(
            first,
            remote_id("Music", "Archive/音乐", "/Artist/Track.flac")
        );
    }

    #[test]
    fn maps_smb_errors_to_protocol_neutral_categories() {
        assert!(matches!(
            map_smb_error(smb2::Error::Auth {
                message: "bad credentials".to_string()
            }),
            StorageBackendError::AuthenticationFailed
        ));
        assert!(matches!(
            map_smb_error(smb2::Error::Timeout),
            StorageBackendError::Timeout
        ));
        assert!(matches!(
            map_smb_error(smb2::Error::Disconnected),
            StorageBackendError::ConnectionLost
        ));
    }

    #[test]
    fn retry_policy_is_finite_and_excludes_authentication_failures() {
        assert!(should_retry(0, &StorageBackendError::ConnectionLost));
        assert!(should_retry(1, &StorageBackendError::Timeout));
        assert!(!should_retry(2, &StorageBackendError::ConnectionLost));
        assert!(!should_retry(0, &StorageBackendError::AuthenticationFailed));
    }

    #[test]
    fn validates_smb_range_against_remote_file_size() {
        assert!(validate_range(100, ByteRange::new(0, 99).unwrap()).is_ok());
        assert!(matches!(
            validate_range(100, ByteRange::new(99, 100).unwrap()),
            Err(StorageBackendError::InvalidRange { .. })
        ));
        assert!(matches!(
            validate_range(0, ByteRange::new(0, 0).unwrap()),
            Err(StorageBackendError::InvalidRange { .. })
        ));
    }

    #[test]
    fn list_from_non_tokio_executor_returns_error_instead_of_panicking() {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let port = listener.local_addr().unwrap().port();
        drop(listener);
        let backend = SmbBackend::new(BuildSmbArg {
            host: "127.0.0.1".to_string(),
            port,
            share: "public".to_string(),
            root_path: String::new(),
            domain: None,
            username: String::new(),
            password: String::new(),
            is_guest: true,
            require_signing: false,
            require_encryption: false,
            connect_timeout: Duration::from_millis(100),
        })
        .unwrap();

        let result = futures_executor::block_on(backend.list("/".to_string()));

        assert!(matches!(result, Err(StorageBackendError::ConnectionLost)));
    }
}
