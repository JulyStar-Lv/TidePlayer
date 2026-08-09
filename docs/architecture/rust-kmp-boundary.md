# Rust and KMP boundary

Last updated: 2026-07-15

## Responsibilities

Rust owns:

- WebDAV and OneDrive networking and authentication protocol handling;
- controlled `Depth: 1` traversal, RFC 6578 `sync-collection`, and Graph pagination;
- bounded HTTP ranges and response validation;
- range-block caching and metadata read budgets;
- Lofty metadata parsing;
- Desktop decoding/playback when the player crate is implemented.

KMP owns:

- secure credential references and platform credential stores;
- import coordination and Room transactions;
- repositories, `Flow`/`StateFlow`, ViewModels, and Compose UI;
- platform player adapters for Android and iOS.

Compose UI must not import generated UniFFI functions. Generated types are
currently confined to repositories and legacy adapters while domain models are
introduced incrementally.

## Metadata call path

```text
KMP MetadataRepository
  -> ctReadRemoteMetadata(MetadataReadOptions)
  -> StorageRangeSource
  -> StorageBackend.get_range(start..=end)
  -> RemoteRangeReader (Read + Seek, block cache, budgets)
  -> Lofty Probe
  -> RemoteMetadata
       - normalized people, release, identifiers, ReplayGain, and audio properties
       - embedded lyrics descriptor
       - bounded generic text-tag list
       - request count, fetched bytes, elapsed time, and cached artwork bytes
```

`MetadataScanMode` has one domain mapping to Rust read options:

| Mode | Artwork | Lyrics | Raw metadata |
| --- | --- | --- | --- |
| Fast | No | No | No |
| Standard | No | Yes | No |
| Full | Yes | Yes | Yes |

The options are passed once per batch through UniFFI. Rust applies
`ParseOptions::read_cover_art(false)` before Lofty reads the file, and it does
not run artwork, lyric, or raw-tag extraction when the corresponding option is
disabled. This is pre-read/pre-extraction pruning, not post-processing of a
fully parsed result. The legacy no-options Rust entry point delegates to Full
for compatibility. Local and OneDrive scans also retain Full behavior.

Remote range reads use finite `bytes=start-end` requests. A successful remote
response must be `206 Partial Content` with a matching `Content-Range`.
Successful `200 OK` responses are rejected as unsupported range behavior rather
than silently downloading the entire object.

Default metadata limits are 256 KiB blocks, 64 requests, and 4 MiB fetched per
file. A 50-file live WebDAV scan showed that 64 KiB blocks caused request-bound
timeouts on FLAC files with large metadata regions; 256 KiB reduced requests by
63.9% and scan time by 70.1%, while keeping average transfer near 0.50 MiB per
file. These are scanner safeguards, not playback limits.

Artwork decoding is enabled only for Full or an artwork-specific refresh. Text
metadata is bounded to 2,048 entries, 256 KiB per value, and 1 MiB total per
file. Oversized input returns an explicit metadata budget error instead of
crossing FFI or being written as an unbounded Room value. Parsed fields now include multi-artist
credits, composer, lyricist, conductor, grouping, comments, copyright,
publisher/label, original release date, BPM, key, ISRC, MusicBrainz IDs,
ReplayGain values, embedded lyrics, channel layout, codec/container, and
lossless classification.

## Import persistence call path

```text
EditStorageVM.prepareImportLibraryFolder
  -> ImportRepository current-directory mode
  -> ImportVM directory picker
  -> RemoteScannerRepository.listDirectory
  -> ctListStorageEntryChildren
  -> user confirms the current directory
  -> LegacyLibrarySyncController
  -> RemoteLibraryImportCoordinator.syncWebDavFolder
  -> cached RFC 6578 capability + typed WebDAV sync-token lookup
  -> REPORT sync-collection when supported
     or ctStartStorageMusicScan with controlled Depth: 1 recursion
  -> RemoteMusicScanSession.nextBatch(default 200 files)
  -> for each bounded batch:
       - match lightweight Room signatures by stable remoteId, then canonical path
       - compare size + ETag or Last-Modified fingerprints
       - MetadataRepository.readBatch(options, bounded concurrency)
       - Room transaction
            - upsert changed source_item rows
            - upsert album, artist, genre, and relationship rows
            - upsert normalized track metadata
            - upsert track_source_ref rows
            - update artwork, lyrics, and raw tags only when requested
            - persist import_job counters and checkpoint
  -> after the Rust session reports done:
       - apply only the remaining complete-snapshot IDs as missing
       - persist the typed sync token/capability and final import_job status atomically
  -> SyncDao.observeRecentJobs
  -> ImportStatusRepository / ImportStatusVM
  -> Dashboard import status and cancellation action
  -> TrackDao.observeAll
  -> LibraryRepository
  -> LibraryVM
  -> LibrarySubpage
```

The full scanner uses repeated single-level remote listings rather than
unbounded `Depth: infinity` WebDAV requests. Each PROPFIND asks only for
`displayname`, `resourcetype`, `getcontentlength`, `getcontenttype`, `getetag`,
`creationdate`, and `getlastmodified`; it does not use `allprop`. Rust schedules
directory requests through a bounded coordinator (default 4, allowed 1...8),
never holds the scan-state lock across network I/O, deduplicates normalized
directory paths, and streams files through a 400-entry channel. KMP consumes
and persists each batch immediately instead of collecting the complete tree.
The 100,000-entry safety limit fails explicitly rather than silently truncating.

Transient connection failures and HTTP 429/500/502/503/504 responses have
finite exponential backoff. `Retry-After` is honored and 429/503 establishes a
shared cooldown; authentication is retried once after a 401 challenge. 403,
404, malformed XML, and other semantic failures are not retried. Cancellation
races and aborts active HTTP request tasks.

WebDAV capability probing is a controlled empty-token RFC 6578 REPORT. A
successful response stores `webdav_sync_capability=sync_collection` and a
`webdav_sync_token`; 405/501 stores `unsupported` and uses the parallel full
scan on later runs. Invalid or expired tokens trigger a complete snapshot and
are replaced only in the same transaction that completes Room writes. Delta
deletion tombstones are applied explicitly, including descendants of a deleted
collection. A delete+add pair reuses a source identity only when raw ETag and
size form a unique match and the destination is unoccupied.

Daily WebDAV rescans use Fast metadata reads for an existing root, first import
defaults to Standard, and an explicit Full request remains Full. Unchanged
files cause no metadata request and no `source_item` update. KMP defaults to
200-file batches and metadata concurrency 8, keeping SQLite queries, FFI
requests, results, and transactions bounded for 1,000- to 100,000-file
libraries.

`import_job` is created before remote enumeration begins and is updated after
every committed batch. Cancelling the Rust scan session preserves already
committed rows, records `CANCELLED`, and deliberately does not run the
missing-file deletion step. The Rust session races each in-flight directory
request against a cancellation signal, so cancellation drops the active
listing future instead of waiting for the remote timeout. Missing remote rows
are marked deleted only after the scanner reports a complete snapshot.

For OneDrive and other backends with stable item IDs, a rename or move reuses
the existing `source_item` primary key. If size and ETag/Last-Modified are
unchanged, the coordinator updates only the source inventory and skips metadata
Range reads. If the item revision also changed, metadata is refreshed while the
existing canonical `track` primary key and creation timestamp are retained.

Every import job stores the effective metadata mode, scan rules, concurrency,
batch size, missing-file policy, and duplicate policy. Resume and retry rebuild
their request from this snapshot rather than from current Settings. The job also
accumulates metadata Range request count, fetched bytes, elapsed milliseconds,
and newly cached artwork bytes. WebDAV jobs additionally persist the sync mode,
directory concurrency/request counts, listed directories, visited/discovered
entries, unchanged/added/modified/renamed/deleted counts, capability/scan/Room
timings, and total elapsed time; Settings renders these counters without paths,
credentials, or tokens.

A local HTTP WebDAV fixture with 100 albums and 10 tracks per album measured
1,310 ms with one directory request versus 333 ms with four concurrent requests
(1,000 files, 101 actual PROPFIND listings). The parallel result is about 74.6%
faster and clears the required 40% improvement gate. The RFC 6578 five-change
fixture completes in one REPORT and avoids recursive PROPFIND.

## Metadata backfill

Settings exposes missing-artwork and missing-lyrics maintenance actions for
WebDAV tracks. The same controller also supports a single track or album and a
raw/all target for non-UI callers. Candidate lookup uses persisted source refs,
so unchanged ETag or modified-time values do not block a refresh. Each target
maps to minimum read options (for example, artwork-only is `true/false/false`),
and Room updates only that requested optional metadata family.

The coordinator also accepts an already-built complete snapshot for tests and
future delta implementations; that path uses the same bounded batch writer. The
directory picker browses one level at a time through `RemoteScannerRepository`;
the Compose page and `ImportVM` do not import generated UniFFI functions. The
picker is not the source of truth for the imported library: Room rows written by
the coordinator are. The Home pager includes a Library tab backed by
`TrackDao.observeAll()`, so imported songs remain visible after the remote scan
objects have been released.

## iOS playback call path

```text
IosPlayerController
  -> ctCreatePlaybackSession
  -> Rust loopback gateway on 127.0.0.1
  -> AVPlayer HTTP Range request
  -> 256 KiB in-memory LRU block cache
  -> StorageBackend.get_range_response(start..=end)
  -> finite WebDAV or OneDrive Range request
```

The loopback URL contains a random per-session token and a media extension for
AVFoundation format detection. The gateway supports one HTTP byte range per
request, returns `206 Partial Content` or `416 Range Not Satisfiable`, and never
falls back to a whole-file disk cache. The KMP controller owns the Rust
`PlaybackSession`; replacing or stopping playback shuts down the gateway.

Gateway setup and serving run inside TidePlayer's Rust Tokio runtime. This is
required because UniFFI async functions may be polled from platform coroutine
threads that do not have a Tokio reactor.

## Credential call path

Room persists only `credentialRef` and non-secret storage display fields.
`StorageRepository` loads the platform credential and registers it in the
in-memory Rust backend state. Rust clears migrated legacy secrets and never
returns passwords or refresh tokens in storage-list responses.

Platform stores:

- Android: AES-GCM key in Android Keystore; encrypted payload in private
  preferences.
- iOS: generic-password item in Keychain.
- Desktop: macOS Keychain or Linux Secret Service command adapter. Windows
  Credential Manager remains to be implemented.
