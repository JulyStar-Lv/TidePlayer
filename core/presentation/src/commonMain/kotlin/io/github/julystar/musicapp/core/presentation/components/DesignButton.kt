package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.julystar.musicapp.core.presentation.theme.designOnPrimaryButtonColor
import io.github.julystar.musicapp.core.presentation.theme.designOnSecondaryButtonColor
import io.github.julystar.musicapp.core.presentation.theme.designPrimaryButtonColor
import io.github.julystar.musicapp.core.presentation.theme.designSecondaryButtonColor
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonColors
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class DesignButtonVariant {
    Primary,
    Secondary,
    Tertiary,
    Ghost,
    Danger,
}

@Composable
fun DesignButton(
    text: String,
    variant: DesignButtonVariant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minWidth: Dp = 0.dp,
    minHeight: Dp = 40.dp,
    insideMargin: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
) {
    DesignButton(
        variant = variant,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        minWidth = minWidth,
        minHeight = minHeight,
        insideMargin = insideMargin,
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.button,
        )
    }
}

@Composable
fun DesignButton(
    variant: DesignButtonVariant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minWidth: Dp = 0.dp,
    minHeight: Dp = 40.dp,
    insideMargin: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    colors: ButtonColors? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        cornerRadius = DesignTokens.shapes.full,
        minWidth = minWidth,
        minHeight = maxOf(minHeight, DesignTokens.adaptive.minimumTouchTarget),
        colors = colors ?: designButtonColors(variant),
        insideMargin = insideMargin,
        content = content,
    )
}

@Composable
private fun designButtonColors(variant: DesignButtonVariant): ButtonColors {
    return when (variant) {
        DesignButtonVariant.Primary -> ButtonDefaults.buttonColorsPrimary(
            color = designPrimaryButtonColor(),
            contentColor = designOnPrimaryButtonColor(),
        )
        DesignButtonVariant.Secondary -> ButtonDefaults.buttonColors(
            color = designSecondaryButtonColor(),
            contentColor = designOnSecondaryButtonColor(),
        )
        DesignButtonVariant.Tertiary -> ButtonDefaults.buttonColors(
            color = MiuixTheme.colorScheme.tertiaryContainer,
            contentColor = MiuixTheme.colorScheme.onTertiaryContainer,
        )
        DesignButtonVariant.Ghost -> ButtonDefaults.buttonColors(
            color = Color.Transparent,
            disabledColor = Color.Transparent,
            contentColor = MiuixTheme.colorScheme.onSurface,
            disabledContentColor = MiuixTheme.colorScheme.disabledOnSurface,
        )
        DesignButtonVariant.Danger -> ButtonDefaults.buttonColors(
            color = MiuixTheme.colorScheme.error,
            disabledColor = MiuixTheme.colorScheme.disabledPrimaryButton,
            contentColor = MiuixTheme.colorScheme.onError,
            disabledContentColor = MiuixTheme.colorScheme.disabledOnPrimaryButton,
        )
    }
}
