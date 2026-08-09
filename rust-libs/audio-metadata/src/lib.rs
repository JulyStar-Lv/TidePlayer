use std::{
    collections::HashMap,
    io::{self, Read, Seek, SeekFrom},
    sync::{Arc, Mutex},
    time::Instant,
};

use bytes::Bytes;
use lofty::{
    config::ParseOptions,
    file::{AudioFile, FileType, TaggedFileExt},
    picture::{Picture, PictureInformation, PictureType},
    probe::Probe,
    properties::FileProperties,
    tag::{Accessor, ItemKey, ItemValue, Tag},
};
use storage_backend::{ByteRange, StorageBackend};

#[cfg(test)]
thread_local! {
    static ARTWORK_PARSE_ATTEMPTS: std::cell::Cell<usize> = std::cell::Cell::new(0);
    static NO_ARTWORK_PARSE_ATTEMPTS: std::cell::Cell<usize> = std::cell::Cell::new(0);
    static ARTWORK_EXTRACTION_ATTEMPTS: std::cell::Cell<usize> = std::cell::Cell::new(0);
    static LYRICS_EXTRACTION_ATTEMPTS: std::cell::Cell<usize> = std::cell::Cell::new(0);
    static RAW_METADATA_EXTRACTION_ATTEMPTS: std::cell::Cell<usize> = std::cell::Cell::new(0);
}

const MAX_TEXT_TAG_ENTRIES: usize = 2_048;
const MAX_TEXT_TAG_VALUE_BYTES: usize = 256 * 1024;
const MAX_TEXT_TAG_TOTAL_BYTES: usize = 1024 * 1024;
const MAX_ARTWORK_BYTES: usize = 4 * 1024 * 1024;

#[derive(Debug, Clone, Copy)]
pub struct ReaderLimits {
    pub block_size: u64,
    pub max_requests: usize,
    pub max_read_bytes: u64,
}

impl Default for ReaderLimits {
    fn default() -> Self {
        Self {
            block_size: 256 * 1024,
            max_requests: 64,
            max_read_bytes: 4 * 1024 * 1024,
        }
    }
}

#[derive(Debug, thiserror::Error)]
pub enum MetadataError {
    #[error("invalid reader limits")]
    InvalidLimits,
    #[error("range source failed: {0}")]
    Source(String),
    #[error("metadata scan exceeded request budget ({0})")]
    RequestBudgetExceeded(usize),
    #[error("metadata scan exceeded byte budget ({0})")]
    ByteBudgetExceeded(u64),
    #[error("metadata text tag exceeded value budget ({0} bytes)")]
    TextTagValueTooLarge(usize),
    #[error("metadata text tags exceeded total budget ({0} bytes)")]
    TextTagBudgetExceeded(usize),
    #[error("metadata text tags exceeded entry budget ({0})")]
    TextTagEntryBudgetExceeded(usize),
    #[error(transparent)]
    Io(#[from] io::Error),
    #[error(transparent)]
    Lofty(#[from] lofty::error::LoftyError),
}

pub trait RangeSource: Send + Sync {
    fn len(&self) -> u64;
    fn is_empty(&self) -> bool {
        self.len() == 0
    }
    fn read_range(&self, range: ByteRange) -> Result<Bytes, MetadataError>;
}

pub struct StorageRangeSource {
    backend: Arc<dyn StorageBackend + Send + Sync>,
    path: String,
    len: u64,
}

impl StorageRangeSource {
    pub fn new(
        backend: Arc<dyn StorageBackend + Send + Sync>,
        path: impl Into<String>,
        len: u64,
    ) -> Self {
        Self {
            backend,
            path: path.into(),
            len,
        }
    }
}

impl RangeSource for StorageRangeSource {
    fn len(&self) -> u64 {
        self.len
    }

    fn read_range(&self, range: ByteRange) -> Result<Bytes, MetadataError> {
        async_runtime::tokio_runtime()
            .block_on(self.backend.get_range(self.path.clone(), range))
            .map_err(|error| MetadataError::Source(error.to_string()))
    }
}

#[derive(Default)]
struct ReaderState {
    cache: HashMap<u64, Bytes>,
    requests: usize,
    read_bytes: u64,
}

pub struct RemoteRangeReader {
    source: Arc<dyn RangeSource>,
    limits: ReaderLimits,
    position: u64,
    state: Arc<Mutex<ReaderState>>,
}

impl RemoteRangeReader {
    pub fn new(source: Arc<dyn RangeSource>, limits: ReaderLimits) -> Result<Self, MetadataError> {
        if limits.block_size == 0 || limits.max_requests == 0 || limits.max_read_bytes == 0 {
            return Err(MetadataError::InvalidLimits);
        }
        Ok(Self {
            source,
            limits,
            position: 0,
            state: Arc::new(Mutex::new(ReaderState::default())),
        })
    }

    pub fn request_count(&self) -> usize {
        self.state.lock().unwrap().requests
    }

    pub fn fetched_bytes(&self) -> u64 {
        self.state.lock().unwrap().read_bytes
    }

    fn block(&self, block_start: u64) -> Result<Bytes, MetadataError> {
        let mut state = self.state.lock().unwrap();
        if let Some(bytes) = state.cache.get(&block_start) {
            return Ok(bytes.clone());
        }
        if state.requests >= self.limits.max_requests {
            return Err(MetadataError::RequestBudgetExceeded(
                self.limits.max_requests,
            ));
        }

        let file_len = self.source.len();
        let end_inclusive = block_start
            .saturating_add(self.limits.block_size - 1)
            .min(file_len.saturating_sub(1));
        let expected = end_inclusive - block_start + 1;
        if state.read_bytes.saturating_add(expected) > self.limits.max_read_bytes {
            return Err(MetadataError::ByteBudgetExceeded(
                self.limits.max_read_bytes,
            ));
        }

        let range = ByteRange::new(block_start, end_inclusive)
            .map_err(|error| MetadataError::Source(error.to_string()))?;
        let bytes = self.source.read_range(range)?;
        state.requests += 1;
        state.read_bytes += bytes.len() as u64;
        state.cache.insert(block_start, bytes.clone());
        Ok(bytes)
    }
}

impl Read for RemoteRangeReader {
    fn read(&mut self, buf: &mut [u8]) -> io::Result<usize> {
        if buf.is_empty() || self.position >= self.source.len() {
            return Ok(0);
        }

        let mut written = 0;
        while written < buf.len() && self.position < self.source.len() {
            let block_start = self.position / self.limits.block_size * self.limits.block_size;
            let block = self
                .block(block_start)
                .map_err(|error| io::Error::other(error.to_string()))?;
            let offset = (self.position - block_start) as usize;
            if offset >= block.len() {
                break;
            }
            let available = block.len() - offset;
            let remaining = buf.len() - written;
            let count = available.min(remaining);
            buf[written..written + count].copy_from_slice(&block[offset..offset + count]);
            self.position += count as u64;
            written += count;
        }
        Ok(written)
    }
}

impl Seek for RemoteRangeReader {
    fn seek(&mut self, position: SeekFrom) -> io::Result<u64> {
        let next = match position {
            SeekFrom::Start(value) => value as i128,
            SeekFrom::Current(value) => self.position as i128 + value as i128,
            SeekFrom::End(value) => self.source.len() as i128 + value as i128,
        };
        if next < 0 || next > u64::MAX as i128 {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "invalid seek position",
            ));
        }
        self.position = next as u64;
        Ok(self.position)
    }
}

#[derive(Debug, Clone, Default, PartialEq)]
pub struct RawMetadataEntry {
    pub key: String,
    pub value: String,
    pub locale: Option<String>,
    pub description: Option<String>,
}

#[derive(Debug, Clone, Default, PartialEq)]
pub struct EmbeddedLyrics {
    pub content: String,
    pub synchronized: bool,
    pub language: Option<String>,
    pub description: Option<String>,
}

#[derive(Debug, Clone, Default, PartialEq)]
pub struct EmbeddedArtwork {
    pub data: Vec<u8>,
    pub mime_type: Option<String>,
    pub picture_type: String,
    pub width: Option<u32>,
    pub height: Option<u32>,
}

#[derive(Debug, Clone, Default, PartialEq)]
pub struct NormalizedMetadata {
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
    pub lyrics: Option<EmbeddedLyrics>,
    pub embedded_lyrics_kind: String,
    pub artwork: Option<EmbeddedArtwork>,
    pub has_embedded_artwork: bool,
    pub raw_metadata: Vec<RawMetadataEntry>,
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

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct MetadataReadOptions {
    pub read_artwork: bool,
    pub read_lyrics: bool,
    pub read_raw_metadata: bool,
}

impl Default for MetadataReadOptions {
    fn default() -> Self {
        Self {
            read_artwork: true,
            read_lyrics: true,
            read_raw_metadata: true,
        }
    }
}

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub struct MetadataReadStats {
    pub request_count: u64,
    pub fetched_bytes: u64,
    pub elapsed_ms: u64,
}

#[derive(Debug, Clone, PartialEq)]
pub struct MetadataReadResult {
    pub metadata: NormalizedMetadata,
    pub stats: MetadataReadStats,
}

pub fn read_metadata(
    source: Arc<dyn RangeSource>,
    limits: ReaderLimits,
) -> Result<NormalizedMetadata, MetadataError> {
    read_metadata_with_options(source, limits, MetadataReadOptions::default())
        .map(|result| result.metadata)
}

pub fn read_metadata_with_options(
    source: Arc<dyn RangeSource>,
    limits: ReaderLimits,
    options: MetadataReadOptions,
) -> Result<MetadataReadResult, MetadataError> {
    let started_at = Instant::now();
    let (first_result, first_stats) =
        read_metadata_attempt(source.clone(), limits, options, options.read_artwork)?;
    let (metadata, mut stats) = match first_result {
        Ok(metadata) => (metadata, first_stats),
        Err(error) if options.read_artwork && is_reader_budget_error(&error) => {
            let retry_options = MetadataReadOptions {
                read_artwork: false,
                ..options
            };
            let (retry_result, retry_stats) =
                read_metadata_attempt(source, limits, retry_options, false)?;
            let metadata = retry_result?;
            (
                metadata,
                MetadataReadStats {
                    request_count: first_stats.request_count + retry_stats.request_count,
                    fetched_bytes: first_stats.fetched_bytes + retry_stats.fetched_bytes,
                    elapsed_ms: 0,
                },
            )
        }
        Err(error) => return Err(error),
    };
    stats.elapsed_ms = started_at
        .elapsed()
        .as_millis()
        .try_into()
        .unwrap_or(u64::MAX);
    Ok(MetadataReadResult { metadata, stats })
}

fn read_metadata_attempt(
    source: Arc<dyn RangeSource>,
    limits: ReaderLimits,
    options: MetadataReadOptions,
    read_cover_art: bool,
) -> Result<(Result<NormalizedMetadata, MetadataError>, MetadataReadStats), MetadataError> {
    #[cfg(test)]
    if read_cover_art {
        ARTWORK_PARSE_ATTEMPTS.with(|count| count.set(count.get() + 1));
    } else {
        NO_ARTWORK_PARSE_ATTEMPTS.with(|count| count.set(count.get() + 1));
    }
    let reader = RemoteRangeReader::new(source, limits)?;
    let state = reader.state.clone();
    let result = (|| {
        let tagged_file = Probe::new(reader)
            .options(
                ParseOptions::new()
                    .read_cover_art(read_cover_art)
                    .read_cover_art_presence(!read_cover_art),
            )
            .guess_file_type()?
            .read()?;
        let properties = tagged_file.properties();
        let file_type = tagged_file.file_type();
        let has_embedded_artwork = tagged_file
            .tags()
            .iter()
            .any(|tag| !tag.pictures().is_empty());
        let tag = tagged_file
            .primary_tag()
            .or_else(|| tagged_file.first_tag());

        let mut metadata = normalize_metadata(tag, properties, file_type, options)?;
        metadata.has_embedded_artwork = has_embedded_artwork;
        Ok(metadata)
    })();
    let state = state.lock().unwrap();
    let stats = MetadataReadStats {
        request_count: state.requests as u64,
        fetched_bytes: state.read_bytes,
        elapsed_ms: 0,
    };

    Ok((result, stats))
}

fn is_reader_budget_error(error: &MetadataError) -> bool {
    match error {
        MetadataError::RequestBudgetExceeded(_) | MetadataError::ByteBudgetExceeded(_) => true,
        MetadataError::Io(error) => is_reader_budget_error_message(&error.to_string()),
        MetadataError::Lofty(error) => is_reader_budget_error_message(&error.to_string()),
        _ => false,
    }
}

fn is_reader_budget_error_message(message: &str) -> bool {
    message.contains("metadata scan exceeded request budget")
        || message.contains("metadata scan exceeded byte budget")
}

fn normalize_metadata(
    tag: Option<&Tag>,
    properties: &FileProperties,
    file_type: FileType,
    options: MetadataReadOptions,
) -> Result<NormalizedMetadata, MetadataError> {
    let artist = tag.and_then(|tag| tag.artist().map(|value| value.into_owned()));
    let mut artists: Vec<String> = tag
        .map(|tag| {
            tag.get_strings(ItemKey::TrackArtists)
                .map(str::to_owned)
                .collect()
        })
        .unwrap_or_default();
    if let Some(primary) = artist.as_ref().filter(|value| !value.is_empty()) {
        if !artists.contains(primary) {
            artists.insert(0, primary.clone());
        }
    }
    let raw_metadata = match (options.read_raw_metadata, tag) {
        (true, Some(tag)) => extract_raw_metadata(tag)?,
        _ => Vec::new(),
    };
    let lyrics = options
        .read_lyrics
        .then(|| tag.and_then(extract_lyrics))
        .flatten();
    let embedded_lyrics_kind = tag
        .and_then(classify_tag_lyrics)
        .unwrap_or(EmbeddedLyricsKind::None)
        .as_str()
        .to_string();
    let artwork = options
        .read_artwork
        .then(|| tag.and_then(extract_artwork))
        .flatten();
    let has_embedded_artwork = tag.is_some_and(|tag| !tag.pictures().is_empty());
    let (codec, container, lossless) = audio_format(file_type);

    Ok(NormalizedMetadata {
        title: tag.and_then(|tag| tag.title().map(|value| value.into_owned())),
        artist,
        artists,
        album_artist: tag
            .and_then(|tag| tag.get_string(ItemKey::AlbumArtist))
            .map(str::to_owned),
        album: tag.and_then(|tag| tag.album().map(|value| value.into_owned())),
        composer: tag.and_then(|tag| text(tag, ItemKey::Composer)),
        lyricist: tag.and_then(|tag| text(tag, ItemKey::Lyricist)),
        conductor: tag.and_then(|tag| text(tag, ItemKey::Conductor)),
        genre: tag.and_then(|tag| tag.genre().map(|value| value.into_owned())),
        grouping: tag.and_then(|tag| text(tag, ItemKey::ContentGroup)),
        comment: tag.and_then(|tag| text(tag, ItemKey::Comment)),
        copyright: tag.and_then(|tag| text(tag, ItemKey::CopyrightMessage)),
        publisher: tag
            .and_then(|tag| text(tag, ItemKey::Publisher).or_else(|| text(tag, ItemKey::Label))),
        date: tag.and_then(|tag| tag.date().map(|value| value.to_string())),
        original_release_date: tag.and_then(|tag| text(tag, ItemKey::OriginalReleaseDate)),
        track_number: tag.and_then(|tag| tag.track()),
        track_total: tag.and_then(|tag| tag.track_total()),
        disc_number: tag.and_then(|tag| tag.disk()),
        disc_total: tag.and_then(|tag| tag.disk_total()),
        bpm: tag.and_then(|tag| {
            text(tag, ItemKey::Bpm)
                .or_else(|| text(tag, ItemKey::IntegerBpm))
                .and_then(|value| value.parse().ok())
        }),
        musical_key: tag.and_then(|tag| text(tag, ItemKey::InitialKey)),
        isrc: tag.and_then(|tag| text(tag, ItemKey::Isrc)),
        musicbrainz_recording_id: tag.and_then(|tag| text(tag, ItemKey::MusicBrainzRecordingId)),
        musicbrainz_track_id: tag.and_then(|tag| text(tag, ItemKey::MusicBrainzTrackId)),
        musicbrainz_release_id: tag.and_then(|tag| text(tag, ItemKey::MusicBrainzReleaseId)),
        musicbrainz_release_group_id: tag
            .and_then(|tag| text(tag, ItemKey::MusicBrainzReleaseGroupId)),
        musicbrainz_artist_id: tag.and_then(|tag| text(tag, ItemKey::MusicBrainzArtistId)),
        musicbrainz_release_artist_id: tag
            .and_then(|tag| text(tag, ItemKey::MusicBrainzReleaseArtistId)),
        musicbrainz_work_id: tag.and_then(|tag| text(tag, ItemKey::MusicBrainzWorkId)),
        replay_gain_track_gain: tag.and_then(|tag| replay_gain(tag, ItemKey::ReplayGainTrackGain)),
        replay_gain_track_peak: tag.and_then(|tag| replay_gain(tag, ItemKey::ReplayGainTrackPeak)),
        replay_gain_album_gain: tag.and_then(|tag| replay_gain(tag, ItemKey::ReplayGainAlbumGain)),
        replay_gain_album_peak: tag.and_then(|tag| replay_gain(tag, ItemKey::ReplayGainAlbumPeak)),
        lyrics,
        embedded_lyrics_kind,
        artwork,
        has_embedded_artwork,
        raw_metadata,
        duration_ms: properties.duration().as_millis() as u64,
        sample_rate: properties.sample_rate(),
        bit_depth: properties.bit_depth(),
        channels: properties.channels(),
        channel_layout: properties.channel_mask().map(|value| format!("{value:?}")),
        overall_bitrate: properties.overall_bitrate(),
        audio_bitrate: properties.audio_bitrate(),
        codec: Some(codec.to_string()),
        container: Some(container.to_string()),
        lossless,
    })
}

fn extract_artwork(tag: &Tag) -> Option<EmbeddedArtwork> {
    #[cfg(test)]
    ARTWORK_EXTRACTION_ATTEMPTS.with(|count| count.set(count.get() + 1));
    let picture = tag.get_picture_type(PictureType::CoverFront).or_else(|| {
        tag.pictures()
            .iter()
            .find(|picture| !picture.data().is_empty())
    })?;
    if picture.data().is_empty() || picture.data().len() > MAX_ARTWORK_BYTES {
        return None;
    }
    Some(embedded_artwork(picture))
}

fn embedded_artwork(picture: &Picture) -> EmbeddedArtwork {
    let info = PictureInformation::from_picture(picture).ok();
    EmbeddedArtwork {
        data: picture.data().to_vec(),
        mime_type: picture
            .mime_type()
            .map(|mime_type| mime_type.as_str().to_owned()),
        picture_type: format!("{:?}", picture.pic_type()),
        width: info.and_then(|info| non_zero_u32(info.width)),
        height: info.and_then(|info| non_zero_u32(info.height)),
    }
}

fn non_zero_u32(value: u32) -> Option<u32> {
    (value > 0).then_some(value)
}

fn text(tag: &Tag, key: ItemKey) -> Option<String> {
    tag.get_string(key)
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .map(str::to_owned)
}

fn replay_gain(tag: &Tag, key: ItemKey) -> Option<f64> {
    text(tag, key).and_then(|value| {
        value
            .trim_end_matches(|character: char| character.is_ascii_alphabetic())
            .trim()
            .parse()
            .ok()
    })
}

fn extract_lyrics(tag: &Tag) -> Option<EmbeddedLyrics> {
    #[cfg(test)]
    LYRICS_EXTRACTION_ATTEMPTS.with(|count| count.set(count.get() + 1));
    let (kind, item) = best_tag_lyrics(tag)?;
    let content = item.value().text()?;
    Some(EmbeddedLyrics {
        content: content.to_owned(),
        synchronized: kind.is_synchronized(),
        language: language(item.lang()),
        description: non_empty(item.description()),
    })
}

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, PartialOrd, Ord)]
enum EmbeddedLyricsKind {
    #[default]
    None,
    Plain,
    LineTimed,
    WordTimed,
    Ttml,
}

impl EmbeddedLyricsKind {
    fn as_str(self) -> &'static str {
        match self {
            Self::None => "None",
            Self::Plain => "Plain",
            Self::LineTimed => "LineTimed",
            Self::WordTimed => "WordTimed",
            Self::Ttml => "Ttml",
        }
    }

    fn is_synchronized(self) -> bool {
        matches!(self, Self::LineTimed | Self::WordTimed | Self::Ttml)
    }
}

fn classify_tag_lyrics(tag: &Tag) -> Option<EmbeddedLyricsKind> {
    best_tag_lyrics(tag).map(|(kind, _)| kind)
}

fn best_tag_lyrics(tag: &Tag) -> Option<(EmbeddedLyricsKind, &lofty::tag::TagItem)> {
    let mut best = None;
    for key in [ItemKey::Lyrics, ItemKey::UnsyncLyrics] {
        for item in tag.get_items(key) {
            let Some(content) = item.value().text().filter(|value| !value.trim().is_empty()) else {
                continue;
            };
            let kind = classify_lyrics(content);
            if best.is_none_or(|(best_kind, _)| kind > best_kind) {
                best = Some((kind, item));
            }
        }
    }
    best
}

fn classify_lyrics(content: &str) -> EmbeddedLyricsKind {
    if content.contains("http://www.w3.org/ns/ttml") {
        EmbeddedLyricsKind::Ttml
    } else if looks_word_timed(content) {
        EmbeddedLyricsKind::WordTimed
    } else if looks_synchronized(content) {
        EmbeddedLyricsKind::LineTimed
    } else {
        EmbeddedLyricsKind::Plain
    }
}

fn looks_synchronized(content: &str) -> bool {
    content.lines().any(|line| {
        line.trim_start()
            .strip_prefix('[')
            .and_then(|line| line.split_once(']'))
            .is_some_and(|(timestamp, _)| timestamp.contains(':') || is_numeric_token(timestamp, 2))
    })
}

fn looks_word_timed(content: &str) -> bool {
    content.lines().any(|raw_line| {
        let line = raw_line.trim();
        let Some((line_timestamp, body)) =
            line.strip_prefix('[').and_then(|line| line.split_once(']'))
        else {
            return false;
        };
        if line_timestamp.contains(':') {
            return contains_timed_token(body, '<', '>', ':')
                || contains_timed_token(body, '[', ']', ':');
        }
        if line_timestamp
            .chars()
            .all(|character| character.is_ascii_digit())
            && contains_numeric_token(body, '(', ')', 2)
        {
            return true;
        }
        if !is_numeric_token(line_timestamp, 2) {
            return false;
        }
        contains_numeric_token(body, '<', '>', 3)
            || contains_numeric_token(body, '(', ')', 2)
            || contains_numeric_token(body, '(', ')', 3)
    })
}

fn contains_timed_token(value: &str, open: char, close: char, separator: char) -> bool {
    value.split(open).skip(1).any(|part| {
        part.split_once(close)
            .is_some_and(|(token, text)| token.contains(separator) && !text.is_empty())
    })
}

fn contains_numeric_token(value: &str, open: char, close: char, parts: usize) -> bool {
    value.split(open).skip(1).any(|part| {
        part.split_once(close)
            .is_some_and(|(token, text)| is_numeric_token(token, parts) && !text.is_empty())
    })
}

fn is_numeric_token(value: &str, parts: usize) -> bool {
    let values = value.split(',').map(str::trim).collect::<Vec<_>>();
    values.len() == parts
        && values.iter().all(|part| {
            !part.is_empty() && part.chars().all(|character| character.is_ascii_digit())
        })
}

fn extract_raw_metadata(tag: &Tag) -> Result<Vec<RawMetadataEntry>, MetadataError> {
    #[cfg(test)]
    RAW_METADATA_EXTRACTION_ATTEMPTS.with(|count| count.set(count.get() + 1));
    let mut total_bytes = 0;
    let mut entries = Vec::new();
    for item in tag.items() {
        let value = match item.value() {
            ItemValue::Text(value) | ItemValue::Locator(value) => value,
            ItemValue::Binary(_) => continue,
        };
        if value.is_empty() {
            continue;
        }
        if value.len() > MAX_TEXT_TAG_VALUE_BYTES {
            return Err(MetadataError::TextTagValueTooLarge(value.len()));
        }
        total_bytes += value.len();
        if total_bytes > MAX_TEXT_TAG_TOTAL_BYTES {
            return Err(MetadataError::TextTagBudgetExceeded(total_bytes));
        }
        if entries.len() >= MAX_TEXT_TAG_ENTRIES {
            return Err(MetadataError::TextTagEntryBudgetExceeded(
                MAX_TEXT_TAG_ENTRIES,
            ));
        }
        entries.push(RawMetadataEntry {
            key: format!("{:?}", item.key()),
            value: value.clone(),
            locale: language(item.lang()),
            description: non_empty(item.description()),
        });
    }
    Ok(entries)
}

fn language(value: &[u8; 3]) -> Option<String> {
    (value != b"XXX")
        .then(|| String::from_utf8_lossy(value).into_owned())
        .filter(|value| !value.is_empty())
}

fn non_empty(value: &str) -> Option<String> {
    (!value.is_empty()).then(|| value.to_owned())
}

fn audio_format(file_type: FileType) -> (&'static str, &'static str, Option<bool>) {
    match file_type {
        FileType::Aac => ("AAC", "ADTS", Some(false)),
        FileType::Aiff => ("PCM", "AIFF", Some(true)),
        FileType::Ape => ("APE", "APE", Some(true)),
        FileType::Flac => ("FLAC", "FLAC", Some(true)),
        FileType::Mpeg => ("MPEG Audio", "MPEG", Some(false)),
        FileType::Mp4 => ("MPEG-4 Audio", "MP4", None),
        FileType::Mpc => ("Musepack", "MPC", Some(false)),
        FileType::Opus => ("Opus", "Ogg", Some(false)),
        FileType::Vorbis => ("Vorbis", "Ogg", Some(false)),
        FileType::Speex => ("Speex", "Ogg", Some(false)),
        FileType::Wav => ("PCM", "WAV", Some(true)),
        FileType::WavPack => ("WavPack", "WavPack", Some(true)),
        FileType::Custom(_) => ("Unknown", "Unknown", None),
        _ => ("Unknown", "Unknown", None),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use lofty::picture::{MimeType, Picture, PictureType};
    use lofty::tag::{TagItem, TagType};

    struct MemorySource(Bytes);

    impl RangeSource for MemorySource {
        fn len(&self) -> u64 {
            self.0.len() as u64
        }

        fn read_range(&self, range: ByteRange) -> Result<Bytes, MetadataError> {
            let start = range.start as usize;
            let end = (range.end_inclusive as usize + 1).min(self.0.len());
            Ok(self.0.slice(start..end))
        }
    }

    #[test]
    fn reads_and_seeks_with_block_cache() {
        let source = Arc::new(MemorySource(Bytes::from_static(b"0123456789")));
        let mut reader = RemoteRangeReader::new(
            source,
            ReaderLimits {
                block_size: 4,
                max_requests: 3,
                max_read_bytes: 12,
            },
        )
        .unwrap();

        let mut first = [0; 3];
        reader.read_exact(&mut first).unwrap();
        assert_eq!(&first, b"012");
        reader.seek(SeekFrom::Start(1)).unwrap();
        let mut cached = [0; 2];
        reader.read_exact(&mut cached).unwrap();
        assert_eq!(&cached, b"12");
        reader.seek(SeekFrom::End(-2)).unwrap();
        let mut tail = [0; 2];
        reader.read_exact(&mut tail).unwrap();
        assert_eq!(&tail, b"89");
        assert_eq!(reader.request_count(), 2);
        assert_eq!(reader.fetched_bytes(), 6);
    }

    #[test]
    fn enforces_byte_budget() {
        let source = Arc::new(MemorySource(Bytes::from_static(b"0123456789")));
        let mut reader = RemoteRangeReader::new(
            source,
            ReaderLimits {
                block_size: 4,
                max_requests: 3,
                max_read_bytes: 4,
            },
        )
        .unwrap();
        let mut data = [0; 5];
        let error = reader.read_exact(&mut data).unwrap_err();
        assert!(error.to_string().contains("byte budget"));
    }

    #[test]
    fn reads_wav_properties_through_range_reader() {
        let wav = minimal_pcm_wav();
        let metadata = read_metadata(
            Arc::new(MemorySource(Bytes::from(wav))),
            ReaderLimits {
                block_size: 16,
                max_requests: 16,
                max_read_bytes: 1024,
            },
        )
        .unwrap();

        assert_eq!(metadata.sample_rate, Some(8_000));
        assert_eq!(metadata.bit_depth, Some(16));
        assert_eq!(metadata.channels, Some(1));
        assert_eq!(metadata.codec.as_deref(), Some("PCM"));
        assert_eq!(metadata.container.as_deref(), Some("WAV"));
        assert_eq!(metadata.lossless, Some(true));
    }

    #[test]
    fn retries_flac_metadata_without_artwork_after_reader_budget_error() {
        reset_parse_attempt_counts();
        let flac = minimal_flac_with_picture(2 * 1024);
        let metadata = read_metadata(
            Arc::new(MemorySource(Bytes::from(flac))),
            ReaderLimits {
                block_size: 256,
                max_requests: 32,
                max_read_bytes: 512,
            },
        )
        .unwrap();

        assert_eq!(metadata.title.as_deref(), Some("Large Art"));
        assert_eq!(metadata.artist.as_deref(), Some("Artist"));
        assert_eq!(metadata.artwork, None);
        assert!(metadata.has_embedded_artwork);
        assert_eq!(metadata.sample_rate, Some(44_100));
        assert_eq!(metadata.channels, Some(2));
        assert_eq!(metadata.codec.as_deref(), Some("FLAC"));
        assert_eq!(parse_attempt_counts(), (1, 1));
    }

    #[test]
    fn disabled_artwork_starts_with_a_single_no_artwork_parse() {
        reset_parse_attempt_counts();
        let flac = minimal_flac_with_picture(2 * 1024);
        let result = read_metadata_with_options(
            Arc::new(MemorySource(Bytes::from(flac))),
            ReaderLimits {
                block_size: 256,
                max_requests: 32,
                max_read_bytes: 512,
            },
            MetadataReadOptions {
                read_artwork: false,
                read_lyrics: true,
                read_raw_metadata: true,
            },
        )
        .unwrap();

        assert_eq!(result.metadata.title.as_deref(), Some("Large Art"));
        assert_eq!(result.metadata.artwork, None);
        assert!(result.metadata.has_embedded_artwork);
        assert!(result.stats.request_count > 0);
        assert!(result.stats.fetched_bytes > 0);
        assert_eq!(parse_attempt_counts(), (0, 1));
    }

    #[test]
    fn disabled_options_skip_optional_extraction_work() {
        reset_extraction_attempt_counts();
        let mut tag = Tag::new(TagType::VorbisComments);
        tag.insert_text(ItemKey::TrackTitle, "Song".to_string());
        tag.insert_text(ItemKey::Comment, "x".repeat(MAX_TEXT_TAG_VALUE_BYTES + 1));
        tag.push(TagItem::new(
            ItemKey::Lyrics,
            ItemValue::Text("[00:01.00]Line".to_string()),
        ));
        tag.push_picture(
            Picture::unchecked(minimal_png(64, 64))
                .pic_type(PictureType::CoverFront)
                .mime_type(MimeType::Png)
                .build(),
        );

        let metadata = normalize_metadata(
            Some(&tag),
            &FileProperties::default(),
            FileType::Flac,
            MetadataReadOptions {
                read_artwork: false,
                read_lyrics: false,
                read_raw_metadata: false,
            },
        )
        .unwrap();

        assert_eq!(metadata.title.as_deref(), Some("Song"));
        assert_eq!(metadata.artwork, None);
        assert!(metadata.has_embedded_artwork);
        assert_eq!(metadata.lyrics, None);
        assert_eq!(metadata.embedded_lyrics_kind, "LineTimed");
        assert!(metadata.raw_metadata.is_empty());
        assert_eq!(extraction_attempt_counts(), (0, 0, 0));
    }

    #[test]
    fn classifies_word_timed_and_ttml_lyrics_without_extracting_content() {
        for (content, expected) in [
            (
                "[00:02.00]<00:02.000>Hello<00:02.500> world<00:03.000>",
                "WordTimed",
            ),
            ("[4]I (0,214)promise (214,345)you", "WordTimed"),
            (
                r#"<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="00:01.000" end="00:02.000">Line</p></div></body></tt>"#,
                "Ttml",
            ),
        ] {
            let mut tag = Tag::new(TagType::VorbisComments);
            tag.push(TagItem::new(
                ItemKey::Lyrics,
                ItemValue::Text(content.to_string()),
            ));

            let metadata = normalize_metadata(
                Some(&tag),
                &FileProperties::default(),
                FileType::Flac,
                MetadataReadOptions {
                    read_artwork: false,
                    read_lyrics: false,
                    read_raw_metadata: false,
                },
            )
            .unwrap();

            assert_eq!(metadata.lyrics, None);
            assert_eq!(metadata.embedded_lyrics_kind, expected);
        }
    }

    #[test]
    fn treats_ttml_stored_in_unsynchronized_tag_as_synchronized() {
        let mut tag = Tag::new(TagType::VorbisComments);
        tag.push(TagItem::new(
            ItemKey::Lyrics,
            ItemValue::Text("Plain lyrics".to_string()),
        ));
        tag.push(TagItem::new(
            ItemKey::UnsyncLyrics,
            ItemValue::Text(
                r#"<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="00:01.000" end="00:02.000">Line</p></div></body></tt>"#
                    .to_string(),
            ),
        ));

        let metadata = normalize_metadata(
            Some(&tag),
            &FileProperties::default(),
            FileType::Flac,
            MetadataReadOptions::default(),
        )
        .unwrap();

        assert_eq!(metadata.embedded_lyrics_kind, "Ttml");
        let lyrics = metadata.lyrics.expect("lyrics");
        assert!(lyrics.synchronized);
        assert!(lyrics.content.contains("http://www.w3.org/ns/ttml"));
    }

    #[test]
    fn disabled_optional_metadata_keeps_core_tags_and_audio_properties() {
        let mut tag = Tag::new(TagType::VorbisComments);
        tag.insert_text(ItemKey::TrackTitle, "Song".to_string());
        tag.insert_text(ItemKey::TrackArtist, "Artist".to_string());
        tag.insert_text(ItemKey::AlbumArtist, "Album Artist".to_string());
        tag.insert_text(ItemKey::AlbumTitle, "Album".to_string());
        tag.insert_text(ItemKey::Genre, "Jazz".to_string());
        tag.insert_text(ItemKey::RecordingDate, "2026-01-02".to_string());
        tag.insert_text(ItemKey::TrackNumber, "3".to_string());
        tag.insert_text(ItemKey::DiscNumber, "1".to_string());
        tag.insert_text(ItemKey::Isrc, "US-AAA-26-00001".to_string());
        tag.insert_text(ItemKey::MusicBrainzRecordingId, "recording-id".to_string());

        let metadata = normalize_metadata(
            Some(&tag),
            &FileProperties::new(
                std::time::Duration::from_secs(180),
                Some(1_000),
                Some(900),
                Some(48_000),
                Some(24),
                Some(2),
                None,
            ),
            FileType::Flac,
            MetadataReadOptions {
                read_artwork: false,
                read_lyrics: false,
                read_raw_metadata: false,
            },
        )
        .unwrap();

        assert_eq!(metadata.title.as_deref(), Some("Song"));
        assert_eq!(metadata.artist.as_deref(), Some("Artist"));
        assert_eq!(metadata.artists, vec!["Artist"]);
        assert_eq!(metadata.album_artist.as_deref(), Some("Album Artist"));
        assert_eq!(metadata.album.as_deref(), Some("Album"));
        assert_eq!(metadata.genre.as_deref(), Some("Jazz"));
        assert_eq!(metadata.date.as_deref(), Some("2026-01-02"));
        assert_eq!(metadata.track_number, Some(3));
        assert_eq!(metadata.disc_number, Some(1));
        assert_eq!(metadata.duration_ms, 180_000);
        assert_eq!(metadata.sample_rate, Some(48_000));
        assert_eq!(metadata.bit_depth, Some(24));
        assert_eq!(metadata.channels, Some(2));
        assert_eq!(metadata.overall_bitrate, Some(1_000));
        assert_eq!(metadata.audio_bitrate, Some(900));
        assert_eq!(metadata.codec.as_deref(), Some("FLAC"));
        assert_eq!(metadata.container.as_deref(), Some("FLAC"));
        assert_eq!(metadata.lossless, Some(true));
        assert_eq!(metadata.isrc.as_deref(), Some("US-AAA-26-00001"));
        assert_eq!(
            metadata.musicbrainz_recording_id.as_deref(),
            Some("recording-id")
        );
    }

    #[test]
    fn full_options_extract_all_optional_metadata() {
        reset_extraction_attempt_counts();
        let mut tag = Tag::new(TagType::VorbisComments);
        tag.insert_text(ItemKey::Comment, "raw value".to_string());
        tag.push(TagItem::new(
            ItemKey::Lyrics,
            ItemValue::Text("[00:01.00]Line".to_string()),
        ));
        tag.push_picture(
            Picture::unchecked(minimal_png(64, 64))
                .pic_type(PictureType::CoverFront)
                .mime_type(MimeType::Png)
                .build(),
        );

        let metadata = normalize_metadata(
            Some(&tag),
            &FileProperties::default(),
            FileType::Flac,
            MetadataReadOptions::default(),
        )
        .unwrap();

        assert!(metadata.artwork.is_some());
        assert!(metadata.has_embedded_artwork);
        assert!(metadata.lyrics.is_some());
        assert!(!metadata.raw_metadata.is_empty());
        assert_eq!(extraction_attempt_counts(), (1, 1, 1));
    }

    #[test]
    fn normalizes_extended_text_tags_lyrics_and_raw_metadata() {
        let mut tag = Tag::new(TagType::VorbisComments);
        tag.insert_text(ItemKey::TrackTitle, "Song".to_string());
        tag.insert_text(ItemKey::TrackArtist, "Primary".to_string());
        tag.push(TagItem::new(
            ItemKey::TrackArtists,
            ItemValue::Text("Guest".to_string()),
        ));
        tag.insert_text(ItemKey::Composer, "Composer".to_string());
        tag.insert_text(ItemKey::Lyricist, "Lyricist".to_string());
        tag.insert_text(ItemKey::Conductor, "Conductor".to_string());
        tag.insert_text(ItemKey::ContentGroup, "Suite".to_string());
        tag.insert_text(ItemKey::CopyrightMessage, "Copyright".to_string());
        tag.insert_text(ItemKey::Label, "Label".to_string());
        tag.insert_text(ItemKey::OriginalReleaseDate, "1999-01-01".to_string());
        tag.insert_text(ItemKey::Bpm, "128.5".to_string());
        tag.insert_text(ItemKey::InitialKey, "8A".to_string());
        tag.insert_text(ItemKey::Isrc, "US-AAA-26-00001".to_string());
        tag.insert_text(ItemKey::MusicBrainzRecordingId, "recording-id".to_string());
        tag.insert_text(ItemKey::ReplayGainTrackGain, "-7.25 dB".to_string());
        let mut lyrics = TagItem::new(
            ItemKey::Lyrics,
            ItemValue::Text("[00:01.00]Line".to_string()),
        );
        lyrics.set_lang(*b"eng");
        lyrics.set_description("main".to_string());
        tag.push(lyrics);

        let metadata = normalize_metadata(
            Some(&tag),
            &FileProperties::new(
                std::time::Duration::from_secs(180),
                Some(1_000),
                Some(900),
                Some(48_000),
                Some(24),
                Some(2),
                None,
            ),
            FileType::Flac,
            MetadataReadOptions::default(),
        )
        .unwrap();

        assert_eq!(metadata.title.as_deref(), Some("Song"));
        assert_eq!(metadata.artists, vec!["Primary", "Guest"]);
        assert_eq!(metadata.composer.as_deref(), Some("Composer"));
        assert_eq!(metadata.lyricist.as_deref(), Some("Lyricist"));
        assert_eq!(metadata.conductor.as_deref(), Some("Conductor"));
        assert_eq!(metadata.grouping.as_deref(), Some("Suite"));
        assert_eq!(metadata.publisher.as_deref(), Some("Label"));
        assert_eq!(
            metadata.original_release_date.as_deref(),
            Some("1999-01-01")
        );
        assert_eq!(metadata.bpm, Some(128.5));
        assert_eq!(metadata.musical_key.as_deref(), Some("8A"));
        assert_eq!(metadata.isrc.as_deref(), Some("US-AAA-26-00001"));
        assert_eq!(
            metadata.musicbrainz_recording_id.as_deref(),
            Some("recording-id")
        );
        assert_eq!(metadata.replay_gain_track_gain, Some(-7.25));
        assert_eq!(
            metadata.lyrics,
            Some(EmbeddedLyrics {
                content: "[00:01.00]Line".to_string(),
                synchronized: true,
                language: Some("eng".to_string()),
                description: Some("main".to_string()),
            })
        );
        assert!(metadata
            .raw_metadata
            .iter()
            .any(|entry| entry.key == "Composer" && entry.value == "Composer"));
        assert_eq!(metadata.codec.as_deref(), Some("FLAC"));
        assert_eq!(metadata.lossless, Some(true));
    }

    #[test]
    fn extracts_bounded_embedded_artwork() {
        let mut tag = Tag::new(TagType::VorbisComments);
        tag.push_picture(
            Picture::unchecked(minimal_png(320, 240))
                .pic_type(PictureType::CoverFront)
                .mime_type(MimeType::Png)
                .build(),
        );

        let metadata = normalize_metadata(
            Some(&tag),
            &FileProperties::default(),
            FileType::Flac,
            MetadataReadOptions::default(),
        )
        .unwrap();

        let artwork = metadata.artwork.expect("artwork should be extracted");
        assert_eq!(artwork.mime_type.as_deref(), Some("image/png"));
        assert_eq!(artwork.picture_type, "CoverFront");
        assert_eq!(artwork.width, Some(320));
        assert_eq!(artwork.height, Some(240));
        assert_eq!(artwork.data, minimal_png(320, 240));
    }

    #[test]
    fn extracts_embedded_artwork_above_legacy_two_megabyte_limit() {
        let artwork_bytes = vec![1; 2 * 1024 * 1024 + 1];
        let mut tag = Tag::new(TagType::VorbisComments);
        tag.push_picture(
            Picture::unchecked(artwork_bytes.clone())
                .pic_type(PictureType::CoverFront)
                .mime_type(MimeType::Jpeg)
                .build(),
        );

        let metadata = normalize_metadata(
            Some(&tag),
            &FileProperties::default(),
            FileType::Flac,
            MetadataReadOptions::default(),
        )
        .unwrap();

        assert_eq!(
            metadata.artwork.as_ref().map(|artwork| artwork.data.len()),
            Some(artwork_bytes.len())
        );
        assert!(metadata.has_embedded_artwork);
    }

    #[test]
    fn skips_oversized_embedded_artwork() {
        let mut tag = Tag::new(TagType::VorbisComments);
        tag.push_picture(
            Picture::unchecked(vec![1; MAX_ARTWORK_BYTES + 1])
                .pic_type(PictureType::CoverFront)
                .mime_type(MimeType::Jpeg)
                .build(),
        );

        let metadata = normalize_metadata(
            Some(&tag),
            &FileProperties::default(),
            FileType::Flac,
            MetadataReadOptions::default(),
        )
        .unwrap();

        assert_eq!(metadata.artwork, None);
        assert!(metadata.has_embedded_artwork);
    }

    #[test]
    fn disabled_artwork_skips_large_id3_picture_payload_and_keeps_presence() {
        let picture_size = 1024 * 1024;
        let mp3 = minimal_mp3_with_picture(picture_size);
        let result = read_metadata_with_options(
            Arc::new(MemorySource(Bytes::from(mp3))),
            ReaderLimits {
                block_size: 256,
                max_requests: 32,
                max_read_bytes: 32 * 1024,
            },
            MetadataReadOptions {
                read_artwork: false,
                read_lyrics: false,
                read_raw_metadata: false,
            },
        )
        .unwrap();

        assert!(result.metadata.has_embedded_artwork);
        assert_eq!(result.metadata.artwork, None);
        assert!(result.stats.fetched_bytes < picture_size as u64 / 8);
    }

    #[test]
    fn rejects_oversized_text_metadata() {
        let mut tag = Tag::new(TagType::VorbisComments);
        tag.insert_text(ItemKey::Comment, "x".repeat(MAX_TEXT_TAG_VALUE_BYTES + 1));

        let error = normalize_metadata(
            Some(&tag),
            &FileProperties::default(),
            FileType::Flac,
            MetadataReadOptions::default(),
        )
        .unwrap_err();

        assert!(matches!(error, MetadataError::TextTagValueTooLarge(_)));
    }

    fn minimal_pcm_wav() -> Vec<u8> {
        let data = [0_u8; 16];
        let mut wav = Vec::new();
        wav.extend_from_slice(b"RIFF");
        wav.extend_from_slice(&(36_u32 + data.len() as u32).to_le_bytes());
        wav.extend_from_slice(b"WAVEfmt ");
        wav.extend_from_slice(&16_u32.to_le_bytes());
        wav.extend_from_slice(&1_u16.to_le_bytes());
        wav.extend_from_slice(&1_u16.to_le_bytes());
        wav.extend_from_slice(&8_000_u32.to_le_bytes());
        wav.extend_from_slice(&16_000_u32.to_le_bytes());
        wav.extend_from_slice(&2_u16.to_le_bytes());
        wav.extend_from_slice(&16_u16.to_le_bytes());
        wav.extend_from_slice(b"data");
        wav.extend_from_slice(&(data.len() as u32).to_le_bytes());
        wav.extend_from_slice(&data);
        wav
    }

    fn minimal_mp3_with_picture(picture_size: usize) -> Vec<u8> {
        let mut picture = Vec::with_capacity(picture_size + 16);
        picture.push(0);
        picture.extend_from_slice(b"image/jpeg");
        picture.push(0);
        picture.push(3);
        picture.push(0);
        picture.resize(picture_size + 14, 0x5A);

        let mut tag = Vec::with_capacity(picture.len() + 20);
        tag.extend_from_slice(b"APIC");
        tag.extend_from_slice(&(picture.len() as u32).to_be_bytes());
        tag.extend_from_slice(&[0, 0]);
        tag.extend_from_slice(&picture);

        let mut mp3 = Vec::with_capacity(tag.len() + 10 + 834);
        mp3.extend_from_slice(b"ID3");
        mp3.extend_from_slice(&[3, 0, 0]);
        mp3.extend_from_slice(&synchsafe_u32(tag.len() as u32));
        mp3.extend_from_slice(&tag);

        let mut frame = vec![0; 417];
        frame[..4].copy_from_slice(&[0xFF, 0xFB, 0x90, 0x64]);
        mp3.extend_from_slice(&frame);
        mp3.extend_from_slice(&frame);
        mp3
    }

    fn synchsafe_u32(value: u32) -> [u8; 4] {
        [
            ((value >> 21) & 0x7F) as u8,
            ((value >> 14) & 0x7F) as u8,
            ((value >> 7) & 0x7F) as u8,
            (value & 0x7F) as u8,
        ]
    }

    fn reset_parse_attempt_counts() {
        ARTWORK_PARSE_ATTEMPTS.with(|count| count.set(0));
        NO_ARTWORK_PARSE_ATTEMPTS.with(|count| count.set(0));
    }

    fn parse_attempt_counts() -> (usize, usize) {
        (
            ARTWORK_PARSE_ATTEMPTS.with(|count| count.get()),
            NO_ARTWORK_PARSE_ATTEMPTS.with(|count| count.get()),
        )
    }

    fn reset_extraction_attempt_counts() {
        ARTWORK_EXTRACTION_ATTEMPTS.with(|count| count.set(0));
        LYRICS_EXTRACTION_ATTEMPTS.with(|count| count.set(0));
        RAW_METADATA_EXTRACTION_ATTEMPTS.with(|count| count.set(0));
    }

    fn extraction_attempt_counts() -> (usize, usize, usize) {
        (
            ARTWORK_EXTRACTION_ATTEMPTS.with(|count| count.get()),
            LYRICS_EXTRACTION_ATTEMPTS.with(|count| count.get()),
            RAW_METADATA_EXTRACTION_ATTEMPTS.with(|count| count.get()),
        )
    }

    fn minimal_flac_with_picture(picture_bytes: usize) -> Vec<u8> {
        let mut flac = Vec::new();
        flac.extend_from_slice(b"fLaC");
        append_flac_block(&mut flac, false, 0, &minimal_streaminfo());
        append_flac_block(
            &mut flac,
            false,
            4,
            &vorbis_comment(&[("TITLE", "Large Art"), ("ARTIST", "Artist")]),
        );
        append_flac_block(&mut flac, true, 6, &flac_picture(picture_bytes));
        flac
    }

    fn append_flac_block(target: &mut Vec<u8>, last: bool, block_type: u8, content: &[u8]) {
        assert!(content.len() <= 0xFF_FFFF);
        target.push(if last { 0x80 | block_type } else { block_type });
        target.push(((content.len() >> 16) & 0xFF) as u8);
        target.push(((content.len() >> 8) & 0xFF) as u8);
        target.push((content.len() & 0xFF) as u8);
        target.extend_from_slice(content);
    }

    fn minimal_streaminfo() -> [u8; 34] {
        let mut streaminfo = [0_u8; 34];
        streaminfo[0..2].copy_from_slice(&4096_u16.to_be_bytes());
        streaminfo[2..4].copy_from_slice(&4096_u16.to_be_bytes());

        let sample_rate = 44_100_u32;
        let channels_minus_one = 1_u32;
        let bits_per_sample_minus_one = 15_u32;
        let total_samples = 44_100_u64;
        let packed = (sample_rate << 12)
            | (channels_minus_one << 9)
            | (bits_per_sample_minus_one << 4)
            | ((total_samples >> 32) as u32 & 0x0F);
        streaminfo[10..14].copy_from_slice(&packed.to_be_bytes());
        streaminfo[14..18].copy_from_slice(&(total_samples as u32).to_be_bytes());
        streaminfo
    }

    fn vorbis_comment(entries: &[(&str, &str)]) -> Vec<u8> {
        let mut data = Vec::new();
        let vendor = b"MelodyTrove";
        data.extend_from_slice(&(vendor.len() as u32).to_le_bytes());
        data.extend_from_slice(vendor);
        data.extend_from_slice(&(entries.len() as u32).to_le_bytes());
        for (key, value) in entries {
            let comment = format!("{key}={value}");
            data.extend_from_slice(&(comment.len() as u32).to_le_bytes());
            data.extend_from_slice(comment.as_bytes());
        }
        data
    }

    fn flac_picture(image_bytes: usize) -> Vec<u8> {
        let mut data = Vec::new();
        let mime_type = b"image/jpeg";
        data.extend_from_slice(&3_u32.to_be_bytes());
        data.extend_from_slice(&(mime_type.len() as u32).to_be_bytes());
        data.extend_from_slice(mime_type);
        data.extend_from_slice(&0_u32.to_be_bytes());
        data.extend_from_slice(&300_u32.to_be_bytes());
        data.extend_from_slice(&300_u32.to_be_bytes());
        data.extend_from_slice(&24_u32.to_be_bytes());
        data.extend_from_slice(&0_u32.to_be_bytes());
        data.extend_from_slice(&(image_bytes as u32).to_be_bytes());
        data.extend(std::iter::repeat_n(0xFF, image_bytes));
        data
    }

    fn minimal_png(width: u32, height: u32) -> Vec<u8> {
        let mut png = Vec::new();
        png.extend_from_slice(&[0x89, b'P', b'N', b'G', 0x0D, 0x0A, 0x1A, 0x0A]);
        png.extend_from_slice(&13_u32.to_be_bytes());
        png.extend_from_slice(b"IHDR");
        png.extend_from_slice(&width.to_be_bytes());
        png.extend_from_slice(&height.to_be_bytes());
        png.extend_from_slice(&[8, 2, 0, 0, 0]);
        png.extend_from_slice(&0_u32.to_be_bytes());
        png
    }
}
