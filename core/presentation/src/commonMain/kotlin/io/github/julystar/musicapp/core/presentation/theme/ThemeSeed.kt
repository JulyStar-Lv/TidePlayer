package io.github.julystar.musicapp.core.presentation.theme

import androidx.compose.runtime.Immutable
import io.github.julystar.musicapp.core.domain.model.DEFAULT_MANUAL_THEME_SEED_ARGB
import io.github.julystar.musicapp.core.domain.model.DEFAULT_DARK_MANUAL_THEME_SEED_ARGB

@Immutable
data class ThemeSeedState(
    val manualSeedArgb: Long,
    val darkManualSeedArgb: Long = DEFAULT_DARK_MANUAL_THEME_SEED_ARGB,
) {
    companion object {
        val Default = ThemeSeedState(
            manualSeedArgb = DEFAULT_MANUAL_THEME_SEED_ARGB,
            darkManualSeedArgb = DEFAULT_DARK_MANUAL_THEME_SEED_ARGB,
        )
    }
}
