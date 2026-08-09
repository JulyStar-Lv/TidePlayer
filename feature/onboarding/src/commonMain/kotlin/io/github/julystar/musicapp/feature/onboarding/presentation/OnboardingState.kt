package io.github.julystar.musicapp.feature.onboarding.presentation

import androidx.compose.runtime.Immutable

@Immutable
data class OnboardingState(
    val currentPage: Int = 0,
    val isComplete: Boolean = false,
)

enum class OnboardingPage(val index: Int, val title: String, val description: String) {
    Welcome(0, "Welcome to TidePlayer", "Your personal music library, synced across all your devices."),
    AddSources(1, "Add Music Sources", "Connect your WebDAV server, OneDrive, or local folders to start building your library."),
    Ready(2, "You're All Set", "Your music is ready. Start exploring your library or add more sources anytime from Settings."),
}
