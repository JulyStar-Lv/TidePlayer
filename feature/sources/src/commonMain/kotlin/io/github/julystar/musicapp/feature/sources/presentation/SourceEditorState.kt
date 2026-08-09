package io.github.julystar.musicapp.feature.sources.presentation

import androidx.compose.runtime.Immutable

@Immutable
data class SourceEditorState(
    val title: String = "",
    val musicCount: ULong = 0u,
    val isCreated: Boolean = true,
    val storageType: SourceEditorType = SourceEditorType.WebDav,
    val testStatus: SourceConnectionTestStatus = SourceConnectionTestStatus.None,
    val validation: SourceEditorValidation = SourceEditorValidation(),
    val removeDialogOpen: Boolean = false,
    val webDav: WebDavSourceEditorState = WebDavSourceEditorState(),
    val oneDrive: OneDriveSourceEditorState = OneDriveSourceEditorState(),
    val smb: SmbSourceEditorState = SmbSourceEditorState(),
)

@Immutable
data class SourceEditorValidation(
    val addressEmpty: Boolean = false,
    val aliasEmpty: Boolean = false,
    val usernameEmpty: Boolean = false,
    val passwordEmpty: Boolean = false,
    val smbPortInvalid: Boolean = false,
)

@Immutable
data class WebDavSourceEditorState(
    val alias: String = "",
    val address: String = "",
    val username: String = "",
    val isAnonymous: Boolean = false,
)

@Immutable
data class OneDriveSourceEditorState(
    val alias: String = "",
    val selectedDriveId: String = "",
    val connected: Boolean = false,
    val drives: List<SourceEditorDriveUi> = emptyList(),
    val drivesLoading: Boolean = false,
)

@Immutable
data class SmbSourceEditorState(
    val alias: String = "",
    val host: String = "",
    val port: String = "445",
    val share: String = "",
    val rootPath: String = "",
    val domain: String = "",
    val username: String = "",
    val isGuest: Boolean = false,
    val requireSigning: Boolean = false,
    val requireEncryption: Boolean = false,
)

@Immutable
data class SourceEditorDriveUi(
    val id: String,
    val name: String,
)

sealed interface SourceEditorAction {
    data object NavigateBack : SourceEditorAction
    data object TestConnection : SourceEditorAction
    data object Save : SourceEditorAction
    data object OpenRemoveDialog : SourceEditorAction
    data object CloseRemoveDialog : SourceEditorAction
    data object ConfirmRemove : SourceEditorAction
    data object ImportLibraryFolder : SourceEditorAction
    data class ChangeType(val storageType: SourceEditorType) : SourceEditorAction
    data class WebDavAnonymousChanged(val isAnonymous: Boolean) : SourceEditorAction
    data class WebDavAliasChanged(val value: String) : SourceEditorAction
    data class WebDavAddressChanged(val value: String) : SourceEditorAction
    data class WebDavUsernameChanged(val value: String) : SourceEditorAction
    data class WebDavPasswordChanged(val value: String) : SourceEditorAction
    data class OneDriveAliasChanged(val value: String) : SourceEditorAction
    data class SmbAliasChanged(val value: String) : SourceEditorAction
    data class SmbHostChanged(val value: String) : SourceEditorAction
    data class SmbPortChanged(val value: String) : SourceEditorAction
    data class SmbDomainChanged(val value: String) : SourceEditorAction
    data class SmbUsernameChanged(val value: String) : SourceEditorAction
    data class SmbPasswordChanged(val value: String) : SourceEditorAction
    data class SmbGuestChanged(val value: Boolean) : SourceEditorAction
    data class SmbSigningChanged(val value: Boolean) : SourceEditorAction
    data class SmbEncryptionChanged(val value: Boolean) : SourceEditorAction
    data object ConnectOneDrive : SourceEditorAction
    data object DisconnectOneDrive : SourceEditorAction
    data class SelectOneDriveDrive(val driveId: String) : SourceEditorAction
}

sealed interface SourceEditorEvent {
    data object NavigateBack : SourceEditorEvent
    data object OpenLibraryFolderImport : SourceEditorEvent
    data class OpenOneDriveOAuth(val authorizationUrl: String) : SourceEditorEvent
}
