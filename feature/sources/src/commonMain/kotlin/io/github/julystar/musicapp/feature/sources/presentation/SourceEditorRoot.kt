package io.github.julystar.musicapp.feature.sources.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SourceEditorRoot(
    onNavigateBack: () -> Unit,
    onNavigateToLibraryFolderImport: (SourceAccountId) -> Unit,
    viewModel: EditStorageVM = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            dispatchSourceEditorNavigation(
                event = event,
                onNavigateBack = onNavigateBack,
                onNavigateToLibraryFolderImport = onNavigateToLibraryFolderImport,
                onOpenUri = uriHandler::openUri,
            )
        }
    }

    SourceEditorScreen(
        state = state,
        onAction = viewModel::onAction,
    )
}

internal fun dispatchSourceEditorNavigation(
    event: SourceEditorEvent,
    onNavigateBack: () -> Unit,
    onNavigateToLibraryFolderImport: (SourceAccountId) -> Unit,
    onOpenUri: (String) -> Unit,
) {
    when (event) {
        SourceEditorEvent.NavigateBack -> onNavigateBack()
        is SourceEditorEvent.OpenLibraryFolderImport -> {
            onNavigateToLibraryFolderImport(event.accountId)
        }
        is SourceEditorEvent.OpenOneDriveOAuth -> onOpenUri(event.authorizationUrl)
    }
}
