# Android backup and restore policy

TidePlayer uses an allowlist for both Android 12+ data extraction and the legacy Auto Backup format. Cloud backup is enabled only when client-side encryption is available.

## Included data

- `files/datastore/settings.preferences_pb`: non-secret UI and playback preferences.
- `databases/library.db`: canonical library metadata, playlists, source-account metadata, download task metadata, and sync cursors.

The database contains credential references, not passwords or OAuth tokens. Download records may be restored even though their corresponding audio files are intentionally not restored; normal download recovery treats the missing file as unavailable rather than as completed media.

## Excluded data

The allowlist excludes every other file by default, including downloaded audio, playback and artwork caches, WebDAV/SMB temporary data, plugin runtime caches, diagnostic logs and crash archives, export bundles, temporary playback-gateway files, cookies, signed URLs, and credential-store material. Shared preferences, external storage, and the Android root domain are explicitly excluded as defense in depth.

## Credentials after restore

Credentials remain encrypted with Android Keystore keys and are not backed up. Keystore keys are not assumed to survive a device restore. If a restored database references a credential that is absent or cannot be decrypted, `AndroidCredentialStore` removes the unreadable encrypted entry and returns no credential. The source then requires authentication again; the app does not crash and does not repeatedly retry the same undecryptable value.

Policy files:

- `androidApp/src/main/res/xml/data_extraction_rules.xml` for Android 12 and newer.
- `androidApp/src/main/res/xml/backup_rules.xml` for older Auto Backup clients.

