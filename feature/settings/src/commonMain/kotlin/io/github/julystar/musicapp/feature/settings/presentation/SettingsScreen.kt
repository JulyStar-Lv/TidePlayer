package io.github.julystar.musicapp.feature.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.components.LiquidGlassActionBar
import io.github.julystar.musicapp.core.presentation.components.LocalDesignBottomContentInset
import io.github.julystar.musicapp.core.presentation.theme.DesignTokens
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import musicapp.core.presentation.generated.resources.Res as CoreRes
import musicapp.core.presentation.generated.resources.app_icon
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
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
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

    val bottomContentInset = LocalDesignBottomContentInset.current
    val pageScrollState = rememberScrollState()
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val actionBarProgress = topAppBarScrollBehavior.state.collapsedFraction
    val pageTitle = stringResource(Res.string.settings_title)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) {
        val compact = maxWidth < DesignTokens.adaptive.largeMinWidth
        val pagePadding = if (compact) 24.dp else DesignTokens.spacing.pageExpanded

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(pageScrollState),
        ) {
            TopAppBar(
                title = pageTitle,
                largeTitle = pageTitle,
                color = Color.Transparent,
                titleColor = Color.Transparent,
                scrollBehavior = topAppBarScrollBehavior,
            )

            Column(
            modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = pagePadding,
                        top = 16.dp,
                        end = pagePadding,
                        bottom = 16.dp + bottomContentInset,
                    ),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
        // Search bar
        InputField(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSearch = {},
            label = stringResource(Res.string.settings_search_hint),
            expanded = false,
            onExpandedChange = {},
        )

        // Personalization
        val showAppearance = matches("appearance", "theme", "language")
        val showLyrics = matches("lyrics", "translation", "alignment")
        if (showAppearance || showLyrics) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallTitle(
                    text = stringResource(Res.string.settings_personalization_section),
                    textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    insideMargin = settingsSectionTitleMargin,
                )
                Card {
                    if (showAppearance) {
                        SettingsNavRow(
                            title = stringResource(Res.string.settings_appearance_title),
                            summary = stringResource(Res.string.settings_appearance_card_summary),
                            icon = CoreRes.drawable.icon_settings_palette,
                            gradientStart = Color(0xFFFF6276),
                            gradientEnd = Color(0xFFFF8A3D),
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
                            gradientStart = Color(0xFF4B8DFF),
                            gradientEnd = Color(0xFF7563EC),
                            onClick = onNavigateToLyrics,
                            selected = selectedPage == SettingsPage.Lyrics,
                            showDivider = false,
                        )
                    }
                }
            }
        }

        // Playback
        if (matches("playback", "audio", "focus", "queue", "replaygain", "dsp")) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallTitle(
                    text = stringResource(Res.string.settings_playback_title),
                    textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    insideMargin = settingsSectionTitleMargin,
                )
                Card {
                    SettingsNavRow(
                        title = stringResource(Res.string.settings_playback_title),
                        summary = stringResource(Res.string.settings_playback_card_summary),
                        icon = CoreRes.drawable.icon_settings_circle_play,
                        gradientStart = Color(0xFFD74FBE),
                        gradientEnd = Color(0xFF7259E9),
                        onClick = onNavigateToPlayback,
                        selected = selectedPage == SettingsPage.Playback ||
                            selectedPage == SettingsPage.Equalizer ||
                            selectedPage == SettingsPage.AudioEffects,
                        showDivider = false,
                    )
                }
            }
        }

        // Library & data
        val showSources = matches("library", "sources", "local", "webdav")
        val showPlugins = matches("metadata", "plugins", "lyrico")
        val showNetworkCache = matches("network", "cache", "streaming")
        val showStorage = matches("storage", "data", "cleanup", "backup")
        if (showSources || showPlugins || showNetworkCache || showStorage) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallTitle(
                    text = stringResource(Res.string.settings_library_data_section),
                    textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    insideMargin = settingsSectionTitleMargin,
                )
                Card {
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
                            gradientStart = Color(0xFF2CBFA8),
                            gradientEnd = Color(0xFF3D9EEE),
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
                            gradientStart = Color(0xFF82C53D),
                            gradientEnd = Color(0xFF31B867),
                            onClick = onNavigateToPlugins,
                            selected = selectedPage == SettingsPage.Plugins,
                            showDivider = showNetworkCache || showStorage,
                        )
                    }
                    if (showNetworkCache) {
                        SettingsNavRow(
                            title = stringResource(Res.string.settings_network_cache_title),
                            summary = stringResource(Res.string.settings_network_cache_card_summary),
                            icon = CoreRes.drawable.icon_settings_wifi,
                            gradientStart = Color(0xFF478EEA),
                            gradientEnd = Color(0xFF5E6FE8),
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
                            gradientStart = Color(0xFFFFA13D),
                            gradientEnd = Color(0xFFFF6E45),
                            onClick = onNavigateToStorage,
                            selected = selectedPage == SettingsPage.Storage,
                            showDivider = false,
                        )
                    }
                }
            }
        }

        // App & info
        if (matches("about", "version", "build", "privacy", "licenses", "logs", "diagnostics",
                "incident", "crash", "safe mode")
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallTitle(
                    text = stringResource(Res.string.settings_app_info_section),
                    textColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    insideMargin = settingsSectionTitleMargin,
                )
                Card {
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
                            gradientStart = Color(0xFF9B66EC),
                            gradientEnd = Color(0xFF665CE8),
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
                        gradientStart = Color(0xFFE57954),
                        gradientEnd = Color(0xFFBD5B8A),
                        onClick = onNavigateToAbout,
                        selected = selectedPage == SettingsPage.About ||
                            selectedPage == SettingsPage.Licenses,
                        showDivider = false,
                    )
                }
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
        }
        LiquidGlassActionBar(
            title = pageTitle,
            collapseFraction = actionBarProgress,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

internal fun shouldShowDiagnosticsCenter(
    capabilities: io.github.julystar.musicapp.core.domain.model.SettingsCapabilities,
    queryMatches: Boolean,
): Boolean = capabilities.diagnosticsCenterSupported && queryMatches

// ── Nav Row ──

@Composable
private fun SettingsNavRow(
    title: String,
    summary: String,
    icon: DrawableResource,
    gradientStart: Color,
    gradientEnd: Color,
    onClick: () -> Unit,
    selected: Boolean = false,
    @Suppress("UNUSED_PARAMETER")
    showDivider: Boolean = true,
) {
    BasicComponent(
        enabled = !selected,
        onClick = onClick,
        insideMargin = PaddingValues(
            horizontal = 16.dp,
            vertical = 14.dp,
        ),
        startAction = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(gradientStart, gradientEnd),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
        endActions = {
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                modifier = Modifier.size(16.dp),
            )
        },
    ) {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.headline1,
        )
        Text(
            text = summary,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
