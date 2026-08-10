use std::{collections::HashMap, sync::Arc};

use crate::{
    error::BResult,
    objects::Storage,
    schema::StorageEntryLoc,
    services::{
        build_storage_backend, promote_completed_playback_cache, start_completed_playback_cache,
        start_http_playback_cache_gateway, start_playback_gateway, PlaybackCacheOptions,
        PlaybackCachePromotionResult, PlaybackSession,
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
