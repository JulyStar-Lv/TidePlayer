package io.github.julystar.musicapp.plugin.management

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.components.DesignDialog
import io.github.julystar.musicapp.core.presentation.components.DesignLoadingIndicator
import io.github.julystar.musicapp.core.presentation.components.DesignSearchBar
import io.github.julystar.musicapp.core.presentation.components.DesignStatusBadge
import io.github.julystar.musicapp.core.presentation.components.DesignStatusTone
import io.github.julystar.musicapp.core.presentation.components.DesignTextButton
import io.github.julystar.musicapp.core.presentation.components.DesignTextButtonSize
import io.github.julystar.musicapp.core.presentation.components.DesignTextButtonVariant
import io.github.julystar.musicapp.core.presentation.theme.DesignPalette
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.julystar.musicapp.service.playback.presentation.nowplaying.NowPlayingTrackItem
import io.github.julystar.musicapp.source.api.MetaSongCandidate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import musicapp.shared.generated.resources.Res
import musicapp.shared.generated.resources.manual_metadata_applied_without_lyrics
import musicapp.shared.generated.resources.manual_metadata_apply
import musicapp.shared.generated.resources.manual_metadata_apply_failed
import musicapp.shared.generated.resources.manual_metadata_applying
import musicapp.shared.generated.resources.manual_metadata_current_track
import musicapp.shared.generated.resources.manual_metadata_keyword
import musicapp.shared.generated.resources.manual_metadata_no_matches
import musicapp.shared.generated.resources.manual_metadata_no_sources
import musicapp.shared.generated.resources.manual_metadata_partial_failure
import musicapp.shared.generated.resources.manual_metadata_reset
import musicapp.shared.generated.resources.manual_metadata_reset_failed
import musicapp.shared.generated.resources.manual_metadata_resetting
import musicapp.shared.generated.resources.manual_metadata_results
import musicapp.shared.generated.resources.manual_metadata_results_title
import musicapp.shared.generated.resources.manual_metadata_search
import musicapp.shared.generated.resources.manual_metadata_search_failed
import musicapp.shared.generated.resources.manual_metadata_searching
import musicapp.shared.generated.resources.manual_metadata_source
import musicapp.shared.generated.resources.manual_metadata_summary
import musicapp.shared.generated.resources.manual_metadata_title
import musicapp.shared.generated.resources.manual_metadata_unknown_artist

private sealed interface ManualMetadataFeedback {
    data class SearchCompleted(
        val resultCount: Int,
        val failedSourceCount: Int,
        val queriedSourceCount: Int,
    ) : ManualMetadataFeedback

    data class AppliedWithoutLyrics(val title: String) : ManualMetadataFeedback
    data object SearchFailed : ManualMetadataFeedback
    data object ApplyFailed : ManualMetadataFeedback
    data object ResetFailed : ManualMetadataFeedback
}

@Composable
fun ManualMetadataSearchDialog(
    track: NowPlayingTrackItem?,
    onDismiss: () -> Unit,
    service: ManualMetadataService = koinInject(),
) {
    val dialogVisible = track != null
    var retainedTrack by remember { mutableStateOf(track) }
    SideEffect {
        if (track != null) retainedTrack = track
    }
    val activeTrack = track ?: retainedTrack ?: return
    val scope = rememberCoroutineScope()
    var keyword by remember(activeTrack.id) {
        mutableStateOf(defaultManualMetadataKeyword(activeTrack))
    }
    var candidates by remember(activeTrack.id) { mutableStateOf(emptyList<MetaSongCandidate>()) }
    var selected by remember(activeTrack.id) { mutableStateOf<MetaSongCandidate?>(null) }
    var feedback by remember(activeTrack.id) { mutableStateOf<ManualMetadataFeedback?>(null) }
    var searching by remember(activeTrack.id) { mutableStateOf(false) }
    var applying by remember(activeTrack.id) { mutableStateOf(false) }
    var resetting by remember(activeTrack.id) { mutableStateOf(false) }

    fun search() {
        if (searching || applying || resetting || keyword.isBlank()) return
        scope.launch {
            searching = true
            candidates = emptyList()
            selected = null
            feedback = null
            try {
                val result = service.search(activeTrack, keyword)
                candidates = result.items
                selected = result.items.firstOrNull()
                feedback = ManualMetadataFeedback.SearchCompleted(
                    resultCount = result.items.size,
                    failedSourceCount = result.failures.size,
                    queriedSourceCount = result.queriedSourceCount,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                candidates = emptyList()
                feedback = ManualMetadataFeedback.SearchFailed
            } finally {
                searching = false
            }
        }
    }

    fun applySelected() {
        val candidate = selected ?: return
        if (searching || applying || resetting) return
        scope.launch {
            applying = true
            feedback = null
            try {
                val lyricFailures = service.apply(activeTrack.id, candidate)
                if (lyricFailures.isEmpty()) {
                    onDismiss()
                } else {
                    feedback = ManualMetadataFeedback.AppliedWithoutLyrics(candidate.title)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                feedback = ManualMetadataFeedback.ApplyFailed
            } finally {
                applying = false
            }
        }
    }

    fun resetFromFile() {
        if (searching || applying || resetting) return
        scope.launch {
            resetting = true
            feedback = null
            try {
                service.resetFromFile(activeTrack.id)
                onDismiss()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                feedback = ManualMetadataFeedback.ResetFailed
            } finally {
                resetting = false
            }
        }
    }

    LaunchedEffect(activeTrack.id, dialogVisible) {
        if (dialogVisible) {
            keyword = defaultManualMetadataKeyword(activeTrack)
            candidates = emptyList()
            selected = null
            feedback = null
            searching = false
            applying = false
            resetting = false
            search()
        } else {
            scope.coroutineContext.cancelChildren()
            searching = false
            applying = false
            resetting = false
        }
    }

    DesignDialog(
        show = dialogVisible,
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(Res.string.manual_metadata_title),
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(Res.string.manual_metadata_summary),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MiuixTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(DesignTokens.shapes.md),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = stringResource(Res.string.manual_metadata_current_track),
                    style = MiuixTheme.textStyles.footnote2,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.primary,
                )
                Text(
                    text = activeTrack.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                activeTrack.artist?.takeIf(String::isNotBlank)?.let { artist ->
                    Text(
                        text = artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DesignSearchBar(
                    value = keyword,
                    onValueChange = { keyword = it },
                    placeholder = stringResource(Res.string.manual_metadata_keyword),
                    onSearch = ::search,
                    onClear = { keyword = "" },
                    enabled = !searching && !applying && !resetting,
                    modifier = Modifier.weight(1f),
                )
                DesignTextButton(
                    text = stringResource(Res.string.manual_metadata_search),
                    variant = DesignTextButtonVariant.PrimaryFilled,
                    size = DesignTextButtonSize.Medium,
                    modifier = Modifier.widthIn(min = 72.dp),
                    enabled = keyword.isNotBlank() && !searching && !applying && !resetting,
                    onClick = ::search,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.manual_metadata_results_title),
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (candidates.isNotEmpty()) {
                    DesignStatusBadge(
                        label = stringResource(
                            Res.string.manual_metadata_results,
                            candidates.size,
                        ),
                        tone = DesignStatusTone.Accent,
                    )
                }
            }
            MetadataResults(
                candidates = candidates,
                selected = selected,
                feedback = feedback,
                loading = searching || (feedback == null && candidates.isEmpty()),
                enabled = !applying && !resetting,
                onSelect = { selected = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            feedback
                ?.takeIf { candidates.isNotEmpty() && it.shouldShowAlongsideResults() }
                ?.let { value ->
                    MetadataFeedbackMessage(feedback = value)
                }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DesignTextButton(
                    text = if (resetting) {
                        stringResource(Res.string.manual_metadata_resetting)
                    } else {
                        stringResource(Res.string.manual_metadata_reset)
                    },
                    variant = DesignTextButtonVariant.Tonal,
                    size = DesignTextButtonSize.Medium,
                    modifier = Modifier.widthIn(min = 120.dp),
                    enabled = !searching && !applying && !resetting,
                    onClick = ::resetFromFile,
                )
                DesignTextButton(
                    text = if (applying) {
                        stringResource(Res.string.manual_metadata_applying)
                    } else {
                        stringResource(Res.string.manual_metadata_apply)
                    },
                    variant = DesignTextButtonVariant.PrimaryFilled,
                    size = DesignTextButtonSize.Medium,
                    modifier = Modifier.widthIn(min = 88.dp),
                    enabled = selected != null && !searching && !applying && !resetting,
                    onClick = ::applySelected,
                )
            }
        }
    }
}

@Composable
private fun MetadataResults(
    candidates: List<MetaSongCandidate>,
    selected: MetaSongCandidate?,
    feedback: ManualMetadataFeedback?,
    loading: Boolean,
    enabled: Boolean,
    onSelect: (MetaSongCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        loading -> MetadataSearchState(
            text = stringResource(Res.string.manual_metadata_searching),
            loading = true,
            modifier = modifier,
        )
        candidates.isEmpty() -> MetadataSearchState(
            text = feedback?.let { manualMetadataFeedbackText(it) }
                ?: stringResource(Res.string.manual_metadata_no_matches),
            error = feedback?.isError() == true,
            modifier = modifier,
        )
        else -> LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = candidates,
                key = { candidate -> "${candidate.sourceId}:${candidate.id}" },
            ) { candidate ->
                MetadataCandidateRow(
                    candidate = candidate,
                    selected = candidate == selected,
                    enabled = enabled,
                    onClick = { onSelect(candidate) },
                )
            }
        }
    }
}

@Composable
private fun MetadataSearchState(
    text: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    error: Boolean = false,
) {
    Box(
        modifier = modifier
            .heightIn(min = 132.dp)
            .clip(RoundedCornerShape(DesignTokens.shapes.md))
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (loading) {
                DesignLoadingIndicator(size = 24.dp, strokeWidth = 2.dp)
            }
            Text(
                text = text,
                style = MiuixTheme.textStyles.body2,
                color = if (error) {
                    MiuixTheme.colorScheme.error
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
            )
        }
    }
}

@Composable
private fun MetadataFeedbackMessage(feedback: ManualMetadataFeedback) {
    val accent = when {
        feedback.isError() -> MiuixTheme.colorScheme.error
        feedback is ManualMetadataFeedback.AppliedWithoutLyrics -> DesignPalette.SupportOrange
        feedback is ManualMetadataFeedback.SearchCompleted && feedback.failedSourceCount > 0 ->
            DesignPalette.SupportOrange
        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DesignTokens.shapes.sm))
            .background(accent.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Text(
            text = manualMetadataFeedbackText(feedback),
            modifier = Modifier.weight(1f),
            style = MiuixTheme.textStyles.footnote1,
            color = accent,
        )
    }
}

private fun ManualMetadataFeedback.shouldShowAlongsideResults(): Boolean = when (this) {
    is ManualMetadataFeedback.SearchCompleted -> failedSourceCount > 0
    else -> true
}

private fun ManualMetadataFeedback.isError(): Boolean = when (this) {
    is ManualMetadataFeedback.SearchCompleted ->
        queriedSourceCount == 0 || (resultCount == 0 && failedSourceCount > 0)
    ManualMetadataFeedback.SearchFailed,
    ManualMetadataFeedback.ApplyFailed,
    ManualMetadataFeedback.ResetFailed -> true
    is ManualMetadataFeedback.AppliedWithoutLyrics -> false
}

private fun defaultManualMetadataKeyword(track: NowPlayingTrackItem): String =
    listOfNotNull(
        track.title.trim().takeIf(String::isNotEmpty),
        track.artist?.trim()?.takeIf(String::isNotEmpty),
    ).joinToString(" ")

@Composable
private fun manualMetadataFeedbackText(feedback: ManualMetadataFeedback): String = when (feedback) {
    is ManualMetadataFeedback.SearchCompleted -> when {
        feedback.queriedSourceCount == 0 ->
            stringResource(Res.string.manual_metadata_no_sources)
        feedback.resultCount == 0 && feedback.failedSourceCount > 0 ->
            stringResource(Res.string.manual_metadata_search_failed)
        feedback.resultCount == 0 ->
            stringResource(Res.string.manual_metadata_no_matches)
        feedback.failedSourceCount > 0 ->
            stringResource(
                Res.string.manual_metadata_partial_failure,
                feedback.resultCount,
                feedback.failedSourceCount,
            )
        else -> stringResource(Res.string.manual_metadata_results, feedback.resultCount)
    }
    is ManualMetadataFeedback.AppliedWithoutLyrics -> stringResource(
        Res.string.manual_metadata_applied_without_lyrics,
        feedback.title,
    )
    ManualMetadataFeedback.SearchFailed ->
        stringResource(Res.string.manual_metadata_search_failed)
    ManualMetadataFeedback.ApplyFailed ->
        stringResource(Res.string.manual_metadata_apply_failed)
    ManualMetadataFeedback.ResetFailed ->
        stringResource(Res.string.manual_metadata_reset_failed)
}

internal fun metadataApplyMessage(
    title: String,
    lyricFailures: List<MetadataLookupFailure>,
): String = if (lyricFailures.isEmpty()) {
    "Applied metadata for $title."
} else {
    "Applied metadata for $title. Lyrics were unavailable from the selected source."
}

@Composable
private fun MetadataCandidateRow(
    candidate: MetaSongCandidate,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(DesignTokens.shapes.md)
    val source = candidate.sourceId?.let { sourceId ->
        stringResource(Res.string.manual_metadata_source, metadataSourceDisplayName(sourceId))
    }
    val details = listOfNotNull(
        candidate.date?.trim()?.takeIf(String::isNotEmpty),
        candidate.durationMs?.let(::formatMetadataDuration),
    ).joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.55f)
            .clip(shape)
            .background(
                color = if (selected) {
                    MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    MiuixTheme.colorScheme.surfaceContainerHigh
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    MiuixTheme.colorScheme.primary.copy(alpha = 0.55f)
                } else {
                    MiuixTheme.colorScheme.outline.copy(alpha = 0f)
                },
                shape = shape,
            )
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = candidate.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = listOfNotNull(candidate.artist, candidate.album).joinToString(" · ")
                    .ifBlank { stringResource(Res.string.manual_metadata_unknown_artist) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            if (source != null || details.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    source?.let {
                        Text(
                            text = it,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                    if (source != null && details.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (details.isNotEmpty()) {
                        Text(
                            text = details,
                            maxLines = 1,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (selected) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary),
                )
            }
        }
    }
}

internal fun metadataSourceDisplayName(sourceId: String): String = sourceId

private fun formatMetadataDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
