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
fun DesignEmptyState(
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
fun DesignStatusCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    loadingColor: Color = MiuixTheme.colorScheme.primary,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    minHeight: Dp = 240.dp,
    cornerRadius: Dp = DesignTokens.shapes.lg,
    surfaceAlpha: Float = 1f,
    borderAlpha: Float = 1f,
    contentSpacing: Dp = 10.dp,
    messageMaxLines: Int = 2,
) {
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(shape)
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = surfaceAlpha))
            .border(1.dp, MiuixTheme.colorScheme.outline.copy(alpha = borderAlpha), shape)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
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

enum class DesignIconBadgeVariant {
    Neutral,
    Surface,
    Brand,
}

@Composable
fun DesignIconBadge(
    modifier: Modifier = Modifier,
    variant: DesignIconBadgeVariant = DesignIconBadgeVariant.Surface,
    marker: String? = null,
    icon: Painter? = null,
    iconContentDescription: String? = null,
    accentColor: Color? = null,
    content: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(DesignTokens.shapes.md)
    val surfaceBackgroundColor = accentColor?.copy(alpha = 0.16f)
        ?: MiuixTheme.colorScheme.tertiaryContainer
    val backgroundBrush = when (variant) {
        DesignIconBadgeVariant.Neutral -> Brush.linearGradient(
            listOf(
                MiuixTheme.colorScheme.surfaceContainerHigh,
                MiuixTheme.colorScheme.surfaceContainerHigh,
            ),
        )
        DesignIconBadgeVariant.Surface -> Brush.linearGradient(
            listOf(
                surfaceBackgroundColor,
                surfaceBackgroundColor,
            ),
        )
        DesignIconBadgeVariant.Brand -> Brush.linearGradient(
            listOf(
                DesignPalette.BrandPink,
                DesignPalette.Secondary,
            ),
        )
    }
    val contentColor = when (variant) {
        DesignIconBadgeVariant.Neutral -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        DesignIconBadgeVariant.Surface -> accentColor ?: MiuixTheme.colorScheme.primary
        DesignIconBadgeVariant.Brand -> Color.White
    }

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(shape)
            .background(backgroundBrush),
        contentAlignment = Alignment.Center,
    ) {
        when {
            content != null -> content()
            icon != null -> Icon(
                painter = icon,
                contentDescription = iconContentDescription,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
            marker != null -> Text(
                text = marker,
                color = contentColor,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

enum class DesignStatusTone {
    Neutral,
    Info,
    Success,
    Warning,
    Error,
    Accent,
}

@Composable
fun DesignStatusBadge(
    label: String,
    modifier: Modifier = Modifier,
    tone: DesignStatusTone = DesignStatusTone.Neutral,
) {
    val shapes = DesignTokens.shapes
    val colors = designStatusBadgeColors(tone)

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
private fun designStatusBadgeColors(tone: DesignStatusTone): DesignStatusBadgeColors {
    val accent = when (tone) {
        DesignStatusTone.Neutral -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        DesignStatusTone.Info -> DesignPalette.SupportBlue
        DesignStatusTone.Success -> DesignPalette.SupportGreen
        DesignStatusTone.Warning -> DesignPalette.SupportOrange
        DesignStatusTone.Error -> MiuixTheme.colorScheme.error
        DesignStatusTone.Accent -> MiuixTheme.colorScheme.primary
    }
    return if (tone == DesignStatusTone.Neutral) {
        DesignStatusBadgeColors(
            container = MiuixTheme.colorScheme.surfaceContainerHigh,
            border = MiuixTheme.colorScheme.outline,
            content = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            indicator = accent,
        )
    } else {
        DesignStatusBadgeColors(
            container = accent.copy(alpha = 0.14f),
            border = accent.copy(alpha = 0.38f),
            content = accent,
            indicator = accent,
        )
    }
}

@Immutable
private data class DesignStatusBadgeColors(
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
