package io.github.julystar.musicapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import io.github.julystar.musicapp.core.data.StorageRepositoryImpl
import io.github.julystar.musicapp.core.domain.recovery.StartupMode
import io.github.julystar.musicapp.core.domain.recovery.StartupPlan
import io.github.julystar.musicapp.core.domain.recovery.allowsNormalApplicationInitialization
import io.github.julystar.musicapp.core.domain.repository.ArtworkRepository
import io.github.julystar.musicapp.core.presentation.platform.dispatchPlatformBack
import io.github.julystar.musicapp.di.AppInitializer
import io.github.julystar.musicapp.di.initKoin
import io.github.julystar.musicapp.diagnostics.DiagnosticsBootstrap
import io.github.julystar.musicapp.diagnostics.DiagnosticsBootstrapState
import io.github.julystar.musicapp.diagnostics.RustDiagnosticsRepository
import io.github.julystar.musicapp.diagnostics.recordKotlinUncaughtException
import io.github.julystar.musicapp.service.download.data.scheduler.IosUrlSessionDownloadScheduler
import io.github.julystar.musicapp.service.download.domain.DownloadTaskScheduler
import io.github.julystar.musicapp.service.playback.data.PlayerController as LegacyPlayerController
import io.github.julystar.musicapp.service.playback.data.PlayerRepository
import io.github.julystar.musicapp.service.playback.data.toPlaybackArtwork
import io.github.julystar.musicapp.service.playback.domain.PlayableItem
import io.github.julystar.musicapp.service.playback.domain.PlaybackController
import io.github.julystar.musicapp.service.playback.domain.PlaybackStatus
import io.github.julystar.musicapp.service.playback.domain.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.koin.core.Koin
import org.koin.core.context.stopKoin
import platform.UIKit.UIViewController
import kotlin.native.getUnhandledExceptionHook
import kotlin.native.setUnhandledExceptionHook

private var applicationInitialized = false
private var applicationKoin: Koin? = null
private var diagnosticsInitialized = false
private lateinit var initialDiagnosticsState: DiagnosticsBootstrapState
private var initialRecoveryIncidentIds: List<String> = emptyList()
private val iosMediaBridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
private var resumeAfterAudioInterruption = false

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
private fun initializeDiagnostics() {
    if (diagnosticsInitialized) return
    DiagnosticsBootstrap.initialize()
    val previous = getUnhandledExceptionHook()
    setUnhandledExceptionHook { throwable ->
        recordKotlinUncaughtException("kotlin-native", throwable)
        previous?.invoke(throwable)
    }
    initialDiagnosticsState = DiagnosticsBootstrap.finishPlatformExitCollection()
    initialRecoveryIncidentIds = initialDiagnosticsState.beginAutomaticDegradedRecovery()
    diagnosticsInitialized = true
}

private fun initializeApplication(disabledComponents: Set<String> = emptySet()) {
    runBlocking { initializeApplicationAsync(disabledComponents) }
}

private suspend fun initializeApplicationAsync(disabledComponents: Set<String> = emptySet()) {
    if (applicationInitialized) return

    val koin = initKoin().koin
    try {
        AppInitializer.initializeBridgeAsync(koin, disabledComponents)
        AppInitializer.reloadRepositories(koin, disabledComponents)
        applicationKoin = koin
        applicationInitialized = true
    } catch (error: Throwable) {
        stopKoin()
        throw error
    }
}

@Suppress("FunctionName")
fun MainViewController(): UIViewController {
    initializeDiagnostics()
    if (initialDiagnosticsState.startupPlan.allowsNormalApplicationInitialization()) {
        initializeApplication(initialDiagnosticsState.startupPlan.disabledComponents)
    }
    return ComposeUIViewController {
        var diagnosticsState by remember {
            mutableStateOf<DiagnosticsBootstrapState>(initialDiagnosticsState)
        }
        var recoveryIncidentIds by remember {
            mutableStateOf(initialRecoveryIncidentIds)
        }
        val applicationScope = rememberCoroutineScope()
        Root(
            diagnosticsState = diagnosticsState,
            onTryNormalStartup = { disabledComponents ->
                val incidentIds = diagnosticsState.recoveryIncidentIds()
                applicationScope.launch {
                    runCatching {
                        RustDiagnosticsRepository.beginRecovery(disabledComponents)
                        incidentIds.forEach { incidentId ->
                            RustDiagnosticsRepository.markRecoveryAttempted(
                                incidentId,
                                disabledComponents,
                            )
                        }
                        recoveryIncidentIds = incidentIds
                        withContext(Dispatchers.Default) {
                            initializeApplicationAsync(disabledComponents)
                        }
                        diagnosticsState = diagnosticsState.copy(
                            snapshot = RustDiagnosticsRepository.snapshot(),
                            startupPlan = StartupPlan(StartupMode.NormalStartup),
                        )
                    }
                }
            },
            onStartupStable = {
                if (recoveryIncidentIds.isNotEmpty()) {
                    RustDiagnosticsRepository.completeRecovery(recoveryIncidentIds)
                    io.github.julystar.musicapp.diagnostics.SafeModeRecoveryStore.clear()
                    recoveryIncidentIds = emptyList()
                    initialRecoveryIncidentIds = emptyList()
                }
            },
        )
    }
}

fun handleOneDriveOAuthRedirect(code: String, state: String) {
    initializeDiagnostics()
    if (initialDiagnosticsState.safeMode && !applicationInitialized) return
    initializeApplication(initialDiagnosticsState.startupPlan.disabledComponents)
    applicationKoin
        ?.get<StorageRepositoryImpl>()
        ?.receiveOneDriveOAuthRedirect(code, state)
}

fun handleEventsForBackgroundURLSession(
    identifier: String,
    completionHandler: () -> Unit,
) {
    initializeDiagnostics()
    if (initialDiagnosticsState.safeMode && !applicationInitialized) {
        completionHandler()
        return
    }
    initializeApplication(initialDiagnosticsState.startupPlan.disabledComponents)
    val scheduler = applicationKoin
        ?.get<DownloadTaskScheduler>() as? IosUrlSessionDownloadScheduler
    scheduler?.setBackgroundCompletionHandler(identifier, completionHandler)
        ?: completionHandler()
}

data class IosNowPlayingSnapshot(
    val mediaId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val positionMs: Long,
    val isPlaying: Boolean,
    val queueIndex: Int,
    val queueCount: Int,
)

fun currentNowPlayingSnapshot(): IosNowPlayingSnapshot? {
    val controller = playbackControllerOrNull() ?: return null
    val item = controller.state.value.currentItem ?: return null
    val legacyController = applicationKoin?.get<LegacyPlayerController>()
    val position = controller.position.value
    val queue = controller.queue.value
    return IosNowPlayingSnapshot(
        mediaId = item.nowPlayingMediaId(),
        title = item.title,
        artist = item.artist,
        album = item.album,
        durationMs = (legacyController?.getDuration() ?: position.durationMs).coerceAtLeast(0L),
        positionMs = (legacyController?.getCurrentPosition() ?: position.positionMs).coerceAtLeast(0L),
        isPlaying = controller.state.value.status == PlaybackStatus.Playing,
        queueIndex = queue.currentIndex,
        queueCount = queue.items.size,
    )
}

fun loadNowPlayingArtworkBase64(
    mediaId: String,
    completion: (String?) -> Unit,
) {
    val controller = playbackControllerOrNull()
    val item = controller?.state?.value?.currentItem
    if (item == null || item.nowPlayingMediaId() != mediaId) {
        completion(null)
        return
    }
    val koin = applicationKoin
    if (koin == null) {
        completion(null)
        return
    }
    val music = koin.get<PlayerRepository>().music.value
    if (music == null || music.meta.id.value != item.libraryTrackId) {
        completion(null)
        return
    }
    val artworkRepository = koin.get<ArtworkRepository>()
    iosMediaBridgeScope.launch {
        val encoded = runCatching {
            artworkRepository.load(music.toPlaybackArtwork())?.encodeBase64()
        }.getOrNull()
        val currentId = playbackControllerOrNull()
            ?.state
            ?.value
            ?.currentItem
            ?.nowPlayingMediaId()
        completion(encoded.takeIf { currentId == mediaId })
    }
}

fun handlePlaybackPlayCommand(): Boolean = withPlaybackController { controller ->
    controller.play()
}

fun handlePlaybackPauseCommand(): Boolean = withPlaybackController { controller ->
    controller.pause()
}

// Keep STOP resumable across lock-screen, headset, Bluetooth, and CarPlay controls.
// It pauses without clearing the current track, queue, or position.
fun handlePlaybackStopCommand(): Boolean = withPlaybackController { controller ->
    controller.pause()
}

fun handlePlaybackToggleCommand(): Boolean = withPlaybackController { controller ->
    controller.togglePlayPause()
}

fun handlePlaybackNextCommand(): Boolean = withPlaybackController { controller ->
    controller.skipNext()
}

fun handlePlaybackPreviousCommand(): Boolean = withPlaybackController { controller ->
    controller.skipPrevious()
}

fun handlePlaybackSeekByCommand(deltaMs: Long): Boolean = withPlaybackController { controller ->
    val legacyController = applicationKoin?.get<LegacyPlayerController>()
    val position = legacyController?.getCurrentPosition() ?: controller.position.value.positionMs
    val duration = legacyController?.getDuration() ?: controller.position.value.durationMs
    val maximum = duration.takeIf { it > 0L } ?: Long.MAX_VALUE
    controller.seekTo((position + deltaMs).coerceIn(0L, maximum))
}

fun handlePlaybackSeekToCommand(positionMs: Long): Boolean = withPlaybackController { controller ->
    val duration = applicationKoin?.get<LegacyPlayerController>()?.getDuration()
        ?: controller.position.value.durationMs
    val maximum = duration.takeIf { it > 0L } ?: Long.MAX_VALUE
    controller.seekTo(positionMs.coerceIn(0L, maximum))
}

fun handlePlaybackRestartCommand(): Boolean = withPlaybackController { controller ->
    controller.seekTo(0L)
}

fun handlePlaybackToggleShuffleCommand(): Boolean = withPlaybackController { controller ->
    controller.setShuffle(!controller.state.value.shuffleEnabled)
}

fun handlePlaybackCycleRepeatCommand(): Boolean = withPlaybackController { controller ->
    val nextMode = when (controller.state.value.repeatMode) {
        RepeatMode.Off -> RepeatMode.All
        RepeatMode.All -> RepeatMode.One
        RepeatMode.One -> RepeatMode.Off
    }
    controller.setRepeatMode(nextMode)
}

fun handleAudioSessionInterruptionBegan(): Boolean {
    val controller = playbackControllerOrNull() ?: return false
    resumeAfterAudioInterruption = controller.state.value.status == PlaybackStatus.Playing
    if (resumeAfterAudioInterruption) controller.pause()
    return true
}

fun handleAudioSessionInterruptionEnded(shouldResume: Boolean): Boolean {
    val controller = playbackControllerOrNull() ?: return false
    val resume = resumeAfterAudioInterruption && shouldResume
    resumeAfterAudioInterruption = false
    if (resume) controller.play()
    return true
}

fun handleAudioRouteDisconnected(): Boolean = withPlaybackController { controller ->
    if (controller.state.value.status == PlaybackStatus.Playing) {
        controller.pause()
    }
}

fun handleAudioRouteChanged(): Boolean {
    val controller = applicationKoin?.getOrNull<
        io.github.julystar.musicapp.service.playback.domain.AdvancedPlaybackController
        >() ?: return false
    controller.refreshOutputDevices()
    return true
}

fun handlePlaybackBackCommand(): Boolean = dispatchPlatformBack()

private inline fun withPlaybackController(action: (PlaybackController) -> Unit): Boolean {
    val controller = playbackControllerOrNull() ?: return false
    action(controller)
    return true
}

private fun playbackControllerOrNull(): PlaybackController? {
    initializeDiagnostics()
    if (!applicationInitialized) {
        if (initialDiagnosticsState.safeMode) return null
        initializeApplication(initialDiagnosticsState.startupPlan.disabledComponents)
    }
    return applicationKoin?.get<PlaybackController>()
}

private fun PlayableItem.nowPlayingMediaId(): String {
    return libraryTrackId?.let { "library:$it" }
        ?: mediaId?.toString()
        ?: title
}

private fun ByteArray.encodeBase64(): String {
    if (isEmpty()) return ""
    val result = StringBuilder(((size + 2) / 3) * 4)
    var index = 0
    while (index < size) {
        val first = this[index++].toInt() and 0xff
        val second = if (index < size) this[index++].toInt() and 0xff else -1
        val third = if (index < size) this[index++].toInt() and 0xff else -1

        result.append(BASE64_ALPHABET[first ushr 2])
        result.append(
            BASE64_ALPHABET[
                ((first and 0x03) shl 4) or if (second >= 0) second ushr 4 else 0
            ]
        )
        result.append(
            if (second >= 0) {
                BASE64_ALPHABET[
                    ((second and 0x0f) shl 2) or if (third >= 0) third ushr 6 else 0
                ]
            } else {
                '='
            }
        )
        result.append(if (third >= 0) BASE64_ALPHABET[third and 0x3f] else '=')
    }
    return result.toString()
}

fun shutdownApplication() {
    resumeAfterAudioInterruption = false
    if (applicationInitialized) {
        stopKoin()
        applicationKoin = null
        applicationInitialized = false
    }
    if (diagnosticsInitialized) {
        runCatching { RustDiagnosticsRepository.shutdown() }
        diagnosticsInitialized = false
        initialRecoveryIncidentIds = emptyList()
    }
}

private const val BASE64_ALPHABET =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
