package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.julystar.musicapp.core.domain.model.BackupSchedule
import io.github.julystar.musicapp.core.domain.model.toStorageRouteIdOrNull
import org.jetbrains.compose.resources.stringResource
import musicapp.feature.settings.generated.resources.*
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun StorageSettingsSection(
    state: SettingsUiState,
    onBack: (() -> Unit)?,
    onAction: (SettingsAction) -> Unit,
) {
    val usage = state.storageUsage
    val busy = state.maintenanceOperationInProgress
    val backup = state.settings.backup
    var editingRemoteDirectory by remember { mutableStateOf(false) }
    var remoteDirectoryInput by remember(backup.remoteDirectory) {
        mutableStateOf(backup.remoteDirectory)
    }

    SettingsPageLayout(title = stringResource(Res.string.settings_storage_title), onBack = onBack) {
        SmallTitle(
            text = stringResource(Res.string.settings_usage_section),
            insideMargin = settingsSectionTitleMargin,
        )
        Card {
            BasicComponent(
                title = stringResource(Res.string.settings_usage_audio),
                summary = formatBytes(usage.audioBytes),
            )
            BasicComponent(
                title = stringResource(Res.string.settings_usage_image),
                summary = formatBytes(usage.imageBytes),
            )
            BasicComponent(
                title = stringResource(Res.string.settings_usage_downloads),
                summary = formatBytes(usage.downloadBytes),
            )
            BasicComponent(
                title = stringResource(Res.string.settings_usage_database),
                summary = formatBytes(usage.databaseBytes),
            )
            BasicComponent(
                title = stringResource(Res.string.settings_usage_logs),
                summary = formatBytes(usage.logBytes),
            )
            BasicComponent(
                title = stringResource(Res.string.settings_usage_total),
                summary = formatBytes(usage.totalBytes),
            )
            ArrowPreference(
                title = stringResource(Res.string.settings_usage_refresh),
                summary = if (state.storageRefreshing) {
                    stringResource(Res.string.settings_usage_refreshing)
                } else {
                    stringResource(Res.string.settings_usage_refresh)
                },
                enabled = !state.storageRefreshing && !busy,
                onClick = { onAction(SettingsAction.RefreshStorageUsage) },
            )
        }

        SmallTitle(
            text = stringResource(Res.string.settings_cleanup_section),
            insideMargin = settingsSectionTitleMargin,
        )
        Card {
            BasicComponent(
                title = stringResource(Res.string.settings_clear_audio),
                summary = stringResource(Res.string.settings_clear_audio_summary),
                enabled = !busy,
                onClick = { onAction(SettingsAction.RequestClearAudio) },
                titleColor = BasicComponentDefaults.titleColor(MiuixTheme.colorScheme.error),
            )
            BasicComponent(
                title = stringResource(Res.string.settings_clear_image),
                summary = stringResource(Res.string.settings_clear_image_summary),
                enabled = !busy,
                onClick = { onAction(SettingsAction.RequestClearImage) },
                titleColor = BasicComponentDefaults.titleColor(MiuixTheme.colorScheme.error),
            )
            BasicComponent(
                title = stringResource(Res.string.settings_clear_all),
                summary = stringResource(Res.string.settings_clear_all_summary),
                enabled = !busy,
                onClick = { onAction(SettingsAction.RequestClearAllCaches) },
                titleColor = BasicComponentDefaults.titleColor(MiuixTheme.colorScheme.error),
            )
        }

        SmallTitle(
            text = stringResource(Res.string.settings_data_section),
            insideMargin = settingsSectionTitleMargin,
        )
        Card {
            if (state.capabilities.diagnosticsExportSupported) {
                ArrowPreference(
                    title = stringResource(Res.string.settings_export_diagnostics),
                    summary = stringResource(Res.string.settings_export_diagnostics_summary),
                    enabled = !busy,
                    onClick = { onAction(SettingsAction.ExportDiagnostics) },
                )
            }
            BasicComponent(
                title = stringResource(Res.string.settings_reset_defaults),
                summary = stringResource(Res.string.settings_reset_defaults_summary),
                enabled = !busy,
                onClick = { onAction(SettingsAction.RequestResetDefaults) },
                titleColor = BasicComponentDefaults.titleColor(MiuixTheme.colorScheme.error),
            )
        }

        if (state.capabilities.settingsBackupSupported) {
            SmallTitle(
                text = stringResource(Res.string.settings_backup_section),
                insideMargin = settingsSectionTitleMargin,
            )
            Card {
                SwitchPreference(
                    title = stringResource(Res.string.settings_backup_appearance),
                    checked = backup.selection.appearance,
                    onCheckedChange = {
                        onAction(
                            SettingsAction.SetBackupSettings(
                                backup.copy(selection = backup.selection.copy(appearance = it))
                            )
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(Res.string.settings_backup_playback),
                    checked = backup.selection.playback,
                    onCheckedChange = {
                        onAction(
                            SettingsAction.SetBackupSettings(
                                backup.copy(selection = backup.selection.copy(playback = it))
                            )
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(Res.string.settings_backup_lyrics),
                    checked = backup.selection.lyrics,
                    onCheckedChange = {
                        onAction(
                            SettingsAction.SetBackupSettings(
                                backup.copy(selection = backup.selection.copy(lyrics = it))
                            )
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(Res.string.settings_backup_library),
                    checked = backup.selection.libraryAndMetadata,
                    onCheckedChange = {
                        onAction(
                            SettingsAction.SetBackupSettings(
                                backup.copy(
                                    selection = backup.selection.copy(libraryAndMetadata = it)
                                )
                            )
                        )
                    },
                )
                SwitchPreference(
                    title = stringResource(Res.string.settings_backup_network),
                    checked = backup.selection.networkAndCache,
                    onCheckedChange = {
                        onAction(
                            SettingsAction.SetBackupSettings(
                                backup.copy(
                                    selection = backup.selection.copy(networkAndCache = it)
                                )
                            )
                        )
                    },
                )
                if (state.capabilities.scheduledBackupSupported) {
                    val schedules = BackupSchedule.entries.toList()
                    OverlayDropdownPreference(
                        title = stringResource(Res.string.settings_backup_schedule),
                        entries = listOf(DropdownEntry(
                            items = schedules.map { schedule ->
                                DropdownItem(
                                    text = stringResource(schedule.titleResource()),
                                    selected = schedule == backup.schedule,
                                    onClick = {
                                        onAction(
                                            SettingsAction.SetBackupSettings(
                                                backup.copy(schedule = schedule),
                                            ),
                                        )
                                    },
                                )
                            },
                        )),
                    )
                    val webDavAccounts = state.sourceAccounts
                        .filter(SourceAccountSettingsItem::isWebDav)
                        .mapNotNull { account ->
                            account.accountId.toStorageRouteIdOrNull()?.let { accountId ->
                                accountId to account.title
                            }
                        }
                    if (webDavAccounts.isNotEmpty()) {
                        val selectedAccount = webDavAccounts.firstOrNull {
                            it.first == backup.webDavAccountId
                        }
                        OverlayDropdownPreference(
                            title = stringResource(Res.string.settings_backup_webdav_account),
                            entries = listOf(DropdownEntry(
                                items = webDavAccounts.map { account ->
                                    DropdownItem(
                                        text = account.second,
                                        selected = account == selectedAccount,
                                        onClick = {
                                            onAction(
                                                SettingsAction.SetBackupSettings(
                                                    backup.copy(webDavAccountId = account.first),
                                                ),
                                            )
                                        },
                                    )
                                },
                            )),
                        )
                    }
                    ArrowPreference(
                        title = stringResource(Res.string.settings_backup_remote_directory),
                        summary = backup.remoteDirectory,
                        onClick = {
                            remoteDirectoryInput = backup.remoteDirectory
                            editingRemoteDirectory = true
                        },
                    )
                }
                ArrowPreference(
                    title = stringResource(Res.string.settings_backup_create),
                    summary = stringResource(Res.string.settings_backup_create_summary),
                    onClick = { onAction(SettingsAction.CreateSettingsBackup) },
                )
                ArrowPreference(
                    title = stringResource(Res.string.settings_backup_restore),
                    summary = stringResource(Res.string.settings_backup_restore_summary),
                    onClick = { onAction(SettingsAction.RestoreLatestSettingsBackup) },
                )
            }
        }

        SmallTitle(
            text = stringResource(Res.string.settings_danger_section),
            insideMargin = settingsSectionTitleMargin,
        )
        Card {
            BasicComponent(
                title = stringResource(Res.string.settings_clear_all_data),
                summary = stringResource(Res.string.settings_clear_all_data_summary),
                enabled = !busy,
                onClick = { onAction(SettingsAction.RequestClearAllData) },
                titleColor = BasicComponentDefaults.titleColor(MiuixTheme.colorScheme.error),
            )
            BasicComponent(
                title = stringResource(Res.string.settings_rebuild_library),
                summary = stringResource(Res.string.settings_rebuild_library_summary),
                enabled = !busy,
                onClick = { onAction(SettingsAction.RequestRebuildLibrary) },
                titleColor = BasicComponentDefaults.titleColor(MiuixTheme.colorScheme.error),
            )
            if (busy) {
                BasicComponent(
                    title = stringResource(Res.string.settings_operation_running),
                    summary = state.rebuildState.failureMessage.orEmpty(),
                )
            }
        }
    }

    SettingsConfirmDialog(
        show = state.pendingConfirmation == SettingsConfirmation.ClearAudio,
        title = stringResource(Res.string.settings_confirm_clear_audio_title),
        message = stringResource(Res.string.settings_confirm_clear_audio_message),
        confirmText = stringResource(Res.string.settings_confirm),
        onConfirm = { onAction(SettingsAction.ConfirmPendingAction) },
        onDismiss = { onAction(SettingsAction.DismissConfirmation) },
    )
    SettingsInputDialog(
        show = editingRemoteDirectory,
        title = stringResource(Res.string.settings_backup_remote_directory),
        message = stringResource(Res.string.settings_backup_remote_directory_summary),
        value = remoteDirectoryInput,
        label = stringResource(Res.string.settings_backup_remote_directory),
        onValueChange = { remoteDirectoryInput = it },
        onConfirm = {
            onAction(
                SettingsAction.SetBackupSettings(
                    backup.copy(remoteDirectory = remoteDirectoryInput.trim().ifBlank { "/" })
                )
            )
            editingRemoteDirectory = false
        },
        onDismiss = { editingRemoteDirectory = false },
    )
    SettingsConfirmDialog(
        show = state.pendingConfirmation == SettingsConfirmation.ClearImage,
        title = stringResource(Res.string.settings_confirm_clear_image_title),
        message = stringResource(Res.string.settings_confirm_clear_image_message),
        confirmText = stringResource(Res.string.settings_confirm),
        onConfirm = { onAction(SettingsAction.ConfirmPendingAction) },
        onDismiss = { onAction(SettingsAction.DismissConfirmation) },
    )
    SettingsConfirmDialog(
        show = state.pendingConfirmation == SettingsConfirmation.ClearAllCaches,
        title = stringResource(Res.string.settings_confirm_clear_all_title),
        message = stringResource(Res.string.settings_confirm_clear_all_message),
        confirmText = stringResource(Res.string.settings_confirm),
        onConfirm = { onAction(SettingsAction.ConfirmPendingAction) },
        onDismiss = { onAction(SettingsAction.DismissConfirmation) },
    )
    SettingsConfirmDialog(
        show = state.pendingConfirmation == SettingsConfirmation.ResetDefaults,
        title = stringResource(Res.string.settings_confirm_reset_title),
        message = stringResource(Res.string.settings_confirm_reset_message),
        confirmText = stringResource(Res.string.settings_confirm),
        onConfirm = { onAction(SettingsAction.ConfirmPendingAction) },
        onDismiss = { onAction(SettingsAction.DismissConfirmation) },
    )
    SettingsConfirmDialog(
        show = state.pendingConfirmation == SettingsConfirmation.ClearAllData,
        title = stringResource(Res.string.settings_confirm_clear_all_data_title),
        message = stringResource(Res.string.settings_confirm_clear_all_data_message),
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
}

private fun BackupSchedule.titleResource() = when (this) {
    BackupSchedule.Off -> Res.string.settings_backup_schedule_off
    BackupSchedule.Daily -> Res.string.settings_backup_schedule_daily
    BackupSchedule.Weekly -> Res.string.settings_backup_schedule_weekly
}
