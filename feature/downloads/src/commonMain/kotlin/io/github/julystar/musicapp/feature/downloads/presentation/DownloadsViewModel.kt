package io.github.julystar.musicapp.feature.downloads.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.julystar.musicapp.service.download.domain.DownloadController
import io.github.julystar.musicapp.core.domain.repository.UiMessage
import io.github.julystar.musicapp.core.domain.repository.UiMessageKey
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DownloadsViewModel(
    private val downloadController: DownloadController,
    private val coroutineScopeOverride: CoroutineScope? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(DownloadsState())
    private val _events = Channel<DownloadsEvent>(Channel.BUFFERED)
    private val coroutineScope: CoroutineScope
        get() = coroutineScopeOverride ?: viewModelScope

    val state = _state.asStateFlow()
    val events = _events.receiveAsFlow()

    init {
        coroutineScope.launch {
            downloadController.tasks.collect { tasks ->
                _state.update {
                    DownloadsState(
                        tasks = tasks.map { task -> task.toDownloadTaskUi() }.toPersistentList(),
                    )
                }
            }
        }
    }

    fun onAction(action: DownloadsAction) {
        when (action) {
            is DownloadsAction.Pause -> launchCommand { downloadController.pause(action.id) }
            is DownloadsAction.Resume -> launchCommand { downloadController.resume(action.id) }
            is DownloadsAction.Retry -> launchCommand { downloadController.retry(action.id) }
            is DownloadsAction.Cancel -> launchCommand { downloadController.cancel(action.id) }
        }
    }

    private fun launchCommand(command: suspend () -> Unit) {
        coroutineScope.launch {
            try {
                command()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                _events.send(
                    DownloadsEvent.ShowMessage(UiMessage.Resource(UiMessageKey.DownloadActionFailed))
                )
            }
        }
    }
}
