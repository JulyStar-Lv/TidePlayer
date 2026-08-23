package io.github.julystar.musicapp.widgets.appbar

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.julystar.musicapp.navigation.HomeTab
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AppNavigationTest {
    @Test
    fun collapsedRailExposesSelectionAndSelectsTabs() = runComposeUiTest {
        var selected = HomeTab.HOME
        setContent {
            MiuixTheme {
                HomeNavigationRail(
                    currentTab = selected,
                    onTabSelected = { selected = it },
                    expanded = false,
                )
            }
        }

        onAllNodes(tab()).get(HomeTab.HOME.index).assertIsSelected()
        onAllNodes(tab()).get(HomeTab.SEARCH.index).assertIsNotSelected().performClick()
        assertEquals(HomeTab.SEARCH, selected)
    }

    @Test
    fun expandedSidebarExposesSelectionAndSelectsTabs() = runComposeUiTest {
        var selected = HomeTab.LIBRARY
        setContent {
            MiuixTheme {
                HomeNavigationRail(
                    currentTab = selected,
                    onTabSelected = { selected = it },
                    expanded = true,
                )
            }
        }

        onAllNodes(tab()).get(HomeTab.LIBRARY.index).assertIsSelected()
        onAllNodes(tab()).get(HomeTab.SETTINGS.index).assertIsNotSelected().performClick()
        assertEquals(HomeTab.SETTINGS, selected)
    }

    private fun tab() =
        SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, Role.Tab)
}
