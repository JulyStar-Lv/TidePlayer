package io.github.julystar.musicapp.widgets.appbar

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asSkiaBitmap
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.model.PlaylistSummary
import io.github.julystar.musicapp.core.presentation.media.ArtworkImageLoader
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.jetbrains.skia.Image
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.navigation.HomeTab
import io.github.julystar.musicapp.core.presentation.theme.AppTheme
import io.github.julystar.musicapp.core.presentation.theme.LocalDesignIsDarkTheme
import io.github.julystar.musicapp.core.presentation.components.LiquidGlassOverlayScene
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DesktopNavigationTest {
    @Test
    fun sidebarShowsLivePlaylistsAndSelectsTheirRows() = runComposeUiTest {
        fun playlist(id: Long, title: String) = PlaylistSummary(id, title, 0, 0, Artwork.LibraryPlaylist(id))
        var playlists by mutableStateOf(listOf(playlist(1, "Demo"), playlist(2, "Demo2")))
        var selectedId by mutableStateOf<Long?>(null)
        val blue = ImageBitmap(24, 24).also { Canvas(it).drawRect(0f, 0f, 24f, 24f, Paint().apply { color = Color.Blue }) }
        startKoin {
            modules(module {
                single<ArtworkImageLoader> {
                    object : ArtworkImageLoader {
                        override fun cachedBitmap(artwork: Artwork) = blue
                        override suspend fun loadBitmap(artwork: Artwork) = blue
                    }
                }
            })
        }
        try {
            setContent {
                AppTheme(darkTheme = true, manageSystemBars = false) {
                    CompositionLocalProvider(LocalDesignIsDarkTheme provides true) {
                        DesktopNavigationSidebar(
                            selectedDestination = DesktopSidebarDestination.ALL_PLAYLISTS,
                            onDestinationSelected = {},
                            modifier = Modifier.height(500.dp),
                            playlists = playlists,
                            selectedPlaylistId = selectedId,
                            onPlaylistSelected = { selectedId = it },
                        )
                    }
                }
            }
            onNodeWithTag("desktop-sidebar-playlist-1").assertIsDisplayed().performClick()
            assertEquals(1L, selectedId)
            onNodeWithTag("desktop-sidebar-playlist-1").assertIsSelected()
            onNodeWithTag("apple-music-sidebar-all_playlists").assertIsNotSelected()
            runOnIdle { playlists = playlists + playlist(3, "New playlist") }
            onNodeWithTag("desktop-sidebar-playlist-3").assertIsDisplayed().performClick()
            assertEquals(3L, selectedId)
            onNodeWithTag("desktop-sidebar-playlist-3").assertIsSelected()
            val row = onNodeWithTag("desktop-sidebar-playlist-3").captureToImage()
            val pixels = row.toPixelMap()
            assertTrue(pixels[25, pixels.height / 2].blue > pixels[25, pixels.height / 2].red + 0.5f)
            val output = File("build/reports/sidebar-playlist.png")
            output.parentFile.mkdirs()
            output.writeBytes(Image.makeFromBitmap(row.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
            runOnIdle { playlists = playlists.filter { it.id != 1L } }
            onNodeWithTag("desktop-sidebar-playlist-1").assertDoesNotExist()
        } finally {
            stopKoin()
        }
    }

    @Test
    fun sidebarMaterialAdoptsNearbyContentColor() = runComposeUiTest {
        setContent {
            val windowInfo = LocalWindowInfo.current
            val focusedWindowInfo = remember(windowInfo) {
                object : WindowInfo by windowInfo {
                    override val isWindowFocused: Boolean = true
                }
            }
            CompositionLocalProvider(
                LocalWindowInfo provides focusedWindowInfo,
                LocalDesignIsDarkTheme provides false,
            ) {
                MiuixTheme {
                    LiquidGlassOverlayScene(
                        modifier = Modifier.size(width = 420.dp, height = 500.dp),
                        backdropContent = {
                            Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = 272.dp, y = 210.dp)
                                        .size(width = 128.dp, height = 180.dp)
                                        .background(Color(0xFF007AFF)),
                                )
                            }
                        },
                        overlayContent = {
                            DesktopNavigationSidebar(
                                selectedDestination = DesktopSidebarDestination.HOME,
                                onDestinationSelected = {},
                                modifier = Modifier.height(500.dp),
                            )
                        },
                    )
                }
            }
        }

        val surface = onNodeWithTag("apple-music-sidebar-surface").captureToImage().toPixelMap()
        val farEdge = surface[12, surface.height * 3 / 5]
        val contentEdge = surface[surface.width - 8, surface.height * 3 / 5]
        assertTrue(
            contentEdge.blue - contentEdge.red > farEdge.blue - farEdge.red + 0.01f,
            "Nearby blue content should cast a subtle cool tint into the sidebar material",
        )
    }

    @Test
    fun sidebarMutesAndRestoresForegroundsWhenLightWindowFocusChanges() = verifyFocusColors(false)

    @Test
    fun sidebarMutesAndRestoresForegroundsWhenDarkWindowFocusChanges() = verifyFocusColors(true)

    private fun verifyFocusColors(darkTheme: Boolean) = runComposeUiTest {
        var focused by mutableStateOf(true)
        var clicked: DesktopSidebarDestination? = null
        setContent {
            val windowInfo = LocalWindowInfo.current
            val controlledWindowInfo = remember(windowInfo) {
                object : WindowInfo by windowInfo {
                    override val isWindowFocused: Boolean get() = focused
                }
            }
            CompositionLocalProvider(
                LocalWindowInfo provides controlledWindowInfo,
                LocalDesignIsDarkTheme provides darkTheme,
            ) {
                AppTheme(darkTheme = darkTheme, manageSystemBars = false) {
                    DesktopNavigationSidebar(
                        selectedDestination = DesktopSidebarDestination.HOME,
                        onDestinationSelected = { clicked = it },
                    )
                }
            }
        }
        val home = onNodeWithTag("apple-music-sidebar-home")
        val settings = onNodeWithTag("apple-music-sidebar-settings")
        val surface = onNodeWithTag("apple-music-sidebar-surface")
        val focusedHome = home.captureToImage()
        val focusedSettings = settings.captureToImage()
        val focusedSurface = surface.captureToImage().backgroundColor()
        assertTrue(focusedHome.hasRedPixels(iconOnly = true))
        assertTrue(focusedHome.hasRedPixels(iconOnly = false))

        runOnIdle { focused = false }
        val unfocusedHome = home.captureToImage()
        val unfocusedSurface = surface.captureToImage().backgroundColor()
        assertFalse(unfocusedHome.hasRedPixels(iconOnly = true))
        assertFalse(unfocusedHome.hasRedPixels(iconOnly = false))
        assertFalse(focusedSettings.pixels().contentEquals(settings.captureToImage().pixels()))
        if (darkTheme) {
            assertTrue(unfocusedSurface.luminance() > focusedSurface.luminance())
        } else {
            assertTrue(unfocusedSurface.luminance() < focusedSurface.luminance())
        }
        home.assertIsSelected()

        runOnIdle { focused = true }
        assertTrue(focusedHome.pixels().contentEquals(home.captureToImage().pixels()), "Selected colors restore")
        assertTrue(focusedSettings.pixels().contentEquals(settings.captureToImage().pixels()), "Normal colors restore")

        runOnIdle { focused = false }
        settings.performClick()
        assertEquals(DesktopSidebarDestination.SETTINGS, clicked)
    }

    @Test
    fun expandedSidebarUsesDesktopShellWithoutBranding() = runComposeUiTest {
        setContent {
            MiuixTheme {
                HomeNavigationRail(
                    currentTab = HomeTab.HOME,
                    onTabSelected = {},
                    expanded = true,
                )
            }
        }

        assertEquals(208.dp, getHomeNavigationRailWidth(expanded = true))
        onNodeWithTag("apple-music-sidebar-home").assertIsSelected()
        onAllNodesWithTag("apple-music-sidebar-radio").assertCountEquals(0)
        onAllNodesWithTag("apple-music-sidebar-itunes_store").assertCountEquals(0)
        onAllNodesWithText("Tide Player").assertCountEquals(0)
    }

    @Test
    fun expandedSidebarDispatchesDesktopDestination() = runComposeUiTest {
        var selectedDestination = DesktopSidebarDestination.HOME
        setContent {
            MiuixTheme {
                HomeNavigationRail(
                    currentTab = HomeTab.HOME,
                    onTabSelected = {},
                    expanded = true,
                    selectedDesktopDestination = selectedDestination,
                    onDesktopDestinationSelected = { selectedDestination = it },
                )
            }
        }

        onNodeWithTag("apple-music-sidebar-settings").performClick()
        assertEquals(DesktopSidebarDestination.SETTINGS, selectedDestination)
    }
}

private fun ImageBitmap.pixels(): IntArray = IntArray(width * height).also { readPixels(it) }

private fun ImageBitmap.backgroundColor() = toPixelMap()[width / 2, height - 12]

private fun ImageBitmap.hasRedPixels(iconOnly: Boolean): Boolean {
    val pixels = toPixelMap()
    // The 180 dp row contains the icon at 15–39 dp and the label starting at 45 dp.
    val split = width * 42 / 180
    val columns = if (iconOnly) 0 until split else split until width
    return (0 until height).any { y ->
        columns.any { x ->
            val color = pixels[x, y]
            color.red > color.green + 0.15f && color.red > color.blue + 0.15f
        }
    }
}
