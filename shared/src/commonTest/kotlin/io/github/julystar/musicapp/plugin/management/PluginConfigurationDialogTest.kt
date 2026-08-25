package io.github.julystar.musicapp.plugin.management

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginConfigurationDialogTest {
    @Test
    fun usesBottomSheetOnlyForCompactWindows() {
        assertTrue(isCompactPluginConfigurationDialog(599.dp))
        assertFalse(isCompactPluginConfigurationDialog(600.dp))
        assertFalse(isCompactPluginConfigurationDialog(1_008.dp))
    }
}
