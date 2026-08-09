package io.github.julystar.musicapp.diagnostics

import kotlin.native.Platform

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
internal actual fun platformDebugFaultInjectionSupported(): Boolean = Platform.isDebugBinary

internal actual fun triggerPlatformKotlinCrash() {
    error("Tide Player debug Kotlin/Native uncaught exception fault injection")
}

internal actual fun triggerPlatformAnr() {
    error("Android ANR injection is only available on Android")
}
