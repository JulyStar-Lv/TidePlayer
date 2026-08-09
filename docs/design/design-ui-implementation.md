# TidePlayer Design UI implementation

This document maps the production Compose Multiplatform UI to the source-of-truth prototype in `Design/`.

## Navigation and adaptive shell

| Design surface | Production implementation |
| --- | --- |
| Mobile root navigation | `BottomBar.kt` + `DesignBottomNavigationBar` |
| Mobile mini player | `PlaybackMiniPlayerHost` + `DesignMiniPlayerBar` |
| Tablet navigation | `NavigationRailBar.kt` |
| Desktop navigation | `SidebarBar.kt` |
| Desktop player placement | Bottom of `HomeMainPane` |
| Root destinations | Home, Search, Library, Settings |
| Now Playing entry | Persistent mini player only |

The obsolete desktop top toolbar has been removed. Compact, medium, expanded, large, and XL layouts all use the same root destination model.

The compact bottom navigation follows the Design source's flat 62 dp bar, 1 dp top divider, 48 × 28 dp selected indicator, 20 dp Lucide icons, and 10 sp labels. Native bottom safe-area padding remains platform-owned.

## Root pages

| Page | Compose implementation | Design coverage |
| --- | --- | --- |
| Home | `HomeDesignScreen.kt` | Daily Picks, Pinned Playlists, Your Listening, Continue Playing, Recently Played, Recently Added, Recommended Artists |
| Search | `SearchDesignScreen.kt` | Search field, history, genre grid, trending, loading/error/empty/results states, source-aware rows |
| Library | `LibraryDesignScreen.kt` | Playlists, songs, albums, artists, genres, folders, favorites, downloads, history, recently added/played, lossless, Hi-Res, sources |
| Settings | `SettingsScreen.kt` + shared settings components | Personalization, Playback, Library & Data, App Info |

The compact Home implementation uses the source prototype's 152 dp Daily Picks banner, real Unsplash cover crops, 160 dp pinned-playlist cards, 120 dp recently-added cards, and ranked monthly-listening preview. Its Home title is a sticky list header: after 48 dp of upward scroll it collapses to the source-aligned 58 dp bar with 24 sp type and a subtle bottom divider. Image attribution is recorded in `Design/ATTRIBUTIONS.md`.

## Secondary and detail pages

Album, Artist, Playlist, Now Playing, Lyrics, Queue, Downloads, Sources, Import, Plugin management, and all Settings subpages retain their existing business state and navigation. Their visual alignment is supplied by the shared components updated in this implementation:

- `DesignPageHeader`
- `DesignCardSurface`
- `DesignSettingsGroup`
- `DesignPreferenceRow`
- `DesignMiniPlayerBar`
- `DesignBottomNavigationBar`
- shared spacing, radius, elevation, blur, motion, and adaptive tokens

## Data rules

- UI screens must render repository/view-model state rather than demo music.
- An empty library displays actionable empty states and source/folder guidance.
- Library playback remains driven by `PlaybackController` and the full-library queue.
- Unsupported source providers must not be shown as active production integrations.
- Now Playing is not a fifth root tab.

## Validation checklist

- Mobile: 390 × 844 and narrow Android widths
- Tablet: 840–1279 dp with navigation rail
- Desktop: 1280+ dp with sidebar and bottom player
- Light and dark themes
- Long titles and translated strings
- Empty, loading, error, disabled, playing, and selected states
- Minimum interactive target: 48 dp where the component surface permits it
