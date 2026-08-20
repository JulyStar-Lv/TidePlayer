use std::time::Duration;

use reqwest::blocking::Client;
use serde_json::Value;

const CONNECT_TIMEOUT: Duration = Duration::from_secs(15);
const REQUEST_TIMEOUT: Duration = Duration::from_secs(45);

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum OpenListAuthError {
    #[error("OpenList address is invalid")]
    InvalidAddress,
    #[error("OpenList request timed out")]
    Timeout,
    #[error("OpenList authentication failed")]
    Unauthorized,
    #[error("OpenList permission denied")]
    PermissionDenied,
    #[error("OpenList requires an OTP")]
    OtpRequired,
    #[error("OpenList request was rate limited")]
    RateLimited,
    #[error("OpenList response is invalid")]
    InvalidResponse,
    #[error("OpenList protocol response failed")]
    ProtocolFailure,
    #[error("OpenList service is unavailable")]
    Unavailable,
}

type OpenListAuthResult<T> = Result<T, OpenListAuthError>;

#[derive(Debug, uniffi::Record)]
pub struct OpenListBrowseEntry {
    pub name: String,
    pub size: i64,
    pub is_dir: bool,
    pub entry_type: i32,
}

#[derive(Debug, uniffi::Record)]
pub struct OpenListBrowsePage {
    pub entries: Vec<OpenListBrowseEntry>,
    pub total: i64,
}

#[uniffi::export]
pub fn ct_openlist_login(
    base_url: String,
    username: String,
    password: String,
    otp_code: String,
) -> OpenListAuthResult<String> {
    let base = parse_openlist_url(&base_url)?;
    let url = openlist_path(&base, &["api", "auth", "login"])?;
    let body = serde_json::json!({
        "username": username,
        "password": password,
        "otp_code": otp_code,
    })
    .to_string();
    let response = openlist_client(&base)?
        .post(url)
        .header(reqwest::header::CONTENT_TYPE, "application/json")
        .body(body)
        .send()
        .map_err(map_openlist_transport)?;
    let status = response.status();
    let text = response
        .text()
        .map_err(|_| OpenListAuthError::InvalidResponse)?;
    classify_http_status(status)?;
    let envelope = parse_envelope(&text)?;
    match envelope.code {
        200 => {
            let token = envelope
                .data
                .as_ref()
                .and_then(|value| value.get("token"))
                .and_then(Value::as_str)
                .map(str::trim)
                .filter(|value| !value.is_empty())
                .map(ToOwned::to_owned)
                .ok_or(OpenListAuthError::InvalidResponse)?;
            Ok(token)
        }
        402 => Err(OpenListAuthError::OtpRequired),
        401 => Err(OpenListAuthError::Unauthorized),
        403 => Err(OpenListAuthError::PermissionDenied),
        429 => Err(OpenListAuthError::RateLimited),
        _ if envelope.code > 0 => Err(OpenListAuthError::ProtocolFailure),
        _ => Err(OpenListAuthError::InvalidResponse),
    }
}

#[uniffi::export]
pub fn ct_openlist_validate_session(base_url: String, token: String) -> OpenListAuthResult<()> {
    let base = parse_openlist_url(&base_url)?;
    let url = openlist_path(&base, &["api", "me"])?;
    let mut request = openlist_client(&base)?.get(url);
    if !token.is_empty() {
        request = request.header(reqwest::header::AUTHORIZATION, token);
    }
    let response = request.send().map_err(map_openlist_transport)?;
    let status = response.status();
    let text = response
        .text()
        .map_err(|_| OpenListAuthError::InvalidResponse)?;
    classify_http_status(status)?;
    let envelope = parse_envelope(&text)?;
    match envelope.code {
        200 if envelope.data.is_some() => Ok(()),
        401 => Err(OpenListAuthError::Unauthorized),
        402 => Err(OpenListAuthError::OtpRequired),
        403 => Err(OpenListAuthError::PermissionDenied),
        429 => Err(OpenListAuthError::RateLimited),
        200 => Err(OpenListAuthError::InvalidResponse),
        _ if envelope.code > 0 => Err(OpenListAuthError::ProtocolFailure),
        _ => Err(OpenListAuthError::InvalidResponse),
    }
}

#[uniffi::export]
pub fn ct_openlist_list_page(
    base_url: String,
    token: String,
    path: String,
    page: u32,
    per_page: u32,
) -> OpenListAuthResult<OpenListBrowsePage> {
    if page == 0 || per_page == 0 || per_page > 500 {
        return Err(OpenListAuthError::ProtocolFailure);
    }
    let base = parse_openlist_url(&base_url)?;
    let url = openlist_path(&base, &["api", "fs", "list"])?;
    let body = serde_json::json!({
        "path": path,
        "password": "",
        "refresh": false,
        "page": page,
        "per_page": per_page,
    })
    .to_string();
    let mut request = openlist_client(&base)?
        .post(url)
        .header(reqwest::header::CONTENT_TYPE, "application/json")
        .body(body);
    if !token.is_empty() {
        request = request.header(reqwest::header::AUTHORIZATION, token);
    }
    let response = request.send().map_err(map_openlist_transport)?;
    let status = response.status();
    let text = response
        .text()
        .map_err(|_| OpenListAuthError::InvalidResponse)?;
    classify_http_status(status)?;
    let envelope = parse_envelope(&text)?;
    if envelope.code != 200 {
        return Err(match envelope.code {
            401 => OpenListAuthError::Unauthorized,
            402 => OpenListAuthError::OtpRequired,
            403 => OpenListAuthError::PermissionDenied,
            429 => OpenListAuthError::RateLimited,
            code if code > 0 => OpenListAuthError::ProtocolFailure,
            _ => OpenListAuthError::InvalidResponse,
        });
    }
    let data = envelope
        .data
        .as_ref()
        .ok_or(OpenListAuthError::InvalidResponse)?;
    let total = data
        .get("total")
        .and_then(Value::as_i64)
        .filter(|value| *value >= 0)
        .ok_or(OpenListAuthError::InvalidResponse)?;
    let entries = match data.get("content") {
        Some(Value::Null) => Vec::new(),
        Some(Value::Array(values)) => values
            .iter()
            .map(|value| {
                let object = value
                    .as_object()
                    .ok_or(OpenListAuthError::InvalidResponse)?;
                let name = object
                    .get("name")
                    .and_then(Value::as_str)
                    .ok_or(OpenListAuthError::InvalidResponse)?
                    .to_owned();
                let size = object
                    .get("size")
                    .and_then(Value::as_i64)
                    .ok_or(OpenListAuthError::InvalidResponse)?;
                let is_dir = object
                    .get("is_dir")
                    .and_then(Value::as_bool)
                    .ok_or(OpenListAuthError::InvalidResponse)?;
                let entry_type = object
                    .get("type")
                    .and_then(Value::as_i64)
                    .and_then(|value| i32::try_from(value).ok())
                    .ok_or(OpenListAuthError::InvalidResponse)?;
                Ok(OpenListBrowseEntry {
                    name,
                    size,
                    is_dir,
                    entry_type,
                })
            })
            .collect::<OpenListAuthResult<Vec<_>>>()?,
        None => return Err(OpenListAuthError::InvalidResponse),
        Some(_) => return Err(OpenListAuthError::InvalidResponse),
    };
    Ok(OpenListBrowsePage { entries, total })
}

struct OpenListEnvelope {
    code: i64,
    data: Option<Value>,
}

fn parse_envelope(text: &str) -> OpenListAuthResult<OpenListEnvelope> {
    let root: Value = serde_json::from_str(text).map_err(|_| OpenListAuthError::InvalidResponse)?;
    let object = root.as_object().ok_or(OpenListAuthError::InvalidResponse)?;
    let code = object
        .get("code")
        .and_then(Value::as_i64)
        .ok_or(OpenListAuthError::InvalidResponse)?;
    let data = match object.get("data") {
        Some(Value::Object(value)) => Some(Value::Object(value.clone())),
        Some(Value::Null) | None => None,
        Some(_) => return Err(OpenListAuthError::InvalidResponse),
    };
    Ok(OpenListEnvelope { code, data })
}

fn classify_http_status(status: reqwest::StatusCode) -> OpenListAuthResult<()> {
    if status.is_success() {
        return Ok(());
    }
    Err(match status {
        reqwest::StatusCode::UNAUTHORIZED => OpenListAuthError::Unauthorized,
        reqwest::StatusCode::FORBIDDEN => OpenListAuthError::PermissionDenied,
        reqwest::StatusCode::PAYMENT_REQUIRED => OpenListAuthError::OtpRequired,
        reqwest::StatusCode::TOO_MANY_REQUESTS => OpenListAuthError::RateLimited,
        _ => OpenListAuthError::Unavailable,
    })
}

fn parse_openlist_url(value: &str) -> OpenListAuthResult<reqwest::Url> {
    let url = reqwest::Url::parse(value.trim_end_matches('/'))
        .map_err(|_| OpenListAuthError::InvalidAddress)?;
    if !matches!(url.scheme(), "http" | "https")
        || url.host_str().is_none()
        || !url.username().is_empty()
        || url.password().is_some()
        || url.query().is_some()
        || url.fragment().is_some()
    {
        return Err(OpenListAuthError::InvalidAddress);
    }
    Ok(url)
}

fn openlist_path(base: &reqwest::Url, segments: &[&str]) -> OpenListAuthResult<reqwest::Url> {
    let mut url = base.clone();
    let mut path = url
        .path_segments_mut()
        .map_err(|_| OpenListAuthError::InvalidAddress)?;
    for segment in segments {
        path.push(segment);
    }
    drop(path);
    Ok(url)
}

fn openlist_client(base: &reqwest::Url) -> OpenListAuthResult<Client> {
    let origin = base.clone();
    Client::builder()
        .connect_timeout(CONNECT_TIMEOUT)
        .timeout(REQUEST_TIMEOUT)
        .redirect(reqwest::redirect::Policy::custom(move |attempt| {
            if attempt.previous().len() >= 5 {
                attempt.error("too many OpenList redirects")
            } else if same_origin(&origin, attempt.url()) {
                attempt.follow()
            } else {
                attempt.error("cross-origin OpenList redirect rejected")
            }
        }))
        .build()
        .map_err(|_| OpenListAuthError::Unavailable)
}

fn same_origin(left: &reqwest::Url, right: &reqwest::Url) -> bool {
    left.scheme() == right.scheme()
        && left.host() == right.host()
        && left.port_or_known_default() == right.port_or_known_default()
}

fn map_openlist_transport(error: reqwest::Error) -> OpenListAuthError {
    if error.is_timeout() {
        OpenListAuthError::Timeout
    } else {
        OpenListAuthError::Unavailable
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::{Read, Write};
    use std::net::TcpListener;
    use std::sync::mpsc;
    use std::thread;
    use std::time::Duration;

    fn read_request(stream: &mut std::net::TcpStream) -> String {
        let mut request = Vec::new();
        let mut chunk = [0_u8; 8 * 1024];
        loop {
            let count = stream.read(&mut chunk).expect("read request");
            if count == 0 {
                break;
            }
            request.extend_from_slice(&chunk[..count]);
            let Some(header_end) = request.windows(4).position(|window| window == b"\r\n\r\n")
            else {
                continue;
            };
            let headers = String::from_utf8_lossy(&request[..header_end]);
            let content_length = headers
                .lines()
                .find_map(|line| {
                    let (name, value) = line.split_once(':')?;
                    name.eq_ignore_ascii_case("content-length")
                        .then(|| value.trim().parse::<usize>().ok())
                        .flatten()
                })
                .unwrap_or(0);
            if request.len() >= header_end + 4 + content_length {
                break;
            }
        }
        String::from_utf8_lossy(&request).into_owned()
    }

    fn mock(response: &'static str) -> (String, mpsc::Receiver<String>, thread::JoinHandle<()>) {
        let listener = TcpListener::bind("127.0.0.1:0").expect("bind mock");
        listener.set_nonblocking(false).expect("configure listener");
        let address = format!("http://{}", listener.local_addr().expect("mock address"));
        let (sender, receiver) = mpsc::channel();
        let handle = thread::spawn(move || {
            let (mut stream, _) = listener.accept().expect("mock accept");
            stream
                .set_read_timeout(Some(Duration::from_secs(2)))
                .expect("read timeout");
            sender
                .send(read_request(&mut stream))
                .expect("send request");
            stream
                .write_all(response.as_bytes())
                .expect("write response");
        });
        (address, receiver, handle)
    }

    #[test]
    fn login_sends_json_and_returns_opaque_token() {
        let (base, requests, server) = mock(
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{\"code\":200,\"data\":{\"token\":\"opaque\"}}",
        );
        let token =
            ct_openlist_login(base, "alice".into(), "password".into(), "".into()).expect("login");
        let request = requests
            .recv_timeout(Duration::from_secs(2))
            .expect("request");
        server.join().expect("server");
        assert_eq!(token, "opaque");
        assert!(request.contains("\"username\":\"alice\""));
        assert!(request.contains("\"otp_code\":\"\""));
    }

    #[test]
    fn http_and_envelope_otp_are_typed() {
        let (base, requests, server) =
            mock("HTTP/1.1 402 Payment Required\r\nConnection: close\r\n\r\n");
        let error = ct_openlist_login(base, "a".into(), "p".into(), "".into()).unwrap_err();
        let _ = requests.recv_timeout(Duration::from_secs(2));
        server.join().expect("server");
        assert!(matches!(error, OpenListAuthError::OtpRequired));
        assert!(!error.to_string().contains("password"));

        let (base, requests, server) =
            mock("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n{\"code\":402,\"data\":null}");
        let error = ct_openlist_login(base, "a".into(), "p".into(), "".into()).unwrap_err();
        let _ = requests.recv_timeout(Duration::from_secs(2));
        server.join().expect("server");
        assert!(matches!(error, OpenListAuthError::OtpRequired));

        let (base, requests, server) =
            mock("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n{\"code\":401,\"data\":null}");
        let error = ct_openlist_validate_session(base, "".into()).unwrap_err();
        let _ = requests.recv_timeout(Duration::from_secs(2));
        server.join().expect("server");
        assert!(matches!(error, OpenListAuthError::Unauthorized));
    }

    #[test]
    fn guest_validation_omits_authorization_header() {
        let (base, requests, server) =
            mock("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n{\"code\":200,\"data\":{}}");
        ct_openlist_validate_session(base, "".into()).expect("guest validation");
        let request = requests
            .recv_timeout(Duration::from_secs(2))
            .expect("request");
        server.join().expect("server");
        assert!(!request.to_ascii_lowercase().contains("authorization:"));
    }

    #[test]
    fn session_validation_sends_raw_authorization_value() {
        let (base, requests, server) =
            mock("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n{\"code\":200,\"data\":{}}");
        ct_openlist_validate_session(base, "opaque-token".into()).expect("validation");
        let request = requests
            .recv_timeout(Duration::from_secs(2))
            .expect("request");
        server.join().expect("server");
        let lower = request.to_ascii_lowercase();
        assert!(lower.contains("authorization: opaque-token"));
        assert!(!lower.contains("bearer opaque-token"));
    }

    #[test]
    fn otp_success_sends_otp_code_and_returns_token() {
        let (base, requests, server) = mock(
            "HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n{\"code\":200,\"data\":{\"token\":\"otp-token\"}}",
        );
        let token = ct_openlist_login(base, "alice".into(), "password".into(), "123456".into())
            .expect("otp login");
        let request = requests
            .recv_timeout(Duration::from_secs(2))
            .expect("request");
        server.join().expect("server");
        assert_eq!(token, "otp-token");
        assert!(request.contains("\"otp_code\":\"123456\""));
    }

    #[test]
    fn malformed_or_missing_token_is_invalid_response_and_errors_are_redacted() {
        let (base, requests, server) =
            mock("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n{\"code\":200,\"data\":{}}");
        let error = ct_openlist_login(
            base,
            "user-secret".into(),
            "password-secret".into(),
            "otp-secret".into(),
        )
        .unwrap_err();
        let _ = requests.recv_timeout(Duration::from_secs(2));
        server.join().expect("server");
        assert!(matches!(error, OpenListAuthError::InvalidResponse));
        let text = error.to_string();
        assert!(!text.contains("password-secret"));
        assert!(!text.contains("otp-secret"));
        assert!(!text.contains("user-secret"));
    }

    #[test]
    fn credential_bearing_or_non_http_addresses_are_rejected() {
        for address in [
            "ftp://openlist.example",
            "https://user:password@openlist.example",
            "https://openlist.example/?token=secret",
        ] {
            let error =
                ct_openlist_validate_session(address.into(), "token-secret".into()).unwrap_err();
            assert!(matches!(error, OpenListAuthError::InvalidAddress));
            assert!(!error.to_string().contains("secret"));
        }
    }

    #[test]
    fn refused_connection_is_unavailable_without_echoing_endpoint() {
        let listener = TcpListener::bind("127.0.0.1:0").expect("bind unused port");
        let address = format!("http://{}", listener.local_addr().expect("port"));
        drop(listener);
        let error = ct_openlist_validate_session(address.clone(), "token-secret".into())
            .expect_err("refused connection");
        assert!(matches!(error, OpenListAuthError::Unavailable));
        assert!(!error.to_string().contains(&address));
        assert!(!error.to_string().contains("token-secret"));
    }

    #[test]
    fn http_auth_and_permission_statuses_are_authoritative() {
        for (status, expected) in [
            ("401 Unauthorized", OpenListAuthError::Unauthorized),
            ("403 Forbidden", OpenListAuthError::PermissionDenied),
        ] {
            let response = format!("HTTP/1.1 {status}\r\nConnection: close\r\n\r\n");
            let leaked: &'static str = Box::leak(response.into_boxed_str());
            let (base, requests, server) = mock(leaked);
            let error = ct_openlist_validate_session(base, "token-secret".into()).unwrap_err();
            let _ = requests.recv_timeout(Duration::from_secs(2));
            server.join().expect("server");
            assert!(matches!(
                (error, expected),
                (
                    OpenListAuthError::Unauthorized,
                    OpenListAuthError::Unauthorized
                ) | (
                    OpenListAuthError::PermissionDenied,
                    OpenListAuthError::PermissionDenied
                )
            ));
        }
    }

    #[test]
    fn list_page_preserves_raw_path_and_pagination_request() {
        let (base, requests, server) = mock(
            "HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n{\"code\":200,\"data\":{\"total\":2,\"content\":[{\"name\":\"音 乐 # % ? 😀\",\"size\":12,\"is_dir\":false,\"type\":3}]}}",
        );
        let base = format!("{base}/prefix");
        let page =
            ct_openlist_list_page(base, "opaque-token".into(), "/CJK # % ? 😀".into(), 2, 17)
                .expect("browse page");
        let request = requests
            .recv_timeout(Duration::from_secs(2))
            .expect("request");
        server.join().expect("server");
        assert!(request.starts_with("POST /prefix/api/fs/list HTTP/1.1"));
        assert!(request
            .to_ascii_lowercase()
            .contains("authorization: opaque-token"));
        assert!(request.contains("\"path\":\"/CJK # % ? 😀\""));
        assert!(request.contains("\"page\":2"));
        assert!(request.contains("\"per_page\":17"));
        assert!(request.contains("\"password\":\"\""));
        assert!(request.contains("\"refresh\":false"));
        assert_eq!(page.total, 2);
        assert_eq!(page.entries[0].name, "音 乐 # % ? 😀");
        assert_eq!(page.entries[0].entry_type, 3);
    }

    #[test]
    fn list_page_accepts_null_content_and_rejects_negative_total() {
        let (base, requests, server) = mock(
            "HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n{\"code\":200,\"data\":{\"total\":0,\"content\":null}}",
        );
        let page = ct_openlist_list_page(base, "".into(), "/".into(), 1, 50).expect("empty");
        let request = requests
            .recv_timeout(Duration::from_secs(2))
            .expect("request");
        server.join().expect("server");
        assert!(!request.to_ascii_lowercase().contains("authorization:"));
        assert!(page.entries.is_empty());

        let (base, requests, server) = mock(
            "HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n{\"code\":200,\"data\":{\"total\":-1,\"content\":[]}}",
        );
        let error = ct_openlist_list_page(base, "".into(), "/".into(), 1, 50).unwrap_err();
        let _ = requests.recv_timeout(Duration::from_secs(2));
        server.join().expect("server");
        assert!(matches!(error, OpenListAuthError::InvalidResponse));
    }

    #[test]
    fn list_page_rejects_missing_or_malformed_content_and_bounds_without_request() {
        for body in [
            r#"{"code":200,"data":{"total":1}}"#,
            r#"{"code":200,"data":{"total":1,"content":{}}}"#,
            r#"{"code":200,"data":{"total":1,"content":[{"name":"x","size":1}]}}"#,
        ] {
            let response = format!("HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n{body}");
            let leaked: &'static str = Box::leak(response.into_boxed_str());
            let (base, requests, server) = mock(leaked);
            let error = ct_openlist_list_page(base, "".into(), "/".into(), 1, 10)
                .expect_err("malformed content");
            let _ = requests.recv_timeout(Duration::from_secs(2));
            server.join().expect("server");
            assert!(matches!(error, OpenListAuthError::InvalidResponse));
        }

        for (page, per_page) in [(0u32, 10u32), (1, 0), (1, 501)] {
            let error = ct_openlist_list_page(
                "http://127.0.0.1:1".into(),
                "".into(),
                "/".into(),
                page,
                per_page,
            )
            .expect_err("invalid bounds");
            assert!(matches!(error, OpenListAuthError::ProtocolFailure));
        }
    }

    #[test]
    fn list_page_http_and_envelope_auth_errors_are_typed() {
        for status in ["401 Unauthorized", "403 Forbidden", "429 Too Many Requests"] {
            let response = format!("HTTP/1.1 {status}\r\nConnection: close\r\n\r\n");
            let leaked: &'static str = Box::leak(response.into_boxed_str());
            let (base, requests, server) = mock(leaked);
            let error = ct_openlist_list_page(base, "secret-token".into(), "/".into(), 1, 10)
                .expect_err("http error");
            let _ = requests.recv_timeout(Duration::from_secs(2));
            server.join().expect("server");
            assert!(!error.to_string().contains("secret-token"));
            assert!(matches!(
                (status, error),
                ("401 Unauthorized", OpenListAuthError::Unauthorized)
                    | ("403 Forbidden", OpenListAuthError::PermissionDenied)
                    | ("429 Too Many Requests", OpenListAuthError::RateLimited)
            ));
        }
        for code in [401, 403, 429] {
            let response = format!(
                "HTTP/1.1 200 OK\r\nConnection: close\r\n\r\n{{\"code\":{code},\"data\":null}}"
            );
            let leaked: &'static str = Box::leak(response.into_boxed_str());
            let (base, requests, server) = mock(leaked);
            let error = ct_openlist_list_page(base, "".into(), "/".into(), 1, 10)
                .expect_err("envelope error");
            let _ = requests.recv_timeout(Duration::from_secs(2));
            server.join().expect("server");
            assert!(matches!(
                (code, error),
                (401, OpenListAuthError::Unauthorized)
                    | (403, OpenListAuthError::PermissionDenied)
                    | (429, OpenListAuthError::RateLimited)
            ));
        }
    }
}
