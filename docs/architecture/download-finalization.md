# Download finalization

Downloaded media and completed listen-and-cache files converge on one
cross-platform finalization path:

```text
explicit download ───────────────┐
                                 ├─> stable raw media
completed playback cache ─promote┘       ↓
                                    Finalizing
                                        ↓
                               resolved MetadataSnapshot
                                        ↓
                         Rust audio-metadata / Lofty writer
                                        ↓
                          re-read and audio-property verify
                                        ↓
                         atomic media and sidecar commit
                                        ↓
                         Room track/source refresh + publish
```

The download schedulers only transfer and durably commit the raw bytes. They
do not know how to edit ID3, Vorbis Comment, or MP4 tags. Kotlin resolves the
already selected metadata, artwork, and lyrics from Room, applies business
priority, and calls the shared Rust writer through UniFFI. The finalizer never
performs another plugin request.

## File states and recovery

- Playback cache `.part` and `.blocks` files are mutable and are never tagged.
- A complete playback cache is promoted only after its complete marker exists.
  Promotion keeps the original extension, uses rename when possible, and falls
  back to copy, fsync, and rename across filesystems. A stable identity-derived
  marker makes repeat promotion idempotent.
- Explicit downloads transition `Downloading -> Finalizing -> Completed`.
  `Finalizing` is persisted, so an interrupted metadata pass can be retried.
- The Rust writer edits `<media>.metadata.tmp`, re-reads it, verifies that the
  audio properties remain sane, flushes it, and atomically replaces the stable
  path. A failed enhancement leaves the already committed raw audio playable.
- Sidecars use the same temporary-write and atomic-replace pattern.

## Metadata policy

`MetadataSnapshot` records values and their source (`User`, `Embedded`,
`Database`, `Plugin`, or `Fallback`). Normal enrichment uses `FillMissing`, so
resolved database/plugin candidates cannot erase sensible embedded fields.
An explicitly locked user snapshot may use `PreferSnapshot`. Empty values are
never used to clear existing tags.

Artwork is limited to validated JPEG or PNG data up to 4 MiB and is written as
front cover only when the target format supports it. Unsupported fields and
artwork are reported as finalization warnings; they do not fail a completed
audio download.

## Lyrics policy

- Plain lyrics are embedded as compatible unsynchronized text.
- LRC stays available both for compatible embedded lyrics and as `.lrc`.
- Word-timed LRC is preserved verbatim rather than flattened.
- TTML is preserved verbatim as `.ttml`; a primary-line-only `.lrc` may also be
  generated for compatibility. Translation text is not promoted to the primary
  line.
- Empty lyrics do not create an empty sidecar.

The high-fidelity sidecar remains authoritative when a container only supports
plain embedded lyrics.

## Format capability

| Format | Metadata | Artwork | Embedded lyrics | Sidecar lyrics |
| --- | --- | --- | --- | --- |
| FLAC | Vorbis Comment fields | JPEG/PNG | Compatible text/LRC | LRC and TTML |
| MP3 | ID3v2 fields | APIC front cover | USLT | LRC and TTML |
| M4A/MP4 | MP4 item fields | JPEG/PNG cover | Compatible text | LRC and TTML |
| OGG Vorbis | Vorbis Comment fields | JPEG/PNG where supported | Compatible text/LRC | LRC and TTML |
| Opus | Vorbis Comment fields | JPEG/PNG where supported | Compatible text/LRC | LRC and TTML |
| WAV | RIFF/ID3 fields supported by Lofty | JPEG/PNG where supported | Compatible text where supported | LRC and TTML |

Capability gaps become warnings. Unsupported containers are rejected before a
temporary copy can replace the original.
