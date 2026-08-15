use std::{
    collections::HashSet,
    fs, io,
    path::{Path, PathBuf},
    sync::{Mutex, MutexGuard},
};

use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};

use super::{
    file_ops::{
        atomic_write, atomic_write_json, copy_with_limit, new_id, now_epoch_ms,
        quarantine_corrupt_file, read_json, sync_directory,
    },
    model::{
        DiagnosticIncident, IncidentDraft, IncidentFilter, IncidentPage, IncidentState,
        PendingRecovery, PlatformExitRecord, StartupStage, DIAGNOSTICS_SCHEMA_VERSION,
        MAX_LOG_PAGE_SIZE,
    },
    redaction::{redact_text, stable_fingerprint_material},
};

const INCIDENT_FILE_NAME: &str = "incident.json";
const PENDING_RECOVERY_FILE_NAME: &str = "pending-recovery.json";
const PROCESSED_EXITS_FILE_NAME: &str = "processed-platform-exits.json";
const MAX_ARTIFACT_READ_BYTES: u64 = 2 * 1024 * 1024;

#[derive(Debug, Clone)]
pub(crate) struct IncidentContext {
    pub process_name: String,
    pub session_id: String,
    pub startup_attempt_id: String,
    pub startup_stage: StartupStage,
}

pub(crate) struct IncidentStore {
    incidents_dir: PathBuf,
    state_dir: PathBuf,
    corrupt_dir: PathBuf,
    app_document_dir: PathBuf,
    app_cache_dir: PathBuf,
    lock: Mutex<()>,
}

impl IncidentStore {
    pub(crate) fn new(
        diagnostics_root: &Path,
        app_document_dir: &Path,
        app_cache_dir: &Path,
    ) -> Self {
        Self {
            incidents_dir: diagnostics_root.join("incidents"),
            state_dir: diagnostics_root.join("state"),
            corrupt_dir: diagnostics_root.join("state/corrupt"),
            app_document_dir: app_document_dir.to_path_buf(),
            app_cache_dir: app_cache_dir.to_path_buf(),
            lock: Mutex::new(()),
        }
    }

    pub(crate) fn create(
        &self,
        draft: IncidentDraft,
        context: &IncidentContext,
        artifact: Option<(&str, &[u8])>,
    ) -> io::Result<DiagnosticIncident> {
        let _guard = self.guard()?;
        self.create_locked(draft, context, artifact)
    }

    pub(crate) fn import_platform_exit(
        &self,
        exit: PlatformExitRecord,
        context: &IncidentContext,
    ) -> io::Result<Option<DiagnosticIncident>> {
        let _guard = self.guard()?;
        let mut processed = self.read_processed_exits_locked();
        if processed.contains(&exit.exit_key) {
            return Ok(None);
        }

        let mut bounded_trace = Vec::new();
        let trace_was_truncated = if let Some(trace) = exit.trace.as_deref() {
            copy_with_limit(trace, &mut bounded_trace, 1024 * 1024)?.1
        } else {
            false
        };
        let detail = format!(
            "exitTimestamp={}\nprocessName={}\npid={}\nreason={}\nstatus={}\nimportance={}\npssKb={}\nrssKb={}\ndescription={}\ntraceTruncated={}\n{}",
            exit.timestamp_epoch_ms,
            exit.process_name,
            exit.pid,
            exit.reason,
            exit.status,
            exit.importance,
            exit.pss_kb,
            exit.rss_kb,
            exit.description.as_deref().unwrap_or("none"),
            exit.trace_truncated || trace_was_truncated,
            exit.environment_summary,
        );
        let summary = match exit.incident_type {
            super::model::IncidentType::AndroidAnr => "Android system ANR",
            super::model::IncidentType::NativeCrash => "Android native crash",
            super::model::IncidentType::OutOfMemory => "Android low-memory exit",
            _ => "Android abnormal process exit",
        };
        let draft = IncidentDraft {
            incident_type: exit.incident_type,
            severity: exit.severity,
            summary: summary.to_string(),
            detail: Some(detail),
            fingerprint_material: Some(format!(
                "{:?}|{}|{}|{}|{}",
                exit.incident_type,
                exit.reason,
                exit.status,
                exit.process_name,
                exit.description.as_deref().unwrap_or("")
            )),
            requires_recovery: exit.requires_recovery,
        };
        let artifact_name = match exit.incident_type {
            super::model::IncidentType::AndroidAnr => "android-anr.txt",
            super::model::IncidentType::NativeCrash => "native-exit.txt",
            _ => "platform-exit.txt",
        };
        let artifact =
            (!bounded_trace.is_empty()).then_some((artifact_name, bounded_trace.as_slice()));
        let exit_context = IncidentContext {
            process_name: exit.process_name.clone(),
            session_id: context.session_id.clone(),
            startup_attempt_id: exit
                .startup_attempt_id
                .clone()
                .unwrap_or_else(|| context.startup_attempt_id.clone()),
            startup_stage: exit.startup_stage.unwrap_or(context.startup_stage),
        };
        let platform_exit_json = serde_json::to_vec_pretty(&serde_json::json!({
            "schemaVersion": DIAGNOSTICS_SCHEMA_VERSION,
            "exitKey": &exit.exit_key,
            "incidentType": exit.incident_type,
            "timestampEpochMs": exit.timestamp_epoch_ms,
            "processName": &exit.process_name,
            "pid": exit.pid,
            "reason": exit.reason,
            "status": exit.status,
            "importance": exit.importance,
            "pssKb": exit.pss_kb,
            "rssKb": exit.rss_kb,
            "description": &exit.description,
            "traceTruncated": exit.trace_truncated || trace_was_truncated,
            "environmentSummary": &exit.environment_summary,
            "startupAttemptId": &exit.startup_attempt_id,
            "startupStage": exit.startup_stage,
        }))
        .map_err(io::Error::other)?;
        let related_fatal = (exit.incident_type == super::model::IncidentType::NativeCrash)
            .then(|| {
                self.read_all_locked().into_iter().find(|incident| {
                    matches!(
                        incident.incident_type,
                        super::model::IncidentType::KotlinUncaught
                            | super::model::IncidentType::RustPanic
                            | super::model::IncidentType::NativeCrash
                    ) && incident
                        .last_seen_at_epoch_ms
                        .abs_diff(exit.timestamp_epoch_ms)
                        <= 15_000
                })
            })
            .flatten();
        let mut incident = if let Some(mut existing) = related_fatal {
            if let Some((name, bytes)) = artifact {
                self.append_artifact_locked(&mut existing, name, bytes)?;
            }
            existing
        } else {
            self.create_locked(draft, &exit_context, artifact)?
        };
        self.append_artifact_locked(&mut incident, "platform-exit.json", &platform_exit_json)?;
        processed.insert(exit.exit_key);
        atomic_write_json(
            &self.state_dir.join(PROCESSED_EXITS_FILE_NAME),
            &ProcessedPlatformExits {
                schema_version: DIAGNOSTICS_SCHEMA_VERSION,
                keys: processed.into_iter().collect(),
            },
        )?;
        Ok(Some(incident))
    }

    pub(crate) fn list(&self, filter: &IncidentFilter) -> io::Result<IncidentPage> {
        let _guard = self.guard()?;
        let mut incidents = self.read_all_locked();
        incidents.retain(|incident| {
            (filter.types.is_empty() || filter.types.contains(&incident.incident_type))
                && (filter.severities.is_empty() || filter.severities.contains(&incident.severity))
                && (filter.states.is_empty() || filter.states.contains(&incident.state))
                && filter
                    .requires_recovery
                    .is_none_or(|required| incident.requires_recovery == required)
        });
        incidents.sort_by_key(|incident| std::cmp::Reverse(incident.last_seen_at_epoch_ms));
        let total = incidents.len() as i64;
        let offset = filter.offset.max(0) as usize;
        let limit = filter.limit.clamp(1, MAX_LOG_PAGE_SIZE) as usize;
        let page = incidents
            .into_iter()
            .skip(offset)
            .take(limit)
            .collect::<Vec<_>>();
        Ok(IncidentPage {
            has_more: offset.saturating_add(page.len()) < total as usize,
            incidents: page,
            offset: offset as i64,
            limit: limit as i64,
            total_matched: total,
        })
    }

    pub(crate) fn update_state(
        &self,
        incident_id: &str,
        state: IncidentState,
    ) -> io::Result<DiagnosticIncident> {
        let _guard = self.guard()?;
        let mut incident = self
            .read_incident_locked(incident_id)?
            .ok_or_else(|| io::Error::new(io::ErrorKind::NotFound, "incident not found"))?;
        incident.state = state;
        if matches!(state, IncidentState::Resolved | IncidentState::Ignored) {
            incident.requires_recovery = false;
        }
        self.write_incident_locked(&incident)?;
        if !incident.requires_recovery
            || matches!(
                incident.state,
                IncidentState::Resolved | IncidentState::Ignored
            )
        {
            self.clear_pending_recovery_locked(Some(incident_id))?;
        }
        Ok(incident)
    }

    pub(crate) fn mark_recovery_attempted(
        &self,
        incident_id: &str,
    ) -> io::Result<DiagnosticIncident> {
        let _guard = self.guard()?;
        let mut incident = self
            .read_incident_locked(incident_id)?
            .ok_or_else(|| io::Error::new(io::ErrorKind::NotFound, "incident not found"))?;
        incident.state = IncidentState::RecoveryAttempted;
        self.write_incident_locked(&incident)?;
        if let Some(mut pending) = self.pending_recovery_locked() {
            if pending.incident_id == incident_id {
                pending.failed_recovery_attempts =
                    pending.failed_recovery_attempts.saturating_add(1);
                pending.updated_at_epoch_ms = now_epoch_ms();
                atomic_write_json(&self.state_dir.join(PENDING_RECOVERY_FILE_NAME), &pending)?;
            }
        }
        Ok(incident)
    }

    pub(crate) fn resolve_and_clear_pending(&self, incident_ids: &[String]) -> io::Result<()> {
        let _guard = self.guard()?;
        for incident_id in incident_ids {
            if let Some(mut incident) = self.read_incident_locked(incident_id)? {
                incident.state = IncidentState::Resolved;
                incident.requires_recovery = false;
                self.write_incident_locked(&incident)?;
            }
        }
        let pending_path = self.state_dir.join(PENDING_RECOVERY_FILE_NAME);
        if pending_path.exists() {
            fs::remove_file(pending_path)?;
            sync_directory(&self.state_dir)?;
        }
        Ok(())
    }

    pub(crate) fn delete(&self, incident_id: &str, allow_unresolved: bool) -> io::Result<()> {
        let _guard = self.guard()?;
        let incident = self
            .read_incident_locked(incident_id)?
            .ok_or_else(|| io::Error::new(io::ErrorKind::NotFound, "incident not found"))?;
        if !allow_unresolved
            && !matches!(
                incident.state,
                IncidentState::Resolved | IncidentState::Ignored
            )
        {
            return Err(io::Error::new(
                io::ErrorKind::PermissionDenied,
                "unresolved incident requires explicit confirmation",
            ));
        }
        if self
            .pending_recovery_locked()
            .is_some_and(|pending| pending.incident_id == incident_id)
        {
            return Err(io::Error::new(
                io::ErrorKind::PermissionDenied,
                "pending recovery incident cannot be deleted",
            ));
        }
        fs::remove_dir_all(self.incident_directory(incident_id))
    }

    pub(crate) fn delete_resolved(&self) -> io::Result<i64> {
        let _guard = self.guard()?;
        let pending_id = self
            .pending_recovery_locked()
            .map(|pending| pending.incident_id);
        let mut removed = 0_i64;
        for incident in self.read_all_locked() {
            if matches!(
                incident.state,
                IncidentState::Resolved | IncidentState::Ignored
            ) && pending_id.as_deref() != Some(&incident.id)
                && fs::remove_dir_all(self.incident_directory(&incident.id)).is_ok()
            {
                removed += 1;
            }
        }
        Ok(removed)
    }

    pub(crate) fn read_artifact(
        &self,
        incident_id: &str,
        artifact_path: &str,
    ) -> io::Result<String> {
        let _guard = self.guard()?;
        let incident_dir = self.incident_directory(incident_id);
        let requested = Path::new(artifact_path);
        let file_name = requested
            .file_name()
            .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidInput, "invalid artifact path"))?;
        let path = incident_dir.join(file_name);
        let bytes = fs::read(path)?;
        let bounded = &bytes[..bytes.len().min(MAX_ARTIFACT_READ_BYTES as usize)];
        Ok(String::from_utf8_lossy(bounded).into_owned())
    }

    pub(crate) fn pending_recovery(&self) -> Option<PendingRecovery> {
        let _guard = self.guard().ok()?;
        self.pending_recovery_locked()
    }

    pub(crate) fn reconcile_pending_recovery(&self) -> io::Result<Option<PendingRecovery>> {
        let _guard = self.guard()?;
        let Some(pending) = self.pending_recovery_locked() else {
            return Ok(None);
        };
        let remains_actionable = self
            .read_incident_locked(&pending.incident_id)?
            .is_some_and(|incident| {
                incident.requires_recovery
                    && !matches!(
                        incident.state,
                        IncidentState::Resolved | IncidentState::Ignored
                    )
            });
        if remains_actionable {
            Ok(Some(pending))
        } else {
            self.clear_pending_recovery_locked(Some(&pending.incident_id))?;
            Ok(None)
        }
    }

    pub(crate) fn protected_log_sessions(&self) -> HashSet<String> {
        let Ok(_guard) = self.guard() else {
            return HashSet::new();
        };
        self.read_all_locked()
            .into_iter()
            .filter(|incident| {
                incident.requires_recovery
                    || !matches!(
                        incident.state,
                        IncidentState::Resolved | IncidentState::Ignored
                    )
            })
            .flat_map(|incident| incident.related_log_session_ids)
            .collect()
    }

    pub(crate) fn has_incident_for_startup_attempt(&self, attempt_id: &str) -> bool {
        let Ok(_guard) = self.guard() else {
            return false;
        };
        self.read_all_locked()
            .iter()
            .any(|incident| incident.startup_attempt_id.as_deref() == Some(attempt_id))
    }

    pub(crate) fn all_for_export(&self, include_resolved: bool) -> Vec<DiagnosticIncident> {
        let Ok(_guard) = self.guard() else {
            return Vec::new();
        };
        self.read_all_locked()
            .into_iter()
            .filter(|incident| {
                include_resolved
                    || !matches!(
                        incident.state,
                        IncidentState::Resolved | IncidentState::Ignored
                    )
            })
            .collect()
    }

    fn create_locked(
        &self,
        draft: IncidentDraft,
        context: &IncidentContext,
        artifact: Option<(&str, &[u8])>,
    ) -> io::Result<DiagnosticIncident> {
        let now = now_epoch_ms();
        let fingerprint = calculate_fingerprint(&draft, context.startup_stage);
        let existing = self
            .read_all_locked()
            .into_iter()
            .filter(|incident| incident.fingerprint.as_deref() == Some(&fingerprint))
            .max_by_key(|incident| incident.last_seen_at_epoch_ms);

        let mut incident = if let Some(mut incident) = existing {
            incident.last_seen_at_epoch_ms = now;
            incident.occurrence_count = incident.occurrence_count.saturating_add(1);
            incident.occurrence_timestamps_epoch_ms.push(now);
            if incident.occurrence_timestamps_epoch_ms.len() > 100 {
                let excess = incident.occurrence_timestamps_epoch_ms.len() - 100;
                incident.occurrence_timestamps_epoch_ms.drain(..excess);
            }
            incident.state = IncidentState::PendingReview;
            if severity_rank(draft.severity) > severity_rank(incident.severity) {
                incident.severity = draft.severity;
            }
            incident.process_name = Some(context.process_name.clone());
            incident.session_id = Some(context.session_id.clone());
            incident.startup_attempt_id = Some(context.startup_attempt_id.clone());
            incident.startup_stage = Some(context.startup_stage);
            incident.summary = self.redact(&draft.summary);
            incident.detail = draft.detail.as_deref().map(|detail| self.redact(detail));
            incident.requires_recovery |= draft.requires_recovery;
            incident
        } else {
            DiagnosticIncident {
                schema_version: DIAGNOSTICS_SCHEMA_VERSION,
                id: new_id("incident"),
                incident_type: draft.incident_type,
                severity: draft.severity,
                state: IncidentState::PendingReview,
                detected_at_epoch_ms: now,
                last_seen_at_epoch_ms: now,
                process_name: Some(context.process_name.clone()),
                session_id: Some(context.session_id.clone()),
                startup_attempt_id: Some(context.startup_attempt_id.clone()),
                startup_stage: Some(context.startup_stage),
                fingerprint: Some(fingerprint.clone()),
                summary: self.redact(&draft.summary),
                detail: draft.detail.as_deref().map(|detail| self.redact(detail)),
                artifact_paths: Vec::new(),
                related_log_session_ids: vec![context.session_id.clone()],
                occurrence_count: 1,
                occurrence_timestamps_epoch_ms: vec![now],
                requires_recovery: draft.requires_recovery,
            }
        };

        if !incident
            .related_log_session_ids
            .contains(&context.session_id)
        {
            incident
                .related_log_session_ids
                .push(context.session_id.clone());
        }
        if let Some((artifact_name, bytes)) = artifact {
            let safe_name = sanitize_artifact_name(artifact_name);
            let path = self.incident_directory(&incident.id).join(&safe_name);
            let sanitized = self.redact(&String::from_utf8_lossy(bytes));
            atomic_write(&path, sanitized.as_bytes())?;
            if !incident.artifact_paths.contains(&safe_name) {
                incident.artifact_paths.push(safe_name);
            }
        }
        self.write_incident_locked(&incident)?;

        if incident.requires_recovery {
            let pending_path = self.state_dir.join(PENDING_RECOVERY_FILE_NAME);
            let failed_recovery_attempts = self
                .pending_recovery_locked()
                .filter(|pending| pending.fingerprint.as_deref() == Some(&fingerprint))
                .map_or(0, |pending| pending.failed_recovery_attempts);
            atomic_write_json(
                &pending_path,
                &PendingRecovery {
                    schema_version: DIAGNOSTICS_SCHEMA_VERSION,
                    incident_id: incident.id.clone(),
                    fingerprint: incident.fingerprint.clone(),
                    incident_type: incident.incident_type,
                    startup_attempt_id: incident.startup_attempt_id.clone(),
                    startup_stage: incident.startup_stage,
                    occurrence_count: incident.occurrence_count,
                    failed_recovery_attempts,
                    created_at_epoch_ms: now,
                    updated_at_epoch_ms: now,
                },
            )?;
        }
        Ok(incident)
    }

    fn read_all_locked(&self) -> Vec<DiagnosticIncident> {
        let Ok(entries) = fs::read_dir(&self.incidents_dir) else {
            return Vec::new();
        };
        entries
            .flatten()
            .filter(|entry| entry.path().is_dir())
            .filter_map(|entry| read_json(&entry.path().join(INCIDENT_FILE_NAME)).ok())
            .collect()
    }

    fn read_incident_locked(&self, incident_id: &str) -> io::Result<Option<DiagnosticIncident>> {
        let path = self
            .incident_directory(incident_id)
            .join(INCIDENT_FILE_NAME);
        if !path.exists() {
            return Ok(None);
        }
        read_json(&path).map(Some)
    }

    fn write_incident_locked(&self, incident: &DiagnosticIncident) -> io::Result<()> {
        atomic_write_json(
            &self
                .incident_directory(&incident.id)
                .join(INCIDENT_FILE_NAME),
            incident,
        )
    }

    fn append_artifact_locked(
        &self,
        incident: &mut DiagnosticIncident,
        artifact_name: &str,
        bytes: &[u8],
    ) -> io::Result<()> {
        let safe_name = sanitize_artifact_name(artifact_name);
        let path = self.incident_directory(&incident.id).join(&safe_name);
        let sanitized = self.redact(&String::from_utf8_lossy(bytes));
        atomic_write(&path, sanitized.as_bytes())?;
        if !incident.artifact_paths.contains(&safe_name) {
            incident.artifact_paths.push(safe_name);
        }
        self.write_incident_locked(incident)
    }

    fn pending_recovery_locked(&self) -> Option<PendingRecovery> {
        let path = self.state_dir.join(PENDING_RECOVERY_FILE_NAME);
        if !path.exists() {
            return None;
        }
        match read_json(&path) {
            Ok(pending) => Some(pending),
            Err(_) => {
                let _ = quarantine_corrupt_file(&path, &self.corrupt_dir);
                None
            }
        }
    }

    fn clear_pending_recovery_locked(&self, incident_id: Option<&str>) -> io::Result<()> {
        let path = self.state_dir.join(PENDING_RECOVERY_FILE_NAME);
        if !path.exists() {
            return Ok(());
        }
        if incident_id.is_some_and(|id| {
            self.pending_recovery_locked()
                .is_some_and(|pending| pending.incident_id != id)
        }) {
            return Ok(());
        }
        fs::remove_file(path)?;
        sync_directory(&self.state_dir)
    }

    fn read_processed_exits_locked(&self) -> HashSet<String> {
        let path = self.state_dir.join(PROCESSED_EXITS_FILE_NAME);
        let state: ProcessedPlatformExits = match read_json(&path) {
            Ok(state) => state,
            Err(error) if error.kind() == io::ErrorKind::NotFound => return HashSet::new(),
            Err(_) => {
                let _ = quarantine_corrupt_file(&path, &self.corrupt_dir);
                return HashSet::new();
            }
        };
        state.keys.into_iter().collect()
    }

    fn incident_directory(&self, incident_id: &str) -> PathBuf {
        let safe_id = incident_id
            .chars()
            .filter(|character| character.is_ascii_alphanumeric() || matches!(character, '-' | '_'))
            .collect::<String>();
        self.incidents_dir.join(safe_id)
    }

    fn redact(&self, value: &str) -> String {
        redact_text(
            value,
            Some(&self.app_document_dir),
            Some(&self.app_cache_dir),
        )
    }

    fn guard(&self) -> io::Result<MutexGuard<'_, ()>> {
        self.lock
            .lock()
            .map_err(|_| io::Error::other("incident store lock poisoned"))
    }
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ProcessedPlatformExits {
    schema_version: i32,
    keys: Vec<String>,
}

fn calculate_fingerprint(draft: &IncidentDraft, startup_stage: StartupStage) -> String {
    let material = draft
        .fingerprint_material
        .as_deref()
        .or(draft.detail.as_deref())
        .unwrap_or(&draft.summary);
    let stable = stable_fingerprint_material(material);
    let digest = Sha256::digest(format!(
        "{:?}|{:?}|{}",
        draft.incident_type, startup_stage, stable
    ));
    format!("{digest:x}")[..24].to_string()
}

fn severity_rank(severity: super::model::IncidentSeverity) -> i32 {
    match severity {
        super::model::IncidentSeverity::Info => 0,
        super::model::IncidentSeverity::Warning => 1,
        super::model::IncidentSeverity::Error => 2,
        super::model::IncidentSeverity::Fatal => 3,
    }
}

fn sanitize_artifact_name(name: &str) -> String {
    let safe = Path::new(name)
        .file_name()
        .and_then(|name| name.to_str())
        .unwrap_or("artifact.txt")
        .chars()
        .filter(|character| {
            character.is_ascii_alphanumeric() || matches!(character, '-' | '_' | '.')
        })
        .collect::<String>();
    if safe.is_empty() {
        "artifact.txt".to_string()
    } else {
        safe
    }
}

#[cfg(test)]
mod tests {
    use std::fs;

    use super::*;
    use crate::infra::{
        file_ops::temporary_test_directory,
        model::{IncidentSeverity, IncidentType},
    };

    fn store() -> (PathBuf, IncidentStore) {
        let root = temporary_test_directory("incident-store");
        let diagnostics = root.join("diagnostics");
        fs::create_dir_all(diagnostics.join("incidents")).unwrap();
        fs::create_dir_all(diagnostics.join("state/corrupt")).unwrap();
        (
            root.clone(),
            IncidentStore::new(&diagnostics, &root, &root.join("cache")),
        )
    }

    fn context() -> IncidentContext {
        IncidentContext {
            process_name: "test".into(),
            session_id: "session".into(),
            startup_attempt_id: "attempt".into(),
            startup_stage: StartupStage::BackendCreating,
        }
    }

    fn draft(secret: &str) -> IncidentDraft {
        IncidentDraft {
            incident_type: IncidentType::RustPanic,
            severity: IncidentSeverity::Fatal,
            summary: "panic".into(),
            detail: Some(format!("token={secret} at 0x1234")),
            fingerprint_material: None,
            requires_recovery: true,
        }
    }

    #[test]
    fn creates_incident_and_atomic_pending_marker() {
        let (root, store) = store();
        let incident = store
            .create(
                draft("secret"),
                &context(),
                Some(("rust-panic.txt", b"secret=bad")),
            )
            .unwrap();
        assert_eq!(incident.occurrence_count, 1);
        assert!(store.pending_recovery().is_some());
        let artifact = store.read_artifact(&incident.id, "rust-panic.txt").unwrap();
        assert!(!artifact.contains("bad"));
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn deduplicates_stable_fingerprint_and_increments_occurrences() {
        let (root, store) = store();
        let first = store.create(draft("one"), &context(), None).unwrap();
        let mut second_draft = draft("two");
        second_draft.detail = Some("token=two at 0xabcd".into());
        let second = store.create(second_draft, &context(), None).unwrap();
        assert_eq!(first.id, second.id);
        assert_eq!(second.occurrence_count, 2);
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn platform_exit_key_is_imported_once() {
        let (root, store) = store();
        let exit = PlatformExitRecord {
            exit_key: "1:2:3".into(),
            incident_type: IncidentType::AndroidAnr,
            severity: IncidentSeverity::Error,
            timestamp_epoch_ms: 1,
            process_name: "app".into(),
            pid: 2,
            reason: 6,
            status: 0,
            importance: 100,
            pss_kb: 1,
            rss_kb: 2,
            description: None,
            trace: Some(b"trace".to_vec()),
            trace_truncated: false,
            requires_recovery: false,
            environment_summary: "Android".into(),
            startup_attempt_id: Some("previous-attempt".into()),
            startup_stage: Some(StartupStage::StartupStable),
        };
        let incident = store
            .import_platform_exit(exit.clone(), &context())
            .unwrap()
            .unwrap();
        assert!(incident
            .artifact_paths
            .contains(&"platform-exit.json".to_string()));
        assert!(store
            .import_platform_exit(exit, &context())
            .unwrap()
            .is_none());
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn legacy_exported_non_recovery_incident_clears_stale_pending_marker() {
        let (root, store) = store();
        let mut incident = store.create(draft("secret"), &context(), None).unwrap();
        incident.state = IncidentState::Exported;
        incident.requires_recovery = false;
        store.write_incident_locked(&incident).unwrap();
        assert!(store.pending_recovery().is_some());

        assert!(store.reconcile_pending_recovery().unwrap().is_none());
        assert!(store.pending_recovery().is_none());
        let retained = store.list(&IncidentFilter::default()).unwrap().incidents;
        assert_eq!(retained.len(), 1);
        assert_eq!(retained[0].state, IncidentState::Exported);
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn acknowledged_incident_is_persisted_and_reopened_only_after_a_new_occurrence() {
        let (root, store) = store();
        let first = store.create(draft("secret"), &context(), None).unwrap();
        store
            .update_state(&first.id, IncidentState::Acknowledged)
            .unwrap();

        let reopened_store =
            IncidentStore::new(&root.join("diagnostics"), &root, &root.join("cache"));
        let acknowledged = reopened_store
            .list(&IncidentFilter::default())
            .unwrap()
            .incidents;
        assert_eq!(acknowledged[0].state, IncidentState::Acknowledged);

        let repeated = reopened_store
            .create(draft("different-secret"), &context(), None)
            .unwrap();
        assert_eq!(repeated.id, first.id);
        assert_eq!(repeated.state, IncidentState::PendingReview);
        assert_eq!(repeated.occurrence_count, 2);
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn ignored_incident_clears_recovery_without_deleting_history() {
        let (root, store) = store();
        let incident = store.create(draft("secret"), &context(), None).unwrap();

        let ignored = store
            .update_state(&incident.id, IncidentState::Ignored)
            .unwrap();

        assert!(!ignored.requires_recovery);
        assert!(store.pending_recovery().is_none());
        let retained = store.list(&IncidentFilter::default()).unwrap().incidents;
        assert_eq!(retained.len(), 1);
        assert_eq!(retained[0].state, IncidentState::Ignored);
        fs::remove_dir_all(root).unwrap();
    }
}
