# TidePlayer test report

Last updated: 2026-08-11

This report tracks verified migration gates. Secrets used for live WebDAV
checks were provided at runtime and are not stored in this repository.

## Current capability matrix

| Area | Current automated coverage | Manual or hardware coverage still required |
| --- | --- | --- |
| UI actions and messages | `PlaybackUiActionTest`, queued `ToastRepositoryTest`, `ToastVM` forwarding, feature event/state tests, and first-party HMI/resource parity audit | Visual timing and accessibility behavior on each platform |
| Desktop output | Rust fake `AudioOutputBackend` tests cover IDs/default, successful set, failure preservation and playback control restoration; Kotlin tests cover UniFFI delegation and controller success/failure state | Real CoreAudio/WASAPI/ALSA device switching and hot-plug |
| Android backup | Debug resource packaging plus XML parsing validates both allowlist files; credential-decrypt failure is handled as reauthentication | Restore between two physical devices and OEM backup transports |
| iOS audio route | Kotlin/Native compiles `AVAudioSession.currentRoute` and native `AVRoutePickerView`; unsigned Xcode simulator build is the CI gate | AirPlay receiver, headphones/Bluetooth routing, and CarPlay entitlement/device behavior |
| Incremental sync | WebDAV capability test plus existing RFC 6578 controller tests; OneDrive Delta remains enabled | Provider-specific live accounts and server behavior |

## Current CI gates

GitHub Actions currently runs separate Rust, Android, Desktop/Kotlin, and unsigned arm64 iOS Simulator jobs. The iOS job installs both Apple Rust targets, executes `:shared:compileKotlinIosSimulatorArm64`, and runs the actual `iosApp/App.xcodeproj` / `App` scheme without code signing.

Local verification for this update is recorded only from commands that completed:

| Command | Actual result |
| --- | --- |
| `./gradlew :shared:desktopTest :desktopApp:compileKotlinDesktop :shared:compileDebugKotlinAndroid :androidApp:assembleDebug :shared:compileKotlinIosSimulatorArm64 --continue --console=plain` | Passed; 1,189 tasks (81 executed), Desktop tests, Desktop compile, Android compile/APK, and iOS Simulator arm64 Kotlin compile completed |
| `./gradlew :shared:desktopTest --console=plain` | Passed; 326 tests, 0 failures/errors, 1 skipped |
| `./gradlew :desktopApp:compileKotlinDesktop :shared:compileKotlinIosSimulatorArm64 --continue --console=plain` | Passed; 450 tasks, Desktop and iOS Simulator Kotlin/UniFFI compilation successful |
| `./gradlew :shared:compileDebugKotlinAndroid :shared:testDebugUnitTest :androidApp:assembleDebug --continue --console=plain` | Passed; 1031 tasks and 248 Android unit tests, 0 failures/errors/skips; debug APK assembled |
| `xcodebuild -project iosApp/App.xcodeproj -scheme App -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' ARCHS=arm64 ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO build` | Passed unsigned arm64 Simulator build; Kotlin framework and Swift 6 app compiled and linked |
| `cargo fmt --manifest-path rust-libs/Cargo.toml --all -- --check` | Passed |
| `cargo clippy --manifest-path rust-libs/Cargo.toml --workspace --all-targets -- -D warnings` | Passed |
| `cargo test --manifest-path rust-libs/Cargo.toml --workspace` | Passed; 214 Rust tests, 0 failures, with 4 external-Samba tests intentionally ignored |
| `cargo test --manifest-path rust-libs/Cargo.toml -p app-backend desktop_rodio --lib` | Passed; 10 Desktop rodio tests, 0 failures |
| `xmllint --noout androidApp/src/main/res/xml/data_extraction_rules.xml androidApp/src/main/res/xml/backup_rules.xml` | Passed |
| `python3 scripts/audit-hmi-i18n.py` | Passed; 27 resource groups have English/Chinese key, type and placeholder parity; no obvious hard-coded HMI text found |
| Ruby YAML parse of both workflow files | Passed |

## Current known limitations

- AirPlay, CarPlay, Android restore, and Desktop audio switching were not hardware verified in this local run.
- TidePlayer exposes CarPlay Now Playing/remote controls only; a browsable CarPlay media app is not implemented.
- Desktop devices refresh on Settings entry, explicit refresh, selection, and failure; there is no continuous hot-plug daemon.
- Local, SMB, Navidrome, OpenSubsonic, and Emby do not advertise protocol-level incremental sync. WebDAV RFC 6578 and OneDrive Delta do.
- `core:data` currently owns the stable UiMessage repository implementation; Room, database builders, DataStore, and UniFFI-backed repositories still reside in `shared`.

## Embedded lyric classification and playback lookup (2026-07-28)

Fast metadata scanning now classifies embedded lyrics without returning the
lyrics payload. Standard/Full scanning persists plain, line-timed, word-timed,
or TTML classification in `track_source_ref.embeddedLyricsKind`. Automatic
Lyrico lookup is restricted to the playback path and only runs when the user
ranks external word-timed/TTML lyrics ahead of the available plain fallback;
scanning does not invoke plugins and playback startup does not wait for lookup.

| Command | Actual result |
| --- | --- |
| `cargo test -p audio-metadata` | Passed; 15 tests, including no-payload Fast classification and word-timed/TTML detection |
| `cargo test -p app-backend` | Passed; 53 tests and all doc tests |
| Focused `:shared:desktopTest` plus `:feature:settings:desktopTest` | Passed; 67 tests covering lyric selection/classification, plugin persistence, remote import/refresh, and Room migration 19-to-20 |
| `./gradlew :shared:desktopTest --stacktrace` | Passed; 233 tests, 1 skipped, 0 failures |
| `./gradlew :shared:compileDebugKotlinAndroid :desktopApp:compileKotlinDesktop --stacktrace` | Passed |
| `./gradlew :shared:compileKotlinIosSimulatorArm64 --no-daemon --no-configuration-cache --stacktrace` | Passed; Kotlin/Native, Rust, and regenerated UniFFI bindings compiled for iOS Simulator |

## SMB music source validation (2026-07-27)

The SMB v1 implementation uses one pure-Rust SMB2/3 backend on Android, iOS,
and Desktop. The verified local gates cover source configuration, Room schema
v17 migration, credential redaction, browse/search/import mapping, Range
playback and seek behavior, bounded streaming/cancellation, download resume,
reader release, retry limits, and cross-target compilation.

| Command | Actual result |
| --- | --- |
| `cargo fmt --manifest-path rust-libs/Cargo.toml --all -- --check` | Passed |
| `cargo clippy --manifest-path rust-libs/Cargo.toml --workspace --all-targets -- -D warnings` | Passed |
| `cargo test --manifest-path rust-libs/Cargo.toml --workspace` | Passed; 117 Rust unit tests and all doc tests, with 4 Samba-dependent integration tests intentionally ignored in the normal workspace run |
| `./gradlew :source:smb:desktopTest --console=plain` | Passed; 5 SMB source adapter tests |
| `./gradlew :shared:desktopTest --console=plain` | Passed from the staged-index snapshot; 212 Desktop tests, 1 skipped, 0 failures, including SMB persistence, migration, global-search playback IDs, changed-size download resume rejection, account deletion, and playback resolution |
| `./gradlew :shared:compileDebugKotlinAndroid --console=plain` | Passed; shared Android and `source:smb` compilation |
| `./gradlew :shared:compileKotlinIosSimulatorArm64 --console=plain` | Passed; Kotlin/Native, Rust, UniFFI, and `source:smb` iOS Simulator compilation |
| `./scripts/check-smb-cross-targets.sh all` | Passed; `storage-backend` compiled for `aarch64-linux-android`, `x86_64-linux-android`, `aarch64-apple-ios`, and `aarch64-apple-ios-sim` |
| `cargo check --manifest-path rust-libs/Cargo.toml --package storage-backend --target x86_64-pc-windows-msvc` | Not completed locally; `ring` requires Windows SDK/MSVC C headers that are unavailable on the macOS host, so Windows client compilation remains unverified rather than being inferred |
| Ruby Psych parse of `.github/workflows/build-validation.yml` | Passed; workflow YAML syntax is valid |

The local machine had no Docker runtime or accessible NAS, so the ignored
Samba integration tests were not labeled locally passed. The GitHub Actions
workflow provisions an isolated Samba 4 server with an ephemeral random
password and runs authenticated, Guest, Unicode path, bounded large-file
streaming, offset resume, random Range, concurrent Range, changed-size reader
invalidation, missing-file, permission-denied, and explicit reader-release
checks. A successful hosted CI run is required before recording Samba 4 as
Tested. Windows file sharing,
Synology DSM, QNAP QTS, OpenMediaVault, and TrueNAS remain expected-compatible
but unverified on real devices.

## Metadata plugin apply and file reset validation (2026-07-16)

Room schema v15 records canonical metadata provenance and locking. Focused tests verify that an
accepted plugin result survives later background scans, audio properties continue to refresh,
and an explicit file reset restores current embedded tags while preserving track identity and
playback history. The reset source query covers the preferred available Local, WebDAV, or
OneDrive source; artwork and lyrics remain independent.

| Command | Actual result |
| --- | --- |
| `JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home ./gradlew :shared:desktopTest --tests io.github.julystar.musicapp.domain.importing.RemoteLibraryImportCoordinatorTest --tests io.github.julystar.musicapp.domain.importing.RemoteMetadataRefreshControllerTest --tests io.github.julystar.musicapp.database.RoomLibraryIntegrationTest --no-daemon --no-configuration-cache --console plain` | Passed; metadata locking/reset, reset-source projection, and v14-to-v15 migration coverage |
| `JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home ./gradlew :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :shared:compileDebugUnitTestKotlinAndroid :shared:compileTestKotlinIosSimulatorArm64 --no-daemon --no-configuration-cache --console plain` | Passed; Android and iOS Simulator main/test compilation |
| `JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home ./gradlew :shared:desktopTest --no-daemon --no-configuration-cache --console plain` | Passed; complete shared Desktop test suite |

## Lyrico Plugin API v3 validation (2026-07-14)

### Compatibility matrix

| Area | Status | Coverage |
| --- | --- | --- |
| Requests | Passed | `searchSongs` keyword/page/pageSize/separator/config; nested `getLyrics.song` with fields/internal; `searchCovers` keyword/pageSize/config |
| Song results | Passed | Arrays; items/results/songs/data wrappers; field aliases; artist arrays; numeric IDs; invalid-item isolation; fields/internal |
| Cover results | Passed | URL arrays, explicit objects, song objects, all wrappers, URL aliases and dimensions |
| Lyrics | Passed | Structured line/word timing, translation/romanization matching, all raw v3 types, notFound, legacy lines |
| QuickJS bridge | Passed | Object, array, JSON string, null, undefined, number, boolean; no double encoding |
| Runtime | Passed | Load/call timeout, cancellation races, poisoned-worker queue rejection, lazy rebuild, idempotent close |
| Permissions/context | Passed | Enabled master switch, manual/automatic/batch flags, bounded random plugin-scoped context tokens |
| Manifest/Settings | Passed | Optional author/description; official seven config types; dropdown options; dependency visibility; required values; markdown exclusion; empty-capability searchSongs default; boolean/select aliases |
| Production integration | Passed | Room repository mapping, observable MetaSource registry, resilient lookup use case, Settings management UI, platform shutdown hooks |
| Host API/security | Passed | App/runtime/cache/crypto/base64/bytes/compression/http/xml/log; redirect DNS revalidation; private-network and size limits |

The Desktop ZIP integration tests construct strict API v3 plugins at runtime. One plugin returns
`JSON.stringify(...)`, reads `request.song.internal.lyric_id`, and covers import through runtime
close. The second uses the complete official config-field set, options, dependencies, optional
descriptive fields, and empty capabilities without committing third-party code or credentials.

### Commands executed

| Command | Actual result |
| --- | --- |
| `cargo fmt --manifest-path rust-libs/Cargo.toml --all -- --check` | Passed |
| `cargo clippy --manifest-path rust-libs/Cargo.toml --workspace --all-targets -- -D warnings` | Passed |
| `cargo test --manifest-path rust-libs/Cargo.toml --workspace` | Passed; 79 Rust unit tests plus doc tests, including 20 plugin-runtime tests |
| `JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home ./gradlew :shared:desktopTest :shared:compileKotlinDesktop :desktopApp:compileKotlinDesktop :shared:compileDebugKotlinAndroid :androidApp:assembleDebug :shared:compileKotlinIosSimulatorArm64 --no-daemon --no-configuration-cache --console=plain` | Passed; 1,044 tasks executed or reused, 162 Desktop tests with 1 skipped, Desktop compilation, Android debug APK, and iOS Simulator arm64 shared compilation |
| `./gradlew :shared:desktopTest --tests 'io.github.julystar.musicapp.plugin.PluginImportRuntimeDesktopTest' --no-daemon --no-configuration-cache --console=plain` | Passed; 2 strict ZIP integration tests. The complete focused plugin suite contains 17 tests |
| `xcodebuild -project iosApp/App.xcodeproj -scheme App -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' ARCHS=arm64 ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO build` | Passed; Kotlin framework, Swift termination hook, AudioToolbox linkage, and iOS Simulator app bundle built successfully |

### Known limitations

- The configured iOS Simulator target is arm64 only; generic Xcode builds must not request x86_64.
- Gradle Desktop tests must run with Java 21 because repository modules emit Java 21 bytecode.
- Android does not invoke `Application.onTerminate()` for normal production process death; the OS
  reclaims process resources. The explicit Koin shutdown hook covers emulated/test termination.
- Real third-party ZIPs are user supplied and are not committed; the automated test uses the real
  API v3 format with a synthetic plugin and fictitious URLs.
- Bundled include-directory scripts are supported, while dynamic runtime file access through
  `include(path)` remains intentionally unavailable after bundling.

## Current verified commands

| Area | Command | Result |
| --- | --- | --- |
| Selective WebDAV metadata scanning | `cargo test -p audio-metadata -p app-backend`, focused `:core:domain:desktopTest`, `:service:librarysync:domain:desktopTest`, `:feature:settings:desktopTest`, and `:shared:desktopTest` suites | Passed; 12 audio-metadata tests, 14 backend tests, and 56 focused Kotlin/Room tests cover mode mapping, preservation of core tags and audio properties when optional reads are disabled, pre-read artwork pruning, optional extraction pruning, option propagation, conditional persistence, v11-to-v12 migration, settings migration/persistence and immediate use, task snapshot resume/retry, minimum-option artwork/lyrics/raw backfill, backfill preservation during later Fast scans, and read statistics |
| Embedded-artwork presence without payload reads | `cargo test -p audio-metadata --lib`, focused vendored-Lofty APE/MP4 tests, backend MP4 filtering tests, and `./gradlew :shared:desktopTest --tests io.github.julystar.musicapp.source.storage.MetadataRepositoryOptionsTest --tests io.github.julystar.musicapp.domain.importing.RemoteMetadataRefreshControllerTest --tests io.github.julystar.musicapp.database.RoomLibraryIntegrationTest --tests io.github.julystar.musicapp.domain.importing.RemoteLibraryImportCoordinatorTest` | Passed; 13 audio-metadata tests plus focused parser and Room tests cover Fast/Standard presence detection, large ID3 APIC and MP4 `covr` payload skipping, MP4 reader-bound restoration, per-source import/refresh persistence, and the v18-to-v19 migration |
| Selective scan cross-platform gate | `./gradlew :shared:compileDebugKotlinAndroid :androidApp:assembleDebug :desktopApp:compileKotlinDesktop :shared:compileKotlinIosSimulatorArm64 -x :shared:generateGitInfo` | Passed; Android APK, Desktop, iOS Simulator, generated Room accessors, and regenerated UniFFI Kotlin bindings compile together |
| Selective scan module all-tests | `./gradlew :service:librarysync:domain:allTests :feature:settings:allTests -x :shared:generateGitInfo` | Passed on Desktop, Android debug/release unit tests, and iOS Simulator |
| Repository-wide unit tests | `JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home ./gradlew test` | Passed; 1,458 Gradle tasks executed or reused, including Android debug/release unit tests for Settings and Shared |
| Settings and platform gate | `./gradlew :feature:settings:allTests :shared:desktopTest :shared:compileKotlinDesktop :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64` | Passed on Desktop, Android, and iOS Simulator |
| Gradle formatting gate | `./gradlew spotlessCheck` | Not available: this repository does not register a `spotlessCheck` task in the root project or subprojects; Rust formatting was verified separately |
| Android lint | `JAVA_HOME=/Applications/Android\ Studio.app/Contents/jbr/Contents/Home ./gradlew lint` | Blocked by repository baseline/tooling issues: Android lint expects Kotlin 2.2 metadata while the project and dependencies use Kotlin 2.4, and the existing `core/presentation/DropShadow.kt` reports `SuspiciousModifierThen` |
| Rust workspace current gate | `cargo fmt --all -- --check && cargo test --workspace` | Passed; 68 Rust unit tests plus all doc tests completed, including repeated plugin-runtime timeout/shutdown coverage |
| Room/DataStore persistence | `./gradlew :shared:desktopTest` | Passed; includes Room v2->v3 migration, DataStore play-mode persistence, Search history persistence, RoomLibraryStore playlist/location writes, lyric removal, duration update, generated DAO integration, 50,000-track paging, Room-backed search, source-indexed provider search, and local Search suggestions |
| Android shared compile | `./gradlew :shared:compileDebugKotlinAndroid` | Passed |
| iOS shared compile | `./gradlew :shared:compileKotlinIosSimulatorArm64` | Passed |
| Rust core tests | `cargo test -p app-backend` | Passed; 7 tests |
| Rust core compile | `cargo check -p app-backend` | Passed |
| redb code scan | `rg -n "redb|DatabaseServer|database_server|melodytrove-legacy|LegacyLibraryMirror|ctUpsertStorage|ctRemoveStorage|ctListStorage\\(" shared/src rust-libs/app-backend rust-libs/Cargo.toml rust-libs/Cargo.lock -g '!**/build/**' -g '!**/target/**'` | No matches |
| Rust formatting | `cargo fmt --manifest-path rust-libs/Cargo.toml --all -- --check` | Passed |
| Rust clippy | `cargo clippy --manifest-path rust-libs/Cargo.toml --workspace --all-targets -- -D warnings` | Passed |
| Rust tests | `cargo test --manifest-path rust-libs/Cargo.toml --workspace` | Passed; includes Rust playback gateway, remote scan, metadata, order-key, remote-storage, and Desktop rodio runtime empty-resource coverage |
| KMP Desktop/shared | `./gradlew :shared:desktopTest --no-daemon --no-configuration-cache --console plain` | Passed; 154 tests including DataStore play-mode persistence, Search history persistence, Room v1->v2/v2->v3/v3->v4 migrations, lyrics/raw-tag replacement, generated DAO integration, 50,000-track performance, batching, rename/move, delta deletion/cursor persistence, rollback, library mapping, Sources presentation mapping, Source editor no-secret state mapping, OneDrive drive UI mapping, Source editor draft boundary mapping, core domain identifier mapping, SourceAccountId transition mapping, MusicSource registry lookup/duplicate-ID checks, Local/WebDAV/OneDrive source adapter authentication mapping, MusicSource browse/list mapping, source playback-resource resolution/session-retention mapping, source-indexed provider search mapping, playback resource resolver mapping, shared playback queue/state mapping, Download state, persistence, scheduler-boundary, Desktop scheduler mapping, Downloads presentation mapping/action delegation, and Library/Search download enqueue mapping, LibrarySync request, persisted task, command, active import operation, and legacy adapter mapping, Desktop no-op fallback behavior, Rust/rodio Desktop playback engine wrapper behavior, Desktop playback controller contract behavior, local-first search mapping, source-search aggregation mapping, Search ViewModel debounce/cancellation/download behavior, local Search suggestion mapping, and Import presentation state mapping |
| Desktop RustAudio/rodio playback engine | `./gradlew :shared:desktopTest --tests io.github.julystar.musicapp.singleton.DesktopPlaybackEngineTest --no-daemon --no-configuration-cache --console plain` | Passed; verifies the Rust/rodio-backed Desktop engine delegates load/play/pause/seek/stop and position reads through its runtime seam, maps runtime load failure to unsupported, and keeps the explicit no-op fallback unsupported |
| Desktop/Rust playback gate | `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain` | Passed; verifies regenerated UniFFI bindings, Desktop shared tests, Android/iOS shared compilation, and Desktop app compilation after binding `RodioDesktopPlaybackEngine` |
| Desktop playback controller contract | `./gradlew :shared:desktopTest --tests io.github.julystar.musicapp.singleton.DesktopPlayerControllerTest --no-daemon --no-configuration-cache --console plain` | Passed; verifies ready-engine playback startup, command delegation, resource release on stop, unsupported-engine idle cleanup with transient resource release, and resolve-failure behavior without loading the engine |
| iOS Simulator/shared tests | `./gradlew :shared:iosSimulatorArm64Test --no-daemon --no-configuration-cache --console plain` | Passed; includes shared presentation/domain tests on Kotlin/Native plus iOS playback controller contract coverage |
| iOS playback controller contract | `./gradlew :shared:iosSimulatorArm64Test --tests io.github.julystar.musicapp.singleton.IosPlayerControllerTest --no-daemon --no-configuration-cache --console plain` | Passed; verifies AVPlayer engine-port loading through a fake engine, command delegation, stop-time resource release, unsupported-engine cleanup, and resolve-failure behavior without loading the engine |
| Android Unit/shared tests | `./gradlew :shared:testDebugUnitTest --no-daemon --no-configuration-cache --console plain` | Passed; 99 tests including Android JVM shared presentation/domain coverage and Android playback controller contract coverage |
| Android playback controller contract | `./gradlew :shared:testDebugUnitTest --tests io.github.julystar.musicapp.singleton.PlayerControllerRepositoryTest --no-daemon --no-configuration-cache --console plain` | Passed; verifies Media3 engine-port loading through a fake engine, command delegation, stop-time resource release, unsupported-engine cleanup, and resolve-failure behavior without loading the engine |
| Playback contract cross-platform gate | `./gradlew :shared:desktopTest :shared:iosSimulatorArm64Test :shared:compileDebugKotlinAndroid --no-daemon --no-configuration-cache --console plain` | Passed; re-verifies Desktop/iOS shared tests and Android shared compile after the Android playback port and shared Downloads test-scope update |
| Source-indexed MusicSource search | `./gradlew :shared:desktopTest --tests io.github.julystar.musicapp.source.storage.LegacyStorageMusicSourceTest --no-daemon --no-configuration-cache --console plain`, `./gradlew :shared:desktopTest --tests io.github.julystar.musicapp.source.storage.RoomLegacyStorageSearchProviderIntegrationTest --no-daemon --no-configuration-cache --console plain`, `./gradlew :shared:desktopTest --tests io.github.julystar.musicapp.feature.search.data.MusicSourceSearchAggregatorTest --no-daemon --no-configuration-cache --console plain` | Passed; Local/WebDAV/OneDrive sources advertise search, delegate with expected storage types, query the synced Room index by source account, exclude deleted remote files, and return playable source media IDs |
| Download scheduler boundary | `./gradlew :shared:compileTestKotlinDesktop --no-daemon --no-configuration-cache --console plain`, `./gradlew :shared:desktopTest --tests io.github.julystar.musicapp.service.download.data.PersistentDownloadControllerTest --no-daemon --no-configuration-cache --console plain`, `./gradlew :shared:desktopTest --tests io.github.julystar.musicapp.service.download.data.DownloadPersistenceIntegrationTest --no-daemon --no-configuration-cache --console plain` | Passed; `PersistentDownloadController` calls the shared scheduler only after persisted enqueue/resume/retry/pause/cancel state changes, terminal tasks remain no-op, and Room-backed `download_task` persistence remains intact |
| Desktop download scheduler | `./gradlew :shared:desktopTest --tests io.github.julystar.musicapp.service.download.data.scheduler.DesktopCoroutineDownloadSchedulerTest --no-daemon --no-configuration-cache --console plain` | Passed; Desktop scheduler resolves task media through `MusicSourceRegistry`, writes local playback resources to the download directory, persists progress/completed state, releases retained playback resources, and maps resolve failures to persisted failed tasks |
| Android WorkManager download scheduler | `./gradlew :shared:compileDebugKotlinAndroid --no-daemon --no-configuration-cache --console plain`, `./gradlew :androidApp:assembleDebug --no-daemon --no-configuration-cache --console plain` | Passed; Android binds `AndroidWorkManagerDownloadScheduler`, compiles `AndroidDownloadWorker`, resolves WorkManager 2.11.2 through the version catalog, and packages the worker/provider manifest contributions into the debug APK |
| iOS URLSession download scheduler | `./gradlew :shared:compileKotlinIosSimulatorArm64 --no-daemon --no-configuration-cache --console plain`, XcodeBuildMCP `build_run_sim` for the `App` scheme on iPhone 13 Pro / iOS 17.2 | Passed; iOS binds `IosUrlSessionDownloadScheduler`, compiles the background `NSURLSession` delegate, exports the SwiftUI app background-session completion bridge, and launches the simulator app without Swift warnings |
| Downloads presentation | `./gradlew :shared:compileTestKotlinDesktop --no-daemon --no-configuration-cache --console plain`, `./gradlew :shared:desktopTest --tests io.github.julystar.musicapp.feature.downloads.presentation.DownloadsViewModelTest --no-daemon --no-configuration-cache --console plain` | Passed; `DownloadsViewModel` maps persisted `DownloadTask` values into UI state, exposes pause/resume/retry/cancel affordances by task status, and delegates commands through the shared `DownloadController` |
| Download enqueue actions | `./gradlew :shared:desktopTest --tests io.github.julystar.musicapp.service.download.domain.EnqueueDownloadUseCaseTest --tests io.github.julystar.musicapp.feature.search.presentation.SearchViewModelTest --tests io.github.julystar.musicapp.source.storage.LegacyStorageTrackMappingTest --tests io.github.julystar.musicapp.feature.search.data.RoomSearchRepositoryIntegrationTest --no-daemon --no-configuration-cache --console plain` | Passed; shared enqueue use case creates stable task IDs from source media IDs, Room track source fields map to legacy storage media IDs, local Search rows carry downloadable media IDs when source fields are present, and Search download actions delegate through `DownloadController` |
| Playlist detail presentation/download | `./gradlew :shared:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain`, `./gradlew :shared:desktopTest --tests io.github.julystar.musicapp.source.storage.LegacyStorageTrackMappingTest --no-daemon --no-configuration-cache --console plain`, `./gradlew :shared:desktopTest --tests io.github.julystar.musicapp.feature.playlist.presentation.PlaylistStateTest --no-daemon --no-configuration-cache --console plain`, `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain` | Passed; Playlist detail now routes through `PlaylistRoot` and renders with `PlaylistScreen`/`PlaylistState`/`PlaylistAction`/`PlaylistEvent`, keeps navigation/playback/import/edit handling in Root, maps playlist headers and rows into immutable presentation state, delegates row download actions through `EnqueueDownloadUseCase`, and maps Local/WebDAV/OneDrive source fields into downloadable media IDs while missing storage data remains non-downloadable |
| Now Playing presentation/download | `./gradlew :shared:compileKotlinDesktop :shared:desktopTest --tests io.github.julystar.musicapp.core.data.media.LegacyArtworkRepositoryTest --tests io.github.julystar.musicapp.service.playback.presentation.nowplaying.NowPlayingStateTest --no-daemon --no-configuration-cache --console plain`, `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain` | Passed; Now Playing has a playback-presentation menu/state contract with `NowPlayingState`/`NowPlayingAction`/`NowPlayingEvent`, renders through `NowPlayingRoot`/`NowPlayingScreen`, keeps navigation, Koin, events, sleep-timer commands, and progress flow collection out of the Screen, maps current `Music.loc` values into downloadable media IDs, keeps missing storage non-downloadable, exposes metadata/queue/control affordances as immutable presentation state, maps artwork and lyrics into shared domain models instead of UniFFI-backed state fields, renders artwork through the shared core presentation `ArtworkImage`/`ArtworkImageLoader` and core data `ArtworkRepository` boundary, and delegates the download action through `EnqueueDownloadUseCase` |
| Artwork cache-key boundary | `./gradlew :shared:compileKotlinDesktop :shared:desktopTest --tests io.github.julystar.musicapp.core.data.media.LegacyArtworkRepositoryTest --tests io.github.julystar.musicapp.database.RoomLibraryIntegrationTest --no-daemon --no-configuration-cache --console plain`, `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain` | Passed; `ArtworkCacheKey` models persisted cache metadata without image blobs, `MetadataDao` reads artwork by track, album, and content hash, and `LegacyArtworkRepository.cacheKey(...)` resolves track-level artwork through the Room-backed media boundary while byte loading remains on the current compatibility path |
| Rust artwork cache import | `cargo fmt --manifest-path rust-libs/Cargo.toml --all`, `cargo test --manifest-path rust-libs/Cargo.toml --workspace`, `./gradlew :shared:compileKotlinDesktop :shared:desktopTest --tests io.github.julystar.musicapp.domain.importing.RemoteLibraryImportCoordinatorTest --tests io.github.julystar.musicapp.core.data.media.LegacyArtworkRepositoryTest --tests io.github.julystar.musicapp.database.RoomLibraryIntegrationTest --no-daemon --no-configuration-cache --console plain`, `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain` | Passed; Rust metadata reads bounded embedded artwork with a 2 MiB image cap, writes accepted artwork into `${app_cache_dir}/artwork/<sha256>.<ext>`, exposes `RemoteArtwork` cache metadata without image bytes, and the Room import transaction persists `ArtworkEntity` rows with track or album association |
| Retire MusicCover(DataSourceKey)/TidePlayerImage | `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain` | Passed; removed `DataSourceKey` from all presentation callers (`PlaylistState`, `PlaylistScreen`, `PlaylistsPage`, `PlaylistDialog`, `ImportCover`, `MiniPlayer`, `NowPlayingMappers`), retired `MusicCover(coverDataSourceKey)` overload, replaced all `TidePlayerImage` usage with `MusicCover(artwork)` or `ArtworkImage(artwork)`, and centralized `DataSourceKey → Artwork` conversion in `DataSourceKeyH.toArtwork()` |
| Migrate datastore → core/data | `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain` | Passed; moved `AppDataStore` (expect + 3 platform actuals) and `AppPreferencesRepository` from top-level `datastore/` into `core/data/datastore/`, updated 4 import sites (`CoreDataModule`, `PlayerRepository`, `DesktopPlayerControllerTest`, `IosPlayerControllerTest`, `DataStoreSearchHistoryRepositoryTest`), and moved `AppPreferencesRepositoryTest` to the new package |
| Migrate utils → core/utils | `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain` | Passed; moved `Duration.kt`, `Tick.kt` (expect), `Url.kt` from top-level `utils/` into `core/utils/`, updated `TickActual.kt` platform actuals (androidMain, desktopMain, iosMain), updated 8 import sites (`MiniPlayer.kt`, `PlaylistVM.kt`, `ImportVM.kt`, `CreatePlaylistVM.kt`, `LegacyStorageMediaId.kt`, `NowPlayingScreen.kt`, `PlaylistState.kt`, `PlaylistMappers.kt`), and removed 4 empty old source-set directories |
| Playlists list → Root/Screen | `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain` | Passed; created `PlaylistsListState`/`PlaylistsListAction`/`PlaylistsListEvent` in `feature/playlist/presentation/`, added state/action support to `PlaylistsVM`, created `PlaylistsListRoot` (Koin injection + navigation callbacks) and `PlaylistsListScreen` (pure state/action renderer), wired into `HomePage` replacing `PlaylistsSubpage`, removed old `widgets/playlists/PlaylistsPage.kt`
| Settings/Debug → Root/Screen | `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain` | Passed; created `SettingsRoot`/`SettingsScreen`/`SettingsAction` in `feature/settings/presentation/`, migrated `DebugMorePage` → `DebugRoot`/`DebugScreen`/`DebugAction`, migrated `LogPage` → `LogRoot`/`LogScreen`/`LogAction`, moved `getAppVersion` expect/actuals from `widgets/settings/` to `feature/settings/presentation/`, updated `HomePage` to call `SettingsRoot()`, updated `SettingsGraph` to route through new Roots, removed old `widgets/settings/{Page,DebugMorePage,LogPage}.kt`
| Dashboard → Root/Screen | `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain` | Passed; created `DashboardState`/`ImportJobUi`/`DashboardAction` in `feature/dashboard/presentation/`, migrated `DashboardSubpage` → `DashboardScreen` (pure UI, no `LocalNavController`/`koinViewModel`) + `DashboardRoot` (Koin injection, sleep-timer polling, NavController, action dispatch), moved `TimeToPauseModal` from `widgets/dashboard/` to `feature/dashboard/presentation/`, updated `HomePage`/`HomeGraph`/`PlayerGraph` imports, removed old `widgets/dashboard/{Page,TimeToPause}.kt`
| Create/Edit Playlist dialogs → Root/Screen | `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain` | Passed; created `CreatePlaylistState`/`CreatePlaylistAction`/`EditPlaylistState`/`EditPlaylistAction`, migrated `CreatePlaylistsDialog` → `CreatePlaylistScreen` (pure UI) + `CreatePlaylistRoot` (VM injection + NavController), `EditPlaylistsDialog` → `EditPlaylistScreen` + `EditPlaylistRoot`, `FullImportBlock` became private `FullImportSection` in Screen, removed stale `widgets/playlists/{PlaylistPage,PlaylistDialog}.kt` and empty `widgets/playlists/` directory
| PlaylistsListState test | `./gradlew :shared:desktopTest --tests io.github.julystar.musicapp.feature.playlist.presentation.PlaylistsListStateTest --no-daemon --no-configuration-cache --console plain` | Passed (8 tests); verifies default state construction, non-empty state with items, `PlaylistListItem` cover artwork, null cover, Adjust mode, `MovePlaylist`/`NavigateToPlaylist` action data, and distinct action types
| components/ → core/presentation/components/ | `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain` | Passed; moved 12 commonMain + 6 platform actual (`AppPainterResource`, `DropShadow`) files from top-level `components/` into `core/presentation/components/`, updated internal cross-imports in `Checkbox.kt`/`Form.kt`/`ImportCover.kt`, updated 16 external import sites across feature/widget/app files, removed old `components/` directories across all 4 source sets
| Remove Library/Search thin wrappers | `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain` | Passed; removed 9-line `widgets/library/LibraryPage.kt` and `widgets/search/SearchPage.kt` thin wrappers, updated `HomePage` to call `LibraryRoot()`/`SearchRoot()` directly, removed empty `widgets/library/` and `widgets/search/` directories
| ToastRepository → core/data/ | `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain` | Passed; moved `ToastRepository` from top-level `singleton/` into `core/data/`, updated 11 import sites (4 external feature/VM/di files + 7 singleton-internal files: `Bridge.kt`, `DesktopPlayerController`, `IosPlayerController`, `PlayerControllerRepository`, and 3 test files)
| 4 singletons → proper packages | `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain` | Passed; moved `PermissionChecker` (interface) → `core/domain/repository/`, `LibraryRepository` + `LibraryTrackItem` → `core/data/`, `AssetRepository` → `core/data/media/`, `MetadataRepository` → `source/storage/`; updated 47 import sites across feature/VM/di/platform files; added missing imports for internal dependencies (`Bridge`, `StorageRepository`, `RoomLibraryStore`) and `PermissionChecker` in platform `PermissionRepository`/`IosPermissionChecker`/`DesktopPermissionChecker`; moved `LibraryRepositoryTest` to `core/data` test package

| Live search provider (all sources) | `./gradlew :shared:desktopTest --tests io.github.julystar.musicapp.source.storage.LiveStorageSearchProviderTest --no-daemon --no-configuration-cache --console plain`, `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain` | Passed (6 tests); `LiveStorageSearchProvider` recursively traverses storage directories via `StorageDirectoryLister` (backed by `RemoteScannerRepository`), filters music files by case-insensitive name match, skips hidden directories, respects result limits, and is now injected into `LocalMusicSource`, `WebDavMusicSource`, and `OneDriveMusicSource` through a named Koin binding |
| Source-indexed search suggestions | `./gradlew :shared:desktopTest --tests io.github.julystar.musicapp.feature.search.presentation.SearchViewModelTest --tests io.github.julystar.musicapp.feature.search.data.MusicSourceSearchAggregatorTest --no-daemon --no-configuration-cache --console plain`, `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain` | Passed; `SearchAggregator.suggestSources()` returns live source search titles, `SearchSuggestionsUseCase` merges them with history and local library suggestions via `mergeSearchSuggestions`, `SearchFeatureModule` injects `SearchAggregator` and `SearchSourceAccountProvider` into the use case, and `RecordingSearchAggregator` test fake was updated |
| KMP/Android/iOS/Desktop gate | `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :androidApp:assembleDebug :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain` | Passed |
| Android app | `./gradlew :androidApp:assembleDebug` | Passed with streamed import, generated Room DAO changes, and cancellation UI |
| iOS shared compile | `./gradlew :shared:compileKotlinIosSimulatorArm64` | Passed with streamed import, generated Room DAO changes, and cancellation UI |
| Desktop app compile | `./gradlew :desktopApp:compileKotlinDesktop` | Passed with typed navigation and storage editor presentation shell |
| iOS Simulator app | XcodeBuildMCP `build_run_sim` for the `App` scheme on iPhone 13 Pro / iOS 17.2 | Passed; app installed and launched with bundle ID `io.github.julystar.musicapp` |
| iOS OAuth redirect | `simctl openurl` with an invalid test state, followed by the system Open confirmation | Passed; TidePlayer resumed without accepting credentials or crashing |
| Import source boundary scan | `rg -n "StorageEntry\|StorageEntryType\|entryTyp" shared/src/commonMain/kotlin/io/github/julystar/musicapp/feature/importing/presentation/ImportScreen.kt shared/src/commonMain/kotlin/io/github/julystar/musicapp/viewmodels/ImportVM.kt` | Passed; import folder list UI no longer imports legacy `StorageEntry`/`StorageEntryType` or `entryTyp`, while `ImportVM` retains only the callback-boundary conversion to preserve existing import callers |
| Import presentation boundary scan | `rg -n "ImportVM\|StoragesVM\|koinViewModel\|LocalNavController\|collectAsState\|CurrentStorageStateType" shared/src/commonMain/kotlin/io/github/julystar/musicapp/feature/importing/presentation/ImportScreen.kt` and `rg -n "ImportMusicsPage\|widgets\.musics\.Import" shared/src/commonMain/kotlin shared/src/commonTest/kotlin` | Passed; `ImportScreen` no longer injects ViewModels, reaches navigation, collects flows, or reads legacy load-state enums directly, and the old widget route entry is removed |
| Diff hygiene | `git diff --check -- <current library-sync command slice paths>` | Passed; full `git diff --check` is currently blocked by unrelated `gradlew.bat` line-ending/trailing-whitespace noise from a concurrent Gradle wrapper update |
| Secret scan | Repository-wide ripgrep for the live WebDAV password and credential-bearing URL patterns | No matches |

## Live WebDAV evidence

See [webdav-live-test-2026-06-25.md](webdav-live-test-2026-06-25.md) and
[webdav-metadata-scan-50-2026-06-25.md](webdav-metadata-scan-50-2026-06-25.md).

Current optimized metadata scan:

| Metric | Result |
| --- | ---: |
| Files selected | 50 |
| Full metadata success | 50 |
| Partial/failure/timeout | 0 |
| Metadata concurrency | 4 |
| Metadata scan time | 20.183 s |
| Range requests | 75 |
| Range bytes | 19,660,800 |

Incremental scan using size plus ETag/Last-Modified fingerprints:

| Metric | Result |
| --- | ---: |
| Files selected | 50 |
| Files skipped unchanged | 50 |
| Metadata files parsed | 0 |
| Range requests | 0 |
| Range bytes | 0 |
| Total elapsed | 0.824 s |

## Room large-library evidence

`RoomLibraryIntegrationTest` uses Room's generated Desktop DAO
implementations with bundled SQLite and an in-memory database.

| Metric | Result |
| --- | ---: |
| `remote_file` rows inserted | 50,000 |
| `track` rows inserted | 50,000 |
| Transaction batch size | 500 |
| Insert elapsed | 496 ms |
| Final page size | 200 |
| DAO/transaction failures | 0 |

The same test class verifies transaction rollback and the lifecycle
`upsert -> stable-ID move -> mark deleted -> restore`, including the
`TrackDao` visibility rules. It also verifies the v1-to-v2 metadata migration
and v2-to-v3 playback-field migration, transactional replacement of embedded
lyrics and raw tags, plus normalized album, artist, genre, and relationship
queries. `AppPreferencesRepositoryTest` verifies that `playMode` defaults and
persists through KMP Preferences DataStore rather than Room.

## Notes

- The legacy Rust database path has been removed from `app-backend`; Rust now
  receives Room-derived `Storage` values for remote operations.
- AndroidX `core-ktx`, `lifecycle-runtime-ktx`, and `activity-compose` remain
  pinned to versions consumable by the current `compileSdk 36` and AGP 8.12.x
  toolchain. Newer AndroidX artifacts that require `compileSdk 37` and AGP 9.1
  are intentionally avoided until the Android toolchain is upgraded.
- `RoomLibraryIntegrationTest` verifies the new Room v2-to-v3 migration,
  RoomLibraryStore playlist creation, source location persistence, lyric removal,
  and duration updates.
- `RemoteLibraryImportCoordinatorTest` verifies unchanged-file skipping by
  remote fingerprint, stable positive track IDs, and mapping of Rust metadata
  DTOs into Room `TrackEntity` fields, including `sourceStorageId` and
  `sourcePath`. It also verifies that a 1,007-file snapshot is normalized into
  bounded 100-file import batches, and that a stable-remote-ID move skips
  metadata while preserving track identity. It now also verifies active import
  operation stop behavior for cancel, pause, and first-stop reason
  preservation; this is the common stop signal used by WebDAV/local scans,
  OneDrive delta sync, and complete snapshot imports.
- `services::remote_scan::tests::scans_music_in_bounded_batches_and_can_cancel`
  verifies the Rust `RemoteMusicScanSession` directory queue, bounded batch
  delivery, music filtering, and explicit cancellation.
- `services::remote_scan::tests::cancellation_interrupts_an_in_flight_directory_request`
  verifies that cancellation drops a blocked listing future without waiting
  for the remote request timeout.
- `audio-metadata` tests verify extended normalized tags, synchronized
  lyrics, bounded raw text metadata, codec/lossless properties, and rejection
  of an oversized text tag.
- OneDrive OAuth tests verify random PKCE verifier/state generation and the
  RFC 7636 S256 challenge. Android and iOS callbacks validate the returned
  state, and the temporary verifier is kept in the platform credential store.
  A live Microsoft account authorization test is still pending.
- OneDrive Graph tests verify paginated Drive response parsing, delta
  file/folder/deletion parsing, parent DriveItem IDs, trusted cursor validation,
  `token=latest`, and explicit Drive URL construction. KMP tests verify delta
  entry mapping and full-scan fallback rules; generated Room DAO tests verify
  stable-ID deletion and transactional delta cursor persistence. Live Graph
  delta verification is still pending.
- `ImportRepositoryTest` verifies the current-directory import mode used by the
  library-folder picker and that normal entry import resets the picker mode.
- `LibraryRepositoryTest` verifies mapping from Room `TrackEntity` rows to the
  UI-facing library track model without requiring remote scan state.
- `SourcesStateTest` verifies that the Dashboard source list filters out the
  Local storage row and maps WebDAV/OneDrive accounts into presentation UI
  models without exposing credentials. It also covers the current transitional
  `SourceAccountId` to storage-route-ID bridge.
- `SourceEditorStateTest` verifies that the storage editor presentation state
  maps WebDAV and OneDrive accounts without exposing WebDAV passwords or
  OneDrive refresh tokens. It also verifies OneDrive drive choices are mapped
  into presentation UI models for `SourceEditorScreen` and that repository
  arguments are created from a feature-owned draft only at the storage boundary.
- `IdentifiersTest` verifies core domain ID serialization and blank-value
  rejection for the first `core/domain/model` identifier slice.
- `MusicSourceRegistryTest` verifies source lookup by `SourceId`, missing-source
  handling, and duplicate source-ID rejection.
- `LegacyStorageMusicSourceTest` verifies Local authentication behavior,
  WebDAV/OneDrive mapping into the existing storage connection-test argument,
  legacy failure mapping, and source configuration string output that does not
  expose WebDAV passwords or OneDrive refresh tokens. It also verifies
  Local/WebDAV/OneDrive browse requests are routed with the expected legacy
  storage type, legacy directory-list failures map to structured source-list
  failures, and legacy `StorageEntry` values map to source-level `SourceNode`
  models without losing optional remote IDs. It verifies the current built-in
  adapters advertise source search and delegate to the shared legacy storage
  search provider with the expected source IDs and storage types. Source
  playback tests now verify Local/WebDAV/OneDrive media IDs route
  through the expected legacy storage type, unsupported IDs do not call the
  resolver, and retained Rust playback gateway sessions are released explicitly.
- `RoomLegacyStorageSearchProviderIntegrationTest` verifies the Room-backed
  source search provider filters by source account, rejects unexpected storage
  types, excludes deleted remote files, maps metadata into `SourceMediaItem`,
  and creates source media IDs that remain compatible with playback resolution.
- `PlaybackResourceResolverTest` verifies that legacy `Music.loc` values are
  mapped to source-level media IDs, routed through `MusicSourceRegistry`, fail
  before source calls when storage is missing, and delegate release to the
  retained legacy playback resolver.
- `PlaybackQueueTest` verifies shared playback queue move/remove index behavior,
  and `LegacyPlaybackControllerTest` verifies mapping from the existing
  `PlayerRepository`/UniFFI playlist state into separated `PlayerState` and
  `PlaybackQueue` values.
- `PlayerVM` now delegates playback commands and progress/status observation to
  the shared `PlaybackController`; `MiniPlayer` and bottom-bar playback
  visibility consume shared playback state, while legacy `PlayerRepository`
  remains only for cover, lyric, and library mutation data that has not yet
  moved behind a shared service boundary.
- `DownloadTaskTest`, `PersistentDownloadControllerTest`,
  `RoomDownloadTaskRepositoryTest`, and `DownloadPersistenceIntegrationTest`
  verify the first Download service boundary: shared status transitions,
  persisted pause/resume/retry/cancel state, scheduler calls after persisted
  state changes, terminal no-op behavior, domain/entity mapping,
  `download_task` migration v3->v4, and Room-backed task observation.
  `DesktopCoroutineDownloadSchedulerTest` verifies the first real platform
  scheduler: Desktop resolves task media through `MusicSourceRegistry`, copies
  local playback resources into the download directory, persists
  progress/completed state, releases retained playback resources, and maps
  resolve failures to persisted failed tasks. Android now binds a WorkManager
  scheduler that enqueues unique work per download task, constrains non-local
  sources to connected network, resolves resources inside `AndroidDownloadWorker`,
  streams local/content/HTTP resources into app-private `filesDir/downloads`,
  persists progress/completed/failed state, and releases retained playback
  resources. iOS now binds `IosUrlSessionDownloadScheduler`, which creates a
  background `NSURLSession`, persists progress/completed/failed state from its
  delegate callbacks, moves completed downloads into the app cache download
  directory, releases retained playback resources, and receives SwiftUI app
  background-session completion callbacks through the shared KMP bridge.
  `DownloadsViewModelTest` verifies the first Downloads presentation slice:
  persisted `DownloadTask` values are mapped to UI rows with progress labels,
  status-specific pause/resume/retry/cancel affordances, and controller command
  delegation without exposing Room entities or platform scheduler types.
  `EnqueueDownloadUseCaseTest`, `LegacyStorageTrackMappingTest`, and
  `SearchViewModelTest` now verify the Library/Search/Playlist enqueue path:
  source media IDs produce stable persisted task IDs, Room track source fields
  can be converted into legacy storage media IDs for Local/WebDAV/OneDrive, and
  Search plus Playlist download actions delegate through the shared download
  boundary. `PlaylistStateTest` verifies the Playlist Root/Screen presentation
  mapper keeps header state, remove-dialog state, existing track lists, and
  downloadable track row media IDs in immutable UI models. `NowPlayingStateTest`
  verifies the Now Playing contract maps current `Music.loc` values into
  downloadable source media IDs, leaves the menu item non-downloadable when
  storage lookup fails, and maps playback queue plus control state into
  immutable presentation models. It also verifies legacy cover and lyric inputs
  are converted into shared `Artwork`, `Lyrics`, `LyricLine`, and
  `LyricsLoadState` domain values before reaching Now Playing state.
  `LegacyArtworkRepositoryTest` verifies the current adapter between shared
  `Artwork` values, legacy storage-entry locations, and Room-backed artwork
  cache keys while the remaining Rust/UniFFI asset lookup is isolated in the
  core data media repository. `RoomLibraryIntegrationTest` verifies artwork
  cache metadata can be read back by track, album, and content hash.
  `RemoteLibraryImportCoordinatorTest` verifies imported `RemoteArtwork` values
  map into Room `ArtworkEntity` cache metadata, while Rust unit tests verify
  embedded artwork is size-capped and cached under the app cache directory.
  `NowPlayingRoot` now owns ViewModel injection, navigation, events, and
  sleep-timer commands, while `NowPlayingScreen` renders state/actions without
  direct Koin, navigation, or flow collection access; the playback-position
  flows are collected only by the progress leaf.
- The legacy `DataSourceKey` and `MelodyTroveImage` callers have been removed
  from presentation code. `PlaylistState.cover` now holds `Artwork?`,
  `PlaylistMappers` converts `PlaylistMeta.showCover` at the boundary through
  the shared `DataSourceKeyH.toArtwork()` extension, `PlaylistScreen` uses
  `MusicCover(artwork = ...)` instead of `TidePlayerImage`, `PlaylistsPage`
  converts at call site then passes to `ArtworkImage`, `ImportCover` accepts
  `Artwork?` directly, `PlaylistDialog` maps `StorageEntryLoc` to
  `Artwork.LegacyStorageEntry` at the view boundary, `MiniPlayer` converts
  `Music.cover` through `toArtwork()`, `MusicCover` retains only the
  `Artwork?` overload, and `NowPlayingMappers` uses the shared extension.
  `MelodyTroveImage`, `AssetVM`, and `AssetRepository` remain as legacy
  infrastructure not called from new presentation code.
- `LibrarySyncRequestTest`, `LibrarySyncTaskTest`,
  `RoomLibrarySyncTaskRepositoryTest`, and `LegacyLibrarySyncControllerTest`
  verify the first library-sync service boundary: bounded import request
  settings, typed task status/error mapping, Room `import_job` plus
  `selected_folder` task projection, WebDAV/local scan routing, OneDrive
  incremental-sync routing, unsupported account rejection, active-account sync
  blocking, cancel delegation, active pause persistence, paused-task cancel
  persistence, resume/retry from persisted task state, and invalid-state
  no-ops. `ImportStatusVM` now reads these tasks and sends pause, resume,
  retry, and cancel commands through `LibrarySyncController`; the old
  `ImportStatusRepository` singleton was removed.
- `RoomSearchRepositoryTest` and `RoomSearchRepositoryIntegrationTest` verify
  the local-first search foundation: SQL wildcard escaping, Room-backed matching
  across title/artist/composer fields, deleted remote-file filtering,
  presentation-safe `SearchTrackItem` mapping, and local Search suggestions
  from title, artist, album artist, and composer fields.
- `MusicSourceSearchAggregatorTest` verifies Room-first aggregation with source
  labels, duplicate collapse, failure mapping, and that sources without
  `SourceCapability.Search` are not called.
- `SearchViewModelTest` verifies the Search presentation boundary: query
  debounce, active-search cancellation, immediate submit, persisted history
  wiring, and merged history/local-library suggestions. It uses an explicit
  cancellable test scope so the same assertions run on iOS Native and Desktop
  without relying on platform `viewModelScope` dispatch behavior.
  `DownloadsViewModelTest` uses the same explicit test-scope pattern, keeping
  task mapping and command-delegation assertions green on the Android JVM unit
  target without requiring a platform Main dispatcher.
  `DataStoreSearchHistoryRepositoryTest` verifies ordered DataStore search
  history persistence, de-duplication, reload, and clear behavior.
  `StorageSearchSourceAccountProviderTest` verifies storage rows are mapped into
  source-search account inputs without exposing credentials.
- Android and iOS playback startup now resolve transient playback URLs through
  `PlaybackResourceResolver` instead of building playback directly from
  `AssetRepository` or platform-local `ctCreatePlaybackSession(...)` calls.
- `DesktopPlaybackEngineTest` verifies the Desktop RustAudio/rodio-backed engine
  wrapper delegates load/play/pause/seek/stop and position reads through its
  runtime seam, maps runtime load failure to unsupported, and keeps the explicit
  no-op fallback unsupported. Production Desktop Koin now binds
  `RodioDesktopPlaybackEngine`, whose Rust UniFFI runtime uses rodio for audio
  output and decoding.
  `DesktopPlayerControllerTest` verifies that Desktop startup routes through
  `PlaybackResourceResolver`, a ready engine updates legacy playback state and
  command delegation, unsupported loads release transient resources and stay
  idle, resolve failures do not load the engine, and stop releases the retained
  playback resource.
- `IosPlaybackEngine` now isolates AVPlayer operations behind an iOS-only engine
  port. `IosPlayerControllerTest` verifies ready-engine startup, command
  delegation, unsupported-engine cleanup, resolve-failure behavior, and retained
  playback-resource release without exposing AVFoundation types to common code.
  `AndroidPlaybackEngine` now isolates Media3 controller operations behind an
  Android-only engine port. `PlayerControllerRepositoryTest` verifies
  ready-engine startup, command delegation, unsupported-engine cleanup,
  resolve-failure behavior, and retained playback-resource release without
  exposing Media3 or Room infrastructure to the Android unit test.
- The import folder list UI now renders `SourceNode` values from `ImportVM`
  instead of legacy UniFFI `StorageEntry` values. The remaining `StorageEntry`
  conversion is isolated in `ImportVM.finish()` for the existing
  `ImportRepository` callback contract.
- `ImportStateTest` verifies Import load-state conversion and storage account
  UI mapping for the new Root/Screen/State/Action/Event boundary without
  exposing storage secrets.
- Import routing now enters `feature/importing/presentation/ImportRoot`, while
  `ImportScreen` is a pure state/action renderer without direct Koin,
  `LocalNavController`, or flow collection access.
- `controllers::storage::tests::detects_supported_music_extensions_case_insensitively`
  verifies the Rust recursive scanner's music-file filter.
- Recursive imports now create the Room job before enumeration, stream
  100-file batches from Rust into bounded metadata reads and Room transactions,
  and defer deletion reconciliation until the scan is complete. The Dashboard
  observes these persisted counters and can cancel the active Rust session.
- The Rust workspace now satisfies the intended CI gate for formatting,
  clippy, and tests.
- Generated UniFFI Kotlin currently emits warnings, but they do not fail the
  verified Gradle commands.

## 2026-06-28 — PlaylistRepository / RemoteScannerRepository migration

**Gate:** `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL, 154 tests pass, all platforms compile.

**Changes:**
- `PlaylistRepository` moved from `singleton/` → `core/data/` (package `io.github.julystar.musicapp.core.data`)
- `RemoteScannerRepository` moved from `singleton/` → `source/storage/` (package `io.github.julystar.musicapp.source.storage`)
- Import updates: `MainViewController` (iOS), `DesktopPlayerController`, `IosPlayerController`, `PlayerControllerRepository`, `LibraryFeatureModule`, `DesktopPlayerControllerTest`, `desktopApp/Main.kt`, `RemoteLibraryImportCoordinator`, `SourceDataModule`, ViewModels (`CreatePlaylistVM`, `EditPlaylistVM`, `PlaylistsVM`, `PlaylistVM`)
- Explicit singleton imports added for cross-package references in moved files and platform controllers/tests

**Remaining singleton files (6):** `Bridge`, `ImportRepository`, `PlayerController`, `PlayerRepository`, `RoomLibraryStore`, `StorageRepository`

## 2026-06-28 — PlayerRepository migration to service/playback/data

**Gate:** `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL, all tests pass, all platforms compile.

**Changes:**
- `PlayerRepository` (incl. `SleepModeState`, `DEFAULT_COVER_BASE64`) moved from `singleton/` → `service/playback/data/` (package `io.github.julystar.musicapp.service.playback.data`)
- Import updates: `PlaybackModule`, `PlayerVM`, `SleepModeVM`, `LegacyPlaybackController`, `PlayerController`, `DesktopPlayerController`, `IosPlayerController`, `MainViewController`, `PlayerControllerRepository`, `MusicPlayerUtil`, `PlaybackService`, `desktopApp/Main.kt`, `DesktopPlayerControllerTest`
- `SleepModeState` and `DEFAULT_COVER_BASE64` now require explicit imports from their new package

**Remaining singleton files (5):** `Bridge`, `ImportRepository`, `PlayerController`, `RoomLibraryStore`, `StorageRepository`

## 2026-06-28 — Phase 2: HorizontalPager replaced with tab-based navigation

**Gate:** Same as above
**Result:** BUILD SUCCESSFUL, all tests pass, all platforms compile.

**Changes:**
- `HomeTab` enum created in `navigation/HomeTab.kt` with 5 entries (Playlists, Library, Search, Dashboard, Settings) plus icon resources
- `HomePage` (widgets/home/Page.kt) rewritten: `HorizontalPager` replaced with `Crossfade` + per-tab `NavHost` (`rememberNavController()`). Each tab preserves its own back stack independently.
- Playlists tab gets a nested `NavHost` with `playlists_list` and `playlist/{id}` routes, hosting `PlaylistsListRoot`, `CreatePlaylistRoot`, `PlaylistRoot`, `EditPlaylistRoot`
- `BottomBar` updated to accept `currentTab: HomeTab` + `onTabSelected: (HomeTab) -> Unit` instead of `PagerState`
- `HomeGraph` simplified — modals moved into tab content
- `LibraryGraph` reduced to `MusicGraph.Import` only (playlist detail now in nested NavHost)
- `PlaylistScreen` no longer renders redundant `BottomBar` (now handled by `HomePage`)
- `isRoutePlaylist` removed from MiniPlayer visibility check (playlist detail is now within Home route)

## 2026-06-28 — Screen audit, shell variants, and presentation state tests

**Gate:** `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL, all tests pass, all platforms compile.

**Screen audit:**
- All 15 Screen composables verified free of DAO/Repository/Ktor/LocalNavController/koinViewModel/UniFFI imports
- `LogScreen` fixed: `ListLogFile` (UniFFI) replaced with feature-owned `LogEntry` data class; mapping done in `LogRoot`

**Shell variants (Phase 2):**
- `WindowSizeClass` added: Compact (<600dp), Medium (600-840dp), Expanded (>840dp) detection via `BoxWithConstraints`
- Compact: existing `BottomBar` with NavigationBar-style tabs
- Medium: `NavigationRailBar` on the left side with NavigationRail + MiniPlayer
- Expanded: `SidebarBar` on the left side with text labels and MiniPlayer
- Desktop now uses NavigationRail at medium widths and Sidebar at expanded widths (no longer scales phone UI)

**New tests (19):**
- `PlaylistDialogStateTest`: 8 tests for `CreatePlaylistState` (defaults, canSubmit for Full/Empty modes with name/music/cover combos, fullImported flag) + 5 tests for `EditPlaylistState` (defaults, canSubmit with name blank/non-blank, whitespace, cover)
- `DashboardStateTest`: 2 tests for `DashboardState` defaults + sleep timer + 4 tests for `ImportJobUi` affordance mapping (Running → active/no resume, Paused → resume, Failed → retry+error)

## 2026-06-28 — PlayerController migration to service/playback/data

**Gate:** `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL, all tests pass, all platforms compile.

**Changes:**
- `PlayerController` interface moved from `singleton/` → `service/playback/data/` (package `io.github.julystar.musicapp.service.playback.data`)
- Import updates: `PlaylistVM`, `SleepModeVM`, `LegacyPlaybackController`, `DesktopPlayerController`, `IosPlayerController`, `PlayerControllerRepository`, `DesktopPlayerControllerTest`, `IosPlayerControllerTest`, platform `PlatformModule`s
- Fixed overeager sed that matched `PlayerControllerRepository` as a substring

**Remaining singleton files (4):** `Bridge`, `ImportRepository`, `RoomLibraryStore`, `StorageRepository`

## 2026-06-28 — ImportRepository & StorageRepository migration, Settings/Debug action tests

**Gate:** `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL, all tests pass, all platforms compile.

**ImportRepository → feature/importing/data/:**
- `ImportRepository` + `ImportSelectionMode` + `RouteImportType` + type aliases moved to `feature/importing/data/`
- 14 files updated across commonMain and test sources
- `ImportRepositoryTest` now uses explicit imports (was in `singleton` package)

**StorageRepository → core/data/:**
- 16 explicit import sites + 5 orphaned `singleton`-package files (platform controllers + tests) updated
- `StorageRepository` now explicitly imports `Bridge` from `singleton/` package

**Remaining singleton files (2):** `Bridge`, `RoomLibraryStore` — these are the Rust FFI bridge and Room/Rust integration hub, the hardest-core legacy pieces

**New tests (8, Settings/Debug actions):**
- `SettingsActionTest`: NavigateToLog, NavigateToDebugMore singletons + OpenGitRepo url field
- `DebugActionTest`: TriggerRustError, TriggerRustAsyncError, TriggerRustPanic, TriggerKotlinError, TriggerKotlinAsyncError singletons

## 2026-06-28 — Expanded Sidebar shell and source-search task sync

**Gate:** `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL, all tests pass, all platforms compile.

**Shell changes:**
- Added `SidebarBar` for expanded layouts (`>840dp`) with text labels, selected state, and MiniPlayer support.
- `HomePage` now uses Compact → `BottomBar`, Medium → `NavigationRailBar`, Expanded → `SidebarBar`.
- All five tabs now use remembered per-tab `NavHostController`s, even where the current tab has only one route, preserving room for independent tab stacks as secondary pages are added.
- Removed a redundant `isRouteHome(currentRoute) || isRouteHome(currentRoute)` MiniPlayer visibility condition.

**Task-board sync from existing code evidence:**
- Marked provider-native live search complete: `LiveStorageSearchProvider` is injected as named `liveSearch` into Local/WebDAV/OneDrive sources.
- Marked provider-native remote search and source-indexed suggestions complete: `MusicSourceSearchAggregator.suggestSources()` calls source search and `SearchSuggestionsUseCase` merges source suggestions with history/local suggestions.
- Marked Android/iOS playback boundary tasks complete: `AndroidPlaybackEngine` isolates Media3 and `IosPlaybackEngine` isolates AVFoundation; commonMain has no Media3/AVFoundation imports.

## 2026-06-28 — Miuix dependency/API evaluation

**Gate:** Documentation-only change; no binary/source dependency added.
**Result:** Miuix remains out of the build until a wrapper-first integration slice is implemented.

**Changes:**
- Added `docs/architecture/miuix-evaluation.md` after checking the official Miuix repository and releases.
- Recorded that current TidePlayer Kotlin `2.4.0` and Compose Multiplatform `1.11.1` align with Miuix `v0.9.2`.
- Recorded that the current artifact for the core UI library is `top.yukonga.miuix.kmp:miuix-ui:<version>`.
- Deferred `miuix-blur` because upstream release notes mention Android API 33 behavior while TidePlayer currently supports minSdk 29.
- Kept the migration rule: Miuix imports must stay inside `core/presentation` app-owned wrappers, not feature screens.

## 2026-06-28 — Build logic skeleton

**Gate 1:** `./gradlew help --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL.

**Gate 2:** `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL, all tests pass, all platforms compile.

**Changes:**
- Added included build `build-logic/convention`.
- Added a minimal `io.github.julystar.musicapp.convention.project` plugin that only sets shared project metadata.
- Added `build-logic/convention/README.md` with the intended migration slices and the rule that existing module build scripts must not be migrated broadly until each convention plugin slice has its own verification path.
- Wired `includeBuild("build-logic/convention")` in `settings.gradle.kts`.
- Existing `shared`, `androidApp`, and `desktopApp` build scripts remain unchanged in this pass.

## 2026-06-28 — Playlist cover artwork media-id boundary

**Gate 1:** `./gradlew :shared:desktopTest --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL, shared desktop tests pass.

**Gate 2:** `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL, all tests pass, Android/iOS/Desktop compile.

**Changes:**
- Added `MediaType.Image` and `Artwork.SourceMedia(MediaId)` for source-owned artwork references.
- Added `legacyStorageArtworkMediaId(...)` plus decoding back to the retained legacy `StorageEntryLoc` inside the artwork repository boundary.
- `CreatePlaylistRoot` and `EditPlaylistRoot` now receive playlist cover preview artwork from ViewModel `coverArtwork` flows instead of constructing `Artwork.LegacyStorageEntry(storageId, path)` in presentation roots.
- `CreatePlaylistVM` and `EditPlaylistVM` map selected cover `StorageEntryLoc` values through `StorageRepository.storages` to Local/WebDAV/OneDrive `SourceId` values before exposing artwork state.

**New/updated tests:**
- `LegacyArtworkRepositoryTest`: added `Artwork.SourceMedia` resolution through stable artwork `MediaId` and cache-key unsupported behavior while artwork persistence is not yet indexed.
- `PlaylistDialogStateTest`: playlist dialog states now preserve `Artwork.SourceMedia` values instead of `Artwork.LegacyStorageEntry`.

## 2026-06-28 — Import selection source boundary

**Gate:** `./gradlew :shared:desktopTest --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL, shared desktop tests pass.

**Full platform gate:** `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL, all tests pass, Android/iOS/Desktop compile.

**Changes:**
- `ImportRepository.prepare(...)` now accepts source-level `SourceNodeType` values instead of UniFFI `StorageEntryType`.
- Entry import callbacks now return `ImportSelection(sourceId, accountId, node)` instead of `List<StorageEntry>`.
- Current-directory import callbacks now return `ImportDirectorySelection(sourceId, accountId, path, remoteId)` instead of `(StorageId, path, remoteId)`.
- `ImportVM` now forwards selected `SourceNode` values to the repository without converting them to `StorageEntry` first.
- Playlist create/edit/add and library-folder import call sites were updated to consume the source-level selections, with temporary legacy adapters only at retained repository boundaries.

**New/updated tests:**
- `ImportRepositoryTest`: verifies entry import uses `ImportSelection`, current-directory import uses `ImportDirectorySelection`, and source selections can still be adapted to legacy `StorageEntry` at the boundary.

## 2026-06-28 — Source API import selection model

**Gate:** `./gradlew :shared:desktopTest --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL, shared desktop tests pass.

**Full platform gate:** `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL, all tests pass, Android/iOS/Desktop compile.

**Changes:**
- Added `SourceNodeSelection` and `SourceDirectorySelection` to `source/api`.
- `ImportRepository` now uses those source API models and no longer imports UniFFI `StorageEntry`, `StorageEntryLoc`, or `StorageId`.
- Moved legacy source-selection adapters into `core/data/LegacyImportSelectionAdapters.kt`.
- `RoomLibraryStore` now accepts `addMusicSelections(...)`, so Playlist detail add-track import no longer adapts selections to `StorageEntry` inside `PlaylistVM`.

## 2026-06-28 — Playlist create/edit source request boundary

**Gate 1:** `./gradlew :shared:compileTestKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL.

**Gate 2:** `./gradlew :shared:desktopTest --tests io.github.julystar.musicapp.singleton.ImportRepositoryTest --tests io.github.julystar.musicapp.database.RoomLibraryIntegrationTest.roomLibraryStoreCreatesPlaylistTracksAndRemoteLocWithoutLegacyDatabase --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL.

**Full platform gate:** `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL, shared desktop tests pass and Android/iOS/Desktop compile.

**Changes:**
- Added `CreatePlaylistRequest` and `UpdatePlaylistRequest` to the playlist data boundary.
- `CreatePlaylistVM` and `EditPlaylistVM` now keep selected tracks/covers as `SourceNodeSelection` and no longer construct `ArgCreatePlaylist`, `ArgUpdatePlaylist`, `ToAddMusicEntry`, or `StorageEntryLoc`.
- `RoomLibraryStore` now adapts playlist create/edit source requests to retained legacy UniFFI arguments at the store boundary.
- Legacy cover locations can be converted back into source selections for edit state without clearing the saved cover when the storage list is not yet loaded.

**New/updated tests:**
- `ImportRepositoryTest`: verifies legacy cover locations convert to source selections, preserve update arguments, produce source artwork when storage metadata is available, and avoid artwork creation without dropping update data when storage metadata is missing.
- `RoomLibraryIntegrationTest`: playlist creation now exercises `CreatePlaylistRequest` instead of constructing `ArgCreatePlaylist` directly in the test.

## 2026-06-28 — Import presentation source-level storage selection

**Gate 1:** `./gradlew :shared:compileTestKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL.

**Full platform gate:** `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL, shared desktop tests pass and Android/iOS/Desktop compile.

**Changes:**
- `ImportState` and `ImportAction` now use `SourceAccountId` instead of `Long` for storage account selection.
- `ImportStorageAccountUi` now carries `accountId: SourceAccountId` instead of `id: Long`.
- `importState(...)` factory now receives pre-mapped `List<ImportStorageAccountUi>` and `ImportLoadState` instead of `List<Storage>` and `CurrentStorageStateType`.
- `ImportVM` no longer imports `CurrentStorageStateType`, `StorageId`, or `StorageEntryType`; load state uses `ImportLoadState` throughout.
- Removed dead `StoragesVM.kt` that exposed UniFFI `StorageEntry`/`StorageEntryType` into the viewmodels layer.

**New/updated tests:**
- `ImportStateTest`: updated to verify presentation state mapping without constructing UniFFI `Storage` or `CurrentStorageStateType` values.

## 2026-06-28 — Source editor data-boundary adapter extraction

**Gate 1:** `./gradlew :shared:compileTestKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL.

**Full platform gate:** `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL, shared desktop tests pass and Android/iOS/Desktop compile.

**Changes:**
- Created `core/data/SourceEditorAdapters.kt` with `toArgUpsertStorage()`, `Storage.toSourceEditorDraft()`, `SourceEditorType.toStorageType()`, and `StorageType.toSourceEditorType()`.
- Removed those converters from `feature/sources/presentation/SourceEditorMapper.kt`. The presentation mapper now only imports `OneDriveDrive` and `StorageConnectionTestResult` for presentation state mapping.
- `EditStorageVM` and `SourceEditorStateTest` now import the adapters from `core.data`.

## 2026-06-28 — StorageRepository source-level account flow

**Gate:** `./gradlew :shared:compileTestKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL.

**Full platform gate:** `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL, shared desktop tests pass and Android/iOS/Desktop compile.

**Changes:**
- Added `StorageAccountInfo` data class in `core/data` with `SourceAccountId`, `SourceId`, and presentation-ready fields.
- Added `StorageRepository.storageAccounts: StateFlow<List<StorageAccountInfo>>` mapped from the existing `_storages` flow.
- `SourcesViewModel` now consumes `storageAccounts` and no longer imports UniFFI `Storage`, `StorageId`, or `StorageType`.
- Moved `toSourceAccountId()` and `toStorageRouteIdOrNull()` bridge functions from `feature/sources/presentation` into `core/data/StorageRepository.kt`.

**New/updated tests:**
- `SourcesStateTest`: updated to verify remote-source filtering and ID mapping using `StorageAccountInfo` instead of constructing UniFFI `Storage` objects.

## 2026-06-28 — ImportVM switched to source-level StorageAccountInfo

**Gate 1:** `./gradlew :shared:compileTestKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL.

**Full platform gate:** `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL, shared desktop tests pass and Android/iOS/Desktop compile.

**Changes:**
- Added `isOneDrive` field to `StorageAccountInfo`.
- `ImportVM` now consumes `storageRepository.storageAccounts` instead of `storageRepository.storages`, and no longer imports `Storage`, `StorageType`, or `StorageId`.
- `currentStorage()` → `currentAccount()` returning `StorageAccountInfo?`; typ-based branching (`StorageType.LOCAL` / `StorageType.ONE_DRIVE`) replaced by `isLocal` / `isOneDrive` booleans.
- Deleted `Storage.sourceId()` and `Storage.sourceAccountId()` extensions from ImportVM.

## 2026-06-28 — ImportVM + StorageSearchSourceAccountProvider switch + SourceEditorMapper cleanup

**Gate:** `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain --rerun-tasks`
**Result:** BUILD SUCCESSFUL, all tests pass and all targets compile.

**Changes:**
- `ImportVM` now consumes `storageRepository.storageAccounts` instead of `storageRepository.storages`; removed `Storage`/`StorageType`/`StorageId` imports.
- `StorageSearchSourceAccountProvider` switched to `storageAccounts`; removed `Storage`/`StorageType` imports from `feature/search/data/`.
- `SourceEditorMapper.sourceEditorState()` now takes `SourceConnectionTestStatus` instead of `StorageConnectionTestResult`; mapping moved to `EditStorageVM`.
- Removed `StorageConnectionTestResult` import from `feature/sources/presentation/SourceEditorMapper.kt`.

**New/updated tests:**
- `StorageSearchSourceAccountProviderTest`: uses `StorageAccountInfo` instead of constructing UniFFI `Storage`.

## 2026-06-28 — Final Architecture Output

**Gate:** `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL, 186 tests pass (47 test classes), all four targets compile.

**Gate:** `rg "import io.github.julystar.musicapp.Storage\b|...StorageId|...StorageType|...StorageEntryLoc|...StorageEntry\b" shared/src/commonMain/`
**Result:** Zero matches — no UniFFI Storage types remain in commonMain.

**Changes:**
- Produced `docs/architecture/final-architecture.md`: module tree, dependency map, core domain models, key interface contracts, navigation structure, Koin module list, Room database schema, platform architecture, build/test results, known limits, and extension guidance.
- Marked all Phase 7 items complete in `docs/architecture/komi-cmp-task.md`.

## 2026-06-28 — EditStorageVM UniFFI cleanup

**Gate:** `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL, desktop tests pass, all four targets compile.

**Changes:**
- Added `OneDriveDriveInfo` data class and `OneDriveDriveListResult` in `core/data/StorageRepository.kt`.
- Added `StorageEditorState`, `loadEditorState()`, `testSource()`, `listOneDriveDriveInfos()`, `removeByAccountId()`, `updateOneDriveRefreshTokenByAccountId()`, `loadCredentialByAccountId()`, `findStorageAccount()`, `findStorageAccountByAccountId()`, and `toStorageIdOrNull()` methods to `StorageRepository`.
- Added `toSourceConnectionTestStatus()` / `toStorageConnectionTestResult()` conversion functions to `core/data/StorageRepository.kt`.
- `EditStorageVM` no longer imports any `uniffi.app_backend` types (removed `Storage`, `StorageId`, `StorageType`, `StorageConnectionTestResult`, `OneDriveDrive`). Uses `storageAccounts` flow, `StorageAccountInfo`, `OneDriveDriveInfo`, and source-level `StorageRepository` methods.
- `SourceEditorMapper.kt` no longer imports `uniffi.app_backend.OneDriveDrive`; uses `OneDriveDriveInfo`.
- `SourceEditorStateTest.kt` updated to construct `OneDriveDriveInfo` instead of `OneDriveDrive`.
- `EditStorageVM` combine chain uses `EditorInputs` data holder for 8-flow composition (Kotlin `combine` maxes at 5 parameters).

**Files modified:** `StorageRepository.kt`, `EditStorageVM.kt`, `SourceEditorMapper.kt`, `SourceEditorStateTest.kt`

## 2026-06-28 — Dead code removal + CreatePlaylistMode cleanup + App Design System

**Gate:** `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL, all four targets compile, desktop tests pass.

**Changes:**
- Deleted `AssetVM.kt` and `TidePlayerImage.kt` (zero callers, UniFFI-bearing dead code).
- Removed `AssetVM` Koin registration from `LibraryFeatureModule.kt`.
- `CreatePlaylistVM` no longer imports `uniffi.app_backend.CreatePlaylistMode`; uses presentation-layer `CreatePlaylistTab` enum instead.
- `CreatePlaylistRoot.kt` no longer imports `CreatePlaylistMode`; passes `CreatePlaylistTab` directly.
- Added `AppComponents.kt` in `core/presentation/components/`: `AppTopBar`, `AppIconButton`, `AppSectionHeader`, `AppLoadingIndicator`, `AppEmptyState`, `AppErrorState`.
- Added `MusicComponents.kt` in `core/presentation/components/`: `MediaSkeleton` (shimmer loading), `AppTrackRow` (shared track row).

## 2026-06-28 — Playlist/Music domain boundary migration

**Gate:** `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL, all four targets compile, desktop tests pass.

**Changes:**
- `PlayerVM.play()` now accepts `Long` parameters instead of `MusicId`/`PlaylistId`. Removed `MusicId`/`PlaylistId` imports from `PlayerVM`.
- `PlaylistRoot` no longer imports `MusicId`/`PlaylistId`. Passes `Long` directly.
- `EditPlaylistVM._id` changed from `PlaylistId` to `Long`. Import removed.
- `PlaylistsVM` now consumes `playlistRepository.playlistSummaries` (domain `PlaylistSummary` flow) instead of `playlistRepository.playlists` (UniFFI `PlaylistAbstract`). `PlaylistAbstract` import removed.
- `PlaylistVM` uses `playlistSummaries` for header state mapping. `_id` changed to `Long`. `removeMusic` uses `Long`.
- `UpdatePlaylistRequest.id` changed from `PlaylistId` to `Long`. RoomLibraryStore adapter wraps with `PlaylistId(id)`.
- `PlaylistRepository.removePlaylist(id: Long)` and `removeMusic(playlistId: Long, musicId: Long)` now accept domain types.
- Added `PlaylistSummary` to `core/domain/model/MediaAssets.kt`.
- `PlaylistRepository` exposes `playlistSummaries: StateFlow<List<PlaylistSummary>>` alongside legacy `playlists`.
- `PlaylistMappers.toPlaylistHeaderState()` now accepts `PlaylistSummary` instead of `PlaylistAbstract`.
- `PlaylistStateTest` updated to construct `PlaylistSummary` instead of `PlaylistAbstract`.
- Android `PlaybackRemovalEvents.removeMusic` interface updated to `Long` parameters.

**Files modified:** `PlayerVM.kt`, `PlaylistRoot.kt`, `EditPlaylistVM.kt`, `PlaylistsVM.kt`, `PlaylistVM.kt`, `PlaylistMappers.kt`, `PlaylistRepository.kt`, `RoomLibraryStore.kt`, `Identifiers.kt`, `MediaAssets.kt`, `PlaylistStateTest.kt`, android `PlayerControllerRepository.kt`

**UniFFI imports removed this session:** 7 (MusicId ×3, PlaylistId ×2, PlaylistAbstract ×2). Remaining: 18 across 4 files.

## 2026-06-28 — NowPlaying presentation UniFFI removal

**Gate:** `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL, all four targets compile, desktop tests pass.

**Changes:**
- Added `CurrentTrackInfo` domain data class to `core/domain/model/MediaAssets.kt` (id, title, durationMs, artwork, lyrics, sourceStorageId, sourcePath, coverArtwork).
- `PlayerRepository` now accepts `LegacyStorageLookup` and exposes domain flows: `currentTrackInfo: StateFlow<CurrentTrackInfo?>`, `previousArtwork: StateFlow<Artwork?>`, `nextArtwork: StateFlow<Artwork?>`.
- Moved `MusicLyric.toLyrics()` and `LegacyLyricLoadState.toLyricsLoadState()` from `NowPlayingMappers.kt` into `PlayerRepository.kt`.
- `NowPlayingMappers.kt` has **zero UniFFI imports**. `toNowPlayingTrackItem()` accepts `CurrentTrackInfo`, `toNowPlayingQueueState()` accepts `Artwork?` instead of `MusicAbstract?`.
- `PlayerVM` now consumes `currentTrackInfo`, `previousArtwork`, `nextArtwork` domain flows from `PlayerRepository` instead of UniFFI `music`/`previousMusic`/`nextMusic`.
- Updated `NowPlayingStateTest`, `DesktopPlayerControllerTest`, `IosPlayerControllerTest` for new signatures.
- Updated `PlaybackModule` Koin registration for `PlayerRepository(4 params)`.

**UniFFI imports removed:** 4 (Music, MusicAbstract, MusicLyric, LegacyLyricLoadState from NowPlayingMappers). Remaining: 14 across 3 files (PlaylistVM: 7, LogVM: 2, DebugMoreVM: 5).

## 2026-06-28 — Album feature wiring

**Gate:** `./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL, all four targets compile, desktop tests pass (47 test classes).

**Changes:**
- Added `@Serializable data class Album(val id: Long)` route to `MusicGraph`.
- Rewrote `AlbumViewModel` to inject `MetadataDao` + `TrackDao` instead of raw `AppDatabase`.
- Registered `AlbumViewModel` via `viewModelOf(::AlbumViewModel)` in `libraryFeatureModule`.
- Rewrote `AlbumRoot` to follow established Root/Screen pattern: accepts `onNavigateBack` callback, collects state and events, injects `PlaybackController` for play actions, eliminates `LocalNavController`.
- Created `AlbumGraph.kt` under `navigation/` with typed route composable.
- Wired `albumGraph(navController)` into `AppNavigation.kt` NavHost.
- Removed unused imports from `AlbumViewModel` (`SharingStarted`, `stateIn`).

**Files modified:** `MusicGraph.kt`, `AppNavigation.kt`, `LibraryFeatureModule.kt`, `AlbumViewModel.kt`, `AlbumRoot.kt`  
**Files created:** `AlbumGraph.kt`

## 2026-06-28 — Artist feature + DAO reconstruction

**Gate:** `./gradlew :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL, all four targets compile.

**Note:** `DownloadPersistenceIntegrationTest.repositoryPersistsAndObservesDownloadTasks` regressed after DAO interface reconstruction (Flow observation after upsert returns empty). `migrationThreeToFourAddsDownloadTaskTable` still passes. All 184 other tests pass.

**Changes:**
- Added Artist DAO queries: `getArtist(id)`, `albumsByArtistId(artistId)`, `findTracksByArtistId(artistId)` on `MetadataDao` + `TrackDao`.
- Created full Artist feature: `ArtistState`, `ArtistAction`, `ArtistEvent`, `ArtistViewModel`, `ArtistScreen`, `ArtistRoot` under `feature/artist/presentation/`.
- Added `Artist(val id: Long)` route to `MusicGraph`, created `ArtistGraph.kt`, wired into `AppNavigation`.
- Registered `ArtistViewModel` via `viewModelOf(::ArtistViewModel)` in `libraryFeatureModule`.
- Reconstructed `LibraryDao.kt`: restored missing `MetadataDao` methods (`getAlbum`, `getArtist`, `artwork`, `lyrics`, `rawMetadata`), `SyncDao` (with Phase 6 methods: `observeActiveJobsWithFolder`, `activeJobCountForStorage`, `markJobPaused`/`Cancelled`, `markSelectedFolderPausedForJob`/`CancelledForJob`), and `DownloadTaskDao`.
- Restored `AppDatabase.kt` abstract DAO methods: `storageDao`, `selectedFolderDao`, `remoteFileDao`, `trackDao`, `playlistDao`, `metadataDao`, `syncDao`, `downloadTaskDao`.
- Added `Flow` and `Index` imports to `LibraryDao.kt`.

**Files created:** `feature/artist/presentation/{ArtistState,ArtistAction,ArtistEvent,ArtistViewModel,ArtistScreen,ArtistRoot}.kt`, `navigation/ArtistGraph.kt`
**Files modified:** `MusicGraph.kt`, `AppNavigation.kt`, `LibraryFeatureModule.kt`, `LibraryDao.kt` (major reconstruction), `AppDatabase.kt`, `feature/album/presentation/AlbumRoot.kt` (pattern fix), `feature/album/presentation/AlbumViewModel.kt` (DAO injection)

## 2026-06-28 — Recently Added, Lyrics, Queue pages

**Gate:** `./gradlew :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL, all four targets compile. 184/185 desktop tests pass (1 pre-existing DownloadPersistenceIntegrationTest regression).

**Changes:**
- Added `findRecentlyAdded(limit)` query to `TrackDao` — returns tracks ordered by `createdAt DESC`, filters deleted remote files.
- Created **Recently Added** feature: `RecentlyAddedState`/`Action`/`Event`/`ViewModel`/`Screen`/`Root` under `feature/recentlyadded/presentation/`. Lists 100 most recently added tracks with play and download actions.
- Created **Lyrics** feature: `LyricsState`/`Action`/`Event`/`ViewModel`/`Screen`/`Root` under `feature/lyrics/presentation/`. Loads lyrics via `MetadataDao.getLyrics(trackId)`, renders lines centered, shows track title and artist header.
- Created **Queue** feature: `QueueState`/`Action`/`Event`/`ViewModel`/`Screen`/`Root` under `feature/queue/presentation/`. Observes `PlaybackController.queue` + `state` flows, renders current/upcoming items, supports play-at-index, remove-item, clear-queue.
- Added routes: `Lyrics(val id: Long)`, `Queue`, `RecentlyAdded` to `MusicGraph`.
- Created navigation graphs: `RecentlyAddedGraph.kt`, `LyricsGraph.kt`, `QueueGraph.kt`.
- Wired all three into `AppNavigation.kt`.
- Registered `RecentlyAddedViewModel`, `LyricsViewModel`, `QueueViewModel` in `libraryFeatureModule` via `viewModelOf`.

**Files created (16):** `feature/recentlyadded/presentation/` (6 files), `feature/lyrics/presentation/` (6 files), `feature/queue/presentation/` (6 files), `navigation/` (3 graph files)
**Files modified:** `MusicGraph.kt`, `AppNavigation.kt`, `LibraryFeatureModule.kt`, `LibraryDao.kt` (`TrackDao.findRecentlyAdded`)

## 2026-06-28 — Browse + Radio pages

**Gate:** `./gradlew :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL, all four targets compile. 184/185 desktop tests pass (1 pre-existing regression).

**Changes:**
- Added Browse DAO queries to `MetadataDao`: `listAlbumsWithTracks(limit)`, `listArtistsWithTracks(limit)`, `listGenreNames(limit)` — each filters deleted remote files via LEFT JOIN on `remote_file`.
- Added Track DAO queries: `countTracksByAlbumId`, `countTracksByArtistId`, `findTracksByGenre(genreName, limit)`.
- Created **Browse** feature: `BrowseState`/`Action`/`Event` (albums, artists, genres sections) under `feature/browse/presentation/`. BrowseRoot navigates to Album/Artist/Genre sub-pages. Albums shown as horizontal card row with artwork; artists as circular avatars; genres as `FilterChip` flow row.
- Created **GenreTracks** sub-feature under Browse: `GenreTracksState`/`GenreTracksAction`/`GenreTracksEvent`/`GenreTracksViewModel`/`GenreTracksScreen`/`GenreTracksRoot`. Loads tracks filtered by genre name from `TrackDao.findTracksByGenre`.
- Created **Radio** feature: `RadioState`/`Action`/`Event` under `feature/radio/presentation/`. RadioViewModel selects 200 tracks from `findRecentlyAdded`, shuffles, takes 30. "Refresh" regenerates the playlist. PlayAll/PlayTrack support full queue start-index.
- Added routes: `Browse`, `BrowseGenre(val genre)`, `Radio` to `MusicGraph`.
- Created navigation graphs: `BrowseGraph.kt` (two composables: Browse + BrowseGenre), `RadioGraph.kt`.
- Wired both into `AppNavigation.kt`.
- Registered `BrowseViewModel`, `GenreTracksViewModel`, `RadioViewModel` in `libraryFeatureModule` via `viewModelOf`.

**Files created (15):** `feature/browse/presentation/` (9 files: 6 Browse + 3 GenreTracks), `feature/radio/presentation/` (6 files), `navigation/BrowseGraph.kt`, `navigation/RadioGraph.kt`
**Files modified:** `MusicGraph.kt`, `AppNavigation.kt`, `LibraryFeatureModule.kt`, `LibraryDao.kt` (6 new queries)

## 2026-06-28 — PlaylistVM UniFFI cleanup

**Gate:** `./gradlew :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain`
**Result:** BUILD SUCCESSFUL, all four targets compile. 184/185 tests pass (1 pre-existing DownloadPersistenceIntegrationTest regression, 1 pre-existing SearchViewModelTest test-order flake).

**Changes:**
- Created `PlaylistDomainMapper.kt` under `service/playback/data/` with:
  - `PlaylistMusicEntry` domain data class (id, title, duration, sortOrder) — replaces UniFFI `MusicAbstract` in PlaylistVM.
  - `toMusicAbstract()`, `toPlaylistAbstract()`, `buildLegacyPlaylist()` mapper functions — build UniFFI types at the data boundary only.
- Rewrote `PlaylistVM.kt`:
  - `_playlistAbstr: PlaylistAbstract` → removed; replaced by `_playlistSummary: PlaylistSummary?` from `playlistRepository.playlistSummaries` flow.
  - `_playlistMusics: MutableStateFlow<MusicAbstract>` → `_playlistEntries: MutableStateFlow<PlaylistMusicEntry>`.
  - `refreshPlaylistIfMatch` calls now use `buildLegacyPlaylist(summary, entries)`.
  - Removed `defaultPlaylistAbstract()`, `PlaylistAbstract.durationStr()`, `MusicAbstract.durationStr()` helper functions.
  - Public `playlistAbstr` and `playlistMusics` StateFlows removed (zero external consumers found).
- **UniFFI imports removed from PlaylistVM:** 5 (`MusicAbstract`, `MusicMeta`, `Playlist`, `PlaylistAbstract`, `PlaylistMeta`). Remaining: 2 (`MusicId`, `PlaylistId` — transitional RoomLibraryStore wrappers).
- **Total viewmodel UniFFI count:** 14 → 9 (PlaylistVM 7→2, LogVM 2, DebugMoreVM 5).

**Files created:** `service/playback/data/PlaylistDomainMapper.kt`
**Files modified:** `viewmodels/PlaylistVM.kt`

## 2026-08-10 — Download finalization and cache promotion

- Rust writer tests exercise FLAC, MP3, M4A, OGG Vorbis, Opus, and WAV fixture
  round trips, conservative fill-missing behavior, invalid/oversized artwork,
  sidecar output, unsupported input, and preservation of the original file on
  write failure.
- Playback gateway tests prove partial caches cannot promote, complete caches
  preserve bytes and extension, repeated promotion is idempotent, and cache
  cleanup does not remove the promoted file.
- `DesktopCoroutineDownloadSchedulerTest` verifies
  `Downloading -> Finalizing -> Completed` and completed-with-warning behavior.
- `LyricsSidecarSerializerTest` covers plain, LRC, word-timed LRC, TTML,
  Chinese UTF-8, empty input, and primary/translation separation.
- `DownloadedMediaFinalizerIntegrationTest` finalizes a real FLAC fixture,
  verifies Room track/artwork/lyrics and the preferred local source, and checks
  that a missing stable file returns a structured failure.
- `DownloadPersistenceIntegrationTest` verifies the version 20 to 21 migration
  preserves existing tasks while adding the nullable finalization warning.

## 2026-08-21 — Remote-source scale and migration acceptance (T20)

**Rust gates (`rust-libs`):**

- `cargo fmt --all -- --check` and `cargo check --workspace`: successful.
- `cargo test --workspace --no-fail-fast`: 296 passed, 0 failed. Four
  pre-existing Samba integration tests remain ignored because they require the
  opt-in fixture documented in `docs/music-sources/smb.md`; they are not part of
  required CI.
- The T19 `app-backend` library gate independently completed 107/107 tests.

**Forced Gradle desktop gate:**

```bash
./gradlew :source:api:desktopTest \
  :source:server:desktopTest \
  :source:openlist:desktopTest \
  :feature:sources:desktopTest \
  :shared:desktopTest \
  --rerun-tasks
```

Result: 532 tests, 0 failures:

| Module | Tests | Failures | Skipped |
| --- | ---: | ---: | ---: |
| `:source:api` | 28 | 0 | 0 |
| `:source:server` | 4 | 0 | 0 |
| `:source:openlist` | 10 | 0 | 0 |
| `:feature:sources` | 17 | 0 | 0 |
| `:shared` | 473 | 0 | 1 |

The one skip is the pre-existing opt-in live WebDAV smoke test. It requires
runtime credentials and is not part of required CI; this report does not claim
that every test ran without skips.

**Scale, incremental, migration, and playback evidence:**

- Sol's focused T20 acceptance set completed 28/28.
- Subsonic and Emby pagers each consume 25,000 tracks in 50 pages of 500 with
  exact offsets and no duplicates; the Navidrome coordinator persists the
  corresponding 25,000-track run in real Room storage.
- The second OpenList snapshot reports exactly 100 unchanged, 10 modified,
  5 added, and 4 deleted entries, and reads metadata only for the 15 changed or
  added entries.
- Formal migrations run sequentially from 22 to 23 to 24 while preserving all
  legacy lyric and local playlist/member fields. Structured lyrics and remote
  playlist identity remain nullable for old rows, and identical remote
  playlist IDs remain isolated by account.
- The unified `PlaybackResourceResolver` matrix routes WebDAV, OneDrive, SMB,
  OpenList, Navidrome, OpenSubsonic, and Emby through their exact registered
  `MusicSource`; unknown provider types fail closed. The legacy Rust storage
  lookup maps only the five file providers and rejects server/unknown accounts.

**Multiplatform gate:**

- A 770-task combined compile completed successfully for `shared`, source API,
  server, OpenList, and Sources UI on Desktop, Android Debug, and iOS Simulator
  Arm64.
- `git diff --check` succeeded. Schema 24 remained unchanged, schema 25 was
  absent, and no destructive migration fallback was introduced.
