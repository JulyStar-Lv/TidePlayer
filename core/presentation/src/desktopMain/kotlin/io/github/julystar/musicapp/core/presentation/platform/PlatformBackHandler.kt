package io.github.julystar.musicapp.core.presentation.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import java.awt.KeyEventPostProcessor
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnBack by rememberUpdatedState(onBack)

    DisposableEffect(Unit) {
        val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        val processor = KeyEventPostProcessor { event ->
            val shouldNavigateBack =
                currentEnabled &&
                    event.id == KeyEvent.KEY_PRESSED &&
                    event.keyCode == KeyEvent.VK_ESCAPE &&
                    event.modifiersEx == 0
            if (shouldNavigateBack) {
                currentOnBack()
                true
            } else {
                false
            }
        }
        focusManager.addKeyEventPostProcessor(processor)
        onDispose {
            focusManager.removeKeyEventPostProcessor(processor)
        }
    }
}
