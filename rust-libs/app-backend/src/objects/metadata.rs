use crate::schema::StorageEntryLoc;

#[derive(Debug, Clone, Copy, uniffi::Record)]
pub struct MetadataReadOptions {
    pub read_artwork: bool,
    pub read_lyrics: bool,
    pub read_raw_metadata: bool,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct RemoteRawMetadataEntry {
    pub key: String,
    pub value: String,
    pub locale: Option<String>,
    pub description: Option<String>,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct RemoteEmbeddedLyrics {
    pub content: String,
    pub synchronized: bool,
    pub language: Option<String>,
    pub description: Option<String>,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct RemoteArtwork {
    pub content_hash: String,
    pub local_path: String,
    pub thumbnail_path: Option<String>,
    pub width: Option<u32>,
    pub height: Option<u32>,
    pub mime_type: Option<String>,
    pub picture_type: Option<String>,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct RemoteMetadata {
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
    pub lyrics: Option<RemoteEmbeddedLyrics>,
    pub embedded_lyrics_kind: String,
    pub artwork: Option<RemoteArtwork>,
    pub has_embedded_artwork: bool,
    pub raw_metadata: Vec<RemoteRawMetadataEntry>,
    pub duration_ms: u64,
    pub sample_rate: Option<u32>,
    pub bit_depth: Option<u8>,
    pub channels: Option<u8>,
    pub channel_layout: Option<String>,
    pub overall_bitrate: Option<u32>,
    pub audio_bitrate: Option<u32>,
    pub codec: Option<String>,
    pub container: Option<String>,
    pub lossless: Option<bool>,
    pub metadata_request_count: u64,
    pub metadata_fetched_bytes: u64,
    pub metadata_elapsed_ms: u64,
    pub artwork_cached_bytes: u64,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct RemoteMetadataRequest {
    pub entry: StorageEntryLoc,
    pub size: u64,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct RemoteMetadataResult {
    pub request_index: u64,
    pub entry: StorageEntryLoc,
    pub metadata: Option<RemoteMetadata>,
    pub error: Option<String>,
}

#[derive(Debug, Clone, Copy, Default, uniffi::Enum)]
pub enum MetadataMergeMode {
    #[default]
    FillMissing,
    PreferSnapshot,
}

#[derive(Debug, Clone, Default, uniffi::Record)]
pub struct MetadataWriteFields {
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

#[derive(Debug, Clone, uniffi::Record)]
pub struct MetadataArtworkWriteRequest {
    pub local_path: String,
    pub mime_type: Option<String>,
}

#[derive(Debug, Clone, Default, uniffi::Record)]
pub struct MetadataLyricsWriteRequest {
    pub embedded: Option<String>,
    pub lrc: Option<String>,
    pub ttml: Option<String>,
    pub save_sidecars: bool,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct AudioMetadataWriteRequest {
    pub path: String,
    pub metadata: MetadataWriteFields,
    pub artwork: Option<MetadataArtworkWriteRequest>,
    pub lyrics: Option<MetadataLyricsWriteRequest>,
    pub merge_mode: MetadataMergeMode,
}

#[derive(Debug, Clone, Default, uniffi::Record)]
pub struct MetadataWriteCapabilities {
    pub format: String,
    pub metadata: bool,
    pub artwork: bool,
    pub embedded_lyrics: bool,
    pub synced_lyrics: bool,
    pub arbitrary_text: bool,
    pub sidecar_lyrics: bool,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct LocalMetadataSummary {
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
    pub has_embedded_artwork: bool,
    pub embedded_lyrics_kind: String,
    pub duration_ms: u64,
    pub sample_rate: Option<u32>,
    pub bit_depth: Option<u8>,
    pub channels: Option<u8>,
    pub channel_layout: Option<String>,
    pub overall_bitrate: Option<u32>,
    pub audio_bitrate: Option<u32>,
    pub codec: Option<String>,
    pub container: Option<String>,
    pub lossless: Option<bool>,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct AudioMetadataWriteResult {
    pub changed: bool,
    pub written_fields: Vec<String>,
    pub warnings: Vec<String>,
    pub capabilities: MetadataWriteCapabilities,
    pub verified: Option<LocalMetadataSummary>,
}
