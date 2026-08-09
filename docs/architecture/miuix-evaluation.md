# Miuix evaluation

Date: 2026-06-28

This note records the required source/API evaluation before adding Miuix to
TidePlayer. It intentionally does not add a dependency yet; adoption should go
through `core/presentation` wrappers so feature screens do not depend directly
on third-party UI APIs.

## Sources checked

- Official repository: https://github.com/compose-miuix-ui/miuix
- Official releases: https://github.com/compose-miuix-ui/miuix/releases
- Project version catalog: `gradle/libs.versions.toml`

## Current upstream facts

- Miuix is a Compose Multiplatform UI library and explicitly documents that the
  library is experimental and APIs may change without notice.
- The official README lists support for Android, iOS, macOS, Desktop, JsCanvas,
  and WasmJs.
- Current module names are:
  - `miuix-ui`: core UI component library.
  - `miuix-preference`: preference components, depends on `miuix-ui`.
  - `miuix-icons`: extended icon library.
  - `miuix-blur`: blur effects.
  - `miuix-squircle`: squircle shape helpers.
  - `miuix-navigation3-ui`: Navigation3 UI helpers.
  - `miuix-shader`: runtime shader/render-effect abstraction.
- The official README dependency coordinate for the core UI module is
  `top.yukonga.miuix.kmp:miuix-ui:<version>`.
- Release `v0.9.2` upgraded to Kotlin `2.4.0` and Compose Multiplatform
  `1.11.1`.
- Release `v0.9.0` introduced a breaking module restructure from old
  `miuix` to `miuix-ui` + `miuix-preference`. Old `Super*` components moved
  into preference or overlay component names.
- Release `v0.9.2` notes `miuix-blur` Android minSdk moved to API 33.

## TidePlayer compatibility

Current TidePlayer versions in `gradle/libs.versions.toml`:

- Kotlin: `2.4.0`
- Compose Multiplatform: `1.11.1`
- Android minSdk: `29`
- Android compileSdk: `36`

This matches Miuix `v0.9.2`'s Kotlin/Compose baseline, so the safest candidate
version is `0.9.2`.

The app minSdk is lower than the blur module's Android API 33 requirement, so
`miuix-blur` should not be added in the first integration batch. If blur is
needed later, it must remain behind a platform-capability wrapper with a
non-blur fallback and a manifest/minSdk check.

## Adoption decision

Proceed with staged adoption, but do not directly import Miuix from feature
screens.

Initial allowed dependency:

```kotlin
miuix = "0.9.2"
miuix-ui = { module = "top.yukonga.miuix.kmp:miuix-ui", version.ref = "miuix" }
```

Defer these modules:

- `miuix-preference`: only after Settings screens are moved behind
  app-owned preference row wrappers.
- `miuix-icons`: current app resources already define icons; avoid churn.
- `miuix-blur`: blocked by Android minSdk/API behavior review.
- `miuix-navigation3-ui`: current app uses typed Navigation Compose, not
  Navigation3.
- `miuix-shader` / `miuix-squircle`: only if required transitively or by a
  concrete app component wrapper.

## Integration rules

- Add dependencies only through `gradle/libs.versions.toml`.
- Add Miuix to `commonMain` only after a small wrapper compiles on Android,
  iOS Simulator, and Desktop.
- Keep Miuix imports inside `core/presentation` wrappers such as
  `AppButton`, `AppIconButton`, `AppNavigationBar`, `AppNavigationRail`,
  `AppSidebar`, `AppCard`, `AppDialog`, and `AppScaffold`.
- Feature screens should depend on TidePlayer wrapper APIs, not Miuix APIs.
- Keep current Material/App components until each wrapper is migrated and
  verified.
- Do not add `miuix-blur` until Android minSdk and fallback behavior are
  verified.

## First safe implementation slice

1. Add version catalog aliases for `miuix` and `miuix-ui`.
2. Add `libs.miuix.ui` to `shared` `commonMain`.
3. Create a single `core/presentation/theme/AppDesignTheme` or wrapper that
   can delegate to the existing theme until Miuix is enabled.
4. Migrate one low-risk wrapper, such as `CompatTextButton`, behind an
   app-owned interface or component name.
5. Run the full gate:

```bash
./gradlew :shared:desktopTest :shared:compileDebugKotlinAndroid :shared:compileKotlinIosSimulatorArm64 :desktopApp:compileKotlinDesktop --no-daemon --no-configuration-cache --console plain
```

Only after that should broader screen-level UI migration start.
