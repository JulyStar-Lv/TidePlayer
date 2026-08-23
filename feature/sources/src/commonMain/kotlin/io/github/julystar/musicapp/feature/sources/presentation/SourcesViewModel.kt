package io.github.julystar.musicapp.feature.sources.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.julystar.musicapp.core.domain.model.SourceId
import io.github.julystar.musicapp.core.domain.model.StorageAccountInfo
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.sanitizeSourceTitleForDisplay
import io.github.julystar.musicapp.core.domain.repository.StorageRepository
import io.github.julystar.musicapp.core.domain.repository.ToastRepository
import io.github.julystar.musicapp.core.domain.repository.UiMessageKey
import io.github.julystar.musicapp.core.domain.repository.emit
import io.github.julystar.musicapp.service.librarysync.domain.SourceAccountLibrarySyncController
import io.github.julystar.musicapp.source.api.BuiltInSourceIds
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SourcesViewModel(
    private val storageRepository: StorageRepository,
    private val sourceAccountLibrarySyncController: SourceAccountLibrarySyncController,
    private val toastRepository: ToastRepository,
) : ViewModel() {
    private val _events = Channel<SourcesEvent>(Channel.BUFFERED)
    private val _syncingAccountIds = MutableStateFlow<Set<SourceAccountId>>(emptySet())
    private val syncJobs = mutableMapOf<SourceAccountId, Job>()

    val events = _events.receiveAsFlow()
    val state = combine(storageRepository.storageAccounts, _syncingAccountIds) { accounts, syncing ->
            SourcesState(
                sources = accounts
                    .filter { account -> !account.isLocal }
                    .map { account -> account.toSourceAccountUi(account.accountId in syncing) }
                    .toPersistentList(),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SourcesState(),
        )

    init {
        reload()
    }

    fun onAction(action: SourcesAction) {
        when (action) {
            SourcesAction.Refresh -> reload()
            SourcesAction.AddSource -> openNewSourceEditor()
            is SourcesAction.OpenSource -> openSourceEditor(action.id)
            is SourcesAction.SyncSource -> syncSource(action.id)
        }
    }

    private fun syncSource(accountId: SourceAccountId) {
        if (syncJobs[accountId]?.isActive == true) return
        syncJobs[accountId] = viewModelScope.launch {
            _syncingAccountIds.value += accountId
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
                _syncingAccountIds.value -= accountId
                syncJobs.remove(accountId)
            }
        }
    }

    private fun reload() {
        viewModelScope.launch {
            storageRepository.reload()
        }
    }

    private fun openNewSourceEditor() {
        viewModelScope.launch {
            _events.send(SourcesEvent.OpenNewSourceEditor)
        }
    }

    private fun openSourceEditor(id: SourceAccountId) {
        viewModelScope.launch {
            _events.send(SourcesEvent.OpenSourceEditor(id))
        }
    }
}

internal fun StorageAccountInfo.toSourceAccountUi(isSyncing: Boolean): SourceAccountUi {
    val sourceType = sourceId.toSourceTypeLabel()
    val safeEndpoint = sanitizeSourceCardEndpoint(subtitle)
    return SourceAccountUi(
        id = accountId,
        title = sanitizeSourceTitleForDisplay(title, subtitle, sourceType),
        safeEndpoint = safeEndpoint,
        sourceType = sourceType,
        musicCount = musicCount,
        syncEnabled = enabled,
        isSyncing = isSyncing,
    )
}

private fun SourceId.toSourceTypeLabel(): String {
    return when (this) {
        BuiltInSourceIds.WebDav -> "WebDAV"
        BuiltInSourceIds.OneDrive -> "OneDrive"
        BuiltInSourceIds.Smb -> "SMB"
        BuiltInSourceIds.Local -> "Local"
        BuiltInSourceIds.Navidrome -> "Navidrome"
        BuiltInSourceIds.OpenSubsonic -> "OpenSubsonic"
        BuiltInSourceIds.Emby -> "Emby"
        BuiltInSourceIds.OpenList -> "OpenList"
        else -> value.replaceFirstChar { char -> char.uppercase() }
    }
}
