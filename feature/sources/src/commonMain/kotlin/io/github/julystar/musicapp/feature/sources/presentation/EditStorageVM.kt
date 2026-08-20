package io.github.julystar.musicapp.feature.sources.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.OneDriveDriveInfo
import io.github.julystar.musicapp.core.domain.model.NeedsReauthenticationException
import io.github.julystar.musicapp.core.domain.model.OpenListOtpRequiredException
import io.github.julystar.musicapp.core.domain.model.metadataScanModeFor
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.core.domain.repository.StorageRepository
import io.github.julystar.musicapp.core.domain.repository.ToastRepository
import io.github.julystar.musicapp.core.domain.repository.UiMessageKey
import io.github.julystar.musicapp.core.domain.repository.emit
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncController
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncRequest
import io.github.julystar.musicapp.service.librarysync.domain.SourceAccountLibrarySyncController
import io.github.julystar.musicapp.source.api.ImportRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


data class Validated(
    val addrEmpty: Boolean = false,
    val aliasEmpty: Boolean = false,
    val usernameEmpty: Boolean = false,
    val passwordEmpty: Boolean = false,
    val smbPortInvalid: Boolean = false,
) {
    fun valid(): Boolean {
        return !addrEmpty &&
            !aliasEmpty &&
            !usernameEmpty &&
            !passwordEmpty &&
            !smbPortInvalid
    }
}

private data class EditorInputs(
    val draft: SourceEditorDraft,
    val title: String,
    val musicCount: ULong,
    val validated: Validated,
    val removeModalOpen: Boolean,
    val oneDriveDrives: List<OneDriveDriveInfo> = emptyList(),
    val oneDriveDrivesLoading: Boolean = false,
)

private data class OpenListOtpUiState(
    val requiresOtp: Boolean = false,
    val showOtp: Boolean = false,
    val hasOtp: Boolean = false,
    val inputGeneration: Int = 0,
)

class EditStorageVM constructor(
    private val storageRepository: StorageRepository,
    private val toastRepository: ToastRepository,
    private val importRepository: ImportRepository,
    private val librarySyncController: LibrarySyncController,
    private val sourceAccountLibrarySyncController: SourceAccountLibrarySyncController,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _events = Channel<SourceEditorEvent>(Channel.BUFFERED)
    private val _title = MutableStateFlow("")
    private val _musicCount = MutableStateFlow(0uL)
    private val _draft = MutableStateFlow(defaultSourceEditorDraft())
    private var _draftBackups = HashMap<SourceEditorType, SourceEditorDraft>()
    private var _editorAccountId: String? = null
    private val _persistedEditorType = MutableStateFlow<SourceEditorType?>(null)

    private val _validated = MutableStateFlow(Validated())
    private val _removeModalOpen = MutableStateFlow(false)
    private val _testResult = MutableStateFlow(SourceConnectionTestStatus.None)
    private var _testJob: Job? = null
    private val _oneDriveDrives = MutableStateFlow<List<OneDriveDriveInfo>>(emptyList())
    private val _oneDriveDrivesLoading = MutableStateFlow(false)
    private var _oneDriveDriveJob: Job? = null
    private val _openListOtpUi = MutableStateFlow(OpenListOtpUiState())
    private var openListOtpCode: String = ""
    private val _connectedServerName = MutableStateFlow("")
    private val _isSyncing = MutableStateFlow(false)
    private var _syncJob: Job? = null

    val events = _events.receiveAsFlow()
    val state = combine(
        _draft,
        _title,
        _musicCount,
        _validated,
        _removeModalOpen,
    ) { draft, title, musicCount, validated, removeModalOpen ->
        EditorInputs(draft, title, musicCount, validated, removeModalOpen)
    }.combine(_oneDriveDrives) { inputs, oneDriveDrives ->
        inputs.copy(oneDriveDrives = oneDriveDrives)
    }.combine(_oneDriveDrivesLoading) { inputs, oneDriveDrivesLoading ->
        inputs.copy(oneDriveDrivesLoading = oneDriveDrivesLoading)
    }.combine(_testResult) { inputs, testResult ->
        inputs to testResult
    }.combine(_openListOtpUi) { (inputs, testResult), otpUi ->
        Triple(inputs, testResult, otpUi)
    }.combine(_connectedServerName) { (inputs, testResult, otpUi), connectedServerName ->
        Triple(inputs, testResult, otpUi) to connectedServerName
    }.combine(_persistedEditorType) { (editor, connectedServerName), persistedEditorType ->
        Triple(editor, connectedServerName, persistedEditorType)
    }.combine(_isSyncing) { (editor, connectedServerName, persistedEditorType), isSyncing ->
        val (inputs, testResult, otpUi) = editor
        sourceEditorState(
            draft = inputs.draft,
            title = inputs.title,
            musicCount = inputs.musicCount,
            validation = inputs.validated.toSourceEditorValidation(),
            removeDialogOpen = inputs.removeModalOpen,
            testResult = testResult,
            oneDriveDrives = inputs.oneDriveDrives,
            oneDriveDrivesLoading = inputs.oneDriveDrivesLoading,
            requiresOtp = otpUi.requiresOtp,
            showOtp = otpUi.showOtp,
            hasOtp = otpUi.hasOtp,
            otpInputGeneration = otpUi.inputGeneration,
            connectedServerName = connectedServerName,
            isSyncing = isSyncing,
            canSyncCurrentServer = inputs.draft.storageType == persistedEditorType &&
                inputs.draft.storageType in SERVER_EDITOR_TYPES,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = sourceEditorState(
            draft = defaultSourceEditorDraft(),
            title = "",
            musicCount = 0u,
            validation = SourceEditorValidation(),
            removeDialogOpen = false,
            testResult = SourceConnectionTestStatus.None,
        ),
    )

    init {
        viewModelScope.launch {
            storageRepository.oauthRefreshToken.collect { refreshToken ->
                updateDraft { draft ->
                    if (draft.storageType == SourceEditorType.OneDrive) {
                        draft.copy(secret = refreshToken)
                    } else {
                        draft
                    }
                }
                if (refreshToken.isNotBlank()) {
                    loadOneDriveDrives(refreshToken)
                }
            }
        }

        _draft.value = defaultSourceEditorDraft()
        _title.value = ""
        _musicCount.value = 0u

        val id: Long? = savedStateHandle["id"]
        if (id != null && id >= 0) {
            viewModelScope.launch {
                val editorState = storageRepository.loadEditorState(id) ?: return@launch
                _editorAccountId = editorState.accountId.value
                _persistedEditorType.value = editorState.draft.storageType
                _draft.value = editorState.draft
                _title.value = editorState.title
                _musicCount.value = editorState.musicCount
                _connectedServerName.value = editorState.connectedServerName
                _openListOtpUi.value = OpenListOtpUiState(
                    requiresOtp = editorState.requiresOtp,
                    showOtp = editorState.requiresOtp,
                )

                storageRepository.loadCredentialByAccountId(editorState.accountId)?.let { credential ->
                    updateDraft { current ->
                        current.copy(
                            username = credential.username,
                            secret = if (
                                current.storageType == SourceEditorType.WebDav ||
                                current.storageType == SourceEditorType.OneDrive
                            ) credential.secret else "",
                            isAnonymous = credential.isAnonymous,
                        )
                    }
                    if (editorState.isOneDrive) {
                        loadOneDriveDrives(credential.secret)
                    }
                }
            }
        }
    }

    private fun test() {
        resetTestResult()
        if (!validate()) {
            return
        }
        _testResult.value = SourceConnectionTestStatus.Testing

        _testJob = viewModelScope.launch {
            val result = if (_draft.value.storageType == SourceEditorType.OpenList) {
                storageRepository.testOpenListSource(_draft.value, openListOtpCode)
            } else {
                storageRepository.testSource(_draft.value)
            }
            _testResult.value = result
            updateOpenListOtpAfterTest(result)

            delay(5000)
            resetTestResult()
        }
    }

    fun onAction(action: SourceEditorAction) {
        when (action) {
            SourceEditorAction.NavigateBack -> {
                clearOpenListOtp(showOtp = false)
                sendEvent(SourceEditorEvent.NavigateBack)
            }
            SourceEditorAction.TestConnection -> test()
            SourceEditorAction.Save -> saveAndNavigateBack()
            SourceEditorAction.OpenRemoveDialog -> openRemoveModal()
            SourceEditorAction.CloseRemoveDialog -> closeRemoveModal()
            SourceEditorAction.ConfirmRemove -> removeAndNavigateBack()
            SourceEditorAction.ImportLibraryFolder -> prepareImportAndNavigate()
            SourceEditorAction.ImportLocalLibraryFolder -> prepareLocalImportAndNavigate()
            SourceEditorAction.SyncNow -> syncCurrentServerAccount()
            is SourceEditorAction.ChangeType -> changeType(action.storageType)
            is SourceEditorAction.WebDavAnonymousChanged -> updateDraft { draft ->
                draft.copy(isAnonymous = action.isAnonymous)
            }
            is SourceEditorAction.WebDavAliasChanged -> updateDraft { draft ->
                draft.copy(alias = action.value)
            }
            is SourceEditorAction.WebDavAddressChanged -> updateDraft { draft ->
                draft.copy(address = action.value)
            }
            is SourceEditorAction.WebDavUsernameChanged -> updateDraft { draft ->
                draft.copy(username = action.value)
            }
            is SourceEditorAction.WebDavPasswordChanged -> updateDraft { draft ->
                draft.copy(secret = action.value)
            }
            is SourceEditorAction.RemoteServerAliasChanged -> updateDraft { draft ->
                draft.copy(alias = action.value)
            }
            is SourceEditorAction.RemoteServerAddressChanged -> updateDraft { draft ->
                draft.copy(address = action.value)
            }
            is SourceEditorAction.RemoteServerUsernameChanged -> updateDraft { draft ->
                draft.copy(username = action.value)
            }
            is SourceEditorAction.RemoteServerPasswordChanged -> updateDraft { draft ->
                draft.copy(secret = action.value)
            }
            is SourceEditorAction.RemoteServerSecondaryAddressChanged -> updateDraft { draft ->
                draft.copy(secondaryBaseUrl = action.value)
            }
            is SourceEditorAction.RemoteServerStreamBitRateChanged -> updateDraft { draft ->
                draft.copy(streamMaxBitRate = action.value)
            }
            is SourceEditorAction.RemoteServerDownloadBitRateChanged -> updateDraft { draft ->
                draft.copy(downloadMaxBitRate = action.value)
            }
            is SourceEditorAction.RemoteServerCoverArtSizeChanged -> updateDraft { draft ->
                draft.copy(coverArtSize = action.value)
            }
            is SourceEditorAction.RemoteServerWriteChanged -> updateDraft { draft ->
                draft.copy(remoteWriteEnabled = action.value)
            }
            is SourceEditorAction.OpenListAliasChanged -> updateDraft { draft ->
                draft.copy(alias = action.value)
            }
            is SourceEditorAction.OpenListAddressChanged -> {
                clearOpenListOtpKeepingPrompt()
                updateDraft { draft -> draft.copy(address = action.value) }
            }
            is SourceEditorAction.OpenListUsernameChanged -> {
                clearOpenListOtpKeepingPrompt()
                updateDraft { draft -> draft.copy(username = action.value) }
            }
            is SourceEditorAction.OpenListPasswordChanged -> {
                clearOpenListOtpKeepingPrompt()
                updateDraft { draft -> draft.copy(secret = action.value) }
            }
            is SourceEditorAction.OpenListGuestChanged -> {
                clearOpenListOtp(showOtp = false)
                updateDraft { draft ->
                    draft.copy(
                        isAnonymous = action.value,
                        username = if (action.value) "" else draft.username,
                        secret = if (action.value) "" else draft.secret,
                    )
                }
                if (!action.value && _openListOtpUi.value.requiresOtp) {
                    _openListOtpUi.value = _openListOtpUi.value.copy(showOtp = true)
                }
            }
            is SourceEditorAction.OpenListOtpChanged -> {
                openListOtpCode = action.value
                _openListOtpUi.value = _openListOtpUi.value.copy(hasOtp = action.value.isNotBlank())
            }
            is SourceEditorAction.OneDriveAliasChanged -> updateDraft { draft ->
                draft.copy(alias = action.value)
            }
            is SourceEditorAction.SmbAliasChanged -> updateDraft { draft ->
                draft.copy(alias = action.value)
            }
            is SourceEditorAction.SmbHostChanged -> updateDraft { draft ->
                draft.copy(smbHost = action.value)
            }
            is SourceEditorAction.SmbPortChanged -> updateDraft { draft ->
                draft.copy(smbPort = action.value.toIntOrNull() ?: 0)
            }
            is SourceEditorAction.SmbDomainChanged -> updateDraft { draft ->
                draft.copy(smbDomain = action.value)
            }
            is SourceEditorAction.SmbUsernameChanged -> updateDraft { draft ->
                draft.copy(username = action.value)
            }
            is SourceEditorAction.SmbPasswordChanged -> updateDraft { draft ->
                draft.copy(secret = action.value)
            }
            is SourceEditorAction.SmbGuestChanged -> updateDraft { draft ->
                draft.copy(
                    isAnonymous = action.value,
                    username = if (action.value) "" else draft.username,
                    secret = if (action.value) "" else draft.secret,
                    smbDomain = if (action.value) "" else draft.smbDomain,
                )
            }
            is SourceEditorAction.SmbSigningChanged -> updateDraft { draft ->
                draft.copy(smbRequireSigning = action.value)
            }
            is SourceEditorAction.SmbEncryptionChanged -> updateDraft { draft ->
                draft.copy(smbRequireEncryption = action.value)
            }
            SourceEditorAction.ConnectOneDrive -> connectOneDrive()
            SourceEditorAction.DisconnectOneDrive -> disconnectOneDrive()
            is SourceEditorAction.SelectOneDriveDrive -> selectOneDriveDrive(action.driveId)
        }
    }

    private fun openRemoveModal() {
        _removeModalOpen.value = true
    }

    private fun closeRemoveModal() {
        _removeModalOpen.value = false
    }

    private fun updateDraft(block: (draft: SourceEditorDraft) -> SourceEditorDraft) {
        _draft.value = block(_draft.value)
    }

    private fun changeType(storageType: SourceEditorType) {
        clearOpenListOtp(showOtp = false)
        _openListOtpUi.value = _openListOtpUi.value.copy(requiresOtp = false)
        _connectedServerName.value = ""
        _draftBackups[_draft.value.storageType] = _draft.value

        val backup = _draftBackups[storageType]
        if (backup != null) {
            _draft.value = backup
        } else {
            val newDraft = SourceEditorDraft(
                id = _draft.value.id,
                address = "",
                alias = _draft.value.alias,
                username = "",
                secret = "",
                isAnonymous = false,
                storageType = storageType,
            )
            _draft.value = newDraft
        }
        _validated.value = Validated()
    }

    private fun validate(): Boolean {
        val draft = _draft.value
        _validated.value = Validated(
            addrEmpty = if (draft.storageType == SourceEditorType.Smb) {
                draft.smbHost.isBlank()
            } else {
                draft.address.isBlank()
            },
            aliasEmpty = if (draft.storageType == SourceEditorType.WebDav) {
                false
            } else {
                draft.alias.isBlank()
            },
            usernameEmpty = when (draft.storageType) {
                SourceEditorType.WebDav -> !draft.isAnonymous && draft.username.isBlank()
                SourceEditorType.Smb -> !draft.isAnonymous && draft.username.isBlank()
                SourceEditorType.OpenList -> !draft.isAnonymous && draft.username.isBlank()
                SourceEditorType.Navidrome,
                SourceEditorType.OpenSubsonic,
                SourceEditorType.Emby -> draft.username.isBlank()
                SourceEditorType.OneDrive -> false
            },
            passwordEmpty = when (draft.storageType) {
                SourceEditorType.WebDav -> !draft.isAnonymous && draft.secret.isBlank()
                SourceEditorType.Smb -> {
                    !draft.isAnonymous && draft.id == null && draft.secret.isBlank()
                }
                SourceEditorType.OpenList -> !draft.isAnonymous && draft.id == null && draft.secret.isBlank()
                SourceEditorType.Navidrome,
                SourceEditorType.OpenSubsonic,
                SourceEditorType.Emby -> draft.id == null && draft.secret.isBlank()
                SourceEditorType.OneDrive -> draft.secret.isBlank()
            },
            smbPortInvalid = draft.storageType == SourceEditorType.Smb &&
                draft.smbPort !in 1..65535,
        )
        return _validated.value.valid()
    }

    private fun remove() {
        val accountId = _editorAccountId ?: return
        viewModelScope.launch {
            storageRepository.removeByAccountId(SourceAccountId(accountId))
        }
    }

    private fun saveAndNavigateBack() {
        viewModelScope.launch {
            try {
                if (finish()) {
                    clearOpenListOtp(showOtp = false)
                    _events.send(SourceEditorEvent.NavigateBack)
                }
            } catch (_: NeedsReauthenticationException) {
                if (_draft.value.storageType == SourceEditorType.OpenList) {
                    clearOpenListOtpKeepingPrompt()
                }
                _testResult.value = SourceConnectionTestStatus.Unauthorized
            } catch (_: OpenListOtpRequiredException) {
                clearOpenListOtp(showOtp = true)
                _testResult.value = SourceConnectionTestStatus.OtpRequired
            }
        }
    }

    private fun removeAndNavigateBack() {
        closeRemoveModal()
        remove()
        clearOpenListOtp(showOtp = false)
        sendEvent(SourceEditorEvent.NavigateBack)
    }

    private fun prepareImportAndNavigate() {
        val accountId = _editorAccountId?.let(::SourceAccountId) ?: return
        if (_draft.value.storageType !in setOf(
                SourceEditorType.WebDav,
                SourceEditorType.OneDrive,
                SourceEditorType.Smb,
                SourceEditorType.OpenList,
            )
        ) {
            return
        }
        prepareImportLibraryFolder(
            accountId = accountId,
            isWebDavLike = _draft.value.storageType == SourceEditorType.WebDav ||
                _draft.value.storageType == SourceEditorType.Smb,
        )
        sendEvent(SourceEditorEvent.OpenLibraryFolderImport)
    }

    private fun prepareLocalImportAndNavigate() {
        val localAccountId = storageRepository.storageAccounts.value
            .firstOrNull { account -> account.isLocal }
            ?.accountId
            ?: return
        prepareImportLibraryFolder(accountId = localAccountId, isWebDavLike = false)
        sendEvent(SourceEditorEvent.OpenLibraryFolderImport)
    }

    private fun syncCurrentServerAccount() {
        if (_syncJob?.isActive == true) return
        val currentType = _draft.value.storageType
        if (currentType !in SERVER_EDITOR_TYPES || currentType != _persistedEditorType.value) {
            return
        }
        val accountId = _editorAccountId?.let(::SourceAccountId) ?: return
        _syncJob = viewModelScope.launch {
            _isSyncing.value = true
            toastRepository.emit(UiMessageKey.LibraryImportStarted)
            try {
                val result = sourceAccountLibrarySyncController.sync(accountId)
                toastRepository.emit(
                    UiMessageKey.LibraryImportCompleted,
                    result.importedCount.toString(),
                    result.skippedCount.toString(),
                    result.failedCount.toString(),
                )
                storageRepository.reload()
            } catch (cancellation: CancellationException) {
                toastRepository.emit(UiMessageKey.LibraryImportCancelled)
                throw cancellation
            } catch (_: Throwable) {
                toastRepository.emit(UiMessageKey.LibraryImportFailed)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private fun connectOneDrive() {
        viewModelScope.launch {
            val result = runCatching {
                startOneDriveOAuth()
            }
            result.onSuccess { authorizationUrl ->
                _events.send(SourceEditorEvent.OpenOneDriveOAuth(authorizationUrl))
            }.onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }
                toastRepository.emit(UiMessageKey.OneDriveSignInFailed)
            }
        }
    }

    private fun prepareImportLibraryFolder(
        accountId: SourceAccountId,
        isWebDavLike: Boolean,
    ) {
        importRepository.prepareCurrentDirectory(accountId) { selection ->
            if (selection.accountId != accountId) return@prepareCurrentDirectory
            viewModelScope.launch {
                toastRepository.emit(UiMessageKey.LibraryImportStarted)
                val metadataScanMode = settingsRepository.settings.first().metadataScanModeFor(
                    isWebDav = isWebDavLike,
                )
                val result = runCatching {
                    librarySyncController.syncFolder(
                        LibrarySyncRequest(
                            accountId = selection.accountId,
                            selectedFolderRemoteId = selection.remoteId,
                            selectedFolderCanonicalPath = selection.path,
                            selectedFolderDisplayPath = selection.path,
                            metadataScanMode = metadataScanMode,
                        )
                    )
                }
                result.onSuccess { value ->
                    toastRepository.emit(
                        UiMessageKey.LibraryImportCompleted,
                        value.importedCount.toString(),
                        value.skippedCount.toString(),
                        value.failedCount.toString(),
                    )
                    storageRepository.reload()
                }.onFailure { error ->
                    if (error is CancellationException) {
                        toastRepository.emit(UiMessageKey.LibraryImportCancelled)
                        throw error
                    } else {
                        toastRepository.emit(UiMessageKey.LibraryImportFailed)
                    }
                }
            }
        }
    }

    private suspend fun startOneDriveOAuth(): String = storageRepository.startOneDriveOAuth()

    private fun selectOneDriveDrive(driveId: String) {
        updateDraft { draft ->
            draft.copy(address = driveId)
        }
    }

    private fun disconnectOneDrive() {
        _oneDriveDriveJob?.cancel()
        _oneDriveDrives.value = emptyList()
        _oneDriveDrivesLoading.value = false
        updateDraft { draft ->
            draft.copy(
                address = "",
                secret = "",
            )
        }
    }

    private suspend fun finish(): Boolean {
        if (!validate()) {
            return false
        }
        if (_draft.value.storageType == SourceEditorType.OpenList &&
            !_draft.value.isAnonymous &&
            _openListOtpUi.value.requiresOtp &&
            openListOtpCode.isBlank()
        ) {
            throw OpenListOtpRequiredException()
        }

        if (_draft.value.storageType == SourceEditorType.OpenList) {
            storageRepository.upsertOpenListSource(_draft.value, openListOtpCode)
        } else {
            storageRepository.upsertSource(_draft.value)
        }
        return true
    }

    private fun resetTestResult() {
        _testJob?.cancel()
        _testJob = null
        _testResult.value = SourceConnectionTestStatus.None
    }

    private fun updateOpenListOtpAfterTest(result: SourceConnectionTestStatus) {
        if (_draft.value.storageType != SourceEditorType.OpenList) return
        when (result) {
            SourceConnectionTestStatus.Success -> {
                if (openListOtpCode.isNotBlank()) {
                    _openListOtpUi.value = _openListOtpUi.value.copy(
                        showOtp = true,
                        hasOtp = true,
                    )
                }
            }
            SourceConnectionTestStatus.OtpRequired -> clearOpenListOtp(showOtp = true)
            SourceConnectionTestStatus.Unauthorized -> clearOpenListOtpKeepingPrompt()
            else -> Unit
        }
    }

    private fun clearOpenListOtpKeepingPrompt() {
        val current = _openListOtpUi.value
        clearOpenListOtp(showOtp = current.showOtp || current.requiresOtp)
    }

    private fun clearOpenListOtp(showOtp: Boolean) {
        openListOtpCode = ""
        _openListOtpUi.value = _openListOtpUi.value.copy(
            showOtp = showOtp,
            hasOtp = false,
            inputGeneration = _openListOtpUi.value.inputGeneration + 1,
        )
    }

    private fun sendEvent(event: SourceEditorEvent) {
        viewModelScope.launch {
            _events.send(event)
        }
    }

    private fun loadOneDriveDrives(refreshToken: String) {
        _oneDriveDriveJob?.cancel()
        _oneDriveDriveJob = viewModelScope.launch {
            _oneDriveDrivesLoading.value = true
            try {
                val result = storageRepository.listOneDriveDriveInfos(refreshToken)
                _oneDriveDrives.value = result.drives
                if (result.refreshedToken != refreshToken) {
                    updateDraft { draft ->
                        draft.copy(secret = result.refreshedToken)
                    }
                    val accountId = _editorAccountId
                    if (accountId != null) {
                        storageRepository.updateOneDriveRefreshTokenByAccountId(
                            SourceAccountId(accountId),
                            result.refreshedToken,
                        )
                    }
                }
                val selected = _draft.value.address
                if (result.drives.isNotEmpty() && result.drives.none { it.id == selected }) {
                    selectOneDriveDrive(result.drives.first().id)
                }
            } catch (error: Exception) {
                _oneDriveDrives.value = emptyList()
                toastRepository.emit(UiMessageKey.OneDriveDriveListFailed)
            } finally {
                _oneDriveDrivesLoading.value = false
            }
        }
    }
}

private val SERVER_EDITOR_TYPES = setOf(
    SourceEditorType.Navidrome,
    SourceEditorType.OpenSubsonic,
    SourceEditorType.Emby,
)

private fun Validated.toSourceEditorValidation(): SourceEditorValidation {
    return SourceEditorValidation(
        addressEmpty = addrEmpty,
        aliasEmpty = aliasEmpty,
        usernameEmpty = usernameEmpty,
        passwordEmpty = passwordEmpty,
        smbPortInvalid = smbPortInvalid,
    )
}
