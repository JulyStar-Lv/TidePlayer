package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.runtime.Composable
import io.github.julystar.musicapp.core.domain.model.AUDIO_CACHE_LIMIT_PRESETS_BYTES
import io.github.julystar.musicapp.core.domain.model.AUDIO_PRELOAD_PRESETS_BYTES
import io.github.julystar.musicapp.core.domain.model.IMAGE_CACHE_LIMIT_PRESETS_BYTES
import org.jetbrains.compose.resources.stringResource
import musicapp.feature.settings.generated.resources.*
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun NetworkCacheSettingsSection(
    state: SettingsUiState,
    onBack: (() -> Unit)?,
    onAction: (SettingsAction) -> Unit,
) {
    val settings = state.settings
    val capabilities = state.capabilities

    SettingsPageLayout(
        title = stringResource(Res.string.settings_network_cache_title),
        onBack = onBack,
    ) {
        if (capabilities.networkStatusSupported || capabilities.backgroundScanSupported) {
            SmallTitle(text = stringResource(Res.string.settings_network_section))
            Card {
                SwitchPreference(
                    title = stringResource(Res.string.settings_allow_mobile_network),
                    summary = stringResource(Res.string.settings_allow_mobile_network_summary),
                    checked = settings.allowMeteredNetworkUsage,
                    onCheckedChange = {
                        onAction(SettingsAction.SetAllowMeteredNetworkUsage(it))
                    },
                )
                if (capabilities.networkStatusSupported) {
                    SwitchPreference(
                        title = stringResource(Res.string.settings_resume_network),
                        summary = stringResource(Res.string.settings_resume_network_summary),
                        checked = settings.resumePlaybackAfterNetworkRecovery,
                        onCheckedChange = {
                            onAction(SettingsAction.SetResumePlaybackAfterNetworkRecovery(it))
                        },
                    )
                }
            }
        }

        SmallTitle(text = stringResource(Res.string.settings_audio_cache_section))
        Card {
            SwitchPreference(
                title = stringResource(Res.string.settings_listen_and_cache),
                summary = stringResource(Res.string.settings_listen_and_cache_summary),
                checked = settings.listenAndCacheEnabled,
                onCheckedChange = {
                    onAction(SettingsAction.SetListenAndCacheEnabled(it))
                },
            )
            CacheLimitChoices(
                currentBytes = settings.audioCacheLimitBytes,
                presets = AUDIO_CACHE_LIMIT_PRESETS_BYTES,
                type = CacheLimitType.Audio,
                onAction = onAction,
            )
        }

        SmallTitle(text = stringResource(Res.string.settings_image_cache_section))
        Card {
            CacheLimitChoices(
                currentBytes = settings.imageCacheLimitBytes,
                presets = IMAGE_CACHE_LIMIT_PRESETS_BYTES,
                type = CacheLimitType.Image,
                onAction = onAction,
            )
        }

        SmallTitle(text = stringResource(Res.string.settings_advanced_section))
        Card {
            if (capabilities.audioPreloadSupported) {
                OverlayDropdownPreference(
                    title = stringResource(Res.string.settings_audio_preload),
                    summary = stringResource(
                        Res.string.settings_audio_preload_summary,
                        settings.audioPreloadBytes.cacheLimitLabel(),
                    ),
                    entries = listOf(DropdownEntry(items = AUDIO_PRELOAD_PRESETS_BYTES.map { bytes ->
                        DropdownItem(
                            text = bytes.cacheLimitLabel(),
                            selected = bytes == settings.audioPreloadBytes,
                            onClick = { onAction(SettingsAction.SetAudioPreloadBytes(bytes)) },
                        )
                    })),
                )
            }
            OverlayDropdownPreference(
                title = stringResource(Res.string.settings_timeout),
                entries = listOf(DropdownEntry(items = listOf(10, 20, 30, 60).map { seconds ->
                    DropdownItem(
                        text = stringResource(Res.string.settings_timeout_value, seconds),
                        selected = seconds == settings.connectionTimeoutSeconds,
                        onClick = { onAction(SettingsAction.SetConnectionTimeoutSeconds(seconds)) },
                    )
                })),
            )
            OverlayDropdownPreference(
                title = stringResource(Res.string.settings_retry_count),
                entries = listOf(DropdownEntry(items = listOf(0, 1, 2, 3, 5).map { count ->
                    DropdownItem(
                        text = stringResource(Res.string.settings_retry_count_value, count),
                        selected = count == settings.networkRetryCount,
                        onClick = { onAction(SettingsAction.SetNetworkRetryCount(count)) },
                    )
                })),
            )
        }
    }

    SettingsInputDialog(
        show = state.customCacheLimitDialog != null,
        title = stringResource(Res.string.settings_custom_cache_title),
        message = stringResource(Res.string.settings_custom_cache_message),
        value = state.customCacheLimitInputMb,
        label = stringResource(Res.string.settings_megabytes_unit),
        onValueChange = { onAction(SettingsAction.SetCustomCacheLimitInput(it)) },
        onConfirm = { onAction(SettingsAction.ApplyCustomCacheLimit) },
        onDismiss = { onAction(SettingsAction.DismissCustomCacheLimitDialog) },
    )
}

@Composable
private fun CacheLimitChoices(
    currentBytes: Long,
    presets: List<Long>,
    type: CacheLimitType,
    onAction: (SettingsAction) -> Unit,
) {
    val isCustom = currentBytes !in presets
    OverlayDropdownPreference(
        title = stringResource(
            if (type == CacheLimitType.Audio) Res.string.settings_audio_cache_section
            else Res.string.settings_image_cache_section,
        ),
        entries = listOf(DropdownEntry(items = buildList {
            presets.forEach { bytes ->
                add(
                    DropdownItem(
                        text = bytes.cacheLimitLabel(),
                        selected = !isCustom && bytes == currentBytes,
                        onClick = {
                            onAction(
                                when (type) {
                                    CacheLimitType.Audio -> SettingsAction.SetAudioCacheLimitBytes(bytes)
                                    CacheLimitType.Image -> SettingsAction.SetImageCacheLimitBytes(bytes)
                                },
                            )
                        },
                    ),
                )
            }
            add(
                DropdownItem(
                    text = stringResource(Res.string.settings_cache_custom),
                    selected = isCustom,
                    onClick = { onAction(SettingsAction.OpenCustomCacheLimitDialog(type)) },
                ),
            )
        })),
    )
}

@Composable
private fun Long.cacheLimitLabel(): String = if (this == 0L) {
    stringResource(Res.string.settings_cache_disabled)
} else {
    formatBytes(this)
}
