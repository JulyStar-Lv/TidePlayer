# Miuix native UI ownership

Date: 2026-08-24

Miuix is TidePlayer's generic UI component system. Feature presentation code
uses its v0.9.3 controls directly; TidePlayer components exist only where the
product has music, platform, or composition semantics that Miuix cannot
express.

```text
feature presentation -> Miuix generic controls

feature presentation -> TidePlayer product component -> Miuix primitives
```

This replaces the retired architecture in which every feature control passed
through `App*`/`Design*` wrappers before reaching Miuix. A wrapper is not kept
to rename an API, preserve an old color/padding/corner radius, or shield a
feature from Miuix.

## Fixed API baseline

- Miuix: `0.9.3`
- Kotlin: `2.4.0`
- Compose Multiplatform: `1.11.1`

The review uses the official `v0.9.3` commit
`c36fab72391801d1e3ea5a00f966bf16bac28d4c`; no API from `main` is used.

- https://github.com/compose-miuix-ui/miuix/tree/v0.9.3/docs/components
- https://github.com/compose-miuix-ui/miuix/tree/v0.9.3/miuix-ui/src/commonMain
- https://github.com/compose-miuix-ui/miuix/tree/v0.9.3/miuix-preference/src/commonMain

Feature modules add `miuix-ui` when they directly use generic controls and add
`miuix-preference` only when they use a preference. This is deliberate: it
makes the dependency visible without recreating a local UI framework.

## commonMain inventory and migration matrix

The inventory covered all `commonMain/**/*.kt` files, specifically wrappers
named `App*`, `Design*`, and `Compat*`, plus `Popup`, `BasicTextField`,
click/select handlers, `Canvas`, dialog/sheet, navigation, card, tab, and
feedback controls.

| Existing component family | v0.9.3 equivalent | Decision | Reason |
| --- | --- | --- | --- |
| `AppPreference`, `AppArrowPreference`, `AppSwitchPreference`, `AppSliderPreference`, `AppDropdownPreference` | `BasicComponent`, `ArrowPreference`, `SwitchPreference`, `SliderPreference`, `OverlayDropdownPreference` | DELETE | No business behavior; they only overrode margins and dividers. |
| Settings rows and groups | Preferences, `BasicComponent`, `SmallTitle`, `Card`, `Scaffold`, `TopAppBar`, `SmallTopAppBar` | DIRECT_MIUix | Settings uses native defaults and native Miuix app bars; no settings screen uses the liquid-glass action bar. |
| `Form.kt` and `AppMiuixTheme` | Miuix `TextField`, `Switch`, and root `MiuixTheme` | DELETE | Both only hid Miuix APIs. Playlists call `TextField` directly, and the source editor keeps only source-scoped password/error composition where v0.9.3 has no single error-field semantic. |
| source and plugin configuration switches | `SwitchPreference` | DIRECT_MIUix | Removed hand-written rows, divider handling, disabled alpha, and switch interaction forwarding. |
| `AppSnackbar*` | `SnackbarHostState`, `SnackbarHost`, `SnackbarDuration`, `SnackbarResult` | DELETE | Miuix owns queueing and result types. |
| `DesignCheckbox`, `CompatCheckbox` | `Checkbox` / `CheckboxPreference` | DIRECT_MIUix | The old code duplicated checked drawing and checkbox semantics. |
| `DesignSwitch`, `DesignTextField`, `DesignLinearProgressIndicator`, `DesignLoadingIndicator` | `Switch`, `TextField`, progress indicators | DIRECT_MIUix | Thin forwarding wrappers are being removed at call sites. |
| `DesignButton`, `DesignTextButton`, generic `DesignIconButton`, `DesignFab` | `Button`, `TextButton`, `IconButton`, `FloatingActionButton` | DIRECT_MIUix | Deleted after migrating all ordinary call sites to native Miuix controls and defaults. |
| `DesignTabs` | `TabRow`, `TabRowWithContour` | DIRECT_MIUix | Miuix owns selection behavior, animation, and semantics. |
| `DesignListDivider` | `HorizontalDivider`, `VerticalDivider` | DIRECT_MIUix | Deleted; complex rows now compose a real divider node. |
| `DesignCardSurface` | `Card` | DIRECT_MIUix | Deleted after ordinary callers moved to native Miuix `Card`; artwork/glass remain specifically named product UI. |
| `DesignTopBar`, `DesignPageHeader` | `SmallTopAppBar`, `TopAppBar`, `MiuixScrollBehavior` | DIRECT_MIUix | Deleted; ordinary pages use Miuix bars while immersive player headers remain product UI. |
| `DesignSearchBar`, library search fields | `SearchBar` / `InputField` | DIRECT_MIUix | Deleted; callers use `InputField` and its built-in search icon, clear behavior, and semantics. |
| `DesignSettingsGroup`, `DesignPreferenceRow`, `SettingsEntryCard`, `SettingsActionRow`, `SettingsIconBadge` | `SmallTitle`, `Card`, `BasicComponent`, `Icon` | DIRECT_MIUix | Deleted; settings and diagnostics compose native controls directly, and settings start actions are plain Miuix icons rather than gradient tiles. |
| `DesignChevron` | `Icon` with existing vector resources | DIRECT_MIUix | Deleted; it was only a resource/size forwarding wrapper. |
| `DesignChipSection` | `SmallTitle`, `Text`, `FlowRow` | DIRECT_MIUix | Deleted; Search/Browse own their domain-specific tag layouts. |
| `TagChip` | none | PRODUCT_SPECIFIC | The fixed Miuix version lacks a semantic equivalent for source/status/search tags; this is the minimal retained primitive. |
| `EmptyState`, `StatusMessageCard`, `StatusBadge`, `SkeletonBlock` | none | PRODUCT_SPECIFIC | Miuix 0.9.3 has no semantic empty-state, status-message, state-label, or skeleton primitive. The retained compositions use native `Card`, `Text`, progress, and color primitives; the `StatusBadge` expresses diagnostic/download/source state rather than notification state. Listening's insight icon is private to that product page; ordinary download rows and settings entries no longer use decorative gradient icon tiles. |
| Context menus | `DropdownEntry`, `DropdownItem`, `OverlayDropdownPopup` | DIRECT_MIUix | Feature call sites construct native dropdown entries directly; v0.9.3 has no per-item danger-text API. |
| `DesignBottomSheet` | `OverlayBottomSheet` | DIRECT_MIUix | Default sheet colors, radius, and margins are preferred. |
| generic `DesignDialog` | `OverlayDialog` | DIRECT_MIUix | Deleted; confirmation, input, settings, and player source dialogs use Miuix overlays directly. |
| HSV theme picker shell | `ColorPicker`, `TextField`, `OverlayDialog` | PRODUCT_SPECIFIC | Saved seeds, presets, preview, and persistence are theme-domain behavior; the HEX input uses Miuix `TextField`. |
| Navigation rail/sidebar | `NavigationRail`, `NavigationRailItem`, `NavigationRailState` | DIRECT_MIUix | `HomeNavigationRail` is the sole desktop rail; it uses native state to select collapsed or expanded Miuix presentation and `vectorResource` for existing vector resources. |
| Home bottom navigation | none | PRODUCT_SPECIFIC | `HomeFloatingNavigationBar` is private to the music home chrome and preserves the MiniPlayer/tab hierarchy. Its icons, text, colors, and accessibility semantics are direct Miuix primitives; it is not a reusable navigation framework. |
| queue drag and plugin configuration overlays | none | TEMP_EXCEPTION | v0.9.3 has no equivalent for the draggable queue sheet or the plugin editor's large-screen layout. `OverlayPresentationSupport` and `PlatformOverlayHost` only supply that missing platform/layout behavior. |
| playback slider and playback controls | none | PRODUCT_SPECIFIC | Buffered progress, seek handling, transport controls, and playback thumb are music behavior. |
| `MusicCover`, `ImportCover`, artwork media helpers | none | PRODUCT_SPECIFIC | Cover rendering, fallback art, artwork palette transition, and shared artwork elements are music/import content presentation rather than generic cards or image controls. |
| track-number badge | none | PRODUCT_SPECIFIC | Track sequence and active-playback state are music semantics; the retained `TrackNumberBadge` is not a generic list-row framework. |
| MiniPlayer, Now Playing, lyrics, artwork, queue drag, EQ/DSP, visualization, `LiquidGlass*` / `StickyHeader*` | none | PRODUCT_SPECIFIC | These are TidePlayer's product UI, not generic component styling. The retained liquid-glass scene and sticky-header coordination are explicitly named for that visual behavior rather than exposing a generic `Design*` API. DSP parameter controls use native `SliderPreference` and retain only DSP-specific delayed commit behavior. |
| breadcrumb path navigation | none in v0.9.3 | TEMP_EXCEPTION | `BreadcrumbBar` is not in the fixed API baseline. |

## Rules

- Prefer Miuix defaults for spacing, colors, corner radius, disabled state,
  animations, feedback, and semantics. Do not layer `clickable`, `toggleable`,
  `selectable`, or duplicate accessibility semantics over a Miuix control.
- A retained component must be explicitly product-, music-, platform-, or
  missing-API-specific. Generic names such as `AppButton` or `DesignCard` are
  not acceptable retention reasons.
- `miuix-blur` and Navigation3 remain out of scope. No Kotlin, Compose, or
  Miuix version upgrade is part of this work.
- Verify Android, iOS Simulator, and Desktop after each migration batch.

## Latest verification

- `:shared:compileDebugKotlinAndroid`, `:shared:compileKotlinIosSimulatorArm64`, and
  `:desktopApp:compileKotlinDesktop` passed together on 2026-08-24. The same
  aggregate command also ran `:core:presentation:desktopTest` successfully.
- The current Settings pass directly migrated the remaining generic controls in
  playback, storage, source management, diagnostics, lyrics, networking,
  equalizer, and DSP: groups now use
  `SmallTitle` + `Card`; switches, selections, sliders, informational rows,
  and destructive actions use their corresponding native Miuix preference or
  basic component. All temporary `Settings*Row`/`SettingsSelectOption` APIs
  have been deleted. `Form.kt` and the unused `AppMiuixTheme` layer were also
  deleted; playlists, sources, and Settings compile against native Miuix
  controls. Source-account scanning/editor cards remain product-specific
  compositions. `:feature:settings:compileKotlinDesktop`,
  `:feature:sources:compileKotlinDesktop`, and
  `:feature:playlist:compileKotlinDesktop` passed after this pass on
  2026-08-24. The source feature explicitly depends on `miuix-preference` for
  its direct `SwitchPreference` use.
- `:shared:desktopTest` passed on 2026-08-24. UI convergence did not require
  modifying preference, database, metadata, playback, or lyric business logic.

## Explicit default overrides

The remaining intentional overrides are audited below. Ordinary Settings,
cards, dialogs, sheets, and controls do not retain old Tide padding,
gradients, radii, or divider masking. The Settings root uses a compact
`BasicComponent` composition because Miuix 0.9.3 preferences bind their title
to the app's global `headline1` style.

| Location | Override | Why it remains |
| --- | --- | --- |
| `core/presentation/theme/Theme.kt` | Miuix colors/text styles and seed-transition animation | Root-level light/dark, manual/artwork color seed, and system-bar integration; this is the app theme rather than a replacement control API. |
| `core/presentation/media/ArtworkImage.kt`, `PlayerBackground.kt`, `FavoritesPlaylistArtwork.kt`, playlist shared artwork | artwork fades, collage gradients, and artwork corner radii | Artwork rendering and cross-screen artwork continuity are music content behavior. |
| `PlaybackSlider.kt`, `PlaybackControlButton.kt`, `PlayerChromeComponents.kt`, `nowplaying/*` | buffered seek track, playback geometry, Liquid Glass, and player animation | Player-specific interaction/drawing with no equivalent Miuix control. |
| `OverlayPresentationSupport.kt`, `CustomAnchoredDraggableState.kt`, `QueueDialog.kt` | drag/settle animation and wide overlay geometry | The fixed Miuix version lacks the queue drag sheet and large-screen editor layout. |
| `SourceSettingsSection.kt` and source editor | scan/status gradients, progress animation, selected save action | Source scan/account state and source-editor workflow are domain state; basic fields and switches remain native Miuix controls. |
| `ImportScreen.kt` | three content-driven desktop minimum widths | Long source/file labels require a bounded wide layout; no Miuix visual token is overridden. |
| `AlbumScreen.kt`, `PlaylistScreen.kt`, `LocalizedSourceEditorScreen.kt` | contextual icon action color | Direct Miuix `IconButton` calls indicate the current editing/save state; there is no local button variant API. |
| `SettingsRows.kt` | native Miuix primary/error `ButtonDefaults` colors | Confirm/save actions express their native Miuix semantic color; they do not recreate a Tide button palette. |
| `SettingsScreen.kt` | Miuix `BasicComponent` title/summary text and compact inside margin | The Miuix 0.9.3 `ArrowPreference` cannot select a title style; this root-page composition keeps native Miuix semantics while preventing the global display headline from inflating every entry. |
| `BottomBar.kt` | private home tab geometry and selected-state rendering | This is the one product navigation surface coupled to MiniPlayer; it uses Miuix `Icon` and `Text`, and does not expose a reusable component API. |

Native Miuix dialogs are used for ordinary overlays; the two explicitly listed
missing-capability overlays are not approved as a new generic dialog system.

## Component footprint

For the current checkout's final cleanup diff, the component package changed
from 17 Kotlin files / 2,264 lines at `HEAD` to 16 Kotlin files / 2,125 lines:
`Form.kt` was deleted. This is a conservative, reproducible snapshot count;
the broader wrapper removals listed in the matrix were already present in the
checked-out baseline and are therefore not attributed to this final diff.
