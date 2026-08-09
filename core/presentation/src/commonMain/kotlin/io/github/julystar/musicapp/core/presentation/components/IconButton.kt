package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.julystar.musicapp.core.presentation.theme.designOnPrimaryButtonColor
import io.github.julystar.musicapp.core.presentation.theme.designPrimaryButtonColor
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class DesignIconButtonSize {
    Small,
    Medium,
    Touch,
    Large,
}

fun designIconButtonSizeToDp(size: DesignIconButtonSize): Dp {
    return when (size) {
        DesignIconButtonSize.Small -> 24.dp
        DesignIconButtonSize.Medium -> 36.dp
        DesignIconButtonSize.Touch -> 44.dp
        DesignIconButtonSize.Large -> 64.dp
    }
}

enum class DesignIconButtonVariant {
    Default,
    Surface,
    Primary,
    Error,
    ErrorFilled,
}

enum class DesignPlayerControlSize {
    Mini,
    Large,
}

enum class DesignPlayerControlVariant {
    Ghost,
    Secondary,
    Primary,
}

data class DesignIconButtonColors(
    val buttonBg: Color? = null,
    val buttonDisabledBg: Color? = null,
    val iconTint: Color? = null,
)

enum class CompatIconButtonSize {
    Small,
    Medium,
    Touch,
    Large,
}

fun compatIconButtonSizeToDp(sizeType: CompatIconButtonSize): Dp {
    return designIconButtonSizeToDp(sizeType.toDesignIconButtonSize())
}

enum class CompatIconButtonType {
    Default,
    Surface,
    Primary,
    Error,
    ErrorVariant,
}

data class CompatIconButtonColors(
    val buttonBg: Color? = null,
    val buttonDisabledBg: Color? = null,
    val iconTint: Color? = null,
)

@Composable
fun CompatIconButton(
    sizeType: CompatIconButtonSize,
    buttonType: CompatIconButtonType,
    painter: Painter,
    onClick: () -> Unit,
    overrideColors: CompatIconButtonColors? = null,
    disabled: Boolean = false,
) {
    DesignIconButton(
        size = sizeType.toDesignIconButtonSize(),
        variant = buttonType.toDesignIconButtonVariant(),
        painter = painter,
        onClick = onClick,
        colors = overrideColors?.toDesignIconButtonColors(),
        enabled = !disabled,
    )
}

@Composable
fun DesignIconButton(
    size: DesignIconButtonSize,
    variant: DesignIconButtonVariant,
    painter: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    colors: DesignIconButtonColors? = null,
    enabled: Boolean = true,
    showClickIndication: Boolean = true,
) {
    val buttonSize = designIconButtonSizeToDp(size)
    val touchTargetSize = maxOf(buttonSize, DesignTokens.adaptive.minimumTouchTarget)
    val isFilled = variant == DesignIconButtonVariant.Primary || variant == DesignIconButtonVariant.ErrorFilled
    val iconSize = designIconButtonIconSizeToDp(size)
    val buttonBg = designIconButtonBackground(
        variant = variant,
        colors = colors,
        enabled = enabled,
        isFilled = isFilled,
    )
    val iconTint = designIconButtonIconTint(
        variant = variant,
        colors = colors,
        enabled = enabled,
        isFilled = isFilled,
    )
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(touchTargetSize)
            .clickable(
                interactionSource = interactionSource,
                indication = if (showClickIndication) {
                    LocalIndication.current
                } else {
                    null
                },
                enabled = enabled,
                onClick = {
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(buttonSize)
                .clip(RoundedCornerShape(999.dp))
                .background(buttonBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painter,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = iconTint,
            )
        }
    }
}

@Composable
fun DesignPlayerControlButton(
    painter: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: DesignPlayerControlSize = DesignPlayerControlSize.Mini,
    variant: DesignPlayerControlVariant = DesignPlayerControlVariant.Secondary,
    contentDescription: String? = null,
    showClickIndication: Boolean = true,
) {
    val isPrimary = variant == DesignPlayerControlVariant.Primary
    val buttonSize = designPlayerControlButtonSize(size, variant)
    val touchTargetSize = maxOf(buttonSize, DesignTokens.adaptive.minimumTouchTarget)
    val background = when (variant) {
        DesignPlayerControlVariant.Ghost -> Brush.linearGradient(
            listOf(Color.Transparent, Color.Transparent),
        )
        DesignPlayerControlVariant.Secondary -> Brush.linearGradient(
            listOf(
                MiuixTheme.colorScheme.secondaryContainer,
                MiuixTheme.colorScheme.secondaryContainer,
            ),
        )
        DesignPlayerControlVariant.Primary -> Brush.linearGradient(
            listOf(
                MiuixTheme.colorScheme.primary,
                MiuixTheme.colorScheme.secondary,
            ),
        )
    }
    val backgroundAlpha = when {
        enabled -> 1f
        size == DesignPlayerControlSize.Large -> 0.42f
        else -> 0.46f
    }
    val iconTint = when {
        !enabled && size == DesignPlayerControlSize.Mini -> MiuixTheme.colorScheme.disabledOnSurface
        isPrimary -> Color.White
        !enabled -> MiuixTheme.colorScheme.disabledOnSurface
        variant == DesignPlayerControlVariant.Ghost -> MiuixTheme.colorScheme.onSurface
        else -> MiuixTheme.colorScheme.onSecondaryContainer
    }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(touchTargetSize)
            .clickable(
                interactionSource = interactionSource,
                indication = if (showClickIndication) {
                    LocalIndication.current
                } else {
                    null
                },
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(buttonSize)
                .clip(RoundedCornerShape(999.dp))
                .background(background, alpha = backgroundAlpha),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painter,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(designPlayerControlIconSize(size, variant)),
            )
        }
    }
}

private fun designIconButtonIconSizeToDp(size: DesignIconButtonSize): Dp {
    return when (size) {
        DesignIconButtonSize.Small -> 10.dp
        DesignIconButtonSize.Medium -> 16.dp
        DesignIconButtonSize.Touch -> 24.dp
        DesignIconButtonSize.Large -> 24.dp
    }
}

private fun designPlayerControlButtonSize(
    size: DesignPlayerControlSize,
    variant: DesignPlayerControlVariant,
): Dp {
    return when (size) {
        DesignPlayerControlSize.Mini -> when (variant) {
            DesignPlayerControlVariant.Ghost -> 40.dp
            DesignPlayerControlVariant.Primary -> 34.dp
            DesignPlayerControlVariant.Secondary -> 30.dp
        }
        DesignPlayerControlSize.Large -> 64.dp
    }
}

private fun designPlayerControlIconSize(
    size: DesignPlayerControlSize,
    variant: DesignPlayerControlVariant,
): Dp {
    return when (size) {
        DesignPlayerControlSize.Mini -> when (variant) {
            DesignPlayerControlVariant.Ghost -> 20.dp
            DesignPlayerControlVariant.Primary -> 15.dp
            DesignPlayerControlVariant.Secondary -> 12.dp
        }
        DesignPlayerControlSize.Large -> 26.dp
    }
}

@Composable
private fun designIconButtonBackground(
    variant: DesignIconButtonVariant,
    colors: DesignIconButtonColors?,
    enabled: Boolean,
    isFilled: Boolean,
): Color {
    return if (!enabled) {
        if (isFilled) {
            colors?.buttonDisabledBg ?: MiuixTheme.colorScheme.surfaceVariant
        } else {
            Color.Transparent
        }
    } else {
        colors?.buttonBg ?: when (variant) {
            DesignIconButtonVariant.Primary -> designPrimaryButtonColor()
            DesignIconButtonVariant.Surface -> MiuixTheme.colorScheme.surfaceContainerHigh
            DesignIconButtonVariant.Default -> Color.Transparent
            DesignIconButtonVariant.Error -> Color.Transparent
            DesignIconButtonVariant.ErrorFilled -> MiuixTheme.colorScheme.error
        }
    }
}

@Composable
private fun designIconButtonIconTint(
    variant: DesignIconButtonVariant,
    colors: DesignIconButtonColors?,
    enabled: Boolean,
    isFilled: Boolean,
): Color {
    return if (!enabled) {
        if (isFilled) {
            MiuixTheme.colorScheme.surface
        } else {
            MiuixTheme.colorScheme.surfaceVariant
        }
    } else {
        colors?.iconTint ?: when (variant) {
            DesignIconButtonVariant.Primary -> designOnPrimaryButtonColor()
            DesignIconButtonVariant.Surface -> designPrimaryButtonColor()
            DesignIconButtonVariant.Default -> MiuixTheme.colorScheme.onSurface
            DesignIconButtonVariant.Error -> MiuixTheme.colorScheme.error
            DesignIconButtonVariant.ErrorFilled -> MiuixTheme.colorScheme.surface
        }
    }
}

private fun CompatIconButtonSize.toDesignIconButtonSize(): DesignIconButtonSize {
    return when (this) {
        CompatIconButtonSize.Small -> DesignIconButtonSize.Small
        CompatIconButtonSize.Medium -> DesignIconButtonSize.Medium
        CompatIconButtonSize.Touch -> DesignIconButtonSize.Touch
        CompatIconButtonSize.Large -> DesignIconButtonSize.Large
    }
}

private fun CompatIconButtonType.toDesignIconButtonVariant(): DesignIconButtonVariant {
    return when (this) {
        CompatIconButtonType.Default -> DesignIconButtonVariant.Default
        CompatIconButtonType.Surface -> DesignIconButtonVariant.Surface
        CompatIconButtonType.Primary -> DesignIconButtonVariant.Primary
        CompatIconButtonType.Error -> DesignIconButtonVariant.Error
        CompatIconButtonType.ErrorVariant -> DesignIconButtonVariant.ErrorFilled
    }
}

private fun CompatIconButtonColors.toDesignIconButtonColors(): DesignIconButtonColors {
    return DesignIconButtonColors(
        buttonBg = buttonBg,
        buttonDisabledBg = buttonDisabledBg,
        iconTint = iconTint,
    )
}
