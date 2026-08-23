package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.julystar.musicapp.core.domain.model.AutoScanMode
import io.github.julystar.musicapp.core.domain.model.LocalMusicDirectory
import io.github.julystar.musicapp.core.domain.model.MAX_MINIMUM_AUDIO_DURATION_MS
import io.github.julystar.musicapp.core.domain.model.MissingFilePolicy
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.SourceConnectionTestStatus
import io.github.julystar.musicapp.core.domain.model.sanitizeSourceEndpointForDisplay
import io.github.julystar.musicapp.core.domain.model.sanitizeSourceTitleForDisplay
import io.github.julystar.musicapp.core.presentation.theme.DesignGradients
import io.github.julystar.musicapp.core.presentation.theme.DesignPalette
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncFailure
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncStatus
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncTask
import kotlin.time.Clock
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import musicapp.feature.settings.generated.resources.*
import musicapp.core.presentation.generated.resources.Res as CorePresentationRes
import musicapp.core.presentation.generated.resources.icon_vertialcal_more
import musicapp.core.presentation.generated.resources.icon_chevron_right
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SourceSettingsSection(
    state: SettingsUiState,
    onBack: (() -> Unit)?,
    onNavigateToSourceEditor: (SourceAccountId?) -> Unit,
    onAction: (SettingsAction) -> Unit,
) {
    val settings = state.settings
    var customDurationDialogOpen by remember { mutableStateOf(false) }
    var customDurationInputSeconds by remember { mutableStateOf("") }
    var localDirectoriesDialogOpen by remember { mutableStateOf(false) }
    var scanResultsAccountId by remember { mutableStateOf<SourceAccountId?>(null) }

    SettingsPageLayout(
        title = stringResource(Res.string.settings_sources_title),
        onBack = onBack,
        compactHorizontalPadding = 16.dp,
    ) {
        UnifiedLibraryCard(state = state, onAction = onAction)

        SmallTitle(text = stringResource(Res.string.settings_download_persistence_section))
        Card {
            SwitchPreference(
                title = stringResource(Res.string.settings_enrich_downloaded_files),
                summary = stringResource(Res.string.settings_enrich_downloaded_files_summary),
                checked = settings.downloadFinalization.enrichMetadata,
                onCheckedChange = { enabled ->
                    onAction(
                        SettingsAction.SetDownloadFinalizationSettings(
                            settings.downloadFinalization.copy(enrichMetadata = enabled),
                        )
                    )
                },
            )
            SwitchPreference(
                title = stringResource(Res.string.settings_save_sidecar_lyrics),
                summary = stringResource(Res.string.settings_save_sidecar_lyrics_summary),
                checked = settings.downloadFinalization.saveSidecarLyrics,
                enabled = settings.downloadFinalization.enrichMetadata,
                onCheckedChange = { enabled ->
                    onAction(
                        SettingsAction.SetDownloadFinalizationSettings(
                            settings.downloadFinalization.copy(saveSidecarLyrics = enabled),
                        )
                    )
                },
            )
        }

        SmallTitle(text = stringResource(Res.string.settings_sources_section))
        Card {
            state.sourceAccounts.forEach { account ->
                SourceAccountRow(
                    account = account,
                    localDirectories = state.localDirectories,
                    activeTask = state.scanTasks.firstOrNull { task ->
                        task.accountId == account.accountId && task.status.isActiveInSettings()
                    },
                    latestTask = state.scanTasks.firstOrNull { task ->
                        task.accountId == account.accountId
                    },
                    onManageLocal = { localDirectoriesDialogOpen = true },
                    onOpenEditor = {
                        dispatchSourceSettingsNavigation(account.accountId, onNavigateToSourceEditor)
                    },
                    onShowScanResults = { scanResultsAccountId = account.accountId },
                    onAction = onAction,
                )
            }
            AddSourceRow(
                enabled = state.capabilities.customMusicDirectorySupported ||
                    state.capabilities.secureCredentialStoreSupported,
                onClick = { dispatchSourceSettingsNavigation(null, onNavigateToSourceEditor) },
            )
        }

        SmallTitle(text = stringResource(Res.string.settings_automatic_scanning_section))
        Card {
            val autoScanModes = if (state.capabilities.backgroundScanSupported) {
                AutoScanMode.entries.toList()
            } else {
                listOf(AutoScanMode.Off, AutoScanMode.OnStartup)
            }
            OverlayDropdownPreference(
                title = stringResource(Res.string.settings_auto_scan),
                entries = listOf(DropdownEntry(items = autoScanModes.map { mode ->
                    DropdownItem(
                        text = stringResource(mode.titleResource()),
                        selected = mode == settings.autoScanMode,
                        onClick = { onAction(SettingsAction.SetAutoScanMode(mode)) },
                    )
                })),
            )
        }

        SmallTitle(text = stringResource(Res.string.settings_import_rules_section))
        Card {
            MinimumDurationSelectRow(
                selectedDurationMs = settings.minimumAudioDurationMs,
                onSelectDuration = { onAction(SettingsAction.SetMinimumAudioDurationMs(it)) },
                onSelectCustom = {
                    customDurationInputSeconds =
                        (settings.minimumAudioDurationMs / 1_000L).toString()
                    customDurationDialogOpen = true
                },
            )
            OverlayDropdownPreference(
                title = stringResource(Res.string.settings_missing_file),
                summary = stringResource(Res.string.settings_missing_file_design_summary),
                entries = listOf(DropdownEntry(items = MissingFilePolicy.entries.map { policy ->
                    DropdownItem(
                        text = stringResource(policy.titleResource()),
                        selected = policy == settings.missingFilePolicy,
                        onClick = { onAction(SettingsAction.SetMissingFilePolicy(policy)) },
                    )
                })),
            )
        }

        SmallTitle(text = stringResource(Res.string.settings_maintenance_section))
        Card {
            ArrowPreference(
                title = stringResource(Res.string.settings_refresh_missing_artwork),
                summary = stringResource(Res.string.settings_refresh_missing_artwork_summary),
                enabled = !state.maintenanceOperationInProgress,
                onClick = { onAction(SettingsAction.RefreshMissingArtwork) },
            )
            ArrowPreference(
                title = stringResource(Res.string.settings_refresh_missing_lyrics),
                summary = stringResource(Res.string.settings_refresh_missing_lyrics_summary),
                enabled = !state.maintenanceOperationInProgress,
                onClick = { onAction(SettingsAction.RefreshMissingLyrics) },
            )
            BasicComponent(
                title = stringResource(Res.string.settings_rebuild_library),
                summary = stringResource(Res.string.settings_rebuild_library_summary),
                enabled = !state.maintenanceOperationInProgress,
                onClick = { onAction(SettingsAction.RequestRebuildLibrary) },
                titleColor = BasicComponentDefaults.titleColor(MiuixTheme.colorScheme.error),
            )
        }
    }

    LocalDirectoriesDialog(
        show = localDirectoriesDialogOpen,
        directories = state.localDirectories,
        onAdd = {
            localDirectoriesDialogOpen = false
            onAction(SettingsAction.RequestAddLocalDirectory)
        },
        onRemove = { directory ->
            localDirectoriesDialogOpen = false
            onAction(
                SettingsAction.RequestRemoveLocalDirectory(
                    id = directory.id,
                    title = directory.displayName,
                ),
            )
        },
        onDismiss = { localDirectoriesDialogOpen = false },
    )

    SettingsInputDialog(
        show = customDurationDialogOpen,
        title = stringResource(Res.string.settings_min_duration_custom_title),
        message = stringResource(Res.string.settings_min_duration_custom_message),
        value = customDurationInputSeconds,
        label = stringResource(Res.string.settings_seconds_unit),
        onValueChange = { customDurationInputSeconds = it.filter(Char::isDigit) },
        onConfirm = {
            customDurationInputSeconds.toLongOrNull()?.let { seconds ->
                val normalizedSeconds = seconds.coerceIn(
                    minimumValue = 0L,
                    maximumValue = MAX_MINIMUM_AUDIO_DURATION_MS / 1_000L,
                )
                onAction(SettingsAction.SetMinimumAudioDurationMs(normalizedSeconds * 1_000L))
                customDurationDialogOpen = false
            }
        },
        onDismiss = { customDurationDialogOpen = false },
    )
    WebDavAccountDialog(state = state, dialog = state.webDavDialog, onAction = onAction)
    SmbAccountDialog(state = state, dialog = state.smbDialog, onAction = onAction)
    SettingsConfirmDialog(
        show = state.pendingConfirmation is SettingsConfirmation.RemoveLocalDirectory,
        title = stringResource(Res.string.settings_confirm_remove_directory_title),
        message = stringResource(Res.string.settings_confirm_remove_directory_message),
        confirmText = stringResource(Res.string.settings_confirm),
        onConfirm = { onAction(SettingsAction.ConfirmPendingAction) },
        onDismiss = { onAction(SettingsAction.DismissConfirmation) },
    )
    SettingsConfirmDialog(
        show = state.pendingConfirmation is SettingsConfirmation.DeleteWebDavAccount,
        title = stringResource(Res.string.settings_confirm_delete_webdav_title),
        message = stringResource(Res.string.settings_confirm_delete_webdav_message),
        confirmText = stringResource(Res.string.settings_delete),
        onConfirm = { onAction(SettingsAction.ConfirmPendingAction) },
        onDismiss = { onAction(SettingsAction.DismissConfirmation) },
    )
    SettingsConfirmDialog(
        show = state.pendingConfirmation is SettingsConfirmation.DeleteSmbAccount,
        title = stringResource(Res.string.settings_confirm_delete_smb_title),
        message = stringResource(Res.string.settings_confirm_delete_smb_message),
        confirmText = stringResource(Res.string.settings_delete),
        onConfirm = { onAction(SettingsAction.ConfirmPendingAction) },
        onDismiss = { onAction(SettingsAction.DismissConfirmation) },
    )
    SettingsConfirmDialog(
        show = state.pendingConfirmation == SettingsConfirmation.RebuildLibrary,
        title = stringResource(Res.string.settings_confirm_rebuild_title),
        message = stringResource(Res.string.settings_confirm_rebuild_message),
        confirmText = stringResource(Res.string.settings_confirm),
        onConfirm = { onAction(SettingsAction.ConfirmPendingAction) },
        onDismiss = { onAction(SettingsAction.DismissConfirmation) },
    )
    ScanFailureDialog(
        show = state.failureDialogTaskId != null,
        failures = state.failureDetails,
        onDismiss = { onAction(SettingsAction.DismissScanFailures) },
    )
    val scanResultsAccount = state.sourceAccounts.firstOrNull { account ->
        account.accountId == scanResultsAccountId
    }
    SourceScanResultsDialog(
        show = scanResultsAccount != null,
        sourceTitle = scanResultsAccount?.let { account ->
            if (account.isLocal) {
                stringResource(Res.string.settings_source_local)
            } else {
                sanitizeSourceTitleForDisplay(account.title, account.subtitle, account.sourceLabel)
            }
        }.orEmpty(),
        task = scanResultsAccount?.let { account ->
            state.scanTasks.firstOrNull { task -> task.accountId == account.accountId }
        },
        onOpenFailures = { taskId ->
            scanResultsAccountId = null
            onAction(SettingsAction.OpenScanFailures(taskId))
        },
        onDismiss = { scanResultsAccountId = null },
    )
}

internal fun dispatchSourceSettingsNavigation(
    accountId: SourceAccountId?,
    onNavigateToSourceEditor: (SourceAccountId?) -> Unit,
) {
    onNavigateToSourceEditor(accountId)
}

@Composable
private fun UnifiedLibraryCard(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    val sourceCount = state.sourceAccounts.size
    val enabledCount = state.enabledSourceCount
    val pausedCount = sourceCount - enabledCount
    val activeTasks = state.scanTasks.filter { it.status.isActiveInSettings() }
    val active = activeTasks.isNotEmpty()
    val latestTask = state.scanTasks.firstOrNull()
    val attentionTaskId = latestTask?.takeIf { task ->
        task.failedCount > 0L ||
            task.status == LibrarySyncStatus.Failed ||
            task.status == LibrarySyncStatus.CompletedWithErrors
    }?.id
    val needsAttention = attentionTaskId != null
    val statusText = when {
        active -> stringResource(Res.string.settings_library_scanning)
        needsAttention -> stringResource(Res.string.settings_library_scan_attention)
        sourceCount == 0 -> stringResource(Res.string.settings_library_no_sources)
        pausedCount > 0 -> stringResource(Res.string.settings_library_sources_paused, pausedCount)
        else -> stringResource(Res.string.settings_library_up_to_date)
    }
    val statusColor = when {
        active -> MiuixTheme.colorScheme.primary
        needsAttention -> MiuixTheme.colorScheme.error
        sourceCount > 0 && pausedCount == 0 -> DesignPalette.SupportGreen
        else -> DesignPalette.SupportYellow
    }
    val lastScanAt = state.sourceAccounts.mapNotNull { it.lastScanAtEpochMs }.maxOrNull()
    val scanSummary = when {
        active -> stringResource(
            Res.string.settings_library_scanning_summary,
            activeTasks.sumOf(LibrarySyncTask::processedCount).groupedCount(),
        )
        latestTask == null -> stringResource(
            Res.string.settings_library_no_scan_summary,
            state.trackCount.groupedCount(),
        )
        latestTask.failedCount == 0L -> stringResource(
            Res.string.settings_library_scan_complete_no_failures_summary,
            latestTask.updatedAtEpochMs.relativeScanLabel(compact = false),
            state.trackCount.groupedCount(),
        )
        else -> stringResource(
            Res.string.settings_library_scan_complete_summary,
            latestTask.updatedAtEpochMs.relativeScanLabel(compact = false),
            state.trackCount.groupedCount(),
            latestTask.failedCount.groupedCount(),
        )
    }
    val processed = activeTasks.sumOf(LibrarySyncTask::processedCount)
    val scanned = activeTasks.sumOf(LibrarySyncTask::scannedCount)
    val progress = if (scanned > 0L) {
        (processed.toFloat() / scanned.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val canScan = state.enabledSourceCount > 0 && !state.maintenanceOperationInProgress

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.icon_source_layers),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(Res.string.settings_unified_library),
                            color = MiuixTheme.colorScheme.onSurface,
                            style = MiuixTheme.textStyles.body1,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        StatusPill(label = statusText, color = statusColor)
                    }
                    Text(
                        text = stringResource(Res.string.settings_unified_library_summary),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.body2,
                        lineHeight = 18.sp,
                    )
                }
            }

            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.6f)),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                LibraryStat(
                    value = state.trackCount.groupedCount(),
                    label = stringResource(Res.string.settings_tracks_indexed),
                    modifier = Modifier.weight(1f),
                )
                StatDivider()
                LibraryStat(
                    value = "$enabledCount/$sourceCount",
                    label = stringResource(Res.string.settings_sources_enabled),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
                StatDivider()
                LibraryStat(
                    value = lastScanAt.relativeScanLabel(compact = true),
                    label = stringResource(Res.string.settings_source_last_scan),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = scanSummary,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (attentionTaskId != null) {
                                Modifier.clickable {
                                    onAction(SettingsAction.OpenScanFailures(attentionTaskId))
                                }
                            } else {
                                Modifier
                            },
                        ),
                )
                IconButton(
                    enabled = active || canScan,
                    onClick = {
                        onAction(
                            if (active) SettingsAction.CancelActiveScans
                            else SettingsAction.ScanAllSources,
                        )
                    },
                ) {
                    Icon(
                        painter = painterResource(
                            if (active) Res.drawable.icon_close else Res.drawable.icon_source_refresh,
                        ),
                        contentDescription = stringResource(
                            if (active) Res.string.settings_cancel else Res.string.settings_scan_now,
                        ),
                        tint = if (active) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (active) {
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .offset(y = 12.dp),
            )
        }
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Text(
        text = label,
        color = color,
        style = MiuixTheme.textStyles.footnote2,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun LibraryStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatDivider() {
    Spacer(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp)
            .background(MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.6f)),
    )
}

@Composable
private fun SourceAccountRow(
    account: SourceAccountSettingsItem,
    localDirectories: List<LocalMusicDirectory>,
    activeTask: LibrarySyncTask?,
    latestTask: LibrarySyncTask?,
    onManageLocal: () -> Unit,
    onOpenEditor: () -> Unit,
    onShowScanResults: () -> Unit,
    onAction: (SettingsAction) -> Unit,
) {
    val sourceTitle = if (account.isLocal) {
        stringResource(Res.string.settings_source_local)
    } else {
        sanitizeSourceTitleForDisplay(account.title, account.subtitle, account.sourceLabel)
    }
    val location = when {
        account.isLocal -> stringResource(
            if (localDirectories.size == 1) {
                Res.string.settings_source_directory_count_one
            } else {
                Res.string.settings_source_directory_count_other
            },
            localDirectories.size,
        )
        !account.rootPath.isNullOrBlank() -> account.rootPath
        else -> sanitizeSourceEndpointForDisplay(account.subtitle).orEmpty()
    }
    val metadata = listOf(
        account.sourceLabel.takeUnless { account.isLocal }.orEmpty(),
        location,
        stringResource(Res.string.settings_track_count, account.trackCount.groupedCount()),
    ).filter(String::isNotBlank).joinToString(" · ")
    val isScanning = activeTask != null || account.lastScanStatus == "RUNNING"
    val scanNeedsAttention = latestTask?.hasError == true ||
        account.lastScanStatus == "SYNCED_WITH_ERRORS"
    val scanStatusLabel = when {
        isScanning -> stringResource(Res.string.settings_source_scanning)
        scanNeedsAttention -> stringResource(Res.string.settings_library_scan_attention)
        account.lastScanStatus == "SYNCED" || account.lastScanAtEpochMs != null ->
            stringResource(Res.string.settings_library_up_to_date)
        else -> stringResource(Res.string.settings_source_never_scanned)
    }
    val scanStatusColor = when {
        isScanning -> MiuixTheme.colorScheme.primary
        scanNeedsAttention -> MiuixTheme.colorScheme.error
        account.lastScanAtEpochMs != null -> DesignPalette.SupportGreen
        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    val connectionLabel = when {
        !account.enabled -> stringResource(Res.string.settings_source_disconnected)
        account.isLocal -> stringResource(Res.string.settings_source_available)
        else -> stringResource(Res.string.settings_source_connected)
    }
    val visual = account.sourceVisual()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { if (account.isLocal) onManageLocal() else onOpenEditor() }
                .heightIn(min = 88.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                painter = painterResource(visual.icon),
                contentDescription = null,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(24.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 1.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = sourceTitle,
                        color = MiuixTheme.colorScheme.onSurface,
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    StatusPill(
                        label = stringResource(
                            if (account.enabled) {
                                Res.string.settings_source_enabled
                            } else {
                                Res.string.settings_source_paused
                            },
                        ),
                        color = if (account.enabled) {
                            DesignPalette.SupportGreen
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                    )
                }
                Text(
                    text = metadata,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                SourceHealthLine(
                    connectionLabel = connectionLabel,
                    scanStatusLabel = scanStatusLabel,
                    scanStatusColor = scanStatusColor,
                    lastScanLabel = account.lastScanAtEpochMs?.relativeScanLabel(compact = true),
                    connected = account.enabled,
                    scanning = isScanning,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Switch(
                    checked = account.enabled,
                    onCheckedChange = {
                        onAction(SettingsAction.SetAccountEnabled(account.accountId, it))
                    },
                )
                SourceActionsButton(
                    account = account,
                    sourceTitle = sourceTitle,
                    onShowScanResults = onShowScanResults,
                    onManageLocal = onManageLocal,
                    onOpenEditor = onOpenEditor,
                    onAction = onAction,
                )
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun SourceHealthLine(
    connectionLabel: String,
    scanStatusLabel: String,
    scanStatusColor: Color,
    lastScanLabel: String?,
    connected: Boolean,
    scanning: Boolean,
) {
    val transition = rememberInfiniteTransition()
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
        ),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    if (connected) {
                        DesignPalette.SupportGreen
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.55f)
                    },
                ),
        )
        Text(
            text = connectionLabel,
            color = if (connected) {
                DesignPalette.SupportGreen
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            },
            style = MiuixTheme.textStyles.footnote2,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        Text(
            text = "·",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote2,
        )
        if (scanning) {
            Icon(
                painter = painterResource(Res.drawable.icon_source_refresh),
                contentDescription = null,
                tint = scanStatusColor,
                modifier = Modifier
                    .size(11.dp)
                    .graphicsLayer(rotationZ = rotation),
            )
        }
        Text(
            text = scanStatusLabel,
            color = scanStatusColor,
            style = MiuixTheme.textStyles.footnote2,
            maxLines = 1,
        )
        if (!scanning && lastScanLabel != null) {
            Text(
                text = "·",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote2,
            )
            Text(
                text = lastScanLabel,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SourceActionsButton(
    account: SourceAccountSettingsItem,
    sourceTitle: String,
    onShowScanResults: () -> Unit,
    onManageLocal: () -> Unit,
    onOpenEditor: () -> Unit,
    onAction: (SettingsAction) -> Unit,
) {
    var menuOpen by remember(account.accountId) { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(DesignTokens.adaptive.minimumTouchTarget)
                .clip(RoundedCornerShape(999.dp))
                .clickable { menuOpen = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(CorePresentationRes.drawable.icon_vertialcal_more),
                contentDescription = stringResource(Res.string.settings_source_more_actions),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier.size(20.dp),
            )
        }
        OverlayDropdownPopup(
            DropdownEntry(
                items = buildList {
                if (account.isLocal) {
                    add(
                        DropdownItem(
                            text = stringResource(Res.string.settings_source_manage),
                            icon = { modifier -> Icon(painterResource(Res.drawable.icon_source_sliders), null, modifier) },
                            onClick = {
                                menuOpen = false
                                onManageLocal()
                            },
                        ),
                    )
                }
                if (!account.isLocal) {
                    add(
                        DropdownItem(
                            text = stringResource(Res.string.settings_source_edit_action),
                            icon = { modifier -> Icon(painterResource(Res.drawable.icon_source_pencil), null, modifier) },
                            onClick = {
                                menuOpen = false
                                onOpenEditor()
                            },
                        ),
                    )
                }
                if (account.isWebDav || account.isSmb || account.isOpenList) {
                    add(
                        DropdownItem(
                            text = stringResource(Res.string.settings_source_path_action),
                            icon = { modifier -> Icon(painterResource(Res.drawable.icon_source_sliders), null, modifier) },
                            onClick = {
                                menuOpen = false
                                onAction(SettingsAction.ConfigureSourcePath(account.accountId))
                            },
                        ),
                    )
                }
                if (!account.isRemoteServer) {
                    add(
                        DropdownItem(
                            text = stringResource(Res.string.settings_source_scan_action),
                            icon = { modifier -> Icon(painterResource(Res.drawable.icon_source_refresh), null, modifier) },
                            enabled = account.enabled &&
                                (!account.isSmb || !account.rootPath.isNullOrBlank()),
                            onClick = {
                                menuOpen = false
                                onAction(SettingsAction.ScanSourceAccount(account.accountId))
                            },
                        ),
                    )
                }
                add(
                    DropdownItem(
                        text = stringResource(Res.string.settings_source_scan_results),
                        icon = { modifier -> Icon(painterResource(Res.drawable.icon_log), null, modifier) },
                        onClick = {
                            menuOpen = false
                            onShowScanResults()
                        },
                    ),
                )
                if (account.isWebDav || account.isSmb) {
                    add(
                        DropdownItem(
                            text = stringResource(Res.string.settings_source_delete_action),
                            icon = { modifier -> Icon(painterResource(Res.drawable.icon_source_trash), null, modifier) },
                            onClick = {
                                menuOpen = false
                                onAction(
                                    if (account.isWebDav) {
                                        SettingsAction.RequestDeleteWebDavAccount(
                                            accountId = account.accountId,
                                            title = sourceTitle,
                                        )
                                    } else {
                                        SettingsAction.RequestDeleteSmbAccount(
                                            accountId = account.accountId,
                                            title = sourceTitle,
                                        )
                                    },
                                )
                            },
                        ),
                    )
                }
            },
            ),
            show = menuOpen,
            onDismiss = { menuOpen = false },
            onDismissFinished = {},
            maxHeight = 360.dp,
            dropdownColors = DropdownDefaults.dropdownColors(),
            renderInRootScaffold = true,
        )
    }
}

@Composable
private fun SourceScanResultsDialog(
    show: Boolean,
    sourceTitle: String,
    task: LibrarySyncTask?,
    onOpenFailures: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayDialog(show = show, onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(Res.string.settings_source_scan_results),
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = sourceTitle,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (task == null) {
                Text(
                    text = stringResource(Res.string.settings_scan_no_history),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                )
            } else {
                StatusPill(
                    label = task.status.localizedScanStatus(),
                    color = task.status.scanStatusColor(),
                )
                Text(
                    text = stringResource(
                        Res.string.settings_scan_status_summary,
                        task.folderDisplayPath,
                        task.status.localizedScanStatus(),
                        task.scannedCount.groupedCount(),
                        task.addedCount.groupedCount(),
                        task.modifiedCount.groupedCount(),
                        task.deletedCount.groupedCount(),
                        task.skippedCount.groupedCount(),
                        task.metadataRequestCount.groupedCount(),
                        task.totalElapsedMs.groupedCount(),
                    ),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                    lineHeight = 16.sp,
                )
                task.errorMessage?.takeIf(String::isNotBlank)?.let { message ->
                    Text(
                        text = message,
                        color = MiuixTheme.colorScheme.error,
                        style = MiuixTheme.textStyles.footnote1,
                        lineHeight = 16.sp,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                task?.takeIf { it.failedCount > 0L }?.let { failedTask ->
                    TextButton(
                        text = stringResource(
                            Res.string.settings_scan_failure_count,
                            failedTask.failedCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        ),
                        onClick = { onOpenFailures(failedTask.id) },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(
                    text = stringResource(Res.string.settings_close),
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun LibrarySyncStatus.localizedScanStatus(): String = when (this) {
    LibrarySyncStatus.Queued -> stringResource(Res.string.settings_scan_status_queued)
    LibrarySyncStatus.Running -> stringResource(Res.string.settings_scan_status_running)
    LibrarySyncStatus.Paused -> stringResource(Res.string.settings_scan_status_paused)
    LibrarySyncStatus.Completed -> stringResource(Res.string.settings_scan_status_completed)
    LibrarySyncStatus.CompletedWithErrors ->
        stringResource(Res.string.settings_scan_status_completed_errors)
    LibrarySyncStatus.Failed -> stringResource(Res.string.settings_scan_status_failed)
    LibrarySyncStatus.Cancelled -> stringResource(Res.string.settings_scan_status_cancelled)
    LibrarySyncStatus.Unknown -> stringResource(Res.string.settings_scan_status_unknown)
}

@Composable
private fun LibrarySyncStatus.scanStatusColor(): Color = when (this) {
    LibrarySyncStatus.Queued,
    LibrarySyncStatus.Running,
    LibrarySyncStatus.Paused -> MiuixTheme.colorScheme.primary
    LibrarySyncStatus.Completed -> DesignPalette.SupportGreen
    LibrarySyncStatus.CompletedWithErrors,
    LibrarySyncStatus.Failed -> MiuixTheme.colorScheme.error
    LibrarySyncStatus.Cancelled,
    LibrarySyncStatus.Unknown -> MiuixTheme.colorScheme.onSurfaceVariantSummary
}

@Composable
private fun AddSourceRow(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(Res.drawable.icon_source_plus),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = stringResource(Res.string.settings_add_source),
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(Res.string.settings_add_source_summary),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SourceTrailingChevron()
    }
}

@Composable
private fun LocalDirectoriesDialog(
    show: Boolean,
    directories: List<LocalMusicDirectory>,
    onAdd: () -> Unit,
    onRemove: (LocalMusicDirectory) -> Unit,
    onDismiss: () -> Unit,
) {
    OverlayDialog(show = show, onDismissRequest = onDismiss) {
        Text(
            text = stringResource(Res.string.settings_source_manage),
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.settings_source_manage_summary),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                .verticalScroll(rememberScrollState()),
        ) {
            directories.forEachIndexed { index, directory ->
                LocalDirectoryRow(directory = directory, onRemove = { onRemove(directory) })
                if (index != directories.lastIndex) {
                    HorizontalDivider()
                }
            }
            if (directories.isNotEmpty()) {
                HorizontalDivider()
            }
            SourcePickerRow(
                icon = Res.drawable.icon_source_plus,
                colors = DesignGradients.PinkOrange.colors,
                title = stringResource(Res.string.settings_source_add_local),
                summary = stringResource(Res.string.settings_source_add_local_summary),
                onClick = onAdd,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                text = stringResource(Res.string.settings_close),
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun LocalDirectoryRow(
    directory: LocalMusicDirectory,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(Res.drawable.icon_source_hard_drive),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = directory.displayName,
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = directory.path,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.icon_source_trash),
                contentDescription = stringResource(Res.string.settings_delete),
                tint = MiuixTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SourcePickerRow(
    icon: DrawableResource,
    colors: List<Color>,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = summary,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SourceTrailingChevron()
    }
}

@Composable
private fun SourceTrailingChevron() {
    Box(
        modifier = Modifier.size(DesignTokens.adaptive.minimumTouchTarget),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(CorePresentationRes.drawable.icon_chevron_right),
            contentDescription = null,
        )
    }
}

private data class SourceVisual(
    val icon: DrawableResource,
    val colors: List<Color>,
)

private fun SourceAccountSettingsItem.sourceVisual(): SourceVisual = when {
    isLocal -> SourceVisual(
        icon = Res.drawable.icon_source_hard_drive,
        colors = DesignGradients.PinkOrange.colors,
    )
    isWebDav -> SourceVisual(
        icon = Res.drawable.icon_source_server,
        colors = DesignGradients.BluePurple.colors,
    )
    isSmb -> SourceVisual(
        icon = Res.drawable.icon_source_database,
        colors = DesignGradients.OrangeYellow.colors,
    )
    else -> SourceVisual(
        icon = Res.drawable.icon_source_server,
        colors = DesignGradients.GreenBlue.colors,
    )
}

@Composable
private fun Long?.relativeScanLabel(compact: Boolean): String {
    if (this == null) return stringResource(Res.string.settings_source_never_scanned)
    val elapsedMs = (Clock.System.now().toEpochMilliseconds() - this).coerceAtLeast(0L)
    val minutes = elapsedMs / 60_000L
    return when {
        minutes < 1L -> stringResource(Res.string.settings_relative_now)
        minutes < 60L -> stringResource(
            if (compact) {
                Res.string.settings_relative_minutes_short
            } else {
                Res.string.settings_relative_minutes
            },
            minutes,
        )
        minutes < 1_440L -> stringResource(
            if (compact) {
                Res.string.settings_relative_hours_short
            } else {
                Res.string.settings_relative_hours
            },
            minutes / 60L,
        )
        else -> stringResource(
            if (compact) {
                Res.string.settings_relative_days_short
            } else {
                Res.string.settings_relative_days
            },
            minutes / 1_440L,
        )
    }
}

private fun Long.groupedCount(): String {
    val raw = toString()
    return raw.reversed().chunked(3).joinToString(",").reversed()
}

@Composable
private fun ScanFailureDialog(
    show: Boolean,
    failures: List<LibrarySyncFailure>,
    onDismiss: () -> Unit,
) {
    OverlayDialog(show = show, onDismissRequest = onDismiss) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = stringResource(Res.string.settings_failure_dialog_title),
                style = MiuixTheme.textStyles.title3,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (failures.isEmpty()) {
                Text(
                    text = stringResource(Res.string.settings_failure_empty),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            } else {
                failures.forEach { failure ->
                    ScanFailureItem(failure.toScanFailureDisplay())
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    text = stringResource(Res.string.settings_close),
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun ScanFailureItem(failure: ScanFailureDisplay) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(
                Res.string.settings_failure_file,
                failure.fileName ?: stringResource(Res.string.settings_failure_unknown_file),
            ),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
        )
        failure.directory?.let { directory ->
            Text(
                text = stringResource(Res.string.settings_failure_location, directory),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Text(
            text = stringResource(Res.string.settings_failure_reason, failure.reason.localizedText()),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun ScanFailureReason.localizedText(): String = when (this) {
    ScanFailureReason.Unknown -> stringResource(Res.string.settings_feedback_unknown_error)
    is ScanFailureReason.RemoteRead -> {
        val status = httpStatus?.let { rawStatus ->
            when {
                rawStatus.startsWith("500 ") -> stringResource(Res.string.settings_failure_http_500)
                rawStatus.startsWith("404 ") -> stringResource(Res.string.settings_failure_http_404)
                rawStatus.startsWith("401 ") -> stringResource(Res.string.settings_failure_http_401)
                rawStatus.startsWith("403 ") -> stringResource(Res.string.settings_failure_http_403)
                else -> "HTTP $rawStatus"
            }
        }
        if (status == null) {
            stringResource(Res.string.settings_failure_remote_read_unknown)
        } else {
            stringResource(Res.string.settings_failure_remote_read_status, status)
        }
    }
    is ScanFailureReason.ByteBudget -> {
        val limit = limitBytes?.let(::formatBytes)
        if (limit == null) {
            stringResource(Res.string.settings_failure_byte_budget)
        } else {
            stringResource(Res.string.settings_failure_byte_budget_limit, limit)
        }
    }
    ScanFailureReason.UnsupportedContainer ->
        stringResource(Res.string.settings_failure_unsupported_container)
    ScanFailureReason.MissingMetadata ->
        stringResource(Res.string.settings_failure_missing_metadata)
    is ScanFailureReason.MetadataError ->
        stringResource(Res.string.settings_failure_metadata_error, detail)
    is ScanFailureReason.Raw -> detail
}

@Composable
private fun WebDavAccountDialog(
    state: SettingsUiState,
    dialog: WebDavAccountDialogState?,
    onAction: (SettingsAction) -> Unit,
) {
    val dialogVisible = dialog != null
    var retainedDialog by remember { mutableStateOf(dialog) }
    SideEffect {
        if (dialog != null) retainedDialog = dialog
    }
    val activeDialog = dialog ?: retainedDialog ?: return
    var draft by remember(activeDialog.accountId, activeDialog.isEditing) {
        mutableStateOf(activeDialog)
    }
    var password by remember(activeDialog.accountId, activeDialog.isEditing) { mutableStateOf("") }
    var showConnectionTestResult by remember(activeDialog.accountId, activeDialog.isEditing) {
        mutableStateOf(false)
    }
    LaunchedEffect(dialogVisible, activeDialog.accountId, activeDialog.isEditing) {
        if (dialogVisible) {
            draft = activeDialog
            password = ""
            showConnectionTestResult = false
        }
    }
    OverlayDialog(
        show = dialogVisible,
        onDismissRequest = { onAction(SettingsAction.DismissWebDavDialog) },
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = stringResource(
                    if (draft.isEditing) Res.string.settings_webdav_edit_title
                    else Res.string.settings_webdav_add_title
                ),
                style = MiuixTheme.textStyles.title3,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextField(
                value = draft.name,
                onValueChange = {
                    draft = draft.copy(name = it)
                    showConnectionTestResult = false
                },
                label = stringResource(Res.string.settings_webdav_name),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = draft.serverUrl,
                onValueChange = {
                    draft = draft.copy(serverUrl = it)
                    showConnectionTestResult = false
                },
                label = stringResource(Res.string.settings_webdav_url),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = draft.username,
                onValueChange = {
                    draft = draft.copy(username = it)
                    showConnectionTestResult = false
                },
                label = stringResource(Res.string.settings_webdav_username),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = password,
                onValueChange = {
                    password = it
                    showConnectionTestResult = false
                },
                label = stringResource(
                    if (draft.isEditing) Res.string.settings_webdav_password_edit
                    else Res.string.settings_webdav_password_new
                ),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SourceConnectionTestMessage(
                    message = state.webDavConnectionTestMessage
                        ?.takeIf { showConnectionTestResult },
                    status = state.webDavConnectionTestStatus,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(Res.string.settings_cancel),
                    onClick = { onAction(SettingsAction.DismissWebDavDialog) },
                )
                TextButton(
                    text = stringResource(Res.string.settings_test),
                    onClick = {
                        showConnectionTestResult = true
                        onAction(SettingsAction.TestWebDavConnection(password, draft))
                    },
                )
                TextButton(
                    text = stringResource(Res.string.settings_save),
                    onClick = { onAction(SettingsAction.SaveWebDavAccount(password, draft)) },
                )
            }
        }
    }
}

@Composable
private fun SmbAccountDialog(
    state: SettingsUiState,
    dialog: SmbAccountDialogState?,
    onAction: (SettingsAction) -> Unit,
) {
    val dialogVisible = dialog != null
    var retainedDialog by remember { mutableStateOf(dialog) }
    SideEffect {
        if (dialog != null) retainedDialog = dialog
    }
    val activeDialog = dialog ?: retainedDialog ?: return
    var draft by remember(activeDialog.accountId, activeDialog.isEditing) {
        mutableStateOf(activeDialog)
    }
    var password by remember(activeDialog.accountId, activeDialog.isEditing) { mutableStateOf("") }
    var showConnectionTestResult by remember(activeDialog.accountId, activeDialog.isEditing) {
        mutableStateOf(false)
    }
    LaunchedEffect(dialogVisible, activeDialog.accountId, activeDialog.isEditing) {
        if (dialogVisible) {
            draft = activeDialog
            password = ""
            showConnectionTestResult = false
        }
    }
    OverlayDialog(
        show = dialogVisible,
        onDismissRequest = { onAction(SettingsAction.DismissSmbDialog) },
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = stringResource(
                    if (draft.isEditing) Res.string.settings_smb_edit_title
                    else Res.string.settings_smb_add_title,
                ),
                style = MiuixTheme.textStyles.title3,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextField(
                value = draft.name,
                onValueChange = {
                    draft = draft.copy(name = it)
                    showConnectionTestResult = false
                },
                label = stringResource(Res.string.settings_smb_name),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = draft.host,
                    onValueChange = {
                        draft = draft.copy(host = it)
                        showConnectionTestResult = false
                    },
                    label = stringResource(Res.string.settings_smb_host),
                    modifier = Modifier.weight(0.72f),
                )
                TextField(
                    value = draft.port,
                    onValueChange = {
                        draft = draft.copy(port = it.filter(Char::isDigit))
                        showConnectionTestResult = false
                    },
                    label = stringResource(Res.string.settings_smb_port),
                    modifier = Modifier.weight(0.28f),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            SwitchPreference(
                title = stringResource(Res.string.settings_smb_guest),
                checked = draft.guestAccess,
                onCheckedChange = {
                    if (it) password = ""
                    draft = draft.copy(guestAccess = it)
                    showConnectionTestResult = false
                },
            )
            if (!draft.guestAccess) {
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = draft.username,
                    onValueChange = {
                        draft = draft.copy(username = it)
                        showConnectionTestResult = false
                    },
                    label = stringResource(Res.string.settings_smb_username),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = password,
                    onValueChange = {
                        password = it
                        showConnectionTestResult = false
                    },
                    label = stringResource(
                        if (draft.isEditing) Res.string.settings_smb_password_edit
                        else Res.string.settings_smb_password_new,
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SourceConnectionTestMessage(
                    message = state.smbConnectionTestMessage
                        ?.takeIf { showConnectionTestResult },
                    status = state.smbConnectionTestStatus,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(Res.string.settings_cancel),
                    onClick = { onAction(SettingsAction.DismissSmbDialog) },
                )
                TextButton(
                    text = stringResource(Res.string.settings_test),
                    onClick = {
                        showConnectionTestResult = true
                        onAction(SettingsAction.TestSmbConnection(password, draft))
                    },
                )
                TextButton(
                    text = stringResource(Res.string.settings_save),
                    onClick = { onAction(SettingsAction.SaveSmbAccount(password, draft)) },
                )
            }
        }
    }
}

@Composable
private fun SourceConnectionTestMessage(
    message: String?,
    status: SourceConnectionTestStatus,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(end = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        message?.let {
            Text(
                text = it,
                style = MiuixTheme.textStyles.body2,
                color = if (status == SourceConnectionTestStatus.Error) {
                    MiuixTheme.colorScheme.error
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun LibrarySyncStatus.isActiveInSettings(): Boolean {
    return this == LibrarySyncStatus.Queued ||
        this == LibrarySyncStatus.Running ||
        this == LibrarySyncStatus.Paused
}

@Composable
private fun MinimumDurationSelectRow(
    selectedDurationMs: Long,
    onSelectDuration: (Long) -> Unit,
    onSelectCustom: () -> Unit,
) {
    val selectedPreset = selectedDurationMs.takeIf { it in MINIMUM_DURATION_PRESETS_MS }
    OverlayDropdownPreference(
        title = stringResource(Res.string.settings_min_duration),
        entries = listOf(DropdownEntry(items = buildList {
            MINIMUM_DURATION_PRESETS_MS.forEach { durationMs ->
                add(
                    DropdownItem(
                        text = durationMs.durationLabel(),
                        selected = durationMs == selectedPreset,
                        onClick = { onSelectDuration(durationMs) },
                    ),
                )
            }
            add(
                DropdownItem(
                    text = stringResource(Res.string.settings_min_duration_custom),
                    selected = selectedPreset == null,
                    onClick = onSelectCustom,
                ),
            )
        })),
    )
}

@Composable
private fun Long.durationLabel(): String = if (this == 0L) {
    stringResource(Res.string.settings_min_duration_off)
} else {
    stringResource(Res.string.settings_seconds, this / 1_000L)
}

private fun AutoScanMode.titleResource() = when (this) {
    AutoScanMode.Off -> Res.string.settings_auto_scan_off
    AutoScanMode.OnStartup -> Res.string.settings_auto_scan_startup
    AutoScanMode.Periodic -> Res.string.settings_auto_scan_periodic
}

private fun MissingFilePolicy.titleResource() = when (this) {
    MissingFilePolicy.MarkUnavailable -> Res.string.settings_missing_mark
    MissingFilePolicy.RemoveOnScan -> Res.string.settings_missing_remove
}

private val MINIMUM_DURATION_PRESETS_MS = listOf(0L, 15_000L, 30_000L, 60_000L)
