package io.github.julystar.musicapp.core.presentation.platform

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal object DesktopSystemTheme {
    private val osName = System.getProperty("os.name").orEmpty()

    fun current(): Boolean = when {
        osName.startsWith("Mac", ignoreCase = true) -> MacSystemTheme.current()
        osName.startsWith("Windows", ignoreCase = true) -> WindowsSystemTheme.current()
        osName.startsWith("Linux", ignoreCase = true) -> LinuxSystemTheme.current()
        else -> false
    }

    fun changes(): Flow<Boolean> = callbackFlow {
        trySend(current())
        val observer = when {
            osName.startsWith("Mac", ignoreCase = true) -> MacSystemTheme.observe { trySend(it) }
            osName.startsWith("Windows", ignoreCase = true) -> WindowsSystemTheme.observe { trySend(it) }
            osName.startsWith("Linux", ignoreCase = true) -> LinuxSystemTheme.observe { trySend(it) }
            else -> null
        }
        awaitClose { observer?.close() }
    }
}

internal fun runDesktopCommand(vararg arguments: String): String {
    val process = ProcessBuilder(*arguments)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText().trim() }
    process.waitFor()
    return output
}
