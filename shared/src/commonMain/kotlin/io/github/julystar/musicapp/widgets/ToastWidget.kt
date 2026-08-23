package io.github.julystar.musicapp.widgets

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
import io.github.julystar.musicapp.core.presentation.overlay.ToastVM
import io.github.julystar.musicapp.core.presentation.overlay.resolve
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import org.koin.compose.viewmodel.koinViewModel
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow

@Composable
fun ToastFrame(
    toastVM: ToastVM = koinViewModel(),
) {
    val spacing = DesignTokens.spacing
    val hostState = remember { SnackbarHostState() }
    var pendingMessage by remember { mutableStateOf<io.github.julystar.musicapp.core.domain.repository.UiMessage?>(null) }
    val resolvedMessage = pendingMessage?.resolve()

    LaunchedEffect(toastVM) {
        toastVM.messages.collect { msg ->
            pendingMessage = msg
            snapshotFlow { pendingMessage == null }.filter { it }.first()
        }
    }
    LaunchedEffect(pendingMessage, resolvedMessage) {
        if (pendingMessage != null && resolvedMessage != null) {
            hostState.showSnackbar(message = resolvedMessage)
            pendingMessage = null
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        SnackbarHost(
            state = hostState,
            modifier = Modifier.padding(bottom = spacing.xxl),
        )
    }
}
