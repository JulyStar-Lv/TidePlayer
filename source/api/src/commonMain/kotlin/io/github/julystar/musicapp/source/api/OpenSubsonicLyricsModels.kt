package io.github.julystar.musicapp.source.api

data class OpenSubsonicExtension(val name: String, val versions: List<Int> = emptyList())

enum class OpenSubsonicLyricsTrackKind { Main, Translation, Pronunciation }

data class OpenSubsonicLyricsAgent(val id: String, val name: String? = null, val role: String? = null)

data class OpenSubsonicCue(
    val startMs: Long? = null,
    val endMs: Long? = null,
    val value: String,
    /** UTF-8 byte offsets into the parent cueLine.value, not character offsets. */
    val byteStart: Int? = null,
    /** UTF-8 byte offsets into the parent cueLine.value, not character offsets. */
    val byteEnd: Int? = null,
)

data class OpenSubsonicLyricsLine(val startMs: Long? = null, val value: String)

data class OpenSubsonicCueLine(
    val index: Int,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val value: String,
    val agentId: String? = null,
    val cues: List<OpenSubsonicCue> = emptyList(),
)

data class OpenSubsonicLyricsTrack(
    val kind: OpenSubsonicLyricsTrackKind,
    val displayArtist: String? = null,
    val displayTitle: String? = null,
    val language: String? = null,
    val offsetMs: Long? = null,
    val synced: Boolean? = null,
    val lines: List<OpenSubsonicLyricsLine> = emptyList(),
    val agents: List<OpenSubsonicLyricsAgent> = emptyList(),
    val cueLines: List<OpenSubsonicCueLine> = emptyList(),
)

data class OpenSubsonicStructuredLyricsDocument(
    val tracks: List<OpenSubsonicLyricsTrack> = emptyList(),
)

data class OpenSubsonicCapabilitySnapshot(
    val extensions: List<OpenSubsonicExtension> = emptyList(),
    val checkedAtEpochMs: Long,
)
