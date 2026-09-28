package io.github.julystar.musicapp.plugin.management

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.julystar.musicapp.core.presentation.components.StatusBadge
import io.github.julystar.musicapp.core.presentation.components.StatusTone
import io.github.julystar.musicapp.core.presentation.theme.DesignPalette
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.julystar.musicapp.core.presentation.platform.isDesktopPlatform
import io.github.julystar.musicapp.platform.byteArrayToImageBitmap
import io.github.julystar.musicapp.service.playback.presentation.nowplaying.NowPlayingTrackItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.theme.MiuixTheme
import musicapp.shared.generated.resources.manual_metadata_lyrics_checking
import musicapp.shared.generated.resources.manual_metadata_lyrics_failed
import musicapp.shared.generated.resources.manual_metadata_lyrics_none
import musicapp.shared.generated.resources.manual_metadata_lyrics_ttml
import musicapp.shared.generated.resources.manual_metadata_lyrics_word
import musicapp.shared.generated.resources.manual_metadata_lyrics_line
import musicapp.shared.generated.resources.manual_metadata_lyrics_text
import musicapp.shared.generated.resources.Res
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
    var candidates by remember(activeTrack.id) { mutableStateOf(emptyList<ManualMetadataResult>()) }
    var selected by remember(activeTrack.id) { mutableStateOf<ManualMetadataResult?>(null) }
    var selectionChangedByUser by remember(activeTrack.id) { mutableStateOf(false) }
    var feedback by remember(activeTrack.id) { mutableStateOf<ManualMetadataFeedback?>(null) }
    var searching by remember(activeTrack.id) { mutableStateOf(false) }
    var applying by remember(activeTrack.id) { mutableStateOf(false) }
    var resetting by remember(activeTrack.id) { mutableStateOf(false) }
    var searchJob by remember(activeTrack.id) { mutableStateOf<Job?>(null) }
    val coverPreviews = remember(service, activeTrack.id) {
        ManualMetadataCoverPreviewCache(scope) { candidate ->
            service.loadCoverPreview(candidate)?.let(::byteArrayToImageBitmap)
        }
    }

    val lyricsPreviews = remember(service, activeTrack.id) {
        ManualMetadataLyricsPreviewCache(scope, service::loadLyricsPreview)
    }

    val rankedCandidates = rankManualMetadataResultsByLyrics(candidates, lyricsPreviews::preview)
    LaunchedEffect(rankedCandidates, selectionChangedByUser, applying, resetting) {
        if (!selectionChangedByUser && !applying && !resetting) {
            selected = rankedCandidates.firstOrNull()
        }
    }

    fun search() {
        if (searching || applying || resetting || keyword.isBlank()) return
        searching = true
        searchJob = scope.launch {
            candidates = emptyList()
            selected = null
            selectionChangedByUser = false
            feedback = null
            try {
                val result = service.search(activeTrack, keyword) { partial ->
                    candidates = partial
                    if (!selectionChangedByUser) {
                        selected = rankManualMetadataResultsByLyrics(partial, lyricsPreviews::preview).firstOrNull()
                    }
                }
                candidates = result.items
                selected = if (selectionChangedByUser) {
                    result.items.firstOrNull { it == selected } ?: result.items.firstOrNull()
                } else {
                    rankManualMetadataResultsByLyrics(result.items, lyricsPreviews::preview).firstOrNull()
                }
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
        val result = selected ?: return
        if (applying || resetting) return
        searchJob?.cancel()
        searching = false
        scope.launch {
            applying = true
            feedback = null
            try {
                val preview = lyricsPreviews.load(result.song)
                service.apply(
                    trackId = activeTrack.id,
                    result = result.copy(lyrics = preview.lyrics),
                    lyricsChecked = !preview.failed,
                )
                onDismiss()
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
            selectionChangedByUser = false
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

    OverlayDialog(
        show = dialogVisible,
        modifier = if (isDesktopPlatform()) {
            Modifier.heightIn(min = minOf(640.dp, LocalWindowInfo.current.containerDpSize.height * 0.9f))
        } else {
            Modifier
        },
        onDismissRequest = onDismiss,
        insideMargin = DpSize.Zero,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.manual_metadata_title),
                        modifier = Modifier.fillMaxWidth(),
                        style = MiuixTheme.textStyles.title2.copy(fontSize = 16.sp),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(Res.string.manual_metadata_summary),
                        textAlign = TextAlign.Center,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Text(
                    text = stringResource(
                        Res.string.manual_metadata_current_track,
                        activeTrack.title,
                        activeTrack.artist?.takeIf(String::isNotBlank)
                            ?: stringResource(Res.string.manual_metadata_unknown_artist),
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MiuixTheme.textStyles.body2.copy(fontSize = 13.sp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InputField(
                        query = keyword,
                        onQueryChange = { keyword = it },
                        onSearch = { search() },
                        label = stringResource(Res.string.manual_metadata_keyword),
                        expanded = false,
                        onExpandedChange = {},
                        enabled = !searching && !applying && !resetting,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(Res.string.manual_metadata_search),
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
                    if (searching && candidates.isNotEmpty()) {
                        CircularProgressIndicator(size = 16.dp, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (candidates.isNotEmpty()) {
                        StatusBadge(
                            label = stringResource(
                                Res.string.manual_metadata_results,
                                candidates.size,
                            ),
                            tone = StatusTone.Accent,
                        )
                    }
                }
                MetadataResults(
                    candidates = rankedCandidates,
                    selected = selected,
                    feedback = feedback,
                    loading = candidates.isEmpty() && (searching || feedback == null),
                    enabled = !applying && !resetting,
                    coverPreviews = coverPreviews,
                    lyricsPreviews = lyricsPreviews,
                    onSelect = {
                        selected = it
                        selectionChangedByUser = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                feedback
                    ?.takeIf { candidates.isNotEmpty() && it.shouldShowAlongsideResults() }
                    ?.let { value ->
                        MetadataFeedbackMessage(feedback = value)
                    }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    text = if (resetting) {
                        stringResource(Res.string.manual_metadata_resetting)
                    } else {
                        stringResource(Res.string.manual_metadata_reset)
                    },
                    cornerRadius = 16.dp,
                    minWidth = 64.dp,
                    minHeight = 32.dp,
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    textStyle = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                    colors = ButtonDefaults.textButtonColors(
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        textColor = MiuixTheme.colorScheme.onSurface,
                    ),
                    enabled = !searching && !applying && !resetting,
                    onClick = ::resetFromFile,
                )
                TextButton(
                    text = if (applying) {
                        stringResource(Res.string.manual_metadata_applying)
                    } else {
                        stringResource(Res.string.manual_metadata_apply)
                    },
                    cornerRadius = 16.dp,
                    minWidth = 64.dp,
                    minHeight = 32.dp,
                    insideMargin = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    textStyle = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                    colors = ButtonDefaults.textButtonColors(
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        disabledColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                        textColor = MiuixTheme.colorScheme.onSurface,
                        disabledTextColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.28f),
                    ),
                    enabled = selected != null && !applying && !resetting,
                    onClick = ::applySelected,
                )
            }
        }
    }
}

@Composable
private fun MetadataResults(
    candidates: List<ManualMetadataResult>,
    selected: ManualMetadataResult?,
    feedback: ManualMetadataFeedback?,
    loading: Boolean,
    enabled: Boolean,
    coverPreviews: ManualMetadataCoverPreviewCache,
    lyricsPreviews: ManualMetadataLyricsPreviewCache,
    onSelect: (ManualMetadataResult) -> Unit,
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
                key = { result -> "${result.song.sourceId}:${result.song.id}" },
            ) { result ->
                MetadataCandidateRow(
                    result = result,
                    selected = result == selected,
                    enabled = enabled,
                    coverPreviews = coverPreviews,
                    lyricsPreviews = lyricsPreviews,
                    onClick = { onSelect(result) },
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
                CircularProgressIndicator(size = 24.dp, strokeWidth = 2.dp)
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
    val compact = feedback is ManualMetadataFeedback.SearchCompleted
    val accent = when {
        feedback.isError() -> MiuixTheme.colorScheme.error
        feedback is ManualMetadataFeedback.SearchCompleted && feedback.failedSourceCount > 0 ->
            DesignPalette.SupportOrange
        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DesignTokens.shapes.sm))
            .background(accent.copy(alpha = if (compact) 0f else 0.10f))
            .padding(horizontal = if (compact) 0.dp else 12.dp, vertical = if (compact) 2.dp else 9.dp),
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
    ManualMetadataFeedback.SearchFailed ->
        stringResource(Res.string.manual_metadata_search_failed)
    ManualMetadataFeedback.ApplyFailed ->
        stringResource(Res.string.manual_metadata_apply_failed)
    ManualMetadataFeedback.ResetFailed ->
        stringResource(Res.string.manual_metadata_reset_failed)
}

@Composable
private fun MetadataCandidateRow(
    result: ManualMetadataResult,
    selected: Boolean,
    enabled: Boolean,
    coverPreviews: ManualMetadataCoverPreviewCache,
    lyricsPreviews: ManualMetadataLyricsPreviewCache,
    onClick: () -> Unit,
) {
    val candidate = result.song
    val shape = RoundedCornerShape(DesignTokens.shapes.md)
    val preview by produceState(coverPreviews.preview(candidate), coverPreviews, candidate.pictureUrl) {
        value = coverPreviews.load(candidate)
    }
    val lyricsPreview by produceState(lyricsPreviews.preview(candidate), lyricsPreviews, candidate) {
        value = lyricsPreviews.load(candidate)
    }
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
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(DesignTokens.shapes.sm))
                .background(MiuixTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            preview?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = candidate.title,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MiuixTheme.textStyles.body2.copy(fontSize = 13.sp, lineHeight = 16.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = lyricsPreviewLabel(lyricsPreview),
                    style = MiuixTheme.textStyles.footnote2.copy(fontSize = 10.sp, lineHeight = 12.sp),
                    color = if (lyricsPreview?.lyrics != null) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier
                        .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
            Text(
                text = listOfNotNull(candidate.artist, candidate.album).joinToString(" · ")
                    .ifBlank { stringResource(Res.string.manual_metadata_unknown_artist) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MiuixTheme.textStyles.footnote2.copy(lineHeight = 12.sp),
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
                            style = MiuixTheme.textStyles.footnote2.copy(lineHeight = 12.sp),

                        )
                    }
                    if (source != null && details.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (details.isNotEmpty()) {
                        Text(
                            text = details,
                            maxLines = 1,
                            style = MiuixTheme.textStyles.footnote2.copy(lineHeight = 12.sp),
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

@Composable
private fun lyricsPreviewLabel(preview: ManualMetadataLyricsPreview?): String = stringResource(
    when {
        preview == null -> Res.string.manual_metadata_lyrics_checking
        preview.failed -> Res.string.manual_metadata_lyrics_failed
        else -> when (preview.lyrics?.lyrics?.previewType()) {
            ManualMetadataLyricsType.TTML -> Res.string.manual_metadata_lyrics_ttml
            ManualMetadataLyricsType.WORD -> Res.string.manual_metadata_lyrics_word
            ManualMetadataLyricsType.LINE -> Res.string.manual_metadata_lyrics_line
            ManualMetadataLyricsType.TEXT -> Res.string.manual_metadata_lyrics_text
            null -> Res.string.manual_metadata_lyrics_none
        }
    },
)
