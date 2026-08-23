package io.github.julystar.musicapp.core.presentation.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.onNodeWithContentDescription
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
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult
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
                SwitchPreference(
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
                SwitchPreference(
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
                SliderPreference(
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
                    OverlayDropdownPreference(
                        title = "Quality",
                        entry = DropdownEntry(
                            items = listOf(
                                DropdownItem(text = "Low", selected = selectedIndex == 0, onClick = { selectedIndex = 0 }),
                                DropdownItem(text = "High", selected = selectedIndex == 1, onClick = { selectedIndex = 1 }),
                            ),
                        ),
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
    fun basicComponentExposesSummaryAndHandlesClick() = runComposeUiTest {
        var clicked = false
        setContent {
            MiuixTheme {
                BasicComponent(
                    title = "Basic component",
                    summary = "Basic summary",
                    onClick = { clicked = true },
                )
            }
        }

        onNodeWithText("Basic summary").assertExists()
        onNodeWithText("Basic component").performClick()
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
                        ResourceDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            items = listOf(
                                ResourceDropdownMenuItem(
                                    label = Res.string.search_title,
                                    icon = Res.drawable.icon_search,
                                    enabled = false,
                                    onClick = { error("Disabled item invoked") },
                                ),
                                ResourceDropdownMenuItem(
                                    label = Res.string.app_name,
                                    icon = Res.drawable.icon_info,
                                    onClick = { regularInvoked = true },
                                ),
                                ResourceDropdownMenuItem(
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
                        ResourceDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            items = listOf(
                                ResourceDropdownMenuItem(
                                    label = Res.string.app_name,
                                    icon = Res.drawable.icon_info,
                                    onClick = {},
                                    children = listOf(
                                        ResourceDropdownMenuItem(
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
                InputField(
                    query = value,
                    onQueryChange = { value = it },
                    onSearch = { submitted = true },
                    label = "Search library",
                    expanded = false,
                    onExpandedChange = {},
                    enabled = enabled,
                )
            }
        }

        onNode(hasSetTextAction()).performTextInput("Tide")
        assertEquals("Tide", value)
        onNode(hasSetTextAction()).performImeAction()
        assertTrue(submitted)
        waitForIdle()
        onNodeWithContentDescription("Search Cleanup", useUnmergedTree = true).performClick()
        assertEquals("", value)

        enabled = false
        waitForIdle()
        onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test
    fun snackbarStateQueuesAndDismissesMessagesInOrder() = runTest {
        val state = SnackbarHostState()
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            state.showSnackbar("first", duration = SnackbarDuration.Indefinite)
        }
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            state.showSnackbar("second", duration = SnackbarDuration.Indefinite)
        }

        state.oldestSnackbarData()?.dismiss()
        assertEquals(SnackbarResult.Dismissed, first.await())
        assertFalse(second.isCompleted)
        state.oldestSnackbarData()?.dismiss()
        assertEquals(SnackbarResult.Dismissed, second.await())
    }
}
