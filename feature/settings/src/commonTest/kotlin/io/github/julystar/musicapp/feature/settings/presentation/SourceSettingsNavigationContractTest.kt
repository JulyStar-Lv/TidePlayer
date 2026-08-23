package io.github.julystar.musicapp.feature.settings.presentation

import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SourceSettingsNavigationContractTest {
    @Test
    fun addAndExistingSourceUseTheSameEditorNavigationCallback() {
        val requests = mutableListOf<SourceAccountId?>()
        val navigate: (SourceAccountId?) -> Unit = requests::add
        val existing = SourceAccountId("storage:42")

        dispatchSourceSettingsNavigation(null, navigate)
        dispatchSourceSettingsNavigation(existing, navigate)

        assertNull(requests[0])
        assertEquals(existing, requests[1])
    }
}
