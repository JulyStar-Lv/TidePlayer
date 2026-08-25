package io.github.julystar.musicapp.service.playback.data

import io.github.julystar.musicapp.core.data.media.PluginArtworkResolver
import io.github.julystar.musicapp.core.domain.model.AppSettings
import io.github.julystar.musicapp.core.domain.model.Artwork
import io.github.julystar.musicapp.core.domain.model.MetadataRefreshTarget
import io.github.julystar.musicapp.core.domain.model.NetworkStatus
import io.github.julystar.musicapp.core.domain.model.LyricSourceKind
import io.github.julystar.musicapp.core.domain.model.LyricSourceMode
import io.github.julystar.musicapp.core.domain.model.ShuffleStrategy
import io.github.julystar.musicapp.core.domain.repository.NetworkStatusProvider
import io.github.julystar.musicapp.core.domain.repository.SettingsRepository
import io.github.julystar.musicapp.database.AppDatabase
import io.github.julystar.musicapp.database.TrackEntity
import io.github.julystar.musicapp.diagnostics.TrackPreparationDiagnosticFields
import io.github.julystar.musicapp.diagnostics.TrackPreparationDiagnostics
import io.github.julystar.musicapp.diagnostics.TrackPreparationEvent
import io.github.julystar.musicapp.metadata.FilenameMetadata
import io.github.julystar.musicapp.metadata.FilenameMetadataParser
import io.github.julystar.musicapp.metadata.MetadataApplyResult
import io.github.julystar.musicapp.metadata.PluginSemanticMetadataEnricher
import io.github.julystar.musicapp.metadata.PluginSemanticMetadataResult
import io.github.julystar.musicapp.metadata.TrackCandidateMatchConfidence
import io.github.julystar.musicapp.metadata.TrackIdentityChangeReason
import io.github.julystar.musicapp.metadata.TrackIdentityReconciler
import io.github.julystar.musicapp.metadata.TrackIdentityResult
import io.github.julystar.musicapp.metadata.TrackMetadataQuality
import io.github.julystar.musicapp.metadata.TrackMetadataQualityEvaluator
import io.github.julystar.musicapp.metadata.TrackMetadataQualityInput
import io.github.julystar.musicapp.metadata.UnifiedMetadataRepository
import io.github.julystar.musicapp.platform.currentTimeMillis
import io.github.julystar.musicapp.plugin.management.PlaybackLyricsEnricher
import io.github.julystar.musicapp.service.librarysync.domain.MetadataRefreshController
import io.github.julystar.musicapp.service.librarysync.domain.MetadataRefreshRequest
import io.github.julystar.musicapp.service.librarysync.domain.MetadataRefreshScope
import io.github.julystar.musicapp.singleton.RoomLibraryStore
import io.github.julystar.musicapp.source.api.MetaSongCandidate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import uniffi.app_backend.MusicId
import uniffi.app_backend.PlayMode

data class TrackPreparationSnapshot(
    val track: TrackEntity,
    val sourcePath: String?,
    val album: String?,
    val artists: List<String>,
    val genres: List<String>,
    val hasArtwork: Boolean,
    val hasLyrics: Boolean,
    val sourceRefCount: Int,
    val sourceIdentities: List<TrackPreparationSourceIdentity> = emptyList(),
) {
    fun quality(): TrackMetadataQuality = TrackMetadataQualityEvaluator.evaluate(
        metadata = TrackMetadataQualityInput(
            title = track.title,
            artist = artists.joinToString(" / ").ifBlank { track.artist.orEmpty() },
            album = album,
            genre = genres.joinToString(" / "),
            durationMs = track.durationMs,
            trackNumber = track.trackNumber,
        ),
        metadataSource = track.metadataSource,
    )
}

data class TrackPreparationSourceIdentity(
    val sourceAccountId: Long?,
    val contentHash: String?,
    val audioFingerprint: String?,
)

interface TrackPreparationOperations {
    suspend fun snapshot(trackId: Long): TrackPreparationSnapshot?
    suspend fun refreshSourceMetadata(trackId: Long, allowNetwork: Boolean)
    suspend fun applyFilenameMetadata(trackId: Long, fileName: String): TrackEntity?
    suspend fun findPluginCandidate(
        track: TrackEntity,
        filenameHints: FilenameMetadata?,
    ): PluginSemanticMetadataResult?
    suspend fun commitPluginMetadata(trackId: Long, result: PluginSemanticMetadataResult): MetadataApplyResult
    suspend fun reconcile(trackId: Long): TrackIdentityResult
    suspend fun prepareArtwork(canonicalTrackId: Long, candidate: MetaSongCandidate?): Boolean
    suspend fun prepareLyrics(canonicalTrackId: Long, candidate: MetaSongCandidate?): Boolean
    suspend fun refreshRuntime(
        originalTrackId: Long,
        canonicalTrackId: Long,
        remappedTrackIds: Map<Long, Long>,
    )
    suspend fun preloadAudio(canonicalTrackId: Long, targetBytes: Long): Boolean
}

class DefaultTrackPreparationOperations(
    private val database: AppDatabase,
    private val roomLibraryStore: RoomLibraryStore,
    private val metadataRefreshController: MetadataRefreshController,
    private val pluginEnricher: PluginSemanticMetadataEnricher,
    private val metadataRepository: UnifiedMetadataRepository,
    private val identityReconciler: TrackIdentityReconciler,
    private val artworkResolver: PluginArtworkResolver,
    private val lyricsEnricher: PlaybackLyricsEnricher,
    private val playerRepository: PlayerRepository,
    private val playbackResourceResolver: PlaybackResourceResolver,
) : TrackPreparationOperations {
    override suspend fun snapshot(trackId: Long): TrackPreparationSnapshot? {
        val track = database.trackDao().get(trackId) ?: return null
        val metadataDao = database.metadataDao()
        val music = roomLibraryStore.getMusic(MusicId(trackId))
        val identitySnapshot = database.trackMergeDao().identitySnapshot(trackId)
        return TrackPreparationSnapshot(
            track = track,
            sourcePath = music?.loc?.path,
            album = track.albumId?.let { metadataDao.getAlbum(it)?.name },
            artists = metadataDao.artistNamesForTrack(trackId),
            genres = metadataDao.genreNamesForTrack(trackId),
            hasArtwork = metadataDao.getArtworkForTrack(trackId) != null ||
                track.albumId?.let { metadataDao.getArtworkForAlbum(it) } != null,
            hasLyrics = metadataDao.getLyrics(trackId) != null,
            sourceRefCount = database.trackMergeDao().listSourceRefs(trackId).size,
            sourceIdentities = identitySnapshot?.sources.orEmpty().map { source ->
                TrackPreparationSourceIdentity(
                    sourceAccountId = source.sourceAccountId,
                    contentHash = source.contentHash,
                    audioFingerprint = source.audioFingerprint,
                )
            },
        )
    }

    override suspend fun refreshSourceMetadata(trackId: Long, allowNetwork: Boolean) {
        metadataRefreshController.refresh(
            MetadataRefreshRequest(
                scope = MetadataRefreshScope.Track(trackId),
                target = MetadataRefreshTarget.All,
                allowNetwork = allowNetwork,
            )
        )
    }

    override suspend fun applyFilenameMetadata(trackId: Long, fileName: String): TrackEntity? =
        metadataRepository.replaceFilenameMetadata(trackId, fileName)

    override suspend fun findPluginCandidate(
        track: TrackEntity,
        filenameHints: FilenameMetadata?,
    ): PluginSemanticMetadataResult? = pluginEnricher.enrich(track, filenameHints)

    override suspend fun commitPluginMetadata(
        trackId: Long,
        result: PluginSemanticMetadataResult,
    ): MetadataApplyResult = metadataRepository.applyAutomaticPluginMatch(trackId, result.match)

    override suspend fun reconcile(trackId: Long): TrackIdentityResult =
        identityReconciler.reconcile(trackId, TrackIdentityChangeReason.MetadataChanged)

    override suspend fun prepareArtwork(
        canonicalTrackId: Long,
        candidate: MetaSongCandidate?,
    ): Boolean = artworkResolver.load(
        artwork = Artwork.LibraryTrack(canonicalTrackId),
        matchedCandidate = candidate,
        canonicalTrackId = canonicalTrackId,
    ) != null

    override suspend fun prepareLyrics(
        canonicalTrackId: Long,
        candidate: MetaSongCandidate?,
    ): Boolean = lyricsEnricher.enrich(
        trackId = canonicalTrackId,
        matchedCandidate = candidate,
        canonicalTrackId = canonicalTrackId,
    )

    override suspend fun refreshRuntime(
        originalTrackId: Long,
        canonicalTrackId: Long,
        remappedTrackIds: Map<Long, Long>,
    ) {
        playerRepository.refreshPreparedTrackRuntime(
            originalTrackId = originalTrackId,
            canonicalTrackId = canonicalTrackId,
            remappedTrackIds = remappedTrackIds,
        )
    }

    override suspend fun preloadAudio(canonicalTrackId: Long, targetBytes: Long): Boolean {
        val music = roomLibraryStore.getMusic(MusicId(canonicalTrackId)) ?: return false
        return playbackResourceResolver.preload(music, targetBytes)
    }
}

internal data class TrackPreparationTrigger(
    val currentTrackId: Long?,
    val nextTrackId: Long?,
    val playlistId: Long?,
    val queueTrackIds: List<Long>,
    val playing: Boolean,
    val loading: Boolean,
    val playMode: PlayMode,
    val shuffleEnabled: Boolean,
    val settings: AppSettings,
    val network: NetworkStatus,
) {
    val remoteAllowed: Boolean
        get() = network.isOnline && (!network.isMetered || settings.allowMeteredNetworkUsage)

    val shouldPrepare: Boolean
        get() = playing && !loading && nextTrackId != null && playMode != PlayMode.SINGLE_LOOP &&
            !(shuffleEnabled && settings.playbackAdvanced.shuffleStrategy == ShuffleStrategy.TrueRandom)

}

private fun TrackPreparationTrigger.dedupeKey(aliases: Map<Long, Long>): TrackPreparationTriggerKey =
    TrackPreparationTriggerKey(
        currentTrackId = currentTrackId?.canonicalId(aliases),
        nextTrackId = nextTrackId?.canonicalId(aliases),
        playlistId = playlistId,
        queueTrackIds = queueTrackIds.map { it.canonicalId(aliases) },
        playing = playing,
        loading = loading,
        playMode = playMode,
        shuffleEnabled = shuffleEnabled,
        settings = TrackPreparationSettingsKey(
            allowMeteredNetworkUsage = settings.allowMeteredNetworkUsage,
            listenAndCacheEnabled = settings.listenAndCacheEnabled,
            audioPreloadBytes = settings.audioPreloadBytes,
            shuffleStrategy = settings.playbackAdvanced.shuffleStrategy,
            lyricSourceMode = settings.lyrics.sourceMode,
            lyricSourcePriority = settings.lyrics.sourcePriority,
        ),
        network = network,
    )

private data class TrackPreparationTriggerKey(
    val currentTrackId: Long?,
    val nextTrackId: Long?,
    val playlistId: Long?,
    val queueTrackIds: List<Long>,
    val playing: Boolean,
    val loading: Boolean,
    val playMode: PlayMode,
    val shuffleEnabled: Boolean,
    val settings: TrackPreparationSettingsKey,
    val network: NetworkStatus,
)

private data class TrackPreparationSettingsKey(
    val allowMeteredNetworkUsage: Boolean,
    val listenAndCacheEnabled: Boolean,
    val audioPreloadBytes: Long,
    val shuffleStrategy: ShuffleStrategy,
    val lyricSourceMode: LyricSourceMode,
    val lyricSourcePriority: List<LyricSourceKind>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TrackPreparationCoordinator internal constructor(
    scope: CoroutineScope,
    triggers: Flow<TrackPreparationTrigger>,
    private val operations: TrackPreparationOperations,
    private val diagnostics: TrackPreparationDiagnostics,
    private val debounceMillis: Long = DEFAULT_TRACK_PREPARATION_DEBOUNCE_MS,
) {
    private val canonicalAliases = kotlinx.coroutines.flow.MutableStateFlow<Map<Long, Long>>(emptyMap())

    constructor(
        scope: CoroutineScope,
        playerRepository: PlayerRepository,
        shuffleEnabled: StateFlow<Boolean>,
        settingsRepository: SettingsRepository,
        networkStatusProvider: NetworkStatusProvider,
        operations: TrackPreparationOperations,
        diagnostics: TrackPreparationDiagnostics,
        debounceMillis: Long = DEFAULT_TRACK_PREPARATION_DEBOUNCE_MS,
    ) : this(
        scope = scope,
        triggers = preparationTriggers(
            playerRepository = playerRepository,
            shuffleEnabled = shuffleEnabled,
            settingsRepository = settingsRepository,
            networkStatusProvider = networkStatusProvider,
        ),
        operations = operations,
        diagnostics = diagnostics,
        debounceMillis = debounceMillis,
    )

    init {
        scope.launch {
            triggers
                .distinctUntilChanged { old, new ->
                    val aliases = canonicalAliases.value
                    old.dedupeKey(aliases) == new.dedupeKey(aliases)
                }
                .collectLatest { trigger ->
                    if (!trigger.shouldPrepare) return@collectLatest
                    val trackId = trigger.nextTrackId ?: return@collectLatest
                    diagnostics.record(
                        TrackPreparationEvent.TrackPrepareScheduled,
                        fields = TrackPreparationDiagnosticFields(trackId = trackId, stage = "scheduled"),
                        correlationId = trackId.correlationId(),
                    )
                    delay(debounceMillis.coerceAtLeast(0L))
                    prepare(trigger)
                }
        }
    }

    private suspend fun prepare(trigger: TrackPreparationTrigger) {
        val trackId = trigger.nextTrackId ?: return
        val correlationId = trackId.correlationId()
        val startedAt = currentTimeMillis()
        try {
            var snapshot = bestEffort<TrackPreparationSnapshot?>(trackId, "snapshot", correlationId) {
                operations.snapshot(trackId)
            } ?: return
            val preRefreshIdentity = snapshot.identitySignature()

            diagnostics.record(
                TrackPreparationEvent.TrackPrepareMetadataStart,
                fields = TrackPreparationDiagnosticFields(trackId = trackId, stage = "source_metadata"),
                correlationId = correlationId,
            )
            bestEffort(trackId, "source_metadata", correlationId) {
                operations.refreshSourceMetadata(trackId, allowNetwork = trigger.remoteAllowed)
            }
            diagnostics.record(
                TrackPreparationEvent.TrackPrepareMetadataComplete,
                fields = TrackPreparationDiagnosticFields(
                    trackId = trackId,
                    stage = "source_metadata",
                    reason = if (trigger.remoteAllowed) "completed" else "local_only",
                ),
                correlationId = correlationId,
            )

            snapshot = bestEffort<TrackPreparationSnapshot?>(trackId, "snapshot_after_source", correlationId) {
                operations.snapshot(trackId)
            } ?: snapshot
            var identityChanged = preRefreshIdentity != snapshot.identitySignature()
            val quality = snapshot.quality()
            var filenameHints: FilenameMetadata? = null

            if (quality == TrackMetadataQuality.FILENAME_ONLY || quality == TrackMetadataQuality.EMPTY) {
                filenameHints = snapshot.filenameMetadata()
                filenameHints?.let { parsed ->
                    diagnostics.record(
                        TrackPreparationEvent.TrackPrepareFilenameDetected,
                        fields = TrackPreparationDiagnosticFields(
                            trackId = trackId,
                            stage = "filename",
                            confidence = parsed.confidence.ordinal.toDouble(),
                        ),
                        correlationId = correlationId,
                    )
                    bestEffort(trackId, "filename", correlationId) {
                        operations.applyFilenameMetadata(trackId, parsed.raw)
                    }?.let { updated ->
                        identityChanged = identityChanged ||
                            snapshot.track.identitySignature() != updated.identitySignature()
                        snapshot = snapshot.copy(track = updated)
                    }
                }
            }

            var pluginResult: PluginSemanticMetadataResult? = null
            if (trigger.remoteAllowed) {
                diagnostics.record(
                    TrackPreparationEvent.TrackPreparePluginLookupStart,
                    fields = TrackPreparationDiagnosticFields(trackId = trackId, stage = "plugin_lookup"),
                    correlationId = correlationId,
                )
                pluginResult = bestEffort(trackId, "plugin_lookup", correlationId) {
                    operations.findPluginCandidate(snapshot.track, filenameHints)
                }
                pluginResult?.let { result ->
                    val match = result.match
                    diagnostics.record(
                        TrackPreparationEvent.TrackPreparePluginCandidate,
                        fields = TrackPreparationDiagnosticFields(
                            trackId = trackId,
                            stage = "plugin_lookup",
                            candidateSourceId = match.candidate.sourceId,
                            matchMethod = match.confidence.name,
                            confidence = match.score.toDouble(),
                            durationDifferenceMs = durationDifference(snapshot.track, match.candidate),
                        ),
                        correlationId = correlationId,
                    )
                    if (result.canApplyAutomatically) {
                        bestEffort(trackId, "metadata_commit", correlationId) {
                            operations.commitPluginMetadata(trackId, result)
                        }?.let { applied ->
                            identityChanged = identityChanged || applied.identityChanged
                            applied.track?.let { snapshot = snapshot.copy(track = it) }
                            diagnostics.record(
                                TrackPreparationEvent.TrackPrepareMetadataCommitted,
                                fields = TrackPreparationDiagnosticFields(
                                    trackId = trackId,
                                    stage = "metadata_commit",
                                    metadataSource = applied.track?.metadataSource,
                                ),
                                correlationId = correlationId,
                            )
                        }
                    } else {
                        diagnostics.record(
                            TrackPreparationEvent.TrackPreparePluginLowConfidence,
                            fields = TrackPreparationDiagnosticFields(
                                trackId = trackId,
                                stage = "plugin_lookup",
                                confidence = match.score.toDouble(),
                                reason = match.confidence.name,
                            ),
                            correlationId = correlationId,
                        )
                    }
                }
            }

            var canonicalTrackId = trackId
            var remappedTrackIds = emptyMap<Long, Long>()
            if (identityChanged) {
                diagnostics.record(
                    TrackPreparationEvent.TrackIdentityReconcileStart,
                    fields = TrackPreparationDiagnosticFields(trackId = trackId, stage = "identity_reconcile"),
                    correlationId = correlationId,
                )
                when (val result = bestEffort(trackId, "identity_reconcile", correlationId) {
                    operations.reconcile(trackId)
                }) {
                    is TrackIdentityResult.Merged -> {
                        canonicalTrackId = result.canonicalTrackId
                        remappedTrackIds = result.mergedTrackIds.associateWith { result.canonicalTrackId }
                        canonicalAliases.value = canonicalAliases.value + remappedTrackIds
                        diagnostics.record(
                            TrackPreparationEvent.TrackIdentityMergeCompleted,
                            fields = TrackPreparationDiagnosticFields(
                                trackId = trackId,
                                canonicalTrackId = canonicalTrackId,
                                stage = "identity_reconcile",
                                matchMethod = result.matchMethod,
                                confidence = result.confidence.toDouble(),
                            ),
                            correlationId = correlationId,
                        )
                    }
                    is TrackIdentityResult.Unchanged -> {
                        canonicalTrackId = result.trackId
                        if (canonicalTrackId != trackId) {
                            remappedTrackIds = mapOf(trackId to canonicalTrackId)
                            canonicalAliases.value = canonicalAliases.value + remappedTrackIds
                            diagnostics.record(
                                TrackPreparationEvent.TrackIdentityRemapped,
                                fields = TrackPreparationDiagnosticFields(
                                    trackId = trackId,
                                    canonicalTrackId = canonicalTrackId,
                                    stage = "identity_reconcile",
                                    reason = "already_merged",
                                ),
                                correlationId = correlationId,
                            )
                        }
                        diagnostics.record(
                            TrackPreparationEvent.TrackIdentityMergeSkipped,
                            fields = TrackPreparationDiagnosticFields(
                                trackId = trackId,
                                canonicalTrackId = canonicalTrackId,
                                stage = "identity_reconcile",
                                reason = "unchanged",
                            ),
                            correlationId = correlationId,
                        )
                    }
                    is TrackIdentityResult.PotentialDuplicate -> diagnostics.record(
                        TrackPreparationEvent.TrackIdentityCandidateFound,
                        fields = TrackPreparationDiagnosticFields(
                            trackId = trackId,
                            candidateTrackId = result.candidateTrackIds.firstOrNull(),
                            stage = "identity_reconcile",
                            reason = "potential_duplicate",
                        ),
                        correlationId = correlationId,
                    )
                    null -> Unit
                }
            }

            val reusableCandidate = pluginResult
                ?.takeIf { it.match.confidence != TrackCandidateMatchConfidence.LOW }
                ?.match
                ?.candidate
            if (trigger.remoteAllowed) {
                bestEffort(trackId, "artwork", correlationId) {
                    operations.prepareArtwork(canonicalTrackId, reusableCandidate)
                }
                diagnostics.record(
                    TrackPreparationEvent.TrackPrepareArtworkComplete,
                    fields = TrackPreparationDiagnosticFields(
                        trackId = trackId,
                        canonicalTrackId = canonicalTrackId,
                        stage = "artwork",
                    ),
                    correlationId = correlationId,
                )
                bestEffort(trackId, "lyrics", correlationId) {
                    operations.prepareLyrics(canonicalTrackId, reusableCandidate)
                }
                diagnostics.record(
                    TrackPreparationEvent.TrackPrepareLyricsComplete,
                    fields = TrackPreparationDiagnosticFields(
                        trackId = trackId,
                        canonicalTrackId = canonicalTrackId,
                        stage = "lyrics",
                    ),
                    correlationId = correlationId,
                )
            }

            bestEffort(trackId, "runtime_refresh", correlationId) {
                operations.refreshRuntime(trackId, canonicalTrackId, remappedTrackIds)
            }
            if (
                trigger.remoteAllowed &&
                trigger.settings.listenAndCacheEnabled &&
                trigger.settings.audioPreloadBytes > 0L
            ) {
                diagnostics.record(
                    TrackPreparationEvent.TrackPrepareAudioStart,
                    fields = TrackPreparationDiagnosticFields(
                        trackId = trackId,
                        canonicalTrackId = canonicalTrackId,
                        stage = "audio",
                        targetBytes = trigger.settings.audioPreloadBytes,
                    ),
                    correlationId = correlationId,
                )
                bestEffort(trackId, "audio", correlationId) {
                    operations.preloadAudio(canonicalTrackId, trigger.settings.audioPreloadBytes)
                }
                diagnostics.record(
                    TrackPreparationEvent.TrackPrepareAudioComplete,
                    fields = TrackPreparationDiagnosticFields(
                        trackId = trackId,
                        canonicalTrackId = canonicalTrackId,
                        stage = "audio",
                        targetBytes = trigger.settings.audioPreloadBytes,
                        elapsedMs = (currentTimeMillis() - startedAt).coerceAtLeast(0L),
                    ),
                    correlationId = correlationId,
                )
            }
        } catch (cancellation: CancellationException) {
            diagnostics.record(
                TrackPreparationEvent.TrackPrepareCancelled,
                fields = TrackPreparationDiagnosticFields(trackId = trackId, stage = "cancelled"),
                correlationId = correlationId,
            )
            throw cancellation
        } catch (_: Exception) {
            diagnostics.record(
                TrackPreparationEvent.TrackPrepareFailed,
                fields = TrackPreparationDiagnosticFields(
                    trackId = trackId,
                    stage = "pipeline",
                    reason = "pipeline_failed",
                ),
                correlationId = correlationId,
            )
        }
    }

    private suspend fun <T> bestEffort(
        trackId: Long,
        stage: String,
        correlationId: String,
        block: suspend () -> T,
    ): T? = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        diagnostics.record(
            TrackPreparationEvent.TrackPrepareFailed,
            fields = TrackPreparationDiagnosticFields(
                trackId = trackId,
                stage = stage,
                reason = "stage_failed",
            ),
            correlationId = correlationId,
        )
        null
    }
}

private data class PlaybackPreparationNavigation(
    val currentTrackId: Long?,
    val nextTrackId: Long?,
    val playlistId: Long?,
    val queueTrackIds: List<Long>,
)

private data class PlaybackPreparationStatus(
    val playing: Boolean,
    val loading: Boolean,
    val playMode: PlayMode,
)

private fun preparationTriggers(
    playerRepository: PlayerRepository,
    shuffleEnabled: StateFlow<Boolean>,
    settingsRepository: SettingsRepository,
    networkStatusProvider: NetworkStatusProvider,
): Flow<TrackPreparationTrigger> {
    val navigation = combine(
        playerRepository.music,
        playerRepository.nextMusic,
        playerRepository.playlist,
    ) { current, next, playlist ->
        PlaybackPreparationNavigation(
            currentTrackId = current?.meta?.id?.value,
            nextTrackId = next?.meta?.id?.value,
            playlistId = playlist?.abstr?.meta?.id?.value,
            queueTrackIds = playlist?.musics.orEmpty().map { it.meta.id.value },
        )
    }
    val status = combine(
        playerRepository.playing,
        playerRepository.loading,
        playerRepository.playMode,
    ) { playing, loading, playMode ->
        PlaybackPreparationStatus(playing, loading, playMode)
    }
    return combine(
        navigation,
        status,
        shuffleEnabled,
        settingsRepository.settings,
        networkStatusProvider.status,
    ) { nav, playback, shuffle, settings, network ->
        TrackPreparationTrigger(
            currentTrackId = nav.currentTrackId,
            nextTrackId = nav.nextTrackId,
            playlistId = nav.playlistId,
            queueTrackIds = nav.queueTrackIds,
            playing = playback.playing,
            loading = playback.loading,
            playMode = playback.playMode,
            shuffleEnabled = shuffle,
            settings = settings,
            network = network,
        )
    }
}

private data class TrackIdentitySignature(
    val title: String,
    val artist: String?,
    val albumId: Long?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val date: String?,
    val isrc: String?,
    val musicBrainzRecordingId: String?,
    val metadataExternalId: String?,
    val durationMs: Long?,
)

private fun TrackEntity.identitySignature() = TrackIdentitySignature(
    title = title,
    artist = artist,
    albumId = albumId,
    trackNumber = trackNumber,
    discNumber = discNumber,
    date = date,
    isrc = isrc,
    musicBrainzRecordingId = musicBrainzRecordingId,
    metadataExternalId = metadataExternalId,
    durationMs = durationMs,
)

private fun TrackPreparationSnapshot.identitySignature(): Pair<TrackIdentitySignature, List<TrackPreparationSourceIdentity>> =
    track.identitySignature() to sourceIdentities.sortedWith(
        compareBy<TrackPreparationSourceIdentity> { it.sourceAccountId }
            .thenBy { it.contentHash }
            .thenBy { it.audioFingerprint },
    )

private fun TrackPreparationSnapshot.filenameMetadata(): FilenameMetadata? {
    val path = sourcePath?.replace('\\', '/') ?: return null
    val segments = path.split('/').filter(String::isNotBlank)
    val fileName = segments.lastOrNull() ?: return null
    return FilenameMetadataParser.parse(
        fileName = fileName,
        parent = segments.getOrNull(segments.lastIndex - 1),
        grandparent = segments.getOrNull(segments.lastIndex - 2),
        durationMs = track.durationMs,
    )
}

private fun durationDifference(track: TrackEntity, candidate: MetaSongCandidate): Long? {
    val local = track.durationMs ?: return null
    val remote = candidate.durationMs ?: return null
    return kotlin.math.abs(local - remote)
}

private fun Long.canonicalId(aliases: Map<Long, Long>): Long {
    var current = this
    repeat(aliases.size) {
        val next = aliases[current] ?: return current
        if (next == current) return current
        current = next
    }
    return current
}

private fun Long.correlationId(): String = "track-$this"

const val DEFAULT_TRACK_PREPARATION_DEBOUNCE_MS = 750L
