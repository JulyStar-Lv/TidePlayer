package io.github.julystar.musicapp.plugin.management

import androidx.compose.runtime.mutableStateMapOf
import io.github.julystar.musicapp.source.api.MetaLyrics
import io.github.julystar.musicapp.source.api.MetaLyricsCandidate
import io.github.julystar.musicapp.source.api.MetaSongCandidate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal data class ManualMetadataLyricsPreview(
    val lyrics: MetaLyricsCandidate? = null,
    val failed: Boolean = false,
)

internal enum class ManualMetadataLyricsType { TTML, WORD, LINE, TEXT }

internal fun MetaLyrics.previewType(): ManualMetadataLyricsType? = when {
    !rawTtml.isNullOrBlank() -> ManualMetadataLyricsType.TTML
    !rawMultiPersonEnhancedLrc.isNullOrBlank() || !rawEnhancedLrc.isNullOrBlank() ||
        !rawVerbatimLrc.isNullOrBlank() || lines.any { line -> line.words.any { it.startMs != null } } ->
        ManualMetadataLyricsType.WORD
    lines.any { it.startMs != null && it.text.isNotBlank() } ||
        rawPlainLrc?.contains(Regex("\\[\\d{1,3}:\\d{2}")) == true -> ManualMetadataLyricsType.LINE
    !rawPlainLrc.isNullOrBlank() || lines.any { it.text.isNotBlank() } -> ManualMetadataLyricsType.TEXT
    else -> null
}

internal fun rankManualMetadataResultsByLyrics(
    results: List<ManualMetadataResult>,
    preview: (MetaSongCandidate) -> ManualMetadataLyricsPreview?,
): List<ManualMetadataResult> = results.sortedBy { result ->
    val status = preview(result.song)
    if (status != null && (status.failed || status.lyrics?.lyrics?.previewType() == null)) 1 else 0
}

/** Dialog-owned checks survive row recycling and do not block song search. */
internal class ManualMetadataLyricsPreviewCache(
    private val scope: CoroutineScope,
    private val loader: suspend (MetaSongCandidate) -> MetaLyricsCandidate?,
) {
    private val previews = mutableStateMapOf<MetaSongCandidate, ManualMetadataLyricsPreview>()
    private val requests = mutableMapOf<MetaSongCandidate, Deferred<ManualMetadataLyricsPreview>>()
    private val permits = Semaphore(2)

    fun preview(candidate: MetaSongCandidate): ManualMetadataLyricsPreview? = previews[candidate]

    suspend fun load(candidate: MetaSongCandidate): ManualMetadataLyricsPreview {
        previews[candidate]?.let { return it }
        val request = requests[candidate]?.takeUnless { it.isCancelled } ?: scope.async {
            val preview = try {
                permits.withPermit { ManualMetadataLyricsPreview(loader(candidate)) }
            } catch (_: TimeoutCancellationException) {
                ManualMetadataLyricsPreview(failed = true)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                ManualMetadataLyricsPreview(failed = true)
            }
            preview.also { previews[candidate] = it }
        }.also { requests[candidate] = it }
        return request.await()
    }
}
