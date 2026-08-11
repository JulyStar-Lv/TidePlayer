package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.domain.model.AudioEffectProfile
import io.github.julystar.musicapp.core.domain.model.withAudioEffectProfile
import musicapp.feature.settings.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun AudioEffectsSettingsScreen(
    state: SettingsUiState,
    onBack: (() -> Unit)?,
    onAction: (SettingsAction) -> Unit,
) {
    val effects = state.settings.audioEffects
    val activeModules = effects.activeAudioEffectModules()

    fun updateProfile(profile: AudioEffectProfile) {
        onAction(
            SettingsAction.SetAudioEffectSettings(
                effects.withAudioEffectProfile(profile),
            ),
        )
    }

    SettingsPageLayout(
        title = stringResource(Res.string.settings_audio_effects_title),
        onBack = onBack,
    ) {
        SettingsSection(title = stringResource(Res.string.settings_audio_effects_title)) {
            SettingsSwitchRow(
                title = stringResource(Res.string.settings_audio_effects_title),
                summary = stringResource(Res.string.settings_audio_effects_subtitle),
                checked = effects.enabled,
                onCheckedChange = { enabled ->
                    onAction(
                        SettingsAction.SetAudioEffectSettings(effects.copy(enabled = enabled)),
                    )
                },
            )
            SettingsInfoRow(
                title = stringResource(Res.string.settings_audio_effects_enabled),
                value = if (!effects.enabled) {
                    stringResource(Res.string.settings_audio_processing_off)
                } else if (activeModules.isEmpty()) {
                    stringResource(Res.string.settings_audio_effects_none_enabled)
                } else {
                    stringResource(Res.string.settings_audio_effects_enabled_count, activeModules.size)
                },
            )
        }

        DspProfilePresetSection(
            effects = effects,
            onUpdate = { updated ->
                onAction(SettingsAction.SetAudioEffectSettings(updated))
            },
        )
        DspProcessingChainSection(state)

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (maxWidth >= 660.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        AudioEffectsDynamicsSection(state, effects.profile, effects.enabled, ::updateProfile)
                        AudioEffectsToneFilterSection(state, effects.profile, effects.enabled, ::updateProfile)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        AudioEffectsSoundFieldSection(state, effects.profile, effects.enabled, ::updateProfile)
                        AudioEffectsOutputSection(state, effects, ::updateProfile) { updated ->
                            onAction(SettingsAction.SetAudioEffectSettings(updated))
                        }
                        DspRuntimeStatusSection(state)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    AudioEffectsDynamicsSection(state, effects.profile, effects.enabled, ::updateProfile)
                    AudioEffectsSoundFieldSection(state, effects.profile, effects.enabled, ::updateProfile)
                    AudioEffectsToneFilterSection(state, effects.profile, effects.enabled, ::updateProfile)
                    AudioEffectsOutputSection(state, effects, ::updateProfile) { updated ->
                        onAction(SettingsAction.SetAudioEffectSettings(updated))
                    }
                    DspRuntimeStatusSection(state)
                }
            }
        }
    }
}
