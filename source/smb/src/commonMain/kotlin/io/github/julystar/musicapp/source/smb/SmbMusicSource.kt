package io.github.julystar.musicapp.source.smb

import io.github.julystar.musicapp.core.domain.model.MediaId
import io.github.julystar.musicapp.core.domain.model.SourceAccountId
import io.github.julystar.musicapp.source.api.BuiltInSourceIds
import io.github.julystar.musicapp.source.api.LegacyStorageConnectionRequest
import io.github.julystar.musicapp.source.api.LegacyStorageConnectionTester
import io.github.julystar.musicapp.source.api.LegacyStorageDirectoryLister
import io.github.julystar.musicapp.source.api.LegacyStorageKind
import io.github.julystar.musicapp.source.api.LegacyStoragePlaybackResolver
import io.github.julystar.musicapp.source.api.LegacyStorageSearchProvider
import io.github.julystar.musicapp.source.api.LegacySmbServerDirectoryLister
import io.github.julystar.musicapp.source.api.MusicSource
import io.github.julystar.musicapp.source.api.MusicSourceDescriptor
import io.github.julystar.musicapp.source.api.SmbSourceConfiguration
import io.github.julystar.musicapp.source.api.SourceAuthFailureReason
import io.github.julystar.musicapp.source.api.SourceAuthResult
import io.github.julystar.musicapp.source.api.SourceCapability
import io.github.julystar.musicapp.source.api.SourceConfiguration
import io.github.julystar.musicapp.source.api.SourceListResult
import io.github.julystar.musicapp.source.api.SourcePlaybackResult
import io.github.julystar.musicapp.source.api.SourceSearchResult
import io.github.julystar.musicapp.source.api.UnsupportedLegacyStorageSearchProvider
import io.github.julystar.musicapp.source.api.resolveLegacyStoragePlayback

class SmbMusicSource(
    private val connectionTester: LegacyStorageConnectionTester,
    private val directoryLister: LegacyStorageDirectoryLister,
    private val playbackResolver: LegacyStoragePlaybackResolver,
    private val searchProvider: LegacyStorageSearchProvider = UnsupportedLegacyStorageSearchProvider,
    private val serverDirectoryLister: LegacySmbServerDirectoryLister =
        LegacySmbServerDirectoryLister { accountId, directoryId ->
            directoryLister.list(accountId, directoryId, LegacyStorageKind.Smb)
        },
) : MusicSource {
    override val descriptor = MusicSourceDescriptor(
        id = BuiltInSourceIds.Smb,
        displayName = "SMB",
    )

    override val capabilities = setOf(
        SourceCapability.Browse,
        SourceCapability.Search,
        SourceCapability.Stream,
        SourceCapability.Download,
    )

    override suspend fun authenticate(configuration: SourceConfiguration): SourceAuthResult {
        if (configuration !is SmbSourceConfiguration) {
            return SourceAuthResult.Failure(SourceAuthFailureReason.UnsupportedConfiguration)
        }
        return connectionTester.test(configuration.toLegacyStorageConnectionRequest())
    }

    override suspend fun list(
        accountId: SourceAccountId,
        directoryId: String?,
    ): SourceListResult {
        return directoryLister.list(accountId, directoryId, LegacyStorageKind.Smb)
    }

    override suspend fun listPathConfiguration(
        accountId: SourceAccountId,
        directoryId: String?,
    ): SourceListResult {
        return serverDirectoryLister.list(accountId, directoryId)
    }

    override suspend fun search(
        accountId: SourceAccountId,
        query: String,
        limit: Int,
    ): SourceSearchResult {
        return searchProvider.search(
            accountId = accountId,
            query = query,
            limit = limit,
            expectedStorageKind = LegacyStorageKind.Smb,
            sourceId = descriptor.id,
        )
    }

    override suspend fun resolvePlayback(mediaId: MediaId): SourcePlaybackResult {
        return mediaId.resolveLegacyStoragePlayback(
            expectedSourceId = descriptor.id,
            expectedStorageKind = LegacyStorageKind.Smb,
            playbackResolver = playbackResolver,
        )
    }
}

fun SmbSourceConfiguration.toSmbAddress(): String {
    require(host.isNotBlank()) { "SMB host cannot be blank" }
    require(port in 1..65535) { "SMB port must be between 1 and 65535" }
    val normalizedShare = share.normalizedSmbPath(required = false)
    val normalizedRoot = rootPath.normalizedSmbPath(required = false)
    val renderedHost = if (':' in host && !host.startsWith("[")) "[$host]" else host
    val path = buildString {
        if (normalizedShare.isNotEmpty()) {
            append('/')
            append(normalizedShare.encodeUrlComponent())
            normalizedRoot.split('/').filter(String::isNotEmpty).forEach { segment ->
                append('/')
                append(segment.encodeUrlComponent())
            }
        }
    }
    val query = buildList {
        domain?.trim()?.takeIf(String::isNotEmpty)?.let { add("domain=${it.encodeUrlComponent()}") }
        if (requireSigning) add("signing=true")
        if (requireEncryption) add("encryption=true")
    }.joinToString("&")
    return buildString {
        append("smb://")
        append(renderedHost)
        if (port != 445) append(":$port")
        append(path)
        if (query.isNotEmpty()) append("?$query")
    }
}

private fun SmbSourceConfiguration.toLegacyStorageConnectionRequest(): LegacyStorageConnectionRequest {
    return LegacyStorageConnectionRequest(
        alias = alias,
        address = toSmbAddress(),
        username = if (isGuest) "" else username,
        password = if (isGuest) "" else password,
        isAnonymous = isGuest,
        kind = LegacyStorageKind.Smb,
    )
}

private fun String.normalizedSmbPath(required: Boolean): String {
    require('\u0000' !in this) { "SMB paths cannot contain NUL" }
    val segments = replace('\\', '/')
        .split('/')
        .filter { it.isNotEmpty() && it != "." }
    require(".." !in segments) { "SMB path traversal is not allowed" }
    val normalized = segments.joinToString("/")
    if (required) require(normalized.isNotEmpty()) { "SMB share cannot be blank" }
    return normalized
}

private fun String.encodeUrlComponent(): String {
    return buildString {
        this@encodeUrlComponent.encodeToByteArray().forEach { byte ->
            val value = byte.toInt() and 0xff
            val character = value.toChar()
            if (
                character in 'A'..'Z' ||
                character in 'a'..'z' ||
                character in '0'..'9' ||
                character == '-' ||
                character == '_' ||
                character == '.' ||
                character == '~'
            ) {
                append(character)
            } else {
                append('%')
                append(value.toString(16).uppercase().padStart(2, '0'))
            }
        }
    }
}
