package io.github.julystar.musicapp.core.presentation.theme

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Miuix theme wrapper for TidePlayer.
 *
 * Wraps Miuix's [MiuixTheme] so feature screens do not depend directly
 * on Miuix APIs. Uses Miuix default color scheme and text styles.
 */
@Composable
fun AppMiuixTheme(
    content: @Composable () -> Unit,
) {
    MiuixTheme(
        colors = MiuixTheme.colorScheme,
        textStyles = MiuixTheme.textStyles,
        content = content,
    )
}
