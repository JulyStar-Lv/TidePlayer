package io.github.julystar.musicapp.core

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import io.github.julystar.musicapp.core.domain.model.AppSettings
import io.github.julystar.musicapp.core.domain.repository.NetworkStatusProvider
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.service.playback.data.PlaybackResourceResolver
import io.github.julystar.musicapp.singleton.RoomLibraryStore
import io.github.julystar.musicapp.source.api.PlaybackResource
import io.github.julystar.musicapp.source.api.SourcePlaybackResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import uniffi.app_backend.MusicId
import java.io.IOException

/**
 * Resolves a lightweight TidePlayer queue URI only when Media3 opens that item.
 *
 * This keeps WebDAV/SMB/OneDrive credentials and temporary resources out of MediaItem metadata,
 * avoids eagerly resolving an entire queue, and guarantees that resources opened for preloading or
 * playback are released when the corresponding Media3 data source closes.
 */
@OptIn(UnstableApi::class)
internal class AndroidPlaybackDataSourceFactory(
    private val upstreamFactory: DataSource.Factory,
    private val roomLibraryStore: RoomLibraryStore,
    private val playbackResourceResolver: PlaybackResourceResolver,
    private val settingsRepository: SettingsRepository,
    private val networkStatusProvider: NetworkStatusProvider,
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return AndroidPlaybackDataSource(
            upstreamFactory = upstreamFactory,
            roomLibraryStore = roomLibraryStore,
            playbackResourceResolver = playbackResourceResolver,
            settingsRepository = settingsRepository,
            networkStatusProvider = networkStatusProvider,
        )
    }
}

@OptIn(UnstableApi::class)
private class AndroidPlaybackDataSource(
    private val upstreamFactory: DataSource.Factory,
    private val roomLibraryStore: RoomLibraryStore,
    private val playbackResourceResolver: PlaybackResourceResolver,
    private val settingsRepository: SettingsRepository,
    private val networkStatusProvider: NetworkStatusProvider,
) : DataSource {
    private val transferListeners = mutableListOf<TransferListener>()
    private var upstream: DataSource? = null
    private var resolvedResource: PlaybackResource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners += transferListener
        upstream?.addTransferListener(transferListener)
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        check(upstream == null) { "DataSource is already open" }
        val trackId = dataSpec.uri.androidPlaybackTrackIdOrNull()
        val resource = trackId?.let(::resolveTrackResource)
        val resolvedSpec = if (resource == null) {
            dataSpec
        } else {
            resolvedResource = resource
            dataSpec.buildUpon()
                .setUri(Uri.parse(resource.uri))
                .setHttpRequestHeaders(dataSpec.httpRequestHeaders + resource.headers)
                .build()
        }

        val source = upstreamFactory.createDataSource().also { created ->
            transferListeners.forEach(created::addTransferListener)
            upstream = created
        }
        return try {
            source.open(resolvedSpec)
        } catch (error: Throwable) {
            runCatching { source.close() }
            upstream = null
            releaseResolvedResource()
            if (error is IOException) throw error
            throw IOException("Unable to open playback resource", error)
        }
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return checkNotNull(upstream) { "DataSource is not open" }
            .read(buffer, offset, length)
    }

    override fun getUri(): Uri? = upstream?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        upstream?.responseHeaders ?: emptyMap()

    @Throws(IOException::class)
    override fun close() {
        val source = upstream
        upstream = null
        var closeFailure: IOException? = null
        if (source != null) {
            try {
                source.close()
            } catch (error: IOException) {
                closeFailure = error
            }
        }
        releaseResolvedResource()
        closeFailure?.let { throw it }
    }

    @Throws(IOException::class)
    private fun resolveTrackResource(trackId: Long): PlaybackResource {
        return runBlocking(Dispatchers.IO) {
            val music = roomLibraryStore.getMusic(MusicId(trackId))
                ?: throw IOException("Playback track $trackId no longer exists")
            val settings = settingsRepository.settings.first()
            val maxAttempts = if (settings.retryPlaybackOnFailure) {
                settings.networkRetryCount + 1
            } else {
                1
            }

            repeat(maxAttempts) { attempt ->
                val resolved = withTimeoutOrNull(
                    settings.connectionTimeoutSeconds * 1_000L
                ) {
                    playbackResourceResolver.resolve(music)
                }
                when (resolved) {
                    is SourcePlaybackResult.Success -> {
                        val resource = resolved.resource
                        validateNetworkPolicy(resource, settings)
                        return@runBlocking resource
                    }
                    is SourcePlaybackResult.Failure,
                    null -> Unit
                }
                if (attempt < maxAttempts - 1) delay(RESOLVE_RETRY_DELAY_MS)
            }
            throw IOException("Unable to resolve playback resource for track $trackId")
        }
    }

    @Throws(IOException::class)
    private suspend fun validateNetworkPolicy(
        resource: PlaybackResource,
        settings: AppSettings,
    ) {
        if (resource.isLocal) return
        val network = networkStatusProvider.status.value
        if (!network.isOnline || (network.isMetered && !settings.allowMeteredNetworkUsage)) {
            playbackResourceResolver.release(resource)
            val message = if (!network.isOnline) {
                "Network is offline"
            } else {
                "Playback is blocked by the metered-network setting"
            }
            throw IOException(message)
        }
    }

    private fun releaseResolvedResource() {
        val resource = resolvedResource ?: return
        resolvedResource = null
        runBlocking(Dispatchers.IO) {
            try {
                playbackResourceResolver.release(resource)
            } catch (_: Throwable) {
                // Closing a stream must not fail because best-effort temporary-resource cleanup did.
            }
        }
    }

    private companion object {
        const val RESOLVE_RETRY_DELAY_MS = 350L
    }
}
