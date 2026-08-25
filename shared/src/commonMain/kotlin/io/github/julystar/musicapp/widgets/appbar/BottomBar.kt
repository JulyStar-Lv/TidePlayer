package io.github.julystar.musicapp.widgets.appbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.julystar.musicapp.core.presentation.components.getBottomBarSpace
import io.github.julystar.musicapp.core.presentation.components.liquidGlassSurface
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import io.github.julystar.musicapp.navigation.HomeTab
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
        HomeFloatingNavigationBar(
            currentTab = currentTab,
            onTabSelected = onTabSelected,
            bottomInset = scaffoldPadding.calculateBottomPadding(),
        )
    }
}

/**
 * The home chrome is a music-player surface, rather than a general navigation abstraction.
 * It intentionally keeps the MiniPlayer and bottom tabs in the same visual hierarchy.
 */
@Composable
private fun HomeFloatingNavigationBar(
    currentTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val surfaceModifier = Modifier.liquidGlassSurface(
        shape = RoundedCornerShape(0.dp),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(surfaceModifier),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DesignTokens.navigation.compactBarDividerHeight)
                .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DesignTokens.navigation.compactBarHeight)
                .padding(horizontal = 12.dp, vertical = 5.dp)
                .selectableGroup(),
        ) {
            HomeTab.entries.forEach { tab ->
                val selected = currentTab == tab
                val label = stringResource(tab.labelRes)
                val tint = if (selected) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurface
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(DesignTokens.shapes.lg))
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = { onTabSelected(tab) },
                        )
                        .clearAndSetSemantics {
                            contentDescription = label
                            role = Role.Tab
                            this.selected = selected
                            onClick { onTabSelected(tab); true }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(tab.painterRes),
                        tint = tint,
                        contentDescription = null,
                        modifier = Modifier.size(DesignTokens.navigation.compactIconSize),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = label,
                        color = tint,
                        style = MiuixTheme.textStyles.footnote2.copy(
                            fontSize = DesignTokens.navigation.compactLabelSize,
                            lineHeight = 12.sp,
                        ),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(bottomInset),
        )
    }
}
