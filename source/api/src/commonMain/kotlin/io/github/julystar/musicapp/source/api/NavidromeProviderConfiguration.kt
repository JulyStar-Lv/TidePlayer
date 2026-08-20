package io.github.julystar.musicapp.source.api

data class NavidromeProviderConfiguration(
    val streamMaxBitRate: Int = 0,
    val downloadMaxBitRate: Int = 0,
    val coverArtSize: Int = DEFAULT_COVER_ART_SIZE,
    val remoteWriteEnabled: Boolean = false,
    val secondaryBaseUrl: String? = null,
) {
    fun sanitized(): NavidromeProviderConfiguration = copy(
        streamMaxBitRate = streamMaxBitRate.coerceAtLeast(0),
        downloadMaxBitRate = downloadMaxBitRate.coerceAtLeast(0),
        coverArtSize = coverArtSize.takeIf { it in ALLOWED_COVER_ART_SIZES } ?: DEFAULT_COVER_ART_SIZE,
        secondaryBaseUrl = sanitizeRemoteServerBaseUrl(secondaryBaseUrl),
    )

    companion object {
        const val DEFAULT_COVER_ART_SIZE = 512
        val ALLOWED_COVER_ART_SIZES = setOf(256, 512, 768, 1024)
        val Defaults = NavidromeProviderConfiguration()
    }
}

object NavidromeProviderConfigurationCodec {
    fun decode(value: String?): NavidromeProviderConfiguration {
        if (value.isNullOrBlank()) return NavidromeProviderConfiguration.Defaults
        return runCatching {
            val fields = parseObject(value)
            NavidromeProviderConfiguration(
                streamMaxBitRate = fields["streamMaxBitRate"]?.toIntOrNull() ?: 0,
                downloadMaxBitRate = fields["downloadMaxBitRate"]?.toIntOrNull() ?: 0,
                coverArtSize = fields["coverArtSize"]?.toIntOrNull() ?: 512,
                remoteWriteEnabled = fields["remoteWriteEnabled"]?.toBooleanStrictOrNull() ?: false,
                secondaryBaseUrl = fields["secondaryBaseUrl"]?.takeUnless { it == "null" },
            ).sanitized()
        }
            .getOrDefault(NavidromeProviderConfiguration.Defaults)
    }

    fun encode(value: NavidromeProviderConfiguration): String {
        val safe = value.sanitized()
        fun quote(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        return "{" + listOf(
            "\"streamMaxBitRate\":${safe.streamMaxBitRate}",
            "\"downloadMaxBitRate\":${safe.downloadMaxBitRate}",
            "\"coverArtSize\":${safe.coverArtSize}",
            "\"remoteWriteEnabled\":${safe.remoteWriteEnabled}",
            "\"secondaryBaseUrl\":" + (safe.secondaryBaseUrl?.let(::quote) ?: "null"),
        ).joinToString(",") + "}"
    }

    private fun parseObject(value: String): Map<String, String> {
        val body = value.trim().removePrefix("{").removeSuffix("}")
        val pairs = mutableListOf<String>()
        var quoted = false
        var escaped = false
        var start = 0
        body.forEachIndexed { index, char ->
            if (escaped) escaped = false
            else if (char == '\\' && quoted) escaped = true
            else if (char == '"') quoted = !quoted
            else if (char == ',' && !quoted) {
                pairs += body.substring(start, index)
                start = index + 1
            }
        }
        if (quoted) error("unterminated JSON string")
        pairs += body.substring(start)
        return pairs.mapNotNull { pair ->
            val separator = pair.indexOfTopLevelColon()
            if (separator < 0) return@mapNotNull null
            val key = pair.substring(0, separator).trim().trim('"')
            val raw = pair.substring(separator + 1).trim()
            key.takeIf(String::isNotBlank)?.let { it to unquote(raw) }
        }.toMap()
    }

    private fun String.indexOfTopLevelColon(): Int {
        var quoted = false
        var escaped = false
        forEachIndexed { index, char ->
            if (escaped) escaped = false
            else if (char == '\\' && quoted) escaped = true
            else if (char == '"') quoted = !quoted
            else if (char == ':' && !quoted) return index
        }
        return -1
    }

    private fun unquote(raw: String): String {
        if (raw == "null") return "null"
        if (!raw.startsWith('"') || !raw.endsWith('"')) return raw
        return raw.substring(1, raw.length - 1)
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

}
