package io.github.julystar.musicapp.source.api

fun sanitizeRemoteServerBaseUrl(value: String?): String? {
    val normalized = value?.trim()?.trimEnd('/')?.takeIf(String::isNotEmpty) ?: return null
    if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) return null
    if (normalized.any(Char::isWhitespace) || normalized.contains('?') || normalized.contains('#')) return null
    val authority = normalized.substringAfter("://").substringBefore('/')
    return normalized.takeIf { authority.isNotBlank() && !authority.contains('@') }
}
