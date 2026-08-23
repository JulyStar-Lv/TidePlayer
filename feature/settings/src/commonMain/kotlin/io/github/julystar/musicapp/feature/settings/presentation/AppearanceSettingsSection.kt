package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.domain.model.AppLanguageMode
import io.github.julystar.musicapp.core.domain.model.AppThemeMode
import io.github.julystar.musicapp.core.presentation.theme.ArtworkThemeSeedStatus
import io.github.julystar.musicapp.core.presentation.theme.LocalThemeSeedState
import io.github.julystar.musicapp.core.presentation.theme.canSelectManualThemeColor
import org.jetbrains.compose.resources.stringResource
import musicapp.feature.settings.generated.resources.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun AppearanceSettingsSection(
    state: SettingsUiState,
    onBack: (() -> Unit)?,
    onAction: (SettingsAction) -> Unit,
) {
    val settings = state.settings
    val themeSeedState = LocalThemeSeedState.current
    var colorPickerOpen by remember { mutableStateOf(false) }
    val manualThemeColorEnabled = canSelectManualThemeColor(settings.artworkThemeEnabled)
    LaunchedEffect(manualThemeColorEnabled) {
        if (!manualThemeColorEnabled) colorPickerOpen = false
    }

    SettingsPageLayout(title = stringResource(Res.string.settings_appearance_title), onBack = onBack) {
        SmallTitle(text = stringResource(Res.string.settings_theme_section))
        Card {
            val themeModes = AppThemeMode.entries.toList()
            OverlayDropdownPreference(
                title = stringResource(Res.string.settings_theme_section),
                summary = stringResource(settings.themeMode.summaryResource()),
                entries = listOf(DropdownEntry(
                    items = themeModes.map { mode ->
                        DropdownItem(
                            text = stringResource(mode.titleResource()),
                            selected = mode == settings.themeMode,
                            onClick = { onAction(SettingsAction.SetThemeMode(mode)) },
                        )
                    },
                )),
            )
        }

        SmallTitle(text = stringResource(Res.string.settings_color_section))
        Card {
            SwitchPreference(
                title = stringResource(Res.string.settings_artwork_color),
                summary = stringResource(Res.string.settings_artwork_color_summary),
                checked = settings.artworkThemeEnabled,
                onCheckedChange = { onAction(SettingsAction.SetArtworkThemeEnabled(it)) },
            )
            ArrowPreference(
                title = stringResource(Res.string.settings_theme_color),
                summary = if (settings.artworkThemeEnabled) {
                    val artworkSummary = when (themeSeedState.artworkStatus) {
                        ArtworkThemeSeedStatus.Available ->
                            stringResource(Res.string.settings_theme_color_artwork_active)
                        ArtworkThemeSeedStatus.Loading ->
                            stringResource(Res.string.settings_theme_color_artwork_loading)
                        ArtworkThemeSeedStatus.Failed ->
                            stringResource(Res.string.settings_theme_color_artwork_failed)
                        ArtworkThemeSeedStatus.Missing ->
                            stringResource(Res.string.settings_theme_color_artwork_missing)
                        else -> stringResource(
                            Res.string.settings_theme_color_current,
                            formatThemeSeedHex(settings.manualThemeSeedArgb),
                        )
                    }
                    "$artworkSummary · ${
                        stringResource(Res.string.settings_theme_color_edit_after_artwork_off)
                    }"
                } else {
                    stringResource(
                        Res.string.settings_theme_color_current,
                        formatThemeSeedHex(settings.manualThemeSeedArgb),
                    )
                },
                enabled = manualThemeColorEnabled,
                onClick = {
                    if (manualThemeColorEnabled) colorPickerOpen = true
                },
                endActions = {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(settings.manualThemeSeedArgb.toInt()))
                            .border(1.dp, MiuixTheme.colorScheme.outline, CircleShape),
                    )
                },
            )
        }

        SmallTitle(text = stringResource(Res.string.settings_language_section))
        Card {
            val languageModes = AppLanguageMode.entries.toList()
            OverlayDropdownPreference(
                title = stringResource(Res.string.settings_language_section),
                summary = stringResource(settings.languageMode.summaryResource()),
                entries = listOf(DropdownEntry(
                    items = languageModes.map { mode ->
                        DropdownItem(
                            text = stringResource(mode.titleResource()),
                            selected = mode == settings.languageMode,
                            onClick = { onAction(SettingsAction.SetLanguageMode(mode)) },
                        )
                    },
                )),
            )
        }
    }

    ThemeColorPickerDialog(
        show = colorPickerOpen && manualThemeColorEnabled,
        savedArgb = settings.manualThemeSeedArgb,
        customArgbValues = settings.customThemeSeedArgbValues,
        onApply = { argb ->
            onAction(SettingsAction.SetManualThemeSeedArgb(argb))
            colorPickerOpen = false
        },
        onCustomColorsChange = { values ->
            onAction(SettingsAction.SetCustomThemeSeedArgbValues(values))
        },
        onDismiss = { colorPickerOpen = false },
    )
}

private fun AppThemeMode.titleResource() = when (this) {
    AppThemeMode.System -> Res.string.settings_theme_system
    AppThemeMode.Light -> Res.string.settings_theme_light
    AppThemeMode.Dark -> Res.string.settings_theme_dark
}

private fun AppThemeMode.summaryResource() = when (this) {
    AppThemeMode.System -> Res.string.settings_theme_system_summary
    AppThemeMode.Light -> Res.string.settings_theme_light_summary
    AppThemeMode.Dark -> Res.string.settings_theme_dark_summary
}

private fun AppLanguageMode.titleResource() = when (this) {
    AppLanguageMode.System -> Res.string.settings_language_system
    AppLanguageMode.Chinese -> Res.string.settings_language_chinese
    AppLanguageMode.English -> Res.string.settings_language_english
}

private fun AppLanguageMode.summaryResource() = when (this) {
    AppLanguageMode.System -> Res.string.settings_language_system_summary
    AppLanguageMode.Chinese -> Res.string.settings_language_chinese_summary
    AppLanguageMode.English -> Res.string.settings_language_english_summary
}
