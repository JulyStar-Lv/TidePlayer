package io.github.julystar.musicapp.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.julystar.musicapp.core.domain.repository.UiMessage
import io.github.julystar.musicapp.core.presentation.components.AppSnackbarHost
import io.github.julystar.musicapp.core.presentation.components.rememberAppSnackbarHostState
import io.github.julystar.musicapp.core.presentation.overlay.ToastVM
import io.github.julystar.musicapp.core.presentation.overlay.resolve
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ToastFrame(
    toastVM: ToastVM = koinViewModel(),
) {
    val spacing = DesignTokens.spacing
    val hostState = rememberAppSnackbarHostState()
    val pendingMessages = remember {
        mutableStateListOf<UiMessage>()
    }

    LaunchedEffect(toastVM) {
        toastVM.messages.collect { msg ->
            pendingMessages.add(msg)
        }
    }
    val currentMessage = pendingMessages.firstOrNull()
    val resolvedMessage = currentMessage?.resolve()
    LaunchedEffect(currentMessage, resolvedMessage, hostState) {
        if (currentMessage != null && resolvedMessage != null) {
            hostState.showMessage(message = resolvedMessage)
            pendingMessages.remove(currentMessage)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        AppSnackbarHost(
            state = hostState,
            modifier = Modifier.padding(bottom = spacing.xxl),
        )
    }
}
