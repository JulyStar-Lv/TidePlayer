package io.github.julystar.musicapp.core.domain.model

fun sanitizeSourceEndpointForDisplay(rawEndpoint: String): String? {
    val value = rawEndpoint.trim()
    if (value.isEmpty() || value.any { it.isWhitespace() || it.isISOControl() }) return null

    val schemeEnd = value.indexOf(SCHEME_SEPARATOR)
    if (schemeEnd <= 0) return null
    val scheme = value.substring(0, schemeEnd).lowercase()
    if (scheme !in SAFE_DISPLAY_SCHEMES) return null

    val authorityStart = schemeEnd + SCHEME_SEPARATOR.length
    val authorityEnd = value.indexOfAny(charArrayOf('/', '?', '#'), authorityStart)
        .takeIf { it >= 0 }
        ?: value.length
    val authority = value.substring(authorityStart, authorityEnd)
    val hostAndPort = authority.substringAfterLast('@')
    val safeHostAndPort = sanitizeHostAndPort(hostAndPort) ?: return null
    return "$scheme$SCHEME_SEPARATOR$safeHostAndPort"
}

fun sanitizeSourceTitleForDisplay(
    title: String,
    rawEndpoint: String,
    providerLabel: String,
): String = title.trim().takeIf { it.isNotEmpty() && it != rawEndpoint.trim() } ?: providerLabel

private fun sanitizeHostAndPort(value: String): String? {
    if (value.isEmpty()) return null
    if (value.startsWith('[')) {
        val closingBracket = value.indexOf(']')
        if (closingBracket <= 1) return null
        val host = value.substring(1, closingBracket)
        if (!isValidIpv6Literal(host)) return null
        val suffix = value.substring(closingBracket + 1)
        val port = when {
            suffix.isEmpty() -> ""
            suffix.startsWith(':') -> sanitizePort(suffix.substring(1))?.let { ":$it" }
                ?: return null
            else -> return null
        }
        return "[${host.lowercase()}]$port"
    }

    if ('[' in value || ']' in value || value.count { it == ':' } > 1) return null
    val host = value.substringBefore(':')
    if (!isValidDnsHost(host)) return null
    val port = if (':' in value) {
        sanitizePort(value.substringAfter(':'))?.let { ":$it" } ?: return null
    } else {
        ""
    }
    return host.lowercase() + port
}

private fun sanitizePort(value: String): String? {
    if (value.isEmpty() || value.any { it !in '0'..'9' }) return null
    return value.toIntOrNull()?.takeIf { it in 1..65535 }?.toString()
}

private fun isValidDnsHost(host: String): Boolean {
    if (host.isEmpty() || host.length > 253) return false
    return host.split('.').all { label ->
        label.isNotEmpty() &&
            label.length <= 63 &&
            label.first().isAsciiLetterOrDigit() &&
            label.last().isAsciiLetterOrDigit() &&
            label.all { it.isAsciiLetterOrDigit() || it == '-' }
    }
}

private fun isValidIpv6Literal(host: String): Boolean {
    if (':' !in host || host.any { it !in "0123456789abcdefABCDEF:" }) return false
    val compressionIndex = host.indexOf("::")
    if (compressionIndex >= 0 && compressionIndex != host.lastIndexOf("::")) return false
    if (host.startsWith(':') && !host.startsWith("::")) return false
    if (host.endsWith(':') && !host.endsWith("::")) return false

    val segments = host.split(':').filter(String::isNotEmpty)
    if (segments.any { it.length > 4 }) return false
    return if (compressionIndex >= 0) segments.size < 8 else segments.size == 8
}

private fun Char.isAsciiLetterOrDigit(): Boolean =
    this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

private const val SCHEME_SEPARATOR = "://"
private val SAFE_DISPLAY_SCHEMES = setOf("http", "https")
