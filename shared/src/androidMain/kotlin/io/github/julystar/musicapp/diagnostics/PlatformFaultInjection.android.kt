package io.github.julystar.musicapp.diagnostics

import android.content.pm.ApplicationInfo
import io.github.julystar.musicapp.platform.appContext

internal actual fun platformDebugFaultInjectionSupported(): Boolean =
    appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

internal actual fun triggerPlatformKotlinCrash() {
    Thread {
        error("Tide Player debug Kotlin uncaught exception fault injection")
    }.apply { name = "diagnostics-kotlin-crash" }.start()
}

internal actual fun triggerPlatformAnr() {
    check(platformDebugFaultInjectionSupported()) { "ANR injection is disabled" }
    Thread.sleep(30_000)
}
