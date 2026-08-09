package io.github.julystar.musicapp.navigation

import io.github.julystar.musicapp.core.presentation.components.DesignStickyHeaderState
import io.github.julystar.musicapp.core.presentation.layout.WindowSizeClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RootNavHostTest {

    @Test
    fun `root navigation exposes only the four primary destinations`() {
        assertEquals(
            listOf(HomeTab.HOME, HomeTab.SEARCH, HomeTab.LIBRARY, HomeTab.SETTINGS),
            HomeTab.entries.toList(),
        )
    }

    @Test
    fun `detail artwork shared elements are disabled while root tabs crossfade`() {
        assertTrue(
            shouldEnableDetailArtworkSharedElements(
                currentTab = HomeTab.LIBRARY,
                targetTab = HomeTab.LIBRARY,
                transitionRunning = false,
            ),
        )
        assertFalse(
            shouldEnableDetailArtworkSharedElements(
                currentTab = HomeTab.HOME,
                targetTab = HomeTab.LIBRARY,
                transitionRunning = false,
            ),
        )
        assertFalse(
            shouldEnableDetailArtworkSharedElements(
                currentTab = HomeTab.HOME,
                targetTab = HomeTab.LIBRARY,
                transitionRunning = true,
            ),
        )
        assertFalse(
            shouldEnableDetailArtworkSharedElements(
                currentTab = HomeTab.LIBRARY,
                targetTab = HomeTab.LIBRARY,
                transitionRunning = true,
            ),
        )
    }

    @Test
    fun `persistent navigation chrome is hidden on immersive routes`() {
        assertFalse(shouldShowPersistentMiniPlayer("Home"))
        assertFalse(shouldShowPersistentMiniPlayer("io.github.julystar.musicapp.MusicGraph.NowPlaying"))
        assertFalse(
            shouldShowPersistentMiniPlayer(
                "io.github.julystar.musicapp.core.presentation.navigation.MusicGraph.Lyrics/{id}",
            ),
        )
    }

    @Test
    fun `now playing and lyrics share immersive player transitions`() {
        assertTrue(isImmersivePlayerRoute("io.github.julystar.musicapp.MusicGraph.NowPlaying"))
        assertTrue(
            isImmersivePlayerRoute(
                "io.github.julystar.musicapp.core.presentation.navigation.MusicGraph.Lyrics/{id}",
            ),
        )
        assertFalse(isImmersivePlayerRoute("io.github.julystar.musicapp.MusicGraph.Album/{id}"))
    }

    @Test
    fun `album playlist and favorites routes use artwork-only detail transitions`() {
        listOf(
            "Album/{id}",
            "io.github.julystar.musicapp.core.presentation.navigation.MusicGraph.Album/{id}",
            "Playlist/{id}",
            "io.github.julystar.musicapp.core.presentation.navigation.MusicGraph.Playlist/{id}",
            "Favorites",
            "io.github.julystar.musicapp.core.presentation.navigation.MusicGraph.Favorites",
        ).forEach { route ->
            assertTrue(isArtworkDetailRoute(route), route)
        }

        assertFalse(isArtworkDetailRoute(null))
        assertFalse(isArtworkDetailRoute("Home"))
        assertFalse(isArtworkDetailRoute("Artist/{id}"))
        assertFalse(isArtworkDetailRoute("Playlists"))
    }

    @Test
    fun `persistent mini player is shown on secondary routes`() {
        listOf(
            "io.github.julystar.musicapp.MusicGraph.Album",
            "io.github.julystar.musicapp.MusicGraph.Artist",
            "io.github.julystar.musicapp.MusicGraph.Playlist",
            "io.github.julystar.musicapp.MusicGraph.Favorites",
            "io.github.julystar.musicapp.MusicGraph.Playlists",
            "io.github.julystar.musicapp.MusicGraph.EditStorage",
            "io.github.julystar.musicapp.MusicGraph.Import",
            "io.github.julystar.musicapp.MusicGraph.Downloads",
            "io.github.julystar.musicapp.MusicGraph.PluginSettings",
        ).forEach { route ->
            assertTrue(shouldShowPersistentMiniPlayer(route), route)
        }
    }

    @Test
    fun `detail routes reuse the secondary liquid glass header`() {
        listOf(
            "io.github.julystar.musicapp.core.presentation.navigation.MusicGraph.Home",
            "io.github.julystar.musicapp.core.presentation.navigation.MusicGraph.Album/{id}",
            "io.github.julystar.musicapp.core.presentation.navigation.MusicGraph.Playlist/{id}",
            "io.github.julystar.musicapp.core.presentation.navigation.MusicGraph.Favorites",
            "io.github.julystar.musicapp.core.presentation.navigation.MusicGraph.Listening",
            "io.github.julystar.musicapp.core.presentation.navigation.MusicGraph.PluginSettings",
        ).forEach { route ->
            assertTrue(shouldCaptureSecondaryStickyHeader(route), route)
        }
        assertFalse(
            shouldCaptureSecondaryStickyHeader(
                "io.github.julystar.musicapp.core.presentation.navigation.MusicGraph.Artist/{id}",
            ),
        )
    }

    @Test
    fun `secondary sticky header is hoisted only in compact windows`() {
        assertTrue(
            shouldHoistSecondaryStickyHeader(
                captureStickyHeader = true,
                windowSizeClass = WindowSizeClass.Compact,
            ),
        )
        listOf(
            WindowSizeClass.Medium,
            WindowSizeClass.Expanded,
            WindowSizeClass.Large,
            WindowSizeClass.XL,
        ).forEach { windowSizeClass ->
            assertFalse(
                shouldHoistSecondaryStickyHeader(
                    captureStickyHeader = true,
                    windowSizeClass = windowSizeClass,
                ),
                windowSizeClass.name,
            )
        }
        assertFalse(
            shouldHoistSecondaryStickyHeader(
                captureStickyHeader = false,
                windowSizeClass = WindowSizeClass.Compact,
            ),
        )
    }

    @Test
    fun `disposing outgoing page does not clear incoming sticky header`() {
        var currentState: DesignStickyHeaderState? = null
        val sink = OwnedDesignStickyHeaderStateSink { state -> currentState = state }
        val outgoingOwner = Any()
        val incomingOwner = Any()
        val outgoingState = DesignStickyHeaderState(
            title = "Settings",
            subtitle = null,
            collapseFraction = 1f,
        )
        val incomingState = DesignStickyHeaderState(
            title = "Appearance",
            subtitle = null,
            collapseFraction = 1f,
        )

        sink.update(outgoingOwner, outgoingState)
        sink.update(incomingOwner, incomingState)
        sink.update(outgoingOwner, outgoingState)

        assertEquals(incomingState, currentState)

        sink.clear(outgoingOwner)

        assertEquals(incomingState, currentState)

        sink.clear(incomingOwner)
        assertNull(currentState)
    }
}
