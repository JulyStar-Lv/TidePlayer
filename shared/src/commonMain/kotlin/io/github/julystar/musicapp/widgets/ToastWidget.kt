package io.github.julystar.musicapp.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.julystar.musicapp.core.presentation.components.DesignToast
import io.github.julystar.musicapp.core.presentation.overlay.ToastVM
import io.github.julystar.musicapp.core.presentation.overlay.resolve
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import kotlinx.coroutines.delay
import io.github.julystar.musicapp.core.domain.repository.UiMessage
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ToastFrame(
    toastVM: ToastVM = koinViewModel(),
) {
    val spacing = DesignTokens.spacing
    var message by remember { mutableStateOf<UiMessage?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        toastVM.messages.collect { msg ->
            message = msg
            visible = true
            delay(2000)
            visible = false
            delay(200)
        }
    }

    val resolvedMessage = message?.resolve().orEmpty()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            DesignToast(
                message = resolvedMessage,
                modifier = Modifier.padding(bottom = spacing.xxl),
            )
        }
    }
}
