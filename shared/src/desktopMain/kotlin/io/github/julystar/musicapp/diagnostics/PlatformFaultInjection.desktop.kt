package io.github.julystar.musicapp.diagnostics

import io.github.julystar.musicapp.migration.LegacyEnvironmentVariables

internal actual fun platformDebugFaultInjectionSupported(): Boolean =
    System.getProperty("musicapp.developerMode").toBoolean() ||
        System.getenv("MUSICAPP_DEVELOPER_MODE").toBoolean() ||
        System.getenv(LegacyEnvironmentVariables.DEVELOPER_MODE).toBoolean()

internal actual fun triggerPlatformKotlinCrash() {
    Thread {
        error("Tide Player developer-mode Kotlin uncaught exception fault injection")
    }.apply { name = "diagnostics-kotlin-crash" }.start()
}

internal actual fun triggerPlatformAnr() {
    error("Android ANR injection is only available on Android")
}
