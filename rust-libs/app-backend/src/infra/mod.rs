mod crash_writer;
mod diagnostics_export;
mod file_ops;
mod incidents;
mod log_reader;
mod log_rotation;
mod logging;
mod model;
mod redaction;
mod runtime;
mod startup_journal;

use std::sync::{Arc, Mutex, Once, OnceLock};

use tracing::subscriber::set_global_default;
use tracing_subscriber::filter::{LevelFilter, Targets};
use tracing_subscriber::prelude::*;

use crate::error::{BError, BResult};

pub use model::*;
use runtime::DiagnosticsRuntime;

static RUNTIME: OnceLock<Arc<DiagnosticsRuntime>> = OnceLock::new();
static INITIALIZE_LOCK: Mutex<()> = Mutex::new(());
static PANIC_HOOK: Once = Once::new();
#[cfg(any(target_os = "android", test))]
const ANDROID_TRACING_TAG: &str = "TidePlayer";

fn diagnostics_filter() -> Targets {
    Targets::new()
        .with_default(LevelFilter::TRACE)
        .with_target("hyper", LevelFilter::WARN)
}

pub fn logs_dir(dir: &str) -> std::path::PathBuf {
    std::path::Path::new(dir).join("diagnostics/logs/sessions")
}

pub(crate) fn init_infra_compat(dir: &str) -> BResult<DiagnosticsRuntimeState> {
    initialize_diagnostics_runtime(DiagnosticsRuntimeInit {
        app_document_dir: dir.to_string(),
        app_cache_dir: std::path::Path::new(dir)
            .join("cache")
            .to_string_lossy()
            .into_owned(),
        platform: std::env::consts::OS.to_string(),
        app_version: "unknown".to_string(),
        build_info: "legacy create_backend initialization".to_string(),
        git_commit_sha: String::new(),
        process_name: "TidePlayer".to_string(),
        user_forced_safe_mode: false,
        last_user_requested_exit_at_epoch_ms: None,
    })
}

#[uniffi::export]
pub fn initialize_diagnostics_runtime(
    init: DiagnosticsRuntimeInit,
) -> BResult<DiagnosticsRuntimeState> {
    let _guard = INITIALIZE_LOCK.lock().map_err(|_| BError::CustomError {
        message: "diagnostics initialization lock poisoned".to_string(),
    })?;
    if let Some(runtime) = RUNTIME.get() {
        if runtime.state().diagnostics_root
            != std::path::Path::new(&init.app_document_dir)
                .join("diagnostics")
                .to_string_lossy()
        {
            return Err(BError::CustomError {
                message: "diagnostics runtime already initialized for a different directory"
                    .to_string(),
            });
        }
        return Ok(runtime.state());
    }

    let runtime = DiagnosticsRuntime::start(init)?;
    let subscriber = tracing_subscriber::registry().with(
        runtime
            .log_store()
            .layer()
            .with_filter(diagnostics_filter()),
    );
    #[cfg(target_os = "android")]
    let subscriber = subscriber.with(
        tracing_android::layer(ANDROID_TRACING_TAG)
            .map_err(|error| BError::CustomError {
                message: format!("failed to initialize Android tracing layer: {error}"),
            })?
            .with_filter(diagnostics_filter()),
    );
    set_global_default(subscriber).map_err(|error| BError::CustomError {
        message: format!("failed to initialize diagnostics tracing subscriber: {error}"),
    })?;
    RUNTIME
        .set(Arc::clone(&runtime))
        .map_err(|_| BError::CustomError {
            message: "diagnostics runtime initialized concurrently".to_string(),
        })?;
    PANIC_HOOK.call_once(|| {
        crash_writer::install_panic_hook(|message, location, backtrace| {
            if let Some(runtime) = RUNTIME.get() {
                runtime.record_rust_panic(message, location, backtrace);
            }
        });
    });
    runtime.update_startup_stage(StartupStage::DiagnosticsReady)?;
    Ok(runtime.state())
}

#[uniffi::export]
pub fn get_diagnostics_runtime_state() -> BResult<DiagnosticsRuntimeState> {
    Ok(runtime()?.state())
}

#[uniffi::export]
pub fn shutdown_diagnostics_runtime() -> BResult<()> {
    runtime()?.shutdown()?;
    Ok(())
}

#[uniffi::export]
pub fn log_diagnostic_event(event: DiagnosticLogEvent) -> BResult<()> {
    runtime()?.log(event);
    Ok(())
}

#[uniffi::export]
pub fn list_log_sessions(offset: i64, limit: i64) -> BResult<DiagnosticLogSessionPage> {
    Ok(runtime()?.list_log_sessions(offset, limit))
}

#[uniffi::export]
pub fn read_log_entries(filter: DiagnosticLogFilter) -> BResult<DiagnosticLogPage> {
    Ok(runtime()?.read_log_entries(&filter))
}

#[uniffi::export]
pub fn read_log_tail(session_id: &str, limit: i64) -> BResult<DiagnosticLogPage> {
    Ok(runtime()?.read_log_tail(session_id, limit))
}

#[uniffi::export]
pub fn clear_log_sessions(session_ids: Vec<String>) -> BResult<i64> {
    Ok(runtime()?.clear_log_sessions(&session_ids)?)
}

#[uniffi::export]
pub fn clear_all_logs() -> BResult<i64> {
    Ok(runtime()?.clear_all_logs()?)
}

#[uniffi::export]
pub fn get_log_retention_policy() -> BResult<DiagnosticLogRetentionPolicy> {
    Ok(runtime()?.retention_policy())
}

#[uniffi::export]
pub fn set_log_retention_policy(
    policy: DiagnosticLogRetentionPolicy,
) -> BResult<DiagnosticLogRetentionPolicy> {
    Ok(runtime()?.set_retention_policy(policy)?)
}

#[uniffi::export]
pub fn enforce_log_retention_policy() -> BResult<i64> {
    Ok(runtime()?.enforce_retention()?)
}

#[uniffi::export]
pub fn get_diagnostic_storage_usage() -> BResult<DiagnosticStorageUsage> {
    Ok(runtime()?.storage_usage())
}

#[uniffi::export]
pub fn flush_diagnostics() -> BResult<()> {
    runtime()?.flush()?;
    Ok(())
}

#[uniffi::export]
pub fn set_diagnostics_music_roots(roots: Vec<String>) -> BResult<()> {
    runtime()?.set_music_roots(roots)?;
    Ok(())
}

#[uniffi::export]
pub fn update_startup_stage(stage: StartupStage) -> BResult<StartupAttempt> {
    Ok(runtime()?.update_startup_stage(stage)?)
}

#[uniffi::export]
pub fn set_diagnostics_startup_mode(
    safe_mode: bool,
    reason: Option<String>,
    disabled_components: Vec<String>,
) -> BResult<StartupAttempt> {
    Ok(runtime()?.set_startup_mode(safe_mode, reason, disabled_components)?)
}

#[uniffi::export]
pub fn get_current_startup_attempt() -> BResult<StartupAttempt> {
    Ok(runtime()?.startup_attempt())
}

#[uniffi::export]
pub fn get_previous_startup_attempt() -> BResult<Option<StartupAttempt>> {
    Ok(runtime()?.previous_startup_attempt())
}

#[uniffi::export]
pub fn list_startup_history(limit: i64) -> BResult<Vec<StartupAttempt>> {
    Ok(runtime()?.startup_history(limit))
}

#[uniffi::export]
pub fn record_diagnostic_incident(draft: IncidentDraft) -> BResult<DiagnosticIncident> {
    Ok(runtime()?.record_incident(draft)?)
}

#[uniffi::export]
pub fn record_fatal_incident(draft: IncidentDraft) -> BResult<Option<DiagnosticIncident>> {
    Ok(runtime()?.record_fatal(draft)?)
}

#[uniffi::export]
pub fn import_platform_exit(exit: PlatformExitRecord) -> BResult<Option<DiagnosticIncident>> {
    Ok(runtime()?.import_platform_exit(exit)?)
}

#[uniffi::export]
pub fn list_diagnostic_incidents(filter: IncidentFilter) -> BResult<IncidentPage> {
    Ok(runtime()?.list_incidents(&filter)?)
}

#[uniffi::export]
pub fn read_incident_artifact(incident_id: &str, artifact_path: &str) -> BResult<String> {
    Ok(runtime()?.read_incident_artifact(incident_id, artifact_path)?)
}

#[uniffi::export]
pub fn set_incident_state(incident_id: &str, state: IncidentState) -> BResult<DiagnosticIncident> {
    Ok(runtime()?.set_incident_state(incident_id, state)?)
}

#[uniffi::export]
pub fn delete_diagnostic_incident(incident_id: &str, allow_unresolved: bool) -> BResult<()> {
    runtime()?.delete_incident(incident_id, allow_unresolved)?;
    Ok(())
}

#[uniffi::export]
pub fn delete_resolved_incidents() -> BResult<i64> {
    Ok(runtime()?.delete_resolved_incidents()?)
}

#[uniffi::export]
pub fn get_pending_recovery() -> BResult<Option<PendingRecovery>> {
    Ok(runtime()?.pending_recovery())
}

#[uniffi::export]
pub fn begin_diagnostics_recovery(disabled_components: Vec<String>) -> BResult<StartupAttempt> {
    Ok(runtime()?.begin_recovery(disabled_components)?)
}

#[uniffi::export]
pub fn mark_recovery_attempted(
    incident_id: &str,
    disabled_components: Vec<String>,
) -> BResult<DiagnosticIncident> {
    Ok(runtime()?.mark_recovery_attempted(incident_id, disabled_components)?)
}

#[uniffi::export]
pub fn complete_diagnostics_recovery(incident_ids: Vec<String>) -> BResult<()> {
    runtime()?.complete_recovery(incident_ids)?;
    Ok(())
}

#[uniffi::export]
pub fn export_diagnostics_bundle(
    request: DiagnosticExportRequest,
) -> BResult<DiagnosticExportResult> {
    Ok(runtime()?.export(&request)?)
}

#[uniffi::export]
pub fn release_diagnostic_export(path: &str) -> BResult<()> {
    runtime()?.release_export(path);
    Ok(())
}

#[uniffi::export]
pub fn clear_diagnostic_exports() -> BResult<i64> {
    Ok(runtime()?.clear_exports()?)
}

#[uniffi::export]
pub fn request_safe_mode_next_start() -> BResult<()> {
    runtime()?.request_safe_mode_next_start()?;
    Ok(())
}

#[uniffi::export]
pub fn diagnostics_debug_fault_injection_supported() -> bool {
    cfg!(debug_assertions)
}

#[uniffi::export]
pub fn debug_trigger_rust_panic() -> BResult<()> {
    if !cfg!(debug_assertions) {
        return Err(BError::CustomError {
            message: "fault injection is disabled in release builds".to_string(),
        });
    }
    panic!("TidePlayer debug Rust panic fault injection");
}

#[uniffi::export]
pub fn debug_mark_startup_incomplete() -> BResult<StartupAttempt> {
    Ok(runtime()?.debug_mark_startup_incomplete()?)
}

pub(crate) fn runtime_if_initialized() -> Option<Arc<DiagnosticsRuntime>> {
    RUNTIME.get().cloned()
}

fn runtime() -> BResult<Arc<DiagnosticsRuntime>> {
    RUNTIME.get().cloned().ok_or_else(|| BError::CustomError {
        message: "diagnostics runtime is not initialized".to_string(),
    })
}

#[cfg(test)]
mod tests {
    use super::ANDROID_TRACING_TAG;

    #[test]
    fn android_tracing_tag_meets_layer_constraints() {
        assert!(!ANDROID_TRACING_TAG.contains('\0'));
        assert!(ANDROID_TRACING_TAG.len() <= 23);
    }
}
