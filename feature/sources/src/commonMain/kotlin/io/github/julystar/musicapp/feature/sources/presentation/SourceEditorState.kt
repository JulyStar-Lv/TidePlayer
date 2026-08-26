package io.github.julystar.musicapp.feature.sources.presentation

import androidx.compose.runtime.Immutable
import io.github.julystar.musicapp.core.domain.model.SourceAccountId

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
    val remoteServer: RemoteServerSourceEditorState = RemoteServerSourceEditorState(),
    val emby: EmbySourceEditorState = EmbySourceEditorState(),
    val openList: OpenListSourceEditorState = OpenListSourceEditorState(),
    val otpInputGeneration: Int = 0,
    val isSyncing: Boolean = false,
    val canSyncCurrentServer: Boolean = false,
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
data class RemoteServerSourceEditorState(
    val alias: String = "",
    val address: String = "",
    val username: String = "",
    val secondaryBaseUrl: String = "",
    val streamMaxBitRate: Int = 0,
    val downloadMaxBitRate: Int = 0,
    val coverArtSize: Int = 512,
    val remoteWriteEnabled: Boolean = false,
)

@Immutable
data class EmbySourceEditorState(
    val alias: String = "",
    val address: String = "",
    val username: String = "",
    val secondaryBaseUrl: String = "",
    val serverName: String = "",
    val connectedUserId: String = "",
)

@Immutable
data class OpenListSourceEditorState(
    val alias: String = "",
    val address: String = "",
    val username: String = "",
    val isGuest: Boolean = false,
    val showOtp: Boolean = false,
    val hasOtp: Boolean = false,
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
    data object ImportLocalLibraryFolder : SourceEditorAction
    data object SyncNow : SourceEditorAction
    data class ChangeType(val storageType: SourceEditorType) : SourceEditorAction
    data class WebDavAnonymousChanged(val isAnonymous: Boolean) : SourceEditorAction
    data class WebDavAliasChanged(val value: String) : SourceEditorAction
    data class WebDavAddressChanged(val value: String) : SourceEditorAction
    data class WebDavUsernameChanged(val value: String) : SourceEditorAction
    data class WebDavPasswordChanged(val value: String) : SourceEditorAction {
        override fun toString(): String = "WebDavPasswordChanged(value=<redacted>)"
    }
    data class RemoteServerAliasChanged(val value: String) : SourceEditorAction
    data class RemoteServerAddressChanged(val value: String) : SourceEditorAction
    data class RemoteServerUsernameChanged(val value: String) : SourceEditorAction
    data class RemoteServerPasswordChanged(val value: String) : SourceEditorAction {
        override fun toString(): String = "RemoteServerPasswordChanged(value=<redacted>)"
    }
    data class RemoteServerSecondaryAddressChanged(val value: String) : SourceEditorAction
    data class RemoteServerStreamBitRateChanged(val value: Int) : SourceEditorAction
    data class RemoteServerDownloadBitRateChanged(val value: Int) : SourceEditorAction
    data class RemoteServerCoverArtSizeChanged(val value: Int) : SourceEditorAction
    data class RemoteServerWriteChanged(val value: Boolean) : SourceEditorAction
    data class OpenListAliasChanged(val value: String) : SourceEditorAction
    data class OpenListAddressChanged(val value: String) : SourceEditorAction
    data class OpenListUsernameChanged(val value: String) : SourceEditorAction
    data class OpenListPasswordChanged(val value: String) : SourceEditorAction {
        override fun toString(): String = "OpenListPasswordChanged(value=<redacted>)"
    }
    data class OpenListGuestChanged(val value: Boolean) : SourceEditorAction
    data class OpenListOtpChanged(val value: String) : SourceEditorAction {
        override fun toString(): String = "OpenListOtpChanged(value=<redacted>)"
    }
    data class OneDriveAliasChanged(val value: String) : SourceEditorAction
    data class SmbAliasChanged(val value: String) : SourceEditorAction
    data class SmbHostChanged(val value: String) : SourceEditorAction
    data class SmbPortChanged(val value: String) : SourceEditorAction
    data class SmbDomainChanged(val value: String) : SourceEditorAction
    data class SmbUsernameChanged(val value: String) : SourceEditorAction
    data class SmbPasswordChanged(val value: String) : SourceEditorAction {
        override fun toString(): String = "SmbPasswordChanged(value=<redacted>)"
    }
    data class SmbGuestChanged(val value: Boolean) : SourceEditorAction
    data class SmbSigningChanged(val value: Boolean) : SourceEditorAction
    data class SmbEncryptionChanged(val value: Boolean) : SourceEditorAction
    data object ConnectOneDrive : SourceEditorAction
    data object DisconnectOneDrive : SourceEditorAction
    data class SelectOneDriveDrive(val driveId: String) : SourceEditorAction
}

val sourceEditorBitRateChoices: List<Int> = listOf(0, 128, 192, 256, 320)
val sourceEditorCoverArtSizeChoices: List<Int> = listOf(256, 512, 768, 1024)

enum class SourceEditorField {
    Alias,
    Address,
    Username,
    Password,
    Guest,
    Otp,
    SecondaryAddress,
    StreamBitRate,
    DownloadBitRate,
    CoverArtSize,
    RemoteWrite,
    ServerName,
    ConnectedAccount,
    LibraryRoot,
}

enum class RemoteServerConfigSection(
    val fields: List<SourceEditorField>,
    val initiallyExpanded: Boolean,
) {
    Basic(
        fields = listOf(
            SourceEditorField.Alias,
            SourceEditorField.Address,
            SourceEditorField.Username,
            SourceEditorField.Password,
        ),
        initiallyExpanded = true,
    ),
    Advanced(
        fields = listOf(
            SourceEditorField.SecondaryAddress,
            SourceEditorField.StreamBitRate,
            SourceEditorField.DownloadBitRate,
            SourceEditorField.CoverArtSize,
            SourceEditorField.RemoteWrite,
        ),
        initiallyExpanded = false,
    ),
}

data class EmbyEditorContract(
    val editableFields: List<SourceEditorField>,
    val readOnlyFields: List<SourceEditorField>,
    val authenticationInputInitialValue: String,
    val showAuthenticationRetentionHint: Boolean,
) {
    val visibleFields: Set<SourceEditorField> = (editableFields + readOnlyFields).toSet()
}

fun embyEditorContract(isCreated: Boolean): EmbyEditorContract = EmbyEditorContract(
    editableFields = listOf(
        SourceEditorField.Alias,
        SourceEditorField.Address,
        SourceEditorField.Username,
        SourceEditorField.Password,
        SourceEditorField.SecondaryAddress,
    ),
    readOnlyFields = if (isCreated) {
        emptyList()
    } else {
        listOf(SourceEditorField.ServerName, SourceEditorField.ConnectedAccount)
    },
    authenticationInputInitialValue = "",
    showAuthenticationRetentionHint = !isCreated,
)

enum class OpenListForbiddenField {
    Cookie,
    Authorization,
    AccessToken,
    RefreshToken,
    Jwt,
    UserAgent,
    HttpHeaders,
    BaiduCookie,
    QuarkCookie,
}

data class OpenListEditorContract(
    val visibleFields: List<SourceEditorField>,
    val forbiddenFields: Set<OpenListForbiddenField>,
    val otpInputInitialValue: String,
    val otpMemoryOnly: Boolean,
)

fun openListEditorContract(
    isGuest: Boolean,
    showOtp: Boolean,
): OpenListEditorContract = OpenListEditorContract(
    visibleFields = buildList {
        addAll(listOf(SourceEditorField.Alias, SourceEditorField.Address, SourceEditorField.Guest))
        if (!isGuest) {
            addAll(listOf(SourceEditorField.Username, SourceEditorField.Password))
            if (showOtp) add(SourceEditorField.Otp)
        }
    },
    forbiddenFields = OpenListForbiddenField.entries.toSet(),
    otpInputInitialValue = "",
    otpMemoryOnly = true,
)

fun sourceEditorVisibleFields(
    type: SourceEditorType,
    isGuest: Boolean = false,
    showOtp: Boolean = false,
    isCreated: Boolean = true,
): Set<SourceEditorField> = buildSet {
    when (type) {
        SourceEditorType.WebDav -> {
            addAll(listOf(SourceEditorField.Alias, SourceEditorField.Address, SourceEditorField.Guest))
            if (!isGuest) addAll(listOf(SourceEditorField.Username, SourceEditorField.Password))
        }
        SourceEditorType.Smb -> {
            addAll(listOf(SourceEditorField.Alias, SourceEditorField.Address, SourceEditorField.Guest))
            if (!isGuest) addAll(listOf(SourceEditorField.Username, SourceEditorField.Password))
        }
        SourceEditorType.OneDrive -> add(SourceEditorField.Alias)
        SourceEditorType.Navidrome,
        SourceEditorType.OpenSubsonic -> addAll(
            listOf(
                SourceEditorField.Alias,
                SourceEditorField.Address,
                SourceEditorField.Username,
                SourceEditorField.Password,
                SourceEditorField.SecondaryAddress,
                SourceEditorField.StreamBitRate,
                SourceEditorField.DownloadBitRate,
                SourceEditorField.CoverArtSize,
                SourceEditorField.RemoteWrite,
            )
        )
        SourceEditorType.Emby -> addAll(embyEditorContract(isCreated).visibleFields)
        SourceEditorType.OpenList -> addAll(openListEditorContract(isGuest, showOtp).visibleFields)
    }
    if (!isCreated && type in setOf(
            SourceEditorType.WebDav,
            SourceEditorType.OneDrive,
            SourceEditorType.Smb,
            SourceEditorType.OpenList,
        )
    ) {
        add(SourceEditorField.LibraryRoot)
    }
}

sealed interface SourceEditorEvent {
    data object NavigateBack : SourceEditorEvent
    data class OpenLibraryFolderImport(val accountId: SourceAccountId) : SourceEditorEvent
    data class OpenOneDriveOAuth(val authorizationUrl: String) : SourceEditorEvent {
        override fun toString(): String = "OpenOneDriveOAuth(authorizationUrl=<redacted>)"
    }
}
