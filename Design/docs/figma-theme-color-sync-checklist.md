# Figma manual sync checklist — Theme colors

The external Figma identifier remains `X30WgdPxOW9skIgTUCgk4b`; URL identifiers
are not product copy and must not be renamed mechanically. The current Codex
environment could open the target URL but did not expose an editable Figma
document, so this checklist is the authoritative manual handoff until the Figma
source is updated.

## Pages and frames

- Rename visible project/page titles to **TidePlayer**.
- Appearance / Compact / Light — Artwork color On.
- Appearance / Compact / Dark — Artwork color Off.
- Appearance / Medium / Light.
- Appearance / Expanded / Dark.
- Picker / Compact / Brand Pink.
- Picker / Medium / Yellow.
- Picker / Expanded / Blue.
- Artwork states: Available, Loading, Missing, Failed, Off.
- Interlock states: Artwork color On + disabled Theme color row; Artwork color
  Off + enabled Theme color row.
- Seed contrast: Pink/Yellow/Blue × Light/Dark.
- Picker validation: initial, dragging, invalid Hex, duplicate, limit, empty
  palette, deleted active favorite, disabled, applied.

## Components

- `Settings/Artwork color row`
- `Settings/Theme color row`
  variants: Artwork-on/Disabled and Artwork-off/Enabled.
- `Color/Swatch` variants: Default, Hover, Pressed, Focused, Selected, Removable,
  Built-in, Disabled.
- `Color/HSV saturation-value`
- `Color/Hue slider`
- `Color/Hex field` variants: Default, Focused, Error, Disabled.
- `Color/Theme preview` variants: Light and Dark.
- `Dialog/Theme color picker` variants: Compact, Medium, Expanded.

## Exact copy

- Artwork color
- Adjust app colors from the current song artwork
- Theme color
- Turn off Artwork color to edit
- Currently using `#FF5B8A`
- Loading artwork · keeping the last valid seed
- No artwork · using your saved color
- Artwork color failed · using your saved color
- Choose theme color
- Current color
- Preset colors
- Saved colors
- Custom color · HSV
- Color value
- Add to palette
- Apply color
- Already in palette
- Palette limit reached
- No saved custom colors yet

The Theme color row must not open the picker while Artwork color is on. Its
disabled state must remain readable and must include an accessibility disabled
state rather than relying only on reduced opacity.

Do not include System dynamic color, system wallpaper color, Monet, Android 12+,
or unsupported-device copy.

## Dimensions

- Swatch: 48 × 48 dp.
- Minimum target: 48 × 48 dp.
- Compact edge: 16 dp.
- Medium dialog max: 640 dp.
- Expanded dialog max: 760 dp.
- Content max height: 720 dp or viewport minus 32 dp.
- Saturation/Value: 180 dp high, 160 dp compact minimum.
- Hue visual height: 32 dp; target 48 dp.
- Indicator: 20 dp, white inner + dark outer ring.
- Preset gap: 12 dp.
- Section gap: 20 dp.
- Dialog radius: 28 dp.

## Color and motion tokens

- Brand Pink `#FF5B8A`
- Purple `#7A6CFF`
- Blue `#3D9AFF`
- Orange `#FF8A3D`
- Green `#3DCA8A`
- Yellow `#FFD93D`
- Error remains semantic `#EF4444`
- Theme Color Transition `400 ms`

## Prototype reference

Run `pnpm --dir Design build && pnpm --dir Design preview`, open Design System →
Theme Colors, and compare each Figma frame to the corresponding live board.
