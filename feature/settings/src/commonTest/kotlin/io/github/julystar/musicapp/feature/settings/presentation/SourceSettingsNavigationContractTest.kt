package io.github.julystar.musicapp.feature.settings.presentation

import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.SourceEditorType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourceSettingsNavigationContractTest {
    @Test
    fun addAndExistingSourceUseTheSameEditorNavigationCallback() {
        val requests = mutableListOf<Pair<SourceAccountId?, SourceEditorType?>>()
        val navigate: (SourceAccountId?, SourceEditorType?) -> Unit = { accountId, type ->
            requests += accountId to type
        }
        val existing = SourceAccountId("storage:42")

        dispatchSourceSettingsNavigation(existing, navigate)

        assertEquals(existing, requests.single().first)
        assertNull(requests.single().second)
    }

    @Test
    fun sourcePickerContainsEachSourceOnceAndDispatchesLocalOrTypedEditor() {
        assertEquals(
            listOf(
                SourcePickerOption.Local,
                SourcePickerOption.WebDav,
                SourcePickerOption.Smb,
                SourcePickerOption.OneDrive,
                SourcePickerOption.OpenList,
                SourcePickerOption.Navidrome,
                SourcePickerOption.OpenSubsonic,
                SourcePickerOption.Emby,
            ),
            sourcePickerOptions,
        )
        assertEquals(sourcePickerOptions.size, sourcePickerOptions.toSet().size)

        var localOpened = false
        val requests = mutableListOf<Pair<SourceAccountId?, SourceEditorType?>>()
        val navigate: (SourceAccountId?, SourceEditorType?) -> Unit = { accountId, type ->
            requests += accountId to type
        }
        dispatchNewSourceSelection(SourcePickerOption.Local, { localOpened = true }, navigate)
        dispatchNewSourceSelection(SourcePickerOption.OpenList, {}, navigate)

        assertTrue(localOpened)
        assertEquals(
            listOf<Pair<SourceAccountId?, SourceEditorType?>>(null to SourceEditorType.OpenList),
            requests,
        )
    }
}
