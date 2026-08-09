# Diagnostics and recovery manual validation

Use a disposable Debug installation or profile. Fault injection intentionally terminates or stalls
the process. Before each scenario, open **Settings → Logs & diagnostics**, export a baseline bundle,
and note the current startup attempt ID.

## Ordinary startup

1. Start the app without a pending incident.
2. Confirm normal UI loads and safe mode is not shown.
3. Wait at least ten seconds after the first frame.
4. In Startup diagnostics, confirm the current attempt is `STARTUP_STABLE`.
5. Exit normally and reopen; confirm the previous attempt is graceful.

## Kotlin and Rust fatal startup

1. Open Debug fault injection.
2. Trigger Kotlin uncaught failure, reopen, and confirm safe mode shows a Kotlin incident.
3. Export the bundle and verify it contains `kotlin-crash.txt`.
4. Repeat with Rust panic and verify `rust-panic.txt`.
5. Choose **Try normal startup**. Confirm the incident remains pending during initialization and is
   resolved only after the first frame plus the ten-second stable window.

## Android ANR and historical exit

Requires Android 11/API 30 or newer.

1. Trigger Android ANR. Do not manually kill the process before the system shows the ANR dialog.
2. Close/reopen the app after the OS records the exit.
3. Confirm one `ANDROID_ANR` incident appears and its trace is no larger than 1 MiB.
4. Reopen again and confirm the same exit key is not imported twice.
5. On API 29, confirm the action/capability reports historical ANR trace as unsupported.

## Database and targeted recovery

1. Trigger the database-open or migration simulation and reopen into safe mode.
2. Run **Check database integrity**. Confirm the file presence, `integrity_check`, schema version,
   WAL and SHM state are shown without starting the full app graph.
3. Create settings and library-database backups.
4. Select queue clear, DSP reset, automatic-scan disable, plugin disable, or remote-source disable.
5. Confirm each modifying action requires a second confirmation.
6. Retry normal startup and confirm selected options are applied before the affected component
   starts.
7. For **Rebuild music library index**, confirm the existing maintenance service rescans source
   roots without deleting music files, playlists, favorites, or settings.

## User-requested safe mode

1. In the Diagnostics Center, choose **Use safe mode on next launch**.
2. Close and reopen TidePlayer.
3. Confirm the minimal safe-mode UI appears without a fabricated crash incident.
4. Choose **Try normal startup** and confirm normal initialization can proceed even though there is
   no incident ID associated with the user request.

## Failed and successful recovery

1. From safe mode, trigger a recovery attempt while the same injected failure is still active.
2. Confirm `pending-recovery.json` remains and the next launch returns to safe mode.
3. Remove the fault condition and retry.
4. Confirm `RECOVERY_ATTEMPTED` precedes normal component initialization.
5. Confirm the marker is cleared only after `STARTUP_STABLE`; the incident becomes `RESOLVED`.

## Diagnostics Center and export

1. Generate several sessions and thousands of structured events.
2. Verify session, log, and incident lists page in groups of 100.
3. Exercise level/category/keyword/time filters and incident type/severity/state filters.
4. Copy a log and an incident summary; view an artifact.
5. Export a single incident, then a full bundle.
6. Verify the ZIP has manifest, summary, environment, privacy report, startup, incidents, logs, and
   state summaries.
7. Search the unzipped bundle for seeded passwords, tokens, cookies, Authorization values, URL
   credentials, the user home path, and music data. None may be present.
8. Verify Android shares through the restricted `diagnostic_exports` FileProvider path, iOS opens
   the share sheet, and Desktop can save/open/reveal the file.
9. While a bundle is active, run cleanup and confirm the active ZIP, current session, referenced
   sessions, and unresolved recovery incident remain protected.

## Release check

Build a Release artifact and confirm Debug fault injection is neither visible nor executable.
No test requires or performs remote telemetry or automatic upload.
