# TidePlayer rename audit

Date: 2026-08-08

## Scope

This audit covers tracked source paths, Kotlin/Java packages, Gradle build logic,
Compose resources, Android, iOS/Xcode, Desktop storage, Room, Rust crates,
UniFFI bindings, scripts, workflows, the design prototype, and documentation.

## Canonical result

| Area | Result |
| --- | --- |
| Product name | `TidePlayer` |
| Repository slug | `TidePlayer` |
| Kotlin/Java package | `io.github.julystar.musicapp` |
| Android | `AppApplication`, `Theme.App`, stable application ID, `tideplayer` primary URL scheme plus both legacy schemes |
| iOS | `App.xcodeproj`, `App` target/scheme, `AppMain`, `SharedKit`, `TidePlayer.app` |
| Desktop | standard platform data root under `TidePlayer` with idempotent legacy migration |
| Persistence | `AppDatabase`, Room schema version 19, `library.db`, `settings.preferences_pb` |
| Rust/UniFFI | `app-backend`, `app_backend`, `uniffi.app_backend` |
| Internal UI/build names | brand-neutral names such as `AppTheme`, `Design*`, and `*ConventionPlugin` |

The Room version remains 19 because the rename does not change the relational
schema. Existing schema JSON files remain under the stable database identity and
their identity hashes are not modified.

## Compatibility

The compatibility chain is `TideTunes -> MelodyTrove -> TidePlayer`.

Desktop startup now targets the standard platform data directory named
`TidePlayer`. If it is not initialized, migration checks the former standard
`MelodyTrove` directory first and the original `~/.tidetunes` directory second.
The previous MelodyTrove layout already uses `library.db` and
`settings.preferences_pb`; the original TideTunes layout continues to map its
product-branded filenames to those stable names. The migration keeps restart-safe
markers, atomic moves where supported, verified copy fallback, and SQLite header
validation.

Android and iOS keep the stable application/bundle ID
`io.github.julystar.musicapp`. Their primary OAuth/deep-link scheme is
`tideplayer`; `melodytrove` and `tidetunes` remain registered for compatibility.
Settings backup discovery accepts all three product-name generations, while new
backups are written with the TidePlayer name.

## Automated checks

The merge gate remains:

```bash
node scripts/audit-release.js
node scripts/audit-branding.js
git diff --check
./gradlew --no-daemon --stacktrace \
  :androidApp:assembleDebug \
  :desktopApp:compileKotlinDesktop \
  :shared:desktopTest \
  :shared:compileKotlinIosSimulatorArm64
cargo fmt --manifest-path rust-libs/Cargo.toml --all -- --check
cargo clippy --manifest-path rust-libs/Cargo.toml \
  --workspace --all-targets -- -D warnings
cargo test --manifest-path rust-libs/Cargo.toml --workspace
xcodebuild -project iosApp/App.xcodeproj -scheme App \
  -configuration Debug -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO build
```

The repository slug is now `JulyStar-Lv/TidePlayer`. GitHub keeps redirects from
the historical repository URL; repository links and external integrations should
use the current slug directly.
