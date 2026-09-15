package io.github.julystar.musicapp.car.presentation.nowplaying

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.car.presentation.focus.CarFocusCoordinator
import io.github.julystar.musicapp.car.presentation.focus.CarFocusHost
import io.github.julystar.musicapp.car.presentation.focus.CarFocusIds
import io.github.julystar.musicapp.car.presentation.layout.CarLayoutProfileResolver
import io.github.julystar.musicapp.car.presentation.theme.CarTheme
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.model.ArtworkCacheKey
import io.github.julystar.musicapp.core.domain.model.CurrentTrackInfo
import io.github.julystar.musicapp.core.domain.model.FilterCriteria
import io.github.julystar.musicapp.core.domain.model.LibraryTrackItem
import io.github.julystar.musicapp.core.domain.model.RepositoryState
import io.github.julystar.musicapp.core.domain.model.SortCriteria
import io.github.julystar.musicapp.core.domain.repository.ArtworkRepository
import io.github.julystar.musicapp.core.domain.repository.FavoritesRepository
import io.github.julystar.musicapp.service.playback.domain.NowPlayingRepository
import io.github.julystar.musicapp.service.playback.domain.PlayableItem
import io.github.julystar.musicapp.service.playback.domain.PlaybackController
import io.github.julystar.musicapp.service.playback.domain.PlaybackPosition
import io.github.julystar.musicapp.service.playback.domain.PlaybackQueue
import io.github.julystar.musicapp.service.playback.domain.PlaybackStatus
import io.github.julystar.musicapp.service.playback.domain.PlayerState
import io.github.julystar.musicapp.service.playback.domain.RepeatMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.core.module.dsl.viewModelOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CarNowPlayingQueueComposeTest {
    @get:Rule
    val compose = createComposeRule()

    private val controller = QueueTestPlaybackController()

    @Before
    fun setUpKoin() {
        stopKoin()
        startKoin {
            modules(module {
                single<PlaybackController> { controller }
                single<NowPlayingRepository> { QueueTestNowPlayingRepository }
                single<ArtworkRepository> { QueueTestArtworkRepository }
                single<FavoritesRepository> { QueueTestFavoritesRepository }
                viewModelOf(::CarNowPlayingViewModel)
            })
        }
    }

    @After
    fun tearDownKoin() {
        stopKoin()
    }

    @Test
    fun queueOpensAndClosesFromQueueControl() {
        setContent()

        compose.onNodeWithText("暂无歌词").assertExists()
        compose.onNodeWithContentDescription("播放队列").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("正在播放").assertExists()
        compose.onNodeWithText("暂无歌词").assertDoesNotExist()

        compose.onNodeWithContentDescription("播放队列").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("暂无歌词").assertExists()
        compose.onNodeWithText("正在播放").assertDoesNotExist()
    }

    @Test
    fun systemBackClosesQueueAndRestoresLyricsPane() {
        val dispatcher = setContent()
        compose.onNodeWithContentDescription("播放队列").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("正在播放").assertExists()

        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("暂无歌词").assertExists()
        compose.onNodeWithText("正在播放").assertDoesNotExist()
    }

    @Test
    fun playbackModeControlCyclesListShuffleSingleAndBackToList() {
        setContent()

        compose.onNodeWithContentDescription("列表循环").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("列表循环").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("随机播放").assertExists()

        compose.onNodeWithContentDescription("随机播放").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("单曲循环").assertExists()

        compose.onNodeWithContentDescription("单曲循环").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("列表循环").assertExists()
    }

    private fun setContent(): OnBackPressedDispatcher {
        var dispatcher: OnBackPressedDispatcher? = null
        val metrics = CarLayoutProfileResolver().resolve(DpSize(320.dp, 470.dp))
        val coordinator = CarFocusCoordinator()
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            CarTheme(layoutMetrics = metrics) {
                CarFocusHost(coordinator, "now-playing-test", CarFocusIds.NowPlayingCollapse) {
                    CarNowPlayingScreen(
                        metrics = metrics,
                        focusCoordinator = coordinator,
                        onCollapse = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        compose.waitForIdle()
        return requireNotNull(dispatcher)
    }
}

private class QueueTestPlaybackController : PlaybackController {
    private val item = PlayableItem(title = "Queue Track", artist = "Queue Artist", libraryTrackId = 1)
    override val state = MutableStateFlow(PlayerState(item, PlaybackStatus.Paused))
    override val position = MutableStateFlow(PlaybackPosition(durationMs = 180_000))
    override val queue = MutableStateFlow(PlaybackQueue(listOf(item), 0))

    override suspend fun play(items: List<PlayableItem>, startIndex: Int) = Unit
    override fun play() = Unit
    override fun pause() = Unit
    override fun togglePlayPause() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun skipNext() = Unit
    override fun skipPrevious() = Unit
    override fun enqueueNext(item: PlayableItem) = Unit
    override fun setShuffle(enabled: Boolean) {
        state.value = state.value.copy(shuffleEnabled = enabled)
    }
    override fun setRepeatMode(mode: RepeatMode) {
        state.value = state.value.copy(repeatMode = mode)
    }
    override fun moveQueueItem(from: Int, to: Int) = Unit
    override fun removeQueueItem(index: Int) = Unit
    override fun clearQueue() = Unit
}

private object QueueTestNowPlayingRepository : NowPlayingRepository {
    override val currentTrackInfo: StateFlow<CurrentTrackInfo?> = MutableStateFlow(null)
    override val previousArtwork: StateFlow<Artwork?> = MutableStateFlow(null)
    override val nextArtwork: StateFlow<Artwork?> = MutableStateFlow(null)
    override val canPlayPrevious: StateFlow<Boolean> = MutableStateFlow(true)
    override val canPlayNext: StateFlow<Boolean> = MutableStateFlow(true)
    override fun removeCurrentTrack() = Unit
    override fun removeCurrentLyrics() = Unit
}

private object QueueTestArtworkRepository : ArtworkRepository {
    override fun cached(artwork: Artwork): ByteArray? = null
    override suspend fun cacheKey(artwork: Artwork): ArtworkCacheKey? = null
    override suspend fun load(artwork: Artwork): ByteArray? = null
}

private object QueueTestFavoritesRepository : FavoritesRepository {
    override val favoriteTrackIds: Flow<Set<Long>> = MutableStateFlow(emptySet())
    override val favoriteCount: Flow<Int> = MutableStateFlow(0)

    override fun favoriteTracks(
        sort: SortCriteria,
        filter: FilterCriteria.FavoritesFilter,
    ): Flow<RepositoryState<List<LibraryTrackItem>>> = MutableStateFlow(RepositoryState.Empty())

    override suspend fun isFavorite(trackId: Long) = false
    override suspend fun toggleFavorite(trackId: Long) = true
}
