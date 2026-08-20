package io.github.julystar.musicapp.core.data

import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.database.LyricsEntity
import io.github.julystar.musicapp.database.MetadataDao
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.database.SourceAccountDao
import io.github.julystar.musicapp.database.SourceItemDao
import io.github.julystar.musicapp.database.TrackSourceRefDao
import io.github.julystar.musicapp.platform.currentTimeMillis
import io.github.julystar.musicapp.plugin.management.toEntity
import io.github.julystar.musicapp.source.api.MetaLyricLine
import io.github.julystar.musicapp.source.api.MetaLyricWord
import io.github.julystar.musicapp.source.api.MetaLyrics
import io.github.julystar.musicapp.source.api.OpenSubsonicLyricsTrackKind
import io.github.julystar.musicapp.source.api.OpenSubsonicLyricsUnsupportedException
import io.github.julystar.musicapp.source.api.OpenSubsonicStructuredLyricsDocument
import io.github.julystar.musicapp.source.api.RemoteServerGateway
import io.github.julystar.musicapp.source.api.RemoteServerKind
import io.github.julystar.musicapp.source.api.RemoteServerLyrics
import io.github.julystar.musicapp.source.server.OpenSubsonicLyricsCodec
import kotlinx.coroutines.CancellationException
import uniffi.app_backend.RemoteMusicException

class OpenSubsonicLyricsResolver(
    private val metadataDao: MetadataDao,
    private val trackSourceRefDao: TrackSourceRefDao,
    private val sourceItemDao: SourceItemDao,
    private val sourceAccountDao: SourceAccountDao,
    private val remoteServerGateway: RemoteServerGateway,
) {
    suspend fun load(trackId: Long, title: String?, artist: String?): RemoteServerLyrics? {
        for (ref in trackSourceRefDao.findByTrackId(trackId)) {
            if (!ref.isAvailable) continue
            val item = sourceItemDao.get(ref.sourceItemId) ?: continue
            if (item.isDeleted) continue
            val account = sourceAccountDao.get(item.sourceAccountId) ?: continue
            if (account.providerType != ProviderTypes.OpenSubsonic || !account.enabled) continue
            val remoteId = item.providerItemId?.takeIf(String::isNotBlank) ?: continue
            val sourcePath = "openSubsonic/${item.sourceAccountId}/$remoteId"
            metadataDao.getLyricsCandidates(trackId)
                .firstOrNull { it.sourceKind == OPEN_SUBSONIC_LYRICS_SOURCE && it.sourcePath == sourcePath }
                ?.takeIf { it.content.isNotBlank() }
                ?.let { cached ->
                    return RemoteServerLyrics(
                        content = cached.content,
                        format = cached.format,
                        synchronized = cached.synchronized,
                        structuredDocument = cached.structuredContent?.let(OpenSubsonicLyricsCodec::decode),
                    )
                }

            val document = try {
                remoteServerGateway.openSubsonicLyrics(
                    RemoteServerKind.OpenSubsonic,
                    SourceAccountId("storage:${item.sourceAccountId}"),
                    remoteId,
                ).fold(
                    onSuccess = { it },
                    onFailure = { error ->
                        if (error is OpenSubsonicLyricsUnsupportedException) null else throw error
                    },
                )
            } catch (error: CancellationException) {
                throw error
            }

            val projected = document?.toRemoteServerLyrics(trackId)
            val result = projected ?: try {
                remoteServerGateway.lyrics(
                    RemoteServerKind.OpenSubsonic,
                    SourceAccountId("storage:${item.sourceAccountId}"),
                    artist.orEmpty(),
                    title.orEmpty(),
                ).getOrThrow()
            } catch (error: CancellationException) {
                throw error
            } catch (error: RemoteMusicException.Unauthorized) {
                throw error
            } catch (error: RemoteMusicException.PermissionDenied) {
                throw error
            } catch (_: Throwable) {
                continue
            }

            if (result.content.isBlank()) continue
            try {
                val generated = document?.toMetaLyrics()?.toEntity(trackId, currentTimeMillis())
                val entity = (generated ?: LyricsEntity(
                    trackId = trackId,
                    format = result.format ?: "plain",
                    language = null,
                    synchronized = result.synchronized,
                    content = result.content,
                    sourcePath = sourcePath,
                    updatedAt = currentTimeMillis(),
                    sourceKind = OPEN_SUBSONIC_LYRICS_SOURCE,
                )).copy(
                    language = document?.mainLanguageForDisplay(),
                    sourcePath = sourcePath,
                    sourceKind = OPEN_SUBSONIC_LYRICS_SOURCE,
                    structuredContent = document?.let(OpenSubsonicLyricsCodec::encode),
                )
                metadataDao.upsertLyrics(listOf(entity))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // A remote lyric may still be displayed if its best-effort cache write fails.
            }
            return result
        }
        return null
    }
}

private const val OPEN_SUBSONIC_LYRICS_SOURCE = "OpenSubsonic"

private fun OpenSubsonicStructuredLyricsDocument.toRemoteServerLyrics(trackId: Long): RemoteServerLyrics? {
    val entity = toMetaLyrics().toEntity(trackId, currentTimeMillis()) ?: return null
    return RemoteServerLyrics(
        content = entity.content,
        format = entity.format,
        synchronized = entity.synchronized,
        structuredDocument = this,
    )
}

private fun OpenSubsonicStructuredLyricsDocument.mainLanguageForDisplay(): String? =
    tracks.firstOrNull { it.kind == OpenSubsonicLyricsTrackKind.Main }
        ?.language
        ?.takeUnless { it.equals("xxx", ignoreCase = true) }

private fun OpenSubsonicStructuredLyricsDocument.toMetaLyrics(): MetaLyrics {
    val main = tracks.firstOrNull { it.kind == OpenSubsonicLyricsTrackKind.Main } ?: tracks.firstOrNull()
    if (main == null) return MetaLyrics()
    val translation = tracks.firstOrNull { it.kind == OpenSubsonicLyricsTrackKind.Translation }
    val pronunciation = tracks.firstOrNull { it.kind == OpenSubsonicLyricsTrackKind.Pronunciation }
    val mainOffset = main.offsetMs ?: 0L
    val count = maxOf(main.lines.size, main.cueLines.maxOfOrNull { it.index + 1 } ?: 0)
    val lines = (0 until count).mapNotNull { index ->
        val plain = main.lines.getOrNull(index)
        val cueLine = main.mainCueLine(index)
        val text = plain?.value ?: cueLine?.value ?: return@mapNotNull null
        val startMs = (plain?.startMs ?: cueLine?.startMs)?.plus(mainOffset)
        val translated = translation?.alignedText(index, startMs)
        val romanized = pronunciation?.alignedText(index, startMs)
        MetaLyricLine(
            text = text,
            startMs = startMs,
            endMs = cueLine?.endMs?.plus(mainOffset),
            words = cueLine?.cues.orEmpty().map { cue ->
                MetaLyricWord(cue.value, cue.startMs?.plus(mainOffset), cue.endMs?.plus(mainOffset))
            },
            translation = listOfNotNull(translated, romanized).joinToString("\n").takeIf(String::isNotBlank),
            person = cueLine?.agentId,
        )
    }
    return MetaLyrics(lines = lines)
}

private fun io.github.julystar.musicapp.source.api.OpenSubsonicLyricsTrack.mainCueLine(index: Int) =
    cueLines.filter { it.index == index }.firstOrNull { cue ->
        agents.firstOrNull { it.id == cue.agentId }?.role.equals("main", ignoreCase = true)
    } ?: cueLines.firstOrNull { it.index == index }

private fun io.github.julystar.musicapp.source.api.OpenSubsonicLyricsTrack.alignedText(
    index: Int,
    absoluteStartMs: Long?,
): String? {
    val offset = offsetMs ?: 0L
    val indexedLine = lines.getOrNull(index)
    val indexedCue = cueLines.firstOrNull { it.index == index }
    if (synced != true) return indexedLine?.value ?: indexedCue?.value
    if (absoluteStartMs == null) return null
    val candidate = sequenceOf(
        lines.mapNotNull { line -> line.startMs?.let { (it + offset) to line.value } },
        cueLines.mapNotNull { line -> line.startMs?.let { (it + offset) to line.value } },
    ).flatten().minByOrNull { (start, _) -> kotlin.math.abs(start - absoluteStartMs) }
    return candidate?.takeIf { kotlin.math.abs(it.first - absoluteStartMs) <= 250L }?.second
}
