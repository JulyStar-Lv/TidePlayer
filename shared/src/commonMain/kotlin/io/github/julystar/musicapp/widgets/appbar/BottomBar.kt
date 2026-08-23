package io.github.julystar.musicapp.widgets.appbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.components.getBottomBarSpace
import io.github.julystar.musicapp.navigation.HomeTab
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem

@Composable
fun BottomBarSpacer(
    showMiniPlayer: Boolean,
    scaffoldPadding: PaddingValues,
) {
    Box(modifier = Modifier.height(getBottomBarSpace(showMiniPlayer, scaffoldPadding)))
}

@Composable
fun BoxScope.BottomBar(
    currentTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    miniPlayerContent: @Composable () -> Unit,
    showMiniPlayer: Boolean,
    showChrome: Boolean,
    scaffoldPadding: PaddingValues,
) {
    if (!showChrome) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(scaffoldPadding.calculateBottomPadding()),
        )
        return
    }

    Column(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth(),
    ) {
        if (showMiniPlayer) {
            Box(modifier = Modifier.padding(start = 12.dp, top = 0.dp, end = 12.dp, bottom = 8.dp)) {
                miniPlayerContent()
            }
        }
        NavigationBar(
            modifier = Modifier.fillMaxWidth(),
            defaultWindowInsetsPadding = false,
        ) {
            HomeTab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = currentTab == tab,
                    onClick = { onTabSelected(tab) },
                    icon = vectorResource(tab.painterRes),
                    label = stringResource(tab.labelRes),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(scaffoldPadding.calculateBottomPadding()),
        )
    }
}
