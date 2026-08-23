package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.overlay.OverlayCascadingListPopup
import top.yukonga.miuix.kmp.popup.OverlayDropdownPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class ResourceDropdownMenuItem(
    val label: StringResource,
    val icon: DrawableResource,
    val onClick: () -> Unit,
    val isError: Boolean = false,
    val enabled: Boolean = true,
    val children: List<ResourceDropdownMenuItem> = emptyList(),
) {
}

@Composable
fun ResourceDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<ResourceDropdownMenuItem>,
    compact: Boolean = false,
) {
    val entries = listOf(DropdownEntry(items = items.map { item -> item.toDropdownItem() }))
    val dropdownColors = DropdownDefaults.dropdownColors(
        containerColor = MiuixTheme.colorScheme.surfaceContainerHighest,
    )
    if (items.any { item -> item.children.isNotEmpty() }) {
        OverlayCascadingListPopup(
            entries = entries,
            show = expanded,
            onDismissRequest = onDismissRequest,
            onDismissFinished = {},
            maxHeight = if (compact) 360.dp else null,
            dropdownColors = dropdownColors,
            renderInRootScaffold = true,
        )
    } else {
        OverlayDropdownPopup(
            entry = entries.single(),
            show = expanded,
            onDismiss = onDismissRequest,
            onDismissFinished = {},
            maxHeight = if (compact) 360.dp else null,
            dropdownColors = dropdownColors,
            renderInRootScaffold = true,
        )
    }
}

@Composable
private fun ResourceDropdownMenuItem.toDropdownItem(): DropdownItem {
    val iconTint = if (isError) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurface
    return DropdownItem(
        text = stringResource(label),
        enabled = enabled,
        icon = { modifier ->
            Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp),
                )
            }
        },
        onClick = onClick,
        children = children.takeIf { it.isNotEmpty() }?.map { item -> item.toDropdownItem() },
    )
}
