package io.github.julystar.musicapp.core.data

import io.github.julystar.musicapp.source.api.SourceNodeSelection

data class CreatePlaylistRequest(
    val title: String,
    val cover: SourceNodeSelection?,
    val entries: List<SourceNodeSelection>,
    val coverImage: ByteArray? = null,
)

data class UpdatePlaylistRequest(
    val id: Long,
    val title: String,
    val cover: SourceNodeSelection?,
    val coverImage: ByteArray? = null,
)
