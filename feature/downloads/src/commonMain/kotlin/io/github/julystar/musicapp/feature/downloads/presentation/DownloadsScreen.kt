package io.github.julystar.musicapp.feature.downloads.presentation

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
import io.github.julystar.musicapp.core.presentation.components.DesignCardSurface
import io.github.julystar.musicapp.core.presentation.components.DesignEmptyState
import io.github.julystar.musicapp.core.presentation.components.DesignIconBadge
import io.github.julystar.musicapp.core.presentation.components.DesignIconBadgeVariant
import io.github.julystar.musicapp.core.presentation.components.DesignLinearProgressIndicator
import io.github.julystar.musicapp.core.presentation.components.DesignPageHeader
import io.github.julystar.musicapp.core.presentation.components.DesignStatusBadge
import io.github.julystar.musicapp.core.presentation.components.DesignStatusTone
import io.github.julystar.musicapp.core.presentation.components.DesignTextButton
import io.github.julystar.musicapp.core.presentation.components.DesignTextButtonSize
import io.github.julystar.musicapp.core.presentation.components.DesignTextButtonVariant
import io.github.julystar.musicapp.core.presentation.components.LocalDesignBottomContentInset
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.julystar.musicapp.service.download.domain.DownloadStatus
import musicapp.feature.downloads.generated.resources.Res
import musicapp.feature.downloads.generated.resources.downloads_cancel
import musicapp.feature.downloads.generated.resources.downloads_empty
import musicapp.feature.downloads.generated.resources.downloads_empty_message
import musicapp.feature.downloads.generated.resources.downloads_pause
import musicapp.feature.downloads.generated.resources.downloads_progress
import musicapp.feature.downloads.generated.resources.downloads_resume
import musicapp.feature.downloads.generated.resources.downloads_retry
import musicapp.feature.downloads.generated.resources.downloads_status_cancelled
import musicapp.feature.downloads.generated.resources.downloads_status_completed
import musicapp.feature.downloads.generated.resources.downloads_status_downloading
import musicapp.feature.downloads.generated.resources.downloads_status_failed
import musicapp.feature.downloads.generated.resources.downloads_status_finalizing
import musicapp.feature.downloads.generated.resources.downloads_status_paused
import musicapp.feature.downloads.generated.resources.downloads_status_queued
import musicapp.feature.downloads.generated.resources.downloads_status_resolving
import musicapp.feature.downloads.generated.resources.downloads_task_count
import musicapp.feature.downloads.generated.resources.downloads_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DownloadsScreen(
    state: DownloadsState,
    onAction: (DownloadsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = DesignTokens.spacing
    val bottomContentInset = LocalDesignBottomContentInset.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val horizontalPadding = if (maxWidth < 600.dp) spacing.pageCompact else spacing.pageExpanded

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
                .padding(horizontal = horizontalPadding, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            DesignPageHeader(
                title = stringResource(Res.string.downloads_title),
                subtitle = stringResource(Res.string.downloads_task_count, state.tasks.size),
            )
            if (state.tasks.isEmpty()) {
                DesignEmptyState(
                    title = stringResource(Res.string.downloads_empty),
                    message = stringResource(Res.string.downloads_empty_message),
                    modifier = Modifier.weight(1f),
                )
                return@Column
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = spacing.xl + bottomContentInset),
            ) {
                itemsIndexed(
                    state.tasks,
                    key = { index, task -> task.lazyListKey(index) },
                ) { _, task ->
                    DownloadTaskRow(task = task, onAction = onAction)
                }
            }
        }
    }
}

internal fun DownloadTaskUi.lazyListKey(index: Int): String = "download-task-$index-${id.value}"

@Composable
private fun DownloadTaskRow(
    task: DownloadTaskUi,
    onAction: (DownloadsAction) -> Unit,
) {
    DesignCardSurface(contentPadding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DesignIconBadge(variant = DesignIconBadgeVariant.Neutral, marker = "D")
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        color = MiuixTheme.colorScheme.onSurface,
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = task.subtitle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                DesignStatusBadge(
                    label = stringResource(task.status.resource),
                    tone = task.status.statusTone,
                )
            }
            task.progressFraction?.let { progress ->
                DesignLinearProgressIndicator(progress = progress)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.progressText(),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    task.errorMessage?.let { message ->
                        Text(
                            text = message,
                            color = MiuixTheme.colorScheme.error,
                            style = MiuixTheme.textStyles.footnote1,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    task.warningMessage?.let { message ->
                        Text(
                            text = message,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.footnote1,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                DownloadTaskActions(task = task, onAction = onAction)
            }
        }
    }
}

@Composable
private fun DownloadTaskUi.progressText(): String {
    val statusText = stringResource(status.resource)
    val percent = progressPercent
    val total = totalBytes
    return if (percent != null && total != null && total > 0L) {
        stringResource(
            Res.string.downloads_progress,
            percent,
            formatByteCount(downloadedBytes),
            formatByteCount(total),
        )
    } else if (downloadedBytes > 0L) {
        formatByteCount(downloadedBytes)
    } else {
        statusText
    }
}

@Composable
private fun DownloadTaskActions(
    task: DownloadTaskUi,
    onAction: (DownloadsAction) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (task.canPause) {
            DesignTextButton(
                text = stringResource(Res.string.downloads_pause),
                variant = DesignTextButtonVariant.Primary,
                size = DesignTextButtonSize.Small,
                onClick = { onAction(DownloadsAction.Pause(task.id)) },
            )
        }
        if (task.canResume) {
            DesignTextButton(
                text = stringResource(Res.string.downloads_resume),
                variant = DesignTextButtonVariant.Primary,
                size = DesignTextButtonSize.Small,
                onClick = { onAction(DownloadsAction.Resume(task.id)) },
            )
        }
        if (task.canRetry) {
            DesignTextButton(
                text = stringResource(Res.string.downloads_retry),
                variant = DesignTextButtonVariant.Primary,
                size = DesignTextButtonSize.Small,
                onClick = { onAction(DownloadsAction.Retry(task.id)) },
            )
        }
        if (task.canCancel) {
            DesignTextButton(
                text = stringResource(Res.string.downloads_cancel),
                variant = DesignTextButtonVariant.Error,
                size = DesignTextButtonSize.Small,
                onClick = { onAction(DownloadsAction.Cancel(task.id)) },
            )
        }
    }
}

private val DownloadStatus.resource: StringResource
    get() = when (this) {
        DownloadStatus.Queued -> Res.string.downloads_status_queued
        DownloadStatus.Resolving -> Res.string.downloads_status_resolving
        DownloadStatus.Downloading -> Res.string.downloads_status_downloading
        DownloadStatus.Finalizing -> Res.string.downloads_status_finalizing
        DownloadStatus.Paused -> Res.string.downloads_status_paused
        DownloadStatus.Completed -> Res.string.downloads_status_completed
        DownloadStatus.Failed -> Res.string.downloads_status_failed
        DownloadStatus.Cancelled -> Res.string.downloads_status_cancelled
    }

private val DownloadStatus.statusTone: DesignStatusTone
    get() = when (this) {
        DownloadStatus.Completed -> DesignStatusTone.Success
        DownloadStatus.Failed -> DesignStatusTone.Error
        DownloadStatus.Cancelled -> DesignStatusTone.Neutral
        else -> DesignStatusTone.Info
    }
