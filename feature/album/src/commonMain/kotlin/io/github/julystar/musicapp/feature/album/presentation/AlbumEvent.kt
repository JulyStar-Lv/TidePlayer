package io.github.julystar.musicapp.feature.album.presentation

import io.github.julystar.musicapp.core.domain.repository.UiMessage

sealed interface AlbumEvent {
    data class ShowMessage(val message: UiMessage) : AlbumEvent
}
