package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import musicapp.core.presentation.generated.resources.Res
import musicapp.core.presentation.generated.resources.app_name
import musicapp.core.presentation.generated.resources.dashboard_import_cancel
import musicapp.core.presentation.generated.resources.icon_info
import musicapp.core.presentation.generated.resources.icon_search
import musicapp.core.presentation.generated.resources.icon_warning
import musicapp.core.presentation.generated.resources.search_title
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class UiWrapperTest {

    @Test
    fun switchPreferenceHandlesRowClickAndExposesSummary() = runComposeUiTest {
        var checked = false
        setContent {
            MiuixTheme {
                AppSwitchPreference(
                    title = "Switch title",
                    summary = "Switch summary",
                    checked = checked,
                    onCheckedChange = { checked = it },
                )
            }
        }

        onNodeWithText("Switch summary").assertExists()
        onNodeWithText("Switch title").performClick()
        assertTrue(checked)
    }

    @Test
    fun disabledSwitchPreferenceDoesNotChange() = runComposeUiTest {
        var checked = false
        setContent {
            MiuixTheme {
                AppSwitchPreference(
                    title = "Disabled switch",
                    checked = checked,
                    enabled = false,
                    onCheckedChange = { checked = it },
                    modifier = Modifier.testTag("disabled-switch"),
                )
            }
        }

        onNodeWithTag("disabled-switch").performClick()
        assertFalse(checked)
    }

    @Test
    fun sliderPreferenceUpdatesValue() = runComposeUiTest {
        var value by mutableStateOf(1f)
        setContent {
            MiuixTheme {
                AppSliderPreference(
                    title = "Slider title",
                    summary = "Slider summary",
                    value = value,
                    valueText = value.toInt().toString(),
                    valueRange = 0f..4f,
                    steps = 3,
                    onValueChange = { value = it },
                )
            }
        }

        onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(1f, 0f..4f, 3)))
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(3f)
            }
        assertEquals(3f, value)
    }

    @Test
    fun dropdownPreferenceSelectsAnOption() = runComposeUiTest {
        var selectedIndex by mutableStateOf(0)
        setContent {
            MiuixTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) {
                    AppDropdownPreference(
                        title = "Quality",
                        items = listOf("Low", "High"),
                        selectedIndex = selectedIndex,
                        onSelectedIndexChange = { selectedIndex = it },
                    )
                }
            }
        }

        onNodeWithText("Quality").performClick()
        waitForIdle()
        onNode(
            hasText("Low") and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton),
        ).assertIsSelected()
        onNodeWithText("High").performClick()
        assertEquals(1, selectedIndex)
    }

    @Test
    fun designPreferenceCompatibilityRowExposesSummaryAndHandlesClick() = runComposeUiTest {
        var clicked = false
        setContent {
            MiuixTheme {
                DesignPreferenceRow(
                    title = "Compatibility preference",
                    summary = "Compatibility summary",
                    onClick = { clicked = true },
                )
            }
        }

        onNodeWithText("Compatibility summary").assertExists()
        onNodeWithText("Compatibility preference").performClick()
        assertTrue(clicked)
    }

    @Test
    fun contextMenuOpensDismissesAndInvokesRegularAndDangerActions() = runComposeUiTest {
        var expanded by mutableStateOf(false)
        var regularInvoked = false
        var dangerInvoked = false
        var appLabel = ""
        var disabledLabel = ""
        var dangerLabel = ""
        setContent {
            MiuixTheme {
                appLabel = stringResource(Res.string.app_name)
                disabledLabel = stringResource(Res.string.search_title)
                dangerLabel = stringResource(Res.string.dashboard_import_cancel)
                Scaffold(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("menu-trigger")
                            .clickable { expanded = true },
                    ) {
                        DesignContextMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            items = listOf(
                                DesignContextMenuItem(
                                    label = Res.string.search_title,
                                    icon = Res.drawable.icon_search,
                                    enabled = false,
                                    onClick = { error("Disabled item invoked") },
                                ),
                                DesignContextMenuItem(
                                    label = Res.string.app_name,
                                    icon = Res.drawable.icon_info,
                                    onClick = { regularInvoked = true },
                                ),
                                DesignContextMenuItem(
                                    label = Res.string.dashboard_import_cancel,
                                    icon = Res.drawable.icon_warning,
                                    onClick = { dangerInvoked = true },
                                    isError = true,
                                ),
                            ),
                        )
                    }
                }
            }
        }

        onAllNodesWithText(appLabel).assertCountEquals(0)
        onNodeWithTag("menu-trigger").performClick()
        waitForIdle()
        onNodeWithText(disabledLabel).assertIsNotEnabled()
        onNodeWithText(appLabel).performClick()
        assertTrue(regularInvoked)
        assertFalse(expanded)

        onNodeWithTag("menu-trigger").performClick()
        waitForIdle()
        onNodeWithText(dangerLabel).performClick()
        assertTrue(dangerInvoked)
        assertFalse(expanded)
    }

    @Test
    fun contextMenuOpensAndSelectsASubmenuItem() = runComposeUiTest {
        var expanded by mutableStateOf(false)
        var childInvoked = false
        var parentLabel = ""
        var childLabel = ""
        setContent {
            MiuixTheme {
                parentLabel = stringResource(Res.string.app_name)
                childLabel = stringResource(Res.string.search_title)
                Scaffold(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("submenu-trigger")
                            .clickable { expanded = true },
                    ) {
                        DesignContextMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            items = listOf(
                                DesignContextMenuItem(
                                    label = Res.string.app_name,
                                    icon = Res.drawable.icon_info,
                                    onClick = {},
                                    children = listOf(
                                        DesignContextMenuItem(
                                            label = Res.string.search_title,
                                            icon = Res.drawable.icon_search,
                                            onClick = { childInvoked = true },
                                        ),
                                    ),
                                ),
                            ),
                        )
                    }
                }
            }
        }

        onNodeWithTag("submenu-trigger").performClick()
        waitForIdle()
        onNodeWithText(parentLabel).performClick()
        waitForIdle()
        onAllNodesWithText(childLabel)[0].performClick()
        assertTrue(childInvoked)
        assertFalse(expanded)
    }

    @Test
    fun searchSupportsInputSubmitClearAndDisabledState() = runComposeUiTest {
        var value by mutableStateOf("")
        var submitted = false
        var enabled by mutableStateOf(true)
        setContent {
            MiuixTheme {
                DesignSearchBar(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = "Search library",
                    onSearch = { submitted = true },
                    enabled = enabled,
                    onClear = { value = "" },
                )
            }
        }

        onNode(hasSetTextAction()).performTextInput("Tide")
        assertEquals("Tide", value)
        onNode(hasSetTextAction()).performImeAction()
        assertTrue(submitted)
        waitForIdle()
        onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button),
            useUnmergedTree = true,
        ).performClick()
        assertEquals("", value)

        enabled = false
        waitForIdle()
        onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test
    fun snackbarStateQueuesAndDismissesMessagesInOrder() = runTest {
        val state = AppSnackbarHostState()
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            state.showMessage("first", duration = AppSnackbarDuration.Indefinite)
        }
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            state.showMessage("second", duration = AppSnackbarDuration.Indefinite)
        }

        state.dismissOldest()
        assertEquals(AppSnackbarResult.Dismissed, first.await())
        assertFalse(second.isCompleted)
        state.dismissOldest()
        assertEquals(AppSnackbarResult.Dismissed, second.await())
    }
}
