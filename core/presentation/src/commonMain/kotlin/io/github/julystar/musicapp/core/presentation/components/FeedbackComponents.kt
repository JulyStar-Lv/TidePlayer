package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.theme.DesignPalette
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun EmptyState(
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
    marker: String? = null,
    icon: Painter? = null,
    iconContentDescription: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val shapes = DesignTokens.shapes
    val spacing = DesignTokens.spacing

    Card(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            if (icon != null || marker != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(shapes.md))
                        .background(MiuixTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (icon != null) {
                        Icon(
                            painter = icon,
                            contentDescription = iconContentDescription,
                            modifier = Modifier.size(24.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    } else if (marker != null) {
                        Text(
                            text = marker,
                            color = MiuixTheme.colorScheme.primary,
                            style = MiuixTheme.textStyles.title2,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (message != null) {
                Text(
                    text = message,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            action?.invoke()
        }
    }
}

@Composable
fun StatusMessageCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    loadingColor: Color = MiuixTheme.colorScheme.primary,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    minHeight: Dp = 240.dp,
    contentSpacing: Dp = 10.dp,
    messageMaxLines: Int = 2,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = loadingColor,
                    ),
                )
            }
            Text(
                text = title,
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = message,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                textAlign = TextAlign.Center,
                maxLines = messageMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (actionText != null && onAction != null) {
                TextButton(
                    text = actionText,
                    onClick = onAction,
                )
            }
        }
    }
}

enum class StatusTone {
    Neutral,
    Info,
    Success,
    Warning,
    Error,
    Accent,
}

@Composable
fun StatusBadge(
    label: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.Neutral,
) {
    val shapes = DesignTokens.shapes
    val colors = statusBadgeColors(tone)

    Row(
        modifier = modifier
            .heightIn(min = 28.dp)
            .clip(RoundedCornerShape(shapes.full))
            .background(colors.container)
            .border(1.dp, colors.border, RoundedCornerShape(shapes.full))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(shapes.full))
                .background(colors.indicator),
        )
        Text(
            text = label,
            color = colors.content,
            style = MiuixTheme.textStyles.footnote2,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun statusBadgeColors(tone: StatusTone): StatusBadgeColors {
    val accent = when (tone) {
        StatusTone.Neutral -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        StatusTone.Info -> DesignPalette.SupportBlue
        StatusTone.Success -> DesignPalette.SupportGreen
        StatusTone.Warning -> DesignPalette.SupportOrange
        StatusTone.Error -> MiuixTheme.colorScheme.error
        StatusTone.Accent -> MiuixTheme.colorScheme.primary
    }
    return if (tone == StatusTone.Neutral) {
        StatusBadgeColors(
            container = MiuixTheme.colorScheme.surfaceContainerHigh,
            border = MiuixTheme.colorScheme.outline,
            content = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            indicator = accent,
        )
    } else {
        StatusBadgeColors(
            container = accent.copy(alpha = 0.14f),
            border = accent.copy(alpha = 0.38f),
            content = accent,
            indicator = accent,
        )
    }
}

@Immutable
private data class StatusBadgeColors(
    val container: Color,
    val border: Color,
    val content: Color,
    val indicator: Color,
)

@Composable
fun SkeletonBlock(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    radius: Dp = DesignTokens.shapes.xs,
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(rememberSkeletonBrush()),
    )
}

@Composable
fun SkeletonBlock(
    height: Dp,
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
    radius: Dp = DesignTokens.shapes.xs,
) {
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(rememberSkeletonBrush()),
    )
}

@Composable
private fun rememberSkeletonBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skeletonOffset",
    )
    val base = MiuixTheme.colorScheme.surfaceContainerHigh
    return Brush.linearGradient(
        colors = listOf(
            base.copy(alpha = 0.86f),
            base.copy(alpha = 0.36f),
            base.copy(alpha = 0.86f),
        ),
        start = Offset(offset - 220f, offset - 220f),
        end = Offset(offset, offset),
    )
}
