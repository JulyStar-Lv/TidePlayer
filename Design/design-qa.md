# Design QA

## Artwork and manual theme colors — 2026-07-28

- Source: repository-maintained Figma Make export plus
  `Design/docs/TidePlayer-PDS-v3.md`.
- Maintained source: `Design/src/app/ThemeColorDesign.tsx`, integrated into
  Appearance and Design System → Theme Colors.
- Prototype build: passed with Vite 6.3.5.
- Responsive render checks: Compact `390 × 844`, Medium `840 × 900`, Expanded
  `1440 × 900`.
- Exports:
  `Design/exports/theme-color/design-compact-theme-colors.png`,
  `design-compact-theme-colors-artwork-off.png`,
  `design-compact-theme-picker.png`, `design-medium-theme-colors.png`,
  `design-medium-theme-picker.png`, `design-expanded-theme-colors.png`, and
  `design-expanded-theme-picker.png`.
- State coverage: artwork available/loading/missing/failed/off, picker
  initial/dragging/invalid/duplicate/limit/empty/delete/cancel/applied, and Swatch
  default/hover/pressed/focus/selected/removable/built-in/disabled.
- Control interlock: while artwork color is on, Theme color remains visible with
  disabled semantics and “Turn off Artwork color to edit”; turning artwork color
  off enables the row and is the only path that can open the manual picker.
- Contrast boards: Brand Pink, Yellow, and Blue in generated light/dark previews.
- Interaction: artwork switch, picker open/close, preset selection, HSV pointer
  surface, Hue slider, custom-color deletion, and palette actions are exposed as
  semantic controls.
- Validation: invalid `GG0000` Hex rendered the icon/message and disabled Apply.
- Responsive result: Compact uses a scrollable one-column dialog with vertical
  actions; Medium/Expanded use a bounded two-column dialog and horizontal
  actions.
- Desktop runtime comparison: the current Compose application was exercised in
  an isolated temporary bundle/user home at `1018 × 683`. Appearance, picker,
  invalid Hex, Yellow light/dark, artwork-off, Apply, and Cancel behavior matched
  the maintained design source.
- Desktop runtime exports:
  `actual-desktop-expanded-appearance-artwork-on-locked.png`,
  `actual-desktop-expanded-appearance-artwork-off-enabled.png`,
  `actual-desktop-expanded-picker-artwork-off.png`,
  `actual-desktop-picker-invalid-hex.png`,
  `actual-desktop-yellow-dark.png`, `actual-desktop-yellow-light.png`, and
  `actual-desktop-expanded-appearance-artwork-off.png` in
  `Design/exports/theme-color/`.
- Android build: `:androidApp:assembleDebug` passed. A connected Android 13
  device rejected USB installation with `INSTALL_FAILED_USER_RESTRICTED`, so no
  Android screenshot was fabricated.
- iOS runtime: the Xcode 26.4 Debug simulator build passed and the app was
  installed on a fresh iPhone 16e / iOS 26.4 simulator. Artwork-on disabled
  behavior, artwork-off enabled behavior, picker opening, and applying Yellow
  were exercised.
- iOS runtime exports:
  `actual-ios-compact-appearance-artwork-on-locked.png`,
  `actual-ios-compact-appearance-artwork-off.png`,
  `actual-ios-compact-picker-artwork-off.png`, and
  `actual-ios-compact-yellow-light.png`.
- Stale pre-interlock Desktop/iOS exports were removed so they cannot suggest
  that the manual picker opens while artwork color is enabled.
- Legacy cleanup: no system-wallpaper, Android 12+, device-support, or
  user-visible Monet control remains on the redesigned Appearance surface.
- External Figma: not modified. The target URL opened but did not expose an
  editable document in this environment. Manual node/copy/token instructions are
  in `Design/docs/figma-theme-color-sync-checklist.md`.
- Full design-to-Compose and platform audit:
  `docs/design/theme-color-implementation.md`.

Final theme-color result: design source, Desktop runtime, and iOS runtime passed.
Android runtime evidence remains explicitly unverified because device-side USB
installation was rejected.

- Source: Figma Make, `Design System for TidePlayer`, Version 31.
- Mobile: Home layout, Daily Picks, Recently Played, Continue Playing, mini player, and bottom navigation visually checked against the source design; mini player and bottom navigation verified in both light and dark themes.
- Desktop: sidebar, responsive content layout, and bottom player visually checked at 1440 × 900; the removed App Cover page and removed top toolbar do not reappear.
- Interaction: Home, Settings, and Appearance & Home navigation verified in the browser.
- Collapsing sticky headers: page titles move upward and scale from the expanded state into a compact pinned bar; Home, Settings, and Design System verified after scrolling on mobile and desktop viewports.
- Daily Picks: animated four-point gradient verified in light and dark themes; motion changes over time and the now-playing label updates with the selected song.
- Home sections: Recently Added, Recommended Artists, and Pinned Playlists verified on mobile with the same data and cards as the landscape layout.
- Recently Played: ranked tracks verified in three swipeable pages with recency, duration, page indicators, and artwork playback animation without row highlighting; no outer card, progress bar, or persistent overflow menu remains.
- Continue Playing: six cards verified using the PlaylistCard presentation without track count or duration; Pinned Playlists retains its metadata.
- Pinned Playlists: Favorite verified as the first card on mobile and desktop; it links to a liked song while the remaining playlist order stays unchanged.
- Hover behavior: card, genre, song, and slider hover-scale effects removed; a desktop album card remains at `transform: none` while hovered.
- Click consistency: pointer clicks no longer leave a persistent song-row focus ring; section headers open the matching Library category; card components use native button semantics; player icon controls use `0.92`, content cards `0.97`, rows/headers `0.985`, and primary actions `0.95`; keyboard focus remains `focus-visible` only.
- Mini player liquid glass: mobile and desktop variants verified in light and dark themes with shared translucent material, 30px backdrop blur, dynamic artwork-colored refraction, highlight rim, glass border, and theme-aware shadow/controls.
- Mini player dark mode: the light top-rim highlight is disabled and the remaining glass border/inset highlight is reduced to prevent a white line along the upper edge.
- Runtime: production build passes and Mini Player controls remain functional; an existing `ListeningSummaryCard` nested-button warning remains outside this change.

Final result: passed

## Library & sources optimization — 2026-07-20

- Product grounding: the current Settings scope is limited to Local and WebDAV management, scanning, and metadata backfill. OneDrive, Jellyfin, Plex, and Navidrome are not exposed on this screen.
- Before/after: `audits/library-sources/mobile-before.png` and `audits/library-sources/mobile-overview.png` at 390 × 844. The six tall standalone cards were replaced by a compact unified-library summary and one manageable source list.
- Responsive implementation: `audits/library-sources/desktop-overview.png` at 1440 × 900 verifies the same hierarchy without unnecessary multi-column card repetition.
- Scanning state: `audits/library-sources/mobile-scanning-menu.png` verifies Automatic scan, the content-fit HyperOS choice menu, metadata scan guidance, maintenance actions, and mini-player clearance.
- Functional fidelity: the screen exposes only Local and WebDAV, presents WebDAV as the only addable source, adds Standard-mode behavior text matching repository documentation, and includes both missing-artwork and missing-lyrics backfill actions.
- Add source flow: `audits/add-source/mobile-form.png`, `audits/add-source/mobile-added.png`, and `audits/add-source/desktop-form.png` verify responsive WebDAV configuration, required-field errors, anonymous mode, password visibility, connection testing, disabled-before-test submission, and immediate list/summary updates after adding.
- WebDAV management: `audits/manage-webdav/mobile-editor.png`, `audits/manage-webdav/mobile-directories.png`, `audits/manage-webdav/mobile-delete-confirm.png`, and `audits/manage-webdav/desktop-editor.png` verify the clickable WebDAV management entry, editable connection details, connection retesting, selectable import directories, immediate source-summary updates, and guarded deletion. Local Storage remains read-only.
- Runtime: production build passes, the browser error log is empty, and the menu opens and closes without leaving an overlay behind.

Final result: passed

## Library & sources repository-aligned refinement — 2026-07-28

- Product grounding: aligned the prototype with
  `SourceSettingsSection`, `SettingsUiState`, `SettingsAction`, and the
  repository defaults in `SettingsModels.kt`.
- Information architecture: reordered the page into library health, source
  accounts, scan status, automatic scanning, import rules, advanced rules, and
  maintenance.
- Source state: Local, WebDAV, and SMB now expose enabled/paused state, indexed
  track counts, last-scan status, add actions, and remote-source management.
- Add-source architecture: replaced separate Local, WebDAV, and SMB actions with
  one Add source entry. Its catalog groups the repository-backed types into Local
  storage, Network storage, Cloud storage, and Media servers, covering Local
  directory, WebDAV, SMB, OneDrive, Navidrome, OpenSubsonic, and Emby.
- SMB fidelity: added the repository-backed SMB2/3 flow with server, port, share,
  root folder, guest access, credentials, domain, signing, encryption, connection
  testing, editing, and guarded deletion. SMB1 is explicitly excluded.
- Scan state: Scan now exposes an in-progress state, progress feedback,
  cancellation, and a completed-without-failures state.
- Settings fidelity: added automatic scanning, minimum duration, missing-file
  policy, duplicate policy, supported formats, ignored directories, and metadata
  parsing. Advanced rules remain collapsed until requested.
- Data fidelity: the supported-format and ignored-directory summaries match
  `SUPPORTED_AUDIO_EXTENSIONS` and `DEFAULT_IGNORED_SOURCE_DIRECTORIES`;
  fabricated missing-artwork and missing-lyrics counts were removed.
- Responsive verification: visually checked at the default desktop viewport and
  at `390 × 844`; the mini player and navigation remain clear of all content.
- Interaction verification: source enable/disable, Scan now, scan completion,
  advanced-rule disclosure, the unified source catalog, WebDAV/SMB routing,
  OneDrive flow guidance, SMB required-field validation, connection testing,
  add/edit flows, and guarded deletion were exercised. The browser error log is
  empty.
- Runtime: Vite 6.3.5 production build and `git diff --check` pass.

Final result: passed

## HyperOS settings choice menu — 2026-07-19

- Source: `/var/folders/jc/z_g_5hld77g5zmm6bxv83_5c0000gn/T/codex-clipboard-9d9bde29-ee6a-43f5-8489-5e7aafab1ecb.jpg`.
- Implementation screenshots: `audits/hyperos-settings-choice/mobile-theme-menu.png` at 390 × 844 and `audits/hyperos-settings-choice/desktop-theme-menu.png` at 1440 × 900, both in the dark theme with the choice menu open.
- Comparison: the source and mobile implementation were inspected together. The implementation matches the reference pattern with a dimmed background, large 28px panel radius, vertically spaced full-row choices, a blue selected value, a right-aligned checkmark, and a compact up/down affordance on the setting row.
- Iteration history: the first pass exposed a light menu over the app's dark theme because the portal rendered outside the themed root; the menu now reads the triggering setting's theme and renders the correct material. A scroll-close listener that dismissed the menu during focus was removed. Lower-page menus now prefer opening upward when the trigger is in the lower half of the viewport.
- Interaction: Theme selection updates the displayed value and selected marker; Escape closes the menu and returns to the settings state. The same component covers language, playback, lyrics, scan, cache, network, and backup choices.
- Responsive and runtime: mobile and desktop placement verified, browser error log is empty, `git diff --check` passes, and the production Vite build succeeds.

Final result: passed

## Now Playing mobile and landscape refinement — 2026-07-20

- Source visual truth: `audits/reference-player-immersive/mobile-now-playing-final.png` plus the user's portrait and landscape layout constraints.
- Implementation screenshot: unavailable; the Codex in-app browser is open but no controllable browser capture surface is exposed in this task.
- Viewports: portrait `390 × 844`; landscape `844 × 390`.
- State: full player, player view, Midnight Cascade, playing.
- Full-view comparison evidence: blocked until a browser-rendered portrait and landscape capture can be made.
- Focused-region comparison evidence: blocked for the lyric scale, progress area, and five-control alignment for the same reason.

**Findings**

- [P1] Landscape composition has not been visually verified.
  Location: full player landscape branch.
  Evidence: the production build passes, but no browser-rendered `844 × 390` screenshot is available.
  Impact: overflow, crop, or optical-alignment issues could remain in a short landscape viewport.
  Fix: capture the player, lyrics, and queue states at `844 × 390`, then compare and correct any visible drift.

- [P2] Portrait spacing changes have not been visually compared.
  Location: portrait lyric preview, full lyrics, and five-button transport.
  Evidence: source values were reduced and control seats normalized in code, but no new `390 × 844` screenshot is available.
  Impact: the intended extra bottom breathing room and smaller lyric hierarchy are not yet visually proven.
  Fix: capture `390 × 844` and compare against the reference and user-requested adjustments.

**Implementation Checklist**

- Capture portrait and landscape player states in a browser.
- Verify all five transport controls share one optical center line and stay clear of the safe area.
- Verify portrait active lyrics at `28px`, surrounding lyrics at `23px`, and landscape lyrics at `24px` / `20px` do not clip.
- Exercise player-to-lyrics and player-to-queue transitions in both orientations.
- Check browser console errors, then update this report with the comparison evidence.

**Follow-up Polish**

- None classified until visual capture is available.

Final result: blocked

## Source configuration and status refinement — 2026-07-28

- Source visual truth:
  `/var/folders/jc/z_g_5hld77g5zmm6bxv83_5c0000gn/T/codex-clipboard-ca653082-5b49-4634-87a4-46d4adf59e88.png`.
- Implementation screenshots: `qa/source-status-mobile-full.png`,
  `qa/source-status-mobile.png`, and `qa/source-status-desktop.png`.
- Combined comparison evidence: `qa/source-status-comparison.png`, with the
  reference on the left and the implementation on the right.
- Viewports: mobile `390 × 844`; desktop `1440 × 900`.
- Normalization: the `776 × 820` reference is a `2×` capture and was reduced to
  `388 × 410`; the focused implementation capture is `388 × 410` at
  `devicePixelRatio: 1`. Both comparison regions therefore represent the same
  CSS size and density.
- State: light theme, three enabled sources, connected/available, indexed, and
  last scan at 12/18 minutes for the focused comparison. Dark-theme desktop
  responsiveness was checked separately.
- Full-view comparison evidence: the mobile capture confirms the card remains
  clear of the mini player and bottom navigation; the desktop capture confirms
  the new actions and tags align without changing the surrounding settings
  hierarchy.
- Focused-region comparison evidence: the side-by-side card comparison verifies
  icon scale, name and Enabled hierarchy, metadata truncation, row separators,
  switch alignment, card radius, and the new configuration/status controls.

**Findings**

- No actionable P0/P1/P2 mismatch remains. The configuration button is an
  intentional addition immediately left of the switch, and the Available /
  Connected plus Indexed labels are an intentional extension of the reference.
- Typography preserves the existing product font, weight hierarchy, and compact
  small-label treatment. Mobile scan time is shortened to `12m` / `18m` so it
  does not create another line; desktop keeps the full text.
- Spacing and layout rhythm match the reference density after correction: all
  three mobile source rows measure `96px` high and all status groups remain one
  line.
- Colors use the existing semantic green, blue, muted, primary, surface, and
  border tokens in both light and dark themes.
- Image and icon fidelity passes: all visible source and action symbols come
  from the existing Lucide icon set; no raster placeholder, emoji, CSS drawing,
  or handcrafted SVG was introduced.
- Copy is concise and source-specific: Local reports Available, network sources
  report Connected, and scan state reports Indexed / Scanning / Not indexed.

**Comparison History**

- First pass: the added tags caused scan time to wrap, producing approximately
  `120px` rows on mobile. This was a P2 density mismatch against the compact
  reference.
- Fix: scan time moved into the Indexed pill on mobile, tag padding was reduced,
  and full scan text was retained for desktop and accessible labels.
- Post-fix evidence: `qa/source-status-comparison.png` shows three `96px` rows
  with all status information on one line; no P0/P1/P2 finding remains.

**Interaction and Runtime Verification**

- Local configuration opens, edits the folder, and saves.
- WebDAV and SMB configuration buttons open their existing source-specific
  editors.
- Enable/pause updates Enabled / Paused and Available / Connected /
  Disconnected states.
- Scan now exposes three Scanning tags before returning to Indexed.
- Browser error log is empty; Vite 6.3.5 production build and
  `git diff --check` pass.

**Implementation Checklist**

- Configuration action present before every source switch.
- Local, WebDAV, and SMB reconfiguration paths verified.
- Enabled, connection, indexing, scanning, and disconnected states verified.
- Mobile and desktop responsive layouts verified.

Final result: passed

## Now Playing mobile queue transition refinement — 2026-07-20

- Source visual truth: the current TidePlayer mobile full-player and queue states, with the queue treated as a secondary surface above the player.
- Implementation screenshot: unavailable; this task does not expose a controllable in-app browser capture surface.
- Viewport: portrait `390 × 844`.
- State: full player switching between player, lyrics, and queue views.

**Scoped Follow-up Changes**

- The queue now enters from the bottom with a short spring transition and exits along the same path.
- Player and lyrics remain stacked beneath the queue and use a subtle scale/opacity response, eliminating the opposing horizontal motion and layout jump.
- Full-screen mobile panels are absolutely stacked so simultaneous exit/enter animations no longer reflow the page.
- Reduced-motion users receive a brief opacity transition instead of spatial movement.
- The existing horizontal player-to-lyrics transition remains unchanged.
- The three-button lyrics/device/queue footer navigation was removed from both the portrait lyrics and queue screens, plus their landscape counterparts.
- Lyrics and queue now use one top-level back control so the player remains reachable without restoring the removed footer.
- Queue previous, play/pause, and next controls now match the main player's button seats, icon scale, translucent play surface, shadow, and pressed motion.
- Queue playback controls now reserve bottom safe-area spacing after the footer removal.
- Production build passes; browser screenshot and interaction comparison remain unavailable.

**Implementation Checklist**

- Exercise player-to-queue, queue-to-player, lyrics-to-queue, and queue-to-lyrics transitions at `390 × 844`.
- Confirm there is no background flash, header jump, or bottom-control misalignment during the spring settle.
- Check the browser console and repeat the interaction with reduced motion enabled.

Final result: blocked

## Now Playing desktop refinement — 2026-07-20

- Source visual truth: the existing TidePlayer full-player desktop implementation and current product design system.
- Implementation screenshot: unavailable; this task does not expose a controllable in-app browser capture surface.
- Viewports: primary `1440 × 900`; compact desktop `860 × 520`; ultrawide width capped by a `1600px` content stage.
- State: full player, lyrics tab, Midnight Cascade, playing.
- Full-view comparison evidence: blocked until browser-rendered desktop captures can be made.
- Focused-region comparison evidence: blocked for the artwork/control column, lyrics panel, and compact-height layout.

**Findings**

- [P1] Desktop composition has not been visually verified.
  Location: full-player wide layout.
  Evidence: the production build succeeds, but no browser-rendered `1440 × 900` or `860 × 520` screenshot is available.
  Impact: optical balance, text wrapping, or compact-height overflow could still need refinement.
  Fix: capture both viewports, compare the artwork/control proportions and lyric panel density, then correct any visible drift.

**Implementation Checklist**

- Capture the player at `1440 × 900` and `860 × 520`.
- Verify artwork remains square and the full control stack stays visible at the compact height.
- Verify the centered header label has no trailing action, the lyrics/queue pill tabs remain balanced, and all keyboard focus states are visible.
- Check the queue tab, seek control, transport controls, and browser console.

**Scoped Follow-up Changes**

- Desktop progress and transport now reuse the mobile interaction set: repeat, previous, play/pause, next, and queue.
- The queue control switches the desktop content pane to the queue tab.
- The top-right playback-device control was removed.
- The lyrics/queue surface no longer has a card background, border, shadow, or backdrop blur.
- The desktop header now contains only the centered `Now Playing` label; the song title was removed from the header.
- The top-left overflow icon was removed and a vertical overflow control now sits beside Favorite using the same `48px` / `40px` mobile control seats and icon sizing.
- The desktop artwork height now reserves `360px` for metadata, progress, margins, and transport, keeping the full bottom control stack visible down to the `860 × 520` desktop threshold.
- The queue header no longer shows Shuffle, Repeat, or Autoplay shortcuts; the equivalent landscape queue shortcuts were removed for cross-orientation consistency.
- The desktop lyrics/queue pill switcher is hidden; the transport queue control now toggles between queue and lyrics so the removed switcher does not create a dead end.
- Production build passes after the scoped changes; browser screenshot comparison remains unavailable.

**Follow-up Polish**

- None classified until visual capture is available.

Final result: blocked
