package io.github.julystar.musicapp.feature.settings.presentation.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.presentation.components.LocalStickyHeaderTransitionContext
import io.github.julystar.musicapp.core.presentation.components.StickyHeaderTransitionContext
import io.github.julystar.musicapp.feature.settings.presentation.SettingsPage
import io.github.julystar.musicapp.feature.settings.presentation.SettingsRoot

private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_APPEARANCE = "settings/appearance"
private const val ROUTE_PLAYBACK = "settings/playback"
private const val ROUTE_EQUALIZER = "settings/playback/equalizer"
private const val ROUTE_AUDIO_EFFECTS = "settings/playback/audio-effects"
private const val ROUTE_LYRICS = "settings/lyrics"
private const val ROUTE_SOURCE = "settings/source"
private const val ROUTE_PLUGINS = "settings/plugins"
private const val ROUTE_NETWORK_CACHE = "settings/network-cache"
private const val ROUTE_STORAGE = "settings/storage"
private const val ROUTE_DIAGNOSTICS = "settings/diagnostics"
private const val ROUTE_ABOUT = "settings/about"
private const val ROUTE_LICENSES = "settings/licenses"
private const val SettingsNavigationTransitionDurationMillis = 700

@Composable
fun SettingsTabGraph(
    navController: NavHostController,
    appVersion: String,
    appBuildInfo: String,
    gitCommitSha: String,
    pluginSettingsContent: @Composable (onBack: (() -> Unit)?) -> Unit,
    onNavigateToSourcePathPicker: () -> Unit,
    onNavigateToSourceEditor: (SourceAccountId?) -> Unit,
) {
    fun navigate(route: String) {
        navController.navigate(route)
    }

    @Composable
    fun Route(entry: NavBackStackEntry, page: SettingsPage) {
        val currentEntry by navController.currentBackStackEntryAsState()
        CompositionLocalProvider(
            LocalStickyHeaderTransitionContext provides StickyHeaderTransitionContext(
                key = entry.id,
                isNavigationTarget = entry.id == currentEntry?.id,
                durationMillis = SettingsNavigationTransitionDurationMillis,
            ),
        ) {
            SettingsRoot(
                page = page,
                appVersion = appVersion,
                appBuildInfo = appBuildInfo,
                gitCommitSha = gitCommitSha,
                pluginSettingsContent = pluginSettingsContent,
                onNavigateToAppearance = { navController.navigateSection(ROUTE_APPEARANCE) },
                onNavigateToPlayback = { navController.navigateSection(ROUTE_PLAYBACK) },
                onNavigateToEqualizer = { navigate(ROUTE_EQUALIZER) },
                onNavigateToAudioEffects = { navigate(ROUTE_AUDIO_EFFECTS) },
                onNavigateToLyrics = { navController.navigateSection(ROUTE_LYRICS) },
                onNavigateToSource = navController::navigateToSourceSettings,
                onNavigateToPlugins = { navController.navigateSection(ROUTE_PLUGINS) },
                onNavigateToNetworkCache = { navController.navigateSection(ROUTE_NETWORK_CACHE) },
                onNavigateToStorage = { navController.navigateSection(ROUTE_STORAGE) },
                onNavigateToDiagnostics = { navController.navigateSection(ROUTE_DIAGNOSTICS) },
                onNavigateToAbout = { navController.navigateSection(ROUTE_ABOUT) },
                onNavigateToLicenses = { navigate(ROUTE_LICENSES) },
                onNavigateToSourcePathPicker = onNavigateToSourcePathPicker,
                onNavigateToSourceEditor = onNavigateToSourceEditor,
                onBack = { navController.navigateUp() },
            )
        }
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_SETTINGS,
        enterTransition = {
            fadeIn(tween(SettingsNavigationTransitionDurationMillis))
        },
        exitTransition = {
            fadeOut(tween(SettingsNavigationTransitionDurationMillis))
        },
        popEnterTransition = {
            fadeIn(tween(SettingsNavigationTransitionDurationMillis))
        },
        popExitTransition = {
            fadeOut(tween(SettingsNavigationTransitionDurationMillis))
        },
    ) {
        composable(ROUTE_SETTINGS) { entry -> Route(entry, SettingsPage.Home) }
        composable(ROUTE_APPEARANCE) { entry -> Route(entry, SettingsPage.Appearance) }
        composable(ROUTE_PLAYBACK) { entry -> Route(entry, SettingsPage.Playback) }
        composable(ROUTE_EQUALIZER) { entry -> Route(entry, SettingsPage.Equalizer) }
        composable(ROUTE_AUDIO_EFFECTS) { entry -> Route(entry, SettingsPage.AudioEffects) }
        composable(ROUTE_LYRICS) { entry -> Route(entry, SettingsPage.Lyrics) }
        composable(ROUTE_SOURCE) { entry -> Route(entry, SettingsPage.Source) }
        composable(ROUTE_PLUGINS) { entry -> Route(entry, SettingsPage.Plugins) }
        composable(ROUTE_NETWORK_CACHE) { entry -> Route(entry, SettingsPage.NetworkCache) }
        composable(ROUTE_STORAGE) { entry -> Route(entry, SettingsPage.Storage) }
        composable(ROUTE_DIAGNOSTICS) { entry -> Route(entry, SettingsPage.Diagnostics) }
        composable(ROUTE_ABOUT) { entry -> Route(entry, SettingsPage.About) }
        composable(ROUTE_LICENSES) { entry -> Route(entry, SettingsPage.Licenses) }
    }
}

fun NavHostController.navigateToSourceSettings() {
    navigate(ROUTE_SOURCE) {
        popUpTo(ROUTE_SETTINGS)
        launchSingleTop = true
    }
}

private fun NavHostController.navigateSection(route: String) {
    navigate(route) {
        popUpTo(ROUTE_SETTINGS)
        launchSingleTop = true
    }
}
