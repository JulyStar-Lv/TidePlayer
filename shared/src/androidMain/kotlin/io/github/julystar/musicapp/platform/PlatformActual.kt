package io.github.julystar.musicapp.platform

import android.graphics.BitmapFactory
import android.app.LocaleManager
import android.app.Application
import android.os.Build
import android.os.LocaleList
import io.github.julystar.musicapp.core.domain.model.AppLanguageMode
import io.github.julystar.musicapp.migration.AppIdentifiers
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun getAppDataDirectory(): String {
    return appContext.filesDir.absolutePath
}

actual fun getAppCacheDir(): String {
    return appContext.cacheDir.absolutePath
}

actual fun getAppDatabasePath(): String? {
    return appContext.getDatabasePath(AppIdentifiers.DATABASE_FILE).absolutePath
}

actual fun getPlatformName(): String = "android"

actual fun getProcessName(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Application.getProcessName() else appContext.packageName

actual fun platformSettingsCapabilities() =
    io.github.julystar.musicapp.core.domain.model.SettingsCapabilities(
        customMusicDirectorySupported = true,
        secureCredentialStoreSupported = true,
        audioFocusSupported = true,
        deviceDisconnectSupported = true,
        replayGainSupported = true,
        audioEffectsSupported = true,
        audioDsp =
            io.github.julystar.musicapp.core.domain.model.AudioDspCapabilities.SharedCore,
        audioPipeline = io.github.julystar.musicapp.core.domain.model.AudioPipelineCapabilities(
            dspInputSampleFormats = setOf(
                io.github.julystar.musicapp.core.domain.model.AudioSampleFormat.Pcm16,
                io.github.julystar.musicapp.core.domain.model.AudioSampleFormat.Float32,
            ),
            dspOutputSampleFormats = setOf(
                io.github.julystar.musicapp.core.domain.model.AudioSampleFormat.Pcm16,
            ),
            highResolutionDspOutput = false,
        ),
        networkStatusSupported = true,
        diagnosticsExportSupported = true,
        diagnosticsCenterSupported = true,
        safeModeSupported = true,
        platformExitInfoSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
        historicalAnrTraceSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
        incidentRecoverySupported = true,
        fileShareSupported = true,
        floatingLyricsSupported = true,
        notificationLyricsSupported = true,
        bluetoothLyricsSupported = true,
        lyriconSupported = true,
        superLyricSupported = true,
        lyricGetterSupported = true,
        flymeStatusLyricsSupported = true,
        colorOsLockScreenLyricsSupported = true,
        settingsBackupSupported = true,
        scheduledBackupSupported = true,
    )

private val systemLocalesAtStartup: LocaleList by lazy {
    appContext.resources.configuration.locales
}

actual fun applyAppLanguageMode(mode: AppLanguageMode) {
    val locales = when (mode) {
        AppLanguageMode.System -> LocaleList.getEmptyLocaleList()
        AppLanguageMode.Chinese -> LocaleList.forLanguageTags("zh-Hans")
        AppLanguageMode.English -> LocaleList.forLanguageTags("en")
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        appContext.getSystemService(LocaleManager::class.java).applicationLocales = locales
        return
    }

    val effectiveLocales = if (mode == AppLanguageMode.System) systemLocalesAtStartup else locales
    val configuration = android.content.res.Configuration(appContext.resources.configuration)
    configuration.setLocales(effectiveLocales)
    if (!effectiveLocales.isEmpty) java.util.Locale.setDefault(effectiveLocales[0])
    @Suppress("DEPRECATION")
    appContext.resources.updateConfiguration(configuration, appContext.resources.displayMetrics)
}

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun byteArrayToImageBitmap(bytes: ByteArray): ImageBitmap? {
    val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    return bm.asImageBitmap()
}

lateinit var appContext: android.content.Context
