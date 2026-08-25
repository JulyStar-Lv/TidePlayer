package io.github.julystar.musicapp.service.playback.data

import io.github.julystar.musicapp.core.domain.model.AppSettings
import io.github.julystar.musicapp.core.domain.model.AppThemeMode
import io.github.julystar.musicapp.core.domain.model.LyricSourceMode
import io.github.julystar.musicapp.core.domain.model.NetworkStatus
import io.github.julystar.musicapp.core.domain.model.ShuffleStrategy
import io.github.julystar.musicapp.database.TrackEntity
import io.github.julystar.musicapp.database.TrackMetadataSources
import io.github.julystar.musicapp.diagnostics.TrackPreparationDiagnostics
import io.github.julystar.musicapp.metadata.FilenameMetadata
import io.github.julystar.musicapp.metadata.MetadataApplyResult
import io.github.julystar.musicapp.metadata.PluginSemanticMetadataResult
import io.github.julystar.musicapp.metadata.TrackCandidateMatch
import io.github.julystar.musicapp.metadata.TrackCandidateMatchConfidence
import io.github.julystar.musicapp.metadata.TrackIdentityResult
import io.github.julystar.musicapp.source.api.MetaSongCandidate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import uniffi.app_backend.Music
import uniffi.app_backend.MusicAbstract
import uniffi.app_backend.MusicId
import uniffi.app_backend.MusicMeta
import uniffi.app_backend.PlayMode
import uniffi.app_backend.Playlist
import uniffi.app_backend.PlaylistAbstract
import uniffi.app_backend.PlaylistId
import uniffi.app_backend.PlaylistMeta
import uniffi.app_backend.StorageEntryLoc
import uniffi.app_backend.StorageId

@OptIn(ExperimentalCoroutinesApi::class)
class TrackPreparationCoordinatorTest {
    @Test
    fun playingNextIsDebouncedAndIdenticalTriggerIsDeduplicated() = runTest {
        val fixture = fixture(debounceMillis = 750)
        fixture.operations.snapshots[2] = snapshot(track(2))

        fixture.triggers.emit(trigger(nextTrackId = 2))
        advanceTimeBy(749)
        runCurrent()
        assertTrue(fixture.operations.calls.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, fixture.operations.calls.count { it == "snapshot:2" })

        fixture.triggers.emit(trigger(nextTrackId = 2))
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, fixture.operations.calls.count { it == "snapshot:2" })
    }

    @Test
    fun nextChangeCancelsInFlightPreparationAndStartsReplacement() = runTest {
        val fixture = fixture(debounceMillis = 0)
        fixture.operations.snapshots[2] = snapshot(track(2))
        fixture.operations.snapshots[3] = snapshot(track(3))
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        fixture.operations.snapshotHook = { trackId ->
            if (trackId == 2L) {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
        }

        fixture.triggers.emit(trigger(nextTrackId = 2))
        runCurrent()
        assertTrue(started.isCompleted)

        fixture.triggers.emit(trigger(nextTrackId = 3))
        runCurrent()

        assertTrue(cancelled.isCompleted)
        assertTrue("snapshot:3" in fixture.operations.calls)
        assertTrue("runtime:3:3" in fixture.operations.calls)
    }

    @Test
    fun repeatOneAndTrueRandomDoNotPredictNext() = runTest {
        val fixture = fixture(debounceMillis = 0)
        fixture.operations.snapshots[2] = snapshot(track(2))

        fixture.triggers.emit(trigger(nextTrackId = 2, playMode = PlayMode.SINGLE_LOOP))
        runCurrent()
        fixture.triggers.emit(
            trigger(
                nextTrackId = 2,
                shuffleEnabled = true,
                settings = AppSettings.Default.copy(
                    playbackAdvanced = AppSettings.Default.playbackAdvanced.copy(
                        shuffleStrategy = ShuffleStrategy.TrueRandom,
                    ),
                ),
            ),
        )
        runCurrent()

        assertTrue(fixture.operations.calls.isEmpty())
    }

    @Test
    fun offlineAndBlockedMeteredRunsLocalPipelineWithoutRemoteStages() = runTest {
        val fixture = fixture(debounceMillis = 0)
        fixture.operations.snapshots[2] = snapshot(track(2))
        fixture.operations.snapshots[3] = snapshot(track(3))

        fixture.triggers.emit(
            trigger(nextTrackId = 2, network = NetworkStatus(isOnline = false, isMetered = false)),
        )
        runCurrent()
        assertLocalOnlyPipeline(fixture.operations.calls, trackId = 2)

        fixture.operations.calls.clear()
        fixture.triggers.emit(
            trigger(nextTrackId = 3, network = NetworkStatus(isOnline = true, isMetered = true)),
        )
        runCurrent()
        assertLocalOnlyPipeline(fixture.operations.calls, trackId = 3)
    }

    @Test
    fun executesStrictOrderAndReusesCandidateAfterCanonicalization() = runTest {
        val fixture = fixture(debounceMillis = 0)
        val raw = track(
            id = 2,
            title = "Artist - Album - 04 - Song",
            metadataSource = TrackMetadataSources.Filename,
        ).copy(albumId = null, artist = null)
        fixture.operations.snapshots[2] = snapshot(
            raw,
            sourcePath = "/Music/Artist/Album/Artist - Album - 04 - Song.flac",
        )
        fixture.operations.filenameTrack = raw.copy(
            title = "Song",
            artist = "Artist",
            trackNumber = 4,
        )
        val candidate = MetaSongCandidate(
            id = "song-2",
            title = "Song",
            artist = "Artist",
            album = "Album",
            sourceId = "plugin",
        )
        fixture.operations.pluginResult = PluginSemanticMetadataResult(
            match = TrackCandidateMatch(candidate, TrackCandidateMatchConfidence.HIGH, 100),
            rounds = 1,
        )
        val committed = fixture.operations.filenameTrack!!.copy(
            albumId = 7,
            metadataSource = TrackMetadataSources.Plugin,
            metadataSourceId = "plugin",
            metadataExternalId = "song-2",
        )
        fixture.operations.commitResult = MetadataApplyResult(
            requestedTrackId = 2,
            track = committed,
            changedFields = setOf("albumId", "metadataExternalId"),
            changedIdentityFields = setOf("albumId", "metadataExternalId"),
        )
        fixture.operations.reconcileResult = TrackIdentityResult.Merged(
            canonicalTrackId = 9,
            mergedTrackIds = listOf(2),
            matchMethod = "content-hash",
            confidence = 100,
        )

        fixture.triggers.emit(trigger(nextTrackId = 2))
        runCurrent()

        assertEquals(
            listOf(
                "snapshot:2",
                "source:2:true",
                "snapshot:2",
                "filename:2",
                "plugin:2",
                "commit:2",
                "reconcile:2",
                "artwork:9",
                "lyrics:9",
                "runtime:2:9",
                "audio:9:${AppSettings.Default.audioPreloadBytes}",
            ),
            fixture.operations.calls,
        )
        assertSame(candidate, fixture.operations.artworkCandidate)
        assertSame(candidate, fixture.operations.lyricsCandidate)
        assertEquals(mapOf(2L to 9L), fixture.operations.runtimeRemap)
    }

    @Test
    fun sourceIdentityOnlyChangeReconcilesButUnchangedSnapshotDoesNot() = runTest {
        val fixture = fixture(debounceMillis = 0)
        val baseTrack = track(2)
        fixture.operations.snapshots[2] = snapshot(
            baseTrack,
            sourceIdentities = listOf(TrackPreparationSourceIdentity(1, "old", null)),
        )
        fixture.operations.sourceRefreshSnapshots[2] = snapshot(
            baseTrack,
            sourceIdentities = listOf(TrackPreparationSourceIdentity(1, "new", null)),
        )

        fixture.triggers.emit(trigger(nextTrackId = 2))
        runCurrent()
        assertEquals(1, fixture.operations.calls.count { it == "reconcile:2" })

        fixture.operations.calls.clear()
        fixture.operations.snapshots[3] = snapshot(track(3))
        fixture.triggers.emit(trigger(nextTrackId = 3))
        runCurrent()
        assertFalse("reconcile:3" in fixture.operations.calls)
    }

    @Test
    fun concurrentMergeSurvivorIsRemappedAndDoesNotRepeatPreparation() = runTest {
        val fixture = fixture(debounceMillis = 0)
        val baseTrack = track(2)
        fixture.operations.snapshots[2] = snapshot(
            baseTrack,
            sourceIdentities = listOf(TrackPreparationSourceIdentity(1, "old", null)),
        )
        fixture.operations.sourceRefreshSnapshots[2] = snapshot(
            baseTrack,
            sourceIdentities = listOf(TrackPreparationSourceIdentity(1, "new", null)),
        )
        fixture.operations.reconcileResult = TrackIdentityResult.Unchanged(9)

        fixture.triggers.emit(trigger(nextTrackId = 2))
        runCurrent()

        assertEquals(mapOf(2L to 9L), fixture.operations.runtimeRemap)
        assertTrue("runtime:2:9" in fixture.operations.calls)

        fixture.operations.calls.clear()
        fixture.triggers.emit(trigger(nextTrackId = 9))
        runCurrent()
        assertTrue(fixture.operations.calls.isEmpty())
    }

    @Test
    fun audioDisablementSkipsOnlyFinalAudioStage() = runTest {
        val fixture = fixture(debounceMillis = 0)
        fixture.operations.snapshots[2] = snapshot(track(2))
        fixture.operations.snapshots[3] = snapshot(track(3))

        fixture.triggers.emit(
            trigger(
                nextTrackId = 2,
                settings = AppSettings.Default.copy(listenAndCacheEnabled = false),
            ),
        )
        runCurrent()
        assertMetadataAssetsAndRuntimeWithoutAudio(fixture.operations.calls, 2)

        fixture.operations.calls.clear()
        fixture.triggers.emit(
            trigger(
                nextTrackId = 3,
                settings = AppSettings.Default.copy(audioPreloadBytes = 0),
            ),
        )
        runCurrent()
        assertMetadataAssetsAndRuntimeWithoutAudio(fixture.operations.calls, 3)
    }

    @Test
    fun unrelatedSettingsDoNotRepeatButNetworkAudioAndLyricsSettingsReevaluate() = runTest {
        val fixture = fixture(debounceMillis = 0)
        fixture.operations.snapshots[2] = snapshot(track(2))
        val original = trigger(nextTrackId = 2)

        fixture.triggers.emit(original)
        runCurrent()
        val initialReads = fixture.operations.calls.count { it == "snapshot:2" }

        fixture.triggers.emit(
            original.copy(settings = original.settings.copy(themeMode = AppThemeMode.Dark)),
        )
        runCurrent()
        assertEquals(initialReads, fixture.operations.calls.count { it == "snapshot:2" })

        fixture.triggers.emit(original.copy(network = NetworkStatus(isOnline = false, isMetered = false)))
        runCurrent()
        assertEquals(initialReads + 2, fixture.operations.calls.count { it == "snapshot:2" })

        fixture.triggers.emit(
            original.copy(settings = original.settings.copy(audioPreloadBytes = 8L * 1024L * 1024L)),
        )
        runCurrent()
        assertEquals(initialReads + 4, fixture.operations.calls.count { it == "snapshot:2" })

        fixture.triggers.emit(
            original.copy(
                settings = original.settings.copy(
                    lyrics = original.settings.lyrics.copy(sourceMode = LyricSourceMode.External),
                ),
            ),
        )
        runCurrent()
        assertEquals(initialReads + 6, fixture.operations.calls.count { it == "snapshot:2" })
    }

    @Test
    fun runtimeRemapPreservesDuplicateOccurrencesAndCurrentLocation() {
        val current = music(5, "Old", "/playing/source.flac", order = 90u)
        val canonical = music(9, "Canonical", "/canonical/source.flac", order = 9u)
        val queue = playlist(
            listOf(
                musicAbstract(5, "Old A", order = 10u),
                musicAbstract(7, "Other", order = 20u),
                musicAbstract(5, "Old B", order = 30u),
            ),
        )

        val remapped = refreshPreparedTrackRuntimeState(
            currentMusic = current,
            playlist = queue,
            canonicalMusic = canonical,
            affectedTrackIds = setOf(5, 9),
        )

        assertEquals(9, remapped.currentMusic?.meta?.id?.value)
        assertEquals("Canonical", remapped.currentMusic?.meta?.title)
        assertEquals("/playing/source.flac", remapped.currentMusic?.loc?.path)
        assertEquals(listOf(90u), remapped.currentMusic?.meta?.order)
        assertEquals(listOf(9L, 7L, 9L), remapped.playlist?.musics?.map { it.meta.id.value })
        assertEquals(listOf(listOf(10u), listOf(20u), listOf(30u)), remapped.playlist?.musics?.map { it.meta.order })
        assertEquals(3uL, remapped.playlist?.abstr?.musicCount)
    }

    private fun kotlinx.coroutines.test.TestScope.fixture(
        debounceMillis: Long,
    ): Fixture {
        val triggers = MutableSharedFlow<TrackPreparationTrigger>(extraBufferCapacity = 16)
        val operations = FakeTrackPreparationOperations()
        TrackPreparationCoordinator(
            scope = backgroundScope,
            triggers = triggers,
            operations = operations,
            diagnostics = TrackPreparationDiagnostics { },
            debounceMillis = debounceMillis,
        )
        runCurrent()
        return Fixture(triggers, operations)
    }

    private fun assertLocalOnlyPipeline(calls: List<String>, trackId: Long) {
        assertTrue("source:$trackId:false" in calls)
        assertTrue("runtime:$trackId:$trackId" in calls)
        assertFalse(calls.any { it.startsWith("plugin:") })
        assertFalse(calls.any { it.startsWith("artwork:") })
        assertFalse(calls.any { it.startsWith("lyrics:") })
        assertFalse(calls.any { it.startsWith("audio:") })
    }

    private fun assertMetadataAssetsAndRuntimeWithoutAudio(calls: List<String>, trackId: Long) {
        assertTrue("source:$trackId:true" in calls)
        assertTrue("plugin:$trackId" in calls)
        assertTrue("artwork:$trackId" in calls)
        assertTrue("lyrics:$trackId" in calls)
        assertTrue("runtime:$trackId:$trackId" in calls)
        assertFalse(calls.any { it.startsWith("audio:") })
    }
}

private data class Fixture(
    val triggers: MutableSharedFlow<TrackPreparationTrigger>,
    val operations: FakeTrackPreparationOperations,
)

private class FakeTrackPreparationOperations : TrackPreparationOperations {
    val calls = mutableListOf<String>()
    val snapshots = mutableMapOf<Long, TrackPreparationSnapshot>()
    val sourceRefreshSnapshots = mutableMapOf<Long, TrackPreparationSnapshot>()
    var snapshotHook: suspend (Long) -> Unit = {}
    var filenameTrack: TrackEntity? = null
    var pluginResult: PluginSemanticMetadataResult? = null
    var commitResult: MetadataApplyResult? = null
    var reconcileResult: TrackIdentityResult = TrackIdentityResult.Unchanged(0)
    var artworkCandidate: MetaSongCandidate? = null
    var lyricsCandidate: MetaSongCandidate? = null
    var runtimeRemap: Map<Long, Long> = emptyMap()

    override suspend fun snapshot(trackId: Long): TrackPreparationSnapshot? {
        calls += "snapshot:$trackId"
        snapshotHook(trackId)
        return snapshots[trackId]
    }

    override suspend fun refreshSourceMetadata(trackId: Long, allowNetwork: Boolean) {
        calls += "source:$trackId:$allowNetwork"
        sourceRefreshSnapshots[trackId]?.let { snapshots[trackId] = it }
    }

    override suspend fun applyFilenameMetadata(trackId: Long, fileName: String): TrackEntity? {
        calls += "filename:$trackId"
        return filenameTrack?.also { updated ->
            snapshots[trackId] = snapshots.getValue(trackId).copy(track = updated)
        }
    }

    override suspend fun findPluginCandidate(
        track: TrackEntity,
        filenameHints: FilenameMetadata?,
    ): PluginSemanticMetadataResult? {
        calls += "plugin:${track.id}"
        return pluginResult
    }

    override suspend fun commitPluginMetadata(
        trackId: Long,
        result: PluginSemanticMetadataResult,
    ): MetadataApplyResult {
        calls += "commit:$trackId"
        return requireNotNull(commitResult).also { applied ->
            applied.track?.let { updated ->
                snapshots[trackId] = snapshots.getValue(trackId).copy(track = updated)
            }
        }
    }

    override suspend fun reconcile(trackId: Long): TrackIdentityResult {
        calls += "reconcile:$trackId"
        return when (val result = reconcileResult) {
            is TrackIdentityResult.Unchanged -> if (result.trackId == 0L) result.copy(trackId = trackId) else result
            else -> result
        }
    }

    override suspend fun prepareArtwork(
        canonicalTrackId: Long,
        candidate: MetaSongCandidate?,
    ): Boolean {
        calls += "artwork:$canonicalTrackId"
        artworkCandidate = candidate
        return true
    }

    override suspend fun prepareLyrics(
        canonicalTrackId: Long,
        candidate: MetaSongCandidate?,
    ): Boolean {
        calls += "lyrics:$canonicalTrackId"
        lyricsCandidate = candidate
        return true
    }

    override suspend fun refreshRuntime(
        originalTrackId: Long,
        canonicalTrackId: Long,
        remappedTrackIds: Map<Long, Long>,
    ) {
        calls += "runtime:$originalTrackId:$canonicalTrackId"
        runtimeRemap = remappedTrackIds
    }

    override suspend fun preloadAudio(canonicalTrackId: Long, targetBytes: Long): Boolean {
        calls += "audio:$canonicalTrackId:$targetBytes"
        return true
    }
}

private fun trigger(
    nextTrackId: Long,
    playMode: PlayMode = PlayMode.LIST,
    shuffleEnabled: Boolean = false,
    settings: AppSettings = AppSettings.Default,
    network: NetworkStatus = NetworkStatus(isOnline = true, isMetered = false),
) = TrackPreparationTrigger(
    currentTrackId = 1,
    nextTrackId = nextTrackId,
    playlistId = 1,
    queueTrackIds = listOf(1, nextTrackId),
    playing = true,
    loading = false,
    playMode = playMode,
    shuffleEnabled = shuffleEnabled,
    settings = settings,
    network = network,
)

private fun snapshot(
    track: TrackEntity,
    sourcePath: String = "/Music/${track.title}.flac",
    sourceIdentities: List<TrackPreparationSourceIdentity> = emptyList(),
) = TrackPreparationSnapshot(
    track = track,
    sourcePath = sourcePath,
    album = track.albumId?.let { "Album" },
    artists = listOfNotNull(track.artist),
    genres = emptyList(),
    hasArtwork = false,
    hasLyrics = false,
    sourceRefCount = sourceIdentities.size,
    sourceIdentities = sourceIdentities,
)

private fun track(
    id: Long,
    title: String = "Song $id",
    metadataSource: String = TrackMetadataSources.File,
) = TrackEntity(
    id = id,
    title = title,
    sortTitle = null,
    albumId = 1,
    albumArtist = null,
    composer = null,
    comment = null,
    grouping = null,
    durationMs = 180_000,
    discNumber = null,
    discTotal = null,
    trackNumber = null,
    trackTotal = null,
    year = null,
    date = null,
    sampleRate = null,
    bitRate = null,
    bitsPerSample = null,
    channels = null,
    channelLayout = null,
    codec = null,
    container = null,
    lossless = null,
    createdAt = 1,
    updatedAt = 1,
    artist = "Artist",
    metadataSource = metadataSource,
)

private fun music(id: Long, title: String, path: String, order: UInt) = Music(
    meta = musicMeta(id, title, order),
    loc = StorageEntryLoc(StorageId(1), path),
    cover = null,
    lyric = null,
)

private fun musicAbstract(id: Long, title: String, order: UInt) = MusicAbstract(
    meta = musicMeta(id, title, order),
    cover = null,
)

private fun musicMeta(id: Long, title: String, order: UInt) = MusicMeta(
    id = MusicId(id),
    title = title,
    duration = 180_000.milliseconds,
    order = listOf(order),
)

private fun playlist(musics: List<MusicAbstract>) = Playlist(
    abstr = PlaylistAbstract(
        meta = PlaylistMeta(
            id = PlaylistId(1),
            title = "Queue",
            cover = null,
            showCover = null,
            createdTime = 0.milliseconds,
            order = listOf(1u),
        ),
        musicCount = musics.size.toULong(),
        duration = null,
    ),
    musics = musics,
)
