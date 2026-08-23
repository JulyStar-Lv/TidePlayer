package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DesignGradientPlayButton(
    painter: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: PlaybackControlSize = PlaybackControlSize.Mini,
    contentDescription: String? = null,
    showClickIndication: Boolean = true,
) {
    PlaybackControlButton(
        painter = painter,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        size = size,
        variant = PlaybackControlVariant.Primary,
        contentDescription = contentDescription,
        showClickIndication = showClickIndication,
    )
}

@Composable
fun DesignMiniPlayerBar(
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
            .background(surface.copy(alpha = DesignLiquidGlassDefaults.fallbackSurfaceAlpha))
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
        DesignMiniPlayerProgress(
            progress = progress,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@Composable
fun DesignExpandedMiniPlayerBar(
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
            .background(surface.copy(alpha = DesignLiquidGlassDefaults.fallbackSurfaceAlpha))
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
        DesignMiniPlayerProgress(
            progress = progress,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@Composable
fun DesignCompactMiniPlayerBar(
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
            .background(surface.copy(alpha = DesignLiquidGlassDefaults.fallbackSurfaceAlpha))
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
        DesignMiniPlayerProgress(
            progress = progress,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@Composable
fun DesignMiniPlayerProgress(
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

@Composable
fun DesignBottomNavigationGlassSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(0.dp)
    val backdrop = currentDesignBackdrop()
    val surface = MiuixTheme.colorScheme.surfaceContainer
    val glassModifier = if (backdrop != null) {
        Modifier.designLiquidGlass(
            backdrop = backdrop,
            shape = shape,
        )
    } else {
        Modifier.background(
            surface.copy(alpha = DesignLiquidGlassDefaults.fallbackSurfaceAlpha),
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(glassModifier),
        content = content,
    )
}

@Immutable
data class DesignBottomNavigationItem(
    val label: String,
    val painter: Painter,
    val contentDescription: String? = label,
)

@Composable
fun DesignBottomNavigationBar(
    items: List<DesignBottomNavigationItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 5.dp),
) {
    if (items.isEmpty()) return

    val shapes = DesignTokens.shapes
    val navigation = DesignTokens.navigation

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height ?: navigation.compactBarHeight)
            .padding(contentPadding),
    ) {
        val selected = selectedIndex.coerceIn(0, items.lastIndex)

        Row(
            modifier = Modifier
                .fillMaxSize()
                .selectableGroup(),
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = selected == index
                val tint = if (isSelected) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurface
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(shapes.lg))
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            onClick = { onItemSelected(index) },
                        )
                        .clearAndSetSemantics {
                            contentDescription = item.contentDescription ?: item.label
                            this.role = Role.Tab
                            this.selected = isSelected
                            onClick { onItemSelected(index); true }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = item.painter,
                        tint = tint,
                        contentDescription = null,
                        modifier = Modifier.size(navigation.compactIconSize),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.label,
                        color = tint,
                        style = MiuixTheme.textStyles.footnote2.copy(
                            fontSize = navigation.compactLabelSize,
                            lineHeight = 12.sp,
                        ),
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
