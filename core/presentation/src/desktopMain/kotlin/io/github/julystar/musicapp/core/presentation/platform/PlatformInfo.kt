package io.github.julystar.musicapp.core.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.distinctUntilChanged

actual fun isDesktopPlatform(): Boolean = true

@Composable
actual fun isSystemDarkTheme(): Boolean {
    val initialValue = remember { DesktopSystemTheme.current() }
    val isDark by produceState(initialValue = initialValue) {
        DesktopSystemTheme.changes()
            .distinctUntilChanged()
            .collect { value = it }
    }
    return isDark
}
