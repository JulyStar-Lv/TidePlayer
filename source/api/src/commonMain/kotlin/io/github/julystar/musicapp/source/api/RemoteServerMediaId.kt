package io.github.julystar.musicapp.source.api

import io.github.julystar.musicapp.core.domain.model.SourceAccountId

data class RemoteServerPlaybackTarget(
    val accountId: SourceAccountId,
    val remoteId: String,
    val sourceMediaId: String? = null,
)

fun RemoteServerPlaybackTarget.encodedPlaybackId(): String = listOfNotNull(
    accountId.value,
    remoteId,
    sourceMediaId?.takeIf(String::isNotBlank),
).joinToString(REMOTE_SERVER_VERSION_SEPARATOR) { encodeRemoteServerComponent(it) }
    .let { "$REMOTE_SERVER_ID_VERSION_PREFIX$it" }

fun RemoteServerTrack.encodedPlaybackId(): String = RemoteServerPlaybackTarget(
    accountId = accountId,
    remoteId = remoteId,
    sourceMediaId = sourceMediaId,
).encodedPlaybackId()

fun String.decodeRemoteServerPlaybackTarget(): RemoteServerPlaybackTarget? {
    if (!contains(REMOTE_SERVER_ID_SEPARATOR) && startsWith(REMOTE_SERVER_ID_VERSION_PREFIX)) {
        val components = removePrefix(REMOTE_SERVER_ID_VERSION_PREFIX)
            .split(REMOTE_SERVER_VERSION_SEPARATOR)
        if (components.size !in 2..3) return null
        val decoded = components.map { decodeRemoteServerComponent(it) }
        if (decoded.any { it.isNullOrBlank() }) return null
        return RemoteServerPlaybackTarget(
            accountId = SourceAccountId(decoded[0].orEmpty()),
            remoteId = decoded[1].orEmpty(),
            sourceMediaId = decoded.getOrNull(2),
        )
    }
    if (!contains(REMOTE_SERVER_ID_SEPARATOR)) return null

    val components = split(REMOTE_SERVER_ID_SEPARATOR)
    if (components.size !in 2..3 || components[0].isBlank() || components[1].isBlank()) {
        return null
    }
    return RemoteServerPlaybackTarget(
        accountId = SourceAccountId(components[0]),
        remoteId = components[1],
        sourceMediaId = components.getOrNull(2)?.takeIf(String::isNotBlank),
    )
}

private fun encodeRemoteServerComponent(value: String): String {
    val encoded = StringBuilder(value.length)
    for (byte in value.encodeToByteArray()) {
        val unsigned = byte.toInt() and 0xff
        val char = unsigned.toChar()
        if (char.isUnreservedRemoteServerComponent()) {
            encoded.append(char)
        } else {
            encoded.append('%')
            encoded.append(unsigned.toString(16).uppercase().padStart(2, '0'))
        }
    }
    return encoded.toString()
}

private fun decodeRemoteServerComponent(value: String): String? {
    val decoded = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        if (value[index] != '%') {
            if (!value[index].isUnreservedRemoteServerComponent()) return null
            decoded.append(value[index])
            index += 1
            continue
        }
        val bytes = mutableListOf<Byte>()
        while (index < value.length && value[index] == '%') {
            if (index + 2 >= value.length) return null
            val byte = value.substring(index + 1, index + 3).toIntOrNull(16) ?: return null
            bytes += byte.toByte()
            index += 3
        }
        decoded.append(bytes.toByteArray().decodeToString())
    }
    return decoded.toString()
}

private fun Char.isUnreservedRemoteServerComponent(): Boolean {
    return this in 'A'..'Z' ||
        this in 'a'..'z' ||
        this in '0'..'9' ||
        this == '-' ||
        this == '_' ||
        this == '.' ||
        this == '~'
}

private const val REMOTE_SERVER_ID_SEPARATOR = "|"
private const val REMOTE_SERVER_VERSION_SEPARATOR = ":"
private const val REMOTE_SERVER_ID_VERSION_PREFIX = "v2:"
