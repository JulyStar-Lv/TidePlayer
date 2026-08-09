package io.github.julystar.musicapp

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.awt.SwingWindow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.julystar.musicapp.core.presentation.platform.LocalDesktopTitleBarInset
import io.github.julystar.musicapp.di.AppInitializer
import io.github.julystar.musicapp.di.initKoin
import io.github.julystar.musicapp.diagnostics.DiagnosticsBootstrap
import io.github.julystar.musicapp.diagnostics.DiagnosticsBootstrapState
import io.github.julystar.musicapp.diagnostics.RustDiagnosticsRepository
import io.github.julystar.musicapp.diagnostics.recordKotlinUncaughtException
import io.github.julystar.musicapp.core.domain.recovery.StartupMode
import io.github.julystar.musicapp.core.domain.recovery.StartupPlan
import io.github.julystar.musicapp.core.domain.recovery.allowsNormalApplicationInitialization
import io.github.julystar.musicapp.core.domain.model.AppSettings
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.service.playback.domain.PlaybackController
import io.github.julystar.musicapp.service.playback.domain.RepeatMode
import io.github.vinceglb.filekit.FileKit
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.res.painterResource
import java.awt.Dimension
import java.awt.GraphicsConfiguration
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.KeyStroke
import kotlin.math.roundToInt
import kotlin.system.exitProcess
import org.koin.core.Koin
import org.koin.core.KoinApplication

private const val MinWindowWidth = 960
private const val MinWindowHeight = 520
private const val MaxWindowWidth = 1200
private const val MaxWindowHeight = 800
private const val WindowWidthRatio = 0.70
private const val WindowHeightRatio = 0.72
private const val SeekStepMs = 10_000L
private const val LargeSeekStepMs = 30_000L
private val IntegratedTitleBarInset = 28.dp
private val IsMacOs = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)

private const val PlayPauseAction = "musicapp.playPause"
private const val PreviousAction = "musicapp.previous"
private const val NextAction = "musicapp.next"
private const val SeekBackwardAction = "musicapp.seekBackward"
private const val SeekForwardAction = "musicapp.seekForward"
private const val SeekBackwardLargeAction = "musicapp.seekBackwardLarge"
private const val SeekForwardLargeAction = "musicapp.seekForwardLarge"
private const val RestartTrackAction = "musicapp.restartTrack"
private const val ToggleShuffleAction = "musicapp.toggleShuffle"
private const val CycleRepeatAction = "musicapp.cycleRepeat"

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    FileKit.init(appId = "io.github.julystar.musicapp")
    DiagnosticsBootstrap.initialize()
    installDesktopFatalHandler()
    val initialDiagnosticsState = DiagnosticsBootstrap.finishPlatformExitCollection()
    val runtime = DesktopApplicationRuntime()
    val initialRecoveryIncidentIds =
        initialDiagnosticsState.beginAutomaticDegradedRecovery()
    if (initialDiagnosticsState.startupPlan.allowsNormalApplicationInitialization()) {
        runtime.initialize(initialDiagnosticsState.startupPlan.disabledComponents)
    }

    application {
        var diagnosticsState by remember {
            mutableStateOf<DiagnosticsBootstrapState>(initialDiagnosticsState)
        }
        var recoveryIncidentIds by remember {
            mutableStateOf(initialRecoveryIncidentIds)
        }

        val initialWindowSize = remember { calculateInitialWindowSize() }
        val windowState = rememberWindowState(size = initialWindowSize)

        SwingWindow(
            onCloseRequest = {
                runtime.close()
                runCatching { RustDiagnosticsRepository.shutdown() }
                exitApplication()
            },
            title = "TidePlayer",
            state = windowState,
            icon = painterResource("icon.png"),
            init = ::configureWindowChrome,
        ) {
            DisposableEffect(window) {
                val availableSize = calculateAvailableScreenSize(window.graphicsConfiguration)
                window.minimumSize = Dimension(
                    minOf(MinWindowWidth, availableSize.width),
                    minOf(MinWindowHeight, availableSize.height),
                )
                onDispose {}
            }
            CompositionLocalProvider(
                LocalDesktopTitleBarInset provides
                    if (IsMacOs) IntegratedTitleBarInset else 0.dp,
            ) {
                if (diagnosticsState.safeMode) {
                    Root(
                        diagnosticsState = diagnosticsState,
                        onTryNormalStartup = { disabledComponents ->
                            val incidentIds = diagnosticsState.recoveryIncidentIds()
                            runCatching {
                                RustDiagnosticsRepository.beginRecovery(disabledComponents)
                                incidentIds.forEach { incidentId ->
                                    RustDiagnosticsRepository.markRecoveryAttempted(
                                        incidentId,
                                        disabledComponents,
                                    )
                                }
                                recoveryIncidentIds = incidentIds
                                runtime.initialize(disabledComponents)
                                diagnosticsState = diagnosticsState.copy(
                                    snapshot = RustDiagnosticsRepository.snapshot(),
                                    startupPlan = StartupPlan(StartupMode.NormalStartup),
                                )
                            }
                        },
                    )
                } else {
                    val koin = runtime.koin
                    val playbackController = remember(koin) { koin.get<PlaybackController>() }
                    val settingsRepository = remember(koin) { koin.get<SettingsRepository>() }
                    val appSettings by settingsRepository.settings.collectAsState(AppSettings.Default)
                    DisposableEffect(window, appSettings.playerInteraction.desktopShortcutsEnabled) {
                        if (appSettings.playerInteraction.desktopShortcutsEnabled) {
                            installPlaybackShortcuts(window.rootPane, playbackController)
                        }
                        onDispose { removePlaybackShortcuts(window.rootPane) }
                    }
                    Root(
                        diagnosticsState = diagnosticsState,
                        onStartupStable = {
                            if (recoveryIncidentIds.isNotEmpty()) {
                                RustDiagnosticsRepository.completeRecovery(recoveryIncidentIds)
                                io.github.julystar.musicapp.diagnostics.SafeModeRecoveryStore.clear()
                                recoveryIncidentIds = emptyList()
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun configureWindowChrome(window: ComposeWindow) {
    if (!IsMacOs) return
    window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
    window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
    window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
}

private class DesktopApplicationRuntime {
    private var koinApp: KoinApplication? = null
    val koin: Koin
        get() = checkNotNull(koinApp).koin

    fun initialize(disabledComponents: Set<String>) {
        if (koinApp != null) return
        val application = initKoin()
        try {
            AppInitializer.initializeBridge(application.koin, disabledComponents)
            runBlocking {
                AppInitializer.reloadRepositories(application.koin, disabledComponents)
            }
            koinApp = application
        } catch (error: Throwable) {
            application.close()
            throw error
        }
    }

    fun close() {
        koinApp?.close()
        koinApp = null
    }
}

private fun installDesktopFatalHandler() {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        recordKotlinUncaughtException(thread.name, throwable)
        if (previous != null) {
            previous.uncaughtException(thread, throwable)
        } else {
            exitProcess(1)
        }
    }
}

private val playbackShortcutBindings = listOf(
    KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_DOWN_MASK) to PlayPauseAction,
    KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK) to PreviousAction,
    KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK) to NextAction,
    KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.CTRL_DOWN_MASK) to SeekBackwardAction,
    KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.CTRL_DOWN_MASK) to SeekForwardAction,
    KeyStroke.getKeyStroke(
        KeyEvent.VK_LEFT,
        InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK,
    ) to SeekBackwardLargeAction,
    KeyStroke.getKeyStroke(
        KeyEvent.VK_RIGHT,
        InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK,
    ) to SeekForwardLargeAction,
    KeyStroke.getKeyStroke(KeyEvent.VK_HOME, InputEvent.CTRL_DOWN_MASK) to RestartTrackAction,
    KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK) to ToggleShuffleAction,
    KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK) to CycleRepeatAction,
)

private fun installPlaybackShortcuts(
    rootPane: JComponent,
    playbackController: PlaybackController,
) {
    val inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
    val actionMap = rootPane.actionMap
    playbackShortcutBindings.forEach { (keyStroke, actionKey) -> inputMap.put(keyStroke, actionKey) }
    actionMap.put(PlayPauseAction, object : AbstractAction() {
        override fun actionPerformed(event: ActionEvent?) = playbackController.togglePlayPause()
    })
    actionMap.put(PreviousAction, object : AbstractAction() {
        override fun actionPerformed(event: ActionEvent?) = playbackController.skipPrevious()
    })
    actionMap.put(NextAction, object : AbstractAction() {
        override fun actionPerformed(event: ActionEvent?) = playbackController.skipNext()
    })
    actionMap.put(SeekBackwardAction, object : AbstractAction() {
        override fun actionPerformed(event: ActionEvent?) = playbackController.seekBy(-SeekStepMs)
    })
    actionMap.put(SeekForwardAction, object : AbstractAction() {
        override fun actionPerformed(event: ActionEvent?) = playbackController.seekBy(SeekStepMs)
    })
    actionMap.put(SeekBackwardLargeAction, object : AbstractAction() {
        override fun actionPerformed(event: ActionEvent?) = playbackController.seekBy(-LargeSeekStepMs)
    })
    actionMap.put(SeekForwardLargeAction, object : AbstractAction() {
        override fun actionPerformed(event: ActionEvent?) = playbackController.seekBy(LargeSeekStepMs)
    })
    actionMap.put(RestartTrackAction, object : AbstractAction() {
        override fun actionPerformed(event: ActionEvent?) = playbackController.restartCurrentTrack()
    })
    actionMap.put(ToggleShuffleAction, object : AbstractAction() {
        override fun actionPerformed(event: ActionEvent?) {
            playbackController.setShuffle(!playbackController.state.value.shuffleEnabled)
        }
    })
    actionMap.put(CycleRepeatAction, object : AbstractAction() {
        override fun actionPerformed(event: ActionEvent?) = playbackController.cycleRepeatMode()
    })
}

private fun removePlaybackShortcuts(rootPane: JComponent) {
    val inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
    val actionMap = rootPane.actionMap
    playbackShortcutBindings.forEach { (keyStroke, actionKey) ->
        inputMap.remove(keyStroke)
        actionMap.remove(actionKey)
    }
}

private fun PlaybackController.seekBy(deltaMs: Long) {
    if (state.value.currentItem == null) return
    val playbackPosition = position.value
    if (playbackPosition.durationMs <= 0L) return
    seekTo(
        (playbackPosition.positionMs + deltaMs)
            .coerceIn(0L, playbackPosition.durationMs),
    )
}

private fun PlaybackController.restartCurrentTrack() {
    if (state.value.currentItem != null) {
        seekTo(0L)
    }
}

private fun PlaybackController.cycleRepeatMode() {
    val nextMode = when (state.value.repeatMode) {
        RepeatMode.Off -> RepeatMode.All
        RepeatMode.All -> RepeatMode.One
        RepeatMode.One -> RepeatMode.Off
    }
    setRepeatMode(nextMode)
}

private fun calculateInitialWindowSize(): DpSize {
    val configuration = GraphicsEnvironment
        .getLocalGraphicsEnvironment()
        .defaultScreenDevice
        .defaultConfiguration
    val availableSize = calculateAvailableScreenSize(configuration)

    return DpSize(
        calculateWindowDimension(
            available = availableSize.width,
            ratio = WindowWidthRatio,
            minimum = MinWindowWidth,
            maximum = MaxWindowWidth,
        ).dp,
        calculateWindowDimension(
            available = availableSize.height,
            ratio = WindowHeightRatio,
            minimum = MinWindowHeight,
            maximum = MaxWindowHeight,
        ).dp,
    )
}

private fun calculateAvailableScreenSize(configuration: GraphicsConfiguration): Dimension {
    val bounds = configuration.bounds
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
    return Dimension(
        (bounds.width - insets.left - insets.right).coerceAtLeast(1),
        (bounds.height - insets.top - insets.bottom).coerceAtLeast(1),
    )
}

private fun calculateWindowDimension(
    available: Int,
    ratio: Double,
    minimum: Int,
    maximum: Int,
): Int {
    return (available * ratio)
        .roundToInt()
        .coerceIn(minOf(minimum, available), minOf(maximum, available))
}
