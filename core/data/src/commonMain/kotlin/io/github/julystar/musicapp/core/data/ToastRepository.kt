package io.github.julystar.musicapp.core.data

import io.github.julystar.musicapp.core.domain.repository.ToastRepository
import io.github.julystar.musicapp.core.domain.repository.UiMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class ToastRepositoryImpl(
    @Suppress("UNUSED_PARAMETER") scope: CoroutineScope,
) : ToastRepository {
    private val queue = Channel<UiMessage>(Channel.UNLIMITED)

    override val messages = queue.receiveAsFlow()

    override fun emit(message: UiMessage) {
        queue.trySend(message)
    }
}
