use std::{collections::HashMap, sync::Arc, time::Duration};

use crate::{
    error::BResult,
    objects::Storage,
    schema::StorageEntryLoc,
    services::{
        build_openlist_playback_backend, build_storage_backend, promote_completed_playback_cache,
        start_completed_playback_cache, start_http_playback_cache_gateway, start_playback_gateway,
        PlaybackCacheOptions, PlaybackCachePromotionResult, PlaybackSession,
    },
    Backend,
};

#[uniffi::export]
pub async fn ct_create_playback_session(
    backend: Arc<Backend>,
    storage: Storage,
    loc: StorageEntryLoc,
) -> BResult<Arc<PlaybackSession>> {
    let context = backend.get_context();
    let storage_backend = build_storage_backend(context, storage)?;
    start_playback_gateway(storage_backend, loc.path).await
}

/// Creates a stable player-facing loopback session for OpenList without
/// constructing the unsupported persisted OpenList storage backend.
#[uniffi::export]
pub async fn ct_create_openlist_playback_session(
    base_url: String,
    token: String,
    path: String,
) -> BResult<Arc<PlaybackSession>> {
    let backend = build_openlist_playback_backend(base_url, token, Duration::from_secs(45))?;
    start_playback_gateway(backend, path).await
}

#[uniffi::export]
pub async fn ct_promote_completed_playback_cache(
    cache_options: PlaybackCacheOptions,
    destination_directory: String,
) -> BResult<PlaybackCachePromotionResult> {
    async_runtime::tokio_runtime()
        .spawn_blocking(move || {
            promote_completed_playback_cache(cache_options, destination_directory)
        })
        .await
        .map_err(|error| crate::error::BError::CustomError {
            message: format!("playback cache promotion task failed: {error}"),
        })?
        .map_err(Into::into)
}

#[uniffi::export]
pub async fn ct_create_http_playback_cache_session(
    uri: String,
    headers: HashMap<String, String>,
    cache_options: PlaybackCacheOptions,
) -> BResult<Arc<PlaybackSession>> {
    start_http_playback_cache_gateway(uri, headers, cache_options).await
}

#[uniffi::export]
pub async fn ct_open_completed_playback_cache(
    cache_options: PlaybackCacheOptions,
) -> BResult<Option<Arc<PlaybackSession>>> {
    start_completed_playback_cache(cache_options).await
}

#[cfg(test)]
mod tests {
    use super::*;
    use reqwest::{header, StatusCode};
    use std::{
        io::{Read, Write},
        net::TcpListener,
        thread,
    };

    const SECOND_BLOCK: u64 = 256 * 1024;
    const TOTAL_SIZE: u64 = SECOND_BLOCK + 3;

    fn read_request(stream: &mut std::net::TcpStream) -> String {
        stream
            .set_read_timeout(Some(Duration::from_secs(2)))
            .unwrap();
        let mut bytes = Vec::new();
        let mut chunk = [0_u8; 8 * 1024];
        loop {
            let count = stream.read(&mut chunk).unwrap();
            if count == 0 {
                break;
            }
            bytes.extend_from_slice(&chunk[..count]);
            let Some(header_end) = bytes.windows(4).position(|part| part == b"\r\n\r\n") else {
                continue;
            };
            let headers = String::from_utf8_lossy(&bytes[..header_end]);
            let content_length = headers
                .lines()
                .find_map(|line| {
                    let (name, value) = line.split_once(':')?;
                    name.eq_ignore_ascii_case("content-length")
                        .then(|| value.trim().parse::<usize>().ok())
                        .flatten()
                })
                .unwrap_or(0);
            if bytes.len() >= header_end + 4 + content_length {
                break;
            }
        }
        String::from_utf8_lossy(&bytes).into_owned()
    }

    fn write_get_response(stream: &mut std::net::TcpStream, raw_url: &str) {
        let body = serde_json::json!({
            "code": 200,
            "data": {"raw_url": raw_url, "size": TOTAL_SIZE, "sign": ""}
        })
        .to_string();
        stream
            .write_all(
                format!(
                    "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}",
                    body.len()
                )
                .as_bytes(),
            )
            .unwrap();
    }

    #[tokio::test]
    async fn openlist_ffi_uses_playback_backend_and_keeps_loopback_across_refresh() {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let origin = format!("http://{}", listener.local_addr().unwrap());
        let origin_for_server = origin.clone();
        let server = thread::spawn(move || {
            let (mut get1, _) = listener.accept().unwrap();
            let request = read_request(&mut get1);
            let lower = request.to_ascii_lowercase();
            assert!(request.starts_with("POST /api/fs/get HTTP/1.1"));
            assert!(request.contains("\"path\":\"/library/type3-track\""));
            assert!(lower.contains("authorization: api-token"));
            write_get_response(&mut get1, &format!("{origin_for_server}/raw-old"));
            drop(get1);

            let (mut probe, _) = listener.accept().unwrap();
            let request = read_request(&mut probe);
            let lower = request.to_ascii_lowercase();
            assert!(request.starts_with("GET /raw-old HTTP/1.1"));
            assert!(lower.contains("range: bytes=0-0"));
            assert!(!lower.contains("authorization:"));
            probe
                .write_all(
                    format!(
                        "HTTP/1.1 206 Partial Content\r\nContent-Range: bytes 0-0/{TOTAL_SIZE}\r\nContent-Length: 1\r\nContent-Type: audio/flac\r\nConnection: close\r\n\r\na"
                    )
                    .as_bytes(),
                )
                .unwrap();
            drop(probe);

            let (mut stale, _) = listener.accept().unwrap();
            let request = read_request(&mut stale);
            let lower = request.to_ascii_lowercase();
            assert!(request.starts_with("GET /raw-old HTTP/1.1"));
            assert!(lower.contains(&format!("range: bytes={SECOND_BLOCK}-{}", TOTAL_SIZE - 1)));
            stale
                .write_all(
                    b"HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
                )
                .unwrap();
            drop(stale);

            let (mut get2, _) = listener.accept().unwrap();
            let request = read_request(&mut get2);
            let lower = request.to_ascii_lowercase();
            assert!(request.starts_with("POST /api/fs/get HTTP/1.1"));
            assert!(request.contains("\"path\":\"/library/type3-track\""));
            assert!(lower.contains("authorization: api-token"));
            write_get_response(&mut get2, &format!("{origin_for_server}/raw-new"));
            drop(get2);

            let (mut refreshed, _) = listener.accept().unwrap();
            let request = read_request(&mut refreshed);
            let lower = request.to_ascii_lowercase();
            assert!(request.starts_with("GET /raw-new HTTP/1.1"));
            assert!(lower.contains(&format!("range: bytes={SECOND_BLOCK}-{}", TOTAL_SIZE - 1)));
            assert!(!lower.contains("authorization:"));
            refreshed
                .write_all(
                    format!(
                        "HTTP/1.1 206 Partial Content\r\nContent-Range: bytes {SECOND_BLOCK}-{}/{TOTAL_SIZE}\r\nContent-Length: 3\r\nContent-Type: audio/flac\r\nConnection: close\r\n\r\nxyz",
                        TOTAL_SIZE - 1
                    )
                    .as_bytes(),
                )
                .unwrap();
        });

        let session = ct_create_openlist_playback_session(
            origin,
            "api-token".into(),
            "/library/type3-track".into(),
        )
        .await
        .unwrap();
        let stable_url = session.url();
        assert!(stable_url.starts_with("http://127.0.0.1:"));
        assert_eq!(session.content_type(), "audio/flac");
        let client = reqwest::Client::builder().no_proxy().build().unwrap();
        let response = client
            .get(&stable_url)
            .header(
                header::RANGE,
                format!("bytes={SECOND_BLOCK}-{}", TOTAL_SIZE - 1),
            )
            .send()
            .await
            .unwrap();
        assert_eq!(response.status(), StatusCode::PARTIAL_CONTENT);
        assert_eq!(
            response.headers()[header::CONTENT_RANGE],
            format!("bytes {SECOND_BLOCK}-{}/{}", TOTAL_SIZE - 1, TOTAL_SIZE)
        );
        assert_eq!(response.bytes().await.unwrap().as_ref(), b"xyz");
        assert_eq!(session.url(), stable_url);
        assert_eq!(session.content_type(), "audio/flac");
        assert_eq!(session.stats().remote_requests, 2);
        server.join().unwrap();

        session.shutdown();
        let stopped =
            match tokio::time::timeout(Duration::from_millis(500), client.get(&stable_url).send())
                .await
            {
                Err(_) | Ok(Err(_)) => true,
                Ok(Ok(response)) => !response.status().is_success(),
            };
        assert!(stopped);
    }
}
