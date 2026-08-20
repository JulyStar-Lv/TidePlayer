package io.github.julystar.musicapp.core.data

import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.database.LyricsEntity
import io.github.julystar.musicapp.database.MetadataDao
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.SourceAccountDao
import io.github.julystar.musicapp.database.SourceItemDao
import io.github.julystar.musicapp.database.TrackSourceRefDao
import io.github.julystar.musicapp.platform.currentTimeMillis
import io.github.julystar.musicapp.source.api.RemoteServerGateway
import io.github.julystar.musicapp.source.api.RemoteServerKind
import kotlinx.coroutines.CancellationException

class NavidromeLyricsResolver(
    private val metadataDao: MetadataDao,
    private val trackSourceRefDao: TrackSourceRefDao,
    private val sourceItemDao: SourceItemDao,
    private val sourceAccountDao: SourceAccountDao,
    private val remoteServerGateway: RemoteServerGateway,
) {
    suspend fun load(trackId: Long, title: String?, artist: String?): io.github.julystar.musicapp.source.api.RemoteServerLyrics? {
        val cached = metadataDao.getLyricsCandidates(trackId)
            .firstOrNull { it.sourceKind == NAVIDROME_LYRICS_SOURCE && it.content.isNotBlank() }
        for (ref in trackSourceRefDao.findByTrackId(trackId)) {
            if (!ref.isAvailable) continue
            val item = sourceItemDao.get(ref.sourceItemId) ?: continue
            if (item.isDeleted) continue
            val account = sourceAccountDao.get(item.sourceAccountId) ?: continue
            if (account.providerType != ProviderTypes.Navidrome || !account.enabled) continue
            val remoteId = item.providerItemId?.takeIf(String::isNotBlank) ?: continue
            cached?.let {
                return io.github.julystar.musicapp.source.api.RemoteServerLyrics(
                    content = it.content,
                    format = it.format,
                    synchronized = it.synchronized,
                )
            }
            val result = try {
                remoteServerGateway.lyrics(
                    RemoteServerKind.Navidrome,
                    SourceAccountId("storage:${item.sourceAccountId}"),
                    artist.orEmpty(),
                    title.orEmpty(),
                ).getOrNull()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            } ?: continue
            if (result.content.isBlank()) continue
            try {
                metadataDao.upsertLyrics(
                listOf(
                    LyricsEntity(
                        trackId = trackId,
                        format = result.format ?: "plain",
                        language = null,
                        synchronized = result.synchronized,
                        content = result.content,
                        sourcePath = "navidrome/${item.sourceAccountId}/$remoteId",
                        updatedAt = currentTimeMillis(),
                        sourceKind = NAVIDROME_LYRICS_SOURCE,
                    )
                )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                continue
            }
            return result
        }
        return null
    }
}

private const val NAVIDROME_LYRICS_SOURCE = "Navidrome"
