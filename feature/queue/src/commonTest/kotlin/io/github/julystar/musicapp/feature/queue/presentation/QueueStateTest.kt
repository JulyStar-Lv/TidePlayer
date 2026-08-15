package io.github.julystar.musicapp.feature.queue.presentation

import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import androidx.compose.ui.unit.dp
import io.github.julystar.musicapp.core.presentation.components.shouldDismissBottomSheet

class QueueStateTest {

    @Test
    fun `default state is empty`() {
        val state = QueueState()

        assertEquals(persistentListOf(), state.items)
        assertEquals(-1, state.currentIndex)
        assertFalse(state.isPlaying)
    }

    @Test
    fun `populated state preserves item data`() {
        val items = persistentListOf(
            QueueItemUi(index = 0, title = "Track 1", artist = "Artist A"),
            QueueItemUi(index = 1, title = "Track 2", artist = null),
        )
        val state = QueueState(items = items, currentIndex = 0, isPlaying = true)

        assertEquals(2, state.items.size)
        assertEquals(0, state.currentIndex)
        assertTrue(state.isPlaying)
        assertEquals("Track 1", state.items[0].title)
        assertTrue(state.items[0].index == state.currentIndex)
        assertFalse(state.items[1].index == state.currentIndex)
    }

    @Test
    fun `play item action carries index`() {
        val action = QueueAction.PlayItem(3)
        assertEquals(3, action.index)
    }

    @Test
    fun `queue item subtitle combines artist and album`() {
        val item = QueueItemUi(
            index = 0,
            title = "Midnight Cascade",
            artist = "Luna Waves",
            album = "Tidal Drift",
        )

        assertEquals("Luna Waves · Tidal Drift", item.subtitle())
        assertEquals(
            "Luna Waves",
            item.copy(album = null).subtitle(),
        )
    }

    @Test
    fun `row actions carry track and queue indices`() {
        assertEquals(42L, QueueAction.ToggleFavorite(42L).trackId)
        assertEquals(3, QueueAction.RemoveItem(3).index)
        assertEquals(1, QueueAction.MoveItem(fromIndex = 1, toIndex = 3).fromIndex)
        assertEquals(3, QueueAction.MoveItem(fromIndex = 1, toIndex = 3).toIndex)
    }

    @Test
    fun `row key follows the queue entry rather than its visual position`() {
        val item = QueueItemUi(index = 3, title = "T", artist = null)

        assertEquals(item.lazyListKey(), item.copy(title = "T2").lazyListKey())
        assertNotEquals(item.lazyListKey(), item.copy(index = 4).lazyListKey())
    }

    @Test
    fun `clear queue is a singleton action`() {
        assertEquals(QueueAction.ClearQueue, QueueAction.ClearQueue)
    }

    @Test
    fun `queue surface follows the Design player breakpoints`() {
        assertFalse(isQueueSideDialog(390.dp, 844.dp))
        assertFalse(isQueueSideDialog(840.dp, 900.dp))
        assertTrue(isQueueSideDialog(860.dp, 520.dp))
        assertTrue(isQueueSideDialog(1440.dp, 900.dp))
        assertTrue(isQueueSideDialog(844.dp, 390.dp))
        assertFalse(isQueueSideDialog(639.dp, 390.dp))
    }

    @Test
    fun `desktop queue covers the complete now playing lyrics column`() {
        assertEquals(525.88.dp, nowPlayingLyricsPanelWidth(1018.dp))
        assertEquals(753.76.dp, nowPlayingLyricsPanelWidth(1440.dp))
    }

    @Test
    fun `bottom queue sheet dismisses after enough downward distance or velocity`() {
        assertFalse(
            shouldDismissBottomSheet(
                dragOffsetPx = 71f,
                velocityPxPerSecond = 899f,
                distanceThresholdPx = 72f,
                velocityThresholdPxPerSecond = 900f,
            ),
        )
        assertTrue(
            shouldDismissBottomSheet(
                dragOffsetPx = 72f,
                velocityPxPerSecond = 0f,
                distanceThresholdPx = 72f,
                velocityThresholdPxPerSecond = 900f,
            ),
        )
        assertTrue(
            shouldDismissBottomSheet(
                dragOffsetPx = 12f,
                velocityPxPerSecond = 900f,
                distanceThresholdPx = 72f,
                velocityThresholdPxPerSecond = 900f,
            ),
        )
    }
}
