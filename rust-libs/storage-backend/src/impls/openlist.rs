use std::{
    collections::HashSet,
    fmt,
    sync::Mutex,
    time::{Duration, SystemTime, UNIX_EPOCH},
};

use futures_util::future::BoxFuture;
use reqwest::header::{HeaderMap, HeaderName, HeaderValue};
use reqwest::{header, Client, StatusCode, Url};
use serde_json::Value;
use tokio::sync::Mutex as AsyncMutex;

use crate::{
    backend::{ByteRange, Entry, RangeResponse, StreamFile},
    StorageBackend, StorageBackendError, StorageBackendResult,
};

/// Credentials are intentionally kept in memory and are never included in Debug output.
pub struct BuildOpenListArg {
    pub base_url: String,
    pub token: String,
    pub timeout: Duration,
}

impl fmt::Debug for BuildOpenListArg {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("BuildOpenListArg")
            .field("base_url", &self.base_url)
            .field("token", &"<redacted>")
            .field("timeout", &self.timeout)
            .finish()
    }
}

pub struct OpenListBackend {
    base: Url,
    token: String,
    client: Client,
    raw_client: Client,
    timeout: Duration,
    playback_mode: bool,
    playback_state: Mutex<PlaybackState>,
    playback_refresh: AsyncMutex<()>,
}

#[derive(Clone)]
struct PlaybackRoute {
    path: String,
    target: PlaybackTarget,
    size: u64,
    content_type: Option<String>,
    expires_at_epoch_ms: Option<u64>,
    generation: u64,
}

#[derive(Clone)]
enum PlaybackTarget {
    Direct { url: Url, headers: HeaderMap },
    Proxy { url: Url },
}

struct PlaybackState {
    route: Option<PlaybackRoute>,
    expected_size: Option<u64>,
    generation: u64,
    refresh_used: bool,
}

struct GetRoute {
    raw_url: Url,
    proxy_url: Url,
    size: u64,
    proxy_expires_at_epoch_ms: Option<u64>,
}

struct LinkRoute {
    url: Url,
    headers: HeaderMap,
    expires_at_epoch_ms: Option<u64>,
}

enum FetchError {
    Stale,
    RangeUnsupported(u16),
    Hard(StorageBackendError),
}

enum SelectionError {
    Stale,
    Hard(StorageBackendError),
}

impl OpenListBackend {
    pub fn new(arg: BuildOpenListArg) -> StorageBackendResult<Self> {
        Self::new_inner(arg, false)
    }

    pub fn new_playback(arg: BuildOpenListArg) -> StorageBackendResult<Self> {
        Self::new_inner(arg, true)
    }

    fn new_inner(arg: BuildOpenListArg, playback_mode: bool) -> StorageBackendResult<Self> {
        let base = Url::parse(arg.base_url.trim_end_matches('/'))
            .map_err(|_| StorageBackendError::UrlParseError("invalid OpenList address".into()))?;
        if !matches!(base.scheme(), "http" | "https")
            || base.host_str().is_none()
            || !base.username().is_empty()
            || base.password().is_some()
            || base.query().is_some()
            || base.fragment().is_some()
        {
            return Err(StorageBackendError::UrlParseError(
                "invalid OpenList address".into(),
            ));
        }
        let client = Client::builder()
            .connect_timeout(arg.timeout)
            .timeout(arg.timeout)
            .redirect(reqwest::redirect::Policy::custom({
                let origin = base.clone();
                move |attempt| {
                    if attempt.previous().len() >= 5 {
                        attempt.error("too many OpenList redirects")
                    } else if same_origin(&origin, attempt.url()) {
                        attempt.follow()
                    } else {
                        attempt.error("cross-origin OpenList redirect rejected")
                    }
                }
            }))
            .build()
            .map_err(|_| StorageBackendError::ConnectionLost)?;
        let raw_client = Client::builder()
            .connect_timeout(arg.timeout)
            .timeout(arg.timeout)
            .build()
            .map_err(|_| StorageBackendError::ConnectionLost)?;
        Ok(Self {
            base,
            token: arg.token,
            client,
            raw_client,
            timeout: arg.timeout,
            playback_mode,
            playback_state: Mutex::new(PlaybackState {
                route: None,
                expected_size: None,
                generation: 0,
                refresh_used: false,
            }),
            playback_refresh: AsyncMutex::new(()),
        })
    }

    fn api_url(&self, segments: &[&str]) -> StorageBackendResult<Url> {
        let mut url = self.base.clone();
        let mut path = url
            .path_segments_mut()
            .map_err(|_| StorageBackendError::UrlParseError("invalid OpenList address".into()))?;
        for segment in segments {
            path.push(segment);
        }
        drop(path);
        Ok(url)
    }

    fn request(&self, builder: reqwest::RequestBuilder) -> reqwest::RequestBuilder {
        if self.token.is_empty() {
            builder
        } else {
            builder.header(header::AUTHORIZATION, &self.token)
        }
    }

    async fn response_json(&self, response: reqwest::Response) -> StorageBackendResult<Value> {
        let status = response.status();
        if status == StatusCode::UNAUTHORIZED {
            return Err(StorageBackendError::AuthenticationFailed);
        }
        if status == StatusCode::FORBIDDEN {
            return Err(StorageBackendError::PermissionDenied);
        }
        if status == StatusCode::TOO_MANY_REQUESTS {
            return Err(StorageBackendError::RetryExhausted("rate limited".into()));
        }
        if !status.is_success() {
            return Err(StorageBackendError::ProtocolError(format!(
                "HTTP {}",
                status.as_u16()
            )));
        }
        let body = response
            .text()
            .await
            .map_err(|_| StorageBackendError::ProtocolError("invalid OpenList response".into()))?;
        serde_json::from_str(&body)
            .map_err(|_| StorageBackendError::ProtocolError("invalid OpenList response".into()))
    }

    async fn list_page(&self, path: String, page: u32) -> StorageBackendResult<(Vec<Entry>, i64)> {
        let url = self.api_url(&["api", "fs", "list"])?;
        let body = serde_json::json!({
            "path": path,
            "password": "",
            "refresh": false,
            "page": page,
            "per_page": 500,
        });
        let response = self
            .response_json(
                self.request(
                    self.client
                        .post(url)
                        .header(header::CONTENT_TYPE, "application/json")
                        .body(body.to_string()),
                )
                .send()
                .await
                .map_err(map_reqwest)?,
            )
            .await?;
        let code = response
            .get("code")
            .and_then(Value::as_i64)
            .unwrap_or_default();
        if code != 200 {
            return Err(match code {
                401 => StorageBackendError::AuthenticationFailed,
                403 => StorageBackendError::PermissionDenied,
                429 => StorageBackendError::RetryExhausted("rate limited".into()),
                _ => StorageBackendError::ProtocolError("OpenList list failed".into()),
            });
        }
        let data = response
            .get("data")
            .and_then(Value::as_object)
            .ok_or_else(|| {
                StorageBackendError::ProtocolError("missing OpenList list data".into())
            })?;
        let total = data
            .get("total")
            .and_then(Value::as_i64)
            .filter(|value| *value >= 0)
            .ok_or_else(|| StorageBackendError::ProtocolError("invalid OpenList total".into()))?;
        let values = match data.get("content") {
            Some(Value::Null) => Vec::new(),
            Some(Value::Array(values)) => values.clone(),
            _ => {
                return Err(StorageBackendError::ProtocolError(
                    "invalid OpenList list content".into(),
                ))
            }
        };
        values
            .iter()
            .map(|value| {
                let object = value.as_object().ok_or_else(|| {
                    StorageBackendError::ProtocolError("invalid OpenList entry".into())
                })?;
                let name = object
                    .get("name")
                    .and_then(Value::as_str)
                    .ok_or_else(|| {
                        StorageBackendError::ProtocolError("invalid OpenList entry name".into())
                    })?
                    .to_owned();
                if name.is_empty()
                    || name == "."
                    || name == ".."
                    || name.contains('/')
                    || name.contains('\0')
                {
                    return Err(StorageBackendError::InvalidPath(
                        "invalid OpenList child name".into(),
                    ));
                }
                let is_dir = object
                    .get("is_dir")
                    .and_then(Value::as_bool)
                    .ok_or_else(|| {
                        StorageBackendError::ProtocolError("invalid OpenList entry type".into())
                    })?;
                let entry_type = object
                    .get("type")
                    .and_then(Value::as_i64)
                    .unwrap_or_default();
                let size = if is_dir {
                    object
                        .get("size")
                        .and_then(Value::as_u64)
                        .and_then(|size| usize::try_from(size).ok())
                } else {
                    let value = object.get("size").ok_or_else(|| {
                        StorageBackendError::ProtocolError("missing OpenList file size".into())
                    })?;
                    let size = value.as_i64().ok_or_else(|| {
                        StorageBackendError::ProtocolError("invalid OpenList file size".into())
                    })?;
                    if size < 0 {
                        return Err(StorageBackendError::ProtocolError(
                            "negative OpenList file size".into(),
                        ));
                    }
                    Some(usize::try_from(size as u64).map_err(|_| {
                        StorageBackendError::ProtocolError(
                            "OpenList file size exceeds platform limit".into(),
                        )
                    })?)
                };
                let path = if path == "/" {
                    format!("/{name}")
                } else {
                    format!("{path}/{name}")
                };
                let parent = path
                    .rsplit_once('/')
                    .map(|(parent, _)| if parent.is_empty() { "/" } else { parent })
                    .unwrap_or("/");
                let mime_type = if is_dir {
                    None
                } else if entry_type == 3 {
                    Some("audio/octet-stream".to_string())
                } else {
                    mime_for_path(&path)
                };
                let modified_at = object
                    .get("modified")
                    .and_then(Value::as_str)
                    .and_then(parse_remote_timestamp);
                let etag = object
                    .get("hashinfo")
                    .and_then(Value::as_str)
                    .map(str::to_owned)
                    .filter(|value| !value.is_empty())
                    .or_else(|| object.get("hash_info").and_then(stable_hash_signature));
                let ctag = object
                    .get("sign")
                    .and_then(Value::as_str)
                    .map(str::to_owned)
                    .filter(|value| !value.is_empty());
                Ok(Entry {
                    name,
                    path: path.clone(),
                    size,
                    is_dir,
                    remote_id: Some(path.clone()),
                    parent_remote_id: Some(parent.to_string()),
                    mime_type,
                    etag,
                    ctag,
                    created_at: None,
                    modified_at,
                })
            })
            .collect::<StorageBackendResult<Vec<_>>>()
            .map(|entries| (entries, total))
    }
}

impl OpenListBackend {
    fn signed_path_url(&self, path: &str, sign: Option<&str>) -> StorageBackendResult<Url> {
        let mut url = self.base.clone();
        {
            let mut segments = url.path_segments_mut().map_err(|_| {
                StorageBackendError::UrlParseError("invalid OpenList address".into())
            })?;
            segments.push("p");
            for segment in path.split('/').filter(|segment| !segment.is_empty()) {
                segments.push(segment);
            }
        }
        if let Some(sign) = sign.filter(|value| !value.is_empty()) {
            url.query_pairs_mut().append_pair("sign", sign);
        }
        Ok(url)
    }

    async fn route_from_get(&self, path: &str) -> StorageBackendResult<GetRoute> {
        let url = self.api_url(&["api", "fs", "get"])?;
        let body = serde_json::json!({"path": path, "password": ""});
        let response = self
            .response_json(
                self.request(
                    self.client
                        .post(url)
                        .header(header::CONTENT_TYPE, "application/json")
                        .body(body.to_string()),
                )
                .send()
                .await
                .map_err(map_reqwest)?,
            )
            .await?;
        let code = response
            .get("code")
            .and_then(Value::as_i64)
            .unwrap_or_default();
        if code != 200 {
            return Err(match code {
                401 => StorageBackendError::AuthenticationFailed,
                403 => StorageBackendError::PermissionDenied,
                429 => StorageBackendError::RetryExhausted("rate limited".into()),
                _ => StorageBackendError::ProtocolError("OpenList get failed".into()),
            });
        }
        let data = response
            .get("data")
            .and_then(Value::as_object)
            .ok_or_else(|| {
                StorageBackendError::ProtocolError("missing OpenList get data".into())
            })?;
        let raw_url = data
            .get("raw_url")
            .and_then(Value::as_str)
            .filter(|value| !value.is_empty())
            .ok_or_else(|| StorageBackendError::ProtocolError("missing OpenList raw URL".into()))?;
        let raw_url = Url::parse(raw_url)
            .map_err(|_| StorageBackendError::ProtocolError("invalid OpenList raw URL".into()))?;
        validate_resource_url(&raw_url, "raw")?;
        let size = data
            .get("size")
            .and_then(Value::as_u64)
            .ok_or_else(|| StorageBackendError::ProtocolError("missing OpenList size".into()))?;
        let sign = data
            .get("sign")
            .and_then(Value::as_str)
            .filter(|value| !value.is_empty())
            .map(str::to_owned);
        let proxy_url = self.signed_path_url(path, sign.as_deref())?;
        let proxy_expires_at_epoch_ms = sign.as_deref().and_then(parse_sign_expiry_epoch_ms);
        Ok(GetRoute {
            raw_url,
            proxy_url,
            size,
            proxy_expires_at_epoch_ms,
        })
    }

    async fn route_from_link(&self, path: &str) -> StorageBackendResult<Option<LinkRoute>> {
        let url = self.api_url(&["api", "fs", "link"])?;
        let body = serde_json::json!({"path": path, "password": ""});
        let response = self
            .request(
                self.client
                    .post(url)
                    .header(header::CONTENT_TYPE, "application/json")
                    .body(body.to_string()),
            )
            .send()
            .await;
        let Ok(response) = response else {
            return Ok(None);
        };
        if !response.status().is_success() {
            return Ok(None);
        }
        let body = match response.text().await {
            Ok(body) => body,
            Err(_) => return Ok(None),
        };
        let response: Value = match serde_json::from_str(&body) {
            Ok(response) => response,
            Err(_) => return Ok(None),
        };
        if response.get("code").and_then(Value::as_i64) != Some(200) {
            return Ok(None);
        }
        let data = response
            .get("data")
            .and_then(Value::as_object)
            .ok_or_else(|| {
                StorageBackendError::ProtocolError("missing OpenList link data".into())
            })?;
        let raw_url = data
            .get("url")
            .and_then(Value::as_str)
            .filter(|value| !value.is_empty());
        let raw_url = raw_url.ok_or_else(|| {
            StorageBackendError::ProtocolError("missing OpenList link URL".into())
        })?;
        let url = Url::parse(raw_url)
            .map_err(|_| StorageBackendError::ProtocolError("invalid OpenList link URL".into()))?;
        validate_resource_url(&url, "link")?;
        let headers = parse_link_headers(data.get("header"))?;
        let expires_at_epoch_ms = match data.get("Expiration") {
            None | Some(Value::Null) => None,
            Some(value) => {
                let duration_ns = value.as_i64().filter(|value| *value >= 0).ok_or_else(|| {
                    StorageBackendError::ProtocolError("invalid OpenList link expiration".into())
                })?;
                (duration_ns > 0).then(|| {
                    now_epoch_ms().saturating_add((duration_ns as u64).div_ceil(1_000_000))
                })
            }
        };
        Ok(Some(LinkRoute {
            url,
            headers,
            expires_at_epoch_ms,
        }))
    }

    async fn fetch_url(
        &self,
        url: &Url,
        headers: &HeaderMap,
        range: ByteRange,
        expected_total: u64,
    ) -> Result<RangeResponse, FetchError> {
        let mut request = if headers.is_empty() {
            self.raw_client.get(url.clone())
        } else {
            let origin = url.clone();
            let client = Client::builder()
                .connect_timeout(self.timeout)
                .timeout(self.timeout)
                .redirect(reqwest::redirect::Policy::custom(move |attempt| {
                    if attempt.previous().len() >= 5 {
                        attempt.error("too many OpenList link redirects")
                    } else if same_origin(&origin, attempt.url()) {
                        attempt.follow()
                    } else {
                        attempt.error("cross-origin OpenList link redirect rejected")
                    }
                }))
                .build()
                .map_err(|_| FetchError::Hard(StorageBackendError::ConnectionLost))?;
            let mut request = client.get(url.clone());
            for (name, value) in headers {
                request = request.header(name, value);
            }
            request
        };
        request = request.header(
            header::RANGE,
            format!("bytes={}-{}", range.start, range.end_inclusive),
        );
        let response = request
            .send()
            .await
            .map_err(|error| FetchError::Hard(map_reqwest(error)))?;
        read_strict_range_response(response, range, expected_total).await
    }

    async fn fetch_playback_route(
        &self,
        route: &PlaybackRoute,
        range: ByteRange,
    ) -> Result<RangeResponse, FetchError> {
        let mut response = match &route.target {
            PlaybackTarget::Direct { url, headers } => {
                self.fetch_url(url, headers, range, route.size).await
            }
            PlaybackTarget::Proxy { url } => {
                self.fetch_url(url, &HeaderMap::new(), range, route.size)
                    .await
            }
        }?;
        if response.content_type.is_none() {
            response.content_type.clone_from(&route.content_type);
        }
        Ok(response)
    }

    async fn select_playback_route(
        &self,
        path: &str,
        range: ByteRange,
        expected_size: Option<u64>,
        generation: u64,
    ) -> Result<(PlaybackRoute, RangeResponse), SelectionError> {
        let get = self
            .route_from_get(path)
            .await
            .map_err(SelectionError::Hard)?;
        if let Some(expected_size) = expected_size {
            if get.size != expected_size {
                return Err(SelectionError::Hard(StorageBackendError::ProtocolError(
                    "OpenList playback size changed".into(),
                )));
            }
        }
        if range.end_inclusive >= get.size {
            return Err(SelectionError::Hard(StorageBackendError::InvalidRange {
                start: range.start,
                end_inclusive: range.end_inclusive,
            }));
        }

        match self
            .fetch_url(&get.raw_url, &HeaderMap::new(), range, get.size)
            .await
        {
            Ok(response) => {
                let route = PlaybackRoute {
                    path: path.to_owned(),
                    target: PlaybackTarget::Direct {
                        url: get.raw_url,
                        headers: HeaderMap::new(),
                    },
                    size: get.size,
                    content_type: response.content_type.clone(),
                    expires_at_epoch_ms: None,
                    generation,
                };
                return Ok((route, response));
            }
            Err(FetchError::Stale | FetchError::RangeUnsupported(_)) => {}
            Err(FetchError::Hard(error)) => return Err(SelectionError::Hard(error)),
        }

        let link = self
            .route_from_link(path)
            .await
            .map_err(SelectionError::Hard)?;
        if let Some(link) = link {
            let expired = link
                .expires_at_epoch_ms
                .is_some_and(|expires| expires <= now_epoch_ms());
            if !expired {
                match self
                    .fetch_url(&link.url, &link.headers, range, get.size)
                    .await
                {
                    Ok(response) => {
                        let route = PlaybackRoute {
                            path: path.to_owned(),
                            target: PlaybackTarget::Direct {
                                url: link.url,
                                headers: link.headers,
                            },
                            size: get.size,
                            content_type: response.content_type.clone(),
                            expires_at_epoch_ms: link.expires_at_epoch_ms,
                            generation,
                        };
                        return Ok((route, response));
                    }
                    Err(FetchError::Stale | FetchError::RangeUnsupported(_)) => {}
                    Err(FetchError::Hard(error)) => return Err(SelectionError::Hard(error)),
                }
            }
        }

        if get
            .proxy_expires_at_epoch_ms
            .is_some_and(|expires| expires <= now_epoch_ms())
        {
            return Err(SelectionError::Stale);
        }
        match self
            .fetch_url(&get.proxy_url, &HeaderMap::new(), range, get.size)
            .await
        {
            Ok(response) => {
                let route = PlaybackRoute {
                    path: path.to_owned(),
                    target: PlaybackTarget::Proxy { url: get.proxy_url },
                    size: get.size,
                    content_type: response.content_type.clone(),
                    expires_at_epoch_ms: get.proxy_expires_at_epoch_ms,
                    generation,
                };
                Ok((route, response))
            }
            Err(FetchError::Stale) => Err(SelectionError::Stale),
            Err(FetchError::RangeUnsupported(status)) => Err(SelectionError::Hard(
                StorageBackendError::RangeNotSupported { status },
            )),
            Err(FetchError::Hard(error)) => Err(SelectionError::Hard(error)),
        }
    }

    async fn standard_range(
        &self,
        path: &str,
        range: ByteRange,
    ) -> StorageBackendResult<RangeResponse> {
        let get = self.route_from_get(path).await?;
        if range.end_inclusive >= get.size {
            return Err(StorageBackendError::InvalidRange {
                start: range.start,
                end_inclusive: range.end_inclusive,
            });
        }
        match self
            .fetch_url(&get.raw_url, &HeaderMap::new(), range, get.size)
            .await
        {
            Ok(response) => Ok(response),
            Err(FetchError::RangeUnsupported(_)) => self
                .fetch_url(&get.proxy_url, &HeaderMap::new(), range, get.size)
                .await
                .map_err(fetch_error_into_storage),
            Err(error) => Err(fetch_error_into_storage(error)),
        }
    }

    async fn playback_range(
        &self,
        path: &str,
        range: ByteRange,
    ) -> StorageBackendResult<RangeResponse> {
        loop {
            let route = self.playback_state.lock().unwrap().route.clone();
            if let Some(route) = route {
                if route.path != path {
                    return Err(StorageBackendError::InvalidPath(
                        "OpenList playback session path changed".into(),
                    ));
                }
                if route
                    .expires_at_epoch_ms
                    .is_some_and(|expires| expires <= now_epoch_ms())
                {
                    return self.recover_playback_route(&route, range).await;
                }
                return match self.fetch_playback_route(&route, range).await {
                    Ok(response) => Ok(response),
                    Err(FetchError::Stale) => self.recover_playback_route(&route, range).await,
                    Err(error) => Err(fetch_error_into_storage(error)),
                };
            }

            let _refresh = self.playback_refresh.lock().await;
            if self.playback_state.lock().unwrap().route.is_some() {
                continue;
            }
            let generation = self
                .playback_state
                .lock()
                .unwrap()
                .generation
                .saturating_add(1);
            let selected = self
                .select_playback_route(path, range, None, generation)
                .await;
            let selected = match selected {
                Ok(selected) => selected,
                Err(SelectionError::Stale) => {
                    {
                        let mut state = self.playback_state.lock().unwrap();
                        if state.refresh_used {
                            return Err(stale_playback_error());
                        }
                        state.refresh_used = true;
                    }
                    self.select_playback_route(path, range, None, generation)
                        .await
                        .map_err(selection_error_into_storage)?
                }
                Err(SelectionError::Hard(error)) => return Err(error),
            };
            let (route, response) = selected;
            {
                let mut state = self.playback_state.lock().unwrap();
                state.expected_size = Some(route.size);
                state.generation = route.generation;
                state.route = Some(route);
            }
            return Ok(response);
        }
    }

    async fn recover_playback_route(
        &self,
        observed: &PlaybackRoute,
        range: ByteRange,
    ) -> StorageBackendResult<RangeResponse> {
        let _refresh = self.playback_refresh.lock().await;
        let current = self.playback_state.lock().unwrap().route.clone();
        if let Some(current) = current {
            if current.generation != observed.generation {
                if current
                    .expires_at_epoch_ms
                    .is_some_and(|expires| expires <= now_epoch_ms())
                {
                    return Err(stale_playback_error());
                }
                return self
                    .fetch_playback_route(&current, range)
                    .await
                    .map_err(fetch_error_into_storage);
            }
        }
        let (generation, expected_size) = {
            let mut state = self.playback_state.lock().unwrap();
            if state.refresh_used {
                return Err(stale_playback_error());
            }
            state.refresh_used = true;
            (state.generation.saturating_add(1), state.expected_size)
        };
        let (route, response) = self
            .select_playback_route(&observed.path, range, expected_size, generation)
            .await
            .map_err(selection_error_into_storage)?;
        {
            let mut state = self.playback_state.lock().unwrap();
            state.generation = route.generation;
            state.route = Some(route);
        }
        Ok(response)
    }
}

fn parse_link_headers(value: Option<&Value>) -> StorageBackendResult<HeaderMap> {
    let Some(value) = value.filter(|value| !value.is_null()) else {
        return Ok(HeaderMap::new());
    };
    let Some(object) = value.as_object() else {
        return Err(StorageBackendError::ProtocolError(
            "invalid OpenList link headers".into(),
        ));
    };
    let mut parsed = Vec::new();
    let mut connection_fields = HashSet::new();
    for (name, values) in object {
        let header_name = HeaderName::from_bytes(name.as_bytes()).map_err(|_| {
            StorageBackendError::ProtocolError("invalid OpenList link header name".into())
        })?;
        let values = values.as_array().ok_or_else(|| {
            StorageBackendError::ProtocolError("invalid OpenList link header values".into())
        })?;
        for value in values {
            let value = value.as_str().ok_or_else(|| {
                StorageBackendError::ProtocolError("invalid OpenList link header value".into())
            })?;
            let value = HeaderValue::from_str(value).map_err(|_| {
                StorageBackendError::ProtocolError("invalid OpenList link header value".into())
            })?;
            if header_name == header::CONNECTION {
                let value = value.to_str().map_err(|_| {
                    StorageBackendError::ProtocolError("invalid OpenList connection header".into())
                })?;
                for field in value.split(',') {
                    let field = field.trim().to_ascii_lowercase();
                    if !field.is_empty() {
                        connection_fields.insert(field);
                    }
                }
            }
            parsed.push((header_name.clone(), value));
        }
    }
    let mut headers = HeaderMap::new();
    for (name, value) in parsed {
        let lowered = name.as_str().to_ascii_lowercase();
        if is_forbidden_link_header(&lowered) || connection_fields.contains(&lowered) {
            return Err(StorageBackendError::ProtocolError(
                "unsupported OpenList link header".into(),
            ));
        }
        headers.append(name, value);
    }
    Ok(headers)
}

async fn read_strict_range_response(
    response: reqwest::Response,
    requested: ByteRange,
    expected_total: u64,
) -> Result<RangeResponse, FetchError> {
    let status = response.status();
    if matches!(status, StatusCode::UNAUTHORIZED | StatusCode::FORBIDDEN) {
        return Err(FetchError::Stale);
    }
    if status != StatusCode::PARTIAL_CONTENT {
        if status == StatusCode::OK {
            return Err(FetchError::RangeUnsupported(status.as_u16()));
        }
        return Err(FetchError::Hard(StorageBackendError::ProtocolError(
            format!("OpenList resource HTTP {}", status.as_u16()),
        )));
    }
    let content_range = response
        .headers()
        .get(header::CONTENT_RANGE)
        .and_then(|value| value.to_str().ok())
        .and_then(parse_content_range_strict)
        .filter(|(start, end, total)| {
            *start == requested.start && *end == requested.end_inclusive && *total == expected_total
        });
    if content_range.is_none() {
        return Err(FetchError::Hard(StorageBackendError::InvalidContentRange(
            "OpenList strict range mismatch".into(),
        )));
    }
    if let Some(content_length) = response.headers().get(header::CONTENT_LENGTH) {
        let content_length = content_length
            .to_str()
            .ok()
            .and_then(|value| value.parse().ok());
        if content_length != Some(requested.len()) {
            return Err(FetchError::Hard(StorageBackendError::ProtocolError(
                "OpenList Content-Length mismatch".into(),
            )));
        }
    }
    let content_type = response
        .headers()
        .get(header::CONTENT_TYPE)
        .and_then(|value| value.to_str().ok())
        .map(str::to_owned);
    let bytes = response
        .bytes()
        .await
        .map_err(|error| FetchError::Hard(map_reqwest(error)))?;
    if bytes.len() as u64 != requested.len() {
        return Err(FetchError::Hard(StorageBackendError::ProtocolError(
            "OpenList range body length mismatch".into(),
        )));
    }
    Ok(RangeResponse {
        bytes,
        total_size: expected_total,
        content_type,
    })
}

fn parse_content_range_strict(value: &str) -> Option<(u64, u64, u64)> {
    let (range, total) = value.strip_prefix("bytes ")?.split_once('/')?;
    let (start, end) = range.split_once('-')?;
    let start = start.parse().ok()?;
    let end = end.parse().ok()?;
    let total = total.parse().ok()?;
    (end >= start && total > 0 && end < total).then_some((start, end, total))
}

fn is_forbidden_link_header(name: &str) -> bool {
    matches!(
        name,
        "host"
            | "content-length"
            | "range"
            | "connection"
            | "keep-alive"
            | "proxy-authenticate"
            | "proxy-authorization"
            | "proxy-connection"
            | "te"
            | "trailer"
            | "transfer-encoding"
            | "upgrade"
    )
}

fn validate_resource_url(url: &Url, kind: &str) -> StorageBackendResult<()> {
    if !matches!(url.scheme(), "http" | "https")
        || url.host_str().is_none()
        || !url.username().is_empty()
        || url.password().is_some()
        || url.fragment().is_some()
    {
        return Err(StorageBackendError::ProtocolError(format!(
            "invalid OpenList {kind} URL"
        )));
    }
    Ok(())
}

fn parse_sign_expiry_epoch_ms(sign: &str) -> Option<u64> {
    let seconds = sign.rsplit_once(':')?.1.parse::<u64>().ok()?;
    (seconds != 0).then_some(seconds.saturating_mul(1_000))
}

fn fetch_error_into_storage(error: FetchError) -> StorageBackendError {
    match error {
        FetchError::Stale => stale_playback_error(),
        FetchError::RangeUnsupported(status) => StorageBackendError::RangeNotSupported { status },
        FetchError::Hard(error) => error,
    }
}

fn selection_error_into_storage(error: SelectionError) -> StorageBackendError {
    match error {
        SelectionError::Stale => stale_playback_error(),
        SelectionError::Hard(error) => error,
    }
}

fn stale_playback_error() -> StorageBackendError {
    StorageBackendError::ProtocolError("OpenList playback resource expired or unauthorized".into())
}

fn now_epoch_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}

impl StorageBackend for OpenListBackend {
    fn list(&self, dir: String) -> BoxFuture<'_, StorageBackendResult<Vec<Entry>>> {
        Box::pin(async move {
            let mut entries = Vec::new();
            let mut page = 1;
            let mut fingerprints: HashSet<Vec<(String, bool, Option<usize>)>> = HashSet::new();
            let mut seen_paths = HashSet::new();
            loop {
                let (current, total) = self.list_page(dir.clone(), page).await?;
                let fingerprint = current
                    .iter()
                    .map(|entry| (entry.name.clone(), entry.is_dir, entry.size))
                    .collect::<Vec<_>>();
                if !fingerprints.insert(fingerprint) && !current.is_empty() {
                    return Err(StorageBackendError::ProtocolError(
                        "OpenList repeated directory page".into(),
                    ));
                }
                let count = current.len();
                entries.extend(
                    current
                        .into_iter()
                        .filter(|entry| seen_paths.insert(entry.path.clone())),
                );
                if count == 0 || count < 500 || (page as i64 * 500) >= total {
                    break;
                }
                if page >= 10_000 {
                    return Err(StorageBackendError::ProtocolError(
                        "OpenList page limit exceeded".into(),
                    ));
                }
                page += 1;
            }
            Ok(entries)
        })
    }

    fn get(
        &self,
        _p: String,
        _byte_offset: u64,
    ) -> BoxFuture<'_, StorageBackendResult<StreamFile>> {
        Box::pin(async {
            Err(StorageBackendError::UnsupportedFeature(
                "OpenList full-file get".into(),
            ))
        })
    }

    fn get_range_response(
        &self,
        p: String,
        range: ByteRange,
    ) -> BoxFuture<'_, StorageBackendResult<RangeResponse>> {
        Box::pin(async move {
            if self.playback_mode {
                self.playback_range(&p, range).await
            } else {
                self.standard_range(&p, range).await
            }
        })
    }
}

fn map_reqwest(error: reqwest::Error) -> StorageBackendError {
    if error.is_timeout() {
        StorageBackendError::Timeout
    } else {
        StorageBackendError::ConnectionLost
    }
}

fn same_origin(left: &Url, right: &Url) -> bool {
    left.scheme() == right.scheme()
        && left.host() == right.host()
        && left.port_or_known_default() == right.port_or_known_default()
}

fn mime_for_path(path: &str) -> Option<String> {
    let lower = path.to_ascii_lowercase();
    let mime = if lower.ends_with(".mp3") {
        "audio/mpeg"
    } else if lower.ends_with(".flac") {
        "audio/flac"
    } else if lower.ends_with(".m4a") {
        "audio/mp4"
    } else if lower.ends_with(".ogg") || lower.ends_with(".oga") {
        "audio/ogg"
    } else if lower.ends_with(".wav") {
        "audio/wav"
    } else if lower.ends_with(".lrc") {
        "text/plain"
    } else {
        return None;
    };
    Some(mime.to_string())
}

fn parse_remote_timestamp(value: &str) -> Option<i64> {
    chrono::DateTime::parse_from_rfc3339(value)
        .ok()
        .map(|value| value.timestamp_millis())
}

fn stable_hash_signature(value: &Value) -> Option<String> {
    if matches!(value, Value::Object(object) if object.is_empty())
        || matches!(value, Value::Array(values) if values.is_empty())
    {
        return None;
    }
    fn encode(value: &Value, output: &mut String) {
        match value {
            Value::Object(object) => {
                let mut keys = object.keys().collect::<Vec<_>>();
                keys.sort();
                output.push('{');
                for key in keys {
                    output.push_str(key);
                    output.push('=');
                    encode(&object[key], output);
                    output.push(';');
                }
                output.push('}');
            }
            Value::Array(values) => {
                output.push('[');
                values.iter().for_each(|value| encode(value, output));
                output.push(']');
            }
            _ => output.push_str(&value.to_string()),
        }
    }
    let mut output = String::new();
    encode(value, &mut output);
    (!output.is_empty()).then_some(output)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::{
        future::Future,
        io::{Read, Write},
        net::TcpListener,
        sync::{
            atomic::{AtomicUsize, Ordering},
            Arc, Mutex,
        },
        thread,
    };

    use hyper::{
        service::{make_service_fn, service_fn},
        Body, Request, Response, Server,
    };

    struct MockServer {
        base_url: String,
        shutdown: Option<tokio::sync::oneshot::Sender<()>>,
        task: tokio::task::JoinHandle<()>,
    }

    impl Drop for MockServer {
        fn drop(&mut self) {
            if let Some(shutdown) = self.shutdown.take() {
                let _ = shutdown.send(());
            }
            self.task.abort();
        }
    }

    fn spawn_mock_server<F, H, Fut>(factory: F) -> MockServer
    where
        F: FnOnce(String) -> H,
        H: Fn(Request<Body>) -> Fut + Send + Sync + 'static,
        Fut: Future<Output = Response<Body>> + Send + 'static,
    {
        let listener = TcpListener::bind("127.0.0.1:0").expect("mock listener");
        listener.set_nonblocking(true).expect("nonblocking");
        let base_url = format!("http://{}", listener.local_addr().expect("mock address"));
        let handler = Arc::new(factory(base_url.clone()));
        let service = make_service_fn(move |_| {
            let handler = Arc::clone(&handler);
            async move {
                Ok::<_, std::convert::Infallible>(service_fn(move |request| {
                    let handler = Arc::clone(&handler);
                    async move { Ok::<_, std::convert::Infallible>(handler(request).await) }
                }))
            }
        });
        let (shutdown_tx, shutdown_rx) = tokio::sync::oneshot::channel();
        let server = Server::from_tcp(listener)
            .expect("mock server")
            .serve(service)
            .with_graceful_shutdown(async move {
                let _ = shutdown_rx.await;
            });
        let task = tokio::spawn(async move {
            let _ = server.await;
        });
        MockServer {
            base_url,
            shutdown: Some(shutdown_tx),
            task,
        }
    }

    fn json_response(body: String) -> Response<Body> {
        Response::builder()
            .status(StatusCode::OK)
            .header(header::CONTENT_TYPE, "application/json")
            .header(header::CONTENT_LENGTH, body.len())
            .body(Body::from(body))
            .unwrap()
    }

    fn range_response(
        range: ByteRange,
        total: u64,
        bytes: &'static [u8],
        content_type: Option<&'static str>,
    ) -> Response<Body> {
        let mut response = Response::builder()
            .status(StatusCode::PARTIAL_CONTENT)
            .header(
                header::CONTENT_RANGE,
                format!("bytes {}-{}/{}", range.start, range.end_inclusive, total),
            )
            .header(header::CONTENT_LENGTH, bytes.len());
        if let Some(content_type) = content_type {
            response = response.header(header::CONTENT_TYPE, content_type);
        }
        response.body(Body::from(bytes)).unwrap()
    }

    fn get_envelope(raw_url: &str, size: u64, sign: &str) -> String {
        serde_json::json!({
            "code": 200,
            "data": {"raw_url": raw_url, "size": size, "sign": sign}
        })
        .to_string()
    }

    fn read_request(stream: &mut std::net::TcpStream) -> String {
        stream
            .set_read_timeout(Some(Duration::from_secs(2)))
            .expect("read timeout");
        let mut bytes = Vec::new();
        let mut chunk = [0_u8; 8 * 1024];
        loop {
            let count = stream.read(&mut chunk).expect("request");
            if count == 0 {
                break;
            }
            bytes.extend_from_slice(&chunk[..count]);
            let Some(header_end) = bytes.windows(4).position(|window| window == b"\r\n\r\n") else {
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

    #[tokio::test]
    async fn list_pages_preserve_raw_identity_and_deduplicate() {
        let listener = TcpListener::bind("127.0.0.1:0").expect("listener");
        let address = format!("http://{}", listener.local_addr().expect("address"));
        let requests = Arc::new(Mutex::new(Vec::new()));
        let requests_for_server = Arc::clone(&requests);
        let first_entries = (0..499)
            .map(|index| {
                serde_json::json!({
                    "name": format!("track-{index}.mp3"),
                    "size": 3,
                    "is_dir": false,
                    "type": 3,
                })
            })
            .chain([serde_json::json!({
                "name": "音 乐 %25 #? 😀 \\\\",
                "size": 3,
                "is_dir": false,
                "type": 3,
                "hashinfo": "hash-a",
                "sign": "sign-a",
                "modified": "2024-01-02T03:04:05Z",
            })])
            .collect::<Vec<_>>();
        let mut second_entries = vec![serde_json::json!({
            "name": "音 乐 %25 #? 😀 \\\\",
            "size": 3,
            "is_dir": false,
            "type": 3,
        })];
        second_entries.push(serde_json::json!({
            "name": "第二根.mp3",
            "size": 4,
            "is_dir": false,
            "type": 3,
        }));
        let first = serde_json::json!({
            "code": 200,
            "data": {"total": 501, "content": first_entries}
        })
        .to_string();
        let second = serde_json::json!({
            "code": 200,
            "data": {"total": 501, "content": second_entries}
        })
        .to_string();
        let server = thread::spawn(move || {
            for body in [first, second] {
                let (mut stream, _) = listener.accept().expect("accept");
                let request = read_request(&mut stream);
                requests_for_server.lock().unwrap().push(request.clone());
                assert!(request.starts_with("POST /prefix/api/fs/list HTTP/1.1"));
                assert!(request.contains("\"password\":\"\""));
                assert!(request.contains("\"refresh\":false"));
                assert!(request.contains("\"per_page\":500"));
                stream
                    .write_all(
                        format!(
                            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n{body}"
                        )
                        .as_bytes(),
                    )
                    .expect("response");
            }
        });
        let backend = OpenListBackend::new(BuildOpenListArg {
            base_url: format!("{address}/prefix"),
            token: "api-token".into(),
            timeout: Duration::from_secs(2),
        })
        .expect("backend");
        let entries = backend.list("/音乐/%25 #? 😀".into()).await.expect("list");
        server.join().expect("server");
        assert_eq!(entries.len(), 501);
        let special = entries
            .iter()
            .find(|entry| entry.name.starts_with("音 乐"))
            .unwrap();
        assert_eq!(special.path, "/音乐/%25 #? 😀/音 乐 %25 #? 😀 \\\\");
        assert_eq!(special.remote_id.as_deref(), Some(special.path.as_str()));
        assert_eq!(special.parent_remote_id.as_deref(), Some("/音乐/%25 #? 😀"));
        assert!(special.etag.as_deref() == Some("hash-a"));
        assert!(special.ctag.as_deref() == Some("sign-a"));
        assert_eq!(requests.lock().unwrap().len(), 2);
        assert!(requests.lock().unwrap().iter().all(|request| request
            .to_ascii_lowercase()
            .contains("authorization: api-token")));
    }

    #[tokio::test]
    async fn range_is_bounded_and_raw_request_has_no_api_authorization() {
        let listener = TcpListener::bind("127.0.0.1:0").expect("listener");
        let address = format!("http://{}", listener.local_addr().expect("address"));
        let server = thread::spawn(move || {
            let (mut api, _) = listener.accept().expect("api accept");
            let api_request = read_request(&mut api);
            assert!(api_request.starts_with("POST /prefix/api/fs/get HTTP/1.1"));
            assert!(api_request.contains("\"password\":\"\""));
            let get_body = r#"{"code":200,"data":{"raw_url":"http://127.0.0.1:0/raw","size":1073741824,"sign":""}}"#;
            // Replace the placeholder with the listener's actual port.
            let port = api.local_addr().expect("local").port();
            let get_body = get_body.replace(":0/raw", &format!(":{port}/raw"));
            api.write_all(format!(
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{get_body}",
                get_body.len()
            ).as_bytes()).expect("get response");
            let (mut raw, _) = listener.accept().expect("raw accept");
            let raw_request = read_request(&mut raw);
            assert!(raw_request.starts_with("GET /raw HTTP/1.1"));
            assert!(raw_request
                .to_ascii_lowercase()
                .contains("range: bytes=0-2"));
            assert!(!raw_request.to_ascii_lowercase().contains("authorization:"));
            raw.write_all(
                b"HTTP/1.1 206 Partial Content\r\nContent-Range: bytes 0-2/1073741824\r\nContent-Length: 3\r\nConnection: close\r\n\r\nabc"
            ).expect("raw response");
        });
        let backend = OpenListBackend::new(BuildOpenListArg {
            base_url: format!("{address}/prefix"),
            token: "api-token".into(),
            timeout: Duration::from_secs(2),
        })
        .expect("backend");
        let response = backend
            .get_range_response(
                "/very/large/%25 😀.mp3".into(),
                ByteRange::new(0, 2).unwrap(),
            )
            .await
            .expect("range");
        server.join().expect("server");
        assert_eq!(response.bytes.as_ref(), b"abc");
        assert_eq!(response.total_size, 1_073_741_824);
    }

    #[tokio::test]
    async fn playback_prefers_bare_direct_exact_range_without_link_or_proxy() {
        let get_calls = Arc::new(AtomicUsize::new(0));
        let raw_calls = Arc::new(AtomicUsize::new(0));
        let forbidden_calls = Arc::new(AtomicUsize::new(0));
        let server = spawn_mock_server({
            let get_calls = Arc::clone(&get_calls);
            let raw_calls = Arc::clone(&raw_calls);
            let forbidden_calls = Arc::clone(&forbidden_calls);
            move |base| {
                move |request: Request<Body>| {
                    let get_calls = Arc::clone(&get_calls);
                    let raw_calls = Arc::clone(&raw_calls);
                    let forbidden_calls = Arc::clone(&forbidden_calls);
                    let base = base.clone();
                    async move {
                        match request.uri().path() {
                            "/api/fs/get" => {
                                get_calls.fetch_add(1, Ordering::SeqCst);
                                assert_eq!(request.headers()[header::AUTHORIZATION], "api-token");
                                json_response(get_envelope(&format!("{base}/raw"), 10, ""))
                            }
                            "/raw" => {
                                raw_calls.fetch_add(1, Ordering::SeqCst);
                                assert_eq!(request.headers()[header::RANGE], "bytes=4-6");
                                assert!(request.headers().get(header::AUTHORIZATION).is_none());
                                range_response(
                                    ByteRange::new(4, 6).unwrap(),
                                    10,
                                    b"456",
                                    Some("audio/flac"),
                                )
                            }
                            "/api/fs/link" | "/p/track" => {
                                forbidden_calls.fetch_add(1, Ordering::SeqCst);
                                Response::builder()
                                    .status(StatusCode::INTERNAL_SERVER_ERROR)
                                    .body(Body::empty())
                                    .unwrap()
                            }
                            _ => Response::builder()
                                .status(StatusCode::NOT_FOUND)
                                .body(Body::empty())
                                .unwrap(),
                        }
                    }
                }
            }
        });
        let backend = OpenListBackend::new_playback(BuildOpenListArg {
            base_url: server.base_url.clone(),
            token: "api-token".into(),
            timeout: Duration::from_secs(2),
        })
        .unwrap();

        let response = backend
            .get_range_response("/track".into(), ByteRange::new(4, 6).unwrap())
            .await
            .unwrap();

        assert_eq!(response.bytes.as_ref(), b"456");
        assert_eq!(response.content_type.as_deref(), Some("audio/flac"));
        assert_eq!(get_calls.load(Ordering::SeqCst), 1);
        assert_eq!(raw_calls.load(Ordering::SeqCst), 1);
        assert_eq!(forbidden_calls.load(Ordering::SeqCst), 0);
    }

    #[tokio::test]
    async fn playback_uses_link_multivalue_headers_only_after_bare_unauthorized() {
        let linked_calls = Arc::new(AtomicUsize::new(0));
        let proxy_calls = Arc::new(AtomicUsize::new(0));
        let server = spawn_mock_server({
            let linked_calls = Arc::clone(&linked_calls);
            let proxy_calls = Arc::clone(&proxy_calls);
            move |base| {
                move |request: Request<Body>| {
                    let linked_calls = Arc::clone(&linked_calls);
                    let proxy_calls = Arc::clone(&proxy_calls);
                    let base = base.clone();
                    async move {
                        match request.uri().path() {
                            "/api/fs/get" => {
                                json_response(get_envelope(&format!("{base}/bare"), 3, ""))
                            }
                            "/bare" => Response::builder()
                                .status(StatusCode::UNAUTHORIZED)
                                .body(Body::empty())
                                .unwrap(),
                            "/api/fs/link" => json_response(
                                serde_json::json!({
                                    "code": 200,
                                    "data": {
                                        "url": format!("{base}/linked"),
                                        "header": {
                                            "Cookie": ["first=1", "second=2"],
                                            "X-Multi": ["one", "two"],
                                            "Authorization": ["provider-secret"]
                                        },
                                        "Expiration": 60_000_000_000_i64
                                    }
                                })
                                .to_string(),
                            ),
                            "/linked" => {
                                linked_calls.fetch_add(1, Ordering::SeqCst);
                                assert_eq!(request.headers()[header::RANGE], "bytes=0-2");
                                assert_eq!(
                                    request.headers()[header::AUTHORIZATION],
                                    "provider-secret"
                                );
                                assert_eq!(
                                    request
                                        .headers()
                                        .get_all("cookie")
                                        .iter()
                                        .map(|value| value.to_str().unwrap())
                                        .collect::<Vec<_>>(),
                                    vec!["first=1", "second=2"]
                                );
                                assert_eq!(
                                    request
                                        .headers()
                                        .get_all("x-multi")
                                        .iter()
                                        .map(|value| value.to_str().unwrap())
                                        .collect::<Vec<_>>(),
                                    vec!["one", "two"]
                                );
                                range_response(
                                    ByteRange::new(0, 2).unwrap(),
                                    3,
                                    b"abc",
                                    Some("audio/flac"),
                                )
                            }
                            "/p/track" => {
                                proxy_calls.fetch_add(1, Ordering::SeqCst);
                                Response::builder()
                                    .status(StatusCode::INTERNAL_SERVER_ERROR)
                                    .body(Body::empty())
                                    .unwrap()
                            }
                            _ => Response::builder()
                                .status(StatusCode::NOT_FOUND)
                                .body(Body::empty())
                                .unwrap(),
                        }
                    }
                }
            }
        });
        let backend = OpenListBackend::new_playback(BuildOpenListArg {
            base_url: server.base_url.clone(),
            token: "api-token".into(),
            timeout: Duration::from_secs(2),
        })
        .unwrap();

        let response = backend
            .get_range_response("/track".into(), ByteRange::new(0, 2).unwrap())
            .await
            .unwrap();

        assert_eq!(response.bytes.as_ref(), b"abc");
        assert_eq!(linked_calls.load(Ordering::SeqCst), 1);
        assert_eq!(proxy_calls.load(Ordering::SeqCst), 0);
    }

    #[tokio::test]
    async fn playback_falls_back_to_signed_and_unsigned_proxy_when_link_is_unavailable() {
        for (sign, expected_query) in [("opaque-sign", Some("sign=opaque-sign")), ("", None)] {
            let proxy_calls = Arc::new(AtomicUsize::new(0));
            let server = spawn_mock_server({
                let proxy_calls = Arc::clone(&proxy_calls);
                move |base| {
                    move |request: Request<Body>| {
                        let proxy_calls = Arc::clone(&proxy_calls);
                        let base = base.clone();
                        async move {
                            match request.uri().path() {
                                "/api/fs/get" => {
                                    json_response(get_envelope(&format!("{base}/bare"), 3, sign))
                                }
                                "/bare" => Response::builder()
                                    .status(StatusCode::OK)
                                    .body(Body::from("abc"))
                                    .unwrap(),
                                "/api/fs/link" => Response::builder()
                                    .status(StatusCode::NOT_FOUND)
                                    .body(Body::empty())
                                    .unwrap(),
                                "/p/folder/track" => {
                                    proxy_calls.fetch_add(1, Ordering::SeqCst);
                                    assert_eq!(request.uri().query(), expected_query);
                                    assert_eq!(request.headers()[header::RANGE], "bytes=0-2");
                                    assert!(request.headers().get(header::AUTHORIZATION).is_none());
                                    range_response(ByteRange::new(0, 2).unwrap(), 3, b"abc", None)
                                }
                                _ => Response::builder()
                                    .status(StatusCode::NOT_FOUND)
                                    .body(Body::empty())
                                    .unwrap(),
                            }
                        }
                    }
                }
            });
            let backend = OpenListBackend::new_playback(BuildOpenListArg {
                base_url: server.base_url.clone(),
                token: "api-token".into(),
                timeout: Duration::from_secs(2),
            })
            .unwrap();

            let response = backend
                .get_range_response("/folder/track".into(), ByteRange::new(0, 2).unwrap())
                .await
                .unwrap();

            assert_eq!(response.bytes.as_ref(), b"abc");
            assert_eq!(proxy_calls.load(Ordering::SeqCst), 1);
        }
    }

    #[tokio::test]
    async fn playback_malformed_partial_content_is_hard_failure_without_fallback() {
        let link_or_proxy_calls = Arc::new(AtomicUsize::new(0));
        let server = spawn_mock_server({
            let link_or_proxy_calls = Arc::clone(&link_or_proxy_calls);
            move |base| {
                move |request: Request<Body>| {
                    let link_or_proxy_calls = Arc::clone(&link_or_proxy_calls);
                    let base = base.clone();
                    async move {
                        match request.uri().path() {
                            "/api/fs/get" => {
                                json_response(get_envelope(&format!("{base}/raw"), 3, ""))
                            }
                            "/raw" => Response::builder()
                                .status(StatusCode::PARTIAL_CONTENT)
                                .header(header::CONTENT_RANGE, "bytes 0-1/3")
                                .header(header::CONTENT_LENGTH, 3)
                                .body(Body::from("abc"))
                                .unwrap(),
                            "/api/fs/link" | "/p/track" => {
                                link_or_proxy_calls.fetch_add(1, Ordering::SeqCst);
                                Response::builder()
                                    .status(StatusCode::INTERNAL_SERVER_ERROR)
                                    .body(Body::empty())
                                    .unwrap()
                            }
                            _ => Response::builder()
                                .status(StatusCode::NOT_FOUND)
                                .body(Body::empty())
                                .unwrap(),
                        }
                    }
                }
            }
        });
        let backend = OpenListBackend::new_playback(BuildOpenListArg {
            base_url: server.base_url.clone(),
            token: "api-token".into(),
            timeout: Duration::from_secs(2),
        })
        .unwrap();

        assert!(backend
            .get_range_response("/track".into(), ByteRange::new(0, 2).unwrap())
            .await
            .is_err());
        assert_eq!(link_or_proxy_calls.load(Ordering::SeqCst), 0);
    }

    #[tokio::test]
    async fn non_ok_success_status_is_hard_failure_without_fallback() {
        for playback in [false, true] {
            let fallback_calls = Arc::new(AtomicUsize::new(0));
            let server = spawn_mock_server({
                let fallback_calls = Arc::clone(&fallback_calls);
                move |base| {
                    move |request: Request<Body>| {
                        let fallback_calls = Arc::clone(&fallback_calls);
                        let base = base.clone();
                        async move {
                            match request.uri().path() {
                                "/api/fs/get" => {
                                    json_response(get_envelope(&format!("{base}/raw"), 1, ""))
                                }
                                "/raw" => Response::builder()
                                    .status(StatusCode::NO_CONTENT)
                                    .body(Body::empty())
                                    .unwrap(),
                                "/api/fs/link" | "/p/track" => {
                                    fallback_calls.fetch_add(1, Ordering::SeqCst);
                                    range_response(ByteRange::new(0, 0).unwrap(), 1, b"x", None)
                                }
                                _ => Response::builder()
                                    .status(StatusCode::NOT_FOUND)
                                    .body(Body::empty())
                                    .unwrap(),
                            }
                        }
                    }
                }
            });
            let arg = BuildOpenListArg {
                base_url: server.base_url.clone(),
                token: "api-token".into(),
                timeout: Duration::from_secs(2),
            };
            let backend = if playback {
                OpenListBackend::new_playback(arg)
            } else {
                OpenListBackend::new(arg)
            }
            .unwrap();

            let error = backend
                .get_range_response("/track".into(), ByteRange::new(0, 0).unwrap())
                .await
                .expect_err("204 must not be treated as ignored Range");

            assert!(matches!(error, StorageBackendError::ProtocolError(_)));
            assert_eq!(fallback_calls.load(Ordering::SeqCst), 0);
        }
    }

    #[tokio::test]
    async fn link_headers_follow_same_origin_redirect_and_reject_cross_origin_leakage() {
        let same_target_calls = Arc::new(AtomicUsize::new(0));
        let same_server = spawn_mock_server({
            let same_target_calls = Arc::clone(&same_target_calls);
            move |base| {
                move |request: Request<Body>| {
                    let same_target_calls = Arc::clone(&same_target_calls);
                    let base = base.clone();
                    async move {
                        match request.uri().path() {
                            "/api/fs/get" => {
                                json_response(get_envelope(&format!("{base}/bare"), 1, ""))
                            }
                            "/bare" => Response::builder()
                                .status(StatusCode::FORBIDDEN)
                                .body(Body::empty())
                                .unwrap(),
                            "/api/fs/link" => json_response(
                                serde_json::json!({
                                    "code": 200,
                                    "data": {
                                        "url": format!("{base}/same-redirect"),
                                        "header": {"X-Provider-Secret": ["kept"]},
                                        "Expiration": 60_000_000_000_i64
                                    }
                                })
                                .to_string(),
                            ),
                            "/same-redirect" => Response::builder()
                                .status(StatusCode::FOUND)
                                .header(header::LOCATION, "/same-target")
                                .body(Body::empty())
                                .unwrap(),
                            "/same-target" => {
                                same_target_calls.fetch_add(1, Ordering::SeqCst);
                                assert_eq!(request.headers()["x-provider-secret"], "kept");
                                range_response(ByteRange::new(0, 0).unwrap(), 1, b"x", None)
                            }
                            _ => Response::builder()
                                .status(StatusCode::NOT_FOUND)
                                .body(Body::empty())
                                .unwrap(),
                        }
                    }
                }
            }
        });
        let backend = OpenListBackend::new_playback(BuildOpenListArg {
            base_url: same_server.base_url.clone(),
            token: "api-token".into(),
            timeout: Duration::from_secs(2),
        })
        .unwrap();
        assert_eq!(
            backend
                .get_range("/track".into(), ByteRange::new(0, 0).unwrap())
                .await
                .unwrap()
                .as_ref(),
            b"x"
        );
        assert_eq!(same_target_calls.load(Ordering::SeqCst), 1);

        let leaked_calls = Arc::new(AtomicUsize::new(0));
        let leak_target = spawn_mock_server({
            let leaked_calls = Arc::clone(&leaked_calls);
            move |_| {
                move |_request: Request<Body>| {
                    let leaked_calls = Arc::clone(&leaked_calls);
                    async move {
                        leaked_calls.fetch_add(1, Ordering::SeqCst);
                        range_response(ByteRange::new(0, 0).unwrap(), 1, b"x", None)
                    }
                }
            }
        });
        let proxy_calls = Arc::new(AtomicUsize::new(0));
        let cross_server = spawn_mock_server({
            let target = leak_target.base_url.clone();
            let proxy_calls = Arc::clone(&proxy_calls);
            move |base| {
                move |request: Request<Body>| {
                    let target = target.clone();
                    let proxy_calls = Arc::clone(&proxy_calls);
                    let base = base.clone();
                    async move {
                        match request.uri().path() {
                            "/api/fs/get" => {
                                json_response(get_envelope(&format!("{base}/bare"), 1, ""))
                            }
                            "/bare" => Response::builder()
                                .status(StatusCode::FORBIDDEN)
                                .body(Body::empty())
                                .unwrap(),
                            "/api/fs/link" => json_response(
                                serde_json::json!({
                                    "code": 200,
                                    "data": {
                                        "url": format!("{base}/cross-redirect"),
                                        "header": {"X-Provider-Secret": ["must-not-leak"]},
                                        "Expiration": 60_000_000_000_i64
                                    }
                                })
                                .to_string(),
                            ),
                            "/cross-redirect" => Response::builder()
                                .status(StatusCode::FOUND)
                                .header(header::LOCATION, format!("{target}/leak"))
                                .body(Body::empty())
                                .unwrap(),
                            "/p/track" => {
                                proxy_calls.fetch_add(1, Ordering::SeqCst);
                                range_response(ByteRange::new(0, 0).unwrap(), 1, b"x", None)
                            }
                            _ => Response::builder()
                                .status(StatusCode::NOT_FOUND)
                                .body(Body::empty())
                                .unwrap(),
                        }
                    }
                }
            }
        });
        let backend = OpenListBackend::new_playback(BuildOpenListArg {
            base_url: cross_server.base_url.clone(),
            token: "api-token".into(),
            timeout: Duration::from_secs(2),
        })
        .unwrap();

        assert!(backend
            .get_range("/track".into(), ByteRange::new(0, 0).unwrap())
            .await
            .is_err());
        tokio::task::yield_now().await;
        assert_eq!(leaked_calls.load(Ordering::SeqCst), 0);
        assert_eq!(proxy_calls.load(Ordering::SeqCst), 0);
    }

    #[tokio::test]
    async fn explicit_link_expiry_refreshes_once_and_second_stale_terminates() {
        let get_calls = Arc::new(AtomicUsize::new(0));
        let raw2_calls = Arc::new(AtomicUsize::new(0));
        let server = spawn_mock_server({
            let get_calls = Arc::clone(&get_calls);
            let raw2_calls = Arc::clone(&raw2_calls);
            move |base| {
                move |request: Request<Body>| {
                    let get_calls = Arc::clone(&get_calls);
                    let raw2_calls = Arc::clone(&raw2_calls);
                    let base = base.clone();
                    async move {
                        match request.uri().path() {
                            "/api/fs/get" => {
                                let call = get_calls.fetch_add(1, Ordering::SeqCst);
                                let raw = if call == 0 { "bare1" } else { "raw2" };
                                json_response(get_envelope(&format!("{base}/{raw}"), 3, ""))
                            }
                            "/bare1" => Response::builder()
                                .status(StatusCode::UNAUTHORIZED)
                                .body(Body::empty())
                                .unwrap(),
                            "/api/fs/link" => json_response(
                                serde_json::json!({
                                    "code": 200,
                                    "data": {
                                        "url": format!("{base}/linked1"),
                                        "header": {"Cookie": ["short-lived=1"]},
                                        "Expiration": 2_000_000_i64
                                    }
                                })
                                .to_string(),
                            ),
                            "/linked1" => {
                                assert_eq!(request.headers()[header::RANGE], "bytes=0-0");
                                range_response(
                                    ByteRange::new(0, 0).unwrap(),
                                    3,
                                    b"a",
                                    Some("audio/flac"),
                                )
                            }
                            "/raw2" => {
                                let call = raw2_calls.fetch_add(1, Ordering::SeqCst);
                                if call == 0 {
                                    assert_eq!(request.headers()[header::RANGE], "bytes=2-2");
                                    range_response(
                                        ByteRange::new(2, 2).unwrap(),
                                        3,
                                        b"c",
                                        Some("audio/flac"),
                                    )
                                } else {
                                    Response::builder()
                                        .status(StatusCode::FORBIDDEN)
                                        .body(Body::empty())
                                        .unwrap()
                                }
                            }
                            _ => Response::builder()
                                .status(StatusCode::NOT_FOUND)
                                .body(Body::empty())
                                .unwrap(),
                        }
                    }
                }
            }
        });
        let backend = OpenListBackend::new_playback(BuildOpenListArg {
            base_url: server.base_url.clone(),
            token: "api-token".into(),
            timeout: Duration::from_secs(2),
        })
        .unwrap();

        assert_eq!(
            backend
                .get_range("/track".into(), ByteRange::new(0, 0).unwrap())
                .await
                .unwrap()
                .as_ref(),
            b"a"
        );
        tokio::time::sleep(Duration::from_millis(10)).await;
        assert_eq!(
            backend
                .get_range("/track".into(), ByteRange::new(2, 2).unwrap())
                .await
                .unwrap()
                .as_ref(),
            b"c"
        );
        assert!(backend
            .get_range("/track".into(), ByteRange::new(1, 1).unwrap())
            .await
            .is_err());
        assert_eq!(get_calls.load(Ordering::SeqCst), 2);
        assert_eq!(raw2_calls.load(Ordering::SeqCst), 2);
    }

    #[tokio::test]
    async fn expired_sign_consumes_only_one_fresh_resolution() {
        let get_calls = Arc::new(AtomicUsize::new(0));
        let proxy_calls = Arc::new(AtomicUsize::new(0));
        let server = spawn_mock_server({
            let get_calls = Arc::clone(&get_calls);
            let proxy_calls = Arc::clone(&proxy_calls);
            move |base| {
                move |request: Request<Body>| {
                    let get_calls = Arc::clone(&get_calls);
                    let proxy_calls = Arc::clone(&proxy_calls);
                    let base = base.clone();
                    async move {
                        match request.uri().path() {
                            "/api/fs/get" => {
                                let call = get_calls.fetch_add(1, Ordering::SeqCst);
                                let seconds = SystemTime::now()
                                    .duration_since(UNIX_EPOCH)
                                    .unwrap()
                                    .as_secs();
                                let sign = if call == 0 {
                                    format!("old:{}", seconds.saturating_sub(60))
                                } else {
                                    format!("new:{}", seconds.saturating_add(60))
                                };
                                json_response(get_envelope(
                                    &format!("{base}/bare-{call}"),
                                    1,
                                    &sign,
                                ))
                            }
                            path if path.starts_with("/bare-") => Response::builder()
                                .status(StatusCode::FORBIDDEN)
                                .body(Body::empty())
                                .unwrap(),
                            "/api/fs/link" => Response::builder()
                                .status(StatusCode::NOT_FOUND)
                                .body(Body::empty())
                                .unwrap(),
                            "/p/track" => {
                                proxy_calls.fetch_add(1, Ordering::SeqCst);
                                assert!(request
                                    .uri()
                                    .query()
                                    .unwrap_or_default()
                                    .starts_with("sign=new%3A"));
                                range_response(ByteRange::new(0, 0).unwrap(), 1, b"x", None)
                            }
                            _ => Response::builder()
                                .status(StatusCode::NOT_FOUND)
                                .body(Body::empty())
                                .unwrap(),
                        }
                    }
                }
            }
        });
        let backend = OpenListBackend::new_playback(BuildOpenListArg {
            base_url: server.base_url.clone(),
            token: "api-token".into(),
            timeout: Duration::from_secs(2),
        })
        .unwrap();

        assert_eq!(
            backend
                .get_range("/track".into(), ByteRange::new(0, 0).unwrap())
                .await
                .unwrap()
                .as_ref(),
            b"x"
        );
        assert_eq!(get_calls.load(Ordering::SeqCst), 2);
        assert_eq!(proxy_calls.load(Ordering::SeqCst), 1);
    }

    #[tokio::test]
    async fn authoritative_get_failures_are_not_stale_retried() {
        for code in [401, 403, 429] {
            let get_calls = Arc::new(AtomicUsize::new(0));
            let server = spawn_mock_server({
                let get_calls = Arc::clone(&get_calls);
                move |_| {
                    move |request: Request<Body>| {
                        let get_calls = Arc::clone(&get_calls);
                        async move {
                            assert_eq!(request.uri().path(), "/api/fs/get");
                            get_calls.fetch_add(1, Ordering::SeqCst);
                            json_response(
                                serde_json::json!({"code": code, "message": "secret"}).to_string(),
                            )
                        }
                    }
                }
            });
            let backend = OpenListBackend::new_playback(BuildOpenListArg {
                base_url: server.base_url.clone(),
                token: "api-token".into(),
                timeout: Duration::from_secs(2),
            })
            .unwrap();

            let error = backend
                .get_range("/track".into(), ByteRange::new(0, 0).unwrap())
                .await
                .expect_err("get must fail");
            assert!(!error.to_string().contains("secret"));
            assert_eq!(get_calls.load(Ordering::SeqCst), 1);
        }
    }

    #[tokio::test]
    async fn concurrent_stale_ranges_share_one_refresh_generation() {
        let get_calls = Arc::new(AtomicUsize::new(0));
        let old_raw_calls = Arc::new(AtomicUsize::new(0));
        let new_raw_calls = Arc::new(AtomicUsize::new(0));
        let stale_barrier = Arc::new(tokio::sync::Barrier::new(2));
        let server = spawn_mock_server({
            let get_calls = Arc::clone(&get_calls);
            let old_raw_calls = Arc::clone(&old_raw_calls);
            let new_raw_calls = Arc::clone(&new_raw_calls);
            let stale_barrier = Arc::clone(&stale_barrier);
            move |base| {
                move |request: Request<Body>| {
                    let get_calls = Arc::clone(&get_calls);
                    let old_raw_calls = Arc::clone(&old_raw_calls);
                    let new_raw_calls = Arc::clone(&new_raw_calls);
                    let stale_barrier = Arc::clone(&stale_barrier);
                    let base = base.clone();
                    async move {
                        match request.uri().path() {
                            "/api/fs/get" => {
                                let call = get_calls.fetch_add(1, Ordering::SeqCst);
                                let raw = if call == 0 { "raw-old" } else { "raw-new" };
                                json_response(get_envelope(&format!("{base}/{raw}"), 3, ""))
                            }
                            "/raw-old" => {
                                let call = old_raw_calls.fetch_add(1, Ordering::SeqCst);
                                if call == 0 {
                                    range_response(
                                        ByteRange::new(0, 0).unwrap(),
                                        3,
                                        b"a",
                                        Some("audio/flac"),
                                    )
                                } else {
                                    stale_barrier.wait().await;
                                    Response::builder()
                                        .status(StatusCode::FORBIDDEN)
                                        .body(Body::empty())
                                        .unwrap()
                                }
                            }
                            "/raw-new" => {
                                new_raw_calls.fetch_add(1, Ordering::SeqCst);
                                match request.headers()[header::RANGE].to_str().expect("range") {
                                    "bytes=1-1" => range_response(
                                        ByteRange::new(1, 1).unwrap(),
                                        3,
                                        b"b",
                                        Some("audio/flac"),
                                    ),
                                    "bytes=2-2" => range_response(
                                        ByteRange::new(2, 2).unwrap(),
                                        3,
                                        b"c",
                                        Some("audio/flac"),
                                    ),
                                    range => panic!("unexpected range {range}"),
                                }
                            }
                            _ => Response::builder()
                                .status(StatusCode::NOT_FOUND)
                                .body(Body::empty())
                                .unwrap(),
                        }
                    }
                }
            }
        });
        let backend = Arc::new(
            OpenListBackend::new_playback(BuildOpenListArg {
                base_url: server.base_url.clone(),
                token: "api-token".into(),
                timeout: Duration::from_secs(2),
            })
            .unwrap(),
        );
        assert_eq!(
            backend
                .get_range("/track".into(), ByteRange::new(0, 0).unwrap())
                .await
                .unwrap()
                .as_ref(),
            b"a"
        );

        let first = {
            let backend = Arc::clone(&backend);
            async move {
                backend
                    .get_range("/track".into(), ByteRange::new(1, 1).unwrap())
                    .await
            }
        };
        let second = {
            let backend = Arc::clone(&backend);
            async move {
                backend
                    .get_range("/track".into(), ByteRange::new(2, 2).unwrap())
                    .await
            }
        };
        let (first, second) = tokio::join!(first, second);

        assert_eq!(first.unwrap().as_ref(), b"b");
        assert_eq!(second.unwrap().as_ref(), b"c");
        assert_eq!(get_calls.load(Ordering::SeqCst), 2);
        assert_eq!(old_raw_calls.load(Ordering::SeqCst), 3);
        assert_eq!(new_raw_calls.load(Ordering::SeqCst), 2);
        assert!(backend.playback_state.lock().unwrap().refresh_used);
    }

    #[tokio::test]
    async fn ordinary_selected_route_body_failure_does_not_refresh() {
        let get_calls = Arc::new(AtomicUsize::new(0));
        let raw_calls = Arc::new(AtomicUsize::new(0));
        let server = spawn_mock_server({
            let get_calls = Arc::clone(&get_calls);
            let raw_calls = Arc::clone(&raw_calls);
            move |base| {
                move |request: Request<Body>| {
                    let get_calls = Arc::clone(&get_calls);
                    let raw_calls = Arc::clone(&raw_calls);
                    let base = base.clone();
                    async move {
                        match request.uri().path() {
                            "/api/fs/get" => {
                                get_calls.fetch_add(1, Ordering::SeqCst);
                                json_response(get_envelope(&format!("{base}/raw"), 2, ""))
                            }
                            "/raw" => {
                                let call = raw_calls.fetch_add(1, Ordering::SeqCst);
                                if call == 0 {
                                    range_response(
                                        ByteRange::new(0, 0).unwrap(),
                                        2,
                                        b"a",
                                        Some("audio/flac"),
                                    )
                                } else {
                                    let (sender, body) = Body::channel();
                                    tokio::spawn(async move {
                                        sender.abort();
                                    });
                                    Response::builder()
                                        .status(StatusCode::PARTIAL_CONTENT)
                                        .header(header::CONTENT_RANGE, "bytes 1-1/2")
                                        .header(header::CONTENT_LENGTH, 1)
                                        .body(body)
                                        .unwrap()
                                }
                            }
                            _ => Response::builder()
                                .status(StatusCode::NOT_FOUND)
                                .body(Body::empty())
                                .unwrap(),
                        }
                    }
                }
            }
        });
        let backend = OpenListBackend::new_playback(BuildOpenListArg {
            base_url: server.base_url.clone(),
            token: "api-token".into(),
            timeout: Duration::from_secs(2),
        })
        .unwrap();
        backend
            .get_range("/track".into(), ByteRange::new(0, 0).unwrap())
            .await
            .unwrap();

        assert!(backend
            .get_range("/track".into(), ByteRange::new(1, 1).unwrap())
            .await
            .is_err());
        assert_eq!(get_calls.load(Ordering::SeqCst), 1);
        assert_eq!(raw_calls.load(Ordering::SeqCst), 2);
        assert!(!backend.playback_state.lock().unwrap().refresh_used);
    }

    #[tokio::test]
    async fn api_transport_failure_does_not_consume_stale_recovery() {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let base_url = format!("http://{}", listener.local_addr().unwrap());
        drop(listener);
        let backend = OpenListBackend::new_playback(BuildOpenListArg {
            base_url,
            token: "api-token".into(),
            timeout: Duration::from_millis(100),
        })
        .unwrap();

        assert!(backend
            .get_range("/track".into(), ByteRange::new(0, 0).unwrap())
            .await
            .is_err());
        assert!(!backend.playback_state.lock().unwrap().refresh_used);
        assert_eq!(backend.playback_state.lock().unwrap().generation, 0);
    }

    #[test]
    fn link_header_validation_rejects_crlf_hop_by_hop_and_connection_fields() {
        for headers in [
            serde_json::json!({"X-Test": ["ok\r\nInjected: yes"]}),
            serde_json::json!({"Host": ["example.com"]}),
            serde_json::json!({"Content-Length": ["1"]}),
            serde_json::json!({"Range": ["bytes=0-1"]}),
            serde_json::json!({"Keep-Alive": ["timeout=5"]}),
            serde_json::json!({"Proxy-Authenticate": ["secret"]}),
            serde_json::json!({"Proxy-Authorization": ["secret"]}),
            serde_json::json!({"TE": ["trailers"]}),
            serde_json::json!({"Trailer": ["X-Test"]}),
            serde_json::json!({"Transfer-Encoding": ["chunked"]}),
            serde_json::json!({"Upgrade": ["websocket"]}),
            serde_json::json!({"Connection": ["X-Dynamic"], "X-Dynamic": ["secret"]}),
        ] {
            assert!(parse_link_headers(Some(&headers)).is_err(), "{headers}");
        }
        let accepted = parse_link_headers(Some(
            &serde_json::json!({"Cookie": ["a=1", "b=2"], "X-Test": ["one", "two"]}),
        ))
        .unwrap();
        assert_eq!(accepted.get_all("cookie").iter().count(), 2);
        assert_eq!(accepted.get_all("x-test").iter().count(), 2);
        assert!(parse_link_headers(Some(&Value::Null)).unwrap().is_empty());
    }

    #[test]
    fn resource_urls_allow_query_but_reject_fragment() {
        assert!(validate_resource_url(
            &Url::parse("https://media.example/track?signature=allowed").unwrap(),
            "raw",
        )
        .is_ok());
        assert!(validate_resource_url(
            &Url::parse("https://media.example/track#fragment").unwrap(),
            "link",
        )
        .is_err());
    }

    #[tokio::test]
    async fn unsafe_successful_link_payloads_fail_closed_without_resource_or_proxy_request() {
        for case in ["crlf", "range", "connection", "expiration", "scheme"] {
            let forbidden_calls = Arc::new(AtomicUsize::new(0));
            let server = spawn_mock_server({
                let forbidden_calls = Arc::clone(&forbidden_calls);
                move |base| {
                    move |request: Request<Body>| {
                        let forbidden_calls = Arc::clone(&forbidden_calls);
                        let base = base.clone();
                        async move {
                            match request.uri().path() {
                                "/api/fs/get" => {
                                    json_response(get_envelope(&format!("{base}/bare"), 1, ""))
                                }
                                "/bare" => Response::builder()
                                    .status(StatusCode::FORBIDDEN)
                                    .body(Body::empty())
                                    .unwrap(),
                                "/api/fs/link" => {
                                    let mut data = serde_json::json!({
                                        "url": format!("{base}/linked"),
                                        "header": {},
                                        "Expiration": 60_000_000_000_i64
                                    });
                                    match case {
                                        "crlf" => {
                                            data["header"] = serde_json::json!({
                                                "X-Test": ["ok\r\nInjected: yes"]
                                            });
                                        }
                                        "range" => {
                                            data["header"] = serde_json::json!({
                                                "rAnGe": ["bytes=0-0"]
                                            });
                                        }
                                        "connection" => {
                                            data["header"] = serde_json::json!({
                                                "Connection": ["X-Dynamic"],
                                                "x-dynamic": ["secret"]
                                            });
                                        }
                                        "expiration" => {
                                            data["Expiration"] =
                                                serde_json::json!("not-a-duration");
                                        }
                                        "scheme" => {
                                            data["url"] = serde_json::json!("file:///secret");
                                        }
                                        _ => unreachable!(),
                                    }
                                    json_response(
                                        serde_json::json!({"code": 200, "data": data}).to_string(),
                                    )
                                }
                                "/linked" | "/p/track" => {
                                    forbidden_calls.fetch_add(1, Ordering::SeqCst);
                                    range_response(ByteRange::new(0, 0).unwrap(), 1, b"x", None)
                                }
                                _ => Response::builder()
                                    .status(StatusCode::NOT_FOUND)
                                    .body(Body::empty())
                                    .unwrap(),
                            }
                        }
                    }
                }
            });
            let backend = OpenListBackend::new_playback(BuildOpenListArg {
                base_url: server.base_url.clone(),
                token: "api-token".into(),
                timeout: Duration::from_secs(2),
            })
            .unwrap();

            assert!(backend
                .get_range("/track".into(), ByteRange::new(0, 0).unwrap())
                .await
                .is_err());
            assert_eq!(forbidden_calls.load(Ordering::SeqCst), 0, "{case}");
        }
    }

    #[tokio::test]
    async fn direct_success_without_range_falls_back_to_unsigned_same_server_path() {
        let listener = TcpListener::bind("127.0.0.1:0").expect("listener");
        let address = format!("http://{}", listener.local_addr().expect("address"));
        let server = thread::spawn(move || {
            let (mut api, _) = listener.accept().expect("api accept");
            let _ = read_request(&mut api);
            let port = api.local_addr().expect("local").port();
            let body = format!(
                r#"{{"code":200,"data":{{"raw_url":"http://127.0.0.1:{port}/raw","size":3,"sign":""}}}}"#
            );
            api.write_all(
                format!(
                    "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}",
                    body.len()
                )
                .as_bytes(),
            )
            .expect("get response");
            drop(api);

            let (mut raw, _) = listener.accept().expect("raw accept");
            let raw_request = read_request(&mut raw);
            assert!(raw_request.starts_with("GET /raw HTTP/1.1"));
            raw.write_all(b"HTTP/1.1 200 OK\r\nContent-Length: 3\r\nConnection: close\r\n\r\nabc")
                .expect("raw response");
            drop(raw);

            let (mut proxy, _) = listener.accept().expect("proxy accept");
            let proxy_request = read_request(&mut proxy);
            assert!(proxy_request.starts_with("GET /prefix/p/"));
            assert!(proxy_request.contains("%E9"));
            assert!(!proxy_request.contains("sign="));
            proxy
                .write_all(
                    b"HTTP/1.1 206 Partial Content\r\nContent-Range: bytes 0-2/3\r\nContent-Length: 3\r\nConnection: close\r\n\r\nabc",
                )
                .expect("proxy response");
        });
        let backend = OpenListBackend::new(BuildOpenListArg {
            base_url: format!("{address}/prefix"),
            token: "api-token".into(),
            timeout: Duration::from_secs(2),
        })
        .expect("backend");
        let response = backend
            .get_range_response("/音 乐.mp3".into(), ByteRange::new(0, 2).unwrap())
            .await
            .expect("range fallback");
        server.join().expect("server");
        assert_eq!(response.bytes.as_ref(), b"abc");
    }

    #[tokio::test]
    async fn direct_success_without_range_falls_back_with_signed_same_server_path() {
        let listener = TcpListener::bind("127.0.0.1:0").expect("listener");
        let address = format!("http://{}", listener.local_addr().expect("address"));
        let server = thread::spawn(move || {
            let (mut api, _) = listener.accept().expect("api accept");
            let _ = read_request(&mut api);
            let port = api.local_addr().expect("local").port();
            let body = format!(
                r#"{{"code":200,"data":{{"raw_url":"http://127.0.0.1:{port}/raw","size":3,"sign":"opaque-sign"}}}}"#
            );
            api.write_all(
                format!(
                    "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}",
                    body.len()
                )
                .as_bytes(),
            )
            .expect("get response");
            drop(api);

            let (mut raw, _) = listener.accept().expect("raw accept");
            let _ = read_request(&mut raw);
            raw.write_all(b"HTTP/1.1 200 OK\r\nContent-Length: 3\r\nConnection: close\r\n\r\nabc")
                .expect("raw response");
            drop(raw);

            let (mut proxy, _) = listener.accept().expect("proxy accept");
            let proxy_request = read_request(&mut proxy);
            assert!(proxy_request.starts_with("GET /prefix/p/"));
            assert!(proxy_request.contains("sign=opaque-sign"));
            proxy
                .write_all(
                    b"HTTP/1.1 206 Partial Content\r\nContent-Range: bytes 0-2/3\r\nContent-Length: 3\r\nConnection: close\r\n\r\nabc",
                )
                .expect("proxy response");
        });
        let backend = OpenListBackend::new(BuildOpenListArg {
            base_url: format!("{address}/prefix"),
            token: "api-token".into(),
            timeout: Duration::from_secs(2),
        })
        .expect("backend");
        let response = backend
            .get_range_response("/signed.mp3".into(), ByteRange::new(0, 2).unwrap())
            .await
            .expect("range fallback");
        server.join().expect("server");
        assert_eq!(response.bytes.as_ref(), b"abc");
    }

    #[tokio::test]
    async fn repeated_full_page_is_a_bounded_protocol_failure() {
        let listener = TcpListener::bind("127.0.0.1:0").expect("listener");
        let address = format!("http://{}", listener.local_addr().expect("address"));
        let content = (0..500)
            .map(|index| {
                serde_json::json!({
                    "name": format!("track-{index}.mp3"),
                    "size": 1,
                    "is_dir": false,
                    "type": 3,
                })
            })
            .collect::<Vec<_>>();
        let body = serde_json::json!({
            "code": 200,
            "data": {"total": 1_000, "content": content}
        })
        .to_string();
        let server = thread::spawn(move || {
            for _ in 0..2 {
                let (mut stream, _) = listener.accept().expect("accept");
                let request = read_request(&mut stream);
                assert!(request.starts_with("POST /api/fs/list HTTP/1.1"));
                stream
                    .write_all(
                        format!(
                            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}",
                            body.len()
                        )
                        .as_bytes(),
                    )
                    .expect("response");
            }
        });
        let backend = OpenListBackend::new(BuildOpenListArg {
            base_url: address,
            token: String::new(),
            timeout: Duration::from_secs(2),
        })
        .expect("backend");
        assert!(backend.list("/".into()).await.is_err());
        server.join().expect("server");
    }

    #[tokio::test]
    async fn malformed_http_and_envelope_statuses_fail_without_body_details() {
        for (status, body) in [
            ("401 Unauthorized", r#"{"code":200,"data":{}}"#),
            (
                "200 OK",
                r#"{"code":403,"message":"secret body","data":{}}"#,
            ),
            (
                "200 OK",
                r#"{"code":429,"message":"secret body","data":{}}"#,
            ),
        ] {
            let listener = TcpListener::bind("127.0.0.1:0").expect("listener");
            let address = format!("http://{}", listener.local_addr().expect("address"));
            let response_body = body.to_string();
            let server = thread::spawn(move || {
                let (mut stream, _) = listener.accept().expect("accept");
                let _ = read_request(&mut stream);
                stream
                    .write_all(
                        format!(
                            "HTTP/1.1 {status}\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{response_body}",
                            response_body.len()
                        )
                        .as_bytes(),
                    )
                    .expect("response");
            });
            let backend = OpenListBackend::new(BuildOpenListArg {
                base_url: address,
                token: "fixed-token".into(),
                timeout: Duration::from_secs(2),
            })
            .expect("backend");
            let error = backend.list("/".into()).await.expect_err("failure");
            let rendered = error.to_string();
            assert!(!rendered.contains("fixed-token"));
            assert!(!rendered.contains("secret body"));
            server.join().expect("server");
        }
    }

    #[tokio::test]
    async fn malformed_content_ranges_and_proxy_statuses_are_rejected() {
        let responses = [
            (
                "missing-header",
                "HTTP/1.1 206 Partial Content\r\nContent-Length: 3\r\nConnection: close\r\n\r\nabc",
                false,
            ),
            (
                "wrong-end",
                "HTTP/1.1 206 Partial Content\r\nContent-Range: bytes 0-1/3\r\nContent-Length: 2\r\nConnection: close\r\n\r\nab",
                false,
            ),
            (
                "wrong-total",
                "HTTP/1.1 206 Partial Content\r\nContent-Range: bytes 0-2/4\r\nContent-Length: 3\r\nConnection: close\r\n\r\nabc",
                false,
            ),
            (
                "short-body",
                "HTTP/1.1 206 Partial Content\r\nContent-Range: bytes 0-2/3\r\nContent-Length: 3\r\nConnection: close\r\n\r\nab",
                false,
            ),
            (
                "proxy-200",
                "HTTP/1.1 200 OK\r\nContent-Length: 3\r\nConnection: close\r\n\r\nabc",
                true,
            ),
        ];
        for (name, raw_response, expects_proxy) in responses {
            let listener = TcpListener::bind("127.0.0.1:0").expect("listener");
            let address = format!("http://{}", listener.local_addr().expect("address"));
            let server = thread::spawn(move || {
                let (mut api, _) = listener.accept().expect("api accept");
                let _ = read_request(&mut api);
                let port = api.local_addr().expect("local").port();
                let body = format!(
                    r#"{{"code":200,"data":{{"raw_url":"http://127.0.0.1:{port}/raw","size":3,"sign":""}}}}"#
                );
                api.write_all(
                    format!(
                        "HTTP/1.1 200 OK\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}",
                        body.len()
                    )
                    .as_bytes(),
                )
                .expect("get response");
                drop(api);
                let (mut raw, _) = listener.accept().expect("raw accept");
                let _ = read_request(&mut raw);
                raw.write_all(raw_response.as_bytes())
                    .expect("raw response");
                drop(raw);
                if expects_proxy {
                    let (mut proxy, _) = listener.accept().expect("proxy accept");
                    let _ = read_request(&mut proxy);
                    proxy
                        .write_all(raw_response.as_bytes())
                        .expect("proxy response");
                }
            });
            let backend = OpenListBackend::new(BuildOpenListArg {
                base_url: address,
                token: "api-token".into(),
                timeout: Duration::from_secs(2),
            })
            .expect("backend");
            assert!(
                backend
                    .get_range_response("/bad-range.mp3".into(), ByteRange::new(0, 2).unwrap())
                    .await
                    .is_err(),
                "fixture {name} should fail"
            );
            server.join().expect("server");
        }
    }

    #[test]
    fn hash_info_fallback_is_stable_and_empty_hash_is_ignored() {
        let first = serde_json::json!({"b": 2, "a": 1});
        let second = serde_json::json!({"a": 1, "b": 2});
        assert_eq!(
            stable_hash_signature(&first),
            stable_hash_signature(&second)
        );
        assert_eq!(stable_hash_signature(&serde_json::json!({})), None);
        assert_eq!(stable_hash_signature(&serde_json::json!([])), None);
    }

    #[tokio::test]
    async fn list_uses_stable_hash_info_fallback_and_prefers_hashinfo() {
        let listener = TcpListener::bind("127.0.0.1:0").expect("listener");
        let address = format!("http://{}", listener.local_addr().expect("address"));
        let body = serde_json::json!({
            "code": 200,
            "data": {"total": 4, "content": [
                {"name":"a.flac","size":1,"is_dir":false,"type":3,"hash_info":{"b":2,"a":1}},
                {"name":"b.flac","size":1,"is_dir":false,"type":3,"hash_info":{"a":1,"b":2}},
                {"name":"c.flac","size":1,"is_dir":false,"type":3,"hashinfo":"preferred","hash_info":{"a":9}},
                {"name":"d.flac","size":1,"is_dir":false,"type":3,"hash_info":{}}
            ]}
        }).to_string();
        let server = thread::spawn(move || {
            let (mut stream, _) = listener.accept().expect("accept");
            let _ = read_request(&mut stream);
            stream
                .write_all(
                    format!(
                        "HTTP/1.1 200 OK\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}",
                        body.len()
                    )
                    .as_bytes(),
                )
                .expect("response");
        });
        let backend = OpenListBackend::new(BuildOpenListArg {
            base_url: address,
            token: String::new(),
            timeout: Duration::from_secs(2),
        })
        .expect("backend");
        let entries = backend.list("/".into()).await.expect("list");
        server.join().expect("server");
        assert_eq!(entries[0].etag, entries[1].etag);
        assert_eq!(entries[0].etag.as_deref(), Some("{a=1;b=2;}"));
        assert_eq!(entries[2].etag.as_deref(), Some("preferred"));
        assert_eq!(entries[3].etag, None);
    }

    #[tokio::test]
    async fn list_rejects_missing_content_wrong_content_and_invalid_file_size() {
        for body in [
            r#"{"code":200,"data":{"total":1}}"#,
            r#"{"code":200,"data":{"total":1,"content":{}}}"#,
            r#"{"code":200,"data":{"total":1,"content":[{"name":"bad.mp3","is_dir":false,"type":3,"size":-1}]}}"#,
        ] {
            let listener = TcpListener::bind("127.0.0.1:0").expect("listener");
            let address = format!("http://{}", listener.local_addr().expect("address"));
            let response_body = body.to_string();
            let server = thread::spawn(move || {
                let (mut stream, _) = listener.accept().expect("accept");
                let _ = read_request(&mut stream);
                stream
                    .write_all(
                        format!(
                            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{response_body}",
                            response_body.len()
                        )
                        .as_bytes(),
                    )
                    .expect("response");
            });
            let backend = OpenListBackend::new(BuildOpenListArg {
                base_url: address,
                token: String::new(),
                timeout: Duration::from_secs(2),
            })
            .expect("backend");
            assert!(backend.list("/".into()).await.is_err());
            server.join().expect("server");
        }
    }
}
