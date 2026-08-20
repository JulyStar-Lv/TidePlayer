# TidePlayer Final Architecture Report

Date: 2026-08-21

This document records the current architecture after the Komi Store-style KMP/CMP refactor. It reflects the physical module split and playback engine adapter work completed through Round 34 in `docs/architecture/komi-cmp-task.md`.

## 1. Module Tree

```text
TidePlayer/
├── androidApp/                         Android application entry point
├── desktopApp/                         Desktop JVM application entry point
├── iosApp/                             iOS Xcode project and Swift entry point
├── shared/                             Transitional app assembly, data, DI, navigation, platform actuals
├── core/
│   ├── domain/                         Pure Kotlin domain models and repository interfaces
│   ├── data/                           Stable cross-platform data implementations
│   └── presentation/                   Compose design system, theme, media rendering helpers
├── source/
│   ├── api/                            MusicSource contracts and registry
│   ├── local/                          Local source adapter
│   ├── webdav/                         WebDAV source adapter
│   ├── onedrive/                       OneDrive source adapter
│   ├── smb/                            SMB2/3 source adapter
│   ├── openlist/                       Independent OpenList source adapter
│   └── server/                         Server source adapter
├── service/
│   ├── playback/domain/                PlaybackController, PlaybackEngine, advanced capabilities, queue/state contracts
│   ├── playback/presentation/          NowPlaying state and mappers
│   ├── download/domain/                DownloadController, task model, enqueue use case
│   └── librarysync/domain/             LibrarySyncController and task contracts
├── feature/
│   ├── search/                         Search domain + presentation
│   ├── downloads/                      Downloads presentation
│   ├── settings/                       Settings presentation
│   ├── playlist/                       Playlist presentation
│   ├── sources/                        Source account/source editor presentation models and screens
│   ├── dashboard/                      Dashboard presentation
│   ├── importing/                      Import presentation contracts
│   ├── onboarding/                     Onboarding presentation
│   ├── radio/                          Radio presentation
│   ├── lyrics/                         Lyrics presentation
│   ├── recentlyadded/                  Recently Added presentation
│   ├── recentlyplayed/                 Recently Played presentation
│   ├── album/                          Album presentation
│   ├── artist/                         Artist presentation
│   ├── browse/                         Browse presentation
│   ├── library/                        Library presentation
│   └── queue/                          Queue presentation
├── build-logic/convention/             Gradle convention plugins
├── rust-libs/                          Rust runtime, core, metadata, remote storage
├── gradle/libs.versions.toml           Central version catalog
└── docs/architecture/                  Migration plan, task board, final architecture docs
```

### Physical Modules

| Module | Purpose | Notes |
|--------|---------|-------|
| `:core:domain` | Pure domain types | No Compose, Room, Ktor, Android, AVFoundation, Media3, UniFFI |
| `:core:presentation` | Shared UI system | Compose theme/components, artwork UI, window size |
| `:source:api` | Music source API | `MusicSource`, `MusicSourceRegistry`, source result models |
| `:source:smb` | SMB music source | SMB configuration, authentication/list/search/playback/download adapters over the shared storage bridge |
| `:source:openlist` | OpenList music source | Dedicated browse and stream adapter; OpenList is not a `RemoteServerKind` |
| `:source:server` | Server music sources | Navidrome, OpenSubsonic, and Emby adapters over the shared gateway |
| `:service:playback:domain` | Playback contracts | `PlaybackController`, `PlaybackEngine`, `AudioOutputController`, optional advanced playback capabilities, queue, state, position |
| `:service:playback:presentation` | Playback presentation | NowPlaying state models and domain-to-presentation mappers |
| `:service:download:domain` | Download contracts | `DownloadTask`, `DownloadController`, `EnqueueDownloadUseCase` |
| `:service:librarysync:domain` | Sync contracts | `LibrarySyncController`, task/status/request models |
| `:feature:search` | Search feature | Presentation + search domain contracts |
| `:feature:downloads` | Downloads feature | Presentation only |
| `:feature:settings` | Settings feature | Presentation only |
| `:feature:playlist` | Playlist feature | Presentation only; Room/UniFFI mappers remain in `shared` |
| `:feature:sources` | Sources feature | Presentation models and screens; data stays in `shared` |
| `:feature:dashboard` | Dashboard feature | Presentation; `SourcesRoot` injected by slot from `shared` |
| `:feature:importing` | Import feature | Presentation contracts; data/screen root remain in `shared` |
| `:feature:onboarding` | Onboarding feature | Presentation only |
| `:feature:queue` | Queue feature | Presentation only; depends on `service:playback:domain` PlaybackController |
| `:feature:radio` | Radio feature | Presentation only; ViewModel and Root stay in `shared` due to DAO deps |
| `:feature:lyrics` | Lyrics feature | Presentation only; ViewModel and Root stay in `shared` due to DAO deps |
| `:feature:recentlyadded` | Recently Added feature | Presentation only; ViewModel and Root stay in `shared` due to DAO deps |
| `:feature:recentlyplayed` | Recently Played feature | Presentation only; ViewModel and Root stay in `shared` due to DAO deps |
| `:feature:album` | Album feature | Presentation only; ViewModel and Root stay in `shared` due to DAO deps |
| `:feature:artist` | Artist feature | Presentation only; ViewModel and Root stay in `shared` due to DAO deps |
| `:feature:browse` | Browse feature | Presentation only; ViewModel and Root stay in `shared` due to DAO deps |
| `:feature:library` | Library feature | Presentation only; ViewModel and Root stay in `shared` due to DAO deps |

`shared` remains the transitional assembly module for Koin, navigation, Room, UniFFI, platform actuals, Rust bridge code, and data implementations that still depend on those boundaries. The cross-platform `ToastRepositoryImpl` has moved to `:core:data`; moving Room or DataStore further remains a separate migration because their builders and several repositories still depend on `shared` platform/UniFFI wiring.

## 2. Dependency Map

```text
androidApp / desktopApp / iosApp
    ↓
shared assembly
    ↓
feature:*        service:*        source:*        core:data / core:presentation
    ↓                    ↓                   ↓                    ↓
core:domain       core:domain          core:domain          core:domain
```

Data-side code stays in `shared` only where it currently depends on Room, UniFFI, Rust bridge APIs, platform actuals, or existing compatibility repositories. `core:data` depends on `core:domain` and never on `shared`.

### Enforced Boundaries

- Domain modules do not depend on data or presentation.
- Feature modules do not depend on another feature's presentation directly, except where `shared` provides a composition slot.
- Composables read immutable State and emit Action callbacks.
- Room entities and UniFFI models do not cross into feature UI state.
- Platform playback types do not leak into commonMain domain models.
- Temporary playback resources are resolved at playback time and not persisted to Room track entities.

## 3. Core Domain

`core/domain/src/commonMain/kotlin/io/github/julystar/musicapp/core/domain/model/`

| Type | Purpose |
|------|---------|
| `SourceId` | Stable source identifier for Local, WebDAV, OneDrive, SMB, OpenList, Navidrome, OpenSubsonic, or Emby |
| `SourceAccountId` | Stable per-account identifier |
| `MediaType` | Track, Album, Artist, Playlist, Folder, Image |
| `MediaId` | Stable cross-layer source media identifier |
| `Artwork` | Library, source, and legacy storage artwork references |
| `Lyrics` / `LyricLine` | Timed lyric model |

Domain module dependencies are limited to Kotlin and stable multiplatform libraries.

## 4. Key Interfaces

### MusicSource

`source/api/src/commonMain/kotlin/io/github/julystar/musicapp/source/api/MusicSource.kt`

```kotlin
interface MusicSource {
    val descriptor: MusicSourceDescriptor
    val capabilities: Set<SourceCapability>
    suspend fun authenticate(configuration: SourceConfiguration): SourceAuthResult
    suspend fun list(accountId: SourceAccountId, directoryId: String?): SourceListResult
    suspend fun search(accountId: SourceAccountId, query: String, limit: Int): SourceSearchResult
    suspend fun resolvePlayback(mediaId: MediaId): SourcePlaybackResult
}
```

Local, WebDAV, OneDrive, SMB, OpenList, and server adapters live in physical `source:*`
modules. `shared` supplies their Room, credential-store, and Rust/UniFFI bridge
implementations through Koin.

Current protocol-level incremental capability is intentionally narrow:

| Source | `IncrementalSync` | Implementation |
| --- | :---: | --- |
| Local | No | Explicit scan; no FileObserver/FSEvents watcher |
| WebDAV | Yes | RFC 6578 sync-token with cached capability and safe full-scan fallback |
| SMB | No | Signature/full scan; SMB Change Notify is not implemented |
| OneDrive | Yes | Microsoft Graph Delta cursor |
| Navidrome / OpenSubsonic / Emby | No | Bounded complete snapshot; no provider delta cursor |
| OpenList | No | Complete snapshot over raw canonical paths; no provider delta cursor |

The Sources selector exposes eight entries: Local, WebDAV, SMB, OneDrive,
Navidrome, OpenSubsonic, Emby, and OpenList. Local remains a UI-only picker
option rather than a persisted remote editor type. The server synchronization
entry accepts only `SourceAccountId`; it reads the persisted account from Room
and dispatches Navidrome, OpenSubsonic, or Emby without a global active server
or a UI-supplied provider kind.

Provider capability boundaries are explicit:

- Navidrome implements authentication, bounded paging, library metadata,
  artwork, lyrics, playback, downloads, and remote-playlist reads/writes.
- OpenSubsonic implements browse/search/stream, preserves its negotiated
  extension snapshot, uses structured-lyrics fallback, and shares remote
  playlist reads/writes with Navidrome. Remote writes default to disabled; it
  does not advertise standalone download.
- Emby keeps token/UserId/ServerId/ServerName identity at their appropriate
  credential/non-secret boundaries and supports 25k paging, metadata, artwork,
  Direct Play, and Direct Stream. Transcoding, playlists, remote writes,
  downloads, and lyrics are not implemented.
- OpenList is the independent `source/openlist` adapter with Browse + Stream.
  Its provider does not advertise Search or Download; synchronized items are
  searchable through the unified Room index.

Navidrome, OpenSubsonic, Emby, and OpenList synchronization is a complete
snapshot, not protocol-level incremental synchronization. A secondary server
endpoint is account-scoped and request-scoped: primary is tried first, and a
typed timeout/connectivity failure may try the sanitized secondary exactly
once. Authentication/permission, TLS, HTTP/protocol/JSON failures, and
cancellation do not fall back. Plain Subsonic resource URLs produced for later
playback, download, or artwork fetches do not receive transparent failover.

### PlaybackController

`service/playback/domain/src/commonMain/kotlin/io/github/julystar/musicapp/service/playback/domain/PlaybackController.kt`

```kotlin
interface PlaybackController {
    val state: StateFlow<PlayerState>
    val position: StateFlow<PlaybackPosition>
    val queue: StateFlow<PlaybackQueue>
}
```

Playback state, queue state, and high-frequency position updates are separated to avoid whole-shell recomposition from progress ticks.

Advanced playback capabilities are modeled as a separate optional contract:

```kotlin
interface AdvancedPlaybackController {
    val capabilities: StateFlow<PlaybackEngineCapabilities>
    val enhancementSettings: StateFlow<PlaybackEnhancementSettings>
    val outputState: StateFlow<AudioOutputState>
}
```

The advanced contract covers gapless playback, crossfade, ReplayGain, output
device selection, Android Auto, AirPlay, and CarPlay without requiring current
platform controllers to fake unsupported backend behavior.

Platform playback engines are represented in commonMain by a pure domain
contract:

```kotlin
interface PlaybackEngine {
    fun load(request: PlaybackEngineLoadRequest): PlaybackEngineLoadResult
    fun play()
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long)
    fun readPosition(): PlaybackPosition
    fun release()
}
```

`PlaybackEngineResource` carries the transient URI, headers, MIME type, local
flag, and expiration metadata needed by platform engines without depending on
Media3, AVFoundation, rodio, UniFFI, or `source:api`. Output selection is
modeled separately by `AudioOutputController`.

Android, iOS, and Desktop platform engine adapters now implement this common
`PlaybackEngine` contract. Shared data-layer mappers convert source-level
`PlaybackResource` values and UniFFI music models into `PlaybackEngineResource`
and `PlayableItem` at the boundary.

All seven remote providers use one playback chain:

```text
persisted account-scoped source candidate
  -> MusicSourceRegistry
  -> MusicSource.resolvePlayback(MediaId)
  -> in-memory PlaybackResource
  -> PlaybackEngineResource
  -> existing Android / iOS / Desktop player
```

There is no provider-specific player. A provider-neutral canonical `Track` can
retain seven account-scoped `SourceItem`/`track_source_ref` identities, and
candidate selection never relies on a global active account.

### DownloadController

`service/download/domain/src/commonMain/kotlin/io/github/julystar/musicapp/service/download/domain/DownloadController.kt`

```kotlin
interface DownloadController {
    val tasks: Flow<List<DownloadTask>>
    suspend fun enqueue(task: DownloadTask)
    suspend fun pause(id: DownloadTaskId)
    suspend fun resume(id: DownloadTaskId)
    suspend fun cancel(id: DownloadTaskId)
    suspend fun cancelAll()
    suspend fun recoverInterruptedTasks(): Int
    suspend fun retry(id: DownloadTaskId)
}
```

Every platform scheduler durably commits raw audio and persists the
`Finalizing` state before invoking `DownloadFinalizer`. Kotlin resolves a
source-aware `MetadataSnapshot` from the existing library; the shared Rust
`audio-metadata` writer edits a temporary copy with Lofty, verifies it through
the existing reader, and atomically replaces the media and sidecars. Completed
playback caches use the same finalizer after an idempotent promote step. See
[download-finalization.md](./download-finalization.md) for recovery, metadata,
lyrics, and format policy.

### LibrarySyncController

`service/librarysync/domain/src/commonMain/kotlin/io/github/julystar/musicapp/service/librarysync/domain/LibrarySyncController.kt`

```kotlin
interface LibrarySyncController {
    val recentTasks: Flow<List<LibrarySyncTask>>
    suspend fun syncFolder(request: LibrarySyncRequest): LibrarySyncResult
    suspend fun pause(scanId: String)
    suspend fun cancel(scanId: String)
    suspend fun resume(scanId: String)
    suspend fun retry(scanId: String)
}
```

## 5. Feature Pattern

Feature modules use the Komi Store-style State/Action/Event pattern:

```text
Root       shared-owned composition, Koin injection, navigation, legacy VM bridging
Screen     pure Composable: reads State, emits Action
State      @Immutable data class
Action     sealed interface
Event      sealed interface for one-shot events
ViewModel  StateFlow + Channel<Event>
```

Physical feature modules contain only the parts that can compile without Room, UniFFI, platform APIs, or legacy singleton dependencies. Root/ViewModel/data files that still depend on those systems stay in `shared`.

## 6. Navigation

`shared/src/commonMain/kotlin/io/github/julystar/musicapp/navigation/`

- `MusicGraph.kt` defines typed serializable routes.
- `AppNavigation.kt` owns the root scaffold and NavHost.
- Route registration is split into Home, Library, Player, Search, Sources, Downloads, Settings graph files.
- Route arguments use stable identifiers or legacy transitional IDs only at bridge boundaries.

Navigation does not carry credentials, temporary playback URLs, Room entities, or platform objects.

## 7. Koin Assembly

Koin is still assembled in `shared` because it wires platform actuals, Room, UniFFI bridge code, and physical feature modules together.

| Module | Responsibility |
|--------|----------------|
| `coreDataModule` | Room, DataStore, credential store, repositories, bridge objects |
| `sourceDataModule` | Eight MusicSource entries, source registry, account-scoped server sync coordinators, legacy source bridges |
| `playbackModule` | Playback controller bridge, resolver, platform engine |
| `downloadModule` | Download controller, repository, scheduler, enqueue use case |
| `librarySyncModule` | Library sync controller, repository, legacy sync bridge |
| `searchFeatureModule` | Search repositories/use cases/ViewModel |
| `onboardingFeatureModule` | Onboarding ViewModel (colocated in `:feature:onboarding`) |
| `queueFeatureModule` | Queue ViewModel (colocated in `:feature:queue`) |
| `downloadsFeatureModule` | Downloads ViewModel (colocated in `:feature:downloads`) |
| `libraryFeatureModule` | Library, playlist, import, track row ViewModels |
| `settingsFeatureModule` | Source editor, source list, logs/debug/settings ViewModels |

Entry points call the shared Koin bootstrap from Android, iOS, and Desktop.

## 8. Room Database

Room KMP remains in `shared`.

- Database: `AppDatabase`
- Driver: `BundledSQLiteDriver`
- Schema path: `shared/schemas/io.github.julystar.musicapp.database.AppDatabase/`
- Current schema version: 24
- Source identity tables: `source_account`, `library_root`, `source_item`, `source_item_property`, `track_source_ref`, `source_sync_cursor`, `source_error`
- Canonical library tables: `track`, `album`, `artist`, `genre`, joins, `artwork`, `lyrics`, `raw_metadata`, `playlist`, `playlist_track`, `download_task`, `import_job`

Room remains the source of truth for library pages. Presentation modules receive mapped immutable UI state, not Room entities.

The live schema is source-agnostic. It no longer contains provider-specific
library tables such as `storage`, `selected_folder`, `remote_file`, or
`sync_cursor`, and `TrackEntity` no longer stores `remoteFileId`,
`sourceStorageId`, or `sourcePath`. Those names appear only in historical
migrations or compatibility domain DTOs.

Source adapters authenticate, browse, scan, and resolve playback resources.
They do not write canonical DAOs directly. `RemoteLibraryImportCoordinator` is
the write boundary that converts source scan output into `source_item`,
canonical metadata rows, and `track_source_ref`.

Playback resolves through `TrackEntity -> TrackSourceRefEntity ->
SourceItemEntity -> persisted SourceAccountEntity -> MusicSourceRegistry ->
MusicSource.resolvePlayback(...)`. Temporary URIs,
headers, cookies, tokens, and signed URLs remain transient and are not written
to Room.

Official migrations `22 -> 23` add nullable structured lyrics and `23 -> 24`
add account-scoped remote playlist identity. Sequential `22 -> 23 -> 24`
migration preserves legacy lyric fields and local playlist/member rows, while
allowing the same remote playlist ID in two different accounts. There is no
destructive migration fallback, schema 24 is the current version, and schema 25 is
absent.

Room stores non-sensitive account configuration, provider identity, canonical
library rows, and credential references. Passwords and long-lived provider
tokens live in platform `CredentialStore`s. Session tokens, resolved playback
URLs/headers, and OpenList OTP values remain memory-only. Diagnostic log,
incident, artifact, and export boundaries use redaction v2, including known
loopback playback capability paths; this does not claim that every ordinary
query-free URL is removed.

### SMB storage and playback

The pure-Rust `smb2` client is implemented as `SmbBackend` behind the existing
`StorageBackend` trait. `StorageType.Smb` is appended to the UniFFI/Serde/
bitcode model, and the generic controller, scanner, metadata reader, and
playback gateway build the backend from credential-free address data plus a
credential-store lookup.

SMB directory enumeration uses three bounded session slots while positioned
reads use a dedicated playback slot. File readers are kept in an eight-entry
LRU and explicitly released on eviction, invalidation, playback shutdown, and
account deletion. Full streaming uses a bounded two-chunk channel with 512 KiB
chunks. Range playback continues through the tokenized localhost HTTP gateway,
so Media3, AVPlayer, and Desktop playback never receive `smb://` or account
credentials.

See [SMB music source](../music-sources/smb.md) for configuration, platform
limits, and the compatibility matrix.

## 9. Platform Architecture

### Android

- Playback uses Media3 through an `AndroidPlaybackEngine` adapter that
  implements the common `PlaybackEngine` contract.
- Downloads are scheduled through the Android platform scheduler.
- Credentials use Android platform secure storage.
- Backup uses an encrypted allowlist for non-secret settings and `library.db`;
  credentials, downloads, caches, diagnostics, temporary resources, and
  external files are excluded. Undecryptable restored credential references
  require reauthentication rather than retrying or crashing.

### iOS

- Playback uses AVPlayer through an `IosPlaybackEngine` adapter, AVPlayer item
  processing taps, and the shared Rust DSP.
- The audio session enables AirPlay; Settings embeds Apple's
  `AVRoutePickerView`, and `IosAdvancedPlaybackController` publishes the actual
  `AVAudioSession.currentRoute`. Route-change notifications refresh that state.
- MPNowPlayingInfoCenter and MPRemoteCommandCenter cover lock-screen, Control
  Center, Bluetooth, and CarPlay Now Playing control. A browsable CarPlay media
  application is not implemented or advertised.
- Downloads use the iOS scheduler boundary.
- Credentials use Keychain-backed storage.

### Desktop

- Playback uses the Desktop RustAudio/rodio adapter through the common
  `PlaybackEngine` contract. cpal enumerates output devices and identifies the
  real system default. UniFFI device selection opens and restores a new rodio
  sink before releasing the old output, preserving position, play/pause state,
  volume, resource headers, crossfade configuration, and DSP state.
- Downloads use coroutine-based desktop scheduling.
- Credentials use desktop platform storage.

## 10. Build Logic

`build-logic/convention/`

| Plugin ID | Purpose |
|-----------|---------|
| `io.github.julystar.musicapp.convention.kmp.library` | Applies KMP, serialization, Android library plugins |
| `io.github.julystar.musicapp.convention.cmp.library` | Applies Compose Multiplatform plugins |
| `io.github.julystar.musicapp.convention.kmp.domain` | Pure domain/API module plugin |
| `io.github.julystar.musicapp.convention.feature` | Feature module plugin |
| `io.github.julystar.musicapp.convention.music-source` | Future source implementation module plugin |
| `io.github.julystar.musicapp.convention.room` | Room/KSP conventions |
| `io.github.julystar.musicapp.convention.cargo-uniffi` | Gobley Cargo + UniFFI conventions |

Class-based convention plugins are currently apply-only. KMP targets and module-specific dependencies stay in module build scripts because direct KGP/AGP/Compose extension configuration from the included build introduced API/classpath ambiguity.

## 11. Build / Test Results

The 2026-08-21 acceptance gate ran the Rust workspace (296 passed, with four
pre-existing opt-in Samba tests ignored), 532 forced Gradle desktop tests
across source API/server/OpenList, Sources UI, and shared (0 failures; one
pre-existing opt-in live WebDAV smoke test skipped), and a 770-task Desktop,
Android Debug, and iOS Simulator Arm64 compilation gate. It includes real Room
25k server synchronization, exact OpenList second-snapshot counts, sequential
schema 22-to-24 migration, and the seven-provider playback resolver matrix.
Exact commands and per-module counts are recorded in
[the test report](../testing/test-report.md).

## 12. Known Limits

| Limit | Status |
|-------|--------|
| Further `core:data` extraction | `ToastRepositoryImpl` is extracted; Room, database builders, DataStore repositories, and UniFFI-backed repositories remain in `shared` pending a boundary-safe relocation |
| SMB incremental synchronization | First version performs full scans; Change Notify is not implemented |
| iOS SMB download after process termination | URLSession cannot rely on a terminated localhost playback session; in-process resume is implemented |
| Desktop output hot-plug | Devices refresh on Settings entry, user refresh, selection, and failure; no background cpal hot-plug daemon |
| AirPlay verification | Native route picker and route state are implemented; real-device/AirPlay receiver behavior still requires hardware verification |
| CarPlay library browser | Not implemented; current capability is Now Playing/remote control only and requires normal Apple entitlement/device validation |
| OneDrive cancellable delta sync | Requires lower-level Rust request cancellation |
| Full Miuix migration | Material 3 wrappers remain because Miuix 0.9.2 APIs differ from expected signatures |

## 13. Extension Guidance

### Adding A Music Source

1. Implement `MusicSource` against `source:api`.
2. Register the source adapter in shared Koin `sourceDataModule`.
3. Map account, browse, search, and playback data through stable `SourceId`, `SourceAccountId`, and `MediaId`.
4. Avoid changing playback, library pages, downloads, or search presentation contracts.

### Replacing Playback Engine

1. Implement the platform playback boundary behind `PlaybackController`.
2. Keep platform playback objects out of common domain models.
3. Keep `PlaybackPosition` separate from `PlayerState`.
4. Advertise backend-dependent advanced capabilities through `AdvancedPlaybackController` only when the platform engine supports them.
5. Adapt source-level playback resources into `PlaybackEngineResource` at the shared data boundary.

### Adding A Feature Page

1. Create State, Action, Event, and Screen in a feature module when dependencies are pure.
2. Keep Koin injection, navigation, and legacy data bridges in `shared` until their dependencies are physically split.
3. Register the route in `MusicGraph` and the appropriate graph file.
