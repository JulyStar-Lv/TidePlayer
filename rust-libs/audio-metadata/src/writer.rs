use std::{
    fs::{self, File, OpenOptions},
    io::{Cursor, Write},
    path::{Path, PathBuf},
};

use lofty::{
    config::{ParseOptions, WriteOptions},
    file::{AudioFile, FileType, TaggedFileExt},
    picture::{Picture, PictureType},
    probe::Probe,
    tag::{Accessor, ItemKey, Tag, TagType},
};

use crate::{read_local_metadata, MetadataReadOptions, NormalizedMetadata};

pub const MAX_WRITE_ARTWORK_BYTES: usize = 4 * 1024 * 1024;

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub enum MetadataMergeMode {
    #[default]
    FillMissing,
    PreferSnapshot,
}

#[derive(Debug, Clone, Default, PartialEq)]
pub struct MetadataFields {
    pub title: Option<String>,
    pub artist: Option<String>,
    pub artists: Vec<String>,
    pub album_artist: Option<String>,
    pub album: Option<String>,
    pub composer: Option<String>,
    pub lyricist: Option<String>,
    pub conductor: Option<String>,
    pub genre: Option<String>,
    pub grouping: Option<String>,
    pub comment: Option<String>,
    pub copyright: Option<String>,
    pub publisher: Option<String>,
    pub date: Option<String>,
    pub original_release_date: Option<String>,
    pub track_number: Option<u32>,
    pub track_total: Option<u32>,
    pub disc_number: Option<u32>,
    pub disc_total: Option<u32>,
    pub bpm: Option<f64>,
    pub musical_key: Option<String>,
    pub isrc: Option<String>,
    pub musicbrainz_recording_id: Option<String>,
    pub musicbrainz_track_id: Option<String>,
    pub musicbrainz_release_id: Option<String>,
    pub musicbrainz_release_group_id: Option<String>,
    pub musicbrainz_artist_id: Option<String>,
    pub musicbrainz_release_artist_id: Option<String>,
    pub musicbrainz_work_id: Option<String>,
    pub replay_gain_track_gain: Option<f64>,
    pub replay_gain_track_peak: Option<f64>,
    pub replay_gain_album_gain: Option<f64>,
    pub replay_gain_album_peak: Option<f64>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ArtworkWriteRequest {
    pub data: Vec<u8>,
    pub mime_type: Option<String>,
}

#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct LyricsWriteRequest {
    pub embedded: Option<String>,
    pub lrc: Option<String>,
    pub ttml: Option<String>,
    pub save_sidecars: bool,
}

#[derive(Debug, Clone, PartialEq)]
pub struct MetadataWriteRequest {
    pub path: String,
    pub metadata: MetadataFields,
    pub artwork: Option<ArtworkWriteRequest>,
    pub lyrics: Option<LyricsWriteRequest>,
    pub merge_mode: MetadataMergeMode,
}

#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct MetadataWriteCapabilities {
    pub format: String,
    pub metadata: bool,
    pub artwork: bool,
    pub embedded_lyrics: bool,
    pub synced_lyrics: bool,
    pub arbitrary_text: bool,
    pub sidecar_lyrics: bool,
}

#[derive(Debug, Clone, PartialEq)]
pub struct MetadataWriteResult {
    pub changed: bool,
    pub written_fields: Vec<String>,
    pub warnings: Vec<String>,
    pub capabilities: MetadataWriteCapabilities,
    pub verified: Option<NormalizedMetadata>,
}

#[derive(Debug, thiserror::Error)]
pub enum MetadataWriteError {
    #[error("metadata target path is empty")]
    InvalidPath,
    #[error("metadata target is not a regular file: {0}")]
    NotAFile(String),
    #[error("failed to prepare metadata temporary file: {0}")]
    TemporaryFile(String),
    #[error("failed to parse audio before writing: {0}")]
    ParseBeforeWrite(String),
    #[error("failed to save audio metadata: {0}")]
    Save(String),
    #[error("metadata verification failed: {0}")]
    Verification(String),
    #[error("failed to atomically replace finalized audio: {0}")]
    AtomicReplace(String),
}

pub fn metadata_write_capabilities(path: impl AsRef<Path>) -> MetadataWriteCapabilities {
    let file_type = FileType::from_path(path.as_ref());
    capabilities_for(file_type)
}

pub fn metadata_temporary_path(path: impl AsRef<Path>) -> PathBuf {
    let path = path.as_ref();
    let mut file_name = path
        .file_name()
        .map(|value| value.to_os_string())
        .unwrap_or_default();
    file_name.push(".metadata.tmp");
    path.with_file_name(file_name)
}

pub fn cleanup_metadata_temporary_file(path: impl AsRef<Path>) -> Result<bool, MetadataWriteError> {
    let temporary = metadata_temporary_path(path);
    if !temporary.exists() {
        return Ok(false);
    }
    fs::remove_file(&temporary).map_err(|error| {
        MetadataWriteError::TemporaryFile(format!("{}: {error}", temporary.display()))
    })?;
    Ok(true)
}

pub fn write_metadata_atomic(
    request: MetadataWriteRequest,
) -> Result<MetadataWriteResult, MetadataWriteError> {
    let path = PathBuf::from(request.path.trim());
    if request.path.trim().is_empty() {
        return Err(MetadataWriteError::InvalidPath);
    }
    if !path.is_file() {
        return Err(MetadataWriteError::NotAFile(path.display().to_string()));
    }

    let capabilities = metadata_write_capabilities(&path);
    if !capabilities.metadata {
        let mut warnings = vec![format!(
            "metadata writing is unsupported for {}",
            capabilities.format
        )];
        let mut written_fields = Vec::new();
        let sidecar_changed = write_requested_sidecars(
            &path,
            request.lyrics.as_ref(),
            &mut written_fields,
            &mut warnings,
        );
        return Ok(MetadataWriteResult {
            changed: sidecar_changed,
            written_fields,
            warnings,
            capabilities,
            verified: None,
        });
    }

    let before = read_local_metadata(&path, MetadataReadOptions::default())
        .map_err(|error| MetadataWriteError::ParseBeforeWrite(error.to_string()))?;
    let temporary = metadata_temporary_path(&path);
    cleanup_metadata_temporary_file(&path)?;
    fs::copy(&path, &temporary).map_err(|error| {
        MetadataWriteError::TemporaryFile(format!(
            "copy {} to {}: {error}",
            path.display(),
            temporary.display()
        ))
    })?;

    let write_result = (|| {
        let mut tagged_file = Probe::open(&temporary)
            .map_err(|error| MetadataWriteError::ParseBeforeWrite(error.to_string()))?
            .options(ParseOptions::new().read_cover_art(true))
            .guess_file_type()
            .map_err(|error| MetadataWriteError::ParseBeforeWrite(error.to_string()))?
            .read()
            .map_err(|error| MetadataWriteError::ParseBeforeWrite(error.to_string()))?;
        let actual_capabilities = capabilities_for(Some(tagged_file.file_type()));
        if !actual_capabilities.metadata {
            return Err(MetadataWriteError::Save(format!(
                "detected {} does not support writable tags",
                actual_capabilities.format
            )));
        }

        let primary_tag_type = tagged_file.primary_tag_type();
        if tagged_file.primary_tag().is_none() {
            tagged_file.insert_tag(Tag::new(primary_tag_type));
        }
        let tag = tagged_file.primary_tag_mut().ok_or_else(|| {
            MetadataWriteError::Save("unable to create the primary tag".to_string())
        })?;

        let mut written_fields = Vec::new();
        let mut warnings = Vec::new();
        apply_metadata_fields(
            tag,
            &request.metadata,
            request.merge_mode,
            &mut written_fields,
            &mut warnings,
        );
        apply_artwork(
            tag,
            request.artwork.as_ref(),
            request.merge_mode,
            &actual_capabilities,
            &mut written_fields,
            &mut warnings,
        );
        apply_embedded_lyrics(
            tag,
            request.lyrics.as_ref(),
            request.merge_mode,
            &actual_capabilities,
            &mut written_fields,
            &mut warnings,
        );

        let tag_changed = !written_fields.is_empty();
        if tag_changed {
            tagged_file
                .save_to_path(&temporary, WriteOptions::default())
                .map_err(|error| MetadataWriteError::Save(error.to_string()))?;
            File::open(&temporary)
                .and_then(|file| file.sync_all())
                .map_err(|error| MetadataWriteError::Save(error.to_string()))?;
        }

        let verified = read_local_metadata(&temporary, MetadataReadOptions::default())
            .map_err(|error| MetadataWriteError::Verification(error.to_string()))?;
        verify_audio_properties(&before, &verified)?;
        verify_written_values(&request, &written_fields, &verified)?;

        if tag_changed {
            atomic_replace_file(&temporary, &path)
                .map_err(|error| MetadataWriteError::AtomicReplace(error.to_string()))?;
            if let Some(parent) = path.parent() {
                if let Err(error) = File::open(parent).and_then(|directory| directory.sync_all()) {
                    warnings.push(format!("failed to sync finalized media directory: {error}"));
                }
            }
        } else {
            let _ = fs::remove_file(&temporary);
        }

        let sidecar_changed = write_requested_sidecars(
            &path,
            request.lyrics.as_ref(),
            &mut written_fields,
            &mut warnings,
        );
        Ok(MetadataWriteResult {
            changed: tag_changed || sidecar_changed,
            written_fields,
            warnings,
            capabilities: actual_capabilities,
            verified: Some(verified),
        })
    })();

    if write_result.is_err() {
        let _ = fs::remove_file(&temporary);
    }
    write_result
}

fn capabilities_for(file_type: Option<FileType>) -> MetadataWriteCapabilities {
    let (format, metadata, artwork, embedded_lyrics, arbitrary_text) = match file_type {
        Some(FileType::Flac) => ("FLAC", true, true, true, true),
        Some(FileType::Mpeg) => ("MP3", true, true, true, true),
        Some(FileType::Mp4) => ("M4A / MP4", true, true, true, false),
        Some(FileType::Vorbis) => ("OGG Vorbis", true, true, true, true),
        Some(FileType::Opus) => ("Opus", true, true, true, true),
        Some(FileType::Wav) => ("WAV", true, true, true, true),
        Some(other) => {
            return MetadataWriteCapabilities {
                format: format!("{other:?}"),
                sidecar_lyrics: true,
                ..MetadataWriteCapabilities::default()
            }
        }
        None => {
            return MetadataWriteCapabilities {
                format: "unknown format".to_string(),
                sidecar_lyrics: true,
                ..MetadataWriteCapabilities::default()
            }
        }
    };
    MetadataWriteCapabilities {
        format: format.to_string(),
        metadata,
        artwork,
        embedded_lyrics,
        synced_lyrics: false,
        arbitrary_text,
        sidecar_lyrics: true,
    }
}

fn apply_metadata_fields(
    tag: &mut Tag,
    fields: &MetadataFields,
    merge_mode: MetadataMergeMode,
    written: &mut Vec<String>,
    warnings: &mut Vec<String>,
) {
    write_text(
        tag,
        ItemKey::TrackTitle,
        fields.title.as_deref(),
        "title",
        merge_mode,
        written,
        warnings,
    );
    let joined_artists = fields
        .artists
        .iter()
        .filter_map(|artist| normalized(artist))
        .collect::<Vec<_>>()
        .join(", ");
    let artist = (!joined_artists.is_empty())
        .then_some(joined_artists.as_str())
        .or(fields.artist.as_deref());
    write_text(
        tag,
        ItemKey::TrackArtist,
        artist,
        "artists",
        merge_mode,
        written,
        warnings,
    );
    write_text(
        tag,
        ItemKey::AlbumArtist,
        fields.album_artist.as_deref(),
        "album_artist",
        merge_mode,
        written,
        warnings,
    );
    write_text(
        tag,
        ItemKey::AlbumTitle,
        fields.album.as_deref(),
        "album",
        merge_mode,
        written,
        warnings,
    );
    write_text(
        tag,
        ItemKey::Composer,
        fields.composer.as_deref(),
        "composer",
        merge_mode,
        written,
        warnings,
    );
    write_text(
        tag,
        ItemKey::Lyricist,
        fields.lyricist.as_deref(),
        "lyricist",
        merge_mode,
        written,
        warnings,
    );
    write_text(
        tag,
        ItemKey::Conductor,
        fields.conductor.as_deref(),
        "conductor",
        merge_mode,
        written,
        warnings,
    );
    write_text(
        tag,
        ItemKey::Genre,
        fields.genre.as_deref(),
        "genre",
        merge_mode,
        written,
        warnings,
    );
    write_text(
        tag,
        ItemKey::ContentGroup,
        fields.grouping.as_deref(),
        "grouping",
        merge_mode,
        written,
        warnings,
    );
    write_text(
        tag,
        ItemKey::Comment,
        fields.comment.as_deref(),
        "comment",
        merge_mode,
        written,
        warnings,
    );
    write_text(
        tag,
        ItemKey::CopyrightMessage,
        fields.copyright.as_deref(),
        "copyright",
        merge_mode,
        written,
        warnings,
    );
    write_text(
        tag,
        ItemKey::Publisher,
        fields.publisher.as_deref(),
        "publisher",
        merge_mode,
        written,
        warnings,
    );
    write_text(
        tag,
        ItemKey::RecordingDate,
        fields.date.as_deref(),
        "date",
        merge_mode,
        written,
        warnings,
    );
    write_text(
        tag,
        ItemKey::OriginalReleaseDate,
        fields.original_release_date.as_deref(),
        "original_release_date",
        merge_mode,
        written,
        warnings,
    );
    write_number(
        tag,
        NumberField::Track,
        fields.track_number,
        "track_number",
        merge_mode,
        written,
    );
    write_number(
        tag,
        NumberField::TrackTotal,
        fields.track_total,
        "track_total",
        merge_mode,
        written,
    );
    write_number(
        tag,
        NumberField::Disc,
        fields.disc_number,
        "disc_number",
        merge_mode,
        written,
    );
    write_number(
        tag,
        NumberField::DiscTotal,
        fields.disc_total,
        "disc_total",
        merge_mode,
        written,
    );
    write_text(
        tag,
        ItemKey::Bpm,
        fields.bpm.map(|value| value.to_string()).as_deref(),
        "bpm",
        merge_mode,
        written,
        warnings,
    );
    write_text(
        tag,
        ItemKey::InitialKey,
        fields.musical_key.as_deref(),
        "musical_key",
        merge_mode,
        written,
        warnings,
    );
    write_text(
        tag,
        ItemKey::Isrc,
        fields.isrc.as_deref(),
        "isrc",
        merge_mode,
        written,
        warnings,
    );
    write_text(
        tag,
        ItemKey::MusicBrainzRecordingId,
        fields.musicbrainz_recording_id.as_deref(),
        "musicbrainz_recording_id",
        merge_mode,
        written,
        warnings,
    );
    write_text(
        tag,
        ItemKey::MusicBrainzTrackId,
        fields.musicbrainz_track_id.as_deref(),
        "musicbrainz_track_id",
        merge_mode,
        written,
        warnings,
    );
    write_text(
        tag,
        ItemKey::MusicBrainzReleaseId,
        fields.musicbrainz_release_id.as_deref(),
        "musicbrainz_release_id",
        merge_mode,
        written,
        warnings,
    );
    write_text(
        tag,
        ItemKey::MusicBrainzReleaseGroupId,
        fields.musicbrainz_release_group_id.as_deref(),
        "musicbrainz_release_group_id",
        merge_mode,
        written,
        warnings,
    );
    write_text(
        tag,
        ItemKey::MusicBrainzArtistId,
        fields.musicbrainz_artist_id.as_deref(),
        "musicbrainz_artist_id",
        merge_mode,
        written,
        warnings,
    );
    write_text(
        tag,
        ItemKey::MusicBrainzReleaseArtistId,
        fields.musicbrainz_release_artist_id.as_deref(),
        "musicbrainz_release_artist_id",
        merge_mode,
        written,
        warnings,
    );
    write_text(
        tag,
        ItemKey::MusicBrainzWorkId,
        fields.musicbrainz_work_id.as_deref(),
        "musicbrainz_work_id",
        merge_mode,
        written,
        warnings,
    );
    write_replay_gain(
        tag,
        ItemKey::ReplayGainTrackGain,
        fields.replay_gain_track_gain,
        true,
        "replay_gain_track_gain",
        merge_mode,
        written,
        warnings,
    );
    write_replay_gain(
        tag,
        ItemKey::ReplayGainTrackPeak,
        fields.replay_gain_track_peak,
        false,
        "replay_gain_track_peak",
        merge_mode,
        written,
        warnings,
    );
    write_replay_gain(
        tag,
        ItemKey::ReplayGainAlbumGain,
        fields.replay_gain_album_gain,
        true,
        "replay_gain_album_gain",
        merge_mode,
        written,
        warnings,
    );
    write_replay_gain(
        tag,
        ItemKey::ReplayGainAlbumPeak,
        fields.replay_gain_album_peak,
        false,
        "replay_gain_album_peak",
        merge_mode,
        written,
        warnings,
    );
}

#[allow(clippy::too_many_arguments)]
fn write_text(
    tag: &mut Tag,
    key: ItemKey,
    value: Option<&str>,
    field: &str,
    merge_mode: MetadataMergeMode,
    written: &mut Vec<String>,
    warnings: &mut Vec<String>,
) {
    let Some(value) = value.and_then(normalized) else {
        return;
    };
    let existing = tag.get_string(key);
    if merge_mode == MetadataMergeMode::FillMissing && existing.is_some_and(is_reasonable) {
        return;
    }
    if tag.insert_text(key, value.to_string()) {
        written.push(field.to_string());
    } else {
        warnings.push(format!("{field} is unsupported by this tag format"));
    }
}

enum NumberField {
    Track,
    TrackTotal,
    Disc,
    DiscTotal,
}

fn write_number(
    tag: &mut Tag,
    number_field: NumberField,
    value: Option<u32>,
    field: &str,
    merge_mode: MetadataMergeMode,
    written: &mut Vec<String>,
) {
    let Some(value) = value.filter(|value| *value > 0) else {
        return;
    };
    let existing = match number_field {
        NumberField::Track => tag.track(),
        NumberField::TrackTotal => tag.track_total(),
        NumberField::Disc => tag.disk(),
        NumberField::DiscTotal => tag.disk_total(),
    };
    if merge_mode == MetadataMergeMode::FillMissing && existing.is_some_and(|value| value > 0) {
        return;
    }
    match number_field {
        NumberField::Track => tag.set_track(value),
        NumberField::TrackTotal => tag.set_track_total(value),
        NumberField::Disc => tag.set_disk(value),
        NumberField::DiscTotal => tag.set_disk_total(value),
    }
    written.push(field.to_string());
}

#[allow(clippy::too_many_arguments)]
fn write_replay_gain(
    tag: &mut Tag,
    key: ItemKey,
    value: Option<f64>,
    gain: bool,
    field: &str,
    merge_mode: MetadataMergeMode,
    written: &mut Vec<String>,
    warnings: &mut Vec<String>,
) {
    let value = value.filter(|value| value.is_finite()).map(|value| {
        if gain {
            format!("{value:+.2} dB")
        } else {
            format!("{value:.6}")
        }
    });
    write_text(
        tag,
        key,
        value.as_deref(),
        field,
        merge_mode,
        written,
        warnings,
    );
}

fn apply_artwork(
    tag: &mut Tag,
    artwork: Option<&ArtworkWriteRequest>,
    merge_mode: MetadataMergeMode,
    capabilities: &MetadataWriteCapabilities,
    written: &mut Vec<String>,
    warnings: &mut Vec<String>,
) {
    let Some(artwork) = artwork else {
        return;
    };
    if !capabilities.artwork {
        warnings.push("artwork is unsupported by this format".to_string());
        return;
    }
    if artwork.data.is_empty() {
        warnings.push("empty artwork was skipped".to_string());
        return;
    }
    if artwork.data.len() > MAX_WRITE_ARTWORK_BYTES {
        warnings.push(format!(
            "artwork exceeds the {} byte embedding limit",
            MAX_WRITE_ARTWORK_BYTES
        ));
        return;
    }
    if tag
        .pictures()
        .iter()
        .any(|picture| picture.data() == artwork.data)
    {
        return;
    }
    if merge_mode == MetadataMergeMode::FillMissing && !tag.pictures().is_empty() {
        return;
    }

    let mut cursor = Cursor::new(&artwork.data);
    let Ok(mut picture) = Picture::from_reader(&mut cursor) else {
        warnings.push("invalid JPEG/PNG artwork was skipped".to_string());
        return;
    };
    let detected_mime = picture.mime_type().map(|value| value.as_str());
    if !matches!(detected_mime, Some("image/jpeg" | "image/png")) {
        warnings.push("only JPEG and PNG artwork can be embedded".to_string());
        return;
    }
    if artwork
        .mime_type
        .as_deref()
        .filter(|value| !value.eq_ignore_ascii_case("image/jpg"))
        .is_some_and(|declared| detected_mime != Some(declared))
    {
        warnings.push("artwork MIME type did not match its binary signature".to_string());
        return;
    }
    picture.set_pic_type(PictureType::CoverFront);
    if merge_mode == MetadataMergeMode::PreferSnapshot {
        tag.remove_picture_type(PictureType::CoverFront);
    }
    tag.push_picture(picture);
    written.push("artwork".to_string());
}

fn apply_embedded_lyrics(
    tag: &mut Tag,
    lyrics: Option<&LyricsWriteRequest>,
    merge_mode: MetadataMergeMode,
    capabilities: &MetadataWriteCapabilities,
    written: &mut Vec<String>,
    warnings: &mut Vec<String>,
) {
    let Some(content) = lyrics
        .and_then(|lyrics| lyrics.embedded.as_deref())
        .and_then(normalized)
    else {
        return;
    };
    if !capabilities.embedded_lyrics {
        warnings.push("embedded lyrics are unsupported by this format".to_string());
        return;
    }
    let existing = [ItemKey::Lyrics, ItemKey::UnsyncLyrics]
        .into_iter()
        .any(|key| tag.get_string(key).is_some_and(is_reasonable));
    if merge_mode == MetadataMergeMode::FillMissing && existing {
        return;
    }
    let preferred_key = if tag.tag_type() == TagType::Id3v2 {
        ItemKey::UnsyncLyrics
    } else {
        ItemKey::Lyrics
    };
    let fallback_key = if preferred_key == ItemKey::Lyrics {
        ItemKey::UnsyncLyrics
    } else {
        ItemKey::Lyrics
    };
    if merge_mode == MetadataMergeMode::PreferSnapshot {
        tag.remove_key(ItemKey::Lyrics);
        tag.remove_key(ItemKey::UnsyncLyrics);
    }
    if tag.insert_text(preferred_key, content.to_string())
        || tag.insert_text(fallback_key, content.to_string())
    {
        written.push("lyrics".to_string());
    } else {
        warnings.push("embedded lyrics are unsupported by this tag format".to_string());
    }
}

fn write_requested_sidecars(
    audio_path: &Path,
    lyrics: Option<&LyricsWriteRequest>,
    written: &mut Vec<String>,
    warnings: &mut Vec<String>,
) -> bool {
    let Some(lyrics) = lyrics.filter(|lyrics| lyrics.save_sidecars) else {
        return false;
    };
    let mut changed = false;
    for (extension, content, field) in [
        ("ttml", lyrics.ttml.as_deref(), "sidecar_ttml"),
        ("lrc", lyrics.lrc.as_deref(), "sidecar_lrc"),
    ] {
        let Some(content) = content.and_then(normalized) else {
            continue;
        };
        let sidecar_path = audio_path.with_extension(extension);
        match write_atomic_text(&sidecar_path, content) {
            Ok(true) => {
                changed = true;
                written.push(field.to_string());
            }
            Ok(false) => {}
            Err(error) => warnings.push(format!(
                "failed to save {} sidecar: {error}",
                extension.to_ascii_uppercase()
            )),
        }
    }
    changed
}

fn write_atomic_text(path: &Path, content: &str) -> std::io::Result<bool> {
    if fs::read_to_string(path).ok().as_deref() == Some(content) {
        return Ok(false);
    }
    let temporary = path.with_extension(format!(
        "{}.tmp",
        path.extension()
            .and_then(|value| value.to_str())
            .unwrap_or("lyrics")
    ));
    let _ = fs::remove_file(&temporary);
    let mut file = OpenOptions::new()
        .create_new(true)
        .write(true)
        .open(&temporary)?;
    file.write_all(content.as_bytes())?;
    file.sync_all()?;
    atomic_replace_file(&temporary, path)?;
    Ok(true)
}

#[cfg(not(windows))]
fn atomic_replace_file(temporary: &Path, destination: &Path) -> std::io::Result<()> {
    fs::rename(temporary, destination)
}

#[cfg(windows)]
fn atomic_replace_file(temporary: &Path, destination: &Path) -> std::io::Result<()> {
    use std::{os::windows::ffi::OsStrExt, ptr};
    use windows_sys::Win32::Storage::FileSystem::{ReplaceFileW, REPLACEFILE_WRITE_THROUGH};

    if !destination.exists() {
        return fs::rename(temporary, destination);
    }
    let destination = destination
        .as_os_str()
        .encode_wide()
        .chain(Some(0))
        .collect::<Vec<_>>();
    let temporary = temporary
        .as_os_str()
        .encode_wide()
        .chain(Some(0))
        .collect::<Vec<_>>();
    let replaced = unsafe {
        ReplaceFileW(
            destination.as_ptr(),
            temporary.as_ptr(),
            ptr::null(),
            REPLACEFILE_WRITE_THROUGH,
            ptr::null_mut(),
            ptr::null_mut(),
        )
    };
    if replaced == 0 {
        Err(std::io::Error::last_os_error())
    } else {
        Ok(())
    }
}

fn verify_audio_properties(
    before: &NormalizedMetadata,
    after: &NormalizedMetadata,
) -> Result<(), MetadataWriteError> {
    let duration_tolerance = (before.duration_ms / 100).max(1_000);
    if before.duration_ms.abs_diff(after.duration_ms) > duration_tolerance {
        return Err(MetadataWriteError::Verification(format!(
            "duration changed from {} ms to {} ms",
            before.duration_ms, after.duration_ms
        )));
    }
    for (name, before_value, after_value) in [
        (
            "sample rate",
            before.sample_rate.map(u64::from),
            after.sample_rate.map(u64::from),
        ),
        (
            "channels",
            before.channels.map(u64::from),
            after.channels.map(u64::from),
        ),
        (
            "bit depth",
            before.bit_depth.map(u64::from),
            after.bit_depth.map(u64::from),
        ),
    ] {
        if before_value.is_some() && before_value != after_value {
            return Err(MetadataWriteError::Verification(format!(
                "{name} changed from {before_value:?} to {after_value:?}"
            )));
        }
    }
    Ok(())
}

fn verify_written_values(
    request: &MetadataWriteRequest,
    written: &[String],
    verified: &NormalizedMetadata,
) -> Result<(), MetadataWriteError> {
    if written.iter().any(|field| field == "title")
        && normalized_option(verified.title.as_deref())
            != normalized_option(request.metadata.title.as_deref())
    {
        return Err(MetadataWriteError::Verification(
            "written title could not be read back".to_string(),
        ));
    }
    if written.iter().any(|field| field == "artists") && verified.artist.is_none() {
        return Err(MetadataWriteError::Verification(
            "written artist could not be read back".to_string(),
        ));
    }
    if written.iter().any(|field| field == "artwork") && !verified.has_embedded_artwork {
        return Err(MetadataWriteError::Verification(
            "written artwork could not be read back".to_string(),
        ));
    }
    if written.iter().any(|field| field == "lyrics") && verified.lyrics.is_none() {
        return Err(MetadataWriteError::Verification(
            "written lyrics could not be read back".to_string(),
        ));
    }
    Ok(())
}

fn normalized(value: &str) -> Option<&str> {
    let value = value.trim();
    (!value.is_empty()).then_some(value)
}

fn normalized_option(value: Option<&str>) -> Option<&str> {
    value.and_then(normalized)
}

fn is_reasonable(value: &str) -> bool {
    let value = value.trim();
    if value.is_empty() {
        return false;
    }
    let normalized = value.to_ascii_lowercase();
    if matches!(
        normalized.as_str(),
        "unknown" | "unknown title" | "unknown artist" | "untitled" | "n/a"
    ) {
        return false;
    }
    if let Some(number) = normalized.strip_prefix("track ") {
        return number.trim().parse::<u32>().is_err();
    }
    true
}
