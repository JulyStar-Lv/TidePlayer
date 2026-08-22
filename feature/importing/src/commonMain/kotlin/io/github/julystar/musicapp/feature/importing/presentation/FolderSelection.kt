package io.github.julystar.musicapp.feature.importing.presentation

/** The four states used by the shared local/WebDAV/SMB folder picker. */
enum class FolderSelectionState {
    Unselected,
    Selected,
    PartiallySelected,
    InheritedSelected,
}

/**
 * Provider paths are compared by complete segments. This deliberately avoids prefix checks, so
 * `/music` does not contain `/music-old`. Encoded URI separators remain part of their segment.
 */
internal fun folderPathSegments(path: String): List<String> =
    path.trim().trimEnd('/').split('/').filter(String::isNotEmpty)

internal fun isFolderAncestor(ancestor: String, descendant: String): Boolean {
    val ancestorSegments = folderPathSegments(ancestor)
    val descendantSegments = folderPathSegments(descendant)
    return ancestorSegments.size < descendantSegments.size &&
        descendantSegments.take(ancestorSegments.size) == ancestorSegments
}

internal fun folderSelectionState(
    path: String,
    selectedRoots: Collection<String>,
): FolderSelectionState = when {
    path in selectedRoots -> FolderSelectionState.Selected
    selectedRoots.any { root -> isFolderAncestor(root, path) } ->
        FolderSelectionState.InheritedSelected
    selectedRoots.any { root -> isFolderAncestor(path, root) } ->
        FolderSelectionState.PartiallySelected
    else -> FolderSelectionState.Unselected
}

/** Removes duplicates and descendants already covered by an ancestor root. */
internal fun normalizeFolderRoots(paths: Collection<String>): List<String> {
    val distinct = paths.distinct()
    return distinct.filter { candidate ->
        distinct.none { other -> other != candidate && isFolderAncestor(other, candidate) }
    }
}

internal fun selectFolderRoot(
    selectedRoots: Collection<String>,
    path: String,
): List<String> {
    if (selectedRoots.any { root -> root == path || isFolderAncestor(root, path) }) {
        return normalizeFolderRoots(selectedRoots)
    }
    return normalizeFolderRoots(selectedRoots.filterNot { root -> isFolderAncestor(path, root) } + path)
}

internal fun deselectFolderRoot(
    selectedRoots: Collection<String>,
    path: String,
): List<String> = normalizeFolderRoots(selectedRoots.filterNot { it == path })

internal fun selectCurrentFolderLevel(
    selectedRoots: Collection<String>,
    directChildren: Collection<String>,
): List<String> = directChildren.fold(normalizeFolderRoots(selectedRoots)) { roots, child ->
    selectFolderRoot(roots, child)
}

/** Removes only roots represented by this level; deeper choices in partially selected branches remain. */
internal fun deselectCurrentFolderLevel(
    selectedRoots: Collection<String>,
    directChildren: Collection<String>,
): List<String> {
    val children = directChildren.toSet()
    return normalizeFolderRoots(selectedRoots.filterNot(children::contains))
}

internal fun isCurrentFolderLevelSelected(
    selectedRoots: Collection<String>,
    directChildren: Collection<String>,
): Boolean = directChildren.isNotEmpty() && directChildren.all { child ->
    folderSelectionState(child, selectedRoots) in setOf(
        FolderSelectionState.Selected,
        FolderSelectionState.InheritedSelected,
    )
}
