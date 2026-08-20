package io.github.julystar.musicapp.feature.settings.presentation

import io.github.julystar.musicapp.core.domain.model.AppLanguageMode
import io.github.julystar.musicapp.core.domain.model.AppSettings
import io.github.julystar.musicapp.core.domain.model.AppThemeMode
import io.github.julystar.musicapp.core.domain.model.AudioFocusMode
import io.github.julystar.musicapp.core.domain.model.AutoScanMode
import io.github.julystar.musicapp.core.domain.model.DiagnosticsExportResult
import io.github.julystar.musicapp.core.domain.model.DiagnosticsReport
import io.github.julystar.musicapp.core.domain.model.LibraryRebuildState
import io.github.julystar.musicapp.core.domain.model.LocalMusicDirectory
import io.github.julystar.musicapp.core.domain.model.LyricTextAlignment
import io.github.julystar.musicapp.core.domain.model.MetadataRefreshTarget
import io.github.julystar.musicapp.core.domain.model.MetadataScanMode
import io.github.julystar.musicapp.core.domain.model.MissingFilePolicy
import io.github.julystar.musicapp.core.domain.model.OneDriveDriveListResult
import io.github.julystar.musicapp.core.domain.model.SettingsCapabilities
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.SourceConnectionTestStatus
import io.github.julystar.musicapp.core.domain.model.SourceEditorDraft
import io.github.julystar.musicapp.core.domain.model.SourceEditorStorageState
import io.github.julystar.musicapp.core.domain.model.SourceEditorType
import io.github.julystar.musicapp.core.domain.model.StorageAccountInfo
import io.github.julystar.musicapp.core.domain.model.StorageUsage
import io.github.julystar.musicapp.core.domain.model.StoredCredential
import io.github.julystar.musicapp.core.domain.model.storageSourceAccountId
import io.github.julystar.musicapp.core.domain.repository.DiagnosticsService
import io.github.julystar.musicapp.core.domain.repository.AppDataClearService
import io.github.julystar.musicapp.core.domain.repository.LibraryMaintenanceService
import io.github.julystar.musicapp.core.domain.repository.PermissionChecker
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.core.domain.repository.AudioDspAnalysisRepository
import io.github.julystar.musicapp.core.domain.repository.AudioDspFrequencyResponse
import io.github.julystar.musicapp.core.domain.repository.SourceSettingsRepository
import io.github.julystar.musicapp.core.domain.repository.StorageRepository
import io.github.julystar.musicapp.core.domain.repository.StorageUsageRepository
import io.github.julystar.musicapp.core.domain.repository.ToastRepository
import io.github.julystar.musicapp.core.domain.repository.UiMessage
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncController
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncFailure
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncRequest
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncResult
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncStatus
import io.github.julystar.musicapp.service.librarysync.domain.LibrarySyncTask
import io.github.julystar.musicapp.service.librarysync.domain.MetadataRefreshController
import io.github.julystar.musicapp.service.librarysync.domain.MetadataRefreshRequest
import io.github.julystar.musicapp.service.librarysync.domain.MetadataRefreshResult
import io.github.julystar.musicapp.service.playback.domain.PlayableItem
import io.github.julystar.musicapp.service.playback.domain.PlaybackController
import io.github.julystar.musicapp.service.playback.domain.PlaybackPosition
import io.github.julystar.musicapp.service.playback.domain.PlaybackQueue
import io.github.julystar.musicapp.service.playback.domain.PlayerState
import io.github.julystar.musicapp.service.playback.domain.RepeatMode
import io.github.julystar.musicapp.source.api.BuiltInSourceIds
import io.github.julystar.musicapp.source.api.ImportRepository
import io.github.julystar.musicapp.source.api.SourceDirectorySelection
import io.github.julystar.musicapp.source.api.SourceNodeSelection
import io.github.julystar.musicapp.source.api.SourceNodeType
import io.github.julystar.musicapp.core.domain.model.ImportSelectionMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsVMTest {

    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads settings modifies values reports failures and gates unsupported capabilities`() = runTest {
        val repository = FakeSettingsRepository(
            AppSettings.Default.copy(
                themeMode = AppThemeMode.Light,
                artworkThemeEnabled = false,
                scanSubdirectories = false,
            )
        )
        val environment = TestEnvironment(settingsRepository = repository)
        withStartedViewModel(environment) { viewModel ->
            assertEquals(AppThemeMode.Light, viewModel.state.value.settings.themeMode)

            viewModel.onAction(SettingsAction.SetThemeMode(AppThemeMode.Dark))
            advanceUntilIdle()
            assertEquals(AppThemeMode.Dark, repository.values.value.themeMode)

            viewModel.onAction(SettingsAction.SetArtworkThemeEnabled(true))
            viewModel.onAction(SettingsAction.SetManualThemeSeedArgb(0xFF3D9AFFL))
            viewModel.onAction(
                SettingsAction.SetCustomThemeSeedArgbValues(listOf(0xFF3D9AFFL, 0xFFFFD93DL)),
            )
            viewModel.onAction(SettingsAction.SetGaplessPlaybackEnabled(true))
            advanceUntilIdle()
            assertTrue(repository.values.value.artworkThemeEnabled)
            assertEquals(0xFF3D9AFFL, repository.values.value.manualThemeSeedArgb)
            assertEquals(
                listOf(0xFF3D9AFFL, 0xFFFFD93DL),
                repository.values.value.customThemeSeedArgbValues,
            )
            assertFalse(repository.values.value.gaplessPlaybackEnabled)

            repository.failThemeUpdates = true
            viewModel.onAction(SettingsAction.SetThemeMode(AppThemeMode.System))
            advanceUntilIdle()
            assertEquals(AppThemeMode.Dark, repository.values.value.themeMode)
            assertTrue(
                (environment.toast.emittedMessages.last() as UiMessage.Text)
                    .value.contains("write failed")
            )

            viewModel.onAction(SettingsAction.SetAutoScanMode(AutoScanMode.OnStartup))
            viewModel.onAction(SettingsAction.SetLyricTextAlignment(LyricTextAlignment.Center))
            viewModel.onAction(SettingsAction.SetLyricPrimaryFontScalePercent(125))
            advanceUntilIdle()
            assertEquals(AutoScanMode.OnStartup, repository.values.value.autoScanMode)
            assertEquals(LyricTextAlignment.Center, repository.values.value.lyrics.textAlignment)
            assertEquals(125, repository.values.value.lyrics.primaryFontScalePercent)
            assertFalse(repository.values.value.scanSubdirectories)
        }
    }

    @Test
    fun `dangerous actions require confirmation before clearing cache or rebuilding library`() = runTest {
        val environment = TestEnvironment()
        withStartedViewModel(environment) { viewModel ->
            viewModel.onAction(SettingsAction.RequestClearAllCaches)
            advanceUntilIdle()
            assertIs<SettingsConfirmation.ClearAllCaches>(viewModel.state.value.pendingConfirmation)
            assertEquals(0, environment.storageUsage.clearAllCalls)

            viewModel.onAction(SettingsAction.ConfirmPendingAction)
            advanceUntilIdle()
            assertNull(viewModel.state.value.pendingConfirmation)
            assertEquals(1, environment.storageUsage.clearAllCalls)

            viewModel.onAction(SettingsAction.RequestRebuildLibrary)
            advanceUntilIdle()
            assertIs<SettingsConfirmation.RebuildLibrary>(viewModel.state.value.pendingConfirmation)
            assertEquals(0, environment.maintenance.rebuildCalls)

            viewModel.onAction(SettingsAction.ConfirmPendingAction)
            advanceUntilIdle()
            assertEquals(1, environment.maintenance.rebuildCalls)

        }
    }

    @Test
    fun `clearing all app data requires confirmation and invokes the wipe service`() = runTest {
        val environment = TestEnvironment()
        withStartedViewModel(environment) { viewModel ->
            viewModel.onAction(SettingsAction.RequestClearAllData)
            advanceUntilIdle()

            assertIs<SettingsConfirmation.ClearAllData>(viewModel.state.value.pendingConfirmation)
            assertEquals(0, environment.appDataClear.clearCalls)

            viewModel.onAction(SettingsAction.ConfirmPendingAction)
            advanceUntilIdle()

            assertNull(viewModel.state.value.pendingConfirmation)
            assertEquals(1, environment.appDataClear.clearCalls)
        }
    }

    @Test
    fun `removing a local directory stops active playback`() = runTest {
        val environment = TestEnvironment()
        withStartedViewModel(environment) { viewModel ->
            viewModel.onAction(SettingsAction.RequestRemoveLocalDirectory("42", "Music"))
            advanceUntilIdle()

            assertEquals(0, environment.playback.clearQueueCalls)

            viewModel.onAction(SettingsAction.ConfirmPendingAction)
            advanceUntilIdle()

            assertEquals(listOf("42"), environment.sourceSettings.removedDirectoryIds)
            assertEquals(1, environment.playback.clearQueueCalls)
        }
    }

    @Test
    fun `cache changes use the repository limit and enforce the same value`() = runTest {
        val environment = TestEnvironment()
        withStartedViewModel(environment) { viewModel ->
            val requestedLimit = 2_147_483_648L
            assertTrue(environment.settingsRepository.values.value.listenAndCacheEnabled)

            viewModel.onAction(SettingsAction.SetListenAndCacheEnabled(false))
            viewModel.onAction(SettingsAction.SetAudioCacheLimitBytes(requestedLimit))
            advanceUntilIdle()

            assertFalse(environment.settingsRepository.values.value.listenAndCacheEnabled)
            assertEquals(requestedLimit, environment.settingsRepository.values.value.audioCacheLimitBytes)
            assertEquals(requestedLimit, environment.storageUsage.lastEnforcedAudioLimit)
            assertEquals(
                environment.settingsRepository.values.value.imageCacheLimitBytes,
                environment.storageUsage.lastEnforcedImageLimit,
            )
        }
    }

    @Test
    fun `shows failures for the selected scan`() = runTest {
        val failure = LibrarySyncFailure("metadata", "unreadable file", 7L)
        val sync = FakeLibrarySyncController().apply {
            failuresByTask["scan-1"] = MutableStateFlow(listOf(failure))
        }
        val environment = TestEnvironment(librarySyncController = sync)
        withStartedViewModel(environment) { viewModel ->
            viewModel.onAction(SettingsAction.OpenScanFailures("scan-1"))
            advanceUntilIdle()

            assertEquals("scan-1", viewModel.state.value.failureDialogTaskId)
            assertEquals(listOf(failure), viewModel.state.value.failureDetails)
        }
    }

    @Test
    fun `cancel active scans cancels the full library scan batch`() = runTest {
        val accountId = storageSourceAccountId(42L)
        val sync = FakeLibrarySyncController().apply {
            recentTasks.value = listOf(
                LibrarySyncTask(
                    id = "scan-1",
                    accountId = accountId,
                    selectedFolderId = 1L,
                    selectedFolderRemoteId = null,
                    folderPath = "/Music",
                    folderDisplayPath = "/Music",
                    status = LibrarySyncStatus.Running,
                    scannedCount = 12L,
                    importedCount = 4L,
                    skippedCount = 2L,
                    failedCount = 0L,
                    checkpoint = null,
                    errorMessage = null,
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 2L,
                ),
            )
        }
        val storage = FakeStorageRepository().apply {
            accounts.value = listOf(
                sourceAccount(
                    id = 42L,
                    sourceId = BuiltInSourceIds.WebDav,
                    title = "Home DAV",
                    count = 12L,
                ),
            )
        }
        val environment = TestEnvironment(
            storageRepository = storage,
            librarySyncController = sync,
        )

        withStartedViewModel(environment) { viewModel ->
            assertTrue(viewModel.state.value.scanTasks.single().isActive)

            viewModel.onAction(SettingsAction.CancelActiveScans)
            advanceUntilIdle()

            assertEquals(1, sync.cancelAllCalls)
        }
    }

    @Test
    fun `editing WebDAV loads account data but never restores its password`() = runTest {
        val accountId = storageSourceAccountId(42L)
        val storage = FakeStorageRepository().apply {
            accounts.value = listOf(
                StorageAccountInfo(
                    accountId = accountId,
                    sourceId = BuiltInSourceIds.WebDav,
                    isLocal = false,
                    isOneDrive = false,
                    title = "Home DAV",
                    subtitle = "https://dav.example.test",
                    musicCount = 12,
                    rootPath = "/Music",
                )
            )
            editorState = SourceEditorStorageState(
                accountId = accountId,
                draft = SourceEditorDraft(
                    id = 42L,
                    address = "https://dav.example.test",
                    alias = "Home DAV",
                    username = "stored-user",
                    secret = "must-not-reach-ui",
                ),
                title = "Home DAV",
                musicCount = 12u,
                isOneDrive = false,
            )
            credential = StoredCredential("stored-user", "must-not-reach-ui", false)
        }
        val environment = TestEnvironment(storageRepository = storage)
        withStartedViewModel(environment) { viewModel ->
            viewModel.onAction(SettingsAction.OpenEditWebDavDialog(accountId))
            advanceUntilIdle()

            val dialog = viewModel.state.value.webDavDialog ?: error("WebDAV editor was not opened")
            assertEquals("stored-user", dialog.username)
            assertFalse(dialog.toString().contains("must-not-reach-ui"))
            assertFalse(dialog.toString().contains("password", ignoreCase = true))

            viewModel.onAction(SettingsAction.SaveWebDavAccount(""))
            advanceUntilIdle()

            assertEquals("must-not-reach-ui", storage.upsertedDraft?.secret)
            assertFalse(viewModel.state.value.toString().contains("must-not-reach-ui"))

            viewModel.onAction(SettingsAction.OpenEditWebDavDialog(accountId))
            advanceUntilIdle()
            viewModel.onAction(SettingsAction.SaveWebDavAccount("replacement-secret"))
            advanceUntilIdle()

            assertEquals("replacement-secret", storage.upsertedDraft?.secret)
            assertFalse(viewModel.state.value.toString().contains("replacement-secret"))
        }
    }

    @Test
    fun `saving SMB source persists its connection configuration without exposing password in state`() = runTest {
        val storage = FakeStorageRepository()
        val environment = TestEnvironment(storageRepository = storage)

        withStartedViewModel(environment) { viewModel ->
            viewModel.onAction(SettingsAction.OpenAddSmbDialog)
            viewModel.onAction(
                SettingsAction.SaveSmbAccount(
                    password = "top-secret",
                    draft = SmbAccountDialogState(
                        name = "Home NAS",
                        host = "nas.example.test",
                        port = "1445",
                        username = "music",
                        requireSigning = true,
                    ),
                ),
            )
            advanceUntilIdle()

            val draft = storage.upsertedDraft ?: error("SMB draft was not saved")
            assertEquals(SourceEditorType.Smb, draft.storageType)
            assertEquals("nas.example.test", draft.smbHost)
            assertEquals(1445, draft.smbPort)
            assertEquals("", draft.smbShare)
            assertEquals("", draft.smbRootPath)
            assertTrue(draft.smbRequireSigning)
            assertEquals("top-secret", draft.secret)
            assertNull(viewModel.state.value.smbDialog)
            assertFalse(viewModel.state.value.toString().contains("top-secret"))
        }
    }

    @Test
    fun `local source is hidden until a music directory is selected`() = runTest {
        val localAccountId = storageSourceAccountId(1L)
        val storage = FakeStorageRepository().apply {
            accounts.value = listOf(
                sourceAccount(1L, BuiltInSourceIds.Local, "Local", 4),
            )
        }
        val environment = TestEnvironment(storageRepository = storage)

        withStartedViewModel(environment) { viewModel ->
            assertTrue(viewModel.state.value.sourceAccounts.isEmpty())

            environment.sourceSettings.localDirectories.value = listOf(
                LocalMusicDirectory(
                    id = "local-root",
                    accountId = localAccountId,
                    displayName = "Music",
                    path = "/Music",
                    lastScannedAtEpochMs = null,
                ),
            )
            advanceUntilIdle()

            assertEquals(
                listOf("Local"),
                viewModel.state.value.sourceAccounts.map(SourceAccountSettingsItem::title),
            )
        }
    }

    @Test
    fun `local directory picker selection starts a local scan`() = runTest {
        val sync = FakeLibrarySyncController()
        val environment = TestEnvironment(librarySyncController = sync)

        withStartedViewModel(environment) { viewModel ->
            viewModel.onAction(SettingsAction.RequestAddLocalDirectory)
            assertIs<SettingsEvent.OpenLibraryFolderPicker>(viewModel.eventFlow.first())

            viewModel.onAction(SettingsAction.AddLocalDirectory("/Music/Albums"))
            advanceUntilIdle()

            val request = sync.requests.single()
            assertEquals(storageSourceAccountId(1L), request.accountId)
            assertEquals("/Music/Albums", request.selectedFolderCanonicalPath)
            assertEquals("/Music/Albums", request.selectedFolderDisplayPath)
        }
    }

    @Test
    fun `remote source path picker targets the source and saves the selected path`() = runTest {
        val accountId = storageSourceAccountId(42L)
        val storage = FakeStorageRepository().apply {
            accounts.value = listOf(
                sourceAccount(42L, BuiltInSourceIds.WebDav, "Home DAV", 12),
            )
        }
        val environment = TestEnvironment(storageRepository = storage)

        withStartedViewModel(environment) { viewModel ->
            viewModel.onAction(SettingsAction.ConfigureSourcePath(accountId))

            assertIs<SettingsEvent.OpenSourcePathPicker>(viewModel.eventFlow.first())
            assertEquals(accountId, environment.importRepository.currentDirectoryAccountId.value)

            environment.importRepository.onFinishCurrentDirectory(
                SourceDirectorySelection(
                    sourceId = BuiltInSourceIds.WebDav,
                    accountId = accountId,
                    path = "/Music/Lossless",
                    remoteId = "folder-42",
                )
            )
            advanceUntilIdle()

            assertEquals(accountId to "/Music/Lossless", storage.lastRootPathUpdate)
            assertTrue(environment.librarySyncController.requests.isEmpty())

            viewModel.onAction(SettingsAction.ScanSourceAccount(accountId))
            advanceUntilIdle()

            assertEquals(
                "/Music/Lossless",
                environment.librarySyncController.requests.single().selectedFolderCanonicalPath,
            )
        }
    }

    @Test
    fun `openlist picker preserves raw roots and scan dispatches each configured root`() = runTest {
        val accountId = storageSourceAccountId(43L)
        val storage = FakeStorageRepository().apply {
            accounts.value = listOf(
                sourceAccount(43L, BuiltInSourceIds.OpenList, "OpenList", 0),
            )
        }
        val environment = TestEnvironment(storageRepository = storage)

        withStartedViewModel(environment) { viewModel ->
            assertEquals("OpenList", viewModel.state.value.sourceAccounts.single().sourceLabel)
            viewModel.onAction(SettingsAction.ConfigureSourcePath(accountId))
            assertIs<SettingsEvent.OpenSourcePathPicker>(viewModel.eventFlow.first())
            environment.importRepository.onFinishCurrentDirectory(
                SourceDirectorySelection(
                    sourceId = BuiltInSourceIds.OpenList,
                    accountId = accountId,
                    path = "/音乐/%25 #? 😀",
                    remoteId = "/音乐/%25 #? 😀",
                ),
            )
            advanceUntilIdle()
            viewModel.onAction(SettingsAction.ConfigureSourcePath(accountId))
            assertIs<SettingsEvent.OpenSourcePathPicker>(viewModel.eventFlow.first())
            environment.importRepository.onFinishCurrentDirectory(
                SourceDirectorySelection(
                    sourceId = BuiltInSourceIds.OpenList,
                    accountId = accountId,
                    path = "/第二根/한국어",
                    remoteId = "/第二根/한국어",
                ),
            )
            advanceUntilIdle()

            assertEquals(
                listOf(
                    accountId to "/音乐/%25 #? 😀",
                    accountId to "/第二根/한국어",
                ),
                storage.rootPathUpdates,
            )
            storage.rootPaths[accountId] = listOf("/音乐/%25 #? 😀", "/第二根/한국어")
            viewModel.onAction(SettingsAction.ScanSourceAccount(accountId))
            advanceUntilIdle()
            assertEquals(
                listOf("/音乐/%25 #? 😀", "/第二根/한국어"),
                environment.librarySyncController.requests.map { it.selectedFolderCanonicalPath },
            )
        }
    }

    @Test
    fun `openlist scan does not request without roots or when disabled`() = runTest {
        val accountId = storageSourceAccountId(44L)
        val storage = FakeStorageRepository().apply {
            accounts.value = listOf(sourceAccount(44L, BuiltInSourceIds.OpenList, "OpenList", 0))
        }
        val environment = TestEnvironment(storageRepository = storage)
        withStartedViewModel(environment) { viewModel ->
            viewModel.onAction(SettingsAction.ScanSourceAccount(accountId))
            advanceUntilIdle()
            assertTrue(environment.librarySyncController.requests.isEmpty())

            storage.accounts.value = listOf(
                sourceAccount(44L, BuiltInSourceIds.OpenList, "OpenList", 0, enabled = false),
            )
            storage.rootPaths[accountId] = listOf("/should-not-scan")
            viewModel.onAction(SettingsAction.ScanSourceAccount(accountId))
            advanceUntilIdle()
            assertTrue(environment.librarySyncController.requests.isEmpty())
        }
    }

    @Test
    fun `scan all sources dispatches every enabled openlist raw root`() = runTest {
        val accountId = storageSourceAccountId(45L)
        val roots = listOf("/音乐/%25 #? 😀\\folder", "/第二根/한국어 space")
        val storage = FakeStorageRepository().apply {
            accounts.value = listOf(sourceAccount(45L, BuiltInSourceIds.OpenList, "OpenList", 0))
            rootPaths[accountId] = roots
        }
        val environment = TestEnvironment(storageRepository = storage)

        withStartedViewModel(environment) { viewModel ->
            viewModel.onAction(SettingsAction.ScanAllSources)
            advanceUntilIdle()

            assertEquals(roots, environment.librarySyncController.requests.map { it.selectedFolderRemoteId })
            assertEquals(roots, environment.librarySyncController.requests.map { it.selectedFolderCanonicalPath })
            assertEquals(roots, environment.librarySyncController.requests.map { it.selectedFolderDisplayPath })
        }
    }

    @Test
    fun `local directory scan waits for storage permission`() = runTest {
        val sync = FakeLibrarySyncController()
        val permissionChecker = FakePermissionChecker(granted = false)
        val environment = TestEnvironment(
            librarySyncController = sync,
            permissionChecker = permissionChecker,
        )

        withStartedViewModel(environment) { viewModel ->
            viewModel.onAction(SettingsAction.AddLocalDirectory("/Music/Albums"))
            advanceUntilIdle()

            assertEquals(1, permissionChecker.requestCount)
            assertTrue(sync.requests.isEmpty())

            permissionChecker.grant()
            advanceUntilIdle()

            assertEquals("/Music/Albums", sync.requests.single().selectedFolderCanonicalPath)
        }
    }

    @Test
    fun `home state uses real supported source counts and excludes unfinished providers`() = runTest {
        val localAccountId = storageSourceAccountId(1L)
        val storage = FakeStorageRepository().apply {
            accounts.value = listOf(
                sourceAccount(1L, BuiltInSourceIds.Local, "Local", 4),
                sourceAccount(
                    id = 2L,
                    sourceId = BuiltInSourceIds.WebDav,
                    title = "DAV",
                    count = 6,
                    lastScanAtEpochMs = 1_725_000_000_000L,
                    lastScanStatus = "SYNCED",
                ),
                sourceAccount(3L, BuiltInSourceIds.OneDrive, "OneDrive", 99),
            )
        }
        val environment = TestEnvironment(storageRepository = storage).apply {
            sourceSettings.localDirectories.value = listOf(
                LocalMusicDirectory(
                    id = "local-root",
                    accountId = localAccountId,
                    displayName = "Music",
                    path = "/Music",
                    lastScannedAtEpochMs = null,
                ),
            )
        }
        withStartedViewModel(environment) { viewModel ->
            assertEquals(listOf("Local", "DAV"), viewModel.state.value.sourceAccounts.map { it.title })
            assertEquals(2, viewModel.state.value.enabledSourceCount)
            assertEquals(10, viewModel.state.value.trackCount)
            val webDav = viewModel.state.value.sourceAccounts.single { it.title == "DAV" }
            assertEquals(1_725_000_000_000L, webDav.lastScanAtEpochMs)
            assertEquals("SYNCED", webDav.lastScanStatus)
        }
    }

    @Test
    fun `WebDAV scan uses latest metadata mode while local scan remains full`() = runTest {
        val localAccountId = storageSourceAccountId(1L)
        val webDavAccountId = storageSourceAccountId(2L)
        val settings = FakeSettingsRepository(
            AppSettings.Default.copy(webDavMetadataScanMode = MetadataScanMode.Standard)
        )
        val storage = FakeStorageRepository().apply {
            accounts.value = listOf(
                sourceAccount(1L, BuiltInSourceIds.Local, "Local", 1),
                sourceAccount(2L, BuiltInSourceIds.WebDav, "DAV", 1),
            )
        }
        val sync = FakeLibrarySyncController()
        val environment = TestEnvironment(
            settingsRepository = settings,
            storageRepository = storage,
            librarySyncController = sync,
        ).apply {
            sourceSettings.localDirectories.value = listOf(
                LocalMusicDirectory(
                    id = "local-root",
                    accountId = localAccountId,
                    displayName = "Music",
                    path = "/Music",
                    lastScannedAtEpochMs = null,
                )
            )
        }

        withStartedViewModel(environment) { viewModel ->
            viewModel.onAction(SettingsAction.SetWebDavMetadataScanMode(MetadataScanMode.Fast))
            advanceUntilIdle()
            assertEquals(MetadataScanMode.Fast, settings.values.value.webDavMetadataScanMode)

            viewModel.onAction(SettingsAction.ScanSourceAccount(webDavAccountId))
            viewModel.onAction(SettingsAction.ScanLocalMusic)
            advanceUntilIdle()

            assertEquals(
                MetadataScanMode.Fast,
                sync.requests.single { it.accountId == webDavAccountId }.metadataScanMode,
            )
            assertEquals(
                MetadataScanMode.Full,
                sync.requests.single { it.accountId == localAccountId }.metadataScanMode,
            )
        }
    }

    @Test
    fun `SMB scan starts at the backend root without repeating its configured root path`() = runTest {
        val accountId = storageSourceAccountId(2L)
        val storage = FakeStorageRepository().apply {
            accounts.value = listOf(
                sourceAccount(2L, BuiltInSourceIds.Smb, "NAS", 0).copy(
                    rootPath = "/Music/Lossless",
                ),
            )
            editorState = SourceEditorStorageState(
                accountId = accountId,
                draft = SourceEditorDraft(
                    id = 2L,
                    alias = "NAS",
                    storageType = SourceEditorType.Smb,
                    smbHost = "nas.example.test",
                    smbShare = "Music",
                    smbRootPath = "Lossless",
                ),
                title = "NAS",
                musicCount = 0u,
                isOneDrive = false,
            )
        }
        val sync = FakeLibrarySyncController()
        val environment = TestEnvironment(
            storageRepository = storage,
            librarySyncController = sync,
        )

        withStartedViewModel(environment) { viewModel ->
            viewModel.onAction(SettingsAction.ScanSourceAccount(accountId))
            advanceUntilIdle()

            val request = sync.requests.single()
            assertEquals("/", request.selectedFolderCanonicalPath)
            assertEquals("/", request.selectedFolderDisplayPath)
        }
    }

    private suspend fun kotlinx.coroutines.test.TestScope.withStartedViewModel(
        environment: TestEnvironment,
        block: suspend (SettingsVM) -> Unit,
    ) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val viewModel = environment.createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect()
        }
        advanceUntilIdle()
        block(viewModel)
    }
}

private class TestEnvironment(
    val settingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
    val storageRepository: FakeStorageRepository = FakeStorageRepository(),
    val librarySyncController: FakeLibrarySyncController = FakeLibrarySyncController(),
    val permissionChecker: FakePermissionChecker = FakePermissionChecker(),
) {
    val sourceSettings = FakeSourceSettingsRepository()
    val storageUsage = FakeStorageUsageRepository()
    val maintenance = FakeLibraryMaintenanceService()
    val appDataClear = FakeAppDataClearService()
    val toast = FakeToastRepository()
    val playback = FakePlaybackController()
    val importRepository = FakeImportRepository()

    fun createViewModel() = SettingsVM(
        settingsRepository = settingsRepository,
        sourceSettingsRepository = sourceSettings,
        storageRepository = storageRepository,
        storageUsageRepository = storageUsage,
        diagnosticsService = FakeDiagnosticsService(),
        libraryMaintenanceService = maintenance,
        appDataClearService = appDataClear,
        toastRepository = toast,
        permissionChecker = permissionChecker,
        librarySyncController = librarySyncController,
        playbackController = playback,
        metadataRefreshController = FakeMetadataRefreshController(),
        audioDspAnalysisRepository = FakeAudioDspAnalysisRepository,
        importRepository = importRepository,
        capabilities = SettingsCapabilities(),
        textProvider = FakeSettingsTextProvider(),
    )
}

private object FakeAudioDspAnalysisRepository : AudioDspAnalysisRepository {
    override fun calculateFrequencyResponse(
        settings: io.github.julystar.musicapp.core.domain.model.AudioEffectSettings,
        sampleRate: UInt,
    ): AudioDspFrequencyResponse = AudioDspFrequencyResponse.Empty
}

private class FakeSettingsTextProvider : SettingsTextProvider {
    override suspend fun get(
        resource: org.jetbrains.compose.resources.StringResource,
        vararg formatArgs: Any,
    ): String = formatArgs.joinToString(" ")
}

private class FakeSettingsRepository(initial: AppSettings = AppSettings.Default) : SettingsRepository {
    val values = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = values
    var failThemeUpdates = false

    private fun update(block: (AppSettings) -> AppSettings) {
        values.value = block(values.value)
    }

    override suspend fun setThemeMode(mode: AppThemeMode) {
        if (failThemeUpdates) error("write failed")
        update { it.copy(themeMode = mode) }
    }

    override suspend fun setArtworkThemeEnabled(enabled: Boolean) =
        update { it.copy(artworkThemeEnabled = enabled) }
    override suspend fun setManualThemeSeedArgb(argb: Long) =
        update { it.copy(manualThemeSeedArgb = argb) }
    override suspend fun setCustomThemeSeedArgbValues(argbValues: List<Long>) =
        update { it.copy(customThemeSeedArgbValues = argbValues) }
    override suspend fun setLanguageMode(mode: AppLanguageMode) = update { it.copy(languageMode = mode) }
    override suspend fun setAudioFocusMode(mode: AudioFocusMode) = update { it.copy(audioFocusMode = mode) }
    override suspend fun setPauseOnDisconnect(enabled: Boolean) = update { it.copy(pauseOnDisconnect = enabled) }
    override suspend fun setGaplessPlaybackEnabled(enabled: Boolean) = update { it.copy(gaplessPlaybackEnabled = enabled) }
    override suspend fun setRetryPlaybackOnFailure(enabled: Boolean) = update { it.copy(retryPlaybackOnFailure = enabled) }
    override suspend fun setResumePlaybackAfterNetworkRecovery(enabled: Boolean) = update { it.copy(resumePlaybackAfterNetworkRecovery = enabled) }
    override suspend fun setKeepScreenOnInPlayer(enabled: Boolean) = update { it.copy(keepScreenOnInPlayer = enabled) }
    override suspend fun setLyricTextAlignment(alignment: LyricTextAlignment) =
        update { it.copy(lyrics = it.lyrics.copy(textAlignment = alignment)) }
    override suspend fun setLyricPrimaryFontScalePercent(value: Int) =
        update { it.copy(lyrics = it.lyrics.copy(primaryFontScalePercent = value)) }
    override suspend fun setLyricPrimaryFontSizeSp(value: Int) =
        update { it.copy(lyrics = it.lyrics.copy(primaryFontSizeSp = value)) }
    override suspend fun setLyricSecondaryFontScalePercent(value: Int) =
        update { it.copy(lyrics = it.lyrics.copy(secondaryFontScalePercent = value)) }
    override suspend fun setLyricSecondaryFontSizeSp(value: Int) =
        update { it.copy(lyrics = it.lyrics.copy(secondaryFontSizeSp = value)) }
    override suspend fun setLyricTranslationVisible(visible: Boolean) =
        update { it.copy(lyrics = it.lyrics.copy(showTranslation = visible)) }
    override suspend fun setLyricWordLiftEnabled(enabled: Boolean) =
        update { it.copy(lyrics = it.lyrics.copy(wordLiftEnabled = enabled)) }
    override suspend fun setLyricBlurEffectEnabled(enabled: Boolean) =
        update { it.copy(lyrics = it.lyrics.copy(blurEffectEnabled = enabled)) }
    override suspend fun setLyricPerspectiveEffectEnabled(enabled: Boolean) =
        update { it.copy(lyrics = it.lyrics.copy(perspectiveEffectEnabled = enabled)) }
    override suspend fun setLyricPerspectiveAngleDegrees(value: Int) =
        update { it.copy(lyrics = it.lyrics.copy(perspectiveAngleDegrees = value)) }
    override suspend fun setLyricTapToSeekEnabled(enabled: Boolean) =
        update { it.copy(lyrics = it.lyrics.copy(tapToSeekEnabled = enabled)) }
    override suspend fun setAutoScanMode(mode: AutoScanMode) = update { it.copy(autoScanMode = mode) }
    override suspend fun setScanSubdirectories(enabled: Boolean) = update { it.copy(scanSubdirectories = enabled) }
    override suspend fun setWebDavMetadataScanMode(mode: MetadataScanMode) = update { it.copy(webDavMetadataScanMode = mode) }
    override suspend fun setMinimumAudioDurationMs(value: Long) = update { it.copy(minimumAudioDurationMs = value) }
    override suspend fun setMissingFilePolicy(policy: MissingFilePolicy) = update { it.copy(missingFilePolicy = policy) }
    override suspend fun setAllowMeteredNetworkUsage(enabled: Boolean) =
        update { it.copy(allowMeteredNetworkUsage = enabled) }
    override suspend fun setNetworkRetryCount(value: Int) = update { it.copy(networkRetryCount = value) }
    override suspend fun setConnectionTimeoutSeconds(value: Int) = update { it.copy(connectionTimeoutSeconds = value) }
    override suspend fun setAudioPreloadBytes(bytes: Long) = update { it.copy(audioPreloadBytes = bytes) }
    override suspend fun setListenAndCacheEnabled(enabled: Boolean) =
        update { it.copy(listenAndCacheEnabled = enabled) }
    override suspend fun setAudioCacheLimitBytes(bytes: Long) = update { it.copy(audioCacheLimitBytes = bytes) }
    override suspend fun setImageCacheLimitBytes(bytes: Long) = update { it.copy(imageCacheLimitBytes = bytes) }
    override suspend fun resetToDefaults() { values.value = AppSettings.Default }
}

private class FakeSourceSettingsRepository : SourceSettingsRepository {
    override val localDirectories = MutableStateFlow<List<LocalMusicDirectory>>(emptyList())
    val removedDirectoryIds = mutableListOf<String>()
    override suspend fun setAccountEnabled(accountId: SourceAccountId, enabled: Boolean) = Unit
    override suspend fun removeLocalDirectory(id: String) {
        removedDirectoryIds += id
    }
}

private class FakePlaybackController : PlaybackController {
    override val state = MutableStateFlow(PlayerState())
    override val position = MutableStateFlow(PlaybackPosition.Zero)
    override val queue = MutableStateFlow(PlaybackQueue.Empty)
    var clearQueueCalls = 0

    override suspend fun play(items: List<PlayableItem>, startIndex: Int) = Unit
    override fun play() = Unit
    override fun pause() = Unit
    override fun togglePlayPause() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun skipNext() = Unit
    override fun skipPrevious() = Unit
    override fun enqueueNext(item: PlayableItem) = Unit
    override fun setShuffle(enabled: Boolean) = Unit
    override fun setRepeatMode(mode: RepeatMode) = Unit
    override fun moveQueueItem(from: Int, to: Int) = Unit
    override fun removeQueueItem(index: Int) = Unit

    override fun clearQueue() {
        clearQueueCalls += 1
        state.value = PlayerState()
        queue.value = PlaybackQueue.Empty
    }
}

private class FakeStorageRepository : StorageRepository {
    val accounts = MutableStateFlow<List<StorageAccountInfo>>(emptyList())
    override val storageAccounts: StateFlow<List<StorageAccountInfo>> = accounts
    override val onRemoveStorageEvent: SharedFlow<Unit> = MutableSharedFlow()
    override val oauthRefreshToken: StateFlow<String> = MutableStateFlow("")
    var editorState: SourceEditorStorageState? = null
    var credential: StoredCredential? = null
    var upsertedDraft: SourceEditorDraft? = null
    var lastRootPathUpdate: Pair<SourceAccountId, String>? = null
    val rootPathUpdates = mutableListOf<Pair<SourceAccountId, String>>()
    val rootPaths = mutableMapOf<SourceAccountId, List<String>>()

    override suspend fun reload() = Unit
    override suspend fun startOneDriveOAuth(): String = ""
    override suspend fun upsertSource(draft: SourceEditorDraft): SourceAccountId {
        upsertedDraft = draft
        return storageSourceAccountId(draft.id ?: 1L)
    }
    override suspend fun loadEditorState(id: Long): SourceEditorStorageState? = editorState
    override suspend fun testSource(draft: SourceEditorDraft) = SourceConnectionTestStatus.Success
    override suspend fun listOneDriveDriveInfos(refreshToken: String) = OneDriveDriveListResult(emptyList(), refreshToken)
    override suspend fun updateOneDriveRefreshTokenByAccountId(accountId: SourceAccountId, refreshToken: String) = Unit
    override fun findStorageAccountByAccountId(accountId: SourceAccountId) = accounts.value.firstOrNull { it.accountId == accountId }
    override suspend fun loadCredentialByAccountId(accountId: SourceAccountId): StoredCredential? = credential
    override suspend fun setAccountRootPath(accountId: SourceAccountId, rootPath: String) {
        lastRootPathUpdate = accountId to rootPath
        rootPathUpdates += accountId to rootPath
        accounts.value = accounts.value.map { account ->
            if (account.accountId == accountId) account.copy(rootPath = rootPath) else account
        }
    }
    override suspend fun listAccountRootPaths(accountId: SourceAccountId): List<String> = rootPaths[accountId].orEmpty()
    override suspend fun removeByAccountId(accountId: SourceAccountId) = Unit
}

private class FakeImportRepository : ImportRepository {
    override val allowTypes = MutableStateFlow<List<SourceNodeType>>(emptyList())
    override val selectionMode = MutableStateFlow(ImportSelectionMode.Entries)
    override val currentDirectoryAccountId = MutableStateFlow<SourceAccountId?>(null)
    private var directoryCallback: ((SourceDirectorySelection) -> Unit)? = null

    override fun prepare(
        types: List<SourceNodeType>,
        block: (List<SourceNodeSelection>) -> Unit,
    ) {
        allowTypes.value = types
        selectionMode.value = ImportSelectionMode.Entries
        currentDirectoryAccountId.value = null
    }

    override fun prepareCurrentDirectory(
        accountId: SourceAccountId?,
        block: (SourceDirectorySelection) -> Unit,
    ) {
        allowTypes.value = emptyList()
        selectionMode.value = ImportSelectionMode.CurrentDirectory
        currentDirectoryAccountId.value = accountId
        directoryCallback = block
    }

    override fun onFinish(entries: List<SourceNodeSelection>) = Unit

    override fun onFinishCurrentDirectory(selection: SourceDirectorySelection) {
        directoryCallback?.invoke(selection)
        directoryCallback = null
    }
}

private class FakeStorageUsageRepository : StorageUsageRepository {
    var clearAllCalls = 0
    var lastEnforcedAudioLimit: Long? = null
    var lastEnforcedImageLimit: Long? = null

    override suspend fun loadUsage() = StorageUsage(totalBytes = 0)
    override suspend fun clearAudioCache() = Unit
    override suspend fun clearImageCache() = Unit
    override suspend fun clearAllCaches() { clearAllCalls += 1 }
    override suspend fun clearAllStoredFiles() = Unit
    override suspend fun enforceCacheLimits(audioLimitBytes: Long, imageLimitBytes: Long) {
        lastEnforcedAudioLimit = audioLimitBytes
        lastEnforcedImageLimit = imageLimitBytes
    }
}

private class FakeLibraryMaintenanceService : LibraryMaintenanceService {
    override val rebuildState = MutableStateFlow(LibraryRebuildState())
    var rebuildCalls = 0
    override suspend fun rebuildLibrary() { rebuildCalls += 1 }
}

private class FakeAppDataClearService : AppDataClearService {
    var clearCalls = 0
    override suspend fun clearAllData() { clearCalls += 1 }
}

private class FakeToastRepository : ToastRepository {
    val emittedMessages = mutableListOf<UiMessage>()
    override val messages: SharedFlow<UiMessage> = MutableSharedFlow()
    override fun emit(message: UiMessage) { emittedMessages += message }
}

private class FakePermissionChecker(
    granted: Boolean = true,
) : PermissionChecker {
    private val permission = MutableStateFlow(granted)
    override val havePermission: StateFlow<Boolean> = permission
    var requestCount = 0

    override fun requestStoragePermission() {
        requestCount += 1
    }

    fun grant() {
        permission.value = true
    }
}

private class FakeDiagnosticsService : DiagnosticsService {
    override suspend fun collectDiagnostics(): DiagnosticsReport = error("not used")
    override suspend fun exportDiagnostics(): DiagnosticsExportResult = DiagnosticsExportResult.Success("diagnostics.txt")
}

private class FakeLibrarySyncController : LibrarySyncController {
    override val recentTasks = MutableStateFlow<List<LibrarySyncTask>>(emptyList())
    val failuresByTask = mutableMapOf<String, MutableStateFlow<List<LibrarySyncFailure>>>()
    val requests = mutableListOf<LibrarySyncRequest>()
    var cancelAllCalls = 0
    override fun observeFailures(taskId: String): Flow<List<LibrarySyncFailure>> =
        failuresByTask.getOrPut(taskId) { MutableStateFlow(emptyList()) }
    override suspend fun syncFolder(request: LibrarySyncRequest): LibrarySyncResult {
        requests += request
        return LibrarySyncResult(
            scanId = "scan-${requests.size}",
            selectedFolderId = requests.size.toLong(),
            scannedCount = 0,
            changedCount = 0,
            skippedCount = 0,
            importedCount = 0,
            failedCount = 0,
        )
    }
    override suspend fun pause(scanId: String) = false
    override suspend fun cancel(scanId: String) = false
    override suspend fun cancelAll() {
        cancelAllCalls += 1
    }
    override suspend fun recoverInterruptedTasks() = 0
    override suspend fun resume(scanId: String): LibrarySyncResult? = null
    override suspend fun retry(scanId: String): LibrarySyncResult? = null
}

private class FakeMetadataRefreshController : MetadataRefreshController {
    override suspend fun refresh(request: MetadataRefreshRequest) = MetadataRefreshResult(
        requestedCount = 0,
        refreshedCount = 0,
        failedCount = 0,
        metadataRequestCount = 0,
        metadataFetchedBytes = 0,
        metadataElapsedMs = 0,
        artworkCachedBytes = 0,
    )
}

private fun sourceAccount(
    id: Long,
    sourceId: io.github.julystar.musicapp.core.domain.model.SourceId,
    title: String,
    count: Long,
    lastScanAtEpochMs: Long? = null,
    lastScanStatus: String? = null,
    enabled: Boolean = true,
) = StorageAccountInfo(
    accountId = storageSourceAccountId(id),
    sourceId = sourceId,
    isLocal = sourceId == BuiltInSourceIds.Local,
    isOneDrive = sourceId == BuiltInSourceIds.OneDrive,
    title = title,
    subtitle = title,
    musicCount = count,
    enabled = enabled,
    lastScanAtEpochMs = lastScanAtEpochMs,
    lastScanStatus = lastScanStatus,
)
