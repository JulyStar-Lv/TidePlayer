package io.github.julystar.musicapp.metadata

/** The small, deterministic subset of filename conventions useful before network lookup. */
data class FilenameMetadata(
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val raw: String = title,
    val normalized: String = title.normalizedFilenameKey(),
    val parent: String? = null,
    val grandparent: String? = null,
    val durationMs: Long? = null,
    val hints: Set<String> = emptySet(),
    val confidence: FilenameMetadataConfidence = FilenameMetadataConfidence.LOW,
)

enum class FilenameMetadataConfidence { HIGH, MEDIUM, LOW }

object FilenameMetadataParser {
    private val extension = Regex("\\.(?:mp3|m4a|mp4|flac|ogg|opus|wav|aac|ape|wma)$", RegexOption.IGNORE_CASE)
    private val trackPrefix = Regex("^(?:(\\d{1,2})[-_. ](\\d{1,2})|((?:\\d{1,2})))\\s*[-_. ]\\s*(.+)$")
    private val artistTitle = Regex("^(.+?)(?:\\s+-\\s+|\\s*[–—－]\\s*)(.+)$")
    private val artistAlbumTrackTitle = Regex("^(.+?)\\s*[-–—－_]\\s*(.+?)\\s*[-–—－_]\\s*(\\d{1,2})\\s*[-–—－_]\\s*(.+)$")
    private val trackArtistTitle = Regex("^(\\d{1,2})\\s*[-_. ]\\s*(.+?)\\s*[-–—－_]\\s*(.+)$")
    private val discTrack = Regex("^(\\d{1,2})[-.]?(\\d{1,2})\\s+(.+)$")

    fun parse(
        fileName: String,
        parent: String? = null,
        grandparent: String? = null,
        durationMs: Long? = null,
    ): FilenameMetadata? {
        val raw = fileName.substringAfterLast('/').substringAfterLast('\\').trim()
        val base = raw
            .replace(extension, "")
            .trim()
        if (base.isBlank()) return null
        val hints = technicalHints(base)
        // Directory names are context only. They are intentionally not promoted to album identity.
        val album: String? = null

        Regex("^(\\d{1,2})[-_. ](\\d{1,2})\\s*[-_. ]\\s*(.+)$").matchEntire(base)?.let { match ->
            return finish(
                raw = raw,
                title = match.groupValues[3],
                album = album,
                trackNumber = match.groupValues[2].toIntOrNull(),
                discNumber = match.groupValues[1].toIntOrNull(),
                parent = parent,
                grandparent = grandparent,
                durationMs = durationMs,
                hints = hints,
                confidence = FilenameMetadataConfidence.MEDIUM,
            )
        }

        artistAlbumTrackTitle.matchEntire(base)?.let { match ->
            return finish(
                raw = raw,
                title = match.groupValues[4],
                artist = match.groupValues[1],
                album = match.groupValues[2].takeIf(String::isNotBlank) ?: album,
                trackNumber = match.groupValues[3].toIntOrNull(),
                parent = parent,
                grandparent = grandparent,
                durationMs = durationMs,
                hints = hints,
                confidence = FilenameMetadataConfidence.HIGH,
            )
        }
        trackArtistTitle.matchEntire(base)?.let { match ->
            return finish(
                raw = raw,
                title = match.groupValues[3],
                artist = match.groupValues[2],
                album = album,
                trackNumber = match.groupValues[1].toIntOrNull(),
                parent = parent,
                grandparent = grandparent,
                durationMs = durationMs,
                hints = hints,
                confidence = FilenameMetadataConfidence.HIGH,
            )
        }

        val numbered = trackPrefix.matchEntire(base)
        if (numbered != null) {
            val disc = numbered.groupValues[1].toIntOrNull()
            val track = (numbered.groupValues[2].ifBlank { numbered.groupValues[3] }).toIntOrNull()
            val title = numbered.groupValues[4].trim()
            if (title.isNotBlank()) return finish(
                raw, title, null, album, track, disc, parent, grandparent, durationMs, hints,
                FilenameMetadataConfidence.MEDIUM,
            )
        }
        val discAndTrack = discTrack.matchEntire(base)
        if (discAndTrack != null) {
            return finish(
                raw = raw,
                title = discAndTrack.groupValues[3].trim(),
                trackNumber = discAndTrack.groupValues[2].toIntOrNull(),
                discNumber = discAndTrack.groupValues[1].toIntOrNull(),
                album = album,
                parent = parent,
                grandparent = grandparent,
                durationMs = durationMs,
                hints = hints,
                confidence = FilenameMetadataConfidence.MEDIUM,
            )
        }
        val split = artistTitle.matchEntire(base)
        if (split != null) {
            return finish(
                raw = raw,
                title = split.groupValues[2].trim(),
                artist = split.groupValues[1].trim().takeIf(String::isNotBlank),
                album = album,
                parent = parent,
                grandparent = grandparent,
                durationMs = durationMs,
                hints = hints,
                confidence = FilenameMetadataConfidence.HIGH,
            )
        }
        return finish(
            raw = raw,
            title = base,
            album = album,
            parent = parent,
            grandparent = grandparent,
            durationMs = durationMs,
            hints = hints,
            confidence = if (album != null) FilenameMetadataConfidence.MEDIUM else FilenameMetadataConfidence.LOW,
        )
    }

    private fun finish(
        raw: String,
        title: String,
        artist: String? = null,
        album: String? = null,
        trackNumber: Int? = null,
        discNumber: Int? = null,
        parent: String? = null,
        grandparent: String? = null,
        durationMs: Long? = null,
        hints: Set<String> = emptySet(),
        confidence: FilenameMetadataConfidence,
    ): FilenameMetadata {
        val cleanTitle = cleanTechnicalTags(title)
        return FilenameMetadata(
            title = cleanTitle,
            artist = artist?.let(::cleanTechnicalTags),
            album = album?.let(::cleanTechnicalTags),
            trackNumber = trackNumber,
            discNumber = discNumber,
            raw = raw,
            normalized = cleanTitle.normalizedFilenameKey(),
            parent = parent,
            grandparent = grandparent,
            durationMs = durationMs,
            hints = hints,
            confidence = confidence,
        )
    }

    private fun technicalHints(value: String): Set<String> =
        Regex("(?i)(?:\\[([^]]+)]|\\(([^)]+)\\))")
            .findAll(value)
            .flatMap { match -> match.groupValues.drop(1).asSequence() }
            .map(String::trim)
            .filter { it.isNotBlank() }
            .toSet()

    private fun cleanTechnicalTags(value: String): String {
        val technical = Regex(
            "(?i)^(?:flac|mp3|m4a|aac|alac|wav|ogg|opus|\\d{3,4}k(?:bps)?|(?:16|24|32)bit|(?:44[.]1|48|88[.]2|96|192)khz|hi[- ]?res|lossless|official audio|official video)$",
        )
        return value.replace(Regex("""\s*(?:\[([^\]]+)]|\(([^)]+)\))""")) { match ->
            val tag = (match.groupValues[1].ifBlank { match.groupValues[2] }).trim()
            val pureTechnical = technical.matches(tag) || tag.split(Regex("\\s+|[,/&]+"))
                .filter(String::isNotBlank)
                .all { technical.matches(it) }
            if (pureTechnical) "" else match.value
        }.trim().ifBlank { value.trim() }
    }
}

private fun String.normalizedFilenameKey(): String =
    trim().lowercase().replace(Regex("[\\s_]+"), " ").replace(Regex("[-–—]+"), "-")
