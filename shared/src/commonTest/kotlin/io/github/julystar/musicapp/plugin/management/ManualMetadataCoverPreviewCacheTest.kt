package io.github.julystar.musicapp.plugin.management

import androidx.compose.ui.graphics.ImageBitmap
import io.github.julystar.musicapp.source.api.MetaSongCandidate
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class ManualMetadataCoverPreviewCacheTest {
    @Test
    fun recycledRowsReuseDecodedCoversAndShareTheSameUrlRequest() = runTest {
        val cover = ImageBitmap(1, 1)
        val candidate = MetaSongCandidate("1", "Song", pictureUrl = "https://example.test/cover.jpg")
        var requests = 0
        val cache = ManualMetadataCoverPreviewCache(this) {
            requests++
            delay(100)
            cover
        }
        val firstRow = async { cache.load(candidate) }
        val sameAlbumRow = async { cache.load(candidate.copy(id = "2")) }
        runCurrent()
        // Disposing a LazyColumn row cancels its waiter, not the dialog's cover download.
        firstRow.cancelAndJoin()
        assertSame(cover, sameAlbumRow.await())
        assertSame(cover, cache.preview(candidate))
        assertSame(cover, cache.load(candidate))
        assertEquals(1, requests)
    }

    @Test
    fun canceledDialogRequestCanBeRetriedOnReopen() = runTest {
        val candidate = MetaSongCandidate("1", "Song", pictureUrl = "https://example.test/cover.jpg")
        val scopeJob = kotlinx.coroutines.Job()
        val scope = kotlinx.coroutines.CoroutineScope(coroutineContext + scopeJob)
        var requests = 0
        val cache = ManualMetadataCoverPreviewCache(scope) {
            requests++
            delay(100)
            null
        }
        val firstRow = async { cache.load(candidate) }
        runCurrent()
        scopeJob.children.forEach { it.cancel() }
        firstRow.cancelAndJoin()
        cache.load(candidate)
        assertEquals(2, requests)
        scopeJob.cancel()
    }
}
