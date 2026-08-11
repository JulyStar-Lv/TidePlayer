package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.service.playback.domain.AdvancedPlaybackController
import org.koin.compose.koinInject
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import io.github.vinceglb.filekit.path
import org.koin.compose.viewmodel.koinViewModel
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsRoot(
    page: SettingsPage,
    appVersion: String,
    appBuildInfo: String,
    gitCommitSha: String,
    onNavigateToAppearance: () -> Unit,
    onNavigateToPlayback: () -> Unit,
    onNavigateToLyrics: () -> Unit,
    onNavigateToSource: () -> Unit,
    onNavigateToPlugins: () -> Unit,
    onNavigateToNetworkCache: () -> Unit,
    onNavigateToStorage: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    onNavigateToSourcePathPicker: () -> Unit,
    onBack: () -> Unit,
    settingsVM: SettingsVM = koinViewModel(),
) {
    val advancedPlaybackController = koinInject<AdvancedPlaybackController>()
    val audioOutputState by advancedPlaybackController.outputState.collectAsState()
    val state by settingsVM.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    val directoryPicker = rememberDirectoryPickerLauncher { directory ->
        val path = directory?.path ?: return@rememberDirectoryPickerLauncher
        val localPath = normalizePickedDirectoryPath(path)
        settingsVM.onAction(
            if (localPath == null) {
                SettingsAction.ReportUnsupportedLocalDirectory
            } else {
                SettingsAction.AddLocalDirectory(localPath)
            },
        )
    }

    LaunchedEffect(settingsVM) {
        settingsVM.eventFlow.collect { event ->
            when (event) {
                SettingsEvent.OpenLibraryFolderPicker -> directoryPicker.launch()
                SettingsEvent.OpenSourcePathPicker -> onNavigateToSourcePathPicker()
            }
        }
    }
    LaunchedEffect(page, advancedPlaybackController) {
        if (page == SettingsPage.Playback) {
            advancedPlaybackController.refreshOutputDevices()
        }
    }

    val settingsMenu: @Composable (SettingsPage?) -> Unit = { selectedPage ->
        SettingsScreen(
            state = state,
            appVersion = appVersion,
            selectedPage = selectedPage,
            onNavigateToAppearance = onNavigateToAppearance,
            onNavigateToPlayback = onNavigateToPlayback,
            onNavigateToLyrics = onNavigateToLyrics,
            onNavigateToSource = onNavigateToSource,
            onNavigateToPlugins = onNavigateToPlugins,
            onNavigateToNetworkCache = onNavigateToNetworkCache,
            onNavigateToStorage = onNavigateToStorage,
            onNavigateToDiagnostics = onNavigateToDiagnostics,
            onNavigateToAbout = onNavigateToAbout,
        )
    }
    val detailContent: @Composable (SettingsPage, (() -> Unit)?) -> Unit = { detailPage, back ->
        when (detailPage) {
            SettingsPage.Home -> settingsMenu(null)
            SettingsPage.Appearance -> AppearanceSettingsSection(
                state = state,
                onBack = back,
                onAction = settingsVM::onAction,
            )
            SettingsPage.Playback -> PlaybackSettingsSection(
                state = state,
                audioOutputState = audioOutputState,
                onSelectAudioOutput = advancedPlaybackController::selectOutputDevice,
                onRefreshAudioOutputs = advancedPlaybackController::refreshOutputDevices,
                onBack = back,
                onAction = settingsVM::onAction,
            )
            SettingsPage.Lyrics -> LyricsSettingsScreen(
                state = state,
                onBack = back,
                onAction = settingsVM::onAction,
            )
            SettingsPage.Source -> SourceSettingsSection(
                state = state,
                onBack = back,
                onAction = settingsVM::onAction,
            )
            SettingsPage.NetworkCache -> NetworkCacheSettingsSection(
                state = state,
                onBack = back,
                onAction = settingsVM::onAction,
            )
            SettingsPage.Storage -> StorageSettingsSection(
                state = state,
                onBack = back,
                onAction = settingsVM::onAction,
            )
            SettingsPage.Diagnostics -> DiagnosticsScreen(onBack = back)
            SettingsPage.About -> AboutSettingsSection(
                appVersion = appVersion,
                appBuildInfo = appBuildInfo,
                gitCommitSha = gitCommitSha,
                onBack = back,
                onOpenLicenses = onNavigateToLicenses,
                onOpenRepository = { uriHandler.openUri(APP_REPOSITORY_URL) },
                onOpenIssues = { uriHandler.openUri(APP_ISSUES_URL) },
            )
            SettingsPage.Licenses -> LicensesSettingsScreen(onBack = back)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth >= DesignTokens.adaptive.mediumMaxWidth) {
            val selectedPage = if (page == SettingsPage.Home) SettingsPage.Appearance else page
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .width(360.dp)
                        .fillMaxHeight(),
                ) {
                    settingsMenu(selectedPage)
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.35f)),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    detailContent(selectedPage, null)
                }
            }
        } else {
            detailContent(page, onBack)
        }
    }
}
