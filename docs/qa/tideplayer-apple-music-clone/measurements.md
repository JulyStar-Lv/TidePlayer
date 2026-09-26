# Apple Music library parity measurements

## Capture normalization

- Reference and common TidePlayer viewport: `980 × 600 pt` (`1960 × 1200 px` Retina capture), light appearance, Chinese UI, bottom mini-player visible.
- Shared shell: `208 pt` sidebar, `44 pt` page toolbar, main content origin `x = 208 pt`, page-title origin `x = 228 pt`.
- Responsive checks now include `960 × 520 pt` (project minimum), `1200 × 760 pt`, the Apple/Tide 4→5-column boundary around `1208 pt` window width, and the largest same-machine capture `1379 × 900 pt`.

## Songs

- Header height: `21 pt`; song row height: `22 pt`.
- Table leading inset: `10 pt`; status `16 pt`; title `196 pt`; more `28 pt`; artist `87 pt`; album `162 pt`; duration `39 pt`; year `48 pt`; favorite `28 pt`.
- Measured text origins at the common viewport: title `x ≈ 238 pt`, more `x ≈ 444 pt`, artist `x ≈ 462 pt`, album `x ≈ 549 pt`, duration `x ≈ 716 pt`, year `x ≈ 749 pt`, favorite center `x ≈ 808 pt`.
- The active sort header uses the foreground color and semibold weight. Rows alternate without cards; the focused/menu-active selection is Apple red, and the unfocused selection is neutral gray.
- Long English, Chinese, mixed-script titles and missing/placeholder album metadata were exercised with the real TidePlayer library; cells use one-line ellipsis and stable fixed columns.

## Albums

- Common viewport: four columns, cover `163 × 163 pt`, `20 pt` horizontal gap, grid origin `x = 226 pt`, `y = 68 pt`, `7 pt` cover-to-label gap and `7 pt` cover radius.
- Grid content width is `748 pt` at the normalized four-column viewport. The verified responsive rule is three columns below `570 pt` content width, four below `1000 pt`, otherwise five. Apple Music remains four columns at a `1200 pt` window (`992 pt` content) and switches to five by `1208 pt` (`1000 pt` content); TidePlayer now uses the same boundary.
- Covers consume the available width instead of staying fixed at `163 pt`: with `18 pt` leading inset, `42 pt` trailing inset and `20 pt` gaps they are `163 pt` at `980 × 600`, about `218 pt` at `1200 × 760`, about `172 pt` immediately after the five-column switch, and about `206–207 pt` at the largest `1379 × 900` capture. At the largest capture Apple first-row x positions are approximately `226/452/678/904/1130 pt`; TidePlayer differs by only about `0–2 pt` on the measured covers.
- Row-to-row distance remains content-aware because a wrapped two-line album title increases that row's measured height; this is the same non-overlapping behavior as the native grid.
- Hover follows the installed Music.app capture: the cover remains undimmed and exposes a compact lower-right play target; the previous full-cover dim/center-play/lower-right-more treatment was removed. Click navigates to the real album route and double-click queues the album.

## Artists

- Master pane: `300 pt`; divider: `1 pt`; master top inset: `8 pt`; horizontal inset: `10 pt`; row height: `54 pt`; artwork/avatar: `36 pt` circle.
- Detail pane horizontal padding: `30 pt`; top padding: `22 pt`; heading row: `47 pt`; a selected artist has the source `35 pt` real track-count line. The aggregate All Artists view intentionally omits that line, matching Music.app and keeping the first album group at the measured top position.
- Album cover: `116 × 116 pt`, top `y ≈ 179 pt`; cover-to-album-title gap: `38 pt`; artist track row: `46 pt`.
- The desktop heading is the source label `艺人` (`Artists`) rather than the broader app's legacy `歌手` copy. The All Artists artwork uses the native macOS `music.microphone` symbol exported on its intrinsic `24 × 24 pt` canvas; no extra transform is applied.
- The master selection, artist switch, contributor-name splitting, current-playing indicator, real album grouping, queue play and shuffle were exercised. The selected artist capture contains one real track and one real album.
- Artist track rows now expose the measured hover surface and a secondary-click menu. Supported commands follow the source order `下载 → 播放 → 喜爱/取消喜爱`, with source-style separators; every visible entry is wired to the real library action.

## Album detail

- Toolbar: `44 pt`; scroll content padding: `40 pt` horizontal and `8 pt` top.
- The source toolbar uses a standalone `36 pt` back control, a single `72 × 36 pt` grouped download/more capsule, an `8 pt` gap, and a `198 × 34 pt` search field. The final TidePlayer toolbar uses the same grouping and dimensions instead of two unrelated circular buttons.
- Hero cover: `270 × 270 pt`, global origin `x = 248 pt`, `y = 52 pt`, `7 pt` radius; cover-to-metadata gap: `32 pt`.
- Hero height: `290 pt`; action buttons: `128 × 36 pt`, `8 pt` gap; track row: `46 pt`.
- The favorite/playing/number cluster is offset to the same leading positions as Music while the rounded row background remains aligned with the cover.
- The active track uses the source's red pause-bars indicator. Detail and row menus preserve the native command order for the subset TidePlayer can execute, including group separators.
- Play and shuffle use the existing queue and playback controller. Runtime play changed the mini-player to the selected album track and exposed the Pause action.

## Diff artifacts and interpretation

- Current final-package side-by-side evidence at `980 × 600 pt`: `final-songs-980x600-comparison.jpg`, `final-albums-980x600-comparison.jpg`, `final-artists-selected-980x600-comparison.jpg`, and `final-album-detail-980x600-comparison.jpg`. Matching `*-overlay.jpg` and `*-diff.jpg` files preserve 50% overlays and enhanced differences. Earlier `latest-*` and `comparison-*` files remain the iteration history.
- 50% overlays: `overlay-*.jpg`; contrast-enhanced pixel differences: `diff-*.jpg`.
- Current final-package whole-frame RGB mean absolute differences are Songs `17.9582`, Albums `47.6984`, selected Artist `10.1938`, and Album detail `16.3347` on the `0–255` channel scale. The artist comparison is now a like-for-like concrete-artist selected state (Apple's selected artist versus TidePlayer's selected Adele). These totals deliberately include different library titles, covers, playback progress, sidebar destinations and metadata; they are evidence, not a visual-pass threshold.
- Toolbar, artist-action and album-detail pill controls now use separate normal, hover, pressed and disabled surface opacities through their shared interaction source; focus-aware row selection remains red while the window is active and neutral gray while inactive.

## Final color and toolbar calibration

- The large focused row fill in the Apple artist reference has raw screenshot median RGB `[202, 47, 51]`. The final focused Artist and Songs selections produce the same median `[202, 47, 51]`; navigation icons and action accents retain the brighter source accent instead of reusing the darker fill token.
- The first material attempt used a `5 pt` shadow and was visibly too heavy (`artists-toolbar-calibrated.jpg`). The retained implementation uses a `2 pt` low-alpha shadow, near-white light fill, and a `0.5 pt` low-alpha border (`artists-toolbar-calibrated-2.jpg`).
- On the fixed Artist toolbar crop, mean absolute difference improved from `12.1809` to `7.9011`; the empty search interior improved from `12.0000` to `0.4667`. On the final common captures, the corresponding full toolbar regions improved from `6.3313` to `4.3602` for Songs, `6.4532` to `5.5091` for Albums, and `4.8137` to `3.6077` for Artists.
- The album-detail toolbar region improved from `6.7673` to `4.9090`, its grouped-control region from `5.7244` to `4.1986`, and its search region from `16.8293` to `10.7236`. The updated more control still opens the real Download/Play menu; disabled Download remains exposed as disabled when the local album has no downloadable source.

## Window focus evidence

- Compose Desktop's `LocalWindowInfo.isWindowFocused` remained false after the native macOS window became active. The packaged host now publishes macOS application foreground/background events through `LocalDesktopWindowFocused`; Windows/Linux continue to use the owning AWT window focus listener, while tests and previews fall back to `LocalWindowInfo`.
- Focused evidence: `artists-selected-focused-app-event-final.jpg` shows colored native traffic lights, a red selected Adele row and a red Artists sidebar destination. Unfocused evidence: `artists-selected-unfocused-app-event-final.jpg` shows gray traffic lights, a neutral selected row and muted sidebar destinations. `songs-selected-focused-final.jpg` confirms the same focused red selection treatment on a long mixed-content song row.
- The same-state artist comparison was regenerated as `latest-artists-selected-focused-final-comparison.jpg`, `latest-artists-selected-focused-final-overlay.jpg` and `latest-artists-selected-focused-final-diff.jpg`; its mean RGB absolute difference is `10.1916/255`, improving the earlier mixed-focus comparison.

## Motion sampling evidence

- Album grid-to-detail sampling is saved under `docs/qa/apple-music-reference/motion-album-detail/` and `docs/qa/tideplayer-apple-music-clone/motion-album-detail/`, with capture timestamps in each directory's JSON file.
- The first post-click frame obtainable through accessibility capture was already settled: Apple Music at `824 ms`, TidePlayer at `1086 ms`. In the top `1000 px`, the grid-to-detail jump measured `54.3235` and `50.5330` mean RGB levels respectively; subsequent Apple frames varied by `0.0065–0.2509` and TidePlayer frames by `0.0009–0.0052`, attributable to playback/cursor/JPEG changes rather than visible layout motion.
- These samples establish only capture-latency upper bounds for reaching the settled detail layout. They do not expose intermediate animation frames, so transition easing and duration parity remain unverified rather than being inferred from static screenshots.

## Remaining source/capability differences

- TidePlayer's real library is not Apple Music's library, so titles, artwork, favorites, current time and row wrapping differ where the underlying records differ.
- TidePlayer currently has no artist portrait store or artist-favorite model. Artist avatars therefore use the first real matching track artwork and the artist-favorite control is visibly disabled instead of fabricating state.
- TidePlayer's album model does not always provide genre, full release date, copyright, Apple catalog rating, recommendation state or Apple lossless provenance. Missing fields stay absent.
- TidePlayer has no Apple Music playlist/recommendation/library-deletion command surface for these rows. The context menu therefore exposes only wired actions (play, favorite, download); unsupported native Apple menu items are not shown as inert imitations.
- The existing TidePlayer sidebar retains its real destinations (including Settings/Recently Added/Genres) and does not invent Apple Music's New/Radio/profile destinations.
- Native AppKit material sampling and Compose material rendering are close but not byte-identical. The saved overlays/diffs preserve that residual evidence; this document does not claim global pixel identity.
- Timestamped post-click screenshots now bound the settled detail state, but the first obtainable frames occurred too late to expose the transition itself. Motion easing and duration parity therefore remain unverified rather than being claimed from settled screenshots.
- The final code revision (desktop `艺人` heading, native microphone asset, aggregate-heading spacing and album-detail pause marker) compiled, packaged, relaunched and was recaptured at the common viewport. The latest evidence files listed above supersede the earlier locked-host note.
- Final handoff evidence `songs-final-build-open.jpg` shows the rebuilt `2026-09-25 10:24` bundle running in normal mode on Songs with the window focused.

## Final toolbar baseline and title-color calibration

- The common library toolbar content was moved down `4 pt`, and the album-detail toolbar received the same offset. At the normalized Retina capture, Apple Music's page-title ink box is `x = 457…505 px, y = 41…63 px`; TidePlayer is `x = 457…506 px, y = 41…63 px`. The prior TidePlayer title occupied `y = 32…55 px`.
- Before the baseline correction, the focused Artist page-title crop measured `9.7533/255`; after the offset it measured `2.7405/255` under the original tight-crop method. On the fixed documented crop `x = 440…534 px, y = 20…81 px`, the subsequent neutral-title-color correction improved `5.3113 → 4.2806/255`.
- The final light-title core median is RGB `[79, 79, 79]`, exactly matching the Apple Music reference on the same mask. Dark appearance continues to use the theme foreground rather than the light-only override.
- Controlled regions improved after the baseline offset: focused Artist main-content toolbar `3.9119 → 3.0240`, right toolbar `8.6047 → 7.6219`, and search crop `9.6892 → 8.8531`. Album detail improved whole-frame `16.3333 → 16.3069`, toolbar `4.9090 → 4.4954`, controls `4.5747 → 4.0811`, search `10.7236 → 9.7246`, and Back `10.4967 → 6.6755`.
- Final-package paired artifacts are `latest-songs-final-build-open-toolbar-baseline-{comparison,overlay,diff}.jpg`, `latest-albums-final-build-open-toolbar-baseline-{comparison,overlay,diff}.jpg`, `latest-artists-selected-toolbar-baseline-color-final-{comparison,overlay,diff}.jpg`, and `latest-album-detail-final-build-open-toolbar-baseline-{comparison,overlay,diff}.jpg`. Their whole-frame means are Songs `20.8700`, Albums `48.2656`, selected Artist `9.9504`, and Album detail `16.3069`; these totals still include different real records, covers and moving player content, so controlled-region measurements are the valid evidence for the toolbar change.
- The final distributable was built at `2026-09-25 11:01`, relaunched through normal startup, exercised through Albums → album detail → Back → Songs, and left open on Songs. Final handoff screenshot: `songs-final-build-open-toolbar-baseline.png`.

## 2026-09-25 final responsive/interaction verification

- Songs: the table is positioned with the measured header baseline, neutral-black light-theme row text, and a blue nonselected current-playing speaker matching the installed Music.app source. Multi-select was exercised with Command-click; secondary-clicking an already-selected row now preserves the whole multi-selection while opening the real context menu.
- Albums: hover was remeasured directly from Music.app and reduced to the native lower-right play affordance. The grid was remeasured at `980`, `1200`, `1208`, and `1379 pt` window widths; the four-to-five-column boundary is now the verified `1000 pt` main-content width rather than the earlier fixed-cover rule.
- Artists: focused and unfocused selection use the host window focus bridge. The final concrete-artist capture selects Adele and presents the same master/detail structure as the Apple selected-artist reference: selected master row, heading, track-count line, album group and track row.
- Album detail: final package capture verifies the `270 pt` hero, grouped toolbar controls, real play/shuffle actions, active-track pause marker and bottom player. The Back control was exercised after capture and returned to the album grid.
- Final common-view evidence: `final-songs-980x600.jpg`, `final-albums-980x600.jpg`, `final-artists-selected-980x600.jpg`, and `final-album-detail-980x600.jpg`. Large-window evidence includes `albums-responsive-large-final.jpg` (`1200 × 760`) and `albums-max-current-final.jpg` (`1379 × 900`), with paired Apple references under `docs/qa/apple-music-reference/`.

## 2026-09-25 artist-row refinement pending rendered recapture

- The source `artist-selected.jpg` establishes the track-row order as favorite star, track/play status, title, Apple-only rating region, download, duration and More. TidePlayer now mirrors those controllable positions while leaving the unavailable Apple catalog rating region empty instead of fabricating rating data.
- The favorite affordance on the Apple Music desktop surfaces now uses a filled-star state rather than the prior filled-heart state.
- Artist track More and secondary-click menus now use separate trailing-edge and pointer-position anchors respectively.
- The normalized Apple artist-detail track-menu surface is about `424 px` wide at Retina `2×`, i.e. about `212 pt`; the Songs multi-select menu reference remains about `176 pt`. TidePlayer therefore uses `212 pt` only for artist-detail track menus and preserves `176 pt` for Songs and other unmeasured compact menus.
- Context-menu row rhythm is now measured rather than inferred: ordinary Apple item centers are about `48 px` (`24 pt`) apart, while a separator produces about `70 px` (`35 pt`) center spacing. Desktop library/album menus now use `24 pt` rows, `3 pt` outer vertical padding, and a roughly `10.5 pt` separator band.
- Menu label size did not need adjustment: the Download text ink is `23 px` high in both source and implementation at Retina `2×`. Icon ink did: Apple Download/Play is about `16 × 18–19 px` while the previous Tide icon was about `22 × 28 px`; the icon now renders at `10 pt` inside the unchanged `14 pt` layout slot so text alignment remains stable.
- Album hover was remeasured on the exact common cover crop (`452,136,326×326 px`). The source and Tide circle footprint/edge inset were already close enough to retain the existing `29 pt` circle and `12 pt` inset. Only the white play glyph was oversized: about `22 × 28 px` versus Apple's `20 × 23 px`; it now uses `12 pt` with a `1 pt` downward optical offset.
- macOS popup focus handling was refined so opening a child menu does not incorrectly turn the still-active artist/sidebar selection gray. Actual application backgrounding continues to use the macOS foreground/background listener.
- The complete desktop test/package command passes after these changes. The latest rendered comparison is intentionally not claimed yet because the Mac locked before the normalized recapture; the previously recorded Artist metrics remain the last measured evidence until the host is manually unlocked.

## 2026-09-25 final live recapture and menu copy correction

- A fresh source set was captured from the installed Music.app at the normalized `980 × 600 pt` / `1960 × 1200 px` focused light state under `docs/qa/apple-music-reference/2026-09-25-current/`. It includes Songs, the native Songs context menu, Albums, All Artists, a selected artist, and album detail.
- The rebuilt TidePlayer package was captured under `docs/qa/tideplayer-apple-music-clone/2026-09-25-current/`. The final comparison set is `final-songs-*`, `final-albums-*`, `final-artists-selected-*`, and `final-album-detail-*`.
- Whole-frame mean RGB absolute differences are Songs `18.1595`, Albums `47.9087`, selected Artist `15.4039`, and Album detail `16.1567` on the `0–255` scale. These include different real records, artwork and player progress; the side-by-side and overlay images are the authoritative geometry evidence.
- The pending Artist refinements are now rendered and verified: the short menu icons, `24 pt` row rhythm, `35 pt` separator rhythm, `212 pt` artist-menu width, filled-star favorite language and application-focus selection behavior are present in the running package.
- Final visual inspection found one remaining controllable defect in album detail: the favorite menu command embedded the full track title and visibly truncated. The desktop album menu now uses dedicated short `添加到收藏` / `取消收藏` labels while the title-bearing accessibility description remains unchanged.
- `:feature:library:desktopTest :feature:album:desktopTest :shared:desktopTest :desktopApp:createDistributable --offline` passed after that correction (`340` tasks), followed by a normal packaged relaunch and live verification of the corrected menu in `08-album-detail-context-menu-final.png`.
