package io.github.julystar.musicapp.service.playback.domain

interface PlaybackEngine {
    fun load(request: PlaybackEngineLoadRequest): PlaybackEngineLoadResult
    fun play()
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long)
    fun readPosition(): PlaybackPosition
    fun release()
}

data class PlaybackEngineLoadRequest(
    val item: PlayableItem,
    val resource: PlaybackEngineResource,
) {
    override fun toString(): String =
        "PlaybackEngineLoadRequest(item=$item, resource=$resource)"
}

data class PlaybackEngineResource(
    val uri: String,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
    val expiresAtEpochMs: Long? = null,
    val isLocal: Boolean = false,
) {
    init {
        require(uri.isNotBlank()) { "PlaybackEngineResource uri cannot be blank" }
        require(headers.keys.none { it.isBlank() }) {
            "PlaybackEngineResource header names cannot be blank"
        }
        require(expiresAtEpochMs == null || expiresAtEpochMs >= 0) {
            "PlaybackEngineResource expiresAtEpochMs cannot be negative"
        }
    }

    fun isExpired(nowEpochMs: Long): Boolean {
        return expiresAtEpochMs?.let { it <= nowEpochMs } ?: false
    }

    override fun toString(): String =
        "PlaybackEngineResource(uri=<redacted>, headers=<redacted>, mimeType=$mimeType, " +
            "expiresAtEpochMs=$expiresAtEpochMs, isLocal=$isLocal)"
}

sealed interface PlaybackEngineLoadResult {
    data object Ready : PlaybackEngineLoadResult
    data class Unsupported(
        val reason: PlaybackEngineUnsupportedReason = PlaybackEngineUnsupportedReason.Unknown,
    ) : PlaybackEngineLoadResult
    data class Failure(
        val reason: PlaybackEngineFailureReason = PlaybackEngineFailureReason.Unknown,
    ) : PlaybackEngineLoadResult
}

enum class PlaybackEngineUnsupportedReason {
    UnsupportedResource,
    UnsupportedFormat,
    MissingPlatformEngine,
    Unknown,
}

enum class PlaybackEngineFailureReason {
    ExpiredResource,
    NetworkUnavailable,
    EngineError,
    Unknown,
}
