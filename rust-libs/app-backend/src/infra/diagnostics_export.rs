use std::{
    fs::{self, File},
    io::{self, Write},
    path::Path,
};

use serde_json::json;
use zip::{write::SimpleFileOptions, CompressionMethod, ZipWriter};

use super::{
    file_ops::{directory_size, now_epoch_ms},
    incidents::IncidentStore,
    logging::LogStore,
    model::{
        DiagnosticExportRequest, DiagnosticExportResult, DiagnosticLogSession,
        DiagnosticsRuntimeInit,
    },
    redaction::{redact_text, redaction_version},
    startup_journal::StartupJournal,
};

const APP_NAME: &str = "TidePlayer";
const PACKAGE_ID: &str = "io.github.julystar.musicapp";

pub(crate) fn export_bundle(
    init: &DiagnosticsRuntimeInit,
    log_store: &LogStore,
    incident_store: &IncidentStore,
    startup_journal: &StartupJournal,
    request: &DiagnosticExportRequest,
) -> io::Result<DiagnosticExportResult> {
    log_store.flush()?;
    let exports_dir = log_store.diagnostics_root().join("exports");
    fs::create_dir_all(&exports_dir)?;
    let file_name = format!("{APP_NAME}-diagnostics-{}.zip", now_epoch_ms());
    let path = exports_dir.join(&file_name);
    let file = File::create(&path)?;
    let mut zip = ZipWriter::new(file);
    let options = SimpleFileOptions::default()
        .compression_method(CompressionMethod::Deflated)
        .unix_permissions(0o600);
    let app_document_dir = Path::new(&init.app_document_dir);
    let app_cache_dir = Path::new(&init.app_cache_dir);
    let redact = |value: &str| redact_text(value, Some(app_document_dir), Some(app_cache_dir));

    let mut sessions = log_store.read_manifest().sessions;
    sessions.sort_by_key(|session| std::cmp::Reverse(session.started_at_epoch_ms));
    sessions.truncate(2);
    let incidents = incident_store
        .all_for_export(request.include_resolved_incidents)
        .into_iter()
        .filter(|incident| {
            request.incident_ids.is_empty() || request.incident_ids.contains(&incident.id)
        })
        .collect::<Vec<_>>();
    let current = startup_journal.current();
    let previous = startup_journal.previous();
    let history = startup_journal.history(20);

    let manifest = json!({
        "schemaVersion": 1,
        "application": APP_NAME,
        "packageId": PACKAGE_ID,
        "generatedAtEpochMs": now_epoch_ms(),
        "appVersion": init.app_version,
        "buildInfo": redact(&init.build_info),
        "gitCommitSha": init.git_commit_sha,
        "platform": init.platform,
        "includedLogSessions": sessions.len(),
        "includedIncidents": incidents.len(),
        "redactionVersion": redaction_version(),
        "contents": [
            "summary.txt",
            "environment.json",
            "privacy-report.txt",
            "startup/",
            "incidents/",
            "logs/",
            "state/"
        ]
    });
    write_redacted_json(&mut zip, "manifest.json", &manifest, options, &redact)?;
    write_text(&mut zip, "summary.txt", &redact(&request.summary), options)?;
    write_text(
        &mut zip,
        "environment.json",
        &redact(&request.environment_json),
        options,
    )?;
    write_text(
        &mut zip,
        "privacy-report.txt",
        &privacy_report(&incidents),
        options,
    )?;
    write_redacted_json(
        &mut zip,
        "startup/current-attempt.json",
        &current,
        options,
        &redact,
    )?;
    if let Some(previous) = &previous {
        write_redacted_json(
            &mut zip,
            "startup/previous-attempt.json",
            previous,
            options,
            &redact,
        )?;
    }
    write_redacted_json(&mut zip, "startup/history.json", &history, options, &redact)?;

    write_redacted_json(
        &mut zip,
        "incidents/incidents.json",
        &incidents,
        options,
        &redact,
    )?;
    for incident in &incidents {
        write_redacted_json(
            &mut zip,
            &format!("incidents/{}/incident.json", incident.id),
            incident,
            options,
            &redact,
        )?;
        for artifact in &incident.artifact_paths {
            if let Ok(content) = incident_store.read_artifact(&incident.id, artifact) {
                write_text(
                    &mut zip,
                    &format!("incidents/{}/{}", incident.id, safe_zip_name(artifact)),
                    &redact(&content),
                    options,
                )?;
            }
        }
    }

    write_redacted_json(&mut zip, "logs/sessions.json", &sessions, options, &redact)?;
    for (index, session) in sessions.iter().enumerate() {
        let name = if index == 0 {
            "logs/current-session.jsonl"
        } else {
            "logs/previous-session.jsonl"
        };
        let snapshot = snapshot_session(log_store, session)?;
        write_text(
            &mut zip,
            name,
            &redact(&String::from_utf8_lossy(&snapshot)),
            options,
        )?;
    }
    write_text(
        &mut zip,
        "state/playback-summary.json",
        &redact(&request.playback_summary_json),
        options,
    )?;
    write_text(
        &mut zip,
        "state/scan-summary.json",
        &redact(&request.scan_summary_json),
        options,
    )?;
    write_text(
        &mut zip,
        "state/plugin-summary.json",
        &redact(&request.plugin_summary_json),
        options,
    )?;
    write_text(
        &mut zip,
        "state/source-summary.json",
        &redact(&request.source_summary_json),
        options,
    )?;
    write_text(
        &mut zip,
        "state/storage-summary.json",
        &redact(&request.storage_summary_json),
        options,
    )?;
    zip.finish()?.sync_all()?;
    let bytes = fs::metadata(&path)?.len().min(i64::MAX as u64) as i64;
    Ok(DiagnosticExportResult {
        path: path.to_string_lossy().into_owned(),
        file_name,
        bytes,
        included_log_sessions: sessions.len() as i64,
        included_incidents: incidents.len() as i64,
    })
}

pub(crate) fn clear_exports(diagnostics_root: &Path, protected_path: Option<&Path>) -> i64 {
    let exports_dir = diagnostics_root.join("exports");
    let Ok(entries) = fs::read_dir(exports_dir) else {
        return 0;
    };
    let mut removed = 0_i64;
    for entry in entries.flatten() {
        let path = entry.path();
        if protected_path.is_some_and(|protected| protected == path) {
            continue;
        }
        if path.extension().and_then(|extension| extension.to_str()) == Some("zip")
            && fs::remove_file(path).is_ok()
        {
            removed += 1;
        }
    }
    removed
}

pub(crate) fn export_bytes(diagnostics_root: &Path) -> i64 {
    directory_size(&diagnostics_root.join("exports"))
}

fn snapshot_session(store: &LogStore, session: &DiagnosticLogSession) -> io::Result<Vec<u8>> {
    let mut result = Vec::new();
    for path in &session.log_paths {
        result.extend(store.snapshot_log_file(path)?);
    }
    Ok(result)
}

fn write_redacted_json<T: serde::Serialize>(
    zip: &mut ZipWriter<File>,
    name: &str,
    value: &T,
    options: SimpleFileOptions,
    redact: &dyn Fn(&str) -> String,
) -> io::Result<()> {
    let serialized = serde_json::to_string_pretty(value).map_err(io::Error::other)?;
    zip.start_file(name, options)?;
    zip.write_all(redact(&serialized).as_bytes())
}

fn write_text(
    zip: &mut ZipWriter<File>,
    name: &str,
    value: &str,
    options: SimpleFileOptions,
) -> io::Result<()> {
    zip.start_file(name, options)?;
    zip.write_all(value.as_bytes())
}

fn privacy_report(incidents: &[super::model::DiagnosticIncident]) -> String {
    format!(
        "TidePlayer diagnostics privacy report\n\
         redactionRulesVersion={}\n\
         replacedFieldTypes=credentials,authorization,cookies,tokens,secrets,otp,url-query,loopback-playback-path,user-paths\n\
         containsSystemAnrTrace={}\n\
         containsFilePathSummary=true\n\
         containsPluginId=true\n\
         containsRemoteHostName=true\n\
         passwordsIncluded=false\n\
         tokensIncluded=false\n\
         musicContentIncluded=false\n\
         dataUploadPerformed=false\n",
        redaction_version(),
        incidents.iter().any(|incident| {
            incident.incident_type == super::model::IncidentType::AndroidAnr
                && incident
                    .artifact_paths
                    .iter()
                    .any(|path| path == "android-anr.txt")
        }),
    )
}

fn safe_zip_name(name: &str) -> String {
    Path::new(name)
        .file_name()
        .and_then(|name| name.to_str())
        .unwrap_or("artifact.txt")
        .to_string()
}

#[cfg(test)]
mod tests {
    use std::{fs, io::Read};

    use super::*;
    use crate::infra::{
        file_ops::{create_diagnostics_directories, temporary_test_directory},
        incidents::{IncidentContext, IncidentStore},
        logging::LogStore,
        model::{
            DiagnosticLogCategory, DiagnosticLogEntry, DiagnosticLogLevel, IncidentDraft,
            IncidentSeverity, IncidentType, StartupStage, DIAGNOSTICS_SCHEMA_VERSION,
        },
    };

    #[test]
    fn zip_has_required_manifest_and_contains_no_test_secret() {
        let root = temporary_test_directory("diagnostics-export");
        let diagnostics = root.join("diagnostics");
        create_diagnostics_directories(&diagnostics).unwrap();
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
        let journal = StartupJournal::start(&diagnostics, false, None).unwrap();
        let logs = LogStore::start(&diagnostics, &init, &journal.current().attempt_id).unwrap();
        logs.write_entry(DiagnosticLogEntry {
            schema_version: DIAGNOSTICS_SCHEMA_VERSION,
            timestamp_epoch_ms: 1,
            level: DiagnosticLogLevel::Info,
            category: DiagnosticLogCategory::Security,
            target: "test".into(),
            message: "token=TEST_SECRET".into(),
            detail: None,
            session_id: logs.current_session_id(),
            correlation_id: None,
            startup_attempt_id: Some(journal.current().attempt_id),
            thread: None,
            platform: "test".into(),
            fields: Default::default(),
        })
        .unwrap();
        let incidents = IncidentStore::new(&diagnostics, &root, &root.join("cache"));
        incidents
            .create(
                IncidentDraft {
                    incident_type: IncidentType::RustPanic,
                    severity: IncidentSeverity::Fatal,
                    summary: "panic".into(),
                    detail: Some("password=TEST_SECRET".into()),
                    fingerprint_material: None,
                    requires_recovery: true,
                },
                &IncidentContext {
                    process_name: "test".into(),
                    session_id: logs.current_session_id(),
                    startup_attempt_id: journal.current().attempt_id,
                    startup_stage: StartupStage::BackendCreating,
                },
                Some(("rust-panic.txt", b"Bearer TEST_SECRET")),
            )
            .unwrap();
        let playback_url = "http://127.0.0.1:45678/media/TEST_SECRET/stream.flac";
        let result = export_bundle(
            &init,
            &logs,
            &incidents,
            &journal,
            &DiagnosticExportRequest {
                summary: "api_key=TEST_SECRET".into(),
                environment_json: "{}".into(),
                playback_summary_json: format!(r#"{{"resource":"{playback_url}"}}"#),
                scan_summary_json: "{}".into(),
                plugin_summary_json: "{}".into(),
                source_summary_json: "{}".into(),
                storage_summary_json: "{}".into(),
                include_resolved_incidents: false,
                incident_ids: Vec::new(),
            },
        )
        .unwrap();
        let file = File::open(&result.path).unwrap();
        let mut archive = zip::ZipArchive::new(file).unwrap();
        let mut combined = String::new();
        for index in 0..archive.len() {
            archive
                .by_index(index)
                .unwrap()
                .read_to_string(&mut combined)
                .unwrap();
        }
        assert!(archive.by_name("manifest.json").is_ok());
        assert!(archive.by_name("privacy-report.txt").is_ok());
        assert!(
            !combined.contains("TEST_SECRET"),
            "diagnostics export credential redaction failed"
        );
        assert!(
            !combined.contains(playback_url),
            "diagnostics export playback URL redaction failed"
        );
        assert!(
            !combined.contains(root.to_string_lossy().as_ref()),
            "diagnostics export path redaction failed"
        );
        fs::remove_dir_all(root).unwrap();
    }
}
