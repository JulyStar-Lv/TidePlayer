package io.github.julystar.musicapp.plugin.management

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import io.github.julystar.musicapp.source.api.MetaSongCandidate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/** Owned by the dialog so recycling a result row cannot cancel or discard its cover. */
internal class ManualMetadataCoverPreviewCache(
    private val scope: CoroutineScope,
    private val loader: suspend (MetaSongCandidate) -> ImageBitmap?,
) {
    private val previews = mutableStateMapOf<String, ImageBitmap?>()
    private val requests = mutableMapOf<String, Deferred<ImageBitmap?>>()

    fun preview(candidate: MetaSongCandidate): ImageBitmap? = previews[candidate.pictureUrl?.trim()]

    suspend fun load(candidate: MetaSongCandidate): ImageBitmap? {
        val url = candidate.pictureUrl?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (previews.containsKey(url)) return previews[url]
        val request = requests[url]?.takeUnless { it.isCancelled } ?: scope.async {
            loader(candidate).also { previews[url] = it }
        }.also { requests[url] = it }
        return request.await()
    }
}
