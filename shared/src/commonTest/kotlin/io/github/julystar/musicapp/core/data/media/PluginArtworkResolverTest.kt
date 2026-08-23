package io.github.julystar.musicapp.core.data.media

import io.github.julystar.musicapp.plugin.management.PluginSummary
import io.github.julystar.musicapp.plugin.runtime.PluginLookupMode
import io.github.julystar.musicapp.source.api.MetaCoverCandidate
import io.github.julystar.musicapp.source.api.MetaSongCandidate
import io.github.julystar.musicapp.source.api.MetaSongQuery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class PluginArtworkResolverTest {
    @Test
    fun automaticAndBatchLookupsRespectTheirSeparatePermissions() {
        val plugins = listOf(
            pluginSummary(id = "automatic", allowAutomatic = true),
            pluginSummary(id = "batch", allowBatch = true),
            pluginSummary(id = "disabled", allowAutomatic = true, enabled = false),
        )

        assertEquals(
            listOf("automatic"),
            plugins.artworkPlugins(PluginLookupMode.AUTOMATIC).map(PluginSummary::id),
        )
        assertEquals(
            listOf("batch"),
            plugins.artworkPlugins(PluginLookupMode.BATCH).map(PluginSummary::id),
        )
    }

    @Test
    fun selectsTheBestMatchingSongCoverInsteadOfTheFirstResult() {
        val query = MetaSongQuery(
            title = "Night Drive",
            artist = "The Waves",
            durationMs = 180_000L,
        )

        val selected = selectPluginSongArtworkUrl(
            query = query,
            candidates = listOf(
                MetaSongCandidate(
                    id = "wrong",
                    title = "Night Drive",
                    artist = "Someone Else",
                    durationMs = 180_000L,
                    pictureUrl = "https://example.test/wrong.jpg",
                ),
                MetaSongCandidate(
                    id = "right",
                    title = "Night Drive",
                    artist = "The Waves",
                    durationMs = 181_000L,
                    pictureUrl = "https://example.test/right.jpg",
                ),
            ),
        )

        assertEquals("https://example.test/right.jpg", selected)
    }

    @Test
    fun selectsTheLargestDedicatedCoverCandidate() {
        val selected = selectPluginCoverArtworkUrl(
            listOf(
                MetaCoverCandidate("https://example.test/small.jpg", width = 300, height = 300),
                MetaCoverCandidate("https://example.test/large.jpg", width = 1_000, height = 1_000),
            ),
        )

        assertEquals("https://example.test/large.jpg", selected)
    }

    @Test
    fun matchedCandidateNeverRepeatsSongSearch() = runTest {
        var searches = 0
        val candidate = MetaSongCandidate(id = "matched", title = "Song", sourceId = "plugin")

        val result = resolveArtworkSongCandidates(candidate) {
            searches++
            emptyList()
        }

        assertEquals(listOf(candidate), result)
        assertEquals(0, searches)
    }
}

private fun pluginSummary(
    id: String,
    allowAutomatic: Boolean = false,
    allowBatch: Boolean = false,
    enabled: Boolean = true,
): PluginSummary = PluginSummary(
    id = id,
    name = id,
    versionName = "1.0.0",
    versionCode = 1,
    author = "Test",
    description = "",
    capabilities = listOf("searchSongs", "searchCovers"),
    enabled = enabled,
    allowManualLookup = false,
    allowAutomaticLookup = allowAutomatic,
    allowBatchLookup = allowBatch,
    installedAt = 1,
    updatedAt = 1,
    entryFile = "source.js",
    includeDirs = emptyList(),
    iconPath = null,
    configFields = emptyList(),
    lastError = null,
    lastErrorAt = null,
)
