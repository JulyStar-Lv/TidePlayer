package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiniPlayerBar(
    title: String,
    subtitle: String,
    progress: Float,
    onClick: () -> Unit,
    artwork: @Composable () -> Unit,
    controls: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = DesignTokens
    val shape = RoundedCornerShape(22.dp)
    val backdrop = currentDesignBackdrop()
    val surface = MiuixTheme.colorScheme.surfaceContainer
    val clickInteractionSource = remember { MutableInteractionSource() }
    val glassModifier = if (backdrop != null) {
        Modifier.designLiquidGlass(
            backdrop = backdrop,
            shape = shape,
        )
    } else {
        Modifier
            .clip(shape)
            .background(surface.copy(alpha = LiquidGlassDefaults.fallbackSurfaceAlpha))
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(tokens.player.miniBarHeight)
            .shadow(tokens.elevation.popup, shape, clip = false)
            .clip(shape)
            .then(glassModifier)
            .border(0.5.dp, MiuixTheme.colorScheme.onSurface.copy(alpha = 0.10f), shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 8.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = clickInteractionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                    .clearAndSetSemantics {
                        contentDescription = "$title, $subtitle"
                        this.role = Role.Button
                        onClick { onClick(); true }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                artwork()
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = title.ifBlank { "Tide Player" },
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle.ifBlank { "Ready to play" },
                        color = MiuixTheme.colorScheme.onSurface,
                        style = MiuixTheme.textStyles.footnote1,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = controls,
            )
        }
        MiniPlayerProgress(
            progress = progress,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@Composable
fun ExpandedMiniPlayerBar(
    title: String,
    subtitle: String,
    progress: Float,
    onClick: () -> Unit,
    artwork: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = DesignTokens
    val shape = RoundedCornerShape(22.dp)
    val backdrop = currentDesignBackdrop()
    val surface = MiuixTheme.colorScheme.surfaceContainer
    val clickInteractionSource = remember { MutableInteractionSource() }
    val glassModifier = if (backdrop != null) {
        Modifier.designLiquidGlass(
            backdrop = backdrop,
            shape = shape,
        )
    } else {
        Modifier
            .clip(shape)
            .background(surface.copy(alpha = LiquidGlassDefaults.fallbackSurfaceAlpha))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(tokens.player.miniBarHeight)
            .shadow(tokens.elevation.popup, shape, clip = false)
            .clip(shape)
            .then(glassModifier)
            .border(0.5.dp, MiuixTheme.colorScheme.onSurface.copy(alpha = 0.10f), shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = clickInteractionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                    .clearAndSetSemantics {
                        contentDescription = "$title, $subtitle"
                        this.role = Role.Button
                        onClick { onClick(); true }
                    },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    artwork()
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = title.ifBlank { "Tide Player" },
                            style = MiuixTheme.textStyles.body1,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                        )
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = subtitle,
                            color = MiuixTheme.colorScheme.onSurface,
                            style = MiuixTheme.textStyles.footnote1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
        MiniPlayerProgress(
            progress = progress,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@Composable
fun CompactMiniPlayerBar(
    progress: Float,
    accessibilityLabel: String,
    onClick: () -> Unit,
    artwork: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overlayControls: @Composable BoxScope.() -> Unit,
) {
    val tokens = DesignTokens
    val cornerRadius = tokens.shapes.lg
    val shape = RoundedCornerShape(cornerRadius)
    val backdrop = currentDesignBackdrop()
    val surface = MiuixTheme.colorScheme.surfaceContainer
    val clickInteractionSource = remember { MutableInteractionSource() }
    val glassModifier = if (backdrop != null) {
        Modifier.designLiquidGlass(
            backdrop = backdrop,
            shape = shape,
        )
    } else {
        Modifier
            .clip(shape)
            .background(surface.copy(alpha = LiquidGlassDefaults.fallbackSurfaceAlpha))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(tokens.player.compactMiniBarHeight)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .shadow(tokens.elevation.card, shape, clip = false)
            .clip(shape)
            .then(glassModifier)
            .border(0.5.dp, MiuixTheme.colorScheme.onSurface.copy(alpha = 0.10f), shape)
            .semantics { contentDescription = accessibilityLabel }
            .clickable(
                interactionSource = clickInteractionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        artwork()
        overlayControls()
        MiniPlayerProgress(
            progress = progress,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@Composable
fun MiniPlayerProgress(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(2.dp)
                .background(MiuixTheme.colorScheme.primary),
        )
    }
}
