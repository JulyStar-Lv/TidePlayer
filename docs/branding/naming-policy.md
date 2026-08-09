# TidePlayer naming policy

Date: 2026-08-08

TidePlayer is the product brand. Use it only for text and artifacts that a user
or operator sees: application labels, package products, release artifacts,
diagnostics, backup exports, documentation, and protocol user agents.

The repository uses stable, brand-neutral technical identifiers:

| Scope | Required name |
| --- | --- |
| Kotlin/Java root package | `io.github.julystar.musicapp` |
| Shared framework bundle ID | `io.github.julystar.musicapp.shared` |
| Build-logic package | `io.github.julystar.musicapp.buildlogic` |
| Android application ID | `io.github.julystar.musicapp` |
| iOS bundle ID | `io.github.julystar.musicapp` |
| Shared Apple framework | `SharedKit` |
| Rust crate/library | `app-backend` / `app_backend` |
| UniFFI package | `uniffi.app_backend` |
| Database class/file | `AppDatabase` / `library.db` |
| Preferences file | `settings.preferences_pb` |
| Desktop data directory | platform data root plus `TidePlayer` |
| Primary deep-link scheme | `tideplayer` |

Internal types should describe responsibility rather than brand. Prefer names
such as `AppTheme`, `AppApplication`, `AppLogger`, `PlatformBackHandler`, and
`LyricsView`. Do not introduce product-branded service, repository, state,
screen, plugin, database, theme, playback, or build-logic type names.

`MelodyTrove` and the older `TideTunes` identifiers are legacy compatibility
identifiers. They may appear only in centralized `migration/Legacy*.kt`
catalogs, compatibility registrations, migration tests, historical migration
documentation, and explicitly audited compatibility code. New data must never
be written with either legacy brand. Run `node scripts/audit-branding.js`
before merging.
