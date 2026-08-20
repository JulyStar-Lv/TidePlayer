package io.github.julystar.musicapp.source.server

import io.github.julystar.musicapp.source.api.sanitizeRemoteServerBaseUrl
import uniffi.app_backend.RemoteMusicException

internal data class RemoteServerEndpointResult<T>(
    val value: T,
    val endpoint: String,
)

internal object RemoteServerEndpointPolicy {
    fun <T> execute(
        primaryBaseUrl: String,
        secondaryBaseUrl: String?,
        request: (String) -> T,
    ): RemoteServerEndpointResult<T> {
        try {
            return RemoteServerEndpointResult(request(primaryBaseUrl), primaryBaseUrl)
        } catch (error: RemoteMusicException.Timeout) {
            return fallback(primaryBaseUrl, secondaryBaseUrl, error, request)
        } catch (error: RemoteMusicException.Connectivity) {
            return fallback(primaryBaseUrl, secondaryBaseUrl, error, request)
        }
    }

    private fun <T> fallback(
        primaryBaseUrl: String,
        secondaryBaseUrl: String?,
        primaryFailure: RemoteMusicException,
        request: (String) -> T,
    ): RemoteServerEndpointResult<T> {
        val secondary = sanitizeRemoteServerBaseUrl(secondaryBaseUrl)
            ?.takeUnless { it.trimEnd('/') == primaryBaseUrl.trimEnd('/') }
            ?: throw primaryFailure
        return RemoteServerEndpointResult(request(secondary), secondary)
    }
}
