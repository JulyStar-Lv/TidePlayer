# TidePlayer — Komi Store Architecture Refactor Task Tracking

## Goal

参考 Komi Store 架构开发 CMP/KMP 多平台音乐 App，采用 Clean Architecture、Feature 分层、Convention Plugin、Koin、类型安全导航、Miuix UI、单向数据流（State/Action/Event）。

Full goal document: `docs/architecture/komi-cmp-goal.md`

## Progress Overview

| Phase | Title | Status |
|-------|-------|--------|
| 1 | 工程骨架 (Project Skeleton) | ✅ 完成 |
| 2 | UI Shell | ✅ 完成 |
| 3 | 领域模型和 Room | ✅ 完成 |
| 4 | 播放器 (Player) | ✅ 完成 |
| 5 | 音乐源 (Music Sources) | ✅ 完成 |
| 6 | 同步和搜索 (Sync & Search) | ✅ 完成 |
| 7 | 下载和高级能力 (Downloads & Advanced) | ✅ 完成 |

---

## Phase 1: 工程骨架 (Project Skeleton)

- [x] Version catalog (`gradle/libs.versions.toml`)
- [x] Convention plugins (`build-logic/convention/`)
- [x] composeApp entry points (androidApp, desktopApp, iosApp)
- [x] core:domain, core:presentation modules
- [x] Koin init and module structure
- [x] Type-safe navigation (MusicGraph)
- [x] Miuix Theme wrapper
- [x] Android, iOS, Desktop compile gate

## Phase 2: UI Shell

- [x] AppShell with MiniPlayer, NavigationBar/Rail/Sidebar
- [x] Five main tabs: Home, Browse, Radio, Library, Search
- [x] Now Playing screen
- [x] Light/Dark theme
- [x] Compact / Medium / Expanded layout (WindowSizeClass)
- [x] Mock data for initial UI prototyping

## Phase 3: 领域模型和 Room

- [x] Domain models: Track, Album, Artist, Playlist, MediaId, etc.
- [x] Room database (v5) with entities, DAOs, migrations 1→5
- [x] Repository interfaces in core:domain
- [x] Repository implementations in shared and feature modules
- [x] Library page wired to Room

## Phase 4: 播放器 (Player)

- [x] PlaybackController, PlaybackEngine interfaces
- [x] PlaybackModels (PlayableItem, PlaybackQueue, PlayerState, etc.)
- [x] Android: Media3 ExoPlayer + PlaybackService + MediaSession
- [x] iOS: AVPlayer engine, processing tap/shared DSP, audio session, Now Playing and Remote Command integration
- [x] Desktop: RustAudio/rodio via uniffi (RodioDesktopPlaybackEngine)
- [x] MiniPlayer and NowPlaying share same controller
- [x] PlaybackShell with global MiniPlayer
- [x] Sleep timer

## Phase 5: 音乐源 (Music Sources)

- [x] MusicSource API (source:api)
- [x] MusicSourceRegistry
- [x] Local MusicSource (source:local)
- [x] WebDAV MusicSource (source:webdav)
- [x] OneDrive MusicSource (source:onedrive)
- [x] PlaybackResource resolution
- [x] Storage account management (sources feature)
- [x] Import/scan UI (importing feature)

## Phase 6: 同步和搜索 (Sync & Search)

### Room FTS4 Full-Text Search
- [x] `TrackFts` entity (`@Fts4(contentEntity = TrackEntity::class)`)
- [x] `TrackFtsDao` with `searchFts()`, `searchFtsExcludingDeleted()`, `searchFtsSuggestions()`
- [x] `MIGRATION_5_6` creating FTS virtual table and rebuilding index
- [x] Database bumped to version 6
- [x] `RoomSearchRepository` uses FTS for alphanumeric queries, LIKE fallback for special chars
- [x] `canUseFts()` guard avoids FTS tokenizer precision loss

### Library Sync (Domain)
- [x] `LibrarySyncController` interface, `LibrarySyncTask`, `LibrarySyncRequest/Result`
- [x] `MetadataImportPipeline` — `RawMetadataItem`, `NormalizedMetadataItem`, `MatchResult`
- [x] `DuplicateMatcher` + `DefaultDuplicateMatcher`
- [x] `MetadataNormalizer` + `DefaultMetadataNormalizer`
- [x] `SyncTransactionWriter` interface
- [x] `DuplicateMatcherTest` (9 tests), `MetadataNormalizerTest` (8 tests)

### Library Sync (Data)
- [x] `service:librarysync:data` KMP module created
- [x] `librarySyncDataModule` Koin assembly
- [x] `LegacyLibrarySyncController` (wraps Rust importer)
- [x] `RoomLibrarySyncTaskRepository`
- [x] Active task detection, pause/cancel/resume/retry
- [x] WebDAV RFC 6578 token sync with cached capability and safe full-scan fallback
- [x] Bounded 4-way `Depth: 1` traversal with exact PROPFIND properties, retry, and cancellation
- [x] Streaming signature diff; zero-change batches skip metadata and `source_item` writes
- [x] Fast daily / Standard first / explicit Full metadata policy and persisted performance metrics

### Search
- [x] `SearchRepository` interface (feature:search:domain)
- [x] `RoomSearchRepository` — FTS-first, LIKE fallback, combined suggestions
- [x] `MusicSourceSearchAggregator` — multi-source parallel search
- [x] `SearchViewModel` with 350ms debounce, per-source batch results
- [x] Search history (DataStore-backed)
- [x] Source-labeled search results

---

## Phase 7: 下载和高级能力 (Downloads & Advanced)

### Downloads
- [x] `DownloadController` interface (service:download:domain)
- [x] `DownloadTask`, `DownloadStatus`, `EnqueueDownloadUseCase`
- [x] `PersistentDownloadController` (service:download:data)
- [x] `DownloadTaskDao` (Room) with status observation
- [x] `DesktopCoroutineDownloadScheduler`
- [x] Downloads feature page (feature:downloads)

### Lyrics
- [x] `LyricsState` with synchronized and word-timed models
- [x] `WordTimedLyricLine`, `WordTimedToken` for word-level highlighting
- [x] `WordTimedLyricsContent` composable with bold active-word rendering
- [x] `LyricsRepository` interface, `LyricsRepositoryImpl`
- [x] `LyricsEntity` in Room

### Playback Enhancements
- [x] `AdvancedPlaybackController` interface (gapless, crossfade, ReplayGain, output selection)
- [x] `PlaybackEngineCapabilities`, `PlaybackEnhancementSettings`, `ReplayGainMode`
- [x] `PlaybackSettingsVM` + `PlaybackSettingsSection` UI in settings
- [x] Settings screen renders playback section conditionally based on engine capabilities

### Desktop Advanced Controller
- [x] `DesktopAudioOutputController` — cpal enumeration through Rust/UniFFI, real rodio output switching, backend state refresh and failure reporting
- [x] `DesktopAdvancedPlaybackController` — full enhancement control
- [x] Registered in desktop `PlatformModule` as `AdvancedPlaybackController`

### Dynamic Player Background
- [x] `PlayerBackground` composable in `core:presentation/media`
- [x] `PlayerBackgroundColorExtractor` interface
- [x] `FallbackPlayerBackgroundColorExtractor` (neutral dark default)

### Android Auto
- [x] `automotive_app_desc.xml` with `<uses name="media" />`
- [x] `com.google.android.gms.car.application` meta-data in manifest
- [x] `MediaBrowserService` intent filter on PlaybackService
- [x] `PlaybackService` exported for AA binding
- [x] `MainActivity` resizeableActivity enabled

---

## Presentation Zero Material UI Migration

- [x] Active Presentation Kotlin source uses Miuix, TidePlayer App components, and Compose runtime/UI/foundation/animation/resources instead of Compose Material or Material3.
- [x] `AppTheme`, core App components, app shell, navigation bars, sidebar, MiniPlayer, Now Playing, and toast/snackbar UI migrated off Material APIs.
- [x] Direct Compose Material3 version-catalog aliases removed from `gradle/libs.versions.toml`.
- [x] Required import scan and extended Kotlin Material API scan report zero matches.

---

## Remaining Known Items

| Item | Status | Notes |
|------|--------|-------|
| AirPlay / CarPlay iOS integration | Partial, truthful boundary | AirPlay session, native route picker, current-route state, route-change refresh, Now Playing and Remote Command are implemented; no browsable CarPlay media app |
| iOS simulator gate | Automated | Kotlin/Native simulator compilation and unsigned Xcode simulator build are part of GitHub Actions; device/AirPlay hardware remains manual |
| Rust rodio audio device selection | Implemented | cpal devices and default are exported through UniFFI; switching restores playback state and leaves the old output active on open/restore failure |
| Data extraction to core:data | In progress | Cross-platform UiMessage repository implementation moved to `core:data`; Room/DataStore/UniFFI-backed repositories remain in `shared` |
| Incremental sync | Aligned | WebDAV RFC 6578 and OneDrive Delta advertise `IncrementalSync`; Local, SMB and server providers do not |

---

## Build Gates

| Date | Gate | Result |
|------|------|--------|
| 2026-06-30 R89 | Desktop tests, Android unit, iOS compile, shared compile | 718 tasks BUILD SUCCESSFUL |
| 2026-06-30 R90 (Phase 6/7) | Desktop tests (119), key module compiles | BUILD SUCCESSFUL, 0 failures |
| 2026-07-01 Zero Material | Required Material import scan, extended Kotlin API scan, Gradle dependency scan, Android/desktop/iOS compile, iOS simulator launch | PASS: zero matches; BUILD SUCCESSFUL for `:androidApp:assembleDebug`, `:desktopApp:compileKotlinDesktop`, `:shared:compileKotlinIosSimulatorArm64`; launched on iPhone 17 / iOS 26.4 |
