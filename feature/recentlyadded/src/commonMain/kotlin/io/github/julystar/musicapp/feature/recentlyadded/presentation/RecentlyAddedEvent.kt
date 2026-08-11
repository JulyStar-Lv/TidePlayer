package io.github.julystar.musicapp.feature.recentlyadded.presentation

import io.github.julystar.musicapp.core.domain.repository.UiMessage

sealed interface RecentlyAddedEvent {
    data class ShowMessage(val message: UiMessage) : RecentlyAddedEvent
}
