package io.github.julystar.musicapp.car.presentation.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.car.presentation.theme.CarTheme
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.model.ArtworkCacheKey
import io.github.julystar.musicapp.core.domain.repository.ArtworkRepository
import io.github.julystar.musicapp.service.playback.domain.PlayableItem
import io.github.julystar.musicapp.service.playback.domain.PlaybackStatus
import io.github.julystar.musicapp.service.playback.domain.PlayerState
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CarMiniPlayerComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun playingItemExposesPauseAndDispatchesEveryControl() {
        val actions = mutableListOf<String>()
        compose.setContent {
            CarTheme {
                CarMiniPlayer(
                    state = PlayerState(
                        currentItem = PlayableItem(
                            title = "Now Playing Track",
                            artist = "Test Artist",
                            libraryTrackId = 42,
                        ),
                        status = PlaybackStatus.Playing,
                    ),
                    artworkRepository = EmptyArtworkRepository,
                    height = 164.dp,
                    controlSize = 48.dp,
                    onOpen = { actions += "open" },
                    onPrevious = { actions += "previous" },
                    onToggle = { actions += "toggle" },
                    onNext = { actions += "next" },
                    modifier = Modifier.testTag("mini-player"),
                )
            }
        }

        compose.onNodeWithTag("mini-player").assertTextContains("Now Playing Track").performClick()
        compose.onNodeWithContentDescription("上一首").performClick()
        compose.onNodeWithContentDescription("暂停").performClick()
        compose.onNodeWithContentDescription("下一首").performClick()

        assertEquals(listOf("open", "previous", "toggle", "next"), actions)
    }

    @Test
    fun pausedItemExposesPlayAndDispatchesToggle() {
        var toggleCount = 0
        compose.setContent {
            CarTheme {
                CarMiniPlayer(
                    state = PlayerState(
                        currentItem = PlayableItem(title = "Paused Track", libraryTrackId = 43),
                        status = PlaybackStatus.Paused,
                    ),
                    artworkRepository = EmptyArtworkRepository,
                    height = 164.dp,
                    controlSize = 48.dp,
                    onOpen = {},
                    onPrevious = {},
                    onToggle = { toggleCount++ },
                    onNext = {},
                )
            }
        }

        compose.onNodeWithContentDescription("播放").performClick()
        assertEquals(1, toggleCount)
    }

    @Test
    fun emptyPlayerDisablesOpenAndPlaybackControls() {
        var actionCount = 0
        compose.setContent {
            CarTheme {
                CarMiniPlayer(
                    state = PlayerState(),
                    artworkRepository = EmptyArtworkRepository,
                    height = 164.dp,
                    controlSize = 48.dp,
                    onOpen = { actionCount++ },
                    onPrevious = { actionCount++ },
                    onToggle = { actionCount++ },
                    onNext = { actionCount++ },
                    modifier = Modifier.testTag("mini-player"),
                )
            }
        }

        compose.onNodeWithTag("mini-player").assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("上一首").assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("播放").assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("下一首").assertIsNotEnabled().performClick()
        assertEquals(0, actionCount)
    }

    @Test
    fun compactVehiclePlayerShowsArtistWithoutTitleOrTransportControls() {
        var openCount = 0
        compose.setContent {
            CarTheme {
                CarMiniPlayer(
                    state = PlayerState(
                        currentItem = PlayableItem(
                            title = "Vehicle Track",
                            libraryTrackId = 44,
                        ),
                        status = PlaybackStatus.Paused,
                    ),
                    artworkRepository = EmptyArtworkRepository,
                    height = 164.dp,
                    controlSize = 48.dp,
                    compact = true,
                    artist = "Vehicle Artist",
                    onOpen = { openCount++ },
                    onPrevious = {},
                    onToggle = {},
                    onNext = {},
                    modifier = Modifier.testTag("compact-mini-player"),
                )
            }
        }

        compose.onNodeWithTag("compact-mini-player").performClick()
        compose.onNodeWithContentDescription("上一首").assertDoesNotExist()
        compose.onNodeWithContentDescription("播放").assertDoesNotExist()
        compose.onNodeWithContentDescription("下一首").assertDoesNotExist()
        compose.onNodeWithText("Vehicle Track").assertDoesNotExist()
        compose.onNodeWithText("Vehicle Artist").assertExists()
        assertEquals(1, openCount)
    }
}

private object EmptyArtworkRepository : ArtworkRepository {
    override fun cached(artwork: Artwork): ByteArray? = null

    override suspend fun cacheKey(artwork: Artwork): ArtworkCacheKey? = null

    override suspend fun load(artwork: Artwork): ByteArray? = null
}
