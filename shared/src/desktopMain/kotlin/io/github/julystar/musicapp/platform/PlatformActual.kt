package io.github.julystar.musicapp.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import io.github.julystar.musicapp.core.domain.model.AppLanguageMode
import io.github.julystar.musicapp.migration.AppIdentifiers
import io.github.julystar.musicapp.migration.DesktopDataMigration
import java.util.Locale

private val resolvedAppDataDirectory by lazy {
    DesktopDataMigration.ensureMigrated().toAbsolutePath().normalize().toString()
}

actual fun getAppDataDirectory(): String = resolvedAppDataDirectory

actual fun getAppCacheDir(): String {
    return "$resolvedAppDataDirectory/cache"
}

actual fun getAppDatabasePath(): String? {
    return "$resolvedAppDataDirectory/${AppIdentifiers.DATABASE_FILE}"
}

actual fun getPlatformName(): String = "desktop"

actual fun getProcessName(): String = AppIdentifiers.BRAND_NAME

actual fun platformSettingsCapabilities() =
    io.github.julystar.musicapp.core.domain.model.SettingsCapabilities(
        customMusicDirectorySupported = true,
        secureCredentialStoreSupported = desktopSecureCredentialStoreAvailable(),
        desktopMediaKeysSupported = true,
        floatingLyricsSupported = true,
        gaplessPlaybackSupported = true,
        crossfadeSupported = true,
        replayGainSupported = true,
        audioEffectsSupported = true,
        audioDsp =
            io.github.julystar.musicapp.core.domain.model.AudioDspCapabilities.SharedCore,
        audioPipeline = io.github.julystar.musicapp.core.domain.model.AudioPipelineCapabilities(
            dspInputSampleFormats = setOf(
                io.github.julystar.musicapp.core.domain.model.AudioSampleFormat.Float32,
            ),
            dspOutputSampleFormats = setOf(
                io.github.julystar.musicapp.core.domain.model.AudioSampleFormat.Float32,
            ),
            highResolutionDspOutput = true,
        ),
        diagnosticsExportSupported = true,
        diagnosticsCenterSupported = true,
        safeModeSupported = true,
        platformExitInfoSupported = false,
        historicalAnrTraceSupported = false,
        incidentRecoverySupported = true,
        fileShareSupported = true,
        settingsBackupSupported = true,
        scheduledBackupSupported = true,
        desktopShortcutsSupported = true,
    )

private val systemLocaleAtStartup: Locale = Locale.getDefault()

actual fun applyAppLanguageMode(mode: AppLanguageMode) {
    Locale.setDefault(
        when (mode) {
            AppLanguageMode.System -> systemLocaleAtStartup
            AppLanguageMode.Chinese -> Locale.forLanguageTag("zh-Hans")
            AppLanguageMode.English -> Locale.ENGLISH
        }
    )
}

private fun desktopSecureCredentialStoreAvailable(): Boolean {
    val osName = System.getProperty("os.name").orEmpty()
    return when {
        osName.startsWith("Mac", ignoreCase = true) -> true
        osName.startsWith("Windows", ignoreCase = true) -> true
        osName.startsWith("Linux", ignoreCase = true) -> runCatching {
            ProcessBuilder("which", "secret-tool").start().waitFor() == 0
        }.getOrDefault(false)
        else -> false
    }
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun byteArrayToImageBitmap(bytes: ByteArray): ImageBitmap? {
    return try {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (e: Exception) {
        null
    }
}
