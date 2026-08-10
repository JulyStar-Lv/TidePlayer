# Unified audio technical metadata

Audio quality data crosses provider boundaries in normalized units:

- `bitrateKbps`: kilobits per second
- `sampleRateHz`: hertz
- `bitDepth`: bits per sample
- `channels`: channel count

File-based sources keep using Rust `audio-metadata`/Lofty. Media-server adapters map list-response metadata without issuing one probe request per track:

```text
Local / WebDAV / SMB / OneDrive -> Rust metadata
Navidrome / OpenSubsonic       -> SubsonicAudioPropertiesMapper
Emby                            -> EmbyAudioPropertiesMapper
                                      |
                                      v
                            SourceAudioProperties
                                      |
                                      v
                             TrackSourceRefEntity
                                      |
                                      v
                              AudioTechnicalInfo
                                      |
                                      v
                               PlaybackAudioInfo
                                      |
                                      v
                   CurrentTrackInfo -> NowPlayingState
```

`PlaybackAudioInfo.source` describes the selected original media source. `effective` describes the stream that the player actually receives after negotiation or transcoding. Presentation uses `effective` first and falls back to `source`. Direct file playback and current Emby `static=true` playback set `effective` to the selected source properties; Subsonic delivery remains unknown until playback negotiation is observable.

`TrackEntity` remains canonical/fallback metadata. `TrackSourceRefEntity` owns source-specific technical fields. When a source field is missing, playback mapping falls back field by field to the canonical track. `AudioTechnicalInfoFormatter` is the only quality-string formatter used by Now Playing and the playback source selector.

Emby selects an explicitly identified media source when present, then a default source, then the first source. Its media-source ID is retained in the encoded playback target so direct-play URLs can include `MediaSourceId`. Additional media-source selection UI and runtime transcoding negotiation can be added without changing the domain models.

Synology Audio Station, Jellyfin, Plex, and future servers should add a provider adapter that produces `SourceAudioProperties`; they must not introduce provider JSON or provider-specific audio models into playback or presentation.
