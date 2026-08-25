package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.domain.model.AudioEffectProfile
import io.github.julystar.musicapp.core.domain.model.BUILT_IN_EQUALIZER_PRESETS
import io.github.julystar.musicapp.core.domain.model.EqualizerMode
import io.github.julystar.musicapp.core.domain.model.HeadroomMode
import io.github.julystar.musicapp.core.domain.model.ParametricEqualizerSettings
import io.github.julystar.musicapp.core.domain.model.applyPreset
import io.github.julystar.musicapp.core.domain.model.matchingPreset
import io.github.julystar.musicapp.core.domain.model.resetEqualizer
import io.github.julystar.musicapp.core.domain.model.withAudioEffectProfile
import musicapp.feature.settings.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EqualizerSettingsScreen(
    state: SettingsUiState,
    onBack: (() -> Unit)?,
    onAction: (SettingsAction) -> Unit,
) {
    val effects = state.settings.audioEffects
    val profile = effects.profile
    val capabilities = state.capabilities.audioDsp
    val pagerState = rememberPagerState(
        initialPage = profile.equalizerMode.ordinal,
        pageCount = { EqualizerMode.entries.size },
    )
    val pagerScope = rememberCoroutineScope()
    val visibleMode = EqualizerMode.entries[pagerState.currentPage]
    val equalizerEnabled = when (visibleMode) {
        EqualizerMode.Graphic -> profile.graphicEqualizer.enabled
        EqualizerMode.Parametric -> profile.parametricEqualizer.enabled
    }
    val selectedModeSupported = when (visibleMode) {
        EqualizerMode.Graphic -> capabilities.graphicEqualizer
        EqualizerMode.Parametric -> capabilities.parametricEqualizer
    }

    fun updateProfile(updated: AudioEffectProfile) {
        onAction(
            SettingsAction.SetAudioEffectSettings(
                effects.withAudioEffectProfile(updated),
            ),
        )
    }

    LaunchedEffect(pagerState.settledPage) {
        val settledMode = EqualizerMode.entries[pagerState.settledPage]
        val supported = when (settledMode) {
            EqualizerMode.Graphic -> capabilities.graphicEqualizer
            EqualizerMode.Parametric -> capabilities.parametricEqualizer
        }
        if (supported && profile.equalizerMode != settledMode) {
            updateProfile(profile.selectEqualizerMode(settledMode))
        }
    }
    LaunchedEffect(profile.equalizerMode) {
        val targetPage = profile.equalizerMode.ordinal
        if (!pagerState.isScrollInProgress && pagerState.settledPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    fun selectMode(index: Int) {
        val mode = EqualizerMode.entries[index]
        val supported = when (mode) {
            EqualizerMode.Graphic -> capabilities.graphicEqualizer
            EqualizerMode.Parametric -> capabilities.parametricEqualizer
        }
        if (supported && pagerState.currentPage != index) {
            pagerScope.launch { pagerState.animateScrollToPage(index) }
        }
    }

    SettingsPageLayout(
        title = stringResource(Res.string.settings_equalizer_section),
        onBack = onBack,
        compactHorizontalPadding = 12.dp,
        scrollable = false,
    ) {
        SmallTitle(
            text = stringResource(Res.string.settings_equalizer_section),
            insideMargin = settingsSectionTitleMargin,
        )
        Card {
            SwitchPreference(
                title = stringResource(Res.string.settings_equalizer_enabled),
                summary = stringResource(Res.string.settings_equalizer_enabled_summary),
                checked = equalizerEnabled,
                enabled = selectedModeSupported,
                onCheckedChange = { enabled ->
                    updateProfile(
                        when (visibleMode) {
                            EqualizerMode.Graphic -> profile.copy(
                                equalizerMode = visibleMode,
                                graphicEqualizer = profile.graphicEqualizer.copy(enabled = enabled),
                            )
                            EqualizerMode.Parametric -> profile.copy(
                                equalizerMode = visibleMode,
                                parametricEqualizer =
                                    profile.parametricEqualizer.copy(enabled = enabled),
                            )
                        },
                    )
                },
            )
        }

        EqualizerModeSection(
            selectedMode = visibleMode,
            capabilities = capabilities,
            onModeSelected = ::selectMode,
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            key = { EqualizerMode.entries[it] },
            userScrollEnabled = capabilities.graphicEqualizer && capabilities.parametricEqualizer,
        ) { page ->
            val pageMode = EqualizerMode.entries[page]
            val pageProfile = profile.copy(equalizerMode = pageMode)
            val pageEnabled = when (pageMode) {
                EqualizerMode.Graphic -> pageProfile.graphicEqualizer.enabled
                EqualizerMode.Parametric -> pageProfile.parametricEqualizer.enabled
            }
            val pageSupported = when (pageMode) {
                EqualizerMode.Graphic -> capabilities.graphicEqualizer
                EqualizerMode.Parametric -> capabilities.parametricEqualizer
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if (maxWidth >= 660.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1.45f),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                EqualizerResponseSection(state)
                                EqualizerEditorSection(
                                    profile = pageProfile,
                                    enabled = pageEnabled && pageSupported,
                                    maxParametricBands = capabilities.maxParametricBands,
                                    onUpdate = ::updateProfile,
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                EqualizerPresetSection(
                                    pageProfile,
                                    capabilities.graphicEqualizer,
                                    ::updateProfile,
                                )
                                EqualizerAdvancedSection(
                                    state,
                                    pageProfile,
                                    pageSupported,
                                    ::updateProfile,
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            EqualizerPresetSection(
                                pageProfile,
                                capabilities.graphicEqualizer,
                                ::updateProfile,
                            )
                            EqualizerResponseSection(state)
                            EqualizerEditorSection(
                                profile = pageProfile,
                                enabled = pageEnabled && pageSupported,
                                maxParametricBands = capabilities.maxParametricBands,
                                onUpdate = ::updateProfile,
                            )
                            EqualizerAdvancedSection(
                                state,
                                pageProfile,
                                pageSupported,
                                ::updateProfile,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EqualizerModeSection(
    selectedMode: EqualizerMode,
    capabilities: io.github.julystar.musicapp.core.domain.model.AudioDspCapabilities,
    onModeSelected: (Int) -> Unit,
) {
    val availableModes = buildList {
        if (capabilities.graphicEqualizer) add(EqualizerMode.Graphic)
        if (capabilities.parametricEqualizer) add(EqualizerMode.Parametric)
    }
    if (availableModes.isEmpty()) return
    SmallTitle(
        text = stringResource(Res.string.settings_equalizer_mode),
        insideMargin = settingsSectionTitleMargin,
    )
    Card {
        TabRow(
            tabs = availableModes.map { mode ->
                stringResource(
                    if (mode == EqualizerMode.Graphic) {
                        Res.string.settings_equalizer_graphic
                    } else {
                        Res.string.settings_equalizer_parametric
                    },
                )
            },
            selectedTabIndex = availableModes.indexOf(selectedMode).coerceAtLeast(0),
            onTabSelected = { index -> onModeSelected(availableModes[index].ordinal) },
            modifier = Modifier.padding(12.dp),
        )
    }
}

private fun AudioEffectProfile.selectEqualizerMode(mode: EqualizerMode): AudioEffectProfile = copy(
    equalizerMode = mode,
    graphicEqualizer = graphicEqualizer.copy(enabled = mode == EqualizerMode.Graphic),
    parametricEqualizer = parametricEqualizer.copy(enabled = mode == EqualizerMode.Parametric),
)

@Composable
private fun EqualizerPresetSection(
    profile: AudioEffectProfile,
    supported: Boolean,
    onUpdate: (AudioEffectProfile) -> Unit,
) {
    if (profile.equalizerMode != EqualizerMode.Graphic) return
    val selectedPreset = profile.graphicEqualizer.matchingPreset()
    val choices = listOf(EqualizerPresetChoice(null, stringResource(Res.string.settings_dsp_preset_custom))) +
        BUILT_IN_EQUALIZER_PRESETS.map { preset ->
            EqualizerPresetChoice(preset.id, equalizerPresetName(preset.id).orEmpty())
        }
    val selected = choices.firstOrNull { it.id == selectedPreset?.id } ?: choices.first()
    SmallTitle(
        text = stringResource(Res.string.settings_eq_presets_section),
        insideMargin = settingsSectionTitleMargin,
    )
    Card {
        OverlayDropdownPreference(
            title = stringResource(Res.string.settings_dsp_preset),
            enabled = supported,
            entries = listOf(DropdownEntry(items = choices.map { choice ->
                DropdownItem(
                    text = choice.name,
                    selected = choice == selected,
                    onClick = {
                        BUILT_IN_EQUALIZER_PRESETS.firstOrNull { it.id == choice.id }?.let { preset ->
                            onUpdate(
                                profile.copy(
                                    equalizerMode = EqualizerMode.Graphic,
                                    graphicEqualizer = profile.graphicEqualizer.applyPreset(preset),
                                    parametricEqualizer = profile.parametricEqualizer.copy(enabled = false),
                                ),
                            )
                        }
                    },
                )
            })),
        )
    }
}

@Composable
private fun EqualizerResponseSection(state: SettingsUiState) {
    SmallTitle(
        text = stringResource(Res.string.settings_eq_frequency_response),
        insideMargin = settingsSectionTitleMargin,
    )
    Card {
        EqualizerFrequencyResponse(response = state.audioDspFrequencyResponse)
    }
}

@Composable
private fun EqualizerEditorSection(
    profile: AudioEffectProfile,
    enabled: Boolean,
    maxParametricBands: Int,
    onUpdate: (AudioEffectProfile) -> Unit,
) {
    SmallTitle(
        text = stringResource(
            if (profile.equalizerMode == EqualizerMode.Graphic) {
                Res.string.settings_eq_editor_section
            } else {
                Res.string.settings_equalizer_parametric
            },
        ),
        insideMargin = settingsSectionTitleMargin,
    )
    Card {
        when (profile.equalizerMode) {
            EqualizerMode.Graphic -> GraphicEqualizerEditor(
                settings = profile.graphicEqualizer,
                enabled = enabled,
                onUpdate = { updated ->
                    onUpdate(profile.copy(graphicEqualizer = updated))
                },
            )
            EqualizerMode.Parametric -> ParametricEqualizerEditor(
                settings = profile.parametricEqualizer,
                enabled = enabled,
                maxBands = maxParametricBands,
                onUpdate = { updated ->
                    onUpdate(profile.copy(parametricEqualizer = updated))
                },
            )
        }
    }
}

@Composable
private fun EqualizerAdvancedSection(
    state: SettingsUiState,
    profile: AudioEffectProfile,
    supported: Boolean,
    onUpdate: (AudioEffectProfile) -> Unit,
) {
    SmallTitle(
        text = stringResource(Res.string.settings_eq_advanced_section),
        insideMargin = settingsSectionTitleMargin,
    )
    Card {
        when (profile.equalizerMode) {
            EqualizerMode.Graphic -> {
                val equalizer = profile.graphicEqualizer
                var preampPreview by remember(equalizer.preampTenthsDb) {
                    mutableFloatStateOf(equalizer.preampTenthsDb.toFloat())
                }
                SliderPreference(
                    title = stringResource(Res.string.settings_eq_preamp),
                    value = preampPreview,
                    valueRange = -240f..120f,
                    steps = 359,
                    valueText = formatTenthsDb(preampPreview.roundToInt()),
                    enabled = supported && equalizer.enabled,
                    onValueChange = { preampPreview = it },
                    onValueChangeFinished = {
                        onUpdate(profile.copy(
                            graphicEqualizer = equalizer.copy(preampTenthsDb = preampPreview.roundToInt()),
                        ))
                    },
                )
                var qPreview by remember(equalizer.qHundredths) {
                    mutableFloatStateOf(equalizer.qHundredths.toFloat())
                }
                SliderPreference(
                    title = stringResource(Res.string.settings_eq_q),
                    value = qPreview,
                    valueRange = 10f..1_000f,
                    steps = 989,
                    valueText = formatHundredths(qPreview.roundToInt()),
                    enabled = supported && equalizer.enabled,
                    onValueChange = { qPreview = it },
                    onValueChangeFinished = {
                        onUpdate(profile.copy(
                            graphicEqualizer = equalizer.copy(qHundredths = qPreview.roundToInt()),
                        ))
                    },
                )
                ArrowPreference(
                    title = stringResource(Res.string.settings_eq_reset),
                    summary = stringResource(Res.string.settings_eq_reset_summary),
                    enabled = supported,
                    onClick = {
                        onUpdate(profile.copy(graphicEqualizer = equalizer.resetEqualizer()))
                    },
                )
            }
            EqualizerMode.Parametric -> {
                val equalizer = profile.parametricEqualizer
                var preampPreview by remember(equalizer.preampTenthsDb) {
                    mutableFloatStateOf(equalizer.preampTenthsDb.toFloat())
                }
                SliderPreference(
                    title = stringResource(Res.string.settings_eq_preamp),
                    value = preampPreview,
                    valueRange = -960f..120f,
                    steps = 1_079,
                    valueText = formatTenthsDb(preampPreview.roundToInt()),
                    enabled = supported && equalizer.enabled,
                    onValueChange = { preampPreview = it },
                    onValueChangeFinished = {
                        onUpdate(profile.copy(
                            parametricEqualizer = equalizer.copy(preampTenthsDb = preampPreview.roundToInt()),
                        ))
                    },
                )
                ArrowPreference(
                    title = stringResource(Res.string.settings_peq_reset),
                    summary = stringResource(Res.string.settings_peq_reset_summary),
                    enabled = supported,
                    onClick = {
                        onUpdate(
                            profile.copy(
                                parametricEqualizer = ParametricEqualizerSettings(
                                    enabled = equalizer.enabled,
                                ),
                            ),
                        )
                    },
                )
            }
        }
        if (state.settings.audioEffects.headroom.mode == HeadroomMode.Automatic) {
            BasicComponent(
                title = stringResource(Res.string.settings_headroom_automatic_info),
                summary = "${formatMeter(state.audioDspMeter.appliedHeadroomDb)} · " +
                    stringResource(Res.string.settings_eq_automatic_headroom_hint),
            )
        }
    }
}

private data class EqualizerPresetChoice(val id: String?, val name: String)
