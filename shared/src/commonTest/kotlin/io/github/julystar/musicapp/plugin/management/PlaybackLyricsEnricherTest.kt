package io.github.julystar.musicapp.plugin.management

import io.github.julystar.musicapp.source.api.MetaSongCandidate
import io.github.julystar.musicapp.source.api.MetaSongQuery
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertNotEquals

class PlaybackLyricsEnricherTest {
    @Test
    fun fallsBackToTitleOnlySearchAndKeepsMetadataValidation() = runTest {
        val query = MetaSongQuery(
            title = "My Story,Your Song",
            artist = "孙燕姿 / 仓木麻衣",
            album = "未完成",
            durationMs = 275_933,
        )
        val queries = mutableListOf<MetaSongQuery>()

        val candidate = findBestLyricsCandidate(query) { searchQuery ->
            queries += searchQuery
            if (queries.size == 1) {
                listOf(
                    MetaSongCandidate(
                        id = "1460787",
                        title = "Tonight, I feel close to you",
                        artist = "仓木麻衣 / 孙燕姿",
                        durationMs = 245_000,
                    ),
                )
            } else {
                listOf(
                    MetaSongCandidate(
                        id = "wrong-artist",
                        title = "My Story,Your Song",
                        artist = "Another Artist",
                        durationMs = 275_000,
                    ),
                    MetaSongCandidate(
                        id = "preview",
                        title = "My Story,Your Song",
                        artist = "孙燕姿 / 仓木麻衣",
                        durationMs = 30_000,
                    ),
                    MetaSongCandidate(
                        id = "8143",
                        title = "My story, your song",
                        artist = "孙燕姿/仓木麻衣",
                        album = "未完成",
                        durationMs = 275_000,
                    ),
                )
            }
        }

        assertEquals("8143", candidate?.id)
        assertEquals(2, queries.size)
        assertNull(queries.first().keyword)
        assertEquals(query.title, queries.last().keyword)
    }

    @Test
    fun doesNotRunTitleFallbackWhenInitialSearchMatches() = runTest {
        val query = MetaSongQuery(
            title = "Song",
            artist = "Artist",
            durationMs = 180_000,
        )
        val queries = mutableListOf<MetaSongQuery>()

        val candidate = findBestLyricsCandidate(query) { searchQuery ->
            queries += searchQuery
            listOf(
                MetaSongCandidate(
                    id = "match",
                    title = "Song",
                    artist = "Artist",
                    durationMs = 180_000,
                ),
            )
        }

        assertEquals("match", candidate?.id)
        assertEquals(1, queries.size)
        assertNull(queries.single().keyword)
    }

    @Test
    fun matchesCandidateWithTrailingFeaturedArtist() {
        val candidate = MetaSongCandidate(
            id = "235175539",
            title = "Soon You'll Get Better (feat. Dixie Chicks)",
            artist = "Taylor Swift / The Chicks",
            album = "Lover",
            durationMs = 201_000,
        )

        assertNotNull(
            candidate.matchScore(
                MetaSongQuery(
                    title = "Soon You'll Get Better",
                    artist = "Taylor Swift",
                    album = "Lover",
                    durationMs = 201_589,
                ),
            ),
        )
    }

    @Test
    fun enforcesThreeSecondDurationDifferenceLimit() {
        val query = MetaSongQuery(
            title = "Song",
            artist = "Artist",
            durationMs = 200_000,
        )
        val candidate = MetaSongCandidate(
            id = "candidate",
            title = "Song",
            artist = "Artist",
            durationMs = 197_000,
        )

        assertNotNull(candidate.matchScore(query))
        assertNull(candidate.copy(durationMs = 196_999).matchScore(query))
    }

    @Test
    fun matchedCandidateNeverRepeatsSongSearch() = runTest {
        var searches = 0
        val candidate = MetaSongCandidate(id = "matched", title = "Song", sourceId = "plugin")

        val result = resolveLyricsSongCandidate(candidate, setOf("plugin")) {
            searches++
            null
        }

        assertEquals(candidate, result)
        assertEquals(0, searches)
        assertNotEquals(
            LyricsAttemptKey(1, null, null),
            LyricsAttemptKey(1, "plugin", "matched"),
        )
    }
}
