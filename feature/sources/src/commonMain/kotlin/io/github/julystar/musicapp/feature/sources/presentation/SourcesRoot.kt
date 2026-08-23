package io.github.julystar.musicapp.feature.sources.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.julystar.musicapp.core.domain.model.toStorageRouteIdOrNull
import org.koin.compose.viewmodel.koinViewModel

private const val NEW_STORAGE_ID = -1L

@Composable
fun SourcesRoot(
    onNavigateToSourceEditor: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SourcesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            dispatchSourcesNavigation(event, onNavigateToSourceEditor)
        }
    }

    SourcesScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

internal fun dispatchSourcesNavigation(
    event: SourcesEvent,
    onNavigateToSourceEditor: (Long) -> Unit,
) {
    when (event) {
        SourcesEvent.OpenNewSourceEditor -> onNavigateToSourceEditor(NEW_STORAGE_ID)
        is SourcesEvent.OpenSourceEditor -> {
            event.id.toStorageRouteIdOrNull()?.let(onNavigateToSourceEditor)
        }
    }
}
