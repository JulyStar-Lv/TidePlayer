package io.github.julystar.musicapp.feature.settings.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalRootNormalizationTest {
    @Test
    fun sequentialSelectionsAppendIndependentRoots() {
        assertEquals(
            listOf("/Music", "/QQMusic"),
            normalizeLocalRootPaths(listOf("/Music", "/QQMusic")),
        )
    }

    @Test
    fun parentSelectionReplacesChildrenButNotPrefixSibling() {
        assertEquals(
            setOf("/music", "/music-old"),
            normalizeLocalRootPaths(
                listOf("/music/Jay", "/music/JJ", "/music-old", "/music"),
            ).toSet(),
        )
    }
}
