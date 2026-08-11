package io.github.julystar.musicapp.feature.radio.presentation

import io.github.julystar.musicapp.core.domain.repository.UiMessage

sealed interface RadioEvent {
    data class ShowMessage(val message: UiMessage) : RadioEvent
}
