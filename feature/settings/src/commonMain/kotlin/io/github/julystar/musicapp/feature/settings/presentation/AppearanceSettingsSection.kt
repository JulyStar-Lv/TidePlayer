package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.domain.model.AppLanguageMode
import io.github.julystar.musicapp.core.domain.model.AppThemeMode
import io.github.julystar.musicapp.core.domain.model.resolvedDarkManualThemeSeedArgb
import io.github.julystar.musicapp.core.presentation.theme.themePrimaryColor
import org.jetbrains.compose.resources.stringResource
import musicapp.feature.settings.generated.resources.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

@Composable
fun AppearanceSettingsSection(
    state: SettingsUiState,
    onBack: (() -> Unit)?,
    onAction: (SettingsAction) -> Unit,
) {
    val settings = state.settings
    var colorPickerMode by remember { mutableStateOf<AppThemeMode?>(null) }
    val darkSeedArgb = settings.resolvedDarkManualThemeSeedArgb()

    SettingsPageLayout(title = stringResource(Res.string.settings_appearance_title), onBack = onBack) {
        SmallTitle(
            text = stringResource(Res.string.settings_theme_section),
            insideMargin = settingsSectionTitleMargin,
        )
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

        SmallTitle(
            text = stringResource(Res.string.settings_color_section),
            insideMargin = settingsSectionTitleMargin,
        )
        Card {
            listOf(false, true).forEach { darkTheme ->
                val argb = if (darkTheme) darkSeedArgb else settings.manualThemeSeedArgb
                ArrowPreference(
                    title = stringResource(
                        if (darkTheme) Res.string.settings_theme_color_dark
                        else Res.string.settings_theme_color_light,
                    ),
                    summary = stringResource(
                        Res.string.settings_theme_color_current,
                        formatThemePrimaryValue(argb, darkTheme),
                    ),
                    onClick = {
                        colorPickerMode = if (darkTheme) AppThemeMode.Dark else AppThemeMode.Light
                    },
                    endActions = {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(themePrimaryColor(argb, darkTheme))
                                .border(1.dp, MiuixTheme.colorScheme.outline, CircleShape),
                        )
                    },
                )
            }
        }

        SmallTitle(
            text = stringResource(Res.string.settings_language_section),
            insideMargin = settingsSectionTitleMargin,
        )
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
        show = colorPickerMode != null,
        darkTheme = colorPickerMode == AppThemeMode.Dark,
        savedArgb = if (colorPickerMode == AppThemeMode.Dark) darkSeedArgb else settings.manualThemeSeedArgb,
        otherModeArgb = if (colorPickerMode == AppThemeMode.Dark) settings.manualThemeSeedArgb else darkSeedArgb,
        customArgbValues = settings.customThemeSeedArgbValues,
        onApply = { argb ->
            onAction(
                if (colorPickerMode == AppThemeMode.Dark) SettingsAction.SetDarkManualThemeSeedArgb(argb)
                else SettingsAction.SetManualThemeSeedArgb(argb),
            )
            colorPickerMode = null
        },
        onCustomColorsChange = { values ->
            onAction(SettingsAction.SetCustomThemeSeedArgbValues(values))
        },
        onDismiss = { colorPickerMode = null },
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
