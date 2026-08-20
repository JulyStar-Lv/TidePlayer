package io.github.julystar.musicapp.source.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MetaSongQuery(
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val date: String? = null,
    val durationMs: Long? = null,
    val config: Map<String, String> = emptyMap(),
    val keyword: String? = null,
    val page: Int = 1,
    val pageSize: Int = 20,
    val separator: String = "/",
    val song: MetaSongCandidate? = null,
) {
    override fun toString(): String =
        "MetaSongQuery(" +
            "title=$title, artist=$artist, album=$album, date=$date, durationMs=$durationMs, " +
            "config=<redacted>, keyword=$keyword, page=$page, pageSize=$pageSize, " +
            "separator=$separator, song=$song)"
}

data class MetaSongCandidate(
    val id: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val date: String? = null,
    val trackNumber: String? = null,
    val pictureUrl: String? = null,
    val fields: Map<String, String> = emptyMap(),
    val contextToken: String? = null,
    val sourceId: String? = null,
) {
    override fun toString(): String =
        "MetaSongCandidate(" +
            "id=$id, title=$title, artist=$artist, album=$album, durationMs=$durationMs, " +
            "date=$date, trackNumber=$trackNumber, pictureUrl=<redacted>, fields=<redacted>, " +
            "contextToken=<redacted>, sourceId=$sourceId)"
}

data class MetaCoverCandidate(
    val url: String,
    val id: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val date: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val sourceId: String? = null,
) {
    override fun toString(): String =
        "MetaCoverCandidate(" +
            "url=<redacted>, id=$id, title=$title, artist=$artist, album=$album, date=$date, " +
            "width=$width, height=$height, sourceId=$sourceId)"
}

data class MetaLyricWord(
    val text: String,
    val startMs: Long? = null,
    val endMs: Long? = null,
)

data class MetaLyricLine(
    val text: String,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val words: List<MetaLyricWord> = emptyList(),
    val translation: String? = null,
    val romanization: String? = null,
    val person: String? = null,
)

data class MetaLyrics(
    val lines: List<MetaLyricLine> = emptyList(),
    val rawPlainLrc: String? = null,
    val rawVerbatimLrc: String? = null,
    val rawEnhancedLrc: String? = null,
    val rawTtml: String? = null,
    val rawMultiPersonEnhancedLrc: String? = null,
    val translated: String? = null,
    val romanization: String? = null,
)

data class MetaLyricsCandidate(
    val id: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val date: String? = null,
    val lyrics: MetaLyrics,
    val sourceId: String,
)

enum class MetaSourceCapability {
    SEARCH_SONGS,
    GET_LYRICS,
    SEARCH_COVERS,
}

interface MetaSource {
    val id: String
    val displayName: String
    val capabilities: Set<MetaSourceCapability>
        get() = MetaSourceCapability.entries.toSet()

    suspend fun searchSongs(query: MetaSongQuery): List<MetaSongCandidate>

    suspend fun getLyrics(
        candidate: MetaSongCandidate,
        config: Map<String, String> = emptyMap(),
    ): MetaLyrics?

    suspend fun getLyricsCandidates(
        candidate: MetaSongCandidate,
        page: Int = 1,
        pageSize: Int = 20,
        config: Map<String, String> = emptyMap(),
    ): List<MetaLyricsCandidate> = getLyrics(candidate, config)?.let { lyrics ->
        listOf(
            MetaLyricsCandidate(
                id = candidate.id,
                title = candidate.title,
                artist = candidate.artist,
                album = candidate.album,
                date = candidate.date,
                lyrics = lyrics,
                sourceId = candidate.sourceId ?: id,
            ),
        )
    }.orEmpty()

    suspend fun searchCovers(query: MetaSongQuery): List<MetaCoverCandidate>
}

class MetaSourceRegistry(
    sources: Collection<MetaSource> = emptyList(),
) {
    private val mutableSources = MutableStateFlow(validate(sources))

    val sourcesFlow: StateFlow<List<MetaSource>> = mutableSources.asStateFlow()
    val sources: List<MetaSource>
        get() = mutableSources.value

    fun replace(sources: Collection<MetaSource>) {
        mutableSources.value = validate(sources)
    }

    fun sourceOrNull(id: String): MetaSource? =
        mutableSources.value.firstOrNull { it.id == id }

    private companion object {
        fun validate(sources: Collection<MetaSource>): List<MetaSource> {
            val list = sources.toList()
            require(list.map(MetaSource::id).distinct().size == list.size) {
                "MetaSource IDs must be unique"
            }
            return list
        }
    }
}
