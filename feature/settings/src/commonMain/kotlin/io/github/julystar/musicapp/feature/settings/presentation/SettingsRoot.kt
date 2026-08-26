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
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.SourceEditorType
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
    pluginSettingsContent: @Composable (onBack: (() -> Unit)?) -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToPlayback: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    onNavigateToAudioEffects: () -> Unit,
    onNavigateToLyrics: () -> Unit,
    onNavigateToSource: () -> Unit,
    onNavigateToPlugins: () -> Unit,
    onNavigateToNetworkCache: () -> Unit,
    onNavigateToStorage: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    onNavigateToSourcePathPicker: () -> Unit,
    onNavigateToSourceEditor: (SourceAccountId?, SourceEditorType?) -> Unit,
    onBack: () -> Unit,
    settingsVM: SettingsVM = koinViewModel(),
) {
    val advancedPlaybackController = koinInject<AdvancedPlaybackController>()
    val audioOutputState by advancedPlaybackController.outputState.collectAsStateWithLifecycle()
    val state by settingsVM.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val directoryPicker = rememberDirectoryPickerLauncher { directory ->
        settingsVM.onAction(
            SettingsAction.HandleLocalDirectoryPickerResult(
                localDirectoryPickerResult(directory?.path, ::normalizePickedDirectoryPath),
            ),
        )
    }

    LifecycleStartEffect(page, settingsVM) {
        settingsVM.setAudioDspRuntimeMonitoringEnabled(page == SettingsPage.AudioEffects)
        onStopOrDispose {
            settingsVM.setAudioDspRuntimeMonitoringEnabled(false)
        }
    }

    LaunchedEffect(settingsVM) {
        settingsVM.eventFlow.collect { event ->
            when (event) {
                SettingsEvent.OpenLibraryFolderPicker -> {
                    try {
                        directoryPicker.launch()
                    } catch (error: Exception) {
                        settingsVM.onAction(
                            SettingsAction.HandleLocalDirectoryPickerResult(
                                LocalDirectoryPickerResult.LaunchFailed(error),
                            )
                        )
                    }
                }
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
                onNavigateToEqualizer = onNavigateToEqualizer,
                onNavigateToAudioEffects = onNavigateToAudioEffects,
                onAction = settingsVM::onAction,
            )
            SettingsPage.Equalizer -> EqualizerSettingsScreen(
                state = state,
                onBack = back,
                onAction = settingsVM::onAction,
            )
            SettingsPage.AudioEffects -> AudioEffectsSettingsScreen(
                state = state,
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
                onNavigateToSourceEditor = onNavigateToSourceEditor,
                onAction = settingsVM::onAction,
            )
            SettingsPage.Plugins -> pluginSettingsContent(back)
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
