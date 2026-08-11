package io.github.julystar.musicapp.feature.recentlyplayed.presentation

import io.github.julystar.musicapp.core.domain.repository.UiMessage

sealed interface RecentlyPlayedEvent {
    data class ShowMessage(val message: UiMessage) : RecentlyPlayedEvent
}
