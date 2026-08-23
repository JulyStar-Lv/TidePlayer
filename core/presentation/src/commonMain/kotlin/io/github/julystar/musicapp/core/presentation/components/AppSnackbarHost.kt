package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult

enum class AppSnackbarDuration {
    Short,
    Long,
    Indefinite,
}

enum class AppSnackbarResult {
    Dismissed,
    ActionPerformed,
}

@Stable
class AppSnackbarHostState internal constructor(
    internal val delegate: SnackbarHostState = SnackbarHostState(),
) {
    suspend fun showMessage(
        message: String,
        actionLabel: String? = null,
        withDismissAction: Boolean = false,
        duration: AppSnackbarDuration = AppSnackbarDuration.Short,
    ): AppSnackbarResult = delegate.showSnackbar(
        message = message,
        actionLabel = actionLabel,
        withDismissAction = withDismissAction,
        duration = when (duration) {
            AppSnackbarDuration.Short -> SnackbarDuration.Short
            AppSnackbarDuration.Long -> SnackbarDuration.Long
            AppSnackbarDuration.Indefinite -> SnackbarDuration.Indefinite
        },
    ).let { result ->
        when (result) {
            SnackbarResult.Dismissed -> AppSnackbarResult.Dismissed
            SnackbarResult.ActionPerformed -> AppSnackbarResult.ActionPerformed
        }
    }

    suspend fun dismissOldest() {
        delegate.oldestSnackbarData()?.dismiss()
    }
}

@Composable
fun rememberAppSnackbarHostState(): AppSnackbarHostState = remember { AppSnackbarHostState() }

@Composable
fun AppSnackbarHost(
    state: AppSnackbarHostState,
    modifier: Modifier = Modifier,
    canSwipeToDismiss: Boolean = true,
) {
    SnackbarHost(
        state = state.delegate,
        modifier = modifier,
        canSwipeToDismiss = canSwipeToDismiss,
    )
}
