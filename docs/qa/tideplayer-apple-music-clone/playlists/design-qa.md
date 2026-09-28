# Apple Music — All Playlists verification

Reference: the installed macOS Music.app, Chinese, light appearance, 980 × 600 pt / 1960 × 1200 px.
Implementation: TidePlayer desktop route `MusicGraph.Playlists`. The final isolated Compose render covers the 772 × 600 pt content region at density 2, producing 1544 × 1200 px.

## Findings and corrections

- First runtime capture matched the 169 pt cover, x = 226 pt / y = 62 pt origin, 7 pt radius, 20 pt column gap and RGB 237/238/240 cover background. The old centered empty-state panel and fixed 136 pt cards are replaced on desktop.
- First comparison found larger playlist-name typography, an overly saturated favorite symbol and a sort menu anchored 129 pt too far left. The final implementation uses 12 sp names, 7.5 pt cover-to-label spacing, the sRGB favorite color and a menu aligned with the sort button's leading edge.
- Native `star.fill`, `music.note.list` and `arrow.up.arrow.down` symbols were exported with AppKit at 4×. The star canvas is 63% of cover width; its intrinsic transparent margins yield the measured 90 pt visible symbol.
- Font and copy checks: system sans font, single-line names, Apple search placeholder and sort/filter labels. The shared sidebar/player keep their existing product destinations and playback content.
- Color check: native captures carry the Display ICC profile; isolated Compose PNGs use sRGB. After ICC conversion, star median is Apple RGB 251/35/60 versus Tide RGB 250/36/60. Compare `final-favorites-comparison-srgb.png`, not the unnormalized diagnostic.
- Image check: genuine native symbols and real artwork loading are retained. Demo/Demo2 in the isolated render are test fixtures with missing artwork; the fallback musical-list symbol is intentional. No test playlist was added to the user's library.
- The first runtime favorite-card crop had mean RGB absolute difference 3.553/255. The final ICC-normalized isolated crop is 2.380/255. Native raster antialiasing differs slightly; these figures are local evidence, not a global pixel-identity claim.

## Interaction and capability checks

The isolated desktop UI test exercises actual text input, no-result filtering, clearing the query, double-click navigation, opening the two-level sort menu, selecting Recently Added and descending sort. It verifies that the newest test playlist moves before the older one. Unit tests cover title filtering, timestamp sorting and unchanged repository order.

The desktop list includes the Favorite Songs entry even when there are no user playlists. Play actions use the real favorites/playlist repositories and playback controller. Secondary-clicking blank space retains access to the existing create-playlist dialog.

Current contracts have no playlist-level favorite flag or playlist playback timestamp. Playlist stars and Recently Played are disabled; Favorites Only produces an empty result when there are no favorited playlists. No favorite or playback metadata is fabricated. Those unavailable capabilities prevent full parity with every Apple state, including recent-playback year grouping.

The installed Apple reference retained a `2024` toolbar label after switching from Recently Played to Title. The implementation uses the descriptive `All Playlists` title in its title-sorted state. Different records, artwork, title state and player content are excluded from controlled geometry/color conclusions.

## Validation scope

The original application was captured for the first visual pass. Later concurrent app navigation prevented a stable final native recapture; final geometry, text, menus and interactions were verified in an isolated Compose UI test of the same production screen. The separate temporary app bundle was removed.

An attempted `:shared:desktopTest` failed because pre-existing navigation tests reference the removed `AppleMusicSidebarDestination.RECENTLY_ADDED`. That unrelated test failure is preserved. Playlist tests and desktop packaging pass separately: `:feature:playlist:desktopTest :desktopApp:createDistributable --offline`; 28 tests, zero failures/errors, final build successful. The UI test also verifies that descending creation-date sorting moves Demo2 before Demo. See `sort-menu-final-render.png`.

Final result: verified for the supported title/creation-date list UI, with the capability, data, material and final-native-capture limitations above. Full Apple Music state/pixel parity is not claimed.
