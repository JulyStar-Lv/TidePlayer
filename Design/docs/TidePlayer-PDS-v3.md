# TidePlayer Product Design System v3

Status: current source-of-truth specification  
Updated: 2026-08-09  
Editable prototype: `Design/src/app/App.tsx` and `Design/src/app/ThemeColorDesign.tsx`

## Product color model

TidePlayer uses a seed color to generate a complete light or dark Miuix color
scheme. A seed is never copied directly into every background or semantic role.
Error, warning, success, and diagnostic roles retain their meanings.
The cross-platform primary role follows the resolution rules below. The default
manual Brand Pink maps to the Apple Music red defined below.

The user-visible setting model has exactly two controls:

1. **Artwork color** — uses the current song artwork when a valid seed can be
   extracted.
2. **Theme color** — selects and persists the manual seed when Artwork color is
   off, and remains the fallback seed in every mode.

The former system-wallpaper dynamic-color, Monet, Android-version, and
unsupported-device controls are obsolete and must not appear in product UI.
`Monet` may remain an implementation-library term, but not a product term.

### Seed definitions

| Token | Definition |
| --- | --- |
| Artwork Theme Seed | Valid representative seed extracted from the current song artwork. |
| Manual Theme Seed | User-selected and persisted theme seed. |
| Fallback Theme Seed | Seed used when artwork is absent, loading without a previous valid seed, or extraction fails. It equals Manual Theme Seed. |
| Default Manual Theme Seed | `#FF5B8A` (TidePlayer Brand Pink). |

### Resolution rules

| Artwork color | Artwork state | Effective seed |
| --- | --- | --- |
| On | Available | Artwork Theme Seed |
| On | Loading | Previous valid Artwork Theme Seed, otherwise Manual Theme Seed |
| On | Missing | Manual Theme Seed |
| On | Failed | Manual Theme Seed |
| Off | Any | Manual Theme Seed |

Deleting a saved custom swatch does not reset the Manual Theme Seed. Editing a
picker draft updates only the local preview. Persistence occurs only after
**Apply color**.

### Control interlock

Artwork color and manual Theme color selection are mutually exclusive:

- while Artwork color is on, the Theme color row remains visible to communicate
  the current fallback status, but is disabled and cannot open the picker;
- turning Artwork color off enables the Theme color row and manual picker;
- turning Artwork color back on closes or prevents any manual picker;
- the saved Manual Theme Seed is retained and continues to supply loading,
  missing-artwork, and failed-extraction fallback states.

The disabled row uses reduced opacity, a disabled accessibility state, and the
explicit instruction “Turn off Artwork color to edit”; it does not rely on color
alone.

## Brand and preset seeds

| Name | Value |
| --- | --- |
| Brand Pink | `#FF5B8A` |
| Purple | `#7A6CFF` |
| Blue | `#3D9AFF` |
| Orange | `#FF8A3D` |
| Green | `#3DCA8A` |
| Yellow | `#FFD93D` |

### Cross-platform Apple Music roles

The primary role and semantic button colors resolve consistently on mobile and
desktop. Available artwork colors take priority while Artwork color is on. With
Artwork color off, the current manual theme color supplies the primary role;
the default Brand Pink maps to Apple Music red, while custom colors remain
the selected colors.

| Role | Light | Dark |
| --- | --- | --- |
| Primary button | `#FA233B` with high-contrast content | `#FA2E48` with high-contrast content |
| Secondary button | `#ECECEC` with `#242424` content | `#404141` with `#E2E2E2` content |
| Secondary text | `#6E6E73` | `#98989D` |
| Tertiary/action text | `#8E8E93` | `#8E8E93` |

The resolved primary role colors selected navigation, switches, sliders, and
custom primary actions consistently across platforms. Text placed on a
background uses Miuix's generated high-contrast `onBackgroundVariant` role, so
bright artwork and manual seeds are not used directly as body text.

Default-brand secondary metadata remains neutral instead of inheriting a
red-tinted generated tone. Artwork and custom themes retain their generated
secondary roles.

NeriPlayer blue is not the TidePlayer default.

## Theme color component tokens

These values extend the existing 4/8/12/16/20/24/32/48 spacing system.

| Token | Value | Notes |
| --- | ---: | --- |
| Theme Color Transition | `400 ms` | Shared by design and Compose. |
| Color Swatch Size | `48 dp` | Visible swatch and primary action. |
| Color Swatch Touch Target | `48 dp` minimum | Never reduced on compact layouts. |
| Color Picker Dialog Maximum Width | `760 dp` | Expanded/Desktop; Medium uses `640 dp`. |
| Color Picker Compact Edge | `16 dp` | Modal is near-full-width. |
| Color Picker Content Maximum Height | `720 dp` or viewport minus `32 dp` | Content scrolls; header/actions remain visible. |
| HSV Indicator Size | `20 dp` | White inner ring + dark outer ring. |
| HSV Hue Slider Height | `32 dp` visual / `48 dp` target | Mouse, touch, and keyboard operable. |
| HSV Saturation/Value Height | `180 dp` | Never below `160 dp` on compact. |
| Preset Grid Gap | `12 dp` | Swatches wrap; no fixed device-only grid. |
| Dialog Section Gap | `20 dp` | Reuses section spacing. |
| Dialog Corner Radius | `28 dp` | Existing large Miuix overlay radius. |
| Custom Swatch Limit | `12` | Limit has explicit text; not color-only feedback. |

## Information architecture

```text
Settings
└── Appearance & language
    ├── Theme
    ├── Color
    │   ├── Artwork color
    │   └── Theme color
    └── Language
```

When Artwork color is off, Theme color opens an overlay rather than introducing
a deeper settings route. Compact uses a near-full-width modal/bottom-sheet
treatment. Medium and Expanded use a centered dialog consistent with existing
Miuix settings overlays.

## Theme color picker

The picker contains, in order:

- Current color and saved/draft status.
- Six TidePlayer presets.
- User-saved custom colors.
- Cross-platform HSV controls: two-dimensional Saturation/Value area and Hue
  slider.
- Hex input in `#RRGGBB` format.
- Light and dark theme previews.
- Add to palette, Cancel, and Apply color actions.

Required states are initial, dragging/unsaved, valid Hex, invalid Hex, duplicate
color, palette limit, empty custom palette, removable custom color, disabled
action, cancel, and applied success.

## Swatch component

Swatches are circular, 48 dp, and support default, hover, pressed, keyboard
focus, selected, removable, built-in/non-removable, and disabled states. Selected
swatches show a Check icon. The Check uses black or white according to the
swatch's relative luminance. A remove action sits outside the primary center hit
area and is exposed as a separate accessible control.

## Responsive layouts

### Compact / phone

- 16 dp viewport edge.
- Single-column picker and preview.
- Wrapping preset and custom swatches.
- Vertically stacked actions.
- Scrollable content with a minimum 160 dp Saturation/Value surface.

### Medium / tablet

- Centered dialog, maximum 640 dp.
- More swatches per row and horizontal actions.
- Picker and preview remain grouped and may flow sequentially.

### Expanded / Desktop

- Centered dialog, maximum 760 dp and bounded content height.
- Two columns: editing on the left, sticky preview on the right.
- Hover, pointer drag, Tab navigation, arrow-key adjustment, and visible focus.

All sizes use the same state model and reusable components.

## Light, dark, and contrast

The same seed produces separate light and dark tonal schemes through Miuix.
Required design checks cover Brand Pink, Yellow, and Blue in both modes.

- Foreground colors are selected by generated roles, not by seed brightness
  alone.
- The picker Check uses luminance-based black/white selection.
- The HSV indicator has two outlines and stays visible on white, black, and
  saturated colors.
- Focus, error, and selection do not rely on color alone.
- Theme generation must not replace error/diagnostic semantic colors.
- Body, secondary text, buttons, and selected surfaces target WCAG AA contrast.

## Accessibility

- All swatches expose the color name/value and selected state.
- HSV controls expose hue, saturation, and brightness values.
- Arrow keys adjust controls; Shift + arrow uses a larger step.
- Every interactive target is at least 48 dp where the component surface allows.
- Focus is visible on all keyboard-operable controls.
- Invalid Hex includes an icon and message and disables Apply.
- Selected swatches include a Check in addition to color.
- The disabled Theme color row exposes a disabled state and explains how to
  enable manual selection.

## Artwork loading and transitions

Artwork extraction is cross-platform and cached in memory by artwork identity.
Near-black/near-white noise and low-information colors should not dominate the
representative seed. While a new artwork loads, retain the previous valid artwork
seed; if none exists, use the manual seed. Missing or failed artwork immediately
uses the manual seed.

Effective seed changes animate for 400 ms. The saved manual seed still defines
all fallback states while Artwork color is enabled, but it is editable only
after Artwork color is turned off.

## Design-to-code mapping

| Design source | Compose contract |
| --- | --- |
| Artwork color switch | `artworkThemeEnabled` |
| Theme color row | `canSelectManualThemeColor` + `manualThemeSeedArgb` + picker dialog |
| Saved colors | `customThemeSeedArgbValues` |
| Seed resolution table | `resolveThemeSeed` |
| HSV surface / Hue slider | commonMain pointer + keyboard component |
| Theme previews | nested seed-generated Miuix preview themes |
| 400 ms transition | `DesignMotion.themeMillis` |
