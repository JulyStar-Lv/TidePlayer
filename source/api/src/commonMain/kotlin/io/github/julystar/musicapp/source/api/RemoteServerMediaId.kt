package io.github.julystar.musicapp.source.api

import io.github.julystar.musicapp.core.domain.model.SourceAccountId

data class RemoteServerPlaybackTarget(
    val accountId: SourceAccountId,
    val remoteId: String,
    val sourceMediaId: String? = null,
)

fun RemoteServerTrack.encodedPlaybackId(): String = listOfNotNull(
    accountId.value,
    remoteId,
    sourceMediaId?.takeIf(String::isNotBlank),
).joinToString(REMOTE_SERVER_ID_SEPARATOR)

fun String.decodeRemoteServerPlaybackTarget(): RemoteServerPlaybackTarget? {
    val components = split(REMOTE_SERVER_ID_SEPARATOR, limit = 3)
    if (components.size < 2 || components[0].isBlank() || components[1].isBlank()) return null
    return RemoteServerPlaybackTarget(
        accountId = SourceAccountId(components[0]),
        remoteId = components[1],
        sourceMediaId = components.getOrNull(2)?.takeIf(String::isNotBlank),
    )
}

private const val REMOTE_SERVER_ID_SEPARATOR = "|"
