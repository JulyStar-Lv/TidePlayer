package io.github.julystar.musicapp.feature.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.components.DesignIconBadge
import io.github.julystar.musicapp.core.presentation.components.DesignStatusCard
import io.github.julystar.musicapp.core.presentation.components.DesignStickyGlassActionBar
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.presentation.media.ArtworkImage
import io.github.julystar.musicapp.core.presentation.components.LocalDesignBottomContentInset
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.julystar.musicapp.feature.home.domain.ListeningDistributionBucket
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import musicapp.core.presentation.generated.resources.Res as CoreRes
import musicapp.core.presentation.generated.resources.icon_album
import musicapp.core.presentation.generated.resources.icon_mode_repeat
import musicapp.core.presentation.generated.resources.icon_music_note
import musicapp.core.presentation.generated.resources.icon_settings_activity
import musicapp.core.presentation.generated.resources.icon_settings_circle_play
import musicapp.core.presentation.generated.resources.icon_settings_mic
import musicapp.core.presentation.generated.resources.icon_settings_sliders
import musicapp.core.presentation.generated.resources.icon_timelapse
import musicapp.feature.home.generated.resources.Res
import musicapp.feature.home.generated.resources.listening_active_days
import musicapp.feature.home.generated.resources.listening_active_days_ratio
import musicapp.feature.home.generated.resources.listening_activity
import musicapp.feature.home.generated.resources.listening_activity_caption
import musicapp.feature.home.generated.resources.listening_all_time
import musicapp.feature.home.generated.resources.listening_average_day
import musicapp.feature.home.generated.resources.listening_calendar
import musicapp.feature.home.generated.resources.listening_calendar_caption
import musicapp.feature.home.generated.resources.listening_day_count
import musicapp.feature.home.generated.resources.listening_favorite_album
import musicapp.feature.home.generated.resources.listening_favorite_artist
import musicapp.feature.home.generated.resources.listening_favorite_track
import musicapp.feature.home.generated.resources.listening_favorites
import musicapp.feature.home.generated.resources.listening_formats
import musicapp.feature.home.generated.resources.listening_four_weeks_ago
import musicapp.feature.home.generated.resources.listening_history
import musicapp.feature.home.generated.resources.listening_history_empty
import musicapp.feature.home.generated.resources.listening_heatmap_less
import musicapp.feature.home.generated.resources.listening_heatmap_more
import musicapp.feature.home.generated.resources.listening_late_night
import musicapp.feature.home.generated.resources.listening_morning
import musicapp.feature.home.generated.resources.listening_afternoon
import musicapp.feature.home.generated.resources.listening_evening
import musicapp.feature.home.generated.resources.listening_month_headline
import musicapp.feature.home.generated.resources.listening_month_summary
import musicapp.feature.home.generated.resources.listening_monthly_activity
import musicapp.feature.home.generated.resources.listening_no_data
import musicapp.feature.home.generated.resources.listening_overview
import musicapp.feature.home.generated.resources.listening_peak_time
import musicapp.feature.home.generated.resources.listening_play_count
import musicapp.feature.home.generated.resources.listening_plays
import musicapp.feature.home.generated.resources.listening_quality
import musicapp.feature.home.generated.resources.listening_rankings
import musicapp.feature.home.generated.resources.listening_rank_by_plays
import musicapp.feature.home.generated.resources.listening_rank_by_time
import musicapp.feature.home.generated.resources.listening_streak
import musicapp.feature.home.generated.resources.listening_time
import musicapp.feature.home.generated.resources.listening_title
import musicapp.feature.home.generated.resources.listening_top_tracks
import musicapp.feature.home.generated.resources.listening_today
import musicapp.feature.home.generated.resources.listening_unique_tracks
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.time.Instant

@Composable
fun ListeningScreen(
    state: ListeningState,
    onAction: (ListeningAction) -> Unit,
) {
    val bottomInset = LocalDesignBottomContentInset.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background),
    ) {
        val pagePadding = if (maxWidth < 600.dp) {
            DesignTokens.spacing.pageCompact
        } else {
            DesignTokens.spacing.pageExpanded
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = DesignTokens.adaptive.contentMaxWidth)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(
                start = pagePadding,
                top = DesignTokens.adaptive.compactHeaderHeight + DesignTokens.spacing.xs,
                end = pagePadding,
                bottom = bottomInset + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(
                if (state.selectedTab == ListeningTab.Calendar) 10.dp else 20.dp,
            ),
        ) {
            item {
                TabRow(
                    tabs = listOf(
                        stringResource(Res.string.listening_overview),
                        stringResource(Res.string.listening_calendar),
                        stringResource(Res.string.listening_rankings),
                    ),
                    selectedTabIndex = state.selectedTab.ordinal,
                    onTabSelected = { index ->
                        onAction(ListeningAction.SelectTab(ListeningTab.entries[index]))
                    },
                )
            }
            if (state.isLoading) {
                item {
                    DesignStatusCard(
                        title = stringResource(Res.string.listening_title),
                        message = stringResource(Res.string.listening_no_data),
                        loading = true,
                    )
                }
            } else {
                when (state.selectedTab) {
                    ListeningTab.Overview -> overviewItems(state)
                    ListeningTab.Calendar -> calendarItems(state, onAction)
                    ListeningTab.Rankings -> rankingItems(state, onAction)
                }
            }
        }
        DesignStickyGlassActionBar(
            title = stringResource(Res.string.listening_title),
            collapseFraction = 1f,
            onNavigateBack = { onAction(ListeningAction.NavigateBack) },
            backContentDescription = stringResource(Res.string.listening_title),
            compactTitle = true,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = DesignTokens.adaptive.contentMaxWidth),
        )
    }
}

private fun LazyListScope.overviewItems(state: ListeningState) {
    item {
        MonthlyListeningReport(state)
    }
    item {
        ListeningHabitGrid(state)
    }
    item {
        OverviewInsightGrid(state)
    }
    item {
        LibraryAnalysisGrid(state)
    }
}

private fun LazyListScope.calendarItems(
    state: ListeningState,
    onAction: (ListeningAction) -> Unit,
) {
    item {
        ListeningCalendarSection(days = state.calendarDays)
    }
    item { SectionTitle(stringResource(Res.string.listening_history)) }
    if (state.recentHistory.isEmpty()) {
        item {
            DesignStatusCard(
                title = stringResource(Res.string.listening_history),
                message = stringResource(Res.string.listening_history_empty),
            )
        }
    } else {
        items(state.recentHistory, key = ListeningHistoryItem::id) { item ->
            HistoryRow(
                item = item,
                onPlay = { onAction(ListeningAction.PlayTrack(item.trackId)) },
            )
        }
    }
}

private fun LazyListScope.rankingItems(
    state: ListeningState,
    onAction: (ListeningAction) -> Unit,
) {
    item {
        ListeningRankingCard(state = state, onAction = onAction)
    }
}

@Composable
private fun MonthlyListeningReport(state: ListeningState) {
    val peakLabel = state.peakTimePeriod?.localizedLabel()
        ?: stringResource(Res.string.listening_no_data)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = state.monthLabel,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(Res.string.listening_month_headline),
                        style = MiuixTheme.textStyles.title1,
                        color = MiuixTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (state.monthPlayCount == 0) {
                            stringResource(Res.string.listening_no_data)
                        } else {
                            stringResource(
                                Res.string.listening_month_summary,
                                state.activeDays,
                                peakLabel,
                            )
                        },
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                DesignIconBadge(
                    icon = painterResource(CoreRes.drawable.icon_settings_activity),
                    accentColor = MiuixTheme.colorScheme.primary,
                )
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth >= 720.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MonthlyMetric(
                            value = formatListeningDuration(state.monthListenedMs),
                            label = stringResource(Res.string.listening_time),
                            modifier = Modifier.weight(1f),
                        )
                        MonthlyMetric(
                            value = state.monthPlayCount.toString(),
                            label = stringResource(Res.string.listening_play_count),
                            modifier = Modifier.weight(1f),
                        )
                        MonthlyMetric(
                            value = stringResource(Res.string.listening_day_count, state.activeDays),
                            label = stringResource(Res.string.listening_active_days),
                            modifier = Modifier.weight(1f),
                        )
                        MonthlyMetric(
                            value = state.monthUniqueTrackCount.toString(),
                            label = stringResource(Res.string.listening_unique_tracks),
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            MonthlyMetric(
                                value = formatListeningDuration(state.monthListenedMs),
                                label = stringResource(Res.string.listening_time),
                                modifier = Modifier.weight(1f),
                            )
                            MonthlyMetric(
                                value = state.monthPlayCount.toString(),
                                label = stringResource(Res.string.listening_play_count),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            MonthlyMetric(
                                value = stringResource(Res.string.listening_day_count, state.activeDays),
                                label = stringResource(Res.string.listening_active_days),
                                modifier = Modifier.weight(1f),
                            )
                            MonthlyMetric(
                                value = state.monthUniqueTrackCount.toString(),
                                label = stringResource(Res.string.listening_unique_tracks),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = MiuixTheme.textStyles.title3,
            color = MiuixTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ListeningHabitGrid(state: ListeningState) {
    val metrics = listOf(
        Triple(
            stringResource(Res.string.listening_peak_time),
            state.peakTimePeriod?.localizedLabel() ?: stringResource(Res.string.listening_no_data),
            CoreRes.drawable.icon_timelapse,
        ),
        Triple(
            stringResource(Res.string.listening_streak),
            stringResource(Res.string.listening_day_count, state.longestStreakDays),
            CoreRes.drawable.icon_mode_repeat,
        ),
        Triple(
            stringResource(Res.string.listening_average_day),
            formatListeningDuration(state.averagePerActiveDayMs),
            CoreRes.drawable.icon_settings_circle_play,
        ),
        Triple(
            stringResource(Res.string.listening_monthly_activity),
            stringResource(
                Res.string.listening_active_days_ratio,
                state.activeDays,
                state.elapsedDaysInMonth,
            ),
            CoreRes.drawable.icon_settings_activity,
        ),
    )
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= 720.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                metrics.forEach { (label, value, icon) ->
                    HabitMetric(
                        label = label,
                        value = value,
                        icon = icon,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                metrics.chunked(2).forEach { rowMetrics ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rowMetrics.forEach { (label, value, icon) ->
                            HabitMetric(
                                label = label,
                                value = value,
                                icon = icon,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewInsightGrid(state: ListeningState) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= 720.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                MonthlyFavoritesCard(
                    state = state,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                ListeningActivityCard(
                    days = state.calendarDays.takeLast(28),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MonthlyFavoritesCard(state)
                ListeningActivityCard(state.calendarDays.takeLast(28))
            }
        }
    }
}

@Composable
private fun MonthlyFavoritesCard(state: ListeningState, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ListeningCardHeader(
                title = stringResource(Res.string.listening_favorites),
                icon = CoreRes.drawable.icon_music_note,
            )
            InsightRow(
                label = stringResource(Res.string.listening_favorite_track),
                insight = state.favoriteTrack,
                icon = CoreRes.drawable.icon_music_note,
            )
            InsightRow(
                label = stringResource(Res.string.listening_favorite_artist),
                insight = state.favoriteArtist,
                icon = CoreRes.drawable.icon_settings_mic,
            )
            InsightRow(
                label = stringResource(Res.string.listening_favorite_album),
                insight = state.favoriteAlbum,
                icon = CoreRes.drawable.icon_album,
            )
        }
    }
}

@Composable
private fun ListeningActivityCard(days: List<ListeningDay>, modifier: Modifier = Modifier) {
    val visibleDays = days.takeLast(28)
    val maxListenedMs = visibleDays.maxOfOrNull(ListeningDay::listenedMs)?.coerceAtLeast(1L) ?: 1L
    Card(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ListeningCardHeader(
                title = stringResource(Res.string.listening_activity),
                subtitle = stringResource(Res.string.listening_activity_caption),
                icon = CoreRes.drawable.icon_settings_activity,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                visibleDays.chunked(7).forEach { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { day ->
                            val intensity = day.listenedMs.toFloat() / maxListenedMs.toFloat()
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        if (day.listenedMs == 0L) {
                                            MiuixTheme.colorScheme.surfaceContainerHigh
                                        } else {
                                            MiuixTheme.colorScheme.primary.copy(
                                                alpha = 0.20f + intensity * 0.80f,
                                            )
                                        },
                                    ),
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(Res.string.listening_four_weeks_ago),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = listOfNotNull(
                        stringResource(Res.string.listening_today),
                        visibleDays.lastOrNull()?.let { formatListeningDuration(it.listenedMs) },
                    ).joinToString(" · "),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun LibraryAnalysisGrid(state: ListeningState) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= 720.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                DistributionCard(
                    title = stringResource(Res.string.listening_formats),
                    buckets = state.formatDistribution,
                    icon = CoreRes.drawable.icon_music_note,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                DistributionCard(
                    title = stringResource(Res.string.listening_quality),
                    buckets = state.qualityDistribution,
                    icon = CoreRes.drawable.icon_settings_sliders,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DistributionCard(
                    title = stringResource(Res.string.listening_formats),
                    buckets = state.formatDistribution,
                    icon = CoreRes.drawable.icon_music_note,
                )
                DistributionCard(
                    title = stringResource(Res.string.listening_quality),
                    buckets = state.qualityDistribution,
                    icon = CoreRes.drawable.icon_settings_sliders,
                )
            }
        }
    }
}

@Composable
private fun HabitMetric(
    label: String,
    value: String,
    icon: DrawableResource,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DesignIconBadge(
                modifier = Modifier.size(36.dp),
                icon = painterResource(icon),
                accentColor = MiuixTheme.colorScheme.primary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = value,
                    style = MiuixTheme.textStyles.title2,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = label,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private enum class ListeningRankingMetric {
    Time,
    Plays,
}

@Composable
private fun ListeningRankingCard(
    state: ListeningState,
    onAction: (ListeningAction) -> Unit,
) {
    var metric by remember { mutableStateOf(ListeningRankingMetric.Time) }
    val tracks = when (metric) {
        ListeningRankingMetric.Time -> state.durationRanking
        ListeningRankingMetric.Plays -> state.playCountRanking
    }
    val maxValue = tracks.maxOfOrNull { track ->
        when (metric) {
            ListeningRankingMetric.Time -> track.listenedMs
            ListeningRankingMetric.Plays -> track.playCount.toLong()
        }
    }?.coerceAtLeast(1L) ?: 1L

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val tabs: @Composable (Modifier) -> Unit = { modifier ->
                    TabRow(
                        tabs = listOf(
                            stringResource(Res.string.listening_rank_by_time),
                            stringResource(Res.string.listening_rank_by_plays),
                        ),
                        selectedTabIndex = metric.ordinal,
                        onTabSelected = { index ->
                            metric = ListeningRankingMetric.entries[index]
                        },
                        modifier = modifier,
                    )
                }
                val heading: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(Res.string.listening_top_tracks),
                            style = MiuixTheme.textStyles.title2,
                            color = MiuixTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(Res.string.listening_all_time),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                if (maxWidth >= 620.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        heading()
                        tabs(Modifier.width(280.dp))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        heading()
                        tabs(Modifier.fillMaxWidth())
                    }
                }
            }
            if (tracks.isEmpty()) {
                Text(
                    text = stringResource(Res.string.listening_no_data),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            } else {
                tracks.forEachIndexed { index, track ->
                    RankingRow(
                        rank = index + 1,
                        track = track,
                        value = when (metric) {
                            ListeningRankingMetric.Time -> formatListeningDuration(track.listenedMs)
                            ListeningRankingMetric.Plays ->
                                stringResource(Res.string.listening_plays, track.playCount)
                        },
                        progress = when (metric) {
                            ListeningRankingMetric.Time -> track.listenedMs.toFloat() / maxValue.toFloat()
                            ListeningRankingMetric.Plays -> track.playCount.toFloat() / maxValue.toFloat()
                        },
                        onPlay = { onAction(ListeningAction.PlayTrack(track.trackId)) },
                    )
                    if (index != tracks.lastIndex) {
                        ListeningDivider(modifier = Modifier.padding(start = 96.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.title2,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onBackground,
        )
        subtitle?.let {
            Text(
                text = it,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onBackgroundVariant,
            )
        }
    }
}

@Composable
private fun ListeningCardHeader(
    title: String,
    icon: DrawableResource,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.title3,
                color = MiuixTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        DesignIconBadge(
            modifier = Modifier.size(36.dp),
            icon = painterResource(icon),
            accentColor = MiuixTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun InsightRow(
    label: String,
    insight: ListeningInsight?,
    icon: DrawableResource,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DesignIconBadge(
            modifier = Modifier.size(34.dp),
            icon = painterResource(icon),
            accentColor = MiuixTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = insight?.title ?: stringResource(Res.string.listening_no_data),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        insight?.let {
            Text(
                text = stringResource(Res.string.listening_plays, it.playCount),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun DistributionCard(
    title: String,
    buckets: List<ListeningDistributionBucket>,
    icon: DrawableResource,
    modifier: Modifier = Modifier,
) {
    val total = buckets.sumOf(ListeningDistributionBucket::trackCount).coerceAtLeast(1)
    Card(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ListeningCardHeader(title = title, icon = icon)
            if (buckets.isEmpty()) {
                Text(
                    stringResource(Res.string.listening_no_data),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            } else {
                buckets.take(6).forEach { bucket ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(bucket.label, color = MiuixTheme.colorScheme.onSurface)
                            Text(
                                bucket.trackCount.toString(),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(MiuixTheme.colorScheme.surfaceContainerHigh),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(bucket.trackCount.toFloat() / total)
                                    .height(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                MiuixTheme.colorScheme.primary,
                                                MiuixTheme.colorScheme.secondary,
                                            ),
                                        ),
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListeningCalendarSection(days: List<ListeningDay>) {
    var selectedDate by remember(days) { mutableStateOf(days.lastOrNull()?.date) }
    val selectedDay = days.firstOrNull { it.date == selectedDate }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= 840.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CalendarHeatmapCard(
                    days = days,
                    selectedDate = selectedDate,
                    onSelectedDateChange = { selectedDate = it },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                SelectedListeningDayCard(
                    day = selectedDay,
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxHeight(),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CalendarHeatmapCard(
                    days = days,
                    selectedDate = selectedDate,
                    onSelectedDateChange = { selectedDate = it },
                )
                SelectedListeningDayCard(day = selectedDay)
            }
        }
    }
}

@Composable
private fun CalendarHeatmapCard(
    days: List<ListeningDay>,
    selectedDate: kotlinx.datetime.LocalDate?,
    onSelectedDateChange: (kotlinx.datetime.LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxListenedMs = days.maxOfOrNull(ListeningDay::listenedMs)?.coerceAtLeast(1L) ?: 1L
    Card(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            ListeningCardHeader(
                title = stringResource(Res.string.listening_calendar),
                subtitle = stringResource(Res.string.listening_calendar_caption),
                icon = CoreRes.drawable.icon_settings_activity,
            )
            if (days.isEmpty()) {
                Text(
                    text = stringResource(Res.string.listening_no_data),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    days.chunked(7).forEach { week ->
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            week.forEach { day ->
                                val intensity = day.listenedMs.toFloat() / maxListenedMs
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(
                                            if (day.listenedMs == 0L) {
                                                MiuixTheme.colorScheme.surfaceContainerHigh
                                            } else {
                                                MiuixTheme.colorScheme.primary.copy(
                                                    alpha = 0.18f + intensity * 0.70f,
                                                )
                                            },
                                        )
                                        .then(
                                            if (day.date == selectedDate) {
                                                Modifier.border(
                                                    width = 2.dp,
                                                    color = MiuixTheme.colorScheme.primary,
                                                    shape = RoundedCornerShape(5.dp),
                                                )
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .clickable { onSelectedDateChange(day.date) },
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = days.first().date.toString(),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    ListeningHeatmapLegend()
                    Text(
                        text = stringResource(Res.string.listening_today),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ListeningHeatmapLegend() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(Res.string.listening_heatmap_less),
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(Modifier.width(5.dp))
        listOf(0.16f, 0.36f, 0.58f, 0.82f).forEach { level ->
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = level)),
            )
            Spacer(Modifier.width(3.dp))
        }
        Text(
            text = stringResource(Res.string.listening_heatmap_more),
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun SelectedListeningDayCard(
    day: ListeningDay?,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        if (day == null) {
            Text(
                text = stringResource(Res.string.listening_no_data),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = day.date.toString(),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = formatListeningDuration(day.listenedMs),
                    style = MiuixTheme.textStyles.title1,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    text = stringResource(Res.string.listening_time),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                ListeningDivider(modifier = Modifier.padding(vertical = 14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DesignIconBadge(
                        modifier = Modifier.size(36.dp),
                        icon = painterResource(CoreRes.drawable.icon_settings_circle_play),
                        accentColor = MiuixTheme.colorScheme.primary,
                    )
                    Column {
                        Text(
                            text = day.playCount.toString(),
                            style = MiuixTheme.textStyles.title3,
                            color = MiuixTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(Res.string.listening_play_count),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                if (day.listenedMs == 0L) {
                    Text(
                        text = stringResource(Res.string.listening_no_data),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    item: ListeningHistoryItem,
    onPlay: () -> Unit,
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
            ArtworkImage(
                artwork = Artwork.LibraryTrack(item.trackId, allowPluginLookup = true),
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp)),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(item.artist, formatListeningTimestamp(item.playedAtEpochMs))
                        .joinToString(" · "),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formatListeningDuration(item.listenedMs),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ListeningDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MiuixTheme.colorScheme.outline.copy(alpha = 0.48f)),
    )
}

@Composable
private fun RankingRow(
    rank: Int,
    track: ListeningRankedTrack,
    value: String,
    progress: Float,
    onPlay: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (rank <= 3) {
                    MiuixTheme.colorScheme.primary.copy(alpha = 0.035f)
                } else {
                    Color.Transparent
                },
            )
            .clickable(onClick = onPlay)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (rank <= 3) {
                        MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                    } else {
                        MiuixTheme.colorScheme.surfaceContainerHigh
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = rank.toString(),
                color = if (rank <= 3) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.Bold,
            )
        }
        ArtworkImage(
            artwork = Artwork.LibraryTrack(track.trackId, allowPluginLookup = true),
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp)),
        )
        Column(Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = track.title,
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = value,
                    color = MiuixTheme.colorScheme.onSurface,
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            val subtitle = listOfNotNull(track.artist, track.album)
                .filter(String::isNotBlank)
                .joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(7.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MiuixTheme.colorScheme.primary,
                                    MiuixTheme.colorScheme.secondary,
                                ),
                            ),
                        ),
                )
            }
        }
    }
}

@Composable
private fun ListeningTimePeriod.localizedLabel(): String = stringResource(
    when (this) {
        ListeningTimePeriod.LateNight -> Res.string.listening_late_night
        ListeningTimePeriod.Morning -> Res.string.listening_morning
        ListeningTimePeriod.Afternoon -> Res.string.listening_afternoon
        ListeningTimePeriod.Evening -> Res.string.listening_evening
    },
)

private fun formatListeningDuration(totalMs: Long): String {
    val totalMinutes = (totalMs / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun formatListeningTimestamp(epochMs: Long): String {
    val value = Instant.fromEpochMilliseconds(epochMs)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return buildString {
        append(value.date)
        append(' ')
        append(value.hour.toString().padStart(2, '0'))
        append(':')
        append(value.minute.toString().padStart(2, '0'))
    }
}
