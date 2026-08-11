package io.github.julystar.musicapp.core.presentation.overlay

import androidx.lifecycle.ViewModel
import io.github.julystar.musicapp.core.domain.repository.ToastRepository

class ToastVM(
    toastRepository: ToastRepository,
) : ViewModel() {
    val messages = toastRepository.messages
}
