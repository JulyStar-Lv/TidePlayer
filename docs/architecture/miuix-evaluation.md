# Miuix adoption and component ownership

Date: 2026-08-23

Miuix is a TidePlayer UI implementation dependency. It is not part of the
product architecture exposed to feature code.

```text
feature/*
    -> TidePlayer App*/Design* wrappers
    -> Miuix
```

## Version baseline

- Miuix: `0.9.3`
- Kotlin: `2.4.0`
- Compose Multiplatform: `1.11.1`
- Android minSdk: `29`
- Android compileSdk: `37`

The API review used the official `v0.9.3` tag, commit
`c36fab72391801d1e3ea5a00f966bf16bac28d4c`, rather than the moving `main`
branch. The tag's own version catalog uses Kotlin `2.4.0` and Compose
Multiplatform `1.11.1`, matching TidePlayer's baseline.

Sources:

- https://github.com/compose-miuix-ui/miuix/tree/v0.9.3/docs/components
- https://github.com/compose-miuix-ui/miuix/tree/v0.9.3/miuix-ui/src/commonMain
- https://github.com/compose-miuix-ui/miuix/tree/v0.9.3/miuix-preference/src/commonMain

Miuix remains experimental and may change API without notice. Its API changes
must stop at the wrapper layer; feature code should not gain new direct Miuix
dependencies as part of migrations.

## Dependency decision

`miuix-ui` and `miuix-preference` are allowed in `core/presentation`
`commonMain`. Preference APIs are exposed only through TidePlayer-owned
wrappers:

- `AppPreference`
- `AppArrowPreference`
- `AppSwitchPreference`
- `AppSliderPreference`
- `AppDropdownPreference`

The previous decision to defer `miuix-preference` is retired. Feature modules
must continue using app-owned row APIs such as `SettingsSwitchRow`,
`SettingsSliderRow`, and `SettingsSelectRow`.

These modules remain out of scope:

- `miuix-blur`: do not replace TidePlayer Backdrop/Liquid Glass; its Android
  requirements and visual behavior are not a fit for this migration.
- `miuix-navigation3-ui`: TidePlayer is not migrating to Navigation3.
- `miuix-icons`: TidePlayer keeps Compose resources and `Painter` icons.

## Component matrix

| Component | Previous implementation | v0.9.3 implementation | Decision |
| --- | --- | --- | --- |
| Settings switch | `DesignPreferenceRow` + `AppSwitch` | `SwitchPreference` behind `AppSwitchPreference` | Migrated; one row/switch state callback and Miuix switch semantics |
| Settings entry/info | `DesignPreferenceRow` + `DesignChevron` | `ArrowPreference` behind `AppArrowPreference` | Migrated when clickable; TidePlayer leading badges remain |
| Settings slider | Hand-built title/summary + `DesignSlider` | `SliderPreference` behind `AppSliderPreference` | Migrated; player `DesignSlider` remains custom |
| Settings select | Hand-built `Popup` + option column | `OverlayDropdownPreference` behind `AppDropdownPreference` | Migrated; selection remains caller-owned |
| Plugin configuration select | Full-screen scrim `Popup` + positioned option `Popup` | `AppDropdownPreference` | Migrated |
| Context menu | Compose `Popup`, rows, and delayed dismiss | Miuix `OverlayDropdownPopup` / `OverlayCascadingListPopup` behind `DesignContextMenu` | Migrated; resource icons, disabled items, danger actions, and two-level menus retained |
| Search | Hand-built `BasicTextField`, editable semantics, IME and clear UI | Miuix `InputField` behind `DesignSearchBar` | Migrated; app API and resource search icon retained |
| Toast | `AnimatedVisibility` plus fixed delays | Miuix `SnackbarHostState`/`SnackbarHost` behind app state/host | Migrated; `UiMessage` and `ToastVM` remain unchanged |
| Bottom sheet experiment | `DesignDialog` delegation | `OverlayBottomSheet` behind `DesignBottomSheet` | Migrated as a low-risk adapter experiment; platform dialog hosts remain |
| Theme HSV controls | Custom Canvas, pointer, hover, focus, and keyboard code | Miuix HSV sliders behind `AppHsvColorPicker` | Migrated; presets, saved colors, HEX, preview, and `ThemeSeed` remain |
| Navigation rail/sidebar | Two Painter-based branded layouts | v0.9.3 `NavigationRail` | Deferred: `NavigationRailItem` accepts only `ImageVector`; no painter/content icon slot |
| Bottom navigation | Custom items inside Liquid Glass | v0.9.3 `NavigationBar` | Deferred: item icon is `ImageVector`; Liquid Glass and resource icons must remain |
| General dialog | Adaptive custom dialog/bottom sheet with platform host | `OverlayDialog` / `OverlayBottomSheet` | Deferred: current dialogs require widths up to 760dp while v0.9.3 `DialogContent` caps at 420dp; Android system-bar behavior also needs device validation |
| Tooltip | Handled by existing labeled actions | v0.9.3 `TooltipBox` | Follow-up only for verified desktop-only icon actions; do not alter mobile long-press behavior |
| Badge | Existing product state only | v0.9.3 `Badge` | No migration without an existing badge-worthy state |

Miuix 0.9.3 applies `DropdownColors` to the whole popup rather than an
individual item. `DesignContextMenu` therefore preserves danger emphasis on
the item icon; per-item danger text color remains a documented v0.9.3 limit
instead of reintroducing custom menu rows.

## Repository scan classification

The `commonMain/**/*.kt` scan covers `Popup`, `BasicTextField`, `Canvas`,
click/select controls, dialogs, dropdowns, switches, sliders, snackbars, and
toasts.

- A/B, migrated: settings preferences, plugin configuration selection, source
  action menu, shared context menus, search wrapper, toast presentation, the
  bottom-sheet experiment, and theme HSV controls.
- B, already thin wrappers: text fields, switches, checkboxes, text/icon
  buttons, and progress indicators in `core/presentation`.
- C, retained: playback progress/buffer slider, MiniPlayer, Liquid Glass,
  artwork, lyrics/karaoke rendering, daily-picks effects, EQ/frequency graphs,
  player backgrounds and audio visualizations.
- Specialized input retained: compact library table search, source credential
  fields, and theme HEX entry. Their layout/validation requirements differ from
  the general `DesignSearchBar`; they should be revisited only through an
  app-owned field wrapper.

## Integration rules

- Add Miuix artifacts only through `gradle/libs.versions.toml`.
- Prefer dependencies in `core/presentation`; do not add `miuix-ui` or
  `miuix-preference` independently to every feature.
- Preserve feature/ViewModel state ownership. Wrappers own only transient
  presentation state required by the underlying control.
- Prefer Miuix semantics and interaction behavior; do not layer duplicate
  editable/selectable semantics over Miuix controls.
- Preserve TidePlayer spacing, gradients, icons, player visuals and Liquid
  Glass through wrapper slots and outer surfaces.
- Do not perform a one-shot repository migration. Compile Android, iOS
  Simulator and Desktop after each adoption batch.
- Keep `DesignDialogHost.android.kt`, `.ios.kt`, and `.desktop.kt` until real
  device/window testing proves Overlay dialog parity on all three platforms.
