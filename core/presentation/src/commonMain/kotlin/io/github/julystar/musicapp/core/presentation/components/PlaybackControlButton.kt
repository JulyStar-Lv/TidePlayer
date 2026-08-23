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
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class PlaybackControlSize {
    Mini,
    Large,
}

enum class PlaybackControlVariant {
    Ghost,
    Secondary,
    Primary,
}

@Composable
fun PlaybackControlButton(
    painter: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: PlaybackControlSize = PlaybackControlSize.Mini,
    variant: PlaybackControlVariant = PlaybackControlVariant.Secondary,
    contentDescription: String? = null,
    showClickIndication: Boolean = true,
) {
    val isPrimary = variant == PlaybackControlVariant.Primary
    val buttonSize = playbackControlButtonSize(size, variant)
    val touchTargetSize = maxOf(buttonSize, DesignTokens.adaptive.minimumTouchTarget)
    val background = when (variant) {
        PlaybackControlVariant.Ghost -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
        PlaybackControlVariant.Secondary -> Brush.linearGradient(
            listOf(MiuixTheme.colorScheme.secondaryContainer, MiuixTheme.colorScheme.secondaryContainer),
        )
        PlaybackControlVariant.Primary -> Brush.linearGradient(
            listOf(MiuixTheme.colorScheme.primary, MiuixTheme.colorScheme.secondary),
        )
    }
    val backgroundAlpha = when {
        enabled -> 1f
        size == PlaybackControlSize.Large -> 0.42f
        else -> 0.46f
    }
    val iconTint = when {
        !enabled && size == PlaybackControlSize.Mini -> MiuixTheme.colorScheme.disabledOnSurface
        isPrimary -> Color.White
        !enabled -> MiuixTheme.colorScheme.disabledOnSurface
        variant == PlaybackControlVariant.Ghost -> MiuixTheme.colorScheme.onSurface
        else -> MiuixTheme.colorScheme.onSecondaryContainer
    }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(touchTargetSize)
            .clickable(
                interactionSource = interactionSource,
                indication = if (showClickIndication) LocalIndication.current else null,
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
                modifier = Modifier.size(playbackControlIconSize(size, variant)),
            )
        }
    }
}

private fun playbackControlButtonSize(
    size: PlaybackControlSize,
    variant: PlaybackControlVariant,
): Dp = when (size) {
    PlaybackControlSize.Mini -> when (variant) {
        PlaybackControlVariant.Ghost -> 40.dp
        PlaybackControlVariant.Primary -> 34.dp
        PlaybackControlVariant.Secondary -> 30.dp
    }
    PlaybackControlSize.Large -> 64.dp
}

private fun playbackControlIconSize(
    size: PlaybackControlSize,
    variant: PlaybackControlVariant,
): Dp = when (size) {
    PlaybackControlSize.Mini -> when (variant) {
        PlaybackControlVariant.Ghost -> 20.dp
        PlaybackControlVariant.Primary -> 15.dp
        PlaybackControlVariant.Secondary -> 12.dp
    }
    PlaybackControlSize.Large -> 26.dp
}
