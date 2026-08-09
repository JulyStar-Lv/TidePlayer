# Legacy identifier inventory

Date: 2026-08-08

The current product name is `TidePlayer`. Two previous public brands are retained
only to read existing data or accept existing integrations: `MelodyTrove` and
the older `TideTunes`. New data must never be written with either legacy brand.

| Legacy identifier | Current identifier | Retention reason |
| --- | --- | --- |
| `MelodyTrove` | `TidePlayer` | Previous product name in migrated Desktop data, backup discovery, and historical messaging |
| `melodytrove` URL scheme | `tideplayer` | Existing OAuth registrations and pending redirects |
| platform data root plus `MelodyTrove` | platform data root plus `TidePlayer` | Desktop in-place data migration |
| `MelodyTrove-settings-*` | `TidePlayer-settings-*` | Existing settings backup discovery |
| `TideTunes` | `TidePlayer` | Original product name in migration messaging and old backup discovery |
| `com.github.tidetunes` | `io.github.julystar.musicapp` | Android credential/key identifiers and upgrade inventory |
| `tidetunes` URL scheme | `tideplayer` | Existing OAuth registrations and pending redirects |
| `~/.tidetunes` | platform data root plus `TidePlayer` | Original Desktop data migration |
| `tidetunes.db` | `library.db` | Existing Room database, WAL, and SHM migration |
| `tidetunes.preferences_pb` | `settings.preferences_pb` | Existing preferences migration |
| `tidetunes_secure_credentials` | `io.github.julystar.musicapp.credentials.preferences` | Android encrypted credential migration |
| `TideTunesCredentialKey` | `io.github.julystar.musicapp.credentials.key` | Android Keystore migration where the old sandbox is accessible |
| `TIDETUNES_*` environment variables | `MUSICAPP_*` | Developer/test compatibility during the transition |

The source of truth is:

- `migration/AppIdentifiers.kt` for the current TidePlayer brand and stable technical IDs
- `migration/LegacyPaths.kt`
- `migration/LegacyIds.kt`
- `migration/LegacyCredentialIds.kt`
- `migration/LegacyPreferenceKeys.kt`
- `migration/LegacyDeepLinks.kt`
- `migration/LegacyEnvironmentVariables.kt`

Android Manifest and iOS `Info.plist` register `tideplayer` as the primary URL
scheme and keep `melodytrove` plus `tidetunes` as compatibility schemes. Desktop
migration checks the previous standard `MelodyTrove` data directory first and
then the original `~/.tidetunes` layout. The stable application/package ID,
database filename, preferences filename, Rust library identity, and Apple shared
framework identity are deliberately not renamed.
