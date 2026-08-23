package io.github.julystar.musicapp.plugin.management

import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.components.shouldDismissBottomSheet
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

    @Test
    fun dismissesCompactSheetAfterEnoughDistanceOrVelocity() {
        assertFalse(
            shouldDismissBottomSheet(
                dragOffsetPx = 71f,
                velocityPxPerSecond = 899f,
                distanceThresholdPx = 72f,
                velocityThresholdPxPerSecond = 900f,
            ),
        )
        assertTrue(
            shouldDismissBottomSheet(
                dragOffsetPx = 72f,
                velocityPxPerSecond = 0f,
                distanceThresholdPx = 72f,
                velocityThresholdPxPerSecond = 900f,
            ),
        )
        assertTrue(
            shouldDismissBottomSheet(
                dragOffsetPx = 12f,
                velocityPxPerSecond = 900f,
                distanceThresholdPx = 72f,
                velocityThresholdPxPerSecond = 900f,
            ),
        )
    }
}
