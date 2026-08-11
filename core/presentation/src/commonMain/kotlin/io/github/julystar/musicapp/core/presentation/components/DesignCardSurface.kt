package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Semantic TidePlayer surface used by page cards, settings groups and feedback panels.
 * Normal content relies on tonal separation instead of a permanent outline; callers can
 * opt into a border for selected, warning or floating states.
 */
@Composable
fun DesignCardSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = DesignTokens.shapes.card,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    fillMaxWidth: Boolean = true,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    elevation: Dp = DesignTokens.elevation.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val widthModifier = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier
    val shadowModifier = if (elevation > 0.dp) {
        Modifier.shadow(elevation, shape, clip = false)
    } else {
        Modifier
    }
    val borderModifier = borderColor?.let { color ->
        Modifier.border(1.dp, color, shape)
    } ?: Modifier
    val interactionModifier = if (onClick != null) {
        Modifier.heightIn(min = DesignTokens.adaptive.minimumTouchTarget)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(widthModifier)
            .then(shadowModifier)
            .then(interactionModifier)
            .clip(shape)
            .background(backgroundColor ?: MiuixTheme.colorScheme.surfaceContainer)
            .then(borderModifier)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
        content = content,
    )
}
