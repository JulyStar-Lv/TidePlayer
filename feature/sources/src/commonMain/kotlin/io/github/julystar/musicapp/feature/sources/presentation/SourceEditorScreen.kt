package io.github.julystar.musicapp.feature.sources.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.components.LocalDesignBottomContentInset
import io.github.julystar.musicapp.core.presentation.components.LiquidGlassActionBar
import io.github.julystar.musicapp.core.presentation.components.TagChip
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import musicapp.core.presentation.generated.resources.Res as CoreRes
import musicapp.core.presentation.generated.resources.icon_visibility
import musicapp.core.presentation.generated.resources.icon_visibility_off
import musicapp.core.presentation.generated.resources.confirm_dialog_btn_cancel
import musicapp.core.presentation.generated.resources.confirm_dialog_btn_ok
import musicapp.core.presentation.generated.resources.confirm_dialog_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import musicapp.feature.sources.generated.resources.Res
import musicapp.feature.sources.generated.resources.icon_deleteseep
import musicapp.feature.sources.generated.resources.icon_ok
import musicapp.feature.sources.generated.resources.icon_wifitethering
import musicapp.feature.sources.generated.resources.source_editor_back
import musicapp.feature.sources.generated.resources.source_editor_delete
import musicapp.feature.sources.generated.resources.source_editor_save
import musicapp.feature.sources.generated.resources.source_editor_source
import musicapp.feature.sources.generated.resources.source_editor_test
import musicapp.feature.sources.generated.resources.storage_edit_addr
import musicapp.feature.sources.generated.resources.storage_edit_basic_settings
import musicapp.feature.sources.generated.resources.storage_edit_alias
import musicapp.feature.sources.generated.resources.storage_edit_anonymous
import musicapp.feature.sources.generated.resources.storage_edit_form_address
import musicapp.feature.sources.generated.resources.storage_edit_form_password
import musicapp.feature.sources.generated.resources.storage_edit_form_username
import musicapp.feature.sources.generated.resources.storage_edit_import_library_action
import musicapp.feature.sources.generated.resources.storage_edit_import_library_label
import musicapp.feature.sources.generated.resources.storage_edit_hide_advanced_settings
import musicapp.feature.sources.generated.resources.storage_edit_original
import musicapp.feature.sources.generated.resources.storage_edit_sync_library_action
import musicapp.feature.sources.generated.resources.storage_edit_sync_library_label
import musicapp.feature.sources.generated.resources.sources_syncing
import musicapp.feature.sources.generated.resources.storage_edit_oauth
import musicapp.feature.sources.generated.resources.storage_edit_onedrive_alias_not_empty
import musicapp.feature.sources.generated.resources.storage_edit_onedrive_connect
import musicapp.feature.sources.generated.resources.storage_edit_onedrive_disconnect
import musicapp.feature.sources.generated.resources.storage_edit_onedrive_drive
import musicapp.feature.sources.generated.resources.storage_edit_onedrive_drive_loading
import musicapp.feature.sources.generated.resources.storage_edit_onedrive_drive_required
import musicapp.feature.sources.generated.resources.storage_edit_onedrive_should_auth
import musicapp.feature.sources.generated.resources.storage_edit_password
import musicapp.feature.sources.generated.resources.storage_edit_primary_addr
import musicapp.feature.sources.generated.resources.storage_edit_secondary_addr
import musicapp.feature.sources.generated.resources.storage_edit_show_advanced_settings
import musicapp.feature.sources.generated.resources.storage_edit_stream_bitrate
import musicapp.feature.sources.generated.resources.storage_edit_download_bitrate
import musicapp.feature.sources.generated.resources.storage_edit_cover_size
import musicapp.feature.sources.generated.resources.storage_edit_remote_write
import musicapp.feature.sources.generated.resources.storage_edit_emby_server_name
import musicapp.feature.sources.generated.resources.storage_edit_emby_connected_account
import musicapp.feature.sources.generated.resources.storage_edit_emby_authentication
import musicapp.feature.sources.generated.resources.storage_edit_emby_authentication_hint
import musicapp.feature.sources.generated.resources.storage_edit_openlist_guest
import musicapp.feature.sources.generated.resources.storage_edit_openlist_otp
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
import musicapp.feature.sources.generated.resources.storage_test_otp_required
import musicapp.feature.sources.generated.resources.storage_test_permission
import musicapp.feature.sources.generated.resources.storage_test_success
import musicapp.feature.sources.generated.resources.storage_test_testing
import musicapp.feature.sources.generated.resources.storage_test_timeout
import musicapp.feature.sources.generated.resources.storage_test_unauthorized
import musicapp.feature.sources.generated.resources.storage_test_unavailable
import musicapp.feature.sources.generated.resources.storage_test_unsupported
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
private fun SourceEditorText(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    error: StringResource? = null,
    isPassword: Boolean = false,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    TextField(
        value = value,
        onValueChange = onChange,
        label = label,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (isPassword && !passwordVisible) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        trailingIcon = if (!isPassword) null else {
            {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        painter = painterResource(
                            if (passwordVisible) CoreRes.drawable.icon_visibility_off
                            else CoreRes.drawable.icon_visibility,
                        ),
                        contentDescription = null,
                    )
                }
            }
        },
    )
    error?.let {
        Text(
            text = stringResource(it),
            color = MiuixTheme.colorScheme.error,
            style = MiuixTheme.textStyles.footnote2,
        )
    }
}

@Composable
private fun SourceEditorField(
    label: String,
    block: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.footnote1,
        )
        block()
    }
}

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

    OverlayDialog(
        show = state.removeDialogOpen,
        title = stringResource(CoreRes.string.confirm_dialog_title),
        onDismissRequest = { onAction(SourceEditorAction.CloseRemoveDialog) },
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                text = stringResource(CoreRes.string.confirm_dialog_btn_cancel),
                onClick = { onAction(SourceEditorAction.CloseRemoveDialog) },
            )
            TextButton(
                text = stringResource(CoreRes.string.confirm_dialog_btn_ok),
                onClick = { onAction(SourceEditorAction.ConfirmRemove) },
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

    SwitchPreference(
        title = stringResource(Res.string.storage_edit_anonymous),
        checked = state.isAnonymous,
        onCheckedChange = { value ->
            onAction(SourceEditorAction.WebDavAnonymousChanged(value))
        },
    )
    SourceEditorText(
        label = stringResource(Res.string.storage_edit_alias),
        value = state.alias,
        onChange = { value ->
            onAction(SourceEditorAction.WebDavAliasChanged(value))
        },
    )
    SourceEditorText(
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
        SourceEditorText(
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
        SourceEditorText(
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
    state: RemoteServerSourceEditorState,
    validation: SourceEditorValidation,
    onAction: (SourceEditorAction) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var advancedExpanded by remember {
        mutableStateOf(RemoteServerConfigSection.Advanced.initiallyExpanded)
    }
    RemoteServerConfigSection.entries.forEach { section ->
        when (section) {
            RemoteServerConfigSection.Basic -> if (section.initiallyExpanded) {
                Text(
                    text = stringResource(Res.string.storage_edit_basic_settings),
                    color = MiuixTheme.colorScheme.onSurface,
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.SemiBold,
                )
                SourceEditorText(
                    label = stringResource(Res.string.storage_edit_alias),
                    value = state.alias,
                    onChange = { onAction(SourceEditorAction.RemoteServerAliasChanged(it)) },
                    error = if (validation.aliasEmpty) {
                        Res.string.storage_edit_onedrive_alias_not_empty
                    } else {
                        null
                    },
                )
                SourceEditorText(
                    label = stringResource(Res.string.storage_edit_primary_addr),
                    value = state.address,
                    onChange = { onAction(SourceEditorAction.RemoteServerAddressChanged(it)) },
                    error = if (validation.addressEmpty) Res.string.storage_edit_form_address else null,
                )
                SourceEditorText(
                    label = stringResource(Res.string.storage_edit_username),
                    value = state.username,
                    onChange = { onAction(SourceEditorAction.RemoteServerUsernameChanged(it)) },
                    error = if (validation.usernameEmpty) Res.string.storage_edit_form_username else null,
                )
                SourceEditorText(
                    label = stringResource(Res.string.storage_edit_password),
                    value = password,
                    isPassword = true,
                    onChange = {
                        password = it
                        onAction(SourceEditorAction.RemoteServerPasswordChanged(it))
                    },
                    error = if (validation.passwordEmpty) Res.string.storage_edit_form_password else null,
                )
            }

            RemoteServerConfigSection.Advanced -> {
                TextButton(
                    text = stringResource(
                        if (advancedExpanded) {
                            Res.string.storage_edit_hide_advanced_settings
                        } else {
                            Res.string.storage_edit_show_advanced_settings
                        }
                    ),
                    onClick = { advancedExpanded = !advancedExpanded },
                )
                if (advancedExpanded) {
                    SourceEditorText(
                        label = stringResource(Res.string.storage_edit_secondary_addr),
                        value = state.secondaryBaseUrl,
                        onChange = {
                            onAction(SourceEditorAction.RemoteServerSecondaryAddressChanged(it))
                        },
                    )
                    IntegerChoices(
                        label = stringResource(Res.string.storage_edit_stream_bitrate),
                        value = state.streamMaxBitRate,
                        values = sourceEditorBitRateChoices,
                        onChange = {
                            onAction(SourceEditorAction.RemoteServerStreamBitRateChanged(it))
                        },
                    )
                    IntegerChoices(
                        label = stringResource(Res.string.storage_edit_download_bitrate),
                        value = state.downloadMaxBitRate,
                        values = sourceEditorBitRateChoices,
                        onChange = {
                            onAction(SourceEditorAction.RemoteServerDownloadBitRateChanged(it))
                        },
                    )
                    IntegerChoices(
                        label = stringResource(Res.string.storage_edit_cover_size),
                        value = state.coverArtSize,
                        values = sourceEditorCoverArtSizeChoices,
                        onChange = {
                            onAction(SourceEditorAction.RemoteServerCoverArtSizeChanged(it))
                        },
                    )
                    SwitchPreference(
                        title = stringResource(Res.string.storage_edit_remote_write),
                        checked = state.remoteWriteEnabled,
                        onCheckedChange = { onAction(SourceEditorAction.RemoteServerWriteChanged(it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmbyConfig(
    state: EmbySourceEditorState,
    validation: SourceEditorValidation,
    isCreated: Boolean,
    onAction: (SourceEditorAction) -> Unit,
) {
    val contract = embyEditorContract(isCreated)
    var password by remember(isCreated) {
        mutableStateOf(contract.authenticationInputInitialValue)
    }
    if (SourceEditorField.Alias in contract.editableFields) {
        SourceEditorText(
            label = stringResource(Res.string.storage_edit_alias),
            value = state.alias,
            onChange = { onAction(SourceEditorAction.RemoteServerAliasChanged(it)) },
            error = if (validation.aliasEmpty) {
                Res.string.storage_edit_onedrive_alias_not_empty
            } else {
                null
            },
        )
    }
    if (SourceEditorField.Address in contract.editableFields) {
        SourceEditorText(
            label = stringResource(Res.string.storage_edit_primary_addr),
            value = state.address,
            onChange = { onAction(SourceEditorAction.RemoteServerAddressChanged(it)) },
            error = if (validation.addressEmpty) Res.string.storage_edit_form_address else null,
        )
    }
    if (SourceEditorField.Username in contract.editableFields) {
        SourceEditorText(
            label = stringResource(Res.string.storage_edit_username),
            value = state.username,
            onChange = { onAction(SourceEditorAction.RemoteServerUsernameChanged(it)) },
            error = if (validation.usernameEmpty) Res.string.storage_edit_form_username else null,
        )
    }
    if (SourceEditorField.Password in contract.editableFields) {
        SourceEditorText(
            label = stringResource(Res.string.storage_edit_emby_authentication),
            value = password,
            isPassword = true,
            onChange = {
                password = it
                onAction(SourceEditorAction.RemoteServerPasswordChanged(it))
            },
            error = if (validation.passwordEmpty) Res.string.storage_edit_form_password else null,
        )
        if (contract.showAuthenticationRetentionHint) {
            Text(
                text = stringResource(Res.string.storage_edit_emby_authentication_hint),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
    }
    if (SourceEditorField.SecondaryAddress in contract.editableFields) {
        SourceEditorText(
            label = stringResource(Res.string.storage_edit_secondary_addr),
            value = state.secondaryBaseUrl,
            onChange = { onAction(SourceEditorAction.RemoteServerSecondaryAddressChanged(it)) },
        )
    }
    if (SourceEditorField.ServerName in contract.readOnlyFields) {
        ReadOnlyValue(Res.string.storage_edit_emby_server_name, state.serverName)
    }
    if (SourceEditorField.ConnectedAccount in contract.readOnlyFields) {
        ReadOnlyValue(Res.string.storage_edit_emby_connected_account, state.connectedUserId)
    }
}

@Composable
private fun OpenListConfig(
    state: OpenListSourceEditorState,
    validation: SourceEditorValidation,
    otpInputGeneration: Int,
    onAction: (SourceEditorAction) -> Unit,
) {
    val contract = openListEditorContract(state.isGuest, state.showOtp)
    var password by remember { mutableStateOf("") }
    var otpCode by remember(otpInputGeneration) {
        mutableStateOf(contract.otpInputInitialValue)
    }
    if (SourceEditorField.Alias in contract.visibleFields) {
        SourceEditorText(
            label = stringResource(Res.string.storage_edit_alias),
            value = state.alias,
            onChange = { onAction(SourceEditorAction.OpenListAliasChanged(it)) },
            error = if (validation.aliasEmpty) {
                Res.string.storage_edit_onedrive_alias_not_empty
            } else {
                null
            },
        )
    }
    if (SourceEditorField.Address in contract.visibleFields) {
        SourceEditorText(
            label = stringResource(Res.string.storage_edit_addr),
            value = state.address,
            onChange = { onAction(SourceEditorAction.OpenListAddressChanged(it)) },
            error = if (validation.addressEmpty) Res.string.storage_edit_form_address else null,
        )
    }
    if (SourceEditorField.Guest in contract.visibleFields) {
        SwitchPreference(
            title = stringResource(Res.string.storage_edit_openlist_guest),
            checked = state.isGuest,
            onCheckedChange = { isGuest ->
                if (isGuest) password = ""
                onAction(SourceEditorAction.OpenListGuestChanged(isGuest))
            },
        )
    }
    if (SourceEditorField.Username in contract.visibleFields) {
        SourceEditorText(
            label = stringResource(Res.string.storage_edit_username),
            value = state.username,
            onChange = { onAction(SourceEditorAction.OpenListUsernameChanged(it)) },
            error = if (validation.usernameEmpty) Res.string.storage_edit_form_username else null,
        )
    }
    if (SourceEditorField.Password in contract.visibleFields) {
        SourceEditorText(
            label = stringResource(Res.string.storage_edit_password),
            value = password,
            isPassword = true,
            onChange = {
                password = it
                onAction(SourceEditorAction.OpenListPasswordChanged(it))
            },
            error = if (validation.passwordEmpty) Res.string.storage_edit_form_password else null,
        )
    }
    if (SourceEditorField.Otp in contract.visibleFields) {
        SourceEditorText(
            label = stringResource(Res.string.storage_edit_openlist_otp),
            value = otpCode,
            isPassword = true,
            onChange = {
                otpCode = it
                onAction(SourceEditorAction.OpenListOtpChanged(it))
            },
        )
    }
}

@Composable
private fun IntegerChoices(
    label: String,
    value: Int,
    values: List<Int>,
    onChange: (Int) -> Unit,
) {
    SourceEditorField(label = label) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            values.chunked(3).forEach { rowValues ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowValues.forEach { option ->
                        TagChip(
                            label = if (option == 0) {
                                stringResource(Res.string.storage_edit_original)
                            } else {
                                option.toString()
                            },
                            selected = option == value,
                            onClick = { onChange(option) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyValue(label: org.jetbrains.compose.resources.StringResource, value: String) {
    if (value.isBlank()) return
    SourceEditorField(label = stringResource(label)) {
        Text(
            text = value,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body1,
        )
    }
}

@Composable
private fun SmbConfig(
    state: SmbSourceEditorState,
    validation: SourceEditorValidation,
    onAction: (SourceEditorAction) -> Unit,
) {
    var password by remember { mutableStateOf("") }

    SwitchPreference(
        title = stringResource(Res.string.storage_edit_smb_guest),
        checked = state.isGuest,
        onCheckedChange = { isGuest ->
            if (isGuest) password = ""
            onAction(SourceEditorAction.SmbGuestChanged(isGuest))
        },
    )
    SourceEditorText(
        label = stringResource(Res.string.storage_edit_alias),
        value = state.alias,
        onChange = { onAction(SourceEditorAction.SmbAliasChanged(it)) },
        error = if (validation.aliasEmpty) {
            Res.string.storage_edit_onedrive_alias_not_empty
        } else {
            null
        },
    )
    SourceEditorText(
        label = stringResource(Res.string.storage_edit_smb_server),
        value = state.host,
        onChange = { onAction(SourceEditorAction.SmbHostChanged(it)) },
        error = if (validation.addressEmpty) {
            Res.string.storage_edit_form_address
        } else {
            null
        },
    )
    SourceEditorText(
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
        SourceEditorText(
            label = stringResource(Res.string.storage_edit_username),
            value = state.username,
            onChange = { onAction(SourceEditorAction.SmbUsernameChanged(it)) },
            error = if (validation.usernameEmpty) {
                Res.string.storage_edit_form_username
            } else {
                null
            },
        )
        SourceEditorText(
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
        SourceEditorText(
            label = stringResource(Res.string.storage_edit_smb_domain),
            value = state.domain,
            onChange = { onAction(SourceEditorAction.SmbDomainChanged(it)) },
        )
    }
    SwitchPreference(
        title = stringResource(Res.string.storage_edit_smb_signing),
        checked = state.requireSigning,
        onCheckedChange = { onAction(SourceEditorAction.SmbSigningChanged(it)) },
    )
    SwitchPreference(
        title = stringResource(Res.string.storage_edit_smb_encryption),
        checked = state.requireEncryption,
        onCheckedChange = { onAction(SourceEditorAction.SmbEncryptionChanged(it)) },
    )
}

@Composable
private fun OneDriveConfig(
    state: OneDriveSourceEditorState,
    validation: SourceEditorValidation,
    onAction: (SourceEditorAction) -> Unit,
) {
    SourceEditorText(
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
    SourceEditorField(
        label = stringResource(Res.string.storage_edit_oauth),
    ) {
        if (!state.connected) {
            TextButton(
                text = stringResource(Res.string.storage_edit_onedrive_connect),
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
            TextButton(
                text = stringResource(Res.string.storage_edit_onedrive_disconnect),
                onClick = {
                    onAction(SourceEditorAction.DisconnectOneDrive)
                },
            )
        }
    }
    if (state.connected) {
        SourceEditorField(
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
                    TextButton(
                        text = drive.name,
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
    val bottomContentInset = LocalDesignBottomContentInset.current

    val testTint = when (state.testStatus) {
        SourceConnectionTestStatus.None -> MiuixTheme.colorScheme.onSurface
        SourceConnectionTestStatus.Testing -> MiuixTheme.colorScheme.onTertiaryContainer
        SourceConnectionTestStatus.Success -> MiuixTheme.colorScheme.primary
        SourceConnectionTestStatus.Unauthorized,
        SourceConnectionTestStatus.OtpRequired,
        SourceConnectionTestStatus.Timeout,
        SourceConnectionTestStatus.PermissionDenied,
        SourceConnectionTestStatus.NotFound,
        SourceConnectionTestStatus.InvalidAddress,
        SourceConnectionTestStatus.Unavailable,
        SourceConnectionTestStatus.UnsupportedSecurityPolicy,
        SourceConnectionTestStatus.Error -> MiuixTheme.colorScheme.error
    }
    val testStatusText = when (state.testStatus) {
        SourceConnectionTestStatus.None -> null
        SourceConnectionTestStatus.Testing -> stringResource(Res.string.storage_test_testing)
        SourceConnectionTestStatus.Success -> stringResource(Res.string.storage_test_success)
        SourceConnectionTestStatus.Unauthorized -> stringResource(Res.string.storage_test_unauthorized)
        SourceConnectionTestStatus.OtpRequired -> stringResource(Res.string.storage_test_otp_required)
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            LiquidGlassActionBar(
                title = state.title.ifBlank { stringResource(Res.string.source_editor_source) },
                collapseFraction = 1f,
                onNavigateBack = { onAction(SourceEditorAction.NavigateBack) },
                backContentDescription = stringResource(Res.string.source_editor_back),
                actions = {
                    if (!state.isCreated) {
                        IconButton(
                            onClick = { onAction(SourceEditorAction.OpenRemoveDialog) },
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.icon_deleteseep),
                                contentDescription = stringResource(Res.string.source_editor_delete),
                                tint = MiuixTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    IconButton(
                        enabled = state.testStatus != SourceConnectionTestStatus.Testing,
                        onClick = { onAction(SourceEditorAction.TestConnection) },
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.icon_wifitethering),
                            contentDescription = stringResource(Res.string.source_editor_test),
                            tint = testTint,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(
                        onClick = { onAction(SourceEditorAction.Save) },
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.icon_ok),
                            contentDescription = stringResource(Res.string.source_editor_save),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            val horizontalPadding = if (maxWidth < 600.dp) {
                spacing.pageCompact
            } else {
                spacing.pageMedium
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(
                        start = horizontalPadding,
                        top = 12.dp,
                        end = horizontalPadding,
                        bottom = 12.dp + bottomContentInset,
                    ),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                        if (storageType == SourceEditorType.Navidrome ||
                            storageType == SourceEditorType.OpenSubsonic
                        ) {
                            key(storageType) {
                                RemoteServerConfig(
                                    state = state.remoteServer,
                                    validation = state.validation,
                                    onAction = onAction,
                                )
                            }
                        }
                        if (storageType == SourceEditorType.Emby) {
                            key(storageType) {
                                EmbyConfig(
                                    state = state.emby,
                                    validation = state.validation,
                                    isCreated = state.isCreated,
                                    onAction = onAction,
                                )
                            }
                        }
                        if (storageType == SourceEditorType.OpenList) {
                            key(storageType) {
                                OpenListConfig(
                                    state = state.openList,
                                    validation = state.validation,
                                    otpInputGeneration = state.otpInputGeneration,
                                    onAction = onAction,
                                )
                            }
                        }
                        if (!state.isCreated && (
                            storageType == SourceEditorType.WebDav ||
                            storageType == SourceEditorType.OneDrive ||
                            storageType == SourceEditorType.Smb ||
                            storageType == SourceEditorType.OpenList
                        )) {
                            SourceEditorField(
                                label = stringResource(
                                    Res.string.storage_edit_import_library_label,
                                ),
                            ) {
                                TextButton(
                                    text = stringResource(
                                        Res.string.storage_edit_import_library_action,
                                    ),
                                    onClick = {
                                        onAction(SourceEditorAction.ImportLibraryFolder)
                                    },
                                )
                            }
                        }
                        if (state.canSyncCurrentServer) {
                            SourceEditorField(
                                label = stringResource(
                                    Res.string.storage_edit_sync_library_label,
                                ),
                            ) {
                                TextButton(
                                    text = stringResource(
                                        if (state.isSyncing) {
                                            Res.string.sources_syncing
                                        } else {
                                            Res.string.storage_edit_sync_library_action
                                        },
                                    ),
                                    enabled = !state.isSyncing,
                                    onClick = { onAction(SourceEditorAction.SyncNow) },
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
        RemoveDialog(
            state = state,
            onAction = onAction,
        )
    }
}
