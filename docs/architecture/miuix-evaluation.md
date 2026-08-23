# Miuix native UI ownership

Date: 2026-08-23

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
| Settings rows and groups | Preferences, `BasicComponent`, `SmallTitle`, `Card` | DIRECT_MIUix | Settings uses native defaults rather than the Tide row/container style. |
| `AppSnackbar*` | `SnackbarHostState`, `SnackbarHost`, `SnackbarDuration`, `SnackbarResult` | DELETE | Miuix owns queueing and result types. |
| `DesignCheckbox`, `CompatCheckbox` | `Checkbox` / `CheckboxPreference` | DIRECT_MIUix | The old code duplicated checked drawing and checkbox semantics. |
| `DesignSwitch`, `DesignTextField`, `DesignLinearProgressIndicator`, `DesignLoadingIndicator` | `Switch`, `TextField`, progress indicators | DIRECT_MIUix | Thin forwarding wrappers are being removed at call sites. |
| `DesignButton`, `DesignTextButton`, generic `DesignIconButton`, `DesignFab` | `Button`, `TextButton`, `IconButton`, `FloatingActionButton` | DIRECT_MIUix | Deleted after migrating all ordinary call sites to native Miuix controls and defaults. |
| `DesignTabs` | `TabRow`, `TabRowWithContour` | DIRECT_MIUix | Miuix owns selection behavior, animation, and semantics. |
| `DesignListDivider` | `HorizontalDivider`, `VerticalDivider` | DIRECT_MIUix | Deleted; complex rows now compose a real divider node. |
| `DesignCardSurface` | `Card` | DIRECT_MIUix | Deleted after ordinary callers moved to native Miuix `Card`; artwork/glass remain specifically named product UI. |
| `DesignTopBar`, `DesignPageHeader` | `SmallTopAppBar`, `TopAppBar`, `MiuixScrollBehavior` | DIRECT_MIUix | Deleted; ordinary pages use Miuix bars while immersive player headers remain product UI. |
| `DesignSearchBar` | `SearchBar` / `InputField` | DIRECT_MIUix | Deleted; callers use `InputField` and its built-in clear behavior. |
| `DesignSettingsGroup`, `DesignPreferenceRow` | `SmallTitle`, `Card`, `BasicComponent` | DIRECT_MIUix | Deleted; settings and diagnostics compose native controls directly. |
| `DesignChevron` | `Icon` with existing vector resources | DIRECT_MIUix | Deleted; it was only a resource/size forwarding wrapper. |
| `DesignChipSection` | `SmallTitle`, `Text`, `FlowRow` | DIRECT_MIUix | Deleted; Search/Browse own their domain-specific tag layouts. |
| `TagChip` | none | PRODUCT_SPECIFIC | The fixed Miuix version lacks a semantic equivalent for source/status/search tags; this is the minimal retained primitive. |
| `DesignContextMenu` | Overlay dropdown/cascading menu APIs | DIRECT_MIUix | Per-item danger text is not a v0.9.3 API; use supported menu styling. |
| `DesignBottomSheet` | `OverlayBottomSheet` | DIRECT_MIUix | Default sheet colors, radius, and margins are preferred. |
| generic `DesignDialog` | `OverlayDialog` | DIRECT_MIUix | Deleted; confirmation, input, settings, and player source dialogs use Miuix overlays directly. |
| HSV theme picker shell | `ColorPicker`, HSV controls, `OverlayDialog` | PRODUCT_SPECIFIC | Saved seeds, presets, preview, and persistence are theme-domain behavior. |
| Navigation rail/sidebar | `NavigationRail`, `NavigationRailItem`, `NavigationRailState` | TEMP_EXCEPTION | Existing resource icons must first be converted through `vectorResource`; no painter icon slot exists in v0.9.3. |
| Bottom navigation | `NavigationBar`, `NavigationBarItem` | TEMP_EXCEPTION | Same `ImageVector` limitation; MiniPlayer remains above navigation. |
| queue drag and plugin configuration overlays | none | TEMP_EXCEPTION | v0.9.3 has no equivalent for the draggable queue sheet or the plugin editor's large-screen layout. `OverlayPresentationSupport` and `PlatformOverlayHost` only supply that missing platform/layout behavior. |
| playback slider and playback controls | none | PRODUCT_SPECIFIC | Buffered progress, seek handling, transport controls, and playback thumb are music behavior. |
| MiniPlayer, Now Playing, lyrics, artwork, queue drag, EQ/DSP, visualization, Liquid Glass | none | PRODUCT_SPECIFIC | These are TidePlayer's product UI, not generic component styling. |
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

## Explicit default overrides

The remaining intentional overrides are limited to product behavior: artwork
and Liquid Glass surfaces, playback buffered progress/thumb treatment,
  brand/theme-seed preview, and named wide editor overlays. Ordinary settings,
cards, dialogs, sheets, controls, and navigation must not retain old Tide
padding, gradients, radii, or divider masking. Native Miuix dialogs are used for
ordinary overlays; the two explicitly listed missing-capability overlays are not
approved as a new generic dialog system.
