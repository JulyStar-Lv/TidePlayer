# OneDrive remote storage

Last updated: 2026-08-09

## Current implementation

TidePlayer uses Microsoft Graph v1.0 and opens the Microsoft authorization page
in the system browser. The authorization request uses OAuth 2.0 Authorization
Code with PKCE:

- a random 32-byte verifier encoded with base64url without padding;
- an S256 code challenge;
- a random state value validated when the app receives the redirect;
- the `Files.Read offline_access` scope;
- the `tideplayer://oauth2redirect/` redirect URI.

`melodytrove://oauth2redirect/` and `tidetunes://oauth2redirect/` remain
registered only for compatibility with existing OAuth registrations and pending
redirects. New integrations should use `tideplayer://oauth2redirect/`.

The pending verifier and state are stored temporarily in the existing platform
credential store rather than Room or ordinary preferences. They are removed
before the authorization code is exchanged. Android handles both cold-start
and `onNewIntent` redirects. iOS registers the same URL schemes and forwards
`onOpenURL` redirects to the shared repository.

The resulting refresh token is stored through the platform credential store:

- Android: Android Keystore-backed encrypted storage;
- iOS: Keychain;
- Desktop: the operating system credential command supported by the current
  platform.

Room stores only the `source_account` credential reference and non-secret
account hints. Rust receives the credential in memory when the backend is
initialized.

## Graph access

The current backend supports:

- refresh-token exchange and access-token refresh;
- paginated `/me/drives` enumeration and explicit Drive selection;
- paginated directory children requests;
- file and folder IDs returned as `remoteId`;
- parent DriveItem IDs returned as `parentRemoteId`;
- ETag, cTag, MIME type, size, created time, and modified time;
- finite HTTP Range requests for metadata and playback;
- one retry after an HTTP 401 by refreshing the access token.

The selected Drive ID is persisted separately from the credential. Directory,
content, Range, and delta requests use `/drives/{drive-id}`. Temporary Graph
download URLs are not persisted.

## Delta synchronization

The `library_root` keeps the selected folder DriveItem ID, while
`source_sync_cursor` keeps the final Graph deltaLink in Room.
The synchronization sequence is:

1. Before the first complete scan, request `token=latest`.
2. Run the bounded recursive snapshot import.
3. Persist the cursor only after the Room import transaction completes.
4. On later refreshes, follow `@odata.nextLink` pages until Graph returns an
   `@odata.deltaLink`.
5. Apply file additions, metadata changes, stable-ID moves, and known file
   deletions to Room.
6. Advance the cursor only after all Room changes succeed.

Graph directory changes, changes without a usable path, unknown deletion IDs,
or HTTP 410 delta expiry trigger a complete snapshot reconciliation. The final
deltaLink is retained only if that reconciliation succeeds. This fallback is
intentional because Graph does not guarantee that every descendant receives a
delta entry when a folder is renamed.

Delta and pagination cursor URLs are accepted only when they use HTTPS and the
exact `graph.microsoft.com` host.

## Remaining work

The following acceptance items are not implemented or not fully verified yet:

- directory browsing and content reads still use canonical paths after a
  DriveItem has been selected; file identity and delta reconciliation use
  DriveItem IDs;
- refresh-token rotation persistence back to the platform credential store;
- a Desktop system-browser callback receiver;
- automatic background sync scheduling;
- live OneDrive integration tests using a test tenant.

Unknown directory-level delta changes intentionally use the bounded recursive
scan rather than risking an incomplete Room library.

## Verification

Automated Rust tests verify:

- random verifier and state generation;
- RFC 7636 S256 challenge generation;
- PKCE and state parameters in the authorization URL.
- Drive-list response parsing;
- delta file, directory, deletion, parent-ID, nextLink, and deltaLink parsing;
- default and explicit-Drive delta URL construction;
- rejection of untrusted delta cursor hosts.

Android, Desktop shared code, Kotlin/Native iOS, and the iOS Simulator app build
all compile with the generated UniFFI API. Room tests verify stable-ID
source-item deletion visibility and transactional cursor persistence. No live
Microsoft authorization or delta run was performed during this stage.
