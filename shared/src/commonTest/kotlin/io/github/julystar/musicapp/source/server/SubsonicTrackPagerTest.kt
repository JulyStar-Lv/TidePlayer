package io.github.julystar.musicapp.source.server

import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.source.api.RemoteServerTrackPage
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubsonicTrackPagerTest {
    @Test
    fun streamsTwentyFiveThousandSongsWithoutDuplicates() = runTest {
        val offsets = mutableListOf<Int>()
        val pager = SubsonicTrackPager(
            accountId = SourceAccountId("storage:7"),
            pageSize = 500,
            request = { endpoint, params ->
                assertEquals("search3", endpoint)
                val offset = params.getValue("songOffset").toInt()
                offsets += offset
                val end = minOf(offset + 500, 25_000)
                val songs = (offset until end).joinToString(",") { index ->
                    "{\"id\":\"song-$index-音乐😀\",\"title\":\"Song $index\"}"
                }
                "{\"subsonic-response\":{\"status\":\"ok\",\"searchResult3\":{\"song\":[$songs]}}}".endpointResult()
            },
            resourceUrl = { _, endpoint, params ->
                "https://example.test/$endpoint?id=${params.getValue("id")}"
            },
        )

        val pages = mutableListOf<RemoteServerTrackPage>()
        pager.pages(null).collect { pages += it.getOrThrow() }

        val ids = pages.flatMap { it.tracks }.map { it.remoteId }
        assertEquals(50, pages.size)
        assertTrue(pages.all { it.tracks.size <= 500 })
        assertEquals(25_000, ids.size)
        assertEquals(25_000, ids.toSet().size)
        assertEquals((0..25_000 step 500).toList(), offsets)
    }

    @Test
    fun mapsSubsonicMetadataForLibrarySync() = runTest {
        val pager = SubsonicTrackPager(
            accountId = SourceAccountId("storage:7"),
            pageSize = 10,
            request = { endpoint, _ ->
                assertEquals("search3", endpoint)
                """
                {"subsonic-response":{"status":"ok","searchResult3":{"song":[{
                  "id":"opaque/音乐😀?","title":"Title","artist":"Artist","album":"Album",
                  "albumArtist":"Album Artist","genre":"Genre","year":2024,"track":3,"discNumber":2,
                  "duration":245,"suffix":"flac","contentType":"audio/flac","bitRate":900,
                  "samplingRate":96000,"bitDepth":24,"channelCount":2
                }]}}}
                """.trimIndent().endpointResult()
            },
            resourceUrl = { _, endpoint, params ->
                "https://example.test/$endpoint?id=${params.getValue("id")}"
            },
        )

        val results = mutableListOf<RemoteServerTrackPage>()
        pager.pages("needle").collect { results += it.getOrThrow() }
        val track = results.single().tracks.single()
        assertEquals("opaque/音乐😀?", track.remoteId)
        assertEquals("Artist", track.artist)
        assertEquals("Album Artist", track.albumArtist)
        assertEquals("Genre", track.genre)
        assertEquals(2024, track.year)
        assertEquals(3, track.track)
        assertEquals(2, track.discNumber)
        assertEquals(245_000L, track.durationMs)
        assertEquals("flac", track.suffix)
        assertEquals("audio/flac", track.mimeType)
        assertEquals(900, track.bitRate)
        assertEquals(96_000, track.sampleRate)
        assertEquals(24, track.bitDepth)
        assertEquals(2, track.channelCount)
    }

    @Test
    fun blankSearchWithEmptyFirstPageFallsBackToAlbums() = runTest {
        val endpoints = mutableListOf<String>()
        val pager = SubsonicTrackPager(
            accountId = SourceAccountId("storage:7"),
            pageSize = 2,
            request = { endpoint, params ->
                endpoints += endpoint
                when (endpoint) {
                    "search3" -> subsonicSongs().endpointResult()
                    "getAlbumList2" -> subsonicAlbums("album-only").endpointResult()
                    "getAlbum" -> {
                        assertEquals("album-only", params.getValue("id"))
                        subsonicAlbumSongs("album-song").endpointResult()
                    }
                    else -> error("unexpected endpoint $endpoint")
                }
            },
            resourceUrl = { _, endpoint, params ->
                "https://example.test/$endpoint?id=${params.getValue("id")}"
            },
        )

        val pages = mutableListOf<RemoteServerTrackPage>()
        pager.pages("").collect { pages += it.getOrThrow() }

        assertEquals(listOf("search3", "getAlbumList2", "getAlbum"), endpoints)
        assertEquals(listOf("album-song"), pages.flatMap { it.tracks }.map { it.remoteId })
    }

    @Test
    fun shortSearchPageStopsWithoutFallbackOrNextPage() = runTest {
        val endpoints = mutableListOf<String>()
        val pager = SubsonicTrackPager(
            accountId = SourceAccountId("storage:7"),
            pageSize = 2,
            request = { endpoint, _ ->
                endpoints += endpoint
                when (endpoint) {
                    "search3" -> subsonicSongs("short-song").endpointResult()
                    else -> error("short page must not trigger fallback")
                }
            },
            resourceUrl = { _, endpoint, params ->
                "https://example.test/$endpoint?id=${params.getValue("id")}"
            },
        )

        val pages = mutableListOf<RemoteServerTrackPage>()
        pager.pages("").collect { pages += it.getOrThrow() }

        assertEquals(listOf("search3"), endpoints)
        assertEquals(listOf("short-song"), pages.flatMap { it.tracks }.map { it.remoteId })
    }

    @Test
    fun repeatedBlankSearchAfterIgnoredOffsetFallsBackAndStops() = runTest {
        val searchOffsets = mutableListOf<Int>()
        val albumOffsets = mutableListOf<Int>()
        val pager = SubsonicTrackPager(
            accountId = SourceAccountId("storage:7"),
            pageSize = 2,
            request = { endpoint, params ->
                when (endpoint) {
                    "search3" -> {
                        searchOffsets += params.getValue("songOffset").toInt()
                        subsonicSongs("primary-1", "primary-2").endpointResult()
                    }
                    "getAlbumList2" -> {
                        albumOffsets += params.getValue("offset").toInt()
                        subsonicAlbums("album-a", "album-b").endpointResult()
                    }
                    "getAlbum" -> when (params.getValue("id")) {
                        "album-a" -> subsonicAlbumSongs("primary-1", "album-a-song").endpointResult()
                        "album-b" -> subsonicAlbumSongs("album-b-song").endpointResult()
                        else -> error("unexpected album")
                    }
                    else -> error("unexpected endpoint $endpoint")
                }
            },
            resourceUrl = { _, endpoint, params ->
                "https://example.test/$endpoint?id=${params.getValue("id")}"
            },
        )

        val pages = mutableListOf<RemoteServerTrackPage>()
        pager.pages("").collect { pages += it.getOrThrow() }

        assertEquals(listOf(0, 2), searchOffsets)
        assertEquals(listOf(0, 2), albumOffsets)
        assertEquals(
            listOf("primary-1", "primary-2", "album-a-song", "album-b-song"),
            pages.flatMap { it.tracks }.map { it.remoteId },
        )
    }

    @Test
    fun repeatedNonBlankSearchStopsWithoutAlbumFallback() = runTest {
        val endpoints = mutableListOf<String>()
        val pager = SubsonicTrackPager(
            accountId = SourceAccountId("storage:7"),
            pageSize = 2,
            request = { endpoint, _ ->
                endpoints += endpoint
                when (endpoint) {
                    "search3" -> subsonicSongs("same-1", "same-2").endpointResult()
                    else -> error("album fallback must not run")
                }
            },
            resourceUrl = { _, endpoint, params ->
                "https://example.test/$endpoint?id=${params.getValue("id")}"
            },
        )

        val pages = mutableListOf<RemoteServerTrackPage>()
        pager.pages("needle").collect { pages += it.getOrThrow() }

        assertEquals(listOf("search3", "search3"), endpoints)
        assertEquals(2, pages.single().tracks.size)
    }
}

private fun subsonicSongs(vararg ids: String): String =
    "{\"subsonic-response\":{\"status\":\"ok\",\"searchResult3\":{\"song\":[" +
        ids.joinToString(",") { "{\"id\":\"$it\",\"title\":\"$it\"}" } +
        "]}}}"

private fun subsonicAlbums(vararg ids: String): String =
    "{\"subsonic-response\":{\"status\":\"ok\",\"albumList2\":{\"album\":[" +
        ids.joinToString(",") { "{\"id\":\"$it\",\"name\":\"$it\"}" } +
        "]}}}"

private fun subsonicAlbumSongs(vararg ids: String): String =
    "{\"subsonic-response\":{\"status\":\"ok\",\"album\":{\"song\":[" +
        ids.joinToString(",") { "{\"id\":\"$it\",\"title\":\"$it\"}" } +
        "]}}}"

private fun String.endpointResult(
    endpoint: String = "https://primary.example",
): RemoteServerEndpointResult<String> = RemoteServerEndpointResult(this, endpoint)
