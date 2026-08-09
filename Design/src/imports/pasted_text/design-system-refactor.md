Refactor and visually optimize the existing TidePlayer Design System / app prototype in this Figma Make project so it matches the CURRENT production Compose Multiplatform repository. Do not create a separate concept and do not only explain. Edit the existing project files, keep all current routes working, and finish with a compiling preview.

Product direction
- Keep the existing TidePlayer identity and the HyperOS / compose-miuix feel: calm, layered, rounded, music-first, dark-mode-first. Avoid generic dashboard styling and avoid direct iOS imitation.
- Preserve the four root tabs only: Home, Search, Library, Settings. Now Playing must open only from the persistent mini player; never add a Playing tab.
- Reduce decorative noise: use gradients only for hero/artwork/selected brand moments, use semantic surfaces for normal cards, and strengthen hierarchy through spacing and typography rather than excessive glow.

Production tokens to implement exactly
- UI font: Noto Sans SC variable for all UI copy, weights 400/500/600/700. Technical badges/data only: JetBrains Mono. Remove Plus Jakarta Sans and Inter from the active design system and update every typography specimen/label accordingly.
- Type scale: Title1 28/34 Bold; Title2 22/28 SemiBold; Title3 16/22 Medium; Title4 14/20 Medium; Body1 14/20 Regular; Body2 13/18 Regular; Button 14/20 SemiBold; Footnote1 12/16 Regular; Footnote2 10/14 SemiBold. Zero letter spacing except uppercase section labels, which use 1.2px.
- Brand: pink #FF5B8A, purple #7A6CFF; support blue #3D9AFF, orange #FF8A3D, green #3DCA8A, yellow #FFD93D.
- Dark semantic colors: background/surface #0C0A14, surfaceContainer #161224, surfaceContainerHigh and surfaceVariant #1E1A30, onBackground/onSurface #F0EDF8, secondary text #9B97B0, disabled #6B6880, selected/disabled deep tone #3A3555. Default the prototype to dark mode.
- Light semantic colors: background/surface #F4F2FA, surfaceContainer #FFFFFF, surfaceContainerHigh/surfaceVariant #EAE7F5, foreground #0D0B18, secondary text #6B6880, disabled #9B97B0/#C5C2D8.
- Spacing: 0,4,8,12,16,24,32,48; page paddings Compact 16, Medium 20, Expanded 24. Radius: 0,4,8,12,20,28,36,40,full. Blur: 0,8,16,32,48. Motion durations: 100,180,280,380,500ms; theme 240ms and player expand 380ms.

Responsive shell must mirror production
- Compact <600: bottom navigation, 64px nav area; floating mini player above it with 12px horizontal margin and 4px vertical gap.
- Medium 600–839: 72px navigation rail.
- Expanded 840–1279: 80px navigation rail.
- Large 1280–1599 and XL >=1600: 224px sidebar, 48px top toolbar, optional 288px Lyrics/Queue right panel. Desktop search field max width 384px.
- Use active nav treatment as a subtle pink/purple tint plus pink icon/text; avoid the current oversized selected glow.

Align reusable component specs
- DesignButton variants Primary, Secondary, Tertiary, Ghost, Danger; minimum height 40, 16x8 padding, full pill radius, with disabled states.
- DesignCardSurface: 28 radius, 16 padding, 1px semantic outline, flat semantic surface by default.
- DesignSearchBar: 48 minimum height, 16 radius, 16 horizontal padding, 16px search icon, correct empty/filled/disabled states.
- Tabs: Line, Pill, Segmented; 44px control height; 3px line indicator; segmented container with 4px inset.
- Settings group: 24 radius; preference rows use 16px horizontal / 14px vertical padding, restrained dividers, uppercase group labels.
- Add clear interactive state specimens for default, pressed/selected, disabled, loading and error where relevant. Keep touch targets at least 40–48px.

Update the key prototype screens, using the production information architecture
1. Home: compact/mobile header “Good Evening”; 208px hero with 32 radius; sections Continue Listening, Recently Added, Recommended Artists, Pinned Playlists, Recently Played. Use 160px primary album cards and 120px secondary cards. Keep hero copy readable with a dark bottom overlay.
2. Search: 48px DesignSearchBar and explicit Idle, Typing, Searching, Results, Empty and Error presentations. Discovery contains Recent Searches chips, a two-column Browse Genres grid with 80px / 24-radius cards, and Trending Now. Result rows must show source/quality badges and download action without looking crowded.
3. Library: a horizontally scrollable pill tab strip with exactly Songs, Albums, Artists, Genres, Folders, Playlists, Favorites, Downloads, History, Recently Added, Recently Played, Lossless, Hi-Res, Sources. Use list rows for Songs and responsive 2-column grids below 640, 3 columns at 640+ for visual categories. Include realistic empty states.
4. Settings: make the landing page match the repository instead of exposing every control at once. The landing entries are Appearance, Playback, Sources, Metadata plugins, Network & cache, Storage, About; each opens grouped settings pages. Use 24-radius grouped cards and concise summaries.
5. Now Playing: remove any standalone navigation entry. Compact layout uses centered artwork up to 300px, title/artist, progress, and five controls. At width >=860 and height >=520 use a two-column 46/54 layout: artwork/metadata/controls left and synced lyrics right. Background uses artwork-derived darkMuted → muted → vibrant → surface gradient with a 74% background veil; artwork radius 36 and restrained shadow.

Design System documentation
- Update Foundation, Tokens, Components, Patterns and Compose pages so examples and Compose mappings use the exact specs above and real names from the repository (DesignButton, DesignCardSurface, DesignSearchBar, DesignTabs, DesignPageHeader, DesignSettingsGroup, DesignPreferenceRow, DesignIconButton, DesignPlayerControlButton).
- Add a compact “Code alignment” note showing the source-of-truth module paths under core/presentation, feature/*, shared/widgets/appbar and service/playback/presentation.
- Keep the current cover and brand story, but update version/date copy to “Repository-aligned · v4 · 2026”.

Quality gate
- Use structured React components instead of duplicating markup.
- Preserve dark/light toggle and all page navigation.
- Verify no clipped text, horizontal overflow, inaccessible low-contrast text, broken mobile layout, or runtime/build error.
- When finished, leave the preview on the optimized Home screen and summarize the actual files changed.
