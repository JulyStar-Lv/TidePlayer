package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.julystar.musicapp.core.presentation.theme.designOnPrimaryButtonColor
import io.github.julystar.musicapp.core.presentation.theme.designPrimaryButtonColor
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextButtonColors
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class DesignTextButtonVariant {
    Primary,
    PrimaryFilled,
    Tonal,
    Error,
    Default,
}

enum class DesignTextButtonSize {
    Medium,
    Small,
}

enum class CompatTextButtonType {
    Primary,
    PrimaryVariant,
    Error,
    Default,
}

enum class CompatTextButtonSize {
    Medium,
    Small,
}

@Composable
fun CompatTextButton(
    text: String,
    type: CompatTextButtonType,
    size: CompatTextButtonSize,
    onClick: () -> Unit,
    disabled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    DesignTextButton(
        text = text,
        variant = type.toDesignTextButtonVariant(),
        size = size.toDesignTextButtonSize(),
        onClick = onClick,
        enabled = !disabled,
        modifier = modifier,
    )
}

@Composable
fun DesignTextButton(
    text: String,
    variant: DesignTextButtonVariant,
    size: DesignTextButtonSize,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: TextButtonColors? = null,
) {
    val buttonColors = colors ?: designTextButtonColors(variant)
    val visualMinHeight = if (size == DesignTextButtonSize.Small) 28.dp else 36.dp

    TextButton(
        modifier = modifier.padding(0.dp),
        colors = buttonColors,
        onClick = onClick,
        enabled = enabled,
        text = text,
        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        minHeight = maxOf(visualMinHeight, DesignTokens.adaptive.minimumTouchTarget),
        minWidth = 0.dp,
    )
}

@Composable
private fun designTextButtonColors(variant: DesignTextButtonVariant): TextButtonColors {
    return when (variant) {
        DesignTextButtonVariant.Default -> ButtonDefaults.textButtonColors(
            color = Color.Transparent,
            textColor = MiuixTheme.colorScheme.onSurface,
        )
        DesignTextButtonVariant.Primary -> ButtonDefaults.textButtonColors(
            color = Color.Transparent,
            textColor = designPrimaryButtonColor(),
        )
        DesignTextButtonVariant.PrimaryFilled -> ButtonDefaults.textButtonColors(
            color = designPrimaryButtonColor(),
            textColor = designOnPrimaryButtonColor(),
        )
        DesignTextButtonVariant.Tonal -> ButtonDefaults.textButtonColors(
            color = MiuixTheme.colorScheme.tertiaryContainer,
            textColor = designPrimaryButtonColor(),
        )
        DesignTextButtonVariant.Error -> ButtonDefaults.textButtonColors(
            color = Color.Transparent,
            textColor = MiuixTheme.colorScheme.error,
        )
    }
}

private fun CompatTextButtonType.toDesignTextButtonVariant(): DesignTextButtonVariant {
    return when (this) {
        CompatTextButtonType.Primary -> DesignTextButtonVariant.Primary
        CompatTextButtonType.PrimaryVariant -> DesignTextButtonVariant.PrimaryFilled
        CompatTextButtonType.Error -> DesignTextButtonVariant.Error
        CompatTextButtonType.Default -> DesignTextButtonVariant.Default
    }
}

private fun CompatTextButtonSize.toDesignTextButtonSize(): DesignTextButtonSize {
    return when (this) {
        CompatTextButtonSize.Medium -> DesignTextButtonSize.Medium
        CompatTextButtonSize.Small -> DesignTextButtonSize.Small
    }
}
