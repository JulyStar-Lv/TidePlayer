package io.github.julystar.musicapp.feature.settings.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LocalDirectoryPickerResultTest {
    @Test
    fun nullResultMeansUserCancellation() {
        assertEquals(
            LocalDirectoryPickerResult.Cancelled,
            localDirectoryPickerResult(null) { it },
        )
    }

    @Test
    fun unsupportedResultMeansLaunchFailureNotCancellation() {
        assertIs<LocalDirectoryPickerResult.LaunchFailed>(
            localDirectoryPickerResult("content://missing") { null },
        )
    }

    @Test
    fun successfulResultKeepsNormalizedPath() {
        assertEquals(
            LocalDirectoryPickerResult.Success("/Music"),
            localDirectoryPickerResult("content://music") { "/Music" },
        )
    }
}
