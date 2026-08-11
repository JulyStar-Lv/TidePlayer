package io.github.julystar.musicapp.feature.downloads.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.julystar.musicapp.core.domain.repository.ToastRepository
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DownloadsRoot(
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = koinViewModel(),
    toastRepository: ToastRepository = koinInject(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DownloadsEvent.ShowMessage -> toastRepository.emit(event.message)
            }
        }
    }

    DownloadsScreen(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}
