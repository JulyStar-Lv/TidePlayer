package io.github.julystar.musicapp.feature.sources.presentation

import io.github.julystar.musicapp.core.domain.model.OneDriveDriveInfo

fun sourceEditorState(
    draft: SourceEditorDraft,
    title: String,
    musicCount: ULong,
    validation: SourceEditorValidation,
    removeDialogOpen: Boolean,
    testResult: SourceConnectionTestStatus,
    oneDriveDrives: List<OneDriveDriveInfo> = emptyList(),
    oneDriveDrivesLoading: Boolean = false,
    requiresOtp: Boolean = false,
    showOtp: Boolean = requiresOtp,
    hasOtp: Boolean = false,
    otpInputGeneration: Int = 0,
    connectedServerName: String = "",
    isSyncing: Boolean = false,
    canSyncCurrentServer: Boolean = false,
): SourceEditorState {
    return SourceEditorState(
        title = title,
        musicCount = musicCount,
        isCreated = draft.id == null,
        storageType = draft.storageType,
        testStatus = testResult,
        validation = validation,
        removeDialogOpen = removeDialogOpen,
        webDav = WebDavSourceEditorState(
            alias = draft.alias,
            address = draft.address,
            username = draft.username,
            isAnonymous = draft.isAnonymous,
        ),
        oneDrive = OneDriveSourceEditorState(
            alias = draft.alias,
            selectedDriveId = draft.address,
            connected = draft.secret.isNotEmpty(),
            drives = oneDriveDrives.toSourceEditorDriveUiList(),
            drivesLoading = oneDriveDrivesLoading,
        ),
        smb = SmbSourceEditorState(
            alias = draft.alias,
            host = draft.smbHost,
            port = draft.smbPort.takeIf { it > 0 }?.toString().orEmpty(),
            share = draft.smbShare,
            rootPath = draft.smbRootPath,
            domain = draft.smbDomain,
            username = draft.username,
            isGuest = draft.isAnonymous,
            requireSigning = draft.smbRequireSigning,
            requireEncryption = draft.smbRequireEncryption,
        ),
        remoteServer = RemoteServerSourceEditorState(
            alias = draft.alias,
            address = draft.address,
            username = draft.username,
            secondaryBaseUrl = draft.secondaryBaseUrl,
            streamMaxBitRate = draft.streamMaxBitRate,
            downloadMaxBitRate = draft.downloadMaxBitRate,
            coverArtSize = draft.coverArtSize,
            remoteWriteEnabled = draft.remoteWriteEnabled,
        ),
        emby = EmbySourceEditorState(
            alias = draft.alias,
            address = draft.address,
            username = draft.username,
            secondaryBaseUrl = draft.secondaryBaseUrl,
            serverName = connectedServerName,
            connectedUserId = draft.externalAccountId,
        ),
        openList = OpenListSourceEditorState(
            alias = draft.alias,
            address = draft.address,
            username = draft.username,
            isGuest = draft.isAnonymous,
            showOtp = draft.storageType == SourceEditorType.OpenList && !draft.isAnonymous &&
                (requiresOtp || showOtp),
            hasOtp = hasOtp,
        ),
        otpInputGeneration = otpInputGeneration,
        isSyncing = isSyncing,
        canSyncCurrentServer = canSyncCurrentServer,
    )
}

private fun List<OneDriveDriveInfo>.toSourceEditorDriveUiList(): List<SourceEditorDriveUi> {
    return map { drive ->
        SourceEditorDriveUi(
            id = drive.id,
            name = drive.name,
        )
    }
}
