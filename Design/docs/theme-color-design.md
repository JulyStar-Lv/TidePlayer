# Theme color design delivery

## Background and decision

The former Appearance design described platform/system wallpaper colors. That
model was Android-specific and did not define useful behavior on iOS or Desktop.
TidePlayer now has one cross-platform model: current artwork may provide the
active seed, while the saved manual seed is always the fallback.

## User flow

```text
Appearance → Theme color
  → available only after Artwork color is turned off
  → picker opens with persisted color
  → preset / HSV / Hex edits update local preview
  → optional Add to palette
  → Apply color persists and closes

Cancel → discard draft and keep persisted color
```

Artwork color and manual selection are mutually exclusive. While Artwork color
is on, Theme color remains visible as disabled fallback status. Turning Artwork
color off enables the picker without losing the saved manual color or custom
palette.

## Maintained source and exports

- Live Appearance design: `Design/src/app/App.tsx`
- Reusable design module and state boards:
  `Design/src/app/ThemeColorDesign.tsx`
- Product tokens and behavior: `Design/docs/TidePlayer-PDS-v3.md`
- Built prototype export: `Design/dist/`
- Visual exports: `Design/exports/theme-color/`

The Theme Colors design-system page contains the live Appearance surface, artwork
state matrix, swatch states, responsive contract, six light/dark seed checks, and
picker validation states.

## Removed legacy elements

- Dynamic color (system-wallpaper meaning)
- System wallpaper color
- Monet palette
- Android 12+ requirement
- Device does not support dynamic color
- Default-brand palette shown only as an inactive fallback

## State transition notes

- Loading retains the last valid artwork seed to avoid flashing.
- Missing/failed artwork uses the Manual Theme Seed.
- Turning artwork color off uses the Manual Theme Seed for every artwork state.
- Theme color cannot open while artwork color is on; turning it off enables
  manual selection.
- Deleting the custom swatch that matches the current Manual Theme Seed removes
  only the favorite entry.
- Draft HSV changes never write persistence.

## Acceptance evidence

Design build, responsive screenshots, pointer/validation checks, Compose tests,
Desktop runtime evidence, and design-versus-implementation findings are recorded
in `Design/design-qa.md` and `docs/design/theme-color-implementation.md`.
