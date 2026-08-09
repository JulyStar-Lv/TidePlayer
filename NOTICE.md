# TidePlayer notices

TidePlayer is a local-first private music collection player developed from an existing open-source codebase and extended substantially across Android, iOS, Desktop, Kotlin Multiplatform, Compose Multiplatform, Rust, and UniFFI.

## Primary license

Except where an individual file, directory, crate, dependency, or third-party notice states otherwise, source code distributed in this repository is provided under the GNU General Public License, version 3.0. See [`LICENSE.md`](./LICENSE.md) for the complete license text.

Copyright in TidePlayer modifications and new contributions remains with the respective contributors. This notice does not replace copyright notices embedded in source files or third-party license requirements.

## Upstream project

TidePlayer's migration baseline was populated from the `kmp` branch of:

- Project: **ease-music-player**
- Upstream repository: <https://github.com/hpp2334/ease-music-player>
- Baseline commit recorded by this repository: `897ce0747dce191070fcc91711b5369e04df903c`
- Upstream author/account: `hpp2334`

Portions derived from that upstream project retain their original copyright and licensing notices. TidePlayer's later refactors, rewrites, platform ports, new modules, and other modifications do not remove or supersede rights held by upstream copyright holders.

The historical migration record is preserved in [`docs/architecture/migration-baseline.md`](./docs/architecture/migration-baseline.md).

## Separately licensed repository modules

The following repository module declares licensing terms separate from the repository-wide default:

- [`rust-libs/order-key`](./rust-libs/order-key): `MIT OR Apache-2.0`, as declared by its `Cargo.toml`.

Copies of the MIT and Apache License 2.0 texts are available in [`license/`](./license/).

## Third-party work and dependencies

Third-party code, adapted implementations, libraries, datasets, assets, and dependencies remain subject to their own licenses and copyright notices. Repository-specific attributions currently requiring explicit documentation are collected in [`THIRD_PARTY_LICENSES.md`](./THIRD_PARTY_LICENSES.md).

Package-manager dependency metadata remains authoritative for ordinary unmodified dependencies when those dependencies are not vendored into this repository. `THIRD_PARTY_LICENSES.md` is therefore an attribution and audit index, not a substitute for the license metadata shipped by every transitive dependency.

## Product names

TidePlayer was previously developed or released under the names **MelodyTrove** and **TideTunes**. These historical product names do not create separate license grants and do not change the copyright or license status of code carried across the renames.

---

This notice is provided for attribution and repository organization. If a file-specific license or copyright notice conflicts with this summary, follow the file-specific notice and the applicable license terms.
