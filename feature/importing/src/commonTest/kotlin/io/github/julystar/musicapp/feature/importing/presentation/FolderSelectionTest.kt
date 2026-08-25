package io.github.julystar.musicapp.feature.importing.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FolderSelectionTest {
    @Test
    fun computesAllFourStatesAcrossAncestors() {
        val roots = listOf("/music/lossless/chinese", "/music/OST")

        assertEquals(FolderSelectionState.Unselected, folderSelectionState("/video", roots))
        assertEquals(FolderSelectionState.PartiallySelected, folderSelectionState("/music", roots))
        assertEquals(FolderSelectionState.PartiallySelected, folderSelectionState("/music/lossless", roots))
        assertEquals(FolderSelectionState.Selected, folderSelectionState("/music/OST", roots))
        assertEquals(
            FolderSelectionState.InheritedSelected,
            folderSelectionState("/music/OST/soundtracks", roots),
        )
    }

    @Test
    fun selectingPartialParentReplacesItsDescendants() {
        val roots = selectFolderRoot(
            listOf("/music/Jay", "/music/JJ", "/video/OST"),
            "/music",
        )

        assertEquals(listOf("/video/OST", "/music"), roots)
    }

    @Test
    fun deselectingLastDescendantClearsPartialState() {
        val roots = deselectFolderRoot(listOf("/music/Jay"), "/music/Jay")
        assertEquals(FolderSelectionState.Unselected, folderSelectionState("/music", roots))
    }

    @Test
    fun pathComparisonUsesSegmentBoundaries() {
        assertTrue(isFolderAncestor("/music", "/music/Jay"))
        assertFalse(isFolderAncestor("/music", "/music-old"))
        assertFalse(isFolderAncestor("content://docs/tree/music", "content://other/tree/music/Jay"))
    }

    @Test
    fun selectingAndDeselectingCurrentLevelPreservesOtherBranches() {
        val selected = selectCurrentFolderLevel(
            selectedRoots = listOf("/music/Jay", "/elsewhere/deep"),
            directChildren = listOf("/music", "/video"),
        )
        assertEquals(setOf("/music", "/video", "/elsewhere/deep"), selected.toSet())
        assertTrue(isCurrentFolderLevelSelected(selected, listOf("/music", "/video")))

        val deselected = deselectCurrentFolderLevel(
            selectedRoots = selected,
            directChildren = listOf("/music", "/video"),
        )
        assertEquals(listOf("/elsewhere/deep"), deselected)
    }

    @Test
    fun inheritedChildrenCountAsSelectedButCannotCreateDuplicateRoots() {
        val roots = selectCurrentFolderLevel(
            selectedRoots = listOf("/music"),
            directChildren = listOf("/music/Jay", "/music/JJ"),
        )
        assertEquals(listOf("/music"), roots)
        assertTrue(isCurrentFolderLevelSelected(roots, listOf("/music/Jay", "/music/JJ")))
    }

    @Test
    fun deselectingInheritedChildrenRemovesTheCoveringAncestorRoot() {
        val roots = deselectCurrentFolderLevel(
            selectedRoots = listOf("/music", "/elsewhere"),
            directChildren = listOf("/music/Jay", "/music/JJ"),
        )

        assertEquals(listOf("/elsewhere"), roots)
        assertFalse(isCurrentFolderLevelSelected(roots, listOf("/music/Jay", "/music/JJ")))
    }
}
