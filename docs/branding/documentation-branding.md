# Documentation branding rules

Current product name: **TidePlayer**.

Repository documentation should follow these rules:

- Use `TidePlayer` for the current application, current UI, current architecture, current build products, current diagnostics, current plugin host behavior, and current design-system specifications.
- Use `README.md` as the default Simplified Chinese project introduction.
- Use `README.en.md` as the secondary English project introduction.
- Keep stable technical identifiers unchanged where they are part of the implementation contract, including `io.github.julystar.musicapp`, `library.db`, `settings.preferences_pb`, `app-backend`, `app_backend`, and `uniffi.app_backend`.
- Keep `MelodyTrove` and `TideTunes` only when documenting historical names, legacy compatibility, migration inputs, old data/backup discovery, old deep-link schemes, historical test/type names, or URLs that have not yet been renamed externally.
- The GitHub repository is `JulyStar-Lv/TidePlayer`; current repository links must use that slug.
- The original Figma URL currently contains `Design-System-for-MelodyTrove`; preserve that URL until the external Figma project is renamed.
- Historical archives such as `docs/architecture/migration-baseline.md` and dated design/work logs preserve the terminology that was true at the time. Do not rewrite historical evidence merely to remove an old brand string.

The intended compatibility chain is:

```text
TideTunes -> MelodyTrove -> TidePlayer
```

New user-facing documentation and new integrations should emit only TidePlayer-era identifiers, including the primary `tideplayer://` deep-link/OAuth scheme.
