mod crypto;
mod xml;

use crate::{HostApiDispatcher, OperationControl, PluginRuntimeError};
use base64::{engine::general_purpose, Engine as _};
use flate2::read::ZlibDecoder;
use reqwest::{
    blocking::Client,
    header::{HeaderMap, HeaderName, HeaderValue, CONTENT_TYPE, LOCATION, USER_AGENT},
    Method,
};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::{
    collections::BTreeMap,
    fs,
    io::Read,
    net::{IpAddr, SocketAddr, ToSocketAddrs},
    path::{Path, PathBuf},
    time::{Duration, SystemTime, UNIX_EPOCH},
};
use url::{Host, Url};

pub const SUPPORTED_HOST_APIS: &[&str] = &[
    "app.info",
    "app.userAgent",
    "runtime.info",
    "cache.get",
    "cache.set",
    "cache.remove",
    "cache.clear",
    "crypto.md5",
    "crypto.aesEcbPkcs5EncryptBase64",
    "crypto.aesEcbPkcs5EncryptHex",
    "crypto.aesEcbPkcs5DecryptBase64ToText",
    "base64.encodeText",
    "base64.decodeText",
    "base64.dropBytes",
    "base64.decodeBytes",
    "base64.encodeBytes",
    "base64.encodeUrlText",
    "base64.decodeUrlText",
    "base64.encodeUrlBytes",
    "base64.decodeUrlBytes",
    "base64.toUrl",
    "base64.fromUrl",
    "bytes.xor",
    "bytes.xorBase64",
    "compression.inflateBytesToText",
    "compression.inflateBase64ToText",
    "http.getText",
    "http.postText",
    "http.postBytes",
    "http.get",
    "http.post",
    "http.getBytes",
    "http.postBytesResponse",
    "xml.getRootAttributes",
    "xml.findElements",
    "xml.replaceChildrenByAttr",
    "xml.removeElements",
    "log.debug",
    "log.warn",
    "log.error",
];

pub const PLUGIN_PROTOCOL_VERSION: u8 = 4;
pub const HOST_API_VERSION: u8 = 3;

#[derive(Clone)]
pub struct HostApiOptions {
    pub plugin_id: String,
    pub plugin_name: String,
    pub app_name: String,
    pub package_name: String,
    pub app_version_name: String,
    pub app_version_code: u64,
    pub cache_directory: PathBuf,
    pub allow_http: bool,
    pub allow_https: bool,
    pub allow_private_network: bool,
    pub connect_timeout_ms: u64,
    pub read_timeout_ms: u64,
    pub max_http_request_bytes: usize,
    pub max_http_response_bytes: usize,
    pub max_redirects: usize,
    pub max_cache_bytes: usize,
    pub max_inflate_bytes: usize,
}

impl Default for HostApiOptions {
    fn default() -> Self {
        Self {
            plugin_id: "plugin".into(),
            plugin_name: "Plugin".into(),
            app_name: "Tide Player".into(),
            package_name: "io.github.julystar.musicapp".into(),
            app_version_name: "0.0.0".into(),
            app_version_code: 0,
            cache_directory: std::env::temp_dir().join("musicapp-plugin-cache"),
            allow_http: false,
            allow_https: true,
            allow_private_network: false,
            connect_timeout_ms: 10_000,
            read_timeout_ms: 20_000,
            max_http_request_bytes: 4 * 1024 * 1024,
            max_http_response_bytes: 16 * 1024 * 1024,
            max_redirects: 5,
            max_cache_bytes: 4 * 1024 * 1024,
            max_inflate_bytes: 16 * 1024 * 1024,
        }
    }
}

pub struct HostApi {
    options: HostApiOptions,
    cache_root: PathBuf,
    cache_index: CacheIndex,
}

impl HostApi {
    pub fn new(options: HostApiOptions) -> Self {
        let hash = format!("{:x}", Sha256::digest(options.plugin_id.as_bytes()));
        let cache_root = options.cache_directory.join("plugins").join(hash);
        let _ = fs::create_dir_all(&cache_root);
        let cache_index = CacheIndex::read(&cache_root.join("index.json"));
        Self {
            options,
            cache_root,
            cache_index,
        }
    }

    fn execute(
        &mut self,
        name: &str,
        payload: serde_json::Value,
        control: &OperationControl,
    ) -> Result<serde_json::Value, PluginRuntimeError> {
        match name {
            "app.info" => Ok(serde_json::json!({
                "name": self.options.app_name,
                "packageName": self.options.package_name,
                "versionName": self.options.app_version_name,
                "versionCode": self.options.app_version_code,
                "buildType": "release",
                "debug": false,
                "commit": serde_json::Value::Null,
            })),
            "app.userAgent" => Ok(serde_json::Value::String(self.user_agent())),
            "runtime.info" => Ok(serde_json::json!({
                "name": "QuickJS",
                "version": "2025-09-13",
                "pluginApiVersion": PLUGIN_PROTOCOL_VERSION,
                "hostApiVersion": HOST_API_VERSION,
                "engine": "quickjs",
                "engineVersion": "2025-09-13",
                "supportedHostApis": SUPPORTED_HOST_APIS,
                "os": std::env::consts::OS,
                "arch": std::env::consts::ARCH,
            })),
            "cache.get" => self.cache_get(required_string(&payload, "key")?),
            "cache.set" => self.cache_set(
                required_string(&payload, "key")?,
                required_string(&payload, "value")?,
                payload.get("ttlMs").and_then(|v| v.as_u64()).unwrap_or(0),
            ),
            "cache.remove" => self.cache_remove(required_string(&payload, "key")?),
            "cache.clear" => self.cache_clear(),
            "crypto.md5" => Ok(serde_json::Value::String(crypto::md5_hex(
                required_string(&payload, "text")?.as_bytes(),
            ))),
            "crypto.aesEcbPkcs5EncryptBase64" => {
                Ok(serde_json::Value::String(crypto::aes_ecb_encrypt_base64(
                    required_string(&payload, "text")?,
                    required_string(&payload, "key")?,
                )?))
            }
            "crypto.aesEcbPkcs5EncryptHex" => {
                Ok(serde_json::Value::String(crypto::aes_ecb_encrypt_hex(
                    required_string(&payload, "text")?,
                    required_string(&payload, "key")?,
                )?))
            }
            "crypto.aesEcbPkcs5DecryptBase64ToText" => {
                Ok(serde_json::Value::String(crypto::aes_ecb_decrypt_base64(
                    required_string(&payload, "base64")?,
                    required_string(&payload, "key")?,
                )?))
            }
            "base64.encodeText" => Ok(serde_json::Value::String(
                general_purpose::STANDARD.encode(required_string(&payload, "text")?),
            )),
            "base64.decodeText" => Ok(serde_json::Value::String(
                String::from_utf8(decode_standard(required_string(&payload, "base64")?)?)
                    .map_err(host_error)?,
            )),
            "base64.dropBytes" => {
                let bytes = decode_standard(required_string(&payload, "base64")?)?;
                let count = payload.get("count").and_then(|v| v.as_u64()).unwrap_or(0) as usize;
                Ok(serde_json::Value::String(
                    general_purpose::STANDARD.encode(bytes.get(count..).unwrap_or_default()),
                ))
            }
            "base64.decodeBytes" => Ok(serde_json::to_value(decode_standard(required_string(
                &payload, "base64",
            )?)?)
            .map_err(host_error)?),
            "base64.encodeBytes" => Ok(serde_json::Value::String(
                general_purpose::STANDARD.encode(required_bytes(&payload, "bytes")?),
            )),
            "base64.encodeUrlText" => Ok(serde_json::Value::String(
                general_purpose::URL_SAFE_NO_PAD.encode(required_string(&payload, "text")?),
            )),
            "base64.decodeUrlText" => Ok(serde_json::Value::String(
                String::from_utf8(decode_url(required_string(&payload, "base64Url")?)?)
                    .map_err(host_error)?,
            )),
            "base64.encodeUrlBytes" => Ok(serde_json::Value::String(
                general_purpose::URL_SAFE_NO_PAD.encode(required_bytes(&payload, "bytes")?),
            )),
            "base64.decodeUrlBytes" => Ok(serde_json::to_value(decode_url(required_string(
                &payload,
                "base64Url",
            )?)?)
            .map_err(host_error)?),
            "base64.toUrl" => Ok(serde_json::Value::String(to_url_safe(required_string(
                &payload, "base64",
            )?))),
            "base64.fromUrl" => Ok(serde_json::Value::String(to_standard_url(required_string(
                &payload,
                "base64Url",
            )?))),
            "bytes.xor" => Ok(serde_json::to_value(xor_bytes(
                &required_bytes(&payload, "bytes")?,
                &required_bytes(&payload, "key")?,
            )?)
            .map_err(host_error)?),
            "bytes.xorBase64" => {
                let bytes = decode_standard(required_string(&payload, "base64")?)?;
                let key = required_bytes(&payload, "key")?;
                Ok(serde_json::Value::String(
                    general_purpose::STANDARD.encode(xor_bytes(&bytes, &key)?),
                ))
            }
            "compression.inflateBytesToText" => self.inflate(&required_bytes(&payload, "bytes")?),
            "compression.inflateBase64ToText" => {
                self.inflate(&decode_standard(required_string(&payload, "base64")?)?)
            }
            "http.getText" => self.http(Method::GET, false, false, payload, control),
            "http.postText" => self.http(Method::POST, false, false, payload, control),
            "http.postBytes" => self.http(Method::POST, false, false, payload, control),
            "http.get" => self.http(Method::GET, false, true, payload, control),
            "http.post" => self.http(Method::POST, false, true, payload, control),
            "http.getBytes" => self.http(Method::GET, true, true, payload, control),
            "http.postBytesResponse" => self.http(Method::POST, true, true, payload, control),
            "xml.getRootAttributes" => xml::root_attributes(required_string(&payload, "xml")?),
            "xml.findElements" => xml::find_elements(
                required_string(&payload, "xml")?,
                payload.get("query").unwrap_or(&serde_json::Value::Null),
            ),
            "xml.replaceChildrenByAttr" => xml::replace_children_by_attr(
                required_string(&payload, "xml")?,
                payload.get("options").unwrap_or(&serde_json::Value::Null),
            )
            .map(serde_json::Value::String),
            "xml.removeElements" => xml::remove_elements(
                required_string(&payload, "xml")?,
                payload.get("query").unwrap_or(&serde_json::Value::Null),
            )
            .map(serde_json::Value::String),
            "log.debug" | "log.warn" | "log.error" => {
                let tag = payload
                    .get("tag")
                    .and_then(|v| v.as_str())
                    .unwrap_or("PlatformPlugin");
                let message = payload
                    .get("message")
                    .and_then(|v| v.as_str())
                    .unwrap_or("");
                eprintln!("[{name}] [{}:{tag}] {message}", self.options.plugin_id);
                Ok(serde_json::Value::Null)
            }
            _ => Err(PluginRuntimeError::HostApi(format!(
                "unsupported API: {name}"
            ))),
        }
    }

    fn user_agent(&self) -> String {
        format!(
            "{}/{} ({}; {}) plugin/{}/{}",
            self.options.app_name,
            self.options.app_version_name,
            std::env::consts::OS,
            std::env::consts::ARCH,
            self.options.plugin_id,
            self.options.plugin_name,
        )
    }

    fn http(
        &self,
        method: Method,
        bytes_response: bool,
        structured: bool,
        payload: serde_json::Value,
        control: &OperationControl,
    ) -> Result<serde_json::Value, PluginRuntimeError> {
        let retry_transport_failure = method == Method::GET;
        self.http_attempt(
            method,
            bytes_response,
            structured,
            &payload,
            control,
            retry_transport_failure,
        )
    }

    fn http_attempt(
        &self,
        method: Method,
        bytes_response: bool,
        structured: bool,
        payload: &serde_json::Value,
        control: &OperationControl,
        retry_transport_failure: bool,
    ) -> Result<serde_json::Value, PluginRuntimeError> {
        let mut url = Url::parse(required_string(payload, "url")?).map_err(host_error)?;
        let connect_timeout = payload
            .get("connectTimeoutMs")
            .and_then(|v| v.as_u64())
            .unwrap_or(self.options.connect_timeout_ms)
            .clamp(1, 120_000);
        let read_timeout = payload
            .get("readTimeoutMs")
            .and_then(|v| v.as_u64())
            .unwrap_or(self.options.read_timeout_ms)
            .clamp(1, 300_000);
        let follow_redirects = payload
            .get("followRedirects")
            .and_then(|v| v.as_bool())
            .unwrap_or(true);
        let headers = self.parse_headers(payload.get("headers"))?;
        let body = request_body(payload, self.options.max_http_request_bytes)?;

        for redirect_count in 0..=self.options.max_redirects {
            check_control(control)?;
            let pinned_addresses = self.validate_url(&url)?;
            let host = url
                .host_str()
                .ok_or_else(|| PluginRuntimeError::HostApi("URL has no host".into()))?;
            let mut client_builder = Client::builder()
                .redirect(reqwest::redirect::Policy::none())
                .no_proxy()
                .connect_timeout(Duration::from_millis(connect_timeout))
                .timeout(Duration::from_millis(read_timeout));
            if let Some(addresses) = pinned_addresses.as_deref() {
                client_builder = client_builder.resolve_to_addrs(host, addresses);
            }
            let client = client_builder.build().map_err(host_error)?;
            let mut builder = client.request(method.clone(), url.clone());
            for (name, value) in &headers {
                builder = builder.header(name, value);
            }
            if !headers.contains_key(USER_AGENT) {
                builder = builder.header(USER_AGENT, self.user_agent());
            }
            if method != Method::GET {
                builder = builder.body(body.clone());
            }
            let mut response = match builder.send() {
                Ok(response) => response,
                Err(_) if retry_transport_failure => {
                    return self.http_attempt(
                        method,
                        bytes_response,
                        structured,
                        payload,
                        control,
                        false,
                    )
                }
                Err(error) => return Err(host_error(error)),
            };
            check_control(control)?;

            if response.status().is_redirection() && follow_redirects {
                if redirect_count == self.options.max_redirects {
                    return Err(PluginRuntimeError::HostApi("too many redirects".into()));
                }
                let location = response
                    .headers()
                    .get(LOCATION)
                    .and_then(|value| value.to_str().ok())
                    .ok_or_else(|| {
                        PluginRuntimeError::HostApi("redirect location missing".into())
                    })?;
                url = url.join(location).map_err(host_error)?;
                continue;
            }

            let status = response.status();
            let response_headers = response_headers(response.headers());
            let content_type = response
                .headers()
                .get(CONTENT_TYPE)
                .and_then(|value| value.to_str().ok())
                .map(str::to_owned);
            let mut data = Vec::new();
            let mut limited =
                (&mut response).take((self.options.max_http_response_bytes + 1) as u64);
            loop {
                check_control(control)?;
                let mut chunk = [0_u8; 8192];
                let read = match limited.read(&mut chunk) {
                    Ok(read) => read,
                    Err(_) if retry_transport_failure => {
                        return self.http_attempt(
                            method,
                            bytes_response,
                            structured,
                            payload,
                            control,
                            false,
                        )
                    }
                    Err(error) => return Err(host_error(error)),
                };
                if read == 0 {
                    break;
                }
                data.extend_from_slice(&chunk[..read]);
                if data.len() > self.options.max_http_response_bytes {
                    return Err(PluginRuntimeError::HostApi(
                        "HTTP response exceeded size limit".into(),
                    ));
                }
            }
            return if structured {
                Ok(serde_json::json!({
                    "code": status.as_u16(),
                    "status": status.as_u16(),
                    "message": status.canonical_reason().unwrap_or(""),
                    "url": url.as_str(),
                    "headers": response_headers,
                    "contentType": content_type,
                    "body": if bytes_response { String::new() } else { String::from_utf8(data.clone()).map_err(host_error)? },
                    "bodyBase64": if bytes_response { general_purpose::STANDARD.encode(data) } else { String::new() },
                }))
            } else if bytes_response {
                Ok(serde_json::Value::String(
                    general_purpose::STANDARD.encode(data),
                ))
            } else {
                let text = String::from_utf8(data).map_err(host_error)?;
                if !status.is_success() {
                    return Err(PluginRuntimeError::HostApi(format!(
                        "HTTP status {}",
                        status.as_u16()
                    )));
                }
                Ok(serde_json::Value::String(text))
            };
        }
        Err(PluginRuntimeError::HostApi("redirect loop".into()))
    }

    fn parse_headers(
        &self,
        value: Option<&serde_json::Value>,
    ) -> Result<HeaderMap, PluginRuntimeError> {
        let mut headers = HeaderMap::new();
        if let Some(object) = value.and_then(|value| value.as_object()) {
            for (name, value) in object {
                let name = HeaderName::from_bytes(name.as_bytes()).map_err(host_error)?;
                let value = value.as_str().ok_or_else(|| {
                    PluginRuntimeError::HostApi("header values must be strings".into())
                })?;
                headers.insert(name, HeaderValue::from_str(value).map_err(host_error)?);
            }
        }
        Ok(headers)
    }

    fn validate_url(&self, url: &Url) -> Result<Option<Vec<SocketAddr>>, PluginRuntimeError> {
        match url.scheme() {
            "http" if !self.options.allow_http => {
                return Err(PluginRuntimeError::HostApi("HTTP is disabled".into()))
            }
            "https" if !self.options.allow_https => {
                return Err(PluginRuntimeError::HostApi("HTTPS is disabled".into()))
            }
            "http" | "https" => {}
            _ => return Err(PluginRuntimeError::HostApi("unsupported URL scheme".into())),
        }
        let host = url
            .host_str()
            .ok_or_else(|| PluginRuntimeError::HostApi("URL has no host".into()))?;
        let literal_ip = match url.host() {
            Some(Host::Ipv4(ip)) => Some(IpAddr::V4(ip)),
            Some(Host::Ipv6(ip)) => Some(IpAddr::V6(ip)),
            Some(Host::Domain(_)) => None,
            None => return Err(PluginRuntimeError::HostApi("URL has no host".into())),
        };
        if let Some(ip) = literal_ip {
            if !self.options.allow_private_network && blocked_ip(ip) {
                return Err(PluginRuntimeError::HostApi(
                    "private network access is disabled".into(),
                ));
            }
            return Ok(None);
        }

        // HTTPS authenticates the hostname with TLS. Let the platform resolver and network
        // stack handle synthetic DNS addresses used by TUN/VPN implementations.
        if url.scheme() == "https" {
            return Ok(None);
        }

        let port = url
            .port_or_known_default()
            .ok_or_else(|| PluginRuntimeError::HostApi("URL has no port".into()))?;
        let addresses: Vec<SocketAddr> = (host, port)
            .to_socket_addrs()
            .map_err(host_error)?
            .collect();
        if addresses.is_empty() {
            return Err(PluginRuntimeError::HostApi(
                "host resolved to no addresses".into(),
            ));
        }
        if !self.options.allow_private_network
            && addresses.iter().any(|address| blocked_ip(address.ip()))
        {
            return Err(PluginRuntimeError::HostApi(
                "private network access is disabled".into(),
            ));
        }
        Ok(Some(addresses))
    }

    fn inflate(&self, bytes: &[u8]) -> Result<serde_json::Value, PluginRuntimeError> {
        let decoder = ZlibDecoder::new(bytes);
        let mut limited = decoder.take((self.options.max_inflate_bytes + 1) as u64);
        let mut output = Vec::new();
        limited.read_to_end(&mut output).map_err(host_error)?;
        if output.len() > self.options.max_inflate_bytes {
            return Err(PluginRuntimeError::HostApi(
                "inflated data exceeded size limit".into(),
            ));
        }
        Ok(serde_json::Value::String(
            String::from_utf8(output).map_err(host_error)?,
        ))
    }

    fn cache_get(&mut self, key: &str) -> Result<serde_json::Value, PluginRuntimeError> {
        self.validate_cache_key(key)?;
        self.cache_index.remove_expired(&self.cache_root);
        let Some(item) = self.cache_index.items.get(key).cloned() else {
            return Ok(serde_json::Value::Null);
        };
        let value = fs::read_to_string(self.cache_root.join(item.file)).map_err(host_error)?;
        Ok(serde_json::Value::String(value))
    }

    fn cache_set(
        &mut self,
        key: &str,
        value: &str,
        ttl_ms: u64,
    ) -> Result<serde_json::Value, PluginRuntimeError> {
        self.validate_cache_key(key)?;
        fs::create_dir_all(&self.cache_root).map_err(host_error)?;
        self.cache_index.remove_expired(&self.cache_root);
        let previous_size = self
            .cache_index
            .items
            .get(key)
            .map(|item| item.size)
            .unwrap_or(0);
        let current_size = self
            .cache_index
            .items
            .values()
            .map(|item| item.size)
            .sum::<usize>()
            .saturating_sub(previous_size);
        if current_size.saturating_add(value.len()) > self.options.max_cache_bytes {
            return Err(PluginRuntimeError::HostApi(
                "plugin cache limit exceeded".into(),
            ));
        }
        let file = format!("{:x}.cache", Sha256::digest(key.as_bytes()));
        fs::write(self.cache_root.join(&file), value).map_err(host_error)?;
        let expires_at = if ttl_ms == 0 {
            0
        } else {
            now_millis().saturating_add(ttl_ms)
        };
        self.cache_index.items.insert(
            key.into(),
            CacheItem {
                file,
                size: value.len(),
                expires_at,
            },
        );
        self.cache_index.write(&self.cache_root)?;
        Ok(serde_json::Value::Null)
    }

    fn cache_remove(&mut self, key: &str) -> Result<serde_json::Value, PluginRuntimeError> {
        self.validate_cache_key(key)?;
        if let Some(item) = self.cache_index.items.remove(key) {
            let _ = fs::remove_file(self.cache_root.join(item.file));
            self.cache_index.write(&self.cache_root)?;
        }
        Ok(serde_json::Value::Null)
    }

    fn cache_clear(&mut self) -> Result<serde_json::Value, PluginRuntimeError> {
        if self.cache_root.exists() {
            fs::remove_dir_all(&self.cache_root).map_err(host_error)?;
        }
        fs::create_dir_all(&self.cache_root).map_err(host_error)?;
        self.cache_index = CacheIndex::default();
        self.cache_index.write(&self.cache_root)?;
        Ok(serde_json::Value::Null)
    }

    fn validate_cache_key(&self, key: &str) -> Result<(), PluginRuntimeError> {
        if key.is_empty() || key.len() > 512 || key.contains('\0') {
            return Err(PluginRuntimeError::HostApi("invalid cache key".into()));
        }
        Ok(())
    }
}

impl HostApiDispatcher for HostApi {
    fn call(
        &mut self,
        name: &str,
        payload_json: &str,
        control: &OperationControl,
    ) -> Result<String, PluginRuntimeError> {
        check_control(control)?;
        let payload = serde_json::from_str(payload_json)
            .map_err(|error| PluginRuntimeError::HostApi(error.to_string()))?;
        let value = self.execute(name, payload, control)?;
        serde_json::to_string(&serde_json::json!({ "value": value }))
            .map_err(|error| PluginRuntimeError::HostApi(error.to_string()))
    }
}

fn request_body(
    payload: &serde_json::Value,
    max_size: usize,
) -> Result<Vec<u8>, PluginRuntimeError> {
    let bytes = if let Some(array) = payload.get("bodyBytes").and_then(|v| v.as_array()) {
        array
            .iter()
            .map(|value| {
                value
                    .as_u64()
                    .filter(|value| *value <= u8::MAX as u64)
                    .map(|value| value as u8)
                    .ok_or_else(|| PluginRuntimeError::HostApi("invalid body byte".into()))
            })
            .collect::<Result<Vec<_>, _>>()?
    } else if let Some(encoded) = payload.get("bodyBase64").and_then(|v| v.as_str()) {
        if encoded.is_empty() {
            payload
                .get("body")
                .and_then(|v| v.as_str())
                .unwrap_or("")
                .as_bytes()
                .to_vec()
        } else {
            decode_standard(encoded)?
        }
    } else {
        payload
            .get("body")
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .as_bytes()
            .to_vec()
    };
    if bytes.len() > max_size {
        return Err(PluginRuntimeError::HostApi(
            "HTTP request body exceeded size limit".into(),
        ));
    }
    Ok(bytes)
}

fn response_headers(headers: &HeaderMap) -> BTreeMap<String, Vec<String>> {
    headers
        .keys()
        .map(|name| {
            let values = headers
                .get_all(name)
                .iter()
                .filter_map(|value| value.to_str().ok().map(str::to_owned))
                .collect();
            (name.as_str().to_owned(), values)
        })
        .collect()
}

fn required_string<'a>(
    value: &'a serde_json::Value,
    key: &str,
) -> Result<&'a str, PluginRuntimeError> {
    value
        .get(key)
        .and_then(|value| value.as_str())
        .ok_or_else(|| PluginRuntimeError::HostApi(format!("missing string: {key}")))
}

fn required_bytes(value: &serde_json::Value, key: &str) -> Result<Vec<u8>, PluginRuntimeError> {
    value
        .get(key)
        .and_then(|value| value.as_array())
        .ok_or_else(|| PluginRuntimeError::HostApi(format!("missing byte array: {key}")))?
        .iter()
        .map(|value| {
            value
                .as_u64()
                .filter(|value| *value <= u8::MAX as u64)
                .map(|value| value as u8)
                .ok_or_else(|| PluginRuntimeError::HostApi("invalid byte value".into()))
        })
        .collect()
}

fn xor_bytes(bytes: &[u8], key: &[u8]) -> Result<Vec<u8>, PluginRuntimeError> {
    if key.is_empty() {
        return Err(PluginRuntimeError::HostApi(
            "XOR key must not be empty".into(),
        ));
    }
    Ok(bytes
        .iter()
        .enumerate()
        .map(|(index, value)| value ^ key[index % key.len()])
        .collect())
}

fn decode_standard(value: &str) -> Result<Vec<u8>, PluginRuntimeError> {
    general_purpose::STANDARD
        .decode(value)
        .or_else(|_| general_purpose::STANDARD_NO_PAD.decode(value))
        .map_err(host_error)
}

fn decode_url(value: &str) -> Result<Vec<u8>, PluginRuntimeError> {
    general_purpose::URL_SAFE_NO_PAD
        .decode(value)
        .or_else(|_| general_purpose::URL_SAFE.decode(value))
        .map_err(host_error)
}

fn to_url_safe(value: &str) -> String {
    value
        .trim_end_matches('=')
        .replace('+', "-")
        .replace('/', "_")
}

fn to_standard_url(value: &str) -> String {
    let mut value = value.replace('-', "+").replace('_', "/");
    while !value.len().is_multiple_of(4) {
        value.push('=');
    }
    value
}

fn check_control(control: &OperationControl) -> Result<(), PluginRuntimeError> {
    if control.should_interrupt() {
        Err(control.interrupted_error())
    } else {
        Ok(())
    }
}

fn blocked_ip(ip: IpAddr) -> bool {
    match ip {
        IpAddr::V4(ip) => {
            ip.is_private()
                || ip.is_loopback()
                || ip.is_link_local()
                || ip.is_broadcast()
                || ip.is_documentation()
                || ip.is_unspecified()
                || ip.octets()[0] == 0
                || ip.octets()[0] >= 224
                || (ip.octets()[0] == 100 && (64..=127).contains(&ip.octets()[1]))
                || (ip.octets()[0] == 198 && (18..=19).contains(&ip.octets()[1]))
        }
        IpAddr::V6(ip) => {
            ip.is_loopback()
                || ip.is_unspecified()
                || ip.is_unique_local()
                || ip.is_unicast_link_local()
                || ip.is_multicast()
        }
    }
}

fn host_error(error: impl std::fmt::Display) -> PluginRuntimeError {
    PluginRuntimeError::HostApi(error.to_string())
}

fn now_millis() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}

#[derive(Default, Serialize, Deserialize)]
struct CacheIndex {
    items: BTreeMap<String, CacheItem>,
}

#[derive(Clone, Serialize, Deserialize)]
struct CacheItem {
    file: String,
    size: usize,
    expires_at: u64,
}

impl CacheIndex {
    fn read(path: &Path) -> Self {
        fs::read_to_string(path)
            .ok()
            .and_then(|text| serde_json::from_str(&text).ok())
            .unwrap_or_default()
    }

    fn write(&self, root: &Path) -> Result<(), PluginRuntimeError> {
        fs::create_dir_all(root).map_err(host_error)?;
        let text = serde_json::to_string(self).map_err(host_error)?;
        fs::write(root.join("index.json"), text).map_err(host_error)
    }

    fn remove_expired(&mut self, root: &Path) {
        let now = now_millis();
        let expired: Vec<String> = self
            .items
            .iter()
            .filter(|(_, item)| item.expires_at != 0 && item.expires_at <= now)
            .map(|(key, _)| key.clone())
            .collect();
        for key in expired {
            if let Some(item) = self.items.remove(&key) {
                let _ = fs::remove_file(root.join(item.file));
            }
        }
        let _ = self.write(root);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::{Read, Write};
    use std::net::TcpListener;
    use std::thread;
    use std::time::Duration;
    use tempfile::tempdir;

    fn host() -> HostApi {
        let dir = tempdir().unwrap().keep();
        HostApi::new(HostApiOptions {
            cache_directory: dir,
            max_cache_bytes: 64,
            max_inflate_bytes: 64,
            ..Default::default()
        })
    }

    fn call(host: &mut HostApi, name: &str, payload: serde_json::Value) -> serde_json::Value {
        let control = OperationControl::default();
        host.execute(name, payload, &control).unwrap()
    }

    fn structured_http_response(status: &str, body: &str) -> serde_json::Value {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        let status = status.to_owned();
        let body = body.to_owned();
        let handle = thread::spawn(move || {
            let (mut stream, _) = listener.accept().unwrap();
            let mut request = [0_u8; 1024];
            let _ = stream.read(&mut request);
            write!(
                stream,
                "HTTP/1.1 {status}\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}",
                body.len(),
            )
            .unwrap();
        });
        let mut host = host();
        host.options.allow_http = true;
        host.options.allow_private_network = true;
        let result = call(
            &mut host,
            "http.get",
            serde_json::json!({"url":format!("http://{address}/response")}),
        );
        handle.join().unwrap();
        result
    }

    #[test]
    fn cache_isolated_and_bounded() {
        let mut first = host();
        let mut second = HostApi::new(HostApiOptions {
            plugin_id: "second".into(),
            cache_directory: first.options.cache_directory.clone(),
            max_cache_bytes: 64,
            ..Default::default()
        });
        call(
            &mut first,
            "cache.set",
            serde_json::json!({"key":"a","value":"value","ttlMs":0}),
        );
        assert_eq!(
            call(&mut first, "cache.get", serde_json::json!({"key":"a"})),
            "value"
        );
        assert_eq!(
            call(&mut second, "cache.get", serde_json::json!({"key":"a"})),
            serde_json::Value::Null
        );
        assert!(first
            .execute(
                "cache.set",
                serde_json::json!({"key":"big","value":"x".repeat(100),"ttlMs":0}),
                &OperationControl::default(),
            )
            .is_err());
    }

    #[test]
    fn base64_xor_and_compression() {
        let mut host = host();
        assert_eq!(
            call(
                &mut host,
                "base64.encodeText",
                serde_json::json!({"text":"hello"}),
            ),
            "aGVsbG8="
        );
        assert_eq!(
            call(
                &mut host,
                "bytes.xor",
                serde_json::json!({"bytes":[1,2,3],"key":[1]}),
            ),
            serde_json::json!([0, 3, 2])
        );
        let mut encoder =
            flate2::write::ZlibEncoder::new(Vec::new(), flate2::Compression::default());
        encoder.write_all(b"hello").unwrap();
        let bytes = encoder.finish().unwrap();
        assert_eq!(
            call(
                &mut host,
                "compression.inflateBytesToText",
                serde_json::json!({"bytes":bytes}),
            ),
            "hello"
        );
    }

    #[test]
    fn blocks_literal_private_ip_by_default_and_allows_it_when_enabled() {
        let mut host = host();
        host.options.allow_http = true;
        assert!(host
            .execute(
                "http.getText",
                serde_json::json!({"url":"http://127.0.0.1:1"}),
                &OperationControl::default(),
            )
            .is_err());

        host.options.allow_private_network = true;
        let addresses = host
            .validate_url(&Url::parse("http://127.0.0.1:80").unwrap())
            .unwrap();
        assert!(addresses.is_none());
    }

    #[test]
    fn https_hostnames_use_platform_resolution_but_private_ip_literals_remain_blocked() {
        let host = host();

        assert!(host
            .validate_url(&Url::parse("https://music.apple.com").unwrap())
            .unwrap()
            .is_none());
        assert!(host
            .validate_url(&Url::parse("https://192.168.1.10").unwrap())
            .is_err());
        assert!(host
            .validate_url(&Url::parse("https://[::1]").unwrap())
            .is_err());
    }

    #[test]
    fn redirect_targets_are_revalidated_and_pinned() {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        let handle = thread::spawn(move || {
            let (mut stream, _) = listener.accept().unwrap();
            let mut request = [0_u8; 1024];
            let _ = stream.read(&mut request);
            stream
                .write_all(
                    b"HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:9/private\r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
                )
                .unwrap();
        });

        let mut host = host();
        host.options.allow_http = true;
        host.options.allow_private_network = true;
        host.options.connect_timeout_ms = 1_000;
        host.options.read_timeout_ms = 1_000;
        let first_url = format!("http://{address}/redirect");
        let error = host
            .execute(
                "http.getText",
                serde_json::json!({"url":first_url}),
                &OperationControl::default(),
            )
            .unwrap_err();
        assert!(matches!(error, PluginRuntimeError::HostApi(_)));
        handle.join().unwrap();
    }

    #[test]
    fn get_retries_once_after_truncated_response_body() {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        let handle = thread::spawn(move || {
            for body in [b"cut".as_slice(), b"complete".as_slice()] {
                let (mut stream, _) = listener.accept().unwrap();
                let mut request = [0_u8; 1024];
                let _ = stream.read(&mut request);
                write!(
                    stream,
                    "HTTP/1.1 200 OK\r\nContent-Length: 8\r\nConnection: close\r\n\r\n",
                )
                .unwrap();
                stream.write_all(body).unwrap();
            }
        });

        let mut host = host();
        host.options.allow_http = true;
        host.options.allow_private_network = true;
        host.options.connect_timeout_ms = 1_000;
        host.options.read_timeout_ms = 1_000;
        let result = call(
            &mut host,
            "http.getText",
            serde_json::json!({"url":format!("http://{address}/retry")}),
        );

        assert_eq!(result, "complete");
        handle.join().unwrap();
    }

    #[test]
    fn request_body_priority_and_limit() {
        let payload = serde_json::json!({
            "body":"text",
            "bodyBase64":general_purpose::STANDARD.encode(b"base64"),
            "bodyBytes":[1,2,3],
        });
        assert_eq!(request_body(&payload, 10).unwrap(), vec![1, 2, 3]);
        assert!(request_body(&payload, 2).is_err());
    }

    #[test]
    fn control_interrupts_host_work() {
        let control = OperationControl::default();
        control.begin(1, 1);
        thread::sleep(Duration::from_millis(2));
        assert!(matches!(
            check_control(&control),
            Err(PluginRuntimeError::Timeout)
        ));
    }

    #[test]
    fn host_apis_match_manifest() {
        for name in SUPPORTED_HOST_APIS {
            assert!(!name.is_empty());
        }
        assert!(SUPPORTED_HOST_APIS.contains(&"app.info"));
        assert!(SUPPORTED_HOST_APIS.contains(&"http.postBytesResponse"));
        assert!(SUPPORTED_HOST_APIS.contains(&"xml.removeElements"));
    }

    #[test]
    fn runtime_info_reports_current_plugin_and_host_contract() {
        let info = call(&mut host(), "runtime.info", serde_json::json!({}));
        assert_eq!(info["pluginApiVersion"], PLUGIN_PROTOCOL_VERSION);
        assert_eq!(info["hostApiVersion"], HOST_API_VERSION);
        assert_eq!(info["engine"], "quickjs");
        assert!(info["engineVersion"]
            .as_str()
            .is_some_and(|value| !value.is_empty()));
        let supported = info["supportedHostApis"].as_array().unwrap();
        assert!(supported.iter().any(|value| value == "http.get"));
        assert!(info["os"].as_str().is_some_and(|value| !value.is_empty()));
        assert!(info["arch"].as_str().is_some_and(|value| !value.is_empty()));
    }

    #[test]
    fn xml_host_calls_follow_lyrico_value_contract() {
        let mut host = host();
        let xml = r#"<tt xml:lang="en"><translation key="a">Hello</translation><translation key="b">Bye</translation></tt>"#;

        let found = call(
            &mut host,
            "xml.findElements",
            serde_json::json!({
                "xml": xml,
                "query": {"tag": "translation", "attrs": {"key": "a"}},
            }),
        );
        assert_eq!(found[0]["text"], "Hello");

        let replaced = call(
            &mut host,
            "xml.replaceChildrenByAttr",
            serde_json::json!({
                "xml": xml,
                "options": {
                    "targetTag": "translation",
                    "keyAttr": "key",
                    "replacements": {"a": {"mode": "text", "value": "Hi & bye"}},
                },
            }),
        );
        let replaced = replaced.as_str().expect("rewritten XML string");
        assert!(replaced.contains("Hi &amp; bye"));

        let removed = call(
            &mut host,
            "xml.removeElements",
            serde_json::json!({
                "xml": replaced,
                "query": {"tag": "translation", "attrs": {"key": "b"}},
            }),
        );
        assert!(!removed
            .as_str()
            .expect("rewritten XML string")
            .contains("Bye"));
    }

    #[test]
    fn structured_http_returns_code_and_body_for_success_errors_and_empty_body() {
        for (status, expected_code, body) in [
            ("200 OK", 200, r#"{"ok":true}"#),
            ("403 Forbidden", 403, r#"{"error":"forbidden"}"#),
            ("429 Too Many Requests", 429, r#"{"error":"rate-limited"}"#),
            ("204 No Content", 204, ""),
        ] {
            let response = structured_http_response(status, body);
            assert_eq!(response["code"], expected_code);
            assert_eq!(response["status"], expected_code);
            assert_eq!(response["body"], body);
            assert_eq!(response["bodyBase64"], "");
            assert_eq!(response["headers"]["content-type"][0], "application/json");
        }
    }

    #[test]
    fn structured_http_follows_redirect_and_reports_final_response() {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        let handle = thread::spawn(move || {
            for index in 0..2 {
                let (mut stream, _) = listener.accept().unwrap();
                let mut request = [0_u8; 1024];
                let _ = stream.read(&mut request);
                if index == 0 {
                    write!(
                        stream,
                        "HTTP/1.1 302 Found\r\nLocation: http://{address}/final\r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
                    )
                    .unwrap();
                } else {
                    stream
                        .write_all(
                            b"HTTP/1.1 200 OK\r\nContent-Length: 5\r\nConnection: close\r\n\r\nfinal",
                        )
                        .unwrap();
                }
            }
        });
        let mut host = host();
        host.options.allow_http = true;
        host.options.allow_private_network = true;
        let response = call(
            &mut host,
            "http.get",
            serde_json::json!({"url":format!("http://{address}/redirect")}),
        );
        assert_eq!(response["code"], 200);
        assert_eq!(response["body"], "final");
        assert!(response["url"].as_str().unwrap().ends_with("/final"));
        handle.join().unwrap();
    }
}
