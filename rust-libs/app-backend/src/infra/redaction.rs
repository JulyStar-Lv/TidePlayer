use std::{
    collections::HashMap,
    path::{Path, PathBuf},
    sync::RwLock,
};

use once_cell::sync::Lazy;
use regex::{Captures, Regex};
use serde::{Deserialize, Serialize};

use super::file_ops::{atomic_write_json, read_json};

const REDACTION_VERSION: &str = "2";
const REDACTED: &str = "***";
static MUSIC_ROOTS: Lazy<RwLock<Vec<PathBuf>>> = Lazy::new(|| RwLock::new(Vec::new()));

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct PersistedMusicRoots {
    schema_version: i32,
    roots: Vec<String>,
}

static URL_CREDENTIALS: Lazy<Regex> = Lazy::new(|| {
    Regex::new(r"(?i)\b(https?://)[^/@\s:]+:[^/@\s]+@").expect("valid credential regex")
});
static LOOPBACK_PLAYBACK_URL: Lazy<Regex> = Lazy::new(|| {
    Regex::new(
        r#"(?i)(https?://(?:127\.0\.0\.1|\[::1\]):\d+)/media/[^\s?#"'<>]+(?:\?[^\s#"'<>]+)?"#,
    )
    .expect("valid loopback playback URL regex")
});
static JSON_SENSITIVE_VALUE: Lazy<Regex> = Lazy::new(|| {
    Regex::new(
        r#"(?i)("(?:token|access[_-]?token|refresh[_-]?token|password|passwd|secret|api[_-]?key|otp(?:[_-]?code)?|one[_-]?time[_-]?password|authorization|cookie|set-cookie|x[_-]?emby[_-]?token|webdav[_-]?password|smb[_-]?password|plugin[_-]?(?:config[_-]?)?secret)"\s*:\s*")(?:\\.|[^"\\\r\n])*(")"#,
    )
    .expect("valid JSON secret regex")
});
static SENSITIVE_ASSIGNMENT: Lazy<Regex> = Lazy::new(|| {
    Regex::new(
        r#"(?i)(\b(?:token|access[_-]?token|refresh[_-]?token|password|passwd|secret|api[_-]?key|otp(?:[_-]?code)?|one[_-]?time[_-]?password|authorization|cookie|set-cookie|x[_-]?emby[_-]?token|webdav[_-]?password|smb[_-]?password|plugin[_-]?(?:config[_-]?)?secret)\b\s*[:=]\s*)(?:"[^"\r\n]*"|'[^'\r\n]*'|[^&,\s}\]\r\n]+)"#,
    )
    .expect("valid assignment regex")
});
static SENSITIVE_HEADER_LINE: Lazy<Regex> = Lazy::new(|| {
    Regex::new(r"(?im)^(\s*(?:authorization|cookie|set-cookie|x-emby-token)\s*:)\s*[^\r\n]*")
        .expect("valid sensitive header regex")
});
static AUTH_VALUE: Lazy<Regex> = Lazy::new(|| {
    Regex::new(r"(?i)\b(authorization\s*[:=]?\s*(?:bearer|basic)?|bearer|basic)\s+[^,\s]+")
        .expect("valid auth regex")
});
static URL_QUERY: Lazy<Regex> = Lazy::new(|| {
    Regex::new(r#"(?i)(https?://[^\s?#"'<>]+)\?[^\s#"'<>]+"#).expect("valid URL regex")
});
static UUID: Lazy<Regex> = Lazy::new(|| {
    Regex::new(r"(?i)\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\b")
        .expect("valid UUID regex")
});
static ADDRESS: Lazy<Regex> =
    Lazy::new(|| Regex::new(r"(?i)\b0x[0-9a-f]+\b").expect("valid address regex"));
static LINE_NUMBER: Lazy<Regex> =
    Lazy::new(|| Regex::new(r"(?i)(:\d+|\bline\s+\d+\b)").expect("valid line regex"));

pub(crate) fn redaction_version() -> &'static str {
    REDACTION_VERSION
}

pub(crate) fn set_music_roots(roots: Vec<String>) {
    let normalized = roots
        .into_iter()
        .filter(|root| root.trim().len() > 3)
        .map(PathBuf::from)
        .collect();
    if let Ok(mut current) = MUSIC_ROOTS.write() {
        *current = normalized;
    }
}

pub(crate) fn persist_music_roots(path: &Path, roots: Vec<String>) -> std::io::Result<()> {
    let normalized = roots
        .into_iter()
        .filter(|root| root.trim().len() > 3)
        .collect::<Vec<_>>();
    atomic_write_json(
        path,
        &PersistedMusicRoots {
            schema_version: 1,
            roots: normalized.clone(),
        },
    )?;
    set_music_roots(normalized);
    Ok(())
}

pub(crate) fn load_music_roots(path: &Path) -> std::io::Result<()> {
    let state: PersistedMusicRoots = read_json(path)?;
    set_music_roots(state.roots);
    Ok(())
}

pub(crate) fn redact_text(
    value: &str,
    app_document_dir: Option<&Path>,
    app_cache_dir: Option<&Path>,
) -> String {
    let mut redacted = URL_CREDENTIALS
        .replace_all(value, "$1***:***@")
        .into_owned();
    redacted = LOOPBACK_PLAYBACK_URL
        .replace_all(&redacted, "$1/<REDACTED_PLAYBACK_PATH>")
        .into_owned();
    redacted = URL_QUERY
        .replace_all(&redacted, "$1?<REDACTED_QUERY>")
        .into_owned();
    redacted = JSON_SENSITIVE_VALUE
        .replace_all(&redacted, |captures: &Captures<'_>| {
            format!("{}{REDACTED}{}", &captures[1], &captures[2])
        })
        .into_owned();
    redacted = SENSITIVE_HEADER_LINE
        .replace_all(&redacted, |captures: &Captures<'_>| {
            format!("{} {REDACTED}", &captures[1])
        })
        .into_owned();
    redacted = AUTH_VALUE
        .replace_all(&redacted, |captures: &Captures<'_>| {
            format!("{} {REDACTED}", &captures[1])
        })
        .into_owned();
    redacted = SENSITIVE_ASSIGNMENT
        .replace_all(&redacted, |captures: &Captures<'_>| {
            format!("{}{REDACTED}", &captures[1])
        })
        .into_owned();

    if let Some(path) = app_document_dir.and_then(Path::to_str) {
        if !path.is_empty() {
            redacted = redacted.replace(path, "<APP_DOCUMENT_DIR>");
        }
    }
    if let Some(path) = app_cache_dir.and_then(Path::to_str) {
        if !path.is_empty() {
            redacted = redacted.replace(path, "<APP_CACHE_DIR>");
        }
    }
    if let Some(home) = user_home() {
        redacted = redacted.replace(&home, "<HOME>");
    }
    if let Ok(roots) = MUSIC_ROOTS.read() {
        for (index, root) in roots.iter().enumerate() {
            if let Some(path) = root.to_str().filter(|path| path.len() > 3) {
                redacted = redacted.replace(path, &format!("<MUSIC_ROOT_{}>", index + 1));
            }
        }
    }
    redacted
}

pub(crate) fn sanitize_fields(
    fields: &HashMap<String, String>,
    app_document_dir: Option<&Path>,
    app_cache_dir: Option<&Path>,
) -> HashMap<String, String> {
    fields
        .iter()
        .filter(|(key, _)| is_allowed_field_name(key))
        .map(|(key, value)| {
            (
                key.clone(),
                redact_text(value, app_document_dir, app_cache_dir),
            )
        })
        .collect()
}

pub(crate) fn stable_fingerprint_material(value: &str) -> String {
    let redacted = redact_text(value, None, None);
    let redacted = UUID.replace_all(&redacted, "<UUID>");
    let redacted = ADDRESS.replace_all(&redacted, "<ADDRESS>");
    LINE_NUMBER.replace_all(&redacted, "<LINE>").into_owned()
}

fn is_allowed_field_name(name: &str) -> bool {
    let normalized = name
        .chars()
        .filter(|character| character.is_ascii_alphanumeric())
        .flat_map(char::to_lowercase)
        .collect::<String>();
    matches!(
        normalized.as_str(),
        "trackid"
            | "playlistid"
            | "queuesize"
            | "sourcetype"
            | "decoder"
            | "backend"
            | "outputdevice"
            | "dspenabled"
            | "playbackcorrelationid"
            | "scanid"
            | "accountid"
            | "rootidentifier"
            | "scanned"
            | "imported"
            | "failed"
            | "elapsedms"
            | "metadatafetchedbytes"
            | "pluginid"
            | "pluginversion"
            | "pluginfunction"
            | "runtimestage"
            | "timeout"
            | "component"
            | "status"
            | "reason"
            | "pid"
            | "importance"
            | "psskb"
            | "rsskb"
            | "truncated"
            | "bytes"
            | "count"
            | "durationms"
            | "attemptid"
            | "stage"
    )
}

fn user_home() -> Option<String> {
    std::env::var("HOME")
        .ok()
        .or_else(|| std::env::var("USERPROFILE").ok())
        .filter(|value| !value.is_empty())
}

#[cfg(test)]
mod tests {
    use std::sync::{Mutex, MutexGuard};

    use super::*;

    static MUSIC_ROOTS_TEST_LOCK: Mutex<()> = Mutex::new(());

    fn music_roots_test_guard() -> MutexGuard<'static, ()> {
        MUSIC_ROOTS_TEST_LOCK
            .lock()
            .unwrap_or_else(|poisoned| poisoned.into_inner())
    }

    #[test]
    fn redacts_credentials_headers_queries_and_paths() {
        let input = "https://user:pass@example.com/a?access_token=abc Authorization: Bearer xyz Cookie: sid=1\n/Users/test/music";
        let output = redact_text(input, Some(Path::new("/Users/test")), None);
        for (index, sensitive_value) in ["pass", "abc", "xyz", "sid=1", "/Users/test"]
            .into_iter()
            .enumerate()
        {
            assert!(
                !output.contains(sensitive_value),
                "redaction smoke case {index} failed"
            );
        }
        assert!(output.contains("<APP_DOCUMENT_DIR>"));
    }

    #[test]
    fn redacts_supported_key_variants_without_redacting_ordinary_code() {
        let cases = [
            r#"{"accessToken":"fixture-one","refresh_token":"fixture-two","otpCode":"fixture-three"}"#,
            "api-key: fixture-four one-time-password=fixture-five",
            "Authorization: Custom fixture-six\nX-Emby-Token: fixture-seven",
            "Cookie: sid=fixture-eight\nSet-Cookie: session=fixture-nine",
            "at call (file.rs:4): PASSWORD=fixture-ten Access_Token:fixture-eleven",
            "https://user:fixture-twelve@example.test/path",
            "https://example.test/path?ordinary=value&token=fixture-thirteen",
            "prefix Authorization: Bearer fixture-fourteen suffix",
            "prefix Authorization=Basic fixture-fifteen suffix",
            "Authorization=fixture-sixteen",
            r#"{"password":"prefix\"fixture-seventeen"}"#,
            r#"{"webdav_password":"fixture-eighteen"}"#,
            "smbPassword=fixture-nineteen",
            "plugin_config_secret: fixture-twenty",
        ];

        for (index, input) in cases.into_iter().enumerate() {
            let output = redact_text(input, None, None);
            assert!(
                !output.contains("fixture-"),
                "redaction case {index} failed"
            );
        }

        assert_eq!(
            redact_text("code=42 status: ok", None, None),
            "code=42 status: ok"
        );
    }

    #[test]
    fn redacts_loopback_playback_capability_from_stack_like_text() {
        let playback_url = "http://127.0.0.1:45678/media/fixture-capability-token/stream.flac";
        let output = redact_text(
            &format!("player failed at {playback_url}\n  at prepare(Player.kt:42)"),
            None,
            None,
        );

        assert!(
            !output.contains("fixture-capability-token"),
            "playback capability redaction failed"
        );
        assert!(
            !output.contains(playback_url),
            "playback URL redaction failed"
        );
        assert!(output.contains("http://127.0.0.1:45678/<REDACTED_PLAYBACK_PATH>"));
    }

    #[test]
    fn structured_fields_use_a_denylist_before_writing() {
        let fields = HashMap::from([
            ("trackId".to_string(), "42".to_string()),
            ("authorizationToken".to_string(), "secret".to_string()),
            ("unreviewedField".to_string(), "value".to_string()),
        ]);
        let sanitized = sanitize_fields(&fields, None, None);
        assert_eq!(sanitized.get("trackId").map(String::as_str), Some("42"));
        assert!(!sanitized.contains_key("authorizationToken"));
        assert!(!sanitized.contains_key("unreviewedField"));
    }

    #[test]
    fn fingerprint_removes_unstable_values() {
        let first = stable_fingerprint_material(
            "panic 0x1234 at /tmp/a.rs:42 123e4567-e89b-12d3-a456-426614174000",
        );
        let second = stable_fingerprint_material(
            "panic 0xabcd at /tmp/a.rs:99 550e8400-e29b-41d4-a716-446655440000",
        );
        assert_eq!(first, second);
    }

    #[test]
    fn replaces_known_music_roots() {
        let _guard = music_roots_test_guard();
        set_music_roots(vec!["/mnt/private-music".to_string()]);
        assert_eq!(
            redact_text("/mnt/private-music/album/song.flac", None, None),
            "<MUSIC_ROOT_1>/album/song.flac",
        );
        set_music_roots(Vec::new());
    }

    #[test]
    fn persisted_music_roots_are_available_before_full_application_startup() {
        let _guard = music_roots_test_guard();
        let root = super::super::file_ops::temporary_test_directory("redaction-roots");
        let path = root.join("music-roots.json");
        persist_music_roots(&path, vec!["/Volumes/private-music".to_string()]).unwrap();
        set_music_roots(Vec::new());
        load_music_roots(&path).unwrap();

        assert_eq!(
            redact_text("/Volumes/private-music/artist/song.flac", None, None),
            "<MUSIC_ROOT_1>/artist/song.flac",
        );
        std::fs::remove_dir_all(root).unwrap();
        set_music_roots(Vec::new());
    }
}
