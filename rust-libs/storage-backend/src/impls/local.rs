use std::io::SeekFrom;

use async_runtime::tokio_runtime;
use futures_util::future::BoxFuture;
use tokio::io::{AsyncReadExt, AsyncSeekExt};

use crate::{
    ByteRange, Entry, RangeResponse, StorageBackend, StorageBackendError, StorageBackendResult,
    StreamFile,
};

pub struct LocalBackend;

static ANDROID_PREFIX_PATH: &str = "/storage/emulated/0";

fn platform_local_path(path: String) -> String {
    if std::env::consts::OS == "windows" {
        path.replace('/', "\\")
    } else if std::env::consts::OS == "android"
        && !path.starts_with("/data/")
        && !path.starts_with("/storage/")
    {
        ANDROID_PREFIX_PATH.to_string() + path.as_str()
    } else {
        path
    }
}

impl Default for LocalBackend {
    fn default() -> Self {
        Self::new()
    }
}

impl LocalBackend {
    pub fn new() -> Self {
        Self
    }

    async fn list_impl(&self, dir: String) -> StorageBackendResult<Vec<Entry>> {
        let dir = platform_local_path(dir);

        let mut ret = tokio_runtime()
            .spawn(async move {
                let path = tokio::fs::canonicalize(dir).await?;
                let mut dir = tokio::fs::read_dir(path).await?;

                let mut ret: Vec<Entry> = Default::default();
                while let Some(entry) = dir.next_entry().await? {
                    let metadata = entry.metadata().await?;
                    let mut path = entry
                        .path()
                        .to_string_lossy()
                        .to_string()
                        .replace("\\\\?\\", "");
                    if std::env::consts::OS == "android" {
                        if let Some(strip_path) = path.strip_prefix(ANDROID_PREFIX_PATH) {
                            path = strip_path.to_string();
                        }
                    }

                    ret.push(Entry {
                        name: entry.file_name().to_string_lossy().to_string(),
                        path: path.replace('\\', "/"),
                        size: Some(metadata.len() as usize),
                        is_dir: metadata.is_dir(),
                        remote_id: None,
                        parent_remote_id: None,
                        mime_type: None,
                        etag: None,
                        ctag: None,
                        created_at: None,
                        modified_at: metadata
                            .modified()
                            .ok()
                            .and_then(|value| value.duration_since(std::time::UNIX_EPOCH).ok())
                            .and_then(|value| i64::try_from(value.as_millis()).ok()),
                    });
                }

                Ok::<_, StorageBackendError>(ret)
            })
            .await??;

        ret.sort_by(|a, b| a.name.cmp(&b.name));
        Ok(ret)
    }

    async fn get_impl(&self, p: String, byte_offset: u64) -> StorageBackendResult<StreamFile> {
        let p = platform_local_path(p);

        let buf = {
            let p = p.clone();
            tokio_runtime()
                .spawn(async move {
                    let mut buf: Vec<u8> = Default::default();
                    let path = tokio::fs::canonicalize(&p).await?;
                    let mut file = tokio::fs::File::open(path).await?;

                    file.seek(SeekFrom::Start(byte_offset)).await?;
                    file.read_to_end(&mut buf).await?;

                    Ok::<_, StorageBackendError>(buf)
                })
                .await??
        };

        Ok(StreamFile::new_from_bytes(buf.as_slice(), &p, 0))
    }

    async fn get_range_response_impl(
        &self,
        p: String,
        range: ByteRange,
    ) -> StorageBackendResult<RangeResponse> {
        let p = platform_local_path(p);

        let (buf, total_size) = tokio_runtime()
            .spawn(async move {
                let path = tokio::fs::canonicalize(&p).await?;
                let mut file = tokio::fs::File::open(path).await?;
                let total_size = file.metadata().await?.len();
                file.seek(SeekFrom::Start(range.start)).await?;
                let mut buf = Vec::with_capacity(range.len() as usize);
                file.take(range.len()).read_to_end(&mut buf).await?;
                Ok::<_, StorageBackendError>((buf, total_size))
            })
            .await??;
        Ok(RangeResponse {
            bytes: bytes::Bytes::from(buf),
            total_size,
            content_type: None,
        })
    }
}

impl StorageBackend for LocalBackend {
    fn list(&self, dir: String) -> BoxFuture<'_, StorageBackendResult<Vec<Entry>>> {
        Box::pin(self.list_impl(dir))
    }
    fn get(&self, p: String, byte_offset: u64) -> BoxFuture<'_, StorageBackendResult<StreamFile>> {
        Box::pin(self.get_impl(p, byte_offset))
    }
    fn get_range_response(
        &self,
        p: String,
        range: ByteRange,
    ) -> BoxFuture<'_, StorageBackendResult<RangeResponse>> {
        Box::pin(self.get_range_response_impl(p, range))
    }
}

#[cfg(test)]
mod test {
    use crate::{ByteRange, LocalBackend, StorageBackend};

    #[tokio::test]
    async fn test_list_dir() {
        let backend = LocalBackend::new();

        let cwd = std::env::current_dir()
            .unwrap()
            .join("test/assets/case_list");
        let cwd = cwd.to_string_lossy().to_string();
        let list = backend.list(cwd).await.unwrap();
        assert_eq!(list.len(), 2);
        assert_eq!(list[0].name, "a.txt");
        assert_eq!(list[1].name, "b.log.txt");
    }

    #[tokio::test]
    async fn test_list_dir_use_linux_slash() {
        let backend = LocalBackend::new();

        let cwd = std::env::current_dir()
            .unwrap()
            .join("test/assets/case_list");
        let cwd = cwd.to_string_lossy().to_string();
        let cwd = cwd.replace("\\", "/");
        let list = backend.list(cwd).await.unwrap();
        assert_eq!(list.len(), 2);
        assert_eq!(list[0].name, "a.txt");
        assert_eq!(list[1].name, "b.log.txt");
    }

    #[tokio::test]
    async fn test_partial_bytes() {
        let backend = LocalBackend::new();

        let cwd = std::env::current_dir()
            .unwrap()
            .join("test/assets/case_list/b.log.txt");
        let cwd = cwd.to_string_lossy().to_string();
        let file = backend.get(cwd, 3).await.unwrap();
        let bytes = file.bytes().await.unwrap();

        assert_eq!(String::from_utf8_lossy(bytes.as_ref()), "og.txt");
    }

    #[tokio::test]
    async fn test_partial_stream() {
        let backend = LocalBackend::new();

        let cwd = std::env::current_dir()
            .unwrap()
            .join("test/assets/case_list/b.log.txt");
        let cwd = cwd.to_string_lossy().to_string();
        let file = backend.get(cwd, 3).await.unwrap();

        let stream = file.into_rx();
        let chunk = stream.recv().await;
        assert!(chunk.is_ok());
        let chunk = chunk.unwrap().unwrap();
        assert_eq!(String::from_utf8_lossy(chunk.as_ref()), "og.txt");
    }

    #[tokio::test]
    async fn test_bounded_range() {
        let backend = LocalBackend::new();
        let path = std::env::current_dir()
            .unwrap()
            .join("test/assets/case_list/b.log.txt")
            .to_string_lossy()
            .to_string();

        let bytes = backend
            .get_range(path, ByteRange::new(2, 4).unwrap())
            .await
            .unwrap();

        assert_eq!(bytes.as_ref(), b"log");
    }
}
