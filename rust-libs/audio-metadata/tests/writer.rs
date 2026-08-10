use std::{
    fs,
    path::PathBuf,
    sync::atomic::{AtomicU64, Ordering},
    time::{SystemTime, UNIX_EPOCH},
};

use audio_metadata::{
    read_local_metadata,
    writer::{
        cleanup_metadata_temporary_file, metadata_temporary_path, write_metadata_atomic,
        ArtworkWriteRequest, LyricsWriteRequest, MetadataFields, MetadataMergeMode,
        MetadataWriteRequest,
    },
    MetadataReadOptions,
};

const LRC: &str = "[00:00.010]第一句\n[00:00.050]Second line";
const TTML: &str = r#"<?xml version="1.0" encoding="UTF-8"?>
<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="0.01s" end="0.05s">逐字歌词</p></div></body></tt>"#;
static TEMP_DIR_SEQUENCE: AtomicU64 = AtomicU64::new(0);

#[test]
fn writes_flac_metadata_artwork_lyrics_and_sidecars() {
    let harness = FixtureHarness::new("flac");
    let result = write_metadata_atomic(full_request(&harness.path, MetadataMergeMode::FillMissing))
        .expect("FLAC metadata write");

    assert!(result.changed);
    assert!(result.warnings.is_empty(), "{:?}", result.warnings);
    assert!(result.written_fields.contains(&"title".to_string()));
    assert!(result.written_fields.contains(&"artwork".to_string()));
    assert!(result.written_fields.contains(&"lyrics".to_string()));
    assert_eq!(
        LRC,
        fs::read_to_string(harness.path.with_extension("lrc")).unwrap()
    );
    assert_eq!(
        TTML,
        fs::read_to_string(harness.path.with_extension("ttml")).unwrap()
    );

    let metadata = read(&harness.path);
    assert_eq!(Some("固化标题"), metadata.title.as_deref());
    assert_eq!(Some("艺术家 A, Artist B"), metadata.artist.as_deref());
    assert_eq!(Some("专辑"), metadata.album.as_deref());
    assert_eq!(Some(3), metadata.track_number);
    assert!(metadata.has_embedded_artwork);
    assert_eq!(
        Some(LRC),
        metadata
            .lyrics
            .as_ref()
            .map(|lyrics| lyrics.content.as_str())
    );
}

#[test]
fn writes_mp3_id3_uslt_and_apic() {
    let harness = FixtureHarness::new("mp3");
    let result = write_metadata_atomic(full_request(&harness.path, MetadataMergeMode::FillMissing))
        .expect("MP3 metadata write");

    assert!(
        result
            .warnings
            .iter()
            .all(|warning| warning.contains("unsupported")),
        "{:?}",
        result.warnings,
    );
    let metadata = read(&harness.path);
    assert_eq!(Some("固化标题"), metadata.title.as_deref());
    assert_eq!(Some("艺术家 A, Artist B"), metadata.artist.as_deref());
    assert_eq!(Some("专辑"), metadata.album.as_deref());
    assert!(metadata.has_embedded_artwork);
    assert_eq!(
        Some(LRC),
        metadata
            .lyrics
            .as_ref()
            .map(|lyrics| lyrics.content.as_str())
    );
}

#[test]
fn writes_m4a_metadata_and_cover() {
    let harness = FixtureHarness::new("m4a");
    let result = write_metadata_atomic(full_request(&harness.path, MetadataMergeMode::FillMissing))
        .expect("M4A metadata write");

    assert!(
        result
            .warnings
            .iter()
            .all(|warning| warning.contains("unsupported")),
        "{:?}",
        result.warnings,
    );
    let metadata = read(&harness.path);
    assert_eq!(Some("固化标题"), metadata.title.as_deref());
    assert_eq!(Some("艺术家 A, Artist B"), metadata.artist.as_deref());
    assert_eq!(Some("专辑"), metadata.album.as_deref());
    assert!(metadata.has_embedded_artwork);
}

#[test]
fn writes_vorbis_and_opus_comments_with_lyrics() {
    for extension in ["ogg", "opus"] {
        let harness = FixtureHarness::new(extension);
        let result =
            write_metadata_atomic(full_request(&harness.path, MetadataMergeMode::FillMissing))
                .unwrap_or_else(|error| panic!("{extension} metadata write: {error}"));

        assert!(
            result.warnings.is_empty(),
            "{extension}: {:?}",
            result.warnings
        );
        let metadata = read(&harness.path);
        assert_eq!(Some("固化标题"), metadata.title.as_deref(), "{extension}");
        assert_eq!(
            Some(LRC),
            metadata
                .lyrics
                .as_ref()
                .map(|lyrics| lyrics.content.as_str()),
            "{extension}"
        );
        assert!(metadata.has_embedded_artwork, "{extension}");
    }
}

#[test]
fn writes_wav_id3_metadata_without_changing_audio_properties() {
    let harness = FixtureHarness::new("wav");
    let before = read(&harness.path);
    let result = write_metadata_atomic(full_request(&harness.path, MetadataMergeMode::FillMissing))
        .expect("WAV metadata write");
    let after = read(&harness.path);

    assert!(
        result
            .warnings
            .iter()
            .all(|warning| warning.contains("unsupported")),
        "{:?}",
        result.warnings,
    );
    assert_eq!(Some("固化标题"), after.title.as_deref());
    assert_eq!(before.duration_ms, after.duration_ms);
    assert_eq!(before.sample_rate, after.sample_rate);
    assert_eq!(before.channels, after.channels);
}

#[test]
fn fill_missing_preserves_existing_trusted_metadata() {
    let harness = FixtureHarness::new("flac");
    let mut first = full_request(&harness.path, MetadataMergeMode::PreferSnapshot);
    first.metadata.title = Some("Embedded title".to_string());
    first.metadata.artist = Some("Embedded artist".to_string());
    first.metadata.artists.clear();
    write_metadata_atomic(first).expect("initial metadata write");

    let mut plugin = full_request(&harness.path, MetadataMergeMode::FillMissing);
    plugin.metadata.title = Some("Wrong plugin title".to_string());
    plugin.metadata.artist = Some("Wrong plugin artist".to_string());
    plugin.metadata.artists.clear();
    write_metadata_atomic(plugin).expect("fill missing metadata write");

    let metadata = read(&harness.path);
    assert_eq!(Some("Embedded title"), metadata.title.as_deref());
    assert_eq!(Some("Embedded artist"), metadata.artist.as_deref());
}

#[test]
fn unsupported_format_keeps_original_bytes_and_returns_warning() {
    let root = unique_temp_dir("unsupported");
    fs::create_dir_all(&root).unwrap();
    let path = root.join("audio.xyz");
    let original = b"complete original media";
    fs::write(&path, original).unwrap();
    let mut request = full_request(&path, MetadataMergeMode::FillMissing);
    request.artwork = None;
    request.lyrics = None;

    let result = write_metadata_atomic(request).expect("unsupported format result");

    assert!(!result.changed);
    assert!(result.verified.is_none());
    assert_eq!(original, fs::read(&path).unwrap().as_slice());
    assert!(result
        .warnings
        .iter()
        .any(|warning| warning.contains("unsupported")));
    fs::remove_dir_all(root).unwrap();
}

#[test]
fn invalid_artwork_is_a_warning_and_audio_still_finalizes() {
    let harness = FixtureHarness::new("flac");
    let mut request = full_request(&harness.path, MetadataMergeMode::FillMissing);
    request.artwork = Some(ArtworkWriteRequest {
        data: b"not an image".to_vec(),
        mime_type: Some("image/png".to_string()),
    });

    let result = write_metadata_atomic(request).expect("metadata write with invalid artwork");

    assert_eq!(Some("固化标题"), read(&harness.path).title.as_deref());
    assert!(result
        .warnings
        .iter()
        .any(|warning| warning.contains("artwork")));
}

#[test]
fn oversized_artwork_is_a_warning_and_audio_still_finalizes() {
    let harness = FixtureHarness::new("flac");
    let mut data = vec![0_u8; 4 * 1024 * 1024 + 1];
    data[..8].copy_from_slice(b"\x89PNG\r\n\x1a\n");
    let mut request = full_request(&harness.path, MetadataMergeMode::FillMissing);
    request.artwork = Some(ArtworkWriteRequest {
        data,
        mime_type: Some("image/png".to_string()),
    });

    let result = write_metadata_atomic(request).expect("metadata write with oversized artwork");

    assert_eq!(Some("固化标题"), read(&harness.path).title.as_deref());
    assert!(result
        .warnings
        .iter()
        .any(|warning| warning.contains("embedding limit")));
}

#[test]
fn failed_parse_preserves_the_original_audio_and_removes_temp_file() {
    let root = unique_temp_dir("invalid-flac");
    fs::create_dir_all(&root).unwrap();
    let path = root.join("broken.flac");
    let original = b"not a flac stream";
    fs::write(&path, original).unwrap();

    assert!(write_metadata_atomic(full_request(&path, MetadataMergeMode::FillMissing)).is_err());
    assert_eq!(original, fs::read(&path).unwrap().as_slice());
    assert!(!metadata_temporary_path(&path).exists());
    fs::remove_dir_all(root).unwrap();
}

#[test]
fn stale_metadata_temp_is_cleaned_safely() {
    let harness = FixtureHarness::new("flac");
    let temporary = metadata_temporary_path(&harness.path);
    fs::write(&temporary, b"stale").unwrap();

    assert!(cleanup_metadata_temporary_file(&harness.path).unwrap());
    assert!(!temporary.exists());
    assert!(harness.path.is_file());
}

fn full_request(path: &std::path::Path, merge_mode: MetadataMergeMode) -> MetadataWriteRequest {
    MetadataWriteRequest {
        path: path.to_string_lossy().into_owned(),
        metadata: MetadataFields {
            title: Some("固化标题".to_string()),
            artists: vec!["艺术家 A".to_string(), "Artist B".to_string()],
            album_artist: Some("Album Artist".to_string()),
            album: Some("专辑".to_string()),
            composer: Some("Composer".to_string()),
            lyricist: Some("Lyricist".to_string()),
            conductor: Some("Conductor".to_string()),
            genre: Some("Pop".to_string()),
            grouping: Some("Group".to_string()),
            comment: Some("Finalized".to_string()),
            copyright: Some("Copyright".to_string()),
            publisher: Some("Publisher".to_string()),
            date: Some("2026-08-10".to_string()),
            track_number: Some(3),
            track_total: Some(12),
            disc_number: Some(1),
            disc_total: Some(2),
            isrc: Some("USABC1234567".to_string()),
            musicbrainz_recording_id: Some("recording-id".to_string()),
            replay_gain_track_gain: Some(-4.25),
            replay_gain_track_peak: Some(0.95),
            ..MetadataFields::default()
        },
        artwork: Some(ArtworkWriteRequest {
            data: include_bytes!("fixtures/cover.png").to_vec(),
            mime_type: Some("image/png".to_string()),
        }),
        lyrics: Some(LyricsWriteRequest {
            embedded: Some(LRC.to_string()),
            lrc: Some(LRC.to_string()),
            ttml: Some(TTML.to_string()),
            save_sidecars: true,
        }),
        merge_mode,
    }
}

fn read(path: &std::path::Path) -> audio_metadata::NormalizedMetadata {
    read_local_metadata(path, MetadataReadOptions::default()).expect("read local metadata")
}

struct FixtureHarness {
    root: PathBuf,
    path: PathBuf,
}

impl FixtureHarness {
    fn new(extension: &str) -> Self {
        let root = unique_temp_dir(extension);
        fs::create_dir_all(&root).unwrap();
        let path = root.join(format!("song.{extension}"));
        let fixture = match extension {
            "flac" => include_bytes!("fixtures/minimal.flac").as_slice(),
            "mp3" => include_bytes!("fixtures/minimal.mp3").as_slice(),
            "m4a" => include_bytes!("fixtures/minimal.m4a").as_slice(),
            "ogg" => include_bytes!("fixtures/minimal.ogg").as_slice(),
            "opus" => include_bytes!("fixtures/minimal.opus").as_slice(),
            "wav" => include_bytes!("fixtures/minimal.wav").as_slice(),
            _ => panic!("unknown fixture extension {extension}"),
        };
        fs::write(&path, fixture).unwrap();
        Self { root, path }
    }
}

impl Drop for FixtureHarness {
    fn drop(&mut self) {
        let _ = fs::remove_dir_all(&self.root);
    }
}

fn unique_temp_dir(label: &str) -> PathBuf {
    let nanos = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_nanos();
    std::env::temp_dir().join(format!(
        "audio-metadata-writer-{label}-{}-{nanos}-{}",
        std::process::id(),
        TEMP_DIR_SEQUENCE.fetch_add(1, Ordering::Relaxed),
    ))
}
