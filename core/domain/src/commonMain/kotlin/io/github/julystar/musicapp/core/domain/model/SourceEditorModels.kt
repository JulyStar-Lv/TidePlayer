package io.github.julystar.musicapp.core.domain.model

data class SourceEditorDraft(
    val id: Long? = null,
    val address: String = "",
    val alias: String = "",
    val username: String = "",
    val secret: String = "",
    val isAnonymous: Boolean = false,
    val storageType: SourceEditorType = SourceEditorType.WebDav,
    val externalAccountId: String = "",
    val smbHost: String = "",
    val smbPort: Int = 445,
    val smbShare: String = "",
    val smbRootPath: String = "",
    val smbDomain: String = "",
    val smbRequireSigning: Boolean = false,
    val smbRequireEncryption: Boolean = false,
    val streamMaxBitRate: Int = 0,
    val downloadMaxBitRate: Int = 0,
    val coverArtSize: Int = 512,
    val remoteWriteEnabled: Boolean = false,
    val secondaryBaseUrl: String = "",
) {
    override fun toString(): String {
        return "SourceEditorDraft(" +
            "id=$id, address=$address, alias=$alias, username=$username, " +
            "secret=<redacted>, isAnonymous=$isAnonymous, storageType=$storageType, " +
            "externalAccountId=$externalAccountId)"
    }
}

fun defaultSourceEditorDraft(): SourceEditorDraft {
    return SourceEditorDraft()
}

enum class SourceEditorType {
    WebDav,
    OneDrive,
    Smb,
    OpenList,
    Navidrome,
    OpenSubsonic,
    Emby,
}

enum class SourceConnectionTestStatus {
    None,
    Testing,
    Success,
    Unauthorized,
    Timeout,
    PermissionDenied,
    NotFound,
    InvalidAddress,
    Unavailable,
    UnsupportedSecurityPolicy,
    OtpRequired,
    Error,
}

data class SourceEditorStorageState(
    val accountId: SourceAccountId,
    val draft: SourceEditorDraft,
    val title: String,
    val musicCount: ULong,
    val isOneDrive: Boolean,
    val requiresOtp: Boolean = false,
    val connectedServerName: String = "",
)

data class OneDriveDriveListResult(
    val drives: List<OneDriveDriveInfo>,
    val refreshedToken: String,
) {
    override fun toString(): String =
        "OneDriveDriveListResult(drives=$drives, refreshedToken=<redacted>)"
}
