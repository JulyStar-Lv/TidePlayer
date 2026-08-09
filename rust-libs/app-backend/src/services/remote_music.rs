use std::collections::HashMap;

use md5::{Digest, Md5};
use rand::{distributions::Alphanumeric, Rng};
use reqwest::blocking::Client;
use serde_json::{json, Value};

use crate::error::{BError, BResult};

const CLIENT_NAME: &str = "TidePlayer";
const SUBSONIC_VERSION: &str = "1.16.1";

#[uniffi::export]
pub fn ct_subsonic_request(
    base_url: String,
    username: String,
    password: String,
    endpoint: String,
    params: HashMap<String, String>,
) -> BResult<String> {
    let url = subsonic_url(&base_url, &username, &password, &endpoint, &params)?;
    get_text(url, HashMap::new())
}

#[uniffi::export]
pub fn ct_subsonic_resource_url(
    base_url: String,
    username: String,
    password: String,
    endpoint: String,
    params: HashMap<String, String>,
) -> BResult<String> {
    Ok(subsonic_url(&base_url, &username, &password, &endpoint, &params)?.to_string())
}

#[uniffi::export]
pub fn ct_emby_login(base_url: String, username: String, password: String) -> BResult<String> {
    let mut url = reqwest::Url::parse(base_url.trim_end_matches('/')).map_err(|error| {
        BError::CustomError {
            message: error.to_string(),
        }
    })?;
    url.set_path(&format!(
        "{}/Users/AuthenticateByName",
        url.path().trim_end_matches('/')
    ));
    let body = json!({ "Username": username, "Pw": password }).to_string();
    let response = http_client()?
        .post(url)
        .header("X-Emby-Authorization", emby_authorization_header())
        .header(reqwest::header::CONTENT_TYPE, "application/json")
        .body(body)
        .send()
        .map_err(anyhow::Error::from)?;
    let status = response.status();
    let text = response.text().map_err(anyhow::Error::from)?;
    if !status.is_success() {
        return Err(BError::CustomError {
            message: format!("Emby login failed with HTTP {}", status.as_u16()),
        });
    }
    Ok(text)
}

#[uniffi::export]
pub fn ct_emby_request(
    base_url: String,
    token: String,
    path: String,
    params: HashMap<String, String>,
) -> BResult<String> {
    let mut url = reqwest::Url::parse(base_url.trim_end_matches('/')).map_err(|error| {
        BError::CustomError {
            message: error.to_string(),
        }
    })?;
    url.set_path(&format!(
        "{}/{}",
        url.path().trim_end_matches('/'),
        path.trim_start_matches('/')
    ));
    url.query_pairs_mut().extend_pairs(params);
    let mut headers = HashMap::new();
    headers.insert("X-Emby-Token".to_string(), token);
    headers.insert(
        "X-Emby-Authorization".to_string(),
        emby_authorization_header(),
    );
    get_text(url, headers)
}

#[uniffi::export]
pub fn ct_emby_resource_url(
    base_url: String,
    token: String,
    path: String,
    params: HashMap<String, String>,
) -> BResult<String> {
    let mut url = reqwest::Url::parse(base_url.trim_end_matches('/')).map_err(|error| {
        BError::CustomError {
            message: error.to_string(),
        }
    })?;
    url.set_path(&format!(
        "{}/{}",
        url.path().trim_end_matches('/'),
        path.trim_start_matches('/')
    ));
    {
        let mut query = url.query_pairs_mut();
        query.append_pair("api_key", &token);
        query.extend_pairs(params);
    }
    Ok(url.to_string())
}

fn subsonic_url(
    base_url: &str,
    username: &str,
    password: &str,
    endpoint: &str,
    params: &HashMap<String, String>,
) -> BResult<reqwest::Url> {
    let mut url = reqwest::Url::parse(base_url.trim_end_matches('/')).map_err(|error| {
        BError::CustomError {
            message: error.to_string(),
        }
    })?;
    url.set_path(&format!(
        "{}/rest/{}.view",
        url.path().trim_end_matches('/'),
        endpoint.trim_matches('/')
    ));
    let salt: String = rand::thread_rng()
        .sample_iter(&Alphanumeric)
        .take(12)
        .map(char::from)
        .collect();
    let token = format!("{:x}", Md5::digest(format!("{password}{salt}")));
    {
        let mut query = url.query_pairs_mut();
        query
            .append_pair("u", username)
            .append_pair("s", &salt)
            .append_pair("t", &token)
            .append_pair("v", SUBSONIC_VERSION)
            .append_pair("c", CLIENT_NAME)
            .append_pair("f", "json");
        query.extend_pairs(params);
    }
    Ok(url)
}

fn get_text(url: reqwest::Url, headers: HashMap<String, String>) -> BResult<String> {
    let mut request = http_client()?.get(url);
    for (name, value) in headers {
        request = request.header(name, value);
    }
    let response = request.send().map_err(anyhow::Error::from)?;
    let status = response.status();
    let text = response.text().map_err(anyhow::Error::from)?;
    if !status.is_success() {
        return Err(BError::CustomError {
            message: format!("remote music request failed with HTTP {}", status.as_u16()),
        });
    }
    validate_remote_response(&text)?;
    Ok(text)
}

fn validate_remote_response(text: &str) -> BResult<()> {
    let Ok(root) = serde_json::from_str::<Value>(text) else {
        return Ok(());
    };
    let Some(response) = root.get("subsonic-response") else {
        return Ok(());
    };
    if response.get("status").and_then(Value::as_str) == Some("failed") {
        let message = response
            .get("error")
            .and_then(|error| error.get("message"))
            .and_then(Value::as_str)
            .unwrap_or("Subsonic request failed")
            .to_string();
        return Err(BError::CustomError { message });
    }
    Ok(())
}

fn http_client() -> BResult<Client> {
    Client::builder()
        .connect_timeout(std::time::Duration::from_secs(15))
        .timeout(std::time::Duration::from_secs(45))
        .build()
        .map_err(anyhow::Error::from)
        .map_err(BError::from)
}

fn emby_authorization_header() -> String {
    "MediaBrowser Client=\"TidePlayer\", Device=\"TidePlayer\", DeviceId=\"musicapp\", Version=\"1.0\""
        .to_string()
}
