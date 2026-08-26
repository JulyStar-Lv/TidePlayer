package io.github.julystar.musicapp.feature.sources.presentation

import androidx.lifecycle.SavedStateHandle
import io.github.julystar.musicapp.core.domain.model.AppLanguageMode
import io.github.julystar.musicapp.core.domain.model.AppSettings
import io.github.julystar.musicapp.core.domain.model.AppThemeMode
import io.github.julystar.musicapp.core.domain.model.AudioFocusMode
import io.github.julystar.musicapp.core.domain.model.AutoScanMode
import io.github.julystar.musicapp.core.domain.model.ImportSelectionMode
import io.github.julystar.musicapp.core.domain.model.MetadataScanMode
import io.github.julystar.musicapp.core.domain.model.MissingFilePolicy
import io.github.julystar.musicapp.core.domain.model.OneDriveDriveListResult
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.SourceAccountRootSelection
import io.github.julystar.musicapp.core.domain.model.SourceEditorDraft
import io.github.julystar.musicapp.core.domain.model.SourceEditorStorageState
import io.github.julystar.musicapp.core.domain.model.StorageAccountInfo
import io.github.julystar.musicapp.core.domain.model.StoredCredential
import io.github.julystar.musicapp.core.domain.model.storageSourceAccountId
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.core.domain.repository.StorageRepository
import io.github.julystar.musicapp.core.domain.repository.ToastRepository
import io.github.julystar.musicapp.core.domain.repository.UiMessage
import io.github.julystar.musicapp.core.domain.repository.UiMessageKey
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncController
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncFailure
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncRequest
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncResult
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncTask
import io.github.julystar.musicapp.service.librarysync.domain.SourceAccountLibrarySyncController
import io.github.julystar.musicapp.service.librarysync.domain.SourceAccountLibrarySyncResult
import io.github.julystar.musicapp.source.api.BuiltInSourceIds
import io.github.julystar.musicapp.source.api.ImportRepository
import io.github.julystar.musicapp.source.api.SourceDirectorySelection
import io.github.julystar.musicapp.source.api.SourceNodeSelection
import io.github.julystar.musicapp.source.api.SourceNodeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EditStorageVMTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun otpChallengeResetSuccessAndSaveStayMemoryOnly() = runTest(dispatcher) {
        val storage = FakeEditorStorageRepository().apply {
            openListTestResults += SourceConnectionTestStatus.OtpRequired
            openListTestResults += SourceConnectionTestStatus.Success
            openListTestResults += SourceConnectionTestStatus.Success
        }
        val viewModel = createViewModel(storage)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        viewModel.onAction(SourceEditorAction.ChangeType(SourceEditorType.OpenList))
        viewModel.onAction(SourceEditorAction.OpenListAliasChanged("List"))
        viewModel.onAction(SourceEditorAction.OpenListAddressChanged("https://list.example"))
        viewModel.onAction(SourceEditorAction.OpenListUsernameChanged("alice"))
        viewModel.onAction(SourceEditorAction.OpenListPasswordChanged("password-secret"))
        runCurrent()
        assertFalse(viewModel.state.value.openList.showOtp)
        viewModel.onAction(SourceEditorAction.TestConnection)
        advanceUntilIdle()

        val challenged = viewModel.state.value
        assertTrue(challenged.openList.showOtp)
        assertFalse(challenged.openList.hasOtp)
        val challengeGeneration = challenged.otpInputGeneration

        viewModel.onAction(SourceEditorAction.OpenListOtpChanged("111111"))
        viewModel.onAction(SourceEditorAction.TestConnection)
        advanceUntilIdle()
        assertTrue(storage.openListTestOtps.last() == "111111", "OTP should reach connection test")
        assertTrue(viewModel.state.value.openList.showOtp)
        assertTrue(viewModel.state.value.openList.hasOtp)
        assertFalse(viewModel.state.value.toString().contains("111111"))

        viewModel.onAction(SourceEditorAction.OpenListAddressChanged("https://new-list.example"))
        runCurrent()
        assertFalse(viewModel.state.value.openList.hasOtp)
        assertTrue(viewModel.state.value.otpInputGeneration > challengeGeneration)
        assertFalse(viewModel.state.value.toString().contains("111111"))

        viewModel.onAction(SourceEditorAction.OpenListOtpChanged("222222"))
        viewModel.onAction(SourceEditorAction.TestConnection)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.openList.showOtp)
        viewModel.onAction(SourceEditorAction.Save)
        advanceUntilIdle()

        assertTrue(storage.savedOpenListOtp == "222222", "OTP should reach save in memory")
        assertFalse(storage.savedOpenListDraft.toString().contains("222222"))
        assertEquals(
            SourceEditorEvent.OpenLibraryFolderImport(storageSourceAccountId(21)),
            viewModel.events.first(),
        )
        assertFalse(viewModel.state.value.openList.showOtp)
        assertFalse(viewModel.state.value.openList.hasOtp)
    }

    @Test
    fun existingOtpAccountFailsFastAndGuestClearsVisibleOtp() = runTest(dispatcher) {
        val storage = FakeEditorStorageRepository().apply {
            editorState = SourceEditorStorageState(
                accountId = storageSourceAccountId(7),
                draft = SourceEditorDraft(
                    id = 7,
                    storageType = SourceEditorType.OpenList,
                    alias = "List",
                    address = "https://list.example",
                    username = "alice",
                ),
                title = "List",
                musicCount = 0u,
                isOneDrive = false,
                requiresOtp = true,
            )
        }
        val viewModel = createViewModel(storage, id = 7)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        advanceUntilIdle()

        assertTrue(viewModel.state.value.openList.showOtp)
        viewModel.onAction(SourceEditorAction.Save)
        advanceUntilIdle()
        assertEquals(SourceConnectionTestStatus.OtpRequired, viewModel.state.value.testStatus)
        assertEquals(0, storage.openListSaveCalls)

        viewModel.onAction(SourceEditorAction.OpenListOtpChanged("333333"))
        runCurrent()
        val previousGeneration = viewModel.state.value.otpInputGeneration
        viewModel.onAction(SourceEditorAction.OpenListGuestChanged(true))
        runCurrent()
        assertTrue(viewModel.state.value.openList.isGuest)
        assertFalse(viewModel.state.value.openList.showOtp)
        assertFalse(viewModel.state.value.openList.hasOtp)
        assertTrue(viewModel.state.value.otpInputGeneration > previousGeneration)
        assertEquals("", viewModel.state.value.openList.username)
    }

    @Test
    fun otpVisibleResetGenerationCoversUsernamePasswordAndTypeChanges() = runTest(dispatcher) {
        val storage = FakeEditorStorageRepository().apply {
            openListTestResults += SourceConnectionTestStatus.OtpRequired
        }
        val viewModel = createViewModel(storage)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        viewModel.onAction(SourceEditorAction.ChangeType(SourceEditorType.OpenList))
        viewModel.onAction(SourceEditorAction.OpenListAliasChanged("List"))
        viewModel.onAction(SourceEditorAction.OpenListAddressChanged("https://list.example"))
        viewModel.onAction(SourceEditorAction.OpenListUsernameChanged("alice"))
        viewModel.onAction(SourceEditorAction.OpenListPasswordChanged("password"))
        viewModel.onAction(SourceEditorAction.TestConnection)
        advanceUntilIdle()

        viewModel.onAction(SourceEditorAction.OpenListOtpChanged("111111"))
        runCurrent()
        var generation = viewModel.state.value.otpInputGeneration
        viewModel.onAction(SourceEditorAction.OpenListUsernameChanged("bob"))
        runCurrent()
        assertFalse(viewModel.state.value.openList.hasOtp)
        assertTrue(viewModel.state.value.otpInputGeneration > generation)

        viewModel.onAction(SourceEditorAction.OpenListOtpChanged("222222"))
        runCurrent()
        generation = viewModel.state.value.otpInputGeneration
        viewModel.onAction(SourceEditorAction.OpenListPasswordChanged("new-password"))
        runCurrent()
        assertFalse(viewModel.state.value.openList.hasOtp)
        assertTrue(viewModel.state.value.otpInputGeneration > generation)

        viewModel.onAction(SourceEditorAction.OpenListOtpChanged("333333"))
        runCurrent()
        generation = viewModel.state.value.otpInputGeneration
        viewModel.onAction(SourceEditorAction.ChangeType(SourceEditorType.Emby))
        runCurrent()
        assertFalse(viewModel.state.value.openList.hasOtp)
        assertFalse(viewModel.state.value.openList.showOtp)
        assertTrue(viewModel.state.value.otpInputGeneration > generation)
    }

    @Test
    fun otpFailureAndRechallengeClearStaleCodeButKeepPromptVisible() = runTest(dispatcher) {
        val storage = FakeEditorStorageRepository().apply {
            openListTestResults += SourceConnectionTestStatus.OtpRequired
            openListTestResults += SourceConnectionTestStatus.Unauthorized
            openListTestResults += SourceConnectionTestStatus.OtpRequired
        }
        val viewModel = createViewModel(storage)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        viewModel.onAction(SourceEditorAction.ChangeType(SourceEditorType.OpenList))
        viewModel.onAction(SourceEditorAction.OpenListAliasChanged("List"))
        viewModel.onAction(SourceEditorAction.OpenListAddressChanged("https://list.example"))
        viewModel.onAction(SourceEditorAction.OpenListUsernameChanged("alice"))
        viewModel.onAction(SourceEditorAction.OpenListPasswordChanged("password"))
        viewModel.onAction(SourceEditorAction.TestConnection)
        advanceUntilIdle()

        viewModel.onAction(SourceEditorAction.OpenListOtpChanged("111111"))
        runCurrent()
        var generation = viewModel.state.value.otpInputGeneration
        viewModel.onAction(SourceEditorAction.TestConnection)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.openList.showOtp)
        assertFalse(viewModel.state.value.openList.hasOtp)
        assertTrue(viewModel.state.value.otpInputGeneration > generation)

        viewModel.onAction(SourceEditorAction.OpenListOtpChanged("222222"))
        runCurrent()
        generation = viewModel.state.value.otpInputGeneration
        viewModel.onAction(SourceEditorAction.TestConnection)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.openList.showOtp)
        assertFalse(viewModel.state.value.openList.hasOtp)
        assertTrue(viewModel.state.value.otpInputGeneration > generation)
        assertTrue(
            storage.openListTestOtps == listOf("", "111111", "222222"),
            "OTP challenge attempts should be forwarded in order",
        )
    }

    @Test
    fun embyEditKeepsPasswordEmptyAndShowsVerifiedIdentity() = runTest(dispatcher) {
        val storage = FakeEditorStorageRepository().apply {
            editorState = SourceEditorStorageState(
                accountId = storageSourceAccountId(8),
                draft = SourceEditorDraft(
                    id = 8,
                    storageType = SourceEditorType.Emby,
                    alias = "Emby",
                    address = "https://emby.example",
                    username = "connected-user",
                    externalAccountId = "user-id-8",
                    secondaryBaseUrl = "https://secondary.example",
                ),
                title = "Emby",
                musicCount = 0u,
                isOneDrive = false,
                connectedServerName = "Verified Emby",
            )
            credential = StoredCredential("connected-user", "stored-token", false)
        }
        val viewModel = createViewModel(storage, id = 8)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        advanceUntilIdle()

        assertEquals("Verified Emby", viewModel.state.value.emby.serverName)
        assertEquals("user-id-8", viewModel.state.value.emby.connectedUserId)
        assertFalse(viewModel.state.value.toString().contains("stored-token"))
        viewModel.onAction(SourceEditorAction.TestConnection)
        advanceUntilIdle()
        assertEquals("", storage.testDrafts.single().secret)
        viewModel.onAction(SourceEditorAction.Save)
        advanceUntilIdle()
        assertEquals("", storage.savedDraft.secret)
    }

    @Test
    fun newEmbyActionsMapEditableFieldsAndAuthenticationToDraft() = runTest(dispatcher) {
        val storage = FakeEditorStorageRepository()
        val viewModel = createViewModel(storage)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        viewModel.onAction(SourceEditorAction.ChangeType(SourceEditorType.Emby))
        viewModel.onAction(SourceEditorAction.RemoteServerAliasChanged("Emby home"))
        viewModel.onAction(SourceEditorAction.RemoteServerAddressChanged("https://emby.example"))
        viewModel.onAction(SourceEditorAction.RemoteServerUsernameChanged("listener"))
        viewModel.onAction(SourceEditorAction.RemoteServerPasswordChanged("new-auth-value"))
        viewModel.onAction(
            SourceEditorAction.RemoteServerSecondaryAddressChanged("https://backup.example")
        )
        viewModel.onAction(SourceEditorAction.TestConnection)
        advanceUntilIdle()

        val draft = storage.testDrafts.single()
        assertEquals(SourceEditorType.Emby, draft.storageType)
        assertEquals("Emby home", draft.alias)
        assertEquals("https://emby.example", draft.address)
        assertEquals("listener", draft.username)
        assertTrue(draft.secret.isNotEmpty(), "authentication action should reach draft")
        assertEquals("https://backup.example", draft.secondaryBaseUrl)
    }

    @Test
    fun remoteServerAdvancedActionsMapToDraftForBothSubsonicProviders() = runTest(dispatcher) {
        for (type in listOf(SourceEditorType.Navidrome, SourceEditorType.OpenSubsonic)) {
            val storage = FakeEditorStorageRepository()
            val viewModel = createViewModel(storage)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
            viewModel.onAction(SourceEditorAction.ChangeType(type))
            runCurrent()
            assertFalse(viewModel.state.value.remoteServer.remoteWriteEnabled)
            viewModel.onAction(SourceEditorAction.RemoteServerAliasChanged(type.name))
            viewModel.onAction(SourceEditorAction.RemoteServerAddressChanged("https://primary.example"))
            viewModel.onAction(SourceEditorAction.RemoteServerUsernameChanged("alice"))
            viewModel.onAction(SourceEditorAction.RemoteServerPasswordChanged("password"))
            viewModel.onAction(SourceEditorAction.RemoteServerSecondaryAddressChanged("https://secondary.example"))
            viewModel.onAction(SourceEditorAction.RemoteServerStreamBitRateChanged(192))
            viewModel.onAction(SourceEditorAction.RemoteServerDownloadBitRateChanged(320))
            viewModel.onAction(SourceEditorAction.RemoteServerCoverArtSizeChanged(768))
            viewModel.onAction(SourceEditorAction.RemoteServerWriteChanged(true))
            viewModel.onAction(SourceEditorAction.TestConnection)
            advanceUntilIdle()

            val draft = storage.testDrafts.single()
            assertEquals(type, draft.storageType)
            assertEquals(type.name, draft.alias)
            assertEquals("https://primary.example", draft.address)
            assertEquals("alice", draft.username)
            assertTrue(draft.secret.isNotEmpty(), "credential action should reach draft")
            assertEquals("https://secondary.example", draft.secondaryBaseUrl)
            assertEquals(192, draft.streamMaxBitRate)
            assertEquals(320, draft.downloadMaxBitRate)
            assertEquals(768, draft.coverArtSize)
            assertTrue(draft.remoteWriteEnabled)
        }
    }

    @Test
    fun localAndOpenListPickerTargetExactAccountAndForwardRawSelection() = runTest(dispatcher) {
        val openListId = storageSourceAccountId(9)
        val localId = storageSourceAccountId(1)
        val storage = FakeEditorStorageRepository().apply {
            accounts.value = listOf(
                StorageAccountInfo(
                    accountId = localId,
                    sourceId = BuiltInSourceIds.Local,
                    isLocal = true,
                    isOneDrive = false,
                    title = "Local",
                    subtitle = "",
                    musicCount = 0,
                )
            )
            editorState = SourceEditorStorageState(
                accountId = openListId,
                draft = SourceEditorDraft(
                    id = 9,
                    storageType = SourceEditorType.OpenList,
                    alias = "List",
                    address = "https://list.example",
                    username = "alice",
                ),
                title = "List",
                musicCount = 0u,
                isOneDrive = false,
            )
        }
        val imports = FakeImportRepository()
        val syncedAccounts = mutableListOf<SourceAccountId>()
        val accountSync = SourceAccountLibrarySyncController { accountId ->
            syncedAccounts += accountId
            SourceAccountLibrarySyncResult(0, 0, 0)
        }
        val folderSync = FakeLibrarySyncController()
        val viewModel = createViewModel(
            storage,
            id = 9,
            imports = imports,
            librarySync = folderSync,
            accountSync = accountSync,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.onAction(SourceEditorAction.ImportLibraryFolder)
        advanceUntilIdle()
        assertEquals(openListId, imports.preparedAccountId)
        val selections = listOf(
            SourceDirectorySelection(
                sourceId = BuiltInSourceIds.OpenList,
                accountId = openListId,
                path = "/原始 路径/音乐",
                remoteId = "raw::目录/音乐",
            ),
            SourceDirectorySelection(
                sourceId = BuiltInSourceIds.OpenList,
                accountId = openListId,
                path = "/第二 根/无改写",
                remoteId = "opaque::第二根?id=%2Fraw",
            ),
        )
        imports.finishAll(selections)
        advanceUntilIdle()
        assertEquals(
            openListId to selections.map { SourceAccountRootSelection(it.remoteId, it.path) },
            storage.replacedRootSelections.single(),
        )
        assertEquals(emptyList(), syncedAccounts)
        assertEquals(
            selections.map { selection ->
                listOf(
                    selection.accountId.value,
                    selection.remoteId,
                    selection.path,
                    selection.path,
                )
            },
            folderSync.requests.map { request ->
                listOf(
                    request.accountId.value,
                    request.selectedFolderRemoteId,
                    request.selectedFolderCanonicalPath,
                    request.selectedFolderDisplayPath,
                )
            },
        )

        viewModel.onAction(SourceEditorAction.ImportLocalLibraryFolder)
        advanceUntilIdle()
        assertEquals(localId, imports.preparedAccountId)
    }

    @Test
    fun newOpenListSaveImmediatelyOpensExactAccountPickerAndSyncsMultipleRawRoots() =
        runTest(dispatcher) {
            val savedAccountId = storageSourceAccountId(21)
            val storage = FakeEditorStorageRepository()
            val imports = FakeImportRepository()
            val folderSync = FakeLibrarySyncController()
            val accountSyncCalls = mutableListOf<SourceAccountId>()
            val viewModel = createViewModel(
                storage = storage,
                imports = imports,
                librarySync = folderSync,
                accountSync = SourceAccountLibrarySyncController { accountId ->
                    accountSyncCalls += accountId
                    SourceAccountLibrarySyncResult(0, 0, 0)
                },
            )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.state.collect {}
            }

            viewModel.onAction(SourceEditorAction.ChangeType(SourceEditorType.OpenList))
            viewModel.onAction(SourceEditorAction.OpenListAliasChanged("OpenList"))
            viewModel.onAction(SourceEditorAction.OpenListAddressChanged("https://list.example"))
            viewModel.onAction(SourceEditorAction.OpenListUsernameChanged("listener"))
            viewModel.onAction(SourceEditorAction.OpenListPasswordChanged("credential-value"))
            viewModel.onAction(SourceEditorAction.TestConnection)
            runCurrent()
            assertEquals(SourceConnectionTestStatus.Success, viewModel.state.value.testStatus)
            advanceUntilIdle()
            assertEquals(SourceConnectionTestStatus.None, viewModel.state.value.testStatus)

            viewModel.onAction(SourceEditorAction.Save)
            advanceUntilIdle()

            val navigationEvent = viewModel.events.first()
            assertEquals(SourceEditorEvent.OpenLibraryFolderImport(savedAccountId), navigationEvent)
            val pickerAccounts = mutableListOf<SourceAccountId>()
            dispatchSourceEditorNavigation(
                event = navigationEvent,
                onNavigateBack = { error("OpenList save must open the folder picker") },
                onNavigateToLibraryFolderImport = pickerAccounts::add,
                onOpenUri = { error("OpenList save must not open a URI") },
            )
            assertEquals(listOf(savedAccountId), pickerAccounts)
            assertEquals(savedAccountId, imports.preparedAccountId)
            assertFalse(viewModel.state.value.isCreated)

            val selections = listOf(
                SourceDirectorySelection(
                    sourceId = BuiltInSourceIds.OpenList,
                    accountId = savedAccountId,
                    path = "/未改写 path/一",
                    remoteId = "opaque://root-one?sig=%2Fkeep",
                ),
                SourceDirectorySelection(
                    sourceId = BuiltInSourceIds.OpenList,
                    accountId = savedAccountId,
                    path = "/未改写 path/二",
                    remoteId = "opaque::root-two/原始",
                ),
            )
            imports.finishAll(selections)
            advanceUntilIdle()

            assertEquals(
                savedAccountId to selections.map {
                    SourceAccountRootSelection(remoteId = it.remoteId, path = it.path)
                },
                storage.replacedRootSelections.single(),
            )
            assertEquals(emptyList(), accountSyncCalls)
            assertEquals(2, folderSync.requests.size)
            folderSync.requests.zip(selections).forEach { (request, selection) ->
                assertEquals(selection.accountId, request.accountId)
                assertEquals(selection.remoteId, request.selectedFolderRemoteId)
                assertEquals(selection.path, request.selectedFolderCanonicalPath)
                assertEquals(selection.path, request.selectedFolderDisplayPath)
            }
        }

    @Test
    fun folderSyncCancellationReportsCancelledWithoutReloadOrSuccess() = runTest(dispatcher) {
        val accountId = storageSourceAccountId(10)
        val storage = FakeEditorStorageRepository().apply {
            editorState = SourceEditorStorageState(
                accountId = accountId,
                draft = SourceEditorDraft(
                    id = 10,
                    storageType = SourceEditorType.WebDav,
                    alias = "WebDAV",
                    address = "https://dav.example",
                    username = "alice",
                ),
                title = "WebDAV",
                musicCount = 0u,
                isOneDrive = false,
            )
        }
        val imports = FakeImportRepository()
        val accountSync = SourceAccountLibrarySyncController {
            throw CancellationException("cancelled")
        }
        val toasts = FakeToastRepository()
        val viewModel = createViewModel(
            storage = storage,
            id = 10,
            imports = imports,
            accountSync = accountSync,
            toasts = toasts,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.onAction(SourceEditorAction.ImportLibraryFolder)
        advanceUntilIdle()
        imports.finish(
            SourceDirectorySelection(
                sourceId = BuiltInSourceIds.WebDav,
                accountId = accountId,
                path = "/Music",
                remoteId = "raw-music",
            )
        )
        advanceUntilIdle()

        assertEquals(
            listOf(UiMessageKey.LibraryImportStarted, UiMessageKey.LibraryImportCancelled),
            toasts.keys,
        )
        assertEquals(0, storage.reloadCalls)
    }

    @Test
    fun dashboardSyncPassesOnlyAccountIdAndRejectsDuplicateTap() = runTest(dispatcher) {
        val accountId = storageSourceAccountId(30)
        val storage = FakeEditorStorageRepository().apply {
            accounts.value = listOf(remoteAccount(accountId, BuiltInSourceIds.Navidrome))
        }
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = mutableListOf<SourceAccountId>()
        val controller = SourceAccountLibrarySyncController { requested ->
            calls += requested
            started.complete(Unit)
            release.await()
            SourceAccountLibrarySyncResult(4, 2, 0)
        }
        val toasts = FakeToastRepository()
        val viewModel = SourcesViewModel(storage, controller, toasts)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.onAction(SourcesAction.SyncSource(accountId))
        viewModel.onAction(SourcesAction.SyncSource(accountId))
        runCurrent()
        started.await()
        assertEquals(listOf(accountId), calls)
        assertTrue(viewModel.state.value.sources.single().isSyncing)

        release.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.sources.single().isSyncing)
        assertEquals(2, storage.reloadCalls)
        assertEquals(
            listOf(UiMessageKey.LibraryImportStarted, UiMessageKey.LibraryImportCompleted),
            toasts.keys,
        )
    }

    @Test
    fun dashboardAddSourceActionOpensNewSourceEditor() = runTest(dispatcher) {
        val viewModel = SourcesViewModel(
            FakeEditorStorageRepository(),
            SourceAccountLibrarySyncController {
                SourceAccountLibrarySyncResult(0, 0, 0)
            },
            FakeToastRepository(),
        )

        viewModel.onAction(SourcesAction.AddSource)

        assertEquals(SourcesEvent.OpenNewSourceEditor, viewModel.events.first())
    }

    @Test
    fun addNavidromeSaveBackCardAndReopenTraverseDashboardAndEditorContracts() =
        runTest(dispatcher) {
            val storage = FakeEditorStorageRepository()
            val dashboard = SourcesViewModel(
                storage,
                SourceAccountLibrarySyncController {
                    SourceAccountLibrarySyncResult(0, 0, 0)
                },
                FakeToastRepository(),
            )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                dashboard.state.collect {}
            }
            advanceUntilIdle()

            dashboard.onAction(SourcesAction.AddSource)
            val addRoutes = mutableListOf<Long>()
            dispatchSourcesNavigation(dashboard.events.first(), addRoutes::add)
            assertEquals(listOf(-1L), addRoutes)

            val editor = createViewModel(storage)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                editor.state.collect {}
            }
            editor.onAction(SourceEditorAction.ChangeType(SourceEditorType.Navidrome))
            editor.onAction(SourceEditorAction.RemoteServerAliasChanged("Navidrome home"))
            editor.onAction(SourceEditorAction.RemoteServerAddressChanged("https://music.example/api"))
            editor.onAction(SourceEditorAction.RemoteServerUsernameChanged("listener"))
            editor.onAction(SourceEditorAction.RemoteServerPasswordChanged("credential-secret"))
            editor.onAction(SourceEditorAction.Save)
            advanceUntilIdle()

            var backCalls = 0
            dispatchSourceEditorNavigation(
                event = editor.events.first(),
                onNavigateBack = { backCalls += 1 },
                onNavigateToLibraryFolderImport = { error("Navidrome save must navigate back") },
                onOpenUri = { error("Navidrome save must not open a URI") },
            )
            assertEquals(1, backCalls)

            val card = dashboard.state.value.sources.single()
            assertEquals("Navidrome home", card.title)
            assertEquals("Navidrome", card.sourceType)
            assertEquals("https://music.example", card.safeEndpoint)
            assertFalse("credential-secret" in card.toString())

            dashboard.onAction(SourcesAction.OpenSource(card.id))
            val reopenRoutes = mutableListOf<Long>()
            dispatchSourcesNavigation(dashboard.events.first(), reopenRoutes::add)
            assertEquals(listOf(20L), reopenRoutes)
        }

    @Test
    fun existingServerEditorSyncPassesExactAccountAndRejectsDuplicateTap() = runTest(dispatcher) {
        val accountId = storageSourceAccountId(33)
        val storage = FakeEditorStorageRepository().apply {
            editorState = SourceEditorStorageState(
                accountId = accountId,
                draft = SourceEditorDraft(
                    id = 33,
                    storageType = SourceEditorType.Navidrome,
                    alias = "Navidrome",
                    address = "https://music.example",
                    username = "alice",
                ),
                title = "Navidrome",
                musicCount = 0u,
                isOneDrive = false,
            )
        }
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = mutableListOf<SourceAccountId>()
        val controller = SourceAccountLibrarySyncController { requested ->
            calls += requested
            started.complete(Unit)
            release.await()
            SourceAccountLibrarySyncResult(3, 1, 0)
        }
        val toasts = FakeToastRepository()
        val viewModel = createViewModel(
            storage = storage,
            id = 33,
            accountSync = controller,
            toasts = toasts,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        advanceUntilIdle()
        assertTrue(viewModel.state.value.canSyncCurrentServer)

        viewModel.onAction(SourceEditorAction.SyncNow)
        viewModel.onAction(SourceEditorAction.SyncNow)
        runCurrent()
        started.await()
        assertEquals(listOf(accountId), calls)
        assertTrue(viewModel.state.value.isSyncing)

        release.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isSyncing)
        assertEquals(1, storage.reloadCalls)
        assertEquals(
            listOf(UiMessageKey.LibraryImportStarted, UiMessageKey.LibraryImportCompleted),
            toasts.keys,
        )
    }

    @Test
    fun existingNavidromeRejectsForgedProviderChangeWithoutMutatingDraft() = runTest(dispatcher) {
        val persistedDraft = SourceEditorDraft(
            id = 34,
            storageType = SourceEditorType.Navidrome,
            alias = "Navidrome home",
            address = "https://navidrome.example",
            username = "alice",
            secondaryBaseUrl = "https://backup.example",
            streamMaxBitRate = 192,
            downloadMaxBitRate = 320,
            coverArtSize = 768,
            remoteWriteEnabled = true,
        )
        val storage = FakeEditorStorageRepository().apply {
            editorState = SourceEditorStorageState(
                accountId = storageSourceAccountId(34),
                draft = persistedDraft,
                title = persistedDraft.alias,
                musicCount = 12u,
                isOneDrive = false,
            )
        }
        val viewModel = createViewModel(storage, id = 34)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        advanceUntilIdle()
        val before = viewModel.state.value
        assertFalse(before.isCreated)
        assertTrue(before.canSyncCurrentServer)

        viewModel.onAction(SourceEditorAction.ChangeType(SourceEditorType.OpenList))
        runCurrent()

        assertEquals(before, viewModel.state.value)
        viewModel.onAction(SourceEditorAction.Save)
        advanceUntilIdle()
        assertEquals(persistedDraft, storage.savedDraft)
    }

    @Test
    fun existingOpenListRejectsForgedProviderChangeWithoutClearingOtpOrDraft() =
        runTest(dispatcher) {
            val persistedDraft = SourceEditorDraft(
                id = 35,
                storageType = SourceEditorType.OpenList,
                alias = "OpenList home",
                address = "https://openlist.example",
                username = "listener",
            )
            val storage = FakeEditorStorageRepository().apply {
                editorState = SourceEditorStorageState(
                    accountId = storageSourceAccountId(35),
                    draft = persistedDraft,
                    title = persistedDraft.alias,
                    musicCount = 7u,
                    isOneDrive = false,
                    requiresOtp = true,
                )
            }
            val viewModel = createViewModel(storage, id = 35)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.state.collect {}
            }
            advanceUntilIdle()
            viewModel.onAction(SourceEditorAction.OpenListOtpChanged("654321"))
            runCurrent()
            val before = viewModel.state.value
            assertFalse(before.isCreated)
            assertTrue(before.openList.showOtp)
            assertTrue(before.openList.hasOtp)

            viewModel.onAction(SourceEditorAction.ChangeType(SourceEditorType.Navidrome))
            runCurrent()

            assertEquals(before, viewModel.state.value)
            viewModel.onAction(SourceEditorAction.Save)
            advanceUntilIdle()
            assertEquals(persistedDraft, storage.savedOpenListDraft)
            assertEquals("654321", storage.savedOpenListOtp)
        }

    @Test
    fun newEditorStillAllowsProviderChanges() = runTest(dispatcher) {
        val viewModel = createViewModel(FakeEditorStorageRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        viewModel.onAction(SourceEditorAction.ChangeType(SourceEditorType.OpenList))
        runCurrent()
        assertEquals(SourceEditorType.OpenList, viewModel.state.value.storageType)

        viewModel.onAction(SourceEditorAction.ChangeType(SourceEditorType.Navidrome))
        runCurrent()
        assertEquals(SourceEditorType.Navidrome, viewModel.state.value.storageType)
        assertTrue(viewModel.state.value.isCreated)
    }

    @Test
    fun newEditorStartsWithProviderRequestedByRoute() = runTest(dispatcher) {
        val viewModel = createViewModel(
            storage = FakeEditorStorageRepository(),
            sourceType = SourceEditorType.Emby.name,
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }

        runCurrent()

        assertEquals(SourceEditorType.Emby, viewModel.state.value.storageType)
        assertTrue(viewModel.state.value.isCreated)
    }

    @Test
    fun dashboardFailureAndCancellationNeverReportSuccess() = runTest(dispatcher) {
        val failedId = storageSourceAccountId(31)
        val cancelledId = storageSourceAccountId(32)
        val storage = FakeEditorStorageRepository().apply {
            accounts.value = listOf(
                remoteAccount(failedId, BuiltInSourceIds.OpenSubsonic),
                remoteAccount(cancelledId, BuiltInSourceIds.Emby),
            )
        }
        val controller = SourceAccountLibrarySyncController { requested ->
            if (requested == cancelledId) throw CancellationException("cancelled")
            error("sync failed")
        }
        val toasts = FakeToastRepository()
        val viewModel = SourcesViewModel(storage, controller, toasts)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        advanceUntilIdle()

        viewModel.onAction(SourcesAction.SyncSource(failedId))
        advanceUntilIdle()
        viewModel.onAction(SourcesAction.SyncSource(cancelledId))
        advanceUntilIdle()

        assertTrue(UiMessageKey.LibraryImportFailed in toasts.keys)
        assertTrue(UiMessageKey.LibraryImportCancelled in toasts.keys)
        assertFalse(UiMessageKey.LibraryImportCompleted in toasts.keys)
        assertEquals(1, storage.reloadCalls)
    }

    private fun createViewModel(
        storage: FakeEditorStorageRepository,
        id: Long = -1,
        sourceType: String? = null,
        imports: FakeImportRepository = FakeImportRepository(),
        librarySync: FakeLibrarySyncController = FakeLibrarySyncController(),
        accountSync: SourceAccountLibrarySyncController = SourceAccountLibrarySyncController {
            SourceAccountLibrarySyncResult(0, 0, 0)
        },
        toasts: FakeToastRepository = FakeToastRepository(),
    ) = EditStorageVM(
        storageRepository = storage,
        toastRepository = toasts,
        importRepository = imports,
        librarySyncController = librarySync,
        sourceAccountLibrarySyncController = accountSync,
        settingsRepository = FakeSettingsRepository(),
        savedStateHandle = SavedStateHandle(
            mapOf(
                "id" to id,
                "sourceType" to sourceType,
            ),
        ),
    )

    private fun remoteAccount(accountId: SourceAccountId, sourceId: io.github.julystar.musicapp.core.domain.model.SourceId) =
        StorageAccountInfo(
            accountId = accountId,
            sourceId = sourceId,
            isLocal = false,
            isOneDrive = false,
            title = sourceId.value,
            subtitle = "https://example.test",
            musicCount = 0,
        )
}

private class FakeEditorStorageRepository : StorageRepository {
    val accounts = MutableStateFlow<List<StorageAccountInfo>>(emptyList())
    override val storageAccounts = accounts
    override val onRemoveStorageEvent = MutableSharedFlow<Unit>()
    override val oauthRefreshToken = MutableStateFlow("")
    var editorState: SourceEditorStorageState? = null
    var credential: StoredCredential? = null
    val openListTestResults = ArrayDeque<SourceConnectionTestStatus>()
    val openListTestOtps = mutableListOf<String>()
    val testDrafts = mutableListOf<SourceEditorDraft>()
    lateinit var savedDraft: SourceEditorDraft
    lateinit var savedOpenListDraft: SourceEditorDraft
    var savedOpenListOtp: String? = null
    var openListSaveCalls = 0
    var reloadCalls = 0
    val replacedRoots = mutableListOf<Pair<SourceAccountId, List<String>>>()
    val replacedRootSelections =
        mutableListOf<Pair<SourceAccountId, List<SourceAccountRootSelection>>>()

    override suspend fun reload() {
        reloadCalls += 1
    }
    override suspend fun startOneDriveOAuth(): String = ""
    override suspend fun upsertSource(draft: SourceEditorDraft): SourceAccountId {
        savedDraft = draft
        val accountId = draft.id?.let(::storageSourceAccountId) ?: storageSourceAccountId(20)
        publishSavedAccount(accountId, draft)
        return accountId
    }
    override suspend fun upsertOpenListSource(draft: SourceEditorDraft, otpCode: String): SourceAccountId {
        openListSaveCalls += 1
        savedOpenListDraft = draft
        savedOpenListOtp = otpCode
        val accountId = draft.id?.let(::storageSourceAccountId) ?: storageSourceAccountId(21)
        publishSavedAccount(accountId, draft)
        return accountId
    }
    override suspend fun loadEditorState(id: Long): SourceEditorStorageState? = editorState
    override suspend fun testSource(draft: SourceEditorDraft): SourceConnectionTestStatus {
        testDrafts += draft
        return SourceConnectionTestStatus.Success
    }
    override suspend fun testOpenListSource(
        draft: SourceEditorDraft,
        otpCode: String,
    ): SourceConnectionTestStatus {
        openListTestOtps += otpCode
        return openListTestResults.removeFirstOrNull() ?: SourceConnectionTestStatus.Success
    }
    override suspend fun listOneDriveDriveInfos(refreshToken: String) =
        OneDriveDriveListResult(emptyList(), refreshToken)
    override suspend fun updateOneDriveRefreshTokenByAccountId(
        accountId: SourceAccountId,
        refreshToken: String,
    ) = Unit
    override fun findStorageAccountByAccountId(accountId: SourceAccountId) =
        accounts.value.firstOrNull { it.accountId == accountId }
    override suspend fun loadCredentialByAccountId(accountId: SourceAccountId) = credential
    override suspend fun setAccountRootPath(accountId: SourceAccountId, rootPath: String) = Unit
    override suspend fun replaceAccountRootPaths(accountId: SourceAccountId, rootPaths: List<String>) {
        replacedRoots += accountId to rootPaths
    }
    override suspend fun replaceAccountRootSelections(
        accountId: SourceAccountId,
        selections: List<SourceAccountRootSelection>,
    ) {
        replacedRootSelections += accountId to selections
    }
    override suspend fun listAccountRootPaths(accountId: SourceAccountId): List<String> = emptyList()
    override suspend fun removeByAccountId(accountId: SourceAccountId) = Unit

    private fun publishSavedAccount(accountId: SourceAccountId, draft: SourceEditorDraft) {
        val sourceId = when (draft.storageType) {
            SourceEditorType.WebDav -> BuiltInSourceIds.WebDav
            SourceEditorType.OneDrive -> BuiltInSourceIds.OneDrive
            SourceEditorType.Smb -> BuiltInSourceIds.Smb
            SourceEditorType.Navidrome -> BuiltInSourceIds.Navidrome
            SourceEditorType.OpenSubsonic -> BuiltInSourceIds.OpenSubsonic
            SourceEditorType.Emby -> BuiltInSourceIds.Emby
            SourceEditorType.OpenList -> BuiltInSourceIds.OpenList
        }
        accounts.value = accounts.value.filterNot { it.accountId == accountId } + StorageAccountInfo(
            accountId = accountId,
            sourceId = sourceId,
            isLocal = false,
            isOneDrive = draft.storageType == SourceEditorType.OneDrive,
            title = draft.alias,
            subtitle = draft.address,
            musicCount = 0,
        )
    }
}

private class FakeImportRepository : ImportRepository {
    override val allowTypes = MutableStateFlow<List<SourceNodeType>>(emptyList())
    override val selectionMode = MutableStateFlow(ImportSelectionMode.Entries)
    override val currentDirectoryAccountId = MutableStateFlow<SourceAccountId?>(null)
    var preparedAccountId: SourceAccountId? = null
    private var callback: ((SourceDirectorySelection) -> Unit)? = null
    private var directoriesCallback: ((List<SourceDirectorySelection>) -> Unit)? = null

    override fun prepare(types: List<SourceNodeType>, block: (List<SourceNodeSelection>) -> Unit) = Unit
    override fun prepareCurrentDirectory(
        accountId: SourceAccountId?,
        block: (SourceDirectorySelection) -> Unit,
    ) {
        preparedAccountId = accountId
        callback = block
        directoriesCallback = null
    }
    override fun prepareDirectories(
        accountId: SourceAccountId?,
        block: (List<SourceDirectorySelection>) -> Unit,
    ) {
        preparedAccountId = accountId
        callback = null
        directoriesCallback = block
    }
    override fun onFinish(entries: List<SourceNodeSelection>) = Unit
    override fun onFinishCurrentDirectory(selection: SourceDirectorySelection) = Unit
    fun finish(selection: SourceDirectorySelection) {
        callback?.invoke(selection) ?: requireNotNull(directoriesCallback)(listOf(selection))
    }
    fun finishAll(selections: List<SourceDirectorySelection>) =
        requireNotNull(directoriesCallback)(selections)
}

private class FakeLibrarySyncController : LibrarySyncController {
    override val recentTasks: Flow<List<LibrarySyncTask>> = emptyFlow()
    val requests = mutableListOf<LibrarySyncRequest>()
    var failure: Throwable? = null
    override fun observeFailures(taskId: String): Flow<List<LibrarySyncFailure>> = emptyFlow()
    override suspend fun syncFolder(request: LibrarySyncRequest): LibrarySyncResult {
        requests += request
        failure?.let { throw it }
        return LibrarySyncResult("scan", 1, 1, 1, 0, 1, 0)
    }
    override suspend fun pause(scanId: String) = false
    override suspend fun cancel(scanId: String) = false
    override suspend fun cancelAll() = Unit
    override suspend fun recoverInterruptedTasks() = 0
    override suspend fun resume(scanId: String): LibrarySyncResult? = null
    override suspend fun retry(scanId: String): LibrarySyncResult? = null
}

private class FakeToastRepository : ToastRepository {
    override val messages: Flow<UiMessage> = emptyFlow()
    val emitted = mutableListOf<UiMessage>()
    override fun emit(message: UiMessage) {
        emitted += message
    }
    val keys: List<UiMessageKey>
        get() = emitted.filterIsInstance<UiMessage.Resource>().map(UiMessage.Resource::key)
}

private class FakeSettingsRepository : SettingsRepository {
    override val settings: Flow<AppSettings> = MutableStateFlow(AppSettings.Default)
    override suspend fun setThemeMode(mode: AppThemeMode) = Unit
    override suspend fun setArtworkThemeEnabled(enabled: Boolean) = Unit
    override suspend fun setManualThemeSeedArgb(argb: Long) = Unit
    override suspend fun setCustomThemeSeedArgbValues(argbValues: List<Long>) = Unit
    override suspend fun setLanguageMode(mode: AppLanguageMode) = Unit
    override suspend fun setAudioFocusMode(mode: AudioFocusMode) = Unit
    override suspend fun setPauseOnDisconnect(enabled: Boolean) = Unit
    override suspend fun setGaplessPlaybackEnabled(enabled: Boolean) = Unit
    override suspend fun setRetryPlaybackOnFailure(enabled: Boolean) = Unit
    override suspend fun setResumePlaybackAfterNetworkRecovery(enabled: Boolean) = Unit
    override suspend fun setKeepScreenOnInPlayer(enabled: Boolean) = Unit
    override suspend fun setLyricTextAlignment(
        alignment: io.github.julystar.musicapp.core.domain.model.LyricTextAlignment,
    ) = Unit
    override suspend fun setLyricPrimaryFontScalePercent(value: Int) = Unit
    override suspend fun setLyricPrimaryFontSizeSp(value: Int) = Unit
    override suspend fun setLyricSecondaryFontScalePercent(value: Int) = Unit
    override suspend fun setLyricSecondaryFontSizeSp(value: Int) = Unit
    override suspend fun setLyricTranslationVisible(visible: Boolean) = Unit
    override suspend fun setLyricWordLiftEnabled(enabled: Boolean) = Unit
    override suspend fun setLyricBlurEffectEnabled(enabled: Boolean) = Unit
    override suspend fun setLyricPerspectiveEffectEnabled(enabled: Boolean) = Unit
    override suspend fun setLyricPerspectiveAngleDegrees(value: Int) = Unit
    override suspend fun setLyricTapToSeekEnabled(enabled: Boolean) = Unit
    override suspend fun setAutoScanMode(mode: AutoScanMode) = Unit
    override suspend fun setScanSubdirectories(enabled: Boolean) = Unit
    override suspend fun setWebDavMetadataScanMode(mode: MetadataScanMode) = Unit
    override suspend fun setMinimumAudioDurationMs(value: Long) = Unit
    override suspend fun setMissingFilePolicy(policy: MissingFilePolicy) = Unit
    override suspend fun setAllowMeteredNetworkUsage(enabled: Boolean) = Unit
    override suspend fun setNetworkRetryCount(value: Int) = Unit
    override suspend fun setConnectionTimeoutSeconds(value: Int) = Unit
    override suspend fun setAudioPreloadBytes(bytes: Long) = Unit
    override suspend fun setListenAndCacheEnabled(enabled: Boolean) = Unit
    override suspend fun setAudioCacheLimitBytes(bytes: Long) = Unit
    override suspend fun setImageCacheLimitBytes(bytes: Long) = Unit
    override suspend fun resetToDefaults() = Unit
}
