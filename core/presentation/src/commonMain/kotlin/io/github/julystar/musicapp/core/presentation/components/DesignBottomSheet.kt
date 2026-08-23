package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DesignBottomSheet(
    show: Boolean,
    title: String? = null,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    OverlayBottomSheet(
        show = show,
        title = title,
        onDismissRequest = onDismissRequest,
        backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
        cornerRadius = DesignTokens.shapes.lg,
        insideMargin = DpSize(width = 20.dp, height = 24.dp),
        renderInRootScaffold = true,
        content = content,
    )
}
