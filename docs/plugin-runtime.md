# TidePlayer Plugin Runtime

TidePlayer implements JavaScript metadata plugins as Lyrico Plugin API v1–v4 compatible
`MetaSource` instances. Plugins are imported from local ZIP files and are never treated as
general playback `MusicSource` implementations.

The current protocol ceilings are intentionally different: the maximum Plugin API is **4** and
the maximum Platform Host API is **3**. Manifest `apiVersion` selects the plugin function/result
contract; `minHostApiVersion` declares the minimum Platform API the script needs. Plugin API 4
does not imply or introduce Host API 4.

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
- Installation validates reverse-domain plugin IDs, API versions 1–4, Host API versions 1–3,
  version,
  capabilities, `.js` entry, include directories, supported icon type, and config fields.
- `author` and `description` are optional. Empty capabilities default to `searchSongs`, matching
  historical host behavior. Explicit capabilities may independently contain any combination of
  `searchSongs`, `getLyrics`, and `searchCovers`; lyrics-only and cover-only plugins are valid.
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

## Lyrico requests and results

- `searchSongs` sends `keyword`, `page`, `pageSize`, `separator`, and merged `config`.
- For Plugin API 1–3, `getLyrics` sends `{ song, config }` and accepts a single lyrics result, an
  LRC string, `null`, or the historical TidePlayer structures. The result is exposed as one
  compatibility `MetaLyricsCandidate` while the original `getLyrics(): MetaLyrics?` API remains.
- For Plugin API 4, `getLyrics` also sends `page` and `pageSize` and preserves every valid result
  from a direct array or an `items`, `results`, or `candidates` wrapper. Each candidate must carry
  `tags.ti`, `tags.ar`, `tags.al`, and `tags.date`; missing judgment metadata makes that candidate
  invalid instead of producing an ambiguous row.
- The nested song includes candidate fields, matching `sourceId`/`pluginId`, and only the same
  plugin's private `internal` value. An API 4 lyrics-only source receives an exact
  `id = "local-song"` request with empty `internal`, allowing it to perform its own song search.
- For Plugin API 1–3, `searchCovers` sends `keyword`, `pageSize`, and merged `config`. API 4 also
  sends `page` and can send a local or same-source `song`.
- Song parsing accepts arrays and `items`, `results`, `songs`, or `data` wrappers, documented
  aliases, array artists, numeric IDs, simple `fields`, and per-plugin private `internal`.
- API 1–3 cover parsing accepts URL strings, explicit cover objects, song-shaped objects, and the
  same wrappers plus `covers`. API 4 requires title, artist, album, date, and a URL; `id` remains
  optional. Candidate ID and `sourceId` are separate, and `sourceId` is always the plugin ID.
- Lyrics payload parsing across all versions accepts structured line/word timing, translated and
  romanized tracks, raw plain/verbatim/enhanced/multi-person LRC, TTML, documented snake_case
  aliases, `notFound`, and the historical `lines` shape.
- The QuickJS boundary returns JavaScript strings directly and JSON-serializes other values.
  `null` and `undefined` normalize to the JSON text `null`; JSON strings are not double encoded.

`MetaSource.capabilities` is a formal source-layer contract. The lookup use case selects sources
before calling them: song search calls only `SEARCH_SONGS`, lyrics lookup only `GET_LYRICS`, and
cover search only `SEARCH_COVERS`. A capability mismatch is skipped and never recorded as a
plugin runtime error. Automatic and batch lyrics selection reuse the existing match scoring and
remain deterministic within one source; manual mode keeps the complete API 4 candidate list.

Private `internal` data is stored in a bounded, TTL-based, thread-safe token store. Tokens are
random and scoped to the producing plugin. The value is not written to normal music tags or
passed to another plugin.

## Applying And Resetting Metadata

The manual metadata dialog shows metadata, lyrics, and cover candidates. Lyrics and covers retain
title, artist, album, date, and source information; cover rows include an image preview. Applying
uses the explicitly selected lyric and cover rather than silently discarding API 4 alternatives.
It writes an accepted metadata candidate into the canonical Room `track` fields
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

`Platform.runtime.getInfo()` reports the host ceiling (`pluginApiVersion = 4`,
`hostApiVersion = 3`), QuickJS engine information, OS/architecture, and the exact
`supportedHostApis` list implemented by Rust.

HTTP adds a TidePlayer User-Agent, supports text/binary bodies and responses, and applies request
and response limits. HTTPS hostnames use the platform resolver and network stack so TUN/VPN
synthetic DNS works without assuming a particular address range; TLS authenticates the requested
hostname. Plaintext HTTP hostnames are resolved, private-address checked, and pinned. Literal
private IPv4/IPv6 targets remain blocked unless explicitly enabled, and every redirect is
revalidated under the same rules. Sensitive header names and response bodies are not emitted to
plugin logs. Structured `Platform.http.get`/`post` and binary counterparts return numeric `code`
(plus the prior `status` alias), headers, and body for ordinary non-2xx responses. Thus plugins can
inspect 403/429 and empty bodies; connection, TLS, timeout, size, and security failures still throw.

## Validation

The focused contract suite retains a generated API 3 ZIP that returns `JSON.stringify(...)` and
verifies the complete legacy metadata flow. API 4 fixtures directly return JavaScript arrays and
cover protocol bounds, independent lyrics/cover capabilities, `local-song`, paging, multiple
lyrics candidates, judgment-field rejection, cover IDs, private-context isolation, runtime info,
capability routing, and configuration regressions. Rust tests cover structured HTTP 200, 403, 429,
redirect, and empty-body behavior together with cache, XML, security, and QuickJS return handling.

An opt-in desktop smoke test reads `LYRICO_PLUGINS_DIR`, packages the current Apple Music, QQ,
NetEase, Kugou, and Soda directories without copying them into the product, then verifies ZIP
import, manifest persistence, bundle construction, QuickJS load, and capability discovery. It
deliberately performs no live provider HTTP calls, so normal CI remains network-independent.

See [testing/test-report.md](testing/test-report.md) for the commands and current platform result.

## Known limitations

- TidePlayer does not ship or download third-party plugin ZIPs; real plugins remain user supplied.
- The real-plugin smoke test validates code compatibility offline. Provider availability,
  authentication, regional restrictions, and anti-abuse responses require an explicit live test.
- `include(path)` is a compatibility no-op after all configured include-directory JavaScript has
  been bundled in deterministic order. Plugins cannot read arbitrary files at runtime.
- The Gradle build configures `iosSimulatorArm64`, not an x86_64 simulator target. Generic Xcode
  builds must select arm64 (or exclude x86_64).
