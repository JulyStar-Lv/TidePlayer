use std::{
    num::NonZeroUsize,
    sync::{Arc, Mutex, OnceLock},
    time::Duration,
};

use lru::LruCache;
use sha2::{Digest, Sha256};

use crate::{
    ctx::BackendContext,
    error::{BError, BResult},
    objects::{ArgUpsertStorage, Storage},
    schema::{StorageEntryLoc, StorageType},
};
use storage_backend::{
    BuildOneDriveArg, BuildOpenListArg, BuildSmbArg, BuildWebdavArg, LocalBackend, OneDriveBackend,
    OpenListBackend, SmbBackend, StorageBackend, StreamFile, Webdav,
};

pub fn build_openlist_backend(
    base_url: String,
    token: String,
    timeout: Duration,
) -> BResult<Arc<dyn StorageBackend + Send + Sync>> {
    Ok(Arc::new(OpenListBackend::new(BuildOpenListArg {
        base_url,
        token,
        timeout,
    })?))
}

pub fn build_openlist_playback_backend(
    base_url: String,
    token: String,
    timeout: Duration,
) -> BResult<Arc<dyn StorageBackend + Send + Sync>> {
    Ok(Arc::new(OpenListBackend::new_playback(BuildOpenListArg {
        base_url,
        token,
        timeout,
    })?))
}

const SMB_BACKEND_CACHE_CAPACITY: usize = 8;

struct CachedSmbBackend {
    fingerprint: [u8; 32],
    backend: Arc<dyn StorageBackend + Send + Sync>,
}

static SMB_BACKENDS: OnceLock<Mutex<LruCache<i64, CachedSmbBackend>>> = OnceLock::new();

pub fn build_storage_backend_by_arg(
    _cx: &BackendContext,
    arg: ArgUpsertStorage,
) -> BResult<Arc<dyn StorageBackend + Send + Sync>> {
    let connect_timeout = Duration::from_secs(5);

    let ret: Arc<dyn StorageBackend + Send + Sync + 'static> = match arg.typ {
        StorageType::Local => Arc::new(LocalBackend::new()),
        StorageType::Webdav => {
            let arg = BuildWebdavArg {
                addr: arg.addr,
                username: arg.username,
                password: arg.password,
                is_anonymous: arg.is_anonymous,
                connect_timeout,
            };
            Arc::new(Webdav::new(arg))
        }
        StorageType::OneDrive => {
            let arg = BuildOneDriveArg {
                code: arg.password,
                drive_id: (!arg.addr.is_empty()).then_some(arg.addr),
            };
            Arc::new(OneDriveBackend::new(arg))
        }
        StorageType::Smb => build_smb_backend(arg, connect_timeout)?,
        StorageType::OpenList => {
            return Err(BError::CustomError {
                message: "OpenList storage backend is not implemented".to_string(),
            });
        }
    };
    Ok(ret)
}

#[cfg(test)]
mod tests {
    use super::build_storage_backend_by_arg;
    use crate::schema::StorageType;
    use crate::{ctx::BackendContext, error::BError, objects::ArgUpsertStorage};

    #[test]
    fn open_list_backend_is_explicitly_unsupported() {
        let result = build_storage_backend_by_arg(
            &BackendContext::new(),
            ArgUpsertStorage {
                typ: StorageType::OpenList,
                ..Default::default()
            },
        );

        match result {
            Err(BError::CustomError { message }) => {
                assert!(message.contains("OpenList"));
            }
            _ => panic!("expected explicit OpenList error"),
        }
    }
}

fn build_smb_backend(
    arg: ArgUpsertStorage,
    connect_timeout: Duration,
) -> BResult<Arc<dyn StorageBackend + Send + Sync>> {
    let storage_id = arg.id.map(|id| *id.as_ref());
    let fingerprint = smb_fingerprint(&arg);
    if let Some(storage_id) = storage_id {
        if let Some(cached) = smb_backends().lock().unwrap().get(&storage_id) {
            if cached.fingerprint == fingerprint {
                return Ok(Arc::clone(&cached.backend));
            }
        }
    }

    let smb_arg = BuildSmbArg::from_url(
        &arg.addr,
        arg.username,
        arg.password,
        arg.is_anonymous,
        connect_timeout,
    )?;
    let backend: Arc<dyn StorageBackend + Send + Sync> = Arc::new(SmbBackend::new(smb_arg)?);
    if let Some(storage_id) = storage_id {
        smb_backends().lock().unwrap().put(
            storage_id,
            CachedSmbBackend {
                fingerprint,
                backend: Arc::clone(&backend),
            },
        );
    }
    Ok(backend)
}

fn smb_backends() -> &'static Mutex<LruCache<i64, CachedSmbBackend>> {
    SMB_BACKENDS.get_or_init(|| {
        Mutex::new(LruCache::new(
            NonZeroUsize::new(SMB_BACKEND_CACHE_CAPACITY)
                .expect("SMB backend cache capacity must be non-zero"),
        ))
    })
}

fn smb_fingerprint(arg: &ArgUpsertStorage) -> [u8; 32] {
    let mut hasher = Sha256::new();
    hasher.update(arg.addr.as_bytes());
    hasher.update([0]);
    hasher.update(arg.username.as_bytes());
    hasher.update([0]);
    hasher.update(arg.password.as_bytes());
    hasher.update([arg.is_anonymous as u8]);
    hasher.finalize().into()
}

pub fn release_cached_storage_backend(storage_id: crate::schema::StorageId) {
    if let Some(cache) = SMB_BACKENDS.get() {
        cache.lock().unwrap().pop(storage_id.as_ref());
    }
}

pub fn build_storage_backend(
    cx: &BackendContext,
    storage: Storage,
) -> BResult<Arc<dyn StorageBackend + Send + Sync>> {
    build_storage_backend_by_arg(
        cx,
        ArgUpsertStorage {
            id: Some(storage.id),
            addr: storage.addr,
            alias: storage.alias,
            username: storage.username,
            password: storage.password,
            is_anonymous: storage.is_anonymous,
            typ: storage.typ,
        },
    )
}

async fn get_asset_file_by_loc(
    cx: &BackendContext,
    storage: Storage,
    entry: StorageEntryLoc,
    byte_offset: u64,
) -> BResult<Option<StreamFile>> {
    let storage_backend = build_storage_backend(cx, storage)?;

    let file = storage_backend.get(entry.path, byte_offset).await;
    if let Err(e) = &file {
        if e.is_not_found() {
            return Ok(None);
        }
    }
    let file = file?;
    Ok(Some(file))
}

pub(crate) async fn get_asset_file(
    cx: &BackendContext,
    storage: Storage,
    entry: StorageEntryLoc,
    byte_offset: u64,
) -> BResult<Option<StreamFile>> {
    get_asset_file_by_loc(cx, storage, entry, byte_offset).await
}
