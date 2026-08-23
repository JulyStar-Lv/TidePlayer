package io.github.julystar.musicapp.metadata

import io.github.julystar.musicapp.database.LyricsEntity

/** Deterministic lyric winner selection; callers can provide user-preferred source kinds. */
object LyricsQualitySelector {
    fun select(
        current: LyricsEntity?,
        incoming: LyricsEntity?,
        preferredSourceKinds: Set<String> = emptySet(),
    ): LyricsEntity? {
        if (current == null) return incoming
        if (incoming == null) return current
        if (current.sourceKind != incoming.sourceKind) return current
        return listOf(current, incoming).maxWithOrNull(
            compareBy<LyricsEntity> {
                if (it.sourceKind in preferredSourceKinds) 1 else 0
            }.thenBy(::validity)
                .thenBy(::formatQuality)
                .thenBy(::translationCompleteness)
                .thenBy(::romanizationCompleteness)
                .thenBy { it.updatedAt }
                .thenBy { it.id },
        )
    }

    fun selectBySourceKind(
        values: Iterable<LyricsEntity>,
        preferredSourceKinds: Set<String> = emptySet(),
    ): List<LyricsEntity> = values.groupBy(LyricsEntity::sourceKind)
        .values.mapNotNull { group ->
            group.reduceOrNull { current, incoming -> select(current, incoming, preferredSourceKinds)!! }
        }.sortedWith(compareByDescending<LyricsEntity> { it.sourceKind in preferredSourceKinds }
            .thenByDescending(::validity)
            .thenByDescending(::formatQuality)
            .thenByDescending(::translationCompleteness)
            .thenByDescending(::romanizationCompleteness)
            .thenByDescending(LyricsEntity::updatedAt))

    private fun formatQuality(entity: LyricsEntity): Int {
        val format = entity.format.lowercase()
        return when {
            format.contains("ttml") -> 5
            entity.structuredContent?.contains("\"words\"", ignoreCase = true) == true ||
                WORD_TIMING.containsMatchIn(entity.content) -> 4
            entity.synchronized || format.contains("lrc") || format.contains("sync") -> 3
            format.contains("plain") || format.contains("text") -> 1
            else -> 2
        }
    }

    private fun validity(entity: LyricsEntity): Int = when {
        entity.content.isBlank() -> 0
        entity.content.length > 20_000_000 -> 0
        entity.content.any(Char::isLetterOrDigit) -> 2
        else -> 1
    }

    private fun translationCompleteness(entity: LyricsEntity): Int =
        completeness(entity, "translation", "translated")

    private fun romanizationCompleteness(entity: LyricsEntity): Int =
        completeness(entity, "romanization", "romanized", "romaji")

    private fun completeness(entity: LyricsEntity, vararg keys: String): Int {
        val content = entity.structuredContent.orEmpty() + '\n' + entity.content
        return keys.sumOf { key -> Regex("(?i)\\\"?$key\\\"?\\s*[:=]\\s*(?!null|\\\"\\\")").findAll(content).count() }
    }

    private val WORD_TIMING = Regex("<(?:\\d{1,2}:)?\\d{1,2}[.:]\\d{2,3}>")
}
