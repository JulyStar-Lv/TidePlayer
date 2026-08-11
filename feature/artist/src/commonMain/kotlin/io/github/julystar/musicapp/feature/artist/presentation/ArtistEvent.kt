package io.github.julystar.musicapp.feature.artist.presentation

import io.github.julystar.musicapp.core.domain.repository.UiMessage

sealed interface ArtistEvent {
    data class ShowMessage(val message: UiMessage) : ArtistEvent
}
