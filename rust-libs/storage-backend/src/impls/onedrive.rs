use std::{cmp::Ordering, time::Duration};

use async_runtime::tokio_runtime;
use futures_util::future::BoxFuture;
use reqwest::header::HeaderValue;
use reqwest::StatusCode;

use crate::{
    backend::{parse_remote_timestamp, read_range_response},
    env::MUSICAPP_ONEDRIVE_ID,
    ByteRange, DeltaItem, DeltaPage, Entry, RangeResponse, StorageBackend, StorageBackendError,
    StorageBackendResult, StreamFile,
};

pub struct BuildOneDriveArg {
    pub code: String,
    pub drive_id: Option<String>,
}

struct Auth {
    access_token: Option<String>,
    refresh_token: String,
}

pub struct OneDriveBackend {
    drive_id: Option<String>,
    auth: tokio::sync::RwLock<Auth>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct OneDriveDrive {
    pub id: String,
    pub name: String,
    pub drive_type: Option<String>,
    pub owner_name: Option<String>,
}

mod onedrive_types {
    use serde::Deserialize;
    use serde_with::{serde_as, DefaultOnError};

    #[derive(Deserialize, Debug)]
    pub struct RedeemCodeResp {
        pub access_token: String,
        pub refresh_token: Option<String>,
    }

    #[serde_as]
    #[derive(Debug, Deserialize)]
    pub struct ListItemResponse {
        #[serde_as(deserialize_as = "Vec<DefaultOnError>")]
        pub value: Vec<Option<ListItem>>,
        #[serde(rename = "@odata.nextLink")]
        pub next_link: Option<String>,
    }

    #[serde_as]
    #[derive(Debug, Deserialize)]
    pub struct DeltaResponse {
        #[serde_as(deserialize_as = "Vec<DefaultOnError>")]
        pub value: Vec<Option<DeltaItem>>,
        #[serde(rename = "@odata.nextLink")]
        pub next_link: Option<String>,
        #[serde(rename = "@odata.deltaLink")]
        pub delta_link: Option<String>,
    }

    #[serde_as]
    #[derive(Debug, Deserialize)]
    pub struct DriveListResponse {
        #[serde_as(deserialize_as = "Vec<DefaultOnError>")]
        pub value: Vec<Option<Drive>>,
        #[serde(rename = "@odata.nextLink")]
        pub next_link: Option<String>,
    }

    #[derive(Debug, Deserialize)]
    pub struct Drive {
        pub id: String,
        pub name: String,
        #[serde(rename = "driveType")]
        pub drive_type: Option<String>,
        pub owner: Option<DriveOwner>,
    }

    #[derive(Debug, Deserialize)]
    pub struct DriveOwner {
        pub user: Option<DriveOwnerUser>,
    }

    #[derive(Debug, Deserialize)]
    pub struct DriveOwnerUser {
        #[serde(rename = "displayName")]
        pub display_name: Option<String>,
    }

    #[derive(Debug, Deserialize)]
    pub struct ListItem {
        pub id: Option<String>,
        pub name: String,
        #[serde(rename = "eTag")]
        pub etag: Option<String>,
        #[serde(rename = "cTag")]
        pub ctag: Option<String>,
        #[serde(rename = "createdDateTime")]
        pub created_at: Option<String>,
        #[serde(rename = "lastModifiedDateTime")]
        pub modified_at: Option<String>,
        #[serde(rename = "parentReference")]
        pub parent_reference: Option<ParentReference>,
        #[serde(flatten)]
        pub kind: ListItemKind,
    }

    #[derive(Debug, Deserialize)]
    pub struct DeltaItem {
        pub id: String,
        pub name: Option<String>,
        pub size: Option<u64>,
        #[serde(rename = "eTag")]
        pub etag: Option<String>,
        #[serde(rename = "cTag")]
        pub ctag: Option<String>,
        #[serde(rename = "createdDateTime")]
        pub created_at: Option<String>,
        #[serde(rename = "lastModifiedDateTime")]
        pub modified_at: Option<String>,
        #[serde(rename = "parentReference")]
        pub parent_reference: Option<ParentReference>,
        pub file: Option<ListFileMetadata>,
        pub folder: Option<ListFolderMetadata>,
        pub deleted: Option<serde_json::Value>,
    }

    #[derive(Debug, Deserialize)]
    pub struct ParentReference {
        pub id: Option<String>,
        pub path: Option<String>,
    }

    #[derive(Debug, Deserialize)]
    #[serde(untagged)]
    pub enum ListItemKind {
        File {
            size: u64,
            #[serde(rename = "file")]
            _file: ListFileMetadata,
        },
        Folder {
            #[serde(rename = "folder")]
            _folder: ListFolderMetadata,
        },
    }

    #[derive(Debug, Deserialize)]
    pub struct ListFolderMetadata {
        #[serde(rename = "childCount")]
        pub _child_count: Option<u64>,
    }

    #[derive(Debug, Deserialize)]
    pub struct ListFileMetadata {
        #[serde(rename = "mimeType")]
        pub mime_type: Option<String>,
    }
}

const ONEDRIVE_GRAPH_API: &str = "https://graph.microsoft.com/v1.0";
const ONEDRIVE_ROOT_API: &str = "https://graph.microsoft.com/v1.0/me/drive";
const ONEDRIVE_API_BASE: &str = "https://login.microsoftonline.com/common/oauth2/v2.0";
const ONEDRIVE_REDIRECT_URI: &str = "melodytrove://oauth2redirect/";

fn is_auth_error<T>(r: &StorageBackendResult<T>) -> bool {
    matches!(
        r,
        Err(StorageBackendError::RequestFail(error))
            if error.status() == Some(StatusCode::UNAUTHORIZED)
    )
}

fn build_client() -> StorageBackendResult<reqwest::Client> {
    let client = reqwest::Client::builder()
        .connect_timeout(Duration::from_secs(10))
        .no_proxy()
        .build()?;
    Ok(client)
}

fn join_remote_path(parent: &str, name: &str) -> String {
    if parent == "/" {
        format!("/{name}")
    } else {
        format!("{}/{name}", parent.trim_end_matches('/'))
    }
}

fn path_from_parent_reference(
    parent_reference: Option<&onedrive_types::ParentReference>,
    name: Option<&str>,
) -> Option<String> {
    let name = name?;
    let parent_path = parent_reference?.path.as_deref()?;
    let relative_parent = parent_path
        .split_once("/root:")
        .map(|(_, path)| path)
        .or_else(|| parent_path.strip_suffix("/root"))?;
    Some(join_remote_path(
        if relative_parent.is_empty() {
            "/"
        } else {
            relative_parent
        },
        name,
    ))
}

fn validate_graph_cursor(cursor: &str) -> StorageBackendResult<reqwest::Url> {
    let url = reqwest::Url::parse(cursor)
        .map_err(|error| StorageBackendError::UrlParseError(error.to_string()))?;
    if url.scheme() != "https" || url.host_str() != Some("graph.microsoft.com") {
        return Err(StorageBackendError::UrlParseError(
            "OneDrive delta cursor must use https://graph.microsoft.com".to_string(),
        ));
    }
    Ok(url)
}

fn build_delta_url(
    root_api: &str,
    root_remote_id: &str,
    cursor: Option<&str>,
    latest_only: bool,
) -> StorageBackendResult<reqwest::Url> {
    if let Some(cursor) = cursor {
        if latest_only {
            return Err(StorageBackendError::UrlParseError(
                "latest-only delta request cannot include a cursor".to_string(),
            ));
        }
        return validate_graph_cursor(cursor);
    }

    let mut url = reqwest::Url::parse(root_api)
        .map_err(|error| StorageBackendError::UrlParseError(error.to_string()))?;
    {
        let mut segments = url
            .path_segments_mut()
            .map_err(|_| StorageBackendError::UrlParseError(root_api.to_string()))?;
        if root_remote_id == "root" {
            segments.extend(["root", "delta"]);
        } else {
            segments.extend(["items", root_remote_id, "delta"]);
        }
    }
    if latest_only {
        url.query_pairs_mut().append_pair("token", "latest");
    }
    Ok(url)
}

fn parse_delta_page(text: &str) -> StorageBackendResult<DeltaPage> {
    let response = serde_json::from_str::<onedrive_types::DeltaResponse>(text)?;
    let items = response
        .value
        .into_iter()
        .flatten()
        .map(|item| {
            let deleted = item.deleted.is_some();
            let is_dir = item.folder.is_some();
            DeltaItem {
                remote_id: item.id,
                parent_remote_id: item
                    .parent_reference
                    .as_ref()
                    .and_then(|reference| reference.id.clone()),
                path: path_from_parent_reference(
                    item.parent_reference.as_ref(),
                    item.name.as_deref(),
                ),
                name: item.name,
                size: item.size.and_then(|size| usize::try_from(size).ok()),
                is_dir,
                deleted,
                mime_type: item.file.and_then(|file| file.mime_type),
                etag: item.etag,
                ctag: item.ctag,
                created_at: item.created_at.as_deref().and_then(parse_remote_timestamp),
                modified_at: item.modified_at.as_deref().and_then(parse_remote_timestamp),
            }
        })
        .collect();
    Ok(DeltaPage {
        items,
        next_link: response.next_link,
        delta_link: response.delta_link,
    })
}

fn parse_drive_page(text: &str) -> StorageBackendResult<(Vec<OneDriveDrive>, Option<String>)> {
    let page = serde_json::from_str::<onedrive_types::DriveListResponse>(text)?;
    let drives = page
        .value
        .into_iter()
        .flatten()
        .map(|drive| OneDriveDrive {
            id: drive.id,
            name: drive.name,
            drive_type: drive.drive_type,
            owner_name: drive
                .owner
                .and_then(|owner| owner.user)
                .and_then(|user| user.display_name),
        })
        .collect();
    Ok((drives, page.next_link))
}

async fn refresh_token_by_code_impl(
    code: String,
    code_verifier: String,
) -> StorageBackendResult<Auth> {
    let client_id = MUSICAPP_ONEDRIVE_ID;
    let body = [
        ("client_id", client_id),
        ("redirect_uri", ONEDRIVE_REDIRECT_URI),
        ("code", code.as_str()),
        ("code_verifier", code_verifier.as_str()),
        ("grant_type", "authorization_code"),
    ]
    .into_iter()
    .map(|(key, value)| format!("{key}={}", urlencoding::encode(value)))
    .collect::<Vec<_>>()
    .join("&");

    let resp = tokio_runtime()
        .spawn(async move {
            let ret = build_client()?
                .request(reqwest::Method::POST, format!("{ONEDRIVE_API_BASE}/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(body)
                .send()
                .await?;
            Ok::<_, StorageBackendError>(ret)
        })
        .await??;
    let resp_text = resp.text().await?;
    let value = serde_json::from_str::<onedrive_types::RedeemCodeResp>(&resp_text)?;
    Ok(Auth {
        access_token: Some(value.access_token),
        refresh_token: value
            .refresh_token
            .ok_or(StorageBackendError::MissingOAuthRefreshToken)?,
    })
}

impl OneDriveBackend {
    pub fn new(arg: BuildOneDriveArg) -> Self {
        Self {
            drive_id: arg.drive_id.filter(|value| !value.is_empty()),
            auth: tokio::sync::RwLock::new(Auth {
                access_token: None,
                refresh_token: arg.code,
            }),
        }
    }

    fn root_api(&self) -> String {
        self.drive_id.as_ref().map_or_else(
            || ONEDRIVE_ROOT_API.to_string(),
            |drive_id| {
                format!(
                    "{ONEDRIVE_GRAPH_API}/drives/{}",
                    urlencoding::encode(drive_id)
                )
            },
        )
    }

    async fn build_base_header_map(&self) -> reqwest::header::HeaderMap {
        let mut header_map = reqwest::header::HeaderMap::new();
        {
            let r = self.auth.read().await;
            if let Some(access_token) = r.access_token.as_ref() {
                if let Ok(mut value) =
                    HeaderValue::from_str(format!("Bearer {access_token}").as_str())
                {
                    value.set_sensitive(true);
                    header_map.append(reqwest::header::AUTHORIZATION, value);
                }
            }
        }
        header_map
    }

    async fn try_ensure_refresh_token_by_refresh_token(&self) -> StorageBackendResult<()> {
        if self.auth.read().await.access_token.is_none() {
            self.refresh_token_by_refresh_token().await?;
        }
        Ok(())
    }

    async fn refresh_token_by_refresh_token(&self) -> StorageBackendResult<()> {
        let client_id = MUSICAPP_ONEDRIVE_ID;
        let refresh_token = self.auth.read().await.refresh_token.clone();
        let body = [
            ("client_id", client_id),
            ("redirect_uri", ONEDRIVE_REDIRECT_URI),
            ("refresh_token", refresh_token.as_str()),
            ("grant_type", "refresh_token"),
        ]
        .into_iter()
        .map(|(key, value)| format!("{key}={}", urlencoding::encode(value)))
        .collect::<Vec<_>>()
        .join("&");

        let resp = {
            let client = self.build_client()?;

            tokio_runtime()
                .spawn(async move {
                    client
                        .request(reqwest::Method::POST, format!("{ONEDRIVE_API_BASE}/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .body(body)
                        .send()
                        .await
                })
                .await??
        };
        let resp_text = resp.text().await?;
        let value = serde_json::from_str::<onedrive_types::RedeemCodeResp>(&resp_text)?;

        let mut w = self.auth.write().await;
        w.access_token = Some(value.access_token);
        if let Some(refresh_token) = value.refresh_token {
            w.refresh_token = refresh_token;
        }
        Ok(())
    }

    pub async fn current_refresh_token(&self) -> String {
        self.auth.read().await.refresh_token.clone()
    }

    async fn list_core_by_url(&self, url: &str) -> StorageBackendResult<reqwest::Response> {
        let url = reqwest::Url::parse(url)
            .map_err(|e| StorageBackendError::UrlParseError(e.to_string()))?;
        let base_headers = self.build_base_header_map().await;

        let resp = {
            let client = self.build_client()?;

            tokio_runtime()
                .spawn(async move {
                    client
                        .request(reqwest::Method::GET, url.clone())
                        .headers(base_headers)
                        .send()
                        .await
                })
                .await??
        };

        Ok(resp)
    }

    fn compute_list_url(&self, dir: &str) -> String {
        let subdir = if dir == "/" {
            "/root/children".to_string()
        } else {
            ("/root:".to_string() + dir + ":/children").to_string()
        };
        let _url = self.root_api() + subdir.as_str();
        _url
    }

    async fn list_impl(&self, dir: &str) -> StorageBackendResult<Vec<Entry>> {
        let mut url = self.compute_list_url(dir);

        let mut ret: Vec<Entry> = Default::default();
        loop {
            let resp = self.list_core_by_url(&url).await?.error_for_status()?;
            let text: String = resp.text().await?;
            let obj: onedrive_types::ListItemResponse = match serde_json::from_str(&text) {
                Ok(obj) => obj,
                Err(error) => {
                    tracing::warn!("could not parse OneDrive list response");
                    return Err(error.into());
                }
            };

            for item in obj.value.into_iter().flatten() {
                let onedrive_types::ListItem {
                    id,
                    name,
                    etag,
                    ctag,
                    created_at,
                    modified_at,
                    parent_reference,
                    kind,
                } = item;
                let path = join_remote_path(dir, &name);
                let parent_remote_id = parent_reference.and_then(|reference| reference.id);
                match kind {
                    onedrive_types::ListItemKind::File { size, _file } => {
                        ret.push(Entry {
                            name,
                            path,
                            size: Some(size as usize),
                            is_dir: false,
                            remote_id: id,
                            parent_remote_id,
                            mime_type: _file.mime_type,
                            etag,
                            ctag,
                            created_at: created_at.as_deref().and_then(parse_remote_timestamp),
                            modified_at: modified_at.as_deref().and_then(parse_remote_timestamp),
                        });
                    }
                    onedrive_types::ListItemKind::Folder { .. } => {
                        ret.push(Entry {
                            name,
                            path,
                            size: None,
                            is_dir: true,
                            remote_id: id,
                            parent_remote_id,
                            mime_type: None,
                            etag,
                            ctag,
                            created_at: created_at.as_deref().and_then(parse_remote_timestamp),
                            modified_at: modified_at.as_deref().and_then(parse_remote_timestamp),
                        });
                    }
                }
            }
            tracing::info!("load {} items", ret.len());

            if let Some(next_link) = obj.next_link {
                url = next_link;
            } else {
                break;
            }
        }

        ret.sort_by(|lhs, rhs| {
            if lhs.is_dir ^ rhs.is_dir {
                if lhs.is_dir {
                    return Ordering::Less;
                } else {
                    return Ordering::Greater;
                }
            }
            if lhs.path < rhs.path {
                Ordering::Less
            } else {
                Ordering::Greater
            }
        });

        Ok(ret)
    }

    async fn list_with_retry_impl(&self, dir: String) -> StorageBackendResult<Vec<Entry>> {
        self.try_ensure_refresh_token_by_refresh_token().await?;
        let r = self.list_impl(dir.as_str()).await;
        if !is_auth_error(&r) {
            return r;
        }
        self.refresh_token_by_refresh_token().await?;
        self.list_impl(dir.as_str()).await
    }

    async fn get_impl(&self, p: &str, byte_offset: u64) -> StorageBackendResult<StreamFile> {
        let _url = self.root_api() + "/root:" + p + ":/content";
        let url = reqwest::Url::parse(_url.as_str())
            .map_err(|e| StorageBackendError::UrlParseError(e.to_string()))?;

        let mut headers = self.build_base_header_map().await;
        headers.insert(
            reqwest::header::RANGE,
            HeaderValue::from_str(format!("bytes={byte_offset}-").as_str()).unwrap(),
        );

        let resp = {
            let client = self.build_client()?;

            tokio_runtime()
                .spawn(async move { client.get(url.clone()).headers(headers).send().await })
                .await??
        };
        let byte_offset = if resp.headers().get(reqwest::header::CONTENT_RANGE).is_some() {
            0
        } else {
            byte_offset
        };
        let res = resp
            .error_for_status()
            .map(|resp| StreamFile::new(resp, byte_offset))?;
        Ok(res)
    }

    async fn get_with_retry_impl(
        &self,
        p: String,
        byte_offset: u64,
    ) -> StorageBackendResult<StreamFile> {
        self.try_ensure_refresh_token_by_refresh_token().await?;
        let r = self.get_impl(p.as_str(), byte_offset).await;
        if !is_auth_error(&r) {
            return r;
        }
        self.refresh_token_by_refresh_token().await?;
        self.get_impl(p.as_str(), byte_offset).await
    }

    async fn get_range_response_impl(
        &self,
        p: &str,
        range: ByteRange,
    ) -> StorageBackendResult<RangeResponse> {
        let url = reqwest::Url::parse(&(self.root_api() + "/root:" + p + ":/content"))
            .map_err(|error| StorageBackendError::UrlParseError(error.to_string()))?;
        let mut headers = self.build_base_header_map().await;
        headers.insert(
            reqwest::header::RANGE,
            HeaderValue::from_str(
                format!("bytes={}-{}", range.start, range.end_inclusive).as_str(),
            )
            .unwrap(),
        );
        let response = {
            let client = self.build_client()?;
            tokio_runtime()
                .spawn(async move { client.get(url).headers(headers).send().await })
                .await??
        };
        read_range_response(response, range).await
    }

    async fn get_range_response_with_retry_impl(
        &self,
        p: String,
        range: ByteRange,
    ) -> StorageBackendResult<RangeResponse> {
        self.try_ensure_refresh_token_by_refresh_token().await?;
        let result = self.get_range_response_impl(&p, range).await;
        if !is_auth_error(&result) {
            return result;
        }
        self.refresh_token_by_refresh_token().await?;
        self.get_range_response_impl(&p, range).await
    }

    async fn delta_impl(
        &self,
        root_remote_id: &str,
        cursor: Option<&str>,
        latest_only: bool,
    ) -> StorageBackendResult<DeltaPage> {
        let url = build_delta_url(&self.root_api(), root_remote_id, cursor, latest_only)?;
        let response = self.list_core_by_url(url.as_str()).await?;
        if response.status() == StatusCode::GONE {
            return Err(StorageBackendError::DeltaResyncRequired);
        }
        let text = response.error_for_status()?.text().await?;
        parse_delta_page(&text)
    }

    async fn delta_with_retry_impl(
        &self,
        root_remote_id: String,
        cursor: Option<String>,
        latest_only: bool,
    ) -> StorageBackendResult<DeltaPage> {
        self.try_ensure_refresh_token_by_refresh_token().await?;
        let result = self
            .delta_impl(&root_remote_id, cursor.as_deref(), latest_only)
            .await;
        if !is_auth_error(&result) {
            return result;
        }
        self.refresh_token_by_refresh_token().await?;
        self.delta_impl(&root_remote_id, cursor.as_deref(), latest_only)
            .await
    }

    pub async fn list_drives(&self) -> StorageBackendResult<Vec<OneDriveDrive>> {
        self.try_ensure_refresh_token_by_refresh_token().await?;
        let mut url = format!("{ONEDRIVE_GRAPH_API}/me/drives");
        let mut drives = Vec::new();
        let mut refreshed = false;
        loop {
            let response = self.list_core_by_url(&url).await?;
            if response.status() == StatusCode::UNAUTHORIZED {
                if refreshed {
                    return Err(response.error_for_status().unwrap_err().into());
                }
                self.refresh_token_by_refresh_token().await?;
                refreshed = true;
                continue;
            }
            let text = response.error_for_status()?.text().await?;
            let (page_drives, next_link) = parse_drive_page(&text)?;
            drives.extend(page_drives);
            match next_link {
                Some(next_link) => url = next_link,
                None => break,
            }
        }
        drives.sort_by(|left, right| left.name.cmp(&right.name));
        Ok(drives)
    }

    fn build_client(&self) -> StorageBackendResult<reqwest::Client> {
        build_client()
    }
}

impl StorageBackend for OneDriveBackend {
    fn list(&self, dir: String) -> BoxFuture<'_, StorageBackendResult<Vec<Entry>>> {
        Box::pin(self.list_with_retry_impl(dir))
    }

    fn get(&self, p: String, byte_offset: u64) -> BoxFuture<'_, StorageBackendResult<StreamFile>> {
        Box::pin(self.get_with_retry_impl(p, byte_offset))
    }
    fn get_range_response(
        &self,
        p: String,
        range: ByteRange,
    ) -> BoxFuture<'_, StorageBackendResult<RangeResponse>> {
        Box::pin(self.get_range_response_with_retry_impl(p, range))
    }
    fn delta(
        &self,
        root_remote_id: String,
        cursor: Option<String>,
        latest_only: bool,
    ) -> BoxFuture<'_, StorageBackendResult<DeltaPage>> {
        Box::pin(self.delta_with_retry_impl(root_remote_id, cursor, latest_only))
    }
    fn current_refresh_token(&self) -> BoxFuture<'_, StorageBackendResult<Option<String>>> {
        Box::pin(async move { Ok(Some(self.current_refresh_token().await)) })
    }
}

impl OneDriveBackend {
    pub async fn request_refresh_token(
        code: String,
        code_verifier: String,
    ) -> StorageBackendResult<String> {
        let authed = refresh_token_by_code_impl(code, code_verifier).await?;
        Ok(authed.refresh_token)
    }
}

#[cfg(test)]
mod tests {
    use super::{
        build_delta_url, parse_delta_page, parse_drive_page, validate_graph_cursor,
        ONEDRIVE_ROOT_API,
    };

    #[test]
    fn parses_delta_files_folders_deletions_and_links() {
        let page = parse_delta_page(
            r#"{
                "value": [
                    {
                        "id": "file-1",
                        "name": "Song.flac",
                        "size": 1234,
                        "eTag": "\"etag\"",
                        "cTag": "\"ctag\"",
                        "createdDateTime": "2026-06-25T01:02:03Z",
                        "lastModifiedDateTime": "2026-06-25T04:05:06Z",
                        "parentReference": {
                            "id": "folder-1",
                            "path": "/drive/root:/Music/Album"
                        },
                        "file": { "mimeType": "audio/flac" }
                    },
                    {
                        "id": "folder-2",
                        "name": "Renamed",
                        "parentReference": {
                            "id": "folder-1",
                            "path": "/drives/drive-id/root:/Music"
                        },
                        "folder": { "childCount": 3 }
                    },
                    {
                        "id": "deleted-1",
                        "deleted": { "state": "deleted" }
                    }
                ],
                "@odata.nextLink": "https://graph.microsoft.com/v1.0/me/drive/root/delta?token=next",
                "@odata.deltaLink": null
            }"#,
        )
        .unwrap();

        assert_eq!(page.items.len(), 3);
        assert_eq!(
            page.items[0].path.as_deref(),
            Some("/Music/Album/Song.flac")
        );
        assert_eq!(page.items[0].parent_remote_id.as_deref(), Some("folder-1"));
        assert_eq!(page.items[0].mime_type.as_deref(), Some("audio/flac"));
        assert_eq!(page.items[1].path.as_deref(), Some("/Music/Renamed"));
        assert!(page.items[1].is_dir);
        assert!(page.items[2].deleted);
        assert_eq!(page.items[2].path, None);
        assert!(page.next_link.is_some());
    }

    #[test]
    fn builds_latest_delta_url_and_rejects_untrusted_cursor() {
        let url = build_delta_url(ONEDRIVE_ROOT_API, "item!123", None, true).unwrap();
        assert_eq!(url.scheme(), "https");
        assert_eq!(url.host_str(), Some("graph.microsoft.com"));
        assert!(url.path().ends_with("/items/item!123/delta"));
        assert_eq!(url.query(), Some("token=latest"));

        let root_url = build_delta_url(ONEDRIVE_ROOT_API, "root", None, false).unwrap();
        assert!(root_url.path().ends_with("/root/delta"));

        let explicit_drive_url = build_delta_url(
            "https://graph.microsoft.com/v1.0/drives/drive-id",
            "item!123",
            None,
            false,
        )
        .unwrap();
        assert!(explicit_drive_url
            .path()
            .ends_with("/drives/drive-id/items/item!123/delta"));

        assert!(validate_graph_cursor(
            "https://graph.microsoft.com/v1.0/me/drive/root/delta?token=next"
        )
        .is_ok());
        assert!(validate_graph_cursor("https://example.com/steal?token=next").is_err());
        assert!(
            validate_graph_cursor("http://graph.microsoft.com/v1.0/me/drive/root/delta").is_err()
        );
    }

    #[test]
    fn parses_drive_list_and_owner() {
        let (drives, next_link) = parse_drive_page(
            r#"{
                "value": [
                    {
                        "id": "drive-1",
                        "name": "Documents",
                        "driveType": "business",
                        "owner": {
                            "user": { "displayName": "TidePlayer Tester" }
                        }
                    }
                ],
                "@odata.nextLink": "https://graph.microsoft.com/v1.0/me/drives?$skiptoken=next"
            }"#,
        )
        .unwrap();

        assert_eq!(drives.len(), 1);
        assert_eq!(drives[0].id, "drive-1");
        assert_eq!(drives[0].drive_type.as_deref(), Some("business"));
        assert_eq!(drives[0].owner_name.as_deref(), Some("TidePlayer Tester"));
        assert!(next_link.is_some());
    }
}
