package io.github.julystar.musicapp.widgets.appbar

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.navigation.HomeTab
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DesktopNavigationTest {
    @Test
    fun expandedSidebarUsesAppleMusicShellWithoutBranding() = runComposeUiTest {
        setContent {
            MiuixTheme {
                HomeNavigationRail(
                    currentTab = HomeTab.HOME,
                    onTabSelected = {},
                    expanded = true,
                )
            }
        }

        assertEquals(208.dp, getHomeNavigationRailWidth(expanded = true))
        onNodeWithTag("apple-music-sidebar-home").assertIsSelected()
        onAllNodesWithTag("apple-music-sidebar-radio").assertCountEquals(0)
        onAllNodesWithTag("apple-music-sidebar-itunes_store").assertCountEquals(0)
        onAllNodesWithText("Tide Player").assertCountEquals(0)
    }

    @Test
    fun expandedSidebarDispatchesAppleMusicDestination() = runComposeUiTest {
        var selectedDestination = AppleMusicSidebarDestination.HOME
        setContent {
            MiuixTheme {
                HomeNavigationRail(
                    currentTab = HomeTab.HOME,
                    onTabSelected = {},
                    expanded = true,
                    selectedDesktopDestination = selectedDestination,
                    onDesktopDestinationSelected = { selectedDestination = it },
                )
            }
        }

        onNodeWithTag("apple-music-sidebar-settings").performClick()
        assertEquals(AppleMusicSidebarDestination.SETTINGS, selectedDestination)
    }
}
