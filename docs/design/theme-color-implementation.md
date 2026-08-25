# Theme color design and implementation audit

Date: 2026-07-28

## Decision

TidePlayer now uses one cross-platform theme-seed model:

- the current artwork supplies the active seed when artwork color is enabled and
  extraction succeeds;
- the persisted Manual Theme Seed is the fallback for missing or failed artwork;
- loading keeps the previous valid artwork seed when one exists;
- disabling artwork color always uses the Manual Theme Seed.
- the root uses `ThemeController` so Miuix consumers can observe the active
  `ColorSchemeMode` and dynamic-color state while TidePlayer's primary-color
  overrides remain intact.

Artwork color and manual Theme color selection are mutually exclusive. The
Theme color row stays visible while artwork color is enabled, but it is disabled
and explains that artwork color must be turned off before editing. Turning
artwork color off enables the row and manual picker.

The user-facing system-wallpaper/Android dynamic-color entry has been removed.
`Monet` remains only as an internal Miuix library enum name.

## Design-to-Compose mapping

| Design contract | Repository source | Result |
| --- | --- | --- |
| Appearance IA: Theme → Color → Language | `AppearanceSettingsSection.kt` | Matched |
| Artwork color on/off and artwork status copy | Compose Resources plus `AppearanceSettingsSection.kt` | Matched |
| Artwork-on disables Theme color; artwork-off enables it | `canSelectManualThemeColor`, `DesignPreferenceRow`, and `AppearanceSettingsSection.kt` | Matched and exercised |
| Artwork/manual/fallback seed matrix | `ThemeSeed.kt` and `Root.kt` | Matched |
| Default Manual Theme Seed `#FF5B8A` | `SettingsModels.kt` and `Color.kt` | Matched |
| Six presets and a 12-color saved palette | `ThemeColorPickerDialog.kt` and DataStore settings | Matched |
| Local HSV/Hex preview; Apply persists | `ThemeColorPickerDialog.kt` | Matched |
| Invalid Hex has icon/message and disables Apply | `ThemeColorPickerDialog.kt` | Matched and exercised |
| Light and dark generated previews | `ThemeSeedPreviewTheme` | Matched |
| Semantic error colors are stable | Miuix Spec 2025 palette generation plus preview note | Matched |
| Swatch 48 dp; minimum target 48 dp | `DesignColorPicker` and swatch semantics | Matched |
| Dialog max 760 dp; content max 720 dp | `DesignDialog.kt` and `DesignColorPicker` | Matched |
| HSV area 180 dp; indicator 20 dp; Hue visual 32 dp | `DesignColorPicker` | Matched |
| Grid gap 12 dp; section gap 20 dp | `DesignColorPicker` | Matched |
| Theme transition 400 ms | `DesignMotion.themeMillis` | Matched |
| Compact one-column/vertical actions; wider horizontal actions | `ThemeColorPickerDialog.kt` | Matched |
| Pointer, touch, keyboard, and accessibility descriptions | picker pointer/key/semantics modifiers | Matched in source |

## Runtime evidence

The Desktop application was launched with a separate temporary bundle identifier
and temporary user home. It did not read or mutate the installed application's
settings.

- Invalid `GG0000`, visible warning, disabled Apply:
  `Design/exports/theme-color/actual-desktop-picker-invalid-hex.png`
- Yellow seed, dark:
  `Design/exports/theme-color/actual-desktop-yellow-dark.png`
- Yellow seed, light:
  `Design/exports/theme-color/actual-desktop-yellow-light.png`
- Artwork color off, persisted Yellow summary:
  `Design/exports/theme-color/actual-desktop-expanded-appearance-artwork-off.png`
- Artwork color on, Theme color visibly disabled:
  `Design/exports/theme-color/actual-desktop-expanded-appearance-artwork-on-locked.png`
- Artwork color off, Theme color enabled:
  `Design/exports/theme-color/actual-desktop-expanded-appearance-artwork-off-enabled.png`
- Picker opened only after artwork color was turned off:
  `Design/exports/theme-color/actual-desktop-expanded-picker-artwork-off.png`

Observed behavior:

- choosing Yellow updated the picker preview without saving;
- Apply persisted `#FFD93D` and recolored the complete Miuix theme;
- light/dark mode kept readable text and controls for the high-luminance Yellow
  seed;
- invalid Hex showed a non-color-only warning and disabled Apply;
- Cancel discarded the invalid draft and retained `#FFD93D`;
- the no-artwork and artwork-off summaries matched the design state matrix;
- clicking Theme color while artwork color was on did not open the picker;
- turning artwork color off immediately enabled Theme color and allowed the
  picker to open.

iOS Compact evidence from a fresh iPhone 16e / iOS 26.4 simulator:

- Artwork color on, Theme color disabled:
  `Design/exports/theme-color/actual-ios-compact-appearance-artwork-on-locked.png`
- Artwork color off, Theme color enabled:
  `Design/exports/theme-color/actual-ios-compact-appearance-artwork-off.png`
- Picker opened from the enabled Theme color row:
  `Design/exports/theme-color/actual-ios-compact-picker-artwork-off.png`
- Yellow applied and persisted while artwork color remained off:
  `Design/exports/theme-color/actual-ios-compact-yellow-light.png`

## Platform verification

| Platform | Build/runtime status | Screenshot status |
| --- | --- | --- |
| Desktop Expanded | Latest distributable built, launched in an isolated bundle/user home, and exercised at `1017 × 683` | Available above |
| Android Compact | `:androidApp:assembleDebug` passed; physical Android 13 USB installation was rejected by the device with `INSTALL_FAILED_USER_RESTRICTED` | Not captured; no screenshot fabricated |
| iOS Compact | Xcode 26.4 Debug simulator build passed; clean-installed and exercised on iPhone 16e / iOS 26.4 | Artwork-on locked, artwork-off enabled, picker, and applied Yellow captured |

### Android manual verification

1. Enable “Install via USB” on the connected test device.
2. Run `./gradlew :androidApp:assembleDebug`.
3. Run
   `adb install -r androidApp/build/outputs/apk/debug/androidApp-arm64-v8a-debug.apk`.
4. Open TidePlayer → Settings → Appearance.
5. Confirm Theme color cannot open while artwork color is on.
6. Turn artwork color off, confirm Theme color becomes enabled, then capture the
   picker, Brand Pink light/dark, Yellow light/dark, and the no-artwork fallback.
7. Remove the QA package with
   `adb uninstall io.github.julystar.musicapp`.

### iOS verification

The App scheme was built with Xcode 26.4 for an iPhone 16e simulator after
isolating `JAVA_TOOL_OPTIONS` and Kotlin/Native data paths. The app was
clean-installed, then the full interlock path and applied Yellow state were
captured. The temporary simulator was removed after verification.

## Design differences and unsynced items

- No design-to-Compose copy, token, state, fallback, or interaction mismatch was
  found in the audited theme-color scope.
- The external Figma Make document was not modified because this environment did
  not expose an editable document. The exact manual handoff is
  `Design/docs/figma-theme-color-sync-checklist.md`.
- Android runtime screenshots remain unverified platform evidence and were not
  fabricated. iOS and Desktop runtime evidence is current and matched the
  repository-maintained design source.
