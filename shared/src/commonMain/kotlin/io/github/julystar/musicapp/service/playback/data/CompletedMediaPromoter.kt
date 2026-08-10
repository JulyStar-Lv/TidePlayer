package io.github.julystar.musicapp.service.playback.data

import io.github.julystar.musicapp.core.domain.model.DiagnosticLogCategory
import io.github.julystar.musicapp.core.domain.model.storageSourceAccountId
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.ProviderTypes
import io.github.julystar.musicapp.diagnostics.AppLogger
import io.github.julystar.musicapp.service.download.domain.DownloadFinalizationRequest
import io.github.julystar.musicapp.service.download.domain.DownloadFinalizationResult
import io.github.julystar.musicapp.service.download.domain.DownloadFinalizer
import io.github.julystar.musicapp.source.api.BuiltInSourceIds
import io.github.julystar.musicapp.source.api.legacyStorageTrackMediaId
import kotlinx.coroutines.CancellationException
import uniffi.app_backend.PlaybackCacheOptions
import uniffi.app_backend.PlaybackCachePromotionStatus
import uniffi.app_backend.ctPromoteCompletedPlaybackCache

data class CompletedPlaybackCache(
    val identity: PlaybackCacheIdentity,
    val mimeType: String?,
    val cacheDirectory: String,
    val extension: String,
)

fun interface CompletedMediaPromoter {
    suspend fun promote(cache: CompletedPlaybackCache)

    data object Disabled : CompletedMediaPromoter {
        override suspend fun promote(cache: CompletedPlaybackCache) = Unit
    }
}

class CompletedPlaybackCachePromoter(
    private val database: AppDatabase,
    private val downloadFinalizer: DownloadFinalizer,
    private val destinationDirectory: String,
) : CompletedMediaPromoter {
    override suspend fun promote(cache: CompletedPlaybackCache) {
        val promoted = try {
            ctPromoteCompletedPlaybackCache(
                cacheOptions = PlaybackCacheOptions(
                    directory = cache.cacheDirectory,
                    key = cache.identity.key,
                    extension = cache.extension,
                    writeEnabled = false,
                    maxBytes = 0u,
                ),
                destinationDirectory = destinationDirectory,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            AppLogger.warn(
                DiagnosticLogCategory.Cache,
                LOG_TARGET,
                "Completed playback cache promotion failed",
                detail = error.message?.take(240),
            )
            return
        }
        if (promoted.status == PlaybackCachePromotionStatus.PARTIAL) return
        val path = promoted.path ?: return
        val account = database.sourceAccountDao().get(cache.identity.storageId) ?: return
        val sourceId = when (account.providerType) {
            ProviderTypes.Local -> BuiltInSourceIds.Local
            ProviderTypes.WebDav -> BuiltInSourceIds.WebDav
            ProviderTypes.OneDrive -> BuiltInSourceIds.OneDrive
            ProviderTypes.Smb -> BuiltInSourceIds.Smb
            else -> return
        }
        val mediaId = legacyStorageTrackMediaId(
            sourceId = sourceId,
            accountId = storageSourceAccountId(cache.identity.storageId),
            path = cache.identity.path,
        )
        val sourceItem = database.sourceItemDao().findByPath(
            cache.identity.storageId,
            cache.identity.path,
        )
        val trackId = sourceItem?.let { item ->
            database.trackSourceRefDao().findBySourceItemIds(listOf(item.id))
                .firstOrNull()
                ?.trackId
        }
        val track = trackId?.let { database.trackDao().get(it) }
        val album = track?.albumId?.let { database.metadataDao().getAlbum(it) }
        when (
            val result = downloadFinalizer.finalize(
                DownloadFinalizationRequest(
                    mediaId = mediaId,
                    localPath = path,
                    mimeType = cache.mimeType,
                    fallbackTitle = track?.title
                        ?: cache.identity.path.substringAfterLast('/').substringBeforeLast('.'),
                    fallbackArtist = track?.artist,
                    fallbackAlbum = album?.name,
                    expectedDurationMs = track?.durationMs,
                    expectedBytes = promoted.bytes.toLong(),
                )
            )
        ) {
            is DownloadFinalizationResult.Success -> AppLogger.info(
                DiagnosticLogCategory.Cache,
                LOG_TARGET,
                "Completed playback cache promoted to local media",
                fields = mapOf(
                    "alreadyPromoted" to
                        (promoted.status == PlaybackCachePromotionStatus.ALREADY_PROMOTED).toString(),
                    "warningCount" to result.warnings.size.toString(),
                ),
            )
            is DownloadFinalizationResult.Failure -> AppLogger.warn(
                DiagnosticLogCategory.Cache,
                LOG_TARGET,
                "Promoted playback cache could not be finalized",
                detail = result.message,
            )
        }
    }
}

private const val LOG_TARGET = "CompletedPlaybackCachePromoter"
