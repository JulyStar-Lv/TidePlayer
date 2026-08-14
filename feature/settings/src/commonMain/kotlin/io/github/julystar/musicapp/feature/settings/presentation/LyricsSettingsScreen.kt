package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.julystar.musicapp.core.domain.model.LyricFontChoice
import io.github.julystar.musicapp.core.domain.model.LyricSourceKind
import io.github.julystar.musicapp.core.domain.model.LyricSourceMode
import io.github.julystar.musicapp.core.domain.model.LyricTextAlignment
import io.github.julystar.musicapp.core.domain.model.MAX_LYRIC_FONT_SCALE_PERCENT
import io.github.julystar.musicapp.core.domain.model.MAX_LYRIC_PERSPECTIVE_ANGLE_DEGREES
import io.github.julystar.musicapp.core.domain.model.MAX_LYRIC_PRIMARY_FONT_SIZE_SP
import io.github.julystar.musicapp.core.domain.model.MAX_LYRIC_SECONDARY_FONT_SIZE_SP
import io.github.julystar.musicapp.core.domain.model.MIN_LYRIC_FONT_SCALE_PERCENT
import io.github.julystar.musicapp.core.domain.model.MIN_LYRIC_PERSPECTIVE_ANGLE_DEGREES
import io.github.julystar.musicapp.core.domain.model.MIN_LYRIC_PRIMARY_FONT_SIZE_SP
import io.github.julystar.musicapp.core.domain.model.MIN_LYRIC_SECONDARY_FONT_SIZE_SP
import io.github.julystar.musicapp.core.domain.model.MAX_LYRIC_FONT_WEIGHT
import io.github.julystar.musicapp.core.domain.model.MIN_LYRIC_FONT_WEIGHT
import io.github.julystar.musicapp.core.domain.model.SecondaryLyricContent
import org.jetbrains.compose.resources.stringResource
import musicapp.feature.settings.generated.resources.*

@Composable
internal fun LyricsSettingsScreen(
    state: SettingsUiState,
    onBack: (() -> Unit)?,
    onAction: (SettingsAction) -> Unit,
) {
    val lyrics = state.settings.lyrics
    val output = state.settings.lyricOutput
    val capabilities = state.capabilities
    var editingSourcePriority by remember { mutableStateOf(false) }

    SettingsPageLayout(title = stringResource(Res.string.settings_lyrics_title), onBack = onBack) {
        SettingsSection(title = stringResource(Res.string.settings_lyrics_alignment_section)) {
            SettingsSelectRow(
                label = stringResource(Res.string.settings_lyrics_alignment_section),
                selected = lyrics.textAlignment,
                options = LyricTextAlignment.entries.toList(),
                optionLabel = { alignment -> stringResource(alignment.titleResource()) },
                onSelect = { onAction(SettingsAction.SetLyricTextAlignment(it)) },
            )
        }

        SettingsSection(title = stringResource(Res.string.settings_lyrics_source_section)) {
            SettingsSelectRow(
                label = stringResource(Res.string.settings_lyrics_source_section),
                subtitle = stringResource(lyrics.sourceMode.summaryResource()),
                selected = lyrics.sourceMode,
                options = LyricSourceMode.entries.toList(),
                optionLabel = { mode -> stringResource(mode.titleResource()) },
                onSelect = { onAction(SettingsAction.SetLyricSourceMode(it)) },
            )
            LyricSourcePrioritySettingsRow(
                priority = lyrics.sourcePriority,
                onClick = { editingSourcePriority = true },
            )
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_lyrics_ignore_headers),
                summary = stringResource(Res.string.settings_lyrics_ignore_headers_summary),
                checked = lyrics.ignoreHeaderTags,
                onCheckedChange = { onAction(SettingsAction.SetIgnoreLyricHeaderTags(it)) },
            )
        }

        SettingsSection(title = stringResource(Res.string.settings_lyrics_style_section)) {
            SettingsSliderRow(
                title = stringResource(Res.string.settings_lyrics_primary_scale),
                summary = stringResource(Res.string.settings_lyrics_primary_scale_summary),
                value = lyrics.primaryFontScalePercent,
                valueRange = MIN_LYRIC_FONT_SCALE_PERCENT..MAX_LYRIC_FONT_SCALE_PERCENT,
                valueText = stringResource(
                    Res.string.settings_lyrics_percent_value,
                    lyrics.primaryFontScalePercent,
                ),
                onValueChange = {
                    onAction(SettingsAction.SetLyricPrimaryFontScalePercent(it))
                },
            )
            SettingsSliderRow(
                title = stringResource(Res.string.settings_lyrics_primary_size),
                summary = stringResource(Res.string.settings_lyrics_primary_size_summary),
                value = lyrics.primaryFontSizeSp,
                valueRange = MIN_LYRIC_PRIMARY_FONT_SIZE_SP..MAX_LYRIC_PRIMARY_FONT_SIZE_SP,
                valueText = stringResource(
                    Res.string.settings_lyrics_sp_value,
                    lyrics.primaryFontSizeSp,
                ),
                onValueChange = { onAction(SettingsAction.SetLyricPrimaryFontSizeSp(it)) },
            )
            SettingsSliderRow(
                title = stringResource(Res.string.settings_lyrics_secondary_scale),
                summary = stringResource(Res.string.settings_lyrics_secondary_scale_summary),
                value = lyrics.secondaryFontScalePercent,
                valueRange = MIN_LYRIC_FONT_SCALE_PERCENT..MAX_LYRIC_FONT_SCALE_PERCENT,
                valueText = stringResource(
                    Res.string.settings_lyrics_percent_value,
                    lyrics.secondaryFontScalePercent,
                ),
                onValueChange = {
                    onAction(SettingsAction.SetLyricSecondaryFontScalePercent(it))
                },
            )
            SettingsSliderRow(
                title = stringResource(Res.string.settings_lyrics_secondary_size),
                summary = stringResource(Res.string.settings_lyrics_secondary_size_summary),
                value = lyrics.secondaryFontSizeSp,
                valueRange = MIN_LYRIC_SECONDARY_FONT_SIZE_SP..MAX_LYRIC_SECONDARY_FONT_SIZE_SP,
                valueText = stringResource(
                    Res.string.settings_lyrics_sp_value,
                    lyrics.secondaryFontSizeSp,
                ),
                showDivider = false,
                onValueChange = { onAction(SettingsAction.SetLyricSecondaryFontSizeSp(it)) },
            )
        }

        SettingsSection(title = stringResource(Res.string.settings_lyrics_effects_section)) {
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_lyrics_translation),
                summary = stringResource(Res.string.settings_lyrics_translation_summary),
                checked = lyrics.showTranslation,
                onCheckedChange = { onAction(SettingsAction.SetLyricTranslationVisible(it)) },
            )
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_lyrics_word_lift),
                summary = stringResource(Res.string.settings_lyrics_word_lift_summary),
                checked = lyrics.wordLiftEnabled,
                onCheckedChange = { onAction(SettingsAction.SetLyricWordLiftEnabled(it)) },
            )
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_lyrics_blur),
                summary = stringResource(Res.string.settings_lyrics_blur_summary),
                checked = lyrics.blurEffectEnabled,
                onCheckedChange = { onAction(SettingsAction.SetLyricBlurEffectEnabled(it)) },
            )
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_lyrics_perspective),
                summary = stringResource(Res.string.settings_lyrics_perspective_summary),
                checked = lyrics.perspectiveEffectEnabled,
                onCheckedChange = {
                    onAction(SettingsAction.SetLyricPerspectiveEffectEnabled(it))
                },
            )
            if (lyrics.perspectiveEffectEnabled) {
                SettingsSliderRow(
                    title = stringResource(Res.string.settings_lyrics_perspective_angle),
                    summary = stringResource(Res.string.settings_lyrics_perspective_angle_summary),
                    value = lyrics.perspectiveAngleDegrees,
                    valueRange = MIN_LYRIC_PERSPECTIVE_ANGLE_DEGREES..
                        MAX_LYRIC_PERSPECTIVE_ANGLE_DEGREES,
                    valueText = stringResource(
                        Res.string.settings_lyrics_degree_value,
                        lyrics.perspectiveAngleDegrees,
                    ),
                    onValueChange = {
                        onAction(SettingsAction.SetLyricPerspectiveAngleDegrees(it))
                    },
                )
            }
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_lyrics_tap_to_seek),
                summary = stringResource(Res.string.settings_lyrics_tap_to_seek_summary),
                checked = lyrics.tapToSeekEnabled,
                onCheckedChange = { onAction(SettingsAction.SetLyricTapToSeekEnabled(it)) },
            )
        }

        if (capabilities.lyricFontSelectionSupported) {
            SettingsSection(title = stringResource(Res.string.settings_lyrics_font_section)) {
                SettingsSelectRow(
                    label = stringResource(Res.string.settings_lyrics_western_font),
                    selected = lyrics.font.westernFont,
                    options = LyricFontChoice.entries.toList(),
                    optionLabel = { choice -> stringResource(choice.titleResource()) },
                    onSelect = { choice ->
                        onAction(
                            SettingsAction.SetLyricFontSettings(
                                lyrics.font.copy(westernFont = choice)
                            )
                        )
                    },
                )
                SettingsSelectRow(
                    label = stringResource(Res.string.settings_lyrics_cjk_font),
                    selected = lyrics.font.cjkFont,
                    options = LyricFontChoice.entries.toList(),
                    optionLabel = { choice -> stringResource(choice.titleResource()) },
                    onSelect = { choice ->
                        onAction(
                            SettingsAction.SetLyricFontSettings(lyrics.font.copy(cjkFont = choice))
                        )
                    },
                )
                SettingsSliderRow(
                    title = stringResource(Res.string.settings_lyrics_font_weight),
                    value = lyrics.font.weight,
                    valueRange = MIN_LYRIC_FONT_WEIGHT..MAX_LYRIC_FONT_WEIGHT,
                    valueText = lyrics.font.weight.toString(),
                    onValueChange = {
                        onAction(
                            SettingsAction.SetLyricFontSettings(lyrics.font.copy(weight = it))
                        )
                    },
                )
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_lyrics_font_apply_page),
                    checked = lyrics.font.applyToLyricsPage,
                    onCheckedChange = {
                        onAction(
                            SettingsAction.SetLyricFontSettings(
                                lyrics.font.copy(applyToLyricsPage = it)
                            )
                        )
                    },
                )
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_lyrics_font_apply_floating),
                    checked = lyrics.font.applyToFloatingLyrics,
                    onCheckedChange = {
                        onAction(
                            SettingsAction.SetLyricFontSettings(
                                lyrics.font.copy(applyToFloatingLyrics = it)
                            )
                        )
                    },
                )
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_lyrics_font_apply_share),
                    checked = lyrics.font.applyToShareCard,
                    onCheckedChange = {
                        onAction(
                            SettingsAction.SetLyricFontSettings(
                                lyrics.font.copy(applyToShareCard = it)
                            )
                        )
                    },
                )
            }
        }

        if (capabilities.hasAnyLyricOutput()) {
            SettingsSection(title = stringResource(Res.string.settings_lyrics_output_section)) {
                if (capabilities.floatingLyricsSupported) {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.settings_lyrics_output_floating),
                        checked = output.floatingLyricsEnabled,
                        onCheckedChange = {
                            onAction(
                                SettingsAction.SetLyricOutputSettings(
                                    output.copy(floatingLyricsEnabled = it)
                                )
                            )
                        },
                    )
                }
                if (capabilities.notificationLyricsSupported) {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.settings_lyrics_output_notification),
                        checked = output.notificationLyricsEnabled,
                        onCheckedChange = {
                            onAction(
                                SettingsAction.SetLyricOutputSettings(
                                    output.copy(notificationLyricsEnabled = it)
                                )
                            )
                        },
                    )
                }
                if (capabilities.bluetoothLyricsSupported) {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.settings_lyrics_output_bluetooth),
                        checked = output.bluetoothLyricsEnabled,
                        onCheckedChange = {
                            onAction(
                                SettingsAction.SetLyricOutputSettings(
                                    output.copy(bluetoothLyricsEnabled = it)
                                )
                            )
                        },
                    )
                }
                PlatformLyricOutputRows(
                    state = state,
                    onAction = onAction,
                )
                SettingsSelectRow(
                    label = stringResource(Res.string.settings_lyrics_output_secondary),
                    selected = output.secondaryContent,
                    options = SecondaryLyricContent.entries.toList(),
                    optionLabel = { content -> stringResource(content.titleResource()) },
                    onSelect = { content ->
                        onAction(
                            SettingsAction.SetLyricOutputSettings(
                                output.copy(secondaryContent = content)
                            )
                        )
                    },
                )
            }
        }
    }

    LyricSourcePriorityDialog(
        show = editingSourcePriority,
        priority = lyrics.sourcePriority,
        onPriorityChange = { priority ->
            onAction(SettingsAction.SetLyricSourcePriority(priority))
        },
        onDismiss = { editingSourcePriority = false },
    )
}

private fun LyricTextAlignment.titleResource() = when (this) {
    LyricTextAlignment.Left -> Res.string.settings_lyrics_align_left
    LyricTextAlignment.Center -> Res.string.settings_lyrics_align_center
    LyricTextAlignment.Right -> Res.string.settings_lyrics_align_right
}

@Composable
private fun PlatformLyricOutputRows(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    val output = state.settings.lyricOutput
    val capabilities = state.capabilities
    val rows = listOf(
        Triple(capabilities.lyriconSupported, Res.string.settings_lyrics_output_lyricon, output.lyriconEnabled),
        Triple(capabilities.superLyricSupported, Res.string.settings_lyrics_output_superlyric, output.superLyricEnabled),
        Triple(capabilities.lyricGetterSupported, Res.string.settings_lyrics_output_lyricgetter, output.lyricGetterEnabled),
        Triple(capabilities.flymeStatusLyricsSupported, Res.string.settings_lyrics_output_flyme, output.flymeStatusLyricsEnabled),
        Triple(capabilities.colorOsLockScreenLyricsSupported, Res.string.settings_lyrics_output_coloros, output.colorOsLockScreenLyricsEnabled),
    )
    rows.forEachIndexed { index, (supported, title, checked) ->
        if (!supported) return@forEachIndexed
        SettingsSwitchRow(
            title = stringResource(title),
            checked = checked,
            onCheckedChange = { enabled ->
                val updated = when (index) {
                    0 -> output.copy(lyriconEnabled = enabled)
                    1 -> output.copy(superLyricEnabled = enabled)
                    2 -> output.copy(lyricGetterEnabled = enabled)
                    3 -> output.copy(flymeStatusLyricsEnabled = enabled)
                    else -> output.copy(colorOsLockScreenLyricsEnabled = enabled)
                }
                onAction(SettingsAction.SetLyricOutputSettings(updated))
            },
        )
    }
}

private fun LyricSourceMode.titleResource() = when (this) {
    LyricSourceMode.Auto -> Res.string.settings_lyrics_source_auto
    LyricSourceMode.Embedded -> Res.string.settings_lyrics_source_embedded
    LyricSourceMode.External -> Res.string.settings_lyrics_source_external
}

private fun LyricSourceMode.summaryResource() = when (this) {
    LyricSourceMode.Auto -> Res.string.settings_lyrics_source_auto_summary
    LyricSourceMode.Embedded -> Res.string.settings_lyrics_source_embedded_summary
    LyricSourceMode.External -> Res.string.settings_lyrics_source_external_summary
}

internal fun LyricSourceKind.titleResource() = when (this) {
    LyricSourceKind.EmbeddedTtml -> Res.string.settings_lyrics_priority_embedded_ttml
    LyricSourceKind.EmbeddedWordTimed -> Res.string.settings_lyrics_priority_embedded_word_timed
    LyricSourceKind.EmbeddedPlain -> Res.string.settings_lyrics_priority_embedded_plain
    LyricSourceKind.ExternalTtml -> Res.string.settings_lyrics_priority_external_ttml
    LyricSourceKind.ExternalWordTimed -> Res.string.settings_lyrics_priority_external_word_timed
    LyricSourceKind.ExternalPlain -> Res.string.settings_lyrics_priority_external_plain
}

private fun LyricFontChoice.titleResource() = when (this) {
    LyricFontChoice.System -> Res.string.settings_lyrics_font_system
    LyricFontChoice.AppSans -> Res.string.settings_lyrics_font_app_sans
    LyricFontChoice.AppCjk -> Res.string.settings_lyrics_font_app_cjk
    LyricFontChoice.Monospace -> Res.string.settings_lyrics_font_monospace
}

private fun SecondaryLyricContent.titleResource() = when (this) {
    SecondaryLyricContent.Off -> Res.string.settings_lyrics_secondary_off
    SecondaryLyricContent.Translation -> Res.string.settings_lyrics_secondary_translation
    SecondaryLyricContent.Pronunciation -> Res.string.settings_lyrics_secondary_pronunciation
}

private fun io.github.julystar.musicapp.core.domain.model.SettingsCapabilities.hasAnyLyricOutput(): Boolean =
    floatingLyricsSupported || notificationLyricsSupported || bluetoothLyricsSupported ||
        lyriconSupported || superLyricSupported || lyricGetterSupported ||
        flymeStatusLyricsSupported || colorOsLockScreenLyricsSupported
