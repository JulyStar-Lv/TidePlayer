# Universal Provider Architecture v3.4 Roadmap

Status: **FROZEN TARGET / FUTURE ROADMAP**  
Frozen: 2026-09-30  
Source Plugin Protocol target: `source/1`  
Source Host API target: `host/4+`

> This document describes the **target direction for future source/plugin work**. It does not
> describe current shipped behavior. The current implementation remains documented in
> [final-architecture.md](./final-architecture.md) and [../plugin-runtime.md](../plugin-runtime.md).

## 1. Goal

TidePlayer should stop adding provider-specific Native UI, registries, scanners, stream resolvers,
playback branches, and account flows for each new remote service.

The target boundary is:

```text
provider-specific control plane
        -> Source Plugin

shared product/runtime/data plane
        -> Native Core
```

The permanent design rule is:

> **Plugins define how a provider speaks. Native Core defines how provider data is safely,
> consistently, and efficiently used.**

Adding an ordinary provider should therefore mean adding or installing a plugin package, not
adding a new provider enum, Native editor, Koin branch, player, scanner, or Room table.

## 2. Non-negotiable constraints

1. Provider-specific API details, URL formats, signatures, cookie names, request parameters, and
   response parsing stay inside the plugin.
2. Large media byte streams never pass through QuickJS.
3. JavaScript never writes Room directly.
4. JavaScript never owns a platform player directly.
5. JavaScript never orchestrates a complete storage-library scan.
6. Temporary playback URLs, headers, cookies, and short-lived tokens are not durable track data.
7. Native Core owns credentials, authentication surfaces, network policy, data-plane IO, sync
   transactions, metadata parsing, playback, download, and persistence.
8. New providers must not introduce `when(providerId)`, `when(providerType)`, or equivalent
   provider-specific Core branches.
9. DRM bypass, protected-key extraction, or provider-specific decryption is not a Source Plugin
   responsibility.
10. Provider-specific Native code is prohibited unless the requirement is first proven to be a
    reusable transport/platform/data-plane capability.
11. Provider plugins contribute normalized home/catalog/collection data, not arbitrary primary
    application UI. Home, Library, playlist, album, artist, search, and Now Playing remain Native
    TidePlayer product surfaces.

## 3. Target topology

```text
TidePlayer
├── Plugin Ecosystem
│   ├── Source Plugins
│   └── Metadata Plugins
├── Provider Runtime
│   ├── Manifest / Package
│   ├── QuickJS
│   ├── Action RPC
│   ├── Permissions
│   └── Plugin Offline Web UI
├── Identity Core
├── Authentication Core
├── Enumeration / Sync Core
├── Network / Route Core
├── Resource Core
├── Playback Core
├── Query / Resolution Policy
├── Home Composition Core
├── Collection Core
├── Canonical Media Library
└── Persistence / Credential Vault
```

Metadata plugins remain a separate ecosystem compatible with the existing Lyrico-style metadata
contract. Source Plugins do not replace that API.

## 4. Provider taxonomy

A Source Plugin may describe a provider as `storage`, `server`, or `platform`.

This classification is **descriptive manifest taxonomy only**. Native Core dispatch must be based
on declared capabilities and standard RPC actions, not on provider type.

- **storage** — hierarchical files/objects, such as WebDAV, SMB, OneDrive, Google Drive, Dropbox,
  S3, Baidu, 115, 123, or OpenList.
- **server** — user-owned structured media servers, such as Navidrome, OpenSubsonic, Emby,
  Jellyfin, or Plex.
- **platform** — public catalog/account services, such as NetEase, QQ Music, Kugou, YouTube Music,
  or Bilibili.

If a future provider does not fit these labels, the Core contract must still remain capability
driven.

## 5. Plugin / Provider / Account / Root model

The frozen relationship is:

```text
1 Plugin
  -> N Providers

1 Provider
  -> N Accounts

1 Account
  -> N Roots / Collections

1 Canonical Media
  -> N ProviderMediaRef / TrackSourceRef
```

Stable provider key:

```text
plugin:<pluginId>:<providerId>
```

Account identity:

```text
providerKey + provider-issued account identity
```

Media identity:

```text
providerKey + account identity + provider item id
```

A root/mount is not an account. Multiple scan roots for one cloud account share one credential
session.

## 6. Identity and scope

### IdentityScope

```text
IdentityScope = Provider + Account
```

### EnumerationScope

```text
EnumerationScope = IdentityScope + selected roots + filters
```

Changing selected roots must not invalidate stable provider item identity. Switching accounts must
not reuse identities even if the remote service happens to reuse an item ID.

### ProviderRootIdentity

Roots should support stable provider identity independently from a display path:

```text
ProviderRootIdentity
├── configuredLocator
├── currentLocator
├── stableId?
├── displayName?
└── revision?
```

A provider-side folder rename should not force reselection when a stable remote folder ID exists.

### ProviderMediaRef and placement

Playback/media identity and library placement are different concepts:

```text
RemoteEntity
├── EntityIdentity
├── normalized metadata
├── ProviderMediaRef
└── Placement[]
```

A single remote track may appear simultaneously in an album, playlist, liked collection, folder,
or recommendation without creating unrelated media identities.

## 7. Source Plugin package

Recommended package shape:

```text
plugin.zip
├── manifest.json
├── src/
│   └── index.js
├── ui/
│   ├── setup.html
│   ├── settings.html
│   ├── account.html
│   ├── browser.html
│   ├── js/
│   └── css/
├── assets/
└── locales/
```

The Native app must not provide WebDAV-, SMB-, OneDrive-, Emby-, NetEase-, or other provider-
specific setup screens.

## 8. Manifest and capabilities

A future Source Plugin manifest should declare provider identity, capabilities, permissions,
authentication requirements, UI entry points, connection hints, and discovery hints.

Capability and permission are separate concepts:

- **capability**: what the provider can do.
- **permission**: what host resources the plugin may access.

Effective capabilities are:

```text
manifest claims
∩ host support
∩ platform support
∩ account/session support
∩ remote-service runtime support
```

Capabilities may change dynamically by account, subscription, region, server version, runtime
health, or DRM availability.

Core code must not infer capabilities from provider names.

## 9. Action RPC

Source Plugins use Action RPC rather than implementing Kotlin interfaces directly.

Request shape:

```json
{
  "protocol": "source/1",
  "requestId": "req_123",
  "providerId": "onedrive",
  "accountId": "account_42",
  "action": "storage.listPage",
  "deadlineMs": 15000,
  "payload": {}
}
```

Response shape:

```json
{
  "ok": true,
  "data": {}
}
```

Error shape:

```json
{
  "ok": false,
  "error": {
    "code": "RATE_LIMITED",
    "retryable": true,
    "retryAfterMs": 30000,
    "detailsCode": "provider-specific-code"
  }
}
```

Standard error vocabulary:

```text
UNAUTHORIZED
PERMISSION_DENIED
NOT_FOUND
RATE_LIMITED
TIMEOUT
NETWORK_UNAVAILABLE
INVALID_CONFIGURATION
AUTH_CHALLENGE_REQUIRED
RESOURCE_EXPIRED
RESYNC_REQUIRED
UNSUPPORTED
INTERNAL
```

Provider-private details may use `detailsCode`, but secrets must never be serialized into errors
or logs.

## 10. QuickJS runtime

Runtime identity should be `pluginId + providerId`, not account ID. Multiple accounts for the same
provider share one provider runtime and pass account context through RPC.

Required lifecycle:

- lazy creation;
- same-runtime serialization;
- cross-provider concurrency;
- timeout and interrupt;
- memory and stack limits;
- poisoned-state teardown;
- rebuild after failure;
- bounded close;
- idle eviction.

Do not create a 64 MiB QuickJS instance per account.

## 11. Plugin Offline Web UI

Provider setup/configuration is plugin owned.

Native supplies only a generic `PluginWebHost` and a narrow scoped bridge. Plugin UI is loaded
from bundled offline resources under an isolated virtual origin.

Default offline UI policy:

- no arbitrary file access;
- no cross-plugin origin access;
- network denied unless explicitly routed through the host;
- no arbitrary Native method invocation;
- no direct credential storage;
- no provider-specific Native forms.

A plugin may implement setup, settings, account profile, folder browsing, diagnostics, and advanced
configuration in its bundled UI.

The UI runtime and provider QuickJS runtime are separate security contexts.

## 12. Authentication Core

Authentication is modeled as a generic challenge flow:

```text
AuthFlow
├── OAuth2
├── WebCookie
├── QRCode
├── SmsOtp
├── Totp
├── Captcha
├── DeviceConfirmation
├── UsernamePassword
├── ManualToken
└── ExternalApproval
```

Target actions:

```text
auth.begin
auth.continue
auth.verify
auth.refresh
auth.logout
```

Cookie/web login is implemented by a shared Native Browser/WebAuth host. SMB/WebDAV credential
entry remains plugin UI, not a fake remote WebAuth page.

## 13. BrowserSessionHost

The WebAuth host should evolve into a reusable BrowserSessionHost with isolated modes:

```text
BrowserSessionHost
├── interactive
├── background
└── managed playback
```

Reusable host capabilities may include controlled cookie/header/storage capture, allowlisted
navigation, controlled page evaluation, and browser-session lifecycle.

Provider-specific page scripts, YouTube state parsing, Apple Music session semantics, and similar
logic remain inside plugins.

## 14. CredentialVault

Room and ordinary plugin configuration never contain passwords, cookies, long-lived tokens, or
authorization headers.

Credential scope:

```text
pluginId + providerId + accountId
```

Target model:

```text
CredentialBundle
├── schemaVersion
└── values<String, SecretValue>
```

The default plugin model is opaque credential injection:

```js
Platform.http.request({
  url,
  credential: "currentAccount"
})
```

High-risk explicit `credential.readOwn` permission is required before a plugin may read its own
secret values.

Credential updates use field patches and account-scoped single-flight refresh to avoid concurrent
refresh overwrites.

## 15. Enumeration and sync

Provider plugins expose primitives. Native owns orchestration.

Storage-style primitives may include:

```text
storage.listPage
storage.getItem
storage.search
storage.deltaPage
storage.stat
resource.resolve
```

Native owns queueing, recursion, paging, concurrency, cancellation, pause/resume, filtering,
checkpointing, changed-file detection, metadata ingestion, batching, and deletion reconciliation.

Structured server/platform providers expose normalized catalog/library page actions instead of
pretending to be filesystems.

Global public catalogs are queried on demand. Only user library/collections are synchronized.

## 16. Enumeration staging

Large paged enumeration must use staging:

```text
provider page
  -> validate
  -> staging transaction
  -> checkpoint
  -> next page
  -> complete observation
  -> reconciliation
  -> canonical commit
```

Page data and the next cursor/checkpoint are one atomic transaction.

A partial traversal must not publish a half-complete authoritative library snapshot.

Provider cursors, pending directories, staging state, route health, retry timers, temporary
sessions, and temporary resource URLs are device-local state and are not cross-device sync data.

## 17. Enumeration observations and deletion safety

Every complete/partial enumeration should produce an observation describing revision and
completeness.

Target deletion authority:

```text
authoritative
evidence
none
```

- `authoritative` — a verified complete snapshot may establish absence.
- `evidence` — absence needs independent repeated observations.
- `none` — absence must never delete canonical data.

If the source drifts while an enumeration is in progress, add/update may be accepted but absence
must not authorize deletion.

Re-reading the same remote revision is one witness, not multiple witnesses.

Large sudden disappearance must enter pending reconciliation and require additional evidence
before destructive pruning.

## 18. Asset observation

Audio identity/revision and sidecar assets are tracked independently.

```text
AssetObservation
├── artwork
├── lyrics
├── video
├── cue
├── fingerprint
└── authority
```

A changed `.lrc` sidecar should invalidate lyrics without forcing full audio metadata extraction.
A missing asset only means deletion when the observation is authoritative.

## 19. Resource resolution

All large byte IO is Native.

The target ResourcePlan family is:

```text
ResourcePlan
├── DirectHttp
├── SessionBoundHttp
├── NativeTransport
└── NativeByteSource
```

Playback also supports a separate `ManagedPlayback` path.

`resource.resolve` always includes a purpose:

```text
playback
metadata
download
fingerprint
artwork
```

This prevents a provider's transcoded playback resource from being incorrectly used for raw-file
metadata or fingerprinting.

## 20. HTTP session and range policy

Session-bound resources may require cookies, user agent, referer, device identity, same session,
same egress, or re-resolution on forbidden responses.

Range support is not a boolean. Target policy:

```text
native
proxy
emulated
unsupported
```

The ResourceSession owns redirects, expiry, bounded 401/403 re-resolution, cache, retry, and range
validation.

## 21. Native transports

Wire protocols that belong in the data plane remain Native capabilities:

```text
Platform.transport
├── smb
├── sftp
├── ftp
└── future reusable transports
```

A plugin describes protocol semantics and remote identifiers; large bytes do not pass through
JavaScript.

This does not permit Synology-, QNAP-, provider-, or vendor-specific Native source implementations.

## 22. NativeByteSource and OpenList bridge

Sources such as MEGA/Proton or transforms such as encrypted/chunked storage may require a
seekable decrypted/reconstructed byte source and cannot honestly be represented by a normal
temporary HTTP URL.

The initial roadmap does **not** allow arbitrary downloadable Native binary plugins.

OpenList is the preferred compatibility bridge for uncommon encrypted/transform drivers:

```text
TidePlayer
  -> OpenList Source Plugin
  -> user's OpenList server
  -> MEGA / Proton / Crypt / Chunk / uncommon drivers
```

NativeByteSource remains an official data-plane extension point for future proven reusable needs.

## 23. Network routes

One account may have multiple routes without becoming multiple sources:

```text
RouteSet
├── LAN
├── WAN
├── VPN
├── VendorRemote
└── Relay
```

Native `RoutePlanner` chooses routes using user constraints, network state, route memory, and
provider-declared hints. Default ports/service hints live in the plugin manifest, not a Native
provider enum.

Route health distinguishes transport failure from authentication, HTTP/API failure, TLS/user
trust decisions, rate limiting, and cancellation.

Network path changes advance a `NetworkGeneration`, invalidating stale route cooldown/health.

A TCP-open socket is not sufficient proof under VPN/TUN environments; protocols may request a
lightweight provider handshake probe.

## 24. DiscoveryHost

Native provides reusable mDNS/Bonjour/Android NSD capabilities. Plugins declare discovery service
types and validate discovered endpoints through provider RPC.

Native must not contain provider-specific discovery classes.

## 25. Playback

Playback resolves a stable media reference at use time:

```text
CanonicalTrack
  -> candidate TrackSourceRef[]
  -> ResolutionPolicyEngine
  -> ProviderMediaRef
  -> provider media.resolve
  -> ResourcePlayback or ManagedPlayback
  -> PlaybackBackendSelector
```

The queue stores stable media/source references, never temporary playback URLs.

### Resource playback

HTTP/HLS/DASH/local/native-transport resources use the ordinary ResourceSession and platform
playback backends.

### Managed playback

A provider whose legal playback must remain inside an authorized runtime/SDK/DRM environment is
represented as `ManagedPlayback`, not as a fake direct URL.

Catalog capability may remain available when playback capability is unavailable.

DRM bypass is outside the architecture.

## 26. Playback variants

A provider may return multiple variants. Provider code describes available variants; Native policy
selects among them according to codec/device support, quality settings, network cost, data saver,
Hi-Res policy, and download policy.

Provider-specific player policy is prohibited.

## 27. Playback backend independence

Provider and playback backend are orthogonal:

```text
ProviderMediaRef
  -> PlaybackDescriptor
  -> PlaybackBackendSelector
  -> Media3 / AVFoundation / Rust audio / browser-managed backend
```

The same source may use different platform backends without changing provider logic.

Playback state should converge on a provider-neutral `PlaybackSnapshot` and monotonic
`PlaybackClock` so Native and managed/browser playback can drive one UI and lyrics timeline.

## 28. Canonical library and resolution policy

Canonical tracks remain provider neutral:

```text
CanonicalTrack
├── TrackSourceRef A
├── TrackSourceRef B
└── TrackSourceRef C
```

Resolution policy may consider local availability, cache, lossless/bit depth/sample rate, network
cost, last-successful source, manual user affinity, provider health, and expiry.

Cross-provider matching/fallback belongs in a generic TrackMatch/ResolutionPolicy layer, not in
individual plugins.

## 29. Query pipeline

Future multi-provider search should support:

- query transforms/normalization;
- streaming federated provider results;
- candidate ranking and deduplication;
- cancellation/generation fencing;
- provider health/circuit breaking.

A slow provider must not block presentation of useful results from faster providers.

## 30. Home & Collection Contract

This contract defines how provider-owned recommendations, liked/saved items, user libraries, and
playlists enter TidePlayer without turning the main product into a collection of provider WebViews.

The governing rule is:

> **Home UI stays TidePlayer-owned. Providers contribute normalized content and collection
> semantics, not arbitrary main-screen layout or Native views.**

Plugin Offline Web UI remains appropriate for provider setup, account management, diagnostics,
specialized management workflows, and other provider-specific configuration. It is not the primary
Home/Library rendering path.

### 30.1 Home ownership

The main Home screen is Native TidePlayer UI and may combine:

```text
TidePlayer Core Sections
+
Provider Home Contributions
+
User Pins / Home Preferences
```

Examples of Core-owned sections include Continue Listening, Recently Played, Recently Added, and
TidePlayer Favorites.

Providers that support personalized/editorial home data expose a capability such as:

```text
feed.home
```

and return normalized data, never HTML/Compose/SwiftUI.

A target response shape is:

```json
{
  "sections": [
    {
      "id": "daily-recommend",
      "semantic": "recommendation",
      "title": "Daily Mix",
      "presentationHint": "carousel",
      "items": [],
      "continuation": null
    }
  ]
}
```

### 30.2 HomeSection

A provider-neutral `HomeSection` should include:

```text
HomeSection
├── id
├── provider/account provenance
├── semantic
├── title?
├── subtitle?
├── presentationHint?
├── items[]
└── continuation?
```

Initial semantic vocabulary may include:

```text
recommendation
recent
newRelease
chart
mix
playlist
album
artist
continueListening
editorial
generic
```

Initial presentation hints may include:

```text
hero
carousel
grid
list
compact
```

Presentation hints are advisory. Native responsive UI decides the actual rendering for phone,
tablet, Desktop, and Automotive. Plugins must not provide pixel dimensions, Compose code, SwiftUI
code, arbitrary CSS for the main application, or ordering that overrides Native product policy.

### 30.3 HomeCompositionEngine

Native Core owns a `HomeCompositionEngine`.

It:

- loads Core sections immediately;
- queries enabled/account-ready providers that expose `feed.home`;
- accepts provider sections incrementally;
- applies provider-health/circuit-breaker and operation-epoch rules;
- caps/filters provider contribution according to product policy;
- applies user pin/hide/reorder preferences;
- adapts section presentation to the current platform/window;
- prevents one slow or failing provider from blocking the whole Home screen.

Provider `rankHint` may be accepted as input but is never authoritative.

### 30.4 Provider Hub

A provider/account may have a Native `ProviderHubScreen`.

It is rendered from generic provider/account data and normalized sections/collections:

```text
ProviderHub(account)
├── account identity/status
├── provider search entry
├── feed.home sections when available
├── remote collections
└── generic capability-driven actions
```

A provider without `feed.home` may still receive a useful Hub assembled from available
capabilities, such as recent items, recently added items, liked items, playlists, and folders.

Provider-specific management pages that cannot be represented by the generic media model may open
the plugin's bundled Web UI under the existing security boundary.

### 30.5 Ephemeral catalog boundary

Home recommendations, charts, search results, and other global catalog browsing are ephemeral
catalog data and must not automatically become canonical library rows merely because they were
displayed.

Target persistence rule:

| Content | Persistence |
| --- | --- |
| Home recommendation sections | Ephemeral catalog cache |
| Charts/editorial feeds | Ephemeral catalog cache |
| Search results | Ephemeral catalog cache |
| Provider user library / liked items | Remote collection mirror |
| Provider user playlists | Remote collection mirror |
| TidePlayer favorites | Canonical Room |
| TidePlayer playlists | Canonical Room |
| Downloaded/saved/playlist-added media | Canonicalize on demand |

A remote catalog entity is canonicalized when durable product behavior requires it, for example
playback history, local favorite, local playlist membership, download, or library import.

### 30.6 Collection model

Liked tracks, saved albums, followed artists, remote playlists, and provider user libraries should
converge on a provider-neutral collection model rather than growing separate per-provider Core
APIs.

Target model:

```text
LibraryCollection
├── collectionId
├── providerRef?
├── role
├── ownership
├── title
├── entityTypes
├── writeCapability
├── syncPolicy
└── ordering
```

Initial ownership values:

```text
local
remote
bound
virtual
```

- `local` — TidePlayer-owned collection.
- `remote` — provider-authoritative collection mirror.
- `bound` — local collection with an explicit synchronization relationship.
- `virtual` — computed projection with no authoritative item list of its own.

Initial collection roles may include:

```text
likedTracks
libraryTracks
savedAlbums
followedArtists
savedPlaylists
playlist
history
recentlyPlayed
generic
```

Role describes semantics. Write capabilities describe what operations are actually supported.

### 30.7 TidePlayer Favorite versus provider favorite

The primary TidePlayer heart/favorite state belongs to the canonical media entity and is not
implicitly equivalent to any provider's like/library state.

A canonical track may therefore have:

```text
TidePlayer favorite = true

remote memberships:
  provider A / likedTracks = true
  provider B / libraryTracks = true
  provider C / likedTracks = false
```

This separation is mandatory so multi-source tracks do not acquire ambiguous provider ownership.

Provider liked/saved states are represented as remote collection membership.

### 30.8 Unified favorite projection

TidePlayer may expose a read-only or safely scoped virtual collection such as "All Favorites":

```text
TidePlayer Favorite
UNION
remote liked/library collections
        ↓
Canonical Track Deduplication
        ↓
VirtualCollection
```

Removing an item from this aggregate must not silently issue destructive writes to every provider.

The default heart action modifies TidePlayer's canonical favorite state. Provider-specific
like/unlike or add/remove-library actions are explicit secondary actions.

Optional favorite synchronization may be added later as a user-configured policy and defaults to
off.

### 30.9 Local and remote playlists

TidePlayer playlists and provider playlists are separate ownership domains.

A TidePlayer playlist:

- is canonical/local;
- stores canonical media identity, not temporary URLs;
- may contain tracks whose best playback source comes from different providers;
- is resolved at playback time through the existing ResolutionPolicyEngine.

A provider playlist:

- retains provider/account/remote playlist identity;
- is mirrored through the Collection contract;
- remains provider authoritative unless explicitly cloned or bound;
- uses provider write capabilities only when those capabilities are exposed and user policy allows
  remote writes.

The product should support at least:

```text
Clone to TidePlayer Playlist
Remote Mirror
```

Two-way bound playlist synchronization is a later capability and is not required by the initial
contract.

### 30.10 Collection RPC direction

Long-term collection capabilities should converge on generic actions such as:

```text
collection.list
collection.itemsPage
collection.create
collection.rename
collection.delete
collection.add
collection.remove
collection.reorder
```

Legacy/finer provider actions such as `favorite.*`, `playlist.*`, or provider-specific library
APIs may remain inside plugin implementation, but Native product code should consume normalized
`LibraryCollection` and `CollectionMembership`.

### 30.11 Collection membership

Target membership model:

```text
CollectionMembership
├── collectionId
├── canonicalMediaId?
├── providerItemRef?
├── remotePosition?
├── addedAt?
├── revision?
└── syncState
```

A provider item may be mirrored before full canonicalization. Canonical identity can be attached
later without blocking large playlist/library synchronization.

### 30.12 Remote mutation outbox

Remote collection writes are unreliable network operations and should not be modeled as completed
merely because the UI changed.

A future `CollectionMutationOutbox` should support durable operations such as:

```text
add
remove
reorder
create
rename
delete
```

The UI may optimistically reflect a pending mutation while exposing sync/failure state. Local
TidePlayer collections do not require a remote outbox.

### 30.13 Generic collection UI

Core Native UI should render collection data using generic screens:

```text
HomeScreen
ProviderHubScreen
CollectionScreen
EntityGridScreen
EntityListScreen
TrackScreen
AlbumScreen
ArtistScreen
PlaylistScreen
SearchScreen
```

Provider additions must not introduce `NeteasePlaylistScreen`,
`AppleLibraryScreen`, `BiliFavoriteScreen`, or equivalent provider-specific Native pages.

### 30.14 Library information architecture

The long-term Library can present provider-neutral destinations such as:

```text
Library
├── Songs
├── Albums
├── Artists
├── Favorites
├── Playlists
└── Sources / Providers
```

Favorites and Playlists may expose provider/account filters and provenance badges without changing
the canonical/local ownership rules.

Provider collections should visibly retain source/account provenance.

### 30.15 Automotive policy

Automotive surfaces consume the same normalized HomeSection and Collection data, but Native
Automotive policy decides what is safe to expose.

Plugins must never inject an arbitrary provider Home WebView into the driving UI.

### 30.16 Home/Collection persistence modules

Target Core additions:

```text
home/
├── HomeCompositionEngine
├── HomeSection
├── HomePresentationPolicy
└── EphemeralCatalogCache

collection/
├── LibraryCollection
├── CollectionRole
├── CollectionMembership
├── CollectionSyncEngine
├── CollectionMutationOutbox
└── UnifiedCollectionProjection
```

These are cross-provider product capabilities and therefore belong in Native Core.

### 30.17 Frozen Home & Collection rules

The v3.4 contract freezes these rules:

1. Home remains TidePlayer Native UI; providers contribute normalized data only.
2. Provider setup/management UI may be plugin-owned, but primary media browsing uses generic
   Native TidePlayer screens.
3. Home/search/chart/catalog browsing is ephemeral until a durable user action requires
   canonicalization.
4. TidePlayer Favorite is canonical/local and is distinct from provider like/library membership.
5. Provider liked/saved/user-library semantics are normalized as remote collections.
6. TidePlayer playlists are canonical and may span providers.
7. Provider playlists retain provider authority and identity.
8. Unified favorites are a projection, not an implicit multi-provider write target.
9. Remote writes require explicit capability and should use a durable mutation/outbox model.
10. Provider-specific Native Home/Playlist/Favorite screens are prohibited.

## 31. Operation epochs

Every asynchronous provider/search/resolve/auth/sync result must be fenced by operation
generation/epoch so a stale response cannot overwrite newer state after account change, track
change, navigation, or cancellation.

This applies to catalog search, playback resolve, auth verification, library sync, BrowserSession,
lyrics, artwork, and metadata lookup.

## 32. Dynamic registry

The current static MusicSource registry is a migration target.

Future registry:

```text
DynamicMusicSourceRegistry
├── built-in Local
└── enabled Source Plugin adapters
```

Install, enable, disable, upgrade, uninstall, account changes, and auth-state changes update the
registry dynamically.

Any temporary adapters for `storage`, `server`, or `platform` must remain generic and must not
contain provider-specific logic.

## 33. Lifecycle

Provider lifecycle:

```text
Installed
Disabled
Enabled
Unavailable
NeedsAuth
Ready
Error
```

Disable cancels operations and closes runtime but retains user accounts/library/credentials.

Uninstall defaults to provider unavailable while retaining user data so reinstalling the same
provider key can recover it. Explicit account/data deletion performs destructive cleanup.

Multi-step deletion/cleanup work should use a durable lifecycle intent journal so crashes cannot
leave credentials or source records in ambiguous partial states.

## 34. Plugin install security

Source Plugin installation should present a user-visible permission summary covering:

- domains;
- HTTP methods;
- private-network access;
- WebAuth/browser access;
- cookie/header capture;
- credential permission;
- Native transports;
- TLS exceptions;
- background browser use;
- declared provider capabilities.

Official bundled plugins and user-installed plugins must use the same runtime path. Trust level may
change permissions/defaults, but not architecture.

## 35. Host API direction

Existing metadata Host APIs remain compatible.

Reusable Source Host capabilities may add:

```text
Platform.auth
Platform.credentials
Platform.browser
Platform.discovery
Platform.transport
Platform.resource
Platform.account
```

Generic algorithmic primitives such as crypto, compression, XML, bytes, Base64, hashing, and
bounded HTTP belong in Host API.

Provider constants, signing secrets, API endpoints, provider cookie names, and protocol-specific
business logic do not.

## 36. Persistence boundaries

Room stores:

- provider/account/root identity;
- non-secret provider configuration;
- provider items and placements;
- canonical media;
- stable source references;
- sync/reconciliation metadata.

CredentialVault stores:

- passwords;
- cookies;
- tokens and refresh tokens;
- authorization/session secrets;
- device secrets.

Memory-only state includes OTP/captcha responses, temporary login state, temporary playback URLs,
ephemeral headers, BrowserSession state, and other short-lived credentials.

## 37. Migration plan

### Phase 1 — Protocol foundation

- Source Plugin manifest/protocol;
- capability/permission model;
- provider key and identity types;
- Source QuickJS runtime;
- Dynamic Registry;
- CredentialVault v2;
- Plugin Offline Web UI;
- Browser/Auth host.

No existing provider behavior should be removed in this phase.

### Phase 2 — Data-plane foundation

- Enumeration staging/checkpoint;
- observation and deletion authority;
- reconciliation guard;
- ResourcePlan / ResourceSession;
- RangePolicy;
- RouteSet / RoutePlanner / NetworkGeneration;
- ResourceProbe.

### Phase 3 — Home & Collection contract

- HomeSection and HomeCompositionEngine;
- EphemeralCatalogCache;
- LibraryCollection / CollectionRole / CollectionMembership;
- TidePlayer Favorite versus remote membership separation;
- canonical local playlists versus provider playlist mirrors;
- generic Provider Hub / Collection screens;
- mutation outbox contract;
- Automotive filtering policy.

Use fake providers to prove that no provider-specific Native Home/Playlist/Favorite UI is needed.

### Phase 4 — Conformance providers

Create deterministic fake Storage, Server, and Platform providers to validate the protocol before
migrating real services.

### Phase 5 — Storage migration

Migrate WebDAV first, then OneDrive. Keep the existing OneDrive implementation as an A/B/reference
baseline until the plugin path reaches parity.

Migrate SMB as plugin control plane + Native SMB transport.

Add OpenList early as the compatibility aggregator/escape hatch.

### Phase 6 — Server migration

Migrate Navidrome/OpenSubsonic and Emby through normalized server/catalog actions.

### Phase 7 — Platform validation

Use representative providers with different complexity:

- NetEase — Cookie WebAuth + HTTP + crypto;
- Bilibili — account session + collection APIs + playback variants;
- YouTube Music — browser/header/session state + Innertube + background browser needs.

### Phase 8 — Managed playback

Validate the ManagedPlayback abstraction independently. Apple Music Web is an experimental
catalog/session/managed-playback validation target; no DRM bypass is part of the project.

### Phase 9 — Remove provider-specific Core

Only after parity and migration tests pass:

- remove hard-coded remote provider editors;
- remove provider-specific source registration;
- remove provider-specific playback branches;
- remove provider-specific sync coordinator branches;
- retain compatibility migrations only where needed for existing user data.

## 38. Conformance and acceptance

The roadmap is not complete until tests cover:

- plugin install/update/disable/uninstall;
- multi-provider plugin;
- multi-account provider;
- multiple roots per account;
- 10k/50k/100k storage enumeration;
- paging/checkpoint/resume/process-death recovery;
- stable IDs, rename/move/root rename/account switch;
- delta and snapshot sync;
- catalog drift and deletion authority;
- repeated-revision evidence;
- mass disappearance guard;
- HTTP range/proxy/emulated/no-range paths;
- URL expiry and bounded re-resolution;
- auth expiry, refresh, Cookie, QR, SMS, TOTP, Captcha;
- LAN/WAN/VPN route switching and route health;
- Native SMB transport;
- direct/HLS/DASH playback variants;
- purpose-specific resource resolution;
- sidecar lyrics/artwork changes;
- plugin timeout/OOM/poison/rebuild;
- domain/credential isolation and secret redaction;
- provider unavailable -> reinstall recovery;
- incremental Home sections where one provider is slow/failing;
- Home presentation remains Native across phone/Desktop/Automotive;
- provider recommendations/search do not pollute canonical library before durable user action;
- canonical TidePlayer favorite remains distinct from remote provider memberships;
- remote liked/library collections mirror and deduplicate through canonical identity;
- local cross-provider playlists resolve through candidate sources;
- provider playlist mirrors retain provenance and authority;
- remote collection mutation outbox success/retry/failure recovery;
- Android/iOS/Desktop compilation and focused runtime gates.

## 39. Core review gate

A future change that adds provider-specific Native code must answer:

> Can this requirement be expressed using the existing capability, AuthFlow, Enumeration,
> ResourcePlan, NativeTransport, BrowserSession, ManagedPlayback, Route, or generic policy
> contracts?

If yes, it belongs in the plugin or generic runtime path.

Core/Host protocol changes are acceptable only when the missing capability is demonstrably reusable
across providers and cannot be represented honestly by the frozen contracts.

## 40. Explicitly out of initial scope

The first implementation does not include:

- arbitrary third-party Native binary plugins;
- generic downloadable NativeByteSource modules;
- provider-specific DRM/decryption modules;
- MEGA/Proton native byte-source implementations;
- Crypt/Chunk native transforms;
- arbitrary Native UI injection.

OpenList is the preferred bridge for these uncommon storage/data-plane cases until a reusable need
is proven.

## 41. Frozen architectural definition

The implementation target is summarized as:

> **Source Plugins own provider control-plane behavior, authentication description, provider UI,
> catalog semantics, and resource location. Native Core owns identity, credentials, routing,
> enumeration, synchronization consistency, large-byte IO, caching, metadata, playback,
> persistence, security, and cross-provider policy.**

Future provider work should extend the plugin ecosystem first. Native Core is extended only for
provider-neutral reusable capabilities.
