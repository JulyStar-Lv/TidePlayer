package io.github.julystar.musicapp.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.navigation.NavHostController
import io.github.julystar.musicapp.core.LocalNavController
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.core.domain.model.SourceEditorType
import io.github.julystar.musicapp.core.presentation.components.LocalDesignStickyHeaderStateSink
import io.github.julystar.musicapp.core.presentation.components.LiquidGlassActionBar
import io.github.julystar.musicapp.core.presentation.components.StickyHeaderState
import io.github.julystar.musicapp.core.presentation.components.StickyHeaderStateSink
import io.github.julystar.musicapp.core.presentation.navigation.MusicGraph
import io.github.julystar.musicapp.core.presentation.transition.LocalDetailArtworkAnimatedVisibilityScope
import io.github.julystar.musicapp.feature.home.presentation.HomeRoot
import io.github.julystar.musicapp.feature.library.presentation.navigation.LibraryTabGraph
import io.github.julystar.musicapp.feature.search.presentation.navigation.SearchTabGraph
import io.github.julystar.musicapp.feature.settings.presentation.navigation.SettingsTabGraph
import io.github.julystar.musicapp.platform.getAppBuildInfo
import io.github.julystar.musicapp.platform.getAppGitCommitSha
import io.github.julystar.musicapp.platform.getAppVersion
import io.github.julystar.musicapp.plugin.management.PluginSettingsRoot
import io.github.julystar.musicapp.service.playback.domain.SleepModeLeftTime
import io.github.julystar.musicapp.service.playback.presentation.shell.rememberOpenSleepTimer

@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun HomeTabContent(
    currentTab: HomeTab,
    libraryNavController: NavHostController,
    searchNavController: NavHostController,
    settingsNavController: NavHostController,
    scaffoldPadding: PaddingValues,
    onNavigateToDownloads: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToSourceSettings: () -> Unit,
    onNavigateToSourceEditor: (SourceAccountId?, SourceEditorType?) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToLibraryFolderImport: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateToArtist: (Long) -> Unit,
    onNavigateToPlaylist: (Long) -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    stickyHeaderStateSink: StickyHeaderStateSink? = null,
) {
    val openSleepTimer = rememberOpenSleepTimer()
    val rootNavController = LocalNavController.current
    val detailArtworkAnimatedVisibilityScope = LocalDetailArtworkAnimatedVisibilityScope.current
    val tabTransition = updateTransition(targetState = currentTab, label = "homeTab")

    tabTransition.Crossfade { tab ->
        CompositionLocalProvider(
            LocalDetailArtworkAnimatedVisibilityScope provides
                detailArtworkAnimatedVisibilityScope.takeIf {
                    shouldEnableDetailArtworkSharedElements(
                        currentTab = tabTransition.currentState,
                        targetTab = tabTransition.targetState,
                        transitionRunning = tabTransition.isRunning,
                    )
                },
            LocalDesignStickyHeaderStateSink provides if (
                stickyHeaderStateSink == null || tab == currentTab
            ) {
                stickyHeaderStateSink
            } else {
                IgnoreStickyHeaderState
            },
        ) {
            when (tab) {
                HomeTab.HOME -> HomeRoot(
                    scaffoldPadding = scaffoldPadding,
                    onNavigateToDownloads = onNavigateToDownloads,
                    onNavigateToLibrary = onNavigateToLibrary,
                    onNavigateToSourceSettings = onNavigateToSourceSettings,
                    onNavigateToSearch = onNavigateToSearch,
                    onNavigateToListening = {
                        rootNavController.navigate(MusicGraph.Listening)
                    },
                    onNavigateToAlbum = onNavigateToAlbum,
                    onNavigateToArtist = onNavigateToArtist,
                    onNavigateToPlaylist = onNavigateToPlaylist,
                    onOpenSleepTimer = { openSleepTimer(SleepModeLeftTime(30 * 60 * 1000L)) },
                )
                HomeTab.SEARCH -> SearchTabGraph(
                    navController = searchNavController,
                    onNavigateToAlbum = onNavigateToAlbum,
                )
                HomeTab.LIBRARY -> LibraryTabGraph(
                    navController = libraryNavController,
                    onNavigateToLibraryFolderImport = onNavigateToLibraryFolderImport,
                    onNavigateToAlbum = onNavigateToAlbum,
                    onNavigateToArtist = onNavigateToArtist,
                    onNavigateToPlaylist = onNavigateToPlaylist,
                    onNavigateToFavorites = onNavigateToFavorites,
                    onNavigateToPlaylists = onNavigateToPlaylists,
                )
                HomeTab.SETTINGS -> SettingsTabGraph(
                    navController = settingsNavController,
                    appVersion = getAppVersion(),
                    appBuildInfo = getAppBuildInfo(),
                    gitCommitSha = getAppGitCommitSha(),
                    pluginSettingsContent = { onBack ->
                        PluginSettingsRoot(onBack = onBack)
                    },
                    onNavigateToSourcePathPicker = onNavigateToLibraryFolderImport,
                    onNavigateToSourceEditor = onNavigateToSourceEditor,
                )
            }
        }
    }
}

internal fun shouldEnableDetailArtworkSharedElements(
    currentTab: HomeTab,
    targetTab: HomeTab,
    transitionRunning: Boolean,
): Boolean = currentTab == targetTab && !transitionRunning

private object IgnoreStickyHeaderState : StickyHeaderStateSink {
    override fun update(owner: Any, state: StickyHeaderState) = Unit

    override fun clear(owner: Any) = Unit
}

internal class OwnedDesignStickyHeaderStateSink(
    private val onStateChange: (StickyHeaderState?) -> Unit,
) : StickyHeaderStateSink {
    private data class Registration(
        val owner: Any,
        val state: StickyHeaderState,
    )

    private val registrations = mutableListOf<Registration>()

    override fun update(owner: Any, state: StickyHeaderState) {
        val existingIndex = registrations.indexOfFirst { registration -> registration.owner === owner }
        if (existingIndex >= 0) {
            registrations[existingIndex] = Registration(owner, state)
            onStateChange(activeState())
            return
        }
        registrations += Registration(owner, state)
        onStateChange(activeState())
    }

    override fun clear(owner: Any) {
        val index = registrations.indexOfFirst { registration -> registration.owner === owner }
        if (index < 0) return
        val removedState = registrations[index].state
        registrations.removeAt(index)
        val nextState = activeState()
        if (nextState != null || removedState.transitionDurationMillis == 0) {
            onStateChange(nextState)
        }
    }

    private fun activeState(): StickyHeaderState? =
        registrations.lastOrNull { it.state.isNavigationTarget }?.state
            ?: registrations.lastOrNull()?.state
}

@Composable
internal fun LiquidGlassStickyHeaderHost(
    state: StickyHeaderState?,
    statusBarInset: Dp,
    modifier: Modifier = Modifier,
) {
    val transitionDurationMillis = state?.transitionDurationMillis ?: 0
    AnimatedContent(
        targetState = state?.transitionKey,
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
        transitionSpec = {
            fadeIn(tween(transitionDurationMillis)) togetherWith
                fadeOut(tween(transitionDurationMillis))
        },
        label = "stickyHeader",
    ) { transitionKey ->
        val retainedHeader = remember(transitionKey) {
            state?.takeIf { header -> header.transitionKey == transitionKey }
        }
        val header = state?.takeIf { it.transitionKey == transitionKey } ?: retainedHeader
        header?.let {
            LiquidGlassActionBar(
                title = it.title,
                subtitle = it.subtitle,
                collapseFraction = it.collapseFraction,
                statusBarInset = statusBarInset,
                onNavigateBack = it.onNavigateBack,
                backContentDescription = it.backContentDescription,
                actions = it.actions,
                centerTitle = true,
                compactTitle = it.compactTitle,
            )
        }
    }
}
