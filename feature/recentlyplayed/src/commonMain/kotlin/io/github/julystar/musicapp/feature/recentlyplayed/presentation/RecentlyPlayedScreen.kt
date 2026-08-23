package io.github.julystar.musicapp.feature.recentlyplayed.presentation

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
import io.github.julystar.musicapp.core.presentation.components.DesignStatusCard
import io.github.julystar.musicapp.core.presentation.components.LocalDesignBottomContentInset
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import musicapp.feature.recentlyplayed.generated.resources.Res
import musicapp.feature.recentlyplayed.generated.resources.recently_played_checking
import musicapp.feature.recentlyplayed.generated.resources.recently_played_download
import musicapp.feature.recentlyplayed.generated.resources.recently_played_empty
import musicapp.feature.recentlyplayed.generated.resources.recently_played_empty_message
import musicapp.feature.recentlyplayed.generated.resources.recently_played_loading
import musicapp.feature.recentlyplayed.generated.resources.recently_played_play_all
import musicapp.feature.recentlyplayed.generated.resources.recently_played_retry
import musicapp.feature.recentlyplayed.generated.resources.recently_played_title
import musicapp.feature.recentlyplayed.generated.resources.recently_played_track_count
import musicapp.feature.recentlyplayed.generated.resources.recently_played_unavailable
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun RecentlyPlayedScreen(
    state: RecentlyPlayedState,
    onAction: (RecentlyPlayedAction) -> Unit,
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
                title = stringResource(Res.string.recently_played_title),
                subtitle = stringResource(Res.string.recently_played_track_count, state.tracks.size),
                actions = {
                    if (!state.isLoading && state.tracks.isNotEmpty()) {
                        TextButton(
                            text = stringResource(Res.string.recently_played_play_all),
                            onClick = { onAction(RecentlyPlayedAction.PlayAll) },
                        )
                    }
                },
            )
            when {
                state.isLoading -> DesignStatusCard(
                    title = stringResource(Res.string.recently_played_loading),
                    message = stringResource(Res.string.recently_played_checking),
                    loading = true,
                    modifier = Modifier.weight(1f),
                )
                state.error != null -> DesignStatusCard(
                    title = stringResource(Res.string.recently_played_unavailable),
                    message = stringResource(Res.string.recently_played_checking),
                    actionText = stringResource(Res.string.recently_played_retry),
                    onAction = { onAction(RecentlyPlayedAction.Retry) },
                    modifier = Modifier.weight(1f),
                )
                state.tracks.isEmpty() -> DesignStatusCard(
                    title = stringResource(Res.string.recently_played_empty),
                    message = stringResource(Res.string.recently_played_empty_message),
                    modifier = Modifier.weight(1f),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = spacing.xl + bottomContentInset),
                ) {
                    itemsIndexed(
                        state.tracks,
                        key = { index, track -> track.lazyListKey(index) },
                    ) { _, track ->
                        TrackRow(
                            track = track,
                            onPlay = { onAction(RecentlyPlayedAction.PlayTrack(track.id)) },
                            onDownload = {
                                if (track.canDownload) onAction(RecentlyPlayedAction.DownloadTrack(track))
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: RecentlyPlayedTrackItem,
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
            track.durationMs?.let {
                Text(
                    text = durationLabel(it),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            if (track.canDownload) {
                TextButton(
                    text = stringResource(Res.string.recently_played_download),
                    onClick = onDownload,
                )
            }
        }
    }
}

internal fun RecentlyPlayedTrackItem.lazyListKey(index: Int): String = "recently-played-track-$index-$id"

private fun durationLabel(durationMs: Long): String {
    val h = durationMs / 1000 / 60 / 60
    val m = durationMs / 1000 / 60 % 60
    val s = durationMs / 1000 % 60
    return "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}
