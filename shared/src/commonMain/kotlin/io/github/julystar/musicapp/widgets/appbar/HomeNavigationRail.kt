package io.github.julystar.musicapp.widgets.appbar

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.components.appIconPainter
import io.github.julystar.musicapp.core.presentation.platform.LocalDesktopTitleBarInset
import io.github.julystar.musicapp.navigation.HomeTab
import musicapp.shared.generated.resources.Res
import musicapp.shared.generated.resources.app_name
import musicapp.shared.generated.resources.sidebar_tagline
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailDefaults
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.theme.MiuixTheme

fun getHomeNavigationRailWidth(expanded: Boolean): Dp = if (expanded) {
    NavigationRailDefaults.ExpandedWidth
} else {
    NavigationRailDefaults.MinWidth
}

@Composable
fun HomeNavigationRail(
    currentTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val state = rememberNavigationRailState()
    val titleBarInset = LocalDesktopTitleBarInset.current
    val appName = stringResource(Res.string.app_name)
    val tagline = stringResource(Res.string.sidebar_tagline)

    LaunchedEffect(expanded) {
        if (expanded) state.expand() else state.collapse()
    }

    NavigationRail(
        modifier = modifier,
        state = state,
        showDivider = true,
        defaultWindowInsetsPadding = false,
        header = {
            Column(modifier = Modifier.padding(top = titleBarInset + 12.dp)) {
                Image(
                    painter = appIconPainter(),
                    contentDescription = appName,
                    modifier = Modifier.size(32.dp),
                )
                if (expanded) {
                    Text(
                        text = appName,
                        color = MiuixTheme.colorScheme.onSurface,
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = tagline,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote2,
                    )
                }
            }
        },
    ) {
        HomeTab.entries.forEach { tab ->
            NavigationRailItem(
                selected = currentTab == tab,
                onClick = { onTabSelected(tab) },
                icon = vectorResource(tab.painterRes),
                label = stringResource(tab.labelRes),
            )
        }
    }
}
