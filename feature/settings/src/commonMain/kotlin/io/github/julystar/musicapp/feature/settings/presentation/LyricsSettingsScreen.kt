package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import kotlin.math.roundToInt

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
        SmallTitle(
            text = stringResource(Res.string.settings_lyrics_alignment_section),
            insideMargin = settingsSectionTitleMargin,
        )
        Card {
            OverlayDropdownPreference(
                title = stringResource(Res.string.settings_lyrics_alignment_section),
                entries = listOf(DropdownEntry(items = LyricTextAlignment.entries.map { alignment ->
                    DropdownItem(
                        text = stringResource(alignment.titleResource()),
                        selected = alignment == lyrics.textAlignment,
                        onClick = { onAction(SettingsAction.SetLyricTextAlignment(alignment)) },
                    )
                })),
            )
        }

        SmallTitle(
            text = stringResource(Res.string.settings_lyrics_source_section),
            insideMargin = settingsSectionTitleMargin,
        )
        Card {
            OverlayDropdownPreference(
                title = stringResource(Res.string.settings_lyrics_source_section),
                summary = stringResource(lyrics.sourceMode.summaryResource()),
                entries = listOf(DropdownEntry(items = LyricSourceMode.entries.map { mode ->
                    DropdownItem(
                        text = stringResource(mode.titleResource()),
                        selected = mode == lyrics.sourceMode,
                        onClick = { onAction(SettingsAction.SetLyricSourceMode(mode)) },
                    )
                })),
            )
            LyricSourcePrioritySettingsRow(
                priority = lyrics.sourcePriority,
                onClick = { editingSourcePriority = true },
            )
            SwitchPreference(
                title = stringResource(Res.string.settings_lyrics_ignore_headers),
                summary = stringResource(Res.string.settings_lyrics_ignore_headers_summary),
                checked = lyrics.ignoreHeaderTags,
                onCheckedChange = { onAction(SettingsAction.SetIgnoreLyricHeaderTags(it)) },
            )
        }

        SmallTitle(
            text = stringResource(Res.string.settings_lyrics_style_section),
            insideMargin = settingsSectionTitleMargin,
        )
        Card {
            var primaryScalePreview by remember(lyrics.primaryFontScalePercent) {
                mutableFloatStateOf(lyrics.primaryFontScalePercent.toFloat())
            }
            SliderPreference(
                title = stringResource(Res.string.settings_lyrics_primary_scale),
                summary = stringResource(Res.string.settings_lyrics_primary_scale_summary),
                value = primaryScalePreview,
                valueRange = MIN_LYRIC_FONT_SCALE_PERCENT.toFloat()..MAX_LYRIC_FONT_SCALE_PERCENT.toFloat(),
                steps = MAX_LYRIC_FONT_SCALE_PERCENT - MIN_LYRIC_FONT_SCALE_PERCENT - 1,
                valueText = stringResource(
                    Res.string.settings_lyrics_percent_value,
                    primaryScalePreview.roundToInt(),
                ),
                onValueChange = { primaryScalePreview = it },
                onValueChangeFinished = {
                    onAction(SettingsAction.SetLyricPrimaryFontScalePercent(primaryScalePreview.roundToInt()))
                },
            )
            var primarySizePreview by remember(lyrics.primaryFontSizeSp) {
                mutableFloatStateOf(lyrics.primaryFontSizeSp.toFloat())
            }
            SliderPreference(
                title = stringResource(Res.string.settings_lyrics_primary_size),
                summary = stringResource(Res.string.settings_lyrics_primary_size_summary),
                value = primarySizePreview,
                valueRange = MIN_LYRIC_PRIMARY_FONT_SIZE_SP.toFloat()..MAX_LYRIC_PRIMARY_FONT_SIZE_SP.toFloat(),
                steps = MAX_LYRIC_PRIMARY_FONT_SIZE_SP - MIN_LYRIC_PRIMARY_FONT_SIZE_SP - 1,
                valueText = stringResource(
                    Res.string.settings_lyrics_sp_value,
                    primarySizePreview.roundToInt(),
                ),
                onValueChange = { primarySizePreview = it },
                onValueChangeFinished = {
                    onAction(SettingsAction.SetLyricPrimaryFontSizeSp(primarySizePreview.roundToInt()))
                },
            )
            var secondaryScalePreview by remember(lyrics.secondaryFontScalePercent) {
                mutableFloatStateOf(lyrics.secondaryFontScalePercent.toFloat())
            }
            SliderPreference(
                title = stringResource(Res.string.settings_lyrics_secondary_scale),
                summary = stringResource(Res.string.settings_lyrics_secondary_scale_summary),
                value = secondaryScalePreview,
                valueRange = MIN_LYRIC_FONT_SCALE_PERCENT.toFloat()..MAX_LYRIC_FONT_SCALE_PERCENT.toFloat(),
                steps = MAX_LYRIC_FONT_SCALE_PERCENT - MIN_LYRIC_FONT_SCALE_PERCENT - 1,
                valueText = stringResource(
                    Res.string.settings_lyrics_percent_value,
                    secondaryScalePreview.roundToInt(),
                ),
                onValueChange = { secondaryScalePreview = it },
                onValueChangeFinished = {
                    onAction(SettingsAction.SetLyricSecondaryFontScalePercent(secondaryScalePreview.roundToInt()))
                },
            )
            var secondarySizePreview by remember(lyrics.secondaryFontSizeSp) {
                mutableFloatStateOf(lyrics.secondaryFontSizeSp.toFloat())
            }
            SliderPreference(
                title = stringResource(Res.string.settings_lyrics_secondary_size),
                summary = stringResource(Res.string.settings_lyrics_secondary_size_summary),
                value = secondarySizePreview,
                valueRange = MIN_LYRIC_SECONDARY_FONT_SIZE_SP.toFloat()..MAX_LYRIC_SECONDARY_FONT_SIZE_SP.toFloat(),
                steps = MAX_LYRIC_SECONDARY_FONT_SIZE_SP - MIN_LYRIC_SECONDARY_FONT_SIZE_SP - 1,
                valueText = stringResource(
                    Res.string.settings_lyrics_sp_value,
                    secondarySizePreview.roundToInt(),
                ),
                onValueChange = { secondarySizePreview = it },
                onValueChangeFinished = {
                    onAction(SettingsAction.SetLyricSecondaryFontSizeSp(secondarySizePreview.roundToInt()))
                },
            )
        }

        SmallTitle(
            text = stringResource(Res.string.settings_lyrics_effects_section),
            insideMargin = settingsSectionTitleMargin,
        )
        Card {
            SwitchPreference(
                title = stringResource(Res.string.settings_lyrics_translation),
                summary = stringResource(Res.string.settings_lyrics_translation_summary),
                checked = lyrics.showTranslation,
                onCheckedChange = { onAction(SettingsAction.SetLyricTranslationVisible(it)) },
            )
            SwitchPreference(
                title = stringResource(Res.string.settings_lyrics_word_lift),
                summary = stringResource(Res.string.settings_lyrics_word_lift_summary),
                checked = lyrics.wordLiftEnabled,
                onCheckedChange = { onAction(SettingsAction.SetLyricWordLiftEnabled(it)) },
            )
            SwitchPreference(
                title = stringResource(Res.string.settings_lyrics_blur),
                summary = stringResource(Res.string.settings_lyrics_blur_summary),
                checked = lyrics.blurEffectEnabled,
                onCheckedChange = { onAction(SettingsAction.SetLyricBlurEffectEnabled(it)) },
            )
            SwitchPreference(
                title = stringResource(Res.string.settings_lyrics_perspective),
                summary = stringResource(Res.string.settings_lyrics_perspective_summary),
                checked = lyrics.perspectiveEffectEnabled,
                onCheckedChange = {
                    onAction(SettingsAction.SetLyricPerspectiveEffectEnabled(it))
                },
            )
            if (lyrics.perspectiveEffectEnabled) {
                var perspectivePreview by remember(lyrics.perspectiveAngleDegrees) {
                    mutableFloatStateOf(lyrics.perspectiveAngleDegrees.toFloat())
                }
                SliderPreference(
                    title = stringResource(Res.string.settings_lyrics_perspective_angle),
                    summary = stringResource(Res.string.settings_lyrics_perspective_angle_summary),
                    value = perspectivePreview,
                    valueRange = MIN_LYRIC_PERSPECTIVE_ANGLE_DEGREES.toFloat()..
                        MAX_LYRIC_PERSPECTIVE_ANGLE_DEGREES.toFloat(),
                    steps = MAX_LYRIC_PERSPECTIVE_ANGLE_DEGREES - MIN_LYRIC_PERSPECTIVE_ANGLE_DEGREES - 1,
                    valueText = stringResource(
                        Res.string.settings_lyrics_degree_value,
                        perspectivePreview.roundToInt(),
                    ),
                    onValueChange = { perspectivePreview = it },
                    onValueChangeFinished = {
                        onAction(SettingsAction.SetLyricPerspectiveAngleDegrees(perspectivePreview.roundToInt()))
                    },
                )
            }
            SwitchPreference(
                title = stringResource(Res.string.settings_lyrics_tap_to_seek),
                summary = stringResource(Res.string.settings_lyrics_tap_to_seek_summary),
                checked = lyrics.tapToSeekEnabled,
                onCheckedChange = { onAction(SettingsAction.SetLyricTapToSeekEnabled(it)) },
            )
        }

        if (capabilities.lyricFontSelectionSupported) {
            SmallTitle(
                text = stringResource(Res.string.settings_lyrics_font_section),
                insideMargin = settingsSectionTitleMargin,
            )
            Card {
                OverlayDropdownPreference(
                    title = stringResource(Res.string.settings_lyrics_western_font),
                    entries = listOf(DropdownEntry(items = LyricFontChoice.entries.map { choice ->
                        DropdownItem(
                            text = stringResource(choice.titleResource()),
                            selected = choice == lyrics.font.westernFont,
                            onClick = { onAction(SettingsAction.SetLyricFontSettings(
                                lyrics.font.copy(westernFont = choice),
                            )) },
                        )
                    })),
                )
                OverlayDropdownPreference(
                    title = stringResource(Res.string.settings_lyrics_cjk_font),
                    entries = listOf(DropdownEntry(items = LyricFontChoice.entries.map { choice ->
                        DropdownItem(
                            text = stringResource(choice.titleResource()),
                            selected = choice == lyrics.font.cjkFont,
                            onClick = { onAction(SettingsAction.SetLyricFontSettings(
                                lyrics.font.copy(cjkFont = choice),
                            )) },
                        )
                    })),
                )
                var fontWeightPreview by remember(lyrics.font.weight) {
                    mutableFloatStateOf(lyrics.font.weight.toFloat())
                }
                SliderPreference(
                    title = stringResource(Res.string.settings_lyrics_font_weight),
                    value = fontWeightPreview,
                    valueRange = MIN_LYRIC_FONT_WEIGHT.toFloat()..MAX_LYRIC_FONT_WEIGHT.toFloat(),
                    steps = MAX_LYRIC_FONT_WEIGHT - MIN_LYRIC_FONT_WEIGHT - 1,
                    valueText = fontWeightPreview.roundToInt().toString(),
                    onValueChange = { fontWeightPreview = it },
                    onValueChangeFinished = {
                        onAction(
                            SettingsAction.SetLyricFontSettings(
                                lyrics.font.copy(weight = fontWeightPreview.roundToInt()),
                            )
                        )
                    },
                )
                SwitchPreference(
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
                SwitchPreference(
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
                SwitchPreference(
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
            SmallTitle(
                text = stringResource(Res.string.settings_lyrics_output_section),
                insideMargin = settingsSectionTitleMargin,
            )
            Card {
                if (capabilities.floatingLyricsSupported) {
                    SwitchPreference(
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
                    SwitchPreference(
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
                    SwitchPreference(
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
                OverlayDropdownPreference(
                    title = stringResource(Res.string.settings_lyrics_output_secondary),
                    entries = listOf(DropdownEntry(items = SecondaryLyricContent.entries.map { content ->
                        DropdownItem(
                            text = stringResource(content.titleResource()),
                            selected = content == output.secondaryContent,
                            onClick = { onAction(SettingsAction.SetLyricOutputSettings(
                                output.copy(secondaryContent = content),
                            )) },
                        )
                    })),
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
        SwitchPreference(
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
