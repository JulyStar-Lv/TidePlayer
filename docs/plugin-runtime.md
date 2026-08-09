# TidePlayer Plugin Runtime

TidePlayer implements JavaScript metadata plugins as Lyrico Plugin API v1–v3 compatible
`MetaSource` instances. Plugins are imported from local ZIP files and are never treated as
general playback `MusicSource` implementations.

## Production pipeline

The production graph is:

```text
Plugin ZIP -> PluginInstaller -> PluginRepository -> InstalledPlugin
  -> PluginMetaSourceRegistry -> LyricoJsMetaSource -> MetadataLookupUseCase
  -> PluginRuntimeManager -> QuickJS/Host API
```

`PluginMetaSourceRegistry` observes the Room-backed repository and updates the shared
`MetaSourceRegistry`. It does not create JavaScript runtimes. A runtime is created lazily only
when a source is called.

The Settings screen exposes a Plugins destination for local ZIP import, enable/disable,
manual/automatic/batch permissions, manifest capabilities, `configFields`, cache clearing,
last runtime error, and uninstall. Password fields use masked input; dropdown options and
manifest dependencies are preserved, while markdown fields are display-only.

## Import and manifest behavior

- Single-plugin and aggregate ZIPs are extracted by the bounded Rust extractor.
- Extraction rejects path traversal, absolute paths, links, excessive file count/depth, and
  excessive uncompressed size.
- Installation validates reverse-domain plugin IDs, API versions 1–3, `minHostApiVersion`, version,
  capabilities, `.js` entry, include directories, supported icon type, and config fields.
- `author` and `description` are optional. Empty capabilities default to `searchSongs`, matching
  the upstream v3 host behavior; an explicit non-empty list must include `searchSongs`.
- Official config field types are `text`, `password`, `number`, `switch`, `dropdown`, `textarea`,
  and `markdown`. Legacy `boolean` and `select` aliases remain accepted. Dropdown options and
  `match`/`and`/`or`/`not` dependencies are validated and rendered by Settings.
- Markdown fields are never persisted or sent in request `config`; required editable fields must
  be populated before Settings saves the plugin configuration.
- Entry, include, and icon paths must resolve below the plugin root and exist with the expected
  type.
- Installation uses staging plus replacement; a failed update leaves the prior version usable.
  Downgrades are rejected.
- New plugins are persisted with `enabled = false`, manual permission enabled, and automatic and
  batch permissions disabled.
- Disabling and uninstalling invalidate the runtime and clear private candidate contexts.
  Uninstall also removes plugin files, configuration, cache, and database records.

## Lyrico v3 requests and results

- `searchSongs` sends `keyword`, `page`, `pageSize`, `separator`, and merged `config`.
- `getLyrics` sends `{ song, config }`. The nested song includes the candidate fields and the
  same plugin's private `internal` value, plus matching `sourceId` and `pluginId`.
- `searchCovers` sends `keyword`, `pageSize`, and merged `config`.
- Song parsing accepts arrays and `items`, `results`, `songs`, or `data` wrappers, documented
  aliases, array artists, numeric IDs, simple `fields`, and per-plugin private `internal`.
- Cover parsing accepts URL strings, explicit cover objects, song-shaped objects, and the same
  wrappers plus `covers`.
- Lyrics parsing accepts structured line/word timing, translated and romanized tracks, all v3
  raw lyric types, `notFound`, and the legacy MelodyTrove `lines` shape.
- The QuickJS boundary returns JavaScript strings directly and JSON-serializes other values.
  `null` and `undefined` normalize to the JSON text `null`; JSON strings are not double encoded.

Private `internal` data is stored in a bounded, TTL-based, thread-safe token store. Tokens are
random and scoped to the producing plugin. The value is not written to normal music tags or
passed to another plugin.

## Applying And Resetting Metadata

The manual metadata dialog writes an accepted candidate into the canonical Room `track` fields
and normalized album/artist relationships. It stores only stable provenance—the plugin ID,
candidate ID, and apply time—and locks descriptive fields against later background file scans.
The source audio file is never modified.

“Reset from file” performs a new core-tag read from the preferred available Local, WebDAV, or
OneDrive source, replaces the canonical descriptive fields and relationships, clears plugin
provenance, and unlocks the track. Track identity, playlists, playback history, artwork, and
lyrics are preserved. This is a reset to the file's current tags, not a historical snapshot.

## Permissions

`enabled` is the master switch for every formal lookup. An enabled plugin must additionally
allow the requested `PluginLookupMode`:

| Mode | Required flag |
| --- | --- |
| `MANUAL` | `allowManualLookup` |
| `AUTOMATIC` | `allowAutomaticLookup` |
| `BATCH` | `allowBatchLookup` |

Denied calls throw `PluginLookupDeniedException` with the plugin ID and lookup mode. Automatic
and batch selection therefore never opts a newly installed plugin in implicitly.

## Runtime lifecycle and limits

- Each plugin has an isolated QuickJS worker; calls for one plugin are serialized while different
  plugins do not share an execution lock.
- The cache key includes plugin ID, version code, update timestamp, and bundled source hash.
- Load and call operations have distinct operation IDs and use the QuickJS interrupt handler for
  timeout, cancellation, runtime close, and poisoned state.
- Timeout, cancellation, OOM, poisoned, or internal failures invalidate the Kotlin cache, clear
  the plugin's private candidate contexts, and destroy the worker. A later call creates a fresh
  runtime.
- A poisoned or closed worker rejects new commands before queueing, avoiding an orphaned request
  during worker shutdown.
- `close`, runtime invalidation, and `closeAll` are idempotent; close uses a bounded join.
- Closing the Koin application shuts down the registry and all plugin runtimes. Desktop window
  exit, Android's emulated/test-process termination callback, and iOS application termination
  invoke that close path. A normal Android process kill relies on OS process resource reclamation.

Default `PluginRuntimeSettings` values are 64 MiB heap, 2 MiB stack, 10 second load timeout,
15 second call timeout, 30 second manual-operation timeout, 16 MiB HTTP response limit, and
4 MiB per-plugin cache limit.

## Host API and security

The bootstrap exposes `Platform.app`, `Platform.runtime`, `Platform.cache`, `Platform.crypto`,
`Platform.base64`, `Platform.bytes`, `Platform.compression`, `Platform.http`, `Platform.xml`, and
`Platform.log`, including the Lyrico global app/runtime shortcuts. Cache paths are isolated by a
hash of the plugin ID.

HTTP adds a TidePlayer User-Agent, supports text/binary bodies and responses, and applies request
and response limits. HTTPS hostnames use the platform resolver and network stack so TUN/VPN
synthetic DNS works without assuming a particular address range; TLS authenticates the requested
hostname. Plaintext HTTP hostnames are resolved, private-address checked, and pinned. Literal
private IPv4/IPv6 targets remain blocked unless explicitly enabled, and every redirect is
revalidated under the same rules. Sensitive header names and response bodies are not emitted to
plugin logs.

## Validation

The focused contract suite includes generated API v3 ZIPs. One returns `JSON.stringify(...)` and
verifies import, default permissions, enablement, bundle/load, `searchSongs`, private `internal`
round-trip to `getLyrics`, structured lyric parsing, `searchCovers`, and runtime close. The other
uses all official config field types, dropdown options, dependencies, optional descriptive fields,
and empty capabilities to verify current upstream manifest compatibility.

See [testing/test-report.md](testing/test-report.md) for the commands and current platform result.

## Known limitations

- TidePlayer does not ship or download third-party plugin ZIPs; real plugins remain user supplied.
- `include(path)` is a compatibility no-op after all configured include-directory JavaScript has
  been bundled in deterministic order. Plugins cannot read arbitrary files at runtime.
- The Gradle build configures `iosSimulatorArm64`, not an x86_64 simulator target. Generic Xcode
  builds must select arm64 (or exclude x86_64).
