use std::{
    io,
    path::{Path, PathBuf},
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc, Mutex, MutexGuard,
    },
};

use super::{
    crash_writer::write_fatal_incident,
    diagnostics_export::{clear_exports, export_bundle, export_bytes},
    file_ops::{
        atomic_write, create_diagnostics_directories, directory_size, quarantine_corrupt_file,
        sync_directory,
    },
    incidents::{IncidentContext, IncidentStore},
    log_reader,
    log_rotation::enforce_retention,
    logging::LogStore,
    model::{
        DiagnosticExportRequest, DiagnosticExportResult, DiagnosticIncident, DiagnosticLogEvent,
        DiagnosticLogFilter, DiagnosticLogPage, DiagnosticLogRetentionPolicy,
        DiagnosticLogSessionPage, DiagnosticStorageUsage, DiagnosticsRuntimeInit,
        DiagnosticsRuntimeState, IncidentDraft, IncidentFilter, IncidentPage, IncidentSeverity,
        IncidentState, IncidentType, PendingRecovery, PlatformExitRecord, StartupAttempt,
        StartupStage,
    },
    startup_journal::StartupJournal,
};

const USER_SAFE_MODE_MARKER: &str = "user-safe-mode-requested";

pub(crate) struct DiagnosticsRuntime {
    init: DiagnosticsRuntimeInit,
    diagnostics_root: PathBuf,
    log_store: Arc<LogStore>,
    incident_store: IncidentStore,
    startup_journal: StartupJournal,
    operation_lock: Mutex<()>,
    active_export: Mutex<Option<PathBuf>>,
    shutdown: AtomicBool,
}

impl DiagnosticsRuntime {
    pub(crate) fn start(mut init: DiagnosticsRuntimeInit) -> io::Result<Arc<Self>> {
        let diagnostics_root = Path::new(&init.app_document_dir).join("diagnostics");
        create_diagnostics_directories(&diagnostics_root)?;
        let user_safe_mode_marker = diagnostics_root.join("state").join(USER_SAFE_MODE_MARKER);
        if user_safe_mode_marker.exists() {
            init.user_forced_safe_mode = true;
            std::fs::remove_file(&user_safe_mode_marker)?;
            sync_directory(&diagnostics_root.join("state"))?;
        }
        let music_roots_path = diagnostics_root.join("state/redaction-music-roots.json");
        if music_roots_path.exists()
            && super::redaction::load_music_roots(&music_roots_path).is_err()
        {
            let _ =
                quarantine_corrupt_file(&music_roots_path, &diagnostics_root.join("state/corrupt"));
        }
        let incident_store = IncidentStore::new(
            &diagnostics_root,
            Path::new(&init.app_document_dir),
            Path::new(&init.app_cache_dir),
        );
        let pending = incident_store.reconcile_pending_recovery()?;
        let safe_mode = init.user_forced_safe_mode || pending.is_some();
        let safe_mode_reason = if init.user_forced_safe_mode {
            Some("User requested safe mode".to_string())
        } else {
            pending
                .as_ref()
                .map(|pending| format!("{:?}", pending.incident_type))
        };
        let startup_journal =
            StartupJournal::start(&diagnostics_root, safe_mode, safe_mode_reason)?;
        let log_store = LogStore::start(
            &diagnostics_root,
            &init,
            &startup_journal.current().attempt_id,
        )?;
        let runtime = Arc::new(Self {
            init,
            diagnostics_root,
            log_store,
            incident_store,
            startup_journal,
            operation_lock: Mutex::new(()),
            active_export: Mutex::new(None),
            shutdown: AtomicBool::new(false),
        });
        runtime.record_unknown_previous_exit_if_needed()?;
        if std::fs::read_dir(runtime.diagnostics_root.join("state/corrupt"))
            .is_ok_and(|mut entries| entries.next().is_some())
        {
            runtime.log(DiagnosticLogEvent {
                level: super::model::DiagnosticLogLevel::Warn,
                category: super::model::DiagnosticLogCategory::Security,
                target: "DiagnosticsState".to_string(),
                message: "DIAGNOSTICS_STATE_CORRUPT".to_string(),
                detail: Some(
                    "A damaged diagnostics marker was quarantined; conservative defaults are active"
                        .to_string(),
                ),
                correlation_id: None,
                fields: Default::default(),
            });
            runtime.log_store.flush()?;
        }
        runtime
            .startup_journal
            .update_stage(StartupStage::PathsReady)?;
        let _ = enforce_retention(
            &runtime.log_store,
            &runtime.incident_store.protected_log_sessions(),
        );
        Ok(runtime)
    }

    pub(crate) fn state(&self) -> DiagnosticsRuntimeState {
        let pending = self.incident_store.pending_recovery();
        let startup_attempt = self.startup_journal.current();
        DiagnosticsRuntimeState {
            initialized: true,
            diagnostics_root: self.diagnostics_root.to_string_lossy().into_owned(),
            session_id: self.log_store.current_session_id(),
            safe_mode_suggested: startup_attempt.safe_mode,
            safe_mode_reason: startup_attempt.safe_mode_reason.clone(),
            startup_attempt,
            previous_startup_attempt: self.startup_journal.previous(),
            pending_recovery: pending,
        }
    }

    pub(crate) fn log_store(&self) -> &Arc<LogStore> {
        &self.log_store
    }

    pub(crate) fn log(&self, event: DiagnosticLogEvent) {
        let fields_json = serde_json::to_string(&event.fields).unwrap_or_else(|_| "{}".to_string());
        let detail = event.detail.as_deref().unwrap_or("");
        let correlation = event.correlation_id.as_deref().unwrap_or("");
        let level = event.level.as_str();
        let category = event.category.as_str();
        macro_rules! emit {
            ($tracing_level:expr) => {
                tracing::event!(
                    $tracing_level,
                    diagnostic_level = level,
                    diagnostic_category = category,
                    diagnostic_target = event.target.as_str(),
                    diagnostic_detail = detail,
                    diagnostic_correlation_id = correlation,
                    diagnostic_fields_json = fields_json.as_str(),
                    message = event.message.as_str(),
                )
            };
        }
        match event.level {
            super::model::DiagnosticLogLevel::Trace => emit!(tracing::Level::TRACE),
            super::model::DiagnosticLogLevel::Debug => emit!(tracing::Level::DEBUG),
            super::model::DiagnosticLogLevel::Info => emit!(tracing::Level::INFO),
            super::model::DiagnosticLogLevel::Warn => emit!(tracing::Level::WARN),
            super::model::DiagnosticLogLevel::Error | super::model::DiagnosticLogLevel::Fatal => {
                emit!(tracing::Level::ERROR)
            }
        }
    }

    pub(crate) fn update_startup_stage(&self, stage: StartupStage) -> io::Result<StartupAttempt> {
        let attempt = self.startup_journal.update_stage(stage)?;
        self.log(DiagnosticLogEvent {
            level: super::model::DiagnosticLogLevel::Info,
            category: super::model::DiagnosticLogCategory::Startup,
            target: "StartupJournal".to_string(),
            message: format!("Startup stage changed to {stage:?}"),
            detail: None,
            correlation_id: Some(attempt.attempt_id.clone()),
            fields: Default::default(),
        });
        self.log_store.flush()?;
        Ok(attempt)
    }

    pub(crate) fn record_incident(&self, draft: IncidentDraft) -> io::Result<DiagnosticIncident> {
        self.incident_store
            .create(draft, &self.incident_context(), None)
    }

    pub(crate) fn record_fatal(
        &self,
        draft: IncidentDraft,
    ) -> io::Result<Option<DiagnosticIncident>> {
        self.log(DiagnosticLogEvent {
            level: super::model::DiagnosticLogLevel::Fatal,
            category: super::model::DiagnosticLogCategory::Crash,
            target: "FatalWriter".to_string(),
            message: draft.summary.clone(),
            detail: draft.detail.clone(),
            correlation_id: None,
            fields: Default::default(),
        });
        write_fatal_incident(
            &self.incident_store,
            &self.log_store,
            draft,
            &self.incident_context(),
        )
    }

    pub(crate) fn record_rust_panic(&self, message: String, location: String, backtrace: String) {
        let detail = format!(
            "location={location}\nthread={:?}\nbacktrace:\n{backtrace}",
            std::thread::current().name()
        );
        let _ = self.record_fatal(IncidentDraft {
            incident_type: IncidentType::RustPanic,
            severity: IncidentSeverity::Fatal,
            summary: message,
            detail: Some(detail),
            fingerprint_material: Some(format!("{location}\n{backtrace}")),
            requires_recovery: self.startup_journal.current().last_stage.is_before_stable(),
        });
    }

    pub(crate) fn import_platform_exit(
        &self,
        exit: PlatformExitRecord,
    ) -> io::Result<Option<DiagnosticIncident>> {
        self.incident_store
            .import_platform_exit(exit, &self.incident_context())
    }

    pub(crate) fn list_incidents(&self, filter: &IncidentFilter) -> io::Result<IncidentPage> {
        self.incident_store.list(filter)
    }

    pub(crate) fn read_incident_artifact(
        &self,
        incident_id: &str,
        artifact_path: &str,
    ) -> io::Result<String> {
        self.incident_store
            .read_artifact(incident_id, artifact_path)
    }

    pub(crate) fn set_incident_state(
        &self,
        incident_id: &str,
        state: IncidentState,
    ) -> io::Result<DiagnosticIncident> {
        self.incident_store.update_state(incident_id, state)
    }

    pub(crate) fn delete_incident(
        &self,
        incident_id: &str,
        allow_unresolved: bool,
    ) -> io::Result<()> {
        let _guard = self.operation_guard()?;
        self.incident_store.delete(incident_id, allow_unresolved)
    }

    pub(crate) fn mark_recovery_attempted(
        &self,
        incident_id: &str,
        disabled_components: Vec<String>,
    ) -> io::Result<DiagnosticIncident> {
        if !self.startup_journal.current().recovery_attempted {
            self.startup_journal
                .mark_recovery_attempted(disabled_components)?;
        }
        self.incident_store.mark_recovery_attempted(incident_id)
    }

    pub(crate) fn begin_recovery(
        &self,
        disabled_components: Vec<String>,
    ) -> io::Result<StartupAttempt> {
        self.startup_journal
            .mark_recovery_attempted(disabled_components)
    }

    pub(crate) fn complete_recovery(&self, incident_ids: Vec<String>) -> io::Result<()> {
        let current = self.startup_journal.current();
        if !current.stable || current.last_stage.rank() < StartupStage::StartupStable.rank() {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "recovery cannot complete before startup is stable",
            ));
        }
        self.incident_store
            .resolve_and_clear_pending(&incident_ids)?;
        self.startup_journal
            .set_startup_mode(false, None, Vec::new())?;
        Ok(())
    }

    pub(crate) fn set_startup_mode(
        &self,
        safe_mode: bool,
        reason: Option<String>,
        disabled_components: Vec<String>,
    ) -> io::Result<StartupAttempt> {
        self.startup_journal
            .set_startup_mode(safe_mode, reason, disabled_components)
    }

    pub(crate) fn pending_recovery(&self) -> Option<PendingRecovery> {
        self.incident_store.pending_recovery()
    }

    pub(crate) fn list_log_sessions(&self, offset: i64, limit: i64) -> DiagnosticLogSessionPage {
        log_reader::list_sessions(&self.log_store, offset, limit)
    }

    pub(crate) fn read_log_entries(&self, filter: &DiagnosticLogFilter) -> DiagnosticLogPage {
        log_reader::read_entries(&self.log_store, filter)
    }

    pub(crate) fn read_log_tail(&self, session_id: &str, limit: i64) -> DiagnosticLogPage {
        log_reader::read_tail(&self.log_store, session_id, limit)
    }

    pub(crate) fn clear_log_sessions(&self, session_ids: &[String]) -> io::Result<i64> {
        let _guard = self.operation_guard()?;
        log_reader::clear_sessions(
            &self.log_store,
            session_ids,
            &self.incident_store.protected_log_sessions(),
        )
    }

    pub(crate) fn clear_all_logs(&self) -> io::Result<i64> {
        let _guard = self.operation_guard()?;
        log_reader::clear_all(
            &self.log_store,
            &self.incident_store.protected_log_sessions(),
        )
    }

    pub(crate) fn retention_policy(&self) -> DiagnosticLogRetentionPolicy {
        self.log_store.retention_policy()
    }

    pub(crate) fn set_retention_policy(
        &self,
        policy: DiagnosticLogRetentionPolicy,
    ) -> io::Result<DiagnosticLogRetentionPolicy> {
        let policy = self.log_store.set_retention_policy(policy)?;
        let _ = enforce_retention(
            &self.log_store,
            &self.incident_store.protected_log_sessions(),
        );
        Ok(policy)
    }

    pub(crate) fn enforce_retention(&self) -> io::Result<i64> {
        let _guard = self.operation_guard()?;
        enforce_retention(
            &self.log_store,
            &self.incident_store.protected_log_sessions(),
        )
    }

    pub(crate) fn storage_usage(&self) -> DiagnosticStorageUsage {
        let log_bytes = directory_size(&self.diagnostics_root.join("logs"));
        let incident_bytes = directory_size(&self.diagnostics_root.join("incidents"));
        let startup_bytes = directory_size(&self.diagnostics_root.join("startup"));
        let export_bytes = export_bytes(&self.diagnostics_root);
        DiagnosticStorageUsage {
            log_bytes,
            incident_bytes,
            startup_bytes,
            export_bytes,
            total_bytes: log_bytes
                .saturating_add(incident_bytes)
                .saturating_add(startup_bytes)
                .saturating_add(export_bytes),
        }
    }

    pub(crate) fn export(
        &self,
        request: &DiagnosticExportRequest,
    ) -> io::Result<DiagnosticExportResult> {
        let _guard = self.operation_guard()?;
        let result = export_bundle(
            &self.init,
            &self.log_store,
            &self.incident_store,
            &self.startup_journal,
            request,
        )?;
        *self
            .active_export
            .lock()
            .map_err(|_| io::Error::other("active export lock poisoned"))? =
            Some(PathBuf::from(&result.path));
        for incident in self
            .incident_store
            .all_for_export(request.include_resolved_incidents)
            .into_iter()
            .filter(|incident| {
                request.incident_ids.is_empty() || request.incident_ids.contains(&incident.id)
            })
        {
            let _ = self
                .incident_store
                .update_state(&incident.id, IncidentState::Exported);
        }
        Ok(result)
    }

    pub(crate) fn release_export(&self, path: &str) {
        if let Ok(mut active) = self.active_export.lock() {
            if active.as_deref() == Some(Path::new(path)) {
                *active = None;
            }
        }
    }

    pub(crate) fn clear_exports(&self) -> io::Result<i64> {
        let _guard = self.operation_guard()?;
        let active = self
            .active_export
            .lock()
            .map_err(|_| io::Error::other("active export lock poisoned"))?
            .clone();
        Ok(clear_exports(&self.diagnostics_root, active.as_deref()))
    }

    pub(crate) fn delete_resolved_incidents(&self) -> io::Result<i64> {
        let _guard = self.operation_guard()?;
        self.incident_store.delete_resolved()
    }

    pub(crate) fn flush(&self) -> io::Result<()> {
        self.log_store.flush()
    }

    pub(crate) fn set_music_roots(&self, roots: Vec<String>) -> io::Result<()> {
        super::redaction::persist_music_roots(
            &self
                .diagnostics_root
                .join("state/redaction-music-roots.json"),
            roots,
        )
    }

    pub(crate) fn request_safe_mode_next_start(&self) -> io::Result<()> {
        atomic_write(
            &self
                .diagnostics_root
                .join("state")
                .join(USER_SAFE_MODE_MARKER),
            b"requested\n",
        )
    }

    pub(crate) fn debug_mark_startup_incomplete(&self) -> io::Result<StartupAttempt> {
        self.startup_journal.debug_mark_incomplete()
    }

    pub(crate) fn shutdown(&self) -> io::Result<()> {
        if self.shutdown.swap(true, Ordering::AcqRel) {
            return Ok(());
        }
        let _guard = self.operation_guard()?;
        self.update_startup_stage(StartupStage::ShutdownStarted)?;
        self.update_startup_stage(StartupStage::ShutdownComplete)?;
        self.log_store.shutdown()
    }

    pub(crate) fn startup_attempt(&self) -> StartupAttempt {
        self.startup_journal.current()
    }

    pub(crate) fn previous_startup_attempt(&self) -> Option<StartupAttempt> {
        self.startup_journal.previous()
    }

    pub(crate) fn startup_history(&self, limit: i64) -> Vec<StartupAttempt> {
        self.startup_journal.history(limit.clamp(1, 100) as usize)
    }

    fn incident_context(&self) -> IncidentContext {
        let attempt = self.startup_journal.current();
        IncidentContext {
            process_name: self.init.process_name.clone(),
            session_id: self.log_store.current_session_id(),
            startup_attempt_id: attempt.attempt_id,
            startup_stage: attempt.last_stage,
        }
    }

    fn record_unknown_previous_exit_if_needed(&self) -> io::Result<()> {
        let Some(previous) = self.startup_journal.previous() else {
            return Ok(());
        };
        if previous.stable
            || previous.graceful_shutdown
            || self.previous_exit_was_user_requested(&previous)
            || self
                .incident_store
                .has_incident_for_startup_attempt(&previous.attempt_id)
        {
            return Ok(());
        }
        let context = IncidentContext {
            process_name: self.init.process_name.clone(),
            session_id: self.log_store.current_session_id(),
            startup_attempt_id: previous.attempt_id.clone(),
            startup_stage: previous.last_stage,
        };
        self.incident_store.create(
            IncidentDraft {
                incident_type: IncidentType::UnknownAbnormalExit,
                severity: IncidentSeverity::Warning,
                summary: "Previous startup attempt did not complete".to_string(),
                detail: Some(format!(
                    "lastStage={:?}, stable={}, gracefulShutdown={}",
                    previous.last_stage, previous.stable, previous.graceful_shutdown
                )),
                fingerprint_material: Some(format!(
                    "UNKNOWN_ABNORMAL_EXIT|{:?}",
                    previous.last_stage
                )),
                requires_recovery: false,
            },
            &context,
            None,
        )?;
        Ok(())
    }

    fn previous_exit_was_user_requested(&self, previous: &StartupAttempt) -> bool {
        self.init
            .last_user_requested_exit_at_epoch_ms
            .is_some_and(|timestamp| {
                timestamp >= previous.last_updated_at_epoch_ms
                    && timestamp <= self.startup_journal.current().started_at_epoch_ms
            })
    }

    fn operation_guard(&self) -> io::Result<MutexGuard<'_, ()>> {
        self.operation_lock
            .lock()
            .map_err(|_| io::Error::other("diagnostics operation lock poisoned"))
    }
}

#[cfg(test)]
mod tests {
    use std::fs;

    use super::*;
    use crate::infra::file_ops::temporary_test_directory;

    #[test]
    fn requested_safe_mode_is_consumed_and_can_retry_without_an_incident() {
        let root = temporary_test_directory("user-safe-mode");
        let init = DiagnosticsRuntimeInit {
            app_document_dir: root.to_string_lossy().into_owned(),
            app_cache_dir: root.join("cache").to_string_lossy().into_owned(),
            platform: "test".into(),
            app_version: "1".into(),
            build_info: "debug".into(),
            git_commit_sha: "abc".into(),
            process_name: "test".into(),
            user_forced_safe_mode: false,
            last_user_requested_exit_at_epoch_ms: None,
        };
        let first = DiagnosticsRuntime::start(init.clone()).unwrap();
        first.request_safe_mode_next_start().unwrap();
        drop(first);

        let second = DiagnosticsRuntime::start(init).unwrap();
        assert!(second.state().startup_attempt.safe_mode);
        assert_eq!(
            second.state().startup_attempt.safe_mode_reason.as_deref(),
            Some("User requested safe mode"),
        );
        assert!(!second
            .diagnostics_root
            .join("state")
            .join(USER_SAFE_MODE_MARKER)
            .exists());
        let retry = second.begin_recovery(Vec::new()).unwrap();
        assert!(retry.recovery_attempted);
        assert_eq!(retry.last_stage, StartupStage::PlatformExitsCollected);
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn repeated_user_requested_exits_do_not_create_unknown_exit_incidents() {
        let root = temporary_test_directory("user-requested-exits");
        let mut init = DiagnosticsRuntimeInit {
            app_document_dir: root.to_string_lossy().into_owned(),
            app_cache_dir: root.join("cache").to_string_lossy().into_owned(),
            platform: "android".into(),
            app_version: "1".into(),
            build_info: "debug".into(),
            git_commit_sha: "abc".into(),
            process_name: "test".into(),
            user_forced_safe_mode: false,
            last_user_requested_exit_at_epoch_ms: None,
        };

        let first = DiagnosticsRuntime::start(init.clone()).unwrap();
        first
            .update_startup_stage(StartupStage::FirstFrameRendered)
            .unwrap();
        init.last_user_requested_exit_at_epoch_ms =
            Some(first.state().startup_attempt.last_updated_at_epoch_ms);
        drop(first);

        let second = DiagnosticsRuntime::start(init.clone()).unwrap();
        second
            .update_startup_stage(StartupStage::FirstFrameRendered)
            .unwrap();
        init.last_user_requested_exit_at_epoch_ms =
            Some(second.state().startup_attempt.last_updated_at_epoch_ms);
        drop(second);

        let third = DiagnosticsRuntime::start(init).unwrap();
        assert!(third
            .incident_store
            .list(&IncidentFilter::default())
            .unwrap()
            .incidents
            .is_empty());
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn stale_user_requested_exit_does_not_hide_a_later_unknown_exit() {
        let root = temporary_test_directory("stale-user-requested-exit");
        let mut init = DiagnosticsRuntimeInit {
            app_document_dir: root.to_string_lossy().into_owned(),
            app_cache_dir: root.join("cache").to_string_lossy().into_owned(),
            platform: "android".into(),
            app_version: "1".into(),
            build_info: "debug".into(),
            git_commit_sha: "abc".into(),
            process_name: "test".into(),
            user_forced_safe_mode: false,
            last_user_requested_exit_at_epoch_ms: None,
        };

        let first = DiagnosticsRuntime::start(init.clone()).unwrap();
        first
            .update_startup_stage(StartupStage::FirstFrameRendered)
            .unwrap();
        init.last_user_requested_exit_at_epoch_ms =
            Some(first.state().startup_attempt.started_at_epoch_ms - 1);
        drop(first);

        let second = DiagnosticsRuntime::start(init).unwrap();
        let incidents = second
            .incident_store
            .list(&IncidentFilter::default())
            .unwrap()
            .incidents;
        assert_eq!(incidents.len(), 1);
        assert_eq!(
            incidents[0].incident_type,
            IncidentType::UnknownAbnormalExit
        );
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn stable_startup_is_not_reclassified_when_shutdown_was_not_yet_recorded() {
        let root = temporary_test_directory("stable-startup");
        let init = DiagnosticsRuntimeInit {
            app_document_dir: root.to_string_lossy().into_owned(),
            app_cache_dir: root.join("cache").to_string_lossy().into_owned(),
            platform: "test".into(),
            app_version: "1".into(),
            build_info: "debug".into(),
            git_commit_sha: "abc".into(),
            process_name: "test".into(),
            user_forced_safe_mode: false,
            last_user_requested_exit_at_epoch_ms: None,
        };
        let first = DiagnosticsRuntime::start(init.clone()).unwrap();
        first
            .update_startup_stage(StartupStage::FirstFrameRendered)
            .unwrap();
        first
            .update_startup_stage(StartupStage::StartupStable)
            .unwrap();
        assert!(!first.state().startup_attempt.graceful_shutdown);
        drop(first);

        let second = DiagnosticsRuntime::start(init).unwrap();
        assert!(second
            .incident_store
            .list(&IncidentFilter::default())
            .unwrap()
            .incidents
            .is_empty());
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn exporting_non_recovery_incident_consumes_attention_and_keeps_history() {
        let root = temporary_test_directory("exported-history");
        let runtime = DiagnosticsRuntime::start(DiagnosticsRuntimeInit {
            app_document_dir: root.to_string_lossy().into_owned(),
            app_cache_dir: root.join("cache").to_string_lossy().into_owned(),
            platform: "test".into(),
            app_version: "1".into(),
            build_info: "debug".into(),
            git_commit_sha: "abc".into(),
            process_name: "test".into(),
            user_forced_safe_mode: false,
            last_user_requested_exit_at_epoch_ms: None,
        })
        .unwrap();
        let incident = runtime
            .record_incident(IncidentDraft {
                incident_type: IncidentType::UnknownAbnormalExit,
                severity: IncidentSeverity::Warning,
                summary: "Previous startup attempt did not complete".into(),
                detail: None,
                fingerprint_material: Some("UNKNOWN_ABNORMAL_EXIT|FIRST_FRAME_RENDERED".into()),
                requires_recovery: false,
            })
            .unwrap();

        runtime
            .export(&DiagnosticExportRequest {
                summary: "test".into(),
                environment_json: "{}".into(),
                playback_summary_json: "{}".into(),
                scan_summary_json: "{}".into(),
                plugin_summary_json: "{}".into(),
                source_summary_json: "{}".into(),
                storage_summary_json: "{}".into(),
                include_resolved_incidents: true,
                incident_ids: vec![incident.id.clone()],
            })
            .unwrap();

        let retained = runtime
            .list_incidents(&IncidentFilter::default())
            .unwrap()
            .incidents;
        assert_eq!(retained.len(), 1);
        assert_eq!(retained[0].state, IncidentState::Exported);
        assert!(!retained[0].requires_recovery);
        assert!(runtime.pending_recovery().is_none());
        fs::remove_dir_all(root).unwrap();
    }
}
