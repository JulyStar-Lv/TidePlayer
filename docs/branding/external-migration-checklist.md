# External migration checklist

Date: 2026-08-09

Repository code has completed the in-app product rename to `TidePlayer`, but it
cannot complete the following operator-owned changes. Complete the applicable
items before publishing a release under the TidePlayer brand.

- Verify GitHub's redirect from the historical `JulyStar-Lv/MelodyTrove` URL to
  the current `JulyStar-Lv/TidePlayer` repository.
- Verify GitHub branch protection, Actions secrets, repository topics, repository
  description, badges, Release links, and external automation after the repository
  rename.
- Register `tideplayer://oauth2redirect/` with the OneDrive/Azure application.
  Keep `melodytrove://oauth2redirect/` and `tidetunes://oauth2redirect/` registered
  for compatibility during the transition.
- Keep Android signing, store listings, Firebase/OAuth clients, app links, and MDM
  policies aligned with the stable application ID `io.github.julystar.musicapp`.
- Keep the Apple App ID, provisioning profiles, capabilities, Keychain groups,
  associated domains, and App Store Connect record aligned with the stable bundle
  ID `io.github.julystar.musicapp`.
- Update release automation, package registries, download links, website metadata,
  social links, screenshots, store copy, and third-party plugin host allow lists to
  the `TidePlayer` product name.
- Verify macOS notarization identities, Windows signing metadata, Linux package
  metadata, installer names, and update feeds use the `TidePlayer` product name.

## Stable application identifiers

The TidePlayer rename deliberately keeps the platform application identifiers
stable:

- Android application ID: `io.github.julystar.musicapp`
- iOS bundle ID: `io.github.julystar.musicapp`
- Kotlin/Java root package: `io.github.julystar.musicapp`
- Database: `library.db`
- Preferences: `settings.preferences_pb`
- Rust/UniFFI: `app-backend` / `app_backend` / `uniffi.app_backend`

Because these identifiers are not changing in this rename, the normal Android and
iOS application sandboxes remain associated with the same app identity. Historical
compatibility code is still retained for older pre-release identifiers and data
layouts where those values are accessible.

## Legacy data and integration compatibility

The compatibility chain is:

```text
TideTunes -> MelodyTrove -> TidePlayer
```

Desktop migration checks the previous standard `MelodyTrove` data directory before
the original `~/.tidetunes` layout. Android and iOS register `tideplayer` as the
primary URL scheme and retain `melodytrove` and `tidetunes` as compatibility
schemes. New data, backups, diagnostics, package products, protocol user agents,
and OAuth configuration should use TidePlayer identifiers only.

## Release verification

- Install over an accessible MelodyTrove/TideTunes data set and confirm library,
  playlists, settings, plugins, downloads, and credentials remain usable.
- Launch a clean install and confirm it creates only current database, preferences,
  service IDs, TidePlayer Desktop data paths, and TidePlayer-named exports.
- Exercise `tideplayer://oauth2redirect/` plus both legacy URL schemes, then confirm
  all newly emitted redirects use `tideplayer`.
- Inspect a settings backup and diagnostics ZIP for `application`, `packageId`, and
  format/schema version fields; new artifacts must identify the application as
  `TidePlayer`.
- Confirm Android, iOS, and Desktop visible labels, About pages, and Safe Mode
  text display `Tide Player`; installers, diagnostics, and release artifacts keep
  the compact `TidePlayer` product name.
