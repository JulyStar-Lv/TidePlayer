package io.github.julystar.musicapp.service.playback.presentation.nowplaying

import io.github.julystar.musicapp.core.domain.model.LyricLine
import io.github.julystar.musicapp.core.domain.model.LyricDisplaySettings
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import io.github.julystar.musicapp.core.lyrics.ui.filterVisibleLyrics as filterSharedVisibleLyrics
import io.github.julystar.musicapp.core.lyrics.ui.toSyncedLyrics as toSharedSyncedLyrics

/** Converts TidePlayer timestamped lyric lines into the accompanist lyrics-core timeline model. */
fun List<LyricLine>.toSyncedLyrics(
    trackTitle: String,
    trackDurationMs: Long?,
    settings: LyricDisplaySettings = LyricDisplaySettings.Default,
): SyncedLyrics {
    return this.toSharedSyncedLyrics(
        trackTitle = trackTitle,
        trackDurationMs = trackDurationMs,
        settings = settings,
    )
}

internal fun List<LyricLine>.filterVisibleLyrics(settings: LyricDisplaySettings): List<LyricLine> =
    this.filterSharedVisibleLyrics(settings)
