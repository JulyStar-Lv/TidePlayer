package io.github.julystar.musicapp.feature.sources.presentation.navigation

import io.github.julystar.musicapp.core.presentation.navigation.MusicGraph
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.feature.sources.presentation.SourceEditorRoot

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

fun NavGraphBuilder.sourcesGraph(
    onNavigateBack: () -> Unit,
    onNavigateToLibraryFolderImport: (SourceAccountId) -> Unit,
) {
    composable<MusicGraph.EditStorage> {
        SourceEditorRoot(
            onNavigateBack = onNavigateBack,
            onNavigateToLibraryFolderImport = onNavigateToLibraryFolderImport,
        )
    }
}
