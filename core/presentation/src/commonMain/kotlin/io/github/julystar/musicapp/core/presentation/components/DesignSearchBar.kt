package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import musicapp.core.presentation.generated.resources.Res
import musicapp.core.presentation.generated.resources.icon_search
import musicapp.core.presentation.generated.resources.search_clear
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DesignSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClear: (() -> Unit)? = null,
) {
    InputField(
        query = value,
        onQueryChange = onValueChange,
        onSearch = { onSearch() },
        expanded = false,
        onExpandedChange = {},
        label = placeholder,
        enabled = enabled,
        leadingIcon = {
            Icon(
                painter = painterResource(Res.drawable.icon_search),
                tint = MiuixTheme.colorScheme.onSurfaceContainerHigh,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 16.dp, end = 8.dp)
                    .size(16.dp),
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                val clearDescription = stringResource(Res.string.search_clear)
                val clearSearch = { onClear?.invoke() ?: onValueChange("") }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(enabled = enabled, onClick = clearSearch)
                        .clearAndSetSemantics {
                            contentDescription = clearDescription
                            role = Role.Button
                            if (!enabled) disabled()
                            onClick {
                                if (enabled) clearSearch()
                                enabled
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "×",
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        style = MiuixTheme.textStyles.body2,
                    )
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}
