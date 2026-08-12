package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class DesignTabsVariant {
    Line,
    Pill,
    Segmented,
    Filled,
}

@Immutable
data class DesignTabItem(
    val label: String,
    val badge: String? = null,
    val enabled: Boolean = true,
)

@Composable
fun DesignTabs(
    items: List<DesignTabItem>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    variant: DesignTabsVariant = DesignTabsVariant.Line,
    enabled: Boolean = true,
) {
    if (items.isEmpty()) return

    val safeSelectedIndex = selectedIndex.coerceIn(0, items.lastIndex)

    when (variant) {
        DesignTabsVariant.Line -> DesignLineTabs(
            items = items,
            selectedIndex = safeSelectedIndex,
            onSelectedIndexChange = onSelectedIndexChange,
            modifier = modifier,
            enabled = enabled,
        )
        DesignTabsVariant.Pill -> DesignPillTabs(
            items = items,
            selectedIndex = safeSelectedIndex,
            onSelectedIndexChange = onSelectedIndexChange,
            modifier = modifier,
            enabled = enabled,
        )
        DesignTabsVariant.Segmented -> DesignSegmentedTabs(
            items = items,
            selectedIndex = safeSelectedIndex,
            onSelectedIndexChange = onSelectedIndexChange,
            modifier = modifier,
            enabled = enabled,
        )
        DesignTabsVariant.Filled -> DesignFilledTabs(
            items = items,
            selectedIndex = safeSelectedIndex,
            onSelectedIndexChange = onSelectedIndexChange,
            modifier = modifier,
            enabled = enabled,
        )
    }
}

@Composable
private fun DesignFilledTabs(
    items: List<DesignTabItem>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .border(1.dp, MiuixTheme.colorScheme.outline, shape)
            .background(MiuixTheme.colorScheme.surface)
            .padding(4.dp)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val itemEnabled = enabled && item.enabled
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected) MiuixTheme.colorScheme.primary else Color.Transparent,
                    )
                    .selectable(
                        selected = selected,
                        enabled = itemEnabled,
                        role = Role.Tab,
                        onClick = { onSelectedIndexChange(index) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                DesignTabLabel(
                    item = item,
                    selected = selected,
                    enabled = itemEnabled,
                    compact = true,
                    selectedContentColor = MiuixTheme.colorScheme.onPrimary,
                    labelStyle = MiuixTheme.textStyles.footnote1,
                )
            }
        }
    }
}

@Composable
private fun DesignLineTabs(
    items: List<DesignTabItem>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
) {
    val motion = DesignTokens.motion

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
    ) {
        val tabWidth = maxWidth / items.size.toFloat()
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * selectedIndex.toFloat(),
            animationSpec = tween(durationMillis = motion.fastMillis),
            label = "designTabsLineIndicator",
        )

        Column {
            Row(modifier = Modifier.fillMaxWidth()) {
                items.forEachIndexed { index, item ->
                    DesignLineTabItem(
                        item = item,
                        selected = index == selectedIndex,
                        enabled = enabled && item.enabled,
                        onClick = { onSelectedIndexChange(index) },
                        modifier = Modifier
                            .weight(1f)
                            .height(DesignTokens.adaptive.minimumTouchTarget),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .padding(horizontal = 18.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(DesignTokens.shapes.full))
                        .background(tabIndicatorBrush(enabled)),
                )
            }
        }
    }
}

@Composable
private fun DesignPillTabs(
    items: List<DesignTabItem>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(DesignTokens.spacing.xs),
    ) {
        items.forEachIndexed { index, item ->
            DesignPillTabItem(
                item = item,
                selected = index == selectedIndex,
                enabled = enabled && item.enabled,
                onClick = { onSelectedIndexChange(index) },
            )
        }
    }
}

@Composable
private fun DesignSegmentedTabs(
    items: List<DesignTabItem>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
) {
    val shape = RoundedCornerShape(DesignTokens.shapes.full)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(DesignTokens.adaptive.minimumTouchTarget + 8.dp)
            .clip(shape)
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MiuixTheme.colorScheme.outline, shape)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEachIndexed { index, item ->
            DesignSegmentedTabItem(
                item = item,
                selected = index == selectedIndex,
                enabled = enabled && item.enabled,
                onClick = { onSelectedIndexChange(index) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DesignLineTabItem(
    item: DesignTabItem,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.selectable(
            selected = selected,
            enabled = enabled,
            role = Role.Tab,
            onClick = onClick,
        ),
        contentAlignment = Alignment.Center,
    ) {
        DesignTabLabel(
            item = item,
            selected = selected,
            enabled = enabled,
            compact = false,
        )
    }
}

@Composable
private fun DesignPillTabItem(
    item: DesignTabItem,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(DesignTokens.shapes.full)
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MiuixTheme.colorScheme.primary
        } else {
            MiuixTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(durationMillis = DesignTokens.motion.fastMillis),
        label = "designTabsPillContainer",
    )
    val borderColor by animateColorAsState(
        targetValue = Color.Transparent,
        animationSpec = tween(durationMillis = DesignTokens.motion.fastMillis),
        label = "designTabsPillBorder",
    )

    Box(
        modifier = Modifier
            .heightIn(min = DesignTokens.adaptive.minimumTouchTarget)
            .widthIn(min = 48.dp)
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        DesignTabLabel(
            item = item,
            selected = selected,
            enabled = enabled,
            compact = true,
            selectedContentColor = MiuixTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun DesignSegmentedTabItem(
    item: DesignTabItem,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(DesignTokens.shapes.full)
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MiuixTheme.colorScheme.tertiaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = DesignTokens.motion.fastMillis),
        label = "designTabsSegmentContainer",
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(shape)
            .background(containerColor)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        DesignTabLabel(
            item = item,
            selected = selected,
            enabled = enabled,
            compact = true,
        )
    }
}

@Composable
private fun DesignTabLabel(
    item: DesignTabItem,
    selected: Boolean,
    enabled: Boolean,
    compact: Boolean,
    selectedContentColor: Color? = null,
    labelStyle: TextStyle? = null,
) {
    val contentColor = if (enabled && selected && selectedContentColor != null) {
        selectedContentColor
    } else {
        tabContentColor(selected = selected, enabled = enabled)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.label,
            color = contentColor,
            style = labelStyle ?: if (compact) {
                MiuixTheme.textStyles.body2
            } else {
                MiuixTheme.textStyles.title4
            },
            fontWeight = if (compact || selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.badge != null) {
            Text(
                text = item.badge,
                color = contentColor,
                style = MiuixTheme.textStyles.footnote2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun tabContentColor(
    selected: Boolean,
    enabled: Boolean,
): Color {
    return when {
        !enabled -> MiuixTheme.colorScheme.disabledOnSurface
        selected -> MiuixTheme.colorScheme.primary
        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
}

@Composable
private fun tabIndicatorBrush(enabled: Boolean): Brush {
    val colors = if (enabled) {
        listOf(MiuixTheme.colorScheme.primary, MiuixTheme.colorScheme.secondary)
    } else {
        listOf(
            MiuixTheme.colorScheme.disabledPrimarySlider,
            MiuixTheme.colorScheme.disabledPrimarySlider,
        )
    }
    return Brush.horizontalGradient(colors)
}
