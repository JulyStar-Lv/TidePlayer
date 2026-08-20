package io.github.julystar.musicapp.source.server

import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EmbyTrackPagerTest {
    @Test
    fun pagesAllItemsWithRawOffsetsAndRequiredQuery() = runBlocking {
        val requests = mutableListOf<Map<String, String>>()
        val pager = EmbyTrackPager(
            accountId = SourceAccountId("storage:7"),
            userId = "user-opaque",
            pageSize = 500,
            request = { path, params ->
                assertEquals("Users/user-opaque/Items", path)
                requests += params
                val offset = params.getValue("StartIndex").toInt()
                val limit = params.getValue("Limit").toInt()
                val end = minOf(offset + limit, 25_000)
                buildString {
                    append("{\"Items\":[")
                    for (index in offset until end) {
                        if (index > offset) append(',')
                        append("{\"Id\":\"id-$index\",\"Name\":\"Song $index\"}")
                    }
                    append("]}")
                }
            },
        )

        val results = pager.pages("  ").toList()
        val pages = results.map { it.getOrThrow() }
        assertEquals(50, pages.size)
        assertEquals(25_000, pages.sumOf { it.tracks.size })
        assertEquals((0 until 25_000).map { "id-$it" }, pages.flatMap { it.tracks }.map { it.remoteId })
        assertEquals(
            (0..25_000 step 500).toList(),
            requests.map { it.getValue("StartIndex").toInt() },
        )
        assertTrue(requests.all { it["Limit"] == "500" })
        assertTrue(requests.all { it["Recursive"] == "true" })
        assertEquals("Audio", requests.first()["IncludeItemTypes"])
        assertEquals("Genres,MediaSources,MediaStreams,AlbumArtist,UserData", requests.first()["Fields"])
        assertFalse(requests.first().containsKey("SearchTerm"))
    }

    @Test
    fun mapsMetadataAndSelectsRequestedDefaultMediaSourceDeterministically() = runBlocking {
        val pager = EmbyTrackPager(
            accountId = SourceAccountId("storage:7"),
            userId = "user-1",
            pageSize = 10,
            request = { _, _ ->
                """{"Items":[{"Id":"opaque/id","Name":"Name","Artists":["A","A"," B "],"AlbumArtist":"Album Artist","Album":"Album","AlbumId":"album-1","Genres":["Rock","Rock"," Jazz "],"ProductionYear":2024,"IndexNumber":3,"ParentIndexNumber":2,"RunTimeTicks":90000000,"ImageTags":{"Primary":"tag-1"},"UserData":{"IsFavorite":true,"PlayCount":4,"LastPlayedDate":"2024-01-01","Played":true},"MediaSourceId":"source-2","MediaSources":[{"Id":"source-1","IsDefault":true,"Container":"mp3","MediaStreams":[{"Index":0,"Type":"Audio","Codec":"mp3"}]},{"Id":"source-2","Container":"flac","Bitrate":900000,"MediaStreams":[{"Index":1,"Type":"Audio","Codec":"flac","SampleRate":96000,"BitDepth":24,"Channels":2,"ChannelLayout":"stereo"}]}],"MediaStreams":[{"Index":9,"Type":"Video"}]}]}"""
            },
        )
        val result = pager.pages("query").toList().single().getOrThrow().tracks.single()
        assertEquals("opaque/id", result.remoteId)
        assertEquals(listOf("A", "B"), result.artists)
        assertEquals(listOf("Rock", "Jazz"), result.genres)
        assertEquals("album-1", result.albumId)
        assertEquals(2024, result.productionYear)
        assertEquals(3, result.indexNumber)
        assertEquals(2, result.parentIndexNumber)
        assertEquals(90_000_000L, result.runTimeTicks)
        assertEquals("source-2", result.sourceMediaId)
        assertEquals("tag-1", result.imageTag)
        assertEquals(true, result.userData?.isFavorite)
        assertEquals(4, result.userData?.playCount)
        assertEquals("FLAC", result.audioProperties?.codec)
        assertEquals(96_000, result.audioProperties?.sampleRateHz)
        assertEquals(24, result.audioProperties?.bitDepth)
        assertEquals(2, result.audioProperties?.channels)
        assertEquals("stereo", result.audioProperties?.channelLayout)
        assertEquals(2, result.mediaSources.size)
        assertEquals(1, result.mediaSources[1].mediaStreams.size)
        assertTrue(result.streamUrl == null || !result.streamUrl!!.contains("token"))
        assertTrue(result.coverUrl == null)
    }

    @Test
    fun repeatedRawPageFailsSoSnapshotDeletionCannotRun() = runBlocking {
        var calls = 0
        val pager = EmbyTrackPager(
            accountId = SourceAccountId("storage:7"),
            userId = "user-1",
            pageSize = 2,
            request = { _, _ ->
                calls++
                """{"Items":[{"Id":"one","Name":"One"},{"Id":"two","Name":"Two"}]}"""
            },
        )
        val results = pager.pages(null).toList()
        assertEquals(2, calls)
        assertEquals(2, results.size)
        assertEquals(listOf("one", "two"), results.first().getOrThrow().tracks.map { it.remoteId })
        assertTrue(results.last().isFailure)
        assertNotNull(results.last().exceptionOrNull())
        Unit
    }

    @Test
    fun malformedItemsStillAdvanceByRawPageSizeAndTotalCountIsAdvisory() = runBlocking {
        val offsets = mutableListOf<Int>()
        val pager = EmbyTrackPager(
            accountId = SourceAccountId("storage:7"),
            userId = "user-1",
            pageSize = 2,
            request = { _, params ->
                val offset = params.getValue("StartIndex").toInt()
                offsets += offset
                if (offset == 0) {
                    """{"TotalRecordCount":1,"Items":[{"Name":"missing id"},{"Id":"opaque-1"}]}"""
                } else {
                    """{"TotalRecordCount":-1,"Items":[{"Id":"opaque-2"}]}"""
                }
            },
        )
        val pages = pager.pages(null).toList().map { it.getOrThrow() }
        assertEquals(listOf(0, 2), offsets)
        assertEquals(listOf("opaque-1", "opaque-2"), pages.flatMap { it.tracks }.map { it.remoteId })
    }

    @Test
    fun nonAdjacentFullPageLoopFailsWithoutUnboundedRequests() = runBlocking {
        var calls = 0
        val pager = EmbyTrackPager(
            accountId = SourceAccountId("storage:7"),
            userId = "user-1",
            pageSize = 2,
            request = { _, params ->
                calls++
                when (params.getValue("StartIndex").toInt()) {
                    0, 4 -> """{"Items":[{"Id":"a"},{"Id":"b"}]}"""
                    else -> """{"Items":[{"Id":"c"},{"Id":"d"}]}"""
                }
            },
        )
        val results = pager.pages(null).toList()
        assertEquals(3, calls)
        assertEquals(2, results.count { it.isSuccess })
        assertTrue(results.last().isFailure)
    }

    @Test
    fun missingOrWrongTypedItemsIsFailureButEmptyItemsIsValidEnd() = runBlocking {
        val missing = EmbyTrackPager(
            SourceAccountId("storage:7"), "user-1", 2,
        ) { _, _ -> "{}" }.pages(null).toList()
        assertTrue(missing.single().isFailure)

        val wrongType = EmbyTrackPager(
            SourceAccountId("storage:7"), "user-1", 2,
        ) { _, _ -> "{\"Items\":{}}" }.pages(null).toList()
        assertTrue(wrongType.single().isFailure)

        val empty = EmbyTrackPager(
            SourceAccountId("storage:7"), "user-1", 2,
        ) { _, _ -> "{\"Items\":[]}" }.pages(null).toList()
        assertTrue(empty.isEmpty())
    }
}
