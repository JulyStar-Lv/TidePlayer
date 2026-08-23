package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun TagChip(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(DesignTokens.shapes.full)
    val colors = tagChipColors(selected = selected, enabled = enabled)

    Box(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(colors.container)
            .border(1.dp, colors.border, shape)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Text(
                text = label,
                color = colors.content,
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun tagChipColors(selected: Boolean, enabled: Boolean): TagChipColors = when {
    !enabled -> TagChipColors(
        container = MiuixTheme.colorScheme.disabledSecondary,
        border = MiuixTheme.colorScheme.disabledSecondaryVariant,
        content = MiuixTheme.colorScheme.disabledOnSurface,
    )
    selected -> TagChipColors(
        container = MiuixTheme.colorScheme.primary,
        border = MiuixTheme.colorScheme.primary,
        content = MiuixTheme.colorScheme.onPrimary,
    )
    else -> TagChipColors(
        container = MiuixTheme.colorScheme.surfaceContainerHigh,
        border = Color.Transparent,
        content = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    )
}

@Immutable
private data class TagChipColors(
    val container: Color,
    val border: Color,
    val content: Color,
)
