package io.github.julystar.musicapp.core.data

import io.github.julystar.musicapp.core.domain.repository.UiMessage
import io.github.julystar.musicapp.core.domain.repository.UiMessageKey
import io.github.julystar.musicapp.core.domain.repository.emit
import io.github.julystar.musicapp.core.presentation.overlay.ToastVM
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ToastRepositoryTest {
    @Test
    fun `messages emitted before collection remain queued and ordered`() = runTest {
        val repository = ToastRepositoryImpl(this)
        repository.emit(UiMessage.Text("first"))
        repository.emit(UiMessageKey.LibraryImportCompleted, "2", "1", "0")

        assertEquals(
            listOf(
                UiMessage.Text("first"),
                UiMessage.Resource(
                    UiMessageKey.LibraryImportCompleted,
                    args = listOf("2", "1", "0"),
                ),
            ),
            repository.messages.take(2).toList(),
        )
    }

    @Test
    fun `toast view model forwards the shared message stream`() = runTest {
        val repository = ToastRepositoryImpl(this)
        val viewModel = ToastVM(repository)
        repository.emit(UiMessageKey.AddedToDownloads)

        assertEquals(
            listOf(UiMessage.Resource(UiMessageKey.AddedToDownloads)),
            viewModel.messages.take(1).toList(),
        )
    }
}
