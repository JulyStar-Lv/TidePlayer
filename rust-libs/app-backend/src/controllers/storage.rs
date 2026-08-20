use std::{sync::Arc, time::Duration};

use crate::schema::{StorageEntryLoc, StorageId, StorageType};
use storage_backend::{
    list_smb_server_path, BuildOneDriveArg, BuildSmbArg, BuildWebdavArg, OneDriveBackend, Webdav,
};

use crate::{
    error::{BError, BResult},
    objects::{
        create_onedrive_oauth_session, ListStorageEntryChildrenResp, OneDriveDeltaItem,
        OneDriveDeltaPage, OneDriveDeltaPageResult, OneDriveDeltaRequest, OneDriveDrive,
        OneDriveDriveList, OneDriveOAuthSession, Storage, StorageConnectionTestResult,
        WebDavSyncItem, WebDavSyncPage, WebDavSyncPageResult, WebDavSyncRequest,
    },
    services::{
        build_openlist_backend, build_storage_backend, build_storage_backend_by_arg,
        release_cached_storage_backend, storage_entry, RemoteMusicScanSession,
    },
    ArgUpsertStorage, Backend,
};

fn normalize_arg_upsert_storage(mut arg: ArgUpsertStorage) -> ArgUpsertStorage {
    if arg.is_anonymous {
        arg.username = Default::default();
        arg.password = Default::default();
    }
    arg
}

#[uniffi::export]
pub async fn ct_upload_webdav_backup(
    address: String,
    username: String,
    password: String,
    is_anonymous: bool,
    directory: String,
    file_name: String,
    content: String,
) -> BResult<()> {
    let backend = Webdav::new(BuildWebdavArg {
        addr: address,
        username,
        password,
        is_anonymous,
        connect_timeout: Duration::from_secs(30),
    });
    backend
        .put_text_file(&directory, &file_name, content)
        .await?;
    Ok(())
}

#[uniffi::export]
pub async fn ct_exchange_onedrive_code(
    _cx: Arc<Backend>,
    code: String,
    code_verifier: String,
) -> BResult<String> {
    let refresh_token = OneDriveBackend::request_refresh_token(code, code_verifier).await?;
    Ok(refresh_token)
}

#[uniffi::export]
pub async fn ct_list_onedrive_drives(refresh_token: String) -> BResult<OneDriveDriveList> {
    let backend = OneDriveBackend::new(BuildOneDriveArg {
        code: refresh_token,
        drive_id: None,
    });
    let drives = backend
        .list_drives()
        .await?
        .into_iter()
        .map(|drive| OneDriveDrive {
            id: drive.id,
            name: drive.name,
            drive_type: drive.drive_type,
            owner_name: drive.owner_name,
        })
        .collect();
    Ok(OneDriveDriveList {
        drives,
        refresh_token: backend.current_refresh_token().await,
    })
}

#[uniffi::export]
pub async fn ct_get_onedrive_delta_page(
    cx: Arc<Backend>,
    storage: Storage,
    request: OneDriveDeltaRequest,
) -> BResult<OneDriveDeltaPageResult> {
    let backend = build_storage_backend(cx.get_context(), storage)?;
    let page = backend
        .delta(request.root_remote_id, request.cursor, request.latest_only)
        .await;
    let page = match page {
        Ok(page) => page,
        Err(error) if error.is_delta_resync_required() => {
            return Ok(OneDriveDeltaPageResult::ResyncRequired);
        }
        Err(error) => return Err(error.into()),
    };
    Ok(OneDriveDeltaPageResult::Page(OneDriveDeltaPage {
        items: page
            .items
            .into_iter()
            .map(|item| OneDriveDeltaItem {
                remote_id: item.remote_id,
                parent_remote_id: item.parent_remote_id,
                name: item.name,
                path: item.path,
                size: item.size.map(|size| size as u64),
                is_dir: item.is_dir,
                deleted: item.deleted,
                mime_type: item.mime_type,
                etag: item.etag,
                ctag: item.ctag,
                created_at: item.created_at,
                modified_at: item.modified_at,
            })
            .collect(),
        next_link: page.next_link,
        delta_link: page.delta_link,
        refresh_token: backend.current_refresh_token().await?,
    }))
}

#[uniffi::export]
pub async fn ct_get_webdav_sync_page(
    cx: Arc<Backend>,
    storage: Storage,
    request: WebDavSyncRequest,
) -> BResult<WebDavSyncPageResult> {
    let backend = build_storage_backend(cx.get_context(), storage)?;
    let page = backend
        .webdav_sync(request.root_path, request.sync_token)
        .await;
    let page = match page {
        Ok(page) => page,
        Err(storage_backend::StorageBackendError::DeltaNotSupported) => {
            return Ok(WebDavSyncPageResult::Unsupported);
        }
        Err(error) if error.is_delta_resync_required() => {
            return Ok(WebDavSyncPageResult::ResyncRequired);
        }
        Err(error) => return Err(error.into()),
    };
    Ok(WebDavSyncPageResult::Page(WebDavSyncPage {
        items: page
            .items
            .into_iter()
            .map(|item| WebDavSyncItem {
                path: item.path,
                name: item.name,
                size: item.size.map(|size| size as u64),
                is_dir: item.is_dir,
                deleted: item.deleted,
                mime_type: item.mime_type,
                etag: item.etag,
                created_at: item.created_at,
                modified_at: item.modified_at,
            })
            .collect(),
        sync_token: page.sync_token,
    }))
}

#[uniffi::export]
pub async fn ct_test_storage(
    cx: Arc<Backend>,
    arg: ArgUpsertStorage,
) -> BResult<StorageConnectionTestResult> {
    let arg = normalize_arg_upsert_storage(arg);
    let cx = cx.get_context();
    let res = if arg.typ == StorageType::Smb {
        let mut smb_arg = BuildSmbArg::from_url(
            &arg.addr,
            arg.username.clone(),
            arg.password.clone(),
            arg.is_anonymous,
            Duration::from_secs(5),
        )?;
        if smb_arg.share.is_empty() {
            smb_arg.root_path.clear();
            list_smb_server_path(smb_arg, "/".to_string()).await
        } else {
            build_storage_backend_by_arg(cx, arg)?
                .list("/".to_string())
                .await
        }
    } else {
        build_storage_backend_by_arg(cx, arg)?
            .list("/".to_string())
            .await
    };

    match res {
        Ok(_) => Ok(StorageConnectionTestResult::Success),
        Err(e) => {
            tracing::warn!(kind = ?storage_error_kind(&e), "storage connection test failed");
            if e.is_unauthorized() {
                Ok(StorageConnectionTestResult::Unauthorized)
            } else if e.is_timeout() {
                Ok(StorageConnectionTestResult::Timeout)
            } else if e.is_permission_denied() {
                Ok(StorageConnectionTestResult::PermissionDenied)
            } else if e.is_not_found() {
                Ok(StorageConnectionTestResult::NotFound)
            } else if e.is_invalid_path() {
                Ok(StorageConnectionTestResult::InvalidAddress)
            } else if e.is_connection_lost() {
                Ok(StorageConnectionTestResult::Unavailable)
            } else if e.is_unsupported() {
                Ok(StorageConnectionTestResult::Unsupported)
            } else {
                Ok(StorageConnectionTestResult::OtherError)
            }
        }
    }
}

#[uniffi::export]
pub async fn ct_list_smb_server_entry_children(
    _cx: Arc<Backend>,
    storage: Storage,
    arg: StorageEntryLoc,
) -> BResult<ListStorageEntryChildrenResp> {
    if storage.typ != StorageType::Smb {
        return Ok(ListStorageEntryChildrenResp::Unsupported);
    }
    let mut smb_arg = BuildSmbArg::from_url(
        &storage.addr,
        storage.username,
        storage.password,
        storage.is_anonymous,
        Duration::from_secs(5),
    )?;
    smb_arg.share.clear();
    smb_arg.root_path.clear();
    let storage_id = arg.storage_id;
    match list_smb_server_path(smb_arg, arg.path).await {
        Ok(entries) => Ok(ListStorageEntryChildrenResp::Ok(
            entries
                .into_iter()
                .map(|entry| storage_entry(storage_id, entry))
                .collect(),
        )),
        Err(error) => {
            tracing::warn!(
                kind = ?storage_error_kind(&error),
                "SMB server directory listing failed"
            );
            Ok(list_error_response(&error))
        }
    }
}

#[uniffi::export]
pub fn ct_release_storage_backend(storage_id: crate::schema::StorageId) {
    release_cached_storage_backend(storage_id);
}

fn storage_error_kind(error: &storage_backend::StorageBackendError) -> &'static str {
    if error.is_unauthorized() {
        "authentication"
    } else if error.is_timeout() {
        "timeout"
    } else if error.is_permission_denied() {
        "permission"
    } else if error.is_not_found() {
        "not_found"
    } else if error.is_invalid_path() {
        "invalid_path"
    } else if error.is_connection_lost() {
        "connection"
    } else if error.is_unsupported() {
        "unsupported"
    } else {
        "other"
    }
}

#[uniffi::export]
pub async fn ct_list_storage_entry_children(
    cx: Arc<Backend>,
    storage: Storage,
    arg: StorageEntryLoc,
) -> BResult<ListStorageEntryChildrenResp> {
    let backend = build_storage_backend(cx.get_context(), storage)?;
    let storage_id = arg.storage_id;
    let res = backend.list(arg.path).await;

    match res {
        Ok(entries) => {
            let entries = entries
                .into_iter()
                .map(|entry| storage_entry(storage_id, entry))
                .collect();
            Ok(ListStorageEntryChildrenResp::Ok(entries))
        }
        Err(e) => {
            tracing::warn!(
                kind = ?storage_error_kind(&e),
                "storage directory listing failed"
            );
            Ok(list_error_response(&e))
        }
    }
}

#[uniffi::export]
pub async fn ct_scan_storage_music_folder(
    cx: Arc<Backend>,
    storage: Storage,
    arg: StorageEntryLoc,
) -> BResult<ListStorageEntryChildrenResp> {
    let backend = build_storage_backend(cx.get_context(), storage)?;
    let mut files = Vec::new();
    let session = RemoteMusicScanSession::new(arg.storage_id, backend, arg.path);
    loop {
        let batch = match session.next_batch(1_000).await {
            Ok(batch) => batch,
            Err(BError::RemoteStorageError(error)) => {
                tracing::warn!(
                    kind = ?storage_error_kind(&error),
                    "storage music scan failed"
                );
                return Ok(list_error_response(&error));
            }
            Err(_) => {
                tracing::warn!("storage music scan failed outside the storage backend");
                return Ok(ListStorageEntryChildrenResp::Unknown);
            }
        };
        files.extend(batch.entries);
        if batch.done {
            break;
        }
    }

    Ok(ListStorageEntryChildrenResp::Ok(files))
}

fn list_error_response(
    error: &storage_backend::StorageBackendError,
) -> ListStorageEntryChildrenResp {
    if error.is_unauthorized() {
        ListStorageEntryChildrenResp::AuthenticationFailed
    } else if error.is_timeout() {
        ListStorageEntryChildrenResp::Timeout
    } else if error.is_permission_denied() {
        ListStorageEntryChildrenResp::PermissionDenied
    } else if error.is_not_found() {
        ListStorageEntryChildrenResp::NotFound
    } else if error.is_invalid_path() {
        ListStorageEntryChildrenResp::InvalidAddress
    } else if error.is_connection_lost() {
        ListStorageEntryChildrenResp::Unavailable
    } else if error.is_unsupported() {
        ListStorageEntryChildrenResp::Unsupported
    } else {
        ListStorageEntryChildrenResp::Unknown
    }
}

#[uniffi::export]
pub fn ct_start_storage_music_scan(
    cx: Arc<Backend>,
    storage: Storage,
    arg: StorageEntryLoc,
) -> BResult<Arc<RemoteMusicScanSession>> {
    let backend = build_storage_backend(cx.get_context(), storage)?;
    Ok(RemoteMusicScanSession::new(
        arg.storage_id,
        backend,
        arg.path,
    ))
}

#[uniffi::export]
pub fn ct_start_openlist_music_scan(
    storage_id: StorageId,
    base_url: String,
    token: String,
    path: String,
) -> BResult<Arc<RemoteMusicScanSession>> {
    let backend = build_openlist_backend(base_url, token, std::time::Duration::from_secs(45))?;
    Ok(RemoteMusicScanSession::new_openlist(
        storage_id, backend, path,
    ))
}

#[uniffi::export]
pub fn ct_start_onedrive_oauth() -> OneDriveOAuthSession {
    create_onedrive_oauth_session()
}

#[cfg(test)]
mod tests {
    use crate::services::is_supported_music_path;

    #[test]
    fn detects_supported_music_extensions_case_insensitively() {
        assert!(is_supported_music_path("/Music/Track.FLAC"));
        assert!(is_supported_music_path("/Music/Track.opus"));
        assert!(!is_supported_music_path("/Music/cover.jpg"));
    }
}
