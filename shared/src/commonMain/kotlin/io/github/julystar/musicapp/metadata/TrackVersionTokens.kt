package io.github.julystar.musicapp.metadata

/** Shared version semantics used by plugin matching and library identity reconciliation. */
object TrackVersionTokens {
    private val patterns = listOf(
        "radio-edit" to Regex("(?i)\\bradio[ \\-_]+edit\\b"),
        "extended" to Regex("(?i)\\bextended(?:[ \\-_]+mix)?\\b"),
        "alternate-take" to Regex("(?i)\\balternate[ \\-_]+take\\b"),
        "re-recorded" to Regex("(?i)\\bre[ \\-_]?record(?:ed|ing)?\\b"),
        "remaster" to Regex("(?i)\\bremaster(?:ed)?\\b"),
        "instrumental" to Regex("(?i)\\binstrumental\\b"),
        "acoustic" to Regex("(?i)\\bacoustic\\b"),
        "karaoke" to Regex("(?i)\\bkaraoke\\b"),
        "remix" to Regex("(?i)\\bremix\\b"),
        "live" to Regex("(?i)\\blive\\b"),
        "demo" to Regex("(?i)\\bdemo\\b"),
        "cover" to Regex("(?i)\\bcover\\b"),
        "mono" to Regex("(?i)\\bmono\\b"),
        "stereo" to Regex("(?i)\\bstereo\\b"),
        "edit" to Regex("(?i)\\bedit\\b"),
    )

    fun extract(value: String?): Set<String> = buildSet {
        val text = value.orEmpty()
        patterns.forEach { (token, pattern) ->
            if (pattern.containsMatchIn(text)) add(token)
        }
        if ("radio-edit" in this) remove("edit")
    }

    fun conflict(left: String?, right: String?): Boolean = extract(left) != extract(right)

    fun hasAny(value: String?): Boolean = extract(value).isNotEmpty()
}
