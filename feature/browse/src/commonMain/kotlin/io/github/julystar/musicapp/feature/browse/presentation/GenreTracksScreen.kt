package io.github.julystar.musicapp.feature.browse.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.components.StatusMessageCard
import io.github.julystar.musicapp.core.presentation.components.TrackNumberBadge
import io.github.julystar.musicapp.core.presentation.components.LocalDesignBottomContentInset
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import musicapp.feature.browse.generated.resources.Res
import musicapp.feature.browse.generated.resources.genre_download
import musicapp.feature.browse.generated.resources.genre_loading
import musicapp.feature.browse.generated.resources.genre_no_tracks
import musicapp.feature.browse.generated.resources.genre_play_all
import musicapp.feature.browse.generated.resources.genre_retry
import musicapp.feature.browse.generated.resources.genre_track_count
import musicapp.feature.browse.generated.resources.genre_unavailable
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun GenreTracksScreen(
    state: GenreTracksState,
    onAction: (GenreTracksAction) -> Unit,
) {
    val spacing = DesignTokens.spacing
    val bottomContentInset = LocalDesignBottomContentInset.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val horizontalPadding = if (maxWidth < 600.dp) spacing.pageCompact else spacing.pageExpanded

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
                .padding(horizontal = horizontalPadding, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            TopAppBar(
                title = state.genre,
                subtitle = stringResource(Res.string.genre_track_count, state.tracks.size),
                actions = {
                    if (!state.isLoading && state.tracks.isNotEmpty()) {
                        TextButton(
                            text = stringResource(Res.string.genre_play_all),
                            onClick = { onAction(GenreTracksAction.PlayAll) },
                        )
                    }
                },
            )
            when {
                state.isLoading -> StatusMessageCard(
                    title = stringResource(Res.string.genre_loading),
                    message = state.genre,
                    loading = true,
                    modifier = Modifier.weight(1f),
                )
                state.error != null -> StatusMessageCard(
                    title = stringResource(Res.string.genre_unavailable),
                    message = state.genre,
                    actionText = stringResource(Res.string.genre_retry),
                    onAction = { onAction(GenreTracksAction.Retry) },
                    modifier = Modifier.weight(1f),
                )
                state.tracks.isEmpty() -> StatusMessageCard(
                    title = stringResource(Res.string.genre_no_tracks),
                    message = state.genre,
                    modifier = Modifier.weight(1f),
                )
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = spacing.xl + bottomContentInset),
                    ) {
                        itemsIndexed(
                            state.tracks,
                            key = { index, track -> track.lazyListKey(index) },
                        ) { _, track ->
                            GenreTrackRow(
                                track = track,
                                onPlay = { onAction(GenreTracksAction.PlayTrack(track.id)) },
                                onDownload = {
                                    if (track.canDownload) {
                                        onAction(GenreTracksAction.DownloadTrack(track))
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreTrackRow(
    track: GenreTrackItem,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onPlay,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TrackNumberBadge(label = "")
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = track.title,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                track.artist?.let {
                    Text(
                        text = it,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            track.durationMs?.let { ms ->
                Text(
                    text = durationLabel(ms),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            if (track.canDownload) {
                TextButton(
                    text = stringResource(Res.string.genre_download),
                    onClick = onDownload,
                )
            }
        }
    }
}

internal fun GenreTrackItem.lazyListKey(index: Int): String = "genre-track-$index-$id"

private fun durationLabel(durationMs: Long): String {
    val h = durationMs / 1000 / 60 / 60
    val m = durationMs / 1000 / 60 % 60
    val s = durationMs / 1000 % 60
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}
