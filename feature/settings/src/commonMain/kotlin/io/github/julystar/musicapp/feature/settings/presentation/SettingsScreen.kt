package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.julystar.musicapp.core.presentation.components.DesignSearchBar
import io.github.julystar.musicapp.core.presentation.components.DesignGlassScene
import io.github.julystar.musicapp.core.presentation.components.DesignListDivider
import io.github.julystar.musicapp.core.presentation.components.LocalDesignBottomContentInset
import io.github.julystar.musicapp.core.presentation.components.DesignSettingsGroup
import io.github.julystar.musicapp.core.presentation.components.DesignStickyGlassActionBar
import io.github.julystar.musicapp.core.presentation.theme.DesignGradients
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import musicapp.core.presentation.generated.resources.Res as CoreRes
import musicapp.core.presentation.generated.resources.app_icon
import musicapp.core.presentation.generated.resources.icon_chevron_right
import musicapp.core.presentation.generated.resources.icon_settings_activity
import musicapp.core.presentation.generated.resources.icon_settings_circle_play
import musicapp.core.presentation.generated.resources.icon_settings_cloud
import musicapp.core.presentation.generated.resources.icon_settings_hard_drive
import musicapp.core.presentation.generated.resources.icon_settings_list_music
import musicapp.core.presentation.generated.resources.icon_settings_palette
import musicapp.core.presentation.generated.resources.icon_settings_puzzle
import musicapp.core.presentation.generated.resources.icon_settings_wifi
import musicapp.feature.settings.generated.resources.*
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    appVersion: String,
    selectedPage: SettingsPage? = null,
    onNavigateToAppearance: () -> Unit,
    onNavigateToPlayback: () -> Unit,
    onNavigateToLyrics: () -> Unit,
    onNavigateToSource: () -> Unit,
    onNavigateToPlugins: () -> Unit,
    onNavigateToNetworkCache: () -> Unit,
    onNavigateToStorage: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToAbout: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val normalizedQuery = searchQuery.trim().lowercase()

    fun matches(vararg terms: String): Boolean {
        if (normalizedQuery.isBlank()) return true
        val joined = terms.joinToString(" ").lowercase()
        return joined.contains(normalizedQuery)
    }

    val scrollState = rememberScrollState()
    val collapseDistance = with(LocalDensity.current) { 88.dp.roundToPx() }
    val actionBarProgress by remember(scrollState, collapseDistance) {
        derivedStateOf {
            (scrollState.value / collapseDistance.toFloat()).coerceIn(0f, 1f)
        }
    }
    val pageTitleAlpha = (1f - actionBarProgress / 0.70f).coerceIn(0f, 1f)
    val bottomContentInset = LocalDesignBottomContentInset.current

    DesignGlassScene(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background),
        ) {
            val compact = maxWidth < DesignTokens.adaptive.largeMinWidth
            val pagePadding = if (compact) 24.dp else DesignTokens.spacing.pageExpanded

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        start = pagePadding,
                        top = if (compact) 0.dp else 16.dp,
                        end = pagePadding,
                        bottom = 16.dp + bottomContentInset,
                    ),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
        // Page title
        if (compact) {
            SettingsMobileHeader(modifier = Modifier.alpha(pageTitleAlpha))
        } else {
            Text(
                text = stringResource(Res.string.settings_title),
                color = MiuixTheme.colorScheme.onBackground,
                style = MiuixTheme.textStyles.title1,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.alpha(pageTitleAlpha),
            )
        }

        // Search bar
        DesignSearchBar(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = stringResource(Res.string.settings_search_hint),
            onSearch = {},
            onClear = { searchQuery = "" },
        )

        // Personalization
        val showAppearance = matches("appearance", "theme", "language")
        val showLyrics = matches("lyrics", "translation", "alignment")
        if (showAppearance || showLyrics) {
            SettingsSectionCard(title = stringResource(Res.string.settings_personalization_section)) {
                if (showAppearance) {
                    SettingsNavRow(
                        title = stringResource(Res.string.settings_appearance_title),
                        summary = stringResource(Res.string.settings_appearance_card_summary),
                        icon = CoreRes.drawable.icon_settings_palette,
                        iconColors = DesignGradients.PinkOrange.colors,
                        onClick = onNavigateToAppearance,
                        selected = selectedPage == SettingsPage.Appearance,
                        showDivider = showLyrics,
                    )
                }
                if (showLyrics) {
                    SettingsNavRow(
                        title = stringResource(Res.string.settings_lyrics_title),
                        summary = stringResource(Res.string.settings_lyrics_card_summary),
                        icon = CoreRes.drawable.icon_settings_list_music,
                        iconColors = DesignGradients.BluePurple.colors,
                        onClick = onNavigateToLyrics,
                        selected = selectedPage == SettingsPage.Lyrics,
                        showDivider = false,
                    )
                }
            }
        }

        // Playback
        if (matches("playback", "audio", "focus", "queue", "replaygain", "dsp")) {
            SettingsSectionCard(title = stringResource(Res.string.settings_playback_title)) {
                SettingsNavRow(
                    title = stringResource(Res.string.settings_playback_title),
                    summary = stringResource(Res.string.settings_playback_card_summary),
                    icon = CoreRes.drawable.icon_settings_circle_play,
                    iconColors = DesignGradients.PinkPurple.colors,
                    onClick = onNavigateToPlayback,
                    selected = selectedPage == SettingsPage.Playback ||
                        selectedPage == SettingsPage.Equalizer ||
                        selectedPage == SettingsPage.AudioEffects,
                    showDivider = false,
                )
            }
        }

        // Library & data
        val showSources = matches("library", "sources", "local", "webdav")
        val showPlugins = matches("metadata", "plugins", "lyrico")
        val showNetworkCache = matches("network", "cache", "streaming")
        val showStorage = matches("storage", "data", "cleanup", "backup")
        if (showSources || showPlugins || showNetworkCache || showStorage) {
            SettingsSectionCard(title = stringResource(Res.string.settings_library_data_section)) {
                if (showSources) {
                    val sourceCount = state.sourceAccounts.size
                    val readyCount = state.enabledSourceCount
                    SettingsNavRow(
                        title = stringResource(Res.string.settings_sources_title),
                        summary = stringResource(
                            Res.string.settings_sources_card_summary,
                            sourceCount,
                            readyCount,
                        ),
                        icon = CoreRes.drawable.icon_settings_cloud,
                        iconColors = DesignGradients.GreenBlue.colors,
                        onClick = onNavigateToSource,
                        selected = selectedPage == SettingsPage.Source,
                        showDivider = showPlugins || showNetworkCache || showStorage,
                    )
                }
                if (showPlugins) {
                    SettingsNavRow(
                        title = stringResource(Res.string.settings_metadata_plugins),
                        summary = stringResource(Res.string.settings_metadata_plugins_summary),
                        icon = CoreRes.drawable.icon_settings_puzzle,
                        iconColors = DesignGradients.LimeEmerald.colors,
                        onClick = onNavigateToPlugins,
                        showDivider = showNetworkCache || showStorage,
                    )
                }
                if (showNetworkCache) {
                    SettingsNavRow(
                        title = stringResource(Res.string.settings_network_cache_title),
                        summary = stringResource(Res.string.settings_network_cache_card_summary),
                        icon = CoreRes.drawable.icon_settings_wifi,
                        iconColors = DesignGradients.CyanTeal.colors,
                        onClick = onNavigateToNetworkCache,
                        selected = selectedPage == SettingsPage.NetworkCache,
                        showDivider = showStorage,
                    )
                }
                if (showStorage) {
                    SettingsNavRow(
                        title = stringResource(Res.string.settings_storage_title),
                        summary = stringResource(
                            Res.string.settings_storage_card_summary,
                            formatBytes(state.storageUsage.totalBytes),
                        ),
                        icon = CoreRes.drawable.icon_settings_hard_drive,
                        iconColors = DesignGradients.OrangeYellow.colors,
                        onClick = onNavigateToStorage,
                        selected = selectedPage == SettingsPage.Storage,
                        showDivider = false,
                    )
                }
            }
        }

        // App & info
        if (matches("about", "version", "build", "privacy", "licenses", "logs", "diagnostics",
                "incident", "crash", "safe mode")
        ) {
            SettingsSectionCard(title = stringResource(Res.string.settings_app_info_section)) {
                if (shouldShowDiagnosticsCenter(
                        state.capabilities,
                        matches(
                            "logs",
                            "diagnostics",
                            "incident",
                            "crash",
                            "safe mode",
                            "privacy",
                        ),
                    )
                ) {
                    SettingsNavRow(
                        title = stringResource(Res.string.diagnostics_title),
                        summary = stringResource(Res.string.diagnostics_card_summary),
                        icon = CoreRes.drawable.icon_settings_activity,
                        iconColors = DesignGradients.PinkOrange.colors,
                        onClick = onNavigateToDiagnostics,
                        selected = selectedPage == SettingsPage.Diagnostics,
                    )
                }
                SettingsNavRow(
                    title = stringResource(Res.string.settings_about_title),
                    summary = stringResource(
                        Res.string.settings_about_card_summary,
                        appVersion.ifBlank { "—" },
                    ),
                    icon = CoreRes.drawable.app_icon,
                    iconColors = DesignGradients.PurplePink.colors,
                    preserveIconColors = true,
                    onClick = onNavigateToAbout,
                    selected = selectedPage == SettingsPage.About ||
                        selectedPage == SettingsPage.Licenses,
                    showDivider = false,
                )
            }
        }

        // No results
        if (normalizedQuery.isNotBlank() && !matches(
                "appearance", "playback", "lyrics", "sources", "plugins",
                "network", "storage", "about", "theme", "language", "audio",
                "focus", "queue", "replaygain", "dsp", "translation", "alignment",
                "library", "local", "webdav", "metadata", "lyrico", "cache",
                "streaming", "data", "cleanup", "backup", "version", "build",
                "privacy", "licenses",
                "logs", "diagnostics", "incident", "crash", "safe mode",
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.settings_search_empty, searchQuery),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body1,
                )
            }
        }

                Spacer(modifier = Modifier.height(32.dp))
            }
            DesignStickyGlassActionBar(
                title = stringResource(Res.string.settings_title),
                collapseFraction = actionBarProgress,
                compactTitle = true,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

internal fun shouldShowDiagnosticsCenter(
    capabilities: io.github.julystar.musicapp.core.domain.model.SettingsCapabilities,
    queryMatches: Boolean,
): Boolean = capabilities.diagnosticsCenterSupported && queryMatches

@Composable
private fun SettingsMobileHeader(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = stringResource(Res.string.settings_title),
            color = MiuixTheme.colorScheme.onBackground,
            style = MiuixTheme.textStyles.title1.copy(
                fontSize = 32.sp,
                lineHeight = 38.sp,
            ),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Section Card ──

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    DesignSettingsGroup(
        title = title,
        maskBottomDivider = false,
        content = content,
    )
}

// ── Nav Row ──

@Composable
private fun SettingsNavRow(
    title: String,
    summary: String,
    icon: DrawableResource,
    iconColors: List<Color>,
    preserveIconColors: Boolean = false,
    onClick: () -> Unit,
    selected: Boolean = false,
    showDivider: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) {
                    MiuixTheme.colorScheme.primary.copy(alpha = 0.08f)
                } else {
                    Color.Transparent
                },
            )
            .clickable(
                enabled = !selected,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SettingsIconBadge(
            drawable = icon,
            colors = iconColors,
            preserveDrawableColors = preserveIconColors,
        )
        // Content
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = if (selected) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurface
                },
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Chevron
        Icon(
            painter = painterResource(CoreRes.drawable.icon_chevron_right),
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp),
        )
    }
    if (showDivider) {
        DesignListDivider()
    }
}
