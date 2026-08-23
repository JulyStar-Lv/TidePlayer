package io.github.julystar.musicapp.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import platform.Foundation.*
import io.github.julystar.musicapp.core.domain.model.AppLanguageMode
import io.github.julystar.musicapp.migration.AppIdentifiers

private fun platformDirectory(directory: ULong): String {
    return NSSearchPathForDirectoriesInDomains(
        directory = directory,
        domainMask = NSUserDomainMask,
        expandTilde = true,
    ).firstOrNull() as? String ?: error("iOS directory $directory is unavailable")
}

actual fun getAppDataDirectory(): String = platformDirectory(NSDocumentDirectory)

actual fun getAppCacheDir(): String = platformDirectory(NSCachesDirectory)

actual fun getAppDatabasePath(): String? =
    "${getAppDataDirectory()}/${AppIdentifiers.DATABASE_FILE}"

actual fun getPlatformName(): String = "ios"

actual fun getProcessName(): String =
    NSBundle.mainBundle.bundleIdentifier ?: AppIdentifiers.BRAND_NAME

actual fun platformSettingsCapabilities() =
    io.github.julystar.musicapp.core.domain.model.SettingsCapabilities(
        customMusicDirectorySupported = true,
        secureCredentialStoreSupported = true,
        replayGainSupported = true,
        audioEffectsSupported = true,
        audioDsp =
            io.github.julystar.musicapp.core.domain.model.AudioDspCapabilities.SharedCore.copy(
                resourceDependent = true,
            ),
        audioPipeline = io.github.julystar.musicapp.core.domain.model.AudioPipelineCapabilities(
            dspInputSampleFormats = setOf(
                io.github.julystar.musicapp.core.domain.model.AudioSampleFormat.Pcm16,
                io.github.julystar.musicapp.core.domain.model.AudioSampleFormat.Float32,
            ),
            dspOutputSampleFormats = setOf(
                io.github.julystar.musicapp.core.domain.model.AudioSampleFormat.Pcm16,
                io.github.julystar.musicapp.core.domain.model.AudioSampleFormat.Float32,
            ),
            highResolutionDspOutput = true,
        ),
        audioPreloadSupported = true,
        diagnosticsExportSupported = true,
        diagnosticsCenterSupported = true,
        safeModeSupported = true,
        platformExitInfoSupported = false,
        historicalAnrTraceSupported = false,
        incidentRecoverySupported = true,
        fileShareSupported = true,
        audioRoutePickerSupported = true,
        settingsBackupSupported = true,
        scheduledBackupSupported = true,
    )

actual fun applyAppLanguageMode(mode: AppLanguageMode) {
    val defaults = NSUserDefaults.standardUserDefaults
    when (mode) {
        AppLanguageMode.System -> defaults.removeObjectForKey("AppleLanguages")
        AppLanguageMode.Chinese -> defaults.setObject(listOf("zh-Hans"), "AppleLanguages")
        AppLanguageMode.English -> defaults.setObject(listOf("en"), "AppleLanguages")
    }
}

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1_000.0).toLong()

actual fun byteArrayToImageBitmap(bytes: ByteArray): ImageBitmap? {
    return try {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (_: Exception) {
        null
    }
}
