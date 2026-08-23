package io.github.julystar.musicapp.widgets.appbar

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.julystar.musicapp.core.presentation.layout.WindowSizeClass
import io.github.julystar.musicapp.navigation.HomeTab
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AppNavigationTest {
    @Test
    fun collapsedRailExposesSelectionAndSelectsTabs() = runComposeUiTest {
        var selected = HomeTab.HOME
        var homeLabel = ""
        var searchLabel = ""
        setContent {
            MiuixTheme {
                homeLabel = stringResource(HomeTab.HOME.labelRes)
                searchLabel = stringResource(HomeTab.SEARCH.labelRes)
                NavigationRailBar(
                    currentTab = selected,
                    onTabSelected = { selected = it },
                    windowSizeClass = WindowSizeClass.Medium,
                )
            }
        }

        onNode(tabWithDescription(homeLabel)).assertIsSelected()
        onNode(tabWithDescription(searchLabel)).assertIsNotSelected().performClick()
        assertEquals(HomeTab.SEARCH, selected)
    }

    @Test
    fun expandedSidebarExposesSelectionAndSelectsTabs() = runComposeUiTest {
        var selected = HomeTab.LIBRARY
        var libraryLabel = ""
        var settingsLabel = ""
        setContent {
            MiuixTheme {
                libraryLabel = stringResource(HomeTab.LIBRARY.labelRes)
                settingsLabel = stringResource(HomeTab.SETTINGS.labelRes)
                SidebarBar(
                    currentTab = selected,
                    onTabSelected = { selected = it },
                    windowSizeClass = WindowSizeClass.Large,
                )
            }
        }

        onNode(tabWithDescription(libraryLabel)).assertIsSelected()
        onNode(tabWithDescription(settingsLabel)).assertIsNotSelected().performClick()
        assertEquals(HomeTab.SETTINGS, selected)
    }

    private fun tabWithDescription(description: String) =
        SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, Role.Tab) and
            SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.ContentDescription,
                listOf(description),
            )
}
