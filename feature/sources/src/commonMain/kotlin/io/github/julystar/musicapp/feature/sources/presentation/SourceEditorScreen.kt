package io.github.julystar.musicapp.feature.sources.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.components.ConfirmDialog
import io.github.julystar.musicapp.core.presentation.components.FormSwitch
import io.github.julystar.musicapp.core.presentation.components.FormText
import io.github.julystar.musicapp.core.presentation.components.FormWidget
import io.github.julystar.musicapp.core.presentation.components.LocalDesignBottomContentInset
import io.github.julystar.musicapp.core.presentation.components.DesignCardSurface
import io.github.julystar.musicapp.core.presentation.components.DesignIconButton
import io.github.julystar.musicapp.core.presentation.components.DesignIconButtonColors
import io.github.julystar.musicapp.core.presentation.components.DesignIconButtonSize
import io.github.julystar.musicapp.core.presentation.components.DesignIconButtonVariant
import io.github.julystar.musicapp.core.presentation.components.DesignTextButton
import io.github.julystar.musicapp.core.presentation.components.DesignTextButtonSize
import io.github.julystar.musicapp.core.presentation.components.DesignTextButtonVariant
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import musicapp.feature.sources.generated.resources.Res
import musicapp.feature.sources.generated.resources.icon_back
import musicapp.feature.sources.generated.resources.icon_cloud
import musicapp.feature.sources.generated.resources.icon_deleteseep
import musicapp.feature.sources.generated.resources.icon_ok
import musicapp.feature.sources.generated.resources.icon_wifitethering
import musicapp.feature.sources.generated.resources.storage_edit_addr
import musicapp.feature.sources.generated.resources.storage_edit_alias
import musicapp.feature.sources.generated.resources.storage_edit_anonymous
import musicapp.feature.sources.generated.resources.storage_edit_form_address
import musicapp.feature.sources.generated.resources.storage_edit_form_password
import musicapp.feature.sources.generated.resources.storage_edit_form_username
import musicapp.feature.sources.generated.resources.storage_edit_import_library_action
import musicapp.feature.sources.generated.resources.storage_edit_import_library_label
import musicapp.feature.sources.generated.resources.storage_edit_oauth
import musicapp.feature.sources.generated.resources.storage_edit_onedrive_alias_not_empty
import musicapp.feature.sources.generated.resources.storage_edit_onedrive_connect
import musicapp.feature.sources.generated.resources.storage_edit_onedrive_disconnect
import musicapp.feature.sources.generated.resources.storage_edit_onedrive_drive
import musicapp.feature.sources.generated.resources.storage_edit_onedrive_drive_loading
import musicapp.feature.sources.generated.resources.storage_edit_onedrive_drive_required
import musicapp.feature.sources.generated.resources.storage_edit_onedrive_should_auth
import musicapp.feature.sources.generated.resources.storage_edit_password
import musicapp.feature.sources.generated.resources.storage_edit_username
import musicapp.feature.sources.generated.resources.storage_edit_smb_domain
import musicapp.feature.sources.generated.resources.storage_edit_smb_encryption
import musicapp.feature.sources.generated.resources.storage_edit_smb_guest
import musicapp.feature.sources.generated.resources.storage_edit_smb_port
import musicapp.feature.sources.generated.resources.storage_edit_smb_port_invalid
import musicapp.feature.sources.generated.resources.storage_edit_smb_server
import musicapp.feature.sources.generated.resources.storage_edit_smb_signing
import musicapp.feature.sources.generated.resources.storage_remove_desc_count
import musicapp.feature.sources.generated.resources.storage_remove_desc_downloads
import musicapp.feature.sources.generated.resources.storage_remove_desc_main
import musicapp.feature.sources.generated.resources.storage_test_error
import musicapp.feature.sources.generated.resources.storage_test_invalid_address
import musicapp.feature.sources.generated.resources.storage_test_not_found
import musicapp.feature.sources.generated.resources.storage_test_permission
import musicapp.feature.sources.generated.resources.storage_test_success
import musicapp.feature.sources.generated.resources.storage_test_testing
import musicapp.feature.sources.generated.resources.storage_test_timeout
import musicapp.feature.sources.generated.resources.storage_test_unauthorized
import musicapp.feature.sources.generated.resources.storage_test_unavailable
import musicapp.feature.sources.generated.resources.storage_test_unsupported
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private fun buildStr(s: String): AnnotatedString {
    val spans = s.split("$$")

    return buildAnnotatedString {
        for (span in spans) {
            if (span.startsWith("B__")) {
                withStyle(style = SpanStyle(fontWeight = FontWeight(700))) {
                    append(span.slice("B__".length until span.length))
                }
            } else {
                append(span)
            }
        }
    }
}

@Composable
private fun RemoveDialog(
    state: SourceEditorState,
    onAction: (SourceEditorAction) -> Unit,
) {
    val mainDesc = buildStr(
        stringResource(Res.string.storage_remove_desc_main)
            .replace("E_TITLE", state.title)
    )
    val countDesc = buildStr(
        stringResource(Res.string.storage_remove_desc_count)
            .replace("E_MCNT", state.musicCount.toString())
    )

    ConfirmDialog(
        open = state.removeDialogOpen,
        onConfirm = {
            onAction(SourceEditorAction.ConfirmRemove)
        },
        onCancel = {
            onAction(SourceEditorAction.CloseRemoveDialog)
        },
    ) {
        Text(
            text = mainDesc,
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.body1,
        )
        Text(
            text = countDesc,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
        )
        Text(
            text = stringResource(Res.string.storage_remove_desc_downloads),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
        )
    }
}

@Composable
private fun StorageBlock(
    title: String,
    isActive: Boolean,
    onSelect: () -> Unit,
) {
    val shapes = DesignTokens.shapes
    val bgColor = if (isActive) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.surfaceContainer
    }
    val tint = if (isActive) {
        MiuixTheme.colorScheme.onPrimary
    } else {
        MiuixTheme.colorScheme.onSurface
    }
    val borderColor = if (isActive) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.22f)
    } else {
        MiuixTheme.colorScheme.outline
    }

    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(RoundedCornerShape(shapes.lg))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(shapes.lg))
            .clickable { onSelect() }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Icon(
                painter = painterResource(Res.drawable.icon_cloud),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = title,
                color = tint,
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WebDavConfig(
    state: WebDavSourceEditorState,
    validation: SourceEditorValidation,
    onAction: (SourceEditorAction) -> Unit,
) {
    var password by remember { mutableStateOf("") }

    FormSwitch(
        label = stringResource(Res.string.storage_edit_anonymous),
        value = state.isAnonymous,
        onChange = { value ->
            onAction(SourceEditorAction.WebDavAnonymousChanged(value))
        },
    )
    FormText(
        label = stringResource(Res.string.storage_edit_alias),
        value = state.alias,
        onChange = { value ->
            onAction(SourceEditorAction.WebDavAliasChanged(value))
        },
    )
    FormText(
        label = stringResource(Res.string.storage_edit_addr),
        value = state.address,
        onChange = { value ->
            onAction(SourceEditorAction.WebDavAddressChanged(value))
        },
        error = if (validation.addressEmpty) {
            Res.string.storage_edit_form_address
        } else {
            null
        },
    )
    if (!state.isAnonymous) {
        FormText(
            label = stringResource(Res.string.storage_edit_username),
            value = state.username,
            onChange = { value ->
                onAction(SourceEditorAction.WebDavUsernameChanged(value))
            },
            error = if (validation.usernameEmpty) {
                Res.string.storage_edit_form_username
            } else {
                null
            },
        )
        FormText(
            label = stringResource(Res.string.storage_edit_password),
            value = password,
            isPassword = true,
            onChange = { value ->
                password = value
                onAction(SourceEditorAction.WebDavPasswordChanged(value))
            },
            error = if (validation.passwordEmpty) {
                Res.string.storage_edit_form_password
            } else {
                null
            },
        )
    }
}

@Composable
private fun RemoteServerConfig(
    state: WebDavSourceEditorState,
    validation: SourceEditorValidation,
    onAction: (SourceEditorAction) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    FormText(
        label = stringResource(Res.string.storage_edit_alias),
        value = state.alias,
        onChange = { onAction(SourceEditorAction.WebDavAliasChanged(it)) },
        error = if (validation.aliasEmpty) Res.string.storage_edit_onedrive_alias_not_empty else null,
    )
    FormText(
        label = stringResource(Res.string.storage_edit_addr),
        value = state.address,
        onChange = { onAction(SourceEditorAction.WebDavAddressChanged(it)) },
        error = if (validation.addressEmpty) Res.string.storage_edit_form_address else null,
    )
    FormText(
        label = stringResource(Res.string.storage_edit_username),
        value = state.username,
        onChange = { onAction(SourceEditorAction.WebDavUsernameChanged(it)) },
        error = if (validation.usernameEmpty) Res.string.storage_edit_form_username else null,
    )
    FormText(
        label = stringResource(Res.string.storage_edit_password),
        value = password,
        isPassword = true,
        onChange = {
            password = it
            onAction(SourceEditorAction.WebDavPasswordChanged(it))
        },
        error = if (validation.passwordEmpty) Res.string.storage_edit_form_password else null,
    )
}

@Composable
private fun SmbConfig(
    state: SmbSourceEditorState,
    validation: SourceEditorValidation,
    onAction: (SourceEditorAction) -> Unit,
) {
    var password by remember { mutableStateOf("") }

    FormSwitch(
        label = stringResource(Res.string.storage_edit_smb_guest),
        value = state.isGuest,
        onChange = { isGuest ->
            if (isGuest) password = ""
            onAction(SourceEditorAction.SmbGuestChanged(isGuest))
        },
    )
    FormText(
        label = stringResource(Res.string.storage_edit_alias),
        value = state.alias,
        onChange = { onAction(SourceEditorAction.SmbAliasChanged(it)) },
        error = if (validation.aliasEmpty) {
            Res.string.storage_edit_onedrive_alias_not_empty
        } else {
            null
        },
    )
    FormText(
        label = stringResource(Res.string.storage_edit_smb_server),
        value = state.host,
        onChange = { onAction(SourceEditorAction.SmbHostChanged(it)) },
        error = if (validation.addressEmpty) {
            Res.string.storage_edit_form_address
        } else {
            null
        },
    )
    FormText(
        label = stringResource(Res.string.storage_edit_smb_port),
        value = state.port,
        onChange = { onAction(SourceEditorAction.SmbPortChanged(it)) },
        error = if (validation.smbPortInvalid) {
            Res.string.storage_edit_smb_port_invalid
        } else {
            null
        },
    )
    if (!state.isGuest) {
        FormText(
            label = stringResource(Res.string.storage_edit_username),
            value = state.username,
            onChange = { onAction(SourceEditorAction.SmbUsernameChanged(it)) },
            error = if (validation.usernameEmpty) {
                Res.string.storage_edit_form_username
            } else {
                null
            },
        )
        FormText(
            label = stringResource(Res.string.storage_edit_password),
            value = password,
            isPassword = true,
            onChange = {
                password = it
                onAction(SourceEditorAction.SmbPasswordChanged(it))
            },
            error = if (validation.passwordEmpty) {
                Res.string.storage_edit_form_password
            } else {
                null
            },
        )
        FormText(
            label = stringResource(Res.string.storage_edit_smb_domain),
            value = state.domain,
            onChange = { onAction(SourceEditorAction.SmbDomainChanged(it)) },
        )
    }
    FormSwitch(
        label = stringResource(Res.string.storage_edit_smb_signing),
        value = state.requireSigning,
        onChange = { onAction(SourceEditorAction.SmbSigningChanged(it)) },
    )
    FormSwitch(
        label = stringResource(Res.string.storage_edit_smb_encryption),
        value = state.requireEncryption,
        onChange = { onAction(SourceEditorAction.SmbEncryptionChanged(it)) },
    )
}

@Composable
private fun OneDriveConfig(
    state: OneDriveSourceEditorState,
    validation: SourceEditorValidation,
    onAction: (SourceEditorAction) -> Unit,
) {
    FormText(
        label = stringResource(Res.string.storage_edit_alias),
        value = state.alias,
        onChange = { value ->
            onAction(SourceEditorAction.OneDriveAliasChanged(value))
        },
        error = if (validation.aliasEmpty) {
            Res.string.storage_edit_onedrive_alias_not_empty
        } else {
            null
        },
    )
    FormWidget(
        label = stringResource(Res.string.storage_edit_oauth),
    ) {
        if (!state.connected) {
            DesignTextButton(
                text = stringResource(Res.string.storage_edit_onedrive_connect),
                variant = DesignTextButtonVariant.PrimaryFilled,
                size = DesignTextButtonSize.Medium,
                onClick = {
                    onAction(SourceEditorAction.ConnectOneDrive)
                },
            )
            if (validation.passwordEmpty) {
                Text(
                    modifier = Modifier.padding(
                        horizontal = 0.dp,
                        vertical = 2.dp,
                    ),
                    text = stringResource(Res.string.storage_edit_onedrive_should_auth),
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
        }
        if (state.connected) {
            DesignTextButton(
                text = stringResource(Res.string.storage_edit_onedrive_disconnect),
                variant = DesignTextButtonVariant.Error,
                size = DesignTextButtonSize.Medium,
                onClick = {
                    onAction(SourceEditorAction.DisconnectOneDrive)
                },
            )
        }
    }
    if (state.connected) {
        FormWidget(
            label = stringResource(Res.string.storage_edit_onedrive_drive),
        ) {
            Column {
                if (state.drivesLoading) {
                    Text(
                        text = stringResource(Res.string.storage_edit_onedrive_drive_loading),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                    )
                }
                state.drives.forEach { drive ->
                    val selected = drive.id == state.selectedDriveId
                    DesignTextButton(
                        text = drive.name,
                        variant = if (selected) {
                            DesignTextButtonVariant.Primary
                        } else {
                            DesignTextButtonVariant.Default
                        },
                        size = DesignTextButtonSize.Medium,
                        onClick = {
                            onAction(SourceEditorAction.SelectOneDriveDrive(drive.id))
                        },
                    )
                }
                if (validation.addressEmpty) {
                    Text(
                        text = stringResource(Res.string.storage_edit_onedrive_drive_required),
                        color = MiuixTheme.colorScheme.error,
                        style = MiuixTheme.textStyles.footnote1,
                    )
                }
            }
        }
    }
}

@Composable
fun SourceEditorScreen(
    state: SourceEditorState,
    onAction: (SourceEditorAction) -> Unit,
) {
    val storageType = state.storageType
    val spacing = DesignTokens.spacing
    val shapes = DesignTokens.shapes
    val bottomContentInset = LocalDesignBottomContentInset.current

    val testingColors = when (state.testStatus) {
        SourceConnectionTestStatus.None -> null
        SourceConnectionTestStatus.Testing -> DesignIconButtonColors(
            buttonBg = Color.Transparent,
            iconTint = MiuixTheme.colorScheme.onTertiaryContainer,
        )
        SourceConnectionTestStatus.Success -> DesignIconButtonColors(
            buttonBg = Color.Transparent,
            iconTint = MiuixTheme.colorScheme.primary,
        )
        SourceConnectionTestStatus.Unauthorized,
        SourceConnectionTestStatus.Timeout,
        SourceConnectionTestStatus.PermissionDenied,
        SourceConnectionTestStatus.NotFound,
        SourceConnectionTestStatus.InvalidAddress,
        SourceConnectionTestStatus.Unavailable,
        SourceConnectionTestStatus.UnsupportedSecurityPolicy,
        SourceConnectionTestStatus.Error -> DesignIconButtonColors(
            buttonBg = Color.Transparent,
            iconTint = MiuixTheme.colorScheme.error,
        )
    }
    val testStatusText = when (state.testStatus) {
        SourceConnectionTestStatus.None -> null
        SourceConnectionTestStatus.Testing -> stringResource(Res.string.storage_test_testing)
        SourceConnectionTestStatus.Success -> stringResource(Res.string.storage_test_success)
        SourceConnectionTestStatus.Unauthorized -> stringResource(Res.string.storage_test_unauthorized)
        SourceConnectionTestStatus.Timeout -> stringResource(Res.string.storage_test_timeout)
        SourceConnectionTestStatus.PermissionDenied -> stringResource(Res.string.storage_test_permission)
        SourceConnectionTestStatus.NotFound -> stringResource(Res.string.storage_test_not_found)
        SourceConnectionTestStatus.InvalidAddress -> stringResource(Res.string.storage_test_invalid_address)
        SourceConnectionTestStatus.Unavailable -> stringResource(Res.string.storage_test_unavailable)
        SourceConnectionTestStatus.UnsupportedSecurityPolicy -> {
            stringResource(Res.string.storage_test_unsupported)
        }
        SourceConnectionTestStatus.Error -> stringResource(Res.string.storage_test_error)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val horizontalPadding = if (maxWidth < 600.dp) spacing.pageCompact else spacing.pageMedium

        Column(
            modifier = Modifier
                .background(MiuixTheme.colorScheme.background)
                .fillMaxSize(),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = horizontalPadding, vertical = 12.dp)
                    .fillMaxWidth(),
            ) {
                DesignIconButton(
                    size = DesignIconButtonSize.Medium,
                    variant = DesignIconButtonVariant.Default,
                    painter = painterResource(Res.drawable.icon_back),
                    onClick = {
                        onAction(SourceEditorAction.NavigateBack)
                    },
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = state.title.ifBlank { "Source" },
                        color = MiuixTheme.colorScheme.onBackground,
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (state.isCreated) "New source" else "Edit source",
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                        style = MiuixTheme.textStyles.footnote1,
                        maxLines = 1,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!state.isCreated) {
                        DesignIconButton(
                            size = DesignIconButtonSize.Medium,
                            variant = DesignIconButtonVariant.Error,
                            painter = painterResource(Res.drawable.icon_deleteseep),
                            onClick = {
                                onAction(SourceEditorAction.OpenRemoveDialog)
                            },
                        )
                    }
                    DesignIconButton(
                        size = DesignIconButtonSize.Medium,
                        variant = DesignIconButtonVariant.Default,
                        enabled = state.testStatus != SourceConnectionTestStatus.Testing,
                        painter = painterResource(Res.drawable.icon_wifitethering),
                        colors = testingColors,
                        onClick = {
                            onAction(SourceEditorAction.TestConnection)
                        },
                    )
                    DesignIconButton(
                        size = DesignIconButtonSize.Medium,
                        variant = DesignIconButtonVariant.Default,
                        painter = painterResource(Res.drawable.icon_ok),
                        onClick = {
                            onAction(SourceEditorAction.Save)
                        },
                    )
                }
            }
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(
                            start = horizontalPadding,
                            top = 12.dp,
                            end = horizontalPadding,
                            bottom = 12.dp + bottomContentInset,
                        ),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StorageBlock(
                            title = "WebDAV",
                            isActive = storageType == SourceEditorType.WebDav,
                            onSelect = {
                                onAction(SourceEditorAction.ChangeType(SourceEditorType.WebDav))
                            },
                        )
                        StorageBlock(
                            title = "OneDrive",
                            isActive = storageType == SourceEditorType.OneDrive,
                            onSelect = {
                                onAction(SourceEditorAction.ChangeType(SourceEditorType.OneDrive))
                            },
                        )
                        StorageBlock(
                            title = "SMB",
                            isActive = storageType == SourceEditorType.Smb,
                            onSelect = {
                                onAction(SourceEditorAction.ChangeType(SourceEditorType.Smb))
                            },
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StorageBlock(
                            title = "Navidrome",
                            isActive = storageType == SourceEditorType.Navidrome,
                            onSelect = { onAction(SourceEditorAction.ChangeType(SourceEditorType.Navidrome)) },
                        )
                        StorageBlock(
                            title = "OpenSubsonic",
                            isActive = storageType == SourceEditorType.OpenSubsonic,
                            onSelect = { onAction(SourceEditorAction.ChangeType(SourceEditorType.OpenSubsonic)) },
                        )
                        StorageBlock(
                            title = "Emby",
                            isActive = storageType == SourceEditorType.Emby,
                            onSelect = { onAction(SourceEditorAction.ChangeType(SourceEditorType.Emby)) },
                        )
                    }
                    DesignCardSurface(
                        cornerRadius = shapes.xl,
                        contentPadding = PaddingValues(16.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (storageType == SourceEditorType.WebDav) {
                                WebDavConfig(
                                    state = state.webDav,
                                    validation = state.validation,
                                    onAction = onAction,
                                )
                            }
                            if (storageType == SourceEditorType.OneDrive) {
                                OneDriveConfig(
                                    state = state.oneDrive,
                                    validation = state.validation,
                                    onAction = onAction,
                                )
                            }
                            if (storageType == SourceEditorType.Smb) {
                                SmbConfig(
                                    state = state.smb,
                                    validation = state.validation,
                                    onAction = onAction,
                                )
                            }
                            if (
                                storageType == SourceEditorType.Navidrome ||
                                storageType == SourceEditorType.OpenSubsonic ||
                                storageType == SourceEditorType.Emby
                            ) {
                                RemoteServerConfig(
                                    state = state.webDav,
                                    validation = state.validation,
                                    onAction = onAction,
                                )
                            }
                            if (!state.isCreated && (
                                storageType == SourceEditorType.WebDav ||
                                storageType == SourceEditorType.OneDrive ||
                                storageType == SourceEditorType.Smb
                            )) {
                                FormWidget(
                                    label = stringResource(Res.string.storage_edit_import_library_label),
                                ) {
                                    DesignTextButton(
                                        text = stringResource(Res.string.storage_edit_import_library_action),
                                        variant = DesignTextButtonVariant.PrimaryFilled,
                                        size = DesignTextButtonSize.Medium,
                                        onClick = {
                                            onAction(SourceEditorAction.ImportLibraryFolder)
                                        },
                                    )
                                }
                            }
                            if (testStatusText != null) {
                                Text(
                                    text = testStatusText,
                                    color = if (
                                        state.testStatus == SourceConnectionTestStatus.Success
                                    ) {
                                        MiuixTheme.colorScheme.primary
                                    } else {
                                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    },
                                    style = MiuixTheme.textStyles.footnote1,
                                )
                            }
                        }
                    }
                }
            }
        }
        RemoveDialog(
            state = state,
            onAction = onAction,
        )
    }
}
