package io.github.julystar.musicapp.feature.settings.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import io.github.julystar.musicapp.feature.settings.presentation.SettingsPage
import io.github.julystar.musicapp.feature.settings.presentation.SettingsRoot

private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_APPEARANCE = "settings/appearance"
private const val ROUTE_PLAYBACK = "settings/playback"
private const val ROUTE_EQUALIZER = "settings/playback/equalizer"
private const val ROUTE_AUDIO_EFFECTS = "settings/playback/audio-effects"
private const val ROUTE_LYRICS = "settings/lyrics"
private const val ROUTE_SOURCE = "settings/source"
private const val ROUTE_NETWORK_CACHE = "settings/network-cache"
private const val ROUTE_STORAGE = "settings/storage"
private const val ROUTE_DIAGNOSTICS = "settings/diagnostics"
private const val ROUTE_ABOUT = "settings/about"
private const val ROUTE_LICENSES = "settings/licenses"

@Composable
fun SettingsTabGraph(
    navController: NavHostController,
    appVersion: String,
    appBuildInfo: String,
    gitCommitSha: String,
    onNavigateToPlugins: () -> Unit,
    onNavigateToSourcePathPicker: () -> Unit,
) {
    fun navigate(route: String) {
        navController.navigate(route)
    }

    @Composable
    fun Route(page: SettingsPage) {
        SettingsRoot(
            page = page,
            appVersion = appVersion,
            appBuildInfo = appBuildInfo,
            gitCommitSha = gitCommitSha,
            onNavigateToAppearance = { navController.navigateSection(ROUTE_APPEARANCE) },
            onNavigateToPlayback = { navController.navigateSection(ROUTE_PLAYBACK) },
            onNavigateToEqualizer = { navigate(ROUTE_EQUALIZER) },
            onNavigateToAudioEffects = { navigate(ROUTE_AUDIO_EFFECTS) },
            onNavigateToLyrics = { navController.navigateSection(ROUTE_LYRICS) },
            onNavigateToSource = navController::navigateToSourceSettings,
            onNavigateToPlugins = onNavigateToPlugins,
            onNavigateToNetworkCache = { navController.navigateSection(ROUTE_NETWORK_CACHE) },
            onNavigateToStorage = { navController.navigateSection(ROUTE_STORAGE) },
            onNavigateToDiagnostics = { navController.navigateSection(ROUTE_DIAGNOSTICS) },
            onNavigateToAbout = { navController.navigateSection(ROUTE_ABOUT) },
            onNavigateToLicenses = { navigate(ROUTE_LICENSES) },
            onNavigateToSourcePathPicker = onNavigateToSourcePathPicker,
            onBack = { navController.navigateUp() },
        )
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_SETTINGS,
    ) {
        composable(ROUTE_SETTINGS) { Route(SettingsPage.Home) }
        composable(ROUTE_APPEARANCE) { Route(SettingsPage.Appearance) }
        composable(ROUTE_PLAYBACK) { Route(SettingsPage.Playback) }
        composable(ROUTE_EQUALIZER) { Route(SettingsPage.Equalizer) }
        composable(ROUTE_AUDIO_EFFECTS) { Route(SettingsPage.AudioEffects) }
        composable(ROUTE_LYRICS) { Route(SettingsPage.Lyrics) }
        composable(ROUTE_SOURCE) { Route(SettingsPage.Source) }
        composable(ROUTE_NETWORK_CACHE) { Route(SettingsPage.NetworkCache) }
        composable(ROUTE_STORAGE) { Route(SettingsPage.Storage) }
        composable(ROUTE_DIAGNOSTICS) { Route(SettingsPage.Diagnostics) }
        composable(ROUTE_ABOUT) { Route(SettingsPage.About) }
        composable(ROUTE_LICENSES) { Route(SettingsPage.Licenses) }
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
