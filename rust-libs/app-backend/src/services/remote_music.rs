use std::collections::HashMap;
use std::time::Duration;

use md5::{Digest, Md5};
use rand::{distributions::Alphanumeric, Rng};
use reqwest::blocking::Client;
use serde_json::{json, Value};

const CLIENT_NAME: &str = "TidePlayer";
const SUBSONIC_VERSION: &str = "1.16.1";
const CONNECT_TIMEOUT: Duration = Duration::from_secs(15);
const REQUEST_TIMEOUT: Duration = Duration::from_secs(45);

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum RemoteMusicError {
    #[error("remote address is invalid")]
    InvalidAddress,
    #[error("remote request timed out")]
    Timeout,
    #[error("remote connection failed")]
    Connectivity,
    #[error("remote authentication failed")]
    Unauthorized,
    #[error("remote permission denied")]
    PermissionDenied,
    #[error("remote resource was not found")]
    NotFound,
    #[error("remote HTTP request failed")]
    HttpFailure,
    #[error("remote response is invalid")]
    InvalidResponse,
    #[error("remote protocol response failed")]
    ProtocolFailure,
    #[error("remote service is unavailable")]
    Unavailable,
}

type RemoteMusicResult<T> = Result<T, RemoteMusicError>;

#[derive(Debug, Clone, uniffi::Record)]
pub struct SubsonicQueryParameter {
    pub key: String,
    pub value: String,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct EmbyLoginIdentity {
    pub access_token: String,
    pub user_id: String,
    pub server_id: String,
    pub server_name: Option<String>,
}

#[uniffi::export]
pub fn ct_subsonic_request(
    base_url: String,
    username: String,
    password: String,
    endpoint: String,
    params: HashMap<String, String>,
) -> RemoteMusicResult<String> {
    let url = subsonic_url(&base_url, &username, &password, &endpoint, &params)?;
    get_text(url, HashMap::new(), validate_subsonic_response)
}

#[uniffi::export]
pub fn ct_subsonic_request_pairs(
    base_url: String,
    username: String,
    password: String,
    endpoint: String,
    params: Vec<SubsonicQueryParameter>,
) -> RemoteMusicResult<String> {
    let url = subsonic_url_pairs(&base_url, &username, &password, &endpoint, &params)?;
    get_text(url, HashMap::new(), validate_subsonic_response)
}

#[uniffi::export]
pub fn ct_subsonic_resource_url(
    base_url: String,
    username: String,
    password: String,
    endpoint: String,
    params: HashMap<String, String>,
) -> RemoteMusicResult<String> {
    Ok(subsonic_url(&base_url, &username, &password, &endpoint, &params)?.to_string())
}

#[uniffi::export]
pub fn ct_emby_login(
    base_url: String,
    username: String,
    password: String,
) -> RemoteMusicResult<EmbyLoginIdentity> {
    let mut url = parse_url(&base_url)?;
    append_path(&mut url, "Users/AuthenticateByName");
    let body = json!({ "Username": username, "Pw": password }).to_string();
    let response = http_client()?
        .post(url)
        .header("X-Emby-Authorization", emby_authorization_header())
        .header(reqwest::header::CONTENT_TYPE, "application/json")
        .body(body)
        .send()
        .map_err(map_transport_error)?;
    ensure_success(response.status())?;
    let text = response.text().map_err(map_transport_error)?;
    let payload: Value =
        serde_json::from_str(&text).map_err(|_| RemoteMusicError::InvalidResponse)?;
    let access_token = required_identity_string(payload.get("AccessToken"))?;
    let user = payload
        .get("User")
        .and_then(Value::as_object)
        .ok_or(RemoteMusicError::InvalidResponse)?;
    let user_id = required_identity_string(user.get("Id"))?;
    let server_id = optional_identity_string(payload.get("ServerId"))
        .or_else(|| optional_identity_string(user.get("ServerId")))
        .ok_or(RemoteMusicError::InvalidResponse)?;
    let server_name = optional_identity_string(user.get("ServerName"))
        .or_else(|| optional_identity_string(payload.get("ServerName")));
    Ok(EmbyLoginIdentity {
        access_token,
        user_id,
        server_id,
        server_name,
    })
}

fn required_identity_string(value: Option<&Value>) -> RemoteMusicResult<String> {
    optional_identity_string(value).ok_or(RemoteMusicError::InvalidResponse)
}

fn optional_identity_string(value: Option<&Value>) -> Option<String> {
    value
        .and_then(Value::as_str)
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .map(ToOwned::to_owned)
}

#[uniffi::export]
pub fn ct_emby_request(
    base_url: String,
    token: String,
    path: String,
    params: HashMap<String, String>,
) -> RemoteMusicResult<String> {
    let mut url = parse_url(&base_url)?;
    append_path(&mut url, &path);
    url.query_pairs_mut().extend_pairs(params);
    let mut headers = HashMap::new();
    headers.insert("X-Emby-Token".to_string(), token);
    headers.insert(
        "X-Emby-Authorization".to_string(),
        emby_authorization_header(),
    );
    get_text(url, headers, validate_json_response)
}

#[uniffi::export]
pub fn ct_emby_playback_info_request(
    base_url: String,
    token: String,
    item_id: String,
    user_id: String,
) -> RemoteMusicResult<String> {
    if item_id.trim().is_empty() || user_id.trim().is_empty() {
        return Err(RemoteMusicError::InvalidResponse);
    }
    let mut url = parse_url(&base_url)?;
    {
        let mut segments = url
            .path_segments_mut()
            .map_err(|_| RemoteMusicError::InvalidAddress)?;
        segments.push("Items").push(&item_id).push("PlaybackInfo");
    }
    url.query_pairs_mut().append_pair("UserId", &user_id);
    let mut headers = HashMap::new();
    headers.insert("X-Emby-Token".to_string(), token);
    headers.insert(
        "X-Emby-Authorization".to_string(),
        emby_authorization_header(),
    );
    get_text(url, headers, validate_json_response)
}

#[uniffi::export]
pub fn ct_emby_resource_url(
    base_url: String,
    token: String,
    path: String,
    params: HashMap<String, String>,
) -> RemoteMusicResult<String> {
    let mut url = parse_url(&base_url)?;
    append_path(&mut url, &path);
    {
        let mut query = url.query_pairs_mut();
        query.append_pair("api_key", &token);
        query.extend_pairs(params);
    }
    Ok(url.to_string())
}

/// Builds Emby's credential-free static audio endpoint.  Authentication is
/// deliberately left to the caller's `X-Emby-Token` header.
#[uniffi::export]
pub fn ct_emby_playback_url(
    base_url: String,
    item_id: String,
    user_id: String,
    source_media_id: Option<String>,
) -> RemoteMusicResult<String> {
    if item_id.trim().is_empty() || user_id.trim().is_empty() {
        return Err(RemoteMusicError::InvalidResponse);
    }
    let mut url = parse_url(&base_url)?;
    {
        let mut segments = url
            .path_segments_mut()
            .map_err(|_| RemoteMusicError::InvalidAddress)?;
        segments.push("Audio").push(&item_id).push("stream");
    }
    {
        let mut query = url.query_pairs_mut();
        query
            .append_pair("UserId", &user_id)
            .append_pair("static", "true");
        if let Some(source_media_id) = source_media_id.filter(|value| !value.trim().is_empty()) {
            query.append_pair("MediaSourceId", &source_media_id);
        }
    }
    Ok(url.to_string())
}

fn parse_url(base_url: &str) -> RemoteMusicResult<reqwest::Url> {
    let url = reqwest::Url::parse(base_url.trim_end_matches('/'))
        .map_err(|_| RemoteMusicError::InvalidAddress)?;
    if !matches!(url.scheme(), "http" | "https")
        || url.host_str().is_none()
        || !url.username().is_empty()
        || url.password().is_some()
        || url.query().is_some()
        || url.fragment().is_some()
    {
        return Err(RemoteMusicError::InvalidAddress);
    }
    Ok(url)
}

fn append_path(url: &mut reqwest::Url, path: &str) {
    let joined = format!(
        "{}/{}",
        url.path().trim_end_matches('/'),
        path.trim_start_matches('/')
    );
    url.set_path(&joined);
}

fn subsonic_url(
    base_url: &str,
    username: &str,
    password: &str,
    endpoint: &str,
    params: &HashMap<String, String>,
) -> RemoteMusicResult<reqwest::Url> {
    let salt: String = rand::thread_rng()
        .sample_iter(&Alphanumeric)
        .take(12)
        .map(char::from)
        .collect();
    subsonic_url_with_salt(base_url, username, password, endpoint, params, &salt)
}

fn subsonic_url_pairs(
    base_url: &str,
    username: &str,
    password: &str,
    endpoint: &str,
    params: &[SubsonicQueryParameter],
) -> RemoteMusicResult<reqwest::Url> {
    let salt: String = rand::thread_rng()
        .sample_iter(&Alphanumeric)
        .take(12)
        .map(char::from)
        .collect();
    subsonic_url_with_salt_pairs(base_url, username, password, endpoint, params, &salt)
}

fn subsonic_url_with_salt(
    base_url: &str,
    username: &str,
    password: &str,
    endpoint: &str,
    params: &HashMap<String, String>,
    salt: &str,
) -> RemoteMusicResult<reqwest::Url> {
    let params = params
        .iter()
        .map(|(key, value)| SubsonicQueryParameter {
            key: key.clone(),
            value: value.clone(),
        })
        .collect::<Vec<_>>();
    subsonic_url_with_salt_pairs(base_url, username, password, endpoint, &params, salt)
}

fn subsonic_url_with_salt_pairs(
    base_url: &str,
    username: &str,
    password: &str,
    endpoint: &str,
    params: &[SubsonicQueryParameter],
    salt: &str,
) -> RemoteMusicResult<reqwest::Url> {
    let mut url = parse_url(base_url)?;
    append_path(
        &mut url,
        &format!("rest/{}.view", endpoint.trim_matches('/')),
    );
    let token = format!("{:x}", Md5::digest(format!("{password}{salt}")));
    {
        let mut query = url.query_pairs_mut();
        query
            .append_pair("u", username)
            .append_pair("s", salt)
            .append_pair("t", &token)
            .append_pair("v", SUBSONIC_VERSION)
            .append_pair("c", CLIENT_NAME)
            .append_pair("f", "json");
        for parameter in params {
            query.append_pair(&parameter.key, &parameter.value);
        }
    }
    Ok(url)
}

fn get_text(
    url: reqwest::Url,
    headers: HashMap<String, String>,
    validator: fn(&str) -> RemoteMusicResult<()>,
) -> RemoteMusicResult<String> {
    get_text_with_timeout(url, headers, validator, REQUEST_TIMEOUT)
}

fn get_text_with_timeout(
    url: reqwest::Url,
    headers: HashMap<String, String>,
    validator: fn(&str) -> RemoteMusicResult<()>,
    timeout: Duration,
) -> RemoteMusicResult<String> {
    let mut request = http_client_with_timeout(timeout)?.get(url);
    for (name, value) in headers {
        request = request.header(name, value);
    }
    let response = request.send().map_err(map_transport_error)?;
    ensure_success(response.status())?;
    let text = response.text().map_err(map_transport_error)?;
    validator(&text)?;
    Ok(text)
}

fn ensure_success(status: reqwest::StatusCode) -> RemoteMusicResult<()> {
    if status.is_success() {
        return Ok(());
    }
    Err(match status {
        reqwest::StatusCode::UNAUTHORIZED => RemoteMusicError::Unauthorized,
        reqwest::StatusCode::FORBIDDEN => RemoteMusicError::PermissionDenied,
        reqwest::StatusCode::NOT_FOUND => RemoteMusicError::NotFound,
        _ => RemoteMusicError::HttpFailure,
    })
}

fn map_transport_error(error: reqwest::Error) -> RemoteMusicError {
    if error.is_timeout() {
        RemoteMusicError::Timeout
    } else if is_connectivity_failure(&error) {
        RemoteMusicError::Connectivity
    } else {
        RemoteMusicError::Unavailable
    }
}

fn is_connectivity_failure(error: &reqwest::Error) -> bool {
    if !error.is_connect() {
        return false;
    }
    let mut source = std::error::Error::source(error);
    while let Some(cause) = source {
        if let Some(io_error) = cause.downcast_ref::<std::io::Error>() {
            if is_connectivity_error_kind(io_error.kind(), io_error.raw_os_error()) {
                return true;
            }
        }
        source = cause.source();
    }
    false
}

fn is_connectivity_error_kind(kind: std::io::ErrorKind, raw_os_error: Option<i32>) -> bool {
    match kind {
        std::io::ErrorKind::ConnectionRefused
        | std::io::ErrorKind::NetworkUnreachable
        | std::io::ErrorKind::NetworkDown
        | std::io::ErrorKind::HostUnreachable
        | std::io::ErrorKind::AddrNotAvailable
        | std::io::ErrorKind::NotConnected
        | std::io::ErrorKind::NotFound => true,
        kind if kind == uncategorized_error_kind() => raw_os_error.is_none(),
        _ => false,
    }
}

fn uncategorized_error_kind() -> std::io::ErrorKind {
    std::io::Error::from_raw_os_error(i32::MAX).kind()
}

fn validate_json_response(text: &str) -> RemoteMusicResult<()> {
    serde_json::from_str::<Value>(text)
        .map(|_| ())
        .map_err(|_| RemoteMusicError::InvalidResponse)
}

fn validate_subsonic_response(text: &str) -> RemoteMusicResult<()> {
    let root =
        serde_json::from_str::<Value>(text).map_err(|_| RemoteMusicError::InvalidResponse)?;
    let response = root
        .as_object()
        .and_then(|root| root.get("subsonic-response"))
        .and_then(Value::as_object)
        .ok_or(RemoteMusicError::InvalidResponse)?;
    let status = response
        .get("status")
        .and_then(Value::as_str)
        .ok_or(RemoteMusicError::InvalidResponse)?;
    match status {
        "ok" => Ok(()),
        "failed" => Err(subsonic_failure(response)),
        _ => Err(RemoteMusicError::ProtocolFailure),
    }
}

fn subsonic_failure(response: &serde_json::Map<String, Value>) -> RemoteMusicError {
    match response
        .get("error")
        .and_then(Value::as_object)
        .and_then(|error| error.get("code"))
        .and_then(Value::as_i64)
    {
        Some(40) => RemoteMusicError::Unauthorized,
        Some(50) => RemoteMusicError::PermissionDenied,
        Some(70) => RemoteMusicError::NotFound,
        _ => RemoteMusicError::ProtocolFailure,
    }
}

fn http_client() -> RemoteMusicResult<Client> {
    http_client_with_timeout(REQUEST_TIMEOUT)
}

fn http_client_with_timeout(timeout: Duration) -> RemoteMusicResult<Client> {
    Client::builder()
        .connect_timeout(CONNECT_TIMEOUT)
        .timeout(timeout)
        .build()
        .map_err(|_| RemoteMusicError::Unavailable)
}

fn emby_authorization_header() -> String {
    "MediaBrowser Client=\"TidePlayer\", Device=\"TidePlayer\", DeviceId=\"musicapp\", Version=\"1.0\""
        .to_string()
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::{Read, Write};
    use std::net::{TcpListener, TcpStream};
    use std::thread::{self, JoinHandle};
    use std::time::Instant;

    const MOCK_SERVER_DEADLINE: Duration = Duration::from_secs(5);
    const MOCK_STREAM_TIMEOUT: Duration = Duration::from_secs(2);

    fn response(status: &str, body: &str) -> String {
        format!(
            "HTTP/1.1 {status}\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}",
            body.len()
        )
    }

    fn read_request(stream: &mut TcpStream) -> String {
        let mut request = Vec::new();
        let mut chunk = [0; 1024];
        loop {
            let count = stream.read(&mut chunk).unwrap();
            if count == 0 {
                break;
            }
            request.extend_from_slice(&chunk[..count]);
            if request.windows(4).any(|window| window == b"\r\n\r\n") {
                break;
            }
        }
        let header_end = request
            .windows(4)
            .position(|window| window == b"\r\n\r\n")
            .map(|position| position + 4)
            .unwrap_or(request.len());
        let content_length = String::from_utf8_lossy(&request[..header_end])
            .lines()
            .find_map(|line| {
                line.strip_prefix("Content-Length:")
                    .or_else(|| line.strip_prefix("content-length:"))
            })
            .and_then(|length| length.trim().parse::<usize>().ok())
            .unwrap_or(0);
        while request.len() < header_end + content_length {
            let count = stream.read(&mut chunk).unwrap();
            if count == 0 {
                break;
            }
            request.extend_from_slice(&chunk[..count]);
        }
        String::from_utf8_lossy(&request).into_owned()
    }

    fn accept_with_deadline(listener: &TcpListener) -> TcpStream {
        listener.set_nonblocking(true).unwrap();
        let deadline = Instant::now() + MOCK_SERVER_DEADLINE;
        loop {
            match listener.accept() {
                Ok((stream, _)) => {
                    stream.set_nonblocking(false).unwrap();
                    stream.set_read_timeout(Some(MOCK_STREAM_TIMEOUT)).unwrap();
                    stream.set_write_timeout(Some(MOCK_STREAM_TIMEOUT)).unwrap();
                    return stream;
                }
                Err(error)
                    if error.kind() == std::io::ErrorKind::WouldBlock
                        && Instant::now() < deadline =>
                {
                    thread::sleep(Duration::from_millis(5));
                }
                Err(error) => panic!("mock server accept failed: {error}"),
            }
        }
    }

    fn serve_once(http_response: String) -> (String, JoinHandle<String>) {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        let handle = thread::spawn(move || {
            let mut stream = accept_with_deadline(&listener);
            let request = read_request(&mut stream);
            stream.write_all(http_response.as_bytes()).unwrap();
            stream.flush().unwrap();
            request
        });
        (format!("http://{address}/api"), handle)
    }

    fn serve_delayed(delay: Duration) -> (String, JoinHandle<()>) {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        let handle = thread::spawn(move || {
            let mut stream = accept_with_deadline(&listener);
            read_request(&mut stream);
            thread::sleep(delay);
            let _ = stream.write_all(response("200 OK", "{}\n").as_bytes());
        });
        (format!("http://{address}/api"), handle)
    }

    fn serve_delayed_body(delay: Duration) -> (String, JoinHandle<()>) {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        let handle = thread::spawn(move || {
            let mut stream = accept_with_deadline(&listener);
            read_request(&mut stream);
            stream
                .write_all(
                    b"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 3\r\nConnection: close\r\n\r\n",
                )
                .unwrap();
            stream.flush().unwrap();
            thread::sleep(delay);
            let _ = stream.write_all(b"{}\n");
        });
        (format!("http://{address}/api"), handle)
    }

    fn serve_invalid_tls() -> (String, JoinHandle<()>) {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        let handle = thread::spawn(move || {
            let mut stream = accept_with_deadline(&listener);
            stream.set_read_timeout(Some(MOCK_STREAM_TIMEOUT)).unwrap();
            let mut client_hello = [0; 1024];
            let _ = stream.read(&mut client_hello);
            let _ = stream.write_all(b"HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
        });
        (format!("https://{address}/api"), handle)
    }

    fn assert_safe(error: RemoteMusicError, secrets: &[&str]) {
        let text = format!("{error:?} {error}");
        for (index, secret) in secrets.iter().enumerate() {
            assert!(
                !text.contains(secret),
                "remote error redaction case {index} failed"
            );
        }
    }

    #[test]
    fn subsonic_url_uses_md5_and_encodes_opaque_string_ids() {
        let mut params = HashMap::new();
        params.insert("id".to_string(), "opaque/id:with?reserved".to_string());
        params.insert("query".to_string(), "CJK 空格".to_string());
        let url = subsonic_url_with_salt(
            "http://localhost/base/",
            "user name",
            "fixed-password",
            "search3",
            &params,
            "fixed-salt",
        )
        .unwrap();
        let query: HashMap<_, _> = url.query_pairs().into_owned().collect();

        assert_eq!(url.path(), "/base/rest/search3.view");
        assert_eq!(
            query["t"],
            format!("{:x}", Md5::digest("fixed-passwordfixed-salt"))
        );
        assert_eq!(query["u"], "user name");
        assert_eq!(query["s"], "fixed-salt");
        assert_eq!(query["v"], "1.16.1");
        assert_eq!(query["c"], "TidePlayer");
        assert_eq!(query["id"], "opaque/id:with?reserved");
        assert_eq!(query["query"], "CJK 空格");
        assert_eq!(query["f"], "json");
        assert!(!url.as_str().contains("fixed-password"));
    }

    #[test]
    fn subsonic_pairs_preserve_duplicate_order_and_encoding() {
        let params = vec![
            SubsonicQueryParameter {
                key: "songId".to_string(),
                value: "first|CJK 空格".to_string(),
            },
            SubsonicQueryParameter {
                key: "songId".to_string(),
                value: "second:/?%#".to_string(),
            },
        ];
        let url = subsonic_url_with_salt_pairs(
            "https://localhost/base",
            "user",
            "fixed-password",
            "updatePlaylist",
            &params,
            "fixed-salt",
        )
        .unwrap();
        let pairs: Vec<_> = url.query_pairs().into_owned().collect();
        assert_eq!(
            pairs[pairs.len() - 2],
            ("songId".to_string(), "first|CJK 空格".to_string())
        );
        assert_eq!(
            pairs[pairs.len() - 1],
            ("songId".to_string(), "second:/?%#".to_string())
        );
        assert!(!url.as_str().contains("fixed-password"));
    }

    #[test]
    fn subsonic_failure_codes_keep_protocol_semantics() {
        let cases = [
            (Some(20), RemoteMusicError::ProtocolFailure),
            (Some(30), RemoteMusicError::ProtocolFailure),
            (Some(40), RemoteMusicError::Unauthorized),
            (Some(50), RemoteMusicError::PermissionDenied),
            (Some(70), RemoteMusicError::NotFound),
            (Some(999), RemoteMusicError::ProtocolFailure),
            (None, RemoteMusicError::ProtocolFailure),
        ];
        for (code, expected) in cases {
            let error = match code {
                Some(code) => validate_subsonic_response(
                    &json!({
                        "subsonic-response": {
                            "status": "failed",
                            "error": {"code": code, "message": "fixed-server-message"}
                        }
                    })
                    .to_string(),
                ),
                None => validate_subsonic_response(
                    &json!({"subsonic-response": {"status": "failed"}}).to_string(),
                ),
            }
            .unwrap_err();
            assert_eq!(
                std::mem::discriminant(&error),
                std::mem::discriminant(&expected)
            );
            assert_safe(error, &["fixed-server-message"]);
        }
    }

    #[test]
    fn subsonic_success_and_protocol_failures_are_strict_and_safe() {
        let cases = [
            (
                response(
                    "200 OK",
                    r#"{"subsonic-response":{"status":"ok","version":"1.16.1"}}"#,
                ),
                None,
            ),
            (
                response(
                    "200 OK",
                    r#"{"subsonic-response":{"status":"failed","error":{"code":40,"message":"fixed-password"}}}"#,
                ),
                Some(RemoteMusicError::Unauthorized),
            ),
            (
                response("200 OK", r#"{"other":{}}"#),
                Some(RemoteMusicError::InvalidResponse),
            ),
            (
                response("200 OK", "not-json"),
                Some(RemoteMusicError::InvalidResponse),
            ),
        ];
        for (http_response, expected) in cases {
            let (base_url, server) = serve_once(http_response);
            let result = ct_subsonic_request(
                base_url,
                "alice".to_string(),
                "fixed-password".to_string(),
                "ping".to_string(),
                HashMap::new(),
            );
            match (result, expected) {
                (Ok(_), None) => {}
                (Err(error), Some(expected)) => {
                    assert_eq!(
                        std::mem::discriminant(&error),
                        std::mem::discriminant(&expected)
                    );
                    assert_safe(error, &["fixed-password"]);
                }
                other => panic!("unexpected result: {other:?}"),
            }
            server.join().unwrap();
        }
    }

    #[test]
    fn subsonic_http_statuses_map_to_typed_errors() {
        let cases = [
            ("401 Unauthorized", RemoteMusicError::Unauthorized),
            ("403 Forbidden", RemoteMusicError::PermissionDenied),
            ("404 Not Found", RemoteMusicError::NotFound),
            ("500 Internal Server Error", RemoteMusicError::HttpFailure),
        ];
        for (status, expected) in cases {
            let (base_url, server) = serve_once(response(status, "fixed-server-message"));
            let result = ct_subsonic_request(
                base_url,
                "alice".to_string(),
                "fixed-password".to_string(),
                "ping".to_string(),
                HashMap::new(),
            );
            let error = result.unwrap_err();
            assert_eq!(
                std::mem::discriminant(&error),
                std::mem::discriminant(&expected)
            );
            assert_safe(error, &["fixed-password", "fixed-server-message"]);
            server.join().unwrap();
        }
    }

    #[test]
    fn emby_login_and_request_validate_json_and_headers() {
        let (base_url, login_server) = serve_once(response(
            "200 OK",
            r#"{"AccessToken":"fixed-token","ServerId":"server-1","User":{"Id":"user-1","ServerName":"Test Server"}}"#,
        ));
        let login =
            ct_emby_login(base_url, "alice".to_string(), "fixed-password".to_string()).unwrap();
        assert_eq!(login.access_token, "fixed-token");
        assert_eq!(login.user_id, "user-1");
        assert_eq!(login.server_id, "server-1");
        assert_eq!(login.server_name.as_deref(), Some("Test Server"));
        let request = login_server.join().unwrap();
        assert!(request.starts_with("POST /api/Users/AuthenticateByName HTTP/1.1"));
        assert!(request
            .to_ascii_lowercase()
            .contains("x-emby-authorization: mediabrowser client=\"tideplayer\""));
        assert!(request.contains(r#""Username":"alice""#));
        assert!(request.contains(r#""Pw":"fixed-password""#));

        let (base_url, fallback_server) = serve_once(response(
            "200 OK",
            r#"{"AccessToken":"fixed-token-2","ServerId":null,"ServerName":"Top Server","User":{"Id":"user-2","ServerId":"server-2","ServerName":""}}"#,
        ));
        let fallback =
            ct_emby_login(base_url, "alice".to_string(), "fixed-password".to_string()).unwrap();
        assert_eq!(fallback.server_id, "server-2");
        assert_eq!(fallback.server_name.as_deref(), Some("Top Server"));
        fallback_server.join().unwrap();

        let (base_url, blank_fallback_server) = serve_once(response(
            "200 OK",
            r#"{"AccessToken":"fixed-token-3","ServerId":"","User":{"Id":"user-3","ServerId":"server-3","ServerName":null}}"#,
        ));
        let blank_fallback =
            ct_emby_login(base_url, "alice".to_string(), "fixed-password".to_string()).unwrap();
        assert_eq!(blank_fallback.server_id, "server-3");
        assert_eq!(blank_fallback.server_name, None);
        blank_fallback_server.join().unwrap();

        let (base_url, request_server) = serve_once(response("200 OK", r#"{"Items":[]}"#));
        let result = ct_emby_request(
            base_url,
            "fixed-token".to_string(),
            "Users/opaque-user/Items".to_string(),
            HashMap::new(),
        )
        .unwrap();
        assert_eq!(result, r#"{"Items":[]}"#);
        let request = request_server.join().unwrap();
        assert!(request.contains("GET /api/Users/opaque-user/Items"));
        assert!(request
            .to_ascii_lowercase()
            .contains("x-emby-token: fixed-token"));
    }

    #[test]
    fn emby_invalid_json_and_unauthorized_are_typed_and_safe() {
        let (base_url, server) = serve_once(response("200 OK", "not-json"));
        let error =
            ct_emby_login(base_url, "alice".to_string(), "fixed-password".to_string()).unwrap_err();
        assert!(matches!(error, RemoteMusicError::InvalidResponse));
        assert_safe(error, &["fixed-password", "not-json"]);
        server.join().unwrap();

        for body in [
            r#"{"AccessToken":"fixed-token","ServerId":"server-1","User":{"ServerName":"Name"}}"#,
            r#"{"AccessToken":"fixed-token","User":{"Id":"user-1"}}"#,
        ] {
            let (base_url, server) = serve_once(response("200 OK", body));
            let error = ct_emby_login(base_url, "alice".to_string(), "fixed-password".to_string())
                .unwrap_err();
            assert!(matches!(error, RemoteMusicError::InvalidResponse));
            assert_safe(error, &["fixed-password", "fixed-token"]);
            server.join().unwrap();
        }

        let (base_url, server) = serve_once(response("401 Unauthorized", "fixed-server-message"));
        let error = ct_emby_request(
            base_url,
            "fixed-token".to_string(),
            "Items".to_string(),
            HashMap::new(),
        )
        .unwrap_err();
        assert!(matches!(error, RemoteMusicError::Unauthorized));
        assert_safe(error, &["fixed-token", "fixed-server-message"]);
        server.join().unwrap();
    }

    #[test]
    fn transport_errors_distinguish_timeout_connectivity_and_other_failures() {
        let (base_url, server) = serve_delayed(Duration::from_millis(150));
        let url = parse_url(&base_url).unwrap();
        let error = get_text_with_timeout(
            url,
            HashMap::new(),
            validate_json_response,
            Duration::from_millis(20),
        )
        .unwrap_err();
        assert!(matches!(error, RemoteMusicError::Timeout));
        server.join().unwrap();

        let (base_url, server) = serve_delayed_body(Duration::from_millis(150));
        let url = parse_url(&base_url).unwrap();
        let error = get_text_with_timeout(
            url,
            HashMap::new(),
            validate_json_response,
            Duration::from_millis(20),
        )
        .unwrap_err();
        assert!(matches!(error, RemoteMusicError::Timeout));
        server.join().unwrap();

        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        drop(listener);
        let transport_error = Client::builder()
            .no_proxy()
            .connect_timeout(Duration::from_millis(100))
            .timeout(Duration::from_millis(100))
            .build()
            .unwrap()
            .get(format!("http://{address}/api"))
            .send()
            .unwrap_err();
        let error = map_transport_error(transport_error);
        assert!(
            matches!(error, RemoteMusicError::Connectivity),
            "unexpected transport mapping: {error:?}"
        );
        assert_safe(error, &["fixed-token"]);

        let (tls_url, tls_server) = serve_invalid_tls();
        let tls_error = Client::builder()
            .no_proxy()
            .connect_timeout(Duration::from_secs(1))
            .timeout(Duration::from_secs(1))
            .build()
            .unwrap()
            .get(tls_url)
            .send()
            .unwrap_err();
        assert!(
            tls_error.is_connect(),
            "TLS handshake must be a connect-stage error"
        );
        let error = map_transport_error(tls_error);
        assert!(
            matches!(error, RemoteMusicError::Unavailable),
            "TLS failures must not trigger endpoint fallback: {error:?}"
        );
        assert_safe(error, &["fixed-token"]);
        tls_server.join().unwrap();

        let (base_url, server) = serve_once(response("200 OK", "not-json"));
        let invalid = ct_emby_request(
            base_url,
            "fixed-token".to_string(),
            "Items".to_string(),
            HashMap::new(),
        )
        .unwrap_err();
        assert!(matches!(invalid, RemoteMusicError::InvalidResponse));
        assert_safe(invalid, &["fixed-token", "not-json"]);
        server.join().unwrap();
    }

    #[test]
    fn connectivity_io_kind_allowlist_is_explicit_and_excludes_tls_style_failures() {
        use std::net::ToSocketAddrs;

        let dns_error = ("tideplayer-does-not-exist.invalid", 443)
            .to_socket_addrs()
            .unwrap_err();
        assert_eq!(dns_error.raw_os_error(), None);
        assert_eq!(dns_error.kind(), uncategorized_error_kind());
        for (kind, raw_os_error) in [
            (dns_error.kind(), dns_error.raw_os_error()),
            (std::io::ErrorKind::NotFound, None),
            (std::io::ErrorKind::ConnectionRefused, Some(61)),
            (std::io::ErrorKind::NetworkUnreachable, Some(51)),
            (std::io::ErrorKind::NetworkDown, Some(50)),
            (std::io::ErrorKind::HostUnreachable, Some(65)),
            (std::io::ErrorKind::AddrNotAvailable, Some(49)),
            (std::io::ErrorKind::NotConnected, Some(57)),
        ] {
            assert!(
                is_connectivity_error_kind(kind, raw_os_error),
                "expected {kind:?}/{raw_os_error:?} to be allowed"
            );
        }
        for (kind, raw_os_error) in [
            (dns_error.kind(), Some(1)),
            (std::io::ErrorKind::InvalidData, None),
            (std::io::ErrorKind::PermissionDenied, Some(13)),
            (std::io::ErrorKind::ConnectionReset, Some(54)),
            (std::io::ErrorKind::TimedOut, Some(60)),
            (std::io::ErrorKind::Other, None),
            (std::io::ErrorKind::UnexpectedEof, None),
            (std::io::ErrorKind::BrokenPipe, None),
            (std::io::ErrorKind::WouldBlock, None),
        ] {
            assert!(
                !is_connectivity_error_kind(kind, raw_os_error),
                "expected {kind:?}/{raw_os_error:?} to be rejected"
            );
        }
    }

    #[test]
    fn invalid_addresses_are_typed_without_echoing_input() {
        for address in [
            "not a url with fixed-secret",
            "ftp://localhost/fixed-secret",
            "file:///tmp/fixed-secret",
            "https://user:fixed-secret@localhost/api",
            "https://localhost/api?token=fixed-secret",
            "https://localhost/api#fixed-secret",
        ] {
            let error = ct_subsonic_resource_url(
                address.to_string(),
                "alice".to_string(),
                "fixed-password".to_string(),
                "ping".to_string(),
                HashMap::new(),
            )
            .unwrap_err();
            assert!(matches!(error, RemoteMusicError::InvalidAddress));
            assert_safe(error, &["fixed-secret", "fixed-password"]);
        }
    }

    #[test]
    fn emby_playback_url_uses_encoded_item_segment_and_no_credential_query() {
        let url = ct_emby_playback_url(
            "https://emby.example/base/".to_string(),
            "opaque/id?x#%".to_string(),
            "user CJK".to_string(),
            Some("source|1".to_string()),
        )
        .unwrap();
        let parsed = reqwest::Url::parse(&url).unwrap();
        assert_eq!(parsed.path(), "/base/Audio/opaque%2Fid%3Fx%23%25/stream");
        let query: HashMap<_, _> = parsed.query_pairs().into_owned().collect();
        assert_eq!(query["UserId"], "user CJK");
        assert_eq!(query["MediaSourceId"], "source|1");
        assert_eq!(query["static"], "true");
        assert!(!url.contains("api_key"));
        assert!(!url.contains("fixed-token"));
    }

    #[test]
    fn emby_playback_info_request_encodes_opaque_item_segment() {
        let (base_url, server) = serve_once(response("200 OK", r#"{"MediaSources":[]}"#));
        let request = ct_emby_playback_info_request(
            base_url,
            "fixed-token".to_string(),
            "opaque/id?#%".to_string(),
            "user id".to_string(),
        )
        .unwrap();
        assert!(request.contains("MediaSources"));
        let received = server.join().unwrap();
        assert!(received.contains("/api/Items/opaque%2Fid%3F%23%25/PlaybackInfo"));
        assert!(received.contains("UserId=user+id"));
        assert!(received
            .to_ascii_lowercase()
            .contains("x-emby-token: fixed-token"));
    }
}
